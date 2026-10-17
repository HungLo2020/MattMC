//! Baking resolved models into quads (`FaceBakery`, `SimpleUnbakedGeometry`,
//! `QuadCollection`, `ItemModelGenerator`).
//!
//! Sprites come from Java's block atlas (UVs, sizes, frames, the shrink
//! ratio hooks decide, and the sheet's pixels for generated item edges);
//! rotation matrices come from Java's JOML (`BlockModelRotation` and
//! element rotations), so only JOML's multiply-add order is reproduced.

use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::sync::Mutex;

use super::geometry::{self, Axis, Direction, Matrix, Quadrant};
use super::model::{Element, ElementRotation, Face, Geometry};
use crate::content::tags::Location;

/// A block atlas sprite as baking reads it.
#[derive(Debug)]
pub(crate) struct Sprite {
    pub(crate) u0: f32,
    pub(crate) u1: f32,
    pub(crate) v0: f32,
    pub(crate) v1: f32,
    /// `uvShrinkRatio()` (Sodium's hook makes it 0).
    pub(crate) shrink: f32,
    /// Frame size.
    pub(crate) width: u32,
    pub(crate) height: u32,
    /// `getUniqueFrames()`: frame indices, or `[1]` when not animated.
    pub(crate) unique_frames: Vec<i32>,
    /// Frames per row of the sheet (0 when not animated).
    pub(crate) row_size: u32,
    /// The sheet (`originalImage`): RGBA rows in Java memory.
    pub(crate) sheet: *const u8,
    pub(crate) sheet_width: u32,
    pub(crate) sheet_height: u32,
}

// Sheets are Java images kept alive (and unchanged) while baking reads them.
unsafe impl Send for Sprite {}
unsafe impl Sync for Sprite {}

impl Sprite {
    /// Generated-item scans reuse a bounded alpha mask with a transparent
    /// border. Validate the complete frame before reading ahead; malformed
    /// frames keep the original per-access error prefix below.
    fn alpha_border(&self, frame: i32) -> Option<Vec<u8>> {
        if !self.frame_inside(frame) || self.width > i32::MAX as u32 || self.height > i32::MAX as u32 {
            return None;
        }
        let stride = (self.width as usize).checked_add(2)?;
        let size = stride.checked_mul((self.height as usize).checked_add(2)?)?;
        if size > 4 * 1024 * 1024 { return None; }
        let mut mask = Vec::new();
        mask.try_reserve_exact(size).ok()?;
        mask.resize(size, 1);
        let (left, top) = if self.row_size == 0 { (0, 0) } else {
            ((frame % self.row_size as i32) as usize * self.width as usize,
             (frame / self.row_size as i32) as usize * self.height as usize)
        };
        for y in 0..self.height as usize {
            let row = ((top + y) * self.sheet_width as usize + left) * 4 + 3;
            for x in 0..self.width as usize {
                mask[(y + 1) * stride + x + 1] = u8::from(unsafe { *self.sheet.add(row + x * 4) } == 0);
            }
        }
        Some(mask)
    }

    /// `SpriteContents.isTransparent(frame, x, y)`: alpha 0; `None` out of
    /// the sheet (Java throws).
    fn transparent(&self, frame: i32, x: i32, y: i32) -> Result<bool, Failure> {
        let (mut px, mut py) = (x, y);
        if self.row_size > 0 {
            px = x + (frame % self.row_size as i32) * self.width as i32;
            py = y + (frame / self.row_size as i32) * self.height as i32;
        }
        if px < 0 || py < 0 || px as u32 >= self.sheet_width || py as u32 >= self.sheet_height || self.sheet.is_null() {
            // `NativeImage.getPixel`.
            return Err(format!(
                "java.lang.IllegalArgumentException: ({px}, {py}) outside of image bounds ({}, {})",
                self.sheet_width, self.sheet_height
            ));
        }
        let offset = (py as usize * self.sheet_width as usize + px as usize) * 4 + 3;
        Ok(unsafe { *self.sheet.add(offset) } == 0)
    }

    /// Whether every pixel of `frame` lies on the sheet.
    fn frame_inside(&self, frame: i32) -> bool {
        let (mut x, mut y) = (0i64, 0i64);
        if self.row_size > 0 {
            x = i64::from(frame % self.row_size as i32) * i64::from(self.width);
            y = i64::from(frame / self.row_size as i32) * i64::from(self.height);
        }
        !self.sheet.is_null()
            && x >= 0
            && y >= 0
            && x + i64::from(self.width) <= i64::from(self.sheet_width)
            && y + i64::from(self.height) <= i64::from(self.sheet_height)
    }

    /// `getU(f)` / `getV(f)`.
    fn u(&self, value: f32) -> f32 {
        let span = self.u1 - self.u0;
        self.u0 + span * value
    }

    fn v(&self, value: f32) -> f32 {
        let span = self.v1 - self.v0;
        self.v0 + span * value
    }
}

/// The block atlas's sprites by name; `missing` is the missing sprite.
pub(crate) struct Sprites {
    pub(crate) sprites: Vec<Sprite>,
    pub(crate) by_name: HashMap<Location, u32>,
    pub(crate) missing: u32,
}

/// A model state (`BlockModelRotation`, optionally with UV lock), from Java.
#[derive(Clone, Debug)]
pub(crate) struct Rotation {
    /// `transformation().getMatrix()`; `None` for `Transformation.identity()`.
    pub(crate) transformation: Option<Matrix>,
    /// The cull-face matrix (`transformation().getMatrix()`, identity included).
    pub(crate) cull: Matrix,
    /// `inverseFaceTransformation(direction)` with UV lock, per direction,
    /// with `MatrixUtil.isIdentity`.
    pub(crate) uv_lock: [(Matrix, bool); 6],
}

/// An element rotation's matrix and rescale factor, from Java's JOML.
#[derive(Clone, Copy, Debug)]
pub(crate) struct ElementMatrix {
    pub(crate) matrix: Matrix,
    /// `1 / Mth.cos(|angle| * PI/180)`.
    pub(crate) rescale: f32,
}

/// The JOML matrices baking needs.
pub(crate) struct Matrices {
    /// By `BlockModelRotation` ordinal (x quadrant * 4 + y quadrant).
    pub(crate) rotations: Vec<Rotation>,
    /// By (axis, angle bits).
    pub(crate) elements: HashMap<(u32, u32), ElementMatrix>,
}

/// A baked quad (`BakedQuad`'s fields).
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Quad {
    pub(crate) vertices: [i32; 32],
    pub(crate) tint: i32,
    pub(crate) direction: Direction,
    pub(crate) sprite: u32,
    pub(crate) shade: bool,
    pub(crate) light_emission: i32,
}

/// A `QuadCollection`: unculled quads, then culled ones by
/// north, south, east, west, up, down.
#[derive(Clone, Debug, Default, PartialEq)]
pub(crate) struct Quads {
    pub(crate) quads: Vec<Quad>,
    /// Counts: unculled, north, south, east, west, up, down.
    pub(crate) counts: [u32; 7],
}

/// Missing sprites one bake step reported (`ModelManager`'s two
/// multimaps, by model debug name). Java merges the steps it evaluated and
/// logs them.
#[derive(Debug, Default)]
pub(crate) struct Missing {
    pub(crate) textures: Mutex<BTreeMap<String, BTreeSet<(String, String)>>>,
    pub(crate) references: Mutex<BTreeMap<String, BTreeSet<String>>>,
}

impl Missing {
    pub(crate) fn merge(&self, other: &Missing) {
        let mut textures = self.textures.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        for (model, entries) in other.textures.lock().unwrap_or_else(|poisoned| poisoned.into_inner()).iter() {
            textures.entry(model.clone()).or_default().extend(entries.iter().cloned());
        }
        let mut references = self.references.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
        for (model, names) in other.references.lock().unwrap_or_else(|poisoned| poisoned.into_inner()).iter() {
            references.entry(model.clone()).or_default().extend(names.iter().cloned());
        }
    }
}

/// The blocks atlas, for missing-texture messages.
const BLOCKS_ATLAS: &str = "minecraft:textures/atlas/blocks.png";

/// One bake's inputs.
pub(crate) struct Baker<'a> {
    pub(crate) sprites: &'a Sprites,
    pub(crate) matrices: &'a Matrices,
    pub(crate) missing: &'a Missing,
}

/// A bake failure, as Java's exception message.
pub(crate) type Failure = String;

impl Baker<'_> {
    /// `SpriteGetter.resolveSlot(textureSlots, name, model)`.
    pub(crate) fn resolve(&self, textures: &HashMap<String, Location>, name: &str, model: &str) -> Result<u32, Failure> {
        if name.is_empty() {
            // `TextureSlots.isTextureReference`: `"".charAt(0)`.
            return Err("java.lang.StringIndexOutOfBoundsException: Index 0 out of bounds for length 0".into());
        }
        let key = name.strip_prefix('#').unwrap_or(name);
        match textures.get(key) {
            Some(texture) => Ok(self.sprite(texture, model)),
            None => {
                let mut references = self.missing.references.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
                references.entry(model.to_owned()).or_default().insert(name.to_owned());
                Ok(self.sprites.missing)
            }
        }
    }

    /// `SpriteGetter.get(material, model)` for a blocks-atlas material.
    fn sprite(&self, texture: &Location, model: &str) -> u32 {
        match self.sprites.by_name.get(texture) {
            Some(&sprite) => sprite,
            None => {
                let mut textures = self.missing.textures.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
                textures.entry(model.to_owned()).or_default().insert((BLOCKS_ATLAS.to_owned(), texture.to_string()));
                self.sprites.missing
            }
        }
    }

    /// `UnbakedGeometry.bake` for a resolved model's top geometry.
    pub(crate) fn geometry(
        &self,
        geometry: Option<&Geometry>,
        textures: &HashMap<String, Location>,
        rotation: usize,
        uv_lock: bool,
        model: &str,
    ) -> Result<Quads, Failure> {
        match geometry {
            None => Ok(Quads::default()),
            Some(Geometry::Elements(elements)) => self.elements(elements, textures, rotation, uv_lock, model),
            Some(Geometry::Generated) => self.generated(textures, rotation, uv_lock, model),
        }
    }

    /// `SimpleUnbakedGeometry.bake`: elements in order, faces in direction
    /// order. A JSON `null` element or face fails the bake where Java's loop
    /// reaches it (after earlier faces' sprites, and their missing-texture
    /// records), with Java's message.
    fn elements(&self, elements: &[Option<Element>], textures: &HashMap<String, Location>, rotation: usize, uv_lock: bool, model: &str) -> Result<Quads, Failure> {
        let mut unculled: Vec<Quad> = Vec::new();
        let mut culled: [Vec<Quad>; 6] = Default::default();
        let state = &self.matrices.rotations[rotation];
        for element in elements {
            let element = element.as_ref().ok_or_else(|| {
                r#"java.lang.NullPointerException: Cannot invoke "net.minecraft.client.renderer.block.model.BlockElement.faces()" because "blockElement" is null"#.to_owned()
            })?;
            for direction in Direction::ALL {
                let Some(face) = &element.faces[direction.index()] else { continue };
                let face = face.as_ref().ok_or_else(|| {
                    r#"java.lang.NullPointerException: Cannot invoke "net.minecraft.client.renderer.block.model.BlockElementFace.texture()" because "blockElementFace" is null"#.to_owned()
                })?;
                let sprite = self.resolve(textures, &face.texture, model)?;
                let quad = self.quad(element, face, sprite, direction, state, uv_lock)?;
                match face.cull {
                    None => unculled.push(quad),
                    Some(cull) => culled[state.cull.rotate(cull).index()].push(quad),
                }
            }
        }
        Ok(collect(unculled, culled))
    }

    /// `FaceBakery.bakeQuad`.
    fn quad(&self, element: &Element, face: &Face, sprite: u32, direction: Direction, state: &Rotation, uv_lock: bool) -> Result<Quad, Failure> {
        let sprite_info = &self.sprites.sprites[sprite as usize];
        let uvs = face.uvs.unwrap_or_else(|| default_uvs(element.from, element.to, direction));
        let uvs = shrink(sprite_info.shrink, uvs);
        let (inverse, identity) = if uv_lock { state.uv_lock[direction.index()] } else { (IDENTITY, true) };
        let shape = geometry::shape(element.from, element.to);
        let element_matrix = match &element.rotation {
            Some(rotation) => Some(self.element_matrix(rotation)?),
            None => None,
        };
        let mut vertices = [0i32; 32];
        let info = geometry::face_info(direction);
        for corner in 0..4 {
            let [x, y, z] = info[corner];
            let mut position = [shape[x], shape[y], shape[z]];
            if let (Some(rotation), Some(matrix)) = (&element.rotation, element_matrix) {
                let rescale = if rotation.rescale && rotation.angle != 0.0 { rescale_vector(rotation.axis, matrix.rescale) } else { [1.0; 3] };
                position = rotate_by(position, rotation.origin, &matrix.matrix, rescale);
            }
            if let Some(matrix) = &state.transformation {
                position = rotate_by(position, [0.5; 3], matrix, [1.0; 3]);
            }
            let u = vertex_u(uvs, face.rotation, corner) / 16.0;
            let v = vertex_v(uvs, face.rotation, corner) / 16.0;
            let (u, v) = if identity {
                (u, v)
            } else {
                let transformed = inverse.transform_position([u - 0.5, v - 0.5, 0.0]);
                (transformed[0] + 0.5, transformed[1] + 0.5)
            };
            let at = corner * 8;
            vertices[at] = position[0].to_bits() as i32;
            vertices[at + 1] = position[1].to_bits() as i32;
            vertices[at + 2] = position[2].to_bits() as i32;
            vertices[at + 3] = -1;
            vertices[at + 4] = sprite_info.u(u).to_bits() as i32;
            vertices[at + 5] = sprite_info.v(v).to_bits() as i32;
        }
        let facing = facing(&vertices);
        if element.rotation.is_none() {
            rewind(&mut vertices, facing);
        }
        Ok(Quad { vertices, tint: face.tint, direction: facing, sprite, shade: element.shade, light_emission: element.light_emission })
    }

    fn element_matrix(&self, rotation: &ElementRotation) -> Result<ElementMatrix, Failure> {
        self.matrices
            .elements
            .get(&(rotation.axis.index(), rotation.angle.to_bits()))
            .copied()
            .ok_or_else(|| format!("no element rotation for {:?} {}", rotation.axis, rotation.angle))
    }

    /// `ItemModelGenerator.bake`: each layer's front and back, and the edges
    /// its pixels' transparency outlines.
    fn generated(&self, textures: &HashMap<String, Location>, rotation: usize, uv_lock: bool, model: &str) -> Result<Quads, Failure> {
        let mut elements: Vec<Element> = Vec::new();
        for layer in 0..5 {
            let name = format!("layer{layer}");
            let Some(texture) = textures.get(&name) else { break };
            let sprite = &self.sprites.sprites[self.sprite(texture, model) as usize];
            let face = |uvs: [f32; 4]| Face { cull: None, tint: layer, texture: name.clone(), uvs: Some(uvs), rotation: Quadrant(0) };
            let mut faces: [Option<Option<Face>>; 6] = Default::default();
            // Java's two-entry `Map.of` iterates in a per-JVM order; south then north here.
            faces[Direction::South.index()] = Some(Some(face([0.0, 0.0, 16.0, 16.0])));
            faces[Direction::North.index()] = Some(Some(face([16.0, 0.0, 0.0, 16.0])));
            elements.push(Element { from: [0.0, 0.0, 7.5], to: [16.0, 16.0, 8.5], faces, rotation: None, shade: true, light_emission: 0 });
            for span in spans(sprite)? {
                elements.push(side_element(sprite, &span, layer, &name));
            }
        }
        // The front element's faces bake south first.
        let mut unculled: Vec<Quad> = Vec::new();
        let mut culled: [Vec<Quad>; 6] = Default::default();
        let state = &self.matrices.rotations[rotation];
        for element in &elements {
            let order: &[Direction] = if element.faces[Direction::South.index()].is_some() && element.faces[Direction::North.index()].is_some() {
                &[Direction::South, Direction::North]
            } else {
                &Direction::ALL
            };
            for &direction in order {
                let Some(Some(face)) = &element.faces[direction.index()] else { continue };
                let sprite = self.resolve(textures, &face.texture, model)?;
                let quad = self.quad(element, face, sprite, direction, state, uv_lock)?;
                match face.cull {
                    None => unculled.push(quad),
                    Some(cull) => culled[state.cull.rotate(cull).index()].push(quad),
                }
            }
        }
        Ok(collect(unculled, culled))
    }
}

const IDENTITY: Matrix = Matrix([1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0]);

/// `QuadCollection.Builder.build`.
fn collect(unculled: Vec<Quad>, culled: [Vec<Quad>; 6]) -> Quads {
    let order = [Direction::North, Direction::South, Direction::East, Direction::West, Direction::Up, Direction::Down];
    let mut counts = [unculled.len() as u32, 0, 0, 0, 0, 0, 0];
    let mut quads = unculled;
    let mut culled = culled;
    for (slot, direction) in order.iter().enumerate() {
        let bucket = std::mem::take(&mut culled[direction.index()]);
        counts[slot + 1] = bucket.len() as u32;
        quads.extend(bucket);
    }
    Quads { quads, counts }
}

/// `FaceBakery.defaultFaceUV`.
fn default_uvs(from: [f32; 3], to: [f32; 3], direction: Direction) -> [f32; 4] {
    match direction {
        Direction::Down => [from[0], 16.0 - to[2], to[0], 16.0 - from[2]],
        Direction::Up => [from[0], from[2], to[0], to[2]],
        Direction::North => [16.0 - to[0], 16.0 - to[1], 16.0 - from[0], 16.0 - from[1]],
        Direction::South => [from[0], 16.0 - to[1], to[0], 16.0 - from[1]],
        Direction::West => [from[2], 16.0 - to[1], to[2], 16.0 - from[1]],
        Direction::East => [16.0 - to[2], 16.0 - to[1], 16.0 - from[2], 16.0 - from[1]],
    }
}

/// `Mth.lerp(f, g, h)`.
fn lerp(f: f32, g: f32, h: f32) -> f32 {
    g + f * (h - g)
}

/// `FaceBakery.shrinkUVs`.
fn shrink(ratio: f32, [min_u, min_v, max_u, max_v]: [f32; 4]) -> [f32; 4] {
    let center_u = (min_u + min_u + max_u + max_u) / 4.0;
    let center_v = (min_v + min_v + max_v + max_v) / 4.0;
    [lerp(ratio, min_u, center_u), lerp(ratio, min_v, center_v), lerp(ratio, max_u, center_u), lerp(ratio, max_v, center_v)]
}

/// `BlockElementFace.UVs.getVertexU(rotation.rotateVertexIndex(corner))`.
fn vertex_u([min_u, _, max_u, _]: [f32; 4], rotation: Quadrant, corner: usize) -> f32 {
    let index = rotation.rotate_vertex_index(corner);
    if index != 0 && index != 1 {
        max_u
    } else {
        min_u
    }
}

fn vertex_v([_, min_v, _, max_v]: [f32; 4], rotation: Quadrant, corner: usize) -> f32 {
    let index = rotation.rotate_vertex_index(corner);
    if index != 0 && index != 3 {
        max_v
    } else {
        min_v
    }
}

/// `FaceBakery.computeRescale`'s vector.
fn rescale_vector(axis: Axis, factor: f32) -> [f32; 3] {
    match axis {
        Axis::X => [1.0, factor, factor],
        Axis::Y => [factor, 1.0, factor],
        Axis::Z => [factor, factor, 1.0],
    }
}

/// `FaceBakery.rotateVertexBy`.
fn rotate_by(position: [f32; 3], origin: [f32; 3], matrix: &Matrix, scale: [f32; 3]) -> [f32; 3] {
    let moved = geometry::sub(position, origin);
    let transformed = matrix.transform_position(moved);
    geometry::add(geometry::mul(transformed, scale), origin)
}

fn position(vertices: &[i32; 32], at: usize) -> [f32; 3] {
    [f32::from_bits(vertices[at] as u32), f32::from_bits(vertices[at + 1] as u32), f32::from_bits(vertices[at + 2] as u32)]
}

/// `FaceBakery.calculateFacing`.
fn facing(vertices: &[i32; 32]) -> Direction {
    let (a, b, c) = (position(vertices, 0), position(vertices, 8), position(vertices, 16));
    let first = geometry::sub(a, b);
    let second = geometry::sub(c, b);
    let normal = geometry::normalize(geometry::cross(second, first));
    if !normal.iter().all(|component| component.is_finite()) {
        return Direction::Up;
    }
    let mut best: Option<Direction> = None;
    let mut score = 0.0f32;
    for direction in Direction::ALL {
        let value = geometry::dot(normal, direction.unit());
        if value >= 0.0 && value > score {
            score = value;
            best = Some(direction);
        }
    }
    best.unwrap_or(Direction::Up)
}

/// `Mth.equal`.
fn equal(a: f32, b: f32) -> bool {
    (b - a).abs() < 1.0e-5
}

/// `FaceBakery.recalculateWinding`: corners re-ordered to the face's
/// order over the quad's bounds, UVs following matching positions.
fn rewind(vertices: &mut [i32; 32], direction: Direction) {
    let original = *vertices;
    let mut bounds = [0.0f32; 6];
    bounds[geometry::SHAPE_MIN_X] = 999.0;
    bounds[geometry::SHAPE_MIN_Y] = 999.0;
    bounds[geometry::SHAPE_MIN_Z] = 999.0;
    bounds[geometry::SHAPE_MAX_X] = -999.0;
    bounds[geometry::SHAPE_MAX_Y] = -999.0;
    bounds[geometry::SHAPE_MAX_Z] = -999.0;
    for corner in 0..4 {
        let [x, y, z] = position(&original, corner * 8);
        if x < bounds[geometry::SHAPE_MIN_X] {
            bounds[geometry::SHAPE_MIN_X] = x;
        }
        if y < bounds[geometry::SHAPE_MIN_Y] {
            bounds[geometry::SHAPE_MIN_Y] = y;
        }
        if z < bounds[geometry::SHAPE_MIN_Z] {
            bounds[geometry::SHAPE_MIN_Z] = z;
        }
        if x > bounds[geometry::SHAPE_MAX_X] {
            bounds[geometry::SHAPE_MAX_X] = x;
        }
        if y > bounds[geometry::SHAPE_MAX_Y] {
            bounds[geometry::SHAPE_MAX_Y] = y;
        }
        if z > bounds[geometry::SHAPE_MAX_Z] {
            bounds[geometry::SHAPE_MAX_Z] = z;
        }
    }
    let info = geometry::face_info(direction);
    for corner in 0..4 {
        let at = corner * 8;
        let [x, y, z] = info[corner].map(|index| bounds[index]);
        vertices[at] = x.to_bits() as i32;
        vertices[at + 1] = y.to_bits() as i32;
        vertices[at + 2] = z.to_bits() as i32;
        for other in 0..4 {
            let from = other * 8;
            let [ox, oy, oz] = position(&original, from);
            if equal(x, ox) && equal(y, oy) && equal(z, oz) {
                vertices[at + 4] = original[from + 4];
                vertices[at + 5] = original[from + 5];
            }
        }
    }
}

// -- generated item edges (`ItemModelGenerator`) -------------------------

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
enum Facing {
    Up,
    Down,
    Left,
    Right,
}

impl Facing {
    const ALL: [Facing; 4] = [Facing::Up, Facing::Down, Facing::Left, Facing::Right];

    fn offset(self) -> (i32, i32) {
        match self {
            Facing::Up => (0, -1),
            Facing::Down => (0, 1),
            Facing::Left => (-1, 0),
            Facing::Right => (1, 0),
        }
    }

    fn direction(self) -> Direction {
        match self {
            Facing::Up => Direction::Up,
            Facing::Down => Direction::Down,
            Facing::Left => Direction::East,
            Facing::Right => Direction::West,
        }
    }

    fn horizontal(self) -> bool {
        matches!(self, Facing::Up | Facing::Down)
    }
}

#[derive(Debug)]
struct Span {
    facing: Facing,
    min: i32,
    max: i32,
    anchor: i32,
}

/// `getSpans`: transitions from an opaque pixel to a transparent (or
/// outside) neighbour, merged per facing and anchor, over unique frames.
fn spans(sprite: &Sprite) -> Result<Vec<Span>, Failure> {
    let (width, height) = (sprite.width as i32, sprite.height as i32);
    let transparent = |frame: i32, x: i32, y: i32| -> Result<bool, Failure> {
        if x >= 0 && y >= 0 && x < width && y < height {
            sprite.transparent(frame, x, y)
        } else {
            Ok(true)
        }
    };
    let mut spans: Vec<Span> = Vec::new();
    // Java searches its span list for (facing, anchor); index it instead,
    // keeping first-seen order.
    let mut index: [Vec<u32>; 4] = std::array::from_fn(|facing| vec![u32::MAX; if Facing::ALL[facing].horizontal() { height } else { width } as usize]);
    for &frame in &sprite.unique_frames {
        // Java reads the neighbours of transparent pixels too, which only
        // matters when a read can fail: a frame outside the sheet.
        let inside = sprite.frame_inside(frame);
        let mask = sprite.alpha_border(frame);
        let stride = width as usize + 2;
        let transparent = |x: i32, y: i32| -> Result<bool, Failure> {
            if let Some(mask) = &mask {
                Ok(mask[(y + 1) as usize * stride + (x + 1) as usize] != 0)
            } else {
                transparent(frame, x, y)
            }
        };
        for y in 0..height {
            for x in 0..width {
                if transparent(x, y)? {
                    if !inside {
                        for facing in Facing::ALL {
                            let (dx, dy) = facing.offset();
                            transparent(x + dx, y + dy)?;
                        }
                    }
                    continue;
                }
                for (slot, facing) in Facing::ALL.into_iter().enumerate() {
                    let (dx, dy) = facing.offset();
                    if transparent(x + dx, y + dy)? {
                        let anchor = if facing.horizontal() { y } else { x };
                        let along = if facing.horizontal() { x } else { y };
                        let entry = &mut index[slot][anchor as usize];
                        match spans.get_mut(*entry as usize) {
                            Some(span) => {
                                if along < span.min {
                                    span.min = along;
                                } else if along > span.max {
                                    span.max = along;
                                }
                            }
                            None => {
                                *entry = spans.len() as u32;
                                spans.push(Span { facing, min: along, max: along, anchor });
                            }
                        }
                    }
                }
            }
        }
    }
    Ok(spans)
}

/// `createSideElements` for one span.
fn side_element(sprite: &Sprite, span: &Span, layer: i32, texture: &str) -> Element {
    let (f, g) = (sprite.width as f32, sprite.height as f32);
    let q = 16.0f32 / f;
    let r = 16.0f32 / g;
    let (s, t, u) = (span.min as f32, span.max as f32, span.anchor as f32);
    // (h, j, k, l) bound the edge; (m, o, n, p) are its UVs (Java's names).
    let (mut h, mut j, mut k, mut l, mut m, mut n, mut o, mut p) = match span.facing {
        Facing::Up => (s, u, t + 1.0, u, s, t + 1.0, u, u + 1.0),
        Facing::Down => (s, u + 1.0, t + 1.0, u + 1.0, s, t + 1.0, u, u + 1.0),
        Facing::Left => (u, s, u, t + 1.0, u, u + 1.0, t + 1.0, s),
        Facing::Right => (u + 1.0, s, u + 1.0, t + 1.0, u, u + 1.0, t + 1.0, s),
    };
    h *= q;
    k *= q;
    j *= r;
    l *= r;
    j = 16.0 - j;
    l = 16.0 - l;
    m *= q;
    n *= q;
    o *= r;
    p *= r;
    let mut faces: [Option<Option<Face>>; 6] = Default::default();
    faces[span.facing.direction().index()] = Some(Some(Face { cull: None, tint: layer, texture: texture.to_owned(), uvs: Some([m, o, n, p]), rotation: Quadrant(0) }));
    let (from, to) = match span.facing {
        Facing::Up => ([h, j, 7.5], [k, j, 8.5]),
        Facing::Down => ([h, l, 7.5], [k, l, 8.5]),
        Facing::Left => ([h, j, 7.5], [h, l, 8.5]),
        Facing::Right => ([k, j, 7.5], [k, l, 8.5]),
    };
    Element { from, to, faces, rotation: None, shade: true, light_emission: 0 }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn identity_rotation() -> Rotation {
        Rotation { transformation: None, cull: IDENTITY, uv_lock: [(IDENTITY, true); 6] }
    }

    #[test]
    fn bakes_a_cube_face_like_face_bakery() {
        let sprite = Sprite {
            u0: 0.0,
            u1: 0.5,
            v0: 0.0,
            v1: 0.25,
            shrink: 0.0,
            width: 16,
            height: 16,
            unique_frames: vec![1],
            row_size: 0,
            sheet: std::ptr::null(),
            sheet_width: 16,
            sheet_height: 16,
        };
        let sprites = Sprites { sprites: vec![sprite], by_name: [(Location::new("minecraft", "missingno"), 0)].into_iter().collect(), missing: 0 };
        let matrices = Matrices { rotations: vec![identity_rotation()], elements: HashMap::new() };
        let missing = Missing::default();
        let baker = Baker { sprites: &sprites, matrices: &matrices, missing: &missing };
        let model = super::super::model::missing();
        let textures: HashMap<String, Location> = [("missingno".to_owned(), Location::new("minecraft", "missingno"))].into_iter().collect();
        let quads = baker.geometry(model.geometry.as_ref(), &textures, 0, false, "minecraft:builtin/missing").unwrap();
        // Every face culls on its own direction: none unculled, one per direction.
        assert_eq!([0, 1, 1, 1, 1, 1, 1], quads.counts);
        let north = &quads.quads[0];
        assert_eq!(Direction::North, north.direction);
        // North corners: (1,1,0), (1,0,0), (0,0,0), (0,1,0); UVs (0,0), (0,1), (1,1), (1,0) of the sprite.
        assert_eq!([1.0f32, 1.0, 0.0], position(&north.vertices, 0));
        assert_eq!(0.0f32, f32::from_bits(north.vertices[4] as u32));
        assert_eq!(0.25f32, f32::from_bits(north.vertices[8 + 5] as u32));
        assert_eq!(-1, north.vertices[3]);
        assert!(missing.textures.lock().unwrap().is_empty());
    }

    /// Java's `getSpans`, searching its list for each pixel's span.
    fn java_spans(sprite: &Sprite) -> Vec<String> {
        let (width, height) = (sprite.width as i32, sprite.height as i32);
        let transparent = |frame: i32, x: i32, y: i32| x < 0 || y < 0 || x >= width || y >= height || sprite.transparent(frame, x, y).unwrap();
        let mut spans: Vec<Span> = Vec::new();
        for &frame in &sprite.unique_frames {
            for y in 0..height {
                for x in 0..width {
                    let opaque = !transparent(frame, x, y);
                    for facing in Facing::ALL {
                        let (dx, dy) = facing.offset();
                        if transparent(frame, x + dx, y + dy) && opaque {
                            let anchor = if facing.horizontal() { y } else { x };
                            let along = if facing.horizontal() { x } else { y };
                            match spans.iter_mut().find(|span| span.facing as u8 == facing as u8 && span.anchor == anchor) {
                                Some(span) if along < span.min => span.min = along,
                                Some(span) if along > span.max => span.max = along,
                                Some(_) => {}
                                None => spans.push(Span { facing, min: along, max: along, anchor }),
                            }
                        }
                    }
                }
            }
        }
        spans.iter().map(|span| format!("{span:?}")).collect()
    }

    #[test]
    fn indexed_spans_match_javas_search() {
        // A 3x2 sheet of 13x7 frames with scattered transparency; frames 4, 0, 5 unique.
        let (width, height, columns, rows) = (13u32, 7u32, 3u32, 2u32);
        let mut sheet = vec![0u8; (width * columns * height * rows * 4) as usize];
        for (index, alpha) in sheet.chunks_exact_mut(4).enumerate() {
            alpha[3] = if (index * 7919 + index / 5) % 3 == 0 { 0 } else { 255 };
        }
        let sprite = Sprite {
            u0: 0.0,
            u1: 1.0,
            v0: 0.0,
            v1: 1.0,
            shrink: 0.0,
            width,
            height,
            unique_frames: vec![4, 0, 5],
            row_size: columns,
            sheet: sheet.as_ptr(),
            sheet_width: width * columns,
            sheet_height: height * rows,
        };
        let indexed: Vec<String> = spans(&sprite).unwrap().iter().map(|span| format!("{span:?}")).collect();
        assert!(indexed.len() > 20);
        assert_eq!(java_spans(&sprite), indexed);
        // A frame past the sheet fails as `NativeImage.getPixel` does.
        let outside = Sprite { unique_frames: vec![6], ..sprite };
        assert_eq!(
            Err("java.lang.IllegalArgumentException: (0, 14) outside of image bounds (39, 14)".to_owned()),
            spans(&outside).map(|spans| spans.len())
        );
    }
}

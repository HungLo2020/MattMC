//! Frozen-identical chunk-section visibility for the camera pass.
//!
//! A port of Sodium's `OcclusionCuller` (with its `Viewport`,
//! `CameraTransform` and JOML `FrustumIntersection`) over a dense section
//! graph. Frozen Java OpenGL selects terrain this way, so every constant,
//! float operation and queue order below follows that code exactly: the set
//! of visited sections and the order they are visited in must match.
//!
//! The graph holds only "ready" sections (Sodium's `ChunkTracker`: the chunk
//! and its 8 neighbours carry block and light data); callers add and remove
//! whole columns as readiness changes and replace a section's build
//! information when a build completes.

use std::collections::HashMap;

use super::occlusion::{angle_visibility_mask, connections_for_visibility};

pub const DIRECTION_DOWN: usize = 0;
pub const DIRECTION_UP: usize = 1;
pub const DIRECTION_NORTH: usize = 2;
pub const DIRECTION_SOUTH: usize = 3;
pub const DIRECTION_WEST: usize = 4;
pub const DIRECTION_EAST: usize = 5;
pub const DIRECTION_ALL: u8 = 0x3F;

const NO_SECTION: u32 = u32::MAX;

/// Block offsets of each graph direction, indexed by `DIRECTION_*`.
const DIRECTION_OFFSETS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

const fn opposite(direction: usize) -> usize {
    direction ^ 1
}

/// `OcclusionCuller.CHUNK_SECTION_SIZE`: half-extent 8 plus one block of
/// model overhang plus the 1/8 epsilon.
pub const CHUNK_SECTION_SIZE: f32 = 8.0 + 1.0 + 0.125;
/// `OcclusionCuller.CHUNK_SECTION_SIZE_NEARBY`.
pub const CHUNK_SECTION_SIZE_NEARBY: f32 = 8.0 + 2.0 + 0.125;

/// Every from/to pair visible: the build information of an all-air section
/// (`BuiltSectionInfo.EMPTY`).
pub const VISIBILITY_ALL_PAIRS: u64 = {
    let mut data = 0u64;
    let mut from = 0;
    while from < 6 {
        let mut to = 0;
        while to < 6 {
            data |= 1u64 << (from * 8 + to);
            to += 1;
        }
        from += 1;
    }
    data
};

/// JOML `FrustumIntersection` built with plane normalisation (Minecraft's
/// `Frustum` calls `set(matrix)`, i.e. `allowTestSpheres = true`).
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Frustum {
    /// nx, px, ny, py, nz, pz planes as (x, y, z, w).
    planes: [[f32; 4]; 6],
}

impl Frustum {
    /// `m` is column-major (JOML `m<column><row>` is `m[column * 4 + row]`):
    /// the combined projection * model-view matrix.
    pub fn from_matrix(m: [f32; 16]) -> Self {
        let at = |column: usize, row: usize| m[column * 4 + row];
        let plane = |sign: f32, column: usize| -> [f32; 4] {
            // JOML: n = m?3 + m?c, p = m?3 - m?c, per row ? of the 3rd column.
            let combine = |row: usize| {
                if sign > 0.0 {
                    at(row, 3) + at(row, column)
                } else {
                    at(row, 3) - at(row, column)
                }
            };
            let mut plane = [combine(0), combine(1), combine(2), combine(3)];
            let inverse_length = 1.0f32
                / ((plane[0] * plane[0] + plane[1] * plane[1] + plane[2] * plane[2]) as f64).sqrt() as f32;
            for component in &mut plane {
                *component *= inverse_length;
            }
            plane
        };
        Self {
            planes: [plane(1.0, 0), plane(-1.0, 0), plane(1.0, 1), plane(-1.0, 1), plane(1.0, 2), plane(-1.0, 2)],
        }
    }

    /// JOML `intersectAab`: `Inside` when every plane also accepts the
    /// negative vertex, `Outside` when a plane rejects the positive vertex
    /// (JOML returns that plane's index), otherwise `Intersect`.
    pub fn intersect_aab(&self, min: [f32; 3], max: [f32; 3]) -> BoxIntersection {
        let mut inside = true;
        for plane in &self.planes {
            let pick = |coefficient: f32, low: f32, high: f32, positive: bool| {
                if (coefficient < 0.0) == positive { low } else { high }
            };
            let positive = plane[0] * pick(plane[0], min[0], max[0], true)
                + plane[1] * pick(plane[1], min[1], max[1], true)
                + plane[2] * pick(plane[2], min[2], max[2], true);
            if !(positive >= -plane[3]) {
                return BoxIntersection::Outside;
            }
            let negative = plane[0] * pick(plane[0], min[0], max[0], false)
                + plane[1] * pick(plane[1], min[1], max[1], false)
                + plane[2] * pick(plane[2], min[2], max[2], false);
            inside &= negative >= -plane[3];
        }
        if inside { BoxIntersection::Inside } else { BoxIntersection::Intersect }
    }

    /// JOML `testAab`: the box's positive vertex against every plane.
    pub fn test_aab(&self, min: [f32; 3], max: [f32; 3]) -> bool {
        self.planes.iter().all(|plane| {
            let x = if plane[0] < 0.0 { min[0] } else { max[0] };
            let y = if plane[1] < 0.0 { min[1] } else { max[1] };
            let z = if plane[2] < 0.0 { min[2] } else { max[2] };
            plane[0] * x + plane[1] * y + plane[2] * z >= -plane[3]
        })
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BoxIntersection {
    Inside,
    Intersect,
    Outside,
}

/// Sodium's `CameraTransform`: the camera split into a truncated integer
/// part and a precision-reduced fraction.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct CameraTransform {
    pub int: [i32; 3],
    pub frac: [f32; 3],
    pub position: [f64; 3],
}

impl CameraTransform {
    /// `RenderRegion.REGION_WIDTH * 16`.
    const PRECISION_MODIFIER: f32 = 8.0 * 16.0;

    pub fn new(position: [f64; 3]) -> Self {
        // Java's (int) cast truncates toward zero and saturates.
        let int = position.map(|value| value as i32);
        let frac = [0, 1, 2].map(|axis| {
            let full = (position[axis] - f64::from(int[axis])) as f32;
            let modifier = Self::PRECISION_MODIFIER.copysign(full);
            (full + modifier) - modifier
        });
        Self { int, frac, position }
    }
}

/// Sodium's `Viewport`: a frustum and the camera it is relative to.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct Viewport {
    pub frustum: Frustum,
    pub transform: CameraTransform,
    /// `SectionPos.posToSectionCoord` of the camera position.
    pub section: [i32; 3],
}

impl Viewport {
    pub fn new(frustum: Frustum, position: [f64; 3]) -> Self {
        Self {
            frustum,
            transform: CameraTransform::new(position),
            section: position.map(|value| (value.floor() as i32) >> 4),
        }
    }

    /// `Viewport.isBoxVisible` for an integer block-space box centre.
    pub fn is_box_visible(&self, origin: [i32; 3], size: f32) -> bool {
        let centre = [0, 1, 2]
            .map(|axis| origin[axis].wrapping_sub(self.transform.int[axis]) as f32 - self.transform.frac[axis]);
        self.frustum.test_aab(centre.map(|value| value - size), centre.map(|value| value + size))
    }
}

/// A section's build information (`BuiltSectionInfo`), or `None` before its
/// first build completes.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct SectionInfo {
    /// Bit 0 block geometry, bit 1 culled block entities, bit 2 animated sprites.
    pub flags: u8,
    pub visibility: u64,
}

impl SectionInfo {
    /// `BuiltSectionInfo.EMPTY`.
    pub const EMPTY: Self = Self { flags: 0, visibility: VISIBILITY_ALL_PAIRS };
}

#[derive(Clone, Debug)]
struct SectionNode {
    position: [i32; 3],
    adjacent: [u32; 6],
    adjacent_mask: u8,
    info: Option<SectionInfo>,
    last_visible_frame: u32,
    incoming: u8,
    /// `SourceState` epoch of the latest search that visited this section.
    visit_epoch: u32,
    /// Interned animated-sprite list of its accepted build (0: none).
    sprite_list: u32,
}

/// One visited section of a traversal, in visit order.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct VisitedSection {
    pub slot: u32,
    pub position: [i32; 3],
    /// `None` until its first build completes (the caller schedules it).
    pub info: Option<SectionInfo>,
}

/// The ready sections of one level and their adjacency.
#[derive(Debug)]
pub struct SectionGraph {
    min_section_y: i32,
    max_section_y: i32,
    pub(super) slots: HashMap<[i32; 3], u32, crate::render::vulkanic::gal::AccessHashBuilder>,
    /// Published layer meshes per section (see `terrain_selection`).
    pub(super) meshes: super::terrain_selection::SectionMeshTable,
    /// The latest camera selection's visits and the terrain built from them.
    pub(super) last_visits: Vec<VisitedSection>,
    pub(super) terrain: super::terrain_selection::TerrainSelection,
    /// Build, block-entity and visit bookkeeping (see `source`).
    pub(super) source: source::SourceState,
    nodes: Vec<SectionNode>,
    free: Vec<u32>,
    /// Shared per traversal; never 0 after the first traversal.
    frame: u32,
    read: Vec<u32>,
    write: Vec<u32>,
    /// Sodium's `renderableSectionTree`: unbuilt sections and built ones
    /// that render something.
    forest: SectionForest,
}

impl SectionGraph {
    pub fn new(min_section_y: i32, max_section_y: i32) -> Self {
        Self {
            min_section_y,
            max_section_y,
            slots: HashMap::default(),
            meshes: HashMap::default(),
            last_visits: Vec::new(),
            terrain: Default::default(),
            source: Default::default(),
            nodes: Vec::new(),
            free: Vec::new(),
            frame: 0,
            read: Vec::new(),
            write: Vec::new(),
            forest: SectionForest::default(),
        }
    }

    pub fn section_count(&self) -> usize {
        self.slots.len()
    }

    /// One past the highest slot index ever allocated.
    pub(super) fn slot_capacity(&self) -> usize {
        self.nodes.len()
    }

    pub fn slot(&self, position: [i32; 3]) -> Option<u32> {
        self.slots.get(&position).copied()
    }

    /// Build flags of a live slot, or `None` before its first build.
    pub(super) fn section_flags(&self, slot: u32) -> Option<u8> {
        self.nodes[slot as usize].info.map(|info| info.flags)
    }

    /// Adds every section of a newly ready column (`onChunkAdded`): all air
    /// until build information arrives, linked to existing neighbours.
    pub fn add_column(&mut self, x: i32, z: i32) {
        for y in self.min_section_y..=self.max_section_y {
            let position = [x, y, z];
            if self.slots.contains_key(&position) {
                continue;
            }
            let node = SectionNode {
                position,
                adjacent: [NO_SECTION; 6],
                adjacent_mask: 0,
                info: None,
                last_visible_frame: 0,
                incoming: 0,
                visit_epoch: 0,
                sprite_list: 0,
            };
            let slot = match self.free.pop() {
                Some(slot) => {
                    self.nodes[slot as usize] = node;
                    slot
                }
                None => {
                    self.nodes.push(node);
                    (self.nodes.len() - 1) as u32
                }
            };
            self.slots.insert(position, slot);
            self.forest.add(position);
            for direction in 0..6 {
                let offset = DIRECTION_OFFSETS[direction];
                let neighbour = [x + offset[0], y + offset[1], z + offset[2]];
                if let Some(&other) = self.slots.get(&neighbour) {
                    self.link(slot, direction, other);
                    self.link(other, opposite(direction), slot);
                }
            }
        }
    }

    /// Removes every section of a column that stopped being ready.
    pub fn remove_column(&mut self, x: i32, z: i32) {
        for y in self.min_section_y..=self.max_section_y {
            let Some(slot) = self.slots.remove(&[x, y, z]) else {
                continue;
            };
            self.forest.remove([x, y, z]);
            let adjacent = self.nodes[slot as usize].adjacent;
            for (direction, other) in adjacent.into_iter().enumerate() {
                if other != NO_SECTION {
                    self.unlink(other, opposite(direction));
                }
            }
            self.free.push(slot);
        }
    }

    /// Replaces a section's build information (`RenderSection.setInfo`).
    pub fn set_info(&mut self, position: [i32; 3], info: Option<SectionInfo>) -> bool {
        match self.slots.get(&position) {
            Some(&slot) => {
                self.nodes[slot as usize].info = info;
                // `updateSectionInfo`: built sections leave the tree unless
                // they render something.
                if info.is_none_or(|info| info.flags != 0) {
                    self.forest.add(position);
                } else {
                    self.forest.remove(position);
                }
                true
            }
            None => false,
        }
    }

    fn link(&mut self, slot: u32, direction: usize, other: u32) {
        let node = &mut self.nodes[slot as usize];
        node.adjacent[direction] = other;
        node.adjacent_mask |= 1 << direction;
    }

    fn unlink(&mut self, slot: u32, direction: usize) {
        let node = &mut self.nodes[slot as usize];
        node.adjacent[direction] = NO_SECTION;
        node.adjacent_mask &= !(1 << direction);
    }

    /// Frozen's camera-pass selection (`createTerrainRenderList`): the
    /// occlusion traversal, or the frustum-only section tree when the camera
    /// section is inside the level but not yet built (`isOutOfGraph`).
    pub fn select_camera_sections(
        &mut self,
        viewport: &Viewport,
        search_distance: f32,
        use_occlusion_culling: bool,
        visit: impl FnMut(VisitedSection),
    ) {
        if self.is_out_of_graph(viewport.section) {
            self.traverse_tree(viewport, visit);
        } else {
            self.find_visible(viewport, search_distance, use_occlusion_culling, visit);
        }
    }

    fn is_out_of_graph(&self, section: [i32; 3]) -> bool {
        if section[1] < self.min_section_y || section[1] > self.max_section_y {
            return false;
        }
        self.slot(section).is_none_or(|slot| self.nodes[slot as usize].info.is_none())
    }

    /// `RemovableMultiForest.traverse`: trees nearest the camera first, each
    /// front to back, frustum-tested without a distance limit.
    pub fn traverse_tree(&mut self, viewport: &Viewport, mut visit: impl FnMut(VisitedSection)) {
        self.forest.prepare();
        let camera = viewport.transform.int.map(|value| value >> 4);
        let mut order = self
            .forest
            .order
            .iter()
            .map(|key| {
                let tree = &self.forest.trees[key];
                let key_distance = (0..3).map(|axis| (tree.offset[axis] + 32 - camera[axis]).abs()).sum::<i32>() + 1;
                (key_distance, *key)
            })
            .collect::<Vec<_>>();
        order.sort_by_key(|(distance, _)| *distance);
        for (_, key) in order {
            let tree = &self.forest.trees[&key];
            let mut sections = Vec::new();
            tree.traverse(viewport, |position| sections.push(position));
            for position in sections {
                if let Some(slot) = self.slot(position) {
                    visit(self.visited(slot));
                }
            }
        }
    }

    /// `OcclusionCuller.findVisible` followed by its nearby-section pass.
    /// `visit` receives sections in exactly Frozen's visit order.
    pub fn find_visible(
        &mut self,
        viewport: &Viewport,
        search_distance: f32,
        use_occlusion_culling: bool,
        mut visit: impl FnMut(VisitedSection),
    ) {
        self.frame = self.frame.wrapping_add(1).max(1);
        let frame = self.frame;
        let mut read = std::mem::take(&mut self.read);
        let mut write = std::mem::take(&mut self.write);
        read.clear();
        write.clear();
        self.init(&mut write, viewport, search_distance, use_occlusion_culling, frame, &mut visit);
        // DoubleBufferedQueue: strict waves, each read in enqueue order.
        while !write.is_empty() {
            std::mem::swap(&mut read, &mut write);
            write.clear();
            for index in 0..read.len() {
                let slot = read[index];
                self.process(slot, viewport, search_distance, use_occlusion_culling, frame, &mut write, &mut visit);
            }
        }
        self.add_nearby_sections(viewport, frame, &mut visit);
        self.read = read;
        self.write = write;
    }

    fn visited(&self, slot: u32) -> VisitedSection {
        let node = &self.nodes[slot as usize];
        VisitedSection { slot, position: node.position, info: node.info }
    }

    #[allow(clippy::too_many_arguments)]
    fn process(
        &mut self,
        slot: u32,
        viewport: &Viewport,
        search_distance: f32,
        use_occlusion_culling: bool,
        frame: u32,
        write: &mut Vec<u32>,
        visit: &mut impl FnMut(VisitedSection),
    ) {
        if !self.is_section_visible(slot, viewport, search_distance) {
            return;
        }
        visit(self.visited(slot));
        let node = &self.nodes[slot as usize];
        let mut connections = if use_occlusion_culling {
            let visibility = node.info.map_or(0, |info| info.visibility);
            let centre = node.position.map(|value| (value << 4) + 8);
            let masked = visibility
                & angle_visibility_mask(
                    viewport.transform.position[0] - f64::from(centre[0]),
                    viewport.transform.position[1] - f64::from(centre[1]),
                    viewport.transform.position[2] - f64::from(centre[2]),
                );
            connections_for_visibility(masked, i32::from(node.incoming), true) as u8
        } else {
            DIRECTION_ALL
        };
        connections &= outward_directions(viewport.section, node.position);
        self.visit_neighbors(write, slot, connections, frame);
    }

    fn visit_neighbors(&mut self, write: &mut Vec<u32>, slot: u32, outgoing: u8, frame: u32) {
        let node = &self.nodes[slot as usize];
        let outgoing = outgoing & node.adjacent_mask;
        if outgoing == 0 {
            return;
        }
        let adjacent = node.adjacent;
        // Frozen's fixed order: down, up, north, south, west, east.
        for direction in 0..6 {
            if outgoing & (1 << direction) != 0 {
                self.visit_node(write, adjacent[direction], 1 << opposite(direction), frame);
            }
        }
    }

    fn visit_node(&mut self, write: &mut Vec<u32>, slot: u32, incoming: u8, frame: u32) {
        let node = &mut self.nodes[slot as usize];
        if node.last_visible_frame != frame {
            node.last_visible_frame = frame;
            node.incoming = 0;
            write.push(slot);
        }
        node.incoming |= incoming;
    }

    fn is_section_visible(&self, slot: u32, viewport: &Viewport, max_distance: f32) -> bool {
        let position = self.nodes[slot as usize].position;
        is_within_render_distance(&viewport.transform, position, max_distance)
            && viewport.is_box_visible(position.map(|value| (value << 4) + 8), CHUNK_SECTION_SIZE)
    }

    fn init(
        &mut self,
        write: &mut Vec<u32>,
        viewport: &Viewport,
        search_distance: f32,
        use_occlusion_culling: bool,
        frame: u32,
        visit: &mut impl FnMut(VisitedSection),
    ) {
        let origin = viewport.section;
        if origin[1] < self.min_section_y {
            self.init_outside_world_height(write, viewport, search_distance, frame, self.min_section_y, DIRECTION_DOWN);
        } else if origin[1] > self.max_section_y {
            self.init_outside_world_height(write, viewport, search_distance, frame, self.max_section_y, DIRECTION_UP);
        } else {
            let Some(slot) = self.slot(origin) else {
                return;
            };
            let node = &mut self.nodes[slot as usize];
            node.last_visible_frame = frame;
            node.incoming = 0;
            visit(self.visited(slot));
            let node = &self.nodes[slot as usize];
            let outgoing = if use_occlusion_culling {
                connections_for_visibility(node.info.map_or(0, |info| info.visibility), 0, false) as u8
            } else {
                DIRECTION_ALL
            };
            self.visit_neighbors(write, slot, outgoing, frame);
        }
    }

    /// Diamond-spiral seeding of the boundary layer when the camera is above
    /// or below the level (innermost layer first, N->W->S->E within a layer).
    fn init_outside_world_height(
        &mut self,
        write: &mut Vec<u32>,
        viewport: &Viewport,
        search_distance: f32,
        frame: u32,
        height: i32,
        direction: usize,
    ) {
        let [ox, _, oz] = viewport.section;
        let radius = (search_distance / 16.0).floor() as i32;
        let mut seed = |graph: &mut Self, x: i32, z: i32| graph.try_visit_node(write, [ox + x, height, oz + z], direction, frame, viewport);
        seed(self, 0, 0);
        for layer in 1..=radius {
            for z in -layer..layer {
                seed(self, z.abs() - layer, z);
            }
            let mut z = layer;
            while z > -layer {
                seed(self, layer - z.abs(), z);
                z -= 1;
            }
        }
        for layer in (radius + 1)..=(2 * radius) {
            let l = layer - radius;
            for z in -radius..=-l {
                seed(self, -z - layer, z);
            }
            for z in l..=radius {
                seed(self, z - layer, z);
            }
            let mut z = radius;
            while z >= l {
                seed(self, layer - z, z);
                z -= 1;
            }
            let mut z = -l;
            while z >= -radius {
                seed(self, layer + z, z);
                z -= 1;
            }
        }
    }

    fn try_visit_node(&mut self, write: &mut Vec<u32>, position: [i32; 3], direction: usize, frame: u32, viewport: &Viewport) {
        let Some(slot) = self.slot(position) else {
            return;
        };
        if !viewport.is_box_visible(position.map(|value| (value << 4) + 8), CHUNK_SECTION_SIZE) {
            return;
        }
        self.visit_node(write, slot, 1 << direction, frame);
    }

    /// Visits the 26 neighbours of the camera section that the traversal did
    /// not reach but whose enlarged box is in the frustum (large models).
    fn add_nearby_sections(&mut self, viewport: &Viewport, frame: u32, visit: &mut impl FnMut(VisitedSection)) {
        let [ox, oy, oz] = viewport.section;
        for dx in -1..=1 {
            for dy in -1..=1 {
                for dz in -1..=1 {
                    if dx == 0 && dy == 0 && dz == 0 {
                        continue;
                    }
                    let position = [ox + dx, oy + dy, oz + dz];
                    let Some(slot) = self.slot(position) else {
                        continue;
                    };
                    if self.nodes[slot as usize].last_visible_frame != frame
                        && viewport.is_box_visible(position.map(|value| (value << 4) + 8), CHUNK_SECTION_SIZE_NEARBY)
                    {
                        self.nodes[slot as usize].last_visible_frame = frame;
                        visit(self.visited(slot));
                    }
                }
            }
        }
    }
}

/// Sodium's `RemovableMultiForest` of 64^3-section trees.
#[derive(Debug, Default)]
struct SectionForest {
    trees: HashMap<[i32; 3], SectionTree>,
    /// Insertion order (`Long2ReferenceLinkedOpenHashMap`).
    order: Vec<[i32; 3]>,
}

impl SectionForest {
    fn add(&mut self, position: [i32; 3]) {
        let key = position.map(|value| value >> 6);
        let tree = self.trees.entry(key).or_insert_with(|| {
            self.order.push(key);
            SectionTree::new(key.map(|value| value << 6))
        });
        tree.set(position, true);
    }

    fn remove(&mut self, position: [i32; 3]) {
        if let Some(tree) = self.trees.get_mut(&position.map(|value| value >> 6)) {
            tree.set(position, false);
        }
    }

    /// `prepareForTraversal`: rebuilds reductions and drops empty trees.
    fn prepare(&mut self) {
        let trees = &mut self.trees;
        self.order.retain(|key| {
            let tree = trees.get_mut(key).expect("ordered trees exist");
            tree.prepare();
            if tree.double_reduced == 0 {
                trees.remove(key);
                false
            } else {
                true
            }
        });
    }
}

/// Sodium's `TraversableTree`: a Morton-interleaved presence bitmap.
#[derive(Debug)]
struct SectionTree {
    offset: [i32; 3],
    tree: Box<[u64; 64 * 64]>,
    reduced: [u64; 64],
    double_reduced: u64,
    reduced_valid: bool,
}

const INSIDE_FRUSTUM: u32 = 0b01;
const INSIDE_DISTANCE: u32 = 0b10;
const FULLY_INSIDE: u32 = INSIDE_FRUSTUM | INSIDE_DISTANCE;
/// `OcclusionCuller.CHUNK_SECTION_MARGIN`.
const CHUNK_SECTION_MARGIN: f32 = 1.0 + 0.125;
/// `OcclusionCuller.CHUNK_SECTION_RADIUS`.
const CHUNK_SECTION_RADIUS: f32 = 8.0;

fn interleave6(n: i32) -> i32 {
    let mut n = n & 0b111111;
    n = (n | n << 4 | n << 8) & 0b000011000011000011;
    (n | n << 2) & 0b001001001001001001
}

fn deinterleave6(n: i32) -> i32 {
    let mut n = n & 0b001001001001001001;
    n = (n | n >> 2) & 0b000011000011000011;
    (n | n >> 4 | n >> 8) & 0b111111
}

fn interleave6x3(x: i32, y: i32, z: i32) -> i32 {
    interleave6(x) | interleave6(y) << 1 | interleave6(z) << 2
}

struct TreeTraversal<'a, F> {
    tree: &'a SectionTree,
    viewport: &'a Viewport,
    camera_offset: [i32; 3],
    visit: F,
}

impl SectionTree {
    fn new(offset: [i32; 3]) -> Self {
        Self { offset, tree: Box::new([0; 64 * 64]), reduced: [0; 64], double_reduced: 0, reduced_valid: true }
    }

    fn set(&mut self, position: [i32; 3], present: bool) {
        let [x, y, z] = [0, 1, 2].map(|axis| position[axis] - self.offset[axis]);
        if !(0..64).contains(&x) || !(0..64).contains(&y) || !(0..64).contains(&z) {
            return;
        }
        let bit = interleave6x3(x, y, z) as usize;
        if present {
            self.tree[bit >> 6] |= 1 << (bit & 63);
        } else {
            self.tree[bit >> 6] &= !(1 << (bit & 63));
        }
        self.reduced_valid = false;
    }

    fn prepare(&mut self) {
        if self.reduced_valid {
            return;
        }
        let mut double_reduced = 0u64;
        for i in 0..64 {
            let mut reduced = 0u64;
            for j in 0..64 {
                if self.tree[(i << 6) + j] != 0 {
                    reduced |= 1 << j;
                }
            }
            self.reduced[i] = reduced;
            if reduced != 0 {
                double_reduced |= 1 << i;
            }
        }
        self.double_reduced = double_reduced;
        self.reduced_valid = true;
    }

    fn traverse(&self, viewport: &Viewport, visit: impl FnMut([i32; 3])) {
        let camera_offset = [0, 1, 2].map(|axis| viewport.section[axis] - self.offset[axis] + 1);
        let mut traversal = TreeTraversal { tree: self, viewport, camera_offset, visit };
        // The forest disables the distance test (distance limit 0).
        let modulator = traversal.child_order_modulator(0, 0, 0, 1 << 5);
        traversal.traverse(modulator, 0, 5, INSIDE_DISTANCE);
    }
}

impl<F: FnMut([i32; 3])> TreeTraversal<'_, F> {
    fn child_order_modulator(&self, x: i32, y: i32, z: i32, child_full_section_dim: i32) -> i32 {
        let sign = |value: i32| ((value as u32) >> 31) as i32;
        sign(x + child_full_section_dim - self.camera_offset[0])
            | sign(y + child_full_section_dim - self.camera_offset[1]) << 1
            | sign(z + child_full_section_dim - self.camera_offset[2]) << 2
    }

    fn traverse(&mut self, mut order_modulator: i32, node_origin: i32, level: i32, inside: u32) {
        let child_half_dim = 1 << (level + 3);
        if level & 1 == 1 {
            order_modulator <<= 3;
        }
        let tree = self.tree;
        if level <= 1 {
            let child_origin_base = node_origin & 0b111111_111111_000000;
            let map = tree.tree[(node_origin >> 6) as usize];
            if level == 0 {
                let start = node_origin & 0b111111;
                for bit in start..start + 8 {
                    let child = bit ^ order_modulator;
                    if map & (1u64 << child) != 0 {
                        let origin = child_origin_base | child;
                        let position = [
                            deinterleave6(origin) + tree.offset[0],
                            deinterleave6(origin >> 1) + tree.offset[1],
                            deinterleave6(origin >> 2) + tree.offset[2],
                        ];
                        if inside == FULLY_INSIDE || self.test_leaf(position, inside) {
                            (self.visit)(position);
                        }
                    }
                }
            } else {
                for bit in (0..64).step_by(8) {
                    let child = bit ^ order_modulator;
                    if map & (0xFFu64 << child) != 0 {
                        self.test_child(child_origin_base | child, child_half_dim, level, inside);
                    }
                }
            }
        } else if level <= 3 {
            let child_origin_base = node_origin & 0b111111_000000_000000;
            let map = tree.reduced[(node_origin >> 12) as usize];
            if level == 2 {
                let start = (node_origin >> 6) & 0b111111;
                for bit in start..start + 8 {
                    let child = bit ^ order_modulator;
                    if map & (1u64 << child) != 0 {
                        self.test_child(child_origin_base | (child << 6), child_half_dim, level, inside);
                    }
                }
            } else {
                for bit in (0..64).step_by(8) {
                    let child = bit ^ order_modulator;
                    if map & (0xFFu64 << child) != 0 {
                        self.test_child(child_origin_base | (child << 6), child_half_dim, level, inside);
                    }
                }
            }
        } else if level == 4 {
            let start = node_origin >> 12;
            for bit in start..start + 8 {
                let child = bit ^ order_modulator;
                if tree.double_reduced & (1u64 << child) != 0 {
                    self.test_child(child << 12, child_half_dim, level, inside);
                }
            }
        } else {
            for bit in (0..64).step_by(8) {
                let child = bit ^ order_modulator;
                if tree.double_reduced & (0xFFu64 << child) != 0 {
                    self.test_child(child << 12, child_half_dim, level, inside);
                }
            }
        }
    }

    fn test_child(&mut self, child_origin: i32, child_half_dim: i32, level: i32, mut inside: u32) {
        let x = deinterleave6(child_origin);
        let y = deinterleave6(child_origin >> 1);
        let z = deinterleave6(child_origin >> 2);
        let level = level - 1;
        if inside == FULLY_INSIDE {
            let modulator = self.child_order_modulator(x, y, z, 1 << level);
            self.traverse(modulator, child_origin, level, inside);
            return;
        }
        let transform = &self.viewport.transform;
        let mut visible = true;
        if inside & INSIDE_FRUSTUM == 0 {
            let local = [x, y, z];
            let centre = [0, 1, 2].map(|axis| {
                let world = ((local[axis] + self.tree.offset[axis]) << 4).wrapping_sub(transform.int[axis]);
                (world + child_half_dim) as f32 - transform.frac[axis]
            });
            let size = child_half_dim as f32 + CHUNK_SECTION_MARGIN;
            match self
                .viewport
                .frustum
                .intersect_aab(centre.map(|value| value - size), centre.map(|value| value + size))
            {
                BoxIntersection::Inside => inside |= INSIDE_FRUSTUM,
                result => visible = result == BoxIntersection::Intersect,
            }
        }
        // The distance bits are always set: the forest traverses without a limit.
        if visible {
            let modulator = self.child_order_modulator(x, y, z, 1 << level);
            self.traverse(modulator, child_origin, level, inside);
        }
    }

    fn test_leaf(&self, position: [i32; 3], inside: u32) -> bool {
        if inside & INSIDE_FRUSTUM != 0 {
            return true;
        }
        let transform = &self.viewport.transform;
        let centre = [0, 1, 2].map(|axis| {
            ((position[axis] << 4).wrapping_sub(transform.int[axis]) + 8) as f32 - transform.frac[axis]
        });
        self.viewport.frustum.test_aab(
            centre.map(|value| value - CHUNK_SECTION_RADIUS),
            centre.map(|value| value + CHUNK_SECTION_RADIUS),
        )
    }
}

fn outward_directions(origin: [i32; 3], position: [i32; 3]) -> u8 {
    let mut planes = 0u8;
    if position[0] <= origin[0] {
        planes |= 1 << DIRECTION_WEST;
    }
    if position[0] >= origin[0] {
        planes |= 1 << DIRECTION_EAST;
    }
    if position[1] <= origin[1] {
        planes |= 1 << DIRECTION_DOWN;
    }
    if position[1] >= origin[1] {
        planes |= 1 << DIRECTION_UP;
    }
    if position[2] <= origin[2] {
        planes |= 1 << DIRECTION_NORTH;
    }
    if position[2] >= origin[2] {
        planes |= 1 << DIRECTION_SOUTH;
    }
    planes
}

/// Vanilla's cylindrical fog test against the section box grown by one block.
fn is_within_render_distance(camera: &CameraTransform, position: [i32; 3], max_distance: f32) -> bool {
    let nearest_to_zero = |min: i32, max: i32| {
        let mut clamped = 0;
        if min > 0 {
            clamped = min;
        }
        if max < 0 {
            clamped = max;
        }
        clamped
    };
    let delta = [0, 1, 2].map(|axis| {
        let origin = (position[axis] << 4).wrapping_sub(camera.int[axis]);
        nearest_to_zero(origin - 1, origin + 17) as f32 - camera.frac[axis]
    });
    (delta[0] * delta[0] + delta[2] * delta[2]) < max_distance * max_distance && delta[1].abs() < max_distance
}

#[cfg(test)]
mod tests;

mod source;
pub use source::BuildCompletion;

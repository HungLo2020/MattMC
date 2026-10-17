//! C ABI for model reloads (`NativeModels`).
//!
//! A reload is a boxed `Mutex<Session>` behind a `u64` handle: `load`
//! (model files and blockstates), `groups`, `discover` (with Java's item
//! roots; returns the element rotations to compute), then `bake` (with the
//! block atlas sprites and JOML matrices). Records are little-endian with
//! u32-length texts.

use std::collections::HashMap;
use std::sync::Mutex;

use super::bake::{ElementMatrix, Matrices, Missing, Rotation, Sprite, Sprites};
use super::blockstate::{Kind, Property, States};
use super::geometry::Matrix;
use super::job::{self, BakedRoot, Definitions, ItemRoot, Loaded, Node, Properties};
use super::model::GuiLight;
use super::resolve::{Discovery, TransformRef};
use crate::assets::ffi_util::{bytes_arg, give, Reader, Writer};
use crate::assets::resources::ffi::with_manager;
use crate::content::tags::Location;

/// One reload's state.
pub(crate) struct Session {
    definitions: Definitions,
    loaded: Loaded,
    discovery: Option<Discovery>,
    item_roots: Vec<Location>,
}

unsafe fn session<'a>(handle: u64) -> Option<&'a Mutex<Session>> {
    (handle != 0).then(|| unsafe { &*(handle as *const Mutex<Session>) })
}

fn lock(session: &Mutex<Session>) -> std::sync::MutexGuard<'_, Session> {
    session.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn location(reader: &mut Reader<'_>) -> Option<Location> {
    Some(Location::new(reader.text()?, reader.text()?))
}

/// State definitions: u32 count; per definition its id (namespace, path),
/// owner text, u32 properties (name, kind u8: 0 boolean, 1 integer,
/// 2 enum, values text, property text, u32-counted value names), then
/// u32 states, each a u16 value index per property.
fn definitions(bytes: &[u8]) -> Option<Definitions> {
    let mut reader = Reader::new(bytes);
    let count = reader.u32()? as usize;
    let mut result = Definitions { by_id: HashMap::with_capacity(count), states: Vec::with_capacity(count), offsets: Vec::with_capacity(count) };
    let mut offset = 0;
    for index in 0..count {
        let id = location(&mut reader)?;
        let owner = reader.text()?.to_owned();
        let mut properties = Vec::new();
        for _ in 0..reader.u32()? {
            let name = reader.text()?.to_owned();
            let kind = match reader.u8()? {
                0 => Kind::Boolean,
                1 => Kind::Integer,
                _ => Kind::Enum,
            };
            let values_text = reader.text()?.to_owned();
            let text = reader.text()?.to_owned();
            let values = (0..reader.u32()?).map(|_| reader.text().map(str::to_owned)).collect::<Option<Vec<_>>>()?;
            properties.push(Property { name, kind, values, values_text, text });
        }
        let states = (0..reader.u32()?).map(|_| (0..properties.len()).map(|_| reader.u16()).collect::<Option<Vec<_>>>()).collect::<Option<Vec<_>>>()?;
        result.offsets.push(offset);
        offset += states.len();
        result.by_id.insert(id, index);
        result.states.push(States { owner, properties, states });
    }
    reader.done().then_some(result)
}

/// Loads model files and blockstates. Returns the session, or 0 on
/// malformed definitions.
///
/// # Safety
/// `manager` live for the call; `records` valid for its length.
#[no_mangle]
pub unsafe extern "C" fn mattmc_models_load(manager: u64, records: *const u8, records_len: u64) -> u64 {
    let Some(definitions) = (unsafe { bytes_arg(records, records_len) }).and_then(definitions) else { return 0 };
    let loaded = unsafe { with_manager(manager, |manager, filters| job::load(manager, filters, &definitions)) };
    match loaded {
        Some(loaded) => Box::into_raw(Box::new(Mutex::new(Session { definitions, loaded, discovery: None, item_roots: Vec::new() }))) as u64,
        None => 0,
    }
}

/// Each state's model group (i32, -1 without a root), in definition order.
///
/// # Safety
/// `handle` live; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_models_groups(handle: u64, out_ptr: *mut *mut u8, out_len: *mut u64) {
    let mut writer = Writer::default();
    if let Some(session) = unsafe { session(handle) } {
        let session = lock(session);
        for group in job::groups(&session.loaded, &session.definitions) {
            writer.i32(group);
        }
    }
    unsafe { give(writer.bytes, out_ptr, out_len) };
}

/// Discovers the models states and item roots reach (`roots`: u32 count of
/// namespace and path pairs, in Java's request order). Writes the element
/// rotations baking needs: u32 count of (axis u32, angle bits u32).
///
/// # Safety
/// `handle` live; `roots` valid; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_models_discover(handle: u64, roots: *const u8, roots_len: u64, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
    let (Some(session), Some(roots)) = (unsafe { session(handle) }, unsafe { bytes_arg(roots, roots_len) }) else { return -1 };
    let mut reader = Reader::new(roots);
    let Some(item_roots) = reader.u32().and_then(|count| (0..count).map(|_| location(&mut reader)).collect::<Option<Vec<_>>>()) else { return -1 };
    let mut session = lock(session);
    let discovery = job::discover(&session.loaded, &item_roots);
    let mut writer = Writer::default();
    let rotations = job::element_rotations(&discovery);
    writer.u32(rotations.len() as u32);
    for (axis, angle) in rotations {
        writer.u32(axis);
        writer.u32(angle);
    }
    session.discovery = Some(discovery);
    session.item_roots = item_roots;
    unsafe { give(writer.bytes, out_ptr, out_len) };
    0
}

fn matrix(reader: &mut Reader<'_>) -> Option<Matrix> {
    let mut values = [0.0f32; 16];
    for value in values.iter_mut() {
        *value = reader.f32()?;
    }
    Some(Matrix(values))
}

/// Sprites: u32 count; per sprite namespace, path, u0, u1, v0, v1, shrink
/// (f32), width, height, frames per row (u32), u32-counted unique frames
/// (i32), sheet pointer (u64), sheet width and height; then the missing
/// sprite's index.
fn sprites(reader: &mut Reader<'_>) -> Option<Sprites> {
    let count = reader.u32()? as usize;
    let mut sprites = Vec::with_capacity(count);
    let mut by_name = HashMap::with_capacity(count);
    for index in 0..count {
        let name = location(reader)?;
        let sprite = Sprite {
            u0: reader.f32()?,
            u1: reader.f32()?,
            v0: reader.f32()?,
            v1: reader.f32()?,
            shrink: reader.f32()?,
            width: reader.u32()?,
            height: reader.u32()?,
            row_size: reader.u32()?,
            unique_frames: (0..reader.u32()?).map(|_| reader.i32()).collect::<Option<Vec<_>>>()?,
            sheet: reader.u64()? as *const u8,
            sheet_width: reader.u32()?,
            sheet_height: reader.u32()?,
        };
        by_name.insert(name, index as u32);
        sprites.push(sprite);
    }
    let missing = reader.u32()?;
    ((missing as usize) < sprites.len()).then_some(Sprites { sprites, by_name, missing })
}

/// Matrices: 16 rotations, each a flag (u8, 1 when the transformation is
/// not `Transformation.identity()`), the transformation matrix, then per
/// direction the UV-lock inverse matrix and its identity flag (u8); then
/// u32-counted element rotations (axis u32, angle bits u32, matrix,
/// rescale f32). Matrices are 16 f32 in `Matrix4f.get` order.
fn matrices(reader: &mut Reader<'_>) -> Option<Matrices> {
    let mut rotations = Vec::with_capacity(16);
    for _ in 0..16 {
        let transformed = reader.u8()? != 0;
        let transformation = matrix(reader)?;
        let mut uv_lock = [(transformation, true); 6];
        for slot in uv_lock.iter_mut() {
            let inverse = matrix(reader)?;
            *slot = (inverse, reader.u8()? != 0);
        }
        rotations.push(Rotation { transformation: transformed.then_some(transformation), cull: transformation, uv_lock });
    }
    let mut elements = HashMap::new();
    for _ in 0..reader.u32()? {
        let key = (reader.u32()?, reader.u32()?);
        let matrix = matrix(reader)?;
        elements.insert(key, ElementMatrix { matrix, rescale: reader.f32()? });
    }
    Some(Matrices { rotations, elements })
}

fn write_node(writer: &mut Writer, node: &Node) {
    match node {
        Node::Single(part) => {
            writer.u8(0);
            writer.u32(*part);
        }
        Node::Weighted(entries) => {
            writer.u8(1);
            writer.u32(entries.len() as u32);
            for (part, weight) in entries {
                writer.u32(*part);
                writer.i32(*weight);
            }
        }
    }
}

/// Transform objects in first-use order.
#[derive(Default)]
struct TransformTable {
    order: Vec<(u32, u8)>,
    index: HashMap<(u32, u8), u32>,
}

impl TransformTable {
    fn id(&mut self, model: u32, value: u8) -> u32 {
        let next = self.order.len() as u32;
        *self.index.entry((model, value)).or_insert_with(|| {
            self.order.push((model, value));
            next
        })
    }
}

fn write_properties(writer: &mut Writer, properties: &Properties, transforms: &mut TransformTable) {
    writer.u32(properties.geometry);
    writer.u8(u8::from(properties.ambient_occlusion));
    writer.u8(match properties.gui_light {
        GuiLight::Front => 0,
        GuiLight::Side => 1,
    });
    writer.u32(properties.particle);
    for transform in &properties.transforms {
        match *transform {
            TransformRef::None => writer.u8(0),
            TransformRef::Null => writer.u8(1),
            TransformRef::Value { model, index } => {
                writer.u8(2);
                writer.u32(transforms.id(model, index));
            }
        }
    }
}

/// Bakes every used root and item root (`input`: sprites, then matrices).
/// Writes geometries (u32 count; per geometry 7 u32 bucket counts, then
/// quads: 32 i32 vertices, tint i32, light face u8, sprite u32, shade u8,
/// light emission i32; preceded by each geometry's u64 offset past that
/// table and the section's u64 length), parts (u32 count of geometry u32, ambient
/// occlusion u8, particle u32), roots (u32 count; kind u8: 0 unused,
/// 1 simple node, 2 u32-counted multipart nodes, 3 failure text; a node is
/// 0 + part, or 1 + u32-counted (part, weight)), states (u32 count; root
/// i32, then for a root u32-counted selector indices, none for a simple
/// root), transform objects (u32 count of 9 f32: rotation, translation,
/// scale), the missing model's properties, and item roots (u32 count;
/// kind u8: 0 properties then u8 1 + failure text when their geometry
/// failed (else 0) and its geometry and particle reports, or 1
/// undiscovered), then the block states' and missing model's reports.
/// Reports are missing textures (u32 count of model text and u32-counted
/// atlas and texture texts) then missing references (u32 count of model
/// text and u32-counted names). Properties are
/// geometry u32, ambient occlusion u8, gui light u8 (0 front, 1 side),
/// particle u32, then 9 transforms (u8: 0 none, 1 null, 2 + object u32).
/// Returns 0, or -1 on malformed input or a session not yet discovered.
///
/// # Safety
/// `handle` live; `input` valid, its sheet pointers readable for their
/// sizes during the call; outputs writable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_models_bake(handle: u64, input: *const u8, input_len: u64, out_ptr: *mut *mut u8, out_len: *mut u64) -> i32 {
    let (Some(session), Some(input)) = (unsafe { session(handle) }, unsafe { bytes_arg(input, input_len) }) else { return -1 };
    let mut reader = Reader::new(input);
    let (Some(sprites), Some(matrices)) = (sprites(&mut reader), matrices(&mut reader)) else { return -1 };
    let session = lock(session);
    let Some(discovery) = &session.discovery else { return -1 };
    let baked = job::bake(&session.loaded, discovery, &session.item_roots, &sprites, &matrices);

    // Quads are most of the output: size the buffer for them up front.
    const QUAD_BYTES: usize = 32 * 4 + 4 + 1 + 4 + 1 + 4;
    let quads: usize = baked.geometries.iter().map(|geometry| geometry.quads.len()).sum();
    let mut writer = Writer { bytes: Vec::with_capacity(quads * QUAD_BYTES + baked.geometries.len() * 28 + (1 << 20)) };
    writer.u32(baked.geometries.len() as u32);
    // Each geometry's offset past the table, then the section's length, so
    // Java can read geometries in parallel.
    let mut offset = 0u64;
    for geometry in &baked.geometries {
        writer.u64(offset);
        offset += 7 * 4 + geometry.quads.len() as u64 * QUAD_BYTES as u64;
    }
    writer.u64(offset);
    let section = writer.bytes.len();
    for geometry in &baked.geometries {
        for count in geometry.counts {
            writer.u32(count);
        }
        for quad in &geometry.quads {
            let mut vertices = [0u8; 32 * 4];
            for (bytes, vertex) in vertices.chunks_exact_mut(4).zip(quad.vertices) {
                bytes.copy_from_slice(&vertex.to_le_bytes());
            }
            writer.bytes.extend_from_slice(&vertices);
            writer.i32(quad.tint);
            writer.u8(quad.direction as u8);
            writer.u32(quad.sprite);
            writer.u8(u8::from(quad.shade));
            writer.i32(quad.light_emission);
        }
    }
    debug_assert_eq!(offset as usize, writer.bytes.len() - section);
    writer.u32(baked.parts.len() as u32);
    for part in &baked.parts {
        writer.u32(part.geometry);
        writer.u8(u8::from(part.ambient_occlusion));
        writer.u32(part.particle);
    }
    writer.u32(baked.roots.len() as u32);
    for root in &baked.roots {
        match root {
            None => writer.u8(0),
            Some(BakedRoot::Simple(node)) => {
                writer.u8(1);
                write_node(&mut writer, node);
            }
            Some(BakedRoot::Multipart(nodes)) => {
                writer.u8(2);
                writer.u32(nodes.len() as u32);
                for node in nodes {
                    write_node(&mut writer, node);
                }
            }
            Some(BakedRoot::Failed(message)) => {
                writer.u8(3);
                writer.text(message);
            }
        }
    }
    writer.u32(session.loaded.state_roots.len() as u32);
    for (definition, states) in session.definitions.states.iter().enumerate() {
        let offset = session.definitions.offsets[definition];
        for state in 0..states.states.len() {
            match session.loaded.state_roots[offset + state] {
                None => writer.i32(-1),
                Some(root) => {
                    writer.i32(root as i32);
                    let selectors = session.loaded.selectors.get(&root).map(|(_, selectors)| &selectors[state][..]).unwrap_or(&[]);
                    writer.u32(selectors.len() as u32);
                    for &selector in selectors {
                        writer.u32(selector);
                    }
                }
            }
        }
    }
    // Transform objects first, so properties can name them.
    let mut transforms = TransformTable::default();
    let properties = std::iter::once(&baked.missing).chain(baked.items.iter().filter_map(|item| match item {
        ItemRoot::Baked(properties, _) => Some(properties),
        ItemRoot::Undiscovered => None,
    }));
    for properties in properties {
        for transform in &properties.transforms {
            if let TransformRef::Value { model, index } = *transform {
                transforms.id(model, index);
            }
        }
    }
    writer.u32(transforms.order.len() as u32);
    for &(model, value) in &transforms.order {
        let transform = discovery.wrappers[model as usize].model.transforms.as_ref().map(|transforms| transforms.values[value as usize]);
        let transform = transform.expect("a transform reference names a declared transform");
        for value in transform.rotation.into_iter().chain(transform.translation).chain(transform.scale) {
            writer.f32(value);
        }
    }
    write_properties(&mut writer, &baked.missing, &mut transforms);
    writer.u32(baked.items.len() as u32);
    for (item, reports) in baked.items.iter().zip(&baked.item_reports) {
        match item {
            ItemRoot::Baked(properties, failure) => {
                writer.u8(0);
                write_properties(&mut writer, properties, &mut transforms);
                match failure {
                    Some(message) => {
                        writer.u8(1);
                        writer.text(message);
                    }
                    None => writer.u8(0),
                }
                write_reports(&mut writer, &reports.geometry);
                write_reports(&mut writer, &reports.particle);
            }
            ItemRoot::Undiscovered => writer.u8(1),
        }
    }
    write_reports(&mut writer, &baked.reports);
    unsafe { give(writer.bytes, out_ptr, out_len) };
    0
}

fn write_reports(writer: &mut Writer, reports: &Missing) {
    let textures = reports.textures.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    writer.u32(textures.len() as u32);
    for (model, entries) in textures.iter() {
        writer.text(model);
        writer.u32(entries.len() as u32);
        for (atlas, texture) in entries {
            writer.text(atlas);
            writer.text(texture);
        }
    }
    let references = reports.references.lock().unwrap_or_else(|poisoned| poisoned.into_inner());
    writer.u32(references.len() as u32);
    for (model, names) in references.iter() {
        writer.text(model);
        writer.u32(names.len() as u32);
        for name in names {
            writer.text(name);
        }
    }
}

/// Frees a reload.
///
/// # Safety
/// `handle` from `mattmc_models_load`, released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_models_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Mutex<Session>) });
    }
}

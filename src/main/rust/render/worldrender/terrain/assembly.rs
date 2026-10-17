//! Terrain layer assembly: turns one decoded section layer (see
//! [`super::intake`]) into its world-mesh asset payload. Opaque and cutout
//! layers get one quad index range per vertex segment; translucent layers take
//! the build sorter's order (made global when it is facing-local), drop
//! primitives without a material contract, classify water and split ranges by
//! material/texture. Also computes the content identity (`mesh_generation`)
//! and the atlas-scoped `mesh_key`.
//!
//! Semantics match Java's former `RustGalTerrainRenderer.decodeMesh` /
//! `buildOrderedTranslucentMesh` / `meshGeneration` bit for bit: identical
//! rebuilds keep their generation, so retained plans and caches stay valid.

use crate::render::scene::material::{
    WORLD_MATERIAL_ID_CUTOUT_TEXTURED, WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
    WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED, WORLD_MATERIAL_ID_WATER_TRANSLUCENT,
    WORLD_MATERIAL_MODE_CUTOUT, WORLD_MATERIAL_MODE_OPAQUE, WORLD_MATERIAL_MODE_TRANSLUCENT,
};
use crate::render::scene::mesh::{WorldMeshSection, WorldMeshVertex, WORLD_CULL_BACK, WORLD_WINDING_CCW};
use crate::render::scene::textures::{
    WORLD_MATERIAL_TEXTURE_WATER_FLOW, WORLD_MATERIAL_TEXTURE_WATER_OVERLAY,
    WORLD_MATERIAL_TEXTURE_WATER_STILL, WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
};

/// `ChunkSectionLayer` ordinals that change assembly.
pub const LAYER_SOLID: u32 = 0;
pub const LAYER_TRANSLUCENT: u32 = 3;

pub const INDEX_TYPE_U16: u32 = 1;
pub const INDEX_TYPE_U32: u32 = 2;

/// Primitive metadata kinds (`NativeSectionMeshBuilder.PRIMITIVE_KIND_*`).
pub const PRIMITIVE_KIND_UNKNOWN: i32 = 0;
pub const PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT: i32 = 1;
pub const PRIMITIVE_KIND_BUILTIN_WATER: i32 = 2;
pub const PRIMITIVE_KIND_GENERIC_FLUID: i32 = 3;
pub const PRIMITIVE_KIND_UNSUPPORTED_FLUID: i32 = 4;

/// Unassigned source facing: the range is always visible.
const FACING_UNASSIGNED: u32 = 6;
/// Retained per-primitive translucent samples (enough for Java's 512-char receipt).
pub const PRIMITIVE_SAMPLE_LIMIT: usize = 64;

/// One built-in water sprite's atlas rectangle.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct FfiWaterSprite {
    pub texture_id: u32,
    pub u0: f32,
    pub u1: f32,
    pub v0: f32,
    pub v1: f32,
}

impl FfiWaterSprite {
    fn contains(&self, u: f32, v: f32) -> bool {
        let min_u = self.u0.min(self.u1) - 0.00001;
        let max_u = self.u0.max(self.u1) + 0.00001;
        let min_v = self.v0.min(self.v1) - 0.00001;
        let max_v = self.v0.max(self.v1) + 0.00001;
        u >= min_u && u <= max_u && v >= min_v && v <= max_v
    }

    fn local_u(&self, u: f32) -> f32 {
        (u - self.u0) / (self.u1 - self.u0)
    }

    fn local_v(&self, v: f32) -> f32 {
        (v - self.v0) / (self.v1 - self.v0)
    }
}

#[derive(Clone, Copy, Debug)]
pub struct LayerAssemblyParams {
    pub section_pos: i64,
    pub layer_ordinal: u32,
    pub atlas_generation: i64,
    /// Water samples the block atlas (whole-frame route) instead of its own sheets.
    pub water_block_atlas: bool,
    /// Still, flow and overlay sprites; required once a layer has built-in water.
    pub water: Option<[FfiWaterSprite; 3]>,
}

/// One translucent primitive's fate, for the diagnostic receipt.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct PrimitiveSample {
    pub primitive_id: u32,
    pub primitive_kind: i32,
    pub retained: bool,
    pub material_id: u32,
    pub texture_id: u32,
    pub retained_index_start: u32,
}

/// Translucent primitive accounting (Java's `OrderedTranslucentMesh` receipt).
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct TranslucentAccounting {
    pub source_primitives: u32,
    pub non_fluid_primitives: u32,
    pub water_primitives: u32,
    pub unsupported_primitives: u32,
    pub retained_primitives: u32,
    pub omitted_primitives: u32,
    pub source_indices: u32,
    pub retained_indices: u32,
    pub omitted_indices: u32,
    pub source_hash: u64,
    pub retained_hash: u64,
    pub omitted_hash: u64,
    pub material_switches: u32,
    pub water_still: u32,
    pub water_flow: u32,
    pub water_overlay: u32,
    pub water_texture_switches: u32,
    pub samples: Vec<PrimitiveSample>,
}

#[derive(Clone, Debug)]
pub struct AssembledLayer {
    pub index_type: u32,
    pub index_bytes: Vec<u8>,
    /// Indices of the assembled quads before translucent omission.
    pub assembled_index_count: u32,
    pub sections: Vec<WorldMeshSection>,
    pub max_index: i32,
    pub positive_y_sections: u32,
    pub negative_y_sections: u32,
    pub horizontal_sections: u32,
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub translucent: Option<TranslucentAccounting>,
}

impl AssembledLayer {
    /// A translucent layer whose every primitive was omitted draws nothing.
    pub fn is_empty(&self) -> bool {
        self.sections.is_empty()
    }
}

/// Assembles `vertices` (decoded, `segments` = `[vertex count, facing]`
/// pairs, `metadata` = 10 ints per quad) with the build sorter's
/// `sorted_indices` (u32 quads; empty when the layer had no sorter output).
/// Water quads are rewritten in place (shader material type, and local UVs
/// when water samples its own sheets), as before.
pub fn assemble_layer(
    vertices: &mut [WorldMeshVertex],
    segments: &[i32],
    metadata: &[i32],
    sorted_indices: &[u8],
    params: &LayerAssemblyParams,
) -> Result<AssembledLayer, String> {
    let vertex_count = vertices.len();
    if segments.len() % 2 != 0 {
        return Err("static terrain vertex segment array length is odd".into());
    }
    let translucent = params.layer_ordinal == LAYER_TRANSLUCENT;
    let mut indices: Vec<u32> = Vec::with_capacity((vertex_count / 4 * 6).max(6));
    let mut sections = Vec::new();
    let mut cursor = 0usize;
    let mut max_index = -1i32;
    let (mut positive_y, mut negative_y, mut horizontal) = (0u32, 0u32, 0u32);
    for pair in segments.chunks_exact(2) {
        let count = pair[0];
        if count <= 0 {
            continue;
        }
        let count = count as usize;
        if cursor + count > vertex_count {
            return Err("static terrain vertex segments exceed vertex payload".into());
        }
        let first_index = indices.len();
        let facing = pair[1];
        match facing {
            1 => positive_y += 1,
            4 => negative_y += 1,
            0 | 2 | 3 | 5 => horizontal += 1,
            _ => {}
        }
        let mut quad = cursor;
        while quad + 3 < cursor + count {
            let base = quad as u32;
            indices.extend_from_slice(&[base, base + 1, base + 2, base + 2, base + 3, base]);
            max_index = max_index.max(base as i32 + 3);
            quad += 4;
        }
        if !translucent {
            sections.push(WorldMeshSection {
                material_id: if params.layer_ordinal == LAYER_SOLID {
                    WORLD_MATERIAL_ID_OPAQUE_TEXTURED
                } else {
                    WORLD_MATERIAL_ID_CUTOUT_TEXTURED
                },
                texture_id: WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
                material_mode: if params.layer_ordinal == LAYER_SOLID {
                    WORLD_MATERIAL_MODE_OPAQUE
                } else {
                    WORLD_MATERIAL_MODE_CUTOUT
                },
                cull_policy: WORLD_CULL_BACK,
                winding: WORLD_WINDING_CCW,
                index_offset: (first_index * 2) as u32,
                index_count: (indices.len() - first_index) as u32,
                source_facing: facing as u32,
            });
        }
        cursor += count;
    }
    if cursor != vertex_count {
        return Err(format!("static terrain vertex segments cover {cursor} of {vertex_count} vertices"));
    }
    let assembled_index_count = indices.len() as u32;
    let (index_type, index_bytes, accounting) = if translucent {
        let segment_quads = translucent_source_segment_quad_counts(segments)?;
        let mut source = normalize_sorted_indices(sorted_indices, vertex_count, &segment_quads)?;
        if source.is_empty() {
            source = pack_u32(&indices);
        }
        let ordered = order_translucent(&source, metadata, vertices, params)?;
        sections = ordered.sections;
        (INDEX_TYPE_U32, ordered.index_bytes, Some(ordered.accounting))
    } else {
        (INDEX_TYPE_U16, pack_u16(&indices), None)
    };
    if sections.is_empty() && !translucent {
        return Err("static terrain mesh has no drawable sections".into());
    }
    let mesh_key = mesh_key(params.section_pos, params.layer_ordinal, params.atlas_generation);
    let mesh_generation = mesh_generation(params.section_pos, params.layer_ordinal, vertices, &index_bytes, &sections);
    Ok(AssembledLayer {
        index_type,
        index_bytes,
        assembled_index_count,
        sections,
        max_index,
        positive_y_sections: positive_y,
        negative_y_sections: negative_y,
        horizontal_sections: horizontal,
        mesh_key,
        mesh_generation,
        translucent: accounting,
    })
}

struct OrderedTranslucent {
    index_bytes: Vec<u8>,
    sections: Vec<WorldMeshSection>,
    accounting: TranslucentAccounting,
}

fn translucent_material(kind: i32) -> Result<u32, String> {
    match kind {
        PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT | PRIMITIVE_KIND_GENERIC_FLUID => Ok(WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED),
        PRIMITIVE_KIND_BUILTIN_WATER => Ok(WORLD_MATERIAL_ID_WATER_TRANSLUCENT),
        PRIMITIVE_KIND_UNSUPPORTED_FLUID => Ok(0),
        _ => Err(format!("unsupported translucent primitive kind {kind}")),
    }
}

fn water_shader_material_type(texture_id: u32) -> i32 {
    match texture_id {
        WORLD_MATERIAL_TEXTURE_WATER_STILL => 1,
        WORLD_MATERIAL_TEXTURE_WATER_FLOW => 2,
        WORLD_MATERIAL_TEXTURE_WATER_OVERLAY => 3,
        _ => 0,
    }
}

/// Classifies one water quad by its UVs (still, then overlay, then flow) and
/// rewrites its material type and, for separate sheets, its local UVs.
fn bind_water_primitive(
    vertices: &mut [WorldMeshVertex],
    primitive: usize,
    params: &LayerAssemblyParams,
) -> Result<u32, String> {
    let base = primitive * 4;
    if base + 3 >= vertices.len() {
        return Err(format!("water primitive {primitive} exceeds vertex payload"));
    }
    let [still, flow, overlay] = params
        .water
        .ok_or_else(|| "water animation texture assets have not been initialized".to_string())?;
    let quad = &vertices[base..base + 4];
    let within = |sprite: &FfiWaterSprite| quad.iter().all(|vertex| sprite.contains(vertex.uv[0], vertex.uv[1]));
    let sprite = if within(&still) {
        still
    } else if within(&overlay) {
        overlay
    } else if within(&flow) {
        flow
    } else {
        return Err("built-in water primitive UVs do not match still, flow, or overlay sprites".into());
    };
    for vertex in &mut vertices[base..base + 4] {
        if !params.water_block_atlas {
            vertex.uv = [
                sprite.local_u(vertex.uv[0]).clamp(0.0, 1.0),
                sprite.local_v(vertex.uv[1]).clamp(0.0, 1.0),
            ];
        }
        vertex.shader_material_type = water_shader_material_type(sprite.texture_id);
    }
    Ok(if params.water_block_atlas { WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS } else { sprite.texture_id })
}

fn close_range(sections: &mut Vec<WorldMeshSection>, material: u32, texture: u32, start: u32, current: u32) {
    if material == 0 || current <= start {
        return;
    }
    sections.push(WorldMeshSection {
        material_id: material,
        texture_id: if texture == 0 { WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS } else { texture },
        material_mode: WORLD_MATERIAL_MODE_TRANSLUCENT,
        cull_policy: WORLD_CULL_BACK,
        winding: WORLD_WINDING_CCW,
        index_offset: start * 4,
        index_count: current - start,
        source_facing: FACING_UNASSIGNED,
    });
}

fn order_translucent(
    source: &[u8],
    metadata: &[i32],
    vertices: &mut [WorldMeshVertex],
    params: &LayerAssemblyParams,
) -> Result<OrderedTranslucent, String> {
    const QUAD_BYTES: usize = 24;
    let vertex_count = vertices.len();
    if source.is_empty() || source.len() % QUAD_BYTES != 0 {
        return Err("translucent sorted index payload must contain whole u32 quads".into());
    }
    let primitive_count = vertex_count / 4;
    let stride = 10usize;
    if metadata.len() != primitive_count * stride {
        return Err(format!(
            "translucent primitive metadata count {} does not match primitive count {primitive_count}",
            metadata.len()
        ));
    }
    let mut retained = Vec::with_capacity(source.len());
    let mut omitted = Vec::new();
    let mut sections = Vec::new();
    let mut accounting = TranslucentAccounting::default();
    let (mut open_material, mut open_texture, mut open_start) = (0u32, 0u32, 0u32);
    let mut retained_index_count = 0u32;
    let (mut previous_material, mut previous_texture) = (0u32, 0u32);
    let mut seen = vec![false; primitive_count];
    for quad in source.chunks_exact(QUAD_BYTES) {
        let primitive = primitive_from_sorted_quad(quad, vertex_count)?;
        if primitive >= primitive_count {
            return Err(format!(
                "translucent sorted payload references primitive {primitive} outside 0..{}",
                primitive_count as i64 - 1
            ));
        }
        if seen[primitive] {
            return Err(format!("translucent sorted payload references primitive {primitive} more than once"));
        }
        seen[primitive] = true;
        let mut kind = metadata[primitive * stride];
        // Flat translucent quads carry no fluid record (kind UNKNOWN).
        if kind == PRIMITIVE_KIND_UNKNOWN {
            kind = PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT;
        }
        match kind {
            PRIMITIVE_KIND_NON_FLUID_TRANSLUCENT | PRIMITIVE_KIND_GENERIC_FLUID => accounting.non_fluid_primitives += 1,
            PRIMITIVE_KIND_BUILTIN_WATER => accounting.water_primitives += 1,
            PRIMITIVE_KIND_UNSUPPORTED_FLUID => accounting.unsupported_primitives += 1,
            _ => {}
        }
        let material = translucent_material(kind)?;
        let mut sample = PrimitiveSample {
            primitive_id: primitive as u32,
            primitive_kind: kind,
            retained: material != 0,
            material_id: 0,
            texture_id: 0,
            retained_index_start: retained_index_count,
        };
        if material == 0 {
            close_range(&mut sections, open_material, open_texture, open_start, retained_index_count);
            open_material = 0;
            open_texture = 0;
            open_start = retained_index_count;
            omitted.extend_from_slice(quad);
            accounting.omitted_primitives += 1;
            if accounting.samples.len() < PRIMITIVE_SAMPLE_LIMIT {
                accounting.samples.push(sample);
            }
            continue;
        }
        let texture = if kind == PRIMITIVE_KIND_BUILTIN_WATER {
            let texture = bind_water_primitive(vertices, primitive, params)?;
            match vertices[primitive * 4].shader_material_type {
                1 => accounting.water_still += 1,
                2 => accounting.water_flow += 1,
                3 => accounting.water_overlay += 1,
                _ => {}
            }
            if previous_texture != 0 && previous_texture != texture {
                accounting.water_texture_switches += 1;
            }
            texture
        } else {
            WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS
        };
        if open_material != material || open_texture != texture {
            close_range(&mut sections, open_material, open_texture, open_start, retained_index_count);
            if previous_material != 0 && previous_material != material {
                accounting.material_switches += 1;
            }
            previous_material = material;
            previous_texture = texture;
            open_material = material;
            open_texture = texture;
            open_start = retained_index_count;
        }
        retained.extend_from_slice(quad);
        retained_index_count += 6;
        accounting.retained_primitives += 1;
        if accounting.samples.len() < PRIMITIVE_SAMPLE_LIMIT {
            sample.material_id = material;
            sample.texture_id = texture;
            accounting.samples.push(sample);
        }
    }
    close_range(&mut sections, open_material, open_texture, open_start, retained_index_count);
    if let Some(missing) = seen.iter().position(|seen| !seen) {
        return Err(format!("translucent sorted payload omitted primitive {missing}"));
    }
    accounting.source_primitives = primitive_count as u32;
    accounting.source_indices = (source.len() / 4) as u32;
    accounting.retained_indices = retained_index_count;
    accounting.omitted_indices = (omitted.len() / 4) as u32;
    accounting.source_hash = sorted_index_hash(source);
    accounting.retained_hash = sorted_index_hash(&retained);
    accounting.omitted_hash = sorted_index_hash(&omitted);
    Ok(OrderedTranslucent { index_bytes: retained, sections, accounting })
}

fn read_u32(bytes: &[u8], offset: usize) -> u32 {
    u32::from_le_bytes([bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]])
}

/// The primitive of one sorted u32 quad `(b, b+1, b+2, b+2, b+3, b)`.
fn primitive_from_sorted_quad(quad: &[u8], vertex_count: usize) -> Result<usize, String> {
    let index = |slot: usize| read_u32(quad, slot * 4) as i32;
    let i0 = index(0);
    if (i0 & 3) != 0
        || index(1) != i0.wrapping_add(1)
        || index(2) != i0.wrapping_add(2)
        || index(3) != i0.wrapping_add(2)
        || index(4) != i0.wrapping_add(3)
        || index(5) != i0
    {
        return Err("translucent sorted payload contains an interleaved or malformed primitive".into());
    }
    let last = index(4);
    if last < 0 || last as usize >= vertex_count {
        return Err(format!("translucent sorted payload references vertex {last} but vertex count is {vertex_count}"));
    }
    Ok(i0 as usize / 4)
}

fn translucent_source_segment_quad_counts(segments: &[i32]) -> Result<Vec<usize>, String> {
    let mut counts = Vec::with_capacity(segments.len() / 2);
    for pair in segments.chunks_exact(2) {
        let vertices = pair[0];
        if vertices < 0 || vertices % 4 != 0 {
            return Err("translucent source vertex segment is not quad-aligned".into());
        }
        if vertices > 0 {
            counts.push(vertices as usize / 4);
        }
    }
    Ok(counts)
}

fn unique_global_primitives(bytes: &[u8], vertex_count: usize) -> bool {
    let mut seen = vec![false; vertex_count / 4];
    for quad in bytes.chunks_exact(24) {
        match primitive_from_sorted_quad(quad, vertex_count) {
            Ok(primitive) if !seen[primitive] => seen[primitive] = true,
            _ => return false,
        }
    }
    seen.iter().all(|present| *present)
}

/// The sorter may emit facing-local quad indices; rebase them onto the
/// layer's global vertex numbering. Empty input stays empty.
fn normalize_sorted_indices(source: &[u8], vertex_count: usize, segment_quads: &[usize]) -> Result<Vec<u8>, String> {
    if source.is_empty() {
        return Ok(Vec::new());
    }
    if source.len() % 24 != 0 || vertex_count % 4 != 0 {
        return Err("translucent sorted index payload has an invalid quad layout".into());
    }
    if source.len() / 24 != vertex_count / 4 {
        return Err("translucent sorted index payload count does not match copied terrain vertices".into());
    }
    if unique_global_primitives(source, vertex_count) {
        return Ok(source.to_vec());
    }
    if segment_quads.iter().sum::<usize>() != vertex_count / 4 {
        return Err("translucent source segments do not cover copied terrain primitives".into());
    }
    let mut normalized = vec![0u8; source.len()];
    let mut offset = 0usize;
    let mut global_base = 0u32;
    for &quads in segment_quads {
        let mut seen_local = vec![false; quads];
        let segment_vertices = quads * 4;
        for _ in 0..quads {
            let quad = &source[offset..offset + 24];
            let local = primitive_from_sorted_quad(quad, segment_vertices)?;
            if seen_local[local] {
                return Err(format!("translucent facing-local payload references primitive {local} more than once"));
            }
            seen_local[local] = true;
            for slot in 0..6 {
                let value = read_u32(quad, slot * 4).wrapping_add(global_base);
                normalized[offset + slot * 4..offset + slot * 4 + 4].copy_from_slice(&value.to_le_bytes());
            }
            offset += 24;
        }
        global_base += segment_vertices as u32;
    }
    if !unique_global_primitives(&normalized, vertex_count) {
        return Err("normalized translucent sorted payload is not globally complete".into());
    }
    Ok(normalized)
}

fn pack_u16(indices: &[u32]) -> Vec<u8> {
    indices.iter().flat_map(|index| (*index as u16).to_le_bytes()).collect()
}

fn pack_u32(indices: &[u32]) -> Vec<u8> {
    indices.iter().flat_map(|index| index.to_le_bytes()).collect()
}

const FNV_OFFSET: u64 = 0xcbf2_9ce4_8422_2325;
const FNV_PRIME: u64 = 0x0000_0100_0000_01b3;

fn fnv64(value: &str) -> u64 {
    value.encode_utf16().fold(FNV_OFFSET, |hash, unit| (hash ^ unit as u64).wrapping_mul(FNV_PRIME))
}

fn fnv64_int(mut hash: u64, value: i32) -> u64 {
    for byte in (value as u32).to_le_bytes() {
        hash = (hash ^ byte as u64).wrapping_mul(FNV_PRIME);
    }
    hash
}

fn fnv64_long(hash: u64, value: i64) -> u64 {
    fnv64_int(fnv64_int(hash, value as i32), (value as u64 >> 32) as i32)
}

fn fnv64_bytes(hash: u64, bytes: &[u8]) -> u64 {
    bytes
        .iter()
        .fold(fnv64_int(hash, bytes.len() as i32), |hash, byte| (hash ^ *byte as u64).wrapping_mul(FNV_PRIME))
}

/// Java's `sortedIndexHash`: FNV over the length and bytes.
pub fn sorted_index_hash(bytes: &[u8]) -> u64 {
    fnv64_bytes(fnv64("static-terrain-translucent-sort-bytes-v1"), bytes)
}

/// Atlas-scoped identity of one section layer: a replacement atlas's meshes
/// coexist with the current ones while they upload.
pub fn mesh_key(section_pos: i64, layer_ordinal: u32, atlas_generation: i64) -> u64 {
    let mut hash = fnv64("static-terrain-section-v2");
    hash = fnv64_long(hash, section_pos);
    hash = fnv64_int(hash, layer_ordinal as i32);
    hash = fnv64_long(hash, atlas_generation);
    if hash == 0 { 1 } else { hash }
}

fn mix64(hash: u64, value: u64) -> u64 {
    let hash = (hash ^ value).wrapping_mul(0x9e37_79b9_7f4a_7c15);
    hash ^ (hash >> 29)
}

fn pair(high: u32, low: u32) -> u64 {
    ((high as u64) << 32) ^ low as u64
}

/// Content identity of one assembled layer; an identical rebuild keeps it.
pub fn mesh_generation(
    section_pos: i64,
    layer_ordinal: u32,
    vertices: &[WorldMeshVertex],
    index_bytes: &[u8],
    sections: &[WorldMeshSection],
) -> u64 {
    let mut hash = fnv64("static-terrain-generation-v2");
    hash = mix64(hash, section_pos as u64);
    hash = mix64(hash, layer_ordinal as u64);
    hash = mix64(hash, vertices.len() as u64);
    for vertex in vertices {
        hash = mix64(hash, pair(vertex.position[0].to_bits(), vertex.position[1].to_bits()));
        hash = mix64(hash, pair(vertex.position[2].to_bits(), vertex.uv[0].to_bits()));
        hash = mix64(hash, pair(vertex.uv[1].to_bits(), vertex.shader_atlas_uv[0].to_bits()));
        hash = mix64(hash, pair(vertex.shader_atlas_uv[1].to_bits(), vertex.shader_block_id as u32));
        hash = mix64(hash, pair(vertex.shader_material_type as u32, vertex.color_argb));
        hash = mix64(hash, pair(vertex.normal_packed, vertex.light));
        // Java mixes the int widened to long (sign-extended).
        hash = mix64(hash, vertex.mid_block_packed as i32 as i64 as u64);
    }
    hash = mix64(hash, sections.len() as u64);
    for section in sections {
        hash = mix64(hash, pair(section.material_id, section.texture_id));
        hash = mix64(hash, pair(section.material_mode, section.cull_policy));
        hash = mix64(hash, pair(section.winding, section.index_offset));
        hash = mix64(hash, pair(section.index_count, section.source_facing));
    }
    hash = mix64(hash, index_bytes.len() as u64);
    let mut words = index_bytes.chunks_exact(8);
    for word in &mut words {
        hash = mix64(hash, u64::from_le_bytes(word.try_into().expect("eight bytes")));
    }
    for byte in words.remainder() {
        hash = mix64(hash, *byte as u64);
    }
    if hash == 0 { 1 } else { hash }
}

#[cfg(test)]
mod tests;

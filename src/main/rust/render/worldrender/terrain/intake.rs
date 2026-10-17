//! Terrain mesh intake: decodes a section layer's compact Sodium vertices
//! (as Rust meshing assembled them) straight into world-mesh vertices,
//! with the same semantics as Java's `RustGalTerrainRenderer.decodeMesh`:
//! segment normals, AO/colour/light decoding, copied-atlas UV shrink, the
//! canonical block identity from the primitive metadata, mid-block packing,
//! and the diagnostic fault injections. Java no longer builds a record per
//! vertex or encodes one field at a time. The C export that writes the
//! vertex ABI lives in `bridge/world/terrain_intake.rs`.

use crate::render::scene::mesh::WorldMeshVertex;

const POSITION_MAX_VALUE: f32 = (1 << 20) as f32;
const TEXTURE_MAX_VALUE: f32 = (1 << 15) as f32;
const COMPACT_TEXTURE_SUB_TEXEL_PRECISION: f32 = (1 << 8) as f32;
const COMPACT_PREFIX_STRIDE: usize = 20;
const POSITION_OFFSET: usize = 0;
const COLOR_OFFSET: usize = 8;
const TEXTURE_OFFSET: usize = 12;
const LIGHT_MATERIAL_OFFSET: usize = 16;

/// Fault injections of the static-terrain audit (`activeFault()`).
pub const FAULT_INVERTED_AO: u32 = 1;
pub const FAULT_DOUBLED_FACE_SHADE: u32 = 2;
pub const FAULT_SWAPPED_BLOCK_SKY_LIGHT: u32 = 4;
pub const FAULT_INVERTED_NORMAL: u32 = 8;
pub const FAULT_WRONG_TOP_FACE_SHADE: u32 = 16;

#[repr(C)]
#[derive(Clone, Copy, Debug)]
pub struct FfiCompactTerrainDecodeParams {
    pub vertex_stride: u32,
    pub separate_ao: u32,
    pub atlas_width: u32,
    pub atlas_height: u32,
    /// Byte offset of a packed mid-block word in each vertex; 0 derives it
    /// from the primitive metadata.
    pub mid_block_offset: u32,
    pub fault_bits: u32,
    /// Ints per primitive metadata record.
    pub metadata_stride: u32,
    pub reserved: u32,
}

/// Bounds and AO receipts of a decode (Java's asset diagnostics).
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct FfiCompactTerrainDecodeStats {
    pub min_position: [f32; 3],
    pub max_position: [f32; 3],
    pub min_uv: [f32; 2],
    pub max_uv: [f32; 2],
    pub min_ao: f32,
    pub max_ao: f32,
    pub separate_ao_vertices: u32,
    pub ao_contract_valid: u32,
}

#[derive(Debug, PartialEq)]
pub enum DecodeError {
    Invalid(&'static str),
}

fn word(buffer: &[u8], offset: usize) -> u32 {
    u32::from_ne_bytes(buffer[offset..offset + 4].try_into().expect("four bytes"))
}

fn decode_position(hi: u32, lo: u32, component: u32) -> f32 {
    let shift = component * 10;
    let value = (((hi >> shift) & 0x3ff) << 10) | ((lo >> shift) & 0x3ff);
    value as f32 / POSITION_MAX_VALUE * 32.0 - 8.0
}

fn decode_texture_for_copied_atlas(packed: u32, atlas_extent: u32) -> f32 {
    let base = (packed & 0x7fff) as f32 / TEXTURE_MAX_VALUE;
    let direction = if packed & 0x8000 == 0 { -1.0 } else { 1.0 };
    let shrink = (1.0 / TEXTURE_MAX_VALUE) - (1.0 / (atlas_extent as f32 * COMPACT_TEXTURE_SUB_TEXEL_PRECISION));
    base + direction * shrink
}

fn multiply_color_byte(color: u32, factor: u32) -> u32 {
    ((color * factor + 255) >> 8).min(255)
}

fn decode_color(compact_abgr: u32, separate_ao: bool, invert_ao: bool, double_shade: bool) -> u32 {
    let mut alpha_or_ao = (compact_abgr >> 24) & 0xff;
    let mut blue = (compact_abgr >> 16) & 0xff;
    let mut green = (compact_abgr >> 8) & 0xff;
    let mut red = compact_abgr & 0xff;
    let mut alpha = alpha_or_ao;
    if separate_ao {
        if invert_ao {
            alpha_or_ao = 255 - alpha_or_ao;
        }
        if double_shade {
            red = multiply_color_byte(red, alpha_or_ao);
            green = multiply_color_byte(green, alpha_or_ao);
            blue = multiply_color_byte(blue, alpha_or_ao);
        }
        alpha = alpha_or_ao;
    }
    (alpha << 24) | (red << 16) | (green << 8) | blue
}

fn multiply_argb_rgb(argb: u32, factor: u32) -> u32 {
    (argb & 0xff00_0000)
        | (multiply_color_byte((argb >> 16) & 0xff, factor) << 16)
        | (multiply_color_byte((argb >> 8) & 0xff, factor) << 8)
        | multiply_color_byte(argb & 0xff, factor)
}

fn decode_light(light_material: u32, swap: bool) -> u32 {
    let (mut block, mut sky) = (light_material & 0xff, (light_material >> 8) & 0xff);
    if swap {
        std::mem::swap(&mut block, &mut sky);
    }
    block | (sky << 16)
}

/// Java `Math.round(float)`.
fn java_round(value: f32) -> i32 {
    (value + 0.5).floor() as i32
}

fn pack_normal(x: f32, y: f32, z: f32) -> u32 {
    let component = |value: f32| (java_round(value * 127.0).clamp(-127, 127) & 0xff) as u32;
    component(x) | (component(y) << 8) | (component(z) << 16)
}

fn unpack_normal_component(packed: u32, shift: u32) -> f32 {
    ((packed >> shift) & 0xff) as u8 as i8 as f32 / 127.0
}

fn computed_normal(a: [f32; 3], b: [f32; 3], c: [f32; 3]) -> u32 {
    let (ax, ay, az) = (b[0] - a[0], b[1] - a[1], b[2] - a[2]);
    let (bx, by, bz) = (c[0] - a[0], c[1] - a[1], c[2] - a[2]);
    let nx = ay * bz - az * by;
    let ny = az * bx - ax * bz;
    let nz = ax * by - ay * bx;
    let length = f64::from(nx * nx + ny * ny + nz * nz).sqrt() as f32;
    if length <= 0.00001 {
        return pack_normal(0.0, 1.0, 0.0);
    }
    pack_normal(nx / length, ny / length, nz / length)
}

fn segment_normal(buffer: &[u8], stride: usize, start: usize, count: usize, facing: i32) -> u32 {
    match facing {
        0 => pack_normal(1.0, 0.0, 0.0),
        1 => pack_normal(0.0, 1.0, 0.0),
        2 => pack_normal(0.0, 0.0, 1.0),
        3 => pack_normal(-1.0, 0.0, 0.0),
        4 => pack_normal(0.0, -1.0, 0.0),
        5 => pack_normal(0.0, 0.0, -1.0),
        _ if count >= 3 => {
            let point = |vertex: usize| {
                let offset = (start + vertex) * stride + POSITION_OFFSET;
                let (hi, lo) = (word(buffer, offset), word(buffer, offset + 4));
                [0, 1, 2].map(|axis| decode_position(hi, lo, axis))
            };
            computed_normal(point(0), point(1), point(2))
        }
        _ => pack_normal(0.0, 1.0, 0.0),
    }
}

/// Decodes `segments` (`[vertex count, facing]` pairs) of `buffer` into
/// `out`, one world-mesh vertex per compact vertex. Returns the vertex count.
pub fn decode_compact_terrain_vertices(
    buffer: &[u8],
    params: &FfiCompactTerrainDecodeParams,
    segments: &[i32],
    metadata: &[i32],
    out: &mut Vec<WorldMeshVertex>,
    stats: &mut FfiCompactTerrainDecodeStats,
) -> Result<usize, DecodeError> {
    let stride = params.vertex_stride as usize;
    let separate_ao = params.separate_ao != 0;
    let faults = params.fault_bits;
    let metadata_stride = params.metadata_stride as usize;
    if stride < COMPACT_PREFIX_STRIDE {
        return Err(DecodeError::Invalid("static terrain vertex stride is smaller than the compact prefix"));
    }
    if buffer.len() % stride != 0 {
        return Err(DecodeError::Invalid("static terrain vertex buffer length is not aligned to its stride"));
    }
    if params.atlas_width == 0 || params.atlas_height == 0 {
        return Err(DecodeError::Invalid("copied terrain atlas has invalid dimensions"));
    }
    if segments.len() % 2 != 0 {
        return Err(DecodeError::Invalid("static terrain vertex segment array length is odd"));
    }
    let mut vertex_count = 0usize;
    for pair in segments.chunks_exact(2) {
        if pair[0] <= 0 {
            continue;
        }
        if pair[0] % 4 != 0 {
            return Err(DecodeError::Invalid("static terrain vertex segment is not quad-aligned"));
        }
        vertex_count += pair[0] as usize;
    }
    if vertex_count == 0 || vertex_count > 0xffff {
        return Err(DecodeError::Invalid("unsupported static terrain vertex count"));
    }
    if vertex_count > buffer.len() / stride {
        return Err(DecodeError::Invalid("static terrain vertex segments exceed the vertex buffer"));
    }
    if metadata_stride < 10 || metadata.len() != vertex_count / 4 * metadata_stride {
        return Err(DecodeError::Invalid("static terrain primitive metadata does not match the assembled primitives"));
    }
    let mid_block_offset = params.mid_block_offset as usize;
    if mid_block_offset != 0 && mid_block_offset + 4 > stride {
        return Err(DecodeError::Invalid("static terrain mid-block offset exceeds the vertex stride"));
    }
    out.clear();
    out.reserve(vertex_count);
    *stats = FfiCompactTerrainDecodeStats {
        min_position: [f32::INFINITY; 3],
        max_position: [f32::NEG_INFINITY; 3],
        min_uv: [f32::INFINITY; 2],
        max_uv: [f32::NEG_INFINITY; 2],
        min_ao: 1.0,
        max_ao: 0.0,
        separate_ao_vertices: 0,
        ao_contract_valid: 1,
    };
    let invert_ao = faults & FAULT_INVERTED_AO != 0;
    let double_shade = faults & FAULT_DOUBLED_FACE_SHADE != 0;
    let swap_light = faults & FAULT_SWAPPED_BLOCK_SKY_LIGHT != 0;
    if invert_ao || double_shade {
        stats.ao_contract_valid = 0;
    }
    let mut segment_start = 0usize;
    for pair in segments.chunks_exact(2) {
        let count = pair[0];
        if count <= 0 {
            continue;
        }
        let count = count as usize;
        let facing = pair[1];
        let mut normal = segment_normal(buffer, stride, segment_start, count, facing);
        if faults & FAULT_INVERTED_NORMAL != 0 {
            normal = pack_normal(
                -unpack_normal_component(normal, 0),
                -unpack_normal_component(normal, 8),
                -unpack_normal_component(normal, 16),
            );
        }
        let top_face = facing == 1;
        for vertex in segment_start..segment_start + count {
            let offset = vertex * stride;
            let (hi, lo) = (word(buffer, offset + POSITION_OFFSET), word(buffer, offset + POSITION_OFFSET + 4));
            let compact_color = word(buffer, offset + COLOR_OFFSET);
            let mut color = decode_color(compact_color, separate_ao, invert_ao, double_shade);
            let texture = word(buffer, offset + TEXTURE_OFFSET);
            let light_material = word(buffer, offset + LIGHT_MATERIAL_OFFSET);
            if separate_ao {
                let mut ao = ((compact_color >> 24) & 0xff) as f32 / 255.0;
                if invert_ao {
                    ao = 1.0 - ao;
                }
                stats.separate_ao_vertices += 1;
                stats.min_ao = stats.min_ao.min(ao);
                stats.max_ao = stats.max_ao.max(ao);
                if (color >> 24) & 0xff != (compact_color >> 24) & 0xff {
                    stats.ao_contract_valid = 0;
                }
            }
            let position = [0, 1, 2].map(|axis| decode_position(hi, lo, axis));
            let u = decode_texture_for_copied_atlas(texture & 0xffff, params.atlas_width);
            let v = decode_texture_for_copied_atlas((texture >> 16) & 0xffff, params.atlas_height);
            for axis in 0..3 {
                stats.min_position[axis] = stats.min_position[axis].min(position[axis]);
                stats.max_position[axis] = stats.max_position[axis].max(position[axis]);
            }
            stats.min_uv = [stats.min_uv[0].min(u), stats.min_uv[1].min(v)];
            stats.max_uv = [stats.max_uv[0].max(u), stats.max_uv[1].max(v)];
            let primitive = &metadata[vertex / 4 * metadata_stride..][..metadata_stride];
            let block_id = primitive[2];
            let emission = primitive[9];
            if !(0..=0xff).contains(&emission) {
                return Err(DecodeError::Invalid("static terrain primitive has an invalid semantic block emission"));
            }
            if block_id < 0 {
                return Err(DecodeError::Invalid("static terrain primitive lacks a canonical native block-state identity"));
            }
            let mid_block_packed = if mid_block_offset != 0 {
                word(buffer, offset + mid_block_offset)
            } else {
                let packed = |local: i32, vertex: f32| (((local as f32 + 0.5 - vertex) * 64.0) as i32 & 0xff) as u32;
                packed(primitive[3], position[0])
                    | (packed(primitive[4], position[1]) << 8)
                    | (packed(primitive[5], position[2]) << 16)
                    | ((emission as u32) << 24)
            };
            if faults & FAULT_WRONG_TOP_FACE_SHADE != 0 && top_face {
                color = multiply_argb_rgb(color, 0x80);
            }
            out.push(WorldMeshVertex {
                color_argb: color,
                normal_packed: normal,
                light: decode_light(light_material, swap_light),
                position,
                uv: [u, v],
                shader_atlas_uv: [u, v],
                shader_block_id: block_id,
                shader_material_type: primitive[6] & 1,
                terrain_material_bits: ((light_material >> 16) & 0xff) | if separate_ao { 0x100 } else { 0 },
                mid_block_packed,
            });
        }
        segment_start += count;
    }
    Ok(vertex_count)
}

#[cfg(test)]
mod tests;

use std::slice;

use super::index;

const OK: i32 = 0;
const ERR_NULL_POINTER: i32 = -1;
const ERR_INVALID_ARGUMENT: i32 = -2;
const ERR_CAPACITY: i32 = -3;

const MODEL_QUAD_FACING_COUNT: usize = 7;
const MODEL_QUAD_FACING_UNASSIGNED: usize = 6;
const POSITION_MAX_VALUE: f32 = (1 << 20) as f32;
const TEXTURE_MAX_VALUE: f32 = (1 << 15) as f32;
const MODEL_ORIGIN: f32 = 8.0;
const MODEL_RANGE: f32 = 32.0;
const INDEX_MODE_NONE: i32 = 0;
const INDEX_MODE_SHARED: i32 = 1;
const INDEX_MODE_SORTED_QUADS: i32 = 2;
const INDEX_MODE_KEY_SORTED: i32 = 3;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct QuadVertex {
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
struct NativeQuad {
    vertices: [QuadVertex; 4],
    block_emission: u8,
    render_type: u8,
    ignore_mid_block: u8,
    _padding: u8,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
}

struct NativeQuadBuffer {
    quads: Vec<NativeQuad>,
}

struct NativeSectionMeshBuilder {
    buffers: [NativeQuadBuffer; MODEL_QUAD_FACING_COUNT],
    counts: [usize; MODEL_QUAD_FACING_COUNT],
}

#[derive(Clone, Copy)]
struct NativeFormat {
    vertex_stride: usize,
    block_id_offset: usize,
    normal_offset: usize,
    tangent_offset: usize,
    mid_uv_offset: usize,
    mid_block_offset: usize,
    section_index: i32,
    separate_ao: bool,
}

impl NativeFormat {
    fn from_abi(
        quad_stride: i32,
        vertex_stride: i32,
        block_id_offset: i32,
        normal_offset: i32,
        tangent_offset: i32,
        mid_uv_offset: i32,
        mid_block_offset: i32,
        section_index: i32,
        separate_ao: i32,
    ) -> Result<Self, i32> {
        let quad_stride = usize::try_from(quad_stride).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let vertex_stride = usize::try_from(vertex_stride).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let block_id_offset = usize::try_from(block_id_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let normal_offset = usize::try_from(normal_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let tangent_offset = usize::try_from(tangent_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let mid_uv_offset = usize::try_from(mid_uv_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;
        let mid_block_offset =
            usize::try_from(mid_block_offset).map_err(|_| ERR_INVALID_ARGUMENT)?;

        if quad_stride != std::mem::size_of::<NativeQuad>() || vertex_stride < 20 {
            return Err(ERR_INVALID_ARGUMENT);
        }

        for offset in [
            block_id_offset,
            normal_offset,
            tangent_offset,
            mid_uv_offset,
            mid_block_offset,
        ] {
            if offset != 0 && offset + 4 > vertex_stride {
                return Err(ERR_INVALID_ARGUMENT);
            }
        }

        Ok(Self {
            vertex_stride,
            block_id_offset,
            normal_offset,
            tangent_offset,
            mid_uv_offset,
            mid_block_offset,
            section_index,
            separate_ao: separate_ao != 0,
        })
    }
}

pub fn verify() -> i32 {
    if std::mem::size_of::<QuadVertex>() == 32 && std::mem::size_of::<NativeQuad>() == 152 {
        OK
    } else {
        ERR_INVALID_ARGUMENT
    }
}

unsafe fn encode(
    input_address: u64,
    vertex_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if vertex_count < 0 || output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if vertex_count == 0 {
        return OK;
    }
    if input_address == 0 || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let vertex_count = match usize::try_from(vertex_count) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if vertex_count % 4 != 0 {
        return ERR_INVALID_ARGUMENT;
    }
    let quad_count = vertex_count / 4;
    let output_len = match vertex_count.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let input = slice::from_raw_parts(input_address as *const NativeQuad, quad_count);
    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);

    for (quad_index, quad) in input.iter().enumerate() {
        let start = quad_index * 4 * format.vertex_stride;
        let end = start + 4 * format.vertex_stride;
        encode_quad(quad, &mut output[start..end], format);
    }

    OK
}

unsafe fn encode_scattered(
    input_address: u64,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if update_count < 0 || output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if update_count == 0 {
        return OK;
    }
    if input_address == 0 || output_vertex_offsets.is_null() || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let update_count = match usize::try_from(update_count) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_capacity);
    let input = slice::from_raw_parts(input_address as *const NativeQuad, update_count);
    let output_vertex_offsets = slice::from_raw_parts(output_vertex_offsets, update_count);
    let quad_byte_len = match 4usize.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };

    for (quad, vertex_offset) in input.iter().zip(output_vertex_offsets.iter()) {
        let vertex_offset = match usize::try_from(*vertex_offset) {
            Ok(value) => value,
            Err(_) => return ERR_INVALID_ARGUMENT,
        };
        if vertex_offset % 4 != 0 {
            return ERR_INVALID_ARGUMENT;
        }

        let byte_offset = match vertex_offset.checked_mul(format.vertex_stride) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let byte_end = match byte_offset.checked_add(quad_byte_len) {
            Some(value) => value,
            None => return ERR_INVALID_ARGUMENT,
        };
        let Some(output_slice) = output.get_mut(byte_offset..byte_end) else {
            return ERR_CAPACITY;
        };

        encode_quad(quad, output_slice, format);
    }

    OK
}

unsafe fn assemble(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    if input_addresses.is_null() || input_vertex_counts.is_null() || vertex_segments.is_null() {
        return ERR_NULL_POINTER;
    }
    if input_count != MODEL_QUAD_FACING_COUNT as i32
        || vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32
        || output_capacity < 0
    {
        return ERR_INVALID_ARGUMENT;
    }

    let input_addresses = slice::from_raw_parts(input_addresses, MODEL_QUAD_FACING_COUNT);
    let input_vertex_counts = slice::from_raw_parts(input_vertex_counts, MODEL_QUAD_FACING_COUNT);
    let vertex_segments = slice::from_raw_parts_mut(vertex_segments, MODEL_QUAD_FACING_COUNT * 2);
    vertex_segments.fill(0);

    let total_vertices = match input_vertex_counts.iter().try_fold(0usize, |acc, count| {
        let count = usize::try_from(*count).ok()?;
        if count % 4 != 0 {
            return None;
        }
        acc.checked_add(count)
    }) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };

    if total_vertices == 0 {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let output_capacity = match usize::try_from(output_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let output_len = match total_vertices.checked_mul(format.vertex_stride) {
        Some(value) => value,
        None => return ERR_INVALID_ARGUMENT,
    };
    if output_capacity < output_len {
        return ERR_CAPACITY;
    }

    let output = slice::from_raw_parts_mut(output_address as *mut u8, output_len);
    let mut output_vertex_offset = 0usize;

    if slice_reordering != 0 {
        let mut segment_index = 0usize;
        if let Err(status) = append_segment(
            MODEL_QUAD_FACING_UNASSIGNED,
            input_addresses,
            input_vertex_counts,
            output,
            &mut output_vertex_offset,
            vertex_segments,
            &mut segment_index,
            format,
        ) {
            return status;
        }

        for step in 0..2 {
            for facing in 0..MODEL_QUAD_FACING_COUNT {
                if facing == MODEL_QUAD_FACING_UNASSIGNED
                    || ((visible_slices >> facing) & 1) == step
                {
                    continue;
                }
                if let Err(status) = append_segment(
                    facing,
                    input_addresses,
                    input_vertex_counts,
                    output,
                    &mut output_vertex_offset,
                    vertex_segments,
                    &mut segment_index,
                    format,
                ) {
                    return status;
                }
            }
        }
    } else {
        if force_unassigned != 0 {
            let segment_index = MODEL_QUAD_FACING_UNASSIGNED << 1;
            vertex_segments[segment_index] = total_vertices as i32;
            vertex_segments[segment_index + 1] = MODEL_QUAD_FACING_UNASSIGNED as i32;
        }

        for facing in 0..MODEL_QUAD_FACING_COUNT {
            let vertex_count = match usize::try_from(input_vertex_counts[facing]) {
                Ok(value) => value,
                Err(_) => return ERR_INVALID_ARGUMENT,
            };
            if vertex_count == 0 {
                continue;
            }

            if force_unassigned == 0 {
                let segment_index = facing << 1;
                vertex_segments[segment_index] = vertex_count as i32;
                vertex_segments[segment_index + 1] = facing as i32;
            }

            if let Err(status) = encode_segment(
                input_addresses[facing],
                vertex_count,
                output,
                &mut output_vertex_offset,
                format,
            ) {
                return status;
            }
        }
    }

    OK
}

unsafe fn assemble_output(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    index_output_address: u64,
    index_output_capacity: i32,
    index_mode: i32,
    index_stride: i32,
    index_values: *const i32,
    index_value_count: i32,
) -> i32 {
    let status = assemble(
        input_addresses,
        input_vertex_counts,
        input_count,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
    );
    if status != OK || index_mode == INDEX_MODE_NONE {
        return status;
    }
    if index_output_capacity < 0 || index_output_address == 0 {
        return if index_output_capacity < 0 {
            ERR_INVALID_ARGUMENT
        } else {
            ERR_NULL_POINTER
        };
    }

    match index_mode {
        INDEX_MODE_SHARED => {
            let total_vertices = match total_vertex_count(input_vertex_counts, input_count) {
                Ok(value) => value,
                Err(status) => return status,
            };
            if total_vertices % 4 != 0 {
                return ERR_INVALID_ARGUMENT;
            }

            let output = slice::from_raw_parts_mut(
                index_output_address as *mut u8,
                index_output_capacity as usize,
            );
            index::write_shared_quad_index_buffer(output, index_stride, (total_vertices / 4) as i32)
        }
        INDEX_MODE_SORTED_QUADS | INDEX_MODE_KEY_SORTED => {
            if index_value_count < 0 {
                return ERR_INVALID_ARGUMENT;
            }
            if index_value_count == 0 {
                return OK;
            }
            if index_values.is_null() {
                return ERR_NULL_POINTER;
            }

            let index_capacity = (index_output_capacity as usize) / std::mem::size_of::<i32>();
            let output =
                slice::from_raw_parts_mut(index_output_address as *mut i32, index_capacity);
            let values = slice::from_raw_parts(index_values, index_value_count as usize);

            if index_mode == INDEX_MODE_SORTED_QUADS {
                index::write_sorted_quad_index_buffer(output, values)
            } else {
                index::write_key_sorted_quad_index_buffer(output, values)
            }
        }
        _ => ERR_INVALID_ARGUMENT,
    }
}

unsafe fn total_vertex_count(
    input_vertex_counts: *const i32,
    input_count: i32,
) -> Result<usize, i32> {
    if input_vertex_counts.is_null() {
        return Err(ERR_NULL_POINTER);
    }
    if input_count != MODEL_QUAD_FACING_COUNT as i32 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let input_vertex_counts = slice::from_raw_parts(input_vertex_counts, MODEL_QUAD_FACING_COUNT);
    input_vertex_counts
        .iter()
        .try_fold(0usize, |acc, count| {
            let count = usize::try_from(*count).ok()?;
            if count % 4 != 0 {
                return None;
            }
            acc.checked_add(count)
        })
        .ok_or(ERR_INVALID_ARGUMENT)
}

fn append_segment(
    facing: usize,
    input_addresses: &[u64],
    input_vertex_counts: &[i32],
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    vertex_segments: &mut [i32],
    segment_index: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    let vertex_count =
        usize::try_from(input_vertex_counts[facing]).map_err(|_| ERR_INVALID_ARGUMENT)?;
    vertex_segments[*segment_index] = vertex_count as i32;
    vertex_segments[*segment_index + 1] = facing as i32;
    *segment_index += 2;

    if vertex_count != 0 {
        encode_segment(
            input_addresses[facing],
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
    }

    Ok(())
}

fn encode_segment(
    input_address: u64,
    vertex_count: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    if input_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if vertex_count % 4 != 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }
    let quad_count = vertex_count / 4;

    let byte_offset = output_vertex_offset
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let byte_len = vertex_count
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let output_slice = output
        .get_mut(byte_offset..byte_offset + byte_len)
        .ok_or(ERR_CAPACITY)?;
    let input = unsafe { slice::from_raw_parts(input_address as *const NativeQuad, quad_count) };

    for (quad_index, quad) in input.iter().enumerate() {
        let start = quad_index * 4 * format.vertex_stride;
        let end = start + 4 * format.vertex_stride;
        encode_quad(quad, &mut output_slice[start..end], format);
    }

    *output_vertex_offset += vertex_count;
    Ok(())
}

fn section_builder_addresses_and_counts(
    builder: &NativeSectionMeshBuilder,
) -> (
    [u64; MODEL_QUAD_FACING_COUNT],
    [i32; MODEL_QUAD_FACING_COUNT],
) {
    let mut addresses = [0u64; MODEL_QUAD_FACING_COUNT];
    let mut vertex_counts = [0i32; MODEL_QUAD_FACING_COUNT];

    for facing in 0..MODEL_QUAD_FACING_COUNT {
        let count = builder.counts[facing];
        if count != 0 {
            addresses[facing] = builder.buffers[facing].quads.as_ptr() as u64;
            vertex_counts[facing] = (count * 4) as i32;
        }
    }

    (addresses, vertex_counts)
}

fn section_builder_prepare_quad(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
) -> Result<u64, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let index = builder.counts[facing];
    if builder.buffers[facing].quads.len() <= index {
        let next_capacity = (builder.buffers[facing].quads.len().max(1) * 2).max(index + 1);
        builder.buffers[facing]
            .quads
            .resize(next_capacity, NativeQuad::default());
    }

    Ok(unsafe { builder.buffers[facing].quads.as_mut_ptr().add(index) as u64 })
}

unsafe fn section_builder_append_batch(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    batch_address: u64,
    quad_count: usize,
    validity: Option<&[u8]>,
) -> Result<i32, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if quad_count == 0 {
        return Ok(0);
    }
    if batch_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if let Some(validity) = validity {
        if validity.len() < quad_count {
            return Err(ERR_INVALID_ARGUMENT);
        }
    }

    let input = slice::from_raw_parts(batch_address as *const NativeQuad, quad_count);
    let valid_count = validity
        .map(|mask| {
            mask.iter()
                .take(quad_count)
                .filter(|&&value| value != 0)
                .count()
        })
        .unwrap_or(quad_count);
    let start = builder.counts[facing];
    let required_len = start.checked_add(valid_count).ok_or(ERR_CAPACITY)?;

    if builder.buffers[facing].quads.len() < required_len {
        builder.buffers[facing]
            .quads
            .resize(required_len, NativeQuad::default());
    }

    let output = &mut builder.buffers[facing].quads[start..required_len];
    let mut output_index = 0usize;

    for index in 0..quad_count {
        let is_valid = match validity {
            Some(mask) => mask[index] != 0,
            None => true,
        };

        if is_valid {
            output[output_index] = input[index];
            output_index += 1;
        }
    }

    builder.counts[facing] = required_len;
    Ok(valid_count as i32)
}

fn encode_quad(quad: &NativeQuad, output: &mut [u8], format: NativeFormat) {
    let vertices = &quad.vertices;
    let tex_centroid_u = vertices.iter().map(|vertex| vertex.u).sum::<f32>() * 0.25;
    let tex_centroid_v = vertices.iter().map(|vertex| vertex.v).sum::<f32>() * 0.25;
    let mid_uv = encode_old_uv(tex_centroid_u, tex_centroid_v);
    let (normal, tangent, packed_normal, packed_tangent) = if format.normal_offset != 0 {
        let normal = compute_face_normal(vertices);
        let tangent = compute_tangent_for_quad(normal, vertices);
        let normal_oct = octahedron_encode(normal);
        let tangent_oct = tangent_encode(tangent);
        (
            normal,
            tangent,
            pack_norm_i8(normal_oct.0, normal_oct.1, tangent_oct.0, tangent_oct.1),
            pack_norm_i8(tangent.0, tangent.1, tangent.2, tangent.3),
        )
    } else {
        (
            (0.0, 1.0, 0.0),
            (0.0, 1.0, 0.0, 1.0),
            default_normal(),
            pack_norm_i8(0.0, 1.0, 0.0, 1.0),
        )
    };
    let _ = (normal, tangent);

    for (index, vertex) in vertices.iter().enumerate() {
        let ptr = &mut output[index * format.vertex_stride..(index + 1) * format.vertex_stride];
        let x = quantize_position(vertex.x);
        let y = quantize_position(vertex.y);
        let z = quantize_position(vertex.z);
        let u = encode_texture(tex_centroid_u, vertex.u);
        let v = encode_texture(tex_centroid_v, vertex.v);
        let light = encode_light(vertex.light);

        put_i32(ptr, 0, pack_position_hi(x, y, z));
        put_i32(ptr, 4, pack_position_lo(x, y, z));
        put_i32(
            ptr,
            8,
            encode_color(vertex.color, vertex.ao, format.separate_ao),
        );
        put_i32(ptr, 12, pack_texture(u, v));
        put_i32(
            ptr,
            16,
            pack_light_and_data(light, quad.material_bits, format.section_index),
        );

        if format.block_id_offset != 0 {
            put_i32(ptr, format.block_id_offset, pack_block_id(quad));
        }
        if format.mid_block_offset != 0 {
            let mid_block = if quad.ignore_mid_block != 0 {
                0
            } else {
                compute_mid_block(vertex, quad)
            };
            put_i32(ptr, format.mid_block_offset, mid_block);
            ptr[format.mid_block_offset + 3] = quad.block_emission;
        }
        if format.mid_uv_offset != 0 {
            put_i32(ptr, format.mid_uv_offset, mid_uv);
        }
        if format.normal_offset != 0 {
            put_i32(ptr, format.normal_offset, packed_normal);
        }
        if format.tangent_offset != 0 {
            put_i32(ptr, format.tangent_offset, packed_tangent);
        }
    }
}

fn put_i32(output: &mut [u8], offset: usize, value: i32) {
    output[offset..offset + 4].copy_from_slice(&value.to_ne_bytes());
}

fn pack_position_hi(x: i32, y: i32, z: i32) -> i32 {
    (((x >> 10) & 0x3ff) << 0) | (((y >> 10) & 0x3ff) << 10) | (((z >> 10) & 0x3ff) << 20)
}

fn pack_position_lo(x: i32, y: i32, z: i32) -> i32 {
    ((x & 0x3ff) << 0) | ((y & 0x3ff) << 10) | ((z & 0x3ff) << 20)
}

fn quantize_position(position: f32) -> i32 {
    ((normalize_position(position) * POSITION_MAX_VALUE) as i32) & 0x0f_ffff
}

fn normalize_position(value: f32) -> f32 {
    (MODEL_ORIGIN + value) / MODEL_RANGE
}

fn encode_texture(center: f32, value: f32) -> i32 {
    let bias = if value < center { 1 } else { -1 };
    let quantized = java_round(value * TEXTURE_MAX_VALUE) + bias;
    (quantized & 0x7fff) | (sign(bias) << 15)
}

fn encode_old_uv(u: f32, v: f32) -> i32 {
    ((java_round(u * TEXTURE_MAX_VALUE) & 0xffff) << 0)
        | ((java_round(v * TEXTURE_MAX_VALUE) & 0xffff) << 16)
}

fn java_round(value: f32) -> i32 {
    (value + 0.5).floor() as i32
}

fn sign(value: i32) -> i32 {
    ((value as u32) >> 31) as i32
}

fn pack_texture(u: i32, v: i32) -> i32 {
    ((u & 0xffff) << 0) | ((v & 0xffff) << 16)
}

fn encode_light(light: i32) -> i32 {
    let sky = clamp_i32(((light as u32 >> 16) & 0xff) as i32, 8, 248);
    let block = clamp_i32(((light as u32 >> 0) & 0xff) as i32, 8, 248);
    (block << 0) | (sky << 8)
}

fn pack_light_and_data(light: i32, material: i32, section: i32) -> i32 {
    ((light & 0xffff) << 0) | ((material & 0xff) << 16) | ((section & 0xff) << 24)
}

fn clamp_i32(value: i32, min: i32, max: i32) -> i32 {
    value.max(min).min(max)
}

fn normalized_float_to_byte(value: f32) -> i32 {
    ((value * 255.0) as i32) & 0xff
}

fn color_mul(color: i32, factor: i32) -> i32 {
    let color = color as u32 as u64;
    let factor = factor as u64;
    let hi = (color & 0x00ff00ff) * factor;
    let lo = (color & 0xff00ff00) * factor;
    let result = (((hi + 0x00ff00ff) >> 8) & 0x00ff00ff) | (((lo + 0xff00ff00) >> 8) & 0xff00ff00);
    result as u32 as i32
}

fn color_mul_rgb(color: i32, factor: f32) -> i32 {
    let alpha_mask = 0xff000000u32 as i32;
    let factor = normalized_float_to_byte(factor);
    (color_mul(color, factor) & !alpha_mask) | (color & alpha_mask)
}

fn with_alpha(color: i32, alpha: f32) -> i32 {
    let alpha = normalized_float_to_byte(alpha);
    (alpha << 24) | (color & !(0xff << 24))
}

fn encode_color(color: i32, ao: f32, separate_ao: bool) -> i32 {
    if separate_ao {
        with_alpha(color, ao)
    } else {
        color_mul_rgb(color, ao)
    }
}

fn compute_mid_block(vertex: &QuadVertex, quad: &NativeQuad) -> i32 {
    pack_mid_block(
        quad.local_x as f32 + 0.5 - vertex.x,
        quad.local_y as f32 + 0.5 - vertex.y,
        quad.local_z as f32 + 0.5 - vertex.z,
    )
}

fn pack_mid_block(x: f32, y: f32, z: f32) -> i32 {
    (((x * 64.0) as i32) & 0xff)
        | ((((y * 64.0) as i32) & 0xff) << 8)
        | ((((z * 64.0) as i32) & 0xff) << 16)
}

fn pack_block_id(quad: &NativeQuad) -> i32 {
    ((quad.block_id + 1) << 1) | ((quad.render_type as i32) & 1)
}

fn compute_face_normal(vertices: &[QuadVertex; 4]) -> (f32, f32, f32) {
    let dx0 = vertices[2].x - vertices[0].x;
    let dy0 = vertices[2].y - vertices[0].y;
    let dz0 = vertices[2].z - vertices[0].z;
    let dx1 = vertices[3].x - vertices[1].x;
    let dy1 = vertices[3].y - vertices[1].y;
    let dz1 = vertices[3].z - vertices[1].z;
    normalize3(
        dy0 * dz1 - dz0 * dy1,
        dz0 * dx1 - dx0 * dz1,
        dx0 * dy1 - dy0 * dx1,
    )
}

fn compute_tangent_for_quad(
    normal: (f32, f32, f32),
    vertices: &[QuadVertex; 4],
) -> (f32, f32, f32, f32) {
    match compute_tangent(normal, vertices[0], vertices[1], vertices[2]) {
        Some(value) => value,
        None => compute_tangent(normal, vertices[2], vertices[3], vertices[0])
            .unwrap_or((0.0, 1.0, 0.0, 1.0)),
    }
}

fn compute_tangent(
    normal: (f32, f32, f32),
    v0: QuadVertex,
    v1: QuadVertex,
    v2: QuadVertex,
) -> Option<(f32, f32, f32, f32)> {
    let edge1x = v1.x - v0.x;
    let edge1y = v1.y - v0.y;
    let edge1z = v1.z - v0.z;
    let edge2x = v2.x - v0.x;
    let edge2y = v2.y - v0.y;
    let edge2z = v2.z - v0.z;

    let delta_u1 = v1.u - v0.u;
    let delta_v1 = v1.v - v0.v;
    let delta_u2 = v2.u - v0.u;
    let delta_v2 = v2.v - v0.v;
    let fdenom = delta_u1 * delta_v2 - delta_u2 * delta_v1;
    let f = if fdenom == 0.0 { 1.0 } else { 1.0 / fdenom };

    let tangent = normalize3(
        f * (delta_v2 * edge1x - delta_v1 * edge2x),
        f * (delta_v2 * edge1y - delta_v1 * edge2y),
        f * (delta_v2 * edge1z - delta_v1 * edge2z),
    );
    if tangent.0 == 0.0 && tangent.1 == 0.0 && tangent.2 == 0.0 {
        return None;
    }

    let bitangent = normalize3(
        f * (-delta_u2 * edge1x + delta_u1 * edge2x),
        f * (-delta_u2 * edge1y + delta_u1 * edge2y),
        f * (-delta_u2 * edge1z + delta_u1 * edge2z),
    );

    let predicted_bitangent = (
        tangent.1 * normal.2 - tangent.2 * normal.1,
        tangent.2 * normal.0 - tangent.0 * normal.2,
        tangent.0 * normal.1 - tangent.1 * normal.0,
    );
    let dot = bitangent.0 * predicted_bitangent.0
        + bitangent.1 * predicted_bitangent.1
        + bitangent.2 * predicted_bitangent.2;
    let w = if dot < 0.0 { -1.0 } else { 1.0 };

    Some((tangent.0, tangent.1, tangent.2, w))
}

fn normalize3(x: f32, y: f32, z: f32) -> (f32, f32, f32) {
    let value = x * x + y * y + z * z;
    let coefficient = if value == 0.0 {
        1.0
    } else {
        1.0 / value.sqrt()
    };
    (x * coefficient, y * coefficient, z * coefficient)
}

fn octahedron_encode(vector: (f32, f32, f32)) -> (f32, f32) {
    let inv_l1 = 1.0 / (vector.0.abs() + vector.1.abs() + vector.2.abs());
    let nx = vector.0 * inv_l1;
    let ny = vector.1 * inv_l1;
    let nz = vector.2 * inv_l1;

    if nz >= 0.0 {
        (nx, ny)
    } else {
        (
            (1.0 - ny.abs()) * if nx >= 0.0 { 1.0 } else { -1.0 },
            (1.0 - nx.abs()) * if ny >= 0.0 { 1.0 } else { -1.0 },
        )
    }
}

fn tangent_encode(tangent: (f32, f32, f32, f32)) -> (f32, f32) {
    let mut encoded = octahedron_encode((tangent.0, tangent.1, tangent.2));
    let y_sign = if encoded.1 >= 0.0 {
        64.0 / 127.0
    } else {
        -64.0 / 127.0
    };
    encoded.1 *= 63.0 / 127.0;
    if tangent.3 < 0.0 {
        encoded.1 += y_sign;
    }
    encoded
}

fn default_normal() -> i32 {
    let normal = octahedron_encode((0.0, 1.0, 0.0));
    let tangent = tangent_encode((0.0, 1.0, 0.0, 1.0));
    pack_norm_i8(normal.0, normal.1, tangent.0, tangent.1)
}

fn pack_norm_i8(x: f32, y: f32, z: f32, w: f32) -> i32 {
    (((x * 127.0) as i32) & 0xff)
        | ((((y * 127.0) as i32) & 0xff) << 8)
        | ((((z * 127.0) as i32) & 0xff) << 16)
        | ((((w * 127.0) as i32) & 0xff) << 24)
}

#[no_mangle]
pub extern "C" fn mattmc_sodium_chunk_mesh_verify() -> i32 {
    verify()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_mesh_encode(
    input_address: u64,
    vertex_count: i32,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
) -> i32 {
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    encode(
        input_address,
        vertex_count,
        output_address,
        output_capacity,
        format,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_mesh_scattered_encode(
    input_address: u64,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
) -> i32 {
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    encode_scattered(
        input_address,
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_mesh_output_assemble(
    input_addresses: *const u64,
    input_vertex_counts: *const i32,
    input_count: i32,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    separate_ao: i32,
    index_output_address: u64,
    index_output_capacity: i32,
    index_mode: i32,
    index_stride: i32,
    index_values: *const i32,
    index_value_count: i32,
) -> i32 {
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    assemble_output(
        input_addresses,
        input_vertex_counts,
        input_count,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
        index_output_address,
        index_output_capacity,
        index_mode,
        index_stride,
        index_values,
        index_value_count,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_create(
    initial_quad_capacity: i32,
    output_handle: *mut u64,
) -> i32 {
    if initial_quad_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(initial_quad_capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let builder = NativeSectionMeshBuilder {
        buffers: std::array::from_fn(|_| NativeQuadBuffer {
            quads: vec![NativeQuad::default(); capacity],
        }),
        counts: [0; MODEL_QUAD_FACING_COUNT],
    };

    *output_handle = Box::into_raw(Box::new(builder)) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeSectionMeshBuilder));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_start(handle: u64) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    builder.counts.fill(0);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_prepare_quad(
    handle: u64,
    facing: i32,
    output_address: *mut u64,
) -> i32 {
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_prepare_quad(builder, facing as usize) {
        Ok(address) => {
            *output_address = address;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_commit_quad(
    handle: u64,
    facing: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || facing as usize >= MODEL_QUAD_FACING_COUNT {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    let facing = facing as usize;
    if builder.counts[facing] >= builder.buffers[facing].quads.len() {
        return ERR_INVALID_ARGUMENT;
    }
    builder.counts[facing] += 1;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_batch(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_batch(
        builder,
        facing as usize,
        batch_address,
        quad_count as usize,
        None,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_batch_filtered(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    validity_address: u64,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count > 0 && validity_address == 0 {
        return ERR_NULL_POINTER;
    }

    let validity = if quad_count == 0 {
        &[][..]
    } else {
        slice::from_raw_parts(validity_address as *const u8, quad_count as usize)
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_batch(
        builder,
        facing as usize,
        batch_address,
        quad_count as usize,
        Some(validity),
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_facing_address(
    handle: u64,
    facing: i32,
    output_address: *mut u64,
) -> i32 {
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || facing as usize >= MODEL_QUAD_FACING_COUNT {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    let facing = facing as usize;
    *output_address = if builder.counts[facing] == 0 {
        0
    } else {
        builder.buffers[facing].quads.as_ptr() as u64
    };
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_facing_vertex_count(
    handle: u64,
    facing: i32,
    output_count: *mut i32,
) -> i32 {
    if handle == 0 || output_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || facing as usize >= MODEL_QUAD_FACING_COUNT {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    *output_count = (builder.counts[facing as usize] * 4) as i32;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_total_vertex_count(
    handle: u64,
    output_count: *mut i32,
) -> i32 {
    if handle == 0 || output_count.is_null() {
        return ERR_NULL_POINTER;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    let Some(total_count) = builder
        .counts
        .iter()
        .try_fold(0usize, |acc, count| acc.checked_add(count * 4))
        .and_then(|value| i32::try_from(value).ok())
    else {
        return ERR_CAPACITY;
    };

    *output_count = total_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_assemble(
    handle: u64,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
    separate_ao: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    let format = match NativeFormat::from_abi(
        quad_stride,
        vertex_stride,
        block_id_offset,
        normal_offset,
        tangent_offset,
        mid_uv_offset,
        mid_block_offset,
        section_index,
        separate_ao,
    ) {
        Ok(value) => value,
        Err(status) => return status,
    };

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    let (addresses, vertex_counts) = section_builder_addresses_and_counts(builder);
    assemble(
        addresses.as_ptr(),
        vertex_counts.as_ptr(),
        MODEL_QUAD_FACING_COUNT as i32,
        output_address,
        output_capacity,
        vertex_segments,
        vertex_segments_len,
        format,
        visible_slices,
        force_unassigned,
        slice_reordering,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_create(
    capacity: i32,
    output_handle: *mut u64,
    output_address: *mut u64,
) -> i32 {
    if capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if output_handle.is_null() || output_address.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let mut buffer = Box::new(NativeQuadBuffer {
        quads: vec![NativeQuad::default(); capacity],
    });

    *output_address = if buffer.quads.is_empty() {
        0
    } else {
        buffer.quads.as_mut_ptr() as u64
    };
    *output_handle = Box::into_raw(buffer) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_ensure_capacity(
    handle: u64,
    capacity: i32,
    output_address: *mut u64,
) -> i32 {
    if capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if handle == 0 || output_address.is_null() {
        return ERR_NULL_POINTER;
    }

    let capacity = match usize::try_from(capacity) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    let buffer = &mut *(handle as *mut NativeQuadBuffer);
    if buffer.quads.len() < capacity {
        buffer.quads.resize(capacity, NativeQuad::default());
    }

    *output_address = if buffer.quads.is_empty() {
        0
    } else {
        buffer.quads.as_mut_ptr() as u64
    };
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_quad_buffer_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeQuadBuffer));
    OK
}

#[cfg(test)]
mod tests {
    use super::*;

    fn vertex(x: f32, y: f32, z: f32, u: f32, v: f32) -> QuadVertex {
        QuadVertex {
            x,
            y,
            z,
            color: 0xff804020u32 as i32,
            ao: 0.5,
            u,
            v,
            light: 0x00f000f0,
        }
    }

    fn quad() -> NativeQuad {
        NativeQuad {
            vertices: [
                vertex(0.0, 0.0, 0.0, 0.0, 0.0),
                vertex(1.0, 0.0, 0.0, 1.0, 0.0),
                vertex(1.0, 1.0, 0.0, 1.0, 1.0),
                vertex(0.0, 1.0, 0.0, 0.0, 1.0),
            ],
            block_emission: 7,
            render_type: 1,
            ignore_mid_block: 0,
            _padding: 0,
            block_id: 41,
            local_x: 1,
            local_y: 2,
            local_z: 3,
            material_bits: 5,
        }
    }

    #[test]
    fn native_quad_layout_matches_java_stride() {
        assert_eq!(32, std::mem::size_of::<QuadVertex>());
        assert_eq!(152, std::mem::size_of::<NativeQuad>());
    }

    #[test]
    fn compact_encoder_writes_expected_base_words() {
        let input = [quad()];
        let mut output = vec![0u8; 4 * 20];
        let format = NativeFormat {
            vertex_stride: 20,
            block_id_offset: 0,
            normal_offset: 0,
            tangent_offset: 0,
            mid_uv_offset: 0,
            mid_block_offset: 0,
            section_index: 3,
            separate_ao: false,
        };

        unsafe {
            assert_eq!(
                OK,
                encode(
                    input.as_ptr() as u64,
                    4,
                    output.as_mut_ptr() as u64,
                    output.len() as i32,
                    format,
                )
            );
        }

        assert_ne!([0u8; 20], output[0..20]);
        assert_eq!(
            pack_light_and_data(0xf0f0, 5, 3).to_ne_bytes(),
            output[16..20]
        );
    }

    #[test]
    fn scattered_encoder_writes_requested_quad_slots_only() {
        let mut first = quad();
        first.material_bits = 5;
        let mut second = quad();
        second.material_bits = 9;
        second.vertices[0].x = 4.0;
        let input = [first, second];
        let offsets = [8, 0];
        let mut output = vec![0u8; 4 * 3 * 20];
        let format = NativeFormat {
            vertex_stride: 20,
            block_id_offset: 0,
            normal_offset: 0,
            tangent_offset: 0,
            mid_uv_offset: 0,
            mid_block_offset: 0,
            section_index: 7,
            separate_ao: false,
        };

        unsafe {
            assert_eq!(
                OK,
                encode_scattered(
                    input.as_ptr() as u64,
                    offsets.as_ptr(),
                    input.len() as i32,
                    output.as_mut_ptr() as u64,
                    output.len() as i32,
                    format,
                )
            );
        }

        assert_eq!(
            pack_light_and_data(0xf0f0, 9, 7).to_ne_bytes(),
            output[16..20]
        );
        assert_eq!([0u8; 20], output[4 * 20..5 * 20]);
        assert_eq!(
            pack_light_and_data(0xf0f0, 5, 7).to_ne_bytes(),
            output[(8 * 20 + 16)..(8 * 20 + 20)]
        );
    }

    #[test]
    fn native_quad_buffer_create_and_grow_returns_writable_memory() {
        unsafe {
            let mut handle = 0u64;
            let mut address = 0u64;
            assert_eq!(
                OK,
                mattmc_sodium_chunk_quad_buffer_create(1, &mut handle, &mut address)
            );
            assert_ne!(0, handle);
            assert_ne!(0, address);

            *(address as *mut NativeQuad) = quad();

            assert_eq!(
                OK,
                mattmc_sodium_chunk_quad_buffer_ensure_capacity(handle, 4, &mut address)
            );
            assert_ne!(0, address);
            *(address as *mut NativeQuad).add(3) = quad();

            assert_eq!(OK, mattmc_sodium_chunk_quad_buffer_destroy(handle));
        }
    }
}

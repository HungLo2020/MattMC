use std::slice;

use super::{index, translucent};

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
const PENDING_BATCH_QUAD_CAPACITY: usize = 256;

const COMPACT_VALUE_STRIDE: i32 = 0;
const COMPACT_VALUE_POSITION_OFFSET: i32 = 1;
const COMPACT_VALUE_COLOR_OFFSET: i32 = 2;
const COMPACT_VALUE_TEXTURE_OFFSET: i32 = 3;
const COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET: i32 = 4;
const COMPACT_VALUE_BLOCK_ID_OFFSET: i32 = 5;
const COMPACT_VALUE_NORMAL_OFFSET: i32 = 6;
const COMPACT_VALUE_TANGENT_OFFSET: i32 = 7;
const COMPACT_VALUE_MID_UV_OFFSET: i32 = 8;
const COMPACT_VALUE_MID_BLOCK_OFFSET: i32 = 9;
const COMPACT_VALUE_POSITION_MAX_VALUE: i32 = 10;
const COMPACT_VALUE_TEXTURE_MAX_VALUE: i32 = 11;

const COMPACT_VERTEX_STRIDE: i32 = 20;
const COMPACT_POSITION_OFFSET: i32 = 0;
const COMPACT_COLOR_OFFSET: i32 = 8;
const COMPACT_TEXTURE_OFFSET: i32 = 12;
const COMPACT_LIGHT_MATERIAL_INDEX_OFFSET: i32 = 16;
const COMPACT_NATIVE_BLOCK_ID_OFFSET: i32 = 0;
const COMPACT_NATIVE_NORMAL_OFFSET: i32 = 0;
const COMPACT_NATIVE_TANGENT_OFFSET: i32 = 0;
const COMPACT_NATIVE_MID_UV_OFFSET: i32 = 0;
const COMPACT_NATIVE_MID_BLOCK_OFFSET: i32 = 0;

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
    encoded: Vec<u8>,
    encoded_format: Option<NativeFormat>,
}

struct NativePendingQuadBuffer {
    quads: Vec<NativeQuad>,
    packed_normals: Vec<i32>,
    validity: Vec<u8>,
}

struct NativeSectionMeshBuilder {
    buffers: [NativeQuadBuffer; MODEL_QUAD_FACING_COUNT],
    pending: [NativePendingQuadBuffer; MODEL_QUAD_FACING_COUNT],
    counts: [usize; MODEL_QUAD_FACING_COUNT],
}

struct NativeUpdatedQuads {
    quads: Vec<u64>,
    mesh_quad_count: i32,
    index_quad_count: i32,
}

pub(crate) fn updated_quads_create_from_handles(
    quads: Vec<u64>,
    mesh_quad_count: i32,
    index_quad_count: i32,
) -> u64 {
    Box::into_raw(Box::new(NativeUpdatedQuads {
        quads,
        mesh_quad_count,
        index_quad_count,
    })) as u64
}

#[derive(Clone, Copy, PartialEq, Eq)]
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

fn compact_format_value(value: i32) -> i32 {
    match value {
        COMPACT_VALUE_STRIDE => COMPACT_VERTEX_STRIDE,
        COMPACT_VALUE_POSITION_OFFSET => COMPACT_POSITION_OFFSET,
        COMPACT_VALUE_COLOR_OFFSET => COMPACT_COLOR_OFFSET,
        COMPACT_VALUE_TEXTURE_OFFSET => COMPACT_TEXTURE_OFFSET,
        COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET => COMPACT_LIGHT_MATERIAL_INDEX_OFFSET,
        COMPACT_VALUE_BLOCK_ID_OFFSET => COMPACT_NATIVE_BLOCK_ID_OFFSET,
        COMPACT_VALUE_NORMAL_OFFSET => COMPACT_NATIVE_NORMAL_OFFSET,
        COMPACT_VALUE_TANGENT_OFFSET => COMPACT_NATIVE_TANGENT_OFFSET,
        COMPACT_VALUE_MID_UV_OFFSET => COMPACT_NATIVE_MID_UV_OFFSET,
        COMPACT_VALUE_MID_BLOCK_OFFSET => COMPACT_NATIVE_MID_BLOCK_OFFSET,
        COMPACT_VALUE_POSITION_MAX_VALUE => POSITION_MAX_VALUE as i32,
        COMPACT_VALUE_TEXTURE_MAX_VALUE => TEXTURE_MAX_VALUE as i32,
        _ => ERR_INVALID_ARGUMENT,
    }
}

unsafe fn native_quad_mut(address: u64) -> Result<&'static mut NativeQuad, i32> {
    if address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(&mut *(address as *mut NativeQuad))
}

unsafe fn native_quad(address: u64) -> Result<&'static NativeQuad, i32> {
    if address == 0 {
        return Err(ERR_NULL_POINTER);
    }

    Ok(&*(address as *const NativeQuad))
}

unsafe fn write_native_quad_metadata(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
) -> i32 {
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    quad.block_emission = block_emission as u8;
    quad.render_type = render_type as u8;
    quad.ignore_mid_block = if ignore_mid_block != 0 { 1 } else { 0 };
    quad._padding = 0;
    quad.block_id = block_id;
    quad.local_x = local_x;
    quad.local_y = local_y;
    quad.local_z = local_z;
    quad.material_bits = material_bits;
    OK
}

unsafe fn write_native_quad_vertex(
    quad_address: u64,
    vertex_index: i32,
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
) -> i32 {
    let vertex_index = match usize::try_from(vertex_index) {
        Ok(value) if value < 4 => value,
        _ => return ERR_INVALID_ARGUMENT,
    };
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    quad.vertices[vertex_index] = QuadVertex {
        x,
        y,
        z,
        color,
        ao,
        u,
        v,
        light,
    };
    OK
}

unsafe fn write_native_quad(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
    x0: f32,
    y0: f32,
    z0: f32,
    color0: i32,
    ao0: f32,
    u0: f32,
    v0: f32,
    light0: i32,
    x1: f32,
    y1: f32,
    z1: f32,
    color1: i32,
    ao1: f32,
    u1: f32,
    v1: f32,
    light1: i32,
    x2: f32,
    y2: f32,
    z2: f32,
    color2: i32,
    ao2: f32,
    u2: f32,
    v2: f32,
    light2: i32,
    x3: f32,
    y3: f32,
    z3: f32,
    color3: i32,
    ao3: f32,
    u3: f32,
    v3: f32,
    light3: i32,
) -> i32 {
    let quad = match native_quad_mut(quad_address) {
        Ok(value) => value,
        Err(status) => return status,
    };

    *quad = NativeQuad {
        vertices: [
            QuadVertex {
                x: x0,
                y: y0,
                z: z0,
                color: color0,
                ao: ao0,
                u: u0,
                v: v0,
                light: light0,
            },
            QuadVertex {
                x: x1,
                y: y1,
                z: z1,
                color: color1,
                ao: ao1,
                u: u1,
                v: v1,
                light: light1,
            },
            QuadVertex {
                x: x2,
                y: y2,
                z: z2,
                color: color2,
                ao: ao2,
                u: u2,
                v: v2,
                light: light2,
            },
            QuadVertex {
                x: x3,
                y: y3,
                z: z3,
                color: color3,
                ao: ao3,
                u: u3,
                v: v3,
                light: light3,
            },
        ],
        block_emission: block_emission as u8,
        render_type: render_type as u8,
        ignore_mid_block: if ignore_mid_block != 0 { 1 } else { 0 },
        _padding: 0,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
    };
    OK
}

unsafe fn native_quad_position(quad_address: u64, vertex_index: i32, component: i32) -> f32 {
    let Ok(vertex_index) = usize::try_from(vertex_index) else {
        return 0.0;
    };
    if vertex_index >= 4 {
        return 0.0;
    }

    let Ok(quad) = native_quad(quad_address) else {
        return 0.0;
    };
    let vertex = quad.vertices[vertex_index];

    match component {
        0 => vertex.x,
        1 => vertex.y,
        2 => vertex.z,
        _ => 0.0,
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

unsafe fn assemble_section_builder(
    builder: &NativeSectionMeshBuilder,
    output_address: u64,
    output_capacity: i32,
    vertex_segments: *mut i32,
    vertex_segments_len: i32,
    format: NativeFormat,
    visible_slices: i32,
    force_unassigned: i32,
    slice_reordering: i32,
) -> i32 {
    if vertex_segments.is_null() || output_capacity < 0 {
        return if vertex_segments.is_null() {
            ERR_NULL_POINTER
        } else {
            ERR_INVALID_ARGUMENT
        };
    }
    if vertex_segments_len != (MODEL_QUAD_FACING_COUNT * 2) as i32 {
        return ERR_INVALID_ARGUMENT;
    }

    let vertex_segments = slice::from_raw_parts_mut(vertex_segments, MODEL_QUAD_FACING_COUNT * 2);
    vertex_segments.fill(0);

    let total_vertices = match builder
        .counts
        .iter()
        .try_fold(0usize, |acc, count| acc.checked_add(count.checked_mul(4)?))
    {
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
        if let Err(status) = append_builder_segment(
            builder,
            MODEL_QUAD_FACING_UNASSIGNED,
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
                if let Err(status) = append_builder_segment(
                    builder,
                    facing,
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
            let vertex_count = builder.counts[facing] * 4;
            if vertex_count == 0 {
                continue;
            }

            if force_unassigned == 0 {
                let segment_index = facing << 1;
                vertex_segments[segment_index] = vertex_count as i32;
                vertex_segments[segment_index + 1] = facing as i32;
            }

            if let Err(status) = encode_builder_segment(
                &builder.buffers[facing],
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

fn append_builder_segment(
    builder: &NativeSectionMeshBuilder,
    facing: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    vertex_segments: &mut [i32],
    segment_index: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    let vertex_count = builder.counts[facing]
        .checked_mul(4)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    vertex_segments[*segment_index] = vertex_count as i32;
    vertex_segments[*segment_index + 1] = facing as i32;
    *segment_index += 2;

    if vertex_count != 0 {
        encode_builder_segment(
            &builder.buffers[facing],
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
    }

    Ok(())
}

fn encode_builder_segment(
    buffer: &NativeQuadBuffer,
    vertex_count: usize,
    output: &mut [u8],
    output_vertex_offset: &mut usize,
    format: NativeFormat,
) -> Result<(), i32> {
    if vertex_count % 4 != 0 {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let byte_offset = output_vertex_offset
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let byte_len = vertex_count
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let output_slice = output
        .get_mut(byte_offset..byte_offset + byte_len)
        .ok_or(ERR_CAPACITY)?;

    if buffer.encoded_format == Some(format) && buffer.encoded.len() >= byte_len {
        output_slice.copy_from_slice(&buffer.encoded[..byte_len]);
    } else {
        if buffer.quads.len() < vertex_count / 4 {
            return Err(ERR_INVALID_ARGUMENT);
        }
        let input_address = buffer.quads.as_ptr() as u64;
        encode_segment(
            input_address,
            vertex_count,
            output,
            output_vertex_offset,
            format,
        )?;
        return Ok(());
    }

    *output_vertex_offset += vertex_count;
    Ok(())
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

fn create_section_mesh_builder(capacity: usize) -> NativeSectionMeshBuilder {
    NativeSectionMeshBuilder {
        buffers: std::array::from_fn(|_| NativeQuadBuffer {
            quads: vec![NativeQuad::default(); capacity],
            encoded: Vec::new(),
            encoded_format: None,
        }),
        pending: std::array::from_fn(|_| NativePendingQuadBuffer {
            quads: vec![NativeQuad::default(); PENDING_BATCH_QUAD_CAPACITY],
            packed_normals: vec![0; PENDING_BATCH_QUAD_CAPACITY],
            validity: vec![0; PENDING_BATCH_QUAD_CAPACITY],
        }),
        counts: [0; MODEL_QUAD_FACING_COUNT],
    }
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

unsafe fn section_builder_append_batch_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    batch_address: u64,
    quad_count: usize,
    validity: Option<&[u8]>,
    format: NativeFormat,
    store_raw_quads: bool,
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
    let buffer = &mut builder.buffers[facing];

    if store_raw_quads && buffer.quads.len() < required_len {
        buffer.quads.resize(required_len, NativeQuad::default());
    }

    let encoded_quad_len = 4usize
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;

    if !buffer.encoded.is_empty() && buffer.encoded_format != Some(format) {
        buffer.encoded.clear();
        buffer.encoded_format = None;
    }
    if buffer.encoded_format.is_none() {
        buffer.encoded_format = Some(format);
    }

    let required_encoded_len = required_len
        .checked_mul(encoded_quad_len)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    if buffer.encoded.len() < required_encoded_len {
        buffer.encoded.resize(required_encoded_len, 0);
    }

    let mut output_index = 0usize;

    for index in 0..quad_count {
        let is_valid = match validity {
            Some(mask) => mask[index] != 0,
            None => true,
        };

        if is_valid {
            let quad = input[index];
            if store_raw_quads {
                buffer.quads[start + output_index] = quad;
            }
            let encoded_start = (start + output_index) * encoded_quad_len;
            let encoded_end = encoded_start + encoded_quad_len;
            encode_quad(
                &quad,
                &mut buffer.encoded[encoded_start..encoded_end],
                format,
            );
            output_index += 1;
        }
    }

    builder.counts[facing] = required_len;
    Ok(valid_count as i32)
}

fn section_builder_staging_addresses(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
) -> Result<(u64, u64, u64, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let pending = &mut builder.pending[facing];
    Ok((
        pending.quads.as_mut_ptr() as u64,
        pending.packed_normals.as_mut_ptr() as u64,
        pending.validity.as_mut_ptr() as u64,
        pending.quads.len() as i32,
    ))
}

unsafe fn section_builder_encode_scattered_unassigned(
    builder: &NativeSectionMeshBuilder,
    output_vertex_offsets: *const i32,
    update_count: i32,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
) -> i32 {
    if update_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if update_count == 0 {
        return OK;
    }
    if output_vertex_offsets.is_null() || output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let input_address = builder.buffers[MODEL_QUAD_FACING_UNASSIGNED].quads.as_ptr() as u64;
    encode_scattered(
        input_address,
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

unsafe fn updated_quads_apply(
    updated_quads: &NativeUpdatedQuads,
    output_address: u64,
    output_capacity: i32,
    format: NativeFormat,
    material_bits: i32,
) -> i32 {
    if output_capacity < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if updated_quads.quads.is_empty() {
        return OK;
    }
    if output_address == 0 {
        return ERR_NULL_POINTER;
    }

    let mut update_builder = create_section_mesh_builder(updated_quads.quads.len());
    let mut output_vertex_offsets = Vec::with_capacity(updated_quads.quads.len());

    for &quad_handle in &updated_quads.quads {
        let mut write_to_index = -1;
        let status = translucent::native_full_quad_write_to_index(quad_handle, &mut write_to_index);
        if status != OK {
            return status;
        }
        if write_to_index < 0 {
            continue;
        }

        let quad_address =
            match section_builder_prepare_quad(&mut update_builder, MODEL_QUAD_FACING_UNASSIGNED) {
                Ok(value) => value,
                Err(status) => return status,
            };
        let status = translucent::native_full_quad_write_to_native_buffer(
            quad_handle,
            quad_address,
            material_bits,
        );
        if status != OK {
            return status;
        }
        update_builder.counts[MODEL_QUAD_FACING_UNASSIGNED] += 1;

        let Some(vertex_offset) = write_to_index.checked_mul(4) else {
            return ERR_INVALID_ARGUMENT;
        };
        output_vertex_offsets.push(vertex_offset);
    }

    section_builder_encode_scattered_unassigned(
        &update_builder,
        output_vertex_offsets.as_ptr(),
        output_vertex_offsets.len() as i32,
        output_address,
        output_capacity,
        format,
    )
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
pub extern "C" fn mattmc_sodium_chunk_compact_format_value(value: i32) -> i32 {
    compact_format_value(value)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_write_metadata(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
) -> i32 {
    write_native_quad_metadata(
        quad_address,
        block_emission,
        render_type,
        ignore_mid_block,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_write_vertex(
    quad_address: u64,
    vertex_index: i32,
    x: f32,
    y: f32,
    z: f32,
    color: i32,
    ao: f32,
    u: f32,
    v: f32,
    light: i32,
) -> i32 {
    write_native_quad_vertex(quad_address, vertex_index, x, y, z, color, ao, u, v, light)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_write(
    quad_address: u64,
    block_emission: i32,
    render_type: i32,
    ignore_mid_block: i32,
    block_id: i32,
    local_x: i32,
    local_y: i32,
    local_z: i32,
    material_bits: i32,
    x0: f32,
    y0: f32,
    z0: f32,
    color0: i32,
    ao0: f32,
    u0: f32,
    v0: f32,
    light0: i32,
    x1: f32,
    y1: f32,
    z1: f32,
    color1: i32,
    ao1: f32,
    u1: f32,
    v1: f32,
    light1: i32,
    x2: f32,
    y2: f32,
    z2: f32,
    color2: i32,
    ao2: f32,
    u2: f32,
    v2: f32,
    light2: i32,
    x3: f32,
    y3: f32,
    z3: f32,
    color3: i32,
    ao3: f32,
    u3: f32,
    v3: f32,
    light3: i32,
) -> i32 {
    write_native_quad(
        quad_address,
        block_emission,
        render_type,
        ignore_mid_block,
        block_id,
        local_x,
        local_y,
        local_z,
        material_bits,
        x0,
        y0,
        z0,
        color0,
        ao0,
        u0,
        v0,
        light0,
        x1,
        y1,
        z1,
        color1,
        ao1,
        u1,
        v1,
        light1,
        x2,
        y2,
        z2,
        color2,
        ao2,
        u2,
        v2,
        light2,
        x3,
        y3,
        z3,
        color3,
        ao3,
        u3,
        v3,
        light3,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_chunk_native_quad_position(
    quad_address: u64,
    vertex_index: i32,
    component: i32,
) -> f32 {
    native_quad_position(quad_address, vertex_index, component)
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
    let builder = create_section_mesh_builder(capacity);

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
    for buffer in &mut builder.buffers {
        buffer.encoded.clear();
        buffer.encoded_format = None;
    }
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_batch_encoded(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
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

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_batch_encoded(
        builder,
        facing as usize,
        batch_address,
        quad_count as usize,
        None,
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_batch_encoded(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    packed_normals_address: u64,
    quad_stride: i32,
    vertex_stride: i32,
    block_id_offset: i32,
    normal_offset: i32,
    tangent_offset: i32,
    mid_uv_offset: i32,
    mid_block_offset: i32,
    section_index: i32,
    separate_ao: i32,
    store_raw_quads: i32,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 || packed_normals_address == 0 {
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

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    let facing = facing as usize;
    if facing >= MODEL_QUAD_FACING_COUNT || quad_count as usize > PENDING_BATCH_QUAD_CAPACITY {
        return ERR_INVALID_ARGUMENT;
    }

    let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
    let mut valid_count = 0i32;
    let status = translucent::append_native_quad_batch_to_analyzer(
        analyzer_handle,
        batch_address,
        quad_count,
        translucent_facing,
        packed_normals_address as *const i32,
        validity_address,
        &mut valid_count,
    );
    if status != OK {
        return status;
    }

    match section_builder_append_batch_encoded(
        builder,
        facing,
        batch_address,
        quad_count as usize,
        Some(slice::from_raw_parts(
            validity_address as *const u8,
            quad_count as usize,
        )),
        format,
        store_raw_quads != 0,
    ) {
        Ok(committed_count) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_batch(
    handle: u64,
    facing: i32,
    batch_address: u64,
    quad_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    packed_normals_address: u64,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || quad_count < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 || packed_normals_address == 0 {
        return ERR_NULL_POINTER;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    let facing = facing as usize;
    if facing >= MODEL_QUAD_FACING_COUNT || quad_count as usize > PENDING_BATCH_QUAD_CAPACITY {
        return ERR_INVALID_ARGUMENT;
    }

    let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
    let mut valid_count = 0i32;
    let status = translucent::append_native_quad_batch_to_analyzer(
        analyzer_handle,
        batch_address,
        quad_count,
        translucent_facing,
        packed_normals_address as *const i32,
        validity_address,
        &mut valid_count,
    );
    if status != OK {
        return status;
    }

    match section_builder_append_batch(
        builder,
        facing,
        batch_address,
        quad_count as usize,
        Some(slice::from_raw_parts(
            validity_address as *const u8,
            quad_count as usize,
        )),
    ) {
        Ok(committed_count) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_staging_addresses(
    handle: u64,
    facing: i32,
    output_quad_address: *mut u64,
    output_packed_normals_address: *mut u64,
    output_validity_address: *mut u64,
    output_capacity: *mut i32,
) -> i32 {
    if handle == 0
        || output_quad_address.is_null()
        || output_packed_normals_address.is_null()
        || output_validity_address.is_null()
        || output_capacity.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if facing < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_staging_addresses(builder, facing as usize) {
        Ok((quad_address, packed_normals_address, validity_address, capacity)) => {
            *output_quad_address = quad_address;
            *output_packed_normals_address = packed_normals_address;
            *output_validity_address = validity_address;
            *output_capacity = capacity;
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
    assemble_section_builder(
        builder,
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_encode_scattered_unassigned(
    handle: u64,
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

    section_builder_encode_scattered_unassigned(
        &*(handle as *const NativeSectionMeshBuilder),
        output_vertex_offsets,
        update_count,
        output_address,
        output_capacity,
        format,
    )
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_create(output_handle: *mut u64) -> i32 {
    if output_handle.is_null() {
        return ERR_NULL_POINTER;
    }

    *output_handle = Box::into_raw(Box::new(NativeUpdatedQuads {
        quads: Vec::new(),
        mesh_quad_count: 0,
        index_quad_count: 0,
    })) as u64;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_destroy(handle: u64) -> i32 {
    if handle == 0 {
        return OK;
    }

    drop(Box::from_raw(handle as *mut NativeUpdatedQuads));
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_add(handle: u64, quad_handle: u64) -> i32 {
    if handle == 0 || quad_handle == 0 {
        return ERR_NULL_POINTER;
    }

    let updated_quads = &mut *(handle as *mut NativeUpdatedQuads);
    updated_quads.quads.push(quad_handle);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_set_counts(
    handle: u64,
    mesh_quad_count: i32,
    index_quad_count: i32,
) -> i32 {
    if handle == 0 {
        return ERR_NULL_POINTER;
    }
    if mesh_quad_count < 0 || index_quad_count < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let updated_quads = &mut *(handle as *mut NativeUpdatedQuads);
    updated_quads.mesh_quad_count = mesh_quad_count;
    updated_quads.index_quad_count = index_quad_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_counts(
    handle: u64,
    output_counts: *mut i32,
    output_counts_len: i32,
) -> i32 {
    if handle == 0 || output_counts.is_null() {
        return ERR_NULL_POINTER;
    }
    if output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }

    let updated_quads = &*(handle as *const NativeUpdatedQuads);
    *output_counts = updated_quads.mesh_quad_count;
    *output_counts.add(1) = updated_quads.index_quad_count;
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_updated_quads_apply(
    handle: u64,
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
    material_bits: i32,
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

    let updated_quads = &*(handle as *const NativeUpdatedQuads);
    updated_quads_apply(
        updated_quads,
        output_address,
        output_capacity,
        format,
        material_bits,
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
        encoded: Vec::new(),
        encoded_format: None,
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
    fn compact_format_metadata_is_rust_owned() {
        assert_eq!(
            20,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_STRIDE)
        );
        assert_eq!(
            0,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_POSITION_OFFSET)
        );
        assert_eq!(
            8,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_COLOR_OFFSET)
        );
        assert_eq!(
            12,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_TEXTURE_OFFSET)
        );
        assert_eq!(
            16,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET)
        );
        assert_eq!(
            0,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_BLOCK_ID_OFFSET)
        );
        assert_eq!(
            1 << 20,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_POSITION_MAX_VALUE)
        );
        assert_eq!(
            1 << 15,
            mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_TEXTURE_MAX_VALUE)
        );
    }

    #[test]
    fn native_quad_write_helpers_populate_rust_owned_layout() {
        let mut quad = NativeQuad::default();
        let address = &mut quad as *mut NativeQuad as u64;

        unsafe {
            assert_eq!(
                OK,
                mattmc_sodium_chunk_native_quad_write_metadata(address, 13, 2, 1, 99, 4, 5, 6, 7)
            );
            assert_eq!(
                OK,
                mattmc_sodium_chunk_native_quad_write_vertex(
                    address, 2, 1.25, 2.5, 3.75, 0x11223344, 0.875, 0.125, 0.625, 0x00f000f0,
                )
            );
        }

        assert_eq!(13, quad.block_emission);
        assert_eq!(2, quad.render_type);
        assert_eq!(1, quad.ignore_mid_block);
        assert_eq!(0, quad._padding);
        assert_eq!(99, quad.block_id);
        assert_eq!(4, quad.local_x);
        assert_eq!(5, quad.local_y);
        assert_eq!(6, quad.local_z);
        assert_eq!(7, quad.material_bits);
        assert_eq!(1.25, quad.vertices[2].x);
        assert_eq!(2.5, unsafe {
            mattmc_sodium_chunk_native_quad_position(address, 2, 1)
        });
        assert_eq!(3.75, unsafe {
            mattmc_sodium_chunk_native_quad_position(address, 2, 2)
        });
        assert_eq!(0x11223344, quad.vertices[2].color);
        assert_eq!(0.875, quad.vertices[2].ao);
        assert_eq!(0.125, quad.vertices[2].u);
        assert_eq!(0.625, quad.vertices[2].v);
        assert_eq!(0x00f000f0, quad.vertices[2].light);
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

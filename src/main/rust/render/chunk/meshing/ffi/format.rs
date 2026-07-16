//! Format metadata, native quad helpers, direct encode, scattered encode, and output assembly exports.

use super::*;

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

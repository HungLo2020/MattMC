//! Translucent analyzer append exports that retain raw quad data and callback fallback compatibility.

use super::*;
use crate::render::chunk::translucent as chunk_translucent;

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
    let status = chunk_translucent::append_native_quad_batch_to_analyzer(
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

    match section_builder_append_batch_encoded_with_kind(
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
        Some(TERRAIN_PRIMITIVE_NON_FLUID_TRANSLUCENT),
        None,
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_flat_quad_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    record_stride: i32,
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
    if facing < 0 || record_count < 0 || record_stride < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 {
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
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_flat_quad_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        Some((analyzer_handle, translucent_facing)),
        format,
        store_raw_quads != 0,
        Some(TERRAIN_PRIMITIVE_NON_FLUID_TRANSLUCENT),
    ) {
        Ok((valid_count, committed_count)) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_translucent_fluid_face_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
    analyzer_handle: u64,
    translucent_facing: i32,
    record_stride: i32,
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
    if facing < 0 || record_count < 0 || record_stride < 0 || output_counts_len < 2 {
        return ERR_INVALID_ARGUMENT;
    }
    if record_count == 0 {
        *output_counts = 0;
        *output_counts.add(1) = 0;
        return OK;
    }
    if analyzer_handle == 0 {
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
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_fluid_face_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
        Some((analyzer_handle, translucent_facing)),
        format,
        store_raw_quads != 0,
    ) {
        Ok((valid_count, committed_count)) => {
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
    let status = chunk_translucent::append_native_quad_batch_to_analyzer(
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

    match section_builder_append_batch_with_kind(
        builder,
        facing,
        batch_address,
        quad_count as usize,
        Some(slice::from_raw_parts(
            validity_address as *const u8,
            quad_count as usize,
        )),
        Some(TERRAIN_PRIMITIVE_NON_FLUID_TRANSLUCENT),
    ) {
        Ok(committed_count) => {
            *output_counts = valid_count;
            *output_counts.add(1) = committed_count;
            OK
        }
        Err(status) => status,
    }
}

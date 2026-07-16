//! Section mesh builder lifecycle and non-translucent staging append exports.

use super::*;

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
    builder.profile.reset();
    builder.section_pass_cache_valid = false;
    builder.section_pass_cache_address = 0;
    builder.section_pass_cache_count = 0;
    builder.section_pass_cache_mask = 0;
    builder.fluid_sprite_mask = 0;
    for buffer in &mut builder.buffers {
        buffer.encoded.clear();
        buffer.encoded_format = None;
    }
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_fluid_sprite_mask(
    handle: u64,
    output_mask: *mut i32,
) -> i32 {
    if handle == 0 || output_mask.is_null() {
        return ERR_NULL_POINTER;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    *output_mask = builder.fluid_sprite_mask;
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_flat_quad_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
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
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 {
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
        None,
        format,
        store_raw_quads != 0,
    ) {
        Ok((_, committed_count)) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_light_block_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
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
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 {
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
    let record_stride = match usize::try_from(record_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_append_light_block_records_encoded(
        builder,
        facing as usize,
        record_address,
        record_count as usize,
        record_stride,
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_fluid_face_batch_encoded(
    handle: u64,
    facing: i32,
    record_address: u64,
    record_count: i32,
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
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if facing < 0 || record_count < 0 || record_stride < 0 {
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
        None,
        format,
        store_raw_quads != 0,
    ) {
        Ok((_, committed_count)) => {
            *output_committed_count = committed_count;
            OK
        }
        Err(status) => status,
    }
}

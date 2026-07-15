//! Builder staging-address, counts, profile-copy, assembly, and scattered update exports.

use super::*;

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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_record_staging_addresses(
    handle: u64,
    facing: i32,
    output_flat_quad_record_address: *mut u64,
    output_light_block_record_address: *mut u64,
    output_fluid_face_record_address: *mut u64,
    output_static_model_block_record_address: *mut u64,
    output_capacity: *mut i32,
) -> i32 {
    if handle == 0
        || output_flat_quad_record_address.is_null()
        || output_light_block_record_address.is_null()
        || output_fluid_face_record_address.is_null()
        || output_static_model_block_record_address.is_null()
        || output_capacity.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if facing < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
    match section_builder_record_staging_addresses(builder, facing as usize) {
        Ok((
            flat_quad_record_address,
            light_block_record_address,
            fluid_face_record_address,
            static_model_block_record_address,
            capacity,
        )) => {
            *output_flat_quad_record_address = flat_quad_record_address;
            *output_light_block_record_address = light_block_record_address;
            *output_fluid_face_record_address = fluid_face_record_address;
            *output_static_model_block_record_address = static_model_block_record_address;
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_copy_profile(
    handle: u64,
    output_values: *mut i64,
    output_len: i32,
) -> i32 {
    if handle == 0 || output_values.is_null() {
        return ERR_NULL_POINTER;
    }
    if output_len < PROFILE_EXPORT_LONGS as i32 {
        return ERR_INVALID_ARGUMENT;
    }

    let builder = &*(handle as *const NativeSectionMeshBuilder);
    let output = slice::from_raw_parts_mut(output_values, PROFILE_EXPORT_LONGS);
    for (index, value) in builder.profile.stage_nanos.iter().enumerate() {
        output[index] = *value as i64;
    }
    for (index, value) in builder.profile.counts.iter().enumerate() {
        output[PROFILE_STAGE_COUNT + index] = *value as i64;
    }
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

    let builder = &mut *(handle as *mut NativeSectionMeshBuilder);
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

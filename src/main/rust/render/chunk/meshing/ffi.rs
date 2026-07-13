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

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_static_model_cache_clear() -> i32 {
    let Ok(mut cache) = static_model_cache().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    cache.clear();
    drop(cache);
    let Ok(mut selectors) = native_model_selectors().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    selectors.clear();
    drop(selectors);
    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    states.clear();
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_static_model_cache_register(
    model_id: i32,
    quad_address: u64,
    quad_count: i32,
    quad_stride: i32,
) -> i32 {
    if model_id < 0 || quad_count < 0 || quad_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if quad_count > 0 && quad_address == 0 {
        return ERR_NULL_POINTER;
    }
    let quad_stride = match usize::try_from(quad_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if quad_stride != std::mem::size_of::<StaticModelQuadRecord>() {
        return ERR_INVALID_ARGUMENT;
    }

    let quads = if quad_count == 0 {
        Vec::new()
    } else {
        slice::from_raw_parts(
            quad_address as *const StaticModelQuadRecord,
            quad_count as usize,
        )
        .to_vec()
    };

    let Ok(mut cache) = static_model_cache().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut cache, model_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    cache[index] = Some(quads);
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_model_selector_register(
    selector_id: i32,
    kind: i32,
    entry_address: u64,
    entry_count: i32,
    entry_stride: i32,
) -> i32 {
    if selector_id < 0 || entry_count < 0 || entry_stride < 0 {
        return ERR_INVALID_ARGUMENT;
    }
    if entry_count > 0 && entry_address == 0 {
        return ERR_NULL_POINTER;
    }
    let entry_stride = match usize::try_from(entry_stride) {
        Ok(value) => value,
        Err(_) => return ERR_INVALID_ARGUMENT,
    };
    if entry_stride != std::mem::size_of::<NativeModelSelectorEntry>() {
        return ERR_INVALID_ARGUMENT;
    }

    let entries = if entry_count == 0 {
        Vec::new()
    } else {
        slice::from_raw_parts(
            entry_address as *const NativeModelSelectorEntry,
            entry_count as usize,
        )
        .to_vec()
    };
    let total_weight = entries
        .iter()
        .filter(|entry| entry.weight > 0)
        .map(|entry| entry.weight)
        .sum();

    let Ok(mut selectors) = native_model_selectors().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut selectors, selector_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    selectors[index] = Some(NativeModelSelector {
        kind,
        entries,
        total_weight,
    });
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_native_meshing_state_register(
    state_id: i32,
    selector_id: i32,
    flags: i32,
    material_bits: i32,
    pass_id: i32,
    block_emission: i32,
    render_type: i32,
    block_id: i32,
    fluid_material_bits: i32,
    fluid_pass_id: i32,
    fluid_block_id: i32,
    skip_group: i32,
    fluid_type: i32,
    fluid_own_height: f32,
    fluid_falling: i32,
    offset_type: i32,
    max_horizontal_offset: f32,
    max_vertical_offset: f32,
    tint_type: i32,
    fluid_still_u0: f32,
    fluid_still_u1: f32,
    fluid_still_v0: f32,
    fluid_still_v1: f32,
    fluid_still_shrink: f32,
    fluid_flow_u0: f32,
    fluid_flow_u1: f32,
    fluid_flow_v0: f32,
    fluid_flow_v1: f32,
    fluid_flow_shrink: f32,
    fluid_overlay_u0: f32,
    fluid_overlay_u1: f32,
    fluid_overlay_v0: f32,
    fluid_overlay_v1: f32,
    fluid_overlay_shrink: f32,
    fluid_overlay_valid: i32,
) -> i32 {
    if state_id < 0 {
        return ERR_INVALID_ARGUMENT;
    }

    let Ok(mut states) = native_meshing_states().lock() else {
        return ERR_INVALID_ARGUMENT;
    };
    let Ok(index) = ensure_table_slot(&mut states, state_id) else {
        return ERR_INVALID_ARGUMENT;
    };
    states[index] = Some(NativeMeshingState {
        selector_id,
        flags,
        material_bits,
        pass_id,
        block_emission,
        render_type,
        block_id,
        fluid_material_bits,
        fluid_pass_id,
        fluid_block_id,
        skip_group,
        fluid_type,
        fluid_own_height,
        fluid_falling,
        offset_type,
        max_horizontal_offset,
        max_vertical_offset,
        tint_type,
        fluid_still: FluidSprite {
            u0: fluid_still_u0,
            u1: fluid_still_u1,
            v0: fluid_still_v0,
            v1: fluid_still_v1,
            shrink: fluid_still_shrink,
        },
        fluid_flow: FluidSprite {
            u0: fluid_flow_u0,
            u1: fluid_flow_u1,
            v0: fluid_flow_v0,
            v1: fluid_flow_v1,
            shrink: fluid_flow_shrink,
        },
        fluid_overlay: FluidSprite {
            u0: fluid_overlay_u0,
            u1: fluid_overlay_u1,
            v0: fluid_overlay_v0,
            v1: fluid_overlay_v1,
            shrink: fluid_overlay_shrink,
        },
        fluid_overlay_valid,
    });
    OK
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_static_model_batch_encoded(
    handle: u64,
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
    if record_count < 0 || record_stride < 0 {
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
    match section_builder_append_static_model_records_encoded(
        builder,
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builder_append_native_section_encoded(
    handle: u64,
    record_address: u64,
    record_count: i32,
    record_stride: i32,
    pass_id: i32,
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
    analyzer_handle: u64,
    output_committed_count: *mut i32,
) -> i32 {
    if handle == 0 || output_committed_count.is_null() {
        return ERR_NULL_POINTER;
    }
    if record_count < 0 || record_stride < 0 {
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
    match section_builder_append_native_section_records_encoded(
        builder,
        record_address,
        record_count as usize,
        record_stride,
        pass_id,
        if analyzer_handle == 0 {
            None
        } else {
            Some(analyzer_handle)
        },
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
pub unsafe extern "C" fn mattmc_sodium_section_mesh_builders_append_native_section_all_passes_encoded(
    solid_handle: u64,
    cutout_handle: u64,
    translucent_handle: u64,
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
    translucent_analyzer_handle: u64,
    output_committed_counts: *mut i32,
) -> i32 {
    if solid_handle == 0
        || cutout_handle == 0
        || translucent_handle == 0
        || output_committed_counts.is_null()
    {
        return ERR_NULL_POINTER;
    }
    if record_count < 0 || record_stride < 0 {
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

    let solid_builder = &mut *(solid_handle as *mut NativeSectionMeshBuilder);
    let cutout_builder = &mut *(cutout_handle as *mut NativeSectionMeshBuilder);
    let translucent_builder = &mut *(translucent_handle as *mut NativeSectionMeshBuilder);
    match section_builders_append_native_section_records_all_passes_encoded(
        solid_builder,
        cutout_builder,
        translucent_builder,
        record_address,
        record_count as usize,
        record_stride,
        if translucent_analyzer_handle == 0 {
            None
        } else {
            Some(translucent_analyzer_handle)
        },
        format,
    ) {
        Ok(committed_counts) => {
            let output = slice::from_raw_parts_mut(output_committed_counts, 3);
            output.copy_from_slice(&committed_counts);
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

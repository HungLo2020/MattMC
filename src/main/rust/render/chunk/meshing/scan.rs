use super::*;

pub(super) unsafe fn section_builder_append_native_section_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    pass_id: i32,
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if record_count == 0 {
        return Ok(0);
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<NativeSectionBlockRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if pass_id >= 0
        && pass_id < 32
        && builder.section_pass_cache_valid
        && builder.section_pass_cache_address == record_address
        && builder.section_pass_cache_count == record_count
        && (builder.section_pass_cache_mask & (1u32 << pass_id)) == 0
    {
        return Ok(0);
    }

    let records = slice::from_raw_parts(
        record_address as *const NativeSectionBlockRecord,
        record_count,
    );
    let emit_all_passes = pass_id < 0;
    builder
        .profile
        .add_count(PROFILE_COUNT_SCANNED_BLOCKS, record_count);
    let metadata_started = Instant::now();
    let states_guard = native_meshing_states()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let selectors_guard = native_model_selectors()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let models_guard = static_model_cache()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    builder
        .profile
        .add_stage(PROFILE_MATERIAL_PASS, metadata_started);
    let mut total_committed = 0i32;
    let mut pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];
    let mut model_ids = Vec::with_capacity(8);
    let mut fluid_faces = Vec::with_capacity(8);
    let mut last_state_id = i32::MIN;
    let mut last_state = None;
    let mut last_direct_selector_id = i32::MIN;
    let mut last_direct_selector_model_id = None;
    let mut last_model_id = i32::MIN;
    let mut last_model = None;
    let mut discovered_pass_mask = 0u32;

    let scan_started = Instant::now();
    for record in records {
        let state = if record.state_id == last_state_id {
            last_state
        } else {
            last_state_id = record.state_id;
            last_state = state_by_id(&states_guard, record.state_id);
            last_state
        };
        let Some(state) = state else {
            continue;
        };
        if (state.flags & STATE_FLAG_AIR) != 0 {
            continue;
        }
        if (state.flags & STATE_FLAG_LIGHT_BLOCK) != 0 {
            discovered_pass_mask |= 1u32 << 1;
        }
        if (state.flags & STATE_FLAG_MODEL) != 0 && state.pass_id >= 0 && state.pass_id < 32 {
            discovered_pass_mask |= 1u32 << state.pass_id;
        }
        if (state.flags & STATE_FLAG_FLUID) != 0
            && (record.flags & NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID) == 0
            && state.fluid_pass_id >= 0
            && state.fluid_pass_id < 32
        {
            discovered_pass_mask |= 1u32 << state.fluid_pass_id;
        }

        if (state.flags & STATE_FLAG_LIGHT_BLOCK) != 0 && (emit_all_passes || pass_id == 1) {
            let model_started = Instant::now();
            push_native_section_quad(
                builder,
                light_block_record_to_quad(LightBlockRecord {
                    material_bits: state.material_bits,
                    block_emission: state.block_emission,
                    block_id: choose_block_id(record.block_id, state.block_id),
                    local_x: record.local_x,
                    local_y: record.local_y,
                    local_z: record.local_z,
                }),
                0,
                MODEL_QUAD_FACING_UNASSIGNED,
                &mut pending_counts,
                analyzer,
                format,
                store_raw_quads,
                &mut total_committed,
            )?;
            builder
                .profile
                .add_stage(PROFILE_MODEL_LOOKUP_EMIT, model_started);
            builder
                .profile
                .add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
        }

        if (state.flags & STATE_FLAG_MODEL) != 0 && (emit_all_passes || state.pass_id == pass_id) {
            let model_started = Instant::now();
            builder
                .profile
                .add_count(PROFILE_COUNT_NATIVE_MODEL_BLOCKS, 1);
            model_ids.clear();
            if state.selector_id == last_direct_selector_id {
                if let Some(model_id) = last_direct_selector_model_id {
                    model_ids.push(model_id);
                }
            } else {
                let direct_model_id =
                    selector_by_id(&selectors_guard, state.selector_id).and_then(|selector| {
                        if selector.kind == SELECTOR_DIRECT {
                            selector.entries.first().map(|entry| entry.target_id)
                        } else {
                            None
                        }
                    });
                if let Some(model_id) = direct_model_id {
                    last_direct_selector_id = state.selector_id;
                    last_direct_selector_model_id = Some(model_id);
                    model_ids.push(model_id);
                } else {
                    last_direct_selector_id = i32::MIN;
                    last_direct_selector_model_id = None;
                    resolve_selector_model_ids(
                        state.selector_id,
                        record_seed(*record),
                        &selectors_guard,
                        &mut model_ids,
                    )?;
                }
            }

            for model_id in &model_ids {
                let model = if *model_id == last_model_id {
                    last_model
                } else {
                    last_model_id = *model_id;
                    last_model = model_by_id(&models_guard, *model_id);
                    last_model
                };
                let Some(model) = model else {
                    continue;
                };

                for quad_record in model {
                    if native_section_culls_quad(record, state, *quad_record, &states_guard) {
                        continue;
                    }

                    let facing = match usize::try_from(quad_record.normal_face) {
                        Ok(value) if value < MODEL_QUAD_FACING_COUNT => value,
                        _ => MODEL_QUAD_FACING_UNASSIGNED,
                    };
                    let quad = static_model_quad_to_native_section(*record, state, *quad_record);
                    push_native_section_quad(
                        builder,
                        quad,
                        quad_record.packed_normal,
                        facing,
                        &mut pending_counts,
                        analyzer,
                        format,
                        store_raw_quads,
                        &mut total_committed,
                    )?;
                    builder
                        .profile
                        .add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
                }
            }
            builder
                .profile
                .add_stage(PROFILE_MODEL_LOOKUP_EMIT, model_started);
        }

        if (state.flags & STATE_FLAG_FLUID) != 0
            && (record.flags & NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID) == 0
            && (emit_all_passes || state.fluid_pass_id == pass_id)
        {
            builder.profile.add_count(PROFILE_COUNT_FLUID_BLOCKS, 1);
            fluid_faces.clear();
            native_section_fluid_faces(
                record,
                state,
                &states_guard,
                &mut fluid_faces,
                &mut builder.profile,
            );
            builder
                .profile
                .add_count(PROFILE_COUNT_FLUID_FACES, fluid_faces.len());
            for fluid_face in &fluid_faces {
                push_native_section_quad(
                    builder,
                    fluid_face.quad,
                    fluid_face.packed_normal,
                    fluid_face.facing,
                    &mut pending_counts,
                    analyzer,
                    format,
                    store_raw_quads,
                    &mut total_committed,
                )?;
            }
        }
    }
    builder
        .profile
        .add_stage(PROFILE_SECTION_SCAN, scan_started);
    if pass_id >= 0 && pass_id < 32 {
        builder.section_pass_cache_address = record_address;
        builder.section_pass_cache_count = record_count;
        builder.section_pass_cache_mask = discovered_pass_mask;
        builder.section_pass_cache_valid = true;
    }

    for facing in 0..MODEL_QUAD_FACING_COUNT {
        flush_static_model_pending_face(
            builder,
            facing,
            &mut pending_counts,
            analyzer,
            format,
            store_raw_quads,
            &mut total_committed,
        )?;
    }

    Ok(total_committed)
}

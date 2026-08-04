//! Compact all-pass production scanner.
//!
//! Active indexes are visited exactly once through `NativeSectionRecordSource`.
//! Each active record is decoded, early-skipped when missing, air, or fully
//! occluded, then dispatched to light-block, model, and fluid producers. Model
//! and fluid outputs are routed by native render pass while preserving one-pass
//! snapshot traversal and per-stage profile accounting.

use std::time::Instant;

use crate::render::chunk::meshing::section::{CompactSectionSnapshot, NativeSectionRecordSource};

use super::*;
pub(in crate::render::chunk::meshing) struct AllPassEmitTarget {
    pub(in crate::render::chunk::meshing) builder: *mut NativeSectionMeshBuilder,
    pub(in crate::render::chunk::meshing) analyzer: Option<u64>,
    pending_counts: [usize; MODEL_QUAD_FACING_COUNT],
    total_committed: i32,
}

impl AllPassEmitTarget {
    #[inline(always)]
    unsafe fn builder(&mut self) -> &mut NativeSectionMeshBuilder {
        &mut *self.builder
    }

    #[inline(always)]
    unsafe fn profile(&mut self) -> &mut NativeMeshingProfile {
        &mut (*self.builder).profile
    }

    unsafe fn push_quad(
        &mut self,
        quad: NativeQuad,
        packed_normal: i32,
        facing: usize,
        format: NativeFormat,
        profile_staging_substages: bool,
    ) -> Result<(), i32> {
        let builder = &mut *self.builder;
        push_native_section_quad(
            builder,
            quad,
            packed_normal,
            TERRAIN_PRIMITIVE_UNKNOWN,
            facing,
            &mut self.pending_counts,
            self.analyzer,
            format,
            false,
            profile_staging_substages,
            &mut self.total_committed,
        )
    }

    unsafe fn flush_pending_face(
        &mut self,
        facing: usize,
        format: NativeFormat,
        profile_staging_substages: bool,
    ) -> Result<(), i32> {
        let builder = &mut *self.builder;
        flush_static_model_pending_face(
            builder,
            facing,
            &mut self.pending_counts,
            self.analyzer,
            format,
            false,
            &mut self.total_committed,
        )?;
        let _ = profile_staging_substages;
        Ok(())
    }

    unsafe fn emit_fluid_faces(
        &mut self,
        record: &NativeSectionBlockRecord,
        state: NativeMeshingState,
        states: &[Option<NativeMeshingState>],
        format: NativeFormat,
        profile_scan_substages: bool,
        profile_staging_substages: bool,
    ) -> Result<usize, i32> {
        let builder = &mut *self.builder;
        emit_native_section_fluid_faces(
            record,
            state,
            states,
            builder,
            &mut self.pending_counts,
            self.analyzer,
            format,
            false,
            profile_scan_substages,
            profile_staging_substages,
            &mut self.total_committed,
        )
    }
}

#[inline(always)]
pub(in crate::render::chunk::meshing) unsafe fn flush_all_pass_target(
    target: &mut AllPassEmitTarget,
    format: NativeFormat,
) -> Result<(), i32> {
    for facing in 0..MODEL_QUAD_FACING_COUNT {
        if target.pending_counts[facing] != 0 {
            target.flush_pending_face(facing, format, false)?;
        }
    }
    Ok(())
}

#[inline(always)]
fn can_direct_emit_static_model_quad(
    target: &AllPassEmitTarget,
    routed_pass: usize,
    format: NativeFormat,
) -> bool {
    target.analyzer.is_none() && routed_pass != 2 && is_compact_fast_format(format)
}

pub(in crate::render::chunk::meshing) unsafe fn section_builders_append_compact_native_section_all_passes_encoded(
    solid_builder: &mut NativeSectionMeshBuilder,
    cutout_builder: &mut NativeSectionMeshBuilder,
    translucent_builder: &mut NativeSectionMeshBuilder,
    snapshot_address: u64,
    translucent_analyzer: Option<u64>,
    format: NativeFormat,
) -> Result<[i32; 3], i32> {
    let source = CompactSectionSnapshot::from_address(snapshot_address)?;
    section_builders_append_native_section_source_all_passes_encoded(
        solid_builder,
        cutout_builder,
        translucent_builder,
        &source,
        translucent_analyzer,
        format,
    )
}

pub(in crate::render::chunk::meshing) unsafe fn section_builders_append_native_section_source_all_passes_encoded<
    S: NativeSectionRecordSource,
>(
    solid_builder: &mut NativeSectionMeshBuilder,
    cutout_builder: &mut NativeSectionMeshBuilder,
    translucent_builder: &mut NativeSectionMeshBuilder,
    source: &S,
    translucent_analyzer: Option<u64>,
    format: NativeFormat,
) -> Result<[i32; 3], i32> {
    let record_count = source.record_count();
    if record_count == 0 {
        return Ok([0, 0, 0]);
    }
    let mut targets = [
        AllPassEmitTarget {
            builder: solid_builder,
            analyzer: None,
            pending_counts: [0; MODEL_QUAD_FACING_COUNT],
            total_committed: 0,
        },
        AllPassEmitTarget {
            builder: cutout_builder,
            analyzer: None,
            pending_counts: [0; MODEL_QUAD_FACING_COUNT],
            total_committed: 0,
        },
        AllPassEmitTarget {
            builder: translucent_builder,
            analyzer: translucent_analyzer,
            pending_counts: [0; MODEL_QUAD_FACING_COUNT],
            total_committed: 0,
        },
    ];

    let profile_static_substages = static_model_substage_profile_enabled();
    let profile_scan_substages = scan_substage_profile_enabled();
    let profile_staging_substages = staging_substage_profile_enabled();
    let metadata_started = Instant::now();
    let cache_lookup_started = profile_start(profile_scan_substages);
    let states_guard = native_meshing_states()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let selectors_guard = native_model_selectors()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let models_guard = static_model_cache()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    {
        let builder = targets[0].builder();
        builder
            .profile
            .add_count(PROFILE_COUNT_SCANNED_BLOCKS, record_count);
        builder
            .profile
            .add_stage(PROFILE_MATERIAL_PASS, metadata_started);
        builder
            .profile
            .add_optional_stage(PROFILE_SCAN_CACHE_LOOKUP, cache_lookup_started);
    }

    let mut model_ids = Vec::with_capacity(8);
    let mut last_state_id = i32::MIN;
    let mut last_state = None;
    let mut last_direct_selector_id = i32::MIN;
    let mut last_direct_selector_model_id = None;
    let mut last_model_id = i32::MIN;
    let mut last_model = None;

    let scan_started = Instant::now();
    for record_index in 0..record_count {
        let iteration_started = profile_start(profile_scan_substages);
        let decoding_started = profile_start(profile_scan_substages);
        let state_id = source.state_id_at(record_index)?;
        let record_flags = source.flags_at(record_index)?;
        let state_lookup_started = profile_start(profile_static_substages);
        let state = if state_id == last_state_id {
            last_state
        } else {
            last_state_id = state_id;
            last_state = state_by_id(&states_guard, state_id);
            last_state
        };
        {
            let builder = targets[0].builder();
            builder
                .profile
                .add_optional_stage(PROFILE_STATIC_STATE_SELECTOR_LOOKUP, state_lookup_started);
            builder
                .profile
                .add_optional_stage(PROFILE_SCAN_RECORD_DECODING, decoding_started);
        }
        let Some(state) = state else {
            targets[0]
                .builder()
                .profile
                .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
            continue;
        };
        let flags = state.flags;
        if (flags & STATE_FLAG_AIR) != 0 {
            targets[0]
                .builder()
                .profile
                .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
            continue;
        }

        let dispatch_started = profile_start(profile_scan_substages);
        let dispatch = scan_dispatch(flags, record_flags);
        let has_light_block = dispatch.has_light_block;
        let has_model = dispatch.has_model;
        let has_fluid = dispatch.has_fluid;
        targets[0]
            .builder()
            .profile
            .add_optional_stage(PROFILE_SCAN_DISPATCH, dispatch_started);

        if has_model
            && !has_fluid
            && !has_light_block
            && (flags & STATE_FLAG_MODEL_FACE_CULLABLE) != 0
            && source.model_fully_occluded_at(record_index, state, &states_guard)?
        {
            targets[0]
                .builder()
                .profile
                .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
            continue;
        }

        let record = if has_light_block || has_fluid {
            Some(source.record_at(record_index)?)
        } else {
            None
        };

        if has_light_block {
            let target = &mut targets[1];
            let model_started = Instant::now();
            let append_started = profile_start(profile_scan_substages);
            let record = record.expect("light-block dispatch requires a full record");
            target.push_quad(
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
                format,
                profile_staging_substages,
            )?;
            let builder = target.profile();
            builder.add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
            builder.add_stage(PROFILE_MODEL_LOOKUP_EMIT, model_started);
            builder.add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
        }

        if has_model {
            let model_started = Instant::now();
            let scan_model_started = profile_start(profile_scan_substages);
            targets[0]
                .builder()
                .profile
                .add_count(PROFILE_COUNT_NATIVE_MODEL_BLOCKS, 1);
            let selector_started = profile_start(profile_static_substages);
            let mut selector_lookup_recorded = false;
            let direct_model_storage;
            let model_id_slice: &[i32];
            if state.selector_id == last_direct_selector_id {
                targets[0]
                    .builder()
                    .profile
                    .add_count(PROFILE_COUNT_SELECTOR_CACHE_HITS, 1);
                if let Some(model_id) = last_direct_selector_model_id {
                    direct_model_storage = [model_id];
                    model_id_slice = &direct_model_storage;
                } else {
                    model_id_slice = &[];
                }
            } else {
                let cache_lookup_started = profile_start(profile_scan_substages);
                let direct_model_id =
                    selector_by_id(&selectors_guard, state.selector_id).and_then(|selector| {
                        if selector.kind == SELECTOR_DIRECT {
                            selector.entries.first().map(|entry| entry.target_id)
                        } else {
                            None
                        }
                    });
                targets[0]
                    .builder()
                    .profile
                    .add_optional_stage(PROFILE_SCAN_CACHE_LOOKUP, cache_lookup_started);
                if let Some(model_id) = direct_model_id {
                    last_direct_selector_id = state.selector_id;
                    last_direct_selector_model_id = Some(model_id);
                    targets[0]
                        .builder()
                        .profile
                        .add_count(PROFILE_COUNT_SELECTOR_CACHE_HITS, 1);
                    direct_model_storage = [model_id];
                    model_id_slice = &direct_model_storage;
                } else {
                    last_direct_selector_id = i32::MIN;
                    last_direct_selector_model_id = None;
                    targets[0]
                        .builder()
                        .profile
                        .add_count(PROFILE_COUNT_SELECTOR_CACHE_MISSES, 1);
                    targets[0]
                        .builder()
                        .profile
                        .add_optional_stage(PROFILE_STATIC_STATE_SELECTOR_LOOKUP, selector_started);
                    selector_lookup_recorded = true;
                    model_ids.clear();
                    targets[0]
                        .builder()
                        .profile
                        .add_count(PROFILE_COUNT_TEMP_VECTOR_CLEARS, 1);
                    let resolution_started = profile_start(profile_static_substages);
                    let seed_record = match record {
                        Some(record) => record,
                        None => source.model_record_at(record_index)?,
                    };
                    resolve_selector_model_ids(
                        state.selector_id,
                        record_seed(seed_record),
                        &selectors_guard,
                        &mut model_ids,
                        &mut targets[0].builder().profile,
                    )?;
                    targets[0].builder().profile.add_optional_stage(
                        PROFILE_STATIC_WEIGHTED_MULTIPART_RESOLUTION,
                        resolution_started,
                    );
                    model_id_slice = &model_ids;
                }
            }
            if !selector_lookup_recorded {
                targets[0]
                    .builder()
                    .profile
                    .add_optional_stage(PROFILE_STATIC_STATE_SELECTOR_LOOKUP, selector_started);
            }

            let model_record = match record {
                Some(record) => record,
                None => source.model_record_at(record_index)?,
            };
            let mut emitted_direct_for_block = false;
            for model_id in model_id_slice {
                let model_lookup_started = profile_start(profile_static_substages);
                let scan_cache_lookup_started = profile_start(profile_scan_substages);
                let model = if *model_id == last_model_id {
                    targets[0]
                        .builder()
                        .profile
                        .add_count(PROFILE_COUNT_MODEL_CACHE_HITS, 1);
                    last_model
                } else {
                    last_model_id = *model_id;
                    last_model = model_by_id(&models_guard, *model_id);
                    targets[0]
                        .builder()
                        .profile
                        .add_count(PROFILE_COUNT_MODEL_CACHE_MISSES, 1);
                    last_model
                };
                {
                    let builder = targets[0].builder();
                    builder.profile.add_optional_stage(
                        PROFILE_STATIC_CACHED_MODEL_LOOKUP,
                        model_lookup_started,
                    );
                    builder
                        .profile
                        .add_optional_stage(PROFILE_SCAN_CACHE_LOOKUP, scan_cache_lookup_started);
                }
                let Some(model) = model else {
                    continue;
                };

                for quad_record in model {
                    let quad_iteration_started = profile_start(profile_static_substages);
                    let quad_record = *quad_record;
                    targets[0]
                        .builder()
                        .profile
                        .add_optional_stage(PROFILE_STATIC_QUAD_ITERATION, quad_iteration_started);

                    let culling_started = profile_start(profile_static_substages);
                    let scan_culling_started = profile_start(profile_scan_substages);
                    if native_section_culls_quad(&model_record, state, quad_record, &states_guard) {
                        let builder = targets[0].builder();
                        builder
                            .profile
                            .add_optional_stage(PROFILE_STATIC_CULLING, culling_started);
                        builder
                            .profile
                            .add_optional_stage(PROFILE_SCAN_CULLING, scan_culling_started);
                        continue;
                    }
                    {
                        let builder = targets[0].builder();
                        builder
                            .profile
                            .add_optional_stage(PROFILE_STATIC_CULLING, culling_started);
                        builder
                            .profile
                            .add_optional_stage(PROFILE_SCAN_CULLING, scan_culling_started);
                    }

                    let quad_iteration_started = profile_start(profile_static_substages);
                    let facing = match usize::try_from(quad_record.normal_face) {
                        Ok(value) if value < MODEL_QUAD_FACING_COUNT => value,
                        _ => MODEL_QUAD_FACING_UNASSIGNED,
                    };
                    targets[0]
                        .builder()
                        .profile
                        .add_optional_stage(PROFILE_STATIC_QUAD_ITERATION, quad_iteration_started);

                    let routed_pass = if let Some(pass) = native_pass_index(quad_record.pass_id) {
                        pass
                    } else if let Some(pass) = native_pass_index(state.pass_id) {
                        pass
                    } else {
                        continue;
                    };
                    let target = &mut targets[routed_pass];

                    if source.supports_direct_static_model_emission()
                        && can_direct_emit_static_model_quad(target, routed_pass, format)
                    {
                        let append_started = profile_start(profile_scan_substages);
                        append_direct_compact_static_model_quad(
                            target.builder(),
                            model_record,
                            state,
                            quad_record,
                            format,
                            facing,
                            profile_static_substages,
                            profile_scan_substages,
                        )?;
                        let builder = target.profile();
                        builder.add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
                        builder.add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
                        target.total_committed =
                            target.total_committed.checked_add(1).ok_or(ERR_CAPACITY)?;
                        emitted_direct_for_block = true;
                    } else {
                        let direct_fallback_counter = if routed_pass == 2 {
                            PROFILE_COUNT_DIRECT_MODEL_FALLBACK_TRANSLUCENT
                        } else if !is_compact_fast_format(format) || target.analyzer.is_some() {
                            PROFILE_COUNT_DIRECT_MODEL_FALLBACK_FORMAT_OR_ANALYZER
                        } else {
                            PROFILE_COUNT_DIRECT_MODEL_FALLBACK_GENERIC_FEATURE
                        };
                        {
                            let builder = target.profile();
                            builder.add_count(direct_fallback_counter, 1);
                        }
                        let quad = static_model_quad_to_native_section(
                            model_record,
                            state,
                            quad_record,
                            target.profile(),
                            profile_static_substages,
                            profile_scan_substages,
                        );
                        let staging_started = profile_start(profile_static_substages);
                        let append_started = profile_start(profile_scan_substages);
                        target.push_quad(
                            quad,
                            quad_record.packed_normal,
                            facing,
                            format,
                            profile_staging_substages,
                        )?;
                        let builder = target.profile();
                        builder.add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
                        builder.add_optional_stage(PROFILE_STATIC_STAGING, staging_started);
                        builder.add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
                    }
                }
            }
            if emitted_direct_for_block {
                targets[0]
                    .builder()
                    .profile
                    .add_count(PROFILE_COUNT_DIRECT_COMPACT_MODEL_BLOCKS, 1);
            }
            targets[0]
                .builder()
                .profile
                .add_stage(PROFILE_MODEL_LOOKUP_EMIT, model_started);
            targets[0]
                .builder()
                .profile
                .add_optional_stage(PROFILE_SCAN_MODEL_EMISSION, scan_model_started);
        }

        if has_fluid {
            let record = record.expect("fluid dispatch requires a full record");
            let Some(routed_pass) = native_pass_index(state.fluid_pass_id) else {
                targets[0]
                    .builder()
                    .profile
                    .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
                continue;
            };
            let target = &mut targets[routed_pass];
            let scan_fluid_started = profile_start(profile_scan_substages);
            target.profile().add_count(PROFILE_COUNT_FLUID_BLOCKS, 1);
            let fluid_face_count = target.emit_fluid_faces(
                &record,
                state,
                &states_guard,
                format,
                profile_scan_substages,
                profile_staging_substages,
            )?;
            let builder = target.profile();
            builder.add_count(PROFILE_COUNT_FLUID_FACES, fluid_face_count);
            builder.add_optional_stage(PROFILE_SCAN_FLUID_EMISSION, scan_fluid_started);
        }
        targets[0]
            .builder()
            .profile
            .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
    }

    for target in &mut targets {
        flush_all_pass_target(target, format)?;
    }
    targets[0]
        .builder()
        .profile
        .add_stage(PROFILE_SECTION_SCAN, scan_started);

    Ok([
        targets[0].total_committed,
        targets[1].total_committed,
        targets[2].total_committed,
    ])
}

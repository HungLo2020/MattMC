use super::*;

struct AllPassEmitTarget {
    builder: *mut NativeSectionMeshBuilder,
    analyzer: Option<u64>,
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
fn native_pass_index(pass_id: i32) -> Option<usize> {
    match pass_id {
        0..=2 => Some(pass_id as usize),
        _ => None,
    }
}

trait NativeSectionRecordSource {
    fn record_count(&self) -> usize;

    unsafe fn state_id_at(&self, index: usize) -> Result<i32, i32> {
        Ok(self.record_at(index)?.state_id)
    }

    unsafe fn flags_at(&self, index: usize) -> Result<i32, i32> {
        Ok(self.record_at(index)?.flags)
    }

    unsafe fn model_fully_occluded_at(
        &self,
        _index: usize,
        _state: NativeMeshingState,
        _states: &[Option<NativeMeshingState>],
    ) -> Result<bool, i32> {
        Ok(false)
    }

    unsafe fn record_at(&self, index: usize) -> Result<NativeSectionBlockRecord, i32>;
}

struct CompactSectionSnapshot<'a> {
    header: &'a CompactSectionSnapshotHeader,
    active_indices: &'a [u16],
    padded_state_ids: &'a [i32],
    padded_light_words: &'a [i32],
    block_ids: &'a [i32],
    seed_los: &'a [i32],
    seed_his: &'a [i32],
    tints: &'a [i32],
    fluid_tints: &'a [i32],
    fluid_flow_x: &'a [f32],
    fluid_flow_z: &'a [f32],
    fluid_block_ids: &'a [i32],
    flags: &'a [i32],
}

impl<'a> CompactSectionSnapshot<'a> {
    unsafe fn from_address(address: u64) -> Result<Self, i32> {
        if address == 0 {
            return Err(ERR_NULL_POINTER);
        }
        let header = &*(address as *const CompactSectionSnapshotHeader);
        if header.version != COMPACT_SECTION_SNAPSHOT_VERSION
            || header.active_count < 0
            || header.active_count as usize > COMPACT_SECTION_BLOCK_COUNT
        {
            return Err(ERR_INVALID_ARGUMENT);
        }
        let active_count = header.active_count as usize;
        if header.active_indices_address == 0
            || header.padded_state_ids_address == 0
            || header.padded_light_words_address == 0
            || header.block_ids_address == 0
            || header.seed_los_address == 0
            || header.seed_his_address == 0
            || header.tints_address == 0
            || header.fluid_tints_address == 0
            || header.fluid_flow_x_address == 0
            || header.fluid_flow_z_address == 0
            || header.fluid_block_ids_address == 0
            || header.flags_address == 0
        {
            return Err(ERR_NULL_POINTER);
        }
        let padded_len = COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH;
        Ok(Self {
            header,
            active_indices: slice::from_raw_parts(
                header.active_indices_address as *const u16,
                active_count,
            ),
            padded_state_ids: slice::from_raw_parts(
                header.padded_state_ids_address as *const i32,
                padded_len,
            ),
            padded_light_words: slice::from_raw_parts(
                header.padded_light_words_address as *const i32,
                padded_len,
            ),
            block_ids: slice::from_raw_parts(
                header.block_ids_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            seed_los: slice::from_raw_parts(
                header.seed_los_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            seed_his: slice::from_raw_parts(
                header.seed_his_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            tints: slice::from_raw_parts(
                header.tints_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_tints: slice::from_raw_parts(
                header.fluid_tints_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_flow_x: slice::from_raw_parts(
                header.fluid_flow_x_address as *const f32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_flow_z: slice::from_raw_parts(
                header.fluid_flow_z_address as *const f32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_block_ids: slice::from_raw_parts(
                header.fluid_block_ids_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            flags: slice::from_raw_parts(
                header.flags_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
        })
    }

    #[inline(always)]
    fn padded_index(x: usize, y: usize, z: usize) -> usize {
        (y * COMPACT_SECTION_PADDED_LENGTH + z) * COMPACT_SECTION_PADDED_LENGTH + x
    }

    #[inline(always)]
    unsafe fn padded_state(&self, x: usize, y: usize, z: usize) -> i32 {
        *self
            .padded_state_ids
            .get_unchecked(Self::padded_index(x, y, z))
    }

    #[inline(always)]
    unsafe fn padded_light_word(&self, x: usize, y: usize, z: usize) -> i32 {
        *self
            .padded_light_words
            .get_unchecked(Self::padded_index(x, y, z))
    }

    #[inline(always)]
    unsafe fn active_local_index(&self, index: usize) -> Result<usize, i32> {
        let local_index = *self.active_indices.get_unchecked(index) as usize;
        if local_index >= COMPACT_SECTION_BLOCK_COUNT {
            return Err(ERR_INVALID_ARGUMENT);
        }
        Ok(local_index)
    }
}

impl NativeSectionRecordSource for CompactSectionSnapshot<'_> {
    #[inline(always)]
    fn record_count(&self) -> usize {
        self.active_indices.len()
    }

    #[inline(always)]
    unsafe fn state_id_at(&self, index: usize) -> Result<i32, i32> {
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        Ok(self.padded_state(local_x + 1, local_y + 1, local_z + 1))
    }

    #[inline(always)]
    unsafe fn flags_at(&self, index: usize) -> Result<i32, i32> {
        let local_index = self.active_local_index(index)?;
        Ok(*self.flags.get_unchecked(local_index))
    }

    #[inline(always)]
    unsafe fn model_fully_occluded_at(
        &self,
        index: usize,
        state: NativeMeshingState,
        states: &[Option<NativeMeshingState>],
    ) -> Result<bool, i32> {
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        let padded_x = local_x + 1;
        let padded_y = local_y + 1;
        let padded_z = local_z + 1;
        let neighbor_ids = [
            self.padded_state(padded_x, padded_y - 1, padded_z),
            self.padded_state(padded_x, padded_y + 1, padded_z),
            self.padded_state(padded_x, padded_y, padded_z - 1),
            self.padded_state(padded_x, padded_y, padded_z + 1),
            self.padded_state(padded_x - 1, padded_y, padded_z),
            self.padded_state(padded_x + 1, padded_y, padded_z),
        ];

        for neighbor_id in neighbor_ids {
            let Some(neighbor) = state_by_id(states, neighbor_id) else {
                return Ok(false);
            };
            if !state_culls_model_face(state, neighbor) {
                return Ok(false);
            }
        }

        Ok(true)
    }

    unsafe fn record_at(&self, index: usize) -> Result<NativeSectionBlockRecord, i32> {
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        let padded_x = local_x + 1;
        let padded_y = local_y + 1;
        let padded_z = local_z + 1;

        let mut record = NativeSectionBlockRecord {
            state_id: self.padded_state(padded_x, padded_y, padded_z),
            block_id: *self.block_ids.get_unchecked(local_index),
            local_x: local_x as i32,
            local_y: local_y as i32,
            local_z: local_z as i32,
            seed_lo: *self.seed_los.get_unchecked(local_index),
            seed_hi: *self.seed_his.get_unchecked(local_index),
            tint: *self.tints.get_unchecked(local_index),
            fluid_tint: *self.fluid_tints.get_unchecked(local_index),
            fluid_flow_x: *self.fluid_flow_x.get_unchecked(local_index),
            fluid_flow_z: *self.fluid_flow_z.get_unchecked(local_index),
            absolute_x: self.header.min_x + local_x as i32,
            absolute_y: self.header.min_y + local_y as i32,
            absolute_z: self.header.min_z + local_z as i32,
            fluid_block_id: *self.fluid_block_ids.get_unchecked(local_index),
            flags: *self.flags.get_unchecked(local_index),
            ..NativeSectionBlockRecord::default()
        };

        record.neighbor_state_ids[0] = self.padded_state(padded_x, padded_y - 1, padded_z);
        record.neighbor_state_ids[1] = self.padded_state(padded_x, padded_y + 1, padded_z);
        record.neighbor_state_ids[2] = self.padded_state(padded_x, padded_y, padded_z - 1);
        record.neighbor_state_ids[3] = self.padded_state(padded_x, padded_y, padded_z + 1);
        record.neighbor_state_ids[4] = self.padded_state(padded_x - 1, padded_y, padded_z);
        record.neighbor_state_ids[5] = self.padded_state(padded_x + 1, padded_y, padded_z);

        let mut neighborhood_index = 0;
        for dy in 0..3 {
            for dz in 0..3 {
                for dx in 0..3 {
                    let sx = padded_x + dx - 1;
                    let sy = padded_y + dy - 1;
                    let sz = padded_z + dz - 1;
                    record.neighborhood_state_ids[neighborhood_index] =
                        self.padded_state(sx, sy, sz);
                    record.light_words[neighborhood_index] = self.padded_light_word(sx, sy, sz);
                    neighborhood_index += 1;
                }
            }
        }

        Ok(record)
    }
}

unsafe fn flush_all_pass_target(
    target: &mut AllPassEmitTarget,
    format: NativeFormat,
    _profile_static_substages: bool,
) -> Result<(), i32> {
    for facing in 0..MODEL_QUAD_FACING_COUNT {
        if target.pending_counts[facing] != 0 {
            target.flush_pending_face(facing, format, false)?;
        }
    }
    Ok(())
}

pub(super) unsafe fn section_builders_append_compact_native_section_all_passes_encoded(
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

unsafe fn section_builders_append_native_section_source_all_passes_encoded<S: NativeSectionRecordSource>(
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
        let has_light_block = (flags & STATE_FLAG_LIGHT_BLOCK) != 0;
        let has_model = (flags & STATE_FLAG_MODEL) != 0;
        let has_fluid = (flags & STATE_FLAG_FLUID) != 0
            && (record_flags & NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID) == 0;
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

        let record = source.record_at(record_index)?;

        if has_light_block {
            let target = &mut targets[1];
            let model_started = Instant::now();
            let append_started = profile_start(profile_scan_substages);
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
            builder
                .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
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
                    resolve_selector_model_ids(
                        state.selector_id,
                        record_seed(record),
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
                    builder
                        .profile
                        .add_optional_stage(PROFILE_STATIC_CACHED_MODEL_LOOKUP, model_lookup_started);
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
                    if native_section_culls_quad(&record, state, quad_record, &states_guard) {
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

                    let quad = static_model_quad_to_native_section(
                        record,
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
                    builder
                        .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
                    builder
                        .add_optional_stage(PROFILE_STATIC_STAGING, staging_started);
                    builder.add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
                }
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
            let Some(routed_pass) = native_pass_index(state.fluid_pass_id) else {
                targets[0]
                    .builder()
                    .profile
                    .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
                continue;
            };
            let target = &mut targets[routed_pass];
            let scan_fluid_started = profile_start(profile_scan_substages);
            target
                .profile()
                .add_count(PROFILE_COUNT_FLUID_BLOCKS, 1);
            let fluid_face_count = target.emit_fluid_faces(
                &record,
                state,
                &states_guard,
                format,
                profile_scan_substages,
                profile_staging_substages,
            )?;
            let builder = target.profile();
            builder
                .add_count(PROFILE_COUNT_FLUID_FACES, fluid_face_count);
            builder
                .add_optional_stage(PROFILE_SCAN_FLUID_EMISSION, scan_fluid_started);
        }
        targets[0]
            .builder()
            .profile
            .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
    }

    for target in &mut targets {
        flush_all_pass_target(target, format, profile_static_substages)?;
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
    let profile_scan_substages = scan_substage_profile_enabled();
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
    builder
        .profile
        .add_stage(PROFILE_MATERIAL_PASS, metadata_started);
    builder
        .profile
        .add_optional_stage(PROFILE_SCAN_CACHE_LOOKUP, cache_lookup_started);
    let mut total_committed = 0i32;
    let mut pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];
    let mut template_pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];
    let mut model_ids = Vec::with_capacity(8);
    let mut last_state_id = i32::MIN;
    let mut last_state = None;
    let mut last_direct_selector_id = i32::MIN;
    let mut last_direct_selector_model_id = None;
    let mut last_model_id = i32::MIN;
    let mut last_model = None;
    let mut discovered_pass_mask = 0u32;
    let profile_static_substages = static_model_substage_profile_enabled();
    let profile_staging_substages = staging_substage_profile_enabled();

    let scan_started = Instant::now();
    for record in records {
        let iteration_started = profile_start(profile_scan_substages);
        let decoding_started = profile_start(profile_scan_substages);
        let state_lookup_started = profile_start(profile_static_substages);
        let state = if record.state_id == last_state_id {
            last_state
        } else {
            last_state_id = record.state_id;
            last_state = state_by_id(&states_guard, record.state_id);
            last_state
        };
        builder
            .profile
            .add_optional_stage(PROFILE_STATIC_STATE_SELECTOR_LOOKUP, state_lookup_started);
        builder
            .profile
            .add_optional_stage(PROFILE_SCAN_RECORD_DECODING, decoding_started);
        let Some(state) = state else {
            builder
                .profile
                .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
            continue;
        };
        let flags = state.flags;
        if (flags & STATE_FLAG_AIR) != 0 {
            builder
                .profile
                .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
            continue;
        }
        let dispatch_started = profile_start(profile_scan_substages);
        let has_light_block = (flags & STATE_FLAG_LIGHT_BLOCK) != 0;
        let has_model = (flags & STATE_FLAG_MODEL) != 0;
        let has_fluid = (flags & STATE_FLAG_FLUID) != 0
            && (record.flags & NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID) == 0;
        if has_light_block {
            discovered_pass_mask |= 1u32 << 1;
        }
        if has_model && state.pass_id >= 0 && state.pass_id < 32 {
            discovered_pass_mask |= 1u32 << state.pass_id;
        } else if has_model && state.pass_id < 0 {
            discovered_pass_mask |= 0b111;
        }
        if has_fluid && state.fluid_pass_id >= 0 && state.fluid_pass_id < 32 {
            discovered_pass_mask |= 1u32 << state.fluid_pass_id;
        }
        builder
            .profile
            .add_optional_stage(PROFILE_SCAN_DISPATCH, dispatch_started);

        if has_light_block && (emit_all_passes || pass_id == 1) {
            let model_started = Instant::now();
            let append_started = profile_start(profile_scan_substages);
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
                profile_staging_substages,
                &mut total_committed,
            )?;
            builder
                .profile
                .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
            builder
                .profile
                .add_stage(PROFILE_MODEL_LOOKUP_EMIT, model_started);
            builder
                .profile
                .add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
        }

        if has_model && (emit_all_passes || state.pass_id < 0 || state.pass_id == pass_id) {
            let model_started = Instant::now();
            let scan_model_started = profile_start(profile_scan_substages);
            builder
                .profile
                .add_count(PROFILE_COUNT_NATIVE_MODEL_BLOCKS, 1);
            let selector_started = profile_start(profile_static_substages);
            let mut selector_lookup_recorded = false;
            let direct_model_storage;
            let model_id_slice: &[i32];
            if state.selector_id == last_direct_selector_id {
                builder
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
                builder
                    .profile
                    .add_optional_stage(PROFILE_SCAN_CACHE_LOOKUP, cache_lookup_started);
                if let Some(model_id) = direct_model_id {
                    last_direct_selector_id = state.selector_id;
                    last_direct_selector_model_id = Some(model_id);
                    builder
                        .profile
                        .add_count(PROFILE_COUNT_SELECTOR_CACHE_HITS, 1);
                    direct_model_storage = [model_id];
                    model_id_slice = &direct_model_storage;
                } else {
                    last_direct_selector_id = i32::MIN;
                    last_direct_selector_model_id = None;
                    builder
                        .profile
                        .add_count(PROFILE_COUNT_SELECTOR_CACHE_MISSES, 1);
                    builder
                        .profile
                        .add_optional_stage(PROFILE_STATIC_STATE_SELECTOR_LOOKUP, selector_started);
                    selector_lookup_recorded = true;
                    model_ids.clear();
                    builder
                        .profile
                        .add_count(PROFILE_COUNT_TEMP_VECTOR_CLEARS, 1);
                    let resolution_started = profile_start(profile_static_substages);
                    resolve_selector_model_ids(
                        state.selector_id,
                        record_seed(*record),
                        &selectors_guard,
                        &mut model_ids,
                        &mut builder.profile,
                    )?;
                    builder.profile.add_optional_stage(
                        PROFILE_STATIC_WEIGHTED_MULTIPART_RESOLUTION,
                        resolution_started,
                    );
                    model_id_slice = &model_ids;
                }
            }
            if !selector_lookup_recorded {
                builder
                    .profile
                    .add_optional_stage(PROFILE_STATIC_STATE_SELECTOR_LOOKUP, selector_started);
            }

            for model_id in model_id_slice {
                let model_lookup_started = profile_start(profile_static_substages);
                let scan_cache_lookup_started = profile_start(profile_scan_substages);
                let model = if *model_id == last_model_id {
                    builder.profile.add_count(PROFILE_COUNT_MODEL_CACHE_HITS, 1);
                    last_model
                } else {
                    last_model_id = *model_id;
                    last_model = model_by_id(&models_guard, *model_id);
                    builder.profile.add_count(PROFILE_COUNT_MODEL_CACHE_MISSES, 1);
                    last_model
                };
                builder
                    .profile
                    .add_optional_stage(PROFILE_STATIC_CACHED_MODEL_LOOKUP, model_lookup_started);
                builder
                    .profile
                    .add_optional_stage(PROFILE_SCAN_CACHE_LOOKUP, scan_cache_lookup_started);
                let Some(model) = model else {
                    continue;
                };

                for quad_record in model {
                    let quad_iteration_started = profile_start(profile_static_substages);
                    let quad_record_ptr = quad_record as *const StaticModelQuadRecord;
                    let quad_record = *quad_record;
                    builder
                        .profile
                        .add_optional_stage(PROFILE_STATIC_QUAD_ITERATION, quad_iteration_started);
                    if !emit_all_passes && quad_record.pass_id >= 0 && quad_record.pass_id != pass_id {
                        continue;
                    }
                    let culling_started = profile_start(profile_static_substages);
                    let scan_culling_started = profile_start(profile_scan_substages);
                    if native_section_culls_quad(record, state, quad_record, &states_guard) {
                        builder
                            .profile
                            .add_optional_stage(PROFILE_STATIC_CULLING, culling_started);
                        builder
                            .profile
                            .add_optional_stage(PROFILE_SCAN_CULLING, scan_culling_started);
                        continue;
                    }
                    builder
                        .profile
                        .add_optional_stage(PROFILE_STATIC_CULLING, culling_started);
                    builder
                        .profile
                        .add_optional_stage(PROFILE_SCAN_CULLING, scan_culling_started);

                    let quad_iteration_started = profile_start(profile_static_substages);
                    let facing = match usize::try_from(quad_record.normal_face) {
                        Ok(value) if value < MODEL_QUAD_FACING_COUNT => value,
                        _ => MODEL_QUAD_FACING_UNASSIGNED,
                    };
                    builder
                        .profile
                        .add_optional_stage(PROFILE_STATIC_QUAD_ITERATION, quad_iteration_started);
                    if analyzer.is_none()
                        && !store_raw_quads
                        && is_compact_fast_format(format)
                        && direct_static_templates_enabled()
                    {
                        if pending_counts[facing] != 0 {
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
                        let append_started = profile_start(profile_scan_substages);
                        let committed = push_static_model_template_quad(
                            builder,
                            record as *const NativeSectionBlockRecord,
                            state,
                            quad_record_ptr,
                            facing,
                            &mut template_pending_counts,
                            format,
                            profile_static_substages,
                            profile_scan_substages,
                        )?;
                        total_committed =
                            total_committed.checked_add(committed).ok_or(ERR_CAPACITY)?;
                        builder
                            .profile
                            .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
                        builder
                            .profile
                            .add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
                        continue;
                    }
                    if template_pending_counts[facing] != 0 {
                        let committed = flush_static_model_template_face(
                            builder,
                            facing,
                            &mut template_pending_counts,
                            format,
                            profile_static_substages,
                            profile_scan_substages,
                        )?;
                        total_committed =
                            total_committed.checked_add(committed).ok_or(ERR_CAPACITY)?;
                    }
                    let quad = static_model_quad_to_native_section(
                        *record,
                        state,
                        quad_record,
                        &mut builder.profile,
                        profile_static_substages,
                        profile_scan_substages,
                    );
                    let staging_started = profile_start(profile_static_substages);
                    let append_started = profile_start(profile_scan_substages);
                    push_native_section_quad(
                        builder,
                        quad,
                        quad_record.packed_normal,
                        facing,
                        &mut pending_counts,
                        analyzer,
                        format,
                        store_raw_quads,
                        profile_staging_substages,
                        &mut total_committed,
                    )?;
                    builder
                        .profile
                        .add_optional_stage(PROFILE_SCAN_QUAD_APPEND, append_started);
                    builder
                        .profile
                        .add_optional_stage(PROFILE_STATIC_STAGING, staging_started);
                    builder
                        .profile
                        .add_count(PROFILE_COUNT_NATIVE_MODEL_QUADS, 1);
                }
            }
            builder
                .profile
                .add_stage(PROFILE_MODEL_LOOKUP_EMIT, model_started);
            builder
                .profile
                .add_optional_stage(PROFILE_SCAN_MODEL_EMISSION, scan_model_started);
        }

        if has_fluid && (emit_all_passes || state.fluid_pass_id == pass_id) {
            let scan_fluid_started = profile_start(profile_scan_substages);
            if native_fluid_diag_enabled() {
                eprintln!(
                    "MATTMC_NATIVE_FLUID_DIAG scan-fluid pass={} pos={},{},{} local={},{},{} state={} fluid_pass={} analyzer={} store_raw={}",
                    pass_id,
                    record.absolute_x,
                    record.absolute_y,
                    record.absolute_z,
                    record.local_x,
                    record.local_y,
                    record.local_z,
                    record.state_id,
                    state.fluid_pass_id,
                    analyzer.is_some(),
                    store_raw_quads
                );
            }
            builder.profile.add_count(PROFILE_COUNT_FLUID_BLOCKS, 1);
            let fluid_face_count = emit_native_section_fluid_faces(
                record,
                state,
                &states_guard,
                builder,
                &mut pending_counts,
                analyzer,
                format,
                store_raw_quads,
                profile_scan_substages,
                profile_staging_substages,
                &mut total_committed,
            )?;
            builder
                .profile
                .add_count(PROFILE_COUNT_FLUID_FACES, fluid_face_count);
            builder
                .profile
                .add_optional_stage(PROFILE_SCAN_FLUID_EMISSION, scan_fluid_started);
        }
        builder
            .profile
            .add_optional_stage(PROFILE_SCAN_ACTIVE_RECORD_ITERATION, iteration_started);
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
        let committed = flush_static_model_template_face(
            builder,
            facing,
            &mut template_pending_counts,
            format,
            profile_static_substages,
            profile_scan_substages,
        )?;
        total_committed = total_committed.checked_add(committed).ok_or(ERR_CAPACITY)?;
    }

    Ok(total_committed)
}

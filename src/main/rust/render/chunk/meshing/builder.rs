use super::*;

pub(super) fn create_section_mesh_builder(capacity: usize) -> NativeSectionMeshBuilder {
    NativeSectionMeshBuilder {
        buffers: std::array::from_fn(|_| NativeQuadBuffer {
            quads: vec![NativeQuad::default(); capacity],
            encoded: Vec::new(),
            encoded_format: None,
        }),
        pending: std::array::from_fn(|_| NativePendingQuadBuffer {
            quads: vec![NativeQuad::default(); PENDING_BATCH_QUAD_CAPACITY],
            flat_quad_records: vec![FlatQuadRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            light_block_records: vec![LightBlockRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            fluid_face_records: vec![FluidFaceRecord::default(); PENDING_BATCH_QUAD_CAPACITY],
            static_model_block_records: vec![
                StaticModelBlockRecord::default();
                PENDING_BATCH_QUAD_CAPACITY
            ],
            packed_normals: vec![0; PENDING_BATCH_QUAD_CAPACITY],
            validity: vec![0; PENDING_BATCH_QUAD_CAPACITY],
        }),
        counts: [0; MODEL_QUAD_FACING_COUNT],
        profile: NativeMeshingProfile::default(),
        section_pass_cache_address: 0,
        section_pass_cache_count: 0,
        section_pass_cache_mask: 0,
        section_pass_cache_valid: false,
    }
}

pub(super) fn section_builder_prepare_quad(
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

pub(super) unsafe fn section_builder_append_batch(
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

pub(super) unsafe fn section_builder_append_batch_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    batch_address: u64,
    quad_count: usize,
    validity: Option<&[u8]>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    let stage_started = Instant::now();
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
    if validity.is_none() && !store_raw_quads {
        let start = builder.counts[facing];
        let required_len = start.checked_add(quad_count).ok_or(ERR_CAPACITY)?;
        let buffer = &mut builder.buffers[facing];
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

        for (index, quad) in input.iter().enumerate() {
            let encoded_start = (start + index) * encoded_quad_len;
            let encoded_end = encoded_start + encoded_quad_len;
            encode_quad(
                quad,
                &mut buffer.encoded[encoded_start..encoded_end],
                format,
            );
        }

        builder.counts[facing] = required_len;
        builder
            .profile
            .add_stage(PROFILE_VERTEX_PACKING, stage_started);
        builder
            .profile
            .add_count(PROFILE_COUNT_EMITTED_QUADS, quad_count);
        return Ok(quad_count as i32);
    }

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
    builder
        .profile
        .add_stage(PROFILE_VERTEX_PACKING, stage_started);
    builder
        .profile
        .add_count(PROFILE_COUNT_EMITTED_QUADS, valid_count);
    Ok(valid_count as i32)
}

pub(super) unsafe fn section_builder_append_flat_quad_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    analyzer: Option<(u64, i32)>,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<(i32, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok((0, 0));
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<FlatQuadRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const FlatQuadRecord, record_count);
    let mut processed = 0usize;
    let mut total_valid = 0i32;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                let record = records[processed + index];
                pending.quads[index] = record.quad;
                pending.packed_normals[index] = record.packed_normal;
            }
        }

        let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
        let mut chunk_valid = chunk_count as i32;
        let validity = if let Some((analyzer_handle, translucent_facing)) = analyzer {
            let status = translucent::append_native_quad_batch_to_analyzer(
                analyzer_handle,
                builder.pending[facing].quads.as_ptr() as u64,
                chunk_count as i32,
                translucent_facing,
                builder.pending[facing].packed_normals.as_ptr(),
                validity_address,
                &mut chunk_valid,
            );
            if status != OK {
                return Err(status);
            }
            Some(slice::from_raw_parts(
                validity_address as *const u8,
                chunk_count,
            ))
        } else {
            None
        };

        let chunk_committed = section_builder_append_batch_encoded(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            validity,
            format,
            store_raw_quads,
        )?;

        total_valid = total_valid.checked_add(chunk_valid).ok_or(ERR_CAPACITY)?;
        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok((total_valid, total_committed))
}

pub(super) unsafe fn section_builder_append_light_block_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }
    if record_count == 0 {
        return Ok(0);
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<LightBlockRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(record_address as *const LightBlockRecord, record_count);
    let mut processed = 0usize;
    let mut total_committed = 0i32;

    while processed < record_count {
        let chunk_count = (record_count - processed).min(PENDING_BATCH_QUAD_CAPACITY);
        {
            let pending = &mut builder.pending[facing];
            for index in 0..chunk_count {
                pending.quads[index] = light_block_record_to_quad(records[processed + index]);
            }
        }

        let chunk_committed = section_builder_append_batch_encoded(
            builder,
            facing,
            builder.pending[facing].quads.as_ptr() as u64,
            chunk_count,
            None,
            format,
            store_raw_quads,
        )?;

        total_committed = total_committed
            .checked_add(chunk_committed)
            .ok_or(ERR_CAPACITY)?;
        processed += chunk_count;
    }

    Ok(total_committed)
}

pub(super) unsafe fn section_builder_append_static_model_records_encoded(
    builder: &mut NativeSectionMeshBuilder,
    record_address: u64,
    record_count: usize,
    record_stride: usize,
    format: NativeFormat,
    store_raw_quads: bool,
) -> Result<i32, i32> {
    if record_count == 0 {
        return Ok(0);
    }
    if record_address == 0 {
        return Err(ERR_NULL_POINTER);
    }
    if record_stride != std::mem::size_of::<StaticModelBlockRecord>() {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let records = slice::from_raw_parts(
        record_address as *const StaticModelBlockRecord,
        record_count,
    );
    let cache_guard = static_model_cache()
        .lock()
        .map_err(|_| ERR_INVALID_ARGUMENT)?;
    let mut total_committed = 0i32;
    let mut pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];

    for record in records {
        if record.model_id == STATIC_MODEL_EMPTY_RECORD_ID {
            continue;
        }
        if record.model_id == STATIC_MODEL_LIGHT_BLOCK_RECORD_ID {
            let facing = MODEL_QUAD_FACING_UNASSIGNED;
            let slot = pending_counts[facing];
            {
                let pending = &mut builder.pending[facing];
                pending.quads[slot] = light_block_record_to_quad(LightBlockRecord {
                    material_bits: record.material_bits,
                    block_emission: record.block_emission,
                    block_id: record.block_id,
                    local_x: record.local_x,
                    local_y: record.local_y,
                    local_z: record.local_z,
                });
            }
            pending_counts[facing] += 1;

            if pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
                flush_static_model_pending_face(
                    builder,
                    facing,
                    &mut pending_counts,
                    None,
                    format,
                    store_raw_quads,
                    &mut total_committed,
                )?;
            }
            continue;
        }

        let Some(model) = model_by_id(&cache_guard, record.model_id) else {
            continue;
        };

        for quad_record in model {
            if quad_record.cull_face >= 0 && ((record.cull_mask >> quad_record.cull_face) & 1) != 0
            {
                continue;
            }

            let facing = match usize::try_from(quad_record.normal_face) {
                Ok(value) if value < MODEL_QUAD_FACING_COUNT => value,
                _ => MODEL_QUAD_FACING_UNASSIGNED,
            };
            let quad = static_model_quad_to_native(*record, *quad_record);
            let slot = pending_counts[facing];
            {
                let pending = &mut builder.pending[facing];
                pending.quads[slot] = quad;
            }
            pending_counts[facing] += 1;

            if pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
                flush_static_model_pending_face(
                    builder,
                    facing,
                    &mut pending_counts,
                    None,
                    format,
                    store_raw_quads,
                    &mut total_committed,
                )?;
            }
        }
    }

    for facing in 0..MODEL_QUAD_FACING_COUNT {
        flush_static_model_pending_face(
            builder,
            facing,
            &mut pending_counts,
            None,
            format,
            store_raw_quads,
            &mut total_committed,
        )?;
    }

    Ok(total_committed)
}

pub(super) unsafe fn flush_static_model_pending_face(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    let count = pending_counts[facing];
    if count == 0 {
        return Ok(());
    }

    let pending_address = builder.pending[facing].quads.as_ptr() as u64;
    let validity_address = builder.pending[facing].validity.as_mut_ptr() as u64;
    let mut valid_count = count as i32;
    let validity = if let Some(analyzer_handle) = analyzer {
        let analyzer_started = Instant::now();
        let status = translucent::append_native_quad_batch_to_analyzer(
            analyzer_handle,
            pending_address,
            count as i32,
            facing as i32,
            builder.pending[facing].packed_normals.as_ptr(),
            validity_address,
            &mut valid_count,
        );
        if status != OK {
            return Err(status);
        }
        builder
            .profile
            .add_stage(PROFILE_TRANSLUCENT_INGEST, analyzer_started);
        builder
            .profile
            .add_count(PROFILE_COUNT_TRANSLUCENT_QUADS, valid_count.max(0) as usize);
        Some(slice::from_raw_parts(validity_address as *const u8, count))
    } else {
        None
    };
    let staging_started = Instant::now();
    let committed = section_builder_append_batch_encoded(
        builder,
        facing,
        pending_address,
        count,
        validity,
        format,
        store_raw_quads,
    )?;
    builder
        .profile
        .add_stage(PROFILE_QUAD_STAGING, staging_started);
    *total_committed = total_committed.checked_add(committed).ok_or(ERR_CAPACITY)?;
    pending_counts[facing] = 0;
    Ok(())
}

pub(super) fn section_builder_staging_addresses(
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

pub(super) fn section_builder_record_staging_addresses(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
) -> Result<(u64, u64, u64, u64, i32), i32> {
    if facing >= MODEL_QUAD_FACING_COUNT {
        return Err(ERR_INVALID_ARGUMENT);
    }

    let pending = &mut builder.pending[facing];
    Ok((
        pending.flat_quad_records.as_mut_ptr() as u64,
        pending.light_block_records.as_mut_ptr() as u64,
        pending.fluid_face_records.as_mut_ptr() as u64,
        pending.static_model_block_records.as_mut_ptr() as u64,
        pending.flat_quad_records.len() as i32,
    ))
}

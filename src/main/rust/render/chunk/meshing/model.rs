use super::*;

pub(super) fn light_block_record_to_quad(record: LightBlockRecord) -> NativeQuad {
    let emission = record.block_emission.clamp(0, 255);
    let x = record.local_x as f32 + 0.25;
    let y = record.local_y as f32 + 0.25;
    let z = record.local_z as f32 + 0.25;
    let light = (emission << 4) | (emission << 20);
    let vertex = QuadVertex {
        x,
        y,
        z,
        color: 0,
        ao: 1.0,
        u: 0.0,
        v: 0.0,
        light,
    };

    NativeQuad {
        vertices: [vertex; 4],
        block_emission: emission as u8,
        render_type: 0,
        ignore_mid_block: 1,
        _padding: 0,
        block_id: record.block_id,
        local_x: record.local_x,
        local_y: record.local_y,
        local_z: record.local_z,
        material_bits: record.material_bits,
    }
}

pub(super) fn static_model_quad_to_native(
    block: StaticModelBlockRecord,
    quad_record: StaticModelQuadRecord,
) -> NativeQuad {
    let material_bits = if block.material_bits != 0 {
        block.material_bits
    } else {
        quad_record.material_bits
    };
    let block_emission = if block.block_emission != 0 {
        block.block_emission
    } else {
        quad_record.block_emission
    };
    let render_type = if block.render_type != 0 {
        block.render_type
    } else {
        quad_record.render_type
    };
    let mut vertices = [QuadVertex::default(); 4];

    for (index, vertex) in vertices.iter_mut().enumerate() {
        let source = quad_record.vertices[index];
        *vertex = QuadVertex {
            x: block.local_x as f32 + block.offset_x + source.x,
            y: block.local_y as f32 + block.offset_y + source.y,
            z: block.local_z as f32 + block.offset_z + source.z,
            color: argb_to_abgr(source.color),
            ao: if quad_record.shade != 0 { 1.0 } else { 1.0 },
            u: source.u,
            v: source.v,
            light: source.light,
        };
    }

    NativeQuad {
        vertices,
        block_emission: block_emission.clamp(0, 255) as u8,
        render_type: render_type.clamp(0, 255) as u8,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: block.block_id,
        local_x: block.local_x,
        local_y: block.local_y,
        local_z: block.local_z,
        material_bits,
    }
}

pub(super) unsafe fn push_native_section_quad(
    builder: &mut NativeSectionMeshBuilder,
    quad: NativeQuad,
    packed_normal: i32,
    facing: usize,
    pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    analyzer: Option<u64>,
    format: NativeFormat,
    store_raw_quads: bool,
    profile_staging_substages: bool,
    total_committed: &mut i32,
) -> Result<(), i32> {
    let append_started = profile_start(profile_staging_substages);
    let pending_started = profile_start(profile_staging_substages);
    let slot = pending_counts[facing];
    builder.pending[facing].quads[slot] = quad;
    builder.pending[facing].packed_normals[slot] = packed_normal;
    pending_counts[facing] += 1;
    builder
        .profile
        .add_count(PROFILE_COUNT_GENERIC_NATIVE_QUADS, 1);
    builder.profile.add_count(
        PROFILE_COUNT_GENERIC_NATIVE_BYTES_RETAINED,
        std::mem::size_of::<NativeQuad>(),
    );
    builder
        .profile
        .add_optional_stage(PROFILE_STAGING_PENDING_WRITE, pending_started);

    if pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
        flush_static_model_pending_face(
            builder,
            facing,
            pending_counts,
            analyzer,
            format,
            store_raw_quads,
            total_committed,
        )?;
    }

    builder
        .profile
        .add_optional_stage(PROFILE_STAGING_QUAD_APPEND, append_started);
    Ok(())
}

#[allow(clippy::too_many_arguments)]
pub(super) unsafe fn push_static_model_template_quad(
    builder: &mut NativeSectionMeshBuilder,
    block: *const NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad_record: *const StaticModelQuadRecord,
    facing: usize,
    template_pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    format: NativeFormat,
    profile_static_substages: bool,
    profile_scan_substages: bool,
) -> Result<i32, i32> {
    let append_started = profile_start(profile_static_substages);
    let slot = template_pending_counts[facing];
    let pending = &mut builder.pending[facing];
    pending.static_template_blocks[slot] = block;
    pending.static_template_states[slot] = state;
    pending.static_template_quads[slot] = quad_record;
    template_pending_counts[facing] += 1;
    builder
        .profile
        .add_optional_stage(PROFILE_STAGING_PENDING_WRITE, append_started);

    let mut committed = 0;
    if template_pending_counts[facing] == PENDING_BATCH_QUAD_CAPACITY {
        committed = flush_static_model_template_face(
            builder,
            facing,
            template_pending_counts,
            format,
            profile_static_substages,
            profile_scan_substages,
        )?;
    }

    builder
        .profile
        .add_optional_stage(PROFILE_STAGING_QUAD_APPEND, append_started);
    Ok(committed)
}

#[allow(clippy::too_many_arguments)]
pub(super) unsafe fn flush_static_model_template_face(
    builder: &mut NativeSectionMeshBuilder,
    facing: usize,
    template_pending_counts: &mut [usize; MODEL_QUAD_FACING_COUNT],
    format: NativeFormat,
    profile_static_substages: bool,
    profile_scan_substages: bool,
) -> Result<i32, i32> {
    let flush_started = profile_start(staging_substage_profile_enabled());
    let count = template_pending_counts[facing];
    if count == 0 {
        return Ok(0);
    }

    let encoded_quad_len = 4usize
        .checked_mul(format.vertex_stride)
        .ok_or(ERR_INVALID_ARGUMENT)?;
    let start = builder.counts[facing];
    let required_len = start.checked_add(count).ok_or(ERR_CAPACITY)?;
    let buffer = &mut builder.buffers[facing];
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
    ensure_encoded_len(&mut buffer.encoded, required_encoded_len, format);

    let encode_started = Instant::now();
    for index in 0..count {
        let block = &*builder.pending[facing].static_template_blocks[index];
        let state = builder.pending[facing].static_template_states[index];
        let quad_record = *builder.pending[facing].static_template_quads[index];

        let patch_started = profile_start(profile_static_substages);
        let offset_started = profile_start(profile_static_substages);
        let offset = if block.legacy_offset_x != 0.0
            || block.legacy_offset_y != 0.0
            || block.legacy_offset_z != 0.0
            || state.offset_type != OFFSET_NONE
        {
            native_model_offset(*block, state)
        } else {
            (0.0, 0.0, 0.0)
        };
        builder
            .profile
            .add_optional_stage(PROFILE_STATIC_POSITION_OFFSET_TRANSFORM, offset_started);

        let tint_started = profile_start(profile_static_substages);
        let scan_tint_started = profile_start(profile_scan_substages);
        let applies_tint = static_quad_applies_tint(quad_record, state);
        let tint = if applies_tint {
            native_tint_color(block, state, false)
        } else {
            -1
        };
        builder
            .profile
            .add_optional_stage(PROFILE_STATIC_TINT, tint_started);
        builder
            .profile
            .add_optional_stage(PROFILE_SCAN_TINTING, scan_tint_started);

        let lighting_started = profile_start(profile_static_substages);
        let scan_lighting_started = profile_start(profile_scan_substages);
        let quad_light = native_quad_lighting(block, &quad_record, state);
        builder
            .profile
            .add_optional_stage(PROFILE_STATIC_LIGHTING_AO, lighting_started);
        builder
            .profile
            .add_optional_stage(PROFILE_SCAN_LIGHTING_AO, scan_lighting_started);

        let material_started = profile_start(profile_static_substages);
        let material_bits = quad_record.material_bits;
        let material_section =
            ((material_bits & 0xff) << 16) | ((format.section_index & 0xff) << 24);
        builder
            .profile
            .add_optional_stage(PROFILE_STATIC_SPRITE_MATERIAL_PASS, material_started);
        builder
            .profile
            .add_optional_stage(PROFILE_TEMPLATE_INSTANCE_PATCH, patch_started);

        let write_index = start + index;
        let encoded_start = write_index * encoded_quad_len;
        let encoded_end = encoded_start + encoded_quad_len;
        let output = &mut builder.buffers[facing].encoded[encoded_start..encoded_end];
        encode_static_template_quad_compact(
            block,
            state,
            quad_record,
            offset,
            quad_light,
            tint,
            material_section,
            output,
            format,
        );
    }

    builder.counts[facing] = required_len;
    template_pending_counts[facing] = 0;
    builder
        .profile
        .add_stage(PROFILE_TEMPLATE_DIRECT_VERTEX_ENCODING, encode_started);
    builder
        .profile
        .add_stage(PROFILE_VERTEX_PACKING, encode_started);
    builder
        .profile
        .add_count(PROFILE_COUNT_DIRECT_TEMPLATE_QUADS, count);
    builder.profile.add_count(
        PROFILE_COUNT_DIRECT_TEMPLATE_BYTES_WRITTEN,
        count * encoded_quad_len,
    );
    builder
        .profile
        .add_count(PROFILE_COUNT_EMITTED_QUADS, count);
    builder
        .profile
        .add_optional_stage(PROFILE_STAGING_FLUSH, flush_started);
    Ok(count as i32)
}

#[inline(always)]
#[allow(clippy::too_many_arguments)]
fn encode_static_template_quad_compact(
    block: &NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad_record: StaticModelQuadRecord,
    offset: (f32, f32, f32),
    quad_light: NativeQuadLight,
    tint: i32,
    material_section: i32,
    output: &mut [u8],
    format: NativeFormat,
) {
    let source = quad_record.vertices;
    let tex_centroid_u = (source[0].u + source[1].u + source[2].u + source[3].u) * 0.25;
    let tex_centroid_v = (source[0].v + source[1].v + source[2].v + source[3].v) * 0.25;
    let applies_tint = tint != -1 && static_quad_applies_tint(quad_record, state);
    for index in 0..4 {
        let source = source[index];
        let color = if applies_tint {
            multiply_argb(source.color, tint)
        } else {
            source.color
        };
        let light = max_brightness(source.light, quad_light.lm[index]);
        let vertex_start = index * format.vertex_stride;
        let vertex_end = vertex_start + format.vertex_stride;
        encode_compact_vertex_values(
            block.local_x as f32 + offset.0 + source.x,
            block.local_y as f32 + offset.1 + source.y,
            block.local_z as f32 + offset.2 + source.z,
            argb_to_abgr(color),
            quad_light.ao[index],
            source.u,
            source.v,
            light,
            &mut output[vertex_start..vertex_end],
            tex_centroid_u,
            tex_centroid_v,
            material_section,
        );
    }
}

pub(super) fn resolve_selector_model_ids(
    selector_id: i32,
    seed: u64,
    selectors: &[Option<NativeModelSelector>],
    output: &mut Vec<i32>,
) -> Result<(), i32> {
    let Some(selector) = selector_by_id(selectors, selector_id) else {
        return Ok(());
    };

    match selector.kind {
        SELECTOR_DIRECT => {
            if let Some(entry) = selector.entries.first() {
                output.push(entry.target_id);
            }
        }
        SELECTOR_WEIGHTED => {
            if selector.total_weight <= 0 {
                return Ok(());
            }
            let mut choice = legacy_next_int(seed, selector.total_weight);
            for entry in &selector.entries {
                choice -= entry.weight;
                if choice < 0 {
                    resolve_selector_model_ids(entry.target_id, seed, selectors, output)?;
                    break;
                }
            }
        }
        SELECTOR_GROUP => {
            let child_seed = legacy_next_long(seed);
            for entry in &selector.entries {
                resolve_selector_model_ids(entry.target_id, child_seed, selectors, output)?;
            }
        }
        _ => return Err(ERR_INVALID_ARGUMENT),
    }

    Ok(())
}

pub(super) fn legacy_next_int(seed: u64, bound: i32) -> i32 {
    if bound <= 0 {
        return 0;
    }

    let mut state = legacy_set_seed(seed);
    if (bound & (bound - 1)) == 0 {
        return (((bound as i64) * (legacy_next(&mut state, 31) as i64)) >> 31) as i32;
    }

    loop {
        let value = legacy_next(&mut state, 31) as i32;
        let result = value % bound;
        if value - result + (bound - 1) >= 0 {
            return result;
        }
    }
}

pub(super) fn legacy_next_long(seed: u64) -> u64 {
    let mut state = legacy_set_seed(seed);
    let high = legacy_next(&mut state, 32) as u64;
    let low = legacy_next(&mut state, 32) as u64;
    (high << 32).wrapping_add(low)
}

pub(super) fn legacy_set_seed(seed: u64) -> u64 {
    (seed ^ 25_214_903_917) & 281_474_976_710_655
}

pub(super) fn legacy_next(state: &mut u64, bits: u32) -> u32 {
    *state = state.wrapping_mul(25_214_903_917).wrapping_add(11) & 281_474_976_710_655;
    (*state >> (48 - bits)) as u32
}

pub(super) fn record_seed(record: NativeSectionBlockRecord) -> u64 {
    ((record.seed_hi as u32 as u64) << 32) | (record.seed_lo as u32 as u64)
}

pub(super) fn static_model_quad_to_native_section(
    block: NativeSectionBlockRecord,
    state: NativeMeshingState,
    quad_record: StaticModelQuadRecord,
    profile: &mut NativeMeshingProfile,
    profile_static_substages: bool,
    profile_scan_substages: bool,
) -> NativeQuad {
    let offset_started = profile_start(profile_static_substages);
    let offset = if block.legacy_offset_x != 0.0
        || block.legacy_offset_y != 0.0
        || block.legacy_offset_z != 0.0
        || state.offset_type != OFFSET_NONE
    {
        native_model_offset(block, state)
    } else {
        (0.0, 0.0, 0.0)
    };
    profile.add_optional_stage(PROFILE_STATIC_POSITION_OFFSET_TRANSFORM, offset_started);

    let lighting_started = profile_start(profile_static_substages);
    let scan_lighting_started = profile_start(profile_scan_substages);
    let light = native_quad_lighting(&block, &quad_record, state);
    profile.add_optional_stage(PROFILE_STATIC_LIGHTING_AO, lighting_started);
    profile.add_optional_stage(PROFILE_SCAN_LIGHTING_AO, scan_lighting_started);

    let tint_started = profile_start(profile_static_substages);
    let scan_tint_started = profile_start(profile_scan_substages);
    let applies_tint = static_quad_applies_tint(quad_record, state);
    let tint = if applies_tint {
        native_tint_color(&block, state, false)
    } else {
        -1
    };
    profile.add_optional_stage(PROFILE_STATIC_TINT, tint_started);
    profile.add_optional_stage(PROFILE_SCAN_TINTING, scan_tint_started);

    let material_started = profile_start(profile_static_substages);
    let block_id = choose_block_id(block.block_id, state.block_id);
    let material_bits = quad_record.material_bits;
    profile.add_optional_stage(PROFILE_STATIC_SPRITE_MATERIAL_PASS, material_started);

    let creation_started = profile_start(profile_static_substages);
    let mut vertices = [QuadVertex::default(); 4];
    for (index, vertex) in vertices.iter_mut().enumerate() {
        let source = quad_record.vertices[index];
        let mut color = source.color;
        if applies_tint {
            color = multiply_argb(color, tint);
        }
        *vertex = QuadVertex {
            x: block.local_x as f32 + offset.0 + source.x,
            y: block.local_y as f32 + offset.1 + source.y,
            z: block.local_z as f32 + offset.2 + source.z,
            color: argb_to_abgr(color),
            ao: light.ao[index],
            u: source.u,
            v: source.v,
            light: max_brightness(source.light, light.lm[index]),
        };
    }

    let quad = NativeQuad {
        vertices,
        block_emission: state.block_emission.clamp(0, 255) as u8,
        render_type: 0,
        ignore_mid_block: 0,
        _padding: 0,
        block_id,
        local_x: block.absolute_x,
        local_y: block.absolute_y,
        local_z: block.absolute_z,
        material_bits,
    };
    profile.add_optional_stage(PROFILE_STATIC_NATIVE_QUAD_CREATION, creation_started);
    quad
}

#[inline(always)]
pub(super) fn static_quad_applies_tint(
    quad_record: StaticModelQuadRecord,
    _state: NativeMeshingState,
) -> bool {
    quad_record.tint_index != -1
}

#[inline(always)]
pub(super) fn max_brightness(a: i32, b: i32) -> i32 {
    let a = a as u32;
    let b = b as u32;
    ((a & 0x0000_ffff).max(b & 0x0000_ffff)
        | (a & 0xffff_0000).max(b & 0xffff_0000)) as i32
}

#[inline(always)]
pub(super) fn choose_block_id(record_block_id: i32, state_block_id: i32) -> i32 {
    if record_block_id >= 0 {
        record_block_id
    } else {
        state_block_id
    }
}

pub(super) fn native_model_offset(
    block: NativeSectionBlockRecord,
    state: NativeMeshingState,
) -> (f32, f32, f32) {
    if block.legacy_offset_x != 0.0 || block.legacy_offset_y != 0.0 || block.legacy_offset_z != 0.0
    {
        return (
            block.legacy_offset_x,
            block.legacy_offset_y,
            block.legacy_offset_z,
        );
    }
    if state.offset_type == OFFSET_NONE {
        return (0.0, 0.0, 0.0);
    }
    let seed = mth_seed(block.absolute_x, 0, block.absolute_z);
    let max_h = state.max_horizontal_offset;
    let x = ((((seed & 15) as f32) / 15.0 - 0.5) * 0.5).clamp(-max_h, max_h);
    let z = ((((seed >> 8 & 15) as f32) / 15.0 - 0.5) * 0.5).clamp(-max_h, max_h);
    if state.offset_type == OFFSET_XYZ {
        let y = (((seed >> 4 & 15) as f32) / 15.0 - 1.0) * state.max_vertical_offset;
        (x, y, z)
    } else if state.offset_type == OFFSET_XZ {
        (x, 0.0, z)
    } else {
        (0.0, 0.0, 0.0)
    }
}

pub(super) fn mth_seed(x: i32, y: i32, z: i32) -> i64 {
    let mut value =
        (x as i64).wrapping_mul(3_129_871) ^ (z as i64).wrapping_mul(116_129_781) ^ (y as i64);
    value = value
        .wrapping_mul(value)
        .wrapping_mul(42_317_861)
        .wrapping_add(value.wrapping_mul(11));
    value >> 16
}

use super::*;

const TEST_TINT_FORCE_GRASS: i32 = 10;

fn vertex(x: f32, y: f32, z: f32, u: f32, v: f32) -> QuadVertex {
    QuadVertex {
        x,
        y,
        z,
        color: 0xff804020u32 as i32,
        ao: 0.5,
        u,
        v,
        light: 0x00f000f0,
    }
}

fn quad() -> NativeQuad {
    NativeQuad {
        vertices: [
            vertex(0.0, 0.0, 0.0, 0.0, 0.0),
            vertex(1.0, 0.0, 0.0, 1.0, 0.0),
            vertex(1.0, 1.0, 0.0, 1.0, 1.0),
            vertex(0.0, 1.0, 0.0, 0.0, 1.0),
        ],
        block_emission: 7,
        render_type: 1,
        ignore_mid_block: 0,
        _padding: 0,
        block_id: 41,
        local_x: 1,
        local_y: 2,
        local_z: 3,
        material_bits: 5,
    }
}

#[test]
fn native_quad_layout_matches_java_stride() {
    assert_eq!(32, std::mem::size_of::<QuadVertex>());
    assert_eq!(152, std::mem::size_of::<NativeQuad>());
    assert_eq!(156, std::mem::size_of::<FlatQuadRecord>());
    assert_eq!(24, std::mem::size_of::<LightBlockRecord>());
    assert_eq!(172, std::mem::size_of::<FluidFaceRecord>());
}

#[test]
fn compact_section_snapshot_header_layout_matches_java() {
    assert_eq!(120, std::mem::size_of::<CompactSectionSnapshotHeader>());
    assert_eq!(
        0,
        std::mem::offset_of!(CompactSectionSnapshotHeader, version)
    );
    assert_eq!(
        4,
        std::mem::offset_of!(CompactSectionSnapshotHeader, active_count)
    );
    assert_eq!(8, std::mem::offset_of!(CompactSectionSnapshotHeader, min_x));
    assert_eq!(
        12,
        std::mem::offset_of!(CompactSectionSnapshotHeader, min_y)
    );
    assert_eq!(
        16,
        std::mem::offset_of!(CompactSectionSnapshotHeader, min_z)
    );
    assert_eq!(
        24,
        std::mem::offset_of!(CompactSectionSnapshotHeader, active_indices_address)
    );
    assert_eq!(
        32,
        std::mem::offset_of!(CompactSectionSnapshotHeader, padded_state_ids_address)
    );
    assert_eq!(
        40,
        std::mem::offset_of!(CompactSectionSnapshotHeader, padded_light_words_address)
    );
    assert_eq!(
        48,
        std::mem::offset_of!(CompactSectionSnapshotHeader, block_ids_address)
    );
    assert_eq!(
        56,
        std::mem::offset_of!(CompactSectionSnapshotHeader, seed_los_address)
    );
    assert_eq!(
        64,
        std::mem::offset_of!(CompactSectionSnapshotHeader, seed_his_address)
    );
    assert_eq!(
        72,
        std::mem::offset_of!(CompactSectionSnapshotHeader, tints_address)
    );
    assert_eq!(
        80,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_tints_address)
    );
    assert_eq!(
        88,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_flow_x_address)
    );
    assert_eq!(
        96,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_flow_z_address)
    );
    assert_eq!(
        104,
        std::mem::offset_of!(CompactSectionSnapshotHeader, fluid_block_ids_address)
    );
    assert_eq!(
        112,
        std::mem::offset_of!(CompactSectionSnapshotHeader, flags_address)
    );
}

struct CompactSnapshotStorage {
    active_indices: Vec<u16>,
    padded_state_ids: Vec<i32>,
    padded_light_words: Vec<i32>,
    block_ids: Vec<i32>,
    seed_los: Vec<i32>,
    seed_his: Vec<i32>,
    tints: Vec<i32>,
    fluid_tints: Vec<i32>,
    fluid_flow_x: Vec<f32>,
    fluid_flow_z: Vec<f32>,
    fluid_block_ids: Vec<i32>,
    flags: Vec<i32>,
}

impl CompactSnapshotStorage {
    fn new() -> Self {
        let padded_len = COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH;
        Self {
            active_indices: vec![0],
            padded_state_ids: vec![0; padded_len],
            padded_light_words: vec![0; padded_len],
            block_ids: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            seed_los: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            seed_his: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            tints: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_tints: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_flow_x: vec![0.0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_flow_z: vec![0.0; COMPACT_SECTION_BLOCK_COUNT],
            fluid_block_ids: vec![0; COMPACT_SECTION_BLOCK_COUNT],
            flags: vec![0; COMPACT_SECTION_BLOCK_COUNT],
        }
    }

    fn header(&self, active_count: i32) -> CompactSectionSnapshotHeader {
        CompactSectionSnapshotHeader {
            version: COMPACT_SECTION_SNAPSHOT_VERSION,
            active_count,
            min_x: 100,
            min_y: 200,
            min_z: 300,
            _padding: 0,
            active_indices_address: self.active_indices.as_ptr() as u64,
            padded_state_ids_address: self.padded_state_ids.as_ptr() as u64,
            padded_light_words_address: self.padded_light_words.as_ptr() as u64,
            block_ids_address: self.block_ids.as_ptr() as u64,
            seed_los_address: self.seed_los.as_ptr() as u64,
            seed_his_address: self.seed_his.as_ptr() as u64,
            tints_address: self.tints.as_ptr() as u64,
            fluid_tints_address: self.fluid_tints.as_ptr() as u64,
            fluid_flow_x_address: self.fluid_flow_x.as_ptr() as u64,
            fluid_flow_z_address: self.fluid_flow_z.as_ptr() as u64,
            fluid_block_ids_address: self.fluid_block_ids.as_ptr() as u64,
            flags_address: self.flags.as_ptr() as u64,
        }
    }
}

#[test]
fn compact_section_snapshot_rejects_malformed_headers_and_pointer_sets() {
    let storage = CompactSnapshotStorage::new();
    assert_eq!(Err(ERR_NULL_POINTER), unsafe {
        CompactSectionSnapshot::from_address(0).map(|_| ())
    });

    let mut header = storage.header(1);
    header.version = COMPACT_SECTION_SNAPSHOT_VERSION + 1;
    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });

    header = storage.header(-1);
    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });

    header = storage.header((COMPACT_SECTION_BLOCK_COUNT + 1) as i32);
    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });

    header = storage.header(1);
    header.padded_light_words_address = 0;
    assert_eq!(Err(ERR_NULL_POINTER), unsafe {
        CompactSectionSnapshot::from_address(&header as *const _ as u64).map(|_| ())
    });
}

#[test]
fn compact_section_snapshot_reconstructs_padded_border_record() {
    let mut storage = CompactSnapshotStorage::new();
    storage.active_indices[0] = 0;
    storage.block_ids[0] = 77;
    storage.seed_los[0] = 11;
    storage.seed_his[0] = 12;
    storage.tints[0] = 13;
    storage.fluid_tints[0] = 14;
    storage.fluid_flow_x[0] = 0.25;
    storage.fluid_flow_z[0] = -0.5;
    storage.fluid_block_ids[0] = 78;
    storage.flags[0] = NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID;

    let set_cell = |states: &mut [i32], lights: &mut [i32], x, y, z, state, light| {
        let index = CompactSectionSnapshot::padded_index(x, y, z);
        states[index] = state;
        lights[index] = light;
    };
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        1,
        1,
        42,
        420,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        0,
        1,
        101,
        1001,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        2,
        1,
        102,
        1002,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        1,
        0,
        103,
        1003,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        1,
        1,
        2,
        104,
        1004,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        0,
        1,
        1,
        105,
        1005,
    );
    set_cell(
        &mut storage.padded_state_ids,
        &mut storage.padded_light_words,
        2,
        1,
        1,
        106,
        1006,
    );

    let header = storage.header(1);
    let snapshot =
        unsafe { CompactSectionSnapshot::from_address(&header as *const _ as u64).unwrap() };
    let record = unsafe { snapshot.record_at(0).unwrap() };

    assert_eq!(42, record.state_id);
    assert_eq!([101, 102, 103, 104, 105, 106], record.neighbor_state_ids);
    assert_eq!(100, record.absolute_x);
    assert_eq!(200, record.absolute_y);
    assert_eq!(300, record.absolute_z);
    assert_eq!(77, record.block_id);
    assert_eq!(11, record.seed_lo);
    assert_eq!(12, record.seed_hi);
    assert_eq!(13, record.tint);
    assert_eq!(14, record.fluid_tint);
    assert_eq!(78, record.fluid_block_id);
    assert_eq!(NATIVE_SECTION_BLOCK_FLAG_SUPPRESS_FLUID, record.flags);
    assert_eq!(
        105,
        record.neighborhood_state_ids[neighborhood_index(-1, 0, 0)]
    );
    assert_eq!(1006, record.light_words[neighborhood_index(1, 0, 0)]);
}

#[test]
fn compact_section_snapshot_rejects_stale_or_invalid_active_indexes() {
    let mut storage = CompactSnapshotStorage::new();
    storage.active_indices[0] = COMPACT_SECTION_BLOCK_COUNT as u16;
    let header = storage.header(1);
    let snapshot =
        unsafe { CompactSectionSnapshot::from_address(&header as *const _ as u64).unwrap() };

    assert_eq!(Err(ERR_INVALID_ARGUMENT), unsafe {
        snapshot.record_at(0).map(|_| ())
    });
}

#[test]
fn argb_to_abgr_preserves_alpha_and_swaps_red_blue() {
    assert_eq!(0xff0000ffu32 as i32, argb_to_abgr(0xffff0000u32 as i32));
    assert_eq!(0xff00ff00u32 as i32, argb_to_abgr(0xff00ff00u32 as i32));
    assert_eq!(0xffff0000u32 as i32, argb_to_abgr(0xff0000ffu32 as i32));
    assert_eq!(0xffffffffu32 as i32, argb_to_abgr(0xffffffffu32 as i32));
    assert_eq!(0x80332211u32 as i32, argb_to_abgr(0x80112233u32 as i32));
    assert_eq!(0xff6f9935u32 as i32, argb_to_abgr(0xff35996fu32 as i32));
    assert_eq!(0xffe4763fu32 as i32, argb_to_abgr(0xff3f76e4u32 as i32));
}

#[test]
fn compact_color_encoding_keeps_material_alpha_when_ao_is_not_separate() {
    assert_eq!(
        0x80112233u32 as i32,
        encode_color(0x80112233u32 as i32, 1.0, false)
    );
    assert_eq!(
        0x7f112233u32 as i32,
        encode_color(0x80112233u32 as i32, 0.5, true)
    );
}

#[test]
fn fluid_bottom_lighting_uses_direction_down_not_model_facing_ordinal() {
    let mut block = NativeSectionBlockRecord::default();
    block.light_words[neighborhood_index(0, -1, 0)] =
        pack_light_word(4, 15, 0, 4096, false, false, false, false);
    block.light_words[neighborhood_index(0, 0, 0)] =
        pack_light_word(10, 7, 0, 4096, false, false, false, false);
    block.light_words[neighborhood_index(-1, 0, 0)] =
        pack_light_word(1, 1, 0, 4096, false, false, false, false);

    let quad = lighting_quad(0, 0, 0.0, 0.0, 1.0);
    let down = native_quad_lighting(&block, &quad, lighting_state_record(0));
    let wrong_facing_ordinal = native_quad_lighting(
        &block,
        &lighting_quad(0, MODEL_QUAD_FACING_NEG_Y as i32, 0.0, 0.0, 1.0),
        lighting_state_record(0),
    );

    assert_eq!(pack_light(4, 15), down.lm[0]);
    assert_ne!(down.lm[0], wrong_facing_ordinal.lm[0]);
}

#[test]
fn compact_format_metadata_is_rust_owned() {
    assert_eq!(
        20,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_STRIDE)
    );
    assert_eq!(
        0,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_POSITION_OFFSET)
    );
    assert_eq!(
        8,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_COLOR_OFFSET)
    );
    assert_eq!(
        12,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_TEXTURE_OFFSET)
    );
    assert_eq!(
        16,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_LIGHT_MATERIAL_INDEX_OFFSET)
    );
    assert_eq!(
        0,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_BLOCK_ID_OFFSET)
    );
    assert_eq!(
        1 << 20,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_POSITION_MAX_VALUE)
    );
    assert_eq!(
        1 << 15,
        mattmc_sodium_chunk_compact_format_value(COMPACT_VALUE_TEXTURE_MAX_VALUE)
    );
}

#[test]
fn native_quad_write_helpers_populate_rust_owned_layout() {
    let mut quad = NativeQuad::default();
    let address = &mut quad as *mut NativeQuad as u64;

    unsafe {
        assert_eq!(
            OK,
            mattmc_sodium_chunk_native_quad_write_metadata(address, 13, 2, 1, 99, 4, 5, 6, 7)
        );
        assert_eq!(
            OK,
            mattmc_sodium_chunk_native_quad_write_vertex(
                address, 2, 1.25, 2.5, 3.75, 0x11223344, 0.875, 0.125, 0.625, 0x00f000f0,
            )
        );
    }

    assert_eq!(13, quad.block_emission);
    assert_eq!(2, quad.render_type);
    assert_eq!(1, quad.ignore_mid_block);
    assert_eq!(0, quad._padding);
    assert_eq!(99, quad.block_id);
    assert_eq!(4, quad.local_x);
    assert_eq!(5, quad.local_y);
    assert_eq!(6, quad.local_z);
    assert_eq!(7, quad.material_bits);
    assert_eq!(1.25, quad.vertices[2].x);
    assert_eq!(2.5, unsafe {
        mattmc_sodium_chunk_native_quad_position(address, 2, 1)
    });
    assert_eq!(3.75, unsafe {
        mattmc_sodium_chunk_native_quad_position(address, 2, 2)
    });
    assert_eq!(0x11223344, quad.vertices[2].color);
    assert_eq!(0.875, quad.vertices[2].ao);
    assert_eq!(0.125, quad.vertices[2].u);
    assert_eq!(0.625, quad.vertices[2].v);
    assert_eq!(0x00f000f0, quad.vertices[2].light);
}

#[test]
fn compact_encoder_writes_expected_base_words() {
    let input = [quad()];
    let mut output = vec![0u8; 4 * 20];
    let format = NativeFormat {
        vertex_stride: 20,
        block_id_offset: 0,
        normal_offset: 0,
        tangent_offset: 0,
        mid_uv_offset: 0,
        mid_block_offset: 0,
        section_index: 3,
        separate_ao: false,
    };

    unsafe {
        assert_eq!(
            OK,
            encode(
                input.as_ptr() as u64,
                4,
                output.as_mut_ptr() as u64,
                output.len() as i32,
                format,
            )
        );
    }

    assert_ne!([0u8; 20], output[0..20]);
    assert_eq!(
        pack_light_and_data(0xf0f0, 5, 3).to_ne_bytes(),
        output[16..20]
    );
}

#[test]
fn block_id_pack_matches_java_wrapping_int_arithmetic() {
    let mut input = quad();
    input.block_id = i32::MAX;
    input.render_type = 1;

    assert_eq!(1, pack_block_id(&input));

    input.block_id = -1;
    input.render_type = 0;
    assert_eq!(0, pack_block_id(&input));
}

#[test]
fn scattered_encoder_writes_requested_quad_slots_only() {
    let mut first = quad();
    first.material_bits = 5;
    let mut second = quad();
    second.material_bits = 9;
    second.vertices[0].x = 4.0;
    let input = [first, second];
    let offsets = [8, 0];
    let mut output = vec![0u8; 4 * 3 * 20];
    let format = NativeFormat {
        vertex_stride: 20,
        block_id_offset: 0,
        normal_offset: 0,
        tangent_offset: 0,
        mid_uv_offset: 0,
        mid_block_offset: 0,
        section_index: 7,
        separate_ao: false,
    };

    unsafe {
        assert_eq!(
            OK,
            encode_scattered(
                input.as_ptr() as u64,
                offsets.as_ptr(),
                input.len() as i32,
                output.as_mut_ptr() as u64,
                output.len() as i32,
                format,
            )
        );
    }

    assert_eq!(
        pack_light_and_data(0xf0f0, 9, 7).to_ne_bytes(),
        output[16..20]
    );
    assert_eq!([0u8; 20], output[4 * 20..5 * 20]);
    assert_eq!(
        pack_light_and_data(0xf0f0, 5, 7).to_ne_bytes(),
        output[(8 * 20 + 16)..(8 * 20 + 20)]
    );
}

#[test]
fn smooth_lighting_uses_java_aligned_partial_offset_face() {
    let block = lighting_block_record();
    let quad = lighting_quad(
        MODEL_QUAD_FLAG_ALIGNED | MODEL_QUAD_FLAG_PARTIAL,
        1,
        0.25,
        0.5,
        0.75,
    );
    let light = smooth_lighting(&block, &quad, lighting_state_record(0), 1, true);
    let weights = corner_weights(1, 0.25, 0.5, 0.75);
    let expected = blend_ao_face(ao_face_data(&block, 1, true), weights);
    let wrong_direct = blend_ao_face(ao_face_data(&block, 1, false), weights);

    assert_close(expected.0 * ambient_shade(1, true), light.ao[0]);
    assert_eq!(expected.1, light.lm[0]);
    assert_ne!(wrong_direct.1, light.lm[0]);
}

#[test]
fn smooth_lighting_uses_java_parallel_inset_depth_weights() {
    let block = lighting_block_record();
    let quad = lighting_quad(
        MODEL_QUAD_FLAG_PARALLEL | MODEL_QUAD_FLAG_PARTIAL,
        1,
        0.4,
        0.25,
        0.6,
    );
    let light = smooth_lighting(&block, &quad, lighting_state_record(0), 1, true);
    let weights = corner_weights(1, 0.4, 0.25, 0.6);
    let depth = face_depth(1, 0.4, 0.25, 0.6);
    let expected = blend_inset_ao_face(&block, 1, depth, 1.0 - depth, weights);

    assert_close(expected.0 * ambient_shade(1, true), light.ao[0]);
    assert_eq!(expected.1, light.lm[0]);
}

#[test]
fn smooth_lighting_snaps_non_parallel_endpoints_like_java() {
    let block = lighting_block_record();
    let top_vertex = lighting_quad(0, 1, 0.5, 1.0, 0.5);
    let bottom_vertex = lighting_quad(0, 1, 0.5, 0.0, 0.5);
    let top_light = smooth_lighting(&block, &top_vertex, lighting_state_record(0), 1, true);
    let bottom_light = smooth_lighting(&block, &bottom_vertex, lighting_state_record(0), 1, true);
    let weights_top = corner_weights(1, 0.5, 1.0, 0.5);
    let weights_bottom = corner_weights(1, 0.5, 0.0, 0.5);
    let expected_top = blend_ao_face(ao_face_data(&block, 1, true), weights_top);
    let expected_bottom = blend_ao_face(ao_face_data(&block, 1, false), weights_bottom);

    assert_eq!(expected_top.1, top_light.lm[0]);
    assert_eq!(expected_bottom.1, bottom_light.lm[0]);
    assert_ne!(top_light.lm[0], bottom_light.lm[0]);
}

#[test]
fn smooth_lighting_treats_parallel_full_cube_as_java_aligned_full_face() {
    let mut block = lighting_block_record();
    block.light_words[13] = pack_light_word(8, 8, 0, 4096, false, false, false, true);
    let quad = lighting_quad(MODEL_QUAD_FLAG_PARALLEL, 1, 0.4, 0.25, 0.6);
    let light = smooth_lighting(&block, &quad, lighting_state_record(0), 1, true);
    let face = ao_face_data(&block, 1, true);
    let (expected_lm, mut expected_ao) = map_ao_corners(1, face.lm, face.ao);
    for value in &mut expected_ao {
        *value *= ambient_shade(1, true);
    }

    assert_eq!(expected_lm, light.lm);
    for i in 0..4 {
        assert_close(expected_ao[i], light.ao[i]);
    }
}

#[test]
fn smooth_lighting_collapses_uniform_neighborhood_without_changing_result() {
    let mut block = lighting_block_record();
    let word = pack_light_word(7, 11, 3, 2048, false, false, false, false);
    block.light_words.fill(word);
    let quad = lighting_quad(0, 1, 0.4, 0.25, 0.6);
    let light = smooth_lighting(&block, &quad, lighting_state_record(0), 1, true);
    let lightmap = get_lightmap(word);
    let expected_lm = calculate_corner_brightness(
        lightmap, lightmap, lightmap, lightmap, false, false, false, false,
    );
    let expected_ao = unpack_ao(word) * ambient_shade(1, true);

    assert_eq!([expected_lm; 4], light.lm);
    for value in light.ao {
        assert_close(expected_ao, value);
    }
}

#[test]
fn static_model_native_quads_use_block_iris_render_type() {
    let mut block = lighting_block_record();
    block.local_x = 1;
    block.local_y = 2;
    block.local_z = 3;
    block.absolute_x = 145;
    block.absolute_y = 66;
    block.absolute_z = 531;
    let mut state = lighting_state_record(0);
    state.render_type = 2;
    let quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0, native.render_type);
    assert_eq!(145, native.local_x);
    assert_eq!(66, native.local_y);
    assert_eq!(531, native.local_z);
}

#[test]
fn static_model_zero_source_light_uses_computed_lighting() {
    let block = lighting_block_record();
    let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
    for vertex in &mut quad.vertices {
        vertex.light = 0;
    }
    let expected = native_quad_lighting(
        &block,
        &quad,
        lighting_state_record(STATE_FLAG_FULL_OCCLUSION),
    );

    let mut profile = NativeMeshingProfile::default();
    let native = static_model_quad_to_native_section(
        block,
        lighting_state_record(STATE_FLAG_FULL_OCCLUSION),
        quad,
        &mut profile,
        false,
        false,
    );

    assert_eq!(expected.lm[0], native.vertices[0].light);
    assert_ne!(0, native.vertices[0].light);
}

#[test]
fn static_model_source_light_uses_java_component_max_brightness() {
    assert_eq!(0x0010_0200, max_brightness(0x0010_0000, 0x0008_0200));
    assert_eq!(0x0008_0200, max_brightness(0x0000_0200, 0x0008_0000));
}

#[test]
fn static_model_state_tint_does_not_apply_without_quad_tint_index() {
    let mut block = lighting_block_record();
    block.tint = 0xff35_996fu32 as i32;
    let mut state = lighting_state_record(0);
    state.tint_type = TEST_TINT_FORCE_GRASS;
    let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
    quad.tint_index = -1;
    quad.vertices[0].color = 0xffff_ffffu32 as i32;
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0xffff_ffffu32 as i32, native.vertices[0].color);
}

#[test]
fn static_model_force_grass_tint_applies_with_quad_tint_index() {
    let mut block = lighting_block_record();
    block.tint = 0xff35_996fu32 as i32;
    let mut state = lighting_state_record(0);
    state.tint_type = TEST_TINT_FORCE_GRASS;
    let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
    quad.tint_index = 0;
    quad.vertices[0].color = 0xffff_ffffu32 as i32;
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0xff6f_9935u32 as i32, native.vertices[0].color);
}

fn lighting_quad(flags: i32, light_face: i32, x: f32, y: f32, z: f32) -> StaticModelQuadRecord {
    StaticModelQuadRecord {
        vertices: [StaticModelVertexRecord {
            x,
            y,
            z,
            color: -1,
            u: 0.0,
            v: 0.0,
            light: -1,
        }; 4],
        material_bits: 5,
        cull_face: -1,
        normal_face: MODEL_QUAD_FACING_UNASSIGNED as i32,
        packed_normal: 0,
        block_emission: 0,
        render_type: 0,
        shade: 1,
        flags,
        light_face,
        tint_index: -1,
        has_ao: 1,
        pass_id: -1,
    }
}

fn lighting_block_record() -> NativeSectionBlockRecord {
    let mut record = NativeSectionBlockRecord::default();
    record.fluid_block_id = -1;
    for dz in -1..=1 {
        for dy in -1..=1 {
            for dx in -1..=1 {
                let index = neighborhood_index(dx, dy, dz);
                let ao = 2048 + (index as i32 * 53);
                let block = ((index as i32 * 3) + 1) & 0xf;
                let sky = ((index as i32 * 5) + 2) & 0xf;
                let luminance = ((index as i32 * 7) + 3) & 0xf;
                record.light_words[index] =
                    pack_light_word(block, sky, luminance, ao, false, false, false, false);
            }
        }
    }
    record
}

fn lighting_state_record(flags: i32) -> NativeMeshingState {
    let mut state = NativeMeshingState::default();
    state.flags = flags;
    state
}

fn pack_light_word(
    block: i32,
    sky: i32,
    luminance: i32,
    ao: i32,
    em: bool,
    op: bool,
    fo: bool,
    fc: bool,
) -> i32 {
    (block & 0xf)
        | ((sky & 0xf) << 4)
        | ((luminance & 0xf) << 8)
        | ((ao & 0xffff) << 12)
        | ((em as i32) << 28)
        | ((op as i32) << 29)
        | ((fo as i32) << 30)
        | ((fc as i32) << 31)
}

fn assert_close(expected: f32, actual: f32) {
    assert!(
        (expected - actual).abs() < 0.00001,
        "expected {expected}, got {actual}"
    );
}

#[test]
fn native_quad_buffer_create_and_grow_returns_writable_memory() {
    unsafe {
        let mut handle = 0u64;
        let mut address = 0u64;
        assert_eq!(
            OK,
            mattmc_sodium_chunk_quad_buffer_create(1, &mut handle, &mut address)
        );
        assert_ne!(0, handle);
        assert_ne!(0, address);

        *(address as *mut NativeQuad) = quad();

        assert_eq!(
            OK,
            mattmc_sodium_chunk_quad_buffer_ensure_capacity(handle, 4, &mut address)
        );
        assert_ne!(0, address);
        *(address as *mut NativeQuad).add(3) = quad();

        assert_eq!(OK, mattmc_sodium_chunk_quad_buffer_destroy(handle));
    }
}

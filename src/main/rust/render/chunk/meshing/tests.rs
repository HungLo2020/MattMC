use super::*;

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
fn fluid_face_record_expands_semantic_side_face_to_quad() {
    let mut record = FluidFaceRecord {
        packed_normal: 0,
        material_bits: 5,
        block_emission: 7,
        render_type: 1,
        ignore_mid_block: 0,
        block_id: 41,
        local_x: 4,
        local_y: 5,
        local_z: 6,
        face_kind: 3,
        flip: 0,
        origin_x: 10.0,
        origin_y: 20.0,
        origin_z: 30.0,
        y_offset: 0.001,
        heights: [0.75, 0.5, 0.0, 0.0],
        side_coords: [0.0, 1.0, 1.0, 1.0],
        uvs: [0.0, 0.2, 0.5, 0.6, 1.0, 0.6, 1.0, 0.1],
        colors: [1, 2, 3, 4],
        aos: [0.1, 0.2, 0.3, 0.4],
        lights: [11, 12, 13, 14],
    };

    let quad = fluid_face_record_to_quad(record).unwrap();
    assert_eq!(11.0, quad.vertices[0].x);
    assert_eq!(20.5, quad.vertices[0].y);
    assert_eq!(31.0, quad.vertices[0].z);
    assert_eq!(10.0, quad.vertices[3].x);
    assert_eq!(20.75, quad.vertices[3].y);
    assert_eq!(31.0, quad.vertices[3].z);
    assert_eq!(5, quad.material_bits);
    assert_eq!(41, quad.block_id);

    record.flip = 1;
    let flipped = fluid_face_record_to_quad(record).unwrap();
    assert_eq!(quad.vertices[0].x, flipped.vertices[0].x);
    assert_eq!(quad.vertices[3].x, flipped.vertices[1].x);
    assert_eq!(quad.vertices[2].x, flipped.vertices[2].x);
    assert_eq!(quad.vertices[1].x, flipped.vertices[3].x);
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
fn direct_static_template_encoding_matches_generic_compact_quad() {
    let mut block = lighting_block_record();
    block.local_x = 3;
    block.local_y = 4;
    block.local_z = 5;
    block.absolute_x = 99;
    block.absolute_y = 66;
    block.absolute_z = -14;
    block.tint = 0xff35_996fu32 as i32;
    let mut state = lighting_state_record(STATE_FLAG_FULL_OCCLUSION);
    state.tint_type = TINT_FORCE_GRASS;
    state.material_bits = 7;
    let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
    quad.cull_face = -1;
    quad.tint_index = -1;
    quad.vertices[0].color = 0xffff_ffffu32 as i32;
    quad.vertices[1].light = 0;

    let format = NativeFormat {
        vertex_stride: 20,
        block_id_offset: 0,
        normal_offset: 0,
        tangent_offset: 0,
        mid_uv_offset: 0,
        mid_block_offset: 0,
        section_index: 5,
        separate_ao: false,
    };
    let mut profile = NativeMeshingProfile::default();
    let generic_quad =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);
    let mut expected = vec![0u8; 4 * format.vertex_stride];
    encode_quad(&generic_quad, &mut expected, format);

    let mut builder = create_section_mesh_builder(0);
    let mut pending_counts = [0usize; MODEL_QUAD_FACING_COUNT];
    let direct = unsafe {
        push_static_model_template_quad(
            &mut builder,
            &block as *const NativeSectionBlockRecord,
            state,
            &quad as *const StaticModelQuadRecord,
            MODEL_QUAD_FACING_UNASSIGNED,
            &mut pending_counts,
            format,
            false,
            false,
        )
    }
    .unwrap();
    assert_eq!(0, direct);
    let committed = unsafe {
        flush_static_model_template_face(
            &mut builder,
            MODEL_QUAD_FACING_UNASSIGNED,
            &mut pending_counts,
            format,
            false,
            false,
        )
    }
    .unwrap();

    assert_eq!(1, committed);
    assert_eq!(1, builder.counts[MODEL_QUAD_FACING_UNASSIGNED]);
    assert_eq!(
        expected,
        builder.buffers[MODEL_QUAD_FACING_UNASSIGNED].encoded[..expected.len()]
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
    let block = lighting_block_record();
    let quad = lighting_quad(MODEL_QUAD_FLAG_PARALLEL, 1, 0.4, 0.25, 0.6);
    let light = smooth_lighting(
        &block,
        &quad,
        lighting_state_record(STATE_FLAG_FULL_OCCLUSION),
        1,
        true,
    );
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
fn static_model_force_grass_tint_applies_without_quad_tint_index() {
    let mut block = lighting_block_record();
    block.tint = 0xff35_996fu32 as i32;
    let mut state = lighting_state_record(0);
    state.tint_type = TINT_FORCE_GRASS;
    let mut quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 1, 0.0, 1.0, 0.0);
    quad.tint_index = -1;
    quad.vertices[0].color = 0xffff_ffffu32 as i32;
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0xff6f_9935u32 as i32, native.vertices[0].color);
}

#[test]
fn native_fluid_uses_fluid_shader_block_id_not_container_block_id() {
    let mut block = lighting_block_record();
    block.block_id = 1234;
    block.fluid_block_id = 5678;
    block.fluid_tint = 0xff3f_76e4u32 as i32;
    let mut state = lighting_state_record(0);
    state.fluid_type = FLUID_WATER;
    state.fluid_block_id = 9012;
    state.fluid_material_bits = 9;
    state.fluid_still = FluidSprite {
        u0: 0.0,
        u1: 1.0,
        v0: 0.0,
        v1: 1.0,
        shrink: 0.0,
    };

    let (record, _) = fluid_semantic_face(
        state,
        &block,
        MODEL_QUAD_FACING_POS_Y,
        false,
        FLUID_FACE_TOP_NW_SE,
        0.0,
        [1.0; 4],
        [0.0; 4],
        [(0.0, 0.0), (0.0, 1.0), (1.0, 1.0), (1.0, 0.0)],
        argb_to_abgr(block.fluid_tint),
        1.0,
        LIGHT_FULL_BRIGHT,
    );
    let quad = fluid_face_record_to_quad(record).unwrap();

    assert_eq!(5678, quad.block_id);
    assert_eq!(1, quad.render_type);
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
        _padding: 0,
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

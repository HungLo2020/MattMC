use super::blend::{blend_ao_face, blend_inset_ao_face};
use super::face::ao_face_data;
use super::parity::{
    ambient_shade, calculate_corner_brightness, get_lightmap, max_brightness, pack_light, unpack_ao,
};
use super::sampling::{corner_weights, face_depth, map_ao_corners, neighborhood_index};
use super::*;

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
        false,
        &mut profile,
        false,
        false,
    );

    assert_eq!(expected.lm[0], native.vertices[0].light);
    assert_ne!(0, native.vertices[0].light);
}

#[test]
fn separate_ao_vertex_format_preserves_raw_ao_for_shader_side_face_shading() {
    let block = lighting_block_record();
    let quad = lighting_quad(MODEL_QUAD_FLAG_ALIGNED, 5, 1.0, 1.0, 1.0);

    let baked = native_quad_lighting(&block, &quad, lighting_state_record(0));
    let separate =
        native_quad_lighting_for_vertex_format(&block, &quad, lighting_state_record(0), true);

    assert_close(separate.ao[0] * ambient_shade(5, true), baked.ao[0]);
    assert!(separate.ao[0] > baked.ao[0]);
    assert_eq!(separate.lm, baked.lm);
}

#[test]
fn static_model_source_light_uses_java_component_max_brightness() {
    assert_eq!(0x0010_0200, max_brightness(0x0010_0000, 0x0008_0200));
    assert_eq!(0x0008_0200, max_brightness(0x0000_0200, 0x0008_0000));
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
    NativeMeshingState {
        flags,
        ..NativeMeshingState::default()
    }
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

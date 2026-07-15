use super::*;

const TEST_TINT_FORCE_GRASS: i32 = 10;

#[test]
fn static_model_native_quads_use_block_iris_render_type() {
    let mut block = static_model_test_block_record();
    block.local_x = 1;
    block.local_y = 2;
    block.local_z = 3;
    block.absolute_x = 145;
    block.absolute_y = 66;
    block.absolute_z = 531;
    let mut state = static_model_test_state_record();
    state.render_type = 2;
    let quad = static_model_test_quad();
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0, native.render_type);
    assert_eq!(145, native.local_x);
    assert_eq!(66, native.local_y);
    assert_eq!(531, native.local_z);
}

#[test]
fn static_model_state_tint_does_not_apply_without_quad_tint_index() {
    let mut block = static_model_test_block_record();
    block.tint = 0xff35_996fu32 as i32;
    let mut state = static_model_test_state_record();
    state.tint_type = TEST_TINT_FORCE_GRASS;
    let mut quad = static_model_test_quad();
    quad.tint_index = -1;
    quad.vertices[0].color = 0xffff_ffffu32 as i32;
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0xffff_ffffu32 as i32, native.vertices[0].color);
}

#[test]
fn static_model_force_grass_tint_applies_with_quad_tint_index() {
    let mut block = static_model_test_block_record();
    block.tint = 0xff35_996fu32 as i32;
    let mut state = static_model_test_state_record();
    state.tint_type = TEST_TINT_FORCE_GRASS;
    let mut quad = static_model_test_quad();
    quad.tint_index = 0;
    quad.vertices[0].color = 0xffff_ffffu32 as i32;
    let mut profile = NativeMeshingProfile::default();

    let native =
        static_model_quad_to_native_section(block, state, quad, &mut profile, false, false);

    assert_eq!(0xff6f_9935u32 as i32, native.vertices[0].color);
}

fn static_model_test_quad() -> StaticModelQuadRecord {
    StaticModelQuadRecord {
        vertices: [StaticModelVertexRecord {
            x: 0.0,
            y: 1.0,
            z: 0.0,
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
        flags: MODEL_QUAD_FLAG_ALIGNED,
        light_face: 1,
        tint_index: -1,
        has_ao: 0,
        pass_id: -1,
    }
}

fn static_model_test_block_record() -> NativeSectionBlockRecord {
    let mut record = NativeSectionBlockRecord::default();
    record.fluid_block_id = -1;
    record
}

fn static_model_test_state_record() -> NativeMeshingState {
    NativeMeshingState::default()
}

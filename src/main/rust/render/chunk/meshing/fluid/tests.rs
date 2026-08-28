use super::*;

#[test]
fn native_fluid_admission_is_explicitly_bounded_to_water_and_lava() {
    assert!(is_supported_native_fluid_type(FLUID_WATER));
    assert!(is_supported_native_fluid_type(FLUID_LAVA));
    assert!(!is_supported_native_fluid_type(0));
    assert!(!is_supported_native_fluid_type(99));
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
        primitive_kind: TERRAIN_PRIMITIVE_BUILTIN_WATER,
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
fn flipped_fluid_faces_flip_packed_normals_like_java_writer() {
    assert_eq!(0x00007f00, flip_packed_normal(0x00008100));
    assert_eq!(0x00008100, flip_packed_normal(0x00007f00));
    assert_eq!(0x0081007f, flip_packed_normal(0x007f0081));
}

#[test]
fn native_fluid_uses_fluid_shader_block_id_not_container_block_id() {
    let mut block = NativeSectionBlockRecord {
        block_id: 1234,
        fluid_block_id: 5678,
        fluid_tint: 0xff3f_76e4u32 as i32,
        ..NativeSectionBlockRecord::default()
    };
    block.absolute_x = block.local_x;
    block.absolute_y = block.local_y;
    block.absolute_z = block.local_z;

    let state = NativeMeshingState {
        fluid_type: FLUID_WATER,
        fluid_block_id: 9012,
        fluid_material_bits: 9,
        fluid_still: FluidSprite {
            u0: 0.0,
            u1: 1.0,
            v0: 0.0,
            v1: 1.0,
            shrink: 0.0,
        },
        ..NativeMeshingState::default()
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

#[test]
fn flowing_fluid_top_trig_initializes_without_native_stack_table() {
    let (dir, sin, cos) = flowing_top_trig_for_test(0.70710677, 0.70710677);

    assert!(dir.is_finite());
    assert!(sin.is_finite());
    assert!(cos.is_finite());
    assert!((dir + std::f32::consts::FRAC_PI_4).abs() < 0.001);
}

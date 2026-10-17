use super::*;

fn dark_disc_quad() -> WorldMaterialQuadRequest {
    let mut quad = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_NO_WRITE);
    quad.material_id = WORLD_MATERIAL_ID_SKY_DARK_DISC;
    quad.texture_id = WORLD_MATERIAL_TEXTURE_GENERATED_WHITE;
    quad.source_program = WORLD_MATERIAL_SOURCE_TEXTURED;
    quad.color_argb = 0xff00_0000;
    quad.source_color_argb = 0xff00_0000;
    quad.vertex_color_argb = [0xff00_0000; 4];
    quad.cull_policy = WORLD_CULL_NONE;
    quad.vertices = [
        [-0.8, -16.0, -0.8],
        [0.8, -16.0, -0.8],
        [0.8, -16.0, 0.8],
        [-0.8, -16.0, 0.8],
    ];
    quad
}

#[test]
fn dark_sky_material_packs_its_own_fog_without_changing_generic_quad_layout() {
    let mut scene = frame(Vec::new());
    scene.shader_environment.fog_sky_end = 32.0;
    scene.shader_environment.fog_parameter_color = [0.4, 0.6, 0.8, 0.5];
    scene.material_quads = vec![
        dark_disc_quad(),
        material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE),
    ];
    let batches = material_batches(&scene, ColorFormat::Rgba8Unorm, RasterYDirection::Up);
    for batch in batches {
        let bytes = packed_material_uniforms_for_batch(&scene, &batch).unwrap();
        if batch.key.material_id == WORLD_MATERIAL_ID_SKY_DARK_DISC {
            assert_eq!(
                WORLD_SKY_MATERIAL_HEADER_BYTES + WORLD_MATERIAL_QUAD_BYTES,
                bytes.len()
            );
            assert_eq!(32.0, read_f32(&bytes, 35));
            for (index, expected) in [0.4, 0.6, 0.8, 0.5].into_iter().enumerate() {
                assert_eq!(expected, read_f32(&bytes, 36 + index));
            }
            assert_eq!(-16.0, read_f32(&bytes, 41));
        } else {
            assert_eq!(
                WORLD_MATERIAL_HEADER_BYTES + WORLD_MATERIAL_QUAD_BYTES,
                bytes.len()
            );
            assert_eq!(0.0, read_f32(&bytes, 35));
        }
    }
}

#[test]
fn source_sky_disc_role_does_not_discard_unrelated_generated_white_materials() {
    let mut generic = dark_disc_quad();
    generic.material_id = WORLD_MATERIAL_ID_OPAQUE_TEXTURED;
    generic.depth_policy = WORLD_DEPTH_POLICY_DISABLED;
    let mut scene = frame(Vec::new());
    scene.material_quads = vec![generic, dark_disc_quad()];
    let error = source_material_batches_for_program(
        &scene,
        WORLD_MATERIAL_SOURCE_TEXTURED,
        &[WORLD_MATERIAL_MODE_OPAQUE],
    )
    .unwrap_err();
    assert!(
        error
            .to_string()
            .contains("unsupported source depth policy"),
        "{error}"
    );
    scene.material_quads[0].depth_policy = WORLD_DEPTH_POLICY_TEST_WRITE;
    let batches = source_material_batches_for_program(
        &scene,
        WORLD_MATERIAL_SOURCE_TEXTURED,
        &[WORLD_MATERIAL_MODE_OPAQUE],
    )
    .unwrap();
    assert_eq!(1, batches.len());
}

#[test]
fn dark_sky_role_rejects_particle_and_copied_texture_contracts() {
    let scene = frame(Vec::new());
    let quad = dark_disc_quad();
    frame::material_quads::validate_quad(&quad, &scene).unwrap();
    let mut particle = quad.clone();
    particle.source_program = WORLD_MATERIAL_SOURCE_PARTICLES;
    assert!(frame::material_quads::validate_quad(&particle, &scene).is_err());
    let mut textured = quad.clone();
    textured.texture_id = WORLD_MATERIAL_TEXTURE_STONE;
    assert!(frame::material_quads::validate_quad(&textured, &scene).is_err());
    let mut writing_depth = quad;
    writing_depth.depth_policy = WORLD_DEPTH_POLICY_TEST_WRITE;
    assert!(frame::material_quads::validate_quad(&writing_depth, &scene).is_err());
}

#[test]
fn native_dark_sky_pixels_match_frozen_local_distance_fog_and_camera_relative_transform() {
    let mut gal =
        crate::render::vulkanic::test_support::vulkan_gal("dark sky fog regression").unwrap();
    let mut frontend = WorldPrimitiveFrontend::default();
    for (end, alpha, translation, expected) in [
        (8.0, 1.0, 0.0, [102, 153, 204, 255]),
        (8.0, 1.0, 100.0, [102, 153, 204, 255]),
        (32.0, 1.0, 0.0, [51, 77, 102, 255]),
        (8.0, 0.5, 0.0, [51, 77, 102, 255]),
        (8.0, 0.0, 0.0, [0, 0, 0, 255]),
        (1.0e13, 1.0, 0.0, [0, 0, 0, 255]),
    ] {
        let mut scene = frame(Vec::new());
        scene.shader_environment = complete_source_material_test_environment();
        scene.shader_environment.fog_parameter_color = [0.4, 0.6, 0.8, alpha];
        scene.shader_environment.fog_sky_end = end;
        scene.view_matrix[12..15].fill(translation);
        // Observe the local XZ fan plane orthographically; Y contributes no
        // clip depth, so these samples isolate Frozen's pre-transform distances.
        scene.projection_matrix = [
            1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ];
        scene.material_quads = vec![dark_disc_quad()];
        let pixels =
            render_material_scene(&mut gal, &mut frontend, 1, 128, 128, scene, "dark-sky-fog")
                .unwrap()
                .pixels;
        let offset = (64 * 128 + 64) * 4;
        assert_rgba_near(
            pixels[offset..offset + 4].try_into().unwrap(),
            expected,
            2,
            "Frozen dark-disc fog",
        );
    }
    frontend.reset(&mut gal);
}

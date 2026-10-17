//! Exercise direct DH pass dependencies through whole-frame GAL submission.

use super::*;

fn direct_frame(flags: u32, size: u32) -> WorldPrimitiveFrame {
    let mut frame = frame(Vec::new());
    // Copied environment/lightmap semantics are independent of pack selection.
    frame.shader_environment.enabled = true;
    frame.shader_environment.far_plane = 1024.0;
    frame.viewport_width = size;
    frame.viewport_height = size;
    frame.background.viewport_width = size;
    frame.background.viewport_height = size;
    frame.lod_render_frame = WorldLodRenderFrame {
        enabled: true,
        flags: WORLD_LOD_FLAG_RUST_ROUTE_SELECTED | flags,
        combined_matrix: frame.projection_matrix,
        model_view_matrix: frame.view_matrix,
        projection_matrix: frame.projection_matrix,
        projection_inverse_matrix: frame.projection_matrix,
        micro_offset: 0.01,
        ..WorldLodRenderFrame::default()
    };
    // Fog keeps the private compositor active on the no-fade control frame.
    frame.lod_render_frame.dh_fog_parameters[16] = 1.0;
    let mut mesh = mesh_instance(81, 1);
    mesh.viewport_width = size;
    mesh.viewport_height = size;
    frame.mesh_instances.push(mesh);
    frame
}

fn prepare_frontend(gal: &mut VulkanicGal) -> WorldPrimitiveFrontend {
    let mut frontend = WorldPrimitiveFrontend::default();
    let mut translucent = mesh_asset(82, 1, IndexType::U16);
    translucent.sections[0].material_id = WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
    translucent.sections[0].material_mode = WORLD_MATERIAL_MODE_TRANSLUCENT;
    frontend
        .apply_world_mesh_asset_update(
            gal,
            1,
            vec![mesh_asset(81, 1, IndexType::U16), translucent],
            Vec::new(),
        )
        .unwrap();
    frontend
        .ensure_rust_lod_lightmap_for_frame(gal, 1, &direct_frame(0, 128))
        .unwrap();
    frontend
}

#[test]
fn direct_dh_composition_snapshot_barriers_follow_same_frame_sample_state() {
    for fade in [
        WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS,
        WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS,
    ] {
        for far_fade in [0, WORLD_LOD_FLAG_DH_FAR_CLIP_FADE] {
            for with_translucent in [false, true] {
                let mut gal = gal();
                let mut frontend = prepare_frontend(&mut gal);
                let target = frame_target(&mut gal, 1, 128, 128);
                let mut frame = direct_frame(fade | far_fade, 128);
                if with_translucent {
                    frame.mesh_instances.push(mesh_instance(82, 1));
                }
                frontend
                    .ensure_rust_lod_lightmap_for_frame(&mut gal, 1, &frame)
                    .unwrap();
                let (ops, _) = frontend
                    .append_frame_ops(&mut gal, 1, target, frame)
                    .unwrap();
                let resources = frontend.lod_direct_composition_resources.as_ref().unwrap();
                for texture in [
                    resources.vanilla_color_texture,
                    resources.vanilla_depth_texture,
                ] {
                    let mut state = TextureUsageState::Undefined;
                    let mut copies = 0;
                    for op in &ops {
                        if let CommandOp::Barrier(barrier) = op {
                            if barrier.resource == texture {
                                assert_eq!(barrier.before, state,
                                "fade={fade} far_fade={far_fade}: earlier resolver use must survive the fade boundary");
                                state = barrier.after;
                                copies += usize::from(state == TextureUsageState::TransferDst);
                            }
                        }
                    }
                    assert_eq!(state, TextureUsageState::ShaderRead);
                    assert_eq!(
                        copies,
                        usize::from(far_fade != 0)
                            + if fade == WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS {
                                2
                            } else {
                                1
                            }
                    );
                }
            }
        }
    }
}

#[test]
fn direct_dh_composition_first_and_repeated_frames_cover_all_fade_boundaries() {
    for fade in [
        0,
        WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS,
        WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS,
        WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS | WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS,
    ] {
        for far_fade in [0, WORLD_LOD_FLAG_DH_FAR_CLIP_FADE] {
            let mut gal = gal();
            let mut frontend = prepare_frontend(&mut gal);
            let target = frame_target(&mut gal, 1, 128, 128);
            frontend
                .ensure_rust_lod_lightmap_for_frame(
                    &mut gal,
                    1,
                    &direct_frame(fade | far_fade, 128),
                )
                .unwrap();
            for frame_id in 1..=2 {
                let mut frame = direct_frame(fade | far_fade, 128);
                frame.frame_id = frame_id;
                frontend
                    .submit_whole_frame(&mut gal, frame_id, target, frame, Vec::new())
                    .unwrap_or_else(|error| {
                        panic!("fade={fade} far_fade={far_fade} frame={frame_id}: {error:?}")
                    });
                assert!(frontend.lod_vanilla_sample_state_initialized,
                    "successful resolver submission must confirm sampled layout even without a pixel copy");
            }
        }
    }
}

#[test]
fn direct_dh_composition_no_fade_to_double_pass_and_resize_preserve_dependencies() {
    let mut gal = gal();
    let mut frontend = prepare_frontend(&mut gal);
    let target = frame_target(&mut gal, 1, 128, 128);
    frontend
        .ensure_rust_lod_lightmap_for_frame(&mut gal, 1, &direct_frame(0, 128))
        .unwrap();
    frontend
        .submit_whole_frame(&mut gal, 1, target, direct_frame(0, 128), Vec::new())
        .unwrap();
    frontend
        .submit_whole_frame(
            &mut gal,
            2,
            target,
            direct_frame(WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS, 128),
            Vec::new(),
        )
        .unwrap();
    let resized = frame_target(&mut gal, 2, 64, 64);
    frontend
        .submit_whole_frame(
            &mut gal,
            3,
            resized,
            direct_frame(WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS, 64),
            Vec::new(),
        )
        .unwrap();
}

#[test]
fn direct_dh_composition_failed_submission_does_not_confirm_snapshot_state() {
    let mut gal = gal();
    let mut frontend = prepare_frontend(&mut gal);
    let target = frame_target(&mut gal, 1, 128, 128);
    frontend
        .ensure_rust_lod_lightmap_for_frame(&mut gal, 1, &direct_frame(0, 128))
        .unwrap();
    // Create resources first so the injected rejection reaches world submission.
    frontend
        .ensure_lod_direct_composition_resources(
            &mut gal,
            target,
            ColorFormat::Bgra8Unorm,
            RasterYDirection::Up,
            true,
        )
        .unwrap();
    gal.mock_backend_mut().unwrap().fail_next_submit = true;
    assert!(frontend
        .submit_whole_frame(&mut gal, 1, target, direct_frame(0, 128), Vec::new())
        .is_err());
    assert!(!frontend.lod_vanilla_sample_state_initialized);
    frontend
        .submit_whole_frame(
            &mut gal,
            2,
            target,
            direct_frame(WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS, 128),
            Vec::new(),
        )
        .unwrap();
}

/// Frozen draws particles in their own pass after the main pass, so DH's
/// vanilla-fade boundaries never see them. A particle drawn before a fade
/// boundary is faded by the depth/colour behind it: falling leaves showed
/// sky and LOD colour that changed every frame (DH on, shaders off).
#[test]
fn direct_dh_fade_composites_run_before_particle_draws() {
    for fade in [
        WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS,
        WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS,
    ] {
        let mut gal = gal();
        let mut frontend = prepare_frontend(&mut gal);
        let target = frame_target(&mut gal, 1, 128, 128);
        let mut frame = direct_frame(fade, 128);
        frame.mesh_instances.push(mesh_instance(82, 1));
        let mut particle = material_quad(WORLD_MATERIAL_MODE_OPAQUE, WORLD_DEPTH_POLICY_TEST_WRITE);
        particle.source_program = WORLD_MATERIAL_SOURCE_PARTICLES;
        frame.material_quads.push(particle);
        frontend
            .ensure_rust_lod_lightmap_for_frame(&mut gal, 1, &frame)
            .unwrap();
        let (ops, _) = frontend.append_frame_ops(&mut gal, 1, target, frame).unwrap();
        let snapshot = frontend
            .lod_direct_composition_resources
            .as_ref()
            .unwrap()
            .vanilla_color_texture;
        let last_fade_snapshot = ops
            .iter()
            .rposition(|op| matches!(op, CommandOp::Barrier(barrier)
                if barrier.resource == snapshot && barrier.after == TextureUsageState::TransferDst))
            .unwrap_or_else(|| panic!("fade={fade}: no DH vanilla snapshot recorded"));
        let particle_draw = ops
            .iter()
            .rposition(|op| matches!(op, CommandOp::DrawIndexed { indices: 6, .. }))
            .unwrap_or_else(|| panic!("fade={fade}: particle material was not drawn"));
        assert!(
            particle_draw > last_fade_snapshot,
            "fade={fade}: particles must draw after the last DH fade boundary"
        );
    }
}

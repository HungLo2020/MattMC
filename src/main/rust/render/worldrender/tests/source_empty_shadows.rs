//! Empty source frames still have fullscreen consumers of both shadow depths.

use super::*;
mod native;
mod capture_extent;
mod uniform_receipts;
mod capture_availability;

fn empty_source_frame() -> (
    VulkanicGal,
    WorldPrimitiveFrontend,
    Handle,
    WorldPrimitiveFrame,
) {
    let mut gal = gal();
    let target = frame_target(&mut gal, 1, 128, 128);
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.enable_candidate_source_preparation_for_test();
    let source = complete_bundled_pack_source_for_test();
    frontend
        .apply_shader_pack_source_update(ShaderPackSourceUpdate {
            pack_name: source.name().into(),
            generation: source.generation(),
            files: source.files(),
        })
        .unwrap();
    let mut scene = shader_mesh_scene_frame(128, 128, 0);
    enter_shader_candidate_overworld(&mut scene);
    scene.voxel_volume = private_voxel_volume_frame([0.25, 0.5, 0.75]);
    scene.mesh_instances.clear();
    assert!(scene.lod_instances.is_empty());
    (gal, frontend, target, scene)
}

fn cleanup(mut gal: VulkanicGal, mut frontend: WorldPrimitiveFrontend, target: Handle) {
    gal.retire_through_for_test(gal.latest_submission_id())
        .unwrap();
    frontend.reset(&mut gal);
    gal.destroy(target).unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn empty_source_preparation_records_the_owned_opaque_shadow_snapshot() {
    let (mut gal, mut frontend, target, scene) = empty_source_frame();
    let (ops, _) = frontend
        .append_frame_ops_inner_with_source_preparation(
            &mut gal,
            1,
            target,
            scene,
            true,
            RasterYDirection::Up,
            true,
        )
        .unwrap();
    let graph = frontend.g_buffer_resources.as_ref().unwrap();
    assert!(
        ops.iter()
            .any(|op| matches!(op, CommandOp::CopyTexture(copy)
        if copy.src_texture == graph.shadow_depth_texture
            && copy.dst_texture == graph.shadow_depth_opaque_texture
            && copy.extent == graph.shadow_extent)),
        "the empty preparation graph must initialize shadowtex1 from cleared shadowtex0"
    );
    cleanup(gal, frontend, target);
}

#[test]
fn empty_named_source_plan_keeps_shadow_targets_without_preparing_caster_draws() {
    let (mut gal, mut frontend, target, scene) = empty_source_frame();
    frontend
        .append_frame_ops_inner_with_source_preparation(
            &mut gal,
            1,
            target,
            scene.clone(),
            true,
            RasterYDirection::Up,
            true,
        )
        .unwrap();
    // Keep this producer check independent of pack assets and late draw
    // families: no geometry reads them in the empty frame being diagnosed.
    let mut files = vec![ShaderSourceFile::new("block.properties", "")];
    for name in ["gbuffers_terrain", "shadow", "composite", "final"] {
        files.push(ShaderSourceFile::new(
            format!("{name}.vsh"),
            "#version 130\nvoid main() { gl_Position=ftransform(); }\n",
        ));
        files.push(ShaderSourceFile::new(
            format!("{name}.fsh"),
            "#version 130\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=vec4(1.0); }\n",
        ));
    }
    let source = ShaderPackSource::new(
        "empty-source-shadow",
        frontend.shader_pack_source_generation().unwrap(),
        files,
    )
    .unwrap();
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            Vec::new(),
            vec![WorldMeshTextureAssetPayload {
                texture_id: WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
                png_bytes: material_scene_png(0),
                mip_png_bytes: Vec::new(),
                frame_width: 0,
                frame_height: 0,
                frame_count: 1,
                frame_ticks: 1,
                animation_flags: 0,
                frame_row_size: 0,
                interpolation_policy: 0,
                animation_frames: Vec::new(),
                coordinate_origin: 0,
                sampling: None,
                requested_mip_levels: 0,
            }],
        )
        .unwrap();
    frontend
        .shader_runtime
        .as_mut()
        .unwrap()
        .observe_source_candidate_for_scope(&source, TerrainProgramScope::Default);
    // This constant-output pack deliberately has no external sampled inputs.
    frontend.candidate_source_resource_snapshot = Some(CandidateSourceResourceSnapshot {
        shader_pack_generation: source.generation(),
        world_generation: scene.voxel_volume.world_generation,
        frame_id: scene.frame_id,
        resources:
            crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResourceSet::new(
                TerrainSourceResourceAvailabilitySet::new(
                    source.generation(),
                    scene.voxel_volume.world_generation,
                    [],
                )
                .unwrap(),
                [],
            )
            .unwrap(),
    });
    let runtime = frontend.shader_runtime.as_ref().unwrap();
    let programs = LoweredSourceTerrainPrograms {
        opaque: runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
            .unwrap()
            .unwrap(),
        cutout: runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)
            .unwrap()
            .unwrap(),
        shadow: runtime
            .prepared_lowered_shadow_source_program()
            .unwrap()
            .unwrap(),
        translucent: None,
    };
    let graph = frontend.g_buffer_resources.as_ref().unwrap();
    let (generation, extent, depth, view, shadow_targets) = (
        graph.generation,
        graph.extent,
        graph.depth_texture,
        graph.depth_view,
        terrain_source_shadow_pass_targets(graph),
    );
    let live_before = gal.metrics().resource_creates - gal.metrics().resource_destroys;
    for _ in 0..3 {
        let plan = frontend
            .prepare_named_source_terrain_frame_plan(
                &mut gal,
                &programs,
                scene.voxel_volume.world_generation,
                generation,
                &scene,
                &[],
                &Default::default(),
                &[],
                extent,
                depth,
                view,
                shadow_targets,
                ShaderPackColorClearValues {
                    fog_color: background_clear_color(&scene.background),
                },
                false,
            )
            .unwrap();
        assert!(plan.shadow_only_draws.is_empty());
        assert!(plan.entity_shadow_draws.is_empty());
        assert!(
            plan.shadow_targets.is_some(),
            "fullscreen shadow readers require a clearing pass even without casters"
        );
        PreparedNamedSourceFramePlan {
            terrain: plan,
            distant_horizons: None,
            fullscreen_consumers: Vec::new(),
            final_output: None,
        }
        .discard(&mut frontend, &mut gal);
        assert!(!frontend
            .shader_runtime
            .as_ref()
            .unwrap()
            .has_pending_source_color_targets());
        assert!(
            !frontend
                .g_buffer_resources
                .as_ref()
                .unwrap()
                .shadow_targets_initialized,
            "discarded preparation must not advance submission-confirmed shadow state"
        );
        assert_eq!(
            live_before,
            gal.metrics().resource_creates - gal.metrics().resource_destroys,
            "repeated rejected source plans must reclaim all target consumers and images"
        );
    }
    // A generation replacement must also release an inert pending plan's
    // cached consumers before destroying the old runtime's color images.
    let pending = frontend
        .prepare_named_source_terrain_frame_plan(
            &mut gal,
            &programs,
            scene.voxel_volume.world_generation,
            generation,
            &scene,
            &[],
            &Default::default(),
            &[],
            extent,
            depth,
            view,
            shadow_targets,
            ShaderPackColorClearValues {
                fog_color: background_clear_color(&scene.background),
            },
            false,
        )
        .unwrap();
    assert!(frontend
        .shader_runtime
        .as_ref()
        .unwrap()
        .has_pending_source_color_targets());
    drop(pending);
    frontend.ensure_shader_runtime(&mut gal, 2).unwrap();
    cleanup(gal, frontend, target);
}

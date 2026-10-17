//! Selected sky staging: Frozen's horizon is independent of disc visibility.
use super::*;
use crate::render::shaderpack::lowering::FullscreenSourceRasterPrimitive;

#[test]
fn source_horizon_staging_precedes_disc_survives_fog_and_rejects_missing_semantics_without_leaks() {
    let mut files = vec![ShaderSourceFile::new("block.properties", "")];
    for name in ["gbuffers_terrain", "gbuffers_skybasic", "final"] {
        files.push(ShaderSourceFile::new(format!("world0/{name}.vsh"),
            "#version 130\nvoid main(){gl_Position=ftransform();}"));
        files.push(ShaderSourceFile::new(format!("world0/{name}.fsh"),
            "#version 130\n/* DRAWBUFFERS:0 */\nvoid main(){gl_FragData[0]=vec4(1.0);}"));
    }
    let source = ShaderPackSource::new("horizon-staging", 1, files).unwrap();
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut gal = gal();
    let targets = runtime.stage_complete_source_color_targets(&mut gal, 1,
        Extent3d { width: 16, height: 16, depth: 1 }).unwrap().unwrap();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.apply_shader_pack_source_update(ShaderPackSourceUpdate {
        pack_name: source.name().into(), generation: source.generation(), files: source.files(),
    }).unwrap();
    frontend.apply_world_mesh_asset_update(&mut gal, 1, Vec::new(), vec![WorldMeshTextureAssetPayload {
        texture_id: WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS, png_bytes: material_scene_png(0),
        mip_png_bytes: Vec::new(), frame_width: 0, frame_height: 0, frame_count: 1,
        frame_ticks: 1, animation_flags: 0, frame_row_size: 0, interpolation_policy: 0,
        animation_frames: Vec::new(), coordinate_origin: 0, sampling: None, requested_mip_levels: 0,
    }]).unwrap();
    frontend.shader_runtime = Some(runtime);
    let empty = TerrainSourceOwnedResourceSet::new(
        TerrainSourceResourceAvailabilitySet::new(1, 1, []).unwrap(), []).unwrap();
    frontend.candidate_source_resource_snapshot = Some(CandidateSourceResourceSnapshot {
        shader_pack_generation: 1, world_generation: 1, frame_id: 1, resources: empty.clone(),
    });
    let mut frame = frame(Vec::new());
    frame.voxel_volume = private_voxel_volume_frame([0.0; 3]);
    frame.background.sky.visible = true;
    frame.shader_environment.enabled = true;
    frame.shader_environment.far_plane = 160.0;
    for (sky_type, visible, expected) in [
        (WORLD_BACKGROUND_SKY_OVERWORLD, true, vec![FullscreenSourceRasterPrimitive::ShaderPackHorizon, FullscreenSourceRasterPrimitive::VanillaSkyDisc]),
        (WORLD_BACKGROUND_SKY_OVERWORLD, false, vec![FullscreenSourceRasterPrimitive::ShaderPackHorizon]),
        (WORLD_BACKGROUND_SKY_NETHER, false, Vec::new()),
        (WORLD_BACKGROUND_SKY_END, false, Vec::new()),
    ] {
        frame.background.sky_type = sky_type;
        frame.background.sky.visible = visible;
        let consumers = frontend.prepare_pre_terrain_source_sky_consumers(&mut gal, &frame, &targets, &empty).unwrap();
        assert_eq!(expected, consumers.iter().map(|c| c.program.raster_primitive).collect::<Vec<_>>());
        destroy_named_source_fullscreen_consumers(&mut gal, consumers);
    }
    frame.background.sky_type = WORLD_BACKGROUND_SKY_OVERWORLD;
    frame.shader_environment.enabled = false;
    let creates = gal.metrics().resource_creates;
    assert!(frontend.prepare_pre_terrain_source_sky_consumers(&mut gal, &frame, &targets, &empty).is_err());
    assert_eq!(creates, gal.metrics().resource_creates, "failed semantic packing staged resources");
    frontend.reset(&mut gal);
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

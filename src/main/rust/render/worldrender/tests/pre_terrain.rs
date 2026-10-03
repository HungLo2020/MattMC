//! Recording regression: begin/shadow/prepare ordering and opaque feedback visibility.
//! Synthetic command recording supplements, but does not replace, live capture.
use super::*;

#[test]
fn pre_terrain_fullscreen_recording_orders_shadows_and_publishes_opaque_before_deferred() {
    let mut files = vec![ShaderSourceFile::new("block.properties", "")];
    for (name, slot) in [("gbuffers_terrain", 1), ("begin", 7), ("prepare", 6), ("final", 0)] {
        files.push(ShaderSourceFile::new(format!("world0/{name}.vsh"),
            "#version 130\nvoid main() { gl_Position=ftransform(); }\n"));
        files.push(ShaderSourceFile::new(format!("world0/{name}.fsh"),
            format!("#version 130\n/* DRAWBUFFERS:{slot} */\nvoid main() {{ gl_FragData[0]=vec4(1.0); }}\n")));
    }
    let source = ShaderPackSource::new("pre-terrain-order", 1, files).unwrap();
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let mut gal = gal();
    let targets = runtime.stage_complete_source_color_targets(&mut gal, 1,
        Extent3d { width: 16, height: 16, depth: 1 }).unwrap().unwrap();
    let clear = ShaderPackColorClearValues {
        fog_color: ClearColor { r: 0.0, g: 0.0, b: 0.0, a: 1.0 },
    };
    let mut operations = Vec::new();
    let mut cold = runtime.begin_source_color_transaction(&mut gal, &targets, clear, &mut operations).unwrap();
    cold.finish(&mut operations).unwrap();
    gal.submit(SubmissionBatch { label: "cold-bootstrap".into(), command_lists: vec![CommandList::from(CommandListDesc { label: "cold-bootstrap".into(), operations })] }).unwrap();
    cold.confirm(&mut runtime, &mut gal).unwrap();
    let opaque = runtime.prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque).unwrap().unwrap();
    let attachments = runtime.resolve_terrain_source_color_outputs(&opaque, &targets).unwrap();
    let source_frame = frame(Vec::new());
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.apply_world_mesh_asset_update(&mut gal, 1, Vec::new(), vec![WorldMeshTextureAssetPayload {
        texture_id: WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS, png_bytes: material_scene_png(0),
        mip_png_bytes: Vec::new(), frame_width: 0, frame_height: 0, frame_count: 1,
        frame_ticks: 1, animation_flags: 0, frame_row_size: 0, interpolation_policy: 0,
        animation_frames: Vec::new(), coordinate_origin: 0, sampling: None, requested_mip_levels: 0,
    }]).unwrap();
    frontend.shader_runtime = Some(runtime);
    frontend.candidate_source_resource_snapshot = Some(CandidateSourceResourceSnapshot {
        shader_pack_generation: 1, world_generation: 1, frame_id: source_frame.frame_id,
        resources: TerrainSourceOwnedResourceSet::new(TerrainSourceResourceAvailabilitySet::new(1, 1, []).unwrap(), []).unwrap(),
    });
    let consumers = frontend.prepare_pre_terrain_fullscreen_consumers(&mut gal, &source_frame, &targets).unwrap();
    assert_eq!(2, consumers.len());
    let runtime = frontend.shader_runtime.as_mut().unwrap();
    let mut operations = Vec::new();
    let transaction = runtime.begin_source_color_transaction(&mut gal, &targets, clear, &mut operations).unwrap();
    let frame_start_end = operations.len();
    // These sentinels identify recording boundaries only; this test never
    // submits the constructed geometry/shadow passes to the mock backend.
    let shadow_target = Handle::new(HandleKind::RenderTarget, 1000, 1).unwrap();
    let terrain_target = Handle::new(HandleKind::RenderTarget, 1001, 1).unwrap();
    let plan = PreparedNamedSourceTerrainFramePlan {
        terrain: PreparedLoweredSourceTerrainFramePlan { frame_id: source_frame.frame_id, draws: Vec::new(), transaction: None },
        shadow_only_draws: Vec::new(), entity_shadow_draws: Vec::new(),
        entities: None, hands: None, textured_material: None, weather: None,
        clouds: None, lines: None, damaged_block: None, entity_glint: None, hand_glint: None,
        color_targets: targets.clone(),
        shadow_targets: Some(TerrainSourceShadowPassTargets {
            shadow_depth_texture: Handle::NULL, shadow_depth_opaque_texture: Handle::NULL,
            shadow_extent: targets.identity.extent, shadow_depth_view: Handle::NULL,
            shadow_color_texture: Handle::NULL, shadow_color_view: Handle::NULL,
            shadow_light_shaft_texture: Handle::NULL, shadow_light_shaft_view: Handle::NULL,
            shadow_target, shadow_pass: Handle::NULL, initialized: true,
        }),
        main_depth_history: None,
        targets: TerrainSourceColorPassTargets {
            phase: TerrainSourceColorPassPhase::BootstrapAfterInitialization,
            color_attachments: attachments.clone(), clear_values: clear,
            depth_texture: Handle::NULL, depth_view: Handle::NULL, target: terrain_target, pass: Handle::NULL,
        },
        translucent_targets: None, pre_terrain_sky: consumers,
        color_transaction: transaction, bootstrap_operations: Vec::new(),
    };
    let (_, transaction, consumers) = plan.into_submission_parts(runtime, None, &mut operations, false,
        |_, _| Ok(()),
        |transaction, _| {
            let mut resolved = attachments.clone();
            transaction.resolve_terrain_color_clear_policy(&mut resolved)?;
            assert!(resolved.iter().all(|a| !a.clear_each_frame),
                "opaque outputs must be visible to deferred before any feedback snapshots");
            Ok(())
        },
        |_, _| Ok(()),
    ).unwrap();
    let pass_position = |view| operations.iter().position(|op| matches!(op,
        CommandOp::BeginPass { colors, .. } if colors.first().is_some_and(|a|
            a.view == view && a.load_op == AttachmentLoadOp::Load))).unwrap();
    let begin = pass_position(targets.target("auxiliary_h").unwrap().current_attachment_view);
    let prepare = pass_position(targets.target("auxiliary_g").unwrap().current_attachment_view);
    let shadow = operations.iter().position(|op| matches!(op, CommandOp::BeginPass { target, .. } if *target == shadow_target)).unwrap();
    let terrain = operations.iter().position(|op| matches!(op, CommandOp::BeginPass { target, .. } if *target == terrain_target)).unwrap();
    assert!(frame_start_end <= begin && begin < shadow && shadow < prepare && prepare < terrain);
    assert!(operations[..frame_start_end].iter().any(|op| matches!(op,
        CommandOp::BeginPass { colors, .. } if colors.iter().all(|a| a.load_op == AttachmentLoadOp::Clear))));
    destroy_named_source_fullscreen_consumers(&mut gal, consumers);
    transaction.discard(runtime, &mut gal);
    frontend.reset(&mut gal);
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

//! Owned frame preparation, source reload and extent/projection changes.

use super::*;

fn frontend() -> WorldPrimitiveFrontend {
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend
        .apply_world_mesh_asset_update(
            &mut gal(),
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
}

fn update(generation: u64, half: f32, rotation: f32) -> ShaderPackSourceUpdate {
    ShaderPackSourceUpdate { pack_name:"builtin-frame-test".into(),generation,files:vec![ShaderSourceFile::new("gbuffers_terrain.fsh",format!("const float eyeBrightnessHalflife={half};\nconst float sunPathRotation={rotation};\n"))] }
}

fn source_frame() -> WorldPrimitiveFrame {
    let mut frame = frame(Vec::new());
    frame.frame_id = 10;
    frame.voxel_volume = private_voxel_volume_frame([0.25, 0.5, 0.75]);
    frame.shader_environment = WorldShaderEnvironmentFrame {
        enabled: true,
        world_generation: 4,
        frame_time_seconds: 0.3,
        eye_brightness: [0, 240],
        time_of_day: 0.0,
        far_plane: 128.0,
        configured_shadow_distance_chunks: 32,
        ..Default::default()
    };
    frame
}

#[test]
fn builtin_frame_uniforms_refresh_after_source_reload_in_the_same_frame() {
    let mut frontend = frontend();
    frontend
        .apply_shader_pack_source_update(update(1, 3.0, -25.0))
        .unwrap();
    let mut source = source_frame();
    let first = frontend
        .source_uniform_frame_for_owned_resources(&source)
        .unwrap();
    assert_eq!(Some([0, 240]), first.eye_brightness_smooth);
    assert_eq!(Some(-25.0), first.celestial_sun_path_rotation);
    assert!(first.sun_position.is_some() && first.shadow_light_position.is_some());
    source.frame_id = 11;
    source.shader_environment.eye_brightness = [240, 0];
    assert_eq!(
        Some([120, 120]),
        frontend
            .source_uniform_frame_for_owned_resources(&source)
            .unwrap()
            .eye_brightness_smooth
    );
    frontend
        .apply_shader_pack_source_update(update(2, 10.0, 0.0))
        .unwrap();
    let reloaded = frontend
        .source_uniform_frame_for_owned_resources(&source)
        .unwrap();
    assert_eq!(Some([240, 0]), reloaded.eye_brightness_smooth);
    assert_eq!(Some(0.0), reloaded.celestial_sun_path_rotation);
    assert_ne!(first.sun_position, reloaded.sun_position);
}

#[test]
fn builtin_frame_uniforms_refresh_after_extent_and_projection_change() {
    let mut frontend = frontend();
    frontend
        .apply_shader_pack_source_update(update(1, 3.0, -25.0))
        .unwrap();
    let mut source = source_frame();
    frontend
        .source_uniform_frame_for_owned_resources(&source)
        .unwrap();
    source.viewport_width = 640;
    source.viewport_height = 360;
    source.projection_matrix[0] = 2.0;
    let resized = frontend
        .source_uniform_frame_for_owned_resources(&source)
        .unwrap();
    assert_eq!(Some(640.0), resized.viewport_width);
    assert_eq!(Some(360.0), resized.viewport_height);
    assert_eq!(Some(source.projection_matrix), resized.projection_matrix);
}

#[test]
fn builtin_frame_uniforms_refresh_when_shader_environment_enables_in_the_same_frame() {
    let mut frontend = frontend();
    frontend
        .apply_shader_pack_source_update(update(1, 0.3, -25.0))
        .unwrap();
    let mut source = source_frame();
    let lightmap = frame(Vec::new()).shader_environment.vanilla_lightmap;
    let mut enabled_environment = source.shader_environment.clone();
    enabled_environment.frame_time_seconds = 0.0;
    enabled_environment.vanilla_lightmap = lightmap;
    // Keep world/frame/time and every other pre-existing memo-key field
    // unchanged across a valid lightmap-only -> shader-enabled transition.
    source.shader_environment = WorldShaderEnvironmentFrame {
        world_generation: enabled_environment.world_generation,
        vanilla_lightmap: lightmap,
        ..Default::default()
    };
    assert_eq!(
        None,
        frontend
            .source_uniform_frame_for_owned_resources(&source)
            .unwrap()
            .eye_brightness_smooth
    );
    source.shader_environment = enabled_environment;
    assert_eq!(
        Some([0, 240]),
        frontend
            .source_uniform_frame_for_owned_resources(&source)
            .unwrap()
            .eye_brightness_smooth
    );
}

#[test]
fn builtin_frame_uniforms_reach_entity_and_hand_writers_without_atlas_or_render_stage_reads() {
    let mut frontend = frontend();
    frontend
        .apply_shader_pack_source_update(update(1, 0.3, -25.0))
        .unwrap();
    let mut asset = mesh_asset(0xb117, 1, IndexType::U16);
    asset.entity_identity = "minecraft:boat".into();
    frontend
        .apply_world_mesh_asset_update(&mut gal(), 2, vec![asset], Vec::new())
        .unwrap();
    let mut frame = source_frame();
    let mut instance = mesh_instance(0xb117, 1);
    instance.stratum = WORLD_STRATUM_ENTITY_MESH;
    frame.mesh_instances = vec![instance.clone()];
    frame.first_person_mesh_instances = vec![instance];
    frame.first_person.enabled = true;
    frame.first_person.clear_depth_before = true;
    frame.first_person.main_hand_instance_count = 1;
    frame.first_person.model_view_matrix = frame.view_matrix;
    frame.first_person.projection_matrix = frame.projection_matrix;

    let source = ShaderPackSource::new(
        "draw-builtins",
        1,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_entities.vsh",
                "#version 130\nvoid main() { gl_Position=ftransform(); }\n",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_hand.vsh",
                "#version 130\nvoid main() { gl_Position=ftransform(); }\n",
            ),
            ShaderSourceFile::new("world0/gbuffers_entities.fsh", BUILTIN_DRAW_FRAGMENT),
            ShaderSourceFile::new("world0/gbuffers_hand.fsh", BUILTIN_DRAW_FRAGMENT),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let entity_contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let entity_lowered = lower_entity_source_pair(&source, &entity_contract).unwrap();
    let entity_bindings = bind_entity_source_resources(&entity_lowered, &declarations).unwrap();
    let entity =
        prepare_lowered_entity_source_program(&entity_contract, &entity_lowered, &entity_bindings)
            .unwrap();
    let hand_contract = derive_hand_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let hand_lowered = lower_hand_source_pair(&source, &hand_contract).unwrap();
    let hand_bindings = bind_hand_source_resources(&hand_lowered, &declarations).unwrap();
    let hand =
        prepare_lowered_hand_source_program(&hand_contract, &hand_lowered, &hand_bindings).unwrap();
    let entities = frontend
        .prepare_source_entity_frames(&entity, &frame)
        .unwrap();
    let hands = frontend.prepare_source_hand_frames(&hand, &frame).unwrap();
    assert_eq!(1, entities.len());
    assert_eq!(1, hands.len());
    for (requirements, bytes) in [
        (
            &entity.scalar_uniform_requirements,
            &entities[0].scalar_uniforms,
        ),
        (&hand.scalar_uniform_requirements, &hands[0].scalar_uniforms),
    ] {
        assert!(!requirements.fields().iter().any(|r| matches!(
            r.semantic,
            Some(
                TerrainSourceUniformSemantic::MaterialAtlasSize
                    | TerrainSourceUniformSemantic::RenderStage
            )
        )));
        let offset = requirements
            .fields()
            .iter()
            .find(|r| r.semantic == Some(TerrainSourceUniformSemantic::EyeBrightnessSmooth))
            .unwrap()
            .field
            .offset() as usize;
        assert_eq!(
            [0, 240],
            [
                i32::from_ne_bytes(bytes[offset..offset + 4].try_into().unwrap()),
                i32::from_ne_bytes(bytes[offset + 4..offset + 8].try_into().unwrap())
            ]
        );
        let offset = requirements
            .fields()
            .iter()
            .find(|r| r.semantic == Some(TerrainSourceUniformSemantic::SunPosition))
            .unwrap()
            .field
            .offset() as usize;
        let expected = frontend
            .source_uniform_frame_for_owned_resources(&frame)
            .unwrap()
            .sun_position
            .unwrap();
        for channel in 0..3 {
            assert_eq!(expected[channel], read_f32(bytes, offset / 4 + channel));
        }
    }
}

const BUILTIN_DRAW_FRAGMENT: &str = "#version 130\n/* DRAWBUFFERS:0 */\nuniform sampler2D tex;\nuniform ivec2 eyeBrightnessSmooth;\nuniform vec3 sunPosition;\nvoid main() { gl_FragData[0] = texture2D(tex,vec2(0.0)) + vec4(vec2(eyeBrightnessSmooth),sunPosition.x,0.0); }\n";

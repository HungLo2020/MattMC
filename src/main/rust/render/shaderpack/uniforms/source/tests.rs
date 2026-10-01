use crate::render::shaderpack::uniforms::source::*;
use crate::render::shaderpack::lowering::lower_terrain_source_pair;
use crate::render::shaderpack::source::preprocess::preprocess_terrain_sources;
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
use crate::render::shaderpack::contracts::terrain::derive_complementary_terrain_contract;

fn source_requirements(
    vertex_uniform: &str,
    vertex_use: &str,
    fragment_uniform: &str,
    fragment_use: &str,
) -> GalResult<TerrainSourceUniformRequirements> {
    let source = ShaderPackSource::new(
        "uniform-requirements",
        1,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                format!(
                    "#version 130\n{vertex_uniform}\nuniform sampler2D tex;\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nvoid main() {{ {vertex_use} texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }}"
                ),
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                format!(
                    "#version 130\n{fragment_uniform}\nuniform sampler2D tex;\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nvoid DoLighting() {{}}\n/* DRAWBUFFERS:06 */\nvoid main() {{ {fragment_use} vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }}"
                ),
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(
                "mattmc/terrain-resource-bindings.properties",
                "tex=material_atlas\n",
            ),
        ],
    )?;
    let contract = derive_complementary_terrain_contract(&source)?;
    let artifacts = preprocess_terrain_sources(&source, &contract.source_stages()?)?;
    let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment)?;
    TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())
}

#[test]
fn maps_known_source_uniforms_without_an_untyped_payload() {
    let requirements = source_requirements(
        "uniform mat4 gbufferModelView;\nuniform int worldTime;",
        "float source_world_time = float(worldTime);",
        "uniform float rainStrength;",
        "float source_rain_strength = rainStrength;",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    assert_eq!(4, requirements.fields().len());
    assert_eq!(
        Some(TerrainSourceUniformSemantic::ViewMatrix),
        requirements.fields()[0].semantic
    );
    assert_eq!(
        Some(TerrainSourceUniformSemantic::RainStrength),
        requirements.fields()[2].semantic
    );
    assert_eq!(
        Some(TerrainSourceUniformSemantic::WorldTime),
        requirements.fields()[3].semantic
    );
    assert_eq!(
        Some(TerrainSourceUniformSemantic::ProjectionMatrix),
        requirements.fields()[1].semantic
    );
}

#[test]
fn maps_and_packs_distant_horizons_render_distance_as_a_gameplay_semantic() {
    let requirements = source_requirements(
        "uniform int dhRenderDistance;",
        "float source_dh_distance = float(dhRenderDistance);",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    assert_eq!(
        Some(TerrainSourceUniformSemantic::DistantHorizonsRenderDistance),
        requirements.fields()[0].semantic
    );
    let bytes = TerrainSourceUniformFrame {
        distant_horizons_render_distance: Some(384),
        view_matrix: Some([1.0; 16]),
        view_matrix_inverse: Some([1.0; 16]),
        projection_matrix: Some([1.0; 16]),
        projection_matrix_inverse: Some([1.0; 16]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(384_i32.to_le_bytes(), bytes[0..4]);
}

#[test]
fn packs_source_derived_shadow_matrices_as_named_semantics() {
    let requirements = source_requirements(
        "uniform mat4 shadowModelView;\nuniform mat4 shadowModelViewInverse;",
        "vec4 source_shadow = shadowModelView * shadowModelViewInverse[0];",
        "uniform mat4 shadowProjection;\nuniform mat4 shadowProjectionInverse;",
        "vec4 source_shadow_projection = shadowProjection * shadowProjectionInverse[0];",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    let frame = TerrainSourceUniformFrame {
        view_matrix: Some(identity),
        view_matrix_inverse: Some(identity),
        projection_matrix: Some(identity),
        projection_matrix_inverse: Some(identity),
        shadow_model_view: Some(identity),
        shadow_model_view_inverse: Some(identity),
        shadow_projection: Some(identity),
        shadow_projection_inverse: Some(identity),
        ..TerrainSourceUniformFrame::default()
    };
    assert!(frame.pack_std140(&requirements).is_ok());
}

#[test]
fn maps_camera_environment_semantics_without_renderer_state() {
    let requirements = source_requirements(
        "uniform int isEyeInWater;\nuniform float screenBrightness;\nuniform float darknessLightFactor;\nuniform float nightVision;\nuniform float inDry;\nuniform float inSnowy;\nuniform float inNetherWastes;\nuniform float inCrimsonForest;\nuniform float inWarpedForest;\nuniform float inBasaltDeltas;\nuniform float inSoulValley;",
        "float source_submersion = float(isEyeInWater) + screenBrightness + darknessLightFactor + nightVision + inDry + inSnowy + inNetherWastes + inCrimsonForest + inWarpedForest + inBasaltDeltas + inSoulValley;",
        "uniform vec3 fogColor;\nuniform vec3 skyColor;",
        "vec3 source_fog = fogColor; vec3 source_sky = skyColor;",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::EyeSubmersion)));
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::ScreenBrightness)));
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::DarknessLightFactor)));
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::NightVision)));
    assert!(requirements.fields().iter().any(
        |requirement| requirement.semantic == Some(TerrainSourceUniformSemantic::BiomeDry)
    ));
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::BiomeNetherWastes)));
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::BiomeSoulValley)));
    assert!(requirements
        .fields()
        .iter()
        .any(|requirement| requirement.semantic
            == Some(TerrainSourceUniformSemantic::BiomeSnowy)));
    assert!(requirements.fields().iter().any(
        |requirement| requirement.semantic == Some(TerrainSourceUniformSemantic::FogColor)
    ));
    assert!(requirements.fields().iter().any(
        |requirement| requirement.semantic == Some(TerrainSourceUniformSemantic::SkyColor)
    ));
}

#[test]
fn packs_temporal_camera_and_visibility_semantics_without_iris_state() {
    let requirements = source_requirements(
        "uniform float aspectRatio;\nuniform float blindness;\nuniform float darknessFactor;\nuniform float maxBlindnessDarkness;\nuniform float near;\nuniform ivec2 eyeBrightness;\nuniform float eyeBrightnessM;\nuniform float eyeBrightnessM2;\nuniform vec3 previousCameraPosition;\nuniform mat4 gbufferPreviousModelView;",
        "float source_visibility = aspectRatio + blindness + darknessFactor + maxBlindnessDarkness + near + eyeBrightnessM + eyeBrightnessM2;\nvec3 source_previous_camera = previousCameraPosition;\nvec4 source_previous_view = gbufferPreviousModelView[0];",
        "uniform mat4 gbufferPreviousProjection;",
        "vec4 source_previous_projection = gbufferPreviousProjection[0];",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    let frame = TerrainSourceUniformFrame {
        aspect_ratio: Some(16.0 / 9.0),
        blindness: Some(0.25),
        darkness_factor: Some(0.5),
        max_blindness_darkness: Some(0.5),
        near_plane: Some(0.05),
        eye_brightness: Some([80, 240]),
        eye_brightness_m: Some(0.75),
        eye_brightness_m2: Some(1.0),
        previous_camera_world_position: Some([1.0, 2.0, 3.0]),
        view_matrix: Some(identity),
        view_matrix_inverse: Some(identity),
        projection_matrix: Some(identity),
        projection_matrix_inverse: Some(identity),
        previous_view_matrix: Some(identity),
        previous_projection_matrix: Some(identity),
        ..TerrainSourceUniformFrame::default()
    };
    assert!(frame.pack_std140(&requirements).is_ok());
    let mut dark_frame = frame;
    dark_frame.eye_brightness = Some([0, 0]);
    assert!(dark_frame.pack_std140(&requirements).is_ok());
}

#[test]
fn packs_rust_resolved_main_and_off_hand_item_ids() {
    let requirements = source_requirements(
        "uniform int heldItemId;\nuniform int heldItemId2;",
        "float source_held_items = float(heldItemId + heldItemId2);",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let main_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::HeldItemIdMain))
        .expect("heldItemId must retain its semantic")
        .field
        .offset() as usize;
    let off_hand_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::HeldItemIdOffHand))
        .expect("heldItemId2 must retain its semantic")
        .field
        .offset() as usize;
    let frame = TerrainSourceUniformFrame {
        held_item_id_main: Some(45_032),
        held_item_id_off_hand: Some(-1),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    };
    let bytes = frame.pack_std140(&requirements).unwrap();
    assert_eq!(
        45_032_i32.to_le_bytes(),
        bytes[main_offset..main_offset + 4]
    );
    assert_eq!(
        (-1_i32).to_le_bytes(),
        bytes[off_hand_offset..off_hand_offset + 4]
    );
}

#[test]
fn packs_explicit_non_entity_sentinels_for_static_terrain() {
    let requirements = source_requirements(
        "uniform int entityId;\nuniform int blockEntityId;",
        "float source_non_entity = float(entityId + blockEntityId);",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let entity_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::EntityId))
        .expect("entityId must retain its semantic")
        .field
        .offset() as usize;
    let block_entity_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::BlockEntityId))
        .expect("blockEntityId must retain its semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        entity_id: Some(-1),
        block_entity_id: Some(-1),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(
        (-1_i32).to_le_bytes(),
        bytes[entity_offset..entity_offset + 4]
    );
    assert_eq!(
        (-1_i32).to_le_bytes(),
        bytes[block_entity_offset..block_entity_offset + 4]
    );
}

#[test]
fn packs_entity_identity_and_color_from_typed_semantics() {
    let requirements = source_requirements(
        "uniform int entityId;\nuniform vec4 entityColor;",
        "vec4 source_entity = entityColor + vec4(float(entityId));",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let entity_id_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::EntityId))
        .expect("entityId must retain its semantic")
        .field
        .offset() as usize;
    let color_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::EntityColor))
        .expect("entityColor must retain its semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        entity_id: Some(50_076),
        entity_color: Some([0.25, 0.5, 0.75, 1.0]),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(
        50_076_i32.to_le_bytes(),
        bytes[entity_id_offset..entity_id_offset + 4]
    );
    assert_eq!(
        0.25_f32.to_le_bytes(),
        bytes[color_offset..color_offset + 4]
    );
    assert_eq!(
        1.0_f32.to_le_bytes(),
        bytes[color_offset + 12..color_offset + 16]
    );

    let missing_color = TerrainSourceUniformFrame {
        entity_id: Some(50_076),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap_err();
    assert!(missing_color
        .to_string()
        .contains("current rendered entity color"));
}

#[test]
fn packs_pack_composed_main_and_off_hand_block_light() {
    let requirements = source_requirements(
        "uniform int heldBlockLightValue;\nuniform int heldBlockLightValue2;",
        "float source_held_light = float(heldBlockLightValue + heldBlockLightValue2);",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let main_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::HeldBlockLightMain))
        .expect("heldBlockLightValue must retain its semantic")
        .field
        .offset() as usize;
    let off_hand_offset = requirements
        .fields()
        .iter()
        .find(|field| {
            field.semantic == Some(TerrainSourceUniformSemantic::HeldBlockLightOffHand)
        })
        .expect("heldBlockLightValue2 must retain its semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        held_block_light_main: Some(12),
        held_block_light_off_hand: Some(7),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(12_i32.to_le_bytes(), bytes[main_offset..main_offset + 4]);
    assert_eq!(
        7_i32.to_le_bytes(),
        bytes[off_hand_offset..off_hand_offset + 4]
    );
}

#[test]
fn unknown_or_mistyped_source_uniforms_remain_explicit_blockers() {
    let requirements = source_requirements(
        "uniform float packSpecificValue;",
        "float source_pack_specific = packSpecificValue;",
        "",
        "",
    )
    .unwrap();
    assert!(!requirements.is_fully_semantic());
    assert_eq!(
        vec!["packSpecificValue"],
        requirements
            .unresolved_fields()
            .map(|field| field.name())
            .collect::<Vec<_>>()
    );
    assert!(requirements
        .require_fully_semantic()
        .unwrap_err()
        .to_string()
        .contains("packSpecificValue"));
    assert_eq!(
        TerrainSourceUniformRequirementSummary {
            field_count: 3,
            resolved_field_count: 2,
            unresolved_field_names: vec!["packSpecificValue".to_string()],
        },
        requirements.summary()
    );
    assert!(source_requirements(
        "uniform float worldTime;",
        "float source_world_time = worldTime;",
        "",
        "",
    )
    .is_err());
}

#[test]
fn packs_recognized_semantics_at_the_source_derived_std140_offsets() {
    let requirements = source_requirements(
        "uniform mat4 gbufferModelView;\nuniform int worldTime;",
        "float source_world_time = float(worldTime);",
        "uniform float rainStrength;",
        "float source_rain_strength = rainStrength;",
    )
    .unwrap();
    let mut view = [0.0_f32; 16];
    view[0] = 2.0;
    view[5] = 3.0;
    view[10] = 4.0;
    view[15] = 1.0;
    let mut projection = [0.0_f32; 16];
    projection[0] = 5.0;
    projection[5] = 6.0;
    projection[10] = 7.0;
    projection[15] = 1.0;
    let bytes = TerrainSourceUniformFrame {
        view_matrix: Some(view),
        projection_matrix: Some(projection),
        rain_strength: Some(0.25),
        world_time: Some(1234),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();

    assert_eq!(144, bytes.len());
    assert_eq!(2.0_f32.to_le_bytes(), bytes[0..4]);
    assert_eq!(3.0_f32.to_le_bytes(), bytes[20..24]);
    assert_eq!(5.0_f32.to_le_bytes(), bytes[64..68]);
    assert_eq!(6.0_f32.to_le_bytes(), bytes[84..88]);
    assert_eq!(0.25_f32.to_le_bytes(), bytes[128..132]);
    assert_eq!(1234_i32.to_le_bytes(), bytes[132..136]);
}

#[test]
fn packs_rust_owned_material_atlas_extent_as_ivec2() {
    let requirements = source_requirements(
        "uniform ivec2 atlasSize;",
        "ivec2 source_atlas_size = atlasSize;",
        "",
        "",
    )
    .unwrap();
    assert_eq!(
        Some(TerrainSourceUniformSemantic::MaterialAtlasSize),
        requirements.fields()[0].semantic
    );
    let bytes = TerrainSourceUniformFrame {
        material_atlas_size: Some([1024, 512]),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    let atlas = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::MaterialAtlasSize))
        .unwrap()
        .field
        .offset() as usize;
    assert_eq!(1024_i32.to_le_bytes(), bytes[atlas..atlas + 4]);
    assert_eq!(512_i32.to_le_bytes(), bytes[atlas + 4..atlas + 8]);
    let required_transforms = TerrainSourceUniformFrame {
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    };
    assert!(required_transforms
        .clone()
        .pack_std140(&requirements)
        .unwrap_err()
        .to_string()
        .contains("material atlas size"));
    assert!(TerrainSourceUniformFrame {
        material_atlas_size: Some([0, 512]),
        ..required_transforms
    }
    .pack_std140(&requirements)
    .is_err());
}

#[test]
fn packs_vanilla_night_vision_as_a_named_source_semantic() {
    let requirements = source_requirements(
        "uniform float nightVision;",
        "float source_night_vision = nightVision;",
        "",
        "",
    )
    .unwrap();
    let night_vision_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::NightVision))
        .expect("nightVision must retain its named semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        night_vision: Some(0.875),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(
        0.875_f32.to_le_bytes(),
        bytes[night_vision_offset..night_vision_offset + 4]
    );
    assert!(TerrainSourceUniformFrame {
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap_err()
    .to_string()
    .contains("night vision"));
}

#[test]
fn packs_rust_owned_temporal_rain_factor_as_a_named_source_semantic() {
    let requirements = source_requirements(
        "uniform float rainFactor;",
        "float source_rain_factor = rainFactor;",
        "",
        "",
    )
    .unwrap();
    let rain_factor_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::RainFactor))
        .expect("rainFactor must retain its named semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        rain_factor: Some(0.625),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(
        0.625_f32.to_le_bytes(),
        bytes[rain_factor_offset..rain_factor_offset + 4]
    );
    assert!(TerrainSourceUniformFrame {
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap_err()
    .to_string()
    .contains("rain factor"));
}

#[test]
fn packs_vanilla_fog_color_as_a_named_source_semantic() {
    let requirements = source_requirements(
        "uniform vec3 fogColor;",
        "vec3 source_fog_color = fogColor;",
        "",
        "",
    )
    .unwrap();
    let fog_color_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::FogColor))
        .expect("fogColor must retain its named semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        fog_color: Some([0.25, 0.5, 0.75]),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(
        0.25_f32.to_le_bytes(),
        bytes[fog_color_offset..fog_color_offset + 4]
    );
    assert_eq!(
        0.5_f32.to_le_bytes(),
        bytes[fog_color_offset + 4..fog_color_offset + 8]
    );
    assert_eq!(
        0.75_f32.to_le_bytes(),
        bytes[fog_color_offset + 8..fog_color_offset + 12]
    );
}

#[test]
fn packs_source_smoothed_biome_climate_as_named_semantics() {
    let requirements = source_requirements(
        "uniform float inDry;\nuniform float inSnowy;",
        "float climate = inDry + inSnowy;",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let dry_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::BiomeDry))
        .expect("inDry must retain its named semantic")
        .field
        .offset() as usize;
    let snowy_offset = requirements
        .fields()
        .iter()
        .find(|field| field.semantic == Some(TerrainSourceUniformSemantic::BiomeSnowy))
        .expect("inSnowy must retain its named semantic")
        .field
        .offset() as usize;
    let bytes = TerrainSourceUniformFrame {
        biome_dry: Some(0.25),
        biome_snowy: Some(0.75),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    assert_eq!(0.25_f32.to_le_bytes(), bytes[dry_offset..dry_offset + 4]);
    assert_eq!(
        0.75_f32.to_le_bytes(),
        bytes[snowy_offset..snowy_offset + 4]
    );
}

#[test]
fn packs_source_smoothed_nether_biomes_as_named_semantics() {
    let requirements = source_requirements(
			"uniform float inNetherWastes;\nuniform float inCrimsonForest;\nuniform float inWarpedForest;\nuniform float inBasaltDeltas;\nuniform float inSoulValley;",
			"float nether = inNetherWastes + inCrimsonForest + inWarpedForest + inBasaltDeltas + inSoulValley;",
			"",
			"",
		)
		.unwrap();
    assert!(requirements.is_fully_semantic());
    let bytes = TerrainSourceUniformFrame {
        biome_nether_wastes: Some(0.1),
        biome_crimson_forest: Some(0.2),
        biome_warped_forest: Some(0.3),
        biome_basalt_deltas: Some(0.4),
        biome_soul_valley: Some(0.5),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    for (semantic, expected) in [
        (TerrainSourceUniformSemantic::BiomeNetherWastes, 0.1_f32),
        (TerrainSourceUniformSemantic::BiomeCrimsonForest, 0.2_f32),
        (TerrainSourceUniformSemantic::BiomeWarpedForest, 0.3_f32),
        (TerrainSourceUniformSemantic::BiomeBasaltDeltas, 0.4_f32),
        (TerrainSourceUniformSemantic::BiomeSoulValley, 0.5_f32),
    ] {
        let offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(semantic))
            .expect("named Nether semantic must retain its source field")
            .field
            .offset() as usize;
        assert_eq!(expected.to_le_bytes(), bytes[offset..offset + 4]);
    }
}

#[test]
fn packs_selected_pack_wetness_and_biome_environment_without_untyped_defaults() {
    let requirements = source_requirements(
        "uniform float inPaleGarden;\nuniform float inRainy;\nuniform float wetness;",
        "float environment = inPaleGarden + inRainy + wetness;",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let bytes = TerrainSourceUniformFrame {
        biome_pale_garden: Some(0.25),
        biome_rainy: Some(0.5),
        wetness: Some(0.75),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    for (semantic, expected) in [
        (TerrainSourceUniformSemantic::BiomePaleGarden, 0.25_f32),
        (TerrainSourceUniformSemantic::BiomeRainy, 0.5_f32),
        (TerrainSourceUniformSemantic::Wetness, 0.75_f32),
    ] {
        let offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(semantic))
            .expect("named environment semantic must retain its source field")
            .field
            .offset() as usize;
        assert_eq!(expected.to_le_bytes(), bytes[offset..offset + 4]);
    }
}

#[test]
fn packs_source_frame_time_smooth_and_camera_velocity_as_named_semantics() {
    let requirements = source_requirements(
        "uniform float frameTimeSmooth;\nuniform float velocity;",
        "float temporal = frameTimeSmooth + velocity;",
        "",
        "",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let bytes = TerrainSourceUniformFrame {
        frame_time_smooth: Some(0.125),
        camera_velocity: Some(0.75),
        view_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        projection_matrix: Some([
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .unwrap();
    for (semantic, expected) in [
        (TerrainSourceUniformSemantic::FrameTimeSmooth, 0.125_f32),
        (TerrainSourceUniformSemantic::CameraVelocity, 0.75_f32),
    ] {
        let offset = requirements
            .fields()
            .iter()
            .find(|field| field.semantic == Some(semantic))
            .expect("named temporal semantic must retain its source field")
            .field
            .offset() as usize;
        assert_eq!(expected.to_le_bytes(), bytes[offset..offset + 4]);
    }
}

#[test]
fn packing_rejects_missing_or_non_finite_semantic_values() {
    let requirements = source_requirements(
        "",
        "",
        "uniform float rainStrength;",
        "float source_rain_strength = rainStrength;",
    )
    .unwrap();
    let transforms = TerrainSourceUniformFrame {
        view_matrix: Some([1.0; 16]),
        projection_matrix: Some([1.0; 16]),
        ..TerrainSourceUniformFrame::default()
    };
    assert!(transforms
        .pack_std140(&requirements)
        .unwrap_err()
        .to_string()
        .contains("rain strength"));
    assert!(TerrainSourceUniformFrame {
        view_matrix: Some([1.0; 16]),
        projection_matrix: Some([1.0; 16]),
        rain_strength: Some(f32::NAN),
        ..TerrainSourceUniformFrame::default()
    }
    .pack_std140(&requirements)
    .is_err());
}

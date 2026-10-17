use crate::render::shaderpack::source::{RUNTIME_BLOCK_STATE_IDENTITIES_PATH, RUNTIME_OPTIONS_PATH};
use crate::render::shaderpack::contracts::terrain::*;

#[test]
fn single_color_terrain_keeps_its_selected_slot_and_original_lighting() {
    let source = ShaderPackSource::new("single-color", 3, vec![
        ShaderSourceFile::new("world0/gbuffers_terrain.vsh", concat!(
            "#version 120\nvarying vec2 texcoord;\nvarying vec4 tintColor;\n",
            "void main() { gl_Position = ftransform(); texcoord = gl_MultiTexCoord0.xy; tintColor = gl_Color; }\n"
        )),
        ShaderSourceFile::new("world0/gbuffers_terrain.fsh", concat!(
            "#version 120\nuniform sampler2D tex;\nvarying vec2 texcoord;\nvarying vec4 tintColor;\n",
            "/* DRAWBUFFERS:1 */\nvoid main() { vec4 pixel = texture2D(tex, texcoord) * tintColor; ",
            "pixel.rgb *= pow(vec3(0.8), vec3(2.0)); gl_FragData[0] = pixel; }\n"
        )),
        ShaderSourceFile::new("block.properties", "block.10201=minecraft:grass_block\n"),
    ]).unwrap();
    let contract = derive_terrain_contract_for_scope(&source, TerrainProgramScope::Overworld).unwrap();
    assert_eq!(contract.outputs, std::collections::BTreeSet::from([TerrainPassOutput::LitTerrainColor]));
    assert_eq!(contract.output_color_slot(TerrainPassOutput::LitTerrainColor), Some(1));
    assert_eq!(contract.material_ids[&10201], ["minecraft:grass_block"]);
    assert!(contract.required_resources.is_empty());
    let stages = contract.source_stages().unwrap();
    let artifacts = crate::render::shaderpack::source::preprocess::preprocess_terrain_sources(&source, &stages).unwrap();
    let lowered = crate::render::shaderpack::lowering::lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    assert!(lowered.fragment().source().contains("pow(vec3(0.8), vec3(2.0))"));
    assert!(!lowered.fragment().source().contains("DoLighting("));
}

#[test]
fn single_color_discovery_requires_a_complete_paired_source() {
    let source = ShaderPackSource::new("fragment-only", 1, vec![
        ShaderSourceFile::new("gbuffers_terrain.fsh",
            "#version 120\n/* DRAWBUFFERS:1 */\nvoid main() { gl_FragData[0] = vec4(1.0); }"),
    ]).unwrap();
    let error = derive_terrain_contract_for_scope(&source, TerrainProgramScope::Default).unwrap_err();
    assert!(error.to_string().contains("missing shader source"));
}

fn source() -> ShaderPackSource {
    ShaderPackSource::new("test", 1, vec![
        ShaderSourceFile::new("program/gbuffers_terrain.glsl", "#include \"/lib/common.glsl\"\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
        ShaderSourceFile::new("lib/common.glsl", "#define SHADOW_QUALITY 2\n"),
        ShaderSourceFile::new("shaders.properties", "profile.MATTMC=SHADOW_QUALITY=2\n"),
        ShaderSourceFile::new("block.properties", "block.10009=minecraft:oak_leaves\n"),
    ]).unwrap()
}

fn terrain_fragment_source() -> &'static str {
    "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"
}

fn translucent_fragment_source() -> &'static str {
    "void DoLighting() {}\nvoid DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n/* DRAWBUFFERS:03 */\nvoid main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0); vec3 viewPos = vec3(0.0); vec3 playerPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0); DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }"
}

#[test]
fn runtime_block_state_snapshot_resolves_pack_materials_in_rule_order() {
    let mut files = source().files();
    files.retain(|file| file.path != "block.properties");
    files.push(ShaderSourceFile::new(
        "block.properties",
        "block.7=sand\nblock.9=sand:color=red\nblock.11=oak_leaves:persistent=true\n",
    ));
    files.push(ShaderSourceFile::new(
        RUNTIME_BLOCK_STATE_IDENTITIES_PATH,
        concat!(
            "state.41=minecraft:sand\n",
            "state.42=minecraft:sand|color=red\n",
            "state.43=minecraft:oak_leaves|persistent=true\n",
            "state.44=minecraft:stone\n",
        ),
    ));
    let source = ShaderPackSource::new("state-map", 1, files).unwrap();

    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let resolved = contract.runtime_block_state_material_ids.unwrap();

    // Pack order is authoritative, matching Iris's first successful rule.
    assert_eq!(Some(&7), resolved.get(&41));
    assert_eq!(Some(&7), resolved.get(&42));
    assert_eq!(Some(&11), resolved.get(&43));
    assert_eq!(Some(&-1), resolved.get(&44));
}

#[test]
fn runtime_block_state_snapshot_rejects_duplicate_or_malformed_semantics() {
    let error = parse_runtime_block_state_identities(
        "state.4=minecraft:sand\nstate.4=minecraft:stone\n",
    )
    .unwrap_err();
    assert!(error.to_string().contains("repeats state 4"));

    let error = parse_runtime_block_state_identities("state.x=minecraft:sand\n").unwrap_err();
    assert!(error.to_string().contains("invalid state key"));
}

#[test]
fn semantic_block_state_identity_reuses_pack_material_rules_for_distant_horizons() {
    let mut files = source().files();
    files.retain(|file| file.path != "block.properties");
    files.push(ShaderSourceFile::new(
        "block.properties",
        "block.7=grass_block:snowy=false\nblock.9=redstone_ore\n",
    ));
    let contract = derive_complementary_terrain_contract(
        &ShaderPackSource::new("dh-state-map", 1, files).unwrap(),
    )
    .unwrap();

    assert_eq!(
        contract
            .material_id_for_block_state_identity("minecraft:grass_block_STATE_{snowy:false}")
            .unwrap(),
        7
    );
    assert_eq!(
        contract
            .material_id_for_block_state_identity("minecraft:redstone_ore_STATE_")
            .unwrap(),
        9
    );
    assert_eq!(
        contract
            .material_id_for_block_state_identity("minecraft:stone_STATE_{}")
            .unwrap(),
        -1
    );

    let piston = parse_semantic_block_state_identity(
        "minecraft:piston_STATE_{facing:east}{extended:true}",
    )
    .unwrap();
    assert_eq!(piston.properties["facing"], "east");
    assert_eq!(piston.properties["extended"], "true");

    let trial_spawner = parse_semantic_block_state_identity(
        "minecraft:trial_spawner_STATE_{trial_spawner_state:WAITING_FOR_PLAYERS}",
    )
    .unwrap();
    assert_eq!(
        trial_spawner.properties["trial_spawner_state"],
        "waiting_for_players"
    );
}

#[test]
fn semantic_block_state_identity_rejects_malformed_distant_horizons_properties() {
    let error =
        parse_semantic_block_state_identity("minecraft:grass_block_STATE_{snowy}").unwrap_err();
    assert!(error.to_string().contains("malformed"));

    let error = parse_semantic_block_state_identity(
        "minecraft:grass_block_STATE_{snowy:false}{snowy:true}",
    )
    .unwrap_err();
    assert!(error.to_string().contains("repeats property"));
}

#[test]
fn source_derived_contract_names_complementary_outputs_not_draw_buffers() {
    let contract = derive_complementary_terrain_contract(&source()).unwrap();
    assert_eq!(TerrainSourcePassKind::OpaqueCutout, contract.pass_kind);
    assert!(contract
        .outputs
        .contains(&TerrainPassOutput::LitTerrainColor));
    assert!(contract
        .outputs
        .contains(&TerrainPassOutput::MaterialAuxiliary));
    assert_eq!(
        Some(0),
        contract.output_color_slot(TerrainPassOutput::LitTerrainColor)
    );
    assert_eq!(
        Some(6),
        contract.output_color_slot(TerrainPassOutput::MaterialAuxiliary)
    );
    assert_eq!(
        None,
        contract.output_color_slot(TerrainPassOutput::ViewSpaceNormal)
    );
    assert_eq!(
        vec![
            TerrainPassOperation::AtlasSample,
            TerrainPassOperation::AlphaDiscard,
            TerrainPassOperation::TintMultiply,
            TerrainPassOperation::TerrainLighting,
            TerrainPassOperation::LitColorOutput,
            TerrainPassOperation::MaterialAuxiliaryOutput,
        ],
        contract.operations
    );
    assert_eq!(
        Some(&vec!["minecraft:oak_leaves".to_string()]),
        contract.material_ids.get(&10009)
    );
    assert!(!contract.inputs.contains(&TerrainPassInput::ShadowMap));
}

#[test]
fn translucent_contract_is_a_distinct_source_stage_with_named_outputs() {
    let source = ShaderPackSource::new(
        "translucent",
        4,
        vec![
            ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", "block.32000=minecraft:water\n"),
        ],
    )
    .unwrap();
    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    assert_eq!(TerrainSourcePassKind::Translucent, contract.pass_kind);
    assert_eq!("gbuffers_water.fsh", contract.program_path);
    assert_eq!(
        BTreeSet::from([TerrainMaterialClass::Translucent]),
        contract.material_classes
    );
    assert_eq!(
        Some(0),
        contract.output_color_slot(TerrainPassOutput::LitTerrainColor)
    );
    assert_eq!(
        Some(3),
        contract.output_color_slot(TerrainPassOutput::TranslucencyAuxiliary)
    );
    assert!(contract.inputs.contains(&TerrainPassInput::MainDepth));
    assert!(contract.inputs.contains(&TerrainPassInput::ViewDirection));
    assert!(!contract.inputs.contains(&TerrainPassInput::SceneColor));
}

#[test]
fn translucent_contract_derives_only_explicit_source_alpha_raster_state() {
    let source = ShaderPackSource::new(
        "translucent-raster",
        5,
        vec![
            ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
            ShaderSourceFile::new(
                "shaders.properties",
                "alphaTest.gbuffers_water=GREATER 0.0001\nblend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n",
            ),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();

    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    let raster = contract
        .translucent_raster_state
        .expect("explicit source raster directives must survive contract derivation");
    assert_eq!(TerrainTranslucentBlend::SourceAlphaOver, raster.blend);
    assert!((raster.alpha_test.unwrap().greater_than() - 0.0001).abs() < f32::EPSILON);
}

#[test]
fn translucent_contract_defers_custom_pbr_admission_to_semantic_resources() {
    let source = ShaderPackSource::new(
        "translucent-custom-pbr",
        8,
        vec![
            ShaderSourceFile::new(
                "gbuffers_water.fsh",
                concat!(
                    "#ifdef CUSTOM_PBR\n",
                    "uniform sampler2D normals;\n",
                    "uniform sampler2D specular;\n",
                    "#endif\n",
                    "void DoLighting() {}\n",
                    "void DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n",
                    "/* DRAWBUFFERS:03 */\n",
                    "void main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0);\n",
                    "#ifdef CUSTOM_PBR\ncolor.rgb += texture2D(normals, texCoord).rgb * 0.0 + texture2D(specular, texCoord).rgb * 0.0;\n#endif\n",
                    "vec3 playerPos = vec3(0.0); vec3 viewPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0); DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }"
                ),
            ),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "CUSTOM_PBR=1\n"),
        ],
    )
    .unwrap();

    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    assert!(contract.supports_selected_subset());
}

#[test]
fn translucent_contract_defers_reflections_to_semantic_history_resources() {
    let source = ShaderPackSource::new(
        "translucent-reflections",
        9,
        vec![
            ShaderSourceFile::new(
                "gbuffers_water.fsh",
                concat!(
                    "void DoLighting() {}\n",
                    "void DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n",
                    "vec4 GetReflection() { return vec4(0.0); }\n",
                    "/* DRAWBUFFERS:03 */\n",
                    "void main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0);\n",
                    "vec3 playerPos = vec3(0.0); vec3 viewPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0);\n",
                    "color.rgb += GetReflection().rgb * 0.0; DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }"
                ),
            ),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();

    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    assert!(contract.supports_selected_subset());
}

#[test]
fn translucent_raster_directives_follow_runtime_environment_conditionals() {
    let source = ShaderPackSource::new(
        "translucent-raster-environment",
        7,
        vec![
            ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
            ShaderSourceFile::new(
                "shaders.properties",
                "#ifdef DISTANT_HORIZONS\nalphaTest.gbuffers_water=GREATER 0.0001\nblend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n#endif\n",
            ),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "DISTANT_HORIZONS=1\n"),
        ],
    )
    .unwrap();

    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    assert!(contract.translucent_raster_state.is_some());
}

#[test]
fn translucent_contract_uses_standard_blend_when_source_declares_only_alpha_test() {
    let source = ShaderPackSource::new(
        "translucent-raster-invalid",
        6,
        vec![
            ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
            ShaderSourceFile::new(
                "shaders.properties",
                "alphaTest.gbuffers_water=GREATER 0.0001\n",
            ),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();

    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    let raster = contract
        .translucent_raster_state
        .expect("an explicit alpha test admits the standard terrain translucent blend");
    assert_eq!(TerrainTranslucentBlend::SourceAlphaOver, raster.blend);
    assert!((raster.alpha_test.unwrap().greater_than() - 0.0001).abs() < f32::EPSILON);

    let blend_only = ShaderPackSource::new(
        "translucent-raster-blend-only",
        7,
        vec![
            ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
            ShaderSourceFile::new(
                "shaders.properties",
                "blend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n",
            ),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let error = derive_complementary_translucent_terrain_contract(&blend_only).unwrap_err();
    assert!(error
        .to_string()
        .contains("without alphaTest.gbuffers_water"));
}

#[test]
fn translucent_stage_uses_the_explicit_world_scope_without_borrowing_terrain() {
    let source = ShaderPackSource::new(
        "scoped-translucent",
        1,
        vec![
            ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("world0/gbuffers_water.fsh", translucent_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let contract = derive_complementary_translucent_terrain_contract_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    )
    .unwrap();
    assert_eq!("world0/gbuffers_water.fsh", contract.program_path);
    let stages = contract.source_stages().unwrap();
    assert_eq!("world0/gbuffers_water.vsh", stages.vertex.path);
    assert_eq!("world0/gbuffers_water.fsh", stages.fragment.path);
}

#[test]
fn terrain_contract_rejects_unsupported_or_malformed_draw_buffer_schema() {
    let unsupported = ShaderPackSource::new(
        "unsupported-draw-buffers",
        1,
        vec![
            ShaderSourceFile::new(
                "program/gbuffers_terrain.glsl",
                terrain_fragment_source().replace("DRAWBUFFERS:06", "DRAWBUFFERS:01"),
            ),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    assert!(derive_complementary_terrain_contract(&unsupported)
        .unwrap_err()
        .to_string()
        .contains("unsupported DRAWBUFFERS schema"));

    let malformed = ShaderPackSource::new(
        "malformed-draw-buffers",
        1,
        vec![
            ShaderSourceFile::new(
                "program/gbuffers_terrain.glsl",
                terrain_fragment_source().replace("DRAWBUFFERS:06", "DRAWBUFFERS:"),
            ),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    assert!(derive_complementary_terrain_contract(&malformed)
        .unwrap_err()
        .to_string()
        .contains("has no color slots"));
}

#[test]
fn drawbuffers_uses_the_final_active_schema_after_an_enabled_extension() {
    let slots = parse_draw_buffers_slots("/* DRAWBUFFERS:054 */\n").unwrap();
    assert_eq!(vec![0, 5, 4], slots);

    let slots =
        parse_draw_buffers_slots("/* DRAWBUFFERS:054 */\n/* DRAWBUFFERS:0547 */\n").unwrap();
    assert_eq!(vec![0, 5, 4, 7], slots);
}

#[test]
fn terrain_contract_admits_the_optional_named_view_space_normal_target() {
    let mut files = source().files();
    let terrain = files
        .iter_mut()
        .find(|file| file.path == "program/gbuffers_terrain.glsl")
        .expect("terrain fixture must include the selected source");
    terrain.contents = concat!(
        "void DoLighting() {}\n",
        "/* DRAWBUFFERS:065 */\n",
        "void main() {\n",
        "  vec4 color = texture2D(tex, texCoord);\n",
        "  if (color.a <= 0.00001) discard;\n",
        "  color.rgb *= glColor.rgb;\n",
        "  DoLighting();\n",
        "  gl_FragData[0] = color;\n",
        "  gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);\n",
        "  gl_FragData[2] = vec4(mat3(gbufferModelViewInverse) * normalM, 1.0);\n",
        "}\n"
    )
    .to_string();
    let source = ShaderPackSource::new("terrain-normal", 2, files).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    assert!(contract
        .outputs
        .contains(&TerrainPassOutput::ViewSpaceNormal));
    assert_eq!(
        Some(5),
        contract.output_color_slot(TerrainPassOutput::ViewSpaceNormal)
    );
}

#[test]
fn terrain_entry_discovery_uses_explicit_world_scope_not_an_active_renderer_pass() {
    let source = ShaderPackSource::new(
        "standard-layout",
        1,
        vec![
            ShaderSourceFile::new("gbuffers_terrain.fsh", terrain_fragment_source()),
            ShaderSourceFile::new("world0/gbuffers_terrain.fsh", terrain_fragment_source()),
            ShaderSourceFile::new("world-1/gbuffers_terrain.fsh", terrain_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();

    assert_eq!(
        "gbuffers_terrain.fsh",
        derive_complementary_terrain_contract(&source)
            .unwrap()
            .program_path
    );
    assert_eq!(
        "world0/gbuffers_terrain.fsh",
        derive_complementary_terrain_contract_for_scope(
            &source,
            TerrainProgramScope::Overworld
        )
        .unwrap()
        .program_path
    );
    assert_eq!(
        "world-1/gbuffers_terrain.fsh",
        derive_complementary_terrain_contract_for_scope(&source, TerrainProgramScope::Nether)
            .unwrap()
            .program_path
    );
    assert_eq!(
        "gbuffers_terrain.fsh",
        derive_complementary_terrain_contract_for_scope(&source, TerrainProgramScope::End)
            .unwrap()
            .program_path
    );
    let missing = ShaderPackSource::new(
        "missing-terrain",
        1,
        vec![
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    let error =
        derive_complementary_terrain_contract_for_scope(&missing, TerrainProgramScope::End)
            .unwrap_err();
    assert!(format!("{error}").contains("missing terrain fragment source for End"));
}

#[test]
fn terrain_contract_pairs_scoped_stage_files_and_shared_stage_defines() {
    let scoped = terrain_source_stages("world0/gbuffers_terrain.fsh").unwrap();
    assert_eq!("world0/gbuffers_terrain.vsh", scoped.vertex.path);
    assert!(scoped.vertex.defines.is_empty());
    assert_eq!("world0/gbuffers_terrain.fsh", scoped.fragment.path);

    let shared = terrain_source_stages("program/gbuffers_terrain.glsl").unwrap();
    assert_eq!("1", shared.vertex.defines["VERTEX_SHADER"]);
    assert_eq!("1", shared.fragment.defines["FRAGMENT_SHADER"]);
    assert!(terrain_source_stages("program/gbuffers_terrain.vert").is_err());
}

#[test]
fn shadow_source_stages_are_scoped_and_never_infer_another_dimension() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new("world0/shadow.vsh", "#version 130\nvoid main() {}"),
            ShaderSourceFile::new("world0/shadow.fsh", "#version 130\nvoid main() {}"),
            ShaderSourceFile::new("world-1/shadow.vsh", "#version 130\nvoid main() {}"),
            ShaderSourceFile::new("world-1/shadow.fsh", "#version 130\nvoid main() {}"),
        ],
    )
    .unwrap();

    let overworld =
        shadow_source_stages_for_scope(&source, TerrainProgramScope::Overworld).unwrap();
    assert_eq!("world0/shadow.vsh", overworld.vertex.path);
    assert_eq!("world0/shadow.fsh", overworld.fragment.path);

    let nether = shadow_source_stages_for_scope(&source, TerrainProgramScope::Nether).unwrap();
    assert_eq!("world-1/shadow.vsh", nether.vertex.path);
    assert_eq!("world-1/shadow.fsh", nether.fragment.path);

    let error = shadow_source_stages_for_scope(&source, TerrainProgramScope::End).unwrap_err();
    assert!(error.to_string().contains("missing shadow source for End"));
    assert!(error.to_string().contains("world1/shadow.fsh"));
}

#[test]
fn missing_normal_terrain_output_is_rejected() {
    let source = ShaderPackSource::new(
        "bad",
        1,
        vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void main() {}"),
            ShaderSourceFile::new("lib/common.glsl", ""),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
        ],
    )
    .unwrap();
    assert!(derive_complementary_terrain_contract(&source).is_err());
}

#[test]
fn selected_profile_requires_a_complete_semantic_voxel_light_volume() {
    let source = ShaderPackSource::new("unsupported", 1, vec![
        ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
        ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 128\n"),
        ShaderSourceFile::new(
            "program/shadowcomp.glsl",
            "void main() { vec3 posOffset = floor(previousCameraPosition) - floor(cameraPosition); }",
        ),
        ShaderSourceFile::new("shaders.properties", "profile.MATTMC=COLORED_LIGHTING=128\nimage.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\nimage.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n"),
        ShaderSourceFile::new("block.properties", ""),
    ]).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    assert!(contract.require_selected_subset().is_ok());
    assert!(contract
        .require_selected_subset_with_resources(None, 0)
        .is_err());
}

#[test]
fn rain_puddles_are_admitted_by_the_semantic_resource_contract_not_a_define_gate() {
    let mut files = source().files();
    files.push(ShaderSourceFile::new(
        RUNTIME_OPTIONS_PATH,
        "RAIN_PUDDLES=1\nDETAIL_QUALITY=3\n",
    ));
    let source = ShaderPackSource::new("rain-puddles", 2, files).unwrap();

    let contract = derive_complementary_terrain_contract(&source).unwrap();
    assert!(contract.supports_selected_subset());
    assert!(contract.require_selected_subset().is_ok());
}

#[test]
fn runtime_option_snapshot_overrides_pack_profile_defines() {
    let source = ShaderPackSource::new(
        "configured",
        1,
        vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", terrain_fragment_source()),
            ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 0\n"),
            ShaderSourceFile::new(
                "program/shadowcomp.glsl",
                "void main() { vec3 posOffset = floor(previousCameraPosition) - floor(cameraPosition); }",
            ),
            ShaderSourceFile::new(
                "shaders.properties",
                "profile.MATTMC=COLORED_LIGHTING=0\nimage.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\nimage.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n",
            ),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "COLORED_LIGHTING=128\n"),
        ],
    )
    .unwrap();

    let contract = derive_complementary_terrain_contract(&source).unwrap();
    assert_eq!(
        Some(&"128".to_string()),
        contract.property_defines.get("COLORED_LIGHTING")
    );
    assert!(contract
        .required_resources
        .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume));
}

#[test]
fn runtime_option_snapshot_rejects_non_identifier_keys() {
    let mut files = source().files();
    files.push(ShaderSourceFile::new(
        RUNTIME_OPTIONS_PATH,
        "BAD-OPTION=1\n",
    ));
    let source = ShaderPackSource::new("bad-runtime-option", 2, files).unwrap();
    let error = derive_complementary_terrain_contract(&source).unwrap_err();
    assert!(format!("{error}").contains("not a preprocessor identifier"));
}

#[test]
fn runtime_option_snapshot_rejects_non_scalar_values() {
    let mut files = source().files();
    files.push(ShaderSourceFile::new(
        RUNTIME_OPTIONS_PATH,
        "VALID_OPTION=not a token\n",
    ));
    let source = ShaderPackSource::new("bad-runtime-option", 2, files).unwrap();
    let error = derive_complementary_terrain_contract(&source).unwrap_err();
    assert!(format!("{error}").contains("not one preprocessor token"));
}

#[test]
fn runtime_option_snapshot_rejects_empty_value() {
    let mut files = source().files();
    files.push(ShaderSourceFile::new(
        RUNTIME_OPTIONS_PATH,
        "VALID_OPTION=\n",
    ));
    let source = ShaderPackSource::new("empty-runtime-option", 2, files).unwrap();
    let error = derive_complementary_terrain_contract(&source).unwrap_err();
    assert!(format!("{error}").contains("not one preprocessor token"));
}

#[test]
fn bundled_terrain_source_contract_does_not_reject_expanded_includes() {
    let contract = derive_complementary_terrain_contract(
        &bundled_complementary_hung_loified_source(9).unwrap(),
    )
    .unwrap();
    assert!(contract.require_selected_subset().is_ok());
}

#[test]
fn bundled_complementary_volume_requirement_is_source_derived() {
    let contract = derive_complementary_terrain_contract(
        &bundled_complementary_hung_loified_source(3).unwrap(),
    )
    .unwrap();
    let requirements = contract.voxel_light_volume_requirements.unwrap();
    assert_eq!(256, requirements.extent.width);
    assert_eq!(128, requirements.extent.height);
    assert_eq!(256, requirements.extent.depth);
    assert_eq!(
        crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint,
        requirements.occupancy_format
    );
    assert_eq!(
        crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
        requirements.lighting_format
    );
    assert!(requirements.update_policy.temporal_reprojection);
    assert!(requirements.update_policy.alternate_x_half_rate);
    assert!(requirements.update_policy.preserve_behind_view);
}

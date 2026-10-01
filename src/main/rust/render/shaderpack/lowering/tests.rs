use std::sync::{Mutex, OnceLock};

use crate::render::shaderpack::lowering::*;
use crate::render::shaderpack::source::preprocess::{preprocess_artifact, PreprocessInput};
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};

fn fullscreen_probe_test_lock() -> &'static Mutex<()> {
    static LOCK: OnceLock<Mutex<()>> = OnceLock::new();
    LOCK.get_or_init(|| Mutex::new(()))
}

fn artifact(source: &str) -> PreprocessedShaderSource {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new("terrain.fsh", source)],
    )
    .unwrap();
    preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap()
}

#[test]
fn lowers_legacy_fragment_outputs_to_named_semantics_without_touching_varyings() {
    let lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nvarying vec2 uv; uniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, uv); gl_FragData [ 1 ] = vec4(1.0); }",
    ))
    .unwrap();
    assert!(lowered.source().starts_with("#version 450\n"));
    assert!(lowered.source().contains("out_terrain_lit_color"));
    assert!(lowered.source().contains("out_terrain_material_auxiliary"));
    assert!(lowered.source().contains("texture(tex, uv)"));
    assert!(!lowered.source().contains("gl_FragData"));
    assert!(!lowered
        .remaining_dialect()
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::PreVulkanGlslVersion));
    assert!(lowered
        .remaining_dialect()
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::CompatibilityVertexAttributes));
}

#[test]
fn selected_source_fragment_probe_replaces_only_the_named_color_outputs() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0); }",
    ))
    .unwrap();

    apply_selected_source_fragment_probe_mode(&mut lowered, Some("tint")).unwrap();

    assert!(lowered
        .source()
        .contains("out_terrain_lit_color = vec4(color.rgb * glColor.rgb, color.a);"));
    assert!(lowered
        .source()
        .contains("out_terrain_material_auxiliary = vec4(0.0);"));
    assert!(lowered
        .source()
        .contains("selected-source diagnostic probe: tint"));

    let mut vertex_color = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; varying vec4 glColor; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();
    apply_selected_source_fragment_probe_mode(&mut vertex_color, Some("vertex-color")).unwrap();
    assert!(vertex_color
        .source()
        .contains("out_terrain_lit_color = glColor;"));

    let mut vertex_color_opaque = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; varying vec4 glColor; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();
    apply_selected_source_fragment_probe_mode(
        &mut vertex_color_opaque,
        Some("vertex-color-opaque"),
    )
    .unwrap();
    assert!(vertex_color_opaque
        .source()
        .contains("out_terrain_lit_color = vec4(glColor.rgb, 1.0);"));

    let mut vertex_color_raw = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; varying vec4 glColorRaw; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();
    apply_selected_source_fragment_probe_mode(&mut vertex_color_raw, Some("vertex-color-raw"))
        .unwrap();
    assert!(vertex_color_raw
        .source()
        .contains("out_terrain_lit_color = vec4(glColorRaw.rgb, 1.0);"));
}

#[test]
fn world_target_lowering_flips_fetches_lod_and_target_parameters_but_not_pack_images() {
    let source = concat!(
        "uniform sampler2D depthtex1; uniform sampler2D gaux4; uniform sampler2D colortex6;\n",
        "float probe(sampler2D depthtex, vec2 uv) { return texture(depthtex, uv).r + textureLod(depthtex, uv, 0.0).r; }\n",
        "float mixed(sampler2D any, vec2 uv) { return texture(any, uv).r; }\n",
        "void main() {\n",
        "  float a = probe(depthtex1, vec2(0.5));\n",
        "  float b = mixed(depthtex1, vec2(0.5)) + mixed(gaux4, vec2(0.5));\n",
        "  vec4 m = texelFetch(colortex6, ivec2(3, 4), 0);\n",
        "  vec4 n = texture(gaux4, vec2(0.25));\n",
        "  vec4 l = textureLod(colortex6, vec2(0.25), 1.0);\n",
        "}\n"
    )
    .to_string();
    let lowered = lower_world_material_fragment_coordinates(
        source,
        &TerrainSourceUniformContract {
            declarations: Vec::new(),
            fields: Vec::new(),
            std140_size: 0,
        },
        &["gaux4".to_string()],
    )
    .unwrap();
    assert!(lowered.contains("vulkanic_source_fetch_target_colortex6( ivec2(3, 4), 0)"));
    assert!(lowered.contains("vulkanic_source_sample_target_lod_colortex6( vec2(0.25), 1.0)"));
    assert!(lowered.contains("vulkanic_source_sample_target_parameter(depthtex, uv)"));
    assert!(lowered.contains("vulkanic_source_sample_target_parameter_lod(depthtex, uv, 0.0)"));
    // Mixed call sites and pack-bound images keep their authored addressing.
    assert!(lowered.contains("return texture(any, uv).r;"));
    assert!(lowered.contains("vec4 n = texture(gaux4, vec2(0.25));"));
    assert!(lowered.contains("size.y - 1 - texel.y"));
}

#[test]
fn selected_source_atlas_probe_reaches_water_style_colorp_sample() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 colorP = texture2D(tex, texCoord); gl_FragData[0] = colorP; gl_FragData[1] = vec4(1.0); }",
    ))
    .unwrap();
    apply_selected_source_fragment_probe_mode(&mut lowered, Some("atlas-alpha")).unwrap();
    assert!(lowered
        .source()
        .contains("out_terrain_lit_color = vec4(vec3(colorP.a), 1.0);"));
    assert!(!lowered
        .source()
        .contains("out_terrain_lit_color = vec4(vec3(color.a), 1.0);"));
}

#[test]
fn selected_source_atlas_probe_uses_translucent_auxiliary_output() {
    let mut lowered = LoweredTranslucentTerrainFragmentSource {
        entry_path: "gbuffers_water.fsh".to_string(),
        source: "vec4 colorP = texture(tex, texCoord);".to_string(),
        outputs: Vec::new(),
        remaining_dialect: analyze_glsl_text("gbuffers_water.fsh", ""),
    };
    apply_selected_source_fragment_probe_mode(&mut lowered, Some("atlas-alpha")).unwrap();
    assert!(lowered
        .source
        .contains("out_terrain_translucency_auxiliary"));
    assert!(!lowered.source.contains("out_terrain_material_auxiliary"));
}

#[test]
fn selected_source_water_only_probe_skips_opaque_entries() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();
    apply_selected_source_fragment_probe_mode(&mut lowered, Some("constant-red-water"))
        .unwrap();
    assert!(!lowered
        .source()
        .contains("selected-source diagnostic probe"));
}

#[test]
fn selected_source_vertex_probe_replaces_only_the_color_varying_assignment() {
    let mut source = "void main() { glColorRaw = vulkanic_source_vertex_color; }".to_string();
    apply_selected_source_vertex_position_probe_mode(&mut source, Some("constant-red"))
        .unwrap();
    assert!(source.contains("glColorRaw = vec4(1.0, 0.0, 0.0, 1.0);"));
    assert!(!source.contains("glColorRaw = vulkanic_source_vertex_color;"));
}

#[test]
fn selected_source_direct_transform_probe_overrides_pack_position_after_main() {
    let mut source =
        "mat4 vulkanic_source_model_transform; mat4 gbufferProjection; void main() { gl_Position = ftransform(); }"
            .to_string();
    apply_selected_source_vertex_position_probe_mode(&mut source, Some("direct-transform"))
        .unwrap();
    assert!(source.contains(
        "gl_Position = gbufferProjection * vulkanic_source_model_view * vulkanic_source_position"
    ));
}

#[test]
fn selected_source_instance_translation_probe_is_idempotent_and_semantic_guarded() {
    let mut source =
        "mat4 vulkanic_source_model_transform; void main() { gl_Position = vec4(0.0); }"
            .to_string();
    apply_selected_source_vertex_position_probe_mode(&mut source, Some("instance-translation"))
        .unwrap();
    let once = source.clone();
    apply_selected_source_vertex_position_probe_mode(&mut source, Some("instance-translation"))
        .unwrap();
    assert_eq!(once, source);

    let mut unrelated = "void main() { gl_Position = vec4(0.0); }".to_string();
    apply_selected_source_vertex_position_probe_mode(
        &mut unrelated,
        Some("instance-translation"),
    )
    .unwrap();
    assert_eq!(unrelated, "void main() { gl_Position = vec4(0.0); }");
}

#[test]
fn selected_source_wave_probe_removes_only_the_pack_position_mutation() {
    let mut source =
        "void main() { DoWave(position.xyz, mat); gl_Position = ftransform(); }".to_string();
    std::env::set_var("MATTMC_RUST_SELECTED_SOURCE_WAVE_PROBE", "disable");
    apply_selected_source_wave_probe(&mut source).unwrap();
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_WAVE_PROBE");
    assert!(!source.contains("DoWave(position.xyz, mat);"));
    assert!(source.contains("gl_Position = ftransform();"));
}

#[test]
fn selected_source_taa_probe_is_idempotent_and_only_replaces_jitter_assignment() {
    let mut source =
        "void main() { gl_Position.xy = TAAJitter(gl_Position.xy, gl_Position.w); }"
            .to_string();
    std::env::set_var("MATTMC_RUST_SELECTED_SOURCE_TAA_PROBE", "disable");
    apply_selected_source_taa_probe(&mut source).unwrap();
    let once = source.clone();
    apply_selected_source_taa_probe(&mut source).unwrap();
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_TAA_PROBE");
    assert_eq!(once, source);
    assert!(source.contains("selected-source diagnostic probe: TAA disabled"));
    assert!(!source.contains("TAAJitter(gl_Position.xy"));
}

#[test]
fn selected_source_direct_model_transform_probe_uses_explicit_semantic_chain() {
    let mut source = "#version 130\nmat4 gbufferProjection;\nvec4 vulkanic_source_position;\nmat4 vulkanic_source_model_transform;\nmat4 gbufferModelView;\nvoid main() { gl_Position = ftransform(); }\n".to_string();
    apply_selected_source_vertex_position_probe_mode(
        &mut source,
        Some("direct-model-transform"),
    )
    .unwrap();
    assert!(source.contains(
        "gbufferProjection * gbufferModelView * vulkanic_source_model_transform * vulkanic_source_position"
    ));
}

#[test]
fn selected_source_direct_model_transform_inline_replaces_source_assignment() {
    let mut source = "mat4 gbufferProjection; mat4 gbufferModelView; mat4 vulkanic_source_model_transform; vec4 vulkanic_source_position; void main() { vec4 position = vulkanic_source_model_transform * vulkanic_source_position; gl_Position = gbufferProjection * gbufferModelView * position; }".to_string();
    apply_selected_source_vertex_position_probe_mode(
        &mut source,
        Some("direct-model-transform-inline"),
    )
    .unwrap();
    assert!(source.contains(
        "gbufferProjection * gbufferModelView * vulkanic_source_model_transform * vulkanic_source_position"
    ));
    assert!(!source.contains("* position;"));
}

#[test]
fn selected_source_direct_transform_probe_does_not_touch_shadow_projection() {
    let mut source = "mat4 gbufferProjection; mat4 shadowProjection; mat4 vulkanic_source_model_transform; void main() { gl_Position = shadowProjection * vulkanic_source_model_transform * vulkanic_source_position; }".to_string();
    let before = source.clone();
    apply_selected_source_vertex_position_probe_mode(&mut source, Some("direct-transform"))
        .unwrap();
    assert_eq!(before, source);
}

#[test]
fn selected_source_fragment_lightmap_probe_preserves_the_shader_visible_inputs() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; varying vec2 lmCoord; varying vec4 glColor; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();

    apply_selected_source_fragment_probe_mode(&mut lowered, Some("lightmap")).unwrap();

    assert!(lowered
        .source()
        .contains("out_terrain_lit_color = vec4(lmCoord.x, lmCoord.y, glColor.a, 1.0);"));
}

#[test]
fn selected_source_fragment_light_probes_isolate_the_scene_components() {
    let source = "#version 130\nvec3 lightColorM; vec3 ambientColorM; vec3 shadowMult; vec3 ambientMult; void DoLighting(inout vec4 color) { vec3 sceneLighting = lightColorM * shadowMult + ambientColorM * ambientMult; color *= vec4(sceneLighting, 1.0); } void main() { vec4 color = vec4(1.0); DoLighting(color); gl_FragData[0] = color; }";
    let mut direct = lower_terrain_fragment_surface(&artifact(source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut direct, Some("light-color")).unwrap();
    assert!(direct.source().contains("color = vec4(lightColorM, 1.0);"));

    let atlas_source = "#version 130\nuniform sampler2D tex; vec2 texCoord; void main() { vec4 color = texture(tex, texCoord); if (color.a <= 0.00001) discard; gl_FragData[0] = color; }";
    let mut atlas_alpha = lower_terrain_fragment_surface(&artifact(atlas_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut atlas_alpha, Some("atlas-alpha")).unwrap();
    assert!(atlas_alpha
        .source()
        .contains("out_terrain_lit_color = vec4(vec3(color.a), 1.0);"));

    let mut atlas_uv = lower_terrain_fragment_surface(&artifact(atlas_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut atlas_uv, Some("atlas-uv")).unwrap();
    assert!(atlas_uv
        .source()
        .contains("out_terrain_lit_color = vec4(texCoord, 0.0, 1.0);"));

    let mut atlas_alpha_flipped =
        lower_terrain_fragment_surface(&artifact(atlas_source)).unwrap();
    apply_selected_source_fragment_probe_mode(
        &mut atlas_alpha_flipped,
        Some("atlas-alpha-flipped-v"),
    )
    .unwrap();
    assert!(atlas_alpha_flipped
        .source()
        .contains("texture(tex, vec2(texCoord.x, 1.0 - texCoord.y))"));

    let mut ambient = lower_terrain_fragment_surface(&artifact(source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut ambient, Some("ambient-color")).unwrap();
    assert!(ambient
        .source()
        .contains("color = vec4(ambientColorM, 1.0);"));

    let mut shadow = lower_terrain_fragment_surface(&artifact(source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut shadow, Some("shadow-mult")).unwrap();
    assert!(shadow.source().contains("color = vec4(shadowMult, 1.0);"));

    let final_light_source = "#version 130\nfloat darknessLightFactor; vec3 finalDiffuse; vec3 color; float pow2(float value) { return value * value; } void DoLighting() { color.rgb *= finalDiffuse; color.rgb *= pow2(1.0 - darknessLightFactor); } void main() { DoLighting(); gl_FragData[0] = vec4(color, 1.0); }";
    let mut diffuse = lower_terrain_fragment_surface(&artifact(final_light_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut diffuse, Some("final-diffuse")).unwrap();
    assert!(diffuse
        .source()
        .contains("color = vec4(finalDiffuse, 1.0);"));

    let factor_source = "#version 130\nfloat directionShade; float vanillaAO; vec3 sceneLighting; vec3 lightColorM; vec3 ambientColorM; vec3 shadowMult; vec3 ambientMult; vec3 blockLighting; vec3 minLighting; vec3 emission; float pow2(float value) { return value * value; } void DoLighting(inout vec4 color) { vec3 finalDiffuse = pow2(directionShade * vanillaAO) * (blockLighting + pow2(sceneLighting) + minLighting) + pow2(emission); color.rgb *= finalDiffuse; } void main() { vec4 color = vec4(1.0); DoLighting(color); gl_FragData[0] = color; }";
    let mut factors = lower_terrain_fragment_surface(&artifact(factor_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut factors, Some("lighting-factors")).unwrap();
    assert!(factors.source().contains("clamp(directionShade, 0.0, 1.0)"));
    assert!(factors.source().contains("clamp(vanillaAO, 0.0, 1.0)"));

    let mut components_a = lower_terrain_fragment_surface(&artifact(factor_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut components_a, Some("lighting-components-a"))
        .unwrap();
    assert!(components_a
        .source()
        .contains("max(max(lightColorM.r, lightColorM.g), lightColorM.b)"));
    assert!(components_a
        .source()
        .contains("max(max(ambientColorM.r, ambientColorM.g), ambientColorM.b)"));

    let mut components_b = lower_terrain_fragment_surface(&artifact(factor_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut components_b, Some("lighting-components-b"))
        .unwrap();
    assert!(components_b
        .source()
        .contains("clamp(ambientMult, 0.0, 1.0)"));
    assert!(components_b
        .source()
        .contains("max(max(blockLighting.r, blockLighting.g), blockLighting.b)"));

    let mut darkness = lower_terrain_fragment_surface(&artifact(final_light_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut darkness, Some("darkness-scale")).unwrap();
    assert!(darkness
        .source()
        .contains("color = vec4(vec3(pow2(1.0 - darknessLightFactor)), 1.0);"));

    let shadow_sampling_source = "#version 130\nfloat shadow0; vec3 shadowcol; vec3 SampleShadow() { return shadowcol * (1.0 - shadow0) + shadow0; } void main() { gl_FragData[0] = vec4(SampleShadow(), 1.0); }";
    let mut primary =
        lower_terrain_fragment_surface(&artifact(shadow_sampling_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut primary, Some("shadow-primary")).unwrap();
    assert!(primary
        .source()
        .contains("return vec3(shadow0); // selected-source diagnostic probe: shadow-primary"));

    let shadow_source = "#version 130\nuniform sampler2DShadow shadowtex0; vec3 GetShadowPos(vec3 playerPos) { return playerPos; } void DoLighting(inout vec4 color) { vec3 playerPosM = vec3(0.25); vec3 shadowPos = GetShadowPos(playerPosM); color *= vec4(shadowPos, 1.0); } void main() { vec4 color = vec4(1.0); DoLighting(color); gl_FragData[0] = color; }";
    let mut coordinates = lower_terrain_fragment_surface(&artifact(shadow_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut coordinates, Some("shadow-coordinate"))
        .unwrap();
    assert!(coordinates
        .source()
        .contains("color = vec4(shadowPos, 1.0);"));

    let mut centered = lower_terrain_fragment_surface(&artifact(shadow_source)).unwrap();
    apply_selected_source_fragment_probe_mode(
        &mut centered,
        Some("shadow-coordinate-centered"),
    )
    .unwrap();
    assert!(centered
        .source()
        .contains("color = vec4(clamp(shadowPos * 0.5 + 0.5, 0.0, 1.0), 1.0);"));

    let player_position_source = "#version 130\nvec3 viewPos; vec3 ViewToPlayer(vec3 value) { return value; } void main() { vec3 playerPos = ViewToPlayer(viewPos); gl_FragData[0] = vec4(playerPos, 1.0); }";
    let mut player_position =
        lower_terrain_fragment_surface(&artifact(player_position_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut player_position, Some("player-position"))
        .unwrap();
    assert!(player_position.source().contains(
        "out_terrain_lit_color = vec4(clamp(playerPos / 384.0 + 0.5, 0.0, 1.0), 1.0);"
    ));

    let mut reconstruction =
        lower_terrain_fragment_surface(&artifact(player_position_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut reconstruction, Some("reconstruction"))
        .unwrap();
    assert!(reconstruction.source().contains(
        "out_terrain_lit_color = vec4(gl_FragCoord.z, clamp(playerPos.y / 384.0 + 0.5, 0.0, 1.0), clamp(length(viewPos) / 384.0, 0.0, 1.0), 1.0);"
    ));

    let mut viewport =
        lower_terrain_fragment_surface(&artifact(player_position_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut viewport, Some("viewport-uniforms"))
        .unwrap();
    assert!(viewport.source().contains("atan(viewWidth / 1024.0)"));
    assert!(viewport.source().contains("atan(viewHeight / 1024.0)"));
    assert!(viewport.source().contains("gl_FragCoord.x / 1280.0"));

    let mut matrix_basis =
        lower_terrain_fragment_surface(&artifact(player_position_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut matrix_basis, Some("matrix-basis")).unwrap();
    assert!(matrix_basis
        .source()
        .contains("gbufferProjectionInverse[3][2]"));

    let mut constant_red = lower_terrain_fragment_surface(&artifact(source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut constant_red, Some("constant-red")).unwrap();
    assert!(constant_red
        .source()
        .contains("out_terrain_lit_color = vec4(1.0, 0.0, 0.0, 1.0);"));

    let mut compare = lower_terrain_fragment_surface(&artifact(shadow_source)).unwrap();
    apply_selected_source_fragment_probe_mode(&mut compare, Some("shadow-compare")).unwrap();
    assert!(compare
        .source()
        .contains("vulkanic_source_shadow2D(shadowtex0"));
}

#[test]
fn legacy_shadow2d_lod_lowers_to_explicit_lod_comparison() {
    let lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2DShadow shadowtex1; varying vec2 texCoord; void main() { float s = shadow2DLod(shadowtex1, vec3(texCoord, 0.5), 0.0).z; gl_FragData[0] = vec4(s); }",
    ))
    .unwrap();
    assert!(lowered
        .source()
        .contains("vulkanic_source_shadow2DLod(shadowtex1, vec3(texCoord, 0.5), 0.0)"));
    assert!(lowered.source().contains(
        "#define vulkanic_source_shadow2DLod(source_texture, source_coordinates, source_lod) vec4(textureLod(source_texture, source_coordinates, source_lod))"
    ));
    assert!(!lowered.source().contains(" shadow2DLod("));
}

#[test]
fn selected_source_fragment_probe_rejects_unknown_mode() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();

    let error =
        apply_selected_source_fragment_probe_mode(&mut lowered, Some("wrong")).unwrap_err();
    assert!(error
        .to_string()
        .contains("unknown selected-source fragment probe 'wrong'"));
}

#[test]
fn selected_source_entity_probe_observes_only_local_texture_or_uv() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; gl_FragData[1] = color; }",
    ))
    .unwrap();
    apply_selected_source_entity_fragment_probe_mode(&mut lowered, Some("texture")).unwrap();
    assert!(lowered.source().contains("out_terrain_lit_color = color;"));
    assert!(lowered
        .source()
        .contains("selected-source entity diagnostic probe: texture"));

    let mut uv = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; gl_FragData[1] = color; }",
    ))
    .unwrap();
    apply_selected_source_entity_fragment_probe_mode(&mut uv, Some("uv")).unwrap();
    assert!(uv
        .source()
        .contains("out_terrain_lit_color = vec4(texCoord, 0.0, 1.0);"));

    let error =
        apply_selected_source_entity_fragment_probe_mode(&mut uv, Some("wrong")).unwrap_err();
    assert!(error
        .to_string()
        .contains("unknown selected-source entity fragment probe 'wrong'"));
}

#[test]
fn distant_horizons_only_pre_lighting_probe_leaves_normal_terrain_unmodified() {
    let mut lowered = lower_terrain_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex; varying vec2 texCoord; void main() { vec4 color = texture2D(tex, texCoord); gl_FragData[0] = color; }",
    ))
    .unwrap();
    let before = lowered.source().to_string();

    apply_selected_source_fragment_probe_mode(&mut lowered, Some("pre-lighting")).unwrap();

    assert_eq!(lowered.source(), before);
}

#[test]
fn fullscreen_depth_probe_reads_the_named_distant_depth_at_the_primary_output() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let source = "#version 130\nuniform sampler2D dhDepthTex; ivec2 texelCoord; vec3 color; void main() { gl_FragData[0] = vec4(color, 1.0); }";
    let mut lowered = source.to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    lowered = lowered.replace("gl_FragData[0]", "out_vulkanic_source_color_primary");
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "distant-horizons-depth",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "out_vulkanic_source_color_primary = vec4(vec3(texelFetch(dhDepthTex, texelCoord, 0).r), 1.0);"
    ));
}

#[test]
fn fullscreen_depth_routing_probe_exposes_the_exact_deferred_dh_predicate() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "uniform sampler2D depthtex0; uniform sampler2D dhDepthTex; ivec2 texelCoord; vec3 color; void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "distant-horizons-depth-routing",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "float z0 = texelFetch(depthtex0, texelCoord, 0).r; float vulkanicDhDepthProbe = texelFetch(dhDepthTex, texelCoord, 0).r;"
    ));
    assert!(lowered.contains(
        "vec4(z0, vulkanicDhDepthProbe, (z0 >= 1.0 && vulkanicDhDepthProbe < 1.0) ? 1.0 : 0.0, 1.0);"
    ));
}

#[test]
fn fullscreen_depth_coordinate_probe_exposes_current_and_mirrored_dh_texels() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "uniform sampler2D depthtex0; uniform sampler2D dhDepthTex; uniform float viewHeight; ivec2 texelCoord; vec3 color; void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "distant-horizons-depth-coordinate",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "vec4(vulkanicDhDepthProbe, texelFetch(dhDepthTex, ivec2(texelCoord.x, int(viewHeight) - 1 - texelCoord.y), 0).r, texCoord.y, 1.0);"
    ));
    assert!(lowered.contains(
        "selected-source fullscreen diagnostic probe: distant-horizons-depth-coordinate"
    ));
}

#[test]
fn fullscreen_fog_input_probe_reads_deferred_dh_values_inside_the_fog_branch() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "int dhRenderDistance; void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; float z0DH = texelFetch(dhDepthTex, texelCoord, 0).r; DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); if (z0DH < 1.0) { // Distant Horizons Chunks\nfloat lViewPos = 7.0; DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); } out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "distant-horizons-fog-inputs",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "float z0 = texelFetch(depthtex0, texelCoord, 0).r; float vulkanicDhDepthProbe = texelFetch(dhDepthTex, texelCoord, 0).r; vec3 vulkanicDhFogInputs = vec3(0.0);"
    ));
    assert!(lowered.contains(
        "vulkanicDhFogInputs = vec3(vulkanicDhDepthProbe, clamp(lViewPos / max(float(dhRenderDistance), 1.0), 0.0, 1.0), texCoord.y); DoFog("
    ));
    assert!(
        lowered.contains("out_vulkanic_source_color_primary = vec4(vulkanicDhFogInputs, 1.0);")
    );
    assert!(lowered.contains(
        "if (z0DH < 1.0) { // Distant Horizons Chunks\nfloat lViewPos = 7.0; vulkanicDhFogInputs ="
    ));
}

#[test]
fn fullscreen_fog_effect_probe_keeps_the_fog_call_and_encodes_its_effect() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; float z0DH = texelFetch(dhDepthTex, texelCoord, 0).r; DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); if (z0DH < 1.0) { // Distant Horizons Chunks\nfloat lViewPos = 7.0; DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); } out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "distant-horizons-fog-effect",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "vec3 vulkanicFogInputColor = color.rgb; DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); vulkanicDhFogInputs = vec3("
    ));
    assert!(lowered.contains("log2(max(lViewPos, 1.0)) / 12.0"));
    assert!(
        lowered.contains("out_vulkanic_source_color_primary = vec4(vulkanicDhFogInputs, 1.0);")
    );
    assert!(lowered.contains(
        "if (z0DH < 1.0) { // Distant Horizons Chunks\nfloat lViewPos = 7.0; vec3 vulkanicFogInputColor ="
    ));
}

#[test]
fn fullscreen_gbuffer_input_probe_exposes_normal_and_material_targets() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered =
        "void main() { out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "gbuffer-inputs",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "texelFetch(colortex5, texelCoord, 0).rgb, texelFetch(colortex6, texelCoord, 0).r"
    ));
}

#[test]
fn fullscreen_gbuffer_primary_probe_exposes_current_primary_target() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered =
        "void main() { out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "gbuffer-primary",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "vec4(texelFetch(colortex0, texelCoord, 0).rgb, 1.0); // selected-source fullscreen diagnostic probe: gbuffer-primary"
    ));
}

#[test]
fn fullscreen_depth_input_probe_exposes_deferred_depth() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "depth-input",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains(
        "vec4(vec3(z0), 1.0); // selected-source fullscreen diagnostic probe: depth-input"
    ));
}

#[test]
fn fullscreen_depth_input_flipped_probe_uses_explicit_mirrored_texel() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "depth-input-flipped",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains("ivec2(texelCoord.x, int(viewHeight) - 1 - texelCoord.y)"));
}

#[test]
fn fullscreen_deferred_fog_probe_exposes_depth_distance_and_sky_fade() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "void main() { float z0 = texelFetch(depthtex0, texelCoord, 0).r; vec4 viewPos = vec4(0.0); float lViewPos = length(viewPos); DoFog(color.rgb, skyFade, lViewPos, playerPos, VdotU, VdotS, dither); out_vulkanic_source_color_primary = vec4(color, 1.0); }".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "deferred-fog-inputs",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/deferred1.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert!(lowered.contains("vec3 vulkanicDeferredFogInputs"));
    assert!(
        lowered.contains("selected-source fullscreen diagnostic probe: deferred-fog-inputs")
    );
}

#[test]
fn fullscreen_depth_probe_leaves_unrelated_source_stages_unchanged() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let mut lowered = "out_vulkanic_source_color_primary = vec4(color, 1.0);".to_string();
    let outputs = vec![FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 0,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        semantic_name: "out_vulkanic_source_color_primary".to_string(),
    }];
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "distant-horizons-depth",
    );
    let result =
        apply_selected_source_fullscreen_probe(&mut lowered, &outputs, "world0/composite4.fsh");
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    result.unwrap();
    assert_eq!(
        "out_vulkanic_source_color_primary = vec4(color, 1.0);",
        lowered
    );
}

#[test]
fn fullscreen_source_pair_uses_named_pack_color_outputs_and_owned_uv_stream() {
    let pack = ShaderPackSource::new(
        "fullscreen-source",
        5,
        vec![
            ShaderSourceFile::new(
                "world0/deferred.vsh",
                "#version 130\nout vec2 uv;\nout vec2 light_uv;\nvoid main() { uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; light_uv = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, uv); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\ncolortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lowered = lower_fullscreen_source_pair(&vertex, &fragment, &bindings).unwrap();
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        lowered.fragment().outputs()[0].role()
    );
    assert!(lowered
        .vertex()
        .source()
        .contains("VulkanicSourceFullscreenFrame"));
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_fullscreen_vertex_index"));
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_fullscreen_uv_coordinates"));
    assert!(!lowered
        .vertex()
        .source()
        .contains("layout(location = 0) in vec2 vulkanic_source_fullscreen_position"));
    assert!(!lowered.vertex().source().contains("gl_MultiTexCoord1"));
    assert!(!lowered.vertex().source().contains("gbufferModelView"));
    assert!(lowered
        .fragment()
        .source()
        .contains("out_vulkanic_source_color_primary"));
    assert!(!lowered.fragment().source().contains("gl_FragData"));
    assert!(lowered
        .vertex()
        .source()
        .contains("vec4(vulkanic_source_fullscreen_position(), 0.0, 1.0)"));
    assert!(lowered
        .vertex()
        .source()
        .contains("coordinate.y = 1.0 - coordinate.y"));
    assert!(lowered
        .vertex()
        .source()
        .contains("#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH"));
}

#[test]
fn fullscreen_history_corner_fetch_uses_source_to_target_row_conversion() {
    let pack = ShaderPackSource::new(
        "fullscreen-history-corner",
        1,
        vec![
            ShaderSourceFile::new(
                "world0/deferred.vsh",
                "#version 130\nuniform sampler2D colortex4;\nuniform float viewWidth;\nuniform float viewHeight;\nout float factor;\nvoid main() { factor = texelFetch(colortex4, ivec2(viewWidth-1, viewHeight-1), 0).r; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred.fsh",
                "#version 130\nin float factor;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0] = vec4(factor); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\ncolortex4=shader_pack_color:volumetric_factor\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lowered = lower_fullscreen_source_pair(&vertex, &fragment, &bindings).unwrap();
    assert!(lowered.vertex().source().contains(
        "vulkanic_source_fullscreen_history_corner(viewWidth, viewHeight)"
    ));
    assert!(lowered.vertex().source().contains(
        "return ivec2(source_texel.x, int(source_height) - 1 - source_texel.y);"
    ));
}

#[test]
fn bundled_reprojection_helpers_convert_screen_uvs_exactly_once() {
    // Complementary's TAA (composite6) and temporal reflection filter
    // (deferred1) reproject through OpenGL screen UVs. A static camera
    // hides a missing conversion; camera motion ghosted without one.
    let pack =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lower = |stage: &str| {
        let vertex = crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options(
            &pack,
            &format!("world0/{stage}.vsh"),
            &[],
        )
        .unwrap();
        let fragment = crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options(
            &pack,
            &format!("world0/{stage}.fsh"),
            &[],
        )
        .unwrap();
        lower_fullscreen_source_pair(&vertex, &fragment, &bindings)
            .unwrap()
            .fragment()
            .source()
            .to_string()
    };
    let converted_return = "return vulkanic_source_fullscreen_screen_uv(previousPosition.xy / previousPosition.w * 0.5 + 0.5);";
    let taa = lower("composite6");
    assert!(taa.contains(converted_return));
    assert!(taa.contains("prvCoord = Reprojection(viewPos1);"));
    assert!(!taa.contains("vulkanic_source_fullscreen_screen_uv(Reprojection("));
    let reflections = lower("deferred1");
    // `Reprojection` and `SHalfReprojection` return screen UVs.
    assert_eq!(2, reflections.matches(converted_return).count());
    assert!(!reflections.contains("return previousPosition.xy / previousPosition.w * 0.5 + 0.5;"));
    assert_eq!(
        2,
        reflections
            .matches("pos.xy = vulkanic_source_fullscreen_screen_uv(pos.xy);")
            .count()
    );
}

#[test]
fn fullscreen_fragment_coordinates_keep_source_math_and_native_target_addresses() {
    let pack = ShaderPackSource::new(
        "fullscreen-fragment-coordinate-domains",
        5,
        vec![
            ShaderSourceFile::new(
                "world0/deferred.vsh",
                "#version 130\nout vec2 texCoord;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 texCoord;\nuniform float viewHeight;\nuniform sampler2D colortex0;\nuniform sampler2D depthtex0;\nuniform sampler2D noisetex;\nuniform mat4 gbufferProjectionInverse;\nvec2 Reprojection(vec4 p) { return p.xy; }\nvoid main() { ivec2 texelCoord = ivec2(gl_FragCoord.xy); vec2 sourceScreen = gl_FragCoord.xy / vec2(1.0, viewHeight); vec4 screenPos = vec4(texCoord, 0.5, 1.0); vec4 screenPosDH = vec4(texCoord, 0.75, 1.0); vec4 screenPos1 = vec4(texCoord, 0.25, 1.0); vec4 screenPos1DH = vec4(texCoord, 0.125, 1.0); vec4 inlineView = gbufferProjectionInverse * (vec4(texCoord, 0.5, 1.0) * 2.0 - 1.0); vec4 viewPos1 = inlineView; vec2 prvCoord = texCoord; prvCoord = Reprojection(viewPos1); vec4 noise = texture2D(noisetex, texCoord * vec2(1280.0, 720.0) / 128.0); vec4 reconstructed = vec4(texCoord, texelFetch(colortex0, texelCoord, 0).r + texelFetch(depthtex0, texelCoord, 0).r, 1.0); gl_FragData[0] = texelFetch(colortex0, texelCoord, 0) + vec4(sourceScreen, 0.0, 0.0) + reconstructed + inlineView + noise + screenPos + screenPosDH + screenPos1 + screenPos1DH + vec4(prvCoord, 0.0, 0.0); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lowered = lower_fullscreen_source_pair(&vertex, &fragment, &bindings).unwrap();
    let fragment = lowered.fragment().source();

    assert!(fragment.contains("vec4 vulkanic_source_fullscreen_fragment_coord()"));
    assert!(fragment.contains("coordinate.y = viewHeight - coordinate.y;"));
    assert!(fragment.contains("vec2 vulkanic_source_fullscreen_screen_uv(vec2 image_uv)"));
    assert!(fragment.contains("ivec2 texelCoord = ivec2(gl_FragCoord.xy);"));
    assert!(fragment.contains("texelFetch(colortex0, texelCoord, 0)"));
    assert!(fragment.contains(
        "vec2 sourceScreen = vulkanic_source_fullscreen_fragment_coord().xy / vec2(1.0, viewHeight);"
    ));
    assert!(fragment.contains("texelFetch(colortex0, texelCoord, 0)"));
    assert!(fragment.contains(
        "vec4 screenPos = vec4(vulkanic_source_fullscreen_screen_uv(texCoord), 0.5, 1.0);"
    ));
    assert!(fragment.contains(
        "vec4 screenPosDH = vec4(vulkanic_source_fullscreen_screen_uv(texCoord), 0.75, 1.0);"
    ));
    assert!(fragment.contains(
        "vec4 screenPos1 = vec4(vulkanic_source_fullscreen_screen_uv(texCoord), 0.25, 1.0);"
    ));
    assert!(fragment.contains(
        "vec4 screenPos1DH = vec4(vulkanic_source_fullscreen_screen_uv(texCoord), 0.125, 1.0);"
    ));
    assert!(fragment.contains(
        "gbufferProjectionInverse * (vec4(vulkanic_source_fullscreen_screen_uv(texCoord), 0.5, 1.0)"
    ));
    assert!(fragment.contains(
        "texture(noisetex, vulkanic_source_fullscreen_screen_uv(texCoord) * vec2(1280.0, 720.0) / 128.0)"
    ));
    assert!(fragment.contains(
        "prvCoord = vulkanic_source_fullscreen_screen_uv(Reprojection(viewPos1));"
    ));
    assert!(
        fragment.find("uniform float viewHeight;")
            < fragment.find("vec4 vulkanic_source_fullscreen_fragment_coord()"),
        "the source viewport uniform must be declared before the coordinate helper"
    );
}

#[test]
fn fullscreen_source_composite7_fxaa_probe_only_suppresses_the_targeted_call() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let prior = std::env::var_os("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "composite7-without-fxaa",
    );

    let mut composite7 = "void main() { vec3 color = vec3(1.0); FXAA311(color); }".to_string();
    apply_selected_source_fullscreen_probe(&mut composite7, &[], "world0/composite7.fsh")
        .unwrap();
    assert!(!composite7.contains("FXAA311(color);"));
    assert!(composite7.contains("FXAA call suppressed"));

    let mut unrelated = "void main() { vec3 color = vec3(1.0); FXAA311(color); }".to_string();
    apply_selected_source_fullscreen_probe(&mut unrelated, &[], "world0/composite6.fsh")
        .unwrap();
    assert!(unrelated.contains("FXAA311(color);"));

    match prior {
        Some(value) => std::env::set_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE", value),
        None => std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE"),
    }
}

#[test]
fn fullscreen_source_composite5_fog_probe_exposes_depth_and_distance_only_for_target_stage() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let prior = std::env::var_os("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE");
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE",
        "composite5-fog-inputs",
    );
    let outputs = [FullscreenSourceFragmentOutput {
        source_location: 0,
        source_slot: 3,
        role: TerrainSourceResourceRole::ShaderPackColor("translucent_final".to_string()),
        semantic_name: "out_vulkanic_source_color_translucent_final".to_string(),
    }];
    let mut composite5 = "void main() { float z0 = texture(depthtex0, texCoord).r; float lViewPos = 4.0; vec3 color = vec3(1.0); out_vulkanic_source_color_translucent_final = vec4(color, 1.0); }".to_string();
    apply_selected_source_fullscreen_probe(&mut composite5, &outputs, "world0/composite5.fsh")
        .unwrap();
    assert!(
        composite5.contains("vec3(z0, clamp(lViewPos / max(far, 1.0), 0.0, 1.0), texCoord.y)")
    );

    let mut unrelated = composite5.clone();
    apply_selected_source_fullscreen_probe(&mut unrelated, &outputs, "world0/composite6.fsh")
        .unwrap();
    assert_eq!(composite5, unrelated);

    match prior {
        Some(value) => std::env::set_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE", value),
        None => std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_PROBE"),
    }
}

#[test]
fn fullscreen_source_far_world_raster_semantics_preserve_source_sky_depth() {
    let pack = ShaderPackSource::new(
        "fullscreen-sky-source",
        5,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_skybasic.vsh",
                "#version 130\nvoid main() { gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_skybasic.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0] = vec4(1.0); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/gbuffers_skybasic.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/gbuffers_skybasic.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lowered = lower_fullscreen_source_pair_with_raster_primitive(
        &vertex,
        &fragment,
        &bindings,
        FullscreenSourceRasterPrimitive::VanillaSkyDisc,
    )
    .unwrap();
    assert_eq!(
        lowered.raster_primitive(),
        FullscreenSourceRasterPrimitive::VanillaSkyDisc
    );
    assert!(lowered
        .uniform_contract()
        .declarations()
        .iter()
        .any(|declaration| declaration == "mat4 gbufferModelView;"));
    assert!(lowered
        .uniform_contract()
        .declarations()
        .iter()
        .any(|declaration| declaration == "mat4 gbufferProjection;"));
    let names = lowered
        .uniform_contract()
        .declarations()
        .iter()
        .map(|declaration| uniform_name(declaration).unwrap())
        .collect::<Vec<_>>();
    assert!(names.windows(2).all(|pair| pair[0] < pair[1]));
    assert!(lowered
        .vertex()
        .source()
        .contains("Expanded eight-wedge form of Minecraft's top SkyRenderer disc"));
    assert!(lowered.vertex().source().contains(
        "gbufferProjection * gbufferModelView * vulkanic_source_fullscreen_sky_position()"
    ));
    assert!(lowered
        .vertex()
        .source()
        .contains("#define vulkanic_source_fullscreen_vertex_color vec4(skyColor, 1.0)"));
    assert!(!lowered
        .vertex()
        .source()
        .contains("const vec4 vulkanic_source_fullscreen_vertex_color = vec4(1.0);"));
    assert!(lowered.vertex().source().contains(
        "#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH\n    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n#endif"
    ));
}

#[test]
fn fullscreen_source_lowers_legacy_fog_to_named_semantic_inputs() {
    let pack = ShaderPackSource::new(
        "fullscreen-legacy-fog",
        6,
        vec![
            ShaderSourceFile::new(
                "world0/deferred.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred.fsh",
                "#version 130\n/* DRAWBUFFERS:0 */\nin vec2 uv;\nvoid main() { float fog = gl_Fog.start * gl_Fog.scale + gl_Fog.color.a; gl_FragData[0] = vec4(uv, fog, 1.0); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lowered = lower_fullscreen_source_pair(&vertex, &fragment, &bindings).unwrap();

    assert!(lowered.require_backend_neutral_lowering().is_ok());
    assert!(!lowered.fragment().source().contains("gl_Fog"));
    assert!(lowered
        .fragment()
        .source()
        .contains("VulkanicSourceLegacyFogParameters"));
    assert!(
        lowered
            .fragment()
            .source()
            .find("vulkanic_source_fog_parameter_color;")
            < lowered
                .fragment()
                .source()
                .find("VulkanicSourceLegacyFogParameters")
    );
    assert!(lowered
        .fragment()
        .source()
        .contains("vulkanic_source_fog().start"));
    assert_eq!(
        vec![
            "vulkanic_source_fog_environmental_end",
            "vulkanic_source_fog_environmental_start",
            "vulkanic_source_fog_parameter_color",
        ],
        lowered
            .uniform_contract()
            .fields()
            .iter()
            .map(|field| field.name())
            .collect::<Vec<_>>()
    );
}

#[test]
fn fullscreen_source_pair_rejects_legacy_output_without_semantic_color_role() {
    let pack = ShaderPackSource::new(
        "fullscreen-source-unmapped-output",
        5,
        vec![
            ShaderSourceFile::new(
                "world0/deferred.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred.fsh",
                "#version 130\n/* DRAWBUFFERS:01 */\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); gl_FragData[1] = vec4(uv, 0.0, 1.0); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    assert!(lower_fullscreen_source_pair(&vertex, &fragment, &bindings)
        .unwrap_err()
        .to_string()
        .contains("has no declared semantic color role"));
}

#[test]
fn fullscreen_source_uses_drawbuffers_slot_not_glsl_output_location() {
    let pack = ShaderPackSource::new(
        "fullscreen-source-drawbuffers-remap",
        5,
        vec![
            ShaderSourceFile::new(
                "world0/deferred.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred.fsh",
                "#version 130\n/* DRAWBUFFERS:3 */\nin vec2 uv;\nvoid main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex3=shader_pack_color:translucent_final\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/deferred.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let lowered = lower_fullscreen_source_pair(&vertex, &fragment, &bindings).unwrap();
    let output = &lowered.fragment().outputs()[0];
    assert_eq!(0, output.source_location());
    assert_eq!(3, output.source_slot());
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("translucent_final".to_string()),
        output.role()
    );
}

#[test]
fn shadow_lowering_externalizes_owned_uimage3d_writes() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "shadow.vsh",
                "#version 130\nwriteonly uniform uimage3D voxel_img;\nuniform usampler3D voxel_sampler;\nvoid main() { imageStore(voxel_img, ivec3(0), uvec4(1)); gl_Position = gl_Vertex; }",
            ),
            ShaderSourceFile::new(
                "shadow.fsh",
                "#version 130\nvoid main() { gl_FragData[0] = vec4(1.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "shadow.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "shadow.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_shadow_source_pair(&vertex, &fragment).unwrap();
    assert!(!lowered.vertex().source().contains("voxel_img"));
    assert!(!lowered.vertex().source().contains("imageStore"));
    assert!(lowered
        .opaque_resource_contract()
        .active_resources()
        .all(|resource| resource.kind() != TerrainSourceOpaqueResourceKind::StorageImage));
}

#[test]
fn terrain_lowering_externalizes_owned_uimage3d_writes() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_terrain.vsh",
                "#version 130\nattribute vec4 mc_Entity;\nwriteonly uniform uimage3D voxel_img;\nvoid UpdateVoxelMap() { imageStore(voxel_img, ivec3(0), uvec4(1)); }\nvoid main() { UpdateVoxelMap(); gl_Position = gl_Vertex; }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_terrain.fsh",
                "#version 130\nvoid main() { gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(0.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/gbuffers_terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "world0/gbuffers_terrain.fsh",
        defines: &[],
    })
    .unwrap();

    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert!(!lowered.vertex().source().contains("voxel_img"));
    assert!(!lowered.vertex().source().contains("imageStore"));
}

#[test]
fn shadow_lowering_refuses_to_drop_puddle_storage_without_an_owned_writer() {
    let source = artifact(
        "#version 130\nwriteonly uniform uimage2D puddle_img;\nvoid main() { imageStore(puddle_img, ivec2(0), uvec4(10)); gl_Position = gl_Vertex; }",
    );
    let error = externalize_owned_semantic_storage_writes(
        &source,
        &TerrainSourceResourceBindings::default(),
    )
    .unwrap_err()
    .to_string();
    assert!(error.contains("puddle_img"));
    assert!(error.contains("explicit Rust semantic writer"));
}

#[test]
fn shadow_lowering_externalizes_declared_puddle_storage_and_keeps_its_role() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "shadow.vsh",
                "#version 130\nwriteonly uniform uimage2D puddle_img;\nvoid main() { imageStore(puddle_img, ivec2(0), uvec4(10)); gl_Position = gl_Vertex; }",
            ),
            ShaderSourceFile::new(
                "shadow.fsh",
                "#version 130\nvoid main() { gl_FragData[0] = vec4(1.0); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "puddle_img=puddle_occupancy\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "shadow.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "shadow.fsh",
        defines: &[],
    })
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&pack).unwrap();

    let lowered =
        lower_shadow_source_pair_with_owned_storage(&vertex, &fragment, &bindings).unwrap();
    assert!(!lowered.vertex().source().contains("puddle_img"));
    assert!(!lowered.vertex().source().contains("imageStore"));
    assert_eq!(
        &[TerrainSourceResourceRole::PuddleOccupancy],
        lowered.owned_storage_roles()
    );
}

#[test]
fn shadow_lowering_rejects_unmapped_storage_write_targets() {
    let source = artifact(
        "#version 130\nwriteonly uniform uimage3D voxel_img;\nvoid main() { imageStore(other_image, ivec3(0), uvec4(1)); gl_Position = gl_Vertex; }",
    );
    let error = externalize_owned_semantic_storage_writes(
        &source,
        &TerrainSourceResourceBindings::default(),
    )
    .unwrap_err()
    .to_string();
    assert!(error.contains("has no Rust-owned semantic writer"));
}

#[test]
fn rejects_outputs_outside_the_named_terrain_contract() {
    let error = lower_terrain_fragment_surface(&artifact(
        "#version 130\nvoid main() { gl_FragData[4] = vec4(1.0); }",
    ))
    .unwrap_err();
    assert!(error.to_string().contains("unsupported gl_FragData index"));
}

#[test]
fn lowers_known_legacy_vertex_semantics_without_assigning_java_locations() {
    let lowered = lower_terrain_vertex_surface(&artifact(
        "#version 130\nattribute vec4 mc_Entity;\nattribute vec4 mc_midTexCoord;\nattribute vec4 at_tangent;\nattribute vec3 at_midBlock;\nvarying vec2 uv;\nvoid main() { uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; vec3 n = gl_NormalMatrix * gl_Normal + at_midBlock / 64.0; float id = mc_Entity.x + mc_midTexCoord.x + at_tangent.w; gl_Position = ftransform() + gl_ModelViewMatrix * gl_Vertex + gl_ProjectionMatrix * gl_Color + vec4(n, id); }",
    ))
    .unwrap();
    assert!(lowered.source().contains("VulkanicSourceTerrainVertex"));
    assert!(lowered.source().contains("VulkanicSourceTerrainInstances"));
    assert!(lowered.source().contains("vec4 color_modulation"));
    assert!(lowered.source().contains(
        "(vulkanic_source_vertex.color * vulkanic_source_instance.color_modulation)"
    ));
    assert!(lowered.source().contains("vulkanic_source_mid_tex_coord"));
    assert!(lowered.source().contains("vulkanic_source_mid_block"));
    assert!(lowered.source().contains("vulkanic_source_model_view"));
    assert!(lowered.source().contains("out vec2 uv"));
    assert!(!lowered.source().contains("attribute vec4"));
    assert!(!lowered.source().contains("gl_MultiTexCoord0"));
    assert!(!lowered.source().contains("gl_ModelViewMatrix"));
    assert!(lowered.remaining_dialect().gaps().is_empty());
}

#[test]
fn terrain_lowering_replaces_legacy_camera_relative_world_reconstruction() {
    let lowered = lower_terrain_vertex_surface(&artifact(
        "#version 130\nvoid main() { vec4 position = gbufferModelViewInverse * gl_ModelViewMatrix * gl_Vertex; gl_Position = gl_ProjectionMatrix * gbufferModelView * position; }",
    ))
    .unwrap();
    assert!(!lowered.source().contains(
        "gbufferModelViewInverse * vulkanic_source_model_view * vulkanic_source_position"
    ));
    assert!(lowered
        .source()
        .contains("vulkanic_source_model_transform * vulkanic_source_position"));
}

#[test]
fn textured_material_lowering_expands_quad_triangles_from_owned_vertex_indices() {
    let vertex = artifact(
        "#version 130\nvarying vec2 uv; void main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
    );
    let fragment = artifact(
        "#version 130\nvarying vec2 uv; void main() { gl_FragData[0] = vec4(uv, 0.0, 1.0); gl_FragData[1] = vec4(1.0); gl_FragData[2] = vec4(0.0); }",
    );
    let lowered = lower_textured_material_source_pair(&vertex, &fragment).unwrap();
    let source = lowered.vertex().source();
    assert!(source.contains("vulkanic_source_textured_quad_indices[6]"));
    assert!(source.contains("(gl_VertexIndex / 6) * 4"));
    assert!(source.contains("vulkanic_source_textured_quad_indices[gl_VertexIndex % 6]"));
    assert!(
        !source.contains("VulkanicSourceTerrainInstances"),
        "textured material must not inherit terrain instance storage"
    );
}

#[test]
fn entity_lowering_applies_the_alpha_test_to_output_zero_after_main_like_iris() {
    let vertex = artifact(
        "#version 130\nvarying vec2 texCoord; varying vec4 glColor; void main() { texCoord = gl_MultiTexCoord0.xy; glColor = gl_Color; gl_Position = ftransform(); }",
    );
    // Complementary-style: alpha-0 texels skip modulation but are written.
    let fragment = artifact(
        "#version 130\nvarying vec2 texCoord; varying vec4 glColor; uniform sampler2D tex; void main() { vec4 color = texture2D(tex, texCoord); if (color.a > 0.00001) { color *= glColor; } gl_FragData[0] = color; gl_FragData[1] = color; }",
    );
    let lowered = lower_entity_source_pair(&vertex, &fragment).unwrap();
    let source = lowered.fragment().source();
    let body = source.find("void vulkanic_source_entity_main()").unwrap();
    let entry = source.rfind("void main() {").unwrap();
    let test = source
        .find("if (!(out_terrain_lit_color.a > VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF)) discard;")
        .unwrap();
    assert!(body < entry && entry < test);
    assert!(source[entry..].contains("vulkanic_source_entity_main();"));
    // Without a material cutoff define the test compiles out entirely.
    assert!(source.contains("#ifdef VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF"));
    assert!(!source.contains("#define VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF"));
}

#[test]
fn hand_lowering_composes_camera_model_view_before_copied_pose() {
    let vertex = artifact(
        "#version 130\nvarying vec2 texCoord; varying vec4 glColor; void main() { texCoord = gl_MultiTexCoord0.xy; glColor = gl_Color; gl_Position = ftransform(); }",
    );
    let fragment = artifact(
        "#version 130\nvarying vec2 texCoord; varying vec4 glColor; uniform sampler2D tex; void main() { vec4 color = texture2D(tex, texCoord); color *= glColor; gl_FragData[0] = color; }",
    );
    let lowered = lower_hand_source_pair(&vertex, &fragment).unwrap();
    assert!(lowered
        .vertex()
        .source()
        .contains("#define vulkanic_source_model_view (gbufferModelView * vulkanic_source_model_transform)"));
    assert!(lowered.vertex().source().contains(
        "mat4 vulkanic_source_hand_projection;"
    ));
    assert!(lowered.vertex().source().contains(
        "vulkanic_source_hand_projection * vulkanic_source_model_view"
    ));
    assert!(!lowered.uniform_contract().declarations.iter().any(|declaration| {
        declaration.contains("vulkanic_source_hand_projection")
    }));
}

#[test]
fn hand_lowering_rejects_unresolved_legacy_attributes_before_route_selection() {
    let vertex = artifact(
        "#version 130\nvarying vec2 texCoord; varying vec4 glColor; void main() { texCoord = gl_MultiTexCoord0.xy; glColor = gl_Color; gl_Position = ftransform(); }",
    );
    let fragment = artifact(
        "#version 130\nvarying vec2 texCoord; varying vec4 glColor; uniform sampler2D tex; void main() { vec4 color = texture2D(tex, texCoord); color *= glColor; gl_FragData[0] = color; gl_FragData[1] = color; }",
    );
    let error = lower_hand_source_pair(&vertex, &fragment)
        .unwrap()
        .require_backend_neutral_lowering()
        .unwrap_err();
    assert!(error
        .to_string()
        .contains("compatibility_vertex_attributes"));
}

#[test]
fn lowers_shadow_color_outputs_without_relabeling_them_as_terrain_gbuffer_data() {
    let lowered = lower_shadow_fragment_surface(&artifact(
        "#version 130\nuniform sampler2D tex;\nvoid main() { vec4 color = texture2D(tex, vec2(0.25)); gl_FragData[0] = color; gl_FragData[1] = vec4(color.rgb * 0.25, 1.0); }",
    ))
    .unwrap();
    assert_eq!(
        vec![
            ShadowFragmentOutput::ShadowColor,
            ShadowFragmentOutput::LightShaftColor,
        ],
        lowered.outputs()
    );
    assert!(lowered.source().contains("out_shadow_color"));
    assert!(lowered.source().contains("out_shadow_light_shaft_color"));
    assert!(!lowered.source().contains("out_terrain_lit_color"));
    assert!(!lowered.source().contains("gl_FragData"));
}

#[test]
fn lowered_source_vertex_finalizes_clip_depth_after_nested_main_logic() {
    let lowered = lower_terrain_vertex_surface(&artifact(
        "#version 130\nvoid helper() { if (true) { int ignored = 0; } }\nvoid main() { if (true) { gl_Position = gl_Vertex; } /* closing braces } are trivia */ }",
    ))
    .unwrap();
    let source = lowered.source();
    let finalizer = "#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH\n    gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n#endif";
    let finalizer_offset = source
        .find(finalizer)
        .expect("clip finalizer must be injected");
    let main_body_end = source.rfind('}').expect("lowered main has a closing brace");
    assert!(finalizer_offset < main_body_end);
    assert!(source[..finalizer_offset].contains("gl_Position = vulkanic_source_position"));
}

#[test]
fn clip_depth_finalizer_rejects_a_source_without_main() {
    let error = append_clip_depth_convention_finalizer("#version 450\nvoid helper() {}")
        .unwrap_err()
        .to_string();
    assert!(error.contains("void main() body"));
}

#[test]
fn lowers_distant_horizons_with_its_own_transform_and_color_contract() {
    let pack = ShaderPackSource::new(
        "dh-test",
        1,
        vec![
            ShaderSourceFile::new(
                "dh_terrain.vsh",
                "#version 130\nflat out int mat;\nout vec2 lmCoord;\nout vec4 glColor;\nuniform mat4 dhProjection;\nvoid main() { mat = 2; lmCoord = vec2(0.5); glColor = gl_Color; gl_Position = ftransform() + gl_ModelViewMatrix * vec4(0.0); }",
            ),
            ShaderSourceFile::new(
                "dh_terrain.fsh",
                "#version 130\nflat in int mat;\nin vec2 lmCoord;\nin vec4 glColor;\nuniform mat4 dhProjectionInverse;\nvoid main() { gl_FragData[0] = glColor * vec4(float(mat)) * (dhProjectionInverse[0][0] + lmCoord.x); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "dh_terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "dh_terrain.fsh",
        defines: &[],
    })
    .unwrap();

    let lowered = lower_distant_horizons_source_pair(&vertex, &fragment).unwrap();
    assert_eq!(
        vec![DistantHorizonsFragmentOutput::LitColor],
        lowered.fragment().outputs()
    );
    assert!(lowered
        .fragment()
        .source()
        .contains("out_distant_horizons_lit_color"));
    assert!(!lowered
        .fragment()
        .source()
        .contains("out_terrain_lit_color"));
    assert!(lowered.vertex().source().contains("dhModelView"));
    assert!(lowered.vertex().source().contains("dhProjection"));
    assert!(
        !lowered.vertex().source().contains("gbufferProjection"),
        "DH source must not inherit the near-terrain projection"
    );
    assert!(lowered
        .vertex()
        .source()
        .contains("VulkanicDistantHorizonsVertices"));
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_dh_material_id"));
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_dh_packed_lightmap_coordinates"));
    let light_coordinates = lowered
        .vertex()
        .source()
        .find("vec2 vulkanic_source_dh_packed_lightmap_coordinates")
        .expect("the lowered DH source must declare packed light coordinates");
    let light_source = &lowered.vertex().source()[light_coordinates..];
    let block_shift = light_source
        .find("data.w >> 8u")
        .expect("DH block light must occupy the first lightmap component");
    let sky_component = light_source
        .find("data.w & 0xffu")
        .expect("DH sky light must occupy the second lightmap component");
    assert!(
        block_shift < sky_component,
        "the generic DH source adapter must preserve Iris's (blockLight, skyLight) order"
    );
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_dh_micro(micro >> 4u)"));
    assert!(!lowered
        .vertex()
        .source()
        .contains("vulkanic_source_dh_micro(micro >> 2u)"));
    assert!(lowered
        .uniform_contract()
        .fields()
        .iter()
        .any(|field| field.name() == "dhProjectionInverse"));
    assert!(!lowered.vertex().source().contains("gl_ModelViewMatrix"));
    assert!(!lowered.fragment().source().contains("gl_FragData"));
}

#[test]
fn distant_horizons_fragment_probe_replaces_only_the_final_named_output() {
    let _guard = fullscreen_probe_test_lock()
        .lock()
        .unwrap_or_else(|poisoned| poisoned.into_inner());
    let source =
        "void main() { vec4 color = vec4(0.5); out_distant_horizons_lit_color = color; }";
    let mut fragment = LoweredDistantHorizonsFragmentSource {
        entry_path: "dh_terrain.fsh".to_string(),
        source: source.to_string(),
        outputs: vec![DistantHorizonsFragmentOutput::LitColor],
        remaining_dialect: analyze_glsl_text("dh_terrain.fsh", source),
    };
    std::env::set_var(
        "MATTMC_RUST_SELECTED_SOURCE_DH_FRAGMENT_PROBE",
        "constant-red",
    );
    let result = apply_selected_source_distant_horizons_fragment_probe(&mut fragment);
    std::env::remove_var("MATTMC_RUST_SELECTED_SOURCE_DH_FRAGMENT_PROBE");

    result.unwrap();
    assert!(fragment
        .source()
        .contains("out_distant_horizons_lit_color = vec4(1.0, 0.0, 0.0, 1.0)"));
    assert!(fragment.source().contains("vec4 color = vec4(0.5)"));
}

#[test]
fn lowered_distant_horizons_maps_fragment_y_without_affecting_fullscreen_stages() {
    let pack = ShaderPackSource::new(
        "dh-fragment-coordinate-test",
        1,
        vec![
            ShaderSourceFile::new(
                "dh_terrain.vsh",
                "#version 130\nflat out int mat;\nout vec2 lmCoord;\nout vec4 glColor;\nuniform mat4 dhProjection;\nvoid main() { mat = 2; lmCoord = vec2(0.5); glColor = gl_Color; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "dh_terrain.fsh",
                "#version 130\nflat in int mat;\nin vec2 lmCoord;\nin vec4 glColor;\nuniform float viewHeight;\nvoid main() { vec2 screen = gl_FragCoord.xy / vec2(1.0, viewHeight); gl_FragData[0] = glColor * vec4(screen, float(mat), 1.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "dh_terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "dh_terrain.fsh",
        defines: &[],
    })
    .unwrap();

    let lowered = lower_distant_horizons_source_pair(&vertex, &fragment).unwrap();
    let fragment = lowered.fragment().source();
    assert!(fragment.contains("vec4 vulkanic_source_world_fragment_coord()"));
    assert!(fragment.contains("coordinate.y = viewHeight - coordinate.y;"));
    assert!(fragment.contains("vulkanic_source_world_fragment_coord().xy"));
    assert!(
        fragment.find("uniform float viewHeight;")
            < fragment.find("vec4 vulkanic_source_world_fragment_coord()"),
        "the source viewport uniform must be declared before the coordinate helper"
    );
}

#[test]
fn lowered_distant_horizons_supplies_the_extent_for_fragment_coordinates() {
    let pack = ShaderPackSource::new(
        "dh-fragment-coordinate-missing-extent-test",
        1,
        vec![
            ShaderSourceFile::new(
                "dh_terrain.vsh",
                "#version 130\nflat out int mat;\nout vec2 lmCoord;\nout vec4 glColor;\nuniform mat4 dhProjection;\nvoid main() { mat = 2; lmCoord = vec2(0.5); glColor = gl_Color; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "dh_terrain.fsh",
                "#version 130\nflat in int mat;\nin vec2 lmCoord;\nin vec4 glColor;\nvoid main() { gl_FragData[0] = glColor * vec4(gl_FragCoord.xy, float(mat), 1.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "dh_terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "dh_terrain.fsh",
        defines: &[],
    })
    .unwrap();
    // Iris always provides viewHeight; the lowered contract requires it
    // for the explicit lower-left coordinate conversion.
    let lowered = lower_distant_horizons_source_pair(&vertex, &fragment).unwrap();
    assert!(lowered
        .uniform_contract()
        .fields()
        .iter()
        .any(|field| field.name() == "viewHeight"));
    assert!(lowered.fragment().source().contains("coordinate.y = viewHeight - coordinate.y;"));
}

#[test]
fn lowers_selected_complementary_dh_source_without_relabeling_it_as_near_terrain() {
    let source =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
        );
    let contract = crate::render::shaderpack::contracts::distant_horizons::derive_distant_horizons_opaque_contract(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let artifacts =
        crate::render::shaderpack::source::preprocess::preprocess_distant_horizons_sources(
            &source,
            &contract.source_stages,
        )
        .unwrap();
    let lowered =
        lower_distant_horizons_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    assert_eq!(
        vec![DistantHorizonsFragmentOutput::LitColor],
        lowered.fragment().outputs()
    );
    assert!(lowered
        .fragment()
        .source()
        .contains("out_distant_horizons_lit_color"));
    assert!(!lowered
        .fragment()
        .source()
        .contains("out_terrain_lit_color"));
    assert!(lowered.vertex().source().contains("dhModelView"));
    assert!(lowered.vertex().source().contains("dhProjection"));
    assert!(lowered
        .vertex()
        .source()
        .contains("VulkanicDistantHorizonsVertices"));
    assert!(!lowered
        .vertex()
        .source()
        .contains("VulkanicSourceTerrainVertices"));
    lowered.require_backend_neutral_lowering().unwrap();
}

#[test]
fn lowers_selected_dh_water_with_the_dh_vertex_stream_not_near_terrain() {
    let complete =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
        );
    let source = ShaderPackSource::new(
        "complete-dh-water-lowering",
        92,
        complete
            .files()
            .into_iter()
            .chain(std::iter::once(ShaderSourceFile::new(
                crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
                "DISTANT_HORIZONS=1\nSHADOW_QUALITY=-1\nFXAA_DEFINE=-1\nCOLORED_LIGHTING=0\nENTITY_SHADOWS_DEFINE=-1\nPLAYER_SHADOW=-1\nRAIN_PUDDLES=0\n",
            )))
            .collect(),
    )
    .unwrap();
    let contract = crate::render::shaderpack::contracts::distant_horizons::derive_distant_horizons_translucent_contract(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let artifacts =
        crate::render::shaderpack::source::preprocess::preprocess_distant_horizons_sources(
            &source,
            &contract.source_stages,
        )
        .unwrap();

    let lowered = lower_distant_horizons_source_pair(&artifacts.vertex, &artifacts.fragment)
        .expect("the selected DH water pair must lower through the DH stream");
    assert_eq!(
        vec![DistantHorizonsFragmentOutput::LitColor],
        lowered.fragment().outputs()
    );
    assert!(lowered
        .vertex()
        .source()
        .contains("VulkanicDistantHorizonsVertices"));
    assert!(lowered.vertex().source().contains("dhProjection"));
    assert!(lowered.fragment().source().contains("depthtex1"));
    assert!(lowered
        .fragment()
        .source()
        .contains("out_distant_horizons_lit_color"));
    assert!(!lowered.fragment().source().contains("gl_FragData"));
    lowered.require_backend_neutral_lowering().unwrap();
}

#[test]
fn lowers_the_selected_scoped_shadow_fragment_without_a_terrain_output_alias() {
    let source =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
        );
    let stages = crate::render::shaderpack::contracts::terrain::shadow_source_stages_for_scope(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let artifacts =
        crate::render::shaderpack::source::preprocess::preprocess_terrain_sources(
            &source, &stages,
        )
        .unwrap();
    let lowered = lower_shadow_fragment_surface(&artifacts.fragment).unwrap();
    assert!(lowered
        .outputs()
        .contains(&ShadowFragmentOutput::ShadowColor));
    assert!(lowered.source().contains("out_shadow_color"));
    assert!(!lowered.source().contains("out_terrain_lit_color"));
}

#[test]
fn shadow_pair_uses_shadow_transforms_and_keeps_shadow_outputs_distinct() {
    let pack = ShaderPackSource::new(
        "shadow-test",
        1,
        vec![
            ShaderSourceFile::new(
                "shadow.vsh",
                "#version 130\nout vec2 tex_coord;\nvoid main() { tex_coord = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "shadow.fsh",
                "#version 130\nin vec2 tex_coord;\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, tex_coord); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "shadow.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "shadow.fsh",
        defines: &[],
    })
    .unwrap();

    let lowered = lower_shadow_source_pair(&vertex, &fragment).unwrap();

    assert!(lowered.vertex().source().contains("shadowModelView"));
    assert!(lowered.vertex().source().contains("shadowProjection"));
    assert!(!lowered.vertex().source().contains("gbufferModelView"));
    assert!(lowered
        .fragment()
        .source()
        .contains("layout(location = 0) in vec2 tex_coord"));
    assert!(lowered.fragment().source().contains("out_shadow_color"));
    assert!(!lowered
        .fragment()
        .source()
        .contains("out_terrain_lit_color"));
    lowered.require_backend_neutral_lowering().unwrap();
}

#[test]
fn selected_scoped_shadow_pair_preserves_shadow_transform_and_output_semantics() {
    let source =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
        );
    let stages = crate::render::shaderpack::contracts::terrain::shadow_source_stages_for_scope(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let artifacts =
        crate::render::shaderpack::source::preprocess::preprocess_terrain_sources(
            &source, &stages,
        )
        .unwrap();

    let lowered = lower_shadow_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();

    assert!(lowered.vertex().source().contains("shadowModelView"));
    assert!(lowered.vertex().source().contains("shadowProjection"));
    assert!(!lowered
        .vertex()
        .source()
        .contains("#define vulkanic_source_model_view (gbufferModelView"));
    assert!(lowered
        .fragment()
        .outputs()
        .contains(&ShadowFragmentOutput::ShadowColor));
    assert!(!lowered
        .fragment()
        .source()
        .contains("out_terrain_lit_color"));
    lowered.require_backend_neutral_lowering().unwrap();
}

#[test]
fn rejects_unknown_legacy_vertex_attributes() {
    let error = lower_terrain_vertex_surface(&artifact(
        "#version 130\nattribute vec4 unmodeled_input;\nvoid main() {}",
    ))
    .unwrap_err();
    assert!(error.to_string().contains("unmodeled_input"));
}

#[test]
fn pair_lowering_uses_one_deterministic_uniform_block_for_both_stages() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform float time;\nuniform mat4 view;\nvoid main() { gl_Position = gl_Vertex + vec4(time + view[0][0]); }",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform float time;\nuniform vec3 fog_color;\nvoid main() { gl_FragData[0] = vec4(time + fog_color.x); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert_eq!(
        lowered.uniform_contract().declarations(),
        ["vec3 fog_color;", "float time;", "mat4 view;"]
    );
    assert_eq!(80, lowered.uniform_contract().std140_size());
    assert_eq!(
        vec![
            ("fog_color", TerrainSourceUniformType::Vec3, 0, 12, 0),
            ("time", TerrainSourceUniformType::Float, 12, 4, 0),
            ("view", TerrainSourceUniformType::Mat4, 16, 64, 0),
        ],
        lowered
            .uniform_contract()
            .fields()
            .iter()
            .map(|field| (
                field.name(),
                field.ty(),
                field.offset(),
                field.size(),
                field.array_stride(),
            ))
            .collect::<Vec<_>>()
    );
    let expected = uniform_block(lowered.uniform_contract());
    assert!(lowered.vertex().source().contains(&expected));
    assert!(lowered.fragment().source().contains(&expected));
}

#[test]
fn translucent_pair_lowering_keeps_its_auxiliary_output_distinct_from_normal_terrain() {
    let vertex = artifact("#version 130\nvoid main() { gl_Position = gl_Vertex; }");
    let fragment = artifact(
        "#version 130\nvoid main() { gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(0.5); gl_FragData[2] = vec4(0.25); }",
    );
    let lowered = lower_translucent_terrain_source_pair(&vertex, &fragment).unwrap();
    assert_eq!(
        &[
            TranslucentTerrainFragmentOutput::LitColor,
            TranslucentTerrainFragmentOutput::TranslucencyAuxiliary,
            TranslucentTerrainFragmentOutput::MaterialAuxiliary,
        ],
        lowered.fragment().outputs()
    );
    assert!(lowered
        .fragment()
        .source()
        .contains("out_terrain_translucency_auxiliary"));
    assert!(!lowered.fragment().source().contains("gl_FragData[1]"));
}

#[test]
fn terrain_and_translucent_fragments_preserve_lower_left_source_fragment_coordinates() {
    let vertex = artifact("#version 130\nvoid main() { gl_Position = gl_Vertex; }");
    let terrain_fragment = artifact(
        "#version 130\nuniform float viewHeight;\nvoid main() { vec2 screen = gl_FragCoord.xy / vec2(1.0, viewHeight); gl_FragData[0] = vec4(screen, 0.0, 1.0); }",
    );
    let translucent_fragment = artifact(
        "#version 130\nuniform float viewHeight;\nvoid main() { vec2 screen = gl_FragCoord.xy / vec2(1.0, viewHeight); gl_FragData[0] = vec4(screen, 0.0, 1.0); gl_FragData[1] = vec4(1.0); }",
    );

    let terrain = lower_terrain_source_pair(&vertex, &terrain_fragment).unwrap();
    let translucent =
        lower_translucent_terrain_source_pair(&vertex, &translucent_fragment).unwrap();
    for lowered in [terrain.fragment().source(), translucent.fragment().source()] {
        assert!(lowered.contains("vec4 vulkanic_source_world_fragment_coord()"));
        assert!(lowered.contains("coordinate.y = viewHeight - coordinate.y;"));
        assert!(lowered.contains("vulkanic_source_world_fragment_coord().xy"));
        assert!(
            lowered.find("uniform float viewHeight;")
                < lowered.find("vec4 vulkanic_source_world_fragment_coord()"),
            "the source extent must be declared before the coordinate helper"
        );
    }
}

#[test]
fn world_material_screen_target_sampling_uses_native_image_coordinates() {
    let source = concat!(
        "#version 130\n",
        "uniform float viewHeight;\n",
        "uniform sampler2D depthtex1;\n",
        "uniform sampler2D tex;\n",
        "void main() {\n",
        "  vec2 screen = gl_FragCoord.xy / vec2(1.0, viewHeight);\n",
        "  vec4 scene = texture2D(depthtex1, screen);\n",
        "  vec4 atlas = texture2D(tex, screen);\n",
        "  gl_FragData[0] = scene + atlas;\n",
        "}\n"
    );
    let lowered = lower_world_material_fragment_coordinates(
        replace_identifier(source, "texture2D", "texture"),
        &TerrainSourceUniformContract {
            declarations: vec!["float viewHeight;".to_string()],
            fields: vec![TerrainSourceUniformField {
                name: "viewHeight".to_string(),
                ty: TerrainSourceUniformType::Float,
                array_length: 1,
                offset: 0,
                size: 4,
                array_stride: 0,
            }],
            std140_size: 4,
        },
        &[],
    )
    .unwrap();

    assert!(lowered.contains(
        "#define vulkanic_source_sample_target_depthtex1(source_uv) texture(depthtex1, vulkanic_source_world_target_uv(source_uv))"
    ));
    assert!(lowered.contains("scene = vulkanic_source_sample_target_depthtex1( screen);"));
    assert!(lowered.contains("vec4 atlas = texture(tex, screen);"));
    assert!(lowered.contains("vec2((source_uv).x, 1.0 - (source_uv).y)"));
}

#[test]
fn terrain_fragment_coordinates_without_a_source_extent_require_view_height() {
    let vertex = artifact("#version 130\nvoid main() { gl_Position = gl_Vertex; }");
    let fragment = artifact(
        "#version 130\nvoid main() { gl_FragData[0] = vec4(gl_FragCoord.xy, 0.0, 1.0); }",
    );
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert!(lowered
        .uniform_contract()
        .fields()
        .iter()
        .any(|field| field.name() == "viewHeight"));
}

#[test]
fn paired_uniform_type_mismatch_is_rejected_before_lowering() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform float shared;\nvoid main() { gl_Position = vec4(shared); }",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform vec2 shared;\nvoid main() { gl_FragData[0] = vec4(shared, 0.0, 1.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let error = lower_terrain_source_pair(&vertex, &fragment).unwrap_err();
    assert!(error
        .to_string()
        .contains("incompatible paired declarations"));
}

#[test]
fn scalar_uniform_layout_rejects_unknown_types_and_lays_out_arrays() {
    let array = artifact(
        "#version 130\nuniform vec2 weights[3];\nvoid main() { gl_Position = vec4(weights[0], 0.0, 1.0); }",
    );
    let layout = derive_terrain_source_uniform_contract(&array, &array).unwrap();
    assert_eq!(48, layout.std140_size());
    assert_eq!(1, layout.fields().len());
    let field = &layout.fields()[0];
    assert_eq!("weights", field.name());
    assert_eq!(TerrainSourceUniformType::Vec2, field.ty());
    assert_eq!(3, field.array_length());
    assert_eq!(0, field.offset());
    assert_eq!(48, field.size());
    assert_eq!(16, field.array_stride());

    let unsupported = artifact(
        "#version 130\nuniform double invalid;\nvoid main() { gl_Position = vec4(float(invalid)); }",
    );
    let error = derive_terrain_source_uniform_contract(&unsupported, &unsupported)
        .unwrap_err()
        .to_string();
    assert!(error.contains("double"));
    assert!(error.contains("std140 semantic layout"));
}

#[test]
fn scalar_uniform_layout_packs_vec3_tail_scalars_without_losing_std140_array_stride() {
    let scalar_tail = artifact(
        "#version 130\nuniform vec3 a_position;\nuniform float b_exposure;\nuniform vec4 c_tint;\nvoid main() { gl_Position = vec4(a_position, 1.0) + vec4(b_exposure) + c_tint; }",
    );
    let layout = derive_terrain_source_uniform_contract(&scalar_tail, &scalar_tail).unwrap();
    assert_eq!(32, layout.std140_size());
    assert_eq!(3, layout.fields().len());
    assert_eq!("a_position", layout.fields()[0].name());
    assert_eq!(0, layout.fields()[0].offset());
    assert_eq!(12, layout.fields()[0].size());
    assert_eq!("b_exposure", layout.fields()[1].name());
    assert_eq!(12, layout.fields()[1].offset());
    assert_eq!("c_tint", layout.fields()[2].name());
    assert_eq!(16, layout.fields()[2].offset());

    let array = artifact(
        "#version 130\nuniform vec3 samples[2];\nvoid main() { gl_Position = vec4(samples[1], 1.0); }",
    );
    let array_layout = derive_terrain_source_uniform_contract(&array, &array).unwrap();
    let field = &array_layout.fields()[0];
    assert_eq!(32, array_layout.std140_size());
    assert_eq!(32, field.size());
    assert_eq!(16, field.array_stride());
}

#[test]
fn uniform_contract_omits_declaration_only_values_but_keeps_lowered_legacy_matrices() {
    let source = artifact(
        "#version 130\nuniform float unused;\nuniform float active;\nuniform mat4 gbufferModelView;\nuniform mat4 gbufferProjection;\n// unused must not count as a source reference\nvoid main() { gl_Position = ftransform() + vec4(active); }",
    );
    let contract = derive_terrain_source_uniform_contract(&source, &source).unwrap();
    assert_eq!(
        [
            "float active;",
            "mat4 gbufferModelView;",
            "mat4 gbufferProjection;",
        ],
        contract.declarations()
    );
    assert_eq!(144, contract.std140_size());

    let identifiers = glsl_identifiers("// ignored\nactive /* ignored too */ gbufferModelView");
    assert!(identifiers.contains("active"));
    assert!(identifiers.contains("gbufferModelView"));
    assert!(!identifiers.contains("ignored"));
}

#[test]
fn legacy_transform_lowering_rejects_conflicting_semantic_matrix_declarations() {
    let source = artifact(
        "#version 130\nuniform float gbufferModelView;\nvoid main() { gl_Position = ftransform(); }",
    );
    let error = derive_terrain_source_uniform_contract(&source, &source)
        .unwrap_err()
        .to_string();
    assert!(error.contains("gbufferModelView"));
    assert!(error.contains("legacy transform"));
}

#[test]
fn post_effect_interface_linking_matches_names_not_declaration_order() {
    let (vertex, fragment) = bind_simple_paired_varyings(
        "out vec2 first;\nout vec3 second;\n",
        "in vec3 second;\nin vec2 first;\n",
    )
    .unwrap();
    assert!(vertex.contains("layout(location = 0) out vec2 first;"));
    assert!(fragment.contains("layout(location = 0) in vec2 first;"));
    assert!(vertex.contains("layout(location = 1) out vec3 second;"));
    assert!(fragment.contains("layout(location = 1) in vec3 second;"));
    for input in [
        "in vec3 first;\n",
        "in vec2 missing;\n",
        "flat in vec2 first;\n",
    ] {
        assert!(bind_simple_paired_varyings("out vec2 first;\n", input).is_err());
    }
    assert!(bind_simple_paired_varyings("out mat4 matrix;\n", "in mat4 matrix;\n").is_err());
    assert!(
        bind_simple_paired_varyings("out vec2 values[2];\n", "in vec2 values[2];\n").is_err()
    );
    assert!(bind_simple_paired_varyings(
        "layout(location=0) out vec2 fixed;\nout vec2 other;\n",
        "in vec2 other;\n"
    )
    .is_err());
}

#[test]
fn pair_lowering_assigns_matching_varyings_stable_locations() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nflat out int material;\nout vec2 atlas_uv;\nout vec3 normal;\nvoid main() { gl_Position = gl_Vertex; }",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nflat in int material;\nin vec2 atlas_uv;\nin vec3 normal;\nvoid main() { gl_FragData[0] = vec4(float(material) + atlas_uv.x + normal.x); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert_eq!(
        lowered
            .varying_contract()
            .fields()
            .iter()
            .map(|field| (field.name(), field.location()))
            .collect::<Vec<_>>(),
        vec![("atlas_uv", 0), ("material", 1), ("normal", 2)]
    );
    assert!(lowered
        .vertex()
        .source()
        .contains("layout(location = 1) flat out int material;"));
    assert!(lowered
        .fragment()
        .source()
        .contains("layout(location = 1) flat in int material;"));
}

#[test]
fn paired_varying_mismatch_is_rejected_before_lowering() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nout vec3 normal;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nin vec2 normal;\nvoid main() { gl_FragData[0] = vec4(1.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let error = lower_terrain_source_pair(&vertex, &fragment).unwrap_err();
    assert!(error.to_string().contains("differs between vertex output"));
}

#[test]
fn pair_lowering_assigns_stable_bindings_to_opaque_resources() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D atlas;\nuniform usampler3D occupancy;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform sampler2D atlas;\nvoid main() { gl_FragData[0] = texture2D(atlas, vec2(0.0)); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert_eq!(
        lowered
            .opaque_resource_contract()
            .resources()
            .iter()
            .map(|resource| (resource.name(), resource.binding()))
            .collect::<Vec<_>>(),
        vec![("atlas", 0), ("occupancy", 1)]
    );
    assert!(lowered
        .vertex()
        .source()
        .contains("layout(set = 1, binding = 0) uniform sampler2D atlas;"));
    assert!(lowered
        .vertex()
        .source()
        .contains("layout(set = 1, binding = 1) uniform usampler3D occupancy;"));
}

#[test]
fn paired_opaque_resource_type_mismatch_is_rejected_before_lowering() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D shared;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform usampler2D shared;\nvoid main() { gl_FragData[0] = vec4(1.0); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let error = lower_terrain_source_pair(&vertex, &fragment).unwrap_err();
    assert!(error.to_string().contains("opaque resource 'shared'"));
}

#[test]
fn opaque_resources_require_exact_pack_declared_semantic_roles() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D tex;\nuniform usampler3D voxel_sampler;\nuniform sampler2D unused_global;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform sampler2D tex;\nuniform usampler3D voxel_sampler;\nuniform sampler2D unused_global;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nunused_global=noise\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let plan = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    assert_eq!(
        vec![("tex", TerrainSourceResourceRole::MaterialAtlas, 0)],
        plan.bindings()
            .iter()
            .map(|binding| (binding.resource_name(), binding.role(), binding.binding()))
            .collect::<Vec<_>>()
    );
}

#[test]
fn raw_shadow_depth_sampling_resolves_the_owned_depth_image_without_a_compare_sampler() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D shadowtex0;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform sampler2D shadowtex0;\nvoid main() { gl_FragData[0] = texture2D(shadowtex0, vec2(0.0)); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "shadowtex0=shadow_depth\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let plan = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    assert_eq!(
        TerrainSourceResourceRole::ShadowDepthRaw,
        plan.bindings()[0].role()
    );
}

#[test]
fn opaque_resource_plan_ignores_unreferenced_global_sampler_declarations() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D tex;\nuniform sampler2D unused_global;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform sampler2D tex;\nuniform sampler2D unused_global;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    let resources = lowered.opaque_resource_contract().resources();
    assert_eq!(
        vec![("tex", true), ("unused_global", false)],
        resources
            .iter()
            .map(|resource| (resource.name(), resource.active()))
            .collect::<Vec<_>>()
    );
    let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let plan = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    assert_eq!(1, plan.bindings().len());
    assert_eq!(
        vec!["tex"],
        plan.bindings()
            .iter()
            .map(|binding| binding.resource_name())
            .collect::<Vec<_>>()
    );
    assert!(lowered
        .fragment()
        .source()
        .contains("layout(set = 1, binding = 1) uniform sampler2D unused_global;"));
}

#[test]
fn opaque_resources_reject_missing_or_unused_semantic_roles() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D tex;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    assert!(lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap_err()
        .to_string()
        .contains("have no declared semantic roles: tex"));
}

#[test]
fn opaque_resources_ignore_pack_wide_declarations_owned_by_other_source_stages() {
    let pack = ShaderPackSource::new(
        "test",
        1,
        vec![
            ShaderSourceFile::new(
                "terrain.vsh",
                "#version 130\nuniform sampler2D tex;\nvoid main() {}",
            ),
            ShaderSourceFile::new(
                "terrain.fsh",
                "#version 130\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, vec2(0.0)); }",
            ),
            ShaderSourceFile::new(
                crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nabsent=noise\n",
            ),
        ],
    )
    .unwrap();
    let vertex = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.vsh",
        defines: &[],
    })
    .unwrap();
    let fragment = preprocess_artifact(PreprocessInput {
        source: &pack,
        entry: "terrain.fsh",
        defines: &[],
    })
    .unwrap();
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&pack).unwrap();
    let plan = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    assert_eq!(
        vec!["tex"],
        plan.bindings()
            .iter()
            .map(|binding| binding.resource_name())
            .collect::<Vec<_>>()
    );
}

#[test]
fn lowered_distant_horizons_positions_do_not_apply_dimension_world_y_offset_twice() {
    assert!(!DISTANT_HORIZONS_VERTEX_SEMANTIC_PREAMBLE
        .contains("+ vec3(0.0, vulkanic_source_dh_column_origin_and_world_y.w, 0.0)"));
    assert!(DISTANT_HORIZONS_VERTEX_SEMANTIC_PREAMBLE.contains(
        "gl_VertexIndex + int(vulkanic_source_dh_model_offset_and_reserved.w)"
    ));
}

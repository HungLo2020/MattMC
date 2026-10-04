use crate::render::shaderpack::programs::*;
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::shaderpack::contracts::cloud::{
    derive_cloud_pass_contract, lower_cloud_source_pair,
};
use crate::render::shaderpack::contracts::distant_horizons::{
    derive_distant_horizons_opaque_contract, derive_distant_horizons_translucent_contract,
    DistantHorizonsPassKind,
};
use crate::render::shaderpack::contracts::entity::{
    bind_entity_source_resources, derive_entity_contract, lower_entity_source_pair,
};
use crate::render::shaderpack::lowering::{
    lower_distant_horizons_source_pair, lower_fullscreen_source_pair, lower_shadow_source_pair,
    lower_terrain_source_pair, lower_translucent_terrain_source_pair,
};
use crate::render::shaderpack::source::preprocess::{
    complete_bundled_pack_source_for_test, preprocess_distant_horizons_sources,
    preprocess_source_stage_pair, preprocess_terrain_sources,
};
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
use crate::render::shaderpack::contracts::terrain::{
    derive_complementary_terrain_contract, derive_complementary_translucent_terrain_contract,
    TerrainProgramScope, TerrainSourceStage, TerrainSourceStages, TerrainTranslucentBlend,
};
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet,
    TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
    TerrainSourceResourceBindings, TerrainSourceResourceRole,
    TerrainSourceSampledResourceShape, TERRAIN_RESOURCE_BINDINGS_PATH,
};

mod single_color;
mod terrain_alpha;

#[test]
fn entity_outline_shader_contract_preserves_outline_color_and_texture_silhouette() {
    assert!(MINIMAL_ENTITY_OUTLINE_VERTEX.contains("WorldMeshVertices"));
    assert!(MINIMAL_ENTITY_OUTLINE_VERTEX.contains("v_outline_color = instance.color"));
    assert!(MINIMAL_ENTITY_OUTLINE_FRAGMENT.contains("out_outline_color = v_outline_color"));
    assert!(MINIMAL_ENTITY_OUTLINE_FRAGMENT.contains("v_outline_uv).a == 0.0) discard"));
    assert!(MINIMAL_ENTITY_OUTLINE_VERTEX.contains("vec4 fog_color_and_environmental_start;"));
    assert!(MINIMAL_ENTITY_OUTLINE_VERTEX.contains("vec4 fog_ranges;"));
}

#[test]
fn entity_outline_fullscreen_contracts_use_explicit_vulkan_bindings() {
    assert!(MINIMAL_ENTITY_OUTLINE_FULLSCREEN_VERTEX.contains("gl_VertexIndex"));
    assert!(MINIMAL_ENTITY_OUTLINE_SOBEL_FRAGMENT.contains("binding = 0"));
    assert!(MINIMAL_ENTITY_OUTLINE_SOBEL_FRAGMENT.contains("textureSize"));
    assert!(MINIMAL_ENTITY_OUTLINE_BLUR_FRAGMENT.contains("binding = 2"));
    assert!(MINIMAL_ENTITY_OUTLINE_BLIT_FRAGMENT.contains("ColorModulate"));
    for source in [
        MINIMAL_ENTITY_OUTLINE_FULLSCREEN_VERTEX,
        MINIMAL_ENTITY_OUTLINE_SOBEL_FRAGMENT,
        MINIMAL_ENTITY_OUTLINE_BLUR_FRAGMENT,
        MINIMAL_ENTITY_OUTLINE_BLIT_FRAGMENT,
    ] {
        assert!(source.starts_with("#version 450"));
    }
}

#[test]
fn distant_horizons_direct_composite_contract_owns_color_depth_and_fog_inputs() {
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_VERTEX.contains("gl_VertexIndex"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("DhColorTexture"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("DhDepthTexture"));
    assert!(
        MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("inverse_combined_matrix")
    );
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("camera_position"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("dh_fog_factor(world.xyz, vec3(0.0), camera_position.y)"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("dh_fog_factor"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("dh_fog_parameters[4].w < -4.5"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("dh_has_coverage ? 1.0 : 0.0"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("if (depth >= 1.0 && dh_color.a <= 0.0) discard;"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("float resolved_alpha = fog + dh_color.a * (1.0 - fog);"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains(
        "out_color = vec4(mix(dh_color.rgb, fog_color_and_alpha.rgb, fog), resolved_alpha);"
    ));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("VanillaColorTexture"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT.contains("inverse_vanilla_matrix"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .contains("smoothstep(fade_parameters.x, fade_parameters.y, vanilla_distance)"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .contains("dh_position.y > fade_parameters.w"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .contains("fade_parameters.z > 2.5 && fade_parameters.z < 3.5"));
    let lod_only_branch = MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .find("fade_parameters.z > 2.5 && fade_parameters.z < 3.5")
        .expect("LOD-only branch");
    let ordinary_unwritten_fallback = MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .find("if (!dh_has_coverage) dh_color = combined_color;")
        .expect("ordinary unwritten-DH fallback");
    assert!(
        lod_only_branch < ordinary_unwritten_fallback,
        "Frozen returns the private DH target in LOD-only mode before the ordinary unwritten-pixel fallback"
    );
    assert!(
        MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT.contains("dh_fog_parameters[4].w < -5.5")
    );
    assert!(
        MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT.contains("vec4(combined_color.rgb, 0.0)")
    );
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT.contains("vanilla_depth >= 1.0"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT.contains("DhResolvedColorTexture"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .contains("bool dh_has_coverage = dh_color.a > 0.0;"));
    assert!(!MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT
        .contains("dh_depth < 1.0 || dh_color.a > 0.0"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_APPLY_FRAGMENT
        .contains("if (dh_depth >= 1.0 && dh_color.a <= 0.0) discard;"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("fog = max(fog, dh_color.a);"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("fade_policy"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("far_clip_distance"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("dh_has_coverage"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("dh_color.a > 0.0"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("smoothstep(\n            far_clip_distance * 0.9"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("dh_color = mix(vanilla_color, dh_color, far_fade);"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("if (dh_fog_parameters[4].x < 0.5) {\n        out_color = dh_color;"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("discard"));
}

#[test]
fn distant_horizons_fog_reconstruction_keeps_source_and_vulkan_depth_conventions_explicit() {
    // The copied DH combined matrix is source/OpenGL-style. The Rust LOD
    // vertex lowers its clip depth into Vulkan's zero-to-one attachment,
    // while the private compositor converts the sampled depth back before
    // multiplying by the inverse source matrix.
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX
        .contains("clip.z = clip.z * 0.5 + clip.w * 0.5;"));
    assert!(MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("depth * 2.0 - 1.0"));
    assert!(
        MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT.contains("world.xyz /= world_w;")
    );
    assert!(!MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT
        .contains("world /= max(abs(world.w)"));
}

#[test]
fn minimal_distant_horizons_streams_follow_horizontal_compact_micro_contract() {
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX
        .contains("vec3 local = base_local + vec3(micro_x, 0.0, micro_z);"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX
        .contains("vec3 base_local = vec3(local_x, local_y, local_z);"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX
        .contains("gl_VertexIndex + int(model_offset_and_reserved.w)"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX
        .contains("vec3(vertex.micro_x, 0.0, vertex.micro_z)"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX
        .contains("vec3 base_local = vec3(vertex.local_x, vertex.local_y, vertex.local_z);"));
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX,
    ] {
        assert!(source.contains("vec3 world = base_local + column_origin_and_world_y.xyz;"));
        assert!(source.contains("v_source_position = base_local;"));
        assert!(!source.contains("v_dh_fade"));
        assert!(source.contains("(flags_and_noise.w & 2u) == 0u"));
        assert!(source.contains("(flags_and_noise.w & 1u) != 0u"));
    }
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT
        .contains("mix(noisy_material, fog_color_and_alpha.rgb, fog), 1.0"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT
        .contains("(flags_and_noise.w & 4u) != 0u"));
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_SOURCE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT,
    ] {
        assert!(source.contains("dh_apply_noise"));
        assert!(source.contains("(flags_and_noise.x & 4u)"));
        // Frozen flat_shaded.frag derives the noise offset from the
        // interpolated raw position derivatives, rather than the packed
        // face-normal metadata used for lighting/G-buffer output.
        assert!(source
            .contains("normalize(cross(dFdy(v_source_position), dFdx(v_source_position)))"));
    }
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT
        .contains("vec4 dh_fog_parameters[5];"));
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT,
    ] {
        assert!(source.contains("float dh_fog_factor(vec3 world_position)"));
        assert!(source.contains("float dh_fog = dh_fog_factor(v_world_position);"));
        assert!(source.contains("float fog = dh_fog >= 0.0 ? dh_fog : 0.0;"));
    }
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT
        .contains("float dh_fade = dh_fragment_fade();"));
    assert!(
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT
            .contains("fog), source_alpha")
    );
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_SOURCE_FRAGMENT,
    ] {
        assert!(source.contains("(flags_and_noise.w & 256u) != 0u"));
        assert!(source.contains(
            "textureLod(sampler2D(TerrainAtlasColor, TerrainAtlasSampler), atlas_uv, 0.0)"
        ));
    }
    assert!(
        !MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT
            .contains("atlas_color.a * v_dh_fade")
    );
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_SOURCE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT,
    ] {
        // Lightmap sampling happens in the matching vertex stage so the
        // result is interpolated exactly as Frozen's standard.vert does.
        assert!(source.contains("v_light_color"));
        assert!(!source.contains("vec2 light_uv = (vec2(v_light.y, v_light.x)"));
    }
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX,
    ] {
        assert!(
            source.contains("layout(set = 1, binding = 0) uniform texture2D LightmapTexture")
                || source
                    .contains("layout(set = 1, binding = 2) uniform texture2D LightmapTexture")
        );
        assert!(source.contains("v_light_color = texture(sampler2D(LightmapTexture, LightmapSampler), light_uv).rgb;"));
        assert!(
            source.contains("float(vertex.data.w & 0x0fu)")
                || source.contains("float(vertex.light_normal_pad & 0x0fu)")
        );
        // Frozen OpenGL keeps low skylight in the dark lightmap rows. The
        // Java Vulkan-only fold would brighten submerged and covered faces.
        assert!(!source.contains("light_sky_uv = max(light_sky_uv, 1.0 - light_sky_uv);"));
    }
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX
        .contains("float((vertex.data.w >> 8u) & 0x0fu)"));
    assert!(!MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX
        .contains("float((vertex.data.w >> 4u) & 0x0fu)"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX
        .contains("float((vertex.light_normal_pad >> 8u) & 0x0fu)"));
    assert!(!MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX
        .contains("float((vertex.light_normal_pad >> 4u) & 0x0fu)"));
    assert!(MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT
        .contains("out_color = vec4(shaded, v_color.a);"));
    assert!(!MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT
        .contains("out_color = vec4(shaded, v_color.a * dh_fade);"));
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT,
    ] {
        // DH's standard.vert multiplies all copied vertex colors by the
        // lightmap before flat_shaded.frag, including water materials.
        assert!(source.contains("vec3 material_color = v_color.rgb;"));
        assert!(source.contains("v_light_color"));
        assert!(!source.contains("v_material == 12u ? v_color.rgb"));
    }
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT,
    ] {
        assert!(source.contains("(flags_and_noise.x & 2u) != 0u"));
        // Bundled DH flat_shaded.frag adds only 0.001 after normalizing
        // the Bayer value; retaining the old +0.5/16 changes transition
        // coverage and is visibly different at the LOD boundary.
        assert!(source.contains("[dither_index] / 16.0) + 0.001"));
        assert!(source.contains("if (dh_fade <= dither_threshold) discard;"));
        assert!(source.contains("else if (dh_fade <= 0.0)"));
    }
}

#[test]
fn distant_horizons_deferred_world_position_matches_shadow_domain() {
    // The shared deferred lighting pass decodes G-buffer positions with
    // the Rust-owned 64-block shadow range. Both reduced and exact DH
    // opaque writers must publish that same normalized attachment ABI.
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT,
    ] {
        assert!(source.contains("v_world_position / 64.0 * 0.5 + 0.5"));
        assert!(!source.contains("v_world_position / 1024.0 * 0.5 + 0.5"));
    }
}

#[test]
fn distant_horizons_black_materials_are_not_treated_as_missing_coverage() {
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT,
        MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT,
    ] {
        // DH's UNKNOWN material category (zero) is still a valid source
        // category; coverage comes from the copied vertex stream rather
        // than a guessed category sentinel.
        assert!(!source.contains("v_material == 0u"));
        assert!(!source.contains("max(max(v_color.r, v_color.g), v_color.b) < 0.003"));
    }
}

#[test]
fn distant_horizons_streams_do_not_apply_the_dimension_world_y_offset_twice() {
    for source in [
        MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX,
        MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX,
    ] {
        assert!(
            !source.contains("+ vec3(0.0, column_origin_and_world_y.w, 0.0)"),
            "the DH model offset already carries the copied column Y"
        );
    }
}

fn paired_source() -> ShaderPackSource {
    ShaderPackSource::new(
        "lowered-source-test",
        9,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap()
}

fn paired_entity_source() -> ShaderPackSource {
    ShaderPackSource::new(
        "lowered-entity-source-test",
        11,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_entities.vsh",
                "#version 130\nvoid main() { vec4 p = gl_Vertex; vec2 light = GetLightMapCoordinates(); vec3 normal = gl_Normal; vec4 color = gl_Color; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_entities.fsh",
                "#version 130\nuniform sampler2D tex;\nuniform int entityId;\nuniform vec4 entityColor;\nvoid DoLighting() {}\nvoid main() { vec4 color = texture2D(tex, texCoord); color *= glColor; color.rgb = mix(color.rgb, entityColor.rgb, entityColor.a); DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; /* DRAWBUFFERS:06 */ }",
            ),
            ShaderSourceFile::new("entity.properties", "entity.50076=boat\n"),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap()
}

fn paired_shadow_source() -> ShaderPackSource {
    ShaderPackSource::new(
        "lowered-shadow-source-test",
        10,
        vec![
            ShaderSourceFile::new(
                "shadow.vsh",
                "#version 130\nout vec2 texCoord;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "shadow.fsh",
                "#version 130\nin vec2 texCoord;\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, texCoord); }",
            ),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap()
}

fn paired_translucent_source() -> ShaderPackSource {
    ShaderPackSource::new(
        "lowered-translucent-source-test",
        12,
        vec![
            ShaderSourceFile::new(
                "gbuffers_water.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_water.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nuniform sampler2D tex;\nvoid DoLighting() {}\nvoid DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n/* DRAWBUFFERS:03 */\nvoid main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0); vec3 viewPos = vec3(0.0); vec3 playerPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0); DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
            ShaderSourceFile::new(
                "shaders.properties",
                "alphaTest.gbuffers_water=GREATER 0.0001\nblend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n",
            ),
            ShaderSourceFile::new("block.properties", "block.32000=minecraft:water\n"),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap()
}

fn paired_cloud_source() -> ShaderPackSource {
    ShaderPackSource::new(
        "lowered-cloud-source-test",
        13,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_clouds.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nvoid main() { texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; glColor = gl_Color; gl_Position = gbufferModelView * gl_Vertex; }",
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_clouds.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nuniform sampler2D tex;\n/* DRAWBUFFERS:063 */\nvoid main() { vec4 color = texture2D(tex, texCoord) * glColor; gl_FragData[0] = color; gl_FragData[1] = vec4(0.0); gl_FragData[2] = vec4(1.0); }",
            ),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap()
}

#[test]
fn minimal_deferred_pass_does_not_relight_already_lit_terrain_color() {
    // The terrain material contract names attachment zero terrain_lit_color:
    // Java/Sodium has already supplied its directional shade and packed
    // light through the vertex color. Applying another synthetic normal
    // term here made valid atlas regions appear as the wrong dark block.
    assert!(MINIMAL_DEFERRED_LIGHTING_FRAGMENT
        .contains("out_color = vec4(albedo.rgb * shadow_factor, albedo.a);"));
    assert!(!MINIMAL_DEFERRED_LIGHTING_FRAGMENT.contains("float face ="));
    assert!(!MINIMAL_DEFERRED_LIGHTING_FRAGMENT.contains("float light ="));
}

#[test]
fn direct_terrain_vertex_has_an_explicit_modelpart_diffuse_branch() {
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("(material_semantics & 2u) != 0u"));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("LIGHT0_DIRECTION"));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("NETHER_LIGHT1_DIRECTION"));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("v_color.rgb *= diffuse"));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("(material_semantics & 1u) != 0u"));
    assert!(
        MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("transpose(inverse(mat3(instance.model)))")
    );
}

#[test]
fn vanilla_fog_uses_sodium_cylindrical_distance_once_after_deferred_lighting() {
    // Frozen Java OpenGL's Sodium shader resolves its fog factor from the
    // interpolated spherical/cylindrical vertex distances before the
    // model-view transform. The deferred Rust route transports that
    // immutable factor through the explicit G-buffer and applies it once
    // after deferred lighting.
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("vec3 fog_position = world.xyz;"));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("v_fog_distances = vec2("));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX
        .contains("max(length(fog_position.xz), abs(fog_position.y))"));
    assert!(!MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("view_position"));
    assert!(!MINIMAL_TERRAIN_MATERIAL_FRAGMENT.contains("v_fog_factor"));
    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.contains(
        "frozen_linear_fog_value(v_fog_distances.x, v_fog_color_and_environmental_start.w, v_fog_ranges.x)"
    ));
    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.contains(
        "frozen_linear_fog_value(v_fog_distances.y, v_fog_ranges.y, v_fog_ranges.z)"
    ));
    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT
        .contains("clamp(fog_value * v_fog_ranges.w, 0.0, 1.0)"));
    assert!(!MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.contains("v_fog_factor"));
    for source in [
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT,
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT,
    ] {
        assert!(source.contains("const float FROZEN_FOG_DISABLED_SENTINEL_THRESHOLD = 1.0e12;"));
        assert!(source.contains("distance != distance"));
        assert!(source.contains("return distance > start ? 1.0 : 0.0;"));
        assert!(source.contains("frozen_fog_range_disabled(start, end)"));
    }
    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT.contains(
        "out_terrain_view_space_normal = vec4(n, max(environmental_fog, render_distance_fog));"
    ));
    assert!(MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT.contains("uniform texture2D NormalTex"));
    assert!(MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT
        .contains("texture(sampler2D(NormalTex, Samp0), v_uv).a"));
    assert!(MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT
        .contains("clamp(fog_factor * fog_ranges.w, 0.0, 1.0)"));
    assert!(!MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT.contains("fog_factor * color.a"));
}

#[test]
fn direct_terrain_preserves_the_explicit_atlas_or_local_uv_semantic() {
    // Atlas coordinates are independent of diffuse and lightmap semantics.
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX
        .contains("(material_semantics & 1u) != 0u\n        ? vertex.shader_data.xy"));
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX
        .contains(": vec2(vertex.position_uv.w, vertex.color_uv.w);"));
    assert!(!MINIMAL_TERRAIN_MATERIAL_VERTEX.contains("instance.material.w > 0.5"));
}

#[test]
fn model_lightmap_contracts_do_not_reuse_sodium_boundary_sampling() {
    let source = minimal_direct_terrain_vertex_source();
    assert!(source.contains("(material_semantics & 8u) != 0u"));
    assert!(source.contains("light_uv += vec2(0.5 / 16.0)"));
    assert!(source.contains("(material_semantics & 16u) != 0u"));
    assert!(source
        .contains("texelFetch(sampler2D(LightmapTexture, LightmapSampler), light_texel, 0)"));
}

#[test]
fn builtin_material_programs_sample_the_explicit_vanilla_lightmap() {
    for source in [MINIMAL_TERRAIN_MATERIAL_VERTEX] {
        assert!(
            source.contains("layout(set = 1, binding = 0) uniform texture2D LightmapTexture")
        );
        assert!(source.contains("layout(set = 1, binding = 1) uniform sampler LightmapSampler"));
        assert!(source.contains(
            "clamp(light_coordinates, vec2(0.0), vec2(255.0 / 240.0)) * (15.0 / 16.0);"
        ));
        assert!(
            source.contains("texture(sampler2D(LightmapTexture, LightmapSampler), light_uv)")
        );
    }
    assert!(!MINIMAL_TERRAIN_MATERIAL_FRAGMENT.contains("LightmapTexture"));
}

#[test]
fn direct_vanilla_terrain_matches_frozen_sodium_lightmap_vertex_sampling() {
    let vertex = minimal_direct_terrain_vertex_source();
    // Frozen OpenGL Sodium `block_layer_opaque.vsh` samples its linear
    // LightTexture directly at `a_LightAndData.xy / 256`. The semantic
    // stream holds each full byte as byte / 240, preserving fractional
    // levels as well as the usual level * 16 coordinates.
    // Sampling here—not
    // the fragment—
    // also preserves Frozen's interpolation of already-lit vertex color.
    assert!(vertex.contains("layout(set = 1, binding = 0) uniform texture2D LightmapTexture"));
    assert!(vertex
        .contains("clamp(light_coordinates, vec2(0.0), vec2(255.0 / 240.0)) * (15.0 / 16.0);"));
    assert!(vertex.contains(
        "v_color = vec4(vertex.color_uv.rgb, vertex.normal_light.w) * instance.color"
    ));
    assert!(vertex.contains("texture(sampler2D(LightmapTexture, LightmapSampler), light_uv)"));
    // Frozen carries the blend alpha independently in `a_Color`; it never
    // multiplies the AO-baked RGB vertex color by that alpha or derives it
    // from packed light.
    assert!(vertex.contains("vec4(vertex.color_uv.rgb, vertex.normal_light.w)"));
    assert!(!vertex.contains("vertex.color_uv.rgb * vertex.normal_light.w"));
    assert!(!MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.contains("LightmapTexture"));
    assert!(!MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.contains("v_light"));
}

#[test]
fn builtin_terrain_paths_preserve_sodium_mip_and_cutout_material_bits() {
    assert!(MINIMAL_TERRAIN_MATERIAL_VERTEX
        .contains("v_terrain_material_bits = uint(clamp(vertex.extra_data.w, 0.0, 255.0));"));
    for fragment in [
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT,
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT,
    ] {
        assert!(fragment.contains("bool use_mipmaps = (v_terrain_material_bits & 1u) != 0u;"));
        assert!(fragment.contains("const float FROZEN_MAX_TEXTURE_LOD_BIAS = 15.0;"));
        assert!(fragment.contains(
            "texture(sampler2D(Tex0, Samp0), sample_uv, -FROZEN_MAX_TEXTURE_LOD_BIAS)"
        ));
        assert!(!fragment.contains("textureLod(sampler2D(Tex0, Samp0), sample_uv, 0.0)"));
        assert!(fragment
            .contains("uint alpha_cutoff_class = (v_terrain_material_bits >> 1u) & 3u;"));
        // Frozen Sodium leaves face selection to the raster pipeline and
        // performs no fragment-side `gl_FrontFacing` discard.
        if fragment == MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT {
            assert_eq!(fragment.matches("gl_FrontFacing").count(), 1);
            assert!(fragment.contains(
                "color *= per_face_lighting && !gl_FrontFacing ? v_back_color : v_color;"
            ));
            assert!(
                fragment.contains("bool per_face_lighting = (uint(v_material.w) & 64u) != 0u;")
            );
        } else {
            assert!(!fragment.contains("gl_FrontFacing"));
        }
    }
}

#[test]
fn terrain_coordinate_probe_is_explicit_and_preserves_normal_source() {
    let normal = terrain_fragment_source_with_pass_define(
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT,
        TerrainMaterialProgramKind::Opaque,
    );
    assert_eq!(
        terrain_fragment_coordinate_probe(normal.clone(), None),
        normal
    );
    assert_eq!(
        terrain_fragment_coordinate_probe(normal.clone(), Some("unknown")),
        normal
    );
    for (mode, coordinate) in [
        ("u-bits", "sample_uv.x"),
        ("v-bits", "sample_uv.y"),
        ("depth-bits", "gl_FragCoord.z"),
    ] {
        let probe = terrain_fragment_coordinate_probe(normal.clone(), Some(mode));
        assert_eq!(probe.matches("DIAGNOSTIC ONLY").count(), 1);
        assert!(probe.contains(&format!("floatBitsToUint({coordinate})")));
        assert!(!probe.contains("gl_FragDepth ="));
        assert!(probe.contains("(coordinate_bits >> 16u) & 255u"));
        assert!(
            probe.find("uint coordinate_bits").unwrap()
                < probe.find("bool use_mipmaps").unwrap()
        );
    }
}

#[test]
fn terrain_clip_probe_observes_without_replacing_position_or_depth_math() {
    for mode in [
        None,
        Some("unknown"),
        Some("u-bits"),
        Some("v-bits"),
        Some("depth-bits"),
    ] {
        assert_eq!(
            terrain_vertex_coordinate_probe(MINIMAL_TERRAIN_MATERIAL_VERTEX.into(), mode),
            MINIMAL_TERRAIN_MATERIAL_VERTEX
        );
    }
    let vertex = terrain_vertex_coordinate_probe(
        MINIMAL_TERRAIN_MATERIAL_VERTEX.into(),
        Some("clip-bits"),
    );
    assert_eq!(
        vertex
            .matches("vec4 clip = projection * view * world;")
            .count(),
        1
    );
    assert!(vertex.contains("layout(location = 14) flat out vec4 diagnostic_clip;"));
    assert!(
        vertex.find("diagnostic_clip = clip;").unwrap()
            < vertex.find("clip.z = clip.z * 0.5").unwrap()
    );
    assert_eq!(vertex.matches("gl_Position = clip;").count(), 1);
    let fragment = terrain_fragment_coordinate_probe(
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT.into(),
        Some("clip-bits"),
    );
    assert!(fragment.contains("layout(location = 14) flat in vec4 diagnostic_clip;"));
    assert!(fragment.contains("(coordinate_bits >> 24u) & 255u, 165u, 90u"));
    assert!(fragment.contains("ivec2(764, 320)"));
    assert!(fragment.contains("uint uv_bits = floatBitsToUint(sample_uv.y);"));
    assert!(!fragment.contains("gl_FragDepth ="));
}

#[test]
fn deferred_opaque_coverage_does_not_reinterpret_compact_vertex_alpha_as_visibility() {
    // Sodium stores baked AO in compact vertex alpha for the normal
    // terrain stream. The forward translucent route needs that alpha;
    // the deferred opaque/cutout route instead has a binary post-discard
    // coverage contract for its lighting pass.
    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT.contains(
        "out_terrain_material_auxiliary = vec4(v_material.x, v_light.x, v_light.y, 1.0);"
    ));
    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT
        .contains("out_world_position = vec4(v_world_position, 1.0);"));
    assert!(MINIMAL_DEFERRED_LIGHTING_FRAGMENT.contains("if (material_light.a < 0.5)"));
}

#[test]
fn direct_translucent_terrain_uses_the_single_target_forward_contract() {
    let program = minimal_direct_terrain_translucent_program();
    assert_eq!(
        ProgramIdentity::new("vulkanic:builtin/direct_terrain_translucent_v1"),
        program.identity
    );
    assert_eq!(
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT,
        program.fragment.source
    );
    assert!(program
        .fragment
        .source
        .contains("layout(location = 0) out vec4 out_color;"));
    assert!(!program.fragment.source.contains("out_terrain_lit_color"));
}

#[test]
fn direct_terrain_fragment_discard_is_cutout_only_like_frozen_sodium() {
    let cutout = minimal_direct_terrain_cutout_program();
    let opaque = minimal_direct_terrain_solid_program();
    let translucent = minimal_direct_terrain_translucent_program();

    assert!(MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT
        .contains("#ifdef VULKANIC_TERRAIN_FRAGMENT_DISCARD"));
    assert!(cutout
        .fragment
        .source
        .starts_with("#version 450\n#define VULKANIC_TERRAIN_FRAGMENT_DISCARD 1\n"));
    assert!(!opaque
        .fragment
        .source
        .contains("#define VULKANIC_TERRAIN_FRAGMENT_DISCARD"));
    assert!(!translucent
        .fragment
        .source
        .contains("#define VULKANIC_TERRAIN_FRAGMENT_DISCARD"));
}

#[test]
fn compact_direct_terrain_program_reads_only_its_32_byte_vertex_record() {
    let program = minimal_compact_direct_terrain_program(TerrainMaterialProgramKind::Opaque);
    assert_eq!(
        ProgramIdentity::new("vulkanic:builtin/direct_terrain_opaque_compact32_v1"),
        program.identity
    );
    assert!(program.vertex.source.contains("vec3 position;"));
    assert!(program.vertex.source.contains("uint material;"));
    assert!(program.vertex.source.contains("vec2 atlas_uv;"));
    assert!(program.vertex.source.contains("uint color_rgba;"));
    assert!(program.vertex.source.contains("uint light_block_sky;"));
    assert!(!program.vertex.source.contains("vec4 shader_data;"));
    assert!(program.vertex.source.contains("v_uv = vertex.atlas_uv;"));
    assert!(program
        .vertex
        .source
        .contains("floatBitsToUint(instance.texture_transform.w)"));
    assert!(program.vertex.source.contains("material_semantics & 1024u"));
    assert!(!program
        .vertex
        .source
        .contains("v_uv = v_uv * instance.texture_transform.xy"));
    assert!(!program.vertex.source.contains("LIGHT0_DIRECTION"));
    assert!(program
        .vertex
        .source
        .contains("float(vertex.light_block_sky & 0xffu)"));
    assert!(program
        .vertex
        .source
        .contains("float(vertex.light_block_sky & 0xffu) / 240.0"));
    assert!(!program.vertex.source.contains("out vec3 v_normal;"));
    assert!(!program.vertex.source.contains("out vec2 v_light;"));
    assert!(!program.vertex.source.contains("out vec3 v_world_position;"));
    assert!(program
        .vertex
        .source
        .contains("v_color = terrain_color * instance.color * light_color"));
}

#[test]
fn static_compact_direct_terrain_program_removes_animation_interface() {
    let program =
        minimal_static_compact_direct_terrain_program(TerrainMaterialProgramKind::Cutout);
    assert_eq!(
        ProgramIdentity::new("vulkanic:builtin/direct_terrain_cutout_compact32_static_v1"),
        program.identity
    );
    assert!(!program
        .vertex
        .source
        .contains("v_animation_region = instance.animation_region"));
    assert!(!program
        .vertex
        .source
        .contains("flat out vec4 v_animation_region"));
    assert!(!program.fragment.source.contains("v_animation_next_region"));
    assert!(!program.fragment.source.contains("v_material.y > 0.5"));
    assert!(program.fragment.source.contains("vec2 sample_uv = v_uv;"));
    assert!(!program.vertex.source.contains("material_semantics"));
    assert!(!program.vertex.source.contains("packed_instance_light"));
    let compact = minimal_compact_direct_terrain_program(TerrainMaterialProgramKind::Cutout);
    assert!(compact.vertex.source.contains("material_semantics"));
    assert!(compact.vertex.source.contains("packed_instance_light"));
}

#[test]
fn deferred_fabulous_terrain_discard_is_cutout_only_like_frozen_sodium() {
    // Fabulous routes opaque/cutout terrain through the deferred
    // material shader. Keep its pass-local discard admission identical
    // to Frozen's `USE_FRAGMENT_DISCARD` policy, rather than allowing
    // the shared source body to make opaque terrain disappear.
    let cutout = minimal_terrain_cutout_program();
    let opaque = minimal_terrain_solid_program();
    let translucent = minimal_terrain_material_program(TerrainMaterialProgramKind::Translucent);

    assert!(
        MINIMAL_TERRAIN_MATERIAL_FRAGMENT.contains("#ifdef VULKANIC_TERRAIN_FRAGMENT_DISCARD")
    );
    assert!(cutout
        .fragment
        .source
        .starts_with("#version 450\n#define VULKANIC_TERRAIN_FRAGMENT_DISCARD 1\n"));
    assert!(!opaque
        .fragment
        .source
        .contains("#define VULKANIC_TERRAIN_FRAGMENT_DISCARD"));
    assert!(!translucent
        .fragment
        .source
        .contains("#define VULKANIC_TERRAIN_FRAGMENT_DISCARD"));
}

#[test]
fn lowered_source_program_preserves_real_stages_and_semantic_sampler_plan() {
    let source = paired_source();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let artifacts =
        preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();

    let program = prepare_lowered_terrain_source_program(
        &contract,
        &lowered,
        &bindings,
        TerrainMaterialProgramKind::Opaque,
    )
    .unwrap();

    assert_eq!(
        "vulkanic:shader-pack/lowered-source-test/terrain_opaque_source_gen9",
        program.identity.as_str()
    );
    assert_eq!(9, program.shader_pack_generation);
    assert_eq!(
        Some(TerrainMaterialProgramKind::Opaque),
        program.material_kind
    );
    assert_eq!(lowered.vertex().source(), program.vertex.source);
    assert_eq!(lowered.fragment().source(), program.fragment.source);
    assert_eq!(
        Some(
            [
                TerrainPassOutput::LitTerrainColor,
                TerrainPassOutput::MaterialAuxiliary,
            ]
            .as_slice()
        ),
        program.terrain_outputs(),
        "prepared source terrain programs must retain the named source output schema"
    );
    assert_eq!(
        Some(
            [
                (TerrainPassOutput::LitTerrainColor, 0),
                (TerrainPassOutput::MaterialAuxiliary, 6),
            ]
            .as_slice()
        ),
        program.terrain_output_color_slots(),
        "prepared source terrain programs must retain source-declared color-target semantics"
    );
    assert_eq!(1, program.opaque_resource_bindings.bindings().len());
    assert_eq!(
        TerrainSourceResourceRole::MaterialAtlas,
        program.opaque_resource_bindings.bindings()[0].role()
    );
    assert_eq!(
        crate::render::shaderpack::lowering::TerrainSourceOpaqueResourceKind::CombinedTextureSampler,
        program.opaque_resource_bindings.bindings()[0].kind()
    );
    let vulkan_modules = program.shader_module_descriptors(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions);
    assert_eq!(ShaderStage::Vertex, vulkan_modules[0].stage);
    assert_eq!(ShaderStage::Fragment, vulkan_modules[1].stage);
    assert_eq!(ShaderCodeFormat::Glsl, vulkan_modules[0].code_format);
    assert!(std::str::from_utf8(&vulkan_modules[0].code)
        .unwrap()
        .contains("VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH"));
    let opengl_modules = program.shader_module_descriptors(crate::render::vulkanic::test_support::opengl_capabilities().shader_conventions);
    let opengl_vertex = std::str::from_utf8(&opengl_modules[0].code).unwrap();
    assert!(
        opengl_vertex.contains("VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH"),
        "the backend-neutral source retains its conditional clip-depth finalizer"
    );
    assert!(
        !opengl_vertex.contains("#define VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH 1"),
        "only Vulkan enables the backend-neutral clip-depth finalizer"
    );
    assert_eq!(program.vertex.label, vulkan_modules[0].label);
    assert_eq!(program.fragment.label, vulkan_modules[1].label);
    assert_eq!(
        TerrainSourceFixedBinding {
            set: 0,
            binding: 0,
            kind: TerrainSourceBindingKind::StorageBuffer,
        },
        program.execution_interface.vertex_stream
    );
    assert_eq!(
        TERRAIN_SOURCE_VERTEX_BYTES as u32,
        program.execution_interface.vertex_stride
    );
    assert_eq!(
        [
            "position",
            "color",
            "normal_light",
            "atlas_uv_lightmap",
            "entity",
            "mid_tex_coord",
            "tangent",
            "mid_block",
        ],
        program
            .execution_interface
            .vertex_fields
            .map(|field| field.name)
    );
    assert_eq!(
        TerrainSourceFixedBinding {
            set: 0,
            binding: 1,
            kind: TerrainSourceBindingKind::UniformBuffer,
        },
        program.execution_interface.legacy_transforms
    );
    assert_eq!(128, program.execution_interface.legacy_transform_bytes);
    assert_eq!(
        TerrainSourceFixedBinding {
            set: 0,
            binding: 3,
            kind: TerrainSourceBindingKind::StorageBuffer,
        },
        program.execution_interface.instance_stream
    );
    assert_eq!(
        TERRAIN_SOURCE_INSTANCE_BYTES as u32,
        program.execution_interface.instance_stride
    );
    assert_eq!(
        Some(TerrainSourceFixedBinding {
            set: 0,
            binding: 2,
            kind: TerrainSourceBindingKind::UniformBuffer,
        }),
        program.execution_interface.scalar_uniforms
    );
    assert_eq!(128, program.execution_interface.scalar_uniform_bytes);
    assert_eq!(
        program.execution_interface.scalar_uniform_bytes,
        lowered.uniform_contract().std140_size()
    );
    assert_eq!(
        program.execution_interface.scalar_uniform_fields,
        lowered.uniform_contract().fields()
    );
    assert_eq!(
        program
            .execution_interface
            .scalar_uniform_fields
            .iter()
            .map(|field| field.name())
            .collect::<Vec<_>>(),
        ["gbufferModelView", "gbufferProjection"]
    );
    assert!(program
        .pack_scalar_uniforms(&TerrainSourceUniformFrame::default())
        .unwrap_err()
        .to_string()
        .contains("view matrix"));
    assert_eq!(
        128,
        program
            .pack_scalar_uniforms(&TerrainSourceUniformFrame {
                view_matrix: Some([1.0; 16]),
                projection_matrix: Some([2.0; 16]),
                ..TerrainSourceUniformFrame::default()
            })
            .unwrap()
            .len()
    );
    let legacy_texture_transforms = program
        .pack_legacy_texture_transforms(&TerrainSourceTextureTransforms {
            atlas_texture_matrix: [1.0; 16],
            lightmap_texture_matrix: [2.0; 16],
        })
        .unwrap();
    assert_eq!(128, legacy_texture_transforms.len());
    assert_eq!(
        1.0,
        f32::from_ne_bytes(legacy_texture_transforms[0..4].try_into().unwrap())
    );
    assert_eq!(
        2.0,
        f32::from_ne_bytes(legacy_texture_transforms[64..68].try_into().unwrap())
    );
    assert!(TerrainSourceTextureTransforms {
        atlas_texture_matrix: [f32::NAN; 16],
        lightmap_texture_matrix: [1.0; 16],
    }
    .validate()
    .unwrap_err()
    .to_string()
    .contains("atlas texture matrix"));
    let canonical_transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
    canonical_transforms.validate().unwrap();
    assert_eq!(1.0, canonical_transforms.atlas_texture_matrix[0]);
    assert_eq!(1.0, canonical_transforms.atlas_texture_matrix[15]);
    assert_eq!(1.0 / 256.0, canonical_transforms.lightmap_texture_matrix[0]);
    assert_eq!(1.0 / 256.0, canonical_transforms.lightmap_texture_matrix[5]);
    assert_eq!(1.0 / 32.0, canonical_transforms.lightmap_texture_matrix[12]);
    assert_eq!(1.0 / 32.0, canonical_transforms.lightmap_texture_matrix[13]);
    let layouts = program.execution_resource_layouts().unwrap();
    assert_eq!(
        vec![
            (0, ResourceBindingKind::StorageBuffer),
            (1, ResourceBindingKind::UniformBuffer),
            (2, ResourceBindingKind::UniformBuffer),
            (3, ResourceBindingKind::StorageBuffer),
        ],
        layouts
            .source_data
            .bindings
            .iter()
            .map(|binding| (binding.binding, binding.kind))
            .collect::<Vec<_>>()
    );
    assert_eq!(
        vec![(0, 0), (1, 1), (2, 1), (3, 1)],
        layouts
            .source_data
            .bindings
            .iter()
            .map(|binding| (binding.binding, binding.dynamic_offset_count))
            .collect::<Vec<_>>()
    );
    assert!(layouts.source_data.bindings.iter().all(|binding| {
        binding.stages == PipelineStageFlags::DRAW
            && binding.array_count == 1
            && !binding.optional
    }));
    assert_eq!(
        vec![(
            program.opaque_resource_bindings.bindings()[0].binding(),
            ResourceBindingKind::CombinedTextureSampler,
        )],
        layouts
            .pack_resources
            .bindings
            .iter()
            .map(|binding| (binding.binding, binding.kind))
            .collect::<Vec<_>>()
    );
    let missing_resources = TerrainSourceResourceAvailabilitySet::new(9, 4, []).unwrap();
    assert!(program
        .require_semantic_resources(&missing_resources)
        .unwrap_err()
        .to_string()
        .contains("material_atlas"));
    let atlas_resources = TerrainSourceResourceAvailabilitySet::new(
        9,
        4,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::MaterialAtlas,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 11,
        }],
    )
    .unwrap();
    program
        .require_semantic_resources(&atlas_resources)
        .unwrap();
    let source_resources = TerrainSourceOwnedResourceSet::new(
        atlas_resources,
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::MaterialAtlas,
            combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 3, 1).unwrap(),
        }],
    )
    .unwrap();
    let packed_resource_set = program
        .pack_resource_set_desc(
            "lowered-source-test.pack-resources",
            Handle::new(HandleKind::ResourceLayout, 5, 1).unwrap(),
            &source_resources,
        )
        .unwrap();
    assert_eq!(1, packed_resource_set.bindings.len());
    assert_eq!(
        program.opaque_resource_bindings.bindings()[0].binding(),
        packed_resource_set.bindings[0].binding
    );
    assert_eq!(
        ResourceBindingKind::CombinedTextureSampler,
        packed_resource_set.bindings[0].kind
    );
    assert!(program
        .pack_resource_set_desc(
            "wrong-layout",
            Handle::new(HandleKind::Sampler, 5, 1).unwrap(),
            &source_resources,
        )
        .unwrap_err()
        .to_string()
        .contains("resource-layout handle"));
    let stale_pack_resources = TerrainSourceResourceAvailabilitySet::new(
        8,
        4,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::MaterialAtlas,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 11,
        }],
    )
    .unwrap();
    assert!(program
        .require_semantic_resources(&stale_pack_resources)
        .unwrap_err()
        .to_string()
        .contains("does not match resource availability"));
    assert!(program.required_resources.is_empty());
    assert!(prepare_lowered_terrain_source_program(
        &contract,
        &lowered,
        &bindings,
        TerrainMaterialProgramKind::Translucent,
    )
    .is_err());
}

#[test]
fn lowered_entity_program_requires_typed_identity_color_and_local_texture_contract() {
    let source = paired_entity_source();
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let lowered = lower_entity_source_pair(&source, &contract).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = bind_entity_source_resources(&lowered, &declarations).unwrap();

    let program =
        prepare_lowered_entity_source_program(&contract, &lowered, &bindings).unwrap();
    assert_eq!(
        "vulkanic:shader-pack/lowered-entity-source-test/entity_source_gen11",
        program.identity.as_str()
    );
    assert_eq!(11, program.shader_pack_generation);
    assert_eq!(11, program.entity_id_generation);
    assert_eq!(
        [
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
        ]
        .as_slice(),
        program.named_output_color_slots()
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialTexture),
        program.opaque_resource_bindings.role_for("tex")
    );
    let cutout_descriptors =
        program.shader_module_descriptors_with_alpha_cutoff(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, Some(0.1));
    let cutout =
        String::from_utf8(cutout_descriptors.into_iter().nth(1).unwrap().code).unwrap();
    assert!(cutout.contains("#define VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF 0.10000000"));
    let opaque_descriptors =
        program.shader_module_descriptors_with_alpha_cutoff(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, None);
    let opaque =
        String::from_utf8(opaque_descriptors.into_iter().nth(1).unwrap().code).unwrap();
    // No cutoff define: the output alpha test compiles out (Iris ALWAYS).
    assert!(!opaque.contains("#define VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF"));
    assert!(opaque.contains("#ifdef VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF"));
    let layouts = program.execution_resource_layouts().unwrap();
    assert_eq!(
        vec![0, 1, 2, 3],
        layouts
            .source_data
            .bindings
            .iter()
            .map(|binding| binding.binding)
            .collect::<Vec<_>>(),
        "entity source data must use only the fixed Rust-owned stream and uniform bindings"
    );
    assert_eq!(
        vec![0],
        layouts
            .pack_resources
            .bindings
            .iter()
            .map(|binding| binding.binding)
            .collect::<Vec<_>>()
    );
    assert_eq!(
        ResourceBindingKind::CombinedTextureSampler,
        layouts.pack_resources.bindings[0].kind
    );
    let uniforms = program
        .pack_scalar_uniforms(&TerrainSourceUniformFrame {
            entity_id: Some(50_076),
            entity_color: Some([0.25, 0.5, 0.75, 1.0]),
            view_matrix: Some([1.0; 16]),
            projection_matrix: Some([1.0; 16]),
            ..TerrainSourceUniformFrame::default()
        })
        .unwrap();
    assert_eq!(
        program.execution_interface.scalar_uniform_bytes as usize,
        uniforms.len()
    );
    let transforms = program
        .pack_legacy_texture_transforms(
            &TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
        )
        .unwrap();
    assert_eq!(
        program.execution_interface.legacy_transform_bytes as usize,
        transforms.len(),
        "entity local UVs and packed light coordinates must use the fixed owned source transform ABI"
    );
    assert!(program
        .pack_scalar_uniforms(&TerrainSourceUniformFrame {
            entity_id: Some(50_076),
            view_matrix: Some([1.0; 16]),
            projection_matrix: Some([1.0; 16]),
            ..TerrainSourceUniformFrame::default()
        })
        .unwrap_err()
        .to_string()
        .contains("current rendered entity color"));
}

#[test]
fn lowered_translucent_source_program_has_a_separate_output_contract() {
    let source = paired_translucent_source();
    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    let artifacts =
        preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    let lowered =
        lower_translucent_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();

    let program =
        prepare_lowered_translucent_terrain_source_program(&contract, &lowered, &bindings)
            .unwrap();

    assert_eq!(
        "vulkanic:shader-pack/lowered-translucent-source-test/terrain_translucent_source_gen12",
        program.identity.as_str()
    );
    assert_eq!(
        Some(TerrainMaterialProgramKind::Translucent),
        program.material_kind
    );
    assert_eq!(
        Some(
            [
                TerrainPassOutput::LitTerrainColor,
                TerrainPassOutput::TranslucencyAuxiliary,
            ]
            .as_slice()
        ),
        program.terrain_outputs()
    );
    assert_eq!(
        Some(
            [
                (TerrainPassOutput::LitTerrainColor, 0),
                (TerrainPassOutput::TranslucencyAuxiliary, 3),
            ]
            .as_slice()
        ),
        program.terrain_output_color_slots()
    );
    let raster = program
        .translucent_raster_state
        .expect("the prepared translucent program must retain source raster semantics");
    assert_eq!(TerrainTranslucentBlend::SourceAlphaOver, raster.blend);
    assert!((raster.alpha_test.unwrap().greater_than() - 0.0001).abs() < f32::EPSILON);
    assert_eq!(Some(BlendMode::Alpha), program.translucent_blend_mode());
}

#[test]
fn translucent_source_program_rejects_missing_explicit_raster_contract() {
    let mut files = paired_translucent_source().files();
    files.retain(|file| file.path != "shaders.properties");
    files.push(ShaderSourceFile::new("shaders.properties", ""));
    let source = ShaderPackSource::new("missing-translucent-raster", 13, files).unwrap();
    let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
    let artifacts =
        preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    let lowered =
        lower_translucent_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();

    let error =
        prepare_lowered_translucent_terrain_source_program(&contract, &lowered, &bindings)
            .unwrap_err();
    assert!(error
        .to_string()
        .contains("no explicit alpha/blend raster contract"));
}

#[test]
fn lowered_fullscreen_program_owns_semantic_outputs_without_terrain_mesh_state() {
    let source = ShaderPackSource::new(
        "fullscreen-program",
        12,
        vec![
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\nin vec2 uv;\nuniform sampler2D tex;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0] = texture2D(tex, uv); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\ncolortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let stages = TerrainSourceStages {
        vertex: TerrainSourceStage {
            path: "world0/deferred1.vsh".to_string(),
            defines: Default::default(),
        },
        fragment: TerrainSourceStage {
            path: "world0/deferred1.fsh".to_string(),
            defines: Default::default(),
        },
    };
    let artifacts = preprocess_source_stage_pair(&source, &stages).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered =
        lower_fullscreen_source_pair(&artifacts.vertex, &artifacts.fragment, &declarations)
            .unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program = prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/deferred1.fsh",
        &lowered,
        &bindings,
    )
    .unwrap();
    assert_eq!(
        "vulkanic:shader-pack/fullscreen-program/world0-deferred1-source-gen12",
        program.identity.as_str()
    );
    assert_eq!(
        TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        program.outputs[0].role()
    );
    assert!(program.feedback_requirements.is_empty());
    let layouts = program.execution_resource_layouts().unwrap();
    assert_eq!(
        vec![0],
        layouts
            .source_data
            .bindings
            .iter()
            .map(|binding| binding.binding)
            .collect::<Vec<_>>()
    );
    assert!(program
        .vertex
        .source
        .contains("VulkanicSourceFullscreenFrame"));
    assert!(!program
        .vertex
        .source
        .contains("VulkanicSourceTerrainVertices"));
    let resources = TerrainSourceOwnedResourceSet::new(
        TerrainSourceResourceAvailabilitySet::new(
            source.generation(),
            4,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 9,
            }],
        )
        .unwrap(),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::MaterialAtlas,
            combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 3, 1).unwrap(),
        }],
    )
    .unwrap();
    let set = program
        .pack_resource_set_desc(
            "lowered-fullscreen-test.pack-resources",
            Handle::new(HandleKind::ResourceLayout, 4, 1).unwrap(),
            &resources,
        )
        .unwrap();
    assert_eq!(1, set.bindings.len());
    assert_eq!(
        ResourceBindingKind::CombinedTextureSampler,
        set.bindings[0].kind
    );
    assert!(program
        .pack_resource_set_desc(
            "lowered-fullscreen-test.wrong-layout",
            Handle::new(HandleKind::Sampler, 4, 1).unwrap(),
            &resources,
        )
        .unwrap_err()
        .to_string()
        .contains("resource-layout handle"));
}

#[test]
fn lowered_fullscreen_program_requires_ping_pong_for_sampled_output_role() {
    let source = ShaderPackSource::new(
        "fullscreen-feedback",
        13,
        vec![
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let stages = TerrainSourceStages {
        vertex: TerrainSourceStage {
            path: "world0/deferred1.vsh".to_string(),
            defines: Default::default(),
        },
        fragment: TerrainSourceStage {
            path: "world0/deferred1.fsh".to_string(),
            defines: Default::default(),
        },
    };
    let artifacts = preprocess_source_stage_pair(&source, &stages).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered =
        lower_fullscreen_source_pair(&artifacts.vertex, &artifacts.fragment, &declarations)
            .unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program = prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/deferred1.fsh",
        &lowered,
        &bindings,
    )
    .unwrap();

    assert_eq!(
        vec![FullscreenSourceFeedbackRequirement {
            role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
            sampled_binding: 0,
            output_location: 0,
        }],
        program.feedback_requirements
    );
}

#[test]
fn lowered_fullscreen_program_derives_mipmap_requirement_from_active_semantic_sampler() {
    let source = ShaderPackSource::new(
        "fullscreen-mipmap",
        14,
        vec![
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\n/* DRAWBUFFERS:0 */\n/* const bool colortex0MipmapEnabled = true; */\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let stages = TerrainSourceStages {
        vertex: TerrainSourceStage {
            path: "world0/deferred1.vsh".to_string(),
            defines: Default::default(),
        },
        fragment: TerrainSourceStage {
            path: "world0/deferred1.fsh".to_string(),
            defines: Default::default(),
        },
    };
    let artifacts = preprocess_source_stage_pair(&source, &stages).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered =
        lower_fullscreen_source_pair(&artifacts.vertex, &artifacts.fragment, &declarations)
            .unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program = prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/deferred1.fsh",
        &lowered,
        &bindings,
    )
    .unwrap();

    assert_eq!(
        vec![FullscreenSourceMipmapRequirement {
            role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
            sampled_binding: 0,
        }],
        program.mipmap_requirements
    );
}

#[test]
fn lowered_fullscreen_program_rejects_mipmap_directive_without_active_sampler() {
    let source = ShaderPackSource::new(
        "fullscreen-mipmap-unbound",
        15,
        vec![
            ShaderSourceFile::new(
                "world0/deferred1.vsh",
                "#version 130\nout vec2 uv;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "world0/deferred1.fsh",
                "#version 130\nin vec2 uv;\n/* DRAWBUFFERS:0 */\n/* const bool colortex0MipmapEnabled = true; */\nvoid main() { gl_FragData[0] = vec4(1.0); }",
            ),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "colortex0=shader_pack_color:primary\n",
            ),
        ],
    )
    .unwrap();
    let stages = TerrainSourceStages {
        vertex: TerrainSourceStage {
            path: "world0/deferred1.vsh".to_string(),
            defines: Default::default(),
        },
        fragment: TerrainSourceStage {
            path: "world0/deferred1.fsh".to_string(),
            defines: Default::default(),
        },
    };
    let artifacts = preprocess_source_stage_pair(&source, &stages).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered =
        lower_fullscreen_source_pair(&artifacts.vertex, &artifacts.fragment, &declarations)
            .unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let error = prepare_lowered_fullscreen_source_program(
        source.name(),
        source.generation(),
        "world0/deferred1.fsh",
        &lowered,
        &bindings,
    )
    .unwrap_err();

    assert!(error
        .to_string()
        .contains("no active semantic resource binding"));
}

#[test]
fn exact_atlas_distant_horizons_source_writer_preserves_owned_material_inputs() {
    let program = minimal_distant_horizons_lod_exact_atlas_source_program();

    assert_eq!(
        "vulkanic:builtin/distant_horizons_lod_exact_atlas_source_v1",
        program.identity.as_str()
    );
    assert!(program.vertex.source.contains("v_tile_uv"));
    assert!(program.fragment.source.contains("TerrainAtlasColor"));
    assert!(program.fragment.source.contains("LightmapTexture"));
    assert!(program.fragment.source.contains("out_source_primary"));
    assert!(program.fragment.source.contains("discard"));
}

#[test]
fn lowered_distant_horizons_program_uses_its_own_column_stream_contract() {
    let source = complete_bundled_pack_source_for_test();
    let contract =
        derive_distant_horizons_opaque_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();
    let artifacts =
        preprocess_distant_horizons_sources(&source, &contract.source_stages).unwrap();
    let lowered =
        lower_distant_horizons_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program =
        prepare_lowered_distant_horizons_source_program(&contract, &lowered, &bindings)
            .unwrap();

    assert_eq!(
        "vulkanic:shader-pack/complementaryhungloified-complete-test/distant_horizons_opaque_source_gen91",
        program.identity.as_str()
    );
    assert_eq!(91, program.shader_pack_generation);
    assert_eq!(
        DISTANT_HORIZONS_SOURCE_VERTEX_BYTES as u32,
        program.execution_interface.vertex_stride
    );
    assert_eq!(
        DistantHorizonsMaterialIdentityContract::ReducedColorMaterialCategory,
        program.execution_interface.material_identity_contract,
        "the bundled Complementary DH source consumes DH color/category semantics, not atlas-backed texels"
    );
    assert_eq!(
        DISTANT_HORIZONS_SOURCE_COLUMN_FRAME_BYTES as u32,
        program.execution_interface.column_frame_bytes
    );
    assert_eq!(
        TerrainSourceFixedBinding {
            set: 0,
            binding: 0,
            kind: TerrainSourceBindingKind::StorageBuffer,
        },
        program.execution_interface.vertex_stream
    );
    assert_eq!(
        TerrainSourceFixedBinding {
            set: 0,
            binding: 1,
            kind: TerrainSourceBindingKind::UniformBuffer,
        },
        program.execution_interface.column_frame
    );
    assert!(program
        .vertex
        .source
        .contains("VulkanicDistantHorizonsVertices"));
    assert!(program
        .vertex
        .source
        .contains("vulkanic_source_dh_vertex.data.w >> 16u"));
    assert!(!program
        .vertex
        .source
        .contains("VulkanicDistantHorizonsMaterialIds"));
    assert!(program.vertex.source.contains("dhModelView"));
    assert!(program
        .fragment
        .source
        .contains("out_distant_horizons_lit_color"));
    assert!(!program.fragment.source.contains("out_terrain_lit_color"));
    let layouts = program.execution_resource_layouts().unwrap();
    assert_eq!(
        vec![0, 1, 2],
        layouts
            .source_data
            .bindings
            .iter()
            .map(|binding| binding.binding)
            .collect::<Vec<_>>()
    );
    assert!(!layouts.pack_resources.bindings.is_empty());

    let mut near_terrain_abi = program.clone();
    near_terrain_abi.execution_interface.vertex_stride = TERRAIN_SOURCE_VERTEX_BYTES as u32;
    assert!(near_terrain_abi
        .execution_resource_layouts()
        .unwrap_err()
        .to_string()
        .contains("Distant Horizons source vertex stream"));
}

#[test]
fn exact_atlas_distant_horizons_adapter_preserves_the_selected_source_contract() {
    let source = complete_bundled_pack_source_for_test();
    let contract =
        derive_distant_horizons_opaque_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();
    let artifacts =
        preprocess_distant_horizons_sources(&source, &contract.source_stages).unwrap();
    let lowered =
        lower_distant_horizons_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program =
        prepare_lowered_distant_horizons_source_program(&contract, &lowered, &bindings)
            .unwrap();

    let exact = prepare_lowered_distant_horizons_exact_atlas_source_program(&program)
        .expect("the selected opaque DH source exposes the semantic adapter anchors");

    assert!(exact.source.identity.as_str().ends_with(":exact-atlas"));
    assert_eq!(
        DISTANT_HORIZONS_EXACT_ATLAS_SOURCE_VERTEX_BYTES as u32,
        exact.source.execution_interface.vertex_stride
    );
    assert_eq!(
        DistantHorizonsMaterialIdentityContract::AtlasBacked,
        exact.source.execution_interface.material_identity_contract
    );
    assert!(exact
        .source
        .vertex
        .source
        .contains("VulkanicDistantHorizonsExactAtlasVertex"));
    assert!(exact
        .source
        .vertex
        .source
        .contains("vulkanic_source_dh_atlas_tint_and_material"));
    let lightmap = exact
        .source
        .vertex
        .source
        .split("vec2 vulkanic_source_dh_packed_lightmap_coordinates()")
        .nth(1)
        .expect("the exact-atlas adapter must define its DH lightmap conversion")
        .split("#define vulkanic_source_texture_matrix")
        .next()
        .expect(
            "the exact-atlas adapter lightmap conversion must terminate before its aliases",
        );
    let block_offset = lightmap
        .find("light_normal_tint_material >> 8u")
        .expect("the DH block-light nibble must be emitted");
    let sky_offset = lightmap
        .find("light_normal_tint_material & 0xffu")
        .expect("the DH sky-light nibble must be emitted");
    assert!(
        block_offset < sky_offset,
        "Iris DH lightmap coordinates are (block, sky), not the copied byte order (sky, block)"
    );
    assert!(
        exact.source.vertex.source.contains(
            "vec3(vulkanic_source_dh_vertex.micro_x, 0.0, vulkanic_source_dh_vertex.micro_z)"
        ),
        "the exact-atlas DH adapter must retain Iris's X/Z-only micro-offset semantics"
    );
    assert!(exact
        .source
        .fragment
        .source
        .contains("vulkanic_source_dh_atlas_color"));
    assert!(exact
        .source
        .fragment
        .source
        .contains("color.rgb *= glColor.rgb;"));
    assert!(!exact
        .source
        .fragment
        .source
        .contains("if ((vulkanic_source_dh_atlas_tint_and_material & 1u) != 0u)"));
    assert!(exact
        .source
        .fragment
        .source
        .contains("layout(set = 2, binding = 0) uniform texture2D"));
    assert!(
        exact.source.fragment.source.contains("DoLighting"),
        "the adapter must retain selected source lighting rather than substitute minimal DH lighting"
    );
    assert!(!exact.source.fragment.source.contains("TerrainAtlasColor"));
}

#[test]
fn exact_atlas_distant_horizons_probe_observes_the_adapter_color() {
    let mut fragment = r#"
void main() {
    vec4 color = vulkanic_source_dh_atlas_color();
    out_distant_horizons_lit_color = color;
}
"#
    .to_string();

    apply_exact_atlas_distant_horizons_fragment_probe(&mut fragment, Some("atlas"))
        .expect("the exact-atlas probe must use the DH adapter color anchor");

    assert!(fragment.contains("selected-source diagnostic probe: atlas"));
    assert!(fragment.contains("out_distant_horizons_lit_color = color;\n    return;"));
    assert_eq!(
        1,
        fragment
            .matches("vec4 color = vulkanic_source_dh_atlas_color();")
            .count(),
        "the probe must preserve the exact-atlas color initialization"
    );
}

#[test]
fn exact_atlas_distant_horizons_probes_preserve_source_lighting_boundaries() {
    let source = r#"
void main() {
    vec2 lmCoord = vec2(0.5);
    vec4 color = vulkanic_source_dh_atlas_color();
    DoLighting(color, shadowMult, playerPos);
}
"#;
    let mut pre_lighting = source.to_string();
    apply_exact_atlas_distant_horizons_fragment_probe(&mut pre_lighting, Some("pre-lighting"))
        .expect("the pre-lighting probe must locate the source lighting boundary");
    assert!(pre_lighting.contains(
        "selected-source diagnostic probe: pre-lighting\n    out_distant_horizons_lit_color = color;\n    return;\nDoLighting"
    ));

    let mut lightmap = source.to_string();
    apply_exact_atlas_distant_horizons_fragment_probe(&mut lightmap, Some("lightmap"))
        .expect("the lightmap probe must use the adapter color anchor");
    assert!(lightmap.contains("out_distant_horizons_lit_color = vec4(lmCoord, 0.0, 1.0);"));
}

#[test]
fn lowered_dh_water_program_retains_source_alpha_phase_and_dh_column_abi() {
    let complete = complete_bundled_pack_source_for_test();
    let source = ShaderPackSource::new(
        "complete-dh-water-program",
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
    let contract =
        derive_distant_horizons_translucent_contract(&source, TerrainProgramScope::Overworld)
            .unwrap();
    let artifacts =
        preprocess_distant_horizons_sources(&source, &contract.source_stages).unwrap();
    let lowered =
        lower_distant_horizons_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program =
        prepare_lowered_distant_horizons_source_program(&contract, &lowered, &bindings)
            .unwrap();

    assert_eq!(DistantHorizonsPassKind::Translucent, program.pass_kind);
    assert_eq!(
        Some(TerrainTranslucentBlend::SourceAlphaOver),
        program.translucent_blend
    );
    assert!(program
        .identity
        .as_str()
        .contains("distant_horizons_translucent_source_gen92"));
    assert_eq!(
        DISTANT_HORIZONS_SOURCE_VERTEX_BYTES as u32,
        program.execution_interface.vertex_stride
    );
    assert!(program.fragment.source.contains("depthtex1"));
    assert!(program
        .fragment
        .source
        .contains("out_distant_horizons_lit_color"));
    program.execution_resource_layouts().unwrap();
}

#[test]
fn lowered_shadow_source_program_keeps_its_shadow_identity_and_outputs() {
    let source = paired_shadow_source();
    let vertex = crate::render::shaderpack::source::preprocess::preprocess_artifact(
        crate::render::shaderpack::source::preprocess::PreprocessInput {
            source: &source,
            entry: "shadow.vsh",
            defines: &[],
        },
    )
    .unwrap();
    let fragment = crate::render::shaderpack::source::preprocess::preprocess_artifact(
        crate::render::shaderpack::source::preprocess::PreprocessInput {
            source: &source,
            entry: "shadow.fsh",
            defines: &[],
        },
    )
    .unwrap();
    let lowered = lower_shadow_source_pair(&vertex, &fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();

    let program = prepare_lowered_shadow_source_program(
        source.name(),
        source.generation(),
        &lowered,
        &bindings,
    )
    .unwrap();

    // Shadow identities carry a deterministic content tag so dimension
    // variants of the shadow program never share cached layouts.
    assert!(program
        .identity
        .as_str()
        .starts_with("vulkanic:shader-pack/lowered-shadow-source-test/shadow_source_gen10_"));
    assert!(program.vertex.source.contains("shadowModelView"));
    assert!(program.vertex.source.contains("shadowProjection"));
    assert!(program.fragment.source.contains("out_shadow_color"));
    assert!(!program.fragment.source.contains("out_terrain_lit_color"));
    assert_eq!(
        vec!["shadowModelView", "shadowProjection"],
        program
            .execution_interface
            .scalar_uniform_fields
            .iter()
            .map(|field| field.name())
            .collect::<Vec<_>>()
    );
    assert_eq!(
        128,
        program
            .pack_scalar_uniforms(&TerrainSourceUniformFrame {
                shadow_model_view: Some([1.0; 16]),
                shadow_projection: Some([2.0; 16]),
                ..TerrainSourceUniformFrame::default()
            })
            .unwrap()
            .len()
    );
    assert_eq!(
        TerrainSourceResourceRole::MaterialAtlas,
        program.opaque_resource_bindings.bindings()[0].role()
    );
    assert!(program.required_resources.is_empty());
    let opaque = program
        .shadow_shader_module_descriptors(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, None)
        .unwrap();
    let cutout = program
        .shadow_shader_module_descriptors(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, Some(0.1))
        .unwrap();
    let overridden = program
        .shadow_shader_module_descriptors(crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions, Some(0.25))
        .unwrap();
    assert_eq!(opaque[0].code, cutout[0].code);
    assert!(!String::from_utf8_lossy(&opaque[1].code).contains("out_shadow_color.a >"));
    assert!(String::from_utf8_lossy(&cutout[1].code).contains("out_shadow_color.a > 0.10000000"));
    assert!(String::from_utf8_lossy(&overridden[1].code)
        .contains("out_shadow_color.a > 0.25000000"));
}

#[test]
fn lowered_cloud_source_program_retains_its_named_output_schema() {
    let source = paired_cloud_source();
    let contract = derive_cloud_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let lowered = lower_cloud_source_pair(&source, &contract).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program = prepare_lowered_cloud_source_program(&contract, &lowered, &bindings).unwrap();

    assert_eq!(
        "vulkanic:shader-pack/lowered-cloud-source-test/cloud_source_gen13",
        program.identity.as_str()
    );
    assert!(program.vertex.source.contains("vulkanic_source_position"));
    assert!(program.fragment.source.contains("out_cloud_lit_color"));
    assert!(program
        .fragment
        .source
        .contains("out_cloud_material_auxiliary"));
    assert!(program
        .fragment
        .source
        .contains("out_cloud_translucency_auxiliary"));
    assert_eq!(
        vec![
            (TerrainPassOutput::LitTerrainColor, 0),
            (TerrainPassOutput::MaterialAuxiliary, 6),
            (TerrainPassOutput::TranslucencyAuxiliary, 3),
        ],
        program.named_output_color_slots()
    );
    program.execution_resource_layouts().unwrap();
}

#[test]
fn bundled_shadow_stages_lower_for_the_entity_mesh_stream() {
    let source =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
        );
    let stages = crate::render::shaderpack::contracts::terrain::shadow_source_stages_for_scope(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let artifacts = preprocess_terrain_sources(&source, &stages).unwrap();
    let lowered = crate::render::shaderpack::lowering::lower_entity_shadow_source_pair(
        &artifacts.vertex,
        &artifacts.fragment,
    )
    .unwrap();
    // Shadow matrices feed the legacy transform, not the camera view.
    assert!(lowered.vertex().source().contains("shadowModelView * vulkanic_source_model_transform"));
    assert!(lowered.fragment().source().contains("layout(location = 0) out vec4"));
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let contract = crate::render::shaderpack::contracts::entity::derive_entity_shadow_contract(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    assert_eq!(vec![0, 1], contract.output_color_slots);
    let program = prepare_lowered_entity_shadow_source_program(&contract, &lowered, &bindings).unwrap();
    assert!(program.identity.as_str().contains("entity_shadow_source"));
    let [_, fragment] = program.shader_module_descriptors_with_alpha_cutoff(
        crate::render::vulkanic::test_support::vulkan_capabilities().shader_conventions,
        Some(0.1),
    );
    let text = String::from_utf8_lossy(&fragment.code).to_string();
    assert!(text.contains("VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF 0.1"));
    assert!(text.contains("vulkanic_entity_shadow_main();"));
    let prepared = crate::render::shaderpack::contracts::entity::prepare_entity_shadow_source_program(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    assert_eq!(program.identity, prepared.identity);
}

#[test]
fn selected_scoped_shadow_source_prepares_without_becoming_an_executable_pass() {
    let source =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
        );
    let stages = crate::render::shaderpack::contracts::terrain::shadow_source_stages_for_scope(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let artifacts = preprocess_terrain_sources(&source, &stages).unwrap();
    let lowered = lower_shadow_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();

    let program = prepare_lowered_shadow_source_program(
        source.name(),
        source.generation(),
        &lowered,
        &bindings,
    )
    .unwrap();

    assert!(program.fragment.source.contains("out_shadow_color"));
    assert!(program
        .fragment
        .source
        .contains("out_shadow_light_shaft_color"));
    assert!(!program.fragment.source.contains("out_terrain_lit_color"));
    let layouts = program.execution_resource_layouts().unwrap();
    assert!(layouts
        .pack_resources
        .bindings
        .iter()
        .all(|binding| binding.kind != ResourceBindingKind::StorageTexture));
    assert!(program
        .opaque_resource_bindings
        .bindings()
        .iter()
        .all(|source| source.resource_name() != "voxel_img"));
}

#[test]
fn source_storage_access_preserves_glsl_read_write_qualifiers() {
    assert_eq!(
        AccessFlags::READ,
        source_storage_access("readonly").unwrap()
    );
    assert_eq!(
        AccessFlags::WRITE,
        source_storage_access("writeonly coherent").unwrap()
    );
    assert_eq!(
        AccessFlags(AccessFlags::READ.0 | AccessFlags::WRITE.0),
        source_storage_access("coherent restrict").unwrap()
    );
    assert!(source_storage_access("readonly writeonly")
        .unwrap_err()
        .to_string()
        .contains("both readonly and writeonly"));
}

#[test]
fn prepared_source_interface_rejects_mutated_fixed_abi_fields() {
    let source = paired_source();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let artifacts =
        preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program = prepare_lowered_terrain_source_program(
        &contract,
        &lowered,
        &bindings,
        TerrainMaterialProgramKind::Opaque,
    )
    .unwrap();

    program.execution_interface.validate().unwrap();

    let mut wrong_stride = program.execution_interface.clone();
    wrong_stride.vertex_stride = 96;
    assert!(wrong_stride
        .validate()
        .unwrap_err()
        .to_string()
        .contains("128-byte ABI"));

    let mut wrong_lane = program.execution_interface.clone();
    wrong_lane.vertex_fields[3].offset = 64;
    assert!(wrong_lane
        .validate()
        .unwrap_err()
        .to_string()
        .contains("atlas_uv_lightmap"));

    let mut missing_scalar_binding = program.execution_interface.clone();
    missing_scalar_binding.scalar_uniforms = None;
    assert!(missing_scalar_binding
        .validate()
        .unwrap_err()
        .to_string()
        .contains("scalar fields require fixed set 0 binding 2"));

    let mut wrong_instances = program.execution_interface.clone();
    wrong_instances.instance_stride = 32;
    assert!(wrong_instances
        .validate()
        .unwrap_err()
        .to_string()
        .contains("binding 3"));
}

#[test]
fn prepared_textured_material_interface_rejects_mutated_fixed_abi_fields() {
    let source = complete_bundled_pack_source_for_test();
    let contract = crate::render::shaderpack::contracts::material::derive_textured_material_contract(
        &source,
        crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
    )
    .unwrap();
    let lowered = crate::render::shaderpack::contracts::material::lower_textured_material_source_pair(
        &source, &contract,
    )
    .unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap();
    let program =
        prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings)
            .unwrap();

    program.execution_interface.validate().unwrap();

    let mut wrong_stride = program.execution_interface.clone();
    wrong_stride.vertex_stride = 48;
    assert!(wrong_stride
        .validate()
        .unwrap_err()
        .to_string()
        .contains("64-byte storage stream"));

    let mut wrong_lane = program.execution_interface.clone();
    wrong_lane.vertex_fields[3].offset = 44;
    assert!(wrong_lane
        .validate()
        .unwrap_err()
        .to_string()
        .contains("texture_uv_lightmap"));

    let mut missing_scalar_binding = program.execution_interface.clone();
    missing_scalar_binding.scalar_uniforms = None;
    assert!(missing_scalar_binding
        .validate()
        .unwrap_err()
        .to_string()
        .contains("scalar fields require fixed set 0 binding 2"));

    let mut invalid_scalar_envelope = program.execution_interface.clone();
    invalid_scalar_envelope.scalar_uniform_bytes = 0;
    assert!(invalid_scalar_envelope
        .validate()
        .unwrap_err()
        .to_string()
        .contains("not a valid std140 envelope"));

    let local_texture_primitive =
        crate::render::shaderpack::contracts::material::stage_textured_material_primitive(
            [
                [-1.0, -1.0, 2.0],
                [1.0, -1.0, 2.0],
                [1.0, 1.0, 2.0],
                [-1.0, 1.0, 2.0],
            ],
            [[0.0, 0.0], [1.0, 0.0], [1.0, 1.0], [0.0, 1.0]],
            0xffff_ffff,
            0,
            crate::render::shaderpack::contracts::material::TexturedMaterialTextureCoordinates::LocalTexture,
            crate::render::shaderpack::contracts::material::TexturedMaterialWinding::CounterClockwise,
        )
        .unwrap();
    assert_eq!(
        crate::render::shaderpack::contracts::material::TEXTURED_MATERIAL_SOURCE_VERTEX_BYTES * 4,
        program
            .pack_material_primitives(&[local_texture_primitive])
            .unwrap()
            .len(),
        "the source ABI accepts local UVs; the frontend selects the owned semantic texture binding"
    );
}

#[test]
fn prepared_textured_material_alpha_test_runs_after_primary_output_and_can_be_disabled() {
    use crate::render::shaderpack::contracts::material::{
        derive_textured_material_contract, lower_textured_material_source_pair,
    };
    let source = complete_bundled_pack_source_for_test();
    let mut contract = derive_textured_material_contract(&source, TerrainProgramScope::Overworld)
        .unwrap();
    let lowered = lower_textured_material_source_pair(&source, &contract).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered.opaque_resource_contract().bind_semantic_roles(&declarations).unwrap();
    for cutoff in [0.1_f32, 0.25] {
        contract.alpha_cutoff_bits = Some(cutoff.to_bits());
        let program = prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings)
            .unwrap();
        let fragment = &program.fragment.source;
        let alpha_test = format!("if (!(out_textured_material_lit_color.a > {cutoff:?})) discard;");
        assert!(fragment.ends_with(&format!(
            "void main() {{\n    vulkanic_source_textured_main();\n    {alpha_test}\n}}\n"
        )), "the pack must compute output alpha before the test");
        assert_eq!(1, fragment.matches("void main()").count());
        assert_eq!(1, fragment.matches("void vulkanic_source_textured_main()").count());
    }
    contract.alpha_cutoff_bits = None;
    let program = prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings)
        .unwrap();
    assert_eq!(lowered.fragment().source(), program.fragment.source);
    for cutoff in [f32::NAN, f32::INFINITY, -0.1, 1.1] {
        contract.alpha_cutoff_bits = Some(cutoff.to_bits());
        assert!(prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings)
            .unwrap_err().to_string().contains("finite and in [0, 1]"));
    }
}

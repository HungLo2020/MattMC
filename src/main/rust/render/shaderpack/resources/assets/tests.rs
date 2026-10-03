use crate::render::shaderpack::contracts::terrain::derive_complementary_terrain_contract;
use crate::render::shaderpack::lowering::lower_terrain_source_pair;
use crate::render::shaderpack::resources::assets::*;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceResourceBindings, TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
};
use crate::render::shaderpack::source::assets::{ShaderPackAssetFile, ShaderPackAssetUpdate};
use crate::render::shaderpack::source::preprocess::preprocess_terrain_sources;
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};

fn png() -> Vec<u8> {
    let mut bytes = Vec::new();
    let mut encoder = png::Encoder::new(&mut bytes, 1, 1);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    encoder
        .write_header()
        .unwrap()
        .write_image_data(&[1, 2, 3, 4])
        .unwrap();
    bytes
}

fn assets() -> ShaderPackAssets {
    ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "test-pack".to_string(),
        generation: 7,
        files: vec![
            ShaderPackAssetFile::new("lib/noise.png", png()),
            ShaderPackAssetFile::new(
                "lib/noise.png.mcmeta",
                br#"{"texture":{"blur":true,"clamp":false}}"#.to_vec(),
            ),
        ],
    })
    .unwrap()
}

fn bindings() -> TerrainShaderPackAssetBindings {
    TerrainShaderPackAssetBindings::from_source(
        &ShaderPackSource::new(
            "test-pack",
            7,
            vec![ShaderSourceFile::new(
                crate::render::shaderpack::source::assets::SHADER_PROPERTIES_PATH,
                "texture.noise=/lib/noise.png\ncustomTexture.gbuffers_terrain.detail=lib/noise.png\n",
            )],
        )
        .unwrap(),
    )
    .unwrap()
}

fn active_resource_bindings() -> TerrainSourceOpaqueResourceBindingPlan {
    let source = ShaderPackSource::new(
        "test-pack",
        7,
        vec![
            ShaderSourceFile::new(
                "gbuffers_terrain.vsh",
                "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
            ),
            ShaderSourceFile::new(
                "gbuffers_terrain.fsh",
                "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nuniform sampler2D noisetex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb * texture2D(noisetex, texCoord).rrr; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
            ),
            ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
            ShaderSourceFile::new("shaders.properties", ""),
            ShaderSourceFile::new("block.properties", ""),
            ShaderSourceFile::new(
                TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nnoisetex=noise\n",
            ),
        ],
    )
    .unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let artifacts =
        preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    let lowered = lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)
        .unwrap()
}

#[test]
fn owns_one_decoded_texture_per_path_and_one_binding_per_sampler() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let active_bindings = active_resource_bindings();
    let resources =
        TerrainSourceAssetResources::create(&mut gal, &assets(), &bindings(), &[&active_bindings])
            .unwrap();
    assert_eq!("test-pack", resources.pack_name());
    assert_eq!(7, resources.generation());
    assert_eq!(1, resources.len());
    assert!(resources
        .combined_sampler_for(TerrainSourceResourceRole::PackTexture(
            "lib/detail.png".into()
        ))
        .is_none());
    // texture, view, upload, sampler, and one active combined sampler.
    assert_eq!(5, gal.metrics().resource_creates);
    resources.destroy(&mut gal).unwrap();
}

#[test]
fn failed_asset_resolution_does_not_leave_valid_source_resources() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let source = ShaderPackSource::new(
        "test-pack",
        7,
        vec![ShaderSourceFile::new(
            crate::render::shaderpack::source::assets::SHADER_PROPERTIES_PATH,
            "texture.noise=lib/missing.png\n",
        )],
    )
    .unwrap();
    let bindings = TerrainShaderPackAssetBindings::from_source(&source).unwrap();
    let active_bindings = active_resource_bindings();
    assert!(TerrainSourceAssetResources::create(
        &mut gal,
        &assets(),
        &bindings,
        &[&active_bindings],
    )
    .is_err());
    assert_eq!(0, gal.metrics().resource_creates);
}

#[test]
fn maps_declared_pack_pngs_into_shared_semantic_resource_roles() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let active_bindings = active_resource_bindings();
    let resources =
        TerrainSourceAssetResources::create(&mut gal, &assets(), &bindings(), &[&active_bindings])
            .unwrap();
    let semantic = resources
        .declared_semantic_resources(&[&active_bindings], 11)
        .unwrap();
    assert_eq!(7, semantic.availability().shader_pack_generation());
    assert_eq!(11, semantic.availability().world_generation());
    assert_eq!(
        resources.combined_sampler_for(TerrainSourceResourceRole::Noise),
        semantic.combined_sampler_for(TerrainSourceResourceRole::Noise)
    );
    assert_eq!(
        None,
        semantic.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
    );
    resources.destroy(&mut gal).unwrap();
}

#[test]
fn copied_png_stage_aliases_keep_distinct_selected_paths_and_residency() {
    use crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options;
    let source = ShaderPackSource::new("test-pack", 7, vec![
        ShaderSourceFile::new("shaders.properties", concat!(
            "texture.gbuffers.gaux2=lib/day.png\n",
            "texture.deferred.gaux2=lib/night.png\n")),
        ShaderSourceFile::new("gbuffers_terrain.vsh", "#version 130\nvoid main() { gl_Position=vec4(0.0); }\n"),
        ShaderSourceFile::new("gbuffers_terrain.fsh", "#version 130\nuniform sampler2D gaux2;\nvoid main() { gl_FragData[0]=texture2D(gaux2,vec2(0.0)); }\n"),
        ShaderSourceFile::new("deferred.fsh", "#version 130\nuniform sampler2D gaux2;\nvoid main() { gl_FragData[0]=texture2D(gaux2,vec2(0.0)); }\n"),
    ]).unwrap();
    let vertex =
        preprocess_artifact_with_runtime_options(&source, "gbuffers_terrain.vsh", &[]).unwrap();
    let plans = ["gbuffers_terrain.fsh", "deferred.fsh"].map(|entry| {
        let fragment = preprocess_artifact_with_runtime_options(&source, entry, &[]).unwrap();
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        let declarations =
            TerrainSourceResourceBindings::from_preprocessed_stage(&source, &fragment).unwrap();
        lowered
            .opaque_resource_contract()
            .bind_semantic_roles(&declarations)
            .unwrap()
    });
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "test-pack".into(),
        generation: 7,
        files: vec![
            ShaderPackAssetFile::new("lib/day.png", png()),
            ShaderPackAssetFile::new("lib/night.png", png()),
        ],
    })
    .unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let resources = TerrainSourceAssetResources::create(
        &mut gal,
        &assets,
        &TerrainShaderPackAssetBindings::from_source(&source).unwrap(),
        &[&plans[0], &plans[1]],
    )
    .unwrap();
    let day = TerrainSourceResourceRole::PackTexture("lib/day.png".into());
    let night = TerrainSourceResourceRole::PackTexture("lib/night.png".into());
    assert_eq!(2, resources.len());
    let semantic = resources
        .declared_semantic_resources(&[&plans[0], &plans[1]], 11)
        .unwrap();
    assert!(semantic.combined_sampler_for(day.clone()).is_some());
    assert!(semantic.combined_sampler_for(night.clone()).is_some());
    assert_ne!(
        semantic.combined_sampler_for(day),
        semantic.combined_sampler_for(night)
    );
    resources.destroy(&mut gal).unwrap();
    gal.retire_through_for_test(gal.latest_submission_id())
        .unwrap();
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn copied_png_declarations_resolve_conditional_properties_before_duplicate_checks() {
    let source = ShaderPackSource::new("test-pack",7,vec![ShaderSourceFile::new("shaders.properties",
        "#define CHOOSE_FIRST 1\n#if CHOOSE_FIRST == 1\ntexture.noise=lib/day.png\n#else\ntexture.noise=lib/night.png\n#endif\n")]).unwrap();
    let declarations =
        TerrainShaderPackAssetBindings::from_source_with_defines(&source, &[]).unwrap();
    assert_eq!(Some("lib/day.png"), declarations.sampler_path("noisetex"));
}

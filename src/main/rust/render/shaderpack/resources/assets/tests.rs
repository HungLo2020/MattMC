use crate::render::shaderpack::resources::assets::*;
    use crate::render::shaderpack::source::assets::{
    ShaderPackAssetFile, ShaderPackAssetUpdate,
};
use crate::render::shaderpack::lowering::lower_terrain_source_pair;
use crate::render::shaderpack::source::preprocess::preprocess_terrain_sources;
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
use crate::render::shaderpack::contracts::terrain::derive_complementary_terrain_contract;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceResourceBindings, TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
};

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
    let resources = TerrainSourceAssetResources::create(
        &mut gal,
        &assets(),
        &bindings(),
        &[&active_bindings],
    )
    .unwrap();
    assert_eq!("test-pack", resources.pack_name());
    assert_eq!(7, resources.generation());
    assert_eq!(1, resources.len());
    assert!(resources.combined_sampler_for("detail").is_none());
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
    let resources = TerrainSourceAssetResources::create(
        &mut gal,
        &assets(),
        &bindings(),
        &[&active_bindings],
    )
    .unwrap();
    let semantic = resources
        .declared_semantic_resources(&[&active_bindings], 11)
        .unwrap();
    assert_eq!(7, semantic.availability().shader_pack_generation());
    assert_eq!(11, semantic.availability().world_generation());
    assert_eq!(
        resources.combined_sampler_for("noisetex"),
        semantic.combined_sampler_for(TerrainSourceResourceRole::Noise)
    );
    assert_eq!(
        None,
        semantic.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
    );
    resources.destroy(&mut gal).unwrap();
}

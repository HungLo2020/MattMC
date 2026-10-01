use crate::render::shaderpack::source::assets::*;

fn png(width: u32, height: u32, rgba: &[u8]) -> Vec<u8> {
    let mut bytes = Vec::new();
    let mut encoder = png::Encoder::new(&mut bytes, width, height);
    encoder.set_color(png::ColorType::Rgba);
    encoder.set_depth(png::BitDepth::Eight);
    encoder
        .write_header()
        .unwrap()
        .write_image_data(rgba)
        .unwrap();
    bytes
}

fn update(generation: u64, bytes: &[u8]) -> ShaderPackAssetUpdate {
    ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation,
        files: vec![ShaderPackAssetFile::new(
            "textures/noise.png",
            bytes.to_vec(),
        )],
    }
}

#[test]
fn copies_binary_assets_and_normalizes_pack_relative_paths() {
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new(
            "textures\\noise.png",
            vec![1, 2, 3],
        )],
    })
    .unwrap();
    assert_eq!(Some(vec![1, 2, 3]), assets.copy("textures/noise.png"));
    let mut caller_copy = assets.copy("textures/noise.png").unwrap();
    caller_copy[0] = 99;
    assert_eq!(Some(vec![1, 2, 3]), assets.copy("textures/noise.png"));
    assert_eq!(
        vec!["textures/noise.png"],
        assets.paths().collect::<Vec<_>>()
    );
    assert!(ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new("../escape.png", Vec::new())],
    })
    .is_err());
}

#[test]
fn resolves_post_effect_definition_and_stages_from_paired_snapshots() {
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new(
            "post_effect/custom.json",
            br#"{"targets":{},"passes":[{"vertex_shader":"minecraft:post/custom","fragment_shader":"minecraft:post/custom","output":"minecraft:main"}]}"#.to_vec(),
        )],
    })
    .unwrap();
    let source = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![
            crate::render::shaderpack::source::ShaderSourceFile::new("post/custom.vsh", "void main(){}"),
            crate::render::shaderpack::source::ShaderSourceFile::new("post/custom.fsh", "void main(){}"),
        ],
    )
    .unwrap();
    let (contract, stages) = assets
        .resolve_post_effect_contract("minecraft:custom", &source)
        .unwrap();
    assert_eq!(assets.validate_post_effect_contracts(&source).unwrap(), 1);
    assert_eq!(contract.passes.len(), 1);
    assert_eq!(stages.len(), 1);
    assert_eq!(stages[0].vertex_shader, b"void main(){}");
    assert_eq!(stages[0].fragment_shader, b"void main(){}");
}

#[test]
fn post_effect_validation_rejects_malformed_generation() {
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 4,
        files: vec![ShaderPackAssetFile::new(
            "post_effect/broken.json",
            br#"{"passes": ["not-a-pass"]}"#.to_vec(),
        )],
    })
    .unwrap();
    let source = ShaderPackSource::new("selected-pack", 4, Vec::new()).unwrap();
    let error = assets.validate_post_effect_contracts(&source).unwrap_err();
    assert!(error.to_string().contains("post-effect") || error.to_string().contains("pass"));
}

#[test]
fn store_keeps_prior_generation_on_stale_or_malformed_updates() {
    let mut store = ShaderPackAssetStore::default();
    store.apply_update(update(1, &[4, 5])).unwrap();
    assert!(store.apply_update(update(1, &[6])).is_err());
    assert!(store
        .apply_update(ShaderPackAssetUpdate {
            pack_name: "selected-pack".to_string(),
            generation: 2,
            files: vec![ShaderPackAssetFile::new("textures/../bad.png", Vec::new())],
        })
        .is_err());
    let active = store.active_for_source("selected-pack", 1).unwrap();
    assert_eq!(Some(vec![4, 5]), active.copy("textures/noise.png"));
    assert_eq!(vec![1, 2], store.failed_generations());
    assert!(store.active_for_source("other-pack", 1).is_err());
    assert!(store.active_for_source("selected-pack", 2).is_err());
}

#[test]
fn decodes_owned_png_without_exposing_backend_resource_state() {
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new(
            "textures/noise.png",
            png(2, 1, &[1, 2, 3, 4, 5, 6, 7, 8]),
        )],
    })
    .unwrap();
    let decoded = assets.decode_rgba8("textures/noise.png").unwrap();
    assert_eq!("textures/noise.png", decoded.path);
    assert_eq!((2, 1), (decoded.width, decoded.height));
    assert_eq!(vec![1, 2, 3, 4, 5, 6, 7, 8], decoded.pixels_rgba8);
    assert!(assets.decode_rgba8("textures/missing.png").is_err());
    assert!(assets.decode_rgba8("textures/noise.raw").is_err());
}

#[test]
fn resolves_png_sampler_metadata_without_backend_state() {
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![
            ShaderPackAssetFile::new("textures/noise.png", png(1, 1, &[1, 2, 3, 4])),
            ShaderPackAssetFile::new(
                "textures/noise.png.mcmeta",
                br#"{"texture": {"blur": true, "clamp": false}}"#.to_vec(),
            ),
        ],
    })
    .unwrap();
    assert_eq!(
        ShaderPackAssetSamplerPolicy {
            blur: true,
            clamp: false,
        },
        assets.sampler_policy("textures/noise.png").unwrap()
    );
    assert_eq!(
        ShaderPackAssetSamplerPolicy::default(),
        assets.sampler_policy("textures/missing.png").unwrap()
    );
    assert!(ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 4,
        files: vec![
            ShaderPackAssetFile::new("textures/bad.png", png(1, 1, &[1, 2, 3, 4])),
            ShaderPackAssetFile::new("textures/bad.png.mcmeta", b"{bad".to_vec()),
        ],
    })
    .unwrap()
    .sampler_policy("textures/bad.png")
    .is_err());
}

#[test]
fn rejects_malformed_png_before_resource_creation() {
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new("textures/bad.png", vec![1, 2, 3])],
    })
    .unwrap();
    assert!(assets
        .decode_rgba8("textures/bad.png")
        .unwrap_err()
        .to_string()
        .contains("header"));
}

#[test]
fn parses_iris_property_texture_declarations_as_pack_semantics() {
    let source = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![crate::render::shaderpack::source::ShaderSourceFile::new(
            SHADER_PROPERTIES_PATH,
            concat!(
                "texture.noise=lib/textures/noise.png\n",
                "texture.gbuffers.gaux4=lib/textures/cloud-water.png\n",
                "customTexture.gbuffers_terrain.detail=lib/textures/detail.png\n",
            ),
        )],
    )
    .unwrap();
    let bindings = TerrainShaderPackAssetBindings::from_source(&source).unwrap();
    assert_eq!(
        Some("lib/textures/noise.png"),
        bindings.sampler_path("noisetex")
    );
    assert_eq!(
        Some("lib/textures/cloud-water.png"),
        bindings.sampler_path("gaux4")
    );
    assert_eq!(
        Some("lib/textures/detail.png"),
        bindings.sampler_path("detail")
    );

    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new(
            "lib/textures/noise.png",
            png(1, 1, &[9, 8, 7, 6]),
        )],
    })
    .unwrap();
    assert_eq!(
        vec![9, 8, 7, 6],
        bindings
            .resolve_rgba8(&assets, "noisetex")
            .unwrap()
            .pixels_rgba8
    );
    assert!(bindings.resolve_rgba8(&assets, "gaux4").is_err());
}

#[test]
fn rejects_excessive_terrain_sampler_declarations_before_map_growth() {
    let mut properties = String::new();
    for index in 0..=MAX_TERRAIN_SAMPLER_DECLARATIONS {
        properties.push_str(&format!(
            "texture.gbuffers.gaux{index}=textures/{index}.png\n"
        ));
    }
    let source = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![crate::render::shaderpack::source::ShaderSourceFile::new(
            SHADER_PROPERTIES_PATH,
            properties,
        )],
    )
    .unwrap();
    let error = TerrainShaderPackAssetBindings::from_source(&source).unwrap_err();
    assert!(error
        .to_string()
        .contains("sampler declarations exceed bounded limit"));
}

#[test]
fn rejects_raw_or_duplicated_terrain_texture_declarations() {
    let raw = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![crate::render::shaderpack::source::ShaderSourceFile::new(
            SHADER_PROPERTIES_PATH,
            "texture.gbuffers.noisetex=volume.raw TEXTURE_3D rgba16f 16 16 16 rgba half_float\n",
        )],
    )
    .unwrap();
    assert!(TerrainShaderPackAssetBindings::from_source(&raw)
        .unwrap_err()
        .to_string()
        .contains("raw texture"));
    let duplicate = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![crate::render::shaderpack::source::ShaderSourceFile::new(
            SHADER_PROPERTIES_PATH,
            "texture.noise=lib/one.png\ntexture.gbuffers.noisetex=lib/two.png\n",
        )],
    )
    .unwrap();
    assert!(TerrainShaderPackAssetBindings::from_source(&duplicate).is_err());

    let leading_slash = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![crate::render::shaderpack::source::ShaderSourceFile::new(
            SHADER_PROPERTIES_PATH,
            "texture.noise=/lib/textures/noise.png\n",
        )],
    )
    .unwrap();
    assert_eq!(
        Some("lib/textures/noise.png"),
        TerrainShaderPackAssetBindings::from_source(&leading_slash)
            .unwrap()
            .sampler_path("noisetex")
    );

    let resource_location = ShaderPackSource::new(
        "selected-pack",
        3,
        vec![crate::render::shaderpack::source::ShaderSourceFile::new(
            SHADER_PROPERTIES_PATH,
            "texture.noise=minecraft:textures/atlas/blocks.png\n",
        )],
    )
    .unwrap();
    let bindings = TerrainShaderPackAssetBindings::from_source(&resource_location).unwrap();
    assert_eq!(
        Some("minecraft/textures/atlas/blocks.png"),
        bindings.sampler_path("noisetex")
    );
    let assets = ShaderPackAssets::new(ShaderPackAssetUpdate {
        pack_name: "selected-pack".to_string(),
        generation: 3,
        files: vec![ShaderPackAssetFile::new(
            "minecraft/textures/atlas/blocks.png",
            png(1, 1, &[255, 255, 255, 255]),
        )],
    })
    .unwrap();
    assert_eq!(
        "minecraft/textures/atlas/blocks.png",
        bindings.resolve_rgba8(&assets, "noisetex").unwrap().path
    );
}

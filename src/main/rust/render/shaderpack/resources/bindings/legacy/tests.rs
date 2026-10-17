use super::*;
use crate::render::shaderpack::source::ShaderSourceFile;

fn source(extra: Vec<ShaderSourceFile>) -> ShaderPackSource {
    let mut files = vec![
        ShaderSourceFile::new(
            "world0/gbuffers_terrain.vsh",
            "#version 130\nvoid main() {}\n",
        ),
        ShaderSourceFile::new(
            "world0/gbuffers_terrain.fsh",
            "#version 130\n#define CLOUD_STYLE 1\nvoid main() {}\n",
        ),
        ShaderSourceFile::new(
            "world0/composite.fsh",
            "#version 130\n#define CLOUD_STYLE 1\nvoid main() {}\n",
        ),
    ];
    files.extend(extra);
    ShaderPackSource::new("protocol", 7, files).unwrap()
}

#[test]
fn legacy_sampler_aliases_preserve_distinct_owned_resources() {
    let source = source(vec![]);
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    for slot in 0..8 {
        let output = bindings
            .shader_pack_color_output_for_slot(slot as u32)
            .unwrap();
        assert!(!output.diagnostic_name().contains("colortex"));
        if slot < 4 {
            assert!(bindings.role_for(&format!("colortex{slot}")).is_none());
            assert!(bindings.role_for(COLOR_ALIASES[slot]).is_none());
        } else {
            assert_eq!(
                Some(output.clone()),
                bindings.role_for(&format!("colortex{slot}"))
            );
            assert_eq!(Some(output), bindings.role_for(COLOR_ALIASES[slot]));
        }
    }
    assert_ne!(
        bindings.role_for("shadowtex0"),
        bindings.role_for("shadowtex1")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::ShadowColor),
        bindings.role_for("shadowcolor0")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialAtlas),
        bindings.role_for("tex")
    );
    assert!(bindings.role_for("invented_sampler").is_none());
    assert_eq!(
        TerrainSourceResourceRole::ShadowDepthRaw,
        bindings
            .role_for("shadowtex0")
            .unwrap()
            .resolve_sampled_declaration("sampler2D")
            .unwrap()
    );
    assert_eq!(
        TerrainSourceResourceRole::ShadowDepthRawSecondary,
        bindings
            .role_for("shadowtex1")
            .unwrap()
            .resolve_sampled_declaration("sampler2D")
            .unwrap()
    );
    let arbitrary = ShaderPackSource::new(
        "arbitrary",
        1,
        vec![
            ShaderSourceFile::new("terrain.fsh", "void main() {}"),
            ShaderSourceFile::new("terrain.vsh", "void main() {}"),
        ],
    )
    .unwrap();
    assert!(TerrainSourceResourceBindings::from_source(&arbitrary)
        .unwrap()
        .role_for("tex")
        .is_none());
}

#[test]
fn legacy_sampler_stage_resolves_texture_domain_and_option_selected_assets() {
    let source = source(vec![ShaderSourceFile::new(
        "shaders.properties",
        concat!(
            "#if CLOUD_STYLE == 1\ntexture.gbuffers.gaux2 = textures/clouds.png\n",
            "#else\ntexture.gbuffers.gaux2 = textures/other.png\n#endif\n",
            "texture.gbuffers.noisetex = textures/water.png\n",
            "texture.deferred.gaux4 = textures/volume.png\n",
            "texture.gbuffers.colortex6 = textures/surface.png\n",
        ),
    )]);
    let pack_wide = TerrainSourceResourceBindings::from_source(&source).unwrap();
    assert!(pack_wide.role_for("gaux2").is_none());
    assert!(pack_wide.role_for("gaux4").is_none());
    let stage = |path: &str| TerrainSourceStage {
        path: path.to_owned(),
        defines: BTreeMap::new(),
    };
    let terrain = TerrainSourceResourceBindings::from_source_stage(
        &source,
        &stage("world0/gbuffers_terrain.fsh"),
    )
    .unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "textures/clouds.png".to_owned()
        )),
        terrain.role_for("gaux2")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "textures/water.png".to_owned()
        )),
        terrain.role_for("noisetex")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialAtlas),
        terrain.role_for("tex")
    );
    assert_eq!(terrain.role_for("colortex7"), terrain.role_for("gaux4"));
    // Frozen's override is for the declared sampler spelling, not every alias.
    assert_ne!(terrain.role_for("gaux2"), terrain.role_for("colortex5"));
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "textures/surface.png".to_owned()
        )),
        terrain.role_for("colortex6")
    );
    assert_eq!(
        pack_wide.shader_pack_color_output_for_slot(6).unwrap(),
        terrain.shader_pack_color_output_for_slot(6).unwrap()
    );
    let composite =
        TerrainSourceResourceBindings::from_source_stage(&source, &stage("world0/composite.fsh"))
            .unwrap();
    assert_eq!(composite.role_for("colortex0"), composite.role_for("tex"));
    assert_eq!(composite.role_for("colortex5"), composite.role_for("gaux2"));
    assert_eq!(
        Some(TerrainSourceResourceRole::Noise),
        composite.role_for("noisetex")
    );
    assert_eq!(composite.role_for("colortex7"), composite.role_for("gaux4"));
}

#[test]
fn legacy_sampler_explicit_manifest_remains_authoritative_and_assets_fail_closed() {
    let explicit = source(vec![ShaderSourceFile::new(
        TERRAIN_RESOURCE_BINDINGS_PATH,
        "tex=material_texture\n",
    )]);
    let bindings = TerrainSourceResourceBindings::from_source(&explicit).unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialTexture),
        bindings.role_for("tex")
    );
    assert!(bindings.role_for("shadowtex0").is_none());
    let stage = TerrainSourceStage {
        path: "world0/gbuffers_terrain.fsh".to_owned(),
        defines: BTreeMap::new(),
    };
    for declaration in [
        "../escape.png",
        "image.raw TEXTURE_3D rgba16f 8 8 8 rgba float",
        "image.jpg",
    ] {
        let invalid = source(vec![ShaderSourceFile::new(
            "shaders.properties",
            format!("texture.gbuffers.gaux4={declaration}\n"),
        )]);
        assert!(TerrainSourceResourceBindings::from_source_stage(&invalid, &stage).is_err());
    }
}

mod owned_images;

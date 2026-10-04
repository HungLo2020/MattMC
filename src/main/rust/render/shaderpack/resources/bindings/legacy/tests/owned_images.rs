use super::*;

const IMAGES: &str = concat!(
    "image.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\n",
    "image.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\n",
    "image.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n",
    "image.puddle_img = puddle_sampler red_integer r8ui unsigned_int true false 128 128\n",
);

fn stage(defines: BTreeMap<String, String>) -> TerrainSourceStage {
    TerrainSourceStage {
        path: "world0/composite.fsh".to_owned(),
        defines,
    }
}

#[test]
fn legacy_owned_image_aliases_resolve_distinct_sampled_fields_and_storage_writers() {
    let source = source(vec![ShaderSourceFile::new("shaders.properties", IMAGES)]);
    let bindings =
        TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new())).unwrap();
    for (sampler, image, role, sampler_type) in [
        (
            "voxel_sampler",
            "voxel_img",
            TerrainSourceResourceRole::ColoredVoxelOccupancy,
            "usampler3D",
        ),
        (
            "floodfill_sampler",
            "floodfill_img",
            TerrainSourceResourceRole::ColoredVoxelLightCurrent,
            "sampler3D",
        ),
        (
            "floodfill_sampler_copy",
            "floodfill_img_copy",
            TerrainSourceResourceRole::ColoredVoxelLightPrevious,
            "sampler3D",
        ),
        (
            "puddle_sampler",
            "puddle_img",
            TerrainSourceResourceRole::PuddleOccupancy,
            "usampler2D",
        ),
    ] {
        assert_eq!(Some(role.clone()), bindings.role_for(sampler));
        assert_eq!(Some(role.clone()), bindings.role_for(image));
        assert_eq!(
            role,
            bindings
                .role_for(sampler)
                .unwrap()
                .resolve_sampled_declaration(sampler_type)
                .unwrap()
        );
    }
    assert_ne!(
        bindings.role_for("floodfill_sampler"),
        bindings.role_for("floodfill_sampler_copy")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::MainDepthPrevious),
        bindings.role_for("depthtex2")
    );
}

#[test]
fn legacy_owned_image_aliases_follow_selected_property_branches() {
    let source = source(vec![ShaderSourceFile::new(
        "shaders.properties",
        format!("#if IMAGE_FIELDS_ACTIVE == 1\n{IMAGES}#endif\n"),
    )]);
    for enabled in [false, true] {
        let bindings = TerrainSourceResourceBindings::from_source_stage(
            &source,
            &stage(BTreeMap::from([(
                "IMAGE_FIELDS_ACTIVE".to_owned(),
                if enabled { "1" } else { "0" }.to_owned(),
            )])),
        )
        .unwrap();
        assert_eq!(enabled, bindings.role_for("floodfill_sampler").is_some());
        assert_eq!(enabled, bindings.role_for("puddle_img").is_some());
    }
    // Pack-wide discovery cannot select a stage's conditional image branch.
    assert!(TerrainSourceResourceBindings::from_source(&source)
        .unwrap()
        .role_for("floodfill_sampler")
        .is_none());
}

#[test]
fn legacy_owned_image_aliases_reject_incompatible_shapes_and_persistence() {
    for value in [
        "image.voxel_img = voxel_sampler rgba rgba16f half_float true false 128 64 128\n",
        "image.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 64\n",
        "image.floodfill_img = floodfill_sampler rgba rgba16f half_float true false 128 64 128\n",
        "image.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 32 128\n",
        "image.puddle_img = puddle_sampler red_integer r8ui unsigned_int true false 256 256\n",
        "image.puddle_img = puddle_sampler red_integer r8ui unsigned_int true false 128 128 128\n",
    ] {
        let source = source(vec![ShaderSourceFile::new("shaders.properties", value)]);
        assert!(
            TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new()))
                .is_err(),
            "{value}"
        );
    }
}

#[test]
fn legacy_owned_image_aliases_do_not_infer_roles_from_sampler_spelling() {
    let source = source(vec![ShaderSourceFile::new(
        "shaders.properties",
        "image.other = floodfill_sampler rgba rgba16f half_float false false 128 64 128\n",
    )]);
    let bindings =
        TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new())).unwrap();
    assert!(bindings.role_for("floodfill_sampler").is_none());
    assert!(bindings.role_for("other").is_none());
}

#[test]
fn legacy_owned_image_aliases_preserve_explicit_partial_manifests() {
    let source = source(vec![
        ShaderSourceFile::new("shaders.properties", IMAGES),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_texture\n"),
    ]);
    let bindings =
        TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new())).unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialTexture),
        bindings.role_for("tex")
    );
    assert!(bindings.role_for("floodfill_sampler").is_none());
    assert!(bindings.role_for("depthtex2").is_none());
}

#[test]
fn legacy_owned_image_aliases_reject_conflicting_resource_aliases() {
    for value in [
        "image.voxel_img = tex red_integer r8ui unsigned_int true false 128 64 128\n",
        "image.voxel_img = volume red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = volume rgba rgba16f half_float false false 128 64 128\n",
    ] {
        let source = source(vec![ShaderSourceFile::new("shaders.properties", value)]);
        assert!(TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new())).is_err(), "{value}");
    }
}

#[test]
fn legacy_owned_image_aliases_respect_final_property_value_without_retaining_old_aliases() {
    let mut properties = String::new();
    for ordinal in 0..1024 {
        properties.push_str(&format!("image.voxel_img = obsolete_{ordinal} red_integer r8ui unsigned_int true false 128 64 128\n"));
    }
    properties.push_str(
        "image.voxel_img = selected red_integer r8ui unsigned_int true false 128 64 128\n",
    );
    let source = source(vec![ShaderSourceFile::new(
        "shaders.properties",
        properties,
    )]);
    let bindings =
        TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new())).unwrap();
    assert!(bindings.role_for("obsolete_0").is_none());
    assert!(bindings.role_for("obsolete_1023").is_none());
    assert_eq!(
        Some(TerrainSourceResourceRole::ColoredVoxelOccupancy),
        bindings.role_for("selected")
    );
    assert!(bindings.names().count() < 64);
}

#[test]
fn legacy_owned_image_aliases_reject_mixed_volume_extents() {
    let source = source(vec![ShaderSourceFile::new(
        "shaders.properties",
        concat!(
            "image.floodfill_img = current rgba rgba16f half_float false false 128 64 128\n",
            "image.floodfill_img_copy = previous rgba rgba16f half_float false false 256 128 256\n",
        ),
    )]);
    assert!(
        TerrainSourceResourceBindings::from_source_stage(&source, &stage(BTreeMap::new())).is_err()
    );
}

#[test]
fn legacy_owned_image_aliases_shadow_shares_gbuffers_overrides_with_shadow_defines() {
    let source = source(vec![
        ShaderSourceFile::new(
            "world0/shadow.fsh",
            "#version 130\n#define SHADOW\nvoid main() {}\n",
        ),
        ShaderSourceFile::new(
            "shaders.properties",
            concat!(
                "#ifdef SHADOW\ntexture.gbuffers.gaux4 = textures/shadow.png\n",
                "#else\ntexture.gbuffers.gaux4 = textures/world.png\n#endif\n",
            ),
        ),
    ]);
    let shadow = TerrainSourceResourceBindings::from_source_stage(
        &source,
        &TerrainSourceStage {
            path: "world0/shadow.fsh".to_owned(),
            defines: BTreeMap::new(),
        },
    )
    .unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "textures/shadow.png".to_owned()
        )),
        shadow.role_for("gaux4")
    );
    let terrain = TerrainSourceResourceBindings::from_source_stage(
        &source,
        &TerrainSourceStage {
            path: "world0/gbuffers_terrain.fsh".to_owned(),
            defines: BTreeMap::new(),
        },
    )
    .unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "textures/world.png".to_owned()
        )),
        terrain.role_for("gaux4")
    );
}

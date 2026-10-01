use crate::render::shaderpack::resources::bindings::*;
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test;
use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::contracts::terrain::{
    TerrainMaterialClass, TerrainPassOperation, TerrainPassOutput, TerrainSourcePassKind,
    UnsupportedTerrainFeature,
};

fn contract(
    inputs: BTreeSet<TerrainPassInput>,
    required_resources: BTreeSet<TerrainPassRequiredResource>,
) -> TerrainPassContract {
    TerrainPassContract {
        pass_kind: TerrainSourcePassKind::OpaqueCutout,
        pack_name: "test".to_string(),
        generation: 1,
        program_path: "gbuffers_terrain.fsh".to_string(),
        material_classes: BTreeSet::from([TerrainMaterialClass::Opaque]),
        inputs,
        outputs: BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
        output_color_slots: BTreeMap::from([(TerrainPassOutput::LitTerrainColor, 0)]),
        property_defines: BTreeMap::new(),
        material_ids: BTreeMap::new(),
        runtime_block_state_material_ids: None,
        operations: vec![TerrainPassOperation::AtlasSample],
        required_resources,
        voxel_light_volume_requirements: None,
        translucent_raster_state: None,
        unsupported: BTreeSet::<UnsupportedTerrainFeature>::new(),
    }
}

#[test]
fn parses_bounded_semantic_resource_roles_without_native_state() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "tex=material_atlas\nvoxel_sampler=colored_voxel_occupancy\nfloodfill_sampler=colored_voxel_light_current\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialAtlas),
        bindings.role_for("tex")
    );
    assert_eq!(
        "usampler3D",
        bindings
            .role_for("voxel_sampler")
            .unwrap()
            .expected_sampler_type()
    );
}

#[test]
fn bundled_pack_declares_voxel_sampler_as_owned_occupancy_volume() {
    let source = complete_bundled_pack_source_for_test();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();

    assert_eq!(
        Some(TerrainSourceResourceRole::ColoredVoxelOccupancy),
        bindings.role_for("voxel_sampler")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::PuddleOccupancy),
        bindings.role_for("puddle_sampler")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::PuddleOccupancy),
        bindings.role_for("puddle_img")
    );
}

#[test]
fn puddle_occupancy_is_an_unsigned_2d_sampler_and_storage_contract() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "puddle_sampler=puddle_occupancy\npuddle_img=puddle_occupancy\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let role = bindings.role_for("puddle_sampler").unwrap();

    assert_eq!(TerrainSourceResourceRole::PuddleOccupancy, role);
    assert_eq!("puddle_occupancy", role.semantic_name());
    assert_eq!("usampler2D", role.expected_sampler_type());
    assert_eq!(Some("uimage2D"), role.expected_storage_image_type());
    assert_eq!(
        TerrainSourceSampledResourceShape::UnsignedTexture2d,
        role.expected_sampled_resource_shape()
    );
    assert_eq!(Some(role.clone()), bindings.role_for("puddle_img"));
}

#[test]
fn pack_texture_roles_keep_normalized_asset_identity() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "cloud_noise=pack_texture:lib/textures/cloud-water.png\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "lib/textures/cloud-water.png".to_string()
        )),
        bindings.role_for("cloud_noise")
    );
    assert_eq!(
        Some("lib/textures/cloud-water.png"),
        bindings
            .role_for("cloud_noise")
            .as_ref()
            .and_then(TerrainSourceResourceRole::pack_texture_path)
    );

    let malformed = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "cloud_noise=pack_texture:../escape.png\n",
        )],
    )
    .unwrap();
    assert!(TerrainSourceResourceBindings::from_source(&malformed)
        .unwrap_err()
        .to_string()
        .contains("normalized relative"));
}

#[test]
fn distinguishes_raw_compare_and_color_shadow_inputs() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            concat!(
                "shadow_compare=shadow_depth_primary\n",
                "shadow_compare_secondary=shadow_depth_secondary\n",
                "shadow_raw=shadow_depth_raw\n",
                "shadow_color=shadow_color\n",
                "normals=material_normal_map\n",
                "specular=material_specular_map\n",
            ),
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::ShadowDepthPrimary),
        bindings.role_for("shadow_compare")
    );
    assert_eq!(
        "sampler2DShadow",
        bindings
            .role_for("shadow_compare")
            .unwrap()
            .expected_sampler_type()
    );
    assert_eq!(
        "sampler2D",
        bindings
            .role_for("shadow_raw")
            .unwrap()
            .expected_sampler_type()
    );
    assert_eq!(
        "sampler2DShadow",
        bindings
            .role_for("shadow_compare_secondary")
            .unwrap()
            .expected_sampler_type()
    );
    assert_eq!(
        "material_normal_map",
        bindings.role_for("normals").unwrap().semantic_name()
    );
    assert_eq!(
        "material_specular_map",
        bindings.role_for("specular").unwrap().semantic_name()
    );
}

#[test]
fn keeps_distant_depth_streams_distinct_from_shadow_depth() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            concat!(
                "dh_opaque=distant_horizons_opaque_depth\n",
                "dh_pre_translucent=distant_horizons_depth_before_translucency\n",
            ),
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    for name in ["dh_opaque", "dh_pre_translucent"] {
        let role = bindings.role_for(name).unwrap();
        assert_eq!("sampler2D", role.expected_sampler_type());
        assert_eq!(
            TerrainSourceSampledResourceShape::Texture2d,
            role.expected_sampled_resource_shape()
        );
        assert_ne!(TerrainSourceResourceRole::ShadowDepthRaw, role);
    }
}

#[test]
fn shader_pack_color_roles_are_named_semantics_not_attachment_indices() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "color=shader_pack_color:primary\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let role = bindings.role_for("color").unwrap();
    assert_eq!(Some("primary"), role.shader_pack_color_name());
    assert_eq!("shader_pack_color:primary", role.diagnostic_name());
    assert_eq!("sampler2D", role.expected_sampler_type());
    assert_eq!(
        TerrainSourceSampledResourceShape::Texture2d,
        role.expected_sampled_resource_shape()
    );
    assert!(ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "color=shader_pack_color:colortex0\n",
        )],
    )
    .and_then(|source| TerrainSourceResourceBindings::from_source(&source))
    .is_err());
}

#[test]
fn permits_semantic_resource_aliases_but_rejects_duplicate_names_or_unknown_roles() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "tex=material_atlas\nother=material_atlas\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialAtlas),
        bindings.role_for("tex")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialAtlas),
        bindings.role_for("other")
    );
    let duplicate_name = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "tex=material_atlas\ntex=material_normal_map\n",
        )],
    )
    .unwrap();
    assert!(TerrainSourceResourceBindings::from_source(&duplicate_name).is_err());
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "tex=bogus\n",
        )],
    )
    .unwrap();
    assert!(TerrainSourceResourceBindings::from_source(&source).is_err());
}

#[test]
fn availability_set_requires_each_role_to_use_its_semantic_sample_shape() {
    let resources = TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 3,
            },
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::ColoredVoxelLightCurrent,
                shape: TerrainSourceSampledResourceShape::FloatTexture3d,
                resource_generation: 4,
            },
        ],
    )
    .unwrap();
    assert_eq!(7, resources.shader_pack_generation());
    assert_eq!(12, resources.world_generation());
    assert_eq!(
        Some(TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::MaterialAtlas,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 3,
        }),
        resources.resource_for(TerrainSourceResourceRole::MaterialAtlas)
    );
    assert!(TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 4,
        }],
    )
    .unwrap_err()
    .to_string()
    .contains("requires UnsignedTexture3d"));

    assert!(TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::PuddleOccupancy,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 4,
        }],
    )
    .unwrap_err()
    .to_string()
    .contains("requires UnsignedTexture2d"));
    assert!(TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::Noise,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 1,
            },
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::Noise,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 2,
            },
        ],
    )
    .unwrap_err()
    .to_string()
    .contains("available more than once"));
    assert!(TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::MaterialAtlas,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 0,
        }],
    )
    .unwrap_err()
    .to_string()
    .contains("has no owned generation"));
}

#[test]
fn contract_requirements_reject_missing_semantic_roles_before_binding() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
                "tex=material_atlas\nshadowtex0=shadow_depth\nfloodfill=colored_voxel_light_current\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let contract = contract(
        BTreeSet::from([TerrainPassInput::AtlasColor, TerrainPassInput::ShadowMap]),
        BTreeSet::from([TerrainPassRequiredResource::ColoredVoxelLightVolume]),
    );

    assert!(bindings
        .require_contract_roles(&contract)
        .unwrap_err()
        .to_string()
        .contains("colored_voxel_light_previous"));
}

#[test]
fn contract_requirements_accept_complete_semantic_roles() {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            TERRAIN_RESOURCE_BINDINGS_PATH,
            "tex=material_atlas\nshadowtex0=shadow_depth\nvoxel=colored_voxel_occupancy\nfloodfill=colored_voxel_light_current\nfloodfill_copy=colored_voxel_light_previous\n",
        )],
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let contract = contract(
        BTreeSet::from([TerrainPassInput::AtlasColor, TerrainPassInput::ShadowMap]),
        BTreeSet::from([TerrainPassRequiredResource::ColoredVoxelLightVolume]),
    );

    bindings.require_contract_roles(&contract).unwrap();
}

#[test]
fn owned_resource_set_requires_exact_available_combined_samplers() {
    let availability = TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::MaterialAtlas,
            shape: TerrainSourceSampledResourceShape::Texture2d,
            resource_generation: 3,
        }],
    )
    .unwrap();
    let sampler = Handle::new(HandleKind::CombinedTextureSampler, 4, 1).unwrap();
    let owned = TerrainSourceOwnedResourceSet::new(
        availability.clone(),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::MaterialAtlas,
            combined_sampler: sampler,
        }],
    )
    .unwrap();
    assert_eq!(
        Some(sampler),
        owned.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
    );

    assert!(TerrainSourceOwnedResourceSet::new(availability.clone(), []).is_err());
    assert!(TerrainSourceOwnedResourceSet::new(
        availability.clone(),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::MaterialAtlas,
            combined_sampler: Handle::new(HandleKind::Sampler, 4, 1).unwrap(),
        }],
    )
    .is_err());
    assert!(TerrainSourceOwnedResourceSet::new(
        availability,
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::Noise,
            combined_sampler: sampler,
        }],
    )
    .is_err());
}

#[test]
fn owned_resource_generation_signature_is_semantic_and_stably_ordered() {
    let availability = TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::Noise,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 9,
            },
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 3,
            },
        ],
    )
    .unwrap();
    let resources = TerrainSourceOwnedResourceSet::new(
        availability,
        [
            TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::Noise,
                combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 4, 1)
                    .unwrap(),
            },
            TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::MaterialAtlas,
                combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 5, 1)
                    .unwrap(),
            },
        ],
    )
    .unwrap();

    assert_eq!(
        vec![
            (TerrainSourceResourceRole::MaterialAtlas, 3),
            (TerrainSourceResourceRole::Noise, 9),
        ],
        resources.generation_signature()
    );
}

#[test]
fn sampled_resource_override_replaces_only_the_named_semantic_role() {
    let availability = TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::MaterialAtlas,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 3,
            },
            TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::Noise,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: 9,
            },
        ],
    )
    .unwrap();
    let atlas = Handle::new(HandleKind::CombinedTextureSampler, 4, 1).unwrap();
    let noise = Handle::new(HandleKind::CombinedTextureSampler, 5, 1).unwrap();
    let local = Handle::new(HandleKind::CombinedTextureSampler, 6, 1).unwrap();
    let resources = TerrainSourceOwnedResourceSet::new(
        availability,
        [
            TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::MaterialAtlas,
                combined_sampler: atlas,
            },
            TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::Noise,
                combined_sampler: noise,
            },
        ],
    )
    .unwrap();
    let overridden = resources
        .with_combined_sampler_override(TerrainSourceResourceRole::MaterialAtlas, local, 17)
        .unwrap();
    assert_eq!(
        Some(local),
        overridden.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
    );
    assert_eq!(
        Some(noise),
        overridden.combined_sampler_for(TerrainSourceResourceRole::Noise)
    );
    assert_eq!(
        vec![
            (TerrainSourceResourceRole::MaterialAtlas, 17),
            (TerrainSourceResourceRole::Noise, 9),
        ],
        overridden.generation_signature()
    );
    assert!(resources
        .with_combined_sampler_override(
            TerrainSourceResourceRole::MaterialAtlas,
            Handle::new(HandleKind::Sampler, 6, 1).unwrap(),
            17,
        )
        .is_err());
}

#[test]
fn owned_resource_set_keeps_storage_views_distinct_from_samplers() {
    let availability = TerrainSourceResourceAvailabilitySet::new(
        7,
        12,
        [TerrainSourceResourceAvailability {
            role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
            shape: TerrainSourceSampledResourceShape::UnsignedTexture3d,
            resource_generation: 3,
        }],
    )
    .unwrap();
    let view = Handle::new(HandleKind::TextureView, 4, 1).unwrap();
    let resources = TerrainSourceOwnedResourceSet::with_storage_resources(
        availability.clone(),
        [],
        [TerrainSourceOwnedStorageResource {
            role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
            texture_view: view,
        }],
    )
    .unwrap();
    assert_eq!(
        Some(view),
        resources.storage_texture_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
    );
    assert_eq!(
        None,
        resources.combined_sampler_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
    );
    assert_eq!(1, resources.len());

    assert!(TerrainSourceOwnedResourceSet::with_storage_resources(
        availability,
        [],
        [TerrainSourceOwnedStorageResource {
            role: TerrainSourceResourceRole::ColoredVoxelOccupancy,
            texture_view: Handle::new(HandleKind::Sampler, 4, 1).unwrap(),
        }],
    )
    .is_err());
}

#[test]
fn semantic_resource_subsets_merge_only_with_exact_generations_and_roles() {
    let availability = |role: TerrainSourceResourceRole, generation, world_generation| {
        TerrainSourceResourceAvailabilitySet::new(
            generation,
            world_generation,
            [TerrainSourceResourceAvailability {
                role: role.clone(),
                shape: role.expected_sampled_resource_shape(),
                resource_generation: 3,
            }],
        )
        .unwrap()
    };
    let noise = TerrainSourceOwnedResourceSet::new(
        availability(TerrainSourceResourceRole::Noise, 7, 12),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::Noise,
            combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 1, 1).unwrap(),
        }],
    )
    .unwrap();
    let atlas = TerrainSourceOwnedResourceSet::new(
        availability(TerrainSourceResourceRole::MaterialAtlas, 7, 12),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::MaterialAtlas,
            combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 2, 1).unwrap(),
        }],
    )
    .unwrap();
    let merged = TerrainSourceOwnedResourceSet::merge([&noise, &atlas]).unwrap();
    assert_eq!(
        Some(Handle::new(HandleKind::CombinedTextureSampler, 1, 1).unwrap()),
        merged.combined_sampler_for(TerrainSourceResourceRole::Noise)
    );
    assert_eq!(
        Some(Handle::new(HandleKind::CombinedTextureSampler, 2, 1).unwrap()),
        merged.combined_sampler_for(TerrainSourceResourceRole::MaterialAtlas)
    );
    let stale = TerrainSourceOwnedResourceSet::new(
        availability(TerrainSourceResourceRole::ShadowColor, 8, 12),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::ShadowColor,
            combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 3, 1).unwrap(),
        }],
    )
    .unwrap();
    assert!(TerrainSourceOwnedResourceSet::merge([&noise, &stale]).is_err());
    assert!(TerrainSourceOwnedResourceSet::merge([&noise, &noise]).is_err());

    let exact_duplicate = noise.excluding_roles_already_owned_by(&noise).unwrap();
    assert_eq!(0, exact_duplicate.len());

    let conflicting_noise = TerrainSourceOwnedResourceSet::new(
        availability(TerrainSourceResourceRole::Noise, 7, 12),
        [TerrainSourceOwnedResource {
            role: TerrainSourceResourceRole::Noise,
            combined_sampler: Handle::new(HandleKind::CombinedTextureSampler, 9, 1).unwrap(),
        }],
    )
    .unwrap();
    assert!(conflicting_noise
        .excluding_roles_already_owned_by(&noise)
        .unwrap_err()
        .to_string()
        .contains("conflicts with an earlier source-stage binding"));
}

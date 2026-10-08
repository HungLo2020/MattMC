use crate::render::worldrender::lod::*;
use crate::render::vulkanic::test_support::{MockBackend, presentation_capabilities, vulkan_capabilities};
use crate::render::vulkanic::handles::HandleKind;
use crate::render::vulkanic::resources::{
    Extent3d, SamplerAddressMode, SamplerDesc, SamplerFilter, TextureDesc, TextureDimension,
    TextureUsage, TextureViewDesc,
};
use crate::render::shaderpack::contracts::distant_horizons::derive_distant_horizons_opaque_contract;
use crate::render::shaderpack::runtime::fullscreen::FullscreenSourcePassFrame;
use crate::render::shaderpack::lowering::lower_distant_horizons_source_pair;
use crate::render::shaderpack::lowering::{
    TerrainSourceOpaqueResourceBindingPlan, TerrainSourceOpaqueResourceKind,
};
use crate::render::shaderpack::source::preprocess::{
    complete_bundled_pack_source_for_test, preprocess_distant_horizons_sources,
};
use crate::render::shaderpack::programs::{
    prepare_lowered_distant_horizons_source_program, TerrainSourceTextureTransforms,
};
use crate::render::shaderpack::runtime::ShaderPackRuntimeExecutor;
use crate::render::shaderpack::resources::color_targets::ShaderPackColorClearValues;
use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet,
    TerrainSourceOwnedStorageResource, TerrainSourceResourceAvailability,
    TerrainSourceResourceAvailabilitySet, TerrainSourceResourceBindings,
    TerrainSourceResourceRole, TerrainSourceSampledResourceShape,
};
use crate::render::worldrender::WorldLodColumnAsset;
use crate::render::worldrender::WorldLodColumnMaterialProvenance;
use crate::render::worldrender::WorldLodFaceMaterial;
use crate::render::worldrender::WorldLodMaterialIdentity;
use crate::render::worldrender::WorldLodRenderFrame;
use crate::render::worldrender::WorldLodSegment;
use crate::render::worldrender::WorldLodSegmentMaterialProvenance;
use crate::render::worldrender::WorldLodVertex;
use crate::render::scene::lod::WORLD_LOD_MATERIAL_MIXED;
use crate::render::scene::lod::WORLD_LOD_MATERIAL_UNAVAILABLE;
use crate::render::scene::lod::WORLD_LOD_VERTEX_LAYOUT_V1;
use crate::render::vulkanic::{CommandList, CommandListDesc, SubmissionBatch};

#[test]
fn world_lod_frame_plan_reuses_bounded_capacity_and_drops_spikes() {
    let mut plan = WorldLodFramePlan::default();
    plan.opaque_draws.reserve(4);
    let retained_capacity = plan.opaque_draws.capacity();
    plan.prepare_for_reuse();
    assert!(plan.opaque_draws.is_empty());
    assert_eq!(plan.opaque_draws.capacity(), retained_capacity);

    plan.opaque_draws
        .reserve(MAX_REUSABLE_WORLD_LOD_PLAN_ELEMENTS + 1);
    assert!(plan.opaque_draws.capacity() > MAX_REUSABLE_WORLD_LOD_PLAN_ELEMENTS);
    plan.prepare_for_reuse();
    assert!(plan.opaque_draws.is_empty());
    assert_eq!(plan.opaque_draws.capacity(), 0);
}

fn asset() -> WorldLodColumnAsset {
    WorldLodColumnAsset {
        column_key: 7,
        column_generation: 3,
        vertex_layout_version: WORLD_LOD_VERTEX_LAYOUT_V1,
        origin: [-128, 64, 256],
        segments: vec![WorldLodSegment {
            layer: WORLD_LOD_LAYER_OPAQUE,
            vertices: vec![
                WorldLodVertex {
                    local_position: [1, 2, 3],
                    packed_light_and_micro_offset: 0x0132,
                    color_rgba: [64, 128, 255, 192],
                    material_id: 4,
                    normal_index: 1,
                },
                WorldLodVertex {
                    local_position: [4, 2, 3],
                    packed_light_and_micro_offset: 0x0c21,
                    color_rgba: [64, 128, 255, 192],
                    material_id: 4,
                    normal_index: 1,
                },
                WorldLodVertex {
                    local_position: [4, 5, 3],
                    packed_light_and_micro_offset: 0x300f,
                    color_rgba: [64, 128, 255, 192],
                    material_id: 4,
                    normal_index: 1,
                },
                WorldLodVertex {
                    local_position: [1, 5, 3],
                    packed_light_and_micro_offset: 0x0000,
                    color_rgba: [64, 128, 255, 192],
                    material_id: 4,
                    normal_index: 1,
                },
            ],
        }],
    }
}

fn single_block_face_asset() -> WorldLodColumnAsset {
    let mut asset = asset();
    let vertices = &mut asset.segments[0].vertices;
    vertices[0].local_position = [1, 2, 3];
    vertices[1].local_position = [1, 2, 4];
    vertices[2].local_position = [2, 2, 4];
    vertices[3].local_position = [2, 2, 3];
    asset
}

#[test]
fn private_dh_source_raster_policy_matches_no_shader_states() {
    assert_eq!(TextureFormat::Rgba16Float, WORLD_LOD_RESOLVED_COLOR_FORMAT);
    assert_eq!(
        (BlendMode::Disabled, CompareOp::Less, true),
        private_dh_source_raster_policy(WorldLodPassClass::Opaque)
    );
    assert_eq!(
        (BlendMode::Alpha, CompareOp::Less, false),
        private_dh_source_raster_policy(WorldLodPassClass::TransparentSide)
    );
    assert_eq!(
        (BlendMode::Alpha, CompareOp::Less, true),
        private_dh_source_raster_policy(WorldLodPassClass::TransparentUp)
    );
    assert_eq!(
        (BlendMode::AlphaSource, CompareOp::Always, true),
        private_dh_source_raster_policy(WorldLodPassClass::WaterSurface)
    );
    assert_eq!(BlendMode::Alpha, private_dh_water_replay_blend_mode());
    assert_eq!(BlendMode::Disabled, private_dh_compositor_blend_mode());
}

fn lowered_source_program() -> LoweredDistantHorizonsSourceProgram {
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
    prepare_lowered_distant_horizons_source_program(&contract, &lowered, &bindings).unwrap()
}

/// Builds minimal Rust-owned physical resources for every active semantic
/// role in a lowered program. The fixture proves descriptor/pipeline
/// ownership and synchronization only; it deliberately supplies no Java,
/// Iris, or backend-native resources and does not stand in for runtime
/// texture/volume generation.
fn owned_resources_for_programs(
    gal: &mut VulkanicGal,
    bindings: &[&TerrainSourceOpaqueResourceBindingPlan],
    shader_pack_generation: u64,
    world_generation: u64,
) -> (TerrainSourceOwnedResourceSet, Vec<Handle>) {
    let mut seen = BTreeSet::new();
    let mut available = Vec::new();
    let mut sampled = Vec::new();
    let mut storage = Vec::new();
    let mut handles = Vec::new();
    for plan in bindings {
        for binding in plan.bindings() {
            let role = binding.role();
            // Named shader-pack colors and DH depth snapshots are owned
            // by their explicit target caches. This test helper supplies
            // only external semantic resources; duplicating either cache
            // role would make the binding conflict that production must
            // reject.
            if matches!(
                role,
                TerrainSourceResourceRole::ShaderPackColor(_)
                    | TerrainSourceResourceRole::DistantHorizonsOpaqueDepth
                    | TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency
            ) {
                continue;
            }
            if !seen.insert(role.clone()) {
                continue;
            }
            let shape = role.expected_sampled_resource_shape();
            let (dimension, format, extent) = match shape {
                TerrainSourceSampledResourceShape::Texture2d => (
                    TextureDimension::D2,
                    TextureFormat::Rgba8Unorm,
                    Extent3d {
                        width: 1,
                        height: 1,
                        depth: 1,
                    },
                ),
                TerrainSourceSampledResourceShape::UnsignedTexture2d => (
                    TextureDimension::D2,
                    TextureFormat::R8Uint,
                    Extent3d {
                        width: 1,
                        height: 1,
                        depth: 1,
                    },
                ),
                TerrainSourceSampledResourceShape::DepthCompareTexture2d => (
                    TextureDimension::D2,
                    TextureFormat::Depth32Float,
                    Extent3d {
                        width: 1,
                        height: 1,
                        depth: 1,
                    },
                ),
                TerrainSourceSampledResourceShape::UnsignedTexture3d => (
                    TextureDimension::D3,
                    TextureFormat::R8Uint,
                    Extent3d {
                        width: 1,
                        height: 1,
                        depth: 1,
                    },
                ),
                TerrainSourceSampledResourceShape::FloatTexture3d => (
                    TextureDimension::D3,
                    TextureFormat::Rgba16Float,
                    Extent3d {
                        width: 1,
                        height: 1,
                        depth: 1,
                    },
                ),
            };
            let storage_only = binding.kind() == TerrainSourceOpaqueResourceKind::StorageImage;
            let mut usages = vec![TextureUsage::Sampled];
            if storage_only {
                usages.push(TextureUsage::Storage);
            }
            let texture = gal
                .create_texture(TextureDesc {
                    label: format!("test.dh-source.{}.texture", role.diagnostic_name()),
                    dimension,
                    format,
                    extent,
                    mip_levels: 1,
                    array_layers: 1,
                    usages,
                })
                .unwrap();
            let view = gal
                .create_texture_view(TextureViewDesc {
                    label: format!("test.dh-source.{}.view", role.diagnostic_name()),
                    texture,
                    format,
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                })
                .unwrap();
            available.push(TerrainSourceResourceAvailability {
                role: role.clone(),
                shape,
                resource_generation: 1,
            });
            if storage_only {
                storage.push(TerrainSourceOwnedStorageResource {
                    role,
                    texture_view: view,
                });
                handles.extend([view, texture]);
                continue;
            }
            let sampler = gal
                .create_sampler(SamplerDesc {
                    label: format!("test.dh-source.{}.sampler", role.diagnostic_name()),
                    min_filter: SamplerFilter::Nearest,
                    mag_filter: SamplerFilter::Nearest,
                    mip_filter: SamplerFilter::Nearest,
                    address_u: SamplerAddressMode::ClampToEdge,
                    address_v: SamplerAddressMode::ClampToEdge,
                    address_w: SamplerAddressMode::ClampToEdge,
                    comparison: (shape
                        == TerrainSourceSampledResourceShape::DepthCompareTexture2d)
                        .then_some(CompareOp::LessOrEqual),
                })
                .unwrap();
            let combined = gal
                .create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("test.dh-source.{}.combined", role.diagnostic_name()),
                    texture_view: view,
                    sampler,
                })
                .unwrap();
            sampled.push(TerrainSourceOwnedResource {
                role,
                combined_sampler: combined,
            });
            handles.extend([combined, sampler, view, texture]);
        }
    }
    let resources = TerrainSourceOwnedResourceSet::with_storage_resources(
        TerrainSourceResourceAvailabilitySet::new(
            shader_pack_generation,
            world_generation,
            available,
        )
        .unwrap(),
        sampled,
        storage,
    )
    .unwrap();
    (resources, handles)
}

fn distant_source_uniforms() -> TerrainSourceUniformFrame {
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    TerrainSourceUniformFrame {
        frame_counter: Some(1),
        render_stage: Some(0),
        frame_modulo_eight: Some(1.0),
        world_time: Some(1),
        world_day: Some(1),
        moon_phase: Some(0),
        frame_time_seconds: Some(0.016),
        frame_time_counter: Some(1.0),
        frame_time_smooth: Some(0.016),
        aspect_ratio: Some(1.5),
        blindness: Some(0.0),
        darkness_factor: Some(0.0),
        max_blindness_darkness: Some(0.0),
        sun_angle: Some(0.25),
        sun_position: None,
        moon_position: None,
        shadow_light_position: None,
        up_position: None,
        celestial_is_moon: Some(0),
        celestial_alpha: Some(1.0),
        celestial_sun_path_rotation: Some(0.0),
        celestial_time_of_day: Some(0.0),
        rain_strength: Some(0.0),
        rain_factor: Some(0.0),
        thunder_strength: Some(0.0),
        sky_darken: Some(0.0),
        camera_world_position: Some([0.0, 64.0, 0.0]),
        camera_world_position_int: Some([0, 64, 0]),
        camera_world_position_fract: Some([0.0, 0.0, 0.0]),
        previous_camera_world_position: Some([0.0, 64.0, 0.0]),
        camera_velocity: Some(0.0),
        view_matrix: Some(identity),
        view_matrix_inverse: Some(identity),
        projection_matrix: Some(identity),
        projection_matrix_inverse: Some(identity),
        previous_view_matrix: Some(identity),
        previous_projection_matrix: Some(identity),
        shadow_model_view: Some(identity),
        shadow_model_view_inverse: Some(identity),
        shadow_projection: Some(identity),
        shadow_projection_inverse: Some(identity),
        distant_model_view: Some(identity),
        distant_projection: Some(identity),
        distant_projection_inverse: Some(identity),
        viewport_width: Some(96.0),
        viewport_height: Some(64.0),
        near_plane: Some(0.05),
        eye_submersion: Some(0),
        screen_brightness: Some(1.0),
        darkness_light_factor: Some(0.0),
        night_vision: Some(0.0),
        eye_brightness: Some([240, 240]),
        eye_brightness_smooth: None,
        eye_brightness_m: Some(1.0),
        eye_brightness_m2: Some(1.0),
        fog_color: Some([0.5, 0.6, 0.7]),
        legacy_fog_parameter_color: Some([0.5, 0.6, 0.7, 1.0]),
        legacy_fog_environmental_start: Some(0.0),
        legacy_fog_environmental_end: Some(128.0),
        biome_precipitation: Some(0),
        biome_resource_location: Some("minecraft:plains".to_string()),
        biome_dry: Some(0.0),
        biome_snowy: Some(0.0),
        biome_nether_wastes: Some(0.0),
        biome_crimson_forest: Some(0.0),
        biome_warped_forest: Some(0.0),
        biome_basalt_deltas: Some(0.0),
        biome_soul_valley: Some(0.0),
        biome_pale_garden: Some(0.0),
        biome_rainy: Some(0.0),
        wetness: Some(0.0),
        sky_color: Some([0.5, 0.6, 0.7]),
        material_atlas_size: Some([256, 256]),
        far_plane: Some(128.0),
        distant_horizons_render_distance: Some(256),
        relative_eye_position: Some([0.0, 0.0, 0.0]),
        entity_id: Some(-1),
        entity_color: None,
        current_rendered_item_id: Some(-1),
        block_entity_id: Some(-1),
        held_item_id_main: Some(0),
        held_item_id_off_hand: Some(0),
        held_block_light_main: Some(0),
        held_block_light_off_hand: Some(0),
    }
}

fn lightmap_binding(gal: &mut VulkanicGal) -> (VanillaLightmapBinding, [Handle; 3]) {
    let texture = gal
        .create_texture(TextureDesc {
            label: "test.world-lod-lightmap.texture".to_string(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 16,
                height: 16,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::Sampled],
        })
        .unwrap();
    let sampler = gal
        .create_sampler(SamplerDesc {
            label: "test.world-lod-lightmap.sampler".to_string(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    let texture_view = gal
        .create_texture_view(TextureViewDesc {
            label: "test.world-lod-lightmap.view".to_string(),
            texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    (
        VanillaLightmapBinding {
            world_generation: 17,
            lightmap_generation: 9,
            texture_view,
            sampler,
        },
        [texture_view, sampler, texture],
    )
}

#[test]
fn expands_dh_quad_layout_without_backend_vertex_assumptions() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let segment = &expanded.segments[0];
    assert_eq!(vec![0, 1, 2, 2, 3, 0], segment.indices);
    assert_eq!([-128, 64, 256], expanded.origin);
    assert_eq!([0.01, 0.0, 0.0], segment.vertices[0].micro_offset);
    assert_eq!([0.0, -0.01, 0.0], segment.vertices[1].micro_offset);
    assert_eq!([0.0, 0.0, -0.01], segment.vertices[2].micro_offset);
    assert_eq!(2, segment.vertices[0].sky_light);
    assert_eq!(3, segment.vertices[0].block_light);
    assert_eq!(
        WorldLodMaterialCategory::Metal,
        segment.vertices[0].material
    );
    assert_eq!(WorldLodFaceNormal::Up, segment.vertices[0].normal);
}

#[test]
fn source_pipeline_uses_the_dh_quad_raster_convention() {
    // QuadElementBuffer emits 0,1,2,2,3,0. RasterYDirection::Up is a GAL
    // coordinate contract; each backend realizes that contract without
    // changing the copied DH source winding, so no source-index rewrite is
    // needed on any backend.
    assert_eq!(FrontFace::CounterClockwise, world_lod_source_front_face());
}

#[test]
fn exact_atlas_plan_preserves_face_uv_orientation_without_guessing() {
    let asset = single_block_face_asset();
    let segment = &asset.segments[0];
    let material = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: false,
        tint_rgb: [1.0, 1.0, 1.0],
        atlas_identity: "minecraft:blocks".to_string(),
        sprite_identity: "minecraft:block/grass_block_top".to_string(),
        atlas_uv: [0.25, 0.5, 0.375, 0.625],
        uv_corner_order: 0x78,
        variant_position: 0,
    };

    let plan = plan_world_lod_textured_segment(segment, &[1], &[material.clone()]).unwrap();

    assert!(plan.unavailable.is_empty());
    assert_eq!(1, plan.quads.len());
    assert_eq!(
        [[1.0, 0.0], [1.0, 1.0], [0.0, 1.0], [0.0, 0.0]],
        plan.quads[0].vertices.map(|vertex| vertex.tile_uv)
    );
    assert_eq!(
        "minecraft:block/grass_block_top",
        plan.quads[0].sprite_identity
    );

    let rotated = WorldLodFaceMaterial {
        uv_corner_order: 0x2d,
        ..material
    };
    let plan = plan_world_lod_textured_segment(segment, &[1], &[rotated]).unwrap();
    assert_eq!(
        [[0.0, 0.0], [0.0, 1.0], [1.0, 1.0], [1.0, 0.0]],
        plan.quads[0].vertices.map(|vertex| vertex.tile_uv)
    );
}

#[test]
fn exact_atlas_plan_preserves_coplanar_base_and_cutout_overlay_layers() {
    let asset = single_block_face_asset();
    let segment = &asset.segments[0];
    let base = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: false,
        tint_rgb: [1.0, 1.0, 1.0],
        atlas_identity: "minecraft:textures/atlas/blocks.png".to_string(),
        sprite_identity: "minecraft:block/grass_block_side".to_string(),
        atlas_uv: [0.25, 0.5, 0.375, 0.625],
        uv_corner_order: 0x78,
        variant_position: 0,
    };
    let overlay = WorldLodFaceMaterial {
        face_layer: 1,
        tinted: true,
        tint_rgb: [0.25, 0.75, 0.25],
        sprite_identity: "minecraft:block/grass_block_side_overlay".to_string(),
        ..base.clone()
    };

    let plan = plan_world_lod_textured_segment(segment, &[1], &[base, overlay]).unwrap();

    assert!(plan.unavailable.is_empty());
    assert_eq!(2, plan.quads.len());
    assert_eq!(0, plan.quads[0].face_layer);
    assert!(!plan.quads[0].tinted);
    assert_eq!(
        "minecraft:block/grass_block_side",
        plan.quads[0].sprite_identity
    );
    assert_eq!(1, plan.quads[1].face_layer);
    assert!(plan.quads[1].tinted);
    assert_eq!(
        "minecraft:block/grass_block_side_overlay",
        plan.quads[1].sprite_identity
    );
    let packed = pack_world_lod_textured_column_asset(&WorldLodTexturedColumnPlan {
        column_key: asset.column_key,
        column_generation: asset.column_generation,
        segments: vec![plan],
    })
    .unwrap();
    assert_eq!(
        12,
        packed.segments[0].index_bytes.len() / std::mem::size_of::<u32>()
    );
    assert!(packed.unavailable_source_segments.is_empty());
}

#[test]
fn exact_atlas_uvs_follow_dh_serialized_vertex_order_for_every_face() {
    let expectations = [
        (0, [3, 2, 1, 0]),
        (1, [3, 2, 1, 0]),
        (2, [0, 1, 2, 3]),
        (3, [0, 1, 2, 3]),
        (4, [0, 3, 2, 1]),
        (5, [0, 3, 2, 1]),
    ];
    for (face, expected) in expectations {
        let actual = std::array::from_fn(|vertex| {
            world_lod_serialized_vertex_canonical_corner(face, vertex).unwrap()
        });
        assert_eq!(expected, actual, "face {face}");
    }
    assert!(world_lod_serialized_vertex_canonical_corner(6, 0).is_err());
    assert!(world_lod_serialized_vertex_canonical_corner(0, 4).is_err());
}

#[test]
fn exact_atlas_plan_rejects_incomplete_or_ambiguous_quad_provenance() {
    let asset = single_block_face_asset();
    let segment = &asset.segments[0];
    let material = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: false,
        tint_rgb: [1.0, 1.0, 1.0],
        atlas_identity: "minecraft:blocks".to_string(),
        sprite_identity: "minecraft:block/grass_block_top".to_string(),
        atlas_uv: [0.25, 0.5, 0.375, 0.625],
        uv_corner_order: 0x78,
        variant_position: 0,
    };

    let unavailable = plan_world_lod_textured_segment(
        segment,
        &[WORLD_LOD_MATERIAL_UNAVAILABLE],
        &[material.clone()],
    )
    .unwrap();
    assert_eq!(
        vec![WorldLodTexturedQuadUnavailable {
            quad_index: 0,
            material_id: WORLD_LOD_MATERIAL_UNAVAILABLE,
            face: 1,
            reason: WorldLodTexturedQuadUnavailableReason::MaterialUnavailable,
        }],
        unavailable.unavailable
    );

    let mixed = plan_world_lod_textured_segment(
        segment,
        &[WORLD_LOD_MATERIAL_MIXED],
        &[material.clone()],
    )
    .unwrap();
    assert_eq!(
        WorldLodTexturedQuadUnavailableReason::MaterialMixed,
        mixed.unavailable[0].reason
    );

    let missing = plan_world_lod_textured_segment(segment, &[1], &[]).unwrap();
    assert_eq!(
        WorldLodTexturedQuadUnavailableReason::MissingFaceMaterial,
        missing.unavailable[0].reason
    );

    let mut inconsistent = segment.clone();
    inconsistent.vertices[3].normal_index = 0;
    let inconsistent =
        plan_world_lod_textured_segment(&inconsistent, &[1], &[material]).unwrap();
    assert_eq!(
        WorldLodTexturedQuadUnavailableReason::InconsistentFace,
        inconsistent.unavailable[0].reason
    );
    assert_eq!(
        "material-unavailable",
        WorldLodTexturedQuadUnavailableReason::MaterialUnavailable.as_str()
    );
    assert_eq!(
        "missing-face-material",
        WorldLodTexturedQuadUnavailableReason::MissingFaceMaterial.as_str()
    );
}

#[test]
fn exact_atlas_plan_requires_the_weighted_model_position_that_selected_its_sprite() {
    let asset = single_block_face_asset();
    let segment = &asset.segments[0];
    let weighted = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: false,
        tint_rgb: [1.0, 1.0, 1.0],
        atlas_identity: "minecraft:textures/atlas/blocks.png".to_string(),
        sprite_identity: "minecraft:block/oak_leaves".to_string(),
        atlas_uv: [0.125, 0.25, 0.1875, 0.3125],
        uv_corner_order: 0x78,
        variant_position: 0x1f2e_3d4c_5b6a_7988,
    };
    let exact = plan_world_lod_textured_segment_with_variants(
        segment,
        &[1],
        &[crate::render::scene::lod::WORLD_LOD_VARIANT_EXACT],
        &[weighted.variant_position],
        &[weighted.clone()],
    )
    .unwrap();
    assert_eq!(1, exact.quads.len());
    assert!(exact.unavailable.is_empty());

    let wrong_position = plan_world_lod_textured_segment_with_variants(
        segment,
        &[1],
        &[crate::render::scene::lod::WORLD_LOD_VARIANT_EXACT],
        &[weighted.variant_position.wrapping_add(1)],
        &[weighted],
    )
    .unwrap();
    assert_eq!(
        WorldLodTexturedQuadUnavailableReason::MissingFaceMaterial,
        wrong_position.unavailable[0].reason
    );

    let mixed = plan_world_lod_textured_segment_with_variants(
        segment,
        &[1],
        &[crate::render::scene::lod::WORLD_LOD_VARIANT_MIXED],
        &[0],
        &[],
    )
    .unwrap();
    assert_eq!(
        WorldLodTexturedQuadUnavailableReason::VariantMixed,
        mixed.unavailable[0].reason
    );
}

#[test]
fn exact_atlas_plan_uses_neutral_tint_only_when_no_positioned_tint_exists() {
    let asset = single_block_face_asset();
    let segment = &asset.segments[0];
    let base = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: true,
        tint_rgb: [1.0, 1.0, 1.0],
        atlas_identity: "minecraft:textures/atlas/blocks.png".to_string(),
        sprite_identity: "minecraft:block/grass_block_side_overlay".to_string(),
        atlas_uv: [0.25, 0.5, 0.375, 0.625],
        uv_corner_order: 0x78,
        variant_position: 0,
    };
    let positioned = WorldLodFaceMaterial {
        tint_rgb: [0.2, 0.7, 0.3],
        variant_position: 0x91_0000_6200_0228,
        ..base.clone()
    };

    let exact = plan_world_lod_textured_segment_with_variants(
        segment,
        &[1],
        &[crate::render::scene::lod::WORLD_LOD_VARIANT_EXACT],
        &[positioned.variant_position],
        &[base.clone(), positioned],
    )
    .unwrap();
    assert_eq!(
        [0.2, 0.7, 0.3, 192.0 / 255.0],
        exact.quads[0].vertices[0].color_rgba
    );

    let merged = plan_world_lod_textured_segment_with_variants(
        segment,
        &[1],
        &[crate::render::scene::lod::WORLD_LOD_VARIANT_EXACT],
        &[0x91_0000_6200_0229],
        &[base],
    )
    .unwrap();
    assert_eq!(
        [1.0, 1.0, 1.0, 192.0 / 255.0],
        merged.quads[0].vertices[0].color_rgba
    );
    assert!(merged.unavailable.is_empty());
}

#[test]
fn exact_atlas_tint_preserves_source_alpha_for_translucent_materials() {
    let mut asset = single_block_face_asset();
    for vertex in &mut asset.segments[0].vertices {
        vertex.color_rgba[3] = 192;
    }
    let material = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: true,
        tint_rgb: [0.2, 0.7, 0.3],
        atlas_identity: "minecraft:textures/atlas/blocks.png".to_string(),
        sprite_identity: "minecraft:block/water_still".to_string(),
        atlas_uv: [0.25, 0.5, 0.375, 0.625],
        uv_corner_order: 0x78,
        variant_position: 0,
    };
    let plan = plan_world_lod_textured_segment(&asset.segments[0], &[1], &[material]).unwrap();
    assert_eq!(
        [0.2, 0.7, 0.3, 192.0 / 255.0],
        plan.quads[0].vertices[0].color_rgba
    );
}

#[test]
fn exact_atlas_column_plan_is_generation_and_segment_order_bound() {
    let asset = single_block_face_asset();
    let provenance = WorldLodColumnMaterialProvenance {
        column_key: asset.column_key,
        column_generation: asset.column_generation,
        identities: vec![WorldLodMaterialIdentity {
            block_state_identity: "minecraft:grass_block[snowy=false]".to_string(),
            biome_identity: "minecraft:plains".to_string(),
        }],
        segments: vec![WorldLodSegmentMaterialProvenance {
            layer: WORLD_LOD_LAYER_OPAQUE,
            segment_index: 0,
            quad_material_ids: vec![1],
            quad_variant_states: vec![
                crate::render::scene::lod::WORLD_LOD_VARIANT_EXACT,
            ],
            quad_variant_positions: vec![0],
        }],
        face_materials: vec![WorldLodFaceMaterial {
            material_id: 1,
            face: 1,
            face_layer: 0,
            tinted: false,
            tint_rgb: [1.0, 1.0, 1.0],
            atlas_identity: "minecraft:textures/atlas/blocks.png".to_string(),
            sprite_identity: "minecraft:block/grass_block_top".to_string(),
            atlas_uv: [0.25, 0.5, 0.375, 0.625],
            uv_corner_order: 0x78,
            variant_position: 0,
        }],
    };

    let plan = plan_world_lod_textured_column(&asset, &provenance).unwrap();
    assert_eq!(asset.column_key, plan.column_key);
    assert_eq!(asset.column_generation, plan.column_generation);
    assert_eq!(1, plan.segments.len());
    assert_eq!(1, plan.segments[0].quads.len());

    let packed = pack_world_lod_textured_column_asset(&plan).unwrap();
    assert_eq!(plan.column_key, packed.column_key);
    assert_eq!(plan.column_generation, packed.column_generation);
    assert_eq!(1, packed.segments.len());
    assert!(packed.unavailable_source_segments.is_empty());
    let segment = &packed.segments[0];
    assert_eq!(0, segment.source_segment_index);
    assert_eq!(WORLD_LOD_LAYER_OPAQUE, segment.layer);
    assert_eq!(
        WORLD_LOD_TEXTURED_GPU_VERTEX_LAYOUT_V2,
        segment.vertex_layout_version
    );
    assert_eq!(IndexType::U32, segment.index_type);
    assert_eq!(
        4 * WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES,
        segment.vertex_bytes.len()
    );
    assert_eq!(6 * std::mem::size_of::<u32>(), segment.index_bytes.len());
    assert_eq!(1.0f32.to_le_bytes(), segment.vertex_bytes[0..4]);
    assert_eq!(1.0f32.to_le_bytes(), segment.vertex_bytes[24..28]);
    assert_eq!(0.25f32.to_le_bytes(), segment.vertex_bytes[32..36]);
    assert_eq!([64, 128, 255, 192], segment.vertex_bytes[48..52]);
    assert_eq!(
        material_category_id(WorldLodMaterialCategory::Metal) << 1,
        segment.vertex_bytes[55],
        "the exact-atlas source stream preserves the DH material category beside its tint bit"
    );

    let mut wrong_atlas = plan.clone();
    wrong_atlas.segments[0].quads[0].atlas_identity =
        "minecraft:textures/atlas/items.png".to_string();
    let wrong_atlas = pack_world_lod_textured_column_asset(&wrong_atlas).unwrap();
    assert!(wrong_atlas.segments.is_empty());
    assert_eq!(vec![0], wrong_atlas.unavailable_source_segments);

    let mut mixed_atlas = plan.clone();
    let mut unsupported_quad = plan.segments[0].quads[0].clone();
    unsupported_quad.quad_index = 1;
    unsupported_quad.atlas_identity = "minecraft:textures/atlas/items.png".to_string();
    mixed_atlas.segments[0].source_quad_count = 2;
    mixed_atlas.segments[0].quads = vec![plan.segments[0].quads[0].clone(), unsupported_quad];
    let mixed_atlas = pack_world_lod_textured_column_asset(&mixed_atlas).unwrap();
    assert_eq!(1, mixed_atlas.segments.len());
    assert_eq!(vec![0], mixed_atlas.unavailable_source_segments);
    assert_eq!(
        6 * std::mem::size_of::<u32>(),
        mixed_atlas.segments[0]
            .unresolved_index_bytes
            .as_ref()
            .map(Vec::len)
            .expect("mixed atlas keeps an explicit coarse range")
    );

    let partial = WorldLodTexturedColumnPlan {
        column_key: plan.column_key,
        column_generation: plan.column_generation,
        segments: vec![WorldLodTexturedSegmentPlan {
            layer: WORLD_LOD_LAYER_OPAQUE,
            source_quad_count: 2,
            quads: vec![plan.segments[0].quads[0].clone()],
            unavailable: vec![WorldLodTexturedQuadUnavailable {
                quad_index: 1,
                material_id: 1,
                face: 1,
                reason: WorldLodTexturedQuadUnavailableReason::MaterialUnavailable,
            }],
        }],
    };
    let partial = pack_world_lod_textured_column_asset(&partial).unwrap();
    assert_eq!(
        1,
        partial.segments.len(),
        "known quads retain their exact atlas payload"
    );
    assert_eq!(
        vec![0],
        partial.unavailable_source_segments,
        "the source segment retains a complementary unresolved range"
    );
    assert_eq!(
        Some(6 * std::mem::size_of::<u32>()),
        partial.segments[0]
            .unresolved_index_bytes
            .as_ref()
            .map(Vec::len),
        "only the unavailable quad remains in the coarse index stream"
    );
    assert_eq!(
        Some(4u32.to_le_bytes()),
        partial.segments[0]
            .unresolved_index_bytes
            .as_ref()
            .map(|bytes| bytes[0..4].try_into().unwrap()),
        "the complementary range starts at the unresolved source quad"
    );

    let wrong_generation = WorldLodColumnMaterialProvenance {
        column_generation: asset.column_generation + 1,
        ..provenance.clone()
    };
    assert!(plan_world_lod_textured_column(&asset, &wrong_generation)
        .unwrap_err()
        .to_string()
        .contains("generation"));

    let wrong_segment = WorldLodColumnMaterialProvenance {
        segments: vec![WorldLodSegmentMaterialProvenance {
            segment_index: 1,
            ..provenance.segments[0].clone()
        }],
        ..provenance
    };
    assert!(plan_world_lod_textured_column(&asset, &wrong_segment)
        .unwrap_err()
        .to_string()
        .contains("layer/order"));
}

#[test]
fn exact_atlas_plan_preserves_merged_dh_tile_repetition_without_atlas_bleed() {
    let asset = asset();
    let segment = &asset.segments[0];
    let material = WorldLodFaceMaterial {
        material_id: 1,
        face: 1,
        face_layer: 0,
        tinted: false,
        tint_rgb: [1.0, 1.0, 1.0],
        atlas_identity: "minecraft:textures/atlas/blocks.png".to_string(),
        sprite_identity: "minecraft:block/grass_block_top".to_string(),
        atlas_uv: [0.25, 0.5, 0.375, 0.625],
        uv_corner_order: 0x78,
        variant_position: 0,
    };

    let plan = plan_world_lod_textured_segment(segment, &[1], &[material.clone()]).unwrap();
    assert!(plan.unavailable.is_empty());
    assert_eq!(1, plan.quads.len());
    assert_eq!(
        [[3.0, 0.0], [0.0, 0.0], [0.0, 0.0], [3.0, 0.0]],
        plan.quads[0].vertices.map(|vertex| vertex.tile_uv),
        "the material shader receives unwrapped repeat coordinates rather than one stretched atlas UV range"
    );
    assert_eq!(
        [3.0, 0.0],
        world_lod_textured_quad_tile_span(&plan.quads[0])
    );
    assert!(plan.quads[0]
        .vertices
        .iter()
        .all(|vertex| vertex.atlas_rect == material.atlas_uv));
}

#[test]
fn lightmap_binding_key_distinguishes_rebuilt_residencies() {
    let first = VanillaLightmapBinding {
        world_generation: 17,
        lightmap_generation: 9,
        texture_view: Handle::new(HandleKind::TextureView, 4, 1).unwrap(),
        sampler: Handle::new(HandleKind::Sampler, 5, 1).unwrap(),
    };
    let replacement = VanillaLightmapBinding {
        texture_view: Handle::new(HandleKind::TextureView, 6, 1).unwrap(),
        sampler: Handle::new(HandleKind::Sampler, 7, 1).unwrap(),
        ..first
    };

    assert_ne!(
        WorldLodLightmapResourceKey::from(first),
        WorldLodLightmapResourceKey::from(replacement)
    );
}

#[test]
fn rejects_unknown_semantic_face_normal() {
    let mut invalid = asset();
    invalid.segments[0].vertices[0].normal_index = 6;
    let error = expand_world_lod_column_asset(&invalid).unwrap_err();
    assert!(error.to_string().contains("face normal"));
}

#[test]
fn packs_owned_gpu_bytes_without_reusing_dh_vertex_layout() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let segment = &packed.segments[0];
    assert_eq!(
        WORLD_LOD_GPU_VERTEX_LAYOUT_V2,
        segment.vertex_layout_version
    );
    assert_eq!(IndexType::U16, segment.index_type);
    assert_eq!(4 * WORLD_LOD_GPU_VERTEX_BYTES, segment.vertex_bytes.len());
    assert_eq!(6 * std::mem::size_of::<u16>(), segment.index_bytes.len());
    let vertex = decode_world_lod_gpu_vertex(&segment.vertex_bytes[..16]).unwrap();
    assert_eq!([1.0, 2.0, 3.0], vertex.local_position);
    assert_eq!([0.01, 0.0, 0.0], vertex.micro_offset);
    assert_eq!(
        [64, 128, 255, 192],
        vertex.color_rgba.map(|v| (v * 255.0).round() as u8)
    );
    assert_eq!(2, vertex.sky_light);
    assert_eq!(3, vertex.block_light);
    assert_eq!(WorldLodMaterialCategory::Metal, vertex.material);
    assert_eq!(WorldLodFaceNormal::Up, vertex.normal);
    assert_eq!(
        [0u16, 1, 2, 2, 3, 0]
            .into_iter()
            .flat_map(u16::to_le_bytes)
            .collect::<Vec<_>>(),
        segment.index_bytes
    );
}

#[test]
fn direct_compact_packing_matches_expanded_bytes_at_both_index_widths() {
    for repeated_quads in [1, 16_385] {
        let mut source = asset();
        source.segments[0].vertices = source.segments[0]
            .vertices
            .repeat(repeated_quads);
        let expanded = expand_world_lod_column_asset(&source).unwrap();
        let traditional = pack_world_lod_gpu_column_asset(&expanded).unwrap();
        let direct = pack_world_lod_gpu_column_asset_from_compact(&source).unwrap();
        assert_eq!(traditional, direct);
        assert_eq!(
            if repeated_quads == 1 {
                IndexType::U16
            } else {
                IndexType::U32
            },
            direct.segments[0].index_type,
        );
    }
}

#[test]
fn direct_compact_packing_preserves_every_micro_pattern_and_color_byte() {
    let mut source = asset();
    let quad = source.segments[0].vertices.clone();
    source.segments[0].vertices.clear();
    for sample in 0..=u8::MAX {
        for mut vertex in quad.iter().copied() {
            vertex.local_position = [u16::from(sample), 2, 3];
            vertex.packed_light_and_micro_offset =
                (u16::from(sample) << 8) | 0x75;
            vertex.color_rgba = [sample, 255 - sample, sample.wrapping_mul(37), sample];
            vertex.material_id = sample % 16;
            vertex.normal_index = sample % 6;
            source.segments[0].vertices.push(vertex);
        }
    }
    let expanded = expand_world_lod_column_asset(&source).unwrap();
    assert_eq!(
        pack_world_lod_gpu_column_asset(&expanded).unwrap(),
        pack_world_lod_gpu_column_asset_from_compact(&source).unwrap(),
    );
}

#[test]
fn rejects_out_of_range_expanded_index_before_gpu_asset_creation() {
    let mut expanded = expand_world_lod_column_asset(&asset()).unwrap();
    expanded.segments[0].indices[0] = 4;
    let error = pack_world_lod_gpu_column_asset(&expanded).unwrap_err();
    assert!(error.to_string().contains("exceeds 4 vertices"));
}

#[test]
fn resolves_pending_uploads_as_generation_checked_draw_ranges() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 9,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut ops)
        .unwrap();
    let draws = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap();
    assert_eq!(1, draws.len());
    assert_eq!(7, draws[0].column_key);
    assert_eq!(3, draws[0].column_generation);
    assert_eq!([-128, 64, 256], draws[0].origin);
    assert_eq!(WORLD_LOD_LAYER_OPAQUE, draws[0].layer);
    assert_eq!(IndexType::U16, draws[0].index_type);
    assert_eq!(6, draws[0].index_count);
    assert!(!draws[0].vertex_buffer.is_null());
    assert!(!draws[0].index_buffer.is_null());

    // The ordinary frame path resolves through reusable caller storage.
    // A failed generation change must not poison the last complete cache.
    let mut cached = Vec::new();
    residency
        .resolve_visible_draws_cached_into(&assets, &[instance], &mut cached)
        .unwrap();
    let cached_first = cached.clone();
    residency
        .resolve_visible_draws_cached_into(&assets, &[instance], &mut cached)
        .unwrap();
    assert_eq!(cached_first, cached);

    let stale = WorldLodColumnInstanceRequest {
        column_generation: 4,
        ..instance
    };
    assert!(residency
        .resolve_visible_draws_cached_into(&assets, &[stale], &mut cached)
        .is_err());
    residency
        .resolve_visible_draws_cached_into(&assets, &[instance], &mut cached)
        .unwrap();
    assert_eq!(cached_first, cached);
    residency.discard_submission(&mut gal);
    assert!(residency
        .resolve_visible_draws_cached_into(&assets, &[instance], &mut cached)
        .is_err());
}

#[test]
fn off_screen_columns_are_prefetched_for_payload_release() {
    let packed = pack_world_lod_gpu_column_asset(&expand_world_lod_column_asset(&asset()).unwrap()).unwrap();
    let mut off_screen = packed.clone();
    off_screen.column_key = 8;
    let assets = BTreeMap::from([(packed.column_key, packed), (8, off_screen)]);
    let visible = [WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    }];
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &visible, &mut ops)
        .unwrap();
    residency.confirm_submission(&mut gal).unwrap();
    assert_eq!(Some(3), residency.active_generation(7));
    assert_eq!(Some(3), residency.active_generation(8));
    // Only the visible column is drawn.
    assert_eq!(1, residency.resolve_visible_draws(&assets, &visible).unwrap().len());
}

#[test]
fn column_index_stream_preserves_each_segments_index_range() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let mut packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let mut second = packed.segments[0].clone();
    let second_indices = second
        .index_bytes
        .chunks_exact(2)
        .flat_map(|bytes| u32::from(u16::from_le_bytes([bytes[0], bytes[1]])).to_le_bytes())
        .collect::<Vec<_>>();
    second.index_type = IndexType::U32;
    second.index_bytes = second_indices.clone();
    second.layer = WORLD_LOD_LAYER_TRANSPARENT_SIDE;
    packed.segments[0].index_count = 3;
    packed.segments[0]
        .index_bytes
        .truncate(3 * std::mem::size_of::<u16>());
    let first_indices = packed.segments[0].index_bytes.clone();
    packed.segments.push(second);
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instances = [
        WorldLodColumnInstanceRequest {
            column_key: 7,
            column_generation: 3,
            layer: WORLD_LOD_LAYER_OPAQUE,
            segment_index: 0,
            order: 0,
        },
        WorldLodColumnInstanceRequest {
            column_key: 7,
            column_generation: 3,
            layer: WORLD_LOD_LAYER_TRANSPARENT_SIDE,
            segment_index: 1,
            order: 1,
        },
    ];
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &instances, &mut ops)
        .unwrap();
    let draws = residency
        .resolve_visible_draws(&assets, &instances)
        .unwrap();
    assert_eq!(draws[0].vertex_buffer, draws[1].vertex_buffer);
    assert_eq!([0, 4], [draws[0].vertex_base, draws[1].vertex_base]);
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [1.0; 16],
        micro_offset: MICRO_OFFSET_SCALE,
        ..WorldLodRenderFrame::default()
    };
    assert_eq!(
        4.0,
        WorldLodDrawUniform::from_semantics(&frame, draws[1])
            .unwrap()
            .model_offset_and_reserved[3]
    );
    assert_eq!(draws[0].index_buffer, draws[1].index_buffer);
    assert_eq!([0, 8], [draws[0].index_offset, draws[1].index_offset]);
    assert_eq!(
        [IndexType::U16, IndexType::U32],
        [draws[0].index_type, draws[1].index_type]
    );
    // One staging write per transaction: the column's vertices, then its
    // index range (segments four-byte aligned), copied into the pages.
    let writes = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::HostWriteBuffer { data, .. } => Some(data.as_slice()),
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(1, writes.len());
    let index_copy = ops
        .iter()
        .find_map(|op| match op {
            CommandOp::CopyBufferRegion { src_offset, dst, size, .. } if *dst == draws[0].index_buffer => {
                Some((*src_offset as usize, *size as usize))
            }
            _ => None,
        })
        .expect("index range copy");
    let staged_indices = &writes[0][index_copy.0..index_copy.0 + index_copy.1];
    assert_eq!(&first_indices[..], &staged_indices[..first_indices.len()]);
    assert_eq!(&[0, 0], &staged_indices[first_indices.len()..8]);
    assert_eq!(&second_indices[..], &staged_indices[8..]);
    residency.discard_submission(&mut gal);

    // A column whose vertices exceed the device's buffer size is rejected.
    let mut small_limit = presentation_capabilities(vulkan_capabilities());
    small_limit.limits.max_buffer_size = 4 * WORLD_LOD_GPU_VERTEX_BYTES as u64;
    let mut small_gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(small_limit);
    let mut small = WorldLodGpuResidency::default();
    assert!(small.stage_visible_uploads(&mut small_gal, &assets, &instances, &mut Vec::new()).is_err());
    assert!(small.pending.is_none());
}

#[test]
fn immutable_lod_upload_copies_to_device_local_and_retires_staging_after_submission() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut ops)
        .unwrap();
    let staged_column = &residency.pending.as_ref().unwrap()[&7];
    let staged = staged_column.segments[0];
    let staged_index_buffer = staged_column.index_buffer;
    let staging = ops
        .iter()
        .find_map(|op| match op {
            CommandOp::HostWriteBuffer { buffer, .. } => Some(*buffer),
            _ => None,
        })
        .expect("staging write");
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::CopyBufferRegion { src, dst, dst_offset, size, .. }
            if *src == staging && *dst == staged.vertex_buffer && *dst_offset == staged_column.vertex_range.offset
                && *size == 4 * WORLD_LOD_GPU_VERTEX_BYTES as u64
    )));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::CopyBufferRegion { src, dst, size, .. }
            if *src == staging && *dst == staged_index_buffer && *size == 6 * std::mem::size_of::<u16>() as u64
    )));
    gal.submit(SubmissionBatch {
        label: "world-lod.device-local-upload".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod.device-local-upload.commands".to_string(),
            operations: ops,
        })],
    })
    .unwrap();
    residency.confirm_submission(&mut gal).unwrap();
    let active_column = &residency.active[&7];
    let active = active_column.segments[0];
    assert_eq!(staged.vertex_buffer, active.vertex_buffer);
    assert_eq!(staged_index_buffer, active_column.index_buffer);
    assert!(residency.pending_staging.is_none(), "staging retires with the accepted submission");
    assert!(residency.initialized_pages.contains(&active.vertex_buffer));
    assert_eq!(
        active.vertex_buffer,
        residency
            .resolve_visible_draws(&assets, &[instance])
            .unwrap()[0]
            .vertex_buffer
    );
    residency.destroy(&mut gal);
}

#[test]
fn columns_share_geometry_pages_and_replaced_ranges_return_after_completion() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let mut second = packed.clone();
    second.column_key = 8;
    let assets = BTreeMap::from([(7, packed.clone()), (8, second)]);
    let instance = |column_key| WorldLodColumnInstanceRequest {
        column_key,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
        vulkan_capabilities(),
    ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency.stage_visible_uploads(&mut gal, &assets, &[instance(7), instance(8)], &mut ops).unwrap();
    let draws = residency.resolve_visible_draws(&assets, &[instance(7), instance(8)]).unwrap();
    assert_eq!(draws[0].vertex_buffer, draws[1].vertex_buffer, "columns share one vertex page");
    assert_eq!(draws[0].index_buffer, draws[1].index_buffer, "and one index page");
    assert_ne!(draws[0].vertex_base, draws[1].vertex_base);
    assert_eq!(1, ops.iter().filter(|op| matches!(op, CommandOp::HostWriteBuffer { .. })).count());
    gal.submit(SubmissionBatch {
        label: "world-lod.pages".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod.pages.commands".to_string(),
            operations: ops,
        })],
    })
    .unwrap();
    residency.confirm_submission(&mut gal).unwrap();
    let old_range = residency.active[&7].vertex_range;

    // A new generation of column 7 takes a new range; the old one returns to
    // the page only after the replacing submission completes.
    let mut replaced = packed;
    replaced.column_generation = 4;
    let assets = BTreeMap::from([(7, replaced), (8, assets[&8].clone())]);
    let mut ops = Vec::new();
    let next = WorldLodColumnInstanceRequest { column_generation: 4, ..instance(7) };
    residency.stage_visible_uploads(&mut gal, &assets, &[next, instance(8)], &mut ops).unwrap();
    assert_ne!(old_range.offset, residency.pending.as_ref().unwrap()[&7].vertex_range.offset);
    gal.submit(SubmissionBatch {
        label: "world-lod.pages.replace".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod.pages.replace.commands".to_string(),
            operations: ops,
        })],
    })
    .unwrap();
    residency.confirm_submission(&mut gal).unwrap();
    assert_eq!(2, residency.pages.pending_releases.len(), "old vertex and index ranges wait for completion");
    // Released after the next submission (conservative: it may still be
    // recorded against the replaced range when the frame is pipelined).
    gal.submit(SubmissionBatch {
        label: "world-lod.pages.after".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod.pages.after.commands".to_string(),
            operations: Vec::new(),
        })],
    })
    .unwrap();
    gal.retire_through(gal.latest_submission_id()).unwrap();
    residency.stage_visible_uploads(&mut gal, &assets, &[next, instance(8)], &mut Vec::new()).unwrap();
    assert!(residency.pages.pending_releases.is_empty());
    residency.destroy(&mut gal);
}

#[test]
fn draw_uniform_preserves_resolved_frame_and_column_origin_without_legacy_state() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        flags: 0b1011,
        world_y_offset: -64,
        combined_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 0.0, 0.0, 3.0, 0.0, 4.0, 5.0, 6.0, 1.0,
        ],
        clip_distance: 24.0,
        micro_offset: 0.01,
        noise_intensity: 0.25,
        earth_radius: 6_371_000.0,
        noise_steps: 4,
        noise_dropoff: 96,
        ..WorldLodRenderFrame::default()
    };
    let uniform = WorldLodDrawUniform::from_semantics(&frame, draw).unwrap();
    assert_eq!(
        [-128.0, 64.0, 256.0, -64.0],
        uniform.column_origin_and_world_y
    );
    assert_eq!(
        [-128.0, 64.0, 256.0, 0.0],
        uniform.model_offset_and_reserved
    );
    assert_eq!(
        [24.0, 0.01, 0.25, 6_371_000.0],
        uniform.clip_micro_noise_earth
    );
    assert_eq!([0b1011, 4, 96, 0], uniform.flags_and_noise);
    assert_eq!(
        [0b1011, 4, 96, 519],
        uniform
            .with_private_audit_flip_y(true)
            .with_private_audit_no_depth_remap(true)
            .with_private_audit_column_ids(true)
            .with_private_audit_dither_y(true)
            .flags_and_noise
    );
    let uniform = uniform.with_fog([0.1, 0.2, 0.4, 0.8], [12.0, 96.0, 24.0, 128.0]);
    assert_eq!([0.1, 0.2, 0.4, 0.8], uniform.fog_color_and_alpha);
    assert_eq!([12.0, 96.0, 24.0, 128.0], uniform.fog_ranges);
    let packed = uniform.pack_std140();
    assert_eq!(240, packed.len());
    assert_eq!(1.0f32.to_ne_bytes(), packed[0..4]);
    assert_eq!((-128.0f32).to_ne_bytes(), packed[64..68]);
    assert_eq!(0b1011u32.to_ne_bytes(), packed[112..116]);
    assert_eq!(0.1f32.to_ne_bytes(), packed[128..132]);
    assert_eq!(128.0f32.to_ne_bytes(), packed[156..160]);
    let source_packed = uniform.pack_source_std140();
    assert_eq!(128, source_packed.len());
    assert_eq!(1.0f32.to_ne_bytes(), source_packed[0..4]);
    assert_eq!(0b1011u32.to_ne_bytes(), source_packed[112..116]);
    residency.discard_submission(&mut gal);
}

#[test]
fn column_uniform_keeps_world_origin_separate_from_dh_camera_relative_offset() {
    let frame = WorldLodRenderFrame {
        enabled: true,
        flags: 0,
        combined_matrix: [1.0; 16],
        clip_distance: 1.0,
        micro_offset: 0.01,
        earth_radius: 1.0,
        ..WorldLodRenderFrame::default()
    };
    let draw = WorldLodGpuDraw {
        column_key: 1,
        column_generation: 1,
        origin: [160, 72, -544],
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
        vertex_buffer: Handle::from_raw(1),
        vertex_base: 0,
        index_buffer: Handle::from_raw(2),
        index_offset: 0,
        index_type: IndexType::U16,
        index_count: 3,
    };
    let uniform =
        WorldLodDrawUniform::from_semantics_with_camera(&frame, draw, [150.5, 64.0, -530.25])
            .unwrap();
    assert_eq!(
        [160.0, 72.0, -544.0, 0.0],
        uniform.column_origin_and_world_y
    );
    assert_eq!([9.5, 8.0, -13.75, 0.0], uniform.model_offset_and_reserved);
}

#[test]
fn source_pass_stages_owned_column_data_without_selecting_pack_resources() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut upload_ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut upload_ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        model_view_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        projection_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        projection_inverse_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        clip_distance: 24.0,
        micro_offset: MICRO_OFFSET_SCALE,
        noise_intensity: 0.25,
        earth_radius: 6_371_000.0,
        noise_steps: 4,
        noise_dropoff: 96,
        ..WorldLodRenderFrame::default()
    };
    let program = lowered_source_program();
    let mut source_resources = WorldLodSourcePassResources::default();
    let mut ops = Vec::new();
    // One source frame: the scalar block is written by the first draw and
    // the frame's column blocks by one flush.
    source_resources.begin_source_frame();
    let prepared = source_resources
        .stage_draw(
            &mut gal,
            &program,
            TextureFormat::Rgba8Unorm,
            draw,
            WorldLodDrawUniform::from_semantics(&frame, draw).unwrap(),
            &distant_source_uniforms(),
            &mut ops,
        )
        .unwrap();
    source_resources.flush_source_frame(&mut ops);

    assert_eq!(Some(HandleKind::GraphicsPipeline), prepared.pipeline.kind());
    assert_eq!(
        Some(HandleKind::PipelineLayout),
        prepared.pipeline_layout.kind()
    );
    assert_eq!(
        Some(HandleKind::ResourceSet),
        prepared.source_data_set.kind()
    );
    assert_eq!(
        Some(HandleKind::ResourceLayout),
        prepared.pack_resources_layout.kind()
    );
    assert_eq!([0, 0], prepared.source_data_dynamic_offsets);
    assert_eq!(2, prepared.source_data_dynamic_offset_count);
    assert_eq!(draw.index_buffer, prepared.index_buffer);
    assert_eq!(draw.index_type, prepared.index_type);
    assert_eq!(draw.index_count, prepared.index_count);
    assert_eq!(6, ops.len());
    assert_eq!(
        2,
        ops.iter()
            .filter(|op| matches!(op, CommandOp::HostWriteBuffer { .. }))
            .count()
    );
    assert!(ops
        .iter()
        .all(|op| !format!("{op:?}").contains("material-id")));

    let mut cached_ops = Vec::new();
    source_resources.begin_source_frame();
    let cached = source_resources
        .stage_draw(
            &mut gal,
            &program,
            TextureFormat::Rgba8Unorm,
            draw,
            WorldLodDrawUniform::from_semantics(&frame, draw).unwrap(),
            &distant_source_uniforms(),
            &mut cached_ops,
        )
        .unwrap();
    source_resources.flush_source_frame(&mut cached_ops);
    assert_eq!(prepared.pipeline, cached.pipeline);
    assert_eq!(prepared.source_data_set, cached.source_data_set);
    assert_eq!(
        prepared.source_data_dynamic_offsets,
        cached.source_data_dynamic_offsets
    );
    assert_eq!(
        prepared.source_data_dynamic_offset_count,
        cached.source_data_dynamic_offset_count
    );
    assert_eq!(6, cached_ops.len());

    source_resources.destroy(&mut gal);
    residency.discard_submission(&mut gal);
}

#[test]
fn source_pass_rejects_incomplete_semantic_pack_resources_before_set_one_creation() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let program = lowered_source_program();
    let empty = TerrainSourceOwnedResourceSet::new(
        TerrainSourceResourceAvailabilitySet::new(program.shader_pack_generation, 17, [])
            .unwrap(),
        [],
    )
    .unwrap();
    let mut source_resources = WorldLodSourcePassResources::default();

    let error = source_resources
        .stage_pack_resources(&mut gal, &program, TextureFormat::Rgba8Unorm, &empty)
        .unwrap_err();
    assert!(error.to_string().contains("unavailable"));
    assert!(source_resources.pack_resources.is_empty());
    assert!(source_resources.pipelines.is_empty());
}

#[test]
fn opaque_layer_admission_preserves_uniforms_and_requires_explicit_transparent_path() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [1.0; 16],
        micro_offset: MICRO_OFFSET_SCALE,
        ..WorldLodRenderFrame::default()
    };
    let admitted = admit_world_lod_draw(&frame, draw).unwrap();
    assert_eq!(WorldLodPassClass::Opaque, admitted.pass);
    assert_eq!(draw.column_key, admitted.draw.column_key);
    assert_eq!(
        draw.origin[0] as f32,
        admitted.uniforms.column_origin_and_world_y[0]
    );
    assert_eq!(WorldLodMaterialContract::OPAQUE, admitted.material_contract);
    assert!(admitted.material_contract.requires_vanilla_lightmap);
    assert!(admitted.material_contract.uses_vertex_color);
    let water_error = admit_world_lod_transparent_draw(
        &frame,
        WorldLodGpuDraw {
            layer: WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
            ..draw
        },
    )
    .unwrap_err();
    assert!(water_error
        .to_string()
        .contains("explicit water-surface admission path"));

    let transparent = WorldLodGpuDraw {
        layer: WORLD_LOD_LAYER_TRANSPARENT_SIDE,
        ..draw
    };
    let error = admit_world_lod_draw(&frame, transparent).unwrap_err();
    assert!(error
        .to_string()
        .contains("explicit transparent admission path"));

    let unknown = WorldLodGpuDraw { layer: 99, ..draw };
    let error = admit_world_lod_draw(&frame, unknown).unwrap_err();
    assert!(error.to_string().contains("unknown Distant Horizons layer"));
    residency.discard_submission(&mut gal);
}

#[test]
fn frame_plan_preserves_transparent_and_water_draws_in_visible_list_order() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [1.0; 16],
        micro_offset: MICRO_OFFSET_SCALE,
        ..WorldLodRenderFrame::default()
    };
    let transparent_later = WorldLodGpuDraw {
        layer: WORLD_LOD_LAYER_TRANSPARENT_UP,
        order: 9,
        ..draw
    };
    let transparent_earlier = WorldLodGpuDraw {
        layer: WORLD_LOD_LAYER_TRANSPARENT_SIDE,
        order: 3,
        ..draw
    };
    let water = WorldLodGpuDraw {
        layer: WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
        order: 11,
        ..draw
    };
    let plan = plan_world_lod_frame(
        &frame,
        &[draw, transparent_later, water, transparent_earlier],
    )
    .unwrap();
    assert_eq!(1, plan.opaque_draws.len());
    assert_eq!(2, plan.transparent_draws.len());
    assert_eq!(1, plan.water_draws.len());
    assert_eq!(
        WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
        plan.water_draws[0].draw.layer
    );
    assert_eq!(11, plan.water_draws[0].draw.order);
    assert_eq!(WorldLodPassClass::WaterSurface, plan.water_draws[0].pass);
    assert_eq!(
        WorldLodMaterialContract::WATER_SURFACE,
        plan.water_draws[0].material_contract
    );
    assert_eq!(3, plan.transparent_draws[0].draw.order);
    assert_eq!(
        WORLD_LOD_LAYER_TRANSPARENT_SIDE,
        plan.transparent_draws[0].draw.layer
    );
    assert_eq!(
        WorldLodPassClass::TransparentSide,
        plan.transparent_draws[0].pass
    );
    assert_eq!(
        WorldLodMaterialContract::TRANSPARENT_SIDE,
        plan.transparent_draws[0].material_contract
    );
    assert_eq!(
        WorldLodPassClass::TransparentUp,
        plan.transparent_draws[1].pass
    );
    assert_eq!(
        WorldLodMaterialContract::TRANSPARENT_UP,
        plan.transparent_draws[1].material_contract
    );
    assert_eq!(9, plan.transparent_draws[1].draw.order);
    residency.discard_submission(&mut gal);
}

#[test]
fn distant_horizons_exact_atlas_forward_pass_policies_stay_layer_specific() {
    let cases = [
        (
            WorldLodExactAtlasPassKind::Opaque,
            WORLD_LOD_LAYER_OPAQUE,
            (
                BlendMode::Disabled,
                CullMode::Back,
                CompareOp::LessOrEqual,
                true,
            ),
            (BlendMode::Disabled, CompareOp::Less, true),
        ),
        (
            WorldLodExactAtlasPassKind::TransparentSide,
            WORLD_LOD_LAYER_TRANSPARENT_SIDE,
            (
                BlendMode::Alpha,
                CullMode::Back,
                CompareOp::LessOrEqual,
                false,
            ),
            (BlendMode::Alpha, CompareOp::Less, false),
        ),
        (
            WorldLodExactAtlasPassKind::TransparentUp,
            WORLD_LOD_LAYER_TRANSPARENT_UP,
            (
                BlendMode::Alpha,
                CullMode::Back,
                CompareOp::LessOrEqual,
                false,
            ),
            (BlendMode::Alpha, CompareOp::Less, true),
        ),
        (
            WorldLodExactAtlasPassKind::WaterSurface,
            WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
            (
                BlendMode::AlphaSource,
                CullMode::None,
                CompareOp::LessOrEqual,
                false,
            ),
            (BlendMode::AlphaSource, CompareOp::Always, true),
        ),
    ];
    for (pass, layer, shared, private) in cases {
        assert_eq!(layer, pass.expected_layer());
        assert_eq!(shared, pass.shared_raster_policy());
        assert_eq!(private, pass.private_raster_policy());
    }
    assert!(
        WorldLodExactAtlasPassResources::new_forward_transparent_side()
            .color_format
            .is_none()
    );
    assert!(WorldLodExactAtlasPassResources::new_forward_water_surface()
        .color_format
        .is_none());
}

#[test]
fn opaque_pass_resources_reuse_generation_keyed_bindings_and_skip_unchanged_frame_uniforms() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut upload_ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut upload_ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [1.0; 16],
        micro_offset: MICRO_OFFSET_SCALE,
        ..WorldLodRenderFrame::default()
    };
    let admitted = admit_world_lod_draw(&frame, draw).unwrap();
    let mut pass_resources = WorldLodOpaquePassResources::default();
    let (lightmap, lightmap_handles) = lightmap_binding(&mut gal);

    let creates_before = gal.metrics().resource_creates;
    let mut first_ops = Vec::new();
    let first = pass_resources
        .stage_draw(&mut gal, admitted, lightmap, &mut first_ops)
        .unwrap();
    let creates_after_first = gal.metrics().resource_creates;
    assert!(creates_after_first > creates_before);
    assert_eq!(3, first_ops.len());
    assert!(matches!(first_ops[0], CommandOp::Barrier(_)));
    assert!(matches!(
        &first_ops[1],
        CommandOp::HostWriteBuffer { data, .. } if data.len() == 240
    ));
    assert!(matches!(first_ops[2], CommandOp::Barrier(_)));

    let mut second_ops = Vec::new();
    let second = pass_resources
        .stage_draw(&mut gal, admitted, lightmap, &mut second_ops)
        .unwrap();
    assert_eq!(creates_after_first, gal.metrics().resource_creates);
    assert_eq!(first.pipeline, second.pipeline);
    assert_eq!(first.pipeline_layout, second.pipeline_layout);
    assert_eq!(first.geometry_resource_set, second.geometry_resource_set);
    assert_eq!(first.lightmap_resource_set, second.lightmap_resource_set);
    assert_eq!(first.index_buffer, second.index_buffer);
    assert_eq!(IndexType::U16, first.index_type);
    assert_eq!(6, first.index_count);
    assert!(second_ops.is_empty());

    let changed = WorldLodOpaqueDraw {
        uniforms: admitted
            .uniforms
            .with_fog([0.2, 0.3, 0.4, 0.9], [8.0, 64.0, 16.0, 96.0]),
        ..admitted
    };
    let mut changed_ops = Vec::new();
    pass_resources
        .stage_draw(&mut gal, changed, lightmap, &mut changed_ops)
        .unwrap();
    assert_eq!(3, changed_ops.len());
    assert!(matches!(
        &changed_ops[1],
        CommandOp::HostWriteBuffer { data, .. } if data.len() == 240
    ));

    pass_resources.reconcile_assets(&mut gal, &BTreeMap::new());
    assert!(pass_resources.inner.draws.is_empty());
    assert_eq!(
        1,
        pass_resources.inner.lightmaps.len(),
        "lightmap ownership follows the shader/runtime generation, not one column asset"
    );
    pass_resources.clear_lightmap_bindings(&mut gal);
    assert!(pass_resources.inner.lightmaps.is_empty());
    for handle in lightmap_handles {
        gal.destroy(handle).unwrap();
    }
    pass_resources.destroy(&mut gal);
    residency.discard_submission(&mut gal);
}

#[test]
fn packed_forward_lod_uniforms_share_one_upload_with_aligned_draw_offsets() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut Vec::new())
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [1.0; 16],
        micro_offset: MICRO_OFFSET_SCALE,
        ..WorldLodRenderFrame::default()
    };
    let first = admit_world_lod_draw(&frame, draw).unwrap();
    let second = WorldLodOpaqueDraw {
        uniforms: first
            .uniforms
            .with_fog([0.2, 0.3, 0.4, 0.9], [8.0, 64.0, 16.0, 96.0]),
        ..first
    };
    let (lightmap, lightmap_handles) = lightmap_binding(&mut gal);
    let mut pass = WorldLodForwardOpaquePassResources::default();
    pass.set_color_format(&mut gal, TextureFormat::Rgba8Unorm).unwrap();
    pass.inner.use_packed_uniforms = true;
    pass.begin_frame();
    let mut ops = Vec::new();
    let first_prepared = pass
        .stage_draw(&mut gal, first, lightmap, &mut ops)
        .unwrap();
    let second_prepared = pass
        .stage_draw(&mut gal, second, lightmap, &mut ops)
        .unwrap();
    assert_eq!(
        first_prepared.geometry_resource_set,
        second_prepared.geometry_resource_set
    );
    assert_eq!(Some(0), first_prepared.uniform_dynamic_offset);
    assert_eq!(Some(256), second_prepared.uniform_dynamic_offset);
    assert!(ops.is_empty());
    pass.flush_packed_uniforms(&mut ops);
    assert_eq!(3, ops.len());
    let CommandOp::HostWriteBuffer { data, .. } = &ops[1] else {
        panic!("packed LOD uniform write missing");
    };
    assert_eq!(512, data.len());
    assert_eq!(&first.uniforms.pack_std140(), &data[..240]);
    assert_eq!(&second.uniforms.pack_std140(), &data[256..496]);
    pass.begin_frame();
    ops.clear();
    pass.stage_draw(&mut gal, first, lightmap, &mut ops)
        .unwrap();
    pass.stage_draw(&mut gal, second, lightmap, &mut ops)
        .unwrap();
    pass.flush_packed_uniforms(&mut ops);
    assert!(ops.is_empty(), "unchanged packed uniforms must not upload");
    pass.begin_frame();
    let changed = WorldLodOpaqueDraw {
        uniforms: first
            .uniforms
            .with_fog([0.3, 0.3, 0.4, 0.9], [8.0, 64.0, 16.0, 96.0]),
        ..first
    };
    pass.stage_draw(&mut gal, changed, lightmap, &mut ops)
        .unwrap();
    pass.stage_draw(&mut gal, second, lightmap, &mut ops)
        .unwrap();
    pass.flush_packed_uniforms(&mut ops);
    assert_eq!(3, ops.len());
    pass.destroy(&mut gal);
    for handle in lightmap_handles {
        gal.destroy(handle).unwrap();
    }
    residency.discard_submission(&mut gal);
}

#[test]
fn transparent_pass_uses_the_shared_semantic_stream_with_distinct_alpha_policy() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 4,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut upload_ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut upload_ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [1.0; 16],
        micro_offset: MICRO_OFFSET_SCALE,
        ..WorldLodRenderFrame::default()
    };
    let admitted = admit_world_lod_transparent_draw(
        &frame,
        WorldLodGpuDraw {
            layer: WORLD_LOD_LAYER_TRANSPARENT_SIDE,
            ..draw
        },
    )
    .unwrap();
    assert_eq!(WorldLodPassClass::TransparentSide, admitted.pass);
    assert_eq!(
        WorldLodMaterialContract::TRANSPARENT_SIDE,
        admitted.material_contract
    );
    let (lightmap, lightmap_handles) = lightmap_binding(&mut gal);
    let mut pass_resources = WorldLodTransparentPassResources::default();
    let mut ops = Vec::new();
    let prepared = pass_resources
        .stage_draw(&mut gal, admitted, lightmap, &mut ops)
        .unwrap();
    assert!(!prepared.pipeline.is_null());
    assert!(!prepared.geometry_resource_set.is_null());
    assert_eq!(3, ops.len());
    assert!(matches!(
        &ops[1],
        CommandOp::HostWriteBuffer { data, .. } if data.len() == 240
    ));
    assert_eq!(1, pass_resources.inner_side.draws.len());
    assert!(pass_resources.inner_up.draws.is_empty());
    assert_eq!(1, pass_resources.inner_side.lightmaps.len());
    assert!(pass_resources.inner_up.lightmaps.is_empty());
    pass_resources.destroy(&mut gal);
    for handle in lightmap_handles {
        gal.destroy(handle).unwrap();
    }
    residency.discard_submission(&mut gal);
}

#[test]
fn frozen_forward_transparent_cache_keeps_equal_bucket_ordinals_distinct() {
    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 4,
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let mut residency = WorldLodGpuResidency::default();
    let mut upload_ops = Vec::new();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut upload_ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let uniforms = WorldLodDrawUniform::from_semantics(
        &WorldLodRenderFrame {
            enabled: true,
            combined_matrix: [1.0; 16],
            micro_offset: MICRO_OFFSET_SCALE,
            ..WorldLodRenderFrame::default()
        },
        draw,
    )
    .unwrap();
    let (lightmap, lightmap_handles) = lightmap_binding(&mut gal);
    let mut pass_resources = WorldLodTransparentPassResources::default();
    pass_resources
        .set_color_format(&mut gal, TextureFormat::Bgra8Unorm)
        .unwrap();
    let mut ops = Vec::new();
    for layer in [
        WORLD_LOD_LAYER_TRANSPARENT_SIDE,
        WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
    ] {
        pass_resources
            .stage_frozen_opengl_draw(
                &mut gal,
                WorldLodGpuDraw { layer, ..draw },
                uniforms,
                lightmap,
                &mut ops,
            )
            .unwrap();
    }
    assert_eq!(2, pass_resources.inner_up.draws.len());
    // A shader toggle rebinds this owner to the HDR G-buffer format: the
    // format-bound pipeline and draw sets are rebuilt rather than failing.
    assert!(pass_resources.inner_up.pipeline.is_some());
    pass_resources
        .set_color_format(&mut gal, TextureFormat::Rgba16Float)
        .unwrap();
    assert!(pass_resources.inner_up.pipeline.is_none());
    assert!(pass_resources.inner_up.draws.is_empty());
    assert_eq!(
        Some(TextureFormat::Rgba16Float),
        pass_resources.inner_up.color_format
    );
    pass_resources.destroy(&mut gal);
    for handle in lightmap_handles {
        gal.destroy(handle).unwrap();
    }
    residency.discard_submission(&mut gal);
}

#[test]
fn source_target_cache_owns_a_generation_bound_distant_depth_target() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let first_identity = WorldLodSourceTargetIdentity {
        world_generation: 17,
        shader_pack_generation: 91,
        extent: Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
    };
    let mut cache = WorldLodSourceTargetCache::default();
    let creates_before = gal.metrics().resource_creates;
    let first = cache.stage(&mut gal, first_identity).unwrap();
    assert!(!first.distant_depth_texture.is_null());
    assert!(!first.distant_depth_view.is_null());
    assert!(!first.distant_depth_before_translucency_texture.is_null());
    assert!(!first.distant_depth_before_translucency_view.is_null());
    assert!(
        gal.texture_view_info(first.distant_depth_before_translucency_view)
            .unwrap()
            .usages
            .contains(&TextureUsage::TransferSrc),
        "the actual opaque snapshot must permit exact-frame depth readbacks"
    );
    assert_ne!(
        first.distant_depth_texture,
        first.distant_depth_before_translucency_texture
    );
    assert_eq!(
        10,
        gal.metrics().resource_creates - creates_before,
        "one source generation owns two depth textures/views/samplers, two combined samplers, and its explicit depth-clear target/pass"
    );
    cache.confirm_submission(&mut gal);
    assert_eq!(Some(first_identity), cache.active_identity());

    let reused = cache.stage(&mut gal, first_identity).unwrap();
    assert_eq!(first.distant_depth_texture, reused.distant_depth_texture);
    assert_eq!(first.distant_depth_view, reused.distant_depth_view);
    assert_eq!(
        first.distant_depth_before_translucency_texture,
        reused.distant_depth_before_translucency_texture
    );
    assert_eq!(
        10,
        gal.metrics().resource_creates - creates_before,
        "reusing the active source generation must not recreate any depth resources"
    );

    let resized_identity = WorldLodSourceTargetIdentity {
        extent: Extent3d {
            width: 640,
            height: 360,
            depth: 1,
        },
        ..first_identity
    };
    let replacement = cache.stage(&mut gal, resized_identity).unwrap();
    assert_ne!(
        first.distant_depth_texture,
        replacement.distant_depth_texture
    );
    assert_eq!(Some(first_identity), cache.active_identity());
    cache.discard_submission(&mut gal);
    assert_eq!(Some(first_identity), cache.active_identity());

    let replacement = cache.stage(&mut gal, resized_identity).unwrap();
    cache.confirm_submission(&mut gal);
    assert_eq!(Some(resized_identity), cache.active_identity());
    assert_ne!(first.distant_depth_view, replacement.distant_depth_view);
    cache.destroy(&mut gal);
    assert_eq!(None, cache.active_identity());
}

#[test]
fn source_target_snapshot_keeps_live_and_pre_translucency_depth_distinct() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let identity = WorldLodSourceTargetIdentity {
        world_generation: 17,
        shader_pack_generation: 91,
        extent: Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
    };
    let mut cache = WorldLodSourceTargetCache::default();
    let targets = cache.stage(&mut gal, identity).unwrap();
    let mut ops = Vec::new();
    targets.append_opaque_depth_snapshot(&mut ops);

    assert!(matches!(
        ops.as_slice(),
        [
            CommandOp::Barrier(ResourceBarrier {
                resource: live_before,
                before: TextureUsageState::DepthStencilAttachment,
                after: TextureUsageState::TransferSrc,
                ..
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: snapshot_before,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferDst,
                ..
            }),
            CommandOp::CopyTexture(TextureImageCopyRegion {
                row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                src_texture,
                dst_texture,
                extent: Extent3d { width: 320, height: 180, depth: 1 },
                ..
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: live_after,
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::ShaderRead,
                ..
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: snapshot_after,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ShaderRead,
                ..
            }),
        ] if *live_before == targets.distant_depth_texture
            && *snapshot_before == targets.distant_depth_before_translucency_texture
            && *src_texture == targets.distant_depth_texture
            && *dst_texture == targets.distant_depth_before_translucency_texture
            && *live_after == targets.distant_depth_texture
            && *snapshot_after == targets.distant_depth_before_translucency_texture
    ));
    cache.discard_submission(&mut gal);
}

#[test]
fn source_targets_expose_distinct_generation_bound_depth_resources() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let identity = WorldLodSourceTargetIdentity {
        world_generation: 17,
        shader_pack_generation: 91,
        extent: Extent3d {
            width: 320,
            height: 180,
            depth: 1,
        },
    };
    let mut cache = WorldLodSourceTargetCache::default();
    let targets = cache.stage(&mut gal, identity).unwrap();
    let resources = targets.semantic_resources().unwrap();
    assert_eq!(2, resources.len());
    assert_eq!(
        Some(targets.distant_depth_combined_sampler),
        resources.combined_sampler_for(TerrainSourceResourceRole::DistantHorizonsOpaqueDepth)
    );
    assert_eq!(
        Some(targets.distant_depth_before_translucency_combined_sampler),
        resources.combined_sampler_for(
            TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency
        )
    );
    assert_eq!(91, resources.availability().shader_pack_generation());
    assert_eq!(17, resources.availability().world_generation());
    let opaque_generation = resources
        .availability()
        .resource_for(TerrainSourceResourceRole::DistantHorizonsOpaqueDepth)
        .expect("opaque depth must be available")
        .resource_generation;
    let snapshot_generation = resources
        .availability()
        .resource_for(TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency)
        .expect("pre-translucency depth must be available")
        .resource_generation;
    assert_ne!(0, opaque_generation);
    assert_eq!(opaque_generation, snapshot_generation);
    cache.discard_submission(&mut gal);
}

#[test]
fn source_opaque_target_pairs_pack_primary_color_with_distinct_dh_depth() {
    let source = complete_bundled_pack_source_for_test();
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let extent = Extent3d {
        width: 96,
        height: 64,
        depth: 1,
    };
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let colors = runtime
        .stage_source_color_targets(&mut gal, 17, extent)
        .unwrap()
        .expect("bundled source declares a semantic primary color target");
    let primary = colors.target("primary").unwrap();
    let identity = WorldLodSourceTargetIdentity {
        world_generation: 17,
        shader_pack_generation: source.generation(),
        extent,
    };
    let mut depth_cache = WorldLodSourceTargetCache::default();
    let depth = depth_cache.stage(&mut gal, identity).unwrap();
    let mut pass_resources = WorldLodSourcePassResources::default();
    let prepared = pass_resources
        .stage_target(&mut gal, &colors, depth)
        .unwrap();
    assert_eq!(Some(HandleKind::RenderTarget), prepared.target.kind());
    assert_eq!(Some(HandleKind::RenderPass), prepared.pass.kind());
    assert_eq!(primary.current_texture, prepared.primary_color_texture);
    assert_eq!(primary.current_attachment_view, prepared.primary_color_view);
    assert_eq!(depth.distant_depth_texture, prepared.distant_depth_texture);
    assert_eq!(depth.distant_depth_view, prepared.distant_depth_view);
    let cached = pass_resources
        .stage_target(&mut gal, &colors, depth)
        .unwrap();
    assert_eq!(prepared.target, cached.target);
    assert_eq!(prepared.pass, cached.pass);

    // The selected DH source contract has one primary color output. Both
    // reduced and atlas-resolved segments must share this target schema;
    // treating atlas ranges as ordinary terrain G-buffer writers would
    // make the source plan semantically inconsistent.
    assert_eq!(1, prepared.color_attachments.len());
    assert_eq!(
        TerrainPassOutput::LitTerrainColor,
        prepared.color_attachments[0].output
    );

    let operations = vec![
        CommandOp::Barrier(texture_barrier(
            prepared.primary_color_texture,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )),
        CommandOp::Barrier(texture_barrier(
            prepared.distant_depth_texture,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        )),
        CommandOp::BeginPass {
            pass: prepared.pass,
            target: prepared.target,
            colors: vec![PassAttachment {
                view: prepared.primary_color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(crate::render::vulkanic::commands::ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: prepared.distant_depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        },
        CommandOp::EndPass,
    ];
    gal.submit(SubmissionBatch {
        label: "world-lod-source.opaque-target".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod-source.opaque-target.commands".to_string(),
            operations,
        })],
    })
    .expect("the explicit DH source target/pass must validate without backend state");

    // The cache may replace a depth stream after a resource-generation
    // boundary without changing world, pack, or extent. The source target
    // must be rebuilt for the replacement; otherwise its attachment and
    // the `dhDepthTex` semantic resource diverge.
    let replacement_depth = WorldLodSourceTargets::create(&mut gal, identity, 2).unwrap();
    let replacement = pass_resources
        .stage_target(&mut gal, &colors, replacement_depth)
        .unwrap();
    assert_ne!(prepared.target, replacement.target);
    assert_ne!(prepared.pass, replacement.pass);
    assert_eq!(
        replacement_depth.distant_depth_texture,
        replacement.distant_depth_texture
    );
    assert_eq!(
        replacement_depth.distant_depth_view,
        replacement.distant_depth_view
    );

    pass_resources.destroy(&mut gal);
    replacement_depth.destroy(&mut gal);
    depth_cache.discard_submission(&mut gal);
    runtime.discard_source_color_targets_submission(&mut gal);
    runtime.destroy(&mut gal).unwrap();
}

#[test]
fn source_opaque_and_depth_transaction_executes_after_pack_semantic_bootstrap() {
    let source = complete_bundled_pack_source_for_test();
    let program = lowered_source_program();
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let extent = Extent3d {
        width: 96,
        height: 64,
        depth: 1,
    };
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );
    let colors = runtime
        .stage_source_color_targets(&mut gal, 17, extent)
        .unwrap()
        .expect("bundled source retains named color targets");
    let mut depth_cache = WorldLodSourceTargetCache::default();
    let depth = depth_cache
        .stage(
            &mut gal,
            WorldLodSourceTargetIdentity {
                world_generation: 17,
                shader_pack_generation: source.generation(),
                extent,
            },
        )
        .unwrap();
    let consumer_programs = runtime
        .prepared_lowered_distant_horizons_depth_consumers()
        .expect("the bundled source retains complete DH depth consumers");
    let mut binding_plans = vec![&program.opaque_resource_bindings];
    binding_plans.extend(
        consumer_programs
            .iter()
            .map(|consumer| &consumer.opaque_resource_bindings),
    );
    let (resources, resource_handles) = owned_resources_for_programs(
        &mut gal,
        &binding_plans,
        program.shader_pack_generation,
        17,
    );
    let depth_resources = depth.semantic_resources().unwrap();
    let consumer_plans = runtime
        .stage_distant_horizons_depth_consumer_execution_plans(
            &mut gal,
            &colors,
            &[resources.clone(), depth_resources],
            extent,
        )
        .expect("complete semantic resources must stage every declared DH depth consumer");
    assert!(
        !consumer_plans.is_empty(),
        "the bundled source must retain at least one explicit DH depth consumer"
    );

    let expanded = expand_world_lod_column_asset(&asset()).unwrap();
    let packed = pack_world_lod_gpu_column_asset(&expanded).unwrap();
    let assets = BTreeMap::from([(packed.column_key, packed)]);
    let instance = WorldLodColumnInstanceRequest {
        column_key: 7,
        column_generation: 3,
        layer: WORLD_LOD_LAYER_OPAQUE,
        segment_index: 0,
        order: 0,
    };
    let mut residency = WorldLodGpuResidency::default();
    let mut ops = Vec::new();
    let mut source_color_transaction = runtime
        .begin_source_color_transaction(
            &mut gal,
            &colors,
            ShaderPackColorClearValues {
                fog_color: crate::render::vulkanic::commands::ClearColor {
                    r: 0.1,
                    g: 0.2,
                    b: 0.3,
                    a: 1.0,
                },
            },
            &mut ops,
        )
        .unwrap();
    residency
        .stage_visible_uploads(&mut gal, &assets, &[instance], &mut ops)
        .unwrap();
    let draw = residency
        .resolve_visible_draws(&assets, &[instance])
        .unwrap()[0];
    let frame = WorldLodRenderFrame {
        enabled: true,
        combined_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        model_view_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        projection_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        projection_inverse_matrix: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        clip_distance: 24.0,
        micro_offset: MICRO_OFFSET_SCALE,
        noise_intensity: 0.25,
        earth_radius: 6_371_000.0,
        noise_steps: 4,
        noise_dropoff: 96,
        ..WorldLodRenderFrame::default()
    };
    let mut source_resources = WorldLodSourcePassResources::default();
    let opaque_target = source_resources
        .stage_target(&mut gal, &colors, depth)
        .unwrap();
    let source_draw = source_resources
        .stage_draw(
            &mut gal,
            &program,
            opaque_target.primary_color_format,
            draw,
            WorldLodDrawUniform::from_semantics(&frame, draw).unwrap(),
            &distant_source_uniforms(),
            &mut ops,
        )
        .unwrap();
    let pack_set = source_resources
        .stage_pack_resources(
            &mut gal,
            &program,
            opaque_target.primary_color_format,
            &resources,
        )
        .unwrap();
    WorldLodSourcePassResources::append_draw(
        &opaque_target,
        source_draw,
        pack_set,
        crate::render::vulkanic::commands::ClearColor {
            r: 0.1,
            g: 0.2,
            b: 0.3,
            a: 1.0,
        },
        true,
        TextureUsageState::ShaderRead,
        TextureUsageState::Undefined,
        None,
        &mut ops,
    )
    .unwrap();
    depth.append_opaque_depth_snapshot(&mut ops);
    // A normal terrain writer may already have populated the shared
    // pack color target. A later DH draw must load that named image, not
    // infer another clear from DH's local target declaration.
    WorldLodSourcePassResources::append_draw(
        &opaque_target,
        source_draw,
        pack_set,
        crate::render::vulkanic::commands::ClearColor {
            r: 0.1,
            g: 0.2,
            b: 0.3,
            a: 1.0,
        },
        false,
        TextureUsageState::ShaderRead,
        TextureUsageState::ShaderRead,
        None,
        &mut ops,
    )
    .unwrap();
    depth.append_opaque_depth_snapshot(&mut ops);
    assert!(ops
        .iter()
        .any(|op| matches!(op, CommandOp::DrawIndexed { .. })));
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { colors, .. }
            if colors.first().is_some_and(|attachment| {
                attachment.load_op == AttachmentLoadOp::Clear
                    && attachment.clear_color
                        == Some(crate::render::vulkanic::commands::ClearColor {
                            r: 0.1,
                            g: 0.2,
                            b: 0.3,
                            a: 1.0,
                        })
            })
    )));
    let shared_primary_load_ops = ops
        .iter()
        .filter_map(|op| match op {
            CommandOp::BeginPass { target, colors, .. } if *target == opaque_target.target => {
                colors.first().map(|attachment| attachment.load_op)
            }
            _ => None,
        })
        .collect::<Vec<_>>();
    assert_eq!(
        vec![AttachmentLoadOp::Clear, AttachmentLoadOp::Load],
        shared_primary_load_ops,
        "the source-frame scheduler must clear the shared named target once, then preserve the prior producer output",
    );
    assert!(ops.iter().any(|op| matches!(op, CommandOp::CopyTexture(_))));
    let mut batched_ops = Vec::new();
    WorldLodSourcePassResources::append_ordered_batch(
        &opaque_target,
        &[(source_draw, pack_set), (source_draw, pack_set)],
        crate::render::vulkanic::commands::ClearColor {
            r: 0.1,
            g: 0.2,
            b: 0.3,
            a: 1.0,
        },
        false,
        TextureUsageState::ShaderRead,
        TextureUsageState::ShaderRead,
        None,
        &mut batched_ops,
    )
    .unwrap();
    depth.append_opaque_depth_snapshot(&mut batched_ops);
    assert_eq!(
        1,
        batched_ops
            .iter()
            .filter(|op| matches!(op, CommandOp::BeginPass { .. }))
            .count(),
        "compatible opaque DH segments must share one attachment pass"
    );
    assert_eq!(
        2,
        batched_ops
            .iter()
            .filter(|op| matches!(op, CommandOp::DrawIndexed { .. }))
            .count()
    );
    source_color_transaction
        .record_external_outputs(&[TerrainSourceResourceRole::ShaderPackColor(
            "primary".to_string(),
        )])
        .unwrap();
    for (consumer, plan) in consumer_programs.iter().zip(consumer_plans.iter()) {
        source_color_transaction
            .append_fullscreen_consumer(
                plan,
                consumer,
                FullscreenSourcePassFrame {
                    texture_transforms: consumer
                        .pack_texture_transforms(
                            &TerrainSourceTextureTransforms::canonical_minecraft_terrain(),
                        )
                        .unwrap(),
                    scalar_uniforms: consumer
                        .pack_scalar_uniforms(&distant_source_uniforms())
                        .unwrap(),
                    texture_transform_before: TextureUsageState::Undefined,
                    scalar_uniform_before: consumer
                        .execution_interface
                        .scalar_uniforms
                        .map(|_| TextureUsageState::Undefined),
                    clear_values: ShaderPackColorClearValues {
                        fog_color: crate::render::vulkanic::commands::ClearColor {
                            r: 0.0,
                            g: 0.0,
                            b: 0.0,
                            a: 1.0,
                        },
                    },
                    color_attachment_before: Vec::new(),
                    clear_targets_this_pass: None,
                },
                &mut ops,
            )
            .unwrap();
    }
    source_color_transaction.finish(&mut ops).unwrap();
    assert!(
        ops.iter().any(|op| matches!(
            op,
            CommandOp::Draw {
                vertices: 3,
                instances: 1
            }
        )),
        "the complete source-derived DH transaction must execute its declared fullscreen consumers after bootstrap"
    );
    gal.submit(SubmissionBatch {
        label: "world-lod-source.complete-opaque".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod-source.complete-opaque.commands".to_string(),
            operations: ops,
        })],
    })
    .expect("DH source opaque draw and depth snapshot must validate as one submission");
    source_color_transaction
        .confirm(&mut runtime, &mut gal)
        .expect(
            "the combined DH source submission must be the only history confirmation point",
        );
    let reused_colors = runtime
        .stage_source_color_targets(&mut gal, 17, extent)
        .unwrap()
        .expect("the confirmed source target generation must become reusable");
    assert_eq!(
        colors.target("primary"),
        reused_colors.target("primary"),
        "a confirmed source-color transaction must promote its first target generation instead of restaging it"
    );

    source_resources.destroy(&mut gal);
    residency.discard_submission(&mut gal);
    for plan in consumer_plans {
        plan.destroy(&mut gal);
    }
    for handle in resource_handles {
        let _ = gal.destroy(handle);
    }
    depth_cache.discard_submission(&mut gal);
    runtime.discard_source_color_targets_submission(&mut gal);
    runtime.destroy(&mut gal).unwrap();
}

#[test]
fn source_target_snapshot_is_a_valid_depth_to_sampled_gal_transition() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let identity = WorldLodSourceTargetIdentity {
        world_generation: 17,
        shader_pack_generation: 91,
        extent: Extent3d {
            width: 64,
            height: 32,
            depth: 1,
        },
    };
    let mut cache = WorldLodSourceTargetCache::default();
    let targets = cache.stage(&mut gal, identity).unwrap();
    let mut ops = vec![CommandOp::Barrier(texture_barrier(
        targets.distant_depth_texture,
        TextureUsageState::Undefined,
        TextureUsageState::DepthStencilAttachment,
    ))];
    targets.append_opaque_depth_snapshot(&mut ops);

    gal.submit(SubmissionBatch {
        label: "world-lod-source.snapshot".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod-source.snapshot.commands".to_string(),
            operations: ops,
        })],
    })
    .expect("the opaque DH depth snapshot must validate as one GAL transaction");
    cache.confirm_submission(&mut gal);
    assert_eq!(Some(identity), cache.active_identity());
}

#[test]
fn empty_source_depth_snapshot_clears_and_reuses_the_distinct_dh_stream() {
    let mut gal = crate::render::vulkanic::test_support::mock_gal_with_capabilities(presentation_capabilities(
            vulkan_capabilities(),
        ));
    let identity = WorldLodSourceTargetIdentity {
        world_generation: 23,
        shader_pack_generation: 47,
        extent: Extent3d {
            width: 64,
            height: 32,
            depth: 1,
        },
    };
    let mut cache = WorldLodSourceTargetCache::default();
    let (targets, initial_usage, created) = cache
        .stage_for_empty_depth_snapshot(&mut gal, identity)
        .unwrap();
    assert_eq!(TextureUsageState::Undefined, initial_usage);
    assert!(created);
    let mut ops = Vec::new();
    targets
        .append_empty_opaque_depth_snapshot(initial_usage, &mut ops)
        .unwrap();
    assert!(ops.iter().any(|op| matches!(
        op,
        CommandOp::BeginPass { colors, .. } if colors.is_empty()
    )));
    assert!(!ops
        .iter()
        .any(|op| matches!(op, CommandOp::Draw { .. } | CommandOp::DrawIndexed { .. })));
    gal.submit(SubmissionBatch {
        label: "world-lod-source.empty-depth-initial".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod-source.empty-depth-initial.commands".to_string(),
            operations: ops,
        })],
    })
    .expect("an empty DH source frame must establish its depth streams without geometry");
    cache.confirm_submission(&mut gal);
    let active_resources = cache
        .active_semantic_resources(identity)
        .unwrap()
        .expect("the confirmed empty snapshot must remain available to a later source frame");
    assert!(active_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::DistantHorizonsOpaqueDepth)
        .is_some());
    assert!(active_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency)
        .is_some());

    let (reused, reuse_usage, recreated) = cache
        .stage_for_empty_depth_snapshot(&mut gal, identity)
        .unwrap();
    assert_eq!(TextureUsageState::ShaderRead, reuse_usage);
    assert!(!recreated);
    let mut reuse_ops = Vec::new();
    reused
        .append_empty_opaque_depth_snapshot(reuse_usage, &mut reuse_ops)
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "world-lod-source.empty-depth-reuse".to_string(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "world-lod-source.empty-depth-reuse.commands".to_string(),
            operations: reuse_ops,
        })],
    })
    .expect("a confirmed DH source depth stream must be reusable from shader-read state");
    let resources = reused.semantic_resources().unwrap();
    assert!(resources.availability().resources().any(|resource| {
        resource.role == TerrainSourceResourceRole::DistantHorizonsOpaqueDepth
    }));
    assert!(resources.availability().resources().any(|resource| {
        resource.role == TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency
    }));
}

#[test]
fn source_target_identity_rejects_zero_generations_and_non_2d_extent() {
    let invalid_generation = WorldLodSourceTargetIdentity {
        world_generation: 0,
        shader_pack_generation: 1,
        extent: Extent3d {
            width: 1,
            height: 1,
            depth: 1,
        },
    };
    assert!(invalid_generation.validate().is_err());
    let invalid_extent = WorldLodSourceTargetIdentity {
        world_generation: 1,
        shader_pack_generation: 1,
        extent: Extent3d {
            width: 1,
            height: 1,
            depth: 2,
        },
    };
    assert!(invalid_extent.validate().is_err());
}

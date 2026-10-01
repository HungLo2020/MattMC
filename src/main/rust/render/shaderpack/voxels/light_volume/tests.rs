use crate::render::shaderpack::voxels::light_volume::*;

fn descriptor() -> VoxelLightVolumeDescriptor {
    let extent = VoxelLightVolumeExtent {
        width: 8,
        height: 4,
        depth: 8,
    };
    VoxelLightVolumeDescriptor {
        identity: VoxelLightVolumeIdentity::new(
            "shader-pack:complementary/colored-voxel-light",
        )
        .unwrap(),
        shader_pack_generation: 4,
        world_generation: 7,
        resource_generation: 9,
        extent,
        requirements: VoxelLightVolumeRequirements {
            extent,
            occupancy_format: VoxelLightVolumeFormat::OccupancyR8Uint,
            lighting_format: VoxelLightVolumeFormat::LightingRgba16Float,
            ping_pong_by_frame_parity: true,
            linear_filtered_lighting: true,
            update_policy: VoxelLightVolumeUpdatePolicy::full_rate(),
        },
        mapping: VoxelLightVolumeMapping::complementary(
            extent,
            [32, 64, -16],
            [0.25, 0.5, 0.75],
        )
        .unwrap(),
    }
}

#[test]
fn frame_mapping_preserves_generation_and_derives_source_camera_offset() {
    let descriptor = descriptor();
    let previous = VoxelLightVolumeFrameMapping::from_descriptor(&descriptor);
    let mut current = previous.clone();
    current.camera_cell = [
        previous.camera_cell[0] + 3,
        previous.camera_cell[1] - 1,
        previous.camera_cell[2] - 2,
    ];
    assert_eq!(
        [-3, 1, 2],
        current.previous_to_current_offset(&previous).unwrap()
    );

    let mut stale = current.clone();
    stale.world_generation += 1;
    assert!(current.previous_to_current_offset(&stale).is_err());
    assert!(stale.validate_against(&descriptor).is_err());
}

#[test]
fn resource_compatibility_excludes_mapping_but_not_generation_or_extent() {
    let descriptor = descriptor();
    let mut fractional = descriptor.clone();
    fractional.mapping.camera_fraction = [0.75, 0.25, 0.5];
    assert!(descriptor.resource_compatible_with(&fractional));

    let mut next_generation = descriptor.clone();
    next_generation.resource_generation += 1;
    assert!(!descriptor.resource_compatible_with(&next_generation));

    let mut different_extent = descriptor.clone();
    different_extent.extent.width += 1;
    assert!(!descriptor.resource_compatible_with(&different_extent));
}

#[test]
fn temporal_mapping_packs_shadowcomp_previous_position_inputs() {
    let descriptor = descriptor();
    let previous = VoxelLightVolumeFrameMapping::from_descriptor(&descriptor);
    let mut current = previous.clone();
    current.camera_cell = [
        previous.camera_cell[0] + 2,
        previous.camera_cell[1] - 3,
        previous.camera_cell[2] + 1,
    ];
    let mapping = VoxelLightVolumeTemporalMapping::from_frame_mappings(
        &current,
        &previous,
        descriptor.extent,
        VoxelLightVolumeUpdatePolicy {
            temporal_reprojection: true,
            alternate_x_half_rate: true,
            preserve_behind_view: true,
        },
        7,
        Some(VoxelLightVolumeViewDirection {
            normalized_camera_forward: [0.0, 0.0, 1.0],
        }),
    )
    .unwrap();
    assert_eq!([-2, 3, -1, 0], mapping.previous_camera_minus_current);
    assert_eq!(
        [
            descriptor.extent.width as i32,
            descriptor.extent.height as i32,
            descriptor.extent.depth as i32,
            0,
        ],
        mapping.extent
    );
    assert_eq!([1, 1, 1, 0], mapping.update_schedule);
    assert_eq!([0.0, 0.0, 1.0, 0.0], mapping.camera_forward);
    assert_eq!((-2_i32).to_le_bytes(), mapping.std140_bytes()[0..4]);
    assert_eq!(
        (descriptor.extent.width as i32).to_le_bytes(),
        mapping.std140_bytes()[16..20]
    );
}

#[test]
fn behind_view_policy_requires_a_valid_camera_direction() {
    let descriptor = descriptor();
    let mapping = VoxelLightVolumeFrameMapping::from_descriptor(&descriptor);
    assert!(VoxelLightVolumeTemporalMapping::from_frame_mappings(
        &mapping,
        &mapping,
        descriptor.extent,
        VoxelLightVolumeUpdatePolicy {
            temporal_reprojection: true,
            alternate_x_half_rate: true,
            preserve_behind_view: true,
        },
        0,
        None,
    )
    .is_err());
}

#[test]
fn camera_direction_matches_the_source_center_far_ray_and_rejects_singular_matrices() {
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    assert_eq!(
        [0.0, 0.0, 1.0],
        VoxelLightVolumeViewDirection::from_camera_matrices(identity, identity)
            .unwrap()
            .normalized_camera_forward
    );
    assert!(VoxelLightVolumeViewDirection::from_camera_matrices([0.0; 16], identity).is_err());
}

#[test]
fn shader_mapping_preserves_complementary_scene_to_voxel_inputs_in_std140_order() {
    let descriptor = descriptor();
    let mapping = VoxelLightVolumeShaderMapping::from_descriptor(&descriptor).unwrap();
    assert_eq!(
        [
            descriptor.mapping.camera_fraction[0] + descriptor.extent.width as f32 * 0.5,
            descriptor.mapping.camera_fraction[1] + descriptor.extent.height as f32 * 0.5,
            descriptor.mapping.camera_fraction[2] + descriptor.extent.depth as f32 * 0.5,
            descriptor.mapping.sample_normal_offset,
        ],
        mapping.scene_to_volume_offset_and_normal_offset
    );
    assert_eq!(
        [
            1.0 / descriptor.extent.width as f32,
            1.0 / descriptor.extent.height as f32,
            1.0 / descriptor.extent.depth as f32,
            0.0,
        ],
        mapping.inverse_extent
    );
    assert_eq!(
        [
            descriptor.mapping.scene_to_volume_scale[0],
            descriptor.mapping.scene_to_volume_scale[1],
            descriptor.mapping.scene_to_volume_scale[2],
            0.0,
        ],
        mapping.scene_to_volume_scale
    );
    let bytes = mapping.std140_bytes();
    assert_eq!(VoxelLightVolumeShaderMapping::STD140_SIZE, bytes.len());
    assert_eq!(
        mapping.scene_to_volume_offset_and_normal_offset[0].to_le_bytes(),
        bytes[0..4]
    );
    assert_eq!(
        mapping.scene_to_volume_scale[0].to_le_bytes(),
        bytes[16..20]
    );
    assert_eq!(mapping.inverse_extent[0].to_le_bytes(), bytes[32..36]);
    assert_eq!(mapping.valid_world_min[0].to_le_bytes(), bytes[48..52]);
    assert_eq!(
        mapping.valid_world_max_exclusive[0].to_le_bytes(),
        bytes[64..68]
    );
    assert_eq!(mapping.camera_cell[0].to_le_bytes(), bytes[80..84]);
}

fn update(
    kind: VoxelLightVolumeKind,
    region: VoxelLightVolumeRegion,
) -> VoxelLightVolumeUpdate {
    let descriptor = descriptor();
    VoxelLightVolumeUpdate {
        identity: descriptor.identity.clone(),
        shader_pack_generation: 4,
        world_generation: 7,
        resource_generation: 9,
        kind,
        region,
        texels: vec![
            kind.format().bytes_per_texel() as u8;
            region.extent.byte_len(kind.format()) as usize
        ],
    }
}

#[test]
fn complete_generation_requires_all_semantic_fields_and_ping_pongs_by_frame() {
    let descriptor = descriptor();
    let mut cache = VoxelLightVolumeCache::new();
    cache.replace_descriptor(descriptor.clone()).unwrap();
    assert!(cache.binding_for_frame(0).is_err());
    for kind in [
        VoxelLightVolumeKind::Occupancy,
        VoxelLightVolumeKind::FloodFillEven,
        VoxelLightVolumeKind::FloodFillOdd,
    ] {
        cache
            .apply_update(update(
                kind,
                VoxelLightVolumeRegion::whole(descriptor.extent),
            ))
            .unwrap();
    }
    assert_eq!(
        VoxelLightVolumeKind::FloodFillOdd,
        cache.binding_for_frame(0).unwrap().active_light_field
    );
    assert_eq!(
        VoxelLightVolumeKind::FloodFillEven,
        cache.binding_for_frame(1).unwrap().active_light_field
    );
}

#[test]
fn complementary_ping_pong_contract_swaps_source_and_output_by_frame_parity() {
    assert_eq!(
        VoxelLightVolumeKind::FloodFillEven,
        flood_fill_source_field_for_frame(0)
    );
    assert_eq!(
        VoxelLightVolumeKind::FloodFillOdd,
        flood_fill_output_field_for_frame(0)
    );
    assert_eq!(
        VoxelLightVolumeKind::FloodFillOdd,
        flood_fill_source_field_for_frame(1)
    );
    assert_eq!(
        VoxelLightVolumeKind::FloodFillEven,
        flood_fill_output_field_for_frame(1)
    );
}

#[test]
fn shadow_compute_policy_requires_explicit_temporal_source_semantics() {
    assert!(
        VoxelLightVolumeUpdatePolicy::from_shadow_compute_source("void main() {}").is_err()
    );
    let policy = VoxelLightVolumeUpdatePolicy::from_shadow_compute_source(
        "#define OPTIMIZATION_ACL_HALF_RATE_UPDATES\n#define OPTIMIZATION_ACL_BEHIND_PLAYER\nvoid main() { vec3 offset = floor(previousCameraPosition) - floor(cameraPosition); }",
    )
    .unwrap();
    assert!(policy.temporal_reprojection);
    assert!(policy.alternate_x_half_rate);
    assert!(policy.preserve_behind_view);
    assert!(policy.require_selected_source_support().is_ok());
    assert!(VoxelLightVolumeUpdatePolicy {
        temporal_reprojection: true,
        alternate_x_half_rate: true,
        preserve_behind_view: false,
    }
    .require_selected_source_support()
    .is_ok());
}

#[test]
fn partial_update_is_rejected_until_the_field_has_a_complete_base() {
    let descriptor = descriptor();
    let mut cache = VoxelLightVolumeCache::new();
    cache.replace_descriptor(descriptor.clone()).unwrap();
    let region = VoxelLightVolumeRegion {
        x: 1,
        y: 1,
        z: 1,
        extent: VoxelLightVolumeExtent {
            width: 2,
            height: 1,
            depth: 2,
        },
    };
    assert!(cache
        .apply_update(update(VoxelLightVolumeKind::FloodFillEven, region))
        .is_err());
    cache
        .apply_update(update(
            VoxelLightVolumeKind::FloodFillEven,
            VoxelLightVolumeRegion::whole(descriptor.extent),
        ))
        .unwrap();
    cache
        .apply_update(update(VoxelLightVolumeKind::FloodFillEven, region))
        .unwrap();
}

#[test]
fn stale_or_mixed_generation_is_rejected_before_copying() {
    let descriptor = descriptor();
    let mut cache = VoxelLightVolumeCache::new();
    cache.replace_descriptor(descriptor.clone()).unwrap();
    let mut stale = update(
        VoxelLightVolumeKind::Occupancy,
        VoxelLightVolumeRegion::whole(descriptor.extent),
    );
    stale.world_generation = 8;
    assert!(cache.apply_update(stale).is_err());
    assert!(cache.fields.is_empty());
}

#[test]
fn stale_descriptor_cannot_replace_a_complete_generation() {
    let descriptor = descriptor();
    let mut cache = VoxelLightVolumeCache::new();
    cache.replace_descriptor(descriptor.clone()).unwrap();
    assert!(cache.replace_descriptor(descriptor).is_err());
}

#[test]
fn world_retirement_drops_owned_fields() {
    let descriptor = descriptor();
    let mut cache = VoxelLightVolumeCache::new();
    cache.replace_descriptor(descriptor.clone()).unwrap();
    cache
        .apply_update(update(
            VoxelLightVolumeKind::Occupancy,
            VoxelLightVolumeRegion::whole(descriptor.extent),
        ))
        .unwrap();
    cache.retire_world_generation(7);
    assert!(cache.descriptor().is_none());
    assert!(cache.fields.is_empty());
}

#[test]
fn semantic_sample_probe_uses_camera_relative_mapping_and_rejects_bounds() {
    let descriptor = descriptor();
    let mut cache = VoxelLightVolumeCache::new();
    cache.replace_descriptor(descriptor.clone()).unwrap();
    for kind in [
        VoxelLightVolumeKind::Occupancy,
        VoxelLightVolumeKind::FloodFillEven,
        VoxelLightVolumeKind::FloodFillOdd,
    ] {
        cache
            .apply_update(update(
                kind,
                VoxelLightVolumeRegion::whole(descriptor.extent),
            ))
            .unwrap();
    }
    let probe = cache
        .sample_probe(0, [32.0, 64.0, -16.0], [0.0, 1.0, 0.0])
        .unwrap();
    assert_eq!(VoxelLightVolumeKind::FloodFillOdd, probe.active_light_field);
    assert!(probe
        .normalized_coordinate
        .iter()
        .all(|value| *value > 0.0 && *value < 1.0));
    assert!(cache
        .sample_probe(0, [10_000.0, 64.0, -16.0], [0.0, 1.0, 0.0])
        .is_err());
}

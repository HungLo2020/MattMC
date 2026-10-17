use crate::render::shaderpack::voxels::light_volume::{
    VoxelLightVolumeExtent, VoxelLightVolumeIdentity, VoxelLightVolumeMapping,
    VoxelLightVolumeRequirements,
};
use crate::render::shaderpack::voxels::occupancy::*;
            use crate::render::vulkanic::commands::{CommandListDesc, SubmissionBatch};
use crate::render::vulkanic::resources::IndexType;
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
use crate::render::shaderpack::contracts::terrain::{
    bundled_complementary_hung_loified_source, derive_complementary_terrain_contract,
};
use crate::render::shaderpack::contracts::terrain::{
    TerrainPassContract, TerrainPassInput, TerrainPassOperation, TerrainPassOutput,
    TerrainSourcePassKind,
};
use crate::render::scene::voxel_source::TerrainVoxelSourceVertex;

fn descriptor() -> VoxelLightVolumeDescriptor {
    let extent = VoxelLightVolumeExtent {
        width: 8,
        height: 4,
        depth: 8,
    };
    VoxelLightVolumeDescriptor {
        identity: VoxelLightVolumeIdentity::new("shader-pack:test/occupancy").unwrap(),
        shader_pack_generation: 1,
        world_generation: 2,
        resource_generation: 3,
        extent,
        requirements: VoxelLightVolumeRequirements {
            extent,
            occupancy_format:
                crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint,
            lighting_format:
                crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
            ping_pong_by_frame_parity: true,
            linear_filtered_lighting: true,
            update_policy:
                crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeUpdatePolicy::full_rate(),
        },
        mapping: VoxelLightVolumeMapping::complementary(extent, [0, 0, 0], [0.0; 3]).unwrap(),
    }
}

fn sample(position: [f32; 3], material: i32) -> TerrainVoxelSample {
    TerrainVoxelSample {
        vertex_position: position,
        mid_block_packed: 0,
        shader_material_id: material,
        model_transform: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
    }
}

fn materials() -> VoxelMaterialMap {
    let source = ShaderPackSource::new(
        "test",
        1,
        vec![ShaderSourceFile::new(
            "lib/misc/voxelization.glsl",
            "int GetVoxelIDs(int mat) { if (mat == 30008) return 2; if (mat == 30012) return 3; return 1; }\nvoid UpdateVoxelMap(int mat) { if (mat == 32000 || mat < 30000 && mat % 4 == 1) return; }",
        )],
    )
    .unwrap();
    let contract = TerrainPassContract {
        pass_kind: TerrainSourcePassKind::OpaqueCutout,
        pack_name: "test".to_string(),
        generation: 1,
        program_path: "unused".to_string(),
        material_classes: Default::default(),
        inputs: std::collections::BTreeSet::from([TerrainPassInput::ColoredVoxelLightVolume]),
        outputs: std::collections::BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
        output_color_slots: std::collections::BTreeMap::from([(
            TerrainPassOutput::LitTerrainColor,
            0,
        )]),
        property_defines: Default::default(),
        material_ids: Default::default(),
        runtime_block_state_material_ids: None,
        operations: vec![TerrainPassOperation::ColoredVoxelLighting],
        required_resources: Default::default(),
        voxel_light_volume_requirements: None,
        normal_alpha_test: Default::default(),
        translucent_raster_state: None,
        unsupported: Default::default(),
    };
    VoxelMaterialMap::derive(&source, &contract).unwrap()
}

fn new_voxelizer(descriptor: VoxelLightVolumeDescriptor) -> TerrainOccupancyVoxelizer {
    TerrainOccupancyVoxelizer::new(descriptor, materials()).unwrap()
}

fn static_terrain_mesh_with_identity(
    mesh_key: u64,
    mesh_generation: u64,
    position: [f32; 3],
    layout_version: u32,
) -> WorldMeshAsset {
    WorldMeshAsset {
        mesh_key,
        mesh_generation,
        vertex_layout_version: layout_version,
        index_type: IndexType::U16,
        vertices: vec![
            WorldMeshVertex {
                position,
                uv: [0.0, 0.0],
                shader_atlas_uv: [0.0, 0.0],
                shader_block_id: 30_008,
                shader_material_type: 0,
                terrain_material_bits: 0,
                mid_block_packed: 0,
                color_argb: u32::MAX,
                normal_packed: 0,
                light: 0,
            };
            3
        ],
        index_bytes: vec![0, 0, 1, 0, 2, 0],
        sections: Vec::new(),
        entity_identity: String::new(),
    }
}

fn static_terrain_mesh(position: [f32; 3], layout_version: u32) -> WorldMeshAsset {
    static_terrain_mesh_with_identity(0x76_6f_78_65_6c, 1, position, layout_version)
}

fn identity_transform() -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn submit_runtime_update(
    gal: &mut VulkanicGal,
    runtime: &mut TerrainOccupancyRuntime,
    label: &str,
    operations: Vec<CommandOp>,
) {
    let list = gal
        .create_command_list(CommandListDesc {
            label: format!("{label}.list"),
            operations,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: label.to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    runtime.confirm_submission().unwrap();
}

#[test]
fn initial_generation_uses_source_midpoint_and_subsequent_update_is_bounded() {
    let mut voxelizer = new_voxelizer(descriptor());
    let first = voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    assert_eq!(
        Some(VoxelLightVolumeRegion::whole(descriptor().extent)),
        first.updated_region
    );
    assert_eq!(1, first.emitted_samples);
    assert!(voxelizer.cache().binding_for_frame(0).is_err());
    voxelizer.confirm_pending_upload().unwrap();
    let second = voxelizer
        .update_from_samples([sample([1.0, 0.0, 0.0], 30_008)])
        .unwrap();
    assert_eq!(
        Some(VoxelLightVolumeRegion {
            x: 4,
            y: 2,
            z: 4,
            extent: VoxelLightVolumeExtent {
                width: 2,
                height: 1,
                depth: 1
            }
        }),
        second.updated_region
    );
}

#[test]
fn skips_non_solids_and_uses_later_source_samples_for_coarse_voxel_collisions() {
    let mut voxelizer = new_voxelizer(descriptor());
    let stats = voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 32_000)])
        .unwrap();
    assert_eq!(1, stats.skipped_non_solid_samples);
    // An uninitialized volume still publishes its (empty) whole field.
    assert_eq!(
        Some(VoxelLightVolumeRegion::whole(descriptor().extent)),
        stats.updated_region
    );
    assert!(voxelizer
        .pending_upload()
        .expect("empty initial occupancy field")
        .texels
        .iter()
        .all(|texel| *texel == 0));
    voxelizer.confirm_pending_upload().unwrap();
    let stats = voxelizer
        .update_from_samples([
            sample([0.0, 0.0, 0.0], 30_008),
            sample([0.0, 0.0, 0.0], 30_004),
            sample([0.0, 0.0, 0.0], 30_012),
        ])
        .unwrap();
    assert_eq!(3, stats.emitted_samples);
    assert_eq!(2, stats.overwritten_samples);
    assert!(voxelizer
        .pending_upload()
        .expect("merged occupancy update")
        .texels
        .contains(&3));
}

#[test]
fn indexed_vertex_occupancy_does_not_invent_triangle_volume_coverage() {
    let descriptor = descriptor();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    let snapshot = indexed_terrain_snapshot(
        1,
        vec![
            sample([0.0, 0.0, 0.0], 30_008),
            sample([3.0, 0.0, 0.0], 30_008),
            sample([0.0, 0.0, 3.0], 30_008),
            sample([3.0, 0.0, 3.0], 30_008),
        ],
        vec![0, 1, 2],
        None,
    )
    .unwrap();
    assert_eq!(3, snapshot.samples.len());
    let stats = voxelizer
        .update_from_samples(snapshot.samples.iter().copied())
        .unwrap();
    assert_eq!(3, stats.changed_voxels);
    let interior =
        voxel_index(descriptor.extent.width, descriptor.extent.height, [5, 2, 5]).unwrap();
    let unreferenced =
        voxel_index(descriptor.extent.width, descriptor.extent.height, [7, 2, 7]).unwrap();
    assert_eq!(0, voxelizer.pending_occupancy.as_ref().unwrap()[interior]);
    assert_eq!(
        0,
        voxelizer.pending_occupancy.as_ref().unwrap()[unreferenced]
    );
}

#[test]
fn d3_residency_records_bounded_copy_and_retires_owned_resources() {
    let descriptor = descriptor();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut resources = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
    let mut operations = Vec::new();
    resources
        .append_upload(voxelizer.pending_upload().unwrap(), &mut operations)
        .unwrap();
    assert!(!resources.is_initialized());
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::CopyBufferToTexture(region)
            if region.extent.depth == descriptor.extent.depth && region.texture_origin.z == 0
    )));
    let list = gal
        .create_command_list(CommandListDesc {
            label: "occupancy-upload".to_owned(),
            operations,
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "occupancy-upload".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    resources.confirm_submission().unwrap();
    assert!(resources.is_initialized());
    voxelizer.confirm_pending_upload().unwrap();
    let texture = resources.texture;
    resources.destroy(&mut gal).unwrap();
    let retired = gal.retire_through_for_test(token.submission).unwrap();
    assert!(retired.contains(&texture));
}

#[test]
fn gpu_residency_rejects_mixed_generations_and_can_discard_a_rejected_submission() {
    let descriptor = descriptor();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut resources = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
    let mut operations = Vec::new();
    resources
        .append_upload(voxelizer.pending_upload().unwrap(), &mut operations)
        .unwrap();
    assert!(resources
        .append_upload(voxelizer.pending_upload().unwrap(), &mut operations)
        .is_err());
    resources.discard_pending_submission();
    voxelizer.discard_pending_upload();

    let mut stale = descriptor.clone();
    stale.resource_generation += 1;
    let mut stale_voxelizer = new_voxelizer(stale);
    stale_voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    assert!(resources
        .append_upload(stale_voxelizer.pending_upload().unwrap(), &mut Vec::new())
        .is_err());
}

#[test]
fn static_terrain_occupancy_runtime_copies_meshes_and_rolls_back_rejected_uploads() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
    let identity = [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ];
    let first = static_terrain_mesh([0.0, 0.0, 0.0], WORLD_MESH_VERTEX_LAYOUT_V3);
    let mut first_ops = Vec::new();
    let first_stats = runtime
        .append_static_terrain_snapshot(
            [(&first, identity, WORLD_STRATUM_TERRAIN)],
            &mut first_ops,
        )
        .unwrap();
    assert_eq!(
        Some(VoxelLightVolumeRegion::whole(runtime.descriptor().extent)),
        first_stats.updated_region
    );
    assert!(first_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::CopyBufferToTexture(region) if region.extent.depth == runtime.descriptor().extent.depth
    )));
    let first_list = gal
        .create_command_list(CommandListDesc {
            label: "occupancy-runtime-first".to_owned(),
            operations: first_ops,
        })
        .unwrap();
    let first_submission = gal
        .submit(SubmissionBatch {
            label: "occupancy-runtime-first".to_owned(),
            command_lists: vec![first_list],
        })
        .unwrap();
    runtime.confirm_submission().unwrap();
    assert!(runtime.is_initialized());

    let mut unchanged_ops = Vec::new();
    let unchanged = runtime
        .append_static_terrain_snapshot(
            [(&first, identity, WORLD_STRATUM_TERRAIN)],
            &mut unchanged_ops,
        )
        .unwrap();
    assert!(unchanged.updated_region.is_none());
    assert_eq!(0, unchanged.input_samples);
    assert_eq!(0, unchanged.emitted_samples);
    assert_eq!(0, unchanged.uploaded_bytes);
    assert!(unchanged_ops.is_empty());

    let mut changed = static_terrain_mesh([1.0, 0.0, 0.0], WORLD_MESH_VERTEX_LAYOUT_V3);
    changed.mesh_generation = 2;
    let mut rejected_ops = Vec::new();
    let changed_stats = runtime
        .append_static_terrain_snapshot(
            [(&changed, identity, WORLD_STRATUM_TERRAIN)],
            &mut rejected_ops,
        )
        .unwrap();
    assert_eq!(2, changed_stats.changed_voxels);
    assert!(changed_stats.updated_region.is_some());
    runtime.discard_submission();
    assert!(runtime.is_initialized());

    let mut replacement_ops = Vec::new();
    runtime
        .append_static_terrain_snapshot(
            [(&changed, identity, WORLD_STRATUM_TERRAIN)],
            &mut replacement_ops,
        )
        .unwrap();
    let replacement_list = gal
        .create_command_list(CommandListDesc {
            label: "occupancy-runtime-replacement".to_owned(),
            operations: replacement_ops,
        })
        .unwrap();
    let replacement_submission = gal
        .submit(SubmissionBatch {
            label: "occupancy-runtime-replacement".to_owned(),
            command_lists: vec![replacement_list],
        })
        .unwrap();
    runtime.confirm_submission().unwrap();

    let unsupported_layout = static_terrain_mesh([2.0, 0.0, 0.0], 2);
    assert!(runtime
        .append_static_terrain_snapshot(
            [(&unsupported_layout, identity, WORLD_STRATUM_TERRAIN)],
            &mut Vec::new(),
        )
        .is_err());

    let texture = runtime.resources.texture;
    runtime.destroy(&mut gal).unwrap();
    let retired = gal
        .retire_through_for_test(replacement_submission.submission)
        .unwrap();
    assert!(retired.contains(&texture));
    assert!(first_submission.submission < replacement_submission.submission);
}

#[test]
fn occupancy_runtime_deltas_preserve_other_meshes_and_reject_stale_generations() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let transform = identity_transform();
    let first = static_terrain_mesh_with_identity(
        0x10,
        1,
        [0.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    let second = static_terrain_mesh_with_identity(
        0x20,
        1,
        [2.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    let mut initial_operations = Vec::new();
    runtime
        .append_static_terrain_snapshot(
            [
                (&first, transform, WORLD_STRATUM_TERRAIN),
                (&second, transform, WORLD_STRATUM_TERRAIN),
            ],
            &mut initial_operations,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-initial",
        initial_operations,
    );
    assert_eq!(2, runtime.mesh_snapshot_count());
    let second_index =
        voxel_index(descriptor.extent.width, descriptor.extent.height, [6, 2, 4]).unwrap();
    assert_ne!(0, runtime.voxelizer.occupancy[second_index]);

    let first_replacement = static_terrain_mesh_with_identity(
        0x10,
        2,
        [1.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    let mut replacement_operations = Vec::new();
    let replacement = runtime
        .append_static_terrain_deltas(
            [TerrainOccupancyMeshDelta::Upsert {
                mesh: &first_replacement,
                model_transform: transform,
                stratum: WORLD_STRATUM_TERRAIN,
            }],
            &mut replacement_operations,
        )
        .unwrap();
    assert_eq!(2, replacement.changed_voxels);
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-replace",
        replacement_operations,
    );
    assert_eq!(2, runtime.mesh_snapshot_count());
    assert_ne!(0, runtime.voxelizer.occupancy[second_index]);

    assert!(runtime
        .append_static_terrain_deltas(
            [TerrainOccupancyMeshDelta::Remove {
                mesh_key: first_replacement.mesh_key,
                mesh_generation: 1,
            }],
            &mut Vec::new(),
        )
        .is_err());
    let conflicting_identity = static_terrain_mesh_with_identity(
        0x10,
        first_replacement.mesh_generation,
        [5.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    assert!(runtime
        .append_static_terrain_snapshot(
            [
                (&conflicting_identity, transform, WORLD_STRATUM_TERRAIN),
                (&second, transform, WORLD_STRATUM_TERRAIN),
            ],
            &mut Vec::new(),
        )
        .is_err());

    let third = static_terrain_mesh_with_identity(
        0x30,
        1,
        [-2.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    let mut discarded_operations = Vec::new();
    runtime
        .append_static_terrain_deltas(
            [TerrainOccupancyMeshDelta::Upsert {
                mesh: &third,
                model_transform: transform,
                stratum: WORLD_STRATUM_TERRAIN,
            }],
            &mut discarded_operations,
        )
        .unwrap();
    runtime.discard_submission();
    assert_eq!(2, runtime.mesh_snapshot_count());
    assert_ne!(0, runtime.voxelizer.occupancy[second_index]);

    let mut retry_operations = Vec::new();
    runtime
        .append_static_terrain_deltas(
            [TerrainOccupancyMeshDelta::Upsert {
                mesh: &third,
                model_transform: transform,
                stratum: WORLD_STRATUM_TERRAIN,
            }],
            &mut retry_operations,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-retry",
        retry_operations,
    );
    assert_eq!(3, runtime.mesh_snapshot_count());

    let mut remove_operations = Vec::new();
    runtime
        .append_static_terrain_deltas(
            [TerrainOccupancyMeshDelta::Remove {
                mesh_key: first_replacement.mesh_key,
                mesh_generation: first_replacement.mesh_generation,
            }],
            &mut remove_operations,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-remove",
        remove_operations,
    );
    assert_eq!(2, runtime.mesh_snapshot_count());
    assert_ne!(0, runtime.voxelizer.occupancy[second_index]);
}

#[test]
fn occupancy_runtime_accepts_a_newer_complete_snapshot_with_a_lower_hash_identity() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
    let transform = identity_transform();
    let high_hash_identity = static_terrain_mesh_with_identity(
        0x71,
        u64::MAX - 1,
        [0.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    let mut first_operations = Vec::new();
    runtime
        .append_static_terrain_snapshot(
            [(&high_hash_identity, transform, WORLD_STRATUM_TERRAIN)],
            &mut first_operations,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-high-hash-identity",
        first_operations,
    );

    let lower_hash_identity = static_terrain_mesh_with_identity(
        0x71,
        7,
        [1.0, 0.0, 0.0],
        WORLD_MESH_VERTEX_LAYOUT_V3,
    );
    let mut replacement_operations = Vec::new();
    runtime
        .append_static_terrain_snapshot(
            [(&lower_hash_identity, transform, WORLD_STRATUM_TERRAIN)],
            &mut replacement_operations,
        )
        .expect(
            "complete terrain snapshots must compare generation identities, not hash order",
        );
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-lower-hash-identity",
        replacement_operations,
    );
    assert_eq!(
        7,
        runtime
            .meshes
            .get(&0x71)
            .expect("replacement snapshot must become live")
            .mesh_generation
    );
}

#[test]
fn occupancy_runtime_replaces_generations_atomically_and_drops_old_mesh_state() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let mesh = static_terrain_mesh([0.0, 0.0, 0.0], WORLD_MESH_VERTEX_LAYOUT_V3);
    let mut initial_operations = Vec::new();
    runtime
        .append_static_terrain_snapshot(
            [(&mesh, identity_transform(), WORLD_STRATUM_TERRAIN)],
            &mut initial_operations,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-generation-initial",
        initial_operations,
    );
    let old_texture = runtime.resources.texture;
    assert!(runtime.is_initialized());
    assert_eq!(1, runtime.mesh_snapshot_count());

    let mut incompatible = descriptor.clone();
    incompatible.shader_pack_generation += 1;
    assert!(runtime
        .replace_descriptor(&mut gal, incompatible, materials())
        .is_err());
    assert_eq!(old_texture, runtime.resources.texture);
    assert!(runtime.is_initialized());
    assert_eq!(1, runtime.mesh_snapshot_count());

    let mut replacement = descriptor;
    replacement.world_generation += 1;
    replacement.resource_generation += 1;
    runtime
        .replace_descriptor(&mut gal, replacement, materials())
        .unwrap();
    assert_ne!(old_texture, runtime.resources.texture);
    assert!(!runtime.is_initialized());
    assert_eq!(0, runtime.mesh_snapshot_count());
}

#[test]
fn occupancy_runtime_accepts_compact_frontend_terrain_sources() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
    let vertices = [TerrainVoxelSourceVertex {
        position: [0.0, 0.0, 0.0],
        mid_block_packed: 0,
        shader_material_id: 30_008,
    }];
    let source = TerrainVoxelSourceMesh {
        mesh_key: 0x88,
        mesh_generation: 1,
        vertices: Arc::new(vertices.to_vec()),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };
    let mut operations = Vec::new();
    let stats = runtime
        .append_terrain_source_snapshot([source.clone()], &mut operations)
        .unwrap();
    assert_eq!(1, stats.input_samples);
    assert_eq!(1, stats.emitted_samples);
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-frontend-source",
        operations,
    );
    assert!(runtime.is_initialized());
    assert_eq!(1, runtime.mesh_snapshot_count());

    let mut repeated_operations = Vec::new();
    let repeated = runtime
        .append_terrain_source_snapshot([source], &mut repeated_operations)
        .unwrap();
    assert_eq!(TerrainOccupancyUpdateStats::default(), repeated);
    assert!(repeated_operations.is_empty());
}

#[test]
fn puddle_voxelizer_uses_only_translucent_non_water_shadow_scene_samples() {
    let descriptor = PuddleOccupancyDescriptor {
        shader_pack_generation: 1,
        world_generation: 2,
        resource_generation: 3,
        camera_fraction: [0.25, 0.5, 0.75],
        shadow_scene_from_world: identity_transform(),
    };
    let mut puddles = TerrainPuddleVoxelizer::new(descriptor).unwrap();
    let mesh = TerrainVoxelSourceMesh {
        mesh_key: 0x90,
        mesh_generation: 1,
        vertices: Arc::new(vec![
            TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            },
            TerrainVoxelSourceVertex {
                position: [1.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 32_000,
            },
            TerrainVoxelSourceVertex {
                position: [0.0, -4.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            },
            TerrainVoxelSourceVertex {
                position: [100.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            },
        ]),
        indices: Arc::new(vec![0, 1, 2, 3]),
        translucent_indices: Arc::new(vec![0, 0, 1, 2, 3]),
        transform: identity_transform(),
    };

    let stats = puddles.rebuild_from_meshes([mesh]).unwrap();
    assert_eq!(5, stats.translucent_samples);
    assert_eq!(1, stats.water_samples_skipped);
    assert_eq!(1, stats.below_scene_samples_skipped);
    assert_eq!(1, stats.out_of_bounds_samples_skipped);
    assert_eq!(1, stats.changed_texels);
    let center = 64 * PuddleOccupancyDescriptor::EXTENT as usize + 64;
    assert_eq!(10, puddles.texels()[center]);

    let unchanged = puddles.rebuild_from_meshes(Vec::new()).unwrap();
    assert_eq!(1, unchanged.changed_texels);
    assert_eq!(0, puddles.texels()[center]);
}

#[test]
fn puddle_gpu_residency_records_a_confirmed_2d_unsigned_upload_and_retires() {
    let descriptor = PuddleOccupancyDescriptor {
        shader_pack_generation: 1,
        world_generation: 2,
        resource_generation: 3,
        camera_fraction: [0.0; 3],
        shadow_scene_from_world: identity_transform(),
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut resources = TerrainPuddleGpuResources::create(&mut gal, descriptor).unwrap();
    let mut operations = Vec::new();
    resources
        .append_upload(
            &vec![
                0;
                PuddleOccupancyDescriptor::EXTENT as usize
                    * PuddleOccupancyDescriptor::EXTENT as usize
            ],
            &mut operations,
        )
        .unwrap();
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::CopyBufferToTexture(region)
            if region.extent.width == PuddleOccupancyDescriptor::EXTENT
                && region.extent.height == PuddleOccupancyDescriptor::EXTENT
                && region.extent.depth == 1
    )));
    assert!(resources.append_upload(&[], &mut Vec::new()).is_err());
    let list = gal
        .create_command_list(CommandListDesc {
            label: "puddle.upload.list".to_string(),
            operations,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "puddle.upload".to_string(),
        command_lists: vec![list],
    })
    .unwrap();
    resources.confirm_submission().unwrap();
    resources.destroy(&mut gal).unwrap();
}

#[test]
fn puddle_runtime_exposes_only_confirmed_semantic_resources_and_rolls_back_rejected_uploads() {
    let descriptor = PuddleOccupancyDescriptor {
        shader_pack_generation: 1,
        world_generation: 2,
        resource_generation: 3,
        camera_fraction: [0.0, 0.0, 0.0],
        shadow_scene_from_world: identity_transform(),
    };
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime = TerrainPuddleRuntime::create(&mut gal, descriptor).unwrap();
    let mesh = TerrainVoxelSourceMesh {
        mesh_key: 0x90,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 2,
        }]),
        indices: Arc::new(vec![0]),
        translucent_indices: Arc::new(vec![0]),
        transform: identity_transform(),
    };
    assert!(runtime.semantic_resource_set().is_err());

    let mut first_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot(descriptor, [mesh.clone()], &mut first_ops)
        .unwrap();
    assert!(runtime.has_pending_submission());
    let pending = runtime
        .semantic_resource_set_for_pending_submission()
        .unwrap();
    assert!(pending
        .combined_sampler_for(TerrainSourceResourceRole::PuddleOccupancy)
        .is_some());
    assert!(pending
        .storage_texture_for(TerrainSourceResourceRole::PuddleOccupancy)
        .is_some());
    let first_list = gal
        .create_command_list(CommandListDesc {
            label: "puddle-runtime-initial".to_owned(),
            operations: first_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "puddle-runtime-initial".to_owned(),
        command_lists: vec![first_list],
    })
    .unwrap();
    runtime.confirm_submission().unwrap();
    assert!(runtime.is_ready());
    assert_eq!(1, runtime.last_update().changed_texels);

    let mut unchanged_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot(descriptor, [mesh.clone()], &mut unchanged_ops)
        .unwrap();
    assert!(unchanged_ops.is_empty());
    assert!(runtime.is_ready());

    let mut moved = descriptor;
    moved.shadow_scene_from_world[12] = 1.0;
    let mut rejected_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot(moved, [mesh], &mut rejected_ops)
        .unwrap();
    assert!(runtime.has_pending_submission());
    runtime.discard_submission();
    assert!(runtime.is_ready());
    assert_eq!(descriptor, runtime.descriptor());
    runtime.destroy(&mut gal).unwrap();
}

#[test]
fn occupancy_runtime_revoxelizes_on_camera_cell_crossing_without_recreating_d3_resources() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let source = TerrainVoxelSourceMesh {
        mesh_key: 0x8b,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }]),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };
    let mut initial_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            descriptor.mapping,
            [source.clone()],
            &mut initial_ops,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-mapping-initial",
        initial_ops,
    );
    let texture = runtime.resources.texture;

    let fractional =
        VoxelLightVolumeMapping::complementary(descriptor.extent, [0, 0, 0], [0.25, 0.0, 0.5])
            .unwrap();
    let mut fractional_ops = Vec::new();
    let fractional_stats = runtime
        .append_terrain_source_snapshot_for_mapping(
            fractional,
            [source.clone()],
            &mut fractional_ops,
        )
        .unwrap();
    assert!(fractional_stats.updated_region.is_none());
    assert_eq!(0, fractional_stats.input_samples);
    assert_eq!(0, fractional_stats.emitted_samples);
    assert_eq!(0, fractional_stats.uploaded_bytes);
    assert!(fractional_ops.is_empty());
    assert_eq!(texture, runtime.resources.texture);
    assert!(runtime.is_initialized());

    let moved =
        VoxelLightVolumeMapping::complementary(descriptor.extent, [1, 0, 0], [0.0, 0.0, 0.0])
            .unwrap();
    let mut moved_ops = Vec::new();
    let moved_stats = runtime
        .append_terrain_source_snapshot_for_mapping(moved, [source], &mut moved_ops)
        .unwrap();
    // The confirmed field is shifted in place; every resident texel moves,
    // so the whole field is uploaded.
    assert_eq!(
        Some(VoxelLightVolumeRegion::whole(descriptor.extent)),
        moved_stats.updated_region
    );
    assert!(!moved_ops.is_empty());
    assert_eq!(texture, runtime.resources.texture);
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-mapping-cell-crossing",
        moved_ops,
    );
    assert!(runtime.is_initialized());
    assert_eq!([1, 0, 0], runtime.descriptor().mapping.camera_cell);
    let mut moved_descriptor = descriptor.clone();
    moved_descriptor.mapping = moved;
    let mut fresh = new_voxelizer(moved_descriptor);
    fresh
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    assert_eq!(
        fresh.pending_occupancy.as_ref().unwrap(),
        &runtime.voxelizer.occupancy
    );
}

#[test]
fn occupancy_patches_upload_disjoint_regions_in_one_submission() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let mesh = |key: u64, generation: u64, position: [f32; 3], material: i32| {
        TerrainVoxelSourceMesh {
            mesh_key: key,
            mesh_generation: generation,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position,
                mid_block_packed: 0,
                shader_material_id: material,
            }]),
            indices: Arc::new(vec![0, 0, 0]),
            translucent_indices: Arc::new(Vec::new()),
            transform: identity_transform(),
        }
    };
    let apply = |runtime: &mut TerrainOccupancyRuntime,
                 gal: &mut VulkanicGal,
                 meshes: Vec<TerrainVoxelSourceMesh>| {
        let mut ops = Vec::new();
        let stats = runtime
            .append_terrain_source_snapshot_for_mapping(descriptor.mapping, meshes, &mut ops)
            .unwrap();
        let copies = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::CopyBufferToTexture(_)))
            .count();
        submit_runtime_update(gal, runtime, "occupancy-disjoint-patches", ops);
        (stats, copies)
    };
    apply(
        &mut runtime,
        &mut gal,
        vec![
            mesh(1, 1, [-3.5, 0.5, -3.5], 30_008),
            mesh(2, 1, [2.5, 0.5, 2.5], 30_008),
        ],
    );
    // Both far-apart meshes change in one frame: two patches, one
    // submission (each copy needs its own transfer-write scope).
    let (stats, copies) = apply(
        &mut runtime,
        &mut gal,
        vec![
            mesh(1, 2, [-3.5, 0.5, -3.5], 30_012),
            mesh(2, 2, [2.5, 0.5, 2.5], 30_004),
        ],
    );
    assert_eq!(2, copies);
    assert_eq!(2, stats.changed_voxels);
    let mut fresh = new_voxelizer(descriptor.clone());
    fresh
        .update_from_samples([
            sample([-3.5, 0.5, -3.5], 30_012),
            sample([2.5, 0.5, 2.5], 30_004),
        ])
        .unwrap();
    assert_eq!(
        fresh.pending_occupancy.as_ref().unwrap(),
        &runtime.voxelizer.occupancy
    );
}

/// Incremental voxel updates (field shift + dirty-region recompute) must
/// equal a from-scratch voxelization of the same mesh set and mapping
/// after every camera move and mesh add/replace/remove.
#[test]
fn incremental_occupancy_matches_full_rebuild_across_moves_and_mesh_changes() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let mut state = 0x2545_f491_4f6c_dd1d_u64;
    let mut next = move |bound: u64| {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        state % bound
    };
    let materials_ids = [30_008, 30_012, 30_004, 32_000];
    let mut meshes: BTreeMap<u64, TerrainVoxelSourceMesh> = BTreeMap::new();
    let mut generation = 1;
    let mut make_mesh = |key: u64, next: &mut dyn FnMut(u64) -> u64| {
        generation += 1;
        // Meshes cluster in 4-block "sections" so neighbours and
        // overlapping writers (same cell, different material) occur.
        let origin = [
            next(6) as i32 * 4 - 12,
            next(3) as i32 * 2 - 3,
            next(6) as i32 * 4 - 12,
        ];
        let count = 1 + next(12) as usize;
        let vertices = (0..count)
            .map(|_| TerrainVoxelSourceVertex {
                position: [
                    (origin[0] + next(4) as i32) as f32 + 0.5,
                    (origin[1] + next(2) as i32) as f32 + 0.5,
                    (origin[2] + next(4) as i32) as f32 + 0.5,
                ],
                mid_block_packed: 0,
                shader_material_id: materials_ids[next(4) as usize],
            })
            .collect::<Vec<_>>();
        let indices = (0..count as u32)
            .flat_map(|index| [index, index, index])
            .collect::<Vec<_>>();
        TerrainVoxelSourceMesh {
            mesh_key: key,
            mesh_generation: generation,
            vertices: Arc::new(vertices),
            indices: Arc::new(indices),
            translucent_indices: Arc::new(Vec::new()),
            transform: identity_transform(),
        }
    };
    let mut camera = [0_i32; 3];
    for step in 0..160 {
        for _ in 0..1 + next(3) {
        match next(4) {
            0 => {
                let key = 1 + next(24);
                let mesh = make_mesh(key, &mut next);
                meshes.insert(key, mesh);
            }
            1 => {
                if let Some(key) = meshes.keys().nth(next(meshes.len().max(1) as u64) as usize).copied() {
                    meshes.remove(&key);
                }
            }
            2 => {
                // Same cells, new materials: the old and new bounds of one
                // mesh overlap, as do neighbouring dirty boxes.
                if let Some(key) = meshes.keys().nth(next(meshes.len().max(1) as u64) as usize).copied() {
                    let previous = meshes[&key].clone();
                    let vertices = previous
                        .vertices
                        .iter()
                        .map(|vertex| TerrainVoxelSourceVertex {
                            shader_material_id: materials_ids[next(4) as usize],
                            ..*vertex
                        })
                        .collect::<Vec<_>>();
                    meshes.insert(
                        key,
                        TerrainVoxelSourceMesh {
                            mesh_generation: 1_000_000 + step as u64,
                            vertices: Arc::new(vertices),
                            ..previous
                        },
                    );
                }
            }
            _ => {}
        }
        }
        if next(2) == 0 {
            let axis = next(3) as usize;
            let delta = [-1, 1, -3, 5, 9][next(5) as usize];
            // Wrap inside the populated region so moves keep exposing
            // cells that unchanged meshes occupy.
            let radius = if axis == 1 { 3 } else { 10 };
            camera[axis] =
                (camera[axis] + delta + radius).rem_euclid(2 * radius) - radius;
        }
        let mapping =
            VoxelLightVolumeMapping::complementary(descriptor.extent, camera, [0.5, 0.25, 0.75])
                .unwrap();
        let mut ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                mapping,
                meshes.values().cloned().collect::<Vec<_>>(),
                &mut ops,
            )
            .unwrap();
        if runtime.upload_pending {
            submit_runtime_update(&mut gal, &mut runtime, "incremental-equivalence", ops);
        }
        let mut expected_descriptor = descriptor.clone();
        expected_descriptor.mapping = mapping;
        let mut fresh = new_voxelizer(expected_descriptor);
        let samples = terrain_voxel_source_snapshot(meshes.values().cloned())
            .unwrap()
            .into_values()
            .flat_map(|mesh| mesh.samples.as_ref().clone())
            .collect::<Vec<_>>();
        fresh.update_from_samples(samples).unwrap();
        let expected = fresh
            .pending_occupancy
            .clone()
            .unwrap_or_else(|| vec![0; fresh.occupancy.len()]);
        assert_eq!(
            expected, runtime.voxelizer.occupancy,
            "step {step}: camera {camera:?}, {} meshes",
            meshes.len()
        );
        // The semantic cache mirrors the confirmed field (patches included).
        assert_eq!(
            Some(&runtime.voxelizer.occupancy),
            runtime.voxelizer.cache.field(VoxelLightVolumeKind::Occupancy),
            "step {step}: cache mirror diverged"
        );
    }
}

#[test]
fn rejected_mapping_submission_restores_the_previous_complete_volume() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let source = TerrainVoxelSourceMesh {
        mesh_key: 0x8c,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }]),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };
    let mut initial_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            descriptor.mapping,
            [source.clone()],
            &mut initial_ops,
        )
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-mapping-rollback-initial",
        initial_ops,
    );
    let previous_mapping = runtime.descriptor().mapping;
    let previous_field = runtime.voxelizer.occupancy.clone();
    assert!(previous_field.iter().any(|texel| *texel != 0));

    let moved =
        VoxelLightVolumeMapping::complementary(descriptor.extent, [1, 0, 0], [0.0, 0.0, 0.0])
            .unwrap();
    let mut moved_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(moved, [source.clone()], &mut moved_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "occupancy-runtime-mapping-rollback.list".to_owned(),
            operations: moved_ops,
        })
        .unwrap();
    gal.fail_next_submit_for_test();
    assert!(gal
        .submit(SubmissionBatch {
            label: "occupancy-runtime-mapping-rollback".to_owned(),
            command_lists: vec![list],
        })
        .is_err());
    runtime.discard_submission();
    assert_eq!(previous_mapping, runtime.descriptor().mapping);
    assert!(runtime.is_initialized());
    assert_eq!(1, runtime.mesh_snapshot_count());
    // The in-place shift never touched the confirmed field.
    assert_eq!(previous_field, runtime.voxelizer.occupancy);
    // And a retried move still produces the exact shifted field.
    let mut retry_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(moved, [source], &mut retry_ops)
        .unwrap();
    submit_runtime_update(&mut gal, &mut runtime, "occupancy-rollback-retry", retry_ops);
    let mut moved_descriptor = descriptor.clone();
    moved_descriptor.mapping = moved;
    let mut fresh = new_voxelizer(moved_descriptor);
    fresh
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    assert_eq!(
        fresh.pending_occupancy.as_ref().unwrap(),
        &runtime.voxelizer.occupancy
    );
}

#[test]
fn occupancy_mapping_rejects_invalid_input_without_changing_live_generation() {
    let descriptor = descriptor();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    let mut invalid = descriptor.mapping;
    invalid.camera_fraction[1] = 1.0;
    assert!(voxelizer.update_mapping(invalid).is_err());
    assert_eq!(descriptor.mapping, voxelizer.descriptor().mapping);
}

#[test]
fn occupancy_runtime_rejects_malformed_compact_triangle_indices() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
    let source = TerrainVoxelSourceMesh {
        mesh_key: 0x89,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }]),
        indices: Arc::new(vec![0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };
    let error = runtime
        .append_terrain_source_snapshot([source], &mut Vec::new())
        .unwrap_err();
    assert!(error.to_string().contains("triangle index stream"));
}

#[test]
fn occupancy_runtime_rejects_compact_topology_changes_without_a_generation_advance() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
    let source = TerrainVoxelSourceMesh {
        mesh_key: 0x8a,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }]),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };
    let mut operations = Vec::new();
    runtime
        .append_terrain_source_snapshot([source.clone()], &mut operations)
        .unwrap();
    submit_runtime_update(
        &mut gal,
        &mut runtime,
        "occupancy-runtime-compact-topology-initial",
        operations,
    );

    // Camera-relative placement is frame semantic state, not mesh
    // content. A transform-only update with the same generation must be
    // accepted and re-evaluated by the occupancy path.
    let mut moved = source.clone();
    moved.transform[12] = 3.0;
    runtime
        .append_terrain_source_snapshot([moved], &mut Vec::new())
        .unwrap();

    let mut changed = source;
    let mut changed_vertices = (*changed.vertices).clone();
    changed_vertices.push(TerrainVoxelSourceVertex {
        position: [1.0, 0.0, 0.0],
        mid_block_packed: 0,
        shader_material_id: 30_008,
    });
    changed.vertices = Arc::new(changed_vertices);
    changed.indices = Arc::new(vec![0, 1, 1]);
    let error = runtime
        .append_terrain_source_snapshot([changed], &mut Vec::new())
        .unwrap_err();
    assert!(error
        .to_string()
        .contains("changed semantic data without advancing generation"));
}

#[test]
fn flood_fill_resources_own_parity_and_retire_both_3d_fields() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let resources = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
    assert_eq!(&descriptor, resources.descriptor());
    assert_eq!(resources.even_view, resources.propagation_source_view(0));
    assert_eq!(resources.odd_view, resources.terrain_sample_view(0));
    assert_eq!(resources.odd_view, resources.propagation_source_view(1));
    assert_eq!(resources.even_view, resources.terrain_sample_view(1));
    let even_texture = resources.even_texture;
    let odd_texture = resources.odd_texture;
    let list = gal
        .create_command_list(CommandListDesc {
            label: "flood-fill-volume-use".to_owned(),
            operations: vec![
                CommandOp::Barrier(resource_barrier(
                    even_texture,
                    None,
                    TextureUsageState::Undefined,
                    TextureUsageState::ShaderWrite,
                )),
                CommandOp::Barrier(resource_barrier(
                    odd_texture,
                    None,
                    TextureUsageState::Undefined,
                    TextureUsageState::ShaderWrite,
                )),
            ],
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "flood-fill-volume-use".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    resources.destroy(&mut gal).unwrap();
    let retired = gal.retire_through_for_test(token.submission).unwrap();
    assert!(retired.contains(&even_texture));
    assert!(retired.contains(&odd_texture));
}

#[test]
fn flood_fill_emission_table_upload_is_generation_bound_and_reused() {
    let descriptor = descriptor();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut resources = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
    let mut operations = Vec::new();
    assert!(resources
        .append_emission_upload(&table, &mut operations)
        .unwrap());
    assert!(!resources.emission_ready());
    assert!(matches!(
        operations.as_slice(),
        [CommandOp::HostWriteBuffer { data, .. }, CommandOp::Barrier(_)] if data.len() == 4096
    ));
    resources.confirm_emission_submission().unwrap();
    assert!(resources.emission_ready());
    assert!(resources
        .append_tint_upload(&table, &mut operations)
        .unwrap());
    assert!(!resources.tint_ready());
    assert!(matches!(operations.last(), Some(CommandOp::Barrier(_))));
    assert!(operations.iter().any(|operation| {
        matches!(operation, CommandOp::HostWriteBuffer { data, .. } if data.len() == VOXEL_TINT_COUNT * 16)
    }));
    resources.confirm_tint_submission().unwrap();
    assert!(resources.tint_ready());
    assert!(!resources
        .append_tint_upload(&table, &mut operations)
        .unwrap());
    assert!(!resources
        .append_emission_upload(&table, &mut operations)
        .unwrap());
    let stale_source = bundled_complementary_hung_loified_source(2).unwrap();
    let stale_contract = derive_complementary_terrain_contract(&stale_source).unwrap();
    let stale = VoxelEmissionTable::derive(&stale_source, &stale_contract).unwrap();
    assert!(resources
        .append_emission_upload(&stale, &mut Vec::new())
        .is_err());
}

#[test]
fn colored_light_runtime_orders_occupancy_tables_and_ping_pong_in_one_submission() {
    let descriptor = descriptor();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainColoredLightRuntime::create(&mut gal, descriptor.clone(), materials(), emission)
            .unwrap();
    assert!(runtime.readiness().is_err());
    let mesh = TerrainVoxelSourceMesh {
        mesh_key: 0xc01,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }]),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };

    let submit = |gal: &mut VulkanicGal,
                  runtime: &mut TerrainColoredLightRuntime,
                  label: &str,
                  operations: Vec<CommandOp>| {
        assert!(runtime.has_pending_submission());
        let list = gal
            .create_command_list(CommandListDesc {
                label: format!("{label}.list"),
                operations,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: label.to_string(),
            command_lists: vec![list],
        })
        .unwrap();
        runtime.confirm_submission().unwrap();
    };

    let mut upload_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            0,
            descriptor.mapping,
            None,
            [mesh.clone()],
            &mut upload_ops,
        )
        .unwrap();
    assert!(upload_ops
        .iter()
        .any(|operation| matches!(operation, CommandOp::CopyBufferToTexture(_))));
    assert_eq!(
        1,
        upload_ops
            .iter()
            .filter(|operation| {
                matches!(operation, CommandOp::HostWriteBuffer { data, .. } if data.len() == VoxelLightVolumeShaderMapping::STD140_SIZE)
            })
            .count()
    );
    assert_eq!(
        2,
        upload_ops
            .iter()
            .filter(|operation| matches!(operation, CommandOp::Dispatch { .. }))
            .count()
    );
    assert!(runtime.pending_sampling_ready_for_frame(0));
    assert!(runtime
        .semantic_resource_set_for_pending_submission(0)
        .is_ok());
    submit(&mut gal, &mut runtime, "colored-light.upload", upload_ops);
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));
    assert!(runtime.readiness().unwrap().binding_for_frame(0).is_ok());

    let mut steady_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            0,
            descriptor.mapping,
            None,
            [mesh.clone()],
            &mut steady_ops,
        )
        .unwrap();
    assert_eq!(
        0,
        steady_ops
            .iter()
            .filter(|operation| matches!(operation, CommandOp::Dispatch { .. }))
            .count()
    );
    assert!(!runtime.has_pending_submission());
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));
    let ready = runtime.readiness().unwrap();
    assert_eq!(
        VoxelLightVolumeKind::FloodFillOdd,
        ready.binding_for_frame(0).unwrap().active_light_field
    );
    assert_eq!(
        VoxelLightVolumeKind::FloodFillEven,
        ready.binding_for_frame(1).unwrap().active_light_field
    );
    let even_binding = runtime.sampling_binding(0).unwrap();
    let odd_binding = runtime.sampling_binding(1).unwrap();
    assert_eq!(
        VoxelLightVolumeKind::FloodFillOdd,
        even_binding.active_light_field
    );
    assert_eq!(
        VoxelLightVolumeKind::FloodFillEven,
        odd_binding.active_light_field
    );
    assert_eq!(even_binding.resource_layout, odd_binding.resource_layout);
    assert_ne!(even_binding.resource_set, odd_binding.resource_set);
    assert_eq!(
        descriptor.resource_generation,
        even_binding.resource_generation
    );

    let even_resources = runtime.semantic_resource_set_for_frame(0).unwrap();
    let odd_resources = runtime.semantic_resource_set_for_frame(1).unwrap();
    assert!(even_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
        .is_some());
    assert!(even_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::ColoredVoxelLightCurrent)
        .is_some());
    assert!(even_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::ColoredVoxelLightPrevious)
        .is_some());
    assert_eq!(
        Some(runtime.occupancy.resources.view),
        even_resources.storage_texture_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
    );
    assert_eq!(
        Some(runtime.occupancy.resources.view),
        odd_resources.storage_texture_for(TerrainSourceResourceRole::ColoredVoxelOccupancy)
    );
    assert_ne!(
        even_resources
            .combined_sampler_for(TerrainSourceResourceRole::ColoredVoxelLightCurrent),
        odd_resources.combined_sampler_for(TerrainSourceResourceRole::ColoredVoxelLightCurrent),
    );
    assert_eq!(
        even_resources
            .combined_sampler_for(TerrainSourceResourceRole::ColoredVoxelLightCurrent),
        odd_resources
            .combined_sampler_for(TerrainSourceResourceRole::ColoredVoxelLightPrevious),
    );

    let even_texture = runtime.flood_fill.even_texture;
    let moved =
        VoxelLightVolumeMapping::complementary(descriptor.extent, [1, 0, 0], [0.25, 0.5, 0.75])
            .unwrap();
    let mut moved_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            2,
            moved,
            None,
            [mesh.clone()],
            &mut moved_ops,
        )
        .unwrap();
    assert_eq!(
        1,
        moved_ops
            .iter()
            .filter(|operation| {
                matches!(operation, CommandOp::HostWriteBuffer { data, .. } if data.len() == VoxelLightVolumeShaderMapping::STD140_SIZE)
            })
            .count()
    );
    assert_eq!(
        1,
        moved_ops
            .iter()
            .filter(|operation| matches!(operation, CommandOp::Dispatch { .. }))
            .count()
    );
    let temporal_bytes = moved_ops
        .iter()
        .find_map(|operation| match operation {
            CommandOp::HostWriteBuffer { buffer, data, .. }
                if *buffer == runtime.compute.temporal_mapping_buffer =>
            {
                Some(data)
            }
            _ => None,
        })
        .expect("same-submission propagation must upload its camera-cell mapping");
    assert_eq!(
        -1,
        i32::from_le_bytes(temporal_bytes[0..4].try_into().unwrap()),
        "previousCameraPosition - cameraPosition must reproject a +X camera move",
    );
    assert!(runtime.sampling_binding(0).is_err());
    let pending_resources = runtime
        .semantic_resource_set_for_pending_submission(2)
        .expect("the ordered propagation output may bind only inside its combined submission");
    assert!(pending_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::ColoredVoxelLightCurrent)
        .is_some());
    assert!(pending_resources
        .availability()
        .resource_for(TerrainSourceResourceRole::ColoredVoxelLightPrevious)
        .is_some());
    submit(
        &mut gal,
        &mut runtime,
        "colored-light.mapping-upload",
        moved_ops,
    );
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));

    let mut temporal_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            3,
            moved,
            None,
            [mesh.clone()],
            &mut temporal_ops,
        )
        .unwrap();
    assert!(!temporal_ops
        .iter()
        .any(|operation| matches!(operation, CommandOp::Dispatch { .. })));
    assert!(!runtime.has_pending_submission());
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));

    let previous_mapping = runtime.descriptor().mapping;
    let later =
        VoxelLightVolumeMapping::complementary(descriptor.extent, [2, 0, 0], [0.25, 0.5, 0.75])
            .unwrap();
    let mut rollback_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(4, later, None, [mesh], &mut rollback_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "colored-light.mapping-rollback.list".to_owned(),
            operations: rollback_ops,
        })
        .unwrap();
    gal.fail_next_submit_for_test();
    assert!(gal
        .submit(SubmissionBatch {
            label: "colored-light.mapping-rollback".to_owned(),
            command_lists: vec![list],
        })
        .is_err());
    runtime.discard_submission();
    assert_eq!(previous_mapping, runtime.descriptor().mapping);
    assert_eq!(even_texture, runtime.flood_fill.even_texture);
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));
    assert!(runtime.readiness().is_ok());
}

#[test]
fn empty_source_snapshot_seeds_a_cleared_field_like_iris() {
    let descriptor = descriptor();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainColoredLightRuntime::create(&mut gal, descriptor.clone(), materials(), emission)
            .unwrap();

    let mut first_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            0,
            descriptor.mapping,
            None,
            Vec::new(),
            &mut first_ops,
        )
        .unwrap();
    // Iris creates the voxel image cleared, so the first frame seeds the
    // flood-fill from an empty occupancy field instead of waiting for
    // terrain (e.g. right after a teleport, before chunks load).
    assert!(first_ops
        .iter()
        .any(|operation| matches!(operation, CommandOp::Dispatch { .. })));
    let list = gal
        .create_command_list(CommandListDesc {
            label: "colored-light.empty-source.prepare".to_owned(),
            operations: first_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "colored-light.empty-source.prepare".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    runtime.confirm_submission().unwrap();

    let mut steady_ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(
            1,
            descriptor.mapping,
            None,
            Vec::new(),
            &mut steady_ops,
        )
        .unwrap();
    assert!(steady_ops
        .iter()
        .all(|operation| !matches!(operation, CommandOp::Dispatch { .. })));
    assert!(!runtime.has_pending_submission());
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));
}

#[test]
fn flood_fill_compute_lifecycle_requires_confirmed_inputs_and_ping_pongs() {
    let descriptor = descriptor();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
    let mut flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
    let mut compute =
        TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();

    assert!(compute
        .append_initialization(&occupancy, &flood_fill, 0, &mut Vec::new())
        .is_err());

    let mut occupancy_ops = Vec::new();
    occupancy
        .append_upload(voxelizer.pending_upload().unwrap(), &mut occupancy_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "occupancy-ready".to_owned(),
            operations: occupancy_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "occupancy-ready".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    occupancy.confirm_submission().unwrap();
    voxelizer.confirm_pending_upload().unwrap();

    let mut emission_ops = Vec::new();
    flood_fill
        .append_emission_upload(&table, &mut emission_ops)
        .unwrap();
    flood_fill
        .append_tint_upload(&table, &mut emission_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "emission-ready".to_owned(),
            operations: emission_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "emission-ready".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    flood_fill.confirm_emission_submission().unwrap();
    flood_fill.confirm_tint_submission().unwrap();

    let mut seed_ops = Vec::new();
    compute
        .append_initialization(&occupancy, &flood_fill, 0, &mut seed_ops)
        .unwrap();
    assert!(matches!(seed_ops.first(), Some(CommandOp::Barrier(_))));
    assert!(seed_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::Dispatch {
            groups_x: 1,
            groups_y: 1,
            groups_z: 1
        }
    )));
    assert!(seed_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::BindResourceSet { set, .. } if *set == compute.odd_init_set
    )));
    assert!(seed_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::BindResourceSet { set, .. } if *set == compute.even_init_set
    )));
    assert!(compute
        .append_propagation(&occupancy, &flood_fill, 0, None, &mut Vec::new())
        .is_err());
    let list = gal
        .create_command_list(CommandListDesc {
            label: "flood-fill-seed".to_owned(),
            operations: seed_ops,
        })
        .unwrap();
    let _token = gal
        .submit(SubmissionBatch {
            label: "flood-fill-seed".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    compute.confirm_initialization().unwrap();
    assert!(compute.even_initialized);
    assert!(compute.odd_initialized);
    assert!(compute.is_initialized_for_frame(0));
    assert!(compute.is_initialized_for_frame(1));

    let mut propagation_ops = Vec::new();
    compute
        .append_propagation(&occupancy, &flood_fill, 1, None, &mut propagation_ops)
        .unwrap();
    assert!(propagation_ops
        .iter()
        .any(|operation| matches!(operation, CommandOp::BindComputePipeline(_))));
    assert!(propagation_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::BindResourceSet { set, .. } if *set == compute.odd_to_even_set
    )));
    assert!(propagation_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::HostWriteBuffer { buffer, offset, data }
            if *buffer == compute.temporal_mapping_buffer
                && *offset == 0
                && data.len() == VoxelLightVolumeTemporalMapping::STD140_SIZE
    )));
    assert!(propagation_ops.iter().any(|operation| matches!(
        operation,
        CommandOp::Barrier(ResourceBarrier {
            resource,
            subresources: None,
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::ShaderRead,
            ..
        }) if *resource == compute.temporal_mapping_buffer
    )));
    let list = gal
        .create_command_list(CommandListDesc {
            label: "flood-fill-propagate".to_owned(),
            operations: propagation_ops,
        })
        .unwrap();
    let token = gal
        .submit(SubmissionBatch {
            label: "flood-fill-propagate".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    compute.confirm_propagation().unwrap();
    assert!(compute.even_initialized);
    assert!(compute.odd_initialized);

    let odd_texture = flood_fill.odd_texture;
    compute.destroy(&mut gal).unwrap();
    flood_fill.destroy(&mut gal).unwrap();
    occupancy.destroy(&mut gal).unwrap();
    let retired = gal.retire_through_for_test(token.submission).unwrap();
    assert!(retired.contains(&odd_texture));
}

#[test]
fn flood_fill_behind_view_policy_uploads_the_source_derived_camera_direction() {
    let mut descriptor = descriptor();
    descriptor.requirements.update_policy.preserve_behind_view = true;
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
    let mut flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    let mut occupancy_ops = Vec::new();
    occupancy
        .append_upload(voxelizer.pending_upload().unwrap(), &mut occupancy_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "behind-view.occupancy".to_owned(),
            operations: occupancy_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "behind-view.occupancy".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    occupancy.confirm_submission().unwrap();
    voxelizer.confirm_pending_upload().unwrap();
    let mut material_ops = Vec::new();
    flood_fill
        .append_emission_upload(&emission, &mut material_ops)
        .unwrap();
    flood_fill
        .append_tint_upload(&emission, &mut material_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "behind-view.materials".to_owned(),
            operations: material_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "behind-view.materials".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    flood_fill.confirm_emission_submission().unwrap();
    flood_fill.confirm_tint_submission().unwrap();
    let mut compute =
        TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();
    let mut seed_ops = Vec::new();
    compute
        .append_initialization(&occupancy, &flood_fill, 0, &mut seed_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "behind-view.seed".to_owned(),
            operations: seed_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "behind-view.seed".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    compute.confirm_initialization().unwrap();

    let mut propagation_ops = Vec::new();
    compute
        .append_propagation(
            &occupancy,
            &flood_fill,
            1,
            Some(VoxelLightVolumeViewDirection {
                normalized_camera_forward: [1.0, 0.0, 0.0],
            }),
            &mut propagation_ops,
        )
        .unwrap();
    let bytes = propagation_ops
        .iter()
        .find_map(|operation| match operation {
            CommandOp::HostWriteBuffer { buffer, data, .. }
                if *buffer == compute.temporal_mapping_buffer =>
            {
                Some(data)
            }
            _ => None,
        })
        .expect("behind-view propagation must upload a temporal mapping");
    assert_eq!(1.0f32.to_le_bytes(), bytes[48..52]);
    assert!(FLOOD_FILL_PROPAGATE_SHADER.contains("preserveBehindView"));
    assert!(FLOOD_FILL_PROPAGATE_SHADER.contains("CameraForward"));
}

#[test]
fn flood_fill_compute_kernels_create_on_opengl_when_the_real_d3_path_is_available() {
    let backend = match crate::render::vulkanic::test_support::opengl_gal("MattMC private voxel flood-fill OpenGL") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                "unexpected OpenGL voxel compute setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    let descriptor = descriptor();
    let occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
    let flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
    let compute =
        TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();
    compute.destroy(&mut gal).unwrap();
    flood_fill.destroy(&mut gal).unwrap();
    occupancy.destroy(&mut gal).unwrap();
}

#[test]
fn flood_fill_compute_kernels_create_on_vulkan_when_available() {
    let backend = match crate::render::vulkanic::test_support::vulkan_gal("MattMC private voxel flood-fill Vulkan") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("Vulkan")
                    || text.contains("vulkan")
                    || text.contains("physical device"),
                "unexpected Vulkan voxel compute setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    let descriptor = descriptor();
    let occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
    let flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
    let compute =
        TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();
    compute.destroy(&mut gal).unwrap();
    flood_fill.destroy(&mut gal).unwrap();
    occupancy.destroy(&mut gal).unwrap();
}

fn run_private_flood_fill_compute_dispatch(gal: &mut VulkanicGal) {
    let descriptor = descriptor();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut voxelizer = new_voxelizer(descriptor.clone());
    voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    let mut occupancy = TerrainOccupancyGpuResources::create(gal, &descriptor).unwrap();
    let mut flood_fill = TerrainFloodFillGpuResources::create(gal, &descriptor).unwrap();
    let mut compute =
        TerrainFloodFillComputeResources::create(gal, &occupancy, &flood_fill).unwrap();

    let mut upload_ops = Vec::new();
    occupancy
        .append_upload(voxelizer.pending_upload().unwrap(), &mut upload_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "private-flood-fill.occupancy".to_owned(),
            operations: upload_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "private-flood-fill.occupancy".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    occupancy.confirm_submission().unwrap();
    voxelizer.confirm_pending_upload().unwrap();

    let mut emission_ops = Vec::new();
    flood_fill
        .append_emission_upload(&table, &mut emission_ops)
        .unwrap();
    flood_fill
        .append_tint_upload(&table, &mut emission_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "private-flood-fill.emission".to_owned(),
            operations: emission_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "private-flood-fill.emission".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    flood_fill.confirm_emission_submission().unwrap();
    flood_fill.confirm_tint_submission().unwrap();

    let mut seed_ops = Vec::new();
    compute
        .append_initialization(&occupancy, &flood_fill, 0, &mut seed_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "private-flood-fill.seed".to_owned(),
            operations: seed_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "private-flood-fill.seed".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    compute.confirm_initialization().unwrap();

    let mut propagation_ops = Vec::new();
    compute
        .append_propagation(&occupancy, &flood_fill, 1, None, &mut propagation_ops)
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "private-flood-fill.propagate".to_owned(),
            operations: propagation_ops,
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "private-flood-fill.propagate".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();
    compute.confirm_propagation().unwrap();

    compute.destroy(gal).unwrap();
    flood_fill.destroy(gal).unwrap();
    occupancy.destroy(gal).unwrap();
}

/// Exercises the exact combined transaction used by the private world
/// frontend. The independent dispatch tests below prove the kernels; this
/// closes the gap where an occupancy upload, source tables, and ping-pong
/// dispatches might each work alone but fail when recorded across real
/// backend submissions.
fn run_private_colored_light_transaction(gal: &mut VulkanicGal) {
    let descriptor = descriptor();
    let source = bundled_complementary_hung_loified_source(1).unwrap();
    let contract = derive_complementary_terrain_contract(&source).unwrap();
    let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
    let mut runtime =
        TerrainColoredLightRuntime::create(gal, descriptor.clone(), materials(), emission)
            .unwrap();
    let mesh = TerrainVoxelSourceMesh {
        mesh_key: 0xc01,
        mesh_generation: 1,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }]),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };

    let mut submitted_transactions = 0_u32;
    for (frame_counter, label) in [
        (0_u64, "private-colored-light.upload"),
        (1_u64, "private-colored-light.seed"),
        (0_u64, "private-colored-light.stable"),
    ] {
        let mut operations = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                frame_counter,
                descriptor.mapping,
                None,
                [mesh.clone()],
                &mut operations,
            )
            .unwrap();
        if operations.is_empty() {
            assert!(
                !runtime.has_pending_submission(),
                "an unchanged voxel field must not retain a pending submission"
            );
            continue;
        }
        assert!(runtime.has_pending_submission());
        let list = gal
            .create_command_list(CommandListDesc {
                label: format!("{label}.list"),
                operations,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: label.to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        runtime.confirm_submission().unwrap();
        submitted_transactions += 1;
    }
    assert!(
        submitted_transactions >= 1,
        "the initial semantic occupancy snapshot must stage real GPU work"
    );
    assert!(runtime.is_ready_for_frame(0));
    assert!(runtime.is_ready_for_frame(1));
    runtime.destroy(gal).unwrap();
}

#[test]
fn flood_fill_compute_dispatches_on_opengl_when_the_real_d3_path_is_available() {
    let backend = match crate::render::vulkanic::test_support::opengl_gal("MattMC private voxel flood-fill OpenGL dispatch") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                "unexpected OpenGL voxel dispatch setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    run_private_flood_fill_compute_dispatch(&mut gal);
}

#[test]
fn colored_light_transaction_dispatches_on_opengl_when_the_real_d3_path_is_available() {
    let backend = match crate::render::vulkanic::test_support::opengl_gal("MattMC private colored-light OpenGL transaction") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                "unexpected OpenGL colored-light setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    run_private_colored_light_transaction(&mut gal);
}

#[test]
fn flood_fill_compute_dispatches_on_vulkan_when_available() {
    let backend = match crate::render::vulkanic::test_support::vulkan_gal("MattMC private voxel flood-fill Vulkan dispatch") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("Vulkan")
                    || text.contains("vulkan")
                    || text.contains("physical device"),
                "unexpected Vulkan voxel dispatch setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    run_private_flood_fill_compute_dispatch(&mut gal);
}

#[test]
fn colored_light_transaction_dispatches_on_vulkan_when_available() {
    let backend = match crate::render::vulkanic::test_support::vulkan_gal("MattMC private colored-light Vulkan transaction") {
        Ok(backend) => backend,
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("Vulkan")
                    || text.contains("vulkan")
                    || text.contains("physical device"),
                "unexpected Vulkan colored-light setup failure: {text}"
            );
            return;
        }
    };
    let mut gal = backend;
    run_private_colored_light_transaction(&mut gal);
}

#[test]
fn rejected_upload_rolls_back_and_rejects_overlapping_candidates() {
    let mut voxelizer = new_voxelizer(descriptor());
    voxelizer
        .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
        .unwrap();
    assert!(voxelizer
        .update_from_samples([sample([1.0, 0.0, 0.0], 30_008)])
        .is_err());
    voxelizer.discard_pending_upload();
    assert!(voxelizer.cache().binding_for_frame(0).is_err());

    voxelizer
        .update_from_samples([sample([1.0, 0.0, 0.0], 30_012)])
        .unwrap();
    voxelizer.confirm_pending_upload().unwrap();
    assert!(voxelizer.cache().binding_for_frame(0).is_err());
    // Occupancy alone is intentionally insufficient for selected-source
    // admission until both flood-fill fields exist.
}

#[test]
fn rejects_descriptor_reload_that_would_reuse_a_stale_material_map() {
    let mut voxelizer = new_voxelizer(descriptor());
    let mut next = descriptor();
    next.shader_pack_generation += 1;
    next.resource_generation += 1;
    assert!(voxelizer.replace_descriptor(next).is_err());
    assert_eq!(1, voxelizer.descriptor().shader_pack_generation);
}

#[test]
fn occupancy_runtime_trusts_a_shared_source_list_only_while_its_meshes_are_confirmed() {
    let descriptor = descriptor();
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let mut runtime =
        TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
    let mesh = |generation, material| TerrainVoxelSourceMesh {
        mesh_key: 0x8c,
        mesh_generation: generation,
        vertices: Arc::new(vec![TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: material,
        }]),
        indices: Arc::new(vec![0, 0, 0]),
        translucent_indices: Arc::new(Vec::new()),
        transform: identity_transform(),
    };
    let first: Arc<[TerrainVoxelSourceMesh]> = vec![mesh(1, 30_008)].into();
    let mut ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(descriptor.mapping, Arc::clone(&first), &mut ops)
        .unwrap();
    submit_runtime_update(&mut gal, &mut runtime, "shared-list-first", ops);
    // The identical shared list is recognized as unchanged.
    let mut ops = Vec::new();
    let unchanged = runtime
        .append_terrain_source_snapshot_for_mapping(descriptor.mapping, Arc::clone(&first), &mut ops)
        .unwrap();
    assert!(unchanged.updated_region.is_none() && ops.is_empty());
    // A new generation replaces the confirmed meshes...
    let mut ops = Vec::new();
    runtime
        .append_terrain_source_snapshot_for_mapping(descriptor.mapping, vec![mesh(2, 30_012)], &mut ops)
        .unwrap();
    submit_runtime_update(&mut gal, &mut runtime, "shared-list-second", ops);
    // ...so the earlier list is a real change again, not a remembered match.
    let mut ops = Vec::new();
    let reverted = runtime
        .append_terrain_source_snapshot_for_mapping(descriptor.mapping, Arc::clone(&first), &mut ops)
        .unwrap();
    assert!(reverted.updated_region.is_some());
    assert!(!ops.is_empty());
    runtime.discard_submission();
    runtime.destroy(&mut gal).unwrap();
}

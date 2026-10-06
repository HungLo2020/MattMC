use super::*;
use crate::render::shaderpack::properties::shadow::{
    AdvancedShadowCasterFrustum, ShadowCasterFrameDistances, ShadowCasterKind,
};

fn caster(key: u64, origin: [f32; 3]) -> WorldMeshInstanceRequest {
    let mut instance = mesh_instance(key, 1);
    instance.stratum = WORLD_STRATUM_TERRAIN;
    instance.mesh_section_index = WORLD_MESH_SECTION_ALL;
    instance.flags = WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY;
    instance.transform[12..15].copy_from_slice(&origin);
    instance
}

fn install_policy(frontend: &mut WorldPrimitiveFrontend, advanced: bool) {
    frontend
        .apply_shader_pack_source_update(ShaderPackSourceUpdate {
            pack_name: "early-shadow-selection".into(),
            generation: if advanced { 42 } else { 41 },
            files: vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    concat!(
                        "const float shadowDistance = 80.0;\n",
                        "const float shadowDistanceRenderMul = 1.0;\n",
                        "const float sunPathRotation = -25.0;\n",
                    ),
                ),
                ShaderSourceFile::new("shaders.properties", format!("shadow.culling={advanced}\n")),
            ],
        })
        .unwrap();
}

fn selected_plan(
    frontend: &mut WorldPrimitiveFrontend,
    frame: &WorldPrimitiveFrame,
    indices: &[usize],
) -> Arc<Vec<MeshBatch>> {
    let identities = indices
        .iter()
        .map(|&index| mesh_batch_instance_key(&frame.mesh_instances[index]))
        .collect::<Vec<_>>();
    frontend
        .cached_mesh_batch_plan_selected(
            frame,
            ColorFormat::Rgba8Unorm,
            RasterYDirection::Up,
            true,
            MeshBatchSelection::ShadowOnly,
            &identities,
            true,
            Some(indices),
        )
        .unwrap()
}

fn assert_batches_equal(a: &[MeshBatch], b: &[MeshBatch]) {
    assert_eq!(a.len(), b.len());
    for (a, b) in a.iter().zip(b) {
        assert_eq!(a.key, b.key);
        assert_eq!(a.indices, b.indices);
        assert_eq!(a.model_submission_order, b.model_submission_order);
        assert_eq!(a.index_offset, b.index_offset);
        assert_eq!(a.index_count, b.index_count);
        assert_eq!(a.sorted_index_offset, b.sorted_index_offset);
    }
}

#[test]
fn shadow_batch_selection_unique_sparse_faces_preserves_reference_ranges_and_order() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let assets = (1..=12).map(|key| {
        let mut asset = mesh_asset(key, 1, IndexType::U16);
        let indices = asset.index_bytes.clone();
        let section = asset.sections[0].clone();
        asset.index_bytes.clear();
        asset.sections.clear();
        for facing in 0..7 {
            let mut section = section.clone();
            section.source_facing = facing;
            section.index_offset = asset.index_bytes.len() as u32;
            section.material_mode = match key % 3 {
                0 => WORLD_MATERIAL_MODE_TRANSLUCENT,
                1 => WORLD_MATERIAL_MODE_CUTOUT,
                _ => WORLD_MATERIAL_MODE_OPAQUE,
            };
            section.material_id = match key % 3 {
                0 => WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED,
                1 => WORLD_MATERIAL_ID_CUTOUT_TEXTURED,
                _ => WORLD_MATERIAL_ID_OPAQUE_TEXTURED,
            };
            asset.sections.push(section);
            asset.index_bytes.extend_from_slice(&indices);
        }
        asset
    }).collect();
    frontend.apply_world_mesh_asset_update(&mut gal, 1, assets, Vec::new()).unwrap();
    let mut scene = frame(Vec::new());
    scene.mesh_instances = (1..=12).map(|key| {
        let mut instance = caster(key, [key as f32, 0.0, 0.0]);
        instance.terrain_visible_facing_mask = (1 << (key % 6)) | (1 << 6);
        instance
    }).collect();
    let selected = [0, 3, 5, 7, 9, 11];
    for selection in [MeshBatchSelection::ShadowOnly, MeshBatchSelection::ShadowSupplement] {
        if selection == MeshBatchSelection::ShadowSupplement {
            for instance in &mut scene.mesh_instances {
                instance.flags = 0;
            }
        }
        let mut reference = mesh_batches_filtered(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true, selection, true).unwrap();
        for batch in &mut reference {
            batch.indices.retain(|index| selected.contains(index));
        }
        reference.retain(|batch| !batch.indices.is_empty());
        let actual = mesh_batches_filtered_indices(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true, selection, true, &selected).unwrap();
        assert_batches_equal(&reference, &actual);
        assert!(!actual.is_empty());

        // Explicit section selection exercises the other unique-range path.
        for instance in &mut scene.mesh_instances {
            instance.mesh_section_index = 3;
        }
        let mut reference = mesh_batches_filtered(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true, selection, true).unwrap();
        reference.retain(|batch| selected.contains(&batch.indices[0]));
        let actual = mesh_batches_filtered_indices(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true, selection, true, &selected).unwrap();
        assert_batches_equal(&reference, &actual);
        for instance in &mut scene.mesh_instances {
            instance.mesh_section_index = WORLD_MESH_SECTION_ALL;
        }
    }
    frontend.reset(&mut gal);
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn shadow_batch_selection_matches_late_frustum_culling_and_draw_order() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let mut assets = (1..=27)
        .map(|key| mesh_asset(key, 1, IndexType::U16))
        .collect::<Vec<_>>();
    for asset in &mut assets {
        if asset.mesh_key % 3 == 0 {
            asset.sections[0].material_mode = WORLD_MATERIAL_MODE_TRANSLUCENT;
            asset.sections[0].material_id = WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
        } else if asset.mesh_key % 3 == 1 {
            asset.sections[0].material_mode = WORLD_MATERIAL_MODE_CUTOUT;
            asset.sections[0].material_id = WORLD_MATERIAL_ID_CUTOUT_TEXTURED;
        }
    }
    frontend
        .apply_world_mesh_asset_update(&mut gal, 1, assets, Vec::new())
        .unwrap();
    let mut scene = frame(Vec::new());
    for x in [-160.0, -8.0, 144.0] {
        for y in [-144.0, -8.0, 160.0] {
            for z in [-160.0, -8.0, 144.0] {
                scene
                    .mesh_instances
                    .push(caster(scene.mesh_instances.len() as u64 + 1, [x, y, z]));
            }
        }
    }
    // Ordinary and outline-only records must keep their positions without
    // entering the shadow terrain pass.
    scene.mesh_instances.insert(0, mesh_instance(1, 1));
    let mut outline = caster(1, [-8.0; 3]);
    outline.flags |= WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY;
    scene.mesh_instances.push(outline);
    for advanced in [false, true] {
        install_policy(&mut frontend, advanced);
        let generation = if advanced { 42 } else { 41 };
        for distance in [32.0, 80.0, 160.0] {
            for time in [0.0, 0.25, 0.5, 0.75] {
                scene.shader_environment.far_plane = distance;
                scene.shader_environment.time_of_day = time;
                scene.shader_environment.configured_shadow_distance_chunks = 5;
                let selected = frontend
                    .source_shadow_terrain_instance_indices(&scene, generation, &Default::default())
                    .unwrap();
                let policy = frontend
                    .shader_pack_sources
                    .active_shadow_policy_for_scope(TerrainProgramScope::Overworld)
                    .unwrap()
                    .unwrap();
                let frustum = AdvancedShadowCasterFrustum::from_frame_with_distances(
                    policy,
                    time,
                    scene.projection_matrix,
                    scene.view_matrix,
                    ShadowCasterFrameDistances {
                        render_distance_blocks: distance,
                        configured_shadow_distance_chunks: 5,
                    },
                    ShadowCasterKind::Terrain,
                )
                .unwrap();
                let mut late = mesh_batches_filtered(
                    &scene,
                    &frontend,
                    ColorFormat::Rgba8Unorm,
                    RasterYDirection::Up,
                    true,
                    MeshBatchSelection::ShadowOnly,
                    true,
                )
                .unwrap();
                for batch in &mut late {
                    batch.indices.retain(|index| {
                        source_shadow_instance_intersects(
                            &frustum,
                            &scene.mesh_instances[*index],
                            Some(distance),
                        )
                    });
                }
                late.retain(|batch| !batch.indices.is_empty());
                let early = selected_plan(&mut frontend, &scene, &selected);
                assert_batches_equal(&early, &late);
                assert!(!early.is_empty());
                assert!(selected.len() < 27);
            }
        }
    }
    for dimension in [WORLD_BACKGROUND_SKY_NETHER, WORLD_BACKGROUND_SKY_END] {
        scene.background.sky_type = dimension;
        assert!(frontend
            .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
            .unwrap()
            .is_empty());
    }
}

#[test]
fn shadow_batch_selection_preserves_repeated_mesh_grouping_before_culling() {
    for material_mode in [WORLD_MATERIAL_MODE_OPAQUE, WORLD_MATERIAL_MODE_TRANSLUCENT] {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut assets = vec![
            mesh_asset(1, 1, IndexType::U16),
            mesh_asset(2, 1, IndexType::U16),
        ];
        for asset in &mut assets {
            asset.sections[0].material_mode = material_mode;
            if material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT {
                asset.sections[0].material_id = WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
            }
        }
        frontend
            .apply_world_mesh_asset_update(&mut gal, 1, assets, Vec::new())
            .unwrap();
        let mut scene = frame(Vec::new());
        scene.mesh_instances = vec![
            caster(1, [200.0; 3]),
            caster(2, [-8.0; 3]),
            caster(1, [-8.0; 3]),
        ];
        for selected in [vec![1, 2], vec![0, 2]] {
            let mut late = mesh_batches_filtered(
                &scene,
                &frontend,
                ColorFormat::Rgba8Unorm,
                RasterYDirection::Up,
                true,
                MeshBatchSelection::ShadowOnly,
                true,
            )
            .unwrap();
            for batch in &mut late {
                batch.indices.retain(|index| selected.contains(index));
            }
            late.retain(|batch| !batch.indices.is_empty());
            assert_batches_equal(&selected_plan(&mut frontend, &scene, &selected), &late);
        }
    }
}

#[test]
fn shadow_batch_selection_rebuilds_when_culled_instances_change_shared_mesh_order() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            (1..=3)
                .map(|key| mesh_asset(key, 1, IndexType::U16))
                .collect(),
            Vec::new(),
        )
        .unwrap();
    let mut scene = frame(Vec::new());
    scene.mesh_instances = vec![
        caster(1, [200.0; 3]),
        caster(2, [-8.0; 3]),
        caster(1, [-8.0; 3]),
    ];
    let first = selected_plan(&mut frontend, &scene, &[1, 2]);
    assert_eq!(
        first
            .iter()
            .map(|batch| batch.key.mesh_key)
            .collect::<Vec<_>>(),
        [1, 2]
    );
    assert_eq!(frontend.mesh_batch_plan_cache.len(), 1);
    // The selected identities and original positions remain equal. Changing
    // the earlier culled record still changes the full plan's first-seen order.
    scene.mesh_instances[0].mesh_key = 3;
    let second = selected_plan(&mut frontend, &scene, &[1, 2]);
    assert_eq!(
        second
            .iter()
            .map(|batch| batch.key.mesh_key)
            .collect::<Vec<_>>(),
        [2, 1]
    );
    assert!(!Arc::ptr_eq(&first, &second));
}

#[test]
fn shadow_batch_selection_reuses_full_repeated_plan_without_losing_culled_order() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.apply_world_mesh_asset_update(&mut gal, 1,
        vec![mesh_asset(1, 1, IndexType::U16), mesh_asset(2, 1, IndexType::U16)],
        Vec::new()).unwrap();
    let mut scene = frame(Vec::new());
    scene.mesh_instances = vec![
        caster(1, [200.0; 3]), caster(2, [-8.0; 3]), caster(1, [-8.0; 3]),
    ];
    selected_plan(&mut frontend, &scene, &[1, 2]);
    assert_eq!(frontend.mesh_batch_plan_cache.len(), 1);
    let full = Arc::clone(&frontend.mesh_batch_plan_cache[0].batches);
    assert_eq!(full[0].indices.as_slice(), &[0, 2]);
    for selected in [vec![0, 2], vec![1, 2], vec![2], vec![]] {
        // A transform change changes frustum admission, not batch topology.
        scene.mesh_instances[0].transform[12] += 1.0;
        let actual = selected_plan(&mut frontend, &scene, &selected);
        let mut reference = mesh_batches_filtered(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true,
            MeshBatchSelection::ShadowOnly, true).unwrap();
        for batch in &mut reference { batch.indices.retain(|index| selected.contains(index)); }
        reference.retain(|batch| !batch.indices.is_empty());
        assert_batches_equal(&actual, &reference);
        assert!(Arc::ptr_eq(&full, &frontend.mesh_batch_plan_cache[0].batches));
        assert_eq!(full[0].indices.as_slice(), &[0, 2]); // Filtering never mutates the cached plan.
    }
    // The full key must still validate an invalid culled generation.
    scene.mesh_instances[0].mesh_generation = 999;
    let identities = [mesh_batch_instance_key(&scene.mesh_instances[2])];
    assert!(frontend.cached_mesh_batch_plan_selected(&scene,
        ColorFormat::Rgba8Unorm, RasterYDirection::Up, true,
        MeshBatchSelection::ShadowOnly, &identities, true, Some(&[2])).is_err());
    scene.mesh_instances[0].mesh_generation = 1;
    frontend.apply_world_mesh_asset_update(&mut gal, 2,
        vec![mesh_asset(2, 2, IndexType::U16)], Vec::new()).unwrap();
    // The stale full plan is invalidated (an empty selection's plan names no mesh).
    assert!(!frontend.mesh_batch_plan_cache.iter().any(|entry| Arc::ptr_eq(&entry.batches, &full)));
    scene.mesh_instances[1].mesh_generation = 2;
    selected_plan(&mut frontend, &scene, &[2]);
    assert!(!Arc::ptr_eq(&full, &frontend.mesh_batch_plan_cache[0].batches));
    frontend.reset(&mut gal);
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn shadow_batch_selection_cache_keys_original_positions_and_ignores_unselected_churn() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            vec![mesh_asset(1, 1, IndexType::U16)],
            Vec::new(),
        )
        .unwrap();
    let mut scene = frame(Vec::new());
    scene.mesh_instances = vec![
        mesh_instance(1, 1),
        caster(1, [-8.0; 3]),
        mesh_instance(1, 1),
    ];
    let first = selected_plan(&mut frontend, &scene, &[1]);
    scene.mesh_instances[0].mesh_key = 999; // Unselected dynamic model, no asset needed.
    let same = selected_plan(&mut frontend, &scene, &[1]);
    assert!(Arc::ptr_eq(&first, &same));
    scene.mesh_instances[2] = scene.mesh_instances[1].clone();
    scene.mesh_instances[1] = mesh_instance(1, 1);
    let shifted = selected_plan(&mut frontend, &scene, &[2]);
    assert!(!Arc::ptr_eq(&first, &shifted));
    assert_eq!(first[0].indices.as_slice(), &[1]);
    assert_eq!(shifted[0].indices.as_slice(), &[2]);
    scene.mesh_instances[1] = scene.mesh_instances[2].clone();
    scene.mesh_instances[2] = mesh_instance(1, 1);
    // Included asset replacement invalidates the selected cache even with
    // unchanged positions; an unrelated model update does not.
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            2,
            vec![mesh_asset(999, 1, IndexType::U16)],
            Vec::new(),
        )
        .unwrap();
    assert!(Arc::ptr_eq(
        &first,
        &selected_plan(&mut frontend, &scene, &[1])
    ));
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            3,
            vec![mesh_asset(1, 2, IndexType::U16)],
            Vec::new(),
        )
        .unwrap();
    assert!(frontend.mesh_batch_plan_cache.is_empty());
    scene.mesh_instances[1].mesh_generation = 2;

    assert!(!Arc::ptr_eq(
        &first,
        &selected_plan(&mut frontend, &scene, &[1])
    ));
}

#[test]
fn shadow_batch_selection_rejects_invalid_positions_and_bounds_cache() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            vec![mesh_asset(1, 1, IndexType::U16)],
            Vec::new(),
        )
        .unwrap();
    let mut scene = frame(Vec::new());
    scene.mesh_instances = (0..6).map(|_| mesh_instance(1, 1)).collect();
    scene.mesh_instances[0] = caster(1, [-8.0; 3]);
    for indices in [vec![6], vec![1, 0], vec![0, 0]] {
        assert!(mesh_batches_filtered_indices(
            &scene,
            &frontend,
            ColorFormat::Rgba8Unorm,
            RasterYDirection::Up,
            true,
            MeshBatchSelection::ShadowOnly,
            true,
            &indices,
        )
        .is_err());
    }
    let first = selected_plan(&mut frontend, &scene, &[0]);
    for index in 1..6 {
        scene.mesh_instances[index - 1] = mesh_instance(1, 1);
        scene.mesh_instances[index] = caster(1, [-8.0; 3]);
        selected_plan(&mut frontend, &scene, &[index]);
    }
    assert_eq!(4, frontend.mesh_batch_plan_cache.len());
    scene.mesh_instances[5] = mesh_instance(1, 1);
    scene.mesh_instances[0] = caster(1, [-8.0; 3]);
    assert!(!Arc::ptr_eq(
        &first,
        &selected_plan(&mut frontend, &scene, &[0])
    ));
}

#[test]
fn shadow_batch_selection_does_not_hide_invalid_culled_asset_or_sorted_topology() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    install_policy(&mut frontend, false);
    frontend
        .apply_world_mesh_asset_update(
            &mut gal,
            1,
            vec![mesh_asset(1, 1, IndexType::U16)],
            Vec::new(),
        )
        .unwrap();
    let mut scene = frame(Vec::new());
    scene.shader_environment.far_plane = 32.0;
    scene.shader_environment.configured_shadow_distance_chunks = 2;
    scene.mesh_instances = vec![caster(1, [2000.0; 3])];
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .unwrap()
        .is_empty());
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 42, &Default::default())
        .is_err());
    scene.mesh_instances[0].mesh_key = 2;
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .is_err());
    scene.mesh_instances[0].mesh_key = 1;
    scene.mesh_instances[0].mesh_generation = 2;
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .is_err());
    scene.mesh_instances[0].mesh_generation = 1;
    scene.mesh_instances[0].mesh_section_index = 9;
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .is_err());
    scene.mesh_instances[0].mesh_section_index = WORLD_MESH_SECTION_ALL;
    scene.mesh_instances[0].flags |= WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS;
    // The installed opaque mesh is not a canonical translucent quad source.
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .is_err());
    let mut translucent = mesh_asset(1, 2, IndexType::U16);
    translucent.sections[0].material_id = WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
    translucent.sections[0].material_mode = WORLD_MATERIAL_MODE_TRANSLUCENT;
    frontend
        .apply_world_mesh_asset_update(&mut gal, 2, vec![translucent], Vec::new())
        .unwrap();
    scene.mesh_instances[0].mesh_generation = 2;
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .unwrap()
        .is_empty());
    scene.mesh_instances[0].transform[0] = 2.0;
    assert!(frontend
        .source_shadow_terrain_instance_indices(&scene, 41, &Default::default())
        .is_err());
}


#[test]
fn lazy_mesh_batch_index_matches_keyed_batches_when_meshes_repeat_mid_stream() {
    let instance = mesh_instance(1, 1);
    let mut asset = mesh_asset(1, 1, IndexType::U16);
    let section = asset.sections.remove(0);
    let base = mesh_key_for_section(&instance, &section, 0, WORLD_CULL_NONE, 1, 4,
        ColorFormat::Rgba8Unorm, RasterYDirection::Up, false, false);
    let mut state = 0x9E37_79B9_7F4A_7C15u64;
    let mut next = |bound: u64| {
        state = state.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        (state >> 33) % bound
    };
    for round in 0..40 {
        let unique_prefix = next(30) as usize;
        let mut reference_batches = Vec::new();
        let mut reference = HashMap::with_hasher(Default::default());
        let mut batches = Vec::new();
        let mut index = MeshBatchIndex::with_capacity(4);
        for instance_index in 0..80usize {
            let mesh = if instance_index < unique_prefix { 1000 + instance_index as u64 } else { next(12) };
            index.begin_instance(&batches, mesh);
            for _ in 0..=next(3) {
                let section_index = next(3) as u32;
                let mut key = base;
                key.mesh_key = mesh;
                key.section_index = section_index;
                if (mesh + u64::from(section_index) + round) % 7 == 0 {
                    key.material_mode = WORLD_MATERIAL_MODE_TRANSLUCENT;
                }
                let order = (mesh % 4 == 0).then_some((mesh % 3) as i32);
                let offset = mesh * 16 + u64::from(section_index) * 4;
                let expected = push_mesh_batch(&mut reference_batches, &mut reference, key, offset, 6,
                    instance_index, order);
                let actual = index.push(&mut batches, key, offset, 6, instance_index, order);
                assert_eq!(expected.is_ok(), actual.is_ok());
            }
        }
        assert_batches_equal(&reference_batches, &batches);
    }
    // A repeated key with a different range fails in both modes.
    let mut batches = Vec::new();
    let mut index = MeshBatchIndex::with_capacity(4);
    index.begin_instance(&batches, 5);
    let mut key = base;
    key.mesh_key = 5;
    index.push(&mut batches, key, 0, 6, 0, None).unwrap();
    assert!(index.push(&mut batches, key, 6, 6, 0, None).is_err());
}


#[test]
fn covering_selection_with_repeated_meshes_matches_the_full_plan_and_ignores_excluded_churn() {
    for material_mode in [WORLD_MATERIAL_MODE_OPAQUE, WORLD_MATERIAL_MODE_TRANSLUCENT] {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut assets = vec![mesh_asset(1, 1, IndexType::U16), mesh_asset(2, 1, IndexType::U16)];
        for asset in &mut assets {
            asset.sections[0].material_mode = material_mode;
            if material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT {
                asset.sections[0].material_id = WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
            }
        }
        frontend.apply_world_mesh_asset_update(&mut gal, 1, assets, Vec::new()).unwrap();
        let camera = |key, x: f32| {
            let mut instance = caster(key, [x, 0.0, 0.0]);
            instance.flags = 0;
            instance
        };
        let mut scene = frame(Vec::new());
        // Mesh 1 repeats among the camera instances; the last is shadow-only.
        scene.mesh_instances = vec![camera(1, 0.0), camera(2, 1.0), camera(1, 2.0), caster(2, [50.0, 0.0, 0.0])];
        let plan = |frontend: &mut WorldPrimitiveFrontend, scene: &WorldPrimitiveFrame| {
            let indices = (0..scene.mesh_instances.len())
                .filter(|&index| MeshBatchSelection::Static.includes(&scene.mesh_instances[index]))
                .collect::<Vec<_>>();
            assert!(mesh_batch_indices_cover_selection(scene, MeshBatchSelection::Static, true, &indices));
            let identities = indices
                .iter()
                .map(|&index| terrain_batch_instance_key(&scene.mesh_instances[index]))
                .collect::<Vec<_>>();
            frontend
                .cached_mesh_batch_plan_selected(scene, ColorFormat::Rgba8Unorm, RasterYDirection::Up, true,
                    MeshBatchSelection::Static, &identities, true, Some(&indices))
                .unwrap()
        };
        let first = plan(&mut frontend, &scene);
        let full = mesh_batches_filtered(&scene, &frontend, ColorFormat::Rgba8Unorm, RasterYDirection::Up,
            true, MeshBatchSelection::Static, true).unwrap();
        assert_batches_equal(&full, &first);
        // A change confined to the excluded shadow-only caster reuses the plan.
        scene.mesh_instances[3].terrain_visible_facing_mask = 0x15;
        assert!(Arc::ptr_eq(&first, &plan(&mut frontend, &scene)));
        frontend.reset(&mut gal);
    }
}

#[test]
fn culled_repeats_of_unselected_meshes_do_not_change_the_selected_plan() {
    for material_mode in [WORLD_MATERIAL_MODE_OPAQUE, WORLD_MATERIAL_MODE_TRANSLUCENT] {
        let mut gal = gal();
        let mut frontend = WorldPrimitiveFrontend::default();
        let mut assets = (1..=3).map(|key| mesh_asset(key, 1, IndexType::U16)).collect::<Vec<_>>();
        for asset in &mut assets {
            asset.sections[0].material_mode = material_mode;
            if material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT {
                asset.sections[0].material_id = WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED;
            }
        }
        frontend.apply_world_mesh_asset_update(&mut gal, 1, assets, Vec::new()).unwrap();
        let mut scene = frame(Vec::new());
        // Mesh 3 repeats, but only among culled instances.
        scene.mesh_instances = vec![
            caster(1, [0.0; 3]), caster(3, [1.0; 3]), caster(2, [2.0; 3]), caster(3, [3.0; 3]),
        ];
        let selected = [0, 2];
        assert!(!mesh_batch_selection_repeats_selected_mesh(
            &scene, MeshBatchSelection::ShadowOnly, true, &selected));
        let mut reference = mesh_batches_filtered(&scene, &frontend, ColorFormat::Rgba8Unorm,
            RasterYDirection::Up, true, MeshBatchSelection::ShadowOnly, true).unwrap();
        for batch in &mut reference {
            batch.indices.retain(|index| selected.contains(index));
        }
        reference.retain(|batch| !batch.indices.is_empty());
        assert_batches_equal(&reference, &selected_plan(&mut frontend, &scene, &selected));
        // Selecting a repeated mesh keeps the full-plan path.
        assert!(mesh_batch_selection_repeats_selected_mesh(
            &scene, MeshBatchSelection::ShadowOnly, true, &[1]));
        frontend.reset(&mut gal);
    }
}

#[test]
fn memoized_static_terrain_ranges_reproduce_cold_plans_for_every_selection() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    let assets = (1..=9).map(|key| {
        let mut asset = mesh_asset(key, 1, IndexType::U16);
        let indices = asset.index_bytes.clone();
        let section = asset.sections[0].clone();
        asset.index_bytes.clear();
        asset.sections.clear();
        for facing in 0..7 {
            let mut section = section.clone();
            section.source_facing = facing;
            section.index_offset = asset.index_bytes.len() as u32;
            asset.sections.push(section);
            asset.index_bytes.extend_from_slice(&indices);
        }
        asset
    }).collect();
    frontend.apply_world_mesh_asset_update(&mut gal, 1, assets, Vec::new()).unwrap();
    let mut scene = frame(Vec::new());
    scene.mesh_instances = (1..=9).map(|key| {
        let mut instance = caster(key, [key as f32, 0.0, 0.0]);
        instance.stratum = WORLD_STRATUM_TERRAIN;
        // Camera instances (no shadow-only flag) with distinct facing masks.
        instance.flags = if key % 3 == 0 { WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY } else { 0 };
        instance.terrain_visible_facing_mask = (1 << (key % 6)) | (1 << 6);
        instance
    }).collect();
    for selection in [MeshBatchSelection::All, MeshBatchSelection::ShadowOnly, MeshBatchSelection::ShadowSupplement] {
        frontend.mesh_range_memo.borrow_mut().clear();
        let cold = mesh_batches_filtered(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true, selection, true).unwrap();
        // The cold build populated the memo; this one is served from it.
        assert!(frontend.mesh_range_memo.borrow().len() > 0);
        let warm = mesh_batches_filtered(&scene, &frontend,
            ColorFormat::Rgba8Unorm, RasterYDirection::Up, true, selection, true).unwrap();
        assert_batches_equal(&cold, &warm);
        assert!(!warm.is_empty());
    }
    frontend.reset(&mut gal);
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

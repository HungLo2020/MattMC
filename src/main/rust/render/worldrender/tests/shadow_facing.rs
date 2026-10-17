//! Camera face omission must not remove surfaces from the selected shadow pass.
use super::*;

fn scene_with_two_facings(shadow_only: bool) -> (VulkanicGal, WorldPrimitiveFrontend, WorldPrimitiveFrame) {
    let mut asset = mesh_asset(0xface5, 1, IndexType::U16);
    asset.sections[0].source_facing = 0;
    let next_offset = asset.index_bytes.len() as u32;
    asset.index_bytes.extend_from_within(..);
    let mut opposite = asset.sections[0].clone();
    opposite.index_offset = next_offset;
    opposite.source_facing = 3;
    asset.sections.push(opposite);
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.apply_world_mesh_asset_update(&mut gal, 1, vec![asset], Vec::new()).unwrap();
    let mut instance = mesh_instance(0xface5, 1);
    instance.stratum = WORLD_STRATUM_TERRAIN;
    instance.mesh_section_index = WORLD_MESH_SECTION_ALL;
    instance.terrain_visible_facing_mask = (1 << 0) | (1 << 6);
    if shadow_only { instance.flags = WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY; }
    let mut scene = frame(Vec::new());
    scene.mesh_instances.push(instance);
    (gal, frontend, scene)
}

#[test]
fn off_camera_source_shadow_batches_keep_both_terrain_facings() {
    let (mut gal, mut frontend, scene) = scene_with_two_facings(true);
    let batches = mesh_batches_filtered(&scene, &frontend, ColorFormat::Rgba8Unorm,
        RasterYDirection::Up, true, MeshBatchSelection::ShadowOnly, true).unwrap();
    assert_eq!(12, batches.iter().map(|batch| batch.index_count).sum::<u32>(),
        "Frozen disables camera-side block face omission during its shadow pass");
    frontend.reset(&mut gal);
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn camera_source_shadow_supplement_keeps_only_faces_missing_from_color_ranges() {
    let (mut gal, mut frontend, mut scene) = scene_with_two_facings(false);
    for mask in [(1 << 0) | (1 << 6), (1 << 3) | (1 << 6)] {
        scene.mesh_instances[0].terrain_visible_facing_mask = mask;
        let color = mesh_batches_filtered(&scene, &frontend, ColorFormat::Rgba8Unorm,
            RasterYDirection::Up, true, MeshBatchSelection::All, true).unwrap();
        let shadow = mesh_batches_filtered(&scene, &frontend, ColorFormat::Rgba8Unorm,
            RasterYDirection::Up, true, MeshBatchSelection::ShadowSupplement, true).unwrap();
        assert_eq!(6, color.iter().map(|batch| batch.index_count).sum::<u32>());
        assert_eq!(6, shadow.iter().map(|batch| batch.index_count).sum::<u32>());
        assert_ne!(color[0].index_offset, shadow[0].index_offset,
            "camera and supplemental shadow ranges must not duplicate the same faces");
    }
    scene.mesh_instances[0].terrain_visible_facing_mask = 0x7f;
    assert!(mesh_batches_filtered(&scene, &frontend, ColorFormat::Rgba8Unorm,
        RasterYDirection::Up, true, MeshBatchSelection::ShadowSupplement, true).unwrap().is_empty());
    scene.mesh_instances[0].flags = WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS;
    assert!(!MeshBatchSelection::ShadowSupplement.includes(&scene.mesh_instances[0]),
        "camera-sorted streams already carry their complete primitive domain");
    frontend.reset(&mut gal);
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

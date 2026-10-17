use super::super::{GuiMeshLightingMode, GuiMeshMaterialMode, GuiMeshVertex};
use super::*;

fn placement() -> GuiItemPlacement {
    GuiItemPlacement {
        stratum: 430,
        sequence: 900,
        pose: [1., 0., 0., 1., 7., 9.],
        bounds: [4, 8, 20, 24],
        extent: [640, 360],
        clip_mode: 1,
        clip: [2, 3, 120, 130],
        cache: Some(GuiItemCache {
            identity: 17,
            animated: false,
        }),
    }
}

#[test]
fn retained_item_geometry_is_immutable_and_survives_owner_release() {
    let mut source = mesh_batch(0);
    let original = source.vertices.clone();
    let owner = GuiItemTemplate::new(vec![source.clone()], vec![None], 1).unwrap();
    source.vertices[0].position = [99., 99., 99.];
    let mut output = Vec::new();
    let mut payload = 0;
    owner
        .append(&placement(), &[None], &mut output, &mut payload)
        .unwrap();
    assert_eq!(1, output.len());
    assert_eq!(owner.geometry_bytes(), payload);
    assert_eq!(original.as_ptr(), output[0].vertices.as_ptr());
    assert_eq!(original, output[0].vertices);
    assert_eq!([4, 8, 20, 24], output[0].bounds);
    assert_eq!([640, 360], output[0].gui_extent);
    assert_eq!(900, output[0].sequence);
    assert_eq!(430, output[0].stratum);
    assert_ne!(
        0,
        output[0].persistent_geometry.unwrap().generation & 0x8000_0000
    );
    assert_eq!(
        [2, 3, 120, 130],
        [
            output[0].clip_left,
            output[0].clip_top,
            output[0].clip_width,
            output[0].clip_height
        ]
    );
    drop(owner);
    assert_eq!([0., 0., 0.], output[0].vertices[0].position);
}

#[test]
fn retained_item_foil_facts_follow_source_layers_instead_of_quads() {
    let base = mesh_batch(0);
    let mut glint = mesh_batch(1);
    glint.material_mode = GuiMeshMaterialMode::Glint;
    glint.lighting_mode = GuiMeshLightingMode::Flat;
    glint.item_foil = GuiItemFoil::decode(1, 123, 1., 0.6).unwrap();
    let owner = GuiItemTemplate::new(vec![base, glint.clone()], vec![None, Some(1)], 2).unwrap();
    let foil = GuiItemFoil::decode(1, 987, 0.25, 0.9).unwrap();
    let mut pose = placement();
    pose.cache.as_mut().unwrap().animated = true;
    let mut output = Vec::new();
    let mut payload = 0;
    owner
        .append(&pose, &[None, foil], &mut output, &mut payload)
        .unwrap();
    assert_eq!(None, output[0].item_foil);
    assert_eq!(foil, output[1].item_foil);
    assert_eq!(glint.model_transform, output[1].model_transform);
    assert_eq!(1, output[1].layer_index);
    let before = output.clone();
    let before_payload = payload;
    assert!(owner
        .append(&pose, &[None, None], &mut output, &mut payload)
        .is_err());
    assert_eq!(before, output);
    assert_eq!(before_payload, payload);
    let invalid_foil = Some(GuiItemFoil {
        speed: 2.,
        ..foil.unwrap()
    });
    assert!(owner
        .append(&pose, &[None, invalid_foil], &mut output, &mut payload)
        .is_err());
    assert_eq!(before, output);
    assert_eq!(before_payload, payload);
}

#[test]
fn retained_item_invalid_dynamic_facts_and_payload_limits_are_transactional() {
    let owner = GuiItemTemplate::new(vec![mesh_batch(0)], vec![None], 1).unwrap();
    let mut output = vec![mesh_batch(0)];
    let before = output.clone();
    let mut payload = 144;
    let mut pose = placement();
    pose.pose[0] = f32::NAN;
    assert!(owner
        .append(&pose, &[None], &mut output, &mut payload)
        .is_err());
    assert_eq!(before, output);
    assert_eq!(144, payload);
    let mut payload = GUI_MESH_MAX_FRAME_PAYLOAD_BYTES;
    assert!(owner
        .append(&placement(), &[None], &mut output, &mut payload)
        .is_err());
    assert_eq!(before, output);
    assert_eq!(GUI_MESH_MAX_FRAME_PAYLOAD_BYTES, payload);
}

#[test]
fn retained_item_budget_is_reclaimed_and_does_not_alias_geometry_generations() {
    let batches = vec![mesh_batch(0)];
    let probe = GuiItemTemplate::new(batches.clone(), vec![None], 1).unwrap();
    let bytes = probe.payload_bytes();
    drop(probe);
    let budget = Arc::new(MemoryBudget {
        live: AtomicUsize::new(0),
        limit: bytes,
    });
    let first =
        GuiItemTemplate::with_budget(batches.clone(), vec![None], 1, budget.clone()).unwrap();
    assert_eq!(bytes, budget.live.load(Ordering::Acquire));
    assert!(GuiItemTemplate::with_budget(batches.clone(), vec![None], 1, budget.clone()).is_err());
    let generation = first.batches[0].persistent_geometry.unwrap().generation;
    drop(first);
    assert_eq!(0, budget.live.load(Ordering::Acquire));
    let next = GuiItemTemplate::with_budget(batches, vec![None], 1, budget.clone()).unwrap();
    assert_ne!(
        generation,
        next.batches[0].persistent_geometry.unwrap().generation
    );
    drop(next);
    assert_eq!(0, budget.live.load(Ordering::Acquire));
}

fn mesh_batch(layer_index: u32) -> GuiMeshBatchRequest {
    GuiMeshBatchRequest {
        item_cache: None,
        persistent_geometry: None,
        block_item_raster: super::super::GuiBlockItemRaster::decode(2, [0., 0., 0., 1., 1., 1.], 1)
            .unwrap(),
        item_raster_scale: 0,
        item_lighting: None,
        item_foil: None,
        decal_foil: None,
        stratum: 420,
        layer_index,
        sequence: 9,
        asset_id: 7,
        material_mode: GuiMeshMaterialMode::Cutout,
        lighting_mode: GuiMeshLightingMode::InventoryBlock,
        alpha_cutoff: 0.5,
        model_transform: [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ],
        gui_pose: [1.0, 0.0, 0.0, 1.0, 0.0, 0.0],
        bounds: [12, 34, 28, 50],
        gui_extent: [320, 180],
        projection_extent: [320.0, 180.0],
        render_extent: [0, 0],
        guard_pixels: 0,
        clip_mode: 0,
        clip_left: 0,
        clip_top: 0,
        clip_width: 0,
        clip_height: 0,
        vertices: vec![
            GuiMeshVertex {
                position: [0.0, 0.0, 0.0],
                atlas_uv: [0.0, 0.0],
                local_uv: [0.0, 0.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
            GuiMeshVertex {
                position: [1.0, 0.0, 0.0],
                atlas_uv: [1.0, 0.0],
                local_uv: [1.0, 0.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
            GuiMeshVertex {
                position: [0.0, 1.0, 0.0],
                atlas_uv: [0.0, 1.0],
                local_uv: [0.0, 1.0],
                color_argb: 0xffff_ffff,
                normal_packed: 0x007f_0000,
                source_face: 0,
                source_foil_type: 0,
            },
        ]
        .into(),
        indices: vec![0, 1, 2].into(),
    }
}

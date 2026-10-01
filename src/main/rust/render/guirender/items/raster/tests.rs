use crate::render::guirender::items::raster::*;

#[test]
fn semantic_model_transforms_lower_only_in_rust_and_preserve_legacy_bits() {
    let full = GuiItemRasterGeometry::default();
    assert_eq!(
        GuiItemModelTransform::default()
            .lower(full)
            .unwrap()
            .identity()
            .unwrap(),
        full.identity().unwrap()
    );
    let mut scaled = GuiItemModelTransform::default();
    scaled.0[0] = 0.5;
    scaled.0[5] = 0.5;
    scaled.0[12] = -0.25;
    scaled.0[13] = -0.25;
    assert_eq!(
        scaled.lower(full).unwrap().corners,
        [4.0, 4.0, 12.0, 4.0, 4.0, 12.0]
    );
    scaled.0[12] += 0.125;
    assert_eq!(
        scaled.lower(full).unwrap().corners,
        [6.0, 4.0, 14.0, 4.0, 6.0, 12.0]
    );
    let mut rotated = GuiItemModelTransform::default();
    rotated.0[0] = 0.0;
    rotated.0[1] = 1.0;
    rotated.0[4] = -1.0;
    rotated.0[5] = 0.0;
    rotated.0[12] = 0.5;
    assert_eq!(
        rotated.lower(full).unwrap().corners,
        [0.0, 16.0, 0.0, 0.0, 16.0, 16.0]
    );
    for (index, value) in [
        (0, f32::NAN),
        (0, -1.0),
        (0, 0.0),
        (2, 0.1),
        (3, 0.1),
        (8, 0.1),
        (10, 2.0),
        (14, 0.0),
        (15, 0.0),
    ] {
        let mut invalid = GuiItemModelTransform::default();
        invalid.0[index] = value;
        assert!(
            invalid.lower(full).is_err(),
            "unsupported transform entry{index}"
        );
    }
    let mut outside = GuiItemModelTransform::default();
    outside.0[12] = 0.0;
    assert!(
        outside.lower(full).is_err(),
        "transformed geometry cannot silently clip outside the item cell"
    );
}

#[test]
fn game_authored_quaternion_display_transform_preserves_planar_coverage() {
    // Exact output of ItemTransform.apply for GUI Z90, XY scale0.5,
    // translation X2/16. Quaternion arithmetic does not produce Z=1
    // bit-exactly; keep the actual copied matrix, including that residue.
    let matrix = GuiItemModelTransform([
        0.0,
        0.49999997,
        0.0,
        0.0,
        -0.49999997,
        0.0,
        0.0,
        0.0,
        0.0,
        0.0,
        0.99999994,
        0.0,
        0.375,
        -0.24999999,
        -0.49999997,
        1.0,
    ]);
    let lowered = matrix.lower(GuiItemRasterGeometry::default()).unwrap();
    for (actual, expected) in lowered
        .corners
        .into_iter()
        .zip([6.0, 12.0, 6.0, 4.0, 14.0, 12.0])
    {
        assert!(
            (actual - expected).abs() <= 0.000002,
            "actual={actual}, expected={expected}"
        );
    }
    let mut tilted = matrix;
    tilted.0[2] = f32::EPSILON;
    assert!(
        tilted.validate().is_err(),
        "actual plane tilt is not numerical Z-scale residue"
    );
    let mut translated = matrix;
    translated.0[14] = 0.0;
    assert!(
        translated.validate().is_err(),
        "Z translation still cannot enter this flat family"
    );
    let mut rescaled = matrix;
    rescaled.0[10] = 0.99;
    rescaled.0[14] = -0.495;
    assert!(
        rescaled.validate().is_err(),
        "authored Z scale is not quaternion roundoff"
    );
}

fn identities(generation: u64, count: u64) -> Vec<GuiItemRasterIdentity> {
    (1..=count)
        .map(|asset_id| GuiItemRasterIdentity {
            asset_id,
            atlas_generation: generation,
            cutout: false,
            texture_id: 1,
            region: [0, 0, 32, 32],
            color_argb: u32::MAX,
            lighting_rgb: [1.0_f32.to_bits(); 3],
            geometry: GuiItemRasterGeometry::default().identity().unwrap(),
            uv: item_uv_identity([0.0, 0.0, 1.0, 1.0]).unwrap(),
        })
        .collect()
}

#[test]
fn grouped_slots_preserve_layer_order_and_all_resource_incarnations() {
    let pair = identities(1, 2);
    let mut replaced = pair.clone();
    replaced[1].atlas_generation = 2;
    let mut relit = pair.clone();
    relit[1].lighting_rgb = [0.5_f32.to_bits(); 3];
    let groups = vec![pair.clone(), vec![pair[1], pair[0]], replaced, relit, pair];
    let mut slots = GuiItemRasterSlots::default();
    let placements = slots.prepare_groups(2, &groups, 4096).unwrap();
    assert_eq!(placements[0], placements[4]);
    for i in 0..4 {
        for j in i + 1..4 {
            assert_ne!(placements[i], placements[j]);
        }
    }
    assert_eq!(slots.prepare_groups(2, &groups, 4096).unwrap(), placements);
    let before = slots.clone();
    for invalid in [
        vec![],
        vec![vec![]],
        vec![vec![groups[0][0]; MAX_ITEM_LAYERS + 1]],
    ] {
        assert!(slots.prepare_groups(2, &invalid, 4096).is_err());
        assert_eq!(
            slots, before,
            "invalid group must not mutate retained slots"
        );
    }
}

#[test]
fn authored_uv_subrectangles_are_validated_and_part_of_raster_identity() {
    let full = identities(1, 1)[0];
    let mut partial = full;
    partial.uv = item_uv_identity([0.25, 0.0, 0.75, 0.5]).unwrap();
    let mut slots = GuiItemRasterSlots::default();
    let placements = slots.prepare(2, &[full, partial, full], 4096).unwrap();
    assert_ne!(placements[0], placements[1]);
    assert_eq!(placements[0], placements[2]);
    assert_eq!(full.uv, item_uv_identity([-0.0, 0.0, 1.0, 1.0]).unwrap());
    for uv in [
        [f32::NAN, 0.0, 1.0, 1.0],
        [0.0, 0.0, f32::INFINITY, 1.0],
        [-0.001, 0.0, 1.0, 1.0],
        [0.0, 0.0, 1.001, 1.0],
        [0.5, 0.0, 0.5, 1.0],
        [0.0, 0.5, 1.0, 0.25],
    ] {
        assert!(item_uv_identity(uv).is_err());
    }
}

#[test]
fn item_local_geometry_validates_all_four_corners_and_canonicalizes_zero() {
    let full = GuiItemRasterGeometry::default();
    assert!(full.validate().is_ok());
    assert!(GuiItemRasterGeometry {
        corners: [4.0, 2.0, 12.0, 2.0, 4.0, 14.0]
    }
    .validate()
    .is_ok());
    assert!(
        GuiItemRasterGeometry {
            corners: [16.0, 0.0, 0.0, 0.0, 16.0, 16.0]
        }
        .validate()
        .is_ok(),
        "UV reflection reverses parameterization, not the semantic face"
    );
    for corners in [
        [0.0, 0.0, 16.0, 4.0, 4.0, 16.0],
        [0.0, 0.0, 0.0, 0.0, 0.0, 16.0],
        [-1.0, 0.0, 16.0, 0.0, 0.0, 16.0],
        [f32::NAN, 0.0, 16.0, 0.0, 0.0, 16.0],
        [0.0, 0.0, f32::INFINITY, 0.0, 0.0, 16.0],
    ] {
        assert!(GuiItemRasterGeometry { corners }.validate().is_err());
    }
    let mut negative_zero = full;
    negative_zero.corners[0] = -0.0;
    assert_eq!(full.identity().unwrap(), negative_zero.identity().unwrap());
}

#[test]
fn different_geometry_on_the_same_atlas_region_cannot_alias_a_raster_slot() {
    let mut slots = GuiItemRasterSlots::default();
    let full = identities(1, 1)[0];
    let mut inset = full;
    inset.geometry = GuiItemRasterGeometry {
        corners: [4.0, 2.0, 12.0, 2.0, 4.0, 14.0],
    }
    .identity()
    .unwrap();
    let placements = slots.prepare(2, &[full, inset, full], 4096).unwrap();
    assert_ne!(placements[0], placements[1]);
    assert_eq!(placements[0], placements[2]);
}

#[test]
fn replacement_models_append_to_retained_slots_as_observed_after_real_reload() {
    let mut slots = GuiItemRasterSlots::default();
    let a = identities(1, 9);
    let b = identities(2, 9);
    assert_eq!(
        slots.prepare(3, &a, 4096).unwrap()[0].rect,
        [0, 464, 48, 48]
    );
    let after = slots.prepare(3, &b, 4096).unwrap();
    assert_eq!(
        after[0].rect,
        [432, 464, 48, 48],
        "r230 Frozen replacement feather remains in row zero, slot nine"
    );
    assert_eq!(
        after[8].rect,
        [336, 416, 48, 48],
        "r230 replacement apple wraps to row one, slot seventeen"
    );
    assert_eq!(slots.entries.len(), 18);
    assert_eq!(slots.prepare(3, &b, 4096).unwrap(), after);
    assert_eq!(slots.entries.len(), 18);
    let before = slots.clone();
    assert!(slots.prepare(0, &b, 4096).is_err());
    assert_eq!(slots, before, "failed plans cannot mutate slot history");
    assert_eq!(
        slots.prepare(2, &b, 4096).unwrap()[0].rect,
        [0, 480, 32, 32]
    );
    assert_eq!(
        slots.entries.len(),
        9,
        "GUI-scale changes explicitly invalidate slot history"
    );
}

#[test]
fn slot_cache_rebuilds_at_capacity_and_deduplicates_current_semantic_models() {
    let mut slots = GuiItemRasterSlots::default();
    let first = identities(1, 1);
    slots.prepare(3, &first, 4096).unwrap();
    for generation in 2..100 {
        slots.prepare(3, &identities(generation, 1), 4096).unwrap();
    }
    assert_eq!(slots.entries.len(), 99);
    let next = identities(100, 1);
    let placements = slots.prepare(3, &[next[0], next[0]], 4096).unwrap();
    assert_eq!(placements[0], placements[1]);
    assert_eq!(placements[0].rect, [0, 464, 48, 48]);
    assert_eq!(
        slots.entries.len(),
        1,
        "vanilla rebuilds when the union reaches existing capacity"
    );
}

#[test]
fn slot_metadata_has_a_hard_bound_and_failed_growth_is_transactional() {
    let mut slots = GuiItemRasterSlots::default();
    slots
        .prepare(1, &identities(1, MAX_ITEMS as u64), 4096)
        .unwrap();
    let before = slots.clone();
    assert!(slots.prepare(1, &identities(2, 1), 4096).is_err());
    assert_eq!(slots, before);
}

#[test]
fn observed_nine_item_scale_three_layout_has_separate_raster_coordinates() {
    let layout = GuiItemRasterLayout::new(3, 9, 16384).unwrap();
    for index in 0..9 {
        let slot = layout.placement(index).unwrap();
        assert_eq!(slot.target_extent, [512, 512]);
        assert_eq!(slot.rect, [48 * index, 0, 48, 48]);
        assert_eq!(
            slot.raster_point([0.0, 0.0]).unwrap(),
            [48.0 * index as f32, 0.0]
        );
        assert_eq!(
            slot.raster_point([16.0, 16.0]).unwrap(),
            [48.0 * (index + 1) as f32, 48.0]
        );
        assert_eq!(
            slot.composite_uv(),
            [
                48.0 * index as f32 / 512.0,
                0.0,
                48.0 * (index + 1) as f32 / 512.0,
                48.0 / 512.0
            ]
        );
    }
    assert!(layout.placement(9).is_err());
}

#[test]
fn observed_source_atlas_uvs_survive_local_transport_bit_exactly() {
    use crate::render::guirender::atlas_reference::{
        AcceptedAtlasIncarnation, GuiAtlasReference,
    };
    // Exact endpoint bits recorded independently by both callsites in r226.
    for (x, y, expected) in [
        (1536, 1904, [0x3ec00000, 0x3f6e0000, 0x3ec40000, 0x3f720000]),
        (1568, 1808, [0x3ec40000, 0x3f620000, 0x3ec80000, 0x3f660000]),
        (1536, 1776, [0x3ec00000, 0x3f5e0000, 0x3ec40000, 0x3f620000]),
    ] {
        let reference = GuiAtlasReference {
            asset_id: 1,
            atlas: AcceptedAtlasIncarnation {
                texture_id: 1,
                generation: 1,
                width: 4096,
                height: 2048,
            },
            x,
            y,
            width: 32,
            height: 32,
        };
        let low = reference.atlas_uv([0.0, 0.0]).unwrap();
        let high = reference.atlas_uv([1.0, 1.0]).unwrap();
        assert_eq!(
            [
                low[0].to_bits(),
                low[1].to_bits(),
                high[0].to_bits(),
                high[1].to_bits()
            ],
            expected
        );
    }
}

#[test]
fn layout_wraps_and_rejects_overflow_without_dropping_items() {
    let layout = GuiItemRasterLayout::new(3, 11, 16384).unwrap();
    assert_eq!(layout.placement(10).unwrap().rect, [0, 48, 48, 48]);
    for (scale, count, limit) in [
        (0, 1, 512),
        (1, 0, 512),
        (1, 4097, 16384),
        (1, 1, 256),
        (u32::MAX, 1, 16384),
        (64, 2, 512),
    ] {
        assert!(GuiItemRasterLayout::new(scale, count, limit).is_err());
    }
    let slot = layout.placement(0).unwrap();
    assert!(slot.raster_point([f32::NAN, 0.0]).is_err());
    assert!(slot.raster_point([f32::MAX, 0.0]).is_err());
    assert_eq!(
        slot.raster_point([-0.5, 16.5]).unwrap(),
        [-1.5, 49.5],
        "geometry is not silently clipped; the explicit raster pass owns its scissor"
    );
}

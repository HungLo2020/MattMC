use super::*;

#[test]
fn family_definitions_keep_distinct_interaction_policy_and_sound_identity() {
    let iron = BlockSet::Iron.definition();
    let copper = BlockSet::Copper.definition();
    let gold = BlockSet::Gold.definition();
    assert!(!iron.can_open_by_hand && !iron.can_open_by_wind_charge);
    assert!(copper.can_open_by_hand && copper.can_open_by_wind_charge);
    assert!(!gold.can_open_by_hand && gold.can_open_by_wind_charge);
    assert_eq!(BlockSet::Stone.definition().pressure_sensitivity, PressureSensitivity::Mobs);
    assert!(BlockSet::Cherry.definition().arrows_activate_button);
    assert_ne!(Wood::Cherry.definition().sound, Wood::Cherry.definition().hanging_sign_sound);
    for set in registry().sets() {
        assert!(std::ptr::eq(set, set.id.definition()));
        for event in set.events { assert!(sound::registry().event(event).is_some()); }
    }
    for wood in registry().woods() {
        assert!(std::ptr::eq(wood, wood.id.definition()));
        assert_eq!(wood.name, wood.set.definition().name);
        for event in wood.gate_events { assert!(sound::registry().event(event).is_some()); }
    }
}

#[test]
fn registered_bindings_preserve_integrated_content_and_weighted_plate_limits() {
    let blocks = super::super::definitions::registry();
    let family = |name| blocks.find(name).unwrap().family;
    assert_eq!(family("minecraft:light_weighted_pressure_plate"), Family::WeightedPlate { set: BlockSet::Gold, max_weight: 15 });
    assert_eq!(family("minecraft:heavy_weighted_pressure_plate"), Family::WeightedPlate { set: BlockSet::Iron, max_weight: 150 });
    assert_eq!(family("minecraft:pewen_button"), Family::Button { set: BlockSet::Cherry, press_ticks: 30 });
    assert_eq!(family("minecraft:pewen_fence_gate"), Family::FenceGate(Wood::Oak));
    assert_eq!(family("minecraft:stone_button"), Family::Button { set: BlockSet::Stone, press_ticks: 20 });
    assert_eq!(family("minecraft:stone"), Family::None);
    assert_eq!(blocks.definitions().iter().filter(|b| b.family != Family::None).count(), 141);
}

#[test]
fn family_buffers_are_stable_bounded_and_reject_unknown_selectors() {
    use ffi::mattmc_block_family_buffer as buffer;
    unsafe {
        let mut count = -1;
        let first = buffer(3, &mut count);
        assert_eq!(count as usize, super::super::definitions::registry().definitions().len() * 3);
        assert_eq!(first, buffer(3, &mut count));
        assert!(buffer(5, &mut count).is_null()); assert_eq!(count, 0);
        assert!(buffer(0, std::ptr::null_mut()).is_null());
    }
}

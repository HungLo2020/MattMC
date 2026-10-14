use super::program::{Anchor, Feature, Modifier, Program};
use super::random::WorldgenRandom;

/// Values printed by Java's `WorldgenRandom(new XoroshiroRandomSource(..))`
/// after `setDecorationSeed(42, -16, 4096)` and `setFeatureSeed(l, 17, 6)`.
#[test]
fn worldgen_random_matches_java() {
    let mut random = WorldgenRandom::new(12345);
    let decoration = random.set_decoration_seed(42, -16, 4096);
    assert_eq!(decoration, 4_608_034_502_445_437_818);
    random.set_feature_seed(decoration, 17, 6);
    assert_eq!(random.next_int(16), 2);
    assert_eq!(random.next_int(16), 4);
    assert_eq!(random.next_int(3), 0);
    assert_eq!(random.next_int(385), 200);
    assert_eq!(random.next_float().to_bits(), 1_052_087_420);
    assert_eq!(random.next_double().to_bits(), 4_575_105_471_119_097_856);
    assert_eq!(random.next_long(), -584_173_319_362_227_347);
    assert_eq!(random.next_int(7), 1);
}

fn ore_words(modifiers: &[[i32; 6]], targets: &[[i32; 4]], sets: &[&[i32]], biomes: &[i32]) -> Vec<i32> {
    let mut words = vec![1, modifiers.len() as i32];
    modifiers.iter().for_each(|m| words.extend_from_slice(m));
    words.extend_from_slice(&[1, 9, 0.5f32.to_bits() as i32, targets.len() as i32]);
    targets.iter().for_each(|t| words.extend_from_slice(t));
    words.push(sets.len() as i32);
    for set in sets {
        words.push(set.len() as i32);
        words.extend_from_slice(set);
    }
    words.push(biomes.len() as i32);
    words.extend_from_slice(biomes);
    words
}

#[test]
fn program_decodes_ore_placements() {
    let words = ore_words(
        &[[1, 20, 0, 0, 0, 0], [3, 0, 0, 0, 0, 0], [5, 1, -80, 0, 80, 0], [7, 0, 0, 0, 0, 0], [8, 7, 0, 0, 0, 0]],
        &[[1, 0, 0, 30], [2, 0.25f32.to_bits() as i32, 1, 31]],
        &[&[0b1000], &[1, 1]],
        &[0b10],
    );
    let program = Program::decode(&words).expect("valid program");
    assert_eq!(
        program.modifiers,
        vec![
            Modifier::CountConstant(20),
            Modifier::InSquare,
            Modifier::HeightTrapezoid(Anchor::AboveBottom(-80), Anchor::Absolute(80), 0),
            Modifier::Biome,
            Modifier::Rarity(7),
        ]
    );
    let Feature::Ore { size, ref targets, .. } = program.feature;
    assert_eq!(size, 9);
    assert_eq!(targets[1].probability, Some(0.25));
    assert!(program.in_set(0, 3) && !program.in_set(0, 2) && program.in_set(1, 32));
    assert!(program.biome_allowed(1) && !program.biome_allowed(0) && !program.biome_allowed(64));
    assert_eq!(program.ore_shape.len(), 18);
    assert!(Anchor::BelowTop(10).resolve(-64, 384) == 309);
}

#[test]
fn program_rejects_malformed_words() {
    let good = ore_words(&[[3, 0, 0, 0, 0, 0]], &[[1, 0, 0, 1]], &[&[1]], &[1]);
    assert!(Program::decode(&good).is_some());
    for cut in 0..good.len() {
        assert!(Program::decode(&good[..cut]).is_none(), "truncated at {cut}");
    }
    let mut extra = good.clone();
    extra.push(0);
    assert!(Program::decode(&extra).is_none());
    let unknown_rule = ore_words(&[], &[[9, 0, 0, 1]], &[&[1]], &[]);
    assert!(Program::decode(&unknown_rule).is_none());
    let missing_set = ore_words(&[], &[[1, 0, 3, 1]], &[&[1]], &[]);
    assert!(Program::decode(&missing_set).is_none());
    let zero_rarity = ore_words(&[[8, 0, 0, 0, 0, 0]], &[], &[], &[]);
    assert!(Program::decode(&zero_rarity).is_none());
}

#[test]
fn empty_height_ranges_stay_in_java() {
    let words = ore_words(&[[4, 0, 10, 0, 5, 0]], &[], &[], &[]);
    let program = Program::decode(&words).unwrap();
    assert!(!super::placement::heights_resolve(&program, -64, 384));
    let words = ore_words(&[[6, 0, 0, 0, 3, 4]], &[], &[], &[]);
    let program = Program::decode(&words).unwrap();
    assert!(!super::placement::heights_resolve(&program, -64, 384));
}

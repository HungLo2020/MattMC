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

/// Version-3 words for one top-level ore placement.
fn ore_words(modifiers: &[[i32; 6]], targets: &[[i32; 4]], sets: &[&[i32]], biomes: &[i32]) -> Vec<i32> {
    let mut words = vec![3, modifiers.len() as i32];
    modifiers.iter().for_each(|m| words.extend_from_slice(m));
    let mut payload = vec![9, 0.5f32.to_bits() as i32, targets.len() as i32];
    targets.iter().for_each(|t| payload.extend_from_slice(t));
    words.extend_from_slice(&[1, payload.len() as i32]);
    words.extend(payload);
    tail(&mut words, sets, biomes);
    words
}

fn tail(words: &mut Vec<i32>, sets: &[&[i32]], biomes: &[i32]) {
    words.push(sets.len() as i32);
    for set in sets {
        words.push(set.len() as i32);
        words.extend_from_slice(set);
    }
    words.push(biomes.len() as i32);
    words.extend_from_slice(biomes);
    // No native noises.
    words.push(0);
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
        program.placed.modifiers,
        vec![
            Modifier::Count(super::program::IntProvider::Constant(20)),
            Modifier::InSquare,
            Modifier::HeightTrapezoid(Anchor::AboveBottom(-80), Anchor::Absolute(80), 0),
            Modifier::Biome,
            Modifier::Rarity(7),
        ]
    );
    let Feature::Ore { size, ref targets, ref shape, .. } = program.placed.feature else {
        panic!("ore program")
    };
    assert_eq!(size, 9);
    assert_eq!(shape.len(), 18);
    assert_eq!(targets[1].probability, Some(0.25));
    assert!(program.in_set(0, 3) && !program.in_set(0, 2) && program.in_set(1, 32));
    assert!(program.biome_allowed(1) && !program.biome_allowed(0) && !program.biome_allowed(64));
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

/// `BlockPos.withinManhattan` order, folded as Java `h = h * 31 + pos.asLong()`
/// for center (10, -5, 300); counts and hashes printed by Java.
#[test]
fn manhattan_order_matches_java() {
    let as_long = |[x, y, z]: [i32; 3]| -> i64 {
        ((x as i64 & 0x3ff_ffff) << 38) | ((z as i64 & 0x3ff_ffff) << 12) | (y as i64 & 0xfff)
    };
    for (range, count, hash) in [
        ([3, 7, 5], 1155, 800_040_425_738_896_171i64),
        ([0, 0, 0], 1, 2_748_780_302_331),
        ([2, 0, 1], 15, 7_557_927_152_594_624_603),
        ([7, 7, 7], 3375, -595_621_141_440_666_637),
    ] {
        let positions: Vec<_> = super::simple::Manhattan::new([10, -5, 300], range[0], range[1], range[2]).collect();
        assert_eq!(positions.len(), count, "{range:?}");
        let folded = positions.iter().fold(0i64, |h, &p| h.wrapping_mul(31).wrapping_add(as_long(p)));
        assert_eq!(folded, hash, "{range:?}");
    }
}

#[test]
fn program_decodes_set_block_features() {
    // Replace blobs: count 75, uniform radius 3..7, target set 0, replacement state 7.
    let mut words = vec![3, 1, 1, 75, 0, 0, 0, 0, 2, 5, 2, 3, 7, 0, 7];
    tail(&mut words, &[&[1]], &[]);
    let program = Program::decode(&words).expect("replace blobs");
    assert_eq!(program.placed.feature, Feature::ReplaceBlobs {
        radius: super::program::IntProvider::Uniform(3, 7), target: 0, replacement: 7 });
    assert_eq!(program.placed.feature.set_block_states(), vec![7]);
    // A spring whose set index is missing is rejected.
    let mut words = vec![3, 0, 4, 6, 1, 4, 1, 5, 9, 3];
    tail(&mut words, &[&[1]], &[]);
    assert!(Program::decode(&words).is_none());
    // A nested placement may not use the biome filter.
    let mut words = vec![3, 0, 7, 0];
    let child_biome = [1, 7, 0, 0, 0, 0, 0, 3, 3, 1, 0, 0];
    let child = [0, 3, 3, 1, 0, 0];
    words.extend_from_slice(&child_biome);
    words.extend_from_slice(&child);
    let length = words.len() as i32 - 4;
    words[3] = length;
    tail(&mut words, &[&[1]], &[]);
    assert!(Program::decode(&words).is_none());
    let mut words = vec![3, 0, 7, 0];
    words.extend_from_slice(&child);
    words.extend_from_slice(&child);
    words[3] = words.len() as i32 - 4;
    tail(&mut words, &[&[1]], &[]);
    let program = Program::decode(&words).expect("boolean selector of glowstone");
    assert!(matches!(program.placed.feature, Feature::RandomBooleanSelector(..)));
    assert_eq!(program.placed.feature.set_block_states(), vec![1, 1]);
}

/// `java.util.HashSet<BlockPos>` iteration after inserts (with resizes and
/// duplicates), iterator removals and copy-construction; hashes printed by Java.
#[test]
fn java_hash_set_order_matches_java() {
    use super::java_set::JavaHashSet;
    let as_long = |[x, y, z]: [i32; 3]| -> i64 {
        ((x as i64 & 0x3ff_ffff) << 38) | ((z as i64 & 0x3ff_ffff) << 12) | (y as i64 & 0xfff)
    };
    let fold = |set: &JavaHashSet| set.iter().fold(0i64, |h, p| h.wrapping_mul(31).wrapping_add(as_long(p)));
    let mut s: i64 = 12345;
    let mut input = Vec::new();
    for _ in 0..700 {
        s = s.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
        let v = ((s as u64) >> 33) as i32;
        input.push([-37 + v.rem_euclid(13), 64 + (v >> 4).rem_euclid(20), 1040 + (v >> 9).rem_euclid(13)]);
    }
    let mut set = JavaHashSet::new();
    input.iter().for_each(|&p| {
        set.insert(p);
    });
    assert_eq!((set.len(), fold(&set)), (628, 2_679_539_587_577_640_241));
    let doomed: Vec<_> = set.iter().enumerate().filter(|(n, _)| n % 3 == 0).map(|(_, p)| p).collect();
    doomed.into_iter().for_each(|p| {
        set.remove(p);
    });
    assert_eq!((set.len(), fold(&set)), (418, -4_481_915_492_641_429_536));
    let copy = JavaHashSet::from_ordered(&set.to_vec());
    assert_eq!((copy.len(), fold(&copy)), (418, -4_481_915_492_641_429_536));
    let mut dense = JavaHashSet::new();
    for i in 0..40 {
        dense.insert([i * 16 * 961 % 7, i * 16, 0]);
    }
    assert_eq!((dense.len(), fold(&dense)), (40, 4_963_341_231_892_844_928));
}

/// Version 3: a clamped count, a noise-threshold count and a block column
/// (kind 12) decode; noise and set indices are checked against their tables.
#[test]
fn program_decodes_version_three_modifiers_and_features() {
    use super::program::IntProvider;
    use super::vegetation::Vegetation;
    let level = (-0.8f64).to_bits() as i64;
    let mut words = vec![3, 2];
    // count: clamped(uniform 1..9, 2, 5).
    words.extend_from_slice(&[13, 0, 0, 0, 0, 0, 5, 2, 5, 2, 1, 9]);
    // noise_threshold_count(-0.8, 5, 10) over noise 0.
    words.extend_from_slice(&[17, level as i32, (level >> 32) as i32, 5, 10, 0]);
    // block_column: one layer (constant 3, simple state 7), up, matching set 0, no tip priority.
    let payload = [1, 1, 3, 0, 0, 1, 1, 1, 7, 1, 5, 0, 1, 0, 0, 0];
    words.extend_from_slice(&[12, payload.len() as i32]);
    words.extend_from_slice(&payload);
    let mut good = words.clone();
    tail(&mut good, &[&[1]], &[]);
    // One noise state address (8-aligned, nonzero) replaces the empty noise table.
    good.pop();
    good.extend_from_slice(&[1, 4096, 0]);
    let program = Program::decode(&good).expect("version-3 program");
    assert_eq!(program.placed.modifiers[0], Modifier::Count(IntProvider::Clamped(Box::new(IntProvider::Uniform(1, 9)), 2, 5)));
    assert!(matches!(program.placed.modifiers[1], Modifier::NoiseThresholdCount { noise: 0, below: 5, above: 10, .. }));
    assert!(matches!(&program.placed.feature, Feature::Vegetation(v) if matches!(**v, Vegetation::BlockColumn { .. })));
    // Without the noise table the threshold count's noise is out of range.
    let mut missing_noise = words.clone();
    tail(&mut missing_noise, &[&[1]], &[]);
    assert!(Program::decode(&missing_noise).is_none());
    // A column whose predicate names set 1 of one set is rejected.
    let mut bad_set = good.clone();
    let at = 2 + 12 + 6 + 2 + 14;
    bad_set[at] = 1;
    assert!(Program::decode(&bad_set).is_none());
    // Clamped bounds must be ordered.
    let mut bad_clamp = good.clone();
    bad_clamp[9] = 6;
    assert!(Program::decode(&bad_clamp).is_none());
}

/// The step plan: per step, each biome's sorted indices and one program slot per feature.
#[test]
fn plan_decodes_and_rejects_malformed_steps() {
    use super::sequence::Plan;
    // 11 steps, 2 with features: step 0 has 3 features over 2 biomes, step 1 has 1.
    let words = [11, 2, 3, 2, 2, 0, 2, 1, 1, 1, 2, 0, 1, 0];
    assert!(Plan::decode(&words, &[0, 0, 0, 0]).is_some());
    // Wrong program count, unsorted indices, an index beyond the step and trailing words.
    assert!(Plan::decode(&words, &[0, 0, 0]).is_none());
    let mut unsorted = words;
    unsorted[5] = 2;
    unsorted[6] = 0;
    assert!(Plan::decode(&unsorted, &[0, 0, 0, 0]).is_none());
    let mut beyond = words;
    beyond[8] = 3;
    assert!(Plan::decode(&beyond, &[0, 0, 0, 0]).is_none());
    let mut trailing = words.to_vec();
    trailing.push(0);
    assert!(Plan::decode(&trailing, &[0, 0, 0, 0]).is_none());
}

/// Vegetation-patch-shaped sets (a ring of columns, Y varying slowly); folds
/// printed by Java.
#[test]
fn java_hash_set_patch_order_matches_java() {
    use super::java_set::JavaHashSet;
    const EXPECTED: [i64; 40] = [
        -3241207266980118337, -1379760346226350454, -2480222909137378873, 2285935738728747978,
        1194856899530216007, 1869883500734353712, -7712518491222673597, -6999680560265113147,
        2576631002935814468, 4066387669614945665, -1972224776500935150, 8691753456218594494,
        6612726009614447023, 7968731847000495358, 6715253903482227257, 6070960886937037179,
        4368206469667172046, 1451073683599645516, 3652893199539458277, 4146264383189360675,
        -7928604366503192339, -8752367474006193398, 6393221939287769130, 3569223760520855269,
        -1400433885115096871, 5372173777596868210, 5770058486695041475, -17225535832825227,
        7447700661936378549, 7844089076048473036, -4421576005996196785, -4004856601720604818,
        8707141409374362270, 5076165866574801613, -3709175645487691708, 1464640511447251834,
        -4209892128995868072, -63645759654903589, -8165315321277271077, -4690386466248213752,
    ];
    let as_long = |[x, y, z]: [i32; 3]| -> i64 {
        ((x as i64 & 0x3ff_ffff) << 38) | ((z as i64 & 0x3ff_ffff) << 12) | (y as i64 & 0xfff)
    };
    let step = |seed: i64| seed.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
    let mut seed: i64 = 99;
    for (patch, expected) in EXPECTED.iter().enumerate() {
        seed = step(seed);
        let cx = 4000 + ((seed as u64 >> 33) % 600) as i32;
        let cz = 2000 + ((seed as u64 >> 40) % 600) as i32;
        let r = 7 + patch as i32 % 4;
        let mut set = JavaHashSet::new();
        for k in -r..=r {
            for l in -r..=r {
                seed = step(seed);
                if seed as u64 >> 60 == 0 {
                    continue;
                }
                let y = ((seed as u64 >> 50) % 3) as i32 + (k + l) / 4;
                set.insert([cx + k, y, cz + l]);
            }
        }
        let fold = set.iter().fold(0i64, |h, p| h.wrapping_mul(31).wrapping_add(as_long(p)));
        assert_eq!(fold, *expected, "patch {patch}");
    }
}

/// Keys whose hashes share their low seven bits fill one bucket of a
/// 128-bucket table: `HashMap` makes it a tree bin (new keys follow their
/// tree parent, the root moves to the front) and later splits it on resize.
/// Folds printed by Java.
#[test]
fn java_hash_set_tree_bins_match_java() {
    use super::java_set::JavaHashSet;
    const EXPECTED: [i64; 12] = [
        7709660635000209408, -2635086543470985216, -7325506940295970816, 3674111562701864960,
        -2487794591424577536, -1262266561599635456, 8920423323175747584, -8097439694419132416,
        -5291096793218547712, 3742618558815797248, 6325744106903437312, -3356881567367561216,
    ];
    let as_long = |[x, y, z]: [i32; 3]| -> i64 {
        ((x as i64 & 0x3ff_ffff) << 38) | ((z as i64 & 0x3ff_ffff) << 12) | (y as i64 & 0xfff)
    };
    let mut seed: i64 = 7;
    for (round, expected) in EXPECTED.iter().enumerate() {
        let mut keys: Vec<[i32; 3]> = (1..=60).map(|i| [i, 0, 0]).collect();
        let mut colliders: Vec<[i32; 3]> = (1..=12 + round as i32 * 3).map(|m| [128 * m, 0, 0]).collect();
        for i in (1..colliders.len()).rev() {
            seed = seed.wrapping_mul(6_364_136_223_846_793_005).wrapping_add(1_442_695_040_888_963_407);
            let j = ((seed as u64 >> 33) % (i as u64 + 1)) as usize;
            colliders.swap(i, j);
        }
        keys.extend(colliders);
        keys.extend((61..=60 + round as i32 * 25).map(|i| [i, 0, 0]));
        let mut set = JavaHashSet::new();
        keys.iter().for_each(|&p| {
            set.insert(p);
        });
        assert!(set.ever_treeified(), "round {round}");
        let fold = set.iter().fold(0i64, |h, p| h.wrapping_mul(31).wrapping_add(as_long(p)));
        assert_eq!(fold, *expected, "round {round}");
    }
}

/// The fallen producer carries every oriented write state into admission, while
/// its retained VM configuration index is not mistaken for a native state set.
#[test]
fn fallen_program_admits_all_orientations_and_validates_sets() {
    let payload = [1, 1, 1, 7, 1, 8, 0, 0, 1, 2, 1, 7, 8, 9];
    let mut words = vec![3, 0, 61, payload.len() as i32];
    words.extend(payload);
    tail(&mut words, &[&[1], &[2]], &[]);
    let program = Program::decode(&words).expect("complete fallen program");
    assert_eq!(program.placed.feature.kind(), 61);
    assert!(program.placed.feature.uses_set_block());
    assert_eq!(program.placed.feature.set_block_states(), vec![7, 8, 9]);
    for end in 0..words.len() {
        assert!(Program::decode(&words[..end]).is_none(), "truncated at {end}");
    }
    let mut missing_support = words.clone();
    missing_support[4 + 8] = 5;
    assert!(Program::decode(&missing_support).is_none());
    let mut missing_axis = words.clone();
    missing_axis[4 + 11] = 6;
    assert!(Program::decode(&missing_axis).is_none(), "provider state lacks an orientation entry");
}

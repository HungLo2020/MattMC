//! Unit checks of the replayed section writes, packing and the fill loop.
//! Exact parity with doFill's Java loop is covered by `NativeNoiseFillTest`.
use super::section::{pack, Section, ENTRIES};
use super::*;
use crate::world::level::levelgen::random::{Legacy, Positional, Xoroshiro};

fn unpack(raw: &[i64], bits: u32, count: usize) -> Vec<u32> {
    let per_long = (64 / bits) as usize;
    (0..count)
        .map(|index| {
            let cell = index / per_long;
            let shift = (index - cell * per_long) as u32 * bits;
            ((raw[cell] as u64 >> shift) & ((1u64 << bits) - 1)) as u32
        })
        .collect()
}

/// Values at every storage index, resolved through the palette.
fn values(section: &Section) -> Vec<i32> {
    let bits = section.storage_bits();
    let ids = unpack(&section.packed(), bits, ENTRIES);
    if section.palette().is_empty() {
        ids.iter().map(|id| *id as i32).collect()
    } else {
        ids.iter().map(|id| section.palette()[*id as usize]).collect()
    }
}

#[test]
fn palette_grows_through_linear_hash_and_global_preserving_storage_order() {
    let mut section = Section::new(0, 15);
    let mut expected = vec![0; ENTRIES];
    // Write states at descending indices so storage order differs from write order.
    for (step, state) in (1..=300).enumerate() {
        let index = ENTRIES - 1 - step * 13;
        section.set(index, state);
        expected[index] = state;
        let (bits, requested) = (section.storage_bits(), section.requested_bits());
        match state {
            1..=15 => assert_eq!((4, 4), (bits, requested), "linear at {state}"),
            16..=31 => assert_eq!((5, 5), (bits, requested), "hash5 at {state}"),
            32..=63 => assert_eq!((6, 6), (bits, requested)),
            64..=127 => assert_eq!((7, 7), (bits, requested)),
            128..=255 => assert_eq!((8, 8), (bits, requested)),
            _ => assert_eq!((15, 9), (bits, requested), "global at {state}"),
        }
        assert_eq!(expected, values(&section), "contents after {state}");
        if !section.palette().is_empty() {
            // Air first, then first occurrence by storage index after the last resize.
            assert_eq!(0, section.palette()[0]);
        }
    }
    assert!(section.palette().is_empty());
}

#[test]
fn resize_orders_palette_by_storage_index_not_write_order() {
    let mut section = Section::new(0, 15);
    // 15 states fill the linear palette in write order 1..=15, at descending indices.
    for state in 1..=15 {
        section.set((16 - state) as usize, state);
    }
    assert_eq!((1..=15).collect::<Vec<_>>(), section.palette()[1..]);
    // The 16th state resizes: index 0 holds air... index 1 holds state 15.
    section.set(4000, 16);
    let mut by_index: Vec<i32> = vec![0];
    by_index.extend((1..=15).rev());
    by_index.push(16);
    assert_eq!(by_index, section.palette());
}

#[test]
fn packing_matches_simple_bit_storage_layout() {
    let values: Vec<u32> = (0..ENTRIES as u32).map(|index| index * 7 % 31).collect();
    for bits in [4, 5, 6, 7, 8, 15] {
        let raw = pack(&values, bits);
        assert_eq!(ENTRIES.div_ceil((64 / bits) as usize), raw.len());
        assert_eq!(values.iter().map(|value| value & ((1 << bits) - 1)).collect::<Vec<_>>(), unpack(&raw, bits, ENTRIES));
    }
    assert!(pack(&values, 0).is_empty());
}

#[test]
fn ceil_log2_matches_mth() {
    for (value, expected) in [(0, 0), (1, 0), (2, 1), (3, 2), (256, 8), (257, 9), (385, 9), (513, 10)] {
        assert_eq!(expected, ceil_log2(value), "{value}");
    }
}

fn config(substance: Substance) -> Config {
    Config {
        min_y: -64,
        height: 384,
        min_section: -4,
        section_count: 24,
        cell_width: 4,
        cell_height: 8,
        air: 0,
        default_block: 1,
        global_bits: 15,
        substance,
        ore: None,
    }
}

#[test]
fn disabled_substance_fills_fluid_below_levels_and_tracks_heights_and_counts() {
    // 0 air, 1 stone (blocks motion), 2 water (fluid), 3 lava (fluid).
    let flags = [FLAG_AIR, FLAG_BLOCKS_MOTION, FLAG_FLUID, FLAG_FLUID | FLAG_RANDOM_TICKS];
    let substance = Substance::Disabled(Picker { lava_below: -54, lava_level: -54, lava: 3, fluid_level: 63, fluid: 2 });
    let mut fill = NoiseFill::new(config(substance), &flags);
    // Upper half solid, lower half open, in descending-Y density order.
    let density: Vec<f64> = (0..128).map(|index| if index < 64 { 1.0 } else { -1.0 }).collect();
    let cell = Cell { x: 16, y: 56, z: -32, density: &density, materials: &[], toggle: Corners([0.0; 8]),
        ridged_a: Corners([0.0; 8]), ridged_b: Corners([0.0; 8]), gap: std::ptr::null() };
    fill.fill_cell(&cell).unwrap();
    // y 60..63 stone, y 56..59 water (below sea level 63), all in section 3 (y 48..63).
    let section = fill.section(7).expect("section of y 56..63");
    assert_eq!(64 + 64, section.non_empty);
    assert_eq!(64, section.fluid);
    assert_eq!(0, section.ticking);
    let ocean = fill.heightmap_raw(true);
    let surface = fill.heightmap_raw(false);
    let ocean_heights = unpack(&ocean, 9, 256);
    let surface_heights = unpack(&surface, 9, 256);
    // Column (0, 0) relative to the chunk: x 16 -> 0, z -32 -> 0.
    assert_eq!((63 + 64 + 1) as u32, ocean_heights[0]);
    assert_eq!((63 + 64 + 1) as u32, surface_heights[0]);
    assert_eq!(0, ocean_heights[4]);
    assert!(fill.post_process().is_empty());
}

#[test]
fn writes_outside_the_sections_and_unknown_states_are_rejected() {
    let flags = [FLAG_AIR, FLAG_BLOCKS_MOTION];
    let picker = Picker { lava_below: -54, lava_level: -54, lava: 1, fluid_level: 63, fluid: 1 };
    let substance = Substance::AquiferBatch { picker, skip_above: 1000, fluid_is_lava: false, lava_default: 1 };
    let mut fill = NoiseFill::new(config(substance), &flags);
    let density = vec![-1.0; 128];
    let mut materials = vec![-1; 256];
    let cell = |y, materials: &Vec<i32>| Cell { x: 0, y, z: 0, density: &density, materials: materials.clone().leak(),
        toggle: Corners([0.0; 8]), ridged_a: Corners([0.0; 8]), ridged_b: Corners([0.0; 8]), gap: std::ptr::null() };
    assert_eq!(Err(Error::OutOfChunk), fill.fill_cell(&cell(400, &materials)));
    materials[0] = 9;
    assert_eq!(Err(Error::UnknownState(9)), fill.fill_cell(&cell(0, &materials)));
}

#[test]
fn interpolation_reaches_each_corner() {
    let corners = Corners([1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0]);
    // Order 000, 100, 010, 110, 001, 101, 011, 111 with (dy, dx, dz).
    assert_eq!(1.0, corners.value(0.0, 0.0, 0.0));
    assert_eq!(2.0, corners.value(0.0, 1.0, 0.0));
    assert_eq!(3.0, corners.value(1.0, 0.0, 0.0));
    assert_eq!(5.0, corners.value(0.0, 0.0, 1.0));
    assert_eq!(8.0, corners.value(1.0, 1.0, 1.0));
}

#[test]
fn random_sources_follow_java_rules() {
    // Xoroshiro128PlusPlus replaces an all-zero seed.
    let mut zero = Xoroshiro::new(0, 0);
    let mut replaced = Xoroshiro::new(-7_046_029_254_386_353_131, 7_640_891_576_956_012_809);
    assert_eq!(replaced.next_long(), zero.next_long());
    // java.util.Random(0).nextInt() uses the same 48-bit LCG and scrambling.
    let mut legacy = Legacy::new(0);
    assert_eq!(-1_155_484_576, legacy.next(32));
    // Power-of-two bounds take the high bits; others stay in range.
    let mut legacy = Legacy::new(42);
    for bound in [1, 2, 8, 10, 9, 1 << 30, i32::MAX] {
        let value = legacy.next_int_bound(bound);
        assert!((0..bound).contains(&value));
    }
    let factory = Positional::Xoroshiro { lo: 3, hi: 4 };
    let (mut a, mut b) = (factory.at(1, 2, 3), factory.at(1, 2, 3));
    assert_eq!(a.next_float(), b.next_float());
    assert!((0.0..1.0).contains(&a.next_float()));
}

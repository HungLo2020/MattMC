use super::export::{decode, FORMAT};
use super::*;

fn facts(flags: u16, light_block: u8) -> StateFacts {
    StateFacts { flags: StateFlags(flags), light_block, ..StateFacts::default() }
}

/// air; stone; a crop (age 0..3); stairs-like (facing × half × waterlogged).
fn sample() -> BlockRegistry {
    let mut b = Builder::new();
    let age = b.property("age", &["0", "1", "2", "3"]).unwrap();
    let facing = b.property("facing", &["north", "south", "west", "east"]).unwrap();
    let half = b.property("half", &["top", "bottom"]).unwrap();
    let wet = b.property("waterlogged", &["true", "false"]).unwrap();
    b.block("minecraft:air", &[], 0, vec![facts(1, 0)]).unwrap();
    b.block("minecraft:stone", &[], 0, vec![facts(2, 15)]).unwrap();
    let wheat = b.block("minecraft:wheat", &[age], 0, (0..4).map(|a| StateFacts { offset: OffsetType::Xz, ..facts(8, a) }).collect()).unwrap();
    b.max_offsets(wheat, 0.5, 0.125).unwrap();
    // Waterlogged ("true", the first value) stairs hold a still water source.
    b.block("minecraft:oak_stairs", &[facing, half, wet], 3, (0..16)
        .map(|i| if i % 2 == 0 { StateFacts { fluid_state: FluidStateId(18), ..facts(2, 0) } } else { facts(2, 0) })
        .collect()).unwrap();
    b.finish(1, vec![0]).unwrap()
}

#[test]
fn ids_are_contiguous_per_block() {
    let r = sample();
    assert_eq!(r.state_count(), 22);
    assert_eq!(r.air(), Some(BlockId(0)));
    assert_eq!(r.by_name("minecraft:wheat"), Some(BlockId(2)));
    assert_eq!(r.block(BlockId(2)).states().map(|s| s.0).collect::<Vec<_>>(), vec![2, 3, 4, 5]);
    assert_eq!(r.block(BlockId(3)).state_range(), 6..22);
    assert_eq!(r.block(BlockId(3)).default_state(), StateId(9));
    assert_eq!(r.block_of(StateId(21)), BlockId(3));
    assert_eq!(r.light_block(StateId(1)), 15);
    assert_eq!(r.light_block(StateId(4)), 2);
    assert_eq!((r.offset(StateId(3)), r.block(BlockId(2)).max_horizontal_offset(), r.block(BlockId(2)).max_vertical_offset()),
        (OffsetType::Xz, 0.5, 0.125));
    assert_eq!((r.block(BlockId(1)).max_horizontal_offset(), r.block(BlockId(1)).max_vertical_offset()), (0.25, 0.2));
    assert_eq!((r.fluid(StateId(6)), r.fluid_height(StateId(6))), (FluidKind::Water, 8.0 / 9.0));
    assert_eq!((r.fluid(StateId(7)), r.fluid_height(StateId(7))), (FluidKind::None, 0.0));
}

#[test]
fn the_last_property_varies_fastest() {
    let r = sample();
    let (facing, half, wet) = (PropertyId(1), PropertyId(2), PropertyId(3));
    // StateDefinition expands sorted properties with the last innermost.
    let mut expected = Vec::new();
    for f in 0..4 {
        for h in 0..2 {
            for w in 0..2 {
                expected.push((f, h, w));
            }
        }
    }
    for (i, &(f, h, w)) in expected.iter().enumerate() {
        let s = StateId(6 + i as u16);
        assert_eq!((r.value(s, facing), r.value(s, half), r.value(s, wet)), (Some(f), Some(h), Some(w)));
        assert_eq!(r.state(BlockId(3), &[f, h, w]), Some(s));
    }
}

#[test]
fn with_value_changes_only_that_property() {
    let r = sample();
    let (age, facing, half, wet) = (PropertyId(0), PropertyId(1), PropertyId(2), PropertyId(3));
    let s = r.state(BlockId(3), &[2, 1, 0]).unwrap();
    assert_eq!(r.with_value(s, facing, 3), r.state(BlockId(3), &[3, 1, 0]));
    assert_eq!(r.with_value(s, half, 0), r.state(BlockId(3), &[2, 0, 0]));
    assert_eq!(r.with_value(s, wet, 1), r.state(BlockId(3), &[2, 1, 1]));
    assert_eq!(r.with_value(s, wet, 2), None);
    assert_eq!(r.with_value(s, age, 0), None);
    assert_eq!(r.value(StateId(4), age), Some(2));
    assert_eq!(r.with_value(StateId(4), age, 3), Some(StateId(5)));
    assert_eq!(r.value(StateId(1), age), None);
    assert_eq!(r.state(BlockId(3), &[0, 0]), None);
    assert_eq!(r.state(BlockId(9), &[]), None);
}

#[test]
fn builder_rejects_inconsistent_input() {
    let mut b = Builder::new();
    let p = b.property("p", &["a", "b"]).unwrap();
    assert!(b.property("q", &[]).is_err());
    assert!(b.block("x", &[p], 0, vec![facts(0, 0)]).is_err());
    assert!(b.block("x", &[p], 2, vec![facts(0, 0), facts(0, 0)]).is_err());
    assert!(b.block("x", &[p, p], 0, vec![facts(0, 0); 4]).is_err());
    assert!(b.block("x", &[PropertyId(7)], 0, vec![]).is_err());
    b.block("x", &[p], 1, vec![facts(0, 0), facts(0, 0)]).unwrap();
    b.block("x", &[], 0, vec![facts(0, 0)]).unwrap();
    assert_eq!(b.finish(1, vec![0]), Err(Error::Invalid("duplicate block name")));

    let mut b = Builder::new();
    b.block("x", &[], 0, vec![facts(0, 16)]).unwrap();
    assert!(b.finish(1, vec![0]).is_err());
    let mut b = Builder::new();
    b.block("x", &[], 0, vec![StateFacts { light_faces: [FaceId(1); 6], ..facts(0, 0) }]).unwrap();
    assert!(b.finish(1, vec![0]).is_err());
    let mut b = Builder::new();
    b.block("x", &[], 0, vec![facts(1 << 15, 0)]).unwrap();
    assert!(b.finish(1, vec![0]).is_err());
    let mut b = Builder::new();
    b.block("x", &[], 0, vec![facts(0, 0)]).unwrap();
    assert!(b.finish(2, vec![0, 1, 1]).is_err());
    let mut b = Builder::new();
    b.block("x", &[], 0, vec![facts(0, 0)]).unwrap();
    assert!(b.finish(1, vec![2]).is_err());
}

#[test]
fn the_state_ceiling_is_enforced() {
    let mut b = Builder::new();
    let p = b.property("p", &(0..256).map(|i| i.to_string()).collect::<Vec<_>>().iter().map(String::as_str).collect::<Vec<_>>()).unwrap();
    let q = b.property("q", &(0..256).map(|i| i.to_string()).collect::<Vec<_>>().iter().map(String::as_str).collect::<Vec<_>>()).unwrap();
    assert_eq!(b.block("x", &[p, q], 0, vec![]), Err(Error::Invalid("state count")));
}

/// Remaining state facts for the entire native declaration table, with no
/// layout/default/name input. This fixture deliberately uses empty fluid facts.
fn native_fact_packet() -> (Vec<i32>, Vec<u8>) {
    let mut ints = vec![FORMAT, 1235, 31809, 1];
    ints.extend(std::iter::repeat_n(0, 1235 * 2 + 31809 * (DIRECTIONS + 2)));
    (ints, vec![0; 31809 * 3 + 1])
}

#[test]
fn native_fact_install_uses_declared_layouts_and_shared_schemas() {
    use crate::content::property::Builtin;
    let (ints, bytes) = native_fact_packet();
    let r = decode(&ints, &bytes).unwrap();
    assert_eq!(r.state_count(), 31809);
    assert_eq!(r.blocks().len(), 1235);
    assert_eq!(r.air(), Some(BlockId(0)));
    assert!(r.flags(StateId(0)).contains(StateFlags::AIR));
    assert!(!r.flags(StateId(0)).contains(StateFlags::CAN_OCCLUDE));
    assert!(r.flags(StateId(1)).contains(StateFlags::CAN_OCCLUDE));
    assert!(!r.flags(StateId(1)).contains(StateFlags::AIR));
    assert_eq!(r.by_name("minecraft:stone"), Some(BlockId(1)));
    assert_eq!(r.block(BlockId(1)).default_state(), StateId(1));
    let leaves = r.by_name("minecraft:oak_leaves").unwrap();
    let default = r.block(leaves).default_state();
    for p in r.block(leaves).properties() {
        let property = r.property(p);
        let value = &property.values()[r.value(default, p).unwrap() as usize];
        assert_eq!(value, match property.name() { "distance" => "7", "persistent" | "waterlogged" => "false", _ => panic!("unexpected leaf property") });
    }
    assert!(r.properties().iter().any(|p| Arc::ptr_eq(p, &Builtin::Lit.definition().schema)));
}

#[test]
fn native_fact_export_rejects_damage() {
    let (ints, bytes) = native_fact_packet();
    assert!(decode(&ints[..ints.len() - 1], &bytes).is_err());
    assert!(decode(&ints, &bytes[..bytes.len() - 1]).is_err());
    let mut extra = ints.clone(); extra.push(0);
    assert!(decode(&extra, &bytes).is_err());
    for (at, value) in [(0, FORMAT - 1), (1, 1234), (2, 31808), (3, -1)] {
        let mut damaged = ints.clone(); damaged[at] = value;
        assert!(decode(&damaged, &bytes).is_err());
    }
    for invalid in [-1, 37, 65535, 65536] {
        let mut damaged = ints.clone();
        *damaged.last_mut().unwrap() = invalid;
        assert!(decode(&damaged, &bytes).is_err());
    }
    for flag in [StateFlags::HAS_FLUID, StateFlags::FLUID_FALLING, StateFlags::AIR, StateFlags::CAN_OCCLUDE] {
        let mut damaged = ints.clone();
        let at = damaged.len() - 2; damaged[at] = flag.0 as i32;
        assert!(decode(&damaged, &bytes).is_err());
    }
}

#[test]
fn all_fluid_associations_derive_facts_without_a_java_round_trip() {
    let mut b = Builder::new();
    for id in 0..37 {
        b.block(&format!("fixture:fluid_{id}"), &[], 0, vec![StateFacts {
            fluid_state: FluidStateId(id), ..StateFacts::default()
        }]).unwrap();
    }
    let r = b.finish(1, vec![0]).unwrap();
    for id in 0..37 {
        let state = StateId(id);
        let source = matches!(id, 17 | 18 | 35 | 36);
        let amount = if id == 0 { 0 } else if source { 8 } else if id < 17 { (id - 1) % 8 + 1 } else { (id - 19) % 8 + 1 };
        let falling = matches!(id, 1..=8 | 17 | 19..=26 | 35);
        assert_eq!(r.fluid_state(state), FluidStateId(id));
        assert_eq!(r.fluid(state), if id == 0 { FluidKind::None } else if id <= 18 { FluidKind::Water } else { FluidKind::Lava });
        assert_eq!(r.fluid_height(state).to_bits(), (amount as f32 / 9.0).to_bits());
        assert_eq!(r.flags(state).contains(StateFlags::HAS_FLUID), id != 0);
        assert_eq!(r.flags(state).contains(StateFlags::FLUID_FALLING), falling);
    }

}

#[test]
fn block_definitions_cannot_override_native_fluid_flags() {
    for flag in [StateFlags::HAS_FLUID, StateFlags::FLUID_FALLING] {
        let mut b = Builder::new();
        b.block("fixture:contradiction", &[], 0, vec![facts(flag.0, 0)]).unwrap();
        assert_eq!(b.finish(1, vec![0]), Err(Error::Invalid("state facts")));
    }
}

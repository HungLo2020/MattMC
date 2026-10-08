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

/// Encodes a registry the way `NativeBlockRegistry` exports Java's.
fn export(r: &BlockRegistry) -> (Vec<i32>, Vec<u16>, Vec<u8>) {
    let (mut ints, mut chars, mut bytes) = (vec![FORMAT, r.properties().len() as i32, r.blocks().len() as i32, r.state_count() as i32,
        r.face_count() as i32], Vec::new(), Vec::new());
    let mut put = |s: &str, ints: &mut Vec<i32>| {
        let units: Vec<u16> = s.encode_utf16().collect();
        ints.push(units.len() as i32);
        chars.extend(units);
    };
    for p in r.properties() {
        ints.push(-1);
        put(p.name(), &mut ints);
        ints.push(p.values().len() as i32);
        for v in p.values() {
            put(v, &mut ints);
        }
    }
    for b in r.blocks() {
        put(b.name(), &mut ints);
        ints.push((b.default_state().0 - b.state_range().start as u16) as i32);
        ints.push(b.max_horizontal_offset().to_bits() as i32);
        ints.push(b.max_vertical_offset().to_bits() as i32);
        ints.push(b.properties().len() as i32);
        ints.extend(b.properties().map(|p| p.0 as i32));
    }
    for b in r.blocks() {
        for s in b.states() {
            ints.extend(b.properties().map(|p| r.value(s, p).unwrap() as i32));
        }
    }
    for s in 0..r.state_count() {
        let s = StateId(s as u16);
        ints.extend((0..DIRECTIONS).map(|d| r.light_face(s, d).0 as i32));
        ints.push((r.flags(s).0 & !(StateFlags::HAS_FLUID.0 | StateFlags::FLUID_FALLING.0)) as i32);
        ints.push(r.fluid_state(s).0 as i32);
        bytes.extend([r.light_block(s), r.emission(s), r.offset(s) as u8]);
    }
    bytes.extend_from_slice(r.face_matrix());
    (ints, chars, bytes)
}

#[test]
fn export_round_trips() {
    let r = sample();
    let (ints, chars, bytes) = export(&r);
    assert_eq!(decode(&ints, &chars, &bytes), Ok(r));
}

#[test]
fn export_rejects_damage() {
    let (ints, chars, bytes) = export(&sample());
    assert!(decode(&ints[..ints.len() - 1], &chars, &bytes).is_err());
    assert!(decode(&ints, &chars[..chars.len() - 1], &bytes).is_err());
    assert!(decode(&ints, &chars, &bytes[..bytes.len() - 1]).is_err());
    let mut more = ints.clone();
    more.push(0);
    assert!(decode(&more, &chars, &bytes).is_err());
    let mut format = ints.clone();
    format[0] = FORMAT + 1;
    assert!(decode(&format, &chars, &bytes).is_err());
    // A value index that is not the arithmetic layout (two stair states swapped).
    let mut swapped = ints.clone();
    let values_at = ints.len() - 22 * (DIRECTIONS + 2) - (16 * 3 + 4);
    let first_stairs = values_at + 4;
    swapped.swap(first_stairs + 2, first_stairs + 5);
    assert_eq!(decode(&swapped, &chars, &bytes), Err(Error::Invalid("export state layout")));
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
    let (ints, chars, bytes) = export(&r);
    assert_eq!(decode(&ints, &chars, &bytes), Ok(r));
    for invalid in [-1, 37, 65535, 65536] {
        let mut damaged = ints.clone();
        *damaged.last_mut().unwrap() = invalid;
        assert!(decode(&damaged, &chars, &bytes).is_err());
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

#[test]
fn exported_native_properties_share_the_owner_and_keep_local_identity() {
    use crate::content::property::Builtin;
    let mut b = Builder::new();
    let lit = b.property_shared(Builtin::Lit.definition().schema.clone()).unwrap();
    b.block("fixture:lit", &[lit], 0, vec![StateFacts::default(); 2]).unwrap();
    let r = b.finish(1, vec![0]).unwrap();
    let (mut ints, mut chars, bytes) = export(&r);
    // Replace the custom-property export with the single native declaration ID.
    ints.splice(5..10, [Builtin::Lit as i32]);
    chars.drain(.."littruefalse".len());
    let imported = decode(&ints, &chars, &bytes).unwrap();
    assert_eq!(imported, r);
    assert!(Arc::ptr_eq(&imported.properties()[0], &Builtin::Lit.definition().schema));
    for invalid in [-2, 134, 65536] {
        ints[5] = invalid;
        assert_eq!(decode(&ints, &chars, &bytes), Err(Error::Invalid("native property id")));
    }
}

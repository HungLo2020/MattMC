use super::*;

fn u(s: &str) -> Vec<u16> {
    units(s)
}

#[test]
fn java_string_hashes() {
    // Values of String.hashCode().
    assert_eq!(java_hash(&u("data")), 3076010);
    assert_eq!(java_hash(&u("Y")), 89);
    assert_eq!(java_hash(&u("")), 0);
}

#[test]
fn hash_map_order_is_by_bucket_then_insertion() {
    // Buckets (hash ^ hash >>> 16) & 15 of the section keys.
    let keys = [u("block_states"), u("biomes"), u("BlockLight"), u("SkyLight"), u("Y")];
    let refs: Vec<&[u16]> = keys.iter().map(|k| k.as_slice()).collect();
    let order = hash_map_order(&refs);
    let mut sorted = order.clone();
    sorted.sort();
    assert_eq!(sorted, vec![0, 1, 2, 3, 4]);
    let bucket = |k: &[u16]| {
        let h = java_hash(k);
        (h ^ ((h as u32) >> 16) as i32) & 15
    };
    for pair in order.windows(2) {
        let (a, b) = (bucket(refs[pair[0]]), bucket(refs[pair[1]]));
        assert!(a < b || (a == b && pair[0] < pair[1]));
    }
}

#[test]
fn compact_keeps_first_occurrence_and_merges_equal_labels() {
    // Four-bit words: ids 2, 1, 2, 0 then zeros; ids 0 and 2 share a label.
    let mut words = vec![0u64; 256];
    words[0] = 2 | 1 << 4 | 2 << 8;
    let container = Container { bits: 4, words: &words, labels: &[7, 3, 7] };
    let (mut lookup, mut order, mut indices) = (Vec::new(), Vec::new(), Vec::new());
    compact(&container, 4096, &mut lookup, &mut order, &mut indices).unwrap();
    assert_eq!(order, vec![2, 1]);
    assert_eq!(&indices[..5], &[0, 1, 0, 0, 0]);
    assert!(lookup.iter().all(|&v| v == -1));
}

#[test]
fn rejected_container_leaves_the_lookup_clean_for_the_next_one() {
    // Ids 0 and 1 are valid, then id 5 is outside the two-entry palette.
    let mut bad = vec![0u64; 256];
    bad[0] = 0 | 1 << 4 | 5 << 8;
    let (mut lookup, mut order, mut indices) = (Vec::new(), Vec::new(), Vec::new());
    let rejected = Container { bits: 4, words: &bad, labels: &[10, 11] };
    assert_eq!(compact(&rejected, 4096, &mut lookup, &mut order, &mut indices), Err(Error::Unsupported));
    assert!(lookup.iter().all(|&v| v == -1), "a rejection must not leave labels marked as seen");
    // The next container on this lookup sees both labels fresh, in its own order.
    let mut good = vec![0u64; 256];
    good[0] = 1 | 0 << 4;
    let valid = Container { bits: 4, words: &good, labels: &[10, 11] };
    compact(&valid, 4096, &mut lookup, &mut order, &mut indices).unwrap();
    assert_eq!(order, vec![1, 0]);
    assert_eq!(&indices[..3], &[0, 1, 1]);
}

#[test]
fn block_storage_bits_follow_the_block_strategy() {
    use super::vocabulary::block_storage_bits;
    let expected = |size: usize| -> u8 {
        match size {
            0 | 1 => 0,
            2..=16 => 4,
            17..=32 => 5,
            33..=64 => 6,
            65..=128 => 7,
            129..=256 => 8,
            _ => (size as f64).log2().ceil() as u8,
        }
    };
    for size in 0..=4096 {
        assert_eq!(block_storage_bits(size), expected(size), "size {size}");
    }
}

#[test]
fn vocabulary_fragments_omit_default_properties_and_keep_hash_order() {
    use crate::content::block::{Builder, FaceId, StateFacts, StateFlags, StateId};
    let facts = StateFacts { flags: StateFlags(0), light_block: 0, emission: 0, light_faces: [FaceId(0); 6] };
    let mut b = Builder::new();
    let lit = b.property("lit", &["true", "false"]).unwrap();
    let facing = b.property("facing", &["north", "south"]).unwrap();
    b.block("minecraft:stone", &[], 0, vec![facts]).unwrap();
    // Properties in name order: facing, lit. Default is facing=north, lit=false.
    b.block("minecraft:furnace", &[facing, lit], 1, vec![facts; 4]).unwrap();
    let registry = b.finish(1, vec![0]).unwrap();
    let v = super::vocabulary::vocabulary(&registry).unwrap();
    assert_eq!(v.labels, vec![0, 1, 2, 3, 4]);
    let fragment = |s: usize| &v.fragments[v.offsets[s] as usize..v.offsets[s + 1] as usize];

    let string = |tape: &mut Tape, key: &str, value: &str| {
        tape.record(TAG_STRING, 0, &u(key), value.len(), 0);
        for unit in u(value) {
            tape.out.extend_from_slice(&unit.to_le_bytes());
        }
    };
    let mut stone = Tape { out: Vec::new() };
    stone.record(TAG_COMPOUND, 0, &[], 1, 0);
    string(&mut stone, "Name", "minecraft:stone");
    assert_eq!(fragment(0), stone.out.as_slice());
    let mut default = Tape { out: Vec::new() };
    default.record(TAG_COMPOUND, 0, &[], 1, 0);
    string(&mut default, "Name", "minecraft:furnace");
    assert_eq!(fragment(2), default.out.as_slice());
    // facing=north, lit=true: {Name, Properties{facing, lit}} in HashMap order.
    let outer = hash_map_order(&[&u("Properties"), &u("Name")]);
    let inner = hash_map_order(&[&u("lit"), &u("facing")]);
    let mut lit_north = Tape { out: Vec::new() };
    lit_north.record(TAG_COMPOUND, 0, &[], 2, 0);
    for i in outer {
        if i == 1 {
            string(&mut lit_north, "Name", "minecraft:furnace");
        } else {
            lit_north.record(TAG_COMPOUND, 0, &u("Properties"), 2, 0);
            for j in &inner {
                if *j == 0 { string(&mut lit_north, "lit", "true") } else { string(&mut lit_north, "facing", "north") }
            }
        }
    }
    assert_eq!(registry.state(registry.by_name("minecraft:furnace").unwrap(), &[0, 0]), Some(StateId(1)));
    assert_eq!(fragment(1), lit_north.out.as_slice());
}

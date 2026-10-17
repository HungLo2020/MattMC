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
    let facts = StateFacts { flags: StateFlags(0), light_block: 0, emission: 0, light_faces: [FaceId(0); 6], ..StateFacts::default() };
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

/// #818: exercise the real thread-local FFI encoder, including block/biome
/// scratch reuse and the caller's insufficient-output-buffer retry protocol.
#[test]
fn repeated_ffi_rejections_zero_bit_interlude_and_output_retry_recover() {
    use crate::content::block::{Builder, FaceId, StateFacts, StateFlags};
    let facts = StateFacts {
        flags: StateFlags(0), light_block: 0, emission: 0, light_faces: [FaceId(0); 6],
        ..StateFacts::default()
    };
    let mut builder = Builder::new();
    builder.block("minecraft:stone", &[], 0, vec![facts]).unwrap();
    builder.block("minecraft:dirt", &[], 0, vec![facts]).unwrap();
    let registry = builder.finish(1, vec![0]).unwrap();
    let vocabulary = super::vocabulary::vocabulary(&registry).unwrap();
    let names = [u("minecraft:plains"), u("minecraft:desert")];
    let chars: Vec<u16> = names.iter().flatten().copied().collect();
    let biome_bits = [0u8, 0, 1];
    let biomes = Biomes { names: names.iter().map(Vec::as_slice).collect(), bits: &biome_bits };
    let mut block_words = vec![0u64; 256];
    block_words[0] = 1; // first-occurrence labels 1, then 0
    let biome_words = [1u64, 0];
    let valid = Section {
        y: 0,
        states: Some((Container { bits: 4, words: &block_words, labels: &[0, 1] },
            Container { bits: 2, words: &biome_words, labels: &[0, 1] })),
        block_light: None, sky_light: None,
    };
    let zero = Section {
        y: 0,
        states: Some((Container { bits: 0, words: &[], labels: &[0] },
            Container { bits: 0, words: &[], labels: &[0] })),
        block_light: None, sky_light: None,
    };
    let mut expected = Tape { out: Vec::new() };
    Encoder::new().encode(&vocabulary, &biomes, &[valid], &mut expected).unwrap();
    let mut expected_zero = Tape { out: Vec::new() };
    Encoder::new().encode(&vocabulary, &biomes, &[zero], &mut expected_zero).unwrap();
    // These named-record fixtures come from clean SerializableChunkData.write()
    // plus Java TapeWriter; Java also rechecks them against its native bridge.
    let java_valid = include_bytes!("../../../../test/resources/storage/chunk-recovery-valid-sections.bin");
    let java_zero = include_bytes!("../../../../test/resources/storage/chunk-recovery-zero-sections.bin");
    assert_eq!(expected.out.as_slice(), java_valid.as_slice());
    assert_eq!(expected_zero.out.as_slice(), java_zero.as_slice());
    let input = |zero: bool| {
        let mut ints = vec![1, 2, names[0].len() as i32, names[1].len() as i32, 0, 1, 0];
        if zero {
            ints.extend_from_slice(&[0, 1, 0, 0, 0, 1, 0, 0]);
        } else {
            ints.extend_from_slice(&[4, 2, 256, 0, 1, 2, 2, 2, 0, 1]);
        }
        ints
    };
    let ints = input(false);
    let zero_ints = input(true);
    let longs: Vec<i64> = block_words.iter().chain(biome_words.iter()).map(|&word| word as i64).collect();
    let call = |ints: &[i32], longs: &[i64], out: &mut [u8], out_len: &mut i64| unsafe {
        super::ffi::mattmc_chunk_sections_encode(
            &vocabulary as *const Vocabulary as u64, ints.as_ptr(), ints.len() as i32,
            longs.as_ptr(), longs.len() as i32, std::ptr::null(), 0,
            chars.as_ptr(), chars.len() as i32, biome_bits.as_ptr(), biome_bits.len() as i32,
            out.as_mut_ptr(), out.len() as i64, out_len,
        )
    };
    for round in 0..3 {
        for corrupt_biomes in [false, true] {
            let mut bad = longs.clone();
            if corrupt_biomes {
                bad[256] = 0 | 1 << 2 | 3 << 4; // valid labels, then missing palette id
            } else {
                bad[0] = 0 | 1 << 4 | 5 << 8;
            }
            let mut guard = vec![0xa5; expected.out.len()];
            let mut length = -1;
            for rejection in 0..2 {
                assert_eq!(call(&ints, &bad, &mut guard, &mut length), -2,
                    "round {round}, corrupt_biomes={corrupt_biomes}, rejection {rejection}");
                assert!(guard.iter().all(|&byte| byte == 0xa5));
            }
            let mut zero_out = vec![0; expected_zero.out.len()];
            assert_eq!(call(&zero_ints, &[], &mut zero_out, &mut length), 0);
            assert_eq!(zero_out, expected_zero.out);
            assert_eq!(length as usize, expected_zero.out.len());
            let mut too_small = [0xa5];
            assert_eq!(call(&ints, &longs, &mut too_small, &mut length), 1);
            assert_eq!(too_small, [0xa5]);
            assert_eq!(length as usize, expected.out.len());
            let mut recovered = vec![0; length as usize];
            assert_eq!(call(&ints, &longs, &mut recovered, &mut length), 0);
            assert_eq!(recovered, expected.out, "round {round}, corrupt_biomes={corrupt_biomes}");
        }
    }
}

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

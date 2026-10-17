/// `seen` starts zero and is restored before returning. Output is insertion
/// order, even when different IDs resolve to the same Java object or null.
pub(super) fn scan(words: &[u64], bits: usize, size: usize, seen: &mut [u64], out: &mut [u32]) -> usize {
    if bits == 0 { out[0] = 0; return 1; }
    let per = 64 / bits;
    let mask = (1u64 << bits) - 1;
    let domain = 1usize << bits;
    let mut count = 0;
    let mut small = 0u64;
    let mut at = 0;
    for &packed in words {
        let mut word = packed;
        let fields = per.min(size - at);
        for _ in 0..fields {
            let id = (word & mask) as usize;
            let bit = 1u64 << (id & 63);
            let visited = if bits <= 6 { &mut small } else { &mut seen[id >> 6] };
            if *visited & bit == 0 {
                *visited |= bit;
                out[count] = id as u32;
                count += 1;
                // Once every representable ID has occurred, later fields
                // cannot add a value to the original insertion-ordered set.
                if count == domain { break; }
            }
            word >>= bits;
        }
        at += fields;
        if count == domain || at == size { break; }
    }
    if bits > 6 {
        for &id in &out[..count] { seen[id as usize >> 6] = 0; }
    }
    count
}

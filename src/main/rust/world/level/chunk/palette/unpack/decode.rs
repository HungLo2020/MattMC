pub(super) fn decode(
    words: &[u64],
    bits: usize,
    count: usize,
    lookup: &mut [u32],
    out: &mut [u32],
) -> Result<usize, ()> {
    let (order, indices) = out.split_at_mut(4096);
    let per = 64 / bits;
    let mask = (1u64 << bits) - 1;
    let mut used = 0;
    let mut at = 0;
    let result = (|| {
        for &word in words {
            let mut value = word;
            for _ in 0..per.min(4096 - at) {
                let id = (value & mask) as usize;
                if id >= count {
                    return Err(());
                }
                let slot = &mut lookup[id];
                if *slot == 0 {
                    order[used] = id as u32;
                    used += 1;
                    *slot = used as u32;
                }
                indices[at] = *slot - 1;
                value >>= bits;
                at += 1;
            }
        }
        if at != 4096 {
            return Err(());
        }
        Ok(used)
    })();
    // Restore every touched slot even on malformed input. Workspace is zero
    // on entry/exit; no output or mapping is reused between calls.
    for &id in &order[..used] {
        lookup[id as usize] = 0;
    }
    result
}

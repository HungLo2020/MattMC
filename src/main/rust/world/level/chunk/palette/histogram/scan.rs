pub(super) const DENSE: usize = 65536;
pub(super) const TABLE: usize = 8192;
pub(crate) const WORK: usize = DENSE + 2 * TABLE;

fn insert(keys: &mut [u32], id: u32) {
    let hash = id.wrapping_mul(0x9e3779b9);
    let mask = keys.len() - 1;
    let mut pos = (hash ^ (hash >> 16)) as usize & mask;
    while keys[pos] != 0 {
        pos = (pos + 1) & mask;
    }
    keys[pos] = id;
}

/// The dense prefix starts zero and is restored to zero before returning.
/// Remaining workspace and output contents need no initialization.
pub(crate) fn scan(words: &[u64], bits: usize, work: &mut [u32], out: &mut [u64]) -> usize {
    if bits == 0 {
        out[0] = 4096u64 << 32;
        return 1;
    }
    let per = 64 / bits;
    let mask = (1u64 << bits) - 1;
    // Stale palettes often retain many entries after the section becomes
    // uniform. Check packed words directly, ignoring all unused padding bits.
    let id = words[0] & mask;
    let used_mask = u64::MAX >> (64 - per * bits);
    let repeated = id * (used_mask / mask);
    let full = 4096 / per;
    if words[..full]
        .iter()
        .all(|word| word & used_mask == repeated)
    {
        let remaining = 4096 % per;
        let uniform = remaining == 0 || {
            let tail_mask = u64::MAX >> (64 - remaining * bits);
            words[full] & tail_mask == repeated & tail_mask
        };
        if uniform {
            out[0] = (4096u64 << 32) | id;
            return 1;
        }
    }
    let (dense, tables) = work.split_at_mut(DENSE);
    let (mut keys, mut temporary) = tables.split_at_mut(TABLE);
    let mut unique = 0;
    let mut at = 0;
    for word in words {
        let mut word = *word;
        for _ in 0..per.min(4096 - at) {
            let id = (word & mask) as usize;
            if dense[id] == 0 {
                out[unique] = id as u64;
                unique += 1;
            }
            dense[id] += 1;
            word >>= bits;
            at += 1;
        }
    }

    // Reconstruct only key placement, once per distinct ID. Counting itself
    // does not hash every occurrence. Include zero in resize thresholds.
    let mut n = 32;
    keys[..n].fill(0);
    for i in 0..unique {
        let id = out[i] as u32;
        if id != 0 {
            insert(&mut keys[..n], id);
        }
        // addTo() rehashes after insertion when its old size >= maxFill.
        if i >= n * 3 / 4 {
            temporary[..n * 2].fill(0);
            for &old in keys[..n].iter().rev() {
                if old != 0 {
                    insert(&mut temporary[..n * 2], old);
                }
            }
            std::mem::swap(&mut keys, &mut temporary);
            n *= 2;
        }
    }

    // MapEntrySet.forEach(): zero first, then occupied buckets descending.
    let mut size = 0;
    if dense[0] != 0 {
        out[size] = (dense[0] as u64) << 32;
        dense[0] = 0;
        size += 1;
    }
    for &id in keys[..n].iter().rev() {
        if id != 0 {
            out[size] = id as u64 | ((dense[id as usize] as u64) << 32);
            dense[id as usize] = 0;
            size += 1;
        }
    }
    debug_assert_eq!(size, unique);
    size
}

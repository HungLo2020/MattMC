pub(super) const SIZE: usize = 4096;

/// Validate used source IDs and collect them in first-use order. No heap allocation.
pub(super) fn used(words: &[u64], bits: usize, count: usize, out: &mut [u32]) -> Result<usize, ()> {
    if bits == 0 {
        out[0] = 0;
        return Ok(1);
    }
    let mut seen = [false; 256];
    let (per, mask) = (64 / bits, (1u64 << bits) - 1);
    let (mut at, mut unique) = (0, 0);
    for &word in words {
        let mut word = word;
        for _ in 0..per.min(SIZE - at) {
            let id = (word & mask) as usize;
            if id >= count {
                return Err(());
            }
            if !seen[id] {
                seen[id] = true;
                out[unique] = id as u32;
                unique += 1;
            }
            word >>= bits;
            at += 1;
        }
    }
    Ok(unique)
}

/// Decode, map and encode every entry. Build each output word once; padding is zero.
pub(super) fn remap(words: &[u64], bits: usize, map: &[u32], target_bits: usize, out: &mut [u64]) {
    let target_per = 64 / target_bits;
    if bits == 0 {
        let id = map[0] as u64;
        let mask = u64::MAX >> (64 - target_per * target_bits);
        let repeated = id * (mask / ((1u64 << target_bits) - 1));
        out.fill(repeated);
        let remaining = SIZE % target_per;
        if remaining != 0 {
            *out.last_mut().unwrap() &= u64::MAX >> (64 - remaining * target_bits);
        }
        return;
    }
    let (per, mask) = (64 / bits, (1u64 << bits) - 1);
    let (mut at, mut output_at, mut field, mut packed) = (0, 0, 0, 0u64);
    for &word in words {
        let mut word = word;
        for _ in 0..per.min(SIZE - at) {
            packed |= (map[(word & mask) as usize] as u64) << (field * target_bits);
            field += 1;
            if field == target_per {
                out[output_at] = packed;
                output_at += 1;
                field = 0;
                packed = 0;
            }
            word >>= bits;
            at += 1;
        }
    }
    if field != 0 {
        out[output_at] = packed;
    }
}

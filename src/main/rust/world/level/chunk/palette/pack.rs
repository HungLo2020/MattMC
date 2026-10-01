pub(super) fn compact(
    words: &[u64],
    bits: usize,
    labels: &[u32],
    lookup: &mut [i32],
    out: &mut [u32],
) -> Result<usize, ()> {
    lookup.fill(-1);
    let (order, indices) = out.split_at_mut(4096);
    if bits == 0 {
        let label = *labels.first().ok_or(())? as usize;
        if label >= lookup.len() {
            return Err(());
        }
        order[0] = 0;
        indices[..4096].fill(0);
        return Ok(1);
    }
    let per = 64 / bits;
    let mask = (1u64 << bits) - 1;
    let mut count = 0;
    let mut at = 0;
    for word in words {
        let mut value = *word;
        for _ in 0..per.min(4096 - at) {
            let id = (value & mask) as usize;
            let label = *labels.get(id).ok_or(())? as usize;
            let slot = lookup.get_mut(label).ok_or(())?;
            if *slot == -1 {
                *slot = count as i32;
                order[count] = id as u32;
                count += 1;
            }
            indices[at] = *slot as u32;
            value >>= bits;
            at += 1;
        }
    }
    if at != 4096 {
        return Err(());
    }
    Ok(count)
}

pub(super) fn encode(
    indices: &[u32],
    bits: usize,
    words: &mut [u64],
    remap: Option<&[u32]>,
) -> Result<(), ()> {
    match remap {
        Some(map) => encode_with(indices, bits, words, |i| {
            map.get(i as usize).copied().ok_or(())
        }),
        None => encode_with(indices, bits, words, Ok),
    }
}

fn encode_with(
    indices: &[u32],
    bits: usize,
    words: &mut [u64],
    map: impl Fn(u32) -> Result<u32, ()>,
) -> Result<(), ()> {
    let per = 64 / bits;
    let mask = (1u64 << bits) - 1;
    // Build each word once. Full-word and final-word padding are always zero.
    for (word, values) in words.iter_mut().zip(indices.chunks(per)) {
        let mut packed = 0;
        for (i, value) in values.iter().enumerate() {
            packed |= (map(*value)? as u64 & mask) << (i * bits);
        }
        *word = packed;
    }
    Ok(())
}

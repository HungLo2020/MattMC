pub(super) fn clear(maps: &mut [u64], bits: usize) {
    let per_word = 64 / bits;
    let full_words = 256 / per_word;
    // Each complete word can also have padding above its packed fields.
    let used = per_word * bits;
    let fields = if used == 64 {
        u64::MAX
    } else {
        (1u64 << used) - 1
    };
    for word in &mut maps[..full_words] {
        *word &= !fields;
    }
    let tail = 256 % per_word;
    if tail != 0 {
        maps[full_words] &= !((1u64 << (tail * bits)) - 1);
    }
}

fn set_height(maps: &mut [u64], column: usize, bits: usize, value: u64) {
    let per_word = 64 / bits;
    let shift = column % per_word * bits;
    let field = ((1u64 << bits) - 1) << shift;
    maps[column / per_word] = (maps[column / per_word] & !field) | ((value << shift) & field);
}

pub(super) fn section(
    words: &[u64],
    flags: &[u32],
    edges: &[u8],
    header: &[i32],
    pending: &mut [u8],
    above: &mut [u32],
    maps: &mut [u64],
) -> Result<i32, ()> {
    let bits = header[0];
    let height_bits = header[1] as usize;
    let base = header[3];
    let min_source = header[4];
    let faces = header[6] as usize;
    let mut remaining = 0;
    // Original columns: Z outer, X inner. Each column carries its upper face
    // across section boundaries; an all-air section resets it without testing edges.
    for column in 0..256 {
        if pending[column] == 0 {
            continue;
        }
        if bits == -1 {
            above[column] = 0;
        } else {
            let per_word = if bits == 0 { 1 } else { 64 / bits as usize };
            let mask = if bits == 0 { 0 } else { (1u64 << bits) - 1 };
            let mut upper = above[column] as usize;
            if upper >= faces {
                return Err(());
            }
            for y in (0..16).rev() {
                let index = column + y * 256;
                let id = if bits == 0 {
                    0
                } else {
                    ((words[index / per_word] >> ((index % per_word) * bits as usize)) & mask)
                        as usize
                };
                let state = *flags.get(id).ok_or(())?;
                let up = ((state >> 1) & 32767) as usize;
                let down = (state >> 16) as usize;
                if up >= faces || down >= faces {
                    return Err(());
                }
                if state & 1 != 0 || edges[upper * faces + up] != 0 {
                    set_height(
                        maps,
                        column,
                        height_bits,
                        (base + y as i32 + 1 - min_source) as u64,
                    );
                    pending[column] = 0;
                    break;
                }
                upper = down;
            }
            above[column] = upper as u32;
        }
        if base == min_source + 1 && pending[column] != 0 {
            // Unlike Heightmap priming, an unblocked source resets to the sentinel.
            set_height(maps, column, height_bits, 0);
            pending[column] = 0;
        }
        remaining += pending[column] as i32;
    }
    Ok(remaining)
}

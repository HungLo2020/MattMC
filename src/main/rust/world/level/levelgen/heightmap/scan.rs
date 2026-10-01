pub(super) fn section(
    words: &[u64],
    bits: usize,
    flags: &[u32],
    header: &[i32],
    pending: &mut [u8],
    maps: &mut [u64],
) -> Result<i32, ()> {
    let height_bits = header[1] as usize;
    let stride = header[3] as usize;
    let base = header[4];
    let min_y = header[5];
    let max_y = header[6];
    let values_per_long = if bits == 0 { 1 } else { 64 / bits };
    let mask = if bits == 0 { 0 } else { (1u64 << bits) - 1 };
    let mut remaining = 0;
    // Original columns: X outer, Z inner. Keep exception/invalid-ID detection order.
    for x in 0..16 {
        for z in 0..16 {
            let column = x + z * 16;
            let mut need = pending[column];
            if need == 0 {
                continue;
            }
            for y in (0..16).rev() {
                let world_y = base + y as i32;
                if world_y < min_y || world_y >= max_y {
                    continue;
                }
                let index = x + z * 16 + y * 256;
                let id = if bits == 0 {
                    0
                } else {
                    ((words[index / values_per_long] >> ((index % values_per_long) * bits)) & mask)
                        as usize
                };
                let matches = *flags.get(id).ok_or(())? as u8 & need;
                if matches == 0 {
                    continue;
                }
                let height = (world_y + 1 - min_y) as u64;
                for map in 0..6 {
                    let bit = 1 << map;
                    if matches & bit != 0 {
                        let per_word = 64 / height_bits;
                        let at = map * stride + column / per_word;
                        let shift = (column % per_word) * height_bits;
                        let field = ((1u64 << height_bits) - 1) << shift;
                        maps[at] = (maps[at] & !field) | ((height << shift) & field);
                    }
                }
                need &= !matches;
                if need == 0 {
                    break;
                }
            }
            pending[column] = need;
            remaining += need.count_ones() as i32;
        }
    }
    Ok(remaining)
}

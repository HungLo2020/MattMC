//! Immutable loaded-section captures and bulk neighbourhood reads. No rendering dependencies.
mod ffi;
#[cfg(test)]
mod tests;

const ENTRIES: usize = 4096;
const PAD: usize = 18;

fn decode(
    words: &[u64],
    bits: usize,
    palette: &[i32],
    limit: usize,
) -> Option<Box<[u16; ENTRIES]>> {
    if bits > 16
        || limit == 0
        || limit > u16::MAX as usize
        || words.len()
            != if bits == 0 {
                0
            } else {
                ENTRIES.div_ceil(64 / bits)
            }
        || (bits == 0 && palette.len() != 1)
        || palette.len() > 257
    {
        return None;
    }
    let mut states = Box::new([0; ENTRIES]);
    let per_word = if bits == 0 { 0 } else { 64 / bits };
    let mask = (1u64 << bits) - 1;
    for (index, state) in states.iter_mut().enumerate() {
        let id = if bits == 0 {
            0
        } else {
            ((words[index / per_word] >> (index % per_word * bits)) & mask) as usize
        };
        let value = if palette.is_empty() {
            id as i32
        } else {
            *palette.get(id)?
        };
        if value < 0 || value as usize >= limit {
            return None;
        }
        *state = value as u16;
    }
    Some(states)
}

fn padded(sections: [Option<&[u16; ENTRIES]>; 27], air: u16, output: &mut [i32]) {
    for y in 0..PAD {
        for z in 0..PAD {
            for x in 0..PAD {
                let [rx, ry, rz] = [x + 15, y + 15, z + 15];
                let section = (ry / 16 * 3 + rz / 16) * 3 + rx / 16;
                let block = ((ry & 15) * 16 + (rz & 15)) * 16 + (rx & 15);
                output[(y * PAD + z) * PAD + x] =
                    sections[section].map_or(air, |s| s[block]) as i32;
            }
        }
    }
}

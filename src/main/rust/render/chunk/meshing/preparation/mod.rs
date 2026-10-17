//! Bulk terrain light preparation reads retained world generations directly.
//! Java supplies only the remaining contextual predicates/shade, not light
//! arrays or already packed mesher words. No input is retained across the call.
use crate::world::level::lighting::layers::View;

mod ffi;
#[cfg(test)]
mod tests;

const PAD: usize = 18;
const CELLS: usize = PAD * PAD * PAD;
const VIEWS: usize = 54;

/// First emissive, view blocking, full collision cube, second emissive.
#[repr(C)]
#[derive(Clone, Copy)]
struct Context {
    flags: u32,
    shade: f32,
}

#[derive(Clone, Copy)]
struct Facts {
    emission: i32,
    light_block: i32,
    full_opaque: bool,
}

fn word(flags: u32, luminance: i32, shade: f32, raw_block: i32, raw_sky: i32) -> i32 {
    let (block, sky) = if flags & 4 != 0 && luminance == 0 {
        (0, 0)
    } else if flags & 1 != 0 {
        (raw_block, raw_sky)
    } else if flags & 16 != 0 {
        (15, 15)
    } else {
        // Frozen packs the raw values first. Masking lazy defaults before this
        // point incorrectly drops carries between block and sky lanes.
        let packed = raw_block.wrapping_shl(4) | raw_sky.wrapping_shl(20);
        let block = (packed as u32 >> 4) as i32 & 15;
        if block < luminance {
            let sky = (packed as u32 >> 20) as i32 & 15;
            let boosted = luminance.wrapping_shl(4) | sky.wrapping_shl(20);
            ((boosted as u32 >> 4) as i32 & 15, (boosted as u32 >> 20) as i32 & 15)
        } else {
            (block, (packed as u32 >> 20) as i32 & 15)
        }
    };
    // Rust's saturating float cast has Java's NaN/infinity/out-of-range results.
    let ao = if luminance == 0 { shade } else { 1.0 };
    let ao_bits = (ao * 4096.0) as i32;
    (block & 15) | ((sky & 15) << 4) | ((luminance & 15) << 8)
        | ((ao_bits & 65535) << 12) | ((flags as i32 & 15) << 28)
}

fn prepare(views: [Option<&View>; VIEWS], ids: &[i32], contexts: &[Context],
    output: &mut [i32], facts: impl Fn(i32) -> Option<Facts>) -> Result<(), i32> {
    if ids.len() != CELLS || contexts.len() != CELLS || output.len() != CELLS { return Err(-1); }
    // Validate the whole semantic span before publishing any output. In
    // particular a custom state must decline before callbacks would be replayed.
    if contexts.iter().any(|context| context.flags & !15 != 0)
        || ids.iter().any(|id| facts(*id).is_none()) { return Err(-2); }
    for y in 0..PAD {
        for z in 0..PAD {
            for x in 0..PAD {
                let index = (y * PAD + z) * PAD + x;
                let context = contexts[index];
                let state = facts(ids[index]).expect("validated immutable state registry");
                let flags = (context.flags & 1)
                    | u32::from(context.flags & 2 != 0 && state.light_block != 0) << 1
                    | u32::from(state.full_opaque) << 2
                    | (context.flags & 4) << 1
                    | (context.flags & 8) << 1;
                let skip = (state.full_opaque && state.emission == 0)
                    || (flags & 1 == 0 && flags & 16 != 0);
                let [rx, ry, rz] = [x + 15, y + 15, z + 15];
                let section = (ry / 16 * 3 + rz / 16) * 3 + rx / 16;
                let cell = ((ry & 15) * 16 + (rz & 15)) * 16 + (rx & 15);
                let light = |kind: usize| if skip { 0 } else {
                    views[section * 2 + kind].map_or(0, |view|
                        view.get(cell as i32).expect("bounded local light cell"))
                };
                output[index] = word(flags, state.emission, context.shade, light(0), light(1));
            }
        }
    }
    Ok(())
}

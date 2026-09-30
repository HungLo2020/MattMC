//! Density program validation.
use super::super::synth::{noise_validate, State};
use super::program::{Header, Node};
/// # Safety
/// `base` denotes `bytes` readable bytes, aligned to eight bytes. Validation must
/// succeed before evaluation; the allocation must remain immutable and live.
pub(crate) unsafe fn density_validate(base: *const u8, bytes: u64) -> i32 {
    if base.is_null() || base as usize % 8 != 0 || bytes < 16 {
        return -1;
    }
    let h = unsafe { &*base.cast::<Header>() };
    if h.count == 0
        || h.count > 48
        || h.inputs > 4
        || h.cells > 8
        || (h.inputs != 0 && h.cells != 0)
        || bytes < 16 + 64 * h.count as u64
    {
        return -2;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    let mut parents = [0u8; 48];
    let mut work = 0;
    let mut visits = [0u64; 48];
    let mut expanded_noise = [0u64; 48];
    for (i, n) in nodes.iter().enumerate() {
        if n.op > 24
            || (n.op == 23 && n.a >= h.inputs)
            || (n.op == 24 && n.a >= h.cells)
            || ((h.inputs != 0 || h.cells != 0) && (1..=7).contains(&n.op))
        {
            return -3;
        }
        let children = match n.op {
            5 | 22 => 3,
            8..=11 => 2,
            6 | 7 | 12..=21 => 1,
            _ => 0,
        };
        visits[i] = 1;
        for child in [n.a, n.b, n.c].iter().take(children) {
            if *child as usize >= i {
                return -4;
            }
            parents[*child as usize] += 1;
            if h.cells != 0 && parents[*child as usize] > 1 {
                return -9;
            }
            visits[i] += visits[*child as usize];
            expanded_noise[i] += expanded_noise[*child as usize];
        }
        if n.offset != 0 {
            if !(1..=7).contains(&n.op)
                || n.offset < 16 + 64 * h.count as u64
                || n.offset % 8 != 0
                || n.bytes < 80
                || n.offset > bytes
                || n.bytes > bytes - n.offset
            {
                return -5;
            }
            let state = unsafe { base.add(n.offset as usize).cast::<State>() };
            if unsafe { noise_validate(state, n.bytes) } != 0
                || unsafe { *state.cast::<u32>() } != 3
            {
                return -6;
            }
            // Count uses, not just distinct states: repeated nodes also consume work.
            work += (n.bytes - 80) / 1320;
            expanded_noise[i] += (n.bytes - 80) / 1320;
        }
        if visits[i] > 512 || expanded_noise[i] > 128 {
            return -8;
        }
    }
    if work > 128 {
        return -7;
    }
    0
}

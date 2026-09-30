//! Validate Java-owned noise state before publication.
use super::state::State;
use std::hint::black_box;
/// Validate the Java-owned state once before publishing it to other threads.
/// # Safety
/// `state` must point to `bytes` readable bytes aligned to eight bytes.
pub(crate) unsafe fn noise_validate(state: *const State, bytes: u64) -> i32 {
    if state.is_null() || (state as usize) % 8 != 0 || bytes < 80 {
        return -1;
    }
    let s = unsafe { &*state };
    let count = s.n1 as u64 + s.n2 as u64 + s.n3 as u64;
    if count > i32::MAX as u64 || bytes != 80 + 1320 * count {
        return -2;
    }
    let valid = match s.kind {
        1 | 5 => s.n1 == 1 && s.n2 == 0 && s.n3 == 0,
        2 | 6 => s.n2 == 0 && s.n3 == 0,
        3 => s.n3 == 0,
        4 => s.n1 == 16 && s.n2 == 16 && s.n3 == 8,
        _ => false,
    };
    if !valid {
        return -3;
    }
    for octave in unsafe { s.octaves() } {
        // Java seed constructors generate offsets in [0,256). Enforce the
        // invariant relied on by the optimized floor-to-int conversions.
        if ![octave.x, octave.y, octave.z]
            .iter()
            .all(|x| *x >= 0. && *x <= 256.)
        {
            return -5;
        }
        for i in 0..256 {
            let p = octave.p[i] as i32;
            if octave.p32[i] != (p | ((p % 12) << 8)) {
                return -4;
            }
        }
    }
    // Complete feature detection before any critical downcall can evaluate.
    #[cfg(target_arch = "x86_64")]
    black_box(is_x86_feature_detected!("avx2"));
    0
}

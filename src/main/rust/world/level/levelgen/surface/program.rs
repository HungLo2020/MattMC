//! Forward-only eight-word rule bytecode and validation.
use std::slice;
pub(super) const STRIDE: usize = 8;
pub(super) const STOP: i32 = 0;
pub(super) const BLOCK: i32 = 1;
pub(super) const EXTERNAL_CONDITION: i32 = 2;
pub(super) const STONE_DEPTH: i32 = 3;
pub(super) const WATER: i32 = 4;
pub(super) const Y_CHECK: i32 = 5;
pub(super) const HOLE: i32 = 6;
pub(super) const BIOME: i32 = 7;
pub(super) const ABOVE_PRELIMINARY: i32 = 8;
pub(super) const EXTERNAL_RULE: i32 = 9;
pub(super) const BANDS: i32 = 10;
pub(super) const VERTICAL_GRADIENT: i32 = 11;
/// # Safety
/// The program is a readable, aligned array of `len` i32 values. Header word 0
/// is the instruction area length; instructions start at word 8, and all jumps
/// are forward. Trailing data contains biome registry IDs.
pub(crate) unsafe fn surface_validate(program: *const i32, len: i32) -> i32 {
    if program.is_null() || program as usize % 4 != 0 || len < 16 {
        return -1;
    }
    let p = unsafe { slice::from_raw_parts(program, len as usize) };
    let end = p[0] as usize;
    if end < 16 || end > p.len() || end % STRIDE != 0 {
        return -2;
    }
    for pc in (8..end).step_by(STRIDE) {
        let n = &p[pc..pc + STRIDE];
        if !(STOP..=VERTICAL_GRADIENT).contains(&n[0]) || n[6] < 0 || n[6] > 1 {
            return -3;
        }
        if ((EXTERNAL_CONDITION..=ABOVE_PRELIMINARY).contains(&n[0]) || n[0] == VERTICAL_GRADIENT)
            && (n[5] as usize <= pc || n[5] as usize > end || n[5] as usize % STRIDE != 0)
        {
            return -4;
        }
        if (n[0] == BIOME || n[0] == BANDS)
            && (n[1] < end as i32
                || n[2] < 0
                || (n[0] == BANDS && n[2] == 0)
                || n[1] as usize > p.len()
                || n[2] as usize > p.len() - n[1] as usize)
        {
            return -5;
        }
        if n[0] == BIOME
            && p[n[1] as usize..n[1] as usize + n[2] as usize]
                .windows(2)
                .any(|pair| pair[0] >= pair[1])
        {
            return -7;
        }
        if (n[0] == EXTERNAL_CONDITION || n[0] == EXTERNAL_RULE || n[0] == VERTICAL_GRADIENT)
            && n[1] < 0
        {
            return -6;
        }
    }
    0
}

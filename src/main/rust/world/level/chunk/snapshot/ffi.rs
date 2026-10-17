use super::{decode, padded, ENTRIES, PAD};

/// Returns an immutable native-owned 4096-state capture, or null on invalid metadata/data.
/// # Safety
/// Input spans must be aligned, readable and live for this call. No input is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_snapshot_create(
    words: *const u64,
    word_len: i32,
    bits: i32,
    palette: *const i32,
    palette_len: i32,
    limit: i32,
) -> *mut u16 {
    if !(0..=16).contains(&bits)
        || !(0..=1024).contains(&word_len)
        || !(0..=257).contains(&palette_len)
        || words.is_null()
        || palette.is_null()
        || words as usize % 8 != 0
        || palette as usize % 4 != 0
    {
        return std::ptr::null_mut();
    }
    decode(
        std::slice::from_raw_parts(words, word_len as usize),
        bits as usize,
        std::slice::from_raw_parts(palette, palette_len as usize),
        limit as usize,
    )
    .map_or(std::ptr::null_mut(), |states| Box::into_raw(states).cast())
}

/// # Safety
/// Release one owner from create exactly once, after all readers are gone.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_snapshot_release(states: *mut u16) {
    if !states.is_null() {
        drop(Box::from_raw(states.cast::<[u16; ENTRIES]>()));
    }
}

/// Builds the 18³ centre-section halo directly from retained world captures.
/// # Safety
/// `sections` covers 27 pointers, each null (air) or a live create result;
/// `output` covers `length` disjoint writable i32s. Owners remain live for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_snapshot_padded(
    sections: *const *const u16,
    output: *mut i32,
    length: i32,
    air: i32,
) -> i32 {
    if sections.is_null()
        || output.is_null()
        || sections as usize % std::mem::align_of::<usize>() != 0
        || output as usize % 4 != 0
        || length != (PAD * PAD * PAD) as i32
        || !(0..u16::MAX as i32).contains(&air)
    {
        return -1;
    }
    let pointers = std::slice::from_raw_parts(sections, 27);
    if pointers
        .iter()
        .any(|p| !p.is_null() && *p as usize % 2 != 0)
    {
        return -1;
    }
    let captures = std::array::from_fn(|i| pointers[i].cast::<[u16; ENTRIES]>().as_ref());
    padded(
        captures,
        air as u16,
        std::slice::from_raw_parts_mut(output, length as usize),
    );
    0
}

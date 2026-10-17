/// Return distinct packed IDs in first-occurrence order, or -1 without writes.
///
/// # Safety
/// Buffers must be valid for supplied lengths, aligned, disjoint and live for
/// this call. The 1024-word workspace starts zero and is restored to zero.
/// No allocation, callbacks, blocking or pointer retention occurs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_distinct(words: *const u64, word_len: i32,
    bits: i32, size: i32, seen: *mut u64, seen_len: i32, out: *mut u32, out_len: i32) -> i32 {
    if words.is_null() || seen.is_null() || out.is_null()
        || words as usize % 8 != 0 || seen as usize % 8 != 0 || out as usize % 4 != 0
        || !(0..=16).contains(&bits) || !(1..=4096).contains(&size)
        || seen_len != 1024 || out_len != size
        || word_len != if bits == 0 { 0 } else { (size + 64 / bits - 1) / (64 / bits) }
    { return -1; }
    super::scan::scan(std::slice::from_raw_parts(words, word_len as usize), bits as usize,
        size as usize, std::slice::from_raw_parts_mut(seen, 1024),
        std::slice::from_raw_parts_mut(out, out_len as usize)) as i32
}

/// Fixed-size entry point for a bounded critical FFM call with heap buffers.
///
/// # Safety
/// Same disjoint-buffer/zero-workspace contract as `mattmc_palette_distinct`.
/// No more than 64 fields and 64 workspace resets; no allocations or callbacks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_distinct_biomes(words: *const u64, word_len: i32,
    bits: i32, size: i32, seen: *mut u64, seen_len: i32, out: *mut u32, out_len: i32) -> i32 {
    if size != 64 { return -1; }
    mattmc_palette_distinct(words, word_len, bits, 64, seen, seen_len, out, out_len)
}

/// Decode exactly one saved block section. Negative results publish no result.
///
/// # Safety
/// Buffers must be aligned, disjoint and live, covering the supplied lengths.
/// `lookup` must contain 65536 zeroed u32s on entry. Every touched slot is reset
/// on success and malformed-ID errors. No pointer is retained or allocation made.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_unpack_decode(
    words: *const u64,
    word_len: i32,
    bits: i32,
    count: i32,
    lookup: *mut u32,
    lookup_len: i32,
    out: *mut u32,
    out_len: i32,
) -> i32 {
    if words.is_null()
        || lookup.is_null()
        || out.is_null()
        || words as usize % 8 != 0
        || lookup as usize % 4 != 0
        || out as usize % 4 != 0
        || !(9..=16).contains(&bits)
        || !(257..=65536).contains(&count)
        || count > (1 << bits)
        || lookup_len != 65536
        || out_len != 8192
        || word_len != (4096 + 64 / bits - 1) / (64 / bits)
    {
        return -1;
    }
    let words = std::slice::from_raw_parts(words, word_len as usize);
    let lookup = std::slice::from_raw_parts_mut(lookup, 65536);
    let out = std::slice::from_raw_parts_mut(out, 8192);
    super::decode::decode(words, bits as usize, count as usize, lookup, out)
        .map(|n| n as i32)
        .unwrap_or(-2)
}

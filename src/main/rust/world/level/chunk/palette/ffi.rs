//! Ordinary calls with disjoint caller-owned buffers. No allocation or retained pointers.
/// Compact exactly one block section; negative results publish no Java result.
/// A null `labels` makes each of the `label_len` palette ids its own label.
///
/// # Safety
/// Non-null pointers must cover the supplied lengths, be aligned and live for
/// the call. Input buffers must not alias either writable buffer, and writable
/// buffers must be disjoint. These functions cannot validate allocation bounds.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_compact(
    words: *const u64,
    word_len: i32,
    bits: i32,
    labels: *const u32,
    label_len: i32,
    lookup: *mut i32,
    lookup_len: i32,
    out: *mut u32,
    out_len: i32,
) -> i32 {
    if words.is_null()
        || lookup.is_null()
        || out.is_null()
        || words as usize % 8 != 0
        || labels as usize % 4 != 0
        || lookup as usize % 4 != 0
        || out as usize % 4 != 0
        || !(0..=32).contains(&bits)
        || !(1..=32768).contains(&label_len)
        || lookup_len != label_len
        || out_len != 8192
        || word_len
            != if bits == 0 {
                0
            } else {
                (4096 + 64 / bits - 1) / (64 / bits)
            }
    {
        return -1;
    }
    let words = std::slice::from_raw_parts(words, word_len as usize);
    let lookup = std::slice::from_raw_parts_mut(lookup, lookup_len as usize);
    let out = std::slice::from_raw_parts_mut(out, out_len as usize);
    let result = if labels.is_null() {
        super::pack::compact_identity(words, bits as usize, label_len as usize, lookup, out)
    } else {
        let labels = std::slice::from_raw_parts(labels, label_len as usize);
        super::pack::compact(words, bits as usize, labels, lookup, out)
    };
    result.map(|n| n as i32).unwrap_or(-2)
}
/// Write the complete padded packed-word output for one block section.
///
/// # Safety
/// The pointers must cover their lengths, be aligned, disjoint and live for
/// the call. No pointer is retained; the caller owns both buffers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_encode(
    indices: *const u32,
    count: i32,
    bits: i32,
    remap: *const u32,
    remap_len: i32,
    words: *mut u64,
    word_len: i32,
) -> i32 {
    if indices.is_null()
        || words.is_null()
        || remap.is_null()
        || remap as usize % 4 != 0
        || !(0..=4096).contains(&remap_len)
        || indices as usize % 4 != 0
        || words as usize % 8 != 0
        || count != 4096
        || !(1..=32).contains(&bits)
        || word_len != (4096 + 64 / bits - 1) / (64 / bits)
    {
        return -1;
    }
    let indices = std::slice::from_raw_parts(indices, 4096);
    let words = std::slice::from_raw_parts_mut(words, word_len as usize);
    let remap = if remap_len == 0 {
        None
    } else {
        Some(std::slice::from_raw_parts(remap, remap_len as usize))
    };
    super::pack::encode(indices, bits as usize, words, remap)
        .map(|_| 0)
        .unwrap_or(-2)
}

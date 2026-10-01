/// Count one block section into (count << 32 | ID) callback records.
///
/// # Safety
/// Buffers must cover the supplied lengths, be aligned, disjoint, and live for
/// the call. The dense workspace prefix must initially be zero. Valid calls
/// restore it to zero; metadata failures do not touch it. No pointers escape.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_histogram(
    words: *const u64,
    word_len: i32,
    bits: i32,
    work: *mut u32,
    work_len: i32,
    out: *mut u64,
    out_len: i32,
) -> i32 {
    if words.is_null()
        || work.is_null()
        || out.is_null()
        || words as usize % 8 != 0
        || work as usize % 4 != 0
        || out as usize % 8 != 0
        || !(0..=16).contains(&bits)
        || work_len != super::scan::WORK as i32
        || out_len != 4096
        || word_len
            != if bits == 0 {
                0
            } else {
                (4096 + 64 / bits - 1) / (64 / bits)
            }
    {
        return -1;
    }
    super::scan::scan(
        std::slice::from_raw_parts(words, word_len as usize),
        bits as usize,
        std::slice::from_raw_parts_mut(work, work_len as usize),
        std::slice::from_raw_parts_mut(out, out_len as usize),
    ) as i32
}

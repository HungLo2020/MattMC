fn length(bits: i32) -> i32 {
    if bits == 0 {
        0
    } else {
        (4096 + 64 / bits - 1) / (64 / bits)
    }
}

/// Collect used local IDs before Java mutates the new palette.
///
/// # Safety
/// Pointers must cover their lengths, be aligned, disjoint and live for the call.
/// No pointer escapes. Metadata errors touch no output; invalid IDs publish no result.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_resize_used(
    words: *const u64,
    word_len: i32,
    bits: i32,
    count: i32,
    out: *mut u32,
    out_len: i32,
) -> i32 {
    if words.is_null()
        || out.is_null()
        || words as usize % 8 != 0
        || out as usize % 4 != 0
        || !(0..=8).contains(&bits)
        || !(1..=256).contains(&count)
        || out_len != 256
        || word_len != length(bits)
    {
        return -1;
    }
    super::remap::used(
        std::slice::from_raw_parts(words, word_len as usize),
        bits as usize,
        count as usize,
        std::slice::from_raw_parts_mut(out, 256),
    )
    .map(|n| n as i32)
    .unwrap_or(-2)
}

/// Repack into freshly allocated destination storage; all padding is cleared.
///
/// # Safety
/// Pointers cover the supplied lengths, are aligned/disjoint, and live for the call.
/// All used source IDs must be less than 256. Java calls `resize_used` first and
/// keeps that input unchanged. Map entries must fit target_bits. No pointer escapes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_palette_resize_remap(
    words: *const u64,
    word_len: i32,
    bits: i32,
    map: *const u32,
    map_len: i32,
    target_bits: i32,
    out: *mut u64,
    out_len: i32,
) -> i32 {
    if words.is_null()
        || map.is_null()
        || out.is_null()
        || words as usize % 8 != 0
        || map as usize % 4 != 0
        || out as usize % 8 != 0
        || !(0..=8).contains(&bits)
        || !(1..=16).contains(&target_bits)
        || map_len != 256
        || word_len != length(bits)
        || out_len != length(target_bits)
    {
        return -1;
    }
    let map = std::slice::from_raw_parts(map, 256);
    if map.iter().any(|&id| id >= 1 << target_bits) {
        return -2;
    }
    super::remap::remap(
        std::slice::from_raw_parts(words, word_len as usize),
        bits as usize,
        map,
        target_bits as usize,
        std::slice::from_raw_parts_mut(out, out_len as usize),
    );
    0
}

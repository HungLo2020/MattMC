//! Borrowed disjoint buffers: metadata[8], pending[256], upper-face IDs[256],
//! then one packed heightmap. A failure may update scratch, never published data.
/// Bounded empty-chunk clear: at most 128 words, no allocations or callbacks.
/// The Java bridge may use critical heap access only for this short operation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_skylight_sources_empty(
    maps: *mut u64,
    word_len: i32,
    bits: i32,
) -> i32 {
    if maps.is_null() || maps as usize % 8 != 0 || !(1..=32).contains(&bits) {
        return -1;
    }
    let per_word = 64 / bits as usize;
    let stride = (256 + per_word - 1) / per_word;
    if word_len != stride as i32 {
        return -1;
    }
    let maps = std::slice::from_raw_parts_mut(maps, stride);
    super::scan::clear(maps, bits as usize);
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_skylight_sources_section(
    words: *const u64,
    word_len: i32,
    flags: *const u32,
    flag_len: i32,
    edges: *const u8,
    edge_len: i32,
    frame: *mut u8,
    frame_len: i32,
) -> i32 {
    if words.is_null()
        || flags.is_null()
        || edges.is_null()
        || frame.is_null()
        || words as usize % 8 != 0
        || flags as usize % 4 != 0
        || frame as usize % 8 != 0
        || word_len < 0
        || flag_len <= 0
        || frame_len < 1312
        || edge_len < 1
    {
        return -1;
    }
    let h = std::slice::from_raw_parts(frame as *const i32, 8);
    let bits = h[0];
    let hb = h[1];
    let stride = h[2];
    let faces = h[6];
    let span = h[5] as i64 - h[4] as i64;
    if !(-1..=32).contains(&bits)
        || !(1..=32).contains(&hb)
        || !(1..=512).contains(&faces)
        || edge_len != faces * faces
        || flag_len != h[7]
        || stride != (256 + 64 / hb - 1) / (64 / hb)
        || (frame_len as i64) < 1312 + stride as i64 * 8
        || h[4] == i32::MAX
        || span < 17
        || span > i32::MAX as i64
        || span > (1i64 << hb) - 1
        || h[3] < h[4] + 1
        || h[3] as i64 + 16 > h[5] as i64
        || (h[3] as i64 - h[4] as i64 - 1) % 16 != 0
        || word_len
            != if bits <= 0 {
                0
            } else {
                (4096 + 64 / bits - 1) / (64 / bits)
            }
    {
        return -1;
    }
    let words = std::slice::from_raw_parts(words, word_len as usize);
    let flags = std::slice::from_raw_parts(flags, flag_len as usize);
    let edges = std::slice::from_raw_parts(edges, edge_len as usize);
    let pending = std::slice::from_raw_parts_mut(frame.add(32), 256);
    if pending.iter().any(|v| *v > 1) {
        return -1;
    }
    let above = std::slice::from_raw_parts_mut(frame.add(288) as *mut u32, 256);
    let maps = std::slice::from_raw_parts_mut(frame.add(1312) as *mut u64, stride as usize);
    super::scan::section(words, flags, edges, h, pending, above, maps).unwrap_or(-2)
}

//! Frame: eight i32 metadata fields, 256 pending type masks, then six packed maps.
//! Buffers are aligned, caller-owned, disjoint from the frame, and borrowed only
//! during this ordinary call. A negative result may leave private scratch updated.

/// Scans one section. `ids` holds the state id of each local palette entry
/// (`id_len` of them), or is null for the global palette, whose ids are
/// state ids. Masks come from the installed block registry; -1 when it is
/// not installed or a state is a custom subclass.
/// # Safety
/// Pointers address their stated counts, aligned, for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_heightmap_section(
    words: *const u64,
    word_len: i32,
    ids: *const u32,
    id_len: i32,
    frame: *mut u8,
    frame_len: i32,
) -> i32 {
    let Some(view) = super::installed_masks() else { return -1 };
    if ids.is_null() {
        if view.custom || id_len as usize != view.masks.len() {
            return -1;
        }
        return section(words, word_len, view.masks.as_ptr(), id_len, frame, frame_len);
    }
    if !(1..=256).contains(&id_len) || ids as usize % 4 != 0 {
        return -1;
    }
    let mut masks = [0u32; 256];
    for (mask, &id) in masks.iter_mut().zip(std::slice::from_raw_parts(ids, id_len as usize)) {
        match view.masks.get(id as usize) {
            Some(&m) if m != super::CUSTOM => *mask = m,
            _ => return -1,
        }
    }
    section(words, word_len, masks.as_ptr(), id_len, frame, frame_len)
}

/// [`mattmc_heightmap_section`] with each palette entry's mask in `flags`.
pub(super) unsafe fn section(
    words: *const u64,
    word_len: i32,
    flags: *const u32,
    flag_len: i32,
    frame: *mut u8,
    frame_len: i32,
) -> i32 {
    if words.is_null()
        || flags.is_null()
        || frame.is_null()
        || word_len < 0
        || flag_len <= 0
        || frame_len < 288
        || words as usize % 8 != 0
        || flags as usize % 4 != 0
        || frame as usize % 8 != 0
    {
        return -1;
    }
    let header = std::slice::from_raw_parts(frame as *const i32, 8);
    let bits = header[0];
    let height_bits = header[1];
    let stride = header[3];
    if !(0..=32).contains(&bits)
        || !(1..=32).contains(&height_bits)
        || header[2] != flag_len
        || header[7] & !63 != 0
        || header[7] == 0
        || header[5] >= header[6]
        || header[4] > i32::MAX - 16
        || (header[6] as i64 - header[5] as i64) > (1i64 << height_bits) - 1
        || (header[6] as i64 - header[5] as i64) > i32::MAX as i64
        || stride != (256 + 64 / height_bits - 1) / (64 / height_bits)
        || (frame_len as i64) < 288 + stride as i64 * 6 * 8
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
    let flags = std::slice::from_raw_parts(flags, flag_len as usize);
    let pending = std::slice::from_raw_parts_mut(frame.add(32), 256);
    if pending.iter().any(|mask| *mask & !(header[7] as u8) != 0) {
        return -1;
    }
    let maps = std::slice::from_raw_parts_mut(frame.add(288) as *mut u64, stride as usize * 6);
    super::scan::section(words, bits as usize, flags, header, pending, maps).unwrap_or(-2)
}

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

/// Scans one section. `ids` holds the state id of each local palette entry
/// (`id_len` of them; unread for an all-air section), or is null for the
/// global palette, whose ids are state ids. Descriptors and the face table
/// come from the installed block registry, and Rust writes the face count
/// into the frame header; -1 when the registry is not installed, its faces
/// do not fit, or a palette state is a custom subclass.
/// # Safety
/// Pointers address their stated counts, aligned, for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_skylight_sources_section(
    words: *const u64,
    word_len: i32,
    ids: *const u32,
    id_len: i32,
    frame: *mut u8,
    frame_len: i32,
) -> i32 {
    let Some(tables) = super::installed_tables() else { return -1 };
    if frame.is_null() || frame as usize % 8 != 0 || frame_len < 32 {
        return -1;
    }
    *(frame as *mut i32).add(6) = tables.faces as i32;
    let all_air = *(frame as *const i32) == -1;
    let edges = (tables.edges.as_ptr(), tables.edges.len() as i32);
    if ids.is_null() {
        if tables.custom || id_len as usize != tables.descriptors.len() {
            return -1;
        }
        return section(words, word_len, tables.descriptors.as_ptr(), id_len, edges.0, edges.1, frame, frame_len);
    }
    if !(1..=256).contains(&id_len) || ids as usize % 4 != 0 {
        return -1;
    }
    let mut descriptors = [0u32; 256];
    if !all_air {
        for (descriptor, &id) in descriptors.iter_mut().zip(std::slice::from_raw_parts(ids, id_len as usize)) {
            match tables.descriptors.get(id as usize) {
                Some(&d) if d != super::CUSTOM => *descriptor = d,
                _ => return -1,
            }
        }
    }
    section(words, word_len, descriptors.as_ptr(), id_len, edges.0, edges.1, frame, frame_len)
}

/// [`mattmc_skylight_sources_section`] with each palette entry's descriptor
/// in `flags` and the `faces`² edge table (face count in the header).
#[allow(clippy::too_many_arguments)]
pub(super) unsafe fn section(
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

/// Verification: copies the installed descriptors (one per state) and the
/// `faces`² edge table when both buffers are large enough. Returns the face
/// count, or -1 when no tables are installed.
/// # Safety
/// Non-null pointers address their stated counts.
#[no_mangle]
pub unsafe extern "C" fn mattmc_skylight_sources_tables(descriptors: *mut u32, descriptor_len: i32, edges: *mut u8, edge_len: i32) -> i32 {
    let Some(tables) = super::installed_tables() else { return -1 };
    if !descriptors.is_null() && !edges.is_null() && descriptor_len as usize >= tables.descriptors.len()
        && edge_len as usize >= tables.edges.len()
    {
        std::slice::from_raw_parts_mut(descriptors, tables.descriptors.len()).copy_from_slice(&tables.descriptors);
        std::slice::from_raw_parts_mut(edges, tables.edges.len()).copy_from_slice(&tables.edges);
    }
    tables.faces as i32
}

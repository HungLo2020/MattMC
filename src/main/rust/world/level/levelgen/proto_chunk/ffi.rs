use super::ProtoStorage;
use crate::world::level::levelgen::noise_fill::FLAG_FLUID;

/// Capture canonical live section inputs entirely within Rust. Counters and
/// heightmaps preserve the original Java stage snapshot; no owner is retained.
/// # Safety
/// Arrays span the stated counts, are aligned, and remain live during this call.
/// Each pointer is a live Owner excluded from concurrent stage mutation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_create_live(
    owners: *const *const crate::world::level::chunk::live::Owner, counts: *const i32,
    count: i32, min_y: i32, height: i32, global_bits: i32,
    surface: *const i64, floor: *const i64, heightmap_words: i32,
) -> u64 {
    let Some(flags) = crate::world::level::levelgen::noise_fill::installed_state_flags() else { return 0 };
    if owners.is_null() || counts.is_null() || surface.is_null() || floor.is_null()
        || owners as usize % 8 != 0 || counts as usize % 4 != 0
        || surface as usize % 8 != 0 || floor as usize % 8 != 0
        || !(1..=256).contains(&count) || heightmap_words <= 0 { return 0; }
    let owner_pointers = unsafe { std::slice::from_raw_parts(owners, count as usize) };
    let counters = unsafe { std::slice::from_raw_parts(counts, count as usize * 3) };
    let mut sections = Vec::with_capacity(count as usize);
    for (index, &pointer) in owner_pointers.iter().enumerate() {
        if pointer.is_null() || pointer as usize % 8 != 0 { return 0; }
        let Some((kind, bits, palette, raw)) = (unsafe { &*pointer }).stage_snapshot(flags.len() as u32, global_bits as u32) else { return u64::MAX };
        sections.push((kind, bits, palette, raw, [counters[index * 3], counters[index * 3 + 1], counters[index * 3 + 2]]));
    }
    let a = unsafe { std::slice::from_raw_parts(surface, heightmap_words as usize) };
    let b = unsafe { std::slice::from_raw_parts(floor, heightmap_words as usize) };
    ProtoStorage::new(min_y, height, sections, global_bits as u32, flags, a, b)
        .map_or(0, |storage| Box::into_raw(Box::new(storage)) as u64)
}

/// Capture packed storage and native section-local counters in one locked snapshot.
/// Arrays and both CPU owner pointers are aligned/live for the complete call;
/// no pointer is retained. The caller excludes other stage mutation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_create_live_counters(
    owners: *const *const crate::world::level::chunk::live::Owner,
    counts: *const *const crate::world::level::chunk::counters::Owner,
    count: i32, min_y: i32, height: i32, global_bits: i32,
    surface: *const i64, floor: *const i64, heightmap_words: i32,
) -> u64 {
    let Some(flags) = crate::world::level::levelgen::noise_fill::installed_state_flags() else { return 0 };
    if owners.is_null() || counts.is_null() || surface.is_null() || floor.is_null()
        || owners as usize % 8 != 0 || counts as usize % 8 != 0
        || surface as usize % 8 != 0 || floor as usize % 8 != 0
        || !(1..=256).contains(&count) || heightmap_words <= 0 { return 0; }
    let owners = unsafe { std::slice::from_raw_parts(owners, count as usize) };
    let counters = unsafe { std::slice::from_raw_parts(counts, count as usize) };
    let mut sections = Vec::with_capacity(count as usize);
    for (&pointer, &counter) in owners.iter().zip(counters) {
        if pointer.is_null() || counter.is_null() || pointer as usize % 8 != 0 || counter as usize % 8 != 0 { return 0; }
        let Some(section) = (unsafe { &*pointer }).stage_with_counts(unsafe { &*counter }, flags.len() as u32, global_bits as u32)
            else { return u64::MAX; };
        sections.push(section);
    }
    let a = unsafe { std::slice::from_raw_parts(surface, heightmap_words as usize) };
    let b = unsafe { std::slice::from_raw_parts(floor, heightmap_words as usize) };
    ProtoStorage::new(min_y, height, sections, global_bits as u32, flags, a, b)
        .map_or(0, |storage| Box::into_raw(Box::new(storage)) as u64)
}

/// Convert a modified stage section directly into a separately owned live
/// section. 0 unmodified, 1 transferred, 2 compatibility, negative invalid.
/// # Safety
/// A live confined stage handle; output is aligned/writable for 24 bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_section_live(
    handle: u64, index: i32, limit: i32, global_bits: i32,
    output: *mut crate::world::level::chunk::stage_transfer::ResultHeader,
) -> i32 {
    let s = unsafe { storage(handle) };
    if index < 0 || index as usize >= s.section_count() || !s.valid() { return -1; }
    let Some(section) = s.modified_section(index as usize) else { return 0 };
    unsafe { crate::world::level::chunk::stage_transfer::write_result(section, limit, global_bits, output) }
}

/// A chunk's block storage for a generation stage. `ints`: [minY, height,
/// sectionCount, globalBits, then per section kind, storage bits, palette
/// length, raw length, three counters, then palette ids]. `longs`: [per section
/// raw storage..., WORLD_SURFACE_WG raw, OCEAN_FLOOR_WG raw] (`heightmap_words`
/// each). State flags come from the installed block registry. Returns 0 if
/// invalid or the registry is not installed.
/// # Safety
/// Arrays hold the stated counts for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_create(ints: *const i32, int_count: i32, longs: *const i64, long_count: i32,
    heightmap_words: i32) -> u64 {
    let Some(flags) = crate::world::level::levelgen::noise_fill::installed_state_flags() else { return 0 };
    if ints.is_null() || longs.is_null() || int_count < 4 || long_count < 0 || heightmap_words <= 0 {
        return 0;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };
    let l = unsafe { std::slice::from_raw_parts(longs, long_count as usize) };
    match parse(i, l, heightmap_words as usize, flags) {
        Some(storage) => Box::into_raw(Box::new(storage)) as u64,
        None => 0,
    }
}

fn parse(i: &[i32], l: &[i64], heightmap_words: usize, flags: &'static [u8]) -> Option<ProtoStorage> {
    let (min_y, height, count, global_bits) = (i[0], i[1], usize::try_from(i[2]).ok()?, u32::try_from(i[3]).ok()?);
    if count > 256 {
        return None;
    }
    let mut at = 4;
    let mut headers = Vec::with_capacity(count);
    for _ in 0..count {
        let h = i.get(at..at + 7)?;
        headers.push((u32::try_from(h[0]).ok()?, u32::try_from(h[1]).ok()?, usize::try_from(h[2]).ok()?, usize::try_from(h[3]).ok()?, [h[4], h[5], h[6]]));
        at += 7;
    }
    let mut long_at = 0;
    let mut sections = Vec::with_capacity(count);
    for (kind, bits, palette_len, raw_len, counts) in headers {
        let palette = i.get(at..at + palette_len)?.to_vec();
        at += palette_len;
        let raw = l.get(long_at..long_at + raw_len)?.to_vec();
        long_at += raw_len;
        sections.push((kind, bits, palette, raw, counts));
    }
    if at != i.len() || long_at + 2 * heightmap_words != l.len() {
        return None;
    }
    ProtoStorage::new(min_y, height, sections, global_bits, flags, &l[long_at..long_at + heightmap_words], &l[long_at + heightmap_words..]).ok()
}

/// # Safety
/// A live handle from create; storages are confined to one thread at a time.
pub(crate) unsafe fn storage<'a>(handle: u64) -> &'a mut ProtoStorage {
    unsafe { &mut *(handle as *mut ProtoStorage) }
}

/// `getHeight(WORLD_SURFACE_WG, x, z)`.
/// # Safety
/// A live handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_height(handle: u64, x: i32, z: i32) -> i32 {
    unsafe { storage(handle) }.height(x, z)
}

/// The block state id at (x, y, z): -1 outside the sections, -2 in an only-air section.
/// # Safety
/// A live handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_get(handle: u64, x: i32, y: i32, z: i32) -> i32 {
    unsafe { storage(handle) }.get(x, y, z)
}

/// `ProtoChunk.setBlockState`, then, when `mark_fluids` is set and the state
/// holds a fluid, a post-processing mark (buildSurface's `BlockColumn`).
/// Returns 0 or negative.
/// # Safety
/// A live handle used by one thread.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_set(handle: u64, x: i32, y: i32, z: i32, state: i32, mark_fluids: i32) -> i32 {
    let s = unsafe { storage(handle) };
    let Ok(flags) = s.flag(state) else { return -1 };
    if s.set_block_state(x, y, z, state).is_err() {
        return -1;
    }
    let inside = y >= s.min_y() && y < s.min_y() + 16 * s.section_count() as i32;
    if mark_fluids != 0 && inside && flags & FLAG_FLUID != 0 {
        s.mark(x, y, z);
    }
    0
}

/// A modified section's result: `info` [requested bits, palette length, raw
/// length, non-empty, ticking, fluid]; 1 written, 0 unmodified, negative errors
/// (including storage whose export proved invalid when a section unpacked).
/// # Safety
/// Buffers are writable for their capacities and borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_section(handle: u64, index: i32, info: *mut i32, palette: *mut i32, palette_cap: i32,
    raw: *mut i64, raw_cap: i32) -> i32 {
    let s = unsafe { storage(handle) };
    if index < 0 || index as usize >= s.section_count() || info.is_null() || palette.is_null() || raw.is_null() || !s.valid() {
        return -1;
    }
    let Some((requested, entries, packed, counts)) = s.section(index as usize) else { return 0 };
    unsafe {
        std::slice::from_raw_parts_mut(info, 6).copy_from_slice(&[requested as i32, entries.len() as i32, packed.len() as i32, counts[0], counts[1], counts[2]]);
    }
    if entries.len() > palette_cap.max(0) as usize || packed.len() > raw_cap.max(0) as usize {
        return -2;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(entries.as_ptr(), palette, entries.len());
        std::ptr::copy_nonoverlapping(packed.as_ptr(), raw, packed.len());
    }
    1
}

/// Both heightmaps' raw data when each fits `cap`; returns the length or -1.
/// # Safety
/// Both buffers are writable for `cap` longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_heightmaps(handle: u64, world_surface: *mut i64, ocean_floor: *mut i64, cap: i32) -> i32 {
    let s = unsafe { storage(handle) };
    let (a, b) = (s.heightmap_raw(false), s.heightmap_raw(true));
    if a.len() > cap.max(0) as usize || world_surface.is_null() || ocean_floor.is_null() {
        return -1;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(a.as_ptr(), world_surface, a.len());
        std::ptr::copy_nonoverlapping(b.as_ptr(), ocean_floor, b.len());
    }
    a.len() as i32
}

/// Post-processing marks in call order (x, y, z); returns the count, or its
/// negation when `cap` is too small.
/// # Safety
/// `out` holds `cap * 3` writable i32s.
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_post_process(handle: u64, out: *mut i32, cap: i32) -> i32 {
    let marks = unsafe { storage(handle) }.post_process();
    if marks.len() > cap.max(0) as usize || out.is_null() {
        return -(marks.len() as i32);
    }
    for (index, mark) in marks.iter().enumerate() {
        unsafe { std::ptr::copy_nonoverlapping(mark.as_ptr(), out.add(index * 3), 3) };
    }
    marks.len() as i32
}

/// # Safety
/// A handle from create, not used afterwards (nor by a stage borrowing it).
#[no_mangle]
pub unsafe extern "C" fn mattmc_proto_chunk_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut ProtoStorage) });
    }
}

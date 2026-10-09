use super::*;

/// All spans must be aligned and live for the call. No input is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_create(
    words: *const u64,
    length: i32,
    bits: i32,
    requested: i32,
    palette: *const i32,
    count: i32,
    limit: i32,
    global_bits: i32,
) -> *mut Owner {
    if words.is_null()
        || palette.is_null()
        || words as usize % 8 != 0
        || palette as usize % 4 != 0
        || !(0..=1024).contains(&length)
        || !(0..=256).contains(&count)
        || !(0..=16).contains(&bits)
        || !(0..=32).contains(&requested)
    {
        return std::ptr::null_mut();
    }
    Owner::load(
        bits as usize,
        requested as usize,
        std::slice::from_raw_parts(palette, count as usize),
        std::slice::from_raw_parts(words, length as usize),
        limit as u32,
        global_bits as usize,
    )
    .map_or(std::ptr::null_mut(), |v| Box::into_raw(Box::new(v)))
}
/// Release exactly one create/copy owner after all calls finish. Views have separate leases.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_release(owner: *mut Owner) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_copy(owner: *const Owner) -> *mut Owner {
    owner
        .as_ref()
        .map_or(std::ptr::null_mut(), |o| Box::into_raw(Box::new(o.copy())))
}
/// Writes [bits, requested, global, word count, word pointer, palette pointer,
/// count pointer, lease pointer] into 48 bytes. Lease pins a complete generation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_view(owner: *const Owner, output: *mut u64) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % 8 != 0 {
        return -1;
    }
    let s = (*owner).state.lock().unwrap();
    let g = &s.generation;
    let header = output.cast::<u32>();
    *header = g.bits as u32;
    *header.add(1) = g.requested as u32;
    *header.add(2) = g.global as u32;
    *header.add(3) = g.words.len() as u32;
    *output.add(2) = g.words.as_ptr() as u64;
    *output.add(3) = g.palette.as_ptr() as u64;
    *output.add(4) = &g.count as *const AtomicU32 as u64;
    *output.add(5) = Arc::into_raw(g.clone()) as u64;
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_view_release(view: *const u8) {
    if !view.is_null() {
        drop(Arc::from_raw(view.cast::<Generation>()));
    }
}
/// -1 invalid. Low32 old canonical ID, bit32 generation changed. Caller publishes
/// a fresh view after a change; retained old views remain safe to read.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_write(
    owner: *const Owner,
    index: i32,
    value: i32,
) -> i64 {
    owner
        .as_ref()
        .and_then(|o| o.write(index as usize, value as u32))
        .map_or(-1, |(old, changed)| old as i64 | ((changed as i64) << 32))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_snapshot(owner: *const Owner) -> *mut u16 {
    owner
        .as_ref()
        .map_or(std::ptr::null_mut(), |o| Box::into_raw(o.capture()).cast())
}
/// Copy a coherent packed generation into 1024 u64 words, 257 i32 palette IDs
/// and a four-i32 header [bits, requested, global, palette count]. Disjoint outputs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_export(
    owner: *const Owner,
    words: *mut u64,
    palette: *mut u32,
    header: *mut u32,
) -> i32 {
    if owner.is_null()
        || words.is_null()
        || palette.is_null()
        || header.is_null()
        || words as usize % 8 != 0
        || palette as usize % 4 != 0
        || header as usize % 4 != 0
    {
        return -1;
    }
    let s = (*owner).state.lock().unwrap();
    let g = &s.generation;
    let count = g.count.load(Ordering::Acquire) as usize;
    for (i, value) in g.words.iter().enumerate() {
        *words.add(i) = value.load(Ordering::Acquire);
    }
    for i in 0..count {
        *palette.add(i) = g.palette[i].load(Ordering::Acquire);
    }
    *header = g.bits as u32;
    *header.add(1) = g.requested as u32;
    *header.add(2) = g.global as u32;
    *header.add(3) = count as u32;
    g.words.len() as i32
}
/// NativeLightBlocks' fixed buffer, at least 19008 bytes, disjoint from this owner.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_light(
    owner: *const Owner,
    output: *mut u8,
    length: i32,
) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % 8 != 0 || length < 19008 {
        return -1;
    }
    let s = (*owner).state.lock().unwrap();
    let g = &s.generation;
    let header = output.cast::<u32>();
    if g.bits == 0 {
        *header = 0;
        *header.add(1) = g.state(0);
        return 0;
    }
    *header = if g.global { 2 } else { 1 };
    *header.add(1) = g.bits as u32;
    let count = g.count.load(Ordering::Acquire) as usize;
    *header.add(2) = count as u32;
    let palette = output.add(2112).cast::<u16>();
    for i in 0..count {
        *palette.add(i) = g.palette[i].load(Ordering::Acquire) as u16;
    }
    let words = output.add(2624).cast::<u64>();
    for (i, value) in g.words.iter().enumerate() {
        *words.add(i) = value.load(Ordering::Acquire);
    }
    0
}
/// Original ordered histogram records, with no Java palette/word projection.
/// Workspace is initially zero in its dense prefix and restored by the kernel.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_histogram(
    owner: *const Owner,
    work: *mut u32,
    work_len: i32,
    out: *mut u64,
    out_len: i32,
) -> i32 {
    use crate::world::level::chunk::palette::histogram::scan;
    if owner.is_null()
        || work.is_null()
        || out.is_null()
        || work as usize % 4 != 0
        || out as usize % 8 != 0
        || work_len != scan::WORK as i32
        || out_len != 4096
    {
        return -1;
    }
    let s = (*owner).state.lock().unwrap();
    let g = &s.generation;
    let mut words = [0u64; 1024];
    for (i, value) in g.words.iter().enumerate() {
        words[i] = value.load(Ordering::Acquire);
    }
    scan::scan(
        &words[..g.words.len()],
        g.bits,
        std::slice::from_raw_parts_mut(work, work_len as usize),
        std::slice::from_raw_parts_mut(out, out_len as usize),
    ) as i32
}
/// SingleValuePalette.read replaces the value shared by zero-width copies.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_section_read_single(owner: *const Owner, value: i32) -> i32 {
    owner
        .as_ref()
        .filter(|o| o.read_single(value as u32))
        .map_or(-1, |_| 0)
}

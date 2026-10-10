//! Transitional CPU boundary. Each live pointer is one Arc owner; world fields
//! clone the typed owner, never Java heap pointers or native GPU handles.
use super::*;

/// # Safety
/// Aligned, live input spans of the declared lengths. Neither span is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_create(
    words: *const u64,
    length: i32,
    bits: i32,
    requested: i32,
    palette: *const u32,
    count: i32,
    limit: i32,
    global_bits: i32,
) -> *const Owner {
    if words.is_null()
        || palette.is_null()
        || words as usize % 8 != 0
        || palette as usize % 4 != 0
        || !(0..=16).contains(&length)
        || !(0..=8).contains(&count)
        || !(0..=16).contains(&bits)
        || !(0..=32).contains(&requested)
    {
        return std::ptr::null();
    }
    Owner::load(
        bits as usize,
        requested as usize,
        std::slice::from_raw_parts(palette, count as usize),
        std::slice::from_raw_parts(words, length as usize),
        limit as u32,
        global_bits as usize,
    )
    .map_or(std::ptr::null(), |owner| Arc::into_raw(Arc::new(owner)))
}
/// # Safety
/// Release exactly one create/copy ownership after its synchronous calls finish.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_release(owner: *const Owner) {
    if !owner.is_null() {
        drop(Arc::from_raw(owner));
    }
}
/// # Safety
/// Owner remains live throughout this call. Copy preserves Frozen zero-palette aliases.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_copy(owner: *const Owner) -> *const Owner {
    owner
        .as_ref()
        .filter(|o| o.is_valid())
        .map_or(std::ptr::null(), |o| Arc::into_raw(Arc::new(o.copy())))
}
/// # Safety
/// Live owner; output addresses 48 aligned writable bytes, disjoint from owner.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_view(owner: *const Owner, output: *mut u64) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % 8 != 0 {
        return -1;
    }
    let state = (*owner).state.lock().unwrap();
    if !(*owner).is_valid() {
        return -1;
    }
    let g = &state.generation;
    let header = output.cast::<u32>();
    *header = g.bits as u32;
    *header.add(1) = g.requested as u32;
    *header.add(2) = g.global as u32;
    *header.add(3) = g.words.len() as u32;
    *output.add(2) = g.words.as_ptr() as u64;
    *output.add(3) = g.palette.as_ptr() as u64;
    *output.add(4) = &g.count as *const AtomicU32 as u64;
    *output.add(5) = Arc::into_raw(Arc::clone(g)) as u64;
    0
}
/// # Safety
/// One unique view lease previously returned by view; no active readers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_view_release(lease: *const Generation) {
    if !lease.is_null() {
        drop(Arc::from_raw(lease));
    }
}
/// # Safety
/// Live owner. Return -1 without output change for invalid writes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_write(owner: *const Owner, index: i32, id: i32) -> i64 {
    owner
        .as_ref()
        .and_then(|o| o.write(index as usize, id as u32))
        .map_or(-1, |(old, changed)| {
            i64::from(old) | (i64::from(changed) << 32)
        })
}
/// # Safety
/// Live owner. Updates any Frozen single-value copy aliases.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_read_single(owner: *const Owner, id: i32) -> i32 {
    if owner.as_ref().is_some_and(|o| o.read_single(id as u32)) {
        0
    } else {
        -1
    }
}
/// # Safety
/// Live owner. Transfers authority to compatibility data; retained views remain valid.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_invalidate(owner: *const Owner) {
    if let Some(o) = owner.as_ref() {
        o.invalidate();
    }
}
/// # Safety
/// Live owner; aligned, disjoint outputs hold 16 words, 8 IDs and 4 header ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_live_biome_export(
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
    let state = (*owner).state.lock().unwrap();
    if !(*owner).is_valid() {
        return -1;
    }
    let g = &state.generation;
    let count = g.count.load(Ordering::Acquire) as usize;
    let single = if g.bits == 0 {
        Some(g.snapshot().0[0])
    } else {
        None
    };
    for (i, word) in g.words.iter().enumerate() {
        *words.add(i) = word.load(Ordering::Acquire);
    }
    for i in 0..count {
        *palette.add(i) = single.unwrap_or_else(|| g.palette[i].load(Ordering::Acquire));
    }
    *header = g.bits as u32;
    *header.add(1) = g.requested as u32;
    *header.add(2) = g.global as u32;
    *header.add(3) = count as u32;
    g.words.len() as i32
}

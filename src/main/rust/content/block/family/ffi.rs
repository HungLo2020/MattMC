//! Borrowed process-lifetime CPU data; never contains live world/device state.
use std::ffi::c_void;

/// 0 header (version, sets, woods, string bytes), 1 set rows (15 i32),
/// 2 wood rows (7 i32), 3 block-family rows (3 i32 per registered block),
/// 4 UTF-8 names. Length is in elements. Invalid selectors return null/zero.
///
/// # Safety
/// `length` must be null or point to one writable i32. Returned memory must
/// not be changed or freed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_block_family_buffer(kind: i32, length: *mut i32) -> *const c_void {
    if length.is_null() { return std::ptr::null(); }
    let registry = super::registry();
    let (pointer, count) = match kind {
        0 => (registry.header.as_ptr().cast(), registry.header.len()),
        1 => (registry.set_rows.as_ptr().cast(), registry.set_rows.len()),
        2 => (registry.wood_rows.as_ptr().cast(), registry.wood_rows.len()),
        3 => { let rows = super::block_rows(); (rows.as_ptr().cast(), rows.len()) },
        4 => (registry.strings.as_ptr().cast(), registry.strings.len()),
        _ => (std::ptr::null(), 0),
    };
    *length = i32::try_from(count).expect("bounded block-family content");
    pointer
}

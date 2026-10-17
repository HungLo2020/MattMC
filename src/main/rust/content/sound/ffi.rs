//! Bounded process-lifetime CPU projections; no playback resource handles.
use std::ffi::c_void;
/// 0 header, 1 event rows (six i32), 2 sound profiles (nine i32),
/// 3 instruments (four i32), 4 UTF-8 bytes. Length is in elements.
/// Invalid selectors return null and zero; callers never free these buffers.
/// # Safety
/// `length` must be null or point to one writable i32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sound_definitions_buffer(kind: i32, length: *mut i32) -> *const c_void {
    if length.is_null() { return std::ptr::null(); }
    let r = super::registry();
    let (ptr, len) = match kind {
        0 => (r.header.as_ptr().cast(), r.header.len()),
        1 => (r.event_rows.as_ptr().cast(), r.event_rows.len()),
        2 => (r.type_rows.as_ptr().cast(), r.type_rows.len()),
        3 => (r.instrument_rows.as_ptr().cast(), r.instrument_rows.len()),
        4 => (r.strings.as_ptr().cast(), r.strings.len()),
        _ => (std::ptr::null(), 0),
    };
    *length = len as i32; ptr
}

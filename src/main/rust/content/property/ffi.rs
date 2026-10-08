//! Borrowed, immutable process-lifetime CPU tables for compatibility views.
use std::ffi::c_void;

/// Selectors: 0 header, 1 definitions, 2 value string ranges (i32), 3 text (u8).
/// Invalid selectors return null and length zero. No caller release.
/// # Safety
/// `length` must be null or point to one writable i32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_property_definitions_buffer(kind: i32, length: *mut i32) -> *const c_void {
    if length.is_null() { return std::ptr::null(); }
    let r = super::registry();
    let (ptr, len) = match kind {
        0 => (r.header.as_ptr().cast(), r.header.len()),
        1 => (r.rows.as_ptr().cast(), r.rows.len()),
        2 => (r.values.as_ptr().cast(), r.values.len()),
        3 => (r.text.as_ptr().cast(), r.text.len()),
        _ => (std::ptr::null(), 0),
    };
    *length = len as i32;
    ptr
}

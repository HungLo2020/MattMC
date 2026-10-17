//! Process-lifetime immutable CPU tables for Java compatibility views.
use std::ffi::c_void;

/// Buffer selectors: 0 header, 1 definition rows, 2 property symbols, 3 state
/// traits (all i32), 4 names (u8). `length` receives the element count.
/// Returned memory is borrowed, immutable, and never released by the caller.
/// Invalid selectors return null and zero length.
/// # Safety
/// `length` must be null or address one writable i32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fluid_definitions_buffer(
    kind: i32,
    length: *mut i32,
) -> *const c_void {
    if length.is_null() {
        return std::ptr::null();
    }
    let r = super::registry();
    let (ptr, len) = match kind {
        0 => (r.header.as_ptr().cast(), r.header.len()),
        1 => (r.definition_rows.as_ptr().cast(), r.definition_rows.len()),
        2 => (r.properties.as_ptr().cast(), r.properties.len()),
        3 => (r.state_rows.as_ptr().cast(), r.state_rows.len()),
        4 => (r.names.as_ptr().cast(), r.names.len()),
        _ => (std::ptr::null(), 0),
    };
    *length = len as i32;
    ptr
}

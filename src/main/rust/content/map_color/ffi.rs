//! Small, borrowed process-lifetime CPU tables for temporary Java views.
use std::ffi::c_void;
const HEADER: [i32; 4] = [1, super::COLOR_COUNT as i32, 4, 256];
const fn rgb_table() -> [u32; super::COLOR_COUNT] {
    let mut result = [0; super::COLOR_COUNT]; let mut id = 0;
    while id < result.len() { result[id] = super::MapColor::ALL[id].rgb(); id += 1; }
    result
}
static RGB: [u32; super::COLOR_COUNT] = rgb_table();
static BRIGHTNESS: [i32; 4] = [
    super::Brightness::Low.modifier() as i32, super::Brightness::Normal.modifier() as i32,
    super::Brightness::High.modifier() as i32, super::Brightness::Lowest.modifier() as i32,
];
/// 0 header, 1 RGB, 2 brightness modifiers, 3 packed ARGB. Length is i32 elements.
/// Unknown selectors return null/zero. Borrowed memory must never be freed.
/// # Safety
/// `length` must be null or point to one writable i32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_map_palette_buffer(kind: i32, length: *mut i32) -> *const c_void {
    if length.is_null() { return std::ptr::null(); }
    let (ptr, count) = match kind {
        0 => (HEADER.as_ptr().cast(), HEADER.len()),
        1 => (RGB.as_ptr().cast(), RGB.len()),
        2 => (BRIGHTNESS.as_ptr().cast(), BRIGHTNESS.len()),
        3 => (super::PACKED_ARGB.as_ptr().cast(), super::PACKED_ARGB.len()),
        _ => (std::ptr::null(), 0),
    };
    *length = count as i32; ptr
}

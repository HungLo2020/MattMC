//! Borrowed process-lifetime declaration and graph buffers, never GPU objects.
use std::ffi::c_void;

/// Selectors: 0 header, 1 block rows, 2 template rows, 3 property IDs (i32),
/// 4 UTF-8 names. Counts are elements. Invalid selectors return null/zero.
/// # Safety
/// `length` is null or addresses one writable i32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_block_definitions_buffer(kind: i32, length: *mut i32) -> *const c_void {
    if length.is_null() { return std::ptr::null(); }
    let r = super::registry();
    let (ptr, len) = match kind {
        0 => (r.header.as_ptr().cast(), r.header.len()),
        1 => (r.rows.as_ptr().cast(), r.rows.len()),
        2 => (r.template_rows.as_ptr().cast(), r.template_rows.len()),
        3 => (r.properties.as_ptr().cast(), r.properties.len()),
        4 => (r.names.as_ptr().cast(), r.names.len()),
        _ => (std::ptr::null(), 0),
    };
    *length = len as i32;
    ptr
}

/// Graph selectors: 0 header, 1 values, 2 targets, 3 offsets (all i32).
/// Shared graphs are immutable and borrowed; callers must never release them.
/// # Safety
/// `length` is null or addresses one writable i32.
#[no_mangle]
pub unsafe extern "C" fn mattmc_block_definition_graph_buffer(id: i32, kind: i32, length: *mut i32) -> *const i32 {
    if length.is_null() { return std::ptr::null(); }
    *length = 0;
    let r = super::registry();
    let Some(graph) = r.graphs.get(id as usize) else { return std::ptr::null(); };
    let values: &[i32] = match kind {
        0 => &r.graph_headers[id as usize],
        1..=3 => graph.buffer((kind - 1) as u8).expect("valid graph selector"),
        _ => return std::ptr::null(),
    };
    *length = values.len() as i32;
    values.as_ptr()
}

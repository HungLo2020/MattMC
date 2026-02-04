// Rust FFI implementation of StringUtil  
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.coreapi.util.StringUtil
// Author: James Seibel (original Java), migrated to Rust for performance

use std::slice;

/// Converts a byte array into a hex string representation
/// Returns the length of the output written to the output buffer
/// 
/// # Safety
/// The caller must ensure:
/// - `bytes` points to a valid array of at least `bytes_len` bytes
/// - `output` points to a valid writable array of at least `bytes_len * 2` bytes
#[no_mangle]
pub unsafe extern "C" fn stringutil_byte_array_to_hex(
    bytes: *const u8,
    bytes_len: usize,
    output: *mut u8,
) -> i32 {
    if bytes.is_null() || output.is_null() {
        return -1;
    }

    let hex_array = b"0123456789ABCDEF";
    let input_slice = unsafe { slice::from_raw_parts(bytes, bytes_len) };
    let output_slice = unsafe { slice::from_raw_parts_mut(output, bytes_len * 2) };

    for (i, &byte) in input_slice.iter().enumerate() {
        let v = byte as usize;
        output_slice[i * 2] = hex_array[v >> 4];
        output_slice[i * 2 + 1] = hex_array[v & 0x0F];
    }

    (bytes_len * 2) as i32
}

/// Returns the length a shortened string would be (used for size calculation)
/// Returns 0 if the string would be empty
#[no_mangle]
pub extern "C" fn stringutil_shorten_string_len(str_len: i32, max_length: i32) -> i32 {
    if str_len <= 0 {
        return 0;
    }
    std::cmp::min(str_len, max_length)
}

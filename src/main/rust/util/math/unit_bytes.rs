// Rust FFI implementation of UnitBytes
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.math.UnitBytes
// Author: James Seibel (original Java), migrated to Rust for performance

/// Converts bytes to gigabytes
#[no_mangle]
pub extern "C" fn unitbytes_byte_to_gb(v: i64) -> i64 {
    v / 1073741824
}

/// Converts bytes to megabytes
#[no_mangle]
pub extern "C" fn unitbytes_byte_to_mb(v: i64) -> i64 {
    v / 1048576
}

/// Converts bytes to kilobytes
#[no_mangle]
pub extern "C" fn unitbytes_byte_to_kb(v: i64) -> i64 {
    v / 1024
}

/// Converts gigabytes to bytes
#[no_mangle]
pub extern "C" fn unitbytes_gb_to_byte(v: i64) -> i64 {
    v * 1073741824
}

/// Converts megabytes to bytes
#[no_mangle]
pub extern "C" fn unitbytes_mb_to_byte(v: i64) -> i64 {
    v * 1048576
}

/// Converts kilobytes to bytes
#[no_mangle]
pub extern "C" fn unitbytes_kb_to_byte(v: i64) -> i64 {
    v * 1024
}

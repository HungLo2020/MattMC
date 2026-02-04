// Rust FFI implementation of NumberUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.NumberUtil
// Author: coolGi (original Java), migrated to Rust for performance
//
// Note: This migration only includes the comparison functions (greaterThan/lessThan)
// for specific numeric types. The reflection-based generic Number min/max functions
// are kept in Java as they rely on Java-specific reflection features.

use std::os::raw::{c_double, c_float, c_int, c_long, c_short};

// Greater than comparisons for different types

#[no_mangle]
pub extern "C" fn numberutil_greater_than_int(a: c_int, b: c_int) -> i32 {
    if a > b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_greater_than_long(a: c_long, b: c_long) -> i32 {
    if a > b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_greater_than_float(a: c_float, b: c_float) -> i32 {
    if a > b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_greater_than_double(a: c_double, b: c_double) -> i32 {
    if a > b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_greater_than_short(a: c_short, b: c_short) -> i32 {
    if a > b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_greater_than_byte(a: i8, b: i8) -> i32 {
    if a > b { 1 } else { 0 }
}

// Less than comparisons for different types

#[no_mangle]
pub extern "C" fn numberutil_less_than_int(a: c_int, b: c_int) -> i32 {
    if a < b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_less_than_long(a: c_long, b: c_long) -> i32 {
    if a < b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_less_than_float(a: c_float, b: c_float) -> i32 {
    if a < b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_less_than_double(a: c_double, b: c_double) -> i32 {
    if a < b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_less_than_short(a: c_short, b: c_short) -> i32 {
    if a < b { 1 } else { 0 }
}

#[no_mangle]
pub extern "C" fn numberutil_less_than_byte(a: i8, b: i8) -> i32 {
    if a < b { 1 } else { 0 }
}

// Rust FFI implementation of MathUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.coreapi.util.MathUtil
// Author: James Seibel (original Java), migrated to Rust for performance

use std::os::raw::c_int;

/// Clamps the given value between the min and max values (int version)
/// May behave strangely if min > max.
#[no_mangle]
pub extern "C" fn mathutil_clamp_int(min: c_int, value: c_int, max: c_int) -> c_int {
    value.clamp(min, max)
}

/// Clamps the given value between the min and max values (float version)
/// May behave strangely if min > max.
#[no_mangle]
pub extern "C" fn mathutil_clamp_float(min: f32, value: f32, max: f32) -> f32 {
    value.clamp(min, max)
}

/// Clamps the given value between the min and max values (double version)
/// May behave strangely if min > max.
#[no_mangle]
pub extern "C" fn mathutil_clamp_double(min: f64, value: f64, max: f64) -> f64 {
    value.clamp(min, max)
}

/// Like Math.floorDiv, but reverse in that it is a ceilDiv
#[no_mangle]
pub extern "C" fn mathutil_ceil_div(value: c_int, divider: c_int) -> c_int {
    -(-value).div_euclid(divider)
}

/// Returns the minimum of two bytes
#[no_mangle]
pub extern "C" fn mathutil_min_byte(a: i8, b: i8) -> i8 {
    a.min(b)
}

/// Returns the maximum of two bytes
#[no_mangle]
pub extern "C" fn mathutil_max_byte(a: i8, b: i8) -> i8 {
    a.max(b)
}

/// Fast inverse square root (Quake III algorithm)
/// This is copied from Minecraft's MathHelper class
#[no_mangle]
pub extern "C" fn mathutil_fast_inv_sqrt(numb: f32) -> f32 {
    let half = 0.5f32 * numb;
    let i = numb.to_bits();
    let i = 0x5f3759df - (i >> 1);
    let numb = f32::from_bits(i);
    numb * (1.5f32 - half * numb * numb)
}

/// Returns the square of a float (x^2)
#[no_mangle]
pub extern "C" fn mathutil_pow2_float(x: f32) -> f32 {
    x * x
}

/// Returns the square of a double (x^2)
#[no_mangle]
pub extern "C" fn mathutil_pow2_double(x: f64) -> f64 {
    x * x
}

/// Returns the square of an int (x^2)
#[no_mangle]
pub extern "C" fn mathutil_pow2_int(x: c_int) -> c_int {
    x * x
}

/// Returns the square of a long (x^2)
#[no_mangle]
pub extern "C" fn mathutil_pow2_long(x: i64) -> i64 {
    x * x
}

/// Equivalent to Log_2(numb)
#[no_mangle]
pub extern "C" fn mathutil_log2(numb: c_int) -> c_int {
    // Using the natural log and converting to log base 2
    ((numb as f64).ln() / 2.0f64.ln()) as c_int
}

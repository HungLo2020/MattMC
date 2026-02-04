// Rust FFI implementation of BitShiftUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.coreapi.util.BitShiftUtil
// Author: James Seibel (original Java), migrated to Rust for performance
//
// A list of helper methods to make code easier to read.
// Specifically written because bit shifts short circuit James' brain.

use std::os::raw::c_int;

/// Equivalent to: 1 << value, 2^value, Math.pow(2, value)
/// Note: Math.pow() isn't identical for large values where bits would be lost in the shift,
/// however for medium to small values they function the same.
/// Can also be used to replace bit shifts in the format:
/// multiplier << value;
/// multiplier * powerOfTwo(value);
#[no_mangle]
pub extern "C" fn bitshiftutil_power_of_two_int(value: c_int) -> c_int {
    1 << value
}

/// Equivalent to: 1 << value (long version)
/// See bitshiftutil_power_of_two_int for documentation
#[no_mangle]
pub extern "C" fn bitshiftutil_power_of_two_long(value: i64) -> i64 {
    1i64 << value
}

/// Equivalent to: value >> 1, value / 2
/// Note: value / 2 isn't identical for negative values
#[no_mangle]
pub extern "C" fn bitshiftutil_half_int(value: c_int) -> c_int {
    value >> 1
}

/// Equivalent to: value >> 1 (long version)
/// See bitshiftutil_half_int for documentation
#[no_mangle]
pub extern "C" fn bitshiftutil_half_long(value: i64) -> i64 {
    value >> 1
}

/// Equivalent to: value >> power, value / 2^power
/// Note: value / 2^power isn't identical for negative values
#[no_mangle]
pub extern "C" fn bitshiftutil_divide_by_power_of_two_int(value: c_int, power: c_int) -> c_int {
    value >> power
}

/// Equivalent to: value >> power (long version)
/// See bitshiftutil_divide_by_power_of_two_int for documentation
#[no_mangle]
pub extern "C" fn bitshiftutil_divide_by_power_of_two_long(value: i64, power: i64) -> i64 {
    value >> power
}

/// Equivalent to: value << 1, value^2, Math.pow(value, 2)
/// Note: Math.pow() isn't identical for large values where bits would be lost in the shift,
/// however for medium to small values they function the same.
#[no_mangle]
pub extern "C" fn bitshiftutil_square_int(value: c_int) -> c_int {
    value << 1
}

/// Equivalent to: value << 1 (long version)
/// See bitshiftutil_square_int for documentation
#[no_mangle]
pub extern "C" fn bitshiftutil_square_long(value: i64) -> i64 {
    value << 1
}

/// Equivalent to: value << power, value^power, Math.pow(value, power)
/// Note: Math.pow() isn't identical for large values where bits would be lost in the shift,
/// however for medium to small values they function the same.
#[no_mangle]
pub extern "C" fn bitshiftutil_pow_int(value: c_int, power: c_int) -> c_int {
    value << power
}

/// Equivalent to: value << power (long version)
/// See bitshiftutil_pow_int for documentation
#[no_mangle]
pub extern "C" fn bitshiftutil_pow_long(value: i64, power: i64) -> i64 {
    value << power
}

// Rust FFI implementation of VarintUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.sql.dto.util.VarintUtil
// Author: James Seibel (original Java), migrated to Rust for performance

use std::os::raw::c_int;

/// zigzagEncode maps 0=>0, -1=>1, 1=>2, -2=>3, 3=>4, etc.
/// this helps encode small magnitude signed numbers as small varints.
/// https://lemire.me/blog/2022/11/25/making-all-your-integers-positive-with-zigzag-encoding/
#[no_mangle]
pub extern "C" fn varintutil_zigzag_encode(n: c_int) -> c_int {
    // if n is (byte)-1, this results in:
    // 0b1111_1110 ^ 0b1111_1111 == 0b0000_0001
    (n << 1) ^ (n >> 31)
}

/// Decodes a zigzag-encoded integer back to signed form
#[no_mangle]
pub extern "C" fn varintutil_zigzag_decode(n: c_int) -> c_int {
    ((n as u32) >> 1) as c_int ^ -(n & 1)
}

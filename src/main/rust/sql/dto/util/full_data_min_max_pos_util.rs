// Rust FFI implementation of FullDataMinMaxPosUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.sql.dto.util.FullDataMinMaxPosUtil
// Author: James Seibel (original Java), migrated to Rust for performance

use std::os::raw::c_int;

const ADJ_POS_MASK: i64 = ((1 << 16) - 1) as i64; // 2^16 - 1 for short size
const MIN_X_OFFSET: i32 = 0;
const MAX_X_OFFSET: i32 = 16; // Short.SIZE
const MIN_Z_OFFSET: i32 = 32; // Short.SIZE * 2
const MAX_Z_OFFSET: i32 = 48; // Short.SIZE * 3

/// Encodes min/max X/Z relative positions into a single long value
#[no_mangle]
pub extern "C" fn fulldataminmaxposutil_encode(
    min_x: i16,
    max_x: i16,
    min_z: i16,
    max_z: i16,
) -> i64 {
    let mut data: i64 = 0;
    data |= (min_x as i64) << MIN_X_OFFSET;
    data |= (max_x as i64) << MAX_X_OFFSET;
    data |= (min_z as i64) << MIN_Z_OFFSET;
    data |= (max_z as i64) << MAX_Z_OFFSET;
    data
}

/// Decodes minX from encoded position
#[no_mangle]
pub extern "C" fn fulldataminmaxposutil_get_min_x(encoded_min_max_pos: i64) -> c_int {
    ((encoded_min_max_pos >> MIN_X_OFFSET) & ADJ_POS_MASK) as c_int
}

/// Decodes maxX from encoded position
#[no_mangle]
pub extern "C" fn fulldataminmaxposutil_get_max_x(encoded_min_max_pos: i64) -> c_int {
    ((encoded_min_max_pos >> MAX_X_OFFSET) & ADJ_POS_MASK) as c_int
}

/// Decodes minZ from encoded position
#[no_mangle]
pub extern "C" fn fulldataminmaxposutil_get_min_z(encoded_min_max_pos: i64) -> c_int {
    ((encoded_min_max_pos >> MIN_Z_OFFSET) & ADJ_POS_MASK) as c_int
}

/// Decodes maxZ from encoded position
#[no_mangle]
pub extern "C" fn fulldataminmaxposutil_get_max_z(encoded_min_max_pos: i64) -> c_int {
    ((encoded_min_max_pos >> MAX_Z_OFFSET) & ADJ_POS_MASK) as c_int
}

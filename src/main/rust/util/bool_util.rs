// Rust FFI implementation of BoolUtil
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.BoolUtil
// Author: James Seibel (original Java), migrated to Rust for performance

/// Used to prevent null Boolean objects in if statements
/// Returns false if the value is 0 (representing null from Java), otherwise returns the boolean value
#[no_mangle]
pub extern "C" fn boolutil_false_if_null(value: i32, is_null: i32) -> i32 {
    if is_null != 0 {
        0 // false
    } else {
        value
    }
}

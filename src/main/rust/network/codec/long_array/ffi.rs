pub(super) const MAX_VALUES: i32 = 8192;

/// Runtime dispatch is initialized outside critical/heap-borrowing calls.
#[no_mangle]
pub extern "C" fn mattmc_long_array_initialize() -> i32 { super::convert::initialize() }

/// # Safety
/// Aligned values covers count immutable longs. Disjoint bytes covers byte_len
/// exclusively writable bytes. Both remain live; no pointers are retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_long_array_encode(values: *const u64, count: i32,
    bytes: *mut u8, byte_len: i32) -> i32 {
    if !valid(values, count, bytes, byte_len) { return -1; }
    super::convert::encode(std::slice::from_raw_parts(values, count as usize),
        std::slice::from_raw_parts_mut(bytes, byte_len as usize));
    0
}

/// # Safety
/// bytes covers byte_len immutable bytes; aligned disjoint values covers count
/// exclusively writable longs. No allocations, callbacks or pointer retention.
#[no_mangle]
pub unsafe extern "C" fn mattmc_long_array_decode(bytes: *const u8, byte_len: i32,
    values: *mut u64, count: i32) -> i32 {
    if !valid(values, count, bytes, byte_len) { return -1; }
    super::convert::decode(std::slice::from_raw_parts(bytes, byte_len as usize),
        std::slice::from_raw_parts_mut(values, count as usize));
    0
}

fn valid(values: *const u64, count: i32, bytes: *const u8, byte_len: i32) -> bool {
    !values.is_null() && !bytes.is_null() && values as usize % 8 == 0
        && (1..=MAX_VALUES).contains(&count) && byte_len == count * 8
}

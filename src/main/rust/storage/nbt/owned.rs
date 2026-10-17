//! Unique CPU tape storage shared by region production and section decoding.
//! Explicit close drops the bulk bytes; the small wrapper lives until its FFM
//! scope releases it. A mutex serializes copy/decode against explicit close.
use std::sync::Mutex;

pub(crate) struct OwnedTape {
    pub(crate) bytes: Mutex<Option<Vec<u8>>>,
}
impl OwnedTape {
    pub(crate) fn new(bytes: Vec<u8>) -> *mut Self {
        Box::into_raw(Box::new(Self { bytes: Mutex::new(Some(bytes)) }))
    }
}

/// # Safety
/// Live tape owner. Idempotently drops bytes without invalidating the wrapper.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_owned_tape_close(owner: *const OwnedTape) {
    if let Some(owner) = owner.as_ref() { owner.bytes.lock().unwrap().take(); }
}
/// # Safety
/// Unique wrapper, released once after all users of the FFM scope finish.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_owned_tape_release(owner: *mut OwnedTape) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}
/// # Safety
/// Live owner and disjoint writable output of exact length. Output may be a
/// synchronous critical heap borrow. No output pointer is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_owned_tape_copy(owner: *const OwnedTape, output: *mut u8, length: i32) -> i32 {
    let Some(owner) = owner.as_ref() else { return -1 };
    let bytes = owner.bytes.lock().unwrap();
    let Some(bytes) = bytes.as_ref() else { return -1 };
    if length < 0 || bytes.len() != length as usize || (!bytes.is_empty() && output.is_null()) { return -1; }
    if !bytes.is_empty() { std::ptr::copy_nonoverlapping(bytes.as_ptr(), output, bytes.len()); }
    0
}

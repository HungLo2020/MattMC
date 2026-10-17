//! Private CPU owner ABI. All calls require a live, exclusively borrowed owner.
//! Retirement output is borrowed until the next retire/release call only.
use super::{Lifetime, Retirement};

#[no_mangle]
pub extern "C" fn mattmc_mesh_lifetime_create() -> *mut Lifetime { Box::into_raw(Box::default()) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_release(owner: *mut Lifetime) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_observe(owner: *mut Lifetime, key: i64, generation: i64, frame: i64) -> i32 {
    owner.as_mut().map_or(-1, |o| if o.observe(key, generation, frame) { 0 } else { -1 })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_checkpoint(owner: *mut Lifetime, frame: i64) -> i64 {
    owner.as_mut().and_then(|o| o.checkpoint(frame)).map_or(-1, |n| n as i64)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_rollback(owner: *mut Lifetime, frame: i64, count: i64) -> i32 {
    if count < 0 { return -1; }
    owner.as_mut().map_or(-1, |o| if o.rollback(frame, count as usize) { 0 } else { -1 })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_discard(owner: *mut Lifetime) {
    if let Some(o) = owner.as_mut() { o.discard(); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_forget(owner: *mut Lifetime, key: i64) {
    if let Some(o) = owner.as_mut() { o.forget(key); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_size(owner: *const Lifetime) -> i32 {
    owner.as_ref().map_or(-1, |o| o.entries.len() as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_retire(owner: *mut Lifetime, frame: i64) -> i32 {
    owner.as_mut().map_or(-1, |o| if o.retire(frame) { o.retired.len() as i32 } else { -1 })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_mesh_lifetime_retirements(owner: *const Lifetime) -> *const Retirement {
    owner.as_ref().map_or(std::ptr::null(), |o| o.retired.as_ptr())
}

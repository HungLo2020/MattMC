//! Private CPU handles. Java fences owner arenas and releases each invocation in
//! a finally block. Native output contains numbers only, never Java pointers.
use std::sync::Arc;

use super::player_loading::{SharedAdmission, SharedState, Walk};
use super::ticket_storage::Fault;

fn fault(result: Result<(), Fault>) -> i32 {
    match result { Ok(()) => 0, Err(Fault::Allocation) => -3, Err(_) => -14 }
}

unsafe fn state<'a>(id: u64) -> &'a Arc<SharedState> {
    &*std::ptr::with_exposed_provenance::<Arc<SharedState>>(id as usize)
}

unsafe fn admission<'a>(id: u64) -> &'a Arc<SharedAdmission> {
    &*std::ptr::with_exposed_provenance::<Arc<SharedAdmission>>(id as usize)
}

unsafe fn walk<'a>(id: u64) -> &'a mut Walk {
    &mut *std::ptr::with_exposed_provenance_mut::<Walk>(id as usize)
}

#[no_mangle]
pub extern "C" fn mattmc_player_loading_create(maximum_distance: i32) -> u64 {
    if !(-2..=251).contains(&maximum_distance) { return 0; }
    match SharedState::new(maximum_distance) {
        Ok(value) => Box::into_raw(Box::new(Arc::new(value))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Arc<SharedState>>(id as usize))); }
}

/// The Java graph facade holds its existing exclusion. Drain before applying,
/// matching the old Java change replay even when a subsequent map write fails.
#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_take_player(id: u64, graph: u64, field: i32) -> i32 {
    if !(0..2).contains(&field) { return -14; }
    let field = super::ffi::handle(graph).field_mut(field as usize);
    let mut changes = Vec::new();
    if changes.try_reserve_exact(field.changes().len()).is_err() { return -3; }
    changes.extend_from_slice(field.changes());
    field.clear_changes();
    fault(state(id).lock().apply(&changes))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_queue_level(id: u64, key: i64) -> i32 {
    state(id).lock().queue_level(key)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_set_queue_level(id: u64, key: i64, level: i32) -> i32 {
    fault(state(id).lock().set_queue_level(key, level))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admits(id: u64, key: i64) -> i32 {
    i32::from(state(id).lock().admits(key))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_mark_release(id: u64, key: i64) -> i32 {
    fault(state(id).lock().mark_release(key))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_retained_bytes(id: u64) -> u64 {
    state(id).retained_bytes() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_walk(id: u64, kind: i32, target: i32) -> u64 {
    if !(0..3).contains(&kind) { return 0; }
    Box::into_raw(Box::new(Walk::loading(state(id).clone(), kind, target))).expose_provenance() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_walk_output(id: u64) -> *const i64 {
    walk(id).output.as_ptr()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_walk_next(id: u64, answer: i32) -> i32 {
    walk(id).next(answer)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_walk_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Walk>(id as usize))); }
}

#[no_mangle]
pub extern "C" fn mattmc_player_loading_admission_create(maximum: i32) -> u64 {
    match SharedAdmission::new(maximum) {
        Ok(value) => Box::into_raw(Box::new(Arc::new(value))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admission_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Arc<SharedAdmission>>(id as usize))); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admission_may_pop(id: u64) -> i32 {
    i32::from(admission(id).may_pop())
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admission_started(id: u64, key: i64) -> i32 {
    fault(admission(id).started(key))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admission_released(id: u64, key: i64) -> i32 {
    fault(admission(id).released(key))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admission_walk(id: u64) -> u64 {
    Box::into_raw(Box::new(Walk::admission(admission(id).clone()))).expose_provenance() as u64
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_loading_admission_retained_bytes(id: u64) -> u64 {
    admission(id).retained_bytes() as u64
}

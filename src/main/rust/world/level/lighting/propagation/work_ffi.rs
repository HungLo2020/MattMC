//! Primary CPU queue boundary; no lock survives a Java callback.
use super::work::Queues;
use std::{ffi::c_void, sync::Arc};

#[no_mangle]
pub extern "C" fn mattmc_light_work_create() -> *const c_void {
    Arc::into_raw(Arc::new(Queues::default())).cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_work_release(owner: *const c_void) {
    if !owner.is_null() {
        drop(Arc::from_raw(owner.cast::<Queues>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_work_view(owner: *const c_void) -> *const c_void {
    owner
        .cast::<Queues>()
        .as_ref()
        .map_or(std::ptr::null(), |q| q.counts.as_ptr().cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_work_enqueue(
    owner: *const c_void,
    side: i32,
    position: i64,
    entry: i64,
) -> i32 {
    let Some(q) = owner.cast::<Queues>().as_ref() else {
        return -1;
    };
    q.enqueue(side, position, entry)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_work_pop(
    owner: *const c_void,
    side: i32,
    out: *mut i64,
) -> i32 {
    let (Some(q), Some(out)) = (owner.cast::<Queues>().as_ref(), out.as_mut()) else {
        return -1;
    };
    if !(0..2).contains(&side) {
        return -1;
    }
    let mut queues = q.data.lock().unwrap();
    let Some(value) = queues[side as usize].pop_front() else {
        return 1;
    };
    *out = value;
    q.publish(&queues);
    0
}

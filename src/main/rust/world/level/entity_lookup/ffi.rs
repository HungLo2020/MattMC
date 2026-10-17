//! Bounded scalar identity mutations and borrowed read-only CPU views.
use super::{Registry, Uuid, View};
use std::{ffi::c_void, sync::Mutex};

struct Context {
    registry: Mutex<Registry>,
    view: View,
}
fn uuid(present: i32, high: u64, low: u64) -> Uuid {
    if present == 0 {
        None
    } else {
        Some((high, low))
    }
}
#[no_mangle]
pub extern "C" fn mattmc_entity_lookup_create() -> *mut c_void {
    let registry = Registry::default();
    let view = View::default();
    registry.publish(&view);
    Box::into_raw(Box::new(Context {
        registry: Mutex::new(registry),
        view,
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_release(owner: *mut c_void) {
    if !owner.is_null() {
        drop(Box::from_raw(owner.cast::<Context>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_view(owner: *const c_void) -> *const c_void {
    owner
        .cast::<Context>()
        .as_ref()
        .map_or(std::ptr::null(), |c| (&c.view as *const View).cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_begin_add(
    owner: *const c_void,
    present: i32,
    high: u64,
    low: u64,
) -> u64 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return 0;
    };
    let mut registry = c.registry.lock().unwrap();
    let token = registry.begin_add(uuid(present, high, low));
    registry.publish(&c.view);
    token
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_finish_add(
    owner: *const c_void,
    token: u64,
    commit: i32,
    id: i32,
    freed: *mut u64,
) -> i32 {
    if freed.is_null() {
        return -1;
    }
    freed.write(0);
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut registry = c.registry.lock().unwrap();
    let Some(retired) = registry.finish_add(token, (commit != 0).then_some(id)) else {
        return -1;
    };
    freed.write(retired);
    registry.publish(&c.view);
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_remove_uuid(
    owner: *const c_void,
    present: i32,
    high: u64,
    low: u64,
) -> u64 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return 0;
    };
    let mut registry = c.registry.lock().unwrap();
    let retired = registry.remove_uuid(uuid(present, high, low));
    registry.publish(&c.view);
    retired
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_remove_id(owner: *const c_void, id: i32) -> u64 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return 0;
    };
    let mut registry = c.registry.lock().unwrap();
    let retired = registry.remove_id(id);
    registry.publish(&c.view);
    retired
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_get_uuid(
    owner: *const c_void,
    present: i32,
    high: u64,
    low: u64,
) -> u64 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return 0;
    };
    *c.registry
        .lock()
        .unwrap()
        .uuids
        .get(&uuid(present, high, low))
        .unwrap_or(&0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_lookup_get_id(owner: *const c_void, id: i32) -> u64 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return 0;
    };
    c.registry.lock().unwrap().ids.get(id)
}

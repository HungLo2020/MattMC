//! Scalar mutations and borrowed immutable CPU pages; no lock spans a Java callback.
use super::{IterationView, Order, OrderError};
use std::{ffi::c_void, sync::Mutex};

struct Context {
    order: Mutex<Order>,
}

#[repr(C)]
#[derive(Default)]
struct Mutation {
    new: u64,
    old: u64,
    pinned: u64,
}

#[repr(C)]
#[derive(Default)]
struct Retired {
    pointer: usize,
    count: u64,
}

fn status(error: OrderError) -> i32 {
    match error {
        OrderError::AlreadyIterating => -2,
        OrderError::NotIterating => -3,
        OrderError::PendingBindingClear => -4,
    }
}

#[no_mangle]
pub extern "C" fn mattmc_entity_order_create() -> *mut c_void {
    Box::into_raw(Box::new(Context {
        order: Mutex::new(Order::default()),
    }))
    .cast()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_release(owner: *mut c_void) {
    if !owner.is_null() {
        drop(Box::from_raw(owner.cast::<Context>()));
    }
}

/// Capture the physical map receiver after Java's pre-getter copy/swap.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_prepare(owner: *mut c_void) -> i32 {
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    context
        .order
        .lock()
        .unwrap()
        .receiver(true)
        .map_or_else(status, |index| index as i32 + 1)
}

/// Contains captures its receiver without preparing a mutation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_receiver(owner: *mut c_void) -> i32 {
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    context
        .order
        .lock()
        .unwrap()
        .receiver(false)
        .map_or_else(status, |index| index as i32 + 1)
}

fn receiver_index(receiver: i32) -> Option<usize> {
    match receiver {
        1 => Some(0),
        2 => Some(1),
        _ => None,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_add(
    owner: *mut c_void,
    receiver: i32,
    key: i32,
    result: *mut c_void,
) -> i32 {
    if result.is_null() {
        return -1;
    }
    result.cast::<Mutation>().write(Mutation::default());
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(receiver) = receiver_index(receiver) else {
        return -1;
    };
    let change = context.order.lock().unwrap().add_to(receiver, key);
    result.cast::<Mutation>().write(Mutation {
        new: change.new,
        old: change.old.unwrap_or(0),
        pinned: u64::from(change.old_pinned),
    });
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_remove(
    owner: *mut c_void,
    receiver: i32,
    key: i32,
    result: *mut c_void,
) -> i32 {
    if result.is_null() {
        return -1;
    }
    result.cast::<Mutation>().write(Mutation::default());
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(receiver) = receiver_index(receiver) else {
        return -1;
    };
    if let Some((old, pinned)) = context.order.lock().unwrap().remove_from(receiver, key) {
        result.cast::<Mutation>().write(Mutation {
            new: 0,
            old,
            pinned: u64::from(pinned),
        });
    }
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_contains(
    owner: *mut c_void,
    receiver: i32,
    key: i32,
) -> i32 {
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(receiver) = receiver_index(receiver) else {
        return -1;
    };
    i32::from(context.order.lock().unwrap().contains_in(receiver, key))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_begin(owner: *mut c_void, view: *mut c_void) -> i32 {
    if view.is_null() {
        return -1;
    }
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut order = context.order.lock().unwrap();
    if let Err(error) = order.begin() {
        return status(error);
    }
    view.cast::<IterationView>()
        .write(order.iteration_view().unwrap());
    0
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_end(owner: *mut c_void, view: *mut c_void) -> i32 {
    if view.is_null() {
        return -1;
    }
    view.cast::<Retired>().write(Retired::default());
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut order = context.order.lock().unwrap();
    match order.end() {
        Ok(retired) => {
            view.cast::<Retired>().write(Retired {
                pointer: if retired.is_empty() {
                    0
                } else {
                    retired.as_ptr() as usize
                },
                count: retired.len() as u64,
            });
            0
        }
        Err(error) => status(error),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_order_acknowledge(owner: *mut c_void) -> i32 {
    let Some(context) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    context.order.lock().unwrap().acknowledge_clear();
    0
}

#[no_mangle]
pub extern "C" fn mattmc_entity_order_node_stride() -> u64 {
    std::mem::size_of::<super::Node>() as u64
}

#[no_mangle]
pub extern "C" fn mattmc_entity_order_view_size() -> u64 {
    std::mem::size_of::<IterationView>() as u64
}

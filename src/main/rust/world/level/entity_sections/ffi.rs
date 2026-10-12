//! Scalar member mutations and immutable retained CPU-array leases.
use super::{
    bindings::Bindings,
    members::{AddError, Array, Members, RemoveError, View},
};
use std::{
    ffi::c_void,
    sync::{Arc, Mutex},
};
struct Context {
    bindings: Arc<Bindings>,
    groups: Mutex<Vec<Box<Members>>>,
}
#[repr(C)]
struct Change {
    source: i32,
    destination: i32,
    length: i32,
    capacity: i32,
}
#[repr(C)]
struct Capture {
    lease: *mut c_void,
    pointer: *const c_void,
    capacity: i32,
    size: i32,
}
#[no_mangle]
pub extern "C" fn mattmc_section_members_create() -> *mut c_void {
    let bindings = Arc::new(Bindings::default());
    let root = Box::new(Members::with_bindings(bindings.clone()));
    Box::into_raw(Box::new(Context {
        bindings,
        groups: Mutex::new(vec![root]),
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_release(owner: *mut c_void) {
    if !owner.is_null() {
        drop(Box::from_raw(owner.cast::<Context>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_view(
    owner: *const c_void,
    group: i32,
) -> *const c_void {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return std::ptr::null();
    };
    let groups = c.groups.lock().unwrap();
    groups
        .get(group as usize)
        .map_or(std::ptr::null(), |m| (&m.view as *const View).cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_new_group(owner: *const c_void) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut groups = c.groups.lock().unwrap();
    let id = i32::try_from(groups.len()).unwrap();
    groups.push(Box::new(Members::with_bindings(c.bindings.clone())));
    id
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_allocate(owner: *const c_void) -> u64 {
    owner
        .cast::<Context>()
        .as_ref()
        .map_or(0, |c| c.bindings.allocate())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_finish_binding(owner: *const c_void, token: u64) {
    if let Some(c) = owner.cast::<Context>().as_ref() {
        c.bindings.release(token);
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_retired(owner: *const c_void) -> u64 {
    owner
        .cast::<Context>()
        .as_ref()
        .map_or(0, |c| c.bindings.retired())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_add(
    owner: *const c_void,
    group: i32,
    token: u64,
    out: *mut Change,
) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let mut groups = c.groups.lock().unwrap();
    let Some(m) = groups.get_mut(group as usize) else {
        return -1;
    };
    match m.add(token) {
        Ok(()) => 0,
        Err(AddError::StoreBounds { index, capacity }) => {
            if let Some(out) = out.as_mut() {
                *out = Change {
                    source: index,
                    destination: 0,
                    length: 0,
                    capacity: capacity as i32,
                };
            }
            2
        }
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_capture(
    owner: *const c_void,
    group: i32,
    out: *mut Capture,
) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(out) = out.as_mut() else { return -1 };
    let groups = c.groups.lock().unwrap();
    let Some(m) = groups.get(group as usize) else {
        return -1;
    };
    let array = m.capture();
    let pointer = array.cells.as_ptr().cast();
    let capacity = array.cells.len() as i32;
    *out = Capture {
        lease: Box::into_raw(Box::new(array)).cast(),
        pointer,
        capacity,
        size: m.size(),
    };
    0
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_release_capture(lease: *mut c_void) {
    if !lease.is_null() {
        drop(Box::from_raw(lease.cast::<Arc<Array>>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_section_members_remove(
    owner: *const c_void,
    group: i32,
    lease: *const c_void,
    index: i32,
    out: *mut Change,
) -> i32 {
    let Some(c) = owner.cast::<Context>().as_ref() else {
        return -1;
    };
    let Some(array) = lease.cast::<Arc<Array>>().as_ref() else {
        return -1;
    };
    let mut groups = c.groups.lock().unwrap();
    let Some(m) = groups.get_mut(group as usize) else {
        return -1;
    };
    match m.remove_from(array, index) {
        Ok(()) => 0,
        Err(RemoveError::CopyBounds {
            source,
            destination,
            length,
            capacity,
        }) => {
            if let Some(out) = out.as_mut() {
                *out = Change {
                    source,
                    destination,
                    length,
                    capacity: capacity as i32,
                }
            };
            1
        }
        Err(RemoveError::StoreBounds { index, capacity }) => {
            if let Some(out) = out.as_mut() {
                *out = Change {
                    source: index,
                    destination: 0,
                    length: 0,
                    capacity: capacity as i32,
                }
            };
            2
        }
    }
}

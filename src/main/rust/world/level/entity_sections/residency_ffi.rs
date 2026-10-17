//! Scalar residency mutation and retained CPU cursor boundaries.
use super::{
    residency::{store_action, Residency},
    residency_set::{Cursor, Failure},
};
use std::{
    collections::HashMap,
    ffi::c_void,
    sync::{
        atomic::{AtomicI32, Ordering},
        Arc, Mutex,
    },
};
struct Data {
    residency: Residency,
    cursors: HashMap<u64, (usize, Cursor)>,
    next: u64,
}
struct Owner {
    data: Mutex<Data>,
    counts: [AtomicI32; 5],
}
#[repr(C)]
#[derive(Default)]
struct View {
    remaining: i32,
    status: i32,
    key: i64,
    index: i32,
    length: i32,
}
struct Lease {
    owner: Arc<Owner>,
    id: u64,
    view: View,
}
impl Owner {
    fn publish(&self, d: &Data) {
        for (cell, value) in self.counts.iter().zip(d.residency.counts()) {
            cell.store(value, Ordering::Release);
        }
    }
}
#[no_mangle]
pub extern "C" fn mattmc_entity_residency_create() -> *const c_void {
    Arc::into_raw(Arc::new(Owner {
        data: Mutex::new(Data {
            residency: Residency::default(),
            cursors: HashMap::new(),
            next: 0,
        }),
        counts: std::array::from_fn(|_| AtomicI32::new(0)),
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_release(owner: *const c_void) {
    if !owner.is_null() {
        drop(Arc::from_raw(owner.cast::<Owner>()));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_view(owner: *const c_void) -> *const c_void {
    owner
        .cast::<Owner>()
        .as_ref()
        .map_or(std::ptr::null(), |o| o.counts.as_ptr().cast())
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_uuid(
    owner: *const c_void,
    action: i32,
    present: i32,
    high: u64,
    low: u64,
) -> i32 {
    let Some(o) = owner.cast::<Owner>().as_ref() else {
        return -2;
    };
    let key = if present == 0 {
        None
    } else {
        Some((high, low))
    };
    let mut d = o.data.lock().unwrap();
    let result = match action {
        0 => d.residency.known.contains(&key),
        1 => d.residency.known.insert(key),
        2 => d.residency.known.remove(&key),
        _ => return -2,
    };
    if action != 0 {
        o.publish(&d);
    }
    i32::from(result)
}
/// 0=get,1=put,2=remove,3=contains,4=default get,5=default set.
/// Absent/present-null/present-enum are encoded distinctly in the primary table.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_map(
    owner: *const c_void,
    map: i32,
    action: i32,
    key: i64,
    value: i32,
) -> i32 {
    let Some(o) = owner.cast::<Owner>().as_ref() else {
        return -3;
    };
    if !(0..2).contains(&map) {
        return -3;
    }
    let m = map as usize;
    let mut d = o.data.lock().unwrap();
    let old = d.residency.get(m, key);
    let result = match action {
        0 => old,
        1 => {
            if !(-1..=2).contains(&value) {
                return -3;
            }
            d.residency.maps[m]
                .put(key, (value + 2) as u64)
                .expect("scalar map insertion owns its current slot");
            old
        }
        2 => {
            d.residency.maps[m]
                .remove(key)
                .expect("scalar map removal owns its current slot");
            old
        }
        3 => i32::from(d.residency.maps[m].find(key).is_ok()),
        4 => d.residency.defaults[m],
        5 => {
            if !(-1..=2).contains(&value) {
                return -3;
            }
            let prior = d.residency.defaults[m];
            d.residency.defaults[m] = value;
            prior
        }
        _ => return -3,
    };
    if matches!(action, 1 | 2) {
        o.publish(&d);
    }
    result
}
/// 0=contains,1=add,2=remove,3=clear.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_set(
    owner: *const c_void,
    set: i32,
    action: i32,
    key: i64,
) -> i32 {
    let Some(o) = owner.cast::<Owner>().as_ref() else {
        return -2;
    };
    if !(0..2).contains(&set) {
        return -2;
    }
    let mut d = o.data.lock().unwrap();
    let s = &mut d.residency.sets[set as usize];
    let result = match action {
        0 => s.contains(key),
        1 => s.add(key),
        2 => s.remove(key),
        3 => {
            s.clear();
            true
        }
        _ => return -2,
    };
    if action != 0 {
        o.publish(&d);
    }
    i32::from(result)
}
/// 0=client visibility,1=needs load,2=should unload.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_policy(
    owner: *const c_void,
    action: i32,
    key: i64,
) -> i32 {
    let Some(o) = owner.cast::<Owner>().as_ref() else {
        return -3;
    };
    let d = o.data.lock().unwrap();
    match action {
        0 => d.residency.client_visibility(key),
        1 => i32::from(d.residency.needs_load(key)),
        2 => i32::from(d.residency.should_unload(key)),
        _ => -3,
    }
}
#[no_mangle]
pub extern "C" fn mattmc_entity_residency_store_action(captured: i32, empty: i32) -> i32 {
    store_action(captured, empty != 0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_loaded_keys(
    owner: *const c_void,
    out: *mut *const i64,
) -> i32 {
    let (Some(o), Some(out)) = (owner.cast::<Owner>().as_ref(), out.as_mut()) else {
        return -1;
    };
    let mut d = o.data.lock().unwrap();
    d.residency.loaded_keys();
    *out = d.residency.output.as_ptr();
    d.residency.output.len() as i32
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_cursor_create(
    owner: *const c_void,
    set: i32,
) -> *mut c_void {
    let Some(o) = owner.cast::<Owner>().as_ref() else {
        return std::ptr::null_mut();
    };
    if !(0..2).contains(&set) {
        return std::ptr::null_mut();
    }
    let mut d = o.data.lock().unwrap();
    let cursor = d.residency.sets[set as usize].cursor();
    let left = cursor.left;
    d.next = d
        .next
        .checked_add(1)
        .expect("residency cursor identity exhausted");
    let id = d.next;
    d.cursors.insert(id, (set as usize, cursor));
    Arc::increment_strong_count(owner.cast::<Owner>());
    Box::into_raw(Box::new(Lease {
        owner: Arc::from_raw(owner.cast::<Owner>()),
        id,
        view: View {
            remaining: left,
            ..Default::default()
        },
    }))
    .cast()
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_cursor_view(
    cursor: *const c_void,
) -> *const c_void {
    cursor
        .cast::<Lease>()
        .as_ref()
        .map_or(std::ptr::null(), |l| (&l.view as *const View).cast())
}
fn error(view: &mut View, result: Result<(), Failure>) {
    view.status = match result {
        Ok(()) => 0,
        Err(Failure::Exhausted) => 1,
        Err(Failure::NoCurrent) => 2,
        Err(Failure::ArrayBounds(i, n)) => {
            view.index = i;
            view.length = n as i32;
            3
        }
        Err(Failure::MissingWrapped) => 4,
        Err(Failure::ListBounds(i, n)) => {
            view.index = i;
            view.length = n as i32;
            5
        }
    };
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_cursor_step(
    cursor: *mut c_void,
    remove: i32,
) -> i32 {
    let Some(l) = cursor.cast::<Lease>().as_mut() else {
        return -1;
    };
    let mut d = l.owner.data.lock().unwrap();
    let Some((set, mut c)) = d.cursors.remove(&l.id) else {
        return -1;
    };
    let result = if remove != 0 {
        c.remove(&mut d.residency.sets[set])
    } else {
        c.next(&d.residency.sets[set]).map(|key| {
            l.view.key = key;
        })
    };
    l.view.remaining = c.left;
    error(&mut l.view, result);
    d.cursors.insert(l.id, (set, c));
    if remove != 0 {
        l.owner.publish(&d);
    }
    l.view.status
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_cursor_close(cursor: *mut c_void) {
    if let Some(l) = cursor.cast::<Lease>().as_mut() {
        l.owner.data.lock().unwrap().cursors.remove(&l.id);
        l.view.remaining = 0;
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_residency_cursor_release(cursor: *mut c_void) {
    if !cursor.is_null() {
        mattmc_entity_residency_cursor_close(cursor);
        drop(Box::from_raw(cursor.cast::<Lease>()));
    }
}

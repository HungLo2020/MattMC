//! `NativeChunkTicks` downcalls. Hot calls take the container address as an
//! integer (the same ABI as a pointer on 64-bit targets). Ids are `i32`; -1
//! means none and `BUSY` (-2) means the container was already in use by an
//! overlapping call, which Java reports as concurrent misuse.
use super::{Collected, Container, Entry, Queue, Registry, RegistryError, LIVE, REGISTRIES};
use std::sync::Arc;
use std::sync::atomic::Ordering::Relaxed;

const BUSY: i32 = -2;
fn id(value: Option<u32>) -> i32 { value.map_or(-1, |v| v as i32) }
/// Runs `f` with exclusive access, or returns `BUSY`.
unsafe fn with(address: i64, f: impl FnOnce(&mut Queue) -> i32) -> i32 {
    match (*(address as *const Container)).enter() {
        Some(mut queue) => f(&mut queue),
        None => BUSY,
    }
}

/// After each mutation, report [head id, size, mod count] into the caller's
/// buffer so Java's peek/size/fail-fast checks need no further call. The buffer
/// is Java-owned and read by the same thread after this call returns.
pub(crate) unsafe fn report_to(c: &Queue, out: i64) { report(c, out) }
unsafe fn report(c: &Queue, out: i64) {
    let out = &mut *(out as *mut [i32; 4]);
    out[0] = id(c.peek());
    out[1] = c.size() as i32;
    out[2] = c.mod_count() as i32;
}

#[no_mangle]
pub extern "C" fn mattmc_chunk_ticks_create() -> *const Container { Arc::into_raw(Arc::new(Container::new())) }
/// Drops the Java owner's reference; registries hold their own.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_release(container: *const Container) {
    if !container.is_null() { drop(Arc::from_raw(container)); }
}
#[no_mangle]
pub extern "C" fn mattmc_chunk_ticks_live() -> i64 { LIVE.load(Relaxed) as i64 }
/// Live entries in one container's id table (allocated ids), for retention checks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_live_entries(address: i64) -> i32 {
    with(address, |c| c.live_entries() as i32)
}

/// Packs a call result (low 32 bits) with the queue head id after the call
/// (high 32 bits), so the hot path needs no report buffer.
fn with_head(result: i32, c: &Queue) -> i64 { (result as u32 as i64) | ((id(c.peek()) as i64) << 32) }

/// `schedule` (dedup first) or, with `unchecked`, `scheduleUnchecked`.
/// Low half: the new entry id, -1 when a dedup key already existed, or BUSY.
/// High half: the head id afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_schedule(address: i64, trigger_tick: i64, priority: i32, sub_tick_order: i64,
    x: i32, y: i32, z: i32, type_handle: i32, unchecked: i32) -> i64 {
    let entry = Entry { trigger_tick, priority, sub_tick_order, x, y, z, type_handle: type_handle as u32 };
    match (*(address as *const Container)).enter() {
        Some(mut c) => {
            let result = if unchecked != 0 { c.schedule_unchecked(entry) as i32 } else { id(c.schedule(entry)) };
            with_head(result, &c)
        }
        None => BUSY as u32 as i64,
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_add_key(address: i64, x: i32, y: i32, z: i32, type_handle: i32) -> i32 {
    with(address, |c| c.add_key(x, y, z, type_handle as u32) as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_contains_key(address: i64, x: i32, y: i32, z: i32, type_handle: i32) -> i32 {
    with(address, |c| c.contains_key(x, y, z, type_handle as u32) as i32)
}
/// `poll`: low half the polled id (-1 when empty, or BUSY), high half the new head.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_poll(address: i64) -> i64 {
    match (*(address as *const Container)).enter() {
        Some(mut c) => {
            let result = id(c.poll());
            with_head(result, &c)
        }
        None => BUSY as u32 as i64,
    }
}
/// Copies the queue's ids in array order into a Java-owned buffer of
/// `capacity` ints; returns the queue size (copying only when it fits).
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_copy_ids(address: i64, out: i64, capacity: i32) -> i32 {
    with(address, |c| {
        let size = c.size();
        if size <= capacity.max(0) as usize {
            let out = std::slice::from_raw_parts_mut(out as *mut i32, size);
            for (i, slot) in out.iter_mut().enumerate() { *slot = c.id_at(i).map_or(-1, |v| v as i32); }
        }
        size as i32
    })
}
/// `removeAt(index)`: returns the removed id (or -1); `out[3]` receives the id
/// that moved before the iterator cursor (or -1).
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_remove_at(address: i64, index: i32, out: i64) -> i32 {
    with(address, |c| {
        let result = if index < 0 { None } else { c.remove_at(index as usize) };
        report(c, out);
        (*(out as *mut [i32; 4]))[3] = result.and_then(|r| r.1).map_or(-1, |v| v as i32);
        id(result.map(|r| r.0))
    })
}

/// `clearArea`'s per-container removal (see `Queue::remove_in_box`). Writes
/// removed entry ids to `removed` (capacity must be at least the queue size)
/// and reports head/size/modCount. Returns the removed count or BUSY.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_remove_in_box(address: i64, min_x: i32, min_y: i32, min_z: i32,
    max_x: i32, max_y: i32, max_z: i32, removed: i64, capacity: i32, out: i64) -> i32 {
    with(address, |c| {
        if c.size() > capacity.max(0) as usize { return -4; }
        let ids = c.remove_in_box([min_x, min_y, min_z], [max_x, max_y, max_z]);
        let r = std::slice::from_raw_parts_mut(removed as *mut i32, ids.len());
        for (i, &id) in ids.iter().enumerate() { r[i] = id as i32; }
        report(c, out);
        ids.len() as i32
    })
}

/// Per-`LevelTicks` registry with the persistent collection heap.
#[no_mangle]
pub extern "C" fn mattmc_chunk_ticks_registry_create() -> *mut Registry { Box::into_raw(Box::new(Registry::new())) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_registry_release(registry: *mut Registry) {
    if !registry.is_null() { drop(Box::from_raw(registry)); }
}
#[no_mangle]
pub extern "C" fn mattmc_chunk_ticks_registry_live() -> i64 { REGISTRIES.load(Relaxed) as i64 }

const STALE: i32 = -5;
const CAPACITY: i32 = -4;
const EMPTY: i32 = -3;

/// Binds a container (its own `Arc` is cloned) with its Java report buffer.
/// Returns the handle or BUSY.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_register(registry: i64, container: i64, report_address: i64) -> i32 {
    let Some(mut r) = (*(registry as *const Registry)).enter() else { return BUSY };
    let raw = container as *const Container;
    Arc::increment_strong_count(raw);
    r.register(Arc::from_raw(raw), report_address) as i32
}
/// 1 when the handle was released now (Java drops its mirror), 0 when a
/// retained heap slot still holds it, STALE or BUSY.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_unregister(registry: i64, handle: i32) -> i32 {
    let Some(mut r) = (*(registry as *const Registry)).enter() else { return BUSY };
    if handle < 0 { return STALE; }
    match r.unregister(handle as u32) { Ok(freed) => freed as i32, Err(_) => STALE }
}

/// The collect phase. `eligible`: `count` handles in scan order. Outputs go to
/// Java-owned buffers: (handle, entry id) i32 pairs, (chunk key, trigger) i64
/// pairs, touched and freed handles; `counts` receives
/// [ticks, updates, touched, freed, heap size]. Returns 0, EMPTY (the
/// completed prefix is still written), CAPACITY (nothing changed; `counts`
/// holds the required tick, update and handle capacities), STALE or BUSY.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_ticks_collect(registry: i64, eligible: i64, count: i32, cleaned_up: i32, now: i64, budget: i64,
    ticks: i64, ticks_capacity: i32, updates: i64, updates_capacity: i32, touched: i64, freed: i64, handles_capacity: i32, counts: i64) -> i32 {
    let Some(mut r) = (*(registry as *const Registry)).enter() else { return BUSY };
    let eligible = std::slice::from_raw_parts(eligible as *const i32, count.max(0) as usize);
    let eligible: Vec<u32> = eligible.iter().map(|&h| h as u32).collect();
    let counts = &mut *(counts as *mut [i32; 5]);
    if r.handle_count() > handles_capacity.max(0) as usize {
        *counts = [0, 0, r.handle_count() as i32, 0, 0];
        return CAPACITY;
    }
    let (out, error): (Collected, Option<RegistryError>) = r.collect(&eligible, cleaned_up != 0, now, budget,
        ticks_capacity.max(0) as usize, updates_capacity.max(0) as usize);
    match error {
        Some(RegistryError::Capacity { ticks, updates }) => { *counts = [ticks as i32, updates as i32, 0, 0, 0]; return CAPACITY; }
        Some(RegistryError::StaleHandle) => return STALE,
        Some(RegistryError::Busy) => return BUSY,
        _ => {}
    }
    let t = std::slice::from_raw_parts_mut(ticks as *mut i32, out.ticks.len() * 2);
    for (i, &(h, id)) in out.ticks.iter().enumerate() { t[2 * i] = h as i32; t[2 * i + 1] = id as i32; }
    let u = std::slice::from_raw_parts_mut(updates as *mut i64, out.updates.len() * 2);
    for (i, &(k, v)) in out.updates.iter().enumerate() { u[2 * i] = k; u[2 * i + 1] = v; }
    let th = std::slice::from_raw_parts_mut(touched as *mut i32, out.touched.len());
    for (i, &h) in out.touched.iter().enumerate() { th[i] = h as i32; }
    let fh = std::slice::from_raw_parts_mut(freed as *mut i32, out.freed.len());
    for (i, &h) in out.freed.iter().enumerate() { fh[i] = h as i32; }
    *counts = [out.ticks.len() as i32, out.updates.len() as i32, out.touched.len() as i32, out.freed.len() as i32, out.heap_size as i32];
    if matches!(error, Some(RegistryError::EmptyContainer)) { EMPTY } else { 0 }
}

//! C ABI for the Java `NativeChunkHolders` bridge. A handle owns a locked
//! `Registry`; Java's auto arena releases it after its last call and fences it
//! across every downcall. Each call takes the lock and returns before Java
//! runs again. Calls may come from any thread (some readers are off-thread).
//!
//! Negative results are faults: -3 allocation, -14 illegal state; exports
//! return the negated pair count needed when the buffer is too small.
use std::sync::{Mutex, MutexGuard};

use super::{Registry, Scheduled};
use crate::world::level::chunk_distance::ticket_storage::Fault;

fn fault(fault: Fault) -> i64 {
    match fault {
        Fault::Allocation => -3,
        _ => -14,
    }
}

/// Caller must provide a live handle from create and keep its arena live.
unsafe fn registry<'a>(id: u64) -> MutexGuard<'a, Registry> {
    let registry = &*std::ptr::with_exposed_provenance::<Mutex<Registry>>(id as usize);
    // Nothing panics while holding it; a poisoned lock still holds whole state.
    registry.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Writes `(key, id)` pairs when they fit; else the negated pair count needed.
unsafe fn export(pairs: &[(i64, u32)], buffer: *mut i64, capacity: i32) -> i32 {
    if pairs.len() > capacity.max(0) as usize {
        return -(pairs.len().min(i32::MAX as usize) as i32);
    }
    for (index, &(key, id)) in pairs.iter().enumerate() {
        buffer.add(index * 2).write(key);
        buffer.add(index * 2 + 1).write(id as i64);
    }
    pairs.len() as i32
}

#[no_mangle]
pub extern "C" fn mattmc_chunk_holders_create(max_level: i32) -> u64 {
    match Registry::new(max_level) {
        Ok(registry) => Box::into_raw(Box::new(Mutex::new(registry))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Mutex<Registry>>(id as usize)));
    }
}

/// `updateChunkScheduling`'s decision: 0 unchanged, 1 set the given holder's
/// level, `2 | id << 8` revive pending holder `id`, `3 | id << 8` construct
/// holder `id` then commit it; negative on a fault.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_schedule(id: u64, key: i64, level: i32, has_holder: i32, old_level: i32) -> i64 {
    match registry(id).schedule(key, level, has_holder != 0, old_level) {
        Ok(Scheduled::Unchanged) => 0,
        Ok(Scheduled::Level) => 1,
        Ok(Scheduled::Revived(holder)) => 2 | (holder as i64) << 8,
        Ok(Scheduled::New(holder)) => 3 | (holder as i64) << 8,
        Err(error) => fault(error),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_commit_new(id: u64, key: i64, holder: i32) -> i64 {
    match registry(id).commit_new(key, holder as u32) {
        Ok(()) => 0,
        Err(error) => fault(error),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_abort_new(id: u64, holder: i32) {
    registry(id).abort_new(holder as u32);
}

/// The updating holder id at `key`, or -1.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_updating(id: u64, key: i64) -> i32 {
    registry(id).updating(key).map_or(-1, |holder| holder as i32)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_dropping(id: u64, key: i64) -> i32 {
    i32::from(registry(id).is_dropping(key))
}

/// `promoteChunkMap`: 0 when unmodified, else 1 + the visible pair count
/// written; the negated count needed (nothing promoted) when it does not fit.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_promote(id: u64, buffer: *mut i64, capacity: i32) -> i32 {
    let mut registry = registry(id);
    if !registry.modified {
        return 0;
    }
    let needed = registry.updating.len();
    if needed > capacity.max(0) as usize {
        return -(needed.min(i32::MAX as usize) as i32);
    }
    match registry.promote() {
        Some(entries) => export(&entries, buffer, capacity) + 1,
        None => 0,
    }
}

/// Mode 0 the updating entries, 1 the visible entries, in iteration order.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_entries(id: u64, mode: i32, buffer: *mut i64, capacity: i32) -> i32 {
    let registry = registry(id);
    let entries = if mode == 0 { registry.updating_entries() } else { registry.visible() };
    export(&entries, buffer, capacity)
}

/// `processUnloads`' drop loop: `(key, id)` of each holder moved to the pending
/// unloads, in order, for `scheduleUnload`. Nothing changes when the buffer is
/// smaller than the drop set (the negated size is returned).
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_drop_pass(id: u64, buffer: *mut i64, capacity: i32) -> i32 {
    let mut registry = registry(id);
    let bound = registry.to_drop.len();
    if bound > capacity.max(0) as usize {
        return -(bound.min(i32::MAX as usize) as i32);
    }
    match registry.drop_pass() {
        Ok(moved) => export(&moved, buffer, capacity),
        Err(error) => fault(error) as i32,
    }
}

/// `pendingUnloads.remove(key, holder)`: 1 when it was there (its id is freed).
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_finish_unload(id: u64, key: i64, holder: i32) -> i32 {
    i32::from(registry(id).finish_unload(key, holder as u32))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_mark_unsaved(id: u64, key: i64) -> i64 {
    match registry(id).mark_unsaved(key) {
        Ok(()) => 0,
        Err(error) => fault(error),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_eager_start(id: u64) {
    registry(id).eager_start();
}

/// The eager-save walk's next key; `*done` is set to 1 at the end.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_eager_next(id: u64, done: *mut i32) -> i64 {
    match registry(id).eager_next() {
        Some(key) => {
            done.write(0);
            key
        }
        None => {
            done.write(1);
            0
        }
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_eager_remove(id: u64) -> i64 {
    match registry(id).eager_remove_current() {
        Ok(()) => 0,
        Err(error) => fault(error),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_next_save(id: u64, key: i64) -> i64 {
    registry(id).next_save(key)
}

/// Mode 0 put `time`, 1 remove, 2 clear.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_set_next_save(id: u64, mode: i32, key: i64, time: i64) -> i64 {
    let mut registry = registry(id);
    match mode {
        0 => match registry.set_next_save(key, time) {
            Ok(()) => 0,
            Err(error) => fault(error),
        },
        1 => {
            registry.forget_next_save(key);
            0
        }
        _ => {
            registry.clear_next_save();
            0
        }
    }
}

/// Bit 0 pending unloads, 1 updating holders, 2 chunks to drop.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_holders_work(id: u64) -> i32 {
    registry(id).work()
}

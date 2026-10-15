//! C ABI for the Java `NativeTicketStorage` facade. A storage handle owns a
//! locked `TicketStorage`; Java's auto arena releases it after its last call
//! and fences it across every downcall. Every call takes the storage lock and
//! returns before Java runs again, so callbacks re-enter without aliasing.
//!
//! Status word: bits 0..8 code (0 ok, 3 allocation failure, 6 graph queue
//! error, 10 NullPointerException, 11 ArrayIndexOutOfBoundsException,
//! 12 IndexOutOfBoundsException, 13 NoSuchElementException,
//! 14 IllegalStateException), bit 8 the operation's boolean result, bit 9
//! dirty, bit 10 released ticket cells waiting for Java, bits 12..14 the first
//! notification Java must deliver, bits 14..16 the add's [simulation, loading]
//! notification conditions, bits 16..20 a walk event, bits 32.. a newly bound
//! cell plus one.
use std::sync::{Mutex, MutexGuard};

use super::super::ticket_ffi;
use super::{slab, Delivered, Entry, Event, Fault, TicketStorage, Walk, WalkKind};

fn code(fault: Fault) -> u64 {
    match fault {
        Fault::Allocation => 3,
        Fault::Queue => 6,
        Fault::NullPointer => 10,
        Fault::ArrayIndex => 11,
        Fault::IndexOutOfBounds => 12,
        Fault::NoSuchElement => 13,
        Fault::IllegalState => 14,
    }
}

fn pending() -> u64 {
    if slab::pending() { 1 << 10 } else { 0 }
}

fn failed(fault: Fault) -> u64 {
    code(fault) | pending()
}

fn delivered(delivered: &Delivered) -> u64 {
    (delivered.resume as u64) << 12
}

fn bound(slot: Option<u32>) -> u64 {
    slot.map_or(0, |slot| (slot as u64 + 1) << 32)
}

/// Caller must provide a live handle from create and keep its arena live.
unsafe fn storage<'a>(id: u64) -> MutexGuard<'a, TicketStorage> {
    let storage = &*std::ptr::with_exposed_provenance::<Mutex<TicketStorage>>(id as usize);
    // Nothing panics while holding it; a poisoned lock still holds whole state.
    storage.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

fn entry(type_id: i32, level: i32, flags: i32, timeout: i64, slot: i32) -> Entry {
    Entry { slot: slot.max(0) as u32, type_id: type_id as u32, level, flags: flags as u32, timeout }
}

#[no_mangle]
pub extern "C" fn mattmc_ticket_storage_create(absent_level: i32) -> u64 {
    match TicketStorage::new(absent_level) {
        Ok(storage) => Box::into_raw(Box::new(Mutex::new(storage))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Mutex<TicketStorage>>(id as usize)));
    }
}

/// Binds a listener slot (0 loading, 1 simulation): a native tracker's live
/// graph handle, a Java listener, or none.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_listen(id: u64, which: i32, java: i32, graph: u64) {
    let graph = if graph != 0 { Some(ticket_ffi::retain(graph)) } else { None };
    storage(id).set_listener(which as usize & 1, java != 0, graph);
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_add(
    id: u64,
    key: i64,
    type_id: i32,
    level: i32,
    flags: i32,
    timeout: i64,
    slot: i32,
    ticks_left: i64,
) -> u64 {
    let ticket = entry(type_id, level, flags, timeout, slot);
    let result = storage(id).add(key, ticket, slot >= 0, ticks_left);
    match result {
        Ok((added, new, wanted, progress)) => {
            u64::from(added) << 8
                | 1 << 9
                | delivered(&progress)
                | u64::from(wanted[0]) << 14
                | u64::from(wanted[1]) << 15
                | bound(new)
                | pending()
        }
        Err(fault) => failed(fault),
    }
}

/// `removeTicket`; writes the list it removed from to `list`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_remove(id: u64, key: i64, type_id: i32, level: i32, flags: i32, list: *mut i64) -> u64 {
    match storage(id).remove(key, type_id as u32, level, flags as u32) {
        Ok(Some((reference, progress))) => {
            list.write(reference as i64);
            1 << 8 | 1 << 9 | delivered(&progress) | pending()
        }
        Ok(None) => pending(),
        Err(fault) => failed(fault),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_notify(id: u64, which: i32, key: i64, level: i32, decrease: i32) -> u64 {
    match storage(id).notify(which as usize & 1, key, level, decrease != 0) {
        Ok(native) => u64::from(native) << 8,
        Err(fault) => failed(fault),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_level(id: u64, list: i64, simulate: i32) -> i32 {
    storage(id).list_level(list as u64, simulate != 0)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_level(id: u64, key: i64, simulate: i32) -> i32 {
    storage(id).level_at(key, simulate != 0)
}

/// The active list at `key`, or -1.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list(id: u64, key: i64) -> i64 {
    storage(id).tickets_at(key) as i64
}

/// The list's size, or -1 once it has left the storage.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_size(id: u64, list: i64) -> i32 {
    let storage = storage(id);
    if storage.is_live(list as u64) { storage.entries(list as u64).len() as i32 } else { -1 }
}

/// `add(index, ticket)` (mode 0) or `set(index, ticket)` (mode 1) on a live
/// list; `set` writes the replaced cell to `io[0]`. Code 14 when the list has
/// left the storage.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_put(
    id: u64,
    list: i64,
    mode: i32,
    index: i32,
    type_id: i32,
    level: i32,
    flags: i32,
    timeout: i64,
    slot: i32,
    ticks_left: i64,
    io: *mut i64,
) -> u64 {
    let ticket = entry(type_id, level, flags, timeout, slot);
    let mut storage = storage(id);
    let result = if mode == 0 {
        storage.list_insert(list as u64, index, ticket, slot >= 0, ticks_left)
    } else {
        storage.list_set(list as u64, index, ticket, slot >= 0, ticks_left).map(|(old, new)| {
            io.write(old as i64);
            new
        })
    };
    match result {
        Ok(new) => 1 << 8 | bound(new) | pending(),
        Err(fault) => failed(fault),
    }
}

/// `remove(index)` on a live list; writes the removed cell to `io[0]`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_remove(id: u64, list: i64, index: i32, io: *mut i64) -> u64 {
    match storage(id).list_remove(list as u64, index) {
        Ok(slot) => {
            io.write(slot as i64);
            1 << 8 | pending()
        }
        Err(fault) => failed(fault),
    }
}

/// The cell of a list's `index`th ticket, or -1 past its end.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_get(id: u64, list: i64, index: i32) -> i32 {
    let storage = storage(id);
    storage.entries(list as u64).get(index as usize).map_or(-1, |e| e.slot as i32)
}

/// The cell of `getLowestTicket(getTickets(key), simulate)`, or -1.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_lowest(id: u64, key: i64, simulate: i32) -> i32 {
    let storage = storage(id);
    TicketStorage::lowest(storage.entries(storage.tickets_at(key)), simulate != 0).map_or(-1, |e| e.slot as i32)
}

/// Bit 0 `hasTickets`, bit 1 `shouldKeepDimensionActive`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_state(id: u64) -> i32 {
    let storage = storage(id);
    i32::from(storage.has_tickets()) | i32::from(storage.keeps_dimension_active()) << 1
}

/// Copies an ordered export into `buffer`: 0 active keys, 1 forced chunks,
/// 2 `(key, cell)` of type `arg`, 3 persisted `(key, cell)` pairs, 4 every
/// deactivated `(key, cell)`. Returns the value count, or its negation
/// without copying when `capacity` values do not fit.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_export(id: u64, mode: i32, arg: i32, buffer: *mut i64, capacity: i32) -> i32 {
    let storage = storage(id);
    let values: Vec<i64> = match mode {
        0 => storage.active_keys(),
        1 => storage.forced_chunks(),
        2 => pairs(storage.of_type(arg as u32)),
        3 => pairs(storage.packed(false)),
        _ => pairs(storage.packed(true)),
    };
    if values.len() > capacity.max(0) as usize {
        return -(values.len().min(i32::MAX as usize) as i32);
    }
    std::ptr::copy_nonoverlapping(values.as_ptr(), buffer, values.len());
    values.len() as i32
}

fn pairs(pairs: Vec<(i64, u32)>) -> Vec<i64> {
    pairs.into_iter().flat_map(|(key, slot)| [key, slot as i64]).collect()
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_add_deactivated(
    id: u64,
    key: i64,
    type_id: i32,
    level: i32,
    flags: i32,
    timeout: i64,
    slot: i32,
    ticks_left: i64,
) -> u64 {
    let ticket = entry(type_id, level, flags, timeout, slot);
    match storage(id).add_deactivated(key, ticket, slot >= 0, ticks_left) {
        Ok(new) => 1 << 8 | bound(new) | pending(),
        Err(fault) => failed(fault),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_clear_deactivated(id: u64) -> u64 {
    storage(id).clear_deactivated();
    pending()
}

/// Starts `removeTicketIf` (0 purge, 1 deactivate, 2 Java predicate) or
/// 3 `activateAllDeactivatedTickets`' iteration.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_walk(id: u64, kind: i32) -> u64 {
    let kind = match kind {
        0 => WalkKind::Purge,
        1 => WalkKind::Deactivate,
        3 => WalkKind::Activate,
        _ => WalkKind::Java,
    };
    let walk = storage(id).begin(kind);
    Box::into_raw(Box::new(walk)).expose_provenance() as u64
}

/// Runs a walk to its next event: bits 16.. 1 done (bit 8 a forced ticket
/// was removed), 2 readiness of `io[0]`, 3 predicates for the `(key, cell)`
/// pairs written to `batch` (count in bits 32..; at most `capacity`),
/// 4 notify listener `io[2]` with `(io[0], io[1], false)` and, with bit 20,
/// then the simulation listener with level `io[3]` unless the lists changed
/// (answer 1 when it was delivered), 5 re-add cell
/// `io[1]` at `io[0]`. `answer` is as `TicketStorage::step` takes it.
/// The walk handle must be live and used by one thread.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_step(id: u64, walk: u64, answer: i32, io: *mut i64, batch: *mut i64, capacity: i32) -> u64 {
    let walk = &mut *std::ptr::with_exposed_provenance_mut::<Walk>(walk as usize);
    let mut storage = storage(id);
    let event = storage.step(walk, answer, capacity.max(1) as usize);
    let dirty = u64::from(TicketStorage::walk_dirty(walk)) << 9;
    match event {
        Ok(Event::Done { forced }) => 1 << 16 | u64::from(forced) << 8 | dirty | pending(),
        Ok(Event::Ready { key }) => {
            io.write(key);
            2 << 16 | dirty | pending()
        }
        Ok(Event::Batch { count }) => {
            for (index, (key, slot)) in walk.batched().enumerate() {
                batch.add(index * 2).write(key);
                batch.add(index * 2 + 1).write(slot as i64);
            }
            (count as u64) << 32 | 3 << 16 | dirty | pending()
        }
        Ok(Event::Activate { key, slot }) => {
            io.write(key);
            io.add(1).write(slot as i64);
            5 << 16 | dirty | pending()
        }
        Ok(Event::Notify { listener, key, level, then }) => {
            io.write(key);
            io.add(1).write(level as i64);
            io.add(2).write(listener as i64);
            io.add(3).write(then.unwrap_or(0) as i64);
            u64::from(then.is_some()) << 20 | 4 << 16 | dirty | pending()
        }
        Err(fault) => failed(fault) | dirty,
    }
}

/// Status of batched ticket `index`'s positional `entry.getValue()` now:
/// the code a Java read would throw, or 0.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_batch_value(id: u64, walk: u64, index: i32) -> u64 {
    let walk = &*std::ptr::with_exposed_provenance::<Walk>(walk as usize);
    let storage = storage(id);
    match walk.batched_index(index.max(0) as usize).map(|slot| storage.entry_value(slot)) {
        Some(Ok(())) => 0,
        Some(Err(fault)) => failed(fault),
        None => code(Fault::IllegalState),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_walk_end(walk: u64) {
    if walk != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Walk>(walk as usize)));
    }
}

/// Address of cell page `page`'s first cell (16-byte cells, `ticks_left` at
/// offset 0), or 0.
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_page(page: i32) -> u64 {
    slab::page_address(page as u32)
}

/// Writes queued cells that have no entries now, for Java to unpin; the
/// negated count needed when `capacity` does not fit. Java holds its binding
/// lock across this call and the unpinning.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cells_drain(buffer: *mut i64, capacity: i32) -> i32 {
    let out = std::slice::from_raw_parts_mut(buffer, capacity.max(0) as usize);
    slab::drain(out)
}

/// Frees an unreachable ticket's cell (its Java cleaner); 0 if it was kept.
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_free(slot: i32) -> i32 {
    i32::from(slot >= 0 && slab::free(slot as u32))
}

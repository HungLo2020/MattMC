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
use std::sync::{Arc, Mutex, MutexGuard};

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
    if slab::pending() || super::roots::pending() { 1 << 10 } else { 0 }
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
        Ok(storage) => Arc::into_raw(Arc::new(Mutex::new(storage))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_release(id: u64) {
    if id != 0 {
        drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Mutex<TicketStorage>>(id as usize)));
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

/// New unbound admission prepares actual JVM resources before publication.
/// Bit31 requests that handoff; duplicates complete without allocating a frame.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_add_adopt(
    id: u64, key: i64, type_id: i32, level: i32, flags: i32,
    timeout: i64, slot: i32, ticks_left: i64,
) -> u64 {
    if slot >= 0 {
        return mattmc_ticket_storage_add(id, key, type_id, level, flags, timeout, slot, ticks_left);
    }
    match storage(id).prepare_add(key, entry(type_id, level, flags, timeout, slot)) {
        Ok(true) => 1 << 31,
        Ok(false) => 1 << 9 | delivered(&super::Delivered { resume: 2 }) | pending(),
        Err(fault) => failed(fault),
    }
}

/// Native command selects the existing producer after actual JVM resources
/// have been prepared. List replacement's released old ticket stays pinned
/// until Java captures it and drains the original pending queue.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_commit_prepared(
    id: u64, target: i64, index: i32, command: u64, type_id: i32,
    level: i32, flags: i32, timeout: i64, slot: i32, io: *mut i64,
) -> u64 {
    let status = match (command >> 24) & 3 {
        0 => mattmc_ticket_storage_add(id, target, type_id, level, flags, timeout, slot, 0),
        1 => mattmc_ticket_storage_add_deactivated(id, target, type_id, level, flags, timeout, slot, 0),
        mode => mattmc_ticket_storage_list_put(id, target, (mode - 2) as i32, index, type_id, level, flags, timeout, slot, 0, io),
    };
    if slab::references(slot as u32) != 0 { status | bound(Some(slot as u32)) } else { status }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_deactivated_adopt(
    id: u64, key: i64, type_id: i32, level: i32, flags: i32,
    timeout: i64, slot: i32, ticks_left: i64,
) -> u64 {
    if slot >= 0 { return mattmc_ticket_storage_add_deactivated(id, key, type_id, level, flags, timeout, slot, ticks_left); }
    match storage(id).prepare_deactivated(key) {
        Ok(()) => 1 << 31 | 1 << 24,
        Err(fault) => failed(fault),
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_adopt(
    id: u64, list: i64, mode: i32, index: i32, type_id: i32,
    level: i32, flags: i32, timeout: i64, slot: i32, ticks_left: i64, io: *mut i64,
) -> u64 {
    if slot >= 0 { return mattmc_ticket_storage_list_put(id, list, mode, index, type_id, level, flags, timeout, slot, ticks_left, io); }
    match storage(id).prepare_list_put(list as u64, index, mode != 0) {
        Ok(()) => 1 << 31 | (if mode == 0 { 2 } else { 3 }) << 24,
        Err(fault) => failed(fault),
    }
}

#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_reserve(ticks_left: i64) -> i64 {
    slab::reserve(ticks_left).map_or(-1, |slot| slot as i64)
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
    let value=storage.entries(list as u64).get(index as usize).map_or(-1, |e| e.slot as i32);value
}

/// The cell of `getLowestTicket(getTickets(key), simulate)`, or -1.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_lowest(id: u64, key: i64, simulate: i32) -> i32 {
    let storage = storage(id);
    let value=TicketStorage::lowest(&storage.entries(storage.tickets_at(key)), simulate != 0).map_or(-1, |e| e.slot as i32);value
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
        4 => WalkKind::JavaEffects,
        5 => WalkKind::DeactivateEffects,
        _ => WalkKind::Java,
    };
    let walk = storage(id).begin(kind);
    Box::into_raw(Box::new(walk)).expose_provenance() as u64
}

/// Runs a walk to its next event: bits 16.. 1 done (bit 8 a forced ticket
/// was removed), 2 readiness of `io[0]`, 3 predicates for the `(key, cell, list VM source)`
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
                batch.add(index * 3).write(key);
                batch.add(index * 3 + 1).write(slot as i64);
                batch.add(index * 3 + 2).write(walk.batched_source(index) as i64);
            }
            (count as u64) << 32 | 3 << 16 | dirty | pending()
        }
        Ok(Event::Activate { key, slot }) => {
            io.write(key);
            io.add(1).write(slot as i64);
            5 << 16 | dirty | pending()
        }
        Ok(Event::RemovedQuery { which }) => {
            io.write(which as i64);
            6 << 16 | pending()
        }
        Ok(Event::LiveNotify { listener, key }) => {
            io.write(key);
            io.add(2).write(listener as i64);
            7 << 16 | pending()
        }
        Ok(Event::Dirty) => 8 << 16 | pending(),
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
    match walk.batched_index(index.max(0) as usize).map(|slot| storage.entry_value_size(slot)) {
        Some(Ok(size)) => (size as u32 as u64) << 32,
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

/// Address of cell page `page`'s first cell (48-byte cells, `ticks_left` at
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

/// Retain the actual allocation, never a second storage or a borrowed raw owner.
pub(crate) unsafe fn retain_storage(id: u64) -> Arc<Mutex<TicketStorage>> {
    let pointer = std::ptr::with_exposed_provenance::<Mutex<TicketStorage>>(id as usize);
    Arc::increment_strong_count(pointer); Arc::from_raw(pointer)
}

#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_resource_reserve(initial: i64) -> i64 {
    slab::reserve_resource(initial).map_or(-1, |slot| slot as i64)
}
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_resource_adopt(slot: i32) -> i64 {
    if slot < 0 { return -1; }
    slab::adopt_resource(slot as u32).map_or(-1, |slot| slot as i64)
}
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_resource_release(slot: i32) {
    if slot >= 0 { slab::release_resource(slot as u32); }
}
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_address(slot: i32) -> u64 { slab::resource_address(slot as u32) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cells_set(address: u64, value: i64) { slab::resource(address).set(value); }
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cells_decrease(address: u64) { slab::resource(address).decrease(); }
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cells_bind_resource(address: u64, slot: i32) { slab::resource(address).bind_resource(slot); }
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cells_timed_out(address: u64) -> i32 {
    i32::from(slab::resource(address).ticks_left.load(std::sync::atomic::Ordering::Relaxed) < 0)
}
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_timed_out_value(value: i64) -> i32 { i32::from(value < 0) }

// A synchronous immutable command carries only this invocation's control.
// Actual iterators and selected Ticket objects remain strongly held JVM leaves.
const QUERY_SIMULATION: u64 = 1 << 8;
const QUERY_HAS_BEST: u64 = 1 << 9;
#[no_mangle]
pub extern "C" fn mattmc_ticket_query_begin(simulation: i32, present: i32) -> u64 {
    (if simulation != 0 { QUERY_SIMULATION } else { 0 }) | if present != 0 { 1 } else { 10 }
}
#[no_mangle]
pub extern "C" fn mattmc_ticket_query_next(command: u64, answer: i64) -> u64 {
    let flags = command & (QUERY_SIMULATION | QUERY_HAS_BEST);
    let predicate = if flags & QUERY_SIMULATION != 0 { 6 } else { 7 };
    let (next, extra) = match command & 255 {
        1 => (2, 0), // actual iterator has been captured
        2 => (if answer != 0 { 3 } else { 10 }, 0),
        3 => (if flags & QUERY_HAS_BEST != 0 { 4 } else { predicate }, 0),
        4 => (5, (answer as u32 as u64) << 32),
        5 => (if ((command >> 32) as u32 as i32) < answer as i32 { predicate } else { 2 }, 0),
        6 | 7 => (if answer != 0 { 8 } else { 2 }, 0),
        8 => return flags | QUERY_HAS_BEST | 2,
        _ => return 0,
    };
    flags | next | extra
}

/// Return the original captured list before actual Ticket getter effects.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_get_or_create(id: u64, key: i64, list: *mut i64) -> u64 {
    match storage(id).get_or_create(key) {
        Ok(value) => { list.write(value as i64); pending() },
        Err(fault) => failed(fault),
    }
}

/// The original map removal is distinct from the captured list iterator.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_unmap(id: u64, key: i64) -> u64 {
    let mut storage = storage(id);
    match storage.tickets.remove(key) {
        Ok(index) => { storage.retire(index); pending() },
        Err(fault) => failed(fault),
    }
}

/// `entry.getValue()` evaluated only after Java captured the live listener.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_walk_list(id: u64, walk: u64, out: *mut i64) -> u64 {
    let walk = &*std::ptr::with_exposed_provenance::<Walk>(walk as usize);
    let storage = storage(id);
    match storage.tickets.value_at(walk.index) {
        Ok(value) => { out.write(storage.reference(value) as i64); 0 },
        Err(fault) => failed(fault),
    }
}

/// A nullable ObjectArrayList slot has no Ticket root or native counter owner.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_put_null(id: u64, list: i64, mode: i32, index: i32, out: *mut i64) -> u64 {
    let mut storage = storage(id);
    let ticket = Entry { slot: u32::MAX, type_id: u32::MAX, level: 0, flags: 0, timeout: 0 };
    let result = if mode == 0 {
        storage.list_insert(list as u64, index, ticket, true, 0).map(|_| ())
    } else {
        storage.list_set(list as u64, index, ticket, true, 0).map(|(old, _)| { out.write(old as i32 as i64); })
    };
    match result { Ok(()) => 1 << 8 | pending(), Err(fault) => failed(fault) }
}

/// Configure the source's JVM root scope before any entry can be published.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_roots_enable(id: u64) -> u64 {
    let mut storage = storage(id);
    if storage.arena.lists.iter().any(|list| list.owner.as_ref().is_some_and(|owner| !owner.lock().entries.is_empty())) { return 0; }
    let Some(source)=super::roots::allocate_source() else{return 0;};
    if storage.roots.enable(source).is_err() { return 0; }
    source
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_root_count(id: u64, cell: i32) -> i32 {
    let store=storage(id);store.arena.lists.iter().filter_map(|list|list.owner.as_ref()).map(|list|list.lock().roots.count(cell as u32)).sum::<u32>() as i32
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_source_roots_drain(out: *mut i64, capacity: i32) -> i32 {
    let out = std::slice::from_raw_parts_mut(out, capacity.max(0) as usize * 2);
    super::roots::drain(out) as i32
}

/// Foreign Ticket cleanup releases a resource lease, independently of entries.
#[no_mangle]
pub extern "C" fn mattmc_ticket_cells_source_resource_release(slot: i32) -> i32 {
    super::slab::release_resource(slot as u32);
    1
}

/// Complete actual list cursor/admission binding. The caller retains its genuine
/// VM source and confined state allocation; no native guard crosses an effect.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_begin(id: u64, value: u64, mode: i32, state: *mut super::admission::LiveCursor) -> u64 {
    if !(0..=3).contains(&mode) { return failed(Fault::IllegalState); }
    match super::admission::LiveCursor::begin(&mut storage(id), value, mode) {
        Ok(cursor) => { state.write(cursor); pending() },
        Err(fault) => failed(fault),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_step(id: u64, state: *mut super::admission::LiveCursor, answer: i64) {
    (*state).step(&storage(id), answer);
}

/// Native cursor allocation belongs to its genuine source. Reserve before any
/// fallible VM binding; open only after adoption. Slots never outlive the source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_reserve(id: u64) -> u64 {
    storage(id).reserve_cursor().unwrap_or(0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_open(id: u64, address: u64, generation: u64, value: u64, mode: i32) -> u64 {
    if !(0..=3).contains(&mode) { return failed(Fault::IllegalState); }
    match storage(id).open_cursor(address, generation, value, mode) {
        Ok(()) => pending(), Err(fault) => failed(fault),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_resume(id: u64, address: u64, generation: u64, answer: i64) -> u64 {
    match storage(id).step_cursor(address, generation, answer) {
        Ok(()) => pending(), Err(fault) => failed(fault),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_close(id: u64, address: u64, generation: u64) -> u64 {
    match storage(id).release_cursor(address, generation) {
        Ok(()) => pending(), Err(fault) => failed(fault),
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_cursor_abort_binding(id: u64, address: u64) -> u64 {
    match storage(id).release_unadopted_cursor(address) {
        Ok(()) => pending(), Err(fault) => failed(fault),
    }
}

/// Public list views retain their physical list, not its former routing map.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_retain(id:u64,list:u64)->u64{
    storage(id).list_owner(list).map_or(0,|owner|owner.clone().into_raw())
}
unsafe fn list_resource<'a>(id:u64)->&'a super::SharedList{&*std::ptr::with_exposed_provenance::<super::SharedList>(id as usize)}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_release(id:u64){if id!=0{drop(super::ListOwner::from_raw(id));}}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_size(id:u64)->i32{list_resource(id).data.lock().unwrap_or_else(|p|p.into_inner()).entries.len() as i32}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_get(id:u64,index:i32)->i32{list_resource(id).data.lock().unwrap_or_else(|p|p.into_inner()).entries.get(index as usize).map_or(-1,|e|e.slot as i32)}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_root_count(id:u64,cell:i32)->i32{list_resource(id).data.lock().unwrap_or_else(|p|p.into_inner()).roots.count(cell as u32) as i32}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_prepare(id:u64,index:i32,replace:i32)->u64{
    match list_resource(id).data.lock().unwrap_or_else(|p|p.into_inner()).prepare(index,replace!=0){Ok(())=>pending(),Err(fault)=>failed(fault)}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_publish(id:u64,index:i32,replace:i32,type_id:i32,level:i32,flags:i32,timeout:i64,cell:i32,previous:*mut i64)->u64{
    let value=if type_id == -1{Entry{slot:u32::MAX,type_id:u32::MAX,level:0,flags:0,timeout:0}}else{entry(type_id,level,flags,timeout,cell)};
    match list_resource(id).data.lock().unwrap_or_else(|p|p.into_inner()).publish(index,replace!=0,value){
        Ok(old)=>{previous.write(old.map_or(-1,|v|v as i64));pending()},Err(fault)=>failed(fault)}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_list_resource_remove(id:u64,index:i32,previous:*mut i64)->u64{
    match list_resource(id).data.lock().unwrap_or_else(|p|p.into_inner()).remove(index){Ok(old)=>{previous.write(old as i64);pending()},Err(fault)=>failed(fault)}
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_list_source(id:u64,list:u64)->u64{storage(id).list_owner(list).map_or(0,|owner|owner.source())}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_storage_root_list(id:u64,key:i64,deactivated:i32,out:*mut i64)->u64{
    let mut store=storage(id);
    let result=if deactivated==0{store.get_or_create(key)}else{store.prepare_deactivated(key).map(|()|store.reference(store.deactivated.get(key)))};
    match result{Ok(list)=>{out.write(list as i64);pending()},Err(fault)=>failed(fault)}
}

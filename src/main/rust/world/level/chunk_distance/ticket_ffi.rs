//! C ABI for the Java `TicketChunkDistance` adapter (simulation and loading
//! trackers). A handle is one reference to an `Arc<SharedTicketDistance>`;
//! Java's auto arena releases it after its last call, and a ticket storage
//! notifying the graph directly holds its own. Every call locks the graph.
//! Status word: bits 0..8 code (0 ok, 3 allocation failure, 5 invalid kind,
//! 6 internal queue error), bit 8 set while work is queued; a run also returns
//! its remaining budget in bits 32..
use super::graph::Error;
use super::graph::DistanceField;
use super::ticket::{SharedTicketDistance, TicketDistance};
use std::sync::Arc;

fn status(distance: &TicketDistance, result: Result<(), Error>) -> u64 {
    let code = match result {
        Ok(()) => 0,
        Err(Error::Allocation) => 3,
        Err(Error::Queue) => 6,
    };
    code | if distance.field().has_work() { 1 << 8 } else { 0 }
}

#[no_mangle]
pub extern "C" fn mattmc_ticket_distance_create(
    kind: i32,
    absent_ticket_level: i32,
    chunk_max_level: i32,
    max_coordinate: i32,
) -> u64 {
    // Kind 0 simulation, 1 loading; the facade passes ChunkLevel/ChunkPos constants.
    let field = match kind {
        0 => DistanceField::simulation(),
        1 if (0..=250).contains(&chunk_max_level) => DistanceField::loading(chunk_max_level, max_coordinate),
        _ => return 0,
    };
    match field.and_then(|field| TicketDistance::new(absent_ticket_level, field)) {
        // The graph's own tables were allocated fallibly; the small shared
        // header uses the global allocator, once per tracker.
        Ok(distance) => Arc::into_raw(Arc::new(SharedTicketDistance::new(distance))).expose_provenance() as u64,
        Err(_) => 0,
    }
}

/// Caller must provide a live handle from create and keep its arena live; the
/// returned reference outlives no call.
pub(crate) unsafe fn shared<'a>(id: u64) -> &'a SharedTicketDistance {
    &*std::ptr::with_exposed_provenance::<SharedTicketDistance>(id as usize)
}

/// A new owning reference for a ticket storage, under the same caller rules.
pub(crate) unsafe fn retain(id: u64) -> Arc<SharedTicketDistance> {
    let pointer = std::ptr::with_exposed_provenance::<SharedTicketDistance>(id as usize);
    Arc::increment_strong_count(pointer);
    Arc::from_raw(pointer)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_release(id: u64) {
    if id != 0 {
        drop(Arc::from_raw(std::ptr::with_exposed_provenance::<SharedTicketDistance>(id as usize)));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_seed(id: u64, position: i64, ticket_level: i32) -> u64 {
    let mut distance = shared(id).lock();
    let result = distance.seed(position, ticket_level);
    status(&distance, result)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_update(
    id: u64,
    position: i64,
    ticket_level: i32,
    level: i32,
    decrease: i32,
) -> u64 {
    let mut distance = shared(id).lock();
    let result = distance.update(position, ticket_level, level, decrease != 0);
    status(&distance, result)
}

/// Runs with `budget`, replacing any undrained change record; bits 32.. hold
/// the remaining budget, as `runUpdates` returns it.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_run(id: u64, budget: i32) -> u64 {
    let mut distance = shared(id).lock();
    distance.field_mut().clear_changes();
    let (result, remaining) = match distance.run_updates(budget) {
        Ok(remaining) => (Ok(()), remaining),
        Err(error) => (Err(error), budget),
    };
    status(&distance, result) | ((remaining as u32 as u64) << 32)
}

/// Copies recorded (position, level) pairs when `capacity` pairs fit, then
/// clears them and returns the pair count. A smaller buffer copies nothing
/// and returns the negated count needed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_drain(id: u64, buffer: *mut i64, capacity: i32) -> i32 {
    let mut distance = shared(id).lock();
    let field = distance.field_mut();
    let needed = field.changes().len();
    if needed > capacity.max(0) as usize {
        return -(needed.min(i32::MAX as usize) as i32);
    }
    super::ffi::drain_changes(field, buffer, capacity)
}

/// Prepare an owning operation before any graph mutation. The VM adopts this
/// handle, then explicitly steps it; registration failure can release unchanged.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_operation_create(
    id: u64, run: i32, position: i64, level: i32, decrease: i32, budget: i32,
) -> u64 {
    let Ok(operation) = super::graph::TicketOperation::new(retain(id), run != 0, position, level, decrease != 0, budget) else { return 0; };
    let layout = std::alloc::Layout::new::<super::graph::TicketOperation>();
    let pointer = std::alloc::alloc(layout).cast::<super::graph::TicketOperation>();
    if pointer.is_null() { return 0; }
    pointer.write(operation);
    pointer.expose_provenance() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_operation_step(id: u64, answer: i32) {
    (&mut *std::ptr::with_exposed_provenance_mut::<super::graph::TicketOperation>(id as usize)).step(answer);
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_operation_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<super::graph::TicketOperation>(id as usize))); }
}

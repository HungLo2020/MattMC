//! C ABI for the Java `TicketChunkDistance` adapter (simulation and loading
//! trackers), with the same handle, lifetime and locking rules as `ffi.rs`.
//! Status word: bits 0..8 code (0 ok, 3 allocation failure, 5 invalid kind,
//! 6 internal queue error), bit 8 set while work is queued; a run also returns
//! its remaining budget in bits 32..
use super::graph::Error;
use super::graph::DistanceField;
use super::ticket::TicketDistance;

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
        Ok(distance) => unsafe {
            // Fallible allocation keeps exhaustion a Java OOME.
            let pointer = std::alloc::alloc(std::alloc::Layout::new::<TicketDistance>())
                .cast::<TicketDistance>();
            if !pointer.is_null() {
                pointer.write(distance);
            }
            pointer.expose_provenance() as u64
        },
        Err(_) => 0,
    }
}

/// Caller must provide a live numeric handle from create, exclude every
/// concurrent access (including release), and keep its arena live.
unsafe fn handle<'a>(id: u64) -> &'a mut TicketDistance {
    &mut *std::ptr::with_exposed_provenance_mut::<TicketDistance>(id as usize)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
            TicketDistance,
        >(id as usize)));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_seed(id: u64, position: i64, ticket_level: i32) -> u64 {
    let distance = handle(id);
    let result = distance.seed(position, ticket_level);
    status(distance, result)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_update(
    id: u64,
    position: i64,
    ticket_level: i32,
    level: i32,
    decrease: i32,
) -> u64 {
    let distance = handle(id);
    let result = distance.update(position, ticket_level, level, decrease != 0);
    status(distance, result)
}

/// Runs with `budget`, replacing any undrained change record; bits 32.. hold
/// the remaining budget, as `runUpdates` returns it.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_run(id: u64, budget: i32) -> u64 {
    let distance = handle(id);
    distance.field_mut().clear_changes();
    let (result, remaining) = match distance.run_updates(budget) {
        Ok(remaining) => (Ok(()), remaining),
        Err(error) => (Err(error), budget),
    };
    status(distance, result) | ((remaining as u32 as u64) << 32)
}

/// Copies recorded (position, level) pairs when `capacity` pairs fit, then
/// clears them and returns the pair count. A smaller buffer copies nothing
/// and returns the negated count needed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_distance_drain(id: u64, buffer: *mut i64, capacity: i32) -> i32 {
    let field = handle(id).field_mut();
    let needed = field.changes().len();
    if needed > capacity.max(0) as usize {
        return -(needed.min(i32::MAX as usize) as i32);
    }
    super::ffi::drain_changes(field, buffer, capacity)
}

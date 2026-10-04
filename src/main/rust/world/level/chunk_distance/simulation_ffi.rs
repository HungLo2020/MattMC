//! C ABI for the Java `SimulationChunkTracker` adapter, with the same handle,
//! lifetime and locking rules as `ffi.rs`. Status word: bits 0..8 code (0 ok,
//! 3 allocation failure, 6 internal queue error), bit 8 set while work is
//! queued, bits 32.. the change count recorded by the last run.
use super::graph::Error;
use super::simulation::SimulationDistance;

fn status(distance: &SimulationDistance, result: Result<(), Error>) -> u64 {
    let code = match result {
        Ok(()) => 0,
        Err(Error::Allocation) => 3,
        Err(Error::Queue) => 6,
    };
    code | if distance.field().has_work() { 1 << 8 } else { 0 }
}

#[no_mangle]
pub extern "C" fn mattmc_simulation_distance_create(absent_ticket_level: i32) -> u64 {
    match SimulationDistance::new(absent_ticket_level) {
        Ok(distance) => unsafe {
            // Fallible allocation keeps exhaustion a Java OOME.
            let pointer = std::alloc::alloc(std::alloc::Layout::new::<SimulationDistance>())
                .cast::<SimulationDistance>();
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
unsafe fn handle<'a>(id: u64) -> &'a mut SimulationDistance {
    &mut *std::ptr::with_exposed_provenance_mut::<SimulationDistance>(id as usize)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_simulation_distance_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
            SimulationDistance,
        >(id as usize)));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_simulation_distance_seed(id: u64, position: i64, ticket_level: i32) -> u64 {
    let distance = handle(id);
    let result = distance.seed(position, ticket_level);
    status(distance, result)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_simulation_distance_update(
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

/// Runs with `budget`, replacing any undrained change record.
#[no_mangle]
pub unsafe extern "C" fn mattmc_simulation_distance_run(id: u64, budget: i32) -> u64 {
    let distance = handle(id);
    distance.field_mut().clear_changes();
    let result = distance.run_updates(budget).map(|_| ());
    let count = distance.field().changes().len() as u64;
    status(distance, result) | (count << 32)
}

/// Copies recorded (position, level) pairs when `capacity` pairs fit, then
/// clears them; returns the pair count, or -1 without copying.
#[no_mangle]
pub unsafe extern "C" fn mattmc_simulation_distance_drain(id: u64, buffer: *mut i64, capacity: i32) -> i32 {
    super::ffi::drain_changes(handle(id).field_mut(), buffer, capacity)
}

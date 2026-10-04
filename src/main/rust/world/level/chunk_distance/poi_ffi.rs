//! C ABI for the Java `PoiManager.DistanceTracker` adapter, with the same
//! handle, lifetime and locking rules as `ffi.rs` and the status layout of
//! `ticket_ffi.rs` (code bits 0..8, queued-work bit 8).
use super::graph::Error;
use super::poi::PoiDistance;

fn status(distance: &PoiDistance, result: Result<(), Error>) -> u64 {
    let code = match result {
        Ok(()) => 0,
        Err(Error::Allocation) => 3,
        Err(Error::Queue) => 6,
    };
    code | if distance.field().has_work() { 1 << 8 } else { 0 }
}

#[no_mangle]
pub extern "C" fn mattmc_poi_distance_create() -> u64 {
    match PoiDistance::new() {
        Ok(distance) => unsafe {
            // Fallible allocation keeps exhaustion a Java OOME.
            let pointer =
                std::alloc::alloc(std::alloc::Layout::new::<PoiDistance>()).cast::<PoiDistance>();
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
unsafe fn handle<'a>(id: u64) -> &'a mut PoiDistance {
    &mut *std::ptr::with_exposed_provenance_mut::<PoiDistance>(id as usize)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_poi_distance_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<PoiDistance>(id as usize)));
    }
}

/// Operation 0 records `centre` without graph work, 1 is a section change,
/// 2 clears every recorded centre (`position`/`centre` unused).
#[no_mangle]
pub unsafe extern "C" fn mattmc_poi_distance_apply(id: u64, operation: i32, position: i64, centre: i32) -> u64 {
    let distance = handle(id);
    let result = match operation {
        0 => distance.seed(position, centre != 0),
        1 => distance.section_changed(position, centre != 0),
        2 => distance.clear_centres(),
        _ => return 5,
    };
    status(distance, result)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_poi_distance_run(id: u64, budget: i32) -> u64 {
    let distance = handle(id);
    distance.field_mut().clear_changes();
    let (result, remaining) = match distance.run_updates(budget) {
        Ok(remaining) => (Ok(()), remaining),
        Err(error) => (Err(error), budget),
    };
    status(distance, result) | ((remaining as u32 as u64) << 32)
}

/// As `mattmc_ticket_distance_drain`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_poi_distance_drain(id: u64, buffer: *mut i64, capacity: i32) -> i32 {
    let field = handle(id).field_mut();
    let needed = field.changes().len();
    if needed > capacity.max(0) as usize {
        return -(needed.min(i32::MAX as usize) as i32);
    }
    super::ffi::drain_changes(field, buffer, capacity)
}

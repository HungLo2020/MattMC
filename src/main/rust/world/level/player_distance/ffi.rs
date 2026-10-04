//! C ABI for `PlayerChunkDistances`. Opaque numeric handles are private to the
//! Java facade: its auto arena owns one handle and releases it only after the
//! last call is unreachable, and Java fences that owner across every downcall.
//! The facade serializes calls with a lock; Rust callers must likewise give each
//! live handle exclusive access. Only `drain` is bound as a critical call: it
//! copies into a caller buffer, never allocates, blocks or calls Java.
//!
//! Status word: bits 0..8 code (0 ok, 3 allocation failure, 5 invalid field,
//! 6 internal queue error), bit 8+field set while that field has queued work,
//! bits 32.. the recorded change count of the field just run.
use super::graph::Error;
use super::PlayerDistances;

const FIELDS: i32 = 2;

fn status(distances: &PlayerDistances, result: Result<(), Error>) -> u64 {
    let code = match result {
        Ok(()) => 0,
        Err(Error::Allocation) => 3,
        Err(Error::Queue) => 6,
    };
    let mut work = 0;
    for field in 0..FIELDS as usize {
        if distances.field(field).has_work() {
            work |= 1 << (8 + field);
        }
    }
    code | work
}

#[no_mangle]
pub extern "C" fn mattmc_player_distance_create(natural_spawn: i32, player_ticket: i32) -> u64 {
    // The facade rejects level counts outside the original queue/graph domain.
    let valid = |distance: i32| (-2..=251).contains(&distance);
    if !valid(natural_spawn) || !valid(player_ticket) {
        return 0;
    }
    match PlayerDistances::new(natural_spawn, player_ticket) {
        Ok(distances) => {
            // Fallible allocation keeps exhaustion a Java OOME instead of the
            // process-aborting allocation error handler.
            unsafe {
                let pointer = std::alloc::alloc(std::alloc::Layout::new::<PlayerDistances>())
                    .cast::<PlayerDistances>();
                if !pointer.is_null() {
                    pointer.write(distances);
                }
                pointer.expose_provenance() as u64
            }
        }
        Err(_) => 0,
    }
}

/// Caller must provide a live numeric handle from create, exclude every
/// concurrent access (including release), and keep its arena live.
unsafe fn handle<'a>(id: u64) -> &'a mut PlayerDistances {
    &mut *std::ptr::with_exposed_provenance_mut::<PlayerDistances>(id as usize)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_distance_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
            PlayerDistances,
        >(id as usize)));
    }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_distance_entered(id: u64, position: i64) -> u64 {
    let distances = handle(id);
    let result = distances.player_entered(position);
    status(distances, result)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_player_distance_vacated(id: u64, position: i64) -> u64 {
    let distances = handle(id);
    let result = distances.chunk_vacated(position);
    status(distances, result)
}

/// Runs one field with `budget` and records its ordered `setLevel` calls,
/// replacing any undrained record of that field.
#[no_mangle]
pub unsafe extern "C" fn mattmc_player_distance_run(id: u64, field: i32, budget: i32) -> u64 {
    if !(0..FIELDS).contains(&field) {
        return 5;
    }
    let distances = handle(id);
    distances.field_mut(field as usize).clear_changes();
    let result = distances.run_updates(field as usize, budget).map(|_| ());
    let count = distances.field(field as usize).changes().len() as u64;
    status(distances, result) | (count << 32)
}

/// Copies the field's recorded changes as (position, level) long pairs into
/// `buffer` when `capacity` pairs fit, then clears them. Returns the copied
/// pair count, or -1 without copying when the buffer is too small.
#[no_mangle]
pub unsafe extern "C" fn mattmc_player_distance_drain(
    id: u64,
    field: i32,
    buffer: *mut i64,
    capacity: i32,
) -> i32 {
    if !(0..FIELDS).contains(&field) {
        return -1;
    }
    let distances = handle(id);
    let field = distances.field_mut(field as usize);
    let changes = field.changes();
    if changes.len() > capacity.max(0) as usize {
        return -1;
    }
    for (index, (position, level)) in changes.iter().enumerate() {
        buffer.add(index * 2).write(*position);
        buffer.add(index * 2 + 1).write(*level as i64);
    }
    let count = changes.len() as i32;
    field.clear_changes();
    count
}

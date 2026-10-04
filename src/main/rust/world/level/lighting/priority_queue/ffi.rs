//! Opaque numeric handles are private to the Java facade. The auto arena owns one handle;
//! release runs only after its last call is unreachable. No pointer is retained
//! for pop output. The Java adapter serializes calls with a reentrant access lease; Rust
//! callers must likewise provide exclusive access to each live handle. IDs carry
//! exposed addresses, not ownership: Java keeps the lifetime arena reachable with
//! fences across each scalar downcall. Never retain/expose IDs without that owner.
//! apply_fast bounds scans/probes and never allocates or calls Java. Status 4
//! requests ordinary retry before any mutation; ordinary mutations may resize.
use super::queue::{Error, Queue};
const RETRY: u64 = 4;

struct NativeHandle {
    queue: Queue,
    error_index: i32,
}

fn status(handle: &mut NativeHandle, result: Result<(), Error>) -> u64 {
    let code = match result {
        Ok(()) => 0,
        Err(Error::Index(index)) => {
            handle.error_index = index;
            1
        }
        Err(Error::Empty) => 2,
        Err(Error::Allocation) => 3,
    };
    ((handle.queue.first as u32 as u64) << 32) | code
}

#[no_mangle]
pub extern "C" fn mattmc_light_queue_create(levels: i32, expected: i32) -> u64 {
    match Queue::new(levels, expected) {
        Ok(queue) => {
            // Fallible allocation keeps allocator exhaustion a Java OOME instead
            // of invoking Rust's process-aborting allocation error handler.
            unsafe {
                let pointer = std::alloc::alloc(std::alloc::Layout::new::<NativeHandle>())
                    .cast::<NativeHandle>();
                if !pointer.is_null() {
                    pointer.write(NativeHandle {
                        queue,
                        error_index: 0,
                    });
                }
                pointer.expose_provenance() as u64
            }
        }
        Err(_) => 0,
    }
}

/// Caller must provide a live numeric handle from create, exclude every concurrent
/// access (including release), and keep its arena live through the whole call.
unsafe fn handle<'a>(id: u64) -> &'a mut NativeHandle {
    &mut *std::ptr::with_exposed_provenance_mut::<NativeHandle>(id as usize)
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_release(id: u64) {
    if id != 0 {
        drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
            NativeHandle,
        >(id as usize)));
    }
}

/// Operation protocol: 0 enqueue, 1 dequeue, 2 FIFO pop, 3 reschedule,
/// 4 cancel computed, 5 enqueue computed. Details low/high halves preserve two
/// signed i32 values; reschedule uses previous/next, others use only the low half.
/// Output is a writable
/// aligned u64 disjoint from the handle for pop.
/// It is unused for other operations; their index errors use owned diagnostics.
/// Successful pops preserve every bit; index errors write the failing index.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_apply(
    id: u64,
    operation: i32,
    value: u64,
    level: i32,
    details: u64,
    output: *mut u64,
) -> u64 {
    if !(0..=5).contains(&operation) {
        return 5;
    }
    let previous = details as u32 as i32;
    let next = (details >> 32) as u32 as i32;
    let owner = handle(id);
    let queue = &mut owner.queue;
    let result = match operation {
        0 => queue.enqueue(value, level),
        1 => queue.dequeue(value, level, previous),
        3 => queue.reschedule(value, level, previous, next),
        4 => queue.cancel_computed(value, level, previous),
        5 => queue.enqueue_computed(value, level, previous),
        _ => {
            let result = queue.pop();
            return pop_result(owner, output, result);
        }
    };
    status(owner, result)
}

#[cfg(test)]
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_apply_fast(
    id: u64,
    operation: i32,
    value: u64,
    level: i32,
    details: u64,
    output: *mut u64,
) -> u64 {
    let previous = details as u32 as i32;
    let next = (details >> 32) as u32 as i32;
    match operation {
        0 => mattmc_light_queue_enqueue_fast(id, value, level),
        1 => mattmc_light_queue_dequeue_fast(id, value, level, previous),
        2 => mattmc_light_queue_pop_fast(id, output),
        3 if (level | previous | next) & !255 == 0 => mattmc_light_queue_reschedule_bytes_fast(
            id,
            value,
            level as u32 | ((previous as u32) << 8) | ((next as u32) << 16),
        ),
        3 => mattmc_light_queue_reschedule_fast(id, value, level, previous, next),
        4 if (level | previous) & !255 == 0 => mattmc_light_queue_cancel_computed_bytes_fast(
            id,
            value,
            level as u32 | ((previous as u32) << 8),
        ),
        4 => mattmc_light_queue_cancel_computed_fast(id, value, level, previous),
        5 if (level | previous) & !255 == 0 => mattmc_light_queue_enqueue_computed_bytes_fast(
            id,
            value,
            level as u32 | ((previous as u32) << 8),
        ),
        5 => mattmc_light_queue_enqueue_computed_fast(id, value, level, previous),
        _ => 5,
    }
}

unsafe fn pop_result(
    owner: &mut NativeHandle,
    output: *mut u64,
    result: Result<u64, Error>,
) -> u64 {
    let result = match result {
        Ok(value) => {
            output.write(value);
            Ok(())
        }
        Err(Error::Index(index)) => {
            output.write(index as i64 as u64);
            Err(Error::Index(index))
        }
        Err(error) => Err(error),
    };
    status(owner, result)
}

// Test-only ABI adapters; every check goes through the production operation protocol.
#[cfg(test)]
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_enqueue(id: u64, value: u64, level: i32) -> u64 {
    mattmc_light_queue_apply(id, 0, value, level, 0, std::ptr::null_mut())
}
#[cfg(test)]
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_dequeue(
    id: u64,
    value: u64,
    level: i32,
    bound: i32,
) -> u64 {
    mattmc_light_queue_apply(
        id,
        1,
        value,
        level,
        bound as u32 as u64,
        std::ptr::null_mut(),
    )
}
#[cfg(test)]
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_pop(id: u64, output: *mut u64) -> u64 {
    mattmc_light_queue_apply(id, 2, 0, 0, 0, output)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_enqueue_fast(id: u64, value: u64, level: i32) -> u64 {
    let owner = handle(id);
    let queue = &mut owner.queue;
    if !queue.bounded() {
        return RETRY;
    }
    match queue.enqueue_without_growth(value, level) {
        Some(result) => status(owner, result),
        None => RETRY,
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_dequeue_fast(
    id: u64,
    value: u64,
    level: i32,
    bound: i32,
) -> u64 {
    let owner = handle(id);
    let queue = &mut owner.queue;
    if !queue.bounded() {
        return RETRY;
    }
    match queue.dequeue_bounded(value, level, bound) {
        Some(result) => status(owner, result),
        None => RETRY,
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_pop_fast(id: u64, output: *mut u64) -> u64 {
    let owner = handle(id);
    let queue = &mut owner.queue;
    if !queue.bounded() {
        return RETRY;
    }
    match queue.pop_bounded() {
        Some(result) => pop_result(owner, output, result),
        None => RETRY,
    }
}

#[inline]
unsafe fn mattmc_light_queue_reschedule_fast(
    id: u64,
    value: u64,
    current: i32,
    previous: i32,
    next: i32,
) -> u64 {
    let owner = handle(id);
    let queue = &mut owner.queue;
    if !queue.bounded() {
        return RETRY;
    }
    match queue.reschedule_bounded(value, current, previous, next) {
        Some(result) => status(owner, result),
        None => RETRY,
    }
}

#[inline]
unsafe fn mattmc_light_queue_cancel_computed_fast(
    id: u64,
    value: u64,
    current: i32,
    computed: i32,
) -> u64 {
    let owner = handle(id);
    let queue = &mut owner.queue;
    if !queue.bounded() {
        return RETRY;
    }
    match queue.cancel_computed_bounded(value, current, computed) {
        Some(result) => status(owner, result),
        None => RETRY,
    }
}

#[inline]
unsafe fn mattmc_light_queue_enqueue_computed_fast(
    id: u64,
    value: u64,
    current: i32,
    computed: i32,
) -> u64 {
    let owner = handle(id);
    let queue = &mut owner.queue;
    if !queue.bounded() {
        return RETRY;
    }
    match queue.enqueue_computed_bounded(value, current, computed) {
        Some(result) => status(owner, result),
        None => RETRY,
    }
}

/// Read diagnostic metadata while the caller still owns the failed operation.
/// One field read; no allocation, blocking or callback.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_error_index(id: u64) -> i32 {
    handle(id).error_index
}

// Graph levels and its absent-computed sentinel are unsigned bytes. The Java
// adapter uses the ordinary full-i32 protocol for values outside that domain.
// These typed entry points reduce register arguments, without changing policy.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_reschedule_bytes_fast(
    id: u64,
    value: u64,
    levels: u32,
) -> u64 {
    mattmc_light_queue_reschedule_fast(
        id,
        value,
        (levels & 255) as i32,
        ((levels >> 8) & 255) as i32,
        ((levels >> 16) & 255) as i32,
    )
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_cancel_computed_bytes_fast(
    id: u64,
    value: u64,
    levels: u32,
) -> u64 {
    mattmc_light_queue_cancel_computed_fast(
        id,
        value,
        (levels & 255) as i32,
        ((levels >> 8) & 255) as i32,
    )
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_queue_enqueue_computed_bytes_fast(
    id: u64,
    value: u64,
    levels: u32,
) -> u64 {
    mattmc_light_queue_enqueue_computed_fast(
        id,
        value,
        (levels & 255) as i32,
        ((levels >> 8) & 255) as i32,
    )
}

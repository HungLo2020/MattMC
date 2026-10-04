// Compile the exact production module, without graphics/audio test dependencies.
#[path = "../../main/rust/world/level/lighting/priority_queue/mod.rs"]
mod priority_queue;

// Allocation tracking belongs only to this standalone harness, not to the game
// crate or its unrelated tests. Track the calling thread to avoid test interference.
use std::alloc::{GlobalAlloc, Layout, System};
use std::cell::Cell;
thread_local! {
    static TRACK: Cell<bool> = const { Cell::new(false) };
    static ALLOCS: Cell<usize> = const { Cell::new(0) };
    static FREES: Cell<usize> = const { Cell::new(0) };
}
struct TrackedAllocator;
#[global_allocator]
static ALLOCATOR: TrackedAllocator = TrackedAllocator;
fn record(counter: &'static std::thread::LocalKey<Cell<usize>>) {
    if TRACK.try_with(Cell::get).unwrap_or(false) {
        let _ = counter.try_with(|count| count.set(count.get() + 1));
    }
}
unsafe impl GlobalAlloc for TrackedAllocator {
    unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
        record(&ALLOCS);
        System.alloc(layout)
    }
    unsafe fn alloc_zeroed(&self, layout: Layout) -> *mut u8 {
        record(&ALLOCS);
        System.alloc_zeroed(layout)
    }
    unsafe fn realloc(&self, ptr: *mut u8, layout: Layout, size: usize) -> *mut u8 {
        record(&ALLOCS);
        System.realloc(ptr, layout, size)
    }
    unsafe fn dealloc(&self, ptr: *mut u8, layout: Layout) {
        record(&FREES);
        System.dealloc(ptr, layout)
    }
}
fn allocation_free(action: impl FnOnce() -> u64) -> u64 {
    ALLOCS.set(0);
    FREES.set(0);
    TRACK.set(true);
    let result = action();
    TRACK.set(false);
    assert_eq!(ALLOCS.get(), 0, "Critical function allocated");
    assert_eq!(FREES.get(), 0, "Critical function deallocated");
    result
}

unsafe extern "C" {
    fn mattmc_light_queue_apply(
        handle: u64,
        operation: i32,
        value: u64,
        level: i32,
        details: u64,
        output: *mut u64,
    ) -> u64;
    fn mattmc_light_queue_apply_fast(
        handle: u64,
        operation: i32,
        value: u64,
        level: i32,
        details: u64,
        output: *mut u64,
    ) -> u64;
    fn mattmc_light_queue_create(levels: i32, expected: i32) -> u64;
    fn mattmc_light_queue_release(handle: u64);
    fn mattmc_light_queue_enqueue(handle: u64, value: u64, level: i32) -> u64;
    fn mattmc_light_queue_dequeue(handle: u64, value: u64, level: i32, bound: i32) -> u64;
    fn mattmc_light_queue_pop(handle: u64, output: *mut u64) -> u64;
    fn mattmc_light_queue_enqueue_fast(handle: u64, value: u64, level: i32) -> u64;
    fn mattmc_light_queue_dequeue_fast(handle: u64, value: u64, level: i32, bound: i32) -> u64;
    fn mattmc_light_queue_pop_fast(handle: u64, output: *mut u64) -> u64;
}

#[test]
fn critical_calls_are_allocation_free_even_with_collisions_and_retries() {
    unsafe {
        let handle = mattmc_light_queue_create(34, 256);
        let mut invalid_output = 0xdeadbeef;
        for operation in [i32::MIN, -1, 6, i32::MAX] {
            assert_eq!(
                allocation_free(|| mattmc_light_queue_apply_fast(
                    handle,
                    operation,
                    17,
                    0,
                    0,
                    &mut invalid_output
                )),
                5
            );
            assert_eq!(
                mattmc_light_queue_apply(handle, operation, 17, 0, 0, &mut invalid_output),
                5
            );
            assert_eq!(invalid_output, 0xdeadbeef);
        }
        let phi = 0x9e3779b97f4a7c15u64;
        let mut inverse = 1u64;
        for _ in 0..6 {
            inverse = inverse.wrapping_mul(2u64.wrapping_sub(phi.wrapping_mul(inverse)));
        }
        let mut positions = [0u64; 128];
        for (index, position) in positions.iter_mut().enumerate() {
            // Same low table-index bits and high control-byte bits in every hash.
            let hash = ((index as u64) << 11) | 2047;
            let mixed = hash ^ (hash >> 16) ^ (hash >> 32) ^ (hash >> 48);
            *position = (mixed ^ (mixed >> 32)).wrapping_mul(inverse);
            let result = allocation_free(|| mattmc_light_queue_enqueue_fast(handle, *position, 7));
            let result = if result == 4 {
                mattmc_light_queue_enqueue(handle, *position, 7)
            } else {
                result
            };
            assert_eq!(result, 7u64 << 32);
        }
        for position in positions {
            let result = allocation_free(|| mattmc_light_queue_enqueue_fast(handle, position, 7));
            let result = if result == 4 {
                mattmc_light_queue_enqueue(handle, position, 7)
            } else {
                result
            };
            assert_eq!(result, 7u64 << 32);
        }
        let result =
            allocation_free(|| mattmc_light_queue_dequeue_fast(handle, positions[127], 7, 34));
        assert_eq!(result, 4, "Long collision search must retry");
        assert_eq!(
            mattmc_light_queue_dequeue(handle, positions[127], 7, 34),
            7u64 << 32
        );
        let mut output = u64::MAX;
        let mut retries = 0;
        for position in &positions[..127] {
            output = u64::MAX;
            let result = allocation_free(|| mattmc_light_queue_pop_fast(handle, &mut output));
            let result = if result == 4 {
                assert_eq!(output, u64::MAX, "Retry must not write output");
                retries += 1;
                mattmc_light_queue_pop(handle, &mut output)
            } else {
                result
            };
            assert_eq!(result as u32, 0);
            assert_eq!(output, *position);
        }
        assert!(retries > 0);
        assert_eq!(
            allocation_free(|| mattmc_light_queue_pop_fast(handle, &mut output)),
            (34u64 << 32) | 1
        );
        mattmc_light_queue_release(handle);
        let handle = mattmc_light_queue_create(34, 0);
        assert_eq!(
            allocation_free(|| mattmc_light_queue_enqueue_fast(handle, 17, 3)),
            3u64 << 32
        );
        assert_eq!(
            allocation_free(|| mattmc_light_queue_enqueue_fast(handle, 18, 3)),
            4
        );
        assert_eq!(
            allocation_free(|| mattmc_light_queue_pop_fast(handle, &mut output)) as u32,
            0
        );
        assert_eq!(
            output, 17,
            "Growth rejection must leave only the original value"
        );
        assert_eq!(mattmc_light_queue_enqueue(handle, 18, 3) as u32, 0);
        mattmc_light_queue_release(handle);
        let handle = mattmc_light_queue_create(257, 0);
        output = u64::MAX;
        assert_eq!(
            allocation_free(|| mattmc_light_queue_pop_fast(handle, &mut output)),
            4
        );
        assert_eq!(output, u64::MAX);
        mattmc_light_queue_release(handle);
    }
}

#[test]
fn critical_reschedule_rejects_growth_before_removing_old_entry() {
    unsafe {
        let handle = mattmc_light_queue_create(34, 0);
        assert_eq!(mattmc_light_queue_enqueue(handle, 17, 1) as u32, 0);
        assert_eq!(mattmc_light_queue_enqueue(handle, 23, 2) as u32, 0);
        let mut output = 0xdeadbeef;
        let details = (2u64 << 32) | 1;
        assert_eq!(
            allocation_free(|| mattmc_light_queue_apply_fast(
                handle,
                3,
                17,
                33,
                details,
                &mut output
            )),
            4
        );
        assert_eq!(output, 0xdeadbeef);
        assert_eq!(mattmc_light_queue_pop(handle, &mut output) as u32, 0);
        assert_eq!(output, 17, "rejected growth must leave old entry queued");
        assert_eq!(mattmc_light_queue_enqueue(handle, 17, 1) as u32, 0);
        assert_eq!(
            mattmc_light_queue_apply(handle, 3, 17, 33, details, &mut output) as u32,
            0
        );
        for value in [23, 17] {
            assert_eq!(
                allocation_free(|| mattmc_light_queue_apply_fast(handle, 2, 0, 0, 0, &mut output))
                    as u32,
                0
            );
            assert_eq!(output, value);
        }
        assert_eq!(mattmc_light_queue_pop(handle, &mut output) as u32, 1);
        mattmc_light_queue_release(handle);
    }
}

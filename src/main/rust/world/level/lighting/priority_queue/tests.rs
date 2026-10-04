use super::{
    ffi::*,
    ordered_set::OrderedSet,
    queue::{Error, Queue},
};

#[test]
fn insertion_order_removal_and_slot_reuse() {
    let mut set = OrderedSet::new(0).unwrap();
    for iteration in 0..10000 {
        for value in [0, u64::MAX, 17, 23] {
            set.insert(value).unwrap();
        }
        set.insert(17).unwrap();
        set.remove(17).unwrap();
        set.insert(17).unwrap();
        for value in [0, u64::MAX, 23, 17] {
            assert_eq!(set.pop().unwrap(), Some(value), "{iteration}");
        }
        assert_eq!(set.pop().unwrap(), None);
    }
    assert_eq!(set.allocated_slots(), 16);
}

#[test]
fn bounded_scan_and_failure_state_match_java() {
    let mut queue = Queue::new(4, 0).unwrap();
    queue.enqueue(1, 0).unwrap();
    queue.enqueue(2, 3).unwrap();
    queue.dequeue(1, 0, 2).unwrap();
    assert_eq!(queue.first, 2); // Deliberately points at an empty bucket.
    assert_eq!(queue.pop(), Err(Error::Empty));
    queue.enqueue(3, 1).unwrap();
    assert_eq!(queue.pop(), Ok(3));
    assert_eq!(queue.pop(), Ok(2));
    assert_eq!(queue.pop(), Err(Error::Index(4)));
    queue.enqueue(9, 0).unwrap();
    assert_eq!(queue.dequeue(9, 0, 8), Err(Error::Index(4)));
    assert_eq!(queue.first, 8); // Original updates first before scanning.
    queue.enqueue(9, 0).unwrap();
    queue.dequeue(9, 0, -1).unwrap();
    assert_eq!(queue.pop(), Err(Error::Index(-1)));
}

#[test]
fn opaque_handle_abi_preserves_all_value_bits() {
    unsafe {
        let handle = mattmc_light_queue_create(16, 0);
        assert_ne!(handle, 0);
        for value in [0, 1, 1u64 << 63, u64::MAX] {
            assert_eq!(mattmc_light_queue_enqueue(handle, value, 7), 7u64 << 32);
        }
        let mut output = 0;
        for value in [0, 1, 1u64 << 63, u64::MAX] {
            assert_eq!(mattmc_light_queue_pop(handle, &mut output) as u32, 0);
            assert_eq!(output, value);
        }
        assert_eq!(
            mattmc_light_queue_pop(handle, &mut output),
            (16u64 << 32) | 1
        );
        assert_eq!(output, 16);
        mattmc_light_queue_release(handle);
        mattmc_light_queue_release(0);
    }
}

#[test]
fn owner_serializes_native_handle_calls() {
    let handle = mattmc_light_queue_create(16, 0);
    assert_ne!(handle, 0);
    let owner = std::sync::Arc::new(std::sync::Mutex::new(handle));
    let mut threads = Vec::new();
    for thread in 0..4u64 {
        let owner = owner.clone();
        threads.push(std::thread::spawn(move || {
            for index in 0..1000u64 {
                let value = (thread << 32) | index;
                unsafe {
                    assert_eq!(
                        mattmc_light_queue_enqueue(*owner.lock().unwrap(), value, 7),
                        7u64 << 32
                    );
                }
            }
        }));
    }
    for thread in threads {
        thread.join().unwrap();
    }
    let mut next = [0u64; 4];
    unsafe {
        let mut output = 0;
        for _ in 0..4000 {
            assert_eq!(mattmc_light_queue_pop(handle, &mut output) as u32, 0);
            let thread = (output >> 32) as usize;
            assert_eq!(output as u32 as u64, next[thread]);
            next[thread] += 1;
        }
        assert_eq!(next, [1000; 4]);
        mattmc_light_queue_release(handle);
    }
}

#[test]
fn bounded_insert_reuses_storage_and_rejects_growth_before_mutation() {
    let mut set = OrderedSet::new(0).unwrap();
    assert!(set.insert_without_growth(17));
    assert!(!set.insert_without_growth(23));
    assert_eq!(set.allocated_slots(), 2);
    assert_eq!(set.pop().unwrap(), Some(17));
    assert!(set.is_empty());
    assert!(set.insert_without_growth(23));
    assert_eq!(set.allocated_slots(), 2);
}

#[test]
fn fast_and_ordinary_paths_have_identical_transitions() {
    unsafe {
        let mut rng = 1977u64;
        let mut retries = 0;
        for levels in [0, 1, 7, 34, 256, 257] {
            let normal = mattmc_light_queue_create(levels, 0);
            let fast = mattmc_light_queue_create(levels, 0);
            for _ in 0..20000 {
                rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1);
                let level = ((rng >> 32) % (levels + 2) as u64) as i32 - 1;
                let value = rng & 4095;
                let bound = match (rng >> 12) & 3 {
                    0 => i32::MIN,
                    1 => i32::MAX,
                    _ => levels,
                };
                let mut a = 0xdeadbeef;
                let mut b = 0xdeadbeef;
                let (ordinary, mut accelerated) = match rng % 3 {
                    0 => (
                        mattmc_light_queue_enqueue(normal, value, level),
                        mattmc_light_queue_enqueue_fast(fast, value, level),
                    ),
                    1 => (
                        mattmc_light_queue_dequeue(normal, value, level, bound),
                        mattmc_light_queue_dequeue_fast(fast, value, level, bound),
                    ),
                    _ => (
                        mattmc_light_queue_pop(normal, &mut a),
                        mattmc_light_queue_pop_fast(fast, &mut b),
                    ),
                };
                if accelerated == 4 {
                    assert_eq!(b, 0xdeadbeef);
                    retries += 1;
                    accelerated = match rng % 3 {
                        0 => mattmc_light_queue_enqueue(fast, value, level),
                        1 => mattmc_light_queue_dequeue(fast, value, level, bound),
                        _ => mattmc_light_queue_pop(fast, &mut b),
                    };
                }
                assert_eq!(accelerated, ordinary);
                assert_eq!(a, b);
            }
            mattmc_light_queue_release(normal);
            mattmc_light_queue_release(fast);
        }
        assert!(retries > 20000);
    }
}

#[test]
fn ordered_table_matches_a_simple_fifo_model() {
    use std::collections::VecDeque;
    let mut rng = 1977u64;
    for expected in [0, 1, 16, 64, 511] {
        let mut set = OrderedSet::new(expected).unwrap();
        let mut model = VecDeque::new();
        for _ in 0..30000 {
            rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1);
            let value = if rng & 1 == 0 { rng } else { (rng >> 32) & 127 };
            match (rng >> 8) % 3 {
                0 if model.len() < 512 => {
                    set.insert(value).unwrap();
                    if !model.contains(&value) {
                        model.push_back(value);
                    }
                }
                1 => {
                    set.remove(value).unwrap();
                    if let Some(index) = model.iter().position(|v| *v == value) {
                        model.remove(index);
                    }
                }
                _ => assert_eq!(set.pop().unwrap(), model.pop_front()),
            }
            assert_eq!(set.is_empty(), model.is_empty());
        }
        while let Some(value) = model.pop_front() {
            assert_eq!(set.pop().unwrap(), Some(value));
        }
        assert!(set.is_empty());
    }
}

#[test]
fn scheduling_matches_original_primitive_sequence_and_retries() {
    let mut rng = 7331u64;
    let mut retries = 0;
    for levels in [0, 1, 7, 34, 253] {
        let mut original = Queue::new(levels, 0).unwrap();
        unsafe {
            let native = mattmc_light_queue_create(levels, 0);
            for step in 0..20000 {
                rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1);
                let value = rng & 127;
                let current = if step % 17 == 0 {
                    i32::MIN
                } else {
                    ((rng >> 32) % (levels + 3) as u64) as i32 - 1
                };
                let previous = if step % 5 == 0 {
                    255
                } else {
                    ((rng >> 20) % (levels + 3) as u64) as i32 - 1
                };
                let next = if step % 19 == 0 {
                    i32::MAX
                } else {
                    ((rng >> 10) % (levels + 3) as u64) as i32 - 1
                };
                let operation = if step % 7 < 4 {
                    3
                } else if step % 7 == 4 {
                    4
                } else if step % 7 == 5 {
                    5
                } else {
                    2
                };
                let priority = |computed: i32| current.min(computed).min(levels - 1);
                let expected = match operation {
                    3 => {
                        let old = priority(previous);
                        let destination = priority(next);
                        let removal = if previous != 255 && old != destination {
                            original.dequeue(value, old, destination)
                        } else {
                            Ok(())
                        };
                        removal
                            .and_then(|_| original.enqueue(value, destination))
                            .map(|_| 0)
                    }
                    4 => original
                        .dequeue(value, priority(previous), levels)
                        .map(|_| 0),
                    5 => original.enqueue(value, priority(previous)).map(|_| 0),
                    _ => original.pop(),
                };
                let details = ((next as u32 as u64) << 32) | previous as u32 as u64;
                let mut output = 0xdeadbeef;
                let mut actual = mattmc_light_queue_apply_fast(
                    native,
                    operation,
                    value,
                    current,
                    details,
                    &mut output,
                );
                if actual == 4 {
                    assert_eq!(output, 0xdeadbeef, "retry output unchanged");
                    retries += 1;
                    actual = mattmc_light_queue_apply(
                        native,
                        operation,
                        value,
                        current,
                        details,
                        &mut output,
                    );
                }
                assert_eq!((actual >> 32) as u32 as i32, original.first);
                match expected {
                    Ok(value) => {
                        assert_eq!(actual as u32, 0);
                        if operation == 2 {
                            assert_eq!(output, value);
                        }
                    }
                    Err(Error::Index(index)) => {
                        assert_eq!(actual as u32, 1);
                        assert_eq!(mattmc_light_queue_error_index(native), index);
                        if operation == 2 {
                            assert_eq!(output as i64, index as i64);
                        }
                    }
                    Err(Error::Empty) => assert_eq!(actual as u32, 2),
                    Err(Error::Allocation) => panic!("unexpected allocation failure"),
                }
            }
            mattmc_light_queue_release(native);
        }
    }
    assert!(retries > 0);
}

use super::{
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
fn compound_scheduling_matches_original_primitive_sequence() {
    // The distance graphs call reschedule/cancel/enqueue-computed; each must
    // equal the original graph's dequeue/enqueue sequence, failures included.
    let mut rng = 7331u64;
    for levels in [0, 1, 7, 34, 253] {
        let mut original = Queue::new(levels, 0).unwrap();
        let mut compound = Queue::new(levels, 0).unwrap();
        for step in 0..20000 {
            rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1);
            let value = rng & 127;
            let current = if step % 17 == 0 { i32::MIN } else { ((rng >> 32) % (levels + 3) as u64) as i32 - 1 };
            let previous = if step % 5 == 0 { 255 } else { ((rng >> 20) % (levels + 3) as u64) as i32 - 1 };
            let next = if step % 19 == 0 { i32::MAX } else { ((rng >> 10) % (levels + 3) as u64) as i32 - 1 };
            let priority = |computed: i32| current.min(computed).min(levels - 1);
            let (expected, actual) = match step % 7 {
                0..=3 => {
                    let old = priority(previous);
                    let destination = priority(next);
                    let removal = if previous != 255 && old != destination {
                        original.dequeue(value, old, destination)
                    } else {
                        Ok(())
                    };
                    (
                        removal.and_then(|_| original.enqueue(value, destination)).map(|_| 0),
                        compound.reschedule(value, current, previous, next).map(|_| 0),
                    )
                }
                4 => (
                    original.dequeue(value, priority(previous), levels).map(|_| 0),
                    compound.cancel_computed(value, current, previous).map(|_| 0),
                ),
                5 => (
                    original.enqueue(value, priority(previous)).map(|_| 0),
                    compound.enqueue_computed(value, current, previous).map(|_| 0),
                ),
                _ => (original.pop(), compound.pop()),
            };
            assert_eq!(expected, actual, "levels {levels} step {step}");
            assert_eq!(original.first, compound.first, "levels {levels} step {step}");
        }
    }
}

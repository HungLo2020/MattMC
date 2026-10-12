//! Retained propagation inputs and a non-copying transaction journal.
use std::collections::VecDeque;
use std::sync::{
    atomic::{AtomicI32, Ordering},
    Arc, Mutex,
};

pub(super) struct Queues {
    pub(super) data: Mutex<[VecDeque<i64>; 2]>,
    pub(super) counts: [AtomicI32; 2],
}
impl Default for Queues {
    fn default() -> Self {
        Self {
            data: Mutex::new(std::array::from_fn(|_| VecDeque::new())),
            counts: std::array::from_fn(|_| AtomicI32::new(0)),
        }
    }
}
impl Queues {
    pub(super) fn publish(&self, queues: &[VecDeque<i64>; 2]) {
        for (target, queue) in self.counts.iter().zip(queues) {
            target.store(queue.len() as i32, Ordering::Release);
        }
    }
    pub(super) fn take(&self) -> [VecDeque<i64>; 2] {
        let mut queues = self.data.lock().unwrap();
        let inputs = std::mem::take(&mut *queues);
        self.publish(&queues);
        inputs
    }
    /// Callback additions precede restored original inputs, like Java's refill.
    pub(super) fn finish(&self, mut inputs: [VecDeque<i64>; 2], restore: bool) {
        let mut queues = self.data.lock().unwrap();
        for (pending, original) in queues.iter_mut().zip(&mut inputs) {
            if restore {
                pending.append(original);
            } else {
                original.clear();
                if pending.is_empty() && original.capacity() > pending.capacity() {
                    std::mem::swap(pending, original);
                }
            }
        }
        self.publish(&queues);
    }
}

/// Original inputs stay intact until admission succeeds. Only newly generated
/// propagation work is dequeued destructively; no input clone is needed.
#[derive(Default)]
pub(super) struct WorkQueue {
    initial: VecDeque<i64>,
    cursor: usize,
    generated: VecDeque<(i64, u64)>,
}
impl WorkQueue {
    pub(super) fn clear(&mut self) {
        self.initial.clear();
        self.cursor = 0;
        self.generated.clear();
    }
    pub(super) fn adopt(&mut self, initial: VecDeque<i64>) {
        self.initial = initial;
        self.cursor = 0;
    }
    pub(super) fn push_back(&mut self, value: (i64, u64)) {
        self.generated.push_back(value);
    }
    pub(super) fn pop_front(&mut self) -> Option<(i64, u64)> {
        if self.cursor < self.initial.len() {
            let pair = (
                self.initial[self.cursor],
                self.initial[self.cursor + 1] as u64,
            );
            self.cursor += 2;
            Some(pair)
        } else {
            self.generated.pop_front()
        }
    }
    pub(super) fn journal(&mut self) -> VecDeque<i64> {
        self.cursor = 0;
        std::mem::take(&mut self.initial)
    }
}

/// Independent queue ownership lets callbacks enqueue without aliasing the
/// excluded propagation engine or holding a native lock across an upcall.
pub(super) unsafe fn retain(pointer: *const Queues) -> Arc<Queues> {
    Arc::increment_strong_count(pointer);
    Arc::from_raw(pointer)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn original_inputs_precede_generated_work_and_survive_partial_consumption() {
        let mut queue = WorkQueue::default();
        queue.adopt(VecDeque::from([1, 11, 2, 22]));
        queue.push_back((3, 33));
        assert_eq!(queue.pop_front(), Some((1, 11)));
        assert_eq!(queue.journal(), VecDeque::from([1, 11, 2, 22]));
        queue.clear();
        queue.adopt(VecDeque::from([1, 11, 2, 22]));
        queue.push_back((3, 33));
        assert_eq!(queue.pop_front(), Some((1, 11)));
        assert_eq!(queue.pop_front(), Some((2, 22)));
        assert_eq!(queue.pop_front(), Some((3, 33)));
        assert_eq!(queue.pop_front(), None);
    }
    #[test]
    fn rejected_inputs_append_after_callback_additions_and_success_recycles_capacity() {
        let owner = Queues::default();
        owner.data.lock().unwrap()[0].extend([1, 11, 2, 22]);
        let inputs = owner.take();
        owner.data.lock().unwrap()[0].extend([3, 33]);
        owner.finish(inputs, true);
        assert_eq!(
            owner.data.lock().unwrap()[0],
            VecDeque::from([3, 33, 1, 11, 2, 22])
        );
        let inputs = owner.take();
        let capacity = inputs[0].capacity();
        owner.finish(inputs, false);
        assert_eq!(owner.counts[0].load(Ordering::Acquire), 0);
        assert!(owner.data.lock().unwrap()[0].capacity() >= capacity);
    }
}

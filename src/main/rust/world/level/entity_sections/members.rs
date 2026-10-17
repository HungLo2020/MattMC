//! ArrayList-compatible primary member storage, including captured old arrays.
use super::bindings::Bindings;
use std::sync::{
    atomic::{AtomicI32, AtomicU64, Ordering},
    Arc,
};

pub(super) struct Array {
    pub(super) cells: Box<[AtomicU64]>,
    bindings: Option<Arc<Bindings>>,
}
impl Array {
    fn new(capacity: usize, bindings: Option<Arc<Bindings>>) -> Self {
        Self {
            cells: (0..capacity).map(|_| AtomicU64::new(0)).collect(),
            bindings,
        }
    }
    pub(super) fn get(&self, index: usize) -> Option<u64> {
        self.cells
            .get(index)
            .map(|cell| cell.load(Ordering::Acquire))
    }
    fn set(&self, index: usize, value: u64) {
        if let Some(bindings) = &self.bindings {
            bindings.retain(value);
        }
        let old = self.cells[index].swap(value, Ordering::AcqRel);
        if let Some(bindings) = &self.bindings {
            bindings.release(old);
        }
    }
}

impl Drop for Array {
    fn drop(&mut self) {
        if let Some(bindings) = &self.bindings {
            for cell in &self.cells {
                bindings.release(cell.load(Ordering::Relaxed));
            }
        }
    }
}

/// Stable scalar header for direct immutable Java CPU views. Mutation remains
/// subject to the original world-thread contract; atomics do not authorize
/// concurrent behavior callbacks or member mutation.
#[repr(C)]
pub(super) struct View {
    pointer: AtomicU64,
    capacity: AtomicI32,
    size: AtomicI32,
    modifications: AtomicI32,
    revision: AtomicI32,
}

pub(super) struct Members {
    pub(super) query_type: u64,
    array: Arc<Array>,
    pub(super) view: View,
}
#[derive(Debug, PartialEq, Eq)]
pub(super) enum RemoveError {
    CopyBounds {
        source: i32,
        destination: i32,
        length: i32,
        capacity: usize,
    },
    StoreBounds {
        index: i32,
        capacity: usize,
    },
}
#[derive(Debug, PartialEq, Eq)]
pub(super) enum AddError {
    StoreBounds { index: i32, capacity: usize },
}
impl Default for Members {
    fn default() -> Self {
        let array = Arc::new(Array::new(0, None));
        Self {
            query_type: 0,
            array,
            view: View {
                pointer: AtomicU64::new(0),
                capacity: AtomicI32::new(0),
                size: AtomicI32::new(0),
                modifications: AtomicI32::new(0),
                revision: AtomicI32::new(1),
            },
        }
    }
}
impl Members {
    pub(super) fn with_bindings(bindings: Arc<Bindings>) -> Self {
        let mut members = Self::default();
        members.array = Arc::new(Array::new(0, Some(bindings)));
        members
    }
    pub(super) fn size(&self) -> i32 {
        self.view.size.load(Ordering::Acquire)
    }
    pub(super) fn modifications(&self) -> i32 {
        self.view.modifications.load(Ordering::Acquire)
    }
    pub(super) fn capture(&self) -> Arc<Array> {
        Arc::clone(&self.array)
    }
    pub(super) fn get(&self, index: usize) -> Option<u64> {
        self.array.get(index)
    }
    fn modified(&self) {
        self.view.modifications.fetch_add(1, Ordering::AcqRel);
        super::super::entity_geometry::advance_spatial_revision();
    }
    pub(super) fn add(&mut self, token: u64) -> Result<(), AddError> {
        // Mirrors ArrayList.add's pre-growth modCount prefix.
        self.modified();
        let size = self.size();
        if size < 0 {
            return Err(AddError::StoreBounds {
                index: size,
                capacity: self.array.cells.len(),
            });
        }
        let size = size as usize;
        if size == self.array.cells.len() {
            let old_capacity = self.array.cells.len();
            let capacity = if old_capacity == 0 {
                10
            } else {
                (old_capacity + old_capacity / 2).max(size + 1)
            };
            assert!(capacity <= i32::MAX as usize);
            let replacement = Arc::new(Array::new(capacity, self.array.bindings.clone()));
            for i in 0..size {
                replacement.set(i, self.array.get(i).unwrap());
            }
            self.array = replacement;
            self.view
                .pointer
                .store(self.array.cells.as_ptr() as u64, Ordering::Release);
            self.view.capacity.store(capacity as i32, Ordering::Release);
            self.view.revision.fetch_add(1, Ordering::AcqRel);
        }
        self.array.set(size, token);
        self.view.size.store((size + 1) as i32, Ordering::Release);
        Ok(())
    }
    /// Equality callbacks capture the receiver's old elementData. A later
    /// successful match removes from that same array using the *current* size,
    /// even if nested mutation has replaced the active backing array.
    pub(super) fn remove_from(
        &mut self,
        captured: &Arc<Array>,
        index: i32,
    ) -> Result<(), RemoveError> {
        self.modified();
        let new_size = self.size().wrapping_sub(1);
        let capacity = captured.cells.len();
        if new_size > index {
            let source = index.wrapping_add(1);
            let length = new_size.wrapping_sub(index);
            if source < 0
                || index < 0
                || length < 0
                || i64::from(source) + i64::from(length) > capacity as i64
                || i64::from(index) + i64::from(length) > capacity as i64
            {
                return Err(RemoveError::CopyBounds {
                    source,
                    destination: index,
                    length,
                    capacity,
                });
            }
            for i in 0..length as usize {
                captured.set(
                    index as usize + i,
                    captured.get(source as usize + i).unwrap(),
                );
            }
        }
        // Java's size assignment happens before the final array store check.
        self.view.size.store(new_size, Ordering::Release);
        if new_size < 0 || new_size as usize >= capacity {
            return Err(RemoveError::StoreBounds {
                index: new_size,
                capacity,
            });
        }
        captured.set(new_size as usize, 0);
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn retained_old_array_keeps_bindings_until_final_lease_drops() {
        let bindings = Arc::new(Bindings::default());
        let mut members = Members::with_bindings(bindings.clone());
        let mut tokens = Vec::new();
        for _ in 0..10 {
            let token = bindings.allocate();
            members.add(token).unwrap();
            bindings.release(token);
            tokens.push(token);
        }
        let old = members.capture();
        let extra = bindings.allocate();
        members.add(extra).unwrap();
        bindings.release(extra);
        let current = members.capture();
        members.remove_from(&current, 0).unwrap();
        assert_eq!(
            bindings.retired(),
            0,
            "captured old array still owns first binding"
        );
        drop(old);
        assert_eq!(bindings.retired(), tokens[0]);
        let reused = bindings.allocate();
        assert_eq!(reused as u32, tokens[0] as u32);
        assert_ne!(reused, tokens[0]);
        bindings.release(reused);
    }
    #[test]
    fn repeated_membership_retirement_reuses_slot_with_distinct_generations() {
        let bindings = Arc::new(Bindings::default());
        let mut members = Members::with_bindings(bindings.clone());
        let mut previous = 0;
        for _ in 0..10_000 {
            let token = bindings.allocate();
            assert_eq!(token as u32, 1);
            assert!(token > previous);
            previous = token;
            members.add(token).unwrap();
            bindings.release(token);
            let array = members.capture();
            members.remove_from(&array, 0).unwrap();
            drop(array);
            assert_eq!(bindings.retired(), token);
            assert_eq!(members.size(), 0);
        }
    }
    #[test]
    fn growth_keeps_old_array_while_quiet_reads_use_primary_storage() {
        let mut members = Members::default();
        for token in 1..=10 {
            members.add(token).unwrap();
        }
        let old = members.capture();
        members.add(11).unwrap();
        assert_eq!(members.size(), 11);
        assert_eq!(members.array.cells.len(), 15);
        assert_eq!(old.cells.len(), 10);
        assert_eq!(old.get(9), Some(10));
        assert_eq!(members.get(10), Some(11));
        assert_eq!(members.modifications(), 11);
    }
    #[test]
    fn equality_receiver_uses_captured_array_after_callback_growth() {
        let mut members = Members::default();
        for token in 1..=10 {
            members.add(token).unwrap();
        }
        let old = members.capture();
        members.add(11).unwrap();
        let count = members.modifications();
        assert_eq!(
            members.remove_from(&old, 0),
            Err(RemoveError::CopyBounds {
                source: 1,
                destination: 0,
                length: 10,
                capacity: 10,
            })
        );
        assert_eq!(members.size(), 11);
        assert_eq!(members.modifications(), count + 1);
        assert_eq!(old.get(0), Some(1));
    }
    #[test]
    fn nested_empty_removal_preserves_size_assignment_before_store_failure() {
        let mut members = Members::default();
        members.add(1).unwrap();
        let old = members.capture();
        members.remove_from(&old, 0).unwrap();
        assert_eq!(
            members.remove_from(&old, 0),
            Err(RemoveError::StoreBounds {
                index: -1,
                capacity: 10
            })
        );
        assert_eq!(members.size(), -1);
        assert_eq!(members.modifications(), 3);
        assert_eq!(
            members.add(2),
            Err(AddError::StoreBounds {
                index: -1,
                capacity: 10
            })
        );
        assert_eq!(members.size(), -1);
        assert_eq!(members.modifications(), 4);
    }
    #[test]
    fn non_growing_callback_mutation_is_visible_through_captured_array() {
        let mut members = Members::default();
        for token in 1..=4 {
            members.add(token).unwrap();
        }
        let old = members.capture();
        members.remove_from(&old, 1).unwrap();
        assert_eq!(
            (old.get(0), old.get(1), old.get(2), old.get(3)),
            (Some(1), Some(3), Some(4), Some(0))
        );
        members.add(5).unwrap();
        assert_eq!(old.get(3), Some(5));
    }
}

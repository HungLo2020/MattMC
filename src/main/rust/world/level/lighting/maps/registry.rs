//! Temporary CPU identity pins for legacy Java objects. Map policy and typed
//! layer ownership stay native; Java only holds the corresponding object slots.
use std::collections::{HashSet, VecDeque};
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::{Arc, Mutex};

#[repr(C)]
pub struct Metadata {
    pub retired: AtomicU32,
}
#[derive(Default)]
struct Slots {
    next: u64,
    free: Vec<u32>,
    reserved: HashSet<u32>,
    active: HashSet<u32>,
    pending: VecDeque<u32>,
    awaiting_ack: HashSet<u32>,
}
pub struct Registry {
    pub metadata: Metadata,
    slots: Mutex<Slots>,
}
impl Registry {
    pub fn new() -> Arc<Self> {
        Arc::new(Self {
            metadata: Metadata {
                retired: AtomicU32::new(0),
            },
            slots: Mutex::new(Slots {
                next: 1,
                ..Slots::default()
            }),
        })
    }
    pub fn reserve(self: &Arc<Self>) -> Option<Reservation> {
        let mut state = self.slots.lock().unwrap();
        let slot = if let Some(slot) = state.free.pop() {
            slot
        } else {
            let slot = u32::try_from(state.next).ok()?;
            state.next += 1;
            slot
        };
        assert!(state.reserved.insert(slot));
        Some(Reservation {
            registry: Arc::clone(self),
            slot,
            installed: false,
        })
    }
    fn retire(&self, slot: u32) {
        let mut state = self.slots.lock().unwrap();
        assert!(state.active.remove(&slot));
        state.pending.push_back(slot);
        self.metadata.retired.fetch_add(1, Ordering::Release);
    }
    pub fn take_retired_into(&self, output: &mut [u32]) -> usize {
        let mut state = self.slots.lock().unwrap();
        let count = output.len().min(state.pending.len());
        for target in &mut output[..count] {
            let slot = state.pending.pop_front().unwrap();
            assert!(state.awaiting_ack.insert(slot));
            *target = slot;
        }
        count
    }
    #[cfg(test)]
    fn take_retired(&self, limit: usize) -> Vec<u32> {
        let mut output = vec![0; limit];
        let count = self.take_retired_into(&mut output);
        output.truncate(count);
        output
    }
    /// Java must clear every returned object slot before acknowledgement makes
    /// those IDs available for reuse. Invalid batches do not partially mutate.
    pub fn acknowledge(&self, slots: &[u32]) -> bool {
        let mut state = self.slots.lock().unwrap();
        if slots.len() > 64 {
            return false;
        }
        let mut sorted = [0u32; 64];
        sorted[..slots.len()].copy_from_slice(slots);
        sorted[..slots.len()].sort_unstable();
        if sorted[..slots.len()]
            .windows(2)
            .any(|pair| pair[0] == pair[1])
            || slots.iter().any(|slot| !state.awaiting_ack.contains(slot))
        {
            return false;
        }
        for &slot in slots {
            state.awaiting_ack.remove(&slot);
            state.free.push(slot);
        }
        self.metadata
            .retired
            .fetch_sub(slots.len() as u32, Ordering::Release);
        true
    }
}
pub struct Reservation {
    pub registry: Arc<Registry>,
    pub slot: u32,
    installed: bool,
}
impl Reservation {
    pub fn install(mut self) -> Pin {
        {
            let mut state = self.registry.slots.lock().unwrap();
            assert!(state.reserved.remove(&self.slot));
            assert!(state.active.insert(self.slot));
        }
        self.installed = true;
        Pin {
            registry: Arc::clone(&self.registry),
            slot: self.slot,
        }
    }
}
impl Drop for Reservation {
    fn drop(&mut self) {
        if !self.installed {
            let mut state = self.registry.slots.lock().unwrap();
            assert!(state.reserved.remove(&self.slot));
            state.free.push(self.slot);
        }
    }
}
pub struct Pin {
    registry: Arc<Registry>,
    pub slot: u32,
}
impl Drop for Pin {
    fn drop(&mut self) {
        self.registry.retire(self.slot);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn canceled_handoff_reuses_slot_without_retiring_a_java_object() {
        let registry = Registry::new();
        let reservation = registry.reserve().unwrap();
        let id = reservation.slot;
        drop(reservation);
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 0);
        assert_eq!(registry.reserve().unwrap().slot, id);
    }
    #[test]
    fn copies_and_cache_pins_retire_only_after_the_last_reference() {
        let registry = Registry::new();
        let pin = Arc::new(registry.reserve().unwrap().install());
        let id = pin.slot;
        let cache = Arc::clone(&pin);
        let snapshot = Arc::clone(&pin);
        drop(pin);
        drop(snapshot);
        assert!(registry.take_retired(64).is_empty());
        drop(cache);
        assert_eq!(registry.take_retired(64), [id]);
        // The object remains pinned on the Java side until this batch is acked.
        let other = registry.reserve().unwrap();
        assert_ne!(other.slot, id);
        assert!(registry.acknowledge(&[id]));
        assert_eq!(registry.reserve().unwrap().slot, id);
    }
    #[test]
    fn invalid_acknowledgement_is_atomic_and_drain_is_bounded() {
        let registry = Registry::new();
        let pins: Vec<_> = (0..130)
            .map(|_| registry.reserve().unwrap().install())
            .collect();
        drop(pins);
        let first = registry.take_retired(64);
        assert_eq!(first.len(), 64);
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 130);
        assert!(!registry.acknowledge(&[first[0], first[0]]));
        assert!(!registry.acknowledge(&[first[0], u32::MAX]));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 130);
        assert!(registry.acknowledge(&first));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 66);
        let second = registry.take_retired(64);
        assert!(registry.acknowledge(&second));
        let last = registry.take_retired(64);
        assert_eq!(last.len(), 2);
        assert!(registry.acknowledge(&last));
        assert_eq!(registry.metadata.retired.load(Ordering::Acquire), 0);
    }
    #[test]
    fn repeated_retirement_keeps_all_slot_tables_bounded() {
        let registry = Registry::new();
        for _ in 0..100_000 {
            drop(registry.reserve().unwrap().install());
            let retired = registry.take_retired(64);
            assert_eq!(retired.len(), 1);
            assert!(registry.acknowledge(&retired));
        }
        let state = registry.slots.lock().unwrap();
        assert_eq!(state.next, 2);
        assert_eq!(state.free, [1]);
        assert!(
            state.reserved.is_empty()
                && state.active.is_empty()
                && state.pending.is_empty()
                && state.awaiting_ack.is_empty()
        );
    }
}

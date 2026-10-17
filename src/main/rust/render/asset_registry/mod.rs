//! Ordered semantic asset membership. Java temporarily projects payload objects
//! through slot IDs; keys, slot reuse, ordering and membership history live here.
//! The render producer lock excludes mutation while a CPU view is being read.
use std::collections::{HashMap, HashSet};
use std::hash::{BuildHasherDefault, Hasher};

mod ffi;
mod proof;
#[cfg(test)]
mod tests;

const NONE: u32 = u32::MAX;
const MAX_EVENTS: usize = 1 << 18;

#[derive(Default)]
struct KeyHasher(u64);
impl Hasher for KeyHasher {
    fn write(&mut self, bytes: &[u8]) {
        for &byte in bytes {
            self.0 = self.0.rotate_left(5) ^ u64::from(byte);
        }
    }
    fn write_i64(&mut self, value: i64) {
        self.0 = (value as u64).wrapping_mul(0x9e3779b97f4a7c15);
    }
    fn finish(&self) -> u64 {
        self.0
    }
}
type KeyMap<V> = HashMap<i64, V, BuildHasherDefault<KeyHasher>>;

/// Read-only CPU header. Refreshed before each mutating ABI call returns.
#[repr(C)]
pub struct Header {
    epoch: u64,
    events: u32,
    size: u32,
    version: u64,
    head: u32,
    slots_len: u32,
    slots: *const Slot,
}
#[repr(C)]
#[derive(Clone, Copy)]
struct Slot {
    key: i64,
    next: u32,
    previous: u32,
    revision: u64,
}

#[repr(C)]
pub struct Registry {
    header: Header,
    keys: KeyMap<u32>,
    slots: Vec<Slot>,
    free: Vec<u32>,
    tail: u32,
    journal: Vec<(i64, bool)>,
    added: Vec<i64>,
    revision: u64,
}
impl Default for Registry {
    fn default() -> Self {
        Self {
            header: Header {
                epoch: 0,
                events: 0,
                size: 0,
                version: 0,
                head: NONE,
                slots_len: 0,
                slots: std::ptr::null(),
            },
            keys: KeyMap::default(),
            slots: Vec::new(),
            free: Vec::new(),
            tail: NONE,
            journal: Vec::new(),
            added: Vec::new(),
            revision: 0,
        }
    }
}
impl Registry {
    fn refresh(&mut self) {
        self.header.events = self.journal.len() as u32;
        self.header.size = self.keys.len() as u32;
        self.header.slots_len = self.slots.len() as u32;
        self.header.slots = self.slots.as_ptr();
    }
    fn reset(&mut self) {
        self.journal.clear();
        self.header.epoch = self.header.epoch.wrapping_add(1);
        self.header.events = 0;
    }
    fn record(&mut self, key: i64, added: bool) {
        if self.journal.len() >= MAX_EVENTS {
            self.reset();
        }
        self.journal.push((key, added));
    }
    fn put(&mut self, key: i64) -> u32 {
        self.revision = self.revision.saturating_add(1);
        if let Some(&slot) = self.keys.get(&key) {
            self.slots[slot as usize].revision = self.revision;
            return slot;
        }
        let value = Slot {
            key,
            next: NONE,
            previous: self.tail,
            revision: self.revision,
        };
        let slot = if let Some(slot) = self.free.pop() {
            self.slots[slot as usize] = value;
            slot
        } else {
            let slot = u32::try_from(self.slots.len()).expect("asset registry exhausted");
            assert!(slot < i32::MAX as u32, "asset registry exhausted");
            self.slots.push(value);
            slot
        };
        if self.tail == NONE {
            self.header.head = slot;
        } else {
            self.slots[self.tail as usize].next = slot;
        }
        self.tail = slot;
        self.keys.insert(key, slot);
        self.record(key, true);
        self.header.version = self.header.version.wrapping_add(1);
        self.refresh();
        slot
    }
    fn remove(&mut self, key: i64) -> Option<u32> {
        let slot = self.keys.remove(&key)?;
        let value = self.slots[slot as usize];
        self.slots[slot as usize].revision = 0;
        if value.previous == NONE {
            self.header.head = value.next;
        } else {
            self.slots[value.previous as usize].next = value.next;
        }
        if value.next == NONE {
            self.tail = value.previous;
        } else {
            self.slots[value.next as usize].previous = value.previous;
        }
        self.free.push(slot);
        self.record(key, false);
        self.header.version = self.header.version.wrapping_add(1);
        self.refresh();
        Some(slot)
    }
    fn clear(&mut self) {
        let mut at = self.header.head;
        while at != NONE {
            let value = self.slots[at as usize];
            at = value.next;
            self.record(value.key, false);
        }
        self.keys.clear();
        self.slots.clear();
        self.free.clear();
        self.header.head = NONE;
        self.tail = NONE;
        self.header.version = self.header.version.wrapping_add(1);
        self.refresh();
    }
    fn added_since(&mut self, epoch: u64, position: i32) -> bool {
        self.added.clear();
        if epoch != self.header.epoch || position < 0 || position as usize > self.journal.len() {
            return false;
        }
        let mut seen = HashSet::<i64, BuildHasherDefault<KeyHasher>>::default();
        for &(key, added) in &self.journal[position as usize..] {
            if seen.insert(key) && added {
                self.added.push(key);
            }
        }
        true
    }
}

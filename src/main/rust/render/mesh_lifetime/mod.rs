//! Semantic lifetime of dynamic model/item mesh generations. This tracker
//! never destroys GPU resources: returned retirements still cross the GAL's
//! asset update and completion boundary. Callers serialize access to an owner.
use std::collections::HashMap;
use std::hash::{BuildHasherDefault, Hasher};

mod ffi;
#[cfg(test)]
mod tests;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
struct Usage { generation: i64, frame: i64 }
#[derive(Clone, Copy, Debug)]
struct Entry { key: i64, usage: Usage, previous: u32, next: u32 }
const NONE: u32 = u32::MAX;
enum Change { Put(i64, Option<Usage>), Snapshot(Vec<(i64, Usage)>) }

// Mesh identities are already content hashes. Mix all signed key bits without
// SipHash's string-key machinery; this map is private to numeric asset IDs.
#[derive(Default)]
struct KeyHasher(u64);
impl Hasher for KeyHasher {
    fn write(&mut self, bytes: &[u8]) {
        for &byte in bytes { self.0 = self.0.rotate_left(5) ^ u64::from(byte); }
    }
    fn write_i64(&mut self, key: i64) {
        self.0 = (key as u64).wrapping_mul(0x9e3779b97f4a7c15);
    }
    fn finish(&self) -> u64 { self.0 }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Retirement { key: i64, generation: i64 }

pub struct Lifetime {
    entries: HashMap<i64, u32, BuildHasherDefault<KeyHasher>>,
    slots: Vec<Entry>,
    free: Vec<u32>,
    head: u32,
    tail: u32,
    changes: Vec<Change>,
    checkpoint_frame: i64,
    retired: Vec<Retirement>,
}
impl Default for Lifetime {
    fn default() -> Self {
        Self { entries: HashMap::default(), slots: Vec::new(), free: Vec::new(), head: NONE, tail: NONE,
            changes: Vec::new(), checkpoint_frame: 0, retired: Vec::new() }
    }
}
impl Lifetime {
    fn checkpoint(&mut self, frame: i64) -> Option<usize> {
        if frame <= 0 { return None; }
        if self.checkpoint_frame != frame {
            self.changes.clear();
            self.checkpoint_frame = frame;
        }
        Some(self.changes.len())
    }
    fn discard(&mut self) {
        self.changes.clear();
        self.checkpoint_frame = 0;
    }
    pub(crate) fn observe(&mut self, key: i64, generation: i64, frame: i64) -> bool {
        // Keys/generations are signed Java longs; only zero is forbidden.
        if key == 0 || generation == 0 || frame <= 0 { return false; }
        let usage = Usage { generation, frame };
        let slot = self.entries.get(&key).copied();
        let previous = slot.map(|i| self.slots[i as usize].usage);
        if previous == Some(usage) { return true; }
        if self.checkpoint_frame == frame { self.changes.push(Change::Put(key, previous)); }
        if let Some(index) = slot { self.slots[index as usize].usage = usage; }
        else { self.insert(key, usage); }
        true
    }
    fn insert(&mut self, key: i64, usage: Usage) {
        let entry = Entry { key, usage, previous: self.tail, next: NONE };
        let index = if let Some(index) = self.free.pop() { self.slots[index as usize] = entry; index }
            else { let index = u32::try_from(self.slots.len()).expect("mesh slots exhausted"); self.slots.push(entry); index };
        if self.tail != NONE { self.slots[self.tail as usize].next = index; }
        else { self.head = index; }
        self.tail = index;
        self.entries.insert(key, index);
    }
    fn remove(&mut self, key: i64) {
        if let Some(index) = self.entries.remove(&key) {
            let entry = self.slots[index as usize];
            if entry.previous != NONE { self.slots[entry.previous as usize].next = entry.next; }
            else { self.head = entry.next; }
            if entry.next != NONE { self.slots[entry.next as usize].previous = entry.previous; }
            else { self.tail = entry.previous; }
            self.free.push(index);
        }
    }
    fn ordered(&self) -> Vec<(i64, Usage)> {
        let mut result = Vec::with_capacity(self.entries.len());
        let mut at = self.head;
        while at != NONE {
            let entry = self.slots[at as usize];
            result.push((entry.key, entry.usage)); at = entry.next;
        }
        result
    }
    fn snapshot(&mut self) { self.changes.push(Change::Snapshot(self.ordered())); }
    fn forget(&mut self, key: i64) {
        if self.checkpoint_frame != 0 && self.entries.contains_key(&key) { self.snapshot(); }
        self.remove(key);
    }
    fn retire(&mut self, frame: i64) -> bool {
        if frame <= 0 { return false; }
        if self.checkpoint_frame != 0 && self.checkpoint_frame != frame { self.discard(); }
        // Keep the original snapshot boundary even for an empty retirement.
        // Observations bearing another frame are deliberately not journaled;
        // a snapshot must also restore those entries exactly on rollback.
        if self.checkpoint_frame == frame { self.snapshot(); }
        self.retired.clear();
        let minimum = (frame - 1).max(1);
        let mut at = self.head;
        while at != NONE {
            let entry = self.slots[at as usize]; at = entry.next;
            if entry.usage.frame < minimum { self.retired.push(Retirement { key: entry.key, generation: entry.usage.generation }); }
        }
        for i in 0..self.retired.len() { self.remove(self.retired[i].key); }
        true
    }
    fn rollback(&mut self, frame: i64, count: usize) -> bool {
        if frame != self.checkpoint_frame || count > self.changes.len() { return false; }
        while self.changes.len() > count {
            match self.changes.pop().unwrap() {
                Change::Put(key, None) => self.remove(key),
                Change::Put(key, Some(usage)) => self.slots[self.entries[&key] as usize].usage = usage,
                Change::Snapshot(entries) => {
                    self.entries.clear(); self.slots.clear(); self.free.clear();
                    self.head = NONE; self.tail = NONE;
                    for (key, usage) in entries { self.insert(key, usage); }
                }
            }
        }
        true
    }
}

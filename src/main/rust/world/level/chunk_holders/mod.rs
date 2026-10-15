//! Native-owned `ChunkMap` holder registry and unload/eager-save state: which
//! chunks have a holder (updating and visible maps, in their exact
//! `Long2ObjectLinkedOpenHashMap` insertion order), holder scheduling, the
//! drop set (exact `LongOpenHashSet` order, emptied through its iterator), the
//! pending-unload map, the eager-save set and the save throttle. Java keeps
//! the `ChunkHolder` objects by id, their futures, save IO and the unload
//! queue's runnables, plus a visible-map projection rebuilt at each promote.
//! Every call holds the registry lock only while it runs; Java code (holder
//! construction, saves, futures) runs between calls.
pub(crate) mod ffi;
#[cfg(test)]
mod tests;

use std::collections::HashMap;

use super::chunk_distance::ticket_storage::map::{Cursor, ListMap};
use super::chunk_distance::ticket_storage::Fault;

/// An insertion-ordered map, as fastutil's linked hash maps iterate: `put` of a
/// present key keeps its place, `remove` unlinks, iteration follows insertion.
#[derive(Clone, Default)]
pub(crate) struct Ordered {
    index: HashMap<i64, usize>,
    /// (key, value, previous, next); freed nodes are `None`.
    nodes: Vec<Option<(i64, u32, usize, usize)>>,
    free: Vec<usize>,
    head: usize,
    tail: usize,
}

const END: usize = usize::MAX;

impl Ordered {
    pub(crate) fn new() -> Self {
        Self { index: HashMap::new(), nodes: Vec::new(), free: Vec::new(), head: END, tail: END }
    }

    pub(crate) fn len(&self) -> usize {
        self.index.len()
    }

    pub(crate) fn get(&self, key: i64) -> Option<u32> {
        self.index.get(&key).map(|&n| self.nodes[n].unwrap().1)
    }

    /// `put`: replaces in place, or appends.
    pub(crate) fn put(&mut self, key: i64, value: u32) -> Result<(), Fault> {
        if let Some(&n) = self.index.get(&key) {
            self.nodes[n].as_mut().unwrap().1 = value;
            return Ok(());
        }
        self.index.try_reserve(1).map_err(|_| Fault::Allocation)?;
        let node = Some((key, value, self.tail, END));
        let n = match self.free.pop() {
            Some(n) => {
                self.nodes[n] = node;
                n
            }
            None => {
                self.nodes.try_reserve(1).map_err(|_| Fault::Allocation)?;
                self.nodes.push(node);
                self.nodes.len() - 1
            }
        };
        if self.tail != END {
            self.nodes[self.tail].as_mut().unwrap().3 = n;
        } else {
            self.head = n;
        }
        self.tail = n;
        self.index.insert(key, n);
        Ok(())
    }

    pub(crate) fn remove(&mut self, key: i64) -> Option<u32> {
        let n = self.index.remove(&key)?;
        let (_, value, previous, next) = self.nodes[n].take().unwrap();
        if previous != END {
            self.nodes[previous].as_mut().unwrap().3 = next;
        } else {
            self.head = next;
        }
        if next != END {
            self.nodes[next].as_mut().unwrap().2 = previous;
        } else {
            self.tail = previous;
        }
        self.free.push(n);
        Some(value)
    }

    pub(crate) fn clear(&mut self) {
        *self = Self::new();
    }

    /// Entries in iteration order.
    pub(crate) fn entries(&self) -> Vec<(i64, u32)> {
        let mut out = Vec::with_capacity(self.len());
        let mut n = self.head;
        while n != END {
            let (key, value, _, next) = self.nodes[n].unwrap();
            out.push((key, value));
            n = next;
        }
        out
    }

    /// The first key, or the key after `key`.
    fn key_after(&self, key: Option<i64>) -> Option<i64> {
        let n = match key {
            None => self.head,
            Some(key) => self.index.get(&key).map_or(END, |&n| self.nodes[n].unwrap().3),
        };
        (n != END).then(|| self.nodes[n].unwrap().0)
    }
}

/// What `updateChunkScheduling` does with a chunk's holder.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Scheduled {
    /// Neither level is loaded: return the holder as given.
    Unchanged,
    /// Set the given holder's level (it stays where it is).
    Level,
    /// Revive the pending-unload holder `id` at this level.
    Revived(u32),
    /// Construct a holder for `id`, then `commit_new`.
    New(u32),
}

pub(crate) struct Registry {
    /// `ChunkLevel.MAX_LEVEL`: levels above it are not loaded.
    max_level: i32,
    pub(super) updating: Ordered,
    /// The last promoted `updating`.
    visible: Ordered,
    pending: Ordered,
    /// `LongOpenHashSet toDrop` (values unused).
    pub(super) to_drop: ListMap,
    eager: Ordered,
    /// The eager-save walk, as fastutil's linked-set iterator: the next key is
    /// fixed when the current one is returned (at creation for the first), so
    /// a key appended after the walk passed the old tail is not visited.
    eager_next: Option<i64>,
    eager_current: Option<i64>,
    next_save: HashMap<i64, i64>,
    pub(super) modified: bool,
    /// Holder ids: allocated on construction, freed when the holder leaves
    /// both maps (an unload completes).
    free_ids: Vec<u32>,
    next_id: u32,
}

impl Registry {
    pub(crate) fn new(max_level: i32) -> Result<Self, Fault> {
        Ok(Self {
            max_level,
            updating: Ordered::new(),
            visible: Ordered::new(),
            pending: Ordered::new(),
            to_drop: ListMap::with_expected(16)?,
            eager: Ordered::new(),
            eager_next: None,
            eager_current: None,
            next_save: HashMap::new(),
            modified: false,
            free_ids: Vec::new(),
            next_id: 0,
        })
    }

    fn loaded(&self, level: i32) -> bool {
        level <= self.max_level
    }

    fn allocate_id(&mut self) -> Result<u32, Fault> {
        if let Some(id) = self.free_ids.pop() {
            return Ok(id);
        }
        self.free_ids.try_reserve(self.next_id as usize + 1).map_err(|_| Fault::Allocation)?;
        self.next_id += 1;
        Ok(self.next_id - 1)
    }

    /// `ChunkMap.updateChunkScheduling(l, i, holder, j)` for the structure:
    /// `has_holder` is whether a holder was passed.
    pub(crate) fn schedule(&mut self, key: i64, level: i32, has_holder: bool, old_level: i32) -> Result<Scheduled, Fault> {
        if !self.loaded(old_level) && !self.loaded(level) {
            return Ok(Scheduled::Unchanged);
        }
        if has_holder {
            if !self.loaded(level) {
                self.to_drop.compute_if_absent(key, || Ok(0))?;
            } else {
                self.to_drop.remove(key)?;
            }
            return Ok(Scheduled::Level);
        }
        if !self.loaded(level) {
            return Ok(Scheduled::Unchanged);
        }
        if let Some(id) = self.pending.remove(key) {
            self.updating.put(key, id)?;
            self.modified = true;
            return Ok(Scheduled::Revived(id));
        }
        Ok(Scheduled::New(self.allocate_id()?))
    }

    /// The constructed holder `id` joins the updating map.
    pub(crate) fn commit_new(&mut self, key: i64, id: u32) -> Result<(), Fault> {
        self.updating.put(key, id)?;
        self.modified = true;
        Ok(())
    }

    /// Holder construction threw: release the reserved id.
    pub(crate) fn abort_new(&mut self, id: u32) {
        self.free_ids.push(id);
    }

    pub(crate) fn updating(&self, key: i64) -> Option<u32> {
        self.updating.get(key)
    }

    pub(crate) fn is_dropping(&self, key: i64) -> bool {
        self.to_drop.get(key) != super::chunk_distance::ticket_storage::map::NONE
    }

    /// `promoteChunkMap`: the new visible entries, or `None` when unmodified.
    pub(crate) fn promote(&mut self) -> Option<Vec<(i64, u32)>> {
        if !self.modified {
            return None;
        }
        self.visible = self.updating.clone();
        self.modified = false;
        Some(self.visible.entries())
    }

    pub(crate) fn visible(&self) -> Vec<(i64, u32)> {
        self.visible.entries()
    }

    pub(crate) fn updating_entries(&self) -> Vec<(i64, u32)> {
        self.updating.entries()
    }

    /// `processUnloads`' drop loop: empties the drop set through its iterator;
    /// each dropped chunk with an updating holder moves to the pending
    /// unloads. Returns those `(key, id)` for `scheduleUnload`, in order.
    pub(crate) fn drop_pass(&mut self) -> Result<Vec<(i64, u32)>, Fault> {
        let mut out = Vec::new();
        let mut cursor: Cursor = self.to_drop.cursor();
        while cursor.has_next() {
            let index = cursor.next_entry(&self.to_drop)?;
            let key = self.to_drop.key_at(index)?;
            if let Some(id) = self.updating.remove(key) {
                self.pending.put(key, id)?;
                self.modified = true;
                out.try_reserve(1).map_err(|_| Fault::Allocation)?;
                out.push((key, id));
            }
            cursor.remove(&mut self.to_drop)?;
        }
        Ok(out)
    }

    /// `pendingUnloads.remove(key, holder)`; frees the id when it was there.
    pub(crate) fn finish_unload(&mut self, key: i64, id: u32) -> bool {
        if self.pending.get(key) != Some(id) {
            return false;
        }
        self.pending.remove(key);
        self.free_ids.push(id);
        true
    }

    pub(crate) fn mark_unsaved(&mut self, key: i64) -> Result<(), Fault> {
        self.eager.put(key, 0)
    }

    /// Starts a walk over the eager-save set (`iterator()`).
    pub(crate) fn eager_start(&mut self) {
        self.eager_next = self.eager.key_after(None);
        self.eager_current = None;
    }

    /// `hasNext() ? nextLong()`: the walk's next key, or `None` at the end.
    pub(crate) fn eager_next(&mut self) -> Option<i64> {
        let key = self.eager_next.filter(|&key| self.eager.get(key).is_some())?;
        self.eager_next = self.eager.key_after(Some(key));
        self.eager_current = Some(key);
        Some(key)
    }

    /// `iterator.remove()` of the key last returned.
    pub(crate) fn eager_remove_current(&mut self) -> Result<(), Fault> {
        let key = self.eager_current.take().ok_or(Fault::IllegalState)?;
        self.eager.remove(key);
        Ok(())
    }

    pub(crate) fn next_save(&self, key: i64) -> i64 {
        self.next_save.get(&key).copied().unwrap_or(-1)
    }

    pub(crate) fn set_next_save(&mut self, key: i64, time: i64) -> Result<(), Fault> {
        self.next_save.try_reserve(1).map_err(|_| Fault::Allocation)?;
        self.next_save.insert(key, time);
        Ok(())
    }

    pub(crate) fn forget_next_save(&mut self, key: i64) {
        self.next_save.remove(&key);
    }

    pub(crate) fn clear_next_save(&mut self) {
        self.next_save.clear();
    }

    /// Bit 0 pending unloads, 1 updating holders, 2 chunks to drop.
    pub(crate) fn work(&self) -> i32 {
        i32::from(self.pending.len() != 0) | i32::from(self.updating.len() != 0) << 1 | i32::from(self.to_drop.len() != 0) << 2
    }
}

//! Native-owned `ChunkMap` holder registry and unload/eager-save state: which
//! chunks have a holder (updating and visible maps, in their exact
//! `Long2ObjectLinkedOpenHashMap` insertion order), holder scheduling, the
//! drop set (exact `LongOpenHashSet` order, emptied through its iterator), the
//! pending-unload map, the eager-save set and the save throttle. Java keeps
//! the `ChunkHolder` objects by id, their futures, save IO and the unload
//! queue's runnables, plus a visible-map projection rebuilt at each promote.
//!
//! It also owns each holder's level state (`ChunkHolder`'s ticket level, the
//! level its futures last reflected, and accessibility since the last save),
//! applies the loading graph's level changes to holders without a Java replay,
//! and decides each holder's status transition: which futures to prepare or
//! unload and whether to demote. Java executes those effects.
//!
//! Every call holds the registry lock only while it runs; Java code (holder
//! construction, saves, futures, listeners) runs between calls.
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

/// One holder's level state, as `ChunkHolder` fields.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Levels {
    /// `ticketLevel`.
    pub(crate) level: i32,
    /// `oldTicketLevel`: the level the futures last reflected.
    pub(crate) old_level: i32,
    /// `wasAccessibleSinceLastSave`.
    pub(crate) accessible: bool,
}

/// Status-transition effects, in `updateFutures`' order of execution.
pub(crate) const PREPARE_FULL: i64 = 1;
pub(crate) const UNLOAD_FULL: i64 = 1 << 1;
pub(crate) const PREPARE_TICKING: i64 = 1 << 2;
pub(crate) const UNLOAD_TICKING: i64 = 1 << 3;
pub(crate) const PREPARE_ENTITY: i64 = 1 << 4;
pub(crate) const UNLOAD_ENTITY: i64 = 1 << 5;
/// Bits 8..: `1 +` the `FullChunkStatus` ordinal to demote to, or 0.
pub(crate) const DEMOTE_SHIFT: u32 = 8;

/// `ChunkLevel.fullStatus(level).ordinal()`, 0 inaccessible .. 3 entity
/// ticking, from the FULL, BLOCK_TICKING and ENTITY_TICKING levels.
pub(crate) fn full_status(status_levels: [i32; 3], level: i32) -> i64 {
    let [full, block, entity] = status_levels;
    if level <= entity {
        3
    } else if level <= block {
        2
    } else {
        i64::from(level <= full)
    }
}

/// `updateFutures`' effects for a holder moving between two levels.
pub(crate) fn transition(status_levels: [i32; 3], old_level: i32, level: i32) -> i64 {
    let (from, to) = (full_status(status_levels, old_level), full_status(status_levels, level));
    let mut effects = 0;
    for (status, prepare, unload) in [(1, PREPARE_FULL, UNLOAD_FULL), (2, PREPARE_TICKING, UNLOAD_TICKING), (3, PREPARE_ENTITY, UNLOAD_ENTITY)] {
        if from < status && to >= status {
            effects |= prepare;
        }
        if from >= status && to < status {
            effects |= unload;
        }
    }
    if to < from {
        effects |= (to + 1) << DEMOTE_SHIFT;
    }
    effects
}

/// A holder level change for Java to apply, in the graph's order: the chunk,
/// the holder id and what to do.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct Applied(pub(crate) i64, pub(crate) u32, pub(crate) Apply);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) enum Apply {
    /// The holder's level is unchanged; it still joins `chunksToUpdateFutures`.
    Update,
    /// Mirror the holder's new level, then it joins `chunksToUpdateFutures`.
    Level(i32),
    /// Construct the holder at the level, `commit_new`, then it joins. Nothing
    /// after it was applied; if construction throws, `discard_level_changes`.
    New(i32),
}

pub(crate) struct Registry {
    /// `ChunkLevel.MAX_LEVEL`: levels above it are not loaded.
    max_level: i32,
    /// `ChunkLevel.byStatus` of FULL, BLOCK_TICKING and ENTITY_TICKING.
    status_levels: [i32; 3],
    /// Level state by holder id.
    levels: Vec<Levels>,
    /// The loading graph's drained `(chunk, level)` changes not yet applied.
    level_changes: Vec<(i64, i32)>,
    next_change: usize,
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
            status_levels: [33, 32, 31],
            levels: Vec::new(),
            level_changes: Vec::new(),
            next_change: 0,
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

    /// The level a chunk without a holder reports: `MAX_LEVEL + 1`.
    fn unloaded(&self) -> i32 {
        self.max_level + 1
    }

    pub(crate) fn set_status_levels(&mut self, full: i32, block_ticking: i32, entity_ticking: i32) {
        self.status_levels = [full, block_ticking, entity_ticking];
    }

    fn full_status(&self, level: i32) -> i64 {
        full_status(self.status_levels, level)
    }

    /// The allocated id gets a new holder's state (as the constructor sets it).
    fn allocate_id(&mut self, level: i32) -> Result<u32, Fault> {
        let id = match self.free_ids.pop() {
            Some(id) => id,
            None => {
                self.free_ids.try_reserve(self.next_id as usize + 1).map_err(|_| Fault::Allocation)?;
                self.levels.try_reserve(1).map_err(|_| Fault::Allocation)?;
                self.levels.push(Levels { level: 0, old_level: 0, accessible: false });
                self.next_id += 1;
                self.next_id - 1
            }
        };
        self.levels[id as usize] = Levels { level, old_level: self.unloaded(), accessible: false };
        Ok(id)
    }

    pub(crate) fn levels(&self, id: u32) -> Result<Levels, Fault> {
        self.levels.get(id as usize).copied().ok_or(Fault::IllegalState)
    }

    fn levels_mut(&mut self, id: u32) -> Result<&mut Levels, Fault> {
        self.levels.get_mut(id as usize).ok_or(Fault::IllegalState)
    }

    /// `setTicketLevel`.
    pub(crate) fn set_level(&mut self, id: u32, level: i32) -> Result<(), Fault> {
        self.levels_mut(id)?.level = level;
        Ok(())
    }

    /// `updateFutures`' decision from the old and current levels, before any
    /// effect runs: marks the holder accessible when it now is, and returns the
    /// effects. `old_level` stays until `commit_transition`, after the effects,
    /// so a throwing effect leaves the transition to be decided again.
    pub(crate) fn begin_transition(&mut self, id: u32) -> Result<i64, Fault> {
        let state = self.levels(id)?;
        let effects = self.transition(state.old_level, state.level);
        if self.full_status(state.level) >= 1 {
            self.levels_mut(id)?.accessible = true;
        }
        Ok(effects)
    }

    fn transition(&self, old_level: i32, level: i32) -> i64 {
        transition(self.status_levels, old_level, level)
    }

    /// `oldTicketLevel = ticketLevel`, after the effects and the listener.
    pub(crate) fn commit_transition(&mut self, id: u32) -> Result<(), Fault> {
        let state = self.levels_mut(id)?;
        state.old_level = state.level;
        Ok(())
    }

    /// `refreshAccessibility`.
    pub(crate) fn refresh_accessibility(&mut self, id: u32) -> Result<(), Fault> {
        let full = self.full_status(self.levels(id)?.level) >= 1;
        self.levels_mut(id)?.accessible = full;
        Ok(())
    }

    /// Takes the loading graph's drained changes; any not applied are dropped,
    /// as a new run's drain replaces the old.
    pub(crate) fn take_level_changes(&mut self, changes: &[(i64, i32)]) -> Result<(), Fault> {
        self.level_changes.clear();
        self.next_change = 0;
        self.level_changes.try_reserve(changes.len()).map_err(|_| Fault::Allocation)?;
        self.level_changes.extend_from_slice(changes);
        Ok(())
    }

    /// Holder construction threw: the rest of the drained changes are lost, as
    /// the exception ends the original's replay.
    pub(crate) fn discard_level_changes(&mut self) {
        self.level_changes.clear();
        self.next_change = 0;
    }

    /// `LoadingChunkTracker.setLevel` for the next changes, in order: at most
    /// `limit` results, stopping after a `New`. Empty when all are applied.
    pub(crate) fn apply_level_changes(&mut self, limit: usize, out: &mut Vec<Applied>) -> Result<(), Fault> {
        while out.len() < limit && self.next_change < self.level_changes.len() {
            let (key, level) = self.level_changes[self.next_change];
            self.next_change += 1;
            let holder = self.updating.get(key);
            let old_level = match holder {
                Some(id) => self.levels(id)?.level,
                None => self.unloaded(),
            };
            if old_level == level {
                continue;
            }
            out.try_reserve(1).map_err(|_| Fault::Allocation)?;
            match self.schedule(key, level, holder.is_some(), old_level)? {
                Scheduled::Unchanged => {
                    if let Some(id) = holder {
                        out.push(Applied(key, id, Apply::Update));
                    }
                }
                Scheduled::Level => out.push(Applied(key, holder.ok_or(Fault::IllegalState)?, Apply::Level(level))),
                Scheduled::Revived(id) => out.push(Applied(key, id, Apply::Level(level))),
                Scheduled::New(id) => {
                    out.push(Applied(key, id, Apply::New(level)));
                    return Ok(());
                }
            }
        }
        Ok(())
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
            // The holder passed is the updating one (the tracker's `getChunk`).
            if let Some(id) = self.updating.get(key) {
                self.set_level(id, level)?;
            }
            return Ok(Scheduled::Level);
        }
        if !self.loaded(level) {
            return Ok(Scheduled::Unchanged);
        }
        if let Some(id) = self.pending.remove(key) {
            self.updating.put(key, id)?;
            self.modified = true;
            self.set_level(id, level)?;
            return Ok(Scheduled::Revived(id));
        }
        Ok(Scheduled::New(self.allocate_id(level)?))
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

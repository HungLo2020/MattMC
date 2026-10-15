//! Native-owned `TicketStorage`: active and deactivated ticket lists in the
//! original fastutil table order, expiry, deactivation, the persisted-ticket
//! filter, forced-chunk membership and listener notification. Java keeps the
//! `Ticket` objects (bound to shared `ticksLeft` cells, see `slab`) and the
//! published forced set, and answers the walk's chunk-readiness questions.
//!
//! Every operation runs to completion natively unless it must call Java: a
//! Java listener, a Java predicate or `isReadyForSaving`. It then returns an
//! event; Java answers and resumes it. Iteration state is plain indices, as the
//! Java iterators were, so a callback may mutate the storage between steps.
//! Production trackers are notified directly under their graph lock.
mod ffi;
mod map;
pub(crate) mod slab;
#[cfg(test)]
mod tests;

use std::sync::Arc;

use super::graph::Error;
use super::ticket::SharedTicketDistance;
use map::{Cursor, ListMap, Mark, NONE};

#[derive(Debug, PartialEq, Eq, Clone, Copy)]
pub(crate) enum Fault {
    Allocation,
    Queue,
    NullPointer,
    ArrayIndex,
    IndexOutOfBounds,
    NoSuchElement,
    IllegalState,
}

impl From<Error> for Fault {
    fn from(error: Error) -> Self {
        match error {
            Error::Allocation => Fault::Allocation,
            Error::Queue => Fault::Queue,
        }
    }
}

pub(crate) const PERSIST: u32 = 1;
pub(crate) const LOADING: u32 = 2;
pub(crate) const SIMULATION: u32 = 4;
pub(crate) const KEEP_DIMENSION_ACTIVE: u32 = 8;
pub(crate) const CAN_EXPIRE_IF_UNLOADED: u32 = 16;
/// `type.equals(TicketType.FORCED)` (record equality).
pub(crate) const FORCED: u32 = 32;
/// `type == TicketType.UNKNOWN`.
pub(crate) const UNKNOWN: u32 = 64;

/// One stored `Ticket`: its type identity and properties and its bound cell.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Entry {
    pub slot: u32,
    pub type_id: u32,
    pub level: i32,
    pub flags: u32,
    pub timeout: i64,
}

impl Entry {
    fn kind(&self, simulate: bool) -> bool {
        self.flags & if simulate { SIMULATION } else { LOADING } != 0
    }
}

/// A Java `ObjectArrayList` identity: index plus generation. A list leaves
/// its map only when empty or cleared, so a stale reference reads as empty.
pub(crate) type ListRef = u64;

struct List {
    generation: u32,
    entries: Vec<Entry>,
}

pub(crate) const LOADING_LISTENER: usize = 0;
pub(crate) const SIMULATION_LISTENER: usize = 1;

enum Listener {
    None,
    Java,
    Native(Arc<SharedTicketDistance>),
}

/// Outcome of a notification sequence: how far native delivery got.
pub(crate) struct Delivered {
    /// Index of the first notification Java must deliver; the sequence length when done.
    pub resume: usize,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub(crate) enum WalkKind {
    /// `purgeStaleTickets`.
    Purge,
    /// `deactivateTicketsOnClosing`.
    Deactivate,
    /// `removeTicketIf` with a Java predicate.
    Java,
    /// `activateAllDeactivatedTickets`' loops over the live deactivated map;
    /// Java re-adds each ticket, then clears the map.
    Activate,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Phase {
    NextEntry,
    Tickets,
    /// Awaiting Java's answer for `pending`.
    Answer,
    /// Awaiting Java's predicate results over `batch`.
    Batch,
    NotifyLoading,
    NotifySimulation,
    AfterNotify,
}

pub(crate) struct Walk {
    kind: WalkKind,
    cursor: Cursor,
    phase: Phase,
    index: i32,
    key: i64,
    list: ListRef,
    position: usize,
    last: i32,
    pending: Option<Entry>,
    loading: bool,
    simulating: bool,
    forced: bool,
    dirty: bool,
    /// The last event was a loading notification with a paired simulation one.
    paired: bool,
    /// Java-predicate walks: upcoming tickets as (entry in `starts`, list
    /// position, ticket). The walk state just after that ticket's `next()` is
    /// its entry's start with `last = position, position + 1`.
    batch: Vec<(u32, u32, Entry)>,
    /// Each batched entry: the current one first. Filling never removes, so
    /// the cursor's wrapped-key list is the same for all of them.
    starts: Vec<Start>,
}

/// A walk's entry state, without the cursor's wrapped-key list.
#[derive(Clone, Copy)]
struct Start {
    mark: Mark,
    index: i32,
    key: i64,
    list: ListRef,
    loading: bool,
    simulating: bool,
}

impl Walk {
    fn start(&self) -> Start {
        Start {
            mark: self.cursor.mark(),
            index: self.index,
            key: self.key,
            list: self.list,
            loading: self.loading,
            simulating: self.simulating,
        }
    }

    /// Continues from just after batched ticket `index`.
    fn restore(&mut self, index: usize) -> Entry {
        let (start, position, entry) = self.batch[index];
        let start = self.starts[start as usize];
        self.cursor.reset(start.mark);
        self.index = start.index;
        self.key = start.key;
        self.list = start.list;
        self.loading = start.loading;
        self.simulating = start.simulating;
        self.last = position as i32;
        self.position = position as usize + 1;
        entry
    }

    /// Batched ticket `index`'s entry slot, for its positional `getValue()`.
    pub(crate) fn batched_index(&self, index: usize) -> Option<i32> {
        self.batch.get(index).map(|&(start, _, _)| self.starts[start as usize].index)
    }

    /// The batched `(key, cell)` pairs, in order.
    pub(crate) fn batched(&self) -> impl Iterator<Item = (i64, u32)> + '_ {
        self.batch.iter().map(|&(start, _, entry)| (self.starts[start as usize].key, entry.slot))
    }
}

/// What a walk step needs from Java, or its end.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Event {
    /// Finished; whether a forced ticket was removed.
    Done { forced: bool },
    /// `chunkMap.getUpdatingChunkIfPresent(key)` is null or ready for saving?
    Ready { key: i64 },
    /// The Java predicate for the next `count` tickets of `Walk::batch`, in
    /// order, up to the first that matches or mutates the storage.
    Batch { count: usize },
    /// Call the Java listener. `then` is the simulation notification due right
    /// after a loading one, with its level read now; Java delivers it too
    /// unless the first call changed the lists.
    Notify { listener: usize, key: i64, level: i32, then: Option<i32> },
    /// `addTicket(entry.getLongKey(), ticket)` for a deactivated ticket.
    Activate { key: i64, slot: u32 },
}

/// Every list object, live or free for reuse.
struct Arena {
    lists: Vec<List>,
    free: Vec<u32>,
}

impl Arena {
    fn create(&mut self) -> Result<u32, Fault> {
        if let Some(index) = self.free.pop() {
            return Ok(index);
        }
        self.lists.try_reserve(1).map_err(|_| Fault::Allocation)?;
        // Room to free every list later without allocating.
        self.free.try_reserve(self.lists.len() + 1).map_err(|_| Fault::Allocation)?;
        self.lists.push(List { generation: 0, entries: Vec::new() });
        Ok(self.lists.len() as u32 - 1)
    }
}

pub(crate) struct TicketStorage {
    tickets: ListMap,
    deactivated: ListMap,
    arena: Arena,
    listeners: [Listener; 2],
    /// `ChunkLevel.MAX_LEVEL + 1`: the level without a ticket of a kind.
    absent: i32,
}

impl TicketStorage {
    /// `new TicketStorage()`; the codec's storage starts with an empty default
    /// deactivated map too.
    pub fn new(absent: i32) -> Result<Self, Fault> {
        Ok(Self {
            tickets: ListMap::with_expected(4)?,
            deactivated: ListMap::with_expected(16)?,
            arena: Arena { lists: Vec::new(), free: Vec::new() },
            listeners: [Listener::None, Listener::None],
            absent,
        })
    }

    pub fn set_listener(&mut self, which: usize, java: bool, graph: Option<Arc<SharedTicketDistance>>) {
        self.listeners[which] = match graph {
            Some(graph) => Listener::Native(graph),
            None if java => Listener::Java,
            None => Listener::None,
        };
    }

    fn reference(&self, index: u32) -> ListRef {
        if index == NONE {
            return u64::MAX;
        }
        (self.arena.lists[index as usize].generation as u64) << 32 | index as u64
    }

    /// Drops a list that left its map, releasing its tickets' cells.
    fn retire(&mut self, index: u32) {
        if index == NONE {
            return;
        }
        let list = &mut self.arena.lists[index as usize];
        for entry in list.entries.drain(..) {
            slab::release(entry.slot);
        }
        list.generation = list.generation.wrapping_add(1);
        // Reserved when the list was created.
        self.arena.free.push(index);
    }

    pub fn entries(&self, list: ListRef) -> &[Entry] {
        let index = list as u32;
        match self.arena.lists.get(index as usize) {
            Some(l) if list != u64::MAX && l.generation == (list >> 32) as u32 => &l.entries,
            _ => &[],
        }
    }

    fn entries_mut(&mut self, list: ListRef) -> Option<&mut Vec<Entry>> {
        let index = list as u32;
        match self.arena.lists.get_mut(index as usize) {
            Some(l) if list != u64::MAX && l.generation == (list >> 32) as u32 => Some(&mut l.entries),
            _ => None,
        }
    }

    pub fn is_live(&self, list: ListRef) -> bool {
        let index = list as u32;
        list != u64::MAX && self.arena.lists.get(index as usize).is_some_and(|l| l.generation == (list >> 32) as u32)
    }

    fn bound(ticket: Entry, bound: bool, ticks_left: i64) -> Result<(Entry, Option<u32>), Fault> {
        if bound {
            slab::retain(ticket.slot);
            Ok((ticket, None))
        } else {
            let slot = slab::bind(ticks_left)?;
            Ok((Entry { slot, ..ticket }, Some(slot)))
        }
    }

    /// `list.add(index, ticket)` on a live list, without listeners, as a
    /// caller mutating the original `ObjectArrayList` did.
    pub fn list_insert(&mut self, list: ListRef, index: i32, ticket: Entry, bound: bool, ticks_left: i64) -> Result<Option<u32>, Fault> {
        let entries = self.entries_mut(list).ok_or(Fault::IllegalState)?;
        if index < 0 || index as usize > entries.len() {
            return Err(Fault::IndexOutOfBounds);
        }
        entries.try_reserve(1).map_err(|_| Fault::Allocation)?;
        let (entry, new) = Self::bound(ticket, bound, ticks_left)?;
        entries.insert(index as usize, entry);
        Ok(new)
    }

    /// `list.set(index, ticket)`: the replaced ticket's cell and the new binding.
    pub fn list_set(&mut self, list: ListRef, index: i32, ticket: Entry, bound: bool, ticks_left: i64) -> Result<(u32, Option<u32>), Fault> {
        let entries = self.entries_mut(list).ok_or(Fault::IllegalState)?;
        if index < 0 || index as usize >= entries.len() {
            return Err(Fault::IndexOutOfBounds);
        }
        let (entry, new) = Self::bound(ticket, bound, ticks_left)?;
        let old = std::mem::replace(&mut entries[index as usize], entry);
        slab::release(old.slot);
        Ok((old.slot, new))
    }

    /// `list.remove(index)`: the removed ticket's cell.
    pub fn list_remove(&mut self, list: ListRef, index: i32) -> Result<u32, Fault> {
        let entries = self.entries_mut(list).ok_or(Fault::IllegalState)?;
        if index < 0 || index as usize >= entries.len() {
            return Err(Fault::IndexOutOfBounds);
        }
        let old = entries.remove(index as usize);
        slab::release(old.slot);
        Ok(old.slot)
    }

    /// `getLowestTicket(list, simulate)`: the first entry of the lowest level.
    pub fn lowest(entries: &[Entry], simulate: bool) -> Option<&Entry> {
        let mut lowest: Option<&Entry> = None;
        for entry in entries {
            if lowest.is_none_or(|l| entry.level < l.level) && entry.kind(simulate) {
                lowest = Some(entry);
            }
        }
        lowest
    }

    pub fn list_level(&self, list: ListRef, simulate: bool) -> i32 {
        Self::lowest(self.entries(list), simulate).map_or(self.absent, |e| e.level)
    }

    pub fn tickets_at(&self, key: i64) -> ListRef {
        self.reference(self.tickets.get(key))
    }

    /// `getTicketLevelAt(key, simulate)`.
    pub fn level_at(&self, key: i64, simulate: bool) -> i32 {
        self.list_level(self.tickets_at(key), simulate)
    }

    /// Delivers one listener's `update(key, level, decrease)`: directly to a
    /// native tracker, which also reads this storage's level now as its
    /// listener did. Returns false for a Java listener, which Java must call.
    pub fn notify(&self, which: usize, key: i64, level: i32, decrease: bool) -> Result<bool, Fault> {
        match &self.listeners[which] {
            Listener::None => Ok(true),
            Listener::Java => Ok(false),
            Listener::Native(graph) => {
                let ticket_level = self.level_at(key, which == SIMULATION_LISTENER);
                graph.lock().update(key, ticket_level, level, decrease)?;
                Ok(true)
            }
        }
    }

    /// Delivers `steps` in order while listeners are native or absent.
    fn deliver(&self, steps: &[(usize, bool, i32, bool)], key: i64) -> Result<Delivered, Fault> {
        for (index, &(which, wanted, level, decrease)) in steps.iter().enumerate() {
            if wanted && !self.notify(which, key, level, decrease)? {
                return Ok(Delivered { resume: index });
            }
        }
        Ok(Delivered { resume: steps.len() })
    }

    /// `addTicket(key, ticket)` through its listener calls. `slot` is the
    /// ticket's bound cell, or none to bind `ticks_left`. Returns whether it
    /// was added, the slot newly bound, the simulation/loading notification
    /// conditions and native delivery progress over [simulation, loading].
    pub fn add(&mut self, key: i64, ticket: Entry, bound: bool, ticks_left: i64) -> Result<(bool, Option<u32>, [bool; 2], Delivered), Fault> {
        let (index, _) = self.tickets.compute_if_absent(key, || self.arena.create())?;
        if index == NONE {
            return Err(Fault::NullPointer);
        }
        let entries = &mut self.arena.lists[index as usize].entries;
        if let Some(existing) = entries.iter().find(|e| e.type_id == ticket.type_id && e.level == ticket.level) {
            slab::cell(existing.slot).ticks_left.store(existing.timeout, std::sync::atomic::Ordering::Relaxed);
            return Ok((false, None, [false; 2], Delivered { resume: 2 }));
        }
        let simulation = Self::lowest(entries, true).map_or(self.absent, |e| e.level);
        let loading = Self::lowest(entries, false).map_or(self.absent, |e| e.level);
        entries.try_reserve(1).map_err(|_| Fault::Allocation)?;
        let (slot, new) = if bound {
            slab::retain(ticket.slot);
            (ticket.slot, None)
        } else {
            let slot = slab::bind(ticks_left)?;
            (slot, Some(slot))
        };
        entries.push(Entry { slot, ..ticket });
        let wanted = [
            ticket.flags & SIMULATION != 0 && ticket.level < simulation,
            ticket.flags & LOADING != 0 && ticket.level < loading,
        ];
        let delivered = self.deliver(
            &[
                (SIMULATION_LISTENER, wanted[0], ticket.level, true),
                (LOADING_LISTENER, wanted[1], ticket.level, true),
            ],
            key,
        )?;
        Ok((true, new, wanted, delivered))
    }

    /// `removeTicket(key, ticket)` through its listener calls. Returns the
    /// list it removed from (later notifications read its level then), or none.
    pub fn remove(&mut self, key: i64, type_id: u32, level: i32, flags: u32) -> Result<Option<(ListRef, Delivered)>, Fault> {
        let index = self.tickets.get(key);
        if index == NONE {
            return Ok(None);
        }
        let list = self.reference(index);
        let entries = &mut self.arena.lists[index as usize].entries;
        let Some(position) = entries.iter().position(|e| e.type_id == type_id && e.level == level) else {
            return Ok(None);
        };
        let removed = entries.remove(position);
        slab::release(removed.slot);
        if entries.is_empty() {
            let index = self.tickets.remove(key)?;
            self.retire(index);
        }
        let mut delivered = Delivered { resume: 0 };
        for (step, which, simulate) in [(0, SIMULATION_LISTENER, true), (1, LOADING_LISTENER, false)] {
            let wanted = flags & if simulate { SIMULATION } else { LOADING } != 0;
            if wanted && !self.notify(which, key, self.list_level(list, simulate), false)? {
                return Ok(Some((list, delivered)));
            }
            delivered.resume = step + 1;
        }
        Ok(Some((list, delivered)))
    }

    /// Lists' keys in `getAllChunksWithTicketThat(FORCED)` order.
    pub fn forced_chunks(&self) -> Vec<i64> {
        self.tickets
            .ordered()
            .into_iter()
            .filter(|&(_, index)| self.entries(self.reference(index)).iter().any(|e| e.flags & FORCED != 0))
            .map(|(key, _)| key)
            .collect()
    }

    pub fn has_tickets(&self) -> bool {
        self.tickets.len() != 0
    }

    pub fn keeps_dimension_active(&self) -> bool {
        self.tickets
            .ordered()
            .into_iter()
            .any(|(_, index)| self.entries(self.reference(index)).iter().any(|e| e.flags & KEEP_DIMENSION_ACTIVE != 0))
    }

    pub fn active_keys(&self) -> Vec<i64> {
        self.tickets.ordered().into_iter().map(|(key, _)| key).collect()
    }

    /// `(key, slot)` for every ticket of `type_id`, in entry-set order.
    pub fn of_type(&self, type_id: u32) -> Vec<(i64, u32)> {
        let mut out = Vec::new();
        for (key, index) in self.tickets.ordered() {
            for entry in self.entries(self.reference(index)) {
                if entry.type_id == type_id {
                    out.push((key, entry.slot));
                }
            }
        }
        out
    }

    /// `forEachTicket` over active then deactivated tickets: persisted ones only,
    /// or every deactivated ticket for reactivation.
    pub fn packed(&self, deactivated_only: bool) -> Vec<(i64, u32)> {
        let mut out = Vec::new();
        let maps: &[&ListMap] = if deactivated_only { &[&self.deactivated] } else { &[&self.tickets, &self.deactivated] };
        for map in maps {
            for (key, index) in map.ordered() {
                for entry in self.entries(self.reference(index)) {
                    if deactivated_only || entry.flags & PERSIST != 0 {
                        out.push((key, entry.slot));
                    }
                }
            }
        }
        out
    }

    /// `fromPacked`: appends a decoded ticket to the deactivated lists.
    pub fn add_deactivated(&mut self, key: i64, ticket: Entry, bound: bool, ticks_left: i64) -> Result<Option<u32>, Fault> {
        let (index, _) = self.deactivated.compute_if_absent(key, || self.arena.create())?;
        if index == NONE {
            return Err(Fault::NullPointer);
        }
        self.arena.lists[index as usize].entries.try_reserve(1).map_err(|_| Fault::Allocation)?;
        let (slot, new) = if bound {
            slab::retain(ticket.slot);
            (ticket.slot, None)
        } else {
            let slot = slab::bind(ticks_left)?;
            (slot, Some(slot))
        };
        self.arena.lists[index as usize].entries.push(Entry { slot, ..ticket });
        Ok(new)
    }

    /// `deactivatedTickets.clear()`.
    pub fn clear_deactivated(&mut self) {
        for index in self.deactivated.clear() {
            self.retire(index);
        }
    }

    pub fn begin(&self, kind: WalkKind) -> Walk {
        Walk {
            kind,
            cursor: if kind == WalkKind::Activate { self.deactivated.cursor() } else { self.tickets.cursor() },
            phase: Phase::NextEntry,
            index: 0,
            key: 0,
            list: u64::MAX,
            position: 0,
            last: -1,
            pending: None,
            loading: false,
            simulating: false,
            forced: false,
            dirty: false,
            paired: false,
            batch: Vec::new(),
            starts: Vec::new(),
        }
    }

    /// Up to `capacity` upcoming tickets of a Java-predicate walk, with the
    /// state after each `next()`. Between tickets whose predicate is false the
    /// original has no side effects, also across entries with nothing removed,
    /// so these are exactly the tickets and states it would reach. Stops at the
    /// end of an entry with removals (notifications follow) or at any fault,
    /// which the live walk then raises itself.
    fn fill_batch(&self, walk: &mut Walk, capacity: usize) -> Result<usize, Fault> {
        walk.batch.clear();
        walk.starts.clear();
        if walk.batch.try_reserve(capacity).is_err() || walk.starts.try_reserve(capacity).is_err() {
            return Err(Fault::Allocation);
        }
        let mut start = walk.start();
        let mut position = walk.position;
        walk.starts.push(start);
        while walk.batch.len() < capacity {
            let entries = self.entries(start.list);
            if position < entries.len() {
                walk.batch.push((walk.starts.len() as u32 - 1, position as u32, entries[position]));
                position += 1;
                continue;
            }
            // Crossing to the next entry is side-effect free only when this one
            // removed nothing. Any fault is dropped here: the live walk raises it
            // in place, or never reaches it if an earlier mutation intervenes.
            if start.loading || start.simulating || !start.mark.has_next() {
                break;
            }
            let Ok(index) = start.mark.next_entry(walk.cursor.wrapped(), &self.tickets) else { break };
            let (Ok(value), Ok(key)) = (self.tickets.value_at(index), self.tickets.key_at(index)) else { break };
            if value == NONE {
                break;
            }
            start.index = index;
            start.list = self.reference(value);
            start.key = key;
            position = 0;
            walk.starts.push(start);
        }
        Ok(walk.batch.len())
    }

    /// Runs `removeTicketIf` until it needs Java. `answer` answers the last
    /// `Ready` event (nonzero), or a `Batch`: -1 when every predicate was false,
    /// else `2k + result` for the k-th ticket, the first that matched or
    /// mutated the storage. Returns the event; `walk_dirty` reports dirtiness.
    pub fn step(&mut self, walk: &mut Walk, answer: i32, capacity: usize) -> Result<Event, Fault> {
        if walk.phase == Phase::Batch {
            if answer < 0 {
                let last = walk.batch.len() - 1;
                walk.restore(last);
                walk.phase = Phase::Tickets;
            } else {
                let entry = walk.restore((answer >> 1) as usize);
                walk.phase = Phase::Tickets;
                if answer & 1 != 0 {
                    self.take(walk, entry)?;
                }
            }
        }
        let answer = answer != 0;
        let paired = std::mem::replace(&mut walk.paired, false);
        let result = self.run(walk, answer, paired, capacity);
        if let Ok(Event::Notify { then: Some(_), .. }) = result {
            walk.paired = true;
        }
        result
    }

    fn run(&mut self, walk: &mut Walk, answer: bool, paired: bool, capacity: usize) -> Result<Event, Fault> {
        walk.paired = paired;
        loop {
            match walk.phase {
                Phase::NextEntry => {
                    if !walk.cursor.has_next() {
                        return Ok(Event::Done { forced: walk.forced });
                    }
                    let map = if walk.kind == WalkKind::Activate { &self.deactivated } else { &self.tickets };
                    walk.index = walk.cursor.next_entry(map)?;
                    // `entry.getValue().iterator()`, then `entry.getLongKey()`.
                    let value = map.value_at(walk.index)?;
                    if value == NONE {
                        return Err(Fault::NullPointer);
                    }
                    walk.list = self.reference(value);
                    if walk.kind != WalkKind::Activate {
                        walk.key = map.key_at(walk.index)?;
                    }
                    walk.position = 0;
                    walk.last = -1;
                    walk.loading = false;
                    walk.simulating = false;
                    walk.phase = Phase::Tickets;
                }
                Phase::Tickets => {
                    if walk.kind == WalkKind::Java && self.fill_batch(walk, capacity)? > 0 {
                        walk.phase = Phase::Batch;
                        return Ok(Event::Batch { count: walk.batch.len() });
                    }
                    let entries = self.entries(walk.list);
                    if walk.position >= entries.len() {
                        walk.phase = if walk.loading || walk.simulating { Phase::NotifyLoading } else { Phase::NextEntry };
                        continue;
                    }
                    let entry = entries[walk.position];
                    walk.last = walk.position as i32;
                    walk.position += 1;
                    let matched = match walk.kind {
                        // The key is read from the entry's slot for every ticket.
                        WalkKind::Activate => return Ok(Event::Activate { key: self.deactivated.key_at(walk.index)?, slot: entry.slot }),
                        WalkKind::Deactivate => entry.flags & UNKNOWN == 0,
                        WalkKind::Java => unreachable!("Java predicates are batched"),
                        WalkKind::Purge => {
                            if entry.timeout == 0 {
                                false
                            } else if entry.flags & CAN_EXPIRE_IF_UNLOADED != 0 {
                                Self::expire(&entry)
                            } else {
                                walk.pending = Some(entry);
                                walk.phase = Phase::Answer;
                                return Ok(Event::Ready { key: walk.key });
                            }
                        }
                    };
                    if matched {
                        self.take(walk, entry)?;
                    }
                }
                Phase::Batch => return Err(Fault::IllegalState),
                Phase::Answer => {
                    let entry = walk.pending.take().ok_or(Fault::IllegalState)?;
                    walk.phase = Phase::Tickets;
                    let matched = match walk.kind {
                        WalkKind::Purge => answer && Self::expire(&entry),
                        _ => answer,
                    };
                    if matched {
                        self.take(walk, entry)?;
                    }
                }
                Phase::NotifyLoading => {
                    walk.phase = Phase::NotifySimulation;
                    if walk.loading {
                        let list = self.reference(self.tickets.value_at(walk.index)?);
                        let level = self.list_level(list, false);
                        if !self.notify(LOADING_LISTENER, walk.key, level, false)? {
                            let then = (walk.simulating && matches!(self.listeners[SIMULATION_LISTENER], Listener::Java))
                                .then(|| self.list_level(list, true));
                            return Ok(Event::Notify { listener: LOADING_LISTENER, key: walk.key, level, then });
                        }
                    }
                }
                Phase::NotifySimulation => {
                    walk.phase = Phase::AfterNotify;
                    // Java delivered it with the loading notification.
                    let delivered = answer && walk.paired;
                    walk.paired = false;
                    if walk.simulating && !delivered {
                        let level = self.list_level(self.reference(self.tickets.value_at(walk.index)?), true);
                        if !self.notify(SIMULATION_LISTENER, walk.key, level, false)? {
                            return Ok(Event::Notify { listener: SIMULATION_LISTENER, key: walk.key, level, then: None });
                        }
                    }
                }
                Phase::AfterNotify => {
                    walk.dirty = true;
                    walk.phase = Phase::NextEntry;
                    let value = self.tickets.value_at(walk.index)?;
                    if value == NONE {
                        return Err(Fault::NullPointer);
                    }
                    if self.entries(self.reference(value)).is_empty() {
                        let removed = walk.cursor.remove(&mut self.tickets)?;
                        self.retire(removed);
                    }
                }
            }
        }
    }

    /// `entry.getValue()` of a walk entry, read now as the original's
    /// destination-list creator read it: a fault where that would throw.
    pub fn entry_value(&self, index: i32) -> Result<(), Fault> {
        match self.tickets.value_at(index)? {
            NONE => Err(Fault::NullPointer),
            _ => Ok(()),
        }
    }

    pub fn walk_dirty(walk: &Walk) -> bool {
        walk.dirty
    }

    /// `decreaseTicksLeft(); return isTimedOut();` for a ticket with a timeout.
    fn expire(entry: &Entry) -> bool {
        let cell = slab::cell(entry.slot);
        let left = cell.ticks_left.load(std::sync::atomic::Ordering::Relaxed) - 1;
        cell.ticks_left.store(left, std::sync::atomic::Ordering::Relaxed);
        left < 0
    }

    /// The matched ticket: copied to the deactivated lists when deactivating
    /// (a Java destination map is Java's), then `iterator.remove()`.
    fn take(&mut self, walk: &mut Walk, entry: Entry) -> Result<(), Fault> {
        if walk.kind == WalkKind::Deactivate {
            let (index, _) = self.deactivated.compute_if_absent(walk.key, || self.arena.create())?;
            if index == NONE {
                return Err(Fault::NullPointer);
            }
            let destination = &mut self.arena.lists[index as usize].entries;
            destination.try_reserve(1).map_err(|_| Fault::Allocation)?;
            slab::retain(entry.slot);
            destination.push(entry);
        }
        if walk.last == -1 {
            return Err(Fault::IllegalState);
        }
        let last = walk.last as usize;
        let Some(entries) = self.entries_mut(walk.list).filter(|e| last < e.len()) else {
            return Err(Fault::IndexOutOfBounds);
        };
        let removed = entries.remove(last);
        slab::release(removed.slot);
        if (last) < walk.position {
            walk.position -= 1;
        }
        walk.last = -1;
        walk.loading |= entry.flags & LOADING != 0;
        walk.simulating |= entry.flags & SIMULATION != 0;
        walk.forced |= entry.flags & FORCED != 0;
        Ok(())
    }
}

impl Drop for TicketStorage {
    fn drop(&mut self) {
        for list in &mut self.arena.lists {
            for entry in list.entries.drain(..) {
                slab::release(entry.slot);
            }
        }
    }
}

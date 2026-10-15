//! Per-chunk scheduled block/fluid tick container (`LevelChunkTicks` core).
//!
//! The queue is an exact port of `java.util.PriorityQueue`'s array heap
//! (`offer`, `poll`, `removeAt`, sift up/down) over entry ids, ordered by
//! `ScheduledTick.DRAIN_ORDER`. Array order is observable in Java through
//! iteration, `getAll` and saved-tick order, so it must not be replaced by a
//! different heap. Java keeps the `ScheduledTick` objects in an id-indexed
//! table, so `peek` returns the same object; this side owns ordering, the
//! (x, y, z, type) dedup set and entry lifetime.
//!
//! Positions keep full `i32` x/y/z: a packed long could alias out-of-range
//! coordinates. Type handles are per-container opaque identities assigned by the
//! Java bridge, not registry ids. Every call enters through [`Container::enter`],
//! an atomic in-use guard: overlapping calls from two threads are rejected
//! instead of aliasing `&mut`. No borrow is held across a return to Java.
use std::cell::UnsafeCell;
use std::sync::Arc;
use std::collections::{HashMap, HashSet};
use std::hash::{BuildHasherDefault, Hasher};

/// Multiply-rotate hasher for small integer keys (dedup keys and container
/// pointers). Not DoS-resistant; keys come from the game, not the network.
#[derive(Default)]
pub struct KeyHasher(u64);
impl Hasher for KeyHasher {
    fn finish(&self) -> u64 { self.0 }
    fn write(&mut self, bytes: &[u8]) { for &b in bytes { self.write_u64(b as u64); } }
    fn write_u32(&mut self, v: u32) { self.write_u64(v as u64); }
    fn write_i32(&mut self, v: i32) { self.write_u64(v as u32 as u64); }
    fn write_usize(&mut self, v: usize) { self.write_u64(v as u64); }
    fn write_u64(&mut self, v: u64) { self.0 = (self.0.rotate_left(5) ^ v).wrapping_mul(0x51_7c_c1_b7_27_22_0a_95); }
}
type FastHash = BuildHasherDefault<KeyHasher>;
use std::sync::atomic::{AtomicBool, AtomicIsize, Ordering::{Acquire, Relaxed, Release}};

pub(crate) mod ffi;
#[cfg(test)]
mod tests;

/// Live containers, for lifetime verification.
pub(crate) static LIVE: AtomicIsize = AtomicIsize::new(0);

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Entry {
    pub trigger_tick: i64,
    /// `TickPriority` ordinal: enum `compareTo` compares ordinals.
    pub priority: i32,
    pub sub_tick_order: i64,
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub type_handle: u32,
}

impl Entry {
    fn key(&self) -> (i32, i32, i32, u32) { (self.x, self.y, self.z, self.type_handle) }
}

/// `ScheduledTick.DRAIN_ORDER`; callers use only the sign, like Java.
fn drain_order(a: &Entry, b: &Entry) -> std::cmp::Ordering {
    a.trigger_tick.cmp(&b.trigger_tick)
        .then(a.priority.cmp(&b.priority))
        .then(a.sub_tick_order.cmp(&b.sub_tick_order))
}

/// FFI-owned wrapper: a queue plus an in-use guard.
pub struct Container {
    busy: AtomicBool,
    queue: UnsafeCell<Queue>,
}

// Access to `queue` is serialized by `busy`.
unsafe impl Sync for Container {}

/// Exclusive access for the duration of one native call.
pub struct Entered<'a> { container: &'a Container }
impl std::ops::Deref for Entered<'_> {
    type Target = Queue;
    fn deref(&self) -> &Queue { unsafe { &*self.container.queue.get() } }
}
impl std::ops::DerefMut for Entered<'_> {
    fn deref_mut(&mut self) -> &mut Queue { unsafe { &mut *self.container.queue.get() } }
}
impl Drop for Entered<'_> {
    fn drop(&mut self) { self.container.busy.store(false, Release); }
}

impl Container {
    pub fn new() -> Self {
        LIVE.fetch_add(1, Relaxed);
        Self { busy: AtomicBool::new(false), queue: UnsafeCell::new(Queue::default()) }
    }
    /// None when another call is in progress (concurrent misuse).
    pub fn enter(&self) -> Option<Entered<'_>> {
        self.busy.compare_exchange(false, true, Acquire, Relaxed).ok().map(|_| Entered { container: self })
    }
}

impl Drop for Container {
    fn drop(&mut self) { LIVE.fetch_sub(1, Relaxed); }
}

#[derive(Default)]
pub struct Queue {
    entries: Vec<Option<Entry>>,
    free: Vec<u32>,
    /// Entry ids in `PriorityQueue` array order.
    queue: Vec<u32>,
    keys: HashSet<(i32, i32, i32, u32), FastHash>,
    /// `PriorityQueue.modCount`, for the Java iterator's fail-fast checks.
    mod_count: u32,
}

impl Queue {
    fn entry(&self, id: u32) -> &Entry { self.entries[id as usize].as_ref().expect("live entry") }
    fn less_or_equal(&self, a: u32, b: u32) -> std::cmp::Ordering { drain_order(self.entry(a), self.entry(b)) }

    fn allocate(&mut self, entry: Entry) -> u32 {
        match self.free.pop() {
            Some(id) => { self.entries[id as usize] = Some(entry); id }
            None => { self.entries.push(Some(entry)); (self.entries.len() - 1) as u32 }
        }
    }
    fn release(&mut self, id: u32) {
        self.entries[id as usize] = None;
        self.free.push(id);
    }

    pub fn size(&self) -> usize { self.queue.len() }
    /// Allocated entry ids (queued entries; freed ids are excluded).
    pub fn live_entries(&self) -> usize { self.entries.len() - self.free.len() }
    pub fn mod_count(&self) -> u32 { self.mod_count }
    pub fn id_at(&self, index: usize) -> Option<u32> { self.queue.get(index).copied() }
    #[cfg(test)]
    pub fn get(&self, id: u32) -> Option<&Entry> { self.entries.get(id as usize).and_then(Option::as_ref) }

    /// `ticksPerPosition.add`: true when the key was absent.
    pub fn add_key(&mut self, x: i32, y: i32, z: i32, type_handle: u32) -> bool { self.keys.insert((x, y, z, type_handle)) }
    pub fn contains_key(&self, x: i32, y: i32, z: i32, type_handle: u32) -> bool { self.keys.contains(&(x, y, z, type_handle)) }

    /// `schedule`: dedup, then `scheduleUnchecked`. None when the key existed.
    pub fn schedule(&mut self, entry: Entry) -> Option<u32> {
        if self.keys.insert(entry.key()) { Some(self.schedule_unchecked(entry)) } else { None }
    }

    /// `PriorityQueue.offer` without touching the dedup set.
    pub fn schedule_unchecked(&mut self, entry: Entry) -> u32 {
        let id = self.allocate(entry);
        self.mod_count = self.mod_count.wrapping_add(1);
        let k = self.queue.len();
        self.queue.push(id);
        self.sift_up(k, id);
        id
    }

    pub fn peek(&self) -> Option<u32> { self.queue.first().copied() }

    /// `LevelChunkTicks.poll`: `PriorityQueue.poll`, then drop the key. The id
    /// is freed; the Java caller clears its object slot before returning.
    pub fn poll(&mut self) -> Option<u32> {
        let result = *self.queue.first()?;
        self.mod_count = self.mod_count.wrapping_add(1);
        let x = self.queue.pop().expect("nonempty");
        if !self.queue.is_empty() { self.sift_down(0, x); }
        let key = self.entry(result).key();
        self.keys.remove(&key);
        self.release(result);
        Some(result)
    }

    /// `PriorityQueue.removeAt(i)` plus the iterator caller's key removal.
    /// Returns (removed id, moved id when the moved element sifted up).
    pub fn remove_at(&mut self, i: usize) -> Option<(u32, Option<u32>)> {
        if i >= self.queue.len() { return None; }
        let removed = self.queue[i];
        self.mod_count = self.mod_count.wrapping_add(1);
        let s = self.queue.len() - 1;
        let mut moved_up = None;
        if s == i {
            self.queue.pop();
        } else {
            let moved = self.queue.pop().expect("nonempty");
            self.sift_down(i, moved);
            if self.queue[i] == moved {
                self.sift_up(i, moved);
                if self.queue[i] != moved { moved_up = Some(moved); }
            }
        }
        let key = self.entry(removed).key();
        self.keys.remove(&key);
        self.release(removed);
        Some((removed, moved_up))
    }

    /// `PriorityQueue.removeEq` (identity scan) plus key removal; false when absent.
    pub fn remove_eq(&mut self, id: u32) -> bool {
        match self.queue.iter().position(|&e| e == id) {
            Some(i) => { self.remove_at(i); true }
            None => false,
        }
    }

    /// `LevelChunkTicks.removeIf(tick -> box.isInside(tick.pos()))` for the known
    /// `LevelTicks.clearArea` box, run through the exact `PriorityQueue.Itr`
    /// sequence: array cursor, `removeAt` (an element moved below the cursor is
    /// revisited from `forgetMeNot`), then `removeEq` for revisited elements. The
    /// box is inclusive on full `i32` bounds. Returns removed ids in removal order.
    pub fn remove_in_box(&mut self, min: [i32; 3], max: [i32; 3]) -> Vec<u32> {
        let inside = |e: &Entry| e.x >= min[0] && e.x <= max[0] && e.y >= min[1] && e.y <= max[1] && e.z >= min[2] && e.z <= max[2];
        let mut removed = Vec::new();
        let mut cursor = 0usize;
        let mut forget_me_not: std::collections::VecDeque<u32> = std::collections::VecDeque::new();
        loop {
            // next(): array elements first, then revisited (moved) elements.
            let (element, last_ret) = if cursor < self.queue.len() {
                cursor += 1;
                (self.queue[cursor - 1], Some(cursor - 1))
            } else if let Some(e) = forget_me_not.pop_front() {
                (e, None)
            } else {
                break;
            };
            if !inside(self.entry(element)) { continue; }
            // remove()
            match last_ret {
                Some(index) => {
                    let (id, moved) = self.remove_at(index).expect("index within queue");
                    removed.push(id);
                    match moved { None => cursor -= 1, Some(m) => forget_me_not.push_back(m) }
                }
                None => {
                    self.remove_eq(element);
                    removed.push(element);
                }
            }
        }
        removed
    }

    /// `siftUpUsingComparator`: stop when `x >= parent`.
    fn sift_up(&mut self, mut k: usize, x: u32) {
        while k > 0 {
            let parent = (k - 1) >> 1;
            let e = self.queue[parent];
            if self.less_or_equal(x, e) != std::cmp::Ordering::Less { break; }
            self.queue[k] = e;
            k = parent;
        }
        self.queue[k] = x;
    }

    /// `siftDownUsingComparator`: take the right child only when strictly
    /// smaller; stop when `x <= child`.
    fn sift_down(&mut self, mut k: usize, x: u32) {
        let n = self.queue.len();
        let half = n >> 1;
        while k < half {
            let mut child = (k << 1) + 1;
            let mut c = self.queue[child];
            let right = child + 1;
            if right < n && self.less_or_equal(c, self.queue[right]) == std::cmp::Ordering::Greater {
                child = right;
                c = self.queue[child];
            }
            if self.less_or_equal(x, c) != std::cmp::Ordering::Greater { break; }
            self.queue[k] = c;
            k = child;
        }
        self.queue[k] = x;
    }
}

/// `updateContainerScheduling(tick)`: `ChunkPos.asLong(tick.pos())` (block to
/// section coordinate by arithmetic shift) and the tick's trigger time.
fn schedule_update(e: &Entry) -> (i64, i64) {
    let key = ((e.x >> 4) as u32 as i64) | (((e.z >> 4) as u32 as i64) << 32);
    (key, e.trigger_tick)
}

/// `ScheduledTick.INTRA_TICK_DRAIN_ORDER` (priority, then subTickOrder), used
/// between containers; distinct from the chunk queue's `DRAIN_ORDER`.
fn intra_order(a: &Entry, b: &Entry) -> std::cmp::Ordering {
    a.priority.cmp(&b.priority).then(a.sub_tick_order.cmp(&b.sub_tick_order))
}

#[derive(Debug, PartialEq, Eq)]
pub enum DrainError {
    /// A queued container had no head, where Java would throw a NullPointerException.
    EmptyContainer,
}

/// Result of [`drain`]. Container references are indices into the caller's
/// container list (`slot_of` maps a list position to its distinct queue).
#[derive(Default, Debug)]
pub struct Drained {
    /// (list index, entry id) in `toRunThisTick` order. Entries are polled.
    pub ticks: Vec<(usize, u32)>,
    /// `nextTickForContainer.put(key, triggerTick)` in `updateContainerScheduling`
    /// order, captured from the head at that moment (a duplicate container may
    /// drain that head later in the same collect).
    pub updates: Vec<(i64, i64)>,
    /// Final `containersToTick` array (list indices), retained until cleanup.
    pub heap: Vec<usize>,
}

/// `LevelTicks.drainContainers` + `rescheduleLeftoverContainers` over a port of
/// the `containersToTick` `PriorityQueue`. `queues[slot_of[i]]` is list entry
/// `i`'s queue. The first `retained` entries are the previous (uncleaned) heap
/// array, kept as-is; the rest are offered in eligibility-scan order.
/// `budget` is `maxAllowedTicks - toRunThisTick.size()`.
pub fn drain(queues: &mut [&mut Queue], slot_of: &[usize], retained: usize, now: i64, budget: i64) -> (Drained, Option<DrainError>) {
    let mut out = Drained::default();
    let mut heap: Vec<usize> = (0..retained).collect();
    let error = drain_into(queues, slot_of, retained, now, budget, &mut out, &mut heap).err();
    out.heap = heap;
    (out, error)
}

/// The drain body. On error the completed prefix stays in `out`/`heap`, like the
/// original: earlier ticks are already queued to run, earlier map puts applied,
/// and the failing container already removed from the outer queue.
fn drain_into(queues: &mut [&mut Queue], slot_of: &[usize], retained: usize, now: i64, budget: i64,
    out: &mut Drained, heap: &mut Vec<usize>) -> Result<(), DrainError> {
    let head = |queues: &[&mut Queue], i: usize| -> Option<Entry> {
        let q = &queues[slot_of[i]];
        q.peek().map(|id| *q.entry(id))
    };
    let cmp = |queues: &[&mut Queue], a: usize, b: usize| -> Result<std::cmp::Ordering, DrainError> {
        match (head(queues, a), head(queues, b)) {
            (Some(x), Some(y)) => Ok(intra_order(&x, &y)),
            _ => Err(DrainError::EmptyContainer),
        }
    };
    // PriorityQueue.offer / poll with the JDK sift rules, over list indices.
    fn offer(heap: &mut Vec<usize>, x: usize, cmp: &dyn Fn(usize, usize) -> Result<std::cmp::Ordering, DrainError>) -> Result<(), DrainError> {
        let mut k = heap.len();
        heap.push(x);
        while k > 0 {
            let parent = (k - 1) >> 1;
            let e = heap[parent];
            if cmp(x, e)? != std::cmp::Ordering::Less { break; }
            heap[k] = e;
            k = parent;
        }
        heap[k] = x;
        Ok(())
    }
    fn poll(heap: &mut Vec<usize>, cmp: &dyn Fn(usize, usize) -> Result<std::cmp::Ordering, DrainError>) -> Result<Option<usize>, DrainError> {
        let Some(&result) = heap.first() else { return Ok(None) };
        let x = heap.pop().expect("nonempty");
        if !heap.is_empty() {
            let n = heap.len();
            let half = n >> 1;
            let mut k = 0;
            while k < half {
                let mut child = (k << 1) + 1;
                let mut c = heap[child];
                let right = child + 1;
                if right < n && cmp(c, heap[right])? == std::cmp::Ordering::Greater { child = right; c = heap[child]; }
                if cmp(x, c)? != std::cmp::Ordering::Greater { break; }
                heap[k] = c;
                k = child;
            }
            heap[k] = x;
        }
        Ok(Some(result))
    }

    for i in retained..slot_of.len() {
        offer(heap, i, &|a, b| cmp(queues, a, b))?;
    }
    let mut taken: i64 = 0;
    while taken < budget {
        let Some(c) = poll(heap, &|a, b| cmp(queues, a, b))? else { break };
        let id = queues[slot_of[c]].poll().ok_or(DrainError::EmptyContainer)?;
        out.ticks.push((c, id));
        taken += 1;
        // drainFromCurrentContainer: the competitor head is read once.
        if taken < budget {
            // `queue.peek()` then its `peek()`: a missing head means no competitor.
            let competitor = heap.first().and_then(|&c2| head(queues, c2));
            while taken < budget {
                let Some(e) = head(queues, c) else { break };
                if e.trigger_tick > now || competitor.is_some_and(|k| intra_order(&e, &k) == std::cmp::Ordering::Greater) { break; }
                let id = queues[slot_of[c]].poll().expect("head exists");
                out.ticks.push((c, id));
                taken += 1;
            }
        }
        if let Some(e) = head(queues, c) {
            if e.trigger_tick <= now && taken < budget {
                offer(heap, c, &|a, b| cmp(queues, a, b))?;
            } else {
                out.updates.push(schedule_update(&e));
            }
        }
    }
    for &c in heap.iter() {
        let e = head(queues, c).ok_or(DrainError::EmptyContainer)?;
        out.updates.push(schedule_update(&e));
    }
    Ok(())
}


/// Per-`LevelTicks` registry of chunk containers plus the persistent
/// `containersToTick` heap (handles). A slot keeps its container alive while it
/// is registered or referenced by a heap entry; only then is its handle freed.
pub struct Registry {
    busy: AtomicBool,
    inner: UnsafeCell<RegistryInner>,
}

// Access to `inner` is serialized by `busy`.
unsafe impl Sync for Registry {}

#[derive(Default)]
pub struct RegistryInner {
    slots: Vec<Slot>,
    free: Vec<u32>,
    /// Handles released since the last collect. They join `free` only when the
    /// next collect starts: by then Java has consumed every output that named
    /// them, so a reused handle can never meet a stale token (no ABA).
    quarantine: Vec<u32>,
    /// `containersToTick` array (handles), retained until a cleaned-up collect.
    heap: Vec<u32>,
}

struct Slot {
    container: Option<Arc<Container>>,
    /// Java-owned report buffer of the container's bridge.
    report: i64,
    registered: bool,
    heap_refs: u32,
}

pub struct RegistryEntered<'a> { registry: &'a Registry }
impl std::ops::Deref for RegistryEntered<'_> {
    type Target = RegistryInner;
    fn deref(&self) -> &RegistryInner { unsafe { &*self.registry.inner.get() } }
}
impl std::ops::DerefMut for RegistryEntered<'_> {
    fn deref_mut(&mut self) -> &mut RegistryInner { unsafe { &mut *self.registry.inner.get() } }
}
impl Drop for RegistryEntered<'_> {
    fn drop(&mut self) { self.registry.busy.store(false, Release); }
}

impl Registry {
    pub fn new() -> Self {
        REGISTRIES.fetch_add(1, Relaxed);
        Self { busy: AtomicBool::new(false), inner: UnsafeCell::new(RegistryInner::default()) }
    }
    pub fn enter(&self) -> Option<RegistryEntered<'_>> {
        self.busy.compare_exchange(false, true, Acquire, Relaxed).ok().map(|_| RegistryEntered { registry: self })
    }
}
impl Drop for Registry {
    fn drop(&mut self) { REGISTRIES.fetch_sub(1, Relaxed); }
}

/// Live registries, for lifetime verification.
pub(crate) static REGISTRIES: AtomicIsize = AtomicIsize::new(0);

#[derive(Debug, PartialEq, Eq)]
pub enum RegistryError {
    /// A handle that is free or not registered where registration is required.
    StaleHandle,
    Busy,
    EmptyContainer,
    /// Output capacity too small; nothing changed. Carries the requirement.
    Capacity { ticks: usize, updates: usize },
}

/// Outputs of [`RegistryInner::collect`].
#[derive(Default, Debug)]
pub struct Collected {
    pub ticks: Vec<(u32, u32)>,
    pub updates: Vec<(i64, i64)>,
    /// Handles whose queues were polled (their report buffers were refreshed).
    pub touched: Vec<u32>,
    /// Handles released by this call; Java drops their mirrors.
    pub freed: Vec<u32>,
    pub heap_size: usize,
}

impl RegistryInner {
    pub fn register(&mut self, container: Arc<Container>, report: i64) -> u32 {
        let slot = Slot { container: Some(container), report, registered: true, heap_refs: 0 };
        match self.free.pop() {
            Some(h) => { self.slots[h as usize] = slot; h }
            None => { self.slots.push(slot); (self.slots.len() - 1) as u32 }
        }
    }

    /// Slots ever allocated (upper bound for touched/freed outputs).
    pub fn handle_count(&self) -> usize { self.slots.len() }

    fn live(&self, handle: u32) -> Option<&Slot> {
        self.slots.get(handle as usize).filter(|s| s.container.is_some())
    }

    /// Unregisters; returns whether the handle was freed now.
    pub fn unregister(&mut self, handle: u32) -> Result<bool, RegistryError> {
        match self.slots.get_mut(handle as usize) {
            Some(s) if s.container.is_some() && s.registered => s.registered = false,
            _ => return Err(RegistryError::StaleHandle),
        }
        Ok(self.release_if_unused(handle))
    }

    fn release_if_unused(&mut self, handle: u32) -> bool {
        let s = &mut self.slots[handle as usize];
        if s.container.is_some() && !s.registered && s.heap_refs == 0 {
            s.container = None;
            self.quarantine.push(handle);
            true
        } else {
            false
        }
    }

    /// The collect phase over the persistent heap. `cleaned_up` clears the
    /// retained heap first; `eligible` are registered handles in scan order.
    pub fn collect(&mut self, eligible: &[u32], cleaned_up: bool, now: i64, budget: i64,
        ticks_capacity: usize, updates_capacity: usize) -> (Collected, Option<RegistryError>) {
        let mut out = Collected::default();
        for &h in eligible {
            if !self.live(h).is_some_and(|s| s.registered) { return (out, Some(RegistryError::StaleHandle)); }
        }
        let retained: Vec<u32> = if cleaned_up { Vec::new() } else { self.heap.clone() };
        let list: Vec<u32> = retained.iter().chain(eligible.iter()).copied().collect();
        // Distinct containers in first-seen order; duplicate heap slots alias one queue.
        // Distinct by container identity: one container may be bound under several
        // handles (keys or registries), and must be entered only once.
        let mut distinct: Vec<u32> = Vec::with_capacity(list.len());
        // Borrowed from the slots' own Arcs: no slot is freed until the end of this
        // call, after every guard below has been dropped.
        let mut containers: Vec<*const Container> = Vec::with_capacity(list.len());
        let mut slot_of = Vec::with_capacity(list.len());
        let mut index_of: HashMap<*const Container, usize, FastHash> = HashMap::with_capacity_and_hasher(list.len(), FastHash::default());
        for &h in &list {
            let c = Arc::as_ptr(self.slots[h as usize].container.as_ref().expect("live slot"));
            let i = *index_of.entry(c).or_insert_with(|| { distinct.push(h); containers.push(c); distinct.len() - 1 });
            slot_of.push(i);
        }
        let mut guards = Vec::with_capacity(containers.len());
        for &c in &containers {
            // Safety: the slot's Arc keeps `c` alive for the whole call.
            match unsafe { &*c }.enter() { Some(g) => guards.push(g), None => return (out, Some(RegistryError::Busy)) }
        }
        let queued: usize = guards.iter().map(|g| g.size()).sum();
        let need_ticks = queued.min(budget.max(0) as usize);
        if ticks_capacity < need_ticks || updates_capacity < list.len() {
            return (out, Some(RegistryError::Capacity { ticks: need_ticks, updates: list.len() }));
        }
        // Outputs of the previous collect are consumed: quarantined handles may be reused.
        let released = std::mem::take(&mut self.quarantine);
        self.free.extend(released);
        // From here state changes; the old heap's references are replaced.
        let sizes_before: Vec<usize> = guards.iter().map(|g| g.size()).collect();
        let mut queues: Vec<&mut Queue> = guards.iter_mut().map(|g| &mut **g).collect();
        let (drained, error) = drain(&mut queues, &slot_of, retained.len(), now, budget);
        out.ticks = drained.ticks.iter().map(|&(i, id)| (list[i], id)).collect();
        out.updates = drained.updates;
        let new_heap: Vec<u32> = drained.heap.iter().map(|&i| list[i]).collect();
        for (i, q) in queues.iter().enumerate() {
            if q.size() != sizes_before[i] {
                let h = distinct[i];
                out.touched.push(h);
                unsafe { ffi::report_to(q, self.slots[h as usize].report); }
            }
        }
        drop(queues);
        drop(guards);
        for &h in &self.heap { self.slots[h as usize].heap_refs -= 1; }
        for &h in &new_heap { self.slots[h as usize].heap_refs += 1; }
        let mut candidates = std::mem::replace(&mut self.heap, new_heap);
        candidates.sort_unstable();
        candidates.dedup();
        for h in candidates {
            if self.release_if_unused(h) { out.freed.push(h); }
        }
        out.heap_size = self.heap.len();
        (out, error.map(|_| RegistryError::EmptyContainer))
    }
}

//! Primary ordered ticking membership shared by client and server worlds.
//! Java retains behavior objects; native pages own keys, ordering and snapshots.
use std::{
    collections::HashMap,
    sync::atomic::{AtomicU64, Ordering},
};
mod ffi;

const NONE: u32 = u32::MAX;
const PAGE: usize = 64;
static NEXT_TABLE: AtomicU64 = AtomicU64::new(1);
fn table_identity() -> u64 {
    NEXT_TABLE
        .fetch_update(Ordering::Relaxed, Ordering::Relaxed, |value| {
            value.checked_add(1)
        })
        .expect("entity table identity exhausted")
}

#[derive(Clone, Copy)]
#[repr(C)]
struct Node {
    token: u64,
    key: i32,
    previous: u32,
    next: u32,
}
impl Default for Node {
    fn default() -> Self {
        Self {
            token: 0,
            key: 0,
            previous: NONE,
            next: NONE,
        }
    }
}

struct Table {
    pages: Vec<Box<[Node; PAGE]>>,
    rows: Vec<usize>,
    identity: u64,
    spare: Vec<u32>,
    keys: HashMap<i32, u32>,
    used: u32,
    head: Option<u32>,
    tail: Option<u32>,
}
impl Default for Table {
    fn default() -> Self {
        Self {
            pages: Vec::new(),
            rows: Vec::new(),
            identity: table_identity(),
            spare: Vec::new(),
            keys: HashMap::new(),
            used: 0,
            head: None,
            tail: None,
        }
    }
}
impl Clone for Table {
    fn clone(&self) -> Self {
        let pages = self.pages.clone();
        // A copied pointer table must name the new boxes, never the old snapshot.
        let rows = pages.iter().map(|page| page.as_ptr() as usize).collect();
        Self {
            pages,
            rows,
            identity: table_identity(),
            spare: self.spare.clone(),
            keys: self.keys.clone(),
            used: self.used,
            head: self.head,
            tail: self.tail,
        }
    }
}

#[repr(C)]
struct IterationView {
    identity: u64,
    rows: *const usize,
    page_count: u64,
    head: u32,
    count: u32,
}
impl Table {
    fn view(&self) -> IterationView {
        IterationView {
            identity: self.identity,
            rows: if self.rows.is_empty() {
                std::ptr::null()
            } else {
                self.rows.as_ptr()
            },
            page_count: self.pages.len() as u64,
            head: self.head.unwrap_or(NONE),
            count: self.keys.len() as u32,
        }
    }
    fn node(&self, index: u32) -> &Node {
        &self.pages[index as usize / PAGE][index as usize % PAGE]
    }
    fn node_mut(&mut self, index: u32) -> &mut Node {
        &mut self.pages[index as usize / PAGE][index as usize % PAGE]
    }
    fn token(&self, key: i32) -> Option<u64> {
        self.keys.get(&key).map(|i| self.node(*i).token)
    }
    fn put(&mut self, key: i32, token: u64) {
        if let Some(index) = self.keys.get(&key).copied() {
            self.node_mut(index).token = token;
            return;
        }
        let index = self.spare.pop().unwrap_or_else(|| {
            let index = self.used;
            self.used = self
                .used
                .checked_add(1)
                .expect("ordered membership exhausted");
            if index as usize / PAGE == self.pages.len() {
                let page = Box::new([Node::default(); PAGE]);
                self.rows.push(page.as_ptr() as usize);
                self.pages.push(page);
            }
            index
        });
        *self.node_mut(index) = Node {
            key,
            token,
            previous: self.tail.unwrap_or(NONE),
            next: NONE,
        };
        if let Some(tail) = self.tail {
            self.node_mut(tail).next = index;
        } else {
            self.head = Some(index);
        }
        self.tail = Some(index);
        self.keys.insert(key, index);
    }
    fn remove(&mut self, key: i32) -> Option<u64> {
        let index = self.keys.remove(&key)?;
        let node = *self.node(index);
        if node.previous == NONE {
            self.head = (node.next != NONE).then_some(node.next);
        } else {
            self.node_mut(node.previous).next = node.next;
        }
        if node.next == NONE {
            self.tail = (node.previous != NONE).then_some(node.previous);
        } else {
            self.node_mut(node.next).previous = node.previous;
        }
        *self.node_mut(index) = Node::default();
        self.spare.push(index);
        Some(node.token)
    }
    #[cfg(test)]
    fn ordered(&self) -> Vec<(i32, u64)> {
        let mut result = Vec::new();
        let mut index = self.head;
        while let Some(i) = index {
            let n = self.node(i);
            result.push((n.key, n.token));
            index = (n.next != NONE).then_some(n.next);
        }
        assert_eq!(result.len(), self.keys.len());
        result
    }
}

#[derive(Default)]
struct Bindings {
    generation: Vec<u32>,
    live: Vec<bool>,
    spare: Vec<u32>,
}
impl Bindings {
    fn allocate(&mut self) -> u64 {
        while let Some(slot) = self.spare.pop() {
            if let Some(next) = self.generation[slot as usize].checked_add(1) {
                self.generation[slot as usize] = next;
                self.live[slot as usize] = true;
                return (u64::from(next) << 32) | u64::from(slot);
            }
        }
        let slot = u32::try_from(self.generation.len()).expect("entity bindings exhausted");
        assert_ne!(slot, NONE);
        self.generation.push(1);
        self.live.push(true);
        1u64 << 32 | u64::from(slot)
    }
    fn release(&mut self, token: u64) {
        let slot = token as u32 as usize;
        assert!(self.live[slot]);
        assert_eq!(self.generation[slot], (token >> 32) as u32);
        self.live[slot] = false;
        self.spare.push(slot as u32);
    }
}

#[derive(Debug, PartialEq, Eq)]
enum OrderError {
    AlreadyIterating,
    NotIterating,
    PendingBindingClear,
}

struct Change {
    new: u64,
    old: Option<u64>,
    old_pinned: bool,
}

struct Order {
    // Match Java's two physical map receivers: getters can capture one across
    // several active/passive swaps. Sharing a receiver does not freeze it.
    tables: [Table; 2],
    active: usize,
    iteration: Option<usize>,
    bindings: Bindings,
    retired: Vec<u64>,
    awaiting_clear: Vec<u64>,
}
impl Default for Order {
    fn default() -> Self {
        Self {
            tables: [Table::default(), Table::default()],
            active: 0,
            iteration: None,
            bindings: Bindings::default(),
            retired: Vec::new(),
            awaiting_clear: Vec::new(),
        }
    }
}
impl Order {
    /// Before virtual getId, perform the same copy/swap as Java, then capture
    /// the physical receiver. Contains captures without preparing a mutation.
    fn receiver(&mut self, mutation: bool) -> Result<usize, OrderError> {
        if mutation {
            self.prepare_mutation()?;
        }
        Ok(self.active)
    }
    fn prepare_mutation(&mut self) -> Result<(), OrderError> {
        if !self.awaiting_clear.is_empty() {
            return Err(OrderError::PendingBindingClear);
        }
        if self.iteration == Some(self.active) {
            let next = 1 - self.active;
            self.tables[next] = self.tables[self.active].clone();
            self.active = next;
        }
        Ok(())
    }
    fn retire(&mut self, key: i32, token: u64) -> bool {
        let pinned = self
            .iteration
            .is_some_and(|old| self.tables[old].token(key) == Some(token));
        if pinned {
            self.retired.push(token);
        } else {
            self.bindings.release(token);
        }
        pinned
    }
    fn add_to(&mut self, receiver: usize, key: i32) -> Change {
        // A delayed put into the inactive physical map is observable through
        // contains, but never ticks an object. A later swap clears this map
        // before copying live membership; no Java object lease is needed.
        if receiver != self.active {
            debug_assert_ne!(Some(receiver), self.iteration);
            self.tables[receiver].put(key, 0);
            return Change {
                new: 0,
                old: None,
                old_pinned: false,
            };
        }
        let old = self.tables[receiver].token(key);
        let old_pinned = old.is_some_and(|token| self.retire(key, token));
        let new = self.bindings.allocate();
        self.tables[receiver].put(key, new);
        Change {
            new,
            old,
            old_pinned,
        }
    }
    fn remove_from(&mut self, receiver: usize, key: i32) -> Option<(u64, bool)> {
        let token = self.tables[receiver].remove(key)?;
        if token == 0 {
            return None;
        }
        Some((token, self.retire(key, token)))
    }
    fn contains_in(&self, receiver: usize, key: i32) -> bool {
        self.tables[receiver].keys.contains_key(&key)
    }
    #[cfg(test)]
    fn add(&mut self, key: i32) -> Result<Change, OrderError> {
        let receiver = self.receiver(true)?;
        Ok(self.add_to(receiver, key))
    }
    #[cfg(test)]
    fn remove(&mut self, key: i32) -> Result<Option<(u64, bool)>, OrderError> {
        let receiver = self.receiver(true)?;
        Ok(self.remove_from(receiver, key))
    }
    #[cfg(test)]
    fn contains(&self, key: i32) -> bool {
        self.contains_in(self.active, key)
    }
    fn begin(&mut self) -> Result<(), OrderError> {
        if self.iteration.is_some() {
            return Err(OrderError::AlreadyIterating);
        }
        if !self.awaiting_clear.is_empty() {
            return Err(OrderError::PendingBindingClear);
        }
        self.iteration = Some(self.active);
        Ok(())
    }
    fn iteration_view(&self) -> Result<IterationView, OrderError> {
        self.iteration
            .map(|table| self.tables[table].view())
            .ok_or(OrderError::NotIterating)
    }
    fn end(&mut self) -> Result<&[u64], OrderError> {
        let old = self.iteration.take().ok_or(OrderError::NotIterating)?;
        if old != self.active {
            // Keep the inactive map's key/order receiver semantics while
            // releasing references that can no longer reach a tick callback.
            for page in &mut self.tables[old].pages {
                for node in page.iter_mut() {
                    node.token = 0;
                }
            }
        }
        self.awaiting_clear = std::mem::take(&mut self.retired);
        Ok(&self.awaiting_clear)
    }
    fn acknowledge_clear(&mut self) {
        for token in self.awaiting_clear.drain(..) {
            self.bindings.release(token);
        }
    }
}

#[cfg(test)]
mod tests;

use super::position_hash::mix;

const NONE: u32 = u32::MAX;
const FAST_PROBES: usize = 32;
const LOAD_DENOMINATOR: usize = 4;

#[derive(Clone, Copy)]
struct Links {
    previous: u32,
    next: u32,
}
const UNLINKED: Links = Links {
    previous: NONE,
    next: NONE,
};

// These plans are private to a single, exclusive queue operation. The target
// bucket must remain unchanged until commit; distinct-priority removal cannot
// alter its slots. Commit never allocates and cannot request a retry.
pub(super) enum Insertion {
    Duplicate,
    At(usize),
}
pub(super) enum Removal {
    Absent,
    At(usize),
}

/// Primitive insertion-ordered open-addressed set. Zero has its own reserved
/// slot. FIFO links refer directly to table cells, so popping needs no hash lookup.
/// Deletion closes probe gaps and repairs links; dense keys keep probes separate
/// from FIFO links. Storage is reused until growth; removals never allocate.
pub(super) struct OrderedSet {
    keys: Vec<u64>,
    links: Vec<Links>,
    first: u32,
    last: u32,
    size: usize,
    contains_zero: bool,
}

impl OrderedSet {
    pub fn new(expected: usize) -> Result<Self, ()> {
        let capacity = expected
            .checked_mul(LOAD_DENOMINATOR)
            .ok_or(())?
            .max(2)
            .checked_next_power_of_two()
            .ok_or(())?;
        let (keys, links) = Self::allocate(capacity)?;
        Ok(Self {
            keys,
            links,
            first: NONE,
            last: NONE,
            size: 0,
            contains_zero: false,
        })
    }

    fn allocate(capacity: usize) -> Result<(Vec<u64>, Vec<Links>), ()> {
        // Match the signed Java array-index domain while keeping each link pair
        // eight bytes. Slot zero's reserved cell also fits this domain.
        if capacity >= i32::MAX as usize {
            return Err(());
        }
        let length = capacity.checked_add(1).ok_or(())?;
        let mut keys = Vec::new();
        let mut links = Vec::new();
        keys.try_reserve_exact(length).map_err(|_| ())?;
        links.try_reserve_exact(length).map_err(|_| ())?;
        keys.resize(length, 0);
        links.resize(length, UNLINKED);
        Ok((keys, links))
    }

    fn capacity(&self) -> usize {
        self.keys.len() - 1
    }
    pub fn is_empty(&self) -> bool {
        self.size == 0
    }

    // Some(Ok(index)): present; Some(Err(index)): empty insertion cell;
    // None: probe budget exhausted. Every nonzero table has an empty cell.
    fn find(&self, value: u64, budget: usize) -> Option<Result<usize, usize>> {
        let capacity = self.capacity();
        if value == 0 {
            return Some(if self.contains_zero {
                Ok(capacity)
            } else {
                Err(capacity)
            });
        }
        let mask = capacity - 1;
        let mut index = mix(value) as usize & mask;
        for _ in 0..budget {
            let key = self.keys[index];
            if key == value {
                return Some(Ok(index));
            }
            if key == 0 {
                return Some(Err(index));
            }
            index = (index + 1) & mask;
        }
        None
    }

    fn append(&mut self, value: u64, index: usize) {
        self.keys[index] = value;
        self.contains_zero |= value == 0;
        self.links[index] = Links {
            previous: self.last,
            next: NONE,
        };
        if self.last == NONE {
            self.first = index as u32;
        } else {
            self.links[self.last as usize].next = index as u32;
        }
        self.last = index as u32;
        self.size += 1;
    }

    fn resize(&mut self, capacity: usize) -> Result<(), ()> {
        let (keys, links) = Self::allocate(capacity)?;
        let mut replacement = Self {
            keys,
            links,
            first: NONE,
            last: NONE,
            size: 0,
            contains_zero: false,
        };
        let mut index = self.first;
        while index != NONE {
            let value = self.keys[index as usize];
            let destination = replacement.find(value, capacity).unwrap().unwrap_err();
            replacement.append(value, destination);
            index = self.links[index as usize].next;
        }
        // Publish only after both allocations and the ordered rebuild succeeded.
        *self = replacement;
        Ok(())
    }

    fn grow(&mut self) -> Result<(), ()> {
        self.resize(self.capacity().checked_mul(2).ok_or(())?)
    }

    pub fn insert(&mut self, value: u64) -> Result<(), ()> {
        let slot = self.find(value, self.capacity()).unwrap();
        if slot.is_ok() {
            return Ok(());
        }
        let index = if self.size < (self.capacity() / LOAD_DENOMINATOR).max(1) {
            slot.unwrap_err()
        } else {
            self.grow()?;
            self.find(value, self.capacity()).unwrap().unwrap_err()
        };
        self.append(value, index);
        Ok(())
    }

    pub fn prepare_insert(&self, value: u64) -> Option<Insertion> {
        match self.find(value, FAST_PROBES) {
            Some(Ok(_)) => Some(Insertion::Duplicate),
            Some(Err(index)) if self.size < (self.capacity() / LOAD_DENOMINATOR).max(1) => {
                Some(Insertion::At(index))
            }
            _ => None,
        }
    }

    pub fn commit_insert(&mut self, value: u64, plan: Insertion) {
        if let Insertion::At(index) = plan {
            self.append(value, index);
        }
    }

    pub fn prepare_remove(&self, value: u64) -> Option<Removal> {
        match self.find(value, FAST_PROBES) {
            Some(Err(_)) => Some(Removal::Absent),
            Some(Ok(index)) if self.short_gap(index) => Some(Removal::At(index)),
            _ => None,
        }
    }

    pub fn commit_remove(&mut self, plan: Removal) {
        if let Removal::At(index) = plan {
            self.remove_index(index);
        }
    }

    /// Never allocates, waits or changes state before a rejected fast operation.
    pub fn insert_without_growth(&mut self, value: u64) -> bool {
        match self.prepare_insert(value) {
            Some(plan) => {
                self.commit_insert(value, plan);
                true
            }
            None => false,
        }
    }

    #[inline]
    fn short_gap(&self, index: usize) -> bool {
        if index == self.capacity() {
            return true;
        }
        let mask = self.capacity() - 1;
        let mut scan = (index + 1) & mask;
        for _ in 0..FAST_PROBES {
            if self.keys[scan] == 0 {
                return true;
            }
            scan = (scan + 1) & mask;
        }
        false
    }

    fn relocate_links(&mut self, from: usize, to: usize) {
        let links = self.links[from];
        if links.previous == NONE {
            self.first = to as u32;
        } else {
            self.links[links.previous as usize].next = to as u32;
        }
        if links.next == NONE {
            self.last = to as u32;
        } else {
            self.links[links.next as usize].previous = to as u32;
        }
        self.links[to] = links;
    }

    fn remove_index(&mut self, index: usize) {
        let links = self.links[index];
        if links.previous == NONE {
            self.first = links.next;
        } else {
            self.links[links.previous as usize].next = links.next;
        }
        if links.next == NONE {
            self.last = links.previous;
        } else {
            self.links[links.next as usize].previous = links.previous;
        }
        self.size -= 1;
        if index == self.capacity() {
            self.contains_zero = false;
            return;
        }
        self.close_gap(index);
    }

    fn close_gap(&mut self, index: usize) {
        let mask = self.capacity() - 1;
        let mut hole = index;
        let mut scan = (hole + 1) & mask;
        loop {
            let value = self.keys[scan];
            if value == 0 {
                self.keys[hole] = 0;
                return;
            }
            let home = mix(value) as usize & mask;
            // The hole lies on this key's cyclic probe path: move into it.
            if (scan.wrapping_sub(home) & mask) >= (scan.wrapping_sub(hole) & mask) {
                self.keys[hole] = value;
                self.relocate_links(scan, hole);
                hole = scan;
            }
            scan = (scan + 1) & mask;
        }
    }

    pub fn remove(&mut self, value: u64) -> Result<(), ()> {
        if let Ok(index) = self.find(value, self.capacity()).unwrap() {
            self.remove_index(index);
        }
        Ok(())
    }

    pub fn remove_bounded(&mut self, value: u64) -> bool {
        match self.prepare_remove(value) {
            Some(plan) => {
                self.commit_remove(plan);
                true
            }
            None => false,
        }
    }

    pub fn pop(&mut self) -> Result<Option<u64>, ()> {
        if self.is_empty() {
            return Ok(None);
        }
        let value = self.remove_head();
        Ok(Some(value))
    }

    fn remove_head(&mut self) -> u64 {
        // FIFO removal already knows there is no predecessor. Avoid a general
        // membership lookup/unlink and share the same probe-gap repair.
        let index = self.first as usize;
        let value = self.keys[index];
        self.first = self.links[index].next;
        self.size -= 1;
        if self.first == NONE {
            self.last = NONE;
        } else {
            self.links[self.first as usize].previous = NONE;
        }
        if index == self.capacity() {
            self.contains_zero = false;
        } else {
            self.close_gap(index);
        }
        value
    }

    pub fn pop_bounded(&mut self) -> Option<Option<u64>> {
        if self.is_empty() {
            return Some(None);
        }
        if !self.short_gap(self.first as usize) {
            return None;
        }
        Some(Some(self.remove_head()))
    }

    #[cfg(test)]
    pub fn allocated_slots(&self) -> usize {
        self.capacity()
    }
}

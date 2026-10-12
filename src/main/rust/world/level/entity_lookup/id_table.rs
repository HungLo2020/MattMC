//! Ordered primitive identity map. Match Fastutil's physical indices as well
//! as insertion order: live iterators can cross Java callback mutations.
use std::sync::atomic::{AtomicI32, AtomicU64, Ordering};

#[repr(C)]
#[derive(Default)]
pub(super) struct Node {
    key: AtomicI32,
    padding: i32,
    pub(super) token: AtomicU64,
    link: AtomicU64,
}
impl Node {
    fn key(&self) -> i32 {
        self.key.load(Ordering::Relaxed)
    }
    fn token(&self) -> u64 {
        self.token.load(Ordering::Relaxed)
    }
    fn link(&self) -> u64 {
        self.link.load(Ordering::Relaxed)
    }
    fn set_previous(&self, previous: i32) {
        self.link.store(
            (self.link() & 0xffff_ffff) | ((previous as u32 as u64) << 32),
            Ordering::Release,
        );
    }
    fn set_next(&self, next: i32) {
        self.link.store(
            (self.link() & 0xffff_ffff_0000_0000) | next as u32 as u64,
            Ordering::Release,
        );
    }
}
fn mix(key: i32) -> usize {
    let h = (key as u32).wrapping_mul(0x9e37_79b9);
    (h ^ (h >> 16)) as usize
}
fn nodes(n: usize) -> Box<[Node]> {
    (0..=n).map(|_| Node::default()).collect()
}

pub(super) struct Table {
    pub(super) nodes: Box<[Node]>,
    n: usize,
    size: usize,
    zero: bool,
    pub(super) first: i32,
    last: i32,
    pub(super) revision: u64,
}
impl Default for Table {
    fn default() -> Self {
        Self {
            nodes: nodes(32),
            n: 32,
            size: 0,
            zero: false,
            first: -1,
            last: -1,
            revision: 1,
        }
    }
}
impl Table {
    fn max_fill(&self) -> usize {
        (self.n * 3 / 4).min(self.n - 1)
    }
    fn find(&self, key: i32) -> Result<usize, usize> {
        if key == 0 {
            return if self.zero { Ok(self.n) } else { Err(self.n) };
        }
        let mut i = mix(key) & (self.n - 1);
        loop {
            match self.nodes[i].key() {
                0 => return Err(i),
                found if found == key => return Ok(i),
                _ => i = (i + 1) & (self.n - 1),
            }
        }
    }
    pub(super) fn get(&self, key: i32) -> u64 {
        self.find(key).map_or(0, |i| self.nodes[i].token())
    }
    pub(super) fn put(&mut self, key: i32, token: u64) -> u64 {
        let i = match self.find(key) {
            Ok(i) => return self.nodes[i].token.swap(token, Ordering::AcqRel),
            Err(i) => i,
        };
        if i == self.n {
            self.zero = true;
        }
        self.nodes[i].key.store(key, Ordering::Release);
        self.nodes[i].token.store(token, Ordering::Release);
        if self.size == 0 {
            self.first = i as i32;
            self.last = i as i32;
            self.nodes[i].link.store(u64::MAX, Ordering::Release);
        } else {
            self.nodes[self.last as usize].set_next(i as i32);
            self.nodes[i].link.store(
                ((self.last as u32 as u64) << 32) | 0xffff_ffff,
                Ordering::Release,
            );
            self.last = i as i32;
        }
        let old_size = self.size;
        self.size += 1;
        if old_size >= self.max_fill() {
            // HashCommon.arraySize(size + 1, .75f).
            self.rehash(((self.size + 1) * 4).div_ceil(3).next_power_of_two().max(2));
        }
        0
    }
    fn unlink(&mut self, i: usize) {
        if self.size == 0 {
            self.first = -1;
            self.last = -1;
            return;
        }
        let link = self.nodes[i].link();
        let previous = (link >> 32) as u32 as i32;
        let next = link as u32 as i32;
        if self.first == i as i32 {
            self.first = next;
            if next >= 0 {
                self.nodes[next as usize].set_previous(-1);
            }
        } else if self.last == i as i32 {
            self.last = previous;
            if previous >= 0 {
                self.nodes[previous as usize].set_next(-1);
            }
        } else {
            self.nodes[previous as usize].set_next(next);
            self.nodes[next as usize].set_previous(previous);
        }
    }
    fn move_links(&mut self, from: usize, to: usize) {
        let link = self.nodes[from].link();
        if self.size == 1 {
            self.first = to as i32;
            self.last = to as i32;
        } else if self.first == from as i32 {
            self.first = to as i32;
            self.nodes[link as u32 as usize].set_previous(to as i32);
        } else if self.last == from as i32 {
            self.last = to as i32;
            self.nodes[(link >> 32) as usize].set_next(to as i32);
        } else {
            self.nodes[(link >> 32) as usize].set_next(to as i32);
            self.nodes[link as u32 as usize].set_previous(to as i32);
        }
        self.nodes[to].link.store(link, Ordering::Release);
    }
    fn shift_keys(&mut self, mut i: usize) {
        let mask = self.n - 1;
        loop {
            let hole = i;
            i = (i + 1) & mask;
            let key = loop {
                let key = self.nodes[i].key();
                if key == 0 {
                    self.nodes[hole].key.store(0, Ordering::Release);
                    self.nodes[hole].token.store(0, Ordering::Release);
                    return;
                }
                let home = mix(key) & mask;
                if if hole <= i {
                    hole >= home || home > i
                } else {
                    hole >= home && home > i
                } {
                    break key;
                }
                i = (i + 1) & mask;
            };
            self.nodes[hole].key.store(key, Ordering::Release);
            self.nodes[hole]
                .token
                .store(self.nodes[i].token(), Ordering::Release);
            self.move_links(i, hole);
        }
    }
    pub(super) fn remove(&mut self, key: i32) -> u64 {
        let Ok(i) = self.find(key) else {
            return 0;
        };
        let old = self.nodes[i].token.swap(0, Ordering::AcqRel);
        self.size -= 1;
        self.unlink(i);
        if i == self.n {
            self.zero = false;
        } else {
            self.shift_keys(i);
        }
        if self.n > 32 && self.size < self.max_fill() / 4 {
            self.rehash(self.n / 2);
        }
        old
    }
    fn rehash(&mut self, n: usize) {
        let new = nodes(n);
        let mut old = self.first;
        let mut previous = -1;
        self.first = -1;
        for _ in 0..self.size {
            let row = &self.nodes[old as usize];
            let key = row.key();
            let mut i = if key == 0 { n } else { mix(key) & (n - 1) };
            while key != 0 && new[i].key() != 0 {
                i = (i + 1) & (n - 1);
            }
            new[i].key.store(key, Ordering::Relaxed);
            new[i].token.store(row.token(), Ordering::Relaxed);
            if previous == -1 {
                self.first = i as i32;
                new[i].link.store(u64::MAX, Ordering::Relaxed);
            } else {
                new[previous as usize].set_next(i as i32);
                new[i].set_previous(previous);
            }
            previous = i as i32;
            old = row.link() as u32 as i32;
        }
        self.last = previous;
        if previous != -1 {
            new[previous as usize].set_next(-1);
        }
        self.n = n;
        self.nodes = new;
        self.revision = self
            .revision
            .checked_add(1)
            .expect("entity lookup view epoch exhausted");
    }
    #[cfg(test)]
    pub(super) fn ordered(&self) -> Vec<(i32, u64)> {
        let mut result = Vec::new();
        let mut i = self.first;
        while i != -1 {
            let row = &self.nodes[i as usize];
            result.push((row.key(), row.token()));
            i = row.link() as u32 as i32;
            assert!(result.len() <= self.size);
        }
        assert_eq!(result.len(), self.size);
        result
    }
}

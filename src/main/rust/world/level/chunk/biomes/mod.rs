//! Authoritative live biome sections and mutation generations; independent of rendering.
use std::sync::{
    atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering},
    Arc, Mutex,
};
const CELLS: usize = 64;

pub struct Generation {
    pub bits: usize,
    pub requested: usize,
    pub global: bool,
    pub count: AtomicU32,
    pub revision: AtomicU64,
    pub palette: Box<[AtomicU32]>,
    pub words: Box<[AtomicU64]>,
}
impl Generation {
    fn new(bits: usize, requested: usize, global: bool, palette: &[u32], words: &[u64]) -> Self {
        let capacity = if global {
            0
        } else if bits == 0 {
            1
        } else {
            1 << bits
        };
        Self {
            bits,
            requested,
            global,
            count: AtomicU32::new(palette.len() as u32),
            revision: AtomicU64::new(0),
            palette: (0..capacity)
                .map(|i| AtomicU32::new(*palette.get(i).unwrap_or(&0)))
                .collect(),
            words: words.iter().map(|&w| AtomicU64::new(w)).collect(),
        }
    }
    fn local(&self, index: usize) -> usize {
        if self.bits == 0 {
            return 0;
        }
        let per = 64 / self.bits;
        ((self.words[index / per].load(Ordering::Acquire) >> (index % per * self.bits))
            & ((1 << self.bits) - 1)) as usize
    }
    pub fn value(&self, index: usize) -> u32 {
        assert!(index < CELLS);
        let local = self.local(index);
        if self.global {
            local as u32
        } else {
            self.palette[local].load(Ordering::Acquire)
        }
    }
    fn set(&self, index: usize, local: u32) {
        if self.bits != 0 {
            let per = 64 / self.bits;
            let shift = index % per * self.bits;
            let mask = ((1u64 << self.bits) - 1) << shift;
            let word = &self.words[index / per];
            word.store(
                (word.load(Ordering::Relaxed) & !mask) | (u64::from(local) << shift),
                Ordering::Release,
            );
        }
    }
    fn snapshot(&self) -> ([u32; CELLS], u64) {
        if self.bits != 0 {
            // Called with the owner mutation lock held. Nonzero copies have
            // independent generations; only single-value generations alias.
            return (
                std::array::from_fn(|i| self.value(i)),
                self.revision.load(Ordering::Acquire),
            );
        }
        loop {
            let before = self.revision.load(Ordering::Acquire);
            if before & 1 != 0 {
                std::hint::spin_loop();
                continue;
            }
            let value = self.value(0);
            if before == self.revision.load(Ordering::Acquire) {
                return ([value; CELLS], before);
            }
        }
    }
}
struct State {
    generation: Arc<Generation>,
}
pub struct Owner {
    state: Mutex<State>,
    pub(crate) limit: u32,
    global_bits: usize,
    valid: AtomicBool,
}
impl Owner {
    pub fn load(
        bits: usize,
        requested: usize,
        palette: &[u32],
        words: &[u64],
        limit: u32,
        global_bits: usize,
    ) -> Option<Self> {
        let global = palette.is_empty();
        // Tiny/custom registries and aliased/malformed palettes retain their
        // compatibility path. Standard client registries use >=4 global bits.
        if !(4..=16).contains(&global_bits)
            || limit == 0
            || limit > 65535
            || limit as usize > 1 << global_bits
            || bits != if global { global_bits } else { requested }
            || (!global && requested > 3)
            || (global && !(4..=32).contains(&requested))
            || words.len()
                != if bits == 0 {
                    0
                } else {
                    CELLS.div_ceil(64 / bits)
                }
            || (!global
                && (palette.is_empty() || palette.len() > if bits == 0 { 1 } else { 1 << bits }))
        {
            return None;
        }
        for (i, &id) in palette.iter().enumerate() {
            if id >= limit || palette[..i].contains(&id) {
                return None;
            }
        }
        let generation = Arc::new(Generation::new(bits, requested, global, palette, words));
        if (0..CELLS).any(|i| {
            generation.local(i)
                >= if global {
                    limit as usize
                } else {
                    palette.len()
                }
        }) {
            return None;
        }
        Some(Self {
            state: Mutex::new(State { generation }),
            limit,
            global_bits,
            valid: AtomicBool::new(true),
        })
    }
    pub fn is_valid(&self) -> bool {
        self.valid.load(Ordering::Acquire)
    }
    pub fn invalidate(&self) {
        let _state = self.state.lock().unwrap();
        self.valid.store(false, Ordering::Release);
    }
    pub fn lease(&self) -> Arc<Generation> {
        Arc::clone(&self.state.lock().unwrap().generation)
    }
    pub fn copy(&self) -> Self {
        let state = self.state.lock().unwrap();
        let g = &state.generation;
        let generation = if g.bits == 0 {
            Arc::clone(g)
        } else {
            let palette: Vec<_> = (0..g.count.load(Ordering::Acquire) as usize)
                .map(|i| g.palette[i].load(Ordering::Acquire))
                .collect();
            let words: Vec<_> = g.words.iter().map(|w| w.load(Ordering::Acquire)).collect();
            Arc::new(Generation::new(
                g.bits,
                g.requested,
                g.global,
                &palette,
                &words,
            ))
        };
        Self {
            state: Mutex::new(State { generation }),
            limit: self.limit,
            global_bits: self.global_bits,
            valid: AtomicBool::new(self.is_valid()),
        }
    }
    pub fn read_single(&self, value: u32) -> bool {
        if value >= self.limit {
            return false;
        }
        let state = self.state.lock().unwrap();
        let g = &state.generation;
        if !self.is_valid() || g.bits != 0 || g.global {
            return false;
        }
        loop {
            let revision = g.revision.load(Ordering::Acquire);
            if revision & 1 != 0 {
                std::hint::spin_loop();
                continue;
            }
            let Some(next) = revision.checked_add(2) else {
                return false;
            };
            if g.revision
                .compare_exchange(revision, revision + 1, Ordering::AcqRel, Ordering::Acquire)
                .is_ok()
            {
                g.palette[0].store(value, Ordering::Release);
                g.revision.store(next, Ordering::Release);
                return true;
            }
        }
    }
    pub fn write(&self, index: usize, value: u32) -> Option<(u32, bool)> {
        if index >= CELLS || value >= self.limit {
            return None;
        }
        let mut state = self.state.lock().unwrap();
        if !self.is_valid() {
            return None;
        }
        let g = &state.generation;
        let old = g.value(index);
        if old == value {
            return Some((old, false));
        }
        if g.revision.load(Ordering::Relaxed) == u64::MAX {
            return None;
        }
        if g.global {
            g.set(index, value);
            g.revision.fetch_add(1, Ordering::Release);
            return Some((old, false));
        }
        let count = g.count.load(Ordering::Acquire) as usize;
        let existing = (0..count).find(|&i| g.palette[i].load(Ordering::Acquire) == value);
        if let Some(id) = existing {
            g.set(index, id as u32);
            g.revision.fetch_add(1, Ordering::Release);
            return Some((old, false));
        }
        if g.bits != 0 && count < 1 << g.bits {
            g.palette[count].store(value, Ordering::Release);
            g.count.store((count + 1) as u32, Ordering::Release);
            g.set(index, count as u32);
            g.revision.fetch_add(1, Ordering::Release);
            return Some((old, false));
        }
        let requested = if g.bits == 0 { 1 } else { g.bits + 1 };
        let global = requested > 3;
        let bits = if global { self.global_bits } else { requested };
        let per = 64 / bits;
        let mut palette = Vec::new();
        let mut words = vec![0u64; CELLS.div_ceil(per)];
        for i in 0..CELLS {
            let id = g.value(i);
            let local = if global {
                id
            } else {
                match palette.iter().position(|&v| v == id) {
                    Some(p) => p as u32,
                    None => {
                        palette.push(id);
                        (palette.len() - 1) as u32
                    }
                }
            };
            words[i / per] |= u64::from(local) << (i % per * bits);
        }
        let local = if global {
            value
        } else {
            match palette.iter().position(|&v| v == value) {
                Some(p) => p as u32,
                None => {
                    palette.push(value);
                    (palette.len() - 1) as u32
                }
            }
        };
        let next = Arc::new(Generation::new(bits, requested, global, &palette, &words));
        next.set(index, local);
        state.generation = next;
        Some((old, true))
    }
    pub fn snapshot(&self) -> (Arc<Generation>, [u32; CELLS], u64) {
        let state = self.state.lock().unwrap();
        let (values, revision) = state.generation.snapshot();
        (Arc::clone(&state.generation), values, revision)
    }
    pub fn matches(&self, generation: &Arc<Generation>, revision: u64) -> bool {
        let state = self.state.lock().unwrap();
        self.is_valid()
            && Arc::ptr_eq(generation, &state.generation)
            && generation.revision.load(Ordering::Acquire) == revision
    }
}

pub(crate) mod ffi;

#[cfg(test)]
mod tests;

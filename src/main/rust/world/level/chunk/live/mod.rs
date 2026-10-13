//! Authoritative live block palettes and packed storage. Java may retain atomic
//! read views of a generation; only this owner changes words or palette policy.
use std::collections::HashMap;
use std::sync::{
    atomic::{AtomicBool, AtomicU32, AtomicU64, Ordering},
    Arc, Mutex, Weak,
};
mod ffi;
#[cfg(test)]
mod tests;
const ENTRIES: usize = 4096;

struct Generation {
    bits: usize,
    requested: usize,
    global: bool,
    count: AtomicU32,
    revision: AtomicU64,
    palette: Box<[AtomicU32]>,
    words: Box<[AtomicU64]>,
}
impl Generation {
    fn new(bits: usize, requested: usize, global: bool, palette: &[u32], words: &[u64]) -> Self {
        let capacity = if global {
            0
        } else if bits == 0 {
            1
        } else {
            (1 << bits) + usize::from(bits >= 5)
        };
        let ids: Vec<_> = (0..capacity)
            .map(|i| AtomicU32::new(*palette.get(i).unwrap_or(&0)))
            .collect();
        Self {
            bits,
            requested,
            global,
            count: AtomicU32::new(palette.len() as u32),
            revision: AtomicU64::new(0),
            palette: ids.into_boxed_slice(),
            words: words.iter().map(|v| AtomicU64::new(*v)).collect(),
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
    fn state(&self, index: usize) -> u32 {
        let id = self.local(index);
        if self.global {
            id as u32
        } else {
            self.palette[id].load(Ordering::Acquire)
        }
    }
    /// Every entry's state, decoding each packed word once. Equivalent to
    /// `state(i)` for every index; only the owner (under its lock) writes.
    fn unpack(&self) -> Box<[u16; ENTRIES]> {
        let mut out = vec![0u16; ENTRIES].into_boxed_slice();
        let bits = self.bits;
        let per = 64 / bits;
        let mask = (1u64 << bits) - 1;
        let palette: Vec<u16> = if self.global {
            Vec::new()
        } else {
            self.palette.iter().map(|p| p.load(Ordering::Acquire) as u16).collect()
        };
        for (chunk, word) in out.chunks_mut(per).zip(self.words.iter()) {
            let mut value = word.load(Ordering::Acquire);
            for slot in chunk {
                let id = (value & mask) as usize;
                *slot = if self.global { id as u16 } else { palette[id] };
                value >>= bits;
            }
        }
        out.try_into().expect("ENTRIES-sized snapshot")
    }
    fn changed(&self) {
        // Saturation permanently declines cached reuse rather than allowing ABA.
        self.revision.fetch_update(Ordering::Release, Ordering::Relaxed,
            |value| Some(value.saturating_add(1))).unwrap();
    }
    fn set(&self, index: usize, id: u32) {
        if self.bits == 0 {
            return;
        }
        let per = 64 / self.bits;
        let shift = index % per * self.bits;
        let mask = ((1u64 << self.bits) - 1) << shift;
        let word = &self.words[index / per];
        word.store(
            (word.load(Ordering::Relaxed) & !mask) | ((id as u64) << shift),
            Ordering::Release,
        );
        self.changed();
    }
    fn append(&self, state: u32) -> u32 {
        let count = self.count.load(Ordering::Relaxed) as usize;
        self.palette[count].store(state, Ordering::Release);
        self.count.store((count + 1) as u32, Ordering::Release);
        self.changed();
        count as u32
    }
}
struct State {
    generation: Arc<Generation>,
    ids: HashMap<u32, u32>,
    /// Generations replaced since Java last loaded a view. Java's previous
    /// view may still be read, so `view` hands these to Java to pin with it.
    retired: Vec<Arc<Generation>>,
}
pub struct Owner {
    authoritative: AtomicBool,
    state: Mutex<State>,
    limit: u32,
    global_bits: usize,
}
/// Weak identity of immutable storage configuration plus its mutation version.
/// Weak pointers prevent allocation-address reuse without retaining section data.
pub(crate) struct ReadStamp {
    generation: Weak<Generation>,
    revision: u64,
    single: Option<u32>,
}
impl ReadStamp {
    pub(crate) fn matches(&self, other: &Self) -> bool {
        Weak::ptr_eq(&self.generation, &other.generation)
            && self.revision == other.revision && self.single == other.single
    }
    pub(crate) fn alive(&self) -> bool { self.generation.strong_count() != 0 }
}
/// A borrowed scan under the storage mutation lock. No palette/word projection.
pub(crate) struct StateReader<'a> {
    generation: &'a Generation,
    single: Option<u32>,
}
impl StateReader<'_> {
    /// Packed semantic reads stay inside `with_state_reader`'s owner lock.
    /// Consumers can capture directly without an intermediate wire projection.
    pub(crate) fn packed_bits(&self) -> usize { self.generation.bits }
    pub(crate) fn global_palette(&self) -> bool { self.generation.global }
    pub(crate) fn palette_states(&self) -> impl ExactSizeIterator<Item = u32> + '_ {
        self.generation.palette[..self.generation.count.load(Ordering::Acquire) as usize]
            .iter().map(|state| self.single.unwrap_or_else(|| state.load(Ordering::Acquire)))
    }
    pub(crate) fn packed_words(&self) -> impl ExactSizeIterator<Item = u64> + '_ {
        self.generation.words.iter().map(|word| word.load(Ordering::Acquire))
    }
    pub(crate) fn get(&self, index: usize) -> u32 {
        self.single.unwrap_or_else(|| self.generation.state(index))
    }
    pub(crate) fn all_states(&self, mut admitted: impl FnMut(u32) -> bool) -> bool {
        if let Some(state) = self.single {
            return admitted(state);
        }
        if self.generation.global {
            return (0..ENTRIES).all(|index| admitted(self.get(index)));
        }
        (0..self.generation.count.load(Ordering::Acquire) as usize)
            .all(|id| admitted(self.generation.palette[id].load(Ordering::Acquire)))
    }
    /// Visit packed indices in Y/Z/X order, loading each word once. The owner
    /// lock excludes palette growth; no temporary state array is constructed.
    pub(crate) fn for_each_state(&self, mut visit: impl FnMut(usize, u32)) {
        if let Some(state) = self.single {
            for index in 0..ENTRIES { visit(index, state); }
            return;
        }
        let generation = self.generation;
        let per = 64 / generation.bits;
        let mask = (1u64 << generation.bits) - 1;
        let mut index = 0;
        for word in generation.words.iter() {
            let mut value = word.load(Ordering::Acquire);
            for _ in 0..per.min(ENTRIES - index) {
                let local = (value & mask) as usize;
                let state = if generation.global { local as u32 }
                    else { generation.palette[local].load(Ordering::Acquire) };
                visit(index, state);
                index += 1;
                value >>= generation.bits;
            }
        }
    }
}
impl Owner {
    /// Retired owners may still back scoped readers, but must no longer serve
    /// current-world queries after their Java container replaces the binding.
    pub(crate) fn invalidate(&self) { self.authoritative.store(false, Ordering::Release); }
    pub(crate) fn world_value(&self, index: usize) -> Option<u32> {
        if !self.authoritative.load(Ordering::Acquire) { return None; }
        let value = self.with_state_reader(|reader| reader.get(index));
        self.authoritative.load(Ordering::Acquire).then_some(value)
    }
    pub(crate) fn read_stamp(&self) -> Option<ReadStamp> {
        let guard = self.state.lock().unwrap();
        let g = &guard.generation;
        let revision = g.revision.load(Ordering::Acquire);
        if revision == u64::MAX { return None; }
        Some(ReadStamp { generation: Arc::downgrade(g), revision,
            // Zero-width copies can mutate through independent owner locks.
            // Their actual single value, not a possibly delayed revision, is decisive.
            single: (g.bits == 0).then(|| g.state(0)) })
    }
    pub(crate) fn with_state_reader<R>(&self, read: impl FnOnce(StateReader<'_>) -> R) -> R {
        let guard = self.state.lock().unwrap();
        let generation = &guard.generation;
        // Zero-width copies can share the palette across independent locks.
        // Capture its single value once, rather than mixing two aliased updates.
        read(StateReader {
            generation,
            single: (generation.bits == 0).then(|| generation.state(0)),
        })
    }
    pub(crate) fn load(
        bits: usize,
        requested: usize,
        palette: &[i32],
        words: &[u64],
        limit: u32,
        global_bits: usize,
    ) -> Option<Self> {
        let global = palette.is_empty();
        if limit == 0
            || limit > 65535
            || !(9..=16).contains(&global_bits)
            || bits
                != if global {
                    global_bits
                } else {
                    match requested {
                        0 => 0,
                        1..=4 => 4,
                        5..=8 => requested,
                        _ => return None,
                    }
                }
            || words.len()
                != if bits == 0 {
                    0
                } else {
                    ENTRIES.div_ceil(64 / bits)
                }
            || (!global
                && (palette.is_empty() || palette.len() > if bits == 0 { 1 } else { 1 << bits }))
            || (global && requested <= 8)
        {
            return None;
        }
        let mut ids = HashMap::new();
        let mut entries = Vec::new();
        for (i, &value) in palette.iter().enumerate() {
            if value < 0 || value as u32 >= limit || ids.insert(value as u32, i as u32).is_some() {
                return None;
            }
            entries.push(value as u32);
        }
        let bound = if global { limit as usize } else { palette.len() };
        if !super::palette::valid_indices(words, bits, ENTRIES, bound) {
            return None;
        }
        let generation = Arc::new(Generation::new(bits, requested, global, &entries, words));
        Some(Self {
            authoritative: AtomicBool::new(true),
            state: Mutex::new(State { generation, ids, retired: Vec::new() }),
            limit,
            global_bits,
        })
    }
    pub(super) fn copy(&self) -> Self {
        let guard = self.state.lock().unwrap();
        let g = &guard.generation;
        // SingleValuePalette.copy returns itself. A zero-width network read
        // mutates that shared palette; copies share only this single generation.
        if g.bits == 0 {
            return Self {
                authoritative: AtomicBool::new(true),
                state: Mutex::new(State {
                    generation: g.clone(),
                    ids: guard.ids.clone(),
                    retired: Vec::new(),
                }),
                limit: self.limit,
                global_bits: self.global_bits,
            };
        }
        let entries: Vec<_> = (0..g.count.load(Ordering::Acquire) as usize)
            .map(|i| g.palette[i].load(Ordering::Acquire))
            .collect();
        let words: Vec<_> = g.words.iter().map(|v| v.load(Ordering::Acquire)).collect();
        Self {
            authoritative: AtomicBool::new(true),
            state: Mutex::new(State {
                generation: Arc::new(Generation::new(
                    g.bits,
                    g.requested,
                    g.global,
                    &entries,
                    &words,
                )),
                ids: guard.ids.clone(),
                retired: Vec::new(),
            }),
            limit: self.limit,
            global_bits: self.global_bits,
        }
    }
    /// A stage's isolated packed input, copied entirely within Rust. No Java
    /// palette, state objects or word-array projection participates in this path.
    pub(crate) fn stage_snapshot(
        &self,
        limit: u32,
        global_bits: u32,
    ) -> Option<(u32, u32, Vec<i32>, Vec<i64>)> {
        if self.limit != limit || self.global_bits != global_bits as usize {
            return None;
        }
        let state = self.state.lock().unwrap();
        Some(Self::stage_generation(&state.generation))
    }
    fn stage_generation(g: &Generation) -> (u32, u32, Vec<i32>, Vec<i64>) {
        let kind = if g.global {
            3
        } else if g.bits == 0 {
            0
        } else if g.bits == 4 {
            1
        } else {
            2
        };
        let palette = (0..g.count.load(Ordering::Acquire) as usize)
            .map(|i| g.palette[i].load(Ordering::Acquire) as i32)
            .collect();
        let words = g
            .words
            .iter()
            .map(|w| w.load(Ordering::Acquire) as i64)
            .collect();
        (kind, g.bits as u32, palette, words)
    }
    /// Counts and storage are sampled under the mutation lock. No Java projection.
    pub(crate) fn stage_with_counts(
        &self,
        counts: &super::counters::Owner,
        limit: u32,
        global_bits: u32,
    ) -> Option<(u32, u32, Vec<i32>, Vec<i64>, [i32; 3])> {
        if self.limit != limit || self.global_bits != global_bits as usize {
            return None;
        }
        let state = self.state.lock().unwrap();
        let (kind, bits, palette, words) = Self::stage_generation(&state.generation);
        Some((kind, bits, palette, words, counts.values()))
    }
    // One fused write, including palette admission, first-use growth and packed mutation.
    pub(super) fn write(&self, index: usize, value: u32) -> Option<(u32, bool)> {
        if index >= ENTRIES || value >= self.limit {
            return None;
        }
        let mut s = self.state.lock().unwrap();
        self.write_locked(&mut s, index, value)
    }
    fn write_locked(&self, s: &mut State, index: usize, value: u32) -> Option<(u32, bool)> {
        let old = s.generation.state(index);
        if s.generation.global {
            s.generation.set(index, value);
            return Some((old, false));
        }
        // A shared single palette may have been replaced through another copy.
        // Its current atomic value, rather than this owner's old map, is authoritative.
        if s.generation.bits == 0 {
            if old == value {
                return Some((old, false));
            }
        } else if let Some(&id) = s.ids.get(&value) {
            s.generation.set(index, id);
            return Some((old, false));
        }
        let count = s.generation.count.load(Ordering::Relaxed) as usize;
        let bits = s.generation.bits;
        if bits != 0 && count < 1 << bits {
            let id = s.generation.append(value);
            s.ids.insert(value, id);
            s.generation.set(index, id);
            return Some((old, false));
        }
        // Hash palettes admit the overflow value before growing. The old view
        // keeps that history, but no old packed index can reference the new id.
        if bits >= 5 {
            s.generation.append(value);
        }
        let requested = if bits == 0 { 4 } else { bits + 1 };
        let global = requested > 8;
        let new_bits = if global { self.global_bits } else { requested };
        let mut entries = Vec::new();
        let mut ids = HashMap::new();
        let mut packed = vec![0u64; ENTRIES.div_ceil(64 / new_bits)];
        let per = 64 / new_bits;
        for i in 0..ENTRIES {
            let state = s.generation.state(i);
            let id = if global {
                state
            } else {
                *ids.entry(state).or_insert_with(|| {
                    let id = entries.len() as u32;
                    entries.push(state);
                    id
                })
            };
            packed[i / per] |= (id as u64) << (i % per * new_bits);
        }
        let id = if global {
            value
        } else {
            *ids.entry(value).or_insert_with(|| {
                let id = entries.len() as u32;
                entries.push(value);
                id
            })
        };
        let next = Arc::new(Generation::new(
            new_bits, requested, global, &entries, &packed,
        ));
        next.set(index, id);
        let previous = std::mem::replace(&mut s.generation, next);
        s.retired.push(previous);
        s.ids = ids;
        Some((old, true))
    }
    pub(super) fn write_counts(
        &self,
        counts: &super::counters::Owner,
        index: usize,
        value: u32,
        policies: &[Option<super::counters::Policy>],
    ) -> Option<(u32, bool)> {
        if index >= ENTRIES || value >= self.limit || policies.len() != self.limit as usize {
            return None;
        }
        let new_policy = policies[value as usize]?;
        let mut s = self.state.lock().unwrap();
        let old_policy = policies[s.generation.state(index) as usize]?;
        // Admission completes before either state or counters changes.
        let result = self.write_locked(&mut s, index, value)?;
        counts.increment(old_policy, new_policy);
        Some(result)
    }
    pub(super) fn recount(
        &self,
        counts: &super::counters::Owner,
        policies: &[Option<super::counters::Policy>],
    ) -> bool {
        if policies.len() != self.limit as usize {
            return false;
        }
        let s = self.state.lock().unwrap();
        let g = &s.generation;
        let mut values = [0; 3];
        if g.bits == 0 {
            let Some(policy) = policies[g.state(0) as usize] else {
                return false;
            };
            let contribution = policy.recount();
            for lane in 0..3 {
                values[lane] = contribution[lane] * ENTRIES as i32;
            }
        } else {
            let per = 64 / g.bits;
            let mask = (1u64 << g.bits) - 1;
            for (cell, word) in g.words.iter().enumerate() {
                let count = per.min(ENTRIES - cell * per);
                let bits = count * g.bits;
                let valid = u64::MAX >> (64 - bits);
                let raw = word.load(Ordering::Acquire) & valid;
                let first = raw & mask;
                // Preserve the existing histogram's uniform-word fast case;
                // complete-word and final-word padding never contributes.
                let uniform = raw == first * (valid / mask);
                let fields = if uniform { 1 } else { count };
                for field in 0..fields {
                    let local = ((raw >> (field * g.bits)) & mask) as usize;
                    let id = if g.global {
                        local
                    } else {
                        g.palette[local].load(Ordering::Acquire) as usize
                    };
                    let Some(policy) = policies[id] else {
                        return false;
                    };
                    let contribution = policy.recount();
                    let frequency = if uniform { count as i32 } else { 1 };
                    for lane in 0..3 {
                        values[lane] += contribution[lane] * frequency;
                    }
                }
            }
        }
        counts.set(values);
        true
    }
    pub(super) fn read_single(&self, value: u32) -> bool {
        if value >= self.limit {
            return false;
        }
        let s = self.state.lock().unwrap();
        if s.generation.bits != 0 {
            return false;
        }
        s.generation.palette[0].store(value, Ordering::Release);
        s.generation.changed();
        true
    }
    pub(crate) fn capture(&self) -> Box<[u16; ENTRIES]> {
        let s = self.state.lock().unwrap();
        if s.generation.bits == 0 {
            return Box::new([s.generation.state(0) as u16; ENTRIES]);
        }
        s.generation.unpack()
    }
}

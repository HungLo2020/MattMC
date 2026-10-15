//! `Long2ObjectOpenHashMap<List<Ticket>>` with list ids for values: exact
//! table layout, growth/shrink, iteration order and iterator removal, so walks
//! and saves visit chunks in the original order.
//! Adapted from fastutil 8.5.12 Long2ObjectOpenHashMap (Apache-2.0),
//! Copyright (C) 2002-2023 Sebastiano Vigna.
//! https://www.apache.org/licenses/LICENSE-2.0
use super::Fault;

/// A `null` value slot.
pub(crate) const NONE: u32 = u32::MAX;
const DEFAULT_INITIAL_SIZE: usize = 16;

fn mix(key: i64) -> usize {
    let h = (key as u64).wrapping_mul(0x9e37_79b9_7f4a_7c15);
    let h = h ^ (h >> 32);
    (h ^ (h >> 16)) as u32 as i32 as usize
}

/// `HashCommon.arraySize(expected, 0.75f)`.
fn array_size(expected: usize) -> usize {
    ((expected as f32 / 0.75f32) as f64).ceil().max(2.0).min((1u64 << 30) as f64) as usize
}

fn max_fill(n: usize) -> usize {
    ((n as f64 * 0.75f32 as f64).ceil() as usize).min(n - 1)
}

fn table(n: usize) -> Result<(Vec<i64>, Vec<u32>), Fault> {
    let mut keys = Vec::new();
    let mut values = Vec::new();
    keys.try_reserve_exact(n + 1).map_err(|_| Fault::Allocation)?;
    values.try_reserve_exact(n + 1).map_err(|_| Fault::Allocation)?;
    keys.resize(n + 1, 0);
    values.resize(n + 1, NONE);
    Ok((keys, values))
}

pub(crate) struct ListMap {
    pub(crate) keys: Vec<i64>,
    pub(crate) values: Vec<u32>,
    n: usize,
    min_n: usize,
    max_fill: usize,
    size: usize,
    zero: bool,
}

impl ListMap {
    /// `new Long2ObjectOpenHashMap<>(expected)`.
    pub(crate) fn with_expected(expected: usize) -> Result<Self, Fault> {
        let n = array_size(expected).next_power_of_two();
        let (keys, values) = table(n)?;
        Ok(Self { keys, values, n, min_n: n, max_fill: max_fill(n), size: 0, zero: false })
    }

    pub(crate) fn len(&self) -> usize {
        self.size
    }

    fn mask(&self) -> usize {
        self.n - 1
    }

    fn find(&self, key: i64) -> Result<usize, usize> {
        if key == 0 {
            return if self.zero { Ok(self.n) } else { Err(self.n) };
        }
        let mut pos = mix(key) & self.mask();
        loop {
            match self.keys[pos] {
                0 => return Err(pos),
                k if k == key => return Ok(pos),
                _ => pos = (pos + 1) & self.mask(),
            }
        }
    }

    pub(crate) fn get(&self, key: i64) -> u32 {
        match self.find(key) {
            Ok(pos) => self.values[pos],
            Err(_) => NONE,
        }
    }

    /// `computeIfAbsent(key, k -> new list)`: the existing value (even null) or
    /// `create()` inserted. Returns the value and whether it was created.
    pub(crate) fn compute_if_absent(&mut self, key: i64, create: impl FnOnce() -> Result<u32, Fault>) -> Result<(u32, bool), Fault> {
        match self.find(key) {
            Ok(pos) => Ok((self.values[pos], false)),
            Err(pos) => {
                let value = create()?;
                if pos == self.n {
                    self.zero = true;
                }
                self.keys[pos] = key;
                self.values[pos] = value;
                let old = self.size;
                self.size += 1;
                if old >= self.max_fill {
                    self.rehash(array_size(self.size + 1).next_power_of_two())?;
                }
                Ok((value, true))
            }
        }
    }

    /// `put(key, value)`: replaces in place, or inserts (growing as fastutil
    /// does); returns the previous value or `NONE`.
    pub(crate) fn put(&mut self, key: i64, value: u32) -> Result<u32, Fault> {
        match self.find(key) {
            Ok(pos) => Ok(std::mem::replace(&mut self.values[pos], value)),
            Err(pos) => {
                if pos == self.n {
                    self.zero = true;
                }
                self.keys[pos] = key;
                self.values[pos] = value;
                let old = self.size;
                self.size += 1;
                if old >= self.max_fill {
                    self.rehash(array_size(self.size + 1).next_power_of_two())?;
                }
                Ok(NONE)
            }
        }
    }

    /// The table length (`key.length`), for an out-of-bounds report.
    pub(crate) fn table_len(&self) -> usize {
        self.keys.len()
    }

    /// `remove(key)`: the removed value, or `NONE`.
    pub(crate) fn remove(&mut self, key: i64) -> Result<u32, Fault> {
        let Ok(pos) = self.find(key) else { return Ok(NONE) };
        let value = self.values[pos];
        self.values[pos] = NONE;
        if pos == self.n {
            self.zero = false;
        } else {
            self.shift(pos, None);
        }
        self.size -= 1;
        if self.n > self.min_n && self.size < self.max_fill / 4 && self.n > DEFAULT_INITIAL_SIZE {
            self.rehash(self.n / 2)?;
        }
        Ok(value)
    }

    /// `clear()`; returns the values it dropped.
    pub(crate) fn clear(&mut self) -> Vec<u32> {
        if self.size == 0 {
            return Vec::new();
        }
        let dropped = self.ordered().into_iter().map(|(_, v)| v).collect();
        self.size = 0;
        self.zero = false;
        self.keys.fill(0);
        self.values.fill(NONE);
        dropped
    }

    fn shift(&mut self, mut pos: usize, mut wrapped: Option<&mut Option<Vec<i64>>>) {
        let mask = self.mask();
        loop {
            let last = pos;
            pos = (last + 1) & mask;
            let current;
            loop {
                let key = self.keys[pos];
                if key == 0 {
                    self.keys[last] = 0;
                    self.values[last] = NONE;
                    return;
                }
                let slot = mix(key) & mask;
                if if last <= pos { last >= slot || slot > pos } else { last >= slot && slot > pos } {
                    current = key;
                    break;
                }
                pos = (pos + 1) & mask;
            }
            if pos < last {
                if let Some(w) = wrapped.as_deref_mut() {
                    w.get_or_insert_with(|| Vec::with_capacity(2)).push(self.keys[pos]);
                }
            }
            self.keys[last] = current;
            self.values[last] = self.values[pos];
        }
    }

    fn rehash(&mut self, new_n: usize) -> Result<(), Fault> {
        let (mut keys, mut values) = table(new_n)?;
        let mask = new_n - 1;
        let mut i = self.n;
        let real = self.size - usize::from(self.zero);
        for _ in 0..real {
            loop {
                i -= 1;
                if self.keys[i] != 0 {
                    break;
                }
            }
            let mut pos = mix(self.keys[i]) & mask;
            while keys[pos] != 0 {
                pos = (pos + 1) & mask;
            }
            keys[pos] = self.keys[i];
            values[pos] = self.values[i];
        }
        values[new_n] = self.values[self.n];
        self.keys = keys;
        self.values = values;
        self.n = new_n;
        self.max_fill = max_fill(new_n);
        Ok(())
    }

    /// Iteration order of every map view: the zero key, then descending slots.
    pub(crate) fn ordered(&self) -> Vec<(i64, u32)> {
        let mut out = Vec::with_capacity(self.size);
        if self.zero {
            out.push((0, self.values[self.n]));
        }
        for pos in (0..self.n).rev() {
            if self.keys[pos] != 0 {
                out.push((self.keys[pos], self.values[pos]));
            }
        }
        out
    }

    pub(crate) fn cursor(&self) -> Cursor {
        Cursor { mark: Mark { pos: self.n as i32, last: -1, left: self.size, zero: self.zero }, wrapped: None }
    }

    /// A fast-iterator entry's positional `getValue()`.
    pub(crate) fn value_at(&self, index: i32) -> Result<u32, Fault> {
        self.values.get(index as usize).copied().ok_or(Fault::ArrayIndex)
    }

    pub(crate) fn key_at(&self, index: i32) -> Result<i64, Fault> {
        self.keys.get(index as usize).copied().ok_or(Fault::ArrayIndex)
    }
}

/// `MapIterator`'s position: everything but its wrapped-key list.
#[derive(Clone, Copy)]
pub(crate) struct Mark {
    pos: i32,
    last: i32,
    left: usize,
    zero: bool,
}

/// `MapIterator`, detached from the map so callbacks can mutate it between
/// steps exactly as they could the Java table.
pub(crate) struct Cursor {
    mark: Mark,
    /// Created on the first wrapped shift; reading it before is Java's NPE.
    /// Only removal through the cursor appends to it.
    wrapped: Option<Vec<i64>>,
}

impl Mark {
    /// The iterator's `pos` (the slot it read last).
    pub(crate) fn position(&self) -> i32 {
        self.pos
    }

    /// The iterator's `last`: the slot of the entry last returned, -1 if none.
    pub(crate) fn last(&self) -> i32 {
        self.last
    }

    /// The iterator's `c`: entries left, counted down as each `nextEntry` begins.
    pub(crate) fn left(&self) -> usize {
        self.left
    }

    pub(crate) fn has_next(&self) -> bool {
        self.left != 0
    }

    fn wrapped_key(&self, wrapped: &Option<Vec<i64>>) -> Result<i64, Fault> {
        let wrapped = wrapped.as_ref().ok_or(Fault::NullPointer)?;
        wrapped.get((-self.pos - 1) as usize).copied().ok_or(Fault::IndexOutOfBounds)
    }

    /// `nextEntry()` over `wrapped`: the entry's table index.
    pub(crate) fn next_entry(&mut self, wrapped: &Option<Vec<i64>>, map: &ListMap) -> Result<i32, Fault> {
        if self.left == 0 {
            return Err(Fault::NoSuchElement);
        }
        self.left -= 1;
        if self.zero {
            self.zero = false;
            self.last = map.n as i32;
            return Ok(self.last);
        }
        loop {
            self.pos -= 1;
            if self.pos < 0 {
                self.last = i32::MIN;
                let key = self.wrapped_key(wrapped)?;
                let mut p = mix(key) & map.mask();
                while key != map.keys[p] {
                    p = (p + 1) & map.mask();
                }
                return Ok(p as i32);
            }
            if map.key_at(self.pos)? != 0 {
                self.last = self.pos;
                return Ok(self.pos);
            }
        }
    }
}

impl Cursor {
    pub(crate) fn mark(&self) -> Mark {
        self.mark
    }

    pub(crate) fn reset(&mut self, mark: Mark) {
        self.mark = mark;
    }

    pub(crate) fn wrapped(&self) -> &Option<Vec<i64>> {
        &self.wrapped
    }

    pub(crate) fn has_next(&self) -> bool {
        self.mark.has_next()
    }

    /// `nextEntry()`: the entry's table index.
    pub(crate) fn next_entry(&mut self, map: &ListMap) -> Result<i32, Fault> {
        self.mark.next_entry(&self.wrapped, map)
    }

    /// `remove()`: the removed value.
    pub(crate) fn remove(&mut self, map: &mut ListMap) -> Result<u32, Fault> {
        if self.mark.last == -1 {
            return Err(Fault::IllegalState);
        }
        let value;
        if self.mark.last == map.n as i32 {
            map.zero = false;
            value = map.values[map.n];
            map.values[map.n] = NONE;
        } else if self.mark.pos >= 0 {
            let last = self.mark.last as usize;
            value = *map.values.get(last).ok_or(Fault::ArrayIndex)?;
            map.shift(last, Some(&mut self.wrapped));
        } else {
            let key = self.mark.wrapped_key(&self.wrapped)?;
            self.mark.last = -1;
            return map.remove(key);
        }
        map.size -= 1;
        self.mark.last = -1;
        Ok(value)
    }
}

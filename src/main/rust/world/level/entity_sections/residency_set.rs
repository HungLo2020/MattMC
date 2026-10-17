//! LongOpenHashSet-compatible residency membership and live removal cursors.
//! Adapted from fastutil 8.5.12 (Apache-2.0),
//! Copyright (C) 2002-2023 Sebastiano Vigna.
//! https://www.apache.org/licenses/LICENSE-2.0
use super::section_map::mix;
#[derive(Debug, PartialEq, Eq)]
pub(super) enum Failure {
    Exhausted,
    NoCurrent,
    ArrayBounds(i32, usize),
    MissingWrapped,
    ListBounds(i32, usize),
}
pub(super) struct LongSet {
    pub(super) keys: Vec<i64>,
    n: usize,
    pub(super) size: i32,
    zero: bool,
}
pub(super) struct Cursor {
    pos: i32,
    last: i32,
    pub(super) left: i32,
    zero: bool,
    wrapped: Option<Vec<i64>>,
}
impl Default for LongSet {
    fn default() -> Self {
        Self {
            keys: vec![0; 33],
            n: 32,
            size: 0,
            zero: false,
        }
    }
}
impl LongSet {
    fn max_fill(&self) -> i32 {
        (self.n * 3 / 4).min(self.n - 1) as i32
    }
    fn find(&self, key: i64) -> Result<usize, usize> {
        if key == 0 {
            return if self.zero { Ok(self.n) } else { Err(self.n) };
        }
        let mut i = mix(key) & (self.n - 1);
        loop {
            match self.keys[i] {
                0 => return Err(i),
                k if k == key => return Ok(i),
                _ => i = (i + 1) & (self.n - 1),
            }
        }
    }
    pub(super) fn contains(&self, key: i64) -> bool {
        self.find(key).is_ok()
    }
    pub(super) fn add(&mut self, key: i64) -> bool {
        let Err(i) = self.find(key) else { return false };
        if i == self.n {
            self.zero = true;
        }
        self.keys[i] = key;
        let old = self.size;
        self.size = self.size.wrapping_add(1);
        if old >= self.max_fill() {
            let n = (((self.size.wrapping_add(1)) as f64 / 0.75).ceil() as usize)
                .next_power_of_two()
                .max(2);
            self.rehash(n);
        }
        true
    }
    fn rehash(&mut self, n: usize) {
        let mut next = vec![0; n + 1];
        // Normal scalar set mutations cannot make size exceed occupied keys.
        // Iterator reentry may leave size smaller; Frozen copies only realSize.
        let mut i = self.n;
        let count = self.size.wrapping_sub(i32::from(self.zero));
        for _ in 0..count.max(0) {
            loop {
                i -= 1;
                if self.keys[i] != 0 {
                    break;
                }
            }
            let key = self.keys[i];
            let mut p = mix(key) & (n - 1);
            while next[p] != 0 {
                p = (p + 1) & (n - 1);
            }
            next[p] = key;
        }
        self.keys = next;
        self.n = n;
    }
    pub(super) fn remove(&mut self, key: i64) -> bool {
        let Ok(hole) = self.find(key) else {
            return false;
        };
        if hole == self.n {
            self.zero = false;
        } else {
            self.shift(hole, None)
                .expect("scalar set removal owns current table slot");
        }
        self.size = self.size.wrapping_sub(1);
        if self.n > 32 && self.size < self.max_fill() / 4 {
            self.rehash(self.n / 2);
        }
        true
    }
    pub(super) fn clear(&mut self) {
        self.keys.fill(0);
        self.zero = false;
        self.size = 0;
    }
    pub(super) fn cursor(&self) -> Cursor {
        Cursor {
            pos: self.n as i32,
            last: -1,
            left: self.size,
            zero: self.zero,
            wrapped: None,
        }
    }
    fn cell(&self, index: i32) -> Result<i64, Failure> {
        self.keys
            .get(index as usize)
            .copied()
            .ok_or(Failure::ArrayBounds(index, self.keys.len()))
    }
    fn shift(
        &mut self,
        mut hole: usize,
        mut wrapped: Option<&mut Option<Vec<i64>>>,
    ) -> Result<(), Failure> {
        loop {
            let mut pos = (hole + 1) & (self.n - 1);
            loop {
                let key = self.keys[pos];
                if key == 0 {
                    let length = self.keys.len();
                    *self
                        .keys
                        .get_mut(hole)
                        .ok_or(Failure::ArrayBounds(hole as i32, length))? = 0;
                    return Ok(());
                }
                let home = mix(key) & (self.n - 1);
                if if hole <= pos {
                    hole >= home || home > pos
                } else {
                    hole >= home && home > pos
                } {
                    break;
                }
                pos = (pos + 1) & (self.n - 1);
            }
            if pos < hole {
                if let Some(w) = wrapped.as_deref_mut() {
                    w.get_or_insert_with(|| Vec::with_capacity(2))
                        .push(self.keys[pos]);
                }
            }
            let key = self.keys[pos];
            let length = self.keys.len();
            *self
                .keys
                .get_mut(hole)
                .ok_or(Failure::ArrayBounds(hole as i32, length))? = key;
            hole = pos;
        }
    }
}
impl Cursor {
    fn wrapped_key(&self) -> Result<i64, Failure> {
        let index = self.pos.wrapping_neg().wrapping_sub(1);
        let Some(w) = &self.wrapped else {
            return Err(Failure::MissingWrapped);
        };
        w.get(index as usize)
            .copied()
            .ok_or(Failure::ListBounds(index, w.len()))
    }
    pub(super) fn next(&mut self, set: &LongSet) -> Result<i64, Failure> {
        if self.left == 0 {
            return Err(Failure::Exhausted);
        }
        self.left = self.left.wrapping_sub(1);
        if self.zero {
            self.zero = false;
            self.last = set.n as i32;
            return set.cell(set.n as i32);
        }
        loop {
            self.pos = self.pos.wrapping_sub(1);
            if self.pos < 0 {
                self.last = i32::MIN;
                return self.wrapped_key();
            }
            let key = set.cell(self.pos)?;
            if key != 0 {
                self.last = self.pos;
                return Ok(key);
            }
        }
    }
    pub(super) fn remove(&mut self, set: &mut LongSet) -> Result<(), Failure> {
        if self.last == -1 {
            return Err(Failure::NoCurrent);
        }
        if self.last == set.n as i32 {
            set.zero = false;
            set.keys[set.n] = 0;
        } else if self.pos >= 0 {
            set.shift(self.last as usize, Some(&mut self.wrapped))?;
        } else {
            let key = self.wrapped_key()?;
            set.remove(key);
            self.last = -1;
            return Ok(());
        }
        set.size = set.size.wrapping_sub(1);
        self.last = -1;
        Ok(())
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn cursor_removal_handles_collisions_wrapped_keys_and_zero() {
        let mut set = LongSet::default();
        for i in 0..2048 {
            set.add(i * 0x1312_3145);
        }
        let mut c = set.cursor();
        let mut removed = 0;
        while c.left != 0 {
            let key = c.next(&set).unwrap();
            assert!(set.contains(key));
            c.remove(&mut set).unwrap();
            assert!(!set.contains(key));
            removed += 1;
        }
        assert_eq!(removed, 2048);
        assert_eq!(set.size, 0);
    }
    #[test]
    fn callback_clear_before_remove_preserves_negative_size_prefix() {
        let mut set = LongSet::default();
        set.add(0);
        let mut c = set.cursor();
        assert_eq!(c.next(&set), Ok(0));
        set.clear();
        c.remove(&mut set).unwrap();
        assert_eq!(set.size, -1);
        assert!(set.add(17));
        assert_eq!(set.size, 0);
        assert!(set.contains(17));
    }
}

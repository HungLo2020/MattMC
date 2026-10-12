//! Physical section-key table, including computeIfAbsent's saved insertion slot.
//! Adapted from fastutil 8.5.12 Long2ObjectOpenHashMap (Apache-2.0),
//! Copyright (C) 2002-2023 Sebastiano Vigna.
//! https://www.apache.org/licenses/LICENSE-2.0

#[derive(Clone, Copy, Default)]
pub(super) struct Cell {
    pub(super) key: i64,
    pub(super) token: u64,
}

#[derive(Debug, PartialEq, Eq)]
pub(super) struct Bounds {
    pub(super) index: i32,
    pub(super) length: usize,
}

pub(super) struct SectionMap {
    pub(super) cells: Vec<Cell>,
    n: usize,
    size: usize,
    zero: bool,
}

pub(super) fn mix(key: i64) -> usize {
    let h = (key as u64).wrapping_mul(0x9e37_79b9_7f4a_7c15);
    let h = h ^ (h >> 32);
    (h ^ (h >> 16)) as usize
}

impl Default for SectionMap {
    fn default() -> Self {
        Self {
            cells: vec![Cell::default(); 33],
            n: 32,
            size: 0,
            zero: false,
        }
    }
}

impl SectionMap {
    pub(super) fn len(&self) -> usize {
        self.size
    }
    pub(super) fn put(&mut self, key: i64, token: u64) -> Result<u64, Bounds> {
        match self.find(key) {
            Ok(i) => Ok(std::mem::replace(&mut self.cells[i].token, token)),
            Err(i) => {
                let mut old = 0;
                self.insert_at(i, key, token, &mut old)?;
                Ok(old)
            }
        }
    }
    fn max_fill(&self) -> usize {
        (self.n * 3 / 4).min(self.n - 1)
    }

    /// Callers save Err(slot) BEFORE the Java visibility callback.
    pub(super) fn find(&self, key: i64) -> Result<usize, usize> {
        if key == 0 {
            return if self.zero { Ok(self.n) } else { Err(self.n) };
        }
        let mut i = mix(key) & (self.n - 1);
        loop {
            match self.cells[i].key {
                0 => return Err(i),
                found if found == key => return Ok(i),
                _ => i = (i + 1) & (self.n - 1),
            }
        }
    }

    pub(super) fn get(&self, key: i64) -> u64 {
        self.find(key).map_or(0, |i| self.cells[i].token)
    }

    /// A callback may have replaced or shrunk the table. Do not re-find key:
    /// Frozen inserts into its captured physical slot in the *current* array.
    /// `overwritten` lets the owner release the displaced behavior binding,
    /// even when a later rehash fails after committing the insertion prefix.
    pub(super) fn insert_at(
        &mut self,
        slot: usize,
        key: i64,
        token: u64,
        overwritten: &mut u64,
    ) -> Result<(), Bounds> {
        if slot == self.n {
            self.zero = true;
        }
        let length = self.cells.len();
        let cell = self.cells.get_mut(slot).ok_or(Bounds {
            index: slot as i32,
            length,
        })?;
        *overwritten = cell.token;
        *cell = Cell { key, token };
        let old_size = self.size;
        self.size += 1;
        if old_size >= self.max_fill() {
            let needed = (((self.size + 1) as f64 / 0.75).ceil() as usize)
                .next_power_of_two()
                .max(2);
            self.rehash(needed)?;
        }
        Ok(())
    }

    pub(super) fn remove(&mut self, key: i64) -> Result<u64, Bounds> {
        let Ok(mut hole) = self.find(key) else {
            return Ok(0);
        };
        let old = self.cells[hole].token;
        self.cells[hole].token = 0;
        self.size -= 1;
        if hole == self.n {
            self.zero = false;
        } else {
            loop {
                let mut pos = (hole + 1) & (self.n - 1);
                loop {
                    let key = self.cells[pos].key;
                    if key == 0 {
                        self.cells[hole] = Cell::default();
                        break;
                    }
                    let home = mix(key) & (self.n - 1);
                    let shift = if hole <= pos {
                        hole >= home || home > pos
                    } else {
                        hole >= home && home > pos
                    };
                    if shift {
                        break;
                    }
                    pos = (pos + 1) & (self.n - 1);
                }
                if self.cells[pos].key == 0 {
                    break;
                }
                self.cells[hole] = self.cells[pos];
                hole = pos;
            }
        }
        if self.n > 32 && self.size < self.max_fill() / 4 {
            self.rehash(self.n / 2)?;
        }
        Ok(old)
    }

    fn rehash(&mut self, n: usize) -> Result<(), Bounds> {
        let mut next = vec![Cell::default(); n + 1];
        let mut i = self.n as i32;
        // Reentrant duplicate insertion can make logical size larger than the
        // occupied-key count. Preserve the old arrays and the VM failure prefix.
        for _ in 0..self.size - usize::from(self.zero) {
            loop {
                i -= 1;
                if i < 0 {
                    return Err(Bounds {
                        index: i,
                        length: self.cells.len(),
                    });
                }
                if self.cells[i as usize].key != 0 {
                    break;
                }
            }
            let cell = self.cells[i as usize];
            let mut pos = mix(cell.key) & (n - 1);
            while next[pos].key != 0 {
                pos = (pos + 1) & (n - 1);
            }
            next[pos] = cell;
        }
        next[n].token = self.cells[self.n].token;
        self.cells = next;
        self.n = n;
        Ok(())
    }

    /// Long2ObjectOpenHashMap.keySet().forEach: zero, then descending buckets.
    pub(super) fn keys(&self) -> impl Iterator<Item = i64> + '_ {
        self.zero
            .then_some(self.cells[self.n].key)
            .into_iter()
            .chain(
                self.cells[..self.n]
                    .iter()
                    .rev()
                    .filter(|cell| cell.key != 0)
                    .map(|cell| cell.key),
            )
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn collisions_resize_shrink_and_zero_keep_physical_values() {
        let mut map = SectionMap::default();
        let keys: Vec<_> = (0..300)
            .map(|i| (i as i64).wrapping_mul(0x31f0_2345_9876_1201))
            .collect();
        for (i, &key) in keys.iter().enumerate() {
            let slot = map.find(key).unwrap_err();
            let mut overwritten = 0;
            map.insert_at(slot, key, i as u64 + 1, &mut overwritten)
                .unwrap();
            assert_eq!(overwritten, 0);
        }
        for (i, &key) in keys.iter().enumerate() {
            assert_eq!(map.get(key), i as u64 + 1);
        }
        for (i, &key) in keys.iter().enumerate().rev() {
            assert_eq!(map.remove(key).unwrap(), i as u64 + 1);
        }
        assert_eq!(map.n, 32);
        assert_eq!(map.keys().count(), 0);
    }

    #[test]
    fn captured_slot_is_not_refound_after_nested_creation() {
        let mut map = SectionMap::default();
        let slot = map.find(17).unwrap_err();
        let mut old = 0;
        map.insert_at(slot, 17, 1, &mut old).unwrap();
        map.insert_at(slot, 17, 2, &mut old).unwrap();
        assert_eq!(old, 1);
        assert_eq!(map.get(17), 2);
        assert_eq!(map.size, 2);
        assert_eq!(map.keys().count(), 1);
    }
}

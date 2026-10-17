//! Open-addressed chunk-position map with fallible growth. Iteration order is
//! never observable: Java publishes its own ordered view from the change log.

/// Fastutil's primitive long mixing family; full-key equality decides membership.
fn mix(value: u64) -> u64 {
    let mixed = value.wrapping_mul(0x9e37_79b9_7f4a_7c15);
    let mixed = mixed ^ (mixed >> 32);
    mixed ^ (mixed >> 16)
}

#[derive(Debug, PartialEq)]
pub(crate) struct AllocationError;

pub(crate) struct PositionMap<V: Copy + Default> {
    keys: Vec<i64>,
    values: Vec<V>,
    used: Vec<bool>,
    len: usize,
}

impl<V: Copy + Default> PositionMap<V> {
    /// Capacity for `expected` entries at most half full, rounded to a power of two.
    pub fn with_expected(expected: usize) -> Result<Self, AllocationError> {
        let mut map = Self {
            keys: Vec::new(),
            values: Vec::new(),
            used: Vec::new(),
            len: 0,
        };
        map.allocate(expected.max(2).saturating_mul(2).next_power_of_two())?;
        Ok(map)
    }

    fn allocate(&mut self, slots: usize) -> Result<(), AllocationError> {
        let mut keys = Vec::new();
        let mut values = Vec::new();
        let mut used = Vec::new();
        keys.try_reserve_exact(slots).map_err(|_| AllocationError)?;
        values.try_reserve_exact(slots).map_err(|_| AllocationError)?;
        used.try_reserve_exact(slots).map_err(|_| AllocationError)?;
        keys.resize(slots, 0);
        values.resize(slots, V::default());
        used.resize(slots, false);
        let old_keys = std::mem::replace(&mut self.keys, keys);
        let old_values = std::mem::replace(&mut self.values, values);
        let old_used = std::mem::replace(&mut self.used, used);
        for slot in 0..old_used.len() {
            if old_used[slot] {
                let index = self.find(old_keys[slot]).unwrap_err();
                self.keys[index] = old_keys[slot];
                self.values[index] = old_values[slot];
                self.used[index] = true;
            }
        }
        Ok(())
    }

    fn mask(&self) -> usize {
        self.keys.len() - 1
    }

    /// `Ok(slot)` holds the key; `Err(slot)` is the free slot ending its probe.
    fn find(&self, key: i64) -> Result<usize, usize> {
        let mask = self.mask();
        let mut index = mix(key as u64) as usize & mask;
        while self.used[index] {
            if self.keys[index] == key {
                return Ok(index);
            }
            index = (index + 1) & mask;
        }
        Err(index)
    }

    #[cfg(test)]
    pub fn len(&self) -> usize {
        self.len
    }

    pub fn get(&self, key: i64) -> Option<V> {
        self.find(key).ok().map(|index| self.values[index])
    }

    pub fn contains(&self, key: i64) -> bool {
        self.find(key).is_ok()
    }

    /// Grows before insertion so a failed allocation leaves the map unchanged.
    pub fn insert(&mut self, key: i64, value: V) -> Result<(), AllocationError> {
        if let Ok(index) = self.find(key) {
            self.values[index] = value;
            return Ok(());
        }
        if (self.len + 1) * 2 > self.keys.len() {
            let slots = self.keys.len().checked_mul(2).ok_or(AllocationError)?;
            self.allocate(slots)?;
        }
        let index = self.find(key).unwrap_err();
        self.keys[index] = key;
        self.values[index] = value;
        self.used[index] = true;
        self.len += 1;
        Ok(())
    }

    /// Removes with backward shifting, keeping every probe chain contiguous.
    pub fn remove(&mut self, key: i64) -> Option<V> {
        let mut hole = self.find(key).ok()?;
        let removed = self.values[hole];
        let mask = self.mask();
        let mut index = hole;
        loop {
            index = (index + 1) & mask;
            if !self.used[index] {
                break;
            }
            let home = mix(self.keys[index] as u64) as usize & mask;
            // Move the entry back when the hole lies on its probe path.
            let distance_to_entry = index.wrapping_sub(home) & mask;
            let distance_to_hole = hole.wrapping_sub(home) & mask;
            if distance_to_hole < distance_to_entry {
                self.keys[hole] = self.keys[index];
                self.values[hole] = self.values[index];
                hole = index;
            }
        }
        self.used[hole] = false;
        self.len -= 1;
        Some(removed)
    }
}

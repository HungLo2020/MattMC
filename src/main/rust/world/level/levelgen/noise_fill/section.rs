//! Exact replay of block-state `PalettedContainer` writes into a fresh section:
//! a single-value air palette that grows to the 4-bit linear palette, the 5..8
//! bit hash-map palettes (ids in insertion order) and the global palette. Each
//! resize rebuilds the palette by first occurrence in storage index order, as
//! `PalettedContainer.Data.copyFrom` does, so palette order and storage match.

pub(crate) const ENTRIES: usize = 4096;

#[derive(Clone, Copy, Debug, PartialEq)]
enum Kind {
    Single,
    Linear,
    Hash(u32),
    Global,
}

pub(crate) struct Section {
    kind: Kind,
    /// Configuration bit count Java requested for the current palette.
    requested: u32,
    palette: Vec<i32>,
    storage: Vec<u32>,
    global_bits: u32,
    pub non_empty: i32,
    pub ticking: i32,
    pub fluid: i32,
}

impl Section {
    /// `new PalettedContainer(AIR, strategy)`: zero bits, single value air.
    pub fn new(air: i32, global_bits: u32) -> Self {
        Self {
            kind: Kind::Single,
            requested: 0,
            palette: vec![air],
            storage: vec![0; ENTRIES],
            global_bits,
            non_empty: 0,
            ticking: 0,
            fluid: 0,
        }
    }

    /// `getAndSetUnchecked(index, state)` on a position not yet written.
    pub fn set(&mut self, index: usize, state: i32) {
        let id = self.id_for(state);
        self.storage[index] = id;
    }

    fn value_for(&self, id: u32) -> i32 {
        match self.kind {
            Kind::Global => id as i32,
            _ => self.palette[id as usize],
        }
    }

    fn id_for(&mut self, state: i32) -> u32 {
        match self.kind {
            Kind::Single => {
                if self.palette[0] == state {
                    0
                } else {
                    self.resize(1, state)
                }
            }
            Kind::Linear => match self.palette.iter().position(|value| *value == state) {
                Some(id) => id as u32,
                None if self.palette.len() < 16 => {
                    self.palette.push(state);
                    (self.palette.len() - 1) as u32
                }
                None => self.resize(5, state),
            },
            Kind::Hash(bits) => match self.palette.iter().position(|value| *value == state) {
                Some(id) => id as u32,
                None => {
                    // HashMapPalette adds before checking capacity.
                    let id = self.palette.len() as u32;
                    self.palette.push(state);
                    if id >= 1 << bits {
                        self.resize(bits + 1, state)
                    } else {
                        id
                    }
                }
            },
            Kind::Global => state as u32,
        }
    }

    /// `PalettedContainer.onResize(requested, state)`.
    fn resize(&mut self, requested: u32, state: i32) -> u32 {
        let kind = match requested {
            1..=4 => Kind::Linear,
            5..=8 => Kind::Hash(requested),
            _ => Kind::Global,
        };
        let values: Vec<i32> = self.storage.iter().map(|id| self.value_for(*id)).collect();
        self.kind = kind;
        self.requested = match kind {
            Kind::Linear => 4,
            _ => requested,
        };
        self.palette.clear();
        for (index, value) in values.into_iter().enumerate() {
            self.storage[index] = self.append(value);
        }
        self.append(state)
    }

    /// `idFor` on a palette with capacity to spare (`noResizeExpected`).
    fn append(&mut self, state: i32) -> u32 {
        if self.kind == Kind::Global {
            return state as u32;
        }
        match self.palette.iter().position(|value| *value == state) {
            Some(id) => id as u32,
            None => {
                self.palette.push(state);
                (self.palette.len() - 1) as u32
            }
        }
    }

    pub fn requested_bits(&self) -> u32 {
        self.requested
    }

    /// Bits per entry in storage (`Configuration.bitsInMemory`).
    pub fn storage_bits(&self) -> u32 {
        match self.kind {
            Kind::Single => 0,
            Kind::Linear => 4,
            Kind::Hash(bits) => bits,
            Kind::Global => self.global_bits,
        }
    }

    /// Palette entries in id order; empty for the global palette.
    pub fn palette(&self) -> &[i32] {
        if self.kind == Kind::Global {
            &[]
        } else {
            &self.palette
        }
    }

    pub fn packed(&self) -> Vec<i64> {
        pack(&self.storage, self.storage_bits())
    }
}

/// `SimpleBitStorage` layout: `64 / bits` values per long, low bits first,
/// no value spanning two longs. Zero bits pack to an empty array.
pub(crate) fn pack(values: &[u32], bits: u32) -> Vec<i64> {
    if bits == 0 {
        return Vec::new();
    }
    let per_long = (64 / bits) as usize;
    let mut raw = vec![0i64; values.len().div_ceil(per_long)];
    let mask = if bits == 64 { u64::MAX } else { (1u64 << bits) - 1 };
    for (index, value) in values.iter().enumerate() {
        let cell = index / per_long;
        let shift = (index - cell * per_long) as u32 * bits;
        raw[cell] = (raw[cell] as u64 | ((*value as u64 & mask) << shift)) as i64;
    }
    raw
}

//! Rust-owned block storage of one `ProtoChunk` for the generation stages that
//! run on it (SURFACE, CARVERS). The chunk's sections, its two world-generation
//! heightmaps and the stage's post-processing marks move here when a stage
//! starts; every write is `ProtoChunk.setBlockState` before light
//! initialization: the `PalettedContainer` replay, the section counters and
//! both heightmaps. Java installs the modified sections, heightmaps and marks
//! once when the stage ends.
pub(crate) mod ffi;
#[cfg(test)]
mod tests;

use std::cell::{Cell, OnceCell, RefCell};

use crate::world::level::levelgen::noise_fill::section::{pack, Section, ENTRIES};
use crate::world::level::levelgen::noise_fill::{ceil_log2, FLAG_AIR, FLAG_BLOCKS_MOTION, FLAG_FLUID, FLAG_RANDOM_TICKS};

/// `is(Blocks.AIR)`: the air block itself, for ProtoChunk's empty-section shortcut.
pub(crate) const FLAG_AIR_BLOCK: u8 = 16;

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    Input,
    State(i32),
}

/// A section as Java exported it, unpacked on first block access.
struct Packed {
    kind: u32,
    bits: u32,
    palette: Vec<i32>,
    raw: Vec<i64>,
    global_bits: u32,
}

pub(crate) struct ProtoStorage {
    min_y: i32,
    height: i32,
    sections: Vec<OnceCell<Section>>,
    packed: Vec<RefCell<Option<Packed>>>,
    /// Counters of sections not yet unpacked: non-empty, ticking, fluid.
    counts: Vec<[i32; 3]>,
    /// Set when a lazily unpacked section was invalid; install then refuses.
    invalid: Cell<bool>,
    modified: Vec<bool>,
    flags: &'static [u8],
    /// `getFirstAvailable - minY` per column: WORLD_SURFACE_WG, OCEAN_FLOOR_WG.
    world_surface: [u32; 256],
    ocean_floor: [u32; 256],
    post_process: Vec<[i32; 3]>,
}

fn unpack(raw: &[i64], bits: u32, count: usize) -> Option<Vec<u32>> {
    if bits == 0 {
        return Some(vec![0; count]);
    }
    let per_long = (64 / bits) as usize;
    if raw.len() != count.div_ceil(per_long) {
        return None;
    }
    let mask = (1u64 << bits) - 1;
    // SimpleBitStorage: `per_long` values per word, low bits first.
    let mut out = Vec::with_capacity(count);
    for &word in raw {
        let mut word = word as u64;
        for _ in 0..per_long.min(count - out.len()) {
            out.push((word & mask) as u32);
            word >>= bits;
        }
    }
    Some(out)
}

impl ProtoStorage {
    /// `sections`: per section (kind, storage bits, palette, raw storage,
    /// counters); heightmaps as raw longs.
    pub(crate) fn new(
        min_y: i32,
        height: i32,
        sections: Vec<(u32, u32, Vec<i32>, Vec<i64>, [i32; 3])>,
        global_bits: u32,
        flags: &'static [u8],
        world_surface: &[i64],
        ocean_floor: &[i64],
    ) -> Result<Self, Error> {
        if height <= 0 || height > 4096 || sections.len() as i32 * 16 != height || flags.is_empty() {
            return Err(Error::Input);
        }
        let mut packed = Vec::with_capacity(sections.len());
        let mut counts = Vec::with_capacity(sections.len());
        for (kind, bits, palette, raw, count) in sections {
            // The header must describe a modelled palette and complete storage;
            // entries are checked against the palette when the section unpacks.
            let words = if bits == 0 { 0 } else { ENTRIES.div_ceil((64 / bits.min(64)) as usize) };
            let header = match (kind, bits) {
                (0, 0) => palette.len() == 1,
                (1, 4) => (1..=16).contains(&palette.len()),
                (2, 5..=8) => !palette.is_empty() && palette.len() <= 1 << bits,
                (3, b) => b == global_bits && palette.is_empty(),
                _ => false,
            };
            if !header || raw.len() != words {
                return Err(Error::Input);
            }
            counts.push(count);
            packed.push(RefCell::new(Some(Packed { kind, bits, palette, raw, global_bits })));
        }
        let bits = ceil_log2(height as u32 + 1);
        let heights = |raw: &[i64]| -> Result<[u32; 256], Error> {
            let values = unpack(raw, bits, 256).ok_or(Error::Input)?;
            Ok(std::array::from_fn(|index| values[index]))
        };
        let count = packed.len();
        Ok(Self {
            min_y,
            height,
            sections: (0..count).map(|_| OnceCell::new()).collect(),
            packed,
            counts,
            invalid: Cell::new(false),
            modified: vec![false; count],
            flags,
            world_surface: heights(world_surface)?,
            ocean_floor: heights(ocean_floor)?,
            post_process: Vec::new(),
        })
    }

    /// Section `index`, unpacked on first use. An invalid export unpacks as
    /// air and marks the storage invalid.
    fn loaded(&self, index: usize) -> &Section {
        self.sections[index].get_or_init(|| {
            let p = self.packed[index].borrow_mut().take().expect("a section unpacks once");
            let counts = self.counts[index];
            let section = unpack(&p.raw, p.bits, ENTRIES).and_then(|values| Section::load(p.kind, p.bits, p.palette, values, p.global_bits, counts));
            section.unwrap_or_else(|| {
                self.invalid.set(true);
                Section::load(0, 0, vec![0], vec![0; ENTRIES], p.global_bits, [0; 3]).expect("an air section")
            })
        })
    }

    /// Whether the export was valid wherever it was unpacked.
    pub(crate) fn valid(&self) -> bool {
        !self.invalid.get()
    }

    /// A section's non-empty count, without unpacking it.
    pub(crate) fn non_empty(&self, index: usize) -> i32 {
        match self.sections[index].get() {
            Some(section) => section.non_empty,
            None => self.counts[index][0],
        }
    }

    pub(crate) fn min_y(&self) -> i32 {
        self.min_y
    }

    /// The state's flag bits (air, fluid, motion, ticking, air block).
    pub(crate) fn flag(&self, state: i32) -> Result<u8, Error> {
        self.flags.get(usize::try_from(state).map_err(|_| Error::State(state))?).copied().ok_or(Error::State(state))
    }

    /// `ChunkAccess.getHeight(WORLD_SURFACE_WG, x, z)`.
    pub(crate) fn height(&self, x: i32, z: i32) -> i32 {
        self.world_surface[((z & 15) * 16 + (x & 15)) as usize] as i32 + self.min_y - 1
    }

    fn index_of(&self, y: i32) -> Option<usize> {
        if y < self.min_y {
            return None;
        }
        let index = ((y - self.min_y) >> 4) as usize;
        (index < self.sections.len()).then_some(index)
    }

    /// The section holding Y with its non-empty count, unpacked only when it
    /// holds a non-air block.
    pub(crate) fn section_at(&self, y: i32) -> Option<&Section> {
        let index = self.index_of(y)?;
        (self.non_empty(index) != 0).then(|| self.loaded(index))
    }

    /// The state id at (x, y, z); -1 outside the sections, -2 in a section
    /// with only air (ProtoChunk.getBlockState answers AIR there).
    pub(crate) fn get(&self, x: i32, y: i32, z: i32) -> i32 {
        let Some(index) = self.index_of(y) else { return -1 };
        if self.non_empty(index) == 0 {
            return -2;
        }
        self.loaded(index).get((((y & 15) << 8) | ((z & 15) << 4) | (x & 15)) as usize)
    }

    /// `ProtoChunk.setBlockState` before light initialization: nothing outside
    /// the build height or for AIR into an only-air section; otherwise the
    /// write, the section counters and both heightmaps.
    pub(crate) fn set_block_state(&mut self, x: i32, y: i32, z: i32, state: i32) -> Result<(), Error> {
        if y < self.min_y || y >= self.min_y + self.height {
            return Ok(());
        }
        let flags = self.flag(state)?;
        let section_index = ((y - self.min_y) >> 4) as usize;
        let index = (((y & 15) << 8) | ((z & 15) << 4) | (x & 15)) as usize;
        if self.non_empty(section_index) == 0 && flags & FLAG_AIR_BLOCK != 0 {
            return Ok(());
        }
        let old = self.loaded(section_index).get(index);
        let old_flags = self.flag(old)?;
        let section = self.sections[section_index].get_mut().expect("loaded above");
        section.set(index, state);
        // LevelChunkSection.setBlockState's counters.
        if old_flags & FLAG_AIR == 0 {
            section.non_empty -= 1;
            if old_flags & FLAG_RANDOM_TICKS != 0 {
                section.ticking -= 1;
            }
        }
        if old_flags & FLAG_FLUID != 0 {
            section.fluid -= 1;
        }
        if flags & FLAG_AIR == 0 {
            section.non_empty += 1;
            if flags & FLAG_RANDOM_TICKS != 0 {
                section.ticking += 1;
            }
        }
        if flags & FLAG_FLUID != 0 {
            section.fluid += 1;
        }
        self.modified[section_index] = true;
        // heightmapsAfter() iterates WORLD_SURFACE_WG, then OCEAN_FLOOR_WG.
        self.update_heightmap(false, x, y, z, flags & FLAG_AIR == 0);
        self.update_heightmap(true, x, y, z, flags & FLAG_BLOCKS_MOTION != 0);
        Ok(())
    }

    /// `ChunkAccess.markPosForPostprocessing`, in call order.
    pub(crate) fn mark(&mut self, x: i32, y: i32, z: i32) {
        self.post_process.push([x, y, z]);
    }

    /// `Heightmap.update(x, y, z, state)`.
    fn update_heightmap(&mut self, ocean_floor: bool, x: i32, y: i32, z: i32, opaque: bool) {
        let column = ((z & 15) * 16 + (x & 15)) as usize;
        let first = self.heights(ocean_floor)[column] as i32 + self.min_y;
        if y <= first - 2 {
            return;
        }
        if opaque {
            if y >= first {
                self.heights_mut(ocean_floor)[column] = (y + 1 - self.min_y) as u32;
            }
        } else if first - 1 == y {
            let mut found = self.min_y;
            for below in (self.min_y..y).rev() {
                let state = self.get(x, below, z);
                // An only-air section reads AIR (-2), which neither heightmap counts.
                let flags = if state < 0 { FLAG_AIR } else { self.flags.get(state as usize).copied().unwrap_or(0) };
                let matches = if ocean_floor { flags & FLAG_BLOCKS_MOTION != 0 } else { flags & FLAG_AIR == 0 };
                if matches {
                    found = below + 1;
                    break;
                }
            }
            self.heights_mut(ocean_floor)[column] = (found - self.min_y) as u32;
        }
    }

    fn heights(&self, ocean_floor: bool) -> &[u32; 256] {
        if ocean_floor { &self.ocean_floor } else { &self.world_surface }
    }

    fn heights_mut(&mut self, ocean_floor: bool) -> &mut [u32; 256] {
        if ocean_floor { &mut self.ocean_floor } else { &mut self.world_surface }
    }

    pub(crate) fn section_count(&self) -> usize {
        self.sections.len()
    }

    /// A modified section's result: (requested bits, palette, raw, counters).
    pub(crate) fn section(&self, index: usize) -> Option<(u32, &[i32], Vec<i64>, [i32; 3])> {
        if !self.modified.get(index).copied().unwrap_or(false) {
            return None;
        }
        let s = self.sections[index].get()?;
        Some((s.requested_bits(), s.palette(), s.packed(), [s.non_empty, s.ticking, s.fluid]))
    }

    pub(crate) fn heightmap_raw(&self, ocean_floor: bool) -> Vec<i64> {
        pack(self.heights(ocean_floor), ceil_log2(self.height as u32 + 1))
    }

    pub(crate) fn post_process(&self) -> &[[i32; 3]] {
        &self.post_process
    }
}

//! One `LightEngine.runLightUpdates()` propagation pass: the decrease queue,
//! then the increase queue, for block or sky light. Java hands over both
//! queues; sections fault in through [`Source`] on first use and the stored
//! levels Rust wrote install once at the end, so an error leaves Java's
//! storage untouched and Java reruns the pass from the same queues.
pub(crate) mod ffi;
mod seed;
#[cfg(test)]
mod tests;

use crate::content::block::{BlockRegistry, StateFlags, StateId};
use std::collections::{HashMap, VecDeque};
use std::sync::OnceLock;
use std::hash::{BuildHasherDefault, Hasher};

const LAYER: usize = 2048;
const DIRECTIONS: u64 = 1008;
const FROM_EMPTY_SHAPE: u64 = 1024;
const FROM_EMISSION: u64 = 2048;
// Direction.values() order: DOWN, UP, NORTH, SOUTH, WEST, EAST.
const OFFSETS: [(i32, i32, i32); 6] = [(0, -1, 0), (0, 1, 0), (0, 0, -1), (0, 0, 1), (-1, 0, 0), (1, 0, 0)];

/// Why a pass stopped; either way Java reruns it.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Error {
    /// Input the original would reject or that is not modelled (Java NPEs,
    /// malformed palettes, unknown states).
    Unsupported,
    /// Java's section callback failed.
    Callback,
}

/// Light properties of a block state: `max(1, getLightBlock())`,
/// `getLightEmission()`, `isEmptyShape()` and the face ID of
/// `getOcclusionShape(state, direction)` for each direction.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub(crate) struct Type {
    pub opacity: u8,
    pub emission: u8,
    pub empty: bool,
    pub faces: [u16; 6],
}

/// Immutable block-state tables: state id to type, the types, and
/// `Shapes.faceShapeOccludes` over face IDs.
pub(crate) struct Tables {
    pub state_types: Vec<u16>,
    pub types: Vec<Type>,
    pub faces: usize,
    pub occludes: Vec<u8>,
    pub air: u16,
}

/// The type of states Rust must not see (custom `BlockState` subclasses).
pub(crate) const UNSUPPORTED: u16 = u16::MAX;
/// Most distinct faces the tables accept.
const MAX_FACES: usize = 4096;

impl Tables {
    /// The light types of `registry`'s states, numbered in state order.
    /// `None` when there are too many types or faces.
    pub(crate) fn from_registry(registry: &BlockRegistry) -> Option<Tables> {
        let faces = registry.face_count();
        if faces > MAX_FACES {
            return None;
        }
        let mut ids: HashMap<Type, u16> = HashMap::new();
        let mut types = Vec::new();
        let mut state_types = Vec::with_capacity(registry.state_count());
        for s in 0..registry.state_count() {
            let state = StateId(s as u16);
            let flags = registry.flags(state);
            if flags.contains(StateFlags::CUSTOM) {
                state_types.push(UNSUPPORTED);
                continue;
            }
            let t = Type {
                opacity: registry.light_block(state).max(1),
                emission: registry.emission(state),
                empty: flags.contains(StateFlags::LIGHT_EMPTY_SHAPE),
                faces: registry.light_face_column()[s].map(|f| f.0),
            };
            let next = types.len();
            let id = *ids.entry(t).or_insert_with(|| {
                types.push(t);
                next as u16
            });
            if types.len() >= UNSUPPORTED as usize {
                return None;
            }
            state_types.push(id);
        }
        let air = registry.block(registry.air()?).default_state();
        let air = *state_types.get(air.index()).filter(|&&t| t != UNSUPPORTED)?;
        Some(Tables { state_types, types, faces, occludes: registry.face_matrix().to_vec(), air })
    }

    /// The type of a state id from Java, [`UNSUPPORTED`] for unknown ids.
    pub(crate) fn state_type(&self, id: u16) -> u16 {
        self.state_types.get(id as usize).copied().unwrap_or(UNSUPPORTED)
    }

    fn occludes(&self, from: u16, to: u16) -> bool {
        self.occludes[from as usize * self.faces + to as usize] != 0
    }
}

/// [`Tables::from_registry`] of the installed registry; `None` until it is
/// installed or when its types do not fit.
pub(crate) fn installed_tables() -> Option<&'static Tables> {
    static TABLES: OnceLock<Option<Tables>> = OnceLock::new();
    let registry = crate::content::block::installed()?;
    TABLES.get_or_init(|| Tables::from_registry(registry)).as_ref()
}

/// A section's block states as Java's `LightChunk.getBlockState` sees them.
pub(crate) enum Blocks<'a> {
    Uniform(u16),
    /// `SimpleBitStorage` words; `palette` maps ids to types, or `None` for
    /// global state ids.
    Packed { bits: u32, palette: Option<&'a [u16]>, words: &'a [u64] },
    /// One type per block, in section index order.
    Full(&'a [u16]),
}

/// Java's side of a pass.
pub(crate) trait Source {
    /// `getDataLayer(section, true)`: `None` when not stored, otherwise
    /// `lightOnInSection(section)` with the layer's bytes written to `layer`.
    fn layer(&mut self, section: i64, layer: &mut [u8; LAYER]) -> Result<Option<bool>, Error>;
    /// The block states of a stored section.
    fn blocks(&mut self, section: i64) -> Result<Blocks<'_>, Error>;
}

#[derive(Clone, Copy)]
enum Held {
    Unknown,
    Uniform(u16),
    Packed { bits: u32, global: bool, palette: usize, palette_len: usize, words: usize, word_len: usize },
    Full(usize),
}

struct Section {
    key: i64,
    stored: bool,
    light_on: bool,
    written: bool,
    affected: bool,
    layer: usize,
    blocks: Held,
}

#[derive(Default)]
struct Mix(u64);

impl Hasher for Mix {
    fn finish(&self) -> u64 {
        let mixed = self.0.wrapping_mul(0x9e3779b97f4a7c15);
        mixed ^ (mixed >> 32)
    }
    fn write(&mut self, _: &[u8]) {
        unreachable!("only i64 keys")
    }
    fn write_i64(&mut self, value: i64) {
        self.0 = value as u64;
    }
}

type Map<V> = HashMap<i64, V, BuildHasherDefault<Mix>>;

pub(crate) fn block_x(pos: i64) -> i32 {
    (pos >> 38) as i32
}

pub(crate) fn block_y(pos: i64) -> i32 {
    ((pos << 52) >> 52) as i32
}

pub(crate) fn block_z(pos: i64) -> i32 {
    ((pos << 26) >> 38) as i32
}

pub(crate) fn block_pos(x: i32, y: i32, z: i32) -> i64 {
    ((x as i64 & 0x3FF_FFFF) << 38) | (y as i64 & 0xFFF) | ((z as i64 & 0x3FF_FFFF) << 12)
}

pub(crate) fn section_pos(x: i32, y: i32, z: i32) -> i64 {
    ((x as i64 & 0x3F_FFFF) << 42) | (y as i64 & 0xF_FFFF) | ((z as i64 & 0x3F_FFFF) << 20)
}

fn section_of(pos: i64) -> i64 {
    section_pos(block_x(pos) >> 4, block_y(pos) >> 4, block_z(pos) >> 4)
}

fn offset(pos: i64, direction: usize) -> i64 {
    let (dx, dy, dz) = OFFSETS[direction];
    block_pos(block_x(pos) + dx, block_y(pos) + dy, block_z(pos) + dz)
}

fn index(pos: i64) -> usize {
    ((block_y(pos) & 15) << 8 | (block_z(pos) & 15) << 4 | (block_x(pos) & 15)) as usize
}

fn bit(direction: usize) -> u64 {
    1 << (direction + 4)
}

fn level(entry: u64) -> i32 {
    (entry & 15) as i32
}

fn with_level(entry: u64, level: i32) -> u64 {
    entry & !15 | (level as u64 & 15)
}

fn empty_flag(empty: bool) -> u64 {
    if empty { FROM_EMPTY_SHAPE } else { 0 }
}

fn decrease_skip_one(level: i32, direction: usize) -> u64 {
    with_level(DIRECTIONS & !bit(direction), level)
}

fn increase_from_emission(level: i32, empty: bool) -> u64 {
    with_level(DIRECTIONS | FROM_EMISSION | empty_flag(empty), level)
}

fn increase_skip_one(level: i32, empty: bool, direction: usize) -> u64 {
    with_level(DIRECTIONS & !bit(direction) | empty_flag(empty), level)
}

fn increase_only_one(level: i32, empty: bool, direction: usize) -> u64 {
    with_level(empty_flag(empty) | bit(direction), level)
}

/// `SkyLightEngine.crossedSectionEdge`.
fn crossed_section_edge(direction: usize, x: i32, z: i32) -> bool {
    match direction {
        2 => z == 15,
        3 => z == 0,
        4 => x == 15,
        5 => x == 0,
        _ => false,
    }
}

/// Reusable state for one Java light engine; a pass borrows it.
pub(crate) struct Engine {
    sky: bool,
    sections: Vec<Section>,
    index: Map<u32>,
    last: [(i64, u32); 2],
    layers: Vec<[u8; LAYER]>,
    palettes: Vec<u16>,
    words: Vec<u64>,
    full: Vec<u16>,
    decreases: VecDeque<(i64, u64)>,
    increases: VecDeque<(i64, u64)>,
    affected: Map<()>,
    affected_order: Vec<i64>,
    lowest: i32,
}

/// What a pass leaves for Java to install.
pub(crate) struct Outcome {
    pub processed: i64,
}

impl Engine {
    pub(crate) fn new(sky: bool) -> Self {
        Self {
            sky,
            sections: Vec::new(),
            index: Map::default(),
            last: [(i64::MAX, 0); 2],
            layers: Vec::new(),
            palettes: Vec::new(),
            words: Vec::new(),
            full: Vec::new(),
            decreases: VecDeque::new(),
            increases: VecDeque::new(),
            affected: Map::default(),
            affected_order: Vec::new(),
            lowest: i32::MAX,
        }
    }

    fn reset(&mut self) {
        self.sections.clear();
        self.index.clear();
        self.last = [(i64::MAX, 0); 2];
        self.palettes.clear();
        self.words.clear();
        self.full.clear();
        self.decreases.clear();
        self.increases.clear();
        self.affected.clear();
        self.affected_order.clear();
    }

    /// Runs `propagateDecreases()` then `propagateIncreases()` over the given
    /// queues (position, entry pairs). `lowest` is the sky storage's
    /// `currentLowestY`, behind `hasLightDataAtOrBelow`.
    pub(crate) fn run(&mut self, tables: &Tables, source: &mut dyn Source, decreases: &[i64], increases: &[i64], lowest: i32) -> Result<Outcome, Error> {
        self.reset();
        self.lowest = lowest;
        for pair in decreases.chunks_exact(2) {
            self.decreases.push_back((pair[0], pair[1] as u64));
        }
        for pair in increases.chunks_exact(2) {
            self.increases.push_back((pair[0], pair[1] as u64));
        }
        let mut pass = Pass { engine: self, tables, source };
        let mut processed = 0i64;
        while let Some((pos, entry)) = pass.engine.decreases.pop_front() {
            processed += 1;
            if pass.engine.sky {
                pass.sky_decrease(pos, entry)?;
            } else {
                pass.block_decrease(pos, entry)?;
            }
        }
        while let Some((pos, entry)) = pass.engine.increases.pop_front() {
            processed += 1;
            let own = pass.stored(pos)?;
            let oi = index(pos);
            let mut stored = pass.level_at(own, oi);
            let from = level(entry);
            if entry & FROM_EMISSION != 0 && stored < from {
                pass.set_at(own, oi, pos, from);
                stored = from;
            }
            if stored == from {
                if pass.engine.sky {
                    pass.sky_increase(pos, own, oi, entry, stored)?;
                } else {
                    pass.block_increase(pos, own, oi, entry, stored)?;
                }
            }
        }
        Ok(Outcome { processed })
    }

    /// Sections written by the last pass, in first-faulted order, with their bytes.
    pub(crate) fn written(&self) -> impl Iterator<Item = (i64, &[u8; LAYER])> {
        self.sections.iter().filter(|s| s.written).map(|s| (s.key, &self.layers[s.layer]))
    }

    /// `sectionsAffectedByLightUpdates` additions of the last pass.
    pub(crate) fn affected(&self) -> &[i64] {
        &self.affected_order
    }

    pub(crate) fn written_count(&self) -> usize {
        self.sections.iter().filter(|s| s.written).count()
    }

}

struct Pass<'a> {
    engine: &'a mut Engine,
    tables: &'a Tables,
    source: &'a mut dyn Source,
}

impl Pass<'_> {
    #[inline]
    fn section(&mut self, key: i64) -> Result<usize, Error> {
        let e = &mut *self.engine;
        if e.last[0].0 == key {
            return Ok(e.last[0].1 as usize);
        }
        if e.last[1].0 == key {
            e.last.swap(0, 1);
            return Ok(e.last[0].1 as usize);
        }
        let at = match e.index.get(&key) {
            Some(&at) => at,
            None => {
                let at = e.sections.len();
                if e.layers.len() <= at {
                    e.layers.push([0; LAYER]);
                }
                let light_on = self.source.layer(key, &mut e.layers[at])?;
                e.sections.push(Section {
                    key,
                    stored: light_on.is_some(),
                    light_on: light_on.unwrap_or(false),
                    written: false,
                    affected: false,
                    layer: at,
                    blocks: Held::Unknown,
                });
                e.index.insert(key, at as u32);
                at as u32
            }
        };
        e.last[1] = e.last[0];
        e.last[0] = (key, at);
        Ok(at as usize)
    }

    fn storing(&mut self, key: i64) -> Result<bool, Error> {
        let at = self.section(key)?;
        Ok(self.engine.sections[at].stored)
    }

    fn stored(&mut self, pos: i64) -> Result<usize, Error> {
        let at = self.section(section_of(pos))?;
        if self.engine.sections[at].stored { Ok(at) } else { Err(Error::Unsupported) }
    }

    /// The slot of `offset(pos, direction)`: `own` (the slot of `pos`) unless
    /// the move leaves the section.
    /// With its index in that section, derived from `i` (the index of `pos`).
    #[inline]
    fn neighbor(&mut self, own: usize, i: usize, next: i64, direction: usize) -> Result<(usize, usize), Error> {
        let crossing = match direction {
            0 => i >> 8 == 0,
            1 => i >> 8 == 15,
            2 => i >> 4 & 15 == 0,
            3 => i >> 4 & 15 == 15,
            4 => i & 15 == 0,
            _ => i & 15 == 15,
        };
        if crossing {
            Ok((self.section(section_of(next))?, index(next)))
        } else {
            Ok((own, (i as isize + [-256, 256, -16, 16, -1, 1][direction]) as usize))
        }
    }

    fn is_stored(&self, at: usize) -> bool {
        self.engine.sections[at].stored
    }

    /// `getStoredLevel` of index `i` in the stored section at `at`.
    #[inline]
    fn level_at(&self, at: usize, i: usize) -> i32 {
        (self.engine.layers[self.engine.sections[at].layer][i >> 1] >> ((i & 1) * 4) & 15) as i32
    }

    /// `setStoredLevel`, including `sectionsAffectedByLightUpdates`.
    fn set(&mut self, pos: i64, value: i32) -> Result<(), Error> {
        let at = self.stored(pos)?;
        self.set_at(at, index(pos), pos, value);
        Ok(())
    }

    /// `setStoredLevel` of a position in the stored section at `at`.
    #[inline]
    fn set_at(&mut self, at: usize, i: usize, pos: i64, value: i32) {
        let e = &mut *self.engine;
        let section = &mut e.sections[at];
        section.written = true;
        let byte = &mut e.layers[section.layer][i >> 1];
        let shift = (i & 1) * 4;
        *byte = *byte & !(15 << shift) | ((value as u8 & 15) << shift);
        let (x, y, z) = (i & 15, i >> 8, i >> 4 & 15);
        if (1..15).contains(&x) && (1..15).contains(&y) && (1..15).contains(&z) {
            if !section.affected {
                // Interior positions: one dedup per section instead of per write.
                section.affected = true;
                let key = section.key;
                e.add_affected(key);
            }
            return;
        }
        let (x, y, z) = (block_x(pos), block_y(pos), block_z(pos));
        let (lx, hx, ly, hy, lz, hz) = ((x - 1) >> 4, (x + 1) >> 4, (y - 1) >> 4, (y + 1) >> 4, (z - 1) >> 4, (z + 1) >> 4);
        // aroundAndAtBlockPos: a position on a section face also touches its neighbours.
        for sx in lx..=hx {
            for sy in ly..=hy {
                for sz in lz..=hz {
                    e.add_affected(section_pos(sx, sy, sz));
                }
            }
        }
    }

    #[cold]
    fn load_blocks(&mut self, at: usize) -> Result<(), Error> {
        {
            let key = self.engine.sections[at].key;
            let held = {
                let e = &mut *self.engine;
                match self.source.blocks(key)? {
                    Blocks::Uniform(t) => Held::Uniform(t),
                    Blocks::Packed { bits, palette, words } => {
                        if !(1..=32).contains(&bits) || words.len() != 4096usize.div_ceil(64 / bits as usize) {
                            return Err(Error::Unsupported);
                        }
                        let start = e.palettes.len();
                        let len = palette.map_or(0, |p| p.len());
                        if let Some(p) = palette {
                            e.palettes.extend_from_slice(p);
                        }
                        let word_start = e.words.len();
                        e.words.extend_from_slice(words);
                        Held::Packed { bits, global: palette.is_none(), palette: start, palette_len: len, words: word_start, word_len: words.len() }
                    }
                    Blocks::Full(types) => {
                        if types.len() != 4096 {
                            return Err(Error::Unsupported);
                        }
                        let start = e.full.len();
                        e.full.extend_from_slice(types);
                        Held::Full(start)
                    }
                }
            };
            self.engine.sections[at].blocks = held;
        }
        Ok(())
    }

    /// `getState` of index `i` in the stored section at `at`.
    #[inline]
    fn state_at(&mut self, at: usize, i: usize) -> Result<Type, Error> {
        if let Held::Unknown = self.engine.sections[at].blocks {
            self.load_blocks(at)?;
        }
        let e = &*self.engine;
        let t = match e.sections[at].blocks {
            Held::Uniform(t) => t,
            Held::Packed { bits, global, palette, palette_len, words, word_len } => {
                let per = 64 / bits as usize;
                let word = e.words[words..words + word_len][i / per];
                let id = ((word >> (i % per * bits as usize)) & ((1u64 << bits) - 1)) as usize;
                if global {
                    *self.tables.state_types.get(id).ok_or(Error::Unsupported)?
                } else if id < palette_len {
                    e.palettes[palette + id]
                } else {
                    return Err(Error::Unsupported);
                }
            }
            Held::Full(start) => e.full[start + i],
            Held::Unknown => unreachable!(),
        };
        self.tables.types.get(t as usize).copied().ok_or(Error::Unsupported)
    }

    /// `BlockLightEngine.propagateIncrease`.
    fn block_increase(&mut self, pos: i64, own: usize, oi: usize, entry: u64, level: i32) -> Result<(), Error> {
        let mut from = None;
        for direction in 0..6 {
            if entry & bit(direction) == 0 {
                continue;
            }
            let next = offset(pos, direction);
            let (at, i) = self.neighbor(own, oi, next, direction)?;
            if !self.is_stored(at) {
                continue;
            }
            let stored = self.level_at(at, i);
            if level - 1 > stored {
                let to = self.state_at(at, i)?;
                let value = level - to.opacity as i32;
                if value > stored {
                    let from = match from {
                        Some(from) => from,
                        None => *from.insert(if entry & FROM_EMPTY_SHAPE != 0 {
                            self.tables.types[self.tables.air as usize]
                        } else {
                            self.state_at(own, oi)?
                        }),
                    };
                    if !self.tables.occludes(from.faces[direction], to.faces[direction ^ 1]) {
                        self.set_at(at, i, next, value);
                        if value > 1 {
                            self.engine.increases.push_back((next, increase_skip_one(value, to.empty, direction ^ 1)));
                        }
                    }
                }
            }
        }
        Ok(())
    }

    /// `BlockLightEngine.propagateDecrease`.
    fn block_decrease(&mut self, pos: i64, entry: u64) -> Result<(), Error> {
        let own = self.section(section_of(pos))?;
        let oi = index(pos);
        let from = level(entry);
        for direction in 0..6 {
            if entry & bit(direction) == 0 {
                continue;
            }
            let next = offset(pos, direction);
            let (at, i) = self.neighbor(own, oi, next, direction)?;
            if !self.is_stored(at) {
                continue;
            }
            let stored = self.level_at(at, i);
            if stored == 0 {
                continue;
            }
            if stored <= from - 1 {
                let state = self.state_at(at, i)?;
                let emission = if state.emission != 0 && self.engine.sections[at].light_on { state.emission as i32 } else { 0 };
                self.set_at(at, i, next, 0);
                if emission < stored {
                    self.engine.decreases.push_back((next, decrease_skip_one(stored, direction ^ 1)));
                }
                if emission > 0 {
                    self.engine.increases.push_back((next, increase_from_emission(emission, state.empty)));
                }
            } else {
                self.engine.increases.push_back((next, increase_only_one(stored, false, direction ^ 1)));
            }
        }
        Ok(())
    }

    /// `SkyLightEngine.propagateIncrease`.
    fn sky_increase(&mut self, pos: i64, own: usize, oi: usize, entry: u64, level: i32) -> Result<(), Error> {
        let empty_below = self.count_empty_sections_below_if_at_border(pos)?;
        let mut from = None;
        for direction in 0..6 {
            if entry & bit(direction) == 0 {
                continue;
            }
            let next = offset(pos, direction);
            let (at, i) = self.neighbor(own, oi, next, direction)?;
            if !self.is_stored(at) {
                continue;
            }
            let stored = self.level_at(at, i);
            if level - 1 > stored {
                let to = self.state_at(at, i)?;
                let value = level - to.opacity as i32;
                if value > stored {
                    let from = match from {
                        Some(from) => from,
                        None => *from.insert(if entry & FROM_EMPTY_SHAPE != 0 {
                            self.tables.types[self.tables.air as usize]
                        } else {
                            self.state_at(own, oi)?
                        }),
                    };
                    if !self.tables.occludes(from.faces[direction], to.faces[direction ^ 1]) {
                        self.set_at(at, i, next, value);
                        if value > 1 {
                            self.engine.increases.push_back((next, increase_skip_one(value, to.empty, direction ^ 1)));
                        }
                        self.propagate_from_empty_sections(next, direction, value, true, empty_below)?;
                    }
                }
            }
        }
        Ok(())
    }

    /// `SkyLightEngine.propagateDecrease`.
    fn sky_decrease(&mut self, pos: i64, entry: u64) -> Result<(), Error> {
        let own = self.section(section_of(pos))?;
        let oi = index(pos);
        let empty_below = self.count_empty_sections_below_if_at_border(pos)?;
        let from = level(entry);
        for direction in 0..6 {
            if entry & bit(direction) == 0 {
                continue;
            }
            let next = offset(pos, direction);
            let (at, i) = self.neighbor(own, oi, next, direction)?;
            if !self.is_stored(at) {
                continue;
            }
            let stored = self.level_at(at, i);
            if stored == 0 {
                continue;
            }
            if stored <= from - 1 {
                self.set_at(at, i, next, 0);
                self.engine.decreases.push_back((next, decrease_skip_one(stored, direction ^ 1)));
                self.propagate_from_empty_sections(next, direction, stored, false, empty_below)?;
            } else {
                self.engine.increases.push_back((next, increase_only_one(stored, false, direction ^ 1)));
            }
        }
        Ok(())
    }

    fn count_empty_sections_below_if_at_border(&mut self, pos: i64) -> Result<i32, Error> {
        let y = block_y(pos);
        if y & 15 != 0 {
            return Ok(0);
        }
        let (x, z) = (block_x(pos), block_z(pos));
        let (rx, rz) = (x & 15, z & 15);
        if rx != 0 && rx != 15 && rz != 0 && rz != 15 {
            return Ok(0);
        }
        let (sx, sy, sz) = (x >> 4, y >> 4, z >> 4);
        let mut count = 0;
        while !self.storing(section_pos(sx, sy - count - 1, sz))? && sy - count - 1 >= self.engine.lowest {
            count += 1;
        }
        Ok(count)
    }

    fn propagate_from_empty_sections(&mut self, pos: i64, direction: usize, value: i32, increase: bool, empty_below: i32) -> Result<(), Error> {
        if empty_below == 0 {
            return Ok(());
        }
        let (x, z) = (block_x(pos), block_z(pos));
        if !crossed_section_edge(direction, x & 15, z & 15) {
            return Ok(());
        }
        let (sx, sz) = (x >> 4, z >> 4);
        let mut sy = (block_y(pos) >> 4) - 1;
        let last = sy - empty_below + 1;
        while sy >= last {
            if self.storing(section_pos(sx, sy, sz))? {
                let base = sy * 16;
                for dy in (0..16).rev() {
                    let at = block_pos(x, base + dy, z);
                    if increase {
                        self.set(at, value)?;
                        if value > 1 {
                            self.engine.increases.push_back((at, increase_skip_one(value, true, direction ^ 1)));
                        }
                    } else {
                        self.set(at, 0)?;
                        self.engine.decreases.push_back((at, decrease_skip_one(value, direction ^ 1)));
                    }
                }
            }
            sy -= 1;
        }
        Ok(())
    }
}

impl Engine {
    fn add_affected(&mut self, key: i64) {
        if self.affected.insert(key, ()).is_none() {
            self.affected_order.push(key);
        }
    }
}

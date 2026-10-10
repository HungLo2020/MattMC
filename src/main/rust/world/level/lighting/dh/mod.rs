//! Whole DH CPU lighting pass over authoritative native block owners.
//! Immutable light fields and cached source positions stay Rust owned.
//! Java borrows the CPU fields directly; unsupported inputs retain original callbacks.
use crate::content::block::collision::Catalog;
use crate::world::level::chunk::{dh_heightmaps::Heightmaps, live};
use std::sync::Arc;

pub(crate) struct Input<'a> {
    pub(crate) slot: usize,
    pub(crate) sections: &'a [&'a live::Owner],
    pub(crate) heights: &'a Heightmaps,
    pub(crate) min_y: i32,
    pub(crate) previous: Option<&'a Field>,
    pub(crate) cached_sources: Option<&'a sources::Sources>,
}
/// Immutable section storage: a uniform nibble uses an encoded constant;
/// a varying section owns exactly 2048 packed bytes. Java borrows these same
/// tables directly, without a dense output projection or mutable mirror.
#[derive(Clone, Debug, PartialEq)]
struct Layer {
    offsets: Box<[i32]>,
    data: Box<[u8]>,
}
impl Layer {
    fn from_cells(cells: &[u8], shift: u8) -> Self {
        let mut offsets = Vec::with_capacity(cells.len() / 4096);
        let mut data = Vec::new();
        for section in cells.chunks_exact(4096) {
            let first = (section[0] >> shift) & 15;
            if section.iter().all(|&v| (v >> shift) & 15 == first) {
                offsets.push(-1 - first as i32);
            } else {
                offsets.push(data.len() as i32);
                data.extend(
                    section
                        .chunks_exact(2)
                        .map(|p| ((p[0] >> shift) & 15) | ((p[1] >> shift) & 15) << 4),
                );
            }
        }
        Self {
            offsets: offsets.into_boxed_slice(),
            data: data.into_boxed_slice(),
        }
    }
    fn get(&self, index: usize) -> u8 {
        let offset = self.offsets[index / 4096];
        if offset < 0 {
            return (-1 - offset) as u8;
        }
        (self.data[offset as usize + (index % 4096) / 2] >> ((index & 1) * 4)) & 15
    }
}
/// Outputs never change; cached source positions retain Frozen's first
/// enumeration independently of later live block edits.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Field {
    pub(crate) min_y: i32,
    block: Layer,
    sky: Layer,
    pub(crate) sources: Arc<[u32]>,
}
impl Field {
    fn new(min_y: i32, cells: &[u8], sources: Arc<[u32]>) -> Self {
        Self {
            min_y,
            block: Layer::from_cells(cells, 0),
            sky: Layer::from_cells(cells, 4),
            sources,
        }
    }
    fn len(&self) -> usize {
        self.block.offsets.len() * 4096
    }
    fn expand(&self) -> Vec<u8> {
        (0..self.len())
            .map(|i| self.block.get(i) | (self.sky.get(i) << 4))
            .collect()
    }
    #[cfg(test)]
    pub(crate) fn level(&self, x: usize, y: i32, z: usize, sky: bool) -> u8 {
        if y < self.min_y {
            return 0;
        }
        let index = (y - self.min_y) as usize * 256 + z * 16 + x;
        if index >= self.len() {
            return if sky { 15 } else { 0 };
        }
        if sky {
            self.sky.get(index)
        } else {
            self.block.get(index)
        }
    }
}
pub(crate) struct Result {
    pub(crate) fields: Vec<(usize, Field)>,
    pub(crate) iterations: usize,
}
struct Chunk {
    slot: usize,
    min: i32,
    max: i32,
    states: Vec<u32>,
    lights: Vec<u8>,
    sources: Arc<[u32]>,
}
struct Queue {
    levels: [Vec<u32>; 16],
    entries: usize,
    limit: usize,
}
impl Queue {
    fn new(limit: usize) -> Self {
        Self {
            levels: std::array::from_fn(|_| Vec::new()),
            entries: 0,
            limit,
        }
    }
    fn push(&mut self, x: usize, y: usize, z: usize, light: u8) -> Option<()> {
        if x >= 48 || z >= 48 || y > 4096 || light > 15 || self.entries >= self.limit {
            return None;
        }
        self.levels[light as usize].try_reserve(1).ok()?;
        self.levels[light as usize].push(x as u32 | (z as u32) << 6 | (y as u32) << 12);
        self.entries += 1;
        Some(())
    }
    fn pop(&mut self, light: u8) -> Option<(i32, i32, i32)> {
        let pos = self.levels[light as usize].pop()?;
        self.entries -= 1;
        Some((
            (pos & 63) as i32,
            (pos >> 12) as i32,
            ((pos >> 6) & 63) as i32,
        ))
    }
}
fn set(chunk: &mut Chunk, index: usize, light: u8, sky: bool) {
    if let Some(byte) = chunk.lights.get_mut(index) {
        let shift = if sky { 4 } else { 0 };
        *byte = (*byte & !(15 << shift)) | light << shift;
    }
}
/// Section-local snapshots are taken in Rust under the existing storage locks.
/// No Java dense state/policy arrays and no per-cell boundary calls are needed.
/// Caller keeps the original chunk exclusion rules; this is not a whole-world
/// concurrent transaction. Unsupported input returns before any publication.
pub(crate) fn build(
    inputs: &[Input<'_>],
    order: &[i8],
    catalog: &Catalog,
    emission: impl Fn(u32) -> Option<u8>,
    max_sky: u8,
    update_block: bool,
    update_sky: bool,
) -> Option<Result> {
    if inputs.is_empty() || inputs.len() > 9 || order.len() > 1024 || max_sky > 15 {
        return None;
    }
    let min_y = inputs[0].min_y;
    let sections = inputs[0].sections.len();
    if !(1..=256).contains(&sections) || min_y % 16 != 0 || min_y.unsigned_abs() > 1_000_000 {
        return None;
    }
    let height = sections * 16;
    let size = height * 256;
    let mut chunks = Vec::with_capacity(inputs.len());
    let mut slots = [None; 9];
    for input in inputs {
        if input.slot >= 9
            || slots[input.slot].is_some()
            || input.min_y != min_y
            || input.sections.len() != sections
            || input.heights.min < min_y
            || input.heights.min >= min_y + height as i32
            || input.heights.max <= input.heights.min
            || input.heights.max > min_y + height as i32
        {
            return None;
        }
        let mut states = Vec::with_capacity(size);
        for owner in input.sections {
            owner.with_state_reader(|reader| {
                if !reader.all_states(|id| {
                    catalog.dh(id).is_some() && emission(id).is_some_and(|e| e <= 15)
                }) {
                    return None;
                }
                states.extend((0..4096).map(|i| reader.get(i)));
                Some(())
            })?;
        }
        let previous = input.previous;
        if previous.is_some_and(|p| p.min_y != min_y || p.len() != size) {
            return None;
        }
        let sources: Arc<[u32]> = if let Some(s) = input.cached_sources {
            if s.min_y != min_y {
                return None;
            }
            s.positions.clone()
        } else if let Some(p) = previous {
            p.sources.clone()
        } else {
            states
                .iter()
                .enumerate()
                .filter_map(|(i, &id)| (emission(id)? != 0).then_some(i as u32))
                .collect()
        };
        if sources.iter().any(|&i| i as usize >= size) {
            return None;
        }
        let lights = previous.map_or_else(|| vec![0; size], Field::expand);
        slots[input.slot] = Some(chunks.len());
        chunks.push(Chunk {
            slot: input.slot,
            min: input.heights.min - min_y,
            max: input.heights.max - min_y,
            states,
            lights,
            sources,
        });
    }
    let center = slots[4]?;
    // Each cell can increase at most 15 times plus initial seeds. Explicit cap
    // prevents unbounded queue growth; capacity follows actual work, not sixteen
    // preallocated 40,000-position Java arrays for each of two queues.
    let limit = inputs.len() * size * 16 + inputs.len() * 256;
    let mut block = Queue::new(limit);
    let mut sky = Queue::new(limit);
    let mut seen = [false; 9];
    for &slot in order {
        if slot == -1 {
            continue;
        }
        if !(0..9).contains(&slot) {
            return None;
        }
        let slot = slot as usize;
        if seen[slot] {
            continue;
        }
        seen[slot] = true;
        let index = slots[slot]?;
        let chunk = &mut chunks[index];
        let ox = (slot % 3) * 16;
        let oz = (slot / 3) * 16;
        if update_block {
            let sources = chunk.sources.clone();
            for &index in sources.iter() {
                let index = index as usize;
                let level = emission(chunk.states[index])?;
                block.push(ox + index % 16, index / 256, oz + (index % 256) / 16, level)?;
                set(chunk, index, level, false);
            }
        }
        if update_sky && max_sky > 0 {
            for x in 0..16 {
                for z in 0..16 {
                    for y in (0..=chunk.max as usize).rev() {
                        let index = y * 256 + z * 16 + x;
                        if index < size && catalog.dh(chunk.states[index])?.opacity != 0 {
                            break;
                        }
                        sky.push(ox + x, y, oz + z, max_sky)?;
                        set(chunk, index, max_sky, true);
                    }
                }
            }
        }
    }
    let mut iterations = 0;
    for (queue, enabled, is_sky) in [
        (&mut block, update_block, false),
        (&mut sky, update_sky, true),
    ] {
        if !enabled {
            continue;
        }
        // Frozen clears center AFTER all initial seeds, including center seeds.
        for byte in chunks[center].lights.iter_mut() {
            *byte &= if is_sky { 15 } else { 240 };
        }
        for level in (0..=15).rev() {
            while let Some((x, y, z)) = queue.pop(level) {
                iterations += 1;
                // EDhDirection.ALL: up, down, west, east, north, south.
                for (dx, dy, dz) in [
                    (0, 1, 0),
                    (0, -1, 0),
                    (-1, 0, 0),
                    (1, 0, 0),
                    (0, 0, -1),
                    (0, 0, 1),
                ] {
                    let (x, y, z) = (x + dx, y + dy, z + dz);
                    if !(0..48).contains(&x) || !(0..48).contains(&z) {
                        continue;
                    }
                    let slot = (x / 16 + z / 16 * 3) as usize;
                    let Some(index) = slots[slot] else {
                        continue;
                    };
                    let chunk = &mut chunks[index];
                    if y < chunk.min || y >= height as i32 {
                        continue;
                    }
                    let cell = y as usize * 256 + (z as usize % 16) * 16 + x as usize % 16;
                    let current = (chunk.lights[cell] >> if is_sky { 4 } else { 0 }) & 15;
                    if current as i32 >= level as i32 - 1 {
                        continue;
                    }
                    let opacity = catalog.dh(chunk.states[cell])?.opacity.max(1);
                    let Some(target) = level.checked_sub(opacity) else {
                        continue;
                    };
                    if target > current {
                        set(chunk, cell, target, is_sky);
                        queue.push(x as usize, y as usize, z as usize, target)?;
                    }
                }
            }
        }
    }
    Some(Result {
        fields: chunks
            .into_iter()
            .map(|c| (c.slot, Field::new(min_y, &c.lights, c.sources)))
            .collect(),
        iterations,
    })
}
mod ffi;
pub(crate) mod sources;
#[cfg(test)]
mod tests;

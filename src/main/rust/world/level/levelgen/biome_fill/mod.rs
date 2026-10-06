//! Rust-owned BIOMES stage for one chunk: `ChunkAccess.fillBiomesFromNoise`
//! with a plain `MultiNoiseBiomeSource`. The chunk's climate sampler (six
//! router functions with the chunk's FlatCache semantics) runs on the router
//! over each quart column, `Climate.target` quantizes the samples, the climate
//! tree is searched in Java's section order from the thread's previous leaf,
//! and each section's biome `PalettedContainer` writes are replayed exactly.
mod ffi;
#[cfg(test)]
mod tests;

use crate::world::level::biome::climate::{search_batch, Node};
use crate::world::level::levelgen::noise_fill::section::pack;
use crate::world::level::levelgen::router::{with_frame, Binding, Error, Program, LANES};

/// Entries of a biome container: 4x4x4 quarts.
pub(crate) const ENTRIES: usize = 64;

#[derive(Clone, Copy, Debug, PartialEq)]
enum Kind {
    Single,
    Linear(u32),
    Global,
}

/// Exact replay of `PalettedContainer.recreate()` and its 64
/// `getAndSetUnchecked` writes under the biome strategy: a single value grows
/// to the 1..3 bit linear palettes, then the global palette. Each resize
/// rebuilds the palette by first occurrence in storage order, as
/// `PalettedContainer.Data.copyFrom` does. Values are registry IDs.
pub(crate) struct Container {
    kind: Kind,
    /// Configuration bit count Java requested for the current palette.
    requested: u32,
    palette: Vec<i32>,
    storage: [u32; ENTRIES],
    global_bits: u32,
}

impl Container {
    /// `recreate()`: zero bits, the old container's first palette value.
    pub(crate) fn new(initial: i32, global_bits: u32) -> Self {
        Self { kind: Kind::Single, requested: 0, palette: vec![initial], storage: [0; ENTRIES], global_bits }
    }

    fn value_for(&self, id: u32) -> i32 {
        match self.kind {
            Kind::Global => id as i32,
            _ => self.palette[id as usize],
        }
    }

    fn id_for(&mut self, value: i32) -> u32 {
        match self.kind {
            Kind::Single => {
                if self.palette[0] == value {
                    0
                } else {
                    self.resize(1, value)
                }
            }
            Kind::Linear(bits) => match self.palette.iter().position(|v| *v == value) {
                Some(id) => id as u32,
                None if self.palette.len() < 1 << bits => {
                    self.palette.push(value);
                    (self.palette.len() - 1) as u32
                }
                None => self.resize(bits + 1, value),
            },
            Kind::Global => value as u32,
        }
    }

    /// `PalettedContainer.onResize(requested, value)`.
    fn resize(&mut self, requested: u32, value: i32) -> u32 {
        let values: Vec<i32> = self.storage.iter().map(|id| self.value_for(*id)).collect();
        self.kind = if requested <= 3 { Kind::Linear(requested) } else { Kind::Global };
        self.requested = requested;
        self.palette.clear();
        for (index, v) in values.into_iter().enumerate() {
            self.storage[index] = self.append(v);
        }
        self.append(value)
    }

    /// `idFor` with `noResizeExpected`: the new palette has room.
    fn append(&mut self, value: i32) -> u32 {
        if self.kind == Kind::Global {
            return value as u32;
        }
        match self.palette.iter().position(|v| *v == value) {
            Some(id) => id as u32,
            None => {
                self.palette.push(value);
                (self.palette.len() - 1) as u32
            }
        }
    }

    /// `getAndSetUnchecked` at a storage index.
    pub(crate) fn set(&mut self, index: usize, value: i32) {
        let id = self.id_for(value);
        self.storage[index] = id;
    }

    pub(crate) fn requested_bits(&self) -> u32 {
        self.requested
    }

    /// Palette entries in id order; empty for the global palette.
    pub(crate) fn palette(&self) -> &[i32] {
        if self.kind == Kind::Global {
            &[]
        } else {
            &self.palette
        }
    }

    /// Storage packed with `Configuration.bitsInMemory` bits per entry.
    pub(crate) fn packed(&self) -> Vec<i64> {
        let bits = match self.kind {
            Kind::Single => 0,
            Kind::Linear(bits) => bits,
            Kind::Global => self.global_bits,
        };
        pack(&self.storage, bits)
    }
}

/// The chunk's quart geometry: its minimum quart X and Z, the quart Y of its
/// lowest section, its section count, and the side of its FlatCache grid.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Geometry {
    pub qx: i32,
    pub qz: i32,
    pub first_quart_y: i32,
    pub sections: usize,
    pub grid: i32,
}

pub(crate) enum Outcome {
    /// Every section's container and the thread's new previous leaf.
    Filled(Vec<Container>, i32),
    /// A search found no leaf; Java's own fill reproduces its failure.
    NoLeaf,
}

/// `Climate.quantizeCoord((float)value)`: float product, saturating cast.
fn quantize(value: f64) -> i64 {
    ((value as f32) * 10000.0f32) as i64
}

/// The chunk's 64 targets per section in `searchSection`'s (dx, dy, dz) order:
/// temperature, humidity, continentalness, erosion, depth, weirdness, then
/// the zero offset and padding coordinates the search expects.
fn targets(program: &Program, g: Geometry) -> Result<Vec<[i64; 8]>, Error> {
    let mut targets = vec![[0i64; 8]; g.sections * ENTRIES];
    let slots = program.point_slots() * (g.grid * g.grid) as usize;
    let (mut memo, mut present) = (vec![0.; slots], vec![0u8; slots]);
    let mut binding = Binding { first_x: g.qx, first_z: g.qz, size: g.grid, memo: &mut memo, present: &mut present };
    let quarts = g.sections * 4;
    let mut out = vec![0.; 6 * LANES];
    with_frame(program, |frame| {
        for dx in 0..4 {
            for dz in 0..4 {
                let x = g.qx.wrapping_add(dx as i32).wrapping_shl(2);
                let z = g.qz.wrapping_add(dz as i32).wrapping_shl(2);
                let mut start = 0;
                while start < quarts {
                    let count = (quarts - start).min(LANES);
                    let ys: Vec<f64> = (start..start + count).map(|q| g.first_quart_y.wrapping_add(q as i32).wrapping_shl(2) as f64).collect();
                    let out = &mut out[..6 * count];
                    program.column_points(frame, x, z, &ys, &mut binding, out)?;
                    for lane in 0..count {
                        let (section, dy) = ((start + lane) / 4, (start + lane) % 4);
                        let target = &mut targets[section * ENTRIES + (dx * 4 + dy) * 4 + dz];
                        for (axis, value) in target.iter_mut().take(6).enumerate() {
                            *value = quantize(out[axis * count + lane]);
                        }
                    }
                    start += count;
                }
            }
        }
        Ok(targets)
    })
}

/// The BIOMES stage for one chunk. `values` maps each leaf node to its
/// biome's registry ID; `initial` is each section's old first palette value.
/// A one-node tree is Java's single-leaf shortcut: no search, every quart its
/// leaf (sampling is pure, so it is skipped).
pub(crate) fn fill(program: &Program, nodes: &[Node], values: &[i32], previous: i32, g: Geometry, initial: &[i32], global_bits: u32)
    -> Result<Outcome, Error> {
    let mut containers = Vec::with_capacity(g.sections);
    if nodes.len() == 1 {
        for &first in initial {
            let mut container = Container::new(first, global_bits);
            for index in 0..ENTRIES {
                container.set(index, values[0]);
            }
            containers.push(container);
        }
        return Ok(Outcome::Filled(containers, 0));
    }
    let targets = targets(program, g)?;
    let mut previous = previous;
    let mut leaves = [0i32; ENTRIES];
    for (section, &first) in initial.iter().enumerate() {
        let completed = search_batch(nodes, &targets[section * ENTRIES..(section + 1) * ENTRIES], &mut leaves, previous);
        if completed < ENTRIES || leaves[ENTRIES - 1] < 0 {
            return Ok(Outcome::NoLeaf);
        }
        previous = leaves[ENTRIES - 1];
        let mut container = Container::new(first, global_bits);
        // Writes in (dx, dy, dz) order; storage index (y << 2 | z) << 2 | x.
        for (index, leaf) in leaves.iter().enumerate() {
            let (dx, dy, dz) = (index / 16, (index / 4) % 4, index % 4);
            container.set((dy << 2 | dz) << 2 | dx, values[*leaf as usize]);
        }
        containers.push(container);
    }
    Ok(Outcome::Filled(containers, previous))
}

//! Biome searches over a multi-noise source: `BiomeSource.findBiomeHorizontal`
//! and `findClosestBiome3d`, with every sample's climate (the RandomState
//! sampler's six functions as a point program) and climate-tree search in
//! Rust. Java keeps the result objects and `findBiomeHorizontal`'s random
//! choice among matches, which it replays from the matches returned here.
pub(crate) mod ffi;
#[cfg(test)]
mod tests;

use crate::world::level::biome::climate::{search_batch, Node};
use crate::world::level::levelgen::router::{with_frame, Binding, Error as RouterError, Frame, Program, LANES};

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Error {
    /// The point program failed; Java searches itself.
    Program,
    /// A climate search found no leaf; Java's search reproduces the failure.
    NoLeaf,
}

impl From<RouterError> for Error {
    fn from(_: RouterError) -> Self {
        Error::Program
    }
}

/// `Climate.quantizeCoord((float)value)`.
fn quantize(value: f64) -> i64 {
    ((value as f32) * 10000.0f32) as i64
}

/// `QuartPos.toBlock`.
fn block(quart: i32) -> i32 {
    quart.wrapping_shl(2)
}

/// Climate searches in sample order from the thread's previous leaf.
pub(crate) struct Sampler<'a> {
    program: &'a Program,
    nodes: &'a [Node],
    pub previous: i32,
    targets: Vec<[i64; 8]>,
    values: Vec<f64>,
}

impl<'a> Sampler<'a> {
    pub(crate) fn new(program: &'a Program, nodes: &'a [Node], previous: i32) -> Self {
        Self { program, nodes, previous, targets: Vec::new(), values: vec![0.; 6 * LANES] }
    }

    /// `sampler.sample(x, y, z)` for each block Y of one column (in order),
    /// without searching: sampling is pure.
    fn targets(&mut self, frame: &mut Frame, x: i32, z: i32, ys: &[i32]) -> Result<(), Error> {
        self.targets.clear();
        let (mut memo, mut present) = (Vec::new(), Vec::new());
        let mut binding = Binding { first_x: 0, first_z: 0, size: 0, memo: &mut memo, present: &mut present };
        for chunk in ys.chunks(LANES) {
            let lanes: Vec<f64> = chunk.iter().map(|&y| y as f64).collect();
            let out = &mut self.values[..6 * chunk.len()];
            self.program.column_points(frame, x, z, &lanes, &mut binding, out)?;
            for lane in 0..chunk.len() {
                let mut target = [0i64; 8];
                for (axis, value) in target.iter_mut().take(6).enumerate() {
                    *value = quantize(out[axis * chunk.len() + lane]);
                }
                self.targets.push(target);
            }
        }
        Ok(())
    }

    /// `parameters().findValue(target)`: the leaf, updating the previous leaf.
    fn search(&mut self, index: usize) -> Result<i32, Error> {
        if self.nodes.len() == 1 {
            // A single-leaf tree is Java's constant shortcut: no search, that leaf.
            if !self.nodes[0].is_leaf() {
                return Err(Error::NoLeaf);
            }
            self.previous = 0;
            return Ok(0);
        }
        let mut leaf = [0i32];
        let completed = search_batch(self.nodes, &self.targets[index..index + 1], &mut leaf, self.previous);
        if completed < 1 || leaf[0] < 0 {
            return Err(Error::NoLeaf);
        }
        self.previous = leaf[0];
        Ok(leaf[0])
    }
}

/// The quart grid of `findBiomeHorizontal(i, j, k, l, m, ..., false, ...)`:
/// centre `(qx, qz)`, quart `y`, radius and step in quarts. (The
/// perimeter-only variant has no production caller and stays Java.)
pub(crate) struct Horizontal {
    pub qx: i32,
    pub qy: i32,
    pub qz: i32,
    pub radius: i32,
    pub step: i32,
}

/// Every matching sample `(quart x, quart z, leaf)` in visiting order, where
/// `accept[leaf]` is the predicate.
pub(crate) fn horizontal(sampler: &mut Sampler, search: &Horizontal, accept: &[u8], matches: &mut Vec<(i32, i32, i32)>) -> Result<(), Error> {
    matches.clear();
    if search.step < 1 {
        return Err(Error::Program);
    }
    let y = [block(search.qy)];
    with_frame(sampler.program, |frame| {
        // The full grid is the single "ring" t = radius.
        let t = search.radius;
        let mut u = -t;
        while u <= t {
            let mut v = -t;
            while v <= t {
                let (w, x) = (search.qx.wrapping_add(v), search.qz.wrapping_add(u));
                sampler.targets(frame, block(w), block(x), &y)?;
                let leaf = sampler.search(0)?;
                if accept[leaf as usize] != 0 {
                    matches.push((w, x, leaf));
                }
                v += search.step;
            }
            u += search.step;
        }
        Ok(())
    })
}

/// `BlockPos.spiralAround(ZERO, radius, EAST, SOUTH)`: offsets (x, z) in order.
pub(crate) fn spiral(radius: i32) -> impl Iterator<Item = (i32, i32)> {
    // EAST, SOUTH, WEST, NORTH.
    const DIRECTIONS: [(i32, i32); 4] = [(1, 0), (0, 1), (-1, 0), (0, -1)];
    let legs = 4 * radius;
    let (mut x, mut z) = (0, 1);
    let (mut leg, mut leg_size, mut leg_index) = (-1i32, 0i32, 0i32);
    std::iter::from_fn(move || {
        let (dx, dz) = DIRECTIONS[(leg + 4) as usize % 4];
        x += dx;
        z += dz;
        if leg_index >= leg_size {
            if leg >= legs {
                return None;
            }
            leg += 1;
            leg_index = 0;
            leg_size = leg / 2 + 1;
        }
        leg_index += 1;
        Some((x, z))
    })
}

/// `findClosestBiome3d`: block `(x, z)` centre, spiral radius and step in
/// blocks, the block Ys in `Mth.outFromOrigin` order. The first accepted
/// sample `(x, y, z, leaf)`.
pub(crate) fn closest(sampler: &mut Sampler, x: i32, z: i32, radius: i32, step: i32, ys: &[i32], accept: &[u8])
    -> Result<Option<(i32, i32, i32, i32)>, Error> {
    // Sampling uses each Y's quart; QuartPos.toBlock(QuartPos.fromBlock(y)).
    let quart_ys: Vec<i32> = ys.iter().map(|&y| block(y >> 2)).collect();
    with_frame(sampler.program, |frame| {
        for (ox, oz) in spiral(radius) {
            let (m, n) = (x.wrapping_add(ox.wrapping_mul(step)), z.wrapping_add(oz.wrapping_mul(step)));
            sampler.targets(frame, block(m >> 2), block(n >> 2), &quart_ys)?;
            for (index, &y) in ys.iter().enumerate() {
                let leaf = sampler.search(index)?;
                if accept[leaf as usize] != 0 {
                    return Ok(Some((m, y, n, leaf)));
                }
            }
        }
        Ok(None)
    })
}

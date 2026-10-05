//! Native noise router: evaluates a NoiseChunk's interpolated density functions
//! for a whole interpolation slice in one call. Java compiles each chunk-wrapped
//! interpolator graph into a DAG of the density operations below (sharing
//! CacheOnce nodes by identity), and Rust evaluates every column's points as up
//! to 64 lanes. Short circuits keep Java's lazy branch visits: a child is
//! evaluated only for lanes whose parent needs it. Nodes that cannot depend on
//! Y are evaluated once per column. Every operation reuses the production
//! density arithmetic, noise sampling, spline and End island implementations.
mod ffi;
#[cfg(test)]
mod tests;

use super::density::end_islands::noise_end_island;
use super::density::evaluator::{coordinates, finish_noise};
use super::density::math::{in_range, math, needs_right};
use super::density::spline::evaluate::evaluate as spline_evaluate;
use super::density::spline::program::{valid as spline_valid, Knot, Node as SplineNode};
use super::synth::{noise_batch, noise_eval, State};

pub(crate) const LANES: usize = 64;
pub(crate) const MAX_NODES: usize = 4096;
const MAX_DEPTH: u32 = 256;

pub(crate) const CONSTANT: u32 = 0;
// 1..=7: noise operations shared with density programs (Noise, Shift, ShiftA,
// ShiftB, ShiftedNoise, WeirdScaledSampler TYPE1/TYPE2).
// 8..=11: Ap2 ADD, MUL, MIN, MAX. 12..=21: MulOrAdd, Mapped, Clamp.
pub(crate) const RANGE_CHOICE: u32 = 22;
pub(crate) const Y_CLAMPED_GRADIENT: u32 = 30;
pub(crate) const BLENDED_NOISE: u32 = 31;
pub(crate) const END_ISLANDS: u32 = 32;
pub(crate) const SPLINE: u32 = 33;
pub(crate) const FLAT_CACHE: u32 = 34;
pub(crate) const CACHE_2D: u32 = 35;

#[derive(Clone, Copy, Debug)]
pub(crate) struct Node {
    pub op: u32,
    pub a: u32,
    pub b: u32,
    pub c: u32,
    pub state: *const State,
    pub p: f64,
    pub q: f64,
    pub r: f64,
    pub s: f64,
}

pub(crate) struct Spline {
    pub nodes: Vec<SplineNode>,
    pub knots: Vec<Knot>,
    /// Router nodes supplying the spline's coordinates, by axis.
    pub axes: [u32; 4],
    pub axis_count: usize,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct Geometry {
    pub columns: usize,
    pub points: usize,
    pub cell_width: i32,
    pub cell_height: i32,
    pub first_cell_z: i32,
    pub cell_min_y: i32,
    pub first_noise_x: i32,
    pub first_noise_z: i32,
    pub flat_size: i32,
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Error {
    /// A FlatCache read outside the chunk grid; Java evaluates the slice itself.
    OutsideFlatCache,
}

#[derive(Debug, PartialEq, Eq)]
pub(crate) enum Invalid {
    Size,
    Operation(usize),
    Child(usize),
    State(usize),
    Spline(usize),
    YDependentCache2D(usize),
    Depth,
}

pub(crate) struct Router {
    nodes: Vec<Node>,
    y_independent: Vec<bool>,
    splines: Vec<Spline>,
    flat: Vec<f64>,
    roots: Vec<u32>,
    geometry: Geometry,
    values: Vec<f64>,
    done: Vec<u64>,
}

struct Column {
    x: i32,
    z: i32,
    ys: [f64; LANES],
    flat: Option<usize>,
}

fn children(n: &Node) -> &'static [usize] {
    match n.op {
        5 | RANGE_CHOICE => &[0, 1, 2],
        8..=11 => &[0, 1],
        6 | 7 | 12..=21 | CACHE_2D => &[0],
        _ => &[],
    }
}

fn child(n: &Node, index: usize) -> u32 {
    [n.a, n.b, n.c][index]
}

/// The noise state kind each operation samples: NormalNoise, BlendedNoise or
/// the End island SimplexNoise. Only noise holders may be unbound (null).
fn state_kind(op: u32) -> Option<(u32, bool)> {
    match op {
        1..=7 => Some((3, true)),
        BLENDED_NOISE => Some((4, false)),
        END_ISLANDS => Some((5, false)),
        _ => None,
    }
}

impl Router {
    /// Validates the program: known operations, backward child references,
    /// bound noise states of the right kind, valid splines, in-range cache
    /// slots, Y-independent Cache2D inputs and bounded recursion depth.
    pub(crate) fn new(
        nodes: Vec<Node>,
        splines: Vec<Spline>,
        flat: Vec<f64>,
        flat_slots: usize,
        roots: Vec<u32>,
        geometry: Geometry,
    ) -> Result<Router, Invalid> {
        let g = geometry;
        if nodes.is_empty()
            || nodes.len() > MAX_NODES
            || roots.is_empty()
            || g.columns == 0
            || g.columns > 64
            || g.points == 0
            || g.points > 8192
            || !(1..=64).contains(&g.flat_size)
            || flat.len() != flat_slots * (g.flat_size * g.flat_size) as usize
            || roots.iter().any(|root| *root as usize >= nodes.len())
        {
            return Err(Invalid::Size);
        }
        let mut y_independent = vec![false; nodes.len()];
        let mut depth = vec![0u32; nodes.len()];
        for (i, n) in nodes.iter().enumerate() {
            let known = matches!(n.op, CONSTANT..=RANGE_CHOICE | Y_CLAMPED_GRADIENT..=CACHE_2D);
            if !known {
                return Err(Invalid::Operation(i));
            }
            for index in children(n) {
                let c = child(n, *index) as usize;
                if c >= i {
                    return Err(Invalid::Child(i));
                }
                depth[i] = depth[i].max(depth[c] + 1);
            }
            match state_kind(n.op) {
                Some((kind, nullable)) => {
                    if n.state.is_null() {
                        if !nullable {
                            return Err(Invalid::State(i));
                        }
                    } else if unsafe { (*n.state).kind } != kind {
                        return Err(Invalid::State(i));
                    }
                }
                None => {
                    if !n.state.is_null() {
                        return Err(Invalid::State(i));
                    }
                }
            }
            let independent = |index: usize| y_independent[child(n, index) as usize];
            y_independent[i] = match n.op {
                CONSTANT | 3 | 4 | END_ISLANDS | FLAT_CACHE => true,
                // y * 0.0 is a signed zero; samplers add a non-negative octave
                // offset before reading it, so neither sign reaches the result.
                1 => n.q == 0.,
                5 => n.q == 0. && (0..3).all(independent),
                2 | 6 | 7 | Y_CLAMPED_GRADIENT | BLENDED_NOISE => false,
                8..=RANGE_CHOICE | CACHE_2D => children(n).iter().all(|index| independent(*index)),
                SPLINE => {
                    let Some(spline) = splines.get(n.a as usize) else {
                        return Err(Invalid::Spline(i));
                    };
                    if spline.axis_count > 4 {
                        return Err(Invalid::Spline(i));
                    }
                    let mut all = true;
                    for axis in &spline.axes[..spline.axis_count] {
                        let c = *axis as usize;
                        if c >= i {
                            return Err(Invalid::Child(i));
                        }
                        depth[i] = depth[i].max(depth[c] + 1);
                        all &= y_independent[c];
                    }
                    all
                }
                _ => unreachable!(),
            };
            if n.op == FLAT_CACHE && n.a as usize >= flat_slots {
                return Err(Invalid::Child(i));
            }
            // Cache2D returns the first value computed in a column. It is
            // transparent only when every point of the column has that value.
            if n.op == CACHE_2D && !y_independent[n.a as usize] {
                return Err(Invalid::YDependentCache2D(i));
            }
            if depth[i] > MAX_DEPTH {
                return Err(Invalid::Depth);
            }
        }
        for (i, spline) in splines.iter().enumerate() {
            let axes = spline.axis_count as i32;
            if !spline_valid(&spline.nodes, &spline.knots) || spline.nodes.iter().any(|n| n.axis >= axes) {
                return Err(Invalid::Spline(i));
            }
        }
        let count = nodes.len();
        Ok(Router {
            nodes,
            y_independent,
            splines,
            flat,
            roots,
            geometry,
            values: vec![0.; count * LANES],
            done: vec![0; count],
        })
    }

    pub(crate) fn output_len(&self) -> usize {
        self.roots.len() * self.geometry.columns * self.geometry.points
    }

    /// Fills one slice at block X: for each interpolator, each Z column, each
    /// cell corner Y, matching fillSlice's points exactly. Output is
    /// interpolator-major, then column, then Y.
    pub(crate) fn slice(&mut self, x: i32, out: &mut [f64]) -> Result<(), Error> {
        let g = self.geometry;
        debug_assert_eq!(out.len(), self.output_len());
        for column in 0..g.columns {
            let z = g.first_cell_z.wrapping_add(column as i32).wrapping_mul(g.cell_width);
            // QuartPos.fromBlock(x) - firstNoiseX, as FlatCache.compute does.
            let (fx, fz) = ((x >> 2).wrapping_sub(g.first_noise_x), (z >> 2).wrapping_sub(g.first_noise_z));
            let flat = if fx >= 0 && fz >= 0 && fx < g.flat_size && fz < g.flat_size {
                Some((fx + fz * g.flat_size) as usize)
            } else {
                None
            };
            let mut start = 0;
            while start < g.points {
                let lanes = (g.points - start).min(LANES);
                let all = if lanes == LANES { !0 } else { (1u64 << lanes) - 1 };
                let mut ys = [0.; LANES];
                for (lane, y) in ys.iter_mut().enumerate().take(lanes) {
                    *y = ((start + lane) as i32).wrapping_add(g.cell_min_y).wrapping_mul(g.cell_height) as f64;
                }
                let c = Column { x, z, ys, flat };
                self.done.fill(0);
                for k in 0..self.roots.len() {
                    let root = self.roots[k] as usize;
                    self.eval(root, all, &c)?;
                    let at = (k * g.columns + column) * g.points + start;
                    out[at..at + lanes].copy_from_slice(&self.values[root * LANES..root * LANES + lanes]);
                }
                start += lanes;
            }
        }
        Ok(())
    }

    fn eval(&mut self, i: usize, need: u64, c: &Column) -> Result<(), Error> {
        let todo = need & !self.done[i];
        if todo == 0 {
            return Ok(());
        }
        if self.y_independent[i] {
            let lane = todo.trailing_zeros() as usize;
            self.compute(i, 1 << lane, c)?;
            let value = self.values[i * LANES + lane];
            self.values[i * LANES..(i + 1) * LANES].fill(value);
            self.done[i] = !0;
        } else {
            self.compute(i, todo, c)?;
            self.done[i] |= todo;
        }
        Ok(())
    }

    fn compute(&mut self, i: usize, m: u64, c: &Column) -> Result<(), Error> {
        let n = self.nodes[i];
        let (x, z) = (c.x as f64, c.z as f64);
        let base = i * LANES;
        // Children first; afterwards only `values` is borrowed.
        let (a, b, cc) = (n.a as usize * LANES, n.b as usize * LANES, n.c as usize * LANES);
        match n.op {
            CONSTANT => {
                let v = &mut self.values;
                lanes(m, |lane| v[base + lane] = n.p);
            }
            1..=7 => {
                match n.op {
                    5 => {
                        self.eval(n.a as usize, m, c)?;
                        self.eval(n.b as usize, m, c)?;
                        self.eval(n.c as usize, m, c)?;
                    }
                    6 | 7 => self.eval(n.a as usize, m, c)?,
                    _ => {}
                }
                let v = &mut self.values;
                let mut xyz = [0.; LANES * 3];
                let mut factors = [0.; LANES];
                let mut order = [0usize; LANES];
                let mut count = 0;
                lanes(m, |lane| {
                    let (sx, sy, sz) = match n.op {
                        5 => (v[a + lane], v[b + lane], v[cc + lane]),
                        6 | 7 => (v[a + lane], 0., 0.),
                        _ => (0., 0., 0.),
                    };
                    let (point, factor) = coordinates(n.op, x, c.ys[lane], z, sx, sy, sz, n.p, n.q);
                    xyz[count * 3..count * 3 + 3].copy_from_slice(&point);
                    factors[count] = factor;
                    order[count] = lane;
                    count += 1;
                });
                let mut raw = [0.; LANES];
                if !n.state.is_null() {
                    sample(n.state, &xyz, &mut raw, count);
                }
                for k in 0..count {
                    v[base + order[k]] = finish_noise(n.op, raw[k], factors[k]);
                }
            }
            8..=11 => {
                self.eval(n.a as usize, m, c)?;
                let mut right = 0;
                lanes(m, |lane| {
                    if needs_right(n.op, self.values[a + lane], n.p, n.q) {
                        right |= 1 << lane;
                    }
                });
                if right != 0 {
                    self.eval(n.b as usize, right, c)?;
                }
                let v = &mut self.values;
                lanes(m, |lane| {
                    // Lanes outside `right` are decided by the left operand alone.
                    let rhs = if right & (1 << lane) != 0 { v[b + lane] } else { 0. };
                    let value = math(n.op, v[a + lane], rhs, n.p, n.q);
                    v[base + lane] = value;
                });
            }
            12..=21 => {
                self.eval(n.a as usize, m, c)?;
                let v = &mut self.values;
                lanes(m, |lane| {
                    let value = math(n.op, v[a + lane], 0., n.p, n.q);
                    v[base + lane] = value;
                });
            }
            RANGE_CHOICE => {
                self.eval(n.a as usize, m, c)?;
                let mut inside = 0;
                lanes(m, |lane| {
                    if in_range(self.values[a + lane], n.p, n.q) {
                        inside |= 1 << lane;
                    }
                });
                if inside != 0 {
                    self.eval(n.b as usize, inside, c)?;
                }
                if m & !inside != 0 {
                    self.eval(n.c as usize, m & !inside, c)?;
                }
                let v = &mut self.values;
                lanes(m, |lane| {
                    let value = if inside & (1 << lane) != 0 { v[b + lane] } else { v[cc + lane] };
                    v[base + lane] = value;
                });
            }
            Y_CLAMPED_GRADIENT => {
                let v = &mut self.values;
                lanes(m, |lane| {
                    // Mth.clampedMap(y, fromY, toY, fromValue, toValue).
                    let t = (c.ys[lane] - n.p) / (n.q - n.p);
                    v[base + lane] = if t < 0. {
                        n.r
                    } else if t > 1. {
                        n.s
                    } else {
                        n.r + t * (n.s - n.r)
                    };
                });
            }
            BLENDED_NOISE => {
                let mut xyz = [0.; LANES * 3];
                let mut order = [0usize; LANES];
                let mut count = 0;
                lanes(m, |lane| {
                    xyz[count * 3..count * 3 + 3].copy_from_slice(&[x, c.ys[lane], z]);
                    order[count] = lane;
                    count += 1;
                });
                let mut raw = [0.; LANES];
                sample(n.state, &xyz, &mut raw, count);
                for k in 0..count {
                    self.values[base + order[k]] = raw[k];
                }
            }
            END_ISLANDS => {
                // (endIslandHeight(x / 8, z / 8) - 8.0) / 128.0, with Java int division.
                let height = unsafe { noise_end_island(n.state, c.x / 8, c.z / 8) };
                let value = (height as f64 - 8.0) / 128.0;
                let v = &mut self.values;
                lanes(m, |lane| v[base + lane] = value);
            }
            SPLINE => {
                let (axes, count) = (self.splines[n.a as usize].axes, self.splines[n.a as usize].axis_count);
                for axis in &axes[..count] {
                    self.eval(*axis as usize, m, c)?;
                }
                let spline = &self.splines[n.a as usize];
                let v = &mut self.values;
                lanes(m, |lane| {
                    let mut point = [0f32; 4];
                    for k in 0..count {
                        point[k] = v[axes[k] as usize * LANES + lane] as f32;
                    }
                    let value = spline_evaluate(&spline.nodes, &spline.knots, spline.nodes.len() - 1, &point);
                    v[base + lane] = value as f64;
                });
            }
            FLAT_CACHE => {
                let Some(index) = c.flat else {
                    return Err(Error::OutsideFlatCache);
                };
                let size = (self.geometry.flat_size * self.geometry.flat_size) as usize;
                let value = self.flat[n.a as usize * size + index];
                let v = &mut self.values;
                lanes(m, |lane| v[base + lane] = value);
            }
            CACHE_2D => {
                self.eval(n.a as usize, m, c)?;
                let v = &mut self.values;
                lanes(m, |lane| v[base + lane] = v[a + lane]);
            }
            _ => unreachable!(),
        }
        Ok(())
    }
}

#[inline]
fn lanes(mask: u64, mut f: impl FnMut(usize)) {
    let mut bits = mask;
    while bits != 0 {
        f(bits.trailing_zeros() as usize);
        bits &= bits - 1;
    }
}

/// One point takes the single-sample path Java's compute uses; more take the
/// batch path Java's array fills use. Both are the production samplers.
fn sample(state: *const State, xyz: &[f64], out: &mut [f64], count: usize) {
    if count == 1 {
        out[0] = unsafe { noise_eval(state, xyz[0], xyz[1], xyz[2], 0., 0., 0) };
    } else if count > 1 {
        let status = unsafe { noise_batch(state, xyz.as_ptr(), out.as_mut_ptr(), count as u32, 0., 0., 0) };
        debug_assert_eq!(0, status);
    }
}

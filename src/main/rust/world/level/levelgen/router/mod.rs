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
/// Only as a point program's root: `p` lower bound, `q` cell height,
/// `a` density, `b` upper bound.
pub(crate) const FIND_TOP_SURFACE: u32 = 36;
/// Point programs only: a chunk FlatCache at an arbitrary point. Inside the
/// chunk's quart grid, `a`'s value at the quart corner with Y 0 (memoized in
/// slot `b` of the caller's `Binding`); outside, `a` at the point.
pub(crate) const FLAT_POINT: u32 = 37;

/// A chunk's FlatCache grid and the corner values computed so far, owned by
/// the caller for the chunk's lifetime.
pub(crate) struct Binding<'a> {
    pub first_x: i32,
    pub first_z: i32,
    pub size: i32,
    pub memo: &'a mut [f64],
    pub present: &'a mut [u8],
}

thread_local! {
    // Frames for corner evaluations nested inside another evaluation.
    static NESTED: std::cell::RefCell<Vec<Frame>> = const { std::cell::RefCell::new(Vec::new()) };
}

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

/// Runs `body` with a scratch frame for `program` from this thread's pool, so
/// evaluations may nest (a surface level inside a fluid status, a corner
/// inside a point).
pub(crate) fn with_frame<R>(program: &Program, body: impl FnOnce(&mut Frame) -> R) -> R {
    let mut frame = NESTED.with(|pool| pool.borrow_mut().pop()).unwrap_or_else(|| Frame::new(0));
    if frame.done.len() < program.nodes.len() {
        frame = Frame::new(program.nodes.len());
    }
    let result = body(&mut frame);
    NESTED.with(|pool| pool.borrow_mut().push(frame));
    result
}

/// An immutable validated program; evaluation state lives in a `Frame`, so
/// one program can serve concurrent callers.
pub(crate) struct Program {
    nodes: Vec<Node>,
    y_independent: Vec<bool>,
    splines: Vec<Spline>,
    roots: Vec<u32>,
    flat_slots: usize,
    /// For a FindTopSurface root: the Y-independent nodes its density reads
    /// from Y-dependent nodes, evaluated once per column before the search.
    frontier: Vec<u32>,
}

// Noise states are immutable and kept alive by Java for the program's lifetime.
unsafe impl Send for Program {}
unsafe impl Sync for Program {}

/// Per-evaluation lane values and the lanes computed so far for each node.
pub(crate) struct Frame {
    values: Vec<f64>,
    done: Vec<u64>,
    /// Per-column frontier values between a surface batch's two phases.
    saved: Vec<f64>,
}

impl Frame {
    pub(crate) fn new(nodes: usize) -> Frame {
        Frame { values: vec![0.; nodes * LANES], done: vec![0; nodes], saved: vec![0.; nodes * LANES] }
    }
}

/// A slice filler: a program (its own, or a shared per-seed template), one
/// chunk's geometry and FlatCache tables, and a frame; used by one thread.
pub(crate) struct Router {
    program: RouterProgram,
    geometry: Geometry,
    flat: Vec<f64>,
    frame: Frame,
}

enum RouterProgram {
    Owned(Program),
    /// A template that outlives the router (Java keeps it reachable).
    Shared(*const Program),
}

/// The points of one evaluation: per-lane block coordinates. `shared` lanes
/// are one column at several Ys; otherwise each lane is its own column.
struct Column {
    xs: [i32; LANES],
    zs: [i32; LANES],
    ys: [f64; LANES],
    flat: Option<usize>,
    shared: bool,
    /// The chunk binding for FLAT_POINT nodes, or null.
    binding: *mut Binding<'static>,
    /// FlatCache tables for FLAT_CACHE nodes (slot-major), or null.
    tables: *const f64,
    table_size: usize,
}

impl Column {
    fn shared(x: i32, z: i32, ys: [f64; LANES], flat: Option<usize>) -> Column {
        Column { xs: [x; LANES], zs: [z; LANES], ys, flat, shared: true, binding: std::ptr::null_mut(), tables: std::ptr::null(), table_size: 0 }
    }
}

fn children(n: &Node) -> &'static [usize] {
    match n.op {
        5 | RANGE_CHOICE => &[0, 1, 2],
        8..=11 => &[0, 1],
        6 | 7 | 12..=21 | CACHE_2D | FLAT_POINT => &[0],
        FIND_TOP_SURFACE => &[0, 1],
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
    pub(crate) fn new(
        nodes: Vec<Node>,
        splines: Vec<Spline>,
        flat: Vec<f64>,
        flat_slots: usize,
        roots: Vec<u32>,
        geometry: Geometry,
    ) -> Result<Router, Invalid> {
        if nodes.iter().any(|n| n.op == FIND_TOP_SURFACE || n.op == FLAT_POINT) {
            return Err(Invalid::Operation(nodes.len() - 1));
        }
        let program = Program::new(nodes, splines, flat.clone(), flat_slots, roots, geometry)?;
        let frame = Frame::new(program.nodes.len());
        Ok(Router { program: RouterProgram::Owned(program), geometry, flat, frame })
    }

    /// One chunk's router over a shared slice template. `flats` (the template's
    /// FlatCache inputs, one root per slot) fills the tables as FlatCache does:
    /// each input at the chunk grid's quart corners with Y 0.
    /// # Safety
    /// `template` and `flats` outlive the router.
    pub(crate) unsafe fn instance(template: *const Program, flats: Option<&Program>, geometry: Geometry) -> Result<Router, Invalid> {
        let program = unsafe { &*template };
        let g = geometry;
        let size = (g.flat_size * g.flat_size) as usize;
        if g.columns == 0 || g.columns > 64 || g.points == 0 || g.points > 8192 || !(1..=8).contains(&g.flat_size)
            || flats.map_or(program.flat_slots != 0, |f| f.roots.len() < program.flat_slots)
        {
            return Err(Invalid::Size);
        }
        // Nested FlatCaches may add slots after the template's; its tables are the prefix.
        let mut flat = vec![0.; flats.map_or(0, |f| f.roots.len()) * size];
        if let Some(flats) = flats {
            let mut present = vec![0u8; flat.len()];
            let mut binding = Binding { first_x: g.first_noise_x, first_z: g.first_noise_z, size: g.flat_size, memo: &mut flat, present: &mut present };
            with_frame(flats, |frame| flats.flat_tables(frame, &mut binding)).map_err(|_| Invalid::Size)?;
        }
        flat.truncate(program.flat_slots * size);
        Ok(Router { program: RouterProgram::Shared(template), geometry, flat, frame: Frame::new(program.nodes.len()) })
    }

    fn program(&self) -> &Program {
        match &self.program {
            RouterProgram::Owned(program) => program,
            RouterProgram::Shared(program) => unsafe { &**program },
        }
    }

    pub(crate) fn output_len(&self) -> usize {
        self.program().roots.len() * self.geometry.columns * self.geometry.points
    }

    pub(crate) fn slice(&mut self, x: i32, out: &mut [f64]) -> Result<(), Error> {
        let program = match &self.program {
            RouterProgram::Owned(program) => program as *const Program,
            RouterProgram::Shared(program) => *program,
        };
        unsafe { &*program }.slice_with(&mut self.frame, &self.geometry, &self.flat, x, out)
    }
}

impl Program {
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
    ) -> Result<Program, Invalid> {
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
            let known = matches!(n.op, CONSTANT..=RANGE_CHOICE | Y_CLAMPED_GRADIENT..=FLAT_POINT);
            // FindTopSurface evaluates its inputs at other points: root only.
            let search = n.op == FIND_TOP_SURFACE
                && (i + 1 != nodes.len() || roots.len() != 1 || !(1. ..=4096.).contains(&n.q) || n.q.fract() != 0.
                    || !(-1e9..=1e9).contains(&n.p) || n.p.fract() != 0.);
            if !known || search {
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
                2 | 6 | 7 | Y_CLAMPED_GRADIENT | BLENDED_NOISE | FIND_TOP_SURFACE => false,
                8..=RANGE_CHOICE | CACHE_2D | FLAT_POINT => children(n).iter().all(|index| independent(*index)),
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
        let mut frontier = vec![];
        if let Some(n) = nodes.last().filter(|n| n.op == FIND_TOP_SURFACE) {
            let mut seen = vec![false; nodes.len()];
            let mut stack = vec![n.a as usize];
            while let Some(i) = stack.pop() {
                if std::mem::replace(&mut seen[i], true) {
                    continue;
                }
                if y_independent[i] {
                    frontier.push(i as u32);
                    continue;
                }
                let n = &nodes[i];
                stack.extend(children(n).iter().map(|index| child(n, *index) as usize));
                if n.op == SPLINE {
                    let spline = &splines[n.a as usize];
                    stack.extend(spline.axes[..spline.axis_count].iter().map(|axis| *axis as usize));
                }
            }
        }
        Ok(Program { nodes, y_independent, splines, roots, flat_slots, frontier })
    }

    pub(crate) fn root_count(&self) -> usize {
        self.roots.len()
    }

    /// FLAT_POINT memo slots a binding must provide.
    pub(crate) fn point_slots(&self) -> usize {
        self.nodes.iter().filter(|n| n.op == FLAT_POINT).map(|n| n.b as usize + 1).max().unwrap_or(0)
    }

    /// Root `root` at one block position (a Java SinglePointContext).
    pub(crate) fn point_value(&self, f: &mut Frame, root: usize, x: i32, y: i32, z: i32, binding: &mut Binding) -> Result<f64, Error> {
        let mut ys = [0.; LANES];
        ys[0] = y as f64;
        let mut c = Column::shared(x, z, ys, None);
        c.binding = (binding as *mut Binding).cast();
        f.done.fill(0);
        let node = self.roots[root] as usize;
        self.eval(f, node, 1, &c)?;
        Ok(f.values[node * LANES])
    }

    /// Every root at (x, y, z) for up to 64 Ys of one column, through a chunk
    /// binding: `out` is root-major (`out[root * ys.len() + lane]`). The
    /// column's Y-independent values are computed once.
    pub(crate) fn column_points(&self, f: &mut Frame, x: i32, z: i32, ys: &[f64], binding: &mut Binding, out: &mut [f64]) -> Result<(), Error> {
        let count = ys.len();
        debug_assert!(count <= LANES && out.len() == self.roots.len() * count);
        if count == 0 {
            return Ok(());
        }
        let mut lanes_y = [0.; LANES];
        lanes_y[..count].copy_from_slice(ys);
        let mut c = Column::shared(x, z, lanes_y, None);
        c.binding = (binding as *mut Binding).cast();
        let all = if count == LANES { !0 } else { (1u64 << count) - 1 };
        f.done.fill(0);
        for (k, root) in self.roots.iter().enumerate() {
            let root = *root as usize;
            self.eval(f, root, all, &c)?;
            out[k * count..(k + 1) * count].copy_from_slice(&f.values[root * LANES..root * LANES + count]);
        }
        Ok(())
    }

    /// FLAT_POINT at the lanes of `m`: memoized corner values inside the grid,
    /// the input at the point outside it (and without a binding).
    fn flat_point(&self, f: &mut Frame, i: usize, m: u64, c: &Column) -> Result<(), Error> {
        let n = self.nodes[i];
        let mut outside = 0u64;
        let mut inside = [usize::MAX; LANES];
        if c.binding.is_null() {
            outside = m;
        } else {
            let b = unsafe { &*c.binding };
            lanes(m, |lane| {
                let k = (c.xs[lane] >> 2).wrapping_sub(b.first_x);
                let l = (c.zs[lane] >> 2).wrapping_sub(b.first_z);
                if k >= 0 && l >= 0 && k < b.size && l < b.size {
                    inside[lane] = (n.b as usize * b.size as usize + l as usize) * b.size as usize + k as usize;
                } else {
                    outside |= 1 << lane;
                }
            });
        }
        if outside != 0 {
            self.eval(f, n.a as usize, outside, c)?;
        }
        let mut bits = m & !outside;
        while bits != 0 {
            let lane = bits.trailing_zeros() as usize;
            bits &= bits - 1;
            let index = inside[lane];
            let binding = unsafe { &mut *c.binding };
            if binding.present[index] == 0 {
                // FlatCache filled this cell with its input at (corner, 0, corner).
                let size = binding.size as usize;
                let (k, l) = ((index % (size * size)) % size, (index % (size * size)) / size);
                let corner_x = binding.first_x.wrapping_add(k as i32).wrapping_mul(4);
                let corner_z = binding.first_z.wrapping_add(l as i32).wrapping_mul(4);
                let mut corner = Column::shared(corner_x, corner_z, [0.; LANES], None);
                corner.binding = c.binding;
                let mut frame = NESTED.with(|pool| pool.borrow_mut().pop()).unwrap_or_else(|| Frame::new(0));
                if frame.done.len() < self.nodes.len() {
                    frame = Frame::new(self.nodes.len());
                }
                frame.done.fill(0);
                let result = self.eval(&mut frame, n.a as usize, 1, &corner);
                let value = frame.values[n.a as usize * LANES];
                NESTED.with(|pool| pool.borrow_mut().push(frame));
                result?;
                let binding = unsafe { &mut *c.binding };
                binding.memo[index] = value;
                binding.present[index] = 1;
            }
            let value = unsafe { (&*c.binding).memo[index] };
            f.values[i * LANES + lane] = value;
        }
        let v = &mut f.values;
        let a = n.a as usize * LANES;
        lanes(outside, |lane| v[i * LANES + lane] = v[a + lane]);
        Ok(())
    }

    /// `NoiseChunk.computePreliminarySurfaceLevel` for up to 64 columns:
    /// `Mth.floor` of the root at (x, 0, z). Each column is a lane, so the
    /// noises behind the columns' Y-independent values run as batches. A
    /// FindTopSurface root first evaluates every column's upper bound and
    /// density frontier this way, then scans each column down from its bound
    /// one cell height at a time, as Java does, a few Ys at a time so the
    /// search stops near the first solid point.
    pub(crate) fn surface_levels(&self, f: &mut Frame, xs: &[i32], zs: &[i32], out: &mut [i32]) -> Result<(), Error> {
        let count = xs.len();
        debug_assert!(count <= LANES && zs.len() == count && out.len() == count);
        if count == 0 {
            return Ok(());
        }
        let all = if count == LANES { !0 } else { (1u64 << count) - 1 };
        let mut c = Column { xs: [xs[0]; LANES], zs: [zs[0]; LANES], ys: [0.; LANES], flat: None, shared: false, binding: std::ptr::null_mut(),
            tables: std::ptr::null(), table_size: 0 };
        c.xs[..count].copy_from_slice(xs);
        c.zs[..count].copy_from_slice(zs);
        f.done.fill(0);
        let root = self.roots[0] as usize;
        let n = self.nodes[root];
        if n.op != FIND_TOP_SURFACE {
            self.eval(f, root, all, &c)?;
            for (k, level) in out.iter_mut().enumerate() {
                *level = floor(f.values[root * LANES + k]);
            }
            return Ok(());
        }
        self.eval(f, n.b as usize, all, &c)?;
        for node in &self.frontier {
            self.eval(f, *node as usize, all, &c)?;
        }
        let mut upper = [0.; LANES];
        upper.copy_from_slice(&f.values[n.b as usize * LANES..(n.b as usize + 1) * LANES]);
        for (index, node) in self.frontier.iter().enumerate() {
            let at = *node as usize * LANES;
            f.saved[index * LANES..(index + 1) * LANES].copy_from_slice(&f.values[at..at + LANES]);
        }
        for k in 0..count {
            out[k] = self.search(f, n, xs[k], zs[k], upper[k], k)?;
        }
        Ok(())
    }

    /// One column's FindTopSurface scan, with its frontier values restored.
    fn search(&self, f: &mut Frame, n: Node, x: i32, z: i32, upper: f64, column: usize) -> Result<i32, Error> {
        let (density, lower, height) = (n.a as usize, n.p as i32, n.q as i32);
        let top = floor(upper / height as f64).wrapping_mul(height);
        if top <= lower {
            return Ok(lower);
        }
        f.done.fill(0);
        for (index, node) in self.frontier.iter().enumerate() {
            let at = *node as usize;
            f.values[at * LANES..(at + 1) * LANES].fill(f.saved[index * LANES + column]);
            f.done[at] = !0;
        }
        let mut c = Column::shared(x, z, [0.; LANES], None);
        let mut next = top as i64;
        while next >= lower as i64 {
            let count = ((next - lower as i64) / height as i64 + 1).min(LANES as i64) as usize;
            for lane in 0..count {
                c.ys[lane] = (next - lane as i64 * height as i64) as f64;
            }
            // The lanes hold new Ys: keep only values that cannot depend on Y.
            for (done, independent) in f.done.iter_mut().zip(&self.y_independent) {
                if !independent {
                    *done = 0;
                }
            }
            let (mut start, mut group) = (0, 4);
            while start < count {
                let end = (start + group).min(count);
                let mask = if end == LANES { !0u64 << start } else { ((1u64 << end) - 1) & (!0u64 << start) };
                self.eval(f, density, mask, &c)?;
                for lane in start..end {
                    if f.values[density * LANES + lane] > 0. {
                        return Ok(c.ys[lane] as i32);
                    }
                }
                start = end;
                group = (group * 2).min(16);
            }
            next -= count as i64 * height as i64;
        }
        Ok(lower)
    }

    pub(crate) fn has_point_nodes(&self) -> bool {
        self.nodes.iter().any(|n| n.op == FIND_TOP_SURFACE || n.op == FLAT_POINT)
    }

    /// FlatCache tables for a chunk: root `s` (slot `s`'s input) at every quart
    /// corner of the binding's grid with Y 0, written to slot `s` of the memo.
    /// Corners are lanes, so their noises run as batches.
    pub(crate) fn flat_tables(&self, f: &mut Frame, binding: &mut Binding) -> Result<(), Error> {
        let size = binding.size as usize;
        let count = size * size;
        if count > LANES || binding.memo.len() != self.roots.len() * count {
            return Err(Error::OutsideFlatCache);
        }
        let mut c = Column { xs: [0; LANES], zs: [0; LANES], ys: [0.; LANES], flat: None, shared: false,
            binding: (binding as *mut Binding).cast(), tables: std::ptr::null(), table_size: 0 };
        for index in 0..count {
            c.xs[index] = binding.first_x.wrapping_add((index % size) as i32).wrapping_mul(4);
            c.zs[index] = binding.first_z.wrapping_add((index / size) as i32).wrapping_mul(4);
        }
        let all = if count == LANES { !0 } else { (1u64 << count) - 1 };
        for slot in 0..self.roots.len() {
            let binding = unsafe { &mut *c.binding };
            if binding.present[slot * count..(slot + 1) * count].iter().all(|p| *p != 0) {
                continue;
            }
            f.done.fill(0);
            let root = self.roots[slot] as usize;
            self.eval(f, root, all, &c)?;
            let binding = unsafe { &mut *c.binding };
            binding.memo[slot * count..(slot + 1) * count].copy_from_slice(&f.values[root * LANES..root * LANES + count]);
            binding.present[slot * count..(slot + 1) * count].fill(1);
        }
        Ok(())
    }

    /// Fills one slice at block X: for each interpolator, each Z column, each
    /// cell corner Y, matching fillSlice's points exactly. Output is
    /// interpolator-major, then column, then Y.
    fn slice_with(&self, f: &mut Frame, geometry: &Geometry, tables: &[f64], x: i32, out: &mut [f64]) -> Result<(), Error> {
        let g = *geometry;
        debug_assert_eq!(out.len(), self.roots.len() * g.columns * g.points);
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
                let mut c = Column::shared(x, z, ys, flat);
                c.tables = tables.as_ptr();
                c.table_size = (g.flat_size * g.flat_size) as usize;
                f.done.fill(0);
                for k in 0..self.roots.len() {
                    let root = self.roots[k] as usize;
                    self.eval(f, root, all, &c)?;
                    let at = (k * g.columns + column) * g.points + start;
                    out[at..at + lanes].copy_from_slice(&f.values[root * LANES..root * LANES + lanes]);
                }
                start += lanes;
            }
        }
        Ok(())
    }

    fn eval(&self, f: &mut Frame, i: usize, need: u64, c: &Column) -> Result<(), Error> {
        let todo = need & !f.done[i];
        if todo == 0 {
            return Ok(());
        }
        // One column's Y-independent values are the same in every lane.
        if self.y_independent[i] && c.shared {
            let lane = todo.trailing_zeros() as usize;
            self.compute(f, i, 1 << lane, c)?;
            let value = f.values[i * LANES + lane];
            f.values[i * LANES..(i + 1) * LANES].fill(value);
            f.done[i] = !0;
        } else {
            self.compute(f, i, todo, c)?;
            f.done[i] |= todo;
        }
        Ok(())
    }

    fn compute(&self, f: &mut Frame, i: usize, m: u64, c: &Column) -> Result<(), Error> {
        let n = self.nodes[i];
        let base = i * LANES;
        // Children first; afterwards only `values` is borrowed.
        let (a, b, cc) = (n.a as usize * LANES, n.b as usize * LANES, n.c as usize * LANES);
        match n.op {
            CONSTANT => {
                let v = &mut f.values;
                lanes(m, |lane| v[base + lane] = n.p);
            }
            1..=7 => {
                match n.op {
                    5 => {
                        self.eval(f, n.a as usize, m, c)?;
                        self.eval(f, n.b as usize, m, c)?;
                        self.eval(f, n.c as usize, m, c)?;
                    }
                    6 | 7 => self.eval(f, n.a as usize, m, c)?,
                    _ => {}
                }
                let v = &mut f.values;
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
                    let (x, z) = (c.xs[lane] as f64, c.zs[lane] as f64);
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
                self.eval(f, n.a as usize, m, c)?;
                let mut right = 0;
                lanes(m, |lane| {
                    if needs_right(n.op, f.values[a + lane], n.p, n.q) {
                        right |= 1 << lane;
                    }
                });
                if right != 0 {
                    self.eval(f, n.b as usize, right, c)?;
                }
                let v = &mut f.values;
                lanes(m, |lane| {
                    // Lanes outside `right` are decided by the left operand alone.
                    let rhs = if right & (1 << lane) != 0 { v[b + lane] } else { 0. };
                    let value = math(n.op, v[a + lane], rhs, n.p, n.q);
                    v[base + lane] = value;
                });
            }
            12..=21 => {
                self.eval(f, n.a as usize, m, c)?;
                let v = &mut f.values;
                lanes(m, |lane| {
                    let value = math(n.op, v[a + lane], 0., n.p, n.q);
                    v[base + lane] = value;
                });
            }
            RANGE_CHOICE => {
                self.eval(f, n.a as usize, m, c)?;
                let mut inside = 0;
                lanes(m, |lane| {
                    if in_range(f.values[a + lane], n.p, n.q) {
                        inside |= 1 << lane;
                    }
                });
                if inside != 0 {
                    self.eval(f, n.b as usize, inside, c)?;
                }
                if m & !inside != 0 {
                    self.eval(f, n.c as usize, m & !inside, c)?;
                }
                let v = &mut f.values;
                lanes(m, |lane| {
                    let value = if inside & (1 << lane) != 0 { v[b + lane] } else { v[cc + lane] };
                    v[base + lane] = value;
                });
            }
            Y_CLAMPED_GRADIENT => {
                let v = &mut f.values;
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
                    xyz[count * 3..count * 3 + 3].copy_from_slice(&[c.xs[lane] as f64, c.ys[lane], c.zs[lane] as f64]);
                    order[count] = lane;
                    count += 1;
                });
                let mut raw = [0.; LANES];
                sample(n.state, &xyz, &mut raw, count);
                for k in 0..count {
                    f.values[base + order[k]] = raw[k];
                }
            }
            END_ISLANDS => {
                // (endIslandHeight(x / 8, z / 8) - 8.0) / 128.0, with Java int division.
                let v = &mut f.values;
                lanes(m, |lane| {
                    let height = unsafe { noise_end_island(n.state, c.xs[lane] / 8, c.zs[lane] / 8) };
                    v[base + lane] = (height as f64 - 8.0) / 128.0;
                });
            }
            SPLINE => {
                let (axes, count) = (self.splines[n.a as usize].axes, self.splines[n.a as usize].axis_count);
                for axis in &axes[..count] {
                    self.eval(f, *axis as usize, m, c)?;
                }
                let spline = &self.splines[n.a as usize];
                let v = &mut f.values;
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
                if c.tables.is_null() {
                    return Err(Error::OutsideFlatCache);
                }
                let value = unsafe { *c.tables.add(n.a as usize * c.table_size + index) };
                let v = &mut f.values;
                lanes(m, |lane| v[base + lane] = value);
            }
            FLAT_POINT => self.flat_point(f, i, m, c)?,
            CACHE_2D => {
                self.eval(f, n.a as usize, m, c)?;
                let v = &mut f.values;
                lanes(m, |lane| v[base + lane] = v[a + lane]);
            }
            _ => unreachable!(),
        }
        Ok(())
    }
}

/// `Mth.floor(double)`, including Java's saturating, NaN-to-zero int cast.
fn floor(value: f64) -> i32 {
    let i = value as i32;
    if value < i as f64 {
        i.wrapping_sub(1)
    } else {
        i
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

//! Cell interpolation and exact-order evaluation.
use super::{
    math::*,
    program::{Header, Node},
};
use terrain::{terrain_cell_array, terrain_cell_shape};
use unary::unary_cell_array;
#[cfg(target_arch = "x86_64")]
mod avx2;
mod terrain;
mod unary;

// A cell program is a tree of pure operations and cached interpolation corners.
// The trace is the last observable fill traversal phase, plus direct-fill kind.
// Node order is postorder, so a later phase always has the larger trace code.
pub(crate) fn eval_cell(nodes: &[Node], i: usize, frame: &[f64], xyz: [f64; 3]) -> (f64, u32) {
    let n = &nodes[i];
    let child = |j: u32| eval_cell(nodes, j as usize, frame, xyz);
    let visit = ((i as u32 + 1) << 1) as u32;
    match n.op {
        0 => (n.p, 0),
        24 => {
            let at = n.a as usize * 8;
            let corners: [f64; 8] = frame[at..at + 8].try_into().unwrap();
            (
                crate::world::level::levelgen::math::lerp3(xyz[0], xyz[1], xyz[2], corners),
                visit | 1,
            )
        }
        8 => {
            let (a, ta) = child(n.a);
            let (b, tb) = child(n.b);
            (a + b, if tb != 0 { tb } else { ta })
        }
        9..=11 => {
            let (a, trace) = child(n.a);
            if needs_right(n.op, a, n.p, n.q) {
                (math(n.op, a, child(n.b).0, n.p, n.q), visit)
            } else {
                (math(n.op, a, 0., n.p, n.q), trace)
            }
        }
        12..=21 => {
            let (a, trace) = child(n.a);
            (math(n.op, a, 0., n.p, n.q), trace)
        }
        22 => {
            let a = child(n.a).0;
            (
                child(if in_range(a, n.p, n.q) { n.b } else { n.c }).0,
                visit,
            )
        }
        _ => unreachable!(),
    }
}
/// # Safety
/// Base is an immutable validated cell program. Frame has 8 doubles per cell
/// input, remains live through the call, and is never retained by Rust.
pub(crate) unsafe fn density_cell(
    base: *const u8,
    frame: *const f64,
    x: f64,
    y: f64,
    z: f64,
) -> f64 {
    let h = unsafe { &*base.cast::<Header>() };
    if h.cells == 0 || frame.is_null() {
        return f64::NAN;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    let frame = unsafe { std::slice::from_raw_parts(frame, h.cells as usize * 8) };
    eval_cell(nodes, nodes.len() - 1, frame, [x, y, z]).0
}
/// # Safety
/// Same validated program/frame contract as density_cell. Output has count
/// writable doubles and cannot alias frame or program. Each call is <=256 points.
pub(crate) unsafe fn density_cell_array(
    base: *const u8,
    frame: *const f64,
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
) -> i64 {
    let h = unsafe { &*base.cast::<Header>() };
    if h.cells == 0
        || frame.is_null()
        || output.is_null()
        || count > 256
        || width == 0
        || width > 64
        || height == 0
        || height > 4096
        || start as u64 + count as u64 > width as u64 * width as u64 * height as u64
    {
        return -2;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    let frame = unsafe { std::slice::from_raw_parts(frame, h.cells as usize * 8) };
    if count == 0 {
        return -1;
    }
    let trailing_add = nodes.len() >= 3
        && nodes[nodes.len() - 1].op == 8
        && nodes[nodes.len() - 1].a as usize == nodes.len() - 3
        && nodes[nodes.len() - 1].b as usize == nodes.len() - 2
        && nodes[nodes.len() - 2].op == 0;
    let core = if trailing_add {
        &nodes[..nodes.len() - 2]
    } else {
        nodes
    };
    if core.len() == 3
        && core[0].op == 24
        && core[1].op == 12
        && core[1].a == 0
        && core[2].op == 20
        && core[2].a == 1
    {
        return if trailing_add {
            unsafe {
                unary_cell_array::<true>(
                    core,
                    frame,
                    output,
                    start,
                    count,
                    width,
                    height,
                    nodes[nodes.len() - 2].p,
                )
            }
        } else {
            unsafe {
                unary_cell_array::<false>(core, frame, output, start, count, width, height, 0.)
            }
        };
    }
    if terrain_cell_shape(core) {
        return if trailing_add {
            unsafe {
                terrain_cell_array::<true>(
                    core,
                    frame,
                    output,
                    start,
                    count,
                    width,
                    height,
                    nodes[nodes.len() - 2].p,
                )
            }
        } else {
            unsafe {
                terrain_cell_array::<false>(core, frame, output, start, count, width, height, 0.)
            }
        };
    }
    // Evaluate one operation across the entire window. All leaves are already
    // captured pure interpolation frames; speculative arithmetic has no context
    // or cache effects. This permits SIMD across points without reassociation.
    let count = count as usize;
    let mut values = [[std::mem::MaybeUninit::<f64>::uninit(); 256]; 48];

    let mut xs = [0.; 256];
    let mut ys = [0.; 256];
    let mut zs = [0.; 256];
    for j in 0..count {
        let i = start + j as u32;
        xs[j] = ((i / width) % width) as f64 / width as f64;
        ys[j] = (height - 1 - i / (width * width)) as f64 / height as f64;
        zs[j] = (i % width) as f64 / width as f64;
    }
    for (index, n) in nodes.iter().enumerate() {
        let value = |node: u32, j: usize| unsafe {
            values
                .get_unchecked(node as usize)
                .get_unchecked(j)
                .assume_init()
        };
        // Write through temporary rows so references to previously initialized
        // children never alias the current mutable row.
        let mut row = [0.; 256];
        match n.op {
            0 => row[..count].fill(n.p),
            24 => {
                let at = n.a as usize * 8;
                let v: [f64; 8] = frame[at..at + 8].try_into().unwrap();
                for j in 0..count {
                    row[j] = crate::world::level::levelgen::math::lerp3(xs[j], ys[j], zs[j], v);
                }
            }
            8..=11 => {
                for j in 0..count {
                    row[j] = math(n.op, value(n.a, j), value(n.b, j), n.p, n.q);
                }
            }
            12..=21 => {
                for j in 0..count {
                    row[j] = math(n.op, value(n.a, j), 0., n.p, n.q);
                }
            }
            22 => {
                for j in 0..count {
                    row[j] = if in_range(value(n.a, j), n.p, n.q) {
                        value(n.b, j)
                    } else {
                        value(n.c, j)
                    };
                }
            }
            _ => unreachable!(),
        }
        for j in 0..count {
            values[index][j].write(row[j]);
        }
    }
    let root = nodes.len() - 1;
    for j in 0..count {
        unsafe {
            *output.add(j) = values[root][j].assume_init();
        }
    }
    // Only the last active traversal phase is observable. Mark nodes whose
    // fillArray method would be called (conditional right children use compute),
    // then find that phase in reverse postorder. No per-point trace matrix.
    let mut filled = [false; 48];
    filled[root] = true;
    for i in (0..nodes.len()).rev() {
        if !filled[i] {
            continue;
        }
        let n = &nodes[i];
        if (8..=22).contains(&n.op) {
            filled[n.a as usize] = true;
        }
        if n.op == 8 {
            filled[n.b as usize] = true;
        }
    }
    if count == 0 {
        return -1;
    }
    for i in (0..nodes.len()).rev() {
        if !filled[i] {
            continue;
        }
        let n = &nodes[i];
        let visit = (i as i64 + 1) << 1;
        if n.op == 24 || n.op == 22 {
            return ((visit | if n.op == 24 { 1 } else { 0 }) << 32)
                | (start as i64 + count as i64 - 1);
        }
        if (9..=11).contains(&n.op) {
            for j in (0..count).rev() {
                if needs_right(
                    n.op,
                    unsafe { values[n.a as usize][j].assume_init() },
                    n.p,
                    n.q,
                ) {
                    return (visit << 32) | (start as i64 + j as i64);
                }
            }
        }
    }
    -1
}

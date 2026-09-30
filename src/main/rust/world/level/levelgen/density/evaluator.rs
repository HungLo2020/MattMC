//! Noise leaves and fused expression evaluation.
use super::super::synth::{noise_batch, noise_eval, State};
use super::{
    math::*,
    program::{Header, Node},
};
#[inline]
pub(crate) fn coordinates(
    op: u32,
    x: f64,
    y: f64,
    z: f64,
    sx: f64,
    sy: f64,
    sz: f64,
    p: f64,
    q: f64,
) -> ([f64; 3], f64) {
    match op {
        1 => ([x * p, y * q, z * p], 1.),
        2 => ([x * 0.25, y * 0.25, z * 0.25], 4.),
        3 => ([x * 0.25, 0., z * 0.25], 4.),
        4 => ([z * 0.25, x * 0.25, 0.], 4.),
        5 => ([x * p + sx, y * q + sy, z * p + sz], 1.),
        6 | 7 => {
            let e = rarity(op, sx);
            ([x / e, y / e, z / e], e)
        }
        _ => ([f64::NAN; 3], f64::NAN),
    }
}
#[inline]
pub(crate) fn finish_noise(op: u32, value: f64, factor: f64) -> f64 {
    match op {
        2..=4 => value * factor,
        6 | 7 => factor * value.abs(),
        _ => value,
    }
}
/// # Safety
/// State is null (an unbound noise holder) or an immutable, validated NormalNoise
/// state. Java selects an ordinary downcall for unusually large octave counts.
pub(crate) unsafe fn density_noise(
    state: *const State,
    op: u32,
    x: f64,
    y: f64,
    z: f64,
    sx: f64,
    sy: f64,
    sz: f64,
    p: f64,
    q: f64,
) -> f64 {
    let (xyz, factor) = coordinates(op, x, y, z, sx, sy, sz, p, q);
    let value = if state.is_null() {
        0.
    } else {
        unsafe { noise_eval(state, xyz[0], xyz[1], xyz[2], 0., 0., 0) }
    };
    finish_noise(op, value, factor)
}
/// # Safety
/// Same state contract as density_noise. Inputs contain count * 6 readable
/// doubles (raw xyz and shift/input values); output contains count writable
/// doubles and does not alias input. Java bounds count and critical-call work.
pub(crate) unsafe fn density_noise_batch(
    state: *const State,
    op: u32,
    input: *const f64,
    output: *mut f64,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    if input.is_null() || output.is_null() || count > 256 || !(1..=7).contains(&op) {
        return -1;
    }
    let input = unsafe { std::slice::from_raw_parts(input, count as usize * 6) };
    let mut xyz = [0.; 256 * 3];
    let mut factors = [0.; 256];
    for i in 0..count as usize {
        let at = i * 6;
        let (point, factor) = coordinates(
            op,
            input[at],
            input[at + 1],
            input[at + 2],
            input[at + 3],
            input[at + 4],
            input[at + 5],
            p,
            q,
        );
        xyz[i * 3..i * 3 + 3].copy_from_slice(&point);
        factors[i] = factor;
    }
    if state.is_null() {
        unsafe { std::slice::from_raw_parts_mut(output, count as usize) }.fill(0.);
    } else {
        let status = unsafe { noise_batch(state, xyz.as_ptr(), output, count, 0., 0., 0) };
        if status != 0 {
            return status;
        }
    }
    for i in 0..count as usize {
        unsafe {
            *output.add(i) = finish_noise(op, *output.add(i), factors[i]);
        }
    }
    0
}
pub(crate) unsafe fn eval(
    base: *const u8,
    nodes: &[Node],
    i: usize,
    x: f64,
    y: f64,
    z: f64,
) -> f64 {
    let n = &nodes[i];
    let child = |j: u32| unsafe { eval(base, nodes, j as usize, x, y, z) };
    match n.op {
        0 => n.p,
        1..=7 => {
            let (sx, sy, sz) = match n.op {
                5 => (child(n.a), child(n.b), child(n.c)),
                6 | 7 => (child(n.a), 0., 0.),
                _ => (0., 0., 0.),
            };
            let state = if n.offset == 0 {
                std::ptr::null()
            } else {
                unsafe { base.add(n.offset as usize).cast::<State>() }
            };
            unsafe { density_noise(state, n.op, x, y, z, sx, sy, sz, n.p, n.q) }
        }
        8..=11 => {
            let a = child(n.a);
            let b = if needs_right(n.op, a, n.p, n.q) {
                child(n.b)
            } else {
                0.
            };
            math(n.op, a, b, n.p, n.q)
        }
        12..=21 => math(n.op, child(n.a), 0., n.p, n.q),
        22 => {
            if in_range(child(n.a), n.p, n.q) {
                child(n.b)
            } else {
                child(n.c)
            }
        }
        _ => unreachable!(),
    }
}
/// # Safety
/// `base` is a live immutable allocation accepted by density_validate.
pub(crate) unsafe fn density_eval(base: *const u8, x: f64, y: f64, z: f64) -> f64 {
    let h = unsafe { &*base.cast::<Header>() };
    if h.inputs != 0 || h.cells != 0 {
        return f64::NAN;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    unsafe { eval(base, nodes, nodes.len() - 1, x, y, z) }
}
pub(crate) fn eval_operands(nodes: &[Node], inputs: &[f64; 4]) -> f64 {
    // Validation guarantees backward references. All arithmetic is pure; Java
    // gathered only mandatory inputs, so evaluating nodes in topological order
    // cannot trigger a skipped child call or mutate a cache.
    let mut values = [std::mem::MaybeUninit::<f64>::uninit(); 48];
    for (i, n) in nodes.iter().enumerate() {
        let child = |j: u32| unsafe { values.get_unchecked(j as usize).assume_init() };
        let value = match n.op {
            0 => n.p,
            23 => inputs[n.a as usize],
            8..=11 => math(n.op, child(n.a), child(n.b), n.p, n.q),
            12..=21 => math(n.op, child(n.a), 0., n.p, n.q),
            22 => {
                if in_range(child(n.a), n.p, n.q) {
                    child(n.b)
                } else {
                    child(n.c)
                }
            }
            _ => f64::NAN,
        };
        values[i].write(value);
    }
    unsafe { values[nodes.len() - 1].assume_init() }
}
/// # Safety
/// Base is a live immutable validated program. Operand programs have no noise
/// nodes and at most four inputs. Inputs are copied scalars, never retained pointers.
pub(crate) unsafe fn density_operands(base: *const u8, a: f64, b: f64, c: f64, d: f64) -> f64 {
    let h = unsafe { &*base.cast::<Header>() };
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    eval_operands(nodes, &[a, b, c, d])
}

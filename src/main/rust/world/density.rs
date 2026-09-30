//! Bounded, immutable density expression interpreter. Child indexes point backwards;
//! noise states are validated offsets in the same allocation (no retained pointers).
use super::noise::{mattmc_noise_batch, mattmc_noise_eval, mattmc_noise_validate, State};
#[repr(C)]
struct Header {
    count: u32,
    reserved: [u32; 3],
}
#[repr(C)]
struct Node {
    op: u32,
    a: u32,
    b: u32,
    c: u32,
    offset: u64,
    bytes: u64,
    p: f64,
    q: f64,
    r: f64,
    reserved: f64,
}
#[inline]
fn min(a: f64, b: f64) -> f64 {
    if a.is_nan() {
        a
    } else if b.is_nan() {
        b
    } else if a == 0. && b == 0. {
        f64::from_bits(a.to_bits() | b.to_bits())
    } else if a <= b {
        a
    } else {
        b
    }
}
#[inline]
fn max(a: f64, b: f64) -> f64 {
    if a.is_nan() {
        a
    } else if b.is_nan() {
        b
    } else if a == 0. && b == 0. {
        f64::from_bits(a.to_bits() & b.to_bits())
    } else if a >= b {
        a
    } else {
        b
    }
}
#[inline]
fn clamp(a: f64, lo: f64, hi: f64) -> f64 {
    if a < lo {
        lo
    } else {
        min(a, hi)
    }
}
/// # Safety
/// `base` denotes `bytes` readable bytes, aligned to eight bytes. Validation must
/// succeed before evaluation; the allocation must remain immutable and live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_validate(base: *const u8, bytes: u64) -> i32 {
    if base.is_null() || base as usize % 8 != 0 || bytes < 16 {
        return -1;
    }
    let h = unsafe { &*base.cast::<Header>() };
    if h.count == 0
        || h.count > 48
        || h.reserved[0] > 4
        || h.reserved[1] > 8
        || (h.reserved[0] != 0 && h.reserved[1] != 0)
        || bytes < 16 + 64 * h.count as u64
    {
        return -2;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    let mut parents = [0u8; 48];
    let mut work = 0;
    let mut visits = [0u64; 48];
    let mut expanded_noise = [0u64; 48];
    for (i, n) in nodes.iter().enumerate() {
        if n.op > 24
            || (n.op == 23 && n.a >= h.reserved[0])
            || (n.op == 24 && n.a >= h.reserved[1])
            || ((h.reserved[0] != 0 || h.reserved[1] != 0) && (1..=7).contains(&n.op))
        {
            return -3;
        }
        let children = match n.op {
            5 | 22 => 3,
            8..=11 => 2,
            6 | 7 | 12..=21 => 1,
            _ => 0,
        };
        visits[i] = 1;
        for child in [n.a, n.b, n.c].iter().take(children) {
            if *child as usize >= i {
                return -4;
            }
            parents[*child as usize] += 1;
            if h.reserved[1] != 0 && parents[*child as usize] > 1 {
                return -9;
            }
            visits[i] += visits[*child as usize];
            expanded_noise[i] += expanded_noise[*child as usize];
        }
        if n.offset != 0 {
            if !(1..=7).contains(&n.op)
                || n.offset < 16 + 64 * h.count as u64
                || n.offset % 8 != 0
                || n.bytes < 80
                || n.offset > bytes
                || n.bytes > bytes - n.offset
            {
                return -5;
            }
            let state = unsafe { base.add(n.offset as usize).cast::<State>() };
            if unsafe { mattmc_noise_validate(state, n.bytes) } != 0
                || unsafe { *state.cast::<u32>() } != 3
            {
                return -6;
            }
            // Count uses, not just distinct states: repeated nodes also consume work.
            work += (n.bytes - 80) / 1320;
            expanded_noise[i] += (n.bytes - 80) / 1320;
        }
        if visits[i] > 512 || expanded_noise[i] > 128 {
            return -8;
        }
    }
    if work > 128 {
        return -7;
    }
    0
}
// Shared by fused programs and the Java traversal adapter. These are the only
// production implementations of the migrated density operations.
#[inline]
fn rarity(op: u32, d: f64) -> f64 {
    if op == 6 {
        if d < -0.5 {
            0.75
        } else if d < 0. {
            1.
        } else if d < 0.5 {
            1.5
        } else {
            2.
        }
    } else {
        if d < -0.75 {
            0.5
        } else if d < -0.5 {
            0.75
        } else if d < 0.5 {
            1.
        } else if d < 0.75 {
            2.
        } else {
            3.
        }
    }
}
#[inline]
fn in_range(a: f64, p: f64, q: f64) -> bool {
    a >= p && a < q
}
#[inline]
fn needs_right(op: u32, a: f64, p: f64, q: f64) -> bool {
    match op {
        9 => a != 0.,
        10 => !(a < p),
        11 => !(a > q),
        _ => true,
    }
}
#[inline]
fn math(op: u32, a: f64, b: f64, p: f64, q: f64) -> f64 {
    match op {
        8 => a + b,
        9 => {
            if a == 0. {
                0.
            } else {
                a * b
            }
        }
        10 => {
            if a < p {
                a
            } else {
                min(a, b)
            }
        }
        11 => {
            if a > q {
                a
            } else {
                max(a, b)
            }
        }
        12 => a * p,
        13 => a + p,
        14 => a.abs(),
        15 => a * a,
        16 => a * a * a,
        17 => {
            if a > 0. {
                a
            } else {
                a * 0.5
            }
        }
        18 => {
            if a > 0. {
                a
            } else {
                a * 0.25
            }
        }
        19 => 1. / a,
        20 => {
            let a = clamp(a, -1., 1.);
            a / 2. - a * a * a / 24.
        }
        21 => clamp(a, p, q),
        22 => {
            if in_range(a, p, q) {
                1.
            } else {
                0.
            }
        }
        23 => rarity(6, a),
        24 => rarity(7, a),
        _ => f64::NAN,
    }
}
#[no_mangle]
pub extern "C" fn mattmc_density_math(op: u32, a: f64, b: f64, p: f64, q: f64) -> f64 {
    math(op, a, b, p, q)
}
// 0: evaluate the right child; 1: retain the input; 2: return positive zero.
#[no_mangle]
pub extern "C" fn mattmc_density_branch(op: u32, a: f64, p: f64, q: f64) -> i32 {
    if needs_right(op, a, p, q) {
        0
    } else if op == 9 {
        2
    } else {
        1
    }
}
/// # Safety
/// Values has count readable doubles, mask has count writable bytes, no aliasing.
/// Java only batches decisions when later child visits cannot mutate these inputs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_mask(
    op: u32,
    values: *const f64,
    mask: *mut u8,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    if values.is_null() || mask.is_null() || count > 256 {
        return -1;
    }
    for i in 0..count as usize {
        unsafe {
            let a = *values.add(i);
            *mask.add(i) = if op == 22 {
                in_range(a, p, q)
            } else {
                needs_right(op, a, p, q)
            } as u8;
        }
    }
    0
}
/// # Safety
/// `values` has count writable doubles, `right` is null or has count readable
/// doubles. Elementwise aliasing is allowed. Java bounds each call to 256 points.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_math_batch(
    op: u32,
    values: *mut f64,
    right: *const f64,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    if values.is_null() || count > 256 || !(8..=24).contains(&op) {
        return -1;
    }
    for i in 0..count as usize {
        unsafe {
            let a = *values.add(i);
            let b = if right.is_null() { 0. } else { *right.add(i) };
            *values.add(i) = math(op, a, b, p, q);
        }
    }
    0
}
#[inline]
fn coordinates(
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
fn finish_noise(op: u32, value: f64, factor: f64) -> f64 {
    match op {
        2..=4 => value * factor,
        6 | 7 => factor * value.abs(),
        _ => value,
    }
}
/// # Safety
/// State is null (an unbound noise holder) or an immutable, validated NormalNoise
/// state. Java selects an ordinary downcall for unusually large octave counts.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_noise(
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
        unsafe { mattmc_noise_eval(state, xyz[0], xyz[1], xyz[2], 0., 0., 0) }
    };
    finish_noise(op, value, factor)
}
/// # Safety
/// Same state contract as density_noise. Inputs contain count * 6 readable
/// doubles (raw xyz and shift/input values); output contains count writable
/// doubles and does not alias input. Java bounds count and critical-call work.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_noise_batch(
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
        let status = unsafe { mattmc_noise_batch(state, xyz.as_ptr(), output, count, 0., 0., 0) };
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
unsafe fn eval(base: *const u8, nodes: &[Node], i: usize, x: f64, y: f64, z: f64) -> f64 {
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
            unsafe { mattmc_density_noise(state, n.op, x, y, z, sx, sy, sz, n.p, n.q) }
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
/// `base` is a live immutable allocation accepted by mattmc_density_validate.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_eval(base: *const u8, x: f64, y: f64, z: f64) -> f64 {
    let h = unsafe { &*base.cast::<Header>() };
    if h.reserved[0] != 0 || h.reserved[1] != 0 {
        return f64::NAN;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    unsafe { eval(base, nodes, nodes.len() - 1, x, y, z) }
}
fn eval_operands(nodes: &[Node], inputs: &[f64; 4]) -> f64 {
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
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_operands(
    base: *const u8,
    a: f64,
    b: f64,
    c: f64,
    d: f64,
) -> f64 {
    let h = unsafe { &*base.cast::<Header>() };
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    eval_operands(nodes, &[a, b, c, d])
}
#[repr(C)]
struct UnaryStep {
    op: u32,
    reserved: u32,
    p: f64,
    q: f64,
}
/// # Safety
/// Base denotes bytes readable bytes, aligned to eight.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_unary_validate(base: *const u8, bytes: u64) -> i32 {
    if base.is_null() || base as usize % 8 != 0 || bytes < 8 {
        return -1;
    }
    let count = unsafe { *base.cast::<u32>() } as usize;
    if count == 0 || count > 16 || bytes != 8 + 24 * count as u64 {
        return -2;
    }
    let steps = unsafe { std::slice::from_raw_parts(base.add(8).cast::<UnaryStep>(), count) };
    if steps.iter().any(|s| !(10..=21).contains(&s.op)) {
        return -3;
    }
    0
}
#[inline]
fn unary(steps: &[UnaryStep], mut value: f64) -> f64 {
    for s in steps {
        value = if s.op == 10 || s.op == 11 {
            math(s.op, value, s.p, s.p, s.p)
        } else {
            math(s.op, value, 0., s.p, s.q)
        };
    }
    value
}
/// # Safety
/// Base is an immutable allocation accepted by unary_validate.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_unary(base: *const u8, value: f64) -> f64 {
    let count = unsafe { *base.cast::<u32>() } as usize;
    let steps = unsafe { std::slice::from_raw_parts(base.add(8).cast::<UnaryStep>(), count) };
    unary(steps, value)
}
/// # Safety
/// Validated immutable program and count writable doubles, no aliasing.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_unary_array(
    base: *const u8,
    values: *mut f64,
    count: u32,
) -> i32 {
    if base.is_null() || values.is_null() || count > 256 {
        return -1;
    }
    let length = unsafe { *base.cast::<u32>() } as usize;
    let steps = unsafe { std::slice::from_raw_parts(base.add(8).cast::<UnaryStep>(), length) };
    let values = unsafe { std::slice::from_raw_parts_mut(values, count as usize) };
    // Keep the operation invariant across the inner loop so LLVM can vectorize
    // each pass. There are no child calls or observable writes between steps.
    for s in steps {
        if s.op == 10 || s.op == 11 {
            for value in &mut *values {
                *value = math(s.op, *value, s.p, s.p, s.p);
            }
        } else {
            for value in &mut *values {
                *value = math(s.op, *value, 0., s.p, s.q);
            }
        }
    }
    0
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn layout() {
        assert_eq!(std::mem::size_of::<Header>(), 16);
        assert_eq!(std::mem::size_of::<Node>(), 64);
    }
    #[test]
    fn java_min_max() {
        assert_eq!(min(0., -0.).to_bits(), (-0.0f64).to_bits());
        assert_eq!(max(-0., 0.).to_bits(), 0.0f64.to_bits());
        assert!(min(1., f64::NAN).is_nan());
        assert!(max(f64::NAN, 1.).is_nan());
    }
    #[test]
    fn reject_exponential_graph() {
        let mut data = [0u64; 2 + 48 * 8];
        data[0] = 48;
        let base = data.as_mut_ptr().cast::<u8>();
        for i in 1..48 {
            unsafe {
                let n = &mut *base.add(16 + i * 64).cast::<Node>();
                n.op = 8;
                n.a = (i - 1) as u32;
                n.b = (i - 1) as u32;
            }
        }
        assert_eq!(
            unsafe { mattmc_density_validate(base, std::mem::size_of_val(&data) as u64) },
            -8
        );
    }
    #[test]
    fn reject_forward_child() {
        #[repr(C)]
        struct Program {
            h: Header,
            n: Node,
        }
        let p = Program {
            h: Header {
                count: 1,
                reserved: [0; 3],
            },
            n: Node {
                op: 8,
                a: 0,
                b: 0,
                c: 0,
                offset: 0,
                bytes: 0,
                p: 0.,
                q: 0.,
                r: 0.,
                reserved: 0.,
            },
        };
        assert_eq!(
            unsafe { mattmc_density_validate((&p as *const Program).cast(), 80) },
            -4
        );
    }
}

// A cell program is a tree of pure operations and cached interpolation corners.
// The trace is the last observable fill traversal phase, plus direct-fill kind.
// Node order is postorder, so a later phase always has the larger trace code.
fn eval_cell(nodes: &[Node], i: usize, frame: &[f64], xyz: [f64; 3]) -> (f64, u32) {
    let n = &nodes[i];
    let child = |j: u32| eval_cell(nodes, j as usize, frame, xyz);
    let visit = ((i as u32 + 1) << 1) as u32;
    match n.op {
        0 => (n.p, 0),
        24 => {
            let at = n.a as usize * 8;
            let corners: [f64; 8] = frame[at..at + 8].try_into().unwrap();
            (
                super::noise::lerp3(xyz[0], xyz[1], xyz[2], corners),
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
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_cell(
    base: *const u8,
    frame: *const f64,
    x: f64,
    y: f64,
    z: f64,
) -> f64 {
    let h = unsafe { &*base.cast::<Header>() };
    if h.reserved[1] == 0 || frame.is_null() {
        return f64::NAN;
    }
    let nodes =
        unsafe { std::slice::from_raw_parts(base.add(16).cast::<Node>(), h.count as usize) };
    let frame = unsafe { std::slice::from_raw_parts(frame, h.reserved[1] as usize * 8) };
    eval_cell(nodes, nodes.len() - 1, frame, [x, y, z]).0
}
/// # Safety
/// Same validated program/frame contract as density_cell. Output has count
/// writable doubles and cannot alias frame or program. Each call is <=256 points.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_cell_array(
    base: *const u8,
    frame: *const f64,
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
) -> i64 {
    let h = unsafe { &*base.cast::<Header>() };
    if h.reserved[1] == 0
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
    let frame = unsafe { std::slice::from_raw_parts(frame, h.reserved[1] as usize * 8) };
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
                    row[j] = super::noise::lerp3(xs[j], ys[j], zs[j], v);
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

// Specialize this expression shape, not a seed or a set of constants. It is the
// common final-density/noodle tree. Exact shape checks retain the general
// evaluator for data packs and every other expression. Shared math routines
// preserve all predicates, operation order, NaNs and signed zeros.
fn terrain_cell_shape(n: &[Node]) -> bool {
    if n.len() != 15 {
        return false;
    }
    let ops = [24, 12, 20, 24, 0, 24, 24, 14, 24, 14, 11, 12, 8, 22, 10];
    if n.iter().zip(ops).any(|(n, op)| n.op != op) {
        return false;
    }
    n[1].a == 0
        && n[2].a == 1
        && n[7].a == 6
        && n[9].a == 8
        && n[10].a == 7
        && n[10].b == 9
        && n[11].a == 10
        && n[12].a == 5
        && n[12].b == 11
        && n[13].a == 3
        && n[13].b == 4
        && n[13].c == 12
        && n[14].a == 2
        && n[14].b == 13
}
unsafe fn terrain_cell_array<const ADD: bool>(
    n: &[Node],
    frame: &[f64],
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
    add: f64,
) -> i64 {
    let corners = |index: usize| -> [f64; 8] {
        let at = n[index].a as usize * 8;
        frame[at..at + 8].try_into().unwrap()
    };
    let frames = [corners(0), corners(3), corners(5), corners(6), corners(8)];
    let mut fractions = [0.; 64];
    for i in 0..width as usize {
        fractions[i] = i as f64 / width as f64;
    }
    let mut ix = (start / width) % width;
    let mut iy = height - 1 - start / (width * width);
    let mut iz = start % width;
    let mut x = fractions[ix as usize];
    let mut y = iy as f64 / height as f64;
    let row = |x, y| {
        frames.map(|v| {
            [
                super::noise::lerp2(x, y, v[0], v[1], v[2], v[3]),
                super::noise::lerp2(x, y, v[4], v[5], v[6], v[7]),
            ]
        })
    };
    let mut rows = row(x, y);
    let mut last = -1;
    for j in 0..count {
        let i = start + j;
        let z = fractions[iz as usize];
        let sample = |index: usize| super::noise::lerp(z, rows[index][0], rows[index][1]);
        let a = math(20, math(12, sample(0), 0., n[1].p, 0.), 0., 0., 0.);
        let value = if needs_right(10, a, n[14].p, 0.) {
            last = ((30i64) << 32) | i as i64;
            let b = if in_range(sample(1), n[13].p, n[13].q) {
                n[4].p
            } else {
                let thickness = sample(2);
                let ra = math(14, sample(3), 0., 0., 0.);
                let rb = if needs_right(11, ra, 0., n[10].q) {
                    math(14, sample(4), 0., 0., 0.)
                } else {
                    0.
                };
                let ridge = math(11, ra, rb, 0., n[10].q);
                math(8, thickness, math(12, ridge, 0., n[11].p, 0.), 0., 0.)
            };
            math(10, a, b, n[14].p, 0.)
        } else {
            a
        };
        unsafe {
            *output.add(j as usize) = if ADD { value + add } else { value };
        }
        iz += 1;
        if iz == width {
            iz = 0;
            ix += 1;
            if ix == width {
                ix = 0;
                iy = iy.wrapping_sub(1);
                y = iy as f64 / height as f64;
            }
            x = fractions[ix as usize];
            if j + 1 < count {
                rows = row(x, y);
            }
        }
    }
    if last < 0 && count > 0 {
        (3i64 << 32) | (start as i64 + count as i64 - 1)
    } else {
        last
    }
}

// The single-interpolator postprocessing used by Nether/End-like settings.
unsafe fn unary_cell_array<const ADD: bool>(
    n: &[Node],
    frame: &[f64],
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
    add: f64,
) -> i64 {
    #[cfg(target_arch = "x86_64")]
    if width >= 4 && count >= 4 && std::is_x86_feature_detected!("avx2") {
        return unsafe {
            unary_cell_array_avx2::<ADD>(n, frame, output, start, count, width, height, add)
        };
    }
    let at = n[0].a as usize * 8;
    let v = &frame[at..at + 8];
    let mut fractions = [0.; 64];
    for i in 0..width as usize {
        fractions[i] = i as f64 / width as f64;
    }
    let mut ix = (start / width) % width;
    let mut iy = height - 1 - start / (width * width);
    let mut iz = start % width;
    let row = |ix: u32, iy: u32| {
        let x = fractions[ix as usize];
        let y = iy as f64 / height as f64;
        [
            super::noise::lerp2(x, y, v[0], v[1], v[2], v[3]),
            super::noise::lerp2(x, y, v[4], v[5], v[6], v[7]),
        ]
    };
    let mut values = row(ix, iy);
    for j in 0..count {
        let value = super::noise::lerp(fractions[iz as usize], values[0], values[1]);
        let value = math(20, math(12, value, 0., n[1].p, 0.), 0., 0., 0.);
        unsafe {
            *output.add(j as usize) = if ADD { value + add } else { value };
        }
        iz += 1;
        if iz == width {
            iz = 0;
            ix += 1;
            if ix == width {
                ix = 0;
                iy = iy.wrapping_sub(1);
            }
            if j + 1 < count {
                values = row(ix, iy);
            }
        }
    }
    (3i64 << 32) | (start as i64 + count as i64 - 1)
}

// Four Z samples share the exact scalar X/Y intermediates. Ordered comparisons
// preserve NaNs and signed zero; every arithmetic operation retains Java order.
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2")]
unsafe fn unary_cell_array_avx2<const ADD: bool>(
    n: &[Node],
    frame: &[f64],
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
    add: f64,
) -> i64 {
    use std::arch::x86_64::*;
    let at = n[0].a as usize * 8;
    let v = &frame[at..at + 8];
    let mut fractions = [0.; 64];
    for i in 0..width as usize {
        fractions[i] = i as f64 / width as f64;
    }
    let factor = _mm256_set1_pd(n[1].p);
    let low = _mm256_set1_pd(-1.);
    let high = _mm256_set1_pd(1.);
    let mut j = 0;
    while j < count {
        let index = start + j;
        let ix = (index / width) % width;
        let iy = height - 1 - index / (width * width);
        let mut iz = index % width;
        let x = fractions[ix as usize];
        let y = iy as f64 / height as f64;
        let a = super::noise::lerp2(x, y, v[0], v[1], v[2], v[3]);
        let b = super::noise::lerp2(x, y, v[4], v[5], v[6], v[7]);
        let end = j + (width - iz).min(count - j);
        let av = _mm256_set1_pd(a);
        let delta = _mm256_set1_pd(b - a);
        while j + 4 <= end {
            let z = unsafe { _mm256_loadu_pd(fractions.as_ptr().add(iz as usize)) };
            let value = _mm256_mul_pd(_mm256_add_pd(av, _mm256_mul_pd(z, delta)), factor);
            let value = _mm256_blendv_pd(value, low, _mm256_cmp_pd::<_CMP_LT_OQ>(value, low));
            let value = _mm256_blendv_pd(value, high, _mm256_cmp_pd::<_CMP_GT_OQ>(value, high));
            let cube = _mm256_mul_pd(_mm256_mul_pd(value, value), value);
            let value = _mm256_sub_pd(
                _mm256_div_pd(value, _mm256_set1_pd(2.)),
                _mm256_div_pd(cube, _mm256_set1_pd(24.)),
            );
            let value = if ADD {
                _mm256_add_pd(value, _mm256_set1_pd(add))
            } else {
                value
            };
            unsafe {
                _mm256_storeu_pd(output.add(j as usize), value);
            }
            j += 4;
            iz += 4;
        }
        while j < end {
            let value = super::noise::lerp(fractions[iz as usize], a, b);
            let value = math(20, math(12, value, 0., n[1].p, 0.), 0., 0., 0.);
            unsafe {
                *output.add(j as usize) = if ADD { value + add } else { value };
            }
            j += 1;
            iz += 1;
        }
    }
    (3i64 << 32) | (start as i64 + count as i64 - 1)
}

//! CPU and coordinate guards, family selection, and bounded batches.
#[cfg(target_arch = "x86_64")]
use super::dispatch_avx2 as avx2;
use super::{
    perlin::perlin,
    state::{Octave, State},
};
impl State {
    #[inline(always)]
    pub(crate) fn eval(
        &self,
        o: &[Octave],
        x: f64,
        y: f64,
        z: f64,
        ys: f64,
        ym: f64,
        flags: u32,
    ) -> f64 {
        match self.kind {
            1 => o[0].noise(x, y, z, ys, ym),
            2 => perlin(
                o,
                self.params[0],
                self.params[1],
                x,
                y,
                z,
                ys,
                ym,
                flags != 0,
            ),
            3 => self.normal(o, x, y, z),
            4 => self.blended(o, x, y, z),
            5 => {
                if flags == 0 {
                    o[0].simplex2(x, y)
                } else {
                    o[0].simplex3(x, y, z)
                }
            }
            6 => self.perlin_simplex(o, x, y, flags),
            _ => unreachable!(),
        }
    }
}
#[inline]
pub(crate) fn sample_simplex2(o: &Octave, x: f64, y: f64) -> f64 {
    #[cfg(target_arch = "x86_64")]
    if x.abs() <= 30000000. && y.abs() <= 30000000. && is_x86_feature_detected!("avx2") {
        return unsafe { super::simplex::optimized::sample_simplex2(o, x, y) };
    }
    o.simplex2(x, y)
}
/// Specialized hot entry: no unused z coordinate, flags, or family dispatch.
/// # Safety
/// Java-owned validated kind=5 state with one live immutable octave.
pub(crate) unsafe fn noise_simplex2(state: *const State, x: f64, y: f64) -> f64 {
    let o = unsafe { &*state.add(1).cast::<Octave>() };
    sample_simplex2(o, x, y)
}
/// # Safety
/// `state` is validated immutable state, live for this call. No allocations or callbacks.
pub(crate) unsafe fn noise_eval(
    state: *const State,
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    let s = unsafe { &*state };
    eval_dispatch(s, unsafe { s.octaves() }, x, y, z, ys, ym, flags)
}
#[inline]
pub(crate) fn eval_dispatch(
    s: &State,
    o: &[Octave],
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    if s.kind == 5 && flags == 0 {
        return sample_simplex2(&o[0], x, y);
    }
    #[cfg(target_arch = "x86_64")]
    if is_x86_feature_detected!("avx2") {
        if (1..=3).contains(&s.kind)
            && ys == 0.
            && ym == 0.
            && flags == 0
            && s.n1 <= 32
            && s.n2 <= 32
            && s.params[0] >= 0.
            && s.params[0] <= 256.
            && (s.kind != 3 || (s.params[2] >= 0. && s.params[2] <= 256.))
            && x.abs() <= 30000000.
            && y.abs() <= 30000000.
            && z.abs() <= 30000000.
        {
            if s.kind != 1 && s.n1 >= 4 {
                return unsafe { avx2::evaluate_single(s, o, x, y, z) };
            }
            return unsafe { avx2::evaluate_bounded(s, o, x, y, z) };
        }
        if x.abs() <= 30000000. && y.abs() <= 30000000. && z.abs() <= 30000000. {
            if s.kind == 5 || (s.kind == 6 && s.params[0] >= 0. && s.params[0] <= 4.) {
                return unsafe {
                    super::simplex::optimized::evaluate_simplex(s, o, x, y, z, flags)
                };
            }
            if s.kind == 4
                && s.params[..2]
                    .iter()
                    .all(|v| *v >= 0.684412 && *v <= 684412.)
                && s.params[2..4].iter().all(|v| *v >= 0.001 && *v <= 1000.)
                && s.params[4] >= 1.
                && s.params[4] <= 8.
            {
                return unsafe { super::blended::avx2::blended(s, o, x, y, z) };
            }
        }
        return unsafe { eval_avx2(s, o, x, y, z, ys, ym, flags) };
    }
    s.eval(o, x, y, z, ys, ym, flags)
}
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn eval_avx2(
    s: &State,
    o: &[Octave],
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    s.eval(o, x, y, z, ys, ym, flags)
}
/// # Safety
/// Valid state, `xyz` contains count*3 doubles, `out` count doubles; buffers do not alias.
/// Bounded for Java critical downcalls; Java splits larger requests.
pub(crate) unsafe fn noise_batch(
    state: *const State,
    xyz: *const f64,
    out: *mut f64,
    count: u32,
    ys: f64,
    ym: f64,
    flags: u32,
) -> i32 {
    if state.is_null() || xyz.is_null() || out.is_null() || count > 256 {
        return -1;
    }
    let s = unsafe { &*state };
    let o = unsafe { s.octaves() };
    let xyz = unsafe { std::slice::from_raw_parts(xyz, count as usize * 3) };
    let out = unsafe { std::slice::from_raw_parts_mut(out, count as usize) };
    evaluate_batch(s, o, xyz, out, ys, ym, flags);
    0
}
pub(crate) fn evaluate_batch(
    s: &State,
    o: &[Octave],
    xyz: &[f64],
    out: &mut [f64],
    ys: f64,
    ym: f64,
    flags: u32,
) {
    #[cfg(target_arch = "x86_64")]
    if s.kind == 4
        && ys == 0.
        && ym == 0.
        && flags == 0
        && s.params[..2]
            .iter()
            .all(|v| *v >= 0.684412 && *v <= 684412.)
        && s.params[2..4].iter().all(|v| *v >= 0.001 && *v <= 1000.)
        && s.params[4] >= 1.
        && s.params[4] <= 8.
        && is_x86_feature_detected!("avx2")
    {
        let groups = out.len() / 4;
        for group in 0..groups {
            let p = &xyz[group * 12..group * 12 + 12];
            if p.iter().all(|v| v.abs() <= 30_000_000.) {
                unsafe {
                    super::blended::avx2::blended4(s, o, p, &mut out[group * 4..group * 4 + 4]);
                }
            } else {
                for lane in 0..4 {
                    out[group * 4 + lane] = s.eval(
                        o,
                        p[lane * 3],
                        p[lane * 3 + 1],
                        p[lane * 3 + 2],
                        ys,
                        ym,
                        flags,
                    );
                }
            }
        }
        for i in groups * 4..out.len() {
            out[i] = s.eval(o, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], ys, ym, flags);
        }
        return;
    }
    #[cfg(target_arch = "x86_64")]
    if ys == 0.0
        && ym == 0.0
        && flags == 0
        && (1..=3).contains(&s.kind)
        && s.n1 <= 32
        && s.n2 <= 32
        && (s.kind == 1 || (s.params[0] >= 0.0 && s.params[0] <= 256.0))
        && (s.kind != 3 || (s.params[2] >= 0.0 && s.params[2] <= 256.0))
        && is_x86_feature_detected!("avx2")
    {
        let groups = out.len() / 4;
        for group in 0..groups {
            let p = &xyz[group * 12..group * 12 + 12];
            if p.iter().all(|x| x.abs() <= 30_000_000.0) {
                // Eligibility bounds keep floor casts in Java's nonsaturating range.
                unsafe {
                    avx2::evaluate4(s, o, p, &mut out[group * 4..group * 4 + 4]);
                }
            } else {
                for lane in 0..4 {
                    out[group * 4 + lane] = s.eval(
                        o,
                        p[lane * 3],
                        p[lane * 3 + 1],
                        p[lane * 3 + 2],
                        ys,
                        ym,
                        flags,
                    );
                }
            }
        }
        for i in groups * 4..out.len() {
            out[i] = s.eval(o, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], ys, ym, flags);
        }
        return;
    }
    for (v, p) in out.iter_mut().zip(xyz.chunks_exact(3)) {
        *v = s.eval(o, p[0], p[1], p[2], ys, ym, flags);
    }
}
/// # Safety
/// Valid kind=1 state and writable, nonaliasing three-double derivative accumulator.
pub(crate) unsafe fn noise_derivative(
    state: *const State,
    x: f64,
    y: f64,
    z: f64,
    out: *mut f64,
) -> f64 {
    unsafe {
        (&*state).octaves()[0].with_derivative(x, y, z, std::slice::from_raw_parts_mut(out, 3))
    }
}

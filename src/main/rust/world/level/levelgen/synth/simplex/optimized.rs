//! Bounded-coordinate Simplex specialization; callers enforce bounds.
use super::super::{
    common::GRAD,
    state::{Octave, State},
};
#[inline(always)]
pub(crate) fn bounded_floor(x: f64) -> i32 {
    unsafe { x.floor().to_int_unchecked::<i32>() }
}

#[inline(always)]
pub(crate) unsafe fn simplex2(o: &Octave, x: f64, y: f64) -> f64 {
    // Java's compile-time Math.sqrt(3.0) rounded constants.
    let f2 = 0.3660254037844386;
    let g2 = 0.21132486540518713;
    let f = (x + y) * f2;
    let i = bounded_floor(x + f);
    let j = bounded_floor(y + f);
    let g = i.wrapping_add(j) as f64 * g2;
    let a = x - (i as f64 - g);
    let b = y - (j as f64 - g);
    let (u, v) = if a > b { (1, 0) } else { (0, 1) };
    let c = a - u as f64 + g2;
    let d = b - v as f64 + g2;
    let e = a - 1.0 + 2.0 * g2;
    let f = b - 1.0 + 2.0 * g2;
    let i = i & 255;
    let j = j & 255;
    let p = o.p12(i + o.p(j));
    let q = o.p12(i + u + o.p(j + v));
    let r = o.p12(i + 1 + o.p(j + 1));
    70.0 * (corner2(p, a, b) + corner2(q, c, d) + corner2(r, e, f))
}

#[inline(always)]
pub(crate) unsafe fn simplex3(octave: &Octave, x: f64, y: f64, z: f64) -> f64 {
    let s = (x + y + z) * 0.3333333333333333;
    let i = bounded_floor(x + s);
    let j = bounded_floor(y + s);
    let k = bounded_floor(z + s);
    let t = i.wrapping_add(j).wrapping_add(k) as f64 * 0.16666666666666666;
    let a = x - (i as f64 - t);
    let b = y - (j as f64 - t);
    let c = z - (k as f64 - t);
    let (u, v, w, p, q, r) = if a >= b {
        if b >= c {
            (1, 0, 0, 1, 1, 0)
        } else if a >= c {
            (1, 0, 0, 1, 0, 1)
        } else {
            (0, 0, 1, 1, 0, 1)
        }
    } else if b < c {
        (0, 0, 1, 0, 1, 1)
    } else if a < c {
        (0, 1, 0, 0, 1, 1)
    } else {
        (0, 1, 0, 1, 1, 0)
    };
    let d = a - u as f64 + 0.16666666666666666;
    let e = b - v as f64 + 0.16666666666666666;
    let f = c - w as f64 + 0.16666666666666666;
    let g = a - p as f64 + 0.3333333333333333;
    let h = b - q as f64 + 0.3333333333333333;
    let l = c - r as f64 + 0.3333333333333333;
    let m = a - 1.0 + 0.5;
    let n = b - 1.0 + 0.5;
    let o = c - 1.0 + 0.5;
    let i = i & 255;
    let j = j & 255;
    let k = k & 255;
    let ga = octave.p12(i + octave.p(j + octave.p(k)));
    let gb = octave.p12(i + u + octave.p(j + v + octave.p(k + w)));
    let gc = octave.p12(i + p + octave.p(j + q + octave.p(k + r)));
    let gd = octave.p12(i + 1 + octave.p(j + 1 + octave.p(k + 1)));
    32.0 * (Octave::corner(ga, a, b, c, 0.6)
        + Octave::corner(gb, d, e, f, 0.6)
        + Octave::corner(gc, g, h, l, 0.6)
        + Octave::corner(gd, m, n, o, 0.6))
}
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn evaluate_simplex(
    s: &State,
    o: &[Octave],
    x: f64,
    y: f64,
    z: f64,
    flags: u32,
) -> f64 {
    unsafe {
        if s.kind == 5 {
            return if flags == 0 {
                simplex2(&o[0], x, y)
            } else {
                simplex3(&o[0], x, y, z)
            };
        }
        let mut sum = 0.;
        let mut frequency = s.params[0];
        let mut weight = s.params[1];
        for n in o {
            if n.present != 0 {
                sum += simplex2(
                    n,
                    x * frequency + if flags != 0 { n.x } else { 0. },
                    y * frequency + if flags != 0 { n.y } else { 0. },
                ) * weight;
            }
            frequency /= 2.;
            weight *= 2.;
        }
        sum
    }
}

#[inline(always)]
pub(crate) fn corner2(g: i32, x: f64, y: f64) -> f64 {
    let h = 0.5 - x * x - y * y;
    if h < 0. {
        return 0.;
    }
    let v = GRAD[g as usize];
    let mut r = v[0] * x + v[1] * y;
    if r == 0. {
        r += v[2] * 0.;
    }
    let h = h * h;
    h * h * r
}

#[target_feature(enable = "avx2")]
pub(crate) unsafe fn sample_simplex2(o: &Octave, x: f64, y: f64) -> f64 {
    unsafe { simplex2(o, x, y) }
}

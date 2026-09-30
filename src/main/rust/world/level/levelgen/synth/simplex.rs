//! Two- and three-dimensional Simplex kernels.
use super::{common::*, state::Octave};
impl Octave {
    #[inline(always)]
    pub(crate) fn corner(g: i32, x: f64, y: f64, z: f64, r: f64) -> f64 {
        let mut h = r - x * x - y * y - z * z;
        if h < 0.0 {
            0.0
        } else {
            h *= h;
            h * h * dot(g as usize, x, y, z)
        }
    }
    pub(crate) fn simplex2(&self, x: f64, y: f64) -> f64 {
        // Java's compile-time Math.sqrt(3.0) rounded constants.
        let f2 = 0.3660254037844386;
        let g2 = 0.21132486540518713;
        let f = (x + y) * f2;
        let i = floor(x + f);
        let j = floor(y + f);
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
        let p = self.p12(i + self.p(j));
        let q = self.p12(i + u + self.p(j + v));
        let r = self.p12(i + 1 + self.p(j + 1));
        70.0 * (Self::corner(p, a, b, 0.0, 0.5)
            + Self::corner(q, c, d, 0.0, 0.5)
            + Self::corner(r, e, f, 0.0, 0.5))
    }
    pub(crate) fn simplex3(&self, x: f64, y: f64, z: f64) -> f64 {
        let s = (x + y + z) * 0.3333333333333333;
        let i = floor(x + s);
        let j = floor(y + s);
        let k = floor(z + s);
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
        let ga = self.p12(i + self.p(j + self.p(k)));
        let gb = self.p12(i + u + self.p(j + v + self.p(k + w)));
        let gc = self.p12(i + p + self.p(j + q + self.p(k + r)));
        let gd = self.p12(i + 1 + self.p(j + 1 + self.p(k + 1)));
        32.0 * (Self::corner(ga, a, b, c, 0.6)
            + Self::corner(gb, d, e, f, 0.6)
            + Self::corner(gc, g, h, l, 0.6)
            + Self::corner(gd, m, n, o, 0.6))
    }
}
#[cfg(target_arch = "x86_64")]
pub(super) mod optimized;

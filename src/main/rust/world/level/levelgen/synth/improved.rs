//! Improved gradient noise and its derivative accumulator.
use super::super::math::*;
use super::{common::*, state::Octave};
impl Octave {
    #[inline(always)]
    pub(crate) fn corners(&self, x: i32, y: i32, z: i32) -> [usize; 8] {
        let a = self.p(x);
        let b = self.p(x.wrapping_add(1));
        let c = self.p(a.wrapping_add(y));
        let d = self.p(a.wrapping_add(y).wrapping_add(1));
        let e = self.p(b.wrapping_add(y));
        let f = self.p(b.wrapping_add(y).wrapping_add(1));
        [c, e, d, f, c, e, d, f].map_with_index(|n, v| {
            (self.p(v.wrapping_add(z).wrapping_add((n / 4) as i32)) & 15) as usize
        })
    }
    #[inline(always)]
    pub(crate) fn noise(&self, x: f64, y: f64, z: f64, ys: f64, ym: f64) -> f64 {
        let x = x + self.x;
        let y = y + self.y;
        let z = z + self.z;
        let ix = floor(x);
        let iy = floor(y);
        let iz = floor(z);
        let dx = x - ix as f64;
        let dy = y - iy as f64;
        let dz = z - iz as f64;
        let shift = if ys != 0.0 {
            let r = if ym >= 0.0 && ym < dy { ym } else { dy };
            floor(r / ys + 1.0e-7_f32 as f64) as f64 * ys
        } else {
            0.0
        };
        let ey = dy - shift;
        let c = self.corners(ix, iy, iz);
        let v = std::array::from_fn(|n| {
            dot(
                c[n],
                dx - (n & 1) as f64,
                ey - ((n >> 1) & 1) as f64,
                dz - (n >> 2) as f64,
            )
        });
        lerp3(smooth(dx), smooth(dy), smooth(dz), v)
    }
    pub(crate) fn with_derivative(&self, x: f64, y: f64, z: f64, out: &mut [f64]) -> f64 {
        let x = x + self.x;
        let y = y + self.y;
        let z = z + self.z;
        let ix = floor(x);
        let iy = floor(y);
        let iz = floor(z);
        let x = x - ix as f64;
        let y = y - iy as f64;
        let z = z - iz as f64;
        let c = self.corners(ix, iy, iz);
        let v: [f64; 8] = std::array::from_fn(|n| {
            dot(
                c[n],
                x - (n & 1) as f64,
                y - ((n >> 1) & 1) as f64,
                z - (n >> 2) as f64,
            )
        });
        let sx = smooth(x);
        let sy = smooth(y);
        let sz = smooth(z);
        let gx = lerp3(sx, sy, sz, c.map(|g| GRAD[g][0]));
        let gy = lerp3(sx, sy, sz, c.map(|g| GRAD[g][1]));
        let gz = lerp3(sx, sy, sz, c.map(|g| GRAD[g][2]));
        let dx = lerp2(sy, sz, v[1] - v[0], v[3] - v[2], v[5] - v[4], v[7] - v[6]);
        let dy = lerp2(sz, sx, v[2] - v[0], v[6] - v[4], v[3] - v[1], v[7] - v[5]);
        let dz = lerp2(sx, sy, v[4] - v[0], v[5] - v[1], v[6] - v[2], v[7] - v[3]);
        out[0] += gx + derivative(x) * dx;
        out[1] += gy + derivative(y) * dy;
        out[2] += gz + derivative(z) * dz;
        lerp3(sx, sy, sz, v)
    }
}
trait ArrayIndexMap<T, const N: usize> {
    fn map_with_index<U>(self, f: impl FnMut(usize, T) -> U) -> [U; N];
}
impl<T: Copy, const N: usize> ArrayIndexMap<T, N> for [T; N] {
    fn map_with_index<U>(self, mut f: impl FnMut(usize, T) -> U) -> [U; N] {
        std::array::from_fn(|i| f(i, self[i]))
    }
}

#[cfg(target_arch = "x86_64")]
pub(super) mod avx2;

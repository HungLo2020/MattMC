//! Lower, upper and selector octave groups for blended terrain noise.
use super::super::math::lerp;
use super::{
    common::wrap,
    state::{Octave, State},
};
impl State {
    pub(crate) fn blended(&self, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
        let d = x * self.params[0];
        let e = y * self.params[1];
        let f = z * self.params[0];
        let g = d / self.params[2];
        let h = e / self.params[3];
        let i = f / self.params[2];
        let j = self.params[1] * self.params[4];
        let k = j / self.params[3];
        let mut a = 0.;
        let mut b = 0.;
        let mut c = 0.;
        let mut freq = 1.;
        for n in o[32..40].iter().rev() {
            if n.present != 0 {
                c += n.noise(
                    wrap(g * freq),
                    wrap(h * freq),
                    wrap(i * freq),
                    k * freq,
                    h * freq,
                ) / freq;
            }
            freq /= 2.;
        }
        let q = (c / 10. + 1.) / 2.;
        let hi = q >= 1.;
        let lo = q <= 0.;
        freq = 1.;
        for r in 0..16 {
            let x = wrap(d * freq);
            let y = wrap(e * freq);
            let z = wrap(f * freq);
            let ys = j * freq;
            let n = &o[15 - r];
            if !hi && n.present != 0 {
                a += n.noise(x, y, z, ys, e * freq) / freq;
            }
            let n = &o[31 - r];
            if !lo && n.present != 0 {
                b += n.noise(x, y, z, ys, e * freq) / freq;
            }
            freq /= 2.;
        }
        let a = a / 512.;
        let b = b / 512.;
        (if q < 0. {
            a
        } else if q > 1. {
            b
        } else {
            lerp(q, a, b)
        }) / 128.
    }
}
#[cfg(target_arch = "x86_64")]
pub(super) mod avx2;

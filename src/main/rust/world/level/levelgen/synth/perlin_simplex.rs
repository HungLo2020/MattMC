//! Ordered octave accumulation over two-dimensional Simplex noise.
use super::state::{Octave, State};
impl State {
    #[inline(always)]
    pub(crate) fn perlin_simplex(&self, o: &[Octave], x: f64, y: f64, flags: u32) -> f64 {
        let mut sum = 0.0;
        let mut freq = self.params[0];
        let mut weight = self.params[1];
        for a in o {
            if a.present != 0 {
                sum += a.simplex2(
                    x * freq + if flags != 0 { a.x } else { 0. },
                    y * freq + if flags != 0 { a.y } else { 0. },
                ) * weight;
            }
            freq /= 2.0;
            weight *= 2.0;
        }
        sum
    }
}

//! Normal noise combines two ordered Perlin samplers.
use super::{
    perlin::perlin,
    state::{Octave, State},
};
impl State {
    #[inline(always)]
    pub(crate) fn normal(&self, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
        let n = self.n1 as usize;

        let a = perlin(
            &o[..n],
            self.params[0],
            self.params[1],
            x,
            y,
            z,
            0.,
            0.,
            false,
        );
        let b = perlin(
            &o[n..],
            self.params[2],
            self.params[3],
            x * 1.0181268882175227,
            y * 1.0181268882175227,
            z * 1.0181268882175227,
            0.,
            0.,
            false,
        );
        (a + b) * self.params[4]
    }
}

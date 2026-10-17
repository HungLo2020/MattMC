//! Within-call plane intermediates for many boxes sharing coordinate endpoints.
//! Compute exactly the original sums/divisions once per endpoint. No reciprocal,
//! FMA, reordering of comparisons, retained inputs, or result cache.
use super::{coordinates::Coordinates, intersection::Intersection};

const EPSILON: f64 = 1.0e-7;
pub(super) struct Planes {
    translated: [[f64; 257]; 3],
    parameters: [[f64; 257]; 3],
    side: [usize; 3],
    face: [u8; 3],
    active: [bool; 3],
}
impl Planes {
    pub fn new<const CUBES: u32>(coordinates: &Coordinates, dims: [usize; 3], ray: &[f64; 9], delta: [f64; 3]) -> Self {
        let mut result = Self { translated: [[0.0; 257]; 3], parameters: [[0.0; 257]; 3],
            side: [0; 3], face: [0; 3], active: [false; 3] };
        for axis in 0..3 {
            result.active[axis] = delta[axis] > EPSILON || delta[axis] < -EPSILON;
            result.side[axis] = if delta[axis] > EPSILON { 0 } else { 3 };
            result.face[axis] = if delta[axis] > EPSILON { [4, 0, 2][axis] } else { [5, 1, 3][axis] };
            for index in 0..=dims[axis] {
                let plane = coordinates.get::<CUBES>(axis, index as i32) + ray[axis + 6];
                result.translated[axis][index] = plane;
                if result.active[axis] { result.parameters[axis][index] = (plane - ray[axis]) / delta[axis]; }
            }
        }
        result
    }
    #[inline(always)]
    pub fn consider(&self, hit: &mut Intersection, indices: [i32; 6], ray: &[f64; 9], delta: [f64; 3]) {
        self.plane::<0>(hit, indices, ray, delta);
        self.plane::<1>(hit, indices, ray, delta);
        self.plane::<2>(hit, indices, ray, delta);
    }
    #[inline(always)]
    fn plane<const AXIS: usize>(&self, hit: &mut Intersection, indices: [i32; 6], ray: &[f64; 9], delta: [f64; 3]) {
        if !self.active[AXIS] { return; }
        let t = self.parameters[AXIS][indices[AXIS + self.side[AXIS]] as usize];
        if !(0.0 < t && t < hit.parameter) { return; }
        let a = (AXIS + 1) % 3;
        let b = (AXIS + 2) % 3;
        let p = ray[a] + t * delta[a];
        let q = ray[b] + t * delta[b];
        if self.translated[a][indices[a] as usize] - EPSILON < p
            && p < self.translated[a][indices[a + 3] as usize] + EPSILON
            && self.translated[b][indices[b] as usize] - EPSILON < q
            && q < self.translated[b][indices[b + 3] as usize] + EPSILON {
            hit.parameter = t;
            hit.direction = Some(self.face[AXIS]);
        }
    }
}

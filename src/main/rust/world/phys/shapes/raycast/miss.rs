//! Conservative whole-grid rejection, without changing any potential hit.
//! A separating axis must reject BOTH transverse tests and its own plane tests.
use super::coordinates::Coordinates;

const EPSILON: f64 = 1.0e-7;

pub(super) fn proven<const CUBES: u32>(coordinates: &Coordinates, dims: [usize; 3],
    ray: &[f64; 9], delta: [f64; 3]) -> bool {
    for axis in 0..3 {
        let start = ray[axis];
        // Use the original rounded delta, not the supplied end. For any
        // accepted 0<t<1, monotonic IEEE multiply then add bounds the original
        // transverse coordinate by these rounded endpoint computations.
        let finish = start + delta[axis];
        let low = coordinates.get::<CUBES>(axis, 0) + ray[axis + 6];
        let high = coordinates.get::<CUBES>(axis, dims[axis] as i32) + ray[axis + 6];
        if !(start.max(finish) <= low - EPSILON || start.min(finish) >= high + EPSILON) { continue; }
        // Every box's transverse interval is contained in the grid interval.
        // Planes on this axis do not test their own transverse coordinate, so
        // prove their literal division also rejects every endpoint in [low,high].
        let (enter, leave) = if delta[axis] > EPSILON {
            ((low - start) / delta[axis], (high - start) / delta[axis])
        } else if delta[axis] < -EPSILON {
            ((high - start) / delta[axis], (low - start) / delta[axis])
        } else { return true; };
        if enter >= 1.0 || leave <= 0.0 { return true; }
    }
    false
}

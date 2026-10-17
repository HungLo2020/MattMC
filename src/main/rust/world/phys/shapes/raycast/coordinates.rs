pub(super) struct Coordinates<'a> {
    dims: [usize; 3],
    values: &'a [f64],
    offsets: [usize; 3],
}

impl<'a> Coordinates<'a> {
    pub fn new(dims: [usize; 3], values: &'a [f64]) -> Self {
        Self { dims, values, offsets: [0, dims[0] + 1, dims[0] + dims[1] + 2] }
    }

    #[inline]
    pub fn get<const CUBES: u32>(&self, axis: usize, index: i32) -> f64 {
        if CUBES & (1 << axis) != 0 {
            index as f64 / self.dims[axis] as f64
        } else {
            self.values[self.offsets[axis] + index as usize]
        }
    }
}

/// Validate finite explicit axes and prove current endpoint ordering in one
/// pass. Implicit cube axes never read their unused coordinate-buffer slots.
pub(super) fn ordered(values: &[f64], dims: [usize; 3], cubes: u32, ray: &[f64; 9]) -> Option<bool> {
    let mut ordered = (6..9).all(|a| ray[a] != 0.0 || ray[a].to_bits() == 0);
    let mut at = 0;
    for axis in 0..3 {
        if cubes & (1 << axis) == 0 {
            let mut previous = values[at];
            if !previous.is_finite() { return None; }
            for &value in &values[at + 1..at + dims[axis] + 1] {
                if !value.is_finite() { return None; }
                ordered &= previous <= value;
                previous = value;
            }
        }
        at += dims[axis] + 1;
    }
    Some(ordered)
}

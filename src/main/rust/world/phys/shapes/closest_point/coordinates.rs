pub(super) struct Coordinates<'a> {
    pub dims: [usize; 3],
    values: &'a [f64],
    offsets: [usize; 3],
}
impl<'a> Coordinates<'a> {
    pub fn new(dims: [usize; 3], values: &'a [f64]) -> Self {
        Self { dims, values, offsets: [0, dims[0] + 1, dims[0] + dims[1] + 2] }
    }
    #[inline]
    pub fn get<const CUBES: u32>(&self, axis: usize, index: usize) -> f64 {
        if CUBES & (1 << axis) != 0 { index as f64 / self.dims[axis] as f64 }
        else { self.values[self.offsets[axis] + index] }
    }
}

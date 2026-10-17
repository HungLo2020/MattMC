const EPSILON: f64 = 1.0e-7;

/// Direction ordinals match Java: DOWN, UP, NORTH, SOUTH, WEST, EAST.
pub(super) struct Intersection {
    pub parameter: f64,
    pub direction: Option<u8>,
}

#[inline]
fn java_min(a: f64, b: f64) -> f64 {
    if a == 0.0 && b == 0.0 { f64::from_bits(a.to_bits() | b.to_bits()) } else { a.min(b) }
}

#[inline]
fn java_max(a: f64, b: f64) -> f64 {
    if a == 0.0 && b == 0.0 { f64::from_bits(a.to_bits() & b.to_bits()) } else { a.max(b) }
}

impl Intersection {
    pub fn new() -> Self { Self { parameter: 1.0, direction: None } }

    /// Literal AABB constructor -> move(BlockPos) constructor -> getDirection.
    /// Keep X/Y/Z plane order, strict parameter/tolerance comparisons and sums.
    #[inline(never)]
    pub fn consider(&mut self, bounds: [f64; 6], ray: &[f64; 9], delta: [f64; 3]) {
        let low = std::array::from_fn::<_, 3, _>(|a| java_min(bounds[a], bounds[a + 3]) + ray[a + 6]);
        let high = std::array::from_fn::<_, 3, _>(|a| java_max(bounds[a], bounds[a + 3]) + ray[a + 6]);
        let lo = std::array::from_fn::<_, 3, _>(|a| java_min(low[a], high[a]));
        let hi = std::array::from_fn::<_, 3, _>(|a| java_max(low[a], high[a]));
        self.planes(lo, hi, ray, delta);
    }

    /// Finite nondecreasing coordinate lists need no endpoint swaps. Adding
    /// an integer block position preserves order; +/-zero both become +zero
    /// for position zero. All six additions still occur exactly as in Java.
    /// Keeping this outside the visitor reduces its register/stack pressure.
    #[inline(never)]
    #[cfg(test)]
    pub fn consider_ordered(&mut self, bounds: [f64; 6], ray: &[f64; 9], delta: [f64; 3]) {
        let lo = std::array::from_fn(|a| bounds[a] + ray[a + 6]);
        let hi = std::array::from_fn(|a| bounds[a + 3] + ray[a + 6]);
        self.planes(lo, hi, ray, delta);
    }

    /// Only a strictly better plane needs transverse endpoints. This uses the
    /// same division and sums, but avoids materializing/normalizing unused boxes.
    #[inline(always)]
    pub fn consider_coordinates<const CUBES: u32>(&mut self, coords: &super::coordinates::Coordinates,
        indices: [i32; 6], ray: &[f64; 9], delta: [f64; 3]) {
        self.coordinate_plane::<CUBES, 0>(coords, indices, ray, delta);
        self.coordinate_plane::<CUBES, 1>(coords, indices, ray, delta);
        self.coordinate_plane::<CUBES, 2>(coords, indices, ray, delta);
    }

    #[inline(always)]
    fn coordinate_plane<const CUBES: u32, const AXIS: usize>(&mut self,
        coords: &super::coordinates::Coordinates, indices: [i32; 6], ray: &[f64; 9], delta: [f64; 3]) {
        let (index, face) = if delta[AXIS] > EPSILON {
            (indices[AXIS], [4, 0, 2][AXIS])
        } else if delta[AXIS] < -EPSILON {
            (indices[AXIS + 3], [5, 1, 3][AXIS])
        } else { return; };
        let t = (coords.get::<CUBES>(AXIS, index) + ray[AXIS + 6] - ray[AXIS]) / delta[AXIS];
        if !(0.0 < t && t < self.parameter) { return; }
        let a = (AXIS + 1) % 3;
        let b = (AXIS + 2) % 3;
        let p = ray[a] + t * delta[a];
        let q = ray[b] + t * delta[b];
        if coords.get::<CUBES>(a, indices[a]) + ray[a + 6] - EPSILON < p
            && p < coords.get::<CUBES>(a, indices[a + 3]) + ray[a + 6] + EPSILON
            && coords.get::<CUBES>(b, indices[b]) + ray[b + 6] - EPSILON < q
            && q < coords.get::<CUBES>(b, indices[b + 3]) + ray[b + 6] + EPSILON {
            self.parameter = t;
            self.direction = Some(face);
        }
    }

    #[inline(always)]
    fn planes(&mut self, lo: [f64; 3], hi: [f64; 3], ray: &[f64; 9], delta: [f64; 3]) {
        self.plane::<0>(lo, hi, ray, delta);
        self.plane::<1>(lo, hi, ray, delta);
        self.plane::<2>(lo, hi, ray, delta);
    }

    #[inline(always)]
    fn plane<const AXIS: usize>(&mut self, lo: [f64; 3], hi: [f64; 3], ray: &[f64; 9], delta: [f64; 3]) {
        let (plane, face) = if delta[AXIS] > EPSILON {
            (lo[AXIS], [4, 0, 2][AXIS])
        } else if delta[AXIS] < -EPSILON {
            (hi[AXIS], [5, 1, 3][AXIS])
        } else { return; };
        let a = (AXIS + 1) % 3;
        let b = (AXIS + 2) % 3;
        let t = (plane - ray[AXIS]) / delta[AXIS];
        let p = ray[a] + t * delta[a];
        let q = ray[b] + t * delta[b];
        if 0.0 < t && t < self.parameter
            && lo[a] - EPSILON < p && p < hi[a] + EPSILON
            && lo[b] - EPSILON < q && q < hi[b] + EPSILON {
            self.parameter = t;
            self.direction = Some(face);
        }
    }

    pub fn point(&self, ray: &[f64; 9], delta: [f64; 3]) -> Option<[f64; 3]> {
        self.direction.map(|_| std::array::from_fn(|a| ray[a] + self.parameter * delta[a]))
    }
}

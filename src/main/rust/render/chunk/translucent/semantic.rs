//! Owned CPU geometry for semantic callers, without FFI handles or renderer state.
//! Returned ordinals always refer to the caller's original quad order (not the
//! mesher's facing buckets). Material boundaries are intentionally absent.

use super::*;

pub(crate) struct SemanticTranslucentGeometry(
    NativeTranslucentSectionGeometry,
    /// Per axis, every coordinate the topological sort compares a camera axis
    /// against (quad extents, aligned separator distances and aligned quad
    /// planes), sorted by `total_cmp` and deduplicated.
    [Vec<f32>; 3],
);

/// The cell of the sort's plane arrangement containing a camera: the open
/// interval per axis between consecutive breakpoints, plus the side of every
/// unaligned quad plane. Every camera comparison of the topological sort has
/// the same outcome throughout one cell, so its order is identical there.
#[derive(Clone, Debug, PartialEq, Eq)]
pub(crate) struct SemanticOrderRegion {
    intervals: [usize; 3],
    unaligned_sides: Vec<bool>,
}

impl SemanticTranslucentGeometry {
    pub(crate) fn new(positions: &[[[f32; 3]; 4]]) -> Option<Self> {
        let mut quads = Vec::with_capacity(positions.len());
        for p in positions {
            if !p.iter().flatten().all(|value| value.is_finite()) {
                return None;
            }
            // The diagonal cross product is also used by the native full-quad
            // implementation. It includes all four corners, including a
            // triangle represented by a duplicated fourth corner.
            let a: [f32; 3] = std::array::from_fn(|i| p[2][i] - p[0][i]);
            let b: [f32; 3] = std::array::from_fn(|i| p[3][i] - p[1][i]);
            let normal = normalize3(
                a[1] * b[2] - a[2] * b[1],
                a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0],
            );
            if ![normal.0, normal.1, normal.2].iter().all(|v| v.is_finite()) {
                return None;
            }
            let record = TranslucentQuadRecord {
                positions: std::array::from_fn(|i| p[i / 3][i % 3]),
                facing: aligned_facing_from_normal(normal).unwrap_or(FACING_UNASSIGNED),
                packed_normal: pack_normal(
                    (normal.0 * 127.0) as i8,
                    (normal.1 * 127.0) as i8,
                    (normal.2 * 127.0) as i8,
                ),
            };
            quads.push(build_quad_info(&record));
        }
        let aligned_separator_distances = build_aligned_separator_distances(&quads);
        let mut breakpoints: [Vec<f32>; 3] = Default::default();
        for direction in 0..FACING_DIRECTIONS {
            let axis = direction % 3;
            let sign = facing_sign(direction as i32) as f32;
            breakpoints[axis].extend(quads.iter().map(|quad| quad.extents[direction]));
            breakpoints[axis].extend(
                aligned_separator_distances[direction]
                    .iter()
                    .map(|distance| distance * sign),
            );
        }
        for quad in &quads {
            if is_aligned(quad.topo_facing) {
                let direction = quad.topo_facing as usize;
                let sign = facing_sign(quad.topo_facing) as f32;
                breakpoints[direction % 3].push(quad.accurate_dot_product * sign);
            }
        }
        for axis in &mut breakpoints {
            axis.sort_by(f32::total_cmp);
            axis.dedup_by(|a, b| a.total_cmp(b).is_eq());
        }
        Some(Self(
            NativeTranslucentSectionGeometry {
                quads,
                aligned_separator_distances,
            },
            breakpoints,
        ))
    }

    /// The camera's cell (see `SemanticOrderRegion`), or `None` when it lies
    /// exactly on a breakpoint or is not finite (the order must be recomputed).
    pub(crate) fn order_region(&self, camera: [f32; 3]) -> Option<SemanticOrderRegion> {
        if !camera.iter().all(|value| value.is_finite()) {
            return None;
        }
        let mut intervals = [0; 3];
        for axis in 0..3 {
            match self.1[axis].binary_search_by(|value| value.total_cmp(&camera[axis])) {
                Ok(_) => return None,
                Err(interval) => intervals[axis] = interval,
            }
        }
        let unaligned_sides = self
            .0
            .quads
            .iter()
            .filter(|quad| !is_aligned(quad.topo_facing))
            .map(|quad| {
                point_outside_half_space(
                    quad.accurate_dot_product,
                    dynamic_accurate_normal(quad),
                    camera[0],
                    camera[1],
                    camera[2],
                )
            })
            .collect();
        Some(SemanticOrderRegion {
            intervals,
            unaligned_sides,
        })
    }

    /// `order`, also reporting whether the topological sort produced it (a
    /// cycle falls back to the distance sort, which varies continuously with
    /// the camera and is therefore not region-stable).
    pub(crate) fn order_with_topology(&self, camera: [f32; 3]) -> Option<(Vec<usize>, bool)> {
        if !camera.iter().all(|value| value.is_finite()) {
            return None;
        }
        if let Some(order) =
            dynamic_topo_graph_sort(&self.0, (camera[0], camera[1], camera[2]), false)
        {
            return Some((order.into_iter().map(|i| i as usize).collect(), true));
        }
        self.order(camera).map(|order| (order, false))
    }

    pub(crate) fn order(&self, camera: [f32; 3]) -> Option<Vec<usize>> {
        if !camera.iter().all(|value| value.is_finite()) {
            return None;
        }
        if let Some(order) =
            dynamic_topo_graph_sort(&self.0, (camera[0], camera[1], camera[2]), false)
        {
            return Some(order.into_iter().map(|i| i as usize).collect());
        }
        // This is the native dynamic sorter's cycle policy, not a backend or
        // Java rendering fallback. Preserve its index-key/tie semantics.
        let mut indices = vec![0; self.0.quads.len().checked_mul(6)?];
        if write_distance_sorted_index_buffer(
            &self.0,
            &mut indices,
            camera[0],
            camera[1],
            camera[2],
        ) != OK
        {
            return None;
        }
        Some(
            indices
                .chunks_exact(6)
                .map(|quad| quad[0] as usize / 4)
                .collect(),
        )
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn pane(z: f32, reverse: bool) -> [[f32; 3]; 4] {
        let mut p = [[-1., -1., z], [1., -1., z], [1., 1., z], [-1., 1., z]];
        if reverse {
            p.reverse();
        }
        p
    }

    /// Within one `order_region` cell every topological order is identical,
    /// so reusing a cached order there is exact. Random water-like sections
    /// mix axis-aligned faces (all six directions) with slanted quads.
    #[test]
    fn topological_order_is_constant_within_an_order_region() {
        let mut state = 0x9e37_79b9_7f4a_7c15_u64;
        let mut next = move || {
            state ^= state << 13;
            state ^= state >> 7;
            state ^= state << 17;
            (state >> 11) as f32 / (1u64 << 53) as f32
        };
        let mut compared = 0;
        for _ in 0..40 {
            let mut quads = Vec::new();
            for _ in 0..(3 + (next() * 10.0) as usize) {
                let base = [
                    (next() * 6.0).floor(),
                    (next() * 4.0).floor(),
                    (next() * 6.0).floor(),
                ];
                let size = 0.25 + next() * 1.5;
                let quad = match (next() * 7.0) as usize {
                    0 => [[0., 0., 0.], [size, 0., 0.], [size, size, 0.], [0., size, 0.]],
                    1 => [[0., 0., 0.], [0., size, 0.], [size, size, 0.], [size, 0., 0.]],
                    2 => [[0., 0., 0.], [0., 0., size], [0., size, size], [0., size, 0.]],
                    3 => [[0., 0., 0.], [0., size, 0.], [0., size, size], [0., 0., size]],
                    4 => [[0., 0., 0.], [0., 0., size], [size, 0., size], [size, 0., 0.]],
                    5 => [[0., 0., 0.], [size, 0., 0.], [size, 0., size], [0., 0., size]],
                    _ => [[0., 0., 0.], [size, 0., 0.], [size, size, size], [0., size, size]],
                };
                quads.push(quad.map(|corner| [0, 1, 2].map(|axis| corner[axis] + base[axis])));
            }
            let geometry = SemanticTranslucentGeometry::new(&quads).unwrap();
            let mut by_region: Vec<(SemanticOrderRegion, Vec<usize>)> = Vec::new();
            for _ in 0..300 {
                let camera = [
                    next() * 12.0 - 3.0,
                    next() * 10.0 - 3.0,
                    next() * 12.0 - 3.0,
                ];
                let Some(region) = geometry.order_region(camera) else {
                    continue;
                };
                let Some((order, true)) = geometry.order_with_topology(camera) else {
                    continue;
                };
                match by_region.iter().find(|(known, _)| *known == region) {
                    Some((_, known_order)) => {
                        compared += 1;
                        assert_eq!(known_order, &order, "camera {camera:?}");
                    }
                    None => by_region.push((region, order)),
                }
            }
        }
        assert!(compared > 500, "only {compared} same-region comparisons");
    }

    #[test]
    fn semantic_order_preserves_source_ordinals_across_facing_buckets() {
        let geometry =
            SemanticTranslucentGeometry::new(&[pane(1., false), pane(3., false), pane(2., false)])
                .unwrap();
        assert_eq!(geometry.order([0., 0., 5.]).unwrap(), vec![0, 2, 1]);
        let reverse =
            SemanticTranslucentGeometry::new(&[pane(1., true), pane(3., true), pane(2., true)])
                .unwrap();
        assert_eq!(reverse.order([0., 0., 0.]).unwrap(), vec![1, 2, 0]);
    }

    #[test]
    fn semantic_order_rejects_nonfinite_input_and_retains_every_quad() {
        let mut bad = pane(1., false);
        bad[2][0] = f32::NAN;
        assert!(SemanticTranslucentGeometry::new(&[bad]).is_none());
        let geometry = SemanticTranslucentGeometry::new(&[
            pane(1., false),
            pane(1., true),
            pane(3., false),
            pane(3., true),
        ])
        .unwrap();
        assert!(geometry.order([f32::INFINITY, 0., 0.]).is_none());
        for camera in [[0., 0., 0.], [0., 0., 2.], [0., 0., 5.]] {
            let mut order = geometry.order(camera).unwrap();
            order.sort_unstable();
            assert_eq!(order, vec![0, 1, 2, 3]);
        }
    }
}

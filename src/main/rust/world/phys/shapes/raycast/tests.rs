use super::{evaluate::evaluate, ffi::*, intersection::Intersection};

#[test]
fn prepared_large_grid_planes_match_literal_boxes_for_every_layout_and_reuse() {
    let dims = [16, 16, 16];
    let mut seed = 0x9e37_79b9_7f4a_7c15u64;
    for mask in 0..8 {
        for scale in [f64::from_bits(1), 1.0e-170, 1.0, 1.0e150] {
            let mut values = Vec::new();
            for axis in 0..3 {
                for i in 0..=16 {
                    values.push(if mask & (1 << axis) != 0 { f64::NAN }
                        else if i == 0 { -0.0 } else { (i * i) as f64 / 256.0 * scale });
                }
            }
            let coords = super::coordinates::Coordinates::new(dims, &values);
            let explicit: Vec<f64> = (0..3).flat_map(|axis| {
                let values = &values;
                (0..=16).map(move |i| if mask & (1 << axis) == 0 {
                    values[axis * 17 + i]
                } else { i as f64 / 16.0 })
            }).collect();
            let explicit_coords = super::coordinates::Coordinates::new(dims, &explicit);
            let mut source = [0u64; 64];
            for word in &mut source {
                seed ^= seed << 13; seed ^= seed >> 7; seed ^= seed << 17;
                *word = seed | (seed >> 1); // many ordered boxes exercise the prepared path
            }
            let mut extraction = source;
            let mut boxes = Vec::new();
            super::super::box_extract::extract::visit(&mut extraction, dims, |b| boxes.push(b));
            assert!(boxes.len() >= 64);
            for position in [0.0, 30_000_000.0, i32::MIN as f64] {
                for (begin, end) in [([-1.0, 0.4, 0.6], [2.0, 0.4, 0.6]),
                    ([2.0, 0.8, 0.1], [-1.0, 0.2, 0.9]),
                    ([-1.0; 3], [2.0; 3]), ([0.5, 0.5, 2.0], [0.5, 0.5, -1.0])] {
                    let ray = [begin[0]+position, begin[1]+position, begin[2]+position,
                        end[0]+position, end[1]+position, end[2]+position, position, position, position];
                    let delta = std::array::from_fn(|a| ray[a+3] - ray[a]);
                    let planes = super::prepared::Planes::new::<0>(&explicit_coords, dims, &ray, delta);
                    let mut literal = Intersection::new(); let mut prepared = Intersection::new();
                    // The public evaluator dispatches the actual const layout.
                    for &b in &boxes {
                        let bounds = std::array::from_fn(|i| if mask & (1 << (i%3)) != 0 {
                            b[i] as f64 / 16.0
                        } else { coords.get::<0>(i%3, b[i]) });
                        literal.consider(bounds, &ray, delta);
                        planes.consider(&mut prepared, b, &ray, delta);
                    }
                    let mut work = source;
                    let actual = evaluate(&mut work, dims, &values, mask, &ray, delta);
                    for hit in [prepared, actual] {
                        assert_eq!(hit.direction, literal.direction);
                        assert_eq!(hit.parameter.to_bits(), literal.parameter.to_bits());
                        assert_eq!(hit.point(&ray, delta).map(|p| p.map(f64::to_bits)),
                            literal.point(&ray, delta).map(|p| p.map(f64::to_bits)));
                    }
                    if work != [0; 64] {
                        assert_eq!(work, source); // proven miss need not consume private occupancy
                        assert_eq!(literal.direction, None);
                    }
                }
            }
        }
    }
}

#[test]
fn whole_grid_miss_proof_rejects_every_contained_box_even_at_rounding_extremes() {
    let values = [-f64::MAX, -1.0e150, -30_000_000.0, -1.0, -1.0e-7,
        -f64::from_bits(1), -0.0, 0.0, f64::from_bits(1), 1.0e-7,
        1.0, 30_000_000.0, 1.0e150, f64::MAX];
    let rays = [([-1.0, 2.0, 0.5], [2.0, 2.0, 0.5]),
        ([-1.0, -2.0, -2.0], [2.0, -2.0, -2.0]),
        ([2.0, 0.5, 0.5], [-1.0, 0.5, 0.5]),
        ([0.5; 3], [2.0; 3]), ([0.5; 3], [-2.0; 3]),
        ([1.0e16, 2.0, 0.5], [1.0, 2.0, 0.5]),
        ([0.0, 1.0e-7, -0.0], [-0.0, -1.0e-7, 0.0]),
        ([-1.0e150; 3], [1.0e150; 3])];
    let mut proven = 0;
    for i in 0..values.len() { for j in i..values.len() {
        let low = values[i]; let high = values[j];
        let middle = low * 0.5 + high * 0.5;
        let points = [low, middle, high];
        let flat: Vec<f64> = (0..3).flat_map(|_| points).collect();
        let coordinates = super::coordinates::Coordinates::new([2; 3], &flat);
        for position in [0.0, 30_000_000.0, i32::MIN as f64, 1.0e150] {
            for (start, end) in rays {
                let ray = [start[0]+position, start[1]+position, start[2]+position,
                    end[0]+position, end[1]+position, end[2]+position, position, position, position];
                let delta = std::array::from_fn(|a| ray[a+3] - ray[a]);
                if !super::miss::proven::<0>(&coordinates, [2; 3], &ray, delta) { continue; }
                proven += 1;
                for x in 0..3 { for xx in x..3 { for y in 0..3 { for yy in y..3 { for z in 0..3 { for zz in z..3 {
                    let mut literal = Intersection::new();
                    literal.consider([points[x], points[y], points[z], points[xx], points[yy], points[zz]], &ray, delta);
                    assert_eq!(literal.direction, None);
                } } } } } }
            }
        }
    } }
    assert!(proven > 1000, "exercise actual rejection, not only fallback");
    let flat = [0.0, 0.5, 1.0, 0.0, 0.5, 1.0, 0.0, 0.5, 1.0];
    let coords = super::coordinates::Coordinates::new([2; 3], &flat);
    let ray = [0.25, 0.25, 0.25, 2.0, 0.25, 0.25, 0.0, 0.0, 0.0];
    assert!(!super::miss::proven::<0>(&coords, [2; 3], &ray, [1.75, 0.0, 0.0]));
    let mut interior = Intersection::new();
    interior.consider([0.5, 0.0, 0.0, 1.0, 1.0, 1.0], &ray, [1.75, 0.0, 0.0]);
    assert!(interior.direction.is_some(), "inside a grid can still hit an interior box");
}

#[test]
fn exhaustive_grids_match_materialized_ordered_boxes() {
    let dims = [2, 2, 3];
    let coordinates = [0., 0.5, 1., 0., 0.5, 1., 0., 0.25, 0.75, 1.];
    let offsets = [0, 3, 6];
    let rays = [
        [-1., 0.3, 0.6, 2., 0.4, 0.7, 0., 0., 0.],
        [2., 0.8, 0.9, -1., 0.1, 0.2, 0., 0., 0.],
        [0.5, 2., 0.5, 0.5, -1., 0.5, 0., 0., 0.],
        [-1., -1., -1., 2., 2., 2., 0., 0., 0.],
        [0., 0., 0., 1., 1., 1., 0., 0., 0.],
        [-1., 2., 0.5, 2., 2., 0.5, 0., 0., 0.],
    ];
    for bits in 0..4096u64 {
        let mut words = [bits];
        let mut boxes = Vec::new();
        super::super::box_extract::extract::visit(&mut words, dims, |b| boxes.push(b));
        for ray in rays {
            let delta = std::array::from_fn(|a| ray[a + 3] - ray[a]);
            let mut expected = Intersection::new();
            for b in &boxes {
                expected.consider(std::array::from_fn(|i| coordinates[offsets[i % 3] + b[i] as usize]), &ray, delta);
            }
            let mut words = [bits];
            let actual = evaluate(&mut words, dims, &coordinates, 0, &ray, delta);
            assert_eq!(actual.direction, expected.direction);
            assert_eq!(actual.parameter.to_bits(), expected.parameter.to_bits());
            let mut words = [bits]; let mut output = [99.; 3];
            let status = unsafe { mattmc_voxel_ray_clip(words.as_mut_ptr(), 1, 2, 2, 3,
                coordinates.as_ptr(), 10, 0, ray.as_ptr(), output.as_mut_ptr()) };
            assert_eq!(status, expected.direction.map_or(0, |d| d as i32 + 1));
            if let Some(p) = expected.point(&ray, delta) { assert_eq!(output.map(f64::to_bits), p.map(f64::to_bits)); }
            else { assert_eq!(output, [99.; 3]); }
        }
    }
}

#[test]
fn plane_and_box_ties_strict_endpoint_and_direction_epsilon() {
    let bounds = [0., 0., 0., 1., 1., 1.];
    let ray = [-1., -1., -1., 2., 2., 2., 0., 0., 0.];
    let mut hit = Intersection::new(); hit.consider(bounds, &ray, [3.; 3]);
    assert_eq!(hit.direction, Some(4)); // X before Y before Z.
    let first = hit.parameter.to_bits(); hit.consider(bounds, &ray, [3.; 3]);
    assert_eq!(hit.parameter.to_bits(), first);
    let ray = [-1., 0.5, 0.5, 0., 0.5, 0.5, 0., 0., 0.];
    let mut hit = Intersection::new(); hit.consider(bounds, &ray, [1., 0., 0.]);
    assert_eq!(hit.direction, None); // t == 1 is excluded.
    for delta in [1.0e-7, -1.0e-7, 0., -0.] {
        let mut hit = Intersection::new(); hit.consider(bounds, &ray, [delta, 0., 0.]);
        assert_eq!(hit.direction, None);
    }
}

#[test]
fn scalar_and_general_paths_match_reversed_zero_translated_bounds() {
    for bounds in [[1., 1., 1., -0., 0., 0.], [-0., 1., 0., 0., -0., 1.],
        [f64::MIN_POSITIVE, -1., -1., 1., 1., 1.]] {
        for position in [[0., 0., 0.], [30_000_000., -64., -30_000_000.]] {
            let ray = [position[0]-1., position[1]+0.5, position[2]+0.5,
                position[0]+2., position[1]+0.5, position[2]+0.5, position[0], position[1], position[2]];
            let coords = [bounds[0], bounds[3], bounds[1], bounds[4], bounds[2], bounds[5]];
            let mut words = [1]; let mut a = [99.; 3]; let mut b = a;
            let first = unsafe { mattmc_voxel_ray_clip(words.as_mut_ptr(), 1, 1, 1, 1,
                coords.as_ptr(), 6, 0, ray.as_ptr(), a.as_mut_ptr()) };
            let second = unsafe { mattmc_voxel_ray_box(ray.as_ptr(), bounds.as_ptr(), b.as_mut_ptr()) };
            assert_eq!(first, second); assert_eq!(a.map(f64::to_bits), b.map(f64::to_bits));
        }
    }
}

#[test]
fn malformed_and_nonfinite_inputs_do_not_publish_or_mutate() {
    let coords = [0., 1., 0., 1., 0., 1.];
    let mut ray = [-1., 0.5, 0.5, 2., 0.5, 0.5, 0., 0., 0.];
    let mut words = [u64::MAX]; let mut output = [99.; 3];
    for (nx, len, mask) in [(0, 6, 0), (1, 5, 0), (1, 6, 8)] {
        let before = words;
        assert_eq!(unsafe { mattmc_voxel_ray_clip(words.as_mut_ptr(), 1, nx, 1, 1,
            coords.as_ptr(), len, mask, ray.as_ptr(), output.as_mut_ptr()) }, -1);
        assert_eq!(words, before); assert_eq!(output, [99.; 3]);
    }
    ray[0] = f64::MAX; ray[3] = -f64::MAX;
    assert_eq!(unsafe { mattmc_voxel_ray_clip(words.as_mut_ptr(), 1, 1, 1, 1,
        coords.as_ptr(), 6, 0, ray.as_ptr(), output.as_mut_ptr()) }, -2);
    assert_eq!(words, [u64::MAX]); assert_eq!(output, [99.; 3]);
    ray[0] = f64::NAN;
    assert_eq!(unsafe { mattmc_voxel_ray_box(ray.as_ptr(), coords.as_ptr(), output.as_mut_ptr()) }, -2);
    assert_eq!(output, [99.; 3]);
}

#[test]
fn all_coordinate_layouts_and_empty_compressed_snapshots() {
    let dims = [3, 4, 5];
    let ray = [-1., 0.3, 0.8, 2., 0.7, 0.1, 0., 0., 0.];
    for mask in 0..8 {
        let mut explicit = Vec::new(); let mut implicit = Vec::new();
        for (a, &n) in dims.iter().enumerate() {
            for i in 0..=n {
                let v = if mask & (1 << a) != 0 { i as f64 / n as f64 } else { (i*i) as f64 / (n*n) as f64 };
                explicit.push(v); implicit.push(if mask & (1 << a) != 0 { f64::NAN } else { v });
            }
        }
        let mut a = [0x050a_050a_050a_050a]; let mut b = a;
        let mut pa = [99.; 3]; let mut pb = pa;
        let first = unsafe { mattmc_voxel_ray_clip(a.as_mut_ptr(), 1, 3, 4, 5,
            explicit.as_ptr(), 15, 0, ray.as_ptr(), pa.as_mut_ptr()) };
        let second = unsafe { mattmc_voxel_ray_clip(b.as_mut_ptr(), 1, 3, 4, 5,
            implicit.as_ptr(), 15, mask, ray.as_ptr(), pb.as_mut_ptr()) };
        assert_eq!(first, second); assert_eq!(pa.map(f64::to_bits), pb.map(f64::to_bits));
    }
    let mut words = [u64::MAX]; let mut output = [99.; 3];
    let coords = [0., 1., 0., 1., 0., 1.];
    assert_eq!(unsafe { mattmc_voxel_ray_clip(words.as_mut_ptr(), 0, 1, 1, 1,
        coords.as_ptr(), 6, 0, ray.as_ptr(), output.as_mut_ptr()) }, 0);
    assert_eq!(words, [0]); assert_eq!(output, [99.; 3]);
}

#[test]
fn ordered_shortcut_preserves_raw_bits_at_zero_cancellation_and_extremes() {
    let values = [-f64::MAX, -1.0e150, -30_000_000., -1., -f64::MIN_POSITIVE,
        -f64::from_bits(1), -0., 0., f64::from_bits(1), f64::MIN_POSITIVE,
        1., 30_000_000., 1.0e150, f64::MAX];
    for i in 0..values.len() {
        for j in i..values.len() {
            for position in [0., 1., -1., 30_000_000., i32::MIN as f64, i32::MAX as f64] {
                let bounds = [values[i], -0., values[i], values[j], 0., values[j]];
                for (start, end) in [([-2., -2., -2.], [2., 2., 2.]),
                    ([2., 2., 2.], [-2., -2., -2.]), ([-2., 0., 0.], [2., -0., 0.])] {
                    let ray = [start[0]+position, start[1]+position, start[2]+position,
                        end[0]+position, end[1]+position, end[2]+position, position, position, position];
                    let delta = std::array::from_fn(|a| ray[a+3] - ray[a]);
                    let mut literal = Intersection::new();
                    let mut shortcut = Intersection::new();
                    literal.consider(bounds, &ray, delta);
                    shortcut.consider_ordered(bounds, &ray, delta);
                    assert_eq!(literal.direction, shortcut.direction);
                    assert_eq!(literal.parameter.to_bits(), shortcut.parameter.to_bits());
                    assert_eq!(literal.point(&ray, delta).map(|p| p.map(f64::to_bits)),
                        shortcut.point(&ray, delta).map(|p| p.map(f64::to_bits)));
                }
            }
        }
    }
}

#[test]
fn packed_ordinary_call_matches_disjoint_buffers_including_scalar_statuses() {
    use super::packet::*;
    let coordinates = [0., 0.5, 1., 0., 0.5, 1., 0., 0.25, 0.75, 1.];
    for bits in 0..4096u64 {
        let mut packet = vec![0u64; BYTES / 8];
        packet[0] = bits;
        for (i, c) in coordinates.iter().enumerate() { packet[1024 + i] = f64::to_bits(*c); }
        let ray = [-1., 0.3, 0.6, 2., 0.4, 0.7, 0., 0., 0.];
        for (i, r) in ray.iter().enumerate() { packet[1795 + i] = f64::to_bits(*r); }
        for i in 1810..1813 { packet[i] = 99f64.to_bits(); }
        let mut words = [bits]; let mut output = [99.; 3];
        let literal = unsafe { mattmc_voxel_ray_clip(words.as_mut_ptr(), 1, 2, 2, 3,
            coordinates.as_ptr(), 10, 0, ray.as_ptr(), output.as_mut_ptr()) };
        let packed = unsafe { mattmc_voxel_ray_packet(packet.as_mut_ptr().cast(), 1, 2, 2, 3, 10, 0) };
        assert_eq!(literal, packed);
        assert_eq!(&packet[1810..1813], &output.map(f64::to_bits));
        let bounds = [-0., 0., 0., 1., 1., 1.];
        for (i, v) in bounds.iter().enumerate() { packet[1804 + i] = f64::to_bits(*v); }
        output = [99.; 3]; for i in 1810..1813 { packet[i] = 99f64.to_bits(); }
        let literal = unsafe { mattmc_voxel_ray_box(ray.as_ptr(), bounds.as_ptr(), output.as_mut_ptr()) };
        let packed = unsafe { mattmc_voxel_ray_box_packet(packet.as_mut_ptr().cast()) };
        assert_eq!(literal, packed); assert_eq!(&packet[1810..1813], &output.map(f64::to_bits));
    }
    assert_eq!(unsafe { mattmc_voxel_ray_packet(std::ptr::null_mut(), 0, 1, 1, 1, 6, 0) }, -1);
    assert_eq!(unsafe { mattmc_voxel_ray_box_packet(std::ptr::null_mut()) }, -1);
}

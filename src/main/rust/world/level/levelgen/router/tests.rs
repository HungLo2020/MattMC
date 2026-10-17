//! Router unit checks; exact Java parity is covered by `NativeNoiseRouterTest`.
use super::*;

fn node(op: u32, a: u32, b: u32, c: u32, p: f64, q: f64) -> Node {
    Node { op, a, b, c, state: std::ptr::null(), p, q, r: 0., s: 0. }
}

fn gradient(from: f64, to: f64) -> Node {
    Node { r: -1., s: 1., ..node(Y_CLAMPED_GRADIENT, 0, 0, 0, from, to) }
}

fn geometry(points: usize) -> Geometry {
    Geometry { columns: 2, points, cell_width: 4, cell_height: 8, first_cell_z: 0, cell_min_y: -8, first_noise_x: 0, first_noise_z: 0, flat_size: 5 }
}

fn run(nodes: Vec<Node>, flat: Vec<f64>, slots: usize, points: usize, x: i32) -> Result<Vec<f64>, Error> {
    let root = nodes.len() as u32 - 1;
    let mut router = Router::new(nodes, vec![], flat, slots, vec![root], geometry(points)).unwrap();
    let mut out = vec![f64::NAN; router.output_len()];
    router.slice(x, &mut out).map(|()| out)
}

fn ys(points: usize) -> Vec<f64> {
    (0..points).map(|i| ((i as i32 - 8) * 8) as f64).collect()
}

#[test]
fn every_lane_block_matches_scalar_arithmetic() {
    // clamp(gradient * 3 + 0.5) over more points than one lane block.
    let nodes = vec![gradient(-64., 64.), node(12, 0, 0, 0, 3., 0.), node(13, 1, 0, 0, 0.5, 0.), node(21, 2, 0, 0, -1., 1.)];
    let out = run(nodes, vec![], 0, 150, 0).unwrap();
    for (lane, y) in ys(150).iter().enumerate() {
        let t = (y + 64.) / 128.;
        let g = if t < 0. { -1. } else if t > 1. { 1. } else { -1. + t * 2. };
        let expected = math(21, g * 3. + 0.5, 0., -1., 1.);
        assert_eq!(expected.to_bits(), out[lane].to_bits(), "column 0 y {y}");
        assert_eq!(expected.to_bits(), out[150 + lane].to_bits(), "column 1 y {y}");
    }
}

#[test]
fn short_circuits_skip_children_like_java() {
    // MUL by zero, MIN below its bound, MAX above its bound and the unselected
    // RangeChoice branch never read the FlatCache, which is outside the grid.
    let zero = node(CONSTANT, 0, 0, 0, 0., 0.);
    let flat = node(FLAT_CACHE, 0, 0, 0, 0., 0.);
    for (op, p, q, left) in [(9, 0., 0., 0.), (10, 5., 0., 1.), (11, 0., -5., 1.)] {
        let nodes = vec![node(CONSTANT, 0, 0, 0, left, 0.), flat, node(op, 0, 1, 0, p, q)];
        assert_eq!(Ok(vec![left; 2]), run(nodes, vec![7.; 25], 1, 1, 400));
    }
    let nodes = vec![zero, flat, node(CONSTANT, 0, 0, 0, 2., 0.), node(RANGE_CHOICE, 0, 2, 1, -1., 1.)];
    assert_eq!(Ok(vec![2.; 2]), run(nodes, vec![7.; 25], 1, 1, 400));
    // When needed, the same read reports the slice back to Java.
    let nodes = vec![node(CONSTANT, 0, 0, 0, 1., 0.), flat, node(9, 0, 1, 0, 0., 0.)];
    assert_eq!(Err(Error::OutsideFlatCache), run(nodes.clone(), vec![7.; 25], 1, 1, 400));
    assert_eq!(Ok(vec![7.; 2]), run(nodes, vec![7.; 25], 1, 1, 0));
}

#[test]
fn range_choice_selects_per_lane() {
    let nodes = vec![gradient(-64., 64.), node(CONSTANT, 0, 0, 0, 10., 0.), node(CONSTANT, 0, 0, 0, 20., 0.), node(RANGE_CHOICE, 0, 1, 2, -0.5, 0.5)];
    let out = run(nodes, vec![], 0, 17, 0).unwrap();
    for (lane, y) in ys(17).iter().enumerate() {
        let t = (y + 64.) / 128.;
        let g = if t < 0. { -1. } else if t > 1. { 1. } else { -1. + t * 2. };
        assert_eq!(if (-0.5..0.5).contains(&g) { 10. } else { 20. }, out[lane]);
    }
}

#[test]
fn flat_cache_reads_quart_grid_by_column() {
    let flat: Vec<f64> = (0..50).map(f64::from).collect();
    let nodes = vec![node(CONSTANT, 0, 0, 0, 0., 0.), node(FLAT_CACHE, 1, 0, 0, 0., 0.)];
    // x 8 -> quart 2; columns z 0 and 4 -> quart 0 and 1; slot 1 starts at 25.
    let out = run(nodes, flat, 2, 3, 8).unwrap();
    assert_eq!(vec![27., 27., 27., 32., 32., 32.], out);
}

#[test]
fn programs_are_validated() {
    let ok = |nodes: Vec<Node>, slots: usize| Router::new(nodes, vec![], vec![0.; slots * 25], slots, vec![0], geometry(4)).err();
    assert_eq!(None, ok(vec![node(CONSTANT, 0, 0, 0, 1., 0.)], 0));
    assert_eq!(Some(Invalid::Operation(0)), ok(vec![node(29, 0, 0, 0, 0., 0.)], 0));
    assert_eq!(Some(Invalid::Child(0)), ok(vec![node(14, 0, 0, 0, 0., 0.)], 0));
    assert_eq!(Some(Invalid::Child(0)), ok(vec![node(FLAT_CACHE, 1, 0, 0, 0., 0.)], 1));
    assert_eq!(Some(Invalid::State(0)), ok(vec![node(BLENDED_NOISE, 0, 0, 0, 0., 0.)], 0));
    // Cache2D is transparent only over Y-independent inputs.
    assert_eq!(None, ok(vec![node(FLAT_CACHE, 0, 0, 0, 0., 0.), node(CACHE_2D, 0, 0, 0, 0., 0.)], 1));
    assert_eq!(Some(Invalid::YDependentCache2D(1)), ok(vec![gradient(0., 1.), node(CACHE_2D, 0, 0, 0, 0., 0.)], 0));
    let deep: Vec<Node> = std::iter::once(node(CONSTANT, 0, 0, 0, 1., 0.)).chain((0..300).map(|i| node(14, i, 0, 0, 0., 0.))).collect();
    assert_eq!(Some(Invalid::Depth), ok(deep, 0));
}

#[test]
fn y_independence_follows_inputs() {
    let nodes = vec![
        node(CONSTANT, 0, 0, 0, 1., 0.),
        node(1, 0, 0, 0, 1., 0.),
        node(1, 0, 0, 0, 1., 1.),
        node(5, 0, 0, 0, 1., 0.),
        node(5, 0, 2, 0, 1., 0.),
        node(8, 0, 1, 0, 0., 0.),
        node(8, 0, 2, 0, 0., 0.),
        gradient(0., 1.),
    ];
    let router = Router::new(nodes, vec![], vec![], 0, vec![0], geometry(4)).unwrap();
    assert_eq!(vec![true, true, false, true, false, true, false, false], router.program().y_independent);
}

#[test]
fn splines_take_float_coordinates() {
    let knots = vec![Knot { location: -1., derivative: 0.5, child: 0 }, Knot { location: 1., derivative: -0.25, child: 1 }];
    let spline_nodes = vec![
        SplineNode { axis: -1, start: 0, count: 0, value: -0.75 },
        SplineNode { axis: -1, start: 0, count: 0, value: 0.6 },
        SplineNode { axis: 0, start: 0, count: 2, value: 0. },
    ];
    let spline = Spline { nodes: spline_nodes.clone(), knots: knots.clone(), axes: [0, 0, 0, 0], axis_count: 1 };
    let nodes = vec![gradient(-64., 64.), node(SPLINE, 0, 0, 0, 0., 0.)];
    let mut router = Router::new(nodes, vec![spline], vec![], 0, vec![1], geometry(17)).unwrap();
    let mut out = vec![0.; router.output_len()];
    router.slice(0, &mut out).unwrap();
    for (lane, y) in ys(17).iter().enumerate() {
        let t = (y + 64.) / 128.;
        let g = if t < 0. { -1. } else if t > 1. { 1. } else { -1. + t * 2. };
        let expected = spline_evaluate(&spline_nodes, &knots, 2, &[g as f32, 0., 0., 0.]) as f64;
        assert_eq!(expected.to_bits(), out[lane].to_bits());
    }
}

fn point_program(nodes: Vec<Node>) -> Program {
    let root = nodes.len() as u32 - 1;
    Program::new(nodes, vec![], vec![], 0, vec![root], Geometry { columns: 1, points: 1, flat_size: 1, ..geometry(1) }).unwrap()
}

/// Java's FindTopSurface loop and Mth.floor, one point at a time.
fn java_search(upper: f64, lower: i32, height: i32, density: impl Fn(i32) -> f64) -> i32 {
    let i = floor(upper / height as f64).wrapping_mul(height);
    if i <= lower {
        return lower;
    }
    let mut j = i;
    while j >= lower {
        if density(j) > 0. {
            return j;
        }
        j -= height;
    }
    lower
}

#[test]
fn find_top_surface_matches_the_java_loop() {
    // density = gradient(y) + c with gradient from 1 at y -2000 to -1 at y 2000.
    for (upper, lower, height, offset) in [
        (100.5, -64, 8, 0.),
        (1500., -2000, 4, -0.9),
        (900., -900, 1, 0.3),
        (-70., -64, 8, 0.),
        (f64::NAN, -64, 8, 0.),
        (1e300, -64, 16, -5.),
        (37.9, -64, 8, -3.),
    ] {
        let nodes = vec![
            Node { r: 1., s: -1., ..node(Y_CLAMPED_GRADIENT, 0, 0, 0, -2000., 2000.) },
            node(CONSTANT, 0, 0, 0, offset, 0.),
            node(8, 0, 1, 0, 0., 0.),
            node(CONSTANT, 0, 0, 0, upper, 0.),
            node(FIND_TOP_SURFACE, 2, 3, 0, lower as f64, height as f64),
        ];
        let program = point_program(nodes);
        let mut frame = Frame::new(program.nodes.len());
        let mut out = [0; 3];
        program.surface_levels(&mut frame, &[0, 4, -8], &[0, 12, 4], &mut out).unwrap();
        let expected = java_search(upper, lower, height, |y| {
            let t = (y as f64 + 2000.) / 4000.;
            (if t < 0. { 1. } else if t > 1. { -1. } else { 1. + t * -2. }) + offset
        });
        // Huge upper bounds saturate like Java's int cast; the scan would not finish there.
        if upper < 1e9 {
            assert_eq!([expected; 3], out, "upper {upper} lower {lower} height {height} offset {offset}");
        }
    }
}

#[test]
fn batched_columns_match_single_columns() {
    // A Y-dependent density over a Y-independent frontier node (the constant).
    let nodes = vec![
        gradient(-64., 320.),
        node(CONSTANT, 0, 0, 0, 0.25, 0.),
        node(8, 0, 1, 0, 0., 0.),
        node(CONSTANT, 0, 0, 0, 200., 0.),
        node(FIND_TOP_SURFACE, 2, 3, 0, -64., 8.),
    ];
    let program = point_program(nodes);
    assert_eq!(vec![1], program.frontier);
    let mut frame = Frame::new(program.nodes.len());
    let xs: Vec<i32> = (0..64).map(|i| i * 4 - 100).collect();
    let zs: Vec<i32> = (0..64).map(|i| 300 - i * 8).collect();
    let mut batch = vec![0; 64];
    program.surface_levels(&mut frame, &xs, &zs, &mut batch).unwrap();
    for k in 0..64 {
        let mut single = [0];
        program.surface_levels(&mut frame, &xs[k..k + 1], &zs[k..k + 1], &mut single).unwrap();
        assert_eq!(single[0], batch[k]);
    }
}

#[test]
fn plain_roots_floor_like_java() {
    // Java: (int) saturates, then d < i takes i - 1, which wraps at Integer.MIN_VALUE.
    for (value, expected) in [(2.5, 2), (-2.5, -3), (-0.0, 0), (f64::NAN, 0), (1e300, i32::MAX), (-1e300, i32::MAX), (-3.0, -3)] {
        let program = point_program(vec![node(CONSTANT, 0, 0, 0, value, 0.)]);
        let mut frame = Frame::new(1);
        let mut out = [7];
        program.surface_levels(&mut frame, &[1], &[2], &mut out).unwrap();
        assert_eq!(expected, out[0], "{value}");
    }
}

#[test]
fn find_top_surface_is_root_only() {
    let nodes = vec![node(CONSTANT, 0, 0, 0, 1., 0.), node(FIND_TOP_SURFACE, 0, 0, 0, -64., 8.), node(14, 1, 0, 0, 0., 0.)];
    let root = vec![2];
    assert!(Program::new(nodes, vec![], vec![], 0, root, Geometry { columns: 1, points: 1, flat_size: 1, ..geometry(1) }).is_err());
    let nodes = vec![node(CONSTANT, 0, 0, 0, 1., 0.), node(FIND_TOP_SURFACE, 0, 0, 0, -64., 0.)];
    assert!(Program::new(nodes, vec![], vec![], 0, vec![1], Geometry { columns: 1, points: 1, flat_size: 1, ..geometry(1) }).is_err());
    let nodes = vec![node(CONSTANT, 0, 0, 0, 1., 0.), node(FIND_TOP_SURFACE, 0, 0, 0, -64., 8.)];
    assert!(Router::new(nodes, vec![], vec![], 0, vec![1], geometry(4)).is_err());
}

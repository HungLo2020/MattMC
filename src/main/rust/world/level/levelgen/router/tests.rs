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
    let mut router = Router::new(nodes, vec![], flat, slots, vec![0], geometry(points)).unwrap();
    // Evaluate the last node as the single root.
    router.roots = vec![router.nodes.len() as u32 - 1];
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
    assert_eq!(vec![true, true, false, true, false, true, false, false], router.y_independent);
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

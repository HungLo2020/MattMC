use super::{
    evaluate::evaluate,
    program::{valid, Knot, Node},
};
#[test]
fn layout_and_validation() {
    assert_eq!(std::mem::size_of::<Node>(), 16);
    assert_eq!(std::mem::size_of::<Knot>(), 12);
    let mut nodes = vec![
        Node {
            axis: -1,
            start: 0,
            count: 0,
            value: 2.0,
        },
        Node {
            axis: 0,
            start: 0,
            count: 1,
            value: 0.0,
        },
    ];
    let knots = vec![Knot {
        location: 0.0,
        derivative: 0.0,
        child: 0,
    }];
    assert!(valid(&nodes, &knots));
    nodes[1].start = 1;
    assert!(!valid(&nodes, &knots));
    nodes[1].start = 0;
    nodes[1].axis = 4;
    assert!(!valid(&nodes, &knots));
    nodes[1].axis = 0;
    assert!(!valid(
        &nodes,
        &[Knot {
            child: 1,
            ..knots[0]
        }]
    ));
}
#[test]
fn zero_derivative_does_not_multiply_infinite_offset() {
    let nodes = [
        Node {
            axis: -1,
            start: 0,
            count: 0,
            value: -0.0,
        },
        Node {
            axis: 0,
            start: 0,
            count: 1,
            value: 0.0,
        },
    ];
    for slope in [0.0, -0.0] {
        let knots = [Knot {
            location: 0.0,
            derivative: slope,
            child: 0,
        }];
        for x in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY, 0.0, -0.0] {
            assert_eq!(
                evaluate(&nodes, &knots, 1, &[x, 0.0, 0.0, 0.0]).to_bits(),
                (-0.0f32).to_bits()
            );
        }
    }
}
#[test]
fn knot_selection_and_linear_extension() {
    let nodes = [
        Node {
            axis: -1,
            start: 0,
            count: 0,
            value: 2.0,
        },
        Node {
            axis: -1,
            start: 0,
            count: 0,
            value: 4.0,
        },
        Node {
            axis: 0,
            start: 0,
            count: 2,
            value: 0.0,
        },
    ];
    let knots = [
        Knot {
            location: 0.0,
            derivative: 2.0,
            child: 0,
        },
        Knot {
            location: 1.0,
            derivative: 2.0,
            child: 1,
        },
    ];
    for (x, v) in [(-1.0, 0.0), (0.0, 2.0), (0.25, 2.5), (1.0, 4.0), (2.0, 6.0)] {
        assert_eq!(evaluate(&nodes, &knots, 2, &[x, 0.0, 0.0, 0.0]), v);
    }
}

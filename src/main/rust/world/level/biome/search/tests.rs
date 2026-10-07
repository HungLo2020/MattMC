use super::*;

/// `BlockPos.spiralAround(ZERO, 1, EAST, SOUTH)` as Java iterates it.
#[test]
fn spiral_matches_java_order() {
    let offsets: Vec<(i32, i32)> = spiral(1).collect();
    assert_eq!(offsets, vec![(0, 0), (1, 0), (1, 1), (0, 1), (-1, 1), (-1, 0), (-1, -1), (0, -1), (1, -1)]);
    assert_eq!(spiral(0).collect::<Vec<_>>(), vec![(0, 0)]);
    assert_eq!(spiral(3).count(), 49);
}

#[test]
fn quantize_matches_java_float_cast() {
    assert_eq!(quantize(0.123456789), (0.123456789f32 * 10000.0f32) as i64);
    assert_eq!(quantize(-1.0), -10000);
    assert_eq!(quantize(f64::NAN), 0);
    // The float product rounds up where a double product would not (Java casts first).
    assert_eq!(quantize(1.2805999899995775), 12806);
}

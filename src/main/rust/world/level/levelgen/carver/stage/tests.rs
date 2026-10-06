use super::*;
use crate::world::level::levelgen::random::Legacy;

#[test]
fn floor_matches_java_mth_floor() {
    assert_eq!(floor(-0.5), -1);
    assert_eq!(floor(2.0), 2);
    assert_eq!(floor(-2.0), -2);
    assert_eq!(floor(f64::NAN), 0);
    // (int) saturates, and d < MIN_VALUE subtracts one with wrapping, as Java does.
    assert_eq!(floor(-1e300), i32::MAX);
    assert_eq!(floor(1e300), i32::MAX);
}

#[test]
fn legacy_next_long_matches_java_random() {
    // new java.util.Random(0).nextLong(), the same LCG as LegacyRandomSource.
    assert_eq!(Legacy::new(0).next_long(), -4962768465676381896);
}

#[test]
fn uniform_float_samples_like_mth_random_between() {
    let mut a = Legacy::new(42);
    let mut b = Legacy::new(42);
    let value = FloatProvider::Uniform(0.75, 1.0).sample(&mut a);
    assert_eq!(value, b.next_float() * (1.0f32 - 0.75f32) + 0.75f32);
    assert_eq!(FloatProvider::Constant(3.0).sample(&mut a), 3.0);
}

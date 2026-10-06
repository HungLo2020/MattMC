//! Exact-order interpolation shared by generation kernels.
#[inline(always)]
pub(crate) fn lerp(t: f64, a: f64, b: f64) -> f64 {
    a + t * (b - a)
}
#[inline(always)]
pub(crate) fn lerp2(x: f64, y: f64, a: f64, b: f64, c: f64, d: f64) -> f64 {
    lerp(y, lerp(x, a, b), lerp(x, c, d))
}
#[inline(always)]
pub(crate) fn lerp3(x: f64, y: f64, z: f64, v: [f64; 8]) -> f64 {
    lerp(
        z,
        lerp2(x, y, v[0], v[1], v[2], v[3]),
        lerp2(x, y, v[4], v[5], v[6], v[7]),
    )
}

/// Java's `Math.round(double)`: halves round toward positive infinity, NaN is
/// 0, out-of-range values saturate (bit-exact port of the JDK algorithm).
pub(crate) fn java_round(a: f64) -> i64 {
    let bits = a.to_bits() as i64;
    let biased_exp = (bits & 0x7FF0_0000_0000_0000) >> 52;
    let shift = (53 - 2 + 1023) - biased_exp;
    if shift & -64 == 0 {
        let mut r = (bits & 0x000F_FFFF_FFFF_FFFF) | (0x000F_FFFF_FFFF_FFFF + 1);
        if bits < 0 {
            r = -r;
        }
        ((r >> shift) + 1) >> 1
    } else {
        a as i64
    }
}

#[cfg(test)]
mod tests {
    use super::java_round;

    #[test]
    fn java_round_matches_math_round() {
        assert_eq!(java_round(0.5), 1);
        assert_eq!(java_round(-0.5), 0);
        assert_eq!(java_round(-2.5), -2);
        assert_eq!(java_round(2.5), 3);
        assert_eq!(java_round(0.49999999999999994), 0);
        assert_eq!(java_round(-1.5000000000000002), -2);
        assert_eq!(java_round(f64::NAN), 0);
        assert_eq!(java_round(1e20), i64::MAX);
        assert_eq!(java_round(-1e20), i64::MIN);
        assert_eq!(java_round(4503599627370497.0), 4503599627370497);
    }
}

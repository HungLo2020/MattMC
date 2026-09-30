//! Java-compatible density arithmetic and short-circuit predicates.
#[inline]
pub(crate) fn min(a: f64, b: f64) -> f64 {
    if a.is_nan() {
        a
    } else if b.is_nan() {
        b
    } else if a == 0. && b == 0. {
        f64::from_bits(a.to_bits() | b.to_bits())
    } else if a <= b {
        a
    } else {
        b
    }
}
#[inline]
pub(crate) fn max(a: f64, b: f64) -> f64 {
    if a.is_nan() {
        a
    } else if b.is_nan() {
        b
    } else if a == 0. && b == 0. {
        f64::from_bits(a.to_bits() & b.to_bits())
    } else if a >= b {
        a
    } else {
        b
    }
}
#[inline]
pub(crate) fn clamp(a: f64, lo: f64, hi: f64) -> f64 {
    if a < lo {
        lo
    } else {
        min(a, hi)
    }
}
// Shared by fused programs and the Java traversal adapter. These are the only
// production implementations of the migrated density operations.
#[inline]
pub(crate) fn rarity(op: u32, d: f64) -> f64 {
    if op == 6 {
        if d < -0.5 {
            0.75
        } else if d < 0. {
            1.
        } else if d < 0.5 {
            1.5
        } else {
            2.
        }
    } else {
        if d < -0.75 {
            0.5
        } else if d < -0.5 {
            0.75
        } else if d < 0.5 {
            1.
        } else if d < 0.75 {
            2.
        } else {
            3.
        }
    }
}
#[inline]
pub(crate) fn in_range(a: f64, p: f64, q: f64) -> bool {
    a >= p && a < q
}
#[inline]
pub(crate) fn needs_right(op: u32, a: f64, p: f64, q: f64) -> bool {
    match op {
        9 => a != 0.,
        10 => !(a < p),
        11 => !(a > q),
        _ => true,
    }
}
#[inline]
pub(crate) fn math(op: u32, a: f64, b: f64, p: f64, q: f64) -> f64 {
    match op {
        8 => a + b,
        9 => {
            if a == 0. {
                0.
            } else {
                a * b
            }
        }
        10 => {
            if a < p {
                a
            } else {
                min(a, b)
            }
        }
        11 => {
            if a > q {
                a
            } else {
                max(a, b)
            }
        }
        12 => a * p,
        13 => a + p,
        14 => a.abs(),
        15 => a * a,
        16 => a * a * a,
        17 => {
            if a > 0. {
                a
            } else {
                a * 0.5
            }
        }
        18 => {
            if a > 0. {
                a
            } else {
                a * 0.25
            }
        }
        19 => 1. / a,
        20 => {
            let a = clamp(a, -1., 1.);
            a / 2. - a * a * a / 24.
        }
        21 => clamp(a, p, q),
        22 => {
            if in_range(a, p, q) {
                1.
            } else {
                0.
            }
        }
        23 => rarity(6, a),
        24 => rarity(7, a),
        _ => f64::NAN,
    }
}

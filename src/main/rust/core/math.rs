//! Pure arithmetic shared by content, world generation and rendering.
/// Java Mth.getSeed: the x multiplication wraps at 32 bits before widening.
#[inline]
pub fn position_seed(x: i32, y: i32, z: i32) -> i64 {
    let value = (x.wrapping_mul(3_129_871) as i64)
        ^ (z as i64).wrapping_mul(116_129_781) ^ y as i64;
    value.wrapping_mul(value).wrapping_mul(42_317_861)
        .wrapping_add(value.wrapping_mul(11)) >> 16
}

#[cfg(test)]
mod tests {
    #[test]
    fn frozen_coordinate_seeds_preserve_integer_wrapping() {
        for (x,z,expected) in [
            (0i32,-113i32,-60271570463935i64),
            (-2147483648i32,2147483647i32,-86341287861592i64),
            (2147483647i32,-2147483648i32,125895685522240i64),
            (-30000000i32,30000000i32,75029583713242i64),
            (-687i32,687i32,-89472936035106i64),
        ] { assert_eq!(super::position_seed(x,0,z),expected); }
    }
}

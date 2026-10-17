//! Scalar equivalent of Frozen DH cloud corner tests: no transient vectors.
fn normalize(mut value: [f32; 3]) -> [f32; 3] {
    let sum = value[0] * value[0] + value[1] * value[1] + value[2] * value[2];
    if (sum as f64) < 1.0e-5 {
        return value;
    }
    // Float.floatToIntBits canonicalizes NaNs; preserve its signed shift.
    let bits = if sum.is_nan() {
        0x7fc00000
    } else {
        sum.to_bits()
    } as i32;
    let estimate = f32::from_bits(1597463007i32.wrapping_sub(bits >> 1) as u32);
    let inverse = estimate * (1.5 - (0.5 * sum) * estimate * estimate);
    for lane in &mut value {
        *lane *= inverse;
    }
    value
}
pub(super) fn culled(
    origin: [f32; 3],
    width: i32,
    offset: [i32; 2],
    camera: [f64; 3],
    look: [f32; 3],
    radius: i32,
) -> bool {
    if offset.iter().all(|v| (-1..=1).contains(v)) {
        return false;
    }
    let [x, y, z] = origin.map(f64::from);
    let end_x = (origin[0] + width as f32) as f64;
    let end_z = (origin[2] + width as f32) as f64;
    let corners = [[x, z], [x, end_z], [end_x, z], [end_x, end_z]];
    let look = normalize(look);
    let distance = radius.wrapping_mul(16) as f64 * 1.5;
    let mut outside = true;
    let mut behind = true;
    for [cx, cz] in corners {
        let dx = cx - camera[0];
        let dz = cz - camera[2];
        if (dx * dx + dz * dz).sqrt() <= distance {
            outside = false;
        }
        let to = normalize([dx as f32, (y - camera[1]) as f32, dz as f32]);
        if look[0] * to[0] + look[1] * to[1] + look[2] * to[2] > 0.0 {
            behind = false;
        }
    }
    outside || behind
}

use super::layout::Geometry;

#[inline]
fn bury(x: f64, y: f64, z: f64) -> f64 {
    let length = (x * x + y * y + z * z).sqrt();
    let t = (length - 0.0) / (6.0 - 0.0);
    if t < 0.0 {
        1.0
    } else if t > 1.0 {
        0.0
    } else {
        1.0 + t * (0.0 - 1.0)
    }
}
#[inline]
fn beard(x: i32, y: i32, z: i32, ground_y: i32, kernel: &[f32]) -> f64 {
    let ix = x.wrapping_add(12);
    let iy = y.wrapping_add(12);
    let iz = z.wrapping_add(12);
    if !(0..24).contains(&ix) || !(0..24).contains(&iy) || !(0..24).contains(&iz) {
        return 0.0;
    }
    let dy = ground_y as f64 + 0.5;
    let length = (x as f64) * (x as f64) + dy * dy + (z as f64) * (z as f64);
    let a = length / 2.0;
    let half = 0.5 * a;
    // Java's signed long shift and wrapping subtraction; no reciprocal-sqrt approximation.
    let bits = 6910469410427058090i64.wrapping_sub((a.to_bits() as i64) >> 1);
    let estimate = f64::from_bits(bits as u64);
    let inverse = estimate * (1.5 - half * estimate * estimate);
    let weight = -dy * inverse / 2.0;
    weight * kernel[(iz * 24 * 24 + ix * 24 + iy) as usize] as f64
}
#[inline]
fn distance(position: i32, low: i32, high: i32) -> i32 {
    0.max(low.wrapping_sub(position).max(position.wrapping_sub(high)))
}
pub(super) fn point(g: &Geometry<'_>, kernel: &[f32], x: i32, y: i32, z: i32) -> f64 {
    let b = g.bounds;
    if x < b[0] || x > b[3] || y < b[1] || y > b[4] || z < b[2] || z > b[5] {
        return 0.0;
    }
    let mut sum = 0.0;
    for p in g.pieces.chunks_exact(8) {
        let dx = distance(x, p[0], p[3]);
        let dz = distance(z, p[2], p[5]);
        let ground = p[1].wrapping_add(p[6]);
        let dy = y.wrapping_sub(ground);
        let q = match p[7] {
            0 => 0,
            1 | 2 => dy,
            3 => distance(y, ground, p[4]),
            _ => distance(y, p[1], p[4]),
        };
        sum += match p[7] {
            0 => 0.0,
            1 => bury(dx as f64, q as f64 / 2.0, dz as f64),
            2 | 3 => beard(dx, q, dz, dy, kernel) * 0.8,
            _ => bury(dx as f64 / 2.0, q as f64 / 2.0, dz as f64 / 2.0) * 0.8,
        };
    }
    for j in g.junctions.chunks_exact(3) {
        let dy = y.wrapping_sub(j[1]);
        sum += beard(x.wrapping_sub(j[0]), dy, z.wrapping_sub(j[2]), dy, kernel) * 0.4;
    }
    sum
}

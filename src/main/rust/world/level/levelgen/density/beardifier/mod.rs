//! Structure terrain adjustment, evaluated in original piece/junction order.
mod evaluate;
mod ffi;
mod layout;
#[cfg(test)]
mod tests;

pub(crate) use layout::KERNEL_SIZE;

/// One terrain cell in `NativeBeardifier.fillCell`'s order (Y down, X, Z),
/// including its exact-zero result for cells wholly outside the affected box
/// (`data[2..8]`). False for invalid geometry or shape.
pub(crate) fn cell(data: &[i32], kernel: &[f32], out: &mut [f64], x: i32, y: i32, z: i32, width: i32, height: i32) -> bool {
    if data.len() < 8 || kernel.len() != KERNEL_SIZE || out.len() as i64 != width as i64 * width as i64 * height as i64 {
        return false;
    }
    let (end_x, end_y, end_z) = (x as i64 + width as i64 - 1, y as i64 + height as i64 - 1, z as i64 + width as i64 - 1);
    let max = i32::MAX as i64;
    if end_x <= max && end_y <= max && end_z <= max
        && (end_x < data[2] as i64 || x > data[5] || end_y < data[3] as i64 || y > data[6] || end_z < data[4] as i64 || z > data[7])
    {
        out.fill(0.);
        return true;
    }
    let Some(geometry) = layout::Geometry::read(data) else {
        return false;
    };
    let mut index = 0;
    for dy in (0..height).rev() {
        for dx in 0..width {
            for dz in 0..width {
                out[index] = evaluate::point(&geometry, kernel, x.wrapping_add(dx), y.wrapping_add(dy), z.wrapping_add(dz));
                index += 1;
            }
        }
    }
    true
}

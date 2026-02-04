// Rust FFI implementation of Vec3f static utility functions
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.math.Vec3f
// Author: James Seibel (original Java), migrated to Rust for performance

/// Calculates Manhattan distance between two 3D float vectors
#[no_mangle]
pub extern "C" fn vec3f_get_manhattan_distance(
    ax: f32, ay: f32, az: f32,
    bx: f32, by: f32, bz: f32,
) -> f32 {
    (ax - bx).abs() + (ay - by).abs() + (az - bz).abs()
}

/// Calculates Euclidean distance between two 3D float vectors
#[no_mangle]
pub extern "C" fn vec3f_get_distance(
    ax: f32, ay: f32, az: f32,
    bx: f32, by: f32, bz: f32,
) -> f64 {
    let dx = (ax - bx) as f64;
    let dy = (ay - by) as f64;
    let dz = (az - bz) as f64;
    (dx * dx + dy * dy + dz * dz).sqrt()
}

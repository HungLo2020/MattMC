// Rust FFI implementation of Vec3d static utility functions
// This provides C-compatible functions that can be called via Java FFM
//
// Original Java class: com.seibel.distanthorizons.core.util.math.Vec3d
// Author: James Seibel (original Java), migrated to Rust for performance

/// Calculates Manhattan distance between two 3D double vectors
#[no_mangle]
pub extern "C" fn vec3d_get_manhattan_distance(
    ax: f64, ay: f64, az: f64,
    bx: f64, by: f64, bz: f64,
) -> f64 {
    (ax - bx).abs() + (ay - by).abs() + (az - bz).abs()
}

/// Calculates Euclidean distance between two 3D double vectors
#[no_mangle]
pub extern "C" fn vec3d_get_distance(
    ax: f64, ay: f64, az: f64,
    bx: f64, by: f64, bz: f64,
) -> f64 {
    let dx = ax - bx;
    let dy = ay - by;
    let dz = az - bz;
    (dx * dx + dy * dy + dz * dz).sqrt()
}

/// Calculates squared Euclidean distance between two 3D double vectors
/// This is faster than get_distance as it avoids the sqrt operation
#[no_mangle]
pub extern "C" fn vec3d_get_squared_distance(
    ax: f64, ay: f64, az: f64,
    bx: f64, by: f64, bz: f64,
) -> f64 {
    let dx = ax - bx;
    let dy = ay - by;
    let dz = az - bz;
    dx * dx + dy * dy + dz * dz
}

/// Calculates horizontal distance between two 3D double vectors (ignoring Y)
#[no_mangle]
pub extern "C" fn vec3d_get_horizontal_distance(
    ax: f64, _ay: f64, az: f64,
    bx: f64, _by: f64, bz: f64,
) -> f64 {
    let dx = ax - bx;
    let dz = az - bz;
    (dx * dx + dz * dz).sqrt()
}

mod ffi;
mod geometry;
mod spheres;
#[cfg(test)]
mod tests;

#[cfg(target_arch = "x86_64")]
mod simd;

/// Ordered `(x, y, firstZ, lastZ)` spans for one vein, for native callers:
/// `samples` holds the six endpoints then one random double per sphere.
/// `spheres` and `output` are reusable scratch; `output` holds the spans after.
pub(crate) fn spans(samples: &[f64], shape: &[f64], lower: [i32; 3], spheres: &mut Vec<f64>, output: &mut Vec<i32>) {
    let count = samples.len().saturating_sub(6);
    spheres.clear();
    spheres.resize(count * 4, 0.0);
    spheres::prepare(samples, &shape[..count * 2], spheres);
    geometry::prune(spheres);
    if output.len() < 1024 {
        output.resize(1024, 0);
    }
    loop {
        let length = geometry::raster(spheres, lower, output);
        if length <= output.len() {
            output.truncate(length);
            return;
        }
        output.resize(length, 0);
    }
}

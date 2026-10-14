mod ffi;
mod geometry;
mod spheres;
#[cfg(test)]
mod tests;

#[cfg(target_arch = "x86_64")]
mod simd;

/// Ordered `(x, y, firstZ, lastZ)` spans for one vein, for native callers:
/// `samples` holds the six endpoints then one random double per sphere.
pub(crate) fn spans(samples: &[f64], shape: &[f64], lower: [i32; 3]) -> Vec<i32> {
    let count = samples.len().saturating_sub(6);
    let mut spheres = vec![0.0; count * 4];
    spheres::prepare(samples, &shape[..count * 2], &mut spheres);
    geometry::prune(&mut spheres);
    let mut output = vec![0; 1024];
    loop {
        let length = geometry::raster(&spheres, lower, &mut output);
        if length <= output.len() {
            output.truncate(length);
            return output;
        }
        output.resize(length, 0);
    }
}

const NO_VALUE: f64 = f64::MAX;

/// Literal Java heightToOffset association, including the double positive modulo.
fn height_to_offset(height: f64) -> f64 {
    let f = height + 0.5;
    let g = ((f % 8.0) + 8.0) % 8.0;
    1.0 * (32.0 * (f - 128.0) - 3.0 * (f - 120.0) * g + 3.0 * g * g) / (128.0 * (32.0 - 3.0 * g))
}

/// Output pairs retain query order. No floating reduction is reordered.
/// false requests the original Java compatibility evaluator (nonfinite result).
pub(super) fn evaluate(
    coordinates: &[i32],
    heights: &[f64],
    queries: &[i32],
    direct: &[f64],
    output: &mut [f64],
) -> bool {
    for (q, &height) in direct.iter().enumerate() {
        let (alpha, offset) = if height != NO_VALUE {
            (0.0, height_to_offset(height))
        } else {
            let mut total = 0.0;
            let mut weighted = 0.0;
            let mut nearest = f64::INFINITY;
            let x = queries[q * 2];
            let z = queries[q * 2 + 1];
            for (p, &height) in heights.iter().enumerate() {
                // Mth.length(float,float): wrapped int subtraction, float cast,
                // double squares/sqrt, then float rounding and double promotion.
                let dx = x.wrapping_sub(coordinates[p * 2]) as f32 as f64;
                let dz = z.wrapping_sub(coordinates[p * 2 + 1]) as f32 as f64;
                let distance = (dx * dx + dz * dz).sqrt() as f32 as f64;
                if !(distance > 27.0) {
                    if distance < nearest {
                        nearest = distance;
                    }
                    let weight = 1.0 / (distance * distance * distance * distance);
                    weighted += height * weight;
                    total += weight;
                }
            }
            if nearest == f64::INFINITY {
                (1.0, 0.0)
            } else {
                let height = weighted / total;
                let f = (nearest / 28.0).clamp(0.0, 1.0);
                (3.0 * f * f - 2.0 * f * f * f, height_to_offset(height))
            }
        };
        if !alpha.is_finite() || !offset.is_finite() {
            return false;
        }
        output[q * 2] = alpha;
        output[q * 2 + 1] = offset;
    }
    true
}

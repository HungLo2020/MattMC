//! Sphere formation with the original float interpolation fractions and sine
//! table factors supplied once by Java. Only random draws cross per placement.
pub(super) fn prepare(input: &[f64], shape: &[f64], output: &mut [f64]) {
    let count = output.len() / 4;
    for (q, sphere) in output.chunks_exact_mut(4).enumerate() {
        let progress = shape[q * 2];
        sphere[0] = input[0] + progress * (input[1] - input[0]);
        sphere[1] = input[2] + progress * (input[3] - input[2]);
        sphere[2] = input[4] + progress * (input[5] - input[4]);
        let v = input[6 + q] * count as f64 / 16.0;
        sphere[3] = (shape[q * 2 + 1] * v + 1.0) / 2.0;
    }
}

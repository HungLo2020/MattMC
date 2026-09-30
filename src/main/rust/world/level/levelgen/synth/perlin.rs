//! Ordered octave accumulation over Improved noise.
use super::{common::wrap, state::Octave};
#[inline(always)]
pub(crate) fn perlin(
    o: &[Octave],
    mut freq: f64,
    mut weight: f64,
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    fixed: bool,
) -> f64 {
    let mut sum = 0.0;
    for n in o {
        if n.present != 0 {
            let v = n.noise(
                wrap(x * freq),
                if fixed { -n.y } else { wrap(y * freq) },
                wrap(z * freq),
                ys * freq,
                ym * freq,
            );
            sum += n.amplitude * v * weight;
        }
        freq *= 2.0;
        weight /= 2.0;
    }
    sum
}

#[cfg(target_arch = "x86_64")]
pub(super) mod avx2;

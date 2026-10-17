const CELLS: usize = 216;
const CURVE: [f64; 7] = [0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0];

fn rgb(color: u32) -> [f64; 3] {
    [
        ((color >> 16) & 255) as f64 / 255.0,
        ((color >> 8) & 255) as f64 / 255.0,
        (color & 255) as f64 / 255.0,
    ]
}
pub(crate) fn sample(position: [f64; 3], colors: &[u32; CELLS]) -> [f64; 3] {
    // Frozen tests full color words for homogeneity, even though alpha is unused.
    if colors.iter().all(|&c| c == colors[0]) {
        return rgb(colors[0]);
    }
    let delta = position.map(|p| p - p.floor());
    let mut sum = [0.0; 3];
    let mut total = 0.0;
    for x in 0..6 {
        let dx = CURVE[x + 1] + delta[0] * (CURVE[x] - CURVE[x + 1]);
        for y in 0..6 {
            let dy = CURVE[y + 1] + delta[1] * (CURVE[y] - CURVE[y + 1]);
            for z in 0..6 {
                let dz = CURVE[z + 1] + delta[2] * (CURVE[z] - CURVE[z + 1]);
                let factor = dx * dy * dz;
                total += factor;
                let color = rgb(colors[z * 36 + y * 6 + x]);
                for axis in 0..3 {
                    sum[axis] += color[axis] * factor;
                }
            }
        }
    }
    let reciprocal = 1.0 / total;
    sum.map(|v| v * reciprocal)
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn frozen_actual_sky_sampler_all_bits_match() {
        use std::io::Read;
        let mut bytes = Vec::new();
        flate2::read::GzDecoder::new(include_bytes!("frozen-sky-sampler.bin.gz").as_slice())
            .read_to_end(&mut bytes)
            .unwrap();
        let mut at = 0;
        let read4 = |at: &mut usize| {
            let value = u32::from_be_bytes(bytes[*at..*at + 4].try_into().unwrap());
            *at += 4;
            value
        };
        let read8 = |at: &mut usize| {
            let value = u64::from_be_bytes(bytes[*at..*at + 8].try_into().unwrap());
            *at += 8;
            value
        };
        assert_eq!(read4(&mut at), 0x534b5931);
        let count = read4(&mut at);
        for _ in 0..count {
            let position = std::array::from_fn(|_| f64::from_bits(read8(&mut at)));
            let colors = std::array::from_fn(|_| read4(&mut at));
            let expected: [u64; 3] = std::array::from_fn(|_| read8(&mut at));
            assert_eq!(sample(position, &colors).map(f64::to_bits), expected);
        }
        assert_eq!(count, 256);
        assert_eq!(at, bytes.len());
    }
}

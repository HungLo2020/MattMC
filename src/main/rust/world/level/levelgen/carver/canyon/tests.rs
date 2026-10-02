use super::evaluate::{evaluate, scalar};
use super::ffi::{mattmc_canyon_candidates, mattmc_canyon_candidates_small};

fn reference(f: &[i32], s: &[f64], widths: &[f32]) -> Vec<u64> {
    let height = (f[5] as i64 - f[4] as i64) as usize;
    let words = height.div_ceil(64);
    let mut out = vec![0; (f[3] - f[2] + 1) as usize * (f[7] - f[6] + 1) as usize * words];
    let mut col = 0;
    for x in f[2]..=f[3] {
        let w = (f[0].wrapping_add(x) as f64 + 0.5 - s[0]) / s[3];
        for z in f[6]..=f[7] {
            let q = (f[1].wrapping_add(z) as f64 + 0.5 - s[2]) / s[3];
            if !(w * w + q * q >= 1.0) {
                for y in ((f[4] as i64 + 1)..=f[5] as i64).rev() {
                    let ab = (y as f64 - 0.5 - s[1]) / s[4];
                    if !((w * w + q * q) * widths[(y - f[8] as i64 - 1) as usize] as f64
                        + ab * ab / 6.0
                        >= 1.0)
                    {
                        let iy = (f[5] as i64 - y) as usize;
                        out[col * words + iy / 64] |= 1 << (iy % 64);
                    }
                }
            }
            col += 1;
        }
    }
    out
}
fn compare(f: &[i32], s: &[f64], widths: &[f32]) {
    super::evaluate::initialize();
    let expected = reference(f, s, widths);
    for evaluator in [evaluate, scalar] {
        let mut actual = vec![123; expected.len()];
        evaluator(f, s, widths, &mut actual);
        assert_eq!(actual, expected);
    }
}
#[test]
fn ordered_shapes_and_word_edges() {
    for height in [1, 2, 63, 64, 65, 127, 128, 129, 384, 2048] {
        let f = [-16, 16, 0, 15, -64, -64 + height, 0, 15, -64];
        let widths: Vec<f32> = (0..height)
            .map(|i| if i % 2 == 0 { 1.0 } else { 3.25 })
            .collect();
        for scale in [0.125, 1.5, 3.0, 7.75, 32.0, 1024.0] {
            compare(&f, &[-8.25, -32.5, 24.75, scale, scale * 3.0], &widths);
        }
    }
}
#[test]
fn strict_boundaries_and_wrapped_coordinates() {
    let widths = vec![1.0; 40];
    for center in [-1.0, 0.0, 0.5, 1.0] {
        for radius in [
            f64::MIN_POSITIVE,
            0.5,
            1.0,
            2.0,
            2.0 - f64::EPSILON,
            2.0 + f64::EPSILON,
        ] {
            let f = [i32::MAX - 3, i32::MIN, 0, 15, -20, 20, 0, 15, -20];
            let s = [
                i32::MIN as f64 + center,
                center,
                i32::MIN as f64 + center,
                radius,
                radius,
            ];
            compare(&f, &s, &widths);
        }
    }
}
fn abi(f: &[i32], s: &[f64], widths: &[f32], out: &mut [u64], small: bool) -> i32 {
    let entry = if small {
        mattmc_canyon_candidates_small
    } else {
        mattmc_canyon_candidates
    };
    unsafe {
        entry(
            f.as_ptr(),
            f.len() as i32,
            s.as_ptr(),
            s.len() as i32,
            widths.as_ptr(),
            widths.len() as i32,
            out.as_mut_ptr(),
            out.len() as i32,
        )
    }
}
#[test]
fn validated_abi_and_rejections_do_not_write() {
    let f = [0, 0, 0, 15, -8, 8, 0, 15, -8];
    let s = [8.0, 0.0, 8.0, 4.0, 6.0];
    let widths = vec![1.0; 16];
    let expected = reference(&f, &s, &widths);
    let mut out = vec![123; expected.len()];
    assert_eq!(abi(&f, &s, &widths, &mut out, false), 0);
    assert_eq!(out, expected);
    for (index, value) in [(2, -1), (3, 16), (4, 8), (5, 3000), (8, 0)] {
        let mut bad = f;
        bad[index] = value;
        out.fill(123);
        assert_eq!(abi(&bad, &s, &widths, &mut out, false), -1);
        assert!(out.iter().all(|v| *v == 123));
    }
    for (index, value) in [(0, f64::NAN), (3, 0.0), (4, -1.0), (1, f64::INFINITY)] {
        let mut bad = s;
        bad[index] = value;
        out.fill(123);
        assert_eq!(abi(&f, &bad, &widths, &mut out, false), -1);
        assert!(out.iter().all(|v| *v == 123));
    }
    for index in 0..16 {
        let mut bad = widths.clone();
        bad[index] = f32::NAN;
        out.fill(123);
        assert_eq!(abi(&f, &s, &bad, &mut out, false), -1);
        assert!(out.iter().all(|v| *v == 123));
    }
    out.fill(123);
    assert_eq!(abi(&f[..8], &s, &widths, &mut out, false), -1);
    assert_eq!(abi(&f, &s[..4], &widths, &mut out, false), -1);
    assert_eq!(abi(&f, &s, &[], &mut out, false), -1);
    assert_eq!(abi(&f, &s, &widths, &mut out[..255], false), -1);
    assert!(out.iter().all(|v| *v == 123));
}
#[test]
fn short_entry_bounds_and_exact_output() {
    let s = [8.0, 0.0, 8.0, 4.0, 6.0];
    let widths = vec![1.0; 2048];
    for height in [16, 32, 33, 65, 2048] {
        let f = [0, 0, 0, 15, 0, height, 0, 15, 0];
        let expected = reference(&f, &s, &widths);
        let mut out = vec![123; expected.len()];
        let accepted = height <= 32;
        assert_eq!(
            abi(&f, &s, &widths, &mut out, true),
            if accepted { 0 } else { -1 }
        );
        if accepted {
            assert_eq!(out, expected);
        } else {
            assert!(out.iter().all(|v| *v == 123));
        }
    }
}

use super::{ffi, geometry};
#[test]
fn boundaries_duplicates_and_pruning() {
    let mut spheres = [0.5, 0.5, 0.5, 1.0, 0.5, 0.5, 0.5, 1.0];
    geometry::prune(&mut spheres);
    let mut out = [99; 12];
    assert_eq!(geometry::raster(&spheres, [-4; 3], &mut out), 8);
    assert_eq!(&out[..8], &[0, 0, 0, 0, 0, 0, 0, 0]);
    assert_eq!(geometry::raster(&spheres, [-4; 3], &mut []), 8);
    spheres[7] = 0.5;
    geometry::prune(&mut spheres);
    assert_eq!(spheres[7], -1.0);
    unsafe {
        assert_eq!(
            ffi::mattmc_ore_geometry(std::ptr::null_mut(), 0, out.as_mut_ptr(), 12, 0, 0, 0),
            -1
        );
    }
}

#[test]
fn small_call_bounds_and_short_output() {
    let input = [0.0; 22];
    let shape = [1.0; 32];
    let mut output = [123; 12];
    unsafe {
        assert_eq!(
            ffi::mattmc_ore_vein_small(
                input.as_ptr(),
                shape.as_ptr(),
                17,
                output.as_mut_ptr(),
                12,
                0,
                0,
                0
            ),
            -2
        );
        assert_eq!(
            ffi::mattmc_ore_vein(
                input.as_ptr(),
                shape.as_ptr(),
                -1,
                output.as_mut_ptr(),
                12,
                0,
                0,
                0
            ),
            -1
        );
        assert_eq!(
            ffi::mattmc_ore_vein_small(
                input.as_ptr(),
                shape.as_ptr(),
                16,
                output.as_mut_ptr(),
                12,
                0,
                0,
                0
            ),
            0
        );
        let mut samples = input;
        samples[6] = f64::NAN;
        assert_eq!(
            ffi::mattmc_ore_vein_small(
                samples.as_ptr(),
                shape.as_ptr(),
                16,
                output.as_mut_ptr(),
                12,
                0,
                0,
                0
            ),
            -2
        );
    }
    let mut data = [0.5, 0.5, 0.5, 2.0];
    let mut full = [0; 256];
    let mut short = [0; 4];
    let expected = geometry::raster(&data, [-4; 3], &mut full);
    assert_eq!(geometry::raster(&data, [-4; 3], &mut short), expected);
    assert_eq!(short, full[..4]);
    data[3] = 1.0;
    assert_eq!(geometry::raster(&data, [-4; 3], &mut short), 4);
}

#[test]
fn floor_matches_java_narrowing_and_wrapping() {
    for (value, expected) in [
        (f64::NAN, 0),
        (f64::INFINITY, i32::MAX),
        (f64::NEG_INFINITY, i32::MAX),
        (-0.0, 0),
        (-0.001, -1),
        (i32::MIN as f64, i32::MIN),
        (i32::MIN as f64 - 0.25, i32::MAX),
        (i32::MAX as f64 + 0.75, i32::MAX),
        (-2147483647.75, i32::MIN),
    ] {
        assert_eq!(geometry::floor(value), expected);
    }
}

#[test]
fn vector_paths_match_scalar_operations() {
    let mut state = 917318751u64;
    let mut next = || {
        state ^= state << 13;
        state ^= state >> 7;
        state ^= state << 17;
        (state >> 11) as f64 / (1u64 << 53) as f64
    };
    for trial in 0..4096 {
        let cx = next() * 20.0 - 10.0;
        let cy = next() * 20.0 - 10.0;
        let cz = next() * 20.0 - 10.0;
        let r = if trial % 7 == 0 { next() } else { next() * 7.0 };
        let spheres = [cx, cy, cz, r];
        let lower = [-12 + trial % 12, -12, -12];
        let mut reference = [0; 4096];
        let mut actual = [0; 4096];
        let a = geometry::raster_scalar(&spheres, lower, &mut reference);
        let b = geometry::raster(&spheres, lower, &mut actual);
        assert_eq!(a, b);
        assert_eq!(&reference[..a], &actual[..b]);
    }
    for count in 0..96 {
        let mut spheres = Vec::new();
        for _ in 0..count {
            spheres.extend([
                next() * 8.0,
                next() * 8.0,
                next() * 8.0,
                next() * 10.0 - 1.0,
            ]);
        }
        if count > 8 {
            spheres[7] = f64::NAN;
            spheres[11] = f64::INFINITY;
        }
        let mut reference = spheres.clone();
        geometry::prune_scalar(&mut reference);
        geometry::prune(&mut spheres);
        assert_eq!(
            reference.iter().map(|v| v.to_bits()).collect::<Vec<_>>(),
            spheres.iter().map(|v| v.to_bits()).collect::<Vec<_>>()
        );
    }
}

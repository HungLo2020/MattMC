use super::{evaluate::evaluate, ffi::mattmc_blending_heights};

#[test]
fn direct_missing_and_ordered_weights() {
    let mut output = [0.0; 6];
    assert!(evaluate(
        &[0, 0, 4, 0],
        &[64.0, 80.0],
        &[1, 0, 1, 0, 100, 100],
        &[64.0, f64::MAX, f64::MAX],
        &mut output
    ));
    assert_eq!(output[0].to_bits(), 0.0f64.to_bits());
    assert_eq!(output[4..], [1.0, 0.0]);
    let distance = 1.0f64 / 28.0;
    assert_eq!(
        output[2].to_bits(),
        (3.0 * distance * distance - 2.0 * distance * distance * distance).to_bits()
    );
}

#[test]
fn invalid_metadata_does_not_write() {
    let c = [0, 0];
    let h = [64.0];
    let q = [1, 1];
    let d = [f64::MAX];
    let mut out = [7.0; 2];
    unsafe {
        for count in [-1, 0, 4097] {
            assert_eq!(
                mattmc_blending_heights(
                    c.as_ptr(),
                    h.as_ptr(),
                    count,
                    q.as_ptr(),
                    d.as_ptr(),
                    1,
                    out.as_mut_ptr(),
                    2
                ),
                -1
            );
        }
        for queries in [-1, 0, 290] {
            assert_eq!(
                mattmc_blending_heights(
                    c.as_ptr(),
                    h.as_ptr(),
                    1,
                    q.as_ptr(),
                    d.as_ptr(),
                    queries,
                    out.as_mut_ptr(),
                    2
                ),
                -1
            );
        }
        assert_eq!(
            mattmc_blending_heights(
                std::ptr::null(),
                h.as_ptr(),
                1,
                q.as_ptr(),
                d.as_ptr(),
                1,
                out.as_mut_ptr(),
                2
            ),
            -1
        );
        assert_eq!(
            mattmc_blending_heights(
                c.as_ptr(),
                h.as_ptr(),
                1,
                q.as_ptr(),
                d.as_ptr(),
                1,
                out.as_mut_ptr(),
                1
            ),
            -1
        );
        assert_eq!(
            mattmc_blending_heights(
                c.as_ptr(),
                [f64::NAN].as_ptr(),
                1,
                q.as_ptr(),
                d.as_ptr(),
                1,
                out.as_mut_ptr(),
                2
            ),
            -1
        );
    }
    assert_eq!(out, [7.0; 2]);
}

#[test]
fn ffi_matches_scalar_across_sizes_and_wrapped_coordinates() {
    let coordinates: Vec<i32> = (0..128)
        .flat_map(|i| [i * 4 - 256, i % 16 * 4 - 32])
        .collect();
    let heights: Vec<f64> = (0..128).map(|i| i as f64 + 0.25).collect();
    for size in [1, 25, 81, 289] {
        let queries: Vec<i32> = (0..size)
            .flat_map(|i| [if i % 2 == 0 { i32::MAX } else { i - 128 }, i % 16 - 8])
            .collect();
        let direct = vec![f64::MAX; size as usize];
        let mut expected = vec![0.0; size as usize * 2];
        let mut actual = expected.clone();
        assert!(evaluate(
            &coordinates,
            &heights,
            &queries,
            &direct,
            &mut expected
        ));
        unsafe {
            assert_eq!(
                mattmc_blending_heights(
                    coordinates.as_ptr(),
                    heights.as_ptr(),
                    128,
                    queries.as_ptr(),
                    direct.as_ptr(),
                    size,
                    actual.as_mut_ptr(),
                    size * 2
                ),
                0
            );
        }
        assert_eq!(
            expected.iter().map(|x| x.to_bits()).collect::<Vec<_>>(),
            actual.iter().map(|x| x.to_bits()).collect::<Vec<_>>()
        );
    }
}

#[test]
fn nonfinite_result_requests_compatibility() {
    let mut out = [0.0; 2];
    // A coincident sample with no direct value gives Infinity/Infinity, as Java.
    assert!(!evaluate(&[0, 0], &[64.0], &[0, 0], &[f64::MAX], &mut out));
}

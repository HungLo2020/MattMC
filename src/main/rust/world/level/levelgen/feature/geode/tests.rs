use super::super::super::synth::State;
use super::evaluate::Point;
use super::ffi::mattmc_geode_fields;

fn state() -> State {
    State {
        kind: 3,
        n1: 0,
        n2: 0,
        n3: 0,
        params: [1.0; 8],
    }
}

#[test]
fn layout_and_ordered_density_grid() {
    assert_eq!(std::mem::size_of::<Point>(), 16);
    let s = state();
    let points = [
        Point {
            x: -2,
            y: 3,
            z: 5,
            offset: 1,
        },
        Point {
            x: 6,
            y: -4,
            z: 0,
            offset: 0,
        },
        Point {
            x: 0,
            y: 2,
            z: 7,
            offset: 2,
        },
    ];
    let frame = [-4, -3, -2, 9, 7, 5];
    let mut output = vec![0.0; 9 * 7 * 5 * 2];
    assert_eq!(
        unsafe {
            mattmc_geode_fields(
                &s,
                80,
                frame.as_ptr(),
                6,
                points.as_ptr(),
                2,
                1,
                0.05,
                output.as_mut_ptr(),
                output.len() as i32,
            )
        },
        0
    );
    let mut index = 0;
    for z in -2..3 {
        for y in -3..4 {
            for x in -4..5 {
                for group in [&points[..2], &points[2..]] {
                    let mut sum: f64 = 0.0;
                    for p in group {
                        let a = x as f64 - p.x as f64;
                        let b = y as f64 - p.y as f64;
                        let c = z as f64 - p.z as f64;
                        sum += 1.0 / (a * a + b * b + c * c + p.offset as f64).sqrt();
                    }
                    assert_eq!(output[index].to_bits(), sum.to_bits());
                    index += 1;
                }
            }
        }
    }
}

#[test]
fn extremes_and_empty_groups() {
    let s = state();
    let p = [Point {
        x: i32::MIN,
        y: i32::MAX,
        z: 0,
        offset: 10,
    }];
    let f = [i32::MAX - 1, i32::MIN, 0, 2, 1, 1];
    let mut out = [0.0; 4];
    assert_eq!(
        unsafe {
            mattmc_geode_fields(
                &s,
                80,
                f.as_ptr(),
                6,
                p.as_ptr(),
                1,
                0,
                0.0,
                out.as_mut_ptr(),
                4,
            )
        },
        0
    );
    for i in 0..2 {
        let dx = (i32::MAX as i64 - 1 + i as i64) as f64 - i32::MIN as f64;
        let dy = i32::MIN as f64 - i32::MAX as f64;
        assert_eq!(
            out[i * 2].to_bits(),
            (1.0 / (dx * dx + dy * dy + 10.0).sqrt()).to_bits()
        );
        assert_eq!(out[i * 2 + 1].to_bits(), 0.0f64.to_bits());
    }
}

#[test]
fn rejects_metadata_before_output_writes() {
    let s = state();
    let p = [Point {
        x: 0,
        y: 0,
        z: 0,
        offset: 1,
    }];
    let mut out = [123.0; 2];
    for f in [
        [0, 0, 0, 0, 1, 1],
        [0, 0, 0, 65, 1, 1],
        [0, 0, 0, 64, 64, 64],
        [i32::MAX, 0, 0, 2, 1, 1],
    ] {
        assert_eq!(
            unsafe {
                mattmc_geode_fields(
                    &s,
                    80,
                    f.as_ptr(),
                    6,
                    p.as_ptr(),
                    1,
                    0,
                    0.0,
                    out.as_mut_ptr(),
                    2,
                )
            },
            -1
        );
        assert_eq!(out, [123.0; 2]);
    }
    let f = [0, 0, 0, 1, 1, 1];
    for (bytes, count, cracks, length, expected) in [
        (79, 1, 0, 2, -2),
        (81, 1, 0, 2, -2),
        (80, -1, 0, 2, -1),
        (80, 65, 0, 2, -1),
        (80, 1, -1, 2, -1),
        (80, 1, 65, 2, -1),
        (80, 1, 0, 1, -1),
    ] {
        assert_eq!(
            unsafe {
                mattmc_geode_fields(
                    &s,
                    bytes,
                    f.as_ptr(),
                    6,
                    p.as_ptr(),
                    count,
                    cracks,
                    0.0,
                    out.as_mut_ptr(),
                    length,
                )
            },
            expected
        );
        assert_eq!(out, [123.0; 2]);
    }
    let invalid = State { kind: 0, ..state() };
    assert_eq!(
        unsafe {
            mattmc_geode_fields(
                &invalid,
                80,
                f.as_ptr(),
                6,
                p.as_ptr(),
                1,
                0,
                0.0,
                out.as_mut_ptr(),
                2,
            )
        },
        -2
    );
    assert_eq!(out, [123.0; 2]);
    assert_eq!(
        unsafe {
            mattmc_geode_fields(
                std::ptr::null(),
                0,
                f.as_ptr(),
                6,
                p.as_ptr(),
                1,
                0,
                0.0,
                out.as_mut_ptr(),
                2,
            )
        },
        -1
    );
}

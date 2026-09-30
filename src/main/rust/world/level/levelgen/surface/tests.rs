use super::{evaluator::surface_step, program::surface_validate};
#[test]
fn rejects_invalid_programs() {
    let mut p = vec![
        32, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 24, 0, 0, 1, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0,
    ];
    unsafe {
        assert_eq!(surface_validate(p.as_ptr(), p.len() as i32), 0);
        p[13] = 8;
        assert_ne!(surface_validate(p.as_ptr(), p.len() as i32), 0);
        p[13] = 24;
        p[8] = 7;
        p[9] = 31;
        p[10] = 2;
        assert_ne!(surface_validate(p.as_ptr(), p.len() as i32), 0);
        assert_ne!(surface_validate(std::ptr::null(), 32), 0);
        assert_ne!(surface_validate(p.as_ptr(), 8), 0);
    }
}
#[test]
fn lazy_requests_preserve_block_order_and_xz_cache() {
    for xz in [0, 1] {
        let p = [
            32, 0, 0, 0, 0, 0, 0, 0, 2, 0, xz, 0, 0, 24, 0, 0, 1, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0,
        ];
        let flags = [3; 129];
        let mut out = [-1; 129];
        let biomes = [0; 129];
        let mut f = [0; 24];
        let mut cache = [-1; 4];
        f[0] = 128;
        f[2] = i32::MIN;
        f[3] = i32::MAX;
        f[12] = -64;
        let mut requests = 0;
        loop {
            let status = unsafe {
                surface_step(
                    p.as_ptr(),
                    p.len() as i32,
                    flags.as_ptr(),
                    out.as_mut_ptr(),
                    biomes.as_ptr(),
                    129,
                    f.as_mut_ptr(),
                    cache.as_mut_ptr(),
                    0.,
                )
            };
            if status == 1 {
                break;
            }
            assert_eq!(status, 2);
            assert_eq!(out[f[0] as usize], -1);
            for y in f[0] as usize + 1..out.len() {
                assert_eq!(out[y], 7);
            }
            assert_eq!(f[1], 129 - f[0]);
            assert_eq!(f[10], f[0] + 1);
            requests += 1;
            f[7] = 1;
            f[8] = 1;
        }
        assert!(out.iter().all(|v| *v == 7));
        assert_eq!(requests, if xz == 0 { 129 } else { 1 });
    }
}
#[test]
fn scan_resets_air_and_tracks_water_and_nondefault_stone() {
    // Top-down: other stone, fluid, default, air, default, default.
    let flags = [3, 3, 0, 3, 1, 2];
    let mut out = [-1; 6];
    let biomes = [0; 6];
    let p = [
        32, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 24, 0, 0, 1, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0,
    ];
    let mut f = [0; 24];
    let mut cache = [-1; 4];
    f[0] = 5;
    f[2] = i32::MIN;
    f[3] = i32::MAX;
    f[12] = -10;
    let mut trace = Vec::new();
    loop {
        let status = unsafe {
            surface_step(
                p.as_ptr(),
                32,
                flags.as_ptr(),
                out.as_mut_ptr(),
                biomes.as_ptr(),
                6,
                f.as_mut_ptr(),
                cache.as_mut_ptr(),
                0.,
            )
        };
        if status == 1 {
            break;
        }
        assert_eq!(status, 2);
        trace.push((f[0], f[1], f[10], f[2]));
        f[7] = 1;
        f[8] = 1;
    }
    assert_eq!(
        trace,
        vec![(3, 2, 1, -5), (1, 1, 2, i32::MIN), (0, 2, 1, i32::MIN)]
    );
}

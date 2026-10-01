use super::{decision, ffi, nearest};

#[test]
fn ties_choose_last_visited_and_integer_distances_wrap() {
    let centers: Vec<_> = (0..12).map(|i| [i, 0, 0, 0]).collect();
    let mut out = [0; 8];
    nearest::point(0, 0, 0, &centers, &mut out);
    assert_eq!(out, [11, 10, 9, 8, 0, 0, 0, 0]);
    let mut seed = 819234u64;
    let mut random = || {
        seed = seed.wrapping_mul(6364136223846793005).wrapping_add(1);
        (seed >> 32) as i32
    };
    for _ in 0..20_000 {
        let p = [random(), random(), random()];
        let centers: Vec<_> = (0..12).map(|i| [i, random(), random(), random()]).collect();
        nearest::point(p[0], p[1], p[2], &centers, &mut out);
        let mut expected: Vec<_> = centers
            .iter()
            .map(|&[id, x, y, z]| {
                let dx = x.wrapping_sub(p[0]);
                let dy = y.wrapping_sub(p[1]);
                let dz = z.wrapping_sub(p[2]);
                (
                    dx.wrapping_mul(dx)
                        .wrapping_add(dy.wrapping_mul(dy))
                        .wrapping_add(dz.wrapping_mul(dz)),
                    -id,
                )
            })
            .collect();
        expected.sort();
        let fixed: &[[i32; 4]; 12] = centers.as_slice().try_into().unwrap();
        let mut vector = [0; 8];
        nearest::Prepared::new(fixed).point(p[0], p[1], p[2], &mut vector);
        assert_eq!(vector, out);
        for i in 0..4 {
            assert_eq!(out[i], -expected[i].1);
            assert_eq!(out[i + 4], expected[i].0);
        }
    }
}

fn packed(x: i32, y: i32, z: i32) -> i64 {
    ((x as i64 & 0x3ffffff) << 38) | ((z as i64 & 0x3ffffff) << 12) | (y as i64 & 0xfff)
}

#[test]
fn cell_matches_scalar_across_negative_grid_edges_and_batch_sizes() {
    for x in [-29999984, -32, -16, 0, 16, 29999968] {
        for z in [-16, 0, 16] {
            for y in [-64, -13, -1, 0, 11, 24, 304] {
                for (w, h) in [(1, 1), (4, 8), (8, 4), (16, 8)] {
                    let minx = (x - 5) >> 4;
                    let miny = (y + 1_i32).div_euclid(12) - 1;
                    let minz = (z - 5) >> 4;
                    let sx = (((x + w - 6) >> 4) + 1) - minx + 1;
                    let sy = (y + h).div_euclid(12) + 1 - miny + 1;
                    let sz = (((z + w - 6) >> 4) + 1) - minz + 1;
                    let mut grid = Vec::new();
                    for iy in 0..sy {
                        for iz in 0..sz {
                            for ix in 0..sx {
                                grid.push(packed(
                                    (minx + ix) * 16 + ((iy + iz) % 10),
                                    (miny + iy) * 12 + ((ix + iz) % 9),
                                    (minz + iz) * 16 + ((ix + iy) % 10),
                                ));
                            }
                        }
                    }
                    let shape = [minx, miny, minz, sx, sz];
                    let mut output = vec![0; (w * w * h * 8) as usize];
                    assert_eq!(
                        unsafe {
                            ffi::mattmc_aquifer_cell(
                                x,
                                y,
                                z,
                                w,
                                h,
                                grid.as_ptr(),
                                grid.len() as i32,
                                shape.as_ptr(),
                                output.as_mut_ptr(),
                            )
                        },
                        0
                    );
                    for iy in 0..h {
                        for ix in 0..w {
                            for iz in 0..w {
                                let px = x + ix;
                                let py = y + iy;
                                let pz = z + iz;
                                let mut centers = Vec::new();
                                for dx in 0..2 {
                                    for dy in -1..2 {
                                        for dz in 0..2 {
                                            let gx = ((px - 5) >> 4) + dx - minx;
                                            let gy = (py + 1).div_euclid(12) + dy - miny;
                                            let gz = ((pz - 5) >> 4) + dz - minz;
                                            let index = (gy * sz + gz) * sx + gx;
                                            let pos = grid[index as usize];
                                            centers.push([
                                                index,
                                                (pos >> 38) as i32,
                                                ((pos << 52) >> 52) as i32,
                                                ((pos << 26) >> 38) as i32,
                                            ]);
                                        }
                                    }
                                }
                                let mut expected = [0; 8];
                                nearest::point(px, py, pz, &centers, &mut expected);
                                let index = ((iy * w + ix) * w + iz) as usize * 8;
                                assert_eq!(output[index..index + 8], expected);
                            }
                        }
                    }
                }
            }
        }
    }
}

#[test]
fn decision_requests_only_needed_sources_and_consumes_nan_once_per_pressure() {
    let mut frame = [0; 16];
    frame[..8].copy_from_slice(&[0, 1, 2, 3, 0, 0, 0, 0]);
    frame[10] = 5;
    frame[11] = 0;
    frame[14] = 0;
    let mut values = [-100.0, f64::NAN];
    let mut cache = [8, -1, 1, 10, -1, 1, 12, -1, 1, 14, -1, 1];
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 1);
    assert_eq!(frame[9], 0);
    cache[1] = 1;
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 1);
    assert_eq!(frame[9], 1);
    cache[4] = 1;
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 3);
    frame[15] = 1;
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 1);
    assert_eq!(frame[9], 2);
    cache[7] = 1;
    // All three pressure calls need a barrier value; a NaN must not become a
    // permanent cached answer, nor cause an endless request at the same phase.
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 3);
    frame[15] = 1;
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 3);
    frame[15] = 1;
    assert_eq!(decision::step(&mut frame, &mut values, &cache, false), 0);
    assert_eq!(frame[12], 1);
    assert_eq!(frame[13], 1);
    assert_eq!(cache[10], -1);
}

#[test]
fn malformed_ffi_inputs_fail_before_dereferencing() {
    unsafe {
        assert_eq!(
            ffi::mattmc_aquifer_nearest(0, 0, 0, std::ptr::null(), std::ptr::null_mut()),
            -1
        );
        assert_eq!(
            ffi::mattmc_aquifer_step(
                std::ptr::null_mut(),
                std::ptr::null_mut(),
                std::ptr::null(),
                1
            ),
            -1
        );
        assert_eq!(ffi::mattmc_aquifer_fluid(std::ptr::null_mut(), 0.0), -1);
        assert_eq!(
            ffi::mattmc_aquifer_cell(
                0,
                0,
                0,
                17,
                8,
                std::ptr::null(),
                1,
                std::ptr::null(),
                std::ptr::null_mut()
            ),
            -1
        );
    }
    let mut f = [0; 16];
    let mut d = [0.0; 2];
    f[0] = 4;
    assert_eq!(decision::step(&mut f, &mut d, &[0; 12], false), -2);
}

#[test]
fn pure_fluid_cache_matches_ordered_source_path() {
    use super::fluid;
    fn run(
        pure: bool,
        x: i32,
        y: i32,
        z: i32,
        policy: &[i32],
        cache: &mut [i32],
        variant: i32,
    ) -> ([i32; 2], Vec<(i32, i32, i32, i32)>) {
        let mut f = [0; 26];
        f[1] = x;
        f[2] = y;
        f[3] = z;
        f[20] = 2;
        f[21] = -32512;
        f[22] = -20;
        f[23] = -20;
        f[24] = 40;
        f[25] = 40;
        let mut answer = 0.0;
        let mut trace = Vec::new();
        for _ in 0..100 {
            let request = if pure {
                fluid::pure_step(&mut f, answer, policy, cache)
            } else {
                fluid::step(&mut f, answer)
            };
            match request {
                0 => return ([f[12], f[13]], trace),
                1 => {
                    let y = f[15];
                    let (level, id, air) = if policy[5] != 0 {
                        (policy[6], policy[7], true)
                    } else if y < policy[0].min(policy[2]) {
                        (policy[0], policy[1], false)
                    } else {
                        (policy[2], policy[3], policy[4] != 0)
                    };
                    f[17] = level;
                    f[18] = id;
                    f[19] = (air || y >= level) as i32;
                }
                2 => {
                    f[17] =
                        ((f[14] >> 2) * 7 + (f[16] >> 2) * 13 + variant * 17).rem_euclid(400) - 70;
                    if pure {
                        let index = (((f[16] >> 2) + 20) * 40 + (f[14] >> 2) + 20) as usize * 2;
                        cache[index] = f[17];
                        cache[index + 1] = 1;
                    }
                }
                3..=7 => {
                    trace.push((request, f[14], f[15], f[16]));
                    let h = f[14] * 31 + f[15] * 79 + f[16] * 163 + variant;
                    answer = if variant == 0 {
                        f64::NAN
                    } else {
                        h.rem_euclid(1000) as f64 / 200.0 - 2.0
                    };
                }
                _ => panic!("bad fluid request {request}"),
            }
        }
        panic!("fluid evaluator failed to finish");
    }
    for variant in 0..8 {
        for sea in [-100, -54, 0, 63, 320] {
            for disabled in [0, 1] {
                let policy = [-54, 2, sea, 1, variant % 2, disabled, -4064, 0];
                let mut cache = vec![0; 40 * 40 * 2];
                for x in [-16, 0, 16] {
                    for z in [-16, 0, 16] {
                        for y in (-64..320).step_by(3) {
                            let expected = run(false, x, y, z, &policy, &mut [], variant);
                            assert_eq!(run(true, x, y, z, &policy, &mut cache, variant), expected);
                        }
                    }
                }
            }
        }
    }
}

#[test]
fn material_cells_match_scalar_decisions_with_yields_and_lazy_cache_misses() {
    use super::cell;
    for (width, height) in [(1, 1), (4, 8), (8, 8), (16, 8)] {
        for base_y in [-64, -12, 0, 56, 120] {
            for debug in [0, 1] {
                let shape = [-2, -8, -2, 5, 5];
                let mut grid = Vec::new();
                let mut statuses = Vec::new();
                for gy in -8..16 {
                    for gz in -2..3 {
                        for gx in -2..3 {
                            grid.push(packed(gx * 16 + 3, gy * 12 + 4, gz * 16 + 7));
                            let id = if (gx + gy + gz) % 4 == 0 { 2 } else { 1 };
                            statuses.extend([gy * 12 + 7, id, id]);
                        }
                    }
                }
                let total = (width * width * height) as usize;
                let density: Vec<_> = (0..total)
                    .map(|i| match i % 9 {
                        0 => f64::NAN,
                        1 => 0.0,
                        2 => -0.0,
                        3 => 1.0,
                        _ => -0.1 - (i % 20) as f64 / 10.0,
                    })
                    .collect();
                let mut output = vec![0; total * 2];
                let mut f = [0; 32];
                let mut values = [0.0; 2];
                f[18] = 0;
                f[19] = base_y;
                f[20] = 0;
                f[21] = width;
                f[22] = height;
                f[23] = 110;
                f[24] = -54;
                f[25] = -54;
                f[26] = 2;
                f[27] = 63;
                f[28] = 1;
                f[29] = 1;
                f[30] = debug;
                f[31] = -4064;
                let mut cache = vec![0; statuses.len()];
                for i in (1..cache.len()).step_by(3) {
                    cache[i] = -1;
                }
                loop {
                    match unsafe {
                        cell::materials(
                            &mut f,
                            &mut values,
                            &density,
                            &mut output,
                            &grid,
                            &shape,
                            &cache,
                            std::ptr::null(),
                            1.0,
                            1.0,
                        )
                    } {
                        0 => break,
                        1 => {
                            let i = f[9] as usize * 3;
                            cache[i..i + 3].copy_from_slice(&statuses[i..i + 3]);
                        }
                        4 => {}
                        code => panic!("bad cell result {code}"),
                    }
                }
                for i in 0..total {
                    let x = (i as i32 / width) % width;
                    let y = base_y + height - 1 - i as i32 / (width * width);
                    let z = i as i32 % width;
                    let expected = if density[i] > 0.0 {
                        [-1, 0]
                    } else if y > 110 {
                        [if debug != 0 || y >= 63 { 0 } else { 1 }, 0]
                    } else if debug == 0 && y < -54 {
                        [2, 0]
                    } else {
                        let mut centers = [[0; 4]; 12];
                        assert!(cell::centers(x, y, z, &grid, &shape, &mut centers));
                        let mut sf = [0; 20];
                        nearest::point(x, y, z, &centers, &mut sf[..8]);
                        sf[10] = y;
                        sf[11] = (debug == 0 && y - 1 < -54) as i32;
                        let mut v = [density[i], f64::NAN];
                        loop {
                            let r = decision::step(&mut sf, &mut v, &statuses, false);
                            if r == 0 {
                                break;
                            }
                            assert_eq!(r, 3);
                            v[1] = 0.0;
                            sf[15] = 1;
                        }
                        [
                            if debug != 0 && sf[12] != -1 {
                                0
                            } else {
                                sf[12]
                            },
                            sf[13],
                        ]
                    };
                    assert_eq!(
                        output[i * 2..i * 2 + 2],
                        expected,
                        "cell {width}x{height} y={base_y}, sample={i}"
                    );
                }
            }
        }
    }
}

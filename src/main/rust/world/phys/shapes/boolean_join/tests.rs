use super::{evaluate::join, ffi::mattmc_voxel_join};
fn occupied(words: &[u64], dims: &[i32], x: i32, y: i32, z: i32) -> bool {
    if x < 0 || y < 0 || z < 0 || x >= dims[0] || y >= dims[1] || z >= dims[2] {
        return false;
    }
    let at = ((x * dims[1] + y) * dims[2] + z) as usize;
    words.get(at / 64).copied().unwrap_or(0) & (1 << (at % 64)) != 0
}
#[test]
fn every_truth_table_identical_and_indirect_maps_bounds_and_padding() {
    let mut state = 1977u64;
    for dims in [[8, 8, 8], [17, 9, 7], [1, 1, 65], [16, 4, 32]] {
        for map_kind in 0..3 {
            let indirect = map_kind != 0;
            let sizes: Vec<i32> = if indirect {
                vec![7, 6, 5, 9, 4, 8]
            } else {
                dims.iter().chain(dims.iter()).map(|&x| x as i32).collect()
            };
            let mut a = vec![0; ((sizes[0] * sizes[1] * sizes[2] + 63) / 64) as usize];
            let mut b = vec![0; ((sizes[3] * sizes[4] * sizes[5] + 63) / 64) as usize];
            for w in a.iter_mut().chain(b.iter_mut()) {
                state = state.wrapping_mul(6364136223846793005).wrapping_add(1);
                *w = state;
            }
            for pattern in 0..4 {
                if pattern == 1 {
                    a.fill(0);
                    b.fill(0);
                }
                if pattern == 2 {
                    a.fill(u64::MAX);
                    b.fill(u64::MAX);
                }
                if pattern == 3 {
                    a.fill(u64::MAX);
                    b.fill(0);
                }
                let sizes_ref = &sizes;
                let mut maps: Vec<i32> = dims
                    .iter()
                    .enumerate()
                    .flat_map(|(axis, &n)| {
                        (0..n).flat_map(move |k| {
                            if indirect {
                                [
                                    k as i32 % (sizes_ref[axis] + 2) - 1,
                                    k as i32 % (sizes_ref[axis + 3] + 2) - 1,
                                ]
                            } else {
                                [k as i32, k as i32]
                            }
                        })
                    })
                    .collect();
                if map_kind == 2 {
                    for z in 0..dims[2] {
                        let offset = (dims[0] + dims[1]) * 2 + z * 2;
                        maps[offset] = z as i32;
                        maps[offset + 1] = z as i32;
                    }
                }
                for truth in 0..16 {
                    let count = dims.iter().product::<usize>();
                    let mut out = vec![u64::MAX; (count + 63) / 64];
                    let mut bounds = [0; 6];
                    join(&a, &b, &sizes, &maps, dims, truth, &mut out, &mut bounds);
                    let mut expected = vec![0; out.len()];
                    let mut limits = [i32::MAX, i32::MAX, i32::MAX, i32::MIN, i32::MIN, i32::MIN];
                    for x in 0..dims[0] {
                        for y in 0..dims[1] {
                            for z in 0..dims[2] {
                                let ym = dims[0] * 2 + y * 2;
                                let zm = (dims[0] + dims[1]) * 2 + z * 2;
                                let av = occupied(&a, &sizes[..3], maps[x * 2], maps[ym], maps[zm]);
                                let bv = occupied(
                                    &b,
                                    &sizes[3..],
                                    maps[x * 2 + 1],
                                    maps[ym + 1],
                                    maps[zm + 1],
                                );
                                if truth & (1 << ((av as u32) * 2 + bv as u32)) != 0 {
                                    let at = (x * dims[1] + y) * dims[2] + z;
                                    expected[at / 64] |= 1 << (at % 64);
                                    for (axis, value) in [x, y, z].iter().enumerate() {
                                        limits[axis] = limits[axis].min(*value as i32);
                                        limits[axis + 3] = limits[axis + 3].max(*value as i32);
                                    }
                                }
                            }
                        }
                    }
                    for n in &mut limits[3..] {
                        *n += 1;
                    }
                    assert_eq!(out, expected);
                    assert_eq!(bounds, limits);
                    let mut ffi = vec![0; out.len()];
                    let mut ffi_bounds = [0; 6];
                    assert_eq!(
                        unsafe {
                            mattmc_voxel_join(
                                a.as_ptr(),
                                a.len() as i32,
                                b.as_ptr(),
                                b.len() as i32,
                                sizes.as_ptr(),
                                maps.as_ptr(),
                                dims[0] as i32,
                                dims[1] as i32,
                                dims[2] as i32,
                                truth as i32,
                                ffi.as_mut_ptr(),
                                ffi.len() as i32,
                                ffi_bounds.as_mut_ptr(),
                            )
                        },
                        0
                    );
                    assert_eq!(ffi, out);
                    assert_eq!(ffi_bounds, bounds);
                }
            }
        }
    }
}
#[test]
fn ffi_metadata_rejection_leaves_output_untouched() {
    let a = [0u64; 8];
    let sizes = [8i32; 6];
    let maps = [0i32; 48];
    let mut out = [77u64; 8];
    let mut bounds = [77i32; 6];
    for (nx, truth, len) in [(0, 14, 8), (257, 14, 8), (8, 16, 8), (8, 14, 7)] {
        assert_eq!(
            unsafe {
                mattmc_voxel_join(
                    a.as_ptr(),
                    8,
                    a.as_ptr(),
                    8,
                    sizes.as_ptr(),
                    maps.as_ptr(),
                    nx,
                    8,
                    8,
                    truth,
                    out.as_mut_ptr(),
                    len,
                    bounds.as_mut_ptr(),
                )
            },
            -1
        );
        assert_eq!(out, [77; 8]);
        assert_eq!(bounds, [77; 6]);
    }
    assert_eq!(
        unsafe {
            mattmc_voxel_join(
                std::ptr::null(),
                8,
                a.as_ptr(),
                8,
                sizes.as_ptr(),
                maps.as_ptr(),
                8,
                8,
                8,
                14,
                out.as_mut_ptr(),
                8,
                bounds.as_mut_ptr(),
            )
        },
        -1
    );
}

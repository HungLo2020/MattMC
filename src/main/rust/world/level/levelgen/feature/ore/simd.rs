//! AVX2 lanes keep each scalar operation and comparison in its original order.
//! No FMA or reciprocal approximations. Dispatch requires AVX2 support.
use std::arch::x86_64::*;

#[target_feature(enable = "avx2")]
pub(super) unsafe fn tiny(s: [f64; 4], lower: [i32; 3], output: &mut [i32], count: &mut usize) {
    let [cx, cy, cz, r] = s;
    let ix = floor_bounded(cx);
    let iy = floor_bounded(cy);
    let iz = floor_bounded(cz);
    let center = _mm256_setr_pd(cx, cy, cz, 0.0);
    let radius = _mm256_set1_pd(r);
    let closest = _mm256_setr_pd(ix as f64 + 0.5, iy as f64 + 0.5, iz as f64 + 0.5, 0.0);
    let delta = _mm256_div_pd(_mm256_sub_pd(closest, center), radius);
    let mut near = [0.0; 4];
    _mm256_storeu_pd(near.as_mut_ptr(), _mm256_mul_pd(delta, delta));
    // This is the closest lattice point on every axis. With monotone rounded
    // additions, if it fails the exact predicate every other point fails too.
    if !(near[0] + near[1] + near[2] < 1.0) {
        return;
    }
    let nx = if cx < ix as f64 + 0.5 { ix - 1 } else { ix + 1 };
    let ny = if cy < iy as f64 + 0.5 { iy - 1 } else { iy + 1 };
    let nz = if cz < iz as f64 + 0.5 { iz - 1 } else { iz + 1 };
    let neighbor = _mm256_setr_pd(nx as f64 + 0.5, ny as f64 + 0.5, nz as f64 + 0.5, 0.0);
    let delta = _mm256_div_pd(_mm256_sub_pd(neighbor, center), radius);
    let mut far = [0.0; 4];
    _mm256_storeu_pd(far.as_mut_ptr(), _mm256_mul_pd(delta, delta));
    let (x0, xx) = if nx < ix {
        (nx, [far[0], near[0]])
    } else {
        (ix, [near[0], far[0]])
    };
    let (y0, yy) = if ny < iy {
        (ny, [far[1], near[1]])
    } else {
        (iy, [near[1], far[1]])
    };
    let (z0, zz) = if nz < iz {
        (nz, [far[2], near[2]])
    } else {
        (iz, [near[2], far[2]])
    };
    let y2 = _mm256_setr_pd(yy[0], yy[0], yy[1], yy[1]);
    let z2 = _mm256_setr_pd(zz[0], zz[1], zz[0], zz[1]);
    let mut allowed = 15;
    if y0 < lower[1] {
        allowed &= !3;
    }
    if y0 + 1 < lower[1] {
        allowed &= !12;
    }
    if z0 < lower[2] {
        allowed &= !5;
    }
    if z0 + 1 < lower[2] {
        allowed &= !10;
    }
    for (offset, x2) in xx.into_iter().enumerate() {
        let x = x0 + offset as i32;
        if x < lower[0] {
            continue;
        }
        let sum = _mm256_add_pd(_mm256_add_pd(_mm256_set1_pd(x2), y2), z2);
        let mask =
            _mm256_movemask_pd(_mm256_cmp_pd::<_CMP_LT_OQ>(sum, _mm256_set1_pd(1.0))) & allowed;
        for row in 0..2 {
            let bits = (mask >> (row * 2)) & 3;
            if bits != 0 {
                let first = if bits & 1 != 0 { z0 } else { z0 + 1 };
                let last = if bits & 2 != 0 { z0 + 1 } else { z0 };
                super::geometry::emit(output, count, [x, y0 + row, first, last]);
            }
        }
    }
}

#[target_feature(enable = "avx2")]
pub(super) unsafe fn prune(spheres: &mut [f64]) {
    let count = spheres.len() / 4;
    for q in 0..count.saturating_sub(1) {
        if !(spheres[q * 4 + 3] > 0.0) {
            continue;
        }
        let cx = _mm256_set1_pd(spheres[q * 4]);
        let cy = _mm256_set1_pd(spheres[q * 4 + 1]);
        let cz = _mm256_set1_pd(spheres[q * 4 + 2]);
        let cr = _mm256_set1_pd(spheres[q * 4 + 3]);
        let mut x = q + 1;
        let mut dead = false;
        while x + 4 <= count {
            // Four AoS records -> four vectors of their respective fields.
            let p = spheres.as_ptr().add(x * 4);
            let a = _mm256_loadu_pd(p);
            let b = _mm256_loadu_pd(p.add(4));
            let c = _mm256_loadu_pd(p.add(8));
            let d = _mm256_loadu_pd(p.add(12));
            let ab0 = _mm256_unpacklo_pd(a, b);
            let ab1 = _mm256_unpackhi_pd(a, b);
            let cd0 = _mm256_unpacklo_pd(c, d);
            let cd1 = _mm256_unpackhi_pd(c, d);
            let xx = _mm256_permute2f128_pd::<0x20>(ab0, cd0);
            let yy = _mm256_permute2f128_pd::<0x20>(ab1, cd1);
            let zz = _mm256_permute2f128_pd::<0x31>(ab0, cd0);
            let rr = _mm256_permute2f128_pd::<0x31>(ab1, cd1);
            let dx = _mm256_sub_pd(cx, xx);
            let dy = _mm256_sub_pd(cy, yy);
            let dz = _mm256_sub_pd(cz, zz);
            let dr = _mm256_sub_pd(cr, rr);
            let distance = _mm256_add_pd(
                _mm256_add_pd(_mm256_mul_pd(dx, dx), _mm256_mul_pd(dy, dy)),
                _mm256_mul_pd(dz, dz),
            );
            let inside = _mm256_cmp_pd::<_CMP_GT_OQ>(_mm256_mul_pd(dr, dr), distance);
            let active = _mm256_cmp_pd::<_CMP_GT_OQ>(rr, _mm256_setzero_pd());
            let mask = _mm256_movemask_pd(_mm256_and_pd(inside, active));
            let larger = _mm256_movemask_pd(_mm256_cmp_pd::<_CMP_GT_OQ>(dr, _mm256_setzero_pd()));
            for lane in 0..4 {
                if mask & (1 << lane) != 0 {
                    if larger & (1 << lane) != 0 {
                        spheres[(x + lane) * 4 + 3] = -1.0;
                    } else {
                        spheres[q * 4 + 3] = -1.0;
                        dead = true;
                        break;
                    }
                }
            }
            if dead {
                break;
            }
            x += 4;
        }
        if dead {
            continue;
        }
        for other in x..count {
            if spheres[other * 4 + 3] <= 0.0 {
                continue;
            }
            let dx = spheres[q * 4] - spheres[other * 4];
            let dy = spheres[q * 4 + 1] - spheres[other * 4 + 1];
            let dz = spheres[q * 4 + 2] - spheres[other * 4 + 2];
            let dr = spheres[q * 4 + 3] - spheres[other * 4 + 3];
            if dr * dr > dx * dx + dy * dy + dz * dz {
                if dr > 0.0 {
                    spheres[other * 4 + 3] = -1.0;
                } else {
                    spheres[q * 4 + 3] = -1.0;
                    break;
                }
            }
        }
    }
}

/// Dispatch guarantees finite centers within +/-1e9 and 1 < radius <= 7.
#[target_feature(enable = "avx2")]
pub(super) unsafe fn spans(s: [f64; 4], lower: [i32; 3], output: &mut [i32], count: &mut usize) {
    use super::geometry::emit;
    let [cx, cy, cz, r] = s;
    let x0 = floor_bounded(cx - r).max(lower[0]);
    let x1 = floor_bounded(cx + r).max(x0);
    let y0 = floor_bounded(cy - r).max(lower[1]);
    let y1 = floor_bounded(cy + r).max(y0);
    let z0 = floor_bounded(cz - r).max(lower[2]);
    let z1 = floor_bounded(cz + r).max(z0);
    let ny = (y1 as i64 - y0 as i64 + 1) as usize;
    let nz = (z1 as i64 - z0 as i64 + 1) as usize;
    // The bounded radius makes these sizes <= 16, including clamped lower bounds.
    debug_assert!(ny <= 16 && nz <= 16);
    let rr = _mm256_set1_pd(r);
    let center_y = _mm256_set1_pd(cy);
    let center_z = _mm256_set1_pd(cz);
    let mut yy = [0.0; 16];
    let mut zz = [_mm256_setzero_pd(); 4];
    for at in (0..ny).step_by(4) {
        let base = y0 as f64 + at as f64;
        let value = _mm256_setr_pd(base + 0.5, base + 1.5, base + 2.5, base + 3.5);
        let delta = _mm256_div_pd(_mm256_sub_pd(value, center_y), rr);
        _mm256_storeu_pd(yy.as_mut_ptr().add(at), _mm256_mul_pd(delta, delta));
    }
    let groups = (nz + 3) / 4;
    for (group, value) in zz[..groups].iter_mut().enumerate() {
        let base = z0 as f64 + (group * 4) as f64;
        let position = _mm256_setr_pd(base + 0.5, base + 1.5, base + 2.5, base + 3.5);
        let delta = _mm256_div_pd(_mm256_sub_pd(position, center_z), rr);
        *value = _mm256_mul_pd(delta, delta);
    }
    let allowed = (1u32 << nz) - 1;
    for x in x0..=x1 {
        let dx = (x as f64 + 0.5 - cx) / r;
        let xx = dx * dx;
        if !(xx < 1.0) {
            continue;
        }
        for (iy, y2) in yy[..ny].iter().enumerate() {
            let xy = xx + y2;
            if !(xy < 1.0) {
                continue;
            }
            let vv = _mm256_set1_pd(xy);
            let mut mask = 0u32;
            for (group, z2) in zz[..groups].iter().enumerate() {
                let inside =
                    _mm256_cmp_pd::<_CMP_LT_OQ>(_mm256_add_pd(vv, *z2), _mm256_set1_pd(1.0));
                mask |= (_mm256_movemask_pd(inside) as u32) << (group * 4);
            }
            mask &= allowed;
            if mask != 0 {
                let first = mask.trailing_zeros() as i32;
                let last = 31 - mask.leading_zeros() as i32;
                emit(output, count, [x, y0 + iy as i32, z0 + first, z0 + last]);
            }
        }
    }
}

/// Only called after the dispatch bounds prove the value finite and strictly
/// inside the i32 range. SSE's conversion is exact here; no saturating fallback
/// may be speculated into this hot path by the compiler.
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn floor_bounded(value: f64) -> i32 {
    let integer = _mm_cvttsd_si32(_mm_set_sd(value));
    integer.wrapping_sub((value < integer as f64) as i32)
}

/// One to four bounded spheres of radius <= 1 (negative/dead radii are allowed).
/// Lanes represent spheres, avoiding a separate setup/division chain per sphere.
#[target_feature(enable = "avx2")]
pub(super) unsafe fn tiny_batch4(
    s: &[f64],
    lower: [i32; 3],
    output: &mut [i32],
    count: &mut usize,
) {
    debug_assert!(s.len() >= 4 && s.len() <= 16 && s.len() % 4 == 0);
    let a = _mm256_loadu_pd(s.as_ptr());
    let b = if s.len() >= 8 {
        _mm256_loadu_pd(s.as_ptr().add(4))
    } else {
        _mm256_setzero_pd()
    };
    let c = if s.len() >= 12 {
        _mm256_loadu_pd(s.as_ptr().add(8))
    } else {
        _mm256_setzero_pd()
    };
    let d = if s.len() >= 16 {
        _mm256_loadu_pd(s.as_ptr().add(12))
    } else {
        _mm256_setzero_pd()
    };
    let ab0 = _mm256_unpacklo_pd(a, b);
    let ab1 = _mm256_unpackhi_pd(a, b);
    let cd0 = _mm256_unpacklo_pd(c, d);
    let cd1 = _mm256_unpackhi_pd(c, d);
    let centers = [
        _mm256_permute2f128_pd::<0x20>(ab0, cd0),
        _mm256_permute2f128_pd::<0x20>(ab1, cd1),
        _mm256_permute2f128_pd::<0x31>(ab0, cd0),
    ];
    let radii = _mm256_permute2f128_pd::<0x31>(ab1, cd1);
    let mut coords = [[0i32; 4]; 3];
    let mut near = [[0.0; 4]; 3];
    let mut far = [[0.0; 4]; 3];
    let mut floors = [_mm256_setzero_pd(); 3];
    let mut square = [_mm256_setzero_pd(); 3];
    let half = _mm256_set1_pd(0.5);
    for axis in 0..3 {
        floors[axis] = _mm256_floor_pd(centers[axis]);
        _mm_storeu_si128(
            coords[axis].as_mut_ptr() as *mut __m128i,
            _mm256_cvttpd_epi32(floors[axis]),
        );
        let delta = _mm256_div_pd(
            _mm256_sub_pd(_mm256_add_pd(floors[axis], half), centers[axis]),
            radii,
        );
        square[axis] = _mm256_mul_pd(delta, delta);
        _mm256_storeu_pd(near[axis].as_mut_ptr(), square[axis]);
    }
    let nearest_sum = _mm256_add_pd(_mm256_add_pd(square[0], square[1]), square[2]);
    let inside = _mm256_cmp_pd::<_CMP_LT_OQ>(nearest_sum, _mm256_set1_pd(1.0));
    let active = _mm256_movemask_pd(_mm256_and_pd(
        inside,
        _mm256_cmp_pd::<_CMP_GT_OQ>(radii, _mm256_setzero_pd()),
    ));
    if active == 0 {
        return;
    }
    let mut left = [0; 3];
    for axis in 0..3 {
        let mask = _mm256_cmp_pd::<_CMP_LT_OQ>(centers[axis], _mm256_add_pd(floors[axis], half));
        left[axis] = _mm256_movemask_pd(mask);
        let direction = _mm256_blendv_pd(_mm256_set1_pd(1.0), _mm256_set1_pd(-1.0), mask);
        let position = _mm256_add_pd(_mm256_add_pd(floors[axis], direction), half);
        let delta = _mm256_div_pd(_mm256_sub_pd(position, centers[axis]), radii);
        _mm256_storeu_pd(far[axis].as_mut_ptr(), _mm256_mul_pd(delta, delta));
    }
    for lane in 0..4 {
        if active & (1 << lane) == 0 {
            continue;
        }
        let mut base = [0; 3];
        let mut squared = [[0.0; 2]; 3];
        for axis in 0..3 {
            let negative = left[axis] & (1 << lane) != 0;
            base[axis] = coords[axis][lane] - (negative as i32);
            squared[axis] = if negative {
                [far[axis][lane], near[axis][lane]]
            } else {
                [near[axis][lane], far[axis][lane]]
            };
        }
        for ix in 0..2 {
            let x = base[0] + ix as i32;
            if x < lower[0] {
                continue;
            }
            for iy in 0..2 {
                let y = base[1] + iy as i32;
                if y < lower[1] {
                    continue;
                }
                let xy = squared[0][ix] + squared[1][iy];
                let first = base[2] >= lower[2] && xy + squared[2][0] < 1.0;
                let last = base[2] + 1 >= lower[2] && xy + squared[2][1] < 1.0;
                if first || last {
                    super::geometry::emit(
                        output,
                        count,
                        [x, y, base[2] + (!first as i32), base[2] + (last as i32)],
                    );
                }
            }
        }
    }
}

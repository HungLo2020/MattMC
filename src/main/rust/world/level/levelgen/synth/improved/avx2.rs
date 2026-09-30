//! Vector and bounded-coordinate Improved kernels.
use super::super::{avx2_helpers::*, state::Octave};
use std::arch::x86_64::*;
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn noise_impl<const SCALED: bool>(
    o: &Octave,
    x: __m256d,
    y: __m256d,
    z: __m256d,
    ys: __m256d,
    ym: __m256d,
) -> __m256d {
    unsafe {
        let x = _mm256_add_pd(x, _mm256_set1_pd(o.x));
        let y = _mm256_add_pd(y, _mm256_set1_pd(o.y));
        let z = _mm256_add_pd(z, _mm256_set1_pd(o.z));
        let ix = _mm256_cvttpd_epi32(_mm256_floor_pd(x));
        let iy = _mm256_cvttpd_epi32(_mm256_floor_pd(y));
        let iz = _mm256_cvttpd_epi32(_mm256_floor_pd(z));
        let x = _mm256_sub_pd(x, _mm256_cvtepi32_pd(ix));
        let y = _mm256_sub_pd(y, _mm256_cvtepi32_pd(iy));
        let z = _mm256_sub_pd(z, _mm256_cvtepi32_pd(iz));
        let fade_y = y;
        let y = if SCALED {
            let use_max = _mm256_and_pd(
                _mm256_cmp_pd::<_CMP_GE_OQ>(ym, _mm256_setzero_pd()),
                _mm256_cmp_pd::<_CMP_LT_OQ>(ym, y),
            );
            let r = _mm256_blendv_pd(y, ym, use_max);
            // Dispatch bounds keep r/ys inside Java's nonsaturating int range.
            let snapped = _mm256_floor_pd(_mm256_add_pd(
                _mm256_div_pd(r, ys),
                _mm256_set1_pd(1.0e-7_f32 as f64),
            ));
            _mm256_sub_pd(y, _mm256_mul_pd(snapped, ys))
        } else {
            y
        };
        let one = _mm_set1_epi32(1);
        let fone = _mm256_set1_pd(1.);
        let a = lookup(o, ix);
        let b = lookup(o, _mm_add_epi32(ix, one));
        let c = lookup(o, _mm_add_epi32(a, iy));
        let d = lookup(o, _mm_add_epi32(_mm_add_epi32(a, iy), one));
        let e = lookup(o, _mm_add_epi32(b, iy));
        let f = lookup(o, _mm_add_epi32(_mm_add_epi32(b, iy), one));
        let xm = _mm256_sub_pd(x, fone);
        let ym = _mm256_sub_pd(y, fone);
        let zm = _mm256_sub_pd(z, fone);
        let v0 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, z);
        let v1 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, z);
        let v2 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, z);
        let v3 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, z);
        let iz = _mm_add_epi32(iz, one);
        let v4 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, zm);
        let v5 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, zm);
        let v6 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, zm);
        let v7 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, zm);
        let sx = smooth(x);
        let sy = smooth(fade_y);
        let sz = smooth(z);
        lerp(
            sz,
            lerp(sy, lerp(sx, v0, v1), lerp(sx, v2, v3)),
            lerp(sy, lerp(sx, v4, v5), lerp(sx, v6, v7)),
        )
    }
}
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn noise(o: &Octave, x: __m256d, y: __m256d, z: __m256d) -> __m256d {
    unsafe { noise_impl::<false>(o, x, y, z, _mm256_setzero_pd(), _mm256_setzero_pd()) }
}
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn noise_octaves(
    o: &[Octave],
    indices: __m128i,
    x: __m256d,
    y: __m256d,
    z: __m256d,
) -> __m256d {
    unsafe {
        let fields = _mm_mullo_epi32(
            indices,
            _mm_set1_epi32((std::mem::size_of::<Octave>() / 8) as i32),
        );
        let base = o.as_ptr().cast::<f64>();
        let x = _mm256_add_pd(x, _mm256_i32gather_pd::<8>(base, fields));
        let y = _mm256_add_pd(y, _mm256_i32gather_pd::<8>(base.add(1), fields));
        let z = _mm256_add_pd(z, _mm256_i32gather_pd::<8>(base.add(2), fields));
        let tables = _mm_mullo_epi32(
            indices,
            _mm_set1_epi32((std::mem::size_of::<Octave>() / 4) as i32),
        );
        let lookup = |_: &[Octave], i| {
            _mm_i32gather_epi32::<4>(
                o[0].p32.as_ptr(),
                _mm_add_epi32(tables, _mm_and_si128(i, _mm_set1_epi32(255))),
            )
        };
        let ix = _mm256_cvttpd_epi32(_mm256_floor_pd(x));
        let iy = _mm256_cvttpd_epi32(_mm256_floor_pd(y));
        let iz = _mm256_cvttpd_epi32(_mm256_floor_pd(z));
        let x = _mm256_sub_pd(x, _mm256_cvtepi32_pd(ix));
        let y = _mm256_sub_pd(y, _mm256_cvtepi32_pd(iy));
        let z = _mm256_sub_pd(z, _mm256_cvtepi32_pd(iz));
        let one = _mm_set1_epi32(1);
        let fone = _mm256_set1_pd(1.);
        let a = lookup(o, ix);
        let b = lookup(o, _mm_add_epi32(ix, one));
        let c = lookup(o, _mm_add_epi32(a, iy));
        let d = lookup(o, _mm_add_epi32(_mm_add_epi32(a, iy), one));
        let e = lookup(o, _mm_add_epi32(b, iy));
        let f = lookup(o, _mm_add_epi32(_mm_add_epi32(b, iy), one));
        let xm = _mm256_sub_pd(x, fone);
        let ym = _mm256_sub_pd(y, fone);
        let zm = _mm256_sub_pd(z, fone);
        let v0 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, z);
        let v1 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, z);
        let v2 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, z);
        let v3 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, z);
        let iz = _mm_add_epi32(iz, one);
        let v4 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, zm);
        let v5 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, zm);
        let v6 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, zm);
        let v7 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, zm);
        let sx = smooth(x);
        let sy = smooth(y);
        let sz = smooth(z);
        lerp(
            sz,
            lerp(sy, lerp(sx, v0, v1), lerp(sx, v2, v3)),
            lerp(sy, lerp(sx, v4, v5), lerp(sx, v6, v7)),
        )
    }
}

#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn bounded_noise(o: &Octave, x: f64, y: f64, z: f64, ys: f64, ym: f64) -> f64 {
    let x = x + o.x;
    let y = y + o.y;
    let z = z + o.z;
    let fx = x.floor();
    let fy = y.floor();
    let fz = z.floor();
    // Caller checks finite coordinate/frequency bounds before this path.
    let (ix, iy, iz) = unsafe {
        (
            fx.to_int_unchecked::<i32>(),
            fy.to_int_unchecked::<i32>(),
            fz.to_int_unchecked::<i32>(),
        )
    };
    let dx = x - ix as f64;
    let dy = y - iy as f64;
    let dz = z - iz as f64;
    let shift = if ys != 0. {
        let r = if ym >= 0. && ym < dy { ym } else { dy };
        super::floor(r / ys + 1.0e-7_f32 as f64) as f64 * ys
    } else {
        0.
    };
    let ey = dy - shift;
    let c = o.corners(ix, iy, iz);
    let v = std::array::from_fn(|n| {
        super::super::common::dot(
            c[n],
            dx - (n & 1) as f64,
            ey - ((n >> 1) & 1) as f64,
            dz - (n >> 2) as f64,
        )
    });
    super::super::super::math::lerp3(
        super::super::common::smooth(dx),
        super::super::common::smooth(dy),
        super::super::common::smooth(dz),
        v,
    )
}

//! Cell interpolation and exact-order evaluation.
#[cfg(target_arch = "x86_64")]
use super::avx2::unary_cell_array_avx2;
use super::*;
pub(crate) unsafe fn unary_cell_array<const ADD: bool>(
    n: &[Node],
    frame: &[f64],
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
    add: f64,
) -> i64 {
    #[cfg(target_arch = "x86_64")]
    if width >= 4 && count >= 4 && std::is_x86_feature_detected!("avx2") {
        return unsafe {
            unary_cell_array_avx2::<ADD>(n, frame, output, start, count, width, height, add)
        };
    }
    let at = n[0].a as usize * 8;
    let v = &frame[at..at + 8];
    let mut fractions = [0.; 64];
    for i in 0..width as usize {
        fractions[i] = i as f64 / width as f64;
    }
    let mut ix = (start / width) % width;
    let mut iy = height - 1 - start / (width * width);
    let mut iz = start % width;
    let row = |ix: u32, iy: u32| {
        let x = fractions[ix as usize];
        let y = iy as f64 / height as f64;
        [
            crate::world::level::levelgen::math::lerp2(x, y, v[0], v[1], v[2], v[3]),
            crate::world::level::levelgen::math::lerp2(x, y, v[4], v[5], v[6], v[7]),
        ]
    };
    let mut values = row(ix, iy);
    for j in 0..count {
        let value =
            crate::world::level::levelgen::math::lerp(fractions[iz as usize], values[0], values[1]);
        let value = math(20, math(12, value, 0., n[1].p, 0.), 0., 0., 0.);
        unsafe {
            *output.add(j as usize) = if ADD { value + add } else { value };
        }
        iz += 1;
        if iz == width {
            iz = 0;
            ix += 1;
            if ix == width {
                ix = 0;
                iy = iy.wrapping_sub(1);
            }
            if j + 1 < count {
                values = row(ix, iy);
            }
        }
    }
    (3i64 << 32) | (start as i64 + count as i64 - 1)
}

// Four Z samples share the exact scalar X/Y intermediates. Ordered comparisons
// preserve NaNs and signed zero; every arithmetic operation retains Java order.

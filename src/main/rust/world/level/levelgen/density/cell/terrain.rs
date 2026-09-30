//! Cell interpolation and exact-order evaluation.
use super::*;
// Specialize this expression shape, not a seed or a set of constants. It is the
// common final-density/noodle tree. Exact shape checks retain the general
// evaluator for data packs and every other expression. Shared math routines
// preserve all predicates, operation order, NaNs and signed zeros.
pub(crate) fn terrain_cell_shape(n: &[Node]) -> bool {
    if n.len() != 15 {
        return false;
    }
    let ops = [24, 12, 20, 24, 0, 24, 24, 14, 24, 14, 11, 12, 8, 22, 10];
    if n.iter().zip(ops).any(|(n, op)| n.op != op) {
        return false;
    }
    n[1].a == 0
        && n[2].a == 1
        && n[7].a == 6
        && n[9].a == 8
        && n[10].a == 7
        && n[10].b == 9
        && n[11].a == 10
        && n[12].a == 5
        && n[12].b == 11
        && n[13].a == 3
        && n[13].b == 4
        && n[13].c == 12
        && n[14].a == 2
        && n[14].b == 13
}
pub(crate) unsafe fn terrain_cell_array<const ADD: bool>(
    n: &[Node],
    frame: &[f64],
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
    add: f64,
) -> i64 {
    let corners = |index: usize| -> [f64; 8] {
        let at = n[index].a as usize * 8;
        frame[at..at + 8].try_into().unwrap()
    };
    let frames = [corners(0), corners(3), corners(5), corners(6), corners(8)];
    let mut fractions = [0.; 64];
    for i in 0..width as usize {
        fractions[i] = i as f64 / width as f64;
    }
    let mut ix = (start / width) % width;
    let mut iy = height - 1 - start / (width * width);
    let mut iz = start % width;
    let mut x = fractions[ix as usize];
    let mut y = iy as f64 / height as f64;
    let row = |x, y| {
        frames.map(|v| {
            [
                crate::world::level::levelgen::math::lerp2(x, y, v[0], v[1], v[2], v[3]),
                crate::world::level::levelgen::math::lerp2(x, y, v[4], v[5], v[6], v[7]),
            ]
        })
    };
    let mut rows = row(x, y);
    let mut last = -1;
    for j in 0..count {
        let i = start + j;
        let z = fractions[iz as usize];
        let sample = |index: usize| {
            crate::world::level::levelgen::math::lerp(z, rows[index][0], rows[index][1])
        };
        let a = math(20, math(12, sample(0), 0., n[1].p, 0.), 0., 0., 0.);
        let value = if needs_right(10, a, n[14].p, 0.) {
            last = ((30i64) << 32) | i as i64;
            let b = if in_range(sample(1), n[13].p, n[13].q) {
                n[4].p
            } else {
                let thickness = sample(2);
                let ra = math(14, sample(3), 0., 0., 0.);
                let rb = if needs_right(11, ra, 0., n[10].q) {
                    math(14, sample(4), 0., 0., 0.)
                } else {
                    0.
                };
                let ridge = math(11, ra, rb, 0., n[10].q);
                math(8, thickness, math(12, ridge, 0., n[11].p, 0.), 0., 0.)
            };
            math(10, a, b, n[14].p, 0.)
        } else {
            a
        };
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
                y = iy as f64 / height as f64;
            }
            x = fractions[ix as usize];
            if j + 1 < count {
                rows = row(x, y);
            }
        }
    }
    if last < 0 && count > 0 {
        (3i64 << 32) | (start as i64 + count as i64 - 1)
    } else {
        last
    }
}

// The single-interpolator postprocessing used by Nether/End-like settings.

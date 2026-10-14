//! `OreFeature.place`/`doPlace` over a decoration region. Vein geometry reuses
//! [`feature::ore`](crate::world::level::levelgen::feature); this module owns the
//! draws, height test, visited bits, rule tests and section writes around it.
use std::sync::OnceLock;

use super::placement::Placement;
use super::program::Feature;
use super::region::{Result, OCEAN_FLOOR_WG};
use crate::core::java_trig;
use crate::world::level::levelgen::feature;

/// `Mth.SIN`: `(float) Math.sin(i * PI * 2 / 65536)` with HotSpot's `Math.sin`.
pub(crate) fn sine_table() -> &'static [f32; 65536] {
    static TABLE: OnceLock<Box<[f32; 65536]>> = OnceLock::new();
    TABLE.get_or_init(|| {
        let mut table = Box::new([0.0f32; 65536]);
        for (i, value) in table.iter_mut().enumerate() {
            let x = i as f64 * std::f64::consts::PI * 2.0 / 65536.0;
            *value = java_trig::sin(x).expect("table inputs lie on the main path") as f32;
        }
        table
    })
}

/// `Mth.sin`.
fn mth_sin(value: f32) -> f32 {
    sine_table()[((value * 10430.378_f32) as i32 & 65535) as usize]
}

/// `Mth.ceil(float)`: Java's saturating cast, then a strict comparison.
fn ceil(value: f32) -> i32 {
    let truncated = value as i32;
    if value > truncated as f32 { truncated + 1 } else { truncated }
}

/// Fraction and sine factors `NativeOreGeometry` precomputes per vein size.
pub(crate) fn shape(size: i32) -> Box<[f64]> {
    (0..size.max(0))
        .flat_map(|q| {
            let fraction = q as f32 / size as f32;
            [f64::from(fraction), f64::from(mth_sin(std::f32::consts::PI * fraction) + 1.0)]
        })
        .collect()
}

/// `Direction.values()` order for `Feature.isAdjacentToAir`.
const DIRECTIONS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

pub(crate) fn place(placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
    let Feature::Ore { size, .. } = placement.program.feature;
    let [x, y, z] = origin;
    let f = placement.random.next_float() * std::f64::consts::PI as f32;
    let g = size as f32 / 8.0;
    let i = ceil((size as f32 / 16.0 * 2.0 + 1.0) / 2.0);
    let sin = java_trig::sin(f64::from(f)).expect("ore angles lie on the main path");
    let cos = java_trig::cos(f64::from(f)).expect("ore angles lie on the main path");
    let x0 = f64::from(x) + sin * f64::from(g);
    let x1 = f64::from(x) - sin * f64::from(g);
    let z0 = f64::from(z) + cos * f64::from(g);
    let z1 = f64::from(z) - cos * f64::from(g);
    let y0 = f64::from(y.wrapping_add(placement.random.next_int(3)).wrapping_sub(2));
    let y1 = f64::from(y.wrapping_add(placement.random.next_int(3)).wrapping_sub(2));
    let min_x = x - ceil(g) - i;
    let min_y = y - 2 - i;
    let min_z = z - ceil(g) - i;
    let width = 2 * (ceil(g) + i);
    let height = 2 * (2 + i);
    for s in min_x..=min_x + width {
        for t in min_z..=min_z + width {
            if min_y <= placement.region.height(OCEAN_FLOOR_WG, s, t)? {
                return place_vein(placement, [x0, x1, z0, z1, y0, y1], [min_x, min_y, min_z], width, height);
            }
        }
    }
    Ok(false)
}

fn place_vein(placement: &mut Placement, ends: [f64; 6], min: [i32; 3], width: i32, height: i32) -> Result<bool> {
    let Feature::Ore { size, discard_chance_on_air_exposure, ref targets } = placement.program.feature;
    let [x0, x1, z0, z1, y0, y1] = ends;
    let mut samples = Vec::with_capacity(size as usize + 6);
    samples.extend_from_slice(&[x0, x1, y0, y1, z0, z1]);
    for _ in 0..size {
        samples.push(placement.random.next_double());
    }
    let spans = feature::ore_spans(&samples, &placement.program.ore_shape, min);
    let mut visited = vec![0u64; ((width * height * width) as usize).div_ceil(64)];
    let mut placed = 0;
    for span in spans.chunks_exact(4) {
        let (bx, by) = (span[0], span[1]);
        for bz in span[2]..=span[3] {
            if placement.region.outside_build_height(by) {
                continue;
            }
            let index = (bx - min[0] + (by - min[1]) * width + (bz - min[2]) * width * height) as usize;
            if visited[index >> 6] & (1 << (index & 63)) != 0 {
                continue;
            }
            visited[index >> 6] |= 1 << (index & 63);
            if !placement.region.ensure_can_write(bx, by, bz) {
                continue;
            }
            let state = placement.region.block_state(bx, by, bz)?;
            for target in targets {
                if can_place(placement, state, discard_chance_on_air_exposure, target.set, target.probability, [bx, by, bz])? {
                    placement.region.set_section_state(bx, by, bz, target.state)?;
                    placed += 1;
                    break;
                }
            }
        }
    }
    Ok(placed > 0)
}

/// `OreFeature.canPlaceOre`: the rule test, then `shouldSkipAirCheck`, then the six neighbours.
fn can_place(
    placement: &mut Placement,
    state: u32,
    discard_chance: f32,
    set: usize,
    probability: Option<f32>,
    pos: [i32; 3],
) -> Result<bool> {
    if !placement.program.in_set(set, state) {
        return Ok(false);
    }
    if let Some(probability) = probability {
        if placement.random.next_float() >= probability {
            return Ok(false);
        }
    }
    let skip_air_check = if discard_chance <= 0.0 {
        true
    } else if discard_chance >= 1.0 {
        false
    } else {
        placement.random.next_float() >= discard_chance
    };
    if skip_air_check {
        return Ok(true);
    }
    for [dx, dy, dz] in DIRECTIONS {
        if placement.region.is_air(pos[0] + dx, pos[1] + dy, pos[2] + dz)? {
            return Ok(false);
        }
    }
    Ok(true)
}

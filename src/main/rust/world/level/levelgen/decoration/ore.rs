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

pub(crate) fn place(placement: &mut Placement, feature: &Feature, origin: [i32; 3]) -> Result<bool> {
    let Feature::Ore { size, .. } = *feature else {
        unreachable!("dispatched by feature kind")
    };
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
                return place_vein(placement, feature, [x0, x1, z0, z1, y0, y1], [min_x, min_y, min_z], width, height);
            }
        }
    }
    Ok(false)
}

fn place_vein(placement: &mut Placement, feature: &Feature, ends: [f64; 6], min: [i32; 3], width: i32, height: i32) -> Result<bool> {
    let Feature::Ore { size, discard_chance_on_air_exposure, ref targets, ref shape } = *feature else {
        unreachable!("dispatched by feature kind")
    };
    let [x0, x1, z0, z1, y0, y1] = ends;
    let mut scratch = std::mem::take(&mut placement.scratch);
    scratch.samples.clear();
    scratch.samples.extend_from_slice(&[x0, x1, y0, y1, z0, z1]);
    for _ in 0..size {
        scratch.samples.push(placement.random.next_double());
    }
    feature::ore_spans(&scratch.samples, shape, min, &mut scratch.spheres, &mut scratch.spans);
    scratch.visited.clear();
    scratch.visited.resize(((width * height * width) as usize).div_ceil(64), 0);
    let result = place_spans(placement, &scratch.spans, &mut scratch.visited, min, width, height, size, discard_chance_on_air_exposure, targets);
    placement.scratch = scratch;
    result
}

#[allow(clippy::too_many_arguments)]
fn place_spans(
    placement: &mut Placement,
    spans: &[i32],
    visited: &mut [u64],
    min: [i32; 3],
    width: i32,
    height: i32,
    _size: i32,
    discard_chance_on_air_exposure: f32,
    targets: &[super::program::Target],
) -> Result<bool> {
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

/// `ScatteredOreFeature`: up to `size` single blocks within seven of the
/// origin, through `WorldGenRegion` reads and `setBlock(…, 2)`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Scattered {
    size: i32,
    discard_chance_on_air_exposure: f32,
    targets: Vec<super::program::Target>,
}

/// `Math.round(float)`: nearest, ties towards positive infinity.
fn round(value: f32) -> i32 {
    let floor = value.floor();
    (if value - floor >= 0.5 { floor + 1.0 } else { floor }) as i32
}

impl Scattered {
    /// The ore words: size, discard chance, then (rule, probability, set, state) per target.
    pub(crate) fn read(reader: &mut super::program::Reader) -> Option<Self> {
        let size = reader.next().filter(|size| (0..=64).contains(size))?;
        let discard_chance_on_air_exposure = reader.float()?;
        let count = reader.count(64)?;
        let mut targets = Vec::with_capacity(count);
        for _ in 0..count {
            let rule = reader.next()?;
            let probability = reader.float()?;
            let set = reader.set()?;
            let state = reader.write_state()?;
            let probability = match rule {
                1 => None,
                2 => Some(probability),
                _ => return None,
            };
            targets.push(super::program::Target { set, probability, state });
        }
        Some(Self { size, discard_chance_on_air_exposure, targets })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let count = placement.random.next_int(self.size + 1);
        for j in 0..count {
            let reach = j.min(7) as f32;
            let mut offset = [0; 3];
            for axis in offset.iter_mut() {
                let a = placement.random.next_float();
                let b = placement.random.next_float();
                *axis = round((a - b) * reach);
            }
            let pos = [origin[0] + offset[0], origin[1] + offset[1], origin[2] + offset[2]];
            let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
            for target in &self.targets {
                if self.can_place(placement, state, target, pos)? {
                    placement.region.set_block(pos[0], pos[1], pos[2], target.state, 2)?;
                    break;
                }
            }
        }
        Ok(true)
    }

    /// `OreFeature.canPlaceOre` over `WorldGenRegion.getBlockState`.
    fn can_place(&self, placement: &mut Placement, state: u32, target: &super::program::Target, pos: [i32; 3]) -> Result<bool> {
        if !placement.program.in_set(target.set, state) {
            return Ok(false);
        }
        if let Some(probability) = target.probability {
            if placement.random.next_float() >= probability {
                return Ok(false);
            }
        }
        let discard = self.discard_chance_on_air_exposure;
        let skip_air_check = if discard <= 0.0 {
            true
        } else if discard >= 1.0 {
            false
        } else {
            placement.random.next_float() >= discard
        };
        if skip_air_check {
            return Ok(true);
        }
        for [dx, dy, dz] in DIRECTIONS {
            let neighbour = placement.region.level_block_state(pos[0] + dx, pos[1] + dy, pos[2] + dz)?;
            if placement.region.registry().flags(crate::content::block::StateId(neighbour as u16)).contains(crate::content::block::StateFlags::AIR) {
                return Ok(false);
            }
        }
        Ok(true)
    }
}

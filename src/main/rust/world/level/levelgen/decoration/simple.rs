//! Small features over `WorldGenRegion` reads and `setBlock`:
//! `ReplaceBlobsFeature`, `GlowstoneFeature` and `SpringFeature`.
use super::placement::Placement;
use super::program::{Feature, IntProvider};
use super::region::Result;
use crate::content::block::{StateFlags, StateId};

/// `Direction.values()`: down, up, north, south, west, east.
const DIRECTIONS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

impl IntProvider {
    /// `IntProvider.sample`. A clamped normal's `Math.log` comes from Java;
    /// should that upcall fail, the region records it and the placement fails at its next step.
    pub(crate) fn sample(&self, placement: &mut Placement) -> i32 {
        if let IntProvider::ClampedNormal(mean, deviation, min, max) = *self {
            let gaussian = placement.next_gaussian().unwrap_or_else(|failure| {
                placement.region.defer(failure);
                0.0
            });
            // `(int)Mth.clamp(Mth.normal(random, mean, deviation), min, max)` over floats.
            let normal = mean + gaussian as f32 * deviation;
            return clamp_float(normal, min as f32, max as f32) as i32;
        }
        let random = &mut placement.random;
        match *self {
            IntProvider::Constant(value) => value,
            IntProvider::Uniform(min, max) => random.between_inclusive(min, max),
            IntProvider::BiasedToBottom(min, max) => {
                let bound = random.next_int(max.wrapping_sub(min).wrapping_add(1)).wrapping_add(1);
                min.wrapping_add(random.next_int(bound))
            }
            // `distribution.getRandomOrThrow(random).sample(random)`.
            IntProvider::Weighted(ref entries, total) => {
                let mut pick = random.next_int(total);
                for (weight, provider) in entries.iter() {
                    pick -= weight;
                    if pick < 0 {
                        return provider.sample(placement);
                    }
                }
                unreachable!("weights sum to the total")
            }
            // `Mth.clamp(source.sample(random), min, max)`; min <= max is checked on decode.
            IntProvider::Clamped(ref source, min, max) => source.sample(placement).clamp(min, max),
            IntProvider::ClampedNormal(..) => unreachable!("sampled above"),
        }
    }
}

/// `Mth.clamp(float, float, float)`: `f < min ? min : Math.min(f, max)`.
pub(crate) fn clamp_float(f: f32, min: f32, max: f32) -> f32 {
    if f < min {
        min
    } else if f.is_nan() || max.is_nan() {
        f32::NAN
    } else {
        f.min(max)
    }
}

impl super::program::FloatProvider {
    /// `FloatProvider.sample`; a clamped normal's failed `Math.log` upcall is deferred.
    pub(crate) fn sample(&self, placement: &mut Placement) -> f32 {
        use super::program::FloatProvider;
        match *self {
            FloatProvider::Constant(value) => value,
            FloatProvider::Uniform(min, max) => placement.random.next_float() * (max - min) + min,
            FloatProvider::ClampedNormal(mean, deviation, min, max) => {
                let gaussian = placement.next_gaussian().unwrap_or_else(|failure| {
                    placement.region.defer(failure);
                    0.0
                });
                clamp_float(mean + gaussian as f32 * deviation, min, max)
            }
            FloatProvider::Trapezoid(min, max, plateau) => {
                let f = max - min;
                let g = (f - plateau) / 2.0f32;
                let h = f - g;
                min + placement.random.next_float() * h + placement.random.next_float() * g
            }
        }
    }
}

fn is_air(placement: &Placement, state: u32) -> bool {
    placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR)
}

/// `SimpleBlockFeature.place`.
pub(crate) fn simple_block(
    placement: &mut Placement,
    provider: &super::tree::Provider,
    survival: &[(u32, super::program::Survival)],
    schedule_tick: bool,
    water_fluid: usize,
    origin: [i32; 3],
) -> Result<bool> {
    use super::program::Survival;
    let [x, y, z] = origin;
    let state = provider.state(placement);
    let rule = survival.iter().find(|(s, _)| *s == state).map(|(_, r)| r).ok_or(super::region::Failure::Unbound)?;
    let survives = match rule {
        Survival::Predicate(survives) | Survival::DoublePlant { survives, .. } => placement.test(survives, origin)?,
    };
    if !survives {
        return Ok(false);
    }
    if let Survival::DoublePlant { lower, upper, .. } = *rule {
        if !is_air(placement, placement.region.level_block_state(x, y + 1, z)?) {
            return Ok(false);
        }
        // `DoublePlantBlock.placeAt`: each half copies `isWaterAt` into WATERLOGGED.
        let lower = waterlogged(placement, lower, origin, water_fluid)?;
        placement.region.set_block(x, y, z, lower, 2)?;
        let upper = waterlogged(placement, upper, [x, y + 1, z], water_fluid)?;
        placement.region.set_block(x, y + 1, z, upper, 2)?;
    } else {
        placement.region.set_block(x, y, z, state, 2)?;
    }
    if schedule_tick {
        let placed = placement.region.level_block_state(x, y, z)?;
        let block = placement.region.registry().block_of(StateId(placed as u16)).0 as u32;
        placement.region.schedule_tick(false, x, y, z, block, 1);
    }
    Ok(true)
}

/// `DoublePlantBlock.copyWaterloggedFrom`.
fn waterlogged(placement: &Placement, state: u32, pos: [i32; 3], water_fluid: usize) -> Result<u32> {
    let Some(properties) = super::tree::properties() else {
        return Err(super::region::Failure::Unbound);
    };
    let registry = placement.region.registry();
    if registry.value(StateId(state as u16), properties.waterlogged).is_none() {
        return Ok(state);
    }
    let water = placement.program.in_set(water_fluid, placement.region.level_block_state(pos[0], pos[1], pos[2])?);
    let index = if water { properties.waterlogged_true } else { properties.waterlogged_false };
    Ok(registry.with_value(StateId(state as u16), properties.waterlogged, index).map_or(state, |s| s.0 as u32))
}

/// `BlockPos.withinManhattan(center, rx, ry, rz)`: depth by depth, X then Y
/// ascending, each nonzero Z offset followed by its mirror.
pub(crate) struct Manhattan {
    center: [i32; 3],
    range: [i32; 3],
    total: i32,
    depth: i32,
    max_x: i32,
    max_y: i32,
    x: i32,
    y: i32,
    mirror: Option<[i32; 3]>,
}

impl Manhattan {
    pub(crate) fn new(center: [i32; 3], rx: i32, ry: i32, rz: i32) -> Self {
        Self { center, range: [rx, ry, rz], total: rx + ry + rz, depth: 0, max_x: 0, max_y: 0, x: 0, y: 0, mirror: None }
    }
}

impl Iterator for Manhattan {
    type Item = [i32; 3];

    fn next(&mut self) -> Option<[i32; 3]> {
        if let Some(pos) = self.mirror.take() {
            return Some(pos);
        }
        loop {
            if self.y > self.max_y {
                self.x += 1;
                if self.x > self.max_x {
                    self.depth += 1;
                    if self.depth > self.total {
                        return None;
                    }
                    self.max_x = self.range[0].min(self.depth);
                    self.x = -self.max_x;
                }
                self.max_y = self.range[1].min(self.depth - self.x.abs());
                self.y = -self.max_y;
            }
            let (x, y) = (self.x, self.y);
            self.y += 1;
            let z = self.depth - x.abs() - y.abs();
            if z <= self.range[2] {
                let [cx, cy, cz] = self.center;
                if z != 0 {
                    self.mirror = Some([cx + x, cy + y, cz - z]);
                }
                return Some([cx + x, cy + y, cz + z]);
            }
        }
    }
}

pub(crate) fn place(placement: &mut Placement, feature: &Feature, origin: [i32; 3]) -> Result<bool> {
    match *feature {
        Feature::ReplaceBlobs { ref radius, target, replacement } => replace_blobs(placement, origin, radius, target, replacement),
        Feature::Glowstone { glowstone, hosts, glowstone_blocks } => glowstone_blob(placement, origin, glowstone, hosts, glowstone_blocks),
        Feature::Spring { requires_below, rocks, holes, valid, state, fluid } => {
            spring(placement, origin, requires_below, rocks, holes, valid, state, fluid)
        }
        _ => unreachable!("dispatched by feature kind"),
    }
}

fn replace_blobs(placement: &mut Placement, origin: [i32; 3], radius: &IntProvider, target: usize, replacement: u32) -> Result<bool> {
    let region = &placement.region;
    let max_y = region.min_y + region.sections as i32 * 16 - 1;
    let mut y = origin[1].clamp(region.min_y + 1, max_y);
    let found = loop {
        if y <= region.min_y + 1 {
            break None;
        }
        if placement.program.in_set(target, placement.region.level_block_state(origin[0], y, origin[2])?) {
            break Some([origin[0], y, origin[2]]);
        }
        y -= 1;
    };
    let Some(center) = found else {
        return Ok(false);
    };
    let rx = radius.sample(placement);
    let ry = radius.sample(placement);
    let rz = radius.sample(placement);
    let limit = rx.max(ry.max(rz));
    let mut placed = false;
    for pos in Manhattan::new(center, rx, ry, rz) {
        let distance = (pos[0] - center[0]).abs() + (pos[1] - center[1]).abs() + (pos[2] - center[2]).abs();
        if distance > limit {
            break;
        }
        if placement.program.in_set(target, placement.region.level_block_state(pos[0], pos[1], pos[2])?) {
            placement.region.set_block(pos[0], pos[1], pos[2], replacement, 3)?;
            placed = true;
        }
    }
    Ok(placed)
}

fn glowstone_blob(placement: &mut Placement, origin: [i32; 3], glowstone: u32, hosts: usize, glowstone_blocks: usize) -> Result<bool> {
    let [x, y, z] = origin;
    if !is_air(placement, placement.region.level_block_state(x, y, z)?) {
        return Ok(false);
    }
    if !placement.program.in_set(hosts, placement.region.level_block_state(x, y + 1, z)?) {
        return Ok(false);
    }
    placement.region.set_block(x, y, z, glowstone, 2)?;
    for _ in 0..1500 {
        let dx = placement.random.next_int(8) - placement.random.next_int(8);
        let dy = -placement.random.next_int(12);
        let dz = placement.random.next_int(8) - placement.random.next_int(8);
        let pos = [x + dx, y + dy, z + dz];
        if !is_air(placement, placement.region.level_block_state(pos[0], pos[1], pos[2])?) {
            continue;
        }
        let mut neighbours = 0;
        for [ox, oy, oz] in DIRECTIONS {
            if placement.program.in_set(glowstone_blocks, placement.region.level_block_state(pos[0] + ox, pos[1] + oy, pos[2] + oz)?) {
                neighbours += 1;
            }
            if neighbours > 1 {
                break;
            }
        }
        if neighbours == 1 {
            placement.region.set_block(pos[0], pos[1], pos[2], glowstone, 2)?;
        }
    }
    Ok(true)
}

#[allow(clippy::too_many_arguments)]
fn spring(placement: &mut Placement, origin: [i32; 3], requires_below: bool, rocks: i32, holes: i32, valid: usize, state: u32, fluid: u32) -> Result<bool> {
    let [x, y, z] = origin;
    let valid_at = |placement: &Placement, dx: i32, dy: i32, dz: i32| -> Result<bool> {
        Ok(placement.program.in_set(valid, placement.region.level_block_state(x + dx, y + dy, z + dz)?))
    };
    let air_at = |placement: &Placement, dx: i32, dy: i32, dz: i32| -> Result<bool> {
        Ok(is_air(placement, placement.region.level_block_state(x + dx, y + dy, z + dz)?))
    };
    if !valid_at(placement, 0, 1, 0)? {
        return Ok(false);
    }
    if requires_below && !valid_at(placement, 0, -1, 0)? {
        return Ok(false);
    }
    let here = placement.region.level_block_state(x, y, z)?;
    if !is_air(placement, here) && !placement.program.in_set(valid, here) {
        return Ok(false);
    }
    // West, east, north, south, below: the order Java tests them in.
    const SIDES: [[i32; 3]; 5] = [[-1, 0, 0], [1, 0, 0], [0, 0, -1], [0, 0, 1], [0, -1, 0]];
    let mut rock = 0;
    for [dx, dy, dz] in SIDES {
        if valid_at(placement, dx, dy, dz)? {
            rock += 1;
        }
    }
    let mut hole = 0;
    for [dx, dy, dz] in SIDES {
        if air_at(placement, dx, dy, dz)? {
            hole += 1;
        }
    }
    if rock == rocks && hole == holes {
        placement.region.set_block(x, y, z, state, 2)?;
        placement.region.schedule_tick(true, x, y, z, fluid, 0);
        return Ok(true);
    }
    Ok(false)
}

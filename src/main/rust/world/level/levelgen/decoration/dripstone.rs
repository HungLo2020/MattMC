//! `PointedDripstoneFeature`, `DripstoneClusterFeature` and `DripstoneUtils`.
use super::placement::Placement;
use super::program::{FloatProvider, IntProvider, Reader};
use super::region::{Failure, Result};
use super::simple::clamp_float;
use super::vegetation::DIRECTIONS;

/// `Direction.Plane.HORIZONTAL`: north, east, south, west.
const HORIZONTAL: [usize; 4] = [2, 5, 3, 4];
const DOWN: usize = 0;
const UP: usize = 1;

/// The dripstone states and sets both features use.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Dripstone {
    /// Pointed dripstone by `[tip down/up][TIP_MERGE, TIP, FRUSTUM, MIDDLE, BASE][waterlogged]`.
    pointed: [[[u32; 2]; 5]; 2],
    block: u32,
    water: u32,
    /// `isDripstoneBase`.
    base: usize,
    /// `dripstone_replaceable`.
    replaceable: usize,
    /// `isEmptyOrWater`.
    empty_or_water: usize,
    lava_block: usize,
    /// `getFluidState().is(FluidTags.WATER)` (`isWaterAt`).
    water_fluid: usize,
    /// Water, dripstone block or pointed dripstone (`canPlacePool`'s exclusions).
    pool_excluded: usize,
    /// `base_stone_overworld` or water fluid (`canBeAdjacentToWater`).
    adjacent_to_water: usize,
}

const TIP_MERGE: usize = 0;
const TIP: usize = 1;
const FRUSTUM: usize = 2;
const MIDDLE: usize = 3;
const BASE: usize = 4;

impl Dripstone {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let mut pointed = [[[0; 2]; 5]; 2];
        for direction in pointed.iter_mut() {
            for thickness in direction.iter_mut() {
                *thickness = [reader.write_state()?, reader.write_state()?];
            }
        }
        Some(Self {
            pointed,
            block: reader.write_state()?,
            water: reader.write_state()?,
            base: reader.set()?,
            replaceable: reader.set()?,
            empty_or_water: reader.set()?,
            lava_block: reader.set()?,
            water_fluid: reader.set()?,
            pool_excluded: reader.set()?,
            adjacent_to_water: reader.set()?,
        })
    }

    fn state(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
        placement.region.level_block_state(pos[0], pos[1], pos[2])
    }

    fn is(&self, placement: &Placement, set: usize, pos: [i32; 3]) -> Result<bool> {
        Ok(placement.program.in_set(set, Self::state(placement, pos)?))
    }

    /// `DripstoneUtils.placeDripstoneBlockIfPossible`.
    fn place_block(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<bool> {
        if self.is(placement, self.replaceable, pos)? {
            placement.region.set_block(pos[0], pos[1], pos[2], self.block, 2)?;
            return Ok(true);
        }
        Ok(false)
    }

    /// `DripstoneUtils.growPointedDripstone` with `buildBaseToTipColumn`.
    fn grow(&self, placement: &mut Placement, pos: [i32; 3], direction: usize, length: i32, merge: bool) -> Result<()> {
        let back = DIRECTIONS[direction ^ 1];
        if !self.is(placement, self.base, [pos[0] + back[0], pos[1] + back[1], pos[2] + back[2]])? {
            return Ok(());
        }
        let mut column = Vec::new();
        if length >= 3 {
            column.push(BASE);
            for _ in 0..length - 3 {
                column.push(MIDDLE);
            }
        }
        if length >= 2 {
            column.push(FRUSTUM);
        }
        if length >= 1 {
            column.push(if merge { TIP_MERGE } else { TIP });
        }
        let step = DIRECTIONS[direction];
        let tip = usize::from(direction == UP);
        let mut at = pos;
        for thickness in column {
            let wet = usize::from(self.is(placement, self.water_fluid, at)?);
            placement.region.set_block(at[0], at[1], at[2], self.pointed[tip][thickness][wet], 2)?;
            at = [at[0] + step[0], at[1] + step[1], at[2] + step[2]];
        }
        Ok(())
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Pointed {
    blocks: Dripstone,
    taller: f32,
    directional_spread: f32,
    spread_radius2: f32,
    spread_radius3: f32,
}

impl Pointed {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            blocks: Dripstone::read(reader)?,
            taller: reader.float()?,
            directional_spread: reader.float()?,
            spread_radius2: reader.float()?,
            spread_radius3: reader.float()?,
        })
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let blocks = &self.blocks;
        let up = blocks.is(placement, blocks.base, [origin[0], origin[1] + 1, origin[2]])?;
        let down = blocks.is(placement, blocks.base, [origin[0], origin[1] - 1, origin[2]])?;
        let tip = match (up, down) {
            (true, true) => {
                if placement.random.next(1) != 0 {
                    DOWN
                } else {
                    UP
                }
            }
            (true, false) => DOWN,
            (false, true) => UP,
            (false, false) => return Ok(false),
        };
        let back = DIRECTIONS[tip ^ 1];
        let root = [origin[0] + back[0], origin[1] + back[1], origin[2] + back[2]];
        blocks.place_block(placement, root)?;
        for direction in HORIZONTAL {
            if placement.random.next_float() > self.directional_spread {
                continue;
            }
            let by = DIRECTIONS[direction];
            let second = [root[0] + by[0], root[1] + by[1], root[2] + by[2]];
            blocks.place_block(placement, second)?;
            if placement.random.next_float() > self.spread_radius2 {
                continue;
            }
            let by = DIRECTIONS[placement.random.next_int(6) as usize];
            let third = [second[0] + by[0], second[1] + by[1], second[2] + by[2]];
            blocks.place_block(placement, third)?;
            if placement.random.next_float() > self.spread_radius3 {
                continue;
            }
            let by = DIRECTIONS[placement.random.next_int(6) as usize];
            blocks.place_block(placement, [third[0] + by[0], third[1] + by[1], third[2] + by[2]])?;
        }
        let ahead = DIRECTIONS[tip];
        let length = if placement.random.next_float() < self.taller
            && blocks.is(placement, blocks.empty_or_water, [origin[0] + ahead[0], origin[1] + ahead[1], origin[2] + ahead[2]])?
        {
            2
        } else {
            1
        };
        blocks.grow(placement, origin, tip, length, false)?;
        Ok(true)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Cluster {
    blocks: Dripstone,
    search_range: i32,
    height: IntProvider,
    radius: IntProvider,
    max_height_diff: i32,
    height_deviation: i32,
    layer_thickness: IntProvider,
    density: FloatProvider,
    wetness: FloatProvider,
    chance_at_max_distance: f32,
    max_distance_from_edge: i32,
    max_distance_from_center: i32,
}

/// `Column`: the floor and ceiling a scan found.
#[derive(Clone, Copy)]
struct Column {
    floor: Option<i32>,
    ceiling: Option<i32>,
}

impl Column {
    /// `Column.Range.height()`; rays and lines have none.
    fn height(self) -> Option<i32> {
        match (self.floor, self.ceiling) {
            (Some(floor), Some(ceiling)) => Some(ceiling - floor - 1),
            _ => None,
        }
    }
}

fn between_inclusive(placement: &mut Placement, min: i32, max: i32) -> Result<i32> {
    // `nextInt` throws for a non-positive bound; Java's route would crash too.
    let bound = max.wrapping_sub(min).wrapping_add(1);
    if bound <= 0 {
        return Err(Failure::Declined);
    }
    Ok(placement.random.next_int(bound).wrapping_add(min))
}

impl Cluster {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            blocks: Dripstone::read(reader)?,
            search_range: reader.next()?,
            height: IntProvider::read(reader)?,
            radius: IntProvider::read(reader)?,
            max_height_diff: reader.next()?,
            height_deviation: reader.next()?,
            layer_thickness: IntProvider::read(reader)?,
            density: FloatProvider::read(reader)?,
            wetness: FloatProvider::read(reader)?,
            chance_at_max_distance: reader.float()?,
            max_distance_from_edge: reader.next()?,
            max_distance_from_center: reader.next()?,
        })
    }

    /// `Column.scan(level, pos, range, isEmptyOrWater, isNeitherEmptyNorWater)`.
    fn scan(&self, placement: &Placement, pos: [i32; 3]) -> Result<Option<Column>> {
        let blocks = &self.blocks;
        if !blocks.is(placement, blocks.empty_or_water, pos)? {
            return Ok(None);
        }
        let direction = |placement: &Placement, step: i32| -> Result<Option<i32>> {
            let mut at = pos;
            let mut k = 1;
            while k < self.search_range && blocks.is(placement, blocks.empty_or_water, at)? {
                at[1] += step;
                k += 1;
            }
            Ok((!blocks.is(placement, blocks.empty_or_water, at)?).then_some(at[1]))
        };
        let ceiling = direction(placement, 1)?;
        let floor = direction(placement, -1)?;
        Ok(Some(Column { floor, ceiling }))
    }

    fn can_place_pool(&self, placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let blocks = &self.blocks;
        if blocks.is(placement, blocks.pool_excluded, pos)? || blocks.is(placement, blocks.water_fluid, [pos[0], pos[1] + 1, pos[2]])? {
            return Ok(false);
        }
        for direction in HORIZONTAL {
            let by = DIRECTIONS[direction];
            if !blocks.is(placement, blocks.adjacent_to_water, [pos[0] + by[0], pos[1], pos[2] + by[2]])? {
                return Ok(false);
            }
        }
        blocks.is(placement, blocks.adjacent_to_water, [pos[0], pos[1] - 1, pos[2]])
    }

    fn replace(&self, placement: &mut Placement, mut pos: [i32; 3], count: i32, step: i32) -> Result<()> {
        for _ in 0..count {
            if !self.blocks.place_block(placement, pos)? {
                return Ok(());
            }
            pos[1] += step;
        }
        Ok(())
    }

    /// `getDripstoneHeight`.
    fn dripstone_height(&self, placement: &mut Placement, dx: i32, dz: i32, density: f32, k: i32) -> i32 {
        if placement.random.next_float() > density {
            return 0;
        }
        let l = (dx.abs() + dz.abs()) as f64;
        // `Mth.clampedMap(l, 0, maxDistanceFromCenter, k / 2.0, 0)` over doubles.
        let delta = (l - 0.0) / (self.max_distance_from_center as f64 - 0.0);
        let (from, to) = (k as f64 / 2.0, 0.0);
        let g = (if delta < 0.0 { from } else if delta > 1.0 { to } else { from + delta * (to - from) }) as f32;
        // `ClampedNormalFloat.sample(random, g, heightDeviation, 0, k)`.
        let gaussian = placement.next_gaussian().unwrap_or_else(|failure| {
            placement.region.defer(failure);
            0.0
        });
        clamp_float(g + gaussian as f32 * self.height_deviation as f32, 0.0, k as f32) as i32
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !self.blocks.is(placement, self.blocks.empty_or_water, origin)? {
            return Ok(false);
        }
        let height = self.height.sample(placement);
        let wetness = self.wetness.sample(placement);
        let density = self.density.sample(placement);
        let rx = self.radius.sample(placement);
        let rz = self.radius.sample(placement);
        for l in -rx..=rx {
            for m in -rz..=rz {
                // `getChanceOfStalagmiteOrStalactite`: `Mth.clampedMap` over floats.
                let o = (rx - l.abs()).min(rz - m.abs()) as f32;
                let delta = (o - 0.0f32) / (self.max_distance_from_edge as f32 - 0.0f32);
                let (from, to) = (self.chance_at_max_distance, 1.0f32);
                let chance = (if delta < 0.0 { from } else if delta > 1.0 { to } else { from + delta * (to - from) }) as f64;
                self.column(placement, [origin[0] + l, origin[1], origin[2] + m], l, m, wetness, chance, height, density)?;
            }
        }
        Ok(true)
    }

    #[allow(clippy::too_many_arguments)]
    fn column(&self, placement: &mut Placement, pos: [i32; 3], dx: i32, dz: i32, wetness: f32, chance: f64, height: i32, density: f32) -> Result<()> {
        let Some(scanned) = self.scan(placement, pos)? else {
            return Ok(());
        };
        let ceiling = scanned.ceiling;
        if ceiling.is_none() && scanned.floor.is_none() {
            return Ok(());
        }
        let at = |y: i32| [pos[0], y, pos[2]];
        let wet = placement.random.next_float() < wetness;
        let mut column = scanned;
        if let (true, Some(floor)) = (wet, scanned.floor) {
            if self.can_place_pool(placement, at(floor))? {
                column = Column { floor: Some(floor - 1), ceiling };
                placement.region.set_block(pos[0], floor, pos[2], self.blocks.water, 2)?;
            }
        }
        let floor = column.floor;
        let lava = |placement: &Placement, y: i32| self.blocks.is(placement, self.blocks.lava_block, at(y));
        let stalactite = placement.random.next_double() < chance;
        let o = match ceiling {
            Some(c) if stalactite && !lava(placement, c)? => {
                let thickness = self.layer_thickness.sample(placement);
                self.replace(placement, at(c), thickness, 1)?;
                let n = floor.map_or(height, |f| height.min(c - f));
                self.dripstone_height(placement, dx, dz, density, n)
            }
            _ => 0,
        };
        let stalagmite = placement.random.next_double() < chance;
        let m = match floor {
            Some(f) if stalagmite && !lava(placement, f)? => {
                let thickness = self.layer_thickness.sample(placement);
                self.replace(placement, at(f), thickness, -1)?;
                if ceiling.is_some() {
                    0.max(o + between_inclusive(placement, -self.max_height_diff, self.max_height_diff)?)
                } else {
                    self.dripstone_height(placement, dx, dz, density, height)
                }
            }
            _ => 0,
        };
        let (p, w) = match (ceiling, floor) {
            (Some(r), Some(q)) if r - o <= q + m => {
                let s = (r - o).max(q + 1);
                let t = (q + m).min(r - 1);
                let u = between_inclusive(placement, s, t + 1)?;
                (r - u, u - 1 - q)
            }
            _ => (o, m),
        };
        let merge = placement.random.next(1) != 0 && p > 0 && w > 0 && column.height().is_some_and(|h| p + w == h);
        if let Some(c) = ceiling {
            self.blocks.grow(placement, at(c - 1), DOWN, p, merge)?;
        }
        if let Some(f) = floor {
            self.blocks.grow(placement, at(f + 1), UP, w, merge)?;
        }
        Ok(())
    }
}

/// `Mth.sin`/`Mth.cos` over the shared `Mth.SIN` table.
fn mth_sin(f: f32) -> f32 {
    super::ore::sine_table()[((f * 10430.378f32) as i32 & 65535) as usize]
}

fn mth_cos(f: f32) -> f32 {
    super::ore::sine_table()[((f * 10430.378f32 + 16384.0f32) as i32 & 65535) as usize]
}

/// `Mth.floor(double)`.
fn floor(d: f64) -> i32 {
    let i = d as i32;
    if d < i as f64 { i - 1 } else { i }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Large {
    blocks: Dripstone,
    search_range: i32,
    column_radius: IntProvider,
    radius_min: i32,
    radius_max: i32,
    height_scale: FloatProvider,
    max_radius_ratio: f32,
    stalactite_bluntness: FloatProvider,
    stalagmite_bluntness: FloatProvider,
    wind_speed: FloatProvider,
    min_radius_for_wind: i32,
    min_bluntness_for_wind: f32,
    /// `isDripstoneBaseOrLava`.
    base_or_lava: usize,
    /// `isEmptyOrWaterOrLava`.
    empty_water_or_lava: usize,
    base_stone: usize,
}

struct Wind {
    origin_y: i32,
    speed: Option<[f64; 2]>,
}

impl Wind {
    /// `WindOffsetter.offset`.
    fn offset(&self, pos: [i32; 3]) -> [i32; 3] {
        let Some([sx, sz]) = self.speed else {
            return pos;
        };
        let i = (self.origin_y - pos[1]) as f64;
        [pos[0] + floor(sx * i), pos[1], pos[2] + floor(sz * i)]
    }
}

struct Column3 {
    root: [i32; 3],
    up: bool,
    radius: i32,
    bluntness: f64,
    scale: f64,
}

impl Large {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            blocks: Dripstone::read(reader)?,
            search_range: reader.next()?,
            column_radius: IntProvider::read(reader)?,
            radius_min: reader.next()?,
            radius_max: reader.next()?,
            height_scale: FloatProvider::read(reader)?,
            max_radius_ratio: reader.float()?,
            stalactite_bluntness: FloatProvider::read(reader)?,
            stalagmite_bluntness: FloatProvider::read(reader)?,
            wind_speed: FloatProvider::read(reader)?,
            min_radius_for_wind: reader.next()?,
            min_bluntness_for_wind: reader.float()?,
            base_or_lava: reader.set()?,
            empty_water_or_lava: reader.set()?,
            base_stone: reader.set()?,
        })
    }

    /// `DripstoneUtils.getDripstoneHeight`; `Math.pow`/`Math.log` come from Java.
    fn height(placement: &mut Placement, f: f32, column: &Column3) -> Result<i32> {
        let mut d = f as f64;
        let e = column.radius as f64;
        if d < column.bluntness {
            d = column.bluntness;
        }
        let i = d / e * 0.384;
        let j = 0.75 * placement.java_math(super::region::OP_MATH_POW, i, 1.3333333333333333)?;
        let k = placement.java_math(super::region::OP_MATH_POW, i, 0.6666666666666666)?;
        let l = 0.3333333333333333 * placement.java_math(super::region::OP_MATH_LOG, i, 0.0)?;
        let m = (column.scale * (j - k - l)).max(0.0);
        Ok((m / 0.384 * e) as i32)
    }

    /// `DripstoneUtils.isCircleMostlyEmbeddedInStone`.
    fn embedded(&self, placement: &Placement, pos: [i32; 3], radius: i32) -> Result<bool> {
        if self.blocks.is(placement, self.empty_water_or_lava, pos)? {
            return Ok(false);
        }
        let step = 6.0f32 / radius as f32;
        let mut h = 0.0f32;
        while h < std::f64::consts::PI as f32 * 2.0 {
            let j = (mth_cos(h) * radius as f32) as i32;
            let k = (mth_sin(h) * radius as f32) as i32;
            if self.blocks.is(placement, self.empty_water_or_lava, [pos[0] + j, pos[1], pos[2] + k])? {
                return Ok(false);
            }
            h += step;
        }
        Ok(true)
    }

    fn settle(&self, placement: &mut Placement, column: &mut Column3, wind: &Wind) -> Result<bool> {
        while column.radius > 1 {
            let mut at = column.root;
            let tall = 10.min(Self::height(placement, 0.0, column)?);
            for _ in 0..tall {
                if self.blocks.is(placement, self.blocks.lava_block, at)? {
                    return Ok(false);
                }
                if self.embedded(placement, wind.offset(at), column.radius)? {
                    column.root = at;
                    return Ok(true);
                }
                at[1] += if column.up { -1 } else { 1 };
            }
            column.radius /= 2;
        }
        Ok(false)
    }

    fn place_column(&self, placement: &mut Placement, column: &Column3, wind: &Wind) -> Result<()> {
        for i in -column.radius..=column.radius {
            for j in -column.radius..=column.radius {
                let f = ((i * i + j * j) as f32 as f64).sqrt() as f32;
                if f > column.radius as f32 {
                    continue;
                }
                let mut k = Self::height(placement, f, column)?;
                if k <= 0 {
                    continue;
                }
                if (placement.random.next_float() as f64) < 0.2 {
                    let scale = placement.random.next_float() * (1.0f32 - 0.8f32) + 0.8f32;
                    k = (k as f32 * scale) as i32;
                }
                let mut at = [column.root[0] + i, column.root[1], column.root[2] + j];
                let mut placed = false;
                let limit = if column.up { placement.region.height_by_type(0, at[0], at[2])? } else { i32::MAX };
                let mut m = 0;
                while m < k && at[1] < limit {
                    let pos = wind.offset(at);
                    if self.blocks.is(placement, self.empty_water_or_lava, pos)? {
                        placed = true;
                        placement.region.set_block(pos[0], pos[1], pos[2], self.blocks.block, 2)?;
                    } else if placed && self.blocks.is(placement, self.base_stone, pos)? {
                        break;
                    }
                    at[1] += if column.up { 1 } else { -1 };
                    m += 1;
                }
            }
        }
        Ok(())
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !self.blocks.is(placement, self.blocks.empty_or_water, origin)? {
            return Ok(false);
        }
        // `Column.scan(level, origin, range, isEmptyOrWater, isDripstoneBaseOrLava)`: a range is needed.
        let blocks = &self.blocks;
        let scan = |placement: &Placement, step: i32| -> Result<Option<i32>> {
            let mut at = origin;
            let mut k = 1;
            while k < self.search_range && blocks.is(placement, blocks.empty_or_water, at)? {
                at[1] += step;
                k += 1;
            }
            Ok(blocks.is(placement, self.base_or_lava, at)?.then_some(at[1]))
        };
        let ceiling = scan(placement, 1)?;
        let floor_y = scan(placement, -1)?;
        let (Some(ceiling), Some(floor_y)) = (ceiling, floor_y) else {
            return Ok(false);
        };
        let height = ceiling - floor_y - 1;
        if height < 4 {
            return Ok(false);
        }
        let i = (height as f32 * self.max_radius_ratio) as i32;
        let j = if i < self.radius_min { self.radius_min } else { i.min(self.radius_max) };
        let k = between_inclusive(placement, self.radius_min, j)?;
        let bluntness = self.stalactite_bluntness.sample(placement) as f64;
        let scale = self.height_scale.sample(placement) as f64;
        let mut top = Column3 { root: [origin[0], ceiling - 1, origin[2]], up: false, radius: k, bluntness, scale };
        let bluntness = self.stalagmite_bluntness.sample(placement) as f64;
        let scale = self.height_scale.sample(placement) as f64;
        let mut bottom = Column3 { root: [origin[0], floor_y + 1, origin[2]], up: true, radius: k, bluntness, scale };
        let suitable = |c: &Column3| c.radius >= self.min_radius_for_wind && c.bluntness >= self.min_bluntness_for_wind as f64;
        let wind = if suitable(&top) && suitable(&bottom) {
            let f = self.wind_speed.sample(placement);
            let g = placement.random.next_float() * (std::f64::consts::PI as f32 - 0.0f32) + 0.0f32;
            Wind { origin_y: origin[1], speed: Some([(mth_cos(g) * f) as f64, (mth_sin(g) * f) as f64]) }
        } else {
            Wind { origin_y: 0, speed: None }
        };
        let top_ok = self.settle(placement, &mut top, &wind)?;
        let bottom_ok = self.settle(placement, &mut bottom, &wind)?;
        if top_ok {
            self.place_column(placement, &top, &wind)?;
        }
        if bottom_ok {
            self.place_column(placement, &bottom, &wind)?;
        }
        Ok(true)
    }
}

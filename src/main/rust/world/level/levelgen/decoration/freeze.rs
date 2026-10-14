//! `SnowAndFreezeFeature` (`freeze_top_layer`): per column, ice on cold water
//! and snow on cold ground under each biome's height-adjusted temperature.
//! Block light comes from Java's own `getBrightness` (a pure upcall); the
//! temperature is a pure function of position, so Java's per-thread cache of
//! it never changes a result.
use super::placement::Placement;
use super::program::{Predicate, Reader};
use super::region::{Failure, Result};
use super::vegetation::set_block;

/// `Heightmap.Types.MOTION_BLOCKING`.
const MOTION_BLOCKING: u32 = 4;

/// A biome's `ClimateSettings`, by region biome index.
#[derive(Clone, Debug, PartialEq)]
struct Climate {
    precipitation: bool,
    temperature: f32,
    /// `TemperatureModifier.FROZEN`.
    frozen: bool,
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Freeze {
    sea_level: i32,
    ice: u32,
    snow: u32,
    /// Each state with `SNOWY` and its snowy state.
    snowy: Vec<(u32, u32)>,
    /// Water source fluid in a `LiquidBlock`.
    water: usize,
    air_or_snow: usize,
    /// `SnowLayerBlock.canSurvive` for the default snow layer.
    survives: Predicate,
    /// `Biome.TEMPERATURE_NOISE`, `FROZEN_TEMPERATURE_NOISE`, `BIOME_INFO_NOISE`.
    noises: [usize; 3],
    climates: Vec<Climate>,
}

impl Freeze {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let sea_level = reader.next()?;
        let ice = reader.write_state()?;
        let snow = reader.write_state()?;
        let count = reader.count(1 << 12)?;
        let snowy = (0..count).map(|_| Some((reader.state()?, reader.write_state()?))).collect::<Option<Vec<_>>>()?;
        let water = reader.set()?;
        let air_or_snow = reader.set()?;
        let survives = Predicate::read_checked(reader)?;
        let mut noises = [0; 3];
        for noise in noises.iter_mut() {
            let index = reader.count(64)?;
            *noise = reader.noise(index)?;
        }
        let count = reader.count(1 << 12)?;
        let climates = (0..count)
            .map(|_| Some(Climate { precipitation: reader.flag()?, temperature: reader.float()?, frozen: reader.flag()? }))
            .collect::<Option<Vec<_>>>()?;
        Some(Self { sea_level, ice, snow, snowy, water, air_or_snow, survives, noises, climates })
    }

    fn noise(placement: &Placement, index: usize, x: f64, z: f64) -> f64 {
        placement.program.noise(index, x, z, 0.0)
    }

    /// `Biome.getHeightAdjustedTemperature` with `TemperatureModifier.modifyTemperature`.
    fn temperature(&self, placement: &Placement, climate: &Climate, pos: [i32; 3]) -> f32 {
        let (x, y, z) = (pos[0], pos[1], pos[2]);
        let mut f = climate.temperature;
        if climate.frozen {
            let d = Self::noise(placement, self.noises[1], x as f64 * 0.05, z as f64 * 0.05) * 7.0;
            let e = Self::noise(placement, self.noises[2], x as f64 * 0.2, z as f64 * 0.2);
            if d + e < 0.3 && Self::noise(placement, self.noises[2], x as f64 * 0.09, z as f64 * 0.09) < 0.8 {
                f = 0.2f32;
            }
        }
        let j = self.sea_level + 17;
        if y > j {
            let g = (Self::noise(placement, self.noises[0], (x as f32 / 8.0f32) as f64, (z as f32 / 8.0f32) as f64) * 8.0) as f32;
            f - (g + y as f32 - j as f32) * 0.05f32 / 40.0f32
        } else {
            f
        }
    }

    fn warm(&self, placement: &Placement, climate: &Climate, pos: [i32; 3]) -> bool {
        self.temperature(placement, climate, pos) >= 0.15f32
    }

    /// `isInsideBuildHeight(y) && getBrightness(LightLayer.BLOCK, pos) < 10`.
    fn dark(placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        Ok(!placement.region.outside_build_height(pos[1]) && placement.region.block_light(pos[0], pos[1], pos[2])? < 10)
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        for i in 0..16 {
            for j in 0..16 {
                let (x, z) = (origin[0] + i, origin[2] + j);
                let y = placement.region.height_by_type(MOTION_BLOCKING, x, z)?;
                let (pos, below) = ([x, y, z], [x, y - 1, z]);
                let biome = placement.region.biome(x, y, z)?;
                let climate = self.climates.get(biome as usize).ok_or(Failure::Unbound)?;
                // `shouldFreeze(level, below, false)`.
                if !self.warm(placement, climate, below) && Self::dark(placement, below)? {
                    let state = placement.region.level_block_state(below[0], below[1], below[2])?;
                    if placement.program.in_set(self.water, state) {
                        set_block(placement, below, self.ice, 2)?;
                    }
                }
                // `shouldSnow(level, pos)`.
                if climate.precipitation && !self.warm(placement, climate, pos) && Self::dark(placement, pos)? {
                    let state = placement.region.level_block_state(x, y, z)?;
                    if placement.program.in_set(self.air_or_snow, state) && placement.test(&self.survives, pos)? {
                        set_block(placement, pos, self.snow, 2)?;
                        let under = placement.region.level_block_state(below[0], below[1], below[2])?;
                        if let Some(&(_, snowy)) = self.snowy.iter().find(|(state, _)| *state == under) {
                            set_block(placement, below, snowy, 2)?;
                        }
                    }
                }
            }
        }
        Ok(true)
    }
}

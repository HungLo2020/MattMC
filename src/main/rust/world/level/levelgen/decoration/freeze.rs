//! `SnowAndFreezeFeature` (`freeze_top_layer`): per column, ice on cold water
//! and snow on cold ground. Block light comes through Java's `getBrightness`.
//! The existing `warmEnoughToRain` adapter reaches the shared native biome
//! temperature/cache service at the original `shouldFreeze`/`shouldSnow` points.
//! The cache remains per-biome/per-calling-thread and keyed by position alone;
//! preserving the adapter call order preserves shared sea-level history.
use super::placement::Placement;
use super::program::{Predicate, Reader};
use super::region::{Failure, Result};
use super::vegetation::set_block;

/// `Heightmap.Types.MOTION_BLOCKING`.
const MOTION_BLOCKING: u32 = 4;

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Freeze {
    ice: u32,
    snow: u32,
    /// Each state with `SNOWY` and its snowy state.
    snowy: Vec<(u32, u32)>,
    /// Water source fluid in a `LiquidBlock`.
    water: usize,
    air_or_snow: usize,
    /// `SnowLayerBlock.canSurvive` for the default snow layer.
    survives: Predicate,
    /// Each region biome's `hasPrecipitation`, by region biome index.
    precipitation: Vec<bool>,
}

impl Freeze {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let ice = reader.write_state()?;
        let snow = reader.write_state()?;
        let count = reader.count(1 << 12)?;
        let snowy = (0..count).map(|_| Some((reader.state()?, reader.write_state()?))).collect::<Option<Vec<_>>>()?;
        let water = reader.set()?;
        let air_or_snow = reader.set()?;
        let survives = Predicate::read_checked(reader)?;
        let count = reader.count(1 << 12)?;
        let precipitation = (0..count).map(|_| reader.flag()).collect::<Option<Vec<_>>>()?;
        Some(Self { ice, snow, snowy, water, air_or_snow, survives, precipitation })
    }

    /// `!warmEnoughToRain(pos, seaLevel)`: Java's own call (see the module docs).
    fn cold(placement: &Placement, biome: u32, pos: [i32; 3]) -> Result<bool> {
        Ok(!placement.region.warm_enough_to_rain(biome, pos[0], pos[1], pos[2])?)
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
                let precipitation = *self.precipitation.get(biome as usize).ok_or(Failure::Unbound)?;
                // `shouldFreeze(level, below, false)`: the temperature first, then light.
                if Self::cold(placement, biome, below)? && Self::dark(placement, below)? {
                    let state = placement.region.level_block_state(below[0], below[1], below[2])?;
                    if placement.program.in_set(self.water, state) {
                        set_block(placement, below, self.ice, 2)?;
                    }
                }
                // `shouldSnow(level, pos)`: `getPrecipitationAt` (precipitation, then the
                // temperature), then light.
                if precipitation && Self::cold(placement, biome, pos)? && Self::dark(placement, pos)? {
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

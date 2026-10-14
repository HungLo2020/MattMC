//! `WorldGenRegion.setBlock` over proto chunks: `ProtoChunk.setBlockState`'s
//! section write, light notifications and final heightmaps, then the region's
//! POI, block-entity and post-processing work. Java-owned effects (POI manager,
//! block-entity maps, post-processing lists, light engine, ticks) are recorded
//! in call order and replayed by Java after the call; nothing in a native
//! placement reads them back.
use super::facts::{self, POST_PROCESS_UNKNOWN};
use super::region::{Failure, Region, Result, CHUNKS};
use crate::content::block::{StateFlags, StateId};
use crate::world::level::chunk::counters;
use crate::world::level::levelgen::heightmap;

/// Recorded effects: `[kind, x, y, z, argument]`.
pub(crate) const EVENT_POI: i64 = 1;
pub(crate) const EVENT_BLOCK_ENTITY_DUMMY: i64 = 2;
pub(crate) const EVENT_BLOCK_ENTITY_REMOVE: i64 = 3;
pub(crate) const EVENT_POST_PROCESS: i64 = 4;
pub(crate) const EVENT_LIGHT_SECTION: i64 = 5;
pub(crate) const EVENT_LIGHT_CHECK: i64 = 6;
pub(crate) const EVENT_FLUID_TICK: i64 = 7;
pub(crate) const EVENT_BLOCK_TICK: i64 = 8;

/// `Heightmap.Types` ordinals of `ChunkStatus.FINAL_HEIGHTMAPS`, in enum order.
pub(crate) const FINAL_ORDINALS: [u32; 4] = [1, 3, 4, 5];

impl Region<'_> {
    fn nonempty(&self, chunk: usize, section: usize) -> Result<bool> {
        let counts = self.counters(chunk, section)?;
        Ok(counts.values()[0] != 0)
    }

    /// `WorldGenRegion.setBlock(pos, state, flags, 512)`; false when the write is refused.
    pub(crate) fn set_block(&mut self, x: i32, y: i32, z: i32, state: u32, flags: i32) -> Result<bool> {
        self.check_deferred()?;
        super::region::injected_failure()?;
        if !self.ensure_can_write(x, y, z) {
            return Ok(false);
        }
        let facts = facts::installed().ok_or(Failure::Unbound)?;
        let old = self.set_block_state(x, y, z, state)?;
        if facts.poi(old) != facts.poi(state) {
            self.event(EVENT_POI, x, y, z, ((old as i64) << 32) | state as i64);
        }
        let registry = self.registry();
        if registry.flags(StateId(state as u16)).contains(StateFlags::BLOCK_ENTITY) {
            self.event(EVENT_BLOCK_ENTITY_DUMMY, x, y, z, 0);
        } else if registry.flags(StateId(old as u16)).contains(StateFlags::BLOCK_ENTITY) {
            self.event(EVENT_BLOCK_ENTITY_REMOVE, x, y, z, 0);
        }
        match facts.post_process(state) {
            0 => {}
            1 if flags & 16 == 0 => self.event(EVENT_POST_PROCESS, x, y, z, 0),
            1 => {}
            _ => return Err(Failure::Unbound),
        }
        Ok(true)
    }

    /// `ProtoChunk.setBlockState(pos, state, flags)`; returns the previous state.
    fn set_block_state(&mut self, x: i32, y: i32, z: i32, state: u32) -> Result<u32> {
        let chunk = self.chunk_index(x >> 4, z >> 4)?;
        if self.outside_build_height(y) {
            return Ok(self.void_air());
        }
        let section = self.section_of(y);
        let was_empty = !self.nonempty(chunk, section)?;
        if was_empty && state == self.air_block_state() {
            return Ok(state);
        }
        let old = self.set_section_state(x, y, z, state)?;
        if self.light_active(chunk) {
            let empty = !self.nonempty(chunk, section)?;
            if empty != was_empty {
                self.event(EVENT_LIGHT_SECTION, x, y, z, i64::from(empty));
            }
            if self.different_light_properties(old, state)? {
                self.update_sky_sources(chunk, x, y, z)?;
                self.event(EVENT_LIGHT_CHECK, x, y, z, 0);
            }
        }
        for kind in 0..FINAL_ORDINALS.len() {
            if !self.final_present(chunk, kind) {
                self.prime_final(chunk, kind)?;
            }
        }
        for kind in 0..FINAL_ORDINALS.len() {
            self.update_final(chunk, kind, x, y, z, state)?;
        }
        Ok(old)
    }

    /// `LightEngine.hasDifferentLightProperties`.
    fn different_light_properties(&self, old: u32, new: u32) -> Result<bool> {
        if old == new {
            return Ok(false);
        }
        let facts = facts::installed().ok_or(Failure::Unbound)?;
        let registry = self.registry();
        let (a, b) = (StateId(old as u16), StateId(new as u16));
        Ok(registry.light_block(a) != registry.light_block(b)
            || registry.emission(a) != registry.emission(b)
            || facts.uses_shape_for_light_occlusion(old)
            || facts.uses_shape_for_light_occlusion(new))
    }

    /// `Heightmap.update(x, y, z, state)` on one final heightmap of a chunk.
    fn update_final(&mut self, chunk: usize, kind: usize, x: i32, y: i32, z: i32, state: u32) -> Result<()> {
        let bit = 1u32 << FINAL_ORDINALS[kind];
        let masks = heightmap::installed_masks().ok_or(Failure::Unbound)?;
        let opaque = |state: u32| -> Result<bool> {
            let mask = *masks.masks.get(state as usize).ok_or(Failure::Unbound)?;
            if mask == heightmap::CUSTOM {
                return Err(Failure::Unbound);
            }
            Ok(mask & bit != 0)
        };
        let column = ((x & 15) + (z & 15) * 16) as usize;
        let first = self.final_height(chunk, kind, column);
        if y <= first - 2 {
            return Ok(());
        }
        if opaque(state)? {
            if y >= first {
                self.set_final_height(chunk, kind, column, y + 1);
            }
        } else if first - 1 == y {
            let mut height = self.min_y;
            for below in (self.min_y..y).rev() {
                if opaque(self.chunk_block_state(chunk, x, below, z)?)? {
                    height = below + 1;
                    break;
                }
            }
            self.set_final_height(chunk, kind, column, height);
        }
        Ok(())
    }

    /// `Heightmap.primeHeightmaps(chunk, {type})` from current blocks.
    fn prime_final(&mut self, chunk: usize, kind: usize) -> Result<()> {
        let bit = 1u32 << FINAL_ORDINALS[kind];
        let heights = self.prime_heights(chunk, bit)?;
        for (column, height) in heights.iter().enumerate() {
            self.set_final_height(chunk, kind, column, height.unwrap_or(self.min_y));
        }
        self.mark_final_present(chunk, kind);
        Ok(())
    }

    /// `ChunkSkyLightSources.update(chunk, x & 15, y, z & 15)`.
    fn update_sky_sources(&mut self, chunk: usize, x: i32, y: i32, z: i32) -> Result<()> {
        if !self.sky_present(chunk) {
            return Err(Failure::Unbound);
        }
        let column = ((x & 15) + (z & 15) * 16) as usize;
        let lowest = self.sky_source(chunk, column);
        if y + 1 < lowest {
            return Ok(());
        }
        let above = self.chunk_block_state(chunk, x, y + 1, z)?;
        let at = self.chunk_block_state(chunk, x, y, z)?;
        if self.update_sky_edge(chunk, column, lowest, y + 1, above, y, at)? {
            return Ok(());
        }
        let below = self.chunk_block_state(chunk, x, y - 1, z)?;
        self.update_sky_edge(chunk, column, lowest, y, at, y - 1, below)?;
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    fn update_sky_edge(&mut self, chunk: usize, column: usize, lowest: i32, upper_y: i32, upper: u32, lower_y: i32, lower: u32) -> Result<bool> {
        if self.edge_occluded(upper, lower)? {
            if upper_y > lowest {
                self.set_sky_source(chunk, column, upper_y);
                return Ok(true);
            }
        } else if upper_y == lowest {
            let source = self.lowest_source_below(chunk, column, lower_y, lower)?;
            self.set_sky_source(chunk, column, source);
            return Ok(true);
        }
        Ok(false)
    }

    /// `ChunkSkyLightSources.findLowestSourceBelow` from `(column, y)` holding `state`.
    fn lowest_source_below(&self, chunk: usize, column: usize, y: i32, state: u32) -> Result<i32> {
        let (x, z) = ((column & 15) as i32, (column >> 4) as i32);
        let min = self.sky_min_y();
        let mut upper_y = y;
        let mut upper = state;
        let mut lower_y = y - 1;
        while lower_y >= min {
            let lower = self.chunk_block_state(chunk, x, lower_y, z)?;
            if self.edge_occluded(upper, lower)? {
                return Ok(upper_y);
            }
            upper = lower;
            upper_y = lower_y;
            lower_y -= 1;
        }
        Ok(min)
    }

    /// `ChunkSkyLightSources.isEdgeOccluded(upper, lower)`.
    fn edge_occluded(&self, upper: u32, lower: u32) -> Result<bool> {
        let tables = crate::world::level::lighting::skylight_sources::installed_tables().ok_or(Failure::Unbound)?;
        let descriptor = |state: u32| -> Result<u32> {
            let d = *tables.descriptors.get(state as usize).ok_or(Failure::Unbound)?;
            if d == crate::world::level::lighting::skylight_sources::CUSTOM {
                return Err(Failure::Unbound);
            }
            Ok(d)
        };
        let (upper, lower) = (descriptor(upper)?, descriptor(lower)?);
        if lower & 1 != 0 {
            return Ok(true);
        }
        let down = (upper >> 16) as usize;
        let up = ((lower >> 1) & 32767) as usize;
        Ok(tables.edges.get(down * tables.faces + up).copied().ok_or(Failure::Unbound)? != 0)
    }

    pub(crate) fn schedule_tick(&mut self, fluid: bool, x: i32, y: i32, z: i32, id: u32, delay: i32) {
        let kind = if fluid { EVENT_FLUID_TICK } else { EVENT_BLOCK_TICK };
        self.event(kind, x, y, z, ((id as i64) << 32) | (delay as u32 as i64));
    }

    fn counters(&self, chunk: usize, section: usize) -> Result<&counters::Owner> {
        let pointer = self.section_word(chunk, section, 1) as *const counters::Owner;
        // SAFETY: Java keeps each bound section's owners alive for the call.
        unsafe { pointer.as_ref() }.ok_or(Failure::Unbound)
    }

    /// Checked before a placement that may write through `set_block`.
    pub(crate) fn write_path_ready(&self) -> bool {
        facts::installed().is_some_and(|f| f.len() == self.registry().state_count())
            && crate::world::level::lighting::skylight_sources::installed_tables().is_some_and(|t| !t.custom)
            && heightmap::installed_masks().is_some_and(|m| !m.custom)
            && self.chunks_present() == (1 << CHUNKS) - 1
            && POST_PROCESS_UNKNOWN == 2
    }
}

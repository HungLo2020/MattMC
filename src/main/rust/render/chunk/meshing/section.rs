//! Compact all-pass section snapshot access.
//!
//! Java owns the backing arrays for one chunk section rebuild. Rust receives a
//! stable header plus raw addresses for active local indexes, padded section
//! state/light arrays, and per-block semantic data. This module is the only
//! place that converts that ABI into safe record-shaped values for the scan
//! orchestrator; production must keep using this compact snapshot path.

use super::*;

pub(super) trait NativeSectionRecordSource {
    fn record_count(&self) -> usize;

    unsafe fn state_id_at(&self, index: usize) -> Result<i32, i32> {
        Ok(self.record_at(index)?.state_id)
    }

    unsafe fn flags_at(&self, index: usize) -> Result<i32, i32> {
        Ok(self.record_at(index)?.flags)
    }

    unsafe fn model_fully_occluded_at(
        &self,
        _index: usize,
        _state: NativeMeshingState,
        _states: &[Option<NativeMeshingState>],
    ) -> Result<bool, i32> {
        Ok(false)
    }

    unsafe fn model_cull_mask_at(&self, _index: usize) -> Result<i32, i32> {
        Ok(0)
    }

    fn supports_direct_static_model_emission(&self) -> bool {
        false
    }

    unsafe fn model_record_at(&self, index: usize) -> Result<NativeSectionBlockRecord, i32> {
        self.record_at(index)
    }

    unsafe fn record_at(&self, index: usize) -> Result<NativeSectionBlockRecord, i32>;
}

pub(super) struct CompactSectionSnapshot<'a> {
    header: &'a CompactSectionSnapshotHeader,
    active_indices: &'a [u16],
    padded_state_ids: &'a [i32],
    padded_light_words: &'a [i32],
    block_ids: &'a [i32],
    seed_los: &'a [i32],
    seed_his: &'a [i32],
    tints: &'a [i32],
    tint_lattices: &'a [i32],
    fluid_tints: &'a [i32],
    fluid_flow_x: &'a [f32],
    fluid_flow_z: &'a [f32],
    fluid_block_ids: &'a [i32],
    flags: &'a [i32],
}

impl<'a> CompactSectionSnapshot<'a> {
    pub(super) unsafe fn from_address(address: u64) -> Result<Self, i32> {
        if address == 0 {
            return Err(ERR_NULL_POINTER);
        }
        let header = &*(address as *const CompactSectionSnapshotHeader);
        if header.version != COMPACT_SECTION_SNAPSHOT_VERSION
            || header.active_count < 0
            || header.active_count as usize > COMPACT_SECTION_BLOCK_COUNT
        {
            return Err(ERR_INVALID_ARGUMENT);
        }
        let active_count = header.active_count as usize;
        if header.active_indices_address == 0
            || header.padded_state_ids_address == 0
            || header.padded_light_words_address == 0
            || header.block_ids_address == 0
            || header.seed_los_address == 0
            || header.seed_his_address == 0
            || header.tints_address == 0
            || header.tint_lattices_address == 0
            || header.fluid_tints_address == 0
            || header.fluid_flow_x_address == 0
            || header.fluid_flow_z_address == 0
            || header.fluid_block_ids_address == 0
            || header.flags_address == 0
        {
            return Err(ERR_NULL_POINTER);
        }
        let padded_len = COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH
            * COMPACT_SECTION_PADDED_LENGTH;
        Ok(Self {
            header,
            active_indices: slice::from_raw_parts(
                header.active_indices_address as *const u16,
                active_count,
            ),
            padded_state_ids: slice::from_raw_parts(
                header.padded_state_ids_address as *const i32,
                padded_len,
            ),
            padded_light_words: slice::from_raw_parts(
                header.padded_light_words_address as *const i32,
                padded_len,
            ),
            block_ids: slice::from_raw_parts(
                header.block_ids_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            seed_los: slice::from_raw_parts(
                header.seed_los_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            seed_his: slice::from_raw_parts(
                header.seed_his_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            tints: slice::from_raw_parts(
                header.tints_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            tint_lattices: slice::from_raw_parts(
                header.tint_lattices_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT * 64,
            ),
            fluid_tints: slice::from_raw_parts(
                header.fluid_tints_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_flow_x: slice::from_raw_parts(
                header.fluid_flow_x_address as *const f32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_flow_z: slice::from_raw_parts(
                header.fluid_flow_z_address as *const f32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            fluid_block_ids: slice::from_raw_parts(
                header.fluid_block_ids_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
            flags: slice::from_raw_parts(
                header.flags_address as *const i32,
                COMPACT_SECTION_BLOCK_COUNT,
            ),
        })
    }

    #[inline(always)]
    pub(super) fn padded_index(x: usize, y: usize, z: usize) -> usize {
        (y * COMPACT_SECTION_PADDED_LENGTH + z) * COMPACT_SECTION_PADDED_LENGTH + x
    }

    #[inline(always)]
    unsafe fn padded_state(&self, x: usize, y: usize, z: usize) -> i32 {
        *self
            .padded_state_ids
            .get_unchecked(Self::padded_index(x, y, z))
    }

    #[inline(always)]
    unsafe fn padded_light_word(&self, x: usize, y: usize, z: usize) -> i32 {
        *self
            .padded_light_words
            .get_unchecked(Self::padded_index(x, y, z))
    }

    #[inline(always)]
    unsafe fn active_local_index(&self, index: usize) -> Result<usize, i32> {
        let local_index = *self.active_indices.get_unchecked(index) as usize;
        if local_index >= COMPACT_SECTION_BLOCK_COUNT {
            return Err(ERR_INVALID_ARGUMENT);
        }
        Ok(local_index)
    }
}

impl NativeSectionRecordSource for CompactSectionSnapshot<'_> {
    #[inline(always)]
    fn record_count(&self) -> usize {
        self.active_indices.len()
    }

    #[inline(always)]
    unsafe fn state_id_at(&self, index: usize) -> Result<i32, i32> {
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        Ok(self.padded_state(local_x + 1, local_y + 1, local_z + 1))
    }

    #[inline(always)]
    unsafe fn flags_at(&self, index: usize) -> Result<i32, i32> {
        let local_index = self.active_local_index(index)?;
        Ok(*self.flags.get_unchecked(local_index))
    }

    #[inline(always)]
    unsafe fn model_fully_occluded_at(
        &self,
        index: usize,
        state: NativeMeshingState,
        states: &[Option<NativeMeshingState>],
    ) -> Result<bool, i32> {
        if self.model_cull_mask_at(index)? == 0b11_1111 {
            return Ok(true);
        }
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        let padded_x = local_x + 1;
        let padded_y = local_y + 1;
        let padded_z = local_z + 1;
        let neighbor_ids = [
            self.padded_state(padded_x, padded_y - 1, padded_z),
            self.padded_state(padded_x, padded_y + 1, padded_z),
            self.padded_state(padded_x, padded_y, padded_z - 1),
            self.padded_state(padded_x, padded_y, padded_z + 1),
            self.padded_state(padded_x - 1, padded_y, padded_z),
            self.padded_state(padded_x + 1, padded_y, padded_z),
        ];

        for (face, neighbor_id) in neighbor_ids.into_iter().enumerate() {
            let Some(neighbor) = state_by_id(states, neighbor_id) else {
                return Ok(false);
            };
            if !state_culls_model_face(face as i32, state, neighbor) {
                return Ok(false);
            }
        }

        Ok(true)
    }

    #[inline(always)]
    unsafe fn model_cull_mask_at(&self, index: usize) -> Result<i32, i32> {
        const SEMANTIC_CULL_MASK_SHIFT: i32 = 8;
        const SEMANTIC_CULL_MASK: i32 = 0b11_1111;
        let local_index = self.active_local_index(index)?;
        Ok(
            ((*self.flags.get_unchecked(local_index)) >> SEMANTIC_CULL_MASK_SHIFT)
                & SEMANTIC_CULL_MASK,
        )
    }

    #[inline(always)]
    fn supports_direct_static_model_emission(&self) -> bool {
        true
    }

    unsafe fn model_record_at(&self, index: usize) -> Result<NativeSectionBlockRecord, i32> {
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        let padded_x = local_x + 1;
        let padded_y = local_y + 1;
        let padded_z = local_z + 1;

        let mut record = NativeSectionBlockRecord {
            state_id: self.padded_state(padded_x, padded_y, padded_z),
            block_id: *self.block_ids.get_unchecked(local_index),
            local_x: local_x as i32,
            local_y: local_y as i32,
            local_z: local_z as i32,
            seed_lo: *self.seed_los.get_unchecked(local_index),
            seed_hi: *self.seed_his.get_unchecked(local_index),
            tint: *self.tints.get_unchecked(local_index),
            fluid_tint: *self.fluid_tints.get_unchecked(local_index),
            fluid_flow_x: *self.fluid_flow_x.get_unchecked(local_index),
            fluid_flow_z: *self.fluid_flow_z.get_unchecked(local_index),
            absolute_x: self.header.min_x + local_x as i32,
            absolute_y: self.header.min_y + local_y as i32,
            absolute_z: self.header.min_z + local_z as i32,
            fluid_block_id: *self.fluid_block_ids.get_unchecked(local_index),
            flags: *self.flags.get_unchecked(local_index),
            ..NativeSectionBlockRecord::default()
        };

        record.neighbor_state_ids[0] = self.padded_state(padded_x, padded_y - 1, padded_z);
        record.neighbor_state_ids[1] = self.padded_state(padded_x, padded_y + 1, padded_z);
        record.neighbor_state_ids[2] = self.padded_state(padded_x, padded_y, padded_z - 1);
        record.neighbor_state_ids[3] = self.padded_state(padded_x, padded_y, padded_z + 1);
        record.neighbor_state_ids[4] = self.padded_state(padded_x - 1, padded_y, padded_z);
        record.neighbor_state_ids[5] = self.padded_state(padded_x + 1, padded_y, padded_z);

        let mut neighborhood_index = 0;
        for dy in 0..3 {
            for dz in 0..3 {
                for dx in 0..3 {
                    let sx = padded_x + dx - 1;
                    let sy = padded_y + dy - 1;
                    let sz = padded_z + dz - 1;
                    record.light_words[neighborhood_index] = self.padded_light_word(sx, sy, sz);
                    neighborhood_index += 1;
                }
            }
        }

        let lattice_start = local_index * 64;
        for y in 0..4 {
            for z in 0..4 {
                for x in 0..4 {
                    record.tint_lattice[y][z][x] = self.tint_lattices[lattice_start + (y * 4 + z) * 4 + x];
                }
            }
        }

        Ok(record)
    }

    unsafe fn record_at(&self, index: usize) -> Result<NativeSectionBlockRecord, i32> {
        let local_index = self.active_local_index(index)?;
        let local_x = local_index & 15;
        let local_z = (local_index >> 4) & 15;
        let local_y = (local_index >> 8) & 15;
        let padded_x = local_x + 1;
        let padded_y = local_y + 1;
        let padded_z = local_z + 1;

        let mut record = NativeSectionBlockRecord {
            state_id: self.padded_state(padded_x, padded_y, padded_z),
            block_id: *self.block_ids.get_unchecked(local_index),
            local_x: local_x as i32,
            local_y: local_y as i32,
            local_z: local_z as i32,
            seed_lo: *self.seed_los.get_unchecked(local_index),
            seed_hi: *self.seed_his.get_unchecked(local_index),
            tint: *self.tints.get_unchecked(local_index),
            fluid_tint: *self.fluid_tints.get_unchecked(local_index),
            fluid_flow_x: *self.fluid_flow_x.get_unchecked(local_index),
            fluid_flow_z: *self.fluid_flow_z.get_unchecked(local_index),
            absolute_x: self.header.min_x + local_x as i32,
            absolute_y: self.header.min_y + local_y as i32,
            absolute_z: self.header.min_z + local_z as i32,
            fluid_block_id: *self.fluid_block_ids.get_unchecked(local_index),
            flags: *self.flags.get_unchecked(local_index),
            ..NativeSectionBlockRecord::default()
        };

        record.neighbor_state_ids[0] = self.padded_state(padded_x, padded_y - 1, padded_z);
        record.neighbor_state_ids[1] = self.padded_state(padded_x, padded_y + 1, padded_z);
        record.neighbor_state_ids[2] = self.padded_state(padded_x, padded_y, padded_z - 1);
        record.neighbor_state_ids[3] = self.padded_state(padded_x, padded_y, padded_z + 1);
        record.neighbor_state_ids[4] = self.padded_state(padded_x - 1, padded_y, padded_z);
        record.neighbor_state_ids[5] = self.padded_state(padded_x + 1, padded_y, padded_z);

        let mut neighborhood_index = 0;
        for dy in 0..3 {
            for dz in 0..3 {
                for dx in 0..3 {
                    let sx = padded_x + dx - 1;
                    let sy = padded_y + dy - 1;
                    let sz = padded_z + dz - 1;
                    record.neighborhood_state_ids[neighborhood_index] =
                        self.padded_state(sx, sy, sz);
                    record.light_words[neighborhood_index] = self.padded_light_word(sx, sy, sz);
                    neighborhood_index += 1;
                }
            }
        }

        Ok(record)
    }

}

#[cfg(test)]
mod tests;

//! Terrain-source bookkeeping owned by the graph (Sodium's
//! `RenderSectionManager` state): which columns are ready, which sections
//! need a build or have one in flight, block-entity membership, animated
//! sprites per section, and the visit set of the latest camera search.
//!
//! Java's terrain source drives the meshing workers and holds the
//! `BlockEntity` objects; every decision about *which* sections to build,
//! accept, draw block entities for or treat as visible lives here.

use std::collections::HashSet;

use super::{SectionGraph, SectionInfo, VisitedSection};
use crate::render::chunk::terrain_selection::section_key;
use crate::render::vulkanic::gal::AccessHashBuilder;

type PositionSet = HashSet<[i32; 3], AccessHashBuilder>;

/// `RenderSectionFlags.HAS_BLOCK_ENTITIES` (culled block entities).
pub const FLAG_BLOCK_ENTITIES: u8 = 1 << 1;

/// Result of finishing an in-flight build.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BuildCompletion {
    /// The build is current; the caller accepts it with [`SectionGraph::accept_build`].
    Current,
    /// The section was edited, reloaded or its column unloaded meanwhile.
    Stale,
}

#[derive(Debug, Default)]
pub struct SourceState {
    columns: HashSet<[i32; 2], AccessHashBuilder>,
    needs_build: PositionSet,
    /// Rebuilds of already-built sections (block edits): requested first.
    urgent: PositionSet,
    in_flight: PositionSet,
    stale_in_flight: PositionSet,
    /// Sections with off-screen block entities, in first-build order.
    global_block_entities: Vec<i64>,
    global_block_entity_set: HashSet<i64, AccessHashBuilder>,
    /// Animated sprite ids of each built section (Java's sprite registry).
    pub(in crate::render::chunk) sprites: std::collections::HashMap<[i32; 3], Box<[u32]>, AccessHashBuilder>,
    /// Outputs of the latest camera search.
    pub build_requests: Vec<i64>,
    pub block_entity_sections: Vec<i64>,
    visit_epoch: u32,
}

impl SectionGraph {
    /// A column became ready (`onChunkAdded`): all-air sections (clear bits of
    /// `non_air_mask`, bit `y - min_section_y`) are built as empty at once,
    /// every other section needs a build. False when it was already ready.
    pub fn add_ready_column(&mut self, x: i32, z: i32, non_air_mask: u64) -> bool {
        if !self.source.columns.insert([x, z]) {
            return false;
        }
        self.add_column(x, z);
        for y in self.min_section_y..=self.max_section_y {
            let bit = (y - self.min_section_y) as u32;
            if bit < 64 && non_air_mask & (1 << bit) != 0 {
                self.source.needs_build.insert([x, y, z]);
            } else {
                self.set_info([x, y, z], Some(SectionInfo::EMPTY));
            }
        }
        true
    }

    /// A column stopped being ready. In-flight builds of it become stale.
    /// False when it was not ready.
    pub fn remove_ready_column(&mut self, x: i32, z: i32) -> bool {
        if !self.source.columns.remove(&[x, z]) {
            return false;
        }
        self.remove_column(x, z);
        let mut removed_global = false;
        for y in self.min_section_y..=self.max_section_y {
            let position = [x, y, z];
            let source = &mut self.source;
            source.needs_build.remove(&position);
            source.urgent.remove(&position);
            source.sprites.remove(&position);
            if source.in_flight.contains(&position) {
                source.stale_in_flight.insert(position);
            }
            removed_global |= source.global_block_entity_set.remove(&section_key(position));
        }
        if removed_global {
            let set = &self.source.global_block_entity_set;
            self.source.global_block_entities.retain(|key| set.contains(key));
        }
        true
    }

    pub fn column_ready(&self, x: i32, z: i32) -> bool {
        self.source.columns.contains(&[x, z])
    }

    /// Sodium's `scheduleRebuild`: only sections of ready columns matter; a
    /// built section keeps drawing until its replacement is accepted. True
    /// when the section was marked.
    pub fn schedule_rebuild(&mut self, position: [i32; 3]) -> bool {
        if position[1] < self.min_section_y
            || position[1] > self.max_section_y
            || !self.column_ready(position[0], position[2])
        {
            return false;
        }
        self.source.needs_build.insert(position);
        if self.is_built(position) {
            self.source.urgent.insert(position);
        }
        if self.source.in_flight.contains(&position) {
            self.source.stale_in_flight.insert(position);
        }
        true
    }

    /// The caller handed a requested section to a meshing worker.
    pub fn build_started(&mut self, position: [i32; 3]) {
        self.source.in_flight.insert(position);
    }

    /// A worker delivered a build; whether it is still current.
    pub fn finish_build(&mut self, position: [i32; 3]) -> BuildCompletion {
        self.source.in_flight.remove(&position);
        let stale = self.source.stale_in_flight.remove(&position);
        if stale || !self.column_ready(position[0], position[2]) {
            BuildCompletion::Stale
        } else {
            BuildCompletion::Current
        }
    }

    /// Accepts a section's newest build (including an empty one).
    pub fn accept_build(&mut self, position: [i32; 3], info: SectionInfo, global_block_entities: bool, sprites: &[u32]) {
        let source = &mut self.source;
        source.needs_build.remove(&position);
        source.urgent.remove(&position);
        if sprites.is_empty() {
            source.sprites.remove(&position);
        } else {
            source.sprites.insert(position, sprites.into());
        }
        let key = section_key(position);
        if global_block_entities {
            if source.global_block_entity_set.insert(key) {
                source.global_block_entities.push(key);
            }
        } else if source.global_block_entity_set.remove(&key) {
            source.global_block_entities.retain(|other| *other != key);
        }
        self.set_info(position, Some(info));
    }

    /// Resource reload: every built section with geometry needs a rebuild and
    /// in-flight builds are stale. `cancel_in_flight` when the worker pool was
    /// replaced (its cancelled jobs deliver nothing).
    pub fn reload_resources(&mut self, cancel_in_flight: bool) {
        if cancel_in_flight {
            self.source.in_flight.clear();
            self.source.stale_in_flight.clear();
        }
        let in_flight: Vec<_> = self.source.in_flight.iter().copied().collect();
        self.source.stale_in_flight.extend(in_flight);
        let geometry: Vec<[i32; 3]> = self
            .slots
            .iter()
            .filter(|(_, &slot)| self.section_flags(slot).is_some_and(|flags| flags != 0))
            .map(|(&position, _)| position)
            .collect();
        self.source.needs_build.extend(geometry);
    }

    /// The client loading gate: the section's current build was accepted and
    /// no rebuild is outstanding.
    pub fn section_ready(&self, position: [i32; 3]) -> bool {
        self.is_built(position)
            && !self.source.needs_build.contains(&position)
            && !self.source.in_flight.contains(&position)
    }

    pub fn needs_build_count(&self) -> usize {
        self.source.needs_build.len()
    }

    pub fn in_flight_count(&self) -> usize {
        self.source.in_flight.len()
    }

    pub fn global_block_entity_sections(&self) -> &[i64] {
        &self.source.global_block_entities
    }

    fn is_built(&self, position: [i32; 3]) -> bool {
        self.slot(position).is_some_and(|slot| self.nodes[slot as usize].info.is_some())
    }

    /// Records the camera search's visits: the build requests (block edits
    /// first, otherwise visit order; sections already in flight skipped),
    /// the visited sections with culled block entities, and the visit set
    /// that [`Self::box_visible`] tests.
    pub fn record_visits(&mut self, visits: &[VisitedSection]) {
        let epoch = self.source.visit_epoch.wrapping_add(1).max(1);
        self.source.visit_epoch = epoch;
        let source = &mut self.source;
        source.build_requests.clear();
        source.block_entity_sections.clear();
        let mut urgent = 0;
        for visit in visits {
            self.nodes[visit.slot as usize].visit_epoch = epoch;
            let position = visit.position;
            if source.needs_build.contains(&position) && !source.in_flight.contains(&position) {
                let key = section_key(position);
                if source.urgent.contains(&position) {
                    source.build_requests.insert(urgent, key);
                    urgent += 1;
                } else {
                    source.build_requests.push(key);
                }
            }
            if visit.info.is_some_and(|info| info.flags & FLAG_BLOCK_ENTITIES != 0) {
                source.block_entity_sections.push(section_key(position));
            }
        }
    }

    /// Sodium's `isBoxVisible`: whether any section overlapping the
    /// inclusive section-coordinate box was visited by the latest search.
    pub fn box_visible(&self, min: [i32; 3], max: [i32; 3]) -> bool {
        let epoch = self.source.visit_epoch;
        if epoch == 0 {
            return false;
        }
        for x in min[0]..=max[0] {
            for z in min[2]..=max[2] {
                for y in min[1]..=max[1] {
                    if self.slot([x, y, z]).is_some_and(|slot| self.nodes[slot as usize].visit_epoch == epoch) {
                        return true;
                    }
                }
            }
        }
        false
    }
}

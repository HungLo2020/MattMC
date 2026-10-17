//! Native-owned `PoiManager.DistanceTracker`: section distances to the nearest
//! village centre and an exact mirror of `PoiManager.isVillageCenter`.
use super::graph::{DistanceField, Error, SourceLevels};
use super::position_map::PositionMap;

/// Sections whose POI section currently holds an occupied village POI.
pub(crate) struct VillageCentres {
    present: PositionMap<u8>,
}

impl VillageCentres {
    fn set(&mut self, position: i64, centre: bool) -> Result<(), Error> {
        if centre {
            self.present.insert(position, 1)?;
        } else {
            self.present.remove(position);
        }
        Ok(())
    }
}

impl SourceLevels for VillageCentres {
    /// `DistanceTracker.getLevelFromSource`.
    fn level_from_source(&self, position: i64) -> i32 {
        if self.present.contains(position) {
            0
        } else {
            7
        }
    }
}

pub(crate) struct PoiDistance {
    centres: VillageCentres,
    field: DistanceField,
}

impl PoiDistance {
    pub fn new() -> Result<Self, Error> {
        Ok(Self {
            centres: VillageCentres {
                present: PositionMap::with_expected(16)?,
            },
            field: DistanceField::poi()?,
        })
    }

    /// Records whether a section is a village centre without scheduling work.
    pub fn seed(&mut self, position: i64, centre: bool) -> Result<(), Error> {
        self.centres.set(position, centre)
    }

    /// `setDirty`/`onSectionLoad`: `update(pos, getLevelFromSource(pos), false)`
    /// with the section's current village-centre state.
    pub fn section_changed(&mut self, position: i64, centre: bool) -> Result<(), Error> {
        self.centres.set(position, centre)?;
        let level = self.centres.level_from_source(position);
        self.field.update(&self.centres, position, level, false)
    }

    /// Drops every recorded centre: the caller reseeds the current ones.
    pub fn clear_centres(&mut self) -> Result<(), Error> {
        self.centres.present = PositionMap::with_expected(16)?;
        Ok(())
    }

    pub fn run_updates(&mut self, budget: i32) -> Result<i32, Error> {
        self.field.run_updates(&self.centres, budget)
    }

    pub fn field(&self) -> &DistanceField {
        &self.field
    }

    pub fn field_mut(&mut self) -> &mut DistanceField {
        &mut self.field
    }
}

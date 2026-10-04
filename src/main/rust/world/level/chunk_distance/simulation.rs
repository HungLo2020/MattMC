//! Native-owned `SimulationChunkTracker`: the simulation distance graph and
//! an exact mirror of `TicketStorage.getTicketLevelAt(pos, true)`.
use super::graph::{DistanceField, Error, SourceLevels};
use super::position_map::PositionMap;

/// Lowest simulating ticket level per chunk; absent chunks report
/// `ChunkLevel.MAX_LEVEL + 1`, as `getTicketLevelAt` does without a ticket.
pub(crate) struct TicketLevels {
    levels: PositionMap<i32>,
    absent: i32,
}

impl TicketLevels {
    fn set(&mut self, position: i64, level: i32) -> Result<(), Error> {
        if level == self.absent {
            self.levels.remove(position);
        } else {
            self.levels.insert(position, level)?;
        }
        Ok(())
    }
}

impl SourceLevels for TicketLevels {
    fn level_from_source(&self, position: i64) -> i32 {
        self.levels.get(position).unwrap_or(self.absent)
    }
}

pub(crate) struct SimulationDistance {
    tickets: TicketLevels,
    field: DistanceField,
}

impl SimulationDistance {
    pub fn new(absent_ticket_level: i32) -> Result<Self, Error> {
        Ok(Self {
            tickets: TicketLevels {
                levels: PositionMap::with_expected(16)?,
                absent: absent_ticket_level,
            },
            field: DistanceField::simulation()?,
        })
    }

    /// Records a chunk's current ticket level without touching the graph:
    /// tickets that already existed when the tracker attached.
    pub fn seed(&mut self, position: i64, ticket_level: i32) -> Result<(), Error> {
        self.tickets.set(position, ticket_level)
    }

    /// The ticket listener's `update(pos, level, decrease)`. `ticket_level`
    /// is the storage's current level there, which the original graph reads lazily.
    pub fn update(
        &mut self,
        position: i64,
        ticket_level: i32,
        level: i32,
        decrease: bool,
    ) -> Result<(), Error> {
        self.tickets.set(position, ticket_level)?;
        self.field.update(&self.tickets, position, level, decrease)
    }

    pub fn run_updates(&mut self, budget: i32) -> Result<i32, Error> {
        self.field.run_updates(&self.tickets, budget)
    }

    pub fn field(&self) -> &DistanceField {
        &self.field
    }

    pub fn field_mut(&mut self) -> &mut DistanceField {
        &mut self.field
    }
}

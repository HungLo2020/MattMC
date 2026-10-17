//! Native-owned ticket distance trackers (`SimulationChunkTracker`,
//! `LoadingChunkTracker`): a distance graph and an exact mirror of
//! `TicketStorage.getTicketLevelAt(pos, simulate)` for the tracker's ticket kind.
use super::graph::{DistanceField, Error, SourceLevels};
use super::position_map::PositionMap;
use std::sync::{Mutex, MutexGuard};

/// Lowest ticket level of the tracker's kind per chunk; absent chunks report
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

pub(crate) struct TicketDistance {
    tickets: TicketLevels,
    field: DistanceField,
}

impl TicketDistance {
    pub fn new(absent_ticket_level: i32, field: DistanceField) -> Result<Self, Error> {
        Ok(Self {
            tickets: TicketLevels {
                levels: PositionMap::with_expected(16)?,
                absent: absent_ticket_level,
            },
            field,
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

/// One graph's synchronization domain. Java's adapter and the ticket storage
/// that notifies it directly both lock it for every access, including reads;
/// neither calls Java while holding it. The last of the adapter's and the
/// storages' references frees it.
pub(crate) struct SharedTicketDistance(Mutex<TicketDistance>);

impl SharedTicketDistance {
    pub fn new(distance: TicketDistance) -> Self {
        Self(Mutex::new(distance))
    }

    pub fn lock(&self) -> MutexGuard<'_, TicketDistance> {
        // A panic cannot unwind across the C ABI, so the lock is never poisoned
        // with a half-applied update that Java could observe.
        self.0.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
    }
}

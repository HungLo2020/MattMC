//! Native-owned chunk/section distance trackers: `DistanceManager`'s natural
//! spawn counter and player-ticket graphs with their shared player-presence
//! source, the simulation and loading ticket graphs with ticket level mirrors,
//! and `PoiManager`'s village section distances with a village-centre mirror. Each owns levels, pending computed levels and work queues.
//! Java keeps only published level views rebuilt from each run's ordered changes.
mod ffi;
pub(crate) mod graph;
mod position_map;
mod poi;
mod poi_ffi;
pub(crate) mod ticket;
mod ticket_ffi;
#[cfg(test)]
mod tests;

use graph::{DistanceField, Error, Players};

pub(crate) struct PlayerDistances {
    players: Players,
    fields: [DistanceField; 2],
}

impl PlayerDistances {
    pub fn new(natural_spawn_distance: i32, player_ticket_distance: i32) -> Result<Self, Error> {
        Ok(Self {
            players: Players::new()?,
            fields: [
                DistanceField::fixed_player(natural_spawn_distance)?,
                DistanceField::fixed_player(player_ticket_distance)?,
            ],
        })
    }

    /// `DistanceManager.addPlayer`: the chunk now holds a player, then each
    /// field receives `update(pos, 0, true)` in the original order.
    pub fn player_entered(&mut self, position: i64) -> Result<(), Error> {
        self.players.insert(position)?;
        for field in &mut self.fields {
            field.update(&self.players, position, 0, true)?;
        }
        Ok(())
    }

    /// `DistanceManager.removePlayer` once the chunk's player set is empty:
    /// each field receives `update(pos, Integer.MAX_VALUE, false)`.
    pub fn chunk_vacated(&mut self, position: i64) -> Result<(), Error> {
        self.players.remove(position);
        for field in &mut self.fields {
            field.update(&self.players, position, i32::MAX, false)?;
        }
        Ok(())
    }

    pub fn run_updates(&mut self, field: usize, budget: i32) -> Result<i32, Error> {
        self.fields[field].run_updates(&self.players, budget)
    }

    pub fn field(&self, field: usize) -> &DistanceField {
        &self.fields[field]
    }

    pub fn field_mut(&mut self, field: usize) -> &mut DistanceField {
        &mut self.fields[field]
    }
}

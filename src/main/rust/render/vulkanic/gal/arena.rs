//! Generational handle arenas and the records and pending destroys they hold.

use super::*;

#[derive(Clone, Debug)]
pub(super) struct Slot<T> {
    pub(super) generation: u32,
    pub(super) last_destroyed_generation: Option<u32>,
    pub(super) value: Option<T>,
}

#[derive(Clone, Debug)]
pub(super) struct Arena<T> {
    pub(super) kind: HandleKind,
    pub(super) slots: Vec<Slot<T>>,
}

impl<T> Arena<T> {
    #[allow(dead_code)]
    pub(super) fn new(kind: HandleKind) -> Self {
        Self {
            kind,
            slots: Vec::new(),
        }
    }

    pub(super) fn next_handle(&self) -> GalResult<Handle> {
        for (index, slot) in self.slots.iter().enumerate() {
            if slot.value.is_none() {
                let index = u32::try_from(index).map_err(|_| {
                    GalError::handle(
                        StatusCode::GenerationExhausted,
                        "handle index space exhausted",
                    )
                })?;
                return Handle::new(self.kind, index, slot.generation);
            }
        }
        if self.slots.len() >= MAX_ARENA_SLOTS {
            return Err(GalError::handle(
                StatusCode::GenerationExhausted,
                format!("{} handle arena slots exhausted", self.kind as u8),
            ));
        }
        let index = u32::try_from(self.slots.len()).map_err(|_| {
            GalError::handle(
                StatusCode::GenerationExhausted,
                "handle index space exhausted",
            )
        })?;
        Handle::new(self.kind, index, 1)
    }

    pub(super) fn insert_at(&mut self, handle: Handle, value: T) -> GalResult<Handle> {
        let (index, generation) = handle.require_kind(self.kind)?;
        if index == self.slots.len() {
            if self.slots.len() >= MAX_ARENA_SLOTS {
                return Err(GalError::handle(
                    StatusCode::GenerationExhausted,
                    format!("{} handle arena slots exhausted", self.kind as u8),
                ));
            }
            self.slots.push(Slot {
                generation,
                last_destroyed_generation: None,
                value: Some(value),
            });
            return Ok(handle);
        }
        let slot = self.slots.get_mut(index).ok_or_else(|| {
            GalError::handle(StatusCode::StaleHandle, "handle slot does not exist")
        })?;
        if slot.generation != generation || slot.value.is_some() {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot is not available for insertion",
            ));
        }
        slot.last_destroyed_generation = None;
        slot.value = Some(value);
        Ok(handle)
    }

    #[track_caller]
    pub(super) fn get(&self, handle: Handle) -> GalResult<&T> {
        let caller = std::panic::Location::caller();
        let (index, generation) = handle.require_kind(self.kind)?;
        let Some(slot) = self.slots.get(index) else {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot does not exist",
            ));
        };
        if slot.generation != generation {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                format!(
                    "stale handle generation kind={:?} index={} requested_generation={} live_generation={} lookup={}:{}",
                    self.kind, index, generation, slot.generation, caller.file(), caller.line()
                ),
            ));
        }
        slot.value.as_ref().ok_or_else(|| {
            if slot.last_destroyed_generation == Some(generation) {
                GalError::handle(StatusCode::DoubleDestroy, "resource was already destroyed")
            } else {
                GalError::handle(StatusCode::StaleHandle, "resource is not live")
            }
        })
    }

    pub(super) fn remove(&mut self, handle: Handle) -> GalResult<T> {
        let (index, generation) = handle.require_kind(self.kind)?;
        let Some(slot) = self.slots.get_mut(index) else {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot does not exist",
            ));
        };
        if slot.generation != generation {
            if slot.last_destroyed_generation == Some(generation) {
                return Err(GalError::handle(
                    StatusCode::DoubleDestroy,
                    "resource was already destroyed",
                ));
            }
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                format!(
                    "stale handle generation kind={:?} index={} requested_generation={} live_generation={}",
                    self.kind, index, generation, slot.generation
                ),
            ));
        }
        let value = slot.value.take().ok_or_else(|| {
            GalError::handle(StatusCode::DoubleDestroy, "resource was already destroyed")
        })?;
        if slot.generation == MAX_GENERATION {
            slot.value = Some(value);
            return Err(GalError::handle(
                StatusCode::GenerationExhausted,
                "resource generation exhausted",
            ));
        }
        slot.last_destroyed_generation = Some(slot.generation);
        slot.generation += 1;
        Ok(value)
    }

    #[cfg(test)]
    pub(super) fn force_generation(&mut self, handle: Handle, generation: u32) {
        let index = handle.index() as usize;
        self.slots[index].generation = generation;
    }
}

#[derive(Clone, Debug)]
pub(super) struct ResourceRecord<T> {
    pub(super) desc: T,
    pub(super) token: BackendToken,
    pub(super) last_submission: Option<SubmissionId>,
}

#[derive(Clone, Copy, Debug)]
pub(super) struct PendingDestroy {
    pub(super) kind: HandleKind,
    pub(super) token: BackendToken,
}

pub(super) trait ArenaRecordExt<T> {
    fn get_mut_record(&mut self, handle: Handle) -> GalResult<&mut ResourceRecord<T>>;
}

impl<T> ArenaRecordExt<T> for Arena<ResourceRecord<T>> {
    fn get_mut_record(&mut self, handle: Handle) -> GalResult<&mut ResourceRecord<T>> {
        let (index, generation) = handle.require_kind(self.kind)?;
        let Some(slot) = self.slots.get_mut(index) else {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot does not exist",
            ));
        };
        if slot.generation != generation {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "stale handle generation",
            ));
        }
        slot.value
            .as_mut()
            .ok_or_else(|| GalError::handle(StatusCode::StaleHandle, "resource is not live"))
    }
}

#[cfg(test)]
mod arena_tests {
    use super::*;

    #[test]
    fn arena_rejects_growth_at_the_explicit_slot_bound() {
        let mut arena = Arena::<()>::new(HandleKind::Buffer);
        arena.slots.resize_with(MAX_ARENA_SLOTS, || Slot {
            generation: 1,
            last_destroyed_generation: None,
            value: Some(()),
        });
        let handle = Handle::new(HandleKind::Buffer, MAX_ARENA_SLOTS as u32, 1).unwrap();
        let error = arena.insert_at(handle, ()).unwrap_err();
        assert_eq!(StatusCode::GenerationExhausted, error.code);
    }
}

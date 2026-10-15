//! Player-loading decisions between the native distance graph and ticket/task
//! owners. Walks yield before every Java effect; no guard crosses a callback.
//! Actual tickets, holder futures and executor mechanics remain Java effects.
use std::sync::{Arc, Mutex, MutexGuard};

use super::ticket_storage::map::{Cursor, ListMap, NONE};
use super::ticket_storage::Fault;

pub(crate) const DONE: i32 = 0;
pub(crate) const RESORT: i32 = 1;
pub(crate) const ENTER: i32 = 2;
pub(crate) const LEAVE: i32 = 3;
pub(crate) const HAS_TICKET: i32 = 4;
pub(crate) const ATTACH: i32 = 5;
pub(crate) const KEY: i32 = 6;

pub(crate) struct State {
    levels: ListMap,
    changed: ListMap,
    queue_levels: ListMap,
    releases: ListMap,
    max_distance: i32,
    view_distance: i32,
}

pub(crate) struct SharedState(Mutex<State>);

impl SharedState {
    pub(crate) fn new(max_distance: i32) -> Result<Self, Fault> {
        Ok(Self(Mutex::new(State {
            levels: ListMap::with_expected(16)?,
            changed: ListMap::with_expected(16)?,
            queue_levels: ListMap::with_expected(16)?,
            releases: ListMap::with_expected(16)?,
            max_distance,
            view_distance: 0,
        })))
    }

    pub(crate) fn lock(&self) -> MutexGuard<'_, State> {
        self.0.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    pub(crate) fn retained_bytes(&self) -> usize {
        let state = self.lock();
        std::mem::size_of::<Self>() + state.levels.allocation_bytes()
            + state.changed.allocation_bytes() + state.queue_levels.allocation_bytes()
            + state.releases.allocation_bytes()
    }
}

impl State {
    pub(crate) fn apply(&mut self, changes: &[(i64, i32)]) -> Result<(), Fault> {
        for &(key, level) in changes {
            if level > self.max_distance { self.levels.remove(key)?; }
            else { self.levels.put(key, level as u8 as u32)?; }
            // onLevelChange runs even when a value happened to be unchanged.
            self.changed.put(key, 0)?;
        }
        Ok(())
    }

    fn level(&self, key: i64) -> i32 {
        let value = self.levels.get(key);
        if value == NONE { (self.max_distance + 2) as u8 as i8 as i32 }
        else { value as u8 as i8 as i32 }
    }

    pub(crate) fn queue_level(&self, key: i64) -> i32 {
        let value = self.queue_levels.get(key);
        if value == NONE { self.max_distance + 2 } else { value as i32 }
    }

    pub(crate) fn set_queue_level(&mut self, key: i64, level: i32) -> Result<(), Fault> {
        if level >= self.max_distance + 2 { self.queue_levels.remove(key)?; }
        else { self.queue_levels.put(key, level as u32)?; }
        Ok(())
    }

    pub(crate) fn admits(&self, key: i64) -> bool { self.level(key) <= self.view_distance }

    pub(crate) fn mark_release(&mut self, key: i64) -> Result<(), Fault> {
        self.releases.put(key, 0)?;
        Ok(())
    }
}

/// The public throttling dispatcher also works independently of a player tracker.
/// Its native admission component owns distinct positions, not task counts.
pub(crate) struct Admission {
    in_flight: ListMap,
    maximum: i32,
}

pub(crate) struct SharedAdmission(Mutex<Admission>);

impl SharedAdmission {
    pub(crate) fn new(maximum: i32) -> Result<Self, Fault> {
        Ok(Self(Mutex::new(Admission { in_flight: ListMap::with_expected(16)?, maximum })))
    }

    fn lock(&self) -> MutexGuard<'_, Admission> {
        self.0.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
    }

    pub(crate) fn may_pop(&self) -> bool {
        let state = self.lock();
        (state.in_flight.len() as i64) < i64::from(state.maximum)
    }

    pub(crate) fn started(&self, key: i64) -> Result<(), Fault> {
        self.lock().in_flight.put(key, 0)?;
        Ok(())
    }

    pub(crate) fn released(&self, key: i64) -> Result<(), Fault> {
        self.lock().in_flight.remove(key)?;
        Ok(())
    }

    pub(crate) fn retained_bytes(&self) -> usize {
        std::mem::size_of::<Self>() + self.lock().in_flight.allocation_bytes()
    }
}

#[derive(Clone)]
enum Source {
    Loading(Arc<SharedState>),
    Admission(Arc<SharedAdmission>),
}

#[derive(Clone, Copy)]
enum Phase {
    Next,
    AfterResort { key: i64, previous: i32, level: i32 },
    TicketAnswer { key: i64 },
}

/// Numeric output belongs to this invocation until its next step or release.
/// Separate nested walks retain independent cursors, phase and output storage.
pub(crate) struct Walk {
    source: Source,
    cursor: Cursor,
    kind: i32,
    target: i32,
    phase: Phase,
    stream_position: usize,
    stream_end: usize,
    stream_zero: bool,
    pub(crate) output: [i64; 4],
}

impl Walk {
    pub(crate) fn loading(owner: Arc<SharedState>, kind: i32, target: i32) -> Self {
        let cursor = {
            let state = owner.lock();
            match kind { 0 => &state.changed, 1 => &state.levels, _ => &state.releases }.cursor()
        };
        Self { source: Source::Loading(owner), cursor, kind, target, phase: Phase::Next,
            stream_position: 0, stream_end: 0, stream_zero: false, output: [0; 4] }
    }

    pub(crate) fn admission(owner: Arc<SharedAdmission>) -> Self {
        let (cursor, stream_end, stream_zero) = {
            let state = owner.lock();
            (state.in_flight.cursor(), state.in_flight.table_len() - 1, state.in_flight.get(0) != NONE)
        };
        Self { source: Source::Admission(owner), cursor, kind: 3, target: 0, phase: Phase::Next,
            stream_position: 0, stream_end, stream_zero, output: [0; 4] }
    }

    fn emit(&mut self, status: i32, key: i64, level: i32) -> i32 {
        self.output[0] = key;
        self.output[1] = i64::from(level);
        status
    }

    fn fault(&mut self, error: Fault, map: &ListMap) -> i32 {
        let position = self.cursor.mark().position();
        match error {
            Fault::ArrayIndex => self.emit(10, i64::from(position), map.table_len() as i32),
            Fault::NullPointer => 11,
            Fault::IndexOutOfBounds => {
                let size = self.cursor.wrapped().as_ref().map_or(0, Vec::len);
                self.emit(12, i64::from(-position - 1), size as i32)
            }
            Fault::Allocation => -3,
            _ => -14,
        }
    }

    fn transition(&mut self, key: i64, level: i32, before: bool, after: bool) -> Option<i32> {
        if before == after { None }
        else { Some(self.emit(if after { ENTER } else { LEAVE }, key, level)) }
    }

    pub(crate) fn next(&mut self, answer: i32) -> i32 {
        // Clone the Arc so the guard borrows a local, not this invocation's
        // mutable phase/output. It is released before any Java effect runs.
        match self.source.clone() {
            Source::Admission(owner) => {
                let state = owner.lock();
                // getDebugStatus uses LongOpenHashSet.longStream(), whose
                // SetSpliterator walks upward, unlike its descending iterator.
                if self.stream_zero {
                    self.stream_zero = false;
                    return self.emit(KEY, 0, 0);
                }
                while self.stream_position < self.stream_end {
                    let index = self.stream_position;
                    self.stream_position += 1;
                    match state.in_flight.key_at(index as i32) {
                        Ok(0) => {},
                        Ok(key) => return self.emit(KEY, key, 0),
                        Err(_) => return self.emit(10, index as i64, state.in_flight.table_len() as i32),
                    }
                }
                DONE
            }
            Source::Loading(owner) => {
                let mut state = owner.lock();
                match self.phase {
                    Phase::AfterResort { key, previous, level } => {
                        self.phase = Phase::Next;
                        if let Some(event) = self.transition(key, level,
                            previous <= state.view_distance, level <= state.view_distance) { return event; }
                    }
                    Phase::TicketAnswer { key } => {
                        if !(0..=1).contains(&answer) { return -14; }
                        self.phase = Phase::Next;
                        if answer == 1 { return self.emit(ATTACH, key, 0); }
                    }
                    Phase::Next => {}
                }
                loop {
                    if !self.cursor.has_next() {
                        match self.kind {
                            0 => state.changed.clear_membership(),
                            1 => state.view_distance = self.target,
                            _ => state.releases.clear_membership(),
                        }
                        return DONE;
                    }
                    let map = match self.kind { 0 => &state.changed, 1 => &state.levels, _ => &state.releases };
                    let index = match self.cursor.next_entry(map) {
                        Ok(index) => index,
                        Err(error) => return self.fault(error, map),
                    };
                    let key = match map.key_at(index) {
                        Ok(key) => key,
                        Err(error) => return self.fault(error, map),
                    };
                    match self.kind {
                        0 => {
                            let previous = state.queue_level(key);
                            let level = state.level(key);
                            if previous != level {
                                self.phase = Phase::AfterResort { key, previous, level };
                                return self.emit(RESORT, key, level);
                            }
                        }
                        1 => {
                            let level = match state.levels.value_at(index) {
                                Ok(value) => value as u8 as i8 as i32,
                                Err(error) => return self.fault(error, &state.levels),
                            };
                            if let Some(event) = self.transition(key, level,
                                level <= state.view_distance, level <= self.target) { return event; }
                        }
                        _ => {
                            self.phase = Phase::TicketAnswer { key };
                            return self.emit(HAS_TICKET, key, 0);
                        }
                    }
                }
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn decisions_follow_delayed_resort_and_view_commit() {
        let owner = Arc::new(SharedState::new(32).unwrap());
        owner.lock().apply(&[(0, 0)]).unwrap();
        let mut updates = Walk::loading(owner.clone(), 0, 0);
        assert_eq!(updates.next(-1), RESORT);
        // The executor may change the view before onLevelChange returns.
        let mut view = Walk::loading(owner.clone(), 1, -1);
        assert_eq!(view.next(-1), LEAVE);
        assert!(owner.lock().admits(0), "the view commits after its live scan");
        assert_eq!(view.next(-1), DONE);
        assert_eq!(updates.next(-1), DONE, "transition compares the current view");
        assert!(!owner.lock().admits(0));
    }

    #[test]
    fn release_clear_waits_for_all_effects_and_walk_keeps_owner_alive() {
        let owner = Arc::new(SharedState::new(32).unwrap());
        owner.lock().mark_release(0).unwrap();
        let mut walk = Walk::loading(owner.clone(), 2, 0);
        drop(owner);
        assert_eq!(walk.next(-1), HAS_TICKET);
        assert_eq!(walk.next(1), ATTACH);
        assert_eq!(walk.next(-1), DONE);
        let Source::Loading(owner) = &walk.source else { panic!() };
        assert_eq!(owner.lock().releases.len(), 0);
    }

    #[test]
    fn admission_counts_distinct_positions_and_accepts_original_limits() {
        let owner = Arc::new(SharedAdmission::new(1).unwrap());
        assert!(owner.may_pop());
        owner.started(0).unwrap();
        owner.started(0).unwrap();
        assert!(!owner.may_pop());
        owner.released(0).unwrap();
        assert!(owner.may_pop());
        assert!(!SharedAdmission::new(0).unwrap().may_pop());
        assert!(!SharedAdmission::new(-1).unwrap().may_pop());
    }
}

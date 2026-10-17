//! Live consumers of actual VM Ticket resources. Cursor state and inclusion /
//! short-circuit decisions stay native; every observable getter is a VM effect.
use super::{map::{Cursor, NONE}, Fault, ListOwner, ListRef, TicketStorage};
use std::sync::{Arc, Mutex};

#[derive(Clone, Copy, PartialEq)]
enum Phase { Next, BindList, Tickets, Answer }

#[repr(C)]
pub(super) struct Consumer {
    command: i64,
    key: i64,
    argument: i64,
    storage: Arc<Mutex<TicketStorage>>,
    mode: i32, // keep-active, persisted pairs, replace-type pairs, forced keys
    cursor: Cursor,
    index: i32,
    list: ListRef,
    owned_list: Option<ListOwner>,
    position: usize,
    entry_key: i64,
    active: bool,
    phase: Phase,
}
impl Consumer {
    fn new(storage: Arc<Mutex<TicketStorage>>, mode: i32) -> Self {
        let cursor = storage.lock().unwrap_or_else(|p| p.into_inner()).tickets.cursor();
        Self { command: 0, key: 0, argument: 0, storage, mode, cursor, index: 0,
            list: u64::MAX, owned_list: None, position: 0, entry_key: 0, active: true, phase: Phase::Next }
    }
    fn step(&mut self, answer: i32) {
        let effect: Result<(i64, i64, i64), Fault> = (|| {
            let storage = self.storage.lock().unwrap_or_else(|p| p.into_inner());
            if self.phase == Phase::Answer {
                self.phase = Phase::Tickets;
                if answer != 0 {
                    match self.mode {
                        0 => return Ok((8, 0, 1)),
                        1 => return Ok((6, self.entry_key, 0)),
                        2 => return Ok((10, storage.tickets.key_at(self.index)?, 0)),
                        _ => {
                            self.phase = Phase::Next; // original breaks this entry's loop
                            return Ok((7, storage.tickets.key_at(self.index)?, 0));
                        }
                    }
                }
            }
            loop {
                match self.phase {
                    Phase::Next => {
                        if !self.cursor.has_next() {
                            if self.mode == 1 && self.active {
                                self.active = false;
                                self.cursor = storage.deactivated.cursor();
                                continue;
                            }
                            return Ok((8, 0, 0));
                        }
                        let map = if self.active { &storage.tickets } else { &storage.deactivated };
                        self.index = self.cursor.next_entry(map)?;
                        let index = map.value_at(self.index)?;
                        if index == NONE { return Err(Fault::NullPointer); }
                        self.list = storage.reference(index);
                        self.owned_list = Some(storage.list_owner(self.list).ok_or(Fault::IllegalState)?.clone());
                        self.position = 0;
                        self.phase = Phase::BindList;
                        if self.mode == 1 {
                            self.entry_key = map.key_at(self.index)?;
                            return Ok((1, self.entry_key, 0)); // original per-entry ChunkPos
                        }
                    }
                    Phase::BindList => {
                        self.phase = Phase::Tickets;
                        return Ok((11, 0, self.list as i64));
                    }
                    Phase::Tickets => {
                        let data = self.owned_list.as_ref().ok_or(Fault::IllegalState)?.lock();
                        let entries = &data.entries;
                        if self.position >= entries.len() { self.phase = Phase::Next; continue; }
                        let entry = entries[self.position];
                        self.position += 1;
                        self.phase = Phase::Answer;
                        return Ok((2 + self.mode as i64, 0, entry.slot as i64));
                    }
                    Phase::Answer => return Err(Fault::IllegalState),
                }
            }
        })();
        let (command, key, argument) = match effect {
            Ok(effect) => effect,
            Err(fault) => (9, 0, match fault {
                Fault::Allocation => 3, Fault::Queue => 6, Fault::NullPointer => 10,
                Fault::ArrayIndex => 11, Fault::IndexOutOfBounds => 12,
                Fault::NoSuchElement => 13, Fault::IllegalState => 14,
            }),
        };
        // The storage lock is gone before a VM query or output effect runs.
        self.command = command; self.key = key; self.argument = argument;
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_consumer_create(id: u64, mode: i32) -> u64 {
    if !(0..=3).contains(&mode) { return 0; }
    let state = Consumer::new(super::ffi::retain_storage(id), mode);
    let pointer = std::alloc::alloc(std::alloc::Layout::new::<Consumer>()).cast::<Consumer>();
    if pointer.is_null() { return 0; }
    pointer.write(state);
    pointer.expose_provenance() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_consumer_step(id: u64, answer: i32) {
    (&mut *std::ptr::with_exposed_provenance_mut::<Consumer>(id as usize)).step(answer);
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_consumer_release(id: u64) {
    if id != 0 { drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<Consumer>(id as usize))); }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::world::level::chunk_distance::ticket_storage::{Entry, WalkKind, Event, LOADING, SIMULATION};
    fn store() -> Arc<Mutex<TicketStorage>> {
        let mut storage = TicketStorage::new(45).unwrap();
        storage.add(7, Entry { slot: 0, type_id: 1, level: 33, flags: LOADING | SIMULATION, timeout: 0 }, false, 0).unwrap();
        Arc::new(Mutex::new(storage))
    }
    #[test]
    fn persisted_output_keeps_its_entry_position_across_query_headers() {
        let mut consumer = Consumer::new(store(), 1);
        assert_eq!(std::mem::offset_of!(Consumer, command), 0);
        assert_eq!(std::mem::offset_of!(Consumer, key), 8);
        assert_eq!(std::mem::offset_of!(Consumer, argument), 16);
        consumer.step(0); assert_eq!((consumer.command, consumer.key), (1, 7));
        consumer.step(0); assert_eq!(consumer.command, 11);
        consumer.step(0); assert_eq!(consumer.command, 3);
        consumer.step(1); assert_eq!((consumer.command, consumer.key), (6, 7));
        consumer.step(0); assert_eq!(consumer.command, 8);
    }
    #[test]
    fn replacement_reads_the_live_entry_key_after_its_type_query() {
        let storage = store();
        let mut consumer = Consumer::new(storage.clone(), 2);
        consumer.step(0); assert_eq!(consumer.command, 11);
        consumer.step(0); assert_eq!(consumer.command, 4);
        let expected = {
            let mut storage = storage.lock().unwrap();
            storage.remove(7, 1, 33, LOADING | SIMULATION).unwrap();
            storage.tickets.key_at(consumer.index).unwrap()
        };
        consumer.step(1); assert_eq!((consumer.command, consumer.key), (10, expected));
    }
    #[test]
    fn removed_getters_and_dirty_are_effects_before_map_retirement() {
        let storage = store();
        let mut storage = storage.lock().unwrap();
        let mut walk = storage.begin(WalkKind::JavaEffects);
        assert_eq!(storage.step(&mut walk, 0, 1).unwrap(), Event::Batch { count: 1 });
        assert_eq!(storage.step(&mut walk, 1, 1).unwrap(), Event::RemovedQuery { which: 0 });
        assert!(storage.has_tickets());
        assert_eq!(storage.step(&mut walk, 0, 1).unwrap(), Event::RemovedQuery { which: 1 });
        assert_eq!(storage.step(&mut walk, 1, 1).unwrap(), Event::RemovedQuery { which: 2 });
        assert_eq!(storage.step(&mut walk, 0, 1).unwrap(), Event::LiveNotify { listener: 1, key: 7 });
        assert_eq!(storage.step(&mut walk, 0, 1).unwrap(), Event::Dirty);
        assert!(storage.has_tickets(), "map entry retired before the actual dirty effect");
        assert_eq!(storage.step(&mut walk, 0, 1).unwrap(), Event::Done { forced: false });
        assert!(!storage.has_tickets());
    }
    #[test]
    fn null_list_slot_never_retains_or_releases_a_counter() {
        let mut storage = TicketStorage::new(45).unwrap();
        let list = storage.get_or_create(7).unwrap();
        let null = Entry { slot: u32::MAX, type_id: u32::MAX, level: 0, flags: 0, timeout: 0 };
        storage.list_insert(list, 0, null, true, 0).unwrap();
        assert_eq!(storage.entries(list)[0].slot, u32::MAX);
        assert_eq!(storage.list_remove(list, 0).unwrap(), u32::MAX);
        assert!(storage.entries(list).is_empty());
    }
    #[test]
    fn source_entry_ownership_is_independent_for_a_shared_counter_resource() {
        use crate::world::level::chunk_distance::ticket_storage::{roots, slab};
        let cell = slab::reserve_resource(7).unwrap();
        let entry = Entry { slot: cell, type_id: 1, level: 33, flags: LOADING, timeout: 0 };
        let mut first = TicketStorage::new(45).unwrap(); first.roots.enable(1001).unwrap();
        let mut second = TicketStorage::new(45).unwrap(); second.roots.enable(1002).unwrap();
        let a = first.get_or_create(7).unwrap(); let b = second.get_or_create(9).unwrap();
        first.list_insert(a,0,entry,true,0).unwrap();second.list_insert(b,0,entry,true,0).unwrap();
        let first_source=first.list_data(a).unwrap().roots.source();
        assert_eq!(slab::references(cell),2);
        first.list_remove(a,0).unwrap();assert_eq!(first.list_data(a).unwrap().roots.count(cell),0);assert_eq!(second.list_data(b).unwrap().roots.count(cell),1);
        let mut out=[0;32];let n=roots::drain(&mut out);
        assert!(out[..n*2].chunks_exact(2).any(|pair|pair==[first_source as i64,cell as i64]));
        assert_eq!(slab::references(cell),1);
        second.list_remove(b,0).unwrap();slab::release_resource(cell);
        let mut retired=vec![0;100_000];let n=slab::drain(&mut retired);
        assert!(retired[..n as usize].contains(&(cell as i64)));
    }
    #[test]
    fn consumer_keeps_the_physical_list_across_map_retirement_and_live_repopulation() {
        let storage = store();
        let mut consumer = Consumer::new(storage.clone(), 0);
        consumer.step(0); assert_eq!(consumer.command, 11);
        consumer.step(0); assert_eq!(consumer.command, 2);
        let entry = {
            let mut source = storage.lock().unwrap();
            let entry = source.entries(consumer.list)[0];
            super::super::slab::retain(entry.slot);
            super::super::slab::retain(entry.slot);
            source.remove(7, 1, 33, LOADING | SIMULATION).unwrap();
            assert!(!source.is_live(consumer.list));
            consumer.owned_list.as_ref().unwrap().lock().entries.extend([entry, entry]);
            entry
        };
        consumer.step(0);
        assert_eq!((consumer.command, consumer.argument), (2, entry.slot as i64), "retired routing slot cannot end the captured list walk");
        consumer.step(1); assert_eq!((consumer.command, consumer.argument), (8, 1));
    }

}

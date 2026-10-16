//! Admission control only. Actual Ticket/List/listener resources stay in the VM;
//! no native reference is retained across an effect. Values here are the live
//! results of the original getters, never constructor-metadata policy shortcuts.
#[repr(C)]
pub(super) struct Admission {
    command: i64,
    stored_level: i32,
    simulation: i32,
    loading: i32,
    reserved: i32,
}
impl Admission {
    fn new() -> Self { Self { command: 1, stored_level: 0, simulation: 0, loading: 0, reserved: 0 } }
    fn next(&mut self, answer: i32) {
        self.command = match self.command {
            1 => 2, // get/create actual list, then its iterator
            2 => 3,
            3 => if answer != 0 { 4 } else { 11 },
            4 => 5, // next actual stored Ticket
            5 => 6, // actual stored type, then incoming type
            6 => 7,
            7 => if answer != 0 { 8 } else { 3 }, // VM identity comparison
            8 => { self.stored_level = answer; 9 },
            9 => if answer == self.stored_level { 10 } else { 3 },
            10 => 30, // actual stored reset, then dirty
            11 => { self.simulation = answer; 12 },
            12 => { self.loading = answer; 13 },
            13 => 14, // append before debug/getter effects
            14 => 15,
            15 => if answer != 0 { 16 } else { 19 },
            16 => if answer < self.simulation { 17 } else { 19 },
            17 => if answer != 0 { 18 } else { 19 },
            18 => 19,
            19 => if answer != 0 { 20 } else { 23 },
            20 => if answer < self.loading { 21 } else { 23 },
            21 => if answer != 0 { 22 } else { 23 },
            22 => 23,
            23 => if answer != 0 { 24 } else { 31 },
            24 => 31,
            30 => 32, // duplicate -> false
            31 => 33, // added -> true
            value => value,
        };
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_admission_init(state: *mut Admission) { state.write(Admission::new()); }
#[no_mangle]
pub unsafe extern "C" fn mattmc_ticket_admission_next(state: *mut Admission, answer: i32) { (*state).next(answer); }
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn actual_duplicate_queries_stop_at_stored_reset() {
        let mut a=Admission::new();
        for (op, answer) in [(1,0),(2,0),(3,1),(4,0),(5,0),(6,0),(7,1),(8,i32::MIN),(9,i32::MIN),(10,0),(30,0)] {
            assert_eq!(a.command,op); a.next(answer);
        }
        assert_eq!(a.command,32);
    }
    #[test]
    fn admission_keeps_both_full_width_minima_and_short_circuits() {
        let mut a=Admission::new();
        for (op, answer) in [(1,0),(2,0),(3,0),(11,i32::MIN),(12,i32::MAX),(13,0),(14,0),(15,1),(16,i32::MIN),(19,1),(20,0),(21,1),(22,0),(23,0),(31,0)] {
            assert_eq!(a.command,op); a.next(answer);
        }
        assert_eq!(a.command,33);
        assert_eq!(std::mem::size_of::<Admission>(),24);
    }
}

/// Purge's predicate retains the actual Ticket and holder in Java. The native
/// command owns short-circuit order; -1/-2 are false/true terminal answers.
#[no_mangle]
pub extern "C" fn mattmc_ticket_expiry_next(command: i32, answer: i32) -> i32 {
    match command {
        0 => 1,
        1 => if answer != 0 { 2 } else { -1 },
        2 => if answer != 0 { 5 } else { 3 },
        3 => if answer != 0 { 5 } else { 4 },
        4 => if answer != 0 { 5 } else { -1 },
        5 => 6,
        6 => if answer != 0 { -2 } else { -1 },
        value => value,
    }
}

/// Removal needs one captured getter value; the immutable command carries all
/// of its native state between VM effects, including arbitrary signed levels.
#[no_mangle]
pub extern "C" fn mattmc_ticket_removal_next(command: u64, answer: i32) -> u64 {
    let next = match command as u8 {
        0 => 1,
        1 => if answer != 0 { 2 } else { 30 },
        2 => 3,
        3 => if answer != 0 { 4 } else { 30 },
        4 => 5,
        5 => 6,
        6 => 7,
        7 => if answer != 0 { 8 } else { 3 },
        8 => return ((answer as u32 as u64) << 32) | 9,
        9 => if answer == (command >> 32) as u32 as i32 { 10 } else { 3 },
        10 => 11,
        11 => 12,
        12 => if answer != 0 { 13 } else { 14 },
        13 => 14,
        14 => if answer != 0 { 15 } else { 17 },
        15 => if answer != 0 { 16 } else { 17 },
        16 => 17,
        17 => if answer != 0 { 18 } else { 20 },
        18 => if answer != 0 { 19 } else { 20 },
        19 => 20,
        20 => if answer != 0 { 21 } else { 22 },
        21 => 22,
        22 => 31,
        value => value,
    };
    next as u64
}

/// One invocation's actual list cursor. Native list membership and cursor
/// progression share the source owner; Java retains actual Ticket resources.
/// A retired native list switches to its genuine detached VM backing at the
/// original iterator query, preserving the same position rather than restarting.
#[repr(C)]
pub(super) struct LiveCursor {
    command: u64,
    cell: i32,
    last: i32,
    list: u64,
    position: u64,
    word: u64,
    admission: Admission,
    mode: i32,
    reserved: i32,
}
const SELECT_PREVIOUS: u64 = 1 << 32;
const CAPTURE_NATIVE: u64 = 1 << 33;
impl LiveCursor {
    /// mode0 admission by key; mode1/2 minima by actual native list capability;
    /// mode3 removal by key. The VM resource owner is fenced by every caller.
    pub(super) fn begin(store: &mut super::TicketStorage, value: u64, mode: i32) -> Result<Self, super::Fault> {
        let list = match mode { 0 => store.get_or_create(value as i64)?, 3 => store.tickets_at(value as i64), _ => value };
        let mut cursor = Self { command: 0, cell: -1, last: -1, list, position: 0,
            word: match mode { 0 => 2, 3 => if list == u64::MAX { 30 } else { 2 },
                _ => super::ffi::mattmc_ticket_query_begin(i32::from(mode == 1), i32::from(list != u64::MAX)) },
            admission: Admission::new(), mode, reserved: 0 };
        if mode == 0 { cursor.admission.command = 2; }
        cursor.drive(store);
        Ok(cursor)
    }
    fn raw(&self) -> u64 { if self.mode == 0 { self.admission.command as u64 } else { self.word } }
    fn advance(&mut self, answer: i64) {
        match self.mode {
            0 => self.admission.next(answer as i32),
            3 => self.word = mattmc_ticket_removal_next(self.word, answer as i32),
            _ => self.word = super::ffi::mattmc_ticket_query_next(self.word, answer),
        }
    }
    fn iterator_test(&self) -> bool { matches!(self.raw() as u8, 3) && matches!(self.mode, 0 | 3) || self.raw() as u8 == 2 && matches!(self.mode, 1 | 2) }
    fn iterator_next(&self) -> bool { self.raw() as u8 == if matches!(self.mode, 0 | 3) { 4 } else { 3 } }
    fn drive(&mut self, store: &super::TicketStorage) {
        let mut flags = self.command & SELECT_PREVIOUS;
        loop {
            let raw = self.raw() as u8;
            // Iterator construction has no escaping resource: its actual cursor
            // is already initialized in this native invocation.
            if raw == if matches!(self.mode, 0 | 3) { 2 } else { 1 } {
                self.advance(0);
            } else if self.iterator_test() {
                if !store.is_live(self.list) { self.command = flags | 200; return; }
                self.advance(i64::from(self.position < store.entries(self.list).len() as u64));
            } else if self.iterator_next() {
                if !store.is_live(self.list) { self.command = flags | 201; self.last = self.position as i32; return; }
                // This follows a successful native hasNext with no VM effect
                // between them. No source guard survives this function.
                let entry = store.entries(self.list)[self.position as usize];
                self.last = self.position as i32;
                self.position += 1;
                self.cell = entry.slot as i32;
                flags |= CAPTURE_NATIVE;
                self.advance(0);
            } else if matches!(self.mode, 1 | 2) && raw == 8 {
                flags |= SELECT_PREVIOUS;
                self.advance(0);
            } else {
                self.command = flags | match (self.mode, raw) {
                    (0 | 3, 5) => 105, // ordered actual type pair + VM identity
                    (0 | 3, 8) => 108, // ordered actual full-width level pair
                    (0, 10) => 110, // actual reset, actual dirty, return false
                    (1 | 2, 4) => 104, // candidate level, then actual best level
                    _ => raw as u64,
                };
                return;
            }
        }
    }
    pub(super) fn step(&mut self, store: &super::TicketStorage, answer: i64) {
        let command = self.command as u32;
        self.command = 0;
        match command {
            200 => self.advance(i64::from(self.position < answer as u64)),
            201 => { self.last = self.position as i32; self.position += 1; self.advance(0); },
            105 => { self.advance(0); self.advance(0); self.advance(answer); },
            108 | 104 => { self.advance((answer >> 32) as i32 as i64); self.advance(answer as i32 as i64); },
            _ => self.advance(answer),
        }
        self.drive(store);
    }
}
#[cfg(test)]
mod live_cursor_tests {
    use super::*;
    fn entry(level: i32) -> super::super::Entry {
        super::super::Entry { slot: 0, type_id: 1, level, flags: 6, timeout: 10 }
    }
    fn source() -> (super::super::TicketStorage, u64) {
        let mut store = super::super::TicketStorage::new(45).unwrap();
        let list = store.get_or_create(17).unwrap();
        store.list_insert(list, 0, entry(33), false, 10).unwrap();
        store.list_insert(list, 1, entry(5), false, 10).unwrap();
        (store, list)
    }
    #[test]
    fn native_cursor_queries_candidate_and_best_before_the_next_type() {
        let (mut store, list) = source();
        let mut cursor = LiveCursor::begin(&mut store, list, 2).unwrap();
        assert_eq!(cursor.command as u32, 7);
        assert_ne!(cursor.command & CAPTURE_NATIVE, 0);
        cursor.step(&store, 1);
        assert_eq!(cursor.command as u32, 104);
        assert_ne!(cursor.command & SELECT_PREVIOUS, 0);
        assert_eq!(cursor.last, 1);
        // Candidate loses: the original short-circuits before its type getter.
        cursor.step(&store, (5i64 << 32) | 1);
        assert_eq!(cursor.command as u32, 10);
        assert_eq!(std::mem::size_of::<LiveCursor>(), 72);
    }
    #[test]
    fn retired_source_cursor_continues_in_actual_vm_backing_at_its_position() {
        let (mut store, list) = source();
        let mut cursor = LiveCursor::begin(&mut store, 17, 0).unwrap();
        assert_eq!(cursor.command as u32, 105);
        let index = store.tickets.remove(17).unwrap();
        store.retire(index);
        let reused = store.get_or_create(18).unwrap();
        assert_ne!(list, reused);
        store.list_insert(reused, 0, entry(5), false, 10).unwrap();
        cursor.step(&store, 0);
        assert_eq!(cursor.command as u32, 200);
        cursor.step(&store, 2); // live VM detached backing has two elements
        assert_eq!(cursor.command as u32, 201);
        assert_eq!(cursor.last, 1);
        cursor.step(&store, 0); // actual VM next captured a Ticket
        assert_eq!(cursor.command as u32, 105);
        assert_eq!(cursor.command & CAPTURE_NATIVE, 0);
        cursor.step(&store, 1);
        assert_eq!(cursor.command as u32, 108);
        cursor.step(&store, (5i64 << 32) | 5);
        assert_eq!(cursor.command as u32, 110);
    }
    #[test]
    fn source_cursor_observes_reentrant_live_list_shift_without_restarting() {
        let (mut store, list) = source();
        store.list_insert(list, 2, entry(7), false, 10).unwrap();
        let expected = store.entries(list)[2].slot;
        let mut cursor = LiveCursor::begin(&mut store, 17, 3).unwrap();
        store.list_remove(list, 0).unwrap();
        cursor.step(&store, 0);
        assert_eq!(cursor.command as u32, 105);
        assert_eq!(cursor.cell as u32, expected);
        assert_eq!(cursor.last, 1);
        cursor.step(&store, 1);
        cursor.step(&store, (7i64 << 32) | 7);
        assert_eq!(cursor.command as u32, 10);
        assert_eq!(cursor.last, 1);
    }
}

/// Actual source-owned cursor allocations. Slots retain only native primitives;
/// genuine bounded VM views live with their source and never anchor a source.
#[repr(C)]
struct CursorSlot {
    cursor: LiveCursor,
    generation: u64,
    index: usize,
    active: bool,
}
#[derive(Default)]
pub(super) struct CursorPool {
    slots: Vec<Option<Box<CursorSlot>>>,
    free: Vec<usize>,
}
impl CursorPool {
    fn reserve(&mut self) -> Result<Box<CursorSlot>, super::Fault> {
        while let Some(index) = self.free.pop() {
            let mut slot = self.slots[index].take().ok_or(super::Fault::IllegalState)?;
            if slot.generation == u64::MAX { self.slots[index] = Some(slot); continue; }
            slot.generation += 1;
            slot.cursor = LiveCursor::blank();
            slot.active = true;
            return Ok(slot);
        }
        self.slots.try_reserve(1).map_err(|_| super::Fault::Allocation)?;
        let wanted = self.slots.len() + 1;
        if self.free.capacity() < wanted {
            self.free.try_reserve_exact(wanted - self.free.len()).map_err(|_| super::Fault::Allocation)?;
        }
        let layout = std::alloc::Layout::new::<CursorSlot>();
        let pointer = unsafe { std::alloc::alloc(layout).cast::<CursorSlot>() };
        if pointer.is_null() { return Err(super::Fault::Allocation); }
        let index = self.slots.len();
        unsafe { pointer.write(CursorSlot { cursor: LiveCursor::blank(), generation: 1, index, active: true }); }
        self.slots.push(None);
        Ok(unsafe { Box::from_raw(pointer) })
    }
    fn take(&mut self, address: u64, generation: u64) -> Result<Box<CursorSlot>, super::Fault> {
        // The genuine source owner keeps all allocated slot addresses live.
        let pointer = std::ptr::with_exposed_provenance::<CursorSlot>(address as usize);
        let index = unsafe { (*pointer).index };
        let candidate = self.slots.get_mut(index).ok_or(super::Fault::IllegalState)?;
        let slot = candidate.as_ref().ok_or(super::Fault::IllegalState)?;
        if std::ptr::from_ref(slot.as_ref()).expose_provenance() as u64 != address || slot.generation != generation || !slot.active {
            return Err(super::Fault::IllegalState);
        }
        Ok(candidate.take().unwrap())
    }
    fn put(&mut self, slot: Box<CursorSlot>) {
        let index = slot.index;
        self.slots[index] = Some(slot);
    }
    fn release(&mut self, mut slot: Box<CursorSlot>) {
        slot.active = false;
        self.free.push(slot.index); // capacity prepared before publication
        self.put(slot);
    }
}
impl LiveCursor {
    fn blank() -> Self {
        Self { command: 0, cell: -1, last: -1, list: u64::MAX, position: 0,
            word: 0, admission: Admission::new(), mode: -1, reserved: 0 }
    }
}
impl super::TicketStorage {
    pub(super) fn reserve_cursor(&mut self) -> Result<u64, super::Fault> {
        let slot = self.cursors.reserve()?;
        let address = std::ptr::from_ref(slot.as_ref()).expose_provenance() as u64;
        self.cursors.put(slot);
        Ok(address)
    }
    pub(super) fn open_cursor(&mut self, address: u64, generation: u64, value: u64, mode: i32) -> Result<(), super::Fault> {
        let mut slot = self.cursors.take(address, generation)?;
        // Taking the Box out of the source prevents overlapping references to
        // cursor state and the source during advancement. No VM effect occurs.
        let result = LiveCursor::begin(self, value, mode);
        match result {
            Ok(cursor) => { slot.cursor = cursor; self.cursors.put(slot); Ok(()) },
            Err(fault) => { self.cursors.put(slot); Err(fault) },
        }
    }
    pub(super) fn step_cursor(&mut self, address: u64, generation: u64, answer: i64) -> Result<(), super::Fault> {
        let mut slot = self.cursors.take(address, generation)?;
        slot.cursor.step(self, answer);
        self.cursors.put(slot);
        Ok(())
    }
    pub(super) fn release_cursor(&mut self, address: u64, generation: u64) -> Result<(), super::Fault> {
        let slot = self.cursors.take(address, generation)?;
        self.cursors.release(slot);
        Ok(())
    }
    pub(super) fn release_unadopted_cursor(&mut self, address: u64) -> Result<(), super::Fault> {
        let pointer = std::ptr::with_exposed_provenance::<CursorSlot>(address as usize);
        let generation = unsafe { (*pointer).generation };
        let slot = self.cursors.take(address, generation)?;
        // This operation is only for failed VM view construction, before open.
        if slot.cursor.mode != -1 { self.cursors.put(slot); return Err(super::Fault::IllegalState); }
        self.cursors.release(slot);
        Ok(())
    }
}
#[cfg(test)]
mod cursor_pool_tests {
    use super::*;
    fn generation(address: u64) -> u64 { unsafe { (*std::ptr::with_exposed_provenance::<CursorSlot>(address as usize)).generation } }
    #[test]
    fn source_owned_reservations_are_distinct_under_reentry_and_reuse_is_incarnated() {
        let mut source = super::super::TicketStorage::new(45).unwrap();
        let first = source.reserve_cursor().unwrap(); let a = generation(first);
        let nested = source.reserve_cursor().unwrap(); let b = generation(nested);
        assert_ne!(first, nested);
        source.open_cursor(first, a, 17, 0).unwrap();
        source.open_cursor(nested, b, 19, 0).unwrap();
        source.release_cursor(nested, b).unwrap();
        assert_eq!(source.release_cursor(nested, b), Err(super::super::Fault::IllegalState));
        let reused = source.reserve_cursor().unwrap();let c=generation(reused);
        assert_eq!(nested, reused);assert_ne!(b, c);
        assert_eq!(source.open_cursor(reused,b,21,0),Err(super::super::Fault::IllegalState));
        assert_eq!(source.step_cursor(reused,b,0),Err(super::super::Fault::IllegalState));
        source.release_unadopted_cursor(reused).unwrap();
        source.release_cursor(first,a).unwrap();
        assert_eq!(source.cursors.slots.len(),2);
        assert!(source.cursors.free.capacity()>=source.cursors.slots.len());
    }
    #[test]
    fn failed_vm_adoption_rolls_back_reservation_before_source_publication() {
        let mut source=super::super::TicketStorage::new(45).unwrap();
        let address=source.reserve_cursor().unwrap();
        assert!(!source.has_tickets());
        source.release_unadopted_cursor(address).unwrap();
        assert!(!source.has_tickets());
        let reused=source.reserve_cursor().unwrap();assert_eq!(address,reused);
        source.release_unadopted_cursor(reused).unwrap();
        assert_eq!(source.release_unadopted_cursor(reused),Err(super::super::Fault::IllegalState));
    }
    #[test]
    fn exhausted_cursor_incarnation_never_wraps_or_reuses_the_old_binding() {
        let mut source=super::super::TicketStorage::new(45).unwrap();
        let address=source.reserve_cursor().unwrap();let token=generation(address);
        source.release_cursor(address,token).unwrap();
        source.cursors.slots[0].as_mut().unwrap().generation=u64::MAX;
        let replacement=source.reserve_cursor().unwrap();assert_ne!(replacement,address);
        assert_eq!(generation(address),u64::MAX);
        source.release_unadopted_cursor(replacement).unwrap();
    }
}

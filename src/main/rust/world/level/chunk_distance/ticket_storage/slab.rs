//! Process-wide `Ticket.ticksLeft` cells. A Java `Ticket` stored in any
//! storage is bound to one cell for the rest of its life, so every entry,
//! every storage and the Java object share one counter, as they shared the
//! field. Cells live in pages that are never freed or moved; Java reads and
//! writes a bound ticket's counter at its address.
//!
//! Lifetime: `refs` counts storage entries. When it reaches zero the cell is
//! queued (globally) so Java can drop the storage's strong reference to the
//! ticket; a later store retains it again and Java re-pins it. The cell itself
//! is freed only by the ticket's Java cleaner, once the ticket is unreachable,
//! so no thread can still read or write it and no storage holds it. Any thread
//! may drain the queue or free a cell; nothing depends on thread affinity.
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicI64, AtomicPtr, AtomicU32, Ordering};
use std::sync::Mutex;

use super::Fault;

const PAGE_BITS: u32 = 12;
const PAGE: usize = 1 << PAGE_BITS;
const PAGES: usize = 4096;

#[repr(C, align(16))]
pub(crate) struct Cell {
    pub(crate) ticks_left: AtomicI64,
    refs: AtomicU32,
    /// Set while the slot sits in the released queue, so it is queued once.
    queued: AtomicBool,
    historical: AtomicI64,
    bound_slot: AtomicI32,
    identity: AtomicU32,
    owners: AtomicU32,
    managed: AtomicBool,
    live: AtomicBool,
}

static PAGE_TABLE: [AtomicPtr<Cell>; PAGES] = [const { AtomicPtr::new(std::ptr::null_mut()) }; PAGES];
static PENDING: AtomicBool = AtomicBool::new(false);

struct Free {
    next: u32,
    free: Vec<u32>,
    /// Cells whose entry count reached zero, for Java to unpin.
    released: Vec<u32>,
}

static FREE: Mutex<Free> = Mutex::new(Free { next: 0, free: Vec::new(), released: Vec::new() });

fn lock() -> std::sync::MutexGuard<'static, Free> {
    FREE.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// The cell of an allocated slot.
pub(crate) fn cell(slot: u32) -> &'static Cell {
    let page = PAGE_TABLE[(slot >> PAGE_BITS) as usize].load(Ordering::Acquire);
    debug_assert!(!page.is_null());
    unsafe { &*page.add(slot as usize & (PAGE - 1)) }
}

/// Address of a page's first cell, or 0 when it was never allocated.
pub(crate) fn page_address(page: u32) -> u64 {
    PAGE_TABLE.get(page as usize).map_or(0, |p| p.load(Ordering::Acquire).expose_provenance() as u64)
}

/// A new cell holding `ticks_left` with one reference.
pub(crate) fn bind(ticks_left: i64) -> Result<u32, Fault> {
    allocate(ticks_left, 1)
}

/// A Ticket resource reservation, not yet published in any storage entry.
pub(crate) fn reserve(ticks_left: i64) -> Result<u32, Fault> {
    allocate(ticks_left, 0)
}

fn allocate(ticks_left: i64, refs: u32) -> Result<u32, Fault> {
    let mut free = lock();
    let slot = match free.free.pop() {
        Some(slot) => slot,
        None => {
            let slot = free.next;
            let page = (slot >> PAGE_BITS) as usize;
            if page >= PAGES {
                return Err(Fault::Allocation);
            }
            // Room for every slot ever handed out, so release and free never allocate.
            let total = free.next as usize + 1;
            let (free_len, released_len) = (free.free.len(), free.released.len());
            free.free.try_reserve(total - free_len).map_err(|_| Fault::Allocation)?;
            free.released.try_reserve(total - released_len).map_err(|_| Fault::Allocation)?;
            if PAGE_TABLE[page].load(Ordering::Acquire).is_null() {
                let layout = std::alloc::Layout::array::<Cell>(PAGE).map_err(|_| Fault::Allocation)?;
                let cells = unsafe { std::alloc::alloc_zeroed(layout) }.cast::<Cell>();
                if cells.is_null() {
                    return Err(Fault::Allocation);
                }
                PAGE_TABLE[page].store(cells, Ordering::Release);
            }
            free.next += 1;
            slot
        }
    };
    let cell = cell(slot);
    cell.ticks_left.store(ticks_left, Ordering::Relaxed);
    cell.refs.store(refs, Ordering::Relaxed);
    cell.historical.store(ticks_left, Ordering::Relaxed);
    cell.bound_slot.store(-1, Ordering::Relaxed);
    cell.identity.store(slot, Ordering::Relaxed);
    cell.owners.store(0, Ordering::Relaxed);
    cell.managed.store(false, Ordering::Relaxed);
    cell.live.store(true, Ordering::Release);
    Ok(slot)
}

pub(crate) fn retain(slot: u32) {
    if slot == u32::MAX { return; } // null is a list resource, not a counter cell
    let _free = lock();
    cell(slot).refs.fetch_add(1, Ordering::Relaxed);
}

/// Drops one entry's reference; the last queues the cell for Java to unpin.
pub(crate) fn release(slot: u32) {
    if slot == u32::MAX { return; }
    let mut free = lock();
    let cell = cell(slot);
    if cell.refs.fetch_sub(1, Ordering::AcqRel) == 1 && !cell.queued.swap(true, Ordering::AcqRel) {
        free.released.push(slot);
        PENDING.store(true, Ordering::Release);
    }
}

pub(crate) fn pending() -> bool {
    PENDING.load(Ordering::Acquire)
}

/// Takes the queue, writing each queued cell that has no entries now; Java
/// unpins those while holding its binding lock, which every re-pin also takes,
/// so a store that retains one meanwhile pins it again afterwards. `out` must
/// hold the whole queue (a fixed bound: a cell is queued at most once), else
/// nothing is taken and the negated queue length is returned; otherwise the
/// count written.
pub(crate) fn drain(out: &mut [i64]) -> i32 {
    let mut free = lock();
    if free.released.len() > out.len() {
        return -(free.released.len().min(i32::MAX as usize) as i32);
    }
    let mut index = 0;
    let released = std::mem::take(&mut free.released);
    for &slot in released.iter() {
        let cell = cell(slot);
        cell.queued.store(false, Ordering::Release);
        // Entry and resource lifecycle changes share this lock. A zero-ref
        // cell is returned before Java clears its old strong binding.
        if cell.refs.load(Ordering::Acquire) == 0 {
            out[index] = slot as i64;
            index += 1;
            if cell.managed.load(Ordering::Acquire) && cell.owners.load(Ordering::Acquire) == 0 {
                cell.live.store(false, Ordering::Release);
                free.free.push(slot);
            }
        }
    }
    free.released = released;
    free.released.clear();
    PENDING.store(false, Ordering::Release);
    index as i32
}

/// Frees an unreachable ticket's cell for reuse. Without entries (a storage
/// entry would have kept the ticket reachable) nothing reads it any more; a
/// cell still referenced is kept rather than reused.
pub(crate) fn free(slot: u32) -> bool {
    free_locked(&mut lock(), slot)
}

fn free_locked(free: &mut Free, slot: u32) -> bool {
    let cell = cell(slot);
    if !cell.live.load(Ordering::Acquire) || cell.refs.load(Ordering::Acquire) != 0 || cell.owners.load(Ordering::Acquire) != 0 {
        return false;
    }
    cell.live.store(false, Ordering::Release);
    if cell.queued.swap(false, Ordering::AcqRel) {
        free.released.retain(|&queued| queued != slot);
    }
    free.free.push(slot);
    true
}

/// Actual entry ownership, used to bind the resource even after a notification
/// failure that happened after the entry was published.
pub(crate) fn references(slot: u32) -> u32 {
    cell(slot).refs.load(Ordering::Acquire)
}

/// Primary counter resources own their cell independently of storage entries.
pub(crate) fn reserve_resource(initial: i64) -> Result<u32, Fault> {
    let slot = reserve(initial)?;
    let cell = cell(slot);
    cell.owners.store(1, Ordering::Relaxed);
    cell.managed.store(true, Ordering::Release);
    Ok(slot)
}

pub(crate) fn adopt_resource(slot: u32) -> Result<u32, Fault> {
    let _free = lock();
    let cell = cell(slot);
    if !cell.live.load(Ordering::Acquire) { return Err(Fault::IllegalState); }
    cell.owners.fetch_update(Ordering::AcqRel, Ordering::Acquire, |n| n.checked_add(1)).map_err(|_| Fault::Allocation)?;
    cell.managed.store(true, Ordering::Release);
    Ok(slot)
}

pub(crate) fn release_resource(slot: u32) {
    // Claim the last owner and its retirement under the same lock as entry
    // release, draining and slot reuse. A queued cell must still reach Java's
    // unpin pass even when its previous resource has already been replaced.
    let mut free = lock();
    let cell = cell(slot);
    if cell.owners.fetch_update(Ordering::AcqRel, Ordering::Acquire, |n| n.checked_sub(1)) == Ok(1)
        && cell.refs.load(Ordering::Acquire) == 0 {
        #[cfg(test)] cleanup_pause::pause(slot);
        if !cell.queued.load(Ordering::Acquire) { free_locked(&mut free, slot); }
    }
}

pub(crate) fn resource_address(slot: u32) -> u64 {
    (cell(slot) as *const Cell).expose_provenance() as u64
}

/// Caller fences its actual owning resource through the complete native call.
pub(crate) unsafe fn resource<'a>(address: u64) -> &'a Cell {
    &*std::ptr::with_exposed_provenance::<Cell>(address as usize)
}

impl Cell {
    pub(crate) fn set(&self, value: i64) {
        self.ticks_left.store(value, Ordering::Relaxed);
        if self.bound_slot.load(Ordering::Acquire) < 0 { self.historical.store(value, Ordering::Relaxed); }
    }
    pub(crate) fn decrease(&self) {
        let value = self.ticks_left.fetch_sub(1, Ordering::Relaxed).wrapping_sub(1);
        if self.bound_slot.load(Ordering::Acquire) < 0 { self.historical.store(value, Ordering::Relaxed); }
    }
    pub(crate) fn bind_resource(&self, slot: i32) { self.bound_slot.store(slot, Ordering::Release); }
}

#[cfg(test)]
mod resource_tests {
    use super::*;
    #[test]
    fn counter_resource_layout_and_history_match_the_vm_binding() {
        assert_eq!(std::mem::align_of::<Cell>(), 16);
        assert_eq!(std::mem::size_of::<Cell>(), 48);
        assert_eq!(std::mem::offset_of!(Cell, ticks_left), 0);
        assert_eq!(std::mem::offset_of!(Cell, historical), 16);
        assert_eq!(std::mem::offset_of!(Cell, bound_slot), 24);
        assert_eq!(std::mem::offset_of!(Cell, identity), 28);
        let slot = reserve_resource(i64::MIN).unwrap();
        let value = cell(slot);
        assert_eq!(value.bound_slot.load(Ordering::Relaxed), -1);
        value.decrease();
        assert_eq!(value.ticks_left.load(Ordering::Relaxed), i64::MAX);
        assert_eq!(value.historical.load(Ordering::Relaxed), i64::MAX);
        value.bind_resource(slot as i32);
        value.set(7); value.decrease();
        assert_eq!(value.ticks_left.load(Ordering::Relaxed), 6);
        assert_eq!(value.historical.load(Ordering::Relaxed), i64::MAX);
        release_resource(slot);
        assert!(!value.live.load(Ordering::Relaxed));
    }
    #[test]
    fn last_entry_drains_a_resource_replaced_while_the_ticket_is_retained() {
        let slot = reserve_resource(19).unwrap();
        adopt_resource(slot).unwrap(); retain(slot);
        release_resource(slot); release_resource(slot);
        assert_eq!(cell(slot).owners.load(Ordering::Relaxed), 0);
        assert!(cell(slot).live.load(Ordering::Relaxed));
        release(slot);
        let mut out = vec![0; 100_000];
        let count = drain(&mut out);
        assert!(count > 0 && out[..count as usize].contains(&(slot as i64)));
        assert!(!cell(slot).live.load(Ordering::Relaxed));
    }
}

#[cfg(test)]
mod cleanup_pause {
    use super::*;
    static TARGET: AtomicU32 = AtomicU32::new(u32::MAX);
    static READY: std::sync::Barrier = std::sync::Barrier::new(2);
    static RESUME: std::sync::Barrier = std::sync::Barrier::new(2);
    pub(super) fn pause(slot: u32) {
        if TARGET.load(Ordering::Acquire) == slot { READY.wait(); RESUME.wait(); }
    }
    #[test]
    fn last_owner_cleanup_cannot_free_a_later_reservation() {
        let slot = reserve_resource(19).unwrap(); retain(slot); release(slot);
        TARGET.store(slot, Ordering::Release);
        let freeing = std::thread::spawn(move || release_resource(slot));
        READY.wait();
        let unlocked = match FREE.try_lock() { Ok(guard) => { drop(guard); true }, Err(_) => false };
        let later = if unlocked {
            let mut out = vec![0; 100_000]; drain(&mut out);
            let next = reserve(27).unwrap(); assert_eq!(next, slot);
            RESUME.wait(); next
        } else {
            let next = std::thread::spawn(|| { let mut out = vec![0; 100_000]; drain(&mut out); reserve(27).unwrap() });
            RESUME.wait(); next.join().unwrap()
        };
        freeing.join().unwrap(); TARGET.store(u32::MAX, Ordering::Release);
        assert!(cell(later).live.load(Ordering::Acquire), "late resource cleanup freed a later reservation");
        assert_eq!(cell(later).ticks_left.load(Ordering::Relaxed), 27);
        assert!(free(later));
    }
}

#[cfg(test)]
mod queued_resource_retirement {
    use super::*;
    #[test]
    fn retiring_last_resource_does_not_lose_the_java_unpin_event() {
        let slot = reserve_resource(19).unwrap(); retain(slot); release(slot);
        release_resource(slot);
        assert!(cell(slot).live.load(Ordering::Acquire));
        let mut out = vec![0; 100_000];
        let count = drain(&mut out);
        assert!(count > 0 && out[..count as usize].contains(&(slot as i64)), "retirement lost the pending Java root unpin");
        assert!(!cell(slot).live.load(Ordering::Acquire));
    }
}

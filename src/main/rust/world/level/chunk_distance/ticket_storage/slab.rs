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
use std::sync::atomic::{AtomicBool, AtomicI64, AtomicPtr, AtomicU32, Ordering};
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
    cell.refs.store(1, Ordering::Relaxed);
    Ok(slot)
}

pub(crate) fn retain(slot: u32) {
    cell(slot).refs.fetch_add(1, Ordering::Relaxed);
}

/// Drops one entry's reference; the last queues the cell for Java to unpin.
pub(crate) fn release(slot: u32) {
    let cell = cell(slot);
    if cell.refs.fetch_sub(1, Ordering::AcqRel) == 1 && !cell.queued.swap(true, Ordering::AcqRel) {
        let mut free = lock();
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
    for &slot in free.released.iter() {
        let cell = cell(slot);
        cell.queued.store(false, Ordering::Release);
        // Read once: a concurrent release may lower it at any time; such a
        // cell is either reported now or queued again by that release.
        if cell.refs.load(Ordering::Acquire) == 0 {
            out[index] = slot as i64;
            index += 1;
        }
    }
    free.released.clear();
    PENDING.store(false, Ordering::Release);
    index as i32
}

/// Frees an unreachable ticket's cell for reuse. Without entries (a storage
/// entry would have kept the ticket reachable) nothing reads it any more; a
/// cell still referenced is kept rather than reused.
pub(crate) fn free(slot: u32) -> bool {
    let mut free = lock();
    let cell = cell(slot);
    if cell.refs.load(Ordering::Acquire) != 0 {
        return false;
    }
    if cell.queued.swap(false, Ordering::AcqRel) {
        free.released.retain(|&queued| queued != slot);
    }
    free.free.push(slot);
    true
}

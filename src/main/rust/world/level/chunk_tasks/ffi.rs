//! Private CPU owner. The caller must exclude compound mutations and borrow
//! output only until its next change. Atomic diagnostics require no queue access.
use super::Queue;
use std::{
    cell::UnsafeCell,
    ffi::c_void,
    panic::{catch_unwind, AssertUnwindSafe},
    sync::atomic::{AtomicI32, AtomicU64, Ordering},
};
#[repr(C)]
struct Header {
    top: AtomicI32,
    levels: i32,
    pending: AtomicU64,
    next_id: AtomicU64,
}
// Deliberately not Sync: the safe Rust core requires &mut Queue. The temporary
// FFI adapter provides the same exclusion through the original consecutive dispatcher.
pub(super) struct Owner {
    header: Header,
    queue: UnsafeCell<Queue>,
}
#[no_mangle]
pub extern "C" fn mattmc_chunk_tasks_create(levels: i32) -> *mut Owner {
    if levels < 0 {
        return std::ptr::null_mut();
    }
    let Some(queue) = Queue::new(levels as usize) else {
        return std::ptr::null_mut();
    };
    Box::into_raw(Box::new(Owner {
        header: Header {
            top: AtomicI32::new(levels),
            levels,
            pending: AtomicU64::new(0),
            next_id: AtomicU64::new(1),
        },
        queue: UnsafeCell::new(queue),
    }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_tasks_destroy(owner: *mut Owner) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_tasks_header(owner: *const Owner) -> *const c_void {
    owner
        .as_ref()
        .map_or(std::ptr::null(), |o| &o.header as *const _ as *const c_void)
}
/// Safety: caller excludes other changes until it consumes every result token,
/// keeps the owner alive, and provides three writable aligned u64 metadata words.
/// Private CPU addresses use scalar words on the supported 64-bit targets;
/// their auto-arena lifetime is pinned at the temporary callable boundary.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_tasks_change_v4(
    owner_address: u64,
    action: i32,
    pos: u64,
    a: i32,
    b: u64,
    result_address: u64,
) -> u64 {
    let Some(owner) = (owner_address as *const Owner).as_ref() else {
        return 4;
    };
    let result = result_address as *mut u64;
    if result.is_null() || result as usize % 8 != 0 {
        return 4;
    }
    catch_unwind(AssertUnwindSafe(|| {
        let queue = &mut *owner.queue.get();
        // The temporary callable table uses the primary arena's physical row.
        // Decline stale projections before publication; no second allocator.
        let status = if action == 0
            && a >= 0
            && (a as usize) < queue.levels.len()
            && b != queue.tasks.next_id()
        {
            queue.output.clear();
            queue.output_pos = 0;
            4
        } else {
            queue.change(action, pos, a, b)
        };
        // Snapshot primary fields before writing external metadata. The
        // metadata allocation is distinct from this owner and its output.
        let pending = queue.pending as u64;
        let next_id = queue.tasks.next_id();
        let top = queue.top as i32;
        let count = queue.output.len() as u64;
        let output = queue.output.as_ptr() as u64;
        let pos = queue.output_pos;
        let fast = queue.levels.len() <= 64 && pending < 4096;
        result.write(output);
        result.add(1).write(count);
        result.add(2).write(pos);
        owner.header.pending.store(pending, Ordering::Release);
        owner.header.next_id.store(next_id, Ordering::Release);
        owner.header.top.store(top, Ordering::Release);
        // Transport facts only: next callable row, bounded call mode, status.
        // Top/pending diagnostics remain native atomic reads; output count is
        // always the full metadata word, without compact-count truncation.
        (next_id << 32) | if fast { 0x100 } else { 0 } | status as u64
    }))
    .unwrap_or(5)
}

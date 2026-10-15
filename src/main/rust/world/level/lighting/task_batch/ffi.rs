//! Original consecutive-executor writer exclusion covers each change and its
//! output consumption. Atomic admission/projections do not borrow the queue.
use super::{Action, Error, Queue};
use std::{
    cell::UnsafeCell,
    ffi::c_void,
    panic::{catch_unwind, AssertUnwindSafe},
    sync::atomic::{AtomicI32, AtomicU64, Ordering},
};

static LIVE: AtomicU64 = AtomicU64::new(0);
#[repr(C)]
struct Header {
    pending: AtomicI32,
    scheduled: AtomicI32,
    next_row: AtomicU64,
    bytes: AtomicU64,
}
pub(super) struct Owner {
    header: Header,
    queue: UnsafeCell<Queue>,
}
impl Drop for Owner {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
    }
}
#[no_mangle]
pub extern "C" fn mattmc_light_tasks_create() -> *mut Owner {
    let owner = Box::new(Owner {
        header: Header {
            pending: AtomicI32::new(0),
            scheduled: AtomicI32::new(0),
            next_row: AtomicU64::new(1),
            bytes: AtomicU64::new(0),
        },
        queue: UnsafeCell::new(Queue::default()),
    });
    LIVE.fetch_add(1, Ordering::Relaxed);
    Box::into_raw(owner)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_destroy(owner: *mut Owner) {
    if !owner.is_null() {
        drop(Box::from_raw(owner));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_header(owner: *const Owner) -> *const c_void {
    owner
        .as_ref()
        .map_or(std::ptr::null(), |o| &o.header as *const _ as *const c_void)
}
#[no_mangle]
pub extern "C" fn mattmc_light_tasks_live() -> u64 {
    LIVE.load(Ordering::Relaxed)
}
/// `finish=1` is called only after normal scheduled execution. Rejection and
/// callback failure deliberately keep admission stuck, exactly as the original.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_schedule(
    owner: *const Owner,
    work: i32,
    finish: i32,
) -> i32 {
    let Some(o) = owner.as_ref() else {
        return -1;
    };
    if finish != 0 {
        o.header.scheduled.store(0, Ordering::SeqCst);
        0
    } else if work != 0
        && o.header
            .scheduled
            .compare_exchange(0, 1, Ordering::SeqCst, Ordering::SeqCst)
            .is_ok()
    {
        1
    } else {
        0
    }
}
/// Metadata is two aligned writable u64 words: retired-row address and length.
/// It is borrowed only until the next change, and must be consumed before Java.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_change(
    owner: *const Owner,
    op: i32,
    a: u64,
    b: u64,
    metadata: *mut u64,
) -> i64 {
    let Some(o) = owner.as_ref() else {
        return -4;
    };
    if metadata.is_null() || metadata as usize % 8 != 0 {
        return -4;
    }
    metadata.write(0);
    metadata.add(1).write(0);
    catch_unwind(AssertUnwindSafe(|| {
        let q = &mut *o.queue.get();
        q.retired.clear();
        let result = match op {
            0 => q.append(b, a != 0).map(|run| i64::from(run)),
            1 => Ok(q.begin() as i64),
            2 => q.next(a).map(|action| match action {
                Action::Done => 0,
                Action::Propagate => 1,
                Action::Callable(row) => (row + 1) as i64,
            }),
            3 => q.end(a).map(|()| 0),
            _ => Err(Error::Cursor),
        };
        metadata.write(q.retired.as_ptr() as u64);
        metadata.add(1).write(q.retired.len() as u64);
        o.header
            .pending
            .store(q.tasks.len() as i32, Ordering::Release);
        o.header.next_row.store(q.next_row(), Ordering::Release);
        o.header
            .bytes
            .store(q.allocation_bytes() as u64, Ordering::Release);
        match result {
            Ok(value) => value,
            Err(Error::RemoveEmpty) => -2,
            Err(Error::Row) => -3,
            Err(Error::Cursor) => -4,
        }
    }))
    .unwrap_or(-5)
}

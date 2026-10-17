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
                Action::Import(row,target) => ((1u64<<62)|((target as u64)<<61)|(row+1)) as i64,
                Action::Context(row) => ((1u64<<60)|(1u64<<62)|(row+1)) as i64,
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
            Err(Error::Lighting(status)) => i64::from(status),
        }
    }))
    .unwrap_or(-5)
}

/// The actual native level token owns block/sky engine bindings. Java supplies
/// only the original VM capture roots in this row, not a callable policy.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_append_check(
    owner: *const Owner, level: *const super::LevelToken, pos: i64, row: u64,
) -> i64 {
    let Some(owner) = owner.as_ref() else { return -4; };
    catch_unwind(AssertUnwindSafe(|| {
        let queue = &mut *owner.queue.get();
        queue.retired.clear();
        let result = queue.append_check(row, level, pos);
        owner.header.pending.store(queue.tasks.len() as i32, Ordering::Release);
        owner.header.next_row.store(queue.next_row(), Ordering::Release);
        owner.header.bytes.store(queue.allocation_bytes() as u64, Ordering::Release);
        match result { Ok(run) => i64::from(run), Err(Error::Lighting(status)) => i64::from(status), Err(_) => -3 }
    })).unwrap_or(-5)
}

unsafe fn append_command(owner:*const Owner,append:impl FnOnce(&mut Queue)->Result<bool,Error>)->i64{
    let Some(owner)=owner.as_ref() else{return -4;};
    catch_unwind(AssertUnwindSafe(||{
        let queue=&mut *owner.queue.get();queue.retired.clear();let result=append(queue);
        owner.header.pending.store(queue.tasks.len() as i32,Ordering::Release);
        owner.header.next_row.store(queue.next_row(),Ordering::Release);
        owner.header.bytes.store(queue.allocation_bytes() as u64,Ordering::Release);
        match result{Ok(run)=>i64::from(run),Err(Error::Lighting(status))=>i64::from(status),Err(_)=>-3}
    })).unwrap_or(-5)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_append_retain(owner:*const Owner,level:*const super::LevelToken,key:i64,value:i32,row:u64)->i64{
    append_command(owner,|queue|queue.append_retain(row,level,key,value!=0))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_append_import(owner:*const Owner,level:*const super::LevelToken,channel:i32,key:i64,row:u64)->i64{
    append_command(owner,|queue|queue.append_import(row,level,channel,key))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_append_status(owner:*const Owner,level:*const super::LevelToken,key:i64,empty:i32,row:u64)->i64{
    append_command(owner,|queue|queue.append_status(row,level,key,empty!=0))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_append_level(owner:*const Owner,level:*const super::LevelToken,kind:i32,key:i64,value:i32,row:u64)->i64{
    append_command(owner,|queue|queue.append_level(row,level,kind,key,value!=0))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_context(owner:*const Owner,cursor:u64,advance:i32,scalar:i64,entry:*const c_void,out:*mut i64)->i32{
    let Some(owner)=owner.as_ref() else{return -27;};if out.is_null(){return -27;}
    catch_unwind(AssertUnwindSafe(||(&mut *owner.queue.get()).context(cursor,advance!=0,scalar,entry,out))).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_context_abort(owner:*const Owner,cursor:u64){
    if let Some(owner)=owner.as_ref(){(&mut *owner.queue.get()).abort_context(cursor);}
}
/// Called only after Java acquires the actual queued-section VM monitor.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_apply_import(owner:*const Owner,cursor:u64,entry:*const c_void)->i32{
    let Some(owner)=owner.as_ref() else{return -4;};
    catch_unwind(AssertUnwindSafe(||match (&*owner.queue.get()).apply_import(cursor,entry){
        Ok(())=>0,Err(Error::Lighting(status))=>status,Err(_)=>-4,
    })).unwrap_or(-5)
}

/// The actual level is retained only by this invocation cursor. A held empty
/// task owner therefore adds no CPU engine/VM facade lifetime after POST.
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_begin_level(owner:*const Owner,level:*const super::LevelToken)->i64{
    let Some(owner)=owner.as_ref()else{return -4;};
    catch_unwind(AssertUnwindSafe(||{
        let Some(level)=super::LevelLease::retain(level)else{return -4;};
        let queue=&mut *owner.queue.get();queue.begin_level(Some(level)) as i64
    })).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_context_bind_source(owner:*const Owner,cursor:u64,
    source:*const super::super::source_owner::Source)->i32 {
    if owner.is_null() || source.is_null() { return -27; }
    catch_unwind(AssertUnwindSafe(||(&mut *(*owner).queue.get()).context_source(cursor,source))).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_context_bind_block(owner:*const Owner,cursor:u64,
    section:*const crate::world::level::chunk::live::Owner,index:i32)->i32 {
    if owner.is_null() || section.is_null(){return -27;}
    catch_unwind(AssertUnwindSafe(||(&mut *(*owner).queue.get()).context_block(cursor,section,index))).unwrap_or(-99)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_light_tasks_context_resource(owner:*const Owner,cursor:u64)->*const c_void{
    owner.as_ref().map_or(std::ptr::null(),|owner|(&mut *owner.queue.get()).context_resource(cursor))
}

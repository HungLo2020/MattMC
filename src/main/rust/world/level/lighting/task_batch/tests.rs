use super::*;

mod typed {
    use super::*;
    use std::sync::{Arc, atomic::{AtomicI32, AtomicUsize, Ordering}};
    #[repr(C)]
    struct ActualLevel {
        token: LevelToken,
        calls: AtomicUsize,
        failure: AtomicI32,
        observed: AtomicUsize,
        dropped_frames: AtomicUsize,
    }
    unsafe extern "C" fn retain(token: *const LevelToken) { Arc::increment_strong_count(token.cast::<ActualLevel>()); }
    unsafe extern "C" fn release(token: *const LevelToken) { drop(Arc::from_raw(token.cast::<ActualLevel>())); }
    unsafe extern "C" fn check(token: *const LevelToken, _pos: i64) -> i32 {
        let level = &*token.cast::<ActualLevel>();
        level.calls.fetch_add(1, Ordering::Relaxed);
        level.failure.swap(0, Ordering::Relaxed)
    }
    unsafe extern "C" fn operation(token:*const LevelToken,_kind:i32,_key:i64,_value:i64,entry:*const c_void)->i32{
        let level=&*token.cast::<ActualLevel>();level.calls.fetch_add(1,Ordering::Relaxed);
        if !entry.is_null(){level.observed.store((&*entry.cast::<AtomicUsize>()).load(Ordering::Relaxed),Ordering::Relaxed);}
        level.failure.swap(0,Ordering::Relaxed)
    }
    unsafe extern "C" fn channel(_: *const LevelToken,channel:i32)->i32{if channel==99{-1}else if channel==1{0}else{1}}
    #[repr(C)]
    struct ActualFrame {token:FrameToken,owner:Arc<ActualLevel>,action:i32}
    unsafe extern "C" fn frame_read(token:*mut FrameToken,out:*mut i64)->i32{
        let frame=&*token.cast::<ActualFrame>();std::ptr::write_bytes(out,0,7);*out=42;*out.add(6)=1;frame.action
    }
    unsafe extern "C" fn frame_step(token:*mut FrameToken,_scalar:i64,_entry:*const c_void,out:*mut i64)->i32{
        let frame=&mut *token.cast::<ActualFrame>();frame.owner.calls.fetch_add(1,Ordering::Relaxed);frame.action=0;frame_read(token,out)
    }
    unsafe extern "C" fn frame_release(token:*mut FrameToken){
        let frame=Box::from_raw(token.cast::<ActualFrame>());frame.owner.dropped_frames.fetch_add(1,Ordering::Relaxed);
    }
    unsafe extern "C" fn frame_source(_token:*mut FrameToken,_source:*const crate::world::level::lighting::source_owner::Source)->i32{-27}
    unsafe extern "C" fn frame_resource(_token:*mut FrameToken)->*const c_void{std::ptr::null()}
    unsafe extern "C" fn start(token:*const LevelToken,_kind:i32,_key:i64,_empty:bool)->*mut FrameToken{
        let owner=&*token.cast::<ActualLevel>();let mode=owner.failure.swap(0,Ordering::Relaxed);
        if mode==0{return std::ptr::null_mut();}
        Arc::increment_strong_count(token.cast::<ActualLevel>());
        Box::into_raw(Box::new(ActualFrame{token:FrameToken{read:frame_read,step:frame_step,release:frame_release,resource:frame_resource,bind_source:frame_source},owner:Arc::from_raw(token.cast::<ActualLevel>()),action:if mode<0{mode}else{1}})).cast()
    }
    fn level() -> Arc<ActualLevel> {
        Arc::new(ActualLevel { token: LevelToken { retain, release, check,operation,channel,start }, calls: AtomicUsize::new(0), failure: AtomicI32::new(0),observed:AtomicUsize::new(0),dropped_frames:AtomicUsize::new(0) })
    }
    #[test]
    fn level_update_context_is_between_pre_and_post_and_lives_only_for_its_pass(){
        let level=level();let weak=Arc::downgrade(&level);let mut queue=Queue::default();
        queue.append(1,false).unwrap();queue.append(2,true).unwrap();
        let cursor=queue.begin_level(unsafe{LevelLease::retain(Arc::as_ptr(&level).cast())});
        assert_eq!(queue.next(cursor),Ok(Action::Callable(1)));
        level.failure.store(1,Ordering::Relaxed);
        assert_eq!(queue.next(cursor),Ok(Action::Context(0)));assert_eq!(queue.tasks.len(),2);
        assert_eq!(queue.next(cursor),Err(Error::Cursor));let mut output=[0i64;7];
        assert_eq!(unsafe{queue.context(cursor,false,0,std::ptr::null(),output.as_mut_ptr())},1);
        assert!(unsafe{queue.context_resource(cursor)}.is_null());
        assert_eq!(unsafe{queue.context(cursor,true,0,std::ptr::null(),output.as_mut_ptr())},0);
        assert_eq!(queue.next(cursor),Ok(Action::Callable(2)));assert_eq!(queue.tasks.len(),1);
        drop(level);assert!(weak.upgrade().is_some());
        assert_eq!(queue.next(cursor),Ok(Action::Done));queue.end(cursor).unwrap();
        assert!(weak.upgrade().is_none(),"a held empty task owner does not retain the retired level");
    }
    #[test]
    fn abandoned_level_update_preserves_all_tasks_and_retries_original_pre(){
        let level=level();let mut queue=Queue::default();queue.append(1,false).unwrap();queue.append(2,true).unwrap();
        let cursor=queue.begin_level(unsafe{LevelLease::retain(Arc::as_ptr(&level).cast())});
        assert_eq!(queue.next(cursor),Ok(Action::Callable(1)));level.failure.store(-26,Ordering::Relaxed);
        assert_eq!(queue.next(cursor),Ok(Action::Context(0)));queue.end(cursor).unwrap();assert_eq!(queue.tasks.len(),2);
        assert_eq!(level.dropped_frames.load(Ordering::Relaxed),1);
        let retry=queue.begin_level(unsafe{LevelLease::retain(Arc::as_ptr(&level).cast())});
        assert_eq!(queue.next(retry),Ok(Action::Callable(1)));
        assert_eq!(queue.next(retry),Ok(Action::Callable(2)),"pure update completes inside native cursor before POST");
        assert_eq!(queue.next(retry),Ok(Action::Done));queue.end(retry).unwrap();
    }
    #[test]
    fn actual_context_keeps_row_waiting_until_vm_reply_and_releases_on_completion(){
        let level=level();level.failure.store(77,Ordering::Relaxed);let mut queue=Queue::default();
        unsafe{queue.append_status(1,Arc::as_ptr(&level).cast(),0,false).unwrap();}
        let cursor=queue.begin();assert_eq!(queue.next(cursor),Ok(Action::Context(1)));
        assert_eq!(queue.next(cursor),Err(Error::Cursor));let mut out=[0i64;7];
        assert_eq!(unsafe{queue.context(cursor,false,0,std::ptr::null(),out.as_mut_ptr())},1);assert_eq!(out[0],42);assert_eq!(out[6],1);
        assert_eq!(unsafe{queue.context(cursor,true,0,std::ptr::null(),out.as_mut_ptr())},0);
        assert_eq!(level.dropped_frames.load(Ordering::Relaxed),1);
        assert_eq!(queue.next(cursor),Ok(Action::Propagate));assert_eq!(queue.next(cursor),Ok(Action::Done));queue.end(cursor).unwrap();
    }
    #[test]
    fn original_error_context_abandonment_releases_frame_but_preserves_pending_retry(){
        let level=level();level.failure.store(-26,Ordering::Relaxed);let mut queue=Queue::default();
        unsafe{queue.append_status(1,Arc::as_ptr(&level).cast(),0,false).unwrap();}
        let cursor=queue.begin();assert_eq!(queue.next(cursor),Ok(Action::Context(1)));let mut out=[0i64;7];
        assert_eq!(unsafe{queue.context(cursor,false,0,std::ptr::null(),out.as_mut_ptr())},-26);
        queue.end(cursor).unwrap();assert_eq!(level.dropped_frames.load(Ordering::Relaxed),1);assert_eq!(queue.tasks.len(),1);
        let retry=queue.begin();assert_eq!(queue.next(retry),Ok(Action::Propagate));assert_eq!(queue.next(retry),Ok(Action::Done));queue.end(retry).unwrap();
    }
    #[test]
    fn import_waits_for_original_vm_adoption_effect_and_consumes_current_borrow(){
        let level=level();let source=Arc::new(AtomicUsize::new(4));let mut queue=Queue::default();
        unsafe{
            queue.append_retain(1,Arc::as_ptr(&level).cast(),777,true).unwrap();
            queue.append_import(2,Arc::as_ptr(&level).cast(),1,888).unwrap();
        }
        let cursor=queue.begin();assert_eq!(queue.next(cursor),Ok(Action::Import(2,0)));
        assert_eq!(level.calls.load(Ordering::Relaxed),1,"only preceding pure retain has executed");
        source.store(9,Ordering::Relaxed);
        queue.apply_import(cursor,Arc::as_ptr(&source).cast()).unwrap();
        assert_eq!(level.observed.load(Ordering::Relaxed),9);
        assert_eq!(queue.next(cursor),Ok(Action::Propagate));assert_eq!(queue.tasks.len(),2);
        assert_eq!(queue.next(cursor),Ok(Action::Done));queue.end(cursor).unwrap();
    }
    #[test]
    fn import_effect_failure_and_invalid_admission_preserve_row_owner_and_retry(){
        let level=level();let source=Arc::new(AtomicUsize::new(3));let before=Arc::strong_count(&level);
        let mut queue=Queue::default();
        assert_eq!(unsafe{queue.append_import(2,Arc::as_ptr(&level).cast(),0,0)},Err(Error::Row));
        assert_eq!(Arc::strong_count(&level),before);
        unsafe{queue.append_import(1,Arc::as_ptr(&level).cast(),0,0).unwrap();}
        level.failure.store(-20,Ordering::Relaxed);let cursor=queue.begin();
        assert_eq!(queue.next(cursor),Ok(Action::Import(1,1)));
        assert_eq!(queue.apply_import(cursor,Arc::as_ptr(&source).cast()),Err(Error::Lighting(-20)));
        queue.end(cursor).unwrap();assert_eq!(queue.tasks.len(),1);assert!(queue.retired.is_empty());
        let retry=queue.begin();assert_eq!(queue.next(retry),Ok(Action::Import(1,1)));
        queue.apply_import(retry,Arc::as_ptr(&source).cast()).unwrap();
        assert_eq!(queue.next(retry),Ok(Action::Propagate));assert_eq!(queue.next(retry),Ok(Action::Done));queue.end(retry).unwrap();
        assert_eq!(Arc::strong_count(&level),before);
    }
    #[test]
    fn absent_native_channel_skips_vm_adoption_effect_but_keeps_owner_through_post(){
        let level=level();let weak=Arc::downgrade(&level);let mut queue=Queue::default();
        unsafe{queue.append_import(1,Arc::as_ptr(&level).cast(),99,0).unwrap();}
        drop(level);let cursor=queue.begin();assert_eq!(queue.next(cursor),Ok(Action::Propagate));
        assert_eq!(weak.upgrade().unwrap().calls.load(Ordering::Relaxed),0);
        assert_eq!(queue.next(cursor),Ok(Action::Done));queue.end(cursor).unwrap();assert!(weak.upgrade().is_none());
    }
    #[test]
    fn pure_native_pre_commands_preserve_live_cap_post_retirement_and_actual_owner_lifetime() {
        let level = level(); let weak = Arc::downgrade(&level);
        let mut queue = Queue::default();
        for key in 0..1001 { unsafe { queue.append_check(queue.next_row(), Arc::as_ptr(&level).cast(), key).unwrap(); } }
        let cursor = queue.begin();
        assert_eq!(queue.next(cursor), Ok(Action::Propagate));
        assert_eq!(level.calls.load(Ordering::Relaxed), 1000);
        assert_eq!(queue.tasks.len(), 1001, "PRE does not retire original capture roots");
        assert_eq!(queue.next(cursor), Ok(Action::Done));
        queue.end(cursor).unwrap();
        assert_eq!(queue.tasks.len(), 1);
        drop(level);
        assert!(weak.upgrade().is_some());
        let rest = queue.begin();
        assert_eq!(queue.next(rest), Ok(Action::Propagate));
        assert_eq!(queue.next(rest), Ok(Action::Done));
        queue.end(rest).unwrap();
        assert!(weak.upgrade().is_none());
        assert_eq!(queue.retired.len(), 1001);
    }
    #[test]
    fn failed_native_pre_retains_row_owner_and_retry_then_continues_live_callables() {
        let level = level(); level.failure.store(-20, Ordering::Relaxed);
        let mut queue = Queue::default();
        unsafe { queue.append_check(1, Arc::as_ptr(&level).cast(), 77).unwrap(); }
        queue.append(2, false).unwrap(); queue.append(3, true).unwrap();
        let failed = queue.begin();
        assert_eq!(queue.next(failed), Err(Error::Lighting(-20)));
        queue.end(failed).unwrap();
        assert_eq!(queue.tasks.len(), 3); assert!(queue.retired.is_empty());
        let retry = queue.begin();
        assert_eq!(queue.next(retry), Ok(Action::Callable(2)));
        assert_eq!(level.calls.load(Ordering::Relaxed), 2);
        assert_eq!(queue.next(retry), Ok(Action::Propagate));
        assert_eq!(queue.next(retry), Ok(Action::Callable(3)));
        assert_eq!(queue.tasks.len(), 1);
        assert_eq!(queue.next(retry), Ok(Action::Done));
        queue.end(retry).unwrap();
    }
    #[test]
    fn rejected_row_drops_prepared_native_owner_without_admitting_or_retiring_a_task() {
        let level = level(); let before = Arc::strong_count(&level);
        let mut queue = Queue::default();
        assert_eq!(unsafe { queue.append_check(2, Arc::as_ptr(&level).cast(), 0) }, Err(Error::Row));
        assert_eq!(Arc::strong_count(&level), before); assert!(queue.tasks.is_empty());
        assert_eq!(unsafe { queue.append_check(1, std::ptr::null(), 0) }, Err(Error::Row));
        assert_eq!(queue.next_row(), 1);
    }
}

#[test]
fn live_phases_cap_and_post_failure_prefix() {
    let mut q = Queue::default();
    for i in 0..1001 {
        assert_eq!(q.append(q.next_row(), i % 2 == 1), Ok(i >= 999));
    }
    let walk = q.begin();
    for i in (1..=1000).step_by(2) {
        assert_eq!(q.next(walk), Ok(Action::Callable(i)));
    }
    assert_eq!(q.next(walk), Ok(Action::Propagate));
    assert_eq!(q.next(walk), Ok(Action::Callable(2)));
    assert_eq!(q.tasks.len(), 1000); // PRE row removed; failing POST is retained.
    q.end(walk).unwrap();
    assert_eq!(q.tasks[0].row, 2);
    let retry = q.begin();
    let mut propagations = 0;
    loop {
        match q.next(retry).unwrap() {
            Action::Done => break,
            Action::Propagate => propagations += 1,
            _ => {}
        }
    }
    q.end(retry).unwrap();
    assert_eq!(propagations, 1);
    assert!(q.tasks.is_empty());
    assert_eq!(q.rows, 1001);
    assert_eq!(q.free_rows.len(), 1001);
}
#[test]
fn nested_post_removal_preserves_original_outer_failure() {
    let mut q = Queue::default();
    q.append(1, true).unwrap();
    let outer = q.begin();
    assert_eq!(q.next(outer), Ok(Action::Propagate));
    assert_eq!(q.next(outer), Ok(Action::Callable(1)));
    let inner = q.begin();
    assert_eq!(q.next(inner), Ok(Action::Propagate));
    assert_eq!(q.next(inner), Ok(Action::Callable(1)));
    assert_eq!(q.next(inner), Ok(Action::Done));
    q.end(inner).unwrap();
    assert_eq!(q.next(outer), Err(Error::RemoveEmpty));
    q.end(outer).unwrap();
    assert_eq!(q.append(q.next_row(), false), Ok(false));
    assert_eq!(q.rows, 1);
}
#[test]
fn pre_reentry_reads_live_membership_and_empty_pass_propagates() {
    let mut q = Queue::default();
    let empty = q.begin();
    assert_eq!(q.next(empty), Ok(Action::Propagate));
    assert_eq!(q.next(empty), Ok(Action::Done));
    q.end(empty).unwrap();
    q.append(1, false).unwrap();
    q.append(2, true).unwrap();
    let walk = q.begin();
    assert_eq!(q.next(walk), Ok(Action::Callable(1)));
    q.append(3, false).unwrap();
    assert_eq!(q.next(walk), Ok(Action::Propagate));
    assert_eq!(q.next(walk), Ok(Action::Callable(2)));
    assert_eq!(q.next(walk), Ok(Action::Done));
    q.end(walk).unwrap();
    assert_eq!(q.tasks.len(), 1);
    assert_eq!(q.tasks[0].row, 3);
}

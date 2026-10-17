use super::*;

#[test]
fn future_slots_keep_native_membership_and_nonreused_publication_tags() {
    let owner = Holder::new(12).unwrap();
    let first = owner.reserve_future(0, true);
    let second = owner.reserve_future(1, true);
    assert_ne!(first, second);
    assert_eq!(owner.publish_future(0, 0, first), 1);
    assert_eq!(owner.publish_future(1, 0, second), 1);
    assert_eq!(owner.change(0, 0, 0) as u64, first);
    assert_eq!(owner.change(8, 0, 0), 2);
    assert_eq!(owner.publish_future(2, 0, first), -14, "a tag cannot migrate to another field");
    let null = owner.reserve_future(0, false);
    assert_ne!(null, 0, "a null publication still needs a matching heap acquire");
    assert_eq!(null & 1, 0);
    assert_eq!(owner.publish_future(0, first, null), 1);
    assert_eq!(owner.change(8, 0, 0), 1);
    let replacement = owner.reserve_future(0, true);
    assert_eq!(owner.publish_future(0, 0, replacement), 0, "initial null is not a later null generation");
    assert_eq!(owner.publish_future(0, null, replacement), 1);
    assert_eq!(owner.publish_future(0, replacement, replacement), -14, "a consumed tag cannot publish twice");
    assert_eq!(owner.change(0, 1, 0) as u64, second);
    assert_eq!(owner.publish_future(20, 0, replacement), -14);
    assert_eq!(owner.publish_future(0, replacement, 0), -14);
}

#[test]
fn fixed_slot_storage_and_generation_exhaustion_leave_membership_intact() {
    let owner = Holder::new(12).unwrap();
    let pointer = owner.fields.as_ptr();
    let mut previous = 0;
    for publication in 0..4096 {
        let word = owner.reserve_future(0, publication % 2 == 0);
        assert!(word >> 1 > previous >> 1);
        assert_eq!(owner.publish_future(0, previous, word), 1);
        previous = word;
    }
    owner.next_future_epoch.store((u64::MAX >> 4) + 1, Ordering::SeqCst);
    assert_eq!(owner.reserve_future(0, true), 0);
    assert_eq!(owner.reserve_future(0, false), 0);
    assert_eq!(owner.change(0, 0, 0) as u64, previous);
    assert_eq!(owner.fields.as_ptr(), pointer);
    assert!(owner.fields[1..].iter().all(|field| field.load(Ordering::SeqCst) == 0));
}

#[test]
fn future_publication_accepts_the_final_full_width_tag_before_exhaustion() {
    let owner = Holder::new(12).unwrap();
    owner.next_future_epoch.store(u64::MAX >> 4, Ordering::SeqCst);
    let last = owner.reserve_future(0, true);
    assert_eq!(last, u64::MAX & !6);
    assert_eq!(owner.publish_future(0, 0, last), 1);
    assert_eq!(owner.reserve_future(0, true), 0);
    assert_eq!(owner.change(0, 0, 0) as u64, last);
    assert_eq!(owner.change(8, 0, 0), 1);
}

#[test]
fn binding_location_is_reserved_from_native_publication_not_an_uncommitted_candidate() {
    let owner = Holder::new(12).unwrap();
    let a = owner.reserve_future(0, true);
    let b = owner.reserve_future(0, true);
    assert_ne!(a, b);
    assert_eq!((a >> 3) & 1, 1);
    assert_eq!((b >> 3) & 1, 1, "reserving does not advance the published location");
    assert_eq!(owner.publish_future(0, 0, a), -14, "superseded reservation cannot publish");
    assert_eq!(owner.publish_future(0, 0, b), 1);
    let c = owner.reserve_future(0, false);
    assert_eq!((c >> 3) & 1, 0);
    assert_eq!(owner.change(0, 0, 0) as u64, b);
    assert_eq!(owner.publish_future(0, b, c), 1);
    assert_eq!(owner.change(0, 0, 0) as u64, c);
}

#[test]
fn full_admission_and_future_membership_share_one_native_publication() {
    let owner = Holder::new(12).unwrap();
    let full = owner.full_field() as i32;
    let future = owner.reserve_future(full, true);
    assert_eq!(owner.publish_future(full, 0, future), 1);
    assert_eq!((future >> 1) & 3, 0);
    let allowed = owner.reserve_allowed(12);
    assert_ne!(allowed, future);
    assert_eq!(owner.change(0, full, 0) as u64, future, "reserve does not publish admission");
    assert_eq!(owner.publish_future(full, future, allowed), 1);
    assert_eq!((allowed >> 1) & 3, 1);
    assert_eq!(allowed & 1, 1, "allowed publication preserves the future");
    let clear = owner.reserve_future(full, false);
    assert_eq!((clear >> 1) & 3, 1, "future publication preserves allowed identity");
    assert_eq!(owner.publish_future(full, allowed, clear), 1);
    let foreign = owner.reserve_allowed(u16::MAX as u32);
    assert_eq!(foreign & 1, 0);
    assert_eq!(owner.publish_future(full, clear, foreign), 1);
    assert_eq!((foreign >> 1) & 3, 2);
    let disallowed = owner.reserve_allowed(0);
    assert_eq!(owner.publish_future(full, foreign, disallowed), 1);
    assert_eq!((disallowed >> 1) & 3, 0);
    assert_eq!(owner.fields[0].load(Ordering::SeqCst), 0);
    assert_eq!(owner.reserve_allowed(13), 0);
    assert_eq!(owner.reserve_allowed(65536), 0);
}

#[test]
fn full_admission_accepts_all_bits_of_the_final_tag_and_never_wraps() {
    let owner = Holder::new(12).unwrap();
    let full = owner.full_field() as i32;
    let first = owner.reserve_future(full, true);
    assert_eq!(owner.publish_future(full, 0, first), 1);
    owner.next_future_epoch.store(u64::MAX >> 4, Ordering::SeqCst);
    let last = owner.reserve_allowed(u16::MAX as u32);
    assert_eq!(last, u64::MAX & !10);
    assert_eq!(owner.publish_future(full, first, last), 1);
    assert_eq!(owner.reserve_allowed(0), 0);
    assert_eq!(owner.reserve_future(full, false), 0);
    assert_eq!(owner.change(0, full, 0) as u64, last);
}

#[test]
fn started_status_cas_preserves_the_exact_publication_generation() {
    let owner = Holder::new(12).unwrap();
    let cell = &owner.header.started;
    let first = cell.reserve(1);
    let second = cell.reserve(2);
    assert!(!cell.compare_exchange(first, second));
    assert!(cell.compare_exchange(0, first));
    assert!(!cell.compare_exchange(0, second));
    assert!(cell.compare_exchange(first, second));
    let same_identity = cell.reserve(1);
    cell.set(same_identity);
    assert!(!cell.compare_exchange(first, second), "same canonical identity does not reuse its old generation");
    assert_eq!(cell.value.load(Ordering::SeqCst), same_identity);
}

#[test]
fn status_identity_tags_are_native_nonwrapping_and_canonical_words_stay_distinct() {
    let cell = StatusCell::new(1);
    let first = cell.reserve(u16::MAX as u32);
    let second = cell.reserve(u16::MAX as u32);
    assert!(first >> 16 > 0);
    assert_ne!(first, second);
    cell.set(first);
    assert_eq!(cell.value.load(Ordering::SeqCst), first);
    cell.set(0);
    cell.set(second);
    cell.next_epoch.store((u64::MAX >> 16) + 1, Ordering::SeqCst);
    assert_eq!(cell.reserve(u16::MAX as u32), 0);
    assert_eq!(cell.value.load(Ordering::SeqCst), second, "failed reservation cannot change the assigned identity");
    assert_eq!(std::mem::offset_of!(Header, started), 8);
    assert_eq!(std::mem::size_of::<Header>(), 24);
    assert_eq!(std::mem::offset_of!(StatusCell, value), 0);
}

#[test]
fn claim_transitions_preserve_negative_prefix_and_zero_epoch_boundary() {
    let owner = Holder::new(12).unwrap();
    assert_eq!(owner.change(7, 0, 0), -1);
    assert_eq!(owner.change(6, 0, 0), -1);
    assert_eq!(owner.change(6, 0, 0), 0);
    assert_eq!(owner.change(6, 0, 0), 1);
    assert_eq!(owner.change(7, 0, 0), 1);
    assert_eq!(owner.change(7, 0, 0), 0);
}

struct Effects<'a> {
    task: &'a Task,
    calls: Vec<(i32, i32, i32, i32, u64)>,
    pending: bool,
    fail_open: bool,
    fail_close: bool,
}

impl TaskEffects for Effects<'_> {
    async fn call(&mut self, op: i32, a: i32, b: i32, c: i32, token: u64) -> Result<i64, ()> {
        // A Java callback may immediately reenter this same owner.
        drop(self.task.state.try_lock().expect("native lock held across an effect"));
        self.calls.push((op, a, b, c, token));
        match op {
            1 => Ok(i64::from(!self.pending)),
            3 | 17 | 19 => Ok(1),
            2 => Ok(i64::from(!self.pending)),
            7 if self.fail_open => Err(()),
            8 if self.fail_close => Err(()),
            _ => Ok(0),
        }
    }
}

fn task(radius: i32) -> Task {
    Task::new(1, 4, -7, TaskPolicy {
        generation_radii: vec![radius, 0],
        loading_radii: vec![radius, 0],
        loading_dependencies: vec![0],
        status_count: 2,
        foreign_radius: None,
    }).unwrap()
}

#[test]
fn task_orders_real_effect_boundaries_without_holding_native_state() {
    let task = task(1);
    let mut effects = Effects { task: &task, calls: vec![], pending: false, fail_open: false, fail_close: false };
    assert_eq!(task.run(&mut effects), Ok(false));
    assert_eq!(task.lock().scheduled, 1);
    assert!(!task.lock().needs_generation);
    let applied: Vec<_> = effects.calls.iter().filter(|row| row.0 == 1).map(|row| (row.1, row.2, row.3)).collect();
    let mut expected = vec![];
    for x in 3..=5 { for z in -8..=-6 { expected.push((0, x, z)); } }
    expected.push((1, 4, -7));
    assert_eq!(applied, expected);
    let released: Vec<_> = effects.calls.iter().filter(|row| row.0 == 10).map(|row| (row.1, row.2)).collect();
    assert_eq!(released, expected[..9].iter().map(|&(_, x, z)| (x, z)).collect::<Vec<_>>());
    assert_eq!(effects.calls.iter().filter(|row| row.0 == 7).count(), 2);
    assert_eq!(effects.calls.iter().filter(|row| row.0 == 8).count(), 2);
}

#[test]
fn task_retains_pending_work_through_cancellation_and_retires_before_success_probe() {
    let task = task(0);
    let mut effects = Effects { task: &task, calls: vec![], pending: true, fail_open: false, fail_close: false };
    assert_eq!(task.run(&mut effects), Ok(true));
    assert_eq!(task.lock().pending.len(), 1);
    task.cancel();
    assert_eq!(task.run(&mut effects), Ok(true));
    assert!(!effects.calls.iter().any(|row| row.0 == 10));
    effects.pending = false;
    effects.calls.clear();
    assert_eq!(task.run(&mut effects), Ok(false));
    assert!(task.lock().pending.is_empty());
    assert_eq!(effects.calls.iter().map(|row| row.0).collect::<Vec<_>>(), vec![2, 5, 3, 9, 10]);
    assert_eq!(task.lock().pins.allocated.iter().filter(|&&live| live).count(), 0);
}

#[test]
fn task_closes_failed_zone_and_does_not_commit_scheduled_status_until_close_succeeds() {
    let task = task(0);
    let mut effects = Effects { task: &task, calls: vec![], pending: false, fail_open: true, fail_close: false };
    assert_eq!(task.run(&mut effects), Err(()));
    assert_eq!(effects.calls.iter().map(|row| row.0).collect::<Vec<_>>(), vec![7, 8]);
    assert_eq!(task.lock().scheduled, -1);
    effects.fail_open = false;
    effects.fail_close = true;
    assert_eq!(task.run(&mut effects), Err(()));
    assert_eq!(task.lock().scheduled, -1);
    effects.fail_close = false;
    assert_eq!(task.run(&mut effects), Ok(false));
    assert_eq!(task.lock().scheduled, 1);
}

struct ReleaseEffects<'a> {
    task: &'a Task,
    calls: Vec<(i32, i32, i32, i32, u64)>,
    fail_completion: bool,
}
impl TaskEffects for ReleaseEffects<'_> {
    async fn call(&mut self, op: i32, a: i32, b: i32, c: i32, token: u64) -> Result<i64, ()> {
        drop(self.task.state.try_lock().expect("task lock across release effect"));
        self.calls.push((op, a, b, c, token));
        if op == 22 {
            if token != 0 {
                let side = 2 * self.task.cache_radius + 1;
                let index = ((b - self.task.x + self.task.cache_radius) * side + c - self.task.z + self.task.cache_radius) as usize;
                assert_eq!(self.task.release_holders.as_ref().unwrap()[index].header.claims.load(Ordering::SeqCst), 1,
                    "later claims must remain untouched until this completion/capture effect");
            }
            if a < 0 || a == 0 && self.fail_completion { return Err(()); }
        }
        Ok(0)
    }
}

#[test]
fn native_task_cache_releases_the_original_live_prefix_between_fence_effects() {
    let mut task = task(1);
    let holders: Vec<_> = (0..9).map(|_| Arc::new(Holder::new(2).unwrap())).collect();
    for holder in &holders { holder.header.claims.store(1, Ordering::SeqCst); }
    task.release_holders = Some(holders.clone());
    let mut effects = ReleaseEffects { task: &task, calls: vec![], fail_completion: true };
    assert_eq!(task.release(&mut effects), Err(()));
    assert_eq!(holders[0].header.claims.load(Ordering::SeqCst), 0);
    assert!(holders[1..].iter().all(|h| h.header.claims.load(Ordering::SeqCst) == 1));
    assert_eq!(effects.calls, vec![(21, 3, -8, 0, 0), (22, 0, 3, -7, 1)]);
    effects.fail_completion = false;
    effects.calls.clear();
    assert_eq!(task.release(&mut effects), Err(()), "retry starts at the original first holder");
    assert_eq!(holders[0].header.claims.load(Ordering::SeqCst), -1);
    assert_eq!(effects.calls, vec![(21, 3, -8, 0, 0), (22, -1, 3, -7, 1)]);
}

#[test]
fn task_cache_pins_native_holder_incarnations_independently_of_registry_ids() {
    let mut task = task(0);
    let holder = Arc::new(Holder::new(2).unwrap());
    let weak = Arc::downgrade(&holder);
    holder.header.claims.store(1, Ordering::SeqCst);
    task.release_holders = Some(vec![holder.clone()]);
    drop(holder);
    assert!(weak.upgrade().is_some());
    let mut effects = ReleaseEffects { task: &task, calls: vec![], fail_completion: false };
    assert_eq!(task.release(&mut effects), Ok(()));
    assert_eq!(effects.calls, vec![(21, 4, -7, 0, 0), (22, 0, 5, -7, 0)]);
    drop(effects);
    drop(task);
    assert!(weak.upgrade().is_none());
}

#[test]
fn pending_walk_preserves_live_fail_fast_and_throwing_prefix() {
    let pending = Pending::new().unwrap();
    let first = pending.change(0, 0) as u32;
    assert_eq!(pending.change(1, first), 0);
    let second = pending.change(0, 0) as u32;
    assert_eq!(pending.change(1, second), 0);
    let mut seen = vec![];
    assert_eq!(pending.walk(|row| {
        // Must be possible to mutate this owner inside its callback.
        seen.push(row);
        let next = pending.change(0, 0) as u32;
        assert_eq!(pending.change(1, next), 0);
        Ok(())
    }), 2);
    assert_eq!(seen, vec![first]);
    assert_eq!(pending.change(4, 0), 3);
    assert_eq!(pending.walk(|_| {
        pending.change(3, 0);
        Err(())
    }), -1, "callback error wins over the changed version");
    assert_eq!(pending.change(4, 0), 0);
    let version = pending.state.lock().unwrap().version;
    pending.change(3, 0);
    assert_eq!(pending.state.lock().unwrap().version, version.wrapping_add(1));
}

#[test]
fn pending_nested_clear_and_append_retains_callback_additions() {
    let pending = Pending::new().unwrap();
    let first = pending.change(0, 0) as u32;
    pending.change(1, first);
    let mut added = 0;
    assert_eq!(pending.walk(|row| {
        assert_eq!(row, first);
        pending.change(3, 0);
        added = pending.change(0, 0) as u32;
        pending.change(1, added);
        Ok(())
    }), 2);
    let mut seen = vec![];
    assert_eq!(pending.walk(|row| { seen.push(row); Ok(()) }), 0);
    assert_eq!(seen, vec![added]);
}

struct Publication {
    calls: Vec<i32>,
    fail: Option<i32>,
    imposter: bool,
    proto: bool,
    before: bool,
}
impl PublicationEffects for Publication {
    fn call(&mut self, op: i32) -> Result<i32, ()> {
        self.calls.push(op);
        if self.fail == Some(op) { return Err(()); }
        Ok(i32::from(match op { 0 => self.imposter, 10 => self.proto, 11 => self.before, _ => false }))
    }
}

#[test]
fn full_publication_preserves_every_hook_failure_prefix_and_loaded_branch() {
    for imposter in [false, true] {
        let order = if imposter { vec![0, 1, 4, 5, 6, 7, 8, 9] } else { vec![0, 2, 3, 4, 5, 6, 7, 8, 9] };
        for (index, &fail) in order.iter().enumerate() {
            let mut effects = Publication { calls: vec![], fail: Some(fail), imposter, proto: false, before: false };
            assert_eq!(publish_full(&mut effects), Err(()));
            assert_eq!(effects.calls, order[..=index]);
        }
        let mut effects = Publication { calls: vec![], fail: None, imposter, proto: false, before: false };
        assert_eq!(publish_full(&mut effects), Ok(()));
        assert_eq!(effects.calls, order);
    }
}

#[test]
fn stage_completion_skips_non_proto_and_already_published_status() {
    for (proto, before, expected) in [(false, true, vec![10, 13]), (true, false, vec![10, 11, 13]), (true, true, vec![10, 11, 12, 13])] {
        let mut effects = Publication { calls: vec![], fail: None, imposter: false, proto, before };
        assert_eq!(complete_stage(&mut effects), Ok(()));
        assert_eq!(effects.calls, expected);
    }
    let mut effects = Publication { calls: vec![], fail: Some(12), imposter: false, proto: true, before: true };
    assert_eq!(complete_stage(&mut effects), Err(()));
    assert_eq!(effects.calls, vec![10, 11, 12]);
}

#[test]
fn registry_detaches_generation_incarnation_before_id_reuse() {
    use crate::world::level::chunk_holders::{Registry, Scheduled};
    use std::sync::Arc;
    let mut registry = Registry::new(44).unwrap();
    let old = Arc::new(Holder::new(12).unwrap());
    let Scheduled::New(id) = registry.schedule(7, 33, false, 45).unwrap() else { panic!() };
    registry.commit_new(7, id).unwrap();
    registry.attach_generation(id, old.clone()).unwrap();
    old.change(6, 0, 0);
    assert_eq!(Arc::strong_count(&old), 2);
    registry.schedule(7, 45, true, 33).unwrap();
    registry.drop_pass().unwrap();
    assert!(registry.finish_unload(7, id));
    assert_eq!(Arc::strong_count(&old), 1);
    let Scheduled::New(reused) = registry.schedule(8, 33, false, 45).unwrap() else { panic!() };
    assert_eq!(reused, id);
    registry.commit_new(8, reused).unwrap();
    let new = Arc::new(Holder::new(12).unwrap());
    registry.attach_generation(reused, new.clone()).unwrap();
    assert_eq!(old.change(7, 0, 0), 0, "late release still belongs to the old incarnation");
    assert_eq!(new.header.claims.load(Ordering::SeqCst), 0);
    assert!(!registry.finish_unload(7, id), "a stale unload must not detach the replacement");
    assert_eq!(Arc::strong_count(&new), 2);
}

#[test]
fn suspended_driver_matches_immediate_effect_trace_including_wait_and_signed_commands() {
    assert_eq!(std::mem::offset_of!(TaskDriver, header), 0);
    for pending in [false, true] {
        let reference = task(1);
        let mut immediate = Effects { task: &reference, calls: vec![], pending, fail_open: false, fail_close: false };
        let expected = reference.run(&mut immediate).unwrap();
        let actual = Arc::new(task(1));
        let mut driver = TaskDriver::new(actual.clone());
        let mut effects = Effects { task: &actual, calls: vec![], pending, fail_open: false, fail_close: false };
        let mut response = 0;
        let terminal = loop {
            let status = driver.next(response);
            if status != 1 { break status; }
            drop(actual.state.try_lock().expect("task lock survived continuation yield"));
            let c = driver.command();
            response = complete_ready(effects.call(c.op, c.a, c.b, c.c, c.token)).unwrap();
        };
        assert_eq!(terminal, if expected { 2 } else { 0 });
        assert_eq!(effects.calls, immediate.calls);
        assert_eq!(driver.next(0), terminal, "terminal polling cannot replay effects");
        assert_eq!(actual.state.lock().unwrap().scheduled, reference.state.lock().unwrap().scheduled);
    }
}

#[test]
fn suspended_driver_resumes_engine_failure_through_zone_close_before_termination() {
    let owner = Arc::new(task(1));
    let mut driver = TaskDriver::new(owner.clone());
    assert_eq!(driver.next(0), 1);
    assert_eq!(driver.command().op, 7);
    assert_eq!(driver.next(i64::MIN), 1, "open failure must still request original close");
    assert_eq!(driver.command().op, 8);
    assert_eq!(driver.next(i64::MIN), -1);
    assert_eq!(owner.state.lock().unwrap().scheduled, -1);
    assert_eq!(driver.next(0), -1);
}

#[test]
fn suspended_driver_pins_actual_task_and_reentrant_driver_cannot_overwrite_its_command() {
    let owner = Arc::new(task(0));
    let weak = Arc::downgrade(&owner);
    let mut outer = TaskDriver::new(owner.clone());
    drop(owner);
    assert_eq!(outer.next(0), 1);
    let captured = outer.command();
    assert_eq!(captured.op, 7);
    let inner_owner = weak.upgrade().expect("suspended continuation lost its actual task");
    inner_owner.cancel();
    let mut inner = TaskDriver::new(inner_owner);
    let mut status = inner.next(0);
    while status == 1 {
        assert_eq!(outer.command(), captured, "nested invocation overwrote borrowed outer command");
        assert!(matches!(inner.command().op, 9 | 10));
        status = inner.next(0);
    }
    assert_eq!(status, 0);
    drop(inner);
    assert!(weak.upgrade().is_some());
    drop(outer);
    assert!(weak.upgrade().is_none(), "released suspended frame retained its task Arc");
}


#[test]
fn live_pending_walk_owns_independent_cursor_and_checks_modification_after_last_callback() {
    let owner = Arc::new(Pending::new().unwrap());
    assert!(PendingWalk::new(owner.clone()).is_none());
    let a = owner.change(0, 0) as u32; assert_eq!(owner.change(1, a), 0);
    let b = owner.change(0, 0) as u32; assert_eq!(owner.change(1, b), 0);
    let mut outer = PendingWalk::new(owner.clone()).unwrap();
    assert_eq!(outer.next(), i64::from(a));
    let mut inner = PendingWalk::new(owner.clone()).unwrap();
    assert_eq!(inner.next(), i64::from(a)); assert_eq!(inner.next(), i64::from(b)); assert_eq!(inner.next(), 0);
    assert_eq!(outer.next(), i64::from(b));
    assert_eq!(owner.change(3, 0), 0);
    assert_eq!(outer.next(), -2, "last callback mutation must not look like normal termination");
    assert_eq!(inner.next(), -2);
}

#[test]
fn live_pending_walk_retains_actual_queue_allocation_and_releases_on_early_drop() {
    let owner = Arc::new(Pending::new().unwrap());
    let row = owner.change(0, 0) as u32; assert_eq!(owner.change(1, row), 0);
    let weak = Arc::downgrade(&owner);
    let mut walk = PendingWalk::new(owner.clone()).unwrap();
    drop(owner);
    assert!(weak.upgrade().is_some());
    assert_eq!(walk.next(), i64::from(row));
    drop(walk);
    assert!(weak.upgrade().is_none());
}

#[test]
fn lifecycle_retries_after_listener_failure_and_commits_the_live_reentrant_level() {
    let owner = Arc::new(Holder::new(12).unwrap());
    owner.levels.restore(super::super::chunk_holders::Levels { level: 33, old_level: 45, accessible: false });
    for (slot, value) in owner.levels.status_levels.iter().zip([33, 32, 31]) { slot.store(value, Ordering::SeqCst); }
    let mut first = super::request::lifecycle(owner.clone(), 0);
    let mut response = 0;
    let mut trace = Vec::new();
    loop {
        assert_eq!(first.next(response), 1);
        let command = first.header[0].load(Ordering::SeqCst);
        let op = command as u32 as i32; trace.push(op);
        if op == 44 { break; }
        response = 0;
    }
    assert_eq!(trace, [33, 34, 36, 37, 38, 35, 44]);
    assert_eq!(first.next(i64::MIN), -1);
    assert_eq!(owner.levels.old.load(Ordering::SeqCst), 45);
    assert!(owner.levels.snapshot().accessible);
    let mut retry = super::request::lifecycle(owner.clone(), 0);
    loop {
        let state = retry.next(0);
        if state == 0 { break; }
        assert_eq!(state, 1);
        if retry.header[0].load(Ordering::SeqCst) as u32 == 44 {
            owner.levels.level.store(32, Ordering::SeqCst);
        }
    }
    assert_eq!(owner.levels.old.load(Ordering::SeqCst), 32, "commit observes listener reentry, not the pre-callback snapshot");
}

#[test]
fn dependency_controller_queries_once_and_selects_the_live_combine_effect() {
    let owner = Arc::new(Holder::new(12).unwrap());
    for operation in [1, 2] {
        for done in [0, 1] {
            let mut driver = super::request::lifecycle(owner.clone(), operation);
            assert_eq!(driver.next(0), 1);
            let query = driver.header[0].load(Ordering::SeqCst);
            assert_eq!(query as u32, 30);
            assert_eq!((query >> 32) as i32, 14 + if operation == 1 { 4 } else { 5 });
            assert_eq!(driver.next(done), 1);
            assert_eq!(driver.header[0].load(Ordering::SeqCst) as u32, if done != 0 { 31 } else { 32 });
            assert_eq!(driver.next(0), 0);
        }
    }
}

#[test]
fn request_control_preserves_ticket_retry_live_queries_and_error_prefix() {
    fn advance(driver: &mut TaskDriver, response: i64, expected: i32) {
        assert_eq!(driver.next(response), 1);
        assert_eq!(driver.header[0].load(Ordering::SeqCst) as u32 as i32, expected);
    }
    let mut driver = super::request::request(None, 33, true, 0);
    advance(&mut driver, 0, 50); // initial visible holder
    advance(&mut driver, 1, 51); // custom holder: ticket before its first level read
    advance(&mut driver, 0, 52);
    advance(&mut driver, 34, 53);
    advance(&mut driver, 0, 54);
    advance(&mut driver, 0, 55);
    advance(&mut driver, 1, 56);
    advance(&mut driver, 0, 52);
    advance(&mut driver, 33, 52); // final independent absence test is not cached
    advance(&mut driver, 33, 58);
    assert_eq!(driver.next(0), 0);
    let mut failure = super::request::request(None, 33, true, 0);
    advance(&mut failure, 0, 50); advance(&mut failure, 1, 51);
    assert_eq!(failure.next(i64::MIN), -1, "throwing ticket effect stops before holder queries");
}

#[test]
fn request_graph_retains_actual_ticket_and_registry_allocations_without_a_java_cycle() {
    unsafe {
        use crate::world::level::{chunk_holders::ffi as holders, chunk_distance::ticket_storage::ffi as tickets};
        let ticket_id = tickets::mattmc_ticket_storage_create(45);
        let holder_id = holders::mattmc_chunk_holders_create(44);
        let ticket = tickets::retain_storage(ticket_id); let registry = holders::retain_registry(holder_id);
        let ticket_weak = Arc::downgrade(&ticket); let registry_weak = Arc::downgrade(&registry);
        let graph = ffi::mattmc_chunk_generation_request_graph_create(ticket_id, holder_id);
        tickets::mattmc_ticket_storage_release(ticket_id); holders::mattmc_chunk_holders_release(holder_id);
        drop(ticket); drop(registry);
        assert!(ticket_weak.upgrade().is_some()); assert!(registry_weak.upgrade().is_some());
        let driver = ffi::mattmc_chunk_generation_request_create(graph, 33, 0, 0);
        ffi::mattmc_chunk_generation_request_graph_release(graph);
        assert!(ticket_weak.upgrade().is_some()); assert!(registry_weak.upgrade().is_some());
        ffi::mattmc_chunk_generation_task_driver_release(driver);
        assert!(ticket_weak.upgrade().is_none()); assert!(registry_weak.upgrade().is_none());
    }
}

#[test]
fn direct_request_commands_preserve_custom_queries_and_retry_without_async_handoffs() {
    use super::request::{direct_begin, direct_next, TERMINAL};
    unsafe {
        let issued = direct_begin(None, 33, true, 0, false);
        assert_eq!(issued & 127, 68); let id = issued & !127;
        assert_eq!(direct_next(id, 1, 0, false), 52);
        assert_eq!(direct_next(id, 34, 0, false), 71);
        assert_eq!(direct_next(id, 1, 0, false), 52);
        assert_eq!(direct_next(id, 33, 0, false), 52, "repeat the original independent final query");
        assert_eq!(direct_next(id, 34, 0, false), TERMINAL | 59, "a reentrant final demotion returns the actual unloaded future");
        let issued = direct_begin(None, 0, false, 1, false); let id = issued & !127;
        assert_eq!(issued & 127, 72);
        assert_eq!(direct_next(id, 1, 33, false), 52);
        assert_eq!(direct_next(id, 33, 0, false), TERMINAL | 67 | 1 << 8);
    }
}

#[test]
fn direct_frame_pool_reuses_only_finished_frames_and_does_not_retain_owner_cycles() {
    unsafe {
        use crate::world::level::{chunk_holders::ffi as holders, chunk_distance::ticket_storage::ffi as tickets};
        let ticket_id = tickets::mattmc_ticket_storage_create(45);
        let holder_id = holders::mattmc_chunk_holders_create(44);
        let tickets = tickets::retain_storage(ticket_id); let weak = Arc::downgrade(&tickets); drop(tickets);
        let graph = ffi::mattmc_chunk_generation_request_graph_create(ticket_id, holder_id);
        tickets::mattmc_ticket_storage_release(ticket_id); holders::mattmc_chunk_holders_release(holder_id);
        let first = ffi::mattmc_chunk_generation_request_begin(graph, 33, 0, 0) & !127;
        let nested = ffi::mattmc_chunk_generation_request_begin(graph, 33, 0, 0) & !127;
        assert_ne!(first, nested, "active frames cannot be reused by a nested invocation");
        assert_eq!(ffi::mattmc_chunk_generation_request_next(first, 0, 0), super::request::TERMINAL | 59);
        let reused = ffi::mattmc_chunk_generation_request_begin_fast(graph, 33, 0, 0) & !127;
        assert_eq!(reused, first, "the complete terminal command is returned by value before reuse");
        ffi::mattmc_chunk_generation_request_graph_release(graph);
        assert!(weak.upgrade().is_some());
        ffi::mattmc_chunk_generation_request_abort(nested);
        assert!(weak.upgrade().is_some());
        ffi::mattmc_chunk_generation_request_abort(reused);
        assert!(weak.upgrade().is_none(), "free pooled frames contain no Arc back to their graph");
    }
}

#[test]
fn request_cache_keeps_insertion_order_and_distinguishes_null_create_policy() {
    let mut cache = request::RequestCache::default();
    let mut slots = Vec::new();
    for key in 0..4 { slots.push(cache.insert(key)); }
    assert_eq!(cache.find(0, 0xfff, false, false), Some(slots[0]));
    cache.insert(4);
    assert_eq!(cache.find(0, 0xfff, false, false), None, "a hit must not refresh insertion order");
    assert_eq!(cache.find(1, 0xfff, false, false), Some(slots[1]));
    let old = cache.insert(7);
    let latest = cache.insert(7);
    let matches = (1 << old) | (1 << latest) | (1 << (old + 4));
    assert_eq!(cache.find(7, matches, false, false), Some(latest));
    assert_eq!(cache.find(7, matches, true, false), Some(old), "create skips a null hit and can find an older matching entry");
    assert_eq!(cache.find(7, matches, true, true), Some(latest), "getChunkNow stops at its first status/key hit");
}

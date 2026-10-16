use super::ordered::{Fault, Table, NONE};

mod connected_inputs {
    use super::super::*;
    use super::super::ffi::{LayerInput, PropagationInput};

    fn map() -> Arc<LightMap> {
        unsafe {
            let registry = super::super::super::maps::ffi::mattmc_light_map_registry_create();
            let map = super::super::super::maps::ffi::mattmc_light_map_create(registry);
            super::super::super::maps::ffi::mattmc_light_map_registry_release(registry);
            Arc::from_raw(map)
        }
    }

    #[test]
    fn shared_registry_reuses_actual_queued_entry_and_missing_identity_has_no_effect() {
        unsafe {
            let registry=super::super::super::maps::ffi::mattmc_light_map_registry_create();
            let source=Arc::from_raw(super::super::super::maps::ffi::mattmc_light_map_create(registry));
            let target=Arc::from_raw(super::super::super::maps::ffi::mattmc_light_map_create(registry));
            super::super::super::maps::ffi::mattmc_light_map_registry_release(registry);
            let layer=Arc::new(Layer::new(3));
            let entry=source.native_entry(Arc::clone(&layer)).unwrap();let weak=Arc::downgrade(&entry);
            source.set(10,Some(Arc::clone(&entry)));
            let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&target),0);
            let mut out=[0i64;3];
            assert_eq!(ffi::mattmc_light_coordinator_queue_existing(owner,77,Arc::as_ptr(&layer),entry.identity+100,out.as_mut_ptr()),1);
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner,std::ptr::null(),QUEUED as i32,4,0,0,std::ptr::null(),out.as_mut_ptr()),0);
            assert_eq!(out[0],0);
            for key in [11,12] {
                assert_eq!(ffi::mattmc_light_coordinator_queue_existing(owner,key,Arc::as_ptr(&layer),entry.identity,out.as_mut_ptr()),0);
                assert_eq!(out[2],0,"reuse consumes no reservation");
                assert_eq!(ffi::mattmc_light_coordinator_collection(owner,std::ptr::null(),QUEUED as i32,1,key,0,std::ptr::null(),out.as_mut_ptr()),0);
                assert_eq!(out[0],i64::from(entry.id));
            }
            drop(entry);drop(source);assert!(weak.upgrade().is_some());
            ffi::mattmc_light_coordinator_release(owner);drop(target);
            assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn actual_cache_enabled_and_live_identity_feed_native_input_without_a_facade() {
        unsafe {
            let map = map();
            let first = Arc::new(Layer::new(5));
            map.set(0, Some(map.native_entry(first).unwrap()));
            let owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map), 0);
            let work = Arc::new(Queues::default());
            let binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 0);
            let input = PropagationInput::retain(binding.cast()).unwrap();
            let mut bytes = [0; 2048];
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Ready(false))));
            assert_eq!(bytes, [0x55; 2048]);
            let changed = Arc::new(Layer::new(9));
            map.set(0, Some(map.native_entry(Arc::clone(&changed)).unwrap()));
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Ready(false))));
            assert_eq!(bytes, [0x55; 2048], "native consumer preserves original get-cache timing");
            map.clear_cache(false);
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Ready(false))));
            assert_eq!(bytes, [0x99; 2048]);
            let mut output = [0i64; 2];
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner, binding, ENABLED as i32, 2, 0, 1, std::ptr::null(), output.as_mut_ptr()), 0);
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Ready(true))));
            changed.invalidate(); bytes.fill(0xab);
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Fallback)));
            assert_eq!(bytes, [0xab; 2048], "escaped input returns to Java before touching pass bytes");
            changed.fill(7);
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Ready(true))));
            assert_eq!(bytes, [0x77; 2048]);
            assert!(matches!(input.layer(999, &mut bytes), Ok(LayerInput::Missing)));
            ffi::mattmc_light_coordinator_release(owner);
            ffi::mattmc_light_coordinator_bind_release(binding);
            drop(input);
        }
    }

    #[test]
    fn input_pins_actual_parent_engine_and_map_after_external_release_then_releases_all() {
        unsafe {
            let map = map(); let weak_map = Arc::downgrade(&map);
            map.set(0, Some(map.native_entry(Arc::new(Layer::new(3))).unwrap()));
            let owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map), 0);
            let work = Arc::new(Queues::default()); let weak_work = Arc::downgrade(&work);
            let binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 0);
            let input = PropagationInput::retain(binding.cast()).unwrap();
            drop(work); drop(map);
            ffi::mattmc_light_coordinator_release(owner);
            ffi::mattmc_light_coordinator_bind_release(binding);
            assert!(weak_map.upgrade().is_some()); assert!(weak_work.upgrade().is_some());
            let mut bytes = [0; 2048];
            assert!(matches!(input.layer(0, &mut bytes), Ok(LayerInput::Ready(false))));
            assert_eq!(bytes, [0x33; 2048]);
            drop(input);
            assert!(weak_map.upgrade().is_none()); assert!(weak_work.upgrade().is_none());
            assert!(PropagationInput::retain(std::ptr::null()).is_none());
        }
    }
}

#[test]
fn node_table_initial_capacity_survives_remove_and_explicit_trim_is_independent() {
    let mut nodes = Table::with_expected(512, 0.5).unwrap();
    assert_eq!(nodes.table_len(), 1025);
    for key in 0..4096 { nodes.put(key, 1).unwrap(); }
    for key in 0..4096 { nodes.remove(key).unwrap(); }
    assert_eq!(nodes.table_len(), 1025);
    assert!(nodes.trim(1));
    assert_eq!(nodes.table_len(), 3);
    // Explicit trim does not lower minN: scalar removal cannot shrink below it.
    for key in 0..4096 { nodes.put(key, 1).unwrap(); }
    for key in 0..4096 { nodes.remove(key).unwrap(); }
    assert_eq!(nodes.table_len(), 1025);
}

#[test]
fn live_iterator_cleared_by_nested_publication_keeps_original_failure() {
    let mut affected = Table::with_expected(16, 0.75).unwrap();
    for key in [-1, 0, 1, 999, i64::MIN] { affected.put(key, 1).unwrap(); }
    let mut outer = affected.cursor();
    assert_eq!(affected.key_at(outer.next_entry(&affected).unwrap()).unwrap(), 0);
    let mut inner = affected.cursor();
    while inner.has_next() { inner.next_entry(&affected).unwrap(); }
    affected.clear_membership();
    assert!(outer.has_next());
    assert_eq!(outer.next_entry(&affected), Err(Fault::NullPointer));
}

#[test]
fn iterator_removal_keeps_wrapped_entries_and_null_values_distinct_from_membership() {
    let mut queued = Table::with_expected(16, 0.75).unwrap();
    for key in -2048..2048 { queued.put(key * 0x13123145, key as u32).unwrap(); }
    queued.put(i64::MIN, NONE).unwrap();
    assert!(queued.find(i64::MIN).is_ok());
    let mut cursor = queued.cursor();
    let mut seen = std::collections::HashSet::new();
    while cursor.has_next() {
        let index = cursor.next_entry(&queued).unwrap();
        let key = queued.key_at(index).unwrap();
        assert!(seen.insert(key));
        cursor.remove(&mut queued).unwrap();
    }
    assert_eq!(seen.len(), 4097);
    assert_eq!(queued.len(), 0);
}

mod composition {
    use super::super::*;
    use std::sync::atomic::Ordering;
    fn storage() -> State {
        unsafe {
            let registry = super::super::super::maps::ffi::mattmc_light_map_registry_create();
            let map = super::super::super::maps::ffi::mattmc_light_map_create(registry);
            super::super::super::maps::ffi::mattmc_light_map_registry_release(registry);
            State::new(false, Arc::from_raw(map)).unwrap()
        }
    }
    #[test]
    fn right_engine_cannot_drain_left_nodes_or_left_work_on_shared_storage() {
        let mut state = storage();
        let left_work = Arc::new(Queues::default()); let right_work = Arc::new(Queues::default());
        let (left, _) = state.attach(Arc::clone(&left_work), 2).unwrap();
        let (right, _) = state.attach(Arc::clone(&right_work), 2).unwrap();
        state.table_mut(left, NODES).unwrap().put(1, 1).unwrap();
        state.enqueue(left, 1, 8, 15).unwrap();
        let frame = state.begin(Op::Run, right).unwrap();
        assert!(matches!(state.step(frame, 0, None).unwrap(), Effect::Propagate));
        assert_eq!(state.table(left, NODES).unwrap().len(), 1);
        assert_eq!(state.table(right, NODES).unwrap().len(), 0);
        assert_eq!(left_work.data.lock().unwrap()[1].len(), 2);
        assert_eq!(right_work.data.lock().unwrap()[1].len(), 0);
        assert!(matches!(state.step(frame, 0, None).unwrap(), Effect::ClearChunkCache));
        assert!(matches!(state.step(frame, 0, None).unwrap(), Effect::Done(0)));
        state.end(frame).unwrap();
    }
    #[test]
    fn nested_engine_frame_and_throw_abandonment_preserve_origin_and_pending_nodes() {
        let mut state = storage();
        let (left, _) = state.attach(Arc::new(Queues::default()), 2).unwrap();
        let (right, _) = state.attach(Arc::new(Queues::default()), 2).unwrap();
        state.table_mut(left, NODES).unwrap().put(111, 1).unwrap();
        state.table_mut(right, NODES).unwrap().put(222, 1).unwrap();
        let outer = state.begin(Op::Run, left).unwrap();
        assert!(matches!(state.step(outer, 0, None).unwrap(), Effect::CustomNode(111)));
        let inner = state.begin(Op::Run, right).unwrap();
        assert!(matches!(state.step(inner, 0, None).unwrap(), Effect::CustomNode(222)));
        assert!(matches!(state.step(inner, 0, None).unwrap(), Effect::Propagate));
        state.end(inner).unwrap();
        // A Java throw ends only this continuation, not its admitted node table.
        state.end(outer).unwrap();
        assert_eq!(state.table(left, NODES).unwrap().len(), 1);
        assert_eq!(state.table(right, NODES).unwrap().len(), 0);
        let retry = state.begin(Op::Run, left).unwrap();
        assert!(matches!(state.step(retry, 0, None).unwrap(), Effect::CustomNode(111)));
        state.end(retry).unwrap();
    }
    #[test]
    fn retiring_one_binding_keeps_its_inflight_frame_and_other_engine_alive() {
        let mut state = storage();
        let work = Arc::new(Queues::default()); let weak = Arc::downgrade(&work);
        let (left, _) = state.attach(work, 2).unwrap();
        let (right, _) = state.attach(Arc::new(Queues::default()), 2).unwrap();
        state.table_mut(right, NODES).unwrap().put(333, 1).unwrap();
        let frame = state.begin(Op::Run, left).unwrap();
        state.detach(left);
        assert!(weak.upgrade().is_some());
        assert!(matches!(state.step(frame, 0, None).unwrap(), Effect::Propagate));
        state.end(frame).unwrap();
        assert!(weak.upgrade().is_none());
        assert_eq!(state.table(right, NODES).unwrap().len(), 1);
        state.detach(right);
        assert!(state.engines.is_empty());
    }
    #[test]
    fn published_roots_and_contained_layer_generations_survive_later_cow() {
        let mut state = storage(); let key = propagation::section_pos(0, 1, 0);
        let init = state.begin(Op::Status(key, false), 0).unwrap();
        assert!(matches!(state.step(init, 0, None).unwrap(), Effect::Done(_))); state.end(init).unwrap();
        let publish = state.begin(Op::Publish, 0).unwrap();
        let Effect::Publish(visible) = state.step(publish, 0, None).unwrap() else { panic!("missing snapshot"); };
        loop { if matches!(state.step(publish, 0, None).unwrap(), Effect::Done(_)) { break; } }
        state.end(publish).unwrap();
        let old = visible.raw(key).unwrap(); let old_generation = old.layer.as_ref().unwrap().view();
        let write = state.begin(Op::WriteStored(propagation::block_pos(8,24,8), 15), 0).unwrap();
        assert!(matches!(state.step(write, 0, None).unwrap(), Effect::Done(_))); state.end(write).unwrap();
        let current = state.updating.raw(key).unwrap();
        assert_ne!(current.identity, old.identity);
        assert_eq!(old_generation.get(index(propagation::block_pos(8,24,8))), Ok(0));
        assert_eq!(old.layer.as_ref().unwrap().view().get(index(propagation::block_pos(8,24,8))), Ok(0));
        assert_eq!(current.layer.as_ref().unwrap().view().get(index(propagation::block_pos(8,24,8))), Ok(15));
        drop(state);
        assert_eq!(visible.raw(key).unwrap().layer.as_ref().unwrap().view().get(0), Ok(0));
    }
    #[test]
    fn ffi_frame_owns_actual_storage_after_parent_and_binding_are_released() {
        unsafe {
            let map = storage().updating;
            let owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map), 0);
            Arc::increment_strong_count(owner); let observing = Arc::from_raw(owner); let weak = Arc::downgrade(&observing); drop(observing);
            let work = Arc::new(Queues::default());
            let binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 2);
            let mut out = [0i64; 6];
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner, binding, 0, 2, 444, 1, std::ptr::null(), out.as_mut_ptr()), 0);
            let frame = ffi::mattmc_light_coordinator_begin(owner, binding, 5, 0, 0);
            ffi::mattmc_light_coordinator_release(owner);
            ffi::mattmc_light_coordinator_bind_release(binding);
            assert!(weak.upgrade().is_some());
            assert_eq!(ffi::mattmc_light_coordinator_step(owner, frame, 0, std::ptr::null(), out.as_mut_ptr()), 14);
            assert_eq!(out[0], 444);
            assert_eq!(ffi::mattmc_light_coordinator_end(owner, frame), 0);
            assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn ffi_live_walk_owns_storage_and_engine_table_without_external_bindings() {
        unsafe {
            let map = storage().updating;
            let owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map), 0);
            Arc::increment_strong_count(owner); let observing = Arc::from_raw(owner); let weak = Arc::downgrade(&observing); drop(observing);
            let work = Arc::new(Queues::default());
            let binding = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 2);
            let mut out = [0i64; 3];
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner, binding, 0, 2, 555, 1, std::ptr::null(), out.as_mut_ptr()), 0);
            let walk = ffi::mattmc_light_coordinator_walk_create(owner, binding, 0);
            ffi::mattmc_light_coordinator_release(owner);
            ffi::mattmc_light_coordinator_bind_release(binding);
            assert!(weak.upgrade().is_some());
            assert_eq!(ffi::mattmc_light_coordinator_walk(owner, walk, 0, out.as_mut_ptr()), 0);
            assert_eq!(out[0], 555);
            ffi::mattmc_light_coordinator_walk_release(walk);
            assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn eligibility_combines_local_work_shared_publication_and_custom_effects() {
        unsafe {
            let map = storage().updating;
            let owner = ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map), 0);
            let left_work = Arc::new(Queues::default()); let right_work = Arc::new(Queues::default());
            let left = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&left_work), 0);
            let right = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&right_work), 0);
            let custom = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&Arc::new(Queues::default())), 2);
            let left_header = &*ffi::mattmc_light_coordinator_bind_header(left);
            let right_header = &*ffi::mattmc_light_coordinator_bind_header(right);
            let custom_header = &*ffi::mattmc_light_coordinator_bind_header(custom);
            assert_eq!(std::mem::size_of::<EngineProjection>(), 16);
            assert_eq!(left_header.idle_update.load(Ordering::Acquire), 1);
            assert_eq!(custom_header.idle_update.load(Ordering::Acquire), 0);
            let mut out = [0i64; 6];
            // Shared changed/affected-only work is deliberately absent from
            // public hasLightWork, but must prevent either empty-update bypass.
            for table in [CHANGED, AFFECTED] {
                assert_eq!(ffi::mattmc_light_coordinator_collection(owner,left,table as i32,2,44,1,std::ptr::null(),out.as_mut_ptr()),0);
                assert_eq!(left_header.pending.load(Ordering::Acquire),0);
                assert_eq!(left_header.idle_update.load(Ordering::Acquire),0);
                assert_eq!(right_header.idle_update.load(Ordering::Acquire),0);
                assert_eq!(ffi::mattmc_light_coordinator_collection(owner,left,table as i32,5,0,0,std::ptr::null(),out.as_mut_ptr()),0);
            }
            // One engine's private work does not consume or invalidate its
            // sibling's genuinely empty work over otherwise clean storage.
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner,left,0,2,55,1,std::ptr::null(),out.as_mut_ptr()),0);
            assert_eq!(left_header.pending.load(Ordering::Acquire),1);
            assert_eq!(right_header.idle_update.load(Ordering::Acquire),1);
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner,left,0,5,0,0,std::ptr::null(),out.as_mut_ptr()),0);
            // Queue producer occurs after eligibility was observed. Clearing
            // Java's chunk cache never resets this newer native dirty mark.
            assert_eq!(left_header.idle_update.load(Ordering::Acquire),1);
            unsafe extern "C" { fn mattmc_light_work_enqueue(owner:*const std::ffi::c_void,side:i32,pos:i64,entry:i64)->i32; }
            let work = mattmc_light_work_enqueue;
            assert_eq!(work(Arc::as_ptr(&left_work).cast(),1,88,15),0);
            assert_eq!(left_header.idle_update.load(Ordering::Acquire),0);
            assert_eq!(left_header.pending.load(Ordering::Acquire),1);
            let inputs = left_work.take();
            assert_eq!(left_header.pending.load(Ordering::Acquire),0);
            assert_eq!(left_header.idle_update.load(Ordering::Acquire),0);
            // Rejection restores original inputs after callback additions.
            assert_eq!(work(Arc::as_ptr(&left_work).cast(),0,99,3),0);
            left_work.finish(inputs,true);
            assert_eq!(left_header.pending.load(Ordering::Acquire),1);
            assert_eq!(left_header.idle_update.load(Ordering::Acquire),0);
            ffi::mattmc_light_coordinator_bind_release(custom);
            ffi::mattmc_light_coordinator_bind_release(right);
            ffi::mattmc_light_coordinator_bind_release(left);
            ffi::mattmc_light_coordinator_release(owner);
        }
    }
    #[test]
    fn empty_enlarged_node_table_is_not_eligible_until_original_trim() {
        let mut state = storage();
        let (engine, _) = state.attach(Arc::new(Queues::default()),0).unwrap();
        let nodes=state.table_mut(engine,NODES).unwrap();
        for key in 0..4096 {nodes.put(key,1).unwrap();}
        let mut cursor=nodes.cursor();
        while cursor.has_next() {cursor.next_entry(nodes).unwrap();cursor.remove(nodes).unwrap();}
        assert_eq!(nodes.len(),0);assert!(nodes.table_len()>1025);
        let frame=state.begin(Op::Run,engine).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::Propagate));
        assert_eq!(state.table(engine,NODES).unwrap().table_len(),1025);
        state.end(frame).unwrap();
    }
    #[test]
    fn concurrent_empty_scan_cannot_overwrite_completed_queue_producer() {
        use std::sync::Barrier;
        for _ in 0..128 {
            let queues=Arc::new(Queues::default());let projection=Arc::new(EngineProjection::default());
            queues.bind_projection(&projection);
            let barrier=Arc::new(Barrier::new(2));
            let worker={let q=Arc::clone(&queues);let p=Arc::clone(&projection);let b=Arc::clone(&barrier);
                std::thread::spawn(move||{b.wait();q.publish_coordinator(&p,false,true);})};
            barrier.wait();
            {let mut data=queues.data.lock().unwrap();data[1].extend([3,15]);queues.publish(&data);}
            worker.join().unwrap();
            assert_eq!(projection.idle_update.load(Ordering::Acquire),0);
            assert_eq!(projection.pending.load(Ordering::Acquire),1);
        }
    }
    #[test]
    fn captured_key_generation_sees_pre_rehash_writes_then_keeps_original_array() {
        let mut table=Table::with_expected(16,0.75).unwrap();
        for key in 0..8 {table.put(key,1).unwrap();}
        let old=table.captured_keys();
        assert_eq!(old.len(),33);
        assert!(!old.iter().any(|key|key.load(Ordering::Relaxed)==114));
        for key in 100..500 {table.put(key,1).unwrap();}
        assert!(!Arc::ptr_eq(&old,&table.captured_keys()));
        assert!(old.iter().any(|key|key.load(Ordering::Relaxed)==114));
        assert!(!old.iter().any(|key|key.load(Ordering::Relaxed)==499));
        let retained=old.iter().map(|key|key.load(Ordering::Relaxed)).collect::<Vec<_>>();
        table.clear_membership();drop(table);
        assert_eq!(retained,old.iter().map(|key|key.load(Ordering::Relaxed)).collect::<Vec<_>>());
    }
    #[test]
    fn captured_object_values_retain_actual_entries_after_rehash_remove_and_owner_drop() {
        let mut state=storage();let entry=state.new_layer(Layer::new(3)).unwrap();
        let slot=entry.id;let weak=Arc::downgrade(&entry);
        state.put_queued(1,Some(entry)).unwrap();
        let at=state.tables[QUEUED].find(1).unwrap();
        let old=state.tables[QUEUED].captured_values();
        for key in 2..100 {let entry=state.new_layer(Layer::new(7)).unwrap();state.put_queued(key,Some(entry)).unwrap();}
        assert!(!Arc::ptr_eq(&old,&state.tables[QUEUED].captured_values()));
        drop(state.remove_queued(1).unwrap());drop(state);
        assert!(weak.upgrade().is_some());
        let held=old.entry_at(at).unwrap();assert_eq!(held.id,slot);
        assert_eq!(held.layer.as_ref().unwrap().view().get(0),Ok(3));drop(held);
        assert!(old.entry_at(at).is_some());
        // No registry/page index can substitute for this typed array lease.
        assert!(at<33);drop(old);assert!(weak.upgrade().is_none());
    }
    #[test]
    fn clearing_current_captured_values_releases_entries_without_replacing_array() {
        let mut state=storage();let entry=state.new_layer(Layer::new(3)).unwrap();
        let slot=entry.id;let weak=Arc::downgrade(&entry);
        state.put_queued(0,Some(entry)).unwrap();let at=state.tables[QUEUED].find(0).unwrap();let values=state.tables[QUEUED].captured_values();
        assert_eq!(values.entry_at(at).unwrap().id,slot);
        state.tables[QUEUED].clear_membership();
        assert!(Arc::ptr_eq(&values,&state.tables[QUEUED].captured_values()));
        assert!(values.entry_at(at).is_none());assert!(weak.upgrade().is_none());
    }
    fn edit(which:usize,kind:i32,key:i64)->CollectionEdit{
        CollectionEdit{which,kind,key,pos:0,input:0,input_entry:None,held_old:None}
    }
    #[test]
    fn callback_commit_uses_original_slot_after_reentrant_insertion_or_removal(){
        let mut state=storage();
        let frame=state.begin(Op::CollectionStart(edit(STATES,0,13)),0).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::CollectionCall(0,13,NONE)));
        state.tables[STATES].put(13,9).unwrap();
        assert!(matches!(state.step(frame,11,None).unwrap(),Effect::Done(11)));state.end(frame).unwrap();
        assert_eq!(state.tables[STATES].len(),2);assert_eq!(state.tables[STATES].get(13),11);
        // Match the original callback's stale positional replacement, even
        // when it writes an unreachable value after removing the original key.
        let mut state=storage();state.tables[STATES].put(1,7).unwrap();
        let at=state.tables[STATES].find(1).unwrap();
        let frame=state.begin(Op::CollectionStart(edit(STATES,2,1)),0).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::CollectionCall(0,1,7)));
        state.tables[STATES].remove(1).unwrap();state.tables[STATES].put(3,9).unwrap();
        assert!(matches!(state.step(frame,11,None).unwrap(),Effect::Done(11)));state.end(frame).unwrap();
        assert_eq!(state.tables[STATES].len(),1);assert!(state.tables[STATES].find(1).is_err());
        assert_eq!(state.tables[STATES].value_at(at as i32),Ok(11));
    }
    #[test]
    fn thrown_callback_abandons_commit_without_undoing_callback_mutations(){
        let mut state=storage();state.tables[STATES].put(1,7).unwrap();
        let frame=state.begin(Op::CollectionStart(edit(STATES,3,1)),0).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::CollectionCall(0,1,7)));
        state.tables[STATES].remove(1).unwrap();state.tables[STATES].put(3,9).unwrap();
        state.end(frame).unwrap();
        assert_eq!(state.tables[STATES].len(),1);assert_eq!(state.tables[STATES].get(3),9);
        assert!(state.tables[STATES].find(1).is_err());
    }
    #[test]
    fn suspended_callback_retains_old_typed_entry_until_commit_or_throw(){
        let mut state=storage();let entry=state.new_layer(Layer::new(7)).unwrap();
        let slot=entry.id;let weak=Arc::downgrade(&entry);state.put_queued(1,Some(entry)).unwrap();
        let frame=state.begin(Op::CollectionStart(edit(QUEUED,2,1)),0).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::CollectionCall(0,1,old) if old==slot));
        drop(state.remove_queued(1).unwrap());assert!(weak.upgrade().is_some());
        state.end(frame).unwrap();assert!(weak.upgrade().is_none());
    }
    #[test]
    fn bulk_throw_keeps_count_and_nested_bulk_cannot_retarget_outer_array(){
        let mut table=Table::with_expected(16,0.75).unwrap();for key in 0..8{table.put(key,1).unwrap();}
        let mut cursor=table.cursor();let mut outer=ordered::Bulk::new(0,&table).unwrap();
        assert_eq!(outer.next(&mut cursor,&table).unwrap(),Some((32,0)));
        assert_eq!(cursor.mark().left(),8);
        // Callback throws: no commit, but zero was already consumed.
        assert!(cursor.has_next());
        for key in 100..500{table.put(key,1).unwrap();}
        let _inner=ordered::Bulk::new(0,&table).unwrap();
        outer.commit(&mut cursor);
        assert_eq!(outer.next(&mut cursor,&table).unwrap().unwrap().1,114);
    }
    #[test]
    fn ffi_bulk_lease_keeps_walk_engine_and_actual_storage_after_all_external_releases(){
        unsafe{
            let map=storage().updating;let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map),0);
            Arc::increment_strong_count(owner);let watching=Arc::from_raw(owner);let weak=Arc::downgrade(&watching);drop(watching);
            let work=Arc::new(Queues::default());let binding=ffi::mattmc_light_coordinator_bind(owner,Arc::as_ptr(&work),0);
            let mut out=[0i64;6];
            assert_eq!(ffi::mattmc_light_coordinator_collection(owner,binding,0,2,0,1,std::ptr::null(),out.as_mut_ptr()),0);
            let walk=ffi::mattmc_light_coordinator_walk_create(owner,binding,0);
            let bulk=ffi::mattmc_light_coordinator_bulk_begin(walk,0);assert_ne!(bulk,0);
            ffi::mattmc_light_coordinator_walk_release(walk);
            ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
            assert!(weak.upgrade().is_some());
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(bulk,0,out.as_mut_ptr()),0);assert_eq!(&out[..3],&[1,1024,0]);
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(bulk,1,out.as_mut_ptr()),0);
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(bulk,0,out.as_mut_ptr()),0);assert_eq!(out[0],0);
            ffi::mattmc_light_coordinator_bulk_end(bulk);assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn ffi_nested_bulk_captures_are_per_invocation_and_throw_does_not_commit_count(){
        unsafe{
            let map=storage().updating;let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map),0);
            let work=Arc::new(Queues::default());let binding=ffi::mattmc_light_coordinator_bind(owner,Arc::as_ptr(&work),0);
            let mut out=[0i64;6];
            for key in 0..8{assert_eq!(ffi::mattmc_light_coordinator_collection(owner,binding,4,2,key,1,std::ptr::null(),out.as_mut_ptr()),0);}
            let walk=ffi::mattmc_light_coordinator_walk_create(owner,binding,4);let outer=ffi::mattmc_light_coordinator_bulk_begin(walk,0);
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(outer,0,out.as_mut_ptr()),0);assert_eq!(out[2],0);
            for key in 100..500{assert_eq!(ffi::mattmc_light_coordinator_collection(owner,binding,4,2,key,1,std::ptr::null(),out.as_mut_ptr()),0);}
            let inner=ffi::mattmc_light_coordinator_bulk_begin(walk,0);ffi::mattmc_light_coordinator_bulk_end(inner);
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(outer,1,out.as_mut_ptr()),0);
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(outer,0,out.as_mut_ptr()),0);assert_eq!(out[2],114);
            // Abandon this second callback before its count decrement.
            ffi::mattmc_light_coordinator_bulk_end(outer);
            assert_eq!(ffi::mattmc_light_coordinator_walk(owner,walk,2,out.as_mut_ptr()),0);assert_eq!(out[0],1);
            ffi::mattmc_light_coordinator_walk_release(walk);ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
        }
    }
    #[test]
    fn contains_value_callback_retains_original_object_array_generation(){
        let mut state=storage();for key in 1..9{let entry=state.new_layer(Layer::new(key as i32)).unwrap();state.put_queued(key,Some(entry)).unwrap();}
        let frame=state.begin(Op::ValuesStart(QUEUED,0),0).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::CollectionCall(0,0,_)));
        state.tables[QUEUED].clear_membership();let mut weak=None;
        for key in 100..500{let entry=state.new_layer(Layer::new((key%16) as i32)).unwrap();if key==100{weak=Some(Arc::downgrade(&entry));}state.put_queued(key,Some(entry)).unwrap();}
        state.tables[QUEUED].clear_membership();
        assert!(weak.as_ref().unwrap().upgrade().is_some());
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::CollectionCall(0,0,_)));
        state.end(frame).unwrap();assert!(weak.unwrap().upgrade().is_none());
    }
    #[test]
    fn split_advance_and_bulk_keep_their_distinct_callback_commit_points(){
        let mut table=Table::with_expected(16,0.75).unwrap();for key in 0..8{table.put(key,1).unwrap();}
        let mut range=ordered::Range::new(&table);let mut advance=ordered::RangeBulk::new(&table,false,true);
        assert_eq!(advance.next(&mut range,&table).unwrap(),Some((32,0)));
        assert_eq!(range.info(&table,0),(7,321)); // tryAdvance committed before callback throw.
        let mut range=ordered::Range::new(&table);let mut bulk=ordered::RangeBulk::new(&table,true,true);
        assert_eq!(bulk.next(&mut range,&table).unwrap(),Some((32,0)));
        assert_eq!(range.info(&table,0),(8,321)); // set forEach zero count remains uncommitted.
        bulk.commit(&mut range);assert_eq!(range.info(&table,0),(7,321));
        let mut range=ordered::Range::new(&table);let mut bulk=ordered::RangeBulk::new(&table,true,false);
        assert_eq!(bulk.next(&mut range,&table).unwrap(),Some((32,0)));
        assert_eq!(range.info(&table,1),(7,65)); // map zero count commits before the callback.
        assert_eq!(range.skip(&table,1),Ok(1));
        assert_eq!(range.info(&table,1),(7,65)); // original skip leaves c unchanged.
    }
    unsafe fn bound_set()->(*mut ffi::Owner,*mut ffi::Binding){
        let map=storage().updating;let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map),0);
        let work=Arc::new(Queues::default());let binding=ffi::mattmc_light_coordinator_bind(owner,Arc::as_ptr(&work),0);
        let mut out=[0i64;2];for key in 0..8{assert_eq!(ffi::mattmc_light_coordinator_collection(owner,binding,4,2,key,1,std::ptr::null(),out.as_mut_ptr()),0);}
        (owner,binding)
    }
    #[test]
    fn ffi_cancelled_staged_split_preserves_donor_and_committed_child_releases_parent(){
        unsafe{
            let (owner,binding)=bound_set();let range=ffi::mattmc_light_coordinator_split_create(owner,binding,4);
            let mut out=[0i64;2];assert_eq!(ffi::mattmc_light_coordinator_split_query(range,0,0,out.as_mut_ptr()),0);assert_eq!(out,[8,321]);
            let child=ffi::mattmc_light_coordinator_split_prepare(range);
            Arc::increment_strong_count(child);let watching=Arc::from_raw(child);let weak_child=Arc::downgrade(&watching);drop(watching);
            ffi::mattmc_light_coordinator_split_release(child);assert!(weak_child.upgrade().is_none());
            assert_eq!(ffi::mattmc_light_coordinator_split_query(range,0,0,out.as_mut_ptr()),0);assert_eq!(out,[8,321]);
            let child=ffi::mattmc_light_coordinator_split_prepare(range);
            Arc::increment_strong_count(range);let watching=Arc::from_raw(range);let weak_parent=Arc::downgrade(&watching);drop(watching);
            ffi::mattmc_light_coordinator_split_release(range);assert!(weak_parent.upgrade().is_some());
            assert_eq!(ffi::mattmc_light_coordinator_split_commit(child),0);assert!(weak_parent.upgrade().is_none());
            assert_eq!(ffi::mattmc_light_coordinator_split_query(child,0,0,out.as_mut_ptr()),0);assert_eq!(out,[4,257]);
            ffi::mattmc_light_coordinator_split_release(child);ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
        }
    }
    #[test]
    fn ffi_split_bulk_retains_actual_range_walk_and_storage_without_external_handles(){
        unsafe{
            let (owner,binding)=bound_set();
            Arc::increment_strong_count(owner);let watching=Arc::from_raw(owner);let weak=Arc::downgrade(&watching);drop(watching);
            let range=ffi::mattmc_light_coordinator_split_create(owner,binding,4);
            let bulk=ffi::mattmc_light_coordinator_split_bulk_begin(range,1);assert_ne!(bulk,0);
            ffi::mattmc_light_coordinator_split_release(range);ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
            assert!(weak.upgrade().is_some());let mut out=[0i64;3];
            assert_eq!(ffi::mattmc_light_coordinator_bulk_step(bulk,0,out.as_mut_ptr()),0);assert_eq!(out,[1,32,0]);
            ffi::mattmc_light_coordinator_bulk_end(bulk);assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn split_nonzero_bulk_throw_retries_same_position_while_advance_commits_it(){
        let mut table=Table::with_expected(16,0.75).unwrap();for key in 1..8{table.put(key,1).unwrap();}
        let mut range=ordered::Range::new(&table);let mut bulk=ordered::RangeBulk::new(&table,true,true);
        let selected=bulk.next(&mut range,&table).unwrap().unwrap();
        assert_eq!(range.info(&table,0),(7,321));
        drop(bulk);let mut retry=ordered::RangeBulk::new(&table,false,true);
        assert_eq!(retry.next(&mut range,&table).unwrap(),Some(selected));assert_eq!(range.info(&table,0),(6,321));
    }
    #[test]
    fn queued_reservation_receipt_covers_rejection_and_modeled_post_transfer_failure() {
        unsafe {
            use super::super::super::maps::ffi as map_ffi;
            let raw = map_ffi::mattmc_light_map_registry_create();
            let mut state = State::new(false, Arc::from_raw(map_ffi::mattmc_light_map_create(raw))).unwrap();
            let registry = Arc::from_raw(raw);
            let foreign = Arc::from_raw(map_ffi::mattmc_light_map_registry_create());
            let reservation = foreign.reserve().unwrap(); let slot = reservation.slot;
            let reservation = Box::into_raw(Box::new(reservation)); let mut out = [0; 3];
            assert!(ffi::queue_admit(&mut state, 7, reservation, std::ptr::null(), 42, &mut out, || Ok(())).is_err());
            assert_eq!(out[2], 0); drop(Box::from_raw(reservation));
            assert_eq!(foreign.reserve().unwrap().slot, slot);
            let reservation = registry.reserve().unwrap(); let slot = reservation.slot;
            let reservation = Box::into_raw(Box::new(reservation));
            let result = ffi::queue_admit(&mut state, 7, reservation, std::ptr::null(), 42, &mut out,
                || Err(Error::Table(Fault::Allocation)));
            assert!(matches!(result, Err(Error::Table(Fault::Allocation))));
            assert_eq!(out[2], 1); assert!(state.queued_entry(7).is_none()); assert!(!state.inconsistent);
            let mut retired = [0; 1]; assert_eq!(registry.take_retired_into(&mut retired), 1);
            assert_eq!(retired[0], slot); assert!(registry.acknowledge(&retired));
            assert_eq!(registry.take_retired_into(&mut retired), 0);
            assert_eq!(registry.reserve().unwrap().slot, slot);
        }
    }
    #[test]
    fn ffi_queued_admission_publishes_both_bindings_and_null_removal_preserves_pending() {
        unsafe {
            use super::super::super::maps::ffi as map_ffi;
            let registry = map_ffi::mattmc_light_map_registry_create();
            let map = map_ffi::mattmc_light_map_create(registry);
            let owner = ffi::mattmc_light_coordinator_create(map, 0);
            let work = Arc::new(Queues::default());
            let left = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&work), 0);
            let right_work = Arc::new(Queues::default());
            let right = ffi::mattmc_light_coordinator_bind(owner, Arc::as_ptr(&right_work), 0);
            let layer = Arc::new(Layer::new(7)); let weak = Arc::downgrade(&layer);
            let mut slot = 0; let reservation = map_ffi::mattmc_light_map_reserve(registry, &mut slot);
            let mut out = [0; 3];
            assert_eq!(ffi::mattmc_light_coordinator_queue(owner, 7, reservation, Arc::as_ptr(&layer), 42, out.as_mut_ptr()), 0);
            assert_eq!(out[2], 1); drop(layer);
            assert!(weak.upgrade().is_some());
            for binding in [left, right] {
                let projection = &*ffi::mattmc_light_coordinator_bind_header(binding);
                assert_eq!(projection.pending.load(Ordering::Acquire), 1);
                assert_eq!(projection.idle_update.load(Ordering::Acquire), 0);
            }
            assert_eq!(ffi::mattmc_light_coordinator_queue(owner, 7, std::ptr::null_mut(), std::ptr::null(), 0, out.as_mut_ptr()), 0);
            assert_eq!(out[2], 0); assert!(weak.upgrade().is_none());
            assert_eq!((&*ffi::mattmc_light_coordinator_bind_header(left)).pending.load(Ordering::Acquire), 1);
            ffi::mattmc_light_coordinator_bind_release(left); ffi::mattmc_light_coordinator_bind_release(right);
            ffi::mattmc_light_coordinator_release(owner);
            map_ffi::mattmc_light_map_release(map); map_ffi::mattmc_light_map_registry_release(registry);
        }
    }

    #[test]
    fn queued_batch_rejection_and_modeled_consumed_failure_preserve_ownership_receipts(){
        unsafe{
            use super::super::super::maps::{ffi as map_ffi,registry::ReservationBatch};
            let raw=map_ffi::mattmc_light_map_registry_create();let mut state=State::new(false,Arc::from_raw(map_ffi::mattmc_light_map_create(raw))).unwrap();let registry=Arc::from_raw(raw);
            let foreign=Arc::from_raw(map_ffi::mattmc_light_map_registry_create());let wrong=ReservationBatch::new(&foreign,4).unwrap();let mut out=[0;3];
            assert!(ffi::queue_batch_admit(&mut state,7,Arc::as_ptr(&wrong),0,std::ptr::null(),42,&mut out,||Ok(())).is_err());assert_eq!(out[2],0);assert!(wrong.take(0).is_some());
            let batch=ReservationBatch::new(&registry,4).unwrap();
            let result=ffi::queue_batch_admit(&mut state,7,Arc::as_ptr(&batch),0,std::ptr::null(),42,&mut out,||Err(Error::Table(Fault::Allocation)));
            assert!(matches!(result,Err(Error::Table(Fault::Allocation))));assert_eq!(out[2],1);assert!(batch.take(0).is_none());assert!(state.queued_entry(7).is_none());assert!(!state.inconsistent);
            let mut retired=[0];assert_eq!(registry.take_retired_into(&mut retired),1);assert!(registry.acknowledge(&retired));assert_eq!(registry.take_retired_into(&mut retired),0);
        }
    }
    #[test]
    fn actual_batch_queue_keeps_typed_layer_after_batch_release_and_rejects_replay(){
        unsafe{
            use super::super::super::maps::{ffi as map_ffi,registry::ReservationBatch};
            let registry=map_ffi::mattmc_light_map_registry_create();let map=map_ffi::mattmc_light_map_create(registry);let owner=ffi::mattmc_light_coordinator_create(map,0);
            Arc::increment_strong_count(registry);let registry_arc=Arc::from_raw(registry);let batch=ReservationBatch::new(&registry_arc,2).unwrap();
            let layer=Arc::new(Layer::new(7));let weak=Arc::downgrade(&layer);let mut out=[0;3];
            assert_eq!(ffi::mattmc_light_coordinator_queue_batch(owner,7,Arc::as_ptr(&batch),0,Arc::as_ptr(&layer),42,out.as_mut_ptr()),0);assert_eq!(out[2],1);
            assert_eq!(ffi::mattmc_light_coordinator_queue_batch(owner,9,Arc::as_ptr(&batch),0,Arc::as_ptr(&layer),42,out.as_mut_ptr()),-27);assert_eq!(out[2],0);
            drop(layer);drop(batch);assert!(weak.upgrade().is_some());
            assert_eq!(ffi::mattmc_light_coordinator_queue_batch(owner,7,std::ptr::null(),-1,std::ptr::null(),0,out.as_mut_ptr()),0);assert!(weak.upgrade().is_none());
            ffi::mattmc_light_coordinator_release(owner);map_ffi::mattmc_light_map_release(map);map_ffi::mattmc_light_map_registry_release(registry);
        }
    }
}


#[cfg(test)]
mod sky_source_producer {
    use super::super::*;
    use std::sync::atomic::Ordering;
    fn storage()->State{unsafe{
        let registry=super::super::super::maps::ffi::mattmc_light_map_registry_create();
        let map=super::super::super::maps::ffi::mattmc_light_map_create(registry);
        super::super::super::maps::ffi::mattmc_light_map_registry_release(registry);
        State::new(true,Arc::from_raw(map)).unwrap()
    }}
    fn put(state:&State,y:i32){state.updating.set(propagation::section_pos(0,y,0),Some(state.new_layer(Layer::new(0)).unwrap()));}
    #[test]
    fn whole_column_cow_queue_order_and_first_non_below_stop_are_connected(){
        let mut state=storage();for y in 0..3{put(&state,y);}
        let old=state.updating.snapshot();let work=Arc::new(Queues::default());let (engine,_)=state.attach(Arc::clone(&work),1).unwrap();
        let frame=state.begin(Op::SeedSky(0,3,0,vec![16;1280].into_boxed_slice()),engine).unwrap();
        assert!(matches!(state.step(frame,0,None).unwrap(),Effect::Done(1)));assert_eq!(state.sky_seeds,1);
        for y in 1..3{
            let key=propagation::section_pos(0,y,0);let current=state.updating.raw(key).unwrap();let previous=old.raw(key).unwrap();
            assert!(!Arc::ptr_eq(&current,&previous));assert_eq!(current.layer.as_ref().unwrap().view().get(0).unwrap(),15);
            assert_eq!(previous.layer.as_ref().unwrap().view().get(0).unwrap(),0);assert!(state.contains(CHANGED,key));
        }
        let key=propagation::section_pos(0,0,0);assert!(Arc::ptr_eq(&state.updating.raw(key).unwrap(),&old.raw(key).unwrap()));assert!(!state.contains(CHANGED,key));
        let queues=work.data.lock().unwrap();assert_eq!(queues[1].len(),512);assert_eq!(work.counts[1].load(Ordering::Acquire),512);
        for r in 0..16{for x in 0..16{let at=(r*16+x)*2;assert_eq!(queues[1][at],propagation::block_pos(x as i32,16,r as i32));assert_eq!(queues[1][at+1],31);}}
        drop(queues);state.end(frame).unwrap();
    }
    #[test]
    fn custom_or_escaped_entries_decline_before_cache_cow_or_queue_mutation(){
        let mut state=storage();put(&state,1);let work=Arc::new(Queues::default());let (engine,_)=state.attach(Arc::clone(&work),1).unwrap();
        let custom=Arc::new(Entry{id:777,identity:777,layer:None,_pin:None});let key=propagation::section_pos(0,0,0);state.updating.set(key,Some(Arc::clone(&custom)));
        assert!(!state.seed_sky(engine,0,2,0,&[0;1280]).unwrap());assert_eq!(state.tables[CHANGED].len(),0);assert!(work.data.lock().unwrap()[1].is_empty());
        assert!(Arc::ptr_eq(&state.updating.raw(key).unwrap(),&custom));
        let escaped=state.new_layer(Layer::new(0)).unwrap();escaped.layer.as_ref().unwrap().invalidate();state.updating.set(key,Some(Arc::clone(&escaped)));
        assert!(!state.seed_sky(engine,0,2,0,&[0;1280]).unwrap());assert_eq!(state.tables[CHANGED].len(),0);assert_eq!(state.sky_seeds,0);
    }
    #[test]
    fn native_seed_input_and_storage_are_owned_by_the_frame_until_completion(){unsafe{
        let map=storage().updating;let key=propagation::section_pos(0,0,0);map.set(key,Some(map.native_entry(Arc::new(Layer::new(0))).unwrap()));
        let owner=ffi::mattmc_light_coordinator_create(Arc::as_ptr(&map),1);let work=Arc::new(Queues::default());
        let binding=ffi::mattmc_light_coordinator_bind(owner,Arc::as_ptr(&work),1);
        let mut columns=vec![0i32;1280];let frame=ffi::mattmc_light_coordinator_seed_begin(owner,binding,0,1i64<<32,columns.as_ptr());assert_ne!(frame,0);
        columns.fill(i32::MAX);drop(columns);ffi::mattmc_light_coordinator_bind_release(binding);ffi::mattmc_light_coordinator_release(owner);
        let mut out=[0i64;6];assert_eq!(ffi::mattmc_light_coordinator_step(owner,frame,0,std::ptr::null(),out.as_mut_ptr()),0);assert_eq!(out[0],1);
        assert_eq!(map.raw(key).unwrap().layer.as_ref().unwrap().view().get(0).unwrap(),15);assert_eq!(work.counts[1].load(Ordering::Acquire),512);
        assert_eq!(ffi::mattmc_light_coordinator_end(owner,frame),0);
    }}
    #[test]
    fn conservative_limits_and_custom_engine_decline_without_partial_mutation(){
        let mut state=storage();put(&state,0);let work=Arc::new(Queues::default());let (stock,_)=state.attach(Arc::clone(&work),1).unwrap();let (custom,_)=state.attach(Arc::clone(&work),2).unwrap();
        for (engine,top,bottom) in [(custom,1,0),(stock,i32::MIN,0),(stock,5000,0),(stock,0,1)]{
            assert!(!state.seed_sky(engine,0,top,bottom,&[0;1280]).unwrap());assert_eq!(state.tables[CHANGED].len(),0);assert!(work.data.lock().unwrap()[1].is_empty());
        }
    }
}


mod enable_sources {
    use super::super::*;
    use crate::world::level::lighting::maps::ffi as maps;
    unsafe fn state() -> State {
        let registry=maps::mattmc_light_map_registry_create();
        let map=Arc::from_raw(maps::mattmc_light_map_create(registry));
        maps::mattmc_light_map_registry_release(registry);
        State::new(true,map).unwrap()
    }
    #[test]
    fn enable_reads_live_bounds_after_highest_and_fills_only_actual_zero_layer() {
        unsafe {
            let mut state=state();let (engine,_)=state.attach(Arc::new(Queues::default()),1).unwrap();
            let key=propagation::section_pos(0,2,0);let column=propagation::section_pos(0,0,0);
            let original=Arc::new(Layer::new(0));let old=state.updating.native_entry(Arc::clone(&original)).unwrap();
            state.updating.set(key,Some(Arc::clone(&old)));state.updating.set_lowest(9);state.updating.set_top(column,Some(9));
            let frame=state.begin(Op::Enable(0,true),engine).unwrap();
            assert!(matches!(state.step(frame,0,None).unwrap(),Effect::HighestSource(0,0)));assert!(state.enabled(column));
            // The original highest-source callback can change these actual bounds.
            state.updating.set_lowest(2);state.updating.set_top(column,Some(3));
            assert!(matches!(state.step(frame,0,None).unwrap(),Effect::Done(_)));
            let current=state.updating.raw(key).unwrap();assert!(!Arc::ptr_eq(&current,&old));
            assert_eq!(current.layer.as_ref().unwrap().view().get(0).unwrap(),15);assert_eq!(original.view().get(0).unwrap(),0);
            state.end(frame).unwrap();
            let frame=state.begin(Op::Enable(0,false),engine).unwrap();assert!(matches!(state.step(frame,0,None).unwrap(),Effect::Done(_)));assert!(!state.enabled(column));state.end(frame).unwrap();
        }
    }
    #[test]
    fn allocated_zero_layer_is_not_definitely_empty_and_must_not_be_filled() {
        unsafe {
            let mut state=state();let (engine,_)=state.attach(Arc::new(Queues::default()),1).unwrap();
            let layer=Arc::new(Layer::new(0));layer.set(0,0).unwrap();assert!(layer.view().allocated());
            let key=propagation::section_pos(0,2,0);let column=propagation::section_pos(0,0,0);
            state.updating.set(key,Some(state.updating.native_entry(Arc::clone(&layer)).unwrap()));state.add(CHANGED,key).unwrap();state.updating.set_lowest(2);state.updating.set_top(column,Some(3));
            let frame=state.begin(Op::Enable(0,true),engine).unwrap();assert!(matches!(state.step(frame,0,None).unwrap(),Effect::HighestSource(0,0)));
            assert!(matches!(state.step(frame,0,None).unwrap(),Effect::Done(_)));assert_eq!(layer.view().get(0).unwrap(),0);assert!(layer.view().allocated());state.end(frame).unwrap();
        }
    }
    #[test]
    fn opaque_empty_callback_replacement_keeps_selected_target_through_fill() {
        unsafe {
            let registry=maps::mattmc_light_map_registry_create();
            let map=Arc::from_raw(maps::mattmc_light_map_create(registry));
            let mut state=State::new(true,map).unwrap();let (engine,_)=state.attach(Arc::new(Queues::default()),1).unwrap();let mut slot=0;
            let reservation=maps::mattmc_light_map_reserve(registry,&mut slot);let identity=maps::mattmc_light_map_identity_create(registry);
            let target=state.updating.entry(*Box::from_raw(reservation),None,identity).unwrap();maps::mattmc_light_map_registry_release(registry);let weak=Arc::downgrade(&target);
            let key=propagation::section_pos(0,2,0);let column=propagation::section_pos(0,0,0);
            state.updating.set(key,Some(target));state.add(CHANGED,key).unwrap();state.updating.set_lowest(2);state.updating.set_top(column,Some(3));
            let frame=state.begin(Op::Enable(0,true),engine).unwrap();assert!(matches!(state.step(frame,0,None).unwrap(),Effect::HighestSource(0,0)));
            assert!(matches!(state.step(frame,0,None).unwrap(),Effect::LayerEmpty(id) if id==slot));
            let replacement=state.updating.native_entry(Arc::new(Layer::new(7))).unwrap();state.updating.set(key,Some(Arc::clone(&replacement)));state.updating.clear_cache(false);
            assert!(weak.upgrade().is_some());assert!(matches!(state.step(frame,1,None).unwrap(),Effect::LayerFill(id,15) if id==slot));assert!(weak.upgrade().is_some(),"original fill target is retained across the second VM effect");
            assert!(matches!(state.step(frame,0,None).unwrap(),Effect::Done(_)));assert!(weak.upgrade().is_none());assert!(Arc::ptr_eq(&state.updating.raw(key).unwrap(),&replacement));state.end(frame).unwrap();
        }
    }
    #[test]
    fn source_effect_follows_actual_enabled_commit_and_abandon_preserves_prefix() {
        unsafe {
            for kind in [0,1] {
                let mut state=state();let (engine,_)=state.attach(Arc::new(Queues::default()),kind).unwrap();let chunk=(3i64<<32)|2;
                let frame=state.begin(Op::Sources(chunk),engine).unwrap();let effect=state.step(frame,0,None).unwrap();
                assert!(matches!((kind,effect),(0,Effect::BlockSources(2,3))|(1,Effect::SkySources(2,3))));assert!(state.enabled(propagation::section_pos(2,0,3)));
                state.end(frame).unwrap();assert!(state.enabled(propagation::section_pos(2,0,3)));assert_eq!(state.engines.get(&engine).unwrap().frames,0);
            }
        }
    }
}

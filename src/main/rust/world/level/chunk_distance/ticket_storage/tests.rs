use std::sync::atomic::Ordering;

use super::map::ListMap;
use super::*;

fn ticket(type_id: u32, level: i32, flags: u32, timeout: i64) -> Entry {
    Entry { slot: 0, type_id, level, flags, timeout }
}

fn ticks(storage: &TicketStorage, key: i64) -> Vec<i64> {
    storage
        .entries(storage.tickets_at(key))
        .iter()
        .map(|e| slab::cell(e.slot).ticks_left.load(Ordering::Relaxed))
        .collect()
}

/// Colliding keys around the table end so removals shift entries across the
/// wrap point and into the iterator's wrapped list.
fn colliding_keys(n: usize) -> Vec<i64> {
    let mut keys = vec![0];
    let mut candidate = 1i64;
    while keys.len() < n {
        let h = (candidate as u64).wrapping_mul(0x9e37_79b9_7f4a_7c15);
        let h = h ^ (h >> 32);
        let slot = (h ^ (h >> 16)) as u32 as usize & 7;
        if slot >= 6 {
            keys.push(candidate);
        }
        candidate += 1;
    }
    keys
}

#[test]
fn iterator_removal_visits_every_key_once_across_wrap_and_zero() {
    for remove_every in 1..4 {
        let mut map = ListMap::with_expected(4).unwrap();
        let keys = colliding_keys(6);
        for (i, &key) in keys.iter().enumerate() {
            map.compute_if_absent(key, || Ok(i as u32)).unwrap();
        }
        let mut cursor = map.cursor();
        let mut seen = Vec::new();
        let mut step = 0;
        while cursor.has_next() {
            let index = cursor.next_entry(&map).unwrap();
            let value = map.value_at(index).unwrap();
            seen.push(keys[value as usize]);
            if step % remove_every == 0 {
                assert_eq!(cursor.remove(&mut map).unwrap(), value);
            }
            step += 1;
        }
        seen.sort();
        let mut expected = keys.clone();
        expected.sort();
        assert_eq!(seen, expected);
        let remaining: Vec<i64> = map.ordered().into_iter().map(|(k, _)| k).collect();
        assert_eq!(remaining.len(), keys.len() - keys.len().div_ceil(remove_every));
    }
}

#[test]
fn growth_and_shrink_keep_every_value() {
    let mut map = ListMap::with_expected(4).unwrap();
    for key in 1..200i64 {
        map.compute_if_absent(key * 7919, || Ok(key as u32)).unwrap();
    }
    for key in 1..190i64 {
        assert_eq!(map.remove(key * 7919).unwrap(), key as u32);
    }
    let mut left: Vec<u32> = map.ordered().into_iter().map(|(_, v)| v).collect();
    left.sort();
    assert_eq!(left, (190..200).collect::<Vec<_>>());
    assert_eq!(map.get(199 * 7919), 199);
    assert_eq!(map.get(5), map::NONE);
}

#[test]
fn add_dedups_by_type_identity_and_level_and_resets_the_stored_ticket() {
    let mut storage = TicketStorage::new(34).unwrap();
    let (added, slot, wanted, _) = storage.add(5, ticket(1, 31, LOADING | SIMULATION, 300), false, 7).unwrap();
    assert!(added && slot.is_some());
    assert_eq!(wanted, [true, true]);
    assert_eq!(ticks(&storage, 5), vec![7]);
    let (added, slot, _, _) = storage.add(5, ticket(1, 31, LOADING, 300), false, 1).unwrap();
    assert!(!added && slot.is_none());
    assert_eq!(ticks(&storage, 5), vec![300]);
    // Same level, other type: added; it does not lower the level.
    let (added, _, wanted, _) = storage.add(5, ticket(2, 31, LOADING, 0), false, 0).unwrap();
    assert!(added);
    assert_eq!(wanted, [false, false]);
    assert_eq!(storage.level_at(5, false), 31);
    assert_eq!(storage.level_at(5, true), 31);
    assert_eq!(storage.level_at(6, true), 34);
}

#[test]
fn shared_ticket_identity_shares_one_counter_until_unreferenced() {
    let mut storage = TicketStorage::new(34).unwrap();
    let (_, slot, _, _) = storage.add(1, ticket(3, 32, LOADING, 40), false, 40).unwrap();
    let slot = slot.unwrap();
    storage.add(2, Entry { slot, ..ticket(3, 32, LOADING, 40) }, true, 0).unwrap();
    let mut walk = storage.begin(WalkKind::Purge);
    // Timed tickets that need readiness ask Java once per entry.
    let mut asked = 0;
    let mut answer = false;
    loop {
        match storage.step(&mut walk, i32::from(answer), 64).unwrap() {
            Event::Ready { .. } => {
                asked += 1;
                answer = true;
            }
            Event::Done { forced } => {
                assert!(!forced);
                break;
            }
            other => panic!("{other:?}"),
        }
    }
    assert_eq!(asked, 2);
    assert_eq!(slab::cell(slot).ticks_left.load(Ordering::Relaxed), 38);
    storage.remove(1, 3, 32, LOADING).unwrap().unwrap();
    assert_eq!(ticks(&storage, 2), vec![38]);
}

#[test]
fn purge_expires_after_timeout_and_reports_removed_list_levels() {
    let mut storage = TicketStorage::new(34).unwrap();
    storage.add(9, ticket(4, 33, LOADING | CAN_EXPIRE_IF_UNLOADED | FORCED, 1), false, 1).unwrap();
    storage.add(9, ticket(5, 31, LOADING, 0), false, 0).unwrap();
    for round in 0..2 {
        let mut walk = storage.begin(WalkKind::Purge);
        let event = storage.step(&mut walk, 0, 64).unwrap();
        // Without listeners nothing is asked; the first purge leaves 0 ticks.
        assert_eq!(event, Event::Done { forced: round == 1 });
        assert_eq!(TicketStorage::walk_dirty(&walk), round == 1);
    }
    assert_eq!(storage.entries(storage.tickets_at(9)).len(), 1);
    assert!(storage.forced_chunks().is_empty());
}

#[test]
fn java_listener_stops_native_delivery_in_order() {
    let mut storage = TicketStorage::new(34).unwrap();
    storage.set_listener(SIMULATION_LISTENER, true, None);
    let (_, _, wanted, delivered) = storage.add(3, ticket(6, 31, LOADING | SIMULATION, 0), false, 0).unwrap();
    assert_eq!(wanted, [true, true]);
    assert_eq!(delivered.resume, 0);
    let (list, delivered) = storage.remove(3, 6, 31, LOADING | SIMULATION).unwrap().unwrap();
    assert_eq!(delivered.resume, 0);
    // The emptied list left the map; its level is read as absent.
    assert_eq!(storage.list_level(list, true), 34);
    assert!(!storage.has_tickets());
}

#[test]
fn deactivate_keeps_unknown_and_reactivation_sees_order() {
    let mut storage = TicketStorage::new(34).unwrap();
    storage.add(1, ticket(7, 33, LOADING | PERSIST, 0), false, 0).unwrap();
    storage.add(1, ticket(8, 32, LOADING | UNKNOWN | CAN_EXPIRE_IF_UNLOADED, 1), false, 1).unwrap();
    storage.add(0, ticket(7, 31, LOADING | PERSIST, 0), false, 0).unwrap();
    let mut walk = storage.begin(WalkKind::Deactivate);
    assert_eq!(storage.step(&mut walk, 0, 64).unwrap(), Event::Done { forced: false });
    assert_eq!(storage.active_keys(), vec![1]);
    let deactivated: Vec<i64> = storage.packed(true).into_iter().map(|(k, _)| k).collect();
    assert_eq!(deactivated, vec![0, 1]);
    assert_eq!(storage.packed(false).len(), 2);
    storage.clear_deactivated();
    assert!(storage.packed(true).is_empty());
}

#[test]
fn walk_tolerates_callback_mutation_between_steps() {
    let mut storage = TicketStorage::new(34).unwrap();
    for key in 1..20i64 {
        storage.add(key, ticket(9, 32, LOADING, 5), false, 5).unwrap();
    }
    let mut walk = storage.begin(WalkKind::Java);
    let mut steps = 0;
    let mut answer = 0;
    loop {
        match storage.step(&mut walk, answer, 3) {
            Ok(Event::Batch { count }) => {
                assert!((1..=3).contains(&count));
                // The first predicate adds and removes elsewhere, as Java code could.
                let key = walk.batched().next().unwrap().0;
                storage.add(key + 1000, ticket(9, 32, LOADING, 5), false, 5).unwrap();
                storage.remove(key + 1, 9, 32, LOADING).unwrap();
                answer = i32::from(key % 2 == 0);
                steps += 1;
            }
            Ok(Event::Done { .. }) => break,
            Ok(other) => panic!("{other:?}"),
            // A positional read beyond a shrunk table is Java's exception too.
            Err(fault) => {
                assert!(matches!(fault, Fault::ArrayIndex | Fault::NullPointer | Fault::IndexOutOfBounds));
                break;
            }
        }
        assert!(steps < 10_000);
    }
}

#[test]
fn batched_predicates_match_one_at_a_time() {
    let fill = |storage: &mut TicketStorage| {
        for key in 0..40i64 {
            for level in 31..31 + (key % 3) as i32 {
                storage.add(key * 7, ticket(9, level, LOADING, 1), false, key).unwrap();
            }
        }
    };
    let run = |capacity: usize| {
        let mut storage = TicketStorage::new(34).unwrap();
        fill(&mut storage);
        let mut walk = storage.begin(WalkKind::Java);
        let mut seen = Vec::new();
        let mut answer = 0;
        loop {
            match storage.step(&mut walk, answer, capacity).unwrap() {
                Event::Batch { count } => {
                    answer = -1;
                    let batch: Vec<(i64, u32)> = walk.batched().collect();
                    assert_eq!(batch.len(), count);
                    for (i, (key, slot)) in batch.into_iter().enumerate() {
                        let left = slab::cell(slot).ticks_left.load(Ordering::Relaxed);
                        seen.push((key, left));
                        // Remove every third ticket by key and counter.
                        if (key + left) % 3 == 0 {
                            answer = (i as i32) << 1 | 1;
                            break;
                        }
                    }
                }
                Event::Done { .. } => break,
                other => panic!("{other:?}"),
            }
        }
        (seen, storage.active_keys(), storage.packed(false).len())
    };
    assert_eq!(run(1), run(64));
    assert_eq!(run(1), run(5));
}

#[test]
fn exhausted_cursor_without_wrapped_keys_is_java_npe() {
    // A callback that clears the table mid-iteration leaves the count ahead of
    // the keys; fastutil then reads its never-created wrapped list.
    let mut map = ListMap::with_expected(4).unwrap();
    for key in 1..4i64 {
        map.compute_if_absent(key, || Ok(key as u32)).unwrap();
    }
    let mut cursor = map.cursor();
    cursor.next_entry(&map).unwrap();
    map.clear();
    assert_eq!(cursor.next_entry(&map), Err(Fault::NullPointer));
}

#[test]
fn cells_are_reused_only_after_their_ticket_frees_them() {
    let mut out = [0i64; 4096];
    let slot = slab::bind(7).unwrap();
    // Released on another thread and drained (here, or by a parallel test):
    // the cell keeps its counter and is not handed out again.
    std::thread::spawn(move || slab::release(slot)).join().unwrap();
    slab::drain(&mut out);
    let other = slab::bind(1).unwrap();
    assert_ne!(other, slot);
    assert_eq!(slab::cell(slot).ticks_left.load(Ordering::Relaxed), 7);
    // Queued, then stored again before a drain: not reported, and a cleaner
    // racing it keeps the referenced cell.
    slab::retain(slot);
    slab::release(slot);
    slab::retain(slot);
    let reported = slab::drain(&mut out);
    assert!(!out[..reported.max(0) as usize].contains(&(slot as i64)));
    assert!(!slab::free(slot));
    // Unreferenced and queued: the cleaner frees it and dequeues it.
    slab::release(slot);
    assert!(slab::free(slot));
    let reported = slab::drain(&mut out);
    assert!(!out[..reported.max(0) as usize].contains(&(slot as i64)));
    slab::release(other);
    assert!(slab::free(other));
}

#[test]
fn drain_output_is_bounded_by_the_queue() {
    // Concurrent releases may lower refs between any two reads; the emitted
    // count can never exceed the queue length, which the caller's buffer holds.
    let slots: Vec<u32> = (0..6).map(|i| slab::bind(i).unwrap()).collect();
    for &slot in &slots {
        slab::release(slot);
    }
    // Revive one before the drain and release it again after the drain read it.
    slab::retain(slots[0]);
    let mut small = [0i64; 1];
    let needed = slab::drain(&mut small);
    let mut out = vec![0i64; 4096];
    let written = if needed < 0 { slab::drain(&mut out) } else { needed };
    assert!(written as usize <= out.len());
    assert!(!out[..written.max(0) as usize].contains(&(slots[0] as i64)));
    slab::release(slots[0]);
    let again = slab::drain(&mut out);
    assert!(again >= 0);
    for &slot in &slots {
        assert!(slab::free(slot));
    }
}

#[test]
fn prepared_admission_publishes_only_after_resource_adoption() {
    let mut storage = TicketStorage::new(34).unwrap();
    let entry = ticket(7, 31, LOADING | SIMULATION, 300);
    assert!(storage.prepare_add(5, entry).unwrap());
    assert!(storage.entries(storage.tickets_at(5)).is_empty());
    assert_eq!(storage.level_at(5, false), 34);
    // Abandoned Java adoption: no entry or level change, retry remains possible.
    assert!(storage.prepare_add(5, entry).unwrap());
    let slot = slab::reserve(9).unwrap();
    assert_eq!(slab::references(slot), 0);
    let (added, _, wanted, _) = storage.add(5, Entry { slot, ..entry }, true, 0).unwrap();
    assert!(added);
    assert_eq!(wanted, [true, true]);
    assert_eq!(slab::references(slot), 1);
    assert_eq!(ticks(&storage, 5), vec![9]);
    assert!(!storage.prepare_add(5, entry).unwrap());
    assert_eq!(ticks(&storage, 5), vec![300]);
    storage.remove(5, entry.type_id, entry.level, entry.flags).unwrap();
    assert_eq!(slab::references(slot), 0);
    assert!(slab::free(slot));
}

#[test]
fn abandoned_unpublished_cell_reservation_is_reusable() {
    let slot = slab::reserve(-7).unwrap();
    assert_eq!(slab::references(slot), 0);
    assert!(slab::free(slot));
    let reused = slab::reserve(19).unwrap();
    assert_eq!(reused, slot);
    assert_eq!(slab::cell(reused).ticks_left.load(Ordering::Relaxed), 19);
    assert!(slab::free(reused));
}

#[test]
fn list_and_decoded_preparation_validate_before_publication() {
    let mut storage = TicketStorage::new(34).unwrap();
    let entry = ticket(7, 31, PERSIST | LOADING, 300);
    storage.add(5, entry, false, 9).unwrap();
    let list = storage.tickets_at(5);
    assert!(matches!(storage.prepare_list_put(list, 1, true), Err(Fault::IndexOutOfBounds)));
    assert!(matches!(storage.prepare_list_put(list, 2, false), Err(Fault::IndexOutOfBounds)));
    assert_eq!(ticks(&storage, 5), vec![9]);
    storage.prepare_list_put(list, 1, false).unwrap();
    assert_eq!(ticks(&storage, 5), vec![9]);
    storage.prepare_deactivated(9).unwrap();
    assert!(storage.packed(true).is_empty());
    let slot = slab::reserve(17).unwrap();
    storage.add_deactivated(9, Entry { slot, ..entry }, true, 0).unwrap();
    assert_eq!(storage.packed(true), vec![(9, slot)]);
    storage.clear_deactivated();
    assert_eq!(slab::references(slot), 0);
    assert!(slab::free(slot));
}

#[test]
fn virtual_minimum_commands_preserve_comparison_then_predicate_order() {
    use super::ffi::{mattmc_ticket_query_begin as begin, mattmc_ticket_query_next as next};
    let mut word = begin(1, 1);
    let scripted = [(1, 0), (2, 1), (3, 0), (6, 1), (8, 0),
        (2, 1), (3, 0), (4, -7), (5, 33), (6, 1), (8, 0), (2, 0)];
    for (command, answer) in scripted { assert_eq!(word & 255, command); word = next(word, answer); }
    assert_eq!(word & 255, 10);
    let unrelated = begin(0, 1);
    assert_eq!(next(next(next(unrelated, 0), 1), 0) & 255, 7);
    assert_eq!(begin(1, 0) & 255, 10);
}

#[test]
fn retained_physical_list_outlives_map_without_retaining_unrelated_entry_owners(){
    let shared=slab::reserve_resource(7).unwrap();let other=slab::reserve_resource(9).unwrap();
    let mut source=TicketStorage::new(45).unwrap();source.roots.enable(9001).unwrap();
    let first=source.get_or_create(7).unwrap();let second=source.get_or_create(19).unwrap();
    source.list_insert(first,0,Entry{slot:shared,..ticket(1,33,LOADING,0)},true,0).unwrap();
    source.list_insert(second,0,Entry{slot:shared,..ticket(1,33,LOADING,0)},true,0).unwrap();
    source.list_insert(second,1,Entry{slot:other,..ticket(2,31,LOADING,0)},true,0).unwrap();
    let retained=source.list_owner(first).unwrap().clone();
    drop(source);
    assert_eq!(slab::references(shared),1);assert_eq!(slab::references(other),0);
    assert_eq!(retained.lock().entries[0].slot,shared);
    retained.lock().publish(1,false,Entry{slot:other,..ticket(2,31,LOADING,0)}).unwrap();
    assert_eq!(slab::references(other),1);
    drop(retained);
    assert_eq!(slab::references(shared),0);assert_eq!(slab::references(other),0);
    slab::release_resource(shared);slab::release_resource(other);
    let mut retired=vec![0;100_000];let _=slab::drain(&mut retired);
}

#[test]
fn removed_list_owner_keeps_its_identity_when_a_map_slot_is_reused(){
    let mut source=TicketStorage::new(45).unwrap();
    let old=source.get_or_create(7).unwrap();let retained=source.list_owner(old).unwrap().clone();
    let index=source.tickets.remove(7).unwrap();source.retire(index);
    let replacement=source.get_or_create(7).unwrap();assert_ne!(old,replacement);
    let old_slot=slab::reserve_resource(7).unwrap();let new_slot=slab::reserve_resource(9).unwrap();
    retained.lock().publish(0,false,Entry{slot:old_slot,..ticket(1,33,LOADING,0)}).unwrap();
    source.list_insert(replacement,0,Entry{slot:new_slot,..ticket(2,31,LOADING,0)},true,0).unwrap();
    assert_eq!(retained.lock().entries[0].slot,old_slot);assert_eq!(source.entries(replacement)[0].slot,new_slot);
    drop(source);assert_eq!(slab::references(old_slot),1);assert_eq!(slab::references(new_slot),0);
    drop(retained);slab::release_resource(old_slot);slab::release_resource(new_slot);
    let mut retired=vec![0;100_000];let _=slab::drain(&mut retired);
}

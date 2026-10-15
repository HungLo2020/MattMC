use super::*;

const MAX: i32 = 44;

#[test]
fn ordered_keeps_insertion_order_through_replace_remove_and_clone() {
    let mut map = Ordered::new();
    for key in [5i64, 1, 9, 3] {
        map.put(key, key as u32).unwrap();
    }
    map.put(1, 100).unwrap();
    map.remove(9);
    map.put(9, 9).unwrap();
    let snapshot = map.clone();
    map.remove(5);
    assert_eq!(snapshot.entries(), vec![(5, 5), (1, 100), (3, 3), (9, 9)]);
    assert_eq!(map.entries(), vec![(1, 100), (3, 3), (9, 9)]);
}

#[test]
fn scheduling_creates_drops_revives_and_ignores_unloaded() {
    let mut registry = Registry::new(MAX).unwrap();
    assert_eq!(registry.schedule(7, MAX + 1, false, MAX + 1).unwrap(), Scheduled::Unchanged);
    let Scheduled::New(id) = registry.schedule(7, 33, false, MAX + 1).unwrap() else { panic!() };
    registry.commit_new(7, id).unwrap();
    assert_eq!(registry.updating(7), Some(id));
    // Leaving the loaded range marks it to drop; returning unmarks it.
    assert_eq!(registry.schedule(7, MAX + 1, true, 33).unwrap(), Scheduled::Level);
    assert!(registry.is_dropping(7));
    assert_eq!(registry.schedule(7, 33, true, MAX + 1).unwrap(), Scheduled::Level);
    assert!(!registry.is_dropping(7));
    registry.schedule(7, MAX + 1, true, 33).unwrap();
    assert_eq!(registry.drop_pass().unwrap(), vec![(7, id)]);
    assert_eq!(registry.updating(7), None);
    // Loaded again before the unload finished: the same holder comes back.
    assert_eq!(registry.schedule(7, 30, false, MAX + 1).unwrap(), Scheduled::Revived(id));
    assert_eq!(registry.updating(7), Some(id));
    assert!(!registry.finish_unload(7, id));
}

#[test]
fn unload_completion_frees_the_id_and_promote_publishes_order() {
    let mut registry = Registry::new(MAX).unwrap();
    for key in [3i64, 1, 2] {
        let Scheduled::New(id) = registry.schedule(key, 33, false, MAX + 1).unwrap() else { panic!() };
        registry.commit_new(key, id).unwrap();
    }
    let visible = registry.promote().unwrap();
    assert_eq!(visible.iter().map(|e| e.0).collect::<Vec<_>>(), vec![3, 1, 2]);
    assert!(registry.promote().is_none());
    registry.schedule(1, MAX + 1, true, 33).unwrap();
    let dropped = registry.drop_pass().unwrap();
    assert_eq!(dropped.len(), 1);
    // The promoted view still holds the dropped holder until the next promote.
    assert_eq!(registry.visible().len(), 3);
    assert!(registry.finish_unload(1, dropped[0].1));
    assert_eq!(registry.work() & 1, 0);
    let Scheduled::New(reused) = registry.schedule(8, 33, false, MAX + 1).unwrap() else { panic!() };
    assert_eq!(reused, dropped[0].1);
}

#[test]
fn eager_walk_fixes_next_like_the_linked_set_iterator() {
    let mut registry = Registry::new(MAX).unwrap();
    for key in [1i64, 2, 3] {
        registry.mark_unsaved(key).unwrap();
    }
    registry.eager_start();
    assert_eq!(registry.eager_next(), Some(1));
    registry.eager_remove_current().unwrap();
    assert_eq!(registry.eager_next(), Some(2));
    assert_eq!(registry.eager_next(), Some(3));
    // Appended after the walk passed the old tail: not visited by this walk.
    registry.mark_unsaved(4).unwrap();
    assert_eq!(registry.eager_next(), None);
    registry.eager_start();
    let mut seen = Vec::new();
    while let Some(key) = registry.eager_next() {
        seen.push(key);
    }
    assert_eq!(seen, vec![2, 3, 4]);
    assert!(registry.eager_remove_current().is_ok());
    assert!(registry.eager_remove_current().is_err());
}

#[test]
fn drop_pass_order_follows_the_hash_set() {
    // Many keys so the open-addressing order differs from insertion order.
    let mut registry = Registry::new(MAX).unwrap();
    let mut ids = Vec::new();
    for key in 0..200i64 {
        let key = key * 7919 - 400;
        let Scheduled::New(id) = registry.schedule(key, 33, false, MAX + 1).unwrap() else { panic!() };
        registry.commit_new(key, id).unwrap();
        ids.push(key);
    }
    let mut expected = ListMap::with_expected(16).unwrap();
    for &key in ids.iter().rev() {
        registry.schedule(key, MAX + 1, true, 33).unwrap();
        expected.compute_if_absent(key, || Ok(0)).unwrap();
    }
    let order: Vec<i64> = registry.drop_pass().unwrap().into_iter().map(|e| e.0).collect();
    let mut cursor = expected.cursor();
    let mut want = Vec::new();
    while cursor.has_next() {
        let index = cursor.next_entry(&expected).unwrap();
        want.push(expected.key_at(index).unwrap());
        cursor.remove(&mut expected).unwrap();
    }
    assert_eq!(order, want);
    assert_eq!(registry.work() & 4, 0);
}

#[test]
fn transitions_follow_update_futures() {
    let levels = [33, 32, 31];
    // Unloaded to entity ticking: all three prepared, no demotion.
    assert_eq!(transition(levels, MAX + 1, 31), PREPARE_FULL | PREPARE_TICKING | PREPARE_ENTITY);
    // Entity ticking to full: two unloaded, demoted to FULL (ordinal 1).
    assert_eq!(transition(levels, 31, 33), UNLOAD_TICKING | UNLOAD_ENTITY | 2 << DEMOTE_SHIFT);
    // Full to inaccessible: demoted to INACCESSIBLE (ordinal 0).
    assert_eq!(transition(levels, 33, 34), UNLOAD_FULL | 1 << DEMOTE_SHIFT);
    // Within one status: nothing.
    assert_eq!(transition(levels, 36, 40), 0);
    assert_eq!(transition(levels, 30, 31), 0);
}

#[test]
fn holder_levels_follow_scheduling_and_transitions() {
    let mut registry = Registry::new(MAX).unwrap();
    let Scheduled::New(id) = registry.schedule(7, 35, false, MAX + 1).unwrap() else { panic!() };
    registry.commit_new(7, id).unwrap();
    assert_eq!(registry.levels(id).unwrap(), Levels { level: 35, old_level: MAX + 1, accessible: false });
    assert_eq!(registry.begin_transition(id).unwrap(), 0);
    registry.commit_transition(id).unwrap();
    registry.schedule(7, 32, true, 35).unwrap();
    // Accessible is marked before the effects; the old level only at commit.
    assert_eq!(registry.begin_transition(id).unwrap(), PREPARE_FULL | PREPARE_TICKING);
    assert_eq!(registry.levels(id).unwrap(), Levels { level: 32, old_level: 35, accessible: true });
    // An effect threw: the same transition is decided again.
    assert_eq!(registry.begin_transition(id).unwrap(), PREPARE_FULL | PREPARE_TICKING);
    registry.commit_transition(id).unwrap();
    assert_eq!(registry.begin_transition(id).unwrap(), 0);
    registry.schedule(7, 36, true, 32).unwrap();
    registry.refresh_accessibility(id).unwrap();
    assert!(!registry.levels(id).unwrap().accessible);
    // A revived holder keeps its state and takes the new level.
    registry.schedule(7, MAX + 1, true, 36).unwrap();
    registry.drop_pass().unwrap();
    assert_eq!(registry.schedule(7, 33, false, MAX + 1).unwrap(), Scheduled::Revived(id));
    assert_eq!(registry.levels(id).unwrap(), Levels { level: 33, old_level: 32, accessible: false });
}

#[test]
fn level_changes_apply_in_order_and_stop_at_construction() {
    let mut registry = Registry::new(MAX).unwrap();
    let Scheduled::New(a) = registry.schedule(1, 33, false, MAX + 1).unwrap() else { panic!() };
    registry.commit_new(1, a).unwrap();
    // Unchanged level, unloaded without holder, level change, new holder, then more.
    registry.take_level_changes(&[(1, 33), (2, MAX + 1), (1, 34), (3, 30), (1, 32)]).unwrap();
    let mut out = Vec::new();
    registry.apply_level_changes(16, &mut out).unwrap();
    let Applied(3, b, Apply::New(30)) = out[1] else { panic!("{out:?}") };
    assert_eq!(out, vec![Applied(1, a, Apply::Level(34)), Applied(3, b, Apply::New(30))]);
    registry.commit_new(3, b).unwrap();
    out.clear();
    registry.apply_level_changes(16, &mut out).unwrap();
    assert_eq!(out, vec![Applied(1, a, Apply::Level(32))]);
    out.clear();
    registry.apply_level_changes(16, &mut out).unwrap();
    assert!(out.is_empty());
    // Construction threw: the id is released and the rest is dropped.
    registry.take_level_changes(&[(5, 30), (1, 31)]).unwrap();
    registry.apply_level_changes(16, &mut out).unwrap();
    let Applied(5, c, Apply::New(30)) = out[0] else { panic!() };
    registry.abort_new(c);
    registry.discard_level_changes();
    out.clear();
    registry.apply_level_changes(16, &mut out).unwrap();
    assert!(out.is_empty());
    assert_eq!(registry.levels(a).unwrap().level, 32);
    // A limit splits the records without losing any.
    registry.take_level_changes(&[(1, 30), (3, 31), (1, 29)]).unwrap();
    registry.apply_level_changes(2, &mut out).unwrap();
    assert_eq!(out.len(), 2);
    registry.apply_level_changes(3, &mut out).unwrap();
    assert_eq!(out[2], Applied(1, a, Apply::Level(29)));
}

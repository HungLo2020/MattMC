use super::*;

#[test]
fn nested_marks_restore_values_and_insertion_order() {
    let mut o = Lifetime::default();
    o.observe(11, 101, 1); o.observe(12, 102, 1);
    let outer = o.checkpoint(2).unwrap();
    o.observe(11, 201, 2);
    let inner = o.checkpoint(2).unwrap();
    o.forget(11); o.observe(11, 301, 2); o.observe(13, 103, 2);
    assert!(o.rollback(2, inner));
    assert_eq!(o.ordered().iter().map(|row| row.0).collect::<Vec<_>>(), [11, 12]);
    assert!(o.rollback(2, outer));
    assert!(o.retire(3));
    assert_eq!(o.retired, [Retirement { key: 11, generation: 101 }, Retirement { key: 12, generation: 102 }]);
    assert!(!o.rollback(2, outer));
}

#[test]
fn removal_snapshot_restores_unjournaled_other_frame_writes() {
    let mut o = Lifetime::default();
    o.observe(-1, i64::MIN, 1);
    o.observe(2, 3, 1);
    let mark = o.checkpoint(2).unwrap();
    o.forget(-1);
    o.observe(2, 4, 3); // Deliberately unjournaled, as in the Java contract.
    o.observe(5, 6, 3);
    assert!(o.rollback(2, mark));
    o.retire(3);
    assert_eq!(o.retired, [Retirement { key: -1, generation: i64::MIN }, Retirement { key: 2, generation: 3 }]);
}

#[test]
fn repeated_touches_and_frame_changes_bound_the_journal() {
    let mut o = Lifetime::default();
    for frame in 1..1001 {
        o.retire(frame);
        o.checkpoint(frame);
        for _ in 0..4 { for key in 1..=128 { o.observe(key, key, frame); } }
        assert_eq!(o.entries.len(), 128);
        assert_eq!(o.slots.len(), 128);
        assert_eq!(o.changes.len(), 128);
    }
    o.discard();
    assert_eq!(o.changes.len(), 0);
    assert!(!o.rollback(1000, 0));
    o.retire(1002);
    assert_eq!(o.retired.len(), 128);
    assert!(o.entries.is_empty());
}

#[test]
fn invalid_calls_do_not_mutate_state() {
    let mut o = Lifetime::default();
    assert!(!o.observe(0, 1, 1)); assert!(!o.observe(1, 0, 1)); assert!(!o.observe(1, 1, 0));
    assert!(o.checkpoint(0).is_none()); assert!(!o.retire(0));
    assert!(o.entries.is_empty());
    o.checkpoint(1);
    assert!(!o.rollback(2, 0)); assert!(!o.rollback(1, 1));
    assert!(o.retire(1));
    assert_eq!(o.checkpoint(1), Some(1)); // Even an empty retire has a snapshot.
}

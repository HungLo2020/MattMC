use super::*;

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

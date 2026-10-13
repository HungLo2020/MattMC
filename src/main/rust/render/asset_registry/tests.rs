use super::*;
fn ordered(r: &Registry) -> Vec<i64> {
    let mut out = Vec::new();
    let mut at = r.header.head;
    while at != NONE {
        let slot = r.slots[at as usize];
        out.push(slot.key);
        at = slot.next;
    }
    out
}
#[test]
fn cpu_layout_and_live_order_match_contract() {
    assert_eq!(std::mem::size_of::<Header>(), 40);
    assert_eq!(std::mem::offset_of!(Header, slots), 32);
    assert_eq!(std::mem::size_of::<Slot>(), 24);
    let mut r = Registry::default();
    let a = r.put(i64::MIN);
    r.put(0);
    r.put(i64::MAX);
    assert_eq!(r.put(i64::MIN), a);
    assert_eq!(r.header.version, 3);
    assert_eq!(r.remove(i64::MIN), Some(a));
    assert_eq!(r.put(-1), a);
    assert_eq!(ordered(&r), [0, i64::MAX, -1]);
    assert_eq!(r.header.size, 3);
    assert_eq!(r.header.slots_len, 3);
}
#[test]
fn journal_uses_first_event_and_retains_removed_additions() {
    let mut r = Registry::default();
    r.put(1);
    r.put(2);
    let mark = (r.header.epoch, r.header.events as i32);
    r.put(1);
    r.put(3);
    r.remove(2);
    r.put(2);
    r.put(4);
    r.remove(4);
    r.put(5);
    assert!(r.added_since(mark.0, mark.1));
    assert_eq!(r.added, [3, 4, 5]);
    r.clear();
    assert!(ordered(&r).is_empty());
    assert!(r.added_since(mark.0, mark.1));
    assert_eq!(r.added, [3, 4, 5]);
    r.reset();
    assert!(!r.added_since(mark.0, mark.1));
}
#[test]
fn event_bound_invalidates_old_marks_and_clear_preserves_removal_order() {
    let mut r = Registry::default();
    for _ in 0..MAX_EVENTS / 2 {
        r.put(9);
        r.remove(9);
    }
    assert_eq!(r.header.epoch, 0);
    assert_eq!(r.journal.len(), MAX_EVENTS);
    r.put(10);
    assert_eq!(r.header.epoch, 1);
    assert_eq!(r.journal, [(10, true)]);
    r.put(20);
    r.clear();
    assert_eq!(
        r.journal,
        [(10, true), (20, true), (10, false), (20, false)]
    );
    assert!(!r.added_since(1, -1));
    assert!(!r.added_since(1, 5));
}

use super::*;
#[test]
fn quiet_iteration_borrows_pages_and_mutation_preserves_old_objects() {
    let mut order = Order::default();
    let a = order.add(7).unwrap().new;
    let b = order.add(8).unwrap().new;
    let c = order.add(9).unwrap().new;
    order.begin().unwrap();
    assert_eq!(order.iteration, Some(order.active));
    assert_eq!(order.begin(), Err(OrderError::AlreadyIterating));
    assert_eq!(order.remove(8).unwrap(), Some((b, true)));
    let change = order.add(9).unwrap();
    assert_eq!(change.old, Some(c));
    assert!(change.old_pinned);
    let e = order.add(10).unwrap().new;
    assert_eq!(
        order.tables[order.iteration.unwrap()].ordered(),
        vec![(7, a), (8, b), (9, c)]
    );
    assert_eq!(
        order.tables[order.active].ordered(),
        vec![(7, a), (9, change.new), (10, e)]
    );
    assert_eq!(order.end().unwrap(), &[b, c]);
    assert_eq!(order.begin(), Err(OrderError::PendingBindingClear));
    order.acknowledge_clear();
    order.begin().unwrap();
    assert!(order.end().unwrap().is_empty());
}
#[test]
fn copied_view_rows_name_the_new_boxes_and_old_rows_remain_pinned() {
    let mut order = Order::default();
    for key in 0..130 {
        order.add(key).unwrap();
    }
    order.begin().unwrap();
    let old = order.iteration_view().unwrap();
    assert_eq!(old.page_count, 3);
    assert_eq!(old.count, 130);
    let old_rows = order.tables[order.iteration.unwrap()].rows.clone();
    order.remove(0).unwrap();
    let current = order.tables[order.active].view();
    assert_ne!(old.identity, current.identity);
    for (page, &pointer) in order.tables[order.active]
        .pages
        .iter()
        .zip(&order.tables[order.active].rows)
    {
        assert_eq!(page.as_ptr() as usize, pointer);
        assert!(!old_rows.contains(&pointer));
    }
    assert_eq!(order.tables[order.iteration.unwrap()].rows, old_rows);
    assert_eq!(order.tables[order.iteration.unwrap()].node(old.head).key, 0);
    assert_eq!(std::mem::size_of::<Node>(), 24);
    assert_eq!(std::mem::size_of::<IterationView>(), 32);
    assert_eq!(std::mem::offset_of!(Node, next), 16);
    order.end().unwrap();
    order.acknowledge_clear();
}
#[test]
fn changes_after_snapshot_do_not_pin_every_transient_object() {
    let mut order = Order::default();
    let first = order.add(0).unwrap().new;
    order.begin().unwrap();
    order.remove(0).unwrap();
    for _ in 0..10000 {
        let change = order.add(1).unwrap();
        assert!(change.old.is_none());
        assert_eq!(order.remove(1).unwrap(), Some((change.new, false)));
    }
    assert_eq!(order.bindings.generation.len(), 2);
    assert_eq!(order.end().unwrap(), &[first]);
    order.acknowledge_clear();
    assert_eq!(order.bindings.live.iter().filter(|&&v| v).count(), 0);
}
#[test]
fn key_edges_replacement_order_and_pre_getter_snapshot_are_preserved() {
    let mut order = Order::default();
    for key in [0, -1, i32::MIN, i32::MAX] {
        order.add(key).unwrap();
    }
    order.add(-1).unwrap();
    assert_eq!(
        order.tables[order.active]
            .ordered()
            .iter()
            .map(|x| x.0)
            .collect::<Vec<_>>(),
        vec![0, -1, i32::MIN, i32::MAX]
    );
    order.begin().unwrap();
    order.prepare_mutation().unwrap();
    assert_ne!(order.iteration, Some(order.active));
    order.remove(i32::MIN).unwrap();
    order.add(77).unwrap();
    assert!(!order.contains(i32::MIN));
    assert!(order.tables[order.iteration.unwrap()]
        .keys
        .contains_key(&i32::MIN));
    order.end().unwrap();
    order.acknowledge_clear();
    assert_eq!(order.end().unwrap_err(), OrderError::NotIterating);
}

#[test]
fn physical_receivers_survive_getter_copy_swap_and_reuse() {
    let mut order = Order::default();
    order.add(1).unwrap();
    order.begin().unwrap();
    let captured = order.receiver(false).unwrap();
    order.remove(1).unwrap();
    assert!(order.contains_in(captured, 1));
    assert!(!order.contains(1));
    order.end().unwrap();
    order.acknowledge_clear();
    let captured = order.receiver(true).unwrap();
    order.begin().unwrap();
    order.add(2).unwrap();
    order.end().unwrap();
    order.acknowledge_clear();
    assert_eq!(order.add_to(captured, 3).new, 0);
    assert!(!order.contains(3));
    assert!(order.contains_in(captured, 3));
    order.begin().unwrap();
    order.add(4).unwrap();
    order.end().unwrap();
    order.acknowledge_clear();
    assert_eq!(captured, order.active);
    assert!(!order.contains(3)); // Reused receiver was cleared before copying.
    assert_ne!(order.add_to(captured, 5).new, 0);
    assert!(order.contains(5));
}

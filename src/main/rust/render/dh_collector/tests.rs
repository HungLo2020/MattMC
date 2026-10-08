use super::*;

fn config() -> Config {
    Config { rust_whole_frame: true, ..Config::default() }
}

fn column(generation: i64, quads: i32) -> Column {
    Column { generation, byte_size: quads as i64 * 64, counts: Counts { opaque: 1, ..Counts::default() } }
}

fn built(generation: i64, same_payload: bool) -> Built {
    Built { column: column(generation, 1), has_segments: true, provenance_bytes: None, same_payload, same_provenance: true }
}

fn publish_all(ledger: &mut Ledger, config: Config) -> PendingUpdate {
    let update = ledger.pending_update(config, false).unwrap().unwrap();
    let assets: Vec<Acknowledged> = update
        .assets
        .iter()
        .map(|a| Acknowledged { column_key: a.column_key, generation: a.generation, counts: Counts { opaque: 1, ..Counts::default() } })
        .collect();
    let mut effects = Vec::new();
    ledger.acknowledge(config, &assets, &update.retirements, &mut effects);
    update
}

#[test]
fn identical_rebuild_reuses_the_generation_and_late_close_cannot_retire_a_newer_one() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    assert_eq!(l.record_built(c, 7, built(1, false), None, &mut e).unwrap(), 1);
    let first = l.acquire_owner(7, 1);
    assert_eq!(l.record_built(c, 7, built(2, true), None, &mut e).unwrap(), 1, "same payload keeps generation 1");
    let second = l.acquire_owner(7, 1);
    assert_eq!(first, second, "owners of one generation share a group");
    l.release_owner(c, 7, 1, first, &mut e);
    assert!(l.has_column_generation(7, 1), "another owner keeps it alive");
    // A changed payload replaces the group; the stale lease cannot remove generation 3.
    assert_eq!(l.record_built(c, 7, built(3, false), Some("opaque-vertex-count".into()), &mut e).unwrap(), 3);
    let third = l.acquire_owner(7, 3);
    assert_ne!(third, second);
    l.release_owner(c, 7, 1, second, &mut e);
    assert!(l.has_column_generation(7, 3));
    assert_eq!(l.receipts.replaced, 1);
    assert_eq!(l.payload_difference(7), Some("opaque-vertex-count"));
    l.release_owner(c, 7, 3, third, &mut e);
    assert!(!l.has_column_generation(7, 3));
}

#[test]
fn publication_waits_for_acknowledgement_and_retirement_follows_removal() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    l.record_built(c, 73, built(1, false), None, &mut e).unwrap();
    assert!(!l.request_publication(73).unwrap());
    publish_all(&mut l, c);
    assert!(l.request_publication(73).unwrap());
    assert!(!l.has_column_generation(73, 1) || l.has_published_column(73));
    // Without execution snapshots the acknowledged payload is discarded but stays drawable.
    assert_eq!(l.column_count(), 0);
    assert!(l.has_column(73));
    l.remove_column_generation(c, 73, 1, &mut e);
    assert!(!l.has_column(73), "a pending retirement hides the published column");
    assert!(l.has_published_column(73), "until Rust acknowledges the retirement");
    let update = publish_all(&mut l, c);
    assert_eq!(update.retirements, vec![(73, 1)]);
    assert!(!l.has_published_column(73));
}

#[test]
fn a_late_acknowledgement_after_a_reset_retires_instead_of_publishing() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    l.record_built(c, 5, built(1, false), None, &mut e).unwrap();
    let in_flight = l.pending_update(c, false).unwrap().unwrap();
    l.clear("world-unload", &mut e);
    assert_eq!(l.receipts.last_invalidated_in_flight, 1);
    l.record_built(c, 5, built(2, false), None, &mut e).unwrap();
    let assets = [Acknowledged { column_key: 5, generation: in_flight.assets[0].generation, counts: Counts::default() }];
    l.acknowledge(c, &assets, &[], &mut e);
    assert_eq!(l.published_generation(5), None, "the old world's generation is never published");
    let next = l.pending_update(c, false).unwrap().unwrap();
    assert_eq!(next.retirements, vec![(5, 1)]);
    assert_eq!(next.assets[0].generation, 2);
}

#[test]
fn eviction_follows_access_order_and_spares_protected_columns() {
    let mut l = Ledger::new();
    let c = Config::default();
    let mut e = Vec::new();
    for key in 1..=4 {
        l.record_built(c, key, built(key, false), None, &mut e).unwrap();
    }
    l.touch(1);
    l.acquire_owner(2, 2);
    e.clear();
    l.trim(2, i64::MAX, &mut e).unwrap();
    // Touching 1, then the owner check's hasColumn get of 2, leaves 3, 4, 1, 2;
    // 2 is owned: 3 then 4 go.
    assert_eq!(e, vec![Effect::DropCurrent(3), Effect::DropCurrent(4)]);
    assert_eq!(l.column_keys().collect::<Vec<_>>(), vec![1, 2]);
    assert_eq!(l.trim(0, 1, &mut e), Err(Failure::RetentionBounds));
}

#[test]
fn visible_columns_use_global_segment_indexes_and_wait_for_publication() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    l.record_built(c, 9, built(1, false), None, &mut e).unwrap();
    l.begin_frame(Some(0), "", "finite", "detail");
    assert_eq!(l.visible_column(9).unwrap(), Visible::Empty);
    assert_eq!(l.route.unpublished_visible_columns, 1);
    let update = l.pending_update(c, false).unwrap().unwrap();
    let counts = Counts { opaque: 2, side: 1, up: 0, water: 1 };
    l.acknowledge(c, &[Acknowledged { column_key: 9, generation: update.assets[0].generation, counts }], &[], &mut e);
    let Visible::Admit { generation, counts } = l.visible_column(9).unwrap() else { panic!() };
    l.append_visible_column(9, generation, counts).unwrap();
    let layers: Vec<(i32, i32, i32)> = l.pending_segments().iter().map(|i| (i.layer, i.segment_index, i.order)).collect();
    assert_eq!(layers, vec![(1, 0, 0), (1, 1, 1), (2, 2, 2), (4, 3, 3)]);
    assert!(l.select_route(false).unwrap());
    assert_eq!(l.route.reason, "reduced-color-with-partial-exact-atlas");
    let (visible, frame) = l.consume_frame();
    assert_eq!(visible.len(), 4);
    assert!(frame.enabled && frame.flags & ROUTE_SELECTED_FLAG != 0);
    assert!(!l.frame().enabled);
    assert_eq!(l.last_consumed().len(), 4);
}

#[test]
fn removing_a_selected_column_before_submit_rejects_the_route() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    l.record_built(c, 4, built(1, false), None, &mut e).unwrap();
    publish_all(&mut l, c);
    l.begin_frame(Some(0), "", "finite", "");
    let Visible::Admit { generation, counts } = l.visible_column(4).unwrap() else { panic!() };
    l.append_visible_column(4, generation, counts).unwrap();
    l.select_route(true).unwrap();
    l.remove_column(c, 4, &mut e);
    assert_eq!(l.route.reason, "visible-columns-retired-before-submit");
    assert!(!l.route.selected);
    assert_eq!(l.frame().flags & ROUTE_SELECTED_FLAG, 0);
}

#[test]
fn stale_lifecycle_executions_are_counted_not_recorded() {
    let mut l = Ledger::new();
    let mut e = Vec::new();
    assert!(l.record_execution(0, 3, 1, 0, 1, 1, 0, 0));
    assert_eq!(l.route.execution_count, 1);
    l.clear("resource-reload", &mut e);
    assert!(!l.record_execution(0, 4, 2, 0, 1, 1, 0, 0));
    assert_eq!(l.receipts.stale_route_executions, 1);
    assert_eq!(l.receipts.resource_reload_resets, 1);
    assert_eq!(l.route.last_executed_world_frame, 0);
}

#[test]
fn ordered_maps_follow_java_linked_hash_map_rules() {
    let mut insertion = OrderedMap::new();
    insertion.put(3, 'a');
    insertion.put(1, 'b');
    insertion.put(3, 'c');
    assert_eq!(insertion.iter().map(|(k, v)| (k, *v)).collect::<Vec<_>>(), vec![(3, 'c'), (1, 'b')]);
    let mut access = OrderedMap::access_ordered();
    access.put(3, ());
    access.put(1, ());
    access.get_touch(3);
    access.put(2, ());
    access.put(1, ());
    assert_eq!(access.keys().collect::<Vec<_>>(), vec![3, 2, 1]);
    assert!(access.peek(3).is_some());
    assert_eq!(access.keys().next(), Some(3), "peek does not reorder");
}

#[test]
fn only_a_selected_route_hands_over_its_segments() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    l.record_built(c, 6, built(1, false), None, &mut e).unwrap();
    publish_all(&mut l, c);
    for select in [false, true] {
        l.begin_frame(Some(0), "", "finite", "");
        let Visible::Admit { generation, counts } = l.visible_column(6).unwrap() else { panic!() };
        l.append_visible_column(6, generation, counts).unwrap();
        if select {
            l.select_route(true).unwrap();
        }
        let (visible, frame) = l.consume_frame();
        assert_eq!(visible.len(), usize::from(select), "selected={select}");
        assert!(frame.enabled);
        assert!(l.pending_segments().is_empty());
    }
    // A rejected route hands over nothing either.
    l.begin_frame(Some(0), "", "finite", "");
    let Visible::Admit { generation, counts } = l.visible_column(6).unwrap() else { panic!() };
    l.append_visible_column(6, generation, counts).unwrap();
    l.select_route(true).unwrap();
    l.reject_route("test", 1, 0, 0);
    assert!(l.consume_frame().0.is_empty());
}

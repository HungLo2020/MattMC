use super::payload::VERTEX_STRIDE;
use super::*;

fn config() -> Config {
    Config { rust_whole_frame: true, ..Config::default() }
}

/// One quad per segment, `segments[layer]` segments per layer; `variant`
/// is the first vertex's red channel.
fn payload(variant: u8, segments: [usize; 4]) -> Payload {
    let segment = |source_index: usize| {
        let mut bytes = vec![0u8; 4 * VERTEX_STRIDE];
        bytes[8] = variant;
        Segment { source_index: source_index as i32, bytes: bytes.into_boxed_slice() }
    };
    Payload { origin: [0, 0, 0], layers: segments.map(|count| (0..count).map(segment).collect()) }
}

fn built_with(generation: i64, payload: Payload) -> Built {
    Built { column: Column::new(generation, payload), provenance_bytes: None, same_provenance: true }
}

/// A one-segment opaque column; equal variants are equal payloads.
fn built(generation: i64, variant: u8) -> Built {
    built_with(generation, payload(variant, [1, 0, 0, 0]))
}

fn publish_all(ledger: &mut Ledger, config: Config) -> PendingUpdate {
    let update = ledger.pending_update(config, false).unwrap().unwrap();
    let assets: Vec<Acknowledged> = update
        .assets
        .iter()
        .map(|a| Acknowledged { column_key: a.column_key, generation: a.generation, payload: a.payload.clone() })
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
    assert_eq!(l.record_built(c, 7, built(1, 1), &mut e).unwrap(), 1);
    let first = l.acquire_owner(7, 1);
    assert_eq!(l.record_built(c, 7, built(2, 1), &mut e).unwrap(), 1, "same payload keeps generation 1");
    let second = l.acquire_owner(7, 1);
    assert_eq!(first, second, "owners of one generation share a group");
    l.release_owner(c, 7, 1, first, &mut e);
    assert!(l.has_column_generation(7, 1), "another owner keeps it alive");
    // A changed payload replaces the group; the stale lease cannot remove generation 3.
    assert_eq!(l.record_built(c, 7, built(3, 3), &mut e).unwrap(), 3);
    let third = l.acquire_owner(7, 3);
    assert_ne!(third, second);
    l.release_owner(c, 7, 1, second, &mut e);
    assert!(l.has_column_generation(7, 3));
    assert_eq!(l.receipts.replaced, 1);
    assert_eq!(l.payload_difference(7), Some("opaque[0].vertex[0].red=1->3"));
    l.release_owner(c, 7, 3, third, &mut e);
    assert!(!l.has_column_generation(7, 3));
}

#[test]
fn publication_waits_for_acknowledgement_and_retirement_follows_removal() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    l.record_built(c, 73, built(1, 1 as u8), &mut e).unwrap();
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
    l.record_built(c, 5, built(1, 1 as u8), &mut e).unwrap();
    let in_flight = l.pending_update(c, false).unwrap().unwrap();
    l.clear("world-unload", &mut e);
    assert_eq!(l.receipts.last_invalidated_in_flight, 1);
    l.record_built(c, 5, built(2, 2 as u8), &mut e).unwrap();
    let assets = [Acknowledged { column_key: 5, generation: in_flight.assets[0].generation, payload: in_flight.assets[0].payload.clone() }];
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
        l.record_built(c, key, built(key, key as u8), &mut e).unwrap();
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
    l.record_built(c, 9, built_with(1, payload(1, [2, 1, 0, 1])), &mut e).unwrap();
    l.begin_frame(Some(0), "", "finite", "detail");
    assert_eq!(l.visible_column(9).unwrap(), Visible::Empty);
    assert_eq!(l.route.unpublished_visible_columns, 1);
    let update = l.pending_update(c, false).unwrap().unwrap();
    let sent = &update.assets[0];
    l.acknowledge(c, &[Acknowledged { column_key: 9, generation: sent.generation, payload: sent.payload.clone() }], &[], &mut e);
    let Visible::Admit { generation, counts } = l.visible_column(9).unwrap() else { panic!() };
    l.append_visible_column(9, generation, counts).unwrap();
    let layers: Vec<(u32, u32, u32)> = l.pending_segments().iter().map(|i| (i.layer, i.segment_index, i.order)).collect();
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
    l.record_built(c, 4, built(1, 1 as u8), &mut e).unwrap();
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
    l.record_built(c, 6, built(1, 1 as u8), &mut e).unwrap();
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

fn segment(source_index: i32, vertices: &[[u8; VERTEX_STRIDE]]) -> Segment {
    Segment { source_index, bytes: vertices.concat().into_boxed_slice() }
}

fn quad(vertex: [u8; VERTEX_STRIDE]) -> Vec<[u8; VERTEX_STRIDE]> {
    vec![vertex; 4]
}

#[test]
fn payload_differences_use_java_names_in_java_order() {
    let base = payload(1, [1, 1, 0, 0]);
    let mut moved = base.clone();
    moved.origin[1] = 16;
    assert_eq!(base.difference(&moved), "origin");
    assert_eq!(base.difference(&payload(1, [1, 2, 0, 0])), "transparent-side-segment-count");
    let mut reindexed = base.clone();
    reindexed.layers[0][0].source_index = 3;
    assert_eq!(base.difference(&reindexed), "opaque-source-index");
    let mut longer = base.clone();
    longer.layers[1][0] = segment(0, &[quad([0; 16]), quad([0; 16])].concat());
    assert_eq!(base.difference(&longer), "transparent-side-vertex-count");
    // The fixture's vertex 0 is red=1; light vertex 1 only.
    let mut relit = base.clone();
    relit.layers[0][0].bytes[VERTEX_STRIDE + 6..VERTEX_STRIDE + 8].copy_from_slice(&0x0102u16.to_ne_bytes());
    assert_eq!(base.difference(&relit), "opaque[0].vertex[1].packed-light-micro=0->258");
    assert_eq!(base.difference(&base.clone()), "unknown");
}

#[test]
fn assets_decode_and_reject_like_the_bridge() {
    let mut vertex = [0u8; 16];
    vertex[0..2].copy_from_slice(&7u16.to_ne_bytes());
    vertex[6..8].copy_from_slice(&0x0302u16.to_ne_bytes());
    vertex[8..12].copy_from_slice(&[10, 20, 30, 40]);
    vertex[12] = 15;
    vertex[13] = 5;
    let column = Payload {
        origin: [1, 2, 3],
        layers: [vec![segment(4, &quad(vertex))], vec![], vec![], vec![segment(0, &quad(vertex))]],
    };
    let asset = column.asset(-2, 9).unwrap();
    assert_eq!((asset.column_key, asset.column_generation, asset.vertex_layout_version, asset.origin), (u64::MAX - 1, 9, 1, [1, 2, 3]));
    assert_eq!(asset.segments.iter().map(|s| s.layer).collect::<Vec<_>>(), vec![1, 4]);
    let decoded = &asset.segments[0].vertices[0];
    assert_eq!(decoded.local_position, [7, 0, 0]);
    assert_eq!(decoded.packed_light_and_micro_offset, 0x0302);
    assert_eq!((decoded.color_rgba, decoded.material_id, decoded.normal_index), ([10, 20, 30, 40], 15, 5));

    let message = |payload: &Payload, generation: i64| payload.asset(1, generation).unwrap_err().message;
    assert_eq!(message(&column, 0), "world LOD column asset generation must be non-zero");
    assert_eq!(message(&Payload::default(), 1), "world LOD column 1 has 0 segments; expected 1..=512");
    let with = |vertex: [u8; 16]| Payload { origin: [0; 3], layers: [vec![segment(0, &quad(vertex))], vec![], vec![], vec![]] };
    let mut padded = vertex;
    padded[15] = 1;
    assert_eq!(message(&with(padded), 1), "packed world LOD vertex has non-zero reserved padding");
    let mut material = vertex;
    material[12] = 16;
    assert_eq!(message(&with(material), 1), "packed world LOD vertex contains an out-of-range material or normal");
    let mut normal = vertex;
    normal[13] = 6;
    assert_eq!(message(&with(normal), 1), "packed world LOD vertex contains an out-of-range material or normal");
    let partial = Payload { origin: [0; 3], layers: [vec![segment(0, &[vertex; 2])], vec![], vec![], vec![]] };
    assert_eq!(message(&partial, 1), "world LOD segment has 2 vertices; expected quad-aligned 1..=2097152");
}

#[test]
fn the_rust_flush_leaves_provenance_updates_to_java_and_acknowledges_what_it_sent() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    assert!(matches!(l.select_flush(c).unwrap(), Flush::Nothing));
    let with_provenance = Built { provenance_bytes: Some(64), ..built_with(1, payload(1, [1, 0, 0, 0])) };
    l.record_built(c, 3, with_provenance, &mut e).unwrap();
    assert!(matches!(l.select_flush(c).unwrap(), Flush::NeedsJava));
    let java = l.pending_update(c, false).unwrap().unwrap();
    assert_eq!(java.assets.len(), 1, "declining marked nothing in flight");

    l.record_built(c, 8, built_with(2, payload(2, [2, 0, 1, 0])), &mut e).unwrap();
    let Flush::Selected(update) = l.select_flush(c).unwrap() else { panic!("expected a selection") };
    assert_eq!(update.assets.iter().map(|a| a.column_key).collect::<Vec<_>>(), vec![8]);
    assert!(l.peek_pending_update(true).unwrap().is_none(), "the selection is in flight");
    // Java acknowledges by key and generation; the ledger supplies the sent payload.
    let sent = l.sent_payload(8, 2).expect("in flight");
    l.acknowledge(c, &[Acknowledged { column_key: 8, generation: 2, payload: sent }], &[], &mut e);
    assert_eq!(l.sent_payload(8, 2), None);
    l.begin_frame(Some(0), "", "finite", "detail");
    assert_eq!(l.visible_column(8).unwrap(), Visible::Admit { generation: 2, counts: Counts { opaque: 2, side: 0, up: 1, water: 0 } });
}

/// `DhSectionPos.encode`.
fn section(detail: i64, x: i64, z: i64) -> i64 {
    detail | ((x & 0x0FFF_FFFF) << 8) | ((z & 0x0FFF_FFFF) << 36)
}

#[test]
fn section_centers_follow_dh_section_pos() {
    use super::visibility::section_center;
    assert_eq!(section_center(section(6, 3, 0)), [224, 32]);
    assert_eq!(section_center(section(6, -4, 5)), [-224, 352]);
    assert_eq!(section_center(section(7, 0, -1)), [64, -64]);
    assert_eq!(section_center(section(1, -3, 2)), [-6, 4]);
    assert_eq!(section_center(section(0, -7, 9)), [-7, 9]);
}

#[test]
fn the_visible_frame_sorts_stably_and_requests_unpublished_columns_in_walk_order() {
    let mut l = Ledger::new();
    let c = config();
    let mut e = Vec::new();
    // Equal distance from the origin: walk order decides.
    let (a, b, near) = (section(6, 1, 0), section(6, 0, 1), section(6, 0, 0));
    for (generation, key) in [(1, a), (2, b), (3, near)] {
        l.record_built(c, key, built(generation, generation as u8), &mut e).unwrap();
    }
    publish_all(&mut l, c);
    l.record_built(c, a, built(4, 4), &mut e).unwrap();
    let pending = section(6, 5, 5);
    l.record_built(c, pending, built(5, 5), &mut e).unwrap();
    l.begin_frame(Some(0), "", "finite", "detail");
    // The container still holding generation 7 of `near` is stale.
    let walked = [(b, 2), (pending, 5), (a, 4), (near, 3), (near, 7)];
    let frame = l.collect_visible_frame(&walked, [0, 0], true, true).unwrap();
    assert_eq!(frame.sorted, vec![near, b, a, pending]);
    assert_eq!(frame.stale, 1);
    assert_eq!(frame.unpublished, 1, "only the never-published column stays unpublished");
    assert_eq!(frame.counts, Counts { opaque: 3, ..Counts::default() });
    assert_eq!(l.route.candidate_columns, 4);
    // Ties keep walk order either way round.
    assert_eq!(l.collect_visible_frame(&[(a, 4), (b, 2)], [0, 0], true, false).unwrap().sorted, vec![a, b]);
    // A container whose generation was replaced and retired is stale.
    assert_eq!(l.collect_visible_frame(&[(b, 9)], [0, 0], true, false).unwrap().stale, 1);
}

#[test]
fn visible_publication_follows_the_walk_order_of_requests() {
    // Without the whole-frame route a build marks no visible demand, so the
    // frame's requests alone order the visible publication.
    let c = Config::default();
    let (first, second) = (section(6, 3, 0), section(6, 0, 3));
    for walk in [[(first, 2), (second, 1)], [(second, 1), (first, 2)]] {
        let mut l = Ledger::new();
        let mut e = Vec::new();
        l.record_built(c, second, built(1, 1), &mut e).unwrap();
        l.record_built(c, first, built(2, 2), &mut e).unwrap();
        l.begin_frame(Some(0), "", "finite", "detail");
        let frame = l.collect_visible_frame(&walk, [0, 0], true, false).unwrap();
        assert_eq!(frame.unpublished, 2);
        let update = l.peek_pending_update(true).unwrap().expect("visible demand");
        let order: Vec<i64> = walk.iter().map(|&(key, _)| key).collect();
        assert_eq!(update.assets.iter().map(|a| a.column_key).collect::<Vec<_>>(), order);
    }
    // A disabled collector neither requests nor records: no visible demand.
    let mut l = Ledger::new();
    let mut e = Vec::new();
    l.record_built(c, first, built(1, 1), &mut e).unwrap();
    assert_eq!(l.collect_visible_frame(&[(first, 1)], [0, 0], false, false).unwrap().unpublished, 1);
    assert!(l.peek_pending_update(true).unwrap().is_none());
}

#[test]
fn the_lifecycle_check_touches_the_column_lru_in_walk_order() {
    let mut l = Ledger::new();
    let c = Config::default();
    let mut e = Vec::new();
    for key in 1..=3 {
        l.record_built(c, key, built(key, key as u8), &mut e).unwrap();
    }
    l.collect_visible_frame(&[(1, 1), (3, 3)], [0, 0], true, false).unwrap();
    // hasColumn's get, then requestPublication's get, for 1 then 3.
    assert_eq!(l.column_keys().collect::<Vec<_>>(), vec![2, 1, 3]);
}

use super::*;

#[test]
fn ordered_identity_map_replaces_in_place_and_reclaims_after_growth() {
    let mut table = Table::default();
    let keys: Vec<_> = (0..4096i32).map(|i| i.wrapping_mul(0x12345)).collect();
    for (i, &key) in keys.iter().enumerate() {
        assert_eq!(table.put(key, i as u64 + 1), 0);
    }
    assert_eq!(
        table.ordered(),
        keys.iter()
            .enumerate()
            .map(|(i, &k)| (k, i as u64 + 1))
            .collect::<Vec<_>>()
    );
    assert_eq!(table.put(keys[1024], 9000), 1025);
    assert_eq!(table.ordered()[1024], (keys[1024], 9000));
    for &key in keys.iter().rev() {
        assert_ne!(table.remove(key), 0);
    }
    assert!(table.ordered().is_empty());
    assert_eq!(table.nodes.len(), 33);
}

#[test]
fn pending_binding_survives_reentrant_uuid_removal_and_identity_commit() {
    let mut r = Registry::default();
    let key = Some((1, 2));
    let first = r.begin_add(key);
    assert_eq!(r.begin_add(key), 0);
    assert_eq!(
        r.remove_uuid(key),
        0,
        "ID getter's pending receiver remains alive"
    );
    let second = r.begin_add(key);
    assert_ne!(first as u32, second as u32);
    assert_eq!(r.finish_add(second, Some(3)), Some(0));
    assert_eq!(r.finish_add(first, Some(3)), Some(0));
    assert_eq!(r.ids.get(3), first);
    assert_eq!(r.uuids[&key], second);
    assert_eq!(r.remove_id(3), first);
    assert_eq!(r.remove_uuid(key), second);
    assert!(r.free.len() == 2);
}

#[test]
fn failed_id_getter_keeps_uuid_prefix_and_null_uuid_is_distinct() {
    let mut r = Registry::default();
    let null = r.begin_add(None);
    let zero = r.begin_add(Some((0, 0)));
    assert_ne!(null, zero);
    assert_eq!(r.finish_add(null, None), Some(0));
    assert_eq!(r.finish_add(zero, Some(0)), Some(0));
    assert_eq!(r.uuids.len(), 2);
    assert_eq!(r.remove_uuid(None), null);
    assert_eq!(r.remove_uuid(Some((0, 0))), 0);
    assert_eq!(r.remove_id(0), zero);
}

#[test]
fn live_binding_storage_stays_bounded_across_repeated_tracking() {
    let mut r = Registry::default();
    for i in 0..10_000 {
        let token = r.begin_add(Some((0, i)));
        assert_eq!(r.finish_add(token, Some(i as i32)), Some(0));
        assert_eq!(r.remove_uuid(Some((0, i))), 0);
        assert_eq!(r.remove_id(i as i32), token);
    }
    assert_eq!(r.bindings.len(), 1);
    assert_eq!(r.ids.nodes.len(), 33);
    assert!(r.uuids.is_empty());
}

#[test]
fn borrowed_cpu_layout_matches_java() {
    use std::mem::{offset_of, size_of};
    assert_eq!(size_of::<View>(), 32);
    assert_eq!(offset_of!(View, first), 24);
    assert_eq!(offset_of!(View, uuid_count), 28);
    assert_eq!(size_of::<id_table::Node>(), 24);
    assert_eq!(offset_of!(id_table::Node, token), 8);
}

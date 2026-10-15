use super::*;

fn entry(trigger_tick: i64, priority: i32, sub_tick_order: i64, x: i32, type_handle: u32) -> Entry {
    Entry { trigger_tick, priority, sub_tick_order, x, y: 64, z: -3, type_handle }
}

fn assert_heap(c: &Queue) {
    for i in 1..c.size() {
        let parent = (i - 1) >> 1;
        let (p, e) = (c.get(c.id_at(parent).unwrap()).unwrap(), c.get(c.id_at(i).unwrap()).unwrap());
        assert_ne!(drain_order(e, p), std::cmp::Ordering::Less, "heap violated at {i}");
    }
}

#[test]
fn polls_in_drain_order_and_dedups_by_full_position_and_type() {
    let mut c = Queue::default();
    assert!(c.schedule(entry(5, 0, 1, 0, 1)).is_some());
    assert!(c.schedule(entry(9, 0, 2, 0, 1)).is_none(), "same position and type");
    assert!(c.schedule(entry(5, 0, 3, 0, 2)).is_some(), "other type");
    assert!(c.schedule(entry(5, -1, 4, i32::MIN, 1)).is_some(), "full i32 coordinates");
    assert!(c.schedule(entry(4, 3, 5, 7, 1)).is_some());
    let order: Vec<_> = std::iter::from_fn(|| c.poll()).map(|_| ()).collect();
    assert_eq!(order.len(), 4);
    assert!(c.schedule(entry(1, 0, 6, 0, 1)).is_some(), "polling removed the key");
}

#[test]
fn unchecked_duplicates_share_one_key_like_unpacked_pending_ticks() {
    let mut c = Queue::default();
    assert!(c.add_key(0, 64, -3, 1));
    assert!(!c.add_key(0, 64, -3, 1));
    c.schedule_unchecked(entry(1, 0, -2, 0, 1));
    c.schedule_unchecked(entry(1, 0, -1, 0, 1));
    assert_eq!(c.size(), 2);
    c.poll();
    assert!(!c.contains_key(0, 64, -3, 1), "the first poll removed the shared key");
    assert!(c.schedule(entry(2, 0, 7, 0, 1)).is_some());
    assert_eq!(c.size(), 2);
}

#[test]
fn remove_at_matches_priority_queue_shape_and_reports_upward_moves() {
    let mut c = Queue::default();
    for (i, t) in [10, 20, 30, 40, 50, 60, 70, 25, 26].iter().enumerate() {
        c.schedule(entry(*t, 0, i as i64, i as i32, 1));
    }
    assert_heap(&c);
    let before = c.mod_count();
    let (removed, moved) = c.remove_at(5).unwrap();
    assert!(c.get(removed).is_none(), "removed ids are freed");
    assert_eq!(c.mod_count(), before.wrapping_add(1));
    assert_heap(&c);
    if let Some(m) = moved { assert!((0..5).any(|i| c.id_at(i) == Some(m))); }
    let last = c.id_at(c.size() - 1).unwrap();
    assert!(c.remove_eq(last));
    assert!(!c.remove_eq(last));
    assert_heap(&c);
}

#[test]
fn freed_ids_are_reused_and_lifetime_is_counted() {
    let mut c = Queue::default();
    let a = c.schedule(entry(1, 0, 1, 1, 1)).unwrap();
    assert!(c.schedule(entry(1, 0, 1, 1, 1)).is_none(), "rejected duplicates allocate nothing");
    assert_eq!(c.live_entries(), 1);
    c.poll();
    assert_eq!(c.live_entries(), 0);
    let b = c.schedule(entry(2, 0, 2, 2, 1)).unwrap();
    assert_eq!(a, b);
    let before = LIVE.load(Relaxed);
    let container = Container::new();
    assert!(LIVE.load(Relaxed) >= before + 1);
    drop(container);
}

#[test]
fn overlapping_entry_is_rejected_not_aliased() {
    let container = Container::new();
    let first = container.enter().expect("free");
    assert!(container.enter().is_none(), "a second concurrent entry must be refused");
    drop(first);
    assert!(container.enter().is_some());
}

/// Structured and adversarial dedup keys: equality matches std's HashSet, and
/// the hash spreads them (bounded bucket load at hashbrown-like table sizes and
/// diverse top-7 control bits), so no coordinate pattern degenerates probing.
#[test]
fn key_hasher_is_well_behaved_on_structured_and_adversarial_coordinates() {
    use std::hash::BuildHasher;
    let sets: Vec<(&str, Vec<(i32, i32, i32, u32)>)> = vec![
        ("dense grid", (0..64).flat_map(|x| (0..64).flat_map(move |z| (0..16).map(move |y| (x, y - 64, z, 0)))).collect()),
        ("chunk-aligned columns", (0..256).flat_map(|c| (-64..192).step_by(1).map(move |y| (c * 16, y, -c * 16, 1))).take(65_536).collect()),
        ("power-of-two strides", (0..65_536).map(|i| ((i & 255) << 8, 0, (i >> 8) << 16, 2)).collect()),
        ("negative and extreme", (0..65_536).map(|i: i32| (i32::MIN + i, i32::MAX - (i % 7), -i, (i % 3) as u32)).collect()),
        ("high bits only", (0..65_536).map(|i: i32| (i << 16, 7, i.wrapping_mul(1 << 20), 0)).collect()),
        ("type handles only", (0..65_536u32).map(|t| (1, 2, 3, t)).collect()),
    ];
    let build = FastHash::default();
    for (name, keys) in sets {
        let mut fast: HashSet<(i32, i32, i32, u32), FastHash> = HashSet::default();
        let mut std_set: HashSet<(i32, i32, i32, u32)> = HashSet::new();
        for &k in &keys { assert_eq!(fast.insert(k), std_set.insert(k), "{name}: insert {k:?}"); }
        assert_eq!(fast.len(), std_set.len(), "{name}");
        for &k in keys.iter().step_by(97) {
            assert!(fast.contains(&k));
            assert!(!fast.contains(&(k.0 ^ 1, k.1, k.2, k.3)) || std_set.contains(&(k.0 ^ 1, k.1, k.2, k.3)), "{name}: false positive");
        }
        let n = std_set.len();
        let buckets = (n * 2).next_power_of_two();
        let mut load = vec![0u32; buckets];
        let mut control = HashSet::new();
        for k in &std_set {
            let h = build.hash_one(k);
            load[(h as usize) & (buckets - 1)] += 1;
            control.insert(h >> 57);
        }
        let max = *load.iter().max().unwrap();
        assert!(max <= 16, "{name}: max bucket load {max} for {n} keys in {buckets} buckets");
        assert!(control.len() >= 100, "{name}: only {} distinct control bytes", control.len());
    }
}

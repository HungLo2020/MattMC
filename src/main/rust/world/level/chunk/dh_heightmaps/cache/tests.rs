use super::*;
use crate::content::block::StateFlags;
fn catalog() -> Catalog {
    Catalog::new(vec![0, 1, -1], vec![vec![], vec![[0., 0., 0., 1., 1., 1.]]], |id|
        (if id == 0 { StateFlags::AIR } else { StateFlags::CAN_OCCLUDE }, 15)).unwrap()
}
fn owner(ids: &[u32]) -> live::Owner {
    let mut words = vec![0u64; 1024];
    for (index, &id) in ids.iter().enumerate() { words[index / 4] |= (id as u64) << ((index % 4) * 15); }
    live::Owner::load(15, 9, &[], &words, 31809, 15).unwrap()
}
#[test]
fn all_actual_frozen_saved_fields_are_reused_without_changing_values() {
    let fixture = super::super::fixture::load();
    let cache = Mutex::new(Cache::new(&fixture.catalog));
    for chunk in &fixture.chunks {
        let owners: Vec<_> = chunk.states.iter().map(|s| owner(s)).collect();
        let counters: Vec<_> = chunk.states.iter().map(|s| counters::Owner::new(
            s.iter().filter(|&&id| !fixture.flags[id as usize].contains(StateFlags::AIR)).count() as u64)).collect();
        let inputs: Vec<_> = owners.iter().zip(&counters).collect();
        let first = build(&inputs, chunk.min_y, &cache).unwrap();
        let second = build(&inputs, chunk.min_y, &cache).unwrap();
        assert!(Arc::ptr_eq(&first, &second));
        assert_eq!((first.min, first.max), (chunk.min, chunk.max));
        assert_eq!(first.solid, chunk.solid); assert_eq!(first.blocking, chunk.blocking);
    }
}
#[test]
fn mutations_counter_masks_minimums_and_contextual_states_cannot_reuse_stale_fields() {
    let catalog = catalog(); let cache = Mutex::new(Cache::new(&catalog));
    let stone = owner(&vec![1; 4096]); let counts = counters::Owner::new(4096);
    let inputs = [(&stone, &counts)];
    let first = build(&inputs, 0, &cache).unwrap(); assert_eq!(first.solid[0], 15);
    stone.write(15 * 256, 0).unwrap();
    let changed = build(&inputs, 0, &cache).unwrap();
    assert!(!Arc::ptr_eq(&first, &changed)); assert_eq!(changed.solid[0], 14); assert_eq!(first.solid[0], 15);
    assert!(Arc::ptr_eq(&changed, &build(&inputs, 0, &cache).unwrap()));
    let shifted = build(&inputs, -64, &cache).unwrap(); assert_eq!(shifted.solid[0], -50);
    counts.set([0, 0, 0]);
    let empty = build(&inputs, 0, &cache).unwrap(); assert_eq!(empty.solid, [0; 256]);
    stone.write(1, 2).unwrap(); // Stale-empty masks deliberately hide contextual data.
    assert!(Arc::ptr_eq(&empty, &build(&inputs, 0, &cache).unwrap()));
    counts.set([4095, 0, 0]); assert!(build(&inputs, 0, &cache).is_none());
    stone.write(1, 1).unwrap(); let recovered = build(&inputs, 0, &cache).unwrap();
    assert_eq!(recovered.solid[0], 14); assert!(!Arc::ptr_eq(&changed, &recovered));
    assert!(build(&inputs, 1, &cache).is_none()); assert!(build(&[], 0, &cache).is_none());
}
#[test]
fn single_palette_alias_and_growth_detachment_preserve_independent_retained_results() {
    let catalog = catalog(); let cache = Mutex::new(Cache::new(&catalog));
    let original = live::Owner::load(0, 0, &[1], &[], 31809, 15).unwrap();
    let alias = original.copy(); let counts = counters::Owner::new(4096);
    let first = build(&[(&original, &counts)], 0, &cache).unwrap();
    assert!(alias.read_single(0));
    let air = build(&[(&original, &counts)], 0, &cache).unwrap();
    assert_eq!(air.solid, [0; 256]); assert_eq!(first.solid, [15; 256]);
    assert!(Arc::ptr_eq(&air, &build(&[(&alias, &counts)], 0, &cache).unwrap()));
    original.write(15 * 256, 1).unwrap();
    let detached = build(&[(&original, &counts)], 0, &cache).unwrap();
    assert_eq!(detached.solid[0], 15); assert_eq!(detached.solid[1], 0);
    assert!(Arc::ptr_eq(&air, &build(&[(&alias, &counts)], 0, &cache).unwrap()));
}
#[test]
fn bounded_cache_does_not_pin_storage_and_eviction_does_not_invalidate_cpu_readers() {
    let catalog = catalog(); let cache = Mutex::new(Cache::new(&catalog));
    let owners: Vec<_> = (0..LIMIT + 8).map(|_| owner(&vec![1; 4096])).collect();
    let counts = counters::Owner::new(4096);
    let first = build(&[(&owners[0], &counts)], 0, &cache).unwrap();
    for storage in &owners { build(&[(storage, &counts)], 0, &cache).unwrap(); }
    assert_eq!(cache.lock().unwrap().entries.len(), LIMIT);
    let stamp = owners[0].read_stamp().unwrap();
    assert!(!Arc::ptr_eq(&first, &build(&[(&owners[0], &counts)], 0, &cache).unwrap()));
    drop(owners); assert!(!stamp.alive()); assert_eq!(first.solid, [15; 256]);
    let next = owner(&vec![1; 4096]);
    build(&[(&next, &counts)], 0, &cache).unwrap();
    assert_eq!(cache.lock().unwrap().entries.len(), 1);
}
#[test]
fn independent_requests_share_fields_without_serializing_world_scans_under_cache_lock() {
    let catalog = catalog(); let cache = Mutex::new(Cache::new(&catalog));
    let owner = owner(&vec![1; 4096]); let counts = counters::Owner::new(4096);
    let expected = build(&[(&owner, &counts)], 0, &cache).unwrap();
    std::thread::scope(|scope| {
        for _ in 0..8 { scope.spawn(|| {
            for _ in 0..64 {
                assert!(Arc::ptr_eq(&expected, &build(&[(&owner, &counts)], 0, &cache).unwrap()));
            }
        }); }
    });
}

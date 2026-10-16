#[path = "../../../../src/main/rust/content/item/runtime/inventory.rs"] mod inventory;
#[path = "../../../../src/main/rust/content/item/runtime/stack.rs"] mod stack;
#[path = "../../../../src/main/rust/content/item/runtime/slots.rs"] mod slots;
// Native-body/projection oracle; no JVM or production consumer admission claim.
#[path = "../../../../src/main/rust/content/item/runtime/patch.rs"]
mod patch;
use patch::{AccessPatch, CanonicalPatch, Key};
use std::sync::atomic::{AtomicU64, Ordering};

enum Map { Old(AccessPatch), Canonical(CanonicalPatch) }
impl Map {
    fn new(canonical: bool) -> Self { if canonical { Self::Canonical(CanonicalPatch::default()) } else { Self::Old(AccessPatch::default()) } }
    fn address(&self) -> usize { match self { Self::Old(p) => p.address(), Self::Canonical(p) => p.address() } }
    fn len(&self) -> usize { match self { Self::Old(p) => p.len(), Self::Canonical(p) => p.len() } }
    fn get(&self, key: Key) -> u64 { match self { Self::Old(p) => p.get(key), Self::Canonical(p) => p.get(key) } }
    fn put(&self, key: Key, value: u64) -> u64 { match self { Self::Old(p) => p.put(key, value), Self::Canonical(p) => p.put(key, value) } }
    fn at(&self, index: usize) -> Option<(Key, u64)> { match self { Self::Old(p) => p.at(index), Self::Canonical(p) => p.at(index) } }
    fn detached(&self) -> Self { match self { Self::Old(p) => Self::Old(p.detached()), Self::Canonical(p) => Self::Canonical(p.detached()) } }
    fn alias(&self) -> Self { match self { Self::Old(p) => Self::Old(p.clone()), Self::Canonical(p) => Self::Canonical(p.clone()) } }
    fn canonical(&self) -> bool { matches!(self, Self::Canonical(_)) }
    fn try_replace(&self, key: Key, value: u64, corrupt: bool) -> Option<u64> {
        let Self::Canonical(patch) = self else { return None; };
        if value <= 1 { return None; }
        let address = projected_word(self, key.0); if address == 0 { return None; }
        unsafe {
            let cell = address as *const AtomicU64;
            let expected = (*cell).load(Ordering::Acquire); if expected <= 1 { return None; }
            let incarnation = (*cell.add(5)).load(Ordering::Acquire) + u64::from(corrupt);
            // Single-threaded oracle is the common serialization guard here.
            patch::canonical_replace(address as *const patch::CanonicalCell, patch.version(), key.0, incarnation, expected, value)
        }
    }
}
fn projected_word(patch: &Map, key: u64) -> u64 {
    // The patch Arc pins the actual descriptor and every geometrically retired
    // allocation. This single-threaded oracle never overlaps a native writer.
    unsafe {
        let header = patch.address() as *const AtomicU64;
        let descriptor = (*header.add(1)).load(Ordering::Acquire) as *const usize;
        let mask = *descriptor;
        let rows = *descriptor.add(1) as *const AtomicU64;
        let mixed = key.wrapping_mul(0x9e3779b97f4a7c15);
        let mut slot = (mixed ^ (mixed >> 32)) as usize & mask;
        for _ in 0..=mask {
            let found = (*rows.add(slot * 2)).load(Ordering::Acquire);
            if found == key { return (*rows.add(slot * 2 + 1)).load(Ordering::Acquire); }
            if found == 0 { return 0; }
            slot = (slot + 1) & mask;
        }
        0
    }
}
fn projected(patch: &Map, key: u64) -> u64 {
    let word = projected_word(patch, key);
    if patch.canonical() && word != 0 { unsafe { (*(word as *const AtomicU64)).load(Ordering::Acquire) } } else { word }
}
fn run_ordered_model(canonical: bool) {
    let mut maps: Vec<Map> = (0..16).map(|_| Map::new(canonical)).collect();
    let mut expected: Vec<Vec<(Key, u64)>> = vec![Vec::new(); maps.len()];
    let mut random = 0x231d_4891_a773_b459u64;
    for step in 0..20_000 {
        random ^= random << 13; random ^= random >> 7; random ^= random << 17;
        let owner = (random as usize >> 4) & 15;
        let key = Key(((random >> 17) % 47) + 1);
        let value = match (random >> 33) % 5 { 0 => 0, 1 => 1, _ => step as u64 + 2 };
        let old = expected[owner].iter().find(|row| row.0 == key).map(|row| row.1).unwrap_or(0);
        if step % 83 == 0 { assert_eq!(maps[owner].try_replace(key, value, true), None); assert_eq!(maps[owner].get(key), old); }
        let replaced = if step % 3 == 0 { maps[owner].try_replace(key, value, false) } else { None };
        assert_eq!(replaced.unwrap_or_else(|| maps[owner].put(key, value)), old);
        if value == 0 { expected[owner].retain(|row| row.0 != key); }
        else if let Some(row) = expected[owner].iter_mut().find(|row| row.0 == key) { row.1 = value; }
        else { expected[owner].push((key, value)); }
        if step % 7 == 0 {
            let target = (owner + 1) & 15;
            maps[target] = maps[owner].detached(); expected[target] = expected[owner].clone();
        }
        if step % 11 == 0 {
            // Escaped aliases mutate the SAME backing, unlike detached versions.
            let alias = maps[owner].alias(); assert_eq!(alias.put(key, value), value);
        }
        if step % 31 == 0 {
            for (map, model) in maps.iter().zip(expected.iter()) {
                assert_eq!(map.len(), model.len());
                for id in 1..=47 {
                    let wanted = model.iter().find(|row| row.0 == Key(id)).map(|row| row.1).unwrap_or(0);
                    assert_eq!(map.get(Key(id)), wanted);
                    assert_eq!(projected(map, id), wanted, "native mutable index disagrees with visible ordered body");
                }
                for (index, row) in model.iter().enumerate() { assert_eq!(map.at(index), Some(*row)); }
                assert_eq!(map.at(model.len()), None);
                for index in 0..4 {
                    let word = unsafe { (*(map.address() as *const AtomicU64).add(4 + index)).load(Ordering::Acquire) };
                    let value = if map.canonical() && word != 0 { unsafe { (*(word as *const AtomicU64)).load(Ordering::Acquire) } } else { word };
                    assert_eq!(value, model.get(index).map(|row| row.1).unwrap_or(0), "native inline row disagrees with order");
                }
            }
        }
    }
}

#[test] fn ordered_model_matches_shared_bodies_detached_versions_and_all_native_projections() { run_ordered_model(false); }
#[test] fn canonical_cells_match_ordered_model_and_direct_native_replacements() { run_ordered_model(true); }

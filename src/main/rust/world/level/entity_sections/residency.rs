//! Primary client/server entity residency and persistence admission state.
use super::{residency_set::LongSet, section_map::SectionMap};
use std::collections::HashSet;
pub(super) type Uuid = Option<(u64, u64)>;
pub(super) struct Residency {
    pub(super) known: HashSet<Uuid>,
    pub(super) maps: [SectionMap; 2],
    pub(super) defaults: [i32; 2],
    pub(super) sets: [LongSet; 2],
    pub(super) output: Vec<i64>,
}
impl Default for Residency {
    fn default() -> Self {
        Self {
            known: HashSet::new(),
            maps: std::array::from_fn(|_| SectionMap::default()),
            defaults: [-1, -1],
            sets: std::array::from_fn(|_| LongSet::default()),
            output: Vec::new(),
        }
    }
}
impl Residency {
    pub(super) fn get(&self, map: usize, key: i64) -> i32 {
        let value = self.maps[map].get(key);
        if value == 0 {
            self.defaults[map]
        } else {
            value as i32 - 2
        }
    }
    pub(super) fn loaded_keys(&mut self) {
        self.output = self.maps[1]
            .keys()
            .filter(|&key| self.maps[1].get(key) == 4)
            .collect();
    }
    pub(super) fn counts(&self) -> [i32; 5] {
        [
            self.known.len() as i32,
            self.maps[0].len() as i32,
            self.maps[1].len() as i32,
            self.sets[0].size,
            self.sets[1].size,
        ]
    }
    pub(super) fn client_visibility(&self, key: i64) -> i32 {
        if self.sets[0].contains(key) {
            2
        } else {
            1
        }
    }
    pub(super) fn needs_load(&self, key: i64) -> bool {
        self.get(1, key) == 0
    }
    pub(super) fn should_unload(&self, key: i64) -> bool {
        self.get(0, key) == 0
    }
}
/// Captured load status, not a second lookup after shouldBeSaved callbacks.
/// 0=pending; 1=done/no store; 2=store empty; 3=request load; 4=store entities.
pub(super) fn store_action(captured: i32, empty: bool) -> i32 {
    if captured == 1 {
        0
    } else if empty {
        if captured == 2 {
            2
        } else {
            1
        }
    } else if captured == 0 {
        3
    } else {
        4
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn nullable_defaults_and_primary_maps_keep_presence_and_loaded_order() {
        let mut r = Residency::default();
        assert_eq!(r.get(0, 0), -1);
        r.defaults = [0, 0];
        assert_eq!(r.get(0, 0), 0);
        r.maps[0].put(0, 1).unwrap();
        assert_eq!(r.get(0, 0), -1);
        assert_eq!(r.counts()[1], 1);
        r.maps[1].put(0, 4).unwrap();
        r.maps[1].put(-7, 3).unwrap();
        r.maps[1].put(23, 4).unwrap();
        r.loaded_keys();
        assert_eq!(r.output[0], 0);
        assert_eq!(r.output.len(), 2);
        assert!(r.output.contains(&23));
        r.maps[0].remove(0).unwrap();
        assert_eq!(r.get(0, 0), 0);
    }
    #[test]
    fn null_uuid_and_all_zero_uuid_are_distinct_and_duplicate_registration_is_atomic() {
        let mut r = Residency::default();
        assert!(r.known.insert(None));
        assert!(r.known.insert(Some((0, 0))));
        assert!(!r.known.insert(None));
        assert_eq!(r.counts()[0], 2);
        assert!(r.known.remove(&None));
        assert!(r.known.contains(&Some((0, 0))));
    }
    #[test]
    fn captured_store_status_and_residency_policy_preserve_null_and_pending_cases() {
        assert_eq!(store_action(1, false), 0);
        assert_eq!(store_action(1, true), 0);
        assert_eq!(store_action(0, true), 1);
        assert_eq!(store_action(2, true), 2);
        assert_eq!(store_action(-1, true), 1);
        assert_eq!(store_action(0, false), 3);
        assert_eq!(store_action(2, false), 4);
        assert_eq!(store_action(-1, false), 4);
        let mut r = Residency::default();
        r.defaults = [0, 0];
        assert!(r.needs_load(7));
        assert!(r.should_unload(7));
        assert_eq!(r.client_visibility(7), 1);
        r.sets[0].add(7);
        assert_eq!(r.client_visibility(7), 2);
    }
}

//! Retained world light-map snapshots, identities and sky metadata.
mod ffi;
mod registry;
use super::layers::Layer;
use std::collections::HashMap;
use std::sync::Arc;

const SHARDS: usize = 64;

/// Copy a root and only its touched shard on mutation. Snapshot creation just
/// retains the root; it never clones every section entry.
struct Paged<T: Clone> {
    root: Arc<[Arc<HashMap<i64, T>>; SHARDS]>,
}
impl<T: Clone> Clone for Paged<T> {
    fn clone(&self) -> Self {
        Self {
            root: Arc::clone(&self.root),
        }
    }
}
impl<T: Clone> Default for Paged<T> {
    fn default() -> Self {
        Self {
            root: Arc::new(std::array::from_fn(|_| Arc::new(HashMap::new()))),
        }
    }
}
impl<T: Clone> Paged<T> {
    fn shard(key: i64) -> usize {
        let mut bits = key as u64;
        bits ^= bits >> 33;
        bits = bits.wrapping_mul(0xff51afd7ed558ccd);
        bits ^= bits >> 33;
        bits as usize & (SHARDS - 1)
    }
    fn get(&self, key: i64) -> Option<&T> {
        self.root[Self::shard(key)].get(&key)
    }
    fn contains(&self, key: i64) -> bool {
        self.root[Self::shard(key)].contains_key(&key)
    }
    fn insert(&mut self, key: i64, value: T) -> Option<T> {
        let root = Arc::make_mut(&mut self.root);
        Arc::make_mut(&mut root[Self::shard(key)]).insert(key, value)
    }
    pub(super) fn remove(&mut self, key: i64) -> Option<T> {
        if !self.contains(key) {
            return None;
        }
        let root = Arc::make_mut(&mut self.root);
        Arc::make_mut(&mut root[Self::shard(key)]).remove(&key)
    }
}

pub(super) struct Entry {
    // CPU identity pin and typed layer owner survive cache and map snapshots.
    pub(super) id: u32,
    pub(super) layer: Option<Arc<Layer>>,
    pub(super) _pin: Option<registry::Pin>,
}

pub struct LightMap {
    sections: Paged<Option<Arc<Entry>>>,
    tops: Paged<i32>,
    pub(super) lowest_y: i32,
    pub(super) top_default: i32,
    cached_keys: [i64; 2],
    cached_layers: [Option<Arc<Entry>>; 2],
    pub(super) cache_enabled: bool,
}
impl LightMap {
    pub(super) fn new() -> Self {
        Self {
            sections: Paged::default(),
            tops: Paged::default(),
            lowest_y: i32::MAX,
            top_default: i32::MAX,
            cached_keys: [i64::MAX; 2],
            cached_layers: [None, None],
            cache_enabled: true,
        }
    }
    pub(super) fn snapshot(&self) -> Self {
        // Frozen's constructor resets both the two-entry cache and the sky
        // default return value, even when the source's default was customized.
        Self {
            sections: self.sections.clone(),
            tops: self.tops.clone(),
            lowest_y: self.lowest_y,
            top_default: self.lowest_y,
            cached_keys: [i64::MAX; 2],
            cached_layers: [None, None],
            cache_enabled: true,
        }
    }
    pub(super) fn keys(&self, tops: bool) -> Vec<i64> {
        if tops {
            self.tops
                .root
                .iter()
                .flat_map(|shard| shard.keys().copied())
                .collect()
        } else {
            self.sections
                .root
                .iter()
                .flat_map(|shard| shard.keys().copied())
                .collect()
        }
    }
    pub(super) fn count(&self, tops: bool) -> usize {
        if tops {
            self.tops.root.iter().map(|shard| shard.len()).sum()
        } else {
            self.sections.root.iter().map(|shard| shard.len()).sum()
        }
    }
    pub(super) fn contains(&self, key: i64) -> bool {
        self.sections.contains(key)
    }
    pub(super) fn raw(&self, key: i64) -> Option<Arc<Entry>> {
        self.sections.get(key).and_then(Clone::clone)
    }
    pub(super) fn get(&mut self, key: i64) -> Option<Arc<Entry>> {
        if self.cache_enabled {
            for i in 0..2 {
                if self.cached_keys[i] == key {
                    return self.cached_layers[i].clone();
                }
            }
        }
        let result = self.raw(key);
        if result.is_some() && self.cache_enabled {
            self.cached_keys[1] = self.cached_keys[0];
            self.cached_layers[1] = self.cached_layers[0].clone();
            self.cached_keys[0] = key;
            self.cached_layers[0] = result.clone();
        }
        result
    }
    pub(super) fn set(&mut self, key: i64, value: Option<Arc<Entry>>) {
        // Set/remove deliberately do not clear Frozen's cache.
        self.sections.insert(key, value);
    }
    pub(super) fn remove(&mut self, key: i64) -> Option<Arc<Entry>> {
        self.sections.remove(key).flatten()
    }
    pub(super) fn sample_light(
        &mut self,
        block: i64,
        sky: bool,
        updating: bool,
        light_on: bool,
    ) -> Option<i32> {
        let top = if sky {
            self.top(light_column(block))
        } else {
            0
        };
        sample_light(block, sky, updating, light_on, top, self.lowest_y, |key| {
            self.get(key)
        })
    }
    pub(super) fn sample_light_uncached(
        &self,
        block: i64,
        sky: bool,
        updating: bool,
        light_on: bool,
    ) -> Option<i32> {
        let top = if sky {
            self.top(light_column(block))
        } else {
            0
        };
        sample_light(block, sky, updating, light_on, top, self.lowest_y, |key| {
            self.raw(key)
        })
    }
    pub(super) fn clear_cache(&mut self) {
        self.cached_keys = [i64::MAX; 2];
        self.cached_layers = [None, None];
    }
    pub(super) fn top(&self, key: i64) -> i32 {
        self.tops.get(key).copied().unwrap_or(self.top_default)
    }
    pub(super) fn put_top(&mut self, key: i64, value: i32) -> i32 {
        self.tops.insert(key, value).unwrap_or(self.top_default)
    }
    pub(super) fn remove_top(&mut self, key: i64) -> i32 {
        self.tops.remove(key).unwrap_or(self.top_default)
    }
}

fn light_column(block: i64) -> i64 {
    use super::propagation::{block_x, block_z, section_pos};
    section_pos(block_x(block) >> 4, 0, block_z(block) >> 4)
}
/// None retains custom/escaped DataLayer callbacks in Java. Raw lazy defaults
/// are full i32 values, including -1; they cannot be used as a decline sentinel.
fn sample_light(
    block: i64,
    sky: bool,
    updating: bool,
    light_on: bool,
    top: i32,
    lowest: i32,
    mut lookup: impl FnMut(i64) -> Option<Arc<Entry>>,
) -> Option<i32> {
    use super::propagation::{block_x, block_y, block_z, section_pos};
    let x = block_x(block);
    let mut y = block_y(block);
    let z = block_z(block);
    let mut section_y = y >> 4;
    let mut key = section_pos(x >> 4, section_y, z >> 4);
    if sky {
        if top == lowest || section_y >= top {
            return Some(if updating && !light_on { 0 } else { 15 });
        }
        let mut entry = lookup(key);
        if entry.is_none() {
            // Frozen clears the block-relative Y when skipping missing layers.
            y &= !15;
            while entry.is_none() {
                section_y = section_y.wrapping_add(1);
                if section_y >= top {
                    return Some(15);
                }
                key = section_pos(x >> 4, section_y, z >> 4);
                entry = lookup(key);
            }
        }
        let entry = entry?;
        let layer = entry.layer.as_ref().filter(|layer| layer.is_valid())?;
        layer
            .view()
            .get(((y & 15) << 8) | ((z & 15) << 4) | (x & 15))
            .ok()
    } else {
        let Some(entry) = lookup(key) else {
            return Some(0);
        };
        let layer = entry.layer.as_ref().filter(|layer| layer.is_valid())?;
        layer
            .view()
            .get(((y & 15) << 8) | ((z & 15) << 4) | (x & 15))
            .ok()
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn actual_frozen_map_cache_identity_and_sky_defaults_match() {
        use std::io::Read;
        let mut raw = Vec::new();
        flate2::read::GzDecoder::new(include_bytes!("frozen-light-maps.bin.gz").as_slice())
            .read_to_end(&mut raw)
            .unwrap();
        let input = raw.as_slice();
        let mut at = 0;
        fn int(input: &[u8], at: &mut usize) -> i32 {
            let n = i32::from_be_bytes(input[*at..*at + 4].try_into().unwrap());
            *at += 4;
            n
        }
        fn long(input: &[u8], at: &mut usize) -> i64 {
            let n = i64::from_be_bytes(input[*at..*at + 8].try_into().unwrap());
            *at += 8;
            n
        }
        assert_eq!(int(input, &mut at), 0x4c4d5031);
        assert_eq!(int(input, &mut at), 4096);
        let mut maps = Vec::new();
        let mut entries: Vec<Arc<Entry>> = Vec::new();
        for row in 0..4096 {
            let kind = int(input, &mut at);
            if row == 0 || row == 2048 {
                maps = vec![LightMap::new()];
                entries = [3, 7, -9, 31]
                    .iter()
                    .enumerate()
                    .map(|(i, &value)| {
                        Arc::new(Entry {
                            id: i as u32 + 1,
                            layer: Some(Arc::new(Layer::new(value))),
                            _pin: None,
                        })
                    })
                    .collect();
            }
            assert_eq!(kind, if row < 2048 { 0 } else { 1 });
            let op = int(input, &mut at);
            let index = int(input, &mut at) as usize;
            let key = long(input, &mut at);
            let arg = int(input, &mut at);
            let arg2 = int(input, &mut at);
            let expected = int(input, &mut at);
            let expected_sample = int(input, &mut at);
            let expected_error = int(input, &mut at);
            let mut result = 0;
            let mut error = 0;
            let mut returned: Option<Arc<Entry>> = None;
            match op {
                0 => maps[index].set(
                    key,
                    if arg == 0 {
                        None
                    } else {
                        Some(Arc::clone(&entries[arg as usize - 1]))
                    },
                ),
                1 => {
                    returned = maps[index].get(key);
                    result = returned.as_ref().map_or(0, |e| e.id as i32);
                }
                2 => result = i32::from(maps[index].sections.contains(key)),
                3 => {
                    returned = maps[index].remove(key);
                    result = returned.as_ref().map_or(0, |e| e.id as i32);
                }
                4 => {
                    let snapshot = maps[index].snapshot();
                    maps.push(snapshot);
                    result = maps.len() as i32 - 1;
                }
                5 => maps[index].clear_cache(),
                6 => maps[index].cache_enabled = false,
                7 => {
                    if let Some(source) = maps[index].raw(key) {
                        let copy = Arc::new(Entry {
                            id: entries.len() as u32 + 1,
                            layer: Some(Arc::new(source.layer.as_ref().unwrap().copy())),
                            _pin: None,
                        });
                        maps[index].set(key, Some(Arc::clone(&copy)));
                        maps[index].clear_cache();
                        entries.push(Arc::clone(&copy));
                        result = copy.id as i32;
                        returned = Some(copy);
                    } else {
                        error = 1;
                    }
                }
                8 => {
                    entries[arg as usize - 1].layer.as_ref().unwrap().fill(arg2);
                    result = entries[arg as usize - 1]
                        .layer
                        .as_ref()
                        .unwrap()
                        .view()
                        .get(0)
                        .unwrap();
                }
                9 => result = maps[index].top(key),
                10 => result = maps[index].put_top(key, arg),
                11 => result = maps[index].remove_top(key),
                12 => {
                    maps[index].lowest_y = arg;
                    maps[index].top_default = arg;
                }
                13 => maps[index].top_default = arg,
                _ => panic!("Unknown Frozen operation {op}"),
            }
            let sample = returned
                .as_ref()
                .map_or(0, |e| e.layer.as_ref().unwrap().view().get(0).unwrap());
            assert_eq!(
                (result, sample, error),
                (expected, expected_sample, expected_error),
                "Frozen row {row}, operation {op}, key {key}"
            );
        }
        assert_eq!(at, input.len());
    }

    #[test]
    fn actual_frozen_scalar_consumers_match_retained_light_owners() {
        use super::super::propagation::{block_x, block_y, block_z, section_pos};
        use std::io::Read;
        let mut raw = Vec::new();
        flate2::read::GzDecoder::new(include_bytes!("frozen-light-samples.bin.gz").as_slice())
            .read_to_end(&mut raw)
            .unwrap();
        let mut at = 0;
        fn int(raw: &[u8], at: &mut usize) -> i32 {
            let value = i32::from_be_bytes(raw[*at..*at + 4].try_into().unwrap());
            *at += 4;
            value
        }
        assert_eq!(int(&raw, &mut at), 0x4c535031);
        assert_eq!(int(&raw, &mut at), 1024);
        for row in 0..1024 {
            let kind = int(&raw, &mut at);
            let mode = int(&raw, &mut at);
            let block = i64::from_be_bytes(raw[at..at + 8].try_into().unwrap());
            at += 8;
            let initial = int(&raw, &mut at);
            let allocated = int(&raw, &mut at) != 0;
            let updating = int(&raw, &mut at) != 0;
            let enabled = int(&raw, &mut at) != 0;
            let expected = int(&raw, &mut at);
            let x = block_x(block) >> 4;
            let y = block_y(block) >> 4;
            let z = block_z(block) >> 4;
            let key = section_pos(x, y, z);
            let layer = if allocated {
                Layer::from_bytes(std::array::from_fn(|j| (j * 31 + row) as u8))
            } else {
                Layer::new(initial)
            };
            let entry = Arc::new(Entry {
                id: 1,
                layer: Some(Arc::new(layer)),
                _pin: None,
            });
            let mut source = LightMap::new();
            if kind == 0 {
                if mode != 0 {
                    source.set(key, Some(entry));
                }
            } else {
                source.lowest_y = y - 4;
                source.top_default = y - 4;
                let top = if mode == 0 {
                    y - 4
                } else if mode == 1 {
                    y
                } else {
                    y + if mode == 2 { 1 } else { 5 }
                };
                source.put_top(section_pos(x, 0, z), top);
                if mode == 2 {
                    source.set(key, Some(entry));
                } else if mode == 3 || mode == 5 {
                    source.set(
                        section_pos(x, y + if mode == 3 { 2 } else { 3 }, z),
                        Some(entry),
                    );
                    if mode == 5 {
                        source.set(key, None);
                    }
                }
            }
            if kind == 0 || !updating {
                source = source.snapshot();
                source.cache_enabled = false;
            }
            let result = if source.cache_enabled {
                source.sample_light(block, kind != 0, updating, enabled)
            } else {
                source.sample_light_uncached(block, kind != 0, updating, enabled)
            };
            assert_eq!(result, Some(expected), "Actual Frozen scalar row {row}");
        }
        assert_eq!(at, raw.len());
    }

    #[test]
    fn snapshots_share_roots_and_detach_only_the_changed_shard() {
        let mut source = LightMap::new();
        for key in 0..4096 {
            source.set(key, None);
        }
        let snapshot = source.snapshot();
        assert!(Arc::ptr_eq(&source.sections.root, &snapshot.sections.root));
        source.set(
            9,
            Some(Arc::new(Entry {
                id: 1,
                layer: Some(Arc::new(Layer::new(7))),
                _pin: None,
            })),
        );
        assert!(!Arc::ptr_eq(&source.sections.root, &snapshot.sections.root));
        for shard in 0..SHARDS {
            assert_eq!(
                Arc::ptr_eq(&source.sections.root[shard], &snapshot.sections.root[shard]),
                shard != Paged::<Option<Arc<Entry>>>::shard(9)
            );
        }
        assert!(snapshot.raw(9).is_none());
        assert_eq!(
            source.raw(9).unwrap().layer.as_ref().unwrap().view().get(0),
            Ok(7)
        );
    }

    #[test]
    fn cached_identity_lives_until_cache_and_snapshot_retirement() {
        let mut source = LightMap::new();
        let entry = Arc::new(Entry {
            id: 1,
            layer: Some(Arc::new(Layer::new(4))),
            _pin: None,
        });
        let weak = Arc::downgrade(&entry);
        let view = entry.layer.as_ref().unwrap().view();
        source.set(7, Some(entry));
        source.get(7);
        let snapshot = source.snapshot();
        source.remove(7);
        assert!(weak.upgrade().is_some());
        source.clear_cache();
        assert!(weak.upgrade().is_some());
        drop(snapshot);
        assert!(weak.upgrade().is_none());
        assert_eq!(view.get(0), Ok(4));
    }
}

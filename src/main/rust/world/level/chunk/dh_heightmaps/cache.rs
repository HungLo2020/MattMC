//! Bounded reuse of immutable fields. Cache keys retain only weak storage identity;
//! scans never hold the cache lock, and input lifetime is confined to the call.
use super::*;
use std::collections::VecDeque;
const LIMIT: usize = 64;
struct Key {
    min_y: i32,
    sections: Vec<Option<live::ReadStamp>>,
}
impl Key {
    fn capture(sections: Sections<'_>, min_y: i32, empty: &[bool]) -> Option<Self> {
        let mut stamps = Vec::with_capacity(sections.len());
        for (index, &empty) in empty.iter().enumerate() {
            let storage = sections.block(index);
            // Stale-empty counters intentionally mask raw block contents in Frozen.
            stamps.push(if empty { None } else { Some(storage.read_stamp()?) });
        }
        Some(Self { min_y, sections: stamps })
    }
    fn matches(&self, other: &Self) -> bool {
        self.min_y == other.min_y && self.sections.len() == other.sections.len()
            && self.sections.iter().zip(&other.sections).all(|(a,b)| match (a,b) {
                (None, None) => true, (Some(a), Some(b)) => a.matches(b), _ => false,
            })
    }
    fn alive(&self) -> bool { self.sections.iter().flatten().all(live::ReadStamp::alive) }
}
struct Entry { key: Key, fields: Arc<Heightmaps> }
pub(super) struct Cache<'a> {
    catalog: &'a Catalog,
    entries: VecDeque<Entry>,
}
impl<'a> Cache<'a> {
    pub(super) fn new(catalog: &'a Catalog) -> Self { Self { catalog, entries: VecDeque::new() } }
    fn get(&mut self, key: &Key) -> Option<Arc<Heightmaps>> {
        let index = self.entries.iter().position(|entry| entry.key.matches(key))?;
        let entry = self.entries.remove(index).unwrap();
        let fields = entry.fields.clone();
        self.entries.push_back(entry);
        Some(fields)
    }
    fn insert(&mut self, key: Key, fields: Arc<Heightmaps>) {
        self.entries.retain(|entry| entry.key.alive() && !entry.key.matches(&key));
        if self.entries.len() == LIMIT { self.entries.pop_front(); }
        self.entries.push_back(Entry { key, fields });
    }
}
pub(super) fn build(sections: &[(&live::Owner, &counters::Owner)], min_y: i32,
    cache: &Mutex<Cache<'_>>) -> Option<Arc<Heightmaps>> {
    build_sections(Sections::Pairs(sections), min_y, cache)
}
pub(super) fn build_sections(sections: Sections<'_>, min_y: i32, cache: &Mutex<Cache<'_>>) -> Option<Arc<Heightmaps>> {
    if sections.len() == 0 || sections.len() > 256 || min_y % 16 != 0 || min_y.unsigned_abs() > 1_000_000 { return None; }
    let empty: Vec<_> = empty_mask(sections);
    let key = Key::capture(sections, min_y, &empty);
    let catalog = {
        let mut cache = cache.lock().unwrap();
        if let Some(key) = &key {
            if let Some(fields) = cache.get(key) { return Some(fields); }
        }
        cache.catalog
    };
    // Use precisely the sampled mask that belongs to the key. Independently
    // resampling counters for the scan would allow counter ABA to poison reuse.
    let fields: Arc<Heightmaps> = Arc::from(build_with_empty(sections, min_y, catalog, &empty)?);
    if let Some(key) = key {
        let after_empty: Vec<_> = empty_mask(sections);
        if let Some(after) = Key::capture(sections, min_y, &after_empty) {
            // Ordinary exclusion still applies. A concurrent mutation may produce
            // the original mixed section-local result, but cannot cache that result.
            if key.matches(&after) { cache.lock().unwrap().insert(key, fields.clone()); }
        }
    }
    Some(fields)
}

#[cfg(test)]
mod tests;

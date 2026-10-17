//! `java.util.LinkedHashMap` orderings keyed by DH column keys. Iteration
//! order is observable in the collector (publication budget, retirement
//! lists, LRU eviction), so these reproduce Java's exact rules.
use std::collections::{BTreeMap, HashMap};

/// A map in insertion order, or in access order when `access_order` is set.
/// Insertion order: re-putting a key keeps its position. Access order:
/// [`OrderedMap::get_touch`] and every put move the key to the end.
#[derive(Clone, Debug, Default)]
pub(crate) struct OrderedMap<V> {
    entries: HashMap<i64, (u64, V)>,
    order: BTreeMap<u64, i64>,
    next: u64,
    access_order: bool,
}

impl<V> OrderedMap<V> {
    pub(crate) fn new() -> Self {
        Self { entries: HashMap::new(), order: BTreeMap::new(), next: 0, access_order: false }
    }

    pub(crate) fn access_ordered() -> Self {
        Self { access_order: true, ..Self::new() }
    }

    pub(crate) fn len(&self) -> usize {
        self.entries.len()
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.entries.is_empty()
    }

    pub(crate) fn contains_key(&self, key: i64) -> bool {
        self.entries.contains_key(&key)
    }

    /// A read that does not count as an access (`containsKey`, iteration).
    pub(crate) fn peek(&self, key: i64) -> Option<&V> {
        self.entries.get(&key).map(|(_, v)| v)
    }

    /// `get`: in access order this moves the key to the end.
    pub(crate) fn get_touch(&mut self, key: i64) -> Option<&V> {
        if self.access_order && self.entries.contains_key(&key) {
            self.move_to_end(key);
        }
        self.peek(key)
    }

    fn move_to_end(&mut self, key: i64) {
        let seq = self.next;
        self.next += 1;
        let entry = self.entries.get_mut(&key).expect("present");
        self.order.remove(&entry.0);
        entry.0 = seq;
        self.order.insert(seq, key);
    }

    /// `put`: returns the previous value.
    pub(crate) fn put(&mut self, key: i64, value: V) -> Option<V> {
        if let Some(entry) = self.entries.get_mut(&key) {
            let previous = std::mem::replace(&mut entry.1, value);
            if self.access_order {
                self.move_to_end(key);
            }
            return Some(previous);
        }
        let seq = self.next;
        self.next += 1;
        self.entries.insert(key, (seq, value));
        self.order.insert(seq, key);
        None
    }

    pub(crate) fn remove(&mut self, key: i64) -> Option<V> {
        let (seq, value) = self.entries.remove(&key)?;
        self.order.remove(&seq);
        Some(value)
    }

    pub(crate) fn clear(&mut self) {
        self.entries.clear();
        self.order.clear();
    }

    /// Keys in iteration order.
    pub(crate) fn keys(&self) -> impl Iterator<Item = i64> + '_ {
        self.order.values().copied()
    }

    /// Entries in iteration order.
    pub(crate) fn iter(&self) -> impl Iterator<Item = (i64, &V)> + '_ {
        self.order.values().map(|key| (*key, &self.entries[key].1))
    }

    pub(crate) fn values(&self) -> impl Iterator<Item = &V> + '_ {
        self.iter().map(|(_, v)| v)
    }
}

impl<V: Clone> OrderedMap<V> {
    /// `clear()` then `putAll(other)`: the same entries in `other`'s order.
    pub(crate) fn replace_with(&mut self, other: &OrderedMap<V>) {
        self.clear();
        for (key, value) in other.iter() {
            self.put(key, value.clone());
        }
    }
}

/// A `LinkedHashSet<Long>`: insertion order, re-adding keeps the position.
#[derive(Clone, Debug, Default)]
pub(crate) struct OrderedSet {
    map: OrderedMap<()>,
}

impl OrderedSet {
    pub(crate) fn new() -> Self {
        Self { map: OrderedMap::new() }
    }

    pub(crate) fn len(&self) -> usize {
        self.map.len()
    }

    pub(crate) fn is_empty(&self) -> bool {
        self.map.is_empty()
    }

    pub(crate) fn contains(&self, key: i64) -> bool {
        self.map.contains_key(key)
    }

    /// Returns whether the key was added.
    pub(crate) fn insert(&mut self, key: i64) -> bool {
        self.map.put(key, ()).is_none()
    }

    pub(crate) fn remove(&mut self, key: i64) -> bool {
        self.map.remove(key).is_some()
    }

    pub(crate) fn clear(&mut self) {
        self.map.clear();
    }

    pub(crate) fn iter(&self) -> impl Iterator<Item = i64> + '_ {
        self.map.keys()
    }
}

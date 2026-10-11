//! Sound asset cache and preload policy (`SoundBufferLibrary`).
//!
//! Rust owns the cache, its reload generation and the preload set; pack IO
//! stays with Java's resource layer. A missing path becomes a load request that
//! Java drains, reads off-thread and answers with [`AssetCache::provide`] or
//! [`AssetCache::fail`]. Answers for an older generation are rejected, so a
//! read that outlives a reload cannot publish a stale asset.

use std::collections::HashMap;

#[derive(Debug)]
enum Entry {
    /// Requested; the listed voices wait for it.
    Loading(Vec<u64>),
    /// The backend asset handle.
    Ready(u64),
    /// The read or registration failed; later plays fail immediately.
    Failed,
}

/// What a voice needs to play `path`.
#[derive(Debug, PartialEq, Eq)]
pub(crate) enum AssetLookup {
    Ready(u64),
    Pending,
    Failed,
}

#[derive(Debug)]
pub(crate) struct AssetCache {
    generation: u64,
    entries: HashMap<String, Entry>,
    requests: Vec<String>,
}

impl Default for AssetCache {
    fn default() -> Self {
        Self { generation: 1, entries: HashMap::new(), requests: Vec::new() }
    }
}

impl AssetCache {
    pub(crate) fn generation(&self) -> u64 {
        self.generation
    }

    /// Looks up `path` for `voice`, requesting a load the first time.
    pub(crate) fn acquire(&mut self, path: &str, voice: u64) -> AssetLookup {
        match self.entries.get_mut(path) {
            Some(Entry::Ready(handle)) => AssetLookup::Ready(*handle),
            Some(Entry::Failed) => AssetLookup::Failed,
            Some(Entry::Loading(waiting)) => {
                waiting.push(voice);
                AssetLookup::Pending
            }
            None => {
                self.entries.insert(path.to_owned(), Entry::Loading(vec![voice]));
                self.requests.push(path.to_owned());
                AssetLookup::Pending
            }
        }
    }

    /// Requests `path` with no waiting voice (preload).
    pub(crate) fn preload(&mut self, path: &str) {
        if !self.entries.contains_key(path) {
            self.entries.insert(path.to_owned(), Entry::Loading(Vec::new()));
            self.requests.push(path.to_owned());
        }
    }

    /// Returns a taken request that could not be delivered.
    pub(crate) fn requeue(&mut self, path: &str) {
        if matches!(self.entries.get(path), Some(Entry::Loading(_))) {
            self.requests.push(path.to_owned());
        }
    }

    pub(crate) fn has_requests(&self) -> bool {
        !self.requests.is_empty()
    }

    pub(crate) fn take_requests(&mut self) -> Vec<String> {
        std::mem::take(&mut self.requests)
    }

    /// Whether an answer for (`path`, `generation`) is still wanted.
    pub(crate) fn awaits(&self, path: &str, generation: u64) -> bool {
        generation == self.generation && matches!(self.entries.get(path), Some(Entry::Loading(_)))
    }

    /// Publishes a loaded asset; returns the voices that waited for it, or
    /// `None` when the answer is stale (the caller destroys `handle`).
    pub(crate) fn provide(&mut self, path: &str, generation: u64, handle: u64) -> Option<Vec<u64>> {
        if !self.awaits(path, generation) {
            return None;
        }
        match self.entries.insert(path.to_owned(), Entry::Ready(handle)) {
            Some(Entry::Loading(waiting)) => Some(waiting),
            _ => Some(Vec::new()),
        }
    }

    /// Records a failed load; returns the voices that waited for it.
    pub(crate) fn fail(&mut self, path: &str, generation: u64) -> Vec<u64> {
        if !self.awaits(path, generation) {
            return Vec::new();
        }
        match self.entries.insert(path.to_owned(), Entry::Failed) {
            Some(Entry::Loading(waiting)) => waiting,
            _ => Vec::new(),
        }
    }

    /// Forgets every asset (`SoundBufferLibrary.clear`) and starts a new
    /// generation; returns the previous one, whose backend assets the caller
    /// destroys.
    pub(crate) fn clear(&mut self) -> u64 {
        let previous = self.generation;
        self.generation += 1;
        self.entries.clear();
        self.requests.clear();
        previous
    }
}

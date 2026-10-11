//! Registry identity snapshots: each registry's elements by network ID, the
//! known pack each element came from, and its tags as ID lists.
//!
//! Java owns registry storage (its element values are gameplay objects) and
//! publishes a snapshot whenever a registry set freezes or its tags change;
//! each publish is a new generation for its context (the server's or the
//! client's registries) and retires the previous one, so a query against a
//! retired generation fails instead of answering with reused IDs. Rust then
//! answers identity questions and makes the registry synchronization
//! decisions: which elements a client already knows from its packs, and the
//! tag ID lists sent over the network.

pub mod ffi;

use std::collections::HashMap;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, RwLock};

use crate::content::tags::Location;

/// `KnownPack(namespace, id, version)`.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub(crate) struct KnownPack {
    pub(crate) namespace: Arc<str>,
    pub(crate) id: Arc<str>,
    pub(crate) version: Arc<str>,
}

/// One registry's elements: fixed once its set freezes, shared by every
/// generation that only rebinds tags.
#[derive(Debug, Default)]
pub(crate) struct Elements {
    /// Element locations, indexed by network ID.
    pub(crate) locations: Vec<Location>,
    index: HashMap<Location, u32>,
    /// Each element's known pack (index into the snapshot's packs).
    known: Vec<Option<u32>>,
}

impl Elements {
    pub(crate) fn push(&mut self, element: Location, known: Option<u32>) {
        self.index.insert(element.clone(), self.locations.len() as u32);
        self.locations.push(element);
        self.known.push(known);
    }
}

/// One registry's identity.
#[derive(Debug, Default)]
pub(crate) struct Table {
    pub(crate) key: Option<Location>,
    pub(crate) elements: Arc<Elements>,
    /// Tags in Java's iteration order, each with its members' IDs.
    pub(crate) tags: Arc<Tags>,
}

/// One registry's tags, each with its members' IDs (shared with the
/// generations that left the tag unchanged).
pub(crate) type Tags = Vec<(Location, Arc<[u32]>)>;

impl Table {
    pub(crate) fn new(key: Location, elements: Elements, tags: Tags) -> Table {
        Table { key: Some(key), elements: Arc::new(elements), tags: Arc::new(tags) }
    }

    pub(crate) fn id(&self, element: &Location) -> Option<u32> {
        self.elements.index.get(element).copied()
    }
}

/// One published set of registries.
#[derive(Debug, Default)]
pub(crate) struct Snapshot {
    pub(crate) generation: u64,
    pub(crate) packs: Vec<KnownPack>,
    tables: Vec<Table>,
    by_key: HashMap<Location, usize>,
}

impl Snapshot {
    pub(crate) fn new(packs: Vec<KnownPack>, tables: Vec<Table>) -> Snapshot {
        let by_key = tables
            .iter()
            .enumerate()
            .filter_map(|(index, table)| table.key.clone().map(|key| (key, index)))
            .collect();
        Snapshot { generation: 0, packs, tables, by_key }
    }

    pub(crate) fn table(&self, key: &Location) -> Option<&Table> {
        self.by_key.get(key).map(|&index| &self.tables[index])
    }

    /// `RegistrySynchronization.packRegistry`'s check: element IDs whose
    /// known pack is one of `known` (the client has them; only their keys
    /// are sent).
    pub(crate) fn known_elements(&self, key: &Location, known: &[KnownPack]) -> Option<Vec<bool>> {
        let table = self.table(key)?;
        let wanted: Vec<bool> = self.packs.iter().map(|pack| known.contains(pack)).collect();
        Some(table.elements.known.iter().map(|pack| pack.is_some_and(|pack| wanted[pack as usize])).collect())
    }

    /// This snapshot with new tags for the registries that rebound them
    /// (`(index, tags)`): elements, IDs and other registries' tags carry over.
    pub(crate) fn retagged(&self, changes: Vec<(usize, Tags)>) -> Option<Snapshot> {
        let mut tables: Vec<Table> = self
            .tables
            .iter()
            .map(|table| Table { key: table.key.clone(), elements: Arc::clone(&table.elements), tags: Arc::clone(&table.tags) })
            .collect();
        for (index, tags) in changes {
            tables.get_mut(index)?.tags = Arc::new(tags);
        }
        Some(Snapshot::new(self.packs.clone(), tables))
    }

    /// A registry by publish order.
    pub(crate) fn table_at(&self, index: usize) -> Option<&Table> {
        self.tables.get(index)
    }

    /// `known_elements` for the registry at `index`.
    pub(crate) fn known_elements_at(&self, index: usize, known: &[KnownPack]) -> Option<Vec<bool>> {
        let key = self.tables.get(index)?.key.clone()?;
        self.known_elements(&key, known)
    }

}

static NEXT_GENERATION: AtomicU64 = AtomicU64::new(1);
static PUBLISHED: RwLock<Vec<Option<Arc<Snapshot>>>> = RwLock::new(Vec::new());

/// Publishes `snapshot` for `context`, retiring the previous generation.
pub(crate) fn publish(context: u32, mut snapshot: Snapshot) -> u64 {
    let generation = NEXT_GENERATION.fetch_add(1, Ordering::Relaxed);
    snapshot.generation = generation;
    let mut published = PUBLISHED.write().unwrap_or_else(|poisoned| poisoned.into_inner());
    let slot = context as usize;
    if published.len() <= slot {
        published.resize(slot + 1, None);
    }
    published[slot] = Some(Arc::new(snapshot));
    generation
}

/// Drops `context`'s snapshot.
pub(crate) fn release(context: u32) {
    let mut published = PUBLISHED.write().unwrap_or_else(|poisoned| poisoned.into_inner());
    if let Some(slot) = published.get_mut(context as usize) {
        *slot = None;
    }
}

/// The snapshot of `generation` for `context`, if it is still current.
pub(crate) fn current(context: u32, generation: u64) -> Option<Arc<Snapshot>> {
    let published = PUBLISHED.read().unwrap_or_else(|poisoned| poisoned.into_inner());
    published
        .get(context as usize)
        .and_then(Option::as_ref)
        .filter(|snapshot| snapshot.generation == generation)
        .cloned()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn location(path: &str) -> Location {
        Location::new("minecraft", path)
    }

    #[test]
    fn snapshots_answer_identity_and_retire_old_generations() {
        let vanilla = KnownPack { namespace: Arc::from("minecraft"), id: Arc::from("core"), version: Arc::from("1.21.10") };
        let mut elements = Elements::default();
        elements.push(location("plains"), Some(0));
        elements.push(location("custom"), None);
        let biomes = Table::new(location("worldgen/biome"), elements, vec![(location("is_overworld"), Arc::from([0, 1]))]);
        let generation = publish(7, Snapshot::new(vec![vanilla.clone()], vec![biomes]));
        let snapshot = current(7, generation).unwrap();
        let table = snapshot.table(&location("worldgen/biome")).unwrap();
        assert_eq!(Some(1), table.id(&location("custom")));
        assert_eq!(location("plains"), table.elements.locations[0]);
        assert_eq!(Some(vec![true, false]), snapshot.known_elements(&location("worldgen/biome"), &[vanilla.clone()]));
        assert_eq!(Some(vec![false, false]), snapshot.known_elements(&location("worldgen/biome"), &[]));
        let retagged = snapshot.retagged(vec![(0, vec![(location("is_overworld"), Arc::from([1]))])]).unwrap();
        assert!(snapshot.retagged(vec![(1, Vec::new())]).is_none(), "a retag names published registries");
        assert_eq!(Some(vec![true, false]), snapshot.known_elements_at(0, &[vanilla.clone()]));
        let newer = publish(7, retagged);
        assert!(current(7, generation).is_none(), "the old generation is retired");
        let newer = current(7, newer).unwrap();
        let table = newer.table(&location("worldgen/biome")).unwrap();
        assert_eq!(Some(1), table.id(&location("custom")), "elements carry over");
        assert_eq!(vec![(location("is_overworld"), Arc::<[u32]>::from([1]))], *table.tags);
        assert_eq!(Some(vec![true, false]), newer.known_elements_at(0, &[vanilla]));
    }
}

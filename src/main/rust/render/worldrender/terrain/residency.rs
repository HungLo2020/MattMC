//! Static-terrain mesh residency: for each terrain mesh key, the registered
//! and acknowledged generation (with its texture ids), the generation the
//! last accepted upload carried, and the registered asset still waiting for
//! upload, with its place in the world-mesh upload order.
//!
//! The upload order is shared with Java's other world meshes
//! (`RustGalWorldPrimitiveRenderer`): every mesh that becomes dirty takes the
//! next number of one Java counter, and the batch merges both queues by it.
//! A dirty mesh keeps its number until it is uploaded or removed. Every call
//! runs under Java's world-primitive lock.

use std::collections::{BTreeMap, HashMap};
use std::sync::{LazyLock, Mutex, MutexGuard};

use super::staging::StagedAsset;

/// A generation and the textures its sections draw with.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Residency {
    pub generation: u64,
    pub texture_ids: Vec<i32>,
}

/// A registered asset waiting for its upload to be acknowledged.
#[derive(Debug)]
pub struct Pending {
    pub generation: u64,
    pub payload_bytes: u64,
    pub asset: StagedAsset,
}

#[derive(Debug, Default)]
struct Mesh {
    registered: Option<Residency>,
    acknowledged: Option<Residency>,
    uploaded: Option<u64>,
    pending: Option<Pending>,
}

/// One upload candidate: the dirty sequence (FIFO entries), key, generation
/// and payload bytes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Candidate {
    /// `None` for an entry chosen by a frame's per-record terrain instance.
    pub sequence: Option<u64>,
    pub key: u64,
    pub generation: u64,
    pub payload_bytes: u64,
}

/// What [`TerrainResidency::accepted`] found.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Accepted {
    /// Not a terrain mesh: Java's generic world-mesh rule applies.
    NotTerrain,
    Rejected,
    /// The generation was uploaded; its textures must be too.
    Uploaded(Vec<i32>),
}

#[derive(Debug, Default)]
pub struct TerrainResidency {
    meshes: HashMap<u64, Mesh>,
    /// Dirty sequence to key, and key to sequence.
    dirty: BTreeMap<u64, u64>,
    dirty_sequence: HashMap<u64, u64>,
    residency_count: usize,
    pending_count: usize,
}

impl TerrainResidency {
    /// The generation of the asset waiting for upload, if any.
    pub fn pending_generation(&self, key: u64) -> Option<u64> {
        self.meshes.get(&key)?.pending.as_ref().map(|pending| pending.generation)
    }

    /// `registerStaticTerrainMeshAsset` after Java's validation, capacity and
    /// texture registration. An identical rebuild (the waiting asset has this
    /// generation) changes only the registered residency. Returns whether it
    /// was identical; `asset` is the newly staged geometry and `sequence` the
    /// next dirty number (used only if the mesh is not dirty yet).
    pub fn register(&mut self, key: u64, residency: Residency, asset: Option<Pending>, sequence: u64) -> bool {
        let mesh = self.meshes.entry(key).or_default();
        let same = mesh.pending.as_ref().is_some_and(|pending| pending.generation == residency.generation);
        if mesh.registered.is_none() {
            self.residency_count += 1;
        }
        mesh.registered = Some(residency);
        if same {
            return true;
        }
        let Some(asset) = asset else {
            return false;
        };
        if mesh.pending.is_none() {
            self.pending_count += 1;
        }
        mesh.pending = Some(asset);
        if !self.dirty_sequence.contains_key(&key) {
            self.dirty_sequence.insert(key, sequence);
            self.dirty.insert(sequence, key);
        }
        false
    }

    /// Whether an identical rebuild's staged copy will never be read: its
    /// generation was uploaded and nothing is waiting.
    pub fn uploaded_and_clean(&self, key: u64, generation: u64) -> bool {
        self.meshes.get(&key).is_some_and(|mesh| mesh.uploaded == Some(generation)) && !self.dirty_sequence.contains_key(&key)
    }

    /// `dirtyWorldMeshAssetsLocked`'s terrain part: the dirty assets of the
    /// frame's per-record terrain instances, in instance order and without
    /// repeats, then (up to `limit` more) the dirty queue in order, skipping
    /// protected generations. The queue may repeat an instance's asset: Java
    /// merges it with its own queue and stops at the first asset over budget,
    /// as one queue would. `protected` maps keys to the one generation a
    /// frozen frame may still draw.
    pub fn candidates(&self, instances: &[(u64, u64)], protected: &HashMap<u64, u64>, limit: usize) -> Vec<Candidate> {
        let may_publish = |key: u64, generation: u64| protected.get(&key).is_none_or(|kept| *kept == generation);
        let mut chosen = Vec::new();
        let mut taken = std::collections::HashSet::new();
        for &(key, generation) in instances {
            if chosen.len() == limit {
                break;
            }
            if !self.dirty_sequence.contains_key(&key) || taken.contains(&key) {
                continue;
            }
            let Some(pending) = self.meshes.get(&key).and_then(|mesh| mesh.pending.as_ref()) else { continue };
            if pending.generation != generation || !may_publish(key, generation) {
                continue;
            }
            taken.insert(key);
            chosen.push(Candidate { sequence: None, key, generation, payload_bytes: pending.payload_bytes });
        }
        let instance_count = chosen.len();
        for (&sequence, &key) in &self.dirty {
            if chosen.len() == instance_count + limit {
                break;
            }
            let Some(pending) = self.meshes.get(&key).and_then(|mesh| mesh.pending.as_ref()) else { continue };
            if !may_publish(key, pending.generation) {
                continue;
            }
            chosen.push(Candidate { sequence: Some(sequence), key, generation: pending.generation, payload_bytes: pending.payload_bytes });
        }
        chosen
    }

    /// The waiting asset of exactly this generation, for the update.
    pub fn pending_asset(&self, key: u64, generation: u64) -> Option<&StagedAsset> {
        let pending = self.meshes.get(&key)?.pending.as_ref()?;
        (pending.generation == generation).then_some(&pending.asset)
    }

    /// The update carrying these (key, generation) assets was accepted: each
    /// records its uploaded generation, the matching residency becomes the
    /// acknowledged one, it leaves the dirty queue, and its geometry is
    /// dropped once the registered generation is the one uploaded. Returns,
    /// per asset, whether that registered generation was the one uploaded
    /// (Java then releases its own copy).
    pub fn acknowledge(&mut self, uploaded: &[(u64, u64)]) -> Vec<bool> {
        let mut released = Vec::with_capacity(uploaded.len());
        for &(key, generation) in uploaded {
            let Some(mesh) = self.meshes.get_mut(&key) else {
                released.push(false);
                continue;
            };
            mesh.uploaded = Some(generation);
            let registered = mesh.registered.as_ref().filter(|registered| registered.generation == generation);
            released.push(registered.is_some());
            if let Some(registered) = registered {
                mesh.acknowledged = Some(registered.clone());
                if mesh.pending.as_ref().is_some_and(|pending| pending.generation == generation) {
                    mesh.pending = None;
                    self.pending_count -= 1;
                }
            }
            if let Some(sequence) = self.dirty_sequence.remove(&key) {
                self.dirty.remove(&sequence);
            }
        }
        released
    }

    /// `removeStaticTerrainMeshAsset`: withdraws the mesh. Returns the
    /// generation to retire (the uploaded one, else the waiting or registered
    /// one), or `None` when the key was neither registered nor waiting.
    pub fn remove(&mut self, key: u64) -> Option<u64> {
        let mesh = self.meshes.remove(&key)?;
        if let Some(sequence) = self.dirty_sequence.remove(&key) {
            self.dirty.remove(&sequence);
        }
        if mesh.registered.is_some() {
            self.residency_count -= 1;
        }
        if mesh.pending.is_some() {
            self.pending_count -= 1;
        }
        let waiting = mesh.pending.as_ref().map(|pending| pending.generation);
        let registered = mesh.registered.as_ref().map(|registered| registered.generation);
        if waiting.is_none() && registered.is_none() {
            return None;
        }
        mesh.uploaded.or(waiting).or(registered)
    }

    /// The terrain half of `isWorldMeshGenerationAndTexturesUploadedLocked`.
    pub fn accepted(&self, key: u64, generation: u64) -> Accepted {
        let Some(mesh) = self.meshes.get(&key) else { return Accepted::NotTerrain };
        let (registered, acknowledged) = (mesh.registered.as_ref(), mesh.acknowledged.as_ref());
        if registered.is_none() && acknowledged.is_none() {
            return Accepted::NotTerrain;
        }
        if mesh.uploaded != Some(generation) {
            return Accepted::Rejected;
        }
        match acknowledged.filter(|r| r.generation == generation).or(registered.filter(|r| r.generation == generation)) {
            Some(residency) => Accepted::Uploaded(residency.texture_ids.clone()),
            None => Accepted::Rejected,
        }
    }

    /// Whether `generation` is the waiting asset's, or else the registered
    /// one's (the per-record terrain enqueue's admission).
    pub fn generation_matches(&self, key: u64, generation: u64) -> bool {
        let Some(mesh) = self.meshes.get(&key) else { return false };
        match &mesh.pending {
            Some(pending) => pending.generation == generation,
            None => mesh.registered.as_ref().is_some_and(|registered| registered.generation == generation),
        }
    }

    /// Whether the key is a terrain mesh (registered or waiting).
    pub fn contains(&self, key: u64) -> bool {
        self.meshes.get(&key).is_some_and(|mesh| mesh.registered.is_some() || mesh.pending.is_some())
    }

    /// Registered residencies (Java's `STATIC_TERRAIN_MESH_RESIDENCY` size).
    pub fn residency_count(&self) -> usize {
        self.residency_count
    }

    /// Assets waiting for upload (terrain's share of `WORLD_MESH_ASSETS`).
    pub fn pending_count(&self) -> usize {
        self.pending_count
    }

    pub fn dirty_count(&self) -> usize {
        self.dirty.len()
    }

    /// Upload bytes of the dirty assets.
    pub fn dirty_bytes(&self) -> u64 {
        self.dirty.values().filter_map(|key| self.meshes.get(key)?.pending.as_ref()).map(|pending| pending.payload_bytes).sum()
    }

    /// Meshes with an uploaded generation.
    pub fn uploaded_count(&self) -> usize {
        self.meshes.values().filter(|mesh| mesh.uploaded.is_some()).count()
    }
}

static RESIDENCY: LazyLock<Mutex<TerrainResidency>> = LazyLock::new(|| Mutex::new(TerrainResidency::default()));

/// The process's terrain residency.
pub fn residency() -> MutexGuard<'static, TerrainResidency> {
    RESIDENCY.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn residency_of(generation: u64) -> Residency {
        Residency { generation, texture_ids: vec![7, 9] }
    }

    fn pending(generation: u64, bytes: u64) -> Option<Pending> {
        Some(Pending { generation, payload_bytes: bytes, asset: StagedAsset::default() })
    }

    #[test]
    fn registration_queues_once_and_an_identical_rebuild_changes_only_the_residency() {
        let mut r = TerrainResidency::default();
        assert!(!r.register(1, residency_of(10), pending(10, 100), 5));
        assert!(r.register(1, residency_of(10), pending(10, 100), 6), "identical rebuild");
        assert!(!r.register(1, residency_of(11), pending(11, 120), 7), "changed payload");
        let queue = r.candidates(&[], &HashMap::new(), 32);
        assert_eq!(queue, vec![Candidate { sequence: Some(5), key: 1, generation: 11, payload_bytes: 120 }],
            "a dirty mesh keeps its first sequence");
        assert_eq!((r.residency_count(), r.pending_count(), r.dirty_count()), (1, 1, 1));
    }

    #[test]
    fn candidates_take_instances_first_then_the_queue_by_sequence() {
        let mut r = TerrainResidency::default();
        r.register(1, residency_of(10), pending(10, 1), 30);
        r.register(2, residency_of(20), pending(20, 2), 10);
        r.register(3, residency_of(30), pending(30, 3), 20);
        let protected = HashMap::from([(3, 29)]);
        let chosen = r.candidates(&[(1, 10), (2, 99), (1, 10)], &protected, 32);
        let keys: Vec<(Option<u64>, u64)> = chosen.iter().map(|c| (c.sequence, c.key)).collect();
        assert_eq!(keys, vec![(None, 1), (Some(10), 2), (Some(30), 1)],
            "stale instance and protected generation skipped; the queue repeats the instance's asset");
        assert_eq!(r.candidates(&[], &HashMap::new(), 2).len(), 2, "limit");
        assert_eq!(r.candidates(&[(1, 10), (2, 20)], &HashMap::new(), 2).len(), 4, "the queue has its own limit");
    }

    #[test]
    fn acknowledgement_publishes_and_drops_the_geometry_of_the_registered_generation() {
        let mut r = TerrainResidency::default();
        r.register(1, residency_of(10), pending(10, 1), 1);
        assert_eq!(r.accepted(1, 10), Accepted::Rejected);
        assert_eq!(r.accepted(2, 10), Accepted::NotTerrain);
        assert!(r.pending_asset(1, 10).is_some());
        r.acknowledge(&[(1, 10)]);
        assert_eq!(r.accepted(1, 10), Accepted::Uploaded(vec![7, 9]));
        assert!(r.pending_asset(1, 10).is_none());
        assert_eq!((r.pending_count(), r.dirty_count()), (0, 0));
        assert!(r.uploaded_and_clean(1, 10));
        assert!(r.generation_matches(1, 10));
        // A newer registration: the acknowledged generation stays drawable.
        r.register(1, residency_of(11), pending(11, 1), 2);
        assert_eq!(r.accepted(1, 10), Accepted::Uploaded(vec![7, 9]));
        assert!(r.generation_matches(1, 11) && !r.generation_matches(1, 10));
        // An upload of a generation that was re-registered meanwhile keeps the waiting asset.
        r.register(1, residency_of(12), pending(12, 1), 3);
        r.acknowledge(&[(1, 11)]);
        assert!(r.pending_asset(1, 12).is_some());
        assert_eq!(r.accepted(1, 11), Accepted::Rejected, "11 is neither registered nor acknowledged");
    }

    #[test]
    fn removal_retires_the_uploaded_generation_first() {
        let mut r = TerrainResidency::default();
        assert_eq!(r.remove(1), None);
        r.register(1, residency_of(10), pending(10, 1), 1);
        assert_eq!(r.remove(1), Some(10), "never uploaded: the waiting generation");
        r.register(1, residency_of(10), pending(10, 1), 2);
        r.acknowledge(&[(1, 10)]);
        r.register(1, residency_of(11), pending(11, 1), 3);
        assert_eq!(r.remove(1), Some(10), "the uploaded generation");
        assert!(!r.contains(1));
        assert_eq!((r.residency_count(), r.pending_count(), r.dirty_count()), (0, 0, 0));
    }
}

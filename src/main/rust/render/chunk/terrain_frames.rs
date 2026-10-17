//! Bounded immutable CPU selection handoff. References contain identities, never
//! native addresses or GPU handles. Decoding acquires ownership before queuing.
use super::terrain_selection::TerrainSelection;
use crate::render::bridge::abi::FFI_MAX_BATCH_ITEMS;
use crate::render::scene::terrain::*;
use std::collections::{HashMap, VecDeque};
use std::sync::{atomic::{AtomicU64, Ordering}, Arc, Mutex, OnceLock, Weak};

const RETAINED_FRAMES: usize = 3;
static NEXT_OWNER: AtomicU64 = AtomicU64::new(1);
static OWNERS: OnceLock<Mutex<HashMap<u64, Weak<TerrainFrames>>>> = OnceLock::new();

fn owners() -> &'static Mutex<HashMap<u64, Weak<TerrainFrames>>> {
    OWNERS.get_or_init(Default::default)
}

#[derive(Clone, Debug)]
struct Snapshot {
    id: u64,
    sections: StaticTerrainSections,
    casters: StaticTerrainShadowCasters,
}

#[derive(Default, Debug)]
struct History {
    next: u64,
    frames: VecDeque<Snapshot>,
}

#[derive(Debug)]
pub(crate) struct TerrainFrames {
    pub(crate) id: u64,
    history: Mutex<History>,
}

impl TerrainFrames {
    pub(crate) fn invalidate(&self) {
        self.history.lock().unwrap().frames.clear();
    }
    pub(crate) fn new() -> Arc<Self> {
        let id = NEXT_OWNER.fetch_update(Ordering::Relaxed, Ordering::Relaxed,
            |id| id.checked_add(1).filter(|next| *next <= i64::MAX as u64)).unwrap_or(0);
        let owner = Arc::new(Self { id, history: Mutex::new(History::default()) });
        if id != 0 { owners().lock().unwrap().insert(id, Arc::downgrade(&owner)); }
        owner
    }

    /// Validate once at production, with exactly the legacy record rules.
    /// Zero/invalid inputs never publish a partial frame.
    pub(crate) fn publish(&self, selection: &TerrainSelection, camera: [f64; 3]) -> Option<u64> {
        use crate::render::scene::mesh::WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS;
        use crate::render::scene::material::{WORLD_DEPTH_POLICY_TEST_WRITE, WORLD_DEPTH_POLICY_TEST_NO_WRITE};
        let valid_depth = |depth| depth == WORLD_DEPTH_POLICY_TEST_WRITE || depth == WORLD_DEPTH_POLICY_TEST_NO_WRITE;
        if self.id == 0 || camera.iter().any(|value| !value.is_finite())
            || selection.sections.len().saturating_add(selection.casters.len()) > FFI_MAX_BATCH_ITEMS
            || selection.sections.iter().any(|section| section.mesh_key == 0 || section.mesh_generation == 0
                || !valid_depth(section.depth_policy) || section.reserved != 0
                || section.flags & !WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0)
            || selection.casters.iter().any(|caster| caster.mesh_key == 0 || caster.mesh_generation == 0
                || !valid_depth(caster.depth_policy)) {
            return None;
        }
        let sections = selection.sections.iter().map(|section| StaticTerrainSection {
            mesh_key: section.mesh_key, depth_policy: section.depth_policy,
            origin: section.origin, flags: section.flags,
        }).collect();
        let mut casters: Vec<_> = selection.casters.iter().map(|caster| StaticTerrainShadowCaster {
            mesh_key: caster.mesh_key, mesh_generation: caster.mesh_generation,
            origin: caster.origin, depth_policy: caster.depth_policy,
        }).collect();
        // The frontend applies this same ordering to the legacy stream.
        casters.sort_unstable_by_key(|caster| caster.mesh_key);
        let mut history = self.history.lock().unwrap();
        let id = history.next.checked_add(1).filter(|next| *next <= i64::MAX as u64)?;
        history.next = id;
        history.frames.push_back(Snapshot { id,
            sections: StaticTerrainSections { camera, sections: Arc::new(sections) },
            casters: StaticTerrainShadowCasters { camera, casters: Arc::new(casters) },
        });
        while history.frames.len() > RETAINED_FRAMES { history.frames.pop_front(); }
        Some(id)
    }
}

impl Drop for TerrainFrames {
    fn drop(&mut self) {
        if self.id != 0 { owners().lock().unwrap().remove(&self.id); }
    }
}

/// An acquired frame survives owner closure, eviction and later graph edits.
/// Unknown/evicted identities and mismatched cameras fail closed.
pub(crate) fn resolve(owner: u64, frame: u64, camera: [f64; 3])
    -> Option<(StaticTerrainSections, StaticTerrainShadowCasters)> {
    if owner == 0 || frame == 0 { return None; }
    let owner = owners().lock().unwrap().get(&owner).and_then(Weak::upgrade)?;
    let history = owner.history.lock().unwrap();
    let snapshot = history.frames.iter().find(|snapshot| snapshot.id == frame
        && snapshot.sections.camera.map(f64::to_bits) == camera.map(f64::to_bits))?;
    Some((snapshot.sections.clone(), snapshot.casters.clone()))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::bridge::abi::{FfiStaticTerrainSection, FfiStaticTerrainShadowCaster};
    use crate::render::scene::material::WORLD_DEPTH_POLICY_TEST_WRITE;

    fn selection() -> TerrainSelection {
        let mut out = TerrainSelection::default();
        out.sections.push(FfiStaticTerrainSection { mesh_key: 7, mesh_generation: 11,
            origin: [-16, 64, 32], depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE, flags: 0, reserved: 0 });
        for key in [9, 3] { out.casters.push(FfiStaticTerrainShadowCaster { mesh_key: key,
            mesh_generation: 12, origin: [16, 0, -32], depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE }); }
        out
    }

    #[test]
    fn exact_identity_camera_order_and_retained_lifetime() {
        let owner = TerrainFrames::new(); let other = TerrainFrames::new();
        let camera = [1.5, 70.25, -0.0]; let mut input = selection();
        let first = owner.publish(&input, camera).unwrap();
        let retained = resolve(owner.id, first, camera).unwrap();
        let again = resolve(owner.id, first, camera).unwrap();
        assert!(Arc::ptr_eq(&retained.0.sections, &again.0.sections));
        assert!(resolve(other.id, first, camera).is_none());
        assert!(resolve(owner.id, first, [1.5, 70.25, 0.0]).is_none());
        assert_eq!(retained.1.casters.iter().map(|caster| caster.mesh_key).collect::<Vec<_>>(), [3, 9]);
        input.sections[0].mesh_key = 99;
        for _ in 0..3 { owner.publish(&input, camera).unwrap(); }
        assert!(resolve(owner.id, first, camera).is_none());
        assert_eq!(owner.history.lock().unwrap().frames.len(), RETAINED_FRAMES);
        assert_eq!(retained.0.sections[0].mesh_key, 7);
        let id = owner.id; drop(owner);
        assert!(resolve(id, first + 3, camera).is_none());
        assert_eq!(retained.0.sections[0].origin, [-16, 64, 32]);
    }

    #[test]
    fn invalid_publications_do_not_replace_or_advance_valid_frames() {
        let owner = TerrainFrames::new(); let camera = [0.0; 3]; let mut input = selection();
        let first = owner.publish(&input, camera).unwrap();
        input.sections[0].reserved = 1; assert!(owner.publish(&input, camera).is_none());
        input.sections[0].reserved = 0;
        assert!(owner.publish(&input, [f64::NAN, 0.0, 0.0]).is_none());
        input.casters[0].depth_policy = u32::MAX; assert!(owner.publish(&input, camera).is_none());
        assert!(resolve(owner.id, first, camera).is_some());
        assert_eq!(owner.history.lock().unwrap().next, first);
    }

    #[test]
    fn reload_invalidates_unresolved_frames_without_reusing_ids_or_mutating_acquired_arrays() {
        let owner = TerrainFrames::new(); let camera = [0.0; 3]; let input = selection();
        let first = owner.publish(&input, camera).unwrap();
        let retained = resolve(owner.id, first, camera).unwrap();
        owner.invalidate();
        assert!(resolve(owner.id, first, camera).is_none());
        let next = owner.publish(&input, camera).unwrap();
        assert!(next > first);
        assert_eq!(retained.0.sections[0].mesh_key, 7);
        assert!(!Arc::ptr_eq(&retained.0.sections, &resolve(owner.id, next, camera).unwrap().0.sections));
        let mut oversized = input;
        oversized.sections.resize(FFI_MAX_BATCH_ITEMS, oversized.sections[0]);
        assert!(owner.publish(&oversized, camera).is_none());
        assert!(resolve(owner.id, next, camera).is_some());
    }
}

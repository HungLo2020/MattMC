//! Stable model admission reads the primary numeric registries directly. A
//! proof records slot revisions, never a second key table or payload copy.
//! Java pins both registries for the proof's entire lifetime and serializes use.
use super::Registry;

struct Bindings {
    owner: usize,
    entries: Vec<(i64, u32, u64)>,
}
impl Bindings {
    fn capture(owner: &Registry, keys: &[i64]) -> Option<Self> {
        if owner.revision == u64::MAX {
            return None;
        }
        let entries = keys
            .iter()
            .map(|key| {
                let &slot = owner.keys.get(key)?;
                Some((*key, slot, owner.slots[slot as usize].revision))
            })
            .collect::<Option<Vec<_>>>()?;
        Some(Self {
            owner: owner as *const Registry as usize,
            entries,
        })
    }
    fn valid(&self, owner: &Registry) -> bool {
        self.owner == owner as *const Registry as usize
            && owner.revision != u64::MAX
            && self.entries.iter().all(|&(_, slot, revision)| {
                owner
                    .slots
                    .get(slot as usize)
                    .is_some_and(|value| value.revision == revision)
            })
    }
    fn can_refresh(&self, owner: &Registry) -> bool {
        self.owner == owner as *const Registry as usize
            && owner.revision != u64::MAX
            && self
                .entries
                .iter()
                .all(|&(key, _, _)| owner.keys.contains_key(&key))
    }
    fn refresh(&mut self, owner: &Registry) {
        for (key, slot, revision) in &mut self.entries {
            *slot = owner.keys[key];
            *revision = owner.slots[*slot as usize].revision;
        }
    }
}
pub struct Proof {
    meshes: Bindings,
    textures: Bindings,
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_proof_create(
    meshes: *const Registry,
    textures: *const Registry,
    keys: *const i64,
    mesh_count: u32,
    texture_count: u32,
) -> *mut Proof {
    if meshes.is_null()
        || textures.is_null()
        || keys.is_null()
        || mesh_count > 16384
        || texture_count > 32768
    {
        return std::ptr::null_mut();
    }
    let keys = std::slice::from_raw_parts(keys, (mesh_count + texture_count) as usize);
    let Some(meshes) = Bindings::capture(&*meshes, &keys[..mesh_count as usize]) else {
        return std::ptr::null_mut();
    };
    let Some(textures) = Bindings::capture(&*textures, &keys[mesh_count as usize..]) else {
        return std::ptr::null_mut();
    };
    Box::into_raw(Box::new(Proof { meshes, textures }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_proof_release(proof: *mut Proof) {
    if !proof.is_null() {
        drop(Box::from_raw(proof));
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_proof_valid(
    proof: *const Proof,
    meshes: *const Registry,
    textures: *const Registry,
) -> i32 {
    let proof = &*proof;
    i32::from(proof.meshes.valid(&*meshes) && proof.textures.valid(&*textures))
}
/// Called only after successful ordered Java admission. Failed refresh changes
/// neither half of the proof; no new buffers or payload mirrors are required.
#[no_mangle]
pub unsafe extern "C" fn mattmc_asset_registry_proof_refresh(
    proof: *mut Proof,
    meshes: *const Registry,
    textures: *const Registry,
) -> i32 {
    let proof = &mut *proof;
    let meshes = &*meshes;
    let textures = &*textures;
    if !proof.meshes.can_refresh(meshes) || !proof.textures.can_refresh(textures) {
        return 0;
    }
    proof.meshes.refresh(meshes);
    proof.textures.refresh(textures);
    1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn proof_tracks_replacement_removal_reuse_clear_and_saturation() {
        let mut r = Registry::default();
        r.put(1);
        r.put(2);
        let proof = Bindings::capture(&r, &[1]).unwrap();
        r.put(2);
        assert!(proof.valid(&r)); // Unrelated payload replacement.
        r.put(1);
        assert!(!proof.valid(&r));
        let proof = Bindings::capture(&r, &[1]).unwrap();
        r.remove(1);
        assert!(!proof.valid(&r));
        r.put(1);
        assert!(!proof.valid(&r)); // Same slot/key cannot revive a proof.
        let proof = Bindings::capture(&r, &[1]).unwrap();
        r.clear();
        r.put(1);
        assert!(!proof.valid(&r));
        let proof = Bindings::capture(&r, &[1]).unwrap();
        r.revision = u64::MAX - 1;
        r.put(2);
        assert!(!proof.valid(&r));
        assert!(Bindings::capture(&r, &[1]).is_none());
    }
    #[test]
    fn different_registry_and_missing_inputs_decline() {
        let mut a = Registry::default();
        let mut b = Registry::default();
        a.put(1);
        b.put(1);
        assert!(!Bindings::capture(&a, &[1]).unwrap().valid(&b));
        assert!(Bindings::capture(&a, &[2]).is_none());
    }
    #[test]
    fn refresh_resolves_reused_slots_only_after_all_dependencies_exist() {
        let mut a = Registry::default();
        let mut b = Registry::default();
        a.put(1);
        b.put(2);
        let mut proof = Proof {
            meshes: Bindings::capture(&a, &[1]).unwrap(),
            textures: Bindings::capture(&b, &[2]).unwrap(),
        };
        a.remove(1);
        a.put(9);
        a.put(1);
        b.remove(2);
        assert_eq!(
            unsafe { mattmc_asset_registry_proof_refresh(&mut proof, &a, &b) },
            0
        );
        assert!(!proof.meshes.valid(&a));
        b.put(2);
        assert_eq!(
            unsafe { mattmc_asset_registry_proof_refresh(&mut proof, &a, &b) },
            1
        );
        assert!(proof.meshes.valid(&a));
        assert!(proof.textures.valid(&b));
    }
}

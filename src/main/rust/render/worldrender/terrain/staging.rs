//! Terrain asset staging: assembly keeps a layer's decoded vertices, index
//! bytes and draw ranges in Rust, keyed by mesh key and generation, and the
//! layer's registration takes them into terrain residency
//! (`residency.rs`), which owns them until the upload is acknowledged.
//! Java's asset record holds only counts, so terrain geometry never crosses
//! back into Java (assembly still copies what Java diagnostics read).

use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use crate::render::scene::mesh::{WorldMeshSection, WorldMeshVertex};

/// Bound on staged layers (each key holds at most one generation).
pub const MAX_STAGED_LAYERS: usize = 32_768;

/// One assembled layer's geometry.
#[derive(Clone, Debug, Default)]
pub struct StagedAsset {
    /// The FFI index type (`ffi_index_type`).
    pub index_type: u32,
    pub vertices: Vec<WorldMeshVertex>,
    pub index_bytes: Vec<u8>,
    pub sections: Vec<WorldMeshSection>,
}

impl StagedAsset {
    /// Upload payload bytes, as `worldMeshAssetPayloadBytes` counts them.
    pub fn payload_bytes(&self, vertex_record_bytes: u64) -> u64 {
        self.index_bytes.len() as u64 + self.vertices.len() as u64 * vertex_record_bytes
    }
}

static STAGED: LazyLock<Mutex<HashMap<u64, (u64, StagedAsset)>>> = LazyLock::new(|| Mutex::new(HashMap::new()));

fn staged() -> std::sync::MutexGuard<'static, HashMap<u64, (u64, StagedAsset)>> {
    STAGED.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Stages `asset` for `mesh_key`, replacing any older generation of it.
/// Returns false (nothing staged) when the bound is reached.
pub fn stage(mesh_key: u64, mesh_generation: u64, asset: StagedAsset) -> bool {
    let mut staged = staged();
    if staged.len() >= MAX_STAGED_LAYERS && !staged.contains_key(&mesh_key) {
        return false;
    }
    staged.insert(mesh_key, (mesh_generation, asset));
    true
}

/// Takes the staged asset of exactly this generation.
pub fn take(mesh_key: u64, mesh_generation: u64) -> Option<StagedAsset> {
    let mut staged = staged();
    match staged.get(&mesh_key) {
        Some((generation, _)) if *generation == mesh_generation => staged.remove(&mesh_key).map(|(_, asset)| asset),
        _ => None,
    }
}

/// Drops a staged asset of `mesh_key` (any generation when `mesh_generation` is 0).
pub fn discard(mesh_key: u64, mesh_generation: u64) {
    let mut staged = staged();
    if staged.get(&mesh_key).is_some_and(|(generation, _)| mesh_generation == 0 || *generation == mesh_generation) {
        staged.remove(&mesh_key);
    }
}

/// Staged layer count (diagnostics).
pub fn len() -> usize {
    staged().len()
}

#[cfg(test)]
mod tests {
    use super::*;

    pub(crate) fn asset(vertex_count: usize) -> StagedAsset {
        StagedAsset {
            index_type: 1,
            vertices: vec![
                WorldMeshVertex {
                    position: [0.0; 3],
                    uv: [0.0; 2],
                    shader_atlas_uv: [0.0; 2],
                    shader_block_id: 0,
                    shader_material_type: 0,
                    terrain_material_bits: 0,
                    mid_block_packed: 0,
                    color_argb: 0,
                    normal_packed: 0,
                    light: 0,
                };
                vertex_count
            ],
            index_bytes: vec![0; vertex_count / 4 * 12],
            sections: Vec::new(),
        }
    }

    #[test]
    fn a_staged_asset_is_taken_once_and_only_by_its_generation() {
        let key = 0x5ea9_0001;
        assert!(stage(key, 7, asset(4)));
        assert!(take(key, 8).is_none(), "another generation must not take it");
        assert!(stage(key, 8, asset(8)), "a newer generation replaces the older one");
        assert!(take(key, 7).is_none());
        assert_eq!(8, take(key, 8).unwrap().vertices.len());
        assert!(take(key, 8).is_none(), "taking removes it");
        assert!(stage(key, 9, asset(4)));
        discard(key, 8);
        assert_eq!(1, staged().get(&key).map_or(0, |_| 1), "discarding another generation keeps it");
        discard(key, 0);
        assert!(take(key, 9).is_none());
    }
}

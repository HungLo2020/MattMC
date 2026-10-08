//! Terrain vertex staging: assembly keeps a layer's decoded vertices in Rust,
//! keyed by mesh key and generation. The world mesh asset update that
//! publishes the layer copies them (a rejected update is retried), and Java
//! discards the entry once that upload is acknowledged or the layer is
//! removed. Java's asset record carries only the vertex count, so terrain
//! vertices never cross back into Java.

use std::collections::HashMap;
use std::sync::{LazyLock, Mutex};

use crate::render::scene::mesh::WorldMeshVertex;

/// Bound on staged layers (each key holds at most one generation).
pub const MAX_STAGED_LAYERS: usize = 32_768;

static STAGED: LazyLock<Mutex<HashMap<u64, (u64, Vec<WorldMeshVertex>)>>> =
    LazyLock::new(|| Mutex::new(HashMap::new()));

fn staged() -> std::sync::MutexGuard<'static, HashMap<u64, (u64, Vec<WorldMeshVertex>)>> {
    STAGED.lock().unwrap_or_else(|poisoned| poisoned.into_inner())
}

/// Stages `vertices` for `mesh_key`, replacing any older generation of it.
/// Returns false (nothing staged) when the bound is reached.
pub fn stage(mesh_key: u64, mesh_generation: u64, vertices: Vec<WorldMeshVertex>) -> bool {
    let mut staged = staged();
    if staged.len() >= MAX_STAGED_LAYERS && !staged.contains_key(&mesh_key) {
        return false;
    }
    staged.insert(mesh_key, (mesh_generation, vertices));
    true
}

/// A copy of the staged vertices of exactly this generation.
pub fn get(mesh_key: u64, mesh_generation: u64) -> Option<Vec<WorldMeshVertex>> {
    match staged().get(&mesh_key) {
        Some((generation, vertices)) if *generation == mesh_generation => Some(vertices.clone()),
        _ => None,
    }
}

/// Drops staged vertices of `mesh_key` (any generation when `mesh_generation` is 0).
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

    fn vertices(count: usize) -> Vec<WorldMeshVertex> {
        vec![
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
            count
        ]
    }

    #[test]
    fn staged_vertices_serve_only_their_generation_until_discarded() {
        let key = 0x5ea9_0001;
        assert!(stage(key, 7, vertices(4)));
        assert!(get(key, 8).is_none(), "another generation must not read them");
        assert!(stage(key, 8, vertices(8)), "a newer generation replaces the older one");
        assert!(get(key, 7).is_none());
        assert_eq!(8, get(key, 8).unwrap().len());
        assert_eq!(8, get(key, 8).unwrap().len(), "a retried update reads them again");
        discard(key, 7);
        assert!(get(key, 8).is_some(), "discarding another generation keeps them");
        discard(key, 8);
        assert!(get(key, 8).is_none());
        assert!(stage(key, 9, vertices(4)));
        discard(key, 0);
        assert!(get(key, 9).is_none());
    }
}

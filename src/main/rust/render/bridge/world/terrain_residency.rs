//! C exports of static-terrain mesh residency
//! (`worldrender/terrain/residency.rs`) for `RustTerrainResidency`. Java
//! calls them under its world-primitive lock.

use std::collections::HashMap;

use crate::render::bridge::abi::FfiWorldMeshVertex;
use crate::render::worldrender::terrain::residency::{residency, Accepted, Pending, Residency};
use crate::render::worldrender::terrain::staging;

const ERR_NULL_POINTER: i32 = -1;
const ERR_NOT_STAGED: i32 = -2;
const NOT_TERRAIN: i32 = -2;
const REJECTED: i32 = -1;

unsafe fn longs<'a>(pointer: *const i64, count: i32) -> Option<&'a [i64]> {
    match count {
        ..=0 => Some(&[]),
        _ if pointer.is_null() => None,
        _ => Some(std::slice::from_raw_parts(pointer, count as usize)),
    }
}

/// The generation of the terrain asset waiting for upload, or 0.
#[no_mangle]
pub extern "C" fn mattmc_terrain_residency_pending_generation(key: i64) -> i64 {
    residency().pending_generation(key as u64).map_or(0, |generation| generation as i64)
}

/// Registers a terrain mesh's generation and textures. Unless the waiting
/// asset already has this generation, the asset assembly staged as
/// (`staged_key`, `generation`) becomes the waiting asset, queued with
/// `sequence` if the mesh is not dirty yet. Returns 1 for an identical
/// rebuild, 0 when queued, or -2 when nothing was staged.
///
/// # Safety
/// `textures` addresses `texture_count` ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_residency_register(
    key: i64,
    generation: i64,
    staged_key: i64,
    textures: *const i32,
    texture_count: i32,
    sequence: i64,
) -> i32 {
    if texture_count > 0 && textures.is_null() {
        return ERR_NULL_POINTER;
    }
    let texture_ids = if texture_count <= 0 { Vec::new() } else { std::slice::from_raw_parts(textures, texture_count as usize).to_vec() };
    let generation = generation as u64;
    let staged = staging::take(staged_key as u64, generation);
    let mut residency = residency();
    let identical = residency.pending_generation(key as u64) == Some(generation);
    if staged.is_none() && !identical {
        return ERR_NOT_STAGED;
    }
    let pending = staged.map(|asset| Pending {
        generation,
        payload_bytes: asset.payload_bytes(std::mem::size_of::<FfiWorldMeshVertex>() as u64),
        asset,
    });
    residency.register(key as u64, Residency { generation, texture_ids }, pending, sequence as u64) as i32
}

/// Writes [registered residencies, waiting assets, dirty assets, dirty
/// upload bytes, uploaded meshes].
///
/// # Safety
/// `out` addresses 5 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_residency_counts(out: *mut i64) -> i32 {
    if out.is_null() {
        return ERR_NULL_POINTER;
    }
    let residency = residency();
    let counts = [
        residency.residency_count() as i64,
        residency.pending_count() as i64,
        residency.dirty_count() as i64,
        residency.dirty_bytes() as i64,
        residency.uploaded_count() as i64,
    ];
    std::slice::from_raw_parts_mut(out, 5).copy_from_slice(&counts);
    0
}

#[no_mangle]
pub extern "C" fn mattmc_terrain_residency_contains(key: i64) -> i32 {
    residency().contains(key as u64) as i32
}

/// Upload candidates (see `TerrainResidency::candidates`): `instances` and
/// `protected` hold (key, generation) pairs. Writes [sequence or -1, key,
/// generation, payload bytes] per candidate and returns their count.
///
/// # Safety
/// `instances` and `protected` address their counts × 2 longs, `out` `limit` × 4.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_residency_candidates(
    instances: *const i64,
    instance_count: i32,
    protected: *const i64,
    protected_count: i32,
    limit: i32,
    out: *mut i64,
) -> i32 {
    let (Some(instances), Some(protected)) = (longs(instances, instance_count * 2), longs(protected, protected_count * 2)) else {
        return ERR_NULL_POINTER;
    };
    if limit > 0 && out.is_null() {
        return ERR_NULL_POINTER;
    }
    let instances: Vec<(u64, u64)> = instances.chunks_exact(2).map(|p| (p[0] as u64, p[1] as u64)).collect();
    let protected: HashMap<u64, u64> = protected.chunks_exact(2).map(|p| (p[0] as u64, p[1] as u64)).collect();
    let chosen = residency().candidates(&instances, &protected, limit.max(0) as usize);
    let out = std::slice::from_raw_parts_mut(out, chosen.len() * 4);
    for (candidate, slot) in chosen.iter().zip(out.chunks_exact_mut(4)) {
        slot.copy_from_slice(&[
            candidate.sequence.map_or(-1, |sequence| sequence as i64),
            candidate.key as i64,
            candidate.generation as i64,
            candidate.payload_bytes as i64,
        ]);
    }
    chosen.len() as i32
}

/// The accepted update carried these (key, generation) terrain assets.
/// Writes 1 to `released` for each whose registered generation was the one
/// uploaded, else 0.
///
/// # Safety
/// `uploaded` addresses `count` × 2 longs, `released` `count` ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_residency_acknowledge(uploaded: *const i64, count: i32, released: *mut i32) -> i32 {
    let Some(uploaded) = longs(uploaded, count * 2) else { return ERR_NULL_POINTER };
    if count > 0 && released.is_null() {
        return ERR_NULL_POINTER;
    }
    let uploaded: Vec<(u64, u64)> = uploaded.chunks_exact(2).map(|p| (p[0] as u64, p[1] as u64)).collect();
    let flags = residency().acknowledge(&uploaded);
    if !flags.is_empty() {
        let out = std::slice::from_raw_parts_mut(released, flags.len());
        for (slot, flag) in out.iter_mut().zip(flags) {
            *slot = flag as i32;
        }
    }
    0
}

/// Withdraws a terrain mesh: returns the generation to retire, or 0 when
/// the key was neither registered nor waiting.
#[no_mangle]
pub extern "C" fn mattmc_terrain_residency_remove(key: i64) -> i64 {
    // An assembled layer that never registered leaves nothing staged behind.
    staging::discard(key as u64, 0);
    residency().remove(key as u64).map_or(0, |generation| generation as i64)
}

/// Whether this generation was uploaded: -2 when the key is not terrain,
/// -1 when not uploaded, else the number of its texture ids (all of which
/// must be uploaded too). They are written to `textures` when they fit in
/// `capacity`; a larger count asks the caller to retry with more room.
///
/// # Safety
/// `textures` addresses `capacity` ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_terrain_residency_accepted(key: i64, generation: i64, textures: *mut i32, capacity: i32) -> i32 {
    match residency().accepted(key as u64, generation as u64) {
        Accepted::NotTerrain => NOT_TERRAIN,
        Accepted::Rejected => REJECTED,
        Accepted::Uploaded(ids) => {
            if !ids.is_empty() && ids.len() <= capacity.max(0) as usize && !textures.is_null() {
                std::slice::from_raw_parts_mut(textures, ids.len()).copy_from_slice(&ids);
            }
            ids.len() as i32
        }
    }
}

/// Whether `generation` is the waiting asset's, or else the registered one's.
#[no_mangle]
pub extern "C" fn mattmc_terrain_residency_generation_matches(key: i64, generation: i64) -> i32 {
    residency().generation_matches(key as u64, generation as u64) as i32
}

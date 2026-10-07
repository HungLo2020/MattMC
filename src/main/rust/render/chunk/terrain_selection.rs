//! Per-frame static-terrain selection from the section graph.
//!
//! Java mirrors each built section's layer meshes (solid, cutout,
//! translucent) into the graph. After a camera selection, the graph turns
//! the visited sections into the frame's compact terrain stream, its
//! off-camera shadow casters and the sections whose animated sprites the
//! frame uses, in this order:
//!
//! - camera layers: visible sections (built, flags != 0) in the graph's
//!   near-to-far visit order (so opaque layers reject hidden fragments
//!   early), each solid then cutout, then translucent back to front by
//!   section centre (stable, so equal distances keep visit order);
//! - every mesh key appears once;
//! - shadow candidates: every other section with geometry, nearest
//!   `max_shadow_candidates` by (centre distance, key) when over the limit;
//! - animated sprites: the ids of drawn sections' animated sprites, each once.

use std::collections::{HashMap, HashSet};

use crate::render::vulkanic::gal::AccessHashBuilder;


use super::section_graph::{SectionGraph, VisitedSection};
use crate::render::bridge::abi::{FfiStaticTerrainSection, FfiStaticTerrainShadowCaster};

/// `RenderSectionFlags.HAS_ANIMATED_SPRITES`.
const FLAG_ANIMATED_SPRITES: u8 = 1 << 2;
pub use crate::render::scene::mesh::WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS as CAMERA_SORTED_QUADS;
const TRANSLUCENT: usize = 2;

/// Section key and layer slot (0 solid, 1 cutout, 2 translucent) of one
/// selected camera layer.
#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct SelectedLayer {
    pub section_key: i64,
    pub layer: u32,
    pub reserved: u32,
}

/// One section's published layer meshes. A zero key is an absent layer.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct SectionMeshes {
    pub keys: [u64; 3],
    pub generations: [u64; 3],
    /// The translucent layer asks Rust to camera-sort its quads.
    pub translucent_camera_sorted: bool,
}

#[derive(Clone, Copy, Debug)]
pub struct TerrainSelectionParams {
    pub camera: [f64; 3],
    /// Depth policy of the solid, cutout and translucent layers.
    pub depth_policies: [u32; 3],
    /// Java `ChunkSectionLayer.ordinal()` of each layer, for receipts.
    pub layer_ordinals: [u32; 3],
    pub shadow_candidates: bool,
    pub max_shadow_candidates: usize,
    /// Compute the layer fingerprint receipt (diagnostics and readiness).
    pub receipts: bool,
}

/// Benchmark receipts of the camera stream, matching Java's producer
/// counters: probes per visible layer lookup, accepted layers and their
/// FNV-1a fingerprint over (section key, mesh key, generation, layer).
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct TerrainSelectionReceipts {
    pub layer_probes: u64,
    pub layer_submissions: u64,
    pub fingerprint: u64,
}

/// The selection of the latest frame; buffers are reused across frames.
#[derive(Debug, Default)]
pub struct TerrainSelection {
    pub sections: Vec<FfiStaticTerrainSection>,
    /// Section key and layer slot of each `sections` entry.
    pub section_layers: Vec<SelectedLayer>,
    pub casters: Vec<FfiStaticTerrainShadowCaster>,
    /// Animated sprite ids this frame uses, each once in first-use order
    /// (camera sections, then casters).
    pub animated: Vec<u32>,
    sprite_seen: Vec<u32>,
    sprite_epoch: u32,
    pub receipts: TerrainSelectionReceipts,
    visible: Vec<(i64, [i32; 3], u8)>,
    visible_keys: HashSet<i64, AccessHashBuilder>,
    seen_meshes: HashSet<u64, AccessHashBuilder>,
    translucent: Vec<(f64, i64, [i32; 3])>,
    candidates: Vec<(i64, [i32; 3], u8)>,
}

/// `SectionPos.asLong`.
pub fn section_key(position: [i32; 3]) -> i64 {
    ((i64::from(position[0]) & 0x3F_FFFF) << 42)
        | (i64::from(position[1]) & 0xF_FFFF)
        | ((i64::from(position[2]) & 0x3F_FFFF) << 20)
}

fn centre_distance(position: [i32; 3], camera: [f64; 3]) -> f64 {
    let dx = f64::from(position[0] << 4) + 8.0 - camera[0];
    let dy = f64::from(position[1] << 4) + 8.0 - camera[1];
    let dz = f64::from(position[2] << 4) + 8.0 - camera[2];
    dx * dx + dy * dy + dz * dz
}

fn fingerprint(mut hash: u64, value: u64) -> u64 {
    for shift in (0..64).step_by(8) {
        hash ^= (value >> shift) & 0xff;
        hash = hash.wrapping_mul(0x0000_0100_0000_01b3);
    }
    hash
}

impl TerrainSelection {
    fn push_section(&mut self, position: [i32; 3], key: i64, layer: usize, meshes: &SectionMeshes, params: &TerrainSelectionParams) {
        let flags = if layer == TRANSLUCENT && meshes.translucent_camera_sorted { CAMERA_SORTED_QUADS } else { 0 };
        self.sections.push(FfiStaticTerrainSection {
            mesh_key: meshes.keys[layer],
            mesh_generation: meshes.generations[layer],
            origin: position.map(|value| value << 4),
            depth_policy: params.depth_policies[layer],
            flags,
            reserved: 0,
        });
        self.section_layers.push(SelectedLayer { section_key: key, layer: layer as u32, reserved: 0 });
        let receipts = &mut self.receipts;
        receipts.layer_submissions += 1;
        if !params.receipts {
            return;
        }
        let ordinal = u64::from(params.layer_ordinals[layer]);
        for value in [key as u64, meshes.keys[layer], meshes.generations[layer], ordinal] {
            receipts.fingerprint = fingerprint(receipts.fingerprint, value);
        }
    }
}

impl SectionGraph {
    /// Replaces (or with `None` clears) a section's published layer meshes.
    pub fn set_meshes(&mut self, position: [i32; 3], meshes: Option<SectionMeshes>) {
        match meshes.filter(|meshes| meshes.keys.iter().any(|key| *key != 0)) {
            Some(meshes) => {
                self.meshes.insert(position, meshes);
            }
            None => {
                self.meshes.remove(&position);
            }
        }
    }

    pub fn clear_meshes(&mut self) {
        self.meshes.clear();
    }

    /// Builds `out` from the camera selection's `visits` (see module docs).
    pub fn select_terrain(&self, visits: &[VisitedSection], params: &TerrainSelectionParams, out: &mut TerrainSelection) {
        out.sections.clear();
        out.section_layers.clear();
        out.casters.clear();
        out.animated.clear();
        out.sprite_epoch = out.sprite_epoch.wrapping_add(1);
        if out.sprite_epoch == 0 {
            out.sprite_seen.fill(0);
            out.sprite_epoch = 1;
        }
        out.receipts = TerrainSelectionReceipts { fingerprint: 0xcbf2_9ce4_8422_2325, ..Default::default() };
        out.visible.clear();
        out.visible_keys.clear();
        out.seen_meshes.clear();
        out.translucent.clear();

        for visit in visits {
            let Some(info) = visit.info.filter(|info| info.flags != 0) else {
                continue;
            };
            let key = section_key(visit.position);
            if out.visible_keys.insert(key) {
                out.visible.push((key, visit.position, info.flags));
            }
        }
        // Keep the graph's breadth-first visit order: it is near-to-far from
        // the camera, so solid and cutout layers get early depth rejection.

        let visible = std::mem::take(&mut out.visible);
        for &(key, position, flags) in &visible {
            out.receipts.layer_probes += 2;
            let Some(meshes) = self.meshes.get(&position) else {
                continue;
            };
            let mut submitted = false;
            for layer in 0..2 {
                if meshes.keys[layer] != 0 && out.seen_meshes.insert(meshes.keys[layer]) {
                    out.push_section(position, key, layer, meshes, params);
                    submitted = true;
                }
            }
            if submitted && flags & FLAG_ANIMATED_SPRITES != 0 {
                self.use_sprites(position, out);
            }
            if meshes.keys[TRANSLUCENT] != 0 {
                out.translucent.push((centre_distance(position, params.camera), key, position));
            }
        }
        // Back to front; the stable sort keeps key order for equal distances.
        out.translucent.sort_by(|a, b| b.0.total_cmp(&a.0));
        let translucent = std::mem::take(&mut out.translucent);
        for &(_, key, position) in &translucent {
            out.receipts.layer_probes += 1;
            let meshes = self.meshes[&position];
            if out.seen_meshes.insert(meshes.keys[TRANSLUCENT]) {
                out.push_section(position, key, TRANSLUCENT, &meshes, params);
                let flags = self.slot(position).and_then(|slot| self.section_flags(slot)).unwrap_or(0);
                if flags & FLAG_ANIMATED_SPRITES != 0 {
                    self.use_sprites(position, out);
                }
            }
        }
        out.translucent = translucent;
        out.visible = visible;

        if params.shadow_candidates {
            self.select_shadow_candidates(params, out);
        }
    }

    /// Appends the section's animated sprites not yet used this frame.
    fn use_sprites(&self, position: [i32; 3], out: &mut TerrainSelection) {
        let Some(sprites) = self.source.sprites.get(&position) else {
            return;
        };
        for &sprite in sprites.iter() {
            let index = sprite as usize;
            if index >= out.sprite_seen.len() {
                out.sprite_seen.resize(index + 1, 0);
            }
            if out.sprite_seen[index] != out.sprite_epoch {
                out.sprite_seen[index] = out.sprite_epoch;
                out.animated.push(sprite);
            }
        }
    }

    fn select_shadow_candidates(&self, params: &TerrainSelectionParams, out: &mut TerrainSelection) {
        let mut candidates = std::mem::take(&mut out.candidates);
        candidates.clear();
        for (&position, &slot) in &self.slots {
            let Some(flags) = self.section_flags(slot).filter(|flags| *flags != 0) else {
                continue;
            };
            let key = section_key(position);
            if !out.visible_keys.contains(&key) {
                candidates.push((key, position, flags));
            }
        }
        if candidates.len() > params.max_shadow_candidates {
            candidates.sort_by(|a, b| {
                centre_distance(a.1, params.camera)
                    .total_cmp(&centre_distance(b.1, params.camera))
                    .then(a.0.cmp(&b.0))
            });
            candidates.truncate(params.max_shadow_candidates);
        }
        candidates.sort_unstable_by_key(|(key, _, _)| *key);
        for &(_, position, flags) in &candidates {
            if let Some(meshes) = self.meshes.get(&position) {
                for layer in 0..3 {
                    if meshes.keys[layer] != 0 {
                        out.casters.push(FfiStaticTerrainShadowCaster {
                            mesh_key: meshes.keys[layer],
                            mesh_generation: meshes.generations[layer],
                            origin: position.map(|value| value << 4),
                            depth_policy: params.depth_policies[layer],
                        });
                    }
                }
            }
            if flags & FLAG_ANIMATED_SPRITES != 0 {
                self.use_sprites(position, out);
            }
        }
        out.candidates = candidates;
    }
}

/// Mesh rows mirrored from Java, keyed by section position.
/// Section meshes by position; a fast non-cryptographic hasher (per-frame lookups).
pub type SectionMeshTable = HashMap<[i32; 3], SectionMeshes, AccessHashBuilder>;

#[cfg(test)]
mod tests;

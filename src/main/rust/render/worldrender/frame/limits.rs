//! Frame, asset and residency limits of the world renderer.

/// Bounded line stream capacity. Light-section diagnostics may emit several
/// thousand edges for the configured 10-section radius; 65,536 remains a
/// fixed transport and GPU submission bound rather than an unbounded queue.
// Vulkan implementations commonly expose a 64 KiB max uniform-buffer range.
// Keep the bounded line payload below that limit; callers preflight this
// capacity and reject overflows rather than silently truncating semantic lines.
pub const WORLD_MAX_LINE_SEGMENTS: usize = 1_360;

pub const WORLD_MAX_CRACK_QUADS: usize = 512;

pub const WORLD_MAX_BORDER_QUADS: usize = 64;

/// Bounded coarse material stream capacity for one semantic frame. This is
/// deliberately independent of the per-draw payload capacity below: large
/// environment producers such as clouds are split by the Rust frontend, not
/// rejected by the transport solely because they need more than one draw.
/// Per-frame material quad bound (clouds at the default range need ~100k).
pub const WORLD_MAX_MATERIAL_QUADS: usize = 262_144;

/// One material draw stores its quads in a fixed-size shader-visible payload.
/// Keep this local execution limit separate from the frame transport bound.
pub(in crate::render::worldrender) const WORLD_MAX_MATERIAL_QUADS_PER_BATCH: usize = 4_096;

pub const WORLD_MAX_MESH_VERTICES: usize = 65_536;

pub const WORLD_MAX_MESH_INDEX_BYTES: usize = 393_216;

pub const WORLD_MAX_MESH_SECTIONS: usize = 4_096;

// Static terrain submits one semantic mesh instance per visible section/layer. The
// whole-frame stream remains bounded, but must accommodate a fully admitted
// terrain frame rather than the earlier diagnostic-only subset.
pub const WORLD_MAX_MESH_INSTANCES: usize = 4_096;

/// Per-frame mesh-instance bound (terrain section layers, shadow-only casters
/// and entity parts together). Shader packs render shadow terrain around the
/// player, not only camera-visible sections, so this equals the FFI batch
/// transport bound rather than the per-batch GPU stream bound above.
pub const WORLD_MAX_FRAME_MESH_INSTANCES: usize = 65_536;

/// Maximum retained semantic world mesh assets, matching the Java collector's
/// residency contract before explicit GAL resources are staged.
pub const WORLD_MESH_ASSET_RESIDENCY: usize = 16_384;

/// Maximum retained semantic world texture assets, including resource-pack and
/// particle/material atlas payloads.
pub const WORLD_MESH_TEXTURE_RESIDENCY: usize = 8_192;

/// Bounds derived GAL resource bindings independently of semantic asset maps;
/// a mesh can produce multiple explicit section/material bindings.
pub const WORLD_MESH_RESOURCE_RESIDENCY: usize = 65_536;

pub const WORLD_MESH_GEOMETRY_RESIDENCY: usize = 16_384;

pub const WORLD_SOURCE_MESH_RESOURCE_RESIDENCY: usize = 16_384;

/// Bounds distinct explicit mesh pipeline contracts across vanilla and
/// shader-pack material variants.
pub const WORLD_MESH_PIPELINE_RESIDENCY: usize = 4_096;

pub const WORLD_MAX_MESH_ANIMATION_FRAMES: usize = 512;

pub const WORLD_MAX_MESH_TEXTURE_ASSETS: usize = 4_096;

pub const WORLD_MAX_MESH_TEXTURE_DECODED_BYTES: usize = 256 * 1024 * 1024;

/// Aggregate byte budget for privately prepared lowered shader-source
/// geometry.  Source preparation is unadmitted until complete; refusing a
/// new mesh at this boundary keeps native residency bounded without ever
/// evicting resources that an in-flight command may still reference.
/// Device-local source geometry. Render distance 10 with a typical pack keeps
/// ~0.9 GiB resident; the bound leaves room for larger distances while still
/// rejecting runaway growth.
pub const LOWERED_SOURCE_GEOMETRY_MAX_BYTES: u64 = 2048 * 1024 * 1024;

/// Aggregate GPU residency budget for copied local-material textures used by
/// lowered shader-source writers. Each texture owns both a sampled image and
/// a completion-gated upload buffer, so the accounting includes both copies.
pub const LOWERED_SOURCE_TEXTURE_MAX_BYTES: u64 = 512 * 1024 * 1024;

/// Maximum number of generation/resource-set combinations retained for each
/// lowered source writer.  Stale generations are retired when replaced, but
/// distinct live keys must still have a fixed native residency ceiling.
pub const LOWERED_SOURCE_PACK_RESIDENCY: usize = 4_096;

pub(in crate::render::worldrender) fn lowered_source_geometry_budget_allows(resident_bytes: u64, requested_bytes: u64) -> bool {
    resident_bytes
        .checked_add(requested_bytes)
        .is_some_and(|total| total <= LOWERED_SOURCE_GEOMETRY_MAX_BYTES)
}

pub(in crate::render::worldrender) fn lowered_source_texture_budget_allows(resident_bytes: u64, requested_bytes: u64) -> bool {
    resident_bytes
        .checked_add(requested_bytes)
        .is_some_and(|total| total <= LOWERED_SOURCE_TEXTURE_MAX_BYTES)
}

#[inline]
pub(in crate::render::worldrender) fn lowered_source_residency_allows(current_entries: usize) -> bool {
    current_entries < WORLD_MESH_PIPELINE_RESIDENCY
}

#[inline]
pub(in crate::render::worldrender) fn lowered_source_pack_residency_allows(current_entries: usize) -> bool {
    current_entries < LOWERED_SOURCE_PACK_RESIDENCY
}

pub const WORLD_LOD_MAX_SEGMENTS_PER_COLUMN: usize = 512;

/// A visible DH column may contain just one segment. Admission must therefore
/// permit the same worst-case column count as the Java visible-candidate and
/// Rust visible-segment contracts. The former 512-column cap rejected dense
/// quadtree frames before either visible bound was reached.
pub const WORLD_LOD_MAX_COLUMNS: usize = WORLD_LOD_MAX_VISIBLE_SEGMENTS;

/// Maximum visible DH segment instances retained in one semantic frame.
pub const WORLD_LOD_MAX_VISIBLE_SEGMENTS: usize = 16_384;

/// Maximum derived DH voxel source meshes retained across frames. The cache
/// is CPU semantic geometry (not a GPU handle registry); when full it is
/// rebuilt from the bounded column/segment inputs rather than growing without
/// limit.
pub const WORLD_LOD_MAX_SOURCE_MESH_CACHE: usize = 16_384;

pub const WORLD_LOD_MAX_VERTICES_PER_SEGMENT: usize = 2_097_152;

pub const WORLD_LOD_MAX_MATERIAL_IDENTITIES_PER_COLUMN: usize = 4_096;

/// Distant Horizons' public semantic block-material table currently spans
/// `UNKNOWN` through `ILLUMINATED` inclusive. This is a material category,
/// never a texture or backend resource identifier.
pub const WORLD_LOD_MAX_MATERIAL_ID: u8 = 15;

pub const WORLD_LOD_MAX_NORMAL_INDEX: u8 = 5;

/// Outline mask instances per frame; equal to the bridge's per-batch transport
/// limit, which bounds the instances Java can send.
pub const WORLD_MAX_OUTLINE_MASK_INSTANCES: usize = 65_536;

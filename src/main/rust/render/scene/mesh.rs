//! Copied world mesh assets (vertex layout versions, vertices, sections and the
//! asset record) and the mesh-instance vocabulary: instance flags, animation
//! interpolation, cull, winding and topology.

use crate::render::vulkanic::resources::IndexType;

pub const WORLD_MESH_VERTEX_LAYOUT_V2: u32 = 2;

/// Adds source-model midpoint bytes used only by private voxelization.
pub const WORLD_MESH_VERTEX_LAYOUT_V3: u32 = 3;

#[derive(Clone, Copy, Debug)]
pub struct WorldMeshVertex {
    pub position: [f32; 3],
    pub uv: [f32; 2],
    pub shader_atlas_uv: [f32; 2],
    pub shader_block_id: i32,
    pub shader_material_type: i32,
    /// Sodium material byte: mip policy and alpha-cutoff class for direct
    /// vanilla terrain rendering.
    pub terrain_material_bits: u32,
    pub mid_block_packed: u32,
    pub color_argb: u32,
    pub normal_packed: u32,
    pub light: u32,
}

#[derive(Clone, Debug)]
pub struct WorldMeshSection {
    pub material_id: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub index_offset: u32,
    pub index_count: u32,
    /// Sodium's baked quad facing (0..5); 6 is unassigned and always visible.
    /// This remains asset metadata so each render pass can select its own cull policy.
    pub source_facing: u32,
}

#[derive(Clone, Debug)]
pub struct WorldMeshAsset {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    pub vertex_layout_version: u32,
    pub index_type: IndexType,
    pub vertices: Vec<WorldMeshVertex>,
    pub index_bytes: Vec<u8>,
    pub sections: Vec<WorldMeshSection>,
    /// Canonical gameplay identity for a copied entity-model asset. Rust owns
    /// the selected source-pack mapping; non-entity meshes use an empty value.
    pub entity_identity: String,
}

pub const WORLD_MESH_SECTION_ALL: u32 = u32::MAX;
pub const WORLD_MESH_ANIMATION_INTERPOLATE_NONE: u32 = 0;
pub const WORLD_MESH_ANIMATION_INTERPOLATE_LINEAR: u32 = 1;
pub const WORLD_TOPOLOGY_TRIANGLES: u32 = 1;
pub const WORLD_CULL_NONE: u32 = 0;
pub const WORLD_CULL_BACK: u32 = 1;
pub const WORLD_CULL_FRONT: u32 = 2;
pub const WORLD_WINDING_CCW: u32 = 1;
pub const WORLD_WINDING_CW: u32 = 2;
/// Semantic mesh-instance flag requesting outline-mask-only consumption.
/// This is a callsite contract bit, never a backend or native handle.
pub const WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY: u32 = 1;
/// Camera-relative translated terrain quads request Rust-owned visibility order.
pub const WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS: u32 = 2;
/// Terrain-only alias for the entity perspective-view bit. The strata are
/// disjoint, and validation removes this bit before checking terrain layering.
pub const WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY: u32 = 4;
/// Bit 31 declares a packed local-U offset; bits 4..30 carry UNORM27.
pub const WORLD_MESH_INSTANCE_FLAG_UV_OFFSET_U: u32 = 0x8000_0000;
pub const WORLD_MESH_INSTANCE_UV_OFFSET_SHIFT: u32 = 4;
pub const WORLD_MESH_INSTANCE_UV_OFFSET_PAYLOAD: u32 = 0x7fff_fff0;
pub const WORLD_MESH_INSTANCE_UV_OFFSET_MAX: u32 = 0x07ff_ffff;

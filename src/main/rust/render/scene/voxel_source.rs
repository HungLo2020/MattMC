//! Compact static-terrain source data retained for shader-pack voxelization.

use std::sync::Arc;

/// Compact, backend-neutral source data retained only for a future Rust-owned
/// terrain voxelization pass. This is deliberately smaller than a render
/// vertex and contains no atlas, pipeline, or native resource information.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct TerrainVoxelSourceVertex {
    pub position: [f32; 3],
    pub mid_block_packed: u32,
    pub shader_material_id: i32,
}

/// One visible static-terrain mesh in a frame. This owns the compact source
/// copy so a private shader runtime can stage it after ordinary frontend work
/// without borrowing the frontend's mutable asset cache across submission.
#[derive(Clone, Debug)]
pub(crate) struct TerrainVoxelSourceMesh {
    pub mesh_key: u64,
    pub mesh_generation: u64,
    /// Shared immutable data copied when the Rust-owned mesh asset was
    /// registered. Per-frame source preparation may retain this without
    /// copying vertex payloads or borrowing Java/native renderer buffers.
    pub vertices: Arc<Vec<TerrainVoxelSourceVertex>>,
    /// Decoded semantic triangle indices retained after asset validation.
    pub indices: Arc<Vec<u32>>,
    /// Indices belonging to translucent sections only. The copied semantic
    /// split is needed by source-derived occupancy writers such as
    /// Complementary's puddle field; it avoids inferring material layers from
    /// vertex attributes or backend draw state.
    pub translucent_indices: Arc<Vec<u32>>,
    pub transform: [f32; 16],
}

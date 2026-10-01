//! Bounds on GUI mesh batches, payloads, offscreen targets and uniforms.

/// One batch may be one independently textured baked quad. A large item-browser
/// grid can therefore approach the aggregate quad capacity without approaching
/// the payload-byte bound.
pub const GUI_MESH_MAX_BATCHES: usize = 16_384;

pub const GUI_MESH_MAX_VERTICES: usize = 1_048_576;

pub const GUI_MESH_MAX_INDICES: usize = 3_145_728;

pub const GUI_MESH_GPU_VERTEX_BYTES: usize = 3 * 4 * 4;

/// Aggregate copied GUI geometry admitted for one semantic frame. This keeps
/// nested mesh slices from multiplying the per-batch limits into an
/// unbounded allocation before command generation.
pub const GUI_MESH_MAX_FRAME_PAYLOAD_BYTES: u64 = 128 * 1024 * 1024;

/// Maximum dimension of a Rust-owned GUI item offscreen raster.
pub const GUI_MESH_MAX_OFFSCREEN_AXIS: u32 = 4096;

pub(super) const GUI_MESH_FRAME_UNIFORM_BYTES: usize = 48;

pub(crate) const GUI_MESH_COMPOSITE_UNIFORM_BYTES: usize = 80;

/// Conservative dynamic-UBO alignment valid for both backend lowerings.
pub const GUI_MESH_COMPOSITE_UNIFORM_STRIDE: u64 = 256;

pub(super) const GUI_MESH_MAX_COMPOSITE_UNIFORM_BYTES: u64 =
    GUI_MESH_MAX_BATCHES as u64 * GUI_MESH_COMPOSITE_UNIFORM_STRIDE;

pub(crate) const GUI_MESH_MAX_VERTEX_BYTES: u64 =
    (GUI_MESH_MAX_VERTICES * GUI_MESH_GPU_VERTEX_BYTES) as u64;

pub(crate) const GUI_MESH_MAX_INDEX_BYTES: u64 =
    (GUI_MESH_MAX_INDICES * std::mem::size_of::<u32>()) as u64;

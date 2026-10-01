//! The renderer-facing description of what to draw.
//!
//! The bridge decodes Java's transport records into these types; the world
//! renderer and the shader pack consume them. This layer holds data and wire
//! vocabulary only: no GAL objects and no rendering decisions.

pub mod background;
pub mod lod;
pub mod material;
pub mod mesh;
pub mod overlays;
pub mod strata;
pub mod textures;
pub mod voxel_source;

/// Maximum viewport axis admitted by semantic frame and GUI submissions.
/// Keeping this finite prevents hostile FFI dimensions from driving unbounded
/// staging, attachment, or uniform allocations.
pub const SEMANTIC_MAX_VIEWPORT_AXIS: i32 = 16_384;

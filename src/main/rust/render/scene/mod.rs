//! The renderer-facing description of what to draw.
//!
//! The bridge decodes Java's transport records into these types; the world
//! renderer and the shader pack consume them. This layer holds data and wire
//! vocabulary only: no GAL objects and no rendering decisions.

pub mod material;
pub mod mesh;
pub mod strata;
pub mod voxel_source;

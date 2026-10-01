//! Resources of the built-in route: line, sky disc, crack, border, material and mesh pipelines.

mod records;
mod outline;
mod sky;
mod overlays;
mod materials;
mod meshes;

pub(crate) use self::records::*;
pub(crate) use self::sky::*;

use super::*;

pub(crate) const WORLD_SHADER_COMPOSITE_UNIFORM_BYTES: u64 = TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES;

/// Two matrices plus the copied ARGB sky colour.  The top sky fan is static
/// Rust geometry; only these semantic frame values vary.
pub(crate) const WORLD_SKY_DISC_UNIFORM_BYTES: u64 = 44 * 4;

pub(crate) const CRACK_STAGE_COUNT: u32 = 10;

pub(crate) const CRACK_STAGE_SIZE: u32 = 16;

impl WorldPrimitiveFrontend {

}


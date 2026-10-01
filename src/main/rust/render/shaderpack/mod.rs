//! Data-driven shader programs: Iris/OptiFine shader packs and Minecraft's
//! own (vanilla and resource-pack) shaders.
//!
//! Layout, in dependency order:
//! - `source`: copied shader-source snapshots, manifest, preprocessing, dialect
//!   preflight and binary assets;
//! - `properties`: pack properties and directives (id maps, custom uniforms,
//!   held light, wetness, shadow policy);
//! - `contracts`: per-family semantic contracts of what a pack program reads
//!   and writes;
//! - `lowering`: rewriting selected sources into explicit, backend-neutral GLSL;
//! - `programs`: the program model, programs lowered from pack sources, and
//!   MattMC's built-in programs;
//! - `uniforms`, `plan`, `resources`, `voxels`: uniform semantics, the runtime
//!   plan and pass graph, GAL residency of pack resources, voxel volumes;
//! - `vanilla`: post-effect graphs, Mojang imports, engine Globals, the
//!   lightmap and Fabulous transparency targets;
//! - `runtime`: the executor that admits a pack's programs and records its
//!   frame graph.
//!
//! This module builds on `render::scene` and the VulkanicGAL's public API only;
//! it never names a backend or reaches into renderer internals.

pub mod contracts;
pub mod diagnostics;
pub mod lowering;
pub mod plan;
pub mod programs;
pub mod properties;
pub mod resources;
pub(crate) mod runtime;
pub mod source;
pub mod uniforms;
pub mod vanilla;
pub mod voxels;

#[cfg(test)]
mod tests;


//! Rust chunk meshing core.
//!
//! Production enters through the compact all-pass section snapshot path: Java
//! snapshots one section into stable ABI arrays, Rust scans that snapshot once,
//! and each emitted quad is routed to the solid, cutout, or translucent builder.
//! The legacy record-shaped entry points remain for benchmark and ABI coverage
//! only; production must not grow new dependencies on them.

use std::slice;
use std::sync::{Mutex, OnceLock};
use std::time::Instant;

use super::{index, translucent};

mod assembly;
mod builder;
mod cache;
mod constants;
mod culling;
mod ffi;
mod fluid;
mod format;
mod lighting;
mod model;
mod packing;
mod profile;
mod quad;
mod scan;
mod section;
mod tint;
mod types;
mod updates;

pub use ffi::*;
pub(crate) use updates::updated_quads_create_from_handles;

use assembly::*;
use builder::*;
use cache::*;
use constants::*;
use culling::*;
use fluid::{
    emit_native_section_fluid_faces, native_fluid_diag_enabled,
    section_builder_append_fluid_face_records_encoded,
};
use format::*;
use lighting::*;
use model::*;
use packing::*;
use profile::*;
use quad::*;
use scan::*;
#[cfg(test)]
use section::{CompactSectionSnapshot, NativeSectionRecordSource};
use tint::*;
use types::*;
use updates::*;

#[cfg(test)]
mod tests;

//! Native fluid face production facade.
//!
//! Fluid meshing preserves Java parity by keeping semantic visibility and height
//! rules separate from UV construction, face records, compact encoding, and the
//! compatibility record bridge. The modules below are deliberately internal:
//! Java-facing ABI symbols, snapshot layout, profile counters, and callback
//! fallback behavior are owned by the surrounding meshing layer.

use std::slice;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::OnceLock;
use std::time::Instant;

use super::*;

mod compatibility;
mod construction;
mod diagnostics;
mod encoding;
mod face;
mod height;
mod uv;
mod visibility;

#[allow(unused_imports)]
use {
    compatibility::*, construction::*, diagnostics::*, encoding::*, face::*, height::*, uv::*,
    visibility::*,
};

pub(super) use construction::emit_native_section_fluid_faces;
pub(super) use diagnostics::native_fluid_diag_enabled;
pub(super) use encoding::section_builder_append_fluid_face_records_encoded;

#[cfg(test)]
mod tests;

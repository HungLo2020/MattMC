//! Compatibility facade for the native chunk mesher.
//!
//! The implementation lives in `chunk::meshing`; this module remains so existing
//! Rust-internal references and Java native symbol wiring keep a stable home.

pub(crate) use super::meshing::updated_quads_create_from_handles;
pub use super::meshing::*;

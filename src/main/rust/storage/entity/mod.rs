//! Dev/test-only typed envelope decoder for entity region chunks.
//!
//! Rust owns only the generic entity-chunk envelope here: root data version,
//! root chunk position, root entity count, opaque per-entity NBT blobs, and
//! syntactic metadata useful for parity tests. Java remains authoritative for
//! DFU, registry resolution, entity construction, custom entity fields, and
//! passenger attachment behavior.

pub mod decoder;
pub mod encoder;
pub mod error;
pub mod ffi;
pub mod model;
pub mod tape;

#[cfg(test)]
mod tests;

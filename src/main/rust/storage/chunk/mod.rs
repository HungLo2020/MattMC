//! Dev/test-only typed chunk-section decoding.
//!
//! This module reads current-version chunk NBT through the existing Rust
//! region/NBT stack and returns one coarse typed tape containing simple chunk
//! metadata, section palette/storage data, light arrays, and heightmaps. It is
//! intentionally not used by production chunk loading yet; Java still owns
//! DFU, registry resolution, chunk object construction, block entities, ticks,
//! structures, and all mod-facing codec behavior.

pub mod decoder;
pub mod error;
pub mod ffi;
pub mod model;
pub mod tape;

#[cfg(test)]
mod tests;

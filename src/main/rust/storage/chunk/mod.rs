//! Typed chunk-section storage support.
//!
//! Production reads use the existing Rust region/NBT stack to parse a
//! current-version chunk once and return one coarse typed tape containing simple
//! chunk metadata, section palette/storage data, light arrays, heightmaps, and
//! residual Java-owned NBT. Production writes for supported current-version
//! chunks use the matching typed tape: Java supplies packed section data plus
//! residual NBT, and Rust merges, encodes, compresses, and writes one complete
//! chunk payload. Java still owns DFU, registry resolution, chunk object
//! construction, block entities, ticks, structures, and mod-facing codec
//! behavior.

pub mod decoder;
pub mod error;
pub mod ffi;
pub mod model;
pub mod tape;
pub mod writer;

#[cfg(test)]
mod tests;

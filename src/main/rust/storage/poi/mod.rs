//! Dev/test-only typed POI chunk decoding.
//!
//! Production POI loading still goes through Java `SectionStorage` and Mojang
//! codecs. This module decodes current-version POI region chunks into a compact
//! typed buffer so tests can compare Rust's direct interpretation against the
//! Java `PoiSection.Packed` codec without moving POI ownership yet.

pub mod decoder;
pub mod encoder;
pub mod error;
pub mod ffi;
pub mod model;
pub mod tape;

#[cfg(test)]
mod tests;

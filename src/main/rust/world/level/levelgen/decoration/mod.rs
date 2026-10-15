//! Native biome decoration: placed features whose modifiers and configured
//! feature have Rust implementations run here end to end, with Java's draws,
//! reads and writes in Java's order. Java keeps the step/feature loop, the
//! choice of route per placed feature, structures and every other feature.
mod ffi;
pub(crate) use ffi::{publish_events, BROKEN_BINDING, CALLBACK_FAILED, INVALID, JAVA_ROUTE, PANIC, PLACED, UNAVAILABLE_CHUNK};
pub(crate) mod ore;
pub(crate) mod placement;
pub(crate) mod program;
pub(crate) mod random;
pub(crate) mod region;
pub(crate) mod write;
pub(crate) mod facts;
pub(crate) mod simple;
pub(crate) mod tree;
pub(crate) mod java_set;
pub(crate) mod vegetation;
pub(crate) mod sequence;
pub(crate) mod geode;
pub(crate) mod rooms;
pub(crate) mod nether;
pub(crate) mod coral;
pub(crate) mod lakes;
pub(crate) mod sculk;
pub(crate) mod dripstone;
pub(crate) mod ice;
pub(crate) mod plants;
pub(crate) mod end;
mod end_ffi;
pub(crate) mod freeze;
pub(crate) mod patch;
pub(crate) mod template;
#[cfg(test)]
mod tests;

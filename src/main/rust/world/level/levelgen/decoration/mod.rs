//! Native biome decoration: placed features whose modifiers and configured
//! feature have Rust implementations run here end to end, with Java's draws,
//! reads and writes in Java's order. Java keeps the step/feature loop, the
//! choice of route per placed feature, structures and every other feature.
mod ffi;
pub(crate) mod ore;
pub(crate) mod placement;
pub(crate) mod program;
pub(crate) mod random;
pub(crate) mod region;
#[cfg(test)]
mod tests;

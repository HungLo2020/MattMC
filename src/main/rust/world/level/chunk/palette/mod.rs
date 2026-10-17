//! First-use identity compaction and exact padded palette packing.
mod ffi;
mod pack;
pub(crate) mod histogram;
pub(crate) mod distinct;
pub(crate) mod resize;
pub(crate) mod unpack;
pub(crate) mod saved;
mod validation;
pub(super) use validation::valid_indices;
#[cfg(test)]
mod tests;

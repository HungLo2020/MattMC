//! First-use identity compaction and exact padded palette packing.
mod ffi;
mod pack;
pub(crate) mod histogram;
pub(crate) mod resize;
pub(crate) mod unpack;
#[cfg(test)]
mod tests;

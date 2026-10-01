//! GAL residency of pack-owned resources: color targets, pack image assets and
//! the semantic bindings of lowered programs.

// Pack image-asset residency is candidate preparation state: compiled and
// unit-tested, but not yet admitted into the production submit path.
#[allow(dead_code)]
pub(crate) mod assets;
pub mod bindings;
pub mod color_targets;


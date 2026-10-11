//! Block and item models built from resource packs: model and blockstate
//! files, discovery and resolution, and baking into quads (`ModelManager`,
//! `ModelBakery`, `FaceBakery`). Java keeps the model objects its renderers
//! and gameplay use, built from these bakes, and item definitions.

pub mod bake;
pub mod blockstate;
pub mod ffi;
pub mod geometry;
pub mod job;
pub mod model;
pub mod resolve;

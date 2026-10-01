//! The GUI renderer: sprites, raw images, affine and tiled quads, 3D item
//! meshes, full item rasters, the panorama and GUI post effects, recorded into
//! GAL command lists.
//!
//! The bridge decodes GUI frames and asset updates and hands them to
//! `frontend::GuiFrontend`. A GUI frame is either submitted on its own or,
//! on the whole-frame route, appended by the world renderer to the frame it is
//! already recording, so world and GUI share one submission.
//!
//! Layering: this module depends on `render::{scene, shared, shaderpack}` and
//! the public GAL modules only. Stitched atlases are owned and animated by the
//! world renderer; the GUI reaches them only through
//! `atlas_reference::GuiAtlasOwner`, which the world renderer implements, so
//! `guirender` never names `worldrender`. Enforced by
//! `vulkanic/architecture_boundary.rs`.
//!
//! - `frontend`: per-frame state, request ordering, recording, resources,
//!   bundled sprites and post effects.
//! - `mesh`: semantic GUI meshes (item models, panorama), their validation,
//!   preparation and passes.
//! - `items`: item raster layout, flat item materials and raster targets.
//! - `atlas_reference`: immutable references into world-owned atlases.
//! - `tiling`: lowering of tiled quads into affine quads.

pub(crate) mod atlas_reference;
pub mod frontend;
pub(crate) mod items;
pub mod mesh;
pub(crate) mod tiling;

//! Resource bounds, draw strata and post-effect identifiers of the GUI frontend.

/// Maximum number of simultaneously staged dynamic GUI images. This mirrors
/// the Java semantic asset collector and keeps the Rust-owned image map
/// bounded independently of the generic FFI batch limit.
pub(crate) const GUI_MAX_RAW_IMAGES: usize = 4_096;

/// Matches Java's copied GUI-image admission bound before any Rust-owned
/// pixel buffer is retained.
pub(crate) const GUI_MAX_RAW_IMAGE_PIXELS: usize = 16 * 1024 * 1024;

/// Aggregate raw-image bytes retained by one generation. This bounds the
/// replacement map independently of the per-image pixel limit.
pub(crate) const GUI_MAX_RAW_IMAGE_BYTES_TOTAL: usize = 256 * 1024 * 1024;

/// Maximum semantic GUI viewport axis accepted by the Rust frontend.
pub(crate) const GUI_MAX_VIEWPORT_AXIS: i32 = crate::render::scene::SEMANTIC_MAX_VIEWPORT_AXIS;

pub const GUI_MAX_PACKED_SPRITES: usize = 256;

/// Hard cap for one semantic GUI submission. Java's coordinator enforces the
/// same ceiling, but the Rust frontend must reject direct FFI callers too.
pub(crate) const GUI_MAX_MESH_BATCHES: usize = 16_384;

pub(crate) const GUI_POST_EFFECT_INVERT_ID: u32 = 92;

pub(crate) const GUI_POST_EFFECT_CREEPER_ID: u32 = 93;

pub(crate) const GUI_POST_EFFECT_SPIDER_ID: u32 = 94;

pub(super) const GUI_OPAQUE_BLIT_STRATUM: u32 = 760;

pub(super) const GUI_VIGNETTE_BLIT_STRATUM: u32 = 770;

pub(super) const GUI_INVERT_RECTANGLE_STRATUM: u32 = 780;

// Matches GuiRenderStratum.GUI_CROSSHAIR. The affine request keeps its
// scheduler order separately, while this semantic stratum selects the exact
// invert blend used by vanilla's CROSSHAIR pipeline.
pub(super) const GUI_CROSSHAIR_INVERT_STRATUM: u32 = 200;

pub(super) const GUI_PREMULTIPLIED_BLIT_STRATUM: u32 = 790;

pub(super) const GUI_ADDITIVE_BLIT_STRATUM: u32 = 795;

pub(super) const GUI_LEQUAL_DEPTH_BLIT_STRATUM: u32 = 805;

pub(super) const GUI_UNIFORM_BYTES: usize = 96;

pub(super) const GUI_PACKED_UNIFORM_BYTES: u64 = (GUI_MAX_PACKED_SPRITES * GUI_UNIFORM_BYTES) as u64;

pub(crate) const MAX_CUSTOM_POST_EFFECT_PASSES: usize = 10;

pub(super) const MAX_CUSTOM_POST_EFFECT_INTERMEDIATES: usize = 4;

pub(super) const MAX_CUSTOM_POST_EFFECT_UNIFORM_BYTES: usize = 1024 * 1024;

pub(super) const MAX_CUSTOM_POST_EFFECT_UNIFORM_GRAPH_BYTES: usize = 2 * 1024 * 1024;

/// A mesh raster owns a complete pipeline/resource-set family. Bound the
/// cross-product of image, material, lighting, and extent variants before it
/// can turn a dynamic GUI stream into unbounded GAL residency.
pub(super) const GUI_MAX_MESH_RASTER_RESOURCES: usize = 4_096;

/// Immutable mesh programs are keyed only by their explicit raster contract;
/// asset-local buffers and resource sets are deliberately excluded.
pub(super) const GUI_MAX_MESH_SHARED_PROGRAMS: usize = 16;

/// Composite resources are keyed by target extent/format and likewise retain
/// explicit pipelines and uniform storage until the GUI generation changes.
pub(super) const GUI_MAX_MESH_COMPOSITE_RESOURCES: usize = 256;

pub(super) const GUI_MAX_SHARED_PIPELINES: usize = 16;

/// Includes ordinary affine requests and all expanded tiled children.
pub(crate) const GUI_MAX_EXPANDED_AFFINE_QUADS: usize = 65_536;

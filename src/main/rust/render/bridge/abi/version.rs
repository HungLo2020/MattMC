//! ABI version history and the ABI name Java checks at load.

pub const FFI_ABI_V1_VERSION: u32 = 1;

pub const FFI_ABI_V2_VERSION: u32 = 2;

pub const FFI_ABI_V3_VERSION: u32 = 3;

pub const FFI_ABI_V4_VERSION: u32 = 4;

pub const FFI_ABI_V5_VERSION: u32 = 5;

pub const FFI_ABI_V6_VERSION: u32 = 6;

pub const FFI_ABI_V7_VERSION: u32 = 7;

pub const FFI_ABI_V8_VERSION: u32 = 8;

pub const FFI_ABI_V9_VERSION: u32 = 9;

pub const FFI_ABI_V10_VERSION: u32 = 10;

pub const FFI_ABI_V11_VERSION: u32 = 11;

pub const FFI_ABI_V12_VERSION: u32 = 12;

pub const FFI_ABI_V13_VERSION: u32 = 13;

pub const FFI_ABI_V14_VERSION: u32 = 14;

pub const FFI_ABI_V15_VERSION: u32 = 15;

pub const FFI_ABI_V16_VERSION: u32 = 16;

pub const FFI_ABI_V17_VERSION: u32 = 17;

pub const FFI_ABI_V18_VERSION: u32 = 18;

pub const FFI_ABI_V19_VERSION: u32 = 19;

pub const FFI_ABI_V20_VERSION: u32 = 20;

pub const FFI_ABI_V21_VERSION: u32 = 21;

/// v22 appends a dedicated first-person mesh stream to the whole-frame
/// request. It reuses the stable indexed-mesh record layout, but keeps the
/// hand depth/projection domain separate from ordinary world mesh instances.
pub const FFI_ABI_V22_VERSION: u32 = 22;

/// v23 appends the first-person model-view matrix. A hand source requires a
/// distinct transform domain in addition to its projection and cleared depth
/// domain; it must never inherit camera-space world matrices implicitly.
pub const FFI_ABI_V23_VERSION: u32 = 23;

/// v24 appends the semantic entity-outline color to mesh instances. Existing
/// offsets remain stable; zero means no outline request.
pub const FFI_ABI_V24_VERSION: u32 = 24;

/// v25 appends the bounded semantic post-effect identity to whole-frame
/// submission; Rust resolves its copied graph and shader stages.
pub const FFI_ABI_V25_VERSION: u32 = 25;

/// v26 appends explicit world-mesh retirement records to incremental asset
/// updates. Streaming producers must be able to release their Rust-owned
/// mesh resources without smuggling a renderer reset through an empty frame.
pub const FFI_ABI_V26_VERSION: u32 = 26;

/// v27 appends copied per-level atlas PNGs to mesh texture assets. This lets
/// Rust upload Frozen's sprite-isolated mip chain without reading a Java GPU
/// texture or generating whole-atlas mips that bleed across sprite borders.
pub const FFI_ABI_V27_VERSION: u32 = 27;

/// v28 separates fractional GUI projection from rounded layout bounds.
pub const FFI_ABI_V28_VERSION: u32 = 28;

/// v29 appends typed tiled GUI commands, keeping geometry expansion in Rust.
pub const FFI_ABI_V29_VERSION: u32 = 29;

/// v30 adds typed immutable atlas animation declarations (private staging only).
pub const FFI_ABI_V30_VERSION: u32 = 30;

/// v31 appends backend-neutral texture filter and address descriptors.
pub const FFI_ABI_V31_VERSION: u32 = 31;

/// v32 appends copied per-frame engine Globals inputs.
pub const FFI_ABI_V32_VERSION: u32 = 32;

/// v33 appends the explicit resource mip-level count to copied mesh textures.
pub const FFI_ABI_V33_VERSION: u32 = 33;

/// v34 appends an explicit affine GUI material designation.
pub const FFI_ABI_V34_VERSION: u32 = 34;

pub const FFI_ABI_V35_VERSION: u32 = 35;

pub const FFI_ABI_V36_VERSION: u32 = 36;

pub const FFI_ABI_V37_VERSION: u32 = 37;

pub const FFI_ABI_V38_VERSION: u32 = 38;

pub const FFI_ABI_V39_VERSION: u32 = 39;

/// v40 appends standard GUI item foil clock/speed/strength semantics.
pub const FFI_ABI_V40_VERSION: u32 = 40;

/// v41 carries semantic flat-item GUI scale instead of a caller raster extent.
pub const FFI_ABI_V41_VERSION: u32 = 41;

/// v42 appends standard world/held item foil semantics to mesh instances.
pub const FFI_ABI_V42_VERSION: u32 = 42;

/// v64 appends copied per-instance vanilla UV2 light for stable entity-model
/// meshes. The field is ignored unless the explicit instance-light flag is set.
/// v53 adds immutable orb appearance assets, lowered to geometry only in Rust.
/// v54 adds semantic orb placement to the ordered entity mesh stream.
/// v58 adds explicit equal-depth/write semantics for entity mesh layers.
/// v67 appends the copied user shadow-distance setting to shader frames and
/// expands the layout-query offset table to 72 fields (312 bytes).
/// v68 appends bounded copied entity culling bounds and extraction roles.
/// v69 appends compact off-camera static-terrain shadow casters and the
/// frame's terrain camera; Rust expands them into shadow-only instances.
/// v70 appends compact camera-visible static-terrain sections; Java no
/// longer sends a per-section terrain instance record.
/// v71 appends model-rig part poses: Java sends one instance per entity model
/// and Rust expands the registered part hierarchy.
/// v72 appends retained DH generic group instances: group boxes are
/// registered once and each frame sends only the groups' origins.
/// v73 adds indexed map-color CPU image input, expanded only by the Rust frontend.
pub const FFI_ABI_VERSION: u32 = 73;

pub const FFI_INITIAL_PRESENTATION_SUPPORTED: bool = false;

pub const FFI_ABI_NAME: &str = "MattMC VulkanicGAL Java-Rust batch ABI";

//! Distant Horizons LOD wire vocabulary: frame flags, layers, material
//! variants and the vertex layout version.

/// Stable semantic layout decoded from Distant Horizons' CPU LOD builder.
/// This is intentionally separate from `WorldMeshVertex`: DH has vertex
/// color/material/light semantics but no Minecraft atlas UV ownership.
pub const WORLD_LOD_VERTEX_LAYOUT_V1: u32 = 1;
pub const WORLD_LOD_MATERIAL_UNAVAILABLE: u32 = 0;
pub const WORLD_LOD_MATERIAL_MIXED: u32 = u32::MAX;
pub const WORLD_LOD_VARIANT_UNAVAILABLE: u8 = 0;
pub const WORLD_LOD_VARIANT_EXACT: u8 = 1;
pub const WORLD_LOD_VARIANT_MIXED: u8 = 2;
pub const WORLD_LOD_LAYER_OPAQUE: u32 = 1;
pub const WORLD_LOD_LAYER_TRANSPARENT_SIDE: u32 = 2;
pub const WORLD_LOD_LAYER_TRANSPARENT_UP: u32 = 3;
pub const WORLD_LOD_LAYER_TRANSPARENT_WATER_UP: u32 = 4;
/// The Java-side DH preflight selected this exact non-water frame for the Rust
/// whole-frame route. Capture-only LOD semantics deliberately leave this clear,
/// so observing Java/DH geometry cannot accidentally create a second renderer.
pub const WORLD_LOD_FLAG_RUST_ROUTE_SELECTED: u32 = 1 << 4;
/// Compatibility name for existing semantic transport users. The bit and ABI
/// are unchanged; it now admits opaque plus explicitly ordered non-water
/// transparent DH work.
pub const WORLD_LOD_FLAG_RUST_OPAQUE_ROUTE_SELECTED: u32 = WORLD_LOD_FLAG_RUST_ROUTE_SELECTED;
/// Copied DH vanilla-transition policy. NONE leaves the direct sparse
/// compositor at its existing boundary; one bit selects SINGLE or DOUBLE,
/// while both bits encode DH's LOD-only replacement at both source callsites.
pub const WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS: u32 = 1 << 5;
pub const WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS: u32 = 1 << 6;
/// Frozen's DH far-clip fade is applied before the copied vanilla transition.
/// The bit is semantic policy only; Rust owns the private color/depth pass.
pub const WORLD_LOD_FLAG_DH_FAR_CLIP_FADE: u32 = 1 << 7;

//! GLSL sources of the built-in programs (files under `glsl/`).

pub const MINIMAL_TERRAIN_MATERIAL_VERTEX: &str = include_str!("glsl/minimal_terrain_material_vertex.glsl");

/// Entity outline mask using the copied mesh/instance ABI. Texture alpha
/// defines the silhouette; the semantic outline color replaces surface RGB.
pub const MINIMAL_ENTITY_OUTLINE_VERTEX: &str = include_str!("glsl/minimal_entity_outline_vertex.glsl");

/// Vanilla discards only exactly transparent texture samples. Nonzero alpha
/// contributes the semantic outline color, with no material lighting or fog.
pub const MINIMAL_ENTITY_OUTLINE_FRAGMENT: &str = include_str!("glsl/minimal_entity_outline_fragment.glsl");

/// Fragment contract for an optical stencil-write draw.  Zero alpha with
/// alpha blending leaves the copied optical color untouched while the
/// explicit GAL stencil replace operation records the aperture value.
pub const MINIMAL_OPTICAL_STENCIL_WRITE_FRAGMENT: &str = include_str!("glsl/minimal_optical_stencil_write_fragment.glsl");

/// Vulkan-native fullscreen contracts for the bundled entity-outline chain.
/// They keep the vanilla effect's semantics while making descriptor bindings
/// explicit and avoiding a Java post-chain or implicit sampler lookup.
pub const MINIMAL_ENTITY_OUTLINE_FULLSCREEN_VERTEX: &str = include_str!("glsl/minimal_entity_outline_fullscreen_vertex.glsl");

/// Rust-owned fullscreen vertex contract for the ordinary Distant Horizons
/// color/depth composition boundary.  This is deliberately independent of
/// shader-pack fullscreen stages: the direct vanilla DH route owns both
/// sampled images and its fog semantic block.
pub const MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_VERTEX: &str = include_str!("glsl/minimal_distant_horizons_direct_composite_vertex.glsl");

/// Rust-owned DH SSAO producer. It mirrors Frozen's spiral depth test while
/// consuming only the private Rust DH depth attachment and copied frame
/// matrices/configuration. The result is an owned sampled AO image used by
/// the direct compositor; no Java framebuffer or GL texture is involved.
pub const MINIMAL_DISTANT_HORIZONS_SSAO_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_ssao_fragment.glsl");

/// Applies the copied DH fog contract after the direct LOD color/depth pass.
/// The depth image is reconstructed through the copied combined matrix rather
/// than borrowing a Java framebuffer or relying on the main terrain depth.
pub const MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_direct_composite_fragment.glsl");

/// Sparse equivalent of Frozen's DhApplyShader. The resolved DH color is
/// copied into the main target before vanilla opaque terrain, while uncovered
/// pixels leave the existing sky/background untouched. Frozen's fullscreen
/// apply disables depth testing and does not write the sampled DH depth.
pub const MINIMAL_DISTANT_HORIZONS_DIRECT_APPLY_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_direct_apply_fragment.glsl");

/// Equivalent of Frozen's VanillaFadeShader. `VanillaColorTexture` is a
/// snapshot taken at the particular opaque or transparent boundary, while
/// `DhResolvedColorTexture` is the immutable DH result produced before
/// vanilla terrain began.
pub const MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_direct_fade_fragment.glsl");

pub const MINIMAL_ENTITY_OUTLINE_SOBEL_FRAGMENT: &str = include_str!("glsl/minimal_entity_outline_sobel_fragment.glsl");

pub const MINIMAL_ENTITY_OUTLINE_BLUR_FRAGMENT: &str = include_str!("glsl/minimal_entity_outline_blur_fragment.glsl");

pub const MINIMAL_ENTITY_OUTLINE_BLIT_FRAGMENT: &str = include_str!("glsl/minimal_entity_outline_blit_fragment.glsl");

/// Rust-owned DH vertex contract. The private 16-byte record retains signed
/// i16 position, signed micro-offset states, color, light, material, and face;
/// it is not DH's legacy GL vertex format. `LightmapTexture` and
/// `LightmapSampler` are a separately bound Rust-owned 16x16 lightmap.
pub const MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_VERTEX: &str = include_str!("glsl/minimal_distant_horizons_lod_opaque_vertex.glsl");

/// Private Rust vertex stream for the exact-atlas DH subset. Its 56-byte
/// layout is owned by the world frontend and is not DH's legacy GL format.
pub const MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_VERTEX: &str = include_str!("glsl/minimal_distant_horizons_lod_exact_atlas_opaque_vertex.glsl");

pub const MINIMAL_DISTANT_HORIZONS_LOD_OPAQUE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_lod_opaque_fragment.glsl");

pub const MINIMAL_DISTANT_HORIZONS_LOD_FORWARD_OPAQUE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_lod_forward_opaque_fragment.glsl");

pub const MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_OPAQUE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_lod_exact_atlas_opaque_fragment.glsl");

pub const MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_FORWARD_OPAQUE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_lod_exact_atlas_forward_opaque_fragment.glsl");

/// The provenance-resolved exact-atlas source writer owns DH's one declared
/// lit-color output. Its target schema intentionally matches the selected
/// `dh_terrain` contract rather than ordinary terrain's G-buffer outputs.
pub const MINIMAL_DISTANT_HORIZONS_LOD_EXACT_ATLAS_SOURCE_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_lod_exact_atlas_source_fragment.glsl");

/// The non-water transparent pass is composited after deferred lighting. It
/// keeps DH's source-visible alpha and lightmap result intact, with blend and
/// depth-write policy declared by the backend-neutral graphics pipeline.
pub const MINIMAL_DISTANT_HORIZONS_LOD_TRANSPARENT_FRAGMENT: &str = include_str!("glsl/minimal_distant_horizons_lod_transparent_fragment.glsl");

pub const MINIMAL_TERRAIN_MATERIAL_FRAGMENT: &str = include_str!("glsl/minimal_terrain_material_fragment.glsl");

/// Rust-owned lowering of the stable normal-terrain expressions shared by the
/// audited Complementary source: atlas sample, alpha discard, tint/AO, light
/// map contribution, directional shade, and named G-buffer outputs. Richer
/// branches are admitted only after their semantic inputs have implementations.
pub const COMPLEMENTARY_TERRAIN_SUBSET_FRAGMENT: &str = include_str!("glsl/complementary_terrain_subset_fragment.glsl");

pub(crate) const STANDARD_ITEM_FOIL_FRAGMENT: &str = include_str!("glsl/standard_item_foil_fragment.glsl");

pub const MINIMAL_TERRAIN_MATERIAL_FRAGMENT_DIRECT: &str = include_str!("glsl/minimal_terrain_material_fragment_direct.glsl");

pub const MINIMAL_G_BUFFER_COMPOSITE_VERTEX: &str = MINIMAL_FULLSCREEN_VERTEX;

pub const MINIMAL_FULLSCREEN_VERTEX: &str = include_str!("glsl/minimal_fullscreen_vertex.glsl");

pub const MINIMAL_G_BUFFER_COMPOSITE_FRAGMENT: &str = MINIMAL_DEFERRED_LIGHTING_FRAGMENT;

pub const MINIMAL_DEFERRED_LIGHTING_FRAGMENT: &str = include_str!("glsl/minimal_deferred_lighting_fragment.glsl");

pub const MINIMAL_COMPOSITE_COLOR_GRADE_FRAGMENT: &str = include_str!("glsl/minimal_composite_color_grade_fragment.glsl");

pub const MINIMAL_COMPOSITE_DEPTH_FOG_FRAGMENT: &str = include_str!("glsl/minimal_composite_depth_fog_fragment.glsl");

pub const MINIMAL_FINAL_COPY_FRAGMENT: &str = include_str!("glsl/minimal_final_copy_fragment.glsl");

pub const MINIMAL_SHADOW_DEPTH_VERTEX: &str = include_str!("glsl/minimal_shadow_depth_vertex.glsl");

pub const MINIMAL_SHADOW_DEPTH_FRAGMENT: &str = include_str!("glsl/minimal_shadow_depth_fragment.glsl");

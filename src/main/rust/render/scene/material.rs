//! World material vocabulary: material modes, built-in material ids, depth
//! policies, source families of copied material quads and the UV spaces their
//! coordinates address. Wire values shared with the Java transport.

/// The legacy/direct material path has no source-pack semantic interface.
/// Such work remains outside selected-source admission.
pub const WORLD_MATERIAL_SOURCE_UNSPECIFIED: u32 = 0;
/// Generic Minecraft textured-material semantic. It is deliberately not a
/// producer name: particles, markers, and later material users can share a
/// source program only after their complete interface is admitted.
pub const WORLD_MATERIAL_SOURCE_TEXTURED: u32 = 1;
/// Copied entity-model quads using the explicit textured-material interface.
pub const WORLD_MATERIAL_SOURCE_ENTITY_MODEL: u32 = 5;
/// Semantic weather-material source family. It uses the same compact quad
/// transport as generic material, but a distinct selected source pass and
/// source-derived blend contract.
pub const WORLD_MATERIAL_SOURCE_WEATHER: u32 = 2;
/// Semantic vanilla cloud-face source family. It shares the compact material
/// transport, but source-plan admission remains separate until a complete
/// cloud pass contract is available.
pub const WORLD_MATERIAL_SOURCE_CLOUDS: u32 = 3;
/// Particle quads use the admitted textured shader ABI but retain producer identity.
pub const WORLD_MATERIAL_SOURCE_PARTICLES: u32 = 4;
/// Source UVs address the Rust-owned standalone material texture.
pub const WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE: u32 = 0;
/// Source UVs retain their original coordinates in the copied Minecraft block
/// atlas. This is semantic texture-coordinate metadata, never an atlas object
/// or backend texture identity.
pub const WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS: u32 = 1;

pub const WORLD_DEPTH_POLICY_DISABLED: u32 = 0;
pub const WORLD_DEPTH_POLICY_TEST_WRITE: u32 = 1;
pub const WORLD_DEPTH_POLICY_TEST_NO_WRITE: u32 = 2;
/// Coincident model layers such as decal armor trims preserve depth writes.
pub const WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE: u32 = 3;
pub const WORLD_MATERIAL_MODE_OPAQUE: u32 = 1;
pub const WORLD_MATERIAL_MODE_CUTOUT: u32 = 2;
pub const WORLD_MATERIAL_MODE_TRANSLUCENT: u32 = 3;
/// Vanilla item foil is a distinct direct-world material. It intentionally is
/// not admitted to selected source-pack terrain/entity programs.
pub const WORLD_MATERIAL_MODE_GLINT: u32 = 4;
/// First-person optical aperture mask. These modes are private to the Rust
/// hand target and are never admitted into ordinary world or source-pack
/// terrain batches.
pub const WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE: u32 = 5;
pub const WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST: u32 = 6;
/// Blended model texture with a fixed 0.1 alpha-discard threshold. Unlike
/// Sodium translucent terrain, transparent texels must not write depth.
pub const WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT: u32 = 7;
pub const WORLD_MATERIAL_ID_OPAQUE_TEXTURED: u32 = 0x6a2f_d335;
pub const WORLD_MATERIAL_ID_CUTOUT_TEXTURED: u32 = 0x129b_1b90;
pub const WORLD_MATERIAL_ID_MODEL_CUTOUT_TEXTURED: u32 = 0x4d43_4f31;
pub const WORLD_MATERIAL_ID_MODEL_CRUMBLING: u32 = 0x4352_4d42;
pub const WORLD_MATERIAL_ID_PER_FACE_MODEL_CUTOUT_TEXTURED: u32 = 0x5046_4331;
pub const WORLD_MATERIAL_ID_PER_FACE_TRANSLUCENT_CUTOUT_TEXTURED: u32 = 0x5046_5431;
pub const WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED: u32 = 0x4d21_a7c3;
pub const WORLD_MATERIAL_ID_TRANSLUCENT_CUTOUT_TEXTURED: u32 = 0x5443_5554;
pub const WORLD_MATERIAL_ID_ENTITY_SHADOW: u32 = 0x5348_444d;
pub const WORLD_MATERIAL_ID_SKY_STARS: u32 = 0x5354_4152;
pub const WORLD_MATERIAL_ID_CELESTIAL: u32 = 0x4345_4c45;
pub const WORLD_MATERIAL_ID_GLINT_TEXTURED: u32 = 0x71e6_a9b4;
/// Vanilla `energy_swirl`: alpha-cutout entity texture, fullbright, additive.
pub const WORLD_MATERIAL_ID_ENERGY_SWIRL: u32 = 0x4553_574c;
/// Vanilla entity eyes: alpha blended, fullbright, and without cardinal lighting.
pub const WORLD_MATERIAL_ID_MODEL_EYES: u32 = 0x4559_4553;
/// Vanilla translucent emissive entity overlay: fullbright with cardinal model lighting.
pub const WORLD_MATERIAL_ID_MODEL_TRANSLUCENT_EMISSIVE: u32 = 0x4d45_4d31;
/// Vanilla Breeze wind: lightmapped scrolling translucent cutout without cardinal lighting.
pub const WORLD_MATERIAL_ID_MODEL_BREEZE_WIND: u32 = 0x4257_5f44;
/// Textureless boat water patch: writes depth without changing the color attachment.
pub const WORLD_MATERIAL_ID_BOAT_WATER_MASK: u32 = 0x4257_4d4b;
pub const WORLD_MATERIAL_ID_WATER_TRANSLUCENT: u32 = 0x39e0_a7e4;
pub const WORLD_MATERIAL_ID_BLOCK_MARKER_CUTOUT: u32 = 0x224a_8659;
pub const WORLD_MATERIAL_ID_DEFAULT_OPAQUE: u32 = WORLD_MATERIAL_ID_OPAQUE_TEXTURED;
pub const WORLD_MATERIAL_ID_DEFAULT_CUTOUT: u32 = WORLD_MATERIAL_ID_CUTOUT_TEXTURED;

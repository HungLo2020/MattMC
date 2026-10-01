//! Material source families of copied world material quads and the UV spaces
//! their coordinates address. Wire values shared with the Java transport.

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

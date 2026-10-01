//! Stable texture identities of copied world materials and mesh atlases
//! (FNV-style ids shared with the Java transport).

pub const WORLD_MATERIAL_TEXTURE_STONE: u32 = 0x21df_896f;
pub const WORLD_MATERIAL_TEXTURE_DIRT: u32 = 0x0b0b_bd25;
pub const WORLD_MATERIAL_TEXTURE_OAK_LEAVES: u32 = 0x7232_1ec7;
pub const WORLD_MATERIAL_TEXTURE_DEEPSLATE: u32 = 0x715d_8d65;
pub const WORLD_MATERIAL_TEXTURE_WHITE_WOOL: u32 = 0x2253_a2ef;
pub const WORLD_MATERIAL_TEXTURE_PARTICLE_ATLAS: u32 = 0x5041_5254;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_BARRIER: u32 = 0x447d_596a;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_00: u32 = 0x665d_a7aa;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_01: u32 = 0x50e8_8e0f;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_02: u32 = 0x079e_2b74;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_03: u32 = 0x4a7c_2b71;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_04: u32 = 0x35e9_0ae6;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_05: u32 = 0x2f21_fecb;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_06: u32 = 0x2a27_abf0;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_07: u32 = 0x0ea4_c92d;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_08: u32 = 0x4473_cce2;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_09: u32 = 0x0ab5_51c7;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_10: u32 = 0x7a25_0241;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_11: u32 = 0x1f43_9384;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_12: u32 = 0x4bab_8f5f;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_13: u32 = 0x4316_88fa;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_14: u32 = 0x0b2b_dbbd;
pub const WORLD_MATERIAL_TEXTURE_BLOCK_MARKER_LIGHT_15: u32 = 0x0194_76c0;
pub const WORLD_MATERIAL_TEXTURE_WATER_STILL: u32 = 0x5a71_a501;
pub const WORLD_MATERIAL_TEXTURE_WATER_FLOW: u32 = 0x5a71_a502;
pub const WORLD_MATERIAL_TEXTURE_WATER_OVERLAY: u32 = 0x5a71_a503;
pub const WORLD_MATERIAL_TEXTURE_WEATHER_RAIN: u32 = 0x3c49_7a11;
pub const WORLD_MATERIAL_TEXTURE_WEATHER_SNOW: u32 = 0x74b5_2e96;
/// Stable semantic identities for copied vanilla celestial textures. They are
/// resource-pack bytes owned by Rust after upload, never Java texture-manager
/// entries or backend handles. No source route may bind them until it owns the
/// complete selected `gbuffers_skytextured` contract.
pub const WORLD_MATERIAL_TEXTURE_SKY_SUN: u32 = 0x534b_5901;
pub const WORLD_MATERIAL_TEXTURE_SKY_MOON_PHASES: u32 = 0x534b_5902;
/// Copied vanilla experience-orb sprite sheet used by the generic translucent
/// world-material path. This is a stable semantic key, never an atlas or
/// backend texture handle.
pub const WORLD_MATERIAL_TEXTURE_EXPERIENCE_ORB: u32 = 0x4f52_4233;
/// Copied vanilla beacon-beam sheet used by the generic translucent
/// world-material path. This semantic key is never a Java texture object or
/// backend handle.
pub const WORLD_MATERIAL_TEXTURE_BEACON_BEAM: u32 = 0x4245_414d;
/// Copied vanilla End Crystal beam sheet used by the explicit translucent
/// crystal-beam primitive. This is a semantic resource key, never a Java
/// texture-manager entry or backend handle.
pub const WORLD_MATERIAL_TEXTURE_CRYSTAL_BEAM: u32 = 0x4352_424d;
/// Copied vanilla End Gateway beam sheet used by the same explicit
/// translucent beam geometry with a distinct semantic texture identity.
pub const WORLD_MATERIAL_TEXTURE_END_GATEWAY_BEAM: u32 = 0x4547_424d;
/// Copied vanilla End Portal base sky texture used by the explicit portal cube.
pub const WORLD_MATERIAL_TEXTURE_END_SKY: u32 = 0x454e_4453;
/// Copied vanilla End flash texture used by the explicit End sky overlay.
pub const WORLD_MATERIAL_TEXTURE_END_FLASH: u32 = 0x454e_4446;
/// Copied vanilla End Portal animated layer texture.
pub const WORLD_MATERIAL_TEXTURE_END_PORTAL: u32 = 0x454e_4450;
/// Vanilla entity-shadow coverage texture. This is a semantic resource key,
/// never a Java texture-manager entry or backend handle.
pub const WORLD_MATERIAL_TEXTURE_ENTITY_SHADOW: u32 = 0x5348_4457;
/// Generated white is a Rust-owned semantic texture for producers whose
/// appearance is entirely carried by copied vertex color. It is not a Java
/// texture, atlas entry, or backend handle.
pub const WORLD_MATERIAL_TEXTURE_GENERATED_WHITE: u32 = 0x4e2a_16c1;
/// Stable semantic identity of Minecraft's terrain atlas. It is copied as an
/// owned mesh texture asset; this is neither a Java atlas object nor a native
/// backend texture handle.
pub const WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS: u32 = 0x54a1_7a1a;
/// Stable semantic identity for the copied resource-pack specular atlas. It
/// shares Minecraft atlas placement with the albedo atlas, but stays a
/// separate Rust-owned resource because shader packs attach different meaning
/// and sampling policy to its pixels.
pub const WORLD_MESH_TEXTURE_TERRAIN_BLOCK_SPECULAR_ATLAS: u32 = 0x54a1_7a1b;
/// Stable semantic identity for the copied resource-pack normal atlas. It
/// shares atlas placement but never resource identity with albedo or
/// specular, so source material semantics cannot alias their texture data.
pub const WORLD_MESH_TEXTURE_TERRAIN_BLOCK_NORMAL_ATLAS: u32 = 0x54a1_7a1c;
pub const WORLD_MATERIAL_TEXTURE_DEFAULT: u32 = WORLD_MATERIAL_TEXTURE_STONE;

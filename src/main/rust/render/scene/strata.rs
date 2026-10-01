//! Draw strata: the ordered layers a world frame's draws belong to. Values are
//! the Java transport's wire values and also the renderers' ordering keys.

pub const WORLD_STRATUM_TERRAIN: u32 = 60;
/// DH generic-object faces render into the private DH color/depth target.
pub const WORLD_STRATUM_DH_GENERIC: u32 = 61;
/// DH generic-object faces that participate in the private depth image before
/// the Rust-owned DH SSAO pass. This value is internal to Rust decoding; Java
/// can only request it through the compact generic-box SSAO semantic bit.
pub const WORLD_STRATUM_DH_GENERIC_SSAO: u32 = 62;
/// Generic copied entity-model mesh. The ordinary whole-frame frontend can
/// batch it with other indexed meshes, while source-plan admission remains
/// explicit until a selected shader profile has an entity material writer.
pub const WORLD_STRATUM_ENTITY_MESH: u32 = 67;
pub const WORLD_STRATUM_MOVING_MESH: u32 = 68;
/// Shadow-only entity caster (the first-person local player in Iris's shadow
/// pass). Never drawn by a camera writer; only the source shadow pass may
/// admit it, per the pack's resolved shadow caster directives.
pub const WORLD_STRATUM_ENTITY_SHADOW_CASTER: u32 = 69;
pub const WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY: u32 = 70;
pub const WORLD_STRATUM_ORDINARY_BLOCK: u32 = 71;
pub const WORLD_STRATUM_WORLD_BORDER: u32 = 80;
pub const WORLD_STRATUM_BLOCK_BREAKING_CRACK: u32 = 90;
pub const WORLD_STRATUM_BLOCK_OUTLINE: u32 = 100;

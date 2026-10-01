//! Block-selection line styles and world-border texture, cull and blend kinds.

/// Block-selection segment flag carried in `WorldLineSegmentRequest::style`:
/// the targeted block renders in the translucent chunk layer, so Iris draws
/// its outline after translucent terrain instead of before deferred.
pub const WORLD_LINE_STYLE_FLAG_TRANSLUCENT_TARGET: u32 = 0x100;
pub const WORLD_BORDER_TEXTURE_FORCEFIELD: u32 = 1;
pub const WORLD_BORDER_BLEND_OVERLAY: u32 = 1;
pub const WORLD_BORDER_CULL_NONE: u32 = 0;

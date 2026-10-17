//! Byte and item bounds on every wire payload the bridge accepts.

/// Fixed offset-table capacity mirrored by Java's layout-query allocation.
pub const FFI_MAX_STRUCT_LAYOUT_FIELDS: usize = 72;

pub const FFI_MAX_LABEL_BYTES: usize = 1024;

pub const FFI_MAX_SHADER_BYTES: usize = 16 * 1024 * 1024;

pub const FFI_MAX_INLINE_BYTES: usize = 64 * 1024 * 1024;

/// Copied GUI images are bounded independently from encoded sprite assets.
/// A 16-million-pixel RGBA semantic image is the largest Java producer may
/// publish, so the ABI must admit its 64 MiB byte representation intact.
pub const FFI_MAX_GUI_ASSET_BYTES: usize = 64 * 1024 * 1024;

/// Hard ceiling for one copied whole-frame semantic request.  This bounds the
/// transient FFI decode arena before any producer-owned vectors are expanded.
pub const FFI_MAX_WHOLE_FRAME_INPUT_BYTES: u64 = 512 * 1024 * 1024;

pub const FFI_MAX_WORLD_BORDER_ASSET_BYTES: usize = 2 * 1024 * 1024;

pub const FFI_MAX_WORLD_CRACK_ASSET_BYTES: usize = 4 * 1024 * 1024;

pub const FFI_MAX_WORLD_MATERIAL_ASSET_BYTES: usize = 4 * 1024 * 1024;

pub const FFI_MAX_WORLD_MESH_TEXTURE_ASSET_BYTES: usize = 4 * 1024 * 1024;

pub const FFI_MAX_WORLD_MESH_INDEX_BYTES: usize = 1024 * 1024;

pub const FFI_MAX_SHADER_PACK_SOURCE_FILES: usize = 4096;

pub const FFI_MAX_SHADER_PACK_SOURCE_FILE_BYTES: usize = 4 * 1024 * 1024;

pub const FFI_MAX_SHADER_PACK_SOURCE_TOTAL_BYTES: usize = 64 * 1024 * 1024;

pub const FFI_MAX_SHADER_PACK_ASSET_FILES: usize = 4096;

pub const FFI_MAX_SHADER_PACK_ASSET_FILE_BYTES: usize = 32 * 1024 * 1024;

pub const FFI_MAX_SHADER_PACK_ASSET_TOTAL_BYTES: usize = 256 * 1024 * 1024;

pub const FFI_MAX_BATCH_ITEMS: usize = 65_536;

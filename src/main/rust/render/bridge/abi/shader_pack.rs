//! Shader-pack source and asset update records.

use super::*;

/// One named, copied shader-pack source file. This is semantic pack input,
/// never a compiled shader, native resource, or backend object.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiShaderPackSourceFile {
    pub byte_size: u32,
    pub reserved0: u32,
    pub path_utf8: FfiBytes,
    pub contents_utf8: FfiBytes,
}

/// A complete source generation replaces the active owned source atomically.
/// It is deliberately separate from world mesh/material asset updates.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiShaderPackSourceUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub pack_name_utf8: FfiBytes,
    pub files: FfiSlice<FfiShaderPackSourceFile>,
}

/// One named, copied binary shader-pack asset. It carries pack-relative
/// semantic bytes only, never an atlas object, decoded texture, or backend
/// resource.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiShaderPackAssetFile {
    pub byte_size: u32,
    pub reserved0: u32,
    pub path_utf8: FfiBytes,
    pub contents: FfiBytes,
}

/// A complete binary asset generation paired with an active source
/// generation. Rust copies and validates it before any decode or backend
/// upload is considered.
#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiShaderPackAssetUpdateRequest {
    pub header: FfiHeader,
    pub generation: u64,
    pub pack_name_utf8: FfiBytes,
    pub files: FfiSlice<FfiShaderPackAssetFile>,
}

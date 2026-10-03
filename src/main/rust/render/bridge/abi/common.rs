//! Shared wire primitives: headers, byte slices, handles, ranges and status results.

use super::*;

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiStructLayout {
    pub header: FfiHeader,
    pub struct_id: u32,
    pub byte_size: u32,
    pub alignment: u32,
    pub field_count: u32,
    pub field_offsets: [u32; FFI_MAX_STRUCT_LAYOUT_FIELDS],
}

impl Default for FfiStructLayout {
    fn default() -> Self {
        Self {
            header: FfiHeader::default(),
            struct_id: 0,
            byte_size: 0,
            alignment: 0,
            field_count: 0,
            field_offsets: [0; FFI_MAX_STRUCT_LAYOUT_FIELDS],
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiHeader {
    pub version: u32,
    pub byte_size: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiBytes {
    pub ptr: *const u8,
    pub len: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiSlice<T> {
    pub ptr: *const T,
    pub count: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiHandle {
    pub raw: u64,
}

impl From<Handle> for FfiHandle {
    fn from(handle: Handle) -> Self {
        Self { raw: handle.raw() }
    }
}

impl From<FfiHandle> for Handle {
    fn from(handle: FfiHandle) -> Self {
        Handle::from_raw(handle.raw)
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub handle: FfiHandle,
    pub submission_id: u64,
    pub required_bytes: u64,
}

impl Default for FfiResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            handle: FfiHandle::default(),
            submission_id: 0,
            required_bytes: 0,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiRange {
    pub offset: u64,
    pub count: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiExtent3d {
    pub width: u32,
    pub height: u32,
    pub depth: u32,
}

impl From<FfiExtent3d> for Extent3d {
    fn from(extent: FfiExtent3d) -> Self {
        Self {
            width: extent.width,
            height: extent.height,
            depth: extent.depth,
        }
    }
}

impl From<Extent3d> for FfiExtent3d {
    fn from(extent: Extent3d) -> Self {
        Self {
            width: extent.width,
            height: extent.height,
            depth: extent.depth,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiTextureOrigin3d {
    pub x: u32,
    pub y: u32,
    pub z: u32,
}

impl From<FfiTextureOrigin3d> for TextureOrigin3d {
    fn from(origin: FfiTextureOrigin3d) -> Self {
        Self {
            x: origin.x,
            y: origin.y,
            z: origin.z,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct FfiTextureSubresourceRange {
    pub base_mip: u32,
    pub mip_count: u32,
    pub base_layer: u32,
    pub layer_count: u32,
}

impl From<FfiTextureSubresourceRange> for TextureSubresourceRange {
    fn from(range: FfiTextureSubresourceRange) -> Self {
        Self {
            base_mip: range.base_mip,
            mip_count: range.mip_count,
            base_layer: range.base_layer,
            layer_count: range.layer_count,
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiStatusResult {
    pub header: FfiHeader,
    pub status: i32,
    pub error_domain: u32,
    pub unsupported_feature: u32,
    pub primary_handle: FfiHandle,
    pub submission_id: u64,
    pub required_bytes: u64,
    pub metrics: FfiMetricsSnapshot,
}

impl Default for FfiStatusResult {
    fn default() -> Self {
        Self {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<Self>() as u32,
            },
            status: StatusCode::Ok as i32,
            error_domain: 0,
            unsupported_feature: 0,
            primary_handle: FfiHandle::default(),
            submission_id: 0,
            required_bytes: 0,
            metrics: FfiMetricsSnapshot::default(),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct FfiClearColor {
    pub r: f32,
    pub g: f32,
    pub b: f32,
    pub a: f32,
}

impl From<FfiClearColor> for ClearColor {
    fn from(color: FfiClearColor) -> Self {
        Self {
            r: color.r,
            g: color.g,
            b: color.b,
            a: color.a,
        }
    }
}

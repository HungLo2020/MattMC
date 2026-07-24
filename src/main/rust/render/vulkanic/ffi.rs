use std::mem::{align_of, size_of};
use std::slice;

use super::error::{GalError, GalResult, StatusCode};
use super::handles::Handle;

pub const FFI_ABI_VERSION: u32 = 1;

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

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FfiMemoryDomain {
    DeviceLocal = 1,
    Upload = 2,
    Readback = 3,
}

impl FfiMemoryDomain {
    pub fn validate(raw: u32) -> GalResult<Self> {
        match raw {
            1 => Ok(Self::DeviceLocal),
            2 => Ok(Self::Upload),
            3 => Ok(Self::Readback),
            _ => Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown memory domain {raw}"),
            )),
        }
    }
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiBufferCreateRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub size: u64,
    pub memory_domain: u32,
    pub usage_bits: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiResourceUse {
    pub resource: FfiHandle,
    pub stage_bits: u32,
    pub access_bits: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiCommandListRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub encoded_ops: FfiBytes,
    pub resource_uses: FfiSlice<FfiResourceUse>,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FfiSubmissionRequest {
    pub header: FfiHeader,
    pub label: FfiBytes,
    pub command_lists: FfiSlice<FfiCommandListRequest>,
}

pub fn validate_header<T>(header: FfiHeader) -> GalResult<()> {
    if header.version != FFI_ABI_VERSION {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("unsupported VulkanicGAL FFI version {}", header.version),
        ));
    }
    if header.byte_size as usize != size_of::<T>() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "FFI byte size mismatch: got {}, expected {}",
                header.byte_size,
                size_of::<T>()
            ),
        ));
    }
    Ok(())
}

pub unsafe fn read_bytes<'a>(bytes: FfiBytes, nullable: bool, label: &str) -> GalResult<&'a [u8]> {
    if bytes.len == 0 {
        if !nullable && bytes.ptr.is_null() {
            return Err(GalError::ffi(
                StatusCode::NullPointer,
                format!("{label} pointer is null"),
            ));
        }
        return Ok(&[]);
    }
    if bytes.ptr.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    let len = usize::try_from(bytes.len).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} length does not fit usize"),
        )
    })?;
    Ok(slice::from_raw_parts(bytes.ptr, len))
}

pub unsafe fn read_slice<'a, T>(
    slice_desc: FfiSlice<T>,
    nullable: bool,
    label: &str,
) -> GalResult<&'a [T]> {
    if slice_desc.count == 0 {
        if !nullable && slice_desc.ptr.is_null() {
            return Err(GalError::ffi(
                StatusCode::NullPointer,
                format!("{label} pointer is null"),
            ));
        }
        return Ok(&[]);
    }
    if slice_desc.ptr.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    if (slice_desc.ptr as usize) % align_of::<T>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            format!("{label} pointer is not aligned to {}", align_of::<T>()),
        ));
    }
    let count = usize::try_from(slice_desc.count).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} count does not fit usize"),
        )
    })?;
    count.checked_mul(size_of::<T>()).ok_or_else(|| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} byte length overflows usize"),
        )
    })?;
    Ok(slice::from_raw_parts(slice_desc.ptr, count))
}

pub unsafe fn validate_buffer_create_request(
    request: *const FfiBufferCreateRequest,
) -> GalResult<FfiBufferCreateRequest> {
    if request.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            "buffer request pointer is null",
        ));
    }
    if (request as usize) % align_of::<FfiBufferCreateRequest>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            "buffer request pointer is misaligned",
        ));
    }
    let request = *request;
    validate_header::<FfiBufferCreateRequest>(request.header)?;
    read_bytes(request.label, true, "buffer label")?;
    FfiMemoryDomain::validate(request.memory_domain)?;
    if request.size == 0 || request.usage_bits == 0 {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "buffer size and usage bits must be non-zero",
        ));
    }
    Ok(request)
}

pub unsafe fn validate_submission_request(
    request: *const FfiSubmissionRequest,
) -> GalResult<FfiSubmissionRequest> {
    if request.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            "submission request pointer is null",
        ));
    }
    if (request as usize) % align_of::<FfiSubmissionRequest>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            "submission request pointer is misaligned",
        ));
    }
    let request = *request;
    validate_header::<FfiSubmissionRequest>(request.header)?;
    read_bytes(request.label, true, "submission label")?;
    let lists = read_slice(request.command_lists, false, "submission command lists")?;
    if lists.is_empty() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            "submission requires at least one command list",
        ));
    }
    for list in lists {
        validate_header::<FfiCommandListRequest>(list.header)?;
        read_bytes(list.label, true, "command list label")?;
        read_bytes(list.encoded_ops, false, "encoded command operations")?;
        for use_decl in read_slice(list.resource_uses, true, "resource uses")? {
            if Handle::from(use_decl.resource).is_null() {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "resource use contains null handle",
                ));
            }
            if use_decl.stage_bits == 0 || use_decl.access_bits == 0 {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "resource use requires stage and access bits",
                ));
            }
        }
    }
    Ok(request)
}

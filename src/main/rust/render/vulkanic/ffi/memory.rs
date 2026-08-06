use super::*;

pub fn validate_header<T>(header: FfiHeader) -> GalResult<()> {
    if !matches!(
        header.version,
        FFI_ABI_V1_VERSION
            | FFI_ABI_V2_VERSION
            | FFI_ABI_V3_VERSION
            | FFI_ABI_V4_VERSION
            | FFI_ABI_V5_VERSION
            | FFI_ABI_V6_VERSION
            | FFI_ABI_V7_VERSION
            | FFI_ABI_V8_VERSION
            | FFI_ABI_V9_VERSION
            | FFI_ABI_V10_VERSION
            | FFI_ABI_VERSION
    ) {
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
pub(crate) unsafe fn write_out<T>(out: *mut T, value: T, label: &str) -> GalResult<()> {
    if out.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    if (out as usize) % align_of::<T>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            format!("{label} pointer is not aligned to {}", align_of::<T>()),
        ));
    }
    ptr::write(out, value);
    Ok(())
}

pub(crate) unsafe fn write_status_out(out: *mut FfiStatusResult, value: FfiStatusResult) {
    if !out.is_null() && (out as usize) % align_of::<FfiStatusResult>() == 0 {
        ptr::write(out, value);
    }
}

pub(crate) unsafe fn write_context_out(out: *mut FfiContextResult, value: FfiContextResult) {
    if !out.is_null() && (out as usize) % align_of::<FfiContextResult>() == 0 {
        ptr::write(out, value);
    }
}

pub(crate) unsafe fn read_struct<T: Copy>(ptr: *const T, label: &str) -> GalResult<T> {
    if ptr.is_null() {
        return Err(GalError::ffi(
            StatusCode::NullPointer,
            format!("{label} pointer is null"),
        ));
    }
    if (ptr as usize) % align_of::<T>() != 0 {
        return Err(GalError::ffi(
            StatusCode::Alignment,
            format!("{label} pointer is not aligned to {}", align_of::<T>()),
        ));
    }
    Ok(*ptr)
}

pub(crate) unsafe fn read_limited_slice<'a, T>(
    slice_desc: FfiSlice<T>,
    nullable: bool,
    label: &str,
) -> GalResult<&'a [T]> {
    let items = read_slice(slice_desc, nullable, label)?;
    if items.len() > FFI_MAX_BATCH_ITEMS {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} count exceeds ABI maximum"),
        ));
    }
    Ok(items)
}

pub(crate) fn validate_item_size<T>(byte_size: u32, label: &str) -> GalResult<()> {
    if byte_size as usize != size_of::<T>() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!(
                "{label} byte size mismatch: got {byte_size}, expected {}",
                size_of::<T>()
            ),
        ));
    }
    Ok(())
}

pub(crate) unsafe fn read_bounded_bytes(
    bytes: FfiBytes,
    nullable: bool,
    max_bytes: usize,
    label: &str,
) -> GalResult<Vec<u8>> {
    let bytes = read_bytes(bytes, nullable, label)?;
    if bytes.len() > max_bytes {
        return Err(GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} length exceeds ABI maximum"),
        ));
    }
    Ok(bytes.to_vec())
}

pub(crate) unsafe fn read_label(label: FfiBytes, label_name: &str) -> GalResult<String> {
    let bytes = read_bounded_bytes(label, true, FFI_MAX_LABEL_BYTES, label_name)?;
    String::from_utf8(bytes).map_err(|_| {
        GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label_name} must be UTF-8"),
        )
    })
}

pub(crate) fn range_slice<'a, T>(
    items: &'a [T],
    range: FfiRange,
    label: &str,
) -> GalResult<&'a [T]> {
    let start = usize::try_from(range.offset).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} offset does not fit usize"),
        )
    })?;
    let count = usize::try_from(range.count).map_err(|_| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} count does not fit usize"),
        )
    })?;
    let end = start.checked_add(count).ok_or_else(|| {
        GalError::ffi(
            StatusCode::LengthOverflow,
            format!("{label} range overflows"),
        )
    })?;
    if end > items.len() {
        return Err(GalError::ffi(
            StatusCode::InvalidArgument,
            format!("{label} range is outside its table"),
        ));
    }
    Ok(&items[start..end])
}

pub(crate) fn bool_flag(raw: u32, label: &str) -> GalResult<bool> {
    match raw {
        0 => Ok(false),
        1 => Ok(true),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("{label} must be 0 or 1"),
        )),
    }
}

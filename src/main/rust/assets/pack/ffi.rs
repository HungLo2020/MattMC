use std::path::PathBuf;
use std::ptr;
use std::slice;
use std::str;
use std::time::Instant;

use super::errors::{PackError, PackErrorKind, PackResult};
use super::index::PackCounters;
use super::tape;

pub const STATUS_OK: i32 = 0;
pub const STATUS_INVALID_ARGUMENT: i32 = -1;
pub const STATUS_IO_ERROR: i32 = -2;
pub const STATUS_ZIP_ERROR: i32 = -3;
pub const STATUS_OUTPUT_TOO_SMALL: i32 = -4;
pub const STATUS_INVALID_HANDLE: i32 = -5;
pub const STATUS_INVALID_PATH: i32 = -6;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativePackOpenResult {
    pub status: i32,
    pub error_kind: i32,
    pub handle: u64,
    pub entries_indexed: u64,
    pub namespaces_indexed: u64,
    pub index_nanos: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativePackResult {
    pub status: i32,
    pub error_kind: i32,
    pub present: i32,
    pub reserved: i32,
    pub output_len: u64,
    pub entry_count: u64,
    pub namespace_count: u64,
    pub duration_nanos: u64,
    pub bytes_returned: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativePackCounters {
    pub list_ops: u64,
    pub exists_ops: u64,
    pub read_ops: u64,
    pub bytes_returned: u64,
    pub invalid_path_rejections: u64,
    pub stale_handle_attempts: u64,
    pub entries_indexed: u64,
    pub namespaces_indexed: u64,
}

/// # Safety
///
/// `path_ptr` must point to `path_len` readable UTF-8 bytes for the duration of
/// the call. `output_result` must point to writable memory for one
/// `NativePackOpenResult`. Rust copies the path and stores no Java pointer.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_open_directory(
    path_ptr: *const u8,
    path_len: u64,
    output_result: *mut NativePackOpenResult,
) -> i32 {
    let result = match ptr_to_str(path_ptr, path_len) {
        Ok(path) => match super::open_directory(&PathBuf::from(path)) {
            Ok((handle, stats)) => NativePackOpenResult {
                status: STATUS_OK,
                error_kind: 0,
                handle,
                entries_indexed: stats.entries_indexed,
                namespaces_indexed: stats.namespaces_indexed,
                index_nanos: stats.index_nanos,
            },
            Err(error) => open_error_result(error),
        },
        Err(error) => open_error_result(error),
    };
    write_open_result(output_result, result)
}

/// # Safety
///
/// `path_ptr`/`prefix_ptr` must either be null with zero length or point to
/// readable UTF-8 bytes for the duration of the call. `output_result` must
/// point to writable memory for one `NativePackOpenResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_open_zip(
    path_ptr: *const u8,
    path_len: u64,
    prefix_ptr: *const u8,
    prefix_len: u64,
    output_result: *mut NativePackOpenResult,
) -> i32 {
    let result = match (
        ptr_to_str(path_ptr, path_len),
        ptr_to_str(prefix_ptr, prefix_len),
    ) {
        (Ok(path), Ok(prefix)) => match super::open_zip(&PathBuf::from(path), prefix) {
            Ok((handle, stats)) => NativePackOpenResult {
                status: STATUS_OK,
                error_kind: 0,
                handle,
                entries_indexed: stats.entries_indexed,
                namespaces_indexed: stats.namespaces_indexed,
                index_nanos: stats.index_nanos,
            },
            Err(error) => open_error_result(error),
        },
        (Err(error), _) | (_, Err(error)) => open_error_result(error),
    };
    write_open_result(output_result, result)
}

#[no_mangle]
pub extern "C" fn mattmc_pack_close(handle: u64) -> i32 {
    match super::close(handle) {
        Ok(()) => STATUS_OK,
        Err(error) => status_for_error(&error),
    }
}

/// # Safety
///
/// String pointers must reference readable UTF-8 bytes for the duration of the
/// call. `output_ptr` may be null only when `output_capacity` is zero.
/// `output_result` must point to writable memory for one `NativePackResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_list_namespaces(
    handle: u64,
    pack_type_ptr: *const u8,
    pack_type_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativePackResult,
) -> i32 {
    let started = Instant::now();
    let result = match ptr_to_str(pack_type_ptr, pack_type_len) {
        Ok(pack_type) => super::with_pack(handle, |pack| {
            pack.list_namespaces(pack_type)
                .and_then(|items| copy_string_tape(items, output_ptr, output_capacity, started))
        }),
        Err(error) => Err(error),
    };
    write_result_from(output_result, result)
}

/// # Safety
///
/// String pointers must reference readable UTF-8 bytes for the duration of the
/// call. `output_ptr` may be null only when `output_capacity` is zero.
/// `output_result` must point to writable memory for one `NativePackResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_list_resources(
    handle: u64,
    pack_type_ptr: *const u8,
    pack_type_len: u64,
    namespace_ptr: *const u8,
    namespace_len: u64,
    prefix_ptr: *const u8,
    prefix_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativePackResult,
) -> i32 {
    let started = Instant::now();
    let result = match (
        ptr_to_str(pack_type_ptr, pack_type_len),
        ptr_to_str(namespace_ptr, namespace_len),
        ptr_to_str(prefix_ptr, prefix_len),
    ) {
        (Ok(pack_type), Ok(namespace), Ok(prefix)) => super::with_pack(handle, |pack| {
            pack.list_resources(pack_type, namespace, prefix)
                .and_then(|items| copy_string_tape(items, output_ptr, output_capacity, started))
        }),
        (Err(error), _, _) | (_, Err(error), _) | (_, _, Err(error)) => Err(error),
    };
    write_result_from(output_result, result)
}

/// # Safety
///
/// String pointers must reference readable UTF-8 bytes for the duration of the
/// call. `output_result` must point to writable memory for one
/// `NativePackResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_resource_exists(
    handle: u64,
    pack_type_ptr: *const u8,
    pack_type_len: u64,
    namespace_ptr: *const u8,
    namespace_len: u64,
    path_ptr: *const u8,
    path_len: u64,
    output_result: *mut NativePackResult,
) -> i32 {
    let started = Instant::now();
    let result = match (
        ptr_to_str(pack_type_ptr, pack_type_len),
        ptr_to_str(namespace_ptr, namespace_len),
        ptr_to_str(path_ptr, path_len),
    ) {
        (Ok(pack_type), Ok(namespace), Ok(path)) => super::with_pack(handle, |pack| {
            pack.exists(pack_type, namespace, path)
                .map(|present| NativePackResult {
                    status: STATUS_OK,
                    error_kind: 0,
                    present: present as i32,
                    duration_nanos: elapsed_nanos(started),
                    ..Default::default()
                })
        }),
        (Err(error), _, _) | (_, Err(error), _) | (_, _, Err(error)) => Err(error),
    };
    write_result_from(output_result, result)
}

/// # Safety
///
/// `path_ptr` must reference readable UTF-8 bytes for the duration of the call.
/// `output_result` must point to writable memory for one `NativePackResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_root_resource_exists(
    handle: u64,
    path_ptr: *const u8,
    path_len: u64,
    output_result: *mut NativePackResult,
) -> i32 {
    let started = Instant::now();
    let result = match ptr_to_str(path_ptr, path_len) {
        Ok(path) => super::with_pack(handle, |pack| {
            pack.root_exists(path).map(|present| NativePackResult {
                status: STATUS_OK,
                error_kind: 0,
                present: present as i32,
                duration_nanos: elapsed_nanos(started),
                ..Default::default()
            })
        }),
        Err(error) => Err(error),
    };
    write_result_from(output_result, result)
}

/// # Safety
///
/// String pointers must reference readable UTF-8 bytes for the duration of the
/// call. `output_ptr` may be null only when `output_capacity` is zero.
/// `output_result` must point to writable memory for one `NativePackResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_read_resource(
    handle: u64,
    pack_type_ptr: *const u8,
    pack_type_len: u64,
    namespace_ptr: *const u8,
    namespace_len: u64,
    path_ptr: *const u8,
    path_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativePackResult,
) -> i32 {
    let started = Instant::now();
    let result = match (
        ptr_to_str(pack_type_ptr, pack_type_len),
        ptr_to_str(namespace_ptr, namespace_len),
        ptr_to_str(path_ptr, path_len),
    ) {
        (Ok(pack_type), Ok(namespace), Ok(path)) => super::with_pack(handle, |pack| {
            pack.read_resource(pack_type, namespace, path)
                .and_then(|bytes| copy_optional_bytes(bytes, output_ptr, output_capacity, started))
        }),
        (Err(error), _, _) | (_, Err(error), _) | (_, _, Err(error)) => Err(error),
    };
    write_result_from(output_result, result)
}

/// # Safety
///
/// `path_ptr` must reference readable UTF-8 bytes for the duration of the call.
/// `output_ptr` may be null only when `output_capacity` is zero.
/// `output_result` must point to writable memory for one `NativePackResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_read_root_resource(
    handle: u64,
    path_ptr: *const u8,
    path_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativePackResult,
) -> i32 {
    let started = Instant::now();
    let result = match ptr_to_str(path_ptr, path_len) {
        Ok(path) => super::with_pack(handle, |pack| {
            pack.read_root_resource(path)
                .and_then(|bytes| copy_optional_bytes(bytes, output_ptr, output_capacity, started))
        }),
        Err(error) => Err(error),
    };
    write_result_from(output_result, result)
}

/// # Safety
///
/// `output_result` must point to writable memory for one `NativePackCounters`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_pack_counters(
    handle: u64,
    output_result: *mut NativePackCounters,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    match super::counters(handle) {
        Ok(counters) => {
            *output_result = counters.into();
            STATUS_OK
        }
        Err(error) => status_for_error(&error),
    }
}

fn copy_string_tape(
    items: Vec<String>,
    output_ptr: *mut u8,
    output_capacity: u64,
    started: Instant,
) -> PackResult<NativePackResult> {
    let tape = tape::string_list(&items)?;
    copy_bytes(&tape, true, output_ptr, output_capacity, started).map(|mut result| {
        result.entry_count = items.len() as u64;
        result.namespace_count = items.len() as u64;
        result
    })
}

fn copy_optional_bytes(
    bytes: Option<Vec<u8>>,
    output_ptr: *mut u8,
    output_capacity: u64,
    started: Instant,
) -> PackResult<NativePackResult> {
    match bytes {
        Some(bytes) => copy_bytes(&bytes, true, output_ptr, output_capacity, started),
        None => Ok(NativePackResult {
            status: STATUS_OK,
            present: 0,
            duration_nanos: elapsed_nanos(started),
            ..Default::default()
        }),
    }
}

fn copy_bytes(
    bytes: &[u8],
    present: bool,
    output_ptr: *mut u8,
    output_capacity: u64,
    started: Instant,
) -> PackResult<NativePackResult> {
    if output_capacity < bytes.len() as u64 {
        return Ok(NativePackResult {
            status: STATUS_OUTPUT_TOO_SMALL,
            present: present as i32,
            output_len: bytes.len() as u64,
            duration_nanos: elapsed_nanos(started),
            ..Default::default()
        });
    }
    if !bytes.is_empty() && output_ptr.is_null() {
        return Err(PackError::invalid_argument(
            "non-empty output requires a writable output pointer",
        ));
    }
    if !bytes.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(bytes.as_ptr(), output_ptr, bytes.len());
        }
    }
    Ok(NativePackResult {
        status: STATUS_OK,
        present: present as i32,
        output_len: bytes.len() as u64,
        bytes_returned: bytes.len() as u64,
        duration_nanos: elapsed_nanos(started),
        ..Default::default()
    })
}

fn ptr_to_str<'a>(ptr: *const u8, len: u64) -> PackResult<&'a str> {
    if len > usize::MAX as u64 {
        return Err(PackError::invalid_argument("string length exceeds usize"));
    }
    if len == 0 {
        return Ok("");
    }
    if ptr.is_null() {
        return Err(PackError::invalid_argument(
            "non-empty string has null pointer",
        ));
    }
    let bytes = unsafe { slice::from_raw_parts(ptr, len as usize) };
    str::from_utf8(bytes).map_err(|_| PackError::invalid_argument("string is not valid UTF-8"))
}

fn write_open_result(output: *mut NativePackOpenResult, result: NativePackOpenResult) -> i32 {
    if output.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    unsafe {
        *output = result;
    }
    result.status
}

fn write_result_from(output: *mut NativePackResult, result: PackResult<NativePackResult>) -> i32 {
    let result = match result {
        Ok(result) => result,
        Err(error) => NativePackResult {
            status: status_for_error(&error),
            error_kind: error.kind as i32,
            ..Default::default()
        },
    };
    if output.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    unsafe {
        *output = result;
    }
    result.status
}

fn open_error_result(error: PackError) -> NativePackOpenResult {
    NativePackOpenResult {
        status: status_for_error(&error),
        error_kind: error.kind as i32,
        ..Default::default()
    }
}

fn status_for_error(error: &PackError) -> i32 {
    match error.kind {
        PackErrorKind::InvalidArgument => STATUS_INVALID_ARGUMENT,
        PackErrorKind::Io => STATUS_IO_ERROR,
        PackErrorKind::Zip => STATUS_ZIP_ERROR,
        PackErrorKind::InvalidPath => STATUS_INVALID_PATH,
        PackErrorKind::InvalidHandle => STATUS_INVALID_HANDLE,
        PackErrorKind::NotFound => STATUS_OK,
    }
}

fn elapsed_nanos(started: Instant) -> u64 {
    started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64
}

impl From<PackCounters> for NativePackCounters {
    fn from(value: PackCounters) -> Self {
        Self {
            list_ops: value.list_ops,
            exists_ops: value.exists_ops,
            read_ops: value.read_ops,
            bytes_returned: value.bytes_returned,
            invalid_path_rejections: value.invalid_path_rejections,
            stale_handle_attempts: value.stale_handle_attempts,
            entries_indexed: value.entries_indexed,
            namespaces_indexed: value.namespaces_indexed,
        }
    }
}

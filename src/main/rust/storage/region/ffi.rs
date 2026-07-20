//! FFI boundary for region files.
//!
//! The handle-based exports are the production API. The path-based exports at
//! the bottom of this file are isolated test/dev helpers for generated fixture
//! checks and deterministic storage replay diagnostics; production Java should
//! not use them.

use std::path::PathBuf;
use std::ptr;
use std::slice;
use std::sync::{Arc, Mutex, OnceLock};

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::fingerprint::fingerprint_document;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::nbt::tape::{document_from_tape, document_to_tape};
use crate::storage::nbt::writer::write_document;

use super::decompress::{compress_region_payload, decompress_region_payload};
use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::open::OpenRegion;
use super::{
    delete_chunk_payload, flush_region, read_chunk_nbt_fingerprint, read_chunk_payload,
    write_chunk_payload, ChunkPayload, ChunkWriteResult,
};

pub const STATUS_OK: i32 = 0;
pub const STATUS_INVALID_ARGUMENT: i32 = -1;
pub const STATUS_READ_ERROR: i32 = -2;
pub const STATUS_CORRUPT_REGION: i32 = -3;
pub const STATUS_OUTPUT_TOO_SMALL: i32 = -4;
pub const STATUS_DECOMPRESSION_ERROR: i32 = -5;
pub const STATUS_NBT_ERROR: i32 = -6;
pub const STATUS_INVALID_HANDLE: i32 = -7;

const ERROR_DOMAIN_NONE: i32 = 0;
const ERROR_DOMAIN_REGION: i32 = 1;
const ERROR_DOMAIN_DECOMPRESSION: i32 = 2;
const ERROR_DOMAIN_NBT: i32 = 3;

const REGION_HANDLE_KIND: u64 = 0x52;
const HANDLE_KIND_SHIFT: u64 = 56;
const HANDLE_GENERATION_SHIFT: u64 = 32;
const HANDLE_GENERATION_MASK: u64 = 0x00ff_ffff;
const HANDLE_SLOT_MASK: u64 = 0xffff_ffff;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeRegionOpenResult {
    pub status: i32,
    pub error_kind: i32,
    pub reserved: u32,
    pub handle: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeRegionPayloadResult {
    pub status: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub reserved: i32,
    pub timestamp: u64,
    pub output_len: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeRegionNbtResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub reserved0: i32,
    pub reserved1: i32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub fingerprint: u64,
    pub error_offset: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeRegionTapeResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub reserved0: i32,
    pub reserved1: i32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub fingerprint: u64,
    pub error_offset: u64,
    pub output_len: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeRegionWriteResult {
    pub status: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub sector_count: i32,
    pub timestamp: u64,
    pub sector_offset: u64,
    pub payload_len: u64,
}

struct RegionHandleTable {
    entries: Vec<RegionHandleEntry>,
}

#[derive(Default)]
struct RegionHandleEntry {
    generation: u32,
    region: Option<Arc<Mutex<OpenRegion>>>,
}

impl RegionHandleTable {
    fn insert(&mut self, region: OpenRegion) -> u64 {
        for (slot, entry) in self.entries.iter_mut().enumerate() {
            if entry.region.is_none() {
                entry.generation = next_generation(entry.generation);
                entry.region = Some(Arc::new(Mutex::new(region)));
                return encode_region_handle(slot as u32, entry.generation);
            }
        }
        let slot = self.entries.len() as u32;
        self.entries.push(RegionHandleEntry {
            generation: 1,
            region: Some(Arc::new(Mutex::new(region))),
        });
        encode_region_handle(slot, 1)
    }

    fn get(&self, handle: u64) -> Result<Arc<Mutex<OpenRegion>>, RegionError> {
        let (slot, generation) = decode_region_handle(handle)?;
        let entry = self.entries.get(slot as usize).ok_or_else(invalid_handle)?;
        if entry.generation != generation {
            return Err(invalid_handle());
        }
        entry.region.as_ref().cloned().ok_or_else(invalid_handle)
    }

    fn remove(&mut self, handle: u64) -> Result<Arc<Mutex<OpenRegion>>, RegionError> {
        let (slot, generation) = decode_region_handle(handle)?;
        let entry = self
            .entries
            .get_mut(slot as usize)
            .ok_or_else(invalid_handle)?;
        if entry.generation != generation {
            return Err(invalid_handle());
        }
        entry.region.take().ok_or_else(invalid_handle)
    }
}

fn region_table() -> &'static Mutex<RegionHandleTable> {
    static TABLE: OnceLock<Mutex<RegionHandleTable>> = OnceLock::new();
    TABLE.get_or_init(|| {
        Mutex::new(RegionHandleTable {
            entries: Vec::new(),
        })
    })
}

fn encode_region_handle(slot: u32, generation: u32) -> u64 {
    (REGION_HANDLE_KIND << HANDLE_KIND_SHIFT)
        | (((generation as u64) & HANDLE_GENERATION_MASK) << HANDLE_GENERATION_SHIFT)
        | slot as u64
}

fn decode_region_handle(handle: u64) -> Result<(u32, u32), RegionError> {
    let kind = handle >> HANDLE_KIND_SHIFT;
    let generation = ((handle >> HANDLE_GENERATION_SHIFT) & HANDLE_GENERATION_MASK) as u32;
    let slot = (handle & HANDLE_SLOT_MASK) as u32;
    if kind != REGION_HANDLE_KIND || generation == 0 {
        return Err(invalid_handle());
    }
    Ok((slot, generation))
}

fn next_generation(current: u32) -> u32 {
    let next = (current.wrapping_add(1)) & HANDLE_GENERATION_MASK as u32;
    if next == 0 {
        1
    } else {
        next
    }
}

fn invalid_handle() -> RegionError {
    RegionError::new(
        RegionErrorKind::InvalidArgument,
        0,
        "invalid or stale region handle",
    )
}

fn status_for_handle_error(error: &RegionError) -> i32 {
    if error.kind == RegionErrorKind::InvalidArgument && error.message.contains("handle") {
        STATUS_INVALID_HANDLE
    } else {
        status_for_error(error)
    }
}

fn region_from_handle(handle: u64) -> Result<Arc<Mutex<OpenRegion>>, RegionError> {
    region_table()
        .lock()
        .map_err(|_| {
            RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                "region handle table is poisoned",
            )
        })?
        .get(handle)
}

pub(crate) fn with_open_region<T>(
    handle: u64,
    operation: impl FnOnce(&mut OpenRegion) -> RegionResult<T>,
) -> RegionResult<T> {
    let region = region_from_handle(handle)?;
    let mut guard = region.lock().map_err(|_| {
        RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            "region handle is poisoned",
        )
    })?;
    operation(&mut guard)
}

fn write_result(output: *mut NativeRegionPayloadResult, result: NativeRegionPayloadResult) -> bool {
    if output.is_null() {
        return false;
    }
    unsafe {
        *output = result;
    }
    true
}

fn write_write_result(
    output: *mut NativeRegionWriteResult,
    result: NativeRegionWriteResult,
) -> bool {
    if output.is_null() {
        return false;
    }
    unsafe {
        *output = result;
    }
    true
}

fn write_nbt_result(output: *mut NativeRegionNbtResult, result: NativeRegionNbtResult) -> bool {
    if output.is_null() {
        return false;
    }
    unsafe {
        *output = result;
    }
    true
}

fn write_tape_result(output: *mut NativeRegionTapeResult, result: NativeRegionTapeResult) -> bool {
    if output.is_null() {
        return false;
    }
    unsafe {
        *output = result;
    }
    true
}

fn write_open_result(output: *mut NativeRegionOpenResult, result: NativeRegionOpenResult) -> bool {
    if output.is_null() {
        return false;
    }
    unsafe {
        *output = result;
    }
    true
}

fn error_result(status: i32, error: RegionError) -> NativeRegionPayloadResult {
    NativeRegionPayloadResult {
        status,
        error_kind: error.kind as i32,
        present: 0,
        compression_id: 0,
        external: 0,
        reserved: 0,
        timestamp: error.offset,
        output_len: 0,
    }
}

fn open_error_result(status: i32, error: RegionError) -> NativeRegionOpenResult {
    NativeRegionOpenResult {
        status,
        error_kind: error.kind as i32,
        reserved: 0,
        handle: 0,
    }
}

fn nbt_error_result(status: i32, error_domain: i32, error: RegionError) -> NativeRegionNbtResult {
    NativeRegionNbtResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        present: 0,
        compression_id: 0,
        external: 0,
        reserved0: 0,
        reserved1: 0,
        timestamp: 0,
        compressed_len: 0,
        decompressed_len: 0,
        fingerprint: 0,
        error_offset: error.offset,
    }
}

fn tape_error_result(status: i32, error_domain: i32, error: RegionError) -> NativeRegionTapeResult {
    NativeRegionTapeResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativeRegionTapeResult::default()
    }
}

fn write_error_result(status: i32, error: RegionError) -> NativeRegionWriteResult {
    NativeRegionWriteResult {
        status,
        error_kind: error.kind as i32,
        ..NativeRegionWriteResult::default()
    }
}

fn write_success_result(result: ChunkWriteResult) -> NativeRegionWriteResult {
    NativeRegionWriteResult {
        status: STATUS_OK,
        error_kind: 0,
        present: i32::from(result.present),
        compression_id: result.compression_id as i32,
        external: i32::from(result.external),
        sector_count: result.sector_count as i32,
        timestamp: result.timestamp as u64,
        sector_offset: result.first_sector as u64,
        payload_len: result.payload_len,
    }
}

fn payload_success_result(payload: &ChunkPayload) -> NativeRegionPayloadResult {
    NativeRegionPayloadResult {
        status: STATUS_OK,
        error_kind: 0,
        present: 1,
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        reserved: 0,
        timestamp: payload.timestamp as u64,
        output_len: payload.payload.len() as u64,
    }
}

fn status_for_error(error: &RegionError) -> i32 {
    match error.kind {
        RegionErrorKind::InvalidArgument | RegionErrorKind::PathEncoding => STATUS_INVALID_ARGUMENT,
        RegionErrorKind::Io => STATUS_READ_ERROR,
        RegionErrorKind::OutputTooSmall => STATUS_OUTPUT_TOO_SMALL,
        _ => STATUS_CORRUPT_REGION,
    }
}

fn nbt_status_and_domain(error: &RegionError) -> (i32, i32) {
    match error.kind {
        RegionErrorKind::InvalidArgument | RegionErrorKind::PathEncoding => {
            (STATUS_INVALID_ARGUMENT, ERROR_DOMAIN_REGION)
        }
        RegionErrorKind::Io => (STATUS_READ_ERROR, ERROR_DOMAIN_REGION),
        RegionErrorKind::DecompressionError
        | RegionErrorKind::DecompressionSizeLimit
        | RegionErrorKind::Lz4InvalidHeader
        | RegionErrorKind::Lz4InvalidBlock
        | RegionErrorKind::Lz4ChecksumMismatch => {
            (STATUS_DECOMPRESSION_ERROR, ERROR_DOMAIN_DECOMPRESSION)
        }
        RegionErrorKind::NbtParseError => (STATUS_NBT_ERROR, ERROR_DOMAIN_NBT),
        _ => (STATUS_CORRUPT_REGION, ERROR_DOMAIN_REGION),
    }
}

fn ptr_to_bytes<'a>(ptr: *const u8, len: u64) -> Result<&'a [u8], NativeRegionPayloadResult> {
    if len > usize::MAX as u64 {
        return Err(NativeRegionPayloadResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionPayloadResult::default()
        });
    }
    if len != 0 && ptr.is_null() {
        return Err(NativeRegionPayloadResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionPayloadResult::default()
        });
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len as usize) })
}

fn ptr_to_bytes_nbt<'a>(ptr: *const u8, len: u64) -> Result<&'a [u8], NativeRegionNbtResult> {
    if len > usize::MAX as u64 {
        return Err(NativeRegionNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_domain: ERROR_DOMAIN_REGION,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionNbtResult::default()
        });
    }
    if len != 0 && ptr.is_null() {
        return Err(NativeRegionNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_domain: ERROR_DOMAIN_REGION,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionNbtResult::default()
        });
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len as usize) })
}

fn ptr_to_bytes_write<'a>(ptr: *const u8, len: u64) -> Result<&'a [u8], NativeRegionWriteResult> {
    if len > usize::MAX as u64 {
        return Err(NativeRegionWriteResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionWriteResult::default()
        });
    }
    if len != 0 && ptr.is_null() {
        return Err(NativeRegionWriteResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionWriteResult::default()
        });
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len as usize) })
}

fn path_from_utf8_bytes(bytes: &[u8]) -> Result<PathBuf, RegionError> {
    let path_str = std::str::from_utf8(bytes).map_err(|_| {
        RegionError::new(
            RegionErrorKind::PathEncoding,
            0,
            "region path is not valid UTF-8",
        )
    })?;
    if path_str.is_empty() {
        return Err(RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            "region path must not be empty",
        ));
    }
    Ok(PathBuf::from(path_str))
}

/// # Safety
///
/// `path_ptr` must be null only when `path_len` is zero, otherwise it must
/// point to `path_len` readable UTF-8 bytes for the duration of the call.
/// `output_result` must point to one writable `NativeRegionOpenResult`. The
/// returned handle is generation-safe and must later be passed to
/// `mattmc_region_close`. Rust retains the open file and region metadata until
/// close.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_open(
    path_ptr: *const u8,
    path_len: u64,
    sync: i32,
    output_result: *mut NativeRegionOpenResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let path_bytes = match ptr_to_bytes(path_ptr, path_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            let result = NativeRegionOpenResult {
                status: result.status,
                error_kind: result.error_kind,
                ..NativeRegionOpenResult::default()
            };
            write_open_result(output_result, result);
            return result.status;
        }
    };
    let path = match path_from_utf8_bytes(path_bytes) {
        Ok(path) => path,
        Err(error) => {
            let status = status_for_error(&error);
            let result = open_error_result(status, error);
            write_open_result(output_result, result);
            return result.status;
        }
    };
    match OpenRegion::open(path, sync != 0) {
        Ok(region) => {
            let handle = match region_table().lock() {
                Ok(mut table) => table.insert(region),
                Err(_) => {
                    let error = RegionError::new(
                        RegionErrorKind::InvalidArgument,
                        0,
                        "region handle table is poisoned",
                    );
                    let result = open_error_result(STATUS_INVALID_ARGUMENT, error);
                    write_open_result(output_result, result);
                    return result.status;
                }
            };
            let result = NativeRegionOpenResult {
                status: STATUS_OK,
                error_kind: 0,
                reserved: 0,
                handle,
            };
            write_open_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_error(&error);
            let result = open_error_result(status, error);
            write_open_result(output_result, result);
            result.status
        }
    }
}

/// # Safety
///
/// `output_result` must point to one writable `NativeRegionWriteResult`.
/// `handle` must be a live region handle returned by `mattmc_region_open`.
/// Close removes the handle before flushing/closing the region so double-close
/// and stale handles fail safely.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_close(
    handle: u64,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let region = match region_table().lock() {
        Ok(mut table) => match table.remove(handle) {
            Ok(region) => region,
            Err(error) => {
                let result = write_error_result(status_for_handle_error(&error), error);
                write_write_result(output_result, result);
                return result.status;
            }
        },
        Err(_) => {
            let error = RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                "region handle table is poisoned",
            );
            let result = write_error_result(STATUS_INVALID_ARGUMENT, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    match Arc::try_unwrap(region) {
        Ok(mutex) => match mutex.into_inner() {
            Ok(region) => match region.close() {
                Ok(()) => {
                    let result = NativeRegionWriteResult {
                        status: STATUS_OK,
                        ..NativeRegionWriteResult::default()
                    };
                    write_write_result(output_result, result);
                    STATUS_OK
                }
                Err(error) => {
                    let result = write_error_result(status_for_error(&error), error);
                    write_write_result(output_result, result);
                    result.status
                }
            },
            Err(_) => {
                let error = RegionError::new(
                    RegionErrorKind::InvalidArgument,
                    0,
                    "region handle is poisoned",
                );
                let result = write_error_result(STATUS_INVALID_ARGUMENT, error);
                write_write_result(output_result, result);
                result.status
            }
        },
        Err(region) => match region.lock() {
            Ok(mut guard) => match guard.flush() {
                Ok(()) => {
                    let result = NativeRegionWriteResult {
                        status: STATUS_OK,
                        ..NativeRegionWriteResult::default()
                    };
                    write_write_result(output_result, result);
                    STATUS_OK
                }
                Err(error) => {
                    let result = write_error_result(status_for_error(&error), error);
                    write_write_result(output_result, result);
                    result.status
                }
            },
            Err(_) => {
                let error = RegionError::new(
                    RegionErrorKind::InvalidArgument,
                    0,
                    "region handle is poisoned",
                );
                let result = write_error_result(STATUS_INVALID_ARGUMENT, error);
                write_write_result(output_result, result);
                result.status
            }
        },
    }
}

/// # Safety
///
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// the required payload length is reported without copying payload bytes.
/// `output_result` must point to one writable `NativeRegionPayloadResult`.
/// `handle` must be a live region handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_read_chunk_payload(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativeRegionPayloadResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeRegionPayloadResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionPayloadResult::default()
        };
        write_result(output_result, result);
        return result.status;
    }
    let payload = match with_open_region(handle, |region| region.read_payload(chunk_x, chunk_z)) {
        Ok(payload) => payload,
        Err(error) => {
            let status = status_for_handle_error(&error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    write_payload_to_ffi(payload, output_ptr, output_capacity, output_result)
}

/// # Safety
///
/// `output_result` must point to one writable `NativeRegionNbtResult`.
/// `handle` must be a live region handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_read_chunk_nbt_fingerprint(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeRegionNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let compression_limits =
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let nbt_limits = NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let payload = match with_open_region(handle, |region| region.read_payload(chunk_x, chunk_z)) {
        Ok(payload) => payload,
        Err(error) => {
            let (status, domain) = nbt_status_and_domain(&error);
            let handle_status = status_for_handle_error(&error);
            let status = if handle_status == STATUS_INVALID_HANDLE {
                handle_status
            } else {
                status
            };
            let result = nbt_error_result(status, domain, error);
            write_nbt_result(output_result, result);
            return result.status;
        }
    };
    let Some(payload) = payload else {
        let result = NativeRegionNbtResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            ..NativeRegionNbtResult::default()
        };
        write_nbt_result(output_result, result);
        return STATUS_OK;
    };
    let decoded = match decompress_region_payload(
        payload.compression_id,
        &payload.payload,
        compression_limits,
    ) {
        Ok(decoded) => decoded,
        Err(error) => {
            let result = nbt_error_result(
                STATUS_DECOMPRESSION_ERROR,
                ERROR_DOMAIN_DECOMPRESSION,
                error,
            );
            write_nbt_result(output_result, result);
            return result.status;
        }
    };
    let document = match read_document(&decoded, nbt_limits) {
        Ok(document) => document,
        Err(error) => {
            let error = RegionError::new(
                RegionErrorKind::NbtParseError,
                error.offset as u64,
                format!("{:?}", error.kind),
            );
            let result = nbt_error_result(STATUS_NBT_ERROR, ERROR_DOMAIN_NBT, error);
            write_nbt_result(output_result, result);
            return result.status;
        }
    };
    let result = NativeRegionNbtResult {
        status: STATUS_OK,
        error_domain: ERROR_DOMAIN_NONE,
        error_kind: 0,
        present: 1,
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        reserved0: 0,
        reserved1: 0,
        timestamp: payload.timestamp as u64,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        fingerprint: fingerprint_document(&document),
        error_offset: 0,
    };
    write_nbt_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// the required MattMC NBT tape length is reported without copying bytes.
/// `output_result` must point to one writable `NativeRegionTapeResult`.
/// `handle` must be a live region handle. Rust reads, decompresses, parses,
/// and converts one whole chunk to a tape during the call without retaining
/// Java-owned memory.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_read_chunk_nbt_tape(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    output_ptr: *mut u8,
    output_capacity: u64,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeRegionTapeResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeRegionTapeResult {
            status: STATUS_INVALID_ARGUMENT,
            error_domain: ERROR_DOMAIN_REGION,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionTapeResult::default()
        };
        write_tape_result(output_result, result);
        return result.status;
    }
    let compression_limits =
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let nbt_limits = NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let payload = match with_open_region(handle, |region| region.read_payload(chunk_x, chunk_z)) {
        Ok(payload) => payload,
        Err(error) => {
            let (status, domain) = nbt_status_and_domain(&error);
            let handle_status = status_for_handle_error(&error);
            let status = if handle_status == STATUS_INVALID_HANDLE {
                handle_status
            } else {
                status
            };
            let result = tape_error_result(status, domain, error);
            write_tape_result(output_result, result);
            return result.status;
        }
    };
    let Some(payload) = payload else {
        let result = NativeRegionTapeResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            ..NativeRegionTapeResult::default()
        };
        write_tape_result(output_result, result);
        return STATUS_OK;
    };
    let decoded = match decompress_region_payload(
        payload.compression_id,
        &payload.payload,
        compression_limits,
    ) {
        Ok(decoded) => decoded,
        Err(error) => {
            let result = tape_error_result(
                STATUS_DECOMPRESSION_ERROR,
                ERROR_DOMAIN_DECOMPRESSION,
                error,
            );
            write_tape_result(output_result, result);
            return result.status;
        }
    };
    let document = match read_document(&decoded, nbt_limits) {
        Ok(document) => document,
        Err(error) => {
            let error = RegionError::new(
                RegionErrorKind::NbtParseError,
                error.offset as u64,
                format!("{:?}", error.kind),
            );
            let result = tape_error_result(STATUS_NBT_ERROR, ERROR_DOMAIN_NBT, error);
            write_tape_result(output_result, result);
            return result.status;
        }
    };
    let tape = match document_to_tape(&document, nbt_limits) {
        Ok(tape) => tape,
        Err(error) => {
            let error = RegionError::new(
                RegionErrorKind::NbtParseError,
                error.offset as u64,
                format!("{:?}", error.kind),
            );
            let result = tape_error_result(STATUS_NBT_ERROR, ERROR_DOMAIN_NBT, error);
            write_tape_result(output_result, result);
            return result.status;
        }
    };
    let fingerprint = fingerprint_document(&document);
    let mut result = NativeRegionTapeResult {
        status: STATUS_OK,
        error_domain: ERROR_DOMAIN_NONE,
        error_kind: 0,
        present: 1,
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        reserved0: 0,
        reserved1: 0,
        timestamp: payload.timestamp as u64,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        fingerprint,
        error_offset: 0,
        output_len: tape.len() as u64,
    };
    if output_capacity < tape.len() as u64 {
        result.status = STATUS_OUTPUT_TOO_SMALL;
        result.error_kind = RegionErrorKind::OutputTooSmall as i32;
        write_tape_result(output_result, result);
        return result.status;
    }
    if !tape.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(tape.as_ptr(), output_ptr, tape.len());
        }
    }
    write_tape_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `tape_ptr` must be null only when `tape_len` is zero, otherwise it must
/// point to `tape_len` readable bytes containing one complete MattMC NBT tape.
/// `output_result` must point to one writable `NativeRegionWriteResult`.
/// `handle` must be a live region handle. Rust copies the tape during the
/// call, encodes and compresses it, then writes a complete chunk payload.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_write_chunk_nbt_tape(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    compression_id: i32,
    tape_ptr: *const u8,
    tape_len: u64,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = NativeRegionWriteResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionWriteResult::default()
        };
        write_write_result(output_result, result);
        return result.status;
    }
    let tape = match ptr_to_bytes_write(tape_ptr, tape_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let compression_limits =
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let nbt_limits = NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match document_from_tape(tape, nbt_limits) {
        Ok(document) => document,
        Err(error) => {
            let error = RegionError::new(
                RegionErrorKind::NbtParseError,
                error.offset as u64,
                format!("{:?}", error.kind),
            );
            let result = write_error_result(STATUS_NBT_ERROR, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let encoded = match write_document(&document, nbt_limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let error = RegionError::new(
                RegionErrorKind::NbtParseError,
                error.offset as u64,
                format!("{:?}", error.kind),
            );
            let result = write_error_result(STATUS_NBT_ERROR, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let compressed =
        match compress_region_payload(compression_id as u8, &encoded, compression_limits) {
            Ok(compressed) => compressed,
            Err(error) => {
                let status = status_for_error(&error);
                let result = write_error_result(status, error);
                write_write_result(output_result, result);
                return result.status;
            }
        };
    match with_open_region(handle, |region| {
        region.write_payload(chunk_x, chunk_z, compression_id as u8, &compressed)
    }) {
        Ok(result) => {
            let result = write_success_result(result);
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_handle_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

/// # Safety
///
/// `payload_ptr` must be null only when `payload_len` is zero, otherwise it
/// must point to `payload_len` readable compressed chunk payload bytes for the
/// duration of the call. `output_result` must point to one writable
/// `NativeRegionWriteResult`. `handle` must be a live region handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_write_chunk_payload(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    compression_id: i32,
    payload_ptr: *const u8,
    payload_len: u64,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = NativeRegionWriteResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionWriteResult::default()
        };
        write_write_result(output_result, result);
        return result.status;
    }
    let payload = match ptr_to_bytes_write(payload_ptr, payload_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    match with_open_region(handle, |region| {
        region.write_payload(chunk_x, chunk_z, compression_id as u8, payload)
    }) {
        Ok(result) => {
            let result = write_success_result(result);
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_handle_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

/// # Safety
///
/// `output_result` must point to one writable `NativeRegionWriteResult`.
/// `handle` must be a live region handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_delete_chunk(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    match with_open_region(handle, |region| region.delete_chunk(chunk_x, chunk_z)) {
        Ok(result) => {
            let result = write_success_result(result);
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_handle_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

/// # Safety
///
/// `output_result` must point to one writable `NativeRegionWriteResult`.
/// `handle` must be a live region handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_handle_flush(
    handle: u64,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    match with_open_region(handle, |region| region.flush()) {
        Ok(()) => {
            let result = NativeRegionWriteResult {
                status: STATUS_OK,
                ..NativeRegionWriteResult::default()
            };
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_handle_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

fn write_payload_to_ffi(
    payload: Option<ChunkPayload>,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativeRegionPayloadResult,
) -> i32 {
    let Some(payload) = payload else {
        let result = NativeRegionPayloadResult {
            status: STATUS_OK,
            ..NativeRegionPayloadResult::default()
        };
        write_result(output_result, result);
        return STATUS_OK;
    };

    if output_capacity < payload.payload.len() as u64 {
        let mut result = payload_success_result(&payload);
        result.status = STATUS_OUTPUT_TOO_SMALL;
        result.error_kind = RegionErrorKind::OutputTooSmall as i32;
        write_result(output_result, result);
        return result.status;
    }

    if !payload.payload.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(payload.payload.as_ptr(), output_ptr, payload.payload.len());
        }
    }
    let result = payload_success_result(&payload);
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `path_ptr` must be null only when `path_len` is zero, otherwise it must
/// point to `path_len` readable UTF-8 bytes for the duration of the call.
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// the required payload length is reported without copying payload bytes.
/// `output_result` must point to one writable `NativeRegionPayloadResult`.
/// Rust does not retain Java-owned memory or path pointers after the call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_read_chunk_payload(
    path_ptr: *const u8,
    path_len: u64,
    chunk_x: i32,
    chunk_z: i32,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_result: *mut NativeRegionPayloadResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeRegionPayloadResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionPayloadResult::default()
        };
        write_result(output_result, result);
        return result.status;
    }
    let path_bytes = match ptr_to_bytes(path_ptr, path_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let path = match path_from_utf8_bytes(path_bytes) {
        Ok(path) => path,
        Err(error) => {
            let status = status_for_error(&error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let payload = match read_chunk_payload(&path, chunk_x, chunk_z) {
        Ok(payload) => payload,
        Err(error) => {
            let status = status_for_error(&error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };

    let Some(payload) = payload else {
        let result = NativeRegionPayloadResult {
            status: STATUS_OK,
            ..NativeRegionPayloadResult::default()
        };
        write_result(output_result, result);
        return STATUS_OK;
    };

    if output_capacity < payload.payload.len() as u64 {
        let result = NativeRegionPayloadResult {
            status: STATUS_OUTPUT_TOO_SMALL,
            error_kind: RegionErrorKind::OutputTooSmall as i32,
            present: 1,
            compression_id: payload.compression_id as i32,
            external: i32::from(payload.external),
            reserved: 0,
            timestamp: payload.timestamp as u64,
            output_len: payload.payload.len() as u64,
        };
        write_result(output_result, result);
        return result.status;
    }

    if !payload.payload.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(payload.payload.as_ptr(), output_ptr, payload.payload.len());
        }
    }
    let result = NativeRegionPayloadResult {
        status: STATUS_OK,
        error_kind: 0,
        present: 1,
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        reserved: 0,
        timestamp: payload.timestamp as u64,
        output_len: payload.payload.len() as u64,
    };
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `path_ptr` must be null only when `path_len` is zero, otherwise it must
/// point to `path_len` readable UTF-8 bytes for the duration of the call.
/// `output_result` must point to one writable `NativeRegionNbtResult`.
/// The operation reads a whole chunk payload, decompresses it, parses NBT, and
/// returns metadata plus a semantic fingerprint. Rust does not retain
/// Java-owned memory or path pointers after the call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_read_chunk_nbt_fingerprint(
    path_ptr: *const u8,
    path_len: u64,
    chunk_x: i32,
    chunk_z: i32,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeRegionNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let path_bytes = match ptr_to_bytes_nbt(path_ptr, path_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_nbt_result(output_result, result);
            return result.status;
        }
    };
    let path = match path_from_utf8_bytes(path_bytes) {
        Ok(path) => path,
        Err(error) => {
            let (status, domain) = nbt_status_and_domain(&error);
            let result = nbt_error_result(status, domain, error);
            write_nbt_result(output_result, result);
            return result.status;
        }
    };
    let compression_limits =
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let nbt_limits = NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let fingerprint =
        match read_chunk_nbt_fingerprint(&path, chunk_x, chunk_z, compression_limits, nbt_limits) {
            Ok(fingerprint) => fingerprint,
            Err(error) => {
                let (status, domain) = nbt_status_and_domain(&error);
                let result = nbt_error_result(status, domain, error);
                write_nbt_result(output_result, result);
                return result.status;
            }
        };
    let Some(fingerprint) = fingerprint else {
        let result = NativeRegionNbtResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            ..NativeRegionNbtResult::default()
        };
        write_nbt_result(output_result, result);
        return STATUS_OK;
    };
    let result = NativeRegionNbtResult {
        status: STATUS_OK,
        error_domain: ERROR_DOMAIN_NONE,
        error_kind: 0,
        present: 1,
        compression_id: fingerprint.compression_id as i32,
        external: i32::from(fingerprint.external),
        reserved0: 0,
        reserved1: 0,
        timestamp: fingerprint.timestamp as u64,
        compressed_len: fingerprint.compressed_len,
        decompressed_len: fingerprint.decompressed_len,
        fingerprint: fingerprint.fingerprint,
        error_offset: 0,
    };
    write_nbt_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `path_ptr` must be null only when `path_len` is zero, otherwise it must
/// point to `path_len` readable UTF-8 bytes for the duration of the call.
/// `payload_ptr` must be null only when `payload_len` is zero, otherwise it
/// must point to `payload_len` readable compressed chunk payload bytes for the
/// duration of the call. `output_result` must point to one writable
/// `NativeRegionWriteResult`. Rust copies data to disk during the call and
/// does not retain Java-owned memory.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_write_chunk_payload(
    path_ptr: *const u8,
    path_len: u64,
    chunk_x: i32,
    chunk_z: i32,
    compression_id: i32,
    payload_ptr: *const u8,
    payload_len: u64,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = NativeRegionWriteResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativeRegionWriteResult::default()
        };
        write_write_result(output_result, result);
        return result.status;
    }
    let path_bytes = match ptr_to_bytes_write(path_ptr, path_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let payload = match ptr_to_bytes_write(payload_ptr, payload_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let path = match path_from_utf8_bytes(path_bytes) {
        Ok(path) => path,
        Err(error) => {
            let status = status_for_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    match write_chunk_payload(&path, chunk_x, chunk_z, compression_id as u8, payload) {
        Ok(result) => {
            let result = write_success_result(result);
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

/// # Safety
///
/// `path_ptr` must be null only when `path_len` is zero, otherwise it must
/// point to `path_len` readable UTF-8 bytes for the duration of the call.
/// `output_result` must point to one writable `NativeRegionWriteResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_delete_chunk(
    path_ptr: *const u8,
    path_len: u64,
    chunk_x: i32,
    chunk_z: i32,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let path_bytes = match ptr_to_bytes_write(path_ptr, path_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let path = match path_from_utf8_bytes(path_bytes) {
        Ok(path) => path,
        Err(error) => {
            let status = status_for_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    match delete_chunk_payload(&path, chunk_x, chunk_z) {
        Ok(result) => {
            let result = write_success_result(result);
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

/// # Safety
///
/// `path_ptr` must be null only when `path_len` is zero, otherwise it must
/// point to `path_len` readable UTF-8 bytes for the duration of the call.
/// `output_result` must point to one writable `NativeRegionWriteResult`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_region_flush(
    path_ptr: *const u8,
    path_len: u64,
    output_result: *mut NativeRegionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let path_bytes = match ptr_to_bytes_write(path_ptr, path_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let path = match path_from_utf8_bytes(path_bytes) {
        Ok(path) => path,
        Err(error) => {
            let status = status_for_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    match flush_region(&path) {
        Ok(()) => {
            let result = NativeRegionWriteResult {
                status: STATUS_OK,
                ..NativeRegionWriteResult::default()
            };
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = status_for_error(&error);
            let result = write_error_result(status, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

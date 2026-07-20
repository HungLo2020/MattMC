//! FFI boundary for dev/test POI chunk decoding and encoding.
//!
//! These APIs intentionally move one coarse typed record buffer per chunk. They
//! never expose individual NBT tags, and production POI ownership remains gated
//! by Java-side development switches.

use std::ptr;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::fingerprint::fingerprint_document;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::nbt::writer::write_document;
use crate::storage::region::decompress::{compress_region_payload, decompress_region_payload};
use crate::storage::region::error::{RegionError, RegionErrorKind};
use crate::storage::region::ffi::{
    with_open_region, STATUS_DECOMPRESSION_ERROR, STATUS_INVALID_ARGUMENT, STATUS_INVALID_HANDLE,
    STATUS_NBT_ERROR, STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
};

use super::decoder::decode_poi_document;
use super::encoder::encode_poi_document;
use super::error::{PoiError, PoiErrorKind};
use super::tape::{count_records, decode_poi_tape, encode_poi_tape};

const STATUS_REGION_ERROR: i32 = -3;
const STATUS_POI_ERROR: i32 = -8;

const ERROR_DOMAIN_NONE: i32 = 0;
const ERROR_DOMAIN_REGION: i32 = 1;
const ERROR_DOMAIN_DECOMPRESSION: i32 = 2;
const ERROR_DOMAIN_NBT: i32 = 3;
const ERROR_DOMAIN_POI: i32 = 4;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativePoiDecodeResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub section_count: u32,
    pub record_count: u32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub output_len: u64,
    pub error_offset: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativePoiWriteResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub section_count: u32,
    pub record_count: u32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub fingerprint: u64,
    pub error_offset: u64,
}

/// # Safety
///
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// Rust reports the required typed POI buffer length without copying records.
/// `output_result` must point to one writable `NativePoiDecodeResult`.
/// `handle` must be a live Rust region handle. Rust does not retain any
/// Java-owned pointers after this call returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_poi_decode_chunk_from_region(
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
    output_result: *mut NativePoiDecodeResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = poi_error_result(
            STATUS_INVALID_ARGUMENT,
            ERROR_DOMAIN_POI,
            PoiError::new(PoiErrorKind::InvalidArgument, "output pointer is null"),
        );
        write_result(output_result, result);
        return result.status;
    }

    let payload = match with_open_region(handle, |region| region.read_payload(chunk_x, chunk_z)) {
        Ok(payload) => payload,
        Err(error) => {
            let status = if is_invalid_handle(&error) {
                STATUS_INVALID_HANDLE
            } else {
                STATUS_REGION_ERROR
            };
            let result = region_error_result(status, ERROR_DOMAIN_REGION, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let Some(payload) = payload else {
        let result = NativePoiDecodeResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            ..NativePoiDecodeResult::default()
        };
        write_result(output_result, result);
        return STATUS_OK;
    };

    let decoded = match decompress_region_payload(
        payload.compression_id,
        &payload.payload,
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes),
    ) {
        Ok(decoded) => decoded,
        Err(error) => {
            let result = region_error_result(
                STATUS_DECOMPRESSION_ERROR,
                ERROR_DOMAIN_DECOMPRESSION,
                error,
            );
            write_result(output_result, result);
            return result.status;
        }
    };

    let document = match read_document(
        &decoded,
        NbtLimits::from_ffi(
            max_depth,
            max_collection_len,
            max_alloc_bytes,
            max_total_bytes,
        ),
    ) {
        Ok(document) => document,
        Err(error) => {
            let result = NativePoiDecodeResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                error_offset: error.offset as u64,
                ..NativePoiDecodeResult::default()
            };
            write_result(output_result, result);
            return result.status;
        }
    };

    let chunk = match decode_poi_document(&document) {
        Ok(chunk) => chunk,
        Err(error) => {
            let result = poi_error_result(STATUS_POI_ERROR, ERROR_DOMAIN_POI, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let tape = match encode_poi_tape(&chunk) {
        Ok(tape) => tape,
        Err(error) => {
            let result = poi_error_result(STATUS_POI_ERROR, ERROR_DOMAIN_POI, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let mut result = NativePoiDecodeResult {
        status: STATUS_OK,
        error_domain: ERROR_DOMAIN_NONE,
        error_kind: 0,
        present: 1,
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        section_count: chunk.sections.len() as u32,
        record_count: count_records(&chunk),
        timestamp: payload.timestamp as u64,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        output_len: tape.len() as u64,
        error_offset: 0,
    };
    if output_capacity < tape.len() as u64 {
        result.status = STATUS_OUTPUT_TOO_SMALL;
        result.error_kind = PoiErrorKind::OutputTooSmall as i32;
        write_result(output_result, result);
        return result.status;
    }
    if !tape.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(tape.as_ptr(), output_ptr, tape.len());
        }
    }
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `tape_ptr` must be null only when `tape_len` is zero, otherwise it must
/// point to `tape_len` readable bytes containing one complete POI typed tape for
/// the duration of the call. `output_result` must point to one writable
/// `NativePoiWriteResult`. `handle` must be a live Rust region handle. Rust
/// copies all input bytes it needs during the call and retains no Java pointers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_poi_write_chunk_to_region(
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
    output_result: *mut NativePoiWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = NativePoiWriteResult {
            status: STATUS_INVALID_ARGUMENT,
            error_domain: ERROR_DOMAIN_REGION,
            error_kind: RegionErrorKind::InvalidArgument as i32,
            ..NativePoiWriteResult::default()
        };
        write_write_result(output_result, result);
        return result.status;
    }
    if tape_len != 0 && tape_ptr.is_null() {
        let result = write_poi_error_result(
            STATUS_INVALID_ARGUMENT,
            ERROR_DOMAIN_POI,
            PoiError::new(PoiErrorKind::InvalidArgument, "POI tape pointer is null"),
        );
        write_write_result(output_result, result);
        return result.status;
    }
    let tape = if tape_len == 0 {
        &[][..]
    } else {
        unsafe { std::slice::from_raw_parts(tape_ptr, tape_len as usize) }
    };
    let chunk = match decode_poi_tape(tape) {
        Ok(chunk) => chunk,
        Err(error) => {
            let result = write_poi_error_result(STATUS_POI_ERROR, ERROR_DOMAIN_POI, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let document = match encode_poi_document(&chunk) {
        Ok(document) => document,
        Err(error) => {
            let result = write_poi_error_result(STATUS_POI_ERROR, ERROR_DOMAIN_POI, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let nbt_limits = NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let encoded = match write_document(&document, nbt_limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = NativePoiWriteResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                error_offset: error.offset as u64,
                ..NativePoiWriteResult::default()
            };
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let compressed = match compress_region_payload(
        compression_id as u8,
        &encoded,
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes),
    ) {
        Ok(compressed) => compressed,
        Err(error) => {
            let result = write_region_error_result(
                STATUS_DECOMPRESSION_ERROR,
                ERROR_DOMAIN_DECOMPRESSION,
                error,
            );
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let fingerprint = fingerprint_document(&document);
    match with_open_region(handle, |region| {
        region.write_payload(chunk_x, chunk_z, compression_id as u8, &compressed)
    }) {
        Ok(result) => {
            let output = NativePoiWriteResult {
                status: STATUS_OK,
                error_domain: ERROR_DOMAIN_NONE,
                error_kind: 0,
                present: 1,
                compression_id: result.compression_id as i32,
                external: i32::from(result.external),
                section_count: chunk.sections.len() as u32,
                record_count: count_records(&chunk),
                timestamp: result.timestamp as u64,
                compressed_len: compressed.len() as u64,
                decompressed_len: encoded.len() as u64,
                fingerprint,
                error_offset: 0,
            };
            write_write_result(output_result, output);
            STATUS_OK
        }
        Err(error) => {
            let status = if is_invalid_handle(&error) {
                STATUS_INVALID_HANDLE
            } else {
                STATUS_REGION_ERROR
            };
            let result = write_region_error_result(status, ERROR_DOMAIN_REGION, error);
            write_write_result(output_result, result);
            result.status
        }
    }
}

fn write_result(output: *mut NativePoiDecodeResult, result: NativePoiDecodeResult) {
    unsafe {
        *output = result;
    }
}

fn write_write_result(output: *mut NativePoiWriteResult, result: NativePoiWriteResult) {
    unsafe {
        *output = result;
    }
}

fn is_invalid_handle(error: &RegionError) -> bool {
    error.kind == RegionErrorKind::InvalidArgument && error.message.contains("handle")
}

fn region_error_result(
    status: i32,
    error_domain: i32,
    error: RegionError,
) -> NativePoiDecodeResult {
    NativePoiDecodeResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativePoiDecodeResult::default()
    }
}

fn poi_error_result(status: i32, error_domain: i32, error: PoiError) -> NativePoiDecodeResult {
    NativePoiDecodeResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        ..NativePoiDecodeResult::default()
    }
}

fn write_region_error_result(
    status: i32,
    error_domain: i32,
    error: RegionError,
) -> NativePoiWriteResult {
    NativePoiWriteResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativePoiWriteResult::default()
    }
}

fn write_poi_error_result(status: i32, error_domain: i32, error: PoiError) -> NativePoiWriteResult {
    NativePoiWriteResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        ..NativePoiWriteResult::default()
    }
}

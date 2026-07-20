//! FFI boundary for dev/test chunk-section decoding.
//!
//! This API returns one bulk typed buffer per current-version chunk. Production
//! chunk loading and saving still use the Java `SerializableChunkData` path.

use std::ptr;
use std::time::Instant;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::region::decompress::decompress_region_payload;
use crate::storage::region::error::{RegionError, RegionErrorKind};
use crate::storage::region::ffi::{
    with_open_region_timed, RegionLockTimings, STATUS_DECOMPRESSION_ERROR, STATUS_INVALID_ARGUMENT,
    STATUS_INVALID_HANDLE, STATUS_NBT_ERROR, STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
};

use super::decoder::decode_chunk_document;
use super::error::{ChunkError, ChunkErrorKind};
use super::tape::encode_chunk_tape;

const STATUS_REGION_ERROR: i32 = -3;
const STATUS_CHUNK_ERROR: i32 = -8;

const ERROR_DOMAIN_NONE: i32 = 0;
const ERROR_DOMAIN_REGION: i32 = 1;
const ERROR_DOMAIN_DECOMPRESSION: i32 = 2;
const ERROR_DOMAIN_NBT: i32 = 3;
const ERROR_DOMAIN_CHUNK: i32 = 6;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeChunkSectionDecodeResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub requires_dfu: i32,
    pub compression_id: i32,
    pub external: i32,
    pub data_version: i32,
    pub chunk_x: i32,
    pub chunk_z: i32,
    pub section_count: u32,
    pub heightmap_count: u32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub output_len: u64,
    pub error_offset: u64,
    pub region_read_nanos: u64,
    pub decompression_nanos: u64,
    pub nbt_parse_nanos: u64,
    pub chunk_decode_nanos: u64,
    pub tape_creation_nanos: u64,
    pub region_handle_lookup_nanos: u64,
    pub region_lock_wait_nanos: u64,
    pub region_lock_hold_nanos: u64,
    pub rust_output_copy_nanos: u64,
    pub rust_ffi_total_nanos: u64,
}

/// # Safety
///
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// Rust reports the required chunk-section tape length without copying.
/// `output_result` must point to one writable `NativeChunkSectionDecodeResult`.
/// `handle` must be a live Rust region handle. Rust does not retain Java-owned
/// pointers after this call returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_decode_sections_from_region(
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
    output_result: *mut NativeChunkSectionDecodeResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = chunk_error_result(
            STATUS_INVALID_ARGUMENT,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "output pointer is null"),
        );
        write_result(output_result, result);
        return result.status;
    }

    let ffi_started = Instant::now();
    let region_started = Instant::now();
    let (payload, lock_timings) =
        match with_open_region_timed(handle, |region| region.read_payload(chunk_x, chunk_z)) {
            Ok(result) => result,
            Err(error) => {
                let status = if is_invalid_handle(&error) {
                    STATUS_INVALID_HANDLE
                } else {
                    STATUS_REGION_ERROR
                };
                let mut result = region_error_result(status, ERROR_DOMAIN_REGION, error);
                result.region_read_nanos = elapsed_nanos(region_started);
                return finish(output_result, result, ffi_started);
            }
        };
    let region_read_nanos = elapsed_nanos(region_started);
    let Some(payload) = payload else {
        let result = NativeChunkSectionDecodeResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            region_read_nanos,
            region_handle_lookup_nanos: lock_timings.handle_lookup_nanos,
            region_lock_wait_nanos: lock_timings.lock_wait_nanos,
            region_lock_hold_nanos: lock_timings.lock_hold_nanos,
            ..NativeChunkSectionDecodeResult::default()
        };
        return finish(output_result, result, ffi_started);
    };

    let decompression_started = Instant::now();
    let decoded = match decompress_region_payload(
        payload.compression_id,
        &payload.payload,
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes),
    ) {
        Ok(decoded) => decoded,
        Err(error) => {
            let mut result = region_error_result(
                STATUS_DECOMPRESSION_ERROR,
                ERROR_DOMAIN_DECOMPRESSION,
                error,
            );
            result.region_read_nanos = region_read_nanos;
            result.decompression_nanos = elapsed_nanos(decompression_started);
            apply_lock_timings(&mut result, lock_timings);
            return finish(output_result, result, ffi_started);
        }
    };
    let decompression_nanos = elapsed_nanos(decompression_started);

    let nbt_started = Instant::now();
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
            let mut result = NativeChunkSectionDecodeResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                error_offset: error.offset as u64,
                region_read_nanos,
                decompression_nanos,
                nbt_parse_nanos: elapsed_nanos(nbt_started),
                ..NativeChunkSectionDecodeResult::default()
            };
            apply_lock_timings(&mut result, lock_timings);
            return finish(output_result, result, ffi_started);
        }
    };
    let nbt_parse_nanos = elapsed_nanos(nbt_started);

    let chunk_started = Instant::now();
    let chunk = match decode_chunk_document(&document, chunk_x, chunk_z) {
        Ok(chunk) => chunk,
        Err(error) => {
            let mut result = chunk_error_result(STATUS_CHUNK_ERROR, error);
            result.region_read_nanos = region_read_nanos;
            result.decompression_nanos = decompression_nanos;
            result.nbt_parse_nanos = nbt_parse_nanos;
            result.chunk_decode_nanos = elapsed_nanos(chunk_started);
            apply_lock_timings(&mut result, lock_timings);
            return finish(output_result, result, ffi_started);
        }
    };
    let chunk_decode_nanos = elapsed_nanos(chunk_started);

    let tape_started = Instant::now();
    let tape = if chunk.requires_dfu {
        Vec::new()
    } else {
        match encode_chunk_tape(&chunk) {
            Ok(tape) => tape,
            Err(error) => {
                let mut result = chunk_error_result(STATUS_CHUNK_ERROR, error);
                result.region_read_nanos = region_read_nanos;
                result.decompression_nanos = decompression_nanos;
                result.nbt_parse_nanos = nbt_parse_nanos;
                result.chunk_decode_nanos = chunk_decode_nanos;
                result.tape_creation_nanos = elapsed_nanos(tape_started);
                apply_lock_timings(&mut result, lock_timings);
                return finish(output_result, result, ffi_started);
            }
        }
    };
    let tape_creation_nanos = elapsed_nanos(tape_started);

    let mut result = NativeChunkSectionDecodeResult {
        status: STATUS_OK,
        error_domain: ERROR_DOMAIN_NONE,
        error_kind: 0,
        present: 1,
        requires_dfu: i32::from(chunk.requires_dfu),
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        data_version: chunk.data_version,
        chunk_x: chunk.chunk_x,
        chunk_z: chunk.chunk_z,
        section_count: chunk.sections.len() as u32,
        heightmap_count: chunk.heightmaps.len() as u32,
        timestamp: payload.timestamp as u64,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        output_len: tape.len() as u64,
        error_offset: 0,
        region_read_nanos,
        decompression_nanos,
        nbt_parse_nanos,
        chunk_decode_nanos,
        tape_creation_nanos,
        region_handle_lookup_nanos: lock_timings.handle_lookup_nanos,
        region_lock_wait_nanos: lock_timings.lock_wait_nanos,
        region_lock_hold_nanos: lock_timings.lock_hold_nanos,
        rust_output_copy_nanos: 0,
        rust_ffi_total_nanos: 0,
    };
    if output_capacity < tape.len() as u64 {
        result.status = STATUS_OUTPUT_TOO_SMALL;
        result.error_domain = ERROR_DOMAIN_CHUNK;
        result.error_kind = ChunkErrorKind::OutputTooSmall as i32;
        return finish(output_result, result, ffi_started);
    }
    if !tape.is_empty() {
        let copy_started = Instant::now();
        unsafe {
            ptr::copy_nonoverlapping(tape.as_ptr(), output_ptr, tape.len());
        }
        result.rust_output_copy_nanos = elapsed_nanos(copy_started);
    }
    finish(output_result, result, ffi_started)
}

fn write_result(
    output: *mut NativeChunkSectionDecodeResult,
    result: NativeChunkSectionDecodeResult,
) {
    unsafe {
        *output = result;
    }
}

fn finish(
    output: *mut NativeChunkSectionDecodeResult,
    mut result: NativeChunkSectionDecodeResult,
    ffi_started: Instant,
) -> i32 {
    result.rust_ffi_total_nanos = elapsed_nanos(ffi_started);
    write_result(output, result);
    result.status
}

fn apply_lock_timings(result: &mut NativeChunkSectionDecodeResult, timings: RegionLockTimings) {
    result.region_handle_lookup_nanos = timings.handle_lookup_nanos;
    result.region_lock_wait_nanos = timings.lock_wait_nanos;
    result.region_lock_hold_nanos = timings.lock_hold_nanos;
}

fn is_invalid_handle(error: &RegionError) -> bool {
    error.kind == RegionErrorKind::InvalidArgument && error.message.contains("handle")
}

fn region_error_result(
    status: i32,
    error_domain: i32,
    error: RegionError,
) -> NativeChunkSectionDecodeResult {
    NativeChunkSectionDecodeResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativeChunkSectionDecodeResult::default()
    }
}

fn chunk_error_result(status: i32, error: ChunkError) -> NativeChunkSectionDecodeResult {
    NativeChunkSectionDecodeResult {
        status,
        error_domain: ERROR_DOMAIN_CHUNK,
        error_kind: error.kind as i32,
        ..NativeChunkSectionDecodeResult::default()
    }
}

fn elapsed_nanos(started: Instant) -> u64 {
    started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64
}

//! FFI boundary for dev/test chunk-section decoding.
//!
//! This API returns one bulk typed buffer per current-version chunk. Production
//! chunk loading and saving still use the Java `SerializableChunkData` path.

use std::ptr;
use std::slice;
use std::time::Instant;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::nbt::writer::write_document;
use crate::storage::region::decompress::compress_region_payload;
use crate::storage::region::decompress::decompress_region_payload;
use crate::storage::region::error::{RegionError, RegionErrorKind};
use crate::storage::region::ffi::with_open_region;
use crate::storage::region::ffi::{
    with_open_region_timed, RegionLockTimings, STATUS_DECOMPRESSION_ERROR, STATUS_INVALID_ARGUMENT,
    STATUS_INVALID_HANDLE, STATUS_NBT_ERROR, STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
};

use super::decoder::decode_unified_chunk_document;
use super::error::{ChunkError, ChunkErrorKind};
use super::tape::{encode_chunk_tape_with_residual_and_tick_bytes, encode_residual_tape};
use super::ticks::{decode_scheduled_ticks_document, encode_scheduled_tick_tape};
use super::writer::document_from_typed_chunk_tape_for_position;

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
    pub residual_tape_len: u64,
    pub residual_tape_creation_nanos: u64,
    pub region_handle_lookup_nanos: u64,
    pub region_lock_wait_nanos: u64,
    pub region_lock_hold_nanos: u64,
    pub rust_output_copy_nanos: u64,
    pub rust_ffi_total_nanos: u64,
    pub block_tick_count: u32,
    pub fluid_tick_count: u32,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeChunkSectionWriteResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub sector_count: i32,
    pub reserved: i32,
    pub timestamp: u64,
    pub sector_offset: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub tape_len: u64,
    pub merge_nanos: u64,
    pub nbt_encode_nanos: u64,
    pub compression_nanos: u64,
    pub region_write_nanos: u64,
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
    let unified = match decode_unified_chunk_document(&document, chunk_x, chunk_z) {
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
    let chunk = &unified.sections;

    let tape_started = Instant::now();
    let mut residual_tape_len = 0;
    let mut residual_tape_creation_nanos = 0;
    let mut block_tick_count = 0;
    let mut fluid_tick_count = 0;
    let tape = if chunk.requires_dfu {
        Vec::new()
    } else {
        let residual_started = Instant::now();
        let residual_tape = match encode_residual_tape(&unified.residual) {
            Ok(tape) => tape,
            Err(error) => {
                let mut result = chunk_error_result(STATUS_CHUNK_ERROR, error);
                result.region_read_nanos = region_read_nanos;
                result.decompression_nanos = decompression_nanos;
                result.nbt_parse_nanos = nbt_parse_nanos;
                result.chunk_decode_nanos = chunk_decode_nanos;
                result.residual_tape_creation_nanos = elapsed_nanos(residual_started);
                result.tape_creation_nanos = elapsed_nanos(tape_started);
                apply_lock_timings(&mut result, lock_timings);
                return finish(output_result, result, ffi_started);
            }
        };
        residual_tape_creation_nanos = elapsed_nanos(residual_started);
        residual_tape_len = residual_tape.len() as u64;
        let ticks = match decode_scheduled_ticks_document(&document, chunk_x, chunk_z) {
            Ok(ticks) => ticks,
            Err(error) => {
                let mut result = chunk_error_result(STATUS_CHUNK_ERROR, error);
                result.region_read_nanos = region_read_nanos;
                result.decompression_nanos = decompression_nanos;
                result.nbt_parse_nanos = nbt_parse_nanos;
                result.chunk_decode_nanos = chunk_decode_nanos;
                result.residual_tape_len = residual_tape_len;
                result.residual_tape_creation_nanos = residual_tape_creation_nanos;
                result.tape_creation_nanos = elapsed_nanos(tape_started);
                apply_lock_timings(&mut result, lock_timings);
                return finish(output_result, result, ffi_started);
            }
        };
        let tick_tape = match encode_scheduled_tick_tape(&ticks) {
            Ok(tape) => tape,
            Err(error) => {
                let mut result = chunk_error_result(STATUS_CHUNK_ERROR, error);
                result.region_read_nanos = region_read_nanos;
                result.decompression_nanos = decompression_nanos;
                result.nbt_parse_nanos = nbt_parse_nanos;
                result.chunk_decode_nanos = chunk_decode_nanos;
                result.residual_tape_len = residual_tape_len;
                result.residual_tape_creation_nanos = residual_tape_creation_nanos;
                result.tape_creation_nanos = elapsed_nanos(tape_started);
                apply_lock_timings(&mut result, lock_timings);
                return finish(output_result, result, ffi_started);
            }
        };
        block_tick_count = ticks.block_ticks.len() as u32;
        fluid_tick_count = ticks.fluid_ticks.len() as u32;
        match encode_chunk_tape_with_residual_and_tick_bytes(chunk, &residual_tape, &tick_tape) {
            Ok(tape) => tape,
            Err(error) => {
                let mut result = chunk_error_result(STATUS_CHUNK_ERROR, error);
                result.region_read_nanos = region_read_nanos;
                result.decompression_nanos = decompression_nanos;
                result.nbt_parse_nanos = nbt_parse_nanos;
                result.chunk_decode_nanos = chunk_decode_nanos;
                result.residual_tape_len = residual_tape_len;
                result.residual_tape_creation_nanos = residual_tape_creation_nanos;
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
        residual_tape_len,
        residual_tape_creation_nanos,
        region_handle_lookup_nanos: lock_timings.handle_lookup_nanos,
        region_lock_wait_nanos: lock_timings.lock_wait_nanos,
        region_lock_hold_nanos: lock_timings.lock_hold_nanos,
        rust_output_copy_nanos: 0,
        rust_ffi_total_nanos: 0,
        block_tick_count,
        fluid_tick_count,
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

/// # Safety
///
/// `tape_ptr` must be null only when `tape_len` is zero, otherwise it must
/// point to `tape_len` readable bytes containing one complete MattMC typed
/// chunk-section tape. `output_result` must point to one writable
/// `NativeChunkSectionWriteResult`. `handle` must be a live Rust region handle.
/// Rust copies Java-owned memory only during this call, merges the typed
/// section fields with residual NBT, encodes/compresses, and publishes one
/// complete region chunk payload atomically through the region backend.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_write_sections_to_region(
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
    output_result: *mut NativeChunkSectionWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = write_chunk_error_result(
            STATUS_INVALID_ARGUMENT,
            ERROR_DOMAIN_CHUNK,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "invalid compression id"),
        );
        write_write_result(output_result, result);
        return result.status;
    }
    let tape = match ptr_to_input(tape_ptr, tape_len) {
        Ok(tape) => tape,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let ffi_started = Instant::now();
    let nbt_limits = NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let compression_limits =
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes);

    let merge_started = Instant::now();
    let document = match document_from_typed_chunk_tape_for_position(
        tape,
        Some((chunk_x, chunk_z)),
        nbt_limits,
    ) {
        Ok(document) => document,
        Err(error) => {
            let mut result =
                write_chunk_error_result(STATUS_CHUNK_ERROR, ERROR_DOMAIN_CHUNK, error);
            result.tape_len = tape.len() as u64;
            result.merge_nanos = elapsed_nanos(merge_started);
            return finish_write(output_result, result, ffi_started);
        }
    };
    let merge_nanos = elapsed_nanos(merge_started);

    let encode_started = Instant::now();
    let encoded = match write_document(&document, nbt_limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = NativeChunkSectionWriteResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                tape_len: tape.len() as u64,
                merge_nanos,
                nbt_encode_nanos: elapsed_nanos(encode_started),
                ..NativeChunkSectionWriteResult::default()
            };
            return finish_write(output_result, result, ffi_started);
        }
    };
    let nbt_encode_nanos = elapsed_nanos(encode_started);

    let compression_started = Instant::now();
    let compressed =
        match compress_region_payload(compression_id as u8, &encoded, compression_limits) {
            Ok(compressed) => compressed,
            Err(error) => {
                let mut result = write_region_error_result(
                    STATUS_DECOMPRESSION_ERROR,
                    ERROR_DOMAIN_DECOMPRESSION,
                    error,
                );
                result.tape_len = tape.len() as u64;
                result.merge_nanos = merge_nanos;
                result.nbt_encode_nanos = nbt_encode_nanos;
                result.compression_nanos = elapsed_nanos(compression_started);
                return finish_write(output_result, result, ffi_started);
            }
        };
    let compression_nanos = elapsed_nanos(compression_started);

    let write_started = Instant::now();
    match with_open_region(handle, |region| {
        region.write_payload(chunk_x, chunk_z, compression_id as u8, &compressed)
    }) {
        Ok(write) => {
            let result = NativeChunkSectionWriteResult {
                status: STATUS_OK,
                error_domain: ERROR_DOMAIN_NONE,
                error_kind: 0,
                present: i32::from(write.present),
                compression_id: write.compression_id as i32,
                external: i32::from(write.external),
                sector_count: write.sector_count as i32,
                reserved: 0,
                timestamp: write.timestamp as u64,
                sector_offset: write.first_sector as u64,
                compressed_len: compressed.len() as u64,
                decompressed_len: encoded.len() as u64,
                tape_len: tape.len() as u64,
                merge_nanos,
                nbt_encode_nanos,
                compression_nanos,
                region_write_nanos: elapsed_nanos(write_started),
                rust_ffi_total_nanos: 0,
            };
            finish_write(output_result, result, ffi_started)
        }
        Err(error) => {
            let status = if is_invalid_handle(&error) {
                STATUS_INVALID_HANDLE
            } else {
                STATUS_REGION_ERROR
            };
            let mut result = write_region_error_result(status, ERROR_DOMAIN_REGION, error);
            result.tape_len = tape.len() as u64;
            result.merge_nanos = merge_nanos;
            result.nbt_encode_nanos = nbt_encode_nanos;
            result.compression_nanos = compression_nanos;
            result.region_write_nanos = elapsed_nanos(write_started);
            finish_write(output_result, result, ffi_started)
        }
    }
}

fn write_result(
    output: *mut NativeChunkSectionDecodeResult,
    result: NativeChunkSectionDecodeResult,
) {
    unsafe {
        *output = result;
    }
}

fn write_write_result(
    output: *mut NativeChunkSectionWriteResult,
    result: NativeChunkSectionWriteResult,
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

fn finish_write(
    output: *mut NativeChunkSectionWriteResult,
    mut result: NativeChunkSectionWriteResult,
    ffi_started: Instant,
) -> i32 {
    result.rust_ffi_total_nanos = elapsed_nanos(ffi_started);
    write_write_result(output, result);
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

fn write_chunk_error_result(
    status: i32,
    error_domain: i32,
    error: ChunkError,
) -> NativeChunkSectionWriteResult {
    NativeChunkSectionWriteResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        ..NativeChunkSectionWriteResult::default()
    }
}

fn write_region_error_result(
    status: i32,
    error_domain: i32,
    error: RegionError,
) -> NativeChunkSectionWriteResult {
    NativeChunkSectionWriteResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        ..NativeChunkSectionWriteResult::default()
    }
}

fn ptr_to_input<'a>(ptr: *const u8, len: u64) -> Result<&'a [u8], NativeChunkSectionWriteResult> {
    if len > usize::MAX as u64 {
        return Err(write_chunk_error_result(
            STATUS_INVALID_ARGUMENT,
            ERROR_DOMAIN_CHUNK,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "input length is too large"),
        ));
    }
    if len != 0 && ptr.is_null() {
        return Err(write_chunk_error_result(
            STATUS_INVALID_ARGUMENT,
            ERROR_DOMAIN_CHUNK,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "input pointer is null"),
        ));
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len as usize) })
}

fn elapsed_nanos(started: Instant) -> u64 {
    started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64
}

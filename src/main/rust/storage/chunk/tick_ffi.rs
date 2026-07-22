//! FFI boundary for dev/test scheduled-tick decoding and writes.
//!
//! These exports exercise a typed `block_ticks`/`fluid_ticks` boundary without
//! changing production chunk loading or saving.

use std::ptr;
use std::slice;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::nbt::writer::write_document;
use crate::storage::region::decompress::{compress_region_payload, decompress_region_payload};
use crate::storage::region::error::{RegionError, RegionErrorKind};
use crate::storage::region::ffi::{
    with_open_region, with_open_region_timed, STATUS_DECOMPRESSION_ERROR, STATUS_INVALID_ARGUMENT,
    STATUS_INVALID_HANDLE, STATUS_NBT_ERROR, STATUS_OK, STATUS_OUTPUT_TOO_SMALL,
};

use super::error::{ChunkError, ChunkErrorKind};
use super::ticks::{
    decode_scheduled_ticks_document, encode_scheduled_tick_tape, merge_scheduled_ticks_from_tapes,
};

const STATUS_REGION_ERROR: i32 = -3;
const STATUS_CHUNK_ERROR: i32 = -8;

const ERROR_DOMAIN_NONE: i32 = 0;
const ERROR_DOMAIN_REGION: i32 = 1;
const ERROR_DOMAIN_DECOMPRESSION: i32 = 2;
const ERROR_DOMAIN_NBT: i32 = 3;
const ERROR_DOMAIN_CHUNK: i32 = 6;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeChunkTickDecodeResult {
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
    pub block_tick_count: u32,
    pub fluid_tick_count: u32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub output_len: u64,
    pub error_offset: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeChunkTickWriteResult {
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
    pub residual_tape_len: u64,
    pub tick_tape_len: u64,
}

/// # Safety
///
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// Rust reports the required scheduled-tick tape length without copying.
/// `output_result` must point to one writable `NativeChunkTickDecodeResult`.
/// `handle` must be a live Rust region handle. Rust copies no Java-owned
/// pointers after this call returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_decode_scheduled_ticks_from_region(
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
    output_result: *mut NativeChunkTickDecodeResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = tick_error_result(
            STATUS_INVALID_ARGUMENT,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "output pointer is null"),
        );
        write_decode_result(output_result, result);
        return result.status;
    }

    let payload =
        match with_open_region_timed(handle, |region| region.read_payload(chunk_x, chunk_z)) {
            Ok((payload, _timings)) => payload,
            Err(error) => {
                let status = if is_invalid_handle(&error) {
                    STATUS_INVALID_HANDLE
                } else {
                    STATUS_REGION_ERROR
                };
                let result = region_error_decode_result(status, ERROR_DOMAIN_REGION, error);
                write_decode_result(output_result, result);
                return result.status;
            }
        };
    let Some(payload) = payload else {
        let result = NativeChunkTickDecodeResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            ..NativeChunkTickDecodeResult::default()
        };
        write_decode_result(output_result, result);
        return STATUS_OK;
    };

    let decoded = match decompress_region_payload(
        payload.compression_id,
        &payload.payload,
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes),
    ) {
        Ok(decoded) => decoded,
        Err(error) => {
            let result = region_error_decode_result(
                STATUS_DECOMPRESSION_ERROR,
                ERROR_DOMAIN_DECOMPRESSION,
                error,
            );
            write_decode_result(output_result, result);
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
            let result = NativeChunkTickDecodeResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                error_offset: error.offset as u64,
                ..NativeChunkTickDecodeResult::default()
            };
            write_decode_result(output_result, result);
            return result.status;
        }
    };

    let ticks = match decode_scheduled_ticks_document(&document, chunk_x, chunk_z) {
        Ok(ticks) => ticks,
        Err(error) => {
            let result = tick_error_result(STATUS_CHUNK_ERROR, error);
            write_decode_result(output_result, result);
            return result.status;
        }
    };
    let tape = if ticks.requires_dfu {
        Vec::new()
    } else {
        match encode_scheduled_tick_tape(&ticks) {
            Ok(tape) => tape,
            Err(error) => {
                let result = tick_error_result(STATUS_CHUNK_ERROR, error);
                write_decode_result(output_result, result);
                return result.status;
            }
        }
    };

    let mut result = NativeChunkTickDecodeResult {
        status: STATUS_OK,
        error_domain: ERROR_DOMAIN_NONE,
        present: 1,
        requires_dfu: i32::from(ticks.requires_dfu),
        compression_id: payload.compression_id as i32,
        external: i32::from(payload.external),
        data_version: ticks.data_version,
        chunk_x: ticks.chunk_x,
        chunk_z: ticks.chunk_z,
        block_tick_count: ticks.block_ticks.len() as u32,
        fluid_tick_count: ticks.fluid_ticks.len() as u32,
        timestamp: payload.timestamp as u64,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        output_len: tape.len() as u64,
        ..NativeChunkTickDecodeResult::default()
    };
    if output_capacity < tape.len() as u64 {
        result.status = STATUS_OUTPUT_TOO_SMALL;
        result.error_domain = ERROR_DOMAIN_CHUNK;
        result.error_kind = ChunkErrorKind::OutputTooSmall as i32;
        write_decode_result(output_result, result);
        return result.status;
    }
    if !tape.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(tape.as_ptr(), output_ptr, tape.len());
        }
    }
    write_decode_result(output_result, result);
    result.status
}

/// # Safety
///
/// `residual_tape_ptr` and `tick_tape_ptr` must be null only when their
/// corresponding lengths are zero, otherwise they must point to readable bytes
/// for the duration of the call. `output_result` must point to one writable
/// `NativeChunkTickWriteResult`. `handle` must be a live Rust region handle.
/// Rust does not retain Java-owned memory after returning.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_write_scheduled_ticks_to_region(
    handle: u64,
    chunk_x: i32,
    chunk_z: i32,
    compression_id: i32,
    residual_tape_ptr: *const u8,
    residual_tape_len: u64,
    tick_tape_ptr: *const u8,
    tick_tape_len: u64,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeChunkTickWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = write_tick_error_result(
            STATUS_INVALID_ARGUMENT,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "invalid compression id"),
        );
        write_write_result(output_result, result);
        return result.status;
    }
    let residual_tape = match ptr_to_input(residual_tape_ptr, residual_tape_len) {
        Ok(bytes) => bytes,
        Err(result) => {
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let tick_tape = match ptr_to_input(tick_tape_ptr, tick_tape_len) {
        Ok(bytes) => bytes,
        Err(result) => {
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
    let compression_limits =
        CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let document = match merge_scheduled_ticks_from_tapes(
        residual_tape,
        tick_tape,
        Some((chunk_x, chunk_z)),
        nbt_limits,
    ) {
        Ok(document) => document,
        Err(error) => {
            let mut result = write_tick_error_result(STATUS_CHUNK_ERROR, error);
            result.residual_tape_len = residual_tape.len() as u64;
            result.tick_tape_len = tick_tape.len() as u64;
            write_write_result(output_result, result);
            return result.status;
        }
    };

    let encoded = match write_document(&document, nbt_limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = NativeChunkTickWriteResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                residual_tape_len: residual_tape.len() as u64,
                tick_tape_len: tick_tape.len() as u64,
                ..NativeChunkTickWriteResult::default()
            };
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let compressed =
        match compress_region_payload(compression_id as u8, &encoded, compression_limits) {
            Ok(compressed) => compressed,
            Err(error) => {
                let mut result = write_region_error_result(STATUS_DECOMPRESSION_ERROR, error);
                result.residual_tape_len = residual_tape.len() as u64;
                result.tick_tape_len = tick_tape.len() as u64;
                write_write_result(output_result, result);
                return result.status;
            }
        };

    match with_open_region(handle, |region| {
        region.write_payload(chunk_x, chunk_z, compression_id as u8, &compressed)
    }) {
        Ok(write) => {
            let result = NativeChunkTickWriteResult {
                status: STATUS_OK,
                error_domain: ERROR_DOMAIN_NONE,
                present: i32::from(write.present),
                compression_id: write.compression_id as i32,
                external: i32::from(write.external),
                sector_count: write.sector_count as i32,
                timestamp: write.timestamp as u64,
                sector_offset: write.first_sector as u64,
                compressed_len: compressed.len() as u64,
                decompressed_len: encoded.len() as u64,
                residual_tape_len: residual_tape.len() as u64,
                tick_tape_len: tick_tape.len() as u64,
                ..NativeChunkTickWriteResult::default()
            };
            write_write_result(output_result, result);
            STATUS_OK
        }
        Err(error) => {
            let status = if is_invalid_handle(&error) {
                STATUS_INVALID_HANDLE
            } else {
                STATUS_REGION_ERROR
            };
            let mut result = write_region_error_result(status, error);
            result.residual_tape_len = residual_tape.len() as u64;
            result.tick_tape_len = tick_tape.len() as u64;
            write_write_result(output_result, result);
            result.status
        }
    }
}

fn write_decode_result(
    output: *mut NativeChunkTickDecodeResult,
    result: NativeChunkTickDecodeResult,
) {
    unsafe {
        *output = result;
    }
}

fn write_write_result(output: *mut NativeChunkTickWriteResult, result: NativeChunkTickWriteResult) {
    unsafe {
        *output = result;
    }
}

fn is_invalid_handle(error: &RegionError) -> bool {
    error.kind == RegionErrorKind::InvalidArgument && error.message.contains("handle")
}

fn region_error_decode_result(
    status: i32,
    error_domain: i32,
    error: RegionError,
) -> NativeChunkTickDecodeResult {
    NativeChunkTickDecodeResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativeChunkTickDecodeResult::default()
    }
}

fn tick_error_result(status: i32, error: ChunkError) -> NativeChunkTickDecodeResult {
    NativeChunkTickDecodeResult {
        status,
        error_domain: ERROR_DOMAIN_CHUNK,
        error_kind: error.kind as i32,
        ..NativeChunkTickDecodeResult::default()
    }
}

fn write_tick_error_result(status: i32, error: ChunkError) -> NativeChunkTickWriteResult {
    NativeChunkTickWriteResult {
        status,
        error_domain: ERROR_DOMAIN_CHUNK,
        error_kind: error.kind as i32,
        ..NativeChunkTickWriteResult::default()
    }
}

fn write_region_error_result(status: i32, error: RegionError) -> NativeChunkTickWriteResult {
    NativeChunkTickWriteResult {
        status,
        error_domain: ERROR_DOMAIN_REGION,
        error_kind: error.kind as i32,
        ..NativeChunkTickWriteResult::default()
    }
}

fn ptr_to_input<'a>(ptr: *const u8, len: u64) -> Result<&'a [u8], NativeChunkTickWriteResult> {
    if len > usize::MAX as u64 {
        return Err(write_tick_error_result(
            STATUS_INVALID_ARGUMENT,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "input length is too large"),
        ));
    }
    if len != 0 && ptr.is_null() {
        return Err(write_tick_error_result(
            STATUS_INVALID_ARGUMENT,
            ChunkError::new(ChunkErrorKind::InvalidArgument, "input pointer is null"),
        ));
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len as usize) })
}

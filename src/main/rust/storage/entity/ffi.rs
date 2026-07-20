//! FFI boundary for dev/test entity-chunk envelope decoding.
//!
//! This API returns one bulk typed buffer per entity chunk. It intentionally
//! avoids per-entity and per-tag calls and is not used by production entity
//! loading or saving.

use std::ptr;
use std::slice;
use std::time::Instant;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::fingerprint::fingerprint_document;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;
use crate::storage::nbt::writer::write_document;
use crate::storage::region::decompress::compress_region_payload;
use crate::storage::region::decompress::decompress_region_payload;
use crate::storage::region::error::{RegionError, RegionErrorKind};
use crate::storage::region::ffi::{
    with_open_region, with_open_region_timed, RegionLockTimings, STATUS_DECOMPRESSION_ERROR,
    STATUS_INVALID_ARGUMENT, STATUS_INVALID_HANDLE, STATUS_NBT_ERROR, STATUS_OK,
    STATUS_OUTPUT_TOO_SMALL,
};

use super::decoder::decode_entity_document_with_timing;
use super::encoder::encode_entity_document_from_tape;
use super::error::{EntityError, EntityErrorKind};
use super::tape::{decode_entity_tape, encode_entity_tape};

const STATUS_REGION_ERROR: i32 = -3;
const STATUS_ENTITY_ERROR: i32 = -8;

const ERROR_DOMAIN_NONE: i32 = 0;
const ERROR_DOMAIN_REGION: i32 = 1;
const ERROR_DOMAIN_DECOMPRESSION: i32 = 2;
const ERROR_DOMAIN_NBT: i32 = 3;
const ERROR_DOMAIN_ENTITY: i32 = 5;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeEntityDecodeResult {
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
    pub entity_count: u32,
    pub reserved: u32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub output_len: u64,
    pub error_offset: u64,
    pub region_read_nanos: u64,
    pub decompression_nanos: u64,
    pub nbt_parse_nanos: u64,
    pub envelope_traversal_nanos: u64,
    pub tape_creation_nanos: u64,
    pub region_handle_lookup_nanos: u64,
    pub region_lock_wait_nanos: u64,
    pub region_lock_hold_nanos: u64,
    pub rust_output_copy_nanos: u64,
    pub rust_ffi_total_nanos: u64,
}

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeEntityWriteResult {
    pub status: i32,
    pub error_domain: i32,
    pub error_kind: i32,
    pub present: i32,
    pub compression_id: i32,
    pub external: i32,
    pub entity_count: u32,
    pub reserved: u32,
    pub timestamp: u64,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub fingerprint: u64,
    pub error_offset: u64,
}

/// # Safety
///
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// Rust reports the required entity-envelope tape length without copying.
/// `output_result` must point to one writable `NativeEntityDecodeResult`.
/// `handle` must be a live Rust region handle. Rust does not retain Java-owned
/// pointers after this call returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_decode_chunk_envelope_from_region(
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
    output_result: *mut NativeEntityDecodeResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = entity_error_result(
            STATUS_INVALID_ARGUMENT,
            EntityError::new(EntityErrorKind::InvalidArgument, "output pointer is null"),
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
        let result = NativeEntityDecodeResult {
            status: STATUS_OK,
            error_domain: ERROR_DOMAIN_NONE,
            region_read_nanos,
            region_handle_lookup_nanos: lock_timings.handle_lookup_nanos,
            region_lock_wait_nanos: lock_timings.lock_wait_nanos,
            region_lock_hold_nanos: lock_timings.lock_hold_nanos,
            ..NativeEntityDecodeResult::default()
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
            let mut result = NativeEntityDecodeResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                error_offset: error.offset as u64,
                region_read_nanos,
                decompression_nanos,
                nbt_parse_nanos: elapsed_nanos(nbt_started),
                ..NativeEntityDecodeResult::default()
            };
            apply_lock_timings(&mut result, lock_timings);
            return finish(output_result, result, ffi_started);
        }
    };
    let nbt_parse_nanos = elapsed_nanos(nbt_started);

    let (chunk, entity_timings) = match decode_entity_document_with_timing(&document) {
        Ok(decoded) => decoded,
        Err(error) => {
            let mut result = entity_error_result(STATUS_ENTITY_ERROR, error);
            result.region_read_nanos = region_read_nanos;
            result.decompression_nanos = decompression_nanos;
            result.nbt_parse_nanos = nbt_parse_nanos;
            apply_lock_timings(&mut result, lock_timings);
            return finish(output_result, result, ffi_started);
        }
    };
    let tape = if chunk.requires_dfu {
        Vec::new()
    } else {
        match encode_entity_tape(&chunk) {
            Ok(tape) => tape,
            Err(error) => {
                let mut result = entity_error_result(STATUS_ENTITY_ERROR, error);
                result.region_read_nanos = region_read_nanos;
                result.decompression_nanos = decompression_nanos;
                result.nbt_parse_nanos = nbt_parse_nanos;
                result.envelope_traversal_nanos = entity_timings.envelope_traversal_nanos;
                result.tape_creation_nanos = entity_timings.tape_creation_nanos;
                apply_lock_timings(&mut result, lock_timings);
                return finish(output_result, result, ffi_started);
            }
        }
    };

    let mut result = NativeEntityDecodeResult {
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
        entity_count: chunk.entities.len() as u32,
        reserved: 0,
        timestamp: payload.timestamp as u64,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        output_len: tape.len() as u64,
        error_offset: 0,
        region_read_nanos,
        decompression_nanos,
        nbt_parse_nanos,
        envelope_traversal_nanos: entity_timings.envelope_traversal_nanos,
        tape_creation_nanos: entity_timings.tape_creation_nanos,
        region_handle_lookup_nanos: lock_timings.handle_lookup_nanos,
        region_lock_wait_nanos: lock_timings.lock_wait_nanos,
        region_lock_hold_nanos: lock_timings.lock_hold_nanos,
        rust_output_copy_nanos: 0,
        rust_ffi_total_nanos: 0,
    };
    if output_capacity < tape.len() as u64 {
        result.status = STATUS_OUTPUT_TOO_SMALL;
        result.error_domain = ERROR_DOMAIN_ENTITY;
        result.error_kind = EntityErrorKind::OutputTooSmall as i32;
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
/// point to `tape_len` readable bytes containing one complete entity-envelope
/// tape for the duration of the call. `output_result` must point to one
/// writable `NativeEntityWriteResult`. `handle` must be a live Rust region
/// handle. Rust copies no Java pointers out of the call; it decodes the tape,
/// builds one current-version entity chunk NBT document, compresses it, and
/// writes it through the persistent region handle before returning.
#[no_mangle]
pub unsafe extern "C" fn mattmc_entity_write_chunk_envelope_to_region(
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
    output_result: *mut NativeEntityWriteResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if !(0..=u8::MAX as i32).contains(&compression_id) {
        let result = write_entity_error_result(
            STATUS_INVALID_ARGUMENT,
            EntityError::new(EntityErrorKind::InvalidArgument, "invalid compression id"),
        );
        write_write_result(output_result, result);
        return result.status;
    }
    if tape_len > usize::MAX as u64 || (tape_len != 0 && tape_ptr.is_null()) {
        let result = write_entity_error_result(
            STATUS_INVALID_ARGUMENT,
            EntityError::new(
                EntityErrorKind::InvalidArgument,
                "invalid entity tape pointer or length",
            ),
        );
        write_write_result(output_result, result);
        return result.status;
    }
    let tape = if tape_len == 0 {
        &[][..]
    } else {
        unsafe { slice::from_raw_parts(tape_ptr, tape_len as usize) }
    };
    let chunk = match decode_entity_tape(tape) {
        Ok(chunk) => chunk,
        Err(error) => {
            let result = write_entity_error_result(STATUS_ENTITY_ERROR, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    if chunk.chunk_x != chunk_x || chunk.chunk_z != chunk_z {
        let result = write_entity_error_result(
            STATUS_ENTITY_ERROR,
            EntityError::new(
                EntityErrorKind::InvalidPosition,
                "entity tape chunk position does not match write coordinates",
            ),
        );
        write_write_result(output_result, result);
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
    let document = match encode_entity_document_from_tape(&chunk, nbt_limits) {
        Ok(document) => document,
        Err(error) => {
            let result = write_entity_error_result(STATUS_ENTITY_ERROR, error);
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let encoded = match write_document(&document, nbt_limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = NativeEntityWriteResult {
                status: STATUS_NBT_ERROR,
                error_domain: ERROR_DOMAIN_NBT,
                error_kind: error.kind as i32,
                error_offset: error.offset as u64,
                ..NativeEntityWriteResult::default()
            };
            write_write_result(output_result, result);
            return result.status;
        }
    };
    let compressed =
        match compress_region_payload(compression_id as u8, &encoded, compression_limits) {
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
            let output = NativeEntityWriteResult {
                status: STATUS_OK,
                error_domain: ERROR_DOMAIN_NONE,
                error_kind: 0,
                present: 1,
                compression_id: result.compression_id as i32,
                external: i32::from(result.external),
                entity_count: chunk.entities.len() as u32,
                reserved: 0,
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

fn write_result(output: *mut NativeEntityDecodeResult, result: NativeEntityDecodeResult) {
    unsafe {
        *output = result;
    }
}

fn write_write_result(output: *mut NativeEntityWriteResult, result: NativeEntityWriteResult) {
    unsafe {
        *output = result;
    }
}

fn finish(
    output: *mut NativeEntityDecodeResult,
    mut result: NativeEntityDecodeResult,
    ffi_started: Instant,
) -> i32 {
    result.rust_ffi_total_nanos = elapsed_nanos(ffi_started);
    write_result(output, result);
    result.status
}

fn apply_lock_timings(result: &mut NativeEntityDecodeResult, timings: RegionLockTimings) {
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
) -> NativeEntityDecodeResult {
    NativeEntityDecodeResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativeEntityDecodeResult::default()
    }
}

fn entity_error_result(status: i32, error: EntityError) -> NativeEntityDecodeResult {
    NativeEntityDecodeResult {
        status,
        error_domain: ERROR_DOMAIN_ENTITY,
        error_kind: error.kind as i32,
        ..NativeEntityDecodeResult::default()
    }
}

fn write_entity_error_result(status: i32, error: EntityError) -> NativeEntityWriteResult {
    NativeEntityWriteResult {
        status,
        error_domain: ERROR_DOMAIN_ENTITY,
        error_kind: error.kind as i32,
        ..NativeEntityWriteResult::default()
    }
}

fn write_region_error_result(
    status: i32,
    error_domain: i32,
    error: RegionError,
) -> NativeEntityWriteResult {
    NativeEntityWriteResult {
        status,
        error_domain,
        error_kind: error.kind as i32,
        error_offset: error.offset,
        ..NativeEntityWriteResult::default()
    }
}

fn elapsed_nanos(started: Instant) -> u64 {
    started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64
}

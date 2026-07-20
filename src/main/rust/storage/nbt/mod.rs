pub mod compression;
pub mod error;
pub mod fingerprint;
pub mod limits;
pub mod model;
pub mod modified_utf8;
pub mod reader;
pub mod tape;
pub mod writer;

#[cfg(test)]
mod tests;

use std::ptr;
use std::slice;

use self::compression::{decode, encode, CompressionLimits, NbtCompression};
use self::error::{NbtError, NbtErrorKind};
use self::fingerprint::fingerprint_document;
use self::limits::NbtLimits;
use self::reader::read_document;
use self::tape::{document_from_tape, document_to_tape};
use self::writer::write_document;

pub const STATUS_OK: i32 = 0;
pub const STATUS_INVALID_ARGUMENT: i32 = -1;
pub const STATUS_PARSE_ERROR: i32 = -2;
pub const STATUS_WRITE_ERROR: i32 = -3;
pub const STATUS_OUTPUT_TOO_SMALL: i32 = -4;
pub const STATUS_COMPRESSION_ERROR: i32 = -5;

#[repr(C)]
#[derive(Clone, Copy, Debug, Default)]
pub struct NativeNbtResult {
    pub status: i32,
    pub error_kind: i32,
    pub offset: u64,
    pub fingerprint: u64,
    pub output_len: u64,
}

fn write_result(output: *mut NativeNbtResult, result: NativeNbtResult) -> bool {
    if output.is_null() {
        return false;
    }
    unsafe {
        *output = result;
    }
    true
}

fn error_result(status: i32, error: NbtError) -> NativeNbtResult {
    NativeNbtResult {
        status,
        error_kind: error.kind as i32,
        offset: error.offset as u64,
        fingerprint: 0,
        output_len: 0,
    }
}

fn compression_status(error: NbtError) -> i32 {
    match error.kind {
        NbtErrorKind::UnsupportedCompression
        | NbtErrorKind::CompressionError
        | NbtErrorKind::CompressedSizeLimit
        | NbtErrorKind::DecompressedSizeLimit
        | NbtErrorKind::TrailingCompressedData => STATUS_COMPRESSION_ERROR,
        _ => STATUS_PARSE_ERROR,
    }
}

fn ptr_to_bytes<'a>(ptr: *const u8, len: u64) -> Result<&'a [u8], NativeNbtResult> {
    if len > usize::MAX as u64 {
        return Err(NativeNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: NbtErrorKind::InvalidLength as i32,
            offset: 0,
            fingerprint: 0,
            output_len: 0,
        });
    }
    if len == 0 {
        return Ok(&[]);
    }
    if len != 0 && ptr.is_null() {
        return Err(NativeNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: NbtErrorKind::InvalidArgument as i32,
            offset: 0,
            fingerprint: 0,
            output_len: 0,
        });
    }
    Ok(unsafe { slice::from_raw_parts(ptr, len as usize) })
}

fn compression_limits_from_ffi(
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
) -> CompressionLimits {
    CompressionLimits::from_ffi(max_compressed_bytes, max_decompressed_bytes)
}

fn limits_from_ffi(
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
) -> NbtLimits {
    NbtLimits::from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    )
}

/// # Safety
///
/// `input_ptr` must be null only when `input_len` is zero, otherwise it must
/// point to `input_len` readable bytes for the duration of the call.
/// `output_result` must point to one writable `NativeNbtResult`. Rust does not
/// retain Java-owned memory.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_parse_fingerprint(
    input_ptr: *const u8,
    input_len: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let input = match ptr_to_bytes(input_ptr, input_len) {
        Ok(input) => input,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let limits = limits_from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match read_document(input, limits) {
        Ok(document) => document,
        Err(error) => {
            let result = error_result(STATUS_PARSE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let result = NativeNbtResult {
        status: STATUS_OK,
        error_kind: 0,
        offset: input.len() as u64,
        fingerprint: fingerprint_document(&document),
        output_len: 0,
    };
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `input_ptr` must be null only when `input_len` is zero, otherwise it must
/// point to `input_len` readable bytes. `output_ptr` may be null only when
/// `output_capacity` is zero; in that mode the required encoded length is
/// returned without copying bytes. `output_result` must point to one writable
/// `NativeNbtResult`. Rust does not retain Java-owned memory.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_reencode(
    input_ptr: *const u8,
    input_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: NbtErrorKind::InvalidArgument as i32,
            offset: 0,
            fingerprint: 0,
            output_len: 0,
        };
        write_result(output_result, result);
        return result.status;
    }
    let input = match ptr_to_bytes(input_ptr, input_len) {
        Ok(input) => input,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let limits = limits_from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match read_document(input, limits) {
        Ok(document) => document,
        Err(error) => {
            let result = error_result(STATUS_PARSE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let encoded = match write_document(&document, limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = error_result(STATUS_WRITE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let fingerprint = fingerprint_document(&document);
    if output_capacity < encoded.len() as u64 {
        let result = NativeNbtResult {
            status: STATUS_OUTPUT_TOO_SMALL,
            error_kind: NbtErrorKind::OutputTooSmall as i32,
            offset: input.len() as u64,
            fingerprint,
            output_len: encoded.len() as u64,
        };
        write_result(output_result, result);
        return result.status;
    }
    if !encoded.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(encoded.as_ptr(), output_ptr, encoded.len());
        }
    }
    let result = NativeNbtResult {
        status: STATUS_OK,
        error_kind: 0,
        offset: input.len() as u64,
        fingerprint,
        output_len: encoded.len() as u64,
    };
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `input_ptr` must be null only when `input_len` is zero, otherwise it must
/// point to `input_len` readable bytes. `input_compression` may be `-1` for
/// auto-detection, raw, gzip, or zlib. `output_result` must point to one
/// writable `NativeNbtResult`. Rust does not retain Java-owned memory.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_decode_fingerprint(
    input_ptr: *const u8,
    input_len: u64,
    input_compression: i32,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    let input = match ptr_to_bytes(input_ptr, input_len) {
        Ok(input) => input,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let requested = match NbtCompression::from_ffi(input_compression) {
        Ok(format) => format,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let compression_limits =
        compression_limits_from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let (format, decoded) = match decode(input, requested, compression_limits) {
        Ok(decoded) => decoded,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let limits = limits_from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match read_document(&decoded, limits) {
        Ok(document) => document,
        Err(error) => {
            let result = error_result(STATUS_PARSE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let result = NativeNbtResult {
        status: STATUS_OK,
        error_kind: format as i32,
        offset: decoded.len() as u64,
        fingerprint: fingerprint_document(&document),
        output_len: 0,
    };
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `input_ptr` must be null only when `input_len` is zero, otherwise it must
/// point to `input_len` readable bytes. `output_ptr` may be null only when
/// `output_capacity` is zero; in that mode the required recompressed length is
/// returned without copying bytes. Compression formats are whole-buffer only:
/// Java-owned memory is never retained and no per-tag calls are made.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_recompress(
    input_ptr: *const u8,
    input_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    input_compression: i32,
    output_compression: i32,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: NbtErrorKind::InvalidArgument as i32,
            offset: 0,
            fingerprint: 0,
            output_len: 0,
        };
        write_result(output_result, result);
        return result.status;
    }
    let input = match ptr_to_bytes(input_ptr, input_len) {
        Ok(input) => input,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let requested_input = match NbtCompression::from_ffi(input_compression) {
        Ok(format) => format,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let requested_output = match NbtCompression::from_ffi(output_compression) {
        Ok(NbtCompression::Auto) => {
            let error = NbtError::new(NbtErrorKind::UnsupportedCompression, 0);
            let result = error_result(STATUS_COMPRESSION_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
        Ok(format) => format,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let compression_limits =
        compression_limits_from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let (_, decoded) = match decode(input, requested_input, compression_limits) {
        Ok(decoded) => decoded,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let limits = limits_from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match read_document(&decoded, limits) {
        Ok(document) => document,
        Err(error) => {
            let result = error_result(STATUS_PARSE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let encoded = match write_document(&document, limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = error_result(STATUS_WRITE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let recompressed = match encode(
        &encoded,
        requested_output,
        compression_limits.max_compressed_bytes,
    ) {
        Ok(encoded) => encoded,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let fingerprint = fingerprint_document(&document);
    if output_capacity < recompressed.len() as u64 {
        let result = NativeNbtResult {
            status: STATUS_OUTPUT_TOO_SMALL,
            error_kind: NbtErrorKind::OutputTooSmall as i32,
            offset: decoded.len() as u64,
            fingerprint,
            output_len: recompressed.len() as u64,
        };
        write_result(output_result, result);
        return result.status;
    }
    if !recompressed.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(recompressed.as_ptr(), output_ptr, recompressed.len());
        }
    }
    let result = NativeNbtResult {
        status: STATUS_OK,
        error_kind: requested_output as i32,
        offset: decoded.len() as u64,
        fingerprint,
        output_len: recompressed.len() as u64,
    };
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `input_ptr` must be null only when `input_len` is zero, otherwise it must
/// point to `input_len` readable bytes. `output_ptr` may be null only when
/// `output_capacity` is zero; in that mode the required tape length is returned
/// without copying bytes. `output_result` must point to one writable result.
/// Rust copies all input bytes it needs during the call and never retains Java
/// pointers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_decode_to_tape(
    input_ptr: *const u8,
    input_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    input_compression: i32,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: NbtErrorKind::InvalidArgument as i32,
            offset: 0,
            fingerprint: 0,
            output_len: 0,
        };
        write_result(output_result, result);
        return result.status;
    }
    let input = match ptr_to_bytes(input_ptr, input_len) {
        Ok(input) => input,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let requested = match NbtCompression::from_ffi(input_compression) {
        Ok(format) => format,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let compression_limits =
        compression_limits_from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let (format, decoded) = match decode(input, requested, compression_limits) {
        Ok(decoded) => decoded,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let limits = limits_from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match read_document(&decoded, limits) {
        Ok(document) => document,
        Err(error) => {
            let result = error_result(STATUS_PARSE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let tape = match document_to_tape(&document, limits) {
        Ok(tape) => tape,
        Err(error) => {
            let result = error_result(STATUS_WRITE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let fingerprint = fingerprint_document(&document);
    if output_capacity < tape.len() as u64 {
        let result = NativeNbtResult {
            status: STATUS_OUTPUT_TOO_SMALL,
            error_kind: NbtErrorKind::OutputTooSmall as i32,
            offset: decoded.len() as u64,
            fingerprint,
            output_len: tape.len() as u64,
        };
        write_result(output_result, result);
        return result.status;
    }
    if !tape.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(tape.as_ptr(), output_ptr, tape.len());
        }
    }
    let result = NativeNbtResult {
        status: STATUS_OK,
        error_kind: format as i32,
        offset: decoded.len() as u64,
        fingerprint,
        output_len: tape.len() as u64,
    };
    write_result(output_result, result);
    STATUS_OK
}

/// # Safety
///
/// `tape_ptr` must be null only when `tape_len` is zero, otherwise it must
/// point to `tape_len` readable bytes containing one complete MattMC NBT tape.
/// `output_ptr` may be null only when `output_capacity` is zero; in that mode
/// the required encoded length is returned without copying bytes.
/// `output_result` must point to one writable result. Rust retains no
/// Java-owned memory after the call returns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_nbt_encode_from_tape(
    tape_ptr: *const u8,
    tape_len: u64,
    output_ptr: *mut u8,
    output_capacity: u64,
    output_compression: i32,
    max_compressed_bytes: u64,
    max_decompressed_bytes: u64,
    max_depth: u32,
    max_collection_len: u32,
    max_alloc_bytes: u64,
    max_total_bytes: u64,
    output_result: *mut NativeNbtResult,
) -> i32 {
    if output_result.is_null() {
        return STATUS_INVALID_ARGUMENT;
    }
    if output_capacity != 0 && output_ptr.is_null() {
        let result = NativeNbtResult {
            status: STATUS_INVALID_ARGUMENT,
            error_kind: NbtErrorKind::InvalidArgument as i32,
            offset: 0,
            fingerprint: 0,
            output_len: 0,
        };
        write_result(output_result, result);
        return result.status;
    }
    let tape = match ptr_to_bytes(tape_ptr, tape_len) {
        Ok(input) => input,
        Err(result) => {
            write_result(output_result, result);
            return result.status;
        }
    };
    let requested_output = match NbtCompression::from_ffi(output_compression) {
        Ok(NbtCompression::Auto) => {
            let error = NbtError::new(NbtErrorKind::UnsupportedCompression, 0);
            let result = error_result(STATUS_COMPRESSION_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
        Ok(format) => format,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let compression_limits =
        compression_limits_from_ffi(max_compressed_bytes, max_decompressed_bytes);
    let limits = limits_from_ffi(
        max_depth,
        max_collection_len,
        max_alloc_bytes,
        max_total_bytes,
    );
    let document = match document_from_tape(tape, limits) {
        Ok(document) => document,
        Err(error) => {
            let result = error_result(STATUS_PARSE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let encoded = match write_document(&document, limits) {
        Ok(encoded) => encoded,
        Err(error) => {
            let result = error_result(STATUS_WRITE_ERROR, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let output = match encode(
        &encoded,
        requested_output,
        compression_limits.max_compressed_bytes,
    ) {
        Ok(output) => output,
        Err(error) => {
            let status = compression_status(error);
            let result = error_result(status, error);
            write_result(output_result, result);
            return result.status;
        }
    };
    let fingerprint = fingerprint_document(&document);
    if output_capacity < output.len() as u64 {
        let result = NativeNbtResult {
            status: STATUS_OUTPUT_TOO_SMALL,
            error_kind: NbtErrorKind::OutputTooSmall as i32,
            offset: tape.len() as u64,
            fingerprint,
            output_len: output.len() as u64,
        };
        write_result(output_result, result);
        return result.status;
    }
    if !output.is_empty() {
        unsafe {
            ptr::copy_nonoverlapping(output.as_ptr(), output_ptr, output.len());
        }
    }
    let result = NativeNbtResult {
        status: STATUS_OK,
        error_kind: requested_output as i32,
        offset: tape.len() as u64,
        fingerprint,
        output_len: output.len() as u64,
    };
    write_result(output_result, result);
    STATUS_OK
}

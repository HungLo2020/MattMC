use lz4_flex::block;
use xxhash_rust::xxh32::xxh32;

use crate::storage::nbt::compression::{decode, encode, CompressionLimits, NbtCompression};
use crate::storage::nbt::error::NbtErrorKind;

use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::format::{COMPRESSION_GZIP, COMPRESSION_LZ4, COMPRESSION_RAW, COMPRESSION_ZLIB};

const LZ4_BLOCK_MAGIC: &[u8; 8] = b"LZ4Block";
const LZ4_BLOCK_HEADER_LEN: usize = 21;
const LZ4_METHOD_RAW: u8 = 0x10;
const LZ4_METHOD_LZ4: u8 = 0x20;
const LZ4_JAVA_XXHASH_SEED: u32 = 0x9747_b28c;
const LZ4_JAVA_CHECKSUM_MASK: u32 = 0x0fff_ffff;

pub fn decompress_region_payload(
    compression_id: u8,
    payload: &[u8],
    limits: CompressionLimits,
) -> RegionResult<Vec<u8>> {
    if payload.len() as u64 > limits.max_compressed_bytes {
        return Err(RegionError::new(
            RegionErrorKind::DecompressionSizeLimit,
            0,
            "compressed region payload exceeds configured limit",
        ));
    }

    match compression_id {
        COMPRESSION_GZIP => decode(payload, NbtCompression::Gzip, limits)
            .map(|(_, bytes)| bytes)
            .map_err(compression_error),
        COMPRESSION_ZLIB => decode(payload, NbtCompression::Zlib, limits)
            .map(|(_, bytes)| bytes)
            .map_err(compression_error),
        COMPRESSION_RAW => {
            if payload.len() as u64 > limits.max_decompressed_bytes {
                Err(RegionError::new(
                    RegionErrorKind::DecompressionSizeLimit,
                    0,
                    "raw region payload exceeds configured decompressed limit",
                ))
            } else {
                Ok(payload.to_vec())
            }
        }
        COMPRESSION_LZ4 => decode_lz4_block_stream(payload, limits.max_decompressed_bytes),
        _ => Err(RegionError::new(
            RegionErrorKind::DecompressionError,
            0,
            format!("unsupported region compression id {}", compression_id),
        )),
    }
}

pub fn compress_region_payload(
    compression_id: u8,
    payload: &[u8],
    limits: CompressionLimits,
) -> RegionResult<Vec<u8>> {
    if payload.len() as u64 > limits.max_decompressed_bytes {
        return Err(RegionError::new(
            RegionErrorKind::DecompressionSizeLimit,
            0,
            "uncompressed region payload exceeds configured limit",
        ));
    }

    match compression_id {
        COMPRESSION_GZIP => encode(payload, NbtCompression::Gzip, limits.max_compressed_bytes)
            .map_err(compression_error),
        COMPRESSION_ZLIB => encode(payload, NbtCompression::Zlib, limits.max_compressed_bytes)
            .map_err(compression_error),
        COMPRESSION_RAW => {
            if payload.len() as u64 > limits.max_compressed_bytes {
                Err(RegionError::new(
                    RegionErrorKind::DecompressionSizeLimit,
                    0,
                    "raw region payload exceeds configured compressed limit",
                ))
            } else {
                Ok(payload.to_vec())
            }
        }
        COMPRESSION_LZ4 => {
            let output = encode_lz4_java_block_stream(payload, true);
            if output.len() as u64 > limits.max_compressed_bytes {
                Err(RegionError::new(
                    RegionErrorKind::DecompressionSizeLimit,
                    0,
                    "lz4-java region payload exceeds configured compressed limit",
                ))
            } else {
                Ok(output)
            }
        }
        _ => Err(RegionError::new(
            RegionErrorKind::DecompressionError,
            0,
            format!("unsupported region compression id {}", compression_id),
        )),
    }
}

fn compression_error(error: crate::storage::nbt::error::NbtError) -> RegionError {
    let kind = match error.kind {
        NbtErrorKind::CompressedSizeLimit | NbtErrorKind::DecompressedSizeLimit => {
            RegionErrorKind::DecompressionSizeLimit
        }
        _ => RegionErrorKind::DecompressionError,
    };
    RegionError::new(kind, error.offset as u64, format!("{:?}", error.kind))
}

fn decode_lz4_block_stream(input: &[u8], max_output_bytes: u64) -> RegionResult<Vec<u8>> {
    let mut cursor = 0usize;
    let mut output = Vec::new();
    loop {
        if input.len().saturating_sub(cursor) < LZ4_BLOCK_HEADER_LEN {
            return Err(RegionError::new(
                RegionErrorKind::Lz4InvalidHeader,
                cursor as u64,
                "truncated lz4-java block header",
            ));
        }
        if &input[cursor..cursor + LZ4_BLOCK_MAGIC.len()] != LZ4_BLOCK_MAGIC {
            return Err(RegionError::new(
                RegionErrorKind::Lz4InvalidHeader,
                cursor as u64,
                "invalid lz4-java block magic",
            ));
        }
        cursor += LZ4_BLOCK_MAGIC.len();
        let token = input[cursor];
        cursor += 1;
        let method = token & 0xF0;
        let compressed_len = read_i32_le(input, &mut cursor)?;
        let decompressed_len = read_i32_le(input, &mut cursor)?;
        let expected_checksum = read_i32_le(input, &mut cursor)? as u32;

        if compressed_len == 0 && decompressed_len == 0 {
            if expected_checksum != 0 {
                return Err(RegionError::new(
                    RegionErrorKind::Lz4InvalidBlock,
                    cursor as u64,
                    "lz4-java stream terminator has non-zero checksum",
                ));
            }
            if cursor != input.len() {
                return Err(RegionError::new(
                    RegionErrorKind::DecompressionError,
                    cursor as u64,
                    "trailing bytes after lz4-java stream terminator",
                ));
            }
            return Ok(output);
        }
        if compressed_len < 0 || decompressed_len < 0 {
            return Err(RegionError::new(
                RegionErrorKind::Lz4InvalidBlock,
                cursor as u64,
                "negative lz4-java block length",
            ));
        }
        let compressed_len = compressed_len as usize;
        let decompressed_len = decompressed_len as usize;
        if input.len().saturating_sub(cursor) < compressed_len {
            return Err(RegionError::new(
                RegionErrorKind::Lz4InvalidBlock,
                cursor as u64,
                "truncated lz4-java block data",
            ));
        }
        let next_total = output.len() as u64 + decompressed_len as u64;
        if next_total > max_output_bytes {
            return Err(RegionError::new(
                RegionErrorKind::DecompressionSizeLimit,
                cursor as u64,
                "lz4-java decompressed output exceeds configured limit",
            ));
        }

        let block_data = &input[cursor..cursor + compressed_len];
        cursor += compressed_len;
        let decoded = match method {
            LZ4_METHOD_RAW => {
                if compressed_len != decompressed_len {
                    return Err(RegionError::new(
                        RegionErrorKind::Lz4InvalidBlock,
                        cursor as u64,
                        "raw lz4-java block length mismatch",
                    ));
                }
                block_data.to_vec()
            }
            LZ4_METHOD_LZ4 => block::decompress(block_data, decompressed_len).map_err(|error| {
                RegionError::new(
                    RegionErrorKind::Lz4InvalidBlock,
                    cursor as u64,
                    error.to_string(),
                )
            })?,
            _ => {
                return Err(RegionError::new(
                    RegionErrorKind::Lz4InvalidBlock,
                    cursor as u64,
                    format!("unsupported lz4-java block method token 0x{token:02X}"),
                ))
            }
        };
        if decoded.len() != decompressed_len {
            return Err(RegionError::new(
                RegionErrorKind::Lz4InvalidBlock,
                cursor as u64,
                "lz4-java block decompressed to an unexpected length",
            ));
        }
        let actual_checksum = java_lz4_checksum(&decoded);
        if actual_checksum != expected_checksum {
            return Err(RegionError::new(
                RegionErrorKind::Lz4ChecksumMismatch,
                cursor as u64,
                "lz4-java block checksum mismatch",
            ));
        }
        output.extend_from_slice(&decoded);
    }
}

fn read_i32_le(input: &[u8], cursor: &mut usize) -> RegionResult<i32> {
    if input.len().saturating_sub(*cursor) < 4 {
        return Err(RegionError::new(
            RegionErrorKind::Lz4InvalidHeader,
            *cursor as u64,
            "truncated little-endian integer",
        ));
    }
    let value = i32::from_le_bytes([
        input[*cursor],
        input[*cursor + 1],
        input[*cursor + 2],
        input[*cursor + 3],
    ]);
    *cursor += 4;
    Ok(value)
}

fn java_lz4_checksum(input: &[u8]) -> u32 {
    xxh32(input, LZ4_JAVA_XXHASH_SEED) & LZ4_JAVA_CHECKSUM_MASK
}

fn encode_lz4_java_block_stream(input: &[u8], compressed: bool) -> Vec<u8> {
    let mut output = Vec::new();
    let data;
    let (method, block_bytes) = if compressed {
        data = block::compress(input);
        (LZ4_METHOD_LZ4, data.as_slice())
    } else {
        (LZ4_METHOD_RAW, input)
    };
    output.extend_from_slice(LZ4_BLOCK_MAGIC);
    output.push(method | 0x0D);
    output.extend_from_slice(&(block_bytes.len() as i32).to_le_bytes());
    output.extend_from_slice(&(input.len() as i32).to_le_bytes());
    output.extend_from_slice(&java_lz4_checksum(input).to_le_bytes());
    output.extend_from_slice(block_bytes);
    output.extend_from_slice(LZ4_BLOCK_MAGIC);
    output.push(LZ4_METHOD_RAW | 0x0D);
    output.extend_from_slice(&0i32.to_le_bytes());
    output.extend_from_slice(&0i32.to_le_bytes());
    output.extend_from_slice(&0i32.to_le_bytes());
    output
}

#[cfg(test)]
pub fn encode_lz4_java_block_stream_for_test(input: &[u8], compressed: bool) -> Vec<u8> {
    encode_lz4_java_block_stream(input, compressed)
}

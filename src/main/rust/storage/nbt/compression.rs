use std::io::{Cursor, Read, Write};

use flate2::bufread::{GzDecoder, ZlibDecoder};
use flate2::write::{GzEncoder, ZlibEncoder};
use flate2::Compression;

use super::error::{NbtError, NbtErrorKind, NbtResult};

pub const FORMAT_AUTO: i32 = -1;
pub const FORMAT_RAW: i32 = 0;
pub const FORMAT_GZIP: i32 = 1;
pub const FORMAT_ZLIB: i32 = 2;

const DEFAULT_MAX_COMPRESSED_BYTES: u64 = 64 * 1024 * 1024;
const DEFAULT_MAX_DECOMPRESSED_BYTES: u64 = 256 * 1024 * 1024;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i32)]
pub enum NbtCompression {
    Auto = FORMAT_AUTO,
    Raw = FORMAT_RAW,
    Gzip = FORMAT_GZIP,
    Zlib = FORMAT_ZLIB,
}

impl NbtCompression {
    pub fn from_ffi(value: i32) -> NbtResult<Self> {
        Ok(match value {
            FORMAT_AUTO => Self::Auto,
            FORMAT_RAW => Self::Raw,
            FORMAT_GZIP => Self::Gzip,
            FORMAT_ZLIB => Self::Zlib,
            _ => return Err(NbtError::new(NbtErrorKind::UnsupportedCompression, 0)),
        })
    }
}

#[derive(Clone, Copy, Debug)]
pub struct CompressionLimits {
    pub max_compressed_bytes: u64,
    pub max_decompressed_bytes: u64,
}

impl CompressionLimits {
    pub fn from_ffi(max_compressed_bytes: u64, max_decompressed_bytes: u64) -> Self {
        Self {
            max_compressed_bytes: if max_compressed_bytes == 0 {
                DEFAULT_MAX_COMPRESSED_BYTES
            } else {
                max_compressed_bytes
            },
            max_decompressed_bytes: if max_decompressed_bytes == 0 {
                DEFAULT_MAX_DECOMPRESSED_BYTES
            } else {
                max_decompressed_bytes
            },
        }
    }
}

pub fn detect(input: &[u8]) -> NbtCompression {
    if input.len() >= 2 && input[0] == 0x1F && input[1] == 0x8B {
        NbtCompression::Gzip
    } else if looks_like_zlib(input) {
        NbtCompression::Zlib
    } else {
        NbtCompression::Raw
    }
}

pub fn decode(
    input: &[u8],
    requested: NbtCompression,
    limits: CompressionLimits,
) -> NbtResult<(NbtCompression, Vec<u8>)> {
    if input.len() as u64 > limits.max_compressed_bytes {
        return Err(NbtError::new(NbtErrorKind::CompressedSizeLimit, 0));
    }
    let format = match requested {
        NbtCompression::Auto => detect(input),
        other => other,
    };
    let decoded = match format {
        NbtCompression::Auto => unreachable!("auto is resolved before decoding"),
        NbtCompression::Raw => input.to_vec(),
        NbtCompression::Gzip => decode_gzip(input, limits.max_decompressed_bytes)?,
        NbtCompression::Zlib => decode_zlib(input, limits.max_decompressed_bytes)?,
    };
    Ok((format, decoded))
}

pub fn encode(input: &[u8], format: NbtCompression, max_output_bytes: u64) -> NbtResult<Vec<u8>> {
    let encoded = match format {
        NbtCompression::Auto => return Err(NbtError::new(NbtErrorKind::UnsupportedCompression, 0)),
        NbtCompression::Raw => input.to_vec(),
        NbtCompression::Gzip => {
            let mut encoder = GzEncoder::new(Vec::new(), Compression::default());
            encoder
                .write_all(input)
                .map_err(|_| NbtError::new(NbtErrorKind::CompressionError, 0))?;
            encoder
                .finish()
                .map_err(|_| NbtError::new(NbtErrorKind::CompressionError, 0))?
        }
        NbtCompression::Zlib => {
            let mut encoder = ZlibEncoder::new(Vec::new(), Compression::default());
            encoder
                .write_all(input)
                .map_err(|_| NbtError::new(NbtErrorKind::CompressionError, 0))?;
            encoder
                .finish()
                .map_err(|_| NbtError::new(NbtErrorKind::CompressionError, 0))?
        }
    };
    if encoded.len() as u64 > max_output_bytes {
        return Err(NbtError::new(NbtErrorKind::CompressedSizeLimit, 0));
    }
    Ok(encoded)
}

fn decode_gzip(input: &[u8], max_output_bytes: u64) -> NbtResult<Vec<u8>> {
    let cursor = Cursor::new(input);
    let mut decoder = GzDecoder::new(cursor);
    let decoded = read_guarded(&mut decoder, max_output_bytes)?;
    if decoder.get_ref().position() != input.len() as u64 {
        return Err(NbtError::new(
            NbtErrorKind::TrailingCompressedData,
            decoder.get_ref().position() as usize,
        ));
    }
    Ok(decoded)
}

fn decode_zlib(input: &[u8], max_output_bytes: u64) -> NbtResult<Vec<u8>> {
    let cursor = Cursor::new(input);
    let mut decoder = ZlibDecoder::new(cursor);
    let decoded = read_guarded(&mut decoder, max_output_bytes)?;
    if decoder.get_ref().position() != input.len() as u64 {
        return Err(NbtError::new(
            NbtErrorKind::TrailingCompressedData,
            decoder.get_ref().position() as usize,
        ));
    }
    Ok(decoded)
}

fn read_guarded<R: Read>(reader: &mut R, max_output_bytes: u64) -> NbtResult<Vec<u8>> {
    let mut limited = reader.take(max_output_bytes.saturating_add(1));
    let mut output = Vec::new();
    limited
        .read_to_end(&mut output)
        .map_err(|_| NbtError::new(NbtErrorKind::CompressionError, 0))?;
    if output.len() as u64 > max_output_bytes {
        return Err(NbtError::new(NbtErrorKind::DecompressedSizeLimit, 0));
    }
    Ok(output)
}

fn looks_like_zlib(input: &[u8]) -> bool {
    if input.len() < 2 {
        return false;
    }
    let cmf = input[0];
    let flg = input[1];
    let compression_method = cmf & 0x0F;
    let compression_info = cmf >> 4;
    compression_method == 8
        && compression_info <= 7
        && ((((cmf as u16) << 8) | flg as u16) % 31 == 0)
}

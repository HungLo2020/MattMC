use std::path::Path;

use crate::storage::nbt::compression::CompressionLimits;
use crate::storage::nbt::fingerprint::fingerprint_document;
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::reader::read_document;

use super::decompress::decompress_region_payload;
use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::read_chunk_payload;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ChunkNbtFingerprint {
    pub timestamp: u32,
    pub compression_id: u8,
    pub external: bool,
    pub compressed_len: u64,
    pub decompressed_len: u64,
    pub fingerprint: u64,
}

pub fn read_chunk_nbt_fingerprint(
    region_path: &Path,
    chunk_x: i32,
    chunk_z: i32,
    compression_limits: CompressionLimits,
    nbt_limits: NbtLimits,
) -> RegionResult<Option<ChunkNbtFingerprint>> {
    let Some(payload) = read_chunk_payload(region_path, chunk_x, chunk_z)? else {
        return Ok(None);
    };
    let decoded =
        decompress_region_payload(payload.compression_id, &payload.payload, compression_limits)?;
    let document = read_document(&decoded, nbt_limits).map_err(|error| {
        RegionError::new(
            RegionErrorKind::NbtParseError,
            error.offset as u64,
            format!("{:?}", error.kind),
        )
    })?;
    Ok(Some(ChunkNbtFingerprint {
        timestamp: payload.timestamp,
        compression_id: payload.compression_id,
        external: payload.external,
        compressed_len: payload.payload.len() as u64,
        decompressed_len: decoded.len() as u64,
        fingerprint: fingerprint_document(&document),
    }))
}

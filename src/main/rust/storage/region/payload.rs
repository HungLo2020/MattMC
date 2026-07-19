use std::fs;
use std::path::Path;

use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::external::read_external_payload;
use super::format::{
    local_chunk_index, valid_compression_id, CHUNK_HEADER_BYTES, COMPRESSION_CUSTOM,
    EXTERNAL_STREAM_FLAG, HEADER_BYTES, SECTOR_BYTES,
};
use super::header::RegionHeader;

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ChunkPayload {
    pub timestamp: u32,
    pub compression_id: u8,
    pub external: bool,
    pub payload: Vec<u8>,
}

pub fn read_chunk_payload(
    region_path: &Path,
    chunk_x: i32,
    chunk_z: i32,
) -> RegionResult<Option<ChunkPayload>> {
    if !region_path.is_file() {
        return Ok(None);
    }

    let file_len = fs::metadata(region_path)
        .map_err(|error| RegionError::io(0, error))?
        .len();
    if file_len == 0 {
        return Ok(None);
    }

    let bytes = fs::read(region_path).map_err(|error| RegionError::io(0, error))?;
    if bytes.len() < HEADER_BYTES {
        return Err(RegionError::new(
            RegionErrorKind::TruncatedHeader,
            bytes.len() as u64,
            format!(
                "region file is {} bytes, expected at least {}",
                bytes.len(),
                HEADER_BYTES
            ),
        ));
    }

    let header = RegionHeader::parse(&bytes[..HEADER_BYTES], file_len)?;
    let location = header.location(local_chunk_index(chunk_x, chunk_z));
    if !location.is_present() {
        return Ok(None);
    }
    let first_sector = location.first_sector();
    let sector_count = location.sector_count() as usize;
    if first_sector < 2 || sector_count == 0 {
        return Ok(None);
    }

    let sector_offset = first_sector as usize * SECTOR_BYTES;
    if sector_offset > bytes.len() || sector_offset + CHUNK_HEADER_BYTES > bytes.len() {
        return Ok(None);
    }
    let allocation_len = (sector_count * SECTOR_BYTES).min(bytes.len() - sector_offset);

    let allocation = &bytes[sector_offset..sector_offset + allocation_len];
    let declared_len =
        i32::from_be_bytes([allocation[0], allocation[1], allocation[2], allocation[3]]);
    if declared_len == 0 {
        return Ok(None);
    }
    if declared_len < 0 {
        return Ok(None);
    }

    let compression_byte = allocation[4];
    let payload_len = declared_len as usize - 1;
    let external = compression_byte & EXTERNAL_STREAM_FLAG != 0;
    let compression_id = compression_byte & !EXTERNAL_STREAM_FLAG;
    if validate_compression(compression_id, sector_offset as u64 + 4).is_err() {
        return Ok(None);
    }

    if external {
        let payload = read_external_payload(region_path, chunk_x, chunk_z)?;
        return Ok(Some(ChunkPayload {
            timestamp: location.timestamp,
            compression_id,
            external: true,
            payload,
        }));
    }

    let max_payload_len = allocation_len - CHUNK_HEADER_BYTES;
    if payload_len > max_payload_len {
        return Ok(None);
    }
    let payload_start = CHUNK_HEADER_BYTES;
    let payload_end = payload_start + payload_len;
    Ok(Some(ChunkPayload {
        timestamp: location.timestamp,
        compression_id,
        external: false,
        payload: allocation[payload_start..payload_end].to_vec(),
    }))
}

fn validate_compression(compression_id: u8, offset: u64) -> RegionResult<()> {
    if compression_id == COMPRESSION_CUSTOM {
        return Err(RegionError::new(
            RegionErrorKind::CustomCompression,
            offset,
            "custom region compression is not supported",
        ));
    }
    if !valid_compression_id(compression_id) {
        return Err(RegionError::new(
            RegionErrorKind::InvalidCompression,
            offset,
            format!("invalid region compression id {}", compression_id),
        ));
    }
    Ok(())
}

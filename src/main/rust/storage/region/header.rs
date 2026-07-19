use std::collections::HashMap;

use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::format::{sector_count, sector_number, ENTRY_COUNT, HEADER_BYTES, SECTOR_BYTES};

#[derive(Clone, Debug)]
pub struct RegionHeader {
    offsets: [u32; ENTRY_COUNT],
    timestamps: [u32; ENTRY_COUNT],
}

impl RegionHeader {
    pub fn parse(bytes: &[u8], file_len: u64) -> RegionResult<Self> {
        Self::parse_internal(bytes, file_len, false)
    }

    pub fn parse_strict(bytes: &[u8], file_len: u64) -> RegionResult<Self> {
        Self::parse_internal(bytes, file_len, true)
    }

    fn parse_internal(bytes: &[u8], file_len: u64, strict: bool) -> RegionResult<Self> {
        if !bytes.is_empty() && bytes.len() < HEADER_BYTES {
            return Err(RegionError::new(
                RegionErrorKind::TruncatedHeader,
                bytes.len() as u64,
                format!(
                    "region header is {} bytes, expected {}",
                    bytes.len(),
                    HEADER_BYTES
                ),
            ));
        }
        if bytes.is_empty() {
            return Ok(Self {
                offsets: [0; ENTRY_COUNT],
                timestamps: [0; ENTRY_COUNT],
            });
        }

        let mut offsets = [0u32; ENTRY_COUNT];
        let mut timestamps = [0u32; ENTRY_COUNT];
        for index in 0..ENTRY_COUNT {
            offsets[index] = read_u32_be(bytes, index * 4);
            timestamps[index] = read_u32_be(bytes, HEADER_BYTES / 2 + index * 4);
        }

        let header = Self {
            offsets,
            timestamps,
        };
        if strict {
            header.validate_allocations(file_len)?;
        }
        Ok(header)
    }

    pub fn location(&self, index: usize) -> ChunkLocation {
        ChunkLocation {
            packed: self.offsets[index],
            timestamp: self.timestamps[index],
        }
    }

    pub fn packed_offset(&self, index: usize) -> u32 {
        self.offsets[index]
    }

    pub fn timestamp(&self, index: usize) -> u32 {
        self.timestamps[index]
    }

    fn validate_allocations(&self, file_len: u64) -> RegionResult<()> {
        let mut owners: HashMap<u32, usize> = HashMap::new();
        for (index, packed) in self.offsets.iter().copied().enumerate() {
            if packed == 0 {
                continue;
            }
            validate_location(index, packed, file_len)?;
            let first = sector_number(packed);
            let count = sector_count(packed) as u32;
            for sector in first..first + count {
                if let Some(previous) = owners.insert(sector, index) {
                    return Err(RegionError::new(
                        RegionErrorKind::OverlappingSectors,
                        (sector as u64) * SECTOR_BYTES as u64,
                        format!(
                            "sector {} is referenced by chunk table entries {} and {}",
                            sector, previous, index
                        ),
                    ));
                }
            }
        }
        Ok(())
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ChunkLocation {
    pub packed: u32,
    pub timestamp: u32,
}

impl ChunkLocation {
    pub fn is_present(self) -> bool {
        self.packed != 0
    }

    pub fn first_sector(self) -> u32 {
        sector_number(self.packed)
    }

    pub fn sector_count(self) -> u8 {
        sector_count(self.packed)
    }
}

pub fn validate_location(index: usize, packed: u32, file_len: u64) -> RegionResult<()> {
    let first = sector_number(packed);
    let count = sector_count(packed) as u32;
    if first < 2 {
        return Err(RegionError::new(
            RegionErrorKind::OffsetInsideHeader,
            (index * 4) as u64,
            format!(
                "chunk table entry {} starts in header sector {}",
                index, first
            ),
        ));
    }
    if count == 0 {
        return Err(RegionError::new(
            RegionErrorKind::ZeroSectorCount,
            (index * 4) as u64,
            format!("chunk table entry {} has zero sector count", index),
        ));
    }
    let start = first as u64 * SECTOR_BYTES as u64;
    let end = (first as u64 + count as u64) * SECTOR_BYTES as u64;
    if start > file_len || end > file_len {
        return Err(RegionError::new(
            RegionErrorKind::OutOfBoundsSector,
            start,
            format!(
                "chunk table entry {} references sectors {}..{} beyond file length {}",
                index,
                first,
                first + count,
                file_len
            ),
        ));
    }
    Ok(())
}

fn read_u32_be(bytes: &[u8], offset: usize) -> u32 {
    u32::from_be_bytes([
        bytes[offset],
        bytes[offset + 1],
        bytes[offset + 2],
        bytes[offset + 3],
    ])
}

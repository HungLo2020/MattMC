//! Test/dev-only region writer for complete compressed chunk payloads.
//!
//! This module intentionally sits below Java `RegionFile` production code. It
//! writes the same `.mca`/`.mcc` on-disk records for parity tests and future
//! guarded experiments, but production world storage does not call it yet.
//!
//! Write ordering is conservative: internal payload sectors are written before
//! the header entry is published; external payloads are fully staged and moved
//! into place before the header entry is published, with a best-effort restore
//! of the old external file if header publication fails. Unlike Java's current
//! `RegionFile`, this does not keep an open region cache or a persistent
//! allocation bitmap; every operation reconstructs ownership from the header.

use std::fs::{self, File, OpenOptions};
use std::io::{Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::external::external_chunk_path;
use super::format::{
    local_chunk_index, pack_location, size_to_sectors, valid_compression_id, CHUNK_HEADER_BYTES,
    COMPRESSION_CUSTOM, ENTRY_COUNT, EXTERNAL_CHUNK_THRESHOLD_SECTORS, EXTERNAL_STREAM_FLAG,
    HEADER_BYTES, SECTOR_BYTES,
};
use super::header::RegionHeader;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct ChunkWriteResult {
    pub timestamp: u32,
    pub compression_id: u8,
    pub external: bool,
    pub present: bool,
    pub first_sector: u32,
    pub sector_count: u8,
    pub payload_len: u64,
}

#[derive(Clone)]
struct MutableHeader {
    offsets: [u32; ENTRY_COUNT],
    timestamps: [u32; ENTRY_COUNT],
}

impl MutableHeader {
    fn load(region_path: &Path) -> RegionResult<(Self, u64)> {
        if !region_path.exists() {
            return Ok((
                Self {
                    offsets: [0; ENTRY_COUNT],
                    timestamps: [0; ENTRY_COUNT],
                },
                0,
            ));
        }
        if !region_path.is_file() {
            return Err(RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                format!("region path is not a file: {}", region_path.display()),
            ));
        }
        let file_len = fs::metadata(region_path)
            .map_err(|error| RegionError::io(0, error))?
            .len();
        if file_len == 0 {
            return Ok((
                Self {
                    offsets: [0; ENTRY_COUNT],
                    timestamps: [0; ENTRY_COUNT],
                },
                0,
            ));
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
        let header = RegionHeader::parse_strict(&bytes[..HEADER_BYTES], file_len)?;
        let mut offsets = [0u32; ENTRY_COUNT];
        let mut timestamps = [0u32; ENTRY_COUNT];
        for index in 0..ENTRY_COUNT {
            offsets[index] = header.packed_offset(index);
            timestamps[index] = header.timestamp(index);
        }
        Ok((
            Self {
                offsets,
                timestamps,
            },
            file_len,
        ))
    }

    fn chunk_location(&self, index: usize) -> (u32, u8) {
        let packed = self.offsets[index];
        ((packed >> 8) & 0x00ff_ffff, (packed & 0xff) as u8)
    }

    fn write_to_file(&self, file: &mut File) -> RegionResult<()> {
        let mut bytes = vec![0u8; HEADER_BYTES];
        for index in 0..ENTRY_COUNT {
            bytes[index * 4..index * 4 + 4].copy_from_slice(&self.offsets[index].to_be_bytes());
            bytes[HEADER_BYTES / 2 + index * 4..HEADER_BYTES / 2 + index * 4 + 4]
                .copy_from_slice(&self.timestamps[index].to_be_bytes());
        }
        file.seek(SeekFrom::Start(0))
            .map_err(|error| RegionError::io(0, error))?;
        file.write_all(&bytes)
            .map_err(|error| RegionError::io(0, error))
    }
}

pub fn write_chunk_payload(
    region_path: &Path,
    chunk_x: i32,
    chunk_z: i32,
    compression_id: u8,
    payload: &[u8],
) -> RegionResult<ChunkWriteResult> {
    validate_region_parent(region_path)?;
    validate_compression(compression_id)?;
    let record_len = payload
        .len()
        .checked_add(CHUNK_HEADER_BYTES)
        .ok_or_else(|| {
            RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                "chunk record length overflows usize",
            )
        })?;
    if record_len > i32::MAX as usize {
        return Err(RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            "chunk record is too large for the region length field",
        ));
    }

    let chunk_index = local_chunk_index(chunk_x, chunk_z);
    let (mut header, file_len) = MutableHeader::load(region_path)?;
    let old_external = existing_external_flag(region_path, header.offsets[chunk_index])?;
    let external = size_to_sectors(record_len).ok_or_else(|| {
        RegionError::new(RegionErrorKind::InvalidArgument, 0, "record size overflow")
    })? >= EXTERNAL_CHUNK_THRESHOLD_SECTORS;
    let required_sectors = if external {
        1
    } else {
        size_to_sectors(record_len).ok_or_else(|| {
            RegionError::new(RegionErrorKind::InvalidArgument, 0, "record size overflow")
        })?
    };
    if required_sectors == 0 || required_sectors > u8::MAX as usize {
        return Err(RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            format!("invalid sector count {}", required_sectors),
        ));
    }

    let mut used = used_sector_bitmap(&header, file_len)?;
    let first_sector = allocate_sectors(&mut used, required_sectors)?;
    let timestamp = current_timestamp();
    let mut file = open_region_file(region_path)?;
    ensure_new_region_header(&mut file, file_len)?;

    let mut external_temp = None;
    let mut external_backup = None;
    let external_final = if external {
        Some(external_chunk_path(region_path, chunk_x, chunk_z)?)
    } else {
        None
    };

    if external {
        let final_path = external_final.as_ref().expect("external path");
        let temp_path = write_external_temp(region_path, payload)?;
        external_temp = Some(temp_path);
        write_external_stub(&mut file, first_sector, compression_id)?;
        if final_path.exists() {
            let backup = unique_external_temp(region_path, "old")?;
            fs::rename(final_path, &backup).map_err(|error| RegionError::io(0, error))?;
            external_backup = Some(backup);
        }
        if let Some(temp_path) = external_temp.take() {
            fs::rename(&temp_path, final_path).map_err(|error| {
                if let Some(backup) = external_backup.as_ref() {
                    let _ = fs::rename(backup, final_path);
                }
                RegionError::io(0, error)
            })?;
        }
    } else {
        write_internal_record(&mut file, first_sector, compression_id, payload)?;
    }

    header.offsets[chunk_index] = pack_location(first_sector, required_sectors as u8);
    header.timestamps[chunk_index] = timestamp;
    if let Err(error) = header.write_to_file(&mut file) {
        if let Some(final_path) = external_final.as_ref() {
            if let Some(backup) = external_backup.as_ref() {
                let _ = fs::remove_file(final_path);
                let _ = fs::rename(backup, final_path);
            } else if external {
                let _ = fs::remove_file(final_path);
            }
        }
        return Err(error);
    }

    if !external && old_external {
        let _ = fs::remove_file(external_chunk_path(region_path, chunk_x, chunk_z)?);
    }
    if let Some(backup) = external_backup {
        let _ = fs::remove_file(backup);
    }
    if let Some(temp_path) = external_temp {
        let _ = fs::remove_file(temp_path);
    }
    Ok(ChunkWriteResult {
        timestamp,
        compression_id,
        external,
        present: true,
        first_sector,
        sector_count: required_sectors as u8,
        payload_len: payload.len() as u64,
    })
}

pub fn delete_chunk_payload(
    region_path: &Path,
    chunk_x: i32,
    chunk_z: i32,
) -> RegionResult<ChunkWriteResult> {
    validate_region_parent(region_path)?;
    let chunk_index = local_chunk_index(chunk_x, chunk_z);
    let (mut header, file_len) = MutableHeader::load(region_path)?;
    let old_packed = header.offsets[chunk_index];
    let old_external = existing_external_flag(region_path, old_packed)?;
    let timestamp = current_timestamp();
    if old_packed == 0 {
        return Ok(ChunkWriteResult {
            timestamp,
            compression_id: 0,
            external: false,
            present: false,
            first_sector: 0,
            sector_count: 0,
            payload_len: 0,
        });
    }

    let mut file = open_region_file(region_path)?;
    ensure_new_region_header(&mut file, file_len)?;
    header.offsets[chunk_index] = 0;
    header.timestamps[chunk_index] = timestamp;
    header.write_to_file(&mut file)?;
    if old_external {
        let _ = fs::remove_file(external_chunk_path(region_path, chunk_x, chunk_z)?);
    }

    Ok(ChunkWriteResult {
        timestamp,
        compression_id: 0,
        external: false,
        present: false,
        first_sector: 0,
        sector_count: 0,
        payload_len: 0,
    })
}

pub fn flush_region(region_path: &Path) -> RegionResult<()> {
    validate_region_parent(region_path)?;
    if !region_path.exists() {
        return Ok(());
    }
    let file = OpenOptions::new()
        .read(true)
        .write(true)
        .open(region_path)
        .map_err(|error| RegionError::io(0, error))?;
    file.sync_all().map_err(|error| RegionError::io(0, error))
}

fn validate_region_parent(region_path: &Path) -> RegionResult<()> {
    if region_path.as_os_str().is_empty() {
        return Err(RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            "region path must not be empty",
        ));
    }
    let parent = region_path.parent().ok_or_else(|| {
        RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            format!("region path has no parent: {}", region_path.display()),
        )
    })?;
    if !parent.is_dir() {
        return Err(RegionError::new(
            RegionErrorKind::Io,
            0,
            format!("region parent does not exist: {}", parent.display()),
        ));
    }
    Ok(())
}

fn validate_compression(compression_id: u8) -> RegionResult<()> {
    if compression_id == COMPRESSION_CUSTOM {
        return Err(RegionError::new(
            RegionErrorKind::CustomCompression,
            0,
            "custom region compression is not supported",
        ));
    }
    if !valid_compression_id(compression_id) {
        return Err(RegionError::new(
            RegionErrorKind::InvalidCompression,
            0,
            format!("invalid region compression id {}", compression_id),
        ));
    }
    Ok(())
}

fn open_region_file(region_path: &Path) -> RegionResult<File> {
    OpenOptions::new()
        .create(true)
        .read(true)
        .write(true)
        .open(region_path)
        .map_err(|error| RegionError::io(0, error))
}

fn ensure_new_region_header(file: &mut File, file_len: u64) -> RegionResult<()> {
    if file_len == 0 {
        file.seek(SeekFrom::Start(0))
            .map_err(|error| RegionError::io(0, error))?;
        file.write_all(&vec![0u8; HEADER_BYTES])
            .map_err(|error| RegionError::io(0, error))?;
    }
    Ok(())
}

fn write_internal_record(
    file: &mut File,
    first_sector: u32,
    compression_id: u8,
    payload: &[u8],
) -> RegionResult<()> {
    let record_len = payload.len() + CHUNK_HEADER_BYTES;
    let sector_len = size_to_sectors(record_len)
        .ok_or_else(|| RegionError::new(RegionErrorKind::InvalidArgument, 0, "record overflow"))?
        * SECTOR_BYTES;
    let mut record = vec![0u8; sector_len];
    let declared_len = (payload.len() + 1) as u32;
    record[0..4].copy_from_slice(&declared_len.to_be_bytes());
    record[4] = compression_id;
    record[CHUNK_HEADER_BYTES..CHUNK_HEADER_BYTES + payload.len()].copy_from_slice(payload);
    file.seek(SeekFrom::Start(first_sector as u64 * SECTOR_BYTES as u64))
        .map_err(|error| RegionError::io(0, error))?;
    file.write_all(&record)
        .map_err(|error| RegionError::io(0, error))
}

fn write_external_stub(file: &mut File, first_sector: u32, compression_id: u8) -> RegionResult<()> {
    let mut record = vec![0u8; SECTOR_BYTES];
    record[0..4].copy_from_slice(&1u32.to_be_bytes());
    record[4] = compression_id | EXTERNAL_STREAM_FLAG;
    file.seek(SeekFrom::Start(first_sector as u64 * SECTOR_BYTES as u64))
        .map_err(|error| RegionError::io(0, error))?;
    file.write_all(&record)
        .map_err(|error| RegionError::io(0, error))
}

fn write_external_temp(region_path: &Path, payload: &[u8]) -> RegionResult<PathBuf> {
    let temp_path = unique_external_temp(region_path, "new")?;
    let mut file = File::create(&temp_path).map_err(|error| RegionError::io(0, error))?;
    file.write_all(payload)
        .map_err(|error| RegionError::io(0, error))?;
    file.sync_all().map_err(|error| RegionError::io(0, error))?;
    Ok(temp_path)
}

fn unique_external_temp(region_path: &Path, label: &str) -> RegionResult<PathBuf> {
    let parent = region_path.parent().ok_or_else(|| {
        RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            format!("region path has no parent: {}", region_path.display()),
        )
    })?;
    for attempt in 0..1000u32 {
        let path = parent.join(format!(
            "tmp-mattmc-region-{}-{}-{}.mcc",
            label,
            std::process::id(),
            attempt
        ));
        if !path.exists() {
            return Ok(path);
        }
    }
    Err(RegionError::new(
        RegionErrorKind::Io,
        0,
        "could not allocate unique external temp path",
    ))
}

fn existing_external_flag(region_path: &Path, packed: u32) -> RegionResult<bool> {
    if packed == 0 || !region_path.is_file() {
        return Ok(false);
    }
    let sector = ((packed >> 8) & 0x00ff_ffff) as u64;
    let offset = sector * SECTOR_BYTES as u64 + 4;
    let bytes = fs::read(region_path).map_err(|error| RegionError::io(0, error))?;
    if offset as usize >= bytes.len() {
        return Ok(false);
    }
    Ok(bytes[offset as usize] & EXTERNAL_STREAM_FLAG != 0)
}

fn used_sector_bitmap(header: &MutableHeader, file_len: u64) -> RegionResult<Vec<bool>> {
    let file_len_usize = usize::try_from(file_len).map_err(|_| {
        RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            "region file length exceeds platform addressable size",
        )
    })?;
    let file_sectors = size_to_sectors(file_len_usize).ok_or_else(|| {
        RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            "file length overflows usize",
        )
    })?;
    let mut used = vec![false; file_sectors.max(2)];
    used[0] = true;
    used[1] = true;
    for index in 0..ENTRY_COUNT {
        let (first, count) = header.chunk_location(index);
        if first == 0 && count == 0 {
            continue;
        }
        if first < 2 || count == 0 {
            return Err(RegionError::new(
                RegionErrorKind::OffsetInsideHeader,
                (index * 4) as u64,
                "invalid chunk location in header",
            ));
        }
        let end = first.checked_add(count as u32).ok_or_else(|| {
            RegionError::new(RegionErrorKind::InvalidArgument, 0, "sector range overflow")
        })?;
        if end as usize > used.len() {
            used.resize(end as usize, false);
        }
        for sector in first..end {
            if used[sector as usize] {
                return Err(RegionError::new(
                    RegionErrorKind::OverlappingSectors,
                    sector as u64 * SECTOR_BYTES as u64,
                    "overlapping region sectors",
                ));
            }
            used[sector as usize] = true;
        }
    }
    Ok(used)
}

fn allocate_sectors(used: &mut Vec<bool>, count: usize) -> RegionResult<u32> {
    if count == 0 || count > u8::MAX as usize {
        return Err(RegionError::new(
            RegionErrorKind::InvalidArgument,
            0,
            format!("invalid sector allocation size {}", count),
        ));
    }
    let mut cursor = 0usize;
    loop {
        while cursor < used.len() && used[cursor] {
            cursor += 1;
        }
        let start = cursor;
        while cursor < used.len() && !used[cursor] && cursor - start < count {
            cursor += 1;
        }
        if cursor - start >= count {
            if start > 0x00ff_ffff || start + count > 0x0100_0000 {
                return Err(RegionError::new(
                    RegionErrorKind::InvalidArgument,
                    0,
                    "region sector offset exceeds 24-bit header range",
                ));
            }
            for slot in &mut used[start..start + count] {
                *slot = true;
            }
            return Ok(start as u32);
        }
        if cursor >= used.len() {
            let start = used.len();
            if start > 0x00ff_ffff || start + count > 0x0100_0000 {
                return Err(RegionError::new(
                    RegionErrorKind::InvalidArgument,
                    0,
                    "region sector offset exceeds 24-bit header range",
                ));
            }
            used.resize(start + count, true);
            return Ok(start as u32);
        }
    }
}

fn current_timestamp() -> u32 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs().min(u32::MAX as u64) as u32)
        .unwrap_or(0)
}

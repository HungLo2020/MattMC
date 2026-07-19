use std::fs::{self, File, OpenOptions};
use std::io::{Read, Seek, SeekFrom, Write};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

use super::error::{RegionError, RegionErrorKind, RegionResult};
use super::external::{external_chunk_path, read_external_payload};
use super::format::{
    local_chunk_index, pack_location, sector_count, sector_number, size_to_sectors,
    valid_compression_id, CHUNK_HEADER_BYTES, COMPRESSION_CUSTOM, ENTRY_COUNT,
    EXTERNAL_CHUNK_THRESHOLD_SECTORS, EXTERNAL_STREAM_FLAG, HEADER_BYTES, SECTOR_BYTES,
};
use super::header::RegionHeader;
use super::{ChunkPayload, ChunkWriteResult};

#[derive(Debug)]
pub struct OpenRegion {
    path: PathBuf,
    file: File,
    offsets: [u32; ENTRY_COUNT],
    timestamps: [u32; ENTRY_COUNT],
    used: Vec<bool>,
    writable: bool,
    sync: bool,
}

impl OpenRegion {
    pub fn open(path: PathBuf, sync: bool) -> RegionResult<Self> {
        validate_region_parent(&path)?;
        let mut file = OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(&path)
            .map_err(|error| RegionError::io(0, error))?;
        let file_len = file
            .metadata()
            .map_err(|error| RegionError::io(0, error))?
            .len();
        let mut offsets = [0u32; ENTRY_COUNT];
        let mut timestamps = [0u32; ENTRY_COUNT];
        let mut writable = true;

        if file_len == 0 {
            write_empty_header(&mut file)?;
        } else if file_len < HEADER_BYTES as u64 {
            return Err(RegionError::new(
                RegionErrorKind::TruncatedHeader,
                file_len,
                format!(
                    "region file is {} bytes, expected at least {}",
                    file_len, HEADER_BYTES
                ),
            ));
        } else {
            let mut header_bytes = vec![0u8; HEADER_BYTES];
            file.seek(SeekFrom::Start(0))
                .map_err(|error| RegionError::io(0, error))?;
            file.read_exact(&mut header_bytes)
                .map_err(|error| RegionError::io(0, error))?;
            let header = RegionHeader::parse(&header_bytes, file_len)?;
            for index in 0..ENTRY_COUNT {
                offsets[index] = header.packed_offset(index);
                timestamps[index] = header.timestamp(index);
            }
            writable = RegionHeader::parse_strict(&header_bytes, file_len).is_ok();
        }

        let used = match used_sector_bitmap(
            &offsets,
            file.metadata()
                .map_err(|error| RegionError::io(0, error))?
                .len(),
        ) {
            Ok(used) => used,
            Err(_) => {
                writable = false;
                vec![true, true]
            }
        };

        Ok(Self {
            path,
            file,
            offsets,
            timestamps,
            used,
            writable,
            sync,
        })
    }

    pub fn read_payload(
        &mut self,
        chunk_x: i32,
        chunk_z: i32,
    ) -> RegionResult<Option<ChunkPayload>> {
        let index = local_chunk_index(chunk_x, chunk_z);
        let packed = self.offsets[index];
        if packed == 0 {
            return Ok(None);
        }
        let first_sector = sector_number(packed);
        let sectors = sector_count(packed) as usize;
        if first_sector < 2 || sectors == 0 {
            return Ok(None);
        }
        let sector_offset = first_sector as u64 * SECTOR_BYTES as u64;
        let file_len = self.file_len()?;
        if sector_offset > file_len || sector_offset + CHUNK_HEADER_BYTES as u64 > file_len {
            return Ok(None);
        }
        let allocation_len = (sectors * SECTOR_BYTES).min((file_len - sector_offset) as usize);
        if allocation_len < CHUNK_HEADER_BYTES {
            return Ok(None);
        }
        let mut allocation = vec![0u8; allocation_len];
        self.file
            .seek(SeekFrom::Start(sector_offset))
            .map_err(|error| RegionError::io(sector_offset, error))?;
        self.file
            .read_exact(&mut allocation)
            .map_err(|error| RegionError::io(sector_offset, error))?;

        let declared_len =
            i32::from_be_bytes([allocation[0], allocation[1], allocation[2], allocation[3]]);
        if declared_len <= 0 {
            return Ok(None);
        }
        let compression_byte = allocation[4];
        let external = compression_byte & EXTERNAL_STREAM_FLAG != 0;
        let compression_id = compression_byte & !EXTERNAL_STREAM_FLAG;
        if !valid_read_compression(compression_id) {
            return Ok(None);
        }
        if external {
            let payload = read_external_payload(&self.path, chunk_x, chunk_z)?;
            return Ok(Some(ChunkPayload {
                timestamp: self.timestamps[index],
                compression_id,
                external: true,
                payload,
            }));
        }

        let payload_len = declared_len as usize - 1;
        let max_payload_len = allocation_len - CHUNK_HEADER_BYTES;
        if payload_len > max_payload_len {
            return Ok(None);
        }
        Ok(Some(ChunkPayload {
            timestamp: self.timestamps[index],
            compression_id,
            external: false,
            payload: allocation[CHUNK_HEADER_BYTES..CHUNK_HEADER_BYTES + payload_len].to_vec(),
        }))
    }

    pub fn write_payload(
        &mut self,
        chunk_x: i32,
        chunk_z: i32,
        compression_id: u8,
        payload: &[u8],
    ) -> RegionResult<ChunkWriteResult> {
        self.ensure_writable()?;
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

        let index = local_chunk_index(chunk_x, chunk_z);
        let old_packed = self.offsets[index];
        let old_external = self.existing_external_flag(chunk_x, chunk_z, old_packed)?;
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

        let first_sector = self.allocate_sectors(required_sectors)?;
        let timestamp = current_timestamp();
        let mut external_temp = None;
        let mut external_backup = None;
        let external_final = if external {
            Some(external_chunk_path(&self.path, chunk_x, chunk_z)?)
        } else {
            None
        };

        if external {
            let final_path = external_final.as_ref().expect("external path");
            let temp_path = write_external_temp(&self.path, payload)?;
            external_temp = Some(temp_path);
            self.write_external_stub(first_sector, compression_id)?;
            if final_path.exists() {
                let backup = unique_external_temp(&self.path, "old")?;
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
            self.write_internal_record(first_sector, compression_id, payload)?;
        }

        self.offsets[index] = pack_location(first_sector, required_sectors as u8);
        self.timestamps[index] = timestamp;
        if let Err(error) = self.write_header() {
            self.free_allocation(first_sector, required_sectors as u8);
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

        if old_packed != 0 {
            self.free_packed(old_packed);
        }
        if !external && old_external {
            let _ = fs::remove_file(external_chunk_path(&self.path, chunk_x, chunk_z)?);
        }
        if let Some(backup) = external_backup {
            let _ = fs::remove_file(backup);
        }
        if let Some(temp_path) = external_temp {
            let _ = fs::remove_file(temp_path);
        }
        self.sync_if_requested()?;

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

    pub fn delete_chunk(&mut self, chunk_x: i32, chunk_z: i32) -> RegionResult<ChunkWriteResult> {
        self.ensure_writable()?;
        let index = local_chunk_index(chunk_x, chunk_z);
        let old_packed = self.offsets[index];
        let old_external = self.existing_external_flag(chunk_x, chunk_z, old_packed)?;
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

        self.offsets[index] = 0;
        self.timestamps[index] = timestamp;
        self.write_header()?;
        self.free_packed(old_packed);
        if old_external {
            let _ = fs::remove_file(external_chunk_path(&self.path, chunk_x, chunk_z)?);
        }
        self.sync_if_requested()?;

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

    pub fn flush(&mut self) -> RegionResult<()> {
        self.file
            .sync_all()
            .map_err(|error| RegionError::io(0, error))
    }

    pub fn close(mut self) -> RegionResult<()> {
        self.pad_to_full_sector()?;
        self.flush()
    }

    fn ensure_writable(&self) -> RegionResult<()> {
        if self.writable {
            Ok(())
        } else {
            Err(RegionError::new(
                RegionErrorKind::OutOfBoundsSector,
                0,
                "region has invalid allocation metadata; reads are allowed but writes are disabled",
            ))
        }
    }

    fn file_len(&self) -> RegionResult<u64> {
        self.file
            .metadata()
            .map(|metadata| metadata.len())
            .map_err(|error| RegionError::io(0, error))
    }

    fn allocate_sectors(&mut self, count: usize) -> RegionResult<u32> {
        if count == 0 || count > u8::MAX as usize {
            return Err(RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                format!("invalid sector allocation size {}", count),
            ));
        }
        let mut cursor = 0usize;
        loop {
            while cursor < self.used.len() && self.used[cursor] {
                cursor += 1;
            }
            let start = cursor;
            while cursor < self.used.len() && !self.used[cursor] && cursor - start < count {
                cursor += 1;
            }
            if cursor - start >= count {
                self.force_allocation(start, count)?;
                return Ok(start as u32);
            }
            if cursor >= self.used.len() {
                let start = self.used.len();
                self.force_allocation(start, count)?;
                return Ok(start as u32);
            }
        }
    }

    fn force_allocation(&mut self, start: usize, count: usize) -> RegionResult<()> {
        if start > 0x00ff_ffff || start + count > 0x0100_0000 {
            return Err(RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                "region sector offset exceeds 24-bit header range",
            ));
        }
        if self.used.len() < start + count {
            self.used.resize(start + count, false);
        }
        for slot in &mut self.used[start..start + count] {
            *slot = true;
        }
        Ok(())
    }

    fn free_packed(&mut self, packed: u32) {
        self.free_allocation(sector_number(packed), sector_count(packed));
    }

    fn free_allocation(&mut self, first: u32, count: u8) {
        let first = first as usize;
        let count = count as usize;
        if first >= self.used.len() {
            return;
        }
        let end = self.used.len().min(first + count);
        for slot in &mut self.used[first..end] {
            *slot = false;
        }
    }

    fn write_header(&mut self) -> RegionResult<()> {
        let mut bytes = vec![0u8; HEADER_BYTES];
        for index in 0..ENTRY_COUNT {
            bytes[index * 4..index * 4 + 4].copy_from_slice(&self.offsets[index].to_be_bytes());
            bytes[HEADER_BYTES / 2 + index * 4..HEADER_BYTES / 2 + index * 4 + 4]
                .copy_from_slice(&self.timestamps[index].to_be_bytes());
        }
        self.file
            .seek(SeekFrom::Start(0))
            .map_err(|error| RegionError::io(0, error))?;
        self.file
            .write_all(&bytes)
            .map_err(|error| RegionError::io(0, error))
    }

    fn write_internal_record(
        &mut self,
        first_sector: u32,
        compression_id: u8,
        payload: &[u8],
    ) -> RegionResult<()> {
        let record_len = payload.len() + CHUNK_HEADER_BYTES;
        let sector_len = size_to_sectors(record_len).ok_or_else(|| {
            RegionError::new(RegionErrorKind::InvalidArgument, 0, "record overflow")
        })? * SECTOR_BYTES;
        let mut record = vec![0u8; sector_len];
        let declared_len = (payload.len() + 1) as u32;
        record[0..4].copy_from_slice(&declared_len.to_be_bytes());
        record[4] = compression_id;
        record[CHUNK_HEADER_BYTES..CHUNK_HEADER_BYTES + payload.len()].copy_from_slice(payload);
        self.file
            .seek(SeekFrom::Start(first_sector as u64 * SECTOR_BYTES as u64))
            .map_err(|error| RegionError::io(0, error))?;
        self.file
            .write_all(&record)
            .map_err(|error| RegionError::io(0, error))
    }

    fn write_external_stub(&mut self, first_sector: u32, compression_id: u8) -> RegionResult<()> {
        let mut record = vec![0u8; SECTOR_BYTES];
        record[0..4].copy_from_slice(&1u32.to_be_bytes());
        record[4] = compression_id | EXTERNAL_STREAM_FLAG;
        self.file
            .seek(SeekFrom::Start(first_sector as u64 * SECTOR_BYTES as u64))
            .map_err(|error| RegionError::io(0, error))?;
        self.file
            .write_all(&record)
            .map_err(|error| RegionError::io(0, error))
    }

    fn existing_external_flag(
        &mut self,
        chunk_x: i32,
        chunk_z: i32,
        packed: u32,
    ) -> RegionResult<bool> {
        if packed == 0 {
            return Ok(false);
        }
        let sector = sector_number(packed) as u64;
        let offset = sector * SECTOR_BYTES as u64 + 4;
        if offset >= self.file_len()? {
            return Ok(false);
        }
        let mut byte = [0u8; 1];
        self.file
            .seek(SeekFrom::Start(offset))
            .map_err(|error| RegionError::io(offset, error))?;
        self.file.read_exact(&mut byte).map_err(|_| {
            RegionError::new(
                RegionErrorKind::TruncatedExternalFile,
                offset,
                "chunk header is truncated",
            )
        })?;
        if byte[0] & EXTERNAL_STREAM_FLAG != 0 {
            let path = external_chunk_path(&self.path, chunk_x, chunk_z)?;
            return Ok(path.exists());
        }
        Ok(false)
    }

    fn pad_to_full_sector(&mut self) -> RegionResult<()> {
        let len = self.file_len()?;
        let padded = size_to_sectors(len as usize).ok_or_else(|| {
            RegionError::new(
                RegionErrorKind::InvalidArgument,
                0,
                "file length overflows usize",
            )
        })? * SECTOR_BYTES;
        if padded as u64 != len {
            self.file
                .seek(SeekFrom::Start(padded as u64 - 1))
                .map_err(|error| RegionError::io(0, error))?;
            self.file
                .write_all(&[0])
                .map_err(|error| RegionError::io(0, error))?;
        }
        Ok(())
    }

    fn sync_if_requested(&mut self) -> RegionResult<()> {
        if self.sync {
            self.flush()
        } else {
            Ok(())
        }
    }
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

fn valid_read_compression(compression_id: u8) -> bool {
    compression_id != COMPRESSION_CUSTOM && valid_compression_id(compression_id)
}

fn write_empty_header(file: &mut File) -> RegionResult<()> {
    file.seek(SeekFrom::Start(0))
        .map_err(|error| RegionError::io(0, error))?;
    file.write_all(&vec![0u8; HEADER_BYTES])
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

fn used_sector_bitmap(offsets: &[u32; ENTRY_COUNT], file_len: u64) -> RegionResult<Vec<bool>> {
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
    for (index, packed) in offsets.iter().copied().enumerate() {
        if packed == 0 {
            continue;
        }
        let first = sector_number(packed);
        let count = sector_count(packed);
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
            return Err(RegionError::new(
                RegionErrorKind::OutOfBoundsSector,
                first as u64 * SECTOR_BYTES as u64,
                "chunk allocation extends beyond the region file",
            ));
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

fn current_timestamp() -> u32 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|duration| duration.as_secs().min(u32::MAX as u64) as u32)
        .unwrap_or(0)
}

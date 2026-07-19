pub const SECTOR_BYTES: usize = 4096;
pub const HEADER_BYTES: usize = SECTOR_BYTES * 2;
pub const ENTRY_COUNT: usize = 1024;
pub const CHUNK_HEADER_BYTES: usize = 5;
pub const EXTERNAL_STREAM_FLAG: u8 = 0x80;
pub const EXTERNAL_FILE_EXTENSION: &str = "mcc";
pub const EXTERNAL_CHUNK_THRESHOLD_SECTORS: usize = 256;

pub const COMPRESSION_GZIP: u8 = 1;
pub const COMPRESSION_ZLIB: u8 = 2;
pub const COMPRESSION_RAW: u8 = 3;
pub const COMPRESSION_LZ4: u8 = 4;
pub const COMPRESSION_CUSTOM: u8 = 127;

pub fn local_chunk_index(chunk_x: i32, chunk_z: i32) -> usize {
    ((chunk_x & 31) as usize) + ((chunk_z & 31) as usize) * 32
}

pub fn sector_number(offset: u32) -> u32 {
    (offset >> 8) & 0x00FF_FFFF
}

pub fn sector_count(offset: u32) -> u8 {
    (offset & 0xFF) as u8
}

pub fn pack_location(first_sector: u32, sector_count: u8) -> u32 {
    (first_sector << 8) | sector_count as u32
}

pub fn size_to_sectors(size: usize) -> Option<usize> {
    size.checked_add(SECTOR_BYTES - 1)
        .map(|adjusted| adjusted / SECTOR_BYTES)
}

pub fn valid_compression_id(id: u8) -> bool {
    matches!(
        id,
        COMPRESSION_GZIP | COMPRESSION_ZLIB | COMPRESSION_RAW | COMPRESSION_LZ4
    )
}

use super::error::{ChunkError, ChunkErrorKind, ChunkResult};
use super::model::{BiomePaletteEntry, ChunkSectionDecode, ChunkSectionRecord, HeightmapRecord};
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::NbtDocument;
use crate::storage::nbt::tape::{document_from_tape, document_to_tape};

const MAGIC: u32 = 0x4b48_434d; // MCHK
const VERSION: u16 = 3;

const SECTION_HAS_BLOCK_STATES: u32 = 1 << 0;
const SECTION_HAS_BIOMES: u32 = 1 << 1;
const SECTION_HAS_BLOCK_LIGHT: u32 = 1 << 2;
const SECTION_HAS_SKY_LIGHT: u32 = 1 << 3;

const BIOME_ENTRY_RESOURCE_ID_UTF16: u32 = 1;
const BIOME_ENTRY_NBT_TAPE: u32 = 2;

pub fn encode_chunk_tape(chunk: &ChunkSectionDecode) -> ChunkResult<Vec<u8>> {
    encode_chunk_tape_inner(chunk, &[])
}

pub fn encode_unified_chunk_tape(
    chunk: &ChunkSectionDecode,
    residual: &NbtDocument,
) -> ChunkResult<Vec<u8>> {
    let residual_tape = encode_residual_tape(residual)?;
    encode_chunk_tape_with_residual_bytes(chunk, &residual_tape)
}

pub fn encode_residual_tape(residual: &NbtDocument) -> ChunkResult<Vec<u8>> {
    document_to_tape(residual, NbtLimits::defaults()).map_err(|_| {
        ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "failed to encode residual chunk NBT tape",
        )
    })
}

pub fn encode_chunk_tape_with_residual_bytes(
    chunk: &ChunkSectionDecode,
    residual_tape: &[u8],
) -> ChunkResult<Vec<u8>> {
    encode_chunk_tape_inner(chunk, &residual_tape)
}

pub fn decode_chunk_tape(input: &[u8]) -> ChunkResult<(ChunkSectionDecode, Vec<u8>, Vec<u8>)> {
    let mut reader = ChunkTapeReader::new(input);
    if reader.read_u32()? != MAGIC {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "invalid chunk-section tape magic",
        ));
    }
    let version = reader.read_u16()?;
    if version == 0 || version > VERSION {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "unsupported chunk-section tape version",
        ));
    }
    let _flags = reader.read_u16()?;
    let data_version = reader.read_i32()?;
    let chunk_x = reader.read_i32()?;
    let chunk_z = reader.read_i32()?;
    let y_pos = reader.read_i32()?;
    let is_light_on = reader.read_u32()? != 0;
    let last_update = reader.read_i64()?;
    let inhabited_time = reader.read_i64()?;
    let section_count = reader.read_len("section count")?;
    let heightmap_count = reader.read_len("heightmap count")?;
    let status = reader.read_java_string()?;
    let mut sections = Vec::with_capacity(section_count);
    for _ in 0..section_count {
        sections.push(decode_section(&mut reader)?);
    }
    let mut heightmaps = Vec::with_capacity(heightmap_count);
    for _ in 0..heightmap_count {
        heightmaps.push(decode_heightmap(&mut reader)?);
    }
    let residual_tape = if version >= 2 {
        let len = reader.read_len("residual chunk NBT tape")?;
        reader.read_bytes_owned(len)?
    } else {
        Vec::new()
    };
    let tick_tape = if version >= 3 {
        let len = reader.read_len("scheduled-tick tape")?;
        reader.read_bytes_owned(len)?
    } else {
        Vec::new()
    };
    if !reader.done() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "trailing bytes in chunk-section tape",
        ));
    }
    Ok((
        ChunkSectionDecode {
            data_version,
            chunk_x,
            chunk_z,
            y_pos,
            status,
            is_light_on,
            last_update,
            inhabited_time,
            requires_dfu: false,
            sections,
            heightmaps,
        },
        residual_tape,
        tick_tape,
    ))
}

pub fn decode_residual_tape(input: &[u8], limits: NbtLimits) -> ChunkResult<NbtDocument> {
    document_from_tape(input, limits).map_err(|error| {
        ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            format!(
                "invalid residual chunk NBT tape at {}: {:?}",
                error.offset, error.kind
            ),
        )
    })
}

fn encode_chunk_tape_inner(
    chunk: &ChunkSectionDecode,
    residual_tape: &[u8],
) -> ChunkResult<Vec<u8>> {
    encode_chunk_tape_inner_with_ticks(chunk, residual_tape, &[])
}

pub fn encode_chunk_tape_with_residual_and_tick_bytes(
    chunk: &ChunkSectionDecode,
    residual_tape: &[u8],
    tick_tape: &[u8],
) -> ChunkResult<Vec<u8>> {
    encode_chunk_tape_inner_with_ticks(chunk, residual_tape, tick_tape)
}

fn encode_chunk_tape_inner_with_ticks(
    chunk: &ChunkSectionDecode,
    residual_tape: &[u8],
    tick_tape: &[u8],
) -> ChunkResult<Vec<u8>> {
    let section_count = checked_u32(chunk.sections.len(), "section count")?;
    let heightmap_count = checked_u32(chunk.heightmaps.len(), "heightmap count")?;
    let mut output = Vec::new();
    write_u32(&mut output, MAGIC);
    write_u16(&mut output, VERSION);
    write_u16(&mut output, 0);
    write_i32(&mut output, chunk.data_version);
    write_i32(&mut output, chunk.chunk_x);
    write_i32(&mut output, chunk.chunk_z);
    write_i32(&mut output, chunk.y_pos);
    write_u32(&mut output, u32::from(chunk.is_light_on));
    write_i64(&mut output, chunk.last_update);
    write_i64(&mut output, chunk.inhabited_time);
    write_u32(&mut output, section_count);
    write_u32(&mut output, heightmap_count);
    write_java_string(&mut output, &chunk.status, "status")?;
    for section in &chunk.sections {
        encode_section(&mut output, section)?;
    }
    for heightmap in &chunk.heightmaps {
        encode_heightmap(&mut output, heightmap)?;
    }
    write_bytes(&mut output, residual_tape, "residual chunk NBT tape")?;
    write_bytes(&mut output, tick_tape, "scheduled-tick tape")?;
    Ok(output)
}

fn encode_section(output: &mut Vec<u8>, section: &ChunkSectionRecord) -> ChunkResult<()> {
    let mut flags = 0u32;
    if section.has_block_states {
        flags |= SECTION_HAS_BLOCK_STATES;
    }
    if section.has_biomes {
        flags |= SECTION_HAS_BIOMES;
    }
    if section.block_light.is_some() {
        flags |= SECTION_HAS_BLOCK_LIGHT;
    }
    if section.sky_light.is_some() {
        flags |= SECTION_HAS_SKY_LIGHT;
    }
    write_i32(output, section.section_y);
    write_u32(output, flags);
    write_u32(
        output,
        checked_u32(section.block_palette.len(), "block palette count")?,
    );
    write_u32(
        output,
        checked_u32(section.block_data.len(), "block data length")?,
    );
    write_u32(
        output,
        checked_u32(section.biome_palette.len(), "biome palette count")?,
    );
    write_u32(
        output,
        checked_u32(section.biome_data.len(), "biome data length")?,
    );
    write_u32(
        output,
        checked_u32(
            section.block_light.as_ref().map_or(0, Vec::len),
            "block light length",
        )?,
    );
    write_u32(
        output,
        checked_u32(
            section.sky_light.as_ref().map_or(0, Vec::len),
            "sky light length",
        )?,
    );
    for entry in &section.block_palette {
        write_bytes(output, entry, "block palette tape")?;
    }
    for value in &section.block_data {
        write_i64(output, *value);
    }
    for entry in &section.biome_palette {
        match entry {
            BiomePaletteEntry::ResourceId(value) => {
                write_u32(output, BIOME_ENTRY_RESOURCE_ID_UTF16);
                write_java_string(output, value, "biome resource id")?;
            }
            BiomePaletteEntry::Tape(tape) => {
                write_u32(output, BIOME_ENTRY_NBT_TAPE);
                write_bytes(output, tape, "biome palette tape")?;
            }
        }
    }
    for value in &section.biome_data {
        write_i64(output, *value);
    }
    if let Some(bytes) = &section.block_light {
        output.extend_from_slice(bytes);
    }
    if let Some(bytes) = &section.sky_light {
        output.extend_from_slice(bytes);
    }
    Ok(())
}

fn encode_heightmap(output: &mut Vec<u8>, heightmap: &HeightmapRecord) -> ChunkResult<()> {
    write_java_string(output, &heightmap.name, "heightmap name")?;
    write_u32(
        output,
        checked_u32(heightmap.data.len(), "heightmap data length")?,
    );
    for value in &heightmap.data {
        write_i64(output, *value);
    }
    Ok(())
}

fn decode_section(reader: &mut ChunkTapeReader<'_>) -> ChunkResult<ChunkSectionRecord> {
    let section_y = reader.read_i32()?;
    let flags = reader.read_u32()?;
    let block_palette_count = reader.read_len("block palette count")?;
    let block_data_count = reader.read_len("block data length")?;
    let biome_palette_count = reader.read_len("biome palette count")?;
    let biome_data_count = reader.read_len("biome data length")?;
    let block_light_length = reader.read_len("block light length")?;
    let sky_light_length = reader.read_len("sky light length")?;
    let mut block_palette = Vec::with_capacity(block_palette_count);
    for _ in 0..block_palette_count {
        let len = reader.read_len("block palette tape")?;
        block_palette.push(reader.read_bytes_owned(len)?);
    }
    let mut block_data = Vec::with_capacity(block_data_count);
    for _ in 0..block_data_count {
        block_data.push(reader.read_i64()?);
    }
    let mut biome_palette = Vec::with_capacity(biome_palette_count);
    for _ in 0..biome_palette_count {
        match reader.read_u32()? {
            BIOME_ENTRY_RESOURCE_ID_UTF16 => {
                biome_palette.push(BiomePaletteEntry::ResourceId(reader.read_java_string()?));
            }
            BIOME_ENTRY_NBT_TAPE => {
                let len = reader.read_len("biome palette tape")?;
                biome_palette.push(BiomePaletteEntry::Tape(reader.read_bytes_owned(len)?));
            }
            other => {
                return Err(ChunkError::new(
                    ChunkErrorKind::InvalidArgument,
                    format!("unknown biome palette entry kind {other}"),
                ))
            }
        }
    }
    let mut biome_data = Vec::with_capacity(biome_data_count);
    for _ in 0..biome_data_count {
        biome_data.push(reader.read_i64()?);
    }
    let block_light = if flags & SECTION_HAS_BLOCK_LIGHT != 0 {
        Some(reader.read_bytes_owned(block_light_length)?)
    } else {
        None
    };
    let sky_light = if flags & SECTION_HAS_SKY_LIGHT != 0 {
        Some(reader.read_bytes_owned(sky_light_length)?)
    } else {
        None
    };
    Ok(ChunkSectionRecord {
        section_y,
        has_block_states: flags & SECTION_HAS_BLOCK_STATES != 0,
        has_biomes: flags & SECTION_HAS_BIOMES != 0,
        block_palette,
        block_data,
        biome_palette,
        biome_data,
        block_light,
        sky_light,
    })
}

fn decode_heightmap(reader: &mut ChunkTapeReader<'_>) -> ChunkResult<HeightmapRecord> {
    let name = reader.read_java_string()?;
    let data_len = reader.read_len("heightmap data length")?;
    let mut data = Vec::with_capacity(data_len);
    for _ in 0..data_len {
        data.push(reader.read_i64()?);
    }
    Ok(HeightmapRecord { name, data })
}

fn write_java_string(
    output: &mut Vec<u8>,
    value: &crate::storage::nbt::model::JavaString,
    field: &str,
) -> ChunkResult<()> {
    write_u32(output, checked_u32(value.units().len(), field)?);
    for unit in value.units() {
        write_u16(output, *unit);
    }
    Ok(())
}

fn write_bytes(output: &mut Vec<u8>, bytes: &[u8], field: &str) -> ChunkResult<()> {
    write_u32(output, checked_u32(bytes.len(), field)?);
    output.extend_from_slice(bytes);
    Ok(())
}

fn checked_u32(len: usize, field: &str) -> ChunkResult<u32> {
    u32::try_from(len).map_err(|_| {
        ChunkError::new(
            ChunkErrorKind::Overflow,
            format!("{field} is too large for the chunk typed tape"),
        )
    })
}

fn write_u16(output: &mut Vec<u8>, value: u16) {
    output.extend_from_slice(&value.to_le_bytes());
}

fn write_u32(output: &mut Vec<u8>, value: u32) {
    output.extend_from_slice(&value.to_le_bytes());
}

fn write_i32(output: &mut Vec<u8>, value: i32) {
    output.extend_from_slice(&value.to_le_bytes());
}

fn write_i64(output: &mut Vec<u8>, value: i64) {
    output.extend_from_slice(&value.to_le_bytes());
}

struct ChunkTapeReader<'a> {
    input: &'a [u8],
    cursor: usize,
}

impl<'a> ChunkTapeReader<'a> {
    fn new(input: &'a [u8]) -> Self {
        Self { input, cursor: 0 }
    }

    fn done(&self) -> bool {
        self.cursor == self.input.len()
    }

    fn read_len(&mut self, field: &str) -> ChunkResult<usize> {
        let value = self.read_u32()?;
        usize::try_from(value).map_err(|_| {
            ChunkError::new(
                ChunkErrorKind::Overflow,
                format!("{field} is too large for this platform"),
            )
        })
    }

    fn read_java_string(&mut self) -> ChunkResult<crate::storage::nbt::model::JavaString> {
        let len = self.read_len("string length")?;
        let mut units = Vec::with_capacity(len);
        for _ in 0..len {
            units.push(self.read_u16()?);
        }
        Ok(crate::storage::nbt::model::JavaString::from_units(units))
    }

    fn read_bytes_owned(&mut self, len: usize) -> ChunkResult<Vec<u8>> {
        Ok(self.read_exact(len)?.to_vec())
    }

    fn read_u16(&mut self) -> ChunkResult<u16> {
        let bytes = self.read_exact(2)?;
        Ok(u16::from_le_bytes([bytes[0], bytes[1]]))
    }

    fn read_u32(&mut self) -> ChunkResult<u32> {
        let bytes = self.read_exact(4)?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_i32(&mut self) -> ChunkResult<i32> {
        Ok(self.read_u32()? as i32)
    }

    fn read_i64(&mut self) -> ChunkResult<i64> {
        let bytes = self.read_exact(8)?;
        Ok(i64::from_le_bytes([
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        ]))
    }

    fn read_exact(&mut self, len: usize) -> ChunkResult<&'a [u8]> {
        if self.input.len().saturating_sub(self.cursor) < len {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidArgument,
                "truncated chunk-section tape",
            ));
        }
        let start = self.cursor;
        self.cursor += len;
        Ok(&self.input[start..start + len])
    }
}

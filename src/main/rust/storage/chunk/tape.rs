use super::error::{ChunkError, ChunkErrorKind, ChunkResult};
use super::model::{BiomePaletteEntry, ChunkSectionDecode, ChunkSectionRecord, HeightmapRecord};

const MAGIC: u32 = 0x4b48_434d; // MCHK
const VERSION: u16 = 1;

const SECTION_HAS_BLOCK_STATES: u32 = 1 << 0;
const SECTION_HAS_BIOMES: u32 = 1 << 1;
const SECTION_HAS_BLOCK_LIGHT: u32 = 1 << 2;
const SECTION_HAS_SKY_LIGHT: u32 = 1 << 3;

const BIOME_ENTRY_RESOURCE_ID_UTF16: u32 = 1;
const BIOME_ENTRY_NBT_TAPE: u32 = 2;

pub fn encode_chunk_tape(chunk: &ChunkSectionDecode) -> ChunkResult<Vec<u8>> {
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

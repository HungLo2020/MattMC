use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::tape::document_to_tape;

use super::error::{ChunkError, ChunkErrorKind, ChunkResult};
use super::model::{BiomePaletteEntry, ChunkSectionDecode, ChunkSectionRecord, HeightmapRecord};

pub const CURRENT_CHUNK_DATA_VERSION: i32 = 4556;
const DEFAULT_MIN_SECTION_Y: i32 = -4;
const CURRENT_MAX_SECTION_Y: i32 = 19;
const LIGHT_ARRAY_BYTES: usize = 2048;
const BLOCK_STATE_ENTRY_COUNT: usize = 4096;
const BIOME_ENTRY_COUNT: usize = 64;

pub fn decode_chunk_document(
    document: &NbtDocument,
    requested_chunk_x: i32,
    requested_chunk_z: i32,
) -> ChunkResult<ChunkSectionDecode> {
    let root = compound(&document.root, "root")?;
    let Some(data_version) = optional_int(root, "DataVersion")? else {
        return Ok(requires_dfu_chunk(0, requested_chunk_x, requested_chunk_z));
    };
    if data_version < CURRENT_CHUNK_DATA_VERSION {
        return Ok(requires_dfu_chunk(
            data_version,
            requested_chunk_x,
            requested_chunk_z,
        ));
    }
    if data_version > CURRENT_CHUNK_DATA_VERSION {
        return Err(ChunkError::new(
            ChunkErrorKind::UnsupportedDataVersion,
            format!(
                "chunk DataVersion {} is newer than supported {}",
                data_version, CURRENT_CHUNK_DATA_VERSION
            ),
        ));
    }

    let chunk_x = required_int(root, "xPos")?;
    let chunk_z = required_int(root, "zPos")?;
    if chunk_x != requested_chunk_x || chunk_z != requested_chunk_z {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPosition,
            format!(
                "chunk coordinates {chunk_x},{chunk_z} do not match requested {requested_chunk_x},{requested_chunk_z}"
            ),
        ));
    }

    let status = required_string(root, "Status")?.clone();
    let y_pos = optional_int(root, "yPos")?.unwrap_or(DEFAULT_MIN_SECTION_Y);
    let is_light_on = optional_bool(root, "isLightOn")?.unwrap_or(false);
    let last_update = optional_long(root, "LastUpdate")?.unwrap_or(0);
    let inhabited_time = optional_long(root, "InhabitedTime")?.unwrap_or(0);
    let sections = decode_sections(find_entry(root, "sections"))?;
    let heightmaps = decode_heightmaps(find_entry(root, "Heightmaps"))?;

    Ok(ChunkSectionDecode {
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
    })
}

fn requires_dfu_chunk(data_version: i32, chunk_x: i32, chunk_z: i32) -> ChunkSectionDecode {
    ChunkSectionDecode {
        data_version,
        chunk_x,
        chunk_z,
        y_pos: DEFAULT_MIN_SECTION_Y,
        status: JavaString::empty(),
        is_light_on: false,
        last_update: 0,
        inhabited_time: 0,
        requires_dfu: true,
        sections: Vec::new(),
        heightmaps: Vec::new(),
    }
}

fn decode_sections(tag: Option<&NbtTag>) -> ChunkResult<Vec<ChunkSectionRecord>> {
    let Some(tag) = tag else {
        return Ok(Vec::new());
    };
    let list = list(tag, "sections")?;
    if list.element_type != TagId::Compound && !list.elements.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            "sections must be a list of compounds",
        ));
    }
    let mut sections = Vec::with_capacity(list.elements.len());
    for element in &list.elements {
        let section = compound(element, "section")?;
        let section_y = required_byte(section, "Y")? as i32;
        if !(DEFAULT_MIN_SECTION_Y..=CURRENT_MAX_SECTION_Y).contains(&section_y) {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPosition,
                format!(
                    "section Y {section_y} is outside supported current-version range {DEFAULT_MIN_SECTION_Y}..={CURRENT_MAX_SECTION_Y}"
                ),
            ));
        }
        let (has_block_states, block_palette, block_data) =
            decode_block_states(find_entry(section, "block_states"))?;
        let (has_biomes, biome_palette, biome_data) = decode_biomes(find_entry(section, "biomes"))?;
        let block_light = decode_light(find_entry(section, "BlockLight"), "BlockLight")?;
        let sky_light = decode_light(find_entry(section, "SkyLight"), "SkyLight")?;
        sections.push(ChunkSectionRecord {
            section_y,
            has_block_states,
            has_biomes,
            block_palette,
            block_data,
            biome_palette,
            biome_data,
            block_light,
            sky_light,
        });
    }
    Ok(sections)
}

fn decode_block_states(tag: Option<&NbtTag>) -> ChunkResult<(bool, Vec<Vec<u8>>, Vec<i64>)> {
    let Some(tag) = tag else {
        return Ok((false, Vec::new(), Vec::new()));
    };
    let entries = compound(tag, "block_states")?;
    let palette_tag = find_entry(entries, "palette").ok_or_else(|| {
        ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            "block_states is missing palette",
        )
    })?;
    let palette = list(palette_tag, "block_states.palette")?;
    if palette.element_type != TagId::Compound && !palette.elements.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            "block state palette must contain compounds",
        ));
    }
    let mut palette_tapes = Vec::with_capacity(palette.elements.len());
    for entry in &palette.elements {
        if entry.id() != TagId::Compound {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPalette,
                "block state palette entry must be a compound",
            ));
        }
        palette_tapes.push(tag_to_tape(entry, "block state palette entry")?);
    }
    let data = decode_optional_packed_data(
        entries,
        "block_states.data",
        palette.elements.len(),
        BLOCK_STATE_ENTRY_COUNT,
        true,
    )?;
    Ok((true, palette_tapes, data))
}

fn decode_biomes(tag: Option<&NbtTag>) -> ChunkResult<(bool, Vec<BiomePaletteEntry>, Vec<i64>)> {
    let Some(tag) = tag else {
        return Ok((false, Vec::new(), Vec::new()));
    };
    let entries = compound(tag, "biomes")?;
    let palette_tag = find_entry(entries, "palette").ok_or_else(|| {
        ChunkError::new(ChunkErrorKind::InvalidPalette, "biomes is missing palette")
    })?;
    let palette = list(palette_tag, "biomes.palette")?;
    if palette.element_type != TagId::String && !palette.elements.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            "biome palette must contain strings",
        ));
    }
    let mut entries_out = Vec::with_capacity(palette.elements.len());
    for entry in &palette.elements {
        match entry {
            NbtTag::String(value) => entries_out.push(BiomePaletteEntry::ResourceId(value.clone())),
            _ => {
                return Err(ChunkError::new(
                    ChunkErrorKind::InvalidPalette,
                    "biome palette entry must be a string",
                ))
            }
        }
    }
    let data = decode_optional_packed_data(
        entries,
        "biomes.data",
        palette.elements.len(),
        BIOME_ENTRY_COUNT,
        false,
    )?;
    Ok((true, entries_out, data))
}

fn decode_optional_packed_data(
    entries: &[CompoundEntry],
    name: &str,
    palette_len: usize,
    entry_count: usize,
    block_states: bool,
) -> ChunkResult<Vec<i64>> {
    let data = match find_entry(entries, "data") {
        Some(NbtTag::LongArray(values)) => values.clone(),
        Some(_) => {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPackedData,
                format!("{name} must be a long array"),
            ))
        }
        None if palette_len <= 1 => return Ok(Vec::new()),
        None => {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPackedData,
                format!("{name} is required for palettes with multiple entries"),
            ))
        }
    };

    let bits = if palette_len <= 1 {
        0
    } else if block_states {
        block_state_bits(palette_len)
    } else {
        biome_bits(palette_len)
    };
    if bits != 0 {
        let expected = expected_storage_longs(entry_count, bits);
        if data.len() != expected {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPackedData,
                format!("{name} has {} longs but expected {expected}", data.len()),
            ));
        }
    } else if !data.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPackedData,
            format!("{name} must be absent or empty for single-value palettes"),
        ));
    }
    Ok(data)
}

fn block_state_bits(palette_len: usize) -> usize {
    let minimum = minimum_bits_required(palette_len);
    match minimum {
        0 => 0,
        1..=4 => 4,
        5 => 5,
        6 => 6,
        7 => 7,
        8 => 8,
        value => value,
    }
}

fn biome_bits(palette_len: usize) -> usize {
    minimum_bits_required(palette_len)
}

fn minimum_bits_required(value: usize) -> usize {
    if value <= 1 {
        0
    } else {
        usize::BITS as usize - (value - 1).leading_zeros() as usize
    }
}

fn expected_storage_longs(entry_count: usize, bits: usize) -> usize {
    let values_per_long = 64 / bits;
    entry_count.div_ceil(values_per_long)
}

fn decode_light(tag: Option<&NbtTag>, name: &str) -> ChunkResult<Option<Vec<u8>>> {
    let Some(tag) = tag else {
        return Ok(None);
    };
    let NbtTag::ByteArray(values) = tag else {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidLightArray,
            format!("{name} must be a byte array"),
        ));
    };
    if values.len() != LIGHT_ARRAY_BYTES {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidLightArray,
            format!(
                "{name} has {} bytes but expected {LIGHT_ARRAY_BYTES}",
                values.len()
            ),
        ));
    }
    Ok(Some(values.iter().map(|value| *value as u8).collect()))
}

fn decode_heightmaps(tag: Option<&NbtTag>) -> ChunkResult<Vec<HeightmapRecord>> {
    let Some(tag) = tag else {
        return Ok(Vec::new());
    };
    let entries = compound(tag, "Heightmaps")?;
    let mut heightmaps = Vec::with_capacity(entries.len());
    for entry in entries {
        let NbtTag::LongArray(values) = &entry.value else {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidHeightmap,
                "heightmap value must be a long array",
            ));
        };
        heightmaps.push(HeightmapRecord {
            name: entry.name.clone(),
            data: values.clone(),
        });
    }
    Ok(heightmaps)
}

fn tag_to_tape(tag: &NbtTag, field: &str) -> ChunkResult<Vec<u8>> {
    document_to_tape(
        &NbtDocument {
            name: JavaString::empty(),
            root: tag.clone(),
        },
        NbtLimits::defaults(),
    )
    .map_err(|_| {
        ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            format!("failed to encode {field} as NBT tape"),
        )
    })
}

fn find_entry<'a>(entries: &'a [CompoundEntry], name: &str) -> Option<&'a NbtTag> {
    entries
        .iter()
        .find(|entry| entry.name.units() == JavaString::from_str(name).units())
        .map(|entry| &entry.value)
}

fn compound<'a>(tag: &'a NbtTag, field: &str) -> ChunkResult<&'a [CompoundEntry]> {
    match tag {
        NbtTag::Compound(entries) => Ok(entries),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{field} must be a compound"),
        )),
    }
}

fn list<'a>(tag: &'a NbtTag, field: &str) -> ChunkResult<&'a ListTag> {
    match tag {
        NbtTag::List(list) => Ok(list),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{field} must be a list"),
        )),
    }
}

fn required_int(entries: &[CompoundEntry], name: &str) -> ChunkResult<i32> {
    optional_int(entries, name)?
        .ok_or_else(|| ChunkError::new(ChunkErrorKind::MissingField, format!("missing {name}")))
}

fn optional_int(entries: &[CompoundEntry], name: &str) -> ChunkResult<Option<i32>> {
    let Some(tag) = find_entry(entries, name) else {
        return Ok(None);
    };
    match tag {
        NbtTag::Int(value) => Ok(Some(*value)),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{name} must be an int"),
        )),
    }
}

fn required_byte(entries: &[CompoundEntry], name: &str) -> ChunkResult<i8> {
    let Some(tag) = find_entry(entries, name) else {
        return Err(ChunkError::new(
            ChunkErrorKind::MissingField,
            format!("missing {name}"),
        ));
    };
    match tag {
        NbtTag::Byte(value) => Ok(*value),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{name} must be a byte"),
        )),
    }
}

fn optional_long(entries: &[CompoundEntry], name: &str) -> ChunkResult<Option<i64>> {
    let Some(tag) = find_entry(entries, name) else {
        return Ok(None);
    };
    match tag {
        NbtTag::Long(value) => Ok(Some(*value)),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{name} must be a long"),
        )),
    }
}

fn optional_bool(entries: &[CompoundEntry], name: &str) -> ChunkResult<Option<bool>> {
    let Some(tag) = find_entry(entries, name) else {
        return Ok(None);
    };
    match tag {
        NbtTag::Byte(value) => Ok(Some(*value != 0)),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{name} must be a byte-backed boolean"),
        )),
    }
}

fn required_string<'a>(entries: &'a [CompoundEntry], name: &str) -> ChunkResult<&'a JavaString> {
    let Some(tag) = find_entry(entries, name) else {
        return Err(ChunkError::new(
            ChunkErrorKind::MissingField,
            format!("missing {name}"),
        ));
    };
    match tag {
        NbtTag::String(value) => Ok(value),
        _ => Err(ChunkError::new(
            ChunkErrorKind::WrongType,
            format!("{name} must be a string"),
        )),
    }
}

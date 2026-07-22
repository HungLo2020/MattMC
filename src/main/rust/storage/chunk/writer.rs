use super::error::{ChunkError, ChunkErrorKind, ChunkResult};
use super::model::{BiomePaletteEntry, ChunkSectionDecode, ChunkSectionRecord, HeightmapRecord};
use super::tape::{decode_chunk_tape, decode_residual_tape};
use super::ticks::{decode_scheduled_tick_tape, merge_scheduled_ticks_document};
use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::tape::document_from_tape;

const CURRENT_CHUNK_DATA_VERSION: i32 = super::decoder::CURRENT_CHUNK_DATA_VERSION;
const DEFAULT_MIN_SECTION_Y: i32 = -4;
const CURRENT_MAX_SECTION_Y: i32 = 19;
const LIGHT_ARRAY_BYTES: usize = 2048;
const BLOCK_STATE_ENTRY_COUNT: usize = 4096;
const BIOME_ENTRY_COUNT: usize = 64;

const OWNED_ROOT_FIELDS: [&str; 10] = [
    "DataVersion",
    "xPos",
    "yPos",
    "zPos",
    "LastUpdate",
    "InhabitedTime",
    "Status",
    "sections",
    "isLightOn",
    "Heightmaps",
];

pub fn document_from_typed_chunk_tape(input: &[u8], limits: NbtLimits) -> ChunkResult<NbtDocument> {
    document_from_typed_chunk_tape_for_position(input, None, limits)
}

pub fn document_from_typed_chunk_tape_for_position(
    input: &[u8],
    expected_position: Option<(i32, i32)>,
    limits: NbtLimits,
) -> ChunkResult<NbtDocument> {
    let (chunk, residual_tape, tick_tape) = decode_chunk_tape(input)?;
    if let Some((expected_x, expected_z)) = expected_position {
        if chunk.chunk_x != expected_x || chunk.chunk_z != expected_z {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPosition,
                format!(
                    "typed chunk coordinates {},{} do not match write target {},{}",
                    chunk.chunk_x, chunk.chunk_z, expected_x, expected_z
                ),
            ));
        }
    }
    if chunk.requires_dfu || chunk.data_version != CURRENT_CHUNK_DATA_VERSION {
        return Err(ChunkError::new(
            ChunkErrorKind::UnsupportedDataVersion,
            "typed chunk-section writes require current-version chunk data",
        ));
    }
    let residual = decode_residual_tape(&residual_tape, limits)?;
    let document = merge_typed_chunk_document(&chunk, &residual, limits)?;
    if tick_tape.is_empty() {
        Ok(document)
    } else {
        let ticks = decode_scheduled_tick_tape(&tick_tape)?;
        merge_scheduled_ticks_document(&document, &ticks, expected_position)
    }
}

pub fn merge_typed_chunk_document(
    chunk: &ChunkSectionDecode,
    residual: &NbtDocument,
    limits: NbtLimits,
) -> ChunkResult<NbtDocument> {
    validate_chunk(chunk)?;
    let residual_entries = match &residual.root {
        NbtTag::Compound(entries) => entries,
        _ => {
            return Err(ChunkError::new(
                ChunkErrorKind::WrongType,
                "residual chunk NBT root must be a compound",
            ))
        }
    };

    let mut entries = Vec::with_capacity(residual_entries.len() + 10);
    push_int(&mut entries, "DataVersion", CURRENT_CHUNK_DATA_VERSION);
    push_int(&mut entries, "xPos", chunk.chunk_x);
    push_int(&mut entries, "yPos", chunk.y_pos);
    push_int(&mut entries, "zPos", chunk.chunk_z);
    push_long(&mut entries, "LastUpdate", chunk.last_update);
    push_long(&mut entries, "InhabitedTime", chunk.inhabited_time);
    push_string(&mut entries, "Status", chunk.status.clone());

    for entry in residual_entries {
        if !is_owned_root_field(&entry.name) {
            entries.push(entry.clone());
        }
    }

    entries.push(CompoundEntry {
        name: JavaString::from_str("sections"),
        value: NbtTag::List(ListTag {
            element_type: TagId::Compound,
            elements: encode_sections(&chunk.sections, limits)?,
        }),
    });
    if chunk.is_light_on {
        entries.push(CompoundEntry {
            name: JavaString::from_str("isLightOn"),
            value: NbtTag::Byte(1),
        });
    }
    entries.push(CompoundEntry {
        name: JavaString::from_str("Heightmaps"),
        value: NbtTag::Compound(encode_heightmaps(&chunk.heightmaps)),
    });

    Ok(NbtDocument {
        name: residual.name.clone(),
        root: NbtTag::Compound(entries),
    })
}

fn encode_sections(sections: &[ChunkSectionRecord], limits: NbtLimits) -> ChunkResult<Vec<NbtTag>> {
    let mut output = Vec::with_capacity(sections.len());
    for section in sections {
        validate_section(section)?;
        let mut entries = Vec::new();
        if section.has_block_states {
            entries.push(CompoundEntry {
                name: JavaString::from_str("block_states"),
                value: encode_block_states(section, limits)?,
            });
        }
        if section.has_biomes {
            entries.push(CompoundEntry {
                name: JavaString::from_str("biomes"),
                value: encode_biomes(section, limits)?,
            });
        }
        if let Some(light) = &section.block_light {
            entries.push(CompoundEntry {
                name: JavaString::from_str("BlockLight"),
                value: NbtTag::ByteArray(light.iter().map(|value| *value as i8).collect()),
            });
        }
        if let Some(light) = &section.sky_light {
            entries.push(CompoundEntry {
                name: JavaString::from_str("SkyLight"),
                value: NbtTag::ByteArray(light.iter().map(|value| *value as i8).collect()),
            });
        }
        if !entries.is_empty() {
            entries.push(CompoundEntry {
                name: JavaString::from_str("Y"),
                value: NbtTag::Byte(section.section_y as i8),
            });
            entries.rotate_right(1);
            output.push(NbtTag::Compound(entries));
        }
    }
    Ok(output)
}

fn encode_block_states(section: &ChunkSectionRecord, limits: NbtLimits) -> ChunkResult<NbtTag> {
    let mut palette = Vec::with_capacity(section.block_palette.len());
    for tape in &section.block_palette {
        let document = document_from_tape(tape, limits).map_err(|error| {
            ChunkError::new(
                ChunkErrorKind::InvalidPalette,
                format!(
                    "invalid block-state palette NBT tape at {}: {:?}",
                    error.offset, error.kind
                ),
            )
        })?;
        if document.root.id() != TagId::Compound {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPalette,
                "block-state palette entry must be a compound",
            ));
        }
        palette.push(canonicalize_block_state_palette_entry(document.root));
    }
    Ok(NbtTag::Compound(palette_container_entries(
        "block_states",
        TagId::Compound,
        palette,
        &section.block_data,
    )))
}

fn canonicalize_block_state_palette_entry(tag: NbtTag) -> NbtTag {
    match tag {
        NbtTag::Compound(entries) => NbtTag::Compound(
            entries
                .into_iter()
                .filter(|entry| {
                    !java_string_eq(&entry.name, "Properties")
                        || !matches!(&entry.value, NbtTag::Compound(properties) if properties.is_empty())
                })
                .collect(),
        ),
        other => other,
    }
}

fn encode_biomes(section: &ChunkSectionRecord, limits: NbtLimits) -> ChunkResult<NbtTag> {
    let mut palette = Vec::with_capacity(section.biome_palette.len());
    for entry in &section.biome_palette {
        match entry {
            BiomePaletteEntry::ResourceId(id) => palette.push(NbtTag::String(id.clone())),
            BiomePaletteEntry::Tape(tape) => {
                let document = document_from_tape(tape, limits).map_err(|error| {
                    ChunkError::new(
                        ChunkErrorKind::InvalidPalette,
                        format!(
                            "invalid biome palette NBT tape at {}: {:?}",
                            error.offset, error.kind
                        ),
                    )
                })?;
                if document.root.id() != TagId::String {
                    return Err(ChunkError::new(
                        ChunkErrorKind::InvalidPalette,
                        "biome palette tape entry must be a string",
                    ));
                }
                palette.push(document.root);
            }
        }
    }
    Ok(NbtTag::Compound(palette_container_entries(
        "biomes",
        TagId::String,
        palette,
        &section.biome_data,
    )))
}

fn palette_container_entries(
    name: &str,
    element_type: TagId,
    palette: Vec<NbtTag>,
    data: &[i64],
) -> Vec<CompoundEntry> {
    let mut entries = Vec::with_capacity(2);
    entries.push(CompoundEntry {
        name: JavaString::from_str("palette"),
        value: NbtTag::List(ListTag {
            element_type,
            elements: palette,
        }),
    });
    if !data.is_empty() {
        entries.push(CompoundEntry {
            name: JavaString::from_str("data"),
            value: NbtTag::LongArray(data.to_vec()),
        });
    }
    debug_assert!(!name.is_empty());
    entries
}

fn encode_heightmaps(heightmaps: &[HeightmapRecord]) -> Vec<CompoundEntry> {
    heightmaps
        .iter()
        .map(|heightmap| CompoundEntry {
            name: heightmap.name.clone(),
            value: NbtTag::LongArray(heightmap.data.clone()),
        })
        .collect()
}

fn validate_chunk(chunk: &ChunkSectionDecode) -> ChunkResult<()> {
    for section in &chunk.sections {
        validate_section(section)?;
    }
    Ok(())
}

fn validate_section(section: &ChunkSectionRecord) -> ChunkResult<()> {
    if !(DEFAULT_MIN_SECTION_Y..=CURRENT_MAX_SECTION_Y).contains(&section.section_y) {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPosition,
            format!("section Y {} is outside supported range", section.section_y),
        ));
    }
    if section.has_block_states {
        validate_palette_storage(
            "block_states.data",
            section.block_palette.len(),
            section.block_data.len(),
            BLOCK_STATE_ENTRY_COUNT,
            true,
        )?;
    } else if !section.block_palette.is_empty() || !section.block_data.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            "block state palette/data present without block_states flag",
        ));
    }
    if section.has_biomes {
        validate_palette_storage(
            "biomes.data",
            section.biome_palette.len(),
            section.biome_data.len(),
            BIOME_ENTRY_COUNT,
            false,
        )?;
    } else if !section.biome_palette.is_empty() || !section.biome_data.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            "biome palette/data present without biomes flag",
        ));
    }
    validate_light(&section.block_light, "BlockLight")?;
    validate_light(&section.sky_light, "SkyLight")?;
    Ok(())
}

fn validate_palette_storage(
    name: &str,
    palette_len: usize,
    data_len: usize,
    entry_count: usize,
    block_states: bool,
) -> ChunkResult<()> {
    if palette_len == 0 {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPalette,
            format!("{name} has an empty palette"),
        ));
    }
    let bits = if palette_len <= 1 {
        0
    } else if block_states {
        block_state_bits(palette_len)
    } else {
        minimum_bits_required(palette_len)
    };
    let expected = if bits == 0 {
        0
    } else {
        expected_storage_longs(entry_count, bits)
    };
    if data_len != expected {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPackedData,
            format!("{name} has {data_len} longs but expected {expected}"),
        ));
    }
    Ok(())
}

fn validate_light(light: &Option<Vec<u8>>, name: &str) -> ChunkResult<()> {
    if let Some(bytes) = light {
        if bytes.len() != LIGHT_ARRAY_BYTES {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidLightArray,
                format!(
                    "{name} has {} bytes but expected {LIGHT_ARRAY_BYTES}",
                    bytes.len()
                ),
            ));
        }
    }
    Ok(())
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

fn is_owned_root_field(name: &JavaString) -> bool {
    OWNED_ROOT_FIELDS
        .iter()
        .any(|field| java_string_eq(name, field))
}

fn java_string_eq(name: &JavaString, value: &str) -> bool {
    name.units() == JavaString::from_str(value).units()
}

fn push_int(entries: &mut Vec<CompoundEntry>, name: &str, value: i32) {
    entries.push(CompoundEntry {
        name: JavaString::from_str(name),
        value: NbtTag::Int(value),
    });
}

fn push_long(entries: &mut Vec<CompoundEntry>, name: &str, value: i64) {
    entries.push(CompoundEntry {
        name: JavaString::from_str(name),
        value: NbtTag::Long(value),
    });
}

fn push_string(entries: &mut Vec<CompoundEntry>, name: &str, value: JavaString) {
    entries.push(CompoundEntry {
        name: JavaString::from_str(name),
        value: NbtTag::String(value),
    });
}

use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::tape::document_from_tape;

use super::decoder::CURRENT_CHUNK_DATA_VERSION;
use super::error::{ChunkError, ChunkErrorKind, ChunkResult};

const MAGIC: u32 = 0x4b54_434d; // MCTK
const VERSION: u16 = 1;
const BLOCK_TICKS_TAG: &str = "block_ticks";
const FLUID_TICKS_TAG: &str = "fluid_ticks";

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ChunkScheduledTicks {
    pub data_version: i32,
    pub chunk_x: i32,
    pub chunk_z: i32,
    pub requires_dfu: bool,
    pub block_ticks: Vec<ScheduledTickRecord>,
    pub fluid_ticks: Vec<ScheduledTickRecord>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ScheduledTickRecord {
    pub id: JavaString,
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub delay: i32,
    pub priority: i32,
}

pub fn decode_scheduled_ticks_document(
    document: &NbtDocument,
    requested_chunk_x: i32,
    requested_chunk_z: i32,
) -> ChunkResult<ChunkScheduledTicks> {
    let root = compound(&document.root, "root")?;
    let Some(data_version) = optional_int(root, "DataVersion")? else {
        return Ok(requires_dfu_ticks(0, requested_chunk_x, requested_chunk_z));
    };
    if data_version < CURRENT_CHUNK_DATA_VERSION {
        return Ok(requires_dfu_ticks(
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

    Ok(ChunkScheduledTicks {
        data_version,
        chunk_x,
        chunk_z,
        requires_dfu: false,
        block_ticks: decode_tick_list(find_entry(root, BLOCK_TICKS_TAG), chunk_x, chunk_z)?,
        fluid_ticks: decode_tick_list(find_entry(root, FLUID_TICKS_TAG), chunk_x, chunk_z)?,
    })
}

pub fn encode_scheduled_tick_tape(ticks: &ChunkScheduledTicks) -> ChunkResult<Vec<u8>> {
    let block_count = checked_u32(ticks.block_ticks.len(), "block tick count")?;
    let fluid_count = checked_u32(ticks.fluid_ticks.len(), "fluid tick count")?;
    let mut output = Vec::new();
    write_u32(&mut output, MAGIC);
    write_u16(&mut output, VERSION);
    write_u16(&mut output, 0);
    write_i32(&mut output, ticks.data_version);
    write_i32(&mut output, ticks.chunk_x);
    write_i32(&mut output, ticks.chunk_z);
    write_u32(&mut output, block_count);
    write_u32(&mut output, fluid_count);
    for tick in &ticks.block_ticks {
        encode_tick(&mut output, tick, ticks.chunk_x, ticks.chunk_z)?;
    }
    for tick in &ticks.fluid_ticks {
        encode_tick(&mut output, tick, ticks.chunk_x, ticks.chunk_z)?;
    }
    Ok(output)
}

pub fn decode_scheduled_tick_tape(input: &[u8]) -> ChunkResult<ChunkScheduledTicks> {
    let mut reader = TickTapeReader::new(input);
    if reader.read_u32()? != MAGIC {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "invalid scheduled-tick tape magic",
        ));
    }
    let version = reader.read_u16()?;
    if version == 0 || version > VERSION {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "unsupported scheduled-tick tape version",
        ));
    }
    let _flags = reader.read_u16()?;
    let data_version = reader.read_i32()?;
    let chunk_x = reader.read_i32()?;
    let chunk_z = reader.read_i32()?;
    let block_count = reader.read_len("block tick count")?;
    let fluid_count = reader.read_len("fluid tick count")?;
    let mut block_ticks = Vec::with_capacity(block_count);
    for _ in 0..block_count {
        block_ticks.push(reader.read_tick(chunk_x, chunk_z)?);
    }
    let mut fluid_ticks = Vec::with_capacity(fluid_count);
    for _ in 0..fluid_count {
        fluid_ticks.push(reader.read_tick(chunk_x, chunk_z)?);
    }
    if !reader.done() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            "trailing bytes in scheduled-tick tape",
        ));
    }
    Ok(ChunkScheduledTicks {
        data_version,
        chunk_x,
        chunk_z,
        requires_dfu: false,
        block_ticks,
        fluid_ticks,
    })
}

pub fn merge_scheduled_ticks_from_tapes(
    residual_tape: &[u8],
    ticks_tape: &[u8],
    expected_position: Option<(i32, i32)>,
    limits: NbtLimits,
) -> ChunkResult<NbtDocument> {
    let residual = document_from_tape(residual_tape, limits).map_err(|error| {
        ChunkError::new(
            ChunkErrorKind::InvalidArgument,
            format!(
                "invalid residual chunk NBT tape at {}: {:?}",
                error.offset, error.kind
            ),
        )
    })?;
    let ticks = decode_scheduled_tick_tape(ticks_tape)?;
    merge_scheduled_ticks_document(&residual, &ticks, expected_position)
}

pub fn merge_scheduled_ticks_document(
    residual: &NbtDocument,
    ticks: &ChunkScheduledTicks,
    expected_position: Option<(i32, i32)>,
) -> ChunkResult<NbtDocument> {
    if ticks.requires_dfu || ticks.data_version != CURRENT_CHUNK_DATA_VERSION {
        return Err(ChunkError::new(
            ChunkErrorKind::UnsupportedDataVersion,
            "typed scheduled-tick writes require current-version chunk data",
        ));
    }
    if let Some((expected_x, expected_z)) = expected_position {
        if ticks.chunk_x != expected_x || ticks.chunk_z != expected_z {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidPosition,
                format!(
                    "scheduled-tick tape coordinates {},{} do not match write target {},{}",
                    ticks.chunk_x, ticks.chunk_z, expected_x, expected_z
                ),
            ));
        }
    }
    let residual_entries = compound(&residual.root, "residual chunk NBT root")?;
    let data_version = required_int(residual_entries, "DataVersion")?;
    if data_version != CURRENT_CHUNK_DATA_VERSION {
        return Err(ChunkError::new(
            ChunkErrorKind::UnsupportedDataVersion,
            "scheduled-tick residual NBT must be current-version chunk data",
        ));
    }
    let chunk_x = required_int(residual_entries, "xPos")?;
    let chunk_z = required_int(residual_entries, "zPos")?;
    if chunk_x != ticks.chunk_x || chunk_z != ticks.chunk_z {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPosition,
            "scheduled-tick tape coordinates do not match residual chunk coordinates",
        ));
    }

    let mut entries = Vec::with_capacity(residual_entries.len() + 2);
    for entry in residual_entries {
        if !matches_name(&entry.name, BLOCK_TICKS_TAG)
            && !matches_name(&entry.name, FLUID_TICKS_TAG)
        {
            entries.push(entry.clone());
        }
    }
    entries.push(CompoundEntry {
        name: JavaString::from_str(BLOCK_TICKS_TAG),
        value: encode_tick_list(&ticks.block_ticks, ticks.chunk_x, ticks.chunk_z)?,
    });
    entries.push(CompoundEntry {
        name: JavaString::from_str(FLUID_TICKS_TAG),
        value: encode_tick_list(&ticks.fluid_ticks, ticks.chunk_x, ticks.chunk_z)?,
    });
    Ok(NbtDocument {
        name: residual.name.clone(),
        root: NbtTag::Compound(entries),
    })
}

fn requires_dfu_ticks(data_version: i32, chunk_x: i32, chunk_z: i32) -> ChunkScheduledTicks {
    ChunkScheduledTicks {
        data_version,
        chunk_x,
        chunk_z,
        requires_dfu: true,
        block_ticks: Vec::new(),
        fluid_ticks: Vec::new(),
    }
}

fn decode_tick_list(
    tag: Option<&NbtTag>,
    chunk_x: i32,
    chunk_z: i32,
) -> ChunkResult<Vec<ScheduledTickRecord>> {
    let Some(tag) = tag else {
        return Ok(Vec::new());
    };
    let list = list(tag, "scheduled tick list")?;
    if list.element_type != TagId::Compound && !list.elements.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidTick,
            "scheduled ticks must be a list of compounds",
        ));
    }
    let mut ticks = Vec::with_capacity(list.elements.len());
    for element in &list.elements {
        let tick = decode_tick(element)?;
        if belongs_to_chunk(tick.x, tick.z, chunk_x, chunk_z) {
            ticks.push(tick);
        }
    }
    Ok(ticks)
}

fn decode_tick(tag: &NbtTag) -> ChunkResult<ScheduledTickRecord> {
    let entries = compound(tag, "scheduled tick")?;
    let id = required_string(entries, "i")?.clone();
    validate_id(&id)?;
    Ok(ScheduledTickRecord {
        id,
        x: required_int(entries, "x")?,
        y: required_int(entries, "y")?,
        z: required_int(entries, "z")?,
        delay: required_int(entries, "t")?,
        priority: required_int(entries, "p")?,
    })
}

fn encode_tick_list(
    ticks: &[ScheduledTickRecord],
    chunk_x: i32,
    chunk_z: i32,
) -> ChunkResult<NbtTag> {
    let mut elements = Vec::with_capacity(ticks.len());
    for tick in ticks {
        validate_tick(tick, chunk_x, chunk_z)?;
        elements.push(NbtTag::Compound(vec![
            CompoundEntry {
                name: JavaString::from_str("i"),
                value: NbtTag::String(tick.id.clone()),
            },
            CompoundEntry {
                name: JavaString::from_str("x"),
                value: NbtTag::Int(tick.x),
            },
            CompoundEntry {
                name: JavaString::from_str("y"),
                value: NbtTag::Int(tick.y),
            },
            CompoundEntry {
                name: JavaString::from_str("z"),
                value: NbtTag::Int(tick.z),
            },
            CompoundEntry {
                name: JavaString::from_str("t"),
                value: NbtTag::Int(tick.delay),
            },
            CompoundEntry {
                name: JavaString::from_str("p"),
                value: NbtTag::Int(tick.priority),
            },
        ]));
    }
    Ok(NbtTag::List(ListTag {
        element_type: TagId::Compound,
        elements,
    }))
}

fn encode_tick(
    output: &mut Vec<u8>,
    tick: &ScheduledTickRecord,
    chunk_x: i32,
    chunk_z: i32,
) -> ChunkResult<()> {
    validate_tick(tick, chunk_x, chunk_z)?;
    write_java_string(output, &tick.id, "scheduled tick id")?;
    write_i32(output, tick.x);
    write_i32(output, tick.y);
    write_i32(output, tick.z);
    write_i32(output, tick.delay);
    write_i32(output, tick.priority);
    Ok(())
}

fn validate_tick(tick: &ScheduledTickRecord, chunk_x: i32, chunk_z: i32) -> ChunkResult<()> {
    validate_id(&tick.id)?;
    if !belongs_to_chunk(tick.x, tick.z, chunk_x, chunk_z) {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidPosition,
            "scheduled tick coordinates are outside the target chunk",
        ));
    }
    if !(-3..=3).contains(&tick.priority) {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidTick,
            "scheduled tick priority is outside the Java TickPriority range",
        ));
    }
    Ok(())
}

fn validate_id(id: &JavaString) -> ChunkResult<()> {
    let Some(value) = id.to_string_lossless_if_valid() else {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidString,
            "scheduled tick id is not valid UTF-16",
        ));
    };
    if value.is_empty() {
        return Err(ChunkError::new(
            ChunkErrorKind::InvalidString,
            "scheduled tick id must not be empty",
        ));
    }
    Ok(())
}

fn belongs_to_chunk(x: i32, z: i32, chunk_x: i32, chunk_z: i32) -> bool {
    (x >> 4) == chunk_x && (z >> 4) == chunk_z
}

fn find_entry<'a>(entries: &'a [CompoundEntry], name: &str) -> Option<&'a NbtTag> {
    entries
        .iter()
        .find(|entry| matches_name(&entry.name, name))
        .map(|entry| &entry.value)
}

fn matches_name(value: &JavaString, name: &str) -> bool {
    value.units() == JavaString::from_str(name).units()
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

fn checked_u32(len: usize, field: &str) -> ChunkResult<u32> {
    u32::try_from(len).map_err(|_| {
        ChunkError::new(
            ChunkErrorKind::Overflow,
            format!("{field} is too large for the scheduled-tick tape"),
        )
    })
}

fn write_java_string(output: &mut Vec<u8>, value: &JavaString, field: &str) -> ChunkResult<()> {
    write_u32(output, checked_u32(value.units().len(), field)?);
    for unit in value.units() {
        write_u16(output, *unit);
    }
    Ok(())
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

struct TickTapeReader<'a> {
    input: &'a [u8],
    cursor: usize,
}

impl<'a> TickTapeReader<'a> {
    fn new(input: &'a [u8]) -> Self {
        Self { input, cursor: 0 }
    }

    fn done(&self) -> bool {
        self.cursor == self.input.len()
    }

    fn read_tick(&mut self, chunk_x: i32, chunk_z: i32) -> ChunkResult<ScheduledTickRecord> {
        let tick = ScheduledTickRecord {
            id: self.read_java_string()?,
            x: self.read_i32()?,
            y: self.read_i32()?,
            z: self.read_i32()?,
            delay: self.read_i32()?,
            priority: self.read_i32()?,
        };
        validate_tick(&tick, chunk_x, chunk_z)?;
        Ok(tick)
    }

    fn read_len(&mut self, field: &str) -> ChunkResult<usize> {
        usize::try_from(self.read_u32()?).map_err(|_| {
            ChunkError::new(
                ChunkErrorKind::Overflow,
                format!("{field} is too large for this platform"),
            )
        })
    }

    fn read_java_string(&mut self) -> ChunkResult<JavaString> {
        let len = self.read_len("string length")?;
        let mut units = Vec::with_capacity(len);
        for _ in 0..len {
            units.push(self.read_u16()?);
        }
        let value = JavaString::from_units(units);
        validate_id(&value)?;
        Ok(value)
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

    fn read_exact(&mut self, len: usize) -> ChunkResult<&'a [u8]> {
        if self.input.len().saturating_sub(self.cursor) < len {
            return Err(ChunkError::new(
                ChunkErrorKind::InvalidArgument,
                "truncated scheduled-tick tape",
            ));
        }
        let start = self.cursor;
        self.cursor += len;
        Ok(&self.input[start..start + len])
    }
}

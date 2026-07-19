use super::error::{NbtError, NbtErrorKind, NbtResult};
use super::limits::{LimitTracker, NbtLimits};
use super::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};

const MAGIC: u32 = 0x5442_544E;
const VERSION: u16 = 1;
const HEADER_LEN: usize = 8;
const RECORD_LEN: usize = 24;

/// Flat, little-endian interchange tape used by Java bulk NBT calls.
///
/// The tape is intentionally not Minecraft's binary NBT encoding. It is a
/// coarse FFI transport: a header followed by preorder records. Each record
/// carries a tag id, optional list element id, UTF-16 code-unit name length,
/// payload item count, and scalar payload bits. Variable payloads follow the
/// record, then list/compound child records follow in preorder.
pub fn document_to_tape(document: &NbtDocument, limits: NbtLimits) -> NbtResult<Vec<u8>> {
    if document.root.id() != TagId::Compound {
        return Err(NbtError::new(NbtErrorKind::InvalidRootType, 0));
    }
    let mut writer = TapeWriter::new(limits);
    writer.write_u32(MAGIC)?;
    writer.write_u16(VERSION)?;
    writer.write_u16(0)?;
    writer.write_tag(document.root.id(), &document.name, &document.root, 1)?;
    Ok(writer.output)
}

pub fn document_from_tape(input: &[u8], limits: NbtLimits) -> NbtResult<NbtDocument> {
    let mut reader = TapeReader::new(input, limits);
    if reader.read_u32()? != MAGIC {
        return Err(NbtError::new(NbtErrorKind::InvalidArgument, 0));
    }
    if reader.read_u16()? != VERSION {
        return Err(NbtError::new(NbtErrorKind::InvalidArgument, 4));
    }
    let _flags = reader.read_u16()?;
    let (name, root) = reader.read_tag(1)?;
    if root.id() != TagId::Compound {
        return Err(NbtError::new(NbtErrorKind::InvalidRootType, HEADER_LEN));
    }
    if reader.cursor != input.len() {
        return Err(NbtError::new(NbtErrorKind::TrailingData, reader.cursor));
    }
    Ok(NbtDocument { name, root })
}

struct TapeWriter {
    output: Vec<u8>,
    limits: LimitTracker,
}

impl TapeWriter {
    fn new(limits: NbtLimits) -> Self {
        Self {
            output: Vec::new(),
            limits: LimitTracker::new(limits),
        }
    }

    fn write_tag(
        &mut self,
        tag_id: TagId,
        name: &JavaString,
        tag: &NbtTag,
        depth: u32,
    ) -> NbtResult<()> {
        self.limits.enter_depth(depth, self.output.len())?;
        if tag.id() != tag_id {
            return Err(NbtError::new(
                NbtErrorKind::InvalidTagType,
                self.output.len(),
            ));
        }
        let start = self.output.len();
        self.output.resize(start + RECORD_LEN, 0);

        let mut list_element_type = TagId::End;
        let mut count = 0u32;
        let mut scalar = 0u64;
        match tag {
            NbtTag::Byte(value) => scalar = *value as u8 as u64,
            NbtTag::Short(value) => scalar = *value as u16 as u64,
            NbtTag::Int(value) => scalar = *value as u32 as u64,
            NbtTag::Long(value) => scalar = *value as u64,
            NbtTag::Float(bits) => scalar = *bits as u64,
            NbtTag::Double(bits) => scalar = *bits,
            NbtTag::ByteArray(values) => count = checked_count(values.len(), start)?,
            NbtTag::String(value) => count = checked_count(value.units().len(), start)?,
            NbtTag::List(value) => {
                list_element_type = value.element_type;
                count = checked_count(value.elements.len(), start)?;
            }
            NbtTag::Compound(entries) => count = checked_count(entries.len(), start)?,
            NbtTag::IntArray(values) => count = checked_count(values.len(), start)?,
            NbtTag::LongArray(values) => count = checked_count(values.len(), start)?,
        }

        write_record_header(
            &mut self.output[start..start + RECORD_LEN],
            tag_id,
            list_element_type,
            checked_count(name.units().len(), start)?,
            count,
            scalar,
        );
        self.write_units(name.units())?;

        match tag {
            NbtTag::Byte(_)
            | NbtTag::Short(_)
            | NbtTag::Int(_)
            | NbtTag::Long(_)
            | NbtTag::Float(_)
            | NbtTag::Double(_) => {}
            NbtTag::ByteArray(values) => {
                self.limits.collection_len(values.len() as i32, 1, start)?;
                for value in values {
                    self.write_u8(*value as u8)?;
                }
            }
            NbtTag::String(value) => self.write_units(value.units())?,
            NbtTag::List(value) => {
                self.limits
                    .collection_len(value.elements.len() as i32, 4, start)?;
                for element in &value.elements {
                    if element.id() != value.element_type {
                        return Err(NbtError::new(
                            NbtErrorKind::InvalidTagType,
                            self.output.len(),
                        ));
                    }
                    self.write_tag(value.element_type, &JavaString::empty(), element, depth + 1)?;
                }
            }
            NbtTag::Compound(entries) => {
                self.limits
                    .collection_len(entries.len() as i32, 32, start)?;
                for entry in entries {
                    self.write_tag(entry.value.id(), &entry.name, &entry.value, depth + 1)?;
                }
            }
            NbtTag::IntArray(values) => {
                self.limits.collection_len(values.len() as i32, 4, start)?;
                for value in values {
                    self.write_all(&value.to_le_bytes())?;
                }
            }
            NbtTag::LongArray(values) => {
                self.limits.collection_len(values.len() as i32, 8, start)?;
                for value in values {
                    self.write_all(&value.to_le_bytes())?;
                }
            }
        }
        Ok(())
    }

    fn write_units(&mut self, units: &[u16]) -> NbtResult<()> {
        self.limits
            .allocate((units.len() as u64).saturating_mul(2), self.output.len())?;
        for unit in units {
            self.write_all(&unit.to_le_bytes())?;
        }
        Ok(())
    }

    fn write_u32(&mut self, value: u32) -> NbtResult<()> {
        self.write_all(&value.to_le_bytes())
    }

    fn write_u16(&mut self, value: u16) -> NbtResult<()> {
        self.write_all(&value.to_le_bytes())
    }

    fn write_u8(&mut self, value: u8) -> NbtResult<()> {
        self.write_all(&[value])
    }

    fn write_all(&mut self, bytes: &[u8]) -> NbtResult<()> {
        self.limits.touch(bytes.len(), self.output.len())?;
        self.output.extend_from_slice(bytes);
        Ok(())
    }
}

struct TapeReader<'a> {
    input: &'a [u8],
    cursor: usize,
    limits: LimitTracker,
}

impl<'a> TapeReader<'a> {
    fn new(input: &'a [u8], limits: NbtLimits) -> Self {
        Self {
            input,
            cursor: 0,
            limits: LimitTracker::new(limits),
        }
    }

    fn read_tag(&mut self, depth: u32) -> NbtResult<(JavaString, NbtTag)> {
        self.limits.enter_depth(depth, self.cursor)?;
        let offset = self.cursor;
        let record = self.read_record()?;
        let tag_id = TagId::from_u8(record.tag_id)
            .ok_or_else(|| NbtError::new(NbtErrorKind::InvalidTagType, offset))?;
        if tag_id == TagId::End {
            return Err(NbtError::new(NbtErrorKind::EndTagHasPayload, offset));
        }
        let list_element_type = TagId::from_u8(record.list_element_type)
            .ok_or_else(|| NbtError::new(NbtErrorKind::InvalidTagType, offset + 1))?;
        let name = self.read_units(record.name_units_len as usize)?;
        let count = record.item_count as usize;
        let tag = match tag_id {
            TagId::End => unreachable!(),
            TagId::Byte => NbtTag::Byte(record.scalar as u8 as i8),
            TagId::Short => NbtTag::Short(record.scalar as u16 as i16),
            TagId::Int => NbtTag::Int(record.scalar as u32 as i32),
            TagId::Long => NbtTag::Long(record.scalar as i64),
            TagId::Float => NbtTag::Float(record.scalar as u32),
            TagId::Double => NbtTag::Double(record.scalar),
            TagId::ByteArray => {
                self.limits
                    .collection_len(record.item_count as i32, 1, offset)?;
                let bytes = self.read_exact(count)?;
                NbtTag::ByteArray(bytes.iter().map(|value| *value as i8).collect())
            }
            TagId::String => NbtTag::String(self.read_units(count)?),
            TagId::List => {
                self.limits
                    .collection_len(record.item_count as i32, 4, offset)?;
                if list_element_type == TagId::End && count > 0 {
                    return Err(NbtError::new(
                        NbtErrorKind::MissingListElementType,
                        offset + 1,
                    ));
                }
                let mut elements = Vec::with_capacity(count);
                for _ in 0..count {
                    let (_, element) = self.read_tag(depth + 1)?;
                    if element.id() != list_element_type {
                        return Err(NbtError::new(NbtErrorKind::InvalidTagType, self.cursor));
                    }
                    elements.push(element);
                }
                NbtTag::List(ListTag {
                    element_type: list_element_type,
                    elements,
                })
            }
            TagId::Compound => {
                self.limits
                    .collection_len(record.item_count as i32, 32, offset)?;
                let mut entries = Vec::with_capacity(count);
                for _ in 0..count {
                    let (name, value) = self.read_tag(depth + 1)?;
                    entries.push(CompoundEntry { name, value });
                }
                NbtTag::Compound(entries)
            }
            TagId::IntArray => {
                self.limits
                    .collection_len(record.item_count as i32, 4, offset)?;
                let mut values = Vec::with_capacity(count);
                for _ in 0..count {
                    values.push(self.read_i32()?);
                }
                NbtTag::IntArray(values)
            }
            TagId::LongArray => {
                self.limits
                    .collection_len(record.item_count as i32, 8, offset)?;
                let mut values = Vec::with_capacity(count);
                for _ in 0..count {
                    values.push(self.read_i64()?);
                }
                NbtTag::LongArray(values)
            }
        };
        Ok((name, tag))
    }

    fn read_record(&mut self) -> NbtResult<TapeRecord> {
        let bytes = self.read_exact(RECORD_LEN)?;
        Ok(TapeRecord {
            tag_id: bytes[0],
            list_element_type: bytes[1],
            name_units_len: u32::from_le_bytes([bytes[4], bytes[5], bytes[6], bytes[7]]),
            item_count: u32::from_le_bytes([bytes[8], bytes[9], bytes[10], bytes[11]]),
            scalar: u64::from_le_bytes([
                bytes[16], bytes[17], bytes[18], bytes[19], bytes[20], bytes[21], bytes[22],
                bytes[23],
            ]),
        })
    }

    fn read_units(&mut self, len: usize) -> NbtResult<JavaString> {
        self.limits
            .allocate((len as u64).saturating_mul(2), self.cursor)?;
        let mut units = Vec::with_capacity(len);
        for _ in 0..len {
            units.push(self.read_u16()?);
        }
        Ok(JavaString::from_units(units))
    }

    fn read_exact(&mut self, len: usize) -> NbtResult<&'a [u8]> {
        let end = self
            .cursor
            .checked_add(len)
            .ok_or_else(|| NbtError::new(NbtErrorKind::Overflow, self.cursor))?;
        if end > self.input.len() {
            return Err(NbtError::new(NbtErrorKind::UnexpectedEof, self.cursor));
        }
        self.limits.touch(len, self.cursor)?;
        let bytes = &self.input[self.cursor..end];
        self.cursor = end;
        Ok(bytes)
    }

    fn read_u16(&mut self) -> NbtResult<u16> {
        let bytes = self.read_exact(2)?;
        Ok(u16::from_le_bytes([bytes[0], bytes[1]]))
    }

    fn read_u32(&mut self) -> NbtResult<u32> {
        let bytes = self.read_exact(4)?;
        Ok(u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_i32(&mut self) -> NbtResult<i32> {
        let bytes = self.read_exact(4)?;
        Ok(i32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_i64(&mut self) -> NbtResult<i64> {
        let bytes = self.read_exact(8)?;
        Ok(i64::from_le_bytes([
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        ]))
    }
}

#[derive(Clone, Copy, Debug)]
struct TapeRecord {
    tag_id: u8,
    list_element_type: u8,
    name_units_len: u32,
    item_count: u32,
    scalar: u64,
}

fn checked_count(len: usize, offset: usize) -> NbtResult<u32> {
    u32::try_from(len).map_err(|_| NbtError::new(NbtErrorKind::ExcessiveLength, offset))
}

fn write_record_header(
    output: &mut [u8],
    tag_id: TagId,
    list_element_type: TagId,
    name_units_len: u32,
    item_count: u32,
    scalar: u64,
) {
    output[0] = tag_id as u8;
    output[1] = list_element_type as u8;
    output[4..8].copy_from_slice(&name_units_len.to_le_bytes());
    output[8..12].copy_from_slice(&item_count.to_le_bytes());
    output[16..24].copy_from_slice(&scalar.to_le_bytes());
}

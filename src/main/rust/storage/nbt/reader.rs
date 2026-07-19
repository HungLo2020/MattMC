use super::error::{NbtError, NbtErrorKind, NbtResult};
use super::limits::{LimitTracker, NbtLimits};
use super::model::{CompoundEntry, ListTag, NbtDocument, NbtTag, TagId};
use super::modified_utf8::decode_modified_utf8;

pub fn read_document(input: &[u8], limits: NbtLimits) -> NbtResult<NbtDocument> {
    let mut reader = Reader::new(input, limits);
    let tag_id = reader.read_tag_id()?;
    if tag_id == TagId::End {
        return Err(NbtError::new(NbtErrorKind::InvalidRootType, 0));
    }
    if tag_id != TagId::Compound {
        return Err(NbtError::new(NbtErrorKind::InvalidRootType, 0));
    }
    let name = reader.read_string()?;
    let root = reader.read_payload(tag_id, 1)?;
    if reader.cursor != input.len() {
        return Err(NbtError::new(NbtErrorKind::TrailingData, reader.cursor));
    }
    Ok(NbtDocument { name, root })
}

struct Reader<'a> {
    input: &'a [u8],
    cursor: usize,
    limits: LimitTracker,
}

impl<'a> Reader<'a> {
    fn new(input: &'a [u8], limits: NbtLimits) -> Self {
        Self {
            input,
            cursor: 0,
            limits: LimitTracker::new(limits),
        }
    }

    fn read_payload(&mut self, tag_id: TagId, depth: u32) -> NbtResult<NbtTag> {
        self.limits.enter_depth(depth, self.cursor)?;
        Ok(match tag_id {
            TagId::End => return Err(NbtError::new(NbtErrorKind::EndTagHasPayload, self.cursor)),
            TagId::Byte => NbtTag::Byte(self.read_i8()?),
            TagId::Short => NbtTag::Short(self.read_i16()?),
            TagId::Int => NbtTag::Int(self.read_i32()?),
            TagId::Long => NbtTag::Long(self.read_i64()?),
            TagId::Float => NbtTag::Float(self.read_u32()?),
            TagId::Double => NbtTag::Double(self.read_u64()?),
            TagId::ByteArray => {
                let len_offset = self.cursor;
                let raw_len = self.read_i32()?;
                let len = self.limits.collection_len(raw_len, 1, len_offset)?;
                let bytes = self.read_exact(len)?;
                NbtTag::ByteArray(bytes.iter().map(|value| *value as i8).collect())
            }
            TagId::String => NbtTag::String(self.read_string()?),
            TagId::List => NbtTag::List(self.read_list(depth + 1)?),
            TagId::Compound => NbtTag::Compound(self.read_compound(depth + 1)?),
            TagId::IntArray => {
                let len_offset = self.cursor;
                let raw_len = self.read_i32()?;
                let len = self.limits.collection_len(raw_len, 4, len_offset)?;
                let mut values = Vec::with_capacity(len);
                for _ in 0..len {
                    values.push(self.read_i32()?);
                }
                NbtTag::IntArray(values)
            }
            TagId::LongArray => {
                let len_offset = self.cursor;
                let raw_len = self.read_i32()?;
                let len = self.limits.collection_len(raw_len, 8, len_offset)?;
                let mut values = Vec::with_capacity(len);
                for _ in 0..len {
                    values.push(self.read_i64()?);
                }
                NbtTag::LongArray(values)
            }
        })
    }

    fn read_list(&mut self, depth: u32) -> NbtResult<ListTag> {
        self.limits.enter_depth(depth, self.cursor)?;
        let element_offset = self.cursor;
        let element_type = self.read_tag_id()?;
        let len_offset = self.cursor;
        let raw_len = self.read_i32()?;
        let len = self.limits.collection_len(raw_len, 4, len_offset)?;
        if element_type == TagId::End && len > 0 {
            return Err(NbtError::new(
                NbtErrorKind::MissingListElementType,
                element_offset,
            ));
        }
        let mut elements = Vec::with_capacity(len);
        for _ in 0..len {
            elements.push(self.read_payload(element_type, depth + 1)?);
        }
        Ok(ListTag {
            element_type,
            elements,
        })
    }

    fn read_compound(&mut self, depth: u32) -> NbtResult<Vec<CompoundEntry>> {
        self.limits.enter_depth(depth, self.cursor)?;
        let mut entries = Vec::new();
        loop {
            let tag_id = self.read_tag_id()?;
            if tag_id == TagId::End {
                break;
            }
            let name = self.read_string()?;
            let value = self.read_payload(tag_id, depth + 1)?;
            if let Some(existing) = entries
                .iter_mut()
                .find(|entry: &&mut CompoundEntry| entry.name == name)
            {
                existing.value = value;
            } else {
                self.limits.allocate(32, self.cursor)?;
                entries.push(CompoundEntry { name, value });
            }
        }
        Ok(entries)
    }

    fn read_tag_id(&mut self) -> NbtResult<TagId> {
        let offset = self.cursor;
        let value = self.read_u8()?;
        TagId::from_u8(value).ok_or_else(|| NbtError::new(NbtErrorKind::InvalidTagType, offset))
    }

    fn read_string(&mut self) -> NbtResult<super::model::JavaString> {
        let len_offset = self.cursor;
        let len = self.read_u16()? as usize;
        self.limits
            .allocate((len as u64).saturating_mul(2), len_offset)?;
        let data_offset = self.cursor;
        let bytes = self.read_exact(len)?;
        decode_modified_utf8(bytes, data_offset)
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

    fn read_u8(&mut self) -> NbtResult<u8> {
        Ok(self.read_exact(1)?[0])
    }

    fn read_i8(&mut self) -> NbtResult<i8> {
        Ok(self.read_u8()? as i8)
    }

    fn read_u16(&mut self) -> NbtResult<u16> {
        let bytes = self.read_exact(2)?;
        Ok(u16::from_be_bytes([bytes[0], bytes[1]]))
    }

    fn read_i16(&mut self) -> NbtResult<i16> {
        Ok(self.read_u16()? as i16)
    }

    fn read_u32(&mut self) -> NbtResult<u32> {
        let bytes = self.read_exact(4)?;
        Ok(u32::from_be_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]))
    }

    fn read_i32(&mut self) -> NbtResult<i32> {
        Ok(self.read_u32()? as i32)
    }

    fn read_u64(&mut self) -> NbtResult<u64> {
        let bytes = self.read_exact(8)?;
        Ok(u64::from_be_bytes([
            bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5], bytes[6], bytes[7],
        ]))
    }

    fn read_i64(&mut self) -> NbtResult<i64> {
        Ok(self.read_u64()? as i64)
    }
}

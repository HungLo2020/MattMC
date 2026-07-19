use super::error::{NbtError, NbtErrorKind, NbtResult};
use super::limits::{LimitTracker, NbtLimits};
use super::model::{JavaString, ListTag, NbtDocument, NbtTag, TagId};
use super::modified_utf8::encode_modified_utf8;

pub fn write_document(document: &NbtDocument, limits: NbtLimits) -> NbtResult<Vec<u8>> {
    let mut writer = Writer::new(limits);
    if document.root.id() != TagId::Compound {
        return Err(NbtError::new(NbtErrorKind::InvalidRootType, 0));
    }
    writer.write_u8(TagId::Compound as u8)?;
    writer.write_string(&document.name)?;
    writer.write_payload(&document.root, 1)?;
    Ok(writer.output)
}

struct Writer {
    output: Vec<u8>,
    limits: LimitTracker,
}

impl Writer {
    fn new(limits: NbtLimits) -> Self {
        Self {
            output: Vec::new(),
            limits: LimitTracker::new(limits),
        }
    }

    fn write_payload(&mut self, tag: &NbtTag, depth: u32) -> NbtResult<()> {
        self.limits.enter_depth(depth, self.output.len())?;
        match tag {
            NbtTag::Byte(value) => self.write_u8(*value as u8),
            NbtTag::Short(value) => self.write_all(&value.to_be_bytes()),
            NbtTag::Int(value) => self.write_all(&value.to_be_bytes()),
            NbtTag::Long(value) => self.write_all(&value.to_be_bytes()),
            NbtTag::Float(bits) => self.write_all(&bits.to_be_bytes()),
            NbtTag::Double(bits) => self.write_all(&bits.to_be_bytes()),
            NbtTag::ByteArray(values) => {
                self.write_len(values.len())?;
                for value in values {
                    self.write_u8(*value as u8)?;
                }
                Ok(())
            }
            NbtTag::String(value) => self.write_string(value),
            NbtTag::List(value) => self.write_list(value, depth + 1),
            NbtTag::Compound(entries) => {
                self.limits.enter_depth(depth + 1, self.output.len())?;
                for entry in entries {
                    self.write_u8(entry.value.id() as u8)?;
                    self.write_string(&entry.name)?;
                    self.write_payload(&entry.value, depth + 1)?;
                }
                self.write_u8(TagId::End as u8)
            }
            NbtTag::IntArray(values) => {
                self.write_len(values.len())?;
                for value in values {
                    self.write_all(&value.to_be_bytes())?;
                }
                Ok(())
            }
            NbtTag::LongArray(values) => {
                self.write_len(values.len())?;
                for value in values {
                    self.write_all(&value.to_be_bytes())?;
                }
                Ok(())
            }
        }
    }

    fn write_list(&mut self, list: &ListTag, depth: u32) -> NbtResult<()> {
        self.limits.enter_depth(depth, self.output.len())?;
        self.write_u8(list.element_type as u8)?;
        self.write_len(list.elements.len())?;
        for element in &list.elements {
            if element.id() != list.element_type {
                return Err(NbtError::new(
                    NbtErrorKind::InvalidTagType,
                    self.output.len(),
                ));
            }
            self.write_payload(element, depth + 1)?;
        }
        Ok(())
    }

    fn write_string(&mut self, value: &JavaString) -> NbtResult<()> {
        let encoded = encode_modified_utf8(value, self.output.len())?;
        self.write_all(&(encoded.len() as u16).to_be_bytes())?;
        self.write_all(&encoded)
    }

    fn write_len(&mut self, len: usize) -> NbtResult<()> {
        if len > i32::MAX as usize {
            return Err(NbtError::new(
                NbtErrorKind::ExcessiveLength,
                self.output.len(),
            ));
        }
        self.write_all(&(len as i32).to_be_bytes())
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

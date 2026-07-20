use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};

use super::error::{PoiError, PoiErrorKind, PoiResult};
use super::model::{PoiChunk, PoiRecord, PoiSection};

pub const CURRENT_POI_DATA_VERSION: i32 = 4295;

pub fn decode_poi_document(document: &NbtDocument) -> PoiResult<PoiChunk> {
    let root = compound(&document.root, "root")?;
    let data_version = required_int(root, "DataVersion")?;
    if data_version != CURRENT_POI_DATA_VERSION {
        return Err(PoiError::new(
            PoiErrorKind::UnsupportedDataVersion,
            format!(
                "POI chunk DataVersion {} is not the current supported version {}",
                data_version, CURRENT_POI_DATA_VERSION
            ),
        ));
    }

    let Some(sections_tag) = find_entry(root, "Sections") else {
        return Ok(PoiChunk {
            sections: Vec::new(),
        });
    };
    let sections_compound = compound(sections_tag, "Sections")?;
    let mut sections = Vec::with_capacity(sections_compound.len());
    for entry in sections_compound {
        let section_y = java_string_to_str(&entry.name, "section key")?
            .parse::<i32>()
            .map_err(|_| {
                PoiError::new(PoiErrorKind::InvalidSectionKey, "invalid POI section Y key")
            })?;
        sections.push(decode_section(section_y, &entry.value)?);
    }
    sections.sort_by_key(|section| section.section_y);
    Ok(PoiChunk { sections })
}

fn decode_section(section_y: i32, tag: &NbtTag) -> PoiResult<PoiSection> {
    let compound = compound(tag, "section")?;
    let valid = optional_bool(compound, "Valid")?.unwrap_or(false);
    let records_tag = find_entry(compound, "Records").ok_or_else(|| {
        PoiError::new(PoiErrorKind::MissingField, "POI section is missing Records")
    })?;
    let records_list = list(records_tag, "Records")?;
    if records_list.element_type != TagId::Compound && !records_list.elements.is_empty() {
        return Err(PoiError::new(
            PoiErrorKind::WrongType,
            "POI Records must be a list of compounds",
        ));
    }
    let mut records = Vec::with_capacity(records_list.elements.len());
    for record in &records_list.elements {
        records.push(decode_record(record)?);
    }
    Ok(PoiSection {
        section_y,
        valid,
        records,
    })
}

fn decode_record(tag: &NbtTag) -> PoiResult<PoiRecord> {
    let compound = compound(tag, "record")?;
    let pos_tag = find_entry(compound, "pos")
        .ok_or_else(|| PoiError::new(PoiErrorKind::MissingField, "POI record is missing pos"))?;
    let (x, y, z) = decode_block_pos(pos_tag)?;
    let type_tag = find_entry(compound, "type")
        .ok_or_else(|| PoiError::new(PoiErrorKind::MissingField, "POI record is missing type"))?;
    let poi_type = string(type_tag, "type")?;
    validate_resource_id(&poi_type)?;
    let free_tickets = optional_int(compound, "free_tickets")?.unwrap_or(0);
    Ok(PoiRecord {
        x,
        y,
        z,
        poi_type,
        free_tickets,
    })
}

fn decode_block_pos(tag: &NbtTag) -> PoiResult<(i32, i32, i32)> {
    match tag {
        NbtTag::IntArray(values) if values.len() == 3 => Ok((values[0], values[1], values[2])),
        NbtTag::List(ListTag {
            element_type: TagId::Int,
            elements,
        }) if elements.len() == 3 => {
            let NbtTag::Int(x) = elements[0] else {
                return Err(PoiError::new(
                    PoiErrorKind::InvalidPosition,
                    "invalid BlockPos x",
                ));
            };
            let NbtTag::Int(y) = elements[1] else {
                return Err(PoiError::new(
                    PoiErrorKind::InvalidPosition,
                    "invalid BlockPos y",
                ));
            };
            let NbtTag::Int(z) = elements[2] else {
                return Err(PoiError::new(
                    PoiErrorKind::InvalidPosition,
                    "invalid BlockPos z",
                ));
            };
            Ok((x, y, z))
        }
        _ => Err(PoiError::new(
            PoiErrorKind::InvalidPosition,
            "POI record pos must be a three-int BlockPos",
        )),
    }
}

fn find_entry<'a>(entries: &'a [CompoundEntry], name: &str) -> Option<&'a NbtTag> {
    entries
        .iter()
        .find(|entry| entry.name.units() == JavaString::from_str(name).units())
        .map(|entry| &entry.value)
}

fn compound<'a>(tag: &'a NbtTag, field: &str) -> PoiResult<&'a [CompoundEntry]> {
    match tag {
        NbtTag::Compound(entries) => Ok(entries),
        _ => Err(PoiError::new(
            PoiErrorKind::WrongType,
            format!("{field} must be a compound"),
        )),
    }
}

fn list<'a>(tag: &'a NbtTag, field: &str) -> PoiResult<&'a ListTag> {
    match tag {
        NbtTag::List(list) => Ok(list),
        _ => Err(PoiError::new(
            PoiErrorKind::WrongType,
            format!("{field} must be a list"),
        )),
    }
}

fn required_int(entries: &[CompoundEntry], name: &str) -> PoiResult<i32> {
    optional_int(entries, name)?
        .ok_or_else(|| PoiError::new(PoiErrorKind::MissingField, format!("missing {name}")))
}

fn optional_int(entries: &[CompoundEntry], name: &str) -> PoiResult<Option<i32>> {
    let Some(tag) = find_entry(entries, name) else {
        return Ok(None);
    };
    match tag {
        NbtTag::Int(value) => Ok(Some(*value)),
        _ => Err(PoiError::new(
            PoiErrorKind::WrongType,
            format!("{name} must be an int"),
        )),
    }
}

fn optional_bool(entries: &[CompoundEntry], name: &str) -> PoiResult<Option<bool>> {
    let Some(tag) = find_entry(entries, name) else {
        return Ok(None);
    };
    match tag {
        NbtTag::Byte(value) => Ok(Some(*value != 0)),
        _ => Err(PoiError::new(
            PoiErrorKind::WrongType,
            format!("{name} must be a byte-backed boolean"),
        )),
    }
}

fn string(tag: &NbtTag, field: &str) -> PoiResult<String> {
    match tag {
        NbtTag::String(value) => java_string_to_str(value, field),
        _ => Err(PoiError::new(
            PoiErrorKind::WrongType,
            format!("{field} must be a string"),
        )),
    }
}

fn java_string_to_str(value: &JavaString, field: &str) -> PoiResult<String> {
    value.to_string_lossless_if_valid().ok_or_else(|| {
        PoiError::new(
            PoiErrorKind::InvalidPoiType,
            format!("{field} is not valid Unicode text"),
        )
    })
}

pub(super) fn validate_resource_id(value: &str) -> PoiResult<()> {
    if value.is_empty()
        || value.bytes().any(|byte| {
            !matches!(
                byte,
                b'a'..=b'z' | b'0'..=b'9' | b'_' | b'-' | b'.' | b'/' | b':'
            )
        })
        || value.split(':').count() > 2
    {
        Err(PoiError::new(
            PoiErrorKind::InvalidPoiType,
            format!("invalid POI type resource identifier {value}"),
        ))
    } else {
        Ok(())
    }
}

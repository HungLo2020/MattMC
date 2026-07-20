use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};

use super::decoder::CURRENT_POI_DATA_VERSION;
use super::error::{PoiError, PoiErrorKind, PoiResult};
use super::model::{PoiChunk, PoiRecord, PoiSection};

pub fn encode_poi_document(chunk: &PoiChunk) -> PoiResult<NbtDocument> {
    let sections = chunk
        .sections
        .iter()
        .map(encode_section_entry)
        .collect::<PoiResult<Vec<_>>>()?;

    Ok(NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![
            entry("DataVersion", NbtTag::Int(CURRENT_POI_DATA_VERSION)),
            entry("Sections", NbtTag::Compound(sections)),
        ]),
    })
}

fn encode_section_entry(section: &PoiSection) -> PoiResult<CompoundEntry> {
    let records = section
        .records
        .iter()
        .map(encode_record)
        .collect::<PoiResult<Vec<_>>>()?;
    Ok(entry(
        &section.section_y.to_string(),
        NbtTag::Compound(vec![
            entry("Valid", NbtTag::Byte(if section.valid { 1 } else { 0 })),
            entry(
                "Records",
                NbtTag::List(ListTag {
                    element_type: TagId::Compound,
                    elements: records,
                }),
            ),
        ]),
    ))
}

fn encode_record(record: &PoiRecord) -> PoiResult<NbtTag> {
    if record.poi_type.is_empty() {
        return Err(PoiError::new(
            PoiErrorKind::InvalidPoiType,
            "POI record has an empty type id",
        ));
    }
    Ok(NbtTag::Compound(vec![
        entry("pos", NbtTag::IntArray(vec![record.x, record.y, record.z])),
        entry(
            "type",
            NbtTag::String(JavaString::from_str(&record.poi_type)),
        ),
        entry("free_tickets", NbtTag::Int(record.free_tickets)),
    ]))
}

fn entry(name: &str, value: NbtTag) -> CompoundEntry {
    CompoundEntry {
        name: JavaString::from_str(name),
        value,
    }
}

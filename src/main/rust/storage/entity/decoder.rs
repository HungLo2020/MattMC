use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::tape::document_to_tape;
use std::time::Instant;

use super::error::{EntityError, EntityErrorKind, EntityResult};
use super::model::{EntityChunkEnvelope, EntityEnvelope};

pub const CURRENT_ENTITY_DATA_VERSION: i32 = 4556;

pub fn decode_entity_document(document: &NbtDocument) -> EntityResult<EntityChunkEnvelope> {
    decode_entity_document_with_timing(document).map(|(chunk, _timings)| chunk)
}

#[derive(Clone, Copy, Debug, Default)]
pub struct EntityDecodeTimings {
    pub envelope_traversal_nanos: u64,
    pub tape_creation_nanos: u64,
}

pub fn decode_entity_document_with_timing(
    document: &NbtDocument,
) -> EntityResult<(EntityChunkEnvelope, EntityDecodeTimings)> {
    let mut timings = EntityDecodeTimings::default();
    let root_started = Instant::now();
    let root = compound(&document.root, "root")?;
    let data_version = required_int(root, "DataVersion")?;
    let (chunk_x, chunk_z) = decode_chunk_position(
        find_entry(root, "Position")
            .ok_or_else(|| EntityError::new(EntityErrorKind::MissingField, "missing Position"))?,
    )?;
    add_elapsed(&mut timings.envelope_traversal_nanos, root_started);

    if data_version < CURRENT_ENTITY_DATA_VERSION {
        return Ok((
            EntityChunkEnvelope {
                data_version,
                chunk_x,
                chunk_z,
                requires_dfu: true,
                entities: Vec::new(),
            },
            timings,
        ));
    }
    if data_version > CURRENT_ENTITY_DATA_VERSION {
        return Err(EntityError::new(
            EntityErrorKind::UnsupportedDataVersion,
            format!(
                "entity chunk DataVersion {} is newer than supported {}",
                data_version, CURRENT_ENTITY_DATA_VERSION
            ),
        ));
    }

    let entities = match find_entry(root, "Entities") {
        Some(tag) => decode_entities(tag, &mut timings)?,
        None => Vec::new(),
    };

    Ok((
        EntityChunkEnvelope {
            data_version,
            chunk_x,
            chunk_z,
            requires_dfu: false,
            entities,
        },
        timings,
    ))
}

fn decode_entities(
    tag: &NbtTag,
    timings: &mut EntityDecodeTimings,
) -> EntityResult<Vec<EntityEnvelope>> {
    let list_started = Instant::now();
    let list = list(tag, "Entities")?;
    if list.element_type != TagId::Compound && !list.elements.is_empty() {
        return Err(EntityError::new(
            EntityErrorKind::WrongType,
            "Entities must be a list of compounds",
        ));
    }
    add_elapsed(&mut timings.envelope_traversal_nanos, list_started);
    let mut entities = Vec::with_capacity(list.elements.len());
    for entity in &list.elements {
        let NbtTag::Compound(entries) = entity else {
            return Err(EntityError::new(
                EntityErrorKind::WrongType,
                "entity list element must be a compound",
            ));
        };
        entities.push(decode_entity(entries, entity, timings)?);
    }
    Ok(entities)
}

fn decode_entity(
    entries: &[CompoundEntry],
    entity: &NbtTag,
    timings: &mut EntityDecodeTimings,
) -> EntityResult<EntityEnvelope> {
    let envelope_started = Instant::now();
    let id_tag = find_entry(entries, "id");
    let (id, id_malformed) = match id_tag {
        Some(NbtTag::String(value)) => (java_string_to_str(value), false),
        Some(_) => (None, true),
        None => (None, false),
    };
    let uuid = find_entry(entries, "UUID").and_then(decode_uuid);
    let position = find_entry(entries, "Pos").and_then(decode_entity_position);
    let (passenger_count, passenger_depth) =
        passenger_shape(find_entry(entries, "Passengers")).unwrap_or((0, 0));
    add_elapsed(&mut timings.envelope_traversal_nanos, envelope_started);

    let tape_started = Instant::now();
    let document = NbtDocument {
        name: JavaString::empty(),
        root: entity.clone(),
    };
    let nbt_tape = document_to_tape(&document, NbtLimits::defaults()).map_err(|_| {
        EntityError::new(
            EntityErrorKind::NbtEncodeFailed,
            "failed to encode opaque entity NBT tape",
        )
    })?;
    add_elapsed(&mut timings.tape_creation_nanos, tape_started);

    Ok(EntityEnvelope {
        id,
        id_malformed,
        uuid,
        position,
        passenger_count,
        passenger_depth,
        nbt_tape,
    })
}

fn passenger_shape(tag: Option<&NbtTag>) -> Option<(u32, u32)> {
    let Some(tag) = tag else {
        return Some((0, 0));
    };
    let NbtTag::List(ListTag {
        element_type,
        elements,
    }) = tag
    else {
        return Some((0, 0));
    };
    if *element_type != TagId::Compound && !elements.is_empty() {
        return Some((0, 0));
    }
    let mut count = 0u32;
    let mut max_depth = 0u32;
    for element in elements {
        let NbtTag::Compound(entries) = element else {
            continue;
        };
        count = count.saturating_add(1);
        let (child_count, child_depth) = passenger_shape(find_entry(entries, "Passengers"))?;
        count = count.saturating_add(child_count);
        max_depth = max_depth.max(1u32.saturating_add(child_depth));
    }
    Some((count, max_depth))
}

fn decode_chunk_position(tag: &NbtTag) -> EntityResult<(i32, i32)> {
    match tag {
        NbtTag::IntArray(values) if values.len() == 2 => Ok((values[0], values[1])),
        NbtTag::List(ListTag {
            element_type: TagId::Int,
            elements,
        }) if elements.len() == 2 => {
            let NbtTag::Int(x) = elements[0] else {
                return Err(invalid_position());
            };
            let NbtTag::Int(z) = elements[1] else {
                return Err(invalid_position());
            };
            Ok((x, z))
        }
        _ => Err(invalid_position()),
    }
}

fn invalid_position() -> EntityError {
    EntityError::new(
        EntityErrorKind::InvalidPosition,
        "entity chunk Position must contain chunk X and Z integers",
    )
}

fn decode_uuid(tag: &NbtTag) -> Option<(i64, i64)> {
    let NbtTag::IntArray(values) = tag else {
        return None;
    };
    if values.len() != 4 {
        return None;
    }
    let most = ((values[0] as i64) << 32) | (values[1] as u32 as i64);
    let least = ((values[2] as i64) << 32) | (values[3] as u32 as i64);
    Some((most, least))
}

fn decode_entity_position(tag: &NbtTag) -> Option<(u64, u64, u64)> {
    let NbtTag::List(ListTag {
        element_type: TagId::Double,
        elements,
    }) = tag
    else {
        return None;
    };
    if elements.len() != 3 {
        return None;
    }
    let NbtTag::Double(x) = elements[0] else {
        return None;
    };
    let NbtTag::Double(y) = elements[1] else {
        return None;
    };
    let NbtTag::Double(z) = elements[2] else {
        return None;
    };
    Some((x, y, z))
}

fn required_int(entries: &[CompoundEntry], name: &str) -> EntityResult<i32> {
    match find_entry(entries, name) {
        Some(NbtTag::Int(value)) => Ok(*value),
        Some(_) => Err(EntityError::new(
            EntityErrorKind::WrongType,
            format!("{name} must be an int"),
        )),
        None => Err(EntityError::new(
            EntityErrorKind::MissingField,
            format!("missing {name}"),
        )),
    }
}

fn find_entry<'a>(entries: &'a [CompoundEntry], name: &str) -> Option<&'a NbtTag> {
    entries
        .iter()
        .find(|entry| entry.name.units() == JavaString::from_str(name).units())
        .map(|entry| &entry.value)
}

fn compound<'a>(tag: &'a NbtTag, field: &str) -> EntityResult<&'a [CompoundEntry]> {
    match tag {
        NbtTag::Compound(entries) => Ok(entries),
        _ => Err(EntityError::new(
            EntityErrorKind::WrongType,
            format!("{field} must be a compound"),
        )),
    }
}

fn list<'a>(tag: &'a NbtTag, field: &str) -> EntityResult<&'a ListTag> {
    match tag {
        NbtTag::List(list) => Ok(list),
        _ => Err(EntityError::new(
            EntityErrorKind::WrongType,
            format!("{field} must be a list"),
        )),
    }
}

fn java_string_to_str(value: &JavaString) -> Option<String> {
    value.to_string_lossless_if_valid()
}

fn add_elapsed(total: &mut u64, started: Instant) {
    *total = total.saturating_add(started.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64);
}

use crate::storage::nbt::limits::NbtLimits;
use crate::storage::nbt::model::{CompoundEntry, JavaString, ListTag, NbtDocument, NbtTag, TagId};
use crate::storage::nbt::tape::document_from_tape;

use super::decoder::CURRENT_ENTITY_DATA_VERSION;
use super::error::{EntityError, EntityErrorKind, EntityResult};
use super::model::EntityChunkEnvelope;

pub fn encode_entity_document_from_tape(
    chunk: &EntityChunkEnvelope,
    limits: NbtLimits,
) -> EntityResult<NbtDocument> {
    if chunk.requires_dfu {
        return Err(EntityError::new(
            EntityErrorKind::UnsupportedDataVersion,
            "entity writer only accepts current-version entity chunks",
        ));
    }
    if chunk.data_version != CURRENT_ENTITY_DATA_VERSION {
        return Err(EntityError::new(
            EntityErrorKind::UnsupportedDataVersion,
            format!(
                "entity writer DataVersion {} does not match current {}",
                chunk.data_version, CURRENT_ENTITY_DATA_VERSION
            ),
        ));
    }

    let mut entities = Vec::with_capacity(chunk.entities.len());
    for entity in &chunk.entities {
        let document = document_from_tape(&entity.nbt_tape, limits).map_err(|error| {
            EntityError::new(
                EntityErrorKind::NbtEncodeFailed,
                format!("invalid entity NBT tape at offset {}", error.offset),
            )
        })?;
        match document.root {
            NbtTag::Compound(_) => entities.push(document.root),
            _ => {
                return Err(EntityError::new(
                    EntityErrorKind::WrongType,
                    "entity NBT tape root must be a compound",
                ));
            }
        }
    }

    Ok(NbtDocument {
        name: JavaString::empty(),
        root: NbtTag::Compound(vec![
            entry("DataVersion", NbtTag::Int(CURRENT_ENTITY_DATA_VERSION)),
            entry(
                "Position",
                NbtTag::IntArray(vec![chunk.chunk_x, chunk.chunk_z]),
            ),
            entry(
                "Entities",
                NbtTag::List(ListTag {
                    element_type: TagId::Compound,
                    elements: entities,
                }),
            ),
        ]),
    })
}

fn entry(name: &str, value: NbtTag) -> CompoundEntry {
    CompoundEntry {
        name: JavaString::from_str(name),
        value,
    }
}

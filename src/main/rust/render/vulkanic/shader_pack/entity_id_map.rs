//! Pack-owned semantic entity identifiers.
//!
//! Shader packs map canonical entity resource locations through
//! `entity.properties`. Rust owns this source-generation mapping so Java sends
//! gameplay identity rather than an Iris/OptiFine numeric material value.

use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::item_id_map::canonical_resource_location;
use super::preprocess::preprocess_artifact_with_runtime_options;
use super::source::ShaderPackSource;

pub const ENTITY_ID_MAP_PATH: &str = "entity.properties";
pub const UNMAPPED_ENTITY_ID: i32 = -1;

/// Immutable, generation-scoped mapping from a copied canonical entity type
/// identity to the selected pack's numeric semantic identifier.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackEntityIdMap {
    generation: u64,
    ids: BTreeMap<String, i32>,
}

impl ShaderPackEntityIdMap {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let Some(raw_properties) = source.get(ENTITY_ID_MAP_PATH) else {
            return Ok(Self {
                generation: source.generation(),
                ids: BTreeMap::new(),
            });
        };
        let properties = preprocess_artifact_with_runtime_options(source, ENTITY_ID_MAP_PATH, &[])
            .map(|artifact| artifact.expanded_source().to_string())
            .unwrap_or_else(|_| raw_properties.to_string());
        let mut ids = BTreeMap::new();

        for (line_number, raw_line) in properties.lines().enumerate() {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
                continue;
            }
            let Some(separator) = line.find(['=', ':']) else {
                return Err(GalError::invalid_argument(format!(
                    "entity.properties line {} is missing a key/value separator",
                    line_number + 1
                )));
            };
            let key = line[..separator].trim();
            let value = line[separator + 1..].trim();
            let Some(id_text) = key.strip_prefix("entity.") else {
                continue;
            };
            let id = match id_text.parse::<i32>() {
                Ok(id) => id,
                Err(_) => continue,
            };
            if value.is_empty() {
                return Err(GalError::invalid_argument(format!(
                    "entity.properties line {} has no entity identities",
                    line_number + 1
                )));
            }
            for token in value.split_whitespace() {
                if token.contains('=') || token.starts_with('#') {
                    continue;
                }
                let resource_location = canonical_resource_location(token).map_err(|reason| {
                    GalError::invalid_argument(format!(
                        "entity.properties line {} has {reason}: {token}",
                        line_number + 1
                    ))
                })?;
                if let Some(previous_id) = ids.insert(resource_location.clone(), id) {
                    return Err(GalError::invalid_argument(format!(
                        "entity.properties maps {resource_location} to both {previous_id} and {id}"
                    )));
                }
            }
        }

        Ok(Self {
            generation: source.generation(),
            ids,
        })
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn entry_count(&self) -> usize {
        self.ids.len()
    }

    /// Resolves copied canonical entity identity. A missing mapping preserves
    /// the source convention of `-1`, never a fabricated pack-specific ID.
    pub fn resolve(&self, resource_location: &str) -> GalResult<i32> {
        let resource_location =
            canonical_resource_location(resource_location).map_err(|reason| {
                GalError::invalid_argument(format!(
                    "entity semantic identity has {reason}: {resource_location}"
                ))
            })?;
        Ok(self
            .ids
            .get(&resource_location)
            .copied()
            .unwrap_or(UNMAPPED_ENTITY_ID))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    fn source(files: Vec<ShaderSourceFile>) -> ShaderPackSource {
        ShaderPackSource::new("entity-id-test", 9, files).unwrap()
    }

    #[test]
    fn parses_canonical_entity_identities_with_default_namespace() {
        let map = ShaderPackEntityIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ENTITY_ID_MAP_PATH,
            "entity.50076=boat minecraft:chest_boat\nentity.50092=trident\n",
        )]))
        .unwrap();
        assert_eq!(9, map.generation());
        assert_eq!(3, map.entry_count());
        assert_eq!(50076, map.resolve("minecraft:boat").unwrap());
        assert_eq!(50076, map.resolve("chest_boat").unwrap());
        assert_eq!(50092, map.resolve("minecraft:trident").unwrap());
        assert_eq!(UNMAPPED_ENTITY_ID, map.resolve("minecraft:arrow").unwrap());
    }

    #[test]
    fn absent_entity_properties_preserves_unmapped_default() {
        let map = ShaderPackEntityIdMap::from_source(&source(Vec::new())).unwrap();
        assert_eq!(0, map.entry_count());
        assert_eq!(UNMAPPED_ENTITY_ID, map.resolve("minecraft:arrow").unwrap());
    }

    #[test]
    fn rejects_ambiguous_or_noncanonical_entity_maps() {
        let duplicate = ShaderPackEntityIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ENTITY_ID_MAP_PATH,
            "entity.7=boat\nentity.8=minecraft:boat\n",
        )]))
        .expect_err("one entity identity cannot have ambiguous source ids");
        assert!(duplicate.to_string().contains("both 7 and 8"));

        let malformed = ShaderPackEntityIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ENTITY_ID_MAP_PATH,
            "entity.7=Minecraft:Boat\n",
        )]))
        .expect_err("source identities must be canonical before binding");
        assert!(malformed.to_string().contains("non-canonical"));
    }

    #[test]
    fn bundled_complementary_map_is_generation_scoped_and_deterministic() {
        let source = crate::render::vulkanic::shader_pack::terrain_contract::bundled_complementary_hung_loified_source(37)
            .expect("bundled source must be a valid owned snapshot");
        let map = ShaderPackEntityIdMap::from_source(&source)
            .expect("bundled entity.properties must be deterministic and unambiguous");
        assert_eq!(37, map.generation());
        assert_eq!(50076, map.resolve("minecraft:boat").unwrap());
        assert_eq!(UNMAPPED_ENTITY_ID, map.resolve("minecraft:arrow").unwrap());
    }
}

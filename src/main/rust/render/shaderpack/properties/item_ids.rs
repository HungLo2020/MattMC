//! Pack-owned semantic held-item identifiers.
//!
//! OptiFine/Iris shader packs map item resource locations through
//! `item.properties`. Rust parses that source generation itself so a future
//! terrain source route never receives an Iris integer map or renderer state.

use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::source::{ShaderPackSource, RUNTIME_BLOCK_ITEM_STATES_PATH};

pub const ITEM_ID_MAP_PATH: &str = "item.properties";
pub const UNMAPPED_ITEM_ID: i32 = -1;

/// Immutable, generation-scoped mapping from a canonical item-model resource
/// location to the integer semantic declared by one shader pack.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackItemIdMap {
    generation: u64,
    ids: BTreeMap<String, i32>,
    /// Drawn block items: item identity -> the pack's block material for the
    /// item's default block state (Iris `currentRenderedItemId`).
    block_item_ids: BTreeMap<String, i32>,
}

impl ShaderPackItemIdMap {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let mut ids = BTreeMap::new();
        let Some(properties) = source.get(ITEM_ID_MAP_PATH) else {
            return Ok(Self {
                generation: source.generation(),
                ids,
                block_item_ids: BTreeMap::new(),
            });
        };

        for (line_number, raw_line) in properties.lines().enumerate() {
            let line = raw_line.trim();
            if line.is_empty() || line.starts_with('#') || line.starts_with('!') {
                continue;
            }
            let Some(separator) = line.find(['=', ':']) else {
                return Err(GalError::invalid_argument(format!(
                    "item.properties line {} is missing a key/value separator",
                    line_number + 1
                )));
            };
            let key = line[..separator].trim();
            let value = line[separator + 1..].trim();
            let Some(id_text) = key.strip_prefix("item.") else {
                continue;
            };
            let id = match id_text.parse::<i32>() {
                Ok(id) => id,
                Err(_) => continue,
            };
            if value.is_empty() {
                return Err(GalError::invalid_argument(format!(
                    "item.properties line {} has no item identities",
                    line_number + 1
                )));
            }
            for token in value.split_whitespace() {
                // Iris ignores item-state predicates because `item.properties`
                // maps only item identity, not stack components.
                if token.contains('=') {
                    continue;
                }
                let resource_location = canonical_resource_location(token).map_err(|reason| {
                    GalError::invalid_argument(format!(
                        "item.properties line {} has {reason}: {token}",
                        line_number + 1
                    ))
                })?;
                if let Some(previous_id) = ids.insert(resource_location.clone(), id) {
                    return Err(GalError::invalid_argument(format!(
                        "item.properties maps {resource_location} to both {previous_id} and {id}"
                    )));
                }
            }
        }

        Ok(Self {
            generation: source.generation(),
            ids,
            block_item_ids: block_item_ids(source)?,
        })
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn entry_count(&self) -> usize {
        self.ids.len()
    }

    /// Resolves a raw copied item-model identity. Missing entries intentionally
    /// preserve Iris's `Object2IntMap` default return value of `-1`.
    pub fn resolve(&self, resource_location: &str) -> GalResult<i32> {
        let resource_location =
            canonical_resource_location(resource_location).map_err(|reason| {
                GalError::invalid_argument(format!(
                    "held item semantic identity has {reason}: {resource_location}"
                ))
            })?;
        Ok(self
            .ids
            .get(&resource_location)
            .copied()
            .unwrap_or(UNMAPPED_ITEM_ID))
    }

    /// Resolves the drawn item's `currentRenderedItemId`. Iris uses the
    /// pack's block material of a block item's default state (0 when no
    /// `block.properties` rule matches) and `item.properties` otherwise.
    pub fn resolve_rendered(&self, resource_location: &str) -> GalResult<i32> {
        if resource_location.is_empty() {
            return Ok(UNMAPPED_ITEM_ID);
        }
        let canonical = canonical_resource_location(resource_location).map_err(|reason| {
            GalError::invalid_argument(format!(
                "rendered item semantic identity has {reason}: {resource_location}"
            ))
        })?;
        match self.block_item_ids.get(&canonical) {
            Some(id) => Ok(*id),
            None => self.resolve(&canonical),
        }
    }

    /// Resolves an optional copied gameplay identity. Empty hands are a
    /// semantic absence, matching Iris's unmapped `-1` behavior without
    /// requiring a fabricated `minecraft:air` model identity.
    pub fn resolve_optional(&self, resource_location: &str) -> GalResult<i32> {
        if resource_location.is_empty() {
            return Ok(UNMAPPED_ITEM_ID);
        }
        self.resolve(resource_location)
    }
}

/// Iris resolves drawn block items only when the pack declares both
/// `item.properties` and `block.properties`.
fn block_item_ids(source: &ShaderPackSource) -> GalResult<BTreeMap<String, i32>> {
    let mut ids = BTreeMap::new();
    let Some(snapshot) = source.get(RUNTIME_BLOCK_ITEM_STATES_PATH) else {
        return Ok(ids);
    };
    if source.get("block.properties").is_none() {
        return Ok(ids);
    }
    // Block-rule support is gated by the terrain contract, which leaves a
    // pack it cannot resolve unadmitted; do not fail generation load here.
    let Ok(state_materials) = crate::render::shaderpack::contracts::terrain::runtime_block_state_material_ids(source)
    else {
        return Ok(ids);
    };
    let Some(state_materials) = state_materials else {
        return Err(GalError::invalid_argument(
            "runtime block-item table requires the runtime block-state table",
        ));
    };
    for (line_number, raw_line) in snapshot.lines().enumerate() {
        let line = raw_line.trim();
        if line.is_empty() {
            continue;
        }
        let parsed = line
            .strip_prefix("item.")
            .and_then(|entry| entry.split_once('='))
            .and_then(|(identity, state)| {
                Some((canonical_resource_location(identity).ok()?, state.parse::<i32>().ok()?))
            });
        let Some((identity, state_id)) = parsed else {
            return Err(GalError::invalid_argument(format!(
                "runtime block-item line {} is malformed",
                line_number + 1
            )));
        };
        let material = *state_materials.get(&state_id).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "runtime block-item line {} names unknown block state {state_id}",
                line_number + 1
            ))
        })?;
        ids.insert(identity, if material < 0 { 0 } else { material });
    }
    Ok(ids)
}

pub(crate) fn canonical_resource_location(value: &str) -> Result<String, &'static str> {
    let (namespace, path) = match value.split_once(':') {
        Some((namespace, path)) => (namespace, path),
        None => ("minecraft", value),
    };
    validate_resource_location_parts(namespace, path)?;
    Ok(format!("{namespace}:{path}"))
}

/// Same as `canonical_resource_location(value) == Ok(value)` without
/// allocating; per-frame mesh validation calls it for every source entity.
pub(crate) fn is_canonical_resource_location(value: &str) -> bool {
    value
        .split_once(':')
        .is_some_and(|(namespace, path)| validate_resource_location_parts(namespace, path).is_ok())
}

fn validate_resource_location_parts(namespace: &str, path: &str) -> Result<(), &'static str> {
    if namespace.is_empty() || path.is_empty() || path.contains(':') {
        return Err("an invalid namespace:path identity");
    }
    let valid_namespace = namespace.bytes().all(|byte| {
        byte.is_ascii_lowercase() || byte.is_ascii_digit() || matches!(byte, b'_' | b'-' | b'.')
    });
    let valid_path = path.bytes().all(|byte| {
        byte.is_ascii_lowercase()
            || byte.is_ascii_digit()
            || matches!(byte, b'_' | b'-' | b'.' | b'/')
    });
    if !valid_namespace || !valid_path {
        return Err("a non-canonical resource location");
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use crate::render::shaderpack::properties::item_ids::*;
    #[test]
    fn canonical_predicate_matches_formatted_identity() {
        for value in ["minecraft:pig", "pig", "Minecraft:pig", "minecraft:", ":pig", "a:b:c",
            "mod-x.y:model-part/arm_1", "minecraft:Pig", "", "minecraft:pig "] {
            assert_eq!(is_canonical_resource_location(value),
                canonical_resource_location(value).ok().as_deref() == Some(value), "{value:?}");
        }
    }

    use crate::render::shaderpack::source::ShaderSourceFile;

    fn source(files: Vec<ShaderSourceFile>) -> ShaderPackSource {
        ShaderPackSource::new("item-id-test", 9, files).unwrap()
    }

    #[test]
    fn drawn_block_items_resolve_their_default_state_block_material() {
        use crate::render::shaderpack::source::RUNTIME_BLOCK_STATE_IDENTITIES_PATH;
        let map = ShaderPackItemIdMap::from_source(&source(vec![
            ShaderSourceFile::new(ITEM_ID_MAP_PATH, "item.45032=lava_bucket\n"),
            ShaderSourceFile::new(
                "block.properties",
                "block.10020=minecraft:gray_stained_glass_pane\n",
            ),
            ShaderSourceFile::new(
                RUNTIME_BLOCK_STATE_IDENTITIES_PATH,
                "state.7=minecraft:gray_stained_glass_pane|east=false\nstate.9=minecraft:dirt\n",
            ),
            ShaderSourceFile::new(
                RUNTIME_BLOCK_ITEM_STATES_PATH,
                "item.minecraft:gray_stained_glass_pane=7\nitem.minecraft:dirt=9\n",
            ),
        ]))
        .unwrap();
        assert_eq!(10020, map.resolve_rendered("minecraft:gray_stained_glass_pane").unwrap());
        // Iris: getOrDefault(defaultState, 0) for an unmatched block item.
        assert_eq!(0, map.resolve_rendered("minecraft:dirt").unwrap());
        assert_eq!(45032, map.resolve_rendered("minecraft:lava_bucket").unwrap());
        assert_eq!(UNMAPPED_ITEM_ID, map.resolve_rendered("minecraft:player/skin").unwrap());
        // heldItemId keeps Iris's item-map semantics for the same item.
        assert_eq!(UNMAPPED_ITEM_ID, map.resolve("minecraft:gray_stained_glass_pane").unwrap());
    }

    #[test]
    fn parses_pack_owned_item_identities_with_minecraft_default_namespace() {
        let map = ShaderPackItemIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ITEM_ID_MAP_PATH,
            "item.40000=spider_eye minecraft:fermented_spider_eye\nitem.45032=lava_bucket\n",
        )]))
        .unwrap();
        assert_eq!(9, map.generation());
        assert_eq!(3, map.entry_count());
        assert_eq!(40000, map.resolve("minecraft:spider_eye").unwrap());
        assert_eq!(40000, map.resolve("fermented_spider_eye").unwrap());
        assert_eq!(45032, map.resolve("minecraft:lava_bucket").unwrap());
        assert_eq!(UNMAPPED_ITEM_ID, map.resolve("minecraft:stick").unwrap());
    }

    #[test]
    fn absent_item_properties_preserves_iris_unmapped_default() {
        let map = ShaderPackItemIdMap::from_source(&source(Vec::new())).unwrap();
        assert_eq!(0, map.entry_count());
        assert_eq!(UNMAPPED_ITEM_ID, map.resolve("minecraft:air").unwrap());
        assert_eq!(UNMAPPED_ITEM_ID, map.resolve_optional("").unwrap());
    }

    #[test]
    fn ignores_non_item_entries_and_item_state_predicates_like_iris() {
        let map = ShaderPackItemIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ITEM_ID_MAP_PATH,
            "block.7=stone\nitem.7=stone damaged=true\n",
        )]))
        .unwrap();
        assert_eq!(7, map.resolve("minecraft:stone").unwrap());
        assert_eq!(1, map.entry_count());
    }

    #[test]
    fn rejects_ambiguous_or_malformed_item_identity_maps() {
        let duplicate = ShaderPackItemIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ITEM_ID_MAP_PATH,
            "item.7=stone\nitem.8=minecraft:stone\n",
        )]))
        .expect_err("one resource identity cannot have ambiguous source ids");
        assert!(duplicate.to_string().contains("both 7 and 8"));

        let malformed = ShaderPackItemIdMap::from_source(&source(vec![ShaderSourceFile::new(
            ITEM_ID_MAP_PATH,
            "item.7=Minecraft:Stone\n",
        )]))
        .expect_err("source identities must be canonical before binding");
        assert!(malformed.to_string().contains("non-canonical"));
    }

    #[test]
    fn bundled_complementary_map_matches_selected_source_item_identities() {
        let source = crate::render::shaderpack::contracts::terrain::bundled_complementary_hung_loified_source(37)
			.expect("bundled source must be a valid owned snapshot");
        let map = ShaderPackItemIdMap::from_source(&source)
            .expect("bundled item.properties must be deterministic and unambiguous");
        assert_eq!(37, map.generation());
        assert_eq!(40000, map.resolve("minecraft:spider_eye").unwrap());
        assert_eq!(45032, map.resolve("minecraft:lava_bucket").unwrap());
        assert_eq!(45108, map.resolve("minecraft:totem_of_undying").unwrap());
    }
}

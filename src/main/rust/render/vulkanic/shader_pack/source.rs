use std::collections::BTreeMap;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::held_light_policy::ShaderPackHeldLightPolicy;
use super::item_id_map::ShaderPackItemIdMap;
use super::shadow_policy::ShaderPackShadowPolicy;

/// Reserved semantic configuration file generated alongside one complete
/// source generation. It contains copied scalar preprocessor choices only,
/// never Java/Iris objects or backend state.
pub const RUNTIME_OPTIONS_PATH: &str = "mattmc/runtime-options.properties";
/// Reserved semantic environment generated for one selected source
/// generation. It carries scalar preprocessor facts only, never Iris objects,
/// active GPU state, or backend handles.
pub const RUNTIME_ENVIRONMENT_PATH: &str = "mattmc/runtime-environment.properties";

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderSourceFile {
    pub path: String,
    pub contents: String,
}

impl ShaderSourceFile {
    pub fn new(path: impl Into<String>, contents: impl Into<String>) -> Self {
        Self {
            path: path.into(),
            contents: contents.into(),
        }
    }
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackSource {
    name: String,
    generation: u64,
    files: BTreeMap<String, String>,
}

impl ShaderPackSource {
    pub fn new(
        name: impl Into<String>,
        generation: u64,
        files: Vec<ShaderSourceFile>,
    ) -> GalResult<Self> {
        let name = name.into();
        if name.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack source name is empty",
            ));
        }
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "shader-pack source generation must be non-zero",
            ));
        }
        if files.len() > Self::MAX_FILES {
            return Err(GalError::invalid_argument(format!(
                "shader-pack source has {} files, exceeding {}",
                files.len(),
                Self::MAX_FILES
            )));
        }
        let mut total_bytes = 0usize;
        let mut map = BTreeMap::new();
        for file in files {
            let path = normalize_source_path(&file.path)?;
            if file.contents.len() > Self::MAX_FILE_BYTES {
                return Err(GalError::invalid_argument(format!(
                    "shader source {path} exceeds {} bytes",
                    Self::MAX_FILE_BYTES
                )));
            }
            total_bytes = total_bytes
                .checked_add(file.contents.len())
                .ok_or_else(|| {
                    GalError::invalid_argument("shader-pack source byte count overflow")
                })?;
            if total_bytes > Self::MAX_TOTAL_BYTES {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack source exceeds {} aggregate bytes",
                    Self::MAX_TOTAL_BYTES
                )));
            }
            if map.insert(path.clone(), file.contents).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "duplicate shader source path {path}"
                )));
            }
        }
        Ok(Self {
            name,
            generation,
            files: map,
        })
    }

    pub const MAX_FILES: usize = 4096;
    pub const MAX_FILE_BYTES: usize = 4 * 1024 * 1024;
    pub const MAX_TOTAL_BYTES: usize = 64 * 1024 * 1024;

    pub fn name(&self) -> &str {
        &self.name
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    /// An empty, explicitly named generation is the semantic disabled state.
    /// It clears any prior selected pack without turning a normal user choice
    /// into a malformed-source diagnostic.
    pub fn is_empty(&self) -> bool {
        self.files.is_empty()
    }

    pub fn get(&self, path: &str) -> Option<&str> {
        normalize_source_path(path)
            .ok()
            .and_then(|path| self.files.get(&path).map(String::as_str))
    }

    /// Returns an owned semantic source snapshot for a bounded transport or
    /// test handoff. Backend objects, shader compiler state, and file handles
    /// remain outside this representation.
    pub fn files(&self) -> Vec<ShaderSourceFile> {
        self.files
            .iter()
            .map(|(path, contents)| ShaderSourceFile::new(path.clone(), contents.clone()))
            .collect()
    }

    /// Parses the optional immutable runtime option snapshot carried by this
    /// source generation. Keeping its validation beside source ownership makes
    /// contract discovery and future source lowering consume precisely the
    /// same configuration bytes.
    pub fn runtime_option_defines(&self) -> GalResult<BTreeMap<String, String>> {
        self.runtime_define_file(RUNTIME_OPTIONS_PATH, "option")
    }

    /// Complete scalar source configuration for deterministic preprocessing.
    /// Pack-selected options and host environment defines remain distinct files
    /// on transport, then merge here with duplicate rejection so a source
    /// branch never depends on an implicit precedence rule.
    pub fn runtime_semantic_defines(&self) -> GalResult<BTreeMap<String, String>> {
        let mut defines = self.runtime_option_defines()?;
        for (key, value) in self.runtime_define_file(RUNTIME_ENVIRONMENT_PATH, "environment")? {
            if defines.insert(key.clone(), value).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "runtime shader-pack define '{key}' is present in both option and environment snapshots"
                )));
            }
        }
        Ok(defines)
    }

    fn runtime_define_file(
        &self,
        path: &str,
        description: &str,
    ) -> GalResult<BTreeMap<String, String>> {
        let Some(options) = self.get(path) else {
            return Ok(BTreeMap::new());
        };
        let mut defines = BTreeMap::new();
        for (line_number, line) in options.lines().enumerate() {
            let line = line.trim();
            if line.is_empty() || line.starts_with('#') {
                continue;
            }
            let Some((key, value)) = line.split_once('=') else {
                return Err(GalError::invalid_argument(format!(
                    "runtime shader-pack {description} line {} is missing '='",
                    line_number + 1
                )));
            };
            let key = key.trim();
            let value = value.trim();
            validate_runtime_define(key, value)?;
            if defines.insert(key.to_owned(), value.to_owned()).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "runtime shader-pack {description} define '{key}' is duplicated"
                )));
            }
        }
        Ok(defines)
    }
}

fn validate_runtime_define(key: &str, value: &str) -> GalResult<()> {
    let mut characters = key.chars();
    if !matches!(characters.next(), Some(character) if character == '_' || character.is_ascii_alphabetic())
        || !characters.all(|character| character == '_' || character.is_ascii_alphanumeric())
    {
        return Err(GalError::invalid_argument(format!(
            "runtime shader-pack option '{key}' is not a preprocessor identifier"
        )));
    }
    if value.is_empty()
        || value.chars().any(char::is_whitespace)
        || value.contains(['#', '\\', '='])
    {
        return Err(GalError::invalid_argument(format!(
            "runtime shader-pack option '{key}' is not one preprocessor token"
        )));
    }
    Ok(())
}

/// One bulk, owned shader-pack source generation. It is deliberately a
/// resource-generation payload rather than a per-program query, so Java can
/// copy source files without sharing a resource manager, Iris object, or
/// backend handle with Rust.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackSourceUpdate {
    pub pack_name: String,
    pub generation: u64,
    pub files: Vec<ShaderSourceFile>,
}

#[derive(Default)]
pub struct ShaderPackSourceStore {
    active: Option<ShaderPackSource>,
    active_item_id_map: Option<ShaderPackItemIdMap>,
    active_held_light_policy: Option<ShaderPackHeldLightPolicy>,
    active_shadow_policy: Option<ShaderPackShadowPolicy>,
    failed_generations: Vec<u64>,
}

impl ShaderPackSourceStore {
    pub const MAX_FAILED_GENERATIONS: usize = 32;

    /// Validates the complete candidate before replacing the active source.
    /// A malformed or stale update leaves the previous valid generation intact.
    pub fn apply_update(&mut self, update: ShaderPackSourceUpdate) -> GalResult<()> {
        if self
            .active
            .as_ref()
            .is_some_and(|active| update.generation <= active.generation())
        {
            self.record_failed_generation(update.generation);
            return Err(GalError::invalid_argument(
                "shader-pack source generation is stale",
            ));
        }
        let candidate =
            match ShaderPackSource::new(update.pack_name, update.generation, update.files) {
                Ok(candidate) => candidate,
                Err(error) => {
                    self.record_failed_generation(update.generation);
                    return Err(error);
                }
            };
        let item_id_map = match ShaderPackItemIdMap::from_source(&candidate) {
            Ok(item_id_map) => item_id_map,
            Err(error) => {
                self.record_failed_generation(update.generation);
                return Err(error);
            }
        };
        let held_light_policy = match ShaderPackHeldLightPolicy::from_source(&candidate) {
            Ok(policy) => policy,
            Err(error) => {
                self.record_failed_generation(update.generation);
                return Err(error);
            }
        };
        let shadow_policy = match ShaderPackShadowPolicy::from_source(&candidate) {
            Ok(policy) => policy,
            Err(error) => {
                self.record_failed_generation(update.generation);
                return Err(error);
            }
        };
        self.active = Some(candidate);
        self.active_item_id_map = Some(item_id_map);
        self.active_held_light_policy = Some(held_light_policy);
        self.active_shadow_policy = shadow_policy;
        Ok(())
    }

    pub fn active(&self) -> Option<&ShaderPackSource> {
        self.active.as_ref()
    }

    pub fn active_generation(&self) -> Option<u64> {
        self.active.as_ref().map(ShaderPackSource::generation)
    }

    /// The pack-owned item map belongs to the same immutable source generation
    /// as `active`. It is never a Java/Iris id map or a backend resource.
    pub fn active_item_id_map(&self) -> Option<&ShaderPackItemIdMap> {
        self.active_item_id_map.as_ref()
    }

    /// The held-light policy belongs to the exact immutable source generation
    /// as the item map and source snapshot.
    pub fn active_held_light_policy(&self) -> Option<ShaderPackHeldLightPolicy> {
        self.active_held_light_policy
    }

    /// Optional source-derived ordinary-world shadow policy. It is absent for
    /// sources that do not provide the common directive file; selected-source
    /// admission will then reject any required shadow uniforms explicitly.
    pub fn active_shadow_policy(&self) -> Option<ShaderPackShadowPolicy> {
        self.active_shadow_policy
    }

    pub fn failed_generations(&self) -> &[u64] {
        &self.failed_generations
    }

    fn record_failed_generation(&mut self, generation: u64) {
        if self.failed_generations.len() == Self::MAX_FAILED_GENERATIONS {
            self.failed_generations.remove(0);
        }
        self.failed_generations.push(generation);
    }
}

fn normalize_source_path(path: &str) -> GalResult<String> {
    if path.contains('\0') {
        return Err(GalError::invalid_argument(
            "shader source path contains NUL",
        ));
    }
    let path = path.trim().replace('\\', "/");
    let path = path.trim_start_matches('/');
    if path.is_empty() {
        return Err(GalError::invalid_argument("shader source path is empty"));
    }
    let mut normalized = Vec::new();
    for component in path.split('/') {
        match component {
            "" | "." => {}
            ".." => {
                return Err(GalError::invalid_argument(
                    "shader source path escapes its pack root",
                ));
            }
            value => normalized.push(value),
        }
    }
    if normalized.is_empty() {
        return Err(GalError::invalid_argument("shader source path is empty"));
    }
    Ok(normalized.join("/"))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn update(generation: u64, contents: &str) -> ShaderPackSourceUpdate {
        ShaderPackSourceUpdate {
            pack_name: "test-pack".to_string(),
            generation,
            files: vec![ShaderSourceFile::new("lib\\common.glsl", contents)],
        }
    }

    #[test]
    fn source_paths_are_canonical_and_do_not_escape_the_pack_root() {
        let source = ShaderPackSource::new(
            "test-pack",
            1,
            vec![ShaderSourceFile::new("/lib\\common.glsl", "void main() {}")],
        )
        .unwrap();
        assert_eq!(Some("void main() {}"), source.get("/lib/common.glsl"));
        assert!(ShaderPackSource::new(
            "test-pack",
            1,
            vec![ShaderSourceFile::new("../outside.glsl", "")],
        )
        .is_err());
    }

    #[test]
    fn source_store_replaces_only_complete_new_generations() {
        let mut store = ShaderPackSourceStore::default();
        store.apply_update(update(3, "first")).unwrap();
        assert_eq!(Some(3), store.active_generation());
        assert_eq!(
            Some("first"),
            store.active().unwrap().get("lib/common.glsl")
        );

        let malformed = ShaderPackSourceUpdate {
            pack_name: "test-pack".to_string(),
            generation: 4,
            files: vec![ShaderSourceFile::new("../../outside.glsl", "bad")],
        };
        assert!(store.apply_update(malformed).is_err());
        assert_eq!(Some(3), store.active_generation());
        assert_eq!(
            Some("first"),
            store.active().unwrap().get("lib/common.glsl")
        );

        store.apply_update(update(5, "replacement")).unwrap();
        assert_eq!(Some(5), store.active_generation());
        assert_eq!(
            Some("replacement"),
            store.active().unwrap().get("lib/common.glsl")
        );
        assert_eq!(&[4], store.failed_generations());
    }

    #[test]
    fn source_store_bounds_failed_generation_diagnostics() {
        let mut store = ShaderPackSourceStore::default();
        store.apply_update(update(100, "valid")).unwrap();
        for generation in 1..=ShaderPackSourceStore::MAX_FAILED_GENERATIONS as u64 + 4 {
            assert!(store.apply_update(update(generation, "stale")).is_err());
        }
        assert_eq!(
            ShaderPackSourceStore::MAX_FAILED_GENERATIONS,
            store.failed_generations().len()
        );
        assert_eq!(
            5,
            store.failed_generations()[0],
            "oldest diagnostics are retired before unbounded accumulation"
        );
        assert_eq!(
            ShaderPackSourceStore::MAX_FAILED_GENERATIONS as u64 + 4,
            *store.failed_generations().last().unwrap()
        );
        assert_eq!(Some(100), store.active_generation());
    }

    #[test]
    fn runtime_option_snapshot_is_canonical_and_rejects_malformed_entries() {
        let source = ShaderPackSource::new(
            "test-pack",
            1,
            vec![ShaderSourceFile::new(
                RUNTIME_OPTIONS_PATH,
                "Z_OPTION=low\n# comment\nA_OPTION=1\n",
            )],
        )
        .unwrap();
        assert_eq!(
            BTreeMap::from([
                ("A_OPTION".to_string(), "1".to_string()),
                ("Z_OPTION".to_string(), "low".to_string()),
            ]),
            source.runtime_option_defines().unwrap()
        );

        for contents in [
            "BAD-OPTION=1\n",
            "VALID=\n",
            "VALID=bad value\n",
            "A=1\nA=2\n",
        ] {
            let malformed = ShaderPackSource::new(
                "bad-options",
                2,
                vec![ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, contents)],
            )
            .unwrap();
            assert!(malformed.runtime_option_defines().is_err(), "{contents}");
        }
    }

    #[test]
    fn semantic_snapshot_merges_environment_defines_without_implicit_precedence() {
        let source = ShaderPackSource::new(
            "test-pack",
            3,
            vec![
                ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "QUALITY=2\n"),
                ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "IRIS_VERSION=12000\nIS_IRIS=1\n"),
            ],
        )
        .unwrap();
        assert_eq!(
            BTreeMap::from([
                ("IRIS_VERSION".to_string(), "12000".to_string()),
                ("IS_IRIS".to_string(), "1".to_string()),
                ("QUALITY".to_string(), "2".to_string()),
            ]),
            source.runtime_semantic_defines().unwrap()
        );

        let duplicate = ShaderPackSource::new(
            "test-pack",
            4,
            vec![
                ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "QUALITY=2\n"),
                ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "QUALITY=1\n"),
            ],
        )
        .unwrap();
        assert!(duplicate.runtime_semantic_defines().is_err());
    }

    #[test]
    fn source_store_rolls_back_an_invalid_pack_owned_item_map() {
        let mut store = ShaderPackSourceStore::default();
        store
            .apply_update(ShaderPackSourceUpdate {
                pack_name: "valid".to_string(),
                generation: 1,
                files: vec![ShaderSourceFile::new("item.properties", "item.7=stone\n")],
            })
            .unwrap();
        assert_eq!(Some(1), store.active_generation());
        assert_eq!(
            Some(7),
            store
                .active_item_id_map()
                .unwrap()
                .resolve("minecraft:stone")
                .ok()
        );

        let error = store
            .apply_update(ShaderPackSourceUpdate {
                pack_name: "invalid".to_string(),
                generation: 2,
                files: vec![ShaderSourceFile::new(
                    "item.properties",
                    "item.7=stone\nitem.8=minecraft:stone\n",
                )],
            })
            .expect_err("ambiguous item mappings must not replace a valid source generation");
        assert!(error.to_string().contains("both 7 and 8"));
        assert_eq!(Some(1), store.active_generation());
        assert_eq!(
            7,
            store
                .active_item_id_map()
                .unwrap()
                .resolve("minecraft:stone")
                .unwrap()
        );
    }

    #[test]
    fn source_store_rolls_back_an_invalid_held_light_policy() {
        let mut store = ShaderPackSourceStore::default();
        store
            .apply_update(ShaderPackSourceUpdate {
                pack_name: "valid".to_string(),
                generation: 1,
                files: vec![ShaderSourceFile::new(
                    "shaders.properties",
                    "oldHandLight=false\n",
                )],
            })
            .unwrap();
        assert!(!store
            .active_held_light_policy()
            .unwrap()
            .main_hand_uses_stronger_off_hand());

        let error = store
            .apply_update(ShaderPackSourceUpdate {
                pack_name: "invalid".to_string(),
                generation: 2,
                files: vec![ShaderSourceFile::new(
                    "shaders.properties",
                    "oldHandLight=maybe\n",
                )],
            })
            .expect_err("a malformed held-light policy must not replace a valid generation");
        assert!(error.to_string().contains("oldHandLight"));
        assert_eq!(Some(1), store.active_generation());
        assert!(!store
            .active_held_light_policy()
            .unwrap()
            .main_hand_uses_stronger_off_hand());
    }
}

//! Copied shader-source snapshots (shader packs and vanilla/resource-pack
//! shaders) and their store, with manifest parsing, preprocessing, dialect
//! preflight and binary pack assets.

pub mod assets;
pub mod dialect;
pub mod manifest;
pub mod preprocess;

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::properties::custom_uniforms::ShaderPackCustomUniformPolicy;
use crate::render::shaderpack::properties::held_light::ShaderPackHeldLightPolicy;
use crate::render::shaderpack::properties::item_ids::ShaderPackItemIdMap;
use crate::render::shaderpack::properties::shadow::ShaderPackShadowPolicy;
use crate::render::shaderpack::properties::wetness::ShaderPackWetnessPolicy;

/// Reserved semantic configuration file generated alongside one complete
/// source generation. It contains copied scalar preprocessor choices only,
/// never Java/Iris objects or backend state.
pub const RUNTIME_OPTIONS_PATH: &str = "mattmc/runtime-options.properties";
/// Reserved typed source-constant values selected by the pack configuration.
/// These are deliberately distinct from preprocessor definitions: a GLSL
/// `const float shadowDistance` cannot be represented as `#define
/// shadowDistance ...` without corrupting its declaration.
pub const RUNTIME_CONSTANTS_PATH: &str = "mattmc/runtime-constants.properties";
/// Reserved semantic environment generated for one selected source
/// generation. It carries scalar preprocessor facts only, never Iris objects,
/// active GPU state, or backend handles.
pub const RUNTIME_ENVIRONMENT_PATH: &str = "mattmc/runtime-environment.properties";
/// Reserved immutable Minecraft block-state table for one selected source
/// generation. Entries are canonical game semantics; Rust resolves them
/// against the pack's own `block.properties` rules and never reads Iris's
/// material map or renderer state.
pub const RUNTIME_BLOCK_STATE_IDENTITIES_PATH: &str = "mattmc/runtime-block-states.properties";
/// Reserved immutable block-item table: `item.<item identity>=<raw default
/// block-state id>`. Rust resolves each state through the table above and the
/// pack's `block.properties`, as Iris does for a drawn block item.
pub const RUNTIME_BLOCK_ITEM_STATES_PATH: &str = "mattmc/runtime-block-items.properties";

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
    runtime_semantic_defines: DerivedDefines,
    runtime_option_selection: DerivedOptionSelection,
    frame_uniform_policies: crate::render::shaderpack::properties::frame_uniforms::FrameUniformPolicies,
    shadow_policies: crate::render::shaderpack::properties::shadow::ShadowPolicies,
}

/// The saved pack options interpreted the way Iris applies them: a boolean
/// `#define` option set to `false` removes the pack's define, one set to
/// `true` enables it, and an option naming a `const` declaration rewrites that
/// constant instead of becoming a preprocessor define.
#[derive(Clone, Debug, Default)]
struct RuntimeOptionSelection {
    defines: BTreeMap<String, String>,
    disabled: BTreeSet<String>,
    constants: BTreeMap<String, String>,
}

#[derive(Clone, Debug, Default)]
struct DerivedOptionSelection(std::sync::OnceLock<GalResult<RuntimeOptionSelection>>);

impl PartialEq for DerivedOptionSelection {
    fn eq(&self, _: &Self) -> bool {
        true
    }
}

impl Eq for DerivedOptionSelection {}

/// Source generations are immutable, so their resolved runtime defines are
/// derived once. Frame planning queries render stages per batch; re-scanning
/// every pack file for include guards on each query cost hundreds of
/// milliseconds per frame. The memo is not part of source identity.
#[derive(Clone, Debug, Default)]
struct DerivedDefines(std::sync::OnceLock<GalResult<BTreeMap<String, String>>>);

impl PartialEq for DerivedDefines {
    fn eq(&self, _: &Self) -> bool {
        true
    }
}

impl Eq for DerivedDefines {}

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
            runtime_semantic_defines: DerivedDefines::default(),
            runtime_option_selection: DerivedOptionSelection::default(),
            frame_uniform_policies: Default::default(),
            shadow_policies: Default::default(),
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

    pub(crate) fn paths(&self) -> impl Iterator<Item = &str> {
        self.files.keys().map(String::as_str)
    }

    pub fn frame_uniform_policy(&self, scope: crate::render::shaderpack::contracts::terrain::TerrainProgramScope) -> GalResult<crate::render::shaderpack::properties::frame_uniforms::ShaderPackFrameUniformPolicy> {
        self.frame_uniform_policies.get(self, scope)
    }

    pub fn shadow_policy_for_scope(&self, scope: crate::render::shaderpack::contracts::terrain::TerrainProgramScope) -> GalResult<Option<ShaderPackShadowPolicy>> {
        self.shadow_policies.get(self, scope)
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

    /// Returns typed GLSL source constants selected by the pack configuration.
    /// Callers must rewrite only an emitted matching `const` declaration; these
    /// values are never valid preprocessor definitions.
    pub fn runtime_constant_values(&self) -> GalResult<BTreeMap<String, String>> {
        let mut constants = self.runtime_define_file(RUNTIME_CONSTANTS_PATH, "constant")?;
        for (key, value) in &self.runtime_option_selection()?.constants {
            constants.insert(key.clone(), value.clone());
        }
        Ok(constants)
    }

    /// Selected preprocessor options. Boolean options switched off are absent
    /// here and reported by [`Self::runtime_disabled_option_defines`].
    pub(crate) fn runtime_option_semantic_defines(&self) -> GalResult<BTreeMap<String, String>> {
        Ok(self.runtime_option_selection()?.defines.clone())
    }

    /// Boolean `#define` options the user switched off. Preprocessing must
    /// suppress the pack's own define for these names.
    pub(crate) fn runtime_disabled_option_defines(&self) -> GalResult<BTreeSet<String>> {
        Ok(self.runtime_option_selection()?.disabled.clone())
    }

    fn runtime_option_selection(&self) -> GalResult<&RuntimeOptionSelection> {
        self.runtime_option_selection
            .0
            .get_or_init(|| self.resolve_runtime_option_selection())
            .as_ref()
            .map_err(Clone::clone)
    }

    fn resolve_runtime_option_selection(&self) -> GalResult<RuntimeOptionSelection> {
        let include_guards = self.translation_unit_include_guards();
        let (define_options, const_options) = self.declared_option_names();
        let mut selection = RuntimeOptionSelection::default();
        for (key, value) in self.runtime_option_defines()? {
            if include_guards.contains(&key) {
                continue;
            }
            if define_options.contains(&key) {
                match value.as_str() {
                    "false" => {
                        selection.disabled.insert(key);
                    }
                    "true" => {
                        selection.defines.insert(key, "1".to_string());
                    }
                    _ => {
                        selection.defines.insert(key, value);
                    }
                }
            } else if const_options.contains(&key) {
                selection.constants.insert(key, value);
            } else {
                selection.defines.insert(key, value);
            }
        }
        Ok(selection)
    }

    /// Names the pack declares as `#define` options (including commented-out
    /// `//#define NAME` toggles) and as `const` declarations.
    fn declared_option_names(&self) -> (BTreeSet<String>, BTreeSet<String>) {
        fn identifier(text: &str) -> Option<&str> {
            let end = text
                .find(|character: char| !(character == '_' || character.is_ascii_alphanumeric()))
                .unwrap_or(text.len());
            (end > 0).then(|| &text[..end])
        }
        let mut defines = BTreeSet::new();
        let mut constants = BTreeSet::new();
        for contents in self.files.values() {
            for raw in contents.lines() {
                let line = raw.trim_start();
                let uncommented = line.strip_prefix("//").map_or(line, str::trim_start);
                if let Some(rest) = uncommented.strip_prefix('#') {
                    if let Some(rest) = rest.trim_start().strip_prefix("define") {
                        if rest.starts_with(char::is_whitespace) {
                            if let Some(name) = identifier(rest.trim_start()) {
                                defines.insert(name.to_string());
                            }
                        }
                    }
                } else if let Some(rest) = line.strip_prefix("const ") {
                    if let Some((left, _)) = rest.split_once('=') {
                        if let Some(name) = left.split_whitespace().last().and_then(identifier) {
                            constants.insert(name.to_string());
                        }
                    }
                }
            }
        }
        (defines, constants)
    }

    pub(crate) fn runtime_environment_semantic_defines(
        &self,
    ) -> GalResult<BTreeMap<String, String>> {
        let include_guards = self.translation_unit_include_guards();
        let mut defines = self.runtime_define_file(RUNTIME_ENVIRONMENT_PATH, "environment")?;
        defines.retain(|key, _| !include_guards.contains(key));
        // Iris adds `IRIS_FEATURE_<FLAG>` to every program environment for
        // each `iris.features.optional` flag the pack requests and the
        // renderer supports (ShaderPack.java). Rust advertises only features
        // it implements, derived from the pack's own selected properties.
        for feature in self.requested_supported_iris_features(&defines)? {
            defines
                .entry(format!("IRIS_FEATURE_{feature}"))
                .or_insert_with(|| "1".to_string());
        }
        Ok(defines)
    }

    /// Iris feature flags Rust implements for shader-pack execution.
    /// `CUSTOM_IMAGES` is backed by the Rust-owned voxel/flood-fill image
    /// runtime; packs declaring images it cannot own stay unadmitted.
    const RUST_SUPPORTED_IRIS_FEATURES: &'static [&'static str] = &["CUSTOM_IMAGES"];

    fn requested_supported_iris_features(
        &self,
        base_environment: &BTreeMap<String, String>,
    ) -> GalResult<Vec<&'static str>> {
        let Some(properties) = self.get("shaders.properties") else {
            return Ok(Vec::new());
        };
        if !properties.contains("iris.features") {
            return Ok(Vec::new());
        }
        let mut merged = self.runtime_option_semantic_defines()?;
        for (key, value) in base_environment {
            merged.entry(key.clone()).or_insert_with(|| value.clone());
        }
        // Packs conventionally keep option defaults in a common include that
        // their properties directives test (Iris evaluates properties with
        // the pack's option graph). Merge those scalar defaults without
        // overriding selected options or the transported environment.
        if self.get("lib/common.glsl").is_some() {
            let references = merged
                .iter()
                .map(|(key, value)| (key.as_str(), value.as_str()))
                .collect::<Vec<_>>();
            let common = crate::render::shaderpack::source::preprocess::preprocess(crate::render::shaderpack::source::preprocess::PreprocessInput {
                source: self,
                entry: "lib/common.glsl",
                defines: &references,
            })?;
            for line in common.lines() {
                let Some(rest) = line.trim().strip_prefix("#define ") else {
                    continue;
                };
                let definition = rest.split_once("//").map_or(rest, |(value, _)| value).trim();
                let mut parts = definition.split_whitespace();
                let (Some(name), Some(value), None) = (parts.next(), parts.next(), parts.next()) else {
                    continue;
                };
                if value.parse::<f64>().is_ok() && !name.contains('(') {
                    merged
                        .entry(name.to_string())
                        .or_insert_with(|| value.to_string());
                }
            }
        }
        let references = merged
            .iter()
            .map(|(key, value)| (key.as_str(), value.as_str()))
            .collect::<Vec<_>>();
        let expanded = crate::render::shaderpack::source::preprocess::preprocess(crate::render::shaderpack::source::preprocess::PreprocessInput {
            source: self,
            entry: "shaders.properties",
            defines: &references,
        })?;
        let mut requested = std::collections::BTreeSet::new();
        for line in expanded.lines() {
            let line = line.trim();
            let Some((key, value)) = line.split_once('=') else {
                continue;
            };
            if key.trim() == "iris.features.optional" {
                requested.extend(value.split_whitespace().map(str::to_ascii_uppercase));
            }
        }
        Ok(Self::RUST_SUPPORTED_IRIS_FEATURES
            .iter()
            .copied()
            .filter(|feature| requested.contains(*feature))
            .collect())
    }

    /// Complete scalar source configuration for deterministic preprocessing.
    /// Pack-selected options and host environment defines remain distinct files
    /// on transport, then merge here with duplicate rejection so a source
    /// branch never depends on an implicit precedence rule.
    pub fn runtime_semantic_defines(&self) -> GalResult<BTreeMap<String, String>> {
        self.runtime_semantic_defines_ref().cloned()
    }

    fn runtime_semantic_defines_ref(&self) -> GalResult<&BTreeMap<String, String>> {
        self.runtime_semantic_defines
            .0
            .get_or_init(|| self.resolve_runtime_semantic_defines())
            .as_ref()
            .map_err(Clone::clone)
    }

    fn resolve_runtime_semantic_defines(&self) -> GalResult<BTreeMap<String, String>> {
        // Iris's option discovery can surface an include guard from a pack
        // source as an apparent option. A translation-unit guard is not a
        // user-selected semantic configuration value: seeding it before Rust
        // expands the source would erase that file's contents. Recognize the
        // conventional guard form generically rather than naming a pack or a
        // particular shader feature here.
        let mut defines = self.runtime_option_semantic_defines()?;
        let disabled = self.runtime_disabled_option_defines()?;
        for (key, value) in self.runtime_environment_semantic_defines()? {
            // Environment defaults (for example a profile's quality levels)
            // yield to the options the user saved, as Iris applies them.
            if disabled.contains(&key) {
                continue;
            }
            defines.entry(key).or_insert(value);
        }
        Ok(defines)
    }

    /// Include guards are translation-unit-local implementation details. The
    /// bounded detection intentionally accepts only the standard file-header
    /// `#ifndef NAME` followed by `#define NAME` form, so regular pack
    /// configuration defines remain visible to the semantic source contract.
    fn translation_unit_include_guards(&self) -> BTreeSet<String> {
        self.files
            .values()
            .filter_map(|contents| {
                let mut directives = contents.lines().filter_map(|raw| {
                    let line = raw.trim();
                    if line.is_empty() || line.starts_with("//") || line.starts_with("/*") {
                        return None;
                    }
                    line.strip_prefix('#').map(str::trim)
                });
                let guard = directives.next()?.strip_prefix("ifndef ")?.trim();
                let define = directives.next()?.strip_prefix("define ")?.trim();
                let name = define.split_whitespace().next()?;
                (name == guard).then(|| guard.to_string())
            })
            .collect()
    }

    /// Resolves one source-generation scalar define required by a Rust-owned
    /// pass scheduler. This is intentionally a semantic configuration value,
    /// never a borrowed Iris phase object or a backend program property.
    pub fn runtime_semantic_i32(&self, name: &str) -> GalResult<i32> {
        let value = self
            .runtime_semantic_defines_ref()?
            .get(name)
            .ok_or_else(|| {
                GalError::unsupported_feature(format!(
                    "shader-pack source generation is missing required semantic define {name}"
                ))
            })?;
        value.parse::<i32>().map_err(|_| {
            GalError::invalid_argument(format!(
                "shader-pack semantic define {name} must be a signed integer, found '{value}'"
            ))
        })
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
    active_custom_uniform_policy: Option<ShaderPackCustomUniformPolicy>,
    active_held_light_policy: Option<ShaderPackHeldLightPolicy>,
    active_shadow_policy: Option<ShaderPackShadowPolicy>,
    active_wetness_policy: Option<ShaderPackWetnessPolicy>,
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
        let custom_uniform_policy = match ShaderPackCustomUniformPolicy::from_source(&candidate) {
            Ok(policy) => policy,
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
        let wetness_policy = match ShaderPackWetnessPolicy::from_source(&candidate) {
            Ok(policy) => policy,
            Err(error) => {
                self.record_failed_generation(update.generation);
                return Err(error);
            }
        };
        self.active = Some(candidate);
        self.active_item_id_map = Some(item_id_map);
        self.active_custom_uniform_policy = Some(custom_uniform_policy);
        self.active_held_light_policy = Some(held_light_policy);
        self.active_shadow_policy = shadow_policy;
        self.active_wetness_policy = Some(wetness_policy);
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

    /// Source-defined custom scalar declarations for this exact generation.
    /// Their values are still supplied only by explicitly supported Rust
    /// semantic evaluators.
    pub fn active_custom_uniform_policy(&self) -> Option<&ShaderPackCustomUniformPolicy> {
        self.active_custom_uniform_policy.as_ref()
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

    pub fn active_shadow_policy_for_scope(&self, scope: crate::render::shaderpack::contracts::terrain::TerrainProgramScope) -> GalResult<Option<ShaderPackShadowPolicy>> {
        self.active.as_ref().map(|source| source.shadow_policy_for_scope(scope)).transpose().map(Option::flatten)
    }

    /// Immutable wetness timing policy derived from the active source
    /// generation. This remains pack data, never Iris smoothing state.
    pub fn active_wetness_policy(&self) -> Option<ShaderPackWetnessPolicy> {
        self.active_wetness_policy
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
mod tests;

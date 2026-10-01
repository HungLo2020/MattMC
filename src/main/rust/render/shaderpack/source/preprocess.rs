use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::source::ShaderPackSource;
#[cfg(test)]
use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::contracts::terrain::TerrainSourceStages;

pub struct PreprocessInput<'a> {
    pub source: &'a ShaderPackSource,
    pub entry: &'a str,
    pub defines: &'a [(&'a str, &'a str)],
}

/// One fully owned, deterministic expansion of a selected shader entry point.
///
/// This is deliberately source data, not a compiler object or a backend shader.
/// It lets the shader-pack runtime identify exactly which pack generation,
/// entry, and semantic configuration a later Rust lowering consumed without
/// retaining Java/Iris objects or reopening pack files during execution.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PreprocessedShaderSource {
    pack_name: String,
    source_generation: u64,
    entry_path: String,
    defines: Vec<(String, String)>,
    resolved_paths: Vec<String>,
    expanded_source: String,
    fingerprint: u64,
    /// Samplers the pack binds to its own images for this world stage
    /// (`texture.gbuffers.*`). They are not Rust-owned render targets, so
    /// world-target coordinate lowering must leave them untouched.
    world_custom_samplers: Vec<String>,
}

/// Complete, owned normal-terrain source expansion. This is a diagnostic and
/// lowering prerequisite only; owning both artifacts does not compile a
/// program, allocate backend resources, or select a render route.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PreprocessedTerrainSources {
    pub vertex: PreprocessedShaderSource,
    pub fragment: PreprocessedShaderSource,
}

/// Engine semantic defines required by Distant Horizons' documented CPU
/// material stream. These values match the copied `WorldLodMaterialCategory`
/// contract, not an Iris program or an OpenGL binding.
const DISTANT_HORIZONS_STANDARD_DEFINES: [(&str, &str); 16] = [
    ("DH_BLOCK_UNKNOWN", "0"),
    ("DH_BLOCK_LEAVES", "1"),
    ("DH_BLOCK_STONE", "2"),
    ("DH_BLOCK_WOOD", "3"),
    ("DH_BLOCK_METAL", "4"),
    ("DH_BLOCK_DIRT", "5"),
    ("DH_BLOCK_LAVA", "6"),
    ("DH_BLOCK_DEEPSLATE", "7"),
    ("DH_BLOCK_SNOW", "8"),
    ("DH_BLOCK_SAND", "9"),
    ("DH_BLOCK_TERRACOTTA", "10"),
    ("DH_BLOCK_NETHER_STONE", "11"),
    ("DH_BLOCK_WATER", "12"),
    ("DH_BLOCK_GRASS", "13"),
    ("DH_BLOCK_AIR", "14"),
    ("DH_BLOCK_ILLUMINATED", "15"),
];

/// Fullscreen consumers following a Distant Horizons source writer must be
/// expanded in the same pack mode as that writer. In particular, the pack's
/// deferred/composite stages use this semantic mode to distinguish pixels
/// backed by `dhDepthTex` from the normal Minecraft sky path.
const DISTANT_HORIZONS_FULLSCREEN_DEFINES: [(&str, &str); 1] = [("DISTANT_HORIZONS", "1")];

/// Bounded provenance retained after source expansion. Runtime candidate
/// discovery stores this summary rather than expanded GLSL, keeping source
/// validation auditable without duplicating large pack contents in memory.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct PreprocessedTerrainSourceSummary {
    pub source_generation: u64,
    pub vertex_entry: String,
    pub fragment_entry: String,
    pub vertex_fingerprint: u64,
    pub fragment_fingerprint: u64,
    pub vertex_dependencies: Vec<String>,
    pub fragment_dependencies: Vec<String>,
}

impl PreprocessedTerrainSources {
    pub fn summary(&self) -> PreprocessedTerrainSourceSummary {
        PreprocessedTerrainSourceSummary {
            source_generation: self.vertex.source_generation(),
            vertex_entry: self.vertex.entry_path().to_string(),
            fragment_entry: self.fragment.entry_path().to_string(),
            vertex_fingerprint: self.vertex.fingerprint(),
            fragment_fingerprint: self.fragment.fingerprint(),
            vertex_dependencies: self.vertex.resolved_paths().to_vec(),
            fragment_dependencies: self.fragment.resolved_paths().to_vec(),
        }
    }
}

impl PreprocessedShaderSource {
    pub const MAX_EXPANDED_BYTES: usize = ShaderPackSource::MAX_TOTAL_BYTES;

    pub fn pack_name(&self) -> &str {
        &self.pack_name
    }

    pub fn source_generation(&self) -> u64 {
        self.source_generation
    }

    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    /// Canonically ordered semantic configuration. These are source-level
    /// options, never driver flags, native handles, or backend state.
    pub fn defines(&self) -> &[(String, String)] {
        &self.defines
    }

    /// Complete, canonical source dependency set reached while expanding the
    /// entry. A missing dependency is rejected before this artifact exists.
    pub fn resolved_paths(&self) -> &[String] {
        &self.resolved_paths
    }

    pub fn expanded_source(&self) -> &str {
        &self.expanded_source
    }

    /// Stable FNV-1a identity over the complete owned source artifact.
    /// It is a cache/diagnostic key, not a security digest.
    pub fn fingerprint(&self) -> u64 {
        self.fingerprint
    }

    /// Retains the same owned source provenance while applying one bounded,
    /// backend-neutral lowering rewrite. Callers cannot alter pack identity,
    /// generation, entry point, or preprocessor facts through this helper.
    pub(crate) fn rewritten_for_lowering(&self, expanded_source: String) -> GalResult<Self> {
        if expanded_source.len() > Self::MAX_EXPANDED_BYTES {
            return Err(GalError::invalid_argument(format!(
                "rewritten shader source exceeds {} bytes",
                Self::MAX_EXPANDED_BYTES
            )));
        }
        let resolved_paths = self.resolved_paths.iter().cloned().collect::<BTreeSet<_>>();
        Ok(Self {
            pack_name: self.pack_name.clone(),
            source_generation: self.source_generation,
            entry_path: self.entry_path.clone(),
            defines: self.defines.clone(),
            resolved_paths: self.resolved_paths.clone(),
            fingerprint: source_fingerprint(
                &self.pack_name,
                self.source_generation,
                &self.entry_path,
                &self.defines,
                &resolved_paths,
                &expanded_source,
            ),
            expanded_source,
            world_custom_samplers: self.world_custom_samplers.clone(),
        })
    }

    pub(crate) fn world_custom_samplers(&self) -> &[String] {
        &self.world_custom_samplers
    }
}

/// Expands one shader source generation without any Iris, Java renderer, or
/// backend state. Includes and conditional branches are resolved against the
/// supplied semantic define set; unsupported expressions fail explicitly.
pub fn preprocess(input: PreprocessInput<'_>) -> GalResult<String> {
    Ok(preprocess_artifact(input)?.expanded_source)
}

/// Produces the owned source artifact consumed by a future Rust shader source
/// lowering. Creating this artifact does not admit the program for rendering:
/// semantic source lowering and backend compilation remain separate gates.
pub fn preprocess_artifact(input: PreprocessInput<'_>) -> GalResult<PreprocessedShaderSource> {
    preprocess_artifact_with_protected_defines(input, &BTreeSet::new())
}

fn preprocess_artifact_with_protected_defines(
    input: PreprocessInput<'_>,
    protected_defines: &BTreeSet<String>,
) -> GalResult<PreprocessedShaderSource> {
    let mut defines = BTreeMap::new();
    let mut resolved_paths = BTreeSet::new();
    let mut out = String::new();
    out.push_str(&format!("// shader-pack: {}\n", input.source.name()));
    out.push_str(&format!("// generation: {}\n", input.source.generation()));
    let mut external_defines = String::new();
    for (key, value) in input.defines {
        validate_define(key, value)?;
        if defines
            .insert((*key).to_string(), (*value).to_string())
            .is_some()
        {
            return Err(GalError::invalid_argument("duplicate shader define key"));
        }
        external_defines.push_str("#define ");
        external_defines.push_str(key);
        external_defines.push(' ');
        external_defines.push_str(value);
        external_defines.push('\n');
    }
    let entry = normalize_path(input.entry)?;
    let entry_source = input
        .source
        .get(&entry)
        .ok_or_else(|| GalError::invalid_argument(format!("missing shader source {entry}")))?;
    let inject_after_version = root_declares_version(entry_source);
    if !inject_after_version {
        out.push_str(&external_defines);
    }
    // A selected option owns its initial definition. The pack may still
    // explicitly `#undef` it inside a scoped branch (for example a feature
    // unavailable in the Nether); after that directive, ordinary pack
    // definitions regain ownership of the name.
    let mut active_protected_defines = protected_defines.clone();
    expand_file(
        input.source,
        &entry,
        &mut BTreeSet::new(),
        &mut resolved_paths,
        &mut defines,
        &mut active_protected_defines,
        inject_after_version.then_some(external_defines.as_str()),
        &mut out,
    )?;
    if out.len() > PreprocessedShaderSource::MAX_EXPANDED_BYTES {
        return Err(GalError::invalid_argument(format!(
            "expanded shader source exceeds {} bytes",
            PreprocessedShaderSource::MAX_EXPANDED_BYTES
        )));
    }
    let defines = defines.into_iter().collect::<Vec<_>>();
    let fingerprint = source_fingerprint(
        input.source.name(),
        input.source.generation(),
        &entry,
        &defines,
        &resolved_paths,
        &out,
    );
    let world_custom_samplers = world_custom_samplers_for_entry(input.source, &entry);
    Ok(PreprocessedShaderSource {
        pack_name: input.source.name().to_string(),
        source_generation: input.source.generation(),
        entry_path: entry,
        defines,
        resolved_paths: resolved_paths.into_iter().collect(),
        expanded_source: out,
        fingerprint,
        world_custom_samplers,
    })
}

fn world_custom_samplers_for_entry(source: &ShaderPackSource, entry: &str) -> Vec<String> {
    let file = entry.rsplit('/').next().unwrap_or(entry);
    let world_stage = file.starts_with("gbuffers_")
        || file.starts_with("dh_")
        || entry.starts_with("program/gbuffers_")
        || entry.starts_with("program/dh_");
    if !world_stage {
        return Vec::new();
    }
    crate::render::shaderpack::source::assets::TerrainShaderPackAssetBindings::from_source(source)
        .map(|bindings| bindings.samplers().map(|(name, _)| name.to_string()).collect())
        .unwrap_or_default()
}

/// Expands one entry using the source generation's validated runtime option
/// snapshot plus caller-supplied engine semantics. A conflict is rejected: a
/// caller must not silently override the selected pack configuration.
pub fn preprocess_artifact_with_runtime_options(
    source: &ShaderPackSource,
    entry: &str,
    defines: &[(&str, &str)],
) -> GalResult<PreprocessedShaderSource> {
    let option_defines = source.runtime_option_semantic_defines()?;
    reject_runtime_stage_selectors("option", &option_defines)?;
    // The immutable option snapshot is the selected pack configuration. It
    // owns preprocessor values across every source entry, including DH stages
    // that do not transitively include the pack's user-settings file. Typed
    // GLSL `const` options remain separate and are rewritten only after this
    // conditional expansion completes.
    let disabled_option_defines = source.runtime_disabled_option_defines()?;
    let mut merged = option_defines.clone();
    let environment_defines = source.runtime_environment_semantic_defines()?;
    reject_runtime_stage_selectors("environment", &environment_defines)?;
    for (key, value) in environment_defines {
        // Environment defaults yield to the user's saved options.
        if disabled_option_defines.contains(&key) {
            continue;
        }
        merged.entry(key).or_insert(value);
    }
    for (key, value) in defines {
        validate_define(key, value)?;
        if let Some(runtime_value) = merged.get(*key) {
            // Engine semantic constants can also be present in Iris's
            // standard macro snapshot. The same value is an agreement on one
            // source-level contract, not an override; a different value still
            // rejects the source pair before any route can be admitted.
            if runtime_value != value {
                return Err(GalError::invalid_argument(format!(
                    "caller shader define '{key}={value}' conflicts with runtime shader-pack option '{runtime_value}'"
                )));
            }
            continue;
        }
        merged.insert((*key).to_owned(), (*value).to_owned());
    }
    let references = merged
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    // Selected options own their names: the pack's own `#define` of a selected
    // or switched-off option is suppressed, as Iris rewrites that line.
    let mut protected_option_defines = option_defines.into_keys().collect::<BTreeSet<_>>();
    protected_option_defines.extend(disabled_option_defines);
    let artifact = preprocess_artifact_with_protected_defines(
        PreprocessInput {
            source,
            entry,
            defines: &references,
        },
        &protected_option_defines,
    )?;
    let constants = source.runtime_constant_values()?;
    if constants.is_empty() {
        return Ok(artifact);
    }
    artifact.rewritten_for_lowering(apply_runtime_constant_values(
        artifact.expanded_source(),
        &constants,
    )?)
}

/// Applies an Iris-resolved `const` option only to its own emitted GLSL
/// declaration. This happens after conditional expansion, so an option that
/// belongs to an inactive branch has no effect and cannot manufacture a macro
/// collision in another translation unit.
fn apply_runtime_constant_values(
    source: &str,
    constants: &BTreeMap<String, String>,
) -> GalResult<String> {
    let mut rewritten = String::with_capacity(source.len());
    for line in source.split_inclusive('\n') {
        let line_without_newline = line.strip_suffix('\n').unwrap_or(line);
        let newline = line.ends_with('\n');
        let trimmed = line_without_newline.trim_start();
        let replacement = if let Some(const_declaration) = trimmed.strip_prefix("const ") {
            let Some((left, right)) = const_declaration.split_once('=') else {
                rewritten.push_str(line);
                continue;
            };
            let Some((_, suffix)) = right.split_once(';') else {
                rewritten.push_str(line);
                continue;
            };
            let Some(name) = left.split_whitespace().last() else {
                rewritten.push_str(line);
                continue;
            };
            constants.get(name).map(|value| {
                let indent = &line_without_newline[..line_without_newline.len() - trimmed.len()];
                format!("{indent}const {left}= {value};{suffix}")
            })
        } else {
            None
        };
        if let Some(replacement) = replacement {
            rewritten.push_str(&replacement);
            if newline {
                rewritten.push('\n');
            }
        } else {
            rewritten.push_str(line);
        }
    }
    Ok(rewritten)
}

/// A selected shader stage is determined by its source entry or the explicit
/// paired-stage request. A copied runtime option/environment snapshot must not
/// select a stage globally: doing so could expand both guarded shader bodies
/// into one owned artifact. This is a source-transport invariant, not a
/// backend compiler or Iris-state rule.
fn reject_runtime_stage_selectors(
    snapshot_kind: &str,
    defines: &BTreeMap<String, String>,
) -> GalResult<()> {
    const STAGE_SELECTORS: [&str; 4] = [
        "VERTEX_SHADER",
        "FRAGMENT_SHADER",
        "GEOMETRY_SHADER",
        "COMPUTE_SHADER",
    ];
    for selector in STAGE_SELECTORS {
        if defines.contains_key(selector) {
            return Err(GalError::invalid_argument(format!(
                "runtime {snapshot_kind} snapshot must not define shader stage selector '{selector}'"
            )));
        }
    }
    Ok(())
}

/// Expands both semantically paired normal-terrain stages from one immutable
/// source generation. Each stage receives the same runtime option snapshot
/// plus its explicit stage-selection defines.
pub fn preprocess_terrain_sources(
    source: &ShaderPackSource,
    stages: &TerrainSourceStages,
) -> GalResult<PreprocessedTerrainSources> {
    preprocess_source_stage_pair(source, stages)
}

/// Expands one explicitly paired shader stage from an immutable source
/// generation. Despite the historical `TerrainSourceStages` name, this is
/// deliberately generic: deferred/composite stages use the same paired source
/// identity but must not be forced through terrain-mesh lowering merely to
/// establish owned source provenance.
///
/// The returned artifacts still carry only source text and semantic defines.
/// They create no program, target, descriptor, or route decision.
pub fn preprocess_source_stage_pair(
    source: &ShaderPackSource,
    stages: &TerrainSourceStages,
) -> GalResult<PreprocessedTerrainSources> {
    let preprocess_stage = |path: &str, defines: &std::collections::BTreeMap<String, String>| {
        let references = defines
            .iter()
            .map(|(key, value)| (key.as_str(), value.as_str()))
            .collect::<Vec<_>>();
        preprocess_artifact_with_runtime_options(source, path, &references)
    };
    Ok(PreprocessedTerrainSources {
        vertex: preprocess_stage(&stages.vertex.path, &stages.vertex.defines)?,
        fragment: preprocess_stage(&stages.fragment.path, &stages.fragment.defines)?,
    })
}

/// Expands the paired DH source stages with the fixed semantic material
/// constants supplied by the copied DH CPU stream. This is an owned
/// preprocessor contract: it never reads Iris's macro table or any live GL
/// state. A pack attempting to redefine one of these semantic identities is
/// rejected rather than silently changing the vertex material meaning.
pub fn preprocess_distant_horizons_sources(
    source: &ShaderPackSource,
    stages: &TerrainSourceStages,
) -> GalResult<PreprocessedTerrainSources> {
    let preprocess_stage =
        |path: &str, stage_defines: &std::collections::BTreeMap<String, String>| {
            let mut defines = DISTANT_HORIZONS_STANDARD_DEFINES
                .iter()
                .map(|(key, value)| ((*key).to_string(), (*value).to_string()))
                .collect::<BTreeMap<_, _>>();
            for (key, value) in stage_defines {
                if defines.insert(key.clone(), value.clone()).is_some() {
                    return Err(GalError::invalid_argument(format!(
                        "Distant Horizons source stage redefines engine semantic '{key}'"
                    )));
                }
            }
            let references = defines
                .iter()
                .map(|(key, value)| (key.as_str(), value.as_str()))
                .collect::<Vec<_>>();
            preprocess_artifact_with_runtime_options(source, path, &references)
        };
    Ok(PreprocessedTerrainSources {
        vertex: preprocess_stage(&stages.vertex.path, &stages.vertex.defines)?,
        fragment: preprocess_stage(&stages.fragment.path, &stages.fragment.defines)?,
    })
}

/// Expands one post-terrain source stage in the explicit Distant Horizons
/// mode. This is source configuration, not an Iris program flag or backend
/// state: the selected Rust frame owns both the DH writer and the consumers
/// that read its named depth/color resources.
pub fn preprocess_distant_horizons_fullscreen_stage_pair(
    source: &ShaderPackSource,
    stages: &TerrainSourceStages,
) -> GalResult<PreprocessedTerrainSources> {
    let preprocess_stage =
        |path: &str, stage_defines: &std::collections::BTreeMap<String, String>| {
            let mut defines = DISTANT_HORIZONS_FULLSCREEN_DEFINES
                .iter()
                .map(|(key, value)| ((*key).to_string(), (*value).to_string()))
                .collect::<BTreeMap<_, _>>();
            for (key, value) in stage_defines {
                if defines.insert(key.clone(), value.clone()).is_some() {
                    return Err(GalError::invalid_argument(format!(
                        "Distant Horizons fullscreen stage redefines engine semantic '{key}'"
                    )));
                }
            }
            let references = defines
                .iter()
                .map(|(key, value)| (key.as_str(), value.as_str()))
                .collect::<Vec<_>>();
            preprocess_artifact_with_runtime_options(source, path, &references)
        };
    Ok(PreprocessedTerrainSources {
        vertex: preprocess_stage(&stages.vertex.path, &stages.vertex.defines)?,
        fragment: preprocess_stage(&stages.fragment.path, &stages.fragment.defines)?,
    })
}

fn source_fingerprint(
    pack_name: &str,
    generation: u64,
    entry: &str,
    defines: &[(String, String)],
    resolved_paths: &BTreeSet<String>,
    source: &str,
) -> u64 {
    const FNV_OFFSET: u64 = 0xcbf29ce484222325;
    const FNV_PRIME: u64 = 0x100000001b3;
    let mut hash = FNV_OFFSET;
    let mut write = |value: &[u8]| {
        for byte in value {
            hash ^= u64::from(*byte);
            hash = hash.wrapping_mul(FNV_PRIME);
        }
        hash ^= 0xff;
        hash = hash.wrapping_mul(FNV_PRIME);
    };
    write(pack_name.as_bytes());
    write(&generation.to_le_bytes());
    write(entry.as_bytes());
    for (key, value) in defines {
        write(key.as_bytes());
        write(value.as_bytes());
    }
    for path in resolved_paths {
        write(path.as_bytes());
    }
    write(source.as_bytes());
    hash
}

#[derive(Clone, Copy)]
struct ConditionalFrame {
    parent_active: bool,
    active: bool,
    any_branch_matched: bool,
    saw_else: bool,
}

fn expand_file(
    source: &ShaderPackSource,
    path: &str,
    include_stack: &mut BTreeSet<String>,
    resolved_paths: &mut BTreeSet<String>,
    defines: &mut BTreeMap<String, String>,
    active_protected_defines: &mut BTreeSet<String>,
    external_defines_after_version: Option<&str>,
    out: &mut String,
) -> GalResult<()> {
    let path = normalize_path(path)?;
    if !include_stack.insert(path.clone()) {
        return Err(GalError::invalid_argument(format!(
            "cyclic shader include {path}"
        )));
    }
    resolved_paths.insert(path.clone());
    let contents = source
        .get(&path)
        .ok_or_else(|| GalError::invalid_argument(format!("missing shader source {path}")))?;
    let result = expand_contents(
        source,
        &path,
        contents,
        include_stack,
        resolved_paths,
        defines,
        active_protected_defines,
        external_defines_after_version,
        out,
    );
    include_stack.remove(&path);
    result
}

fn expand_contents(
    source: &ShaderPackSource,
    path: &str,
    contents: &str,
    include_stack: &mut BTreeSet<String>,
    resolved_paths: &mut BTreeSet<String>,
    defines: &mut BTreeMap<String, String>,
    active_protected_defines: &mut BTreeSet<String>,
    external_defines_after_version: Option<&str>,
    out: &mut String,
) -> GalResult<()> {
    let mut frames = Vec::<ConditionalFrame>::new();
    let mut active = true;
    let mut injected_external_defines = false;
    for raw in contents.lines() {
        if !injected_external_defines
            && external_defines_after_version.is_some()
            && raw.trim_start().starts_with("#version")
        {
            out.push_str(raw);
            out.push('\n');
            out.push_str(external_defines_after_version.unwrap());
            injected_external_defines = true;
            continue;
        }
        let directive = raw.trim().strip_prefix('#').map(str::trim);
        if let Some(directive) = directive {
            if let Some(expression) = directive.strip_prefix("if ") {
                // A disabled parent suppresses the entire nested conditional.
                // Do not reject expressions in that unreachable branch: selected
                // packs often leave optional feature expressions there.
                let condition = if active {
                    evaluate_condition(expression, defines)?
                } else {
                    false
                };
                let frame = ConditionalFrame {
                    parent_active: active,
                    active: active && condition,
                    any_branch_matched: condition,
                    saw_else: false,
                };
                active = frame.active;
                frames.push(frame);
                continue;
            }
            if let Some(name) = directive.strip_prefix("ifdef ") {
                let condition = defines.contains_key(strip_line_comment(name).trim());
                let frame = ConditionalFrame {
                    parent_active: active,
                    active: active && condition,
                    any_branch_matched: condition,
                    saw_else: false,
                };
                active = frame.active;
                frames.push(frame);
                continue;
            }
            if let Some(name) = directive.strip_prefix("ifndef ") {
                let condition = !defines.contains_key(strip_line_comment(name).trim());
                let frame = ConditionalFrame {
                    parent_active: active,
                    active: active && condition,
                    any_branch_matched: condition,
                    saw_else: false,
                };
                active = frame.active;
                frames.push(frame);
                continue;
            }
            if let Some(expression) = directive.strip_prefix("elif ") {
                let frame = frames.last_mut().ok_or_else(|| {
                    GalError::invalid_argument(format!("shader #elif without #if in {path}"))
                })?;
                if frame.saw_else {
                    return Err(GalError::invalid_argument(format!(
                        "shader #elif after #else in {path}"
                    )));
                }
                let condition = if frame.parent_active && !frame.any_branch_matched {
                    evaluate_condition(expression, defines)?
                } else {
                    false
                };
                frame.active = frame.parent_active && !frame.any_branch_matched && condition;
                frame.any_branch_matched |= condition;
                active = frame.active;
                continue;
            }
            if directive == "else" || directive.starts_with("else //") {
                let frame = frames.last_mut().ok_or_else(|| {
                    GalError::invalid_argument(format!("shader #else without #if in {path}"))
                })?;
                if frame.saw_else {
                    return Err(GalError::invalid_argument(format!(
                        "shader has repeated #else in {path}"
                    )));
                }
                frame.active = frame.parent_active && !frame.any_branch_matched;
                frame.any_branch_matched = true;
                frame.saw_else = true;
                active = frame.active;
                continue;
            }
            if directive == "endif" || directive.starts_with("endif //") {
                let frame = frames.pop().ok_or_else(|| {
                    GalError::invalid_argument(format!("shader #endif without #if in {path}"))
                })?;
                active = frame.parent_active;
                continue;
            }
            if let Some(include) = parse_include(raw) {
                if active {
                    let include_path = resolve_include(path, include)?;
                    expand_file(
                        source,
                        &include_path,
                        include_stack,
                        resolved_paths,
                        defines,
                        active_protected_defines,
                        None,
                        out,
                    )?;
                }
                continue;
            }
            if let Some(rest) = directive.strip_prefix("define ") {
                if active {
                    let (key, value) = parse_define(rest)?;
                    if !active_protected_defines.contains(&key) {
                        defines.insert(key, value);
                        out.push_str(raw);
                        out.push('\n');
                    }
                }
                continue;
            }
            if let Some(name) = directive.strip_prefix("undef ") {
                if active {
                    let name = strip_line_comment(name).trim();
                    defines.remove(name);
                    active_protected_defines.remove(name);
                    out.push_str(raw);
                    out.push('\n');
                }
                continue;
            }
        }
        if active {
            out.push_str(raw);
            out.push('\n');
        }
    }
    if !frames.is_empty() {
        return Err(GalError::invalid_argument(format!(
            "unterminated shader conditional in {path}"
        )));
    }
    if external_defines_after_version.is_some() && !injected_external_defines {
        return Err(GalError::invalid_argument(format!(
            "shader root {path} declared #version but no version directive was emitted"
        )));
    }
    Ok(())
}

fn root_declares_version(source: &str) -> bool {
    source
        .lines()
        .any(|line| line.trim_start().starts_with("#version"))
}

fn parse_include(line: &str) -> Option<&str> {
    let trimmed = line.trim();
    let rest = trimmed.strip_prefix("#include")?.trim();
    rest.strip_prefix('"')
        .and_then(|include| include.strip_suffix('"'))
        .or_else(|| {
            rest.strip_prefix('<')
                .and_then(|include| include.strip_suffix('>'))
        })
}

fn parse_define(rest: &str) -> GalResult<(String, String)> {
    let definition = strip_line_comment(rest).trim();
    let identifier_end = definition
        .find(|character: char| character.is_whitespace() || character == '(')
        .unwrap_or(definition.len());
    let key = definition[..identifier_end].trim();
    if key.is_empty() {
        return Err(GalError::invalid_argument("shader #define has no key"));
    }
    let remainder = &definition[identifier_end..];
    let function_like = remainder.starts_with('(');
    if function_like && remainder.find(')').is_none() {
        return Err(GalError::invalid_argument(
            "shader function-like #define has unclosed parameters",
        ));
    }
    let value = if function_like {
        // Function-like macro replacement is left untouched in the expanded
        // GLSL. The preprocessor only needs the name for `defined(NAME)`;
        // using it as a numeric expression remains an explicit unsupported
        // source feature instead of guessing a replacement value.
        "1"
    } else {
        remainder.trim().split_whitespace().next().unwrap_or("1")
    };
    validate_define(key, value)?;
    Ok((key.to_string(), value.to_string()))
}

fn normalize_path(path: &str) -> GalResult<String> {
    if path.contains('\0') {
        return Err(GalError::invalid_argument(
            "shader source path contains NUL",
        ));
    }
    let path = path.trim_start_matches('/').replace('\\', "/");
    let mut components = Vec::new();
    for component in path.split('/') {
        match component {
            "" | "." => {}
            ".." => {
                if components.pop().is_none() {
                    return Err(GalError::invalid_argument(
                        "shader source path escapes its pack root",
                    ));
                }
            }
            value => components.push(value),
        }
    }
    if components.is_empty() {
        return Err(GalError::invalid_argument("shader source path is empty"));
    }
    Ok(components.join("/"))
}

fn resolve_include(parent: &str, include: &str) -> GalResult<String> {
    if include.starts_with('/') {
        return normalize_path(include);
    }
    let parent = Path::new(parent);
    normalize_path(
        &parent
            .parent()
            .unwrap_or_else(|| Path::new(""))
            .join(include)
            .to_string_lossy()
            .replace('\\', "/"),
    )
}

fn evaluate_condition(expression: &str, defines: &BTreeMap<String, String>) -> GalResult<bool> {
    let expression = trim_outer_parens(strip_line_comment(expression).trim());
    if let Some((left, right)) = split_top(expression, "||") {
        return Ok(evaluate_condition(left, defines)? || evaluate_condition(right, defines)?);
    }
    if let Some((left, right)) = split_top(expression, "&&") {
        return Ok(evaluate_condition(left, defines)? && evaluate_condition(right, defines)?);
    }
    if let Some(rest) = expression.strip_prefix('!') {
        return Ok(!evaluate_condition(rest, defines)?);
    }
    for operator in [">=", "<=", "==", "!=", ">", "<"] {
        if let Some((left, right)) = split_top(expression, operator) {
            let left = numeric_define_value(left, defines)?;
            let right = numeric_define_value(right, defines)?;
            return Ok(match operator {
                ">=" => left >= right,
                "<=" => left <= right,
                "==" => left == right,
                "!=" => left != right,
                ">" => left > right,
                "<" => left < right,
                _ => unreachable!(),
            });
        }
    }
    let defined = expression
        .strip_prefix("defined(")
        .and_then(|value| value.strip_suffix(')'))
        .or_else(|| expression.strip_prefix("defined "))
        .map(str::trim);
    if let Some(name) = defined {
        return Ok(defines.contains_key(name));
    }
    if expression.parse::<f64>().is_ok() {
        return Ok(numeric_define_value(expression, defines)? != 0.0);
    }
    if expression
        .bytes()
        .all(|byte| byte == b'_' || byte.is_ascii_alphanumeric())
    {
        return Ok(defines
            .get(expression)
            .map(|value| value != "0")
            .unwrap_or(false));
    }
    Err(GalError::unsupported_feature(format!(
        "unsupported shader preprocessor condition {expression}"
    )))
}

fn numeric_define_value(value: &str, defines: &BTreeMap<String, String>) -> GalResult<f64> {
    numeric_define_value_inner(value, defines, &mut BTreeSet::new())
}

fn numeric_define_value_inner(
    value: &str,
    defines: &BTreeMap<String, String>,
    visited: &mut BTreeSet<String>,
) -> GalResult<f64> {
    let value = trim_outer_parens(value.trim());
    if let Ok(value) = value.parse::<f64>() {
        return Ok(value);
    }
    if !value
        .bytes()
        .all(|byte| byte == b'_' || byte.is_ascii_alphanumeric())
    {
        return Err(GalError::unsupported_feature(format!(
            "unsupported numeric shader preprocessor value {value}"
        )));
    }
    if !visited.insert(value.to_string()) {
        return Err(GalError::invalid_argument(format!(
            "cyclic numeric shader define {value}"
        )));
    }
    let resolved = defines.get(value).ok_or_else(|| {
        GalError::unsupported_feature(format!(
            "undefined numeric shader preprocessor value {value}"
        ))
    })?;
    numeric_define_value_inner(resolved, defines, visited)
}

fn strip_line_comment(value: &str) -> &str {
    value.split_once("//").map_or(value, |(value, _)| value)
}

fn trim_outer_parens(mut value: &str) -> &str {
    while value.starts_with('(') && value.ends_with(')') && outer_parens_match(value) {
        value = value[1..value.len() - 1].trim();
    }
    value
}

fn outer_parens_match(value: &str) -> bool {
    let mut depth = 0i32;
    for (index, character) in value.char_indices() {
        match character {
            '(' => depth += 1,
            ')' => {
                depth -= 1;
                if depth == 0 && index + character.len_utf8() != value.len() {
                    return false;
                }
            }
            _ => {}
        }
    }
    depth == 0
}

fn split_top<'a>(value: &'a str, operator: &str) -> Option<(&'a str, &'a str)> {
    let mut depth = 0_i32;
    for (index, character) in value.char_indices() {
        match character {
            '(' => depth += 1,
            ')' => depth -= 1,
            _ if depth == 0 && value[index..].starts_with(operator) => {
                return Some((&value[..index], &value[index + operator.len()..]));
            }
            _ => {}
        }
    }
    None
}

fn validate_define(key: &str, value: &str) -> GalResult<()> {
    if key.is_empty()
        || !key
            .bytes()
            .all(|byte| byte == b'_' || byte.is_ascii_alphanumeric())
    {
        return Err(GalError::invalid_argument("invalid shader define key"));
    }
    if value.contains('\n') || value.contains('\r') {
        return Err(GalError::invalid_argument("invalid shader define value"));
    }
    Ok(())
}

/// Test-only owned fixture for backend compilation conformance. Keeping the
/// source collection here lets the backend prove it can compile the exact
/// lowered source without making shader-pack preparation depend on a Vulkan
/// compiler implementation.
#[cfg(test)]
pub(crate) fn complete_bundled_pack_source_for_test() -> ShaderPackSource {
    use std::fs;
    use std::path::{Path, PathBuf};

    fn collect_pack_files(root: &Path, directory: &Path, files: &mut Vec<ShaderSourceFile>) {
        let mut entries = fs::read_dir(directory)
            .unwrap()
            .collect::<Result<Vec<_>, _>>()
            .unwrap();
        entries.sort_by_key(|entry| entry.file_name());
        for entry in entries {
            let path = entry.path();
            if path.is_dir() {
                collect_pack_files(root, &path, files);
                continue;
            }
            let relative = path
                .strip_prefix(root)
                .unwrap()
                .to_string_lossy()
                .replace('\\', "/");
            if !is_shader_source_path(&relative) {
                continue;
            }
            files.push(ShaderSourceFile::new(
                relative,
                fs::read_to_string(path).unwrap(),
            ));
        }
    }

    fn is_shader_source_path(path: &str) -> bool {
        [".vsh", ".fsh", ".gsh", ".csh", ".glsl", ".properties"]
            .iter()
            .any(|suffix| path.ends_with(suffix))
    }

    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .join("../resources/shaders/ComplementaryHungLoIfied/shaders");
    let mut files = Vec::new();
    collect_pack_files(&root, &root, &mut files);
    files.push(ShaderSourceFile::new(
        crate::render::shaderpack::source::RUNTIME_ENVIRONMENT_PATH,
        concat!(
            "IRIS_VERSION=12000\n",
            "MC_VERSION=12105\n",
            "IS_IRIS=1\n",
            "IRIS_FEATURE_CUSTOM_IMAGES=1\n",
            "IRIS_FEATURE_SSBO=1\n",
            "MC_OS_LINUX=1\n",
            "MC_MIPMAP_LEVEL=4\n",
            "MC_RENDER_STAGE_SUN=4\n",
            "MC_RENDER_STAGE_MOON=5\n",
            "MC_RENDER_STAGE_TERRAIN_SOLID=8\n",
            "MC_RENDER_STAGE_TERRAIN_CUTOUT_MIPPED=9\n",
            "MC_RENDER_STAGE_TERRAIN_CUTOUT=10\n",
            "MC_RENDER_STAGE_TERRAIN_TRANSLUCENT=15\n",
            "MC_RENDER_STAGE_RAIN_SNOW=19\n",
        ),
    ));
    files.sort_by(|left, right| left.path.cmp(&right.path));
    ShaderPackSource::new("ComplementaryHungLoIfied-complete-test", 91, files).unwrap()
}

#[cfg(test)]
mod tests;

//! Semantic discovery of a selected shader pack's post-terrain fullscreen chain.
//!
//! The contract retains ordered source identities only. It does not compile a
//! program, allocate targets, select a route, or encode backend state.

use std::collections::BTreeMap;

use super::preprocess::preprocess_artifact_with_runtime_options;
use super::source::ShaderPackSource;
use super::terrain_contract::{terrain_source_stages, TerrainProgramScope, TerrainSourceStages};
use crate::render::vulkanic::error::{GalError, GalResult};

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum FullscreenSourceStageKind {
    /// A source-defined world-sky initializer. Unlike deferred/composite
    /// stages, it must run after the named-color bootstrap and before opaque
    /// terrain writes so terrain can load the pack's initialized primary
    /// color instead of compositing against a generic clear.
    Sky,
    /// Vanilla sun/moon/custom-sky textured geometry. This is distinct from
    /// the sky-disc initializer because it needs an owned quad and a copied
    /// semantic texture asset, not a fullscreen or disc approximation.
    SkyTextured,
    Deferred {
        ordinal: u32,
    },
    Composite {
        ordinal: u32,
    },
    Final,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FullscreenSourceStage {
    pub stage_path: String,
    pub source_stages: TerrainSourceStages,
    pub kind: FullscreenSourceStageKind,
}

/// Discovers the optional source-defined sky initializer for one world
/// scope. A pack that declares it must lower it successfully before the
/// Rust-owned source route can use its terrain/Distant-Horizons composition;
/// a pack that does not declare one retains the ordinary semantic background
/// initialization path.
pub fn derive_sky_source_stage(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<Option<FullscreenSourceStage>> {
    let scope_path = match scope {
        TerrainProgramScope::Default => "gbuffers_skybasic.fsh".to_string(),
        TerrainProgramScope::Overworld => "world0/gbuffers_skybasic.fsh".to_string(),
        TerrainProgramScope::Nether => "world-1/gbuffers_skybasic.fsh".to_string(),
        TerrainProgramScope::End => "world1/gbuffers_skybasic.fsh".to_string(),
    };
    if source.get(&scope_path).is_none() {
        return Ok(None);
    }
    let source_stages = terrain_source_stages(&scope_path)?;
    if source.get(&source_stages.vertex.path).is_none() {
        return Err(GalError::invalid_argument(format!(
            "sky source stage {scope_path} has no paired vertex source {}",
            source_stages.vertex.path,
        )));
    }
    Ok(Some(FullscreenSourceStage {
        stage_path: scope_path,
        source_stages,
        kind: FullscreenSourceStageKind::Sky,
    }))
}

/// Discovers the optional vanilla celestial source stage for one world scope.
/// It remains a distinct semantic writer from `gbuffers_skybasic`: packs use
/// it for the sun, moon, and compatible custom-sky textures after the sky
/// initializer has prepared the named scene color.
pub fn derive_sky_textured_source_stage(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<Option<FullscreenSourceStage>> {
    let scope_path = match scope {
        TerrainProgramScope::Default => "gbuffers_skytextured.fsh".to_string(),
        TerrainProgramScope::Overworld => "world0/gbuffers_skytextured.fsh".to_string(),
        TerrainProgramScope::Nether => "world-1/gbuffers_skytextured.fsh".to_string(),
        TerrainProgramScope::End => "world1/gbuffers_skytextured.fsh".to_string(),
    };
    if source.get(&scope_path).is_none() {
        return Ok(None);
    }
    let source_stages = terrain_source_stages(&scope_path)?;
    if source.get(&source_stages.vertex.path).is_none() {
        return Err(GalError::invalid_argument(format!(
            "celestial source stage {scope_path} has no paired vertex source {}",
            source_stages.vertex.path,
        )));
    }
    Ok(Some(FullscreenSourceStage {
        stage_path: scope_path,
        source_stages,
        kind: FullscreenSourceStageKind::SkyTextured,
    }))
}

/// Discovers every normal post-terrain fullscreen stage belonging to one
/// selected world scope. The ordering is source semantic ordering, not a GL
/// draw-buffer order or an Iris program handle.
pub fn derive_fullscreen_source_chain(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<Vec<FullscreenSourceStage>> {
    let program_gates = source_program_gates(source, scope)?;
    let mut stages = source
        .files()
        .into_iter()
        .filter_map(|file| {
            let kind = fullscreen_stage_kind(&file.path)?;
            belongs_to_scope(&file.path, scope).then_some((file.path, kind))
        })
        .map(
            |(stage_path, kind)| -> GalResult<Option<FullscreenSourceStage>> {
                let source_stages = terrain_source_stages(&stage_path)?;
                if source.get(&source_stages.vertex.path).is_none() {
                    return Err(GalError::invalid_argument(format!(
                        "fullscreen source stage {stage_path} has no paired vertex source {}",
                        source_stages.vertex.path,
                    )));
                }
                let program_name = stage_path.strip_suffix(".fsh").ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "fullscreen source stage {stage_path} is not a fragment program"
                    ))
                })?;
                if let Some(expression) = program_gates.expressions.get(program_name) {
                    if !evaluate_program_gate(expression, &program_gates.option_macros)? {
                        return Ok(None);
                    }
                }
                Ok(Some(FullscreenSourceStage {
                    stage_path,
                    source_stages,
                    kind,
                }))
            },
        )
        .collect::<GalResult<Vec<_>>>()?
        .into_iter()
        .flatten()
        .collect::<Vec<_>>();
    stages.sort_by_key(|stage| stage.kind);
    if stages.is_empty() {
        return Err(GalError::unsupported_feature(format!(
            "selected shader-pack scope {scope:?} has no post-terrain fullscreen source stages",
        )));
    }
    if !matches!(
        stages.last().map(|stage| stage.kind),
        Some(FullscreenSourceStageKind::Final)
    ) {
        return Err(GalError::unsupported_feature(format!(
            "selected shader-pack scope {scope:?} has no final fullscreen source stage",
        )));
    }
    Ok(stages)
}

/// Reads active `program.<path>.enabled` directives from the selected source
/// properties after source preprocessing. These directives are source policy:
/// they decide whether a declared program participates in the pack graph, but
/// they never name a backend program, framebuffer, or native handle.
struct SourceProgramGates {
    expressions: BTreeMap<String, String>,
    option_macros: BTreeMap<String, String>,
}

fn source_program_gates(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<SourceProgramGates> {
    if source.get("shaders.properties").is_none() {
        return Ok(SourceProgramGates {
            expressions: BTreeMap::new(),
            option_macros: BTreeMap::new(),
        });
    }
    // Shader-pack properties are evaluated by Iris against the resolved pack
    // option set, not as a standalone file. Reuse the selected scope's final
    // source to obtain the active scalar macro values first, then feed those
    // semantic values into the existing source preprocessor. This keeps
    // `#if`-guarded directives (such as optional post effects) aligned with
    // the source program that would otherwise be scheduled.
    let reference_stage = match scope {
        TerrainProgramScope::Default => "final.fsh",
        TerrainProgramScope::Overworld => "world0/final.fsh",
        TerrainProgramScope::Nether => "world-1/final.fsh",
        TerrainProgramScope::End => "world1/final.fsh",
    };
    let mut macros = BTreeMap::new();
    // Complementary and other packs conventionally centralize defaults in a
    // common include. Program-enable directives are evaluated from selected
    // options, so include these defaults even when `final` itself does not
    // reference the common source.
    if source.get("lib/common.glsl").is_some() {
        macros.extend(source_program_macro_values(source, "lib/common.glsl")?);
    }
    macros.extend(source_program_macro_values(source, reference_stage)?);
    let runtime_defines = source.runtime_semantic_defines()?;
    let scalar_defines = macros
        .iter()
        .filter(|(name, value)| {
            !runtime_defines.contains_key(*name) && property_scalar_define(value)
        })
        .map(|(name, value)| (name.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    let properties =
        preprocess_artifact_with_runtime_options(source, "shaders.properties", &scalar_defines)?;
    let mut gates = BTreeMap::new();
    for raw in properties.expanded_source().lines() {
        let line = raw.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let key = key.trim();
        let Some(program) = key
            .strip_prefix("program.")
            .and_then(|key| key.strip_suffix(".enabled"))
        else {
            continue;
        };
        if program.is_empty() {
            return Err(GalError::invalid_argument(
                "shader-pack program enabled directive has an empty program path",
            ));
        }
        let expression = value.trim();
        if expression.is_empty() {
            return Err(GalError::invalid_argument(format!(
                "shader-pack program enabled directive for {program} is empty"
            )));
        }
        if gates
            .insert(program.to_string(), expression.to_string())
            .is_some()
        {
            return Err(GalError::invalid_argument(format!(
                "shader-pack program enabled directive for {program} is declared more than once"
            )));
        }
    }
    Ok(SourceProgramGates {
        expressions: gates,
        option_macros: macros,
    })
}

fn property_scalar_define(value: &str) -> bool {
    let value = value.trim();
    value.is_empty() || matches!(value, "true" | "false") || value.parse::<i64>().is_ok()
}

/// Determines the selected configuration's effective source macros for one
/// stage. The artifact is owned Rust source text; this is not Iris state and
/// does not inspect a linked program. A stage-local scan is deliberate: pack
/// feature defines are commonly introduced by the same shared includes that
/// feed the stage itself.
fn source_program_macro_values(
    source: &ShaderPackSource,
    stage_path: &str,
) -> GalResult<BTreeMap<String, String>> {
    let artifact = preprocess_artifact_with_runtime_options(source, stage_path, &[])?;
    let mut macros = BTreeMap::new();
    for raw in artifact.expanded_source().lines() {
        let line = raw.trim();
        if let Some(rest) = line.strip_prefix("#define ") {
            let definition = rest
                .split_once("//")
                .map_or(rest, |(value, _)| value)
                .trim();
            let identifier_end = definition
                .find(|character: char| character.is_whitespace() || character == '(')
                .unwrap_or(definition.len());
            let name = &definition[..identifier_end];
            if name.is_empty() {
                return Err(GalError::invalid_argument(format!(
                    "shader-pack stage {stage_path} has a malformed #define"
                )));
            }
            // Function-like macros cannot be boolean program gates. Retain
            // their presence only so an attempted use is rejected below.
            let value = definition[identifier_end..].trim();
            macros.insert(name.to_string(), value.to_string());
        } else if let Some(name) = line.strip_prefix("#undef ") {
            macros.remove(
                name.split_once("//")
                    .map_or(name, |(value, _)| value)
                    .trim(),
            );
        }
    }
    Ok(macros)
}

/// Evaluates Iris-compatible boolean program-enable expressions against the
/// selected stage's effective source macros. The grammar is deliberately the
/// same bounded boolean family used by Iris program directives: literals,
/// option names, `!`, `&&`, `||`, and parentheses. Unknown names are false,
/// matching an unset boolean shader option; malformed expressions reject the
/// source graph rather than silently admitting an unselected stage.
fn evaluate_program_gate(expression: &str, macros: &BTreeMap<String, String>) -> GalResult<bool> {
    ProgramGateParser::new(expression, macros).parse()
}

struct ProgramGateParser<'a> {
    bytes: &'a [u8],
    cursor: usize,
    macros: &'a BTreeMap<String, String>,
}

impl<'a> ProgramGateParser<'a> {
    fn new(expression: &'a str, macros: &'a BTreeMap<String, String>) -> Self {
        Self {
            bytes: expression.as_bytes(),
            cursor: 0,
            macros,
        }
    }

    fn parse(mut self) -> GalResult<bool> {
        let value = self.parse_or()?;
        self.skip_whitespace();
        if self.cursor != self.bytes.len() {
            return Err(GalError::invalid_argument(format!(
                "unsupported trailing token in shader-pack program gate '{}'",
                String::from_utf8_lossy(&self.bytes[self.cursor..])
            )));
        }
        Ok(value)
    }

    fn parse_or(&mut self) -> GalResult<bool> {
        let mut value = self.parse_and()?;
        while self.consume(b"||") {
            value |= self.parse_and()?;
        }
        Ok(value)
    }

    fn parse_and(&mut self) -> GalResult<bool> {
        let mut value = self.parse_unary()?;
        while self.consume(b"&&") {
            value &= self.parse_unary()?;
        }
        Ok(value)
    }

    fn parse_unary(&mut self) -> GalResult<bool> {
        if self.consume(b"!") {
            return Ok(!self.parse_unary()?);
        }
        if self.consume(b"(") {
            let value = self.parse_or()?;
            if !self.consume(b")") {
                return Err(GalError::invalid_argument(
                    "shader-pack program gate has an unterminated parenthesis",
                ));
            }
            return Ok(value);
        }
        let token = self.identifier()?;
        Ok(match token {
            "true" | "1" => true,
            "false" | "0" => false,
            _ => self
                .macros
                .get(token)
                .map(|value| macro_gate_value(token, value))
                .transpose()?
                .unwrap_or(false),
        })
    }

    fn identifier(&mut self) -> GalResult<&'a str> {
        self.skip_whitespace();
        let start = self.cursor;
        while self.cursor < self.bytes.len()
            && (self.bytes[self.cursor] == b'_' || self.bytes[self.cursor].is_ascii_alphanumeric())
        {
            self.cursor += 1;
        }
        if start == self.cursor {
            return Err(GalError::invalid_argument(
                "shader-pack program gate expects a boolean option name",
            ));
        }
        std::str::from_utf8(&self.bytes[start..self.cursor])
            .map_err(|_| GalError::invalid_argument("shader-pack program gate is not valid UTF-8"))
    }

    fn consume(&mut self, token: &[u8]) -> bool {
        self.skip_whitespace();
        if self.bytes[self.cursor..].starts_with(token) {
            self.cursor += token.len();
            true
        } else {
            false
        }
    }

    fn skip_whitespace(&mut self) {
        while self.cursor < self.bytes.len() && self.bytes[self.cursor].is_ascii_whitespace() {
            self.cursor += 1;
        }
    }
}

fn macro_gate_value(name: &str, value: &str) -> GalResult<bool> {
    let value = value.trim();
    if value.is_empty() {
        return Ok(true);
    }
    match value {
        "true" | "1" => Ok(true),
        "false" | "0" => Ok(false),
        _ if value.parse::<i64>().is_ok() => Ok(value.parse::<i64>().unwrap() != 0),
        _ => Err(GalError::unsupported_feature(format!(
            "shader-pack program gate {name} resolves to unsupported macro value '{value}'"
        ))),
    }
}

fn fullscreen_stage_kind(path: &str) -> Option<FullscreenSourceStageKind> {
    let name = path.rsplit('/').next()?.strip_suffix(".fsh")?;
    if name == "final" {
        return Some(FullscreenSourceStageKind::Final);
    }
    if let Some(suffix) = name.strip_prefix("deferred") {
        return parse_ordinal(suffix)
            .map(|ordinal| FullscreenSourceStageKind::Deferred { ordinal });
    }
    if let Some(suffix) = name.strip_prefix("composite") {
        return parse_ordinal(suffix)
            .map(|ordinal| FullscreenSourceStageKind::Composite { ordinal });
    }
    None
}

fn parse_ordinal(suffix: &str) -> Option<u32> {
    if suffix.is_empty() {
        Some(0)
    } else if suffix.bytes().all(|byte| byte.is_ascii_digit()) {
        suffix.parse().ok()
    } else {
        None
    }
}

fn belongs_to_scope(path: &str, scope: TerrainProgramScope) -> bool {
    match scope {
        TerrainProgramScope::Default => !path.starts_with("world"),
        TerrainProgramScope::Overworld => path.starts_with("world0/"),
        TerrainProgramScope::Nether => path.starts_with("world-1/"),
        TerrainProgramScope::End => path.starts_with("world1/"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::{
        preprocess::complete_bundled_pack_source_for_test, source::ShaderSourceFile,
    };

    #[test]
    fn bundled_overworld_chain_is_ordered_and_ends_at_final() {
        let chain = derive_fullscreen_source_chain(
            &complete_bundled_pack_source_for_test(),
            TerrainProgramScope::Overworld,
        )
        .unwrap();
        assert_eq!("world0/deferred1.fsh", chain[0].stage_path);
        assert!(matches!(
            chain[0].kind,
            FullscreenSourceStageKind::Deferred { ordinal: 1 }
        ));
        assert!(chain
            .iter()
            .any(|stage| stage.stage_path == "world0/composite.fsh"));
        assert!(chain
            .iter()
            .any(|stage| stage.stage_path == "world0/composite4.fsh"));
        assert!(!chain
            .iter()
            .any(|stage| stage.stage_path == "world0/composite2.fsh"));
        // `composite7` has no active `.enabled` directive for this selected
        // FXAA configuration, so Iris leaves the existing program enabled.
        assert!(chain
            .iter()
            .any(|stage| stage.stage_path == "world0/composite7.fsh"));
        assert_eq!("world0/final.fsh", chain.last().unwrap().stage_path);
        assert!(matches!(
            chain.last().unwrap().kind,
            FullscreenSourceStageKind::Final
        ));
    }

    #[test]
    fn source_program_enable_directives_follow_active_source_macros() {
        let source = ShaderPackSource::new(
            "gated-pack",
            1,
            vec![
                ShaderSourceFile::new(
                    "shaders.properties",
                    concat!(
                        "program.world0/composite.enabled=FEATURE\n",
                        "#if MODE == 1\n",
                        "program.world0/composite2.enabled=DISABLED\n",
                        "#endif\n",
                    ),
                ),
                ShaderSourceFile::new(
                    "world0/final.fsh",
                    "#define FEATURE\n#define DISABLED 0\n#define MODE 1\n",
                ),
                ShaderSourceFile::new("world0/final.vsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite.fsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite.vsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite2.fsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite2.vsh", "void main() {}"),
            ],
        )
        .unwrap();

        let gates = source_program_gates(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(
            Some(&"FEATURE".to_string()),
            gates.expressions.get("world0/composite")
        );
        assert_eq!(
            Some(&"DISABLED".to_string()),
            gates.expressions.get("world0/composite2")
        );
        let chain =
            derive_fullscreen_source_chain(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(
            chain
                .iter()
                .map(|stage| stage.stage_path.as_str())
                .collect::<Vec<_>>(),
            vec!["world0/composite.fsh", "world0/final.fsh"]
        );
    }

    #[test]
    fn malformed_program_enable_directive_is_rejected() {
        let source = ShaderPackSource::new(
            "bad-gate-pack",
            1,
            vec![
                ShaderSourceFile::new(
                    "shaders.properties",
                    "program.world0/composite.enabled=A+B\n",
                ),
                ShaderSourceFile::new("world0/final.fsh", "void main() {}"),
                ShaderSourceFile::new("world0/final.vsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite.fsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite.vsh", "void main() {}"),
            ],
        )
        .unwrap();

        assert!(derive_fullscreen_source_chain(&source, TerrainProgramScope::Overworld).is_err());
    }

    #[test]
    fn bundled_overworld_sky_initializer_is_discovered_before_terrain() {
        let sky = derive_sky_source_stage(
            &complete_bundled_pack_source_for_test(),
            TerrainProgramScope::Overworld,
        )
        .unwrap()
        .expect("Complementary declares an overworld sky initializer");
        assert_eq!("world0/gbuffers_skybasic.fsh", sky.stage_path);
        assert_eq!(FullscreenSourceStageKind::Sky, sky.kind);
        assert_eq!(
            "world0/gbuffers_skybasic.vsh",
            sky.source_stages.vertex.path
        );
    }

    #[test]
    fn rejects_missing_final_or_paired_vertex_stage() {
        let no_final = ShaderPackSource::new(
            "no-final",
            1,
            vec![
                ShaderSourceFile::new("world0/composite.fsh", "void main() {}"),
                ShaderSourceFile::new("world0/composite.vsh", "void main() {}"),
            ],
        )
        .unwrap();
        assert!(derive_fullscreen_source_chain(&no_final, TerrainProgramScope::Overworld).is_err());

        let missing_vertex = ShaderPackSource::new(
            "missing-vertex",
            1,
            vec![ShaderSourceFile::new("world0/final.fsh", "void main() {}")],
        )
        .unwrap();
        assert!(
            derive_fullscreen_source_chain(&missing_vertex, TerrainProgramScope::Overworld)
                .is_err()
        );
    }
}

//! Source-derived, backend-neutral contract for vanilla rain and snow.
//!
//! Weather is not terrain-water and it is not a screen overlay.  This module
//! records only the game-semantic stream and source-program requirements that
//! a later Rust-owned translucent pass must satisfy.  It deliberately carries
//! no Iris objects, OpenGL state, attachment numbers, or backend handles.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::lowering::{
    lower_weather_source_pair as lower_weather_stages, LoweredWeatherSourcePair,
};
use super::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedTerrainSourceSummary,
};
use super::source::ShaderPackSource;
use super::terrain_contract::TerrainProgramScope;

/// Stable semantic weather texture family.  Java may name rain or snow, but
/// Rust owns texture decoding, resource lifetime, binding, and sampling.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum WeatherTextureKind {
    Rain,
    Snow,
}

/// Explicit source-level blend meaning.  This is intentionally not a GL blend
/// enum or Vulkan pipeline bit; the two backends lower it privately.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum WeatherBlend {
    SourceAlphaOver,
}

/// One selected shader-pack weather-pass requirement.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct WeatherPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub source_summary: PreprocessedTerrainSourceSummary,
    /// The source writes one semantic lit scene-color output.  This is a
    /// semantic ordinal from the source's DRAWBUFFERS declaration, never a
    /// backend attachment identity.
    pub lit_color_output_slot: u8,
    pub alpha_discard_threshold_bits: u32,
    pub blend: WeatherBlend,
    pub requires_lightmap: bool,
    pub requires_camera_relative_positions: bool,
    pub requires_weather_texture: bool,
}

impl WeatherPassContract {
    pub fn alpha_discard_threshold(&self) -> f32 {
        f32::from_bits(self.alpha_discard_threshold_bits)
    }

    pub fn requires_texture(&self, texture: WeatherTextureKind) -> bool {
        // Both families share the same source sampler contract.  The per-draw
        // semantic identity selects the Rust-owned resource later.
        let _ = texture;
        self.requires_weather_texture
    }
}

/// Derives the bounded normal-weather contract from the selected pack source.
/// Missing or incompatible source is a precise admission failure, not a
/// reason to borrow Iris state or substitute a generic material program.
pub fn derive_weather_pass_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<WeatherPassContract> {
    let fragment_path = weather_fragment_candidates(scope)
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "selected shader pack has no weather fragment source for {scope:?}; tried {}",
                weather_fragment_candidates(scope).join(", ")
            ))
        })?;
    let vertex_path = paired_vertex_path(source, fragment_path)?;
    let vertex = preprocess_artifact_with_runtime_options(source, &vertex_path, &[])?;
    let fragment = preprocess_artifact_with_runtime_options(source, fragment_path, &[])?;
    let vertex_source = vertex.expanded_source();
    let fragment_source = fragment.expanded_source();

    for required in [
        "gl_Vertex",
        "gl_MultiTexCoord0",
        "GetLightMapCoordinates",
        "gbufferModelView",
    ] {
        require_expression(vertex_source, required, "weather vertex")?;
    }
    for required in [
        "texture2D(tex, texCoord)",
        "color *= glColor",
        "lmCoord",
        "gl_FragData[0] = color",
    ] {
        require_expression(fragment_source, required, "weather fragment")?;
    }
    // The bounded v1 contract intentionally admits the exact alpha cutoff
    // used by the selected Complementary weather source.  A different source
    // expression needs a later general alpha-expression contract; it must not
    // silently receive this one.
    require_expression(fragment_source, "color.a < 0.1", "weather fragment")?;
    let slots = parse_draw_buffers(fragment_source)?;
    if slots.as_slice() != [0] {
        return Err(GalError::unsupported_feature(format!(
            "selected weather source requires unsupported DRAWBUFFERS schema {slots:?}; v1 admits only [0]",
        )));
    }
    Ok(WeatherPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        source_summary: PreprocessedTerrainSourceSummary {
            source_generation: source.generation(),
            vertex_entry: vertex.entry_path().to_string(),
            fragment_entry: fragment.entry_path().to_string(),
            vertex_fingerprint: vertex.fingerprint(),
            fragment_fingerprint: fragment.fingerprint(),
            vertex_dependencies: vertex.resolved_paths().to_vec(),
            fragment_dependencies: fragment.resolved_paths().to_vec(),
        },
        lit_color_output_slot: 0,
        alpha_discard_threshold_bits: 0.1f32.to_bits(),
        blend: WeatherBlend::SourceAlphaOver,
        requires_lightmap: true,
        requires_camera_relative_positions: true,
        requires_weather_texture: true,
    })
}

/// Lowers the exact selected weather source only after its source-derived
/// contract has been accepted. The result has no backend handle or route
/// effect; a later runtime writer must still supply named targets, owned
/// textures, and all required semantic uniforms.
pub fn lower_weather_source_pair(
    source: &ShaderPackSource,
    contract: &WeatherPassContract,
) -> GalResult<LoweredWeatherSourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "weather contract does not belong to the supplied shader-pack source",
        ));
    }
    let vertex = preprocess_artifact_with_runtime_options(
        source,
        &contract.source_summary.vertex_entry,
        &[],
    )?;
    let fragment = preprocess_artifact_with_runtime_options(
        source,
        &contract.source_summary.fragment_entry,
        &[],
    )?;
    let lowered = lower_weather_stages(&vertex, &fragment)?;
    lowered.require_backend_neutral_lowering()?;
    Ok(lowered)
}

fn weather_fragment_candidates(scope: TerrainProgramScope) -> &'static [&'static str] {
    match scope {
        TerrainProgramScope::Default => &["gbuffers_weather.fsh", "program/gbuffers_weather.glsl"],
        TerrainProgramScope::Overworld => &[
            "world0/gbuffers_weather.fsh",
            "gbuffers_weather.fsh",
            "program/gbuffers_weather.glsl",
        ],
        TerrainProgramScope::Nether => &[
            "world-1/gbuffers_weather.fsh",
            "gbuffers_weather.fsh",
            "program/gbuffers_weather.glsl",
        ],
        TerrainProgramScope::End => &[
            "world1/gbuffers_weather.fsh",
            "gbuffers_weather.fsh",
            "program/gbuffers_weather.glsl",
        ],
    }
}

fn paired_vertex_path(source: &ShaderPackSource, fragment_path: &str) -> GalResult<String> {
    if let Some(stem) = fragment_path.strip_suffix(".fsh") {
        let path = format!("{stem}.vsh");
        if source.get(&path).is_some() {
            return Ok(path);
        }
        return Err(GalError::invalid_argument(format!(
            "weather fragment source {fragment_path} has no matching vertex source {path}",
        )));
    }
    if fragment_path.ends_with(".glsl") {
        return Ok(fragment_path.to_string());
    }
    Err(GalError::invalid_argument(format!(
        "weather fragment source {fragment_path} has no semantic vertex-stage pairing",
    )))
}

fn parse_draw_buffers(source: &str) -> GalResult<Vec<u8>> {
    let marker = "DRAWBUFFERS:";
    let start = source.find(marker).ok_or_else(|| {
        GalError::invalid_argument("weather fragment source has no DRAWBUFFERS declaration")
    })? + marker.len();
    let digits = source[start..]
        .chars()
        .take_while(|character| character.is_ascii_digit())
        .collect::<String>();
    if digits.is_empty() {
        return Err(GalError::invalid_argument(
            "weather fragment DRAWBUFFERS declaration has no color slots",
        ));
    }
    digits
        .chars()
        .map(|digit| {
            digit.to_digit(10).map(|value| value as u8).ok_or_else(|| {
                GalError::invalid_argument(
                    "weather fragment DRAWBUFFERS contains a non-decimal slot",
                )
            })
        })
        .collect()
}

fn require_expression(source: &str, expression: &str, stage: &str) -> GalResult<()> {
    if source.contains(expression) {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "selected {stage} source is missing required semantic expression '{expression}'",
        )))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    fn source(fragment_draw_buffers: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "weather-test",
            7,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_weather.vsh",
                    "#version 130\nvoid main() { vec4 p = gl_Vertex; vec2 uv = gl_MultiTexCoord0.xy; vec2 lm = GetLightMapCoordinates(); gl_Position = gbufferModelView * p; }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_weather.fsh",
                    format!("#version 130\nvoid main() {{ vec4 color = texture2D(tex, texCoord); color *= glColor; if (color.a < 0.1) discard; vec2 x = lmCoord; /* DRAWBUFFERS:{fragment_draw_buffers} */ gl_FragData[0] = color; }}"),
                ),
            ],
        )
        .unwrap()
    }

    #[test]
    fn derives_explicit_weather_source_contract() {
        let contract =
            derive_weather_pass_contract(&source("0"), TerrainProgramScope::Overworld).unwrap();
        assert_eq!("weather-test", contract.pack_name);
        assert_eq!(7, contract.generation);
        assert_eq!(WeatherBlend::SourceAlphaOver, contract.blend);
        assert_eq!(0.1, contract.alpha_discard_threshold());
        assert!(contract.requires_texture(WeatherTextureKind::Rain));
        assert!(contract.requires_texture(WeatherTextureKind::Snow));
        assert_eq!(
            "world0/gbuffers_weather.vsh",
            contract.source_summary.vertex_entry
        );
    }

    #[test]
    fn rejects_unsupported_weather_output_schema() {
        let error = derive_weather_pass_contract(&source("06"), TerrainProgramScope::Overworld)
            .unwrap_err();
        assert!(error.to_string().contains("DRAWBUFFERS schema"));
    }

    #[test]
    fn rejects_missing_weather_vertex_pair() {
        let source = ShaderPackSource::new(
            "weather-test",
            7,
            vec![ShaderSourceFile::new(
                "world0/gbuffers_weather.fsh",
                "void main() {}",
            )],
        )
        .unwrap();
        let error =
            derive_weather_pass_contract(&source, TerrainProgramScope::Overworld).unwrap_err();
        assert!(error.to_string().contains("matching vertex source"));
    }

    #[test]
    fn lowers_weather_with_a_distinct_named_output_and_compact_material_stream() {
        let source = source("0");
        let contract =
            derive_weather_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let lowered = lower_weather_source_pair(&source, &contract).unwrap();

        assert!(lowered
            .vertex()
            .source()
            .contains("VulkanicSourceTexturedMaterialVertex"));
        assert!(lowered
            .fragment()
            .source()
            .contains("out_weather_lit_color"));
        assert!(!lowered.fragment().source().contains("gl_FragData"));
        assert!(lowered.require_backend_neutral_lowering().is_ok());
    }

    #[test]
    fn complete_bundled_weather_source_prepares_through_the_semantic_lowerer() {
        let source =
            crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test(
            );
        let contract =
            derive_weather_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let lowered = lower_weather_source_pair(&source, &contract).unwrap();
        assert_eq!(
            &[super::super::lowering::WeatherFragmentOutput::LitColor],
            lowered.fragment().outputs()
        );
    }
}

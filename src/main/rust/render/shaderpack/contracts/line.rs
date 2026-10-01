//! Source-derived contract for the vanilla block-selection outline.
//!
//! Iris draws the selection box with the pack's `gbuffers_line` program into
//! the G-buffer (before deferred for ordinary blocks, after translucent
//! terrain for translucent ones). This module records only the selected
//! source's output schema; the Rust-owned line writer supplies the copied
//! segment stream, targets and pass placement. No Iris or GL state is read.

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::lowering::{
    lower_line_source_pair as lower_line_stages, CloudFragmentOutput, LoweredCloudSourcePair,
    TerrainSourceOpaqueResourceBindingPlan,
};
use crate::render::shaderpack::source::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedTerrainSourceSummary,
};
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::contracts::terrain::{TerrainPassOutput, TerrainProgramScope};
use crate::render::shaderpack::resources::bindings::TerrainSourceResourceBindings;

/// Named outputs written by the selected line source.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum LinePassOutput {
    LitColor,
    MaterialAuxiliary,
}

impl LinePassOutput {
    pub(crate) fn terrain_output(self) -> TerrainPassOutput {
        match self {
            Self::LitColor => TerrainPassOutput::LitTerrainColor,
            Self::MaterialAuxiliary => TerrainPassOutput::MaterialAuxiliary,
        }
    }

    fn fragment_output(self) -> CloudFragmentOutput {
        match self {
            Self::LitColor => CloudFragmentOutput::LitColor,
            Self::MaterialAuxiliary => CloudFragmentOutput::MaterialAuxiliary,
        }
    }
}

/// One selected `gbuffers_line` stage.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct LinePassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub source_summary: PreprocessedTerrainSourceSummary,
    pub outputs: Vec<LinePassOutput>,
    /// Source DRAWBUFFERS slots paired with `outputs`; mapped to named
    /// Rust-owned targets by the writer, never to backend attachments.
    pub output_color_slots: Vec<u32>,
}

/// Derives the line contract. A pack without a `gbuffers_line` stage that
/// expands segments itself stays unadmitted: Rust does not substitute a
/// fixed-function line raster or another program.
pub fn derive_line_pass_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<LinePassContract> {
    let fragment_path = line_fragment_candidates(scope)
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "selected shader pack has no line fragment source for {scope:?}; tried {}",
                line_fragment_candidates(scope).join(", ")
            ))
        })?;
    let vertex_path = paired_vertex_path(source, fragment_path)?;
    let vertex = preprocess_artifact_with_runtime_options(source, &vertex_path, &[])?;
    let fragment = preprocess_artifact_with_runtime_options(source, fragment_path, &[])?;
    for required in ["vaPosition", "vaNormal", "gl_VertexID", "gl_Color"] {
        if !vertex.expanded_source().contains(required) {
            return Err(GalError::unsupported_feature(format!(
                "selected line vertex source does not expand segments itself (missing '{required}')",
            )));
        }
    }
    let slots = parse_draw_buffers(fragment.expanded_source())?;
    let outputs = match slots.as_slice() {
        [0] => vec![LinePassOutput::LitColor],
        [0, 6] => vec![LinePassOutput::LitColor, LinePassOutput::MaterialAuxiliary],
        _ => {
            return Err(GalError::unsupported_feature(format!(
                "selected line source requires unsupported DRAWBUFFERS schema {slots:?}; admitted [0] or [0, 6]",
            )));
        }
    };
    Ok(LinePassContract {
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
        outputs,
        output_color_slots: slots.into_iter().map(u32::from).collect(),
    })
}

/// Lowers the exact selected line pair after contract acceptance.
pub fn lower_line_source_pair(
    source: &ShaderPackSource,
    contract: &LinePassContract,
) -> GalResult<LoweredCloudSourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "line contract does not belong to the supplied shader-pack source",
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
    let outputs = contract
        .outputs
        .iter()
        .map(|output| output.fragment_output())
        .collect::<Vec<_>>();
    let lowered = lower_line_stages(&vertex, &fragment, &outputs)?;
    lowered.require_backend_neutral_lowering()?;
    Ok(lowered)
}

/// Contract -> lowering -> semantic resource binding -> program, for one
/// immutable source generation.
pub fn prepare_line_source_program(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<crate::render::shaderpack::programs::LoweredTexturedMaterialSourceProgram> {
    let contract = derive_line_pass_contract(source, scope)?;
    let lowered = lower_line_source_pair(source, &contract)?;
    let declarations = TerrainSourceResourceBindings::from_source(source)?;
    let bindings: TerrainSourceOpaqueResourceBindingPlan = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)?;
    crate::render::shaderpack::programs::prepare_lowered_line_source_program(&contract, &lowered, &bindings)
}

fn line_fragment_candidates(scope: TerrainProgramScope) -> &'static [&'static str] {
    match scope {
        TerrainProgramScope::Default => &["gbuffers_line.fsh"],
        TerrainProgramScope::Overworld => &["world0/gbuffers_line.fsh", "gbuffers_line.fsh"],
        TerrainProgramScope::Nether => &["world-1/gbuffers_line.fsh", "gbuffers_line.fsh"],
        TerrainProgramScope::End => &["world1/gbuffers_line.fsh", "gbuffers_line.fsh"],
    }
}

fn paired_vertex_path(source: &ShaderPackSource, fragment_path: &str) -> GalResult<String> {
    let stem = fragment_path.strip_suffix(".fsh").ok_or_else(|| {
        GalError::invalid_argument(format!(
            "line fragment source {fragment_path} has no semantic vertex-stage pairing",
        ))
    })?;
    let path = format!("{stem}.vsh");
    if source.get(&path).is_some() {
        Ok(path)
    } else {
        Err(GalError::invalid_argument(format!(
            "line fragment source {fragment_path} has no matching vertex source {path}",
        )))
    }
}

fn parse_draw_buffers(source: &str) -> GalResult<Vec<u8>> {
    let marker = "DRAWBUFFERS:";
    let start = source.find(marker).ok_or_else(|| {
        GalError::invalid_argument("line fragment source has no DRAWBUFFERS declaration")
    })? + marker.len();
    let digits = source[start..]
        .chars()
        .take_while(|character| character.is_ascii_digit())
        .collect::<String>();
    if digits.is_empty() {
        return Err(GalError::invalid_argument(
            "line fragment DRAWBUFFERS declaration has no color slots",
        ));
    }
    Ok(digits.bytes().map(|digit| digit - b'0').collect())
}

#[cfg(test)]
mod tests {
    use crate::render::shaderpack::contracts::line::*;

    #[test]
    fn bundled_complementary_line_source_lowers_onto_the_material_stream() {
        let source =
            crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
            );
        let contract = derive_line_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(
            vec![LinePassOutput::LitColor, LinePassOutput::MaterialAuxiliary],
            contract.outputs
        );
        assert_eq!(vec![0, 6], contract.output_color_slots);
        let lowered = lower_line_source_pair(&source, &contract).unwrap();
        let vertex = lowered.vertex().source();
        for injected in ["vaPosition", "vaNormal", "modelViewMatrix", "projectionMatrix", "gl_VertexID"] {
            assert!(
                !crate::render::shaderpack::lowering::glsl_identifiers_for_test(vertex).contains(injected),
                "{injected} must be lowered to an explicit Rust stream input"
            );
        }
        assert!(vertex.contains("vulkanic_source_stored_vertex_id"));
        assert!(lowered.fragment().source().contains("out_cloud_lit_color"));
        assert!(lowered
            .fragment()
            .source()
            .contains("out_cloud_material_auxiliary"));
        let program = prepare_line_source_program(&source, TerrainProgramScope::Overworld).unwrap();
        assert!(program.identity.as_str().contains("line_source_gen"));
    }

    #[test]
    fn rejects_a_line_source_that_relies_on_fixed_function_lines() {
        use crate::render::shaderpack::source::ShaderSourceFile;
        let source = ShaderPackSource::new(
            "line-test",
            3,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_line.vsh",
                    "#version 130\nvoid main() { gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_line.fsh",
                    "#version 130\nvoid main() { /* DRAWBUFFERS:0 */ gl_FragData[0] = vec4(1.0); }",
                ),
            ],
        )
        .unwrap();
        let error = derive_line_pass_contract(&source, TerrainProgramScope::Overworld).unwrap_err();
        assert!(error.to_string().contains("does not expand segments itself"));
    }
}

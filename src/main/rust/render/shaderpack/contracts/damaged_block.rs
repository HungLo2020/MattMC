//! Source-derived contract for block-breaking progress ("crumbling").
//!
//! Iris draws vanilla's crumbling overlay with the pack's
//! `gbuffers_damagedblock` program (`ProgramId.DamagedBlock`) into the
//! G-buffer after opaque, entity, and block-entity work and before the
//! deferred passes. The draw keeps vanilla's crumbling state: a 2x multiply
//! blend, depth test without depth write, and a negative depth bias. This
//! module records only the selected source's output schema and alpha test;
//! the Rust-owned writer supplies the copied crumbling quads, targets, and
//! pass placement. No Iris or GL state is read.

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::lowering::{
    lower_damaged_block_source_pair as lower_damaged_block_stages, CloudFragmentOutput,
    LoweredCloudSourcePair, TerrainSourceOpaqueResourceBindingPlan,
};
use crate::render::shaderpack::source::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedTerrainSourceSummary,
};
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::contracts::terrain::{TerrainPassOutput, TerrainProgramScope};
use crate::render::shaderpack::resources::bindings::TerrainSourceResourceBindings;

/// Iris `ShaderKey.CRUMBLING` default alpha test (`AlphaTests.ONE_TENTH_ALPHA`),
/// used when the pack declares no `alphaTest.gbuffers_damagedblock`.
const DEFAULT_ALPHA_CUTOFF: f32 = 0.1;

/// Named outputs written by the selected damaged-block source.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum DamagedBlockPassOutput {
    LitColor,
    MaterialAuxiliary,
}

impl DamagedBlockPassOutput {
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

/// One selected `gbuffers_damagedblock` stage.
#[derive(Clone, Debug, PartialEq)]
pub struct DamagedBlockPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub source_summary: PreprocessedTerrainSourceSummary,
    pub outputs: Vec<DamagedBlockPassOutput>,
    /// Source DRAWBUFFERS slots paired with `outputs`; mapped to named
    /// Rust-owned targets by the writer, never to backend attachments.
    pub output_color_slots: Vec<u32>,
    /// Iris's injected alpha test on the primary output (`None` = off).
    pub alpha_cutoff: Option<f32>,
}

/// Derives the damaged-block contract. Iris would fall back to
/// `gbuffers_terrain` (then textured/basic) for a pack without this stage;
/// those consume terrain-only inputs this stream does not model, so such a
/// pack stays unadmitted rather than receiving a substitute program.
pub fn derive_damaged_block_pass_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<DamagedBlockPassContract> {
    let fragment_path = damaged_block_fragment_candidates(scope)
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "selected shader pack has no damagedblock fragment source for {scope:?}; tried {} (Iris fallback programs are not modeled)",
                damaged_block_fragment_candidates(scope).join(", ")
            ))
        })?;
    let vertex_path = paired_vertex_path(source, fragment_path)?;
    let vertex = preprocess_artifact_with_runtime_options(source, &vertex_path, &[])?;
    let fragment = preprocess_artifact_with_runtime_options(source, fragment_path, &[])?;
    let slots = parse_draw_buffers(fragment.expanded_source())?;
    let outputs = match slots.as_slice() {
        [0] => vec![DamagedBlockPassOutput::LitColor],
        [0, 6] => vec![
            DamagedBlockPassOutput::LitColor,
            DamagedBlockPassOutput::MaterialAuxiliary,
        ],
        _ => {
            return Err(GalError::unsupported_feature(format!(
                "selected damagedblock source requires unsupported DRAWBUFFERS schema {slots:?}; admitted [0] or [0, 6]",
            )));
        }
    };
    let alpha_cutoff = crate::render::shaderpack::properties::shadow::source_alpha_test_cutoff(
        source,
        "alphaTest.gbuffers_damagedblock",
        DEFAULT_ALPHA_CUTOFF,
    )?;
    Ok(DamagedBlockPassContract {
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
        alpha_cutoff,
    })
}

/// Lowers the exact selected damaged-block pair after contract acceptance.
pub fn lower_damaged_block_source_pair(
    source: &ShaderPackSource,
    contract: &DamagedBlockPassContract,
) -> GalResult<LoweredCloudSourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "damagedblock contract does not belong to the supplied shader-pack source",
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
    let lowered = lower_damaged_block_stages(&vertex, &fragment, &outputs)?;
    lowered.require_backend_neutral_lowering()?;
    Ok(lowered)
}

/// Contract -> lowering -> semantic resource binding -> program, for one
/// immutable source generation.
pub fn prepare_damaged_block_source_program(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<crate::render::shaderpack::programs::LoweredTexturedMaterialSourceProgram> {
    let contract = derive_damaged_block_pass_contract(source, scope)?;
    let lowered = lower_damaged_block_source_pair(source, &contract)?;
    let fragment = preprocess_artifact_with_runtime_options(
        source, &contract.source_summary.fragment_entry, &[],
    )?;
    let declarations = TerrainSourceResourceBindings::from_preprocessed_stage(source, &fragment)?;
    let bindings: TerrainSourceOpaqueResourceBindingPlan = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)?;
    crate::render::shaderpack::programs::prepare_lowered_damaged_block_source_program(&contract, &lowered, &bindings)
}

fn damaged_block_fragment_candidates(scope: TerrainProgramScope) -> &'static [&'static str] {
    match scope {
        TerrainProgramScope::Default => &["gbuffers_damagedblock.fsh"],
        TerrainProgramScope::Overworld => &[
            "world0/gbuffers_damagedblock.fsh",
            "gbuffers_damagedblock.fsh",
        ],
        TerrainProgramScope::Nether => &[
            "world-1/gbuffers_damagedblock.fsh",
            "gbuffers_damagedblock.fsh",
        ],
        TerrainProgramScope::End => &[
            "world1/gbuffers_damagedblock.fsh",
            "gbuffers_damagedblock.fsh",
        ],
    }
}

fn paired_vertex_path(source: &ShaderPackSource, fragment_path: &str) -> GalResult<String> {
    let stem = fragment_path.strip_suffix(".fsh").ok_or_else(|| {
        GalError::invalid_argument(format!(
            "damagedblock fragment source {fragment_path} has no semantic vertex-stage pairing",
        ))
    })?;
    let path = format!("{stem}.vsh");
    if source.get(&path).is_some() {
        Ok(path)
    } else {
        Err(GalError::invalid_argument(format!(
            "damagedblock fragment source {fragment_path} has no matching vertex source {path}",
        )))
    }
}

fn parse_draw_buffers(source: &str) -> GalResult<Vec<u8>> {
    let marker = "DRAWBUFFERS:";
    let start = source.find(marker).ok_or_else(|| {
        GalError::invalid_argument("damagedblock fragment source has no DRAWBUFFERS declaration")
    })? + marker.len();
    let digits = source[start..]
        .chars()
        .take_while(|character| character.is_ascii_digit())
        .collect::<String>();
    if digits.is_empty() {
        return Err(GalError::invalid_argument(
            "damagedblock fragment DRAWBUFFERS declaration has no color slots",
        ));
    }
    Ok(digits.bytes().map(|digit| digit - b'0').collect())
}

#[cfg(test)]
mod tests {
    use crate::render::shaderpack::contracts::damaged_block::*;

    #[test]
    fn bundled_complementary_damaged_block_source_lowers_onto_the_material_stream() {
        let source =
            crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test(
            );
        let contract =
            derive_damaged_block_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(vec![DamagedBlockPassOutput::LitColor], contract.outputs);
        assert_eq!(vec![0], contract.output_color_slots);
        let lowered = lower_damaged_block_source_pair(&source, &contract).unwrap();
        assert!(lowered.fragment().source().contains("out_cloud_lit_color"));
        let program =
            prepare_damaged_block_source_program(&source, TerrainProgramScope::Overworld).unwrap();
        assert!(program.identity.as_str().contains("damagedblock_source_gen"));
        let fragment = &program.fragment.source;
        let cutoff = contract.alpha_cutoff.expect("damagedblock keeps an alpha test");
        assert!(
            fragment.contains(&format!("out_cloud_lit_color.a > {cutoff:.8}")),
            "the Iris alpha test must be applied to the primary output"
        );
    }

    #[test]
    fn alpha_test_property_selects_the_cutoff_and_defaults_like_iris() {
        use crate::render::shaderpack::source::ShaderSourceFile;
        let files = |properties: Option<&str>| {
            let mut files = vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_damagedblock.vsh",
                    "#version 130\nvarying vec2 texCoord;\nvarying vec4 glColor;\nvoid main() { gl_Position = ftransform(); texCoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy; glColor = gl_Color; }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_damagedblock.fsh",
                    "#version 130\nuniform sampler2D tex;\nvarying vec2 texCoord;\nvarying vec4 glColor;\nvoid main() { vec4 color = texture2D(tex, texCoord); color.rgb *= glColor.rgb; /* DRAWBUFFERS:0 */ gl_FragData[0] = color; }",
                ),
            ];
            if let Some(properties) = properties {
                files.push(ShaderSourceFile::new("shaders.properties", properties));
            }
            ShaderPackSource::new("damaged-test", 4, files).unwrap()
        };
        let declared = derive_damaged_block_pass_contract(
            &files(Some("alphaTest.gbuffers_damagedblock=GREATER 0.004\n")),
            TerrainProgramScope::Overworld,
        )
        .unwrap();
        assert_eq!(Some(0.004), declared.alpha_cutoff);
        let default = derive_damaged_block_pass_contract(&files(None), TerrainProgramScope::Overworld)
            .unwrap();
        assert_eq!(Some(DEFAULT_ALPHA_CUTOFF), default.alpha_cutoff);
        let off = derive_damaged_block_pass_contract(
            &files(Some("alphaTest.gbuffers_damagedblock=off\n")),
            TerrainProgramScope::Overworld,
        )
        .unwrap();
        assert_eq!(None, off.alpha_cutoff);
    }

    #[test]
    fn a_pack_without_a_damaged_block_stage_stays_unadmitted() {
        use crate::render::shaderpack::source::ShaderSourceFile;
        let source = ShaderPackSource::new(
            "damaged-missing",
            5,
            vec![ShaderSourceFile::new(
                "world0/gbuffers_terrain.fsh",
                "#version 130\nvoid main() { /* DRAWBUFFERS:0 */ gl_FragData[0] = vec4(1.0); }",
            )],
        )
        .unwrap();
        let error =
            derive_damaged_block_pass_contract(&source, TerrainProgramScope::Overworld).unwrap_err();
        assert!(error.to_string().contains("no damagedblock fragment source"));
    }
}

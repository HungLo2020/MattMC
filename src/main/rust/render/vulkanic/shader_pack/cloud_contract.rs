//! Source-derived semantic contract for vanilla cloud geometry.
//!
//! Clouds are neither terrain nor weather.  The Java renderer's packed cloud
//! face buffer is an implementation detail, so a Rust route must instead own
//! copied cloud-face semantics and a source-defined output contract.  This
//! module records that contract without importing Iris state, OpenGL objects,
//! or backend handles.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::lowering::{lower_cloud_source_pair as lower_cloud_stages, LoweredCloudSourcePair};
use super::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedTerrainSourceSummary,
};
use super::source::ShaderPackSource;
use super::terrain_contract::TerrainProgramScope;

/// Named outputs written by the bounded vanilla-cloud source contract.
///
/// These names are semantic roles.  They deliberately do not expose the
/// source pack's numeric DRAWBUFFERS slots to frontends or backends.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CloudPassOutput {
    LitColor,
    MaterialAuxiliary,
    Translucency,
}

impl CloudPassOutput {
    /// Maps a source-cloud output onto a named shader-runtime target role.
    /// Source DRAWBUFFERS slots never become backend attachment identities.
    pub(crate) fn terrain_output(self) -> super::terrain_contract::TerrainPassOutput {
        match self {
            Self::LitColor => super::terrain_contract::TerrainPassOutput::LitTerrainColor,
            Self::MaterialAuxiliary => {
                super::terrain_contract::TerrainPassOutput::MaterialAuxiliary
            }
            Self::Translucency => super::terrain_contract::TerrainPassOutput::TranslucencyAuxiliary,
        }
    }
}

/// Explicit source-level blend meaning for vanilla cloud faces.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CloudBlend {
    SourceAlphaOver,
}

/// The selected pack either accepts vanilla cloud faces through its cloud
/// writer or explicitly discards them in favor of pack-owned cloud effects.
/// This is source policy, not a Java-route or backend decision.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum CloudFaceDisposition {
    DrawVanillaFaces,
    SuppressVanillaFaces,
}

/// One source-defined cloud stage consumed by the Rust-owned cloud writer.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CloudPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub source_summary: PreprocessedTerrainSourceSummary,
    pub outputs: Vec<CloudPassOutput>,
    /// Source-declared color output slots paired with `outputs`. These remain
    /// source-contract metadata and are mapped to Rust-owned named targets by
    /// the runtime writer, never exposed as backend attachment identities.
    pub output_color_slots: Vec<u32>,
    pub blend: CloudBlend,
    pub face_disposition: CloudFaceDisposition,
    pub requires_camera_relative_positions: bool,
    pub requires_face_color: bool,
    pub requires_cloud_texture_coordinates: bool,
}

/// Derives the selected pack's vanilla-cloud contract from source.  A pack
/// that uses a different output schema or a non-vanilla cloud representation
/// stays unadmitted instead of being run through terrain or weather code.
pub fn derive_cloud_pass_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<CloudPassContract> {
    let fragment_path = cloud_fragment_candidates(scope)
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "selected shader pack has no cloud fragment source for {scope:?}; tried {}",
                cloud_fragment_candidates(scope).join(", ")
            ))
        })?;
    let vertex_path = paired_vertex_path(source, fragment_path)?;
    // The active cloud style is an immutable semantic source option copied
    // with the pack generation. `preprocess_artifact_with_runtime_options`
    // applies it here; discovery must not infer or override a style.
    let vertex = preprocess_artifact_with_runtime_options(source, &vertex_path, &[])?;
    let fragment = preprocess_artifact_with_runtime_options(source, fragment_path, &[])?;
    let vertex_source = vertex.expanded_source();
    let fragment_source = fragment.expanded_source();

    // Complementary's configured non-vanilla cloud modes intentionally
    // reduce this legacy stage to an off-screen vertex and fragment discard.
    // Rust must honor that source-owned decision rather than attempting to
    // revive a generic cloud draw with another program.
    if source_suppresses_vanilla_cloud_faces(vertex_source, fragment_source) {
        return Ok(CloudPassContract {
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
            outputs: Vec::new(),
            output_color_slots: Vec::new(),
            blend: CloudBlend::SourceAlphaOver,
            face_disposition: CloudFaceDisposition::SuppressVanillaFaces,
            requires_camera_relative_positions: false,
            requires_face_color: false,
            requires_cloud_texture_coordinates: false,
        });
    }

    for required in [
        "gl_Vertex",
        "gl_MultiTexCoord0",
        "gl_Color",
        "gbufferModelView",
    ] {
        require_expression(vertex_source, required, "cloud vertex")?;
    }
    for required in ["gl_FragData[0]", "gl_FragData[1]", "gl_FragData[2]"] {
        require_expression(fragment_source, required, "cloud fragment")?;
    }
    let slots = parse_draw_buffers(fragment_source)?;
    if slots.as_slice() != [0, 6, 3] {
        return Err(GalError::unsupported_feature(format!(
            "selected cloud source requires unsupported DRAWBUFFERS schema {slots:?}; v1 admits only [0, 6, 3]",
        )));
    }
    Ok(CloudPassContract {
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
        outputs: vec![
            CloudPassOutput::LitColor,
            CloudPassOutput::MaterialAuxiliary,
            CloudPassOutput::Translucency,
        ],
        output_color_slots: vec![0, 6, 3],
        blend: CloudBlend::SourceAlphaOver,
        face_disposition: CloudFaceDisposition::DrawVanillaFaces,
        requires_camera_relative_positions: true,
        requires_face_color: true,
        requires_cloud_texture_coordinates: true,
    })
}

/// Lowers the exact selected cloud pair after contract acceptance. This owns
/// the source text and semantic layouts consumed by the Rust-owned cloud
/// fullscreen/material writer; target allocation and route selection happen
/// later, after the copied cloud-cell stream has been validated.
pub fn lower_cloud_source_pair(
    source: &ShaderPackSource,
    contract: &CloudPassContract,
) -> GalResult<LoweredCloudSourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "cloud contract does not belong to the supplied shader-pack source",
        ));
    }
    if contract.face_disposition == CloudFaceDisposition::SuppressVanillaFaces {
        return Err(GalError::unsupported_feature(
            "source-declared cloud suppression has no gbuffers_clouds draw pair",
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
    let lowered = lower_cloud_stages(&vertex, &fragment)?;
    lowered.require_backend_neutral_lowering()?;
    Ok(lowered)
}

fn source_suppresses_vanilla_cloud_faces(vertex: &str, fragment: &str) -> bool {
    vertex.contains("gl_Position = vec4(-1.0)")
        && fragment.contains("discard;")
        && !vertex.contains("gl_Vertex")
}

fn cloud_fragment_candidates(scope: TerrainProgramScope) -> &'static [&'static str] {
    match scope {
        TerrainProgramScope::Default => &["gbuffers_clouds.fsh", "program/gbuffers_clouds.glsl"],
        TerrainProgramScope::Overworld => &[
            "world0/gbuffers_clouds.fsh",
            "gbuffers_clouds.fsh",
            "program/gbuffers_clouds.glsl",
        ],
        TerrainProgramScope::Nether => &[
            "world-1/gbuffers_clouds.fsh",
            "gbuffers_clouds.fsh",
            "program/gbuffers_clouds.glsl",
        ],
        TerrainProgramScope::End => &[
            "world1/gbuffers_clouds.fsh",
            "gbuffers_clouds.fsh",
            "program/gbuffers_clouds.glsl",
        ],
    }
}

fn paired_vertex_path(source: &ShaderPackSource, fragment_path: &str) -> GalResult<String> {
    let Some(stem) = fragment_path.strip_suffix(".fsh") else {
        return Err(GalError::invalid_argument(format!(
            "cloud fragment source {fragment_path} has no semantic vertex-stage pairing",
        )));
    };
    let path = format!("{stem}.vsh");
    if source.get(&path).is_some() {
        Ok(path)
    } else {
        Err(GalError::invalid_argument(format!(
            "cloud fragment source {fragment_path} has no matching vertex source {path}",
        )))
    }
}

fn parse_draw_buffers(source: &str) -> GalResult<Vec<u8>> {
    let marker = "DRAWBUFFERS:";
    let start = source.find(marker).ok_or_else(|| {
        GalError::invalid_argument("cloud fragment source has no DRAWBUFFERS declaration")
    })? + marker.len();
    let digits = source[start..]
        .chars()
        .take_while(|character| character.is_ascii_digit())
        .collect::<String>();
    if digits.is_empty() {
        return Err(GalError::invalid_argument(
            "cloud fragment DRAWBUFFERS declaration has no color slots",
        ));
    }
    digits
        .chars()
        .map(|digit| {
            digit.to_digit(10).map(|value| value as u8).ok_or_else(|| {
                GalError::invalid_argument("cloud fragment DRAWBUFFERS contains a non-decimal slot")
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

    fn source(draw_buffers: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "cloud-test",
            9,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_clouds.vsh",
                    "#version 130\nvoid main() { vec4 p = gl_Vertex; vec2 uv = gl_MultiTexCoord0.xy; vec4 color = gl_Color; gl_Position = gbufferModelView * p; }",
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_clouds.fsh",
                    format!("#version 130\nvoid main() {{ /* DRAWBUFFERS:{draw_buffers} */ gl_FragData[0] = vec4(1.0); gl_FragData[1] = vec4(0.0); gl_FragData[2] = vec4(0.0); }}"),
                ),
            ],
        )
        .unwrap()
    }

    #[test]
    fn derives_named_vanilla_cloud_outputs() {
        let contract =
            derive_cloud_pass_contract(&source("063"), TerrainProgramScope::Overworld).unwrap();
        assert_eq!("cloud-test", contract.pack_name);
        assert_eq!(9, contract.generation);
        assert_eq!(CloudBlend::SourceAlphaOver, contract.blend);
        assert_eq!(
            vec![
                CloudPassOutput::LitColor,
                CloudPassOutput::MaterialAuxiliary,
                CloudPassOutput::Translucency,
            ],
            contract.outputs
        );
        assert_eq!(
            "world0/gbuffers_clouds.vsh",
            contract.source_summary.vertex_entry
        );
    }

    #[test]
    fn rejects_a_cloud_schema_that_cannot_share_the_named_gbuffer_writer() {
        let error =
            derive_cloud_pass_contract(&source("06"), TerrainProgramScope::Overworld).unwrap_err();
        assert!(error.to_string().contains("DRAWBUFFERS schema"));
    }

    #[test]
    fn bundled_complementary_cloud_source_rejects_its_inactive_cloud_branch() {
        let source =
            crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test(
            );
        let contract = derive_cloud_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(
            CloudFaceDisposition::SuppressVanillaFaces,
            contract.face_disposition
        );
        assert!(contract.outputs.is_empty());
        let error = lower_cloud_source_pair(&source, &contract).unwrap_err();
        assert!(error.to_string().contains("cloud suppression"));
    }

    #[test]
    fn bundled_complementary_cloud_source_discovers_the_runtime_selected_vanilla_style() {
        let source =
            crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test(
            );
        let mut files = source.files();
        files.push(ShaderSourceFile::new(
            crate::render::vulkanic::shader_pack::source::RUNTIME_OPTIONS_PATH,
            "CLOUD_STYLE_DEFINE=50\n",
        ));
        let source =
            ShaderPackSource::new("Complementary-cloud-style-50", source.generation(), files)
                .unwrap();

        let contract = derive_cloud_pass_contract(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(TerrainProgramScope::Overworld, contract.scope);
        assert_eq!(
            CloudFaceDisposition::DrawVanillaFaces,
            contract.face_disposition
        );
        assert_eq!(
            "world0/gbuffers_clouds.fsh",
            contract.source_summary.fragment_entry
        );
        let lowered = lower_cloud_source_pair(&source, &contract).unwrap();
        lowered.require_backend_neutral_lowering().unwrap();
        assert!(lowered.fragment().source().contains("out_cloud_lit_color"));
        assert!(lowered
            .fragment()
            .source()
            .contains("out_cloud_translucency_auxiliary"));
    }
}

//! Backend-neutral GLSL source-dialect preflight.
//!
//! Shader packs frequently target the historical OpenGL compatibility
//! surface. Before a selected source can become a Rust-owned program, that
//! source must be lowered to explicit shader inputs and named outputs. This
//! module records the exact source-language work still required; it neither
//! borrows an OpenGL program nor selects a renderer route.

use std::collections::BTreeSet;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::preprocess::PreprocessedShaderSource;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum GlslDialectGap {
    PreVulkanGlslVersion,
    CompatibilityTextureBuiltin,
    CompatibilityFragmentOutputs,
    FixedFunctionVertexTransform,
    CompatibilityVertexAttributes,
    /// Legacy fixed-function fog parameters such as `gl_Fog.start` and
    /// `gl_Fog.scale`. These require an explicit semantic fog-range contract;
    /// a fog color alone is not equivalent.
    CompatibilityFogParameters,
}

impl GlslDialectGap {
    pub fn semantic_name(self) -> &'static str {
        match self {
            Self::PreVulkanGlslVersion => "pre_vulkan_glsl_version",
            Self::CompatibilityTextureBuiltin => "compatibility_texture_builtin",
            Self::CompatibilityFragmentOutputs => "compatibility_fragment_outputs",
            Self::FixedFunctionVertexTransform => "fixed_function_vertex_transform",
            Self::CompatibilityVertexAttributes => "compatibility_vertex_attributes",
            Self::CompatibilityFogParameters => "compatibility_fog_parameters",
        }
    }
}

/// Immutable source-language requirements for one already-expanded stage.
/// The report is suitable for diagnostics and lowering admission only; it
/// carries no compiler object, backend state, or native resource identity.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct GlslDialectReport {
    entry_path: String,
    declared_version: Option<u32>,
    gaps: BTreeSet<GlslDialectGap>,
}

impl GlslDialectReport {
    pub fn entry_path(&self) -> &str {
        &self.entry_path
    }

    pub fn declared_version(&self) -> Option<u32> {
        self.declared_version
    }

    pub fn gaps(&self) -> &BTreeSet<GlslDialectGap> {
        &self.gaps
    }

    pub fn requires_lowering(&self) -> bool {
        !self.gaps.is_empty()
    }

    /// A later source-lowering implementation must call this before it hands
    /// source to any backend compiler. Reporting the missing dialect work is
    /// preferable to presenting a legacy OpenGL shader as Vulkan GLSL.
    pub fn require_backend_neutral_lowering(&self) -> GalResult<()> {
        if self.gaps.is_empty() {
            return Ok(());
        }
        Err(GalError::unsupported_feature(format!(
            "shader source '{}' requires explicit dialect lowering: {}",
            self.entry_path,
            self.gaps
                .iter()
                .map(|gap| gap.semantic_name())
                .collect::<Vec<_>>()
                .join(", ")
        )))
    }
}

/// Inspects complete preprocessed shader text. Comments are removed before
/// token analysis so a pack's documentation cannot change admission.
pub fn analyze_glsl_dialect(source: &PreprocessedShaderSource) -> GlslDialectReport {
    analyze_glsl_text(source.entry_path(), source.expanded_source())
}

/// Inspects owned GLSL text after a source-lowering step. The caller must
/// retain the source entry identity; this helper never accepts a backend
/// compiler object, program, or native resource.
pub(crate) fn analyze_glsl_text(entry_path: &str, source: &str) -> GlslDialectReport {
    let stripped = strip_comments(source);
    let mut gaps = BTreeSet::new();
    let declared_version = shader_version(&stripped);
    if declared_version.is_none_or(|version| version < 450) {
        gaps.insert(GlslDialectGap::PreVulkanGlslVersion);
    }
    let tokens = source_identifiers(&stripped);
    if ["texture2D", "texture3D", "textureCube"]
        .iter()
        .any(|builtin| contains_function_call(&stripped, builtin))
    {
        gaps.insert(GlslDialectGap::CompatibilityTextureBuiltin);
    }
    if tokens.contains("gl_FragData") {
        gaps.insert(GlslDialectGap::CompatibilityFragmentOutputs);
    }
    if ["ftransform", "gl_ModelViewMatrix", "gl_ProjectionMatrix"]
        .iter()
        .any(|token| tokens.contains(*token))
    {
        gaps.insert(GlslDialectGap::FixedFunctionVertexTransform);
    }
    if tokens.contains("gl_Fog") {
        gaps.insert(GlslDialectGap::CompatibilityFogParameters);
    }
    if [
        "attribute",
        "varying",
        "gl_Color",
        "gl_Vertex",
        "gl_MultiTexCoord0",
    ]
    .iter()
    .any(|token| tokens.contains(*token))
    {
        gaps.insert(GlslDialectGap::CompatibilityVertexAttributes);
    }
    GlslDialectReport {
        entry_path: entry_path.to_string(),
        declared_version,
        gaps,
    }
}

/// `texture2D` is a valid GLSL 450 opaque texture type, while
/// `texture2D(...)` is a legacy sampling builtin. Token membership alone
/// cannot distinguish them after source lowering has split combined samplers.
fn contains_function_call(source: &str, name: &str) -> bool {
    let bytes = source.as_bytes();
    let mut start = 0;
    while let Some(relative) = source[start..].find(name) {
        let index = start + relative;
        let before = index.checked_sub(1).and_then(|offset| bytes.get(offset));
        let after_index = index + name.len();
        let after = bytes.get(after_index);
        let identifier_boundary = before
            .is_none_or(|byte| !(*byte == b'_' || byte.is_ascii_alphanumeric()))
            && after.is_none_or(|byte| !(*byte == b'_' || byte.is_ascii_alphanumeric()));
        if identifier_boundary {
            let mut cursor = after_index;
            while bytes.get(cursor).is_some_and(u8::is_ascii_whitespace) {
                cursor += 1;
            }
            if bytes.get(cursor) == Some(&b'(') {
                return true;
            }
        }
        start = after_index;
    }
    false
}

fn shader_version(source: &str) -> Option<u32> {
    source.lines().find_map(|line| {
        let rest = line.trim_start().strip_prefix("#version")?.trim();
        rest.split_whitespace().next()?.parse().ok()
    })
}

/// Extracts source identifiers after comments have been discarded. This is a
/// source-analysis utility only: it does not infer driver locations, vertex
/// layouts, or backend bindings from identifier spelling.
pub(crate) fn source_identifiers(source: &str) -> BTreeSet<String> {
    let source = strip_comments(source);
    let mut identifiers = BTreeSet::new();
    let mut start = None;
    for (index, character) in source.char_indices() {
        if character == '_' || character.is_ascii_alphabetic() {
            if start.is_none() {
                start = Some(index);
            }
        } else if character.is_ascii_digit() && start.is_some() {
            continue;
        } else if let Some(start_index) = start.take() {
            identifiers.insert(source[start_index..index].to_string());
        }
    }
    if let Some(start_index) = start {
        identifiers.insert(source[start_index..].to_string());
    }
    identifiers
}

fn strip_comments(source: &str) -> String {
    let mut output = String::with_capacity(source.len());
    let mut characters = source.chars().peekable();
    let mut block_comment = false;
    while let Some(character) = characters.next() {
        if block_comment {
            if character == '*' && characters.peek() == Some(&'/') {
                characters.next();
                block_comment = false;
            } else if character == '\n' {
                output.push('\n');
            }
            continue;
        }
        if character == '/' && characters.peek() == Some(&'*') {
            characters.next();
            block_comment = true;
            continue;
        }
        if character == '/' && characters.peek() == Some(&'/') {
            characters.next();
            for character in characters.by_ref() {
                if character == '\n' {
                    output.push('\n');
                    break;
                }
            }
            continue;
        }
        output.push(character);
    }
    output
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::preprocess::{preprocess_artifact, PreprocessInput};
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};

    fn artifact(source: &str) -> PreprocessedShaderSource {
        let pack = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new("program.glsl", source)],
        )
        .unwrap();
        preprocess_artifact(PreprocessInput {
            source: &pack,
            entry: "program.glsl",
            defines: &[],
        })
        .unwrap()
    }

    #[test]
    fn compatibility_features_are_reported_before_backend_compilation() {
        let report = analyze_glsl_dialect(&artifact(
            "#version 130\nattribute vec4 pos; varying vec2 uv; void main() { gl_Position = ftransform(); gl_FragData[0] = texture2D(tex, uv); }",
        ));
        assert_eq!(Some(130), report.declared_version());
        assert_eq!(
            BTreeSet::from([
                GlslDialectGap::PreVulkanGlslVersion,
                GlslDialectGap::CompatibilityTextureBuiltin,
                GlslDialectGap::CompatibilityFragmentOutputs,
                GlslDialectGap::FixedFunctionVertexTransform,
                GlslDialectGap::CompatibilityVertexAttributes,
            ]),
            *report.gaps()
        );
        assert!(report.require_backend_neutral_lowering().is_err());
    }

    #[test]
    fn modern_source_is_not_rejected_by_comment_tokens() {
        let report = analyze_glsl_dialect(&artifact(
            "#version 450\n// texture2D gl_FragData ftransform attribute\nvoid main() {}",
        ));
        assert_eq!(Some(450), report.declared_version());
        assert!(report.gaps().is_empty());
        assert!(report.require_backend_neutral_lowering().is_ok());
    }

    #[test]
    fn modern_separate_texture_type_is_not_a_legacy_sampling_builtin() {
        let report = analyze_glsl_dialect(&artifact(
            "#version 450\nlayout(set = 1, binding = 0) uniform texture2D atlas_image;\nlayout(set = 1, binding = 1) uniform sampler atlas_sampler;\nvoid main() { vec4 color = texture(sampler2D(atlas_image, atlas_sampler), vec2(0.0)); }",
        ));
        assert!(report.gaps().is_empty());

        let legacy = analyze_glsl_dialect(&artifact(
            "#version 450\nvoid main() { vec4 color = texture2D(atlas, vec2(0.0)); }",
        ));
        assert!(legacy
            .gaps()
            .contains(&GlslDialectGap::CompatibilityTextureBuiltin));
    }

    #[test]
    fn legacy_fog_parameters_remain_an_explicit_semantic_blocker() {
        let report = analyze_glsl_dialect(&artifact(
            "#version 450\nvoid main() { float fog = (1.0 - gl_Fog.start) * gl_Fog.scale; }",
        ));
        assert_eq!(Some(450), report.declared_version());
        assert_eq!(
            &BTreeSet::from([GlslDialectGap::CompatibilityFogParameters]),
            report.gaps()
        );
        assert!(report
            .require_backend_neutral_lowering()
            .unwrap_err()
            .to_string()
            .contains("compatibility_fog_parameters"));
    }
}

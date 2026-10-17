//! Scoped pack directives for CPU-derived built-in frame uniforms.

use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
use crate::render::shaderpack::source::{
    preprocess::preprocess_artifact_with_runtime_options, ShaderPackSource,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use std::sync::OnceLock;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ShaderPackFrameUniformPolicy {
    pub generation: u64,
    pub sun_path_rotation_degrees: f32,
    pub eye_brightness_half_life_seconds: f32,
    pub end_flash_shadows: bool,
}

/// Immutable generation data is derived once per world scope. The memo does
/// not participate in source identity and retains neither expanded shaders
/// nor per-frame values.
#[derive(Clone, Debug, Default)]
pub(crate) struct FrameUniformPolicies([OnceLock<GalResult<ShaderPackFrameUniformPolicy>>; 4]);
impl PartialEq for FrameUniformPolicies {
    fn eq(&self, _: &Self) -> bool {
        true
    }
}
impl Eq for FrameUniformPolicies {}
impl FrameUniformPolicies {
    pub(crate) fn get(
        &self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) -> GalResult<ShaderPackFrameUniformPolicy> {
        let index = match scope {
            TerrainProgramScope::Default => 0,
            TerrainProgramScope::Overworld => 1,
            TerrainProgramScope::Nether => 2,
            TerrainProgramScope::End => 3,
        };
        self.0[index]
            .get_or_init(|| ShaderPackFrameUniformPolicy::from_source(source, scope))
            .clone()
    }
}

impl ShaderPackFrameUniformPolicy {
    pub fn from_source(source: &ShaderPackSource, scope: TerrainProgramScope) -> GalResult<Self> {
        let mut policy = Self {
            generation: source.generation(),
            sun_path_rotation_degrees: 0.0,
            eye_brightness_half_life_seconds: 1.0,
            end_flash_shadows: false,
        };
        let scan = super::directives::visit_fragment_directives(source, scope, true, |artifact| {
            policy.read_constants(artifact.expanded_source(), artifact.defines())
        })?;
        let property_defines = scan.property_defines;
        if source.get("shaders.properties").is_some() {
            let references = property_defines
                .iter()
                .map(|(name, value)| (name.as_str(), value.as_str()))
                .collect::<Vec<_>>();
            let properties = preprocess_artifact_with_runtime_options(
                source,
                "shaders.properties",
                &references,
            )?;
            for line in properties.expanded_source().lines() {
                let Some((name, value)) = line.trim().split_once('=') else {
                    continue;
                };
                if name.trim() != "endFlashShadows" {
                    continue;
                }
                policy.end_flash_shadows = match value.trim() {
                    "true" => true,
                    "false" => false,
                    _ => {
                        return Err(GalError::invalid_argument(
                            "endFlashShadows requires a resolved boolean",
                        ))
                    }
                };
            }
        }
        Ok(policy)
    }

    fn read_constants(&mut self, text: &str, defines: &[(String, String)]) -> GalResult<()> {
        for line in text.lines() {
            let Some(line) = line.trim_start().strip_prefix("const") else {
                continue;
            };
            if !line.starts_with(char::is_whitespace) {
                continue;
            }
            let Some(line) = line.trim_start().strip_prefix("float") else {
                continue;
            };
            if !line.starts_with(char::is_whitespace) {
                continue;
            }
            let Some((name, value)) = line.trim_start().split_once('=') else {
                continue;
            };
            let name = name.trim();
            if !matches!(name, "sunPathRotation" | "eyeBrightnessHalflife") {
                continue;
            }
            let Some((value, _)) = value.split_once(';') else {
                return Err(GalError::invalid_argument(
                    "frame uniform directive lacks semicolon",
                ));
            };
            let mut value = value.trim();
            for _ in 0..32 {
                let Some((_, replacement)) = defines.iter().find(|(key, _)| key == value) else {
                    break;
                };
                value = replacement;
            }
            let value: f32 = value.trim_end_matches(['f', 'F']).parse().map_err(|_| {
                GalError::invalid_argument(format!(
                    "frame uniform directive {name} requires a resolved float literal"
                ))
            })?;
            if !value.is_finite() || (name == "eyeBrightnessHalflife" && value < 0.0) {
                return Err(GalError::invalid_argument(format!(
                    "invalid frame uniform directive {name}"
                )));
            }
            // Frozen processes declarations in program order; later ones win.
            if name == "sunPathRotation" {
                self.sun_path_rotation_degrees = value;
            } else {
                self.eye_brightness_half_life_seconds = value * 0.1;
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests;

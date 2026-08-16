//! Source-defined scalar-uniform declarations.
//!
//! A GLSL uniform declaration alone does not mean Iris writes it. Shader-pack
//! `uniform.float.*` properties define the custom uniforms that are actually
//! populated. Rust keeps that fact with the immutable source generation so a
//! missing custom evaluator cannot be mistaken for a meaningful zero value.

use std::collections::BTreeSet;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct ShaderPackCustomUniformPolicy {
    generation: u64,
    float_uniforms: BTreeSet<String>,
}

impl ShaderPackCustomUniformPolicy {
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let mut float_uniforms = BTreeSet::new();
        if let Some(properties) = source.get("shaders.properties") {
            for (line_number, raw_line) in properties.lines().enumerate() {
                let line = raw_line.trim();
                if line.is_empty()
                    || line.starts_with('#')
                    || line.starts_with('!')
                    || line.starts_with("//")
                {
                    continue;
                }
                let Some((key, _expression)) = line.split_once('=') else {
                    continue;
                };
                let Some(name) = key.trim().strip_prefix("uniform.float.") else {
                    continue;
                };
                if name.is_empty()
                    || !name
                        .bytes()
                        .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
                {
                    return Err(GalError::invalid_argument(format!(
                        "shaders.properties line {} has an invalid custom float uniform name",
                        line_number + 1,
                    )));
                }
                float_uniforms.insert(name.to_string());
            }
        }
        Ok(Self {
            generation: source.generation(),
            float_uniforms,
        })
    }

    pub fn generation(&self) -> u64 {
        self.generation
    }

    pub fn declares_float(&self, name: &str) -> bool {
        self.float_uniforms.contains(name)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    #[test]
    fn observes_only_active_property_declarations() {
        let source = ShaderPackSource::new(
            "custom-uniforms",
            9,
            vec![ShaderSourceFile::new(
                "shaders.properties",
                "uniform.float.inRainy=smooth(1, 1, 2, 2)\n//uniform.float.inPaleGarden=1\n",
            )],
        )
        .unwrap();
        let policy = ShaderPackCustomUniformPolicy::from_source(&source).unwrap();
        assert_eq!(9, policy.generation());
        assert!(policy.declares_float("inRainy"));
        assert!(!policy.declares_float("inPaleGarden"));
    }
}

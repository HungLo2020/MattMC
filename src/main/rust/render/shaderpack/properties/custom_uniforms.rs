//! Source-defined scalar-uniform declarations.
//!
//! A GLSL uniform declaration alone does not mean Iris writes it. Shader-pack
//! `uniform.float.*` properties define the custom uniforms that are actually
//! populated. Rust keeps that fact with the immutable source generation so a
//! missing custom evaluator cannot be mistaken for a meaningful zero value.

use std::collections::{BTreeMap, BTreeSet};

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::source::ShaderPackSource;

/// One active custom property after the source's option/conditional expansion.
/// Expressions remain source data until the uniform catalog links the inputs.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct CustomUniformDefinition {
    pub ty: String,
    pub expression: String,
    pub uniform: bool,
}

pub fn definitions_from_preprocessed(
    properties: &str,
) -> GalResult<BTreeMap<String, CustomUniformDefinition>> {
    const MAX_DEFINITIONS: usize = 256;
    const MAX_EXPRESSION_BYTES: usize = 8192;
    const MAX_TOTAL_BYTES: usize = 128 * 1024;
    let mut definitions = BTreeMap::new();
    let mut logical = String::new();
    let mut bytes = 0usize;
    for raw in properties.lines() {
        let line = raw.trim();
        if logical.is_empty()
            && (line.is_empty()
                || line.starts_with('#')
                || line.starts_with('!')
                || line.starts_with("//"))
        {
            continue;
        }
        let continued = line.ends_with('\\');
        logical.push_str(if continued {
            &line[..line.len() - 1]
        } else {
            line
        });
        logical.push(' ');
        if logical.len() > MAX_TOTAL_BYTES {
            return Err(GalError::invalid_argument(
                "custom uniform property exceeds byte budget",
            ));
        }
        if continued {
            continue;
        }
        if let Some((key, expression)) = logical.split_once('=') {
            let key = key.trim();
            let property = key
                .strip_prefix("uniform.")
                .map(|rest| (rest, true))
                .or_else(|| key.strip_prefix("variable.").map(|rest| (rest, false)));
            if let Some((property, uniform)) = property {
                let (ty, name) = property.split_once('.').ok_or_else(|| {
                    GalError::invalid_argument("malformed custom uniform property")
                })?;
                if !name
                    .as_bytes()
                    .first()
                    .is_some_and(|byte| byte.is_ascii_alphabetic() || *byte == b'_')
                    || !name
                        .bytes()
                        .all(|byte| byte.is_ascii_alphanumeric() || byte == b'_')
                {
                    return Err(GalError::invalid_argument(
                        "invalid custom uniform property name",
                    ));
                }
                let expression = expression.trim();
                bytes = bytes.checked_add(expression.len()).ok_or_else(|| {
                    GalError::invalid_argument("custom uniform byte count overflow")
                })?;
                if expression.is_empty()
                    || expression.len() > MAX_EXPRESSION_BYTES
                    || bytes > MAX_TOTAL_BYTES
                    || definitions.len() >= MAX_DEFINITIONS
                {
                    return Err(GalError::invalid_argument(
                        "custom uniform expression exceeds declaration/byte budget or is empty",
                    ));
                }
                let definition = CustomUniformDefinition {
                    ty: ty.to_string(),
                    expression: expression.to_string(),
                    uniform,
                };
                if definitions.insert(name.to_string(), definition).is_some() {
                    return Err(GalError::invalid_argument(format!(
                        "duplicate custom uniform property '{name}'"
                    )));
                }
            }
        }
        logical.clear();
    }
    if !logical.is_empty() {
        return Err(GalError::invalid_argument(
            "unterminated custom uniform property continuation",
        ));
    }
    Ok(definitions)
}

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
    use crate::render::shaderpack::properties::custom_uniforms::*;
    use crate::render::shaderpack::source::ShaderSourceFile;

    #[test]
    fn bounds_custom_property_storage_and_rejects_duplicate_definitions() {
        assert!(
            definitions_from_preprocessed("uniform.float.value=1\nvariable.float.value=2").is_err()
        );
        assert!(definitions_from_preprocessed("uniform.float.9value=1").is_err());
        assert!(definitions_from_preprocessed("uniform.float.value=").is_err());
        assert!(definitions_from_preprocessed(&format!(
            "uniform.float.value={}",
            "1".repeat(8193)
        ))
        .is_err());
        let too_many = (0..257)
            .map(|i| format!("variable.float.value{i}=1\n"))
            .collect::<String>();
        assert!(definitions_from_preprocessed(&too_many).is_err());
        let too_large = (0..128)
            .map(|i| format!("variable.float.value{i}={}\n", "1".repeat(2048)))
            .collect::<String>();
        assert!(definitions_from_preprocessed(&too_large).is_err());
    }

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

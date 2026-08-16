//! Source-derived surface-wetness timing policy.
//!
//! Iris consumes `wetnessHalflife` and `drynessHalflife` as shader-pack
//! directives.  Rust keeps the parsed values as immutable generation data;
//! the temporal smoothing itself remains in `source_temporal`.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::source::ShaderPackSource;

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ShaderPackWetnessPolicy {
    generation: u64,
    wetness_half_life_deciseconds: f32,
    dryness_half_life_deciseconds: f32,
}

impl ShaderPackWetnessPolicy {
    // Iris's PackDirectives defaults when a pack does not declare either
    // directive.  These are source semantics, not renderer defaults.
    const DEFAULT_WETNESS_HALF_LIFE_DECISECONDS: f32 = 600.0;
    const DEFAULT_DRYNESS_HALF_LIFE_DECISECONDS: f32 = 200.0;

    pub fn from_source(source: &ShaderPackSource) -> GalResult<Self> {
        let mut wetness = None;
        let mut dryness = None;
        for file in source.files() {
            for (line_number, raw_line) in file.contents.lines().enumerate() {
                let line = raw_line.split("//").next().unwrap_or("").trim();
                let Some((left, right)) = line.split_once('=') else {
                    continue;
                };
                let mut left = left.split_whitespace();
                if left.next() != Some("const") || left.next() != Some("float") {
                    continue;
                }
                let Some(name) = left.next() else {
                    continue;
                };
                if left.next().is_some() || !matches!(name, "wetnessHalflife" | "drynessHalflife") {
                    continue;
                }
                let value = right
                    .trim()
                    .trim_end_matches(';')
                    .trim()
                    .parse::<f32>()
                    .map_err(|_| {
                        GalError::invalid_argument(format!(
                            "{} line {} has a non-literal {name} directive",
                            file.path,
                            line_number + 1,
                        ))
                    })?;
                if !value.is_finite() || value < 0.0 {
                    return Err(GalError::invalid_argument(format!(
                        "{} line {} has an invalid {name} directive",
                        file.path,
                        line_number + 1,
                    )));
                }
                let slot = if name == "wetnessHalflife" {
                    &mut wetness
                } else {
                    &mut dryness
                };
                if let Some(previous) = *slot {
                    if previous != value {
                        return Err(GalError::invalid_argument(format!(
                            "shader-pack source declares conflicting {name} values"
                        )));
                    }
                } else {
                    *slot = Some(value);
                }
            }
        }
        Ok(Self {
            generation: source.generation(),
            wetness_half_life_deciseconds: wetness
                .unwrap_or(Self::DEFAULT_WETNESS_HALF_LIFE_DECISECONDS),
            dryness_half_life_deciseconds: dryness
                .unwrap_or(Self::DEFAULT_DRYNESS_HALF_LIFE_DECISECONDS),
        })
    }

    pub fn generation(self) -> u64 {
        self.generation
    }

    pub fn wetness_half_life_seconds(self) -> f32 {
        self.wetness_half_life_deciseconds / 10.0
    }

    pub fn dryness_half_life_seconds(self) -> f32 {
        self.dryness_half_life_deciseconds / 10.0
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    fn source(contents: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "wetness-policy",
            7,
            vec![ShaderSourceFile::new("lib/pipeline.glsl", contents)],
        )
        .unwrap()
    }

    #[test]
    fn reads_source_directives_and_uses_iris_defaults_when_absent() {
        let explicit = ShaderPackWetnessPolicy::from_source(&source(
            "const float wetnessHalflife = 300.0;\nconst float drynessHalflife = 150.0;",
        ))
        .unwrap();
        assert_eq!(7, explicit.generation());
        assert_eq!(30.0, explicit.wetness_half_life_seconds());
        assert_eq!(15.0, explicit.dryness_half_life_seconds());

        let defaults = ShaderPackWetnessPolicy::from_source(&source("void main() {}")).unwrap();
        assert_eq!(60.0, defaults.wetness_half_life_seconds());
        assert_eq!(20.0, defaults.dryness_half_life_seconds());
    }

    #[test]
    fn rejects_ambiguous_or_invalid_directives() {
        assert!(ShaderPackWetnessPolicy::from_source(&source(
            "const float wetnessHalflife = 1.0;\nconst float wetnessHalflife = 2.0;",
        ))
        .is_err());
        assert!(ShaderPackWetnessPolicy::from_source(&source(
            "const float drynessHalflife = -1.0;",
        ))
        .is_err());
    }
}

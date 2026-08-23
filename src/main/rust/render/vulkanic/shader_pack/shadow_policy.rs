//! Source-derived shadow-camera semantics for shader-pack uniform preparation.
//!
//! This module mirrors the documented Iris shadow matrix rules from pack
//! directives and copied gameplay values. It owns no Java/Iris renderer state,
//! shader object, or backend resource. End flash support is selected only by
//! the pack's explicit `endFlashShadows` property and copied angle semantics.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::preprocess::preprocess_artifact_with_runtime_options;
use super::source::ShaderPackSource;
use super::terrain_contract::TerrainProgramScope;
use super::voxel_light_volume::invert_column_major_mat4;

const DEFAULT_SHADOW_DISTANCE: f32 = 160.0;
const DEFAULT_SHADOW_NEAR_PLANE: f32 = -100.05;
const DEFAULT_SHADOW_FAR_PLANE: f32 = 156.0;
const DEFAULT_SHADOW_INTERVAL: f32 = 2.0;
const DEFAULT_SUN_PATH_ROTATION_DEGREES: f32 = 0.0;

/// Immutable pack-generation shadow directives. These are source semantics,
/// not a cached Java matrix or an OpenGL/Vulkan implementation detail.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ShaderPackShadowPolicy {
    generation: u64,
    distance: f32,
    near_plane: f32,
    far_plane: f32,
    interval_size: f32,
    sun_path_rotation_degrees: f32,
    supports_end_flash: bool,
}

/// One complete ordinary-world shadow uniform set. Matrices use the same
/// column-major convention as the copied world matrices and GLSL mat4 values.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct ShaderPackShadowUniforms {
    pub model_view: [f32; 16],
    pub model_view_inverse: [f32; 16],
    pub projection: [f32; 16],
    pub projection_inverse: [f32; 16],
}

impl ShaderPackShadowPolicy {
    /// Parses the source generation's ordinary-world directives. A missing
    /// common source means this policy is not applicable; it is not a default
    /// to a guessed source contract.
    pub fn from_source(source: &ShaderPackSource) -> GalResult<Option<Self>> {
        if source.get("lib/common.glsl").is_none() {
            return Ok(None);
        }
        let artifact = preprocess_artifact_with_runtime_options(source, "lib/common.glsl", &[])?;
        let common = artifact.expanded_source();
        let policy = Self {
            generation: source.generation(),
            distance: source_float_constant(&common, "shadowDistance")?
                .unwrap_or(DEFAULT_SHADOW_DISTANCE),
            near_plane: source_float_constant(&common, "shadowNearPlane")?
                .unwrap_or(DEFAULT_SHADOW_NEAR_PLANE),
            far_plane: source_float_constant(&common, "shadowFarPlane")?
                .unwrap_or(DEFAULT_SHADOW_FAR_PLANE),
            interval_size: source_float_constant(&common, "shadowIntervalSize")?
                .unwrap_or(DEFAULT_SHADOW_INTERVAL),
            sun_path_rotation_degrees: source_float_constant(&common, "sunPathRotation")?
                .unwrap_or(DEFAULT_SUN_PATH_ROTATION_DEGREES),
            supports_end_flash: source_bool_property(source, "endFlashShadows")?.unwrap_or(false),
        };
        policy.validate()?;
        Ok(Some(policy))
    }

    pub fn generation(self) -> u64 {
        self.generation
    }

    /// Source-defined celestial path rotation shared by the owned shadow and
    /// sky transforms. This exposes a pack semantic only; it carries no Iris
    /// pipeline, renderer, or backend state.
    pub fn sun_path_rotation_degrees(self) -> f32 {
        self.sun_path_rotation_degrees
    }

    /// Derives shadow uniforms from semantic frame inputs. End uses the
    /// ordinary celestial transform unless the pack explicitly opts into its
    /// copied End flash branch via `uniforms_with_end_flash`.
    pub fn uniforms(
        self,
        scope: TerrainProgramScope,
        time_of_day: f32,
        camera_world_position: [f32; 3],
    ) -> GalResult<ShaderPackShadowUniforms> {
        self.uniforms_with_end_flash(scope, time_of_day, camera_world_position, None)
    }

    /// Derives shadow uniforms with the copied End flash angles. Packs only
    /// enter the End branch when they explicitly opt into `endFlashShadows`;
    /// other End packs retain Iris's ordinary celestial shadow transform.
    pub fn uniforms_with_end_flash(
        self,
        scope: TerrainProgramScope,
        time_of_day: f32,
        camera_world_position: [f32; 3],
        end_flash_angles: Option<[f32; 2]>,
    ) -> GalResult<ShaderPackShadowUniforms> {
        if !time_of_day.is_finite()
            || camera_world_position
                .iter()
                .any(|coordinate| !coordinate.is_finite())
        {
            return Err(GalError::invalid_argument(
                "source shadow matrix inputs must be finite",
            ));
        }
        let model_view = if scope == TerrainProgramScope::End && self.supports_end_flash {
            let angles = end_flash_angles.ok_or_else(|| {
                GalError::unsupported_feature(
                    "End shadow support requires copied End flash angles",
                )
            })?;
            if angles.iter().any(|value| !value.is_finite()) {
                return Err(GalError::invalid_argument(
                    "End flash shadow angles must be finite",
                ));
            }
            end_shadow_model_view(angles[0], angles[1], self.interval_size, camera_world_position)
        } else {
            let sun_angle = source_sun_angle(time_of_day);
            let shadow_angle = if sun_angle <= 0.5 {
                sun_angle
            } else {
                sun_angle - 0.5
            };
            shadow_model_view(
                shadow_angle,
                self.sun_path_rotation_degrees,
                self.interval_size,
                camera_world_position,
            )
        };
        let projection = shadow_ortho_projection(self.distance, self.near_plane, self.far_plane);
        Ok(ShaderPackShadowUniforms {
            model_view,
            model_view_inverse: invert_column_major_mat4(model_view, "source shadow model view")?,
            projection,
            projection_inverse: invert_column_major_mat4(projection, "source shadow projection")?,
        })
    }

    fn validate(self) -> GalResult<()> {
        for (label, value) in [
            ("shadow distance", self.distance),
            ("shadow near plane", self.near_plane),
            ("shadow far plane", self.far_plane),
            ("shadow interval size", self.interval_size),
            ("sun path rotation", self.sun_path_rotation_degrees),
        ] {
            if !value.is_finite() {
                return Err(GalError::invalid_argument(format!(
                    "source {label} must be finite"
                )));
            }
        }
        if self.distance <= 0.0 {
            return Err(GalError::invalid_argument(
                "source shadow distance must be positive",
            ));
        }
        if (self.near_plane - self.far_plane).abs() <= f32::EPSILON {
            return Err(GalError::invalid_argument(
                "source shadow near and far planes must differ",
            ));
        }
        Ok(())
    }
}

fn source_bool_property(source: &ShaderPackSource, key: &str) -> GalResult<Option<bool>> {
    let Some(properties) = source.get("shaders.properties") else {
        return Ok(None);
    };
    let mut result = None;
    for (line_number, raw_line) in properties.lines().enumerate() {
        let line = raw_line.split_once('#').map_or(raw_line, |(code, _)| code).trim();
        let Some((name, value)) = line.split_once('=') else { continue; };
        if name.trim() != key { continue; }
        if result.is_some() {
            return Err(GalError::invalid_argument(format!(
                "shaders.properties declares {key} more than once"
            )));
        }
        result = Some(match value.trim() {
            "true" => true,
            "false" => false,
            other => return Err(GalError::invalid_argument(format!(
                "shaders.properties {key} line {} must be true or false, got {other}",
                line_number + 1
            ))),
        });
    }
    Ok(result)
}

fn source_float_constant(source: &str, name: &str) -> GalResult<Option<f32>> {
    let declaration = format!("const float {name}");
    let mut value = None;
    for (line_number, raw_line) in source.lines().enumerate() {
        // Shader-pack directive lists conventionally trail the declaration
        // with a line comment. Preprocessing has already selected branches;
        // this parser needs only the declaration token sequence.
        let line = raw_line
            .split_once("//")
            .map_or(raw_line, |(code, _)| code)
            .trim();
        if !line.starts_with(&declaration) {
            continue;
        }
        let remainder = line[declaration.len()..].trim_start();
        let Some(expression) = remainder
            .strip_prefix('=')
            .and_then(|value| value.trim().strip_suffix(';'))
        else {
            return Err(GalError::invalid_argument(format!(
                "source {name} declaration on line {} must be one float literal",
                line_number + 1
            )));
        };
        let parsed = expression.trim().parse::<f32>().map_err(|_| {
            GalError::invalid_argument(format!(
                "source {name} declaration on line {} is not a float literal",
                line_number + 1
            ))
        })?;
        if !parsed.is_finite() {
            return Err(GalError::invalid_argument(format!(
                "source {name} declaration on line {} must be finite",
                line_number + 1
            )));
        }
        if value.replace(parsed).is_some() {
            return Err(GalError::invalid_argument(format!(
                "source declares {name} more than once after preprocessing"
            )));
        }
    }
    Ok(value)
}

fn source_sun_angle(sky_angle: f32) -> f32 {
    if sky_angle < 0.75 {
        sky_angle + 0.25
    } else {
        sky_angle - 0.75
    }
}

fn shadow_model_view(
    shadow_angle: f32,
    sun_path_rotation_degrees: f32,
    interval_size: f32,
    camera: [f32; 3],
) -> [f32; 16] {
    let sky_angle = if shadow_angle < 0.25 {
        shadow_angle + 0.75
    } else {
        shadow_angle - 0.25
    };
    let mut result = identity();
    result = multiply(result, rotation_x(90.0));
    result = multiply(result, rotation_z(sky_angle * -360.0));
    result = multiply(result, rotation_x(sun_path_rotation_degrees));
    if interval_size.abs() > 0.0 {
        // Java intentionally uses f32 remainder, which preserves a negative
        // remainder for negative camera coordinates.
        let offset = camera.map(|coordinate| coordinate % interval_size - interval_size / 2.0);
        result = multiply(result, translation(offset));
    }
    result
}

fn end_shadow_model_view(
    x_angle: f32,
    y_angle: f32,
    interval_size: f32,
    camera: [f32; 3],
) -> [f32; 16] {
    let mut result = identity();
    result = multiply(result, rotation_x(-x_angle));
    result = multiply(result, rotation_y(y_angle));
    if interval_size.abs() > 0.0 {
        let offset = camera.map(|coordinate| coordinate % interval_size - interval_size / 2.0);
        result = multiply(result, translation(offset));
    }
    result
}

fn shadow_ortho_projection(distance: f32, near_plane: f32, far_plane: f32) -> [f32; 16] {
    let depth = near_plane - far_plane;
    [
        distance.recip(),
        0.0,
        0.0,
        0.0,
        0.0,
        distance.recip(),
        0.0,
        0.0,
        0.0,
        0.0,
        2.0 / depth,
        0.0,
        0.0,
        0.0,
        (far_plane + near_plane) / depth,
        1.0,
    ]
}

fn identity() -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn translation(offset: [f32; 3]) -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, offset[0], offset[1],
        offset[2], 1.0,
    ]
}

fn rotation_x(degrees: f32) -> [f32; 16] {
    let (sine, cosine) = degrees.to_radians().sin_cos();
    [
        1.0, 0.0, 0.0, 0.0, 0.0, cosine, sine, 0.0, 0.0, -sine, cosine, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn rotation_z(degrees: f32) -> [f32; 16] {
    let (sine, cosine) = degrees.to_radians().sin_cos();
    [
        cosine, sine, 0.0, 0.0, -sine, cosine, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

fn rotation_y(degrees: f32) -> [f32; 16] {
    let (sine, cosine) = degrees.to_radians().sin_cos();
    [
        cosine, 0.0, -sine, 0.0, 0.0, 1.0, 0.0, 0.0, sine, 0.0, cosine, 0.0, 0.0, 0.0, 0.0,
        1.0,
    ]
}

fn multiply(left: [f32; 16], right: [f32; 16]) -> [f32; 16] {
    std::array::from_fn(|index| {
        let column = index / 4;
        let row = index % 4;
        (0..4)
            .map(|inner| left[inner * 4 + row] * right[column * 4 + inner])
            .sum()
    })
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;

    fn source(common: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "shadow-policy",
            9,
            vec![ShaderSourceFile::new("lib/common.glsl", common)],
        )
        .unwrap()
    }

    fn assert_close(actual: [f32; 16], expected: [f32; 16]) {
        for (index, (actual, expected)) in actual.iter().zip(expected).enumerate() {
            assert!(
                (actual - expected).abs() < 0.0005,
                "matrix index {index}: expected {expected}, got {actual}"
            );
        }
    }

    #[test]
    fn derives_ordinary_shadow_matrices_from_preprocessed_source() {
        let policy = ShaderPackShadowPolicy::from_source(&source(
            "const float shadowDistance = 32.0;\nconst float shadowNearPlane = 0.05;\nconst float shadowFarPlane = 256.0;\nconst float shadowIntervalSize = 2.0;\nconst float sunPathRotation = 0.0;\n",
        ))
        .unwrap()
        .unwrap();
        let uniforms = policy
            .uniforms(TerrainProgramScope::Overworld, 0.0, [0.0, 0.0, 0.0])
            .unwrap();
        assert_eq!(9, policy.generation());
        assert_close(
            uniforms.projection,
            [
                0.03125,
                0.0,
                0.0,
                0.0,
                0.0,
                0.03125,
                0.0,
                0.0,
                0.0,
                0.0,
                -0.007814026,
                0.0,
                0.0,
                0.0,
                -1.0003906,
                1.0,
            ],
        );
        assert_close(
            multiply(uniforms.model_view, uniforms.model_view_inverse),
            identity(),
        );
    }

    #[test]
    fn applies_the_active_source_generation_runtime_options() {
        let default = ShaderPackShadowPolicy::from_source(&source(
            "#ifdef SOURCE_ROTATION\nconst float sunPathRotation = 30.0;\n#else\nconst float sunPathRotation = 0.0;\n#endif\n",
        ))
        .unwrap()
        .unwrap();
        let configured_source = ShaderPackSource::new(
            "shadow-policy-options",
            10,
            vec![
                ShaderSourceFile::new(
                    "lib/common.glsl",
                    "#ifdef SOURCE_ROTATION\nconst float sunPathRotation = 30.0;\n#else\nconst float sunPathRotation = 0.0;\n#endif\n",
                ),
                ShaderSourceFile::new(
                    "mattmc/runtime-options.properties",
                    "SOURCE_ROTATION=1\n",
                ),
            ],
        )
        .unwrap();
        let configured = ShaderPackShadowPolicy::from_source(&configured_source)
            .unwrap()
            .unwrap();
        assert_ne!(
            default
                .uniforms(TerrainProgramScope::Overworld, 0.0, [0.0, 0.0, 0.0])
                .unwrap()
                .model_view,
            configured
                .uniforms(TerrainProgramScope::Overworld, 0.0, [0.0, 0.0, 0.0])
                .unwrap()
                .model_view
        );
    }

    #[test]
    fn preserves_java_negative_remainder_grid_snapping() {
        let policy =
            ShaderPackShadowPolicy::from_source(&source("const float shadowIntervalSize = 2.0;\n"))
                .unwrap()
                .unwrap();
        let negative = policy
            .uniforms(TerrainProgramScope::Overworld, 0.0, [-1.0, 0.0, 0.0])
            .unwrap();
        let positive = policy
            .uniforms(TerrainProgramScope::Overworld, 0.0, [1.0, 0.0, 0.0])
            .unwrap();
        assert_ne!(negative.model_view, positive.model_view);
    }

    #[test]
    fn end_shadow_uses_copied_angles_only_when_pack_opts_in() {
        let policy = ShaderPackShadowPolicy::from_source(&source(""))
            .unwrap()
            .unwrap();
        assert!(policy
            .uniforms(TerrainProgramScope::End, 0.0, [0.0, 0.0, 0.0])
            .is_ok());

        let opted_in = ShaderPackSource::new(
            "end-shadow-policy",
            10,
            vec![
                ShaderSourceFile::new("lib/common.glsl", "const float shadowIntervalSize = 2.0;"),
                ShaderSourceFile::new("shaders.properties", "endFlashShadows=true\n"),
            ],
        )
        .unwrap();
        let opted_in = ShaderPackShadowPolicy::from_source(&opted_in)
            .unwrap()
            .unwrap();
        assert!(opted_in
            .uniforms_with_end_flash(TerrainProgramScope::End, 0.0, [0.0, 0.0, 0.0], None)
            .is_err());
        let uniforms = opted_in
            .uniforms_with_end_flash(
                TerrainProgramScope::End,
                0.0,
                [0.0, 0.0, 0.0],
                Some([15.0, 30.0]),
            )
            .unwrap();
        assert_close(
            uniforms.model_view,
            multiply(
                multiply(rotation_x(-15.0), rotation_y(30.0)),
                translation([-1.0, -1.0, -1.0]),
            ),
        );
    }

    #[test]
    fn rejects_malformed_or_duplicate_directives() {
        assert!(
            ShaderPackShadowPolicy::from_source(&source("const float shadowDistance = no;\n"))
                .is_err()
        );
        assert!(ShaderPackShadowPolicy::from_source(&source(
            "const float shadowDistance = 64.0;\nconst float shadowDistance = 32.0;\n"
        ))
        .is_err());
    }
}

//! Celestial view-space positions from immutable frame and pack semantics.

use crate::render::shaderpack::contracts::terrain::TerrainProgramScope;
use crate::render::shaderpack::properties::frame_uniforms::ShaderPackFrameUniformPolicy;
use crate::render::vulkanic::error::{GalError, GalResult};

#[derive(Clone, Copy, Debug, PartialEq)]
pub struct CelestialFrameUniforms {
    pub sun_position: [f32; 3],
    pub moon_position: [f32; 3],
    pub shadow_light_position: [f32; 3],
    pub up_position: [f32; 3],
}

impl CelestialFrameUniforms {
    pub fn from_frame(
        policy: ShaderPackFrameUniformPolicy,
        scope: TerrainProgramScope,
        time_of_day: f32,
        view: [f32; 16],
        end_flash_angles: Option<[f32; 2]>,
    ) -> GalResult<Self> {
        if !time_of_day.is_finite()
            || !(0.0..=1.0).contains(&time_of_day)
            || !policy.sun_path_rotation_degrees.is_finite()
            || view.iter().any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "celestial uniforms require finite camera/rotation and sky angle within [0, 1]",
            ));
        }
        let before_time = multiply(
            multiply(view, rotation(1, -90.0)),
            rotation(2, policy.sun_path_rotation_degrees),
        );
        let sun_position = position(multiply(before_time, rotation(0, time_of_day * 360.0)));
        let moon_position = sun_position.map(|value| -value);
        let sun_angle = if time_of_day < 0.75 {
            time_of_day + 0.25
        } else {
            time_of_day - 0.75
        };
        let shadow_light_position = if scope == TerrainProgramScope::End && policy.end_flash_shadows
        {
            let [pitch, yaw] = end_flash_angles.ok_or_else(|| {
                GalError::invalid_argument("celestial End flash requires copied angles")
            })?;
            if !pitch.is_finite() || !yaw.is_finite() {
                return Err(GalError::invalid_argument(
                    "celestial End flash angles must be finite",
                ));
            }
            position(multiply(
                multiply(view, rotation(1, 180.0 - yaw)),
                rotation(0, -90.0 - pitch),
            ))
        } else if sun_angle <= 0.5 {
            sun_position
        } else {
            moon_position
        };
        let up_position = position(multiply(view, rotation(1, -90.0)));
        Ok(Self {
            sun_position,
            moon_position,
            shadow_light_position,
            up_position,
        })
    }
}

fn position(matrix: [f32; 16]) -> [f32; 3] {
    // Frozen transforms (0,100,0,0). Camera translation has no contribution.
    [matrix[4] * 100.0, matrix[5] * 100.0, matrix[6] * 100.0]
}

fn multiply(left: [f32; 16], right: [f32; 16]) -> [f32; 16] {
    std::array::from_fn(|index| {
        (0..4)
            .map(|inner| left[inner * 4 + index % 4] * right[index / 4 * 4 + inner])
            .sum()
    })
}

fn rotation(axis: usize, degrees: f32) -> [f32; 16] {
    // Axis.rotationDegrees -> Quaternionf.rotationX/Y/Z -> Matrix4f.
    // Match JOML's float half-angle and cosFromSin, rather than substituting
    // a normalized sun direction or a shadow-camera matrix.
    let half = degrees * (core::f64::consts::PI / 180.0) as f32 * 0.5;
    let sine = (half as f64).sin() as f32;
    let mut cosine = (1.0 - sine * sine).sqrt();
    let angle = half + core::f32::consts::FRAC_PI_2;
    let mut wrapped =
        angle - (angle / core::f32::consts::TAU) as i32 as f32 * core::f32::consts::TAU;
    if wrapped < 0.0 {
        wrapped += core::f32::consts::TAU;
    }
    if wrapped >= core::f32::consts::PI {
        cosine = -cosine;
    }
    let diagonal = cosine * cosine - sine * sine;
    let unchanged = cosine * cosine + sine * sine;
    let cross = 2.0 * sine * cosine;
    let mut matrix = [0.0; 16];
    matrix[15] = 1.0;
    for i in 0..3 {
        matrix[i * 5] = if i == axis { unchanged } else { diagonal };
    }
    let (a, b) = match axis {
        0 => (1, 2),
        1 => (2, 0),
        2 => (0, 1),
        _ => unreachable!(),
    };
    matrix[a * 4 + b] = cross;
    matrix[b * 4 + a] = -cross;
    matrix
}

#[cfg(test)]
mod tests;

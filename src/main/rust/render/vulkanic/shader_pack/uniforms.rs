use crate::render::vulkanic::error::{GalError, GalResult};

/// Source-derived world inputs used by Complementary's normal terrain
/// lighting path. This is intentionally semantic: it names game/environment
/// values rather than Iris uniforms, GL locations, or backend resources.
///
/// The selected terrain route must provide one validated value for every
/// frame. Until that extraction exists, source-plan admission remains off.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct TerrainLightingEnvironment {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub frame_id: u64,
    pub camera_world_position: [f32; 3],
    pub time_of_day: f32,
    pub rain_strength: f32,
    pub thunder_strength: f32,
    pub vanilla_brightness: f32,
    pub sun_direction: [f32; 3],
    pub up_direction: [f32; 3],
    pub block_light_color: [f32; 3],
    pub scene_light_color: [f32; 3],
}

impl TerrainLightingEnvironment {
    /// Five std140 vec4 fields. Generations/frame identity stay CPU-side for
    /// coherence validation; the shader receives only the semantic values it
    /// actually consumes.
    pub const STD140_SIZE: usize = 80;

    pub fn validate(&self) -> GalResult<()> {
        if self.shader_pack_generation == 0 || self.world_generation == 0 || self.frame_id == 0 {
            return Err(GalError::invalid_argument(
                "terrain lighting environment generations and frame id must be non-zero",
            ));
        }
        if !self.time_of_day.is_finite()
            || !(0.0..=1.0).contains(&self.time_of_day)
            || !self.rain_strength.is_finite()
            || !(0.0..=1.0).contains(&self.rain_strength)
            || !self.thunder_strength.is_finite()
            || !(0.0..=1.0).contains(&self.thunder_strength)
            || !self.vanilla_brightness.is_finite()
            || self.vanilla_brightness < 0.0
        {
            return Err(GalError::invalid_argument(
                "terrain lighting environment scalars are invalid",
            ));
        }
        for values in [
            self.camera_world_position,
            self.sun_direction,
            self.up_direction,
            self.block_light_color,
            self.scene_light_color,
        ] {
            if values.iter().any(|value| !value.is_finite()) {
                return Err(GalError::invalid_argument(
                    "terrain lighting environment contains a non-finite vector",
                ));
            }
        }
        if squared_length(self.sun_direction) <= f32::EPSILON
            || squared_length(self.up_direction) <= f32::EPSILON
        {
            return Err(GalError::invalid_argument(
                "terrain lighting environment directions must be non-zero",
            ));
        }
        Ok(())
    }

    pub fn std140_bytes(&self) -> GalResult<[u8; Self::STD140_SIZE]> {
        self.validate()?;
        let fields = [
            [
                self.camera_world_position[0],
                self.camera_world_position[1],
                self.camera_world_position[2],
                self.time_of_day,
            ],
            [
                self.sun_direction[0],
                self.sun_direction[1],
                self.sun_direction[2],
                self.rain_strength,
            ],
            [
                self.up_direction[0],
                self.up_direction[1],
                self.up_direction[2],
                self.thunder_strength,
            ],
            [
                self.block_light_color[0],
                self.block_light_color[1],
                self.block_light_color[2],
                self.vanilla_brightness,
            ],
            [
                self.scene_light_color[0],
                self.scene_light_color[1],
                self.scene_light_color[2],
                0.0,
            ],
        ];
        let mut bytes = [0; Self::STD140_SIZE];
        for (field_index, field) in fields.into_iter().enumerate() {
            for (component_index, component) in field.into_iter().enumerate() {
                let offset = field_index * 16 + component_index * 4;
                bytes[offset..offset + 4].copy_from_slice(&component.to_le_bytes());
            }
        }
        Ok(bytes)
    }
}

fn squared_length(values: [f32; 3]) -> f32 {
    values[0] * values[0] + values[1] * values[1] + values[2] * values[2]
}

#[derive(Clone, Debug, PartialEq)]
pub struct TerrainUniforms {
    pub view: [f32; 16],
    pub projection: [f32; 16],
    pub model: [f32; 16],
    pub time_seconds: f32,
    pub block_light: f32,
    pub sky_light: f32,
    pub directional_shade: f32,
}

impl TerrainUniforms {
    pub fn identity_for_tests() -> Self {
        let mut identity = [0.0f32; 16];
        identity[0] = 1.0;
        identity[5] = 1.0;
        identity[10] = 1.0;
        identity[15] = 1.0;
        Self {
            view: identity,
            projection: identity,
            model: identity,
            time_seconds: 0.0,
            block_light: 1.0,
            sky_light: 1.0,
            directional_shade: 1.0,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn environment() -> TerrainLightingEnvironment {
        TerrainLightingEnvironment {
            shader_pack_generation: 3,
            world_generation: 7,
            frame_id: 11,
            camera_world_position: [12.5, 64.0, -3.25],
            time_of_day: 0.75,
            rain_strength: 0.25,
            thunder_strength: 0.5,
            vanilla_brightness: 1.0,
            sun_direction: [0.25, 0.75, 0.5],
            up_direction: [0.0, 1.0, 0.0],
            block_light_color: [1.0, 0.74, 0.48],
            scene_light_color: [0.7, 0.82, 1.0],
        }
    }

    #[test]
    fn terrain_lighting_environment_validates_and_packs_stably() {
        let environment = environment();
        let bytes = environment.std140_bytes().unwrap();
        assert_eq!(TerrainLightingEnvironment::STD140_SIZE, bytes.len());
        assert_eq!(12.5f32.to_le_bytes(), bytes[0..4]);
        assert_eq!(0.75f32.to_le_bytes(), bytes[12..16]);
        assert_eq!(0.25f32.to_le_bytes(), bytes[28..32]);
        assert_eq!(0.5f32.to_le_bytes(), bytes[44..48]);
        assert_eq!(1.0f32.to_le_bytes(), bytes[60..64]);
        assert_eq!(0.7f32.to_le_bytes(), bytes[64..68]);
    }

    #[test]
    fn terrain_lighting_environment_rejects_missing_identity_and_invalid_vectors() {
        let mut invalid = environment();
        invalid.frame_id = 0;
        assert!(invalid.validate().is_err());
        invalid = environment();
        invalid.sun_direction = [0.0; 3];
        assert!(invalid.validate().is_err());
        invalid = environment();
        invalid.rain_strength = 1.5;
        assert!(invalid.validate().is_err());
    }
}

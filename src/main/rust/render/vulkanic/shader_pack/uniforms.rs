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

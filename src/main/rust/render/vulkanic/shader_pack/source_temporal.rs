//! Rust-owned temporal source semantics.
//!
//! These values reproduce documented shader-pack temporal behavior from
//! copied game-frame values. They intentionally carry no Iris objects,
//! backend state, or Java-owned smoothing state.

use crate::render::vulkanic::error::{GalError, GalResult};

const RAIN_FACTOR_HALF_LIFE_SECONDS: f32 = 1.5;
const LN_2: f32 = core::f32::consts::LN_2;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainSourceTemporalKey {
    pub world_generation: u64,
    pub shader_pack_generation: u64,
}

#[derive(Clone, Copy, Debug)]
struct RainFactorState {
    key: TerrainSourceTemporalKey,
    frame_id: u64,
    value: f32,
}

#[derive(Clone, Copy, Debug)]
struct BiomeClimateState {
    key: TerrainSourceTemporalKey,
    frame_id: u64,
    dry: f32,
    snowy: f32,
}

#[derive(Clone, Copy, Debug)]
struct NetherBiomeState {
    key: TerrainSourceTemporalKey,
    frame_id: u64,
    values: [f32; 5],
}

/// Stateful source semantics whose definitions require temporal history.
///
/// This mirrors Iris's documented `SmoothedFloat(15, 15, rainStrength)` used
/// for Complementary's `rainFactor`: first observation is unsmoothed; later
/// distinct frames use exponential smoothing with a 1.5 second half-life.
#[derive(Clone, Debug, Default)]
pub struct TerrainSourceTemporalUniforms {
    rain_factor: Option<RainFactorState>,
    biome_climate: Option<BiomeClimateState>,
    nether_biomes: Option<NetherBiomeState>,
}

impl TerrainSourceTemporalUniforms {
    pub fn rain_factor(
        &mut self,
        key: TerrainSourceTemporalKey,
        frame_id: u64,
        frame_time_seconds: f32,
        rain_strength: f32,
    ) -> GalResult<f32> {
        if !frame_time_seconds.is_finite() || frame_time_seconds < 0.0 {
            return Err(GalError::invalid_argument(
                "terrain source rainFactor frame time must be finite and non-negative",
            ));
        }
        if !rain_strength.is_finite() || !(0.0..=1.0).contains(&rain_strength) {
            return Err(GalError::invalid_argument(
                "terrain source rainFactor rain strength must be finite and within [0, 1]",
            ));
        }

        let Some(previous) = self.rain_factor else {
            self.rain_factor = Some(RainFactorState {
                key,
                frame_id,
                value: rain_strength,
            });
            return Ok(rain_strength);
        };

        if previous.key != key || frame_id < previous.frame_id {
            self.rain_factor = Some(RainFactorState {
                key,
                frame_id,
                value: rain_strength,
            });
            return Ok(rain_strength);
        }
        if frame_id == previous.frame_id {
            return Ok(previous.value);
        }

        let decay = LN_2 / RAIN_FACTOR_HALF_LIFE_SECONDS;
        let alpha = 1.0 - (-decay * frame_time_seconds).exp();
        let value = previous.value + (rain_strength - previous.value) * alpha;
        self.rain_factor = Some(RainFactorState {
            key,
            frame_id,
            value,
        });
        Ok(value)
    }

    /// Reproduces Complementary's source-defined `inDry` and `inSnowy`
    /// uniforms from the raw vanilla precipitation semantic. The pack uses
    /// `smooth(..., 20, 10)`, which Iris defines in deciseconds: 2 seconds
    /// while increasing and 1 second while decreasing.
    pub fn biome_climate(
        &mut self,
        key: TerrainSourceTemporalKey,
        frame_id: u64,
        frame_time_seconds: f32,
        precipitation: i32,
    ) -> GalResult<(f32, f32)> {
        if !frame_time_seconds.is_finite() || frame_time_seconds < 0.0 {
            return Err(GalError::invalid_argument(
                "terrain source biome climate frame time must be finite and non-negative",
            ));
        }
        if !(0..=2).contains(&precipitation) {
            return Err(GalError::invalid_argument(
                "terrain source biome precipitation must be 0 (none), 1 (rain), or 2 (snow)",
            ));
        }
        let target_dry = (precipitation == 0) as u8 as f32;
        let target_snowy = (precipitation == 2) as u8 as f32;
        let Some(previous) = self.biome_climate else {
            self.biome_climate = Some(BiomeClimateState {
                key,
                frame_id,
                dry: target_dry,
                snowy: target_snowy,
            });
            return Ok((target_dry, target_snowy));
        };
        if previous.key != key || frame_id < previous.frame_id {
            self.biome_climate = Some(BiomeClimateState {
                key,
                frame_id,
                dry: target_dry,
                snowy: target_snowy,
            });
            return Ok((target_dry, target_snowy));
        }
        if frame_id == previous.frame_id {
            return Ok((previous.dry, previous.snowy));
        }
        let dry = smooth_asymmetric(previous.dry, target_dry, frame_time_seconds, 2.0, 1.0);
        let snowy = smooth_asymmetric(previous.snowy, target_snowy, frame_time_seconds, 2.0, 1.0);
        self.biome_climate = Some(BiomeClimateState {
            key,
            frame_id,
            dry,
            snowy,
        });
        Ok((dry, snowy))
    }

    /// Reproduces the selected source's named Nether biome uniforms declared
    /// with `smooth(..., 15, 15)`. The canonical gameplay identity is mapped in
    /// Rust, so Java neither evaluates shader-pack conditions nor owns history.
    pub fn nether_biomes(
        &mut self,
        key: TerrainSourceTemporalKey,
        frame_id: u64,
        frame_time_seconds: f32,
        biome_resource_location: &str,
    ) -> GalResult<[f32; 5]> {
        if !frame_time_seconds.is_finite() || frame_time_seconds < 0.0 {
            return Err(GalError::invalid_argument(
                "terrain source Nether biome frame time must be finite and non-negative",
            ));
        }
        let target = nether_biome_target(biome_resource_location);
        let Some(previous) = self.nether_biomes else {
            self.nether_biomes = Some(NetherBiomeState {
                key,
                frame_id,
                values: target,
            });
            return Ok(target);
        };
        if previous.key != key || frame_id < previous.frame_id {
            self.nether_biomes = Some(NetherBiomeState {
                key,
                frame_id,
                values: target,
            });
            return Ok(target);
        }
        if frame_id == previous.frame_id {
            return Ok(previous.values);
        }
        let values = core::array::from_fn(|index| {
            smooth_asymmetric(
                previous.values[index],
                target[index],
                frame_time_seconds,
                1.5,
                1.5,
            )
        });
        self.nether_biomes = Some(NetherBiomeState {
            key,
            frame_id,
            values,
        });
        Ok(values)
    }
}

fn nether_biome_target(biome_resource_location: &str) -> [f32; 5] {
    match biome_resource_location {
        "minecraft:nether_wastes" => [1.0, 0.0, 0.0, 0.0, 0.0],
        "minecraft:crimson_forest" => [0.0, 1.0, 0.0, 0.0, 0.0],
        "minecraft:warped_forest" => [0.0, 0.0, 1.0, 0.0, 0.0],
        "minecraft:basalt_deltas" => [0.0, 0.0, 0.0, 1.0, 0.0],
        "minecraft:soul_sand_valley" => [0.0, 0.0, 0.0, 0.0, 1.0],
        _ => [0.0; 5],
    }
}

fn smooth_asymmetric(
    previous: f32,
    target: f32,
    frame_time_seconds: f32,
    half_life_up_seconds: f32,
    half_life_down_seconds: f32,
) -> f32 {
    let half_life = if target > previous {
        half_life_up_seconds
    } else {
        half_life_down_seconds
    };
    let alpha = 1.0 - (-(LN_2 / half_life) * frame_time_seconds).exp();
    previous + (target - previous) * alpha
}

#[cfg(test)]
mod tests {
    use super::*;

    const KEY: TerrainSourceTemporalKey = TerrainSourceTemporalKey {
        world_generation: 4,
        shader_pack_generation: 9,
    };

    #[test]
    fn rain_factor_matches_iris_symmetric_exponential_smoothing() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        assert_eq!(0.0, uniforms.rain_factor(KEY, 1, 0.016, 0.0).unwrap());
        let smoothed = uniforms.rain_factor(KEY, 2, 1.5, 1.0).unwrap();
        assert!((smoothed - 0.5).abs() < 0.000_001);
        assert_eq!(smoothed, uniforms.rain_factor(KEY, 2, 1.5, 0.0).unwrap());
        let descending = uniforms.rain_factor(KEY, 3, 1.5, 0.0).unwrap();
        assert!((descending - 0.25).abs() < 0.000_001);
    }

    #[test]
    fn rain_factor_resets_for_new_world_or_pack_and_out_of_order_frames() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        uniforms.rain_factor(KEY, 10, 0.0, 0.0).unwrap();
        assert!(uniforms.rain_factor(KEY, 11, 1.5, 1.0).unwrap() > 0.49);

        assert_eq!(
            0.25,
            uniforms
                .rain_factor(
                    TerrainSourceTemporalKey {
                        shader_pack_generation: 10,
                        ..KEY
                    },
                    12,
                    1.5,
                    0.25,
                )
                .unwrap()
        );
        assert_eq!(0.75, uniforms.rain_factor(KEY, 1, 1.5, 0.75).unwrap());
    }

    #[test]
    fn rain_factor_rejects_invalid_semantic_inputs() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        assert!(uniforms.rain_factor(KEY, 1, f32::NAN, 0.0).is_err());
        assert!(uniforms.rain_factor(KEY, 1, 0.0, -0.01).is_err());
    }

    #[test]
    fn biome_climate_matches_complementary_precipitation_smoothing() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        assert_eq!(
            (1.0, 0.0),
            uniforms.biome_climate(KEY, 1, 0.016, 0).unwrap()
        );

        let (dry, snowy) = uniforms.biome_climate(KEY, 2, 1.0, 1).unwrap();
        assert!((dry - 0.5).abs() < 0.000_001, "{dry}");
        assert_eq!(0.0, snowy);

        let (dry, snowy) = uniforms.biome_climate(KEY, 3, 2.0, 2).unwrap();
        assert!((dry - 0.125).abs() < 0.000_001, "{dry}");
        assert!((snowy - 0.5).abs() < 0.000_001, "{snowy}");
    }

    #[test]
    fn biome_climate_resets_and_rejects_invalid_precipitation() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        uniforms.biome_climate(KEY, 10, 0.0, 1).unwrap();
        assert_eq!(
            (0.0, 1.0),
            uniforms
                .biome_climate(
                    TerrainSourceTemporalKey {
                        shader_pack_generation: 10,
                        ..KEY
                    },
                    11,
                    0.0,
                    2,
                )
                .unwrap()
        );
        assert!(uniforms.biome_climate(KEY, 12, f32::NAN, 1).is_err());
        assert!(uniforms.biome_climate(KEY, 12, 0.0, 3).is_err());
    }

    #[test]
    fn nether_biomes_follow_selected_source_mapping_and_smoothing() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        assert_eq!(
            [1.0, 0.0, 0.0, 0.0, 0.0],
            uniforms
                .nether_biomes(KEY, 1, 0.016, "minecraft:nether_wastes")
                .unwrap()
        );
        let values = uniforms
            .nether_biomes(KEY, 2, 1.5, "minecraft:crimson_forest")
            .unwrap();
        assert_eq!([0.5, 0.5, 0.0, 0.0, 0.0], values);
        assert_eq!(
            values,
            uniforms
                .nether_biomes(KEY, 2, 0.0, "minecraft:warped_forest")
                .unwrap(),
            "a repeated render frame must not advance source temporal state"
        );
        assert_eq!(
            [0.0, 0.0, 1.0, 0.0, 0.0],
            uniforms
                .nether_biomes(
                    TerrainSourceTemporalKey {
                        shader_pack_generation: 10,
                        ..KEY
                    },
                    3,
                    0.016,
                    "minecraft:warped_forest",
                )
                .unwrap(),
            "world or source generation changes reset the source-defined history"
        );
    }

    #[test]
    fn nether_biomes_treat_unknown_or_overworld_biomes_as_all_false() {
        let mut uniforms = TerrainSourceTemporalUniforms::default();
        assert_eq!(
            [0.0; 5],
            uniforms
                .nether_biomes(KEY, 1, 0.016, "minecraft:plains")
                .unwrap()
        );
        assert!(uniforms
            .nether_biomes(KEY, 2, f32::NAN, "minecraft:plains")
            .is_err());
    }
}

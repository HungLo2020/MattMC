//! Built-in two-channel Iris brightness, separate from named custom scalars.

use super::*;

#[derive(Clone, Copy, Debug)]
pub(super) struct State {
    key: TerrainSourceTemporalKey,
    frame_id: u64,
    half_life_bits: u32,
    values: [f32; 2],
}

impl TerrainSourceTemporalUniforms {
    pub fn eye_brightness_smooth(
        &mut self,
        key: TerrainSourceTemporalKey,
        frame_id: u64,
        delta_seconds: f32,
        raw: [i32; 2],
        half_life_seconds: f32,
    ) -> GalResult<[i32; 2]> {
        if !delta_seconds.is_finite()
            || delta_seconds < 0.0
            || !half_life_seconds.is_finite()
            || half_life_seconds < 0.0
            || raw
                .iter()
                .any(|value| !(0..=240).contains(value) || value % 16 != 0)
        {
            return Err(GalError::invalid_argument("smoothed eye brightness requires valid packed light and non-negative finite timing"));
        }
        let values = match self.eye_brightness_smooth {
            Some(previous)
                if previous.key == key
                    && frame_id >= previous.frame_id
                    && previous.half_life_bits == half_life_seconds.to_bits() =>
            {
                if frame_id == previous.frame_id {
                    return Ok(previous.values.map(|value| value as i32));
                }
                // Preserve SmoothedFloat's float intermediates and Java Math
                // exp cast, followed by truncation of each packed channel.
                let decay = (1.0 / (half_life_seconds as f64 / core::f64::consts::LN_2)) as f32;
                let alpha = 1.0 - ((-decay * delta_seconds) as f64).exp() as f32;
                std::array::from_fn(|index| {
                    (1.0 - alpha) * previous.values[index] + alpha * raw[index] as f32
                })
            }
            _ => raw.map(|value| value as f32),
        };
        self.eye_brightness_smooth = Some(State {
            key,
            frame_id,
            half_life_bits: half_life_seconds.to_bits(),
            values,
        });
        Ok(values.map(|value| value as i32))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    const KEY: TerrainSourceTemporalKey = TerrainSourceTemporalKey {
        world_generation: 2,
        shader_pack_generation: 3,
    };

    #[test]
    fn builtin_eye_brightness_smooth_preserves_both_channels_and_one_update_per_frame() {
        let mut state = TerrainSourceTemporalUniforms::default();
        assert_eq!(
            [0, 240],
            state
                .eye_brightness_smooth(KEY, 1, 0.01, [0, 240], 0.3)
                .unwrap()
        );
        assert_eq!(
            [120, 120],
            state
                .eye_brightness_smooth(KEY, 2, 0.3, [240, 0], 0.3)
                .unwrap()
        );
        assert_eq!(
            [120, 120],
            state
                .eye_brightness_smooth(KEY, 2, 0.3, [0, 240], 0.3)
                .unwrap()
        );
        assert_eq!(
            [180, 60],
            state
                .eye_brightness_smooth(KEY, 3, 0.3, [240, 0], 0.3)
                .unwrap()
        );
        assert_eq!(
            [180, 60],
            state
                .eye_brightness_smooth(KEY, 4, 0.0, [240, 0], 0.3)
                .unwrap()
        );
    }

    #[test]
    fn builtin_eye_brightness_smooth_resets_generation_policy_and_rejects_bad_inputs() {
        let mut state = TerrainSourceTemporalUniforms::default();
        state
            .eye_brightness_smooth(KEY, 10, 0.01, [0, 240], 1.0)
            .unwrap();
        assert_eq!(
            [16, 32],
            state
                .eye_brightness_smooth(KEY, 9, 1.0, [16, 32], 1.0)
                .unwrap()
        );
        assert_eq!(
            [64, 128],
            state
                .eye_brightness_smooth(KEY, 10, 1.0, [64, 128], 0.3)
                .unwrap()
        );
        assert_eq!(
            [240, 16],
            state
                .eye_brightness_smooth(
                    TerrainSourceTemporalKey {
                        world_generation: 4,
                        ..KEY
                    },
                    11,
                    1.0,
                    [240, 16],
                    0.3
                )
                .unwrap()
        );
        assert_eq!(
            [32, 48],
            state
                .eye_brightness_smooth(
                    TerrainSourceTemporalKey {
                        shader_pack_generation: 5,
                        ..KEY
                    },
                    12,
                    1.0,
                    [32, 48],
                    0.3
                )
                .unwrap()
        );
        for (delta, raw, half) in [
            (f32::NAN, [0, 0], 1.0),
            (-1.0, [0, 0], 1.0),
            (1.0, [15, 0], 1.0),
            (1.0, [256, 0], 1.0),
            (1.0, [0, 0], -1.0),
        ] {
            assert!(state
                .eye_brightness_smooth(KEY, 13, delta, raw, half)
                .is_err());
        }
    }

    #[test]
    fn builtin_eye_brightness_smooth_matches_frozen_default_and_zero_half_life() {
        // Values from actual Frozen SmoothedVec2f/Timer/FrameUpdateNotifier.
        for (half, expected) in [
            (1.0, [[0, 240], [45, 194], [81, 158], [81, 158], [77, 162]]),
            (0.3, [[0, 240], [120, 120], [180, 60], [180, 60], [146, 93]]),
            (0.0, [[0, 240], [240, 0], [240, 0], [0, 0], [0, 0]]),
        ] {
            let mut state = TerrainSourceTemporalUniforms::default();
            let raw = [[0, 240], [240, 0], [240, 0], [240, 0], [16, 224]];
            for i in 0..5 {
                assert_eq!(
                    expected[i],
                    state
                        .eye_brightness_smooth(
                            KEY,
                            i as u64 + 1,
                            [0.0, 0.3, 0.3, 0.0, 0.1][i],
                            raw[i],
                            half
                        )
                        .unwrap()
                );
            }
        }
    }
}

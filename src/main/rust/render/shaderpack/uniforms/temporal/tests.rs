use crate::render::shaderpack::uniforms::temporal::*;

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
        (1.0, 0.0, 0.0),
        uniforms.biome_climate(KEY, 1, 0.016, 0).unwrap()
    );

    let (dry, rainy, snowy) = uniforms.biome_climate(KEY, 2, 1.0, 1).unwrap();
    assert!((dry - 0.5).abs() < 0.000_001, "{dry}");
    assert!(
        (rainy - (1.0 - 2.0_f32.powf(-0.5))).abs() < 0.000_001,
        "{rainy}"
    );
    assert_eq!(0.0, snowy);

    let (dry, rainy, snowy) = uniforms.biome_climate(KEY, 3, 2.0, 2).unwrap();
    assert!((dry - 0.125).abs() < 0.000_001, "{dry}");
    assert!(
        (rainy - (1.0 - 2.0_f32.powf(-0.5)) * 0.25).abs() < 0.000_001,
        "{rainy}"
    );
    assert!((snowy - 0.5).abs() < 0.000_001, "{snowy}");
}

#[test]
fn biome_climate_resets_and_rejects_invalid_precipitation() {
    let mut uniforms = TerrainSourceTemporalUniforms::default();
    uniforms.biome_climate(KEY, 10, 0.0, 1).unwrap();
    assert_eq!(
        (0.0, 0.0, 1.0),
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
fn wetness_uses_source_derived_asymmetric_half_lives() {
    let mut uniforms = TerrainSourceTemporalUniforms::default();
    assert_eq!(
        1.0,
        uniforms.wetness(KEY, 1, 0.016, 1.0, 30.0, 30.0).unwrap()
    );
    let value = uniforms.wetness(KEY, 2, 30.0, 0.0, 30.0, 30.0).unwrap();
    assert!((value - 0.5).abs() < 0.000_001, "{value}");
    assert!(uniforms.wetness(KEY, 3, 0.0, 1.1, 30.0, 30.0).is_err());
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

#[test]
fn pale_garden_biome_uses_only_the_copied_canonical_identity() {
    assert_eq!(
        1.0,
        TerrainSourceTemporalUniforms::pale_garden_biome("minecraft:pale_garden")
    );
    assert_eq!(
        0.0,
        TerrainSourceTemporalUniforms::pale_garden_biome("minecraft:plains")
    );
    assert_eq!(
        0.0,
        TerrainSourceTemporalUniforms::pale_garden_biome("other:pale_garden")
    );
}

#[test]
fn camera_history_is_stable_within_a_frame_and_resets_by_generation() {
    let mut uniforms = TerrainSourceTemporalUniforms::default();
    let first = uniforms
        .camera_history(KEY, 10, [1.0, 2.0, 3.0], [1.0; 16], [2.0; 16])
        .unwrap();
    assert_eq!([1.0, 2.0, 3.0], first.previous_camera_world_position);
    let repeated = uniforms
        .camera_history(KEY, 10, [9.0, 9.0, 9.0], [9.0; 16], [9.0; 16])
        .unwrap();
    assert_eq!(
        first, repeated,
        "one frame must retain one temporal history"
    );
    let next = uniforms
        .camera_history(KEY, 11, [4.0, 5.0, 6.0], [3.0; 16], [4.0; 16])
        .unwrap();
    assert_eq!([1.0, 2.0, 3.0], next.previous_camera_world_position);
    assert_eq!([1.0; 16], next.previous_view_matrix);
    // Later stages of the same frame (deferred/composite TAA) must see the
    // same previous camera, not this frame's camera.
    let next_repeated = uniforms
        .camera_history(KEY, 11, [4.0, 5.0, 6.0], [3.0; 16], [4.0; 16])
        .unwrap();
    assert_eq!(next, next_repeated);
    let reset = uniforms
        .camera_history(
            TerrainSourceTemporalKey {
                shader_pack_generation: 10,
                ..KEY
            },
            12,
            [7.0, 8.0, 9.0],
            [5.0; 16],
            [6.0; 16],
        )
        .unwrap();
    assert_eq!([7.0, 8.0, 9.0], reset.previous_camera_world_position);
}

#[test]
fn eye_brightness_uses_source_declared_smoothing_and_rejects_bad_packing() {
    let mut uniforms = TerrainSourceTemporalUniforms::default();
    assert_eq!(
        (0.0, 0.0),
        uniforms.eye_brightness(KEY, 1, 0.016, [0, 0]).unwrap()
    );
    let (smoothed, sky_lit) = uniforms.eye_brightness(KEY, 2, 0.5, [80, 240]).unwrap();
    assert!((smoothed - 0.5).abs() < 0.000_001, "{smoothed}");
    assert!((sky_lit - 0.823_223_3).abs() < 0.000_01, "{sky_lit}");
    assert_eq!(
        (smoothed, sky_lit),
        uniforms.eye_brightness(KEY, 2, 0.0, [0, 0]).unwrap(),
        "repeated source preparation must not advance smoothing"
    );
    assert!(uniforms.eye_brightness(KEY, 3, 0.0, [1, 240]).is_err());
}

#[test]
fn frame_time_smooth_matches_the_source_declared_five_decisecond_window() {
    let mut uniforms = TerrainSourceTemporalUniforms::default();
    assert_eq!(
        0.016,
        uniforms.frame_time_smooth(KEY, 1, 0.016).unwrap(),
        "the first frame carries its exact copied semantic duration"
    );
    let smoothed = uniforms.frame_time_smooth(KEY, 2, 0.5).unwrap();
    assert!((smoothed - 0.258).abs() < 0.000_001, "{smoothed}");
    assert_eq!(
        smoothed,
        uniforms.frame_time_smooth(KEY, 2, 0.016).unwrap(),
        "repeated source preparation cannot advance temporal state"
    );
    assert_eq!(
        0.25,
        uniforms
            .frame_time_smooth(
                TerrainSourceTemporalKey {
                    shader_pack_generation: 10,
                    ..KEY
                },
                3,
                0.25,
            )
            .unwrap(),
        "world or source generation changes reset smoothing"
    );
    assert!(uniforms.frame_time_smooth(KEY, 4, f32::NAN).is_err());
}

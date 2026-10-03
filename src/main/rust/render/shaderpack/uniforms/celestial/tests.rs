use super::*;

fn policy() -> ShaderPackFrameUniformPolicy {
    ShaderPackFrameUniformPolicy {
        generation: 1,
        sun_path_rotation_degrees: 0.0,
        eye_brightness_half_life_seconds: 1.0,
        end_flash_shadows: false,
    }
}
fn identity() -> [f32; 16] {
    let mut view = [0.0; 16];
    for i in 0..4 {
        view[i * 5] = 1.0;
    }
    view
}
fn near(actual: [f32; 3], expected: [f32; 3]) {
    for i in 0..3 {
        assert!(
            (actual[i] - expected[i]).abs() < 0.0001,
            "{actual:?} != {expected:?}"
        );
    }
}

#[test]
fn builtin_celestial_positions_preserve_day_night_and_direction_translation() {
    let mut view = identity();
    view[12] = 1000.0;
    view[13] = -2000.0;
    view[14] = 3000.0;
    for (time, expected) in [
        (0.0, [0.0, 100.0, 0.0]),
        (0.25, [-100.0, 0.0, 0.0]),
        (0.5, [0.0, -100.0, 0.0]),
        (0.75, [100.0, 0.0, 0.0]),
    ] {
        let value = CelestialFrameUniforms::from_frame(
            policy(),
            TerrainProgramScope::Overworld,
            time,
            view,
            None,
        )
        .unwrap();
        near(value.sun_position, expected);
        near(value.moon_position, expected.map(|x| -x));
        near(value.up_position, [0.0, 100.0, 0.0]);
        near(
            value.shadow_light_position,
            if time == 0.5 {
                expected.map(|x| -x)
            } else {
                expected
            },
        );
    }
}

#[test]
fn builtin_celestial_end_flash_is_scoped_opt_in_and_requires_valid_angles() {
    let enabled = ShaderPackFrameUniformPolicy {
        end_flash_shadows: true,
        ..policy()
    };
    assert!(CelestialFrameUniforms::from_frame(
        enabled,
        TerrainProgramScope::End,
        0.0,
        identity(),
        None
    )
    .is_err());
    let end = CelestialFrameUniforms::from_frame(
        enabled,
        TerrainProgramScope::End,
        0.0,
        identity(),
        Some([0.0, 180.0]),
    )
    .unwrap();
    near(end.shadow_light_position, [0.0, 0.0, -100.0]);
    let ordinary = CelestialFrameUniforms::from_frame(
        enabled,
        TerrainProgramScope::Overworld,
        0.0,
        identity(),
        None,
    )
    .unwrap();
    near(ordinary.shadow_light_position, [0.0, 100.0, 0.0]);
    assert!(CelestialFrameUniforms::from_frame(
        enabled,
        TerrainProgramScope::End,
        0.0,
        identity(),
        Some([f32::NAN, 0.0])
    )
    .is_err());
    assert!(CelestialFrameUniforms::from_frame(
        policy(),
        TerrainProgramScope::Overworld,
        f32::NAN,
        identity(),
        None
    )
    .is_err());
}

#[test]
fn builtin_celestial_positions_match_frozen_joml_camera_and_path_fixtures() {
    // FrozenBuiltinUniformProbe uses Frozen Axis and JOML with the exact
    // CelestialUniforms rotation sequence. These are view-space positions.
    let view = [
        0.79863554,
        0.0,
        -0.601815,
        0.0,
        -0.114831716,
        0.9816272,
        -0.15238684,
        0.0,
        0.59075797,
        0.190809,
        0.78396237,
        0.0,
        10.0,
        20.0,
        30.0,
        1.0,
    ];
    let policy = ShaderPackFrameUniformPolicy {
        sun_path_rotation_degrees: -25.0,
        ..policy()
    };
    for (time, expected) in [
        (0.0, [14.559221, 97.02957, 19.320744]),
        (0.25, [-79.86354, 0.0, 60.181488]),
        (0.5, [-14.559221, -97.02957, -19.320744]),
        (0.75, [79.86354, 0.0, -60.181488]),
        (0.3, [-80.4538, -29.983803, 51.265556]),
    ] {
        near(
            CelestialFrameUniforms::from_frame(
                policy,
                TerrainProgramScope::Overworld,
                time,
                view,
                None,
            )
            .unwrap()
            .sun_position,
            expected,
        );
    }
    near(
        CelestialFrameUniforms::from_frame(
            ShaderPackFrameUniformPolicy {
                end_flash_shadows: true,
                ..policy
            },
            TerrainProgramScope::End,
            0.3,
            view,
            Some([13.0, -47.0]),
        )
        .unwrap()
        .shadow_light_position,
        [98.75163, -9.402185, 12.637843],
    );
}

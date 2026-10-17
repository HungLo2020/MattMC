use super::*;

#[test]
fn builtin_frame_uniforms_pack_distinct_celestial_vectors_and_smoothed_light() {
    let requirements = source_requirements(
        "uniform vec3 sunPosition;\nuniform vec3 moonPosition;\nuniform vec3 upPosition;",
        "vec3 directions=sunPosition+moonPosition+upPosition;",
        "uniform vec3 shadowLightPosition;\nuniform ivec2 eyeBrightnessSmooth;",
        "vec3 light=shadowLightPosition; ivec2 brightness=eyeBrightnessSmooth;",
    )
    .unwrap();
    assert!(requirements.is_fully_semantic());
    let frame = TerrainSourceUniformFrame {
        sun_position: Some([10.0, 20.0, 30.0]),
        moon_position: Some([-10.0, -20.0, -30.0]),
        up_position: Some([0.0, 100.0, 0.0]),
        shadow_light_position: Some([40.0, 50.0, 60.0]),
        eye_brightness_smooth: Some([119, 121]),
        view_matrix: Some([1.0; 16]),
        projection_matrix: Some([1.0; 16]),
        ..Default::default()
    };
    let bytes = frame.pack_std140(&requirements).unwrap();
    let offset = |name| {
        requirements
            .fields()
            .iter()
            .find(|field| field.field.name() == name)
            .unwrap()
            .field
            .offset() as usize
    };
    for (name, values) in [
        ("sunPosition", [10.0_f32, 20.0, 30.0]),
        ("moonPosition", [-10.0, -20.0, -30.0]),
        ("upPosition", [0.0, 100.0, 0.0]),
        ("shadowLightPosition", [40.0, 50.0, 60.0]),
    ] {
        let base = offset(name);
        for (i, value) in values.into_iter().enumerate() {
            assert_eq!(value.to_le_bytes(), bytes[base + i * 4..base + i * 4 + 4]);
        }
    }
    let light = offset("eyeBrightnessSmooth");
    assert_eq!(119_i32.to_le_bytes(), bytes[light..light + 4]);
    assert_eq!(121_i32.to_le_bytes(), bytes[light + 4..light + 8]);
    assert!(TerrainSourceUniformFrame {
        eye_brightness_smooth: None,
        ..frame.clone()
    }
    .pack_std140(&requirements)
    .is_err());
    assert!(TerrainSourceUniformFrame {
        eye_brightness_smooth: Some([241, 0]),
        ..frame
    }
    .pack_std140(&requirements)
    .is_err());
}

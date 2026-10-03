use super::*;
use crate::render::shaderpack::source::{ShaderSourceFile, RUNTIME_OPTIONS_PATH};

fn source(files: Vec<ShaderSourceFile>) -> ShaderPackSource {
    ShaderPackSource::new("builtin-frame-policy", 6, files).unwrap()
}

#[test]
fn builtin_frame_policy_resolves_fragment_includes_and_selected_constants() {
    let source = source(vec![
        ShaderSourceFile::new("world0/gbuffers_terrain.fsh", "#version 120\n#include \"/lib/settings.glsl\"\n"),
        ShaderSourceFile::new("lib/settings.glsl", "#define USE_FLASH 1 // [0 1]\nconst float sunPathRotation=-25.0; // [-25.0 -35.0]\nconst float eyeBrightnessHalflife=3.0;\n"),
        ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "sunPathRotation=-35.0\n"),
        ShaderSourceFile::new("shaders.properties", "#if USE_FLASH == 1\nendFlashShadows=true\n#else\nendFlashShadows=false\n#endif\n"),
    ]);
    let before = source.clone();
    let policy = source
        .frame_uniform_policy(TerrainProgramScope::Overworld)
        .unwrap();
    assert_eq!(-35.0, policy.sun_path_rotation_degrees);
    assert_eq!(0.3, policy.eye_brightness_half_life_seconds);
    assert!(policy.end_flash_shadows);
    assert_eq!(before, source); // Resolving the memo does not change identity.
    assert_eq!(
        policy,
        source
            .frame_uniform_policy(TerrainProgramScope::Overworld)
            .unwrap()
    );
}

#[test]
fn builtin_frame_policy_uses_frozen_program_order_and_separate_dimension_sets() {
    let source = source(vec![
        ShaderSourceFile::new(
            "gbuffers_terrain.fsh",
            "const float sunPathRotation=99.0;\n",
        ),
        ShaderSourceFile::new("world0/final.fsh", "const float sunPathRotation=-10.0;\n"),
        ShaderSourceFile::new(
            "world0/deferred.fsh",
            "const float sunPathRotation=-20.0;\n",
        ),
        ShaderSourceFile::new(
            "world0/composite.fsh",
            "const float sunPathRotation=-25.0;\n",
        ),
        ShaderSourceFile::new(
            "world0/composite2.fsh",
            "const float sunPathRotation=-30.0;\n",
        ),
        ShaderSourceFile::new(
            "world1/gbuffers_terrain.fsh",
            "const float eyeBrightnessHalflife=20.0;\n",
        ),
        ShaderSourceFile::new(
            "world0/ignored.glsl",
            "const float sunPathRotation=invalid;\n",
        ),
        ShaderSourceFile::new(
            "world0/composite.vsh",
            "const float sunPathRotation=invalid;\n",
        ),
    ]);
    assert_eq!(
        -30.0,
        source
            .frame_uniform_policy(TerrainProgramScope::Overworld)
            .unwrap()
            .sun_path_rotation_degrees
    );
    let end = source
        .frame_uniform_policy(TerrainProgramScope::End)
        .unwrap();
    assert_eq!(0.0, end.sun_path_rotation_degrees);
    assert_eq!(2.0, end.eye_brightness_half_life_seconds);
    assert_eq!(
        99.0,
        source
            .frame_uniform_policy(TerrainProgramScope::Nether)
            .unwrap()
            .sun_path_rotation_degrees
    );
}

#[test]
fn builtin_frame_policy_rejects_invalid_active_directives() {
    for declaration in [
        "const float sunPathRotation=NaN;",
        "const float eyeBrightnessHalflife=-1.0;",
        "const float sunPathRotation=unknown;",
    ] {
        let source = source(vec![ShaderSourceFile::new(
            "gbuffers_terrain.fsh",
            declaration,
        )]);
        assert!(source
            .frame_uniform_policy(TerrainProgramScope::Default)
            .is_err());
    }
}

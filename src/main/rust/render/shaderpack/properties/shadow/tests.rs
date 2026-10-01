use crate::render::shaderpack::properties::shadow::*;
use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::contracts::terrain::bundled_complementary_hung_loified_source;

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
    assert_eq!(1024, policy.resolution());
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
fn selects_and_bounds_the_preprocessed_shadow_attachment_resolution() {
    let selected = source(
        "#define SHADOW_SMOOTHING 4\n#if SHADOW_SMOOTHING >= 3\nconst int shadowMapResolution = 2048;\n#else\nconst int shadowMapResolution = 4096;\n#endif\n",
    );
    assert_eq!(
        2048,
        ShaderPackShadowPolicy::from_source(&selected)
            .unwrap()
            .unwrap()
            .resolution()
    );
    assert!(ShaderPackShadowPolicy::from_source(&source(
        "const int shadowMapResolution = 8192;\n",
    ))
    .is_err());
}

#[test]
fn shadow_cutout_alpha_uses_the_selected_pack_override() {
    let with_property = |property: &str| {
        ShaderPackSource::new(
            "shadow-alpha-policy",
            10,
            vec![
                ShaderSourceFile::new("lib/common.glsl", "const float shadowDistance = 64.0;"),
                ShaderSourceFile::new("shaders.properties", property),
            ],
        )
        .unwrap()
    };
    assert_eq!(
        Some(0.1),
        ShaderPackShadowPolicy::from_source(&source(""))
            .unwrap()
            .unwrap()
            .cutout_alpha_cutoff()
    );
    assert_eq!(
        Some(0.25),
        ShaderPackShadowPolicy::from_source(&with_property(
            "alphaTest.shadow=GREATER 0.25\n"
        ))
        .unwrap()
        .unwrap()
        .cutout_alpha_cutoff()
    );
    assert_eq!(
        None,
        ShaderPackShadowPolicy::from_source(&with_property("alphaTest.shadow=off\n"))
            .unwrap()
            .unwrap()
            .cutout_alpha_cutoff()
    );
    assert!(ShaderPackShadowPolicy::from_source(&with_property("alphaTest.shadow=LESS 0.25\n"))
        .is_err());
    assert!(ShaderPackShadowPolicy::from_source(&with_property(
        "#if SHADOW_QUALITY > 0\nalphaTest.shadow=GREATER 0.25\n#endif\n"
    ))
    .is_err());
}

#[test]
fn translucent_shadow_directive_defaults_on_and_can_disable_the_phase() {
    assert!(ShaderPackShadowPolicy::from_source(&source(""))
        .unwrap()
        .unwrap()
        .render_translucent());
    let disabled = ShaderPackSource::new(
        "shadow-translucent-policy",
        11,
        vec![
            ShaderSourceFile::new("lib/common.glsl", "const float shadowDistance = 64.0;"),
            ShaderSourceFile::new("shaders.properties", "shadowTranslucent=false\n"),
        ],
    )
    .unwrap();
    assert!(!ShaderPackShadowPolicy::from_source(&disabled)
        .unwrap()
        .unwrap()
        .render_translucent());
    let conditional = ShaderPackSource::new(
        "conditional-shadow-translucent-policy",
        12,
        vec![
            ShaderSourceFile::new("lib/common.glsl", "const float shadowDistance = 64.0;"),
            ShaderSourceFile::new(
                "shaders.properties",
                "#if(SHADOW_QUALITY > 0)\nshadowTranslucent=false\n#endif\n",
            ),
        ],
    )
    .unwrap();
    assert!(ShaderPackShadowPolicy::from_source(&conditional).is_err());
}

#[test]
fn inactive_shadow_culling_branch_does_not_override_advanced_default() {
    let source = ShaderPackSource::new(
        "shadow-culling-options",
        13,
        vec![
            ShaderSourceFile::new("lib/common.glsl", "const float shadowDistance = 192.0;"),
            ShaderSourceFile::new(
                "shaders.properties",
                "#if COLORED_LIGHTING > 0\nshadow.culling = reversed\n#endif\n",
            ),
            ShaderSourceFile::new("mattmc/runtime-options.properties", "COLORED_LIGHTING=0\n"),
        ],
    )
    .unwrap();
    ShaderPackShadowPolicy::from_source(&source)
        .unwrap()
        .unwrap()
        .require_supported_caster_selection()
        .unwrap();
}

#[test]
fn active_reversed_shadow_culling_uses_copied_voxel_distance() {
    let source = ShaderPackSource::new(
        "shadow-safe-zone-options",
        14,
        vec![
            ShaderSourceFile::new("lib/common.glsl", "const float shadowDistance = 192.0;"),
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "const float voxelDistance = 32.0;"),
            ShaderSourceFile::new("shaders.properties", "#if COLORED_LIGHTING > 0\nshadow.culling = reversed\n#endif\n"),
            ShaderSourceFile::new("mattmc/runtime-options.properties", "COLORED_LIGHTING=256\n"),
        ],
    ).unwrap();
    let policy = ShaderPackShadowPolicy::from_source(&source).unwrap().unwrap();
    assert_eq!(policy.require_supported_caster_selection().unwrap(), ShadowCasterSelection::SafeZone);
    assert_eq!(policy.voxel_distance, 32.0);
}

#[test]
fn bundled_selected_pack_uses_its_declared_square_shadow_resolution() {
    let source = bundled_complementary_hung_loified_source(11).unwrap();
    assert_eq!(
        2048,
        ShaderPackShadowPolicy::from_source(&source)
            .unwrap()
            .unwrap()
            .resolution()
    );
}

#[test]
fn bundled_selected_pack_resolves_conditional_caster_directives() {
    // Complementary guards these with `#if ENTITY_SHADOWS_DEFINE == -1`
    // and `PLAYER_SHADOW`; its defaults disable entity and block-entity
    // casters but keep the local player, exactly as Frozen Iris resolves.
    let source = bundled_complementary_hung_loified_source(11).unwrap();
    let casters = ShaderPackShadowPolicy::from_source(&source)
        .unwrap()
        .unwrap()
        .casters();
    assert_eq!(
        ShadowCasterDirectives {
            entities: false,
            player: true,
            block_entities: false,
        },
        casters
    );
}

#[test]
fn missing_caster_directives_use_iris_defaults() {
    let policy =
        ShaderPackShadowPolicy::from_source(&source("const float shadowIntervalSize = 2.0;\n"))
            .unwrap()
            .unwrap();
    assert_eq!(
        ShadowCasterDirectives {
            entities: true,
            player: false,
            block_entities: true,
        },
        policy.casters()
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

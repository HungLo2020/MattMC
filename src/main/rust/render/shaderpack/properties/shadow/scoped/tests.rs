use super::*;
use crate::render::shaderpack::source::ShaderSourceFile;

fn source() -> ShaderPackSource {
    ShaderPackSource::new("scope-shadow", 41, vec![
        ShaderSourceFile::new("gbuffers_terrain.fsh", "const int shadowMapResolution = 512;\nconst float shadowDistance = 40.0;\n"),
        ShaderSourceFile::new("world0/gbuffers_terrain.fsh", concat!(
            "#define SELECTED_END 0\n#define ENTITY_ENABLED 0\n#define MAP_SIZE 840\n",
            "const int shadowMapResolution = MAP_SIZE;\nconst float shadowDistance = 105.0f;\n",
            "const float sunPathRotation = -25.0;\n")),
        ShaderSourceFile::new("world0/final.fsh", "#define SELECTED_END 0\n#define ENTITY_ENABLED 0\nconst float shadowIntervalSize = 3.0;\n"),
        ShaderSourceFile::new("world0/composite2.fsh", "#define SELECTED_END 0\n#define ENTITY_ENABLED 0\nconst int shadowMapResolution = 1200;\n"),
        ShaderSourceFile::new("world1/gbuffers_terrain.fsh", "#define SELECTED_END 1\n#define ENTITY_ENABLED 1\nconst int shadowMapResolution = 600;\n"),
        ShaderSourceFile::new("shaders.properties", concat!(
            "#if SELECTED_END\nendFlashShadows=true\nalphaTest.shadow=off\n#else\nendFlashShadows=false\nalphaTest.shadow=GREATER 0.25\n#endif\n",
            "#if ENTITY_ENABLED\nshadowEntities=true\n#else\nshadowEntities=false\n#endif\n",
            "shadowPlayer=true\nshadowTranslucent=false\nshadow.culling=true\n")),
    ]).unwrap()
}

#[test]
fn scoped_directive_shadow_uses_dimension_order_options_and_bounded_cache() {
    let source = source();
    let before = source.clone();
    let overworld = source
        .shadow_policy_for_scope(TerrainProgramScope::Overworld)
        .unwrap()
        .unwrap();
    assert_eq!(1200, overworld.resolution());
    assert_eq!(105.0, overworld.distance);
    assert_eq!(3.0, overworld.interval_size);
    assert_eq!(-25.0, overworld.sun_path_rotation_degrees());
    assert_eq!(Some(0.25), overworld.cutout_alpha_cutoff());
    assert!(!overworld.render_translucent());
    assert!(!overworld.casters().entities);
    assert!(overworld.casters().player);
    assert_eq!(
        overworld,
        source
            .shadow_policy_for_scope(TerrainProgramScope::Overworld)
            .unwrap()
            .unwrap()
    );
    let end = source
        .shadow_policy_for_scope(TerrainProgramScope::End)
        .unwrap()
        .unwrap();
    assert_eq!(600, end.resolution());
    assert_eq!(
        DEFAULT_SHADOW_DISTANCE, end.distance,
        "do not merge absent base directives"
    );
    assert!(end.supports_end_flash);
    assert!(end.casters().entities);
    assert_eq!(None, end.cutout_alpha_cutoff());
    assert!(end
        .uniforms(TerrainProgramScope::End, 0.0, [0.0; 3])
        .is_err());
    assert!(end
        .uniforms_with_end_flash(TerrainProgramScope::End, 0.0, [0.0; 3], Some([0.1, 0.2]))
        .is_ok());
    assert_eq!(
        source, before,
        "memo does not alter immutable source identity"
    );
    let cache = ShadowPolicies::default();
    assert_eq!(
        Some(overworld),
        cache.get(&source, TerrainProgramScope::Overworld).unwrap()
    );
    assert_eq!(
        Some(end),
        cache.get(&source, TerrainProgramScope::End).unwrap()
    );
    assert_eq!(
        2,
        cache.0.iter().filter(|slot| slot.get().is_some()).count()
    );
}

#[test]
fn scoped_directive_shadow_rejects_invalid_and_unsupported_active_semantics() {
    for declaration in [
        "const int shadowMapResolution = 4097;",
        "const float shadowDistance = NaN;",
        "const int shadowDistance = 80;",
        "const float shadowMapFov = 90.0;",
    ] {
        let source = ShaderPackSource::new(
            "bad-scope",
            1,
            vec![ShaderSourceFile::new("gbuffers_terrain.fsh", declaration)],
        )
        .unwrap();
        assert!(
            source
                .shadow_policy_for_scope(TerrainProgramScope::Default)
                .is_err(),
            "{declaration}"
        );
    }
    for declaration in [
        "const float shadowDistanceRenderMul = 0.5;",
        "const float entityShadowDistanceMul = 0.5;",
    ] {
        let source = ShaderPackSource::new(
            "caster-prerequisite",
            1,
            vec![ShaderSourceFile::new("gbuffers_terrain.fsh", declaration)],
        )
        .unwrap();
        let policy = source
            .shadow_policy_for_scope(TerrainProgramScope::Default)
            .unwrap()
            .unwrap();
        assert!(
            policy
                .uniforms(TerrainProgramScope::Default, 0.0, [0.0; 3])
                .is_ok(),
            "camera policy is independent of caster selection"
        );
        assert!(policy.caster_limits(None, ShadowCasterKind::Terrain).is_err());
        assert!(policy.caster_limits(Some(ShadowCasterFrameDistances {
            render_distance_blocks: 160.0, configured_shadow_distance_chunks: 32,
        }), ShadowCasterKind::Terrain).is_ok());
    }
    let source = ShaderPackSource::new(
        "comment-policy",
        1,
        vec![ShaderSourceFile::new(
            "gbuffers_terrain.fsh",
            "/* SHADOWRES:768 */\n/* SHADOWHPL:80.0 */\n",
        )],
    )
    .unwrap();
    let policy = source
        .shadow_policy_for_scope(TerrainProgramScope::Nether)
        .unwrap()
        .unwrap();
    assert_eq!(768, policy.resolution());
    assert_eq!(80.0, policy.distance);
}

#[test]
fn scoped_directive_empty_dimension_does_not_import_base_library_directives() {
    let source = ShaderPackSource::new("empty-dimension", 1, vec![
        ShaderSourceFile::new("gbuffers_terrain.vsh", "void main() {}"),
        ShaderSourceFile::new("gbuffers_terrain.fsh", "#include \"/lib/common.glsl\"\n"),
        ShaderSourceFile::new("lib/common.glsl", "const int shadowMapResolution = 512;\nconst float sunPathRotation = -25.0;\nconst int colortex0Format = RGBA16F;\n"),
        ShaderSourceFile::new("world1/gbuffers_terrain.vsh", "void main() {}"),
    ]).unwrap();
    assert_eq!(
        512,
        source
            .shadow_policy_for_scope(TerrainProgramScope::Overworld)
            .unwrap()
            .unwrap()
            .resolution()
    );
    assert_eq!(
        DEFAULT_SHADOW_RESOLUTION,
        source
            .shadow_policy_for_scope(TerrainProgramScope::End)
            .unwrap()
            .unwrap()
            .resolution()
    );
    assert_eq!(
        0.0,
        source
            .frame_uniform_policy(TerrainProgramScope::End)
            .unwrap()
            .sun_path_rotation_degrees
    );
    let bindings =
        crate::render::shaderpack::resources::bindings::TerrainSourceResourceBindings::from_source(
            &source,
        )
        .unwrap();
    let manifest = crate::render::shaderpack::resources::color_targets::ShaderPackColorTargetManifest::from_source_for_scope(&source, &bindings, TerrainProgramScope::End).unwrap();
    assert_eq!(
        crate::render::shaderpack::resources::color_targets::ShaderPackColorFormat::Rgba8,
        manifest.target_for_source_slot(0).unwrap().format
    );
}

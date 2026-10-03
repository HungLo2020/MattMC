use super::*;
use crate::render::shaderpack::source::ShaderSourceFile;

fn policy(mode: &str, terrain: f32, entity: f32) -> ShaderPackShadowPolicy {
    let source = ShaderPackSource::new("shadow-distance", 1, vec![
        ShaderSourceFile::new("gbuffers_terrain.fsh", format!(
            "const float shadowDistance = 80.0;\nconst float voxelDistance = 16.0;\nconst float shadowDistanceRenderMul = {terrain};\nconst float entityShadowDistanceMul = {entity};\n")),
        ShaderSourceFile::new("shaders.properties", format!("shadow.culling={mode}\n")),
    ]).unwrap();
    source
        .shadow_policy_for_scope(TerrainProgramScope::Default)
        .unwrap()
        .unwrap()
}
fn frame(normal: f32, user: i32) -> Option<ShadowCasterFrameDistances> {
    Some(ShadowCasterFrameDistances {
        render_distance_blocks: normal,
        configured_shadow_distance_chunks: user,
    })
}

#[test]
fn shadow_distance_selection_caps_positive_negative_and_distinct_entity_policies() {
    let positive = policy("true", 0.5, 0.25);
    assert_eq!(
        Some(40.0),
        positive
            .caster_limits(frame(160.0, 32), ShadowCasterKind::Terrain)
            .unwrap()
            .distance_limit
    );
    assert_eq!(
        Some(10.0),
        positive
            .caster_limits(frame(160.0, 32), ShadowCasterKind::Entity)
            .unwrap()
            .distance_limit
    );
    assert_eq!(
        None,
        positive
            .caster_limits(frame(40.0, 32), ShadowCasterKind::Terrain)
            .unwrap()
            .distance_limit,
        "advanced culling drops the distance box at the normal render-distance cap"
    );
    let negative = policy("true", -1.0, 0.25);
    assert_eq!(
        Some(32.0),
        negative
            .caster_limits(frame(160.0, 2), ShadowCasterKind::Terrain)
            .unwrap()
            .distance_limit
    );
    assert_eq!(
        Some(32.0),
        negative
            .caster_limits(frame(160.0, 2), ShadowCasterKind::Entity)
            .unwrap()
            .distance_limit,
        "a multiplied negative sentinel still selects the user's distance"
    );
    assert_eq!(
        None,
        negative
            .caster_limits(frame(160.0, 32), ShadowCasterKind::Terrain)
            .unwrap()
            .distance_limit
    );
    assert_eq!(
        Some(0.0),
        negative
            .caster_limits(frame(160.0, 0), ShadowCasterKind::Terrain)
            .unwrap()
            .distance_limit
    );
}

#[test]
fn shadow_distance_selection_preserves_distance_only_and_safe_zone_boundary_rules() {
    for multiplier in [-1.0, 0.0, 3.0] {
        let limits = policy("false", multiplier, 1.0)
            .caster_limits(frame(160.0, 1), ShadowCasterKind::Terrain)
            .unwrap();
        assert!(!limits.use_planes);
        assert_eq!(None, limits.distance_limit);
    }
    assert_eq!(
        Some(160.0),
        policy("false", 2.0, 1.0)
            .caster_limits(frame(160.0, 1), ShadowCasterKind::Terrain)
            .unwrap()
            .distance_limit,
        "distance-only culling keeps a box at equality with normal distance"
    );
    let safe = policy("reversed", -1.0, 1.0)
        .caster_limits(frame(160.0, 0), ShadowCasterKind::Terrain)
        .unwrap();
    assert!(safe.use_planes);
    assert_eq!(
        Some((16.0, 80.0)),
        safe.safe_zone,
        "negative safe-zone multipliers select one, independent of the slider"
    );
    let safe = policy("reversed", 0.5, 0.5)
        .caster_limits(frame(160.0, 32), ShadowCasterKind::Entity)
        .unwrap();
    assert_eq!(Some((4.0, 20.0)), safe.safe_zone);
    let invalid = policy("true", 0.5, 1.0);
    assert!(invalid
        .caster_limits(None, ShadowCasterKind::Terrain)
        .is_err());
    assert!(invalid
        .caster_limits(frame(f32::NAN, 32), ShadowCasterKind::Terrain)
        .is_err());
}

#[test]
fn shadow_distance_selection_constructs_explicit_box_frustums_and_requires_copied_inputs() {
    let policy = policy("false", 0.5, 1.0);
    let frustum = AdvancedShadowCasterFrustum::from_frame_with_distances(
        policy,
        0.0,
        identity(),
        identity(),
        frame(160.0, 32).unwrap(),
        ShadowCasterKind::Terrain,
    )
    .unwrap();
    assert!(
        frustum.intersects([40.0, -1.0, -1.0], [41.0, 1.0, 1.0]),
        "touching the exact distance boundary stays visible"
    );
    assert!(!frustum.intersects([40.001, -1.0, -1.0], [41.0, 1.0, 1.0]));
    assert!(AdvancedShadowCasterFrustum::from_frame(policy, 0.0, identity(), identity()).is_err());
}

#[test]
fn shadow_distance_selection_matches_compiled_frozen_frustum_decisions() {
    // Captured by FrozenShadowCullingProbe against the read-only Frozen
    // classes. This tests actual frustum decisions, not only our limits recipe.
    let fixture = include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../DevUtils/tests/rendering/fixtures/frozen_shadow_culling.tsv"
    ));
    let mut bounds = Vec::<[f32; 6]>::new();
    let mut decisions = 0;
    for (row, line) in fixture.lines().enumerate() {
        if line.starts_with('#') {
            continue;
        }
        let fields = line.split('\t').collect::<Vec<_>>();
        match fields[0] {
            "B" => {
                assert_eq!(fields.len(), 7);
                bounds.push(std::array::from_fn(|i| fields[i + 1].parse().unwrap()));
            }
            "F" => {
                assert_eq!(fields.len(), 8);
                let selected = policy(
                    fields[1],
                    fields[2].parse().unwrap(),
                    fields[3].parse().unwrap(),
                );
                let frustum = AdvancedShadowCasterFrustum::from_frame_with_distances(
                    selected,
                    0.0,
                    identity(),
                    identity(),
                    frame(fields[4].parse().unwrap(), fields[5].parse().unwrap()).unwrap(),
                    match fields[6] {
                        "terrain" => ShadowCasterKind::Terrain,
                        "entity" => ShadowCasterKind::Entity,
                        _ => panic!("unknown Frozen domain"),
                    },
                )
                .unwrap();
                assert_eq!(fields[7].len(), bounds.len());
                for (index, (b, expected)) in bounds.iter().zip(fields[7].bytes()).enumerate() {
                    assert!(expected == b'0' || expected == b'1');
                    assert_eq!(
                        frustum.intersects([b[0], b[1], b[2]], [b[3], b[4], b[5]]),
                        expected == b'1',
                        "Frozen fixture row {} bounds {index}: {line}",
                        row + 1
                    );
                    decisions += 1;
                }
            }
            _ => panic!("invalid Frozen fixture record"),
        }
    }
    assert_eq!(bounds.len(), 17);
    assert_eq!(decisions, 9_180);
}

#[test]
fn entity_shadow_culling_matches_frozen_world_bounds_at_large_camera_origins() {
    let fixture = include_str!(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../DevUtils/tests/rendering/fixtures/frozen_entity_bounds.tsv"
    ));
    let mut decisions = 0;
    for line in fixture.lines().filter(|line| !line.starts_with('#')) {
        let fields = line.split('\t').collect::<Vec<_>>();
        assert_eq!(fields.len(), 11);
        let b: [f64; 6] = std::array::from_fn(|i| fields[i + 4].parse().unwrap());
        let frustum = AdvancedShadowCasterFrustum::from_frame_with_distances(
            policy(fields[0], 0.5, 1.0),
            0.0,
            identity(),
            identity(),
            frame(160.0, 32).unwrap(),
            ShadowCasterKind::Entity,
        )
        .unwrap();
        assert_eq!(
            frustum.intersects_entity_world(
                b,
                std::array::from_fn(|i| fields[i + 1].parse().unwrap())
            ),
            fields[10].parse::<bool>().unwrap(),
            "Frozen world bounds: {line}"
        );
        decisions += 1;
    }
    assert_eq!(decisions, 108);
}

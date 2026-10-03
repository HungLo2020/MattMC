use super::*;
use crate::render::shaderpack::source::{ShaderPackSource, ShaderSourceFile};
use crate::render::worldrender::tests::{frame, mesh_instance};

fn policy(entity: f32, entities: bool, player: bool, blocks: bool) -> ShaderPackShadowPolicy {
    let source = ShaderPackSource::new("entity-cull",1,vec![
        ShaderSourceFile::new("gbuffers_terrain.fsh",format!("const float shadowDistance = 80.0;\nconst float shadowDistanceRenderMul = 1.0;\nconst float entityShadowDistanceMul = {entity};\n")),
        ShaderSourceFile::new("shaders.properties",format!("shadow.culling=false\nshadowEntities={entities}\nshadowPlayer={player}\nshadowBlockEntities={blocks}\n")),
    ]).unwrap();
    source
        .shadow_policy_for_scope(TerrainProgramScope::Default)
        .unwrap()
        .unwrap()
}

fn caster(key: u64, flags: u32, bounds: [f64; 6]) -> WorldMeshInstanceRequest {
    let mut instance = mesh_instance(key, 1);
    instance.stratum = WORLD_STRATUM_ENTITY_MESH;
    instance.entity_culling = Some(WorldEntityCullingInputs {
        flags,
        bounds,
        leash_holder_bounds: None,
        camera: [0.0; 3],
    });
    instance
}
fn selected(
    instances: Vec<WorldMeshInstanceRequest>,
    policy: ShaderPackShadowPolicy,
) -> GalResult<Vec<u64>> {
    let mut frame = frame(Vec::new());
    frame.shader_environment.far_plane = 160.0;
    frame.shader_environment.configured_shadow_distance_chunks = 32;
    frame.mesh_instances = instances;
    Ok(select_source_entity_shadow_casters(&frame, policy)?
        .iter()
        .map(|i| i.mesh_key)
        .collect())
}

#[test]
fn entity_shadow_culling_selects_distinct_frustum_and_preserves_frozen_absolute_float_box_edges() {
    let mut center = [-0.5; 6];
    center[3..].fill(0.5);
    let edge = caster(1, ENTITY_CULL_ELIGIBLE, [40.0, -0.5, -0.5, 41.0, 0.5, 0.5]);
    let outside = caster(
        2,
        ENTITY_CULL_ELIGIBLE,
        [40.0 + 1e-5, -0.5, -0.5, 41.0, 0.5, 0.5],
    );
    let near = caster(3, ENTITY_CULL_ELIGIBLE, center);
    assert_eq!(
        selected(
            vec![edge.clone(), outside.clone(), near.clone()],
            policy(0.5, true, false, false)
        )
        .unwrap(),
        vec![1, 3]
    );
    assert_eq!(
        selected(vec![edge, outside, near], policy(0.25, true, false, false)).unwrap(),
        vec![3]
    );
}

#[test]
fn entity_shadow_culling_handles_leash_union_forced_visibility_and_eligibility() {
    let outside = [50.0, 50.0, 50.0, 51.0, 51.0, 51.0];
    let mut leash = caster(1, ENTITY_CULL_ELIGIBLE | ENTITY_CULL_LEASH_HOLDER, outside);
    leash.entity_culling.as_mut().unwrap().leash_holder_bounds =
        Some([-51.0, -51.0, -51.0, -50.0, -50.0, -50.0]);
    let forced = caster(
        2,
        ENTITY_CULL_ELIGIBLE | ENTITY_CULL_BYPASS_FRUSTUM | ENTITY_CULL_UNRESOLVED_HOOKS,
        outside,
    );
    let ineligible = caster(3, ENTITY_CULL_BYPASS_FRUSTUM, outside);
    assert_eq!(
        selected(
            vec![leash, forced, ineligible],
            policy(0.25, true, false, false)
        )
        .unwrap(),
        vec![1, 2]
    );
    assert!(selected(
        vec![caster(
            4,
            ENTITY_CULL_ELIGIBLE | ENTITY_CULL_UNRESOLVED_HOOKS,
            outside
        )],
        policy(1.0, true, false, false)
    )
    .is_err());
}

#[test]
fn entity_shadow_culling_preserves_independent_block_flags_and_player_only_role() {
    let outside = [500.0, 500.0, 500.0, 501.0, 501.0, 501.0];
    let ordinary = caster(1, ENTITY_CULL_ELIGIBLE | ENTITY_CULL_PLAYER_GROUP, outside);
    let mut player = caster(
        2,
        ENTITY_CULL_PLAYER_GROUP | ENTITY_CULL_PLAYER_ONLY_VARIANT | ENTITY_CULL_UNRESOLVED_HOOKS,
        outside,
    );
    player.stratum = WORLD_STRATUM_ENTITY_SHADOW_CASTER;
    let mut block = mesh_instance(3, 1);
    block.stratum = WORLD_STRATUM_ENTITY_MESH;
    block.block_entity_id = 42;
    assert_eq!(
        selected(
            vec![ordinary.clone(), player.clone(), block.clone()],
            policy(0.125, false, true, true)
        )
        .unwrap(),
        vec![2, 3]
    );
    assert_eq!(
        selected(
            vec![ordinary, player, block],
            policy(0.125, true, true, true)
        )
        .unwrap(),
        vec![3]
    );
    let mut absent = mesh_instance(4, 1);
    absent.stratum = WORLD_STRATUM_ENTITY_MESH;
    assert!(selected(vec![absent], policy(1.0, true, false, false)).is_err());
}

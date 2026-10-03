//! Source shadow entity selection from copied gameplay bounds and extraction roles.

use super::*;
use crate::render::shaderpack::properties::shadow::{
    AdvancedShadowCasterFrustum, ShaderPackShadowPolicy, ShadowCasterFrameDistances,
    ShadowCasterKind,
};
use crate::render::worldrender::frame::entity_culling::*;

pub(crate) fn select_source_entity_shadow_casters(
    frame: &WorldPrimitiveFrame,
    policy: ShaderPackShadowPolicy,
) -> GalResult<Vec<WorldMeshInstanceRequest>> {
    let directives = policy.casters();
    let mut frustum = None;
    let mut selected = Vec::new();
    for instance in &frame.mesh_instances {
        if !matches!(
            instance.stratum,
            WORLD_STRATUM_ENTITY_MESH | WORLD_STRATUM_ENTITY_SHADOW_CASTER
        ) || instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0
            || instance.item_foil.is_some()
            || instance.decal_foil.is_some()
        {
            continue;
        }
        let admitted = if instance.block_entity_id != -1 {
            // Frozen obtains block entities from its separate visible-section
            // list. Entity directives and entity frustums never select them.
            directives.block_entities
        } else if let Some(inputs) = instance.entity_culling {
            inputs.validate()?;
            if inputs.flags & ENTITY_CULL_PLAYER_ONLY_VARIANT != 0 {
                !directives.entities && directives.player
            } else if !directives.entities || inputs.flags & ENTITY_CULL_ELIGIBLE == 0 {
                false
            } else if inputs.flags & ENTITY_CULL_BYPASS_FRUSTUM != 0 {
                true
            } else {
                if inputs.flags & ENTITY_CULL_UNRESOLVED_HOOKS != 0 {
                    return Err(GalError::unsupported_feature(
                        "entity shadow selection requires resolved CPU hook semantics",
                    ));
                }
                if frustum.is_none() {
                    frustum = Some(AdvancedShadowCasterFrustum::from_frame_with_distances(
                        policy,
                        frame.shader_environment.time_of_day,
                        frame.projection_matrix,
                        frame.view_matrix,
                        ShadowCasterFrameDistances {
                            render_distance_blocks: frame.shader_environment.far_plane,
                            configured_shadow_distance_chunks: frame
                                .shader_environment
                                .configured_shadow_distance_chunks,
                        },
                        ShadowCasterKind::Entity,
                    )?);
                }
                entity_bounds_visible(frustum.as_ref().unwrap(), inputs)
            }
        } else {
            let requested = directives.entities
                || (instance.stratum == WORLD_STRATUM_ENTITY_SHADOW_CASTER && directives.player);
            if requested && policy.requires_copied_entity_culling() {
                return Err(GalError::unsupported_feature(
                    "source entity shadow selection requires copied gameplay culling bounds",
                ));
            }
            // Explicit compact fixtures predate entity culling transport.
            requested
        };
        if admitted {
            let mut copied = instance.clone();
            copied.stratum = WORLD_STRATUM_ENTITY_MESH;
            copied.flags = 0;
            copied.outline_color_argb = 0;
            copied.model_submission_order = None;
            if copied.depth_policy == WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE {
                copied.depth_policy = WORLD_DEPTH_POLICY_TEST_WRITE;
            }
            selected.push(copied);
        }
    }
    Ok(selected)
}

fn entity_bounds_visible(
    frustum: &AdvancedShadowCasterFrustum,
    inputs: WorldEntityCullingInputs,
) -> bool {
    fn visible(frustum: &AdvancedShadowCasterFrustum, b: [f64; 6], camera: [f64; 3]) -> bool {
        frustum.intersects_entity_world(b, camera)
    }
    if visible(frustum, inputs.bounds, inputs.camera) {
        return true;
    }
    inputs.leash_holder_bounds.is_some_and(|holder| {
        visible(frustum, holder, inputs.camera)
            || visible(
                frustum,
                std::array::from_fn(|i| {
                    if i < 3 {
                        inputs.bounds[i].min(holder[i])
                    } else {
                        inputs.bounds[i].max(holder[i])
                    }
                }),
                inputs.camera,
            )
    })
}

#[cfg(test)]
mod tests;

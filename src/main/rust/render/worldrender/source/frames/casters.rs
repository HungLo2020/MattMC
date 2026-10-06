//! Source shadow entity selection from copied gameplay bounds and extraction roles.

use super::*;
use crate::render::shaderpack::properties::shadow::{
    AdvancedShadowCasterFrustum, ShaderPackShadowPolicy, ShadowCasterFrameDistances,
    ShadowCasterKind,
};
use crate::render::worldrender::frame::entity_culling::*;

/// Frame inputs of Iris's entity shadow frustum.
#[derive(Clone, Copy, Debug)]
pub(crate) struct EntityShadowFrame {
    pub time_of_day: f32,
    pub projection: [f32; 16],
    pub view: [f32; 16],
    pub distances: ShadowCasterFrameDistances,
}

impl EntityShadowFrame {
    fn of(frame: &WorldPrimitiveFrame) -> Self {
        Self {
            time_of_day: frame.shader_environment.time_of_day,
            projection: frame.projection_matrix,
            view: frame.view_matrix,
            distances: ShadowCasterFrameDistances {
                render_distance_blocks: frame.shader_environment.far_plane,
                configured_shadow_distance_chunks: frame.shader_environment.configured_shadow_distance_chunks,
            },
        }
    }
}

/// Iris's shadow-pass admission of one entity from its copied culling facts
/// (`ShadowRenderer.extractVisibleEntities`). The frustum is built on first use.
pub(crate) struct EntityShadowAdmission {
    policy: ShaderPackShadowPolicy,
    frame: EntityShadowFrame,
    frustum: Option<AdvancedShadowCasterFrustum>,
}

impl EntityShadowAdmission {
    pub(crate) fn new(policy: ShaderPackShadowPolicy, frame: EntityShadowFrame) -> Self {
        Self { policy, frame, frustum: None }
    }

    pub(crate) fn admits(&mut self, inputs: WorldEntityCullingInputs) -> GalResult<bool> {
        inputs.validate()?;
        let directives = self.policy.casters();
        if inputs.flags & ENTITY_CULL_PLAYER_ONLY_VARIANT != 0 {
            return Ok(!directives.entities && directives.player);
        }
        if !directives.entities || inputs.flags & ENTITY_CULL_ELIGIBLE == 0 {
            return Ok(false);
        }
        if inputs.flags & ENTITY_CULL_BYPASS_FRUSTUM != 0 {
            return Ok(true);
        }
        if inputs.flags & ENTITY_CULL_UNRESOLVED_HOOKS != 0 {
            return Err(GalError::unsupported_feature(
                "entity shadow selection requires resolved CPU hook semantics",
            ));
        }
        if self.frustum.is_none() {
            self.frustum = Some(AdvancedShadowCasterFrustum::from_frame_with_distances(
                self.policy,
                self.frame.time_of_day,
                self.frame.projection,
                self.frame.view,
                self.frame.distances,
                ShadowCasterKind::Entity,
            )?);
        }
        Ok(entity_bounds_visible(self.frustum.as_ref().unwrap(), inputs))
    }
}

/// The overworld shadow policy of the active pack, shared with a standalone
/// query handle so Java can skip extracting entities the shadow pass would
/// reject without joining a pipelined frame.
pub(crate) type SharedEntityShadowPolicy = std::sync::Arc<std::sync::Mutex<Option<ShaderPackShadowPolicy>>>;

/// Prefilter for Java's shadow-only entity extraction. It applies exactly the
/// admission the frame plan applies later, from the same copied inputs.
pub(crate) struct EntityShadowQuery {
    policy: SharedEntityShadowPolicy,
}

impl EntityShadowQuery {
    pub(crate) fn new(policy: SharedEntityShadowPolicy) -> Self {
        Self { policy }
    }

    /// Writes 1 for each candidate the shadow pass admits. Returns `None`
    /// when admission cannot be decided here (no active policy, or an input
    /// the frame plan must reject itself); the caller then keeps every candidate.
    pub(crate) fn select(
        &self,
        sky_type: u32,
        frame: EntityShadowFrame,
        candidates: impl ExactSizeIterator<Item = WorldEntityCullingInputs>,
        out: &mut [u8],
    ) -> Option<()> {
        if candidates.len() != out.len() {
            return None;
        }
        // Entity casters exist only in the overworld shadow pass.
        if terrain_program_scope_for_sky_type(sky_type).ok()? != Some(TerrainProgramScope::Overworld) {
            out.fill(0);
            return Some(());
        }
        let policy = (*self.policy.lock().ok()?)?;
        let mut admission = EntityShadowAdmission::new(policy, frame);
        for (inputs, slot) in candidates.zip(out.iter_mut()) {
            *slot = u8::from(admission.admits(inputs).ok()?);
        }
        Some(())
    }
}

pub(crate) fn select_source_entity_shadow_casters(
    frame: &WorldPrimitiveFrame,
    policy: ShaderPackShadowPolicy,
) -> GalResult<Vec<WorldMeshInstanceRequest>> {
    let directives = policy.casters();
    let mut admission = EntityShadowAdmission::new(policy, EntityShadowFrame::of(frame));
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
            admission.admits(inputs)?
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

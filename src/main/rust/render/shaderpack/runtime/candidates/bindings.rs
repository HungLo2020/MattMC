//! Required resource roles and resource/asset binding plans of the selected source.

use super::*;

impl ShaderPackRuntimeExecutor {
    /// Returns the validated, source-derived semantic resource plan for the
    /// current candidate. This is preparation metadata only: it contains no
    /// resource handles and cannot select a route or issue a draw.
    pub(crate) fn source_resource_binding_plan(
        &self,
    ) -> Option<&TerrainSourceOpaqueResourceBindingPlan> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_resource_bindings,
                ..
            } => source_resource_bindings.as_ref(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        }
    }

    /// Returns every pass-local source plan that can share copied semantic
    /// resources in one Rust-owned frame. The plans remain deliberately
    /// separate at descriptor-set creation: terrain, shadow, Distant Horizons,
    /// and later fullscreen consumers all keep their own binding layouts.
    /// This union is only for generation/coherence and missing-resource
    /// validation, never a hidden cross-pass resource set.
    pub(crate) fn source_resource_binding_plans(&self) -> Vec<&TerrainSourceOpaqueResourceBindingPlan> {
        self.source_resource_binding_plans_for_frame(true)
    }

    /// A selected source frame only requires Distant Horizons bindings when
    /// that exact frame contains DH ranges. Discovery keeps the DH plans for
    /// validation and later execution, but it must not make ordinary vanilla
    /// terrain wait for a far-depth resource that no producer requested.
    pub(crate) fn source_resource_binding_plans_for_frame(
        &self,
        includes_distant_horizons: bool,
    ) -> Vec<&TerrainSourceOpaqueResourceBindingPlan> {
        let mut plans = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_resource_bindings,
                source_shadow_resource_bindings,
                translucent_source_resource_bindings,
                pre_terrain_preparation,
                post_terrain_preparation,
                ..
            } => source_resource_bindings
                .iter()
                .chain(source_shadow_resource_bindings.iter())
                .chain(translucent_source_resource_bindings.iter())
                .chain(pre_terrain_preparation.iter().chain(post_terrain_preparation.iter())
                    .filter_map(|stage| stage.source_program.as_ref())
                    .map(|program| &program.opaque_resource_bindings))
                .collect(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Vec::new(),
        };
        if includes_distant_horizons {
            if let DistantHorizonsSourceCandidateState::Discovered {
                source_resource_bindings,
                depth_consumer_preparation,
                pre_terrain_preparation,
                ..
            } = &self.distant_horizons_source_candidate
            {
                plans.extend(source_resource_bindings.iter());
                plans.extend(
                    pre_terrain_preparation.iter()
                        .filter_map(|stage| stage.source_program.as_ref())
                        .map(|program| &program.opaque_resource_bindings),
                );
                plans.extend(
                    depth_consumer_preparation
                        .iter()
                        .filter_map(|consumer| consumer.source_program.as_ref())
                        .map(|program| &program.opaque_resource_bindings),
                );
            }
        }
        plans
    }

    /// Records the roles a prepared writer program binds. Returns whether a
    /// role was new, so the caller can let the next frame stage it.
    pub(crate) fn note_writer_required_roles(
        &self,
        roles: impl IntoIterator<Item = TerrainSourceResourceRole>,
    ) -> bool {
        let mut noted = self.writer_required_roles.borrow_mut();
        if noted.0 != self.source_candidate_epoch {
            *noted = (self.source_candidate_epoch, BTreeSet::new());
        }
        let before = noted.1.len();
        noted.1.extend(roles);
        noted.1.len() != before
    }

    pub(crate) fn source_required_resource_roles(&self) -> BTreeSet<TerrainSourceResourceRole> {
        self.source_required_resource_roles_for_frame(true)
    }

    pub(crate) fn source_required_resource_roles_for_frame(
        &self,
        includes_distant_horizons: bool,
    ) -> BTreeSet<TerrainSourceResourceRole> {
        let mut roles = self
            .source_resource_binding_plans_for_frame(includes_distant_horizons)
            .into_iter()
            .flat_map(|bindings| bindings.bindings().iter().map(|binding| binding.role()))
            .collect::<BTreeSet<_>>();
        // A source shadow storage declaration is removed from the lowered
        // GLSL only after a named Rust semantic producer takes responsibility
        // for its update. Retain that role in completeness checks so the
        // producer cannot disappear merely because the rewritten program no
        // longer binds the legacy image directly.
        if let TerrainSourceCandidateState::Discovered {
            source_lowered_shadow_pair: Some(shadow),
            ..
        } = &self.source_candidate
        {
            roles.extend(shadow.owned_storage_roles().iter().cloned());
        }
        let noted = self.writer_required_roles.borrow();
        if noted.0 == self.source_candidate_epoch {
            // Only roles a candidate producer can supply are added; writer
            // outputs (colour targets) are staged per writer, not here.
            roles.extend(noted.1.iter().cloned().filter(|role| {
                matches!(
                    role,
                    TerrainSourceResourceRole::ShadowDepthPrimary
                        | TerrainSourceResourceRole::ShadowDepthSecondary
                        | TerrainSourceResourceRole::ShadowDepthRaw
                        | TerrainSourceResourceRole::ShadowDepthRawSecondary
                )
            }));
        }
        roles
    }

    /// Returns source-derived PNG sampler paths for diagnostics and a future
    /// Rust-owned resource preparer. This has no native resource identity and
    /// cannot select a shader or rendering route.
    pub(crate) fn source_asset_binding_plan(&self) -> Option<&TerrainShaderPackAssetBindings> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_asset_bindings,
                ..
            } => source_asset_bindings.as_ref(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        }
    }
}

//! GAL residency for pack assets, material textures, shadow and main-depth inputs.

use super::*;

/// Rust-internal handoff from the world texture cache to the shader runtime.
/// It has no Java, OpenGL, Vulkan, or native-handle representation.
#[derive(Clone, Debug)]
pub(crate) struct TerrainSourceMaterialTextureInput {
    pub role: TerrainSourceResourceRole,
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub mesh_asset_generation: u64,
    pub texture_view: Handle,
    pub sampler: Handle,
}

/// Rust-internal handoff from the owned terrain runtime targets to source
/// resource preparation. This is intentionally a GAL view identity only:
/// neither backend objects nor shader-pack-specific binding slots escape.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainSourceShadowDepthInput {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub shader_graph_generation: u64,
    pub shadow_depth_view: Handle,
    /// Pre-translucent shadow depth (`shadowtex1`); NULL aliases the primary.
    pub shadow_depth_secondary_view: Handle,
}

/// Rust-internal handoff from a future owned shadow-color target. The input
/// carries only GAL resource identities and generation semantics; it has no
/// Java, Iris, OpenGL, Vulkan, or native-handle representation.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainSourceShadowColorInput {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub shader_graph_generation: u64,
    pub shadow_color_view: Handle,
    pub shadow_color_secondary_view: Handle,
    pub sampler: Handle,
}

/// Rust-internal handoff from the owned G-buffer to source-resource
/// preparation. Optional snapshots are present only after their own combined
/// frame submission was confirmed by the frontend.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainSourceMainDepthInput {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub shader_graph_generation: u64,
    pub main_depth_view: Handle,
    pub before_translucency_view: Option<Handle>,
    pub previous_view: Option<Handle>,
    pub sampler: Handle,
}

#[derive(Debug)]
pub(super) struct TerrainSourceMaterialTextureResources {
    pub(super) role: TerrainSourceResourceRole,
    pub(super) shader_pack_generation: u64,
    pub(super) world_generation: u64,
    pub(super) mesh_asset_generation: u64,
    pub(super) texture_view: Handle,
    pub(super) sampler: Handle,
    pub(super) combined_sampler: Handle,
}

impl TerrainSourceMaterialTextureResources {
    /// The wrapper pairs one texture view with one sampler; it stays valid
    /// while both handles do (GAL handles are unique per creation). Keying it
    /// on the mesh-asset generation replaced it — and retired every pack
    /// set-one and DH source draw set — on each streamed mesh update.
    pub(super) fn compatible_with(&self, input: &TerrainSourceMaterialTextureInput) -> bool {
        self.role == input.role
            && self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.texture_view == input.texture_view
            && self.sampler == input.sampler
    }

    pub(super) fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        let availability = crate::render::shaderpack::resources::bindings::TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            [crate::render::shaderpack::resources::bindings::TerrainSourceResourceAvailability {
                role: self.role.clone(),
                shape: crate::render::shaderpack::resources::bindings::TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: self.shader_pack_generation,
            }],
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [
                crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResource {
                    role: self.role.clone(),
                    combined_sampler: self.combined_sampler,
                },
            ],
        )
    }
}

#[derive(Debug)]
pub(super) struct TerrainSourceShadowDepthResources {
    pub(super) shader_pack_generation: u64,
    pub(super) world_generation: u64,
    pub(super) shader_graph_generation: u64,
    pub(super) primary_sampler: Handle,
    pub(super) secondary_sampler: Handle,
    pub(super) raw_sampler: Option<Handle>,
    pub(super) primary_combined_sampler: Handle,
    pub(super) secondary_combined_sampler: Handle,
    pub(super) raw_combined_sampler: Option<Handle>,
}

#[derive(Debug)]
pub(super) struct TerrainSourceShadowColorResources {
    pub(super) shader_pack_generation: u64,
    pub(super) world_generation: u64,
    pub(super) shader_graph_generation: u64,
    pub(super) shadow_color_view: Handle,
    pub(super) shadow_color_secondary_view: Handle,
    pub(super) sampler: Handle,
    pub(super) combined_samplers: BTreeMap<TerrainSourceResourceRole, Handle>,
}

#[derive(Debug)]
pub(super) struct TerrainSourceMainDepthResources {
    pub(super) shader_pack_generation: u64,
    pub(super) world_generation: u64,
    pub(super) shader_graph_generation: u64,
    pub(super) main_depth_view: Handle,
    pub(super) before_translucency_view: Option<Handle>,
    pub(super) previous_view: Option<Handle>,
    pub(super) sampler: Handle,
    pub(super) combined_samplers: BTreeMap<TerrainSourceResourceRole, Handle>,
}

impl TerrainSourceShadowDepthResources {
    pub(super) fn compatible_with(&self, input: TerrainSourceShadowDepthInput) -> bool {
        self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.shader_graph_generation == input.shader_graph_generation
    }

    pub(super) fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        use crate::render::shaderpack::resources::bindings::{
            TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
            TerrainSourceSampledResourceShape,
        };

        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ShadowDepthPrimary,
                    shape: TerrainSourceSampledResourceShape::DepthCompareTexture2d,
                    resource_generation: self.shader_graph_generation,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ShadowDepthSecondary,
                    shape: TerrainSourceSampledResourceShape::DepthCompareTexture2d,
                    resource_generation: self.shader_graph_generation,
                },
            ]
            .into_iter()
            .chain(self.raw_combined_sampler.map(|_| {
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ShadowDepthRaw,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: self.shader_graph_generation,
                }
            })),
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [
                crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ShadowDepthPrimary,
                    combined_sampler: self.primary_combined_sampler,
                },
                crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ShadowDepthSecondary,
                    combined_sampler: self.secondary_combined_sampler,
                },
            ]
            .into_iter()
            .chain(self.raw_combined_sampler.map(|combined_sampler| {
                crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ShadowDepthRaw,
                    combined_sampler,
                }
            })),
        )
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        for handle in [
            self.raw_combined_sampler,
            Some(self.secondary_combined_sampler),
            Some(self.primary_combined_sampler),
            self.raw_sampler,
            Some(self.secondary_sampler),
            Some(self.primary_sampler),
        ]
        .into_iter()
        .flatten()
        {
            gal.destroy(handle)?;
        }
        Ok(())
    }
}

impl TerrainSourceShadowColorResources {
    pub(super) fn compatible_with(&self, input: TerrainSourceShadowColorInput) -> bool {
        self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.shader_graph_generation == input.shader_graph_generation
            && self.shadow_color_view == input.shadow_color_view
            && self.shadow_color_secondary_view == input.shadow_color_secondary_view
            && self.sampler == input.sampler
    }

    pub(super) fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        use crate::render::shaderpack::resources::bindings::{
            TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
            TerrainSourceSampledResourceShape,
        };

        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            self.combined_samplers
                .keys()
                .cloned()
                .map(|role| TerrainSourceResourceAvailability {
                    role,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: self.shader_graph_generation,
                }),
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            self.combined_samplers
                .iter()
                .map(|(role, &combined_sampler)| {
                    crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResource {
                        role: role.clone(),
                        combined_sampler,
                    }
                }),
        )
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        for (_, combined_sampler) in self.combined_samplers.into_iter().rev() {
            gal.destroy(combined_sampler)?;
        }
        Ok(())
    }
}

impl TerrainSourceMainDepthResources {
    pub(super) fn compatible_with(&self, input: TerrainSourceMainDepthInput) -> bool {
        self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.shader_graph_generation == input.shader_graph_generation
            && self.main_depth_view == input.main_depth_view
            && self.before_translucency_view == input.before_translucency_view
            && self.previous_view == input.previous_view
            && self.sampler == input.sampler
    }

    pub(super) fn base_compatible_with(&self, input: TerrainSourceMainDepthInput) -> bool {
        self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.shader_graph_generation == input.shader_graph_generation
            && self.main_depth_view == input.main_depth_view
            && self.sampler == input.sampler
    }

    pub(super) fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        use crate::render::shaderpack::resources::bindings::{
            TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
            TerrainSourceSampledResourceShape,
        };

        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            self.combined_samplers
                .keys()
                .cloned()
                .map(|role| TerrainSourceResourceAvailability {
                    role,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: self.shader_graph_generation,
                }),
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            self.combined_samplers
                .iter()
                .map(|(role, &combined_sampler)| {
                    crate::render::shaderpack::resources::bindings::TerrainSourceOwnedResource {
                        role: role.clone(),
                        combined_sampler,
                    }
                }),
        )
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        for (_, handle) in self.combined_samplers.into_iter().rev() {
            gal.destroy(handle)?;
        }
        Ok(())
    }
}

impl ShaderPackRuntimeExecutor {
    /// Creates generation-coherent copied PNG resources for the active,
    /// lowered source terrain plan. This is a private preparation operation:
    /// it deliberately does not construct a program resource set, bind a
    /// pipeline, or make source-selected terrain executable.
    pub(crate) fn ensure_candidate_source_asset_resources(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &ShaderPackAssets,
    ) -> GalResult<bool> {
        let (generation, pack_name, asset_bindings) = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                generation,
                pack_name,
                source_asset_bindings: Some(asset_bindings),
                ..
            } => (*generation, pack_name.as_str(), asset_bindings),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. }
            | TerrainSourceCandidateState::Discovered { .. } => return Ok(false),
        };
        if assets.generation() != generation || assets.pack_name() != pack_name {
            return Err(GalError::invalid_argument(format!(
                "shader-pack asset resources '{}'/{} do not match source candidate '{}'/{}",
                assets.pack_name(),
                assets.generation(),
                pack_name,
                generation
            )));
        }
        if self
            .source_asset_resources
            .as_ref()
            .is_some_and(|resources| {
                resources.generation() == generation && resources.pack_name() == pack_name
            })
        {
            return Ok(false);
        }
        let resource_binding_plans = self.source_resource_binding_plans();
        if resource_binding_plans.is_empty() {
            return Ok(false);
        }
        let replacement = TerrainSourceAssetResources::create(
            gal,
            assets,
            asset_bindings,
            &resource_binding_plans,
        )?;
        if let Some(previous) = self.source_asset_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        Ok(true)
    }

    /// Discards copied PNG preparation when its matching source/assets no
    /// longer form a complete generation. This does not affect the fixture
    /// plan or source-candidate discovery state.
    pub(crate) fn clear_candidate_source_asset_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        self.discard_vanilla_lightmap_submission(gal);
        if let Some(resources) = self.vanilla_lightmap_residency.take() {
            resources.destroy(gal)?;
        }
        for resources in self.retired_vanilla_lightmap_residencies.drain(..) {
            resources.destroy(gal)?;
        }
        self.clear_candidate_source_shadow_depth_resources(gal)?;
        self.clear_candidate_source_shadow_color_resources(gal)?;
        self.clear_candidate_source_material_texture_resources(gal)?;
        if let Some(previous) = self.source_asset_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn has_candidate_source_asset_resources(&self) -> bool {
        self.source_asset_resources.is_some()
    }

    /// Builds the active copied-PNG semantic subset for a concrete world
    /// generation. It is intentionally incomplete when atlas, lightmap,
    /// shadow, or voxel roles have not yet been supplied by other Rust-owned
    /// runtime components.
    pub(crate) fn candidate_source_asset_resource_set(
        &self,
        world_generation: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let Some(resources) = self.source_asset_resources.as_ref() else {
            return Ok(None);
        };
        let binding_plans = self.source_resource_binding_plans();
        if binding_plans.is_empty() {
            return Ok(None);
        }
        Ok(Some(resources.declared_semantic_resources(
            &binding_plans,
            world_generation,
        )?))
    }

    /// Reports whether the lowered candidate references a semantic resource
    /// role. This keeps world-resource preparation driven by source lowering,
    /// not by pack filenames or backend-specific binding slots.
    pub(crate) fn candidate_source_requires_resource(
        &self,
        role: TerrainSourceResourceRole,
    ) -> bool {
        self.source_required_resource_roles().contains(&role)
    }

    /// Exact-frame source completeness. DH-only inputs participate only once
    /// the frame has real selected-route DH work; the DH program itself still
    /// validates its complete resource plan before any far draw is staged.
    pub(crate) fn candidate_source_missing_resource_roles_for_frame(
        &self,
        prepared: Option<&TerrainSourceOwnedResourceSet>,
        includes_distant_horizons: bool,
    ) -> Vec<TerrainSourceResourceRole> {
        self.candidate_source_missing_resource_roles_for_frame_with_declared_outputs(
            prepared,
            includes_distant_horizons,
            [],
        )
    }

    /// The complete source graph can own an image before an individual
    /// program-local sampler wrapper exists for it. `declared_outputs` covers
    /// exactly those Rust-owned graph targets; it does not authorize a draw,
    /// relax a program's binding validation, or substitute an external
    /// resource. Every consuming program still creates and validates its
    /// precise sampler/resource set when the combined source frame executes.
    pub(crate) fn candidate_source_missing_resource_roles_for_frame_with_declared_outputs(
        &self,
        prepared: Option<&TerrainSourceOwnedResourceSet>,
        includes_distant_horizons: bool,
        declared_outputs: impl IntoIterator<Item = TerrainSourceResourceRole>,
    ) -> Vec<TerrainSourceResourceRole> {
        let declared_outputs = declared_outputs.into_iter().collect::<BTreeSet<_>>();
        self.source_required_resource_roles_for_frame(includes_distant_horizons)
            .into_iter()
            .filter(|role| {
                !declared_outputs.contains(role)
                    && prepared
                        .and_then(|set| set.availability().resource_for(role.clone()))
                        .is_none()
            })
            .collect()
    }

    /// Returns the parity-correct owned voxel subset only after the matching
    /// source candidate and D3 generation are fully confirmed. This is not a
    /// source-program binding and cannot alter route selection.
    pub(crate) fn candidate_colored_light_resource_set(
        &self,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let source_generation = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        let Some(runtime) = self.terrain_colored_light.as_ref() else {
            return Ok(None);
        };
        if runtime.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "colored voxel-light resources do not match the discovered shader-pack generation",
            ));
        }
        if !runtime.is_ready_for_frame(frame_counter) {
            return Ok(None);
        }
        runtime
            .semantic_resource_set_for_frame(frame_counter)
            .map(Some)
    }

    /// Returns resources which are ordered for the current combined
    /// submission but intentionally not yet confirmed. Callers must append
    /// the producing operations before the terrain draw and either confirm
    /// or discard the same runtime transaction with that submission.
    pub(crate) fn candidate_colored_light_resource_set_for_pending_submission(
        &self,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let source_generation = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        let Some(runtime) = self.terrain_colored_light.as_ref() else {
            return Ok(None);
        };
        if runtime.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "colored voxel-light resources do not match the discovered shader-pack generation",
            ));
        }
        if !runtime.has_pending_submission() {
            return Ok(None);
        }
        // The first source-owned occupancy frame legitimately uploads the
        // derived emission/tint tables and mapping before it can dispatch or
        // sample flood-fill output. That incomplete transaction is not an
        // asset failure and must not tear down material/color preparation;
        // the normal Rust graph confirms it, then a later exact frame may
        // expose the pending read-after-write resource set.
        if !runtime.pending_sampling_ready_for_frame(frame_counter) {
            return Ok(None);
        }
        runtime
            .semantic_resource_set_for_pending_submission(frame_counter)
            .map(Some)
    }

    /// Returns the source-derived puddle field only after the exact owned
    /// upload has completed. The result is semantic resource metadata; it
    /// neither binds a program nor changes source-route admission.
    pub(crate) fn candidate_puddle_resource_set(
        &self,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let source_generation = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        if !self.candidate_source_requires_resource(TerrainSourceResourceRole::PuddleOccupancy) {
            return Ok(None);
        }
        let Some(runtime) = self.terrain_puddle.as_ref() else {
            return Ok(None);
        };
        if runtime.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "puddle occupancy resources do not match the discovered shader-pack generation",
            ));
        }
        if !runtime.is_ready() {
            return Ok(None);
        }
        runtime.semantic_resource_set().map(Some)
    }

    /// Same-submission resource table for the exact ordered puddle upload.
    /// This is valid only while the enclosing world submission remains
    /// pending; confirmation or discard follows that single transaction.
    pub(crate) fn candidate_puddle_resource_set_for_pending_submission(
        &self,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let source_generation = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        if !self.candidate_source_requires_resource(TerrainSourceResourceRole::PuddleOccupancy) {
            return Ok(None);
        }
        let Some(runtime) = self.terrain_puddle.as_ref() else {
            return Ok(None);
        };
        if runtime.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "pending puddle occupancy resources do not match the discovered shader-pack generation",
            ));
        }
        if !runtime.has_pending_submission() {
            return Ok(None);
        }
        runtime
            .semantic_resource_set_for_pending_submission()
            .map(Some)
    }

    pub(crate) fn candidate_puddle_diagnostic_state(&self) -> Option<TerrainPuddleDiagnosticState> {
        self.terrain_puddle
            .as_ref()
            .map(TerrainPuddleRuntime::diagnostic_state)
    }

    /// Creates a source-runtime-owned semantic material texture wrapper. The
    /// supplied view and sampler already belong to Rust's world texture cache;
    /// this method owns only the role-specific combined source-resource object.
    pub(crate) fn ensure_candidate_source_material_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceMaterialTextureInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if !matches!(
            input.role,
            TerrainSourceResourceRole::MaterialTexture
                | TerrainSourceResourceRole::MaterialAtlas
                | TerrainSourceResourceRole::MaterialNormalMap
                | TerrainSourceResourceRole::MaterialSpecularMap
        ) {
            return Err(GalError::invalid_argument(
                "source material texture wrapper received an unsupported semantic role",
            ));
        }
        if !self.candidate_source_requires_resource(input.role.clone()) {
            self.clear_candidate_source_material_texture_role(gal, &input.role)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.mesh_asset_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source material texture requires non-zero shader-pack, world, and mesh generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source material texture shader-pack generation does not match the discovered candidate",
            ));
        }
        if let Some(resources) = self.source_material_texture_resources.get(&input.role) {
            if resources.compatible_with(&input) {
                return resources.semantic_resource_set().map(Some);
            }
        }
        let combined_sampler = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: format!(
                "shader-pack.source-material-{}.pack{}.world{}.mesh{}",
                input.role.semantic_name(),
                input.shader_pack_generation,
                input.world_generation,
                input.mesh_asset_generation
            ),
            texture_view: input.texture_view,
            sampler: input.sampler,
        })?;
        let replacement = TerrainSourceMaterialTextureResources {
            role: input.role.clone(),
            shader_pack_generation: input.shader_pack_generation,
            world_generation: input.world_generation,
            mesh_asset_generation: input.mesh_asset_generation,
            texture_view: input.texture_view,
            sampler: input.sampler,
            combined_sampler,
        };
        if let Some(previous) = self
            .source_material_texture_resources
            .insert(input.role.clone(), replacement)
        {
            gal.destroy(previous.combined_sampler)?;
        }
        self.source_material_texture_resources
            .get(&input.role)
            .expect("material texture wrapper was inserted")
            .semantic_resource_set()
            .map(Some)
    }

    /// Reports whether replacing this semantic wrapper would invalidate
    /// caller-owned program resource sets. The runtime intentionally does
    /// not destroy those sets itself because terrain and DH own distinct
    /// frontend caches; callers must retire their consumers before asking the
    /// runtime to replace the combined sampler.
    pub(crate) fn candidate_source_material_texture_will_replace(
        &self,
        input: &TerrainSourceMaterialTextureInput,
    ) -> bool {
        self.source_material_texture_resources
            .get(&input.role)
            .is_some_and(|resources| !resources.compatible_with(input))
    }

    pub(crate) fn clear_candidate_source_material_texture_role(
        &mut self,
        gal: &mut VulkanicGal,
        role: &TerrainSourceResourceRole,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_material_texture_resources.remove(role) {
            gal.destroy(previous.combined_sampler)?;
        }
        Ok(())
    }

    pub(crate) fn clear_candidate_source_material_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        let resources = std::mem::take(&mut self.source_material_texture_resources);
        for (_, resource) in resources {
            gal.destroy(resource.combined_sampler)?;
        }
        Ok(())
    }

    /// Creates private comparison samplers for the two source semantic shadow
    /// roles. The source route remains unavailable: this owns only a
    /// generation-coherent GAL resource subset for diagnostics and later
    /// explicit source-program assembly.
    pub(crate) fn ensure_candidate_source_shadow_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceShadowDepthInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let requires_primary =
            self.candidate_source_requires_resource(TerrainSourceResourceRole::ShadowDepthPrimary);
        let requires_secondary = self
            .candidate_source_requires_resource(TerrainSourceResourceRole::ShadowDepthSecondary);
        let requires_raw =
            self.candidate_source_requires_resource(TerrainSourceResourceRole::ShadowDepthRaw);
        if !requires_primary && !requires_secondary && !requires_raw {
            self.clear_candidate_source_shadow_depth_resources(gal)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.shader_graph_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source shadow depth requires non-zero shader-pack, world, and shader-graph generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source shadow depth shader-pack generation does not match the discovered candidate",
            ));
        }
        if self
            .source_shadow_depth_resources
            .as_ref()
            .is_some_and(|resources| resources.compatible_with(input))
        {
            return self
                .source_shadow_depth_resources
                .as_ref()
                .map(TerrainSourceShadowDepthResources::semantic_resource_set)
                .transpose();
        }

        let label_prefix = format!(
            "shader-pack.source-shadow-depth.pack{}.world{}.graph{}",
            input.shader_pack_generation, input.world_generation, input.shader_graph_generation
        );
        let compare_desc = |label: String| SamplerDesc {
            label,
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: Some(CompareOp::LessOrEqual),
        };
        let mut created = Vec::new();
        let result = (|| -> GalResult<TerrainSourceShadowDepthResources> {
            let primary_sampler =
                gal.create_sampler(compare_desc(format!("{label_prefix}.primary")))?;
            created.push(primary_sampler);
            let secondary_sampler =
                gal.create_sampler(compare_desc(format!("{label_prefix}.secondary")))?;
            created.push(secondary_sampler);
            let raw_sampler = if requires_raw {
                let sampler = gal.create_sampler(SamplerDesc {
                    label: format!("{label_prefix}.raw"),
                    min_filter: SamplerFilter::Nearest,
                    mag_filter: SamplerFilter::Nearest,
                    mip_filter: SamplerFilter::Nearest,
                    address_u: SamplerAddressMode::ClampToEdge,
                    address_v: SamplerAddressMode::ClampToEdge,
                    address_w: SamplerAddressMode::ClampToEdge,
                    comparison: None,
                })?;
                created.push(sampler);
                Some(sampler)
            } else {
                None
            };
            let primary_combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label_prefix}.primary.combined"),
                    texture_view: input.shadow_depth_view,
                    sampler: primary_sampler,
                })?;
            created.push(primary_combined_sampler);
            let secondary_combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label_prefix}.secondary.combined"),
                    texture_view: if input.shadow_depth_secondary_view == Handle::NULL {
                        input.shadow_depth_view
                    } else {
                        input.shadow_depth_secondary_view
                    },
                    sampler: secondary_sampler,
                })?;
            created.push(secondary_combined_sampler);
            let raw_combined_sampler = if let Some(raw_sampler) = raw_sampler {
                let combined = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label_prefix}.raw.combined"),
                    texture_view: input.shadow_depth_view,
                    sampler: raw_sampler,
                })?;
                created.push(combined);
                Some(combined)
            } else {
                None
            };
            Ok(TerrainSourceShadowDepthResources {
                shader_pack_generation: input.shader_pack_generation,
                world_generation: input.world_generation,
                shader_graph_generation: input.shader_graph_generation,
                primary_sampler,
                secondary_sampler,
                raw_sampler,
                primary_combined_sampler,
                secondary_combined_sampler,
                raw_combined_sampler,
            })
        })();
        let replacement = match result {
            Ok(resources) => resources,
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
                return Err(error);
            }
        };
        if let Some(previous) = self.source_shadow_depth_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        self.source_shadow_depth_resources
            .as_ref()
            .map(TerrainSourceShadowDepthResources::semantic_resource_set)
            .transpose()
    }

    pub(crate) fn clear_candidate_source_shadow_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_shadow_depth_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    /// Creates semantic combined samplers for the declared Rust-owned source
    /// shadow-color attachments. This method neither invents an attachment nor
    /// selects source execution: callers provide the matching graph views.
    pub(crate) fn ensure_candidate_source_shadow_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceShadowColorInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let required = [
            TerrainSourceResourceRole::ShadowColor,
            TerrainSourceResourceRole::ShadowColorSecondary,
        ]
        .into_iter()
        .filter(|role| self.candidate_source_requires_resource(role.clone()))
        .collect::<Vec<_>>();
        if required.is_empty() {
            self.clear_candidate_source_shadow_color_resources(gal)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.shader_graph_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source shadow color requires non-zero shader-pack, world, and shader-graph generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source shadow color shader-pack generation does not match the discovered candidate",
            ));
        }
        if self
            .source_shadow_color_resources
            .as_ref()
            .is_some_and(|resources| resources.compatible_with(input))
        {
            return self
                .source_shadow_color_resources
                .as_ref()
                .map(TerrainSourceShadowColorResources::semantic_resource_set)
                .transpose();
        }
        let mut created = Vec::new();
        let replacement = (|| -> GalResult<TerrainSourceShadowColorResources> {
            let mut combined_samplers = BTreeMap::new();
            for role in required {
                let texture_view = match role {
                    TerrainSourceResourceRole::ShadowColor => input.shadow_color_view,
                    TerrainSourceResourceRole::ShadowColorSecondary => {
                        input.shadow_color_secondary_view
                    }
                    _ => unreachable!("shadow-color requirements are bounded above"),
                };
                let combined_sampler =
                    gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                        label: format!(
                            "shader-pack.source-{}.pack{}.world{}.graph{}",
                            role.semantic_name(),
                            input.shader_pack_generation,
                            input.world_generation,
                            input.shader_graph_generation
                        ),
                        texture_view,
                        sampler: input.sampler,
                    })?;
                created.push(combined_sampler);
                combined_samplers.insert(role, combined_sampler);
            }
            Ok(TerrainSourceShadowColorResources {
                shader_pack_generation: input.shader_pack_generation,
                world_generation: input.world_generation,
                shader_graph_generation: input.shader_graph_generation,
                shadow_color_view: input.shadow_color_view,
                shadow_color_secondary_view: input.shadow_color_secondary_view,
                sampler: input.sampler,
                combined_samplers,
            })
        })();
        let replacement = match replacement {
            Ok(resources) => resources,
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
                return Err(error);
            }
        };
        if let Some(previous) = self.source_shadow_color_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        self.source_shadow_color_resources
            .as_ref()
            .map(TerrainSourceShadowColorResources::semantic_resource_set)
            .transpose()
    }

    pub(crate) fn clear_candidate_source_shadow_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_shadow_color_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    /// Binds only confirmed Rust-owned main-depth semantics declared by the
    /// discovered source. A missing temporal snapshot remains absent from the
    /// returned set, allowing the source completeness check to reject the
    /// frame instead of aliasing it to live depth.
    pub(crate) fn ensure_candidate_source_main_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceMainDepthInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let mut required = [
            TerrainSourceResourceRole::MainDepth,
            TerrainSourceResourceRole::MainDepthBeforeTranslucency,
            TerrainSourceResourceRole::MainDepthPrevious,
        ]
        .into_iter()
        .filter(|role| self.candidate_source_requires_resource(role.clone()))
        .collect::<Vec<_>>();
        // A complete selected-source plan may discover depth consumption in a
        // retained fullscreen stage after the terrain candidate was observed.
        // The caller supplies the exact Rust-owned G-buffer view here, so keep
        // the current main-depth role available for that same frame rather
        // than allowing the later fullscreen admission to see a stale subset.
        if !required.contains(&TerrainSourceResourceRole::MainDepth) {
            required.push(TerrainSourceResourceRole::MainDepth);
        }
        // The selected-frame planner may discover a temporal sampler while
        // the immutable candidate snapshot is still carrying the prior
        // frame's role set.  An explicitly supplied Rust-owned view is safe
        // to wrap now; final completeness still rejects any undeclared or
        // missing role before execution.
        for (role, view) in [
            (
                TerrainSourceResourceRole::MainDepthBeforeTranslucency,
                input.before_translucency_view,
            ),
            (
                TerrainSourceResourceRole::MainDepthPrevious,
                input.previous_view,
            ),
        ] {
            if view.is_some() && !required.contains(&role) {
                required.push(role);
            }
        }
        if required.is_empty() {
            self.clear_candidate_source_main_depth_resources(gal)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.shader_graph_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source main depth requires non-zero shader-pack, world, and shader-graph generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source main depth shader-pack generation does not match the discovered candidate",
            ));
        }
        if self
            .source_main_depth_resources
            .as_ref()
            .is_some_and(|resources| resources.compatible_with(input))
        {
            return self
                .source_main_depth_resources
                .as_ref()
                .map(TerrainSourceMainDepthResources::semantic_resource_set)
                .transpose();
        }

        // A private source frame may make a new post-terrain snapshot
        // available after its base resource assembly already retained
        // `main_depth`. Extend that same generation in place so the old
        // combined sampler stays valid for the exact-frame snapshot. A
        // changed view for an already-owned role is not safe to replace here:
        // callers must retire the prior frame/resource generation first.
        if self
            .source_main_depth_resources
            .as_ref()
            .is_some_and(|resources| resources.base_compatible_with(input))
        {
            let resources = self
                .source_main_depth_resources
                .as_mut()
                .expect("main-depth resource checked before in-place extension");
            for (role, existing_view, requested_view) in [
                (
                    TerrainSourceResourceRole::MainDepthBeforeTranslucency,
                    resources.before_translucency_view,
                    input.before_translucency_view,
                ),
                (
                    TerrainSourceResourceRole::MainDepthPrevious,
                    resources.previous_view,
                    input.previous_view,
                ),
            ] {
                if resources.combined_samplers.contains_key(&role)
                    && requested_view.is_some()
                    && existing_view != requested_view
                {
                    return Err(GalError::invalid_argument(format!(
                        "source main-depth role '{}' cannot replace a view in place for the same generation",
                        role.semantic_name(),
                    )));
                }
            }
            for (role, view) in [
                (
                    TerrainSourceResourceRole::MainDepthBeforeTranslucency,
                    input.before_translucency_view,
                ),
                (
                    TerrainSourceResourceRole::MainDepthPrevious,
                    input.previous_view,
                ),
            ] {
                if !required.contains(&role) || resources.combined_samplers.contains_key(&role) {
                    continue;
                }
                let Some(view) = view else {
                    continue;
                };
                let combined_sampler =
                    gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                        label: format!(
                            "shader-pack.source-{}.pack{}.world{}.graph{}",
                            role.semantic_name(),
                            input.shader_pack_generation,
                            input.world_generation,
                            input.shader_graph_generation,
                        ),
                        texture_view: view,
                        sampler: input.sampler,
                    })?;
                resources.combined_samplers.insert(role, combined_sampler);
            }
            if input.before_translucency_view.is_some() {
                resources.before_translucency_view = input.before_translucency_view;
            }
            if input.previous_view.is_some() {
                resources.previous_view = input.previous_view;
            }
            return resources.semantic_resource_set().map(Some);
        }

        let candidates = [
            (
                TerrainSourceResourceRole::MainDepth,
                Some(input.main_depth_view),
            ),
            (
                TerrainSourceResourceRole::MainDepthBeforeTranslucency,
                input.before_translucency_view,
            ),
            (
                TerrainSourceResourceRole::MainDepthPrevious,
                input.previous_view,
            ),
        ];
        let mut created = Vec::new();
        let result = (|| -> GalResult<TerrainSourceMainDepthResources> {
            let mut combined_samplers = BTreeMap::new();
            for (role, view) in candidates {
                if !required.contains(&role) {
                    continue;
                }
                let Some(view) = view else {
                    continue;
                };
                let combined_sampler =
                    gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                        label: format!(
                            "shader-pack.source-{}.pack{}.world{}.graph{}",
                            role.semantic_name(),
                            input.shader_pack_generation,
                            input.world_generation,
                            input.shader_graph_generation,
                        ),
                        texture_view: view,
                        sampler: input.sampler,
                    })?;
                created.push(combined_sampler);
                combined_samplers.insert(role, combined_sampler);
            }
            Ok(TerrainSourceMainDepthResources {
                shader_pack_generation: input.shader_pack_generation,
                world_generation: input.world_generation,
                shader_graph_generation: input.shader_graph_generation,
                main_depth_view: input.main_depth_view,
                before_translucency_view: input.before_translucency_view,
                previous_view: input.previous_view,
                sampler: input.sampler,
                combined_samplers,
            })
        })();
        let replacement = match result {
            Ok(resources) => resources,
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
                return Err(error);
            }
        };
        if let Some(previous) = self.source_main_depth_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        self.source_main_depth_resources
            .as_ref()
            .map(TerrainSourceMainDepthResources::semantic_resource_set)
            .transpose()
    }

    pub(crate) fn clear_candidate_source_main_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_main_depth_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    #[cfg(test)]
    pub(crate) fn candidate_source_material_atlas_identity(&self) -> Option<(Handle, u64)> {
        self.source_material_texture_resources
            .get(&TerrainSourceResourceRole::MaterialAtlas)
            .map(|resources| (resources.combined_sampler, resources.mesh_asset_generation))
    }

    #[cfg(test)]
    pub(crate) fn candidate_source_material_texture_identity(
        &self,
        role: TerrainSourceResourceRole,
    ) -> Option<(Handle, u64)> {
        self.source_material_texture_resources
            .get(&role)
            .map(|resources| (resources.combined_sampler, resources.mesh_asset_generation))
    }

    #[cfg(test)]
    pub(crate) fn candidate_source_asset_resource_count(&self) -> Option<usize> {
        self.source_asset_resources
            .as_ref()
            .map(TerrainSourceAssetResources::len)
    }

    /// Returns an owned binding only when the currently discovered source
    /// requires it and the exact source generation has a complete matching
    /// colored-light volume. This does not select a source program; pipeline
    /// composition remains the frontend's explicit next step.
    pub(crate) fn candidate_shader_binding(
        &self,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainShaderProgramBinding>> {
        let (source_generation, requires_colored_voxel_light) = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                generation,
                requires_colored_voxel_light,
                ..
            } => (*generation, *requires_colored_voxel_light),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        if !requires_colored_voxel_light {
            return Ok(None);
        }
        let colored_light = self.terrain_colored_light.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected terrain source requires a complete owned colored voxel-light volume",
            )
        })?;
        if colored_light.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "selected terrain source and colored voxel-light volume generations differ",
            ));
        }
        let TerrainVoxelLightSamplingBinding {
            resource_layout,
            resource_set,
            resource_generation,
            ..
        } = colored_light.sampling_binding(frame_counter)?;
        Ok(Some(TerrainShaderProgramBinding {
            resource: TerrainProgramResource::ColoredVoxelLightVolume,
            resource_layout,
            resource_set: TerrainShaderResourceSet {
                set_index: 1,
                set: resource_set,
            },
            resource_generation,
        }))
    }
}

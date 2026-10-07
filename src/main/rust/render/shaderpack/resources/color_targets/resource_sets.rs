//! Per-program source color resource sets and their cache.

use super::*;

/// Rust-owned sampler bindings for the shader-pack color subset of one
/// source stage. These are deliberately separate from target allocation:
/// targets have a source-generation lifetime, while bindings are
/// program-local and may choose the previous feedback image or mip sampling.
/// No Java/Iris sampler, texture unit, framebuffer, or native handle crosses
/// this boundary.
#[derive(Clone, Debug)]
pub(crate) struct ShaderPackSourceColorResources {
    pub(super) resources: TerrainSourceOwnedResourceSet,
    pub(super) combined_samplers: Vec<Handle>,
    pub(super) samplers: Vec<Handle>,
    pub(super) sampled_views: BTreeMap<TerrainSourceResourceRole, Handle>,
}

impl ShaderPackSourceColorResources {
    pub(crate) fn resources(&self) -> &TerrainSourceOwnedResourceSet {
        &self.resources
    }

    /// Diagnostic correlation only: the selected GAL view is still opaque to
    /// callers and backend-native identity never leaves the runtime.
    pub(crate) fn sampled_view_for(&self, role: TerrainSourceResourceRole) -> Option<Handle> {
        self.sampled_views.get(&role).copied()
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in self.combined_samplers.into_iter().rev() {
            let _ = gal.retire(handle);
        }
        for handle in self.samplers.into_iter().rev() {
            let _ = gal.retire(handle);
        }
    }
}

/// Stable semantic identity for one program-local table of Rust-owned named
/// color samplers. It deliberately contains target generations and lowered
/// binding facts, never a texture handle, texture unit, descriptor, or
/// backend-native identity.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct ShaderPackSourceColorResourceKey {
    pub(super) world_generation: u64,
    pub(super) shader_pack_generation: u64,
    pub(super) extent: (u32, u32, u32),
    pub(super) feedback_target_names: Vec<String>,
    pub(super) mipmapped_target_names: Vec<String>,
    pub(super) bindings: Vec<(TerrainSourceResourceRole, u32, bool, bool)>,
}

impl ShaderPackSourceColorResourceKey {
    pub(super) fn new(
        shader_pack_generation: u64,
        opaque_bindings: &TerrainSourceOpaqueResourceBindingPlan,
        sampling: &ShaderPackColorSamplingPlan,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<Self> {
        if shader_pack_generation != targets.identity.shader_pack_generation {
            return Err(GalError::invalid_argument(format!(
                "source program generation {} does not match color target generation {}",
                shader_pack_generation, targets.identity.shader_pack_generation
            )));
        }
        sampling.validate_for(opaque_bindings)?;
        let mut bindings = opaque_bindings
            .bindings()
            .iter()
            .filter(|binding| {
                binding.kind() == TerrainSourceOpaqueResourceKind::CombinedTextureSampler
                    && matches!(
                        binding.role(),
                        TerrainSourceResourceRole::ShaderPackColor(_)
                    )
            })
            .map(|binding| {
                let policy = sampling.binding_for(binding.role(), binding.binding());
                (
                    binding.role(),
                    binding.binding(),
                    policy.feedback,
                    policy.mipmapped,
                )
            })
            .collect::<Vec<_>>();
        bindings.sort();
        Ok(Self {
            world_generation: targets.identity.world_generation,
            shader_pack_generation,
            extent: (
                targets.identity.extent.width,
                targets.identity.extent.height,
                targets.identity.extent.depth,
            ),
            feedback_target_names: targets.identity.feedback_target_names.clone(),
            mipmapped_target_names: targets.identity.mipmapped_target_names.clone(),
            bindings,
        })
    }

    pub(super) fn matches_targets(&self, targets: &ShaderPackColorTargets) -> bool {
        self.world_generation == targets.identity.world_generation
            && self.shader_pack_generation == targets.identity.shader_pack_generation
            && self.extent
                == (
                    targets.identity.extent.width,
                    targets.identity.extent.height,
                    targets.identity.extent.depth,
                )
            && self.feedback_target_names == targets.identity.feedback_target_names
            && self.mipmapped_target_names == targets.identity.mipmapped_target_names
    }
}

/// Two-phase owner for program-local named-color samplers. Source targets and
/// the combined samplers that reference them must advance together: a source
/// submission either confirms both or discards both. This cache has no route
/// authority and does not expose a backend object outside the GAL.
#[derive(Debug, Default)]
pub(crate) struct ShaderPackSourceColorResourceCache {
    pub(super) active: BTreeMap<ShaderPackSourceColorResourceKey, ShaderPackSourceColorResources>,
    pub(super) pending: BTreeMap<ShaderPackSourceColorResourceKey, ShaderPackSourceColorResources>,
}

impl ShaderPackSourceColorResourceCache {
    /// Stages an owned source-color subset and returns a cloned semantic table
    /// suitable for merging into a one-frame source resource snapshot. The
    /// resource handles remain owned by this cache until matching confirmation
    /// or discard; callers never own target lifetime by cloning the table.
    pub(crate) fn stage(
        &mut self,
        gal: &mut VulkanicGal,
        shader_pack_generation: u64,
        opaque_bindings: &TerrainSourceOpaqueResourceBindingPlan,
        sampling: &ShaderPackColorSamplingPlan,
        targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        let key = ShaderPackSourceColorResourceKey::new(
            shader_pack_generation,
            opaque_bindings,
            sampling,
            targets,
        )?;
        if let Some(resources) = self.pending.get(&key).or_else(|| self.active.get(&key)) {
            return Ok(resources.resources().clone());
        }
        if self
            .pending
            .keys()
            .any(|pending| !pending.matches_targets(targets))
        {
            return Err(GalError::backend(
                "shader-pack source-color replacement is awaiting confirmation for a different target generation",
            ));
        }
        let resources = prepare_source_color_resources(
            gal,
            shader_pack_generation,
            opaque_bindings,
            sampling,
            targets,
        )?;
        let snapshot = resources.resources().clone();
        self.pending.insert(key, resources);
        Ok(snapshot)
    }

    /// Promotes all program-local bindings prepared for the target generation
    /// used by a successful combined source submission. Older target
    /// generations retire through the GAL before their images are replaced.
    pub(crate) fn confirm_submission(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        if self.pending.is_empty() {
            return Ok(());
        }
        let target_key = self
            .pending
            .keys()
            .next()
            .expect("non-empty pending source-color cache")
            .clone();
        if self
            .pending
            .keys()
            .any(|pending| !pending.matches_targets_key(&target_key))
        {
            return Err(GalError::backend(
                "shader-pack source-color cache cannot confirm mixed target generations",
            ));
        }
        let stale = self
            .active
            .keys()
            .filter(|active| !active.matches_targets_key(&target_key))
            .cloned()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(resources) = self.active.remove(&key) {
                resources.destroy(gal);
            }
        }
        for (key, resources) in std::mem::take(&mut self.pending) {
            if let Some(previous) = self.active.insert(key, resources) {
                previous.destroy(gal);
            }
        }
        Ok(())
    }

    /// Discards only bindings staged for a failed combined source submission.
    /// Confirmed bindings remain valid for their still-active target generation.
    pub(crate) fn discard_submission(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.pending) {
            resources.destroy(gal);
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.discard_submission(gal);
        for (_, resources) in std::mem::take(&mut self.active) {
            resources.destroy(gal);
        }
    }

    #[cfg(test)]
    pub(super) fn active_len(&self) -> usize {
        self.active.len()
    }

    #[cfg(test)]
    pub(super) fn pending_len(&self) -> usize {
        self.pending.len()
    }
}

impl ShaderPackSourceColorResourceKey {
    pub(super) fn matches_targets_key(&self, other: &Self) -> bool {
        self.world_generation == other.world_generation
            && self.shader_pack_generation == other.shader_pack_generation
            && self.extent == other.extent
            && self.feedback_target_names == other.feedback_target_names
            && self.mipmapped_target_names == other.mipmapped_target_names
    }
}

/// Resolves only source-declared shader-pack color samplers into owned GAL
/// resources. Other semantic roles (material atlas, depth, custom textures,
/// storage images, and so on) are deliberately left for their own complete
/// owned resource paths; the caller merges those subsets and rejects missing
/// roles before pass creation.
///
/// The sampling policy follows the portable pack-color protocol: render
/// targets are linearly sampled with clamp-to-edge addressing. A source-local
/// `*MipmapEnabled` directive upgrades only that role to linear mip sampling,
/// after target staging has allocated the requested mip chain. This derives
/// from source semantics, not from a borrowed renderer sampler object.
pub(crate) fn prepare_source_color_resources(
    gal: &mut VulkanicGal,
    shader_pack_generation: u64,
    opaque_bindings: &TerrainSourceOpaqueResourceBindingPlan,
    sampling: &ShaderPackColorSamplingPlan,
    targets: &ShaderPackColorTargets,
) -> GalResult<ShaderPackSourceColorResources> {
    if shader_pack_generation != targets.identity.shader_pack_generation {
        return Err(GalError::invalid_argument(format!(
            "source program generation {} does not match color target generation {}",
            shader_pack_generation, targets.identity.shader_pack_generation
        )));
    }
    sampling.validate_for(opaque_bindings)?;

    let mut requirements = BTreeMap::<TerrainSourceResourceRole, SourceColorBinding>::new();
    for binding in opaque_bindings.bindings() {
        if !matches!(
            binding.kind(),
            TerrainSourceOpaqueResourceKind::CombinedTextureSampler
        ) {
            // Storage images belong to an independently owned semantic
            // subset. This helper has no authority to create or bind them.
            continue;
        }
        let role = binding.role();
        if !matches!(role, TerrainSourceResourceRole::ShaderPackColor(_)) {
            // Depth, material, shadow, volume, and copied pack assets are
            // prepared by their own owners, then merged by the fullscreen
            // source pass contract. Rejecting them here made mixed semantic
            // passes impossible to admit without a borrowed renderer state.
            continue;
        }
        let candidate = sampling.binding_for(role.clone(), binding.binding());
        if let Some(existing) = requirements.insert(role.clone(), candidate) {
            if existing != candidate {
                return Err(GalError::unsupported_feature(format!(
                    "fullscreen source aliases semantic color '{}' with incompatible feedback or mipmap behavior",
                    role.semantic_name()
                )));
            }
        }
    }

    let mut created = Vec::new();
    let result = (|| -> GalResult<ShaderPackSourceColorResources> {
        let mut availability = Vec::with_capacity(requirements.len());
        let mut resources = Vec::with_capacity(requirements.len());
        let mut combined_samplers = Vec::with_capacity(requirements.len());
        let mut samplers = Vec::with_capacity(requirements.len());
        let mut sampled_views = BTreeMap::new();
        for (role, binding) in requirements {
            let name = role.shader_pack_color_name().expect("validated color role");
            let target = targets.target(name).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "fullscreen source semantic color target '{name}' is unavailable"
                ))
            })?;
            // Iris applies a mipmapped minification filter only for a program
            // that declares `colortexNMipmapEnabled`; every other reader uses
            // a non-mip filter and therefore sees mip 0 alone. A Vulkan
            // nearest-mip sampler over the full chain is not equivalent: its
            // derivative-selected LOD can read stale descendants in divergent
            // control flow (Complementary's FXAA edge search). Non-mipmapped
            // bindings therefore sample the explicit base-mip view.
            let view = match (binding.feedback, binding.mipmapped) {
                (true, true) => target.previous_view,
                (true, false) => target.previous_attachment_view.or(target.previous_view),
                (false, true) => Some(target.current_view),
                (false, false) => Some(target.current_attachment_view),
            }
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "fullscreen source target '{name}' samples its own output but has no previous feedback view"
                ))
            })?;
            if binding.mipmapped && target.mip_levels < 2 {
                return Err(GalError::invalid_argument(format!(
                    "fullscreen source target '{name}' requests mip sampling but has no staged mip chain"
                )));
            }
            let sampler = gal.create_sampler(
                ShaderPackColorSamplingPolicy {
                    mipmapped: binding.mipmapped,
                }
                .descriptor(&format!(
                    "shader-pack-color.world{}-pack{}.{}.{}-sampler",
                    targets.identity.world_generation,
                    targets.identity.shader_pack_generation,
                    name,
                    if binding.feedback {
                        "previous"
                    } else {
                        "current"
                    }
                )),
            )?;
            created.push(sampler);
            samplers.push(sampler);
            let combined = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!(
                    "shader-pack-color.world{}-pack{}.{}.{}-combined",
                    targets.identity.world_generation,
                    targets.identity.shader_pack_generation,
                    name,
                    if binding.feedback {
                        "previous"
                    } else {
                        "current"
                    }
                ),
                texture_view: view,
                sampler,
            })?;
            created.push(combined);
            combined_samplers.push(combined);
            availability.push(TerrainSourceResourceAvailability {
                role: role.clone(),
                shape: role.expected_sampled_resource_shape(),
                resource_generation: color_resource_generation(&targets.identity, name, binding),
            });
            resources.push(TerrainSourceOwnedResource {
                role: role.clone(),
                combined_sampler: combined,
            });
            sampled_views.insert(role, view);
        }
        Ok(ShaderPackSourceColorResources {
            resources: TerrainSourceOwnedResourceSet::new(
                TerrainSourceResourceAvailabilitySet::new(
                    targets.identity.shader_pack_generation,
                    targets.identity.world_generation,
                    availability,
                )?,
                resources,
            )?,
            combined_samplers,
            samplers,
            sampled_views,
        })
    })();
    if result.is_err() {
        for handle in created.into_iter().rev() {
            let _ = gal.retire(handle);
        }
    }
    result
}

/// Fullscreen stages derive feedback and mip policy from their own lowered
/// source. Keep this focused wrapper so existing fullscreen preparation stays
/// compact while terrain and DH use the generic allocator above.
pub(crate) fn prepare_fullscreen_source_color_resources(
    gal: &mut VulkanicGal,
    program: &LoweredFullscreenSourceProgram,
    targets: &ShaderPackColorTargets,
) -> GalResult<ShaderPackSourceColorResources> {
    prepare_source_color_resources(
        gal,
        program.shader_pack_generation,
        &program.opaque_resource_bindings,
        &ShaderPackColorSamplingPlan::from_fullscreen(program),
        targets,
    )
}

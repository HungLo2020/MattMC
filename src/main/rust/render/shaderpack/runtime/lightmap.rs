//! Vanilla lightmap observation, residency and bindings for pack programs.

use super::*;

impl ShaderPackRuntimeExecutor {
    /// Accepts only copied semantic lightmap inputs. It cannot observe or
    /// retain a Java texture, sampler, image view, or renderer state.
    pub(crate) fn observe_vanilla_lightmap(
        &mut self,
        world_generation: u64,
        frame: Option<VanillaLightmapFrame>,
    ) -> GalResult<Option<VanillaLightmapCacheUpdate>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "vanilla lightmap observation requires a non-zero world generation",
            ));
        }
        match frame {
            Some(frame) => self
                .vanilla_lightmap
                .update(world_generation, frame)
                .map(Some),
            None => {
                if self.vanilla_lightmap.world_generation() != 0
                    && self.vanilla_lightmap.world_generation() != world_generation
                {
                    self.vanilla_lightmap.clear();
                }
                Ok(None)
            }
        }
    }

    pub(crate) fn vanilla_lightmap_cache(&self) -> &VanillaLightmapCache {
        &self.vanilla_lightmap
    }

    /// Stages a fresh Rust-owned lightmap image into the caller's existing
    /// combined frame submission. The resource is intentionally unavailable
    /// to selected-source assembly until `confirm_vanilla_lightmap_submission`
    /// observes that submission's success.
    #[track_caller]
    pub(crate) fn stage_vanilla_lightmap_residency(
        &mut self,
        gal: &mut VulkanicGal,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if self.vanilla_lightmap.world_generation() == 0 {
            return Ok(false);
        }
        if self
            .vanilla_lightmap_residency
            .as_ref()
            .is_some_and(|resources| resources.is_compatible_with(&self.vanilla_lightmap))
        {
            return Ok(false);
        }
        if let Some(pending) = self.pending_vanilla_lightmap_residency.as_ref() {
            // Several semantic consumers can request the same lightmap while
            // one combined frame is being assembled. The pending binding is
            // valid for that exact submission, so a compatible request is a
            // no-op; only a changed generation would need another transaction.
            if pending.is_compatible_with(&self.vanilla_lightmap) {
                if self.pending_vanilla_lightmap_upload_recorded {
                    return Ok(false);
                }
                pending.append_upload(&self.vanilla_lightmap, ops)?;
                self.pending_vanilla_lightmap_upload_recorded = true;
                return Ok(true);
            }
            let pending_binding = pending.binding();
            return Err(GalError::invalid_argument(format!(
                "vanilla lightmap replacement conflicts with a different pending combined submission: pending world/lightmap={}/{}, observed world/lightmap={}/{}, stage caller={}",
                pending_binding.world_generation,
                pending_binding.lightmap_generation,
                self.vanilla_lightmap.world_generation(),
                self.vanilla_lightmap.lightmap_generation(),
                std::panic::Location::caller(),
            )));
        }
        let replacement = VanillaLightmapResidency::create(gal, &self.vanilla_lightmap)?;
        if let Err(error) = replacement.append_upload(&self.vanilla_lightmap, ops) {
            let _ = replacement.destroy(gal);
            return Err(error);
        }
        self.pending_vanilla_lightmap_residency = Some(replacement);
        self.pending_vanilla_lightmap_upload_recorded = true;
        Ok(true)
    }

    /// The caller dropped the operations holding the pending lightmap
    /// upload (e.g. a provisional frame assembly). The residency stays
    /// pending for this frame's consumers, which must record the upload again.
    pub(crate) fn forget_recorded_vanilla_lightmap_upload(&mut self) {
        self.pending_vanilla_lightmap_upload_recorded = false;
    }

    pub(crate) fn has_pending_vanilla_lightmap_submission(&self) -> bool {
        self.pending_vanilla_lightmap_residency.is_some()
    }

    pub(crate) fn confirm_vanilla_lightmap_submission(
        &mut self,
        _gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        let Some(replacement) = self.pending_vanilla_lightmap_residency.take() else {
            return Ok(());
        };
        if !std::mem::take(&mut self.pending_vanilla_lightmap_upload_recorded) {
            // Never uploaded by a submitted command list: promoting it would
            // let a later consumer sample an undefined image. Retire it with
            // the replaced generations once frontend consumers are released.
            self.retired_vanilla_lightmap_residencies.push(replacement);
            return Ok(());
        }
        if let Some(previous) = self.vanilla_lightmap_residency.replace(replacement) {
            self.retired_vanilla_lightmap_residencies.push(previous);
        }
        Ok(())
    }

    /// Destroys lightmaps whose frontend resource-set consumers have already
    /// been removed. This is called after pass caches retain the new binding,
    /// keeping the sampled view alive across the replacement boundary.
    pub(crate) fn retire_replaced_vanilla_lightmaps(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        for resources in self.retired_vanilla_lightmap_residencies.drain(..) {
            resources.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn discard_vanilla_lightmap_submission(&mut self, gal: &mut VulkanicGal) {
        if let Some(pending) = self.pending_vanilla_lightmap_residency.take() {
            let _ = pending.destroy(gal);
        }
    }

    pub(crate) fn candidate_vanilla_lightmap_resource_set(
        &self,
        allow_pending: bool,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if !self.candidate_source_requires_resource(TerrainSourceResourceRole::Lightmap) {
            return Ok(None);
        }
        let resources = if allow_pending {
            self.pending_vanilla_lightmap_residency
                .as_ref()
                .or(self.vanilla_lightmap_residency.as_ref())
        } else {
            self.vanilla_lightmap_residency.as_ref()
        };
        resources
            .map(|resources| {
                resources
                    .semantic_resource_set(self.expected_shader_pack_generation_for_resources())
            })
            .transpose()
    }

    /// Returns only a Rust-owned generation-coherent lightmap binding for
    /// built-in material passes. Unlike the source-candidate accessor above,
    /// this has no shader-pack admission policy: the caller still has to own
    /// a complete pass contract before it can bind or draw with this resource.
    pub(crate) fn vanilla_lightmap_binding(
        &self,
        allow_pending: bool,
    ) -> Option<VanillaLightmapBinding> {
        let resources = if allow_pending {
            self.pending_vanilla_lightmap_residency
                .as_ref()
                .or(self.vanilla_lightmap_residency.as_ref())
        } else {
            self.vanilla_lightmap_residency.as_ref()
        };
        resources.map(VanillaLightmapResidency::binding)
    }

    /// Resolves a caller-declared descriptor layout against the active (or
    /// pending combined-submission) Rust-owned lightmap generation. The
    /// returned set is retained by that residency, not by the caller.
    pub(crate) fn vanilla_lightmap_resource_set(
        &mut self,
        gal: &mut VulkanicGal,
        layout: Handle,
        allow_pending: bool,
    ) -> GalResult<Option<VanillaLightmapResourceSet>> {
        let resources = if allow_pending {
            self.pending_vanilla_lightmap_residency
                .as_mut()
                .or(self.vanilla_lightmap_residency.as_mut())
        } else {
            self.vanilla_lightmap_residency.as_mut()
        };
        resources
            .map(|resources| {
                Ok(VanillaLightmapResourceSet {
                    world_generation: resources.binding().world_generation,
                    lightmap_generation: resources.binding().lightmap_generation,
                    set: resources.resource_set_for_layout(gal, layout)?,
                })
            })
            .transpose()
    }
}

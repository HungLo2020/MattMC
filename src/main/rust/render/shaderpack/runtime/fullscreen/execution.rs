//! Ordered fullscreen execution plans.

use super::*;

/// Complete Rust-owned execution preparation for one source-derived
/// fullscreen stage. Staging is atomic across semantic preparation, GAL
/// compilation, and resource-set binding; this reusable owner is shared by
/// future vanilla and Distant Horizons scheduling without selecting either
/// route here.
#[derive(Debug)]
pub(crate) struct FullscreenSourceExecutionPlan {
    pub(super) prepared: PreparedFullscreenSourcePass,
    pub(super) compiled: CompiledFullscreenSourcePass,
    pub(super) bound: BoundFullscreenSourcePass,
    /// Set while this plan uses a cached stage's objects; cleared on destroy.
    pub(super) stage_lease: Option<std::sync::Arc<std::sync::atomic::AtomicBool>>,
}

impl FullscreenSourceExecutionPlan {
    /// Semantic outputs written by this stage in the exact source declaration
    /// order. This is intentionally diagnostic-facing only: callers can
    /// correlate a bounded readback to a source-stage output without learning
    /// attachment numbers or backend resource identity.
    pub(crate) fn outputs(&self) -> &[FullscreenSourceColorAttachment] {
        &self.prepared.outputs
    }

    #[cfg(test)]
    pub(crate) fn stage(
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        manifest: &ShaderPackColorTargetManifest,
        targets: &ShaderPackColorTargets,
        external_inputs: impl IntoIterator<Item = TerrainSourceOwnedResourceSet>,
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<Self> {
        Self::stage_cached(gal, program, manifest, targets, external_inputs, extent, None)
    }

    /// As [`Self::stage`], reusing compiled pipeline objects from `cache`.
    pub(crate) fn stage_cached(
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        manifest: &ShaderPackColorTargetManifest,
        targets: &ShaderPackColorTargets,
        external_inputs: impl IntoIterator<Item = TerrainSourceOwnedResourceSet>,
        extent: crate::render::vulkanic::resources::Extent3d,
        cache: Option<(&FullscreenPipelineCache, (u64, u64))>,
    ) -> GalResult<Self> {
        if targets.identity.extent != extent {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source target extent {:?} does not match requested pass extent {:?}",
                targets.identity.extent, extent
            )));
        }
        let Some((cache, epochs)) = cache else {
            return Self::stage_uncached(gal, program, manifest, targets, external_inputs, extent, None);
        };
        cache.retire_stages(gal, []);
        let external_inputs = external_inputs.into_iter().collect::<Vec<_>>();
        if cache.epochs.get() == Some(epochs) {
            if let Some(plan) =
                Self::stage_from_cached_stage(gal, program, manifest, targets, &external_inputs, extent, cache, epochs)?
            {
                return Ok(plan);
            }
        }
        let plan = Self::stage_uncached(
            gal, program, manifest, targets, external_inputs, extent, Some((cache, epochs)),
        )?;
        Ok(Self::adopt_into_stage_cache(gal, plan, program, targets, cache, epochs))
    }

    /// Stages against a matching unleased cache entry, creating only the
    /// pack-resources set. `None` when there is no usable entry.
    #[allow(clippy::too_many_arguments)]
    fn stage_from_cached_stage(
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        manifest: &ShaderPackColorTargetManifest,
        targets: &ShaderPackColorTargets,
        external_inputs: &[TerrainSourceOwnedResourceSet],
        extent: crate::render::vulkanic::resources::Extent3d,
        cache: &FullscreenPipelineCache,
        epochs: (u64, u64),
    ) -> GalResult<Option<Self>> {
        let entry = {
            let stages = cache.stages.borrow();
            let Some(entry) = stages.get(&program.source_stage_path).and_then(|candidates| {
                candidates.iter().find(|stage| {
                    stage.matches(program, targets, epochs)
                        && !stage.lease.load(std::sync::atomic::Ordering::Acquire)
                })
            }) else {
                return Ok(None);
            };
            (
                entry.color_resources.clone(),
                (entry.target, entry.pass),
                std::sync::Arc::clone(&entry.objects),
                (entry.texture_transform_buffer, entry.scalar_uniform_buffer, entry.source_data_set),
                std::sync::Arc::clone(&entry.lease),
            )
        };
        let (color_resources, stage_target, objects, stage_resources, lease) = entry;
        let prepared = PreparedFullscreenSourcePass::prepare_with(
            gal, program, manifest, targets, external_inputs.iter().cloned(), Some(color_resources),
        )?;
        let compiled = prepared.compile_reusing(gal, program, extent, Some((cache, epochs)), Some(stage_target))?;
        if compiled.owns_target
            || !compiled.shared.as_ref().is_some_and(|shared| std::sync::Arc::ptr_eq(shared, &objects))
        {
            // The pipeline was recompiled: this entry no longer describes the
            // stage. Drop it and stage from scratch.
            compiled.destroy(gal);
            let stale = cache.stages.borrow_mut().get_mut(&program.source_stage_path).map(|candidates| {
                let (stale, kept) = std::mem::take(candidates)
                    .into_iter()
                    .partition::<Vec<_>, _>(|stage| std::sync::Arc::ptr_eq(&stage.lease, &lease));
                *candidates = kept;
                stale
            });
            cache.retire_stages(gal, stale.into_iter().flatten());
            return Ok(None);
        }
        let bound = match prepared.bind_resources_reusing(gal, program, &compiled, Some(stage_resources)) {
            Ok(bound) => bound,
            Err(error) => {
                compiled.destroy(gal);
                return Err(error);
            }
        };
        lease.store(true, std::sync::atomic::Ordering::Release);
        Ok(Some(Self { prepared, compiled, bound, stage_lease: Some(lease) }))
    }

    /// Moves a freshly staged plan's frame-invariant objects into the stage
    /// cache, leased to that plan. Plans that cannot be cached keep owning
    /// everything.
    fn adopt_into_stage_cache(
        gal: &mut VulkanicGal,
        mut plan: Self,
        program: &LoweredFullscreenSourceProgram,
        targets: &ShaderPackColorTargets,
        cache: &FullscreenPipelineCache,
        epochs: (u64, u64),
    ) -> Self {
        let Some(objects) = plan.compiled.shared.clone() else {
            return plan;
        };
        if cache.epochs.get() != Some(epochs) || !plan.prepared.owns_color_resources {
            return plan;
        }
        let mut stages = cache.stages.borrow_mut();
        let candidates = stages.entry(program.source_stage_path.clone()).or_default();
        // Never displace an entry a live plan is using.
        if candidates.len() >= FULLSCREEN_STAGE_CACHE_VARIANTS
            && candidates.iter().all(|stage| stage.lease.load(std::sync::atomic::Ordering::Acquire))
        {
            return plan;
        }
        let evicted = if candidates.len() >= FULLSCREEN_STAGE_CACHE_VARIANTS {
            let index = candidates
                .iter()
                .position(|stage| !stage.lease.load(std::sync::atomic::Ordering::Acquire))
                .expect("an unleased stage exists");
            Some(candidates.remove(index))
        } else {
            None
        };
        let lease = std::sync::Arc::new(std::sync::atomic::AtomicBool::new(true));
        candidates.push(CachedFullscreenStage {
            program_identity: program.identity.as_str().to_string(),
            shader_pack_generation: program.shader_pack_generation,
            epochs,
            targets: targets.clone(),
            color_resources: plan.prepared.color_resources.clone(),
            target: plan.compiled.target,
            pass: plan.compiled.pass,
            objects,
            texture_transform_buffer: plan.bound.texture_transform_buffer,
            scalar_uniform_buffer: plan.bound.scalar_uniform_buffer,
            source_data_set: plan.bound.source_data_set,
            lease: std::sync::Arc::clone(&lease),
        });
        drop(stages);
        plan.prepared.owns_color_resources = false;
        plan.compiled.owns_target = false;
        plan.bound.owns_stage_resources = false;
        plan.stage_lease = Some(lease);
        cache.retire_stages(gal, evicted);
        plan
    }

    #[allow(clippy::too_many_arguments)]
    fn stage_uncached(
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        manifest: &ShaderPackColorTargetManifest,
        targets: &ShaderPackColorTargets,
        external_inputs: impl IntoIterator<Item = TerrainSourceOwnedResourceSet>,
        extent: crate::render::vulkanic::resources::Extent3d,
        cache: Option<(&FullscreenPipelineCache, (u64, u64))>,
    ) -> GalResult<Self> {
        let prepared = PreparedFullscreenSourcePass::prepare(
            gal,
            program,
            manifest,
            targets,
            external_inputs,
        )?;
        let compiled = match prepared.compile_cached(gal, program, extent, cache) {
            Ok(compiled) => compiled,
            Err(error) => {
                prepared.destroy(gal);
                return Err(error);
            }
        };
        let bound = match prepared.bind_resources(gal, program, &compiled) {
            Ok(bound) => bound,
            Err(error) => {
                compiled.destroy(gal);
                prepared.destroy(gal);
                return Err(error);
            }
        };
        Ok(Self {
            prepared,
            compiled,
            bound,
            stage_lease: None,
        })
    }

    pub(crate) fn append_draw(
        &self,
        program: &LoweredFullscreenSourceProgram,
        frame: FullscreenSourcePassFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.prepared
            .append_draw(program, &self.compiled, &self.bound, frame, operations)
    }

    /// Records one source stage through the submit-confirmed semantic color
    /// scheduler. This is the route-facing API for both vanilla and Distant
    /// Horizons source chains: callers cannot invent color attachment states
    /// or sample current/feedback images before the scheduler established
    /// them. Native target identity remains entirely inside the GAL objects.
    pub(crate) fn append_draw_with_color_frame(
        &self,
        program: &LoweredFullscreenSourceProgram,
        color_frame: &mut ShaderPackColorFramePlan,
        mut frame: FullscreenSourcePassFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        for binding in program.opaque_resource_bindings.bindings() {
            if !matches!(
                binding.kind(),
                TerrainSourceOpaqueResourceKind::CombinedTextureSampler
            ) || !matches!(
                binding.role(),
                TerrainSourceResourceRole::ShaderPackColor(_)
            ) {
                continue;
            }
            let feedback = program.feedback_requirements.iter().any(|requirement| {
                requirement.role == binding.role()
                    && requirement.sampled_binding == binding.binding()
            });
            let mipmapped = program.mipmap_requirements.iter().any(|requirement| {
                requirement.role == binding.role()
                    && requirement.sampled_binding == binding.binding()
            });
            color_frame.require_sample_with_mips(&binding.role(), feedback, mipmapped)?;
        }
        frame.color_attachment_before =
            color_frame.attachment_states(&self.prepared.color_targets)?;
        let clear_mask = color_frame.attachment_clear_mask(&self.prepared.color_targets)?;
        frame.clear_targets_this_pass = Some(clear_mask.clone());
        self.append_draw(program, frame, operations)?;
        color_frame.record_pass(&self.prepared.color_targets, &self.prepared.outputs, &clear_mask)
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        self.bound.destroy(gal);
        self.compiled.destroy(gal);
        self.prepared.destroy(gal);
        if let Some(lease) = self.stage_lease {
            lease.store(false, std::sync::atomic::Ordering::Release);
        }
    }
}

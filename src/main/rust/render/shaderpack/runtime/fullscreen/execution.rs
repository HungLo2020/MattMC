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
    }
}

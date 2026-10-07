//! Destroying resources: deferred and immediate destroys and the dependency graph between handles.

use super::*;

impl VulkanicGal {
    /// Destroys a resource. Fails while other resources depend on it. A
    /// resource still used by an incomplete submission is retired once that
    /// submission completes; inside a command-recording scope the destroy is
    /// queued until the outermost scope finishes.
    pub fn destroy(&mut self, handle: Handle) -> GalResult<()> {
        if self.command_recording_depth != 0 {
            if self.command_recording_destroy_set.contains(&handle) {
                return Err(GalError::handle(
                    StatusCode::DoubleDestroy,
                    "resource is already queued for destruction after command recording",
                ));
            }
            self.validate_any_resource(handle)?;
            if let Some(dependents) = self.dependencies.get(&handle) {
                if dependents
                    .iter()
                    .any(|dependent| !self.command_recording_destroy_set.contains(dependent))
                {
                    return self.ensure_no_dependents(handle);
                }
            }
            self.command_recording_destroys.push(handle);
            self.command_recording_destroy_set.insert(handle);
            return Ok(());
        }
        self.destroy_now(handle)
    }

    pub(super) fn destroy_now(&mut self, handle: Handle) -> GalResult<()> {
        self.ensure_no_dependents(handle)?;
        self.buffer_upload_capture.forget(handle);
        let owned_frame_depth = if handle.kind() == Some(HandleKind::FrameTarget) {
            self.frame_target_depth_populated.remove(&handle);
            self.frame_target_depth_pending.remove(&handle);
            self.frame_target_depth.remove(&handle)
        } else {
            None
        };
        let pending = match handle.kind() {
            Some(HandleKind::Buffer) => self.remove_record(handle, HandleKind::Buffer)?,
            Some(HandleKind::Texture) => self.remove_record(handle, HandleKind::Texture)?,
            Some(HandleKind::TextureView) => self.remove_record(handle, HandleKind::TextureView)?,
            Some(HandleKind::Sampler) => self.remove_record(handle, HandleKind::Sampler)?,
            Some(HandleKind::CombinedTextureSampler) => {
                self.remove_record(handle, HandleKind::CombinedTextureSampler)?
            }
            Some(HandleKind::ShaderModule) => {
                self.remove_record(handle, HandleKind::ShaderModule)?
            }
            Some(HandleKind::ResourceLayout) => {
                self.remove_record(handle, HandleKind::ResourceLayout)?
            }
            Some(HandleKind::ResourceSet) => self.remove_record(handle, HandleKind::ResourceSet)?,
            Some(HandleKind::PipelineLayout) => {
                self.remove_record(handle, HandleKind::PipelineLayout)?
            }
            Some(HandleKind::GraphicsPipeline) => {
                self.remove_record(handle, HandleKind::GraphicsPipeline)?
            }
            Some(HandleKind::ComputePipeline) => {
                self.remove_record(handle, HandleKind::ComputePipeline)?
            }
            Some(HandleKind::RenderTarget) => {
                self.remove_record(handle, HandleKind::RenderTarget)?
            }
            Some(HandleKind::FrameTarget) => self.remove_record(handle, HandleKind::FrameTarget)?,
            Some(HandleKind::RenderPass) => self.remove_record(handle, HandleKind::RenderPass)?,
            None => {
                return Err(GalError::handle(
                    StatusCode::WrongHandleType,
                    "unknown handle kind",
                ))
            }
        };
        if let Some((depth_texture, depth_view)) = owned_frame_depth {
            // The view owns the dependency edge to the texture, so retire it
            // first. Both remain private Rust GAL resources.
            let _ = self.destroy(depth_view);
            let _ = self.destroy(depth_texture);
        }
        self.remove_reverse_edges(handle);
        if let Some(last_submission) = pending.1 {
            if last_submission > self.completed_submission {
                self.retirement.defer(handle, last_submission);
                self.pending_destroys.insert(handle, pending.0);
                self.metrics.deferred_retires += 1;
                return Ok(());
            }
        }
        self.backend
            .destroy(handle, pending.0.kind, pending.0.token)?;
        self.metrics.resource_destroys += 1;
        Ok(())
    }

    pub(super) fn add_dependency(&mut self, resource: Handle, dependent: Handle) {
        self.dependencies
            .entry(resource)
            .or_default()
            .insert(dependent);
        self.reverse_dependencies
            .entry(dependent)
            .or_default()
            .insert(resource);
    }

    pub(super) fn remove_reverse_edges(&mut self, dependent: Handle) {
        if let Some(resources) = self.reverse_dependencies.remove(&dependent) {
            for resource in resources {
                if let Some(dependents) = self.dependencies.get_mut(&resource) {
                    dependents.remove(&dependent);
                    if dependents.is_empty() {
                        self.dependencies.remove(&resource);
                    }
                }
            }
        }
    }

    pub(super) fn ensure_no_dependents(&mut self, handle: Handle) -> GalResult<()> {
        if let Some(dependents) = self.dependencies.get(&handle) {
            if !dependents.is_empty() {
                let label = self.dependency_debug_label(handle);
                let dependents = dependents
                    .iter()
                    .map(|dependent| {
                        format!(
                            "0x{:016x} ({})",
                            dependent.raw(),
                            self.dependency_debug_label(*dependent)
                        )
                    })
                    .collect::<Vec<_>>()
                    .join(",");
                return self.validation_error(GalError::resource(
                    StatusCode::DependencyViolation,
                    format!(
                        "resource 0x{:016x} ({label}) has live dependents [{dependents}]",
                        handle.raw()
                    ),
                ));
            }
        }
        Ok(())
    }

    pub(super) fn dependency_debug_label(&self, handle: Handle) -> &str {
        match handle.kind() {
            Some(HandleKind::TextureView) => self
                .texture_views
                .get(handle)
                .map(|record| record.desc.label.as_str())
                .unwrap_or("<stale-texture-view>"),
            Some(HandleKind::ResourceSet) => self
                .resource_sets
                .get(handle)
                .map(|record| record.desc.label.as_str())
                .unwrap_or("<stale-resource-set>"),
            Some(HandleKind::CombinedTextureSampler) => self
                .combined_texture_samplers
                .get(handle)
                .map(|record| record.desc.label.as_str())
                .unwrap_or("<stale-combined-texture-sampler>"),
            Some(HandleKind::Texture) => self
                .textures
                .get(handle)
                .map(|record| record.desc.label.as_str())
                .unwrap_or("<stale-texture>"),
            _ => "<unlabeled-resource>",
        }
    }

    pub(super) fn remove_record(
        &mut self,
        handle: Handle,
        kind: HandleKind,
    ) -> GalResult<(PendingDestroy, Option<SubmissionId>)> {
        macro_rules! remove_from {
            ($arena:expr) => {{
                let record = $arena.remove(handle)?;
                (
                    PendingDestroy {
                        kind,
                        token: record.token,
                    },
                    record.last_submission,
                )
            }};
        }
        Ok(match kind {
            HandleKind::Buffer => remove_from!(self.buffers),
            HandleKind::Texture => remove_from!(self.textures),
            HandleKind::TextureView => remove_from!(self.texture_views),
            HandleKind::Sampler => remove_from!(self.samplers),
            HandleKind::CombinedTextureSampler => remove_from!(self.combined_texture_samplers),
            HandleKind::ShaderModule => remove_from!(self.shaders),
            HandleKind::ResourceLayout => remove_from!(self.resource_layouts),
            HandleKind::ResourceSet => remove_from!(self.resource_sets),
            HandleKind::PipelineLayout => remove_from!(self.pipeline_layouts),
            HandleKind::GraphicsPipeline => remove_from!(self.graphics_pipelines),
            HandleKind::ComputePipeline => remove_from!(self.compute_pipelines),
            HandleKind::RenderTarget => remove_from!(self.render_targets),
            HandleKind::FrameTarget => remove_from!(self.frame_targets),
            HandleKind::RenderPass => remove_from!(self.render_passes),
        })
    }
}

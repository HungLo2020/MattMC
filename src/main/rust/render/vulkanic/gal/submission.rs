//! Submitting command batches, tracking in-flight resources, completion and retirement.

use super::*;

/// Whole-frame submission tracing (`MATTMC_TRACE_WHOLE_FRAME`, read once).
/// The message is built only when tracing is on.
pub(super) fn submission_trace(message: impl FnOnce() -> String) {
    static ENABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    if *ENABLED.get_or_init(|| std::env::var_os("MATTMC_TRACE_WHOLE_FRAME").is_some()) {
        crate::core::console::stderr(format_args!("{}", message()));
    }
}

impl VulkanicGal {
    /// Validates a batch of command lists (ops, handles, hazards: overlapping
    /// conflicting accesses need an explicit barrier), removes redundant
    /// state changes, and submits it. The returned token names the submission.
    pub fn submit(&mut self, batch: SubmissionBatch) -> GalResult<SyncToken> {
        self.submit_inner(batch, None)
    }

    /// `submit`, also accumulating what the GAL measured into `profile`.
    pub fn submit_profiled(
        &mut self,
        batch: SubmissionBatch,
        profile: &mut SubmitProfile,
    ) -> GalResult<SyncToken> {
        self.submit_inner(batch, Some(profile))
    }

    pub(super) fn submit_inner(
        &mut self,
        mut batch: SubmissionBatch,
        mut profile: Option<&mut SubmitProfile>,
    ) -> GalResult<SyncToken> {
        let submit_started = std::time::Instant::now();
        let creates_before = self.metrics.resource_creates;
        let destroys_before = self.metrics.resource_destroys;
        let backend_metrics_before = self.backend.runtime_metrics();
        if batch.command_lists.is_empty() {
            return self.validation_error(GalError::submission(
                StatusCode::InvalidArgument,
                "submission batch must contain command lists",
            ));
        }
        let capabilities = self.capabilities();
        if batch.command_lists.len() > capabilities.limits.max_command_lists_per_submission as usize
        {
            return self.unsupported(format!(
                "submission '{}' command list count {} exceeds backend '{}' limit {}",
                batch.label,
                batch.command_lists.len(),
                capabilities.name,
                capabilities.limits.max_command_lists_per_submission
            ));
        }
        // Keep receipts alive through validation/encoding/submission, but do
        // not expose CPU reservation bookkeeping to backend command lowering.
        let mut submission_usages = Vec::new();
        for list in &mut batch.command_lists {
            list.operations.retain(|op| {
                if let CommandOp::TrackSubmission(usage) = op {
                    submission_usages.push(usage.clone());
                    false
                } else {
                    true
                }
            });
        }
        // Command, handle and hazard checks guard against producer bugs. Normal
        // play skips them (see `per_frame_validation`); upload capture needs
        // the hazard walk to observe watched writes.
        let validate = per_frame_validation() || self.buffer_upload_capture.is_watching();
        for list in &batch.command_lists {
            if list.operations.len() > capabilities.limits.max_commands_per_list as usize {
                return self.unsupported(format!(
                    "command list '{}' operation count {} exceeds backend '{}' limit {}",
                    list.label,
                    list.operations.len(),
                    capabilities.name,
                    capabilities.limits.max_commands_per_list
                ));
            }
            if !validate {
                continue;
            }
            let validate_ops_started = std::time::Instant::now();
            self.validate_command_ops(&list.label, &list.operations)?;
            if let Some(profile) = profile.as_deref_mut() {
                profile.gal_validate_ops_nanos = profile
                    .gal_validate_ops_nanos
                    .saturating_add(elapsed_nanos_u64(validate_ops_started));
            }
        }
        // Normalization may retain descriptor sets across a pipeline switch
        // only when the two pipeline objects explicitly share the same layout.
        // Resolve those immutable layout identities once before mutating the
        // command lists; an unknown handle remains conservatively incompatible.
        let mut graphics_pipeline_layouts = BTreeMap::new();
        let mut compute_pipeline_layouts = BTreeMap::new();
        for list in &batch.command_lists {
            for operation in &list.operations {
                match operation {
                    CommandOp::BindGraphicsPipeline(handle) => {
                        if !graphics_pipeline_layouts.contains_key(handle) {
                            graphics_pipeline_layouts
                                .insert(*handle, self.graphics_pipelines.get(*handle)?.desc.layout);
                        }
                    }
                    CommandOp::BindComputePipeline(handle) => {
                        if !compute_pipeline_layouts.contains_key(handle) {
                            compute_pipeline_layouts
                                .insert(*handle, self.compute_pipelines.get(*handle)?.desc.layout);
                        }
                    }
                    _ => {}
                }
            }
        }
        let normalization = normalize_submission_batch_with_pipeline_layouts(
            &mut batch,
            &graphics_pipeline_layouts,
            &compute_pipeline_layouts,
        );
        if let Some(profile) = profile.as_deref_mut() {
            profile.gal_command_ops_before_normalize = profile
                .gal_command_ops_before_normalize
                .saturating_add(normalization.ops_before);
            profile.gal_command_ops_after_normalize = profile
                .gal_command_ops_after_normalize
                .saturating_add(normalization.ops_after);
            profile.gal_redundant_pipeline_binds_removed = profile
                .gal_redundant_pipeline_binds_removed
                .saturating_add(normalization.pipeline_binds_removed);
            profile.gal_redundant_resource_set_binds_removed = profile
                .gal_redundant_resource_set_binds_removed
                .saturating_add(normalization.resource_set_binds_removed);
            profile.gal_redundant_vertex_buffer_binds_removed = profile
                .gal_redundant_vertex_buffer_binds_removed
                .saturating_add(normalization.vertex_buffer_binds_removed);
            profile.gal_redundant_index_buffer_binds_removed = profile
                .gal_redundant_index_buffer_binds_removed
                .saturating_add(normalization.index_buffer_binds_removed);
        }
        // Gather host buffer writes at the start of their lists so the GPU
        // does not drain between passes for each one.
        for list in &mut batch.command_lists {
            host_write_hoist::hoist_host_writes(list, |set, buffer| {
                self.resource_sets.get(set).map_or(true, |record| {
                    record.desc.bindings.iter().any(|binding| binding.resource == buffer)
                })
            });
        }
        let referenced = referenced_handles(&batch);
        let validate_handles_started = std::time::Instant::now();
        if validate {
            for handle in &referenced {
                self.validate_any_resource(*handle)?;
            }
        }
        if let Some(profile) = profile.as_deref_mut() {
            profile.gal_validate_handles_nanos = profile
                .gal_validate_handles_nanos
                .saturating_add(elapsed_nanos_u64(validate_handles_started));
        }
        let hazards_started = std::time::Instant::now();
        if validate {
            self.validate_submission_hazards(&batch, profile.as_deref_mut())?;
        }
        if let Some(profile) = profile.as_deref_mut() {
            profile.gal_hazard_analysis_nanos = profile
                .gal_hazard_analysis_nanos
                .saturating_add(elapsed_nanos_u64(hazards_started));
            add_command_profile(profile, &batch);
        }
        let validated = ValidatedSubmissionBatch::from(batch);
        let id = SubmissionId(self.next_submission);
        self.next_submission += 1;
        submission_trace(|| format!(
            "gal.submit.encode.begin id={} label={} pre_encode_nanos={}",
            id.0,
            validated.label,
            elapsed_nanos_u64(submit_started)
        ));
        let backend_encode_started = std::time::Instant::now();
        self.backend.encode_passes(&validated)?;
        submission_trace(|| format!(
            "gal.submit.encode.end id={} elapsed_nanos={}",
            id.0,
            elapsed_nanos_u64(backend_encode_started)
        ));
        if let Some(profile) = profile.as_deref_mut() {
            profile.backend_encode_nanos = profile
                .backend_encode_nanos
                .saturating_add(elapsed_nanos_u64(backend_encode_started));
        }
        let backend_submit_started = std::time::Instant::now();
        submission_trace(|| format!("gal.submit.queue.begin id={}", id.0));
        self.backend.submit(id, &validated)?;
        self.latest_accepted_submission = id;
        self.buffer_upload_capture.accept(id);
        for usage in &submission_usages {
            usage.accept(id);
        }
        submission_trace(|| format!(
            "gal.submit.queue.end id={} elapsed_nanos={}",
            id.0,
            elapsed_nanos_u64(backend_submit_started)
        ));
        if let Some(profile) = profile.as_deref_mut() {
            profile.backend_submit_nanos = profile
                .backend_submit_nanos
                .saturating_add(elapsed_nanos_u64(backend_submit_started));
        }
        self.metrics.submissions += 1;
        // Shared dependencies (layouts, samplers, textures behind several
        // sets) are marked once per submission instead of once per referrer.
        let mut marked = std::mem::take(&mut self.in_flight_scratch);
        marked.clear();
        for handle in referenced {
            self.mark_in_flight(handle, id, &mut marked)?;
        }
        self.in_flight_scratch = marked;
        if let Some(profile) = profile.as_deref_mut() {
            profile.gal_submit_total_nanos = profile
                .gal_submit_total_nanos
                .saturating_add(elapsed_nanos_u64(submit_started));
            profile.resource_creates_delta =
                self.metrics.resource_creates.saturating_sub(creates_before);
            profile.resource_destroys_delta = self
                .metrics
                .resource_destroys
                .saturating_sub(destroys_before);
            let backend_metrics_after = self.backend.runtime_metrics();
            add_backend_metric_deltas(profile, backend_metrics_before, backend_metrics_after);
        }
        Ok(SyncToken { submission: id })
    }

    /// Polls completion and destroys every deferred resource whose last
    /// submission has completed. Returns the handles destroyed.
    pub fn retire_completed(&mut self) -> GalResult<Vec<Handle>> {
        self.poll_completed();
        self.backend.retire(self.completed_submission)?;
        let mut retired = Vec::new();
        for entry in self.retirement.drain_completed(self.completed_submission) {
            if let Some(pending) = self.pending_destroys.remove(&entry.handle) {
                self.backend
                    .destroy(entry.handle, pending.kind, pending.token)?;
                self.metrics.resource_destroys += 1;
                retired.push(entry.handle);
            }
        }
        Ok(retired)
    }

    /// The newest submission the backend reports complete, without waiting.
    pub fn poll_completed(&mut self) -> SubmissionId {
        let completed = self.backend.completed_submission();
        if completed > self.completed_submission {
            self.completed_submission = completed;
        }
        self.completed_submission
    }

    /// The newest submission the GAL has accepted.
    pub fn latest_submission_id(&self) -> SubmissionId {
        self.latest_accepted_submission
    }

    /// The last submission that used a render pass, if any.
    pub fn render_pass_last_submission(
        &self,
        pass: Handle,
    ) -> GalResult<Option<SubmissionId>> {
        Ok(self.render_passes.get(pass)?.last_submission)
    }

    /// Exposes the id that the immediately following submission will receive.
    /// Frontends use this to protect transient stream ranges until that
    /// submission completes; taking this value must be followed by one submit.
    pub fn next_submission_id(&self) -> SubmissionId {
        SubmissionId(self.next_submission)
    }

    /// Waits for submissions up to `id` to complete, then destroys the
    /// deferred resources they retire. Fails for an id not yet submitted.
    pub fn retire_through(
        &mut self,
        id: SubmissionId,
    ) -> GalResult<Vec<Handle>> {
        if id > self.latest_submission_id() {
            return Err(GalError::invalid_argument(
                "cannot wait for an unsubmitted completion id",
            ));
        }
        self.backend.retire(id)?;
        if id > self.completed_submission {
            self.completed_submission = id;
        }
        let mut retired = Vec::new();
        for entry in self.retirement.drain_completed(self.completed_submission) {
            if let Some(pending) = self.pending_destroys.remove(&entry.handle) {
                self.backend
                    .destroy(entry.handle, pending.kind, pending.token)?;
                self.metrics.resource_destroys += 1;
                retired.push(entry.handle);
            }
        }
        Ok(retired)
    }

    /// Buffer bytes read back to the host by completed submissions.
    pub fn completed_host_reads(&self) -> Vec<CompletedHostRead> {
        self.backend.completed_host_reads()
    }

    pub(super) fn mark_in_flight(
        &mut self,
        handle: Handle,
        id: SubmissionId,
        marked: &mut HashSet<Handle, AccessHashBuilder>,
    ) -> GalResult<()> {
        if !marked.insert(handle) {
            // Already marked with its whole dependency closure for `id`.
            return Ok(());
        }
        // A command references resource sets and pipelines, while their
        // descriptor/layout dependency edges own the sampler, image view,
        // texture, and shader handles they contain.  Retire the complete
        // dependency closure with the submission; marking only the directly
        // encoded set allowed a replaced sampler to be destroyed while its
        // descriptor set was still executing on Vulkan.
        let dependencies: smallvec::SmallVec<[Handle; 8]> = self
            .reverse_dependencies
            .get(&handle)
            .map(|dependencies| dependencies.iter().copied().collect())
            .unwrap_or_default();
        for dependency in dependencies {
            self.mark_in_flight(dependency, id, marked)?;
        }
        match handle.kind() {
            Some(HandleKind::Buffer) => {
                self.buffers.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::Texture) => {
                self.textures.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::TextureView) => {
                self.texture_views.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::Sampler) => {
                self.samplers.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::CombinedTextureSampler) => {
                // Copy the two handles; cloning the descriptor allocated its label.
                let pair = {
                    let desc = &self.combined_texture_samplers.get(handle)?.desc;
                    (desc.texture_view, desc.sampler)
                };
                self.combined_texture_samplers
                    .get_mut_record(handle)?
                    .last_submission = Some(id);
                self.texture_views
                    .get_mut_record(pair.0)?
                    .last_submission = Some(id);
                self.samplers.get_mut_record(pair.1)?.last_submission = Some(id);
            }
            Some(HandleKind::ShaderModule) => {
                self.shaders.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::ResourceLayout) => {
                self.resource_layouts
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::ResourceSet) => {
                self.resource_sets.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::PipelineLayout) => {
                self.pipeline_layouts
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::GraphicsPipeline) => {
                self.graphics_pipelines
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::ComputePipeline) => {
                self.compute_pipelines
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::RenderTarget) => {
                self.render_targets.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::FrameTarget) => {
                self.frame_targets.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::RenderPass) => {
                self.render_passes.get_mut_record(handle)?.last_submission = Some(id)
            }
            None => {
                return Err(GalError::handle(
                    StatusCode::WrongHandleType,
                    "unknown handle kind",
                ))
            }
        }
        Ok(())
    }
}

/// Every handle the batch names directly, sorted and deduplicated.
/// Whether submissions are validated every frame (command ops, handles and
/// hazards). Always in tests and debug builds; in release builds only with
/// `MATTMC_GAL_VALIDATION=1` (captures and validation runs set it). Normal
/// play trusts its producers, as Vulkan itself does without validation layers.
pub(crate) fn per_frame_validation() -> bool {
    static ENABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *ENABLED.get_or_init(|| {
        cfg!(test)
            || cfg!(debug_assertions)
            || matches!(std::env::var("MATTMC_GAL_VALIDATION").as_deref(), Ok("1" | "true" | "on"))
    })
}

pub(super) fn referenced_handles(batch: &SubmissionBatch) -> Vec<Handle> {
    let mut handles = ReferencedHandles(Vec::with_capacity(batch.command_lists.iter().map(|list| list.operations.len() * 2).sum()));
    for list in &batch.command_lists {
        for op in &list.operations {
            match op {
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors,
                    depth_stencil,
                } => {
                    handles.insert(*pass);
                    handles.insert(*target);
                    for color in colors {
                        handles.insert(color.view);
                    }
                    if let Some(depth) = depth_stencil {
                        handles.insert(depth.view);
                    }
                }
                CommandOp::BindGraphicsPipeline(handle)
                | CommandOp::BindComputePipeline(handle)
                | CommandOp::DrawIndirect { buffer: handle, .. }
                | CommandOp::DrawIndexedIndirect { buffer: handle, .. }
                | CommandOp::DispatchIndirect { buffer: handle, .. }
                | CommandOp::SetIndexBuffer { buffer: handle, .. }
                | CommandOp::HostWriteBuffer { buffer: handle, .. }
                | CommandOp::HostReadBuffer { buffer: handle, .. }
                | CommandOp::Barrier(crate::render::vulkanic::commands::ResourceBarrier {
                    resource: handle, ..
                }) => {
                    handles.insert(*handle);
                }
                CommandOp::CopyBufferToTexture(region) | CommandOp::CopyTextureToBuffer(region) => {
                    handles.insert(region.buffer);
                    handles.insert(region.texture);
                }
                CommandOp::CopyTexture(region) => {
                    handles.insert(region.src_texture);
                    handles.insert(region.dst_texture);
                }
                CommandOp::CopyFrameTargetToTexture { src, dst, .. } => {
                    handles.insert(*src);
                    handles.insert(*dst);
                }
                CommandOp::CopyTextureToFrameTarget { src, dst, .. } => {
                    handles.insert(*src);
                    handles.insert(*dst);
                }
                CommandOp::GenerateMipmaps { texture, .. } => {
                    handles.insert(*texture);
                }
                CommandOp::Present {
                    texture: handle, ..
                } => {
                    handles.insert(*handle);
                }
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set,
                    ..
                } => {
                    handles.insert(*pipeline_layout);
                    handles.insert(*set);
                }
                CommandOp::SetVertexBuffer { buffer, .. } => {
                    handles.insert(*buffer);
                }
                CommandOp::CopyBuffer { src, dst, .. }
                | CommandOp::CopyBufferRegion { src, dst, .. } => {
                    handles.insert(*src);
                    handles.insert(*dst);
                }
                CommandOp::Draw { .. }
                | CommandOp::DrawIndexed { .. }
                | CommandOp::Dispatch { .. }
                | CommandOp::TrackSubmission(_)
                | CommandOp::EndPass => {}
            }
        }
    }
    let mut handles = handles.0;
    handles.sort_unstable();
    handles.dedup();
    handles
}

/// Collects handles; `insert` keeps the call sites of the former set.
struct ReferencedHandles(Vec<Handle>);

impl ReferencedHandles {
    fn insert(&mut self, handle: Handle) {
        self.0.push(handle);
    }
}

/// Diagnostic tracing selected by label: the environment variable holds a
/// case-insensitive label substring; unset or empty traces nothing.
pub(super) fn trace_label_matches(variable: &str, label: &str) -> bool {
    std::env::var(variable).is_ok_and(|filter| {
        !filter.is_empty() && label.to_ascii_lowercase().contains(&filter.to_ascii_lowercase())
    })
}

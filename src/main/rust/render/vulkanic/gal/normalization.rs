//! Command normalization: removing redundant binds and fusing identical adjacent passes.

use super::*;

#[derive(Default)]
pub(in crate::render::vulkanic) struct CommandNormalizationStats {
    pub(in crate::render::vulkanic) ops_before: u64,
    pub(in crate::render::vulkanic) ops_after: u64,
    pub(in crate::render::vulkanic) pipeline_binds_removed: u64,
    pub(in crate::render::vulkanic) resource_set_binds_removed: u64,
    pub(in crate::render::vulkanic) vertex_buffer_binds_removed: u64,
    pub(in crate::render::vulkanic) index_buffer_binds_removed: u64,
}

#[derive(Default)]
pub(super) struct CommandStateTracker {
    pub(super) graphics_pipeline: Option<Handle>,
    pub(super) compute_pipeline: Option<Handle>,
    pub(super) pipeline_layout: Option<Handle>,
    pub(super) pipeline_layout_known: bool,
    pub(super) resource_sets: BTreeMap<(Handle, u32), (Handle, Vec<u64>)>,
    pub(super) vertex_buffers: BTreeMap<u32, (Handle, u64)>,
    pub(super) index_buffer: Option<(Handle, u64, IndexType)>,
}

impl CommandStateTracker {
    pub(super) fn invalidate(&mut self) {
        self.graphics_pipeline = None;
        self.compute_pipeline = None;
        self.pipeline_layout = None;
        self.pipeline_layout_known = false;
        self.resource_sets.clear();
        self.vertex_buffers.clear();
        self.index_buffer = None;
    }
}

#[cfg(test)]
pub(in crate::render::vulkanic) fn normalize_submission_batch(batch: &mut SubmissionBatch) -> CommandNormalizationStats {
    normalize_submission_batch_with_pipeline_layouts(batch, &BTreeMap::new(), &BTreeMap::new())
}

pub(in crate::render::vulkanic) fn normalize_submission_batch_with_pipeline_layouts(
    batch: &mut SubmissionBatch,
    graphics_pipeline_layouts: &BTreeMap<Handle, Handle>,
    compute_pipeline_layouts: &BTreeMap<Handle, Handle>,
) -> CommandNormalizationStats {
    let mut stats = CommandNormalizationStats::default();
    for list in &mut batch.command_lists {
        stats.ops_before = stats
            .ops_before
            .saturating_add(list.operations.len() as u64);
        let original = fuse_adjacent_identical_passes(std::mem::take(&mut list.operations));
        let mut normalized = Vec::with_capacity(original.len());
        let mut state = CommandStateTracker::default();
        for op in original {
            let keep =
                match &op {
                    CommandOp::TrackSubmission(_) => true,
                    CommandOp::BeginPass { .. } | CommandOp::EndPass | CommandOp::Barrier(_) => {
                        state.invalidate();
                        true
                    }
                    CommandOp::BindGraphicsPipeline(handle) => {
                        if state.graphics_pipeline == Some(*handle) {
                            stats.pipeline_binds_removed =
                                stats.pipeline_binds_removed.saturating_add(1);
                            false
                        } else {
                            state.graphics_pipeline = Some(*handle);
                            state.compute_pipeline = None;
                            let layout = graphics_pipeline_layouts.get(handle).copied();
                            if layout.is_none()
                                || !state.pipeline_layout_known
                                || state.pipeline_layout != layout
                            {
                                // Descriptor sets are bound against a pipeline
                                // layout. If the identity is unknown or changes,
                                // replay every set; this is what protects DH's
                                // four-binding exact-atlas set from being reused
                                // by a two-binding reduced-color pipeline.
                                state.resource_sets.clear();
                            }
                            state.pipeline_layout = layout;
                            state.pipeline_layout_known = layout.is_some();
                            true
                        }
                    }
                    CommandOp::BindComputePipeline(handle) => {
                        if state.compute_pipeline == Some(*handle) {
                            stats.pipeline_binds_removed =
                                stats.pipeline_binds_removed.saturating_add(1);
                            false
                        } else {
                            state.compute_pipeline = Some(*handle);
                            state.graphics_pipeline = None;
                            let layout = compute_pipeline_layouts.get(handle).copied();
                            if layout.is_none()
                                || !state.pipeline_layout_known
                                || state.pipeline_layout != layout
                            {
                                state.resource_sets.clear();
                            }
                            state.pipeline_layout = layout;
                            state.pipeline_layout_known = layout.is_some();
                            true
                        }
                    }
                    CommandOp::BindResourceSet {
                        pipeline_layout,
                        set_index,
                        set,
                        dynamic_offsets,
                    } => {
                        let key = (*pipeline_layout, *set_index);
                        let value = (*set, dynamic_offsets.clone());
                        if state.resource_sets.get(&key) == Some(&value) {
                            stats.resource_set_binds_removed =
                                stats.resource_set_binds_removed.saturating_add(1);
                            false
                        } else {
                            state.resource_sets.insert(key, value);
                            true
                        }
                    }
                    CommandOp::SetVertexBuffer {
                        slot,
                        buffer,
                        offset,
                    } => {
                        let value = (*buffer, *offset);
                        if state.vertex_buffers.get(slot).copied() == Some(value) {
                            stats.vertex_buffer_binds_removed =
                                stats.vertex_buffer_binds_removed.saturating_add(1);
                            false
                        } else {
                            state.vertex_buffers.insert(*slot, value);
                            true
                        }
                    }
                    CommandOp::SetIndexBuffer {
                        buffer,
                        offset,
                        index_type,
                    } => {
                        let value = (*buffer, *offset, *index_type);
                        if state.index_buffer == Some(value) {
                            stats.index_buffer_binds_removed =
                                stats.index_buffer_binds_removed.saturating_add(1);
                            false
                        } else {
                            state.index_buffer = Some(value);
                            true
                        }
                    }
                    CommandOp::CopyBuffer { .. }
                    | CommandOp::CopyBufferRegion { .. }
                    | CommandOp::CopyBufferToTexture(_)
                    | CommandOp::CopyTextureToBuffer(_)
                    | CommandOp::CopyTexture(_)
                    | CommandOp::CopyFrameTargetToTexture { .. }
                    | CommandOp::CopyTextureToFrameTarget { .. }
                    | CommandOp::GenerateMipmaps { .. }
                    | CommandOp::HostWriteBuffer { .. }
                    | CommandOp::HostReadBuffer { .. }
                    | CommandOp::Present { .. } => {
                        state.invalidate();
                        true
                    }
                    CommandOp::Draw { .. }
                    | CommandOp::DrawIndexed { .. }
                    | CommandOp::DrawIndirect { .. }
                    | CommandOp::DrawIndexedIndirect { .. }
                    | CommandOp::Dispatch { .. }
                    | CommandOp::DispatchIndirect { .. } => true,
                };
            if keep {
                normalized.push(op);
            }
        }
        stats.ops_after = stats.ops_after.saturating_add(normalized.len() as u64);
        list.operations = normalized;
    }
    stats
}

pub(super) fn fuse_adjacent_identical_passes(original: Vec<CommandOp>) -> Vec<CommandOp> {
    let original_capacity = original.len();
    let mut source = original.into_iter().peekable();
    // Pass fusion only removes operations; retain the input capacity so a
    // steady-state frame does not grow the temporary command vector.
    let mut fused = Vec::with_capacity(original_capacity);
    let mut active_begin: Option<CommandOp> = None;
    while let Some(operation) = source.next() {
        match &operation {
            CommandOp::BeginPass { .. } => {
                active_begin = Some(operation.clone());
                fused.push(operation);
            }
            CommandOp::EndPass => {
                let same_pass = active_begin
                    .as_ref()
                    .is_some_and(|begin| source.peek().is_some_and(|next| next == begin));
                if same_pass {
                    // The second pass has the exact same target, attachments,
                    // and load/store contract. Keep the first pass open so
                    // adjacent ordered draws do not manufacture a Vulkan pass
                    // boundary. Barriers, uploads, and ownership receipts are
                    // hard boundaries because they prevent this adjacency.
                    source.next();
                } else {
                    active_begin = None;
                    fused.push(operation);
                }
            }
            _ => fused.push(operation),
        }
    }
    fused
}

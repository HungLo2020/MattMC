//! Access tracking and hazard analysis: read/write events per resource range and required barriers.

use super::*;

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) enum AccessMode {
    Read,
    Write,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) enum AccessFamily {
    Vertex,
    Index,
    Uniform,
    Storage,
    Sampled,
    Transfer,
    MipGeneration,
    Attachment,
    DepthAttachment,
    Host,
    Present,
    Indirect,
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) enum AccessTarget {
    Buffer {
        handle: Handle,
        offset: u64,
        size: u64,
    },
    Texture {
        texture: Handle,
        range: TextureSubresourceRange,
    },
    FrameTarget {
        handle: Handle,
    },
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) enum AccessResourceKey {
    Buffer(Handle),
    Texture(Handle),
    FrameTarget(Handle),
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(super) struct AccessEvent {
    pub(super) target: AccessTarget,
    pub(super) mode: AccessMode,
    pub(super) family: AccessFamily,
    pub(super) attachment_load_op: Option<AttachmentLoadOp>,
    pub(super) attachment_store_op: Option<AttachmentStoreOp>,
}

/// Multiply-rotate hasher for the per-submission access tracker. Its keys
/// are small handle/range tuples built by the GAL itself, so SipHash's
/// flooding resistance buys nothing; a whole frame hashes ~20k events.
/// Also used for other Rust-built, non-adversarial per-frame keys.
#[derive(Default, Clone, Copy)]
pub struct AccessHasher {
    pub(super) hash: u64,
}

impl hashing::Hasher for AccessHasher {
    fn write(&mut self, bytes: &[u8]) {
        for chunk in bytes.chunks(8) {
            let mut word = [0_u8; 8];
            word[..chunk.len()].copy_from_slice(chunk);
            self.write_u64(u64::from_le_bytes(word));
        }
    }

    fn write_u8(&mut self, value: u8) {
        self.write_u64(u64::from(value));
    }

    fn write_u16(&mut self, value: u16) {
        self.write_u64(u64::from(value));
    }

    fn write_u32(&mut self, value: u32) {
        self.write_u64(u64::from(value));
    }

    fn write_u64(&mut self, value: u64) {
        self.hash = (self.hash.rotate_left(5) ^ value).wrapping_mul(0x51_7c_c1_b7_27_22_0a_95);
    }

    fn write_usize(&mut self, value: usize) {
        self.write_u64(value as u64);
    }

    fn finish(&self) -> u64 {
        self.hash
    }
}

pub type AccessHashBuilder = hashing::BuildHasherDefault<AccessHasher>;

#[derive(Default)]
pub(super) struct AccessTracker {
    pub(super) resources: HashMap<AccessResourceKey, AccessBucket, AccessHashBuilder>,
    destinations: Vec<(AccessTarget, TextureUsageState)>,
}

#[derive(Default)]
pub(super) struct AccessBucket {
    pub(super) reads: Vec<AccessEvent>,
    pub(super) read_membership: HashSet<AccessEvent, AccessHashBuilder>,
    pub(super) writes: Vec<AccessEvent>,
}

impl AccessTracker {
    pub(super) fn push_read(&mut self, event: AccessEvent) {
        let bucket = self
            .resources
            .entry(event.target.resource_key())
            .or_default();
        if bucket.read_membership.insert(event) {
            bucket.reads.push(event);
        }
    }

    pub(super) fn push_write(&mut self, event: AccessEvent) {
        self.resources
            .entry(event.target.resource_key())
            .or_default()
            .writes
            .push(event);
    }

    pub(super) fn bucket(&self, target: AccessTarget) -> Option<&AccessBucket> {
        self.resources.get(&target.resource_key())
    }

    pub(super) fn retain_non_overlapping(&mut self, target: AccessTarget) {
        let key = target.resource_key();
        if let Some(bucket) = self.resources.get_mut(&key) {
            bucket.reads = bucket
                .reads
                .drain(..)
                .flat_map(|event| {
                    subtract_target(event.target, target)
                        .into_iter()
                        .map(move |target| AccessEvent { target, ..event })
                })
                .collect();
            bucket.read_membership = bucket.reads.iter().copied().collect();
            bucket.writes = bucket
                .writes
                .drain(..)
                .flat_map(|event| {
                    subtract_target(event.target, target)
                        .into_iter()
                        .map(move |target| AccessEvent { target, ..event })
                })
                .collect();
            if bucket.reads.is_empty() && bucket.writes.is_empty() {
                self.resources.remove(&key);
            }
        }
    }

    pub(super) fn active_read_entries(&self) -> usize {
        self.resources
            .values()
            .map(|bucket| bucket.reads.len())
            .sum()
    }

    pub(super) fn active_write_entries(&self) -> usize {
        self.resources
            .values()
            .map(|bucket| bucket.writes.len())
            .sum()
    }
}

impl VulkanicGal {
    pub(super) fn validate_submission_hazards(
        &mut self,
        batch: &SubmissionBatch,
        mut profile: Option<&mut SubmitProfile>,
    ) -> GalResult<()> {
        self.buffer_upload_capture.begin();
        let mut accesses = AccessTracker::default();
        for list in &batch.command_lists {
            let mut bound_sets = BTreeMap::<(Handle, u32), Vec<AccessEvent>>::new();
            let mut vertices = BTreeMap::<u32, AccessEvent>::new();
            let mut indices = None;
            let mut active_layout = None;
            for op in &list.operations {
                let draw = matches!(
                    op,
                    CommandOp::Draw { .. }
                        | CommandOp::DrawIndexed { .. }
                        | CommandOp::DrawIndirect { .. }
                        | CommandOp::DrawIndexedIndirect { .. }
                );
                let dispatch = matches!(
                    op,
                    CommandOp::Dispatch { .. } | CommandOp::DispatchIndirect { .. }
                );
                if draw || dispatch {
                    for ((layout, _), events) in &bound_sets {
                        if Some(*layout) == active_layout {
                            for event in events {
                                self.record_access(&mut accesses, *event, profile.as_deref_mut())?;
                            }
                        }
                    }
                    if draw {
                        for event in vertices.values() {
                            self.record_access(&mut accesses, *event, profile.as_deref_mut())?;
                        }
                        if matches!(
                            op,
                            CommandOp::DrawIndexed { .. } | CommandOp::DrawIndexedIndirect { .. }
                        ) {
                            if let Some(event) = indices {
                                self.record_access(&mut accesses, event, profile.as_deref_mut())?;
                            }
                        }
                    }
                }
                match op {
                    CommandOp::BeginPass {
                        colors,
                        depth_stencil,
                        ..
                    } => {
                        let mut pass_attachment_targets = Vec::new();
                        for color in colors {
                            let target =
                                match color.view.kind() {
                                    Some(HandleKind::FrameTarget) => {
                                        self.frame_targets.get(color.view)?;
                                        AccessTarget::FrameTarget { handle: color.view }
                                    }
                                    Some(HandleKind::TextureView) => {
                                        self.texture_view_access_target(color.view)?
                                    }
                                    _ => return self.validation_error(GalError::submission(
                                        StatusCode::WrongHandleType,
                                        "color attachment must be a texture view or frame target",
                                    )),
                                };
                            if pass_attachment_targets
                                .iter()
                                .any(|previous| targets_overlap(*previous, target))
                            {
                                return self.validation_error(GalError::submission(
                                    StatusCode::InvalidArgument,
                                    "overlapping attachments in the same pass are invalid",
                                ));
                            }
                            pass_attachment_targets.push(target);
                            let event = AccessEvent {
                                target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Attachment,
                                attachment_load_op: Some(color.load_op),
                                attachment_store_op: Some(color.store_op),
                            };
                            self.record_access(&mut accesses, event, profile.as_deref_mut())?;
                        }
                        if let Some(depth) = depth_stencil {
                            let target = self.texture_view_access_target(depth.view)?;
                            if pass_attachment_targets
                                .iter()
                                .any(|previous| targets_overlap(*previous, target))
                            {
                                return self.validation_error(GalError::submission(
                                    StatusCode::InvalidArgument,
                                    "overlapping attachments in the same pass are invalid",
                                ));
                            }
                            let event = AccessEvent {
                                target,
                                mode: AccessMode::Write,
                                family: AccessFamily::DepthAttachment,
                                attachment_load_op: Some(depth.load_op),
                                attachment_store_op: Some(depth.store_op),
                            };
                            self.record_access(&mut accesses, event, profile.as_deref_mut())?;
                        }
                    }
                    CommandOp::BindResourceSet {
                        set,
                        dynamic_offsets,
                        pipeline_layout,
                        set_index,
                    } => {
                        let mut events = Vec::new();
                        let binding_count = self.resource_sets.get(*set)?.desc.bindings.len();
                        let mut offset_index = 0;
                        for index in 0..binding_count {
                            let count = self.resource_sets.get(*set)?.desc.bindings[index]
                                .dynamic_offsets
                                .len();
                            // Keep normal binding validation allocation-free;
                            // each declared range contributes one access event.
                            for slot in 0..count.max(1) {
                                let event = {
                                    let binding =
                                        &self.resource_sets.get(*set)?.desc.bindings[index];
                                    let offset = if count == 0 {
                                        0
                                    } else if dynamic_offsets.is_empty() {
                                        binding.dynamic_offsets[slot]
                                    } else {
                                        dynamic_offsets[offset_index + slot]
                                    };
                                    self.resource_binding_access(binding, offset)?
                                };
                                events.push(event);
                            }
                            offset_index += count;
                        }
                        bound_sets.insert((*pipeline_layout, *set_index), events);
                    }
                    CommandOp::SetVertexBuffer {
                        slot,
                        buffer,
                        offset,
                    } => {
                        vertices.insert(
                            *slot,
                            AccessEvent {
                                target: self.buffer_access_target(*buffer, *offset, None)?,
                                mode: AccessMode::Read,
                                family: AccessFamily::Vertex,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                        );
                    }
                    CommandOp::SetIndexBuffer { buffer, offset, .. } => {
                        indices = Some(AccessEvent {
                            target: self.buffer_access_target(*buffer, *offset, None)?,
                            mode: AccessMode::Read,
                            family: AccessFamily::Index,
                            attachment_load_op: None,
                            attachment_store_op: None,
                        });
                    }
                    CommandOp::DrawIndirect {
                        buffer,
                        offset,
                        draw_count: _,
                    }
                    | CommandOp::DrawIndexedIndirect {
                        buffer,
                        offset,
                        draw_count: _,
                    }
                    | CommandOp::DispatchIndirect { buffer, offset } => {
                        let target = self.buffer_access_target(*buffer, *offset, None)?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Indirect,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::CopyBuffer { src, dst, size }
                    | CommandOp::CopyBufferRegion { src, dst, size, .. } => {
                        let (src_offset, dst_offset) = match op {
                            CommandOp::CopyBufferRegion {
                                src_offset,
                                dst_offset,
                                ..
                            } => (*src_offset, *dst_offset),
                            _ => (0, 0),
                        };
                        let src_target =
                            self.buffer_access_target(*src, src_offset, Some(*size))?;
                        let dst_target =
                            self.buffer_access_target(*dst, dst_offset, Some(*size))?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: src_target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: dst_target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        self.buffer_upload_capture
                            .copy(*src, src_offset, *dst, dst_offset, *size);
                    }
                    CommandOp::CopyBufferToTexture(region) => {
                        let buffer_target = self.buffer_access_target(
                            region.buffer,
                            region.buffer_offset,
                            Some(self.buffer_texture_copy_size(region)?),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: buffer_target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_copy_target(region)?,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::CopyTextureToBuffer(region) => {
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_copy_target(region)?,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        let buffer_target = self.buffer_access_target(
                            region.buffer,
                            region.buffer_offset,
                            Some(self.buffer_texture_copy_size(region)?),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: buffer_target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::CopyTexture(region) => {
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_image_copy_target(
                                    region.src_texture,
                                    region.src_mip,
                                    region.src_layer,
                                )?,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_image_copy_target(
                                    region.dst_texture,
                                    region.dst_mip,
                                    region.dst_layer,
                                )?,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::CopyFrameTargetToTexture { src, dst, .. } => {
                        // An acquired frame target has no separately addressable
                        // image handle on the explicit GAL surface. The copy
                        // operation itself is therefore the ownership/state
                        // transition from the preceding stored attachment to a
                        // transfer read; requiring a synthetic barrier on the
                        // opaque FrameTarget handle would violate that boundary.
                        accesses.retain_non_overlapping(AccessTarget::FrameTarget { handle: *src });
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: AccessTarget::FrameTarget { handle: *src },
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        // The explicit copy has completed the transfer read;
                        // the acquired target may be attached again later in
                        // this same atomic submission without a backend handle
                        // transition being exposed to the frontend.
                        accesses.retain_non_overlapping(AccessTarget::FrameTarget { handle: *src });
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_image_copy_target(*dst, 0, 0)?,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::CopyTextureToFrameTarget { src, dst, .. } => {
                        accesses.retain_non_overlapping(AccessTarget::FrameTarget { handle: *dst });
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_image_copy_target(*src, 0, 0)?,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: AccessTarget::FrameTarget { handle: *dst },
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        // A texture-to-frame copy is a complete explicit
                        // presentation write. Later GUI/world overlay passes
                        // may legally attach that same acquired target in the
                        // combined submission, with Vulkan lowering providing
                        // the TransferDst -> ColorAttachment transition.
                        // Keep this symmetric with CopyFrameTargetToTexture:
                        // no opaque frame-image state leaks into callers, but
                        // the completed transfer cannot look like an
                        // overlapping write hazard.
                        accesses.retain_non_overlapping(AccessTarget::FrameTarget { handle: *dst });
                    }
                    CommandOp::GenerateMipmaps {
                        texture,
                        subresources,
                    } => {
                        for mip in
                            subresources.base_mip..subresources.base_mip + subresources.mip_count
                        {
                            let base = mip == subresources.base_mip;
                            let range = TextureSubresourceRange {
                                base_mip: mip,
                                mip_count: 1,
                                ..*subresources
                            };
                            self.record_access(
                                &mut accesses,
                                AccessEvent {
                                    target: AccessTarget::Texture {
                                        texture: *texture,
                                        range,
                                    },
                                    mode: if base {
                                        AccessMode::Read
                                    } else {
                                        AccessMode::Write
                                    },
                                    family: if base {
                                        AccessFamily::Transfer
                                    } else {
                                        AccessFamily::MipGeneration
                                    },
                                    attachment_load_op: None,
                                    attachment_store_op: None,
                                },
                                profile.as_deref_mut(),
                            )?;
                        }
                        // The lowering publishes each generated level as a transfer source.
                        let target = AccessTarget::Texture {
                            texture: *texture,
                            range: *subresources,
                        };
                        accesses
                            .destinations
                            .retain(|(prior, _)| !targets_overlap(*prior, target));
                        accesses
                            .destinations
                            .push((target, TextureUsageState::TransferSrc));
                    }
                    CommandOp::HostWriteBuffer {
                        buffer,
                        offset,
                        data,
                    } => {
                        let target =
                            self.buffer_access_target(*buffer, *offset, Some(data.len() as u64))?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Host,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                        self.buffer_upload_capture
                            .host_write(*buffer, *offset, data);
                    }
                    CommandOp::HostReadBuffer {
                        buffer,
                        offset,
                        size,
                    } => {
                        let target = self.buffer_access_target(*buffer, *offset, Some(*size))?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Host,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::Present {
                        texture,
                        subresources,
                    } => {
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: AccessTarget::Texture {
                                    texture: *texture,
                                    range: *subresources,
                                },
                                mode: AccessMode::Read,
                                family: AccessFamily::Present,
                                attachment_load_op: None,
                                attachment_store_op: None,
                            },
                            profile.as_deref_mut(),
                        )?;
                    }
                    CommandOp::Barrier(barrier) => {
                        let barrier_target = self.barrier_target(barrier)?;
                        if let Some(bucket) = accesses.bucket(barrier_target) {
                            if let Some(prior) =
                                bucket.reads.iter().chain(&bucket.writes).find(|event| {
                                    targets_overlap(event.target, barrier_target)
                                        && !access_matches_state(**event, barrier.before)
                                })
                            {
                                let label = match barrier_target {
                                    AccessTarget::Texture { texture, .. } => {
                                        self.textures.get(texture)?.desc.label.as_str()
                                    }
                                    AccessTarget::Buffer { handle, .. } => {
                                        self.buffers.get(handle)?.desc.label.as_str()
                                    }
                                    _ => "frame target",
                                };
                                return self.validation_error(GalError::submission(
                                    StatusCode::InvalidArgument,
                                    format!(
                                        "barrier before {:?} on {:?} ({label}) does not cover prior {:?} {:?} access in {}",
                                        barrier.before, barrier_target, prior.family, prior.mode, list.label
                                    ),
                                ));
                            }
                        }
                        let published: Vec<_> = accesses
                            .bucket(barrier_target)
                            .into_iter()
                            .flat_map(|bucket| bucket.reads.iter().chain(&bucket.writes))
                            .filter(|event| targets_overlap(event.target, barrier_target))
                            .map(|event| intersect_target(event.target, barrier_target))
                            .collect();
                        accesses.retain_non_overlapping(barrier_target);
                        accesses.destinations = accesses
                            .destinations
                            .drain(..)
                            .flat_map(|(target, state)| {
                                subtract_target(target, barrier_target)
                                    .into_iter()
                                    .map(move |target| (target, state))
                            })
                            .collect();
                        if matches!(barrier_target, AccessTarget::Buffer { .. }) {
                            accesses.destinations.extend(
                                published.into_iter().map(|target| (target, barrier.after)),
                            );
                        } else {
                            accesses.destinations.push((barrier_target, barrier.after));
                        }
                        if let Some(profile) = profile.as_deref_mut() {
                            profile.gal_hazard_barriers_applied =
                                profile.gal_hazard_barriers_applied.saturating_add(1);
                        }
                    }
                    CommandOp::BindGraphicsPipeline(pipeline) => {
                        active_layout = Some(self.graphics_pipelines.get(*pipeline)?.desc.layout);
                    }
                    CommandOp::BindComputePipeline(pipeline) => {
                        active_layout = Some(self.compute_pipelines.get(*pipeline)?.desc.layout);
                    }
                    CommandOp::Draw { .. }
                    | CommandOp::DrawIndexed { .. }
                    | CommandOp::Dispatch { .. }
                    | CommandOp::TrackSubmission(_)
                    | CommandOp::EndPass => {}
                }
            }
        }
        if let Some(profile) = profile.as_deref_mut() {
            profile.gal_hazard_active_read_entries = profile
                .gal_hazard_active_read_entries
                .saturating_add(accesses.active_read_entries() as u64);
            profile.gal_hazard_active_write_entries = profile
                .gal_hazard_active_write_entries
                .saturating_add(accesses.active_write_entries() as u64);
        }
        Ok(())
    }

    pub(super) fn resource_binding_access(
        &self,
        binding: &ResourceBinding,
        offset: u64,
    ) -> GalResult<AccessEvent> {
        let mode = if binding.access.writes() {
            AccessMode::Write
        } else {
            AccessMode::Read
        };
        let buffer_size = || -> GalResult<u64> {
            let size = self.buffers.get(binding.resource)?.desc.size;
            Ok(binding.buffer_range.unwrap_or_else(|| {
                let max_default_offset = binding.dynamic_offsets.iter().copied().max().unwrap_or(0);
                size.saturating_sub(max_default_offset)
            }))
        };
        match binding.kind {
            ResourceBindingKind::UniformBuffer => Ok(AccessEvent {
                target: self.buffer_access_target(
                    binding.resource,
                    offset,
                    Some(buffer_size()?),
                )?,
                mode: AccessMode::Read,
                family: AccessFamily::Uniform,
                attachment_load_op: None,
                attachment_store_op: None,
            }),
            ResourceBindingKind::StorageBuffer => Ok(AccessEvent {
                target: self.buffer_access_target(
                    binding.resource,
                    offset,
                    Some(buffer_size()?),
                )?,
                mode,
                family: AccessFamily::Storage,
                attachment_load_op: None,
                attachment_store_op: None,
            }),
            ResourceBindingKind::SampledTexture => Ok(AccessEvent {
                target: self.texture_view_access_target(binding.resource)?,
                mode: AccessMode::Read,
                family: AccessFamily::Sampled,
                attachment_load_op: None,
                attachment_store_op: None,
            }),
            ResourceBindingKind::CombinedTextureSampler => {
                let pair = self.combined_texture_samplers.get(binding.resource)?;
                Ok(AccessEvent {
                    target: self.texture_view_access_target(pair.desc.texture_view)?,
                    mode: AccessMode::Read,
                    family: AccessFamily::Sampled,
                    attachment_load_op: None,
                    attachment_store_op: None,
                })
            }
            ResourceBindingKind::StorageTexture => Ok(AccessEvent {
                target: self.texture_view_access_target(binding.resource)?,
                mode,
                family: AccessFamily::Storage,
                attachment_load_op: None,
                attachment_store_op: None,
            }),
            ResourceBindingKind::Sampler => Ok(AccessEvent {
                target: AccessTarget::Buffer {
                    handle: binding.resource,
                    offset: 0,
                    size: 0,
                },
                mode: AccessMode::Read,
                family: AccessFamily::Sampled,
                attachment_load_op: None,
                attachment_store_op: None,
            }),
        }
    }

    pub(super) fn record_access(
        &mut self,
        accesses: &mut AccessTracker,
        event: AccessEvent,
        mut profile: Option<&mut SubmitProfile>,
    ) -> GalResult<()> {
        if event.mode == AccessMode::Write {
            if let AccessTarget::Buffer {
                handle,
                offset,
                size,
            } = event.target
            {
                self.buffer_upload_capture.write(handle, offset, size);
            }
        }
        if event.target.is_zero_sized_sampler_marker() {
            return Ok(());
        }
        if let Some((target, state)) = accesses.destinations.iter().find(|(target, state)| {
            targets_overlap(*target, event.target) && !access_matches_state(event, *state)
        }) {
            return self.validation_error(GalError::submission(
                StatusCode::InvalidArgument,
                format!("barrier after {state:?} on {target:?} does not cover {event:?}"),
            ));
        }
        accesses.destinations = accesses
            .destinations
            .drain(..)
            .flat_map(|(target, state)| {
                subtract_target(target, event.target)
                    .into_iter()
                    .map(move |target| (target, state))
            })
            .collect();
        if let Some(profile) = profile.as_deref_mut() {
            match event.mode {
                AccessMode::Read => {
                    profile.gal_hazard_read_events =
                        profile.gal_hazard_read_events.saturating_add(1);
                }
                AccessMode::Write => {
                    profile.gal_hazard_write_events =
                        profile.gal_hazard_write_events.saturating_add(1);
                }
            }
        }
        match event.mode {
            AccessMode::Read => {
                if let Some(bucket) = accesses.bucket(event.target) {
                    for previous in bucket.writes.iter().copied() {
                        if let Some(profile) = profile.as_deref_mut() {
                            profile.gal_hazard_candidates_examined =
                                profile.gal_hazard_candidates_examined.saturating_add(1);
                        }
                        if targets_overlap(previous.target, event.target) {
                            if let Some(profile) = profile.as_deref_mut() {
                                profile.gal_hazard_conflicts =
                                    profile.gal_hazard_conflicts.saturating_add(1);
                            }
                            return self.validation_error(GalError::submission(
                                StatusCode::InvalidArgument,
                                format!(
                                    "overlapping {:?} {:?} access on {:?} conflicts with prior {:?} {:?} access",
                                    event.family, event.mode, event.target, previous.family, previous.mode
                                ),
                            ));
                        }
                    }
                }
                accesses.push_read(event);
            }
            AccessMode::Write => {
                if let Some(bucket) = accesses.bucket(event.target) {
                    for previous in bucket.writes.iter().copied() {
                        if let Some(profile) = profile.as_deref_mut() {
                            profile.gal_hazard_candidates_examined =
                                profile.gal_hazard_candidates_examined.saturating_add(1);
                        }
                        if targets_overlap(previous.target, event.target) {
                            if matches!(
                                previous.family,
                                AccessFamily::Attachment | AccessFamily::DepthAttachment
                            ) && previous.family == event.family
                            {
                                if event.attachment_load_op == Some(AttachmentLoadOp::Load)
                                    && previous.attachment_store_op
                                        != Some(AttachmentStoreOp::Store)
                                {
                                    if let Some(profile) = profile.as_deref_mut() {
                                        profile.gal_hazard_conflicts =
                                            profile.gal_hazard_conflicts.saturating_add(1);
                                    }
                                    return self.validation_error(GalError::submission(
                                        StatusCode::InvalidArgument,
                                        "attachment load depends on a prior pass that did not store",
                                    ));
                                }
                                continue;
                            }
                            if let Some(profile) = profile.as_deref_mut() {
                                profile.gal_hazard_conflicts =
                                    profile.gal_hazard_conflicts.saturating_add(1);
                            }
                            return self.validation_error(GalError::submission(
                                StatusCode::InvalidArgument,
                                format!(
                                "overlapping {:?} {:?} access on {:?} conflicts with prior {:?} {:?} access",
                                    event.family, event.mode, event.target, previous.family, previous.mode
                                ),
                            ));
                        }
                    }
                    for previous in bucket.reads.iter().copied() {
                        if let Some(profile) = profile.as_deref_mut() {
                            profile.gal_hazard_candidates_examined =
                                profile.gal_hazard_candidates_examined.saturating_add(1);
                        }
                        if targets_overlap(previous.target, event.target) {
                            if let Some(profile) = profile.as_deref_mut() {
                                profile.gal_hazard_conflicts =
                                    profile.gal_hazard_conflicts.saturating_add(1);
                            }
                            return self.validation_error(GalError::submission(
                                StatusCode::InvalidArgument,
                                format!(
                                    "overlapping {:?} {:?} access on {:?} conflicts with prior {:?} {:?} access",
                                    event.family, event.mode, event.target, previous.family, previous.mode
                                ),
                            ));
                        }
                    }
                }
                accesses.push_write(event);
            }
        }
        Ok(())
    }

    pub(super) fn buffer_access_target(
        &self,
        buffer: Handle,
        offset: u64,
        size: Option<u64>,
    ) -> GalResult<AccessTarget> {
        let record = self.buffers.get(buffer)?;
        let size = size.unwrap_or_else(|| record.desc.size.saturating_sub(offset));
        Ok(AccessTarget::Buffer {
            handle: buffer,
            offset,
            size,
        })
    }

    pub(super) fn barrier_target(&self, barrier: &ResourceBarrier) -> GalResult<AccessTarget> {
        match barrier.resource.kind() {
            Some(HandleKind::Buffer) => self.buffer_access_target(barrier.resource, 0, None),
            Some(HandleKind::Texture) => {
                let record = self.textures.get(barrier.resource)?;
                Ok(AccessTarget::Texture {
                    texture: barrier.resource,
                    range: barrier.subresources.unwrap_or(TextureSubresourceRange {
                        base_mip: 0,
                        mip_count: record.desc.mip_levels,
                        base_layer: 0,
                        layer_count: record.desc.array_layers,
                    }),
                })
            }
            Some(HandleKind::TextureView) => {
                let AccessTarget::Texture { texture, range } =
                    self.texture_view_access_target(barrier.resource)?
                else {
                    unreachable!("texture view access target is always a texture")
                };
                Ok(AccessTarget::Texture {
                    texture,
                    range: barrier.subresources.unwrap_or(range),
                })
            }
            _ => Err(GalError::command(
                StatusCode::InvalidArgument,
                "barrier resource must be a buffer, texture, or texture view",
            )),
        }
    }

    pub(super) fn texture_view_access_target(&self, view: Handle) -> GalResult<AccessTarget> {
        let view_record = self.texture_views.get(view)?;
        Ok(AccessTarget::Texture {
            texture: view_record.desc.texture,
            range: TextureSubresourceRange {
                base_mip: view_record.desc.base_mip,
                mip_count: view_record.desc.mip_count,
                base_layer: view_record.desc.base_layer,
                layer_count: view_record.desc.layer_count,
            },
        })
    }

    pub(super) fn texture_copy_target(
        &self,
        region: &BufferImageCopyRegion,
    ) -> GalResult<AccessTarget> {
        self.texture_image_copy_target(region.texture, region.texture_mip, region.texture_layer)
    }

    pub(super) fn texture_image_copy_target(
        &self,
        texture: Handle,
        mip: u32,
        layer: u32,
    ) -> GalResult<AccessTarget> {
        self.textures.get(texture)?;
        Ok(AccessTarget::Texture {
            texture,
            range: TextureSubresourceRange {
                base_mip: mip,
                mip_count: 1,
                base_layer: layer,
                layer_count: 1,
            },
        })
    }
}

impl AccessTarget {
    pub(super) fn is_zero_sized_sampler_marker(self) -> bool {
        matches!(self, AccessTarget::Buffer { size: 0, .. })
    }

    pub(super) fn resource_key(self) -> AccessResourceKey {
        match self {
            AccessTarget::Buffer { handle, .. } => AccessResourceKey::Buffer(handle),
            AccessTarget::Texture { texture, .. } => AccessResourceKey::Texture(texture),
            AccessTarget::FrameTarget { handle } => AccessResourceKey::FrameTarget(handle),
        }
    }
}

pub(super) fn targets_overlap(left: AccessTarget, right: AccessTarget) -> bool {
    match (left, right) {
        (
            AccessTarget::Buffer {
                handle: left_handle,
                offset: left_offset,
                size: left_size,
            },
            AccessTarget::Buffer {
                handle: right_handle,
                offset: right_offset,
                size: right_size,
            },
        ) => {
            if left_handle != right_handle || left_size == 0 || right_size == 0 {
                return false;
            }
            ranges_overlap(left_offset, left_size, right_offset, right_size)
        }
        (
            AccessTarget::Texture {
                texture: left_texture,
                range: left_range,
            },
            AccessTarget::Texture {
                texture: right_texture,
                range: right_range,
            },
        ) => left_texture == right_texture && texture_ranges_overlap(left_range, right_range),
        (
            AccessTarget::FrameTarget {
                handle: left_handle,
            },
            AccessTarget::FrameTarget {
                handle: right_handle,
            },
        ) => left_handle == right_handle,
        _ => false,
    }
}

pub(super) fn ranges_overlap(
    left_offset: u64,
    left_size: u64,
    right_offset: u64,
    right_size: u64,
) -> bool {
    let left_end = left_offset.saturating_add(left_size);
    let right_end = right_offset.saturating_add(right_size);
    left_offset < right_end && right_offset < left_end
}

pub(super) fn texture_ranges_overlap(
    left: TextureSubresourceRange,
    right: TextureSubresourceRange,
) -> bool {
    ranges_overlap(
        left.base_mip as u64,
        left.mip_count as u64,
        right.base_mip as u64,
        right.mip_count as u64,
    ) && ranges_overlap(
        left.base_layer as u64,
        left.layer_count as u64,
        right.base_layer as u64,
        right.layer_count as u64,
    )
}

pub(super) fn texture_range_contains(
    outer: TextureSubresourceRange,
    inner: TextureSubresourceRange,
) -> bool {
    let outer_mip_end = outer.base_mip.saturating_add(outer.mip_count);
    let inner_mip_end = inner.base_mip.saturating_add(inner.mip_count);
    let outer_layer_end = outer.base_layer.saturating_add(outer.layer_count);
    let inner_layer_end = inner.base_layer.saturating_add(inner.layer_count);
    outer.base_mip <= inner.base_mip
        && inner_mip_end <= outer_mip_end
        && outer.base_layer <= inner.base_layer
        && inner_layer_end <= outer_layer_end
}

fn access_matches_state(event: AccessEvent, state: TextureUsageState) -> bool {
    use AccessFamily::*;
    use TextureUsageState::*;
    // HostReadBuffer executes only after the submission timeline has completed.
    if event.family == Host && event.mode == AccessMode::Read {
        return true;
    }
    match state {
        Undefined => false,
        ShaderRead => {
            event.mode == AccessMode::Read
                && matches!(event.family, Sampled | Uniform | Storage | Vertex)
        }
        ShaderStorageRead => event.mode == AccessMode::Read && event.family == Storage,
        ShaderWrite => event.family == Storage,
        ColorAttachment => event.family == Attachment,
        DepthStencilAttachment => event.family == DepthAttachment,
        TransferSrc => {
            (event.mode == AccessMode::Read && matches!(event.family, Transfer | Host))
                || event.family == MipGeneration
        }
        TransferDst => {
            event.mode == AccessMode::Write
                && matches!(event.family, Transfer | Host | MipGeneration)
        }
        TextureUsageState::Present => event.family == AccessFamily::Present,
        IndexRead => event.family == Index,
        IndirectRead => event.family == Indirect,
    }
}

fn intersect_target(target: AccessTarget, cover: AccessTarget) -> AccessTarget {
    match (target, cover) {
        (AccessTarget::Texture { texture, range: a }, AccessTarget::Texture { range: b, .. }) => {
            let mip = a.base_mip.max(b.base_mip);
            let layer = a.base_layer.max(b.base_layer);
            AccessTarget::Texture {
                texture,
                range: TextureSubresourceRange {
                    base_mip: mip,
                    mip_count: (a.base_mip + a.mip_count).min(b.base_mip + b.mip_count) - mip,
                    base_layer: layer,
                    layer_count: (a.base_layer + a.layer_count).min(b.base_layer + b.layer_count)
                        - layer,
                },
            }
        }
        _ => target,
    }
}

// A barrier on one mip/layer must not erase an access to the rest of a view.
fn subtract_target(target: AccessTarget, cover: AccessTarget) -> Vec<AccessTarget> {
    if !targets_overlap(target, cover) {
        return vec![target];
    }
    match (target, intersect_target(target, cover)) {
        (AccessTarget::Texture { texture, range: a }, AccessTarget::Texture { range: b, .. }) => {
            let mut result = Vec::new();
            let mut push = |mip, mips, layer, layers| {
                if mips > 0 && layers > 0 {
                    result.push(AccessTarget::Texture {
                        texture,
                        range: TextureSubresourceRange {
                            base_mip: mip,
                            mip_count: mips,
                            base_layer: layer,
                            layer_count: layers,
                        },
                    });
                }
            };
            push(
                a.base_mip,
                b.base_mip - a.base_mip,
                a.base_layer,
                a.layer_count,
            );
            push(
                b.base_mip + b.mip_count,
                a.base_mip + a.mip_count - b.base_mip - b.mip_count,
                a.base_layer,
                a.layer_count,
            );
            push(
                b.base_mip,
                b.mip_count,
                a.base_layer,
                b.base_layer - a.base_layer,
            );
            push(
                b.base_mip,
                b.mip_count,
                b.base_layer + b.layer_count,
                a.base_layer + a.layer_count - b.base_layer - b.layer_count,
            );
            result
        }
        (
            AccessTarget::Buffer {
                handle,
                offset,
                size,
            },
            AccessTarget::Buffer { .. },
        ) => {
            let AccessTarget::Buffer {
                offset: start,
                size: count,
                ..
            } = cover
            else {
                return Vec::new();
            };
            let end = offset + size;
            let cut_start = start.max(offset);
            let cut_end = (start + count).min(end);
            let mut result = Vec::new();
            if cut_start > offset {
                result.push(AccessTarget::Buffer {
                    handle,
                    offset,
                    size: cut_start - offset,
                });
            }
            if cut_end < end {
                result.push(AccessTarget::Buffer {
                    handle,
                    offset: cut_end,
                    size: end - cut_end,
                });
            }
            result
        }
        _ => Vec::new(),
    }
}

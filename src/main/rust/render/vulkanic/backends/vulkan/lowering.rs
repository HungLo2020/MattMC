use std::collections::{BTreeMap, VecDeque};
use std::sync::Arc;

use ash::vk;
use ash::vk::Handle as _;

use super::device::VulkanContext;
use super::resources::VulkanObjects;
use super::trace;
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, BufferImageCopyRegion, CommandOp, TextureUsageState,
    ValidatedSubmissionBatch,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::sync::SubmissionId;

pub(super) struct SubmissionLowerer {
    context: Arc<VulkanContext>,
    pending: VecDeque<EncodedSubmission>,
    in_flight: VecDeque<InFlightSubmission>,
    completed: SubmissionId,
    completed_host_reads: Vec<CompletedHostRead>,
}

impl SubmissionLowerer {
    pub(super) fn new(context: Arc<VulkanContext>) -> Self {
        Self {
            context,
            pending: VecDeque::new(),
            in_flight: VecDeque::new(),
            completed: SubmissionId(0),
            completed_host_reads: Vec::new(),
        }
    }

    pub(super) fn encode(
        &mut self,
        objects: &VulkanObjects,
        batch: &ValidatedSubmissionBatch,
    ) -> GalResult<()> {
        let command_buffer = self.allocate_command_buffer()?;
        let _zone = trace::Zone::new("vulkan.lowering.command-recording");
        let begin_info = vk::CommandBufferBeginInfo::default()
            .flags(vk::CommandBufferUsageFlags::ONE_TIME_SUBMIT);
        unsafe {
            self.context
                .device
                .begin_command_buffer(command_buffer, &begin_info)
        }
        .map_err(|error| {
            GalError::backend(format!("failed to begin Vulkan command buffer: {error:?}"))
        })?;

        unsafe {
            self.context.begin_label(
                command_buffer,
                &format!("gal.batch.{}", sanitize_label(&batch.label)),
            );
        }
        let mut state = EncodingState::default();
        for list in &batch.command_lists {
            unsafe {
                self.context.begin_label(
                    command_buffer,
                    &format!("gal.command-list.{}", sanitize_label(&list.label)),
                );
            }
            for op in &list.operations {
                self.encode_op(objects, command_buffer, &mut state, op)?;
            }
            unsafe { self.context.end_label(command_buffer) };
        }
        self.transition_pending_frame_targets_to_present(command_buffer, &mut state);
        unsafe { self.context.end_label(command_buffer) };
        unsafe { self.context.device.end_command_buffer(command_buffer) }.map_err(|error| {
            GalError::backend(format!("failed to end Vulkan command buffer: {error:?}"))
        })?;
        self.pending.push_back(EncodedSubmission {
            command_buffer,
            host_reads: state.host_reads,
        });
        Ok(())
    }

    pub(super) fn submit(&mut self, id: SubmissionId) -> GalResult<()> {
        let Some(encoded) = self.pending.pop_front() else {
            return Err(GalError::backend(
                "Vulkan submit called without encoded commands",
            ));
        };
        let _zone = trace::Zone::new("vulkan.backend.queue-submit");
        let command_buffer_info =
            [vk::CommandBufferSubmitInfo::default().command_buffer(encoded.command_buffer)];
        let signal_info = [vk::SemaphoreSubmitInfo::default()
            .semaphore(self.context.timeline)
            .value(id.0)
            .stage_mask(vk::PipelineStageFlags2::ALL_COMMANDS)];
        let submit = vk::SubmitInfo2::default()
            .command_buffer_infos(&command_buffer_info)
            .signal_semaphore_infos(&signal_info);
        unsafe {
            self.context
                .device
                .queue_submit2(self.context.queue, &[submit], vk::Fence::null())
        }
        .map_err(|error| GalError::backend(format!("Vulkan queue submit failed: {error:?}")))?;
        self.in_flight.push_back(InFlightSubmission {
            id,
            command_buffer: encoded.command_buffer,
            host_reads: encoded.host_reads,
        });
        Ok(())
    }

    pub(super) fn completed_submission(&mut self) -> SubmissionId {
        if let Ok(value) = unsafe {
            self.context
                .device
                .get_semaphore_counter_value(self.context.timeline)
        } {
            self.completed = SubmissionId(value);
        }
        while let Some(front) = self.in_flight.front() {
            if front.id > self.completed {
                break;
            }
            let complete = self.in_flight.pop_front().expect("front existed");
            self.complete_host_reads(&complete);
            unsafe {
                self.context
                    .device
                    .free_command_buffers(self.context.command_pool, &[complete.command_buffer]);
            }
        }
        self.completed
    }

    pub(super) fn retire(&mut self, completed: SubmissionId) -> GalResult<()> {
        while let Some(front) = self.in_flight.front() {
            if front.id > completed {
                break;
            }
            let complete = self.in_flight.pop_front().expect("front existed");
            let _zone = trace::Zone::new("vulkan.backend.wait-timeline");
            wait_timeline(&self.context, complete.id)?;
            unsafe {
                self.context
                    .device
                    .free_command_buffers(self.context.command_pool, &[complete.command_buffer]);
            }
            self.complete_host_reads(&complete);
            self.completed = complete.id;
        }
        Ok(())
    }

    pub(super) fn completed_host_reads_snapshot(&self) -> &[CompletedHostRead] {
        &self.completed_host_reads
    }

    pub(super) fn wait_idle_and_clear(&mut self) {
        let _ = self.context.wait_idle();
        for encoded in self.pending.drain(..) {
            unsafe {
                self.context
                    .device
                    .free_command_buffers(self.context.command_pool, &[encoded.command_buffer]);
            }
        }
        for complete in self.in_flight.drain(..) {
            unsafe {
                self.context
                    .device
                    .free_command_buffers(self.context.command_pool, &[complete.command_buffer]);
            }
        }
    }

    fn allocate_command_buffer(&self) -> GalResult<vk::CommandBuffer> {
        let allocate_info = vk::CommandBufferAllocateInfo::default()
            .command_pool(self.context.command_pool)
            .level(vk::CommandBufferLevel::PRIMARY)
            .command_buffer_count(1);
        unsafe { self.context.device.allocate_command_buffers(&allocate_info) }
            .map_err(|error| {
                GalError::backend(format!(
                    "failed to allocate Vulkan command buffer: {error:?}"
                ))
            })?
            .into_iter()
            .next()
            .ok_or_else(|| GalError::backend("Vulkan returned no command buffer"))
    }

    fn encode_op(
        &self,
        objects: &VulkanObjects,
        command_buffer: vk::CommandBuffer,
        state: &mut EncodingState,
        op: &CommandOp,
    ) -> GalResult<()> {
        unsafe {
            match op {
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors,
                    depth_stencil,
                } => {
                    let pass_object = objects.render_pass(*pass)?;
                    self.context
                        .begin_label(command_buffer, &format!("gal.pass.0x{:016x}", pass.raw()));
                    if pass_object.target != *target {
                        return Err(GalError::backend(
                            "render pass target mismatch during lowering",
                        ));
                    }
                    let (color_attachments, depth_attachment, extent, frame_present) = if target
                        .kind()
                        == Some(HandleKind::FrameTarget)
                    {
                        let frame = objects.frame_target(*target)?;
                        let clear_frame = !state.frame_target_touched;
                        let old_layout = state
                            .frame_target_layouts
                            .get(target)
                            .copied()
                            .unwrap_or(frame.image_layout);
                        let range = vk::ImageSubresourceRange {
                            aspect_mask: vk::ImageAspectFlags::COLOR,
                            base_mip_level: 0,
                            level_count: 1,
                            base_array_layer: 0,
                            layer_count: 1,
                        };
                        let to_attachment = vk::ImageMemoryBarrier2::default()
                            .src_stage_mask(
                                if old_layout == vk::ImageLayout::PRESENT_SRC_KHR {
                                    vk::PipelineStageFlags2::BOTTOM_OF_PIPE
                                } else {
                                    vk::PipelineStageFlags2::COLOR_ATTACHMENT_OUTPUT
                                },
                            )
                            .src_access_mask(
                                if old_layout == vk::ImageLayout::PRESENT_SRC_KHR {
                                    vk::AccessFlags2::empty()
                                } else {
                                    vk::AccessFlags2::COLOR_ATTACHMENT_READ
                                        | vk::AccessFlags2::COLOR_ATTACHMENT_WRITE
                                },
                            )
                            .dst_stage_mask(vk::PipelineStageFlags2::COLOR_ATTACHMENT_OUTPUT)
                            .dst_access_mask(
                                vk::AccessFlags2::COLOR_ATTACHMENT_READ
                                    | vk::AccessFlags2::COLOR_ATTACHMENT_WRITE,
                            )
                            .old_layout(old_layout)
                            .new_layout(vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL)
                            .image(frame.image)
                            .subresource_range(range);
                        self.context.device.cmd_pipeline_barrier2(
                            command_buffer,
                            &vk::DependencyInfo::default()
                                .image_memory_barriers(std::slice::from_ref(&to_attachment)),
                        );
                        state
                            .frame_target_layouts
                            .insert(*target, vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL);
                        let clear = colors
                            .first()
                            .and_then(|attachment| attachment.clear_color)
                            .map(|color| [color.r, color.g, color.b, color.a])
                            .unwrap_or([0.08, 0.31, 0.74, 1.0]);
                        let load = if clear_frame {
                            vk::AttachmentLoadOp::CLEAR
                        } else if colors.is_empty() {
                            vk::AttachmentLoadOp::LOAD
                        } else {
                            load_op(colors[0].load_op)
                        };
                        trace::message(&format!(
                                "gal.frame.target.begin backend=vulkan frame={} image={} view=0x{:016x} extent={}x{} layout={} load={} clear={:.3},{:.3},{:.3},{:.3}",
                                frame.frame_id,
                                frame.image_index,
                                frame.image_view.as_raw(),
                                frame.extent.width,
                                frame.extent.height,
                                old_layout.as_raw(),
                                load.as_raw(),
                                clear[0],
                                clear[1],
                                clear[2],
                                clear[3]
                            ));
                        state.frame_target_touched = true;
                        let depth_attachment = depth_stencil
                            .as_ref()
                            .map(|attachment| {
                                let view = objects.texture_view(attachment.view)?;
                                Ok(vk::RenderingAttachmentInfo::default()
                                    .image_view(view.view)
                                    .image_layout(vk::ImageLayout::DEPTH_ATTACHMENT_OPTIMAL)
                                    .load_op(load_op(attachment.load_op))
                                    .store_op(store_op(attachment.store_op))
                                    .clear_value(vk::ClearValue {
                                        depth_stencil: vk::ClearDepthStencilValue {
                                            depth: 1.0,
                                            stencil: 0,
                                        },
                                    }))
                            })
                            .transpose()?;
                        (
                            vec![vk::RenderingAttachmentInfo::default()
                                .image_view(frame.image_view)
                                .image_layout(vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL)
                                .load_op(load)
                                .store_op(if colors.is_empty() {
                                    vk::AttachmentStoreOp::STORE
                                } else {
                                    store_op(colors[0].store_op)
                                })
                                .clear_value(vk::ClearValue {
                                    color: vk::ClearColorValue { float32: clear },
                                })],
                            depth_attachment,
                            frame.extent,
                            Some(FramePresentTransition {
                                target: *target,
                                image: frame.image,
                                image_index: frame.image_index,
                                frame_id: frame.frame_id,
                                range,
                            }),
                        )
                    } else {
                        let target_object = objects.render_target(*target)?;
                        let color_attachments = colors
                            .iter()
                            .map(|attachment| {
                                let view = objects.texture_view(attachment.view)?;
                                Ok(vk::RenderingAttachmentInfo::default()
                                    .image_view(view.view)
                                    .image_layout(vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL)
                                    .load_op(load_op(attachment.load_op))
                                    .store_op(store_op(attachment.store_op))
                                    .clear_value(vk::ClearValue {
                                        color: vk::ClearColorValue {
                                            float32: attachment
                                                .clear_color
                                                .map(|color| [color.r, color.g, color.b, color.a])
                                                .unwrap_or([0.0, 0.0, 0.0, 0.0]),
                                        },
                                    }))
                            })
                            .collect::<GalResult<Vec<_>>>()?;
                        let depth_attachment = depth_stencil
                            .as_ref()
                            .map(|attachment| {
                                let view = objects.texture_view(attachment.view)?;
                                Ok(vk::RenderingAttachmentInfo::default()
                                    .image_view(view.view)
                                    .image_layout(vk::ImageLayout::DEPTH_ATTACHMENT_OPTIMAL)
                                    .load_op(load_op(attachment.load_op))
                                    .store_op(store_op(attachment.store_op))
                                    .clear_value(vk::ClearValue {
                                        depth_stencil: vk::ClearDepthStencilValue {
                                            depth: 1.0,
                                            stencil: 0,
                                        },
                                    }))
                            })
                            .transpose()?;
                        (
                            color_attachments,
                            depth_attachment,
                            target_object.extent,
                            None,
                        )
                    };
                    let mut rendering = vk::RenderingInfo::default()
                        .render_area(vk::Rect2D {
                            offset: vk::Offset2D { x: 0, y: 0 },
                            extent: vk::Extent2D {
                                width: extent.width,
                                height: extent.height,
                            },
                        })
                        .layer_count(1)
                        .color_attachments(&color_attachments);
                    if let Some(depth_attachment) = depth_attachment.as_ref() {
                        rendering = rendering.depth_attachment(depth_attachment);
                    }
                    self.context
                        .device
                        .cmd_begin_rendering(command_buffer, &rendering);
                    let viewport = vk::Viewport {
                        x: 0.0,
                        y: extent.height as f32,
                        width: extent.width as f32,
                        height: -(extent.height as f32),
                        min_depth: 0.0,
                        max_depth: 1.0,
                    };
                    let scissor = vk::Rect2D {
                        offset: vk::Offset2D { x: 0, y: 0 },
                        extent: vk::Extent2D {
                            width: extent.width,
                            height: extent.height,
                        },
                    };
                    self.context
                        .device
                        .cmd_set_viewport(command_buffer, 0, &[viewport]);
                    self.context
                        .device
                        .cmd_set_scissor(command_buffer, 0, &[scissor]);
                    state.in_pass = true;
                    state.frame_present = frame_present;
                }
                CommandOp::EndPass => {
                    self.context.device.cmd_end_rendering(command_buffer);
                    if let Some(present) = state.frame_present.take() {
                        state.pending_frame_presents.insert(present.target, present);
                    }
                    self.context.end_label(command_buffer);
                    state.in_pass = false;
                    state.graphics_pipeline = None;
                    state.compute_pipeline = None;
                    state.pipeline_layout = None;
                }
                CommandOp::BindGraphicsPipeline(handle) => {
                    let pipeline = objects.graphics_pipeline(*handle)?;
                    self.context.device.cmd_bind_pipeline(
                        command_buffer,
                        vk::PipelineBindPoint::GRAPHICS,
                        pipeline.pipeline,
                    );
                    state.graphics_pipeline = Some(*handle);
                    state.compute_pipeline = None;
                    state.pipeline_layout = Some(pipeline.layout);
                }
                CommandOp::BindComputePipeline(handle) => {
                    let pipeline = objects.compute_pipeline(*handle)?;
                    self.context.device.cmd_bind_pipeline(
                        command_buffer,
                        vk::PipelineBindPoint::COMPUTE,
                        pipeline.pipeline,
                    );
                    state.compute_pipeline = Some(*handle);
                    state.graphics_pipeline = None;
                    state.pipeline_layout = Some(pipeline.layout);
                }
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set,
                    ..
                } => {
                    let layout = objects.pipeline_layout(*pipeline_layout)?;
                    let set = objects.resource_set(*set)?;
                    let bind_point = if state.graphics_pipeline.is_some() {
                        vk::PipelineBindPoint::GRAPHICS
                    } else {
                        vk::PipelineBindPoint::COMPUTE
                    };
                    self.context.device.cmd_bind_descriptor_sets(
                        command_buffer,
                        bind_point,
                        layout.layout,
                        0,
                        &[set.set],
                        &set.dynamic_offsets,
                    );
                }
                CommandOp::SetVertexBuffer {
                    slot,
                    buffer,
                    offset,
                } => {
                    let buffer = objects.buffer(*buffer)?;
                    self.context.device.cmd_bind_vertex_buffers(
                        command_buffer,
                        *slot,
                        &[buffer.buffer],
                        &[*offset],
                    );
                }
                CommandOp::SetIndexBuffer { buffer, offset } => {
                    let buffer = objects.buffer(*buffer)?;
                    self.context.device.cmd_bind_index_buffer(
                        command_buffer,
                        buffer.buffer,
                        *offset,
                        vk::IndexType::UINT32,
                    );
                }
                CommandOp::Draw {
                    vertices,
                    instances,
                } => {
                    self.context
                        .device
                        .cmd_draw(command_buffer, *vertices, *instances, 0, 0);
                }
                CommandOp::DrawIndexed { indices, instances } => {
                    self.context.device.cmd_draw_indexed(
                        command_buffer,
                        *indices,
                        *instances,
                        0,
                        0,
                        0,
                    );
                }
                CommandOp::DrawIndirect {
                    buffer,
                    offset,
                    draw_count,
                } => {
                    let buffer = objects.buffer(*buffer)?;
                    self.context.device.cmd_draw_indirect(
                        command_buffer,
                        buffer.buffer,
                        *offset,
                        *draw_count,
                        std::mem::size_of::<vk::DrawIndirectCommand>() as u32,
                    );
                }
                CommandOp::Dispatch {
                    groups_x,
                    groups_y,
                    groups_z,
                } => {
                    self.context.device.cmd_dispatch(
                        command_buffer,
                        *groups_x,
                        *groups_y,
                        *groups_z,
                    );
                }
                CommandOp::DispatchIndirect { buffer, offset } => {
                    let buffer = objects.buffer(*buffer)?;
                    self.context.device.cmd_dispatch_indirect(
                        command_buffer,
                        buffer.buffer,
                        *offset,
                    );
                }
                CommandOp::CopyBuffer { src, dst, size } => {
                    let _zone = trace::Zone::new("vulkan.lowering.copy-buffer");
                    let src = objects.buffer(*src)?;
                    let dst = objects.buffer(*dst)?;
                    let region = vk::BufferCopy {
                        src_offset: 0,
                        dst_offset: 0,
                        size: *size,
                    };
                    self.context.device.cmd_copy_buffer(
                        command_buffer,
                        src.buffer,
                        dst.buffer,
                        &[region],
                    );
                }
                CommandOp::CopyBufferToTexture(region) => {
                    let _zone = trace::Zone::new("vulkan.lowering.copy-buffer-to-texture");
                    let buffer = objects.buffer(region.buffer)?;
                    let texture = objects.texture(region.texture)?;
                    let copy = buffer_image_copy(region);
                    self.context.device.cmd_copy_buffer_to_image(
                        command_buffer,
                        buffer.buffer,
                        texture.image,
                        vk::ImageLayout::TRANSFER_DST_OPTIMAL,
                        &[copy],
                    );
                }
                CommandOp::CopyTextureToBuffer(region) => {
                    let _zone = trace::Zone::new("vulkan.lowering.copy-texture-to-buffer");
                    let buffer = objects.buffer(region.buffer)?;
                    let texture = objects.texture(region.texture)?;
                    let copy = buffer_image_copy(region);
                    self.context.device.cmd_copy_image_to_buffer(
                        command_buffer,
                        texture.image,
                        vk::ImageLayout::TRANSFER_SRC_OPTIMAL,
                        buffer.buffer,
                        &[copy],
                    );
                }
                CommandOp::Barrier(barrier) => {
                    let _zone = trace::Zone::new("vulkan.lowering.barrier");
                    if barrier.resource.kind()
                        == Some(crate::render::vulkanic::handles::HandleKind::Texture)
                    {
                        let texture = objects.texture(barrier.resource)?;
                        let range = barrier.subresources.unwrap_or(
                            crate::render::vulkanic::resources::TextureSubresourceRange {
                                base_mip: 0,
                                mip_count: texture.mip_levels,
                                base_layer: 0,
                                layer_count: texture.array_layers,
                            },
                        );
                        let image_barrier = vk::ImageMemoryBarrier2::default()
                            .src_stage_mask(stage_mask(barrier.before))
                            .src_access_mask(access_mask(barrier.before))
                            .dst_stage_mask(stage_mask(barrier.after))
                            .dst_access_mask(access_mask(barrier.after))
                            .old_layout(image_layout(barrier.before))
                            .new_layout(image_layout(barrier.after))
                            .image(texture.image)
                            .subresource_range(vk::ImageSubresourceRange {
                                aspect_mask: texture.aspect,
                                base_mip_level: range.base_mip,
                                level_count: range.mip_count,
                                base_array_layer: range.base_layer,
                                layer_count: range.layer_count,
                            });
                        let dependency = vk::DependencyInfo::default()
                            .image_memory_barriers(std::slice::from_ref(&image_barrier));
                        self.context
                            .device
                            .cmd_pipeline_barrier2(command_buffer, &dependency);
                    } else if barrier.resource.kind()
                        == Some(crate::render::vulkanic::handles::HandleKind::Buffer)
                    {
                        let buffer = objects.buffer(barrier.resource)?;
                        let buffer_barrier = vk::BufferMemoryBarrier2::default()
                            .src_stage_mask(stage_mask(barrier.before))
                            .src_access_mask(access_mask(barrier.before))
                            .dst_stage_mask(stage_mask(barrier.after))
                            .dst_access_mask(access_mask(barrier.after))
                            .buffer(buffer.buffer)
                            .offset(0)
                            .size(buffer.size);
                        let dependency = vk::DependencyInfo::default()
                            .buffer_memory_barriers(std::slice::from_ref(&buffer_barrier));
                        self.context
                            .device
                            .cmd_pipeline_barrier2(command_buffer, &dependency);
                    }
                }
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset,
                    data,
                } => {
                    let _zone = trace::Zone::new("vulkan.lowering.host-write");
                    let buffer = objects.buffer(*buffer)?;
                    if !data.is_empty()
                        && *offset % 4 == 0
                        && data.len() % 4 == 0
                        && data.len() <= 65_536
                    {
                        self.context.device.cmd_update_buffer(
                            command_buffer,
                            buffer.buffer,
                            *offset,
                            data,
                        );
                    } else {
                        self.context
                            .write_mapped_memory(buffer.memory, *offset, data)?;
                    }
                }
                CommandOp::HostReadBuffer {
                    buffer,
                    offset,
                    size,
                } => {
                    let _zone = trace::Zone::new("vulkan.lowering.host-read-schedule");
                    let buffer_object = objects.buffer(*buffer)?;
                    state.host_reads.push(HostReadRequest {
                        buffer: *buffer,
                        memory: buffer_object.memory,
                        offset: *offset,
                        size: *size,
                    });
                }
                CommandOp::Present { .. } => {}
            }
        }
        Ok(())
    }

    fn transition_pending_frame_targets_to_present(
        &self,
        command_buffer: vk::CommandBuffer,
        state: &mut EncodingState,
    ) {
        for (target, present) in std::mem::take(&mut state.pending_frame_presents) {
            let old_layout = state
                .frame_target_layouts
                .get(&target)
                .copied()
                .unwrap_or(vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL);
            let to_present = vk::ImageMemoryBarrier2::default()
                .src_stage_mask(vk::PipelineStageFlags2::COLOR_ATTACHMENT_OUTPUT)
                .src_access_mask(
                    vk::AccessFlags2::COLOR_ATTACHMENT_READ
                        | vk::AccessFlags2::COLOR_ATTACHMENT_WRITE,
                )
                .dst_stage_mask(vk::PipelineStageFlags2::BOTTOM_OF_PIPE)
                .old_layout(old_layout)
                .new_layout(vk::ImageLayout::PRESENT_SRC_KHR)
                .image(present.image)
                .subresource_range(present.range);
            unsafe {
                self.context.device.cmd_pipeline_barrier2(
                    command_buffer,
                    &vk::DependencyInfo::default()
                        .image_memory_barriers(std::slice::from_ref(&to_present)),
                );
            }
            state
                .frame_target_layouts
                .insert(target, vk::ImageLayout::PRESENT_SRC_KHR);
            trace::message(&format!(
                "gal.frame.target.present-ready backend=vulkan frame={} image={}",
                present.frame_id, present.image_index
            ));
        }
    }

    fn complete_host_reads(&mut self, complete: &InFlightSubmission) {
        let _zone = trace::Zone::new("vulkan.backend.host-readback");
        for request in &complete.host_reads {
            match self
                .context
                .read_mapped_memory(request.memory, request.offset, request.size)
            {
                Ok(bytes) => self.completed_host_reads.push(CompletedHostRead {
                    submission: complete.id,
                    buffer: request.buffer,
                    offset: request.offset,
                    bytes,
                }),
                Err(error) => self.completed_host_reads.push(CompletedHostRead {
                    submission: complete.id,
                    buffer: request.buffer,
                    offset: request.offset,
                    bytes: error.to_string().into_bytes(),
                }),
            }
        }
    }
}

fn sanitize_label(label: &str) -> String {
    label
        .chars()
        .map(|ch| {
            if ch.is_ascii_alphanumeric() || ch == '-' || ch == '_' || ch == '.' {
                ch
            } else {
                '_'
            }
        })
        .collect()
}

impl Drop for SubmissionLowerer {
    fn drop(&mut self) {
        self.wait_idle_and_clear();
    }
}

#[derive(Default)]
struct EncodingState {
    in_pass: bool,
    graphics_pipeline: Option<crate::render::vulkanic::handles::Handle>,
    compute_pipeline: Option<crate::render::vulkanic::handles::Handle>,
    pipeline_layout: Option<crate::render::vulkanic::handles::Handle>,
    frame_target_touched: bool,
    frame_present: Option<FramePresentTransition>,
    frame_target_layouts: BTreeMap<Handle, vk::ImageLayout>,
    pending_frame_presents: BTreeMap<Handle, FramePresentTransition>,
    host_reads: Vec<HostReadRequest>,
}

struct FramePresentTransition {
    target: Handle,
    image: vk::Image,
    image_index: u32,
    frame_id: u64,
    range: vk::ImageSubresourceRange,
}

struct EncodedSubmission {
    command_buffer: vk::CommandBuffer,
    host_reads: Vec<HostReadRequest>,
}

struct InFlightSubmission {
    id: SubmissionId,
    command_buffer: vk::CommandBuffer,
    host_reads: Vec<HostReadRequest>,
}

#[derive(Clone, Debug)]
struct HostReadRequest {
    buffer: Handle,
    memory: vk::DeviceMemory,
    offset: u64,
    size: u64,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::vulkanic) struct CompletedHostRead {
    pub(super) submission: SubmissionId,
    pub(super) buffer: Handle,
    pub(super) offset: u64,
    pub(super) bytes: Vec<u8>,
}

fn load_op(op: AttachmentLoadOp) -> vk::AttachmentLoadOp {
    match op {
        AttachmentLoadOp::Load => vk::AttachmentLoadOp::LOAD,
        AttachmentLoadOp::Clear => vk::AttachmentLoadOp::CLEAR,
        AttachmentLoadOp::DontCare => vk::AttachmentLoadOp::DONT_CARE,
    }
}

fn store_op(op: AttachmentStoreOp) -> vk::AttachmentStoreOp {
    match op {
        AttachmentStoreOp::Store => vk::AttachmentStoreOp::STORE,
        AttachmentStoreOp::DontCare => vk::AttachmentStoreOp::DONT_CARE,
    }
}

pub(super) fn image_layout(state: TextureUsageState) -> vk::ImageLayout {
    match state {
        TextureUsageState::Undefined => vk::ImageLayout::UNDEFINED,
        TextureUsageState::ShaderRead => vk::ImageLayout::SHADER_READ_ONLY_OPTIMAL,
        TextureUsageState::ShaderWrite => vk::ImageLayout::GENERAL,
        TextureUsageState::ColorAttachment => vk::ImageLayout::COLOR_ATTACHMENT_OPTIMAL,
        TextureUsageState::DepthStencilAttachment => vk::ImageLayout::DEPTH_ATTACHMENT_OPTIMAL,
        TextureUsageState::TransferSrc => vk::ImageLayout::TRANSFER_SRC_OPTIMAL,
        TextureUsageState::TransferDst => vk::ImageLayout::TRANSFER_DST_OPTIMAL,
        TextureUsageState::Present => vk::ImageLayout::PRESENT_SRC_KHR,
        TextureUsageState::IndexRead => vk::ImageLayout::UNDEFINED,
    }
}

pub(super) fn stage_mask(state: TextureUsageState) -> vk::PipelineStageFlags2 {
    match state {
        TextureUsageState::Undefined => vk::PipelineStageFlags2::TOP_OF_PIPE,
        TextureUsageState::ShaderRead | TextureUsageState::ShaderWrite => {
            vk::PipelineStageFlags2::VERTEX_SHADER | vk::PipelineStageFlags2::FRAGMENT_SHADER
        }
        TextureUsageState::ColorAttachment => vk::PipelineStageFlags2::COLOR_ATTACHMENT_OUTPUT,
        TextureUsageState::DepthStencilAttachment => {
            vk::PipelineStageFlags2::EARLY_FRAGMENT_TESTS
                | vk::PipelineStageFlags2::LATE_FRAGMENT_TESTS
        }
        TextureUsageState::TransferSrc | TextureUsageState::TransferDst => {
            vk::PipelineStageFlags2::TRANSFER
        }
        TextureUsageState::IndexRead => vk::PipelineStageFlags2::INDEX_INPUT,
        TextureUsageState::Present => vk::PipelineStageFlags2::BOTTOM_OF_PIPE,
    }
}

pub(super) fn access_mask(state: TextureUsageState) -> vk::AccessFlags2 {
    match state {
        TextureUsageState::Undefined | TextureUsageState::Present => vk::AccessFlags2::empty(),
        TextureUsageState::ShaderRead => vk::AccessFlags2::SHADER_SAMPLED_READ,
        TextureUsageState::ShaderWrite => vk::AccessFlags2::SHADER_STORAGE_WRITE,
        TextureUsageState::ColorAttachment => {
            vk::AccessFlags2::COLOR_ATTACHMENT_READ | vk::AccessFlags2::COLOR_ATTACHMENT_WRITE
        }
        TextureUsageState::DepthStencilAttachment => {
            vk::AccessFlags2::DEPTH_STENCIL_ATTACHMENT_READ
                | vk::AccessFlags2::DEPTH_STENCIL_ATTACHMENT_WRITE
        }
        TextureUsageState::TransferSrc => vk::AccessFlags2::TRANSFER_READ,
        TextureUsageState::TransferDst => vk::AccessFlags2::TRANSFER_WRITE,
        TextureUsageState::IndexRead => vk::AccessFlags2::INDEX_READ,
    }
}

fn buffer_image_copy(region: &BufferImageCopyRegion) -> vk::BufferImageCopy {
    vk::BufferImageCopy::default()
        .buffer_offset(region.buffer_offset)
        .buffer_row_length(region.bytes_per_row / 4)
        .buffer_image_height(region.rows_per_image)
        .image_subresource(vk::ImageSubresourceLayers {
            aspect_mask: vk::ImageAspectFlags::COLOR,
            mip_level: region.texture_mip,
            base_array_layer: region.texture_layer,
            layer_count: 1,
        })
        .image_offset(vk::Offset3D {
            x: region.texture_origin.x as i32,
            y: region.texture_origin.y as i32,
            z: region.texture_origin.z as i32,
        })
        .image_extent(vk::Extent3D {
            width: region.extent.width,
            height: region.extent.height,
            depth: region.extent.depth,
        })
}

fn wait_timeline(context: &VulkanContext, id: SubmissionId) -> GalResult<()> {
    let semaphores = [context.timeline];
    let values = [id.0];
    let wait_info = vk::SemaphoreWaitInfo::default()
        .semaphores(&semaphores)
        .values(&values);
    unsafe { context.device.wait_semaphores(&wait_info, 30_000_000_000) }
        .map_err(|error| GalError::backend(format!("Vulkan timeline wait failed: {error:?}")))
}

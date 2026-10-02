//! Validation of recorded command ops and of buffer, texture and frame-target copies.

use super::*;

impl VulkanicGal {
    pub(super) fn validation_error<T>(&mut self, error: GalError) -> GalResult<T> {
        self.metrics.validation_failures += 1;
        Err(error)
    }

    pub(super) fn unsupported<T>(&mut self, message: impl Into<String>) -> GalResult<T> {
        self.validation_error(GalError::unsupported_feature(message))
    }

    pub(super) fn validate_command_ops(&mut self, label: &str, ops: &[CommandOp]) -> GalResult<()> {
        let capabilities = self.capabilities();
        let mut in_pass = false;
        let mut active_pass = None;
        let mut graphics_pipeline = None;
        let mut compute_pipeline = None;
        let mut active_pipeline_layout = None;
        let mut index_buffer = None;
        for (op_index, op) in ops.iter().enumerate() {
            match op {
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors,
                    depth_stencil,
                } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "nested passes are invalid",
                        ));
                    }
                    let pass_target = self.render_pass_desc(*pass)?.target;
                    let frame_target = match target.kind() {
                        Some(HandleKind::FrameTarget) => {
                            self.frame_targets.get(*target)?;
                            true
                        }
                        Some(HandleKind::RenderTarget) => false,
                        _ => {
                            return self.validation_error(GalError::command(
                                StatusCode::WrongHandleType,
                                "pass target must be a render target or frame target",
                            ))
                        }
                    };
                    let (expected_colors, expected_depth) = if frame_target {
                        let pass_desc = self.render_pass_desc(*pass)?;
                        let expected_colors = if pass_desc.color_formats.is_empty() {
                            Vec::new()
                        } else {
                            vec![*target]
                        };
                        let pass_depth = pass_desc.depth_format;
                        if let Some(format) = pass_depth {
                            let attachment = depth_stencil.as_ref().ok_or_else(|| {
                                GalError::command(
                                    StatusCode::InvalidArgument,
                                    format!(
                                        "frame-target pass '{}' with depth format requires a depth attachment",
                                        pass_desc.label
                                    ),
                                )
                            })?;
                            let info = self.texture_view_info(attachment.view)?;
                            if info.format != format {
                                return self.validation_error(GalError::command(
                                    StatusCode::InvalidArgument,
                                    "frame-target pass depth attachment format does not match render pass",
                                ));
                            }
                            (expected_colors, Some(attachment.view))
                        } else {
                            (expected_colors, None)
                        }
                    } else {
                        let target_record = self.render_targets.get(*target)?;
                        (
                            target_record.desc.color_views.clone(),
                            if self.render_pass_desc(*pass)?.depth_format.is_some() {
                                target_record.desc.depth_stencil_view
                            } else { None },
                        )
                    };
                    if pass_target != *target {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass target does not match command target",
                        ));
                    }
                    if colors.len() != expected_colors.len() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass color attachment count does not match target",
                        ));
                    }
                    for (index, color) in colors.iter().enumerate() {
                        if color.view != expected_colors[index] {
                            return self.validation_error(GalError::command(
                                StatusCode::InvalidArgument,
                                "pass color attachment view does not match target",
                            ));
                        }
                    }
                    if depth_stencil.is_some() != expected_depth.is_some() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass depth attachment presence does not match target",
                        ));
                    }
                    if let (Some(depth), Some(expected_depth)) = (depth_stencil, expected_depth) {
                        if depth.view != expected_depth {
                            return self.validation_error(GalError::command(
                                StatusCode::InvalidArgument,
                                "pass depth attachment view does not match target",
                            ));
                        }
                    }
                    in_pass = true;
                    active_pass = Some(*pass);
                }
                CommandOp::BindGraphicsPipeline(handle) => {
                    let layout = self.graphics_pipelines.get(*handle)?.desc.layout;
                    if let Some(pass) = active_pass {
                        self.validate_pipeline_pass_compatibility(*handle, pass)?;
                    }
                    graphics_pipeline = Some(*handle);
                    compute_pipeline = None;
                    active_pipeline_layout = Some(layout);
                }
                CommandOp::BindComputePipeline(handle) => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "compute pipeline cannot be bound inside a render pass",
                        ));
                    }
                    let layout = self.compute_pipelines.get(*handle)?.desc.layout;
                    compute_pipeline = Some(*handle);
                    graphics_pipeline = None;
                    active_pipeline_layout = Some(layout);
                }
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index,
                    set,
                    dynamic_offsets,
                } => {
                    if active_pipeline_layout != Some(*pipeline_layout) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource set pipeline layout does not match active pipeline",
                        ));
                    }
                    let layout = self.pipeline_layouts.get(*pipeline_layout)?;
                    let set_record = match self.resource_sets.get(*set) {
                        Ok(record) => record,
                        Err(mut error) => {
                            let replacement = self
                                .resource_sets
                                .slots
                                .get(set.index() as usize)
                                .and_then(|slot| {
                                    slot.value.as_ref().map(|record| (slot.generation, record))
                                })
                                .map(|(generation, record)| {
                                    format!(
                                        "generation={} label='{}'",
                                        generation, record.desc.label
                                    )
                                })
                                .unwrap_or_else(|| "none".to_owned());
                            error.message = format!(
                                "command list '{label}' op {op_index} BindResourceSet set={set:?} live_slot={replacement}: {}",
                                error.message
                            );
                            return Err(error);
                        }
                    };
                    let Some(expected_layout) =
                        layout.desc.resource_layouts.get(*set_index as usize)
                    else {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource set index is outside pipeline layout",
                        ));
                    };
                    if *expected_layout != set_record.desc.layout {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource set layout is not compatible with pipeline layout",
                        ));
                    }
                    let set_bindings = &set_record.desc.bindings;
                    let expected_dynamic_offsets: usize = set_bindings
                        .iter()
                        .map(|binding| binding.dynamic_offsets.len())
                        .sum();
                    if !dynamic_offsets.is_empty()
                        && dynamic_offsets.len() != expected_dynamic_offsets
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            format!(
                                "resource set bind dynamic offset count {} does not match expected {}",
                                dynamic_offsets.len(),
                                expected_dynamic_offsets
                            ),
                        ));
                    }
                    // Borrow the set's bindings; the pure checker needs no
                    // mutable GAL state, so no per-bind clone is required.
                    let mut offset_index = 0usize;
                    let mut range_error = None;
                    for binding in set_bindings {
                        let count = binding.dynamic_offsets.len();
                        if count == 0 {
                            continue;
                        }
                        let offsets = if dynamic_offsets.is_empty() {
                            binding.dynamic_offsets.as_slice()
                        } else {
                            let end = offset_index + count;
                            let slice = &dynamic_offsets[offset_index..end];
                            offset_index = end;
                            slice
                        };
                        if let Err(error) = self.check_resource_binding_buffer_range(binding, offsets) {
                            range_error = Some(error);
                            break;
                        }
                    }
                    if let Some(error) = range_error {
                        return match error.code {
                            StatusCode::InvalidArgument => self.validation_error(error),
                            _ => Err(error),
                        };
                    }
                }
                CommandOp::SetVertexBuffer { buffer, offset, .. } => {
                    let record = self.buffers.get(*buffer)?;
                    if !record.desc.usages.contains(&BufferUsage::Vertex) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "vertex buffer binding requires vertex buffer usage",
                        ));
                    }
                    if *offset >= record.desc.size {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "vertex buffer offset is outside the buffer",
                        ));
                    }
                }
                CommandOp::SetIndexBuffer {
                    buffer,
                    offset,
                    index_type,
                } => {
                    let record = self.buffers.get(*buffer)?;
                    if !record.desc.usages.contains(&BufferUsage::Index) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "index buffer binding requires index buffer usage",
                        ));
                    }
                    let index_size = index_type_size(*index_type);
                    if *offset % index_size != 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "index buffer offset must be aligned to index type size",
                        ));
                    }
                    if *offset >= record.desc.size {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "index buffer offset is outside the buffer",
                        ));
                    }
                    index_buffer = Some((*buffer, *offset, *index_type));
                }
                CommandOp::Draw {
                    vertices,
                    instances,
                } => {
                    if !in_pass || graphics_pipeline.is_none() || *vertices == 0 || *instances == 0
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "draw requires active pass, graphics pipeline, and non-zero counts",
                        ));
                    }
                }
                CommandOp::DrawIndexed { indices, instances } => {
                    if !in_pass || graphics_pipeline.is_none() || *indices == 0 || *instances == 0 {
                        return self.validation_error(GalError::command(StatusCode::InvalidArgument, "indexed draw requires active pass, pipeline, index buffer, and non-zero counts"));
                    }
                    let Some((buffer, offset, index_type)) = index_buffer else {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indexed draw requires active index buffer",
                        ));
                    };
                    let bytes = u64::from(*indices)
                        .checked_mul(index_type_size(index_type))
                        .ok_or_else(|| {
                            GalError::command(
                                StatusCode::InvalidArgument,
                                "indexed draw byte range overflows",
                            )
                        })?;
                    self.validate_buffer_range(buffer, offset, bytes, BufferUsage::Index)?;
                }
                CommandOp::DrawIndirect {
                    buffer,
                    offset,
                    draw_count,
                } => {
                    if !capabilities.supports(BackendFeature::IndirectDraw) {
                        return self.unsupported(format!(
                            "backend '{}' does not support indirect draw commands",
                            capabilities.name
                        ));
                    }
                    if !in_pass || graphics_pipeline.is_none() || *draw_count == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect draw requires active pass, graphics pipeline, and non-zero draw count",
                        ));
                    }
                    if *draw_count > capabilities.limits.max_draw_count {
                        return self.unsupported(format!(
                            "indirect draw count {} exceeds backend '{}' limit {}",
                            draw_count, capabilities.name, capabilities.limits.max_draw_count
                        ));
                    }
                    if *offset % 4 != 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect draw offset must be four-byte aligned",
                        ));
                    }
                    let bytes = u64::from(*draw_count).checked_mul(16).ok_or_else(|| {
                        GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect draw byte range overflows",
                        )
                    })?;
                    self.validate_buffer_range(*buffer, *offset, bytes, BufferUsage::Indirect)?;
                }
                CommandOp::DrawIndexedIndirect {
                    buffer,
                    offset,
                    draw_count,
                } => {
                    if !capabilities.supports(BackendFeature::IndirectDraw) {
                        return self.unsupported(format!(
                            "backend '{}' does not support indexed indirect draw commands",
                            capabilities.name
                        ));
                    }
                    if !in_pass || graphics_pipeline.is_none() || *draw_count == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indexed indirect draw requires active pass, graphics pipeline, index buffer, and non-zero draw count",
                        ));
                    }
                    if index_buffer.is_none() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indexed indirect draw requires active index buffer",
                        ));
                    }
                    if *draw_count > capabilities.limits.max_draw_count {
                        return self.unsupported(format!(
                            "indexed indirect draw count {} exceeds backend '{}' limit {}",
                            draw_count, capabilities.name, capabilities.limits.max_draw_count
                        ));
                    }
                    if *offset % 4 != 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indexed indirect draw offset must be four-byte aligned",
                        ));
                    }
                    let bytes = u64::from(*draw_count).checked_mul(20).ok_or_else(|| {
                        GalError::command(
                            StatusCode::InvalidArgument,
                            "indexed indirect draw byte range overflows",
                        )
                    })?;
                    self.validate_buffer_range(*buffer, *offset, bytes, BufferUsage::Indirect)?;
                }
                CommandOp::Dispatch {
                    groups_x,
                    groups_y,
                    groups_z,
                } => {
                    if !capabilities.supports(BackendFeature::Compute) {
                        return self.unsupported(format!(
                            "backend '{}' does not support dispatch commands",
                            capabilities.name
                        ));
                    }
                    if in_pass
                        || compute_pipeline.is_none()
                        || *groups_x == 0
                        || *groups_y == 0
                        || *groups_z == 0
                    {
                        return self.validation_error(GalError::command(StatusCode::InvalidArgument, "dispatch requires compute pipeline outside render pass and non-zero groups"));
                    }
                    if *groups_x > capabilities.limits.max_dispatch_groups_per_axis
                        || *groups_y > capabilities.limits.max_dispatch_groups_per_axis
                        || *groups_z > capabilities.limits.max_dispatch_groups_per_axis
                    {
                        return self.unsupported(format!(
                            "dispatch group count exceeds backend '{}' limit {}",
                            capabilities.name, capabilities.limits.max_dispatch_groups_per_axis
                        ));
                    }
                }
                CommandOp::DispatchIndirect { buffer, offset } => {
                    if !capabilities.supports(BackendFeature::IndirectDispatch) {
                        return self.unsupported(format!(
                            "backend '{}' does not support indirect dispatch commands",
                            capabilities.name
                        ));
                    }
                    if in_pass || compute_pipeline.is_none() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect dispatch requires compute pipeline outside render pass",
                        ));
                    }
                    if *offset % 4 != 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect dispatch offset must be four-byte aligned",
                        ));
                    }
                    // One record: three u32 work-group counts.
                    self.validate_buffer_range(*buffer, *offset, 12, BufferUsage::Indirect)?;
                }
                CommandOp::CopyBuffer { src, dst, size }
                | CommandOp::CopyBufferRegion {
                    src,
                    dst,
                    size,
                    ..
                } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer copy requires no active render pass",
                        ));
                    }
                    let src_record = self.buffers.get(*src)?;
                    let src_ok = src_record.desc.usages.contains(&BufferUsage::TransferSrc);
                    let dst_record = self.buffers.get(*dst)?;
                    let dst_ok = dst_record.desc.usages.contains(&BufferUsage::TransferDst);
                    if src == dst || *size == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer copy requires distinct buffers and non-zero size",
                        ));
                    }
                    if !src_ok || !dst_ok {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer copy requires transfer source and destination usages",
                        ));
                    }
                    let (src_offset, dst_offset) = match op {
                        CommandOp::CopyBufferRegion {
                            src_offset,
                            dst_offset,
                            ..
                        } => (*src_offset, *dst_offset),
                        _ => (0, 0),
                    };
                    self.validate_buffer_range(*src, src_offset, *size, BufferUsage::TransferSrc)?;
                    self.validate_buffer_range(*dst, dst_offset, *size, BufferUsage::TransferDst)?;
                }
                CommandOp::CopyBufferToTexture(region) => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer-to-texture copy requires no active render pass",
                        ));
                    }
                    if (region.texture_mip > 0 || region.texture_layer > 0)
                        && !capabilities.supports(BackendFeature::TextureSubresourceCopies)
                    {
                        return self.unsupported(format!(
                            "backend '{}' does not support texture subresource copies",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_texture_copy_region(
                        region,
                        BufferUsage::TransferSrc,
                        TextureUsage::TransferDst,
                    )?;
                }
                CommandOp::CopyTextureToBuffer(region) => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "texture-to-buffer copy requires no active render pass",
                        ));
                    }
                    if (region.texture_mip > 0 || region.texture_layer > 0)
                        && !capabilities.supports(BackendFeature::TextureSubresourceCopies)
                    {
                        return self.unsupported(format!(
                            "backend '{}' does not support texture subresource copies",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_texture_copy_region(
                        region,
                        BufferUsage::TransferDst,
                        TextureUsage::TransferSrc,
                    )?;
                }
                CommandOp::CopyTexture(region) => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "texture copy requires no active render pass",
                        ));
                    }
                    self.validate_texture_copy_region(region)?;
                }
                CommandOp::CopyFrameTargetToTexture { src, dst, extent } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "frame-target copy requires no active render pass",
                        ));
                    }
                    self.validate_frame_target_copy(*src, *dst, *extent)?;
                }
                CommandOp::CopyTextureToFrameTarget { src, dst, extent } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "texture-to-frame-target copy requires no active render pass",
                        ));
                    }
                    self.validate_texture_to_frame_target_copy(*src, *dst, *extent)?;
                }
                CommandOp::GenerateMipmaps {
                    texture,
                    subresources,
                } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "mip generation requires no active render pass",
                        ));
                    }
                    if !capabilities.supports(BackendFeature::TextureMipLevels) {
                        return self.unsupported(format!(
                            "backend '{}' does not support texture mip generation",
                            capabilities.name
                        ));
                    }
                    let texture_record = self.textures.get(*texture)?;
                    if !texture_record
                        .desc
                        .usages
                        .contains(&TextureUsage::TransferSrc)
                        || !texture_record
                            .desc
                            .usages
                            .contains(&TextureUsage::TransferDst)
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "mip generation requires transfer source and destination texture usages",
                        ));
                    }
                    if is_depth_format(texture_record.desc.format)
                        || texture_record.desc.format == TextureFormat::R8Uint
                    {
                        return self.unsupported(
                            "mip generation requires a non-depth, non-integer color texture",
                        );
                    }
                    if subresources.mip_count < 2 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "mip generation requires a range containing a source mip and at least one destination mip",
                        ));
                    }
                    self.validate_texture_subresource_range(*texture, *subresources)?;
                }
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset,
                    data,
                } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "host writes require no active render pass",
                        ));
                    }
                    if data.is_empty() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            format!(
                                "command list '{label}' operation {op_index} host write payload must be non-zero"
                            ),
                        ));
                    }
                    if !capabilities.supports(BackendFeature::HostBufferAccess) {
                        return self.unsupported(format!(
                            "backend '{}' does not support host buffer writes",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_range(
                        *buffer,
                        *offset,
                        data.len() as u64,
                        BufferUsage::HostWrite,
                    )?;
                    let record = self.buffers.get(*buffer)?;
                    if record.desc.memory != MemoryDomain::Upload {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "host writes require upload memory",
                        ));
                    }
                }
                CommandOp::HostReadBuffer {
                    buffer,
                    offset,
                    size,
                } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "host reads require no active render pass",
                        ));
                    }
                    if *size == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            format!(
                                "command list '{label}' operation {op_index} host read size must be non-zero"
                            ),
                        ));
                    }
                    if !capabilities.supports(BackendFeature::HostBufferAccess) {
                        return self.unsupported(format!(
                            "backend '{}' does not support host buffer reads",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_range(*buffer, *offset, *size, BufferUsage::HostRead)?;
                    let record = self.buffers.get(*buffer)?;
                    if record.desc.memory != MemoryDomain::Readback {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "host reads require readback memory",
                        ));
                    }
                }
                CommandOp::Present {
                    texture,
                    subresources,
                } => {
                    if !capabilities.supports(BackendFeature::Presentation) {
                        return self.unsupported(format!(
                            "backend '{}' does not support presentation commands in the isolated path",
                            capabilities.name
                        ));
                    }
                    let record = self.textures.get(*texture)?;
                    if !record.desc.usages.contains(&TextureUsage::Present) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "presentation requires present texture usage",
                        ));
                    }
                    self.validate_texture_subresource_range(*texture, *subresources)?;
                }
                CommandOp::Barrier(barrier) => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource barriers require no active render pass",
                        ));
                    }
                    self.validate_any_resource(barrier.resource)?;
                    if let Some(range) = barrier.subresources {
                        match barrier.resource.kind() {
                            Some(HandleKind::Texture) => {
                                self.validate_texture_subresource_range(barrier.resource, range)?;
                            }
                            Some(HandleKind::TextureView) => {
                                let info = self.texture_view_info(barrier.resource)?;
                                self.validate_texture_subresource_range(info.texture, range)?;
                                if !texture_range_contains(info.range, range) {
                                    return self.validation_error(GalError::command(
                                        StatusCode::InvalidArgument,
                                        "barrier subresource range is outside the texture view",
                                    ));
                                }
                            }
                            _ => {
                                return self.validation_error(GalError::command(
                                    StatusCode::InvalidArgument,
                                    "barrier subresource ranges are only valid for textures",
                                ));
                            }
                        }
                    }
                    // A write-after-write/read dependency is meaningful even
                    // when the resource keeps its layout and semantic usage.
                    // Read-only same-usage barriers still describe no work.
                    if barrier.before == barrier.after
                        && !matches!(
                            barrier.before,
                            TextureUsageState::ShaderWrite
                                | TextureUsageState::ColorAttachment
                                | TextureUsageState::DepthStencilAttachment
                                | TextureUsageState::TransferDst
                        )
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "barrier must declare a semantic state change or write dependency",
                        ));
                    }
                    if barrier.src_queue == QueueClass::Present
                        || barrier.dst_queue == QueueClass::Present
                    {
                        let texture = match barrier.resource.kind() {
                            Some(HandleKind::Texture) => self.textures.get(barrier.resource)?,
                            Some(HandleKind::TextureView) => {
                                let info = self.texture_view_info(barrier.resource)?;
                                self.textures.get(info.texture)?
                            }
                            _ => {
                                return self.validation_error(GalError::command(
                                    StatusCode::InvalidArgument,
                                    "presentation queue ownership requires a texture or texture view",
                                ));
                            }
                        };
                        if !texture.desc.usages.contains(&TextureUsage::Present) {
                            return self.validation_error(GalError::command(
                                StatusCode::InvalidArgument,
                                "queue ownership transfer involving presentation requires present texture usage",
                            ));
                        }
                    }
                }
                CommandOp::TrackSubmission(_) => {}
                CommandOp::EndPass => {
                    if !in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "EndPass without BeginPass",
                        ));
                    }
                    in_pass = false;
                    active_pass = None;
                    graphics_pipeline = None;
                    active_pipeline_layout = None;
                    index_buffer = None;
                }
            }
        }
        if in_pass {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "command list ended inside a pass",
            ));
        }
        Ok(())
    }

    pub(super) fn validate_pipeline_pass_compatibility(
        &mut self,
        pipeline: Handle,
        pass: Handle,
    ) -> GalResult<()> {
        let compatible = {
            let pipeline_record = self.graphics_pipelines.get(pipeline)?;
            let pass_record = self.render_passes.get(pass)?;
            pipeline_record.desc.color_formats == pass_record.desc.color_formats
                && pipeline_record.desc.depth_format == pass_record.desc.depth_format
        };
        if !compatible {
            if std::env::var_os("MATTMC_TRACE_PIPELINE_PASS_COMPAT").is_some() {
                let pipeline_record = self.graphics_pipelines.get(pipeline)?;
                let pass_record = self.render_passes.get(pass)?;
                eprintln!(
                    "vulkan.pipeline-pass-mismatch pipeline={:?} label={} colors={:?} depth={:?} pass={:?} label={} colors={:?} depth={:?}",
                    pipeline,
                    pipeline_record.desc.label,
                    pipeline_record.desc.color_formats,
                    pipeline_record.desc.depth_format,
                    pass,
                    pass_record.desc.label,
                    pass_record.desc.color_formats,
                    pass_record.desc.depth_format,
                );
            }
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "graphics pipeline attachment formats are not compatible with render pass",
            ));
        }
        Ok(())
    }

    pub(super) fn validate_any_resource(&self, handle: Handle) -> GalResult<()> {
        match handle.kind() {
            Some(HandleKind::Buffer) => self.buffers.get(handle).map(|_| ()),
            Some(HandleKind::Texture) => self.textures.get(handle).map(|_| ()),
            Some(HandleKind::TextureView) => self.texture_views.get(handle).map(|_| ()),
            Some(HandleKind::Sampler) => self.samplers.get(handle).map(|_| ()),
            Some(HandleKind::CombinedTextureSampler) => {
                self.combined_texture_samplers.get(handle).map(|_| ())
            }
            Some(HandleKind::ShaderModule) => self.shaders.get(handle).map(|_| ()),
            Some(HandleKind::ResourceLayout) => self.resource_layouts.get(handle).map(|_| ()),
            Some(HandleKind::ResourceSet) => self.resource_sets.get(handle).map(|_| ()),
            Some(HandleKind::PipelineLayout) => self.pipeline_layouts.get(handle).map(|_| ()),
            Some(HandleKind::GraphicsPipeline) => self.graphics_pipelines.get(handle).map(|_| ()),
            Some(HandleKind::ComputePipeline) => self.compute_pipelines.get(handle).map(|_| ()),
            Some(HandleKind::RenderTarget) => self.render_targets.get(handle).map(|_| ()),
            Some(HandleKind::FrameTarget) => self.frame_targets.get(handle).map(|_| ()),
            Some(HandleKind::RenderPass) => self.render_passes.get(handle).map(|_| ()),
            None => Err(GalError::handle(
                StatusCode::WrongHandleType,
                "unknown handle kind",
            )),
        }
    }

    pub(super) fn validate_buffer_range(
        &mut self,
        buffer: Handle,
        offset: u64,
        size: u64,
        usage: BufferUsage,
    ) -> GalResult<()> {
        let record = self.buffers.get(buffer)?;
        let has_usage = record.desc.usages.contains(&usage);
        let buffer_size = record.desc.size;
        if size == 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "buffer range size must be non-zero",
            ));
        }
        let Some(end) = offset.checked_add(size) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "buffer range overflows",
            ));
        };
        if end > buffer_size {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "buffer range is outside the buffer",
            ));
        }
        if !has_usage {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                format!("buffer range requires {usage:?} usage"),
            ));
        }
        Ok(())
    }

    pub(super) fn validate_buffer_texture_copy_region(
        &mut self,
        region: &BufferImageCopyRegion,
        buffer_usage: BufferUsage,
        texture_usage: TextureUsage,
    ) -> GalResult<()> {
        let size = self.buffer_texture_copy_size(region)?;
        self.validate_buffer_range(region.buffer, region.buffer_offset, size, buffer_usage)?;
        let (texture_dimension, texture_format, texture_usages, texture_extent) = {
            let texture = self.textures.get(region.texture)?;
            (
                texture.desc.dimension,
                texture.desc.format,
                texture.desc.usages.clone(),
                texture.desc.extent,
            )
        };
        if !texture_usages.contains(&texture_usage) {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                format!("texture copy requires {texture_usage:?} usage"),
            ));
        }
        if region.extent.width == 0 || region.extent.height == 0 || region.extent.depth == 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy extent must be non-zero",
            ));
        }
        let Some(bytes_per_texel) = texture_format.copy_bytes_per_texel() else {
            return self.unsupported(format!(
                "texture format {texture_format:?} does not support buffer texture copies"
            ));
        };
        let Some(minimum_row_bytes) = region.extent.width.checked_mul(bytes_per_texel) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy row byte count overflows",
            ));
        };
        if region.buffer_offset % 4 != 0
            || region.bytes_per_row < minimum_row_bytes
            || region.rows_per_image < region.extent.height
            || region.bytes_per_row % bytes_per_texel != 0
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy row layout is malformed",
            ));
        }
        self.validate_texture_subresource_range(
            region.texture,
            TextureSubresourceRange {
                base_mip: region.texture_mip,
                mip_count: 1,
                base_layer: region.texture_layer,
                layer_count: 1,
            },
        )?;
        if texture_dimension == TextureDimension::D3 && region.texture_layer != 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "D3 texture copies must use texture layer 0",
            ));
        }
        if texture_dimension == TextureDimension::D2
            && (region.texture_origin.z != 0 || region.extent.depth != 1)
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "D2 texture copies must address one depth slice at z 0",
            ));
        }
        let mip_extent = texture_mip_extent(texture_extent, region.texture_mip);
        let Some(x_end) = region.texture_origin.x.checked_add(region.extent.width) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy x range overflows",
            ));
        };
        let Some(y_end) = region.texture_origin.y.checked_add(region.extent.height) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy y range overflows",
            ));
        };
        let Some(z_end) = region.texture_origin.z.checked_add(region.extent.depth) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy z range overflows",
            ));
        };
        if x_end > mip_extent.width || y_end > mip_extent.height || z_end > mip_extent.depth {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy region is outside texture extent",
            ));
        }
        Ok(())
    }

    pub(super) fn buffer_texture_copy_size(&self, region: &BufferImageCopyRegion) -> GalResult<u64> {
        if region.extent.width == 0 || region.extent.height == 0 || region.extent.depth == 0 {
            return Err(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy extent must be non-zero",
            ));
        }
        let bytes_per_row = u64::from(region.bytes_per_row);
        let rows_per_image = u64::from(region.rows_per_image);
        let layers = u64::from(region.extent.depth);
        rows_per_image
            .checked_mul(layers.saturating_sub(1))
            .and_then(|rows_before_last| {
                rows_before_last.checked_add(u64::from(region.extent.height))
            })
            .and_then(|rows| rows.checked_mul(bytes_per_row))
            .ok_or_else(|| {
                GalError::command(
                    StatusCode::InvalidArgument,
                    "texture copy buffer size overflows",
                )
            })
    }

    pub(super) fn validate_texture_copy_region(&mut self, region: &TextureImageCopyRegion) -> GalResult<()> {
        if region.row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse
            && !self
                .capabilities()
                .supports(BackendFeature::TextureRowReversal)
        {
            return self.validation_error(GalError::unsupported_feature(
                "backend does not support explicit texture row reversal",
            ));
        }
        if region.src_texture == region.dst_texture
            || region.extent.width == 0
            || region.extent.height == 0
            || region.extent.depth == 0
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy requires distinct textures and a non-zero extent",
            ));
        }
        let (src_dimension, src_format, src_usages, src_extent) = {
            let record = self.textures.get(region.src_texture)?;
            (
                record.desc.dimension,
                record.desc.format,
                record.desc.usages.clone(),
                record.desc.extent,
            )
        };
        let (dst_dimension, dst_format, dst_usages, dst_extent) = {
            let record = self.textures.get(region.dst_texture)?;
            (
                record.desc.dimension,
                record.desc.format,
                record.desc.usages.clone(),
                record.desc.extent,
            )
        };
        if !src_usages.contains(&TextureUsage::TransferSrc)
            || !dst_usages.contains(&TextureUsage::TransferDst)
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy requires transfer source and destination usages",
            ));
        }
        if src_dimension != dst_dimension || src_format != dst_format {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy requires matching dimensions and formats",
            ));
        }
        if region.row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse
            && src_dimension != TextureDimension::D2
        {
            return self.validation_error(GalError::unsupported_feature(
                "texture row reversal currently requires D2 textures",
            ));
        }
        self.validate_texture_copy_box(
            region.src_texture,
            region.src_mip,
            region.src_layer,
            region.src_origin,
            region.extent,
            src_dimension,
            src_extent,
        )?;
        self.validate_texture_copy_box(
            region.dst_texture,
            region.dst_mip,
            region.dst_layer,
            region.dst_origin,
            region.extent,
            dst_dimension,
            dst_extent,
        )
    }

    pub(super) fn validate_texture_copy_box(
        &mut self,
        texture: Handle,
        mip: u32,
        layer: u32,
        origin: TextureOrigin3d,
        extent: Extent3d,
        dimension: TextureDimension,
        base_extent: Extent3d,
    ) -> GalResult<()> {
        self.validate_texture_subresource_range(
            texture,
            TextureSubresourceRange {
                base_mip: mip,
                mip_count: 1,
                base_layer: layer,
                layer_count: 1,
            },
        )?;
        if dimension == TextureDimension::D3 && layer != 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "D3 texture copies must use layer 0",
            ));
        }
        if dimension == TextureDimension::D2 && (origin.z != 0 || extent.depth != 1) {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "D2 texture copies must address one depth slice at z 0",
            ));
        }
        let mip_extent = texture_mip_extent(base_extent, mip);
        let valid = origin
            .x
            .checked_add(extent.width)
            .is_some_and(|end| end <= mip_extent.width)
            && origin
                .y
                .checked_add(extent.height)
                .is_some_and(|end| end <= mip_extent.height)
            && origin
                .z
                .checked_add(extent.depth)
                .is_some_and(|end| end <= mip_extent.depth);
        if !valid {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy region is outside texture extent",
            ));
        }
        Ok(())
    }

    pub(super) fn validate_frame_target_copy(
        &mut self,
        src: Handle,
        dst: Handle,
        extent: Extent3d,
    ) -> GalResult<()> {
        if src == dst || extent.width == 0 || extent.height == 0 || extent.depth == 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "frame-target copy requires distinct handles and a non-zero extent",
            ));
        }
        let source = self.frame_targets.get(src)?.desc.clone();
        let destination = self.textures.get(dst)?.desc.clone();
        if !destination.usages.contains(&TextureUsage::TransferDst) {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "frame-target copy destination requires transfer-destination usage",
            ));
        }
        if destination.format != source.color_format
            || destination.dimension != TextureDimension::D2
            || extent.width > source.extent.width
            || extent.height > source.extent.height
            || extent.depth != 1
            || extent.width > destination.extent.width
            || extent.height > destination.extent.height
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "frame-target copy requires matching 2D format and bounded extent",
            ));
        }
        Ok(())
    }

    pub(super) fn validate_texture_to_frame_target_copy(
        &mut self,
        src: Handle,
        dst: Handle,
        extent: Extent3d,
    ) -> GalResult<()> {
        if src == dst || extent.width == 0 || extent.height == 0 || extent.depth == 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture-to-frame-target copy requires distinct handles and a non-zero extent",
            ));
        }
        let source = self.textures.get(src)?.desc.clone();
        let destination = self.frame_targets.get(dst)?.desc.clone();
        if !source.usages.contains(&TextureUsage::TransferSrc) {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture-to-frame-target copy source requires transfer-source usage",
            ));
        }
        if source.format != destination.color_format
            || source.dimension != TextureDimension::D2
            || extent.width > source.extent.width
            || extent.height > source.extent.height
            || extent.depth != 1
            || extent.width > destination.extent.width
            || extent.height > destination.extent.height
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture-to-frame-target copy requires matching 2D format and bounded extent",
            ));
        }
        Ok(())
    }

    pub(super) fn validate_texture_subresource_range(
        &mut self,
        texture: Handle,
        range: TextureSubresourceRange,
    ) -> GalResult<()> {
        let record = self.textures.get(texture)?;
        let dimension = record.desc.dimension;
        let mip_levels = record.desc.mip_levels;
        let array_layers = record.desc.array_layers;
        let Some(mip_end) = range.base_mip.checked_add(range.mip_count) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture subresource mip range overflows",
            ));
        };
        let Some(layer_end) = range.base_layer.checked_add(range.layer_count) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture subresource layer range overflows",
            ));
        };
        if range.mip_count == 0
            || range.layer_count == 0
            || mip_end > mip_levels
            || layer_end > array_layers
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture subresource range is outside the texture",
            ));
        }
        if dimension == TextureDimension::D3 && (range.base_layer != 0 || range.layer_count != 1) {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "D3 texture subresources have exactly one image layer",
            ));
        }
        Ok(())
    }
}

pub(super) fn index_type_size(index_type: crate::render::vulkanic::resources::IndexType) -> u64 {
    match index_type {
        crate::render::vulkanic::resources::IndexType::U16 => 2,
        crate::render::vulkanic::resources::IndexType::U32 => 4,
    }
}

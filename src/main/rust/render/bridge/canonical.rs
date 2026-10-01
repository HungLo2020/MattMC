//! Canonical byte serialization of decoded resource and submission batches,
//! so tests can compare what the bridge decoded independently of layout.

use crate::render::bridge::*;

pub fn serialize_resource_batch_canonical(batch: &FfiOwnedResourceBatch) -> Vec<u8> {
    let mut out = Vec::new();
    push_u32(&mut out, FFI_ABI_VERSION);
    push_u64(&mut out, batch.negotiated_feature_bits);
    push_u64(&mut out, batch.buffers.len() as u64);
    for item in &batch.buffers {
        push_create_prefix(&mut out, item.request_id, &item.desc.label);
        push_u64(&mut out, item.desc.size);
        push_u32(&mut out, item.desc.memory as u32);
        push_u64(&mut out, buffer_usage_bits_from_desc(&item.desc.usages));
    }
    push_u64(&mut out, batch.textures.len() as u64);
    for item in &batch.textures {
        push_create_prefix(&mut out, item.request_id, &item.desc.label);
        push_u32(&mut out, item.desc.dimension as u32);
        push_u32(&mut out, item.desc.format as u32);
        push_extent(&mut out, item.desc.extent);
        push_u32(&mut out, item.desc.mip_levels);
        push_u32(&mut out, item.desc.array_layers);
        push_u64(&mut out, texture_usage_bits_from_desc(&item.desc.usages));
    }
    push_u64(&mut out, batch.shaders.len() as u64);
    for item in &batch.shaders {
        push_create_prefix(&mut out, item.request_id, &item.desc.label);
        push_u32(&mut out, item.desc.stage as u32);
        push_u32(&mut out, item.desc.code_format as u32);
        push_bytes(&mut out, &item.desc.code);
        push_str(&mut out, &item.desc.entry_point);
    }
    push_u64(&mut out, batch.destroys.len() as u64);
    for (handle, kind) in &batch.destroys {
        push_u64(&mut out, handle.raw());
        push_u32(&mut out, *kind as u32);
    }
    out
}

pub fn serialize_submission_batch_canonical(batch: &SubmissionBatch) -> Vec<u8> {
    let mut out = Vec::new();
    push_u32(&mut out, FFI_ABI_VERSION);
    push_str(&mut out, &batch.label);
    push_u64(&mut out, batch.command_lists.len() as u64);
    for list in &batch.command_lists {
        push_str(&mut out, &list.label);
        // CPU lifetime receipts are private Rust state, not ABI commands.
        push_u64(
            &mut out,
            list.operations
                .iter()
                .filter(|op| !matches!(op, CommandOp::TrackSubmission(_)))
                .count() as u64,
        );
        for op in &list.operations {
            serialize_command_op(&mut out, op);
        }
    }
    out
}

pub(crate) fn buffer_usage_bits_from_desc(usages: &[BufferUsage]) -> u64 {
    usages.iter().fold(0_u64, |bits, usage| {
        bits | match usage {
            BufferUsage::Vertex => 1 << 0,
            BufferUsage::Index => 1 << 1,
            BufferUsage::Uniform => 1 << 2,
            BufferUsage::Storage => 1 << 3,
            BufferUsage::TransferSrc => 1 << 4,
            BufferUsage::TransferDst => 1 << 5,
            BufferUsage::Indirect => 1 << 6,
            BufferUsage::HostRead => 1 << 7,
            BufferUsage::HostWrite => 1 << 8,
        }
    })
}

pub(crate) fn texture_usage_bits_from_desc(usages: &[TextureUsage]) -> u64 {
    usages.iter().fold(0_u64, |bits, usage| {
        bits | match usage {
            TextureUsage::Sampled => 1 << 0,
            TextureUsage::Storage => 1 << 1,
            TextureUsage::ColorAttachment => 1 << 2,
            TextureUsage::DepthStencilAttachment => 1 << 3,
            TextureUsage::TransferSrc => 1 << 4,
            TextureUsage::TransferDst => 1 << 5,
            TextureUsage::Present => 1 << 6,
            TextureUsage::HostRead => 1 << 7,
            TextureUsage::HostWrite => 1 << 8,
        }
    })
}

pub(crate) fn serialize_command_op(out: &mut Vec<u8>, op: &CommandOp) {
    match op {
        CommandOp::TrackSubmission(_) => {}
        CommandOp::BeginPass {
            pass,
            target,
            colors,
            depth_stencil,
        } => {
            push_u32(out, FfiCommandOpKind::BeginPass as u32);
            push_u64(out, pass.raw());
            push_u64(out, target.raw());
            push_u64(out, colors.len() as u64);
            for color in colors {
                serialize_attachment(out, color);
            }
            push_u32(out, u32::from(depth_stencil.is_some()));
            if let Some(depth) = depth_stencil {
                serialize_attachment(out, depth);
            }
        }
        CommandOp::BindGraphicsPipeline(handle) => {
            push_u32(out, FfiCommandOpKind::BindGraphicsPipeline as u32);
            push_u64(out, handle.raw());
        }
        CommandOp::BindComputePipeline(handle) => {
            push_u32(out, FfiCommandOpKind::BindComputePipeline as u32);
            push_u64(out, handle.raw());
        }
        CommandOp::BindResourceSet {
            pipeline_layout,
            set_index,
            set,
            ..
        } => {
            push_u32(out, FfiCommandOpKind::BindResourceSet as u32);
            push_u64(out, pipeline_layout.raw());
            push_u32(out, *set_index);
            push_u64(out, set.raw());
        }
        CommandOp::SetVertexBuffer {
            slot,
            buffer,
            offset,
        } => {
            push_u32(out, FfiCommandOpKind::SetVertexBuffer as u32);
            push_u32(out, *slot);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
        }
        CommandOp::SetIndexBuffer {
            buffer,
            offset,
            index_type,
        } => {
            push_u32(out, FfiCommandOpKind::SetIndexBuffer as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_u32(out, *index_type as u32);
        }
        CommandOp::Draw {
            vertices,
            instances,
        } => {
            push_u32(out, FfiCommandOpKind::Draw as u32);
            push_u32(out, *vertices);
            push_u32(out, *instances);
        }
        CommandOp::DrawIndexed { indices, instances } => {
            push_u32(out, FfiCommandOpKind::DrawIndexed as u32);
            push_u32(out, *indices);
            push_u32(out, *instances);
        }
        CommandOp::DrawIndirect {
            buffer,
            offset,
            draw_count,
        } => {
            push_u32(out, FfiCommandOpKind::DrawIndirect as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_u32(out, *draw_count);
        }
        CommandOp::DrawIndexedIndirect {
            buffer,
            offset,
            draw_count,
        } => {
            push_u32(out, FfiCommandOpKind::DrawIndexedIndirect as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_u32(out, *draw_count);
        }
        CommandOp::Dispatch {
            groups_x,
            groups_y,
            groups_z,
        } => {
            push_u32(out, FfiCommandOpKind::Dispatch as u32);
            push_u32(out, *groups_x);
            push_u32(out, *groups_y);
            push_u32(out, *groups_z);
        }
        CommandOp::DispatchIndirect { buffer, offset } => {
            push_u32(out, FfiCommandOpKind::DispatchIndirect as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
        }
        CommandOp::CopyBuffer { src, dst, size } => {
            push_u32(out, FfiCommandOpKind::CopyBuffer as u32);
            push_u64(out, src.raw());
            push_u64(out, dst.raw());
            push_u64(out, *size);
        }
        CommandOp::CopyBufferRegion { .. } => unreachable!(
            "offset buffer copies are Rust-owned resource uploads, not Java FFI submission commands"
        ),
        CommandOp::CopyBufferToTexture(region) => {
            push_u32(out, FfiCommandOpKind::CopyBufferToTexture as u32);
            serialize_copy_region(out, region);
        }
        CommandOp::CopyTextureToBuffer(region) => {
            push_u32(out, FfiCommandOpKind::CopyTextureToBuffer as u32);
            serialize_copy_region(out, region);
        }
        CommandOp::CopyTexture(_) => unreachable!(
            "Rust-owned texture snapshots are not Java FFI submission commands; they remain inside the Rust shader-pack executor"
        ),
        CommandOp::CopyFrameTargetToTexture { .. } => unreachable!(
            "frame-target sampling copies are Rust-owned post-processing commands, not Java FFI submission commands"
        ),
        CommandOp::CopyTextureToFrameTarget { .. } => unreachable!(
            "texture-to-frame-target presentation copies are Rust-owned post-processing commands, not Java FFI submission commands"
        ),
        CommandOp::GenerateMipmaps { .. } => {
            unreachable!(
                "Rust-owned mip generation is not a Java FFI submission command; it must remain inside the Rust shader-pack executor"
            );
        }
        CommandOp::HostWriteBuffer {
            buffer,
            offset,
            data,
        } => {
            push_u32(out, FfiCommandOpKind::HostWriteBuffer as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_bytes(out, data);
        }
        CommandOp::HostReadBuffer {
            buffer,
            offset,
            size,
        } => {
            push_u32(out, FfiCommandOpKind::HostReadBuffer as u32);
            push_u64(out, buffer.raw());
            push_u64(out, *offset);
            push_u64(out, *size);
        }
        CommandOp::Present {
            texture,
            subresources,
        } => {
            push_u32(out, FfiCommandOpKind::Present as u32);
            push_u64(out, texture.raw());
            serialize_subresources(out, *subresources);
        }
        CommandOp::Barrier(barrier) => {
            push_u32(out, FfiCommandOpKind::Barrier as u32);
            push_u64(out, barrier.resource.raw());
            push_u32(out, u32::from(barrier.subresources.is_some()));
            if let Some(range) = barrier.subresources {
                serialize_subresources(out, range);
            }
            push_u32(out, barrier.before as u32);
            push_u32(out, barrier.after as u32);
            push_u32(out, 0);
            push_u32(out, 0);
            push_u32(out, barrier.src_queue as u32);
            push_u32(out, barrier.dst_queue as u32);
        }
        CommandOp::EndPass => push_u32(out, FfiCommandOpKind::EndPass as u32),
    }
}

pub(crate) fn serialize_attachment(out: &mut Vec<u8>, attachment: &PassAttachment) {
    push_u64(out, attachment.view.raw());
    push_u32(out, attachment.load_op as u32);
    push_u32(out, attachment.store_op as u32);
    push_u32(out, u32::from(attachment.clear_color.is_some()));
    if let Some(color) = attachment.clear_color {
        push_f32(out, color.r);
        push_f32(out, color.g);
        push_f32(out, color.b);
        push_f32(out, color.a);
    }
}

pub(crate) fn serialize_copy_region(out: &mut Vec<u8>, region: &BufferImageCopyRegion) {
    push_u64(out, region.buffer.raw());
    push_u64(out, region.buffer_offset);
    push_u32(out, region.bytes_per_row);
    push_u32(out, region.rows_per_image);
    push_u64(out, region.texture.raw());
    push_u32(out, region.texture_mip);
    push_u32(out, region.texture_layer);
    push_u32(out, region.texture_origin.x);
    push_u32(out, region.texture_origin.y);
    push_u32(out, region.texture_origin.z);
    push_extent(out, region.extent);
}

pub(crate) fn serialize_subresources(out: &mut Vec<u8>, range: TextureSubresourceRange) {
    push_u32(out, range.base_mip);
    push_u32(out, range.mip_count);
    push_u32(out, range.base_layer);
    push_u32(out, range.layer_count);
}

pub(crate) fn push_create_prefix(out: &mut Vec<u8>, request_id: u64, label: &str) {
    push_u64(out, request_id);
    push_str(out, label);
}

pub(crate) fn push_extent(out: &mut Vec<u8>, extent: Extent3d) {
    push_u32(out, extent.width);
    push_u32(out, extent.height);
    push_u32(out, extent.depth);
}

pub(crate) fn push_str(out: &mut Vec<u8>, value: &str) {
    push_bytes(out, value.as_bytes());
}

pub(crate) fn push_bytes(out: &mut Vec<u8>, bytes: &[u8]) {
    push_u64(out, bytes.len() as u64);
    out.extend_from_slice(bytes);
}

pub(crate) fn push_u64(out: &mut Vec<u8>, value: u64) {
    out.extend_from_slice(&value.to_le_bytes());
}

pub(crate) fn push_u32(out: &mut Vec<u8>, value: u32) {
    out.extend_from_slice(&value.to_le_bytes());
}

pub(crate) fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_le_bytes());
}

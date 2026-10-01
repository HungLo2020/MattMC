//! The creeper spectator post effect.

use super::*;

impl GuiFrontend {
    /// Lowers the vanilla creeper vision graph: color-convolve into a private
    /// intermediate target, then apply the bounded mosaic/bits pass back to
    /// the acquired target. The effect is intentionally explicit and uses no
    /// Java post-chain state.
    pub(crate) fn append_creeper_post_effect(
        &mut self,
        gal: &mut VulkanicGal,
        render_target: Handle,
        color_attachment: Handle,
    ) -> GalResult<Vec<CommandOp>> {
        let extent = gal.pass_target_extent(render_target)?;
        let color_format = gal.pass_target_color_format(render_target)?;
        let resources =
            self.ensure_blur_resources(gal, extent.width, extent.height, color_format)?;
        let snapshot = resources.texture;
        let intermediate = resources.creeper_texture;
        let intermediate_view = resources.creeper_view;
        let intermediate_target = resources.creeper_target;
        let intermediate_pass = resources.creeper_pass;
        let uniform_buffer = resources.uniform_buffer;
        let source_set = resources.resource_set;
        let intermediate_set = resources.creeper_resource_set;
        let layout = resources.pipeline_layout;
        let color_pipeline = resources.creeper_color_pipeline;
        let bits_pipeline = resources.creeper_bits_pipeline;
        let final_pass = self.frame_pass(gal, render_target, None)?;
        let snapshot_before = if self.blur_snapshot_initialized {
            TextureUsageState::ShaderRead
        } else {
            TextureUsageState::Undefined
        };
        let mut ops = vec![
            CommandOp::HostWriteBuffer {
                buffer: uniform_buffer,
                offset: 0,
                data: {
                    let mut bytes = vec![0u8; 64];
                    for (index, value) in [
                        0.0f32, 0.0, 0.0, 0.0, 0.3, 0.59, 0.11, 0.0, 0.0, 0.0, 0.0, 0.0,
                    ]
                    .into_iter()
                    .enumerate()
                    {
                        bytes[index * 4..index * 4 + 4].copy_from_slice(&value.to_le_bytes());
                    }
                    bytes
                },
            },
            CommandOp::Barrier(buffer_barrier(
                uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )),
            CommandOp::Barrier(ResourceBarrier {
                resource: snapshot,
                subresources: None,
                before: snapshot_before,
                after: TextureUsageState::TransferDst,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
        ];
        if render_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            ops.push(CommandOp::CopyFrameTargetToTexture {
                src: render_target,
                dst: snapshot,
                extent,
            });
        } else {
            let source_texture = gal.pass_target_color_texture(render_target)?;
            ops.extend([
                CommandOp::Barrier(ResourceBarrier {
                    resource: source_texture,
                    subresources: None,
                    before: TextureUsageState::ColorAttachment,
                    after: TextureUsageState::TransferSrc,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Transfer,
                }),
                CommandOp::CopyTexture(TextureImageCopyRegion {
                    row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                    src_texture: source_texture,
                    src_mip: 0,
                    src_layer: 0,
                    src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                    dst_texture: snapshot,
                    dst_mip: 0,
                    dst_layer: 0,
                    dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                    extent,
                }),
                CommandOp::Barrier(ResourceBarrier {
                    resource: source_texture,
                    subresources: None,
                    before: TextureUsageState::TransferSrc,
                    after: TextureUsageState::ColorAttachment,
                    src_queue: QueueClass::Transfer,
                    dst_queue: QueueClass::Graphics,
                }),
            ]);
        }
        ops.extend([
            CommandOp::Barrier(ResourceBarrier {
                resource: snapshot,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ShaderRead,
                src_queue: QueueClass::Transfer,
                dst_queue: QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: intermediate,
                subresources: None,
                before: if self.creeper_intermediate_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                after: TextureUsageState::ColorAttachment,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }),
            CommandOp::BeginPass {
                pass: intermediate_pass,
                target: intermediate_target,
                colors: vec![PassAttachment {
                    view: intermediate_view,
                    load_op: AttachmentLoadOp::DontCare,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(color_pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout: layout,
                set_index: 0,
                set: source_set,
                dynamic_offsets: Vec::new(),
            },
            CommandOp::Draw {
                vertices: 3,
                instances: 1,
            },
            CommandOp::EndPass,
            CommandOp::Barrier(ResourceBarrier {
                resource: intermediate,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }),
            CommandOp::HostWriteBuffer {
                buffer: uniform_buffer,
                offset: 0,
                data: {
                    let mut bytes = vec![0u8; 64];
                    bytes[..4].copy_from_slice(&16.0f32.to_le_bytes());
                    bytes[4..8].copy_from_slice(&4.0f32.to_le_bytes());
                    bytes
                },
            },
            CommandOp::Barrier(buffer_barrier(
                uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )),
            CommandOp::BeginPass {
                pass: final_pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(bits_pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout: layout,
                set_index: 0,
                set: intermediate_set,
                dynamic_offsets: Vec::new(),
            },
            CommandOp::Draw {
                vertices: 3,
                instances: 1,
            },
            CommandOp::EndPass,
        ]);
        self.blur_snapshot_initialized = true;
        self.creeper_intermediate_initialized = true;
        Ok(ops)
    }
}

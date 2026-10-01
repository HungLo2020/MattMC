//! The crosshair and rectangle invert post effect.

use super::*;

impl GuiFrontend {
    /// Lowers the vanilla invert post effect against the acquired Rust target.
    /// The source is copied into the persistent Rust-owned snapshot, then a
    /// fullscreen Rust pipeline writes the inverted result back before GUI
    /// composition. No Java post-chain or GUI fallback is consulted.
    pub(crate) fn append_invert_post_effect(
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
        let uniform_buffer = resources.uniform_buffer;
        let pipeline = resources.invert_pipeline;
        let pipeline_layout = resources.pipeline_layout;
        let resource_set = resources.resource_set;
        let pass = self.frame_pass(gal, render_target, None)?;
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
                    let mut bytes = vec![0u8; 16];
                    bytes[..4].copy_from_slice(&0.8f32.to_le_bytes());
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
            CommandOp::BeginPass {
                pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout,
                set_index: 0,
                set: resource_set,
                dynamic_offsets: Vec::new(),
            },
            CommandOp::Draw {
                vertices: 3,
                instances: 1,
            },
            CommandOp::EndPass,
        ]);
        self.blur_snapshot_initialized = true;
        Ok(ops)
    }
}

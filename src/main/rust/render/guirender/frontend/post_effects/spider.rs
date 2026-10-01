//! The spider spectator post effect.

use super::*;

impl GuiFrontend {
    pub(crate) fn append_spider_post_effect(
        &mut self,
        gal: &mut VulkanicGal,
        render_target: Handle,
        color_attachment: Handle,
    ) -> GalResult<Vec<CommandOp>> {
        let extent = gal.pass_target_extent(render_target)?;
        let color_format = gal.pass_target_color_format(render_target)?;
        let (
            snapshot,
            uniform,
            single_layout,
            dual_layout,
            box_pipeline,
            clip_pipeline,
            blit_pipeline,
            single_sets,
            dual_sets,
            targets,
            views,
            passes,
            spider_textures,
        ) = {
            let resources =
                self.ensure_blur_resources(gal, extent.width, extent.height, color_format)?;
            (
                resources.texture,
                resources.uniform_buffer,
                resources.pipeline_layout,
                resources.spider_dual_pipeline_layout,
                resources.spider_box_pipeline,
                resources.spider_clip_pipeline,
                resources.spider_blit_pipeline,
                resources.spider_single_sets,
                resources.spider_dual_sets,
                resources.spider_targets,
                resources.spider_views,
                resources.spider_passes,
                resources.spider_textures,
            )
        };
        let final_pass = self.frame_pass(gal, render_target, None)?;
        let snapshot_before = if self.blur_snapshot_initialized {
            TextureUsageState::ShaderRead
        } else {
            TextureUsageState::Undefined
        };
        let mut spider_initialized = self.spider_initialized;
        let mut ops = vec![CommandOp::Barrier(ResourceBarrier {
            resource: snapshot,
            subresources: None,
            before: snapshot_before,
            after: TextureUsageState::TransferDst,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Transfer,
        })];
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
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: snapshot,
            subresources: None,
            before: TextureUsageState::TransferDst,
            after: TextureUsageState::ShaderRead,
            src_queue: QueueClass::Transfer,
            dst_queue: QueueClass::Graphics,
        }));
        let mut append_single = |source_set: Handle,
                                 output: usize,
                                 pass: Handle,
                                 target: Handle,
                                 view: Handle,
                                 radius: f32,
                                 dir: [f32; 2]| {
            let mut bytes = vec![0u8; 64];
            bytes[..4].copy_from_slice(&dir[0].to_le_bytes());
            bytes[4..8].copy_from_slice(&dir[1].to_le_bytes());
            bytes[8..12].copy_from_slice(&radius.to_le_bytes());
            push_uniform_write(&mut ops, uniform, bytes);
            let before = if spider_initialized[output] {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            };
            ops.push(CommandOp::Barrier(ResourceBarrier {
                resource: spider_textures[output],
                subresources: None,
                before,
                after: TextureUsageState::ColorAttachment,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
            ops.extend([
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![PassAttachment {
                        view,
                        load_op: AttachmentLoadOp::DontCare,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(box_pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout: single_layout,
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
                    resource: spider_textures[output],
                    subresources: None,
                    before: TextureUsageState::ColorAttachment,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ]);
            spider_initialized[output] = true;
        };
        append_single(
            single_sets[0],
            2,
            passes[2],
            targets[2],
            views[2],
            15.0,
            [1.0, 0.0],
        );
        append_single(
            single_sets[1],
            0,
            passes[0],
            targets[0],
            views[0],
            15.0,
            [0.0, 1.0],
        );
        append_single(
            single_sets[0],
            2,
            passes[2],
            targets[2],
            views[2],
            7.0,
            [1.0, 0.0],
        );
        append_single(
            single_sets[1],
            1,
            passes[1],
            targets[1],
            views[1],
            7.0,
            [0.0, 1.0],
        );
        drop(append_single);
        let mut append_clip = |set: Handle,
                               output: usize,
                               pass: Handle,
                               target: Handle,
                               view: Handle,
                               scale: [f32; 2],
                               offset: [f32; 2],
                               rotation: f32,
                               scissor: [f32; 4],
                               vignette: [f32; 4]| {
            let mut bytes = vec![0u8; 64];
            for (index, value) in [
                scale[0],
                scale[1],
                offset[0],
                offset[1],
                rotation,
                0.0,
                0.0,
                0.0,
                scissor[0],
                scissor[1],
                scissor[2],
                scissor[3],
                vignette[0],
                vignette[1],
                vignette[2],
                vignette[3],
            ]
            .into_iter()
            .enumerate()
            {
                bytes[index * 4..index * 4 + 4].copy_from_slice(&value.to_le_bytes());
            }
            push_uniform_write(&mut ops, uniform, bytes);
            let before = if spider_initialized[output] {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            };
            ops.push(CommandOp::Barrier(ResourceBarrier {
                resource: spider_textures[output],
                subresources: None,
                before,
                after: TextureUsageState::ColorAttachment,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
            ops.extend([
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![PassAttachment {
                        view,
                        load_op: AttachmentLoadOp::DontCare,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(clip_pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout: dual_layout,
                    set_index: 0,
                    set,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1,
                },
                CommandOp::EndPass,
                CommandOp::Barrier(ResourceBarrier {
                    resource: spider_textures[output],
                    subresources: None,
                    before: TextureUsageState::ColorAttachment,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ]);
            spider_initialized[output] = true;
        };
        let full = [0.0, 0.0, 1.0, 1.0];
        let vignette = [0.1, 0.1, 0.9, 0.9];
        append_clip(
            dual_sets[0],
            2,
            passes[2],
            targets[2],
            views[2],
            [1.25, 2.0],
            [-0.125, -0.1],
            0.0,
            full,
            vignette,
        );
        append_clip(
            dual_sets[1],
            3,
            passes[3],
            targets[3],
            views[3],
            [2.35, 4.2],
            [-1.1, -1.5],
            -45.0,
            [0.21, 0.0, 0.79, 1.0],
            [0.31, 0.1, 0.69, 0.9],
        );
        append_clip(
            dual_sets[2],
            2,
            passes[2],
            targets[2],
            views[2],
            [2.35, 2.35],
            [-0.385, -1.29],
            0.0,
            full,
            vignette,
        );
        let mut blit_bytes = vec![0u8; 64];
        blit_bytes[..4].copy_from_slice(&1.0f32.to_le_bytes());
        blit_bytes[4..8].copy_from_slice(&1.0f32.to_le_bytes());
        blit_bytes[8..12].copy_from_slice(&1.0f32.to_le_bytes());
        blit_bytes[12..16].copy_from_slice(&1.0f32.to_le_bytes());
        push_uniform_write(&mut ops, uniform, blit_bytes);
        ops.extend([
            CommandOp::BeginPass {
                pass: final_pass,
                target: render_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(blit_pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout: single_layout,
                set_index: 0,
                set: single_sets[1],
                dynamic_offsets: Vec::new(),
            },
            CommandOp::Draw {
                vertices: 3,
                instances: 1,
            },
            CommandOp::EndPass,
        ]);
        self.blur_snapshot_initialized = true;
        self.spider_initialized = spider_initialized;
        Ok(ops)
    }
}

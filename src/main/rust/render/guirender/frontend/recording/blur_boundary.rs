//! Recording a GUI frame split around the menu background blur boundary.

use super::*;

impl GuiFrontend {
    pub(crate) fn append_frame_ops_with_owned_atlases_and_blur_boundary(
        &mut self,
        gal: &mut VulkanicGal,
        mut world: Option<&mut (dyn GuiAtlasOwner + 'static)>,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
        boundary_stratum: i32,
        blur_radius: i32,
        pre_present_y_flip: bool,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        // Validate the whole frame before partitioning or creating resources;
        // splitting at blur must not bypass aggregate bounds or hide collisions.
        preflight_tiled_affine_count(&tiled_quads, affine_quads.len())?;
        validate_gui_frame_sequences(&requests, &affine_quads, &mesh_batches, &tiled_quads)?;
        self.preflight_owned_atlas_commands(world.as_deref(), &affine_quads, &tiled_quads)?;
        self.preflight_mesh_atlas_commands(world.as_deref(), &mesh_batches)?;
        let boundary_plan = plan_gui_blur_boundary(boundary_stratum, std::iter::empty())?;
        let threshold = boundary_plan.phase_threshold().map_err(|_| {
            GalError::invalid_argument("GUI blur boundary is outside the bounded semantic range")
        })?;
        if blur_radius < -1 || blur_radius > 64 {
            return Err(GalError::invalid_argument(
                "GUI blur radius must be -1 or within the bounded range 0..=64",
            ));
        }
        let blur_radius = blur_radius.max(0);
        let mut before_requests = Vec::new();
        let mut after_requests = Vec::new();
        for request in requests {
            if request.stratum < threshold {
                before_requests.push(request);
            } else {
                after_requests.push(request);
            }
        }
        let mut before_affine = Vec::new();
        let mut after_affine = Vec::new();
        for request in affine_quads {
            if request.stratum < threshold {
                before_affine.push(request);
            } else {
                after_affine.push(request);
            }
        }
        let mut before_mesh = Vec::new();
        let mut after_mesh = Vec::new();
        for request in mesh_batches {
            if request.stratum < threshold {
                before_mesh.push(request);
            } else {
                after_mesh.push(request);
            }
        }
        let (before_tiled, after_tiled) = tiled_quads
            .into_iter()
            .partition(|request| request.stratum < threshold);
        let (mut ops, mut stats) = self.append_frame_ops_with_owned_atlases_to_target(
            gal,
            world.as_deref_mut(),
            generation,
            render_target,
            color_attachment,
            None,
            None,
            None,
            pre_present_y_flip,
            before_requests,
            before_affine,
            before_mesh,
            before_tiled,
        )?;
        let extent = gal.pass_target_extent(render_target)?;
        let color_format = gal.pass_target_color_format(render_target)?;
        let resources =
            self.ensure_blur_resources(gal, extent.width, extent.height, color_format)?;
        let snapshot = resources.texture;
        let pipeline = resources.pipeline;
        let pipeline_layout = resources.pipeline_layout;
        let resource_set = resources.resource_set;
        let uniform_buffer = resources.uniform_buffer;
        // Reuse the owned fullscreen scratch pool. These images are private
        // GAL resources, not Java targets or borrowed shader-pack state.
        let scratch_textures = resources.spider_textures;
        let scratch_views = resources.spider_views;
        let scratch_targets = resources.spider_targets;
        let scratch_passes = resources.spider_passes;
        let scratch_sets = resources.spider_single_sets;
        let pass = self.frame_pass(gal, render_target, None)?;
        let snapshot_before = if self.blur_snapshot_initialized {
            TextureUsageState::ShaderRead
        } else {
            TextureUsageState::Undefined
        };
        ops.push(CommandOp::Barrier(ResourceBarrier {
            resource: snapshot,
            subresources: None,
            before: snapshot_before,
            after: TextureUsageState::TransferDst,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Transfer,
        }));
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
        // Frozen minecraft:blur is three horizontal/vertical box-blur pairs.
        // Ping-pong owned scratch images, writing the final pass to the frame.
        let mut initialized = self.spider_initialized;
        for index in 0..6 {
            let final_pass = index == 5;
            let output = if index % 2 == 0 { 2 } else { 3 };
            let source_set = if index == 0 {
                resource_set
            } else if index % 2 == 1 {
                scratch_sets[1]
            } else {
                scratch_sets[3]
            };
            let mut bytes = vec![0u8; 64];
            let direction: [f32; 2] = if index % 2 == 0 {
                [1.0, 0.0]
            } else {
                [0.0, 1.0]
            };
            bytes[..4].copy_from_slice(&direction[0].to_le_bytes());
            bytes[4..8].copy_from_slice(&direction[1].to_le_bytes());
            bytes[8..12].copy_from_slice(&(blur_radius as f32).to_le_bytes());
            if index > 0 || self.blur_snapshot_initialized {
                ops.push(CommandOp::Barrier(buffer_barrier(
                    uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
            }
            push_uniform_write(&mut ops, uniform_buffer, bytes);
            if !final_pass {
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: scratch_textures[output],
                    subresources: None,
                    before: if initialized[output] {
                        TextureUsageState::ShaderRead
                    } else {
                        TextureUsageState::Undefined
                    },
                    after: TextureUsageState::ColorAttachment,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }));
            }
            ops.extend([
                CommandOp::BeginPass {
                    pass: if final_pass {
                        pass
                    } else {
                        scratch_passes[output]
                    },
                    target: if final_pass {
                        render_target
                    } else {
                        scratch_targets[output]
                    },
                    colors: vec![if final_pass {
                        loaded_frame_color_attachment(color_attachment)
                    } else {
                        PassAttachment {
                            view: scratch_views[output],
                            load_op: AttachmentLoadOp::DontCare,
                            store_op: AttachmentStoreOp::Store,
                            clear_color: None,
                        }
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set: source_set,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1,
                },
                CommandOp::EndPass,
            ]);
            if !final_pass {
                ops.push(CommandOp::Barrier(ResourceBarrier {
                    resource: scratch_textures[output],
                    subresources: None,
                    before: TextureUsageState::ColorAttachment,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }));
                initialized[output] = true;
            }
        }
        self.spider_initialized = initialized;
        let (after_ops, after_stats) = self.append_frame_ops_with_owned_atlases_to_target(
            gal,
            world,
            generation,
            render_target,
            color_attachment,
            None,
            None,
            None,
            false,
            after_requests,
            after_affine,
            after_mesh,
            after_tiled,
        )?;
        ops.extend(after_ops);
        stats.sprite_count = stats.sprite_count.saturating_add(after_stats.sprite_count);
        stats.affine_quad_count = stats
            .affine_quad_count
            .saturating_add(after_stats.affine_quad_count);
        stats.mesh_item_count = stats
            .mesh_item_count
            .saturating_add(after_stats.mesh_item_count);
        stats.mesh_batch_count = stats
            .mesh_batch_count
            .saturating_add(after_stats.mesh_batch_count);
        stats.mesh_draw_count = stats
            .mesh_draw_count
            .saturating_add(after_stats.mesh_draw_count);
        stats.entity_preview_item_count = stats
            .entity_preview_item_count
            .saturating_add(after_stats.entity_preview_item_count);
        stats.entity_preview_batch_count = stats
            .entity_preview_batch_count
            .saturating_add(after_stats.entity_preview_batch_count);
        stats.entity_preview_draw_count = stats
            .entity_preview_draw_count
            .saturating_add(after_stats.entity_preview_draw_count);
        stats.entity_preview_material_mask |= after_stats.entity_preview_material_mask;
        stats.entity_preview_vertex_count = stats
            .entity_preview_vertex_count
            .saturating_add(after_stats.entity_preview_vertex_count);
        stats.entity_preview_index_count = stats
            .entity_preview_index_count
            .saturating_add(after_stats.entity_preview_index_count);
        stats.sprite_batch_count = stats
            .sprite_batch_count
            .saturating_add(after_stats.sprite_batch_count);
        stats.cache_hits = stats.cache_hits.saturating_add(after_stats.cache_hits);
        stats.cache_misses = stats.cache_misses.saturating_add(after_stats.cache_misses);
        stats.resource_creates = stats
            .resource_creates
            .saturating_add(after_stats.resource_creates);
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        stats
            .owned_intermediate_targets
            .extend(after_stats.owned_intermediate_targets);
        stats
            .transient_diagnostic_passes
            .extend(after_stats.transient_diagnostic_passes);
        self.blur_snapshot_initialized = true;
        Ok((ops, stats))
    }
}

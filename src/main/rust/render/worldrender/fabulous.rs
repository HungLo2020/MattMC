//! Fabulous transparency: terrain handoff, overlays and the oriented material frame.

use super::*;

pub(super) type FabulousFrameBlitResources = (
    (Handle, Handle, Handle, Handle),
    Handle,
    Handle,
    Handle,
    Handle,
    Handle,
    Handle,
    Handle,
);

pub(super) fn destroy_fabulous_frame_blit_resources(
    gal: &mut VulkanicGal,
    resources: &FabulousFrameBlitResources,
) -> GalResult<()> {
    let ((resource_set, combined, sampler, pass), _, _, _, _, _, _, _) = resources;
    gal.destroy(*pass)?;
    gal.destroy(*resource_set)?;
    gal.destroy(*combined)?;
    gal.destroy(*sampler)?;
    Ok(())
}

pub(super) fn cleanup_fabulous_frame_blit_resources(
    gal: &mut VulkanicGal,
    resources: &mut Option<FabulousFrameBlitResources>,
) {
    if let Some(resources) = resources.take() {
        // Cleanup is best-effort on an error path: preserve the original
        // lowering/submission diagnostic while still releasing every private
        // object allocated for this frame.
        let _ = destroy_fabulous_frame_blit_resources(gal, &resources);
    }
}

impl WorldPrimitiveFrontend {
    pub(super) fn fabulous_material_frame_content_is_supported(frame: &WorldPrimitiveFrame) -> bool {
        frame.segments.is_empty()
            && frame.crack_quads.is_empty()
            && frame.border_quads.is_empty()
            && frame.lod_instances.is_empty()
            && !frame
                .mesh_instances
                .iter()
                .any(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
    }

    pub(super) fn fabulous_material_role(
        material_id: u32,
        mode: u32,
        source: u32,
    ) -> crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole {
        use crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole;
        // Celestial overlays need the actual sky destination for their blend.
        if mode != WORLD_MATERIAL_MODE_TRANSLUCENT
            || matches!(
                material_id,
                WORLD_MATERIAL_ID_CELESTIAL | WORLD_MATERIAL_ID_SKY_STARS
            )
        {
            FabulousTargetRole::Main
        } else {
            FabulousTargetRole::for_material_source(source)
                .expect("translucent source families validated before routing")
        }
    }

    pub(super) fn submit_fabulous_material_frame_oriented(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        mut frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
        fabulous_attachments_initialized: bool,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        self.world_text.begin_submission();
        self.expand_static_terrain(&mut frame)?;
        validate_frame(&frame)?;
        if self.generation == 0 {
            self.generation = generation;
        }
        let color_format = gal.pass_target_color_format(frame_target)?;
        let batches = material_batches(&frame, color_format, raster_y_direction);
        let world_mesh_batches =
            mesh_batches(&frame, self, color_format, raster_y_direction, false, false)?;
        let draw_sky_disc = vanilla_sky_disc_required_for_frame(&frame, true, true);
        if draw_sky_disc {
            self.ensure_sky_disc_forward_resources(gal, color_format, raster_y_direction)?;
        }
        let normalize_output = raster_y_direction != RasterYDirection::Up;
        let acquired_output = frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget);
        let normalized_images = if normalize_output {
            let desc = passes::oriented_target::WorldTargetDesc {
                extent: gal.pass_target_extent(frame_target)?,
                color_format,
                raster_y_direction: RasterYDirection::Up,
            };
            if acquired_output {
                if !self
                    .canonical_world_target
                    .as_ref()
                    .is_some_and(|owner| owner.desc == desc)
                {
                    let replacement = passes::oriented_target::OrientedWorldTarget::create(
                        gal,
                        "minecraft.world.canonical-output",
                        desc,
                    )?;
                    if let Some(old) = self.canonical_world_target.replace(replacement) {
                        self.clear_frame_passes_for_targets(gal, &[old.target]);
                        for handle in old.handles_in_destroy_order() {
                            gal.destroy(handle)?;
                        }
                    }
                }
                Some(
                    self.canonical_world_target
                        .as_ref()
                        .expect("canonical output prepared")
                        .images(),
                )
            } else {
                let (depth_texture, _) = gal
                    .pass_target_depth_attachment(frame_target)?
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "oriented Fabulous output requires an explicit depth attachment",
                        )
                    })?;
                Some(passes::oriented_target::WorldAttachmentImages {
                    desc,
                    target: frame_target,
                    color_texture: gal.pass_target_color_texture(frame_target)?,
                    depth_texture,
                })
            }
        } else {
            None
        };
        // First-person meshes use the copied hand projection/model-view and a
        // fresh depth domain. Keep them in a separate semantic frame so they
        // cannot coalesce with camera-space entity batches or inherit the
        // world matrices from the ordinary stream.
        let hand_frame = if frame.first_person_mesh_instances.is_empty() {
            None
        } else {
            if !frame.first_person.enabled || !frame.first_person.clear_depth_before {
                return Err(GalError::invalid_argument(
                    "Fabulous first-person meshes require an enabled hand frame with an explicit depth clear",
                ));
            }
            let mut hand_frame = frame.clone();
            hand_frame.view_matrix = frame.first_person.model_view_matrix;
            hand_frame.projection_matrix = frame.first_person.projection_matrix;
            hand_frame.mesh_instances = frame.first_person_mesh_instances.clone();
            hand_frame.first_person_mesh_instances.clear();
            hand_frame.material_quads.clear();
            hand_frame.text_quads.clear();
            Some(hand_frame)
        };
        let hand_mesh_batches = hand_frame
            .as_ref()
            .map(|hand_frame| {
                mesh_batches(
                    hand_frame,
                    self,
                    color_format,
                    raster_y_direction,
                    false,
                    true,
                )
            })
            .transpose()?;
        let hand_optical_batch_indices = hand_mesh_batches
            .as_ref()
            .map(|batches| {
                batches
                    .iter()
                    .enumerate()
                    .filter_map(|(index, batch)| {
                        (batch.key.material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                            || batch.key.material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST)
                            .then_some(index)
                    })
                    .collect::<Vec<_>>()
            })
            .unwrap_or_default();
        let mut fabulous_slot_counts: BTreeMap<MaterialResourceKey, usize> = BTreeMap::new();
        for batch in &batches {
            self.ensure_material_resources(gal, batch.key)?;
            *fabulous_slot_counts.entry(batch.key).or_insert(0) += 1;
        }
        // A key split into several 4,096-quad batches (for example clouds at
        // the default cloud range) needs one data slot per batch.
        for (key, count) in fabulous_slot_counts {
            self.ensure_material_resource_slots(gal, key, count)?;
        }
        let (mesh_stream_payload, mesh_stream_offsets, hand_stream_offsets, mesh_stream_buffer) =
            if world_mesh_batches.is_empty() {
                if let Some(hand_mesh_batches) = hand_mesh_batches.as_ref() {
                    let required = required_mesh_instance_stream_bytes(hand_mesh_batches)?;
                    let (payload, offsets) = packed_mesh_uniforms_for_batches(
                        hand_frame.as_ref().expect("hand frame for hand batches"),
                        self,
                        hand_mesh_batches,
                        required,
                    )?;
                    let stream = self.ensure_mesh_instance_stream(gal, required)?;
                    if stream.grew {
                        self.invalidate_mesh_instance_stream_bindings(gal);
                    }
                    (payload, Vec::new(), offsets, Some(stream.buffer))
                } else {
                    (Vec::new(), Vec::new(), Vec::new(), None)
                }
            } else {
                let normal_required = required_mesh_instance_stream_bytes(&world_mesh_batches)?;
                let hand_required = hand_mesh_batches
                    .as_ref()
                    .map(|batches| required_mesh_instance_stream_bytes(batches))
                    .transpose()?
                    .unwrap_or(0);
                let hand_base =
                    align_up_u64(normal_required, WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64)?;
                let required = hand_base.checked_add(hand_required).ok_or_else(|| {
                    GalError::invalid_argument("Fabulous mesh stream byte count overflow")
                })?;
                let stream = self.ensure_mesh_instance_stream(gal, required)?;
                if stream.grew {
                    self.invalidate_mesh_instance_stream_bindings(gal);
                }
                let (payload, offsets) =
                    packed_mesh_uniforms_for_batches(&frame, self, &world_mesh_batches, required)?;
                let mut hand_offsets = Vec::new();
                let mut combined_payload = payload;
                if let Some(hand_mesh_batches) = hand_mesh_batches.as_ref() {
                    let (hand_payload, offsets) = packed_mesh_uniforms_for_batches(
                        hand_frame.as_ref().expect("hand frame for hand batches"),
                        self,
                        hand_mesh_batches,
                        hand_required,
                    )?;
                    let base = align_up_u64(
                        combined_payload.len() as u64,
                        WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64,
                    )? as usize;
                    combined_payload.resize(base, 0);
                    combined_payload.extend_from_slice(&hand_payload);
                    hand_offsets = offsets
                        .into_iter()
                        .map(|offset| offset + base as u64)
                        .collect();
                } else {
                }
                (combined_payload, offsets, hand_offsets, Some(stream.buffer))
            };
        for batch in &world_mesh_batches {
            self.ensure_mesh_resources(gal, batch.key)?;
        }
        if let Some(hand_mesh_batches) = hand_mesh_batches.as_ref() {
            for batch in hand_mesh_batches {
                self.ensure_mesh_resources(gal, batch.key)?;
            }
        }
        // Fabulous is a separate explicit composition target, not a separate
        // terrain shader contract. Its builtin mesh pipelines use the same
        // Rust-owned copied vanilla lightmap as the normal terrain route.
        let fabulous_meshes_present = !world_mesh_batches.is_empty()
            || hand_mesh_batches
                .as_ref()
                .is_some_and(|batches| !batches.is_empty());
        let fabulous_lightmap_required = fabulous_meshes_present
            || batches
                .iter()
                .any(|batch| material_uses_lightmap(batch.key));
        let builtin_terrain_lightmap_layout = if fabulous_lightmap_required {
            if frame.shader_environment.world_generation == 0
                || frame.shader_environment.vanilla_lightmap.is_none()
            {
                return Err(GalError::unsupported_feature(
                    "Rust Fabulous indexed meshes, weather and particles require copied vanilla lightmap semantics",
                ));
            }
            self.ensure_shader_runtime(gal, self.generation)?;
            let lightmap_layout = self.ensure_builtin_terrain_lightmap_layout(gal)?;
            let runtime = self
                .shader_runtime
                .as_mut()
                .expect("shader runtime is installed before Fabulous lightmap staging");
            runtime.observe_vanilla_lightmap(
                frame.shader_environment.world_generation,
                frame.shader_environment.vanilla_lightmap,
            )?;
            Some(lightmap_layout)
        } else {
            None
        };
        // Prepare the outline against the Rust-owned main attachment. This
        // records commands only; the enclosing frame remains the sole submitter.
        // The enclosing dedicated Fabulous submission owns this complete chain.
        let outline_plan = features::outline::prepare_entity_outline_post_effect(&frame)?;
        let mut outline_operations = Vec::new();
        if let Some(plan) = outline_plan.as_ref() {
            let main = self
                .fabulous_attachment_set
                .as_ref()
                .ok_or_else(|| {
                    GalError::backend("Fabulous main attachment missing for outline preparation")
                })?
                .main;
            self.ensure_entity_outline_target_resources_with_depth(
                gal,
                frame.viewport_width,
                frame.viewport_height,
                color_format,
                Some(main.depth_view),
            )?;
            self.prepare_entity_outline_mask_gpu_resources(
                gal,
                &frame,
                color_format,
                raster_y_direction,
            )?;
            self.ensure_entity_outline_post_effect_resource_sets(gal, color_format)?;
            let composite_pass = self.color_only_frame_pass(gal, main.render_target)?;
            let targets = self
                .entity_outline_targets
                .as_ref()
                .expect("prepared outline targets");
            let pipelines = self
                .entity_outline_post_effect_pipelines
                .as_ref()
                .expect("prepared outline pipelines");
            let sets = self
                .entity_outline_post_effect_sets
                .as_ref()
                .expect("prepared outline bindings");
            let prior = if self.entity_outline_targets_initialized {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            };
            outline_operations.extend(features::outline::lower_entity_outline_mask_pass(
                targets,
                self.entity_outline_mask_gpu
                    .as_ref()
                    .expect("prepared outline mesh"),
                main.depth_view,
                TextureUsageState::DepthStencilAttachment,
                TextureUsageState::DepthStencilAttachment,
                prior,
            )?);
            outline_operations.extend(features::outline::lower_entity_outline_post_effect_with_resources(
                plan,
                targets,
                pipelines,
                sets,
                composite_pass,
                main.render_target,
                main.color_view,
                pipelines.composite_depthless_pipeline,
                prior,
                prior,
                prior,
            )?);
        }
        let mut slot_indices = BTreeMap::<MaterialResourceKey, usize>::new();
        let set = self.fabulous_attachment_set.as_ref().ok_or_else(|| {
            GalError::backend("Fabulous attachment set vanished before material routing")
        })?;
        let mut operations = Vec::with_capacity(batches.len() * 12 + 64);
        if draw_sky_disc {
            let sky = &self.sky_disc_forward_resources[&(color_format, raster_y_direction)];
            operations.push(CommandOp::Barrier(buffer_barrier(
                sky.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer: sky.uniform_buffer,
                offset: 0,
                data: packed_sky_disc_uniforms(&frame),
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                sky.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }

        let builtin_terrain_lightmap_resource_set = if let Some(lightmap_layout) =
            builtin_terrain_lightmap_layout
        {
            let runtime = self
                .shader_runtime
                .as_mut()
                .expect("shader runtime remains installed during Fabulous lightmap upload");
            runtime.stage_vanilla_lightmap_residency(gal, &mut operations)?;
            let resource_set = runtime
                    .vanilla_lightmap_resource_set(gal, lightmap_layout, true)?
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                        "Rust Fabulous indexed meshes, weather and particles require a staged copied vanilla lightmap",
                        )
                    })?;
            Some(TerrainShaderResourceSet {
                set_index: 1,
                set: resource_set.set,
            })
        } else {
            None
        };

        // Upload every per-batch semantic record before entering any external
        // attachment pass.  The material resource set remains the same
        // explicit GAL descriptor used by the ordinary Rust material path.
        for batch in &batches {
            let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                GalError::backend("Fabulous material resources vanished before routing")
            })?;
            let slot_index = slot_indices.entry(batch.key).or_insert(0);
            let slot = resources.data_slots.get(*slot_index).ok_or_else(|| {
                GalError::backend("Fabulous material data slot missing before routing")
            })?;
            *slot_index += 1;
            operations.push(CommandOp::Barrier(buffer_barrier(
                slot.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer: slot.uniform_buffer,
                offset: 0,
                data: packed_material_uniforms_for_batch(&frame, batch)?,
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                slot.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        if let Some(buffer) = mesh_stream_buffer {
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer,
                offset: 0,
                data: mesh_stream_payload,
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }

        let role_for_batch = |batch: &MaterialBatch| {
            Self::fabulous_material_role(
                batch.key.material_id,
                batch.key.material_mode,
                batch.key.source_program,
            )
        };
        let main_clear_color = if frame.background.enabled {
            // Frozen's LevelRenderer clears the main target with the fog
            // colour before SkyRenderer draws its finite sky disc.  The disc
            // gets its separate biome/time sky colour through its own
            // uniform; using that colour for this clear leaves a visible
            // boundary outside the fan.
            background_clear_color(&frame.background)
        } else {
            ClearColor {
                r: 0.0,
                g: 0.0,
                b: 0.0,
                a: 0.0,
            }
        };
        for role in [
            crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Main,
            crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Translucent,
            crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::ItemEntity,
            crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Particles,
            crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Clouds,
            crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Weather,
        ] {
            let attachment = match role {
                crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Main => &set.main,
                crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Translucent => {
                    &set.translucent
                }
                crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::ItemEntity => {
                    &set.item_entity
                }
                crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Particles => {
                    &set.particles
                }
                crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Clouds => &set.clouds,
                crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Weather => &set.weather,
            };
            operations.push(CommandOp::Barrier(texture_barrier(
                attachment.color_texture,
                if fabulous_attachments_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::ColorAttachment,
            )));
            operations.push(CommandOp::Barrier(texture_barrier(
                attachment.depth_texture,
                if fabulous_attachments_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::DepthStencilAttachment,
            )));
            operations.push(CommandOp::BeginPass {
                pass: attachment.render_pass,
                target: attachment.render_target,
                colors: vec![PassAttachment {
                    view: attachment.color_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(
                        if role == crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Main {
                            main_clear_color
                        } else {
                            ClearColor {
                                r: 0.0,
                                g: 0.0,
                                b: 0.0,
                                a: 0.0,
                            }
                        },
                    ),
                }],
                depth_stencil: Some(PassAttachment {
                    view: attachment.depth_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            let mut role_slots = BTreeMap::<MaterialResourceKey, usize>::new();
            if role == crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Main
                && draw_sky_disc
            {
                let sky = &self.sky_disc_forward_resources[&(color_format, raster_y_direction)];
                operations.push(CommandOp::BindGraphicsPipeline(sky.pipeline));
                operations.push(CommandOp::BindResourceSet {
                    pipeline_layout: sky.pipeline_layout,
                    set_index: 0,
                    set: sky.resource_set,
                    dynamic_offsets: Vec::new(),
                });
                operations.push(CommandOp::Draw {
                    vertices: 10,
                    instances: 1,
                });
            }
            for batch in batches.iter().filter(|batch| role_for_batch(batch) == role) {
                let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("Fabulous material resources vanished during pass lowering")
                })?;
                let slot_index = role_slots.entry(batch.key).or_insert(0);
                let slot = resources.data_slots.get(*slot_index).ok_or_else(|| {
                    GalError::backend("Fabulous material slot vanished during pass lowering")
                })?;
                *slot_index += 1;
                operations.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
                operations.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: 0,
                    set: slot.resource_set,
                    dynamic_offsets: Vec::new(),
                });
                if resources.lightmap_resource_layout.is_some() {
                    let lightmap = builtin_terrain_lightmap_resource_set.ok_or_else(|| {
                        GalError::backend("Fabulous lightmapped material pipeline has no staged Rust lightmap binding")
                    })?;
                    operations.push(CommandOp::BindResourceSet {
                        pipeline_layout: resources.pipeline_layout,
                        set_index: lightmap.set_index,
                        set: lightmap.set,
                        dynamic_offsets: Vec::new(),
                    });
                }
                operations.push(CommandOp::SetIndexBuffer {
                    buffer: resources.index_buffer,
                    offset: 0,
                    index_type: IndexType::U32,
                });
                operations.push(CommandOp::DrawIndexed {
                    indices: 6,
                    instances: batch.count() as u32,
                });
            }
            for (batch_index, batch) in
                world_mesh_batches.iter().enumerate().filter(|(_, batch)| {
                    if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH {
                        role == crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::ItemEntity
                    } else if material_mode_uses_alpha_blending(batch.key.material_mode) {
                        role
                            == crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Translucent
                    } else {
                        role == crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Main
                    }
                })
            {
                let resources = self.mesh_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("Fabulous mesh resources vanished during pass lowering")
                })?;
                let asset = self.mesh_assets.get(&batch.key.mesh_key).ok_or_else(|| {
                    GalError::backend("Fabulous mesh asset vanished during pass lowering")
                })?;
                operations.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
                operations.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: 0,
                    set: resources.resource_set,
                    dynamic_offsets: mesh_stream_dynamic_offsets(
                        batch,
                        resources.vertex_offset,
                        mesh_stream_offsets[batch_index],
                    )?,
                });
                let lightmap_resource_set = builtin_terrain_lightmap_resource_set
                    .expect("Fabulous world mesh draw has a copied vanilla lightmap binding");
                operations.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: lightmap_resource_set.set_index,
                    set: lightmap_resource_set.set,
                    dynamic_offsets: Vec::new(),
                });
                operations.push(CommandOp::SetIndexBuffer {
                    buffer: resources.index_buffer,
                    offset: resources
                        .index_offset
                        .checked_add(batch.index_offset)
                        .ok_or_else(|| {
                            GalError::invalid_argument("Fabulous mesh index offset overflow")
                        })?,
                    index_type: asset.index_type,
                });
                operations.push(CommandOp::DrawIndexed {
                    indices: batch.index_count,
                    instances: batch.count() as u32,
                });
            }
            operations.push(CommandOp::EndPass);
            if role == crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Main {
                if let (Some(hand_frame), Some(hand_mesh_batches), Some(_)) = (
                    hand_frame.as_ref(),
                    hand_mesh_batches.as_ref(),
                    mesh_stream_buffer,
                ) {
                    if !hand_mesh_batches.is_empty() {
                        if !hand_optical_batch_indices.is_empty() {
                            operations.extend(set.optical_hand_copy_from_main(Extent3d {
                                width: frame.viewport_width,
                                height: frame.viewport_height,
                                depth: 1,
                            }));
                            operations.push(CommandOp::Barrier(texture_barrier(
                                set.optical_hand.depth_texture,
                                TextureUsageState::Undefined,
                                TextureUsageState::DepthStencilAttachment,
                            )));
                            operations.push(CommandOp::BeginPass {
                                pass: set.optical_hand.render_pass,
                                target: set.optical_hand.render_target,
                                colors: vec![PassAttachment {
                                    view: set.optical_hand.color_view,
                                    load_op: AttachmentLoadOp::Load,
                                    store_op: AttachmentStoreOp::Store,
                                    clear_color: None,
                                }],
                                depth_stencil: Some(PassAttachment {
                                    view: set.optical_hand.depth_view,
                                    load_op: AttachmentLoadOp::Clear,
                                    store_op: AttachmentStoreOp::Store,
                                    clear_color: None,
                                }),
                            });
                            for batch_index in &hand_optical_batch_indices {
                                let batch = &hand_mesh_batches[*batch_index];
                                let resources = self.mesh_resources.get(&batch.key).ok_or_else(|| {
                                    GalError::backend(
                                        "Fabulous optical hand mesh resources vanished before lowering",
                                    )
                                })?;
                                let asset =
                                    self.mesh_assets.get(&batch.key.mesh_key).ok_or_else(|| {
                                        GalError::backend(
                                        "Fabulous optical hand mesh asset vanished before lowering",
                                    )
                                    })?;
                                operations
                                    .push(CommandOp::BindGraphicsPipeline(resources.pipeline));
                                operations.push(CommandOp::BindResourceSet {
                                    pipeline_layout: resources.pipeline_layout,
                                    set_index: 0,
                                    set: resources.resource_set,
                                    dynamic_offsets: mesh_stream_dynamic_offsets(
                                        batch,
                                        resources.vertex_offset,
                                        hand_stream_offsets[*batch_index],
                                    )?,
                                });
                                let lightmap_resource_set = builtin_terrain_lightmap_resource_set
                                    .expect("Fabulous optical hand mesh draw has a copied vanilla lightmap binding");
                                operations.push(CommandOp::BindResourceSet {
                                    pipeline_layout: resources.pipeline_layout,
                                    set_index: lightmap_resource_set.set_index,
                                    set: lightmap_resource_set.set,
                                    dynamic_offsets: Vec::new(),
                                });
                                operations.push(CommandOp::SetIndexBuffer {
                                    buffer: resources.index_buffer,
                                    offset: resources
                                        .index_offset
                                        .checked_add(batch.index_offset)
                                        .ok_or_else(|| {
                                            GalError::invalid_argument(
                                                "Fabulous hand mesh index offset overflow",
                                            )
                                        })?,
                                    index_type: asset.index_type,
                                });
                                operations.push(CommandOp::DrawIndexed {
                                    indices: batch.index_count,
                                    instances: batch.count() as u32,
                                });
                            }
                            operations.push(CommandOp::EndPass);
                            operations.extend(set.optical_hand_copy_to_main(Extent3d {
                                width: frame.viewport_width,
                                height: frame.viewport_height,
                                depth: 1,
                            }));
                        }
                        // The world color attachment is loaded, while its
                        // depth domain is explicitly cleared for the copied
                        // first-person projection. This is the hand/world
                        // boundary; no implicit Vulkan state is inherited.
                        operations.push(CommandOp::BeginPass {
                            pass: attachment.render_pass,
                            target: attachment.render_target,
                            colors: vec![PassAttachment {
                                view: attachment.color_view,
                                load_op: AttachmentLoadOp::Load,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: None,
                            }],
                            depth_stencil: Some(PassAttachment {
                                view: attachment.depth_view,
                                load_op: if hand_frame.first_person.clear_depth_before {
                                    AttachmentLoadOp::Clear
                                } else {
                                    AttachmentLoadOp::Load
                                },
                                store_op: AttachmentStoreOp::Store,
                                clear_color: None,
                            }),
                        });
                        for (batch_index, batch) in hand_mesh_batches.iter().enumerate() {
                            if hand_optical_batch_indices.contains(&batch_index) {
                                continue;
                            }
                            let resources = self.mesh_resources.get(&batch.key).ok_or_else(|| {
                                GalError::backend(
                                    "Fabulous first-person mesh resources vanished during pass lowering",
                                )
                            })?;
                            let asset = self.mesh_assets.get(&batch.key.mesh_key).ok_or_else(|| {
                                GalError::backend(
                                    "Fabulous first-person mesh asset vanished during pass lowering",
                                )
                            })?;
                            operations.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
                            operations.push(CommandOp::BindResourceSet {
                                pipeline_layout: resources.pipeline_layout,
                                set_index: 0,
                                set: resources.resource_set,
                                dynamic_offsets: mesh_stream_dynamic_offsets(
                                    batch,
                                    resources.vertex_offset,
                                    hand_stream_offsets[batch_index],
                                )?,
                            });
                            let lightmap_resource_set = builtin_terrain_lightmap_resource_set
                                .expect(
                                    "Fabulous hand mesh draw has a copied vanilla lightmap binding",
                                );
                            operations.push(CommandOp::BindResourceSet {
                                pipeline_layout: resources.pipeline_layout,
                                set_index: lightmap_resource_set.set_index,
                                set: lightmap_resource_set.set,
                                dynamic_offsets: Vec::new(),
                            });
                            operations.push(CommandOp::SetIndexBuffer {
                                buffer: resources.index_buffer,
                                offset: resources
                                    .index_offset
                                    .checked_add(batch.index_offset)
                                    .ok_or_else(|| {
                                        GalError::invalid_argument(
                                            "Fabulous hand mesh index offset overflow",
                                        )
                                    })?,
                                index_type: asset.index_type,
                            });
                            operations.push(CommandOp::DrawIndexed {
                                indices: batch.index_count,
                                instances: batch.count() as u32,
                            });
                        }
                        operations.push(CommandOp::EndPass);
                    }
                }
            }
        }
        operations.extend(set.external_shader_read_barriers());
        operations.push(set.final_target_color_attachment_barrier());
        let executor =
            crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_executor()?;
        let populated_roles = BTreeSet::from([
            "minecraft:main".to_owned(),
            "minecraft:translucent".to_owned(),
            "minecraft:item_entity".to_owned(),
            "minecraft:particles".to_owned(),
            "minecraft:clouds".to_owned(),
            "minecraft:weather".to_owned(),
        ]);
        set.external_inventory()
            .validate_populated_for_plan(executor.plan(), &populated_roles)?;
        let has_main_overlays =
            !frame.text_quads.is_empty() || outline_plan.is_some() || normalize_output;
        let (post_bindings, presentation_pass) = if has_main_overlays {
            (set.transparency_pass_bindings()?, Handle::NULL)
        } else {
            set.transparency_pass_bindings_to_frame_target(gal, frame_target)?
        };
        // This frame-local pass is not in a frontend cache. Keep every
        // subsequent early return inside the transaction so rejection also
        // releases its dependency on the acquired frame target.
        let result = (|| -> GalResult<WorldPrimitiveSubmitStats> {
            let mut final_blit_resources = if has_main_overlays && !normalize_output {
                let pipelines = set
                    .pipelines
                    .as_ref()
                    .ok_or_else(|| GalError::backend("Fabulous pipelines are not initialized"))?;
                Some((
                    set.create_main_to_frame_blit_resources(gal, frame_target)?,
                    pipelines.blit_frame_pipeline,
                    pipelines.blit_pipeline_layout,
                    set.main.render_target,
                    set.main.render_pass,
                    set.main.color_view,
                    set.main.depth_texture,
                    set.main.depth_view,
                ))
            } else {
                None
            };
            let blit_uniform_buffer = set
                .bindings
                .as_ref()
                .expect("Fabulous bindings")
                .blit_uniform_buffer;
            operations.push(CommandOp::Barrier(buffer_barrier(
                blit_uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer: blit_uniform_buffer,
                offset: 0,
                data: vec![0, 0, 128, 63, 0, 0, 128, 63, 0, 0, 128, 63, 0, 0, 128, 63],
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                blit_uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            let mut post_operations = match executor.lower(&post_bindings) {
                Ok(operations) => operations,
                Err(error) => {
                    self.world_text.cancel_submission();
                    cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                    return Err(error);
                }
            };
            let first_pass_end = post_operations
                .iter()
                .position(|operation| matches!(operation, CommandOp::EndPass))
                .ok_or_else(|| {
                    GalError::backend(
                        "Fabulous transparency lowering omitted its first pass terminator",
                    )
                });
            let first_pass_end = match first_pass_end {
                Ok(index) => index,
                Err(error) => {
                    self.world_text.cancel_submission();
                    cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                    return Err(error);
                }
            };
            post_operations.insert(
                first_pass_end + 1,
                set.between_transparency_pass_barriers()
                    .into_iter()
                    .next()
                    .expect("Fabulous graph has one intermediate transition"),
            );
            if has_main_overlays {
                let second_pass_begin = first_pass_end + 2;
                post_operations.insert(
                    second_pass_begin,
                    CommandOp::Barrier(texture_barrier(
                        set.main.color_texture,
                        TextureUsageState::ShaderRead,
                        TextureUsageState::ColorAttachment,
                    )),
                );
                post_operations.insert(
                    second_pass_begin + 1,
                    CommandOp::Barrier(texture_barrier(
                        set.main.depth_texture,
                        TextureUsageState::ShaderRead,
                        TextureUsageState::DepthStencilAttachment,
                    )),
                );
                let second_pass_end = post_operations
                    .iter()
                    .rposition(|operation| matches!(operation, CommandOp::EndPass))
                    .ok_or_else(|| {
                        GalError::backend(
                            "Fabulous transparency lowering omitted its final pass terminator",
                        )
                    });
                let second_pass_end = match second_pass_end {
                    Ok(index) => index,
                    Err(error) => {
                        self.world_text.cancel_submission();
                        cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                        return Err(error);
                    }
                };
                post_operations.insert(
                    second_pass_end + 1,
                    CommandOp::Barrier(texture_barrier(
                        set.main.color_texture,
                        TextureUsageState::ColorAttachment,
                        TextureUsageState::ShaderRead,
                    )),
                );
                // Main depth was explicitly transitioned for the overlays above.
                // The depthless transparency pass does not change that state;
                // outline/text consume it in attachment usage and the final blit
                // boundary below restores ShaderRead.
            }
            operations.extend(post_operations);
            let mut text_stats = None;
            if has_main_overlays {
                // The fabulous graph leaves the main color image shader-readable
                // after its transparency pass.  World text renders into that
                // image as a color attachment, so make the ownership/layout
                // transition explicit before opening the text pass.
                operations.push(CommandOp::Barrier(texture_barrier(
                    set.main.color_texture,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::ColorAttachment,
                )));
                // Transparency is complete; preserve it while blending the outline,
                // then draw world text. Fullscreen outline stages use image
                // coordinates and do not change the main depth attachment.
                operations.append(&mut outline_operations);
                let text_result = self.world_text.append_frame_ops(
                    gal,
                    set.main.render_target,
                    set.main.render_pass,
                    set.main.color_view,
                    set.main.depth_texture,
                    set.main.depth_view,
                    // The Fabulous text target is already left in depth-attachment
                    // layout by its preceding terrain pass.  Passing ShaderRead
                    // here would request an invalid reverse transition before the
                    // text pass begins.
                    TextureUsageState::DepthStencilAttachment,
                    color_format,
                    raster_y_direction,
                    frame.view_matrix,
                    frame.projection_matrix,
                    &frame.text_quads,
                    &mut operations,
                    false,
                );
                let text_result = match text_result {
                    Ok(stats) => stats,
                    Err(error) => {
                        self.world_text.cancel_submission();
                        cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                        return Err(error);
                    }
                };
                text_stats = Some(text_result);
                operations.push(CommandOp::Barrier(texture_barrier(
                    set.main.color_texture,
                    TextureUsageState::ColorAttachment,
                    TextureUsageState::ShaderRead,
                )));
                // Text's depth-tested pass leaves the main depth image in
                // attachment layout.  The final blit samples it, therefore the
                // post-text transition must be emitted by this caller.
                operations.push(CommandOp::Barrier(texture_barrier(
                    set.main.depth_texture,
                    TextureUsageState::DepthStencilAttachment,
                    TextureUsageState::ShaderRead,
                )));
            }
            if let Some(destination) = normalized_images {
                let source = passes::oriented_target::WorldAttachmentImages {
                    desc: passes::oriented_target::WorldTargetDesc {
                        extent: set.main.extent,
                        color_format,
                        raster_y_direction,
                    },
                    target: set.main.render_target,
                    color_texture: set.main.color_texture,
                    depth_texture: set.main.depth_texture,
                };
                operations.extend(source.transfer_to(
                    destination,
                    passes::oriented_target::WorldAttachmentStates {
                        color: TextureUsageState::ShaderRead,
                        depth: TextureUsageState::ShaderRead,
                    },
                    passes::oriented_target::WorldAttachmentStates::UNDEFINED,
                    passes::oriented_target::WorldAttachmentStates::ATTACHMENTS,
                )?);
                // Main remains a sampled external attachment for subsequent effects
                // and for the next frame's explicitly tracked initial transitions.
                for texture in [set.main.color_texture, set.main.depth_texture] {
                    operations.push(CommandOp::Barrier(texture_barrier(
                        texture,
                        TextureUsageState::TransferSrc,
                        TextureUsageState::ShaderRead,
                    )));
                }
                if acquired_output {
                    operations.extend(
                        self.canonical_world_target
                            .as_ref()
                            .expect("canonical output prepared")
                            .copy_to_frame(gal, frame_target)?,
                    );
                    gal.begin_frame_target_depth_write(frame_target)?;
                }
            } else if final_blit_resources.is_some() {
                let (blit_handles, blit_pipeline, blit_layout, _, _, _, _, _) =
                    final_blit_resources
                        .as_ref()
                        .expect("text final blit resources");
                let frame_color = match gal.pass_target_color_attachment(frame_target) {
                    Ok(view) => view,
                    Err(error) => {
                        self.world_text.cancel_submission();
                        cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                        return Err(error);
                    }
                };
                operations.extend([
                    CommandOp::BeginPass {
                        pass: blit_handles.3,
                        target: frame_target,
                        colors: vec![PassAttachment {
                            view: frame_color,
                            load_op: AttachmentLoadOp::DontCare,
                            store_op: AttachmentStoreOp::Store,
                            clear_color: None,
                        }],
                        depth_stencil: None,
                    },
                    CommandOp::BindGraphicsPipeline(*blit_pipeline),
                    CommandOp::BindResourceSet {
                        pipeline_layout: *blit_layout,
                        set_index: 0,
                        set: blit_handles.0,
                        dynamic_offsets: Vec::new(),
                    },
                    CommandOp::Draw {
                        vertices: 3,
                        instances: 1,
                    },
                    CommandOp::EndPass,
                ]);
            }
            operations.extend(gui_ops);
            let command_ops = operations.len() as u64;
            let command_lists = match Self::partition_command_lists_at_pass_boundaries(
                "minecraft.fabulous-material.commands",
                operations,
                Self::bounded_command_list_limit(gal),
            ) {
                Ok(command_lists) => command_lists,
                Err(error) => {
                    self.world_text.cancel_submission();
                    cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                    return Err(error);
                }
            };
            let command_list_count = command_lists.len() as u64;
            let token = match gal.submit(SubmissionBatch {
                label: "minecraft.fabulous-material.frame".to_string(),
                command_lists,
            }) {
                Ok(token) => token,
                Err(error) => {
                    self.world_text.cancel_submission();
                    cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                    return Err(error);
                }
            };
            if let Err(error) = gal.retire_completed() {
                self.world_text.cancel_submission();
                cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
                return Err(error);
            }
            // The Fabulous path stages the same generation-bound vanilla lightmap as
            // the direct world path. Commit that transaction after its combined GPU
            // submission succeeds so a later tick can replace it with updated
            // time-of-day inputs instead of colliding with a stale pending resource.
            if let Some(runtime) = self.shader_runtime.as_mut() {
                runtime.confirm_vanilla_lightmap_submission(gal)?;
            }
            cleanup_fabulous_frame_blit_resources(gal, &mut final_blit_resources);
            if normalize_output && acquired_output {
                gal.commit_frame_target_depth_write(frame_target)?;
            }
            whole_frame_phase_trace(
                if normalize_output {
                    "fabulous-material.down.completed"
                } else {
                    "fabulous-material.up.completed"
                },
                frame.frame_id,
                None,
            );
            self.world_text.confirm_submission();
            self.fabulous_attachment_set_initialized = true;
            if outline_plan.is_some() {
                self.entity_outline_targets_initialized = true;
            }
            Ok(WorldPrimitiveSubmitStats {
                material_quad_count: frame.material_quads.len() as u64,
                material_batch_count: batches.len() as u64,
                material_draw_count: batches.len() as u64,
                mesh_instance_count: (frame.mesh_instances.len()
                    + frame.first_person_mesh_instances.len())
                    as u64,
                mesh_batch_count: (world_mesh_batches.len()
                    + hand_mesh_batches.as_ref().map_or(0, Vec::len))
                    as u64,
                mesh_draw_count: (world_mesh_batches.len()
                    + hand_mesh_batches.as_ref().map_or(0, Vec::len))
                    as u64,
                world_draws: (batches.len()
                    + world_mesh_batches.len()
                    + hand_mesh_batches.as_ref().map_or(0, Vec::len)
                    + usize::from(draw_sky_disc)) as u64,
                command_lists: command_list_count,
                command_ops,
                submission_id: token.submission.0,
                world_text_quad_count: text_stats.as_ref().map_or(0, |stats| stats.quad_count),
                world_text_batch_count: text_stats.as_ref().map_or(0, |stats| stats.batch_count),
                world_text_draw_count: text_stats.as_ref().map_or(0, |stats| stats.draw_count),
                ..WorldPrimitiveSubmitStats::default()
            })
        })();
        let cleanup = if presentation_pass != Handle::NULL {
            gal.destroy(presentation_pass)
        } else {
            Ok(())
        };
        if result.is_err() || cleanup.is_err() {
            if normalize_output && acquired_output {
                gal.rollback_frame_target_depth_write(frame_target);
            }
            self.world_text.cancel_submission();
            if let Some(runtime) = self.shader_runtime.as_mut() {
                runtime.discard_vanilla_lightmap_submission(gal);
            }
        }
        // Always attempt cleanup, but retain the original rejection when
        // the frame itself failed. Successful frames must also retire cleanly.
        result.and_then(|stats| cleanup.map(|()| stats))
    }

    pub(super) fn frame_has_fabulous_transparency_work(&self, frame: &WorldPrimitiveFrame) -> bool {
        // The compact Fabulous attachment route is only a complete route for
        // material-only frames.  Once the frame also carries the ordinary
        // terrain/deferred graph (including DH LOD instances), keep the whole
        // submission on that graph so its material forward phase can compose
        // with the LOD, crack, border, and segment draws.  Returning false is
        // deliberate admission control: it selects the existing complete
        // graph instead of preparing Fabulous and then rejecting the frame
        // after other callsites have already contributed work.
        if !frame.lod_instances.is_empty()
            || !frame.segments.is_empty()
            || !frame.crack_quads.is_empty()
            || !frame.border_quads.is_empty()
        {
            return false;
        }
        // Indexed terrain (including translucent fluid sections) must stay on
        // the Rust deferred/G-buffer graph. The compact Fabulous attachment
        // route is intentionally reserved for frames whose translucent work
        // does not need terrain lighting; admitting terrain here would render
        // it as a forward mesh and silently bypass the deferred light/depth
        // history contract.
        if frame
            .mesh_instances
            .iter()
            .any(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
        {
            return false;
        }
        if frame
            .material_quads
            .iter()
            .any(|quad| quad.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT)
        {
            return true;
        }
        frame
            .mesh_instances
            .iter()
            .chain(frame.first_person_mesh_instances.iter())
            .any(|instance| {
                self.mesh_assets
                    .get(&instance.mesh_key)
                    .is_some_and(|asset| {
                        asset
                            .sections
                            .iter()
                            .any(|section| material_mode_uses_alpha_blending(section.material_mode))
                    })
            })
    }

    /// Whether this semantic frame contains a translucent input for Vanilla's
    /// Fabulous composition.  The game may request the `transparency` effect
    /// while constructing/loading an otherwise opaque frame (including GUI or
    /// first-person mesh bookkeeping).  Such a frame has no transparency
    /// attachment to compose, so routing it through the generic post-effect
    /// graph would both be semantically meaningless and demand external
    /// targets that do not exist.  This is deliberately narrower than route
    /// admission: actual translucent terrain still has to take the explicit
    /// handoff, and actual translucent material still has to take the
    /// six-attachment executor.
    pub(super) fn frame_requires_transparency_composition(&self, frame: &WorldPrimitiveFrame) -> bool {
        if frame
            .material_quads
            .iter()
            .any(|quad| quad.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT)
        {
            return true;
        }
        frame
            .mesh_instances
            .iter()
            .chain(frame.first_person_mesh_instances.iter())
            .any(|instance| {
                self.mesh_assets
                    .get(&instance.mesh_key)
                    .is_some_and(|asset| {
                        asset
                            .sections
                            .iter()
                            .any(|section| material_mode_uses_alpha_blending(section.material_mode))
                    })
            })
    }

    /// Returns whether the deferred graph can retain every indexed mesh while
    /// the terrain-only Fabulous handoff owns the late transparency stage.
    /// Opaque/cutout/glint sections are already part of the deferred color and
    /// depth result. Eligible translucent entity sections are lowered into the
    /// external `item_entity` writer; moving/opaque-textured and all optical,
    /// unknown, or missing sections remain unavailable.
    pub(super) fn terrain_handoff_meshes_are_supported(&self, frame: &WorldPrimitiveFrame) -> bool {
        self.terrain_handoff_meshes_support_reason(frame).is_none()
    }

    /// Explains why a semantic mesh set cannot yet be owned by the explicit
    /// Fabulous handoff.  This is deliberately a capability gate, not a
    /// fallback: the caller keeps the route unavailable until every submitted
    /// mesh has a concrete Rust lowering.
    pub(super) fn terrain_handoff_meshes_support_reason(&self, frame: &WorldPrimitiveFrame) -> Option<String> {
        if frame
            .mesh_instances
            .iter()
            .all(|instance| instance.stratum == WORLD_STRATUM_ENTITY_SHADOW_CASTER)
        {
            return Some("no world mesh instances".to_owned());
        }
        for instance in frame
            .mesh_instances
            .iter()
            .filter(|instance| instance.stratum != WORLD_STRATUM_ENTITY_SHADOW_CASTER)
        {
            let unsupported = |detail: String| {
                format!(
                    "mesh stratum={},key={},generation={},section={}: {detail}",
                    instance.stratum,
                    instance.mesh_key,
                    instance.mesh_generation,
                    instance.mesh_section_index,
                )
            };
            match instance.stratum {
                WORLD_STRATUM_TERRAIN => continue,
                WORLD_STRATUM_ENTITY_MESH
                | WORLD_STRATUM_MOVING_MESH
                | WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY
                | WORLD_STRATUM_ORDINARY_BLOCK => {
                    let Some(asset) = self.mesh_assets.get(&instance.mesh_key) else {
                        return Some(unsupported("mesh asset is unavailable".to_owned()));
                    };
                    if asset.mesh_generation != instance.mesh_generation {
                        return Some(unsupported(format!(
                            "mesh generation is stale (available={})",
                            asset.mesh_generation
                        )));
                    }
                    let sections: Box<dyn Iterator<Item = (usize, &WorldMeshSection)> + '_> =
                        if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                            Box::new(asset.sections.iter().enumerate())
                        } else {
                            let Ok(index) = usize::try_from(instance.mesh_section_index) else {
                                return Some(unsupported(
                                    "section index does not fit usize".to_owned(),
                                ));
                            };
                            let Some(section) = asset.sections.get(index) else {
                                return Some(unsupported(format!(
                                    "section index is unavailable (sections={})",
                                    asset.sections.len()
                                )));
                            };
                            Box::new(std::iter::once((index, section)))
                        };
                    let mut any_section = false;
                    for (index, section) in sections {
                        any_section = true;
                        let supported = matches!(
                            section.material_mode,
                            WORLD_MATERIAL_MODE_OPAQUE
                                | WORLD_MATERIAL_MODE_CUTOUT
                                | WORLD_MATERIAL_MODE_GLINT
                        ) || (instance.stratum == WORLD_STRATUM_ENTITY_MESH
                            && material_mode_uses_alpha_blending(section.material_mode));
                        if !supported {
                            return Some(unsupported(format!(
                                "section {index} material mode {} has no Fabulous lowering",
                                section.material_mode
                            )));
                        }
                    }
                    if !any_section {
                        return Some(unsupported("mesh asset has no sections".to_owned()));
                    }
                }
                _ => return Some(unsupported("stratum has no Fabulous lowering".to_owned())),
            }
        }
        None
    }

    /// Direct material quads in opaque/cutout mode are already folded into
    /// the deferred lit color before the Fabulous handoff. Translucent and
    /// special overlay modes need a family-specific external writer and must
    /// not be admitted merely because the generic material pipeline exists.
    pub(super) fn terrain_handoff_material_quads_are_supported(&self, frame: &WorldPrimitiveFrame) -> bool {
        frame.material_quads.iter().all(|quad| {
            matches!(
                quad.material_mode,
                WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT
            ) || Self::terrain_main_overlay_material_quad(quad)
                || Self::terrain_external_material_quad(quad)
        })
    }

    pub(super) fn terrain_main_overlay_material_quad(quad: &WorldMaterialQuadRequest) -> bool {
        quad.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
            && matches!(
                quad.material_id,
                WORLD_MATERIAL_ID_CELESTIAL | WORLD_MATERIAL_ID_SKY_STARS
            )
    }

    pub(super) fn terrain_external_material_quad(quad: &WorldMaterialQuadRequest) -> bool {
        quad.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
            // Overlay blending needs the actual background color. Flattening
            // celestial work into a transparent external layer loses that
            // dependency when Fabulous later applies ordinary alpha blending.
            && !Self::terrain_main_overlay_material_quad(quad)
            && matches!(
                quad.source_program,
                WORLD_MATERIAL_SOURCE_TEXTURED
                    | WORLD_MATERIAL_SOURCE_PARTICLES
                    | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                    | WORLD_MATERIAL_SOURCE_CLOUDS
                    | WORLD_MATERIAL_SOURCE_WEATHER
            )
    }

    pub(super) fn terrain_handoff_first_person_is_supported(&self, frame: &WorldPrimitiveFrame) -> bool {
        if frame.first_person_mesh_instances.is_empty() {
            return true;
        }
        if !frame.first_person.enabled || !frame.first_person.clear_depth_before {
            return false;
        }
        frame.first_person_mesh_instances.iter().all(|instance| {
            if instance.stratum != WORLD_STRATUM_ENTITY_MESH {
                return false;
            }
            let Some(asset) = self.mesh_assets.get(&instance.mesh_key) else {
                return false;
            };
            if asset.mesh_generation != instance.mesh_generation {
                return false;
            }
            if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                !asset.sections.is_empty()
            } else {
                usize::try_from(instance.mesh_section_index)
                    .ok()
                    .and_then(|index| asset.sections.get(index))
                    .is_some()
            }
        })
    }

    /// Prepares the named translucent material families that accompany a
    /// deferred terrain frame. Their resources remain ordinary Rust-owned
    /// material bindings, but their passes are recorded against the matching
    /// Fabulous external role after the terrain capture/clear boundary.
    pub(super) fn prepare_terrain_external_material_ops(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<(Vec<CommandOp>, [bool; 4], (u64, u64, u64))> {
        let mut external_frame = frame.clone();
        external_frame
            .material_quads
            .retain(Self::terrain_external_material_quad);
        if external_frame.material_quads.is_empty() {
            return Ok((Vec::new(), [false; 4], (0, 0, 0)));
        }
        let batches = material_batches(
            &external_frame,
            ColorFormat::Rgba8Unorm,
            RasterYDirection::Up,
        );
        for batch in &batches {
            self.ensure_material_resources(gal, batch.key)?;
            self.ensure_material_resource_slots(gal, batch.key, batch.count())?;
        }
        let mut operations = Vec::with_capacity(batches.len() * 12);
        let external_particle_lightmap = if batches
            .iter()
            .any(|batch| material_uses_lightmap(batch.key))
        {
            let lightmap = frame.shader_environment.vanilla_lightmap.ok_or_else(|| {
                GalError::unsupported_feature(
                    "Fabulous external weather and particles require copied vanilla lightmap semantics",
                )
            })?;
            if frame.shader_environment.world_generation == 0 {
                return Err(GalError::unsupported_feature(
                    "Fabulous external weather and particles require a non-zero vanilla lightmap world generation",
                ));
            }
            self.ensure_shader_runtime(gal, self.generation)?;
            let layout = self.ensure_builtin_terrain_lightmap_layout(gal)?;
            let runtime = self
                .shader_runtime
                .as_mut()
                .expect("shader runtime is installed before external particle lightmap staging");
            runtime.observe_vanilla_lightmap(
                frame.shader_environment.world_generation,
                Some(lightmap),
            )?;
            runtime.stage_vanilla_lightmap_residency(gal, &mut operations)?;
            Some(
                runtime
                    .vanilla_lightmap_resource_set(gal, layout, true)?
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "Fabulous external weather and particles require a staged Rust-owned vanilla lightmap",
                        )
                    })?,
            )
        } else {
            None
        };
        let set = self.fabulous_attachment_set.as_ref().ok_or_else(|| {
            GalError::backend("Fabulous attachment set missing before external material routing")
        })?;
        let role_for_batch = |batch: &MaterialBatch| -> Option<(
            usize,
            &crate::render::shaderpack::vanilla::fabulous::FabulousAttachmentResources,
        )> {
            match crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::for_material_source(
                batch.key.source_program,
            ) {
                Some(crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Translucent) => {
                    Some((0, &set.translucent))
                }
                Some(crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Particles) => {
                    Some((1, &set.particles))
                }
                Some(crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Clouds) => {
                    Some((2, &set.clouds))
                }
                Some(crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::Weather) => {
                    Some((3, &set.weather))
                }
                _ => None,
            }
        };
        let mut slot_indices = BTreeMap::<MaterialResourceKey, usize>::new();
        for batch in &batches {
            let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                GalError::backend("external material resources vanished before routing")
            })?;
            let slot_index = slot_indices.entry(batch.key).or_insert(0usize);
            let slot = resources.data_slots.get(*slot_index).ok_or_else(|| {
                GalError::backend("external material data slot missing before routing")
            })?;
            *slot_index += 1;
            operations.push(CommandOp::Barrier(buffer_barrier(
                slot.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer: slot.uniform_buffer,
                offset: 0,
                data: packed_material_uniforms_for_batch(&external_frame, batch)?,
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                slot.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        let mut roles_written = [false; 4];
        for role_index in 0..=3 {
            let role_batches = batches.iter().filter(|batch| {
                role_for_batch(batch).is_some_and(|(index, _)| index == role_index)
            });
            let role_batches = role_batches.collect::<Vec<_>>();
            if role_batches.is_empty() {
                continue;
            }
            let attachment = role_for_batch(role_batches[0])
                .expect("role batch has a mapped external attachment")
                .1;
            operations.push(CommandOp::Barrier(texture_barrier(
                attachment.color_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::ColorAttachment,
            )));
            operations.push(CommandOp::Barrier(texture_barrier(
                attachment.depth_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::DepthStencilAttachment,
            )));
            operations.push(CommandOp::BeginPass {
                pass: attachment.render_pass,
                target: attachment.render_target,
                colors: vec![PassAttachment {
                    view: attachment.color_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }],
                depth_stencil: Some(PassAttachment {
                    view: attachment.depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            let mut role_slots = BTreeMap::<MaterialResourceKey, usize>::new();
            for batch in role_batches {
                let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("external material resources vanished during pass lowering")
                })?;
                let slot_index = role_slots.entry(batch.key).or_insert(0usize);
                let slot = resources.data_slots.get(*slot_index).ok_or_else(|| {
                    GalError::backend("external material slot vanished during pass lowering")
                })?;
                *slot_index += 1;
                operations.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
                operations.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: 0,
                    set: slot.resource_set,
                    dynamic_offsets: Vec::new(),
                });
                if resources.lightmap_resource_layout.is_some() {
                    let lightmap = external_particle_lightmap.ok_or_else(|| {
                        GalError::backend("external lightmapped material pipeline has no staged Rust lightmap binding")
                    })?;
                    operations.push(CommandOp::BindResourceSet {
                        pipeline_layout: resources.pipeline_layout,
                        set_index: 1,
                        set: lightmap.set,
                        dynamic_offsets: Vec::new(),
                    });
                }
                operations.push(CommandOp::SetIndexBuffer {
                    buffer: resources.index_buffer,
                    offset: 0,
                    index_type: IndexType::U32,
                });
                operations.push(CommandOp::DrawIndexed {
                    indices: 6,
                    instances: batch.count() as u32,
                });
            }
            operations.push(CommandOp::EndPass);
            if role_index != 0 {
                roles_written[role_index] = true;
            }
        }
        let quad_count = external_frame.material_quads.len() as u64;
        let batch_count = batches.len() as u64;
        Ok((
            operations,
            roles_written,
            (quad_count, batch_count, batch_count),
        ))
    }

    /// Records eligible translucent entity meshes into Fabulous's explicit
    /// `item_entity` attachment. Draw records already carry Rust-owned
    /// pipelines, resource sets, dynamic stream offsets, and index ranges;
    /// this method supplies only the named attachment pass and synchronization.
    pub(super) fn append_terrain_external_entity_mesh_ops(
        &mut self,
        ops: &mut Vec<CommandOp>,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        let Some(set) = self.fabulous_attachment_set.as_ref() else {
            return Err(GalError::backend(
                "Fabulous attachment set missing before entity mesh handoff",
            ));
        };
        let draws = draws
            .iter()
            .filter(|draw| {
                draw.stratum == WORLD_STRATUM_ENTITY_MESH
                    && draw.material_mode == TerrainMaterialPassMode::Translucent
            })
            .collect::<Vec<_>>();
        if draws.is_empty() {
            return Ok(());
        }
        let attachment = &set.item_entity;
        ops.push(CommandOp::Barrier(texture_barrier(
            attachment.color_texture,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            attachment.depth_texture,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: attachment.render_pass,
            target: attachment.render_target,
            colors: vec![PassAttachment {
                view: attachment.color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 0.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: attachment.depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        for draw in draws {
            ops.push(CommandOp::BindGraphicsPipeline(draw.pipeline));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: draw.pipeline_layout,
                set_index: 0,
                set: draw.resource_set,
                dynamic_offsets: draw.resource_set_dynamic_offsets.to_vec(),
            });
            if let Some(shader_resource_set) = draw.shader_resource_set {
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout: draw.pipeline_layout,
                    set_index: shader_resource_set.set_index,
                    set: shader_resource_set.set,
                    dynamic_offsets: Vec::new(),
                });
            }
            ops.push(CommandOp::SetIndexBuffer {
                buffer: draw.index_buffer,
                offset: draw.index_offset,
                index_type: draw.index_type,
            });
            ops.push(CommandOp::DrawIndexed {
                indices: draw.index_count,
                instances: draw.instance_count,
            });
        }
        ops.push(CommandOp::EndPass);
        self.pending_terrain_external_item_entity_written = true;
        Ok(())
    }

    /// Lowers direct world overlays into Fabulous' copied `main` attachment.
    /// The ordinary primitive path targets the acquired frame image, but that
    /// would be overwritten by the later Fabulous blit. Rendering here keeps
    /// the semantic overlay families in the same explicit composition graph.
    pub(super) fn prepare_terrain_overlay_ops(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        if frame.segments.is_empty()
            && frame.crack_quads.is_empty()
            && frame.border_quads.is_empty()
        {
            return Ok((Vec::new(), WorldPrimitiveSubmitStats::default()));
        }
        let color_format = {
            let set = self.fabulous_attachment_set.as_ref().ok_or_else(|| {
                GalError::backend("Fabulous attachment set missing before overlay routing")
            })?;
            gal.pass_target_color_format(set.main.render_target)?
        };
        if !frame.segments.is_empty() {
            self.ensure_resources(gal, color_format, RasterYDirection::Up)?;
        }
        if !frame.crack_quads.is_empty() {
            self.ensure_crack_resources(gal, color_format, RasterYDirection::Up)?;
        }
        if !frame.border_quads.is_empty() {
            self.ensure_border_resources(gal, color_format, RasterYDirection::Up)?;
        }
        let (main_color, main_depth, target, pass) = {
            let set = self.fabulous_attachment_set.as_ref().ok_or_else(|| {
                GalError::backend("Fabulous attachment set missing before overlay lowering")
            })?;
            (
                set.main.color_texture,
                set.main.depth_texture,
                set.main.render_target,
                set.main.render_pass,
            )
        };
        let (color_view, depth_view) = {
            let set = self
                .fabulous_attachment_set
                .as_ref()
                .expect("set remains installed");
            (set.main.color_view, set.main.depth_view)
        };
        let mut operations = vec![
            CommandOp::Barrier(texture_barrier(
                main_color,
                TextureUsageState::ShaderRead,
                TextureUsageState::ColorAttachment,
            )),
            CommandOp::Barrier(texture_barrier(
                main_depth,
                TextureUsageState::ShaderRead,
                TextureUsageState::DepthStencilAttachment,
            )),
        ];
        let mut append_pass = |uniform_buffer: Handle,
                               pipeline_layout: Handle,
                               resource_set: Handle,
                               pipeline: Handle,
                               data: Vec<u8>,
                               vertices: u32,
                               instances: u32| {
            operations.extend([
                CommandOp::Barrier(buffer_barrier(
                    uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::HostWriteBuffer {
                    buffer: uniform_buffer,
                    offset: 0,
                    data,
                },
                CommandOp::Barrier(buffer_barrier(
                    uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![PassAttachment {
                        view: color_view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }],
                    depth_stencil: Some(PassAttachment {
                        view: depth_view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set: resource_set,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Draw {
                    vertices,
                    instances,
                },
                CommandOp::EndPass,
            ]);
        };
        if !frame.border_quads.is_empty() {
            let resources = self
                .border_resources
                .get(&(color_format, RasterYDirection::Up))
                .ok_or_else(|| {
                    GalError::backend("world border resources vanished before Fabulous overlay")
                })?;
            for batch in border_batches(frame) {
                let pipeline = if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                    resources.pipeline_depth_test_write
                } else {
                    resources.pipeline_depth_disabled
                };
                append_pass(
                    resources.uniform_buffer,
                    resources.pipeline_layout,
                    resources.resource_set,
                    pipeline,
                    packed_border_uniforms_for_batch(frame, &batch)?,
                    6,
                    batch.count as u32,
                );
            }
        }
        if !frame.crack_quads.is_empty() {
            let resources = self
                .crack_resources
                .get(&(color_format, RasterYDirection::Up))
                .ok_or_else(|| {
                    GalError::backend("world crack resources vanished before Fabulous overlay")
                })?;
            for batch in crack_batches(frame) {
                let pipeline = if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                    resources.pipeline_depth_test_write
                } else {
                    resources.pipeline_depth_disabled
                };
                append_pass(
                    resources.uniform_buffer,
                    resources.pipeline_layout,
                    resources.resource_set,
                    pipeline,
                    packed_crack_uniforms_for_batch(frame, &batch)?,
                    6,
                    batch.count as u32,
                );
            }
        }
        if !frame.segments.is_empty() {
            let resources = self
                .resources
                .get(&(color_format, RasterYDirection::Up))
                .ok_or_else(|| {
                    GalError::backend("world line resources vanished before Fabulous overlay")
                })?;
            for batch in line_batches(frame) {
                let pipeline = match batch.depth_policy {
                    WORLD_DEPTH_POLICY_TEST_WRITE => resources.pipeline_depth_test_write,
                    WORLD_DEPTH_POLICY_TEST_NO_WRITE => resources.pipeline_depth_test_no_write,
                    _ => resources.pipeline_depth_disabled,
                };
                append_pass(
                    resources.uniform_buffer,
                    resources.pipeline_layout,
                    resources.resource_set,
                    pipeline,
                    packed_line_uniforms_for_batch(frame, &batch)?,
                    (batch.count * 6) as u32,
                    1,
                );
            }
        }
        operations.extend([
            CommandOp::Barrier(texture_barrier(
                main_color,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )),
            CommandOp::Barrier(texture_barrier(
                main_depth,
                TextureUsageState::DepthStencilAttachment,
                TextureUsageState::ShaderRead,
            )),
        ]);
        let mut stats = WorldPrimitiveSubmitStats {
            segment_count: frame.segments.len() as u64,
            vertex_count: (frame.segments.len() * 6) as u64,
            primitive_batch_count: line_batches(frame).len() as u64,
            crack_quad_count: frame.crack_quads.len() as u64,
            crack_batch_count: crack_batches(frame).len() as u64,
            border_quad_count: frame.border_quads.len() as u64,
            border_batch_count: border_batches(frame).len() as u64,
            ..WorldPrimitiveSubmitStats::default()
        };
        stats.crack_draw_count = stats.crack_batch_count;
        stats.border_draw_count = stats.border_batch_count;
        stats.world_draws = stats
            .primitive_batch_count
            .saturating_add(stats.crack_draw_count)
            .saturating_add(stats.border_draw_count);
        Ok((operations, stats))
    }

    /// Explains why a frame remains on the normal deferred graph instead of
    /// entering the compact six-attachment Fabulous executor. Keeping this
    /// classification explicit prevents terrain transparency from falling
    /// through to the generic single-target post-effect route.
    pub(super) fn fabulous_transparency_route_classification(
        frame: &WorldPrimitiveFrame,
    ) -> Option<&'static str> {
        if frame
            .mesh_instances
            .iter()
            .any(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
        {
            return Some("terrain/deferred frame requires an explicit Fabulous attachment handoff");
        }
        if !frame.lod_instances.is_empty()
            || !frame.segments.is_empty()
            || !frame.crack_quads.is_empty()
            || !frame.border_quads.is_empty()
        {
            return Some(
                "frame contains deferred or auxiliary world work not supported by the compact Fabulous attachment route",
            );
        }
        None
    }

    pub(super) fn validate_fabulous_material_sources(&self, frame: &WorldPrimitiveFrame) -> GalResult<()> {
        for (index, quad) in frame.material_quads.iter().enumerate() {
            if quad.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT {
                continue;
            }
            if crate::render::shaderpack::vanilla::fabulous::FabulousTargetRole::for_material_source(
                quad.source_program,
            )
            .is_none()
            {
                return Err(GalError::unsupported_feature(format!(
                    "translucent material quad {index} uses an unavailable Fabulous source family {}",
                    quad.source_program
                )));
            }
        }
        Ok(())
    }

    pub(super) fn ensure_fabulous_attachment_set(
        &mut self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
        frame: &WorldPrimitiveFrame,
        translucent_color_format: ColorFormat,
        external_color_format: ColorFormat,
    ) -> GalResult<()> {
        let extent = Extent3d {
            width: frame.viewport_width,
            height: frame.viewport_height,
            depth: 1,
        };
        let color_format = gal.pass_target_color_format(frame_target)?;
        if self.fabulous_attachment_set.as_ref().is_some_and(|set| {
            set.main.extent == extent
                && gal
                    .pass_target_color_format(set.main.render_target)
                    .is_ok_and(|format| format == color_format)
                && set.translucent_color_format == translucent_color_format
                && set.external_color_format == external_color_format
        }) {
            return Ok(());
        }
        if let Some(previous) = self.fabulous_attachment_set.take() {
            self.clear_frame_passes_for_targets(gal, &[previous.main.render_target]);
            // Outline mask targets may reference this owned main depth view.
            // Retire consumers before replacing the attachment generation.
            if self
                .entity_outline_targets
                .as_ref()
                .is_some_and(|targets| targets.mask_depth_view == Some(previous.main.depth_view))
            {
                self.destroy_entity_outline_mask_gpu_resources(gal);
                if let Some(sets) = self.entity_outline_post_effect_sets.take() {
                    for handle in sets.handles_in_destroy_order() {
                        gal.destroy(handle)?;
                    }
                }
                if let Some(targets) = self.entity_outline_targets.take() {
                    for handle in targets.handles_in_destroy_order() {
                        gal.destroy(handle)?;
                    }
                }
                self.entity_outline_targets_initialized = false;
                self.pending_entity_outline_targets_written = false;
            }
            previous.destroy(gal);
        }
        self.fabulous_attachment_set_initialized = false;
        self.fabulous_attachment_set = Some(
            crate::render::shaderpack::vanilla::fabulous::FabulousAttachmentSet::create_with_external_formats(
                gal,
                extent,
                color_format,
                translucent_color_format,
                external_color_format,
            )?,
        );
        Ok(())
    }
}

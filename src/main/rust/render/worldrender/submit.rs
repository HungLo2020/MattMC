//! Whole-frame and partial submission entry points.

use super::*;

/// Removes render passes that neither draw nor change their attachments
/// (every attachment loads and stores). Passes that clear or discard are kept
/// so validation still sees them.
pub(super) fn strip_empty_load_store_passes(ops: &mut Vec<CommandOp>) {
    let preserves = |attachment: &PassAttachment| {
        attachment.load_op == AttachmentLoadOp::Load && attachment.store_op == AttachmentStoreOp::Store
    };
    let mut index = 0;
    while index + 1 < ops.len() {
        let empty = matches!(
            (&ops[index], &ops[index + 1]),
            (CommandOp::BeginPass { colors, depth_stencil, .. }, CommandOp::EndPass)
                if colors.iter().all(preserves) && depth_stencil.as_ref().is_none_or(preserves)
        );
        if empty {
            ops.drain(index..index + 2);
        } else {
            index += 1;
        }
    }
}

/// SkyRenderer's End sky box faces (opaque textured, no depth).
pub(super) fn is_end_sky_quad(quad: &WorldMaterialQuadRequest) -> bool {
    quad.texture_id == WORLD_MATERIAL_TEXTURE_END_SKY
        && quad.material_id == WORLD_MATERIAL_ID_OPAQUE_TEXTURED
}

pub(super) fn frame_has_end_sky_quads(frame: &WorldPrimitiveFrame) -> bool {
    frame.material_quads.iter().any(is_end_sky_quad)
}

/// Admit a mixed DH/vanilla frame to the ordinary Rust graph only when the
/// copied DH frame explicitly selected that route and no deferred source
/// execution path is still armed.  The latter paths have separate ownership
/// contracts and must be rejected before any partial submission occurs.
pub(super) fn selected_dh_ordinary_graph_is_admissible(
    frame: &WorldPrimitiveFrame,
    runtime_source_execution_armed: bool,
) -> bool {
    !frame.lod_instances.is_empty()
        && frame.lod_render_frame.rust_route_selected()
        && !runtime_source_execution_armed
}

impl WorldPrimitiveFrontend {
    pub fn submit_whole_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        self.submit_whole_frame_with_gui_stats(
            gal,
            generation,
            frame_target,
            frame,
            gui_ops,
            GuiSubmitStats::default(),
        )
    }

    /// Submits prebuilt GUI operations together with the stats that recorded
    /// them. The route is chosen inside this call: source preparation may arm
    /// the selected-source route for this very frame, and that submission
    /// validates the GUI stream against the private targets the stats declare.
    pub(super) fn submit_whole_frame_with_gui_stats(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
        gui_stats: GuiSubmitStats,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        gal.begin_command_recording()?;
        let result = self.submit_whole_frame_recorded(
            gal,
            generation,
            frame_target,
            frame,
            gui_ops,
            gui_stats,
        );
        let finish = gal.finish_command_recording();
        result.and_then(|stats| finish.map(|()| stats))
    }

    pub(super) fn submit_whole_frame_recorded(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
        gui_stats: GuiSubmitStats,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        let direction = match crate::core::environment::var("MATTMC_RUST_OWNED_WORLD_TARGET").as_deref() {
            Ok("1") => Some(RasterYDirection::Up),
            Ok("down") => Some(RasterYDirection::Down),
            _ => self.vanilla_world_output_direction(gal, frame_target, &frame),
        };
        self.submit_whole_frame_with_target_policy(
            gal,
            generation,
            frame_target,
            frame,
            gui_ops,
            gui_stats,
            direction,
        )
    }

    /// World rasterization and image-coordinate composition are distinct
    /// domains. Vanilla Vulkan renders into a Down-oriented owned color/depth
    /// pair, then explicitly normalizes both before post effects and GUI reach
    /// the existing frame presenter. Partial/offscreen and selected-source/LOD
    /// routes retain their own output contracts; this is not a submit fallback.
    pub(super) fn vanilla_world_output_direction(
        &self,
        gal: &VulkanicGal,
        target: Handle,
        frame: &WorldPrimitiveFrame,
    ) -> Option<RasterYDirection> {
        (gal.capabilities().supports(BackendFeature::TextureRowReversal)
            && target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget)
            && frame.background.enabled
            && frame.background.load_intent == WORLD_BACKGROUND_LOAD_CLEAR
            && !self.runtime_source_execution_is_armed()
            && !self.candidate_lowered_source_execution_requested()
            && frame.lod_instances.is_empty()
            && !frame.lod_render_frame.rust_route_selected())
        .then_some(RasterYDirection::Down)
    }

    pub(super) fn submit_whole_frame_with_target_policy(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
        gui_stats: GuiSubmitStats,
        owned_world_direction: Option<RasterYDirection>,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        #[cfg(not(test))]
        let mut frame = frame;
        #[cfg(not(test))]
        {
            self.disarm_source_route_on_extent_change(&frame);
            self.prepare_runtime_source_before_presentation(gal, generation, frame_target, &mut frame)?;
        }
        self.submit_whole_frame_with_initial_ops(
            gal,
            generation,
            frame_target,
            frame,
            gui_ops,
            gui_stats,
            owned_world_direction,
            Vec::new(),
            false,
        )
    }

    pub(super) fn submit_whole_frame_with_initial_ops(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
        gui_stats: GuiSubmitStats,
        owned_world_direction: Option<RasterYDirection>,
        mut pre_graph_ops: Vec<CommandOp>,
        preparing_source_entry: bool,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        if owned_world_direction.is_some()
            && (!gal.capabilities().supports(BackendFeature::TextureRowReversal)
                || self.runtime_source_execution_is_armed())
        {
            return Err(GalError::unsupported_feature("private owned vanilla world target requires texture row reversal without an armed source runtime"));
        }
        self.discard_stale_pending_vanilla_lightmap(gal);
        self.world_text.begin_submission();
        require_particle_group_semantics(&frame)?;
        self.pending_graph_targets_written = false;
        self.pending_translucent_capture_written = false;
        self.pending_lod_direct_composition_written = false;
        self.pending_lod_ssao_written = false;
        self.pending_lod_vanilla_sample_state_established = false;
        self.pending_entity_outline_targets_written = false;
        self.pending_terrain_external_item_entity_written = false;
        #[cfg(test)]
        if self.candidate_lowered_source_execution_requested() {
            self.pending_terrain_fabulous_handoff = false;
            Self::reject_source_gui_presenters(&gui_ops)?;
            return self
                .submit_complete_named_source_frame(
                    gal,
                    generation,
                    frame_target,
                    frame,
                    move |_, _, _, _| Ok((gui_ops.clone(), gui_stats.clone())),
                )
                .map(|(stats, _)| stats);
        }
        self.disarm_source_route_on_extent_change(&frame);
        #[cfg(not(test))]
        let frame = self.admit_armed_source_frame(frame);
        #[cfg(not(test))]
        if frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            self.report_shader_route_outcome(&frame);
        }
        #[cfg(not(test))]
        if self.runtime_source_execution_is_armed() {
            self.write_runtime_source_admission_status(
                gal,
                &frame,
                "target-policy-source-armed",
                false,
            );
        }
        #[cfg(not(test))]
        if self.runtime_source_execution_is_armed() {
            self.pending_terrain_fabulous_handoff = false;
            Self::reject_source_gui_presenters(&gui_ops)?;
            return self
                .submit_armed_runtime_source_frame(
                    gal,
                    generation,
                    frame_target,
                    frame,
                    gui_ops,
                    gui_stats,
                )
                .map(|(stats, _)| stats);
        }
        if self.frame_has_fabulous_transparency_work(&frame)
            && !self.pending_terrain_fabulous_handoff
            && !preparing_source_entry
        {
            self.validate_fabulous_material_sources(&frame)?;
            // Admit only complete semantic frames; never drop an unsupported
            // family to make the compact graph eligible.
            if !Self::fabulous_material_frame_content_is_supported(&frame) {
                return Err(GalError::unsupported_feature(
                    "Rust Fabulous material graph does not yet support this frame's terrain or non-material translucent work",
                ));
            }
            let color_format = gal.pass_target_color_format(frame_target)?;
            self.ensure_fabulous_attachment_set(
                gal,
                frame_target,
                &frame,
                color_format,
                color_format,
            )?;
            // Keep the caller's explicit raster convention on the compact
            // branch too. Its executor normalizes both color and depth before
            // the existing frame owner presents. Vanilla acquired world frames
            // select Down; callers with other explicit output contracts use Up.
            return self.submit_fabulous_material_frame_oriented(
                gal,
                generation,
                frame_target,
                frame,
                gui_ops,
                self.fabulous_attachment_set_initialized,
                owned_world_direction.unwrap_or(RasterYDirection::Up),
            );
        }
        // The direct world output is normalized before the optional Fabulous
        // handoff. Fabulous's own attachments and external draws remain
        // canonical; only its deferred input copies carry row reversal.
        // Semantic selection happens
        // before any submission; there is no failed-submit retry/fallback.
        let owned_world_target = owned_world_direction.is_some();
        let graph_direction = owned_world_direction.unwrap_or(RasterYDirection::Up);
        let total_started = std::time::Instant::now();
        let pre_graph_started = std::time::Instant::now();
        let world_frame_id = frame.frame_id;
        whole_frame_phase_trace("pre-graph.begin", world_frame_id, None);
        // The selected-source admission path needs an exact semantic snapshot
        // after the normal graph submission has completed. Ordinary vanilla
        // and DH frames do not request that path, so retaining a second copy
        // of every visible mesh/material list would only add render-thread
        // allocation and copying. Keep the snapshot strictly behind the
        // explicit Rust-owned source admission request.
        let source_activation_frame = self
            .runtime_source_preparation_requested()
            .then(|| frame.clone());
        let source_frame_for_admission = source_activation_frame.as_ref().unwrap_or(&frame);
        let rust_lod_opaque_route = frame.lod_render_frame.rust_route_selected();
        // Submission generations identify Java-side frame/lifecycle updates.
        // The shader runtime instead belongs to this frontend's persistent
        // graph generation. Recreating it midway through this submission
        // would discard copied semantic resources before their first draw.
        let shader_runtime_generation = self.shader_runtime_generation_for_submission(generation);
        let phase_started = std::time::Instant::now();
        self.ensure_candidate_colored_light_for_frame(gal, shader_runtime_generation, &frame)?;
        whole_frame_phase_trace(
            "candidate-colored-light",
            world_frame_id,
            Some(phase_started),
        );
        self.write_runtime_source_admission_status(gal, &frame, "pre-graph", false);
        let phase_started = std::time::Instant::now();
        self.ensure_rust_lod_lightmap_for_frame(gal, shader_runtime_generation, &frame)?;
        whole_frame_phase_trace("lod-lightmap", world_frame_id, Some(phase_started));
        // Keep a bounded semantic snapshot only when the private runtime is
        // installed. Normal submissions retain their existing allocation and
        // routing cadence.
        // Persistent attachment state is committed only after the enclosing
        // submission succeeds.  Command construction can still be discarded
        // by a later validation/resource step, so recording it eagerly would
        // make the next frame claim ShaderRead for an attachment that was
        // never written.
        let phase_started = std::time::Instant::now();
        if self.append_private_terrain_occupancy_for_frame(
            source_frame_for_admission,
            &mut pre_graph_ops,
        )? {
            whole_frame_phase_trace("private-occupancy", world_frame_id, Some(phase_started));
        }
        if frame.shader_environment.enabled || rust_lod_opaque_route {
            if let Some(runtime) = self.shader_runtime.as_mut() {
                if rust_lod_opaque_route
                    || runtime
                        .candidate_source_requires_resource(TerrainSourceResourceRole::Lightmap)
                {
                    let phase_started = std::time::Instant::now();
                    let current_lightmap = frame
                        .shader_environment
                        .vanilla_lightmap
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "Rust source and LOD lightmap passes require copied current-frame vanilla lightmap semantics",
                            )
                        })?;
                    // Candidate discovery may have observed an earlier frame.
                    // The pre-graph upload and the later built-in consumers
                    // must bind the same current-frame generation in this
                    // combined submission.
                    runtime.observe_vanilla_lightmap(
                        frame.shader_environment.world_generation,
                        Some(current_lightmap),
                    )?;
                    runtime.stage_vanilla_lightmap_residency(gal, &mut pre_graph_ops)?;
                    whole_frame_phase_trace(
                        "vanilla-lightmap",
                        world_frame_id,
                        Some(phase_started),
                    );
                }
            }
        }
        // The source plan may bind the exact flood-fill parity that the
        // pre-graph operations above make shader-readable. Rebuild the
        // snapshot only after those operations have been staged; it remains
        // invalid outside this combined submission and is discarded on any
        // submission failure below.
        if self.should_refresh_candidate_source_assets_for_frame(&frame) {
            // Source admission is exact-frame by design. After the colored
            // volume reaches a stable state there may be no pending upload,
            // but its immutable semantic resource snapshot still has to be
            // correlated to this frame before the selected route can arm.
            // Rebuilding this bounded record does not recreate GPU resources
            // or relax the frame/generation equality check below.
            let source_started = std::time::Instant::now();
            let source_result = self.ensure_candidate_source_assets_for_frame(
                gal,
                frame.voxel_volume.world_generation,
                frame.frame_id,
                false,
                source_frame_includes_distant_horizons(&frame),
            );
            whole_frame_phase_trace(
                "candidate-source-assets",
                world_frame_id,
                Some(source_started),
            );
            let _ = source_result?;
        }
        let phase_started = std::time::Instant::now();
        if let Err(error) = self.lod_gpu_residency.stage_visible_uploads(
            gal,
            &self.lod_gpu_column_assets,
            &frame.lod_instances,
            &mut pre_graph_ops,
        ) {
            if let Some(runtime) = self.shader_runtime.as_mut() {
                runtime.discard_private_terrain_occupancy_submission();
                runtime.discard_vanilla_lightmap_submission(gal);
            }
            return Err(error);
        }
        whole_frame_phase_trace("lod-visible-uploads", world_frame_id, Some(phase_started));
        let mut gameplay_attachment_capture = GameplayAttachmentCapture::select(
            &frame,
            generation,
            self.mesh_asset_generation,
            gal.capabilities().shader_conventions,
        )?;
        if gameplay_attachment_capture.is_some() {
            for observation in &self.latest_atlas_animation_observations {
                eprintln!("{observation}");
            }
        }
        let deferred_handoff_text_quads = if self.pending_terrain_fabulous_handoff {
            frame.text_quads.clone()
        } else {
            Vec::new()
        };
        let deferred_handoff_first_person = if self.pending_terrain_fabulous_handoff {
            Some((
                frame.first_person.clone(),
                frame.first_person_mesh_instances.clone(),
            ))
        } else {
            None
        };
        // Keep the Fabulous handoff's filtered graph separate from the full
        // semantic frame. Full attachment capture also needs the original for
        // post-graph observations. Ordinary vanilla/DH frames have no later
        // reader, so move their frame directly into graph construction instead
        // of cloning every mesh instance and semantic vector each render.
        let preserve_world_text_receipt = !frame.text_quads.is_empty()
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let preserve_post_graph_frame = self.pending_terrain_fabulous_handoff
            || gameplay_attachment_capture.is_some()
            || preserve_world_text_receipt;
        let (graph_frame, post_graph_frame) = if self.pending_terrain_fabulous_handoff {
            let mut graph_frame = frame.clone();
            graph_frame
                .material_quads
                .retain(|quad| !Self::terrain_external_material_quad(quad));
            if !frame.text_quads.is_empty() {
                graph_frame.text_quads.clear();
            }
            // The builtin hand uses a fresh depth domain and must be lowered
            // after the Fabulous presentation blit. Keeping it in the
            // deferred graph would let that blit overwrite the hand.
            graph_frame.first_person = WorldFirstPersonFrame::default();
            graph_frame.first_person_mesh_instances.clear();
            // Direct primitive families render into the acquired image in the
            // ordinary path. In a Fabulous frame they are lowered into the
            // copied main attachment before the transparency graph.
            graph_frame.segments.clear();
            graph_frame.crack_quads.clear();
            graph_frame.border_quads.clear();
            (graph_frame, Some(frame))
        } else if preserve_post_graph_frame {
            (frame.clone(), Some(frame))
        } else {
            (frame, None)
        };
        whole_frame_phase_trace("pre-graph-total", world_frame_id, Some(pre_graph_started));
        let graph_target_started = std::time::Instant::now();
        let graph_target = if owned_world_target {
            let desc = passes::oriented_target::WorldTargetDesc {
                extent: gal.pass_target_extent(frame_target)?,
                color_format: gal.pass_target_color_format(frame_target)?,
                raster_y_direction: graph_direction,
            };
            if !self
                .oriented_world_target
                .as_ref()
                .is_some_and(|owner| owner.desc == desc)
            {
                let replacement = passes::oriented_target::OrientedWorldTarget::create(
                    gal,
                    "minecraft.world.output",
                    desc,
                )?;
                if let Some(old) = self.oriented_world_target.replace(replacement) {
                    self.clear_frame_passes_for_targets(gal, &[old.target]);
                    for handle in old.handles_in_destroy_order() {
                        gal.destroy(handle)?;
                    }
                }
            }
            if graph_direction == RasterYDirection::Down {
                let canonical_desc = passes::oriented_target::WorldTargetDesc {
                    raster_y_direction: RasterYDirection::Up,
                    ..desc
                };
                if !self
                    .canonical_world_target
                    .as_ref()
                    .is_some_and(|owner| owner.desc == canonical_desc)
                {
                    let replacement = passes::oriented_target::OrientedWorldTarget::create(
                        gal,
                        "minecraft.world.canonical-output",
                        canonical_desc,
                    )?;
                    if let Some(old) = self.canonical_world_target.replace(replacement) {
                        self.clear_frame_passes_for_targets(gal, &[old.target]);
                        for handle in old.handles_in_destroy_order() {
                            gal.destroy(handle)?;
                        }
                    }
                }
            }
            self.oriented_world_target
                .as_ref()
                .expect("owned world target prepared")
                .target
        } else {
            frame_target
        };
        whole_frame_phase_trace(
            "graph-target-prepare",
            world_frame_id,
            Some(graph_target_started),
        );
        let graph_started = std::time::Instant::now();
        let (graph_ops, mut stats) = match self.append_frame_ops_inner_with_source_preparation(
            gal,
            generation,
            graph_target,
            graph_frame,
            true,
            graph_direction,
            preparing_source_entry,
        ) {
            Ok(result) => result,
            Err(error) => {
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
        };
        whole_frame_phase_trace("graph-append", world_frame_id, Some(graph_started));
        let post_graph_compose_started = std::time::Instant::now();
        let (
            terrain_external_material_ops,
            mut terrain_external_roles_written,
            external_material_stats,
        ) = if self.pending_terrain_fabulous_handoff {
            let frame = post_graph_frame
                .as_ref()
                .expect("Fabulous handoff retains the complete semantic frame");
            match self.prepare_terrain_external_material_ops(gal, frame) {
                Ok(plan) => plan,
                Err(error) => {
                    gal.rollback_frame_target_depth_write(frame_target);
                    self.discard_pending_lowered_source_terrain_submission(gal);
                    self.discard_pending_g_buffer_depth_history_submission();
                    self.lod_gpu_residency.discard_submission(gal);
                    self.lod_textured_gpu_residency.discard_submission(gal);
                    if let Some(runtime) = self.shader_runtime.as_mut() {
                        runtime.discard_private_terrain_occupancy_submission();
                        runtime.discard_vanilla_lightmap_submission(gal);
                    }
                    return Err(error);
                }
            }
        } else {
            (Vec::new(), [false; 4], (0, 0, 0))
        };
        terrain_external_roles_written[0] = self.pending_terrain_external_item_entity_written;
        stats.material_quad_count = stats
            .material_quad_count
            .saturating_add(external_material_stats.0);
        stats.material_batch_count = stats
            .material_batch_count
            .saturating_add(external_material_stats.1);
        stats.material_draw_count = stats
            .material_draw_count
            .saturating_add(external_material_stats.2);
        let mut graph_ops = graph_ops;
        if owned_world_target {
            let owner = self
                .oriented_world_target
                .as_ref()
                .expect("owned world target prepared");
            graph_ops.insert(
                0,
                CommandOp::Barrier(texture_barrier(
                    owner.color_texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::ColorAttachment,
                )),
            );
            if graph_direction == RasterYDirection::Down {
                let canonical = self
                    .canonical_world_target
                    .as_ref()
                    .expect("canonical world output prepared");
                graph_ops.extend(owner.transfer_to(
                    canonical,
                    passes::oriented_target::WorldAttachmentStates::ATTACHMENTS,
                    passes::oriented_target::WorldAttachmentStates::UNDEFINED,
                    passes::oriented_target::WorldAttachmentStates::ATTACHMENTS,
                )?);
                graph_ops.extend(canonical.copy_to_frame(gal, frame_target)?);
            } else {
                graph_ops.extend(owner.copy_to_frame(gal, frame_target)?);
            }
            gal.begin_frame_target_depth_write(frame_target)?;
        }
        let mut ops = pre_graph_ops;
        let mut observation_reset_ops = Vec::new();
        for pipeline in self.mesh_pipeline_resources.values() {
            if let Some(observation) = &pipeline.vertex_observation {
                observation.begin(&mut observation_reset_ops);
            }
        }
        if !observation_reset_ops.is_empty() {
            observation_reset_ops.append(&mut ops);
            ops = observation_reset_ops;
        }
        ops.extend(graph_ops);
        if let Some(source_frame_for_admission) = source_activation_frame.as_ref() {
            let distant_depth_started = std::time::Instant::now();
            if let Err(error) = self.append_candidate_source_distant_depth_for_admission(
                gal,
                source_frame_for_admission,
                &mut ops,
            ) {
                gal.rollback_frame_target_depth_write(frame_target);
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.discard_pending_g_buffer_depth_history_submission();
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
            whole_frame_phase_trace(
                "candidate-distant-depth",
                world_frame_id,
                Some(distant_depth_started),
            );
            // DH depth joins the same source snapshot after named color targets
            // have already been staged by frame preparation. Re-run that bounded
            // color assembly so its declared output roles remain explicit in the
            // completeness check instead of being mistaken for missing samplers.
            let source_color_started = std::time::Instant::now();
            if let Err(error) = self.prepare_candidate_source_color_resources_for_admission(
                gal,
                source_frame_for_admission.voxel_volume.world_generation,
                Extent3d {
                    width: source_frame_for_admission.viewport_width,
                    height: source_frame_for_admission.viewport_height,
                    depth: 1,
                },
                source_frame_includes_distant_horizons(source_frame_for_admission),
            ) {
                gal.rollback_frame_target_depth_write(frame_target);
                return Err(error);
            }
            whole_frame_phase_trace(
                "candidate-source-colors",
                world_frame_id,
                Some(source_color_started),
            );
            // Refresh source admission only when that explicit path retained a
            // frame snapshot. Normal direct frames have no candidate resource
            // transaction to prepare after graph construction.
            self.write_runtime_source_admission_status(
                gal,
                source_frame_for_admission,
                "post-distant-depth-preparation",
                true,
            );
        }
        let mut terrain_presentation_pass = Handle::NULL;
        if self.pending_terrain_fabulous_handoff {
            let frame = post_graph_frame
                .as_ref()
                .expect("Fabulous handoff retains the complete semantic frame");
            let extent = Extent3d {
                width: frame.viewport_width,
                height: frame.viewport_height,
                depth: 1,
            };
            let (
                translucent_capture_texture,
                translucent_capture_depth_texture,
                world_text_depth_texture,
            ) = match self.g_buffer_resources.as_ref() {
                Some(resources) => (
                    resources.translucent_capture_texture,
                    resources.translucent_capture_depth_texture,
                    resources.depth_texture,
                ),
                None => {
                    self.pending_terrain_fabulous_handoff = false;
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(GalError::backend(
                        "G-buffer resources missing before terrain Fabulous handoff",
                    ));
                }
            };
            let set = match self.fabulous_attachment_set.as_ref() {
                Some(set) => set,
                None => {
                    self.pending_terrain_fabulous_handoff = false;
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(GalError::backend(
                        "Fabulous attachment set missing before terrain handoff",
                    ));
                }
            };
            terrain_presentation_pass = match set.append_terrain_handoff_to_frame_target_oriented(
                gal,
                &mut ops,
                frame_target,
                extent,
                frame_target,
                translucent_capture_texture,
                world_text_depth_texture,
                if self.pending_translucent_capture_written {
                    translucent_capture_depth_texture
                } else {
                    world_text_depth_texture
                },
                &terrain_external_material_ops,
                terrain_external_roles_written,
                self.fabulous_attachment_set_initialized,
                self.pending_translucent_capture_written,
                if graph_direction == RasterYDirection::Down {
                    TextureRowOrder::Reverse
                } else {
                    TextureRowOrder::Preserve
                },
            ) {
                Ok(pass) => pass,
                Err(error) => {
                    self.pending_terrain_fabulous_handoff = false;
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(error);
                }
            };
        }
        if self.pending_terrain_fabulous_handoff {
            let frame = post_graph_frame
                .as_ref()
                .expect("Fabulous handoff retains the complete semantic frame");
            let (overlay_ops, overlay_stats) = match self.prepare_terrain_overlay_ops(gal, frame) {
                Ok(result) => result,
                Err(error) => {
                    self.pending_terrain_fabulous_handoff = false;
                    if terrain_presentation_pass != Handle::NULL {
                        let _ = gal.destroy(terrain_presentation_pass);
                    }
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(error);
                }
            };
            ops.extend(overlay_ops);
            stats.segment_count = stats
                .segment_count
                .saturating_add(overlay_stats.segment_count);
            stats.vertex_count = stats
                .vertex_count
                .saturating_add(overlay_stats.vertex_count);
            stats.primitive_batch_count = stats
                .primitive_batch_count
                .saturating_add(overlay_stats.primitive_batch_count);
            stats.crack_quad_count = stats
                .crack_quad_count
                .saturating_add(overlay_stats.crack_quad_count);
            stats.crack_batch_count = stats
                .crack_batch_count
                .saturating_add(overlay_stats.crack_batch_count);
            stats.crack_draw_count = stats
                .crack_draw_count
                .saturating_add(overlay_stats.crack_draw_count);
            stats.border_quad_count = stats
                .border_quad_count
                .saturating_add(overlay_stats.border_quad_count);
            stats.border_batch_count = stats
                .border_batch_count
                .saturating_add(overlay_stats.border_batch_count);
            stats.border_draw_count = stats
                .border_draw_count
                .saturating_add(overlay_stats.border_draw_count);
            stats.world_draws = stats.world_draws.saturating_add(overlay_stats.world_draws);
        }
        if self.pending_terrain_fabulous_handoff {
            if let Some((hand, hand_instances)) = deferred_handoff_first_person {
                let frame = post_graph_frame
                    .as_ref()
                    .expect("Fabulous handoff retains the complete semantic frame");
                let (hand_ops, hand_stats) = match self.append_builtin_first_person_ops(
                    gal,
                    generation,
                    frame_target,
                    frame,
                    hand,
                    hand_instances,
                    RasterYDirection::Up,
                ) {
                    Ok(result) => result,
                    Err(error) => {
                        self.pending_terrain_fabulous_handoff = false;
                        if terrain_presentation_pass != Handle::NULL {
                            let _ = gal.destroy(terrain_presentation_pass);
                        }
                        gal.rollback_frame_target_depth_write(frame_target);
                        return Err(error);
                    }
                };
                ops.extend(hand_ops);
                stats.mesh_instance_count = stats
                    .mesh_instance_count
                    .saturating_add(hand_stats.mesh_instance_count);
                stats.mesh_batch_count = stats
                    .mesh_batch_count
                    .saturating_add(hand_stats.mesh_batch_count);
                stats.mesh_draw_count = stats
                    .mesh_draw_count
                    .saturating_add(hand_stats.mesh_draw_count);
                stats.world_draws = stats.world_draws.saturating_add(hand_stats.world_draws);
            }
        }
        if self.pending_terrain_fabulous_handoff && !deferred_handoff_text_quads.is_empty() {
            let frame = post_graph_frame
                .as_ref()
                .expect("Fabulous handoff retains the complete semantic frame");
            let (world_text_depth_texture, world_text_depth_view) =
                match self.g_buffer_resources.as_ref() {
                    Some(resources) => (resources.depth_texture, resources.depth_view),
                    None => {
                        self.pending_terrain_fabulous_handoff = false;
                        if terrain_presentation_pass != Handle::NULL {
                            let _ = gal.destroy(terrain_presentation_pass);
                        }
                        gal.rollback_frame_target_depth_write(frame_target);
                        return Err(GalError::backend(
                            "G-buffer resources missing before deferred terrain text",
                        ));
                    }
                };
            let text_pass = match self.color_only_frame_pass(gal, frame_target) {
                Ok(pass) => pass,
                Err(error) => {
                    self.pending_terrain_fabulous_handoff = false;
                    if terrain_presentation_pass != Handle::NULL {
                        let _ = gal.destroy(terrain_presentation_pass);
                    }
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(error);
                }
            };
            let color_attachment = match gal.pass_target_color_attachment(frame_target) {
                Ok(view) => view,
                Err(error) => {
                    self.pending_terrain_fabulous_handoff = false;
                    if terrain_presentation_pass != Handle::NULL {
                        let _ = gal.destroy(terrain_presentation_pass);
                    }
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(error);
                }
            };
            let color_format = match gal.pass_target_color_format(frame_target) {
                Ok(format) => format,
                Err(error) => {
                    self.pending_terrain_fabulous_handoff = false;
                    if terrain_presentation_pass != Handle::NULL {
                        let _ = gal.destroy(terrain_presentation_pass);
                    }
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(error);
                }
            };
            let text_stats = match self.world_text.append_frame_ops(
                gal,
                frame_target,
                text_pass,
                color_attachment,
                world_text_depth_texture,
                world_text_depth_view,
                TextureUsageState::ShaderRead,
                color_format,
                RasterYDirection::Up,
                frame.view_matrix,
                frame.projection_matrix,
                &deferred_handoff_text_quads,
                &mut ops,
                false,
            ) {
                Ok(stats) => stats,
                Err(error) => {
                    self.pending_terrain_fabulous_handoff = false;
                    if terrain_presentation_pass != Handle::NULL {
                        let _ = gal.destroy(terrain_presentation_pass);
                    }
                    gal.rollback_frame_target_depth_write(frame_target);
                    return Err(error);
                }
            };
            stats.world_text_quad_count = text_stats.quad_count;
            stats.world_text_batch_count = text_stats.batch_count;
            stats.world_text_draw_count = text_stats.draw_count;
            stats.world_text_clip_xy_visible_quad_count = text_stats.clip_xy_visible_quad_count;
            stats.world_text_first_ndc_bounds = text_stats.first_ndc_bounds;
            stats.world_text_first_ndc_corners = text_stats.first_ndc_corners;
            stats.world_text_ndc_bounds_sample = text_stats.ndc_bounds_sample;
            ops.push(CommandOp::Barrier(texture_barrier(
                world_text_depth_texture,
                TextureUsageState::DepthStencilAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        if let Some(capture) = gameplay_attachment_capture.as_mut() {
            // A full opt-in diagnostic retains both sides of the private DH
            // compositor boundary. `dh_private_color` is sparse and carries
            // geometry coverage in alpha; `dh_resolved_color` is the exact
            // fog/fade result sampled by the later apply/fade passes. Keeping
            // these out of final-only capture avoids any steady-state copy or
            // readback cost.
            let direct_dh_textures = (!capture.final_output_only
                && stats.lod_direct_composite_pass_count > 0)
                .then(|| {
                    self.lod_direct_composition_resources
                        .as_ref()
                        .map(|resources| {
                            (
                                resources.private_color_texture(),
                                resources.color_format,
                                resources.resolved_color_texture(),
                                stats
                                    .lod_ssao_pass_appended
                                    .then(|| resources.ssao_texture()),
                            )
                        })
                })
                .flatten();
            let capture_result = capture
                .append_ops(gal, &mut ops, self.g_buffer_resources.as_ref(), None)
                .and_then(|()| {
                    if let Some((
                        private_color,
                        private_color_format,
                        resolved_color,
                        ssao_texture,
                    )) = direct_dh_textures
                    {
                        capture.append_shader_read_attachment(
                            gal,
                            &mut ops,
                            "dh_private_color",
                            private_color,
                            private_color_format,
                        )?;
                        capture.append_shader_read_attachment(
                            gal,
                            &mut ops,
                            "dh_resolved_color",
                            resolved_color,
                            TextureFormat::Rgba16Float,
                        )?;
                        if let Some(texture) = ssao_texture {
                            capture.append_shader_read_attachment(
                                gal,
                                &mut ops,
                                "dh_ssao",
                                texture,
                                TextureFormat::Rgba16Float,
                            )?;
                        }
                    }
                    Ok(())
                });
            if let Err(error) = capture_result {
                self.world_text.cancel_submission();
                if terrain_presentation_pass != Handle::NULL {
                    let _ = gal.destroy(terrain_presentation_pass);
                }
                gal.rollback_frame_target_depth_write(frame_target);
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.discard_pending_g_buffer_depth_history_submission();
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                self.discard_candidate_source_distant_depth(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
            // The normal graph has already copied its final fullscreen result
            // into the acquired Rust-owned target at this point.  Retain that
            // exact pre-GUI image only for a full diagnostic capture so visual
            // parity can attribute a mismatch to the world final copy or to a
            // later semantic overlay without changing either render path.
            if let Err(error) = capture.append_normal_world_output(gal, &mut ops, frame_target) {
                self.world_text.cancel_submission();
                if terrain_presentation_pass != Handle::NULL {
                    let _ = gal.destroy(terrain_presentation_pass);
                }
                gal.rollback_frame_target_depth_write(frame_target);
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.discard_pending_g_buffer_depth_history_submission();
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                self.discard_candidate_source_distant_depth(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
        }
        if let Some(capture) = gameplay_attachment_capture.as_mut() {
            // Forward frames do not expose a G-buffer, but they still have a
            // Rust-owned depth attachment. Capture that exact attachment for
            // the opt-in audit before semantic GUI/hand passes can clear or
            // otherwise reuse it. This is observational only: the normal
            // route keeps the attachment in DepthStencilAttachment state and
            // does not add a copy.
            if self.g_buffer_resources.is_none() {
                let depth_texture =
                    if frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
                        gal.frame_target_owned_depth_attachment(frame_target)?.0
                    } else {
                        gal.pass_target_depth_attachment(frame_target)?
                            .map(|(texture, _)| texture)
                            .ok_or_else(|| {
                                GalError::backend(
                                    "forward gameplay attachment capture has no depth attachment",
                                )
                            })?
                    };
                if let Err(error) = capture.append_forward_depth_output(
                    gal,
                    &mut ops,
                    depth_texture,
                    TextureUsageState::DepthStencilAttachment,
                ) {
                    self.world_text.cancel_submission();
                    if terrain_presentation_pass != Handle::NULL {
                        let _ = gal.destroy(terrain_presentation_pass);
                    }
                    gal.rollback_frame_target_depth_write(frame_target);
                    self.discard_pending_lowered_source_terrain_submission(gal);
                    self.discard_pending_g_buffer_depth_history_submission();
                    self.lod_gpu_residency.discard_submission(gal);
                    self.lod_textured_gpu_residency.discard_submission(gal);
                    self.discard_candidate_source_distant_depth(gal);
                    if let Some(runtime) = self.shader_runtime.as_mut() {
                        runtime.discard_private_terrain_occupancy_submission();
                        runtime.discard_vanilla_lightmap_submission(gal);
                    }
                    return Err(error);
                }
            }
        }
        ops.extend(gui_ops);
        if let Some(capture) = gameplay_attachment_capture.as_mut() {
            if let Err(error) = capture.append_normal_presented_output(gal, &mut ops, frame_target)
            {
                self.world_text.cancel_submission();
                if terrain_presentation_pass != Handle::NULL {
                    let _ = gal.destroy(terrain_presentation_pass);
                }
                gal.rollback_frame_target_depth_write(frame_target);
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.discard_pending_g_buffer_depth_history_submission();
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                self.discard_candidate_source_distant_depth(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
        }
        stats.command_ops = ops.len() as u64;
        let observed_pipelines = self
            .mesh_pipeline_resources
            .values()
            .filter(|p| p.vertex_observation.is_some())
            .map(|p| (p.pipeline, p.pipeline_layout))
            .collect();
        diagnostics::vertex_observation::assign_draw_ranges(&mut ops, &observed_pipelines)?;
        for pipeline in self.mesh_pipeline_resources.values() {
            if let Some(observation) = &pipeline.vertex_observation {
                observation.end(&mut ops);
            }
        }
        if let Some(capture) = gameplay_attachment_capture.as_mut() {
            let frame = post_graph_frame
                .as_ref()
                .expect("attachment capture retains the complete semantic frame");
            capture.decal_inputs = diagnostics::decal_capture::observe(gal, self, frame, &ops);
            capture.equipment_inputs = diagnostics::equipment_capture::observe(gal, self, frame, &ops);
            if crate::core::environment::var_os("MATTMC_GRAPHICS_AUDIT").is_some() {
                capture.wolf_inputs = diagnostics::equipment_capture::observe_wolf(gal, self, frame, &ops);
            }
        }
        whole_frame_phase_trace(
            "post-graph-compose",
            world_frame_id,
            Some(post_graph_compose_started),
        );
        let partition_started = std::time::Instant::now();
        let command_lists = match Self::partition_command_lists_at_pass_boundaries(
            "minecraft.world-and-gui.frame.commands",
            ops,
            Self::bounded_command_list_limit(gal),
        ) {
            Ok(command_lists) => command_lists,
            Err(error) => {
                self.world_text.cancel_submission();
                if terrain_presentation_pass != Handle::NULL {
                    let _ = gal.destroy(terrain_presentation_pass);
                }
                gal.rollback_frame_target_depth_write(frame_target);
                self.pending_entity_outline_targets_written = false;
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.discard_pending_g_buffer_depth_history_submission();
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                self.discard_candidate_source_distant_depth(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
        };
        whole_frame_phase_trace(
            "command-list-partition",
            world_frame_id,
            Some(partition_started),
        );
        stats.command_lists = command_lists.len() as u64;
        let frontend_done_nanos = elapsed_nanos_u64(total_started);
        stats.profile.world_frontend_total_nanos = stats
            .profile
            .world_frontend_total_nanos
            .saturating_add(frontend_done_nanos);
        let token = match gal.submit_profiled(
            SubmissionBatch {
                label: "minecraft.world-and-gui.frame".to_string(),
                command_lists,
            },
            &mut stats.profile.gal,
        ) {
            Ok(token) => token,
            Err(error) => {
                self.world_text.cancel_submission();
                if terrain_presentation_pass != Handle::NULL {
                    let _ = gal.destroy(terrain_presentation_pass);
                }
                gal.rollback_frame_target_depth_write(frame_target);
                self.pending_entity_outline_targets_written = false;
                self.discard_pending_lowered_source_terrain_submission(gal);
                self.discard_pending_g_buffer_depth_history_submission();
                self.lod_gpu_residency.discard_submission(gal);
                self.lod_textured_gpu_residency.discard_submission(gal);
                self.discard_candidate_source_distant_depth(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
                return Err(error);
            }
        };
        let post_submit_confirm_started = std::time::Instant::now();
        if terrain_presentation_pass != Handle::NULL {
            gal.destroy(terrain_presentation_pass)?;
        }
        self.pending_terrain_fabulous_handoff = false;
        self.fabulous_attachment_set_initialized = true;
        if frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            gal.commit_frame_target_depth_write(frame_target)?;
        }
        if owned_world_target && gameplay_attachment_capture.is_some() {
            eprintln!("[VulkanicGAL] owned-world-output frame={} submission={} raster={:?} rowReverse={} colorCopy=true depthCopy=true presenter=existing-frame-owner",
                world_frame_id, token.submission.0, graph_direction, graph_direction == RasterYDirection::Down);
        }
        self.world_text.confirm_submission();
        // The submission has now validated and marked every handle used by
        // this semantic frame.  Replaced streamed resources can safely enter
        // GAL's normal submission-aware retirement path at this boundary.
        self.flush_deferred_mesh_resource_destroys(gal);
        if self.pending_graph_targets_written {
            if let Some(g_buffer) = self.g_buffer_resources.as_mut() {
                g_buffer.screen_targets_initialized = true;
                g_buffer.shadow_targets_initialized = true;
            }
            self.pending_graph_targets_written = false;
        }
        if self.pending_translucent_capture_written {
            if let Some(g_buffer) = self.g_buffer_resources.as_mut() {
                g_buffer.translucent_capture_initialized = true;
            }
            self.pending_translucent_capture_written = false;
        }
        if self.pending_lod_direct_composition_written {
            self.lod_direct_composition_initialized = true;
            self.pending_lod_direct_composition_written = false;
        }
        if self.pending_lod_ssao_written {
            self.lod_ssao_initialized = true;
            self.pending_lod_ssao_written = false;
        }
        if self.pending_lod_vanilla_sample_state_established {
            self.lod_vanilla_sample_state_initialized = true;
            self.pending_lod_vanilla_sample_state_established = false;
        }
        if self.pending_entity_outline_targets_written {
            self.entity_outline_targets_initialized = true;
            self.pending_entity_outline_targets_written = false;
        }
        if let Err(error) = self.confirm_candidate_source_distant_depth(gal, world_frame_id) {
            self.discard_candidate_source_distant_depth(gal);
            return Err(error);
        }
        if self.pending_g_buffer_depth_history_submission.is_none() && self.source_main_depth_history_required() {
        }
        if let Some(history_submission) = self.pending_g_buffer_depth_history_submission.take() {
            let active_graph_generation = self
                .g_buffer_resources
                .as_ref()
                .map(|resources| resources.generation)
                .ok_or_else(|| {
                    GalError::backend(
                        "main depth history resources were retired before submission confirmation",
                    )
                })?;
            if let Err(error) = self.confirm_g_buffer_depth_history_submission(
                history_submission,
                token.submission,
                active_graph_generation,
            ) {
                self.discard_pending_g_buffer_depth_history_submission();
                return Err(error);
            }
        }
        self.lod_gpu_residency.confirm_submission(gal)?;
        self.lod_textured_gpu_residency.confirm_submission(gal)?;
        self.release_uploaded_lod_gpu_payloads();
        let terrain_atlas_generation =
            self.mesh_texture_generation(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS);
        if let Some(runtime) = self.shader_runtime.as_mut() {
            if let Some(lightmap) = runtime.vanilla_lightmap_binding(true) {
                self.lod_opaque_pass_resources
                    .retain_lightmap_binding(gal, lightmap);
                self.lod_forward_opaque_pass_resources
                    .retain_lightmap_binding(gal, lightmap);
                self.lod_transparent_pass_resources
                    .retain_lightmap_binding(gal, lightmap);
                self.lod_water_pass_resources
                    .retain_lightmap_binding(gal, lightmap);
                if let Some(atlas) = self
                    .mesh_texture_resources
                    .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
                {
                    self.lod_exact_atlas_opaque_pass_resources.retain_bindings(
                        gal,
                        lod::WorldLodTerrainAtlasBinding {
                            mesh_generation: terrain_atlas_generation,
                            texture_view: atlas.view,
                            sampler: atlas.sampler,
                        },
                        lightmap,
                    );
                    if let Some(resources) =
                        self.lod_exact_atlas_forward_opaque_pass_resources.as_mut()
                    {
                        resources.retain_bindings(
                            gal,
                            lod::WorldLodTerrainAtlasBinding {
                                mesh_generation: terrain_atlas_generation,
                                texture_view: atlas.view,
                                sampler: atlas.sampler,
                            },
                            lightmap,
                        );
                    }
                    for resources in [
                        self.lod_exact_atlas_forward_transparent_side_pass_resources
                            .as_mut(),
                        self.lod_exact_atlas_forward_transparent_up_pass_resources
                            .as_mut(),
                        self.lod_exact_atlas_forward_water_pass_resources.as_mut(),
                    ]
                    .into_iter()
                    .flatten()
                    {
                        resources.retain_bindings(
                            gal,
                            lod::WorldLodTerrainAtlasBinding {
                                mesh_generation: terrain_atlas_generation,
                                texture_view: atlas.view,
                                sampler: atlas.sampler,
                            },
                            lightmap,
                        );
                    }
                    for resources in [
                        self.lod_exact_atlas_deferred_transparent_side_pass_resources
                            .as_mut(),
                        self.lod_exact_atlas_deferred_transparent_up_pass_resources
                            .as_mut(),
                        self.lod_exact_atlas_deferred_water_pass_resources.as_mut(),
                    ]
                    .into_iter()
                    .flatten()
                    {
                        resources.retain_bindings(
                            gal,
                            lod::WorldLodTerrainAtlasBinding {
                                mesh_generation: terrain_atlas_generation,
                                texture_view: atlas.view,
                                sampler: atlas.sampler,
                            },
                            lightmap,
                        );
                    }
                    self.lod_exact_atlas_source_pass_resources.retain_bindings(
                        gal,
                        lod::WorldLodTerrainAtlasBinding {
                            mesh_generation: terrain_atlas_generation,
                            texture_view: atlas.view,
                            sampler: atlas.sampler,
                        },
                    );
                }
            }
            if let Err(error) = runtime.confirm_vanilla_lightmap_submission(gal) {
                runtime.discard_vanilla_lightmap_submission(gal);
                return Err(error);
            }
            if let Err(error) = runtime.confirm_private_terrain_occupancy_submission() {
                runtime.discard_private_terrain_occupancy_submission();
                return Err(error);
            }
        }
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.retire_replaced_vanilla_lightmaps(gal)?;
        }
        if let Some(source_submission) = self.pending_lowered_source_terrain_submission.take() {
            if let Err(error) =
                self.confirm_source_terrain_frame_transaction(&source_submission, token.submission)
            {
                self.discard_source_terrain_frame_transaction(gal, world_frame_id);
                return Err(error);
            }
        }
        stats.submission_id = token.submission.0;
        if self
            .mesh_pipeline_resources
            .values()
            .any(|p| p.vertex_observation.is_some())
        {
            gal.retire_through(token.submission)?;
            let reads = gal.completed_host_reads();
            for pipeline in self.mesh_pipeline_resources.values_mut() {
                if let Some(observation) = &mut pipeline.vertex_observation {
                    observation.complete(
                        &reads,
                        token.submission.0,
                        gameplay_attachment_capture.is_some(),
                    )?;
                }
            }
        }
        if let Some(frame) = source_activation_frame
            .as_ref()
            .or(post_graph_frame.as_ref())
        {
            self.write_runtime_world_text_execution_receipt(frame, &stats, token.submission.0);
        }
        if let Some(capture) = gameplay_attachment_capture {
            gal.retire_through(token.submission)?;
            let reads = gal.completed_host_reads().to_vec();
            capture.write_artifacts(gal, reads, token.submission.0, &stats)?;
        }
        // The source route requires confirmation of this normal graph's
        // voxel/lightmap work. At world entry this graph targets private
        // attachments, then the same semantic frame rebuilds admission and
        // executes the selected source graph into the acquired image.
        if let Some(source_frame_for_admission) = source_activation_frame.as_ref() {
            self.arm_runtime_source_execution_if_ready(source_frame_for_admission);
            // Pre-submit status establishes that source resources were staged;
            // capture the separate route decision only after the normal Rust
            // submission has confirmed that exact-frame work. This remains a
            // bounded audit record and has no rendering effect.
            self.write_runtime_source_admission_status(
                gal,
                source_frame_for_admission,
                "post-normal-submission-confirmation",
                true,
            );
        } else {
            // The opt-in source route is disabled, so no frame snapshot was
            // retained. Keep the admission state explicitly disarmed without
            // copying the ordinary direct frame solely to reach the early-exit
            // branch of `arm_runtime_source_execution_if_ready`.
            self.source_execution_armed = false;
            self.source_execution_activation_reported = false;
            self.source_execution_distant_horizons_reported = false;
            self.source_execution_admission_reason =
                Some("runtime-selected-source-opt-in-disabled".to_string());
        }
        stats.profile.world_post_submit_confirm_nanos =
            elapsed_nanos_u64(post_submit_confirm_started);
        Ok(stats)
    }

    /// Records GUI semantics against the target selected by the frame route.
    /// A selected-source frame presents the shader-pack world first, then
    /// composes top-left GUI coordinates into the acquired Rust target in the
    /// same submission. This keeps the framebuffer-to-sampled-image source
    /// copy from vertically inverting GUI without borrowing Java/Iris state.
    pub(crate) fn submit_whole_frame_with_gui_frontend(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_frontend: &mut GuiFrontend,
        gui_sprites: Vec<GuiSpriteRequest>,
        gui_affine_quads: Vec<GuiAffineQuadRequest>,
        gui_mesh_batches: Vec<GuiMeshBatchRequest>,
        post_effect_id: Vec<u8>,
        gui_blur_before_stratum: i32,
        gui_blur_radius: i32,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)> {
        self.submit_whole_frame_with_tiled_gui_frontend(
            gal,
            generation,
            frame_target,
            frame,
            gui_frontend,
            gui_sprites,
            gui_affine_quads,
            gui_mesh_batches,
            post_effect_id,
            gui_blur_before_stratum,
            gui_blur_radius,
            Vec::new(),
        )
    }

    pub(crate) fn submit_whole_frame_with_tiled_gui_frontend(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_frontend: &mut GuiFrontend,
        gui_sprites: Vec<GuiSpriteRequest>,
        gui_affine_quads: Vec<GuiAffineQuadRequest>,
        gui_mesh_batches: Vec<GuiMeshBatchRequest>,
        post_effect_id: Vec<u8>,
        gui_blur_before_stratum: i32,
        gui_blur_radius: i32,
        gui_tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)> {
        // A freshly (re)armed shader route can still meet a resource race in
        // its first frames (startup, world or dimension change). Such a
        // failure must not end the game: keep the inputs for a bounded window
        // after arming and draw that frame with the vanilla Rust route while
        // the shader route re-prepares. Steady-state frames pay no copy.
        const ARMED_RETRY_WINDOW_FRAMES: u32 = 60;
        let armed = self.runtime_source_execution_is_armed();
        self.armed_submission_streak = if armed {
            self.armed_submission_streak.saturating_add(1)
        } else {
            0
        };
        let retry_inputs = (armed && self.armed_submission_streak <= ARMED_RETRY_WINDOW_FRAMES)
            .then(|| {
                (
                    frame.clone(),
                    gui_sprites.clone(),
                    gui_affine_quads.clone(),
                    gui_mesh_batches.clone(),
                    post_effect_id.clone(),
                    gui_tiled_quads.clone(),
                )
            });
        let result = self.submit_whole_frame_with_tiled_gui_frontend_attempt(
            gal,
            generation,
            frame_target,
            frame,
            gui_frontend,
            gui_sprites,
            gui_affine_quads,
            gui_mesh_batches,
            post_effect_id,
            gui_blur_before_stratum,
            gui_blur_radius,
            gui_tiled_quads,
        );
        let Err(error) = result else {
            return result;
        };
        let Some((frame, sprites, affine, meshes, post_effect_id, tiled)) = retry_inputs else {
            if !armed {
                return Err(error);
            }
            // Steady-state armed frames keep no input copy. Disarm and let the
            // Java coordinator resubmit this frame once; the resubmission is
            // drawn by the vanilla Rust route while the shader route re-arms.
            self.source_execution_armed = false;
            self.armed_submission_streak = 0;
            self.coverage_validated_frame_id = None;
            self.candidate_source_asset_error =
                Some(format!("selected-source submission failed: {error}"));
            self.discard_all_unsubmitted_source_terrain_frames(gal);
            return Err(GalError::new(
                error.domain,
                error.code,
                format!("{SELECTED_SOURCE_RETRYABLE_FAILURE}: {}", error.message),
            ));
        };
        self.source_execution_armed = false;
        self.armed_submission_streak = 0;
        self.coverage_validated_frame_id = None;
        self.candidate_source_asset_error =
            Some(format!("selected-source submission failed after arming: {error}"));
        self.discard_all_unsubmitted_source_terrain_frames(gal);
        self.submit_whole_frame_with_tiled_gui_frontend_attempt(
            gal,
            generation,
            frame_target,
            frame,
            gui_frontend,
            sprites,
            affine,
            meshes,
            post_effect_id,
            gui_blur_before_stratum,
            gui_blur_radius,
            tiled,
        )
    }

    pub(super) fn submit_whole_frame_with_tiled_gui_frontend_attempt(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_frontend: &mut GuiFrontend,
        gui_sprites: Vec<GuiSpriteRequest>,
        gui_affine_quads: Vec<GuiAffineQuadRequest>,
        gui_mesh_batches: Vec<GuiMeshBatchRequest>,
        post_effect_id: Vec<u8>,
        gui_blur_before_stratum: i32,
        gui_blur_radius: i32,
        gui_tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)> {
        let creates_before = gal.metrics().resource_creates;
        let destroys_before = gal.metrics().resource_destroys;
        gal.begin_command_recording()?;
        let result = self.submit_whole_frame_with_tiled_gui_frontend_recorded(
            gal,
            generation,
            frame_target,
            frame,
            gui_frontend,
            gui_sprites,
            gui_affine_quads,
            gui_mesh_batches,
            post_effect_id,
            gui_blur_before_stratum,
            gui_blur_radius,
            gui_tiled_quads,
        );
        let deferred_destroy_count = gal.command_recording_deferred_destroy_count() as u64;
        let finish_started = std::time::Instant::now();
        let finish = gal.finish_command_recording();
        let finish_nanos = elapsed_nanos_u64(finish_started);
        result.and_then(|(mut stats, gui_stats)| {
            finish.map(|()| {
                stats.profile.gal.gal_command_recording_finish_nanos = finish_nanos;
                stats.profile.gal.gal_command_recording_deferred_destroys = deferred_destroy_count;
                // SubmitProfile alone excludes frontend preparation and the
                // retirement boundary. This embedded profile covers the whole
                // accepted world/GUI attempt, including both lifecycle phases.
                stats.profile.gal.resource_creates_delta =
                    gal.metrics().resource_creates.saturating_sub(creates_before);
                stats.profile.gal.resource_destroys_delta =
                    gal.metrics().resource_destroys.saturating_sub(destroys_before);
                (stats, gui_stats)
            })
        })
    }

    pub(super) fn submit_whole_frame_with_tiled_gui_frontend_recorded(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_frontend: &mut GuiFrontend,
        gui_sprites: Vec<GuiSpriteRequest>,
        mut gui_affine_quads: Vec<GuiAffineQuadRequest>,
        mut gui_mesh_batches: Vec<GuiMeshBatchRequest>,
        post_effect_id: Vec<u8>,
        gui_blur_before_stratum: i32,
        gui_blur_radius: i32,
        gui_tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)> {
        self.discard_stale_pending_vanilla_lightmap(gal);
        self.disarm_source_route_on_extent_change(&frame);
        #[cfg(not(test))]
        let mut frame = frame;
        #[cfg(not(test))]
        self.prepare_runtime_source_before_presentation(gal, generation, frame_target, &mut frame)?;
        #[cfg(not(test))]
        let frame = self.admit_armed_source_frame(frame);
        #[cfg(not(test))]
        self.report_shader_route_outcome(&frame);
        crate::render::guirender::frontend::preflight_tiled_affine_count(
            &gui_tiled_quads,
            gui_affine_quads.len(),
        )?;
        crate::render::guirender::frontend::validate_gui_frame_sequences(
            &gui_sprites,
            &gui_affine_quads,
            &gui_mesh_batches,
            &gui_tiled_quads,
        )?;
        for batch in &mut gui_mesh_batches {
            batch.resolve_item_lighting(frame.shader_environment.vanilla_lightmap)?;
        }
        for quad in &mut gui_affine_quads {
            quad.material
                .resolve(frame.shader_environment.vanilla_lightmap)?;
            for layer in &mut quad.item_raster_layers {
                layer
                    .material
                    .resolve(frame.shader_environment.vanilla_lightmap)?;
            }
        }
        // The invert marker is carried as a semantic GUI asset for ABI
        // compatibility, but it is a fullscreen post effect rather than a
        // GUI sprite. Consume it here so Rust owns the snapshot/pass and the
        // marker cannot be rasterized as an ordinary overlay.
        let effect_name = std::str::from_utf8(&post_effect_id).unwrap_or("");
        let effect_name = effect_name
            .strip_prefix("minecraft:")
            .or_else(|| effect_name.split_once(':').map(|(_, path)| path))
            .unwrap_or(effect_name);
        // Fabulous transparency is a complete Rust-owned six-attachment
        // executor selected later by `submit_whole_frame`.  Do not resolve
        // that same identity through the generic single-target custom graph:
        // doing so would reject a valid Fabulous frame before the dedicated
        // executor can consume its semantic external attachments.
        let transparency_contract_is_bundled = effect_name == "transparency"
            && self.dedicated_post_effect_is_bundled("transparency")?;
        let fabulous_transparency_requested =
            transparency_contract_is_bundled && self.frame_has_fabulous_transparency_work(&frame);
        // Entity outlines have the same ownership split: the frame executor
        // prepares the Rust-owned mask/intermediate chain from semantic mesh
        // instances, while the generic custom graph cannot bind the
        // `minecraft:entity_outline` attachment.  Let the dedicated chain
        // consume its identity when this frame actually contains an outline.
        let entity_outline_requested = effect_name == "entity_outline"
            && features::outline::prepare_entity_outline_post_effect(&frame)?.is_some()
            && self.dedicated_post_effect_is_bundled("entity_outline")?;
        let terrain_handoff_meshes_reason = (effect_name == "transparency")
            .then(|| self.terrain_handoff_meshes_support_reason(&frame))
            .flatten();
        let terrain_handoff_meshes_supported = terrain_handoff_meshes_reason.is_none();
        let terrain_handoff_materials_supported = effect_name == "transparency"
            && self.terrain_handoff_material_quads_are_supported(&frame);
        let terrain_handoff_first_person_supported =
            effect_name == "transparency" && self.terrain_handoff_first_person_is_supported(&frame);
        let terrain_handoff_requested = transparency_contract_is_bundled
            && terrain_handoff_meshes_supported
            && frame.lod_instances.is_empty()
            && terrain_handoff_materials_supported
            && terrain_handoff_first_person_supported
            && !self.runtime_source_execution_is_armed()
            && !self.candidate_lowered_source_execution_requested()
            // The complete compact graph owns material-only frames (including
            // outlines). Terrain and other world work keep the full handoff.
            && !(fabulous_transparency_requested
                && Self::fabulous_material_frame_content_is_supported(&frame));
        self.pending_terrain_fabulous_handoff = terrain_handoff_requested;
        if terrain_handoff_requested {
            self.ensure_fabulous_attachment_set(
                gal,
                frame_target,
                &frame,
                if terrain_handoff_requested {
                    ColorFormat::Rgba8Unorm
                } else {
                    gal.pass_target_color_format(frame_target)?
                },
                if terrain_handoff_requested {
                    ColorFormat::Rgba8Unorm
                } else {
                    gal.pass_target_color_format(frame_target)?
                },
            )?;
        }
        // A selected Rust DH route already has an explicit ordinary-graph
        // writer for its LOD opaque/translucent/water draws.  When nearby
        // vanilla terrain contributes material quads in the same frame, the
        // compact Fabulous-only handoff is not the complete route: keep the
        // whole frame on the direct Rust graph instead of rejecting a valid
        // mixed DH/terrain submission.  Unselected LOD instances remain a
        // hard error below because silently omitting them would weaken
        // admission.
        let selected_dh_ordinary_graph = selected_dh_ordinary_graph_is_admissible(
            &frame,
            self.runtime_source_execution_is_armed(),
        );
        if effect_name == "transparency"
            && !fabulous_transparency_requested
            && !terrain_handoff_requested
            && self.frame_requires_transparency_composition(&frame)
            && self.frame_has_fabulous_transparency_work(&frame) == false
            && !selected_dh_ordinary_graph
        {
            if let Some(reason) = Self::fabulous_transparency_route_classification(&frame) {
                let unsupported_materials = frame
                    .material_quads
                    .iter()
                    .enumerate()
                    .filter(|(_, quad)| {
                        !matches!(
                            quad.material_mode,
                            WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT
                        ) && !Self::terrain_external_material_quad(quad)
                    })
                    .take(8)
                    .map(|(index, quad)| {
                        format!(
                            "{index}:mode={},source={}",
                            quad.material_mode, quad.source_program
                        )
                    })
                    .collect::<Vec<_>>()
                    .join(",");
                let first_person_instances = frame
                    .first_person_mesh_instances
                    .iter()
                    .take(8)
                    .map(|instance| {
                        format!(
                            "stratum={},key={},generation={},section={}",
                            instance.stratum,
                            instance.mesh_key,
                            instance.mesh_generation,
                            instance.mesh_section_index
                        )
                    })
                    .collect::<Vec<_>>()
                    .join(",");
                return Err(GalError::unsupported_feature(format!(
                    "Rust Fabulous transparency route is unavailable: {reason}; bundled_contract={transparency_contract_is_bundled}, handoff_meshes={terrain_handoff_meshes_supported}, handoff_mesh_reason={}, handoff_materials={terrain_handoff_materials_supported}, handoff_first_person={terrain_handoff_first_person_supported}, lod_instances={}, lod_route_selected={}, lod_frame_enabled={}, source_armed={}, source_requested={}, selected_dh_ordinary_graph={}, unsupported_materials=[{unsupported_materials}], first_person=(enabled={},clear_depth_before={},main_hand_instances={},instances=[{first_person_instances}])",
                    terrain_handoff_meshes_reason.as_deref().unwrap_or("not evaluated"),
                    frame.lod_instances.len(),
                    frame.lod_render_frame.rust_route_selected(),
                    frame.lod_render_frame.enabled,
                    self.runtime_source_execution_is_armed(),
                    self.candidate_lowered_source_execution_requested(),
                    selected_dh_ordinary_graph,
                    frame.first_person.enabled,
                    frame.first_person.clear_depth_before,
                    frame.first_person.main_hand_instance_count,
                )));
            }
        }
        // During the loading overlay Fabulous is already selected, but there
        // is no world mesh/attachment work to compose yet. The immutable
        // vanilla post-effect snapshot is staged independently of Iris and
        // can become active on the following semantic frame. Do not send that
        // empty transitional frame through the generic executor: it cannot
        // own Fabulous's external targets and would turn a not-yet-admitted
        // capability into a client crash. This is not a fallback or an
        // admission of transparency rendering; the Rust presenter simply
        // presents the loading/UI frame and retries after source staging.
        let transparency_snapshot_pending_without_world_work = effect_name == "transparency"
            && !self.frame_requires_transparency_composition(&frame)
            && !fabulous_transparency_requested
            && !terrain_handoff_requested;
        if effect_name == "transparency"
            && !fabulous_transparency_requested
            && !terrain_handoff_requested
            && !transparency_snapshot_pending_without_world_work
            && !selected_dh_ordinary_graph
        {
            return Err(GalError::unsupported_feature(format!(
                "Rust Fabulous transparency route is unadmitted: bundled_contract={transparency_contract_is_bundled}, translucent_semantic_work={}, meshes={}, first_person_meshes={}, material_quads={}, lod_instances={}, lod_route_selected={}, lod_frame_enabled={}, source_armed={}, source_requested={}, selected_dh_ordinary_graph={}",
                self.frame_requires_transparency_composition(&frame),
                frame.mesh_instances.len(),
                frame.first_person_mesh_instances.len(),
                frame.material_quads.len(),
                frame.lod_instances.len(),
                frame.lod_render_frame.rust_route_selected(),
                frame.lod_render_frame.enabled,
                self.runtime_source_execution_is_armed(),
                self.candidate_lowered_source_execution_requested(),
                selected_dh_ordinary_graph,
            )));
        }
        let custom_post_effect = if !effect_name.is_empty()
            && !fabulous_transparency_requested
            && !terrain_handoff_requested
            && !entity_outline_requested
            && !transparency_snapshot_pending_without_world_work
            && !selected_dh_ordinary_graph
        {
            Some(self.custom_post_effect_sources_with_globals(
                std::str::from_utf8(&post_effect_id).map_err(|_| {
                    GalError::invalid_argument("post-effect identity must be UTF-8")
                })?,
                frame.engine_globals,
                gal.capabilities().shader_conventions,
            )?)
        } else {
            None
        };
        let custom_external_targets = if custom_post_effect.is_some() {
            self.prepare_custom_external_post_effect_targets(
                gal,
                frame_target,
                &frame,
                std::str::from_utf8(&post_effect_id).map_err(|_| {
                    GalError::invalid_argument("post-effect identity must be UTF-8")
                })?,
            )?
        } else {
            None
        };
        let gui_sprites: Vec<GuiSpriteRequest> = gui_sprites
            .into_iter()
            .filter(|sprite| {
                sprite.sprite_id != crate::render::guirender::frontend::GUI_POST_EFFECT_INVERT_ID
                    && sprite.sprite_id
                        != crate::render::guirender::frontend::GUI_POST_EFFECT_CREEPER_ID
                    && sprite.sprite_id
                        != crate::render::guirender::frontend::GUI_POST_EFFECT_SPIDER_ID
            })
            .collect();
        if gui_blur_before_stratum >= 0 && !terrain_handoff_requested {
            let (mut gui_ops, gui_stats) = gui_frontend
                .append_frame_ops_with_owned_atlases_and_blur_boundary(
                    gal,
                    Some(self),
                    generation,
                    frame_target,
                    frame_target,
                    gui_sprites,
                    gui_affine_quads,
                    gui_mesh_batches,
                    gui_tiled_quads,
                    gui_blur_before_stratum,
                    gui_blur_radius,
                    false,
                )?;
            let mut gui_stats = gui_stats;
            if let Some(shader_sources) = custom_post_effect.as_ref() {
                let (mut custom_ops, custom_owned_targets) = gui_frontend
                    .append_custom_post_effect_with_owned_targets(
                        gal,
                        frame_target,
                        frame_target,
                        std::str::from_utf8(&post_effect_id)
                            .expect("validated post-effect identity"),
                        shader_sources,
                        custom_external_targets.as_ref(),
                    )?;
                custom_ops.extend(gui_ops);
                gui_ops = custom_ops;
                gui_stats = declare_gui_owned_targets(gui_stats, &custom_owned_targets);
            }
            return self
                .submit_whole_frame_with_gui_stats(
                    gal,
                    generation,
                    frame_target,
                    frame,
                    gui_ops,
                    gui_stats.clone(),
                )
                .map(|stats| (stats, gui_stats));
        }
        #[cfg(test)]
        if self.candidate_lowered_source_execution_requested() {
            return self.submit_complete_named_source_frame(
                gal,
                generation,
                frame_target,
                frame,
                |gal, target, color_attachment, diagnostic_capture| {
                    let mut post_ops = Vec::new();
                    // Private custom post-effect targets must be declared to
                    // the complete source frame's GUI target validation.
                    let mut post_owned_targets = Vec::new();
                    if let Some(shader_sources) = custom_post_effect.as_ref() {
                        let (custom_ops, custom_owned_targets) =
                            gui_frontend.append_custom_post_effect_with_owned_targets(
                                gal,
                                target,
                                color_attachment,
                                std::str::from_utf8(&post_effect_id)
                                    .expect("validated post-effect identity"),
                                shader_sources,
                                custom_external_targets.as_ref(),
                            )?;
                        post_ops.extend(custom_ops);
                        post_owned_targets = custom_owned_targets;
                    }
                    if gui_blur_before_stratum >= 0 {
                        let (gui_ops, stats) = gui_frontend
                            .append_frame_ops_with_tiled_blur_boundary(
                                gal,
                                generation,
                                target,
                                color_attachment,
                                gui_sprites.clone(),
                                gui_affine_quads.clone(),
                                gui_mesh_batches.clone(),
                                gui_tiled_quads.clone(),
                                gui_blur_before_stratum,
                                gui_blur_radius,
                                true,
                            )?;
                        post_ops.extend(gui_ops);
                        return Ok((
                            post_ops,
                            declare_gui_owned_targets(stats, &post_owned_targets),
                        ));
                    }
                    if diagnostic_capture {
                        let (gui_ops, stats) = gui_frontend
                            .append_frame_ops_to_transient_tiled_diagnostic_target(
                                gal,
                                generation,
                                target,
                                color_attachment,
                                gui_sprites.clone(),
                                gui_affine_quads.clone(),
                                gui_mesh_batches.clone(),
                                gui_tiled_quads.clone(),
                            )?;
                        post_ops.extend(gui_ops);
                        Ok((
                            post_ops,
                            declare_gui_owned_targets(stats, &post_owned_targets),
                        ))
                    } else {
                        let (gui_ops, stats) = gui_frontend
                            .append_frame_ops_with_tiled_quads_to_target(
                                gal,
                                generation,
                                target,
                                color_attachment,
                                None,
                                None,
                                None,
                                true,
                                gui_sprites.clone(),
                                gui_affine_quads.clone(),
                                gui_mesh_batches.clone(),
                                gui_tiled_quads.clone(),
                            )?;
                        post_ops.extend(gui_ops);
                        Ok((
                            post_ops,
                            declare_gui_owned_targets(stats, &post_owned_targets),
                        ))
                    }
                },
            );
        }
        #[cfg(not(test))]
        if self.runtime_source_execution_is_armed() {
            // A selected-source attachment capture is frame-local, but it
            // needs the same semantic GUI replay after the world transfer.
            // Keep request data copy-owned: no Java or backend state is
            // consulted by the diagnostic path.
            // Prepare the normal presentation GUI while the frontend still
            // has exclusive access to the Rust atlas owner. The complete
            // source planner later borrows its own frame resources while it
            // records the world graph, so trying to reacquire `self` from the
            // planner callback would violate the ownership boundary. These
            // operations target the acquired Rust frame target, exactly where
            // the source final-output plan presents its result.
            let (prepared_gui_ops, prepared_gui_stats) = if gui_blur_before_stratum < 0 {
                gui_frontend.append_frame_ops_with_owned_atlases_to_target(
                    gal,
                    Some(self),
                    generation,
                    frame_target,
                    frame_target,
                    None,
                    None,
                    None,
                    false,
                    gui_sprites.clone(),
                    gui_affine_quads.clone(),
                    gui_mesh_batches.clone(),
                    gui_tiled_quads.clone(),
                )?
            } else {
                (Vec::new(), GuiSubmitStats::default())
            };
            return self.submit_armed_runtime_source_frame_with_gui(
                gal,
                generation,
                frame_target,
                frame,
                move |gal, target, color_attachment, diagnostic_capture| {
                    let mut post_ops = Vec::new();
                    // Private custom post-effect targets must be declared to
                    // the complete source frame's GUI target validation.
                    let mut post_owned_targets = Vec::new();
                    if let Some(shader_sources) = custom_post_effect.as_ref() {
                        let (custom_ops, custom_owned_targets) =
                            gui_frontend.append_custom_post_effect_with_owned_targets(
                                gal,
                                target,
                                color_attachment,
                                std::str::from_utf8(&post_effect_id)
                                    .expect("validated post-effect identity"),
                                shader_sources,
                                custom_external_targets.as_ref(),
                            )?;
                        post_ops.extend(custom_ops);
                        post_owned_targets = custom_owned_targets;
                    }
                    if gui_blur_before_stratum >= 0 {
                        let (gui_ops, stats) = gui_frontend
                            .append_frame_ops_with_tiled_blur_boundary(
                                gal,
                                generation,
                                target,
                                color_attachment,
                                gui_sprites.clone(),
                                gui_affine_quads.clone(),
                                gui_mesh_batches.clone(),
                                gui_tiled_quads.clone(),
                                gui_blur_before_stratum,
                                gui_blur_radius,
                                false,
                            )?;
                        post_ops.extend(gui_ops);
                        return Ok((
                            post_ops,
                            declare_gui_owned_targets(stats, &post_owned_targets),
                        ));
                    }
                    if !diagnostic_capture {
                        post_ops.extend(prepared_gui_ops.clone());
                        return Ok((
                            post_ops,
                            declare_gui_owned_targets(
                                prepared_gui_stats.clone(),
                                &post_owned_targets,
                            ),
                        ));
                    }
                    let (gui_ops, stats) = gui_frontend
                        .append_frame_ops_with_tiled_quads_to_target(
                            gal,
                            generation,
                            target,
                            color_attachment,
                            None,
                            None,
                            None,
                            false,
                            gui_sprites.clone(),
                            gui_affine_quads.clone(),
                            Vec::new(),
                            gui_tiled_quads.clone(),
                        )?;
                    post_ops.extend(gui_ops);
                    Ok((
                        post_ops,
                        declare_gui_owned_targets(stats, &post_owned_targets),
                    ))
                },
            );
        }

        let (mut gui_ops, gui_stats) = if gui_blur_before_stratum >= 0 {
            gui_frontend.append_frame_ops_with_owned_atlases_and_blur_boundary(
                gal,
                Some(self),
                generation,
                frame_target,
                frame_target,
                gui_sprites,
                gui_affine_quads,
                gui_mesh_batches,
                gui_tiled_quads,
                gui_blur_before_stratum,
                gui_blur_radius,
                false,
            )?
        } else {
            gui_frontend.append_frame_ops_with_owned_atlases_to_target(
                gal,
                Some(self),
                generation,
                frame_target,
                frame_target,
                None,
                None,
                None,
                false,
                gui_sprites,
                gui_affine_quads,
                gui_mesh_batches,
                gui_tiled_quads,
            )?
        };
        let mut gui_stats = gui_stats;
        if let Some(shader_sources) = custom_post_effect.as_ref() {
            let (mut custom_ops, custom_owned_targets) = gui_frontend
                .append_custom_post_effect_with_owned_targets(
                    gal,
                    frame_target,
                    frame_target,
                    std::str::from_utf8(&post_effect_id).expect("validated post-effect identity"),
                    shader_sources,
                    custom_external_targets.as_ref(),
                )?;
            custom_ops.extend(gui_ops);
            gui_ops = custom_ops;
            gui_stats = declare_gui_owned_targets(gui_stats, &custom_owned_targets);
        }
        // Source preparation inside this submission may arm the selected
        // route for this frame; keep the recorded GUI declarations with the
        // operations so that route validates them like its own GUI.
        self.submit_whole_frame_with_gui_stats(
            gal,
            generation,
            frame_target,
            frame,
            gui_ops,
            gui_stats.clone(),
        )
        .map(|stats| (stats, gui_stats))
    }

    pub fn submit_partial_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
    ) -> GalResult<WorldPrimitiveSubmitStats> {
        self.world_text.begin_submission();
        if self.candidate_lowered_source_execution_requested() {
            return Err(GalError::unsupported_feature(
                "lowered selected-source terrain execution requires one combined whole-frame submission",
            ));
        }
        let (ops, mut stats) = self.append_frame_ops_inner(
            gal,
            generation,
            frame_target,
            frame,
            false,
            RasterYDirection::Up,
        )?;
        if self.pending_entity_outline_targets_written {
            gal.rollback_frame_target_depth_write(frame_target);
            self.pending_entity_outline_targets_written = false;
            return Err(GalError::unsupported_feature(
                "entity outline requires submit_whole_frame for target-state confirmation",
            ));
        }
        if self.pending_g_buffer_depth_history_submission.is_some() {
            gal.rollback_frame_target_depth_write(frame_target);
            self.discard_pending_g_buffer_depth_history_submission();
            return Err(GalError::unsupported_feature(
                "main depth history requires submit_whole_frame for transaction confirmation",
            ));
        }
        stats.command_ops = ops.len() as u64;
        let command_lists = match Self::partition_command_lists_at_pass_boundaries(
            "minecraft.world-primitives.partial-frame.commands",
            ops,
            Self::bounded_command_list_limit(gal),
        ) {
            Ok(command_lists) => command_lists,
            Err(error) => {
                self.world_text.cancel_submission();
                gal.rollback_frame_target_depth_write(frame_target);
                return Err(error);
            }
        };
        stats.command_lists = command_lists.len() as u64;
        let token = match gal.submit(SubmissionBatch {
            label: "minecraft.world-primitives.partial-frame".to_string(),
            command_lists,
        }) {
            Ok(token) => token,
            Err(error) => {
                self.world_text.cancel_submission();
                gal.rollback_frame_target_depth_write(frame_target);
                return Err(error);
            }
        };
        if let Err(error) = gal.commit_frame_target_depth_write(frame_target) {
            self.world_text.cancel_submission();
            return Err(error);
        }
        self.world_text.confirm_submission();
        stats.submission_id = token.submission.0;
        Ok(stats)
    }

    pub fn append_frame_ops(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        if self.candidate_lowered_source_execution_requested() {
            return Err(GalError::unsupported_feature(
                "lowered selected-source terrain execution requires submit_whole_frame for transaction confirmation",
            ));
        }
        let result = self.append_frame_ops_inner(
            gal,
            generation,
            frame_target,
            frame,
            true,
            RasterYDirection::Up,
        );
        if self.pending_entity_outline_targets_written {
            self.pending_entity_outline_targets_written = false;
            return Err(GalError::unsupported_feature(
                "entity outline requires submit_whole_frame for target-state confirmation",
            ));
        }
        if self.pending_g_buffer_depth_history_submission.is_some() {
            self.discard_pending_g_buffer_depth_history_submission();
            return Err(GalError::unsupported_feature(
                "main depth history requires submit_whole_frame for transaction confirmation",
            ));
        }
        result
    }
}

/// Adds private GUI-owned targets written by GUI-side post work (custom
/// post-effect intermediates) to the stats that the complete source frame
/// validates its GUI command stream against.
fn declare_gui_owned_targets(mut stats: GuiSubmitStats, owned_targets: &[Handle]) -> GuiSubmitStats {
    for target in owned_targets {
        if !stats.owned_intermediate_targets.contains(target) {
            stats.owned_intermediate_targets.push(*target);
        }
    }
    stats
}

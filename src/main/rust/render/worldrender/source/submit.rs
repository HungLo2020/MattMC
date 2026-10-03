//! Submission of complete named-source and armed runtime frames.

use super::*;

impl WorldPrimitiveFrontend {
    /// Appends this frame's private terrain voxel work (occupancy, colored
    /// light flood-fill, puddles) when the runtime owns such volumes. Iris
    /// packs update these every frame; both the graph route and the armed
    /// named-source route record them ahead of their passes. Returns whether
    /// a private occupancy runtime is installed.
    pub(crate) fn append_private_terrain_occupancy_for_frame(
        &mut self,
        occupancy_frame: &WorldPrimitiveFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        // Occupancy submissions are confirmed by the call that submits them;
        // one still pending here was left by an aborted frame (either route).
        if let Some(runtime) = self.shader_runtime.as_mut() {
            if runtime.has_pending_private_terrain_occupancy_submission() {
                runtime.discard_private_terrain_occupancy_submission();
            }
        }
        if !self
            .shader_runtime
            .as_ref()
            .is_some_and(|runtime| runtime.has_private_terrain_occupancy())
        {
            return Ok(false);
        }
        let puddle_descriptor = self.candidate_puddle_descriptor_for_frame(occupancy_frame)?;
        let mapping_result = self
            .shader_runtime
            .as_ref()
            .expect("private terrain occupancy checked before mapping preparation")
            .private_terrain_occupancy_descriptor()
            .map(|descriptor| {
                let mapping = Self::voxel_volume_mapping_from_frame(
                    occupancy_frame.voxel_volume,
                    descriptor,
                )?;
                let view_direction = descriptor
                    .requirements
                    .update_policy
                    .preserve_behind_view
                    .then(|| {
                        VoxelLightVolumeViewDirection::from_camera_matrices(
                            occupancy_frame.view_matrix,
                            occupancy_frame.projection_matrix,
                        )
                    })
                    .transpose()?;
                Ok((mapping, view_direction))
            })
            .transpose();
        let (mapping, view_direction) = match mapping_result? {
            Some((mapping, view_direction)) => (Some(mapping), view_direction),
            None => (None, None),
        };
        // Only meshes touching the voxel volume can change it. The puddle
        // field has its own (shadow-scene) extent, so it keeps every mesh.
        let cull = mapping
            .filter(|_| puddle_descriptor.is_none())
            .map(|mapping| [mapping.valid_world_min, mapping.valid_world_max_exclusive]);
        let source_meshes = self.terrain_voxel_source_meshes_within(occupancy_frame, cull)?;
        let runtime = self
            .shader_runtime
            .as_mut()
            .expect("private terrain occupancy checked before frame submission");
        if let Err(error) = runtime.append_private_terrain_occupancy(
            occupancy_frame.frame_id,
            mapping,
            view_direction,
            puddle_descriptor,
            source_meshes,
            operations,
        ) {
            runtime.discard_private_terrain_occupancy_submission();
            return Err(error);
        }
        Ok(true)
    }

    /// A lightmap staged by a frame attempt that failed before reaching its
    /// discard/confirm owner was never submitted. Left pending, the next frame
    /// would treat it as already staged and sample its never-uploaded image,
    /// so each frame entry drops it (and the consumer sets that reference its
    /// view) before recording.
    pub(crate) fn discard_stale_pending_vanilla_lightmap(&mut self, gal: &mut VulkanicGal) {
        if !self
            .shader_runtime
            .as_ref()
            .is_some_and(|runtime| runtime.has_pending_vanilla_lightmap_submission())
        {
            return;
        }
        self.release_lightmap_dependent_source_pack_resources(gal);
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.discard_vanilla_lightmap_submission(gal);
        }
    }

    /// Initializes source prerequisites without writing the acquired image.
    /// The ordinary graph owns depth history, lightmaps and voxel uploads;
    /// prepare those through an offscreen GAL target, then rebuild admission
    /// for the same semantic frame and the actual final target. Only the
    /// subsequent selected-source submission may present this world frame.
    pub(crate) fn prepare_runtime_source_before_presentation(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: &mut WorldPrimitiveFrame,
    ) -> GalResult<()> {
        if self.runtime_source_execution_is_armed()
            || !self.runtime_source_preparation_requested()
            || !frame.background.enabled
            || frame.voxel_volume.world_generation == 0
            || frame_target.kind() != Some(crate::render::vulkanic::handles::HandleKind::FrameTarget)
        {
            return Ok(());
        }
        let owner = passes::oriented_target::OrientedWorldTarget::create(
            gal,
            "minecraft.source.entry-preparation",
            passes::oriented_target::WorldTargetDesc {
                extent: gal.pass_target_extent(frame_target)?,
                color_format: gal.pass_target_color_format(frame_target)?,
                raster_y_direction: RasterYDirection::Up,
            },
        )?;
        let result = (|| {
            self.submit_whole_frame_with_initial_ops(
                gal, generation, owner.target, frame.clone(), Vec::new(), None,
                vec![CommandOp::Barrier(texture_barrier(
                    owner.color_texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::ColorAttachment,
                ))],
                true,
            )?;
            // Confirmation makes the newly written history available. The
            // pre-submit snapshot still described the uninitialized history;
            // rebuild it before testing this frame's source admission.
            if self.shader_runtime.is_some() && self.shader_pack_sources.active().is_some() {
                self.ensure_candidate_source_assets_for_frame(
                    gal, frame.voxel_volume.world_generation, frame.frame_id,
                    false, source_frame_includes_distant_horizons(frame),
                )?;
                // Rejected contracts/assets deliberately leave no snapshot and
                // retain their admission error. They cannot participate in
                // final-target correlation or confirmed-depth merging. Keep the
                // source unarmed instead of turning that rejection into a
                // backend failure during world entry.
                if self.candidate_source_resource_snapshot.is_some() {
                    self.prepare_runtime_source_snapshot(gal, generation, frame_target, frame)?;
                    self.arm_runtime_source_execution_if_ready(frame);
                } else {
                    self.source_execution_armed = false;
                }
            }
            Ok(())
        })();
        self.clear_frame_passes_for_targets(gal, &[owner.target]);
        let mut cleanup = Ok(());
        for handle in owner.handles_in_destroy_order() {
            if let Err(error) = gal.destroy(handle) {
                cleanup = Err(error);
            }
        }
        result.and(cleanup)
    }

    /// Stages the exact current-frame source snapshot before the complete
    /// source executor records its one real submission. This intentionally
    /// uses the ordinary Rust frontend only as a private semantic/resource
    /// preparer: the generated normal-graph commands are discarded, never
    /// submitted, and cannot become a second presenter or Java fallback.
    pub(crate) fn prepare_runtime_source_snapshot(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: &mut WorldPrimitiveFrame,
    ) -> GalResult<()> {
        // The selected route reached this method only after a prior complete
        // transaction armed it. The provisional assembly below intentionally
        // lacks the frame's merged DH depth/color roles, so retain that arm
        // until the same transaction performs its final role validation.
        let preserve_source_execution_arm = self.source_execution_armed;
        // Clone without the per-instance streams (cleared below anyway):
        // copying thousands of mesh instances only to drop them was a
        // measurable per-frame cost.
        let mut snapshot = {
            let mesh_instances = std::mem::take(&mut frame.mesh_instances);
            let first_person_mesh_instances =
                std::mem::take(&mut frame.first_person_mesh_instances);
            let lod_instances = std::mem::take(&mut frame.lod_instances);
            let dh_generic_boxes = std::mem::take(&mut frame.dh_generic_boxes);
            let snapshot = frame.clone();
            frame.mesh_instances = mesh_instances;
            frame.first_person_mesh_instances = first_person_mesh_instances;
            frame.lod_instances = lod_instances;
            frame.dh_generic_boxes = dh_generic_boxes;
            snapshot
        };
        let frame: &WorldPrimitiveFrame = frame;
        // This provisional graph owns no presentation and is discarded. Its
        // only purpose is to refresh target/resource semantics before the
        // actual selected-source planner consumes `frame` below. Do not make
        // it upload ordinary terrain/entity geometry: that data has a distinct
        // ABI from the selected source stream and would otherwise be rebuilt
        // on every source frame solely to discard its commands.
        snapshot.mesh_instances.clear();
        snapshot.first_person_mesh_instances.clear();
        snapshot.first_person = WorldFirstPersonFrame::default();
        // The normal internal DH material pass is not a source-pass stand-in.
        // The complete source transaction below consumes the real selected DH
        // semantics through its dedicated source plan.
        snapshot.lod_instances.clear();
        snapshot.dh_generic_boxes.clear();
        snapshot.lod_render_frame = WorldLodRenderFrame::default();
        // This call only rebuilds the exact semantic snapshot.  Do not let
        // the normal graph start a lowered source terrain transaction before
        // the selected planner has merged its owned depth-history resources.
        // Restore the arm immediately afterward; the final selected submit
        // remains gated by the exact-frame completeness checks.
        let source_arm = self.source_execution_armed;
        self.source_execution_armed = false;
        let lightmap_was_pending = self
            .shader_runtime
            .as_ref()
            .is_some_and(|runtime| runtime.has_pending_vanilla_lightmap_submission());
        let provisional = self.append_frame_ops_inner(
            gal,
            generation,
            frame_target,
            snapshot,
            true,
            RasterYDirection::Up,
        );
        self.source_execution_armed = source_arm;
        // The provisional operations are dropped below, including any
        // lightmap upload they staged; later consumers must record it again.
        if !lightmap_was_pending {
            if let Some(runtime) = self.shader_runtime.as_mut() {
                runtime.forget_recorded_vanilla_lightmap_upload();
            }
        }
        let _ = provisional?;
        // `append_frame_ops_inner` may refresh or retire a source-owned
        // target while assembling the preparatory normal graph. Rebuild the
        // tiny by-value resource snapshot at the selected frame boundary
        // before merging the already-confirmed DH depth pair. The resource
        // owners remain cached; this only restores the strict same-frame
        // correlation required by the selected transaction.
        let validates_runtime_source_roles = self.runtime_source_preparation_requested();
        // The provisional frame deliberately has no terrain, entity, or DH
        // instances. Its only job is to materialize target metadata. Reusing
        // ensure_candidate_source_assets_for_frame here would rebuild the
        // exact-frame semantic snapshot against that empty frame while the
        // G-buffer-dependent shadow/depth roles are still being staged, which
        // can erase otherwise complete roles. The real frame assembled its
        // snapshot immediately before this pass; preserve it and let the
        // final selected transaction perform the authoritative revalidation.
        if validates_runtime_source_roles && self.candidate_source_resource_snapshot.is_none() {
            let _ = self.ensure_candidate_source_assets_for_frame(
                gal,
                frame.voxel_volume.world_generation,
                frame.frame_id,
                false,
                // DH depth is merged immediately below from its confirmed
                // generation. Do not classify the provisional resource record as
                // incomplete before that merge; doing so would disarm an already
                // validated route between the two halves of one transaction.
                false,
            )?;
        }
        self.merge_active_candidate_source_distant_depth(frame)?;
        if validates_runtime_source_roles {
            self.try_prepare_candidate_source_color_resources_for_admission(
                gal,
                frame.voxel_volume.world_generation,
                Extent3d {
                    width: frame.viewport_width,
                    height: frame.viewport_height,
                    depth: 1,
                },
                source_frame_includes_distant_horizons(frame),
            )?;
            // Color preparation just checked the merged resources together
            // with the complete graph's declared outputs. Those outputs are
            // initialized by its bootstrap/writers, even when no terrain
            // draws exist yet. Rechecking without declared outputs would
            // incorrectly require a previous terrain frame to supply them.

            // Rebuilding the exact-frame source resource snapshot above
            // deliberately clears its final-target correlation. Reattach the
            // existing backend-neutral G-buffer final binding for this same
            // acquired target only after all source roles have been merged.
            // The binding cache owns no native identity: the GAL target and
            // optional depth view remain opaque handles here.
            let color_format = gal.pass_target_color_format(frame_target)?;
            let final_depth_view =
                if frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
                    Some(gal.frame_target_owned_depth_attachment(frame_target)?.1)
                } else {
                    gal.pass_target_depth_attachment(frame_target)?
                        .map(|(_, view)| view)
                };
            let mut profile = WholeFrameProfile::default();
            let final_binding = self.ensure_g_buffer_final_binding(
                gal,
                frame_target,
                color_format,
                final_depth_view,
                &mut profile,
            )?;
            if !self.record_candidate_source_frame_target(final_binding) {
                return Err(GalError::backend(
                    "selected source frame rebuilt no resource snapshot before final-target correlation",
                ));
            }
        }
        if preserve_source_execution_arm
            && (!validates_runtime_source_roles
                || self.candidate_source_missing_resource_roles.is_empty())
        {
            self.source_execution_armed = true;
        }
        // Snapshot preparation deliberately records the ordinary Rust graph
        // only to assemble exact-frame semantic resources. Its depth-history
        // plan is normally confirmed by that graph's submission, but this
        // preparatory command list is intentionally discarded before the one
        // selected-source submission. Drop that unsubmitted bookkeeping here;
        // it is not a source-route transaction and must not block the route.
        self.discard_pending_g_buffer_depth_history_submission();
        if self.pending_lowered_source_terrain_submission.is_some() {
            self.discard_pending_lowered_source_terrain_submission(gal);
            return Err(GalError::backend(
                "source snapshot preparation unexpectedly staged a submit-bound transaction",
            ));
        }
        Ok(())
    }

    pub(crate) fn submit_armed_runtime_source_frame(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        gui_ops: Vec<CommandOp>,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)> {
        self.submit_armed_runtime_source_frame_with_gui(
            gal,
            generation,
            frame_target,
            frame,
            move |_, _, _, _| Ok((gui_ops.clone(), GuiSubmitStats::default())),
        )
    }

    pub(crate) fn submit_armed_runtime_source_frame_with_gui<F>(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        mut frame: WorldPrimitiveFrame,
        append_gui: F,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)>
    where
        F: FnMut(
            &mut VulkanicGal,
            Handle,
            Handle,
            bool,
        ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)>,
    {
        let started = std::time::Instant::now();
        let admitted = self.coverage_validated_frame_id == Some(frame.frame_id);
        // A pack that draws its own clouds suppresses vanilla cloud faces.
        // Drop them before per-quad validation and packing (at the default
        // cloud range there are ~100k), keeping their count as evidence.
        if !admitted {
            self.pre_dropped_suppressed_cloud_quads = 0;
        }
        if self
            .shader_runtime
            .as_ref()
            .is_some_and(ShaderPackRuntimeExecutor::suppresses_vanilla_cloud_faces)
        {
            let before = frame.material_quads.len();
            frame
                .material_quads
                .retain(|quad| quad.source_program != WORLD_MATERIAL_SOURCE_CLOUDS);
            self.pre_dropped_suppressed_cloud_quads += (before - frame.material_quads.len()) as u64;
        }
        self.write_runtime_source_execution_attempt(&frame, "entered", started.elapsed());
        self.prepare_runtime_source_snapshot(gal, generation, frame_target, &mut frame)?;
        self.write_runtime_source_execution_attempt(&frame, "snapshot-prepared", started.elapsed());
        // Route arming was established by a prior confirmed frame. Recheck
        // the current frame after its exact source snapshot exists: a newly
        // active vanilla producer or DH layer must become an explicit failure
        // rather than disappearing from the selected source submission.
        if let Err(error) = if admitted {
            Ok(())
        } else {
            self.validate_selected_source_frame_coverage(&frame)
        } {
            self.source_execution_armed = false;
            self.candidate_source_asset_error = Some(format!(
                "selected-source current-frame coverage is incomplete: {error}"
            ));
            self.write_runtime_source_admission_status(
                gal,
                &frame,
                "current-frame-coverage-rejected",
                true,
            );
            return Err(error);
        }
        self.write_runtime_source_execution_attempt(
            &frame,
            "coverage-validated",
            started.elapsed(),
        );
        self.retire_ordinary_mesh_execution_resources_for_selected_source();
        // Do this before source geometry is materialized below. The explicit
        // GAL destruction path preserves any previous in-flight frame, while
        // resources created solely by the discarded snapshot release their
        // backing allocations before they can overlap the selected ABI.
        self.flush_deferred_mesh_resource_destroys(gal);
        // The post-submit reports are audit-only; copying a whole world frame
        // (every mesh instance and material quad) just to feed them cost a
        // large memcpy per frame, so keep a copy only when auditing.
        let audit_frame = graphics_audit_enabled().then(|| frame.clone());
        self.coverage_validated_frame_id = Some(frame.frame_id);
        let (stats, gui_stats) = self.submit_complete_named_source_frame(
            gal,
            generation,
            frame_target,
            frame,
            append_gui,
        )?;
        // A successful selected submission has just validated and executed
        // the complete current-frame source graph. Keep that confirmed route
        // eligible for the next frame; the planner still rebuilds and checks
        // its exact resources before any later selected submission.
        self.source_execution_armed = self.candidate_source_missing_resource_roles.is_empty();
        if let Some(frame) = audit_frame.as_ref() {
            self.write_runtime_source_execution_attempt(frame, "submitted", started.elapsed());
            self.report_runtime_source_execution(frame, &stats, &gui_stats);
            self.write_runtime_source_execution_latest(frame, &stats, &gui_stats);
            self.write_runtime_source_execution_attempt(frame, "reported", started.elapsed());
        }
        Ok((stats, gui_stats))
    }

    /// Splits a complete source frame only at pass boundaries so a backend can
    /// enforce a bounded per-list validation budget without changing frame
    /// ordering, hazard analysis, submission count, or presentation ownership.
    pub(crate) fn partition_command_lists_at_pass_boundaries(
        label: &str,
        operations: Vec<CommandOp>,
        max_operations_per_list: usize,
    ) -> GalResult<Vec<CommandList>> {
        if max_operations_per_list == 0 {
            return Err(GalError::invalid_argument(
                "source command-list partition requires a nonzero backend operation limit",
            ));
        }

        // A segment contains the outside-pass setup immediately before a
        // pass plus that complete pass. The next segment starts with the
        // barriers after EndPass. That makes every segment safe to move to a
        // later list without resetting a live graphics-pass state.
        let mut segments = Vec::<Vec<CommandOp>>::new();
        let mut segment = Vec::new();
        for operation in operations {
            let ends_pass = matches!(operation, CommandOp::EndPass);
            segment.push(operation);
            if ends_pass {
                segments.push(std::mem::take(&mut segment));
            }
        }
        if !segment.is_empty() {
            segments.push(segment);
        }

        let mut command_lists = Vec::new();
        let mut current = Vec::new();
        for segment in segments {
            for segment in Self::split_oversized_graphics_pass_segment(
                label,
                segment,
                max_operations_per_list,
            )? {
                if !current.is_empty()
                    && current.len().saturating_add(segment.len()) > max_operations_per_list
                {
                    let index = command_lists.len();
                    command_lists.push(CommandList::from(CommandListDesc {
                        label: format!("{label}.{index}"),
                        operations: std::mem::take(&mut current),
                    }));
                }
                current.extend(segment);
            }
        }

        if !current.is_empty() {
            let index = command_lists.len();
            command_lists.push(CommandList::from(CommandListDesc {
                label: format!("{label}.{index}"),
                operations: current,
            }));
        }
        if command_lists.is_empty() {
            return Err(GalError::invalid_argument(
                "source command-list partition requires at least one operation",
            ));
        }
        Ok(command_lists)
    }

    /// Split an oversized graphics pass without allowing a command list to
    /// end while the pass is live. Each continuation reopens the same
    /// attachments with LOAD semantics and replays the explicit graphics
    /// state accumulated before the next draw. This keeps ordering and
    /// resource ownership explicit while allowing large DH/source terrain
    /// populations to remain within the backend's bounded list budget.
    pub(crate) fn split_oversized_graphics_pass_segment(
        label: &str,
        segment: Vec<CommandOp>,
        max_operations_per_list: usize,
    ) -> GalResult<Vec<Vec<CommandOp>>> {
        if segment.len() <= max_operations_per_list {
            return Ok(vec![segment]);
        }
        let begin_index = segment
            .iter()
            .position(|operation| matches!(operation, CommandOp::BeginPass { .. }));
        let end_index = segment
            .iter()
            .rposition(|operation| matches!(operation, CommandOp::EndPass));
        let (Some(begin_index), Some(end_index)) = (begin_index, end_index) else {
            return Err(GalError::unsupported_feature(format!(
                "source command pass segment in '{}' contains {} operations, exceeding backend per-list limit {}; no splittable graphics pass",
                label,
                segment.len(),
                max_operations_per_list,
            )));
        };
        if begin_index >= end_index {
            return Err(GalError::invalid_argument(
                "oversized source graphics pass has invalid BeginPass/EndPass ordering",
            ));
        }
        let original_begin = match &segment[begin_index] {
            CommandOp::BeginPass {
                pass,
                target,
                colors,
                depth_stencil,
            } => CommandOp::BeginPass {
                pass: *pass,
                target: *target,
                colors: colors.clone(),
                depth_stencil: depth_stencil.clone(),
            },
            _ => unreachable!("begin index identifies BeginPass"),
        };
        let continuation_begin = match &original_begin {
            CommandOp::BeginPass {
                pass,
                target,
                colors,
                depth_stencil,
            } => CommandOp::BeginPass {
                pass: *pass,
                target: *target,
                colors: colors
                    .iter()
                    .map(|attachment| PassAttachment {
                        view: attachment.view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    })
                    .collect(),
                depth_stencil: depth_stencil.as_ref().map(|attachment| PassAttachment {
                    view: attachment.view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            },
            _ => unreachable!("original_begin is BeginPass"),
        };
        let prefix = segment[..begin_index].to_vec();
        let body = &segment[(begin_index + 1)..end_index];
        let mut replay_state = Vec::<CommandOp>::new();
        let mut current = prefix;
        current.push(original_begin.clone());
        let mut chunks = Vec::<Vec<CommandOp>>::new();
        let mut saw_draw = false;
        for operation in body {
            let is_draw = matches!(
                operation,
                CommandOp::Draw { .. }
                    | CommandOp::DrawIndexed { .. }
                    | CommandOp::DrawIndirect { .. }
                    | CommandOp::DrawIndexedIndirect { .. }
            );
            // Once a draw has completed, the pass can safely continue in a new
            // list before the next state change. Waiting for the next draw can
            // consume the final slot with a vertex/index binding and leave no room
            // for the required EndPass operation.
            if saw_draw && current.len().saturating_add(2) > max_operations_per_list {
                current.push(CommandOp::EndPass);
                chunks.push(std::mem::take(&mut current));
                current.push(continuation_begin.clone());
                current.extend(replay_state.iter().cloned());
            }
            if current.len().saturating_add(1) > max_operations_per_list {
                return Err(GalError::unsupported_feature(format!(
                    "source graphics pass in '{}' cannot be split within backend per-list limit {} without breaking explicit state (current_ops={}, replay_state={}, saw_draw={}, next_op={:?})",
                    label,
                    max_operations_per_list,
                    current.len(),
                    replay_state.len(),
                    saw_draw,
                    operation,
                )));
            }
            current.push(operation.clone());
            let prior_state = match operation {
                CommandOp::BindGraphicsPipeline(_) => replay_state
                    .iter()
                    .position(|state| matches!(state, CommandOp::BindGraphicsPipeline(_))),
                CommandOp::BindResourceSet { set_index, .. } => replay_state.iter().position(
                    |state| matches!(state, CommandOp::BindResourceSet { set_index: prior, .. } if prior == set_index),
                ),
                CommandOp::SetVertexBuffer { slot, .. } => replay_state.iter().position(
                    |state| matches!(state, CommandOp::SetVertexBuffer { slot: prior, .. } if prior == slot),
                ),
                CommandOp::SetIndexBuffer { .. } => replay_state
                    .iter()
                    .position(|state| matches!(state, CommandOp::SetIndexBuffer { .. })),
                _ => None,
            };
            if let Some(index) = prior_state {
                replay_state[index] = operation.clone();
            } else if matches!(
                operation,
                CommandOp::BindGraphicsPipeline(_)
                    | CommandOp::BindResourceSet { .. }
                    | CommandOp::SetVertexBuffer { .. }
                    | CommandOp::SetIndexBuffer { .. }
            ) {
                replay_state.push(operation.clone());
            }
            saw_draw |= is_draw;
        }
        if !saw_draw {
            return Err(GalError::unsupported_feature(format!(
                "oversized source graphics pass in '{}' contains no draw boundary for safe splitting",
                label,
            )));
        }
        if current.len().saturating_add(1) > max_operations_per_list {
            return Err(GalError::unsupported_feature(format!(
                "source graphics pass in '{}' cannot fit its final EndPass within backend per-list limit {}",
                label, max_operations_per_list,
            )));
        }
        current.push(CommandOp::EndPass);
        chunks.push(current);

        // Every non-final chunk stores because the next chunk loads the same
        // image. The final chunk restores the original store policy.
        if let CommandOp::BeginPass {
            colors: original_colors,
            depth_stencil: original_depth,
            ..
        } = &original_begin
        {
            let non_final_chunk_count = chunks.len().saturating_sub(1);
            for chunk in chunks.iter_mut().take(non_final_chunk_count) {
                if let Some(CommandOp::BeginPass {
                    colors,
                    depth_stencil,
                    ..
                }) = chunk.first_mut()
                {
                    for actual in colors {
                        actual.store_op = AttachmentStoreOp::Store;
                    }
                    if let Some(actual) = depth_stencil.as_mut() {
                        actual.store_op = AttachmentStoreOp::Store;
                    }
                }
                // The runtime keeps replaced lightmap views alive until all
                // pass-owned set-one consumers have been retired above.
            }
            if let Some(CommandOp::BeginPass {
                colors,
                depth_stencil,
                ..
            }) = chunks.last_mut().and_then(|chunk| chunk.first_mut())
            {
                for (actual, original) in colors.iter_mut().zip(original_colors) {
                    actual.store_op = original.store_op;
                }
                if let (Some(actual), Some(original)) =
                    (depth_stencil.as_mut(), original_depth.as_ref())
                {
                    actual.store_op = original.store_op;
                }
            }
        }
        Ok(chunks)
    }

    pub(crate) fn bounded_command_list_limit(gal: &VulkanicGal) -> usize {
        (gal.capabilities().limits.max_commands_per_list as usize)
            .min(Self::MAX_COMMAND_OPS_PER_LIST)
            .max(1)
    }

    /// Records the complete semantic source chain into one submission. Route
    /// selection remains outside this method; callers must already have
    /// admitted an exact frame-scoped source snapshot and target.
    pub(crate) fn submit_complete_named_source_frame<F>(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        frame: WorldPrimitiveFrame,
        mut append_gui: F,
    ) -> GalResult<(WorldPrimitiveSubmitStats, GuiSubmitStats)>
    where
        F: FnMut(
            &mut VulkanicGal,
            Handle,
            Handle,
            bool,
        ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)>,
    {
        self.world_text.begin_submission();
        // DH source targets are confirmed within the same call that submits
        // them, so one still pending here belongs to an aborted earlier frame.
        // Release it instead of rejecting every later frame.
        if self.pending_distant_horizons_source_targets.is_some() {
            self.discard_distant_horizons_source_targets(gal);
            self.discard_distant_horizons_generic_source_buffers(gal);
            self.lod_gpu_residency.discard_submission(gal);
            self.lod_textured_gpu_residency.discard_submission(gal);
        }
        let started = std::time::Instant::now();
        let mut profile = WholeFrameProfile::default();
        self.write_runtime_source_execution_attempt(
            &frame,
            "complete-plan-entered",
            started.elapsed(),
        );
        let validate_started = std::time::Instant::now();
        validate_frame(&frame)?;
        let entity_outline_plan = features::outline::prepare_entity_outline_post_effect(&frame)?;
        // The armed entry validates this exact frame's coverage just before
        // handing it here; other entries still validate it themselves.
        if self.coverage_validated_frame_id.take() != Some(frame.frame_id) {
            self.validate_selected_source_frame_coverage(&frame)?;
        }
        profile.world_validate_frame_nanos = elapsed_nanos_u64(validate_started);
        let program_lookup_started = std::time::Instant::now();
        let color_format = gal.pass_target_color_format(frame_target)?;
        let programs = self.lowered_source_programs_for_complete_plan(
            frame.voxel_volume.world_generation,
            frame.frame_id,
            frame_target,
        )?;
        profile.shader_plan_lookup_nanos = elapsed_nanos_u64(program_lookup_started);
        self.write_runtime_source_execution_attempt(
            &frame,
            "source-programs-ready",
            started.elapsed(),
        );
        let expected_extent = Extent3d {
            width: frame.viewport_width,
            height: frame.viewport_height,
            depth: 1,
        };
        let (
            g_buffer_generation,
            source_depth_texture,
            source_depth_view,
            source_shadow_targets,
            source_main_depth,
        ) = {
            let g_buffer = self.g_buffer_resources.as_ref().ok_or_else(|| {
                GalError::invalid_argument(
                    "complete source execution requires owned G-buffer resources for the exact frame",
                )
            })?;
            if g_buffer.extent != expected_extent {
                return Err(GalError::invalid_argument(
                    "complete source execution G-buffer extent does not match the requested frame",
                ));
            }
            (
                g_buffer.generation,
                g_buffer.depth_texture,
                g_buffer.depth_view,
                terrain_source_shadow_pass_targets(g_buffer),
                NamedSourceMainDepthInputs {
                    targets: TerrainDepthHistoryTargets {
                        main_depth_texture: g_buffer.depth_texture,
                        before_translucency_texture: g_buffer
                            .main_depth_before_translucency_texture,
                        previous_texture: g_buffer.main_depth_previous_texture,
                    },
                    // The deferred source stage consumes the live post-terrain
                    // depth attachment; temporal roles retain their explicit
                    // history identities and are committed after fullscreen.
                    main_depth_view: g_buffer.depth_view,
                    before_translucency_view: g_buffer.main_depth_before_translucency_view,
                    previous_view: g_buffer.main_depth_previous_view,
                    sampler: g_buffer.sampler,
                    graph_generation: g_buffer.generation,
                },
            )
        };
        let batching_started = std::time::Instant::now();
        // Same cached static plan plus frame-local camera-sorted batches as
        // the vanilla route; only camera-sorted index order is rebuilt.
        let mut mesh_identities = std::mem::take(&mut self.mesh_batch_identity_scratch);
        mesh_identities.clear();
        // Only source-terrain batches are consumed here (entities, glint and
        // hands have their own writers), so key and build terrain-only plans.
        mesh_identities.extend(frame.mesh_instances.iter().map(terrain_batch_instance_key));
        let has_camera_sorted_meshes = frame.mesh_instances.iter().any(|instance| {
            instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
                && MeshBatchSelection::All.includes(instance)
                && is_source_terrain_mesh_stratum(instance.stratum)
        });
        let static_batches = self.cached_mesh_batch_plan(
            &frame,
            color_format,
            RasterYDirection::Up,
            true,
            if has_camera_sorted_meshes {
                MeshBatchSelection::Static
            } else {
                MeshBatchSelection::All
            },
            &mesh_identities,
            true,
        );
        let shadow_indices = self.source_shadow_terrain_instance_indices(
            &frame, programs.opaque.shader_pack_generation,
        )?;
        let shadow_identities = shadow_indices.iter().map(|&index| mesh_identities[index].clone()).collect::<Vec<_>>();
        let shadow_batches = self.cached_mesh_batch_plan_selected(
            &frame, color_format, RasterYDirection::Up, true,
            MeshBatchSelection::ShadowOnly, &shadow_identities, true, Some(&shadow_indices),
        );
        self.mesh_batch_identity_scratch = mesh_identities;
        let (static_batches, shadow_batches) = (static_batches?, shadow_batches?);
        let batches: Arc<Vec<MeshBatch>> = if has_camera_sorted_meshes {
            let mut combined = Vec::with_capacity(static_batches.len() + 64);
            combined.extend(static_batches.iter().cloned());
            combined.extend(mesh_batches_filtered(
                &frame,
                self,
                color_format,
                RasterYDirection::Up,
                true,
                MeshBatchSelection::CameraSorted,
                true,
            )?);
            sort_mesh_batches(&mut combined, &frame);
            Arc::new(combined)
        } else {
            static_batches
        };
        profile.world_batching_nanos = elapsed_nanos_u64(batching_started);
        // A frame with no world geometry yet (joining, teleporting, respawning
        // while chunks load) is still a shader-pack frame: Iris renders the
        // sky, fog and composite chain over empty terrain. Falling back to the
        // vanilla route here flashed shaders off for seconds after every
        // teleport, so empty terrain is admitted like a far-only DH frame.
        self.write_runtime_source_execution_attempt(
            &frame,
            "mesh-batches-ready",
            started.elapsed(),
        );
        let resource_prepare_started = std::time::Instant::now();
        let plan = self.prepare_named_source_frame_plan(
            gal,
            &programs,
            frame.voxel_volume.world_generation,
            g_buffer_generation,
            &frame,
            &batches,
            &shadow_batches,
            expected_extent,
            source_depth_texture,
            source_depth_view,
            source_shadow_targets,
            source_main_depth,
            frame_target,
            ShaderPackColorClearValues {
                fog_color: background_clear_color(&frame.background),
            },
        )?;
        profile.world_resource_prepare_nanos = elapsed_nanos_u64(resource_prepare_started);
        self.write_runtime_source_execution_attempt(&frame, "named-plan-ready", started.elapsed());
        let source_draw_coverage = SourceTerrainDrawCoverage::from_batches_and_draws(
            &batches,
            &plan.terrain.terrain.draws,
        );
        require_source_terrain_writer_coverage(&batches, source_draw_coverage)?;
        let source_lod_draw_count = plan.distant_horizons.as_ref().map_or(0_u64, |dh| {
            (dh.draws.len() + dh.exact_atlas_draws.len() + dh.translucent_draws.len()) as u64
        });
        require_source_lod_writer_coverage(
            frame.lod_instances.len() as u64,
            source_lod_draw_count,
        )?;
        require_source_mesh_family_writer_coverage(
            "block-model",
            WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY,
            &batches,
            &plan.terrain.terrain.draws,
        )?;
        require_source_mesh_family_writer_coverage(
            "ordinary-block",
            WORLD_STRATUM_ORDINARY_BLOCK,
            &batches,
            &plan.terrain.terrain.draws,
        )?;
        require_source_mesh_family_writer_coverage(
            "moving-block",
            WORLD_STRATUM_MOVING_MESH,
            &batches,
            &plan.terrain.terrain.draws,
        )?;
        // Glint sections are drawn by the `gbuffers_armor_glint` writer and
        // crumbling instances by the damaged-block writer; both count.
        let (source_entity_instance_count, source_entity_draw_count, source_entity_index_count) =
            plan.terrain
                .entities
                .iter()
                .flat_map(|entities| entities.draws.iter())
                .chain(plan.terrain.entity_glint.iter().flat_map(|glint| glint.draws.iter()))
                .fold((0u64, 0u64, 0u64), |coverage, draw| {
                        (
                            coverage.0.saturating_add(u64::from(draw.instance_count)),
                            coverage.1.saturating_add(1),
                            coverage.2.saturating_add(
                                u64::from(draw.index_count) * u64::from(draw.instance_count),
                            ),
                        )
                    });
        let expected_source_entity_instance_count = frame
            .mesh_instances
            .iter()
            .filter(|instance| {
                instance.stratum == WORLD_STRATUM_ENTITY_MESH
                    && instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY == 0
                    && !self.is_crumbling_mesh_instance(instance)
            })
            .count() as u64;
        require_source_entity_writer_coverage(
            expected_source_entity_instance_count,
            source_entity_instance_count,
            source_entity_draw_count,
            source_entity_index_count,
        )?;
        let (source_hand_instance_count, source_hand_draw_count, source_hand_index_count) = plan
            .terrain
            .hands
            .iter()
            .flat_map(|hands| hands.draws.iter().chain(hands.translucent_draws.iter()))
            .chain(plan.terrain.hand_glint.iter().flat_map(|glint| glint.draws.iter()))
            .fold((0_u64, 0_u64, 0_u64), |coverage, draw| {
                    (
                        coverage.0.saturating_add(u64::from(draw.instance_count)),
                        coverage.1.saturating_add(1),
                        coverage.2.saturating_add(
                            u64::from(draw.index_count) * u64::from(draw.instance_count),
                        ),
                    )
                });
        require_source_hand_writer_coverage(
            frame.first_person_mesh_instances.len() as u64,
            source_hand_instance_count,
            source_hand_draw_count,
            source_hand_index_count,
        )?;
        // Source-material writers share transport but not source programs.
        // Keep their admission and execution counts distinct so a selected
        // source receipt cannot satisfy a cloud/weather producer with an
        // unrelated textured-material draw.
        let textured_batches = source_textured_material_batches(&frame)?;
        let particle_batches = source_material_batches_for_program(
            &frame,
            WORLD_MATERIAL_SOURCE_PARTICLES,
            &[
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_MATERIAL_MODE_CUTOUT,
                WORLD_MATERIAL_MODE_TRANSLUCENT,
            ],
        )?;
        let weather_batches = source_weather_material_batches(&frame)?;
        let cloud_batches = source_cloud_material_batches(&frame)?;
        let clouds_suppressed = self
            .shader_runtime
            .as_ref()
            .is_some_and(ShaderPackRuntimeExecutor::suppresses_vanilla_cloud_faces);
        let source_textured_material_coverage = source_material_writer_coverage(
            &textured_batches,
            plan.terrain
                .textured_material
                .as_ref()
                .map(|material| material.draws.as_slice()),
        )?;
        let source_weather_coverage = source_material_writer_coverage(
            &weather_batches,
            plan.terrain
                .weather
                .as_ref()
                .map(|weather| weather.draws.as_slice()),
        )?;
        let source_cloud_coverage = source_material_writer_coverage(
            if clouds_suppressed {
                &[]
            } else {
                &cloud_batches
            },
            plan.terrain
                .clouds
                .as_ref()
                .map(|clouds| clouds.draws.as_slice()),
        )?;
        require_source_material_writer_coverage("textured", source_textured_material_coverage)?;
        require_source_material_family_coverage(
            "particles",
            source_material_writer_coverage(&particle_batches, None)?,
            source_textured_material_coverage,
        )?;
        require_source_material_writer_coverage("weather", source_weather_coverage)?;
        require_source_material_writer_coverage("cloud", source_cloud_coverage)?;
        let pre_dropped_clouds = std::mem::take(&mut self.pre_dropped_suppressed_cloud_quads);
        let source_cloud_suppressed_quad_count = if clouds_suppressed {
            source_material_writer_coverage(&cloud_batches, None)?.quads + pre_dropped_clouds
        } else {
            0
        };
        let suppressed_clouds_present = !cloud_batches.is_empty() || pre_dropped_clouds > 0;
        let source_cloud_fullscreen_stage_count = if clouds_suppressed && suppressed_clouds_present
        {
            plan.fullscreen_consumers
                .iter()
                .filter(|consumer| {
                    // The selected Complementary profile moves its cloud
                    // generation to deferred1. This is source metadata, not
                    // an attachment or backend identity.
                    consumer
                        .program
                        .source_stage_path
                        .ends_with("deferred1.fsh")
                        && consumer.program.fragment.source.contains("GetClouds(")
                })
                .count() as u64
        } else {
            0
        };
        if clouds_suppressed
            && suppressed_clouds_present
            && source_cloud_fullscreen_stage_count == 0
        {
            plan.discard(self, gal);
            return Err(GalError::unsupported_feature(
                "selected source suppresses vanilla cloud faces but no Rust-owned fullscreen cloud stage was prepared",
            ));
        }
        let source_material_batch_count = source_textured_material_coverage
            .batches
            .saturating_add(source_weather_coverage.batches)
            .saturating_add(source_cloud_coverage.batches);
        let source_material_quad_count = source_textured_material_coverage
            .quads
            .saturating_add(source_weather_coverage.quads)
            .saturating_add(source_cloud_coverage.quads);
        let source_material_draw_count = source_textured_material_coverage
            .draws
            .saturating_add(source_weather_coverage.draws)
            .saturating_add(source_cloud_coverage.draws);
        let source_material_vertex_count = source_textured_material_coverage
            .vertices
            .saturating_add(source_weather_coverage.vertices)
            .saturating_add(source_cloud_coverage.vertices);
        let source_entity_model_quad_count = frame
            .material_quads
            .iter()
            .filter(|quad| quad.source_program == WORLD_MATERIAL_SOURCE_ENTITY_MODEL)
            .count() as u64;
        // The final selected-source stage owns the format of its named color
        // output. Prepare normal Rust overlay resources against that explicit
        // target format, not the acquired swapchain format and not an RGBA8
        // guess. The resources are format-keyed caches, so this cannot retire
        // the ordinary whole-frame variants recorded while staging this frame.
        let source_overlay_color_format = self
            .source_final_output_cache
            .plan(plan.final_output.as_ref().ok_or_else(|| {
                GalError::backend("named source frame omitted its final-output reservation")
            })?)?
            .overlay_color_format();
        if entity_outline_plan.is_some() {
            self.ensure_entity_outline_target_resources_with_depth(
                gal,
                frame.viewport_width,
                frame.viewport_height,
                source_overlay_color_format,
                Some(source_depth_view),
            )?;
            self.prepare_entity_outline_mask_gpu_resources(
                gal,
                &frame,
                source_overlay_color_format,
                RasterYDirection::Up,
            )?;
            self.ensure_entity_outline_post_effect_resource_sets(gal, source_overlay_color_format)?;
        }
        let source_entity_outline_resources = if entity_outline_plan.is_some() {
            Some((
                self.entity_outline_targets
                    .as_ref()
                    .ok_or_else(|| GalError::backend("source outline targets vanished"))?
                    .clone(),
                self.entity_outline_mask_gpu
                    .as_ref()
                    .ok_or_else(|| GalError::backend("source outline mask resources vanished"))?
                    .clone(),
                self.entity_outline_post_effect_pipelines
                    .as_ref()
                    .ok_or_else(|| GalError::backend("source outline pipelines vanished"))?
                    .clone(),
                self.entity_outline_post_effect_sets
                    .as_ref()
                    .ok_or_else(|| GalError::backend("source outline sets vanished"))?
                    .clone(),
            ))
        } else {
            None
        };
        // A selected-source capture cannot be bound to the preceding normal
        // preparation frame. Promote an explicitly pending diagnostic request
        // only here, where this exact source plan is about to execute.
        let mut gameplay_attachment_capture = GameplayAttachmentCapture::select_source(
            &frame,
            generation,
            self.mesh_asset_generation,
            gal.capabilities().shader_conventions,
        )?;
        let source_dh_depth_coverage = plan.distant_horizons.as_ref().map(|distant_horizons| {
            (
                source_main_depth.targets.before_translucency_texture,
                distant_horizons.depth_targets.distant_depth_before_translucency_texture,
            )
        });
        if gameplay_attachment_capture.is_some() {
            for observation in &self.latest_atlas_animation_observations {
                eprintln!("{observation}");
            }
        }
        let terrain_primary = plan
            .terrain
            .color_targets()
            .target("primary")
            .ok_or_else(|| {
                GalError::backend("named source frame omitted the semantic primary color target")
            })?;
        let terrain_primary_capture = SelectedSourceOutputCapture::select_terrain_primary(
            gal,
            &frame,
            terrain_primary.current_texture,
            terrain_primary.format,
            plan.terrain.color_targets().identity.extent,
        )?;
        let distant_horizons_primary_capture =
            SelectedSourceOutputCapture::select_distant_horizons_primary(
                gal,
                &frame,
                terrain_primary.current_texture,
                terrain_primary.format,
                plan.terrain.color_targets().identity.extent,
            )?;
        let distant_horizons_depth_capture = match plan.distant_horizons.as_ref() {
            Some(distant_horizons) => SelectedSourceOutputCapture::select_distant_horizons_depth(
                gal,
                &frame,
                distant_horizons.target.distant_depth_texture,
                plan.terrain.color_targets().identity.extent,
            )?,
            None => None,
        };
        let shader_pack_color_capture = selected_source_shader_pack_color_capture_name()
            .map(|name| {
                let target = plan.terrain.color_targets().target(&name).ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "selected-source diagnostic requested unknown shader-pack color target '{name}'"
                    ))
                })?;
                SelectedSourceOutputCapture::select_shader_pack_color(
                    gal,
                    &frame,
                    &name,
                    target.current_texture,
                    target.format,
                    plan.terrain.color_targets().identity.extent,
                )
            })
            .transpose()?
            .flatten();
        let fullscreen_stage_trace_captures = if selected_source_fullscreen_stage_trace_enabled() {
            SelectedSourceFullscreenTraceCapture::select_all(
                gal,
                &frame,
                &plan.fullscreen_consumers,
                plan.terrain.color_targets().identity.extent,
            )?
        } else {
            Vec::new()
        };
        let (pre_terrain_sky_capture, fullscreen_stage_capture) = (!selected_source_fullscreen_stage_trace_enabled())
            .then(selected_source_fullscreen_stage_capture)
            .flatten()
            .map(|request| {
                let pre_terrain_sky = plan
                    .terrain
                    .pre_terrain_sky
                    .iter()
                    .find(|consumer| {
                        consumer.writes_named_color(&request.program_identity, &request.color_name)
                    });
                let consumer = pre_terrain_sky.or_else(|| {
                    plan.fullscreen_consumers.iter().find(|consumer| {
                        consumer.writes_named_color(&request.program_identity, &request.color_name)
                    })
                });
                let consumer = consumer.ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "selected-source diagnostic requested color '{}' after stage '{}', but that stage does not write the named color",
                        request.color_name, request.program_identity
                    ))
                })?;
                let output = if request.color_name == "*" {
                    consumer
                        .plan
                        .outputs()
                        .first()
                        .expect("fullscreen source programs always declare one named output")
                } else {
                    consumer
                        .plan
                        .outputs()
                        .iter()
                        .find(|output| output.role.shader_pack_color_name() == Some(request.color_name.as_str()))
                        .expect("matching fullscreen source output remains present")
                };
                let capture = SelectedSourceOutputCapture::select_fullscreen_stage(
                    gal,
                    &frame,
                    &request.program_identity,
                    output
                        .role
                        .shader_pack_color_name()
                        .expect("fullscreen output is a named shader-pack color"),
                    output.texture,
                    output.format,
                    plan.terrain.color_targets().identity.extent,
                )?;
                Ok(if pre_terrain_sky.is_some() {
                    (capture, None)
                } else {
                    (None, capture)
                })
            })
            .transpose()?
            .unwrap_or((None, None));
        let selected_source_capture = match self.source_final_output_cache.plan(
            plan.final_output
                .as_ref()
                .expect("source final-output reservation remains present"),
        ) {
            Ok(output) => SelectedSourceOutputCapture::select(gal, &frame, output),
            Err(error) => Err(error),
        };
        let selected_source_capture = match selected_source_capture {
            Ok(capture) => capture,
            Err(error) => {
                plan.discard(self, gal);
                return Err(error);
            }
        };
        // Block-selection segments consumed by the pack's `gbuffers_line`
        // writer must not be redrawn by the post-final vanilla overlay.
        let overlay_segments = !frame.segments.is_empty() && plan.terrain.lines.is_none();
        let (outline_resources, crack_resources, border_resources) = match (|| -> GalResult<_> {
            if overlay_segments {
                self.ensure_resources(gal, source_overlay_color_format, RasterYDirection::Up)?;
            }
            if !frame.crack_quads.is_empty() {
                self.ensure_crack_resources(
                    gal,
                    source_overlay_color_format,
                    RasterYDirection::Up,
                )?;
            }
            if !frame.border_quads.is_empty() {
                self.ensure_border_resources(
                    gal,
                    source_overlay_color_format,
                    RasterYDirection::Up,
                )?;
            }
            let outline_resources = self
                .resources
                .get(&(source_overlay_color_format, RasterYDirection::Up))
                .filter(|_| overlay_segments)
                .map(|resources| {
                    (
                        resources.uniform_buffer,
                        resources.pipeline_layout,
                        resources.resource_set,
                        resources.pipeline_depth_disabled,
                        resources.pipeline_depth_test_no_write,
                        resources.pipeline_depth_test_write,
                    )
                });
            let crack_resources = self
                .crack_resources
                .get(&(source_overlay_color_format, RasterYDirection::Up))
                .map(|resources| {
                    (
                        resources.uniform_buffer,
                        resources.pipeline_layout,
                        resources.resource_set,
                        resources.pipeline_depth_disabled,
                        resources.pipeline_depth_test_write,
                    )
                });
            let border_resources = self
                .border_resources
                .get(&(source_overlay_color_format, RasterYDirection::Up))
                .map(|resources| {
                    (
                        resources.uniform_buffer,
                        resources.pipeline_layout,
                        resources.resource_set,
                        resources.pipeline_depth_disabled,
                        resources.pipeline_depth_test_write,
                    )
                });
            if overlay_segments && outline_resources.is_none() {
                return Err(GalError::backend(
                    "world outline resources vanished before source-frame submit",
                ));
            }
            if !frame.crack_quads.is_empty() && crack_resources.is_none() {
                return Err(GalError::backend(
                    "world crack resources vanished before source-frame submit",
                ));
            }
            if !frame.border_quads.is_empty() && border_resources.is_none() {
                return Err(GalError::backend(
                    "world border resources vanished before source-frame submit",
                ));
            }
            Ok((outline_resources, crack_resources, border_resources))
        })() {
            Ok(resources) => resources,
            Err(error) => {
                plan.discard(self, gal);
                return Err(error);
            }
        };
        self.write_runtime_source_execution_attempt(
            &frame,
            "overlay-resources-ready",
            started.elapsed(),
        );
        let command_generation_started = std::time::Instant::now();
        let mut source_world_text_stats = features::world_text::WorldTextFrameStats::default();
        let mut source_gui_stats = GuiSubmitStats::default();
        let mut operations = Vec::new();
        // The armed route owns the per-frame voxel update too (Iris packs
        // update colored light every frame). A voxel submission left pending
        // by an aborted frame was never submitted; drop it before recording.
        if let Some(runtime) = self.shader_runtime.as_mut() {
            if runtime.has_pending_private_terrain_occupancy_submission() {
                runtime.discard_private_terrain_occupancy_submission();
            }
        }
        let occupancy_started = std::time::Instant::now();
        match self.append_private_terrain_occupancy_for_frame(&frame, &mut operations) {
            Ok(true) => whole_frame_phase_trace(
                "private-occupancy",
                frame.frame_id,
                Some(occupancy_started),
            ),
            Ok(false) => {}
            Err(error) => {
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                }
                return Err(error);
            }
        }
        // Voxel volumes rest in storage layout for the Rust-owned compute;
        // source passes sample them, so bracket the passes in sampled layout.
        let storage_volumes = self
            .shader_runtime
            .as_ref()
            .map(|runtime| runtime.private_terrain_storage_volume_textures())
            .unwrap_or_default();
        for texture in &storage_volumes {
            operations.push(CommandOp::Barrier(texture_barrier(
                *texture,
                TextureUsageState::ShaderStorageRead,
                TextureUsageState::ShaderRead,
            )));
        }
        let source_submission = plan.into_submission_parts(
            gal,
            self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::backend(
                    "source runtime vanished while recording the complete source frame",
                )
            })?,
            &mut self.source_final_output_cache,
            background_clear_color(&frame.background),
            pre_terrain_sky_capture.as_ref(),
            terrain_primary_capture.as_ref(),
            shader_pack_color_capture.as_ref(),
            distant_horizons_primary_capture.as_ref(),
            distant_horizons_depth_capture.as_ref(),
            fullscreen_stage_capture.as_ref(),
            &fullscreen_stage_trace_captures,
            &mut operations,
            |gal, final_output, operations| {
                if let (Some(outline_plan), Some((targets, mask_gpu, pipelines, sets))) = (
                    entity_outline_plan.as_ref(),
                    source_entity_outline_resources.as_ref(),
                ) {
                    let entity_outline_start = operations.len();
                    let prior_target_state = if self.entity_outline_targets_initialized {
                        TextureUsageState::ShaderRead
                    } else {
                        TextureUsageState::Undefined
                    };
                    operations.extend(features::outline::lower_entity_outline_mask_pass(
                        targets,
                        mask_gpu,
                        source_depth_view,
                        TextureUsageState::ShaderRead,
                        TextureUsageState::ShaderRead,
                        prior_target_state,
                    )?);
                    operations.extend(features::outline::lower_entity_outline_post_effect_with_resources(
                        outline_plan,
                        targets,
                        pipelines,
                        sets,
                        final_output.overlay_pass(),
                        final_output.overlay_target(),
                        final_output.overlay_color_attachment(),
                        pipelines.composite_pipeline,
                        prior_target_state,
                        prior_target_state,
                        prior_target_state,
                    )?);
                    require_source_overlay_writer_coverage(
                        "entity-outline",
                        false,
                        &operations[entity_outline_start..],
                    )?;
                }
                let overlay_start = operations.len();
                append_source_outline_overlay_ops(
                    &frame,
                    outline_resources,
                    final_output,
                    operations,
                )?;
                require_source_overlay_writer_coverage(
                    "outline",
                    overlay_segments,
                    &operations[overlay_start..],
                )?;
                let crack_start = operations.len();
                append_source_crack_overlay_ops(&frame, crack_resources, final_output, operations)?;
                require_source_overlay_writer_coverage(
                    "crack",
                    !frame.crack_quads.is_empty(),
                    &operations[crack_start..],
                )?;
                let border_start = operations.len();
                append_source_border_overlay_ops(
                    &frame,
                    border_resources,
                    final_output,
                    operations,
                )?;
                require_source_overlay_writer_coverage(
                    "border",
                    !frame.border_quads.is_empty(),
                    &operations[border_start..],
                )?;
                if !frame.text_quads.is_empty() {
                    // Source execution presents from its own Rust-owned
                    // overlay target. Compose the same semantic text frontend
                    // into that target before its one final present copy;
                    // the selected route must not silently drop text just
                    // because it bypasses the ordinary graph executor.
                    source_world_text_stats = self.world_text.append_frame_ops(
                        gal,
                        final_output.overlay_target(),
                        final_output.overlay_pass(),
                        final_output.overlay_color_attachment(),
                        source_depth_texture,
                        final_output.overlay_depth_attachment(),
                        // The source graph leaves its shared g-buffer depth
                        // sampled before the overlay pass; the text frontend
                        // must perform the explicit attachment transition.
                        TextureUsageState::ShaderRead,
                        source_overlay_color_format,
                        RasterYDirection::Up,
                        frame.view_matrix,
                        frame.projection_matrix,
                        &frame.text_quads,
                        operations,
                        false,
                    )?;
                    require_source_text_writer_coverage(
                        frame.text_quads.len() as u64,
                        source_world_text_stats.draw_count,
                    )?;
                    // The source overlay's text pass depth-tests against the
                    // world g-buffer.  Its later shader work samples that
                    // same depth image, so close the explicit attachment
                    // usage before continuing the source command stream.
                    operations.push(CommandOp::Barrier(texture_barrier(
                        source_depth_texture,
                        TextureUsageState::DepthStencilAttachment,
                        TextureUsageState::ShaderRead,
                    )));
                }
                // Every source overlay writer (outline, crack, border, and
                // text) targets the private final-output depth domain. Close
                // that attachment phase even when no text was emitted so the
                // following fullscreen consumers see the snapshot in its
                // declared sampled layout.
                operations.push(CommandOp::Barrier(texture_barrier(
                    final_output.overlay_depth_attachment(),
                    TextureUsageState::DepthStencilAttachment,
                    TextureUsageState::ShaderRead,
                )));
                Ok(())
            },
            |gal, final_output, operations| {
                // The source present copy converts the shader-pack world's
                // sampled-image row convention to the acquired image. GUI
                // vertices are already top-left semantic coordinates, so it
                // must be composed afterwards rather than inherit that flip.
                let (mut gui_ops, gui_stats) = append_gui(
                    gal,
                    final_output.presentation_target(),
                    final_output.presentation_target(),
                    false,
                )?;
                // A frame with no GUI elements (spectator mode, F1) still
                // opens its load/store pass; that pass is a no-op.
                strip_empty_load_store_passes(&mut gui_ops);
                Self::validate_source_gui_ops(
                    &gui_ops,
                    final_output.presentation_target(),
                    &gui_stats.owned_intermediate_targets,
                )?;
                source_gui_stats = gui_stats;
                operations.extend(gui_ops);
                if let Some(capture) = gameplay_attachment_capture.as_mut() {
                    // Read back the actual acquired Rust target after the
                    // complete GUI stream, including owned item meshes. A
                    // second GUI replay could omit atlas-backed meshes and
                    // produce a screenshot unlike the presented frame.
                    capture.append_normal_frame_output(
                        gal,
                        operations,
                        frame_target,
                        "final_output",
                    )?;
                    capture.append_ops(gal, operations, self.g_buffer_resources.as_ref(), None)?;
                    if let Some((main_depth, dh_depth)) = source_dh_depth_coverage {
                        capture.append_source_dh_depth_coverage(gal, operations, main_depth, dh_depth)?;
                    }
                }
                Ok(())
            },
        );
        // A failed recording step (overlay/GUI validation, consumer staging)
        // consumed the plan. Release every frame-local owner it staged, or
        // the next frame is rejected as "already awaiting" forever.
        let mut source_submission = match source_submission {
            Ok(submission) => submission,
            Err(error) => {
                self.discard_unrecorded_source_frame(gal, frame.frame_id);
                if let Some(capture) = gameplay_attachment_capture.take() {
                    capture.discard(gal);
                }
                return Err(error);
            }
        };
        self.write_runtime_source_execution_attempt(
            &frame,
            "submission-ops-ready",
            started.elapsed(),
        );
        if let Some(capture) = selected_source_capture.as_ref() {
            if let Err(error) = capture.append_ops(
                TextureUsageState::ShaderRead,
                // The capture reads the persistent source overlay after its
                // final present copy. It must leave that overlay sampled, as
                // the next acquired-image reuse begins from ShaderRead.
                TextureUsageState::ShaderRead,
                &mut operations,
            ) {
                source_submission.discard(self, gal);
                if let Some(capture) = gameplay_attachment_capture.take() {
                    capture.discard(gal);
                }
                return Err(error);
            }
        }
        // A local-texture upload is part of the same source transaction as
        // its draw. If a writer moved the transaction into a private plan
        // before another writer requested the same staged texture, make the
        // upload's explicit copy/barriers visible in this final operation
        // stream before *every* consumer of the texture. A source fullscreen
        // writer can request a local texture while its pre-terrain pass is
        // being assembled; the terrain transaction then naturally owns the
        // upload, but appends it after that pass. Move the exact upload
        // operation group to the front instead of merely checking whether a
        // copy exists somewhere in the stream. This is a semantic transaction
        // repair, not a backend/layout fallback.
        let staged_texture_uploads = self
            .source_material_texture_resources
            .keys()
            .filter(|texture_id| {
                !self
                    .source_material_texture_upload_confirmed
                    .contains(texture_id)
            })
            .filter_map(|texture_id| {
                let resources = self.source_material_texture_resources.get(texture_id)?;
                let upload = if let Some(upload) = self
                    .source_material_texture_upload_operations
                    .get(texture_id)
                {
                    upload.clone()
                } else {
                    // An older rejected candidate may have retained the
                    // owned image while dropping only its pending-op
                    // bookkeeping. The semantic resource is still
                    // explicitly unconfirmed, so rebuild its bounded upload
                    // transaction from the Rust-owned asset rather than
                    // admitting a sampled image with no upload.
                    let (bytes, width, height) =
                        self.source_local_material_texture_bytes(*texture_id).ok()?;
                    Self::mesh_texture_upload_ops_from_state(
                        resources,
                        bytes,
                        width,
                        height,
                        TextureUsageState::Undefined,
                    )
                    .ok()?
                };
                Some((
                    *texture_id,
                    resources.upload_buffer,
                    resources.texture,
                    upload,
                ))
            })
            .collect::<Vec<_>>();
        if !staged_texture_uploads.is_empty() {
            let upload_resources = staged_texture_uploads
                .iter()
                .map(|(_, upload_buffer, texture, _)| (*upload_buffer, *texture))
                .collect::<BTreeSet<_>>();
            operations.retain(|operation| !match operation {
                CommandOp::HostWriteBuffer { buffer, .. } => upload_resources
                    .iter()
                    .any(|(upload_buffer, _)| buffer == upload_buffer),
                CommandOp::Barrier(barrier) => {
                    upload_resources.iter().any(|(upload_buffer, texture)| {
                        barrier.resource == *upload_buffer || barrier.resource == *texture
                    })
                }
                CommandOp::CopyBufferToTexture(region) => {
                    upload_resources.iter().any(|(upload_buffer, texture)| {
                        region.buffer == *upload_buffer && region.texture == *texture
                    })
                }
                _ => false,
            });
            let mut repaired_operations = staged_texture_uploads
                .into_iter()
                .flat_map(|(_, _, _, upload)| upload)
                .collect::<Vec<_>>();
            repaired_operations.append(&mut operations);
            operations = repaired_operations;
        }
        let uploaded = source_submission
            .terrain
            .as_ref()
            .map(|terrain| terrain.source_material_texture_ids.iter())
            .into_iter()
            .flatten()
            .chain(self.source_material_texture_resources.keys())
            .filter_map(|texture_id| {
                let resources = self.source_material_texture_resources.get(texture_id)?;
                operations
                    .iter()
                    .any(|operation| {
                        matches!(
                            operation,
                            CommandOp::CopyBufferToTexture(region)
                                if region.texture == resources.texture
                        )
                    })
                    .then_some(*texture_id)
            })
            .collect::<BTreeSet<_>>();
        // Keep the complete named-submission token authoritative even for a
        // fullscreen-only frame that has no near-terrain stream token.
        source_submission
            .uploaded_source_material_texture_ids
            .extend(uploaded.iter().copied());
        if let Some(terrain) = source_submission.terrain.as_mut() {
            terrain
                .source_material_texture_ids
                .extend(uploaded.iter().copied());
            terrain
                .uploaded_source_material_texture_ids
                .extend(uploaded);
        }
        let source_draw_ops = operations
            .iter()
            .filter(|operation| matches!(operation, CommandOp::Draw { .. }))
            .count() as u64;
        if operations
            .iter()
            .any(|operation| matches!(operation, CommandOp::Present { .. }))
        {
            source_submission.discard(self, gal);
            if let Some(capture) = gameplay_attachment_capture.take() {
                capture.discard(gal);
            }
            return Err(GalError::invalid_argument(
                "complete source execution rejects embedded presentation; the frame coordinator owns the sole present",
            ));
        }
        let source_draw_indexed_ops = operations
            .iter()
            .filter(|operation| {
                matches!(
                    operation,
                    CommandOp::DrawIndexed { .. } | CommandOp::DrawIndexedIndirect { .. }
                )
            })
            .count() as u64;
        if source_draw_ops.saturating_add(source_draw_indexed_ops) == 0 {
            source_submission.discard(self, gal);
            if let Some(capture) = gameplay_attachment_capture.take() {
                capture.discard(gal);
            }
            return Err(GalError::invalid_argument(
                "complete source execution recorded no terrain or fullscreen draws",
            ));
        }
        let command_ops = operations.len() as u64;
        let pass_count = operations
            .iter()
            .filter(|operation| matches!(operation, CommandOp::BeginPass { .. }))
            .count() as u64;
        let draw_ops = operations
            .iter()
            .filter(|operation| matches!(operation, CommandOp::Draw { .. }))
            .count() as u64;
        let draw_indexed_ops = operations
            .iter()
            .filter(|operation| {
                matches!(
                    operation,
                    CommandOp::DrawIndexed { .. } | CommandOp::DrawIndexedIndirect { .. }
                )
            })
            .count() as u64;
        // A texture upload can be assembled by both the ordinary world
        // resource path and the selected-source preparation path in the same
        // frame. Keep the first complete upload transaction and discard only
        // a later mip-generation command that has no preceding transfer
        // transition; such a command would write a texture already sampled
        // by an earlier source pass and is an invalid overlapping access.
        let mut texture_transfer_pending = BTreeSet::new();
        operations.retain(|operation| match operation {
            CommandOp::Barrier(barrier)
                if barrier.resource.kind() == Some(crate::render::vulkanic::handles::HandleKind::Texture) =>
            {
                if barrier.after == TextureUsageState::TransferDst {
                    texture_transfer_pending.insert(barrier.resource);
                }
                true
            }
            CommandOp::CopyBufferToTexture(region) => {
                texture_transfer_pending.insert(region.texture);
                true
            }
            CommandOp::GenerateMipmaps { texture, .. } => texture_transfer_pending.remove(texture),
            _ => true,
        });
        profile.gal.gal_command_generation_nanos = elapsed_nanos_u64(command_generation_started);
        // One source-derived shader-pack frame can legitimately contain far
        // more operations than a backend's *single command-list* budget.
        // Keep pass recording atomic, but use the backend's ordered multi-list
        // submission support between completed passes. Vulkan records each
        // list as its own primary command buffer, then submits/presents the
        // ordered set once; no extra presenter or synchronization timeline is
        // introduced.
        for texture in &storage_volumes {
            operations.push(CommandOp::Barrier(texture_barrier(
                *texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::ShaderStorageRead,
            )));
        }
        let command_lists = match Self::partition_command_lists_at_pass_boundaries(
            "minecraft.source-terrain-dh.whole-frame.commands",
            operations,
            Self::bounded_command_list_limit(gal),
        ) {
            Ok(command_lists) => command_lists,
            Err(error) => {
                self.world_text.cancel_submission();
                source_submission.discard(self, gal);
                if let Some(capture) = gameplay_attachment_capture.take() {
                    capture.discard(gal);
                }
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                }
                return Err(error);
            }
        };
        let command_list_count = command_lists.len() as u64;
        let token = match gal.submit_profiled(
            SubmissionBatch {
                label: "minecraft.source-terrain-dh.whole-frame".to_string(),
                command_lists,
            },
            &mut profile.gal,
        ) {
            Ok(token) => token,
            Err(error) => {
                self.world_text.cancel_submission();
                source_submission.discard(self, gal);
                if let Some(capture) = gameplay_attachment_capture.take() {
                    capture.discard(gal);
                }
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_private_terrain_occupancy_submission();
                }
                return Err(error);
            }
        };
        self.flush_deferred_mesh_resource_destroys(gal);
        self.write_runtime_source_execution_attempt(&frame, "gal-submitted", started.elapsed());
        if let Some(runtime) = self.shader_runtime.as_mut() {
            if runtime.has_pending_private_terrain_occupancy_submission() {
                runtime.confirm_private_terrain_occupancy_submission()?;
            }
        }
        if let Err(error) = source_submission.confirm(self, gal, token.submission) {
            if self
                .shader_runtime
                .as_ref()
                .is_some_and(|runtime| runtime.has_pending_vanilla_lightmap_submission())
            {
                self.release_lightmap_dependent_source_pack_resources(gal);
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_vanilla_lightmap_submission(gal);
                }
            }
            return Err(error);
        }
        self.world_text.confirm_submission();
        if entity_outline_plan.is_some() {
            self.entity_outline_targets_initialized = true;
            self.pending_entity_outline_targets_written = false;
        }
        let mut stats = WorldPrimitiveSubmitStats {
            submission_id: token.submission.0,
            command_lists: command_list_count,
            command_ops,
            mesh_instance_count: (frame.mesh_instances.len()
                + frame.first_person_mesh_instances.len()) as u64,
            mesh_batch_count: batches.len() as u64,
            mesh_draw_count: source_draw_indexed_ops,
            // Preserve the ordinary frontend metric family when the selected
            // source route owns generic material work. Java correlation and
            // capture gates consume semantic counts only; reporting zero here
            // would make a real source-material draw look absent.
            material_quad_count: source_material_quad_count,
            material_batch_count: source_material_batch_count,
            material_draw_count: source_material_draw_count,
            source_opaque_batch_count: source_draw_coverage.opaque_batches,
            source_cutout_batch_count: source_draw_coverage.cutout_batches,
            source_opaque_instance_count: source_draw_coverage.opaque_instances,
            source_cutout_instance_count: source_draw_coverage.cutout_instances,
            source_opaque_index_count: source_draw_coverage.opaque_indices,
            source_cutout_index_count: source_draw_coverage.cutout_indices,
            source_opaque_draw_count: source_draw_coverage.opaque_draws,
            source_cutout_draw_count: source_draw_coverage.cutout_draws,
            source_opaque_draw_index_count: source_draw_coverage.opaque_draw_indices,
            source_cutout_draw_index_count: source_draw_coverage.cutout_draw_indices,
            source_translucent_batch_count: source_draw_coverage.translucent_batches,
            source_translucent_instance_count: source_draw_coverage.translucent_instances,
            source_translucent_index_count: source_draw_coverage.translucent_indices,
            source_translucent_draw_count: source_draw_coverage.translucent_draws,
            source_translucent_draw_index_count: source_draw_coverage.translucent_draw_indices,
            source_entity_instance_count,
            source_entity_draw_count,
            source_entity_index_count,
            source_material_batch_count,
            source_material_quad_count,
            source_material_draw_count,
            source_material_vertex_count,
            source_textured_material_batch_count: source_textured_material_coverage.batches,
            source_textured_material_quad_count: source_textured_material_coverage.quads,
            source_textured_material_draw_count: source_textured_material_coverage.draws,
            source_textured_material_vertex_count: source_textured_material_coverage.vertices,
            source_entity_model_quad_count,
            source_weather_batch_count: source_weather_coverage.batches,
            source_weather_quad_count: source_weather_coverage.quads,
            source_weather_draw_count: source_weather_coverage.draws,
            source_weather_vertex_count: source_weather_coverage.vertices,
            source_cloud_batch_count: source_cloud_coverage.batches,
            source_cloud_quad_count: source_cloud_coverage.quads,
            source_cloud_draw_count: source_cloud_coverage.draws,
            source_cloud_vertex_count: source_cloud_coverage.vertices,
            source_cloud_suppressed_quad_count,
            source_cloud_faces_suppressed: clouds_suppressed,
            source_cloud_fullscreen_stage_count,
            world_text_quad_count: source_world_text_stats.quad_count,
            world_text_batch_count: source_world_text_stats.batch_count,
            world_text_draw_count: source_world_text_stats.draw_count,
            world_text_clip_xy_visible_quad_count: source_world_text_stats
                .clip_xy_visible_quad_count,
            world_text_first_ndc_bounds: source_world_text_stats.first_ndc_bounds,
            world_text_first_ndc_corners: source_world_text_stats.first_ndc_corners,
            world_text_ndc_bounds_sample: source_world_text_stats.ndc_bounds_sample,
            profile: WholeFrameProfile {
                gal: crate::render::vulkanic::metrics::SubmitProfile {
                    pass_count,
                    draw_ops,
                    draw_indexed_ops,
                    ..profile.gal
                },
                ..profile
            },
            ..WorldPrimitiveSubmitStats::default()
        };
        if terrain_primary_capture.is_some()
            || distant_horizons_primary_capture.is_some()
            || distant_horizons_depth_capture.is_some()
            || shader_pack_color_capture.is_some()
            || pre_terrain_sky_capture.is_some()
            || fullscreen_stage_capture.is_some()
            || !fullscreen_stage_trace_captures.is_empty()
            || selected_source_capture.is_some()
            || gameplay_attachment_capture.is_some()
        {
            gal.retire_through(token.submission)?;
            let reads = gal.completed_host_reads().to_vec();
            if let Some(capture) = terrain_primary_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = distant_horizons_primary_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = distant_horizons_depth_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = shader_pack_color_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = pre_terrain_sky_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = fullscreen_stage_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            for capture in fullscreen_stage_trace_captures {
                capture
                    .capture
                    .write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = selected_source_capture {
                capture.write_artifacts(&reads, token.submission.0)?;
            }
            if let Some(capture) = gameplay_attachment_capture {
                capture.write_artifacts(gal, reads, token.submission.0, &stats)?;
            }
        }
        self.write_runtime_source_execution_attempt(
            &frame,
            "complete-plan-finished",
            started.elapsed(),
        );
        stats.profile.world_frontend_total_nanos = elapsed_nanos_u64(started);
        Ok((stats, source_gui_stats))
    }

    /// GUI is composed into the acquired Rust target after the source final
    /// copy. It may contribute explicit draw commands, but it cannot
    /// introduce another target or presenter into that frame.
    pub(crate) fn validate_source_gui_ops(
        gui_ops: &[CommandOp],
        presentation_target: Handle,
        owned_intermediate_targets: &[Handle],
    ) -> GalResult<()> {
        Self::reject_source_gui_presenters(gui_ops)?;
        let has_render_pass = gui_ops
            .iter()
            .any(|operation| matches!(operation, CommandOp::BeginPass { .. }));
        let has_draw = gui_ops.iter().any(|operation| {
            matches!(
                operation,
                CommandOp::Draw { .. }
                    | CommandOp::DrawIndexed { .. }
                    | CommandOp::DrawIndirect { .. }
                    | CommandOp::DrawIndexedIndirect { .. }
            )
        });
        if has_render_pass && !has_draw {
            return Err(GalError::backend(
                "complete source execution GUI command list opened a render pass without a Rust draw",
            ));
        }
        for operation in gui_ops {
            match operation {
                CommandOp::BeginPass { target, .. }
                    if *target != presentation_target
                        && !owned_intermediate_targets.contains(target) =>
                {
                    return Err(GalError::invalid_argument(
                        "complete source execution rejects GUI work targeting an undeclared target",
                    ));
                }
                _ => {}
            }
        }
        Ok(())
    }

    pub(crate) fn reject_source_gui_presenters(gui_ops: &[CommandOp]) -> GalResult<()> {
        if gui_ops
            .iter()
            .any(|operation| matches!(operation, CommandOp::Present { .. }))
        {
            return Err(GalError::invalid_argument(
                "complete source execution rejects GUI presentation; the frame coordinator owns the sole present",
            ));
        }
        Ok(())
    }
}

/// Records the already-semantic block outline into the private source-frame
/// overlay target. This is deliberately the same Rust pipeline and uniform
/// encoding used by the ordinary whole-frame route; only the explicit target
/// differs. No shader-pack or Java renderer state is borrowed.
pub(crate) fn append_source_outline_overlay_ops(
    frame: &WorldPrimitiveFrame,
    resources: Option<(Handle, Handle, Handle, Handle, Handle, Handle)>,
    final_output: &SourceFinalOutputPlan,
    operations: &mut Vec<CommandOp>,
) -> GalResult<()> {
    let Some((
        uniform_buffer,
        pipeline_layout,
        resource_set,
        depth_disabled,
        depth_no_write,
        depth_write,
    )) = resources
    else {
        return Ok(());
    };
    for batch in line_batches(frame) {
        let uniforms = packed_line_uniforms_for_batch(frame, &batch)?;
        operations.push(CommandOp::Barrier(buffer_barrier(
            uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::HostWriteBuffer {
            buffer: uniform_buffer,
            offset: 0,
            data: uniforms,
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::BeginPass {
            pass: final_output.overlay_pass(),
            target: final_output.overlay_target(),
            colors: vec![loaded_frame_color_attachment(
                final_output.overlay_color_attachment(),
            )],
            depth_stencil: Some(PassAttachment {
                view: final_output.overlay_depth_attachment(),
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(match batch.depth_policy {
            WORLD_DEPTH_POLICY_TEST_WRITE => depth_write,
            WORLD_DEPTH_POLICY_TEST_NO_WRITE => depth_no_write,
            _ => depth_disabled,
        }));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Draw {
            vertices: (batch.count * 6) as u32,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
    }
    Ok(())
}

/// Uses the existing Rust-owned destroy-stage atlas and multiply pipeline in
/// the same private source overlay target as outlines. The semantic request,
/// stage mapping, depth policy, and asset generation all remain unchanged.
pub(crate) fn append_source_crack_overlay_ops(
    frame: &WorldPrimitiveFrame,
    resources: Option<(Handle, Handle, Handle, Handle, Handle)>,
    final_output: &SourceFinalOutputPlan,
    operations: &mut Vec<CommandOp>,
) -> GalResult<()> {
    let Some((uniform_buffer, pipeline_layout, resource_set, depth_disabled, depth_write)) =
        resources
    else {
        return Ok(());
    };
    for batch in crack_batches(frame) {
        let uniforms = packed_crack_uniforms_for_batch(frame, &batch)?;
        operations.push(CommandOp::Barrier(buffer_barrier(
            uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::HostWriteBuffer {
            buffer: uniform_buffer,
            offset: 0,
            data: uniforms,
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::BeginPass {
            pass: final_output.overlay_pass(),
            target: final_output.overlay_target(),
            colors: vec![loaded_frame_color_attachment(
                final_output.overlay_color_attachment(),
            )],
            depth_stencil: Some(PassAttachment {
                view: final_output.overlay_depth_attachment(),
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(
            if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                depth_write
            } else {
                depth_disabled
            },
        ));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Draw {
            vertices: 6,
            instances: batch.count as u32,
        });
        operations.push(CommandOp::EndPass);
    }
    Ok(())
}

pub(crate) fn append_source_border_overlay_ops(
    frame: &WorldPrimitiveFrame,
    resources: Option<(Handle, Handle, Handle, Handle, Handle)>,
    final_output: &SourceFinalOutputPlan,
    operations: &mut Vec<CommandOp>,
) -> GalResult<()> {
    let Some((uniform_buffer, pipeline_layout, resource_set, depth_disabled, depth_write)) =
        resources
    else {
        return Ok(());
    };
    for batch in border_batches(frame) {
        let uniforms = packed_border_uniforms_for_batch(frame, &batch)?;
        operations.push(CommandOp::Barrier(buffer_barrier(
            uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::HostWriteBuffer {
            buffer: uniform_buffer,
            offset: 0,
            data: uniforms,
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::BeginPass {
            pass: final_output.overlay_pass(),
            target: final_output.overlay_target(),
            colors: vec![loaded_frame_color_attachment(
                final_output.overlay_color_attachment(),
            )],
            depth_stencil: Some(PassAttachment {
                view: final_output.overlay_depth_attachment(),
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(
            if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                depth_write
            } else {
                depth_disabled
            },
        ));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Draw {
            vertices: 6,
            instances: batch.count as u32,
        });
        operations.push(CommandOp::EndPass);
    }
    Ok(())
}

//! The complete named-source frame plan and its submission parts.

use super::*;

/// The private combined source-frame assembly for the two terrain families
/// currently represented in the source-derived graph. Keeping ordinary
/// terrain, optional opaque Distant Horizons, and named-color history in one
/// owner prevents a later route from accidentally submitting one writer or
/// sealing feedback before the other has recorded its work.
///
/// This is deliberately not a presenter or route policy. Once the exact
/// source snapshot, semantic coverage, and all named targets are validated,
/// the production source executor joins shadow, fullscreen, and final-output
/// stages to the same Rust-owned transaction; route selection and presentation
/// remain with the outer whole-frame coordinator.
pub(crate) struct PreparedNamedSourceFramePlan {
    pub(in crate::render::worldrender) terrain: PreparedNamedSourceTerrainFramePlan,
    pub(in crate::render::worldrender) distant_horizons: Option<PreparedNamedSourceDistantHorizonsFramePlan>,
    pub(in crate::render::worldrender) fullscreen_consumers: Vec<PreparedNamedSourceFullscreenConsumer>,
    pub(in crate::render::worldrender) final_output: Option<SourceFinalOutputReservation>,
}

pub(crate) struct NamedSourceFrameSubmission {
    pub(in crate::render::worldrender) terrain: Option<SourceTerrainFrameSubmission>,
    /// Source-local textures whose upload copies are present in this complete
    /// submission, including fullscreen-only frames with no terrain token.
    pub(in crate::render::worldrender) uploaded_source_material_texture_ids: BTreeSet<u32>,
    pub(in crate::render::worldrender) pre_terrain_sky: Vec<PreparedNamedSourceFullscreenConsumer>,
    pub(in crate::render::worldrender) distant_horizons: Option<NamedSourceDistantHorizonsSubmission>,
    pub(in crate::render::worldrender) distant_horizons_target: Option<Handle>,
    pub(in crate::render::worldrender) fullscreen_consumers: Vec<PreparedNamedSourceFullscreenConsumer>,
    pub(in crate::render::worldrender) final_output: SourceFinalOutputReservation,
    pub(in crate::render::worldrender) color_transaction: ShaderPackSourceColorFrameTransaction,
}

impl NamedSourceFrameSubmission {
    /// Promotes every prepared source-frame owner only after the one combined
    /// GAL submission was accepted. Normal terrain stream reuse, optional DH
    /// column uploads/depth targets, and named-color history therefore share
    /// the same completion point.
    pub(crate) fn confirm(
        self,
        frontend: &mut WorldPrimitiveFrontend,
        gal: &mut VulkanicGal,
        submission: SubmissionId,
    ) -> GalResult<()> {
        let Self {
            terrain,
            uploaded_source_material_texture_ids,
            pre_terrain_sky,
            distant_horizons,
            distant_horizons_target: _,
            fullscreen_consumers,
            final_output,
            color_transaction,
        } = self;
        if let Some(terrain) = terrain {
            if let Err(error) =
                frontend.confirm_source_terrain_frame_transaction(&terrain, submission)
            {
                frontend.discard_source_terrain_frame_submission(gal, terrain);
                destroy_named_source_fullscreen_consumers(gal, pre_terrain_sky);
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                frontend
                    .source_final_output_cache
                    .discard(final_output, gal);
                return Err(error);
            }
        }
        if distant_horizons.is_some() {
            if let Err(error) = frontend.lod_gpu_residency.confirm_submission(gal) {
                destroy_named_source_fullscreen_consumers(gal, pre_terrain_sky);
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                frontend
                    .source_final_output_cache
                    .discard(final_output, gal);
                return Err(error);
            }
            if let Err(error) = frontend.lod_textured_gpu_residency.confirm_submission(gal) {
                destroy_named_source_fullscreen_consumers(gal, pre_terrain_sky);
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                frontend
                    .source_final_output_cache
                    .discard(final_output, gal);
                return Err(error);
            }
            frontend.release_uploaded_lod_gpu_payloads();
        }
        if let Err(error) = color_transaction.confirm(
            frontend.shader_runtime.as_mut().ok_or_else(|| {
                GalError::backend(
                    "source runtime was retired before combined source-frame confirmation",
                )
            })?,
            gal,
        ) {
            destroy_named_source_fullscreen_consumers(gal, pre_terrain_sky);
            destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
            frontend
                .source_final_output_cache
                .discard(final_output, gal);
            return Err(error);
        }
        destroy_named_source_fullscreen_consumers(gal, pre_terrain_sky);
        destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
        frontend.source_final_output_cache.confirm(&final_output)?;
        if let Some(distant_horizons) = distant_horizons {
            frontend.confirm_distant_horizons_source_targets(gal, distant_horizons.targets)?;
        }
        // A source frame may be fullscreen-only (for example, a shader-pack
        // sky or post pass). Promote its explicit local-texture uploads only
        // after every named-frame confirmation step succeeds, even when
        // there is no near-terrain stream submission to carry them.
        frontend
            .source_material_texture_resident
            .extend(uploaded_source_material_texture_ids.iter().copied());
        frontend
            .source_material_texture_upload_confirmed
            .extend(uploaded_source_material_texture_ids.iter().copied());
        for texture_id in &uploaded_source_material_texture_ids {
            frontend.source_material_texture_staged.remove(texture_id);
            frontend
                .source_material_texture_upload_operations
                .remove(texture_id);
        }
        if frontend
            .shader_runtime
            .as_ref()
            .is_some_and(|runtime| runtime.has_pending_vanilla_lightmap_submission())
        {
            // Source set-one descriptors may still retain the previous
            // lightmap view. Drop those frontend consumers before the runtime
            // retires that view. New sets are rebuilt from the confirmed
            // current-frame semantic generation on the next source frame.
            frontend.release_lightmap_dependent_source_pack_resources(gal);
            let runtime = frontend.shader_runtime.as_mut().ok_or_else(|| {
                GalError::backend("source lightmap runtime vanished before combined confirmation")
            })?;
            runtime.confirm_vanilla_lightmap_submission(gal)?;
            runtime.retire_replaced_vanilla_lightmaps(gal)?;
        }
        Ok(())
    }

    /// Releases a recorded-but-unsubmitted source transaction. This mirrors
    /// the confirmation owner boundary so a GAL validation/submit failure
    /// cannot leave stream reservations, color history, DH targets, or a
    /// newly staged final copy live for a later frame.
    pub(crate) fn discard(self, frontend: &mut WorldPrimitiveFrontend, gal: &mut VulkanicGal) {
        let Self {
            terrain,
            uploaded_source_material_texture_ids,
            pre_terrain_sky,
            distant_horizons,
            distant_horizons_target: _,
            fullscreen_consumers,
            final_output,
            color_transaction,
        } = self;
        if let Some(terrain) = terrain {
            frontend.discard_source_terrain_frame_submission(gal, terrain);
        }
        frontend.discard_unsubmitted_source_material_textures(
            gal,
            &uploaded_source_material_texture_ids,
        );
        destroy_named_source_fullscreen_consumers(gal, pre_terrain_sky);
        destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
        frontend
            .source_final_output_cache
            .discard(final_output, gal);
        if distant_horizons.is_some() {
            frontend.lod_source_targets.discard_submission(gal);
            frontend.discard_distant_horizons_generic_source_buffers(gal);
            frontend.pending_distant_horizons_source_targets = None;
            frontend.lod_gpu_residency.discard_submission(gal);
            frontend.lod_textured_gpu_residency.discard_submission(gal);
        }
        if let Some(runtime) = frontend.shader_runtime.as_mut() {
            color_transaction.discard(runtime, gal);
        }
        if frontend
            .shader_runtime
            .as_ref()
            .is_some_and(|runtime| runtime.has_pending_vanilla_lightmap_submission())
        {
            frontend.release_lightmap_dependent_source_pack_resources(gal);
            if let Some(runtime) = frontend.shader_runtime.as_mut() {
                runtime.discard_vanilla_lightmap_submission(gal);
            }
        }
    }
}

impl PreparedNamedSourceFramePlan {
    pub(in crate::render::worldrender) fn into_submission_parts<F, G>(
        self,
        gal: &mut VulkanicGal,
        runtime: &ShaderPackRuntimeExecutor,
        final_output_cache: &mut SourceFinalOutputCache,
        fog_color: ClearColor,
        pre_terrain_sky_capture: Option<&SelectedSourceOutputCapture>,
        terrain_primary_capture: Option<&SelectedSourceOutputCapture>,
        shader_pack_color_capture: Option<&SelectedSourceOutputCapture>,
        distant_horizons_primary_capture: Option<&SelectedSourceOutputCapture>,
        distant_horizons_depth_capture: Option<&SelectedSourceOutputCapture>,
        fullscreen_stage_capture: Option<&SelectedSourceOutputCapture>,
        fullscreen_stage_trace_captures: &[SelectedSourceFullscreenTraceCapture],
        operations: &mut Vec<CommandOp>,
        append_overlays: F,
        append_after_present: G,
    ) -> GalResult<NamedSourceFrameSubmission>
    where
        F: FnOnce(&mut VulkanicGal, &SourceFinalOutputPlan, &mut Vec<CommandOp>) -> GalResult<()>,
        G: FnOnce(&mut VulkanicGal, &SourceFinalOutputPlan, &mut Vec<CommandOp>) -> GalResult<()>,
    {
        let Self {
            terrain,
            distant_horizons,
            fullscreen_consumers,
            final_output,
        } = self;
        let final_output = final_output.ok_or_else(|| {
            GalError::backend(
                "complete source frame reached recording without a staged final-output plan",
            )
        })?;
        // Every frame follows Iris's order: DH opaque right before vanilla
        // opaque terrain, deferred at `beginTranslucents`, DH translucents
        // right before vanilla translucent terrain, then the composite chain.
        let deferred_before_translucents = true;
        let distant_horizons_target = distant_horizons.as_ref().map(|plan| plan.target.target);
        let distant_horizons = std::cell::RefCell::new(distant_horizons);
        let (terrain, mut color_transaction, mut pre_terrain_sky) =
            match terrain.into_submission_parts(
                runtime,
                pre_terrain_sky_capture,
                operations,
                deferred_before_translucents,
                |transaction, operations| match distant_horizons.borrow_mut().as_mut() {
                    Some(plan) => plan.append_opaque(transaction, fog_color, operations),
                    None => Ok(()),
                },
                |transaction, operations| {
                    if deferred_before_translucents {
                        for consumer in fullscreen_consumers.iter().filter(|consumer| {
                            is_deferred_source_stage(&consumer.program.source_stage_path)
                        }) {
                            append_named_source_fullscreen_consumer(
                                consumer,
                                transaction,
                                fullscreen_stage_capture,
                                fullscreen_stage_trace_captures,
                                operations,
                            )?;
                        }
                    }
                    Ok(())
                },
                |transaction, operations| match distant_horizons.borrow_mut().as_mut() {
                    Some(plan) => plan.append_translucent(transaction, fog_color, operations),
                    None => Ok(()),
                },
            ) {
                Ok(parts) => parts,
                Err(error) => {
                    destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                    final_output_cache.discard(final_output, gal);
                    return Err(error);
                }
            };
        let distant_horizons = distant_horizons.into_inner();
        if let Some(capture) = terrain_primary_capture {
            if let Err(error) = capture.append_ops(
                // The source terrain transaction closes its named outputs in
                // ShaderRead state before any optional diagnostic readback.
                // Declaring ColorAttachment here replays an impossible old
                // layout and triggers VUID-VkImageMemoryBarrier2-oldLayout.
                selected_source_terrain_capture_before_state(),
                TextureUsageState::ShaderRead,
                operations,
            ) {
                destroy_named_source_fullscreen_consumers(
                    gal,
                    std::mem::take(&mut pre_terrain_sky),
                );
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                final_output_cache.discard(final_output, gal);
                return Err(error);
            }
        }
        if let Some(capture) = shader_pack_color_capture {
            if let Err(error) = capture.append_ops(
                TextureUsageState::ShaderRead,
                TextureUsageState::ShaderRead,
                operations,
            ) {
                destroy_named_source_fullscreen_consumers(
                    gal,
                    std::mem::take(&mut pre_terrain_sky),
                );
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                final_output_cache.discard(final_output, gal);
                return Err(error);
            }
        }
        let distant_horizons = distant_horizons.map(PreparedNamedSourceDistantHorizonsFramePlan::into_submission);
        if let Some(capture) = distant_horizons_primary_capture {
            if let Err(error) = capture.append_ops(
                TextureUsageState::ShaderRead,
                TextureUsageState::ShaderRead,
                operations,
            ) {
                destroy_named_source_fullscreen_consumers(
                    gal,
                    std::mem::take(&mut pre_terrain_sky),
                );
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                final_output_cache.discard(final_output, gal);
                return Err(error);
            }
        }
        if let Some(capture) = distant_horizons_depth_capture {
            if let Err(error) = capture.append_ops(
                TextureUsageState::ShaderRead,
                TextureUsageState::ShaderRead,
                operations,
            ) {
                destroy_named_source_fullscreen_consumers(
                    gal,
                    std::mem::take(&mut pre_terrain_sky),
                );
                destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
                final_output_cache.discard(final_output, gal);
                return Err(error);
            }
        }
        let mut appended_fullscreen_consumers = Vec::with_capacity(fullscreen_consumers.len());
        for consumer in fullscreen_consumers {
            if !(deferred_before_translucents
                && is_deferred_source_stage(&consumer.program.source_stage_path))
            {
                if let Err(error) = append_named_source_fullscreen_consumer(
                    &consumer,
                    &mut color_transaction,
                    fullscreen_stage_capture,
                    fullscreen_stage_trace_captures,
                    operations,
                ) {
                    consumer.destroy(gal);
                    destroy_named_source_fullscreen_consumers(
                        gal,
                        std::mem::take(&mut pre_terrain_sky),
                    );
                    destroy_named_source_fullscreen_consumers(gal, appended_fullscreen_consumers);
                    final_output_cache.discard(final_output, gal);
                    return Err(error);
                }
            }
            appended_fullscreen_consumers.push(consumer);
        }
        if let Err(error) = color_transaction.finish(operations) {
            destroy_named_source_fullscreen_consumers(gal, std::mem::take(&mut pre_terrain_sky));
            destroy_named_source_fullscreen_consumers(gal, appended_fullscreen_consumers);
            final_output_cache.discard(final_output, gal);
            return Err(error);
        }
        match final_output_cache.plan(&final_output) {
            Ok(plan) => {
                // Keep the selected source chain and the one final
                // presentation copy distinct. The intermediate is a
                // Rust-owned color target paired with the source main depth;
                // future supported world overlays belong here, never on a
                // depthless swapchain target or in Java/Iris state.
                plan.append_source_copy_from_state(
                    operations,
                    if final_output.newly_staged() {
                        TextureUsageState::Undefined
                    } else {
                        TextureUsageState::ShaderRead
                    },
                );
                if let Err(error) = append_overlays(gal, plan, operations) {
                    destroy_named_source_fullscreen_consumers(
                        gal,
                        std::mem::take(&mut pre_terrain_sky),
                    );
                    destroy_named_source_fullscreen_consumers(gal, appended_fullscreen_consumers);
                    final_output_cache.discard(final_output, gal);
                    return Err(error);
                }
                plan.append_present_copy(operations);
                if let Err(error) = append_after_present(gal, plan, operations) {
                    destroy_named_source_fullscreen_consumers(
                        gal,
                        std::mem::take(&mut pre_terrain_sky),
                    );
                    destroy_named_source_fullscreen_consumers(gal, appended_fullscreen_consumers);
                    final_output_cache.discard(final_output, gal);
                    return Err(error);
                }
            }
            Err(error) => {
                destroy_named_source_fullscreen_consumers(
                    gal,
                    std::mem::take(&mut pre_terrain_sky),
                );
                destroy_named_source_fullscreen_consumers(gal, appended_fullscreen_consumers);
                final_output_cache.discard(final_output, gal);
                return Err(error);
            }
        }
        let uploaded_source_material_texture_ids = terrain
            .as_ref()
            .map(|submission| submission.uploaded_source_material_texture_ids.clone())
            .unwrap_or_default();
        Ok(NamedSourceFrameSubmission {
            terrain,
            uploaded_source_material_texture_ids,
            pre_terrain_sky,
            distant_horizons,
            distant_horizons_target,
            fullscreen_consumers: appended_fullscreen_consumers,
            final_output,
            color_transaction,
        })
    }

    /// Discards the complete unsubmitted source frame. This is intentionally
    /// stronger than dropping the plan: the normal terrain stream reservation,
    /// named-color bootstrap, and any staged DH depth/upload work must all be
    /// released together after a later preparation failure.
    pub(crate) fn discard(self, frontend: &mut WorldPrimitiveFrontend, gal: &mut VulkanicGal) {
        let PreparedNamedSourceFramePlan {
            terrain,
            distant_horizons,
            fullscreen_consumers,
            final_output,
        } = self;
        // This is also correct for a DH-only source frame: no near-terrain
        // token exists, but the generic cleanup is still idempotent and
        // releases any frame-local source material staging.
        frontend.discard_source_terrain_frame_transaction(gal, terrain.terrain.frame_id);
        destroy_named_source_fullscreen_consumers(gal, terrain.pre_terrain_sky);
        destroy_named_source_fullscreen_consumers(gal, fullscreen_consumers);
        if let Some(final_output) = final_output {
            frontend
                .source_final_output_cache
                .discard(final_output, gal);
        }
        if distant_horizons.is_some() {
            frontend.lod_source_targets.discard_submission(gal);
            frontend.discard_distant_horizons_generic_source_buffers(gal);
            frontend.pending_distant_horizons_source_targets = None;
            frontend.lod_gpu_residency.discard_submission(gal);
            frontend.lod_textured_gpu_residency.discard_submission(gal);
        }
        terrain.color_transaction.discard(
            frontend
                .shader_runtime
                .as_mut()
                .expect("source runtime remains installed while discarding a source frame"),
            gal,
        );
    }
}

impl WorldPrimitiveFrontend {
    /// Hashes the decoded fixed-layout source vertices in the same canonical
    /// little-endian field order as Java's capture-only producer receipt. This
    /// is audit evidence only; it never participates in route selection or
    /// resource identity.
    pub(crate) fn semantic_source_hash(segment: &WorldLodSegment) -> u64 {
        let mut hash = 0xcbf29ce484222325u64;
        for vertex in &segment.vertices {
            for value in vertex.local_position {
                hash = Self::fnv_update_u16(hash, value);
            }
            hash = Self::fnv_update_u16(hash, vertex.packed_light_and_micro_offset);
            for value in vertex.color_rgba {
                hash = Self::fnv_update_byte(hash, value);
            }
            hash = Self::fnv_update_byte(hash, vertex.material_id);
            hash = Self::fnv_update_byte(hash, vertex.normal_index);
            hash = Self::fnv_update_u16(hash, 0);
        }
        hash
    }
}

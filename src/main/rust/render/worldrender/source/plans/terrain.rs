//! The named-source terrain frame plan and its preparation.

use super::*;

/// One complete normal-terrain portion of a source-derived frame. It couples
/// the source mesh upload reservation to the named color scheduler and pass
/// recording, so an eventual source graph cannot submit either half on its
/// own. This is private assembly only: presentation, route selection, DH,
/// fullscreen, and final-output completion remain separate admission
/// requirements.
pub(crate) struct PreparedNamedSourceTerrainFramePlan {
    pub(in crate::render::worldrender) terrain: PreparedLoweredSourceTerrainFramePlan,
    /// Caster geometry outside the camera color domain. These draws are
    /// admitted only to the Rust-owned shadow pass and cannot reach the
    /// G-buffer or translucent color writers.
    pub(in crate::render::worldrender) shadow_only_draws: Vec<TerrainShadowMeshDraw>,
    /// Entity-stream shadow casters (pack `shadow` program, entity layout).
    pub(in crate::render::worldrender) entity_shadow_draws: Vec<EntitySourceDraw>,
    pub(in crate::render::worldrender) entities: Option<PreparedNamedSourceEntityFramePlan>,
    pub(in crate::render::worldrender) hands: Option<PreparedNamedSourceHandFramePlan>,
    pub(in crate::render::worldrender) textured_material: Option<PreparedNamedSourceTexturedMaterialFramePlan>,
    pub(in crate::render::worldrender) weather: Option<PreparedNamedSourceWeatherFramePlan>,
    pub(in crate::render::worldrender) clouds: Option<PreparedNamedSourceCloudFramePlan>,
    pub(in crate::render::worldrender) lines: Option<PreparedNamedSourceLineFramePlan>,
    pub(in crate::render::worldrender) damaged_block: Option<PreparedNamedSourceDamagedBlockFramePlan>,
    pub(in crate::render::worldrender) entity_glint: Option<PreparedNamedSourceGlintFramePlan>,
    pub(in crate::render::worldrender) hand_glint: Option<PreparedNamedSourceGlintFramePlan>,
    pub(in crate::render::worldrender) color_targets: ShaderPackColorTargets,
    pub(in crate::render::worldrender) shadow_targets: Option<TerrainSourceShadowPassTargets>,
    pub(in crate::render::worldrender) main_depth_history: Option<(TerrainDepthHistoryTargets, TerrainDepthHistoryPlan)>,
    pub(in crate::render::worldrender) targets: TerrainSourceColorPassTargets,
    /// A separately staged writer for source-derived translucent terrain.
    /// It is deliberately distinct from `targets`: the pack may declare a
    /// different named output schema and this writer must load, never clear,
    /// the opaque/cutout colors and depth.
    pub(in crate::render::worldrender) translucent_targets: Option<TerrainSourceColorPassTargets>,
    /// Source-defined begin/prepare and sky writers. The sky initializer and textured
    /// celestial writer are distinct source stages, but share the same
    /// Rust-owned color transaction and must execute before opaque terrain.
    pub(in crate::render::worldrender) pre_terrain_sky: Vec<PreparedNamedSourceFullscreenConsumer>,
    pub(in crate::render::worldrender) color_transaction: ShaderPackSourceColorFrameTransaction,
    pub(in crate::render::worldrender) bootstrap_operations: Vec<CommandOp>,
}

fn is_begin_source_stage(path: &str) -> bool {
    path.rsplit('/').next().is_some_and(|name| name.starts_with("begin") && name.ends_with(".fsh"))
}

impl PreparedNamedSourceTerrainFramePlan {
    pub(crate) fn color_targets(&self) -> &ShaderPackColorTargets {
        &self.color_targets
    }

    /// Appends the terrain upload and named color pass to one combined command
    /// list and records its named outputs. It deliberately leaves the color
    /// transaction open: DH and later source stages may write the same pack
    /// generation before the sole frame executor seals feedback/history. The
    /// caller receives both confirmation tokens only after every operation is
    /// recorded, preventing a source stream slot or feedback image from
    /// advancing independently.
    pub(in crate::render::worldrender) fn into_submission_parts<F, O, T>(
        mut self,
        runtime: &ShaderPackRuntimeExecutor,
        pre_terrain_sky_capture: Option<&SelectedSourceOutputCapture>,
        operations: &mut Vec<CommandOp>,
        hands_before_deferred: bool,
        mut append_before_opaque_terrain: O,
        mut append_before_translucents: F,
        mut append_before_translucent_terrain: T,
    ) -> GalResult<(
        Option<SourceTerrainFrameSubmission>,
        ShaderPackSourceColorFrameTransaction,
        Vec<PreparedNamedSourceFullscreenConsumer>,
    )>
    where
        F: FnMut(&mut ShaderPackSourceColorFrameTransaction, &mut Vec<CommandOp>) -> GalResult<()>,
        O: FnMut(&mut ShaderPackSourceColorFrameTransaction, &mut Vec<CommandOp>) -> GalResult<()>,
        T: FnMut(&mut ShaderPackSourceColorFrameTransaction, &mut Vec<CommandOp>) -> GalResult<()>,
    {
        // Iris `beginTranslucents()` runs `beginHand()` (pre-hand depthtex2
        // copy), then `HandRenderer.renderSolid` into the main depth, then the
        // pre-translucent depthtex1 copy and the deferred chain. The solid hand
        // is therefore part of deferred lighting and of depthtex1/depthtex0.
        let hands_before_deferred = hands_before_deferred
            && self.hands.as_ref().is_some_and(|hands| hands.copies_world_depth)
            && self.main_depth_history.is_some();
        operations.append(&mut self.bootstrap_operations);
        for sky in self.pre_terrain_sky.iter().filter(|stage| is_begin_source_stage(&stage.program.source_stage_path)) {
            let operation_start = operations.len();
            sky.append(&mut self.color_transaction, operations)?;
            require_source_fullscreen_writer_coverage(
                "pre-terrain sky",
                &operations[operation_start..],
            )?;
            if let Some(capture) =
                pre_terrain_sky_capture.filter(|capture| capture.matches_fullscreen_consumer(sky))
            {
                capture.append_ops(
                    // The fullscreen source writer finishes with its named
                    // outputs in shader-read state.  The diagnostic copy must
                    // describe that actual state rather than replaying the
                    // attachment state from before the writer.
                    TextureUsageState::ShaderRead,
                    TextureUsageState::ShaderRead,
                    operations,
                )?;
            }
        }
        let (draws, upload_ops, terrain_submission) = self.terrain.into_submission_parts();
        operations.extend(upload_ops);
        if let Some(shadow_targets) = self.shadow_targets {
            runtime.append_terrain_source_shadow_pass(
                operations,
                shadow_targets,
                &draws,
                &self.shadow_only_draws,
                &self.entity_shadow_draws,
            )?;
        } else if !self.shadow_only_draws.is_empty()
            || !self.entity_shadow_draws.is_empty()
            || draws.iter().any(|draw| draw.shadow_participation == TerrainShadowParticipation::Required)
        {
            return Err(GalError::invalid_argument(
                "named source terrain draws require an explicit Rust-owned shadow target",
            ));
        }
        for sky in self.pre_terrain_sky.iter().filter(|stage| !is_begin_source_stage(&stage.program.source_stage_path)) {
            let operation_start = operations.len();
            sky.append(&mut self.color_transaction, operations)?;
            require_source_fullscreen_writer_coverage(
                "pre-terrain sky",
                &operations[operation_start..],
            )?;
            if let Some(capture) =
                pre_terrain_sky_capture.filter(|capture| capture.matches_fullscreen_consumer(sky))
            {
                capture.append_ops(
                    // The fullscreen source writer finishes with its named
                    // outputs in shader-read state.  The diagnostic copy must
                    // describe that actual state rather than replaying the
                    // attachment state from before the writer.
                    TextureUsageState::ShaderRead,
                    TextureUsageState::ShaderRead,
                    operations,
                )?;
            }
        }
        // Iris + DH (Frozen LevelRenderer): DH draws its opaque LODs from
        // prepareChunkRenders, after the sky and immediately before vanilla
        // opaque terrain, which then paints over DH wherever it draws.
        append_before_opaque_terrain(&mut self.color_transaction, operations)?;
        // Resolve against writers recorded in this exact transaction, then
        // publish opaque outputs before any deferred/feedback consumer.
        self.color_transaction.resolve_terrain_color_clear_policy(&mut self.targets.color_attachments)?;
        runtime.append_terrain_source_color_pass(operations, &self.targets, &draws)?;
        let bootstrap_output_roles = self.targets.color_attachments.iter()
            .map(|attachment| attachment.role.clone()).collect::<Vec<_>>();
        self.color_transaction.record_external_outputs(&bootstrap_output_roles)?;
        if let Some(entities) = self.entities {
            runtime.append_entity_source_color_pass(
                operations,
                &entities.targets,
                &entities.draws,
            )?;
            let entity_output_roles = entities
                .targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&entity_output_roles)?;
        }
        if let Some(glint) = self.entity_glint.as_ref() {
            runtime.append_glint_source_color_pass(
                operations,
                &glint.targets,
                &glint.draws,
                TerrainSourceColorPassPhase::EntityGlint,
            )?;
            let glint_output_roles = glint
                .targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&glint_output_roles)?;
        }
        // Iris: `renderBlockOutline(.., false)` follows the opaque flush and
        // precedes `beginTranslucents` (hand, depth copies, deferred).
        if let Some(lines) = self.lines.as_ref().filter(|lines| !lines.opaque_draws.is_empty()) {
            runtime.append_line_source_color_pass(operations, &lines.targets, &lines.opaque_draws)?;
            let line_output_roles = lines
                .targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&line_output_roles)?;
        }
        // Iris: `renderBlockDestroyAnimation` + crumbling flush follow the
        // outline and precede `beginTranslucents`.
        if let Some(damaged) = self.damaged_block.as_ref().filter(|plan| !plan.draws.is_empty()) {
            runtime.append_damaged_block_source_color_pass(
                operations,
                &damaged.targets,
                &damaged.draws,
            )?;
            let damaged_output_roles = damaged
                .targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&damaged_output_roles)?;
        }
        let mut late_translucent_hands = None;
        if hands_before_deferred {
            let (targets, history) = self
                .main_depth_history
                .expect("early hands require the owned world depth plan");
            let mut hands = self.hands.take().expect("early hands were checked");
            late_translucent_hands = Some((
                hands.targets.clone(),
                std::mem::take(&mut hands.translucent_draws),
                targets.main_depth_texture,
                history.extent,
            ))
            .filter(|(_, draws, _, _)| !draws.is_empty());
            // depthtex2: opaque world depth without the hand.
            ShaderPackRuntimeExecutor::append_source_main_depth_snapshot(
                operations,
                targets.main_depth_texture,
                targets.previous_texture,
                history.extent,
            )?;
            runtime.append_hand_source_color_pass(
                operations,
                &hands.targets,
                &hands.draws,
                Some((targets.main_depth_texture, history.extent)),
            )?;
            if let Some(glint) = self.hand_glint.as_ref() {
                runtime.append_glint_source_color_pass(
                    operations,
                    &glint.targets,
                    &glint.draws,
                    TerrainSourceColorPassPhase::HandGlint,
                )?;
            }
            // The hand draws into a private copy of world depth; publish that
            // merged depth as the main depth so depthtex1, deferred, later
            // world writers, and composites observe the hand exactly as Iris.
            ShaderPackRuntimeExecutor::append_source_main_depth_snapshot(
                operations,
                hands.targets.depth_texture,
                targets.main_depth_texture,
                history.extent,
            )?;
            let hand_output_roles = hands
                .targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&hand_output_roles)?;
        }
        if let Some((targets, history)) = self.main_depth_history {
            // Frozen depthtex1 is this frame's opaque depth, captured before
            // translucent world writers or deferred consumers execute.
            ShaderPackRuntimeExecutor::append_source_main_depth_snapshot(
                operations,
                targets.main_depth_texture,
                targets.before_translucency_texture,
                history.extent,
            )?;
        }
        // Iris begins the deferred chain at beginTranslucents, after the
        // opaque depth snapshot and before any translucent world writer.
        append_before_translucents(&mut self.color_transaction, operations)?;
        for writer in VANILLA_POST_TERRAIN_SOURCE_WRITER_ORDER {
            match writer {
                // This is an alpha-composition dependency, not a backend
                // detail: later source writers must never be hidden behind
                // translucent water or glass merely because the Rust
                // transaction was assembled in a convenient producer order.
                VanillaPostTerrainSourceWriter::TranslucentTerrain => {
                    // Iris + DH: DH renders its deferred translucent LODs
                    // (`dh_water`) right before vanilla's translucent layer.
                    append_before_translucent_terrain(&mut self.color_transaction, operations)?;
                    if let Some(translucent_targets) = self.translucent_targets.as_ref() {
                        runtime.append_terrain_source_color_pass(
                            operations,
                            translucent_targets,
                            &draws,
                        )?;
                        let output_roles = translucent_targets
                            .color_attachments
                            .iter()
                            .map(|attachment| attachment.role.clone())
                            .collect::<Vec<_>>();
                        self.color_transaction
                            .record_external_outputs(&output_roles)?;
                    }
                    // Iris: translucent-block outlines follow translucent
                    // terrain (and tripwire) in the same main pass.
                    if let Some(lines) = self
                        .lines
                        .as_ref()
                        .filter(|lines| !lines.translucent_draws.is_empty())
                    {
                        runtime.append_line_source_color_pass(
                            operations,
                            &lines.targets,
                            &lines.translucent_draws,
                        )?;
                        let line_output_roles = lines
                            .targets
                            .color_attachments
                            .iter()
                            .map(|attachment| attachment.role.clone())
                            .collect::<Vec<_>>();
                        self.color_transaction
                            .record_external_outputs(&line_output_roles)?;
                    }
                }
                VanillaPostTerrainSourceWriter::TexturedMaterial => {
                    if let Some(textured_material) = self.textured_material.as_ref() {
                        runtime.append_textured_material_source_color_pass(
                            operations,
                            &textured_material.targets,
                            &textured_material.draws,
                        )?;
                        let output_roles = textured_material
                            .targets
                            .color_attachments
                            .iter()
                            .map(|attachment| attachment.role.clone())
                            .collect::<Vec<_>>();
                        self.color_transaction
                            .record_external_outputs(&output_roles)?;
                    }
                }
                VanillaPostTerrainSourceWriter::Clouds => {
                    if let Some(clouds) = self.clouds.as_ref() {
                        runtime.append_cloud_source_color_pass(
                            operations,
                            &clouds.targets,
                            &clouds.draws,
                        )?;
                        let cloud_output_roles = clouds
                            .targets
                            .color_attachments
                            .iter()
                            .map(|attachment| attachment.role.clone())
                            .collect::<Vec<_>>();
                        self.color_transaction
                            .record_external_outputs(&cloud_output_roles)?;
                    }
                }
                VanillaPostTerrainSourceWriter::Weather => {
                    if let Some(weather) = self.weather.as_ref() {
                        runtime.append_weather_source_color_pass(
                            operations,
                            &weather.targets,
                            &weather.draws,
                        )?;
                        let weather_output_roles = weather
                            .targets
                            .color_attachments
                            .iter()
                            .map(|attachment| attachment.role.clone())
                            .collect::<Vec<_>>();
                        self.color_transaction
                            .record_external_outputs(&weather_output_roles)?;
                    }
                }
            }
        }
        // Iris `HandRenderer.renderTranslucent`: after every world writer,
        // immediately before the composite chain, into the main depth (so
        // depthtex0 carries the hand while depthtex1 keeps the earlier
        // pre-translucent snapshot).
        if let Some((hand_targets, draws, main_depth_texture, extent)) = late_translucent_hands {
            runtime.append_hand_source_color_pass(
                operations,
                &hand_targets,
                &draws,
                Some((main_depth_texture, extent)),
            )?;
            ShaderPackRuntimeExecutor::append_source_main_depth_snapshot(
                operations,
                hand_targets.depth_texture,
                main_depth_texture,
                extent,
            )?;
            let hand_output_roles = hand_targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&hand_output_roles)?;
        }
        if let Some((targets, history)) = self.main_depth_history.filter(|_| !hands_before_deferred) {
            // Legacy (DH-combined / stencil-hand) ordering: depthtex2 is copied
            // immediately before the late first-person writer.
            ShaderPackRuntimeExecutor::append_source_main_depth_snapshot(
                operations,
                targets.main_depth_texture,
                targets.previous_texture,
                history.extent,
            )?;
        }
        // First-person geometry runs after world-depth writers and retains
        // their depth in its private attachment for later depthtex0 sampling.
        if let Some(hands) = self.hands {
            let world_depth = if hands.copies_world_depth {
                let (targets, history) = self.main_depth_history.ok_or_else(|| {
                    GalError::backend("hand depth copy requires the owned world depth plan")
                })?;
                Some((targets.main_depth_texture, history.extent))
            } else {
                None
            };
            let mut late_draws = hands.draws;
            late_draws.extend(hands.translucent_draws);
            runtime.append_hand_source_color_pass(operations, &hands.targets, &late_draws, world_depth)?;
            if let Some(glint) = self.hand_glint.as_ref() {
                runtime.append_glint_source_color_pass(
                    operations,
                    &glint.targets,
                    &glint.draws,
                    TerrainSourceColorPassPhase::HandGlint,
                )?;
            }
            let hand_output_roles = hands
                .targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.role.clone())
                .collect::<Vec<_>>();
            self.color_transaction
                .record_external_outputs(&hand_output_roles)?;
        }
        Ok((
            terrain_submission,
            self.color_transaction,
            self.pre_terrain_sky,
        ))
    }
}

impl WorldPrimitiveFrontend {
    /// Assembles the ordinary terrain portion of a source-derived frame over
    /// the pack's Rust-owned named color generation. This is intentionally a
    /// private transaction builder: it prepares no presenter and cannot make
    /// the selected-source route available on its own. Its purpose is to keep
    /// vanilla terrain's stream upload, named color samplers, bootstrap clear,
    /// and color history inseparable from the eventual combined terrain/DH
    /// source submission.
    pub(in crate::render::worldrender) fn prepare_named_source_terrain_frame_plan(
        &mut self,
        gal: &mut VulkanicGal,
        programs: &LoweredSourceTerrainPrograms,
        world_generation: u64,
        graph_generation: u64,
        frame: &WorldPrimitiveFrame,
        batches: &[MeshBatch],
        shadow_batches: &[MeshBatch],
        extent: Extent3d,
        depth_texture: Handle,
        depth_view: Handle,
        shadow_targets: TerrainSourceShadowPassTargets,
        clear_values: ShaderPackColorClearValues,
        source_color_initializer: bool,
    ) -> GalResult<PreparedNamedSourceTerrainFramePlan> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "named source terrain preparation requires a non-zero world generation",
            ));
        }
        let shader_pack_generation = programs.shader_pack_generation()?;
        let color_targets = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "named source terrain preparation requires an initialized shader runtime",
                )
            })?
            .stage_complete_source_color_targets(gal, world_generation, extent)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source terrain program has no Rust-owned named color target manifest",
                )
            })?;
        if color_targets.identity.shader_pack_generation != shader_pack_generation {
            self.discard_source_color_submission(gal);
            return Err(GalError::invalid_argument(
                "named source terrain color targets do not match the lowered program generation",
            ));
        }

        for staging in std::mem::take(&mut self.retired_source_geometry_staging) {
            let _ = gal.destroy(staging);
        }
        self.retire_unused_decal_glint_meshes(gal);
        let emptied_pages = self.source_terrain_geometry_pages.reclaim(gal);
        if !emptied_pages.is_empty() {
            let page_keys = self
                .lowered_source_terrain_frame_data_resources
                .keys()
                .filter(|key| {
                    key.geometry.abi == SourceGeometryAbi::TerrainPage
                        && emptied_pages.iter().any(|page| page.raw() == key.geometry.mesh_key)
                })
                .cloned()
                .collect::<Vec<_>>();
            for key in page_keys {
                if let Some(resources) = self.lowered_source_terrain_frame_data_resources.remove(&key) {
                    for handle in resources.handles_in_destroy_order() {
                        let _ = gal.destroy(handle);
                    }
                }
            }
            for page in emptied_pages {
                let _ = gal.destroy(page);
            }
        }
        let result = (|| -> GalResult<PreparedNamedSourceTerrainFramePlan> {
            let terrain_phase = if source_color_initializer {
                TerrainSourceColorPassPhase::BootstrapAfterInitialization
            } else {
                TerrainSourceColorPassPhase::for_program(&programs.opaque)
            };
            let terrain_targets = self
                .stage_source_terrain_color_pass_targets(
                    gal,
                    world_generation,
                    graph_generation,
                    extent,
                    &programs.opaque,
                    &color_targets,
                    depth_texture,
                    depth_view,
                    clear_values,
                    terrain_phase,
                )?
                .clone();
            let opaque_resources = self.stage_candidate_source_resources_for_terrain_program(
                gal,
                shader_pack_generation,
                world_generation,
                frame.frame_id,
                &programs.opaque,
                &color_targets,
            )?;
            let cutout_resources = self.stage_candidate_source_resources_for_terrain_program(
                gal,
                shader_pack_generation,
                world_generation,
                frame.frame_id,
                &programs.cutout,
                &color_targets,
            )?;
            let opaque_formats = terrain_targets
                .color_attachments
                .iter()
                .map(|attachment| attachment.format)
                .collect::<Vec<_>>();
            let cutout_targets = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime remains installed while resolving cutout outputs")
                .resolve_terrain_source_color_outputs(&programs.cutout, &color_targets)?;
            let cutout_formats = cutout_targets
                .iter()
                .map(|attachment| attachment.format)
                .collect::<Vec<_>>();
            if opaque_formats != cutout_formats {
                return Err(GalError::unsupported_feature(
                    "ordinary source terrain opaque and cutout programs require different named color target schemas",
                ));
            }

            // The source pack owns a distinct translucent program and may
            // declare a different named output schema for it. Resolve and
            // stage that writer only when this exact frame actually contains
            // translucent terrain, so an incomplete water/transparency
            // contract cannot block ordinary vanilla terrain or DH.
            let has_translucent_batches = batches.iter().any(|batch| {
                is_source_terrain_mesh_stratum(batch.key.stratum)
                    && batch.key.g_buffer
                    && batch.key.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
            });
            let has_bootstrap_batches = batches.iter().any(|batch| {
                is_source_terrain_mesh_stratum(batch.key.stratum)
                    && batch.key.g_buffer
                    && matches!(
                        batch.key.material_mode,
                        WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT
                    )
            });
            let scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?.ok_or_else(|| GalError::unsupported_feature("source shadow policy requires a dimension scope"))?;
            let shadow_policy = self
                .shader_pack_sources
                .active_shadow_policy_for_scope(scope)?
                .filter(|policy| policy.generation() == shader_pack_generation)
                .ok_or_else(|| GalError::invalid_argument("named source shadow policy generation is missing or stale"))?;
            let render_translucent_shadows = shadow_policy.render_translucent();
            let selected_shadow_batches = if shadow_batches.is_empty()
                || terrain_program_scope_for_sky_type(frame.background.sky_type)?
                    != Some(TerrainProgramScope::Overworld)
            {
                Vec::new()
            } else {
                let frustum = crate::render::shaderpack::properties::shadow::AdvancedShadowCasterFrustum::from_frame_with_distances(
                    shadow_policy,
                    frame.shader_environment.time_of_day,
                    frame.projection_matrix,
                    frame.view_matrix,
                    crate::render::shaderpack::properties::shadow::ShadowCasterFrameDistances {
                        render_distance_blocks: frame.shader_environment.far_plane,
                        configured_shadow_distance_chunks: frame.shader_environment.configured_shadow_distance_chunks,
                    },
                    crate::render::shaderpack::properties::shadow::ShadowCasterKind::Terrain,
                )?;
                shadow_batches
                    .iter()
                    .filter_map(|batch| {
                        let mut selected = batch.clone();
                        selected.indices.retain(|index| {
                            let instance = &frame.mesh_instances[*index];
                            instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY == 0 || source_shadow_instance_intersects(
                                &frustum,
                                instance,
                                Some(frame.shader_environment.far_plane),
                            )
                        });
                        (!selected.indices.is_empty()).then_some(selected)
                    })
                    .collect::<Vec<_>>()
            };
            // Iris shadow-pass entity casters (`ShadowRenderer`): with
            // `shadowEntities` every rendered entity (and the local player)
            // casts; otherwise only the local player when `shadowPlayer`.
            // Block entities follow `shadowBlockEntities`. Casters are cloned
            // onto the ordinary entity stratum for the shared entity stream.
            let entity_shadow_casters =
                if terrain_program_scope_for_sky_type(frame.background.sky_type)?
                    == Some(TerrainProgramScope::Overworld)
                {
                    select_source_entity_shadow_casters(frame, shadow_policy)?
                } else {
                    Vec::new()
                };
            let has_shadow_casters = !selected_shadow_batches.is_empty() || has_bootstrap_batches
                || (has_translucent_batches && render_translucent_shadows)
                || !entity_shadow_casters.is_empty();
            let shadow_alpha_cutoff = shadow_policy.cutout_alpha_cutoff();
            let (entity_shadow_program, entity_shadow_frames, entity_shadow_resources) =
                if entity_shadow_casters.is_empty() {
                    (None, Vec::new(), None)
                } else {
                    let program = self.entity_shadow_program(shader_pack_generation)?;
                    let render_stage = self.source_shadow_render_stage()?;
                    let frames = self
                        .prepare_source_entity_frames_for(
                            &program,
                            frame,
                            &entity_shadow_casters,
                            Some(render_stage),
                            false,
                        )?
                        .into_iter()
                        .filter(|prepared| prepared.material_mode != WORLD_MATERIAL_MODE_GLINT)
                        .collect::<Vec<_>>();
                    let resources = self.stage_candidate_source_resources_for_entity_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &program,
                        &color_targets,
                    )?;
                    (Some(program), frames, Some(resources))
                };
            let shadow_resources = has_shadow_casters
                .then(|| {
                    self.candidate_source_resources_for_program(
                        shader_pack_generation,
                        world_generation,
                        frame.frame_id,
                        &programs.shadow,
                    )
                    .cloned()
                })
                .transpose()?;
            let (
                translucent_program,
                translucent_targets,
                translucent_resources,
                translucent_formats,
            ) = if has_translucent_batches {
                let program = self
                        .shader_runtime
                        .as_ref()
                        .expect("shader runtime remains installed while resolving translucent source program")
                        .prepared_lowered_translucent_terrain_source_program()?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "named source terrain frame contains translucent batches but the selected pack has no admitted translucent program",
                            )
                        })?;
                if program.shader_pack_generation != shader_pack_generation {
                    return Err(GalError::invalid_argument(
                        "translucent source program does not match the opaque/cutout shader-pack generation",
                    ));
                }
                let translucent_phase = source_terrain_translucent_phase(has_bootstrap_batches);
                let targets = self
                    .stage_source_terrain_color_pass_targets(
                        gal,
                        world_generation,
                        graph_generation,
                        extent,
                        &program,
                        &color_targets,
                        depth_texture,
                        depth_view,
                        clear_values,
                        translucent_phase,
                    )?
                    .clone();
                let resources = self.stage_candidate_source_resources_for_terrain_program(
                    gal,
                    shader_pack_generation,
                    world_generation,
                    frame.frame_id,
                    &program,
                    &color_targets,
                )?;
                let formats = targets
                    .color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect::<Vec<_>>();
                (Some(program), Some(targets), Some(resources), Some(formats))
            } else {
                (None, None, None, None)
            };

            let textured_material_batches = source_textured_material_batches(frame)?;
            let (
                textured_material_program,
                textured_material_targets,
                textured_material_resources,
                textured_material_formats,
            ) = if textured_material_batches.is_empty() {
                (None, None, None, None)
            } else {
                let program = self
                    .shader_runtime
                    .as_ref()
                    .expect("shader runtime remains installed while resolving gbuffers_textured")
                    .prepared_lowered_textured_material_source_program()?
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "selected source frame has textured material work but no lowered gbuffers_textured program",
                        )
                    })?;
                if program.shader_pack_generation != shader_pack_generation {
                    return Err(GalError::invalid_argument(
                        "gbuffers_textured program generation does not match the named terrain target generation",
                    ));
                }
                let targets = self
                    .stage_textured_material_source_color_pass_targets(
                        gal,
                        world_generation,
                        graph_generation,
                        extent,
                        &program,
                        &color_targets,
                        depth_texture,
                        depth_view,
                        clear_values,
                    )?
                    .clone();
                let resources = self
                    .stage_candidate_source_resources_for_textured_material_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &program,
                        &color_targets,
                    )?;
                let formats = targets
                    .color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect::<Vec<_>>();
                (Some(program), Some(targets), Some(resources), Some(formats))
            };

            let weather_batches = source_weather_material_batches(frame)?;
            let (weather_program, weather_targets, weather_resources, weather_formats) =
                if weather_batches.is_empty() {
                    (None, None, None, None)
                } else {
                    let weather = self
                    .shader_runtime
                    .as_ref()
                    .expect("shader runtime remains installed while resolving gbuffers_weather")
                    .prepared_lowered_weather_source_program()?
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "selected source frame has weather work but no lowered gbuffers_weather program",
                        )
                    })?;
                    if weather.shader_pack_generation != shader_pack_generation {
                        return Err(GalError::invalid_argument(
                            "gbuffers_weather program generation does not match the named terrain target generation",
                        ));
                    }
                    let program = weather.material_stream_program();
                    let targets = self
                        .stage_weather_source_color_pass_targets(
                            gal,
                            world_generation,
                            graph_generation,
                            extent,
                            &program,
                            &color_targets,
                            depth_texture,
                            depth_view,
                            clear_values,
                        )?
                        .clone();
                    let resources = self.stage_candidate_source_resources_for_weather_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &weather,
                        &color_targets,
                    )?;
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    (Some(program), Some(targets), Some(resources), Some(formats))
                };

            let cloud_batches = source_cloud_material_batches(frame)?;
            let clouds_suppressed = self
                .shader_runtime
                .as_ref()
                .is_some_and(ShaderPackRuntimeExecutor::suppresses_vanilla_cloud_faces);
            let (cloud_program, cloud_targets, cloud_resources, cloud_formats, cloud_blend) =
                if cloud_batches.is_empty() || clouds_suppressed {
                    (None, None, None, None, None)
                } else {
                    let clouds = self
                        .shader_runtime
                        .as_ref()
                        .expect("shader runtime remains installed while resolving gbuffers_clouds")
                        .prepared_lowered_cloud_source_program()?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "selected source frame has cloud work but no lowered gbuffers_clouds program",
                            )
                        })?;
                    if clouds.shader_pack_generation != shader_pack_generation {
                        return Err(GalError::invalid_argument(
                            "gbuffers_clouds program generation does not match the named terrain target generation",
                        ));
                    }
                    let program = clouds.material_stream_program();
                    let targets = self
                        .stage_cloud_source_color_pass_targets(
                            gal,
                            world_generation,
                            graph_generation,
                            extent,
                            &program,
                            &color_targets,
                            depth_texture,
                            depth_view,
                            clear_values,
                        )?
                        .clone();
                    let resources = self.stage_candidate_source_resources_for_cloud_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &clouds,
                        &color_targets,
                    )?;
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    (
                        Some(program),
                        Some(targets),
                        Some(resources),
                        Some(formats),
                        Some(clouds.blend),
                    )
                };

            let line_batches = source_line_batches(frame)?;
            let (line_program, line_targets, line_resources, line_formats) =
                if line_batches.is_empty() {
                    (None, None, None, None)
                } else {
                    let scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "selected source block outline has no source program scope",
                            )
                        })?;
                    let program = self.line_source_program(shader_pack_generation, scope)?;
                    let targets = self
                        .stage_source_material_color_pass_targets(
                            gal,
                            world_generation,
                            graph_generation,
                            extent,
                            &program,
                            &color_targets,
                            depth_texture,
                            depth_view,
                            clear_values,
                            TerrainSourceColorPassPhase::Lines,
                            "lines",
                        )?
                        .clone();
                    let resources = self
                        .stage_candidate_source_resources_for_textured_material_program(
                            gal,
                            world_generation,
                            frame.frame_id,
                            &program,
                            &color_targets,
                        )?;
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    (Some(program), Some(targets), Some(resources), Some(formats))
                };

            let damaged_block_batches = self.source_damaged_block_batches(frame)?;
            let (damaged_program, damaged_targets, damaged_resources, damaged_formats) =
                if damaged_block_batches.is_empty() {
                    (None, None, None, None)
                } else {
                    let scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "selected source block-breaking progress has no source program scope",
                            )
                        })?;
                    let program = self.damaged_block_source_program(shader_pack_generation, scope)?;
                    let targets = self
                        .stage_source_material_color_pass_targets(
                            gal,
                            world_generation,
                            graph_generation,
                            extent,
                            &program,
                            &color_targets,
                            depth_texture,
                            depth_view,
                            clear_values,
                            TerrainSourceColorPassPhase::DamagedBlock,
                            "damagedblock",
                        )?
                        .clone();
                    let resources = self
                        .stage_candidate_source_resources_for_textured_material_program(
                            gal,
                            world_generation,
                            frame.frame_id,
                            &program,
                            &color_targets,
                        )?;
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    (Some(program), Some(targets), Some(resources), Some(formats))
                };

            let has_entity_meshes = frame.mesh_instances.iter().any(|instance| {
                instance.stratum == WORLD_STRATUM_ENTITY_MESH
                    && !self.is_crumbling_mesh_instance(instance)
            });
            let (entity_program, entity_targets, entity_resources, entity_formats, entity_frames) =
                if has_entity_meshes {
                    let program = self
                    .shader_runtime
                    .as_ref()
                    .expect("shader runtime remains installed while resolving gbuffers_entities")
                    .prepared_lowered_entity_source_program()?
                    .ok_or_else(|| {
                        GalError::unsupported_feature(
                            "selected source frame has entity meshes but no lowered gbuffers_entities program",
                        )
                    })?;
                    if program.shader_pack_generation != shader_pack_generation {
                        return Err(GalError::invalid_argument(
                            "gbuffers_entities program generation does not match the named terrain target generation",
                        ));
                    }
                    let targets = self
                        .stage_entity_source_color_pass_targets(
                            gal,
                            world_generation,
                            graph_generation,
                            extent,
                            &program,
                            &color_targets,
                            depth_texture,
                            depth_view,
                            clear_values,
                        )?
                        .clone();
                    let resources = self.stage_candidate_source_resources_for_entity_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &program,
                        &color_targets,
                    )?;
                    let frames = self.prepare_source_entity_frames(&program, frame)?;
                    if frames.is_empty() {
                        return Err(GalError::invalid_argument(
                            "selected source frame advertised entity meshes but entity preparation emitted no semantic sections",
                        ));
                    }
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    (
                        Some(program),
                        Some(targets),
                        Some(resources),
                        Some(formats),
                        frames,
                    )
                } else {
                    (None, None, None, None, Vec::new())
                };

            let has_first_person_meshes = !frame.first_person_mesh_instances.is_empty();
            let (hand_program, hand_targets, hand_resources, hand_formats, hand_frames) =
                if has_first_person_meshes {
                    let program = self
                        .shader_runtime
                        .as_ref()
                        .expect("shader runtime remains installed while resolving gbuffers_hand")
                        .prepared_lowered_hand_source_program()?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "selected source frame has first-person meshes but no lowered gbuffers_hand program",
                            )
                        })?;
                    if program.shader_pack_generation != shader_pack_generation {
                        return Err(GalError::invalid_argument(
                            "gbuffers_hand program generation does not match the named terrain target generation",
                        ));
                    }
                    let frames = self.prepare_source_hand_frames(&program, frame)?;
                    if frames.is_empty() {
                        return Err(GalError::invalid_argument(
                            "selected source frame advertised first-person meshes but hand preparation emitted no semantic sections",
                        ));
                    }
                    let depth_format = if frames.iter().any(|prepared| {
                        matches!(
                            prepared.material_mode,
                            WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                                | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
                        )
                    }) {
                        TextureFormat::Depth24Stencil8
                    } else {
                        TextureFormat::Depth32Float
                    };
                    let targets = self
                        .stage_hand_source_color_pass_targets(
                            gal,
                            world_generation,
                            graph_generation,
                            extent,
                            &program,
                            &color_targets,
                            clear_values,
                            depth_format,
                        )?
                        .clone();
                    let resources = self.stage_candidate_source_resources_for_hand_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &program,
                        &color_targets,
                    )?;
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    (
                        Some(program),
                        Some(targets),
                        Some(resources),
                        Some(formats),
                        frames,
                    )
                } else {
                    (None, None, None, None, Vec::new())
                };

            let base_uniform_frame = self.source_uniform_frame_for_owned_resources(frame)?;
            let texture_transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
            // Entity meshes have an independent source-program writer below.
            // Keep the terrain stream reservation and terrain draw preparation
            // scoped to the terrain strata so a combined frame never attempts
            // to interpret entity sections as terrain batches.
            let terrain_batches = batches
                .iter()
                .filter(|batch| is_source_terrain_mesh_stratum(batch.key.stratum))
                .collect::<Vec<_>>();
            let required_stream_bytes = terrain_batches.iter().try_fold(0_u64, |total, batch| {
                let program = match batch.key.material_mode {
                    WORLD_MATERIAL_MODE_OPAQUE => &programs.opaque,
                    WORLD_MATERIAL_MODE_CUTOUT => &programs.cutout,
                    WORLD_MATERIAL_MODE_TRANSLUCENT => translucent_program.as_ref().ok_or_else(|| {
                        GalError::unsupported_feature(
                            "named source terrain frame has translucent batches without an admitted translucent program",
                        )
                    })?,
                    mode => {
                        return Err(GalError::unsupported_feature(format!(
                            "named source terrain preparation rejects material mode {mode}",
                        )));
                    }
                };
                let instance_count = u64::try_from(batch.indices.len()).map_err(|_| {
                    GalError::invalid_argument(
                        "named source terrain batch instance count exceeds u64",
                    )
                })?;
                let color_bytes =
                    Self::source_terrain_frame_stream_payload_bytes(program, instance_count)?;
                let shadow_bytes = (source_shadow_required_for_material_mode(batch.key.material_mode)
                    && (batch.key.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT
                        || render_translucent_shadows))
                .then(|| {
                    Self::source_terrain_frame_stream_payload_bytes(
                        &programs.shadow,
                        instance_count,
                    )
                })
                .transpose()?
                .unwrap_or(0);
                total
                    .checked_add(color_bytes)
                    .and_then(|total| total.checked_add(shadow_bytes))
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "named source terrain frame stream reservation overflows",
                        )
                    })
            })?;
            let shadow_only_stream_bytes = selected_shadow_batches.iter().try_fold(0_u64, |total, batch| {
                if !is_source_terrain_mesh_stratum(batch.key.stratum) || !batch.key.g_buffer {
                    return Err(GalError::unsupported_feature(
                        "shadow-only source batches require copied G-buffer terrain",
                    ));
                }
                if !source_shadow_required_for_material_mode(batch.key.material_mode) {
                    return Err(GalError::unsupported_feature(
                        "shadow-only source batch has no admitted terrain shadow material",
                    ));
                }
                if batch.key.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
                    && !render_translucent_shadows
                {
                    return Ok(total);
                }
                let count = u64::try_from(batch.indices.len()).map_err(|_| {
                    GalError::invalid_argument("shadow-only batch instance count exceeds u64")
                })?;
                let bytes = Self::source_terrain_frame_stream_payload_bytes(&programs.shadow, count)?;
                total.checked_add(bytes).ok_or_else(|| {
                    GalError::invalid_argument("shadow-only source stream reservation overflows")
                })
            })?;
            let textured_material_stream_bytes = match textured_material_program.as_ref() {
                Some(program) => source_material_batch_stream_bytes(
                    program,
                    &textured_material_batches,
                    "textured material",
                )?,
                None => 0,
            };
            let weather_stream_bytes = match weather_program.as_ref() {
                Some(program) => {
                    source_material_batch_stream_bytes(program, &weather_batches, "weather")?
                }
                None => 0,
            };
            let cloud_stream_bytes = match cloud_program.as_ref() {
                Some(program) => {
                    source_material_batch_stream_bytes(program, &cloud_batches, "clouds")?
                }
                None => 0,
            };
            // Glint payloads share the frame stream; prepare them before the
            // reservation below so it covers them.
            let glint_scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?;
            let entity_glint_prepared = if frame.mesh_instances.iter().any(|instance| {
                instance.stratum == WORLD_STRATUM_ENTITY_MESH && instance.item_foil.is_some()
            }) {
                let scope = glint_scope.ok_or_else(|| {
                    GalError::unsupported_feature("selected source glint has no source program scope")
                })?;
                let program = self.entity_glint_source_program(shader_pack_generation, scope)?;
                let frames = self.prepare_source_entity_glint_frames(&program, frame)?;
                (!frames.is_empty()).then_some((program, frames))
            } else {
                None
            };
            let hand_glint_prepared = if hand_program.is_some()
                && frame
                    .first_person_mesh_instances
                    .iter()
                    .any(|instance| instance.item_foil.is_some())
            {
                let scope = glint_scope.ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source hand glint has no source program scope",
                    )
                })?;
                let program = self.hand_glint_source_program(shader_pack_generation, scope)?;
                let frames = self.prepare_source_hand_glint_frames(&program, frame)?;
                (!frames.is_empty()).then_some((program, frames))
            } else {
                None
            };
            let mut glint_stream_bytes = 0_u64;
            if let Some((program, frames)) = entity_glint_prepared.as_ref() {
                for prepared in frames {
                    let instances = u64::try_from(
                        prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
                    )
                    .map_err(|_| GalError::invalid_argument("glint instance count exceeds u64"))?;
                    glint_stream_bytes = glint_stream_bytes
                        .checked_add(Self::source_entity_frame_stream_payload_bytes(program, instances)?)
                        .ok_or_else(|| GalError::invalid_argument("glint stream reservation overflows"))?;
                }
            }
            if let Some((program, frames)) = hand_glint_prepared.as_ref() {
                for prepared in frames {
                    let instances = u64::try_from(
                        prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
                    )
                    .map_err(|_| GalError::invalid_argument("glint instance count exceeds u64"))?;
                    glint_stream_bytes = glint_stream_bytes
                        .checked_add(Self::source_hand_frame_stream_payload_bytes(program, instances)?)
                        .ok_or_else(|| GalError::invalid_argument("glint stream reservation overflows"))?;
                }
            }
            let entity_stream_bytes = match entity_program.as_ref() {
                Some(program) => entity_frames.iter().try_fold(0_u64, |total, prepared| {
                    let instance_count = u64::try_from(
                        prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
                    )
                    .map_err(|_| {
                        GalError::invalid_argument("entity source instance count exceeds u64")
                    })?;
                    let payload =
                        Self::source_entity_frame_stream_payload_bytes(program, instance_count)?;
                    total.checked_add(payload).ok_or_else(|| {
                        GalError::invalid_argument(
                            "entity source frame stream reservation overflows",
                        )
                    })
                })?,
                None => 0,
            };
            let damaged_block_stream_bytes = match damaged_program.as_ref() {
                Some(program) => damaged_block_batches.iter().try_fold(0_u64, |total, batch| {
                    u64::try_from(batch.primitives.len())
                        .ok()
                        .and_then(|count| count.checked_mul(4))
                        .and_then(|count| {
                            count.checked_mul(u64::from(program.execution_interface.vertex_stride))
                        })
                        .and_then(|vertices| {
                            total
                                .checked_add(u64::from(
                                    program.execution_interface.legacy_transform_bytes,
                                ))?
                                .checked_add(u64::from(
                                    program.execution_interface.scalar_uniform_bytes,
                                ))?
                                .checked_add(vertices)?
                                .checked_add(3 * WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64)
                        })
                        .ok_or_else(|| {
                            GalError::invalid_argument(
                                "damagedblock source frame stream reservation overflows",
                            )
                        })
                })?,
                None => 0,
            };
            let line_stream_bytes = match line_program.as_ref() {
                Some(program) => line_batches.iter().try_fold(0_u64, |total, batch| {
                    let vertices = u64::try_from(batch.primitives.len())
                        .ok()
                        .and_then(|count| count.checked_mul(4))
                        .and_then(|count| {
                            count.checked_mul(u64::from(program.execution_interface.vertex_stride))
                        });
                    vertices
                        .and_then(|vertices| {
                            total
                                .checked_add(u64::from(
                                    program.execution_interface.legacy_transform_bytes,
                                ))?
                                .checked_add(u64::from(
                                    program.execution_interface.scalar_uniform_bytes,
                                ))?
                                .checked_add(vertices)?
                                .checked_add(3 * WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64)
                        })
                        .ok_or_else(|| {
                            GalError::invalid_argument(
                                "line source frame stream reservation overflows",
                            )
                        })
                })?,
                None => 0,
            };
            let hand_stream_bytes = match hand_program.as_ref() {
                Some(program) => hand_frames.iter().try_fold(0_u64, |total, prepared| {
                    let instance_count = u64::try_from(
                        prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
                    )
                    .map_err(|_| {
                        GalError::invalid_argument("hand source instance count exceeds u64")
                    })?;
                    let payload =
                        Self::source_hand_frame_stream_payload_bytes(program, instance_count)?;
                    total.checked_add(payload).ok_or_else(|| {
                        GalError::invalid_argument("hand source frame stream reservation overflows")
                    })
                })?,
                None => 0,
            };
            let entity_shadow_stream_bytes = match entity_shadow_program.as_ref() {
                Some(program) => entity_shadow_frames.iter().try_fold(0_u64, |total, prepared| {
                    let instance_count = u64::try_from(
                        prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
                    )
                    .map_err(|_| {
                        GalError::invalid_argument("entity shadow instance count exceeds u64")
                    })?;
                    let payload =
                        Self::source_entity_frame_stream_payload_bytes(program, instance_count)?;
                    total.checked_add(payload).ok_or_else(|| {
                        GalError::invalid_argument(
                            "entity shadow frame stream reservation overflows",
                        )
                    })
                })?,
                None => 0,
            };
            let required_stream_bytes = required_stream_bytes
                .checked_add(entity_shadow_stream_bytes)
                .ok_or_else(|| {
                    GalError::invalid_argument("entity shadow stream reservation overflows")
                })?;
            let required_stream_bytes = required_stream_bytes
                .checked_add(shadow_only_stream_bytes)
                .and_then(|bytes| bytes.checked_add(textured_material_stream_bytes))
                .and_then(|bytes| bytes.checked_add(weather_stream_bytes))
                .and_then(|bytes| bytes.checked_add(cloud_stream_bytes))
                .and_then(|bytes| bytes.checked_add(entity_stream_bytes))
                .and_then(|bytes| bytes.checked_add(hand_stream_bytes))
                .and_then(|bytes| bytes.checked_add(line_stream_bytes))
                .and_then(|bytes| bytes.checked_add(damaged_block_stream_bytes))
                .and_then(|bytes| bytes.checked_add(glint_stream_bytes))
                .ok_or_else(|| {
                    GalError::invalid_argument("combined source frame stream reservation overflows")
                })?;
            self.reserve_source_terrain_frame_stream_capacity(
                gal,
                frame.frame_id,
                required_stream_bytes,
            )?;
            // One indirect command per selected section at most.
            let multidraw_commands = terrain_batches
                .iter()
                .map(|batch| batch.key.mesh_key)
                .chain(selected_shadow_batches.iter().map(|batch| batch.key.mesh_key))
                .map(|mesh_key| {
                    self.mesh_assets
                        .get(&mesh_key)
                        .map_or(1, |asset| asset.sections.len().max(1)) as u64
                })
                .sum::<u64>();
            self.reserve_source_terrain_multidraw_commands(gal, frame.frame_id, multidraw_commands)?;
            let mut draws = Vec::new();
            // Each pass packs one immutable uniform block for this frame.
            // Batches keep independent model/color records while sharing it.
            let mut shadow_uniform_frame = base_uniform_frame.clone();
            shadow_uniform_frame.render_stage = Some(self.source_shadow_render_stage()?);
            let mut camera_uniforms: Vec<(u32, PreparedSourceTerrainUniforms<'_>)> = Vec::new();
            let mut shadow_uniforms = None;
            let mut scope_programs = vec![&programs.opaque, &programs.cutout, &programs.shadow];
            scope_programs.extend(translucent_program.as_ref());
            self.open_source_terrain_batch_scope(frame.frame_id, &scope_programs)?;
            let source_draw_trace = matches!(
                crate::core::environment::var("MATTMC_RUST_SOURCE_DRAW_TRACE").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
            let mut transform_probes = Vec::new();
            for batch in terrain_batches {
                if !batch.key.g_buffer {
                    return Err(GalError::unsupported_feature(
                        "named source terrain preparation accepts only source-compatible G-buffer mesh batches",
                    ));
                }
                let (program, resources, color_formats, shadow_required) = match batch.key.material_mode {
                    WORLD_MATERIAL_MODE_OPAQUE => {
                        (&programs.opaque, &opaque_resources, &opaque_formats, true)
                    }
                    WORLD_MATERIAL_MODE_CUTOUT => {
                        (&programs.cutout, &cutout_resources, &opaque_formats, true)
                    }
                    WORLD_MATERIAL_MODE_TRANSLUCENT => (
                        translucent_program.as_ref().ok_or_else(|| {
                            GalError::unsupported_feature(
                                "named source terrain frame has translucent batches without an admitted translucent program",
                            )
                        })?,
                        translucent_resources.as_ref().ok_or_else(|| {
                            GalError::backend(
                                "named source terrain frame lost its translucent resource snapshot",
                            )
                        })?,
                        translucent_formats.as_ref().ok_or_else(|| {
                            GalError::backend(
                                "named source terrain frame lost its translucent named output schema",
                            )
                        })?,
                        render_translucent_shadows,
                    ),
                    mode => {
                        return Err(GalError::unsupported_feature(format!(
                            "named source terrain preparation rejects material mode {mode}",
                        )));
                }
            };
                if source_draw_trace {
                    crate::core::console::stderr(format_args!(
                        "[MattMC source-draw-trace] frame={} stratum={} material_mode={} program={} instances={} indices={} color_formats={:?}",
                        frame.frame_id,
                        batch.key.stratum,
                        batch.key.material_mode,
                        program.identity.as_str(),
                        batch.indices.len(),
                        batch.index_count,
                        color_formats,
                    ));
                }
                let instances = batch
                    .indices
                    .iter()
                    .map(|&index| {
                        frame.mesh_instances.get(index).map(|instance| (instance.transform, instance.color_argb)).ok_or_else(
                            || {
                                GalError::invalid_argument(
                                    "named source terrain batch references a missing semantic instance",
                                )
                            },
                        )
                    })
                    .collect::<GalResult<Vec<_>>>()?;
                let uniforms = match camera_uniforms
                    .iter()
                    .find(|(mode, _)| *mode == batch.key.material_mode)
                {
                    Some((_, packed)) => packed,
                    None => {
                        let mut uniform_frame = base_uniform_frame.clone();
                        uniform_frame.render_stage = Some(
                            self.source_render_stage_for_material_mode(batch.key.material_mode)?,
                        );
                        let packed = self.prepare_source_terrain_uniforms(
                            program, frame.frame_id, &texture_transforms, &uniform_frame,
                        )?;
                        camera_uniforms.push((batch.key.material_mode, packed));
                        &camera_uniforms.last().expect("just pushed").1
                    }
                };
                let prepared = self.prepare_source_terrain_frame_for_mesh_range_using_uniforms(
                    program,
                    frame.frame_id,
                    batch.key.mesh_key,
                    batch.key.mesh_generation,
                    batch.index_offset,
                    batch.index_count,
                    &instances,
                    SourceTerrainFrameUniforms::Packed(uniforms),
                )?;
                collect_selected_source_terrain_transform_probes(
                    &mut transform_probes,
                    frame,
                    batch,
                    &prepared,
                )?;
                let mut terrain_draws = self
                    .prepare_lowered_source_terrain_draws_for_color_formats(
                        gal,
                        program,
                        &prepared,
                        resources,
                        batch.key.material_mode,
                        batch.key.cull_policy,
                        batch.key.winding,
                        color_formats.clone(),
                    )?;
                // Source lowering is shared across semantic mesh families;
                // retain the batch's producer stratum on every lowered draw
                // so moving-block coverage cannot be misclassified as an
                // entity submission.
                for terrain_draw in &mut terrain_draws {
                    terrain_draw.stratum = batch.key.stratum;
                }
                if shadow_required {
                    if shadow_uniforms.is_none() {
                        shadow_uniforms = Some(self.prepare_source_terrain_uniforms(
                            &programs.shadow, frame.frame_id, &texture_transforms, &shadow_uniform_frame,
                        )?);
                    }
                    let shadow_prepared = self.prepare_source_terrain_frame_for_mesh_range_using_uniforms(
                        &programs.shadow,
                        frame.frame_id,
                        batch.key.mesh_key,
                        batch.key.mesh_generation,
                        batch.index_offset,
                        batch.index_count,
                        &instances,
                        SourceTerrainFrameUniforms::Packed(shadow_uniforms.as_ref().expect("shadow uniforms prepared")),
                    )?;
                    let shadow_draws = self.prepare_lowered_source_shadow_draws(
                        gal,
                        &programs.shadow,
                        &shadow_prepared,
                        shadow_resources.as_ref().ok_or_else(|| {
                            GalError::backend(
                                "named source terrain frame lost its opaque/cutout shadow resource snapshot",
                            )
                        })?,
                        batch.key.material_mode,
                        batch.key.cull_policy,
                        batch.key.winding,
                        shadow_alpha_cutoff,
                    )?;
                    if terrain_draws.len() != shadow_draws.len() {
                        return Err(GalError::invalid_argument(
                            "named source terrain and shadow programs selected different mesh section counts",
                        ));
                    }
                    for (terrain_draw, shadow_draw) in terrain_draws.iter_mut().zip(shadow_draws) {
                        terrain_draw.shadow = Some(shadow_draw);
                    }
                } else {
                    for terrain_draw in &mut terrain_draws {
                        terrain_draw.shadow_participation = TerrainShadowParticipation::Unavailable;
                    }
                }
                draws.extend(terrain_draws);
            }
            let mut shadow_only_draws = Vec::new();
            for batch in selected_shadow_batches {
                if batch.key.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
                    && !render_translucent_shadows
                {
                    continue;
                }
                let instances = batch
                    .indices
                    .iter()
                    .map(|&index| {
                        frame.mesh_instances.get(index).map(|instance| {
                            (instance.transform, instance.color_argb)
                        }).ok_or_else(|| {
                            GalError::invalid_argument(
                                "shadow-only terrain batch references a missing semantic instance",
                            )
                        })
                    })
                    .collect::<GalResult<Vec<_>>>()?;
                if shadow_uniforms.is_none() {
                    shadow_uniforms = Some(self.prepare_source_terrain_uniforms(
                        &programs.shadow, frame.frame_id, &texture_transforms, &shadow_uniform_frame,
                    )?);
                }
                let prepared = self.prepare_source_terrain_frame_for_mesh_range_using_uniforms(
                    &programs.shadow,
                    frame.frame_id,
                    batch.key.mesh_key,
                    batch.key.mesh_generation,
                    batch.index_offset,
                    batch.index_count,
                    &instances,
                    SourceTerrainFrameUniforms::Packed(shadow_uniforms.as_ref().expect("shadow uniforms prepared")),
                )?;
                shadow_only_draws.extend(self.prepare_lowered_source_shadow_only_draws(
                    gal,
                    &programs.shadow,
                    &prepared,
                    shadow_resources.as_ref().ok_or_else(|| {
                        GalError::backend("shadow-only terrain lost its source resource snapshot")
                    })?,
                    batch.key.material_mode,
                    batch.key.cull_policy,
                    batch.key.winding,
                    shadow_alpha_cutoff,
                )?);
            }
            self.source_terrain_batch_scope = None;
            self.write_selected_source_terrain_transform_receipt(frame, &transform_probes);
            let textured_material = match (
                textured_material_program.as_ref(),
                textured_material_targets,
                textured_material_resources.as_ref(),
                textured_material_formats.as_ref(),
            ) {
                (None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats)) => {
                    let texture_transforms =
                        self.source_texture_transforms_for_owned_resources()?;
                    let mut material_draws = Vec::with_capacity(textured_material_batches.len());
                    for batch in textured_material_batches {
                        let (batch_resources, local_texture, local_texture_extent) = self
                            .source_resources_for_textured_material_batch(
                                gal,
                                program,
                                resources,
                                batch,
                                frame.frame_id,
                            )?;
                        let mut uniform_frame = base_uniform_frame.clone();
                        uniform_frame.render_stage =
                            Some(self.source_render_stage_for_material_mode(batch.material_mode)?);
                        uniform_frame.block_entity_id = Some(batch.block_entity_id);
                        if let Some(extent) = local_texture_extent {
                            uniform_frame.material_atlas_size = Some(extent);
                        }
                        let prepared = self.prepare_textured_material_source_frame_for_indices(
                            program,
                            frame,
                            batch.indices(),
                            &texture_transforms,
                            &uniform_frame,
                        )?;
                        material_draws.push(self.prepare_lowered_textured_material_source_draw(
                            gal,
                            program,
                            &prepared,
                            &batch_resources,
                            local_texture,
                            batch.material_mode,
                            batch.depth_policy,
                            batch.cull_policy,
                            batch.winding,
                            formats.clone(),
                        )?);
                    }
                    Some(PreparedNamedSourceTexturedMaterialFramePlan {
                        targets,
                        draws: material_draws,
                    })
                }
                _ => {
                    return Err(GalError::backend(
                        "textured material source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let weather = match (
                weather_program.as_ref(),
                weather_targets,
                weather_resources.as_ref(),
                weather_formats.as_ref(),
            ) {
                (None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats)) => {
                    let texture_transforms =
                        self.source_texture_transforms_for_owned_resources()?;
                    let mut weather_draws = Vec::with_capacity(weather_batches.len());
                    for batch in weather_batches {
                        let (batch_resources, local_texture, local_texture_extent) = self
                            .source_resources_for_textured_material_batch(
                                gal,
                                program,
                                resources,
                                batch,
                                frame.frame_id,
                            )?;
                        let mut uniform_frame = base_uniform_frame.clone();
                        uniform_frame.render_stage = Some(self.source_weather_render_stage()?);
                        if let Some(extent) = local_texture_extent {
                            uniform_frame.material_atlas_size = Some(extent);
                        }
                        let prepared = self.prepare_textured_material_source_frame_for_indices(
                            program,
                            frame,
                            batch.indices(),
                            &texture_transforms,
                            &uniform_frame,
                        )?;
                        weather_draws.push(self.prepare_lowered_weather_source_draw(
                            gal,
                            program,
                            &prepared,
                            &batch_resources,
                            local_texture,
                            batch.material_mode,
                            batch.depth_policy,
                            batch.cull_policy,
                            batch.winding,
                            formats.clone(),
                        )?);
                    }
                    Some(PreparedNamedSourceWeatherFramePlan {
                        targets,
                        draws: weather_draws,
                    })
                }
                _ => {
                    return Err(GalError::backend(
                        "weather source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let clouds = match (
                cloud_program.as_ref(),
                cloud_targets,
                cloud_resources.as_ref(),
                cloud_formats.as_ref(),
                cloud_blend,
            ) {
                (None, None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats), Some(blend)) => {
                    self.write_cloud_pipeline_receipt(
                        frame.frame_id,
                        program,
                        &cloud_batches,
                        blend,
                        targets.color_attachments.len(),
                    );
                    let texture_transforms =
                        self.source_texture_transforms_for_owned_resources()?;
                    let mut cloud_draws = Vec::with_capacity(cloud_batches.len());
                    for batch in cloud_batches {
                        let (batch_resources, local_texture, local_texture_extent) = self
                            .source_resources_for_textured_material_batch(
                                gal,
                                program,
                                resources,
                                batch,
                                frame.frame_id,
                            )?;
                        let mut uniform_frame = base_uniform_frame.clone();
                        uniform_frame.render_stage = Some(self.source_cloud_render_stage()?);
                        if let Some(extent) = local_texture_extent {
                            uniform_frame.material_atlas_size = Some(extent);
                        }
                        let prepared = self.prepare_textured_material_source_frame_for_indices(
                            program,
                            frame,
                            batch.indices(),
                            &texture_transforms,
                            &uniform_frame,
                        )?;
                        cloud_draws.push(self.prepare_lowered_cloud_source_draw(
                            gal,
                            program,
                            &prepared,
                            &batch_resources,
                            local_texture,
                            batch.material_mode,
                            batch.depth_policy,
                            batch.cull_policy,
                            batch.winding,
                            formats.clone(),
                            blend,
                        )?);
                    }
                    Some(PreparedNamedSourceCloudFramePlan {
                        targets,
                        draws: cloud_draws,
                    })
                }
                _ => {
                    return Err(GalError::backend(
                        "cloud source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let lines = match (
                line_program.as_ref(),
                line_targets,
                line_resources.as_ref(),
                line_formats.as_ref(),
            ) {
                (None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats)) => {
                    let texture_transforms =
                        self.source_texture_transforms_for_owned_resources()?;
                    let mut uniform_frame = base_uniform_frame.clone();
                    uniform_frame.render_stage = self.source_line_render_stage()?;
                    let legacy_texture_transforms =
                        program.pack_legacy_texture_transforms(&texture_transforms)?;
                    let scalar_uniforms = program.pack_scalar_uniforms(&uniform_frame)?;
                    let mut opaque_draws = Vec::new();
                    let mut translucent_draws = Vec::new();
                    for batch in line_batches {
                        let vertex_stream = pack_textured_material_source_primitives(&batch.primitives)?;
                        let prepared = PreparedTexturedMaterialSourceFrame {
                            frame_id: frame.frame_id,
                            primitives: batch.primitives,
                            vertex_stream,
                            legacy_texture_transforms: legacy_texture_transforms.clone(),
                            scalar_uniforms: scalar_uniforms.clone(),
                        };
                        let draw = self.prepare_lowered_source_material_draw(
                            gal,
                            program,
                            &prepared,
                            resources,
                            None,
                            WORLD_MATERIAL_MODE_TRANSLUCENT,
                            batch.depth_policy,
                            WORLD_CULL_NONE,
                            WORLD_WINDING_CCW,
                            formats.clone(),
                            SourceMaterialWriterKind::Lines,
                            None,
                        )?;
                        if batch.translucent_target {
                            translucent_draws.push(draw);
                        } else {
                            opaque_draws.push(draw);
                        }
                    }
                    self.last_source_line_execution = Some((
                        frame.frame_id,
                        program.identity.as_str().to_string(),
                        frame.segments.len(),
                        opaque_draws.len(),
                        translucent_draws.len(),
                    ));
                    Some(PreparedNamedSourceLineFramePlan {
                        targets,
                        opaque_draws,
                        translucent_draws,
                    })
                }
                _ => {
                    return Err(GalError::backend(
                        "line source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let damaged_block = match (
                damaged_program.as_ref(),
                damaged_targets,
                damaged_resources.as_ref(),
                damaged_formats.as_ref(),
            ) {
                (None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats)) => {
                    let texture_transforms =
                        self.source_texture_transforms_for_owned_resources()?;
                    let legacy_texture_transforms =
                        program.pack_legacy_texture_transforms(&texture_transforms)?;
                    let scalar_uniforms = program.pack_scalar_uniforms(&base_uniform_frame)?;
                    let mut draws = Vec::with_capacity(damaged_block_batches.len());
                    for batch in damaged_block_batches {
                        let (batch_resources, local_texture, _) = self
                            .source_resources_with_local_material_texture(
                                gal,
                                program.shader_pack_generation,
                                resources,
                                batch.texture_id,
                                frame.frame_id,
                            )?;
                        let vertex_stream = pack_textured_material_source_primitives(&batch.primitives)?;
                        let prepared = PreparedTexturedMaterialSourceFrame {
                            frame_id: frame.frame_id,
                            primitives: batch.primitives,
                            vertex_stream,
                            legacy_texture_transforms: legacy_texture_transforms.clone(),
                            scalar_uniforms: scalar_uniforms.clone(),
                        };
                        draws.push(self.prepare_lowered_source_material_draw(
                            gal,
                            program,
                            &prepared,
                            &batch_resources,
                            Some(local_texture),
                            WORLD_MATERIAL_MODE_TRANSLUCENT,
                            // Vanilla crumbling: depth test, no depth write.
                            WORLD_DEPTH_POLICY_TEST_NO_WRITE,
                            batch.cull_policy,
                            WORLD_WINDING_CCW,
                            formats.clone(),
                            SourceMaterialWriterKind::DamagedBlock,
                            None,
                        )?);
                    }
                    self.absorb_pending_source_material_texture_uploads(frame.frame_id)?;
                    Some(PreparedNamedSourceDamagedBlockFramePlan { targets, draws })
                }
                _ => {
                    return Err(GalError::backend(
                        "damagedblock source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let entities = match (
                entity_program.as_ref(),
                entity_targets,
                entity_resources.as_ref(),
                entity_formats.as_ref(),
            ) {
                (None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats)) => {
                    let mut entity_draws = Vec::with_capacity(entity_frames.len());
                    for prepared in &entity_frames {
                        entity_draws.push(self.prepare_lowered_source_entity_draw(
                            gal,
                            program,
                            prepared,
                            resources,
                            formats.clone(),
                        )?);
                    }
                    Some(PreparedNamedSourceEntityFramePlan {
                        targets,
                        draws: entity_draws,
                    })
                }
                _ => {
                    return Err(GalError::backend(
                        "entity source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let mut entity_shadow_draws = Vec::with_capacity(entity_shadow_frames.len());
            if let (Some(program), Some(resources)) =
                (entity_shadow_program.as_ref(), entity_shadow_resources.as_ref())
            {
                for prepared in &entity_shadow_frames {
                    entity_shadow_draws.push(self.prepare_lowered_local_source_draw(
                        gal,
                        program,
                        prepared.frame_id,
                        &prepared.mesh,
                        prepared.section_index,
                        prepared.texture_id,
                        prepared.material_mode,
                        WORLD_DEPTH_POLICY_TEST_WRITE,
                        prepared.cull_policy,
                        prepared.winding,
                        TextureFormat::Depth32Float,
                        &prepared.legacy_texture_transforms,
                        &prepared.scalar_uniforms,
                        &prepared.instance_transforms,
                        resources,
                        vec![SHADER_G_BUFFER_COLOR_FORMAT; 2],
                        Some(shadow_alpha_cutoff),
                    )?);
                }
            }
            let hands = match (
                hand_program.as_ref(),
                hand_targets,
                hand_resources.as_ref(),
                hand_formats.as_ref(),
            ) {
                (None, None, None, None) => None,
                (Some(program), Some(targets), Some(resources), Some(formats)) => {
                    let mut hand_draws = Vec::with_capacity(hand_frames.len());
                    let hand_depth_format = if hand_frames.iter().any(|prepared| {
                        matches!(
                            prepared.material_mode,
                            WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                                | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
                        )
                    }) {
                        TextureFormat::Depth24Stencil8
                    } else {
                        TextureFormat::Depth32Float
                    };
                    let mut translucent_hand_draws = Vec::new();
                    for prepared in &hand_frames {
                        let draw = self.prepare_lowered_source_hand_draw(
                            gal,
                            program,
                            prepared,
                            resources,
                            formats.clone(),
                            hand_depth_format,
                        )?;
                        if frame.first_person.hand_is_translucent(prepared.hand) {
                            translucent_hand_draws.push(draw);
                        } else {
                            hand_draws.push(draw);
                        }
                    }
                    // Iris draws the translucent hand with `gbuffers_hand_water`,
                    // falling back to `gbuffers_hand` only when the pack has no
                    // hand_water program. Rust lowers only the latter, so a pack
                    // that ships hand_water stays unadmitted for this frame.
                    if !translucent_hand_draws.is_empty()
                        && self.shader_pack_sources.active().is_some_and(|source| {
                            [
                                "gbuffers_hand_water.fsh",
                                "world0/gbuffers_hand_water.fsh",
                                "world-1/gbuffers_hand_water.fsh",
                                "world1/gbuffers_hand_water.fsh",
                                "program/gbuffers_hand_water.glsl",
                            ]
                            .iter()
                            .any(|path| source.get(path).is_some())
                        })
                    {
                        return Err(GalError::unsupported_feature(
                            "translucent hand requires the pack's unlowered gbuffers_hand_water program",
                        ));
                    }
                    Some(PreparedNamedSourceHandFramePlan {
                        targets,
                        draws: hand_draws,
                        translucent_draws: translucent_hand_draws,
                        copies_world_depth: hand_depth_format == TextureFormat::Depth32Float,
                    })
                }
                _ => {
                    return Err(GalError::backend(
                        "hand source preparation retained an incomplete program/target/resource tuple",
                    ));
                }
            };
            let entity_glint = if let Some((program, frames)) = entity_glint_prepared {
                {
                    let color_attachments = self
                        .shader_runtime
                        .as_ref()
                        .ok_or_else(|| GalError::backend("shader runtime vanished before glint staging"))?
                        .resolve_entity_source_color_outputs(&program, &color_targets)?;
                    let targets = self
                        .stage_glint_source_color_pass_targets(
                            gal,
                            world_generation,
                            program.shader_pack_generation,
                            graph_generation,
                            extent,
                            color_attachments,
                            TerrainSourceColorPassPhase::EntityGlint,
                            depth_texture,
                            depth_view,
                            TextureFormat::Depth32Float,
                            clear_values,
                        )?
                        .clone();
                    let resources = self.stage_candidate_source_resources_for_entity_program(
                        gal,
                        world_generation,
                        frame.frame_id,
                        &program,
                        &color_targets,
                    )?;
                    let formats = targets
                        .color_attachments
                        .iter()
                        .map(|attachment| attachment.format)
                        .collect::<Vec<_>>();
                    let mut draws = Vec::with_capacity(frames.len());
                    for prepared in &frames {
                        draws.push(self.prepare_lowered_source_entity_draw(
                            gal,
                            &program,
                            prepared,
                            &resources,
                            formats.clone(),
                        )?);
                    }
                    Some(PreparedNamedSourceGlintFramePlan { targets, draws })
                }
            } else {
                None
            };
            let hand_glint_depth = hands.as_ref().map(|hands| {
                (
                    hands.targets.depth_texture,
                    hands.targets.depth_view,
                    if hands.copies_world_depth {
                        TextureFormat::Depth32Float
                    } else {
                        TextureFormat::Depth24Stencil8
                    },
                )
            });
            let hand_glint = match (hand_glint_depth, hand_glint_prepared) {
                (Some((hand_depth_texture, hand_depth_view, hand_depth_format)), Some((program, frames))) => {
                    {
                        let color_attachments = self
                            .shader_runtime
                            .as_ref()
                            .ok_or_else(|| {
                                GalError::backend("shader runtime vanished before hand glint staging")
                            })?
                            .resolve_hand_source_color_outputs(&program, &color_targets)?;
                        let targets = self
                            .stage_glint_source_color_pass_targets(
                                gal,
                                world_generation,
                                program.shader_pack_generation,
                                graph_generation,
                                extent,
                                color_attachments,
                                TerrainSourceColorPassPhase::HandGlint,
                                hand_depth_texture,
                                hand_depth_view,
                                hand_depth_format,
                                clear_values,
                            )?
                            .clone();
                        let resources = self.stage_candidate_source_resources_for_hand_program(
                            gal,
                            world_generation,
                            frame.frame_id,
                            &program,
                            &color_targets,
                        )?;
                        let formats = targets
                            .color_attachments
                            .iter()
                            .map(|attachment| attachment.format)
                            .collect::<Vec<_>>();
                        let mut draws = Vec::with_capacity(frames.len());
                        for prepared in &frames {
                            draws.push(self.prepare_lowered_source_hand_draw(
                                gal,
                                &program,
                                prepared,
                                &resources,
                                formats.clone(),
                                hand_depth_format,
                            )?);
                        }
                        Some(PreparedNamedSourceGlintFramePlan { targets, draws })
                    }
                }
                _ => None,
            };
            // Celestial and other source fullscreen consumers can request a
            // copied local material after the indexed writers have prepared
            // their draws. Fold those uploads into the same frame transaction
            // before taking it, preserving upload-before-draw ordering.
            self.absorb_pending_source_material_texture_uploads(frame.frame_id)?;
            let terrain = match self.take_source_terrain_frame_transaction(frame.frame_id) {
                Ok(transaction) => PreparedLoweredSourceTerrainFramePlan {
                    frame_id: frame.frame_id,
                    draws,
                    transaction: Some(transaction),
                },
                Err(_error) if draws.is_empty() => PreparedLoweredSourceTerrainFramePlan {
                    frame_id: frame.frame_id,
                    draws,
                    transaction: None,
                },
                Err(error) => return Err(error),
            };
            let mut bootstrap_operations = Vec::new();
            let color_transaction = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime remains installed while creating named source transaction")
                .begin_source_color_transaction(
                    gal,
                    &color_targets,
                    clear_values,
                    &mut bootstrap_operations,
                )?;
            Ok(PreparedNamedSourceTerrainFramePlan {
                terrain,
                shadow_only_draws,
                entity_shadow_draws,
                entities,
                hands,
                textured_material,
                weather,
                clouds,
                lines,
                damaged_block,
                entity_glint,
                hand_glint,
                color_targets,
                // Empty world frames still run source consumers of shadow
                // depth/colors. Clear and snapshot the owned targets before
                // those consumers; only caster preparation is conditional.
                shadow_targets: Some(shadow_targets),
                main_depth_history: None,
                targets: terrain_targets,
                translucent_targets,
                pre_terrain_sky: Vec::new(),
                color_transaction,
                bootstrap_operations,
            })
        })();
        self.source_terrain_batch_scope = None;
        if result.is_err() {
            self.discard_source_terrain_frame_transaction(gal, frame.frame_id);
            self.discard_source_color_submission(gal);
        }
        result
    }
}

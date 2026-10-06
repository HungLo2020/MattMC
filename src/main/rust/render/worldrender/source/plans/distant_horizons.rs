//! The named-source Distant Horizons frame plan, depth schedule and submission.

use super::*;

/// Exact Rust-owned depth views needed by one private source frame. This is
/// frontend/GAL state only; it does not expose Java, Iris, or native images.
#[derive(Clone, Copy)]
pub(crate) struct NamedSourceMainDepthInputs {
    pub(in crate::render::worldrender) targets: TerrainDepthHistoryTargets,
    pub(in crate::render::worldrender) main_depth_view: Handle,
    pub(in crate::render::worldrender) before_translucency_view: Handle,
    pub(in crate::render::worldrender) previous_view: Handle,
    pub(in crate::render::worldrender) sampler: Handle,
    pub(in crate::render::worldrender) graph_generation: u64,
}

/// The Distant Horizons opaque portion of one shared source frame. Geometry
/// and depth ownership remain specific to DH, but its color writer is joined
/// to the same pack target generation as ordinary terrain. This does not own
/// a route decision, presentation, or color-history completion.
pub(crate) struct PreparedNamedSourceDistantHorizonsFramePlan {
    pub(in crate::render::worldrender) submission: DistantHorizonsSourceTargetSubmission,
    pub(in crate::render::worldrender) target: lod::WorldLodPreparedSourceTarget,
    pub(in crate::render::worldrender) depth_targets: lod::WorldLodSourceTargets,
    pub(in crate::render::worldrender) draws: Vec<lod::WorldLodPreparedSourceDraw>,
    pub(in crate::render::worldrender) pack_resources: Handle,
    // These provenance-resolved ranges complement `draws`: complete ranges
    // replace their reduced-color counterpart, while partial ranges retain
    // only their unresolved reduced-color indices. Keeping both inside the
    // one source transaction prevents a diagnostic receipt from describing a
    // different frame than the selected shader program actually renders.
    pub(in crate::render::worldrender) exact_atlas_draws: Vec<(lod::WorldLodPreparedSourceDraw, Handle)>,
    /// One late selected-source DH material stream. The pack calls the source
    /// entry `dh_water`, but it is the generic DH translucent program: water
    /// takes a material branch within that program while ordinary transparent
    /// ranges retain their own semantic layer/order in the copied stream.
    pub(in crate::render::worldrender) translucent_draws: Vec<lod::WorldLodPreparedSourceDraw>,
    pub(in crate::render::worldrender) translucent_pack_resources: Option<Handle>,
    /// DH generic boxes drawn with the opaque `dh_terrain` program and DH's
    /// generic alpha blend, right after the opaque LODs.
    pub(in crate::render::worldrender) generic_draws: Vec<lod::WorldLodPreparedSourceDraw>,
    pub(in crate::render::worldrender) upload_operations: Vec<CommandOp>,
    /// Whether this frame's DH depth attachment was already cleared.
    pub(in crate::render::worldrender) depth_cleared: bool,
}

/// Keeps source-plan rejection tied to the semantic shader-pack discovery
/// result. Complementary names its generic late DH translucent program
/// `dh_water`; that source contains a material-id branch for water and also
/// defines the ordinary transparent path. The name never changes the copied
/// layer classification or ordering. This wording is diagnostic only: it
/// cannot select a Java fallback or alter the opaque source route.
pub(crate) fn describe_distant_horizons_translucent_source_status(
    status: Option<&DistantHorizonsTranslucentSourceCandidate>,
) -> String {
    match status {
        None => {
            "the selected shader-pack has no usable Distant Horizons source candidate".to_string()
        }
        Some(DistantHorizonsTranslucentSourceCandidate::Unavailable) => {
            "the selected shader-pack declares no late Distant Horizons translucent source pair"
                .to_string()
        }
        Some(DistantHorizonsTranslucentSourceCandidate::Discovered(contract)) => format!(
            "'{}' was discovered with {:?} blending, but its late Distant Horizons translucent source program is not admitted for this frame",
            contract.program_path, contract.translucent_blend,
        ),
        Some(DistantHorizonsTranslucentSourceCandidate::Rejected(reason)) => {
            format!(
                "the selected Distant Horizons translucent source contract was rejected: {reason}"
            )
        }
    }
}

pub(crate) struct NamedSourceDistantHorizonsSubmission {
    pub(in crate::render::worldrender) targets: DistantHorizonsSourceTargetSubmission,
}

/// One opaque DH section keeps the shared depth attachment writable until the
/// final section completes. The snapshot is a single semantic input to later
/// pack stages, not a per-section history buffer.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct DistantHorizonsOpaqueDepthSchedule {
    pub(in crate::render::worldrender) depth_before: TextureUsageState,
    pub(in crate::render::worldrender) depth_after: Option<TextureUsageState>,
    pub(in crate::render::worldrender) snapshot_after_draw: bool,
}

pub(crate) fn distant_horizons_opaque_depth_schedule(
    draw_index: usize,
    opaque_draw_count: usize,
) -> DistantHorizonsOpaqueDepthSchedule {
    debug_assert!(draw_index < opaque_draw_count);
    let snapshot_after_draw = draw_index + 1 == opaque_draw_count;
    DistantHorizonsOpaqueDepthSchedule {
        depth_before: if draw_index == 0 {
            TextureUsageState::Undefined
        } else {
            TextureUsageState::ShaderRead
        },
        depth_after: (!snapshot_after_draw).then_some(TextureUsageState::ShaderRead),
        snapshot_after_draw,
    }
}

impl PreparedNamedSourceDistantHorizonsFramePlan {
    /// Appends all opaque DH draws after normal terrain has populated the
    /// shared named colors. The caller retains the color transaction and must
    /// finish it only after every later source writer has been appended.
    /// DH opaque LODs (`dh_terrain`): drawn after the sky and right before
    /// vanilla opaque terrain (Frozen `prepareChunkRenders`), so vanilla paints
    /// over them and the deferred chain samples the completed DH depth.
    pub(crate) fn append_opaque(
        &mut self,
        color_transaction: &mut ShaderPackSourceColorFrameTransaction,
        fog_color: ClearColor,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        operations.extend(std::mem::take(&mut self.upload_operations));
        // The distinct DH depth snapshot represents the completed opaque
        // phase. Copying it after each section makes later source stages
        // observe an arbitrary prefix of the visible far terrain and also
        // reuses the snapshot destination before it is re-established as a
        // transfer target. Batch compatible opaque writers and snapshot once
        // after the complete opaque phase.
        let opaque_draw_count =
            self.draws.len() + self.exact_atlas_draws.len() + self.generic_draws.len();
        if opaque_draw_count != 0 {
            let mut opaque_draws = Vec::with_capacity(opaque_draw_count);
            opaque_draws.extend(
                self.draws.iter().copied().map(|draw| (draw, self.pack_resources)),
            );
            opaque_draws.extend(self.exact_atlas_draws.iter().copied());
            // DH's GenericObjectRenderer runs right after the opaque LODs.
            opaque_draws.extend(
                self.generic_draws.iter().copied().map(|draw| (draw, self.pack_resources)),
            );
            // DH clears its own depth buffer every frame. Loading the previous
            // frame's depth made this frame's identical LOD geometry fail its
            // depth test almost everywhere (only silhouette edges survived).
            lod::WorldLodSourcePassResources::append_opaque_batch(
                &self.target,
                &opaque_draws,
                fog_color,
                false,
                TextureUsageState::ShaderRead,
                TextureUsageState::Undefined,
                None,
                operations,
            )?;
            self.depth_cleared = true;
            self.depth_targets.append_opaque_depth_snapshot(operations);
            color_transaction.record_external_outputs(&[
                TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
            ])?;
        } else if !self.translucent_draws.is_empty() {
            self.depth_targets
                .append_empty_opaque_depth_snapshot(TextureUsageState::Undefined, operations)?;
        }
        Ok(())
    }

    /// DH translucent LODs (`dh_water`): drawn after the deferred chain and
    /// right before vanilla translucent terrain (DH's TRANSLUCENT layer hook).
    pub(crate) fn append_translucent(
        &mut self,
        color_transaction: &mut ShaderPackSourceColorFrameTransaction,
        fog_color: ClearColor,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.translucent_draws.is_empty() {
            return Ok(());
        }
        let translucent_pack_resources = self.translucent_pack_resources.ok_or_else(|| {
            GalError::backend(
                "prepared Distant Horizons translucent draws have no prepared source resource set",
            )
        })?;
        for draw in std::mem::take(&mut self.translucent_draws) {
            // Without opaque DH work this frame, the first translucent draw
            // starts from a cleared DH depth rather than last frame's.
            let depth_before = if self.depth_cleared {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            };
            self.depth_cleared = true;
            lod::WorldLodSourcePassResources::append_draw(
                &self.target,
                draw,
                translucent_pack_resources,
                fog_color,
                false,
                TextureUsageState::ShaderRead,
                depth_before,
                Some(TextureUsageState::ShaderRead),
                operations,
            )?;
        }
        color_transaction.record_external_outputs(&[
            TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        ])?;
        Ok(())
    }

    pub(crate) fn into_submission(self) -> NamedSourceDistantHorizonsSubmission {
        NamedSourceDistantHorizonsSubmission {
            targets: self.submission,
        }
    }
}

impl WorldPrimitiveFrontend {
    /// Prepares the source-derived Distant Horizons material stages against
    /// the exact named color generation staged by normal terrain. Opaque and
    /// the source pack's late translucent program use their own lowered
    /// programs but join one Rust-owned color/depth transaction. Complementary
    /// calls that late material program `dh_water`, yet it contains both its
    /// generic transparent path and a material-id water branch; copied layer
    /// identity and ordering remain explicit here.
    pub(crate) fn prepare_named_source_distant_horizons_frame_plan(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        color_targets: ShaderPackColorTargets,
    ) -> GalResult<Option<PreparedNamedSourceDistantHorizonsFramePlan>> {
        if !frame.lod_render_frame.rust_route_selected() || frame.lod_instances.is_empty() {
            return Ok(None);
        }
        let extent = Extent3d {
            width: frame.viewport_width,
            height: frame.viewport_height,
            depth: 1,
        };
        let Some(staged) = self.stage_distant_horizons_source_targets_for_colors(
            gal,
            frame.frame_id,
            frame.shader_environment.world_generation,
            extent,
            color_targets,
        )?
        else {
            return Ok(None);
        };
        let result = (|| -> GalResult<PreparedNamedSourceDistantHorizonsFramePlan> {
            let program = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime remains installed while preparing source DH")
                .prepared_lowered_distant_horizons_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "Rust Distant Horizons source plan has no admitted opaque program",
                    )
                })?;
            self.lod_source_pass_resources.begin_source_frame();
            let resources = self.candidate_source_resources_for_distant_horizons_program(
                gal,
                frame.shader_environment.world_generation,
                frame.frame_id,
                &program,
                &staged.color_targets,
                &staged.depth_targets,
            )?;
            let mut upload_operations = Vec::new();
            self.lod_gpu_residency.stage_visible_uploads(
                gal,
                &self.lod_gpu_column_assets,
                &frame.lod_instances,
                &mut upload_operations,
            )?;
            let visible = self
                .lod_gpu_residency
                .resolve_visible_draws_cached(&self.lod_gpu_column_assets, &frame.lod_instances)?;
            let plan = lod::plan_world_lod_frame_with_camera(
                &frame.lod_render_frame,
                &visible,
                frame.lod_render_frame.camera_world_position,
            )?;
            // Preserve the selected source program's declared material
            // convention. Complementary's dh_terrain program consumes DH's
            // reduced color/category stream and has no material-atlas input.
            // Atlas replacement is valid only for a source program that
            // explicitly declares an atlas-backed stream.
            let audit_exact_atlas_mode = world_lod_audit_exact_atlas_mode();
            let source_accepts_exact_atlas = program.has_exact_material_texture_identity();
            // The source route only needs this lookup when the selected
            // program explicitly admits exact-atlas replacement. Avoid
            // rebuilding a frame-sized ordered map for reduced-color source
            // programs; the route contract and ownership stay unchanged.
            let visible_by_source = source_accepts_exact_atlas.then(|| {
                visible
                    .iter()
                    .map(|draw| {
                        (
                            (
                                draw.column_key,
                                draw.column_generation,
                                draw.layer,
                                draw.segment_index,
                            ),
                            *draw,
                        )
                    })
                    .collect::<BTreeMap<_, _>>()
            });
            if !source_accepts_exact_atlas
                && matches!(
                    audit_exact_atlas_mode,
                    WorldLodAuditExactAtlasMode::ExactOnly
                )
            {
                return Err(GalError::unsupported_feature(
                    "the selected Distant Horizons source program does not declare an atlas-backed material interface",
                ));
            }
            let mut exact_atlas_instances = Vec::new();
            let mut partial_exact_instances = Vec::new();
            for instance in frame
                .lod_instances
                .iter()
                .filter(|_| source_accepts_exact_atlas)
            {
                if instance.layer != WORLD_LOD_LAYER_OPAQUE {
                    continue;
                }
                let asset = self
                    .lod_textured_gpu_column_assets
                    .get(&instance.column_key)
                    .filter(|asset| asset.column_generation == instance.column_generation)
                    .ok_or_else(|| GalError::invalid_argument(format!(
                        "world LOD exact-atlas source instance {} has no matching immutable column asset",
                        instance.column_key
                    )))?;
                if let Some(complete) = world_lod_exact_atlas_segment_completeness(asset, instance)?
                {
                    exact_atlas_instances.push(*instance);
                    if !complete {
                        partial_exact_instances.push(*instance);
                    }
                }
            }
            if matches!(
                audit_exact_atlas_mode,
                WorldLodAuditExactAtlasMode::CoarseOnly
            ) {
                // Audit-only isolation: leave every visible source segment on
                // the normal copied DH stream. This must happen before the
                // replacement set is derived so no coarse geometry is lost.
                exact_atlas_instances.clear();
                partial_exact_instances.clear();
            }
            self.lod_textured_gpu_residency.stage_visible_uploads(
                gal,
                &self.lod_textured_gpu_column_assets,
                &exact_atlas_instances,
                &mut upload_operations,
            )?;
            let mut exact_atlas_geometry = Vec::new();
            for instance in &exact_atlas_instances {
                let origin = self
                    .lod_column_assets
                    .get(&instance.column_key)
                    .filter(|asset| asset.column_generation == instance.column_generation)
                    .map(|asset| asset.origin)
                    .ok_or_else(|| {
                        GalError::invalid_argument(format!(
                        "world LOD exact-atlas source instance {} has no matching column origin",
                        instance.column_key
                    ))
                    })?;
                if let Some(draw) = self.lod_textured_gpu_residency.resolve_visible_draw(
                    &self.lod_textured_gpu_column_assets,
                    instance,
                    origin,
                )? {
                    exact_atlas_geometry.push(draw);
                }
            }
            let exact_source_segments = exact_atlas_geometry
                .iter()
                .map(|draw| {
                    (
                        draw.column_key,
                        draw.column_generation,
                        draw.source_segment_index,
                    )
                })
                .collect::<BTreeSet<_>>();
            let mut unresolved_coarse_draws = BTreeMap::new();
            for instance in &partial_exact_instances {
                let key = (
                    instance.column_key,
                    instance.column_generation,
                    instance.layer,
                    instance.segment_index,
                );
                let source_draw = visible_by_source
                    .as_ref()
                    .and_then(|draws| draws.get(&key).copied())
                    .ok_or_else(|| {
                        GalError::backend(
                            "world LOD partial exact-atlas source segment is absent from visible source draws",
                        )
                    })?;
                let unresolved = self
                    .lod_textured_gpu_residency
                    .resolve_visible_unresolved_draw(
                        &self.lod_textured_gpu_column_assets,
                        instance,
                        source_draw,
                    )?
                    .ok_or_else(|| {
                        GalError::backend(
                            "world LOD partial exact-atlas source segment has no complementary reduced-color range",
                        )
                    })?;
                unresolved_coarse_draws.insert(key, unresolved);
            }
            let partial_exact_source_segments = partial_exact_instances
                .iter()
                .map(|instance| {
                    (
                        instance.column_key,
                        instance.column_generation,
                        instance.segment_index,
                    )
                })
                .collect::<BTreeSet<_>>();
            let complete_replacement_draw_count = exact_source_segments
                .difference(&partial_exact_source_segments)
                .count() as u64;
            // The capture gate consumes this bounded, frame-scoped semantic
            // receipt. Selected-source execution resolves the same exact
            // geometry through a distinct pass owner, so publish its plan on
            // this frame rather than letting a prior ordinary DH plan stand
            // in for the presented source submission.
            self.write_world_lod_exact_atlas_status(
                frame,
                &exact_atlas_geometry,
                &partial_exact_instances,
                if source_accepts_exact_atlas {
                    "selected-source-atlas-contract"
                } else {
                    "selected-source-reduced-color-contract"
                },
                plan.opaque_draws.len() as u64,
                complete_replacement_draw_count,
                exact_atlas_geometry.len() as u64,
                partial_exact_instances.len() as u64,
                exact_atlas_geometry
                    .iter()
                    .map(|draw| u64::from(draw.index_count))
                    .sum(),
                unresolved_coarse_draws
                    .values()
                    .map(|draw| u64::from(draw.index_count))
                    .sum(),
            );
            let transparent_draw_count = plan.transparent_draws.len();
            let transparent_index_count = plan
                .transparent_draws
                .iter()
                .map(|draw| u64::from(draw.draw.index_count))
                .sum();
            let water_draw_count = plan.water_draws.len();
            let water_index_count = plan
                .water_draws
                .iter()
                .map(|draw| u64::from(draw.draw.index_count))
                .sum();
            let mut late_translucent_draws = plan
                .transparent_draws
                .iter()
                .map(|admitted| (admitted.draw, admitted.uniforms))
                .collect::<Vec<_>>();
            late_translucent_draws.extend(
                plan.water_draws
                    .iter()
                    .map(|admitted| (admitted.draw, admitted.uniforms)),
            );
            // The Java collector's order is the authoritative semantic order
            // for these alpha-blended DH ranges. Preserve it across the two
            // material classifications; the selected source program branches
            // on material identity rather than on a backend pass alias.
            late_translucent_draws.sort_by_key(|(draw, _)| {
                (draw.order, draw.layer, draw.column_key, draw.segment_index)
            });
            let late_translucent_draw_count = late_translucent_draws.len();
            let late_translucent_index_count = late_translucent_draws
                .iter()
                .map(|(draw, _)| u64::from(draw.index_count))
                .sum();
            let translucent_program = if late_translucent_draws.is_empty() {
                None
            } else {
                let translucent_source_status = self.shader_runtime.as_ref().and_then(
                    ShaderPackRuntimeExecutor::distant_horizons_translucent_source_candidate,
                );
                Some(
                    self.shader_runtime
                        .as_ref()
                        .expect("shader runtime remains installed while preparing source DH water")
                        .prepared_lowered_distant_horizons_translucent_source_program()?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(format!(
                                "Rust Distant Horizons source plan has {} late translucent ranges, but its selected source program is unavailable: {}",
                                late_translucent_draw_count,
                                describe_distant_horizons_translucent_source_status(
                                    translucent_source_status,
                                ),
                            ))
                        })?,
                )
            };
            let target = self.lod_source_pass_resources.stage_target(
                gal,
                &staged.color_targets,
                staged.depth_targets,
            )?;
            let source_uniforms = self.source_uniform_frame_for_distant_horizons(frame)?;
            let pack_resources = self.lod_source_pass_resources.stage_pack_resources(
                gal,
                &program,
                target.primary_color_format,
                &resources,
            )?;
            let pack_resources_layout = self.lod_source_pass_resources.pack_resources_layout(
                gal,
                &program,
                target.primary_color_format,
            )?;
            let atlas = if exact_atlas_geometry.is_empty() {
                None
            } else {
                self.ensure_mesh_texture_resources(
                    gal,
                    WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
                    "world-lod-exact-atlas-source",
                )?;
                let resources = self
                    .mesh_texture_resources
                    .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
                    .expect("terrain atlas resources exist after successful source preparation");
                Some(lod::WorldLodTerrainAtlasBinding {
                    mesh_generation: self
                        .mesh_texture_generation(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS),
                    texture_view: resources.view,
                    sampler: resources.sampler,
                })
            };
            let primary_attachment = target
                .color_attachments
                .iter()
                .find(|attachment| attachment.output == TerrainPassOutput::LitTerrainColor)
                .ok_or_else(|| {
                    GalError::backend(
                        "selected-source Distant Horizons target omitted its semantic primary color attachment",
                    )
                })?;
            let mut exact_atlas_draws = Vec::with_capacity(exact_atlas_geometry.len());
            if let Some(atlas) = atlas {
                let exact_atlas_program =
                    prepare_lowered_distant_horizons_exact_atlas_source_program(&program)?;
                // An audit-only limiter isolates one real provenance-resolved
                // range when diagnosing a selected-source GPU fault. It is
                // unavailable outside graphics-audit runs and never changes
                // production route selection.
                let exact_atlas_draw_limit = if matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                        .as_deref()
                        .map(str::trim),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    crate::core::environment::var("MATTMC_RUST_DH_EXACT_ATLAS_MAX_DRAWS")
                        .ok()
                        .and_then(|value| value.trim().parse::<usize>().ok())
                } else {
                    None
                };
                let exact_atlas_index_limit = if matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                        .as_deref()
                        .map(str::trim),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    crate::core::environment::var("MATTMC_RUST_DH_EXACT_ATLAS_MAX_INDICES")
                        .ok()
                        .and_then(|value| value.trim().parse::<u32>().ok())
                        .map(|indices| indices - indices % 3)
                } else {
                    None
                };
                for draw in exact_atlas_geometry
                    .iter()
                    .copied()
                    .take(exact_atlas_draw_limit.unwrap_or(usize::MAX))
                {
                    let draw = lod::WorldLodTexturedGpuDraw {
                        index_count: exact_atlas_index_limit
                            .map(|limit| draw.index_count.min(limit))
                            .unwrap_or(draw.index_count),
                        ..draw
                    };
                    if draw.index_count == 0 {
                        continue;
                    }
                    let uniforms = lod::WorldLodDrawUniform::from_semantics_with_camera(
                        &frame.lod_render_frame,
                        lod::WorldLodGpuDraw {
                            column_key: draw.column_key,
                            column_generation: draw.column_generation,
                            origin: draw.origin,
                            layer: draw.layer,
                            segment_index: draw.source_segment_index,
                            order: draw.order,
                            vertex_buffer: draw.vertex_buffer,
                            vertex_base: 0,
                            index_buffer: draw.index_buffer,
                            index_offset: 0,
                            index_type: draw.index_type,
                            index_count: draw.index_count,
                        },
                        frame.lod_render_frame.camera_world_position,
                    )?
                    .with_fog(
                        frame.shader_environment.fog_parameter_color,
                        [
                            frame.shader_environment.fog_environmental_start,
                            frame.shader_environment.fog_environmental_end,
                            frame.shader_environment.fog_render_distance_start,
                            frame.shader_environment.fog_render_distance_end,
                        ],
                    );
                    let prepared = self.lod_exact_atlas_source_pass_resources.stage_draw(
                        gal,
                        &exact_atlas_program,
                        primary_attachment,
                        pack_resources_layout,
                        draw,
                        uniforms,
                        atlas,
                        &source_uniforms,
                        &mut upload_operations,
                    )?;
                    exact_atlas_draws.push((prepared, pack_resources));
                }
            }
            let generic_geometry = if frame.dh_generic_boxes.is_empty() {
                DistantHorizonsGenericSourceGeometry::default()
            } else {
                distant_horizons_generic_source_geometry(&frame.dh_generic_boxes)?
            };
            self.lod_source_pass_resources.reserve_column_frames(
                gal,
                &program,
                target.primary_color_format,
                plan.opaque_draws.len() + generic_geometry.ranges.len(),
            )?;
            let mut draws = Vec::with_capacity(plan.opaque_draws.len());
            // Keep the semantic draw records alongside the private prepared
            // commands for the audit receipt below. This is intentionally
            // scoped to the selected source stream: uploaded or exact-atlas
            // replacement geometry must not be mistaken for a source-program
            // draw when diagnosing clip-space faults.
            let mut selected_source_geometry = Vec::with_capacity(plan.opaque_draws.len());
            for admitted in plan.opaque_draws {
                let key = (
                    admitted.draw.column_key,
                    admitted.draw.column_generation,
                    admitted.draw.layer,
                    admitted.draw.segment_index,
                );
                let exact_key = (key.0, key.1, key.3);
                let draw = match audit_exact_atlas_mode {
                    WorldLodAuditExactAtlasMode::ExactOnly => continue,
                    WorldLodAuditExactAtlasMode::Default => {
                        if exact_source_segments.contains(&exact_key) {
                            let Some(unresolved) = unresolved_coarse_draws.get(&key).copied()
                            else {
                                continue;
                            };
                            unresolved
                        } else {
                            admitted.draw
                        }
                    }
                    WorldLodAuditExactAtlasMode::CoarseOnly => admitted.draw,
                };
                if draw.index_count == 0 {
                    continue;
                }
                selected_source_geometry.push(draw);
                draws.push(self.lod_source_pass_resources.stage_draw(
                    gal,
                    &program,
                    target.primary_color_format,
                    draw,
                    admitted.uniforms,
                    &source_uniforms,
                    &mut upload_operations,
                )?);
            }
            let generic_draws = self.stage_distant_horizons_generic_source_draws(
                gal,
                frame,
                generic_geometry,
                &program,
                target.primary_color_format,
                &source_uniforms,
                &mut upload_operations,
            )?;
            self.write_selected_source_distant_horizons_material_receipt(
                frame,
                &program,
                translucent_program.as_deref(),
                draws.len(),
                draws.iter().map(|draw| u64::from(draw.index_count)).sum(),
                transparent_draw_count,
                transparent_index_count,
                water_draw_count,
                water_index_count,
                &plan
                    .water_draws
                    .iter()
                    .map(|draw| draw.draw.origin)
                    .collect::<Vec<_>>(),
                late_translucent_draw_count,
                late_translucent_index_count,
                exact_atlas_draws.len(),
                exact_atlas_draws
                    .iter()
                    .map(|(draw, _)| u64::from(draw.index_count))
                    .sum(),
            );
            self.write_selected_source_distant_horizons_stream_receipt(frame, &program);
            // Keep the capture receipt representative of every executed DH
            // material range. Water has its own late translucent pass, so an
            // opaque-only trace cannot distinguish a bad water transform from
            // a later depth/composite defect.
            let mut selected_source_transform_geometry = selected_source_geometry.clone();
            selected_source_transform_geometry
                .extend(late_translucent_draws.iter().map(|(draw, _uniforms)| *draw));
            self.write_distant_horizons_transform_receipt(
                frame,
                "selected-source",
                program.identity.as_str(),
                &selected_source_transform_geometry,
            );
            let (translucent_draws, translucent_pack_resources) =
                if let Some(translucent_program) = translucent_program {
                    let translucent_resources = self
                        .candidate_source_resources_for_distant_horizons_program(
                            gal,
                            frame.shader_environment.world_generation,
                            frame.frame_id,
                            &translucent_program,
                            &staged.color_targets,
                            &staged.depth_targets,
                        )?;
                    let translucent_pack_resources =
                        self.lod_source_pass_resources.stage_pack_resources(
                            gal,
                            &translucent_program,
                            target.primary_color_format,
                            &translucent_resources,
                        )?;
                    self.lod_source_pass_resources.reserve_column_frames(
                        gal,
                        &translucent_program,
                        target.primary_color_format,
                        late_translucent_draws.len(),
                    )?;
                    let mut translucent_draws = Vec::with_capacity(late_translucent_draws.len());
                    for (draw, uniforms) in late_translucent_draws {
                        translucent_draws.push(self.lod_source_pass_resources.stage_draw(
                            gal,
                            &translucent_program,
                            target.primary_color_format,
                            draw,
                            uniforms,
                            &source_uniforms,
                            &mut upload_operations,
                        )?);
                    }
                    (translucent_draws, Some(translucent_pack_resources))
                } else {
                    (Vec::new(), None)
                };
            self.lod_source_pass_resources
                .flush_source_frame(&mut upload_operations);
            Ok(PreparedNamedSourceDistantHorizonsFramePlan {
                submission: staged.submission,
                target,
                depth_targets: staged.depth_targets,
                draws,
                pack_resources,
                exact_atlas_draws,
                translucent_draws,
                translucent_pack_resources,
                generic_draws,
                upload_operations,
                depth_cleared: false,
            })
        })();
        if result.is_err() {
            // This helper owns only the DH depth/upload half. Its caller owns
            // the shared color generation and decides whether normal terrain
            // can continue or the complete source frame must be discarded.
            self.lod_source_targets.discard_submission(gal);
            self.discard_distant_horizons_generic_source_buffers(gal);
            self.pending_distant_horizons_source_targets = None;
            self.lod_gpu_residency.discard_submission(gal);
            self.lod_textured_gpu_residency.discard_submission(gal);
        }
        result.map(Some)
    }
}

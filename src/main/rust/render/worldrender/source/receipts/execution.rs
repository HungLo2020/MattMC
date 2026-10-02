//! Runtime source execution attempts, latest state, admission status and execution JSON.

use super::*;

impl WorldPrimitiveFrontend {
    /// Emits one overwrite-in-place record while an explicitly requested
    /// selected-source frame is being prepared. This is diagnostic-only and
    /// intentionally records no backend object identity, so an interrupted
    /// capture can identify the exact native stage without making ordinary
    /// presentation or source-route behavior depend on filesystem I/O.
    pub(crate) fn write_runtime_source_execution_attempt(
        &self,
        frame: &WorldPrimitiveFrame,
        stage: &str,
        elapsed: std::time::Duration,
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
                Path::new(&dir).join("selected-source-execution-attempt.json"),
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"world_generation\":{},",
                        "\"stage\":\"{}\",\"elapsed_nanos\":{},",
                        "\"mesh_instances\":{},\"lod_instances\":{},",
                        "\"viewport\":[{},{}]}}"
                    ),
                    frame.frame_id,
                    frame.voxel_volume.world_generation,
                    stage,
                    elapsed.as_nanos(),
                    frame.mesh_instances.len(),
                    frame.lod_instances.len(),
                    frame.viewport_width,
                    frame.viewport_height,
                ),
            );
        }
    }

    /// Capture-only summary of the exact explicit states selected for the
    /// Rust-owned cloud writer.  This deliberately records semantic pipeline
    /// inputs, not native pipeline handles or Java/Iris state, so visual
    /// diagnosis can distinguish an incorrect source pass from later frame
    /// composition without affecting admission or execution.
    pub(in crate::render::worldrender) fn write_cloud_pipeline_receipt(
        &self,
        frame_id: u64,
        program: &LoweredTexturedMaterialSourceProgram,
        batches: &[SourceTexturedMaterialBatch],
        blend: CloudBlend,
        color_attachment_count: usize,
    ) {
        if !matches!(
            crate::core::environment::var("MATTMC_RUST_CLOUD_PIPELINE_RECEIPT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) || !matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let mut states = BTreeMap::<(u32, u32, u32), usize>::new();
        for batch in batches {
            *states
                .entry((batch.depth_policy, batch.cull_policy, batch.winding))
                .or_default() += batch.count;
        }
        let state_json = states
			.into_iter()
			.map(|((depth, cull, winding), quads)| {
				format!("{{\"depth_policy\":{depth},\"cull_policy\":{cull},\"winding\":{winding},\"quads\":{quads}}}")
			})
			.collect::<Vec<_>>()
			.join(",");
        if std::fs::create_dir_all(&dir).is_ok() {
            let _ = std::fs::write(
				Path::new(&dir).join("rust-cloud-pipeline-last.json"),
				format!(
					"{{\"frame_id\":{frame_id},\"writer\":\"clouds\",\"program\":\"{}\",\"generation\":{},\"blend\":\"{:?}\",\"color_attachments\":{color_attachment_count},\"states\":[{state_json}]}}\n",
					json_escape(program.identity.as_str()), program.shader_pack_generation, blend
				),
			);
        }
    }

    /// Keeps one bounded receipt for capture correlation after every actual
    /// selected-source submission. The activation evidence below intentionally
    /// remains first-frame-only; this companion cannot let a later edited
    /// deterministic scenario inherit that earlier success.
    pub(crate) fn write_runtime_source_execution_latest(
        &self,
        frame: &WorldPrimitiveFrame,
        stats: &WorldPrimitiveSubmitStats,
        gui_stats: &GuiSubmitStats,
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_ok() {
            let source_mesh_instance_semantics = source_mesh_instance_semantics_json(frame);
            let source_entity_instance_semantics =
                self.source_entity_instance_semantics_json(frame);
            let source_material_semantics = source_material_semantics_json(frame);
            let source_material_execution = source_material_execution_json(stats);
            let world_text_execution = world_text_execution_json(stats);
            let gui_execution = gui_execution_json(gui_stats);
            let _ = std::fs::write(
                dir.join("selected-source-execution-latest.json"),
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"submission_id\":{},\"world_generation\":{},",
                        "\"mesh_instances\":{},\"lod_instances\":{},",
                        "\"source_mesh_instance_semantics\":{},",
                        "\"source_entity_instance_semantics\":{},",
                        "\"source_entity_coverage\":{{\"instances\":{},\"draws\":{},\"indices\":{}}},",
                        "\"source_material_coverage\":{{\"batches\":{},\"quads\":{},\"draws\":{},\"vertices\":{}}},",
                        "\"source_material_semantics\":{},",
                        "\"source_material_execution\":{},",
                        "\"world_text_execution\":{},",
                        "\"gui_execution\":{},",
                        "\"source_line_execution\":{},",
                        "\"route\":\"rust-native-selected-source\"}}\n"
                    ),
                    frame.frame_id,
                    stats.submission_id,
                    frame.voxel_volume.world_generation,
                    frame.mesh_instances.len(),
                    frame.lod_instances.len(),
                    source_mesh_instance_semantics,
                    source_entity_instance_semantics,
                    stats.source_entity_instance_count,
                    stats.source_entity_draw_count,
                    stats.source_entity_index_count,
                    stats.source_material_batch_count,
                    stats.source_material_quad_count,
                    stats.source_material_draw_count,
                    stats.source_material_vertex_count,
                    source_material_semantics,
                    source_material_execution,
                    world_text_execution,
                    gui_execution,
                    self.last_source_line_execution
                        .as_ref()
                        .filter(|(frame_id, ..)| *frame_id == frame.frame_id)
                        .map(|(_, program, segments, opaque, translucent)| format!(
                            "{{\"program\":\"{}\",\"segments\":{segments},\"opaque_draws\":{opaque},\"translucent_draws\":{translucent}}}",
                            json_escape(program)
                        ))
                        .unwrap_or_else(|| "null".to_string()),
                ),
            );
        }
    }

    /// Receipts for ordinary whole-frame submissions that carried text. The
    /// selected-source receipt is intentionally sparse and may be emitted on
    /// a neighboring preparation frame, so it cannot prove this producer's
    /// actual draw boundary by itself.
    pub(crate) fn write_runtime_world_text_execution_receipt(
        &self,
        frame: &WorldPrimitiveFrame,
        stats: &WorldPrimitiveSubmitStats,
        submission_id: u64,
    ) {
        if stats.world_text_quad_count == 0
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_ok() {
            let _ = std::fs::write(
                dir.join(format!(
                    "world-text-execution-frame-{}.json",
                    frame.frame_id
                )),
                format!(
                    concat!(
                        "{{\"frame_id\":{},\"correlation_id\":{},\"submission_id\":{},",
                        "\"world_generation\":{},\"viewport\":[{},{}],",
                        "\"execution\":{},\"route\":\"rust-whole-frame\"}}\n"
                    ),
                    frame.frame_id,
                    frame.correlation_id,
                    submission_id,
                    frame.voxel_volume.world_generation,
                    frame.viewport_width,
                    frame.viewport_height,
                    world_text_execution_json(stats),
                ),
            );
        }
    }

    pub(crate) fn report_runtime_source_execution(
        &mut self,
        frame: &WorldPrimitiveFrame,
        stats: &WorldPrimitiveSubmitStats,
        gui_stats: &GuiSubmitStats,
    ) {
        if !matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            return;
        }
        let (lod_opaque_instances, lod_transparent_instances, lod_water_instances) =
            frame.lod_instances.iter().fold(
                (0_u64, 0_u64, 0_u64),
                |counts, instance| match instance.layer {
                    1 => (counts.0.saturating_add(1), counts.1, counts.2),
                    2 | 3 => (counts.0, counts.1.saturating_add(1), counts.2),
                    4 => (counts.0, counts.1, counts.2.saturating_add(1)),
                    _ => counts,
                },
            );
        let includes_distant_horizons = frame.lod_render_frame.rust_route_selected()
            && lod_opaque_instances
                .saturating_add(lod_transparent_instances)
                .saturating_add(lod_water_instances)
                > 0;
        if self.source_execution_activation_reported
            && (!includes_distant_horizons || self.source_execution_distant_horizons_reported)
        {
            return;
        }
        let (distant_horizons_opaque_program_ready, distant_horizons_water_program_ready) = self
            .shader_runtime
            .as_ref()
            .map(|runtime| {
                (
                    runtime
                        .prepared_lowered_distant_horizons_source_program()
                        .ok()
                        .flatten()
                        .is_some(),
                    runtime
                        .prepared_lowered_distant_horizons_translucent_source_program()
                        .ok()
                        .flatten()
                        .is_some(),
                )
            })
            .unwrap_or((false, false));
        eprintln!(
            "Rust VulkanicGAL selected-source execution admitted frame={} submission={} world_generation={} mesh_instances={} lod_instances={} lod_opaque_instances={} lod_transparent_instances={} lod_water_instances={}",
            frame.frame_id,
            stats.submission_id,
            frame.voxel_volume.world_generation,
            frame.mesh_instances.len(),
            frame.lod_instances.len(),
            lod_opaque_instances,
            lod_transparent_instances,
            lod_water_instances,
        );
        // Diagnostic-only receipt for the exact Rust-derived shadow transform.
        // Do not rebuild the temporal source-uniform block after submit: this
        // reads only the immutable source policy and copied frame semantics.
        let source_shadow_semantics = self
            .shader_pack_sources
            .active_shadow_policy()
            .and_then(|policy| {
                terrain_program_scope_for_sky_type(frame.background.sky_type)
                    .ok()
                    .flatten()
                    .and_then(|scope| {
                        policy
                            .uniforms_with_end_flash(
                                scope,
                                frame.shader_environment.time_of_day,
                                frame.voxel_volume.camera_world_position,
                                Some([
                                    frame.background.sky.end_flash_x_angle,
                                    frame.background.sky.end_flash_y_angle,
                                ]),
                            )
                            .ok()
                    })
            })
            .map(|shadow| {
                format!(
                    concat!(
                        "{{\"status\":\"available\",\"camera_world_position\":{:?},",
                        "\"model_view\":{:?},\"projection\":{:?}}}"
                    ),
                    frame.voxel_volume.camera_world_position, shadow.model_view, shadow.projection,
                )
            })
            .unwrap_or_else(|| "{\"status\":\"unavailable\"}".to_owned());
        if let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") {
            let dir = Path::new(&dir);
            if std::fs::create_dir_all(dir).is_ok() {
                let source_mesh_instance_semantics = source_mesh_instance_semantics_json(frame);
                let source_entity_instance_semantics =
                    self.source_entity_instance_semantics_json(frame);
                let first_person_transform_semantics =
                    first_person_transform_semantics_json(self, frame);
                let source_material_semantics = source_material_semantics_json(frame);
                let source_material_execution = source_material_execution_json(stats);
                let world_text_execution = world_text_execution_json(stats);
                let gui_execution = gui_execution_json(gui_stats);
                let filename = if includes_distant_horizons {
                    format!(
                        "selected-source-execution-distant-horizons-frame-{}.json",
                        frame.frame_id
                    )
                } else {
                    format!("selected-source-execution-frame-{}.json", frame.frame_id)
                };
                let _ = std::fs::write(
                    dir.join(filename),
                    format!(
                        concat!(
                            "{{\"frame_id\":{},\"submission_id\":{},\"world_generation\":{},",
                            "\"mesh_instances\":{},\"lod_instances\":{},",
                            "\"first_person\":{{\"enabled\":{},\"mesh_instances\":{},\"main_hand_instances\":{},\"clear_depth_before\":{}}},",
                            "\"source_mesh_instance_semantics\":{},",
                            "\"source_entity_instance_semantics\":{},",
                            "\"first_person_transform_semantics\":{},",
                            "\"source_entity_coverage\":{{\"instances\":{},\"draws\":{},\"indices\":{}}},",
                            "\"source_draw_coverage\":{{\"opaque_batches\":{},\"cutout_batches\":{},\"translucent_batches\":{},\"opaque_instances\":{},\"cutout_instances\":{},\"translucent_instances\":{},\"opaque_indices\":{},\"cutout_indices\":{},\"translucent_indices\":{},\"opaque_draws\":{},\"cutout_draws\":{},\"translucent_draws\":{},\"opaque_draw_indices\":{},\"cutout_draw_indices\":{},\"translucent_draw_indices\":{}}},",
                            "\"source_material_coverage\":{{\"batches\":{},\"quads\":{},\"draws\":{},\"vertices\":{}}},",
                            "\"source_material_semantics\":{},",
                            "\"source_material_execution\":{},",
                            "\"world_text_execution\":{},",
                            "\"gui_execution\":{},",
                            "\"lod_opaque_instances\":{},\"lod_transparent_instances\":{},\"lod_water_instances\":{},",
                            "\"shader_environment\":{{\"enabled\":{},\"world_time\":{},\"frame_counter\":{},\"frame_time_seconds\":{},\"frame_time_counter\":{},\"time_of_day\":{},\"rain_strength\":{},\"sky_darken\":{},\"sky_color\":[{},{},{}],\"fog_color\":[{},{},{}],\"fog_parameters\":{{\"color\":[{},{},{},{}],\"environmental_start\":{},\"environmental_end\":{},\"render_distance_start\":{},\"render_distance_end\":{}}},\"eye_brightness\":[{},{}],\"darkness_light_factor\":{},\"lightmap\":{{\"generation\":{},\"darkness_scale\":{},\"darken_world_factor\":{},\"sky_factor\":{}}}}},",
                            "\"source_shadow_semantics\":{},",
                            "\"distant_horizons_opaque_program_ready\":{},\"distant_horizons_water_program_ready\":{},",
                            "\"selected_source_fragment_probe\":\"{}\",",
                            "\"route\":\"rust-native-selected-source\"}}\n"
                        ),
                        frame.frame_id,
                        stats.submission_id,
                        frame.voxel_volume.world_generation,
                        frame.mesh_instances.len(),
                        frame.lod_instances.len(),
                        frame.first_person.enabled,
                        frame.first_person_mesh_instances.len(),
                        frame.first_person.main_hand_instance_count,
                        frame.first_person.clear_depth_before,
                        source_mesh_instance_semantics,
                        source_entity_instance_semantics,
                        first_person_transform_semantics,
                        stats.source_entity_instance_count,
                        stats.source_entity_draw_count,
                        stats.source_entity_index_count,
                        stats.source_opaque_batch_count,
                        stats.source_cutout_batch_count,
                        stats.source_translucent_batch_count,
                        stats.source_opaque_instance_count,
                        stats.source_cutout_instance_count,
                        stats.source_translucent_instance_count,
                        stats.source_opaque_index_count,
                        stats.source_cutout_index_count,
                        stats.source_translucent_index_count,
                        stats.source_opaque_draw_count,
                        stats.source_cutout_draw_count,
                        stats.source_translucent_draw_count,
                        stats.source_opaque_draw_index_count,
                        stats.source_cutout_draw_index_count,
                        stats.source_translucent_draw_index_count,
                        stats.source_material_batch_count,
                        stats.source_material_quad_count,
                        stats.source_material_draw_count,
                        stats.source_material_vertex_count,
                        source_material_semantics,
                        source_material_execution,
                        world_text_execution,
                        gui_execution,
                        lod_opaque_instances,
                        lod_transparent_instances,
                        lod_water_instances,
                        frame.shader_environment.enabled,
                        frame.shader_environment.world_time,
                        frame.shader_environment.frame_counter,
                        frame.shader_environment.frame_time_seconds,
                        frame.shader_environment.frame_time_counter,
                        frame.shader_environment.time_of_day,
                        frame.shader_environment.rain_strength,
                        frame.shader_environment.sky_darken,
                        frame.shader_environment.sky_color[0],
                        frame.shader_environment.sky_color[1],
                        frame.shader_environment.sky_color[2],
                        frame.shader_environment.fog_color[0],
                        frame.shader_environment.fog_color[1],
                        frame.shader_environment.fog_color[2],
                        frame.shader_environment.fog_parameter_color[0],
                        frame.shader_environment.fog_parameter_color[1],
                        frame.shader_environment.fog_parameter_color[2],
                        frame.shader_environment.fog_parameter_color[3],
                        frame.shader_environment.fog_environmental_start,
                        frame.shader_environment.fog_environmental_end,
                        frame.shader_environment.fog_render_distance_start,
                        frame.shader_environment.fog_render_distance_end,
                        frame.shader_environment.eye_brightness[0],
                        frame.shader_environment.eye_brightness[1],
                        frame.shader_environment.darkness_light_factor,
                        frame
                            .shader_environment
                            .vanilla_lightmap
                            .map(|lightmap| lightmap.generation)
                            .unwrap_or_default(),
                        frame
                            .shader_environment
                            .vanilla_lightmap
                            .map(|lightmap| lightmap.inputs.darkness_scale)
                            .unwrap_or_default(),
                        frame
                            .shader_environment
                            .vanilla_lightmap
                            .map(|lightmap| lightmap.inputs.darken_world_factor)
                            .unwrap_or_default(),
                        frame
                            .shader_environment
                            .vanilla_lightmap
                            .map(|lightmap| lightmap.inputs.sky_factor)
                            .unwrap_or_default(),
                        source_shadow_semantics,
                        distant_horizons_opaque_program_ready,
                        distant_horizons_water_program_ready,
                        crate::core::environment::var("MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE")
                            .unwrap_or_else(|_| "lit".to_owned()),
                    ),
                );
            }
        }
        self.source_execution_activation_reported = true;
        self.source_execution_distant_horizons_reported |= includes_distant_horizons;
    }

    /// Records the native source-admission state only when diagnostics are
    /// enabled. It is deliberately overwritten in place: diagnostics stay
    /// bounded and no ordinary frame performs filesystem work.
    pub(crate) fn write_runtime_source_admission_status(
        &self,
        gal: &VulkanicGal,
        frame: &WorldPrimitiveFrame,
        phase: &str,
        retain_as_last_world: bool,
    ) {
        if !self.source_execution_enabled()
            || !matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                    .as_deref()
                    .map(str::trim),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            return;
        }
        let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
            return;
        };
        let capabilities = gal.capabilities();
        let d3_ready = supports_private_colored_light_volume(gal);
        let (
            source_occupancy_stable_frames,
            source_occupancy_terrain_instance_count,
            source_occupancy_unique_mesh_count,
        ) = self.candidate_source_occupancy_stability_diagnostic(frame);
        let missing_roles = self
            .candidate_source_missing_resource_roles
            .iter()
            .map(TerrainSourceResourceRole::diagnostic_name)
            .collect::<Vec<_>>()
            .join(",");
        let (candidate_state, candidate_reason, candidate_requires_colored_volume) = self
            .shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::source_candidate_admission_diagnostic)
            .unwrap_or(("runtime-unavailable", None, None));
        let (source_weather_state, source_weather_reason) = self
            .shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::candidate_weather_source_diagnostic)
            .unwrap_or(("runtime-unavailable", None));
        let (source_cloud_state, source_cloud_reason) = self
            .shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::candidate_cloud_source_diagnostic)
            .unwrap_or(("runtime-unavailable", None));
        let (
            source_translucent_contract_discovered,
            source_translucent_contract_reason,
            source_translucent_unsupported_feature_count,
        ) = self
            .shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::source_candidate_translucent_diagnostic)
            .unwrap_or((false, None, None));
        let (
            source_translucent_resource_binding_count,
            source_translucent_resource_binding_reason,
            source_translucent_resource_bindings,
        ) = self
            .shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::source_candidate_translucent_resource_diagnostic)
            .unwrap_or((None, None, Vec::new()));
        let source_snapshot_prepared = self.candidate_source_resource_snapshot.is_some();
        let unsupported_feature_families = frame
            .feature_coverage
            .unsupported_families()
            .into_iter()
            .map(|(family, count)| format!("{{\"family\":\"{family}\",\"count\":{count}}}"))
            .collect::<Vec<_>>()
            .join(",");
        let colored_voxel_state = self
            .shader_runtime
            .as_ref()
            .and_then(|runtime| runtime.candidate_colored_light_diagnostic_state(frame.frame_id));
        let puddle_state = self
            .shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::candidate_puddle_diagnostic_state);
        let source_program_status = |kind: Option<TerrainMaterialProgramKind>| {
            let Some(runtime) = self.shader_runtime.as_ref() else {
                return (false, Some("shader runtime unavailable".to_string()));
            };
            let result = match kind {
                Some(kind) => runtime.prepared_lowered_terrain_source_program(kind),
                None => runtime.prepared_lowered_shadow_source_program(),
            };
            match result {
                Ok(Some(_)) => (true, None),
                Ok(None) => (false, Some("source program unavailable".to_string())),
                Err(error) => (false, Some(error.to_string())),
            }
        };
        let (source_opaque_prepared, source_opaque_reason) =
            source_program_status(Some(TerrainMaterialProgramKind::Opaque));
        let (source_cutout_prepared, source_cutout_reason) =
            source_program_status(Some(TerrainMaterialProgramKind::Cutout));
        let (source_shadow_prepared, source_shadow_reason) = source_program_status(None);
        let (source_translucent_prepared, source_translucent_reason) = self
            .shader_runtime
            .as_ref()
            .map(
                |runtime| match runtime.prepared_lowered_translucent_terrain_source_program() {
                    Ok(Some(_)) => (true, None),
                    Ok(None) => (false, Some("source program unavailable".to_string())),
                    Err(error) => (false, Some(error.to_string())),
                },
            )
            .unwrap_or((false, Some("shader runtime unavailable".to_string())));
        let blocker = if !frame.background.enabled {
            "world-background-disabled"
        } else if !frame.voxel_volume.enabled {
            "voxel-volume-frame-disabled"
        } else if !d3_ready {
            "backend-d3-capability-unavailable"
        } else if source_occupancy_terrain_instance_count == 0 {
            "source-terrain-input-unavailable"
        } else if !self.candidate_colored_light_runtime {
            "colored-voxel-runtime-not-created"
        } else if self.candidate_source_asset_error.is_some() {
            "source-asset-preparation-failed"
        } else if !self.candidate_source_missing_resource_roles.is_empty() {
            "source-resource-roles-incomplete"
        } else if !source_snapshot_prepared {
            "source-resource-snapshot-unavailable"
        } else if !(source_opaque_prepared && source_cutout_prepared && source_shadow_prepared) {
            "source-programs-not-lowered"
        } else if !self.source_execution_armed {
            "source-snapshot-awaiting-confirmation"
        } else {
            "source-execution-armed"
        };
        let json_escape = |value: &str| value.replace('\\', "\\\\").replace('"', "\\\"");
        let translucent_resource_bindings = source_translucent_resource_bindings
            .iter()
            .map(|(name, role)| {
                format!(
                    "{{\"source_name\":\"{}\",\"semantic_role\":\"{}\"}}",
                    json_escape(name),
                    json_escape(role),
                )
            })
            .collect::<Vec<_>>()
            .join(",");
        let dir = Path::new(&dir);
        if std::fs::create_dir_all(dir).is_ok() {
            let status = format!(
                concat!(
                    "{{\"phase\":\"{}\",\"frame_id\":{},\"world_generation\":{},\"voxel_resource_generation\":{},",
                    "\"background_enabled\":{},\"voxel_volume_enabled\":{},",
                    "\"backend\":\"{}\",\"texture_3d_supported\":{},\"colored_voxel_d3_ready\":{},",
                    "\"source_occupancy_stable_frames\":{},\"source_occupancy_terrain_instance_count\":{},\"source_occupancy_unique_mesh_count\":{},",
                    "\"source_candidate_state\":\"{}\",\"source_candidate_reason\":{},",
                    "\"source_candidate_requires_colored_voxel_light\":{},",
                    "\"source_weather_state\":\"{}\",\"source_weather_reason\":{},",
                    "\"source_cloud_state\":\"{}\",\"source_cloud_reason\":{},",
                    "\"source_translucent_contract_discovered\":{},\"source_translucent_contract_reason\":{},",
                    "\"source_translucent_unsupported_feature_count\":{},",
                    "\"source_translucent_resource_binding_count\":{},\"source_translucent_resource_binding_reason\":{},",
                    "\"source_translucent_resource_bindings\":[{}],",
                    "\"colored_voxel_runtime_created\":{},\"colored_voxel_mesh_snapshot_count\":{},",
                    "\"colored_voxel_extent_width\":{},\"colored_voxel_extent_height\":{},\"colored_voxel_extent_depth\":{},\"colored_voxel_expected_owned_bytes\":{},",
                    "\"colored_voxel_submission_pending\":{},\"colored_voxel_occupancy_upload_pending\":{},\"colored_voxel_occupancy_initialized\":{},",
                    "\"colored_voxel_emission_ready\":{},\"colored_voxel_tint_ready\":{},",
                    "\"colored_voxel_mapping_ready\":{},\"colored_voxel_compute_even_initialized\":{},\"colored_voxel_compute_odd_initialized\":{},",
                    "\"colored_voxel_pending_initialization_frame\":{},\"colored_voxel_pending_propagation_frame\":{},",
                    "\"colored_voxel_pending_compute_output_ready\":{},",
                    "\"colored_voxel_occupancy_input_samples\":{},\"colored_voxel_occupancy_emitted_samples\":{},\"colored_voxel_occupancy_overwritten_samples\":{},",
                    "\"colored_voxel_occupancy_skipped_non_solid_samples\":{},\"colored_voxel_occupancy_skipped_out_of_bounds_samples\":{},",
                    "\"colored_voxel_occupancy_changed_voxels\":{},\"colored_voxel_occupancy_uploaded_bytes\":{},",
                    "\"colored_voxel_ready_for_frame\":{},",
                    "\"puddle_runtime_created\":{},\"puddle_ready\":{},\"puddle_submission_pending\":{},\"puddle_initialized\":{},\"puddle_changed_texels\":{},",
                    "\"source_pack_asset_resources_ready\":{},",
                    "\"source_resource_snapshot_prepared\":{},\"source_opaque_prepared\":{},",
                    "\"source_opaque_reason\":{},\"source_cutout_prepared\":{},",
                    "\"source_cutout_reason\":{},\"source_shadow_prepared\":{},",
                    "\"source_shadow_reason\":{},",
                    "\"source_translucent_prepared\":{},\"source_translucent_reason\":{},",
                    "\"missing_resource_roles\":[{}],\"unsupported_feature_families\":[{}],\"source_distant_depth_admission\":{},\"source_asset_error\":{},",
                    "\"source_execution_armed\":{},\"source_execution_admission_reason\":{},\"blocker\":\"{}\"}}\n"
                ),
                json_escape(phase),
                frame.frame_id,
                frame.voxel_volume.world_generation,
                frame.voxel_volume.resource_generation,
                frame.background.enabled,
                frame.voxel_volume.enabled,
                capabilities.name,
                capabilities.supports(BackendFeature::Texture3d),
                d3_ready,
                source_occupancy_stable_frames,
                source_occupancy_terrain_instance_count,
                source_occupancy_unique_mesh_count,
                candidate_state,
                candidate_reason.map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                candidate_requires_colored_volume
                    .map_or_else(|| "null".to_string(), |required| required.to_string(),),
                source_weather_state,
                source_weather_reason.map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                source_cloud_state,
                source_cloud_reason.map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                source_translucent_contract_discovered,
                source_translucent_contract_reason.as_deref().map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                source_translucent_unsupported_feature_count
                    .map_or_else(|| "null".to_string(), |count| count.to_string()),
                source_translucent_resource_binding_count
                    .map_or_else(|| "null".to_string(), |count| count.to_string()),
                source_translucent_resource_binding_reason
                    .as_deref()
                    .map_or_else(
                        || "null".to_string(),
                        |reason| format!("\"{}\"", json_escape(reason)),
                    ),
                translucent_resource_bindings,
                self.candidate_colored_light_runtime,
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.mesh_snapshot_count.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.extent_width.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.extent_height.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.extent_depth.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.expected_owned_bytes.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.submission_pending.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_upload_pending.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_initialized.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.emission_ready.to_string()
                ),
                colored_voxel_state
                    .map_or_else(|| "null".to_string(), |state| state.tint_ready.to_string()),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.sampling_mapping_ready.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.compute_even_initialized.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.compute_odd_initialized.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state
                        .pending_initialization_frame
                        .map_or_else(|| "null".to_string(), |frame| frame.to_string())
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state
                        .pending_propagation_frame
                        .map_or_else(|| "null".to_string(), |frame| frame.to_string())
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.pending_compute_output_ready.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_input_samples.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_emitted_samples.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_overwritten_samples.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_skipped_non_solid_samples.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_skipped_out_of_bounds_samples.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_changed_voxels.to_string()
                ),
                colored_voxel_state.map_or_else(
                    || "null".to_string(),
                    |state| state.occupancy_uploaded_bytes.to_string()
                ),
                colored_voxel_state
                    .map_or_else(|| "null".to_string(), |state| state.frame_ready.to_string()),
                puddle_state.is_some(),
                puddle_state.map_or_else(|| "null".to_string(), |state| state.ready.to_string()),
                puddle_state.map_or_else(
                    || "null".to_string(),
                    |state| state.submission_pending.to_string()
                ),
                puddle_state
                    .map_or_else(|| "null".to_string(), |state| state.initialized.to_string()),
                puddle_state.map_or_else(
                    || "null".to_string(),
                    |state| state.changed_texels.to_string()
                ),
                self.candidate_source_asset_runtime,
                source_snapshot_prepared,
                source_opaque_prepared,
                source_opaque_reason.as_deref().map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                source_cutout_prepared,
                source_cutout_reason.as_deref().map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                source_shadow_prepared,
                source_shadow_reason.as_deref().map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                source_translucent_prepared,
                source_translucent_reason.as_deref().map_or_else(
                    || "null".to_string(),
                    |reason| format!("\"{}\"", json_escape(reason)),
                ),
                missing_roles
                    .split(',')
                    .filter(|role| !role.is_empty())
                    .map(|role| format!("\"{}\"", json_escape(role)))
                    .collect::<Vec<_>>()
                    .join(","),
                unsupported_feature_families,
                self.candidate_source_distant_depth_admission
                    .as_deref()
                    .map_or_else(
                        || "null".to_string(),
                        |reason| { format!("\"{}\"", json_escape(reason)) }
                    ),
                self.candidate_source_asset_error.as_deref().map_or_else(
                    || "null".to_string(),
                    |error| format!("\"{}\"", json_escape(error)),
                ),
                self.source_execution_armed,
                self.source_execution_admission_reason
                    .as_deref()
                    .map_or_else(
                        || "null".to_string(),
                        |reason| format!("\"{}\"", json_escape(reason)),
                    ),
                blocker,
            );
            let _ = std::fs::write(dir.join("selected-source-admission-status.json"), &status);
            // Keep a bounded phase trace while bringing the first real
            // selected-source frame online. Four fixed phase files make it
            // possible to identify where a semantic role disappears without
            // retaining per-frame diagnostics or changing rendering.
            let phase_name = phase
                .chars()
                .map(|character| {
                    if character.is_ascii_alphanumeric() {
                        character
                    } else {
                        '-'
                    }
                })
                .collect::<String>();
            let _ = std::fs::write(
                dir.join(format!("selected-source-admission-phase-{phase_name}.json")),
                &status,
            );
            // A small rolling history makes frame-to-frame admission churn
            // diagnosable without retaining unbounded per-frame artifacts.
            let history_path = dir.join("selected-source-admission-history.ndjson");
            let mut history = std::fs::read_to_string(&history_path).unwrap_or_default();
            history.push_str(&status);
            if history.len() > 64 * 1024 {
                let keep_from = history.len() - 64 * 1024;
                let boundary = history[keep_from..]
                    .find('\n')
                    .map(|offset| keep_from + offset + 1)
                    .unwrap_or(keep_from);
                history.drain(..boundary);
            }
            let _ = std::fs::write(history_path, history);
            // Preserve one bounded in-world observation because normal menu
            // teardown intentionally clears the candidate runtime.
            if retain_as_last_world
                && frame.background.enabled
                && !matches!(candidate_state, "unavailable" | "runtime-unavailable")
            {
                let _ = std::fs::write(
                    dir.join("selected-source-admission-last-world.json"),
                    status,
                );
            }
        }
    }
}

/// Bounded per-writer execution receipt paired with
/// `source_material_semantics_json`. Producer gates use this only to prove
/// that the corresponding Rust-owned source writer executed; it contains no
/// pipeline, descriptor, or backend identity.
pub(crate) fn source_material_execution_json(stats: &WorldPrimitiveSubmitStats) -> String {
    let cloud_face_disposition = if stats.source_cloud_faces_suppressed {
        "suppressed"
    } else if stats.source_cloud_quad_count > 0 {
        "draw"
    } else {
        "not-present"
    };
    format!(
        concat!(
            "{{\"textured\":{{\"batches\":{},\"quads\":{},\"draws\":{},\"vertices\":{},\"entity_model_quads\":{}}},",
            "\"weather\":{{\"batches\":{},\"quads\":{},\"draws\":{},\"vertices\":{}}},",
            "\"clouds\":{{\"batches\":{},\"quads\":{},\"draws\":{},\"vertices\":{},\"suppressed_quads\":{},\"face_disposition\":\"{}\",\"fullscreen_cloud_stages\":{}}}}}"
        ),
        stats.source_textured_material_batch_count,
        stats.source_textured_material_quad_count,
        stats.source_textured_material_draw_count,
        stats.source_textured_material_vertex_count,
        stats.source_entity_model_quad_count,
        stats.source_weather_batch_count,
        stats.source_weather_quad_count,
        stats.source_weather_draw_count,
        stats.source_weather_vertex_count,
        stats.source_cloud_batch_count,
        stats.source_cloud_quad_count,
        stats.source_cloud_draw_count,
        stats.source_cloud_vertex_count,
        stats.source_cloud_suppressed_quad_count,
        cloud_face_disposition,
        stats.source_cloud_fullscreen_stage_count,
    )
}

/// Bounded GUI execution receipt for selected-source captures. This exposes
/// only semantic GUI work that the Rust frontend accepted into the final owned
/// overlay target; it contains no backend image or binding identity.
pub(crate) fn gui_execution_json(stats: &GuiSubmitStats) -> String {
    format!(
        concat!(
            "{{\"sprites\":{},\"affine_quads\":{},\"sprite_batches\":{},",
            "\"cache_hits\":{},\"cache_misses\":{},\"resource_creates\":{},",
            "\"command_ops\":{}}}"
        ),
        stats.sprite_count,
        stats.affine_quad_count,
        stats.sprite_batch_count,
        stats.cache_hits,
        stats.cache_misses,
        stats.resource_creates,
        stats.command_ops,
    )
}

/// Bounded world-text receipt. Unlike generic semantic collection counters,
/// this is emitted only after the Rust frontend has planned its actual draws.
pub(crate) fn world_text_execution_json(stats: &WorldPrimitiveSubmitStats) -> String {
    let first_ndc_bounds = stats
        .world_text_first_ndc_bounds
        .map(|bounds| {
            format!(
                "[{:.6},{:.6},{:.6},{:.6}]",
                bounds[0], bounds[1], bounds[2], bounds[3]
            )
        })
        .unwrap_or_else(|| "null".to_owned());
    let first_ndc_corners = stats
        .world_text_first_ndc_corners
        .map(|corners| {
            format!(
                "[[{:.6},{:.6}],[{:.6},{:.6}],[{:.6},{:.6}],[{:.6},{:.6}]]",
                corners[0][0],
                corners[0][1],
                corners[1][0],
                corners[1][1],
                corners[2][0],
                corners[2][1],
                corners[3][0],
                corners[3][1],
            )
        })
        .unwrap_or_else(|| "null".to_owned());
    let ndc_bounds_sample = stats
        .world_text_ndc_bounds_sample
        .iter()
        .map(|bounds| {
            format!(
                "[{:.6},{:.6},{:.6},{:.6}]",
                bounds[0], bounds[1], bounds[2], bounds[3]
            )
        })
        .collect::<Vec<_>>()
        .join(",");
    format!(
        "{{\"quads\":{},\"batches\":{},\"draws\":{},\"clip_xy_visible_quads\":{},\"first_ndc_bounds\":{first_ndc_bounds},\"first_ndc_corners\":{first_ndc_corners},\"ndc_bounds_sample\":[{ndc_bounds_sample}]}}",
        stats.world_text_quad_count,
        stats.world_text_batch_count,
        stats.world_text_draw_count,
        stats.world_text_clip_xy_visible_quad_count,
    )
}

pub(crate) fn sky_fog_receipt_json(frame: &WorldPrimitiveFrame) -> GalResult<String> {
    let projection_inverse =
        invert_column_major_mat4(frame.projection_matrix, "terrain fog receipt projection")?;
    let fog = &frame.shader_environment;
    // The vanilla below-horizon disc is represented as eight ordinary
    // black material triangles. Record its semantic admission separately
    // from the sky fan so a visual wedge can be attributed without
    // inspecting Java renderer state or backend-private commands.
    let dark_disc_quad_count = frame
        .material_quads
        .iter()
        .filter(|quad| {
            quad.texture_id == WORLD_MATERIAL_TEXTURE_GENERATED_WHITE
                && quad.material_id == WORLD_MATERIAL_ID_OPAQUE_TEXTURED
                && quad.material_mode == WORLD_MATERIAL_MODE_OPAQUE
                && quad.depth_policy == WORLD_DEPTH_POLICY_DISABLED
                && quad.color_argb == 0xff00_0000
        })
        .count();
    Ok(format!(
        concat!(
            "{{\"frame_id\":{},\"enabled\":{},",
            "\"fog_color\":[{},{},{}],",
            "\"fog_parameter_color\":[{},{},{},{}],",
            "\"environmental_start\":{},\"environmental_end\":{},",
            "\"render_distance_start\":{},\"render_distance_end\":{},\"sky_end\":{},\"clouds_end\":{},",
            "\"background_clear_argb\":{},\"sky_disc_color_argb\":{},",
            "\"sky_dark_disc\":{},\"dark_disc_quad_count\":{},",
            "\"projection\":{},\"projection_inverse\":{}}}\n"
        ),
        frame.frame_id,
        fog.enabled,
        fog.fog_color[0],
        fog.fog_color[1],
        fog.fog_color[2],
        fog.fog_parameter_color[0],
        fog.fog_parameter_color[1],
        fog.fog_parameter_color[2],
        fog.fog_parameter_color[3],
        fog.fog_environmental_start,
        fog.fog_environmental_end,
        fog.fog_render_distance_start,
        fog.fog_render_distance_end,
        fog.fog_sky_end,
        fog.fog_clouds_end,
        frame.background.color_argb,
        frame.background.sky.sky_color_argb,
        frame.background.sky.dark_disc,
        dark_disc_quad_count,
        matrix4_json_array(frame.projection_matrix),
        matrix4_json_array(projection_inverse),
    ))
}

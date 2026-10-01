//! Observing the selected pack, arming source execution and admitting frames.

use super::*;

impl WorldPrimitiveFrontend {
    pub fn apply_shader_pack_source_update(
        &mut self,
        update: ShaderPackSourceUpdate,
    ) -> GalResult<()> {
        self.shader_pack_sources.apply_update(update)?;
        // Converted source streams embed the pack's block-state material
        // mapping; a new source generation must not reuse them.
        self.source_terrain_mesh_cache.clear();
        self.source_entity_mesh_cache.clear();
        self.validated_source_terrain_meshes.clear();
        self.source_terrain_validated_identities.clear();
        self.source_terrain_range_memo.clear();
        self.reset_candidate_source_occupancy_stability();
        self.source_execution_armed = false;
        self.source_execution_activation_reported = false;
        self.source_execution_distant_horizons_reported = false;
        self.observe_shader_pack_source_candidate();
        Ok(())
    }

    /// A prepared source mesh contains source-generation material identities.
    /// Retires final-copy bindings after a successfully decoded shader-pack
    /// source generation switch. The source store is updated first so an
    /// invalid update preserves the last valid generation and its cache;
    /// callers with GAL ownership invoke this only after that success.
    pub fn retire_source_final_outputs_for_shader_reload(&mut self, gal: &mut VulkanicGal) {
        self.source_final_output_cache.destroy(gal);
    }

    pub(crate) fn observe_shader_pack_source_candidate(&mut self) {
        self.observe_shader_pack_source_candidate_for_scope(TerrainProgramScope::Default);
    }

    pub(crate) fn observe_shader_pack_source_candidate_for_scope(&mut self, scope: TerrainProgramScope) {
        let Some(source) = self.shader_pack_sources.active() else {
            return;
        };
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.observe_source_candidate_for_scope(source, scope);
            runtime.observe_distant_horizons_source_candidate_for_scope(source, scope);
        }
    }

    #[cfg(test)]
    pub(crate) fn shader_pack_source_generation(&self) -> Option<u64> {
        self.shader_pack_sources.active_generation()
    }

    #[cfg(test)]
    pub(crate) fn shader_pack_source_candidate(
        &self,
    ) -> Option<&crate::render::shaderpack::runtime::TerrainSourceCandidateState> {
        self.shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::source_candidate)
    }

    #[cfg(test)]
    pub(crate) fn enable_candidate_subset_execution_for_test(&mut self) {
        assert!(
            !self.candidate_lowered_source_execution_enabled,
            "fixture and lowered selected-source terrain routes are mutually exclusive",
        );
        self.candidate_subset_execution_enabled = true;
        // This helper names an explicitly test-only selected-source fixture.
        // Production cannot reach this path; it still requires the runtime
        // admission gate. Keeping the arm here makes fixture tests exercise
        // their intended private source route rather than ordinary vanilla.
        self.source_execution_armed = true;
    }

    #[cfg(test)]
    pub(crate) fn enable_candidate_lowered_source_execution_for_test(&mut self) {
        assert!(
            !self.candidate_subset_execution_enabled,
            "fixture and lowered selected-source terrain routes are mutually exclusive",
        );
        self.candidate_lowered_source_execution_enabled = true;
    }

    #[cfg(test)]
    pub(crate) fn enable_candidate_source_preparation_for_test(&mut self) {
        self.candidate_source_preparation_enabled = true;
    }
}

impl WorldPrimitiveFrontend {
    /// Proves that a terrain mesh converts to the selected source stream,
    /// converting it only the first time an exact mesh generation is seen.
    pub(crate) fn validate_source_terrain_mesh(&mut self, mesh_key: u64, mesh_generation: u64) -> GalResult<()> {
        self.ensure_source_mesh_generation("terrain", mesh_key, mesh_generation)?;
        let material_ids = self
            .shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::candidate_runtime_block_state_material_ids)
            .is_some();
        let key = (mesh_key, mesh_generation, material_ids);
        if self.validated_source_terrain_meshes.contains(&key) {
            return Ok(());
        }
        self.source_terrain_mesh_asset(mesh_key, mesh_generation)?;
        if self.validated_source_terrain_meshes.len() >= WORLD_MAX_FRAME_MESH_INSTANCES {
            self.validated_source_terrain_meshes.clear();
        }
        self.validated_source_terrain_meshes.insert(key);
        Ok(())
    }

    pub(crate) fn validate_source_range_material_mode(
        sections: &[WorldMeshSection],
        section_indices: &[u32],
        expected_mode: u32,
        program_kind: Option<TerrainMaterialProgramKind>,
    ) -> GalResult<()> {
        for &section_index in section_indices {
            let section = &sections[section_index as usize];
            if section.material_mode != expected_mode {
                return Err(GalError::invalid_argument(format!(
                    "source terrain range selected section {} with material mode {} for {:?} program",
                    section_index, section.material_mode, program_kind
                )));
            }
        }
        Ok(())
    }

    pub(crate) fn ensure_shader_runtime(&mut self, gal: &mut VulkanicGal, generation: u64) -> GalResult<()> {
        let has_matching_runtime = self
            .shader_runtime
            .as_ref()
            .is_some_and(|runtime| runtime.generation() == generation);
        if has_matching_runtime {
            return Ok(());
        }
        if let Some(previous) = self.shader_runtime.take() {
            // The LOD pass caches resource sets that reference the runtime's
            // lightmap view. Runtime replacement owns that view's retirement,
            // so remove its consumers before destroying the previous runtime.
            self.lod_opaque_pass_resources.clear_lightmap_bindings(gal);
            self.lod_forward_opaque_pass_resources
                .clear_lightmap_bindings(gal);
            self.lod_exact_atlas_opaque_pass_resources
                .clear_bindings(gal);
            for resources in [
                self.lod_exact_atlas_forward_transparent_side_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_forward_transparent_up_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_forward_water_pass_resources.as_mut(),
                self.lod_exact_atlas_deferred_transparent_side_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_deferred_transparent_up_pass_resources
                    .as_mut(),
                self.lod_exact_atlas_deferred_water_pass_resources.as_mut(),
            ]
            .into_iter()
            .flatten()
            {
                resources.clear_bindings(gal);
            }
            self.lod_exact_atlas_source_pass_resources
                .clear_bindings(gal);
            self.lod_transparent_pass_resources
                .clear_lightmap_bindings(gal);
            self.lod_water_pass_resources.clear_lightmap_bindings(gal);
            self.lod_exact_atlas_source_pass_resources.destroy(gal);
            self.lod_source_pass_resources.destroy(gal);
            self.discard_distant_horizons_source_targets(gal);
            self.destroy_source_terrain_color_pass_targets(gal);
            self.destroy_lowered_source_terrain_pipeline_resources(gal);
            self.destroy_lowered_source_terrain_pack_resources(gal);
            self.destroy_lowered_entity_source_resources(gal);
            self.destroy_lowered_textured_material_source_resources(gal);
            self.destroy_lowered_source_terrain_resources(gal);
            self.destroy_lowered_source_terrain_program_layouts(gal);
            previous.destroy(gal)?;
        }
        self.candidate_colored_light_runtime = false;
        self.candidate_source_asset_runtime = false;
        self.candidate_source_asset_error = None;
        self.clear_candidate_source_resource_snapshot();
        self.shader_runtime = Some(ShaderPackRuntimeExecutor::terrain_material_multipass_v1(
            generation,
        )?);
        // Source updates are allowed to arrive before the world runtime is
        // needed. Replay the active immutable source into a newly created
        // runtime so source discovery is independent of that ordering.
        if let Some(source) = self.shader_pack_sources.active().cloned() {
            self.shader_runtime
                .as_mut()
                .expect("shader runtime was installed above")
                .observe_source_candidate(&source);
        }
        Ok(())
    }

    #[cfg(not(test))]
    pub(crate) fn candidate_subset_programs_for_frame(
        &self,
        _frame_id: u64,
    ) -> GalResult<Option<SourceTerrainPrograms>> {
        // Lowered shader-pack source execution is intentionally unavailable
        // in production until its full vertex and semantic resource interface
        // can be bound through explicit GAL layouts.
        Ok(None)
    }

    #[cfg(test)]
    pub(crate) fn candidate_subset_programs_for_frame(
        &self,
        frame_id: u64,
    ) -> GalResult<Option<SourceTerrainPrograms>> {
        if !self.candidate_subset_execution_enabled {
            return Ok(None);
        }
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "candidate terrain subset execution requires an initialized shader runtime",
            )
        })?;
        let opaque = runtime
            .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, frame_id)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "candidate terrain subset execution has no admitted opaque program",
                )
            })?;
        let cutout = runtime
            .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Cutout, frame_id)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "candidate terrain subset execution has no admitted cutout program",
                )
            })?;
        Ok(Some(SourceTerrainPrograms { opaque, cutout }))
    }

    #[cfg(not(test))]
    pub(crate) fn candidate_lowered_source_programs_for_frame(
        &self,
        world_generation: u64,
        frame_id: u64,
        frame_target: Handle,
    ) -> GalResult<Option<LoweredSourceTerrainPrograms>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "lowered selected-source terrain execution requires a non-zero world generation",
            ));
        }
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "lowered selected-source terrain execution requires an initialized shader runtime",
            )
        })?;
        let opaque = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted opaque program",
                )
            })?;
        let cutout = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted cutout program",
                )
            })?;
        let shadow = runtime
            .prepared_lowered_shadow_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted shadow program",
                )
            })?;
        let translucent = runtime
            .prepared_lowered_translucent_terrain_source_program()
            .ok()
            .flatten();
        let programs = LoweredSourceTerrainPrograms {
            opaque,
            cutout,
            shadow,
            translucent,
        };
        let shader_pack_generation = programs.shader_pack_generation()?;
        // This pre-graph lookup only proves the exact snapshot identity.  The
        // selected-frame planner adds owned depth/color/DH resources later in
        // the same transaction; requiring the admission blocker list here
        // would reject that frame before those resources can be merged.
        self.candidate_source_resource_snapshot_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
        )?;
        self.candidate_source_g_buffer_final_binding_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
            frame_target,
        )?;
        Ok(Some(programs))
    }

    #[cfg(test)]
    pub(crate) fn candidate_lowered_source_programs_for_frame(
        &self,
        world_generation: u64,
        frame_id: u64,
        frame_target: Handle,
    ) -> GalResult<Option<LoweredSourceTerrainPrograms>> {
        if !self.candidate_lowered_source_execution_enabled {
            return Ok(None);
        }
        if self.candidate_subset_execution_enabled {
            return Err(GalError::invalid_argument(
                "fixture and lowered selected-source terrain routes cannot execute together",
            ));
        }
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "lowered selected-source terrain execution requires a non-zero world generation",
            ));
        }
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "lowered selected-source terrain execution requires an initialized shader runtime",
            )
        })?;
        let opaque = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted opaque program",
                )
            })?;
        let cutout = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted cutout program",
                )
            })?;
        let shadow = runtime
            .prepared_lowered_shadow_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted shadow program",
                )
            })?;
        let translucent = runtime
            .prepared_lowered_translucent_terrain_source_program()
            .ok()
            .flatten();
        let programs = LoweredSourceTerrainPrograms {
            opaque,
            cutout,
            shadow,
            translucent,
        };
        let shader_pack_generation = programs.shader_pack_generation()?;
        self.candidate_source_resource_snapshot_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
        )?;
        self.candidate_source_g_buffer_final_binding_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
            frame_target,
        )?;
        Ok(Some(programs))
    }

    #[cfg(not(test))]
    pub(crate) fn candidate_lowered_source_execution_requested(&self) -> bool {
        // Production source execution follows the copied immutable shader
        // snapshot.  The route is still armed only after exact-frame resource,
        // uniform, program, and coverage validation; this gate must not make
        // the fully lowered Rust path test-only when an admitted pack exists.
        self.source_execution_enabled()
    }

    #[cfg(test)]
    pub(crate) fn candidate_lowered_source_execution_requested(&self) -> bool {
        self.candidate_lowered_source_execution_enabled
    }

    /// Source-resource preparation must be possible before the source route
    /// is armed. This remains separate from `runtime_source_execution_is_armed`:
    /// no preparation-only frame may replace the normal Rust vanilla or DH
    /// graph, submit an alternate presenter, or borrow Java/Iris state.
    #[cfg(not(test))]
    pub(crate) fn runtime_source_preparation_requested(&self) -> bool {
        self.source_execution_enabled()
    }

    #[cfg(test)]
    pub(crate) fn runtime_source_preparation_requested(&self) -> bool {
        self.candidate_source_preparation_enabled
            || self.candidate_lowered_source_execution_requested()
    }

    /// Pass dependencies required only by the selected source execution must
    /// not be created while the source is merely being prepared for admission.
    #[cfg(not(test))]
    pub(crate) fn runtime_source_execution_requested(&self) -> bool {
        self.source_execution_enabled()
    }

    #[cfg(test)]
    pub(crate) fn runtime_source_execution_requested(&self) -> bool {
        self.candidate_lowered_source_execution_requested()
    }

    /// Source execution uses the explicit runtime selector. The persistent
    /// occupancy cache tracks settling separately from the exact-frame source
    /// resource assembly, so streaming terrain does not indefinitely prevent
    /// a current coherent frame from being validated and selected.
    pub(crate) fn source_preparation_requires_stable_input(&self) -> bool {
        #[cfg(not(test))]
        {
            self.source_execution_enabled()
        }
        #[cfg(test)]
        {
            self.runtime_source_execution_requested()
        }
    }

    /// Exact-frame source snapshots are meaningful only after the entered
    /// world's background scope has installed a shader runtime. World entry
    /// can briefly expose a non-zero copied voxel generation before that
    /// scope arrives; treating that loading frame as source-preparation work
    /// would manufacture an internal ordering failure instead of waiting for
    /// the first real world frame.
    pub(crate) fn should_refresh_candidate_source_assets_for_frame(
        &self,
        frame: &WorldPrimitiveFrame,
    ) -> bool {
        frame.voxel_volume.world_generation != 0
            && self.runtime_source_preparation_requested()
            && self.shader_runtime.is_some()
        // Resource assembly records the exact current semantic frame. It
        // does not recreate the persistent volume, textures, or pipelines,
        // and every selected draw revalidates this frame-local snapshot.
        // Requiring a second identical visible terrain list here deadlocks
        // source admission while normal terrain streaming is active.
    }

    /// The source-derived route is a Rust-native route. Its current DH
    /// program can legally declare only reduced color/material-category
    /// inputs, so admission still waits for the exact source snapshot and
    /// frame contract. This is neither Java fallback nor Iris borrowing:
    /// both alternatives remain entirely Rust-owned.
    /// Source preparation is an explicit admission request, not an automatic
    /// consequence of observing a shader-pack snapshot. The lowered source
    /// route is still incomplete in production, so an active pack alone must
    /// not retain a second copy of every streamed mesh or begin private source
    /// resource preparation. The admission coordinator sets this signal only
    /// when it explicitly requests the route; an absent signal keeps the
    /// unfinished capability unavailable.
    /// Shader-pack execution follows the game configuration: it is enabled
    /// whenever the copied source is a real selected pack (Java stages the
    /// empty `disabled` snapshot when shaders are off). The environment
    /// variable is only a testing override (`1` forces on, anything else off).
    #[cfg(not(test))]
    pub(crate) fn source_execution_enabled(&self) -> bool {
        Self::source_execution_decision(
            std::env::var("MATTMC_RUST_SELECTED_SOURCE_EXECUTION")
                .ok()
                .as_deref(),
            self.shader_pack_sources.active(),
        )
    }

    pub(crate) fn source_execution_decision(
        override_value: Option<&str>,
        active: Option<&crate::render::shaderpack::source::ShaderPackSource>,
    ) -> bool {
        match override_value {
            Some(configured) => Self::selected_source_execution_env_enabled(Some(configured)),
            None => active.is_some_and(|source| {
                !source.is_empty()
                    && source.name() != "disabled"
                    && !source.name().starts_with("minecraft-resource-pack:")
            }),
        }
    }

    #[cfg(test)]
    pub(crate) fn runtime_source_execution_enabled() -> bool {
        false
    }

    #[cfg(test)]
    pub(crate) fn source_execution_enabled(&self) -> bool {
        Self::runtime_source_execution_enabled()
    }

    pub(crate) fn selected_source_execution_env_enabled(value: Option<&str>) -> bool {
        matches!(
            value.map(str::trim),
            Some("1")
                | Some("true")
                | Some("TRUE")
                | Some("yes")
                | Some("YES")
                | Some("on")
                | Some("ON")
        )
    }

    /// Source semantic retention is an explicit execution dependency, not a
    /// speculative cache.  When false, later source preparation remains
    /// unavailable until the normal semantic asset publisher supplies the
    /// next generation; it cannot reach back into Java/Iris state.
    pub(crate) fn should_retain_source_semantics(source_execution_enabled: bool) -> bool {
        source_execution_enabled
    }

    /// A mesh-generation update invalidates the assembled resource snapshot,
    /// not a previously proven source-route choice. The selected executor
    /// rebuilds and validates a new exact-frame snapshot before it draws, so
    /// preserving this arm cannot make stale mesh data executable.
    pub(crate) fn preserve_source_execution_arm_after_mesh_update(
        source_execution_armed: bool,
        source_execution_enabled: bool,
    ) -> bool {
        source_execution_armed && source_execution_enabled
    }

    /// Source admission is a conjunction over the exact semantic resource
    /// snapshot. Any later preparation step that finds a missing role must
    /// invalidate a prior arm immediately; a subsequent frame may re-arm only
    /// after rebuilding a complete, generation-coherent snapshot.
    pub(crate) fn set_candidate_source_missing_resource_roles(
        &mut self,
        missing_roles: Vec<TerrainSourceResourceRole>,
    ) {
        self.candidate_source_missing_resource_roles = missing_roles;
        if !self.candidate_source_missing_resource_roles.is_empty() {
            self.source_execution_armed = false;
            self.source_execution_activation_reported = false;
            self.source_execution_distant_horizons_reported = false;
        }
    }

    /// Verifies the exact scalar ABI for every writer and fullscreen consumer
    /// before the selected source route replaces the internal Rust graph.
    /// Discovery can prove that a name is semantic without proving this frame
    /// carries its value (for example DH matrices required by a pack-wide
    /// fullscreen branch). Keep that distinction at admission so an absent
    /// semantic frame cannot become a submit-time client failure.
    pub(crate) fn validate_selected_source_frame_uniforms(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<()> {
        let base_uniforms = self.source_uniform_frame_for_owned_resources(frame)?;
        let mut opaque_uniforms = base_uniforms.clone();
        opaque_uniforms.render_stage =
            Some(self.source_render_stage_for_material_mode(WORLD_MATERIAL_MODE_OPAQUE)?);
        let mut cutout_uniforms = base_uniforms.clone();
        cutout_uniforms.render_stage =
            Some(self.source_render_stage_for_material_mode(WORLD_MATERIAL_MODE_CUTOUT)?);
        let mut shadow_uniforms = base_uniforms.clone();
        shadow_uniforms.render_stage = Some(self.source_shadow_render_stage()?);
        let distant_uniforms = source_frame_includes_distant_horizons(frame)
            .then(|| self.source_uniform_frame_for_distant_horizons(frame))
            .transpose()?;

        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected source scalar admission requires an initialized shader runtime",
            )
        })?;
        let opaque = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source scalar admission has no admitted opaque terrain program",
                )
            })?;
        opaque.pack_scalar_uniforms(&opaque_uniforms)?;
        let cutout = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source scalar admission has no admitted cutout terrain program",
                )
            })?;
        cutout.pack_scalar_uniforms(&cutout_uniforms)?;
        let shadow = runtime
            .prepared_lowered_shadow_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source scalar admission has no admitted shadow program",
                )
            })?;
        shadow.pack_scalar_uniforms(&shadow_uniforms)?;
        if source_sky_initializer_requested(frame)
            && runtime
                .prepared_lowered_pre_terrain_celestial_program()?
                .is_some()
        {
            let end_sky = frame_has_end_sky_quads(frame);
            if !self.source_celestial_assets_available(end_sky) {
                return Err(GalError::unsupported_feature(if end_sky {
                    "selected source End sky requires a Rust-owned End sky texture asset"
                } else {
                    "selected source sky requires Rust-owned sun and moon texture assets"
                }));
            }
            let draws: &[i32] = if end_sky {
                &[SOURCE_CELESTIAL_END_SKY]
            } else {
                &[SOURCE_CELESTIAL_SUN, SOURCE_CELESTIAL_MOON]
            };
            for &celestial in draws {
                let mut celestial_uniforms = base_uniforms.clone();
                celestial_uniforms.render_stage =
                    Some(self.source_celestial_render_stage(celestial)?);
                celestial_uniforms.celestial_is_moon = Some(celestial);
                celestial_uniforms.celestial_alpha = Some(if celestial == SOURCE_CELESTIAL_END_SKY {
                    1.0
                } else {
                    frame.background.sky.rain_brightness
                });
                celestial_uniforms.moon_phase = Some(frame.background.sky.moon_phase);
                runtime
                    .prepared_lowered_pre_terrain_celestial_program()?
                    .expect("checked source celestial program presence")
                    .pack_scalar_uniforms(&celestial_uniforms)?;
            }
        }
        let textured_batches = source_textured_material_batches(frame)?;
        require_particle_group_semantics(frame)?;
        let weather_batches = source_weather_material_batches(frame)?;
        let cloud_batches = source_cloud_material_batches(frame)?;
        let clouds_suppressed = runtime.suppresses_vanilla_cloud_faces();
        if !textured_batches.is_empty() {
            let material = runtime
                .prepared_lowered_textured_material_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source scalar admission has material work but no admitted gbuffers_textured program",
                    )
                })?;
            for batch in textured_batches {
                let mut material_uniforms = base_uniforms.clone();
                material_uniforms.render_stage =
                    Some(self.source_render_stage_for_material_mode(batch.material_mode)?);
                material.pack_scalar_uniforms(&material_uniforms)?;
            }
        }
        if !weather_batches.is_empty() {
            let weather = runtime
                .prepared_lowered_weather_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source scalar admission has weather work but no admitted gbuffers_weather program",
                    )
                })?;
            let weather_program = weather.material_stream_program();
            for _ in weather_batches {
                let mut weather_uniforms = base_uniforms.clone();
                weather_uniforms.render_stage = Some(self.source_weather_render_stage()?);
                weather_program.pack_scalar_uniforms(&weather_uniforms)?;
            }
        }
        if !cloud_batches.is_empty() && !clouds_suppressed {
            let clouds = runtime
                .prepared_lowered_cloud_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source scalar admission has cloud work but no admitted gbuffers_clouds program",
                    )
                })?;
            let cloud_program = clouds.material_stream_program();
            for _ in cloud_batches {
                let mut cloud_uniforms = base_uniforms.clone();
                cloud_uniforms.render_stage = Some(self.source_cloud_render_stage()?);
                cloud_program.pack_scalar_uniforms(&cloud_uniforms)?;
            }
        }
        let mut fullscreen_uniforms = base_uniforms.clone();
        let fullscreen_programs = if source_frame_includes_distant_horizons(frame) {
            apply_distant_horizons_fullscreen_projection(
                &mut fullscreen_uniforms,
                &frame.lod_render_frame,
            )?;
            runtime.prepared_lowered_distant_horizons_post_terrain_fullscreen_programs()?
        } else {
            runtime.prepared_lowered_post_terrain_fullscreen_programs()?
        };
        for program in fullscreen_programs {
            program.pack_scalar_uniforms(&fullscreen_uniforms)?;
        }
        if let Some(distant_uniforms) = distant_uniforms.as_ref() {
            let distant = runtime
                .prepared_lowered_distant_horizons_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source scalar admission has no admitted Distant Horizons program",
                    )
                })?;
            distant.pack_scalar_uniforms(distant_uniforms)?;
        }
        Ok(())
    }

    /// A complete source plan must be prepared and confirmed by prior Rust
    /// frames before it can replace the internal Rust graph. In particular,
    /// source discovery alone is never enough to arm the route.
    pub(crate) fn arm_runtime_source_execution_if_ready(&mut self, frame: &WorldPrimitiveFrame) {
        self.arm_runtime_source_execution_if_ready_inner(frame);
        // Keep the decision for the user-facing route report: mesh updates
        // clear the snapshot (and its reason) before the next frame reports.
        self.last_admission_decision = self
            .candidate_source_asset_error
            .clone()
            .or_else(|| self.source_execution_admission_reason.clone());
    }

    pub(crate) fn arm_runtime_source_execution_if_ready_inner(&mut self, frame: &WorldPrimitiveFrame) {
        // Admission is exact-frame state. A prior frame may have been complete
        // while the current one gained a DH layer, changed a source target, or
        // otherwise lost a required role. Never carry that old decision into
        // the next frame: only the checks below may re-arm the source route.
        self.source_execution_armed = false;
        self.source_execution_activation_reported = false;
        self.source_execution_distant_horizons_reported = false;
        self.source_execution_admission_reason = None;
        if !self.source_execution_enabled() {
            self.source_execution_admission_reason =
                Some("runtime-selected-source-opt-in-disabled".to_string());
            return;
        }
        if frame.voxel_volume.world_generation == 0 {
            self.source_execution_admission_reason =
                Some("world-generation-unavailable".to_string());
            return;
        }
        if !frame.background.enabled {
            self.source_execution_admission_reason =
                Some("world-background-unavailable".to_string());
            return;
        }
        if !self.candidate_source_missing_resource_roles.is_empty() {
            self.source_execution_admission_reason = Some(format!(
                "source-resource-roles-incomplete:{}",
                self.candidate_source_missing_resource_roles
                    .iter()
                    .map(|role| format!("{role:?}"))
                    .collect::<Vec<_>>()
                    .join(",")
            ));
            return;
        }
        let Some(snapshot) = self.candidate_source_resource_snapshot.as_ref() else {
            self.source_execution_admission_reason =
                Some("source-resource-snapshot-unavailable".to_string());
            return;
        };
        if snapshot.world_generation != frame.voxel_volume.world_generation
            || snapshot.frame_id != frame.frame_id
        {
            self.source_execution_admission_reason = Some(format!(
                "source-resource-snapshot-mismatch:world={}/{}:frame={}/{}",
                snapshot.world_generation,
                frame.voxel_volume.world_generation,
                snapshot.frame_id,
                frame.frame_id
            ));
            return;
        }
        if let Err(error) = self.validate_selected_source_frame_coverage(frame) {
            // A selected source frame has no Java fallback. Malformed,
            // unclassified, or unported work remains an admission error so
            // the private route cannot present an incomplete game frame.
            self.source_execution_armed = false;
            self.candidate_source_asset_error = Some(format!(
                "selected-source frame coverage is incomplete: {error}"
            ));
            self.source_execution_admission_reason =
                Some(format!("selected-source-frame-coverage-incomplete:{error}"));
            return;
        }
        let scalar_error = self.validate_selected_source_frame_uniforms(frame).err();
        let terrain_program_result = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected source route has no initialized shader runtime",
                )
            })
            .and_then(|runtime| {
                runtime
                    .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
                    .and_then(|opaque| {
                        opaque.ok_or_else(|| {
                            GalError::unsupported_feature(
                                "selected source route has no admitted opaque terrain program",
                            )
                        })
                    })
                    .and_then(|_| {
                        runtime
                            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)
                            .and_then(|cutout| {
                                cutout.ok_or_else(|| {
                                    GalError::unsupported_feature(
                                        "selected source route has no admitted cutout terrain program",
                                    )
                                })
                            })
                    })
                    .and_then(|_| {
                        runtime.prepared_lowered_shadow_source_program().and_then(|shadow| {
                            shadow.ok_or_else(|| {
                                GalError::unsupported_feature(
                                    "selected source route has no admitted shadow program",
                                )
                            })
                        })
                    })
            });
        let terrain_program_error = terrain_program_result
            .as_ref()
            .err()
            .map(ToString::to_string);
        let terrain_ready = scalar_error.is_none() && terrain_program_error.is_none();
        self.source_execution_armed = terrain_ready;
        if terrain_ready {
            self.candidate_source_asset_error = None;
            self.source_execution_admission_reason = Some("source-execution-armed".to_string());
        } else if let Some(error) = scalar_error {
            self.candidate_source_asset_error = Some(format!(
                "selected-source scalar inputs are incomplete: {error}"
            ));
            self.source_execution_admission_reason =
                Some(format!("selected-source-scalar-inputs-incomplete:{error}"));
        } else if let Some(error) = terrain_program_error {
            self.source_execution_admission_reason =
                Some(format!("selected-source-programs-incomplete:{error}"));
        }
    }

    /// The selected-source executor owns only the source-derived terrain/DH
    /// writers, the pack fullscreen chain, and explicit Rust GUI work.
    /// A frame carrying a Java feature family without a source writer is not
    /// admissible: presenting it would silently omit visible work. Keep the
    /// source route unarmed until that family has a real semantic writer.
    pub(crate) fn validate_selected_source_frame_coverage(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<()> {
        if let Some((mesh_key, stratum, identity)) =
            frame.mesh_instances.iter().find_map(|instance| {
                self.mesh_assets.get(&instance.mesh_key).and_then(|asset| {
                    (source_mesh_layout_has_shader_semantics(asset.vertex_layout_version)
                        && asset.source_input.is_none())
                    .then(|| (instance.mesh_key, instance.stratum, asset.entity_identity.clone()))
                })
            })
        {
            // Uploaded while shaders were off; the rebuild requested when the
            // source route activated will resend it with source semantics.
            return Err(GalError::unsupported_feature(format!(
                "visible mesh {mesh_key} (stratum {stratum}, identity '{identity}') has no retained source semantics yet"
            )));
        }
        if frame_has_distant_horizons_generic_objects(frame) {
            // Iris draws DH generic objects (LOD clouds, beacon beams) with the
            // pack's dh_generic/dh_terrain program; Rust draws them in the
            // selected-source DH pass. That pass exists only with DH LODs, and
            // only the compact box stream is expressible there.
            if frame
                .material_quads
                .iter()
                .any(|quad| is_distant_horizons_generic_stratum(quad.stratum))
            {
                return Err(GalError::unsupported_feature(
                    "selected source frame contains DH generic material quads without a source writer",
                ));
            }
            if !source_frame_includes_distant_horizons(frame) {
                return Err(GalError::unsupported_feature(
                    "selected source frame has DH generic objects but no DH LOD pass yet",
                ));
            }
            validate_distant_horizons_generic_source_boxes(&frame.dh_generic_boxes)?;
        }
        let unsupported_families = frame.feature_coverage.unsupported_families();
        if !unsupported_families.is_empty() {
            let families = unsupported_families
                .into_iter()
                .map(|(family, count)| format!("{family}={count}"))
                .collect::<Vec<_>>()
                .join(",");
            return Err(GalError::unsupported_feature(format!(
                "selected source frame contains unported feature families: {families}"
            )));
        }
        if source_sky_initializer_requested(frame) {
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source frame has visible sky semantics but no Rust shader runtime",
                )
            })?;
            let has_sky_writer = runtime
                .prepared_lowered_pre_terrain_sky_program()?
                .is_some();
            let has_celestial_writer = runtime
                .prepared_lowered_pre_terrain_celestial_program()?
                .is_some();
            if !has_sky_writer && !has_celestial_writer {
                return Err(GalError::unsupported_feature(
                    "selected source frame has visible sky semantics but no Rust-owned sky or celestial writer",
                ));
            }
        }
        if frame.background.enabled
            && frame.background.sky.visible
            && frame.background.sky_type == WORLD_BACKGROUND_SKY_CUSTOM
        {
            return Err(GalError::unsupported_feature(
                "selected source frame has visible custom-dimension sky semantics without an explicit Rust dimension mapping",
            ));
        }
        let textured_material_quad_count = frame
            .material_quads
            .iter()
            .filter(|quad| {
                matches!(
                    quad.source_program,
                    WORLD_MATERIAL_SOURCE_TEXTURED | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                )
            })
            .count();
        if frame.feature_coverage.shadow_submits != 0 && textured_material_quad_count == 0 {
            return Err(GalError::unsupported_feature(
                "selected source frame contains entity shadows without Rust-owned textured material quads",
            ));
        }
        if frame.feature_coverage.flame_submits != 0 && textured_material_quad_count == 0 {
            return Err(GalError::unsupported_feature(
                "selected source frame contains entity flames without Rust-owned textured material quads",
            ));
        }
        if frame.feature_coverage.leash_submits != 0 && textured_material_quad_count == 0 {
            return Err(GalError::unsupported_feature(
                "selected source frame contains entity leashes without Rust-owned textured material quads",
            ));
        }
        let reported_world_text = frame
            .feature_coverage
            .name_tag_submits
            .saturating_add(frame.feature_coverage.text_submits);
        if reported_world_text != 0 && frame.text_quads.is_empty() {
            return Err(GalError::unsupported_feature(
                "selected source frame reports world text work without Rust-owned semantic text quads",
            ));
        }
        if !frame.text_quads.is_empty() {
            // Validate the copied glyph/image contract while arming this
            // exact source frame. Otherwise a missing image or malformed quad
            // would be discovered only after source resources and commands
            // had already been staged, weakening the fail-closed admission
            // boundary for world text.
            self.world_text.prepare_frame(&features::world_text::WorldTextFrame {
                quads: frame.text_quads.clone(),
            })?;
        }
        for (index, quad) in frame.material_quads.iter().enumerate() {
            if !matches!(
                quad.source_program,
                WORLD_MATERIAL_SOURCE_TEXTURED
                    | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                    | WORLD_MATERIAL_SOURCE_PARTICLES
                    | WORLD_MATERIAL_SOURCE_WEATHER
                    | WORLD_MATERIAL_SOURCE_CLOUDS
            ) {
                return Err(GalError::unsupported_feature(format!(
                    "selected source frame material quad {index} is not classified for a source-derived material program"
                )));
            }
        }
        if frame
            .mesh_instances
            .iter()
            .any(|instance| self.is_crumbling_mesh_instance(instance))
        {
            // Block-breaking progress needs the pack's own damagedblock writer;
            // a pack without one stays unadmitted for this frame.
            let scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source block-breaking progress has no source program scope",
                    )
                })?;
            let generation = self
                .shader_pack_sources
                .active()
                .map(|source| source.generation())
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source block-breaking progress has no active shader pack",
                    )
                })?;
            self.damaged_block_source_program(generation, scope)?;
        }
        let textured_batches = source_textured_material_batches(frame)?;
        require_particle_group_semantics(frame)?;
        let weather_batches = source_weather_material_batches(frame)?;
        let cloud_batches = source_cloud_material_batches(frame)?;
        let clouds_suppressed = self
            .shader_runtime
            .as_ref()
            .is_some_and(ShaderPackRuntimeExecutor::suppresses_vanilla_cloud_faces);
        if clouds_suppressed && !cloud_batches.is_empty() {
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source suppresses vanilla cloud faces but has no Rust shader runtime",
                )
            })?;
            let fullscreen_programs = if source_frame_includes_distant_horizons(frame) {
                runtime.prepared_lowered_distant_horizons_post_terrain_fullscreen_programs()?
            } else {
                runtime.prepared_lowered_post_terrain_fullscreen_programs()?
            };
            let has_cloud_writer = fullscreen_programs.iter().any(|program| {
                program.source_stage_path.ends_with("deferred1.fsh")
                    && program.fragment.source.contains("GetClouds(")
            });
            if !has_cloud_writer {
                return Err(GalError::unsupported_feature(
                    "selected source suppresses vanilla cloud faces but has no Rust-owned fullscreen cloud writer",
                ));
            }
        }
        if !textured_batches.is_empty() {
            let staged = stage_source_material_primitives_for_indices(
                frame,
                textured_batches.iter().flat_map(|batch| batch.indices()),
            )?;
            let runtime = self
                .shader_runtime
                .as_ref()
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source frame has source-textured material work but no Rust shader runtime",
                    )
                })?;
            let program = runtime
                .prepared_lowered_textured_material_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source frame has source-textured material work but no lowered gbuffers_textured writer",
                    )
                })?;
            // This validates the semantic material stream and source program
            // ABI at admission time. Per-batch atlas/local resource binding
            // is staged in the one combined submission below, where a
            // generation or resource failure remains explicit.
            program.pack_material_primitives(&staged)?;
        }
        if !weather_batches.is_empty() {
            let staged = stage_source_material_primitives_for_indices(
                frame,
                weather_batches.iter().flat_map(|batch| batch.indices()),
            )?;
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source frame has weather work but no Rust shader runtime",
                )
            })?;
            let weather = runtime
                .prepared_lowered_weather_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source frame has weather work but no lowered gbuffers_weather writer",
                    )
                })?;
            weather
                .material_stream_program()
                .pack_material_primitives(&staged)?;
        }
        if !cloud_batches.is_empty() && !clouds_suppressed {
            let staged = stage_source_material_primitives_for_indices(
                frame,
                cloud_batches.iter().flat_map(|batch| batch.indices()),
            )?;
            let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source frame has cloud work but no Rust shader runtime",
                )
            })?;
            let clouds = runtime
                .prepared_lowered_cloud_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source frame has cloud work but no lowered gbuffers_clouds writer",
                    )
                })?;
            clouds
                .material_stream_program()
                .pack_material_primitives(&staged)?;
        }
        if !frame.segments.is_empty() {
            // Block-selection outlines have no fallback: the pack's
            // `gbuffers_line` must lower before the frame is admitted.
            let scope = terrain_program_scope_for_sky_type(frame.background.sky_type)?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source block outline has no source program scope",
                    )
                })?;
            let generation = self
                .shader_pack_sources
                .active()
                .map(|source| source.generation())
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source block outline requires an active shader pack",
                    )
                })?;
            self.line_source_program(generation, scope)?;
        }
        self.validate_source_first_person_meshes_for_frame(frame)?;
        for instance in &frame.mesh_instances {
            // Outline-only instances are consumed exclusively by the
            // dedicated entity-mask planner. They must retain their copied
            // mesh identity for that planner, but must not require a source
            // entity material program or enter source terrain/entity draws.
            if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0 {
                continue;
            }
            if !is_source_terrain_mesh_stratum(instance.stratum)
                && instance.stratum != WORLD_STRATUM_ENTITY_MESH
                && instance.stratum != WORLD_STRATUM_ENTITY_SHADOW_CASTER
            {
                return Err(GalError::unsupported_feature(format!(
                    "selected source frame has mesh {} generation {} in stratum {}; it is outside the indexed source terrain/entity material families",
                    instance.mesh_key, instance.mesh_generation, instance.stratum
                )));
            }
        }
        self.validate_source_meshes_for_frame(frame)?;
        // DH is part of the same one-presenter source frame. Do not arm the
        // route until its exact visible semantic layers have source programs;
        // later preparation must never discover this only after another
        // source writer has staged resources.
        self.validate_source_distant_horizons_for_frame(frame)
    }

    /// Validates the dedicated first-person semantic stream before a selected
    /// source frame is armed. Hand meshes never join ordinary entity coverage:
    /// they require copied hand matrices, a distinct source program, and the
    /// separate depth domain staged by the combined planner.
    pub(crate) fn validate_source_first_person_meshes_for_frame(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<()> {
        let requested_hand_items = usize::from(
            !frame
                .shader_environment
                .main_hand_item_model_resource_location
                .is_empty(),
        ) + usize::from(
            !frame
                .shader_environment
                .off_hand_item_model_resource_location
                .is_empty(),
        );
        if frame.first_person.enabled
            && requested_hand_items != 0
            && frame.first_person_mesh_instances.len() < requested_hand_items
        {
            return Err(GalError::unsupported_feature(format!(
                "selected source frame requests {requested_hand_items} held-item hand model(s) but copied only {} Rust hand mesh record(s)",
                frame.first_person_mesh_instances.len()
            )));
        }
        if frame.first_person_mesh_instances.is_empty() {
            return Ok(());
        }
        if !frame.first_person.enabled {
            return Err(GalError::invalid_argument(
                "selected source frame has first-person meshes without an enabled first-person frame",
            ));
        }
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::unsupported_feature(
                "selected source frame has first-person work but no Rust shader runtime",
            )
        })?;
        runtime
            .prepared_lowered_hand_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source frame has first-person meshes but no lowered gbuffers_hand writer",
                )
            })?;
        let count = frame.first_person_mesh_instances.len();
        for (index, instance) in frame.first_person_mesh_instances.iter().enumerate() {
            validate_mesh_instance(instance, frame)?;
            if instance.stratum != WORLD_STRATUM_ENTITY_MESH {
                return Err(GalError::invalid_argument(
                    "selected source first-person mesh is outside the indexed entity material family",
                ));
            }
            if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0 {
                return Err(GalError::invalid_argument(
                    "selected source first-person mesh cannot be outline-only",
                ));
            }
            if instance.block_entity_id != -1 {
                return Err(GalError::invalid_argument(
                    "selected source first-person mesh cannot carry block-entity identity",
                ));
            }
            frame.first_person.hand_for_instance(index, count)?;
            let mesh = self.source_entity_mesh_asset(
                instance.mesh_key,
                instance.mesh_generation,
                instance.packed_light,
            )?;
            mesh.validate()?;
            let section_indices = if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                0..mesh.sections.len()
            } else {
                let section = usize::try_from(instance.mesh_section_index).map_err(|_| {
                    GalError::invalid_argument(
                        "selected source first-person section ordinal exceeds usize",
                    )
                })?;
                section..section.saturating_add(1)
            };
            for section_index in section_indices {
                let _section = mesh.sections.get(section_index).ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "selected source first-person mesh {} selects missing section {}",
                        mesh.mesh_key, section_index
                    ))
                })?;
            }
        }
        Ok(())
    }

    /// Validates DH source-stage admission from the same copied layer
    /// semantics consumed by the whole-frame frontend. This is intentionally
    /// a route-arming check, not a Java fallback policy: unsupported layers
    /// leave the selected-source route unarmed before it owns a frame.
    pub(crate) fn validate_source_distant_horizons_for_frame(
        &self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<()> {
        if frame.lod_instances.is_empty() {
            return Ok(());
        }
        if !frame.lod_render_frame.rust_route_selected() {
            return Err(GalError::invalid_argument(
                "selected source frame contains Distant Horizons instances without an explicit Rust route decision",
            ));
        }
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected source Distant Horizons admission requires an initialized shader runtime",
            )
        })?;
        let program = runtime
            .prepared_lowered_distant_horizons_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "selected source Distant Horizons admission has no opaque source program",
                )
            })?;
        // A DH source program may intentionally consume its native reduced
        // color/category stream. Complementary's `dh_terrain`, for example,
        // uses `gl_Color`, `dhMaterialId`, lightmap coordinates, and normals;
        // it does not sample the Minecraft atlas. Requiring atlas identity for
        // that source would reject the only semantically correct stream and
        // force the unrelated exact-atlas overlay path instead.
        //
        // Keep the stronger requirement for a future source program that
        // actually declares atlas-backed material sampling. That remains a
        // separate stream contract, never an inferred texture assignment.
        if matches!(
            program.execution_interface.material_identity_contract,
            DistantHorizonsMaterialIdentityContract::AtlasBacked
        ) && !program.has_exact_material_texture_identity()
        {
            return Err(GalError::unsupported_feature(
                "selected source Distant Horizons program declares atlas-backed material identity but its prepared resource bindings do not provide the material atlas",
            ));
        }

        let mut requires_late_translucent_program = false;
        for instance in &frame.lod_instances {
            match instance.layer {
                WORLD_LOD_LAYER_OPAQUE => {}
                WORLD_LOD_LAYER_TRANSPARENT_SIDE
                | WORLD_LOD_LAYER_TRANSPARENT_UP
                | WORLD_LOD_LAYER_TRANSPARENT_WATER_UP => {
                    requires_late_translucent_program = true;
                }
                layer => {
                    return Err(GalError::invalid_argument(format!(
                        "selected source Distant Horizons admission received unknown layer {layer}",
                    )));
                }
            }
        }
        if requires_late_translucent_program {
            runtime
                .prepared_lowered_distant_horizons_translucent_source_program()?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "selected source Distant Horizons admission has late translucent ranges but no admitted translucent source program",
                    )
                })?;
        }
        Ok(())
    }

    /// Validates the complete semantic mesh set that a selected source frame
    /// would consume. The source route has one Rust-owned presenter, so it
    /// cannot be armed for a frame that contains an unsupported mesh and then
    /// delegate that mesh to the ordinary graph or Java.
    pub(crate) fn validate_source_meshes_for_frame(&mut self, frame: &WorldPrimitiveFrame) -> GalResult<()> {
        let material_ids = self
            .shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::candidate_runtime_block_state_material_ids)
            .is_some();
        let pack_generation = self.shader_pack_sources.active().map(|source| source.generation());
        let is_terrain = |instance: &WorldMeshInstanceRequest| {
            instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY == 0
                && instance.stratum != WORLD_STRATUM_ENTITY_SHADOW_CASTER
                && instance.stratum != WORLD_STRATUM_ENTITY_MESH
        };
        let identities = frame
            .mesh_instances
            .iter()
            .filter(|instance| is_terrain(instance))
            .map(|instance| (instance.stratum, instance.mesh_key, instance.mesh_generation))
            .collect::<Vec<_>>();
        let stamp = (material_ids, pack_generation);
        if self.source_terrain_validated_stamp != Some(stamp) {
            self.source_terrain_validated_identities.clear();
            self.source_terrain_validated_stamp = Some(stamp);
        }
        let mut any_translucent = false;
        let mut seen = BTreeSet::new();
        // The translucent writer's availability is frame-invariant; resolve it
        // at most once instead of per translucent mesh.
        let mut translucent_stage_status: Option<Option<String>> = None;
        let mut entity_writer_checked = false;
        for instance in &frame.mesh_instances {
            validate_mesh_instance(instance, frame)?;
            if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0 {
                // The outline mask resolves this asset separately; it is not
                // a source-material mesh and therefore must not arm or
                // require the entity material program.
                continue;
            }
            let key = (
                instance.stratum,
                instance.mesh_key,
                instance.mesh_generation,
            );
            if !seen.insert(key) {
                continue;
            }
            if is_terrain(instance) {
                if let Some(translucent) = self.source_terrain_validated_identities.get(&key) {
                    any_translucent |= *translucent;
                    continue;
                }
            }
            if instance.stratum == WORLD_STRATUM_ENTITY_SHADOW_CASTER {
                // Shadow-only casters (Iris `shadowPlayer`) never reach a
                // colour writer; the shadow planner lowers them through the
                // pack's shadow stage. Only their copied mesh must exist.
                self.source_entity_mesh_asset(
                    instance.mesh_key,
                    instance.mesh_generation,
                    instance.packed_light,
                )?;
                continue;
            }
            if instance.stratum == WORLD_STRATUM_ENTITY_MESH {
                // The writer's availability is frame-invariant (and resolving
                // it clones the prepared program); check it once per frame.
                if !entity_writer_checked {
                    let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
                        GalError::unsupported_feature(
                            "selected source frame has entity work but no Rust shader runtime",
                        )
                    })?;
                    runtime
                        .prepared_lowered_entity_source_program()?
                        .ok_or_else(|| {
                            GalError::unsupported_feature(
                                "selected source frame has entity meshes but no lowered gbuffers_entities writer",
                            )
                        })?;
                    entity_writer_checked = true;
                }
                let mesh = self.source_entity_mesh_asset(
                    instance.mesh_key,
                    instance.mesh_generation,
                    instance.packed_light,
                )?;
                if mesh.sections.iter().any(|section| {
                    matches!(
                        section.material_mode,
                        WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                            | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
                    )
                }) {
                    return Err(GalError::unsupported_feature(
                        "selected source ordinary entity stream contains first-person optical stencil sections",
                    ));
                }
                continue;
            }
            self.validate_source_terrain_mesh(instance.mesh_key, instance.mesh_generation)?;
            // Scan without allocating; the section list is only needed for
            // the rejection message.
            let translucent_asset = self
                .mesh_assets
                .get(&instance.mesh_key)
                .filter(|asset| asset.mesh_generation == instance.mesh_generation)
                .filter(|asset| {
                    asset
                        .sections
                        .iter()
                        .any(|section| section.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT)
                });
            let is_translucent = translucent_asset.is_some();
            if let Some(asset) = translucent_asset {
                any_translucent = true;
                let stage_status = translucent_stage_status.get_or_insert_with(|| {
                    match self.shader_runtime.as_ref() {
                        Some(runtime) => {
                            match runtime.prepared_lowered_translucent_terrain_source_program() {
                                Ok(Some(_)) => None,
                                Ok(None) => Some(
                                    "translucent source contract has not been discovered"
                                        .to_string(),
                                ),
                                Err(reason) => Some(reason.to_string()),
                            }
                        }
                        None => Some("shader runtime is unavailable".to_string()),
                    }
                });
                let Some(stage_status) = stage_status else {
                    self.source_terrain_validated_identities.insert(key, true);
                    continue;
                };
                let translucent_sections = asset
                    .sections
                    .iter()
                    .enumerate()
                    .filter_map(|(index, section)| {
                        (section.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT).then_some(index)
                    })
                    .collect::<Vec<_>>();
                return Err(GalError::unsupported_feature(format!(
                    "selected source terrain mesh {} generation {} has unsupported translucent section ranges {:?}; {stage_status}",
                    instance.mesh_key, instance.mesh_generation, translucent_sections
                )));
            }
            if !is_translucent {
                self.source_terrain_validated_identities.insert(key, false);
            }
        }
        // Retained translucent meshes still need a translucent writer now.
        if any_translucent
            && !self.shader_runtime.as_ref().is_some_and(|runtime| {
                matches!(
                    runtime.prepared_lowered_translucent_terrain_source_program(),
                    Ok(Some(_))
                )
            })
        {
            return Err(GalError::unsupported_feature(
                "selected source terrain has translucent sections but no admitted translucent writer",
            ));
        }
        // Bound the cache to identities still visible.
        if self.source_terrain_validated_identities.len() > identities.len().saturating_mul(2).max(4096) {
            let visible = identities.iter().copied().collect::<std::collections::HashSet<_>>();
            self.source_terrain_validated_identities.retain(|key, _| visible.contains(key));
        }
        Ok(())
    }

    /// With a shader pack selected, a frame the shader route cannot run is
    /// drawn by the vanilla Rust renderer. Say so (and why) on stderr each
    /// time the outcome changes, so a fallback is never silent.
    /// A resized frame cannot reuse the armed source route's extent-bound
    /// resources. Disarm so this frame renders through the ordinary graph,
    /// which prepares resources at the new extent; admission re-arms later.
    pub(crate) fn disarm_source_route_on_extent_change(&mut self, frame: &WorldPrimitiveFrame) {
        let extent = (frame.viewport_width, frame.viewport_height);
        if self
            .last_source_route_extent
            .is_some_and(|previous| previous != extent)
        {
            self.source_execution_armed = false;
            self.source_execution_activation_reported = false;
            self.source_execution_distant_horizons_reported = false;
            self.last_admission_decision = Some(format!(
                "viewport resized to {}x{}; re-preparing extent-bound source resources",
                extent.0, extent.1
            ));
        }
        self.last_source_route_extent = Some(extent);
    }

    /// Admits an armed frame before any source work is recorded. A frame the
    /// shader route cannot cover (a producer or resource it does not model yet)
    /// disarms the route and is drawn by the vanilla Rust route instead, with
    /// the reason reported; it must never fail the whole frame. Packs that draw
    /// their own clouds drop vanilla cloud faces here, before validation.
    #[cfg(not(test))]
    pub(crate) fn admit_armed_source_frame(&mut self, mut frame: WorldPrimitiveFrame) -> WorldPrimitiveFrame {
        if !self.runtime_source_execution_is_armed() {
            return frame;
        }
        self.pre_dropped_suppressed_cloud_quads = 0;
        if self
            .shader_runtime
            .as_ref()
            .is_some_and(ShaderPackRuntimeExecutor::suppresses_vanilla_cloud_faces)
        {
            let before = frame.material_quads.len();
            frame
                .material_quads
                .retain(|quad| quad.source_program != WORLD_MATERIAL_SOURCE_CLOUDS);
            self.pre_dropped_suppressed_cloud_quads = (before - frame.material_quads.len()) as u64;
        }
        // A world/dimension change or resize leaves no confirmed DH depth
        // snapshot for the new identity until a DH source frame completes.
        // Draw such frames with the vanilla Rust route instead of failing the
        // selected submission (which crashed on dimension change).
        // Coverage validation runs first: it must see every frame.
        let coverage = self
            .validate_selected_source_frame_coverage(&frame)
            .and_then(|()| self.source_distant_depth_ready(&frame));
        match coverage {
            Ok(()) => self.coverage_validated_frame_id = Some(frame.frame_id),
            Err(error) => {
                self.source_execution_armed = false;
                self.coverage_validated_frame_id = None;
                self.candidate_source_asset_error = Some(format!(
                    "selected-source current-frame coverage is incomplete: {error}"
                ));
            }
        }
        frame
    }

    #[cfg(not(test))]
    pub(crate) fn report_shader_route_outcome(&mut self, frame: &WorldPrimitiveFrame) {
        let outcome = if !self.source_execution_enabled() {
            None
        } else if self.runtime_source_execution_is_armed() {
            Some("active".to_string())
        } else {
            let reason = self
                .candidate_source_asset_error
                .clone()
                .or_else(|| self.last_admission_decision.clone())
                .unwrap_or_else(|| "shader route is still preparing".to_string());
            Some(format!("vanilla fallback: {reason}"))
        };
        // Reasons embed frame-local numbers (mesh keys, generations); compare
        // their shape so one persistent cause is printed once, not per frame.
        let shape = |text: &Option<String>| {
            text.as_ref().map(|text| {
                text.chars()
                    .map(|character| if character.is_ascii_digit() { '#' } else { character })
                    .collect::<String>()
            })
        };
        if shape(&outcome) == shape(&self.last_reported_shader_route_outcome) {
            return;
        }
        match &outcome {
            Some(text) => eprintln!(
                "[MattMC shaders] frame {} shader route {}",
                frame.frame_id,
                text
            ),
            None if self.last_reported_shader_route_outcome.is_some() => {
                eprintln!("[MattMC shaders] frame {} shader route off", frame.frame_id)
            }
            None => {}
        }
        self.last_reported_shader_route_outcome = outcome;
    }

    #[cfg(not(test))]
    pub(crate) fn runtime_source_execution_is_armed(&self) -> bool {
        self.source_execution_enabled() && self.source_execution_armed
    }

    #[cfg(test)]
    pub(crate) fn runtime_source_execution_is_armed(&self) -> bool {
        self.candidate_lowered_source_execution_requested()
            || self.candidate_subset_execution_enabled
    }

    #[cfg(test)]
    pub(crate) fn shader_runtime_mut(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
    ) -> GalResult<&mut ShaderPackRuntimeExecutor> {
        self.ensure_shader_runtime(gal, generation)?;
        Ok(self
            .shader_runtime
            .as_mut()
            .expect("shader runtime is installed after successful initialization"))
    }

    pub(crate) fn shader_runtime_generation_for_submission(&self, submission_generation: u64) -> u64 {
        if self.generation == 0 {
            submission_generation
        } else {
            self.generation
        }
    }
}

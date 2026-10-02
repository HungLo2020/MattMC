//! Candidate source resources: snapshots, pack assets, material textures, G-buffer bindings and depth inputs.

use super::*;

/// The complete semantic source-resource assembly observed for one exact
/// gameplay frame. Resource handles remain Rust-owned GAL handles, while the
/// identity used to select this snapshot stays purely semantic: source-pack,
/// world, and frame generations. This is preparation data only and cannot
/// enable source-derived execution by itself.
#[derive(Clone, Debug)]
pub(crate) struct CandidateSourceResourceSnapshot {
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) resources: TerrainSourceOwnedResourceSet,
}

/// Exact final-target identity paired with one complete private source
/// resource snapshot. `frame_target` is a backend-neutral GAL handle; native
/// image/view details remain private to the backend.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct CandidateSourceFrameTargetSnapshot {
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) final_binding: GBufferFinalBindingKey,
}

impl WorldPrimitiveFrontend {
    pub(crate) fn ensure_candidate_source_assets_for_frame(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_counter: u64,
        allow_pending_colored_light: bool,
        includes_distant_horizons: bool,
    ) -> GalResult<bool> {
        let assets = match self.shader_pack_sources.active() {
            Some(source) => match self
                .shader_pack_assets
                .active_for_source(source.name(), source.generation())
            {
                Ok(assets) => assets,
                Err(error) => {
                    self.clear_candidate_source_asset_runtime(gal)?;
                    self.candidate_source_asset_error = Some(error.to_string());
                    return Ok(false);
                }
            },
            None => {
                self.clear_candidate_source_asset_runtime(gal)?;
                self.candidate_source_asset_error =
                    Some("shader-pack source has not been provided".to_string());
                return Ok(false);
            }
        };
        let asset_resources_started = std::time::Instant::now();
        let result = self
            .shader_runtime
            .as_mut()
            .expect("shader runtime is installed before source asset preparation")
            .ensure_candidate_source_asset_resources(gal, assets);
        whole_frame_phase_trace(
            "candidate-source-asset-resources",
            frame_counter,
            Some(asset_resources_started),
        );
        let asset_replaced = match result {
            Ok(replaced) => {
                self.candidate_source_asset_runtime = self
                    .shader_runtime
                    .as_ref()
                    .is_some_and(ShaderPackRuntimeExecutor::has_candidate_source_asset_resources);
                self.candidate_source_asset_error = None;
                replaced
            }
            Err(error) => {
                self.clear_candidate_source_asset_runtime(gal)?;
                self.candidate_source_asset_error = Some(error.to_string());
                return Ok(false);
            }
        };
        let material_resources_started = std::time::Instant::now();
        let material_sets =
            match self.ensure_candidate_source_material_texture_resources(gal, world_generation) {
                Ok(resources) => resources,
                Err(error) => {
                    self.clear_candidate_source_asset_runtime(gal)?;
                    self.candidate_source_asset_error = Some(error.to_string());
                    return Ok(false);
                }
            };
        whole_frame_phase_trace(
            "candidate-source-material-resources",
            frame_counter,
            Some(material_resources_started),
        );
        let prepared_started = std::time::Instant::now();
        let prepared = (|| -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
            let asset_set = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime is installed before source resource assembly")
                .candidate_source_asset_resource_set(world_generation)?;
            let runtime = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime is installed before source resource assembly");
            let voxel_set = if allow_pending_colored_light {
                runtime
                    .candidate_colored_light_resource_set_for_pending_submission(frame_counter)?
            } else {
                runtime.candidate_colored_light_resource_set(frame_counter)?
            };
            // A same-submission puddle upload is immediately usable by the
            // ordered source pass, but a stable frame must retain the already
            // confirmed field. The pending-only query previously dropped that
            // confirmed resource from every later exact-frame snapshot.
            let puddle_set = if allow_pending_colored_light {
                runtime
                    .candidate_puddle_resource_set_for_pending_submission()?
                    .or(runtime.candidate_puddle_resource_set()?)
            } else {
                runtime.candidate_puddle_resource_set()?
            };
            let lightmap_set =
                runtime.candidate_vanilla_lightmap_resource_set(allow_pending_colored_light)?;
            let shadow_set =
                self.ensure_candidate_source_shadow_depth_resources(gal, world_generation)?;
            let shadow_color_set =
                self.ensure_candidate_source_shadow_color_resources(gal, world_generation)?;
            let main_depth_set =
                self.ensure_candidate_source_main_depth_resources(gal, world_generation)?;
            let mut sets = Vec::with_capacity(8 + material_sets.len());
            if let Some(set) = asset_set.as_ref() {
                sets.push(set);
            }
            sets.extend(material_sets.iter());
            if let Some(set) = voxel_set.as_ref() {
                sets.push(set);
            }
            if let Some(set) = puddle_set.as_ref() {
                sets.push(set);
            }
            if let Some(set) = lightmap_set.as_ref() {
                sets.push(set);
            }
            if let Some(set) = shadow_set.as_ref() {
                sets.push(set);
            }
            if let Some(set) = shadow_color_set.as_ref() {
                sets.push(set);
            }
            if let Some(set) = main_depth_set.as_ref() {
                sets.push(set);
            }
            match sets.as_slice() {
                [] => Ok(None),
                [set] => Ok(Some((*set).clone())),
                _ => TerrainSourceOwnedResourceSet::merge(sets).map(Some),
            }
        })();
        let prepared = match prepared {
            Ok(prepared) => prepared,
            Err(error) => {
                self.clear_candidate_source_asset_runtime(gal)?;
                self.candidate_source_asset_error = Some(error.to_string());
                return Ok(false);
            }
        };
        whole_frame_phase_trace(
            "candidate-source-resource-merge",
            frame_counter,
            Some(prepared_started),
        );
        self.candidate_source_resource_role_count = prepared
            .as_ref()
            .map_or(0, TerrainSourceOwnedResourceSet::len);
        let missing_roles = self
            .shader_runtime
            .as_ref()
            .expect("shader runtime is installed before source resource diagnostics")
            .candidate_source_missing_resource_roles_for_frame(
                prepared.as_ref(),
                includes_distant_horizons,
            );
        // The base set precedes named color preparation in this same frame.
        // Record its provisional status without disarming a confirmed route;
        // the complete role check above or in the selected planner owns that
        // decision.
        self.candidate_source_missing_resource_roles = missing_roles;
        let shader_pack_generation = self
            .shader_runtime
            .as_ref()
            .expect("shader runtime is installed before source resource snapshot")
            .expected_shader_pack_generation_for_resources();
        self.candidate_source_frame_target_snapshot = None;
        self.candidate_source_resource_snapshot =
            prepared.map(|resources| CandidateSourceResourceSnapshot {
                shader_pack_generation,
                world_generation,
                frame_id: frame_counter,
                resources,
            });
        Ok(asset_replaced)
    }

    /// Stages the pack-declared color sampler family for source admission
    /// without selecting the source route. The target and sampler caches keep
    /// this generation pending until an eventual complete source submission
    /// confirms it; normal Rust vanilla and DH execution continue unchanged.
    pub(crate) fn prepare_candidate_source_color_resources_for_admission(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        extent: Extent3d,
        includes_distant_horizons: bool,
    ) -> GalResult<()> {
        match self.try_prepare_candidate_source_color_resources_for_admission(
            gal,
            world_generation,
            extent,
            includes_distant_horizons,
        ) {
            Ok(()) => Ok(()),
            Err(error) => {
                if let Some(runtime) = self.shader_runtime.as_mut() {
                    runtime.discard_source_color_targets_submission(gal);
                }
                self.candidate_source_asset_error =
                    Some(format!("source color target preparation failed: {error}"));
                Ok(())
            }
        }
    }

    /// Extends the exact-frame candidate snapshot with resources that are
    /// created by the selected-frame planner itself (depth history and, when
    /// present, DH depth).  Candidate admission is reported before those
    /// resources exist, so the missing-role set must be recomputed after the
    /// merge rather than carrying a stale loading-frame result into execution.
    pub(crate) fn merge_candidate_source_frame_resources(
        &mut self,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
        color_targets: &ShaderPackColorTargets,
        includes_distant_horizons: bool,
        additional: &[TerrainSourceOwnedResourceSet],
    ) -> GalResult<()> {
        self.append_candidate_source_frame_resources(
            shader_pack_generation,
            world_generation,
            frame_id,
            additional,
        )?;
        let snapshot = self
            .candidate_source_resource_snapshot
            .as_ref()
            .expect("candidate snapshot remains present after resource append");
        let missing_roles = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished while revalidating source resources")
            })?
            .candidate_source_missing_resource_roles_for_frame_with_declared_outputs(
                Some(&snapshot.resources),
                includes_distant_horizons,
                color_targets.declared_roles(),
            );
        self.set_candidate_source_missing_resource_roles(missing_roles);
        Ok(())
    }

    /// Appends Rust-owned frame resources before any source program asks the
    /// exact snapshot for semantic bindings. Admission is intentionally not
    /// recomputed here because declared color/DH roles are finalized later.
    pub(crate) fn append_candidate_source_frame_resources(
        &mut self,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
        additional: &[TerrainSourceOwnedResourceSet],
    ) -> GalResult<()> {
        let snapshot = self
            .candidate_source_resource_snapshot
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "selected source frame resources cannot extend an absent candidate snapshot",
                )
            })?;
        if snapshot.shader_pack_generation != shader_pack_generation
            || snapshot.world_generation != world_generation
            || snapshot.frame_id != frame_id
        {
            return Err(GalError::invalid_argument(
                "selected source frame resource extension does not match the candidate snapshot",
            ));
        }
        let mut merged = snapshot.resources.clone();
        for resources in additional {
            let unique = resources.excluding_roles_already_owned_by(&merged)?;
            if unique.len() != 0 {
                merged = TerrainSourceOwnedResourceSet::merge([&merged, &unique])?;
            }
        }
        snapshot.resources = merged;
        self.candidate_source_resource_role_count = snapshot.resources.len();
        Ok(())
    }

    pub(crate) fn try_prepare_candidate_source_color_resources_for_admission(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        extent: Extent3d,
        includes_distant_horizons: bool,
    ) -> GalResult<()> {
        if !self.runtime_source_preparation_requested() || world_generation == 0 {
            return Ok(());
        }
        let (targets, color_sets) = {
            let runtime = self.shader_runtime.as_mut().ok_or_else(|| {
                GalError::backend("shader runtime vanished before source color preparation")
            })?;
            let Some(targets) =
                runtime.stage_complete_source_color_targets(gal, world_generation, extent)?
            else {
                return Ok(());
            };
            let opaque = runtime
                .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)?;
            let cutout = runtime
                .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)?;
            let mut color_sets = Vec::with_capacity(2);
            if let Some(program) = opaque.as_ref() {
                color_sets
                    .push(runtime.stage_terrain_source_color_resources(gal, program, &targets)?);
            }
            if let Some(program) = cutout.as_ref() {
                color_sets
                    .push(runtime.stage_terrain_source_color_resources(gal, program, &targets)?);
            }
            (targets, color_sets)
        };
        let Some(snapshot) = self.candidate_source_resource_snapshot.as_mut() else {
            return Ok(());
        };
        if snapshot.world_generation != world_generation
            || snapshot.shader_pack_generation != targets.identity.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "candidate source color targets do not match the exact source resource snapshot generation",
            ));
        }

        let mut merged = snapshot.resources.clone();
        for colors in color_sets {
            let unique = colors.excluding_roles_already_owned_by(&merged)?;
            if unique.len() != 0 {
                merged = TerrainSourceOwnedResourceSet::merge([&merged, &unique])?;
            }
        }
        snapshot.resources = merged;
        self.candidate_source_resource_role_count = snapshot.resources.len();
        let missing_roles = self
            .shader_runtime
            .as_ref()
            .expect("shader runtime is installed while checking source color admission")
            .candidate_source_missing_resource_roles_for_frame_with_declared_outputs(
                Some(&snapshot.resources),
                includes_distant_horizons,
                targets.declared_roles(),
            );
        self.set_candidate_source_missing_resource_roles(missing_roles);
        Ok(())
    }

    /// Returns the complete private source-resource assembly only when it was
    /// observed for the exact source-pack, world, and frame identity. This
    /// preserves ping-pong parity and prevents a future source executor from
    /// binding a previous frame's shadow or voxel resources by accident.
    pub(crate) fn candidate_source_resources_for_frame(
        &self,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
    ) -> GalResult<&TerrainSourceOwnedResourceSet> {
        let snapshot = self.candidate_source_resource_snapshot_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
        )?;
        if !self.candidate_source_missing_resource_roles.is_empty() {
            let roles = self
                .candidate_source_missing_resource_roles
                .iter()
                .map(TerrainSourceResourceRole::semantic_name)
                .collect::<Vec<_>>()
                .join(", ");
            return Err(GalError::invalid_argument(format!(
                "source resource assembly is incomplete for the requested frame: {roles}"
            )));
        }
        Ok(&snapshot.resources)
    }

    /// Returns the exact-frame semantic subset needed by one lowered source
    /// program. This permits private preparation of an independently complete
    /// terrain or shadow stage while the whole source frame remains correctly
    /// unavailable because DH/fullscreen stages still lack resources.
    pub(crate) fn candidate_source_resources_for_program(
        &self,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredTerrainSourceProgram,
    ) -> GalResult<&TerrainSourceOwnedResourceSet> {
        let snapshot = self.candidate_source_resource_snapshot_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
        )?;
        program.require_semantic_resources(snapshot.resources.availability())?;
        Ok(&snapshot.resources)
    }

    /// Merges the exact-frame base resource snapshot with the program-local
    /// named color sampler table staged for one ordinary terrain source pass.
    /// The cache retains physical sampler ownership behind the shader runtime;
    /// this returns only a semantic table for pipeline/resource-set
    /// preparation. It deliberately does not update the global candidate
    /// readiness flag: a complete color transaction and every later source
    /// stage are still required before route admission.
    pub(crate) fn stage_candidate_source_resources_for_terrain_program(
        &mut self,
        gal: &mut VulkanicGal,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredTerrainSourceProgram,
        color_targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != shader_pack_generation
            || program.shader_pack_generation != shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "ordinary terrain source program, color targets, and requested frame must share world and shader-pack generations",
            ));
        }
        let base = self
            .candidate_source_resource_snapshot_for_frame(
                shader_pack_generation,
                world_generation,
                frame_id,
            )?
            .resources
            .clone();
        let color_resources = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument("source color resources require a shader runtime")
            })?
            .stage_terrain_source_color_resources(gal, program, color_targets)?;
        let unique_color_resources = color_resources.excluding_roles_already_owned_by(&base)?;
        let merged = if unique_color_resources.len() == 0 {
            base
        } else {
            TerrainSourceOwnedResourceSet::merge([&base, &unique_color_resources])?
        };
        program.require_semantic_resources(merged.availability())?;
        Ok(merged)
    }

    /// Builds the exact frame-scoped semantic resource set for the distinct
    /// `gbuffers_textured` program. It deliberately does not reuse a terrain
    /// program or infer a standalone texture binding: the lowered material
    /// contract must find its own atlas and named source resources here.
    pub(crate) fn stage_candidate_source_resources_for_textured_material_program(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredTexturedMaterialSourceProgram,
        color_targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "textured material source program, color targets, and requested frame must share world and shader-pack generations",
            ));
        }
        let base = self
            .candidate_source_resource_snapshot_for_frame(
                program.shader_pack_generation,
                world_generation,
                frame_id,
            )?
            .resources
            .clone();
        let color_resources = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "textured material source resources require a shader runtime",
                )
            })?
            .stage_textured_material_source_color_resources(gal, program, color_targets)?;
        let unique_color_resources = color_resources.excluding_roles_already_owned_by(&base)?;
        let merged = if unique_color_resources.len() == 0 {
            base
        } else {
            TerrainSourceOwnedResourceSet::merge([&base, &unique_color_resources])?
        };
        // Let the candidate stage producer roles this writer declares even
        // when the dimension's terrain program does not (Nether/End writers
        // can declare a shadow map the terrain program never samples).
        if let Some(runtime) = self.shader_runtime.as_ref() {
            runtime.note_writer_required_roles(
                program
                    .opaque_resource_bindings
                    .bindings()
                    .iter()
                    .map(|binding| binding.role()),
            );
        }
        program.require_semantic_resources(merged.availability())?;
        Ok(merged)
    }

    /// Builds the exact frame-scoped semantic resource set for the distinct
    /// entity source program. The base snapshot supplies pack-owned globals;
    /// the per-draw local material is deliberately added later by the entity
    /// writer so no terrain atlas binding can leak into this contract.
    pub(crate) fn stage_candidate_source_resources_for_entity_program(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredEntitySourceProgram,
        color_targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "entity source program, color targets, and requested frame must share world and shader-pack generations",
            ));
        }
        let base = self
            .candidate_source_resource_snapshot_for_frame(
                program.shader_pack_generation,
                world_generation,
                frame_id,
            )?
            .resources
            .clone();
        let color_resources = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument("entity source resources require a shader runtime")
            })?
            .stage_terrain_source_color_resources_for_entity(gal, program, color_targets)?;
        let unique_color_resources = color_resources.excluding_roles_already_owned_by(&base)?;
        let merged = if unique_color_resources.len() == 0 {
            base
        } else {
            TerrainSourceOwnedResourceSet::merge([&base, &unique_color_resources])?
        };
        // Local `MaterialTexture` is explicitly supplied for each entity draw.
        let without_local_texture = if merged
            .availability()
            .resource_for(TerrainSourceResourceRole::MaterialTexture)
            .is_some()
        {
            merged.excluding_roles([TerrainSourceResourceRole::MaterialTexture])?
        } else {
            merged
        };
        for binding in program.opaque_resource_bindings.bindings() {
            if binding.role() != TerrainSourceResourceRole::MaterialTexture
                && without_local_texture
                    .availability()
                    .resource_for(binding.role())
                    .is_none()
            {
                return Err(GalError::invalid_argument(format!(
                    "entity source resource '{}' is unavailable before local material binding",
                    binding.resource_name(),
                )));
            }
        }
        Ok(without_local_texture)
    }

    /// Builds the frame-scoped semantic resource set for `gbuffers_hand`.
    /// Global pack resources come from the same Rust-owned snapshot as world
    /// terrain, while each hand draw supplies its local item material later.
    /// No Iris/Java program, atlas object, or transient binding participates.
    pub(crate) fn stage_candidate_source_resources_for_hand_program(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredHandSourceProgram,
        color_targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "hand source program, color targets, and requested frame must share world and shader-pack generations",
            ));
        }
        let base = self
            .candidate_source_resource_snapshot_for_frame(
                program.shader_pack_generation,
                world_generation,
                frame_id,
            )?
            .resources
            .clone();
        let color_resources = self
            .shader_runtime
            .as_mut()
            .ok_or_else(|| {
                GalError::invalid_argument("hand source resources require a shader runtime")
            })?
            .stage_terrain_source_color_resources_for_hand(gal, program, color_targets)?;
        let unique_color_resources = color_resources.excluding_roles_already_owned_by(&base)?;
        let merged = if unique_color_resources.len() == 0 {
            base
        } else {
            TerrainSourceOwnedResourceSet::merge([&base, &unique_color_resources])?
        };
        // The selected hand program samples the copied local item texture per
        // draw. Deliberately remove any broad material role before binding it
        // so a terrain atlas can never alias an item material.
        let without_local_texture = if merged
            .availability()
            .resource_for(TerrainSourceResourceRole::MaterialTexture)
            .is_some()
        {
            merged.excluding_roles([TerrainSourceResourceRole::MaterialTexture])?
        } else {
            merged
        };
        for binding in program.opaque_resource_bindings.bindings() {
            if binding.role() != TerrainSourceResourceRole::MaterialTexture
                && without_local_texture
                    .availability()
                    .resource_for(binding.role())
                    .is_none()
            {
                return Err(GalError::invalid_argument(format!(
                    "hand source resource '{}' is unavailable before local material binding",
                    binding.resource_name(),
                )));
            }
        }
        Ok(without_local_texture)
    }

    /// Weather has an independently lowered source program, but resolves its
    /// named sampler roles through the same Rust-owned pack resource snapshot
    /// as any other compact material writer. The typed adapter is intentionally
    /// local to this frontend; no weather policy enters GAL or a backend.
    pub(crate) fn stage_candidate_source_resources_for_weather_program(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredWeatherSourceProgram,
        color_targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.stage_candidate_source_resources_for_textured_material_program(
            gal,
            world_generation,
            frame_id,
            &program.material_stream_program(),
            color_targets,
        )
    }

    /// Clouds use a separately lowered source program but the same owned
    /// semantic resource snapshot and compact material-stream binding model.
    /// No Java/Iris resource or renderer state participates here.
    pub(crate) fn stage_candidate_source_resources_for_cloud_program(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        frame_id: u64,
        program: &LoweredCloudSourceProgram,
        color_targets: &ShaderPackColorTargets,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        self.stage_candidate_source_resources_for_textured_material_program(
            gal,
            world_generation,
            frame_id,
            &program.material_stream_program(),
            color_targets,
        )
    }

    pub(crate) fn candidate_source_resource_snapshot_for_frame(
        &self,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
    ) -> GalResult<&CandidateSourceResourceSnapshot> {
        let snapshot = self
            .candidate_source_resource_snapshot
            .as_ref()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source resource assembly has not been prepared for the requested frame",
                )
            })?;
        if snapshot.shader_pack_generation != shader_pack_generation
            || snapshot.world_generation != world_generation
            || snapshot.frame_id != frame_id
        {
            return Err(GalError::invalid_argument(format!(
                "source resource assembly identity pack{} world{} frame{} does not match requested pack{} world{} frame{}",
                snapshot.shader_pack_generation,
                snapshot.world_generation,
                snapshot.frame_id,
                shader_pack_generation,
                world_generation,
                frame_id,
            )));
        }
        Ok(snapshot)
    }

    /// Correlates the exact source-resource snapshot with the Rust-owned
    /// final target created for that frame. Whole-frame source admission is
    /// checked separately; retaining this correlation lets independently
    /// complete terrain/shadow preparation stay testable while an unfinished
    /// DH/fullscreen dependency keeps the route unavailable.
    pub(in crate::render::worldrender) fn record_candidate_source_frame_target(
        &mut self,
        final_binding: GBufferFinalBindingKey,
    ) -> bool {
        let Some(snapshot) = self.candidate_source_resource_snapshot.as_ref() else {
            return false;
        };
        self.candidate_source_frame_target_snapshot = Some(CandidateSourceFrameTargetSnapshot {
            shader_pack_generation: snapshot.shader_pack_generation,
            world_generation: snapshot.world_generation,
            frame_id: snapshot.frame_id,
            final_binding,
        });
        true
    }

    /// Returns only the final-output binding explicitly paired with the
    /// source snapshot for this exact frame and target. A future whole-frame
    /// executor still has to pass the stricter all-stage resource admission
    /// check before it can use this correlation for presentation.
    pub(in crate::render::worldrender) fn candidate_source_g_buffer_final_binding_for_frame(
        &self,
        shader_pack_generation: u64,
        world_generation: u64,
        frame_id: u64,
        frame_target: Handle,
    ) -> GalResult<&GBufferFinalBindingResources> {
        self.candidate_source_resource_snapshot_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
        )?;
        let snapshot = self
            .candidate_source_frame_target_snapshot
            .as_ref()
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source resource assembly has no final target for the requested frame",
                )
            })?;
        if snapshot.shader_pack_generation != shader_pack_generation
            || snapshot.world_generation != world_generation
            || snapshot.frame_id != frame_id
            || snapshot.final_binding.frame_target != frame_target
        {
            return Err(GalError::invalid_argument(format!(
                "source final target identity pack{} world{} frame{} target{:?} does not match requested pack{} world{} frame{} target{:?}",
                snapshot.shader_pack_generation,
                snapshot.world_generation,
                snapshot.frame_id,
                snapshot.final_binding.frame_target,
                shader_pack_generation,
                world_generation,
                frame_id,
                frame_target,
            )));
        }
        self.g_buffer_final_bindings
            .get(&snapshot.final_binding)
            .ok_or_else(|| {
                GalError::backend(
                    "source final target binding was retired before its correlated frame executed",
                )
            })
    }

    pub(crate) fn ensure_candidate_source_material_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
    ) -> GalResult<Vec<TerrainSourceOwnedResourceSet>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "source material texture preparation requires a non-zero world generation",
            ));
        }
        let shader_pack_generation = self
            .shader_runtime
            .as_ref()
            .map(ShaderPackRuntimeExecutor::expected_shader_pack_generation_for_resources)
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before source material preparation")
            })?;
        let role_textures = [
            (
                TerrainSourceResourceRole::MaterialAtlas,
                WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
                "shader-pack.source-material-atlas",
            ),
            (
                TerrainSourceResourceRole::MaterialNormalMap,
                WORLD_MESH_TEXTURE_TERRAIN_BLOCK_NORMAL_ATLAS,
                "shader-pack.source-material-normal-atlas",
            ),
            (
                TerrainSourceResourceRole::MaterialSpecularMap,
                WORLD_MESH_TEXTURE_TERRAIN_BLOCK_SPECULAR_ATLAS,
                "shader-pack.source-material-specular-atlas",
            ),
        ];
        let mut prepared = Vec::with_capacity(role_textures.len());
        let mut retired_source_pack_consumers = false;
        for (role, texture_id, label) in role_textures {
            let requires_role = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime is installed before source material preparation")
                .candidate_source_requires_resource(role.clone());
            if !requires_role {
                self.shader_runtime
                    .as_mut()
                    .expect("shader runtime is installed before source material preparation")
                    .clear_candidate_source_material_texture_role(gal, &role)?;
                continue;
            }
            if !self.mesh_texture_assets.contains_key(&texture_id) {
                self.shader_runtime
                    .as_mut()
                    .expect("shader runtime is installed before source material preparation")
                    .clear_candidate_source_material_texture_role(gal, &role)?;
                continue;
            }
            self.ensure_mesh_texture_resources(gal, texture_id, label)?;
            let texture = self
                .mesh_texture_resources
                .get(&texture_id)
                .ok_or_else(|| {
                    GalError::backend("Rust-owned terrain material texture resource vanished")
                })?;
            let input = TerrainSourceMaterialTextureInput {
                role,
                shader_pack_generation,
                world_generation,
                mesh_asset_generation: self.mesh_asset_generation,
                texture_view: texture.view,
                sampler: texture.sampler,
            };
            let replaces_live_wrapper = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime is installed before source material preparation")
                .candidate_source_material_texture_will_replace(&input);
            if replaces_live_wrapper && !retired_source_pack_consumers {
                // Set-one bindings own the direct dependency on source
                // combined samplers. Retire both near-terrain and DH caches
                // before the runtime swaps a semantic texture wrapper; the
                // next private preparation rebuilds them against one coherent
                // source resource generation.
                self.destroy_lowered_source_terrain_pack_resources(gal);
                self.destroy_lowered_textured_material_source_pack_resources(gal);
                self.destroy_lowered_entity_source_pack_resources(gal);
                self.lod_exact_atlas_source_pass_resources.destroy(gal);
                self.lod_source_pass_resources.destroy(gal);
                retired_source_pack_consumers = true;
            }
            if let Some(resources) = self
                .shader_runtime
                .as_mut()
                .expect("shader runtime is installed before source material preparation")
                .ensure_candidate_source_material_texture_resources(gal, input)?
            {
                prepared.push(resources);
            }
        }
        Ok(prepared)
    }

    pub(crate) fn clear_candidate_source_asset_runtime(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        // Candidate teardown may retire the runtime's copied lightmap. Drop
        // every private source set-one consumer first; material, named-color,
        // shadow, and lightmap wrappers are all semantic GAL resources which
        // may be retained by either near-terrain or DH source program sets.
        // The runtime owns those wrappers, while these frontends own their
        // consumers, so this ordering is the only valid retirement boundary.
        self.destroy_lowered_source_terrain_pack_resources(gal);
        self.destroy_lowered_textured_material_source_pack_resources(gal);
        self.destroy_lowered_entity_source_pack_resources(gal);
        self.lod_exact_atlas_source_pass_resources.destroy(gal);
        self.lod_source_pass_resources.destroy(gal);
        self.lod_opaque_pass_resources.clear_lightmap_bindings(gal);
        self.lod_forward_opaque_pass_resources
            .clear_lightmap_bindings(gal);
        self.lod_exact_atlas_opaque_pass_resources
            .clear_bindings(gal);
        if let Some(resources) = self.lod_exact_atlas_forward_opaque_pass_resources.as_mut() {
            resources.clear_bindings(gal);
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
            resources.clear_bindings(gal);
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
            resources.clear_bindings(gal);
        }
        self.lod_exact_atlas_source_pass_resources
            .clear_bindings(gal);
        self.lod_transparent_pass_resources
            .clear_lightmap_bindings(gal);
        self.lod_water_pass_resources.clear_lightmap_bindings(gal);
        self.discard_distant_horizons_source_targets(gal);
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.clear_candidate_source_asset_resources(gal)?;
        }
        self.candidate_source_asset_runtime = false;
        self.clear_candidate_source_resource_snapshot();
        self.reset_candidate_source_occupancy_stability();
        self.source_execution_armed = false;
        self.source_execution_activation_reported = false;
        self.source_execution_distant_horizons_reported = false;
        Ok(())
    }

    /// Drops only the borrowed-by-value semantic assembly record. The Rust
    /// runtime retains and retires the actual GAL resources through its own
    /// generation-aware owners.
    pub(crate) fn clear_candidate_source_resource_snapshot(&mut self) {
        self.candidate_source_resource_role_count = 0;
        self.candidate_source_missing_resource_roles.clear();
        self.candidate_source_distant_depth_admission = None;
        self.source_execution_admission_reason = None;
        self.candidate_source_resource_snapshot = None;
        self.candidate_source_frame_target_snapshot = None;
    }

    pub(crate) fn reset_candidate_source_occupancy_stability(&mut self) {
        self.candidate_source_occupancy_input_identity = None;
        self.candidate_source_occupancy_stable_frames = 0;
    }

    /// Returns bounded, semantic-only source-preparation facts for the
    /// selected-source receipt. This deliberately reports the same terrain
    /// population used by the debounce without retaining a second cache or
    /// exposing backend resources in the diagnostic boundary.
    pub(crate) fn candidate_source_occupancy_stability_diagnostic(
        &self,
        frame: &WorldPrimitiveFrame,
    ) -> (u8, usize, usize) {
        let mut terrain_meshes = BTreeSet::new();
        let mut terrain_instances = 0_usize;
        for instance in frame
            .mesh_instances
            .iter()
            .filter(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
        {
            terrain_instances = terrain_instances.saturating_add(1);
            terrain_meshes.insert((instance.mesh_key, instance.mesh_generation));
        }
        (
            self.candidate_source_occupancy_stable_frames,
            terrain_instances,
            terrain_meshes.len(),
        )
    }

    /// Records one frame's semantic terrain input and returns true only once
    /// it has remained unchanged for the bounded preparation window. This
    /// keeps an incomplete streaming snapshot from forcing a full occupancy
    /// rebuild every frame while retaining exact mesh-generation validation
    /// when preparation does begin.
    pub(crate) fn candidate_source_occupancy_input_is_stable(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<bool> {
        let camera = frame.voxel_volume.camera_world_position;
        if camera.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(
                "candidate source occupancy camera position is not finite",
            ));
        }
        let mut camera_cell = [0_i32; 3];
        for axis in 0..3 {
            let cell = camera[axis].floor();
            if cell < i32::MIN as f32 || cell > i32::MAX as f32 {
                return Err(GalError::invalid_argument(
                    "candidate source occupancy camera cell is outside the supported i32 range",
                ));
            }
            camera_cell[axis] = cell as i32;
        }

        let mut terrain_meshes = frame
            .mesh_instances
            .iter()
            .filter(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
            .map(|instance| (instance.mesh_key, instance.mesh_generation))
            .collect::<Vec<_>>();
        terrain_meshes.sort_unstable();
        terrain_meshes.dedup();
        let mut lod_segments = frame
            .lod_instances
            .iter()
            .map(|instance| {
                (
                    instance.column_key,
                    instance.column_generation,
                    instance.layer,
                    instance.segment_index,
                )
            })
            .collect::<Vec<_>>();
        lod_segments.sort_unstable();
        lod_segments.dedup();
        // An empty pre-stream frame cannot establish a complete occupancy
        // field. Treating it as stable would allocate/upload a zero field,
        // then immediately rebuild it as the first terrain sections arrive.
        if terrain_meshes.is_empty() && lod_segments.is_empty() {
            self.reset_candidate_source_occupancy_stability();
            return Ok(false);
        }
        let identity = CandidateSourceOccupancyInputIdentity {
            world_generation: frame.voxel_volume.world_generation,
            resource_generation: frame.voxel_volume.resource_generation,
            camera_cell,
            terrain_meshes,
            lod_segments,
            lod_world_y_offset: frame.lod_render_frame.world_y_offset,
        };
        if self
            .candidate_source_occupancy_input_identity
            .as_ref()
            .is_some_and(|previous| previous == &identity)
        {
            self.candidate_source_occupancy_stable_frames = self
                .candidate_source_occupancy_stable_frames
                .saturating_add(1)
                .min(CANDIDATE_SOURCE_OCCUPANCY_STABLE_FRAME_COUNT);
        } else {
            self.candidate_source_occupancy_input_identity = Some(identity);
            self.candidate_source_occupancy_stable_frames = 1;
        }
        Ok(self.candidate_source_occupancy_stable_frames
            >= CANDIDATE_SOURCE_OCCUPANCY_STABLE_FRAME_COUNT)
    }

    pub(crate) fn clear_candidate_source_material_texture_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(runtime) = self.shader_runtime.as_mut() {
            let _ = runtime.clear_candidate_source_material_texture_resources(gal);
        }
        self.clear_candidate_source_resource_snapshot();
        self.source_execution_armed = false;
        self.source_execution_activation_reported = false;
        self.source_execution_distant_horizons_reported = false;
    }

    pub(crate) fn ensure_candidate_source_shadow_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "source shadow depth preparation requires a non-zero world generation",
            ));
        }
        let (requires_primary, requires_secondary, requires_raw, shader_pack_generation) = self
            .shader_runtime
            .as_ref()
            .map(|runtime| {
                (
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::ShadowDepthPrimary,
                    ),
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::ShadowDepthSecondary,
                    ),
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::ShadowDepthRaw,
                    ),
                    runtime.expected_shader_pack_generation_for_resources(),
                )
            })
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before source shadow preparation")
            })?;
        if !requires_primary && !requires_secondary && !requires_raw {
            if let Some(runtime) = self.shader_runtime.as_mut() {
                runtime.clear_candidate_source_shadow_depth_resources(gal)?;
            }
            return Ok(None);
        }
        let Some(g_buffer) = self.g_buffer_resources.as_ref() else {
            // The target has not been prepared yet in this frame. It is not
            // valid to retain a wrapper around a prior graph generation.
            if let Some(runtime) = self.shader_runtime.as_mut() {
                runtime.clear_candidate_source_shadow_depth_resources(gal)?;
            }
            return Ok(None);
        };
        let input = TerrainSourceShadowDepthInput {
            shader_pack_generation,
            world_generation,
            shader_graph_generation: g_buffer.generation,
            shadow_depth_view: g_buffer.shadow_depth_view,
            shadow_depth_secondary_view: g_buffer.shadow_depth_opaque_view,
        };
        self.shader_runtime
            .as_mut()
            .expect("shader runtime is installed before source shadow preparation")
            .ensure_candidate_source_shadow_depth_resources(gal, input)
    }

    pub(crate) fn clear_candidate_source_shadow_depth_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(runtime) = self.shader_runtime.as_mut() {
            let _ = runtime.clear_candidate_source_shadow_depth_resources(gal);
        }
    }

    /// Prepares the declared Rust-owned source shadow-color samplers. They
    /// remain distinct from depth and from each other: a pack may sample both
    /// `shadowcolor0` and `shadowcolor1` with different semantics.
    pub(crate) fn ensure_candidate_source_shadow_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "source shadow color preparation requires a non-zero world generation",
            ));
        }
        let (requires_shadow_color, requires_shadow_color_secondary, shader_pack_generation) = self
            .shader_runtime
            .as_ref()
            .map(|runtime| {
                (
                    runtime
                        .candidate_source_requires_resource(TerrainSourceResourceRole::ShadowColor),
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::ShadowColorSecondary,
                    ),
                    runtime.expected_shader_pack_generation_for_resources(),
                )
            })
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before source shadow color preparation")
            })?;
        if !requires_shadow_color && !requires_shadow_color_secondary {
            self.clear_candidate_source_shadow_color_resources(gal);
            return Ok(None);
        }
        let Some(g_buffer) = self.g_buffer_resources.as_ref() else {
            // Do not retain a wrapper across a graph replacement. The next
            // graph generation will recreate the attachment before it can be
            // made available to any future selected-source resource set.
            self.clear_candidate_source_shadow_color_resources(gal);
            return Ok(None);
        };
        let input = TerrainSourceShadowColorInput {
            shader_pack_generation,
            world_generation,
            shader_graph_generation: g_buffer.generation,
            shadow_color_view: g_buffer.shadow_color_view,
            shadow_color_secondary_view: g_buffer.shadow_light_shaft_view,
            sampler: g_buffer.sampler,
        };
        self.shader_runtime
            .as_mut()
            .expect("shader runtime is installed before source shadow color preparation")
            .ensure_candidate_source_shadow_color_resources(gal, input)
    }

    pub(crate) fn clear_candidate_source_shadow_color_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(runtime) = self.shader_runtime.as_mut() {
            let _ = runtime.clear_candidate_source_shadow_color_resources(gal);
        }
    }

    /// Prepares only source main-depth roles whose Rust-owned snapshots have
    /// been confirmed for this exact G-buffer generation. A declared but
    /// unconfirmed temporal role is intentionally omitted so source admission
    /// remains blocked instead of sampling a current or stale depth image.
    pub(crate) fn ensure_candidate_source_main_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "source main depth preparation requires a non-zero world generation",
            ));
        }
        let (requires_main, requires_before, requires_previous, shader_pack_generation) = self
            .shader_runtime
            .as_ref()
            .map(|runtime| {
                (
                    runtime
                        .candidate_source_requires_resource(TerrainSourceResourceRole::MainDepth),
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::MainDepthBeforeTranslucency,
                    ),
                    runtime.candidate_source_requires_resource(
                        TerrainSourceResourceRole::MainDepthPrevious,
                    ),
                    runtime.expected_shader_pack_generation_for_resources(),
                )
            })
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before source main depth preparation")
            })?;
        if !requires_main && !requires_before && !requires_previous {
            self.shader_runtime
                .as_mut()
                .expect("shader runtime checked before source main depth preparation")
                .clear_candidate_source_main_depth_resources(gal)?;
            return Ok(None);
        }
        let Some(g_buffer) = self.g_buffer_resources.as_ref() else {
            self.shader_runtime
                .as_mut()
                .expect("shader runtime checked before source main depth preparation")
                .clear_candidate_source_main_depth_resources(gal)?;
            return Ok(None);
        };
        let history = (self.g_buffer_depth_history.graph_generation == g_buffer.generation)
            .then_some(self.g_buffer_depth_history)
            .unwrap_or_default();
        let input = TerrainSourceMainDepthInput {
            shader_pack_generation,
            world_generation,
            shader_graph_generation: g_buffer.generation,
            // Fullscreen consumers read the live depth attachment while it is
            // still in shader-read state. Temporal snapshots are committed
            // after the fullscreen chain has consumed this frame.
            main_depth_view: g_buffer.depth_view,
            before_translucency_view: (requires_before && history.before_translucency_valid)
                .then_some(g_buffer.main_depth_before_translucency_view),
            previous_view: (requires_previous && history.previous_valid)
                .then_some(g_buffer.main_depth_previous_view),
            sampler: g_buffer.sampler,
        };
        if matches!(
            crate::core::environment::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] semantic main=0x{:016x} before={:?} previous={:?}",
                input.main_depth_view.raw(),
                input.before_translucency_view.map(Handle::raw),
                input.previous_view.map(Handle::raw),
            );
        }
        self.shader_runtime
            .as_mut()
            .expect("shader runtime checked before source main depth preparation")
            .ensure_candidate_source_main_depth_resources(gal, input)
    }

    pub(crate) fn clear_candidate_source_main_depth_resources(&mut self, gal: &mut VulkanicGal) {
        if let Some(runtime) = self.shader_runtime.as_mut() {
            let _ = runtime.clear_candidate_source_main_depth_resources(gal);
        }
    }
}

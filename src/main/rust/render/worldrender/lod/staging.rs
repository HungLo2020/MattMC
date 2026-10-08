//! Distant Horizons frame work: Rust LOD material staging, exact-atlas passes, generic boxes and LOD diagnostics.

use crate::render::worldrender::*;

/// Bounded graphics-audit selector for comparing the two real copied DH
/// material streams. It is deliberately unavailable outside audit runs and
/// never participates in producer selection or normal route execution.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) enum WorldLodAuditExactAtlasMode {
    Default,
    CoarseOnly,
    ExactOnly,
}

pub(in crate::render::worldrender) fn world_lod_audit_exact_atlas_mode() -> WorldLodAuditExactAtlasMode {
    let audit_enabled = matches!(
        crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
            .as_deref()
            .map(str::trim),
        Ok("1") | Ok("true") | Ok("TRUE")
    );
    if !audit_enabled {
        return WorldLodAuditExactAtlasMode::Default;
    }
    world_lod_audit_exact_atlas_mode_from(
        crate::core::environment::var("MATTMC_RUST_DH_SOURCE_STREAM")
            .ok()
            .as_deref(),
    )
}

pub(in crate::render::worldrender) fn world_lod_audit_exact_atlas_mode_from(value: Option<&str>) -> WorldLodAuditExactAtlasMode {
    match value.map(str::trim) {
        Some("coarse-only") => WorldLodAuditExactAtlasMode::CoarseOnly,
        Some("exact-atlas-only") => WorldLodAuditExactAtlasMode::ExactOnly,
        _ => WorldLodAuditExactAtlasMode::Default,
    }
}

pub(in crate::render::worldrender) fn world_lod_draw_isolation_enabled_from(
    requested: Option<&str>,
    graphics_audit: Option<&str>,
    performance_isolation: Option<&str>,
) -> bool {
    let enabled = |value: Option<&str>| matches!(value.map(str::trim), Some("1" | "true" | "TRUE"));
    enabled(requested) && (enabled(graphics_audit) || enabled(performance_isolation))
}

/// Material suppression is diagnostic-only. Performance isolation keeps the
/// probe scoped to DH draw admission and avoids enabling every unrelated
/// fail-closed graphics-audit fixture in a real saved world.
pub(in crate::render::worldrender) fn world_lod_draw_isolation_enabled(variable: &str) -> bool {
    let requested = crate::core::environment::var(variable).ok();
    let graphics_audit = crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").ok();
    let performance_isolation = crate::core::environment::var("MATTMC_RUST_DH_PERF_ISOLATION").ok();
    world_lod_draw_isolation_enabled_from(
        requested.as_deref(),
        graphics_audit.as_deref(),
        performance_isolation.as_deref(),
    )
}

pub(in crate::render::worldrender) fn world_lod_exact_atlas_segment_is_complete(
    asset: &lod::WorldLodTexturedGpuColumnAsset,
    instance: &WorldLodColumnInstanceRequest,
) -> GalResult<bool> {
    if asset.column_generation != instance.column_generation {
        return Err(GalError::invalid_argument(
            "world LOD exact-atlas instance generation differs from its immutable asset",
        ));
    }
    if !world_lod_exact_atlas_has_packed_source_segment(asset, instance) {
        return Err(GalError::invalid_argument(
            "world LOD exact-atlas instance does not name a packed source segment",
        ));
    }
    Ok(!asset
        .unavailable_source_segments
        .contains(&instance.segment_index))
}

/// A copied DH source segment need not have a corresponding exact-atlas
/// segment: it may contain only reduced-color material data or no resolvable
/// atlas quads. Presence selects the private exact-atlas path; completeness
/// is meaningful only after that selection.
pub(in crate::render::worldrender) fn world_lod_exact_atlas_has_packed_source_segment(
    asset: &lod::WorldLodTexturedGpuColumnAsset,
    instance: &WorldLodColumnInstanceRequest,
) -> bool {
    asset.column_generation == instance.column_generation
        && asset.segments.iter().any(|segment| {
            segment.source_segment_index == instance.segment_index
                && segment.layer == instance.layer
        })
}

/// Returns `None` when the visible semantic source segment has no resolved
/// exact-atlas payload and must therefore remain on the reduced-color path.
/// A present segment is still validated strictly before it can be selected.
pub(in crate::render::worldrender) fn world_lod_exact_atlas_segment_completeness(
    asset: &lod::WorldLodTexturedGpuColumnAsset,
    instance: &WorldLodColumnInstanceRequest,
) -> GalResult<Option<bool>> {
    if !world_lod_exact_atlas_has_packed_source_segment(asset, instance) {
        return Ok(None);
    }
    world_lod_exact_atlas_segment_is_complete(asset, instance).map(Some)
}

impl WorldPrimitiveFrontend {
    /// The bounded DH non-water route uses the built-in Rust terrain material
    /// pass, not a selected shader-pack source plan. Its copied vanilla
    /// lightmap must therefore outlive optional source-candidate discovery:
    /// loading/menu background admission may clear that candidate runtime,
    /// but it must not make an otherwise selected LOD frame bind a missing
    /// Rust-owned lightmap resource.
    pub(in crate::render::worldrender) fn ensure_rust_lod_lightmap_for_frame(
        &mut self,
        gal: &mut VulkanicGal,
        runtime_generation: u64,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<()> {
        if !frame.lod_render_frame.rust_route_selected() {
            return Ok(());
        }
        if !frame.shader_environment.enabled {
            return Err(GalError::invalid_argument(
                "Rust Distant Horizons non-water route requires copied vanilla lightmap semantics",
            ));
        }
        self.ensure_shader_runtime(gal, runtime_generation)?;
        self.shader_runtime
            .as_mut()
            .expect("Rust LOD lightmap runtime is installed before observation")
            .observe_vanilla_lightmap(
                frame.shader_environment.world_generation,
                frame.shader_environment.vanilla_lightmap,
            )?;
        Ok(())
    }
}

impl WorldPrimitiveFrontend {
    /// Atomically installs copied Distant Horizons column assets. Visible
    /// whole-frame work may stage private Rust-owned geometry buffers, but no
    /// LOD pipeline or draw is admitted until its material/pass contract is complete.
    pub fn apply_world_lod_column_asset_update(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        assets: Vec<WorldLodColumnAsset>,
        retirements: Vec<WorldLodColumnRetirement>,
    ) -> GalResult<()> {
        self.apply_world_lod_column_asset_update_with_provenance(
            gal,
            generation,
            assets,
            retirements,
            Vec::new(),
        )
    }

    pub fn apply_world_lod_column_asset_update_with_provenance(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        assets: Vec<WorldLodColumnAsset>,
        retirements: Vec<WorldLodColumnRetirement>,
        material_provenance: Vec<WorldLodColumnMaterialProvenance>,
    ) -> GalResult<()> {
        let asset_count = assets.len();
        let trace_started = std::time::Instant::now();
        let result = self.apply_world_lod_column_asset_update_inner(
            generation,
            assets,
            retirements,
            material_provenance,
        );
        let inner_nanos = elapsed_nanos_u64(trace_started);
        match result {
            Ok(()) => {
                let reconcile_started = std::time::Instant::now();
                self.lod_opaque_pass_resources
                    .reconcile_assets(gal, &self.lod_gpu_column_assets);
                self.lod_forward_opaque_pass_resources
                    .reconcile_assets(gal, &self.lod_gpu_column_assets);
                self.lod_exact_atlas_opaque_pass_resources
                    .reconcile_assets(gal, &self.lod_textured_gpu_column_assets);
                if let Some(resources) = self.lod_exact_atlas_forward_opaque_pass_resources.as_mut()
                {
                    resources.reconcile_assets(gal, &self.lod_textured_gpu_column_assets);
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
                    resources.reconcile_assets(gal, &self.lod_textured_gpu_column_assets);
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
                    resources.reconcile_assets(gal, &self.lod_textured_gpu_column_assets);
                }
                self.lod_exact_atlas_source_pass_resources
                    .reconcile_assets(gal, &self.lod_textured_gpu_column_assets);
                self.lod_transparent_pass_resources
                    .reconcile_assets(gal, &self.lod_gpu_column_assets);
                self.lod_water_pass_resources
                    .reconcile_assets(gal, &self.lod_gpu_column_assets);
                self.lod_source_pass_resources
                    .reconcile_assets(gal, &self.lod_gpu_column_assets);
                // Release descriptor dependencies before the residency buffers.
                self.lod_gpu_residency
                    .reconcile_assets(gal, &self.lod_gpu_column_assets);
                self.lod_textured_gpu_residency
                    .reconcile_assets(gal, &self.lod_textured_gpu_column_assets);
                if matches!(
                    crate::core::environment::var("MATTMC_RUST_DH_ASSET_PHASE_TRACE").as_deref(),
                    Ok("1" | "true" | "TRUE")
                ) {
                    crate::core::console::stdout(format_args!(
                        "world-lod.asset-phase generation={generation} columns={asset_count} inner_nanos={inner_nanos} reconcile_nanos={}",
                        elapsed_nanos_u64(reconcile_started),
                    ));
                }
                Ok(())
            }
            Err(error) => {
                self.lod_asset_update_failures = self.lod_asset_update_failures.saturating_add(1);
                Err(error)
            }
        }
    }

    pub(in crate::render::worldrender) fn apply_world_lod_column_asset_update_inner(
        &mut self,
        generation: u64,
        assets: Vec<WorldLodColumnAsset>,
        retirements: Vec<WorldLodColumnRetirement>,
        material_provenance: Vec<WorldLodColumnMaterialProvenance>,
    ) -> GalResult<()> {
        let validate_started = std::time::Instant::now();
        if generation == 0 {
            return Err(GalError::invalid_argument(
                "world LOD asset update generation must be non-zero",
            ));
        }
        if generation <= self.lod_asset_generation {
            return Err(GalError::invalid_argument(format!(
                "stale world LOD asset update generation {generation}; current generation is {}",
                self.lod_asset_generation
            )));
        }

        let mut asset_keys = BTreeSet::new();
        for asset in &assets {
            validate_world_lod_column_asset(asset)?;
            if !asset_keys.insert(asset.column_key) {
                return Err(GalError::invalid_argument(format!(
                    "duplicate world LOD column asset {} in one update",
                    asset.column_key
                )));
            }
        }
        let mut retirement_keys = BTreeSet::new();
        for retirement in &retirements {
            if retirement.column_generation == 0 {
                return Err(GalError::invalid_argument(
                    "world LOD retirement generation must be non-zero",
                ));
            }
            if !retirement_keys.insert(retirement.column_key) {
                return Err(GalError::invalid_argument(format!(
                    "duplicate world LOD column retirement {} in one update",
                    retirement.column_key
                )));
            }
            if asset_keys.contains(&retirement.column_key) {
                return Err(GalError::invalid_argument(format!(
                    "world LOD update both replaces and retires column {}",
                    retirement.column_key
                )));
            }
        }

        let validate_nanos = elapsed_nanos_u64(validate_started);
        let provenance_started = std::time::Instant::now();

        let provenance_by_key =
            validate_world_lod_material_provenance(&assets, &retirements, material_provenance)?;
        let provenance_nanos = elapsed_nanos_u64(provenance_started);

        // Build all replacement artifacts before touching retained state. This
        // preserves failure atomicity without cloning every retained DH cache.
        let retired_keys: BTreeSet<u64> = retirements
            .iter()
            .filter_map(|retirement| {
                self.lod_gpu_column_assets
                    .get(&retirement.column_key)
                    .filter(|asset| asset.column_generation == retirement.column_generation)
                    .map(|_| retirement.column_key)
            })
            .collect();
        let mut projected_columns = self
            .lod_gpu_column_assets
            .len()
            .saturating_sub(retired_keys.len());
        for asset in &assets {
            if !self.lod_gpu_column_assets.contains_key(&asset.column_key) {
                projected_columns = projected_columns.checked_add(1).ok_or_else(|| {
                    GalError::invalid_argument("world LOD retained column count overflows")
                })?;
            }
        }
        if projected_columns > WORLD_LOD_MAX_COLUMNS {
            return Err(GalError::invalid_argument(format!(
                "world LOD retained column count {projected_columns} exceeds bounded limit {WORLD_LOD_MAX_COLUMNS}"
            )));
        }
        let asset_count = assets.len();
        let prepare_started = std::time::Instant::now();
        let mut expand_nanos = 0_u64;
        let mut pack_nanos = 0_u64;
        let mut prepared = Vec::with_capacity(asset_count);
        for asset in assets {
            if let Some(current) = self.lod_gpu_column_assets.get(&asset.column_key) {
                if current.column_generation >= asset.column_generation {
                    return Err(GalError::invalid_argument(format!(
                        "stale world LOD column {} generation {}; current generation is {}",
                        asset.column_key, asset.column_generation, current.column_generation
                    )));
                }
            }
            let pack_started = std::time::Instant::now();
            let (expanded, gpu) = if provenance_by_key.contains_key(&asset.column_key) {
                let expand_started = std::time::Instant::now();
                let expanded = lod::expand_world_lod_column_asset(&asset)?;
                expand_nanos = expand_nanos.saturating_add(elapsed_nanos_u64(expand_started));
                let gpu = lod::pack_world_lod_gpu_column_asset(&expanded)?;
                (Some(expanded), gpu)
            } else {
                (
                    None,
                    lod::pack_world_lod_gpu_column_asset_from_compact(&asset)?,
                )
            };
            pack_nanos = pack_nanos.saturating_add(elapsed_nanos_u64(pack_started));
            if gpu.column_key != asset.column_key
                || gpu.column_generation != asset.column_generation
            {
                return Err(GalError::invalid_argument(
                    "world LOD GPU payload identity differs from its semantic column asset",
                ));
            }
            if gpu.segments.len() != asset.segments.len() {
                return Err(GalError::invalid_argument(
                    "world LOD GPU payload segment count differs from its semantic column asset",
                ));
            }
            for (segment_index, segment) in gpu.segments.iter().enumerate() {
                if segment.vertex_count == 0 || segment.index_count == 0 {
                    return Err(GalError::invalid_argument(format!(
                        "world LOD GPU payload segment {segment_index} is empty",
                    )));
                }
            }
            let textured = if let Some(provenance) = provenance_by_key.get(&asset.column_key) {
                let textured_plan = lod::plan_world_lod_textured_column(&asset, provenance)?;
                if textured_plan.column_key != asset.column_key
                    || textured_plan.column_generation != asset.column_generation
                {
                    return Err(GalError::invalid_argument(
                        "world LOD textured plan identity differs from its column asset",
                    ));
                }
                Some((
                    provenance.clone(),
                    lod::pack_world_lod_textured_column_asset(&textured_plan)?,
                    textured_plan,
                ))
            } else {
                None
            };
            prepared.push((asset, expanded, gpu, textured));
        }
        let prepare_nanos = elapsed_nanos_u64(prepare_started);
        let commit_started = std::time::Instant::now();
        for key in retired_keys {
            self.lod_column_assets.remove(&key);
            self.lod_expanded_column_assets.remove(&key);
            self.lod_gpu_column_assets.remove(&key);
            self.lod_material_provenance.remove(&key);
            self.lod_textured_column_plans.remove(&key);
            self.lod_textured_gpu_column_assets.remove(&key);
        }
        for (asset, expanded, gpu, textured) in prepared {
            let key = asset.column_key;
            self.lod_gpu_column_assets.insert(key, gpu);
            if let Some((provenance, textured_gpu, textured_plan)) = textured {
                // Exact-atlas/source execution needs the original copied column
                // and typed expansion to prove its material contract. Ordinary
                // reduced-color DH frames never read either representation after
                // this point; retaining them alongside the packed upload asset
                // multiplied large real-world columns in native memory.
                self.lod_column_assets.insert(key, asset);
                self.lod_expanded_column_assets.insert(
                    key,
                    expanded.expect("exact-atlas provenance requires the expanded DH column"),
                );
                self.lod_material_provenance.insert(key, provenance);
                self.lod_textured_gpu_column_assets
                    .insert(key, textured_gpu);
                self.lod_textured_column_plans.insert(key, textured_plan);
            } else {
                self.lod_column_assets.remove(&key);
                self.lod_expanded_column_assets.remove(&key);
                self.lod_material_provenance.remove(&key);
                self.lod_textured_gpu_column_assets.remove(&key);
                self.lod_textured_column_plans.remove(&key);
            }
        }
        self.lod_voxel_source_meshes
            .retain(|(column_key, _), entry| {
                self.lod_column_assets
                    .get(column_key)
                    .is_some_and(|asset| asset.column_generation == entry.mesh.mesh_generation)
            });
        self.lod_asset_generation = generation;
        if matches!(
            crate::core::environment::var("MATTMC_RUST_DH_ASSET_PHASE_TRACE").as_deref(),
            Ok("1" | "true" | "TRUE")
        ) {
            crate::core::console::stdout(format_args!(
                "world-lod.asset-inner generation={generation} columns={asset_count} validate_nanos={validate_nanos} provenance_nanos={provenance_nanos} prepare_nanos={prepare_nanos} expand_nanos={expand_nanos} pack_nanos={pack_nanos} commit_nanos={}",
                elapsed_nanos_u64(commit_started),
            ));
        }
        Ok(())
    }

    /// Once a generation has reached private Vulkan buffers, ordinary
    /// reduced-color DH rendering keeps only the compact draw metadata. Exact
    /// material/source paths deliberately retain their CPU payload because
    /// their later provenance and audit stages can still inspect it.
    pub(in crate::render::worldrender) fn release_uploaded_lod_gpu_payloads(&mut self) {
        let (gpu_assets, source_assets, expanded_assets, residency) = (
            &mut self.lod_gpu_column_assets,
            &self.lod_column_assets,
            &self.lod_expanded_column_assets,
            &self.lod_gpu_residency,
        );
        for (&column_key, asset) in gpu_assets.iter_mut() {
            if source_assets.contains_key(&column_key) || expanded_assets.contains_key(&column_key)
            {
                continue;
            }
            if residency.active_generation(column_key) == Some(asset.column_generation) {
                for segment in &mut asset.segments {
                    segment.release_uploaded_payload();
                }
            }
        }
    }

    /// Resolves a Java-preflighted DH frame into Rust-owned graph draws.
    /// Opaque, transparent, and water-surface segments retain their distinct
    /// material contracts; unknown source layers remain explicit failures.
    pub(in crate::render::worldrender) fn ensure_exact_atlas_pass_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: TextureFormat,
        layer: u32,
        deferred: bool,
    ) -> GalResult<()> {
        if deferred && layer == WORLD_LOD_LAYER_OPAQUE {
            // The deferred opaque owner is initialized directly at its fixed
            // four-attachment format; it does not share the one-color cache.
            return Ok(());
        }
        let resources = match (deferred, layer) {
            (false, WORLD_LOD_LAYER_OPAQUE) => self
                .lod_exact_atlas_forward_opaque_pass_resources
                .get_or_insert_with(lod::WorldLodExactAtlasOpaquePassResources::new_forward),
            (false, WORLD_LOD_LAYER_TRANSPARENT_SIDE) => self
                .lod_exact_atlas_forward_transparent_side_pass_resources
                .get_or_insert_with(
                    lod::WorldLodExactAtlasPassResources::new_forward_transparent_side,
                ),
            (false, WORLD_LOD_LAYER_TRANSPARENT_UP) => self
                .lod_exact_atlas_forward_transparent_up_pass_resources
                .get_or_insert_with(
                    lod::WorldLodExactAtlasPassResources::new_forward_transparent_up,
                ),
            (false, WORLD_LOD_LAYER_TRANSPARENT_WATER_UP) => self
                .lod_exact_atlas_forward_water_pass_resources
                .get_or_insert_with(
                    lod::WorldLodExactAtlasPassResources::new_forward_water_surface,
                ),
            (true, WORLD_LOD_LAYER_TRANSPARENT_SIDE) => self
                .lod_exact_atlas_deferred_transparent_side_pass_resources
                .get_or_insert_with(
                    lod::WorldLodExactAtlasPassResources::new_deferred_transparent_side,
                ),
            (true, WORLD_LOD_LAYER_TRANSPARENT_UP) => self
                .lod_exact_atlas_deferred_transparent_up_pass_resources
                .get_or_insert_with(
                    lod::WorldLodExactAtlasPassResources::new_deferred_transparent_up,
                ),
            (true, WORLD_LOD_LAYER_TRANSPARENT_WATER_UP) => self
                .lod_exact_atlas_deferred_water_pass_resources
                .get_or_insert_with(
                    lod::WorldLodExactAtlasPassResources::new_deferred_water_surface,
                ),
            _ => {
                return Err(GalError::invalid_argument(format!(
                    "world LOD exact-atlas owner received unknown layer {layer}"
                )))
            }
        };
        if deferred {
            // Deferred DH color attachments are Rust-owned and fixed to the
            // shader graph's HDR intermediate format; retain the explicit
            // argument check so a future caller cannot silently bind a
            // different attachment.
            if color_format != SHADER_G_BUFFER_COLOR_FORMAT {
                return Err(GalError::invalid_argument(
                    "deferred exact-atlas pass requires the shader G-buffer color format",
                ));
            }
            Ok(())
        } else {
            resources.set_color_format(gal, color_format)
        }
    }

    pub(in crate::render::worldrender) fn stage_forward_exact_atlas_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: lod::WorldLodTexturedGpuDraw,
        uniforms: lod::WorldLodDrawUniform,
        atlas: lod::WorldLodTerrainAtlasBinding,
        lightmap: VanillaLightmapBinding,
        water_transparent_replay: bool,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainMeshDraw> {
        let prepared = match draw.layer {
            WORLD_LOD_LAYER_OPAQUE => self
                .lod_exact_atlas_forward_opaque_pass_resources
                .as_mut()
                .expect("forward exact-atlas opaque resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            WORLD_LOD_LAYER_TRANSPARENT_SIDE => self
                .lod_exact_atlas_forward_transparent_side_pass_resources
                .as_mut()
                .expect("forward exact-atlas transparent-side resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            WORLD_LOD_LAYER_TRANSPARENT_UP => self
                .lod_exact_atlas_forward_transparent_up_pass_resources
                .as_mut()
                .expect("forward exact-atlas transparent-up resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            WORLD_LOD_LAYER_TRANSPARENT_WATER_UP => self
                .lod_exact_atlas_forward_water_pass_resources
                .as_mut()
                .expect("forward exact-atlas water resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            layer => {
                return Err(GalError::invalid_argument(format!(
                    "world LOD exact-atlas forward draw received unknown layer {layer}"
                )))
            }
        };
        Ok(TerrainMeshDraw {
            shadow: None,
            pipeline: prepared.pipeline,
            offscreen_pipeline: if water_transparent_replay
                && draw.layer == WORLD_LOD_LAYER_TRANSPARENT_WATER_UP
            {
                prepared
                    .offscreen_replay_pipeline
                    .or(prepared.offscreen_pipeline)
            } else {
                prepared.offscreen_pipeline
            },
            pipeline_layout: prepared.pipeline_layout,
            resource_set: prepared.geometry_resource_set,
            resource_set_dynamic_offsets: Vec::new().into(),
            shader_resource_set: Some(TerrainShaderResourceSet {
                set_index: 1,
                set: prepared.atlas_and_lightmap_resource_set,
            }),
            index_buffer: prepared.index_buffer,
            index_offset: 0,
            index_type: prepared.index_type,
            index_count: prepared.index_count,
            instance_count: 1,
            indexed_indirect: None,
            stratum: WORLD_STRATUM_TERRAIN,
            material_mode: if draw.layer == WORLD_LOD_LAYER_OPAQUE {
                TerrainMaterialPassMode::Opaque
            } else {
                TerrainMaterialPassMode::Translucent
            },
            shadow_participation: TerrainShadowParticipation::Unavailable,
        })
    }

    pub(in crate::render::worldrender) fn stage_exact_atlas_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: lod::WorldLodTexturedGpuDraw,
        uniforms: lod::WorldLodDrawUniform,
        atlas: lod::WorldLodTerrainAtlasBinding,
        lightmap: VanillaLightmapBinding,
        deferred: bool,
        water_transparent_replay: bool,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainMeshDraw> {
        if !deferred {
            return self.stage_forward_exact_atlas_draw(
                gal,
                draw,
                uniforms,
                atlas,
                lightmap,
                water_transparent_replay,
                ops,
            );
        }
        let prepared = match draw.layer {
            WORLD_LOD_LAYER_TRANSPARENT_SIDE => self
                .lod_exact_atlas_deferred_transparent_side_pass_resources
                .as_mut()
                .expect("deferred exact-atlas transparent-side resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            WORLD_LOD_LAYER_TRANSPARENT_UP => self
                .lod_exact_atlas_deferred_transparent_up_pass_resources
                .as_mut()
                .expect("deferred exact-atlas transparent-up resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            WORLD_LOD_LAYER_TRANSPARENT_WATER_UP => self
                .lod_exact_atlas_deferred_water_pass_resources
                .as_mut()
                .expect("deferred exact-atlas water resources initialized")
                .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?,
            layer => {
                return Err(GalError::invalid_argument(format!(
                    "deferred exact-atlas draw received unsupported layer {layer}"
                )))
            }
        };
        Ok(TerrainMeshDraw {
            shadow: None,
            pipeline: prepared.pipeline,
            offscreen_pipeline: prepared.offscreen_pipeline,
            pipeline_layout: prepared.pipeline_layout,
            resource_set: prepared.geometry_resource_set,
            resource_set_dynamic_offsets: Vec::new().into(),
            shader_resource_set: Some(TerrainShaderResourceSet {
                set_index: 1,
                set: prepared.atlas_and_lightmap_resource_set,
            }),
            index_buffer: prepared.index_buffer,
            index_offset: 0,
            index_type: prepared.index_type,
            index_count: prepared.index_count,
            instance_count: 1,
            indexed_indirect: None,
            stratum: WORLD_STRATUM_TERRAIN,
            material_mode: TerrainMaterialPassMode::Translucent,
            shadow_participation: TerrainShadowParticipation::Unavailable,
        })
    }

    pub(in crate::render::worldrender) fn stage_rust_lod_material_draws(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        deferred: bool,
        target_color_format: TextureFormat,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        let mut visible = std::mem::take(&mut self.lod_visible_draw_scratch);
        let mut plan = std::mem::take(&mut self.lod_frame_plan_scratch);
        let result = (|| {
            self.lod_gpu_residency.resolve_visible_draws_cached_into(
                &self.lod_gpu_column_assets,
                &frame.lod_instances,
                &mut visible,
            )?;
            lod::plan_world_lod_frame_with_camera_into(
                &frame.lod_render_frame,
                &visible,
                frame.lod_render_frame.camera_world_position,
                &mut plan,
            )?;
            if plan.opaque_draws.len() + plan.transparent_draws.len() + plan.water_draws.len()
                != frame.lod_instances.len()
            {
                return Err(GalError::backend(
                    "world LOD draw classification did not cover every visible semantic segment",
                ));
            }
            self.stage_rust_lod_material_draws_resolved(
                gal,
                frame,
                deferred,
                target_color_format,
                ops,
                &visible,
                &mut plan,
            )
        })();
        self.lod_visible_draw_scratch = visible;
        self.lod_frame_plan_scratch = plan;
        return result;
    }

    /// Expands a prevalidated LOD residency/plan pair into Rust-owned material
    /// draws. Keeping the resolved data explicit here preserves one ownership
    /// boundary for callers that already performed the CPU validation while
    /// the public helper below remains self-contained for standalone callers.
    pub(in crate::render::worldrender) fn stage_rust_lod_material_draws_resolved(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        deferred: bool,
        target_color_format: TextureFormat,
        ops: &mut Vec<CommandOp>,
        visible: &[lod::WorldLodGpuDraw],
        plan: &mut lod::WorldLodFramePlan,
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        if !frame.lod_render_frame.rust_route_selected() {
            return Ok(Vec::new());
        }
        if frame.lod_instances.is_empty() {
            return Ok(Vec::new());
        }
        if !deferred {
            self.lod_forward_opaque_pass_resources.begin_frame();
            self.lod_forward_opaque_pass_resources.prune_idle_page_sets(gal);
        }
        // Transparent/water owners serve both routes and may pack per-frame
        // uniforms in either; reset their per-frame slots every frame.
        self.lod_transparent_pass_resources.begin_frame();
        self.lod_water_pass_resources.begin_frame();
        self.lod_transparent_pass_resources.prune_idle_page_sets(gal);
        self.lod_water_pass_resources.prune_idle_page_sets(gal);
        // Deferred DH alpha still lands in the graph's one-color translucent
        // attachment. Keep its exact-atlas owners on that attachment format;
        // the acquired target format is only valid for the direct forward
        // route.
        let lod_color_format = if deferred {
            // Deferred DH material draws land in the Rust-owned G-buffer
            // translucent attachment, which uses the same HDR FP16 domain as
            // the terrain graph.  The acquired target format is only valid
            // for the direct forward route.
            SHADER_G_BUFFER_COLOR_FORMAT
        } else {
            target_color_format
        };
        if !deferred {
            self.lod_forward_opaque_pass_resources
                .set_color_format(gal, lod_color_format)?;
        }
        // Transparent and water owners serve both routes; bind them to this
        // route's attachment format (rebuilding after a shader toggle).
        self.lod_transparent_pass_resources
            .set_color_format(gal, lod_color_format)?;
        self.lod_water_pass_resources
            .set_color_format(gal, lod_color_format)?;
        // The outer whole-frame builder normally stages this residency in its
        // pre-graph operations. Keep the draw boundary self-sufficient as
        // well: candidate/resource preparation may run between that staging
        // point and this draw expansion. This is idempotent and appends the
        // same Rust-owned upload to the combined submission only when no
        // compatible residency is already staged or confirmed.
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.stage_vanilla_lightmap_residency(gal, ops)?;
        }
        let lightmap = self
            .shader_runtime
            .as_ref()
            .and_then(|runtime| runtime.vanilla_lightmap_binding(true))
            .ok_or_else(|| {
                let frame_lightmap_generation = frame
                    .shader_environment
                    .vanilla_lightmap
                    .as_ref()
                    .map(|lightmap| lightmap.generation);
                let (cache_world_generation, cache_lightmap_generation) = self
                    .shader_runtime
                    .as_ref()
                    .map(|runtime| {
                        let cache = runtime.vanilla_lightmap_cache();
                        (cache.world_generation(), cache.lightmap_generation())
                    })
                    .unwrap_or((0, 0));
                GalError::invalid_argument(format!(
                    "Rust Distant Horizons material route requires a staged Rust-owned vanilla lightmap \
                     (frame_lightmap_generation={frame_lightmap_generation:?}, \
                     route_world_generation={}, cache_world_generation={cache_world_generation}, \
                     cache_lightmap_generation={cache_lightmap_generation})",
                    frame.shader_environment.world_generation,
                ))
            })?;
        let fog_color_and_alpha = frame.shader_environment.fog_parameter_color;
        let fog_ranges = [
            frame.shader_environment.fog_environmental_start,
            frame.shader_environment.fog_environmental_end,
            frame.shader_environment.fog_render_distance_start,
            frame.shader_environment.fog_render_distance_end,
        ];
        let direct_dh_fog_composition = !deferred
            && self.lod_direct_composition_resources.is_some()
            && (frame.lod_render_frame.dh_fog_parameters[16] >= 0.5
                || frame.lod_render_frame.ssao_parameters[0] >= 0.5);
        let audit_private_flip_y = direct_dh_fog_composition
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_FLIP_Y").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let audit_private_no_depth_remap = direct_dh_fog_composition
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_NO_DEPTH_REMAP").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let audit_private_column_ids = direct_dh_fog_composition
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COLUMN_IDS").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let audit_private_no_fade = direct_dh_fog_composition
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_NO_FADE").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let audit_private_raw_transparent_color = matches!(
            crate::core::environment::var("MATTMC_CAPTURE_DH_TRANSPARENT_RAW_COLOR").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) && matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let audit_private_water_debug_color = matches!(
            crate::core::environment::var("MATTMC_CAPTURE_DH_WATER_DEBUG_COLOR").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) && matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let audit_private_raw_lightmap_color = matches!(
            crate::core::environment::var("MATTMC_CAPTURE_DH_LIGHTMAP_RAW_COLOR").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) && matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let audit_private_raw_exact_atlas_color = matches!(
            crate::core::environment::var("MATTMC_CAPTURE_DH_EXACT_ATLAS_RAW_COLOR").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) && matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let audit_private_exact_atlas_base_mip = matches!(
            crate::core::environment::var("MATTMC_CAPTURE_DH_EXACT_ATLAS_BASE_MIP").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) && matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        let audit_private_dither_y = matches!(
            crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_DITHER_Y").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) && matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        // The same bounded audit selector used by the source-derived path is
        // also honored by the ordinary Rust DH pass. This lets a capture
        // isolate exact-atlas sampling from reduced-color fallback without
        // changing normal route admission or resource ownership.
        let audit_exact_atlas_mode = world_lod_audit_exact_atlas_mode();
        // These receipts are available only to graphics-audit captures. Keep
        // their frame-sized transform vector and vertex scans entirely out of
        // normal gameplay; the draw/material route is unchanged.
        let audit_receipts_enabled = matches!(
            crate::core::environment::var("MATTMC_GRAPHICS_AUDIT")
                .as_deref()
                .map(str::trim),
            Ok("1") | Ok("true") | Ok("TRUE")
        );
        if audit_receipts_enabled {
            let ordinary_transform_draws = plan
                .opaque_draws
                .iter()
                .map(|draw| draw.draw)
                .chain(plan.transparent_draws.iter().map(|draw| draw.draw))
                .chain(plan.water_draws.iter().map(|draw| draw.draw))
                .collect::<Vec<_>>();
            self.write_distant_horizons_channel_receipt(frame, visible);
            self.write_distant_horizons_transform_receipt(
                frame,
                "ordinary",
                "vulkanic:builtin/distant_horizons_lod_v1",
                &ordinary_transform_draws,
            );
        }
        // Exact-atlas provenance is per reduced quad. A partial source
        // segment is therefore partitioned into two complementary index
        // ranges: resolved quads use the atlas path and unresolved quads keep
        // the existing DH reduced-color path. Neither pass ever draws the
        // other's range, which avoids both false atlas overlays and holes.
        // The direct vanilla DH route must preserve Frozen's material
        // contract: its standard vertex stage produces copied LOD vertex
        // color multiplied by the lightmap, and it never samples the
        // Minecraft terrain atlas. Replacing that color with atlas texels in
        // the direct path creates animated/checkered detail that Frozen does
        // not render (most visible on water). Exact atlas geometry remains an
        // explicit Rust-owned option for the deferred/source graph, where a
        // selected source contract can declare and consume that material
        // identity. Keeping the admission boundary here prevents the direct
        // path from silently changing shader-disabled vanilla semantics while
        // preserving the future Iris/DH source path.
        let admit_exact_atlas = deferred;
        // The direct route never admits exact-atlas replacement. Build this
        // frame-sized source lookup only for the deferred/source contract,
        // where partial exact-atlas segments actually need it.
        let visible_by_source = admit_exact_atlas.then(|| {
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
        let mut exact_atlas_instances = Vec::new();
        let mut partial_exact_instances = Vec::new();
        if admit_exact_atlas {
            for instance in &frame.lod_instances {
                // Provenance is optional per immutable column. A column with
                // no complete copied material table remains on the validated
                // reduced-color path; it must not make the whole DH frame
                // fail closed now that atlas admission is a production path.
                let Some(asset) = self
                    .lod_textured_gpu_column_assets
                    .get(&instance.column_key)
                    .filter(|asset| asset.column_generation == instance.column_generation)
                else {
                    continue;
                };
                if let Some(complete) = world_lod_exact_atlas_segment_completeness(asset, instance)?
                {
                    // Partial alpha streams cannot be partitioned into a
                    // second draw without changing source blend ordering.
                    // Keep those segments wholly on the reduced-color path;
                    // opaque partial segments retain their complementary
                    // index range as before.
                    if complete || instance.layer == WORLD_LOD_LAYER_OPAQUE {
                        exact_atlas_instances.push(*instance);
                    }
                    if !complete && instance.layer == WORLD_LOD_LAYER_OPAQUE {
                        partial_exact_instances.push(*instance);
                    }
                }
            }
        }
        self.lod_textured_gpu_residency.stage_visible_uploads(
            gal,
            &self.lod_textured_gpu_column_assets,
            &exact_atlas_instances,
            ops,
        )?;
        let mut exact_draws = Vec::new();
        for instance in &exact_atlas_instances {
            let origin = self
                .lod_column_assets
                .get(&instance.column_key)
                .filter(|asset| asset.column_generation == instance.column_generation)
                .map(|asset| asset.origin)
                .ok_or_else(|| GalError::invalid_argument(format!(
                    "world LOD exact-atlas visible instance {} has no matching immutable column asset",
                    instance.column_key
                )))?;
            if let Some(draw) = self.lod_textured_gpu_residency.resolve_visible_draw(
                &self.lod_textured_gpu_column_assets,
                instance,
                origin,
            )? {
                exact_draws.push(draw);
            }
        }
        if admit_exact_atlas {
            for draw in &exact_draws {
                if deferred && draw.layer == WORLD_LOD_LAYER_OPAQUE {
                    continue;
                }
                self.ensure_exact_atlas_pass_resources(gal, lod_color_format, draw.layer, deferred)?;
            }
        }
        let exact_source_segments = exact_draws
            .iter()
            .map(|draw| {
                (
                    draw.column_key,
                    draw.column_generation,
                    draw.layer,
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
                        "world LOD partial exact-atlas segment is absent from the resolved visible source draw set",
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
                        "world LOD partial exact-atlas segment has no complementary reduced-color index range",
                    )
                })?;
            unresolved_coarse_draws.insert(key, unresolved);
        }
        let exact_partial_draw_count = partial_exact_instances.len() as u64;
        let partial_exact_source_segments = partial_exact_instances
            .iter()
            .map(|instance| {
                (
                    instance.column_key,
                    instance.column_generation,
                    instance.layer,
                    instance.segment_index,
                )
            })
            .collect::<BTreeSet<_>>();
        let complete_replacement_draw_count = exact_source_segments
            .difference(&partial_exact_source_segments)
            .count() as u64;
        let unresolved_index_count = partial_exact_instances
            .iter()
            .filter_map(|instance| {
                self.lod_textured_gpu_column_assets
                    .get(&instance.column_key)
                    .and_then(|asset| {
                        asset.segments.iter().find(|segment| {
                            segment.source_segment_index == instance.segment_index
                                && segment.layer == instance.layer
                        })
                    })
                    .and_then(|segment| segment.unresolved_index_bytes.as_ref())
            })
            .map(|bytes| (bytes.len() / std::mem::size_of::<u32>()) as u64)
            .sum();
        self.write_world_lod_exact_atlas_status(
            frame,
            &exact_draws,
            &partial_exact_instances,
            if admit_exact_atlas {
                "ordinary-deferred-atlas-contract"
            } else {
                "ordinary-direct-reduced-color-contract"
            },
            plan.opaque_draws.len() as u64,
            complete_replacement_draw_count,
            exact_draws.len() as u64,
            exact_partial_draw_count,
            exact_draws
                .iter()
                .map(|draw| u64::from(draw.index_count))
                .sum(),
            unresolved_index_count,
        );
        let audit_disable_dh_opaque =
            world_lod_draw_isolation_enabled("MATTMC_RUST_DH_DISABLE_OPAQUE");
        let atlas = if exact_draws.is_empty() {
            None
        } else {
            self.ensure_mesh_texture_resources(
                gal,
                WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
                "world-lod-exact-atlas",
            )?;
            let resources = self
                .mesh_texture_resources
                .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
                .expect("terrain atlas resources exist after successful preparation");
            Some(lod::WorldLodTerrainAtlasBinding {
                mesh_generation: self
                    .mesh_texture_generation(WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS),
                texture_view: resources.view,
                sampler: resources.sampler,
            })
        };
        let mut draws = Vec::with_capacity(
            plan.opaque_draws.len() + plan.transparent_draws.len() + plan.water_draws.len(),
        );
        for admitted in plan.opaque_draws.iter().copied() {
            if audit_disable_dh_opaque {
                continue;
            }
            if matches!(
                audit_exact_atlas_mode,
                WorldLodAuditExactAtlasMode::ExactOnly
            ) {
                continue;
            }
            let key = (
                admitted.draw.column_key,
                admitted.draw.column_generation,
                admitted.draw.layer,
                admitted.draw.segment_index,
            );
            let exact_key = (key.0, key.1, key.2, key.3);
            let mut admitted = if exact_source_segments.contains(&exact_key) {
                let Some(unresolved_draw) = unresolved_coarse_draws.get(&key).copied() else {
                    continue;
                };
                lod::WorldLodOpaqueDraw {
                    draw: unresolved_draw,
                    ..admitted
                }
            } else {
                admitted
            };
            if admitted.draw.index_count == 0 {
                continue;
            }
            admitted.uniforms = admitted.uniforms.with_fog(fog_color_and_alpha, fog_ranges);
            if direct_dh_fog_composition {
                admitted.uniforms = admitted.uniforms.without_dh_fog();
            }
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_flip_y(audit_private_flip_y);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_no_depth_remap(audit_private_no_depth_remap);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_column_ids(audit_private_column_ids);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_no_fade(audit_private_no_fade);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_dither_y(audit_private_dither_y);
            // The forward opaque DH shader owns the same reduced-color and
            // lightmap probe contract as the transparent streams. Keep the
            // flags capture-only: normal frames remain byte-for-byte on the
            // production material path, while diagnostics can isolate
            // opaque terrain that is carrying the paired image difference.
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_transparent_color(audit_private_raw_transparent_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_lightmap_color(audit_private_raw_lightmap_color);
            let prepared = if deferred {
                self.lod_opaque_pass_resources
                    .stage_draw(gal, admitted, lightmap, ops)?
            } else {
                self.lod_forward_opaque_pass_resources
                    .stage_draw(gal, admitted, lightmap, ops)?
            };
            draws.push(TerrainMeshDraw {
                shadow: None,
                pipeline: prepared.pipeline,
                offscreen_pipeline: prepared.offscreen_pipeline,
                pipeline_layout: prepared.pipeline_layout,
                resource_set: prepared.geometry_resource_set,
                resource_set_dynamic_offsets: prepared.uniform_dynamic_offset.into_iter().collect(),
                shader_resource_set: Some(TerrainShaderResourceSet {
                    set_index: 1,
                    set: prepared.lightmap_resource_set,
                }),
                index_buffer: prepared.index_buffer,
                index_offset: prepared.index_offset,
                index_type: prepared.index_type,
                index_count: prepared.index_count,
                instance_count: 1,
                indexed_indirect: None,
                stratum: WORLD_STRATUM_TERRAIN,
                material_mode: TerrainMaterialPassMode::Opaque,
                shadow_participation: TerrainShadowParticipation::Unavailable,
            });
        }
        let exact_uniform_for =
            |draw: lod::WorldLodTexturedGpuDraw| -> GalResult<lod::WorldLodDrawUniform> {
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
                .with_fog(fog_color_and_alpha, fog_ranges);
                let uniforms = if direct_dh_fog_composition {
                    uniforms.without_dh_fog()
                } else {
                    uniforms
                };
                let uniforms = uniforms.with_private_audit_flip_y(audit_private_flip_y);
                let uniforms =
                    uniforms.with_private_audit_no_depth_remap(audit_private_no_depth_remap);
                let uniforms = uniforms.with_private_audit_column_ids(audit_private_column_ids);
                let uniforms = uniforms.with_private_audit_no_fade(audit_private_no_fade);
                let uniforms = uniforms.with_private_audit_dither_y(audit_private_dither_y);
                let uniforms = uniforms
                    .with_private_audit_raw_transparent_color(audit_private_raw_transparent_color);
                let uniforms =
                    uniforms.with_private_audit_water_debug_color(audit_private_water_debug_color);
                let uniforms = uniforms
                    .with_private_audit_raw_lightmap_color(audit_private_raw_lightmap_color);
                let uniforms = uniforms
                    .with_private_audit_raw_exact_atlas_color(audit_private_raw_exact_atlas_color);
                Ok(uniforms
                    .with_private_audit_exact_atlas_base_mip(audit_private_exact_atlas_base_mip))
            };
        if !matches!(
            audit_exact_atlas_mode,
            WorldLodAuditExactAtlasMode::CoarseOnly
        ) {
            if let Some(atlas) = atlas {
                for draw in exact_draws.iter().copied() {
                    if draw.layer != WORLD_LOD_LAYER_OPAQUE {
                        continue;
                    }
                    if audit_disable_dh_opaque {
                        continue;
                    }
                    let uniforms = exact_uniform_for(draw)?;
                    if deferred {
                        let prepared = self
                            .lod_exact_atlas_opaque_pass_resources
                            .stage_draw(gal, draw, uniforms, atlas, lightmap, ops)?;
                        draws.push(TerrainMeshDraw {
                            shadow: None,
                            pipeline: prepared.pipeline,
                            offscreen_pipeline: prepared.offscreen_pipeline,
                            pipeline_layout: prepared.pipeline_layout,
                            resource_set: prepared.geometry_resource_set,
                            resource_set_dynamic_offsets: Vec::new().into(),
                            shader_resource_set: Some(TerrainShaderResourceSet {
                                set_index: 1,
                                set: prepared.atlas_and_lightmap_resource_set,
                            }),
                            index_buffer: prepared.index_buffer,
                            index_offset: 0,
                            index_type: prepared.index_type,
                            index_count: prepared.index_count,
                            instance_count: 1,
                            indexed_indirect: None,
                            stratum: WORLD_STRATUM_TERRAIN,
                            material_mode: TerrainMaterialPassMode::Opaque,
                            shadow_participation: TerrainShadowParticipation::Unavailable,
                        });
                    } else {
                        draws.push(self.stage_exact_atlas_draw(
                            gal, draw, uniforms, atlas, lightmap, deferred, false, ops,
                        )?);
                    }
                }
            }
        }
        let exact_draw_by_key = exact_draws
            .iter()
            .copied()
            .map(|draw| {
                (
                    (
                        draw.column_key,
                        draw.column_generation,
                        draw.layer,
                        draw.source_segment_index,
                    ),
                    draw,
                )
            })
            .collect::<BTreeMap<_, _>>();
        let audit_disable_dh_transparent =
            world_lod_draw_isolation_enabled("MATTMC_RUST_DH_DISABLE_TRANSPARENT");
        let audit_disable_dh_transparent_side =
            world_lod_draw_isolation_enabled("MATTMC_RUST_DH_DISABLE_TRANSPARENT_SIDE");
        let audit_disable_dh_transparent_up =
            world_lod_draw_isolation_enabled("MATTMC_RUST_DH_DISABLE_TRANSPARENT_UP");
        let audit_disable_dh_water =
            world_lod_draw_isolation_enabled("MATTMC_RUST_DH_DISABLE_WATER");
        // The deferred shader graph retains its explicit water prelude. The
        // ordinary forward route instead follows Frozen OpenGL: side, up, and
        // water-up are submitted once under the inherited TRANSPARENT state.
        // Do not copy the Java Vulkan compatibility renderer's two-phase water
        // replay into the Rust-owned implementation; it changes private depth
        // and makes fog discontinuous across a continuous water surface.
        if Self::should_stage_dh_water_in_opaque_lod_phase(deferred) {
            for mut admitted in plan.water_draws.iter().copied() {
                if audit_disable_dh_water {
                    continue;
                }
                let exact_key = (
                    admitted.draw.column_key,
                    admitted.draw.column_generation,
                    admitted.draw.layer,
                    admitted.draw.segment_index,
                );
                if !matches!(
                    audit_exact_atlas_mode,
                    WorldLodAuditExactAtlasMode::CoarseOnly
                ) {
                    if let (Some(atlas), Some(exact_draw)) =
                        (atlas, exact_draw_by_key.get(&exact_key).copied())
                    {
                        let uniforms = exact_uniform_for(exact_draw)?;
                        draws.push(self.stage_exact_atlas_draw(
                            gal, exact_draw, uniforms, atlas, lightmap, deferred, false, ops,
                        )?);
                        continue;
                    }
                }
                admitted.uniforms = admitted.uniforms.with_fog(fog_color_and_alpha, fog_ranges);
                if direct_dh_fog_composition {
                    admitted.uniforms = admitted.uniforms.without_dh_fog();
                }
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_flip_y(audit_private_flip_y);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_no_depth_remap(audit_private_no_depth_remap);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_column_ids(audit_private_column_ids);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_no_fade(audit_private_no_fade);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_dither_y(audit_private_dither_y);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_raw_transparent_color(audit_private_raw_transparent_color);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_water_debug_color(audit_private_water_debug_color);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_raw_lightmap_color(audit_private_raw_lightmap_color);
                admitted.uniforms = admitted
                    .uniforms
                    .with_private_audit_raw_exact_atlas_color(audit_private_raw_exact_atlas_color);
                let prepared = self
                    .lod_water_pass_resources
                    .stage_draw(gal, admitted, lightmap, ops)?;
                draws.push(TerrainMeshDraw {
                    shadow: None,
                    pipeline: prepared.pipeline,
                    offscreen_pipeline: prepared.offscreen_pipeline,
                    pipeline_layout: prepared.pipeline_layout,
                    resource_set: prepared.geometry_resource_set,
                    resource_set_dynamic_offsets: prepared
                        .uniform_dynamic_offset
                        .into_iter()
                        .collect(),
                    shader_resource_set: Some(TerrainShaderResourceSet {
                        set_index: 1,
                        set: prepared.lightmap_resource_set,
                    }),
                    index_buffer: prepared.index_buffer,
                    index_offset: prepared.index_offset,
                    index_type: prepared.index_type,
                    index_count: prepared.index_count,
                    instance_count: 1,
                    indexed_indirect: None,
                    stratum: WORLD_STRATUM_TERRAIN,
                    material_mode: TerrainMaterialPassMode::Translucent,
                    shadow_participation: TerrainShadowParticipation::Unavailable,
                });
            }
        }
        if !deferred {
            // Frozen's ordinary transparent render plan draws every side
            // bucket before every upward bucket, regardless of whether DH
            // fog/SSAO needs the direct compositor. Keep that source order
            // when those effects are disabled too. Reorder the reusable plan
            // in place; its entries are copied below and its capacity remains
            // available for the next frame.
            plan.transparent_draws
                .sort_by_key(|admitted| match admitted.pass {
                    lod::WorldLodPassClass::TransparentSide => 0u8,
                    lod::WorldLodPassClass::TransparentUp => 1u8,
                    lod::WorldLodPassClass::Opaque | lod::WorldLodPassClass::WaterSurface => 2u8,
                });
        }
        let transparent_draws = plan.transparent_draws.iter().copied();
        for mut admitted in transparent_draws {
            if audit_disable_dh_transparent
                || (audit_disable_dh_transparent_side
                    && admitted.pass == lod::WorldLodPassClass::TransparentSide)
                || (audit_disable_dh_transparent_up
                    && admitted.pass == lod::WorldLodPassClass::TransparentUp)
            {
                continue;
            }
            let exact_key = (
                admitted.draw.column_key,
                admitted.draw.column_generation,
                admitted.draw.layer,
                admitted.draw.segment_index,
            );
            if !matches!(
                audit_exact_atlas_mode,
                WorldLodAuditExactAtlasMode::CoarseOnly
            ) {
                if let (Some(atlas), Some(exact_draw)) =
                    (atlas, exact_draw_by_key.get(&exact_key).copied())
                {
                    let uniforms = exact_uniform_for(exact_draw)?;
                    draws.push(self.stage_exact_atlas_draw(
                        gal, exact_draw, uniforms, atlas, lightmap, deferred, false, ops,
                    )?);
                    continue;
                }
            }
            admitted.uniforms = admitted.uniforms.with_fog(fog_color_and_alpha, fog_ranges);
            if direct_dh_fog_composition {
                admitted.uniforms = admitted.uniforms.without_dh_fog();
            }
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_flip_y(audit_private_flip_y);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_no_depth_remap(audit_private_no_depth_remap);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_column_ids(audit_private_column_ids);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_no_fade(audit_private_no_fade);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_dither_y(audit_private_dither_y);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_transparent_color(audit_private_raw_transparent_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_water_debug_color(audit_private_water_debug_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_lightmap_color(audit_private_raw_lightmap_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_exact_atlas_color(audit_private_raw_exact_atlas_color);
            let prepared = if !deferred {
                self.lod_transparent_pass_resources
                    .stage_frozen_opengl_draw(
                        gal,
                        admitted.draw,
                        admitted.uniforms,
                        lightmap,
                        ops,
                    )?
            } else {
                self.lod_transparent_pass_resources
                    .stage_draw(gal, admitted, lightmap, ops)?
            };
            draws.push(TerrainMeshDraw {
                shadow: None,
                pipeline: prepared.pipeline,
                offscreen_pipeline: prepared.offscreen_pipeline,
                pipeline_layout: prepared.pipeline_layout,
                resource_set: prepared.geometry_resource_set,
                resource_set_dynamic_offsets: prepared.uniform_dynamic_offset.into_iter().collect(),
                shader_resource_set: Some(TerrainShaderResourceSet {
                    set_index: 1,
                    set: prepared.lightmap_resource_set,
                }),
                index_buffer: prepared.index_buffer,
                index_offset: prepared.index_offset,
                index_type: prepared.index_type,
                index_count: prepared.index_count,
                instance_count: 1,
                indexed_indirect: None,
                stratum: WORLD_STRATUM_TERRAIN,
                material_mode: TerrainMaterialPassMode::Translucent,
                shadow_participation: TerrainShadowParticipation::Unavailable,
            });
        }
        // Deferred graph water was staged in its explicit prelude above. The
        // ordinary route appends water once after the other transparent
        // buckets, exactly as Frozen OpenGL's default render plan does.
        for mut admitted in plan.water_draws.iter().copied() {
            if deferred {
                continue;
            }
            if audit_disable_dh_water {
                continue;
            }
            // A provenance-resolved segment must use the exact atlas owner in
            // the same source-ordered transparent position as its reduced
            // fallback.
            if !matches!(
                audit_exact_atlas_mode,
                WorldLodAuditExactAtlasMode::CoarseOnly
            ) {
                let exact_key = (
                    admitted.draw.column_key,
                    admitted.draw.column_generation,
                    admitted.draw.layer,
                    admitted.draw.segment_index,
                );
                if let (Some(atlas), Some(exact_draw)) =
                    (atlas, exact_draw_by_key.get(&exact_key).copied())
                {
                    let uniforms = exact_uniform_for(exact_draw)?;
                    draws.push(self.stage_exact_atlas_draw(
                        gal, exact_draw, uniforms, atlas, lightmap, deferred, true, ops,
                    )?);
                    continue;
                }
            }
            admitted.uniforms = admitted.uniforms.with_fog(fog_color_and_alpha, fog_ranges);
            if direct_dh_fog_composition {
                admitted.uniforms = admitted.uniforms.without_dh_fog();
            }
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_flip_y(audit_private_flip_y);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_no_depth_remap(audit_private_no_depth_remap);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_column_ids(audit_private_column_ids);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_no_fade(audit_private_no_fade);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_dither_y(audit_private_dither_y);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_transparent_color(audit_private_raw_transparent_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_water_debug_color(audit_private_water_debug_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_lightmap_color(audit_private_raw_lightmap_color);
            admitted.uniforms = admitted
                .uniforms
                .with_private_audit_raw_exact_atlas_color(audit_private_raw_exact_atlas_color);
            let prepared = self
                .lod_transparent_pass_resources
                .stage_frozen_opengl_draw(gal, admitted.draw, admitted.uniforms, lightmap, ops)?;
            draws.push(TerrainMeshDraw {
                shadow: None,
                pipeline: prepared.pipeline,
                offscreen_pipeline: prepared.offscreen_pipeline,
                pipeline_layout: prepared.pipeline_layout,
                resource_set: prepared.geometry_resource_set,
                resource_set_dynamic_offsets: prepared.uniform_dynamic_offset.into_iter().collect(),
                shader_resource_set: Some(TerrainShaderResourceSet {
                    set_index: 1,
                    set: prepared.lightmap_resource_set,
                }),
                index_buffer: prepared.index_buffer,
                index_offset: prepared.index_offset,
                index_type: prepared.index_type,
                index_count: prepared.index_count,
                instance_count: 1,
                indexed_indirect: None,
                stratum: WORLD_STRATUM_TERRAIN,
                material_mode: TerrainMaterialPassMode::Translucent,
                shadow_participation: TerrainShadowParticipation::Unavailable,
            });
        }
        if !deferred {
            self.lod_forward_opaque_pass_resources
                .flush_packed_uniforms(ops);
        }
        self.lod_transparent_pass_resources
            .flush_packed_uniforms(ops);
        self.lod_water_pass_resources.flush_packed_uniforms(ops);
        Ok(draws)
    }

    /// Only the deferred shader graph needs the explicit water prelude. The
    /// ordinary route follows Frozen OpenGL's single inherited-transparent
    /// submission after the side and non-water upward buckets.
    pub(in crate::render::worldrender) fn should_stage_dh_water_in_opaque_lod_phase(deferred: bool) -> bool {
        deferred
    }

    pub(in crate::render::worldrender) fn ensure_lod_direct_composition_resources(
        &mut self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
        color_format: TextureFormat,
        raster_y_direction: RasterYDirection,
        enabled: bool,
    ) -> GalResult<()> {
        if !enabled {
            return Ok(());
        }
        let extent = gal.pass_target_extent(frame_target)?;
        let compatible = self
            .lod_direct_composition_resources
            .as_ref()
            .is_some_and(|resources| {
                resources.extent == extent
                    && resources.color_format == color_format
                    && resources.raster_y_direction == raster_y_direction
            });
        if compatible {
            return Ok(());
        }
        if let Some(resources) = self.lod_direct_composition_resources.take() {
            resources.destroy(gal);
        }
        self.lod_direct_composition_initialized = false;
        self.pending_lod_direct_composition_written = false;
        self.lod_ssao_initialized = false;
        self.pending_lod_ssao_written = false;
        self.lod_vanilla_sample_state_initialized = false;
        self.pending_lod_vanilla_sample_state_established = false;
        self.lod_direct_composition_resources =
            Some(lod::WorldLodDirectCompositionResources::create(
                gal,
                extent,
                color_format,
                raster_y_direction,
            )?);
        Ok(())
    }

    /// Writes one bounded audit receipt for the DH material split. This is
    /// deliberately recorded after visible exact-atlas draw resolution but
    /// before command construction: it identifies whether a captured frame
    /// can possibly contain exact atlas geometry without exposing backend
    /// objects or changing the fallback route.
    pub(in crate::render::worldrender) fn write_world_lod_exact_atlas_status(
        &mut self,
        frame: &WorldPrimitiveFrame,
        exact_draws: &[lod::WorldLodTexturedGpuDraw],
        partial_exact_instances: &[WorldLodColumnInstanceRequest],
        atlas_admission: &str,
        opaque_source_draw_count: u64,
        complete_replacement_count: u64,
        exact_draw_count: u64,
        partial_exact_draw_count: u64,
        exact_index_count: u64,
        unresolved_index_count: u64,
    ) {
        if !matches!(
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
        let directory = Path::new(&dir);
        let mut receipt_key = self.dh_audit_frame_key(frame);
        for draw in exact_draws {
            for value in [
                draw.column_key,
                draw.column_generation,
                u64::from(draw.layer),
                u64::from(draw.source_segment_index),
                u64::from(draw.order),
                u64::from(draw.index_count),
            ] {
                receipt_key = Self::fnv_update_u64(receipt_key, value);
            }
        }
        for instance in partial_exact_instances {
            for value in [
                instance.column_key,
                instance.column_generation,
                u64::from(instance.layer),
                u64::from(instance.segment_index),
                u64::from(instance.order),
            ] {
                receipt_key = Self::fnv_update_u64(receipt_key, value);
            }
        }
        for value in [
            opaque_source_draw_count,
            complete_replacement_count,
            exact_draw_count,
            partial_exact_draw_count,
            exact_index_count,
            unresolved_index_count,
        ] {
            receipt_key = Self::fnv_update_u64(receipt_key, value);
        }
        if !Self::audit_receipt_is_new(&mut self.last_dh_exact_atlas_status, directory, receipt_key)
        {
            return;
        }
        if std::fs::create_dir_all(directory).is_err() {
            return;
        }
        let planned_source_segments = self
            .lod_textured_column_plans
            .values()
            .map(|plan| plan.segments.len() as u64)
            .sum::<u64>();
        let packed_source_segments = self
            .lod_textured_gpu_column_assets
            .values()
            .map(|asset| asset.segments.len() as u64)
            .sum::<u64>();
        let partial_source_segments = self
            .lod_textured_gpu_column_assets
            .values()
            .map(|asset| asset.unavailable_source_segments.len() as u64)
            .sum::<u64>();
        let mut unavailable_reason_counts = BTreeMap::<&'static str, u64>::new();
        for plan in self.lod_textured_column_plans.values() {
            for segment in &plan.segments {
                for unavailable in &segment.unavailable {
                    *unavailable_reason_counts
                        .entry(unavailable.reason.as_str())
                        .or_default() += 1;
                }
            }
        }
        let unavailable_reason_counts = unavailable_reason_counts
            .into_iter()
            .map(|(reason, count)| format!("\"{}\":{}", reason, count))
            .collect::<Vec<_>>();
        // Preserve the first semantic boundary that made a visible reduced
        // quad ineligible for exact atlas sampling. This is capture-only
        // provenance: it cannot choose a fallback sprite or alter admission.
        let mut unavailable_material_counts = BTreeMap::<(String, u32, &'static str), u64>::new();
        for (column_key, plan) in &self.lod_textured_column_plans {
            let identities = self
                .lod_material_provenance
                .get(column_key)
                .filter(|provenance| provenance.column_generation == plan.column_generation)
                .map(|provenance| &provenance.identities);
            for segment in &plan.segments {
                for unavailable in &segment.unavailable {
                    let identity = identities
                        .and_then(|identities| {
                            unavailable
                                .material_id
                                .checked_sub(1)
                                .and_then(|index| identities.get(index as usize))
                        })
                        .map(|identity| identity.block_state_identity.as_str())
                        .unwrap_or("<unavailable-material>");
                    *unavailable_material_counts
                        .entry((
                            identity.to_owned(),
                            unavailable.face,
                            unavailable.reason.as_str(),
                        ))
                        .or_default() += 1;
                }
            }
        }
        let unavailable_material_samples = unavailable_material_counts
            .into_iter()
            .rev()
            .take(32)
            .map(|((identity, face, reason), count)| {
                format!(
                    "{{\"identity\":\"{}\",\"face\":{},\"reason\":\"{}\",\"count\":{}}}",
                    json_escape(&identity),
                    face,
                    reason,
                    count,
                )
            })
            .collect::<Vec<_>>();
        // The Java receipt can prove a model's candidate sprite, but it cannot
        // prove that Rust selected the same face record for the exact source
        // segment drawn in this frame. Keep a bounded, backend-neutral receipt
        // next to the aggregate status so texture reports can be traced past
        // the FFI boundary without exposing a Vulkan image or descriptor.
        let exact_keys = exact_draws
            .iter()
            .map(|draw| {
                (
                    draw.column_key,
                    draw.column_generation,
                    draw.source_segment_index,
                )
            })
            .collect::<BTreeSet<_>>();
        let mut exact_draw_layer_counts = BTreeMap::<u32, u64>::new();
        for draw in exact_draws {
            *exact_draw_layer_counts.entry(draw.layer).or_default() += 1;
        }
        let exact_draw_layer_counts = exact_draw_layer_counts
            .into_iter()
            .map(|(layer, count)| format!("\"{}\":{}", layer, count))
            .collect::<Vec<_>>();
        let mut partial_exact_draw_layer_counts = BTreeMap::<u32, u64>::new();
        for instance in partial_exact_instances {
            *partial_exact_draw_layer_counts
                .entry(instance.layer)
                .or_default() += 1;
        }
        let partial_exact_draw_layer_counts = partial_exact_draw_layer_counts
            .into_iter()
            .map(|(layer, count)| format!("\"{}\":{}", layer, count))
            .collect::<Vec<_>>();
        let mut sprite_counts = BTreeMap::<String, u64>::new();
        let mut samples = Vec::new();
        // Keep the receipt bounded but representative. Columns are ordered by
        // key, so retaining the first quads alone mostly recorded bedrock and
        // hid the exact sprite associations this diagnostic exists to audit.
        // Reserve one entry per sprite, up to 128 distinct sprites.
        let mut sampled_sprites = BTreeSet::new();
        for (column_key, plan) in &self.lod_textured_column_plans {
            for (segment_index, segment) in plan.segments.iter().enumerate() {
                let Ok(segment_index) = u32::try_from(segment_index) else {
                    continue;
                };
                if !exact_keys.contains(&(*column_key, plan.column_generation, segment_index)) {
                    continue;
                }
                for quad in &segment.quads {
                    *sprite_counts
                        .entry(quad.sprite_identity.clone())
                        .or_default() += 1;
                    if samples.len() < 128 && sampled_sprites.insert(quad.sprite_identity.clone()) {
                        samples.push(format!(
                            concat!(
                                "{{\"columnKey\":{},\"columnGeneration\":{},",
                                "\"segment\":{},\"quad\":{},\"materialId\":{},\"face\":{},",
                                "\"atlas\":\"{}\",\"sprite\":\"{}\",",
                                "\"tinted\":{},\"color\":[{:.6},{:.6},{:.6}],",
                                "\"uv0\":[{:.7},{:.7}],\"uv2\":[{:.7},{:.7}]}}"
                            ),
                            column_key,
                            plan.column_generation,
                            segment_index,
                            quad.quad_index,
                            quad.material_id,
                            quad.face,
                            json_escape(&quad.atlas_identity),
                            json_escape(&quad.sprite_identity),
                            quad.tinted,
                            quad.vertices[0].color_rgba[0],
                            quad.vertices[0].color_rgba[1],
                            quad.vertices[0].color_rgba[2],
                            quad.vertices[0].atlas_rect[0],
                            quad.vertices[0].atlas_rect[1],
                            quad.vertices[0].atlas_rect[2],
                            quad.vertices[0].atlas_rect[3],
                        ));
                    }
                }
            }
        }
        // Keep requested exact-atlas witness sprites in the bounded summary
        // even when the column contains more than 96 ordinary terrain sprites.
        // The full spatial proof remains in `paletteTargetCoverage`; this only
        // prevents the compact count map from hiding a required witness due to
        // lexicographic truncation.
        let required_palette_sprites = read_world_lod_exact_atlas_palette_targets(Path::new(&dir))
            .into_iter()
            .flat_map(|target| target.required_sprites)
            .collect::<BTreeSet<_>>();
        let mut sprite_count_entries = sprite_counts.into_iter().collect::<Vec<_>>();
        sprite_count_entries.sort_by(|(left, _), (right, _)| {
            required_palette_sprites
                .contains(right)
                .cmp(&required_palette_sprites.contains(left))
                .then_with(|| left.cmp(right))
        });
        let sprite_counts = sprite_count_entries
            .into_iter()
            .take(96)
            .map(|(sprite, count)| format!("\"{}\":{}", json_escape(&sprite), count))
            .collect::<Vec<_>>();
        let atlas = self
            .mesh_texture_assets
            .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS)
            .map(|atlas| {
                format!(
                    "\"atlas\":{{\"extent\":[{},{}],\"rgbaHash\":\"{:016x}\"}}",
                    atlas.width,
                    atlas.height,
                    fnv64_bytes(&atlas.rgba),
                )
            })
            .unwrap_or_else(|| "\"atlas\":null".to_string());
        let terrain_atlas = self
            .mesh_texture_assets
            .get(&WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS);
        let palette_target_coverage = read_world_lod_exact_atlas_palette_targets(Path::new(&dir))
            .into_iter()
            .map(|target| {
                let projection = world_lod_exact_atlas_target_projection(
                    frame.lod_render_frame.combined_matrix,
                    frame.voxel_volume.camera_world_position,
                    target.position,
                );
                let mut observed_sprites = BTreeSet::new();
                let mut matches = Vec::new();
                let mut nearest_expected = None;
                let mut tile_repeat_required = false;
                for (column_key, plan) in &self.lod_textured_column_plans {
                    let Some(origin) = self.lod_column_assets.get(column_key).and_then(|asset| {
                        (asset.column_generation == plan.column_generation).then_some(asset.origin)
                    }) else {
                        continue;
                    };
                    for (segment_index, segment) in plan.segments.iter().enumerate() {
                        let Ok(segment_index) = u32::try_from(segment_index) else {
                            continue;
                        };
                        if !exact_keys.contains(&(*column_key, plan.column_generation, segment_index)) {
                            continue;
                        }
                        for quad in &segment.quads {
                            let bounds = world_lod_textured_quad_world_bounds(origin, quad);
                            if target.expected_sprites.contains(&quad.sprite_identity) {
                                let distance = world_lod_exact_atlas_target_distance_squared(
                                    bounds,
                                    target.position,
                                );
                                if nearest_expected
                                    .as_ref()
                                    .is_none_or(|(nearest, _): &(f32, String)| distance < *nearest)
                                {
                                    nearest_expected = Some((
                                        distance,
                                        format!(
                                            concat!(
                                                "{{\"sprite\":\"{}\",\"faceLayer\":{},",
                                                "\"worldBounds\":[{:.3},{:.3},{:.3},{:.3},{:.3},{:.3}],",
                                                "\"distanceSquared\":{:.3}}}"
                                            ),
                                            json_escape(&quad.sprite_identity),
                                            quad.face_layer,
                                            bounds[0],
                                            bounds[1],
                                            bounds[2],
                                            bounds[3],
                                            bounds[4],
                                            bounds[5],
                                            distance,
                                        ),
                                    ));
                                }
                            }
                            if !world_lod_exact_atlas_target_intersects(bounds, target.position) {
                                continue;
                            }
                            observed_sprites.insert(quad.sprite_identity.clone());
                            if target.expected_sprites.contains(&quad.sprite_identity) {
                                let largest_world_span = [
                                    bounds[3] - bounds[0],
                                    bounds[4] - bounds[1],
                                    bounds[5] - bounds[2],
                                ]
                                .into_iter()
                                .fold(0.0_f32, f32::max);
                                tile_repeat_required |= largest_world_span > 1.5;
                            }
                            if target.expected_sprites.contains(&quad.sprite_identity)
                                && matches.len() < 32
                            {
                                let quad_projection = world_lod_exact_atlas_quad_projection(
                                    frame.lod_render_frame.combined_matrix,
                                    frame.voxel_volume.camera_world_position,
                                    origin,
                                    quad,
                                );
                                let vertex_inputs = quad
                                    .vertices
                                    .iter()
                                    .map(|vertex| {
                                        format!(
                                            concat!(
                                                "{{\"color\":[{:.6},{:.6},{:.6},{:.6}],",
                                                "\"light\":[{},{}],\"tileUv\":[{:.6},{:.6}]}}"
                                            ),
                                            vertex.color_rgba[0],
                                            vertex.color_rgba[1],
                                            vertex.color_rgba[2],
                                            vertex.color_rgba[3],
                                            vertex.sky_light,
                                            vertex.block_light,
                                            vertex.tile_uv[0],
                                            vertex.tile_uv[1],
                                        )
                                    })
                                    .collect::<Vec<_>>();
                                let atlas_source = world_lod_atlas_rect_diagnostic(
                                    terrain_atlas,
                                    quad.vertices[0].atlas_rect,
                                );
                                matches.push(format!(
                                    concat!(
                                        "{{\"columnKey\":{},\"columnGeneration\":{},",
                                        "\"segment\":{},\"quad\":{},\"faceLayer\":{},\"tinted\":{},\"tintRgb\":[{:.6},{:.6},{:.6}],\"sprite\":\"{}\",",
                                        "\"worldBounds\":[{:.3},{:.3},{:.3},{:.3},{:.3},{:.3}],",
                                        "\"tileSpan\":[{:.3},{:.3}],\"atlasRect\":[{:.7},{:.7},{:.7},{:.7}],",
                                        "\"vertexInputs\":[{}],\"atlasSource\":{},\"projection\":{}}}"
                                    ),
                                    column_key,
                                    plan.column_generation,
                                    segment_index,
                                    quad.quad_index,
                                    quad.face_layer,
                                    quad.tinted,
                                    quad.vertices[0].color_rgba[0],
                                    quad.vertices[0].color_rgba[1],
                                    quad.vertices[0].color_rgba[2],
                                    json_escape(&quad.sprite_identity),
                                    bounds[0],
                                    bounds[1],
                                    bounds[2],
                                    bounds[3],
                                    bounds[4],
                                    bounds[5],
                                    lod::world_lod_textured_quad_tile_span(quad)[0],
                                    lod::world_lod_textured_quad_tile_span(quad)[1],
                                    quad.vertices[0].atlas_rect[0],
                                    quad.vertices[0].atlas_rect[1],
                                    quad.vertices[0].atlas_rect[2],
                                    quad.vertices[0].atlas_rect[3],
                                    vertex_inputs.join(","),
                                    atlas_source,
                                    quad_projection,
                                ));
                            }
                        }
                    }
                }
                let expected_sprites = target
                    .expected_sprites
                    .iter()
                    .map(|sprite| format!("\"{}\"", json_escape(sprite)))
                    .collect::<Vec<_>>();
                let required_present = target.required_sprites.is_subset(&observed_sprites);
                let observed_sprites = observed_sprites
                    .into_iter()
                    .take(16)
                    .map(|sprite| format!("\"{}\"", json_escape(&sprite)))
                    .collect::<Vec<_>>();
                let required_sprites = target
                    .required_sprites
                    .iter()
                    .map(|sprite| format!("\"{}\"", json_escape(sprite)))
                    .collect::<Vec<_>>();
                let nearest_expected = nearest_expected
                    .map(|(_, diagnostic)| diagnostic)
                    .unwrap_or_else(|| "null".to_string());
                format!(
                    concat!(
                        "{{\"position\":[{},{},{}],\"expectedSprites\":[{}],",
                        "\"requiredSprites\":[{}],",
                        "\"projection\":{},\"matched\":{},\"tileRepeatRequired\":{},",
                        "\"observedSprites\":[{}],\"matches\":[{}],",
                        "\"nearestExpectedCandidate\":{}}}"
                    ),
                    target.position[0],
                    target.position[1],
                    target.position[2],
                    expected_sprites.join(","),
                    required_sprites.join(","),
                    projection,
                    !matches.is_empty() && required_present,
                    tile_repeat_required,
                    observed_sprites.join(","),
                    matches.join(","),
                    nearest_expected,
                )
            })
            .collect::<Vec<_>>();
        let _ = std::fs::write(
            Path::new(&dir).join("world-lod-exact-atlas-last.json"),
            format!(
                concat!(
                    "{{\"frame_id\":{},\"world_generation\":{},",
                    "\"atlas_admission\":\"{}\",",
                    "\"visible_lod_instances\":{},\"opaque_source_draws\":{},",
                    "\"planned_columns\":{},\"planned_source_segments\":{},",
                    "\"packed_columns\":{},\"packed_source_segments\":{},",
                    "\"partial_source_segments\":{},",
                    "\"complete_replacement_draws\":{},\"exact_draws_staged\":{},",
                    "\"exact_draw_layer_counts\":{{{}}},",
                    "\"partial_exact_draw_layer_counts\":{{{}}},",
                    "\"partial_exact_draws_staged\":{},\"exact_indices_staged\":{},",
                    "\"unresolved_indices_staged\":{},\"unavailable_reason_counts\":{{{}}},",
                    "\"unavailable_material_samples\":[{}]}}\n"
                ),
                frame.frame_id,
                frame.voxel_volume.world_generation,
                json_escape(atlas_admission),
                frame.lod_instances.len(),
                opaque_source_draw_count,
                self.lod_textured_column_plans.len(),
                planned_source_segments,
                self.lod_textured_gpu_column_assets.len(),
                packed_source_segments,
                partial_source_segments,
                complete_replacement_count,
                exact_draw_count,
                exact_draw_layer_counts.join(","),
                partial_exact_draw_layer_counts.join(","),
                partial_exact_draw_count,
                exact_index_count,
                unresolved_index_count,
                unavailable_reason_counts.join(","),
                unavailable_material_samples.join(","),
            ),
        );
        // A Java-side provenance receipt can outlive a DH column replacement.
        // Publish the exact-atlas planner's frame-local verdict separately so
        // the deterministic capture refuses to certify an unrelated draw.
        let palette_targets_matched =
            world_lod_exact_atlas_palette_targets_match(&palette_target_coverage);
        let plan = format!(
            concat!(
                "{{\"schema\":\"mattmc-world-lod-exact-atlas-plan-v1\",",
                "\"frameId\":{},\"correlationId\":{},\"worldGeneration\":{},",
                "\"atlasAdmission\":\"{}\",",
                "\"executedExactSegments\":{},\"selectedSpriteCounts\":{{{}}},",
                "\"unavailableReasonCounts\":{{{}}},\"unavailableMaterialSamples\":[{}],",
                "\"samples\":[{}],\"paletteTargetsMatched\":{},\"paletteTargetCoverage\":[{}],",
                "\"distantTransformConsistency\":{},{} }}\n"
            ),
            frame.frame_id,
            frame.correlation_id,
            frame.voxel_volume.world_generation,
            json_escape(atlas_admission),
            exact_keys.len(),
            sprite_counts.join(","),
            unavailable_reason_counts.join(","),
            unavailable_material_samples.join(","),
            samples.join(","),
            palette_targets_matched,
            palette_target_coverage.join(","),
            world_lod_transform_consistency_diagnostic(frame.lod_render_frame),
            atlas,
        );
        let dir = Path::new(&dir);
        // Keep the summary receipt on the latest successful exact-atlas plan.
        // DH may legitimately emit an alternating empty frame while a dynamic
        // source generation is replaced; overwriting a proven receipt with that
        // transient frame would make the capture validator lose the completed
        // evidence even though every frame-local receipt remains available.
        let last_plan = dir.join("world-lod-exact-atlas-plan-last.json");
        if palette_targets_matched || !last_plan.exists() {
            let _ = std::fs::write(&last_plan, &plan);
        }
        let _ = std::fs::write(
            dir.join(format!(
                "world-lod-exact-atlas-plan-frame-{}.json",
                frame.frame_id
            )),
            plan,
        );
        Self::trim_world_lod_exact_atlas_plan_receipts(dir, 12);
    }

    /// Audit-only capture receipts are frame-bound so the harness can reject a
    /// screenshot paired with a stale material plan. Keep the directory
    /// bounded even for unusually long diagnostic sessions.
    pub(in crate::render::worldrender) fn trim_world_lod_exact_atlas_plan_receipts(dir: &Path, maximum: usize) {
        let mut plans = std::fs::read_dir(dir)
            .ok()
            .into_iter()
            .flatten()
            .filter_map(|entry| {
                let entry = entry.ok()?;
                let name = entry.file_name();
                let name = name.to_str()?;
                let frame = name
                    .strip_prefix("world-lod-exact-atlas-plan-frame-")?
                    .strip_suffix(".json")?
                    .parse::<u64>()
                    .ok()?;
                Some((frame, entry.path()))
            })
            .collect::<Vec<_>>();
        plans.sort_unstable_by_key(|(frame, _)| *frame);
        let remove_count = plans.len().saturating_sub(maximum);
        for (_, path) in plans.into_iter().take(remove_count) {
            let _ = std::fs::remove_file(path);
        }
    }

    /// Destroys only source set-one consumers that bind the vanilla lightmap
    /// (their keys carry its `Lightmap` role generation) before the runtime
    /// replaces or drops a lightmap generation. Packs that never sample the
    /// lightmap keep their sets, instead of rebuilding every pack set each
    /// time the lightmap changes (nearly every frame).
    /// Stages DH generic boxes (LOD clouds, beacon beams) as `dh_terrain`
    /// source draws, as Iris does when a pack has no `dh_generic` program.
    /// They are appended right after the opaque DH LODs (DH's
    /// GenericObjectRenderer order) with DH's generic alpha blend.
    pub(in crate::render::worldrender) fn stage_distant_horizons_generic_source_draws(
        &mut self,
        gal: &mut VulkanicGal,
        frame: &WorldPrimitiveFrame,
        geometry: DistantHorizonsGenericSourceGeometry,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
        source_uniforms: &TerrainSourceUniformFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<Vec<lod::WorldLodPreparedSourceDraw>> {
        if geometry.indices.is_empty() {
            return Ok(Vec::new());
        }
        let current = self.dh_generic_source_buffers.as_ref().is_some_and(|buffers| {
            buffers.vertices == geometry.vertices && buffers.indices == geometry.indices
        });
        if !current {
            self.discard_distant_horizons_generic_source_buffers(gal);
            self.dh_generic_source_generation += 1;
            let generation = self.dh_generic_source_generation;
            let vertex_bytes = geometry
                .vertices
                .iter()
                .flatten()
                .flat_map(|word| word.to_le_bytes())
                .collect::<Vec<u8>>();
            let index_bytes = geometry
                .indices
                .iter()
                .flat_map(|index| index.to_le_bytes())
                .collect::<Vec<u8>>();
            let vertex_buffer = gal.create_buffer(BufferDesc {
                label: format!("dh-generic-source-gen{generation}.vertices"),
                size: vertex_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Storage, BufferUsage::HostWrite],
            })?;
            let index_buffer = match gal.create_buffer(BufferDesc {
                label: format!("dh-generic-source-gen{generation}.indices"),
                size: index_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Index, BufferUsage::HostWrite],
            }) {
                Ok(handle) => handle,
                Err(error) => {
                    let _ = gal.retire(vertex_buffer);
                    return Err(error);
                }
            };
            operations.extend([
                CommandOp::HostWriteBuffer {
                    buffer: vertex_buffer,
                    offset: 0,
                    data: vertex_bytes,
                },
                CommandOp::Barrier(buffer_barrier(
                    vertex_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::HostWriteBuffer {
                    buffer: index_buffer,
                    offset: 0,
                    data: index_bytes,
                },
                CommandOp::Barrier(buffer_barrier(
                    index_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::IndexRead,
                )),
            ]);
            self.dh_generic_source_buffers = Some(DistantHorizonsGenericSourceBuffers {
                generation,
                vertices: geometry.vertices.clone(),
                indices: geometry.indices.clone(),
                vertex_buffer,
                index_buffer,
            });
        }
        let buffers = self
            .dh_generic_source_buffers
            .as_ref()
            .expect("generic DH source buffers exist after staging");
        let (vertex_buffer, index_buffer, generation) =
            (buffers.vertex_buffer, buffers.index_buffer, buffers.generation);
        let mut draws = Vec::with_capacity(geometry.ranges.len());
        for (segment, range) in geometry.ranges.iter().enumerate() {
            let draw = lod::WorldLodGpuDraw {
                column_key: u64::MAX,
                column_generation: generation,
                origin: [0; 3],
                layer: 0,
                segment_index: segment as u32,
                order: 0,
                vertex_buffer,
                vertex_base: range.vertex_base,
                index_buffer,
                index_offset: u64::from(range.first_index) * 4,
                index_type: IndexType::U32,
                index_count: range.index_count,
            };
            let mut uniforms = lod::WorldLodDrawUniform::from_semantics_with_camera(
                &frame.lod_render_frame,
                draw,
                [0.0; 3],
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
            // Box corners are camera-relative; the range's shared sub-block
            // fraction is the model offset of its i16 local positions.
            uniforms.model_offset_and_reserved = [
                range.model_offset[0],
                range.model_offset[1],
                range.model_offset[2],
                range.vertex_base as f32,
            ];
            draws.push(self.lod_source_pass_resources.stage_generic_draw(
                gal,
                program,
                color_format,
                draw,
                uniforms,
                source_uniforms,
                operations,
            )?);
        }
        Ok(draws)
    }

    /// Drops the generic-box geometry (and the draw sets binding it). Used
    /// when its content changes and when a frame that would have uploaded it
    /// is discarded, so never-uploaded buffers are not reused.
    pub(in crate::render::worldrender) fn discard_distant_horizons_generic_source_buffers(&mut self, gal: &mut VulkanicGal) {
        self.lod_source_pass_resources.retire_generic_draws(gal);
        if let Some(previous) = self.dh_generic_source_buffers.take() {
            let _ = gal.retire(previous.vertex_buffer);
            let _ = gal.retire(previous.index_buffer);
        }
    }
}

/// The deferred graph is an explicit *admitted source-execution* composition
/// path, not a generic semantic-environment path. The environment record is
/// also required by vanilla for copied lightmap/fog inputs, so its `enabled`
/// bit cannot select shader-pack composition. Until the selected source graph
/// is fully armed, keep terrain on the direct Rust vanilla pass.
/// One DH generic-object range for the selected `dh_terrain` program. Boxes
/// sharing a sub-block camera fraction share one model offset, so their
/// corners stay exact in the DH source stream's i16 local positions.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(in crate::render::worldrender) struct DistantHorizonsGenericSourceRange {
    pub(in crate::render::worldrender) model_offset: [f32; 3],
    pub(in crate::render::worldrender) vertex_base: u32,
    /// First index of this range, in indices (u32).
    pub(in crate::render::worldrender) first_index: u32,
    pub(in crate::render::worldrender) index_count: u32,
}

/// DH generic boxes expanded into the DH source vertex stream (one `uvec4`
/// per vertex: i16 xyz, RGBA8 color, sky/block light, DH material, normal).
/// Mirrors Iris's `DHGenericTransformer`: unshaded box color, per-face
/// normals, the group's block/sky light, and the box's DH material.
#[derive(Clone, Debug, Default, PartialEq)]
pub(in crate::render::worldrender) struct DistantHorizonsGenericSourceGeometry {
    pub(in crate::render::worldrender) vertices: Vec<[u32; 4]>,
    pub(in crate::render::worldrender) indices: Vec<u32>,
    pub(in crate::render::worldrender) ranges: Vec<DistantHorizonsGenericSourceRange>,
}

pub(in crate::render::worldrender) fn distant_horizons_generic_source_geometry(
    boxes: &[WorldDistantHorizonsGenericBoxRequest],
) -> GalResult<DistantHorizonsGenericSourceGeometry> {
    let mut geometry = DistantHorizonsGenericSourceGeometry::default();
    // Six faces of four vertices and six indices per box.
    geometry.vertices.reserve(boxes.len() * 24);
    geometry.indices.reserve(boxes.len() * 36);
    // DH draws SSAO groups before non-SSAO groups; keep that order.
    for ssao in [true, false] {
        let mut bucket_order = Vec::<u32>::new();
        let mut buckets = HashMap::<u32, ([f32; 3], Vec<usize>)>::new();
        for (index, item) in boxes.iter().enumerate() {
            if item.ssao_enabled != ssao {
                continue;
            }
            let fraction = item.min.map(|value| value - value.floor());
            for axis in 0..3 {
                let local_max = item.max[axis] - fraction[axis];
                if (local_max - local_max.round()).abs() > 1.0e-3 {
                    return Err(GalError::unsupported_feature(
                        "DH generic box is not representable in the DH source vertex stream",
                    ));
                }
            }
            let bucket = buckets.entry(item.group).or_insert_with(|| {
                bucket_order.push(item.group);
                (fraction, Vec::new())
            });
            // One group shares one origin, hence one sub-block fraction.
            if (0..3).any(|axis| {
                let delta = (fraction[axis] - bucket.0[axis]).abs();
                delta > 1.0e-3 && delta < 1.0 - 1.0e-3
            }) {
                return Err(GalError::unsupported_feature(
                    "DH generic box group has inconsistent sub-block offsets",
                ));
            }
            bucket.1.push(index);
        }
        for key in bucket_order {
            let (fraction, members) = &buckets[&key];
            // Anchor the range at its first box: a moving group (drifting
            // clouds, a moving camera) then keeps identical local vertices and
            // only its model offset changes, so geometry is not re-uploaded.
            let anchor = boxes[members[0]]
                .min
                .map(|value| value.floor() as i32);
            let vertex_base = geometry.vertices.len() as u32;
            let first_index = geometry.indices.len() as u32;
            for &index in members {
                let item = &boxes[index];
                // Both corners are validated above to lie within 1e-3 of whole blocks.
                let a = [0, 1, 2].map(|axis| {
                    round_near_integer(item.min[axis] - fraction[axis]) - anchor[axis]
                });
                let b = [0, 1, 2].map(|axis| {
                    round_near_integer(item.max[axis] - fraction[axis]) - anchor[axis]
                });
                if a.iter().chain(b.iter()).any(|value| value.abs() > 32767) {
                    return Err(GalError::unsupported_feature(
                        "DH generic box range exceeds the DH source vertex stream",
                    ));
                }
                let argb = item.color_argb;
                let color = ((argb >> 16) & 0xff)
                    | (((argb >> 8) & 0xff) << 8)
                    | ((argb & 0xff) << 16)
                    | (((argb >> 24) & 0xff) << 24);
                let block = (item.packed_light >> 4) & 0xf;
                let sky = (item.packed_light >> 20) & 0xf;
                let material = item.material & 0xff;
                // Counter-clockwise from outside (the DH source front face).
                let faces: [(u32, [[i32; 3]; 4]); 6] = [
                    (0, [[a[0], a[1], a[2]], [b[0], a[1], a[2]], [b[0], a[1], b[2]], [a[0], a[1], b[2]]]),
                    (1, [[a[0], b[1], a[2]], [a[0], b[1], b[2]], [b[0], b[1], b[2]], [b[0], b[1], a[2]]]),
                    (2, [[b[0], a[1], a[2]], [a[0], a[1], a[2]], [a[0], b[1], a[2]], [b[0], b[1], a[2]]]),
                    (3, [[a[0], a[1], b[2]], [b[0], a[1], b[2]], [b[0], b[1], b[2]], [a[0], b[1], b[2]]]),
                    (4, [[a[0], a[1], a[2]], [a[0], a[1], b[2]], [a[0], b[1], b[2]], [a[0], b[1], a[2]]]),
                    (5, [[b[0], a[1], b[2]], [b[0], a[1], a[2]], [b[0], b[1], a[2]], [b[0], b[1], b[2]]]),
                ];
                for (normal, corners) in faces {
                    let local = geometry.vertices.len() as u32 - vertex_base;
                    for corner in corners {
                        let [x, y, z] = corner.map(|value| u32::from(value as i16 as u16));
                        geometry.vertices.push([
                            x | (y << 16),
                            z,
                            color,
                            sky | (block << 8) | (material << 16) | (normal << 24),
                        ]);
                    }
                    geometry.indices.extend_from_slice(&[
                        local,
                        local + 1,
                        local + 2,
                        local + 2,
                        local + 3,
                        local,
                    ]);
                }
            }
            geometry.ranges.push(DistantHorizonsGenericSourceRange {
                model_offset: [0, 1, 2].map(|axis| anchor[axis] as f32 + fraction[axis]),
                vertex_base,
                first_index,
                index_count: geometry.indices.len() as u32 - first_index,
            });
        }
    }
    Ok(geometry)
}

/// Rounds a value known to lie within 1e-3 of an integer, without a libm call.
fn round_near_integer(value: f32) -> i32 {
    (value + 0.5f32.copysign(value)) as i32
}

/// Admission check for [`distant_horizons_generic_source_geometry`] without
/// building vertices: every group needs one consistent sub-block offset and
/// an i16-representable extent around its first box.
pub(in crate::render::worldrender) fn validate_distant_horizons_generic_source_boxes(
    boxes: &[WorldDistantHorizonsGenericBoxRequest],
) -> GalResult<()> {
    let mut groups = HashMap::<(bool, u32), ([f32; 3], [i32; 3])>::new();
    for item in boxes {
        let fraction = item.min.map(|value| value - value.floor());
        for axis in 0..3 {
            let local_max = item.max[axis] - fraction[axis];
            if (local_max - local_max.round()).abs() > 1.0e-3 {
                return Err(GalError::unsupported_feature(
                    "DH generic box is not representable in the DH source vertex stream",
                ));
            }
        }
        let (group_fraction, anchor) = *groups
            .entry((item.ssao_enabled, item.group))
            .or_insert_with(|| (fraction, item.min.map(|value| value.floor() as i32)));
        for axis in 0..3 {
            let delta = (fraction[axis] - group_fraction[axis]).abs();
            if delta > 1.0e-3 && delta < 1.0 - 1.0e-3 {
                return Err(GalError::unsupported_feature(
                    "DH generic box group has inconsistent sub-block offsets",
                ));
            }
            let low = (item.min[axis] - group_fraction[axis]).round() as i64 - i64::from(anchor[axis]);
            let high = (item.max[axis] - group_fraction[axis]).round() as i64 - i64::from(anchor[axis]);
            if low.abs() > 32767 || high.abs() > 32767 {
                return Err(GalError::unsupported_feature(
                    "DH generic box range exceeds the DH source vertex stream",
                ));
            }
        }
    }
    Ok(())
}

/// Transient GPU copy of the current generic-box geometry. Replaced (never
/// rewritten in place) whenever its content changes, so an in-flight frame
/// keeps reading its own buffers until GAL retires them.
pub(in crate::render::worldrender) struct DistantHorizonsGenericSourceBuffers {
    pub(in crate::render::worldrender) generation: u64,
    pub(in crate::render::worldrender) vertices: Vec<[u32; 4]>,
    pub(in crate::render::worldrender) indices: Vec<u32>,
    pub(in crate::render::worldrender) vertex_buffer: Handle,
    pub(in crate::render::worldrender) index_buffer: Handle,
}

/// DH generic objects (clouds, beacon beams) arrive both as generic-stratum
/// material quads and as instanced boxes.
pub(in crate::render::worldrender) fn frame_has_distant_horizons_generic_objects(frame: &WorldPrimitiveFrame) -> bool {
    !frame.dh_generic_boxes.is_empty()
        || frame
            .material_quads
            .iter()
            .any(|quad| is_distant_horizons_generic_stratum(quad.stratum))
}

pub(in crate::render::worldrender) fn uses_shader_g_buffer_mesh_path(
    frame: &WorldPrimitiveFrame,
    clear_background: bool,
    source_execution_armed: bool,
    fabulous_terrain_handoff: bool,
) -> bool {
    clear_background
        && (source_execution_armed || fabulous_terrain_handoff)
        && (frame
            .mesh_instances
            .iter()
            .any(|instance| instance.stratum != WORLD_STRATUM_ENTITY_SHADOW_CASTER)
            || (frame.lod_render_frame.rust_route_selected() && !frame.lod_instances.is_empty()))
}

#[derive(Debug)]
pub(in crate::render::worldrender) struct WorldLodExactAtlasPaletteTarget {
    pub(in crate::render::worldrender) position: [i32; 3],
    pub(in crate::render::worldrender) expected_sprites: BTreeSet<String>,
    pub(in crate::render::worldrender) required_sprites: BTreeSet<String>,
}

pub(in crate::render::worldrender) fn world_lod_exact_atlas_palette_targets_match(coverage: &[String]) -> bool {
    !coverage.is_empty()
        && coverage
            .iter()
            .all(|entry| entry.contains("\"matched\":true"))
}

/// Reads an optional capture-only target list written by the deterministic
/// Java fixture. It cannot influence frontend admission or draw construction;
/// it only narrows the already-executed exact-atlas receipt to target blocks.
pub(in crate::render::worldrender) fn read_world_lod_exact_atlas_palette_targets(dir: &Path) -> Vec<WorldLodExactAtlasPaletteTarget> {
    let Ok(contents) =
        std::fs::read_to_string(dir.join("world-lod-texture-palette-targets-v2.txt"))
    else {
        return Vec::new();
    };
    contents
        .lines()
        .skip_while(|line| *line == "mattmc-world-lod-texture-palette-targets-v2")
        .filter_map(|line| {
            let mut fields = line.split('|');
            let x = fields.next()?.parse::<i32>().ok()?;
            let y = fields.next()?.parse::<i32>().ok()?;
            let z = fields.next()?.parse::<i32>().ok()?;
            let expected_sprites = fields
                .next()?
                .split(',')
                .map(str::trim)
                .filter(|sprite| !sprite.is_empty())
                .map(ToOwned::to_owned)
                .collect::<BTreeSet<_>>();
            let required_sprites = fields
                .next()?
                .split(',')
                .map(str::trim)
                .filter(|sprite| !sprite.is_empty())
                .map(ToOwned::to_owned)
                .collect::<BTreeSet<_>>();
            if fields.next().is_some()
                || expected_sprites.is_empty()
                || !required_sprites.is_subset(&expected_sprites)
            {
                return None;
            }
            Some(WorldLodExactAtlasPaletteTarget {
                position: [x, y, z],
                expected_sprites,
                required_sprites,
            })
        })
        .take(16)
        .collect()
}

pub(in crate::render::worldrender) fn world_lod_textured_quad_world_bounds(
    origin: [i32; 3],
    quad: &lod::WorldLodTexturedQuad,
) -> [f32; 6] {
    let mut min = [f32::INFINITY; 3];
    let mut max = [f32::NEG_INFINITY; 3];
    for vertex in quad.vertices {
        for axis in 0..3 {
            let world =
                origin[axis] as f32 + vertex.local_position[axis] + vertex.micro_offset[axis];
            min[axis] = min[axis].min(world);
            max[axis] = max[axis].max(world);
        }
    }
    [min[0], min[1], min[2], max[0], max[1], max[2]]
}

pub(in crate::render::worldrender) fn world_lod_exact_atlas_target_intersects(bounds: [f32; 6], position: [i32; 3]) -> bool {
    // The target is a block cell, not a point. A reduced DH quad may span the
    // whole cell, so intersect its complete cell volume rather than depending
    // on an arbitrary exact vertex match or camera projection. Keeping Y in
    // the test is essential: terrain below the panel can legitimately carry
    // the same sprite without proving the panel was textured.
    let target_min_x = position[0] as f32;
    let target_max_x = target_min_x + 1.0;
    let target_min_y = position[1] as f32;
    let target_max_y = target_min_y + 1.0;
    let target_min_z = position[2] as f32;
    let target_max_z = target_min_z + 1.0;
    bounds[0] <= target_max_x
        && bounds[3] >= target_min_x
        && bounds[1] <= target_max_y
        && bounds[4] >= target_min_y
        && bounds[2] <= target_max_z
        && bounds[5] >= target_min_z
}

pub(in crate::render::worldrender) fn world_lod_exact_atlas_target_distance_squared(bounds: [f32; 6], position: [i32; 3]) -> f32 {
    let center = [
        position[0] as f32 + 0.5,
        position[1] as f32 + 0.5,
        position[2] as f32 + 0.5,
    ];
    (0..3)
        .map(|axis| {
            if center[axis] < bounds[axis] {
                bounds[axis] - center[axis]
            } else if center[axis] > bounds[axis + 3] {
                center[axis] - bounds[axis + 3]
            } else {
                0.0
            }
        })
        .map(|distance| distance * distance)
        .sum()
}

/// Capture-only projection receipt for one semantic DH target. DH's copied
/// model-view matrix consumes camera-relative column positions, as does the
/// source vertex preamble. Keep this evidence in that coordinate space rather
/// than multiplying an absolute-world palette target by a relative matrix.
pub(in crate::render::worldrender) fn world_lod_exact_atlas_target_projection(
    matrix: [f32; 16],
    camera_world_position: [f32; 3],
    position: [i32; 3],
) -> String {
    let point = world_lod_camera_relative_point(
        [
            position[0] as f32 + 0.5,
            position[1] as f32 + 1.0,
            position[2] as f32 + 0.5,
        ],
        camera_world_position,
    );
    let clip = [
        matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12],
        matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13],
        matrix[2] * point[0] + matrix[6] * point[1] + matrix[10] * point[2] + matrix[14],
        matrix[3] * point[0] + matrix[7] * point[1] + matrix[11] * point[2] + matrix[15],
    ];
    if !clip.into_iter().all(f32::is_finite) || clip[3].abs() < 1.0e-6 {
        return format!(
            "{{\"status\":\"invalid-clip\",\"clip\":[{:.6},{:.6},{:.6},{:.6}]}}",
            clip[0], clip[1], clip[2], clip[3]
        );
    }
    let ndc = [clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]];
    let inside = clip[3] > 0.0
        && ndc[0].abs() <= 1.0
        && ndc[1].abs() <= 1.0
        && (-1.0..=1.0).contains(&ndc[2]);
    format!(
        concat!(
            "{{\"status\":\"ok\",\"clip\":[{:.6},{:.6},{:.6},{:.6}],",
            "\"ndc\":[{:.6},{:.6},{:.6}],\"insideClip\":{}}}"
        ),
        clip[0], clip[1], clip[2], clip[3], ndc[0], ndc[1], ndc[2], inside,
    )
}

/// Capture-only consistency check for the copied DH camera contract. The
/// arrays are semantic column-major matrices, so this records algebraic
/// agreement without observing a backend matrix or Java renderer object.
pub(in crate::render::worldrender) fn world_lod_transform_consistency_diagnostic(frame: WorldLodRenderFrame) -> String {
    let combined_from_parts =
        matrix4_column_major_multiply(frame.projection_matrix, frame.model_view_matrix);
    let projection_inverse =
        matrix4_column_major_multiply(frame.projection_matrix, frame.projection_inverse_matrix);
    format!(
        concat!(
            "{{\"projectionModelViewMaxAbsError\":{:.8},",
            "\"projectionInverseMaxAbsError\":{:.8},",
            "\"combined\":[{}],\"projection\":[{}],",
            "\"projectionInverse\":[{}]}}"
        ),
        matrix4_max_abs_difference(frame.combined_matrix, combined_from_parts),
        matrix4_max_abs_difference(projection_inverse, matrix4_identity()),
        matrix4_json_values(frame.combined_matrix),
        matrix4_json_values(frame.projection_matrix),
        matrix4_json_values(frame.projection_inverse_matrix),
    )
}

pub(in crate::render::worldrender) fn matrix4_column_major_multiply(left: [f32; 16], right: [f32; 16]) -> [f32; 16] {
    let mut result = [0.0; 16];
    for column in 0..4 {
        for row in 0..4 {
            result[column * 4 + row] = (0..4)
                .map(|inner| left[inner * 4 + row] * right[column * 4 + inner])
                .sum();
        }
    }
    result
}

pub(in crate::render::worldrender) fn matrix4_column_major_transform_point(matrix: [f32; 16], point: [f32; 4]) -> [f32; 4] {
    [
        matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12] * point[3],
        matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13] * point[3],
        matrix[2] * point[0] + matrix[6] * point[1] + matrix[10] * point[2] + matrix[14] * point[3],
        matrix[3] * point[0] + matrix[7] * point[1] + matrix[11] * point[2] + matrix[15] * point[3],
    ]
}

pub(in crate::render::worldrender) fn vector4_max_abs_difference(left: [f32; 4], right: [f32; 4]) -> f32 {
    left.into_iter()
        .zip(right)
        .map(|(left, right)| (left - right).abs())
        .fold(0.0_f32, f32::max)
}

pub(in crate::render::worldrender) fn matrix4_ndc(clip: [f32; 4]) -> Option<[f32; 3]> {
    if clip.into_iter().all(f32::is_finite) && clip[3].abs() >= 1.0e-6 {
        Some([clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]])
    } else {
        None
    }
}

pub(in crate::render::worldrender) fn matrix4_ndc_json(ndc: Option<[f32; 3]>) -> String {
    match ndc {
        Some([x, y, z]) => format!("[{x:.6},{y:.6},{z:.6}]"),
        None => "null".to_string(),
    }
}

pub(in crate::render::worldrender) fn matrix4_max_abs_difference(left: [f32; 16], right: [f32; 16]) -> f32 {
    left.into_iter()
        .zip(right)
        .map(|(left, right)| (left - right).abs())
        .fold(0.0_f32, f32::max)
}

pub(in crate::render::worldrender) fn matrix4_identity() -> [f32; 16] {
    [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ]
}

pub(in crate::render::worldrender) fn matrix4_json_values(matrix: [f32; 16]) -> String {
    matrix
        .into_iter()
        .map(|value| format!("{value:.8}"))
        .collect::<Vec<_>>()
        .join(",")
}

pub(in crate::render::worldrender) fn matrix4_json_array(matrix: [f32; 16]) -> String {
    format!("[{}]", matrix4_json_values(matrix))
}

/// Capture-only screen footprint for one already-planned exact-atlas quad.
/// This uses the owned vertex positions and the same combined matrix as the
/// draw; it never inspects a backend viewport or native resource. Point crops
/// can include sky or a neighbouring LOD tile on a steep perspective face, so
/// the harness consumes this polygon when it classifies final-frame evidence.
pub(in crate::render::worldrender) fn world_lod_exact_atlas_quad_projection(
    matrix: [f32; 16],
    camera_world_position: [f32; 3],
    origin: [i32; 3],
    quad: &lod::WorldLodTexturedQuad,
) -> String {
    let mut vertices = Vec::with_capacity(4);
    let mut min = [f32::INFINITY; 2];
    let mut max = [f32::NEG_INFINITY; 2];
    for vertex in quad.vertices {
        let point = world_lod_camera_relative_point(
            [
                origin[0] as f32 + vertex.local_position[0] + vertex.micro_offset[0],
                origin[1] as f32 + vertex.local_position[1] + vertex.micro_offset[1],
                origin[2] as f32 + vertex.local_position[2] + vertex.micro_offset[2],
            ],
            camera_world_position,
        );
        let clip = [
            matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12],
            matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13],
            matrix[2] * point[0] + matrix[6] * point[1] + matrix[10] * point[2] + matrix[14],
            matrix[3] * point[0] + matrix[7] * point[1] + matrix[11] * point[2] + matrix[15],
        ];
        if !clip.into_iter().all(f32::is_finite) || clip[3] <= 1.0e-6 {
            return "{\"status\":\"invalid-or-behind-clip\"}".to_string();
        }
        let ndc = [clip[0] / clip[3], clip[1] / clip[3], clip[2] / clip[3]];
        if !ndc.into_iter().all(f32::is_finite) {
            return "{\"status\":\"invalid-ndc\"}".to_string();
        }
        min[0] = min[0].min(ndc[0]);
        min[1] = min[1].min(ndc[1]);
        max[0] = max[0].max(ndc[0]);
        max[1] = max[1].max(ndc[1]);
        vertices.push(format!("[{:.6},{:.6},{:.6}]", ndc[0], ndc[1], ndc[2]));
    }
    let inside = min[0] <= 1.0 && max[0] >= -1.0 && min[1] <= 1.0 && max[1] >= -1.0;
    format!(
        concat!(
            "{{\"status\":\"ok\",\"insideClip\":{},",
            "\"ndcBounds\":[{:.6},{:.6},{:.6},{:.6}],\"ndcVertices\":[{}]}}"
        ),
        inside,
        min[0],
        min[1],
        max[0],
        max[1],
        vertices.join(","),
    )
}

/// Matches `WorldLodDrawUniform::model_offset_and_reserved`: DH geometry is
/// local to a column and the copied DH model-view is already camera-relative.
/// This helper is diagnostic-only and intentionally has no backend knowledge.
pub(in crate::render::worldrender) fn world_lod_camera_relative_point(
    world_position: [f32; 3],
    camera_world_position: [f32; 3],
) -> [f32; 4] {
    [
        world_position[0] - camera_world_position[0],
        world_position[1] - camera_world_position[1],
        world_position[2] - camera_world_position[2],
        1.0,
    ]
}

/// Bounded source receipt for a selected exact-atlas rectangle. This identifies
/// the CPU-owned atlas content consumed by the Rust material path without
/// exposing an image, descriptor, or backend handle to diagnostics.
pub(in crate::render::worldrender) fn world_lod_atlas_rect_diagnostic(
    atlas: Option<&WorldMaterialTextureAsset>,
    rect: [f32; 4],
) -> String {
    let Some(atlas) = atlas else {
        return "null".to_string();
    };
    if atlas.width == 0
        || atlas.height == 0
        || atlas.rgba.len()
            != usize::try_from(atlas.width)
                .ok()
                .and_then(|width| usize::try_from(atlas.height).ok()?.checked_mul(width))
                .and_then(|pixels| pixels.checked_mul(4))
                .unwrap_or(0)
        || !rect.into_iter().all(f32::is_finite)
        || rect[0] < 0.0
        || rect[1] < 0.0
        || rect[2] <= rect[0]
        || rect[3] <= rect[1]
        || rect[2] > 1.0
        || rect[3] > 1.0
    {
        return "{\"status\":\"invalid-atlas-rect\"}".to_string();
    }
    let x0 = (rect[0] * atlas.width as f32).floor() as u32;
    let y0 = (rect[1] * atlas.height as f32).floor() as u32;
    let x1 = (rect[2] * atlas.width as f32).ceil() as u32;
    let y1 = (rect[3] * atlas.height as f32).ceil() as u32;
    if x0 >= x1 || y0 >= y1 || x1 > atlas.width || y1 > atlas.height {
        return "{\"status\":\"invalid-atlas-texel-bounds\"}".to_string();
    }
    let mut bytes =
        Vec::with_capacity(usize::try_from((x1 - x0) * (y1 - y0) * 4).unwrap_or_default());
    for y in y0..y1 {
        let row_start = usize::try_from((y * atlas.width + x0) * 4).unwrap_or(usize::MAX);
        let row_end = row_start.saturating_add(usize::try_from((x1 - x0) * 4).unwrap_or(0));
        let Some(row) = atlas.rgba.get(row_start..row_end) else {
            return "{\"status\":\"atlas-row-out-of-range\"}".to_string();
        };
        bytes.extend_from_slice(row);
    }
    let center_x = (x0 + x1 - 1) / 2;
    let center_y = (y0 + y1 - 1) / 2;
    let center = usize::try_from((center_y * atlas.width + center_x) * 4).unwrap_or(usize::MAX);
    let Some(center_rgba) = atlas.rgba.get(center..center.saturating_add(4)) else {
        return "{\"status\":\"atlas-center-out-of-range\"}".to_string();
    };
    format!(
        concat!(
            "{{\"status\":\"ok\",\"texelBounds\":[{},{},{},{}],",
            "\"rgbaHash\":\"{:016x}\",\"centerRgba\":[{},{},{},{}]}}"
        ),
        x0,
        y0,
        x1,
        y1,
        fnv64_bytes(&bytes),
        center_rgba[0],
        center_rgba[1],
        center_rgba[2],
        center_rgba[3],
    )
}

//! Recording of the built-in world frame.

use crate::render::worldrender::vanilla::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn append_frame_ops_inner(
        &mut self, gal: &mut VulkanicGal, generation: u64, frame_target: Handle,
        frame: WorldPrimitiveFrame, clear_background: bool, raster_y_direction: RasterYDirection,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        self.append_frame_ops_inner_with_source_preparation(
            gal, generation, frame_target, frame, clear_background, raster_y_direction, false,
        )
    }

    pub(crate) fn append_frame_ops_inner_with_source_preparation(
        &mut self, gal: &mut VulkanicGal, generation: u64, frame_target: Handle,
        frame: WorldPrimitiveFrame, clear_background: bool, raster_y_direction: RasterYDirection,
        preparing_source_entry: bool,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        let previous = self.defer_world_uploads;
        let result = self.record_frame_ops(gal, generation, frame_target, frame, clear_background, raster_y_direction, preparing_source_entry);
        self.defer_world_uploads = previous;
        result
    }

    fn record_frame_ops(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        mut frame: WorldPrimitiveFrame,
        clear_background: bool,
        raster_y_direction: RasterYDirection,
        preparing_source_entry: bool,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        // The vanilla graph draws terrain from instances.
        self.expand_static_terrain(&mut frame)?;
        self.pending_terrain_external_item_entity_written = false;
        // Staged device-local uploads are batched into one flush per frame.
        let batch_staged_uploads = gal.capabilities().supports(BackendFeature::DeviceLocalMemory);
        if self.generation == 0 {
            self.generation = generation;
        }
        // Vanilla first-person items use the same copied indexed mesh assets as
        // world entities, but must be rendered with their own projection and a
        // fresh depth domain. Keep that stream separate from camera-space
        // batching and schedule a second explicit mesh pass below when a
        // selected shader-pack hand writer is not armed.
        let builtin_first_person = if !self.runtime_source_execution_is_armed()
            && !frame.first_person_mesh_instances.is_empty()
        {
            Some((
                frame.first_person.clone(),
                std::mem::take(&mut frame.first_person_mesh_instances),
            ))
        } else {
            None
        };
        // The Java semantic callsite arms the hand domain before it knows
        // whether either visible hand will contribute geometry (for example
        // an invisible player). An enabled frame with no copied meshes is an
        // explicit no-op, not an unowned Java draw or a reason to reject the
        // otherwise valid world frame.
        if frame.first_person_mesh_instances.is_empty() {
            frame.first_person = WorldFirstPersonFrame::default();
        }
        if builtin_first_person.is_some() {
            frame.first_person = WorldFirstPersonFrame::default();
        }
        let validate_started = std::time::Instant::now();
        validate_frame(&frame)?;
        let entity_outline_plan = features::outline::prepare_entity_outline_post_effect(&frame)?;
        if raster_y_direction != RasterYDirection::Up
            && (self.runtime_source_execution_is_armed() || !frame.lod_instances.is_empty())
        {
            return Err(GalError::unsupported_feature(
                "noncanonical world raster requires the direct vanilla graph",
            ));
        }
        // First-person records may enter only a fully armed selected-source
        // frame, whose dedicated hand writer owns their projection and fresh
        // depth domain. The ordinary world graph must still reject them rather
        // than batch them as camera-space entities or silently drop them.
        if (frame.first_person.enabled || !frame.first_person_mesh_instances.is_empty())
            && !self.runtime_source_execution_is_armed()
        {
            return Err(GalError::unsupported_feature(
                "world first-person mesh stream requires an armed Rust-owned hand source pass",
            ));
        }
        for instance in &frame.lod_instances {
            let gpu = self
                .lod_gpu_column_assets
                .get(&instance.column_key)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "world LOD instance {} has no Rust-owned GPU payload",
                        instance.column_key
                    ))
                })?;
            if gpu.column_key != instance.column_key
                || gpu.column_generation != instance.column_generation
            {
                return Err(GalError::invalid_argument(
                    "world LOD GPU payload generation does not match visible instance",
                ));
            }
            let segment = gpu
                .segments
                .get(instance.segment_index as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "world LOD GPU payload is missing segment {}",
                        instance.segment_index
                    ))
                })?;
            if segment.layer != instance.layer {
                return Err(GalError::invalid_argument(
                    "world LOD GPU payload layer differs from visible instance layer",
                ));
            }
        }
        let mut profile = WholeFrameProfile {
            world_validate_frame_nanos: elapsed_nanos_u64(validate_started),
            ..WholeFrameProfile::default()
        };
        profile.g_buffer_resources_retired =
            std::mem::take(&mut self.pending_g_buffer_resources_retired);
        let target_query_started = std::time::Instant::now();
        let color_format = gal.pass_target_color_format(frame_target)?;
        let color_attachment = gal.pass_target_color_attachment(frame_target)?;
        let owned_color_texture = if frame_target.kind()
            == Some(crate::render::vulkanic::handles::HandleKind::RenderTarget)
        {
            Some(gal.pass_target_color_texture(frame_target)?)
        } else {
            None
        };
        profile.world_prepare_target_query_nanos = elapsed_nanos_u64(target_query_started);
        let had_resources = self
            .resources
            .contains_key(&(color_format, raster_y_direction));
        let had_crack_resources = self
            .crack_resources
            .contains_key(&(color_format, raster_y_direction));
        let had_border_resources = self
            .border_resources
            .contains_key(&(color_format, raster_y_direction));
        let source_preparation_warmup = {
            #[cfg(not(test))]
            {
                self.runtime_source_preparation_requested()
            }
            #[cfg(test)]
            {
                false
            }
        };
        let use_g_buffer_mesh_path = uses_shader_g_buffer_mesh_path(
            &frame,
            clear_background,
            // Source preparation uses the normal semantic graph to establish
            // depth history; selected execution still requires admission.
            self.runtime_source_execution_is_armed() || source_preparation_warmup,
            self.pending_terrain_fabulous_handoff,
        ) || (preparing_source_entry && clear_background && frame.background.enabled
            && frame.voxel_volume.world_generation != 0);
        // Entry may contain only sky and a hand while terrain/LOD data streams
        // in. Its private graph must still initialize the depth snapshots.
        // The direct Rust DH compositor overlays its private sparse target
        // after the background/sky setup and before ordinary opaque terrain.
        // Keep the vanilla sky fan in that target so uncovered pixels retain
        // the copied sky gradient; only a shader-pack/G-buffer route has a
        // later source-owned sky stage.
        let draw_vanilla_sky_disc =
            vanilla_sky_disc_required_for_frame(&frame, clear_background, use_g_buffer_mesh_path);
        // Builtin deferred passes sample image coordinates and preserve the
        // world attachment row order. World vertex fog is already stored in
        // the G-buffer; no screen-space ray is reconstructed by those passes.
        // Source runtimes remain excluded by the explicit guard above.
        if draw_vanilla_sky_disc {
            if use_g_buffer_mesh_path {
                self.ensure_sky_disc_resources(gal, ColorFormat::Rgba8Unorm, raster_y_direction)?;
            } else {
                self.ensure_sky_disc_forward_resources(gal, color_format, raster_y_direction)?;
            }
        }
        let batching_started = std::time::Instant::now();
        // Material batches join the owned graph's forward color phase when
        // indexed terrain is present. That phase targets the same HDR
        // intermediate domain as the G-buffer, so select the compatible
        // pipeline key before resource construction rather than asking an
        // acquired-target pipeline to render into the graph attachment.
        let material_color_format = if use_g_buffer_mesh_path {
            SHADER_G_BUFFER_COLOR_FORMAT
        } else {
            color_format
        };
        let material_batch_plan = if material_batch_plan_cache_disabled() {
            // This is a diagnostic-only A/B escape hatch. It intentionally
            // rebuilds the same semantic plan without changing the material
            // ABI, resource ownership, ordering, or draw contracts.
            Arc::new(material_batches(
                &frame,
                material_color_format,
                raster_y_direction,
            ))
        } else {
            // Reuse the large identity staging vector across frames. The
            // cache still compares the complete ordered sequence, while a
            // miss clones it into the bounded entry that owns the plan.
            let mut material_identity_scratch =
                std::mem::take(&mut self.material_batch_identity_scratch);
            material_identity_scratch.clear();
            material_identity_scratch.reserve(frame.material_quads.len());
            material_identity_scratch.extend(
                frame
                    .material_quads
                    .iter()
                    .map(|quad| material_key(quad, material_color_format, raster_y_direction)),
            );
            if let Some(entry) = self.material_batch_plan_cache.iter().find(|entry| {
                entry.key.color_format == material_color_format
                    && entry.key.raster_y_direction == raster_y_direction
                    && entry.key.identities == material_identity_scratch
            }) {
                let batches = Arc::clone(&entry.batches);
                self.material_batch_identity_scratch = material_identity_scratch;
                batches
            } else {
                let material_batch_key = MaterialBatchPlanKey {
                    color_format: material_color_format,
                    raster_y_direction,
                    identities: material_identity_scratch.clone(),
                };
                let batches = Arc::new(material_batches(
                    &frame,
                    material_color_format,
                    raster_y_direction,
                ));
                const MAX_MATERIAL_BATCH_PLAN_CACHE: usize = 4;
                if self.material_batch_plan_cache.len() >= MAX_MATERIAL_BATCH_PLAN_CACHE {
                    self.material_batch_plan_cache.remove(0);
                }
                self.material_batch_plan_cache
                    .push(MaterialBatchPlanCacheEntry {
                        key: material_batch_key,
                        batches: Arc::clone(&batches),
                    });
                self.material_batch_identity_scratch = material_identity_scratch;
                batches
            }
        };
        let material_batches: &[MaterialBatch] = material_batch_plan.as_slice();
        // On the G-buffer path DH generic objects belong to the selected-source
        // DH pass (`dh_terrain`, as Iris draws them); the private direct
        // target is not part of that frame. A not-yet-armed warmup frame has
        // no source DH pass and omits them for that single arming frame.
        let (distant_horizons_generic_batches, distant_horizons_box_batches) =
            if use_g_buffer_mesh_path {
                (Vec::new(), Vec::new())
            } else {
                (
                    distant_horizons_generic_material_batches(
                        &frame,
                        material_color_format,
                        raster_y_direction,
                    ),
                    distant_horizons_generic_box_batches(
                        &frame,
                        material_color_format,
                        raster_y_direction,
                    ),
                )
            };
        let mesh_group_started = std::time::Instant::now();
        let mut mesh_identity_scratch = std::mem::take(&mut self.mesh_batch_identity_scratch);
        mesh_identity_scratch.clear();
        mesh_identity_scratch.reserve(frame.mesh_instances.len());
        mesh_identity_scratch.extend(frame.mesh_instances.iter().map(mesh_batch_instance_key));
        // Camera-sorted translucent sections derive their order from the
        // frame transform. Keep only those batches frame-local; their presence
        // must not force every stable opaque/cutout/entity instance through
        // section expansion and hash grouping again. The cache key still owns
        // the complete ordered instance identity, including the dynamic
        // records, so cached static indices remain exact frame indices.
        let has_camera_sorted_meshes = frame
            .mesh_instances
            .iter()
            .any(|instance| instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0);
        let static_mesh_batch_plan = self.cached_mesh_batch_plan(
            &frame,
            color_format,
            raster_y_direction,
            use_g_buffer_mesh_path,
            if has_camera_sorted_meshes {
                MeshBatchSelection::Static
            } else {
                MeshBatchSelection::All
            },
            &mesh_identity_scratch,
            false,
        )?;
        self.mesh_batch_identity_scratch = mesh_identity_scratch;
        let mut sorted_index_payload = Vec::new();
        let mesh_batch_plan = if has_camera_sorted_meshes {
            let mut combined = Vec::with_capacity(
                static_mesh_batch_plan.len()
                    + frame
                        .mesh_instances
                        .iter()
                        .filter(|instance| {
                            instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
                        })
                        .count(),
            );
            combined.extend(static_mesh_batch_plan.iter().cloned());
            combined.extend(mesh_batches_selected_with_sorted_indices(
                &frame,
                self,
                color_format,
                raster_y_direction,
                use_g_buffer_mesh_path,
                false,
                MeshBatchSelection::CameraSorted,
                (!use_g_buffer_mesh_path).then_some(&mut sorted_index_payload),
            )?);
            sort_mesh_batches(&mut combined, &frame);
            Arc::new(combined)
        } else {
            static_mesh_batch_plan
        };
        // The cached plan contains only semantic batch ranges and stable
        // frame indices. Borrow it for the normal path; `to_mut` below makes
        // a private copy only for a selector that actually changes topology
        // or destination policy for this frame.
        let mut mesh_batches: Cow<'_, [MeshBatch]> = Cow::Borrowed(mesh_batch_plan.as_slice());
        trace_static_terrain_mesh_batch(&frame, self, &mesh_batches, &sorted_index_payload);
        if self.pending_terrain_fabulous_handoff {
            // These entity draws target the canonical Fabulous item/entity
            // attachment, not the oriented deferred world images. Match the
            // destination convention explicitly before pipeline lookup.
            for batch in mesh_batches.to_mut() {
                if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH
                    && material_mode_uses_alpha_blending(batch.key.material_mode)
                {
                    batch.key.raster_y_direction = RasterYDirection::Up;
                }
            }
        }
        // A private-target capture must not be repainted by the ordinary
        // near-terrain pass that follows DH composition. This is a bounded
        // audit selector; normal frames never read it and keep the existing
        // whole-frame ordering.
        if frame.lod_render_frame.rust_route_selected()
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_ISOLATE_VANILLA").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            mesh_batches.to_mut().clear();
        }
        if frame.lod_render_frame.rust_route_selected()
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_DISABLE_VANILLA_TRANSLUCENT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            mesh_batches
                .to_mut()
                .retain(|batch| !material_mode_uses_alpha_blending(batch.key.material_mode));
        }
        if frame.lod_render_frame.rust_route_selected()
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_DISABLE_VANILLA_OPAQUE").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
        {
            mesh_batches
                .to_mut()
                .retain(|batch| material_mode_uses_alpha_blending(batch.key.material_mode));
        }
        profile.world_mesh_section_expand_group_nanos = elapsed_nanos_u64(mesh_group_started);
        profile.world_batching_nanos = elapsed_nanos_u64(batching_started);
        let material_cache_hits = material_batches
            .iter()
            .filter(|batch| self.material_resources.contains_key(&batch.key))
            .count() as u64;
        if use_g_buffer_mesh_path {
            self.ensure_shader_runtime(gal, self.generation)?;
        }
        // A candidate subset program is not a vanilla replacement: until the
        // selected source route is fully armed it can carry only its optional
        // source-owned binding while the generated direct-terrain shader also
        // declares the builtin lightmap at set 1.  Admitting that hybrid
        // pipeline produces an incomplete Vulkan descriptor contract.  Keep
        // it unavailable and retain the complete Rust-owned vanilla lightmap
        // route unless selected-source execution has passed its own gate.
        let source_terrain_programs = if use_g_buffer_mesh_path
            // Focused tests retain access to the private candidate selector
            // so they can prove its retirement/rejection behaviour.  It is
            // never admitted by production merely because a candidate was
            // discovered.
            && (self.candidate_lowered_source_execution_requested() || cfg!(test))
        {
            self.candidate_subset_programs_for_frame(frame.frame_id)?
        } else {
            None
        };
        // Normal Rust mesh materials always consume the copied dynamic
        // vanilla lightmap.  Stage its upload into this same explicit frame
        // submission and bind a descriptor retained by that exact residency;
        // there is no Java texture, native handle, or hidden renderer path.
        let builtin_terrain_lightmap_required = (!mesh_batches.is_empty()
            && source_terrain_programs.is_none())
            || !distant_horizons_generic_batches.is_empty()
            || !distant_horizons_box_batches.is_empty()
            || material_batches
                .iter()
                .any(|batch| material_uses_particle_shader(batch.key.source_program));
        if builtin_terrain_lightmap_required {
            let Some(lightmap_frame) = frame.shader_environment.vanilla_lightmap else {
                return Err(GalError::unsupported_feature(
                    "Rust indexed meshes, weather and particles require copied vanilla lightmap semantics",
                ));
            };
            if frame.shader_environment.world_generation == 0 {
                return Err(GalError::unsupported_feature(
                    "Rust indexed meshes, weather and particles require copied vanilla lightmap semantics",
                ));
            }
            self.ensure_shader_runtime(gal, self.generation)?;
            self.ensure_builtin_terrain_lightmap_layout(gal)?;
            let runtime = self
                .shader_runtime
                .as_mut()
                .expect("shader runtime is installed before builtin lightmap staging");
            runtime.observe_vanilla_lightmap(
                frame.shader_environment.world_generation,
                Some(lightmap_frame),
            )?;
            trace_builtin_terrain_lightmap_receipt(
                frame.frame_id,
                frame.shader_environment.world_generation,
                lightmap_frame,
                runtime.vanilla_lightmap_cache().rgba8(),
            );
        }
        let lowered_source_execution_requested = use_g_buffer_mesh_path
            && self.runtime_source_execution_is_armed()
            && self.candidate_lowered_source_execution_requested();
        if frame.lod_render_frame.rust_route_selected() && lowered_source_execution_requested {
            return Err(GalError::unsupported_feature(
                "Rust Distant Horizons non-water route requires its own admitted shader-pack pass contract; it cannot reuse near-terrain source execution",
            ));
        }
        let mut mesh_cache_hits = 0u64;
        profile.world_prepare_mesh_cache_scan_nanos = 0;
        profile.world_prepare_mesh_batch_count = mesh_batches.len() as u64;
        let resource_creates_before = gal.metrics().resource_creates;
        let resource_destroys_before = gal.metrics().resource_destroys;
        let resource_started = std::time::Instant::now();
        let previous_defer_world_uploads = self.defer_world_uploads;
        self.defer_world_uploads = previous_defer_world_uploads || batch_staged_uploads;
        let render_resources_started = std::time::Instant::now();
        if !frame.segments.is_empty() {
            self.ensure_resources(gal, color_format, raster_y_direction)?;
        }
        if !frame.crack_quads.is_empty() {
            self.ensure_crack_resources(gal, color_format, raster_y_direction)?;
        }
        if !frame.border_quads.is_empty() {
            self.ensure_border_resources(gal, color_format, raster_y_direction)?;
        }
        let mesh_material_asset_started = std::time::Instant::now();
        let builtin_mesh_resource_layout = self.builtin_terrain_lightmap_layout;
        let material_resource_started = std::time::Instant::now();
        let resource_upload_result = (|| -> GalResult<()> {
            for batch in material_batches {
                self.ensure_material_resources(gal, batch.key)?;
            }
            for batch in &distant_horizons_generic_batches {
                self.ensure_material_resources(gal, batch.key)?;
            }
            for batch in &distant_horizons_box_batches {
                self.ensure_material_resources(gal, batch.key)?;
            }
            Ok(())
        })();
        if resource_upload_result.is_err() {
            self.defer_world_uploads = previous_defer_world_uploads;
            // Keep queued uploads for resources already cached; retry must publish them.
        }
        resource_upload_result?;
        profile.world_prepare_material_resource_nanos =
            elapsed_nanos_u64(material_resource_started);
        if !mesh_batches.is_empty() && !lowered_source_execution_requested {
            let stream_capacity_started = std::time::Instant::now();
            let required_stream_bytes = required_mesh_instance_stream_bytes(&mesh_batches)?;
            profile.world_prepare_mesh_stream_capacity_nanos =
                elapsed_nanos_u64(stream_capacity_started);
            profile.world_prepare_mesh_stream_required_bytes = required_stream_bytes;
            let stream_lookup_started = std::time::Instant::now();
            let stream_binding = self.ensure_mesh_instance_stream(gal, required_stream_bytes)?;
            profile.world_prepare_mesh_stream_lookup_nanos =
                elapsed_nanos_u64(stream_lookup_started);
            profile.world_prepare_mesh_stream_capacity_bytes = stream_binding.capacity;
            if stream_binding.grew {
                // Resource sets bind the backing stream buffer, not an
                // abstract stream name. Rebuild those bindings before this
                // frame records dynamic offsets into the replacement buffer.
                self.invalidate_mesh_instance_stream_bindings(gal);
                profile.world_prepare_mesh_stream_grows = 1;
                profile.world_prepare_mesh_stream_grow_nanos =
                    profile.world_prepare_mesh_stream_lookup_nanos;
            }
        }
        let mesh_resource_started = std::time::Instant::now();
        let mesh_upload_result = (|| -> GalResult<()> {
            for batch in mesh_batches.iter() {
                if lowered_source_execution_requested {
                    if self.mesh_assets.contains_key(&batch.key.mesh_key) {
                        mesh_cache_hits = mesh_cache_hits.saturating_add(1);
                    }
                    continue;
                }
                let had_resources = self.mesh_resources.contains_key(&batch.key);
                // A resident non-foil resource already carries the exact
                // content generation and pipeline variant in its key. Skip
                // the second residency probe inside ensure_mesh_resources;
                // foil still validates its texture contract every call.
                if !had_resources || batch.key.standard_item_foil {
                    if let Some(lightmap_layout) = builtin_mesh_resource_layout {
                        self.ensure_mesh_resources_with_shader_resource_layout(
                            gal,
                            batch.key,
                            Some(lightmap_layout),
                        )?;
                    } else {
                        self.ensure_mesh_resources(gal, batch.key)?;
                    }
                }
                if let Some(programs) = source_terrain_programs.as_ref() {
                    self.ensure_source_mesh_resources(
                        gal,
                        batch.key,
                        programs.for_material_mode(batch.key.material_mode)?,
                    )?;
                }
                if had_resources {
                    mesh_cache_hits = mesh_cache_hits.saturating_add(1);
                }
            }
            Ok(())
        })();
        self.defer_world_uploads = previous_defer_world_uploads;
        if mesh_upload_result.is_err() {
            // Keep queued uploads for resources already cached; retry must publish them.
        }
        mesh_upload_result?;
        if !previous_defer_world_uploads && batch_staged_uploads {
            self.flush_pending_world_uploads(gal)?;
        }
        profile.world_prepare_mesh_resource_nanos = elapsed_nanos_u64(mesh_resource_started);
        let mut material_slot_counts = BTreeMap::new();
        let material_slot_started = std::time::Instant::now();
        for batch in material_batches {
            let count = material_slot_counts.entry(batch.key).or_insert(0usize);
            *count += 1;
            self.ensure_material_resource_slots(gal, batch.key, *count)?;
        }
        for batch in &distant_horizons_generic_batches {
            let count = material_slot_counts.entry(batch.key).or_insert(0usize);
            *count += 1;
            self.ensure_material_resource_slots(gal, batch.key, *count)?;
        }
        for batch in &distant_horizons_box_batches {
            let count = material_slot_counts.entry(batch.key).or_insert(0usize);
            *count += 1;
            self.ensure_material_resource_slots(gal, batch.key, *count)?;
        }
        profile.world_prepare_material_slot_check_nanos = elapsed_nanos_u64(material_slot_started);
        profile.world_prepare_mesh_slot_check_nanos = 0;
        profile.world_prepare_mesh_material_asset_nanos =
            elapsed_nanos_u64(mesh_material_asset_started);
        profile.world_prepare_render_resources_nanos = elapsed_nanos_u64(render_resources_started);
        let depth_started = std::time::Instant::now();
        let (depth_texture, depth_view, created_depth, retired_depth) = self
            .ensure_depth_attachment(
                gal,
                frame_target,
                frame.viewport_width,
                frame.viewport_height,
            )?;
        profile.world_prepare_depth_attachment_nanos = elapsed_nanos_u64(depth_started);
        let mut g_buffer_final_binding_key = None;
        // Selected-source admission needs Rust-owned G-buffer views for
        // shadow and main-depth semantic roles before the route can arm.
        // Prepare those resources under the explicit opt-in as well, without
        // submitting a source graph or changing the presenter.
        let prepare_source_g_buffer = source_preparation_warmup
            && frame.background.enabled
            && frame.voxel_volume.world_generation != 0;
        if use_g_buffer_mesh_path || prepare_source_g_buffer {
            let g_buffer_started = std::time::Instant::now();
            let final_target_query_started = std::time::Instant::now();
            let final_depth_view =
                if frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
                    Some(gal.frame_target_owned_depth_attachment(frame_target)?.1)
                } else {
                    gal.pass_target_depth_attachment(frame_target)?
                        .map(|(_, view)| view)
                };
            profile.world_prepare_frame_target_attachment_query_nanos =
                elapsed_nanos_u64(final_target_query_started);
            let final_depth_format = final_depth_view.map(|_| TextureFormat::Depth32Float);
            self.ensure_g_buffer_resources(
                gal,
                frame.viewport_width,
                frame.viewport_height,
                color_format,
                final_depth_format,
                terrain_program_scope_for_sky_type(frame.background.sky_type)?,
                &mut profile,
            )?;
            g_buffer_final_binding_key = Some(self.ensure_g_buffer_final_binding(
                gal,
                frame_target,
                color_format,
                final_depth_view,
                &mut profile,
            )?);
            // Source-resource preparation runs again only after the owned
            // shadow target exists. This updates private diagnostics; it does
            // not alter source-program selection or the active fixture path.
            if self.should_refresh_candidate_source_assets_for_frame(&frame) {
                let allow_pending_colored_light = self.shader_runtime.as_ref().is_some_and(
                    ShaderPackRuntimeExecutor::has_pending_private_terrain_occupancy_submission,
                );
                let _ = self.ensure_candidate_source_assets_for_frame(
                    gal,
                    frame.voxel_volume.world_generation,
                    frame.frame_id,
                    allow_pending_colored_light,
                    source_frame_includes_distant_horizons(&frame),
                )?;
                self.prepare_candidate_source_color_resources_for_admission(
                    gal,
                    frame.voxel_volume.world_generation,
                    Extent3d {
                        width: frame.viewport_width,
                        height: frame.viewport_height,
                        depth: 1,
                    },
                    source_frame_includes_distant_horizons(&frame),
                )?;
                // The color role set is added after the generic source
                // snapshot exists, so refresh the bounded audit record with
                // the exact post-staging blocker list.
                self.write_runtime_source_admission_status(
                    gal,
                    &frame,
                    "post-resource-preparation",
                    true,
                );
            }
            if let Some(final_binding_key) = g_buffer_final_binding_key {
                let _ = self.record_candidate_source_frame_target(final_binding_key);
            }
            profile.world_prepare_g_buffer_resources_nanos = elapsed_nanos_u64(g_buffer_started);
        }
        if entity_outline_plan.is_some() {
            let mask_depth_view = if use_g_buffer_mesh_path {
                self.g_buffer_resources
                    .as_ref()
                    .ok_or_else(|| GalError::backend("outline mask requires G-buffer depth"))?
                    .depth_view
            } else {
                depth_view
            };
            // Outline intermediates and the final blit share the acquired
            // frame format, so the final pass never binds a pipeline whose
            // color format differs from the sole presenter target.
            let outline_color_format = color_format;
            self.ensure_entity_outline_target_resources_with_depth(
                gal,
                frame.viewport_width,
                frame.viewport_height,
                outline_color_format,
                Some(mask_depth_view),
            )?;
            self.prepare_entity_outline_mask_gpu_resources(
                gal,
                &frame,
                outline_color_format,
                raster_y_direction,
            )?;
            self.ensure_entity_outline_post_effect_resource_sets(gal, outline_color_format)?;
        }
        let lowered_source_terrain_programs = if lowered_source_execution_requested {
            self.candidate_lowered_source_programs_for_frame(
                frame.voxel_volume.world_generation,
                frame.frame_id,
                frame_target,
            )?
        } else {
            None
        };
        profile.world_resource_prepare_nanos = elapsed_nanos_u64(resource_started);
        let pending_depth_retires = std::mem::take(&mut self.pending_depth_attachment_retires);
        let frame_pass_started = std::time::Instant::now();
        let pass = self.frame_pass(gal, frame_target, depth_view)?;
        profile.world_prepare_frame_pass_nanos = elapsed_nanos_u64(frame_pass_started);
        let metrics_started = std::time::Instant::now();
        let resource_metrics_after = gal.metrics();
        profile.gal.resource_creates_delta = profile.gal.resource_creates_delta.saturating_add(
            resource_metrics_after
                .resource_creates
                .saturating_sub(resource_creates_before),
        );
        profile.gal.resource_destroys_delta = profile.gal.resource_destroys_delta.saturating_add(
            resource_metrics_after
                .resource_destroys
                .saturating_sub(resource_destroys_before),
        );
        profile.world_prepare_metrics_accounting_nanos = elapsed_nanos_u64(metrics_started);
        let mut stats = WorldPrimitiveSubmitStats {
            segment_count: frame.segments.len() as u64,
            vertex_count: (frame.segments.len() * 6) as u64,
            primitive_batch_count: line_batches(&frame).len() as u64,
            crack_quad_count: frame.crack_quads.len() as u64,
            crack_batch_count: crack_batches(&frame).len() as u64,
            border_quad_count: frame.border_quads.len() as u64,
            border_batch_count: border_batches(&frame).len() as u64,
            material_quad_count: frame.material_quads.len() as u64
                + frame.dh_generic_boxes.len() as u64 * 6,
            material_batch_count: material_batches.len() as u64,
            mesh_instance_count: frame.mesh_instances.len() as u64,
            mesh_batch_count: mesh_batches.len() as u64,
            depth_attachment_creates: u64::from(created_depth),
            depth_attachment_reuses: u64::from(!created_depth && !depth_view.is_null()),
            depth_attachment_retires: retired_depth.saturating_add(pending_depth_retires),
            border_asset_generation: self.border_asset_generation,
            border_asset_payload_bytes: self.border_asset_payload_bytes,
            border_asset_update_failures: self.border_asset_update_failures,
            crack_asset_generation: self.crack_asset_generation,
            crack_asset_payload_bytes: self.crack_asset_payload_bytes,
            crack_asset_update_failures: self.crack_asset_update_failures,
            material_asset_generation: self.material_asset_generation,
            material_asset_payload_bytes: self.material_asset_payload_bytes,
            material_asset_update_failures: self.material_asset_update_failures,
            mesh_asset_generation: self.mesh_asset_generation,
            mesh_asset_payload_bytes: self.mesh_asset_payload_bytes,
            mesh_asset_update_failures: self.mesh_asset_update_failures,
            background_clear_count: u64::from(frame.background.enabled),
            background_diagnostic_fallback_count: u64::from(!frame.background.enabled),
            background_sky_type: frame.background.sky_type as u64,
            background_color_argb: frame.background.color_argb as u64,
            profile,
            ..WorldPrimitiveSubmitStats::default()
        };
        if !frame.segments.is_empty() && had_resources {
            stats.cache_hits = 1;
            stats.outline_cache_hits = 1;
        } else if !frame.segments.is_empty() {
            stats.cache_misses = 1;
            stats.outline_cache_misses = 1;
            stats.resource_creates = 8;
        }
        if !frame.crack_quads.is_empty() && had_crack_resources {
            stats.cache_hits += 1;
            stats.crack_cache_hits = 1;
        } else if !frame.crack_quads.is_empty() {
            stats.cache_misses += 1;
            stats.crack_cache_misses = 1;
            stats.resource_creates += 12;
        }
        if !frame.border_quads.is_empty() && had_border_resources {
            stats.cache_hits += 1;
            stats.border_cache_hits = 1;
        } else if !frame.border_quads.is_empty() {
            stats.cache_misses += 1;
            stats.border_cache_misses = 1;
            stats.resource_creates += 12;
        }
        if !material_batches.is_empty() {
            stats.material_cache_hits = material_cache_hits;
            stats.material_cache_misses = material_batches.len() as u64 - material_cache_hits;
            stats.cache_hits = stats.cache_hits.saturating_add(stats.material_cache_hits);
            stats.cache_misses = stats
                .cache_misses
                .saturating_add(stats.material_cache_misses);
            stats.resource_creates = stats
                .resource_creates
                .saturating_add(stats.material_cache_misses.saturating_mul(13));
        }
        if !mesh_batches.is_empty() {
            stats.mesh_cache_hits = mesh_cache_hits;
            stats.mesh_cache_misses = mesh_batches.len() as u64 - mesh_cache_hits;
            stats.cache_hits = stats.cache_hits.saturating_add(stats.mesh_cache_hits);
            stats.cache_misses = stats.cache_misses.saturating_add(stats.mesh_cache_misses);
            stats.resource_creates = stats
                .resource_creates
                .saturating_add(stats.mesh_cache_misses.saturating_mul(12));
        }
        let command_generation_started = std::time::Instant::now();
        // Frozen clears the main target to the extracted fog colour, then
        // draws the camera-relative sky fan with its independent sky colour.
        // The Rust-owned G-buffer follows the same explicit ordering.
        // Frozen clears the world target with the active Fog uniform before
        // drawing the independent sky fan.  `background.color_argb` is the
        // fan's extracted sky colour, not that clear value; using it here
        // leaves uncovered sky pixels and later composition with the wrong
        // semantic colour.
        let background_color = vanilla_world_clear_color(&frame);
        let batches = line_batches(&frame);
        let crack_batches = crack_batches(&frame);
        let border_batches = border_batches(&frame);
        let mut ops = Vec::with_capacity(
            6 + batches.len() * 8
                + crack_batches.len() * 8
                + border_batches.len() * 8
                + material_batches.len() * 9
                + mesh_batches.len() * 8,
        );
        let sorted_index_stream = if sorted_index_payload.is_empty() {
            None
        } else {
            let stream =
                self.ensure_mesh_sorted_index_stream(gal, sorted_index_payload.len() as u64)?;
            ops.push(CommandOp::Barrier(buffer_barrier(
                stream.buffer,
                if stream.initialized {
                    TextureUsageState::IndexRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::HostWriteBuffer {
                buffer: stream.buffer,
                offset: 0,
                data: sorted_index_payload,
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                stream.buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::IndexRead,
            )));
            if let Some(slot) = self.mesh_sorted_index_stream.as_mut() {
                slot.initialized = true;
            }
            Some(stream)
        };
        // Frozen invokes DH's opaque render immediately before vanilla opaque
        // terrain. Resolve the private direct-DH target at that same boundary
        // so vanilla opaque depth/color can overwrite the near-field fade and
        // dither pixels. Deferred/source graphs keep their explicit
        // graph-owned ordering; this stage applies only to the direct route.
        let lod_only = frame.lod_render_frame.flags
            & (WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS | WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS)
            == (WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS | WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS);
        let vanilla_fade_mode = if lod_only {
            3.0
        } else if frame.lod_render_frame.flags & WORLD_LOD_FLAG_VANILLA_FADE_DOUBLE_PASS != 0 {
            2.0
        } else if frame.lod_render_frame.flags & WORLD_LOD_FLAG_VANILLA_FADE_SINGLE_PASS != 0 {
            1.0
        } else {
            0.0
        };
        let far_clip_fade = frame.lod_render_frame.flags & WORLD_LOD_FLAG_DH_FAR_CLIP_FADE != 0;
        // Frozen clears and applies DH's private target whenever its renderer
        // is active, then invokes the configured vanilla-fade boundary even
        // if the current visible set contributed no drawable LOD buffers.
        // Keeping this transaction alive is essential for LOD-only mode: an
        // empty or transitioning DH frame must resolve to the private clear,
        // rather than exposing an otherwise complete vanilla terrain frame.
        let direct_dh_fog_composition = !use_g_buffer_mesh_path
            && frame.lod_render_frame.rust_route_selected()
            && (!distant_horizons_generic_batches.is_empty()
                || !distant_horizons_box_batches.is_empty()
                || frame.lod_render_frame.dh_fog_parameters[16] >= 0.5
                || frame.lod_render_frame.ssao_parameters[0] >= 0.5
                || vanilla_fade_mode > 0.0
                || far_clip_fade);
        let double_pass_vanilla_fade = vanilla_fade_mode >= 1.5;
        let defer_dh_composite_until_after_opaque = frame.lod_render_frame.rust_route_selected()
            && (vanilla_fade_mode > 0.0
                || (matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COMPOSITE_AFTER_OPAQUE").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                )));
        let skip_dh_composite_for_audit = frame.lod_render_frame.rust_route_selected()
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_SKIP_COMPOSITE").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let skip_final_double_pass_for_audit = double_pass_vanilla_fade
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_SKIP_FINAL_DOUBLE_PASS").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let skip_opaque_double_pass_for_audit = double_pass_vanilla_fade
            && matches!(
                crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_SKIP_OPAQUE_DOUBLE_PASS").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            )
            && matches!(
                crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                Ok("1") | Ok("true") | Ok("TRUE")
            );
        let mut deferred_dh_composite_ops = Vec::new();
        // The actual Frozen hook runs renderFadeTransparent immediately before
        // the TRANSLUCENT chunk group (after opaque terrain). DOUBLE_PASS adds
        // renderFadeOpaque immediately before TRIPWIRE, after the translucent
        // group. Keep the second command list separate so both callsite
        // boundaries receive their own Rust-owned color/depth snapshot.
        let mut final_double_pass_dh_composite_ops = Vec::new();
        let mut builtin_lightmap_upload_ops = Vec::new();
        let builtin_terrain_lightmap_resource_set = if builtin_terrain_lightmap_required {
            let lightmap_layout = self
                .builtin_terrain_lightmap_layout
                .expect("builtin terrain lightmap layout is installed before command assembly");
            let runtime = self
                .shader_runtime
                .as_mut()
                .expect("shader runtime is installed before builtin lightmap command staging");
            runtime.stage_vanilla_lightmap_residency(gal, &mut builtin_lightmap_upload_ops)?;
            let resource_set = runtime
                .vanilla_lightmap_resource_set(gal, lightmap_layout, true)?
                .ok_or_else(|| {
                    GalError::unsupported_feature(
                        "Rust indexed meshes, weather and particles require a staged copied vanilla lightmap",
                    )
                })?;
            Some(TerrainShaderResourceSet {
                set_index: 1,
                set: resource_set.set,
            })
        } else {
            None
        };
        ops.append(&mut builtin_lightmap_upload_ops);
        if clear_background {
            ops.push(CommandOp::Barrier(texture_barrier(
                depth_texture,
                TextureUsageState::Undefined,
                TextureUsageState::DepthStencilAttachment,
            )));
        }
        // Start a requested depth domain before any material or mesh writer.
        // In particular, the hand keeps world color but requests fresh depth;
        // a world mesh pass must not clear depth again after particles drew.
        let clear_direct_mesh_depth = !use_g_buffer_mesh_path
            && !mesh_batches.is_empty()
            && frame.background.load_intent == WORLD_BACKGROUND_LOAD_CLEAR;
        if clear_background || clear_direct_mesh_depth {
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![PassAttachment {
                    view: color_attachment,
                    load_op: if clear_background {
                        AttachmentLoadOp::Clear
                    } else {
                        AttachmentLoadOp::Load
                    },
                    store_op: AttachmentStoreOp::Store,
                    clear_color: clear_background.then_some(background_color),
                }],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.push(CommandOp::EndPass);
        }
        // The sky is background, not a late mesh writer: drawing it after
        // no-depth-write celestial materials would erase the submitted stars.
        if draw_vanilla_sky_disc && !use_g_buffer_mesh_path {
            let resources = self
                .sky_disc_forward_resources
                .get(&(color_format, raster_y_direction))
                .ok_or_else(|| {
                    GalError::backend("direct vanilla sky-disc resources vanished before submit")
                })?;
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::HostWriteBuffer {
                buffer: resources.uniform_buffer,
                offset: 0,
                data: packed_sky_disc_uniforms(&frame),
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
                dynamic_offsets: Vec::new(),
            });
            ops.push(CommandOp::Draw {
                vertices: 10,
                instances: 1,
            });
            ops.push(CommandOp::EndPass);
        }
        let mut forward_material_draws = Vec::new();
        let mut receiver_shadow_ops = Vec::new();
        let mut deferred_entity_layer_ops = Vec::new();
        // Frozen draws particles in their own frame pass after the whole main
        // pass: after translucent terrain and both DH vanilla-fade boundaries.
        // Drawn earlier, a DH fade reads the terrain behind each particle and
        // replaces the particle's pixels with LOD/sky colour.
        let mut late_particle_ops = Vec::new();
        if !material_batches.is_empty() {
            let mut material_slot_indices = BTreeMap::new();
            let mut material_draws = Vec::with_capacity(material_batches.len());
            let mut particle_draws = Vec::with_capacity(material_batches.len());
            for batch in material_batches {
                let slot_index = material_slot_indices.entry(batch.key).or_insert(0usize);
                let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("world material resources vanished before submit")
                })?;
                let slot = resources
                    .data_slots
                    .get(*slot_index)
                    .ok_or_else(|| GalError::backend("world material data slot missing"))?;
                *slot_index += 1;
                let uniforms = packed_material_uniforms_for_batch(&frame, batch)?;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: slot.uniform_buffer,
                    offset: 0,
                    data: uniforms,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                material_draws.push((
                    batch.key.material_id,
                    resources.pipeline,
                    resources.pipeline_layout,
                    slot.resource_set,
                    resources.lightmap_resource_layout.is_some(),
                    resources.index_buffer,
                    batch.count() as u32,
                ));
                particle_draws.push(batch.key.source_program == WORLD_MATERIAL_SOURCE_PARTICLES);
            }
            if use_g_buffer_mesh_path {
                forward_material_draws = material_draws
                    .into_iter()
                    .map(
                        |(
                            _material_id,
                            pipeline,
                            pipeline_layout,
                            resource_set,
                            requires_lightmap,
                            index_buffer,
                            instance_count,
                        )| {
                            TerrainForwardMaterialDraw {
                                pipeline,
                                pipeline_layout,
                                resource_set,
                                shader_resource_set: requires_lightmap
                                    .then_some(builtin_terrain_lightmap_resource_set)
                                    .flatten(),
                                index_buffer,
                                index_offset: 0,
                                index_type: IndexType::U32,
                                index_count: 6,
                                instance_count,
                            }
                        },
                    )
                    .collect();
            } else {
                ops.push(CommandOp::BeginPass {
                    pass,
                    target: frame_target,
                    colors: vec![loaded_frame_color_attachment(color_attachment)],
                    depth_stencil: Some(PassAttachment {
                        view: depth_view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                });
                for (
                    (
                        material_id,
                        pipeline,
                        pipeline_layout,
                        resource_set,
                        requires_lightmap,
                        index_buffer,
                        instance_count,
                    ),
                    particle,
                ) in material_draws.into_iter().zip(particle_draws)
                {
                    // Receiver shadows consume the completed world depth.
                    // The direct terrain pass below clears that attachment;
                    // emitting shadows here would erase them under terrain.
                    let ops = if particle {
                        &mut late_particle_ops
                    } else if material_id == WORLD_MATERIAL_ID_ENTITY_SHADOW {
                        &mut receiver_shadow_ops
                    } else if material_id == WORLD_MATERIAL_ID_ENERGY_SWIRL {
                        // EnergySwirl is authored after its entity's base model.
                        // Its inflated shell writes depth, so drawing this material
                        // before entity meshes would occlude the base instead of
                        // adding the charged overlay over it.
                        &mut deferred_entity_layer_ops
                    } else {
                        &mut ops
                    };
                    ops.push(CommandOp::BindGraphicsPipeline(pipeline));
                    ops.push(CommandOp::BindResourceSet {
                        pipeline_layout,
                        set_index: 0,
                        set: resource_set,
                        dynamic_offsets: Vec::new(),
                    });
                    if requires_lightmap {
                        let lightmap = builtin_terrain_lightmap_resource_set.ok_or_else(|| {
                            GalError::backend(
                                "weather material pipeline has no staged Rust lightmap binding",
                            )
                        })?;
                        ops.push(CommandOp::BindResourceSet {
                            pipeline_layout,
                            set_index: lightmap.set_index,
                            set: lightmap.set,
                            dynamic_offsets: Vec::new(),
                        });
                    }
                    ops.push(CommandOp::SetIndexBuffer {
                        buffer: index_buffer,
                        offset: 0,
                        index_type: IndexType::U32,
                    });
                    ops.push(CommandOp::DrawIndexed {
                        indices: 6,
                        instances: instance_count,
                    });
                }
                ops.push(CommandOp::EndPass);
            }
        }
        self.ensure_lod_direct_composition_resources(
            gal,
            frame_target,
            color_format,
            raster_y_direction,
            direct_dh_fog_composition,
        )?;
        let lod_material_started = std::time::Instant::now();
        let mut lod_mesh_draws =
            if use_g_buffer_mesh_path || frame.lod_render_frame.rust_route_selected() {
                self.stage_rust_lod_material_draws(
                    gal,
                    &frame,
                    use_g_buffer_mesh_path,
                    color_format,
                    &mut ops,
                )?
            } else {
                Vec::new()
            };
        let mut post_ssao_generic_draws = Vec::new();
        if !distant_horizons_generic_batches.is_empty() || !distant_horizons_box_batches.is_empty()
        {
            if !direct_dh_fog_composition {
                return Err(GalError::unsupported_feature(
                    "DH generic objects require the private direct DH composition target",
                ));
            }
            let lightmap = builtin_terrain_lightmap_resource_set.ok_or_else(|| {
                GalError::backend("DH generic material has no staged Rust lightmap binding")
            })?;
            let mut slot_indices = BTreeMap::<MaterialResourceKey, usize>::new();
            let mut pre_ssao_generic_draws =
                Vec::with_capacity(distant_horizons_generic_batches.len());
            for batch in &distant_horizons_generic_batches {
                let slot_index = slot_indices.entry(batch.key).or_insert(0usize);
                let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("DH generic material resources vanished before submit")
                })?;
                let slot = resources
                    .data_slots
                    .get(*slot_index)
                    .ok_or_else(|| GalError::backend("DH generic material data slot missing"))?;
                *slot_index += 1;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: slot.uniform_buffer,
                    offset: 0,
                    data: packed_material_uniforms_for_batch(&frame, batch)?,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                let draw = TerrainMeshDraw {
                    shadow: None,
                    pipeline: resources.pipeline,
                    offscreen_pipeline: Some(resources.pipeline),
                    pipeline_layout: resources.pipeline_layout,
                    resource_set: slot.resource_set,
                    resource_set_dynamic_offsets: Vec::new().into(),
                    shader_resource_set: Some(lightmap),
                    index_buffer: resources.index_buffer,
                    index_offset: 0,
                    index_type: IndexType::U32,
                    index_count: 6,
                    instance_count: batch.count() as u32,
                    indexed_indirect: None,
                    stratum: batch.key.stratum,
                    material_mode: terrain_material_pass_mode(batch.key.material_mode)?,
                    shadow_participation: TerrainShadowParticipation::Unavailable,
                };
                if batch.key.stratum == WORLD_STRATUM_DH_GENERIC_SSAO {
                    pre_ssao_generic_draws.push(draw);
                } else {
                    post_ssao_generic_draws.push(draw);
                }
            }
            for batch in &distant_horizons_box_batches {
                let slot_index = slot_indices.entry(batch.key).or_insert(0usize);
                let resources = self.material_resources.get(&batch.key).ok_or_else(|| {
                    GalError::backend("compact DH generic box resources vanished before submit")
                })?;
                let slot = resources
                    .data_slots
                    .get(*slot_index)
                    .ok_or_else(|| GalError::backend("compact DH generic box data slot missing"))?;
                *slot_index += 1;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: slot.uniform_buffer,
                    offset: 0,
                    data: packed_dh_generic_box_uniforms_for_batch(&frame, batch),
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    slot.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                let draw = TerrainMeshDraw {
                    shadow: None,
                    pipeline: resources.pipeline,
                    offscreen_pipeline: Some(resources.pipeline),
                    pipeline_layout: resources.pipeline_layout,
                    resource_set: slot.resource_set,
                    resource_set_dynamic_offsets: SmallVec::new(),
                    shader_resource_set: Some(lightmap),
                    index_buffer: resources.index_buffer,
                    index_offset: 0,
                    index_type: IndexType::U32,
                    index_count: 6,
                    instance_count: batch.face_count(),
                    indexed_indirect: None,
                    stratum: batch.key.stratum,
                    material_mode: terrain_material_pass_mode(batch.key.material_mode)?,
                    shadow_participation: TerrainShadowParticipation::Unavailable,
                };
                if batch.key.stratum == WORLD_STRATUM_DH_GENERIC_SSAO {
                    pre_ssao_generic_draws.push(draw);
                } else {
                    post_ssao_generic_draws.push(draw);
                }
            }
            let insertion = lod_mesh_draws
                .iter()
                .position(|draw| draw.material_mode == TerrainMaterialPassMode::Translucent)
                .unwrap_or(lod_mesh_draws.len());
            lod_mesh_draws.splice(insertion..insertion, pre_ssao_generic_draws);
        }
        whole_frame_phase_trace(
            "lod-material-draws",
            frame.frame_id,
            Some(lod_material_started),
        );
        if !mesh_batches.is_empty() || !lod_mesh_draws.is_empty() || use_g_buffer_mesh_path {
            // The direct vanilla graph and the admitted source graph consume
            // the same immutable frame fog semantics.  Keep this bounded
            // diagnostic at their shared submission boundary so ordinary
            // Rust Vulkan captures can prove the values without changing
            // either graph's commands or presentation.
            write_normal_route_fog_diagnostic(&frame);
            let (mut mesh_draws, lowered_source_submission) = if let Some(programs) =
                lowered_source_terrain_programs.as_ref()
            {
                let source_plan = self.prepare_lowered_source_terrain_frame_plan(
                    gal,
                    programs,
                    frame.voxel_volume.world_generation,
                    &frame,
                    &mesh_batches,
                )?;
                let (draws, upload_ops, mut submission) = source_plan.into_submission_parts();
                ops.extend(upload_ops);
                if let Some(submission) = submission.as_mut() {
                    submission.uploaded_source_material_texture_ids = submission
                        .source_material_texture_ids
                        .iter()
                        .filter_map(|texture_id| {
                            let resources =
                                self.source_material_texture_resources.get(texture_id)?;
                            ops.iter()
                                .any(|operation| {
                                    matches!(
                                        operation,
                                        CommandOp::CopyBufferToTexture(region)
                                            if region.texture == resources.texture
                                    )
                                })
                                .then_some(*texture_id)
                        })
                        .collect();
                }
                (draws, Some(submission))
            } else if mesh_batches.is_empty() {
                // The shared terrain graph also consumes Rust-owned LOD
                // draws. A LOD-only frame has no indexed-mesh instance table
                // to upload, so it must not manufacture a zero-byte host
                // write merely to enter the common draw phase.
                (Vec::new(), None)
            } else {
                let mesh_pack_started = std::time::Instant::now();
                let packed_stream = packed_mesh_draw_stream(
                    &frame,
                    self,
                    &mesh_batches,
                    stats.profile.world_prepare_mesh_stream_required_bytes,
                    source_terrain_programs.is_none()
                        && gal.capabilities().supports(BackendFeature::IndirectDraw),
                    crate::core::environment::var_os("MATTMC_RUST_DISABLE_TRANSLUCENT_PAGE_INDIRECT").is_none(),
                )?;
                stats.profile.world_mesh_stream_payload_pack_nanos =
                    elapsed_nanos_u64(mesh_pack_started);
                stats.profile.world_mesh_stream_payload_bytes = packed_stream.payload.len() as u64;
                stats.profile.world_mesh_dynamic_offset_count = packed_stream
                    .first_instances
                    .iter()
                    .filter(|first_instance| first_instance.is_none())
                    .count() as u64;
                let mesh_stream_binding =
                    self.ensure_mesh_instance_stream(gal, packed_stream.payload.len() as u64)?;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    mesh_stream_binding.buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: mesh_stream_binding.buffer,
                    offset: 0,
                    data: packed_stream.payload,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    mesh_stream_binding.buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                let indirect_draw_count = if source_terrain_programs.is_none() {
                    packed_stream
                        .first_instances
                        .iter()
                        .filter(|value| value.is_some())
                        .count()
                } else {
                    0
                };
                let indirect_stream = if indirect_draw_count == 0 {
                    None
                } else {
                    Some(self.ensure_mesh_indirect_stream(
                        gal,
                        indirect_draw_count as u64 * WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES,
                    )?)
                };
                let mut indirect_payload = Vec::with_capacity(
                    indirect_draw_count * WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES as usize,
                );
                let mut pending_draws = Vec::with_capacity(mesh_batches.len());
                let mut previous_page_set: Option<(MeshResourceKey, Handle)> = None;
                let mesh_draw_record_started = std::time::Instant::now();
                for (batch_index, batch) in mesh_batches.iter().enumerate() {
                    let use_indirect = source_terrain_programs.is_none()
                        && packed_stream.first_instances[batch_index].is_some();
                    let (
                        index_buffer,
                        index_type,
                        geometry_index_offset,
                        vertex_offset,
                        vertex_stride,
                        shadow_pipeline,
                        pipeline,
                        pipeline_layout,
                        resource_set,
                        cached_page_resource_set,
                        shader_resource_set,
                    ) = if let Some(programs) = source_terrain_programs.as_ref() {
                        let candidate = programs.for_material_mode(batch.key.material_mode)?;
                        let source_key = source_mesh_resource_key(batch.key, candidate);
                        let resources =
                            self.source_mesh_resources.get(&source_key).ok_or_else(|| {
                                GalError::backend("source mesh resources vanished before submit")
                            })?;
                        (
                            resources.index_buffer,
                            resources.index_type,
                            resources.index_offset,
                            resources.vertex_offset,
                            resources.vertex_stride,
                            resources.shadow_pipeline,
                            resources.pipeline,
                            resources.pipeline_layout,
                            resources.resource_set,
                            None,
                            candidate.binding.map(|binding| binding.resource_set),
                        )
                    } else {
                        let resources = self.mesh_resources.get(&batch.key).ok_or_else(|| {
                            GalError::backend("world mesh resources vanished before submit")
                        })?;
                        (
                            resources.index_buffer,
                            resources.index_type,
                            resources.index_offset,
                            resources.vertex_offset,
                            resources.vertex_stride,
                            resources.shadow_pipeline,
                            resources.pipeline,
                            resources.pipeline_layout,
                            resources.resource_set,
                            resources.page_resource_set,
                            builtin_terrain_lightmap_resource_set,
                        )
                    };
                    let (index_buffer, geometry_index_offset, batch_index_offset) =
                        if let Some(offset) = batch.sorted_index_offset {
                            let stream = sorted_index_stream.ok_or_else(|| {
                                GalError::backend("camera-sorted index stream was not uploaded")
                            })?;
                            (stream.buffer, 0, offset)
                        } else {
                            (index_buffer, geometry_index_offset, batch.index_offset)
                        };
                    let (resource_set, dynamic_offsets, draw_index_offset, page_command) =
                        if use_indirect {
                            // Resource sets are stable throughout this frame's draw-record
                            // construction. Camera-sorted translucent quads repeatedly use
                            // the same exact mesh key, so borrow the adjacent binding
                            // instead of revisiting the residency map for every quad.
                            let page_set = match previous_page_set {
                                Some((key, set)) if key == batch.key => set,
                                _ => {
                                    let set = if let Some(set) = cached_page_resource_set {
                                        set
                                    } else {
                                        self.ensure_mesh_page_resource_set(
                                            gal,
                                            batch.key,
                                            mesh_stream_binding,
                                            builtin_mesh_resource_layout.ok_or_else(|| {
                                                GalError::backend(
                                                    "indexed mesh page binding requires a Rust-owned lightmap layout",
                                                )
                                            })?,
                                        )?
                                    };
                                    previous_page_set = Some((batch.key, set));
                                    set
                                }
                            };
                            let index_size = match index_type {
                                IndexType::U16 => 2u64,
                                IndexType::U32 => 4u64,
                            };
                            let absolute_index_offset = geometry_index_offset
                                .checked_add(batch_index_offset)
                                .ok_or_else(|| {
                                    GalError::invalid_argument("world mesh index offset overflow")
                                })?;
                            if absolute_index_offset % index_size != 0
                                || vertex_offset % vertex_stride as u64 != 0
                            {
                                return Err(GalError::invalid_argument(
                                    "page-addressed mesh geometry is not record aligned",
                                ));
                            }
                            let first_index = u32::try_from(absolute_index_offset / index_size)
                                .map_err(|_| {
                                    GalError::invalid_argument("world mesh first index exceeds u32")
                                })?;
                            let vertex_offset_records = i32::try_from(
                                vertex_offset / vertex_stride as u64,
                            )
                            .map_err(|_| {
                                GalError::invalid_argument("world mesh vertex offset exceeds i32")
                            })?;
                            (
                                page_set,
                                smallvec![0, 0],
                                0,
                                Some(PageIndexedDrawCommand {
                                    index_count: batch.index_count,
                                    instance_count: batch.count() as u32,
                                    first_index,
                                    vertex_offset: vertex_offset_records,
                                    first_instance: packed_stream.first_instances[batch_index]
                                        .expect("indirect batch has first instance"),
                                }),
                            )
                        } else {
                            (
                                resource_set,
                                mesh_stream_dynamic_offsets_inline(
                                    batch,
                                    vertex_offset,
                                    packed_stream.dynamic_offsets[batch_index],
                                )?,
                                geometry_index_offset
                                    .checked_add(batch_index_offset)
                                    .ok_or_else(|| {
                                        GalError::invalid_argument(
                                            "world mesh index offset overflow",
                                        )
                                    })?,
                                None,
                            )
                        };
                    let front_to_back_distance_squared = if page_command.is_some()
                        && matches!(
                            batch.key.material_mode,
                            WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT
                        ) {
                        batch
                            .indices
                            .iter()
                            .filter_map(|index| frame.mesh_instances.get(*index))
                            .map(|instance| {
                                // Static terrain vertices are section-local and
                                // their transforms are camera-relative. Use the
                                // section centre so compatible opaque/cutout
                                // draws populate depth from near to far without
                                // changing any semantic or resource grouping.
                                let x = instance.transform[12] + 8.0;
                                let y = instance.transform[13] + 8.0;
                                let z = instance.transform[14] + 8.0;
                                x.mul_add(x, y.mul_add(y, z * z))
                            })
                            .fold(f32::INFINITY, f32::min)
                    } else {
                        f32::INFINITY
                    };
                    // Only draws whose pipeline set includes a shadow
                    // pipeline (opaque/cutout G-buffer meshes) cast shadows;
                    // others must not be demanded by the shadow pass.
                    let shadow_participation = if shadow_pipeline.is_some() {
                        TerrainShadowParticipation::Required
                    } else {
                        TerrainShadowParticipation::Unavailable
                    };
                    pending_draws.push(PendingMeshDraw {
                        draw: TerrainMeshDraw {
                            shadow: shadow_pipeline.map(|pipeline| TerrainShadowDraw {
                                pipeline,
                                pipeline_layout,
                                resource_set,
                                resource_set_dynamic_offsets: dynamic_offsets.clone(),
                                shader_resource_set,
                            }),
                            pipeline,
                            offscreen_pipeline: None,
                            pipeline_layout,
                            resource_set,
                            resource_set_dynamic_offsets: dynamic_offsets,
                            shader_resource_set,
                            index_buffer,
                            index_offset: draw_index_offset,
                            index_type,
                            index_count: batch.index_count,
                            instance_count: batch.count() as u32,
                            indexed_indirect: None,
                            stratum: batch.key.stratum,
                            // Standard item foil has a single-target pipeline;
                            // in the G-buffer layout it belongs to the
                            // single-target translucent phase after lighting,
                            // not the four-target terrain pass.
                            material_mode: if use_g_buffer_mesh_path && batch.key.standard_item_foil {
                                TerrainMaterialPassMode::Translucent
                            } else {
                                terrain_material_pass_mode(batch.key.material_mode)?
                            },
                            shadow_participation,
                        },
                        page_command,
                        front_to_back_distance_squared,
                    });
                }
                order_compatible_page_indirect_draws(&mut pending_draws);
                stats.profile.world_mesh_page_indirect_batch_count = pending_draws
                    .iter()
                    .filter(|pending| pending.page_command.is_some())
                    .count()
                    as u64;
                stats.profile.world_mesh_page_indirect_run_count = pending_draws
                    .iter()
                    .enumerate()
                    .filter(|(index, pending)| {
                        pending.page_command.is_some()
                            && (*index == 0
                                || pending_draws[*index - 1].page_command.is_none()
                                || page_indirect_draw_order(&pending_draws[*index - 1], pending)
                                    != std::cmp::Ordering::Equal)
                    })
                    .count()
                    as u64;
                stats.profile.world_mesh_dynamic_terrain_batch_count = pending_draws
                    .iter()
                    .filter(|pending| {
                        pending.page_command.is_none()
                            && pending.draw.stratum == WORLD_STRATUM_TERRAIN
                    })
                    .count()
                    as u64;
                stats.profile.world_mesh_dynamic_non_terrain_batch_count = pending_draws
                    .iter()
                    .filter(|pending| {
                        pending.page_command.is_none()
                            && pending.draw.stratum != WORLD_STRATUM_TERRAIN
                    })
                    .count()
                    as u64;
                stats.profile.world_mesh_terrain_translucent_batch_count = pending_draws
                    .iter()
                    .filter(|pending| {
                        pending.draw.stratum == WORLD_STRATUM_TERRAIN
                            && pending.draw.material_mode == TerrainMaterialPassMode::Translucent
                    })
                    .count()
                    as u64;
                for pending in &mut pending_draws {
                    let Some(command) = pending.page_command else {
                        continue;
                    };
                    let command_offset = indirect_payload.len() as u64;
                    command.append_bytes(&mut indirect_payload);
                    pending.draw.indexed_indirect =
                        indirect_stream.map(|stream| TerrainIndexedIndirect {
                            buffer: stream.buffer,
                            offset: command_offset,
                            draw_count: 1,
                        });
                }
                let draws = pending_draws
                    .into_iter()
                    .map(|pending| pending.draw)
                    .collect();
                if let Some(stream) = indirect_stream {
                    debug_assert_eq!(indirect_payload.len(), indirect_draw_count * 20);
                    ops.push(CommandOp::Barrier(buffer_barrier(
                        stream.buffer,
                        if stream.initialized {
                            TextureUsageState::IndirectRead
                        } else {
                            TextureUsageState::Undefined
                        },
                        TextureUsageState::TransferDst,
                    )));
                    ops.push(CommandOp::HostWriteBuffer {
                        buffer: stream.buffer,
                        offset: 0,
                        data: indirect_payload,
                    });
                    ops.push(CommandOp::Barrier(buffer_barrier(
                        stream.buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::IndirectRead,
                    )));
                    if let Some(slot) = self.mesh_indirect_stream.as_mut() {
                        slot.initialized = true;
                    }
                }
                stats.profile.world_mesh_draw_record_nanos =
                    elapsed_nanos_u64(mesh_draw_record_started);
                (draws, None)
            };
            if direct_dh_fog_composition {
                let resources =
                    self.lod_direct_composition_resources
                        .as_ref()
                        .ok_or_else(|| {
                            GalError::backend(
                                "direct DH fog composition resources missing after preparation",
                            )
                        })?;
                let previous_usage = if self.lod_direct_composition_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                };
                resources.append_begin(
                    previous_usage,
                    previous_usage,
                    frame.shader_environment.fog_parameter_color,
                    &mut ops,
                );
                // Frozen draws opaque LODs and SSAO-enabled generic groups,
                // computes SSAO from that depth, then draws non-SSAO generic
                // groups and transparent LODs. Preserve that producer order
                // with two explicit private-target passes.
                let first_translucent_lod = lod_mesh_draws
                    .iter()
                    .position(|draw| draw.material_mode == TerrainMaterialPassMode::Translucent)
                    .unwrap_or(lod_mesh_draws.len());
                let transparent_lod_draws = lod_mesh_draws.split_off(first_translucent_lod);
                append_private_dh_draws(&lod_mesh_draws, &mut ops)?;
                resources.append_end(&mut ops);
                post_ssao_generic_draws.extend(transparent_lod_draws);
                let inverse_combined = invert_column_major_mat4(
                    frame.lod_render_frame.combined_matrix,
                    "direct DH composition combined matrix",
                )?;
                let inverse_vanilla = if vanilla_fade_mode > 0.0 {
                    // The vanilla depth snapshot belongs to the whole-frame
                    // camera, so reconstruct positions with that exact camera
                    // transform rather than DH's independently copied matrices.
                    let vanilla_combined =
                        multiply_column_major_mat4(frame.projection_matrix, frame.view_matrix);
                    invert_column_major_mat4(
                        vanilla_combined,
                        "direct DH vanilla fade combined matrix",
                    )?
                } else {
                    [0.0; 16]
                };
                let fade_parameters = [
                    frame.lod_render_frame.clip_distance * 1.5,
                    frame.lod_render_frame.clip_distance * 1.9,
                    vanilla_fade_mode,
                    frame.lod_render_frame.max_level_height as f32,
                ];
                let far_fade_parameters = [
                    frame.lod_render_frame.clip_distance * 1.5,
                    frame.lod_render_frame.clip_distance * 1.9,
                    if far_clip_fade { 4.0 } else { 0.0 },
                    frame.lod_render_frame.max_level_height as f32,
                ];
                let mut compositor_dh_fog_parameters = frame.lod_render_frame.dh_fog_parameters;
                // Capture-only isolation for the private color/depth boundary.
                // This leaves route selection and the source-owned draw
                // uniforms untouched, so it can distinguish compositor fog
                // math from private-target geometry without changing the
                // production contract.
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_NO_FOG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[16] = 0.0;
                }
                // Capture-only probe for the private DH depth attachment. A
                // negative enable sentinel is kept out of normal semantics;
                // the compositor shader returns normalized depth so a
                // deterministic frame can distinguish a real write from the
                // cleared value before fog math is changed.
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_DEPTH_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[16] = -1.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_FOG_RECONSTRUCTION").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[19] = -1.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_FOG_FACTOR_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[19] = -2.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_VANILLA_COLOR_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[19] = -3.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_VANILLA_FADE_FACTOR_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[19] = -4.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COVERAGE_MASK_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[19] = -5.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_FADE_HEIGHT_GUARD_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[19] = -6.0;
                }
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COLOR_DEBUG").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[18] = -3.0;
                }
                // Capture-only projection/depth convention probe. This is
                // independent of the production matrix and source route; it
                // only asks the fullscreen audit shader to invert the
                // sampled private depth for one comparison run.
                if matches!(
                    crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_INVERT_DEPTH").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) && matches!(
                    crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
                    Ok("1") | Ok("true") | Ok("TRUE")
                ) {
                    compositor_dh_fog_parameters[18] = -4.0;
                }
                if !skip_dh_composite_for_audit {
                    let previous_vanilla_color = if self.lod_vanilla_sample_state_initialized
                        || self.pending_lod_vanilla_sample_state_established
                    {
                        TextureUsageState::ShaderRead
                    } else {
                        TextureUsageState::Undefined
                    };
                    let previous_vanilla_depth = previous_vanilla_color;
                    let previous_ssao = if self.lod_ssao_initialized {
                        TextureUsageState::ShaderRead
                    } else {
                        TextureUsageState::Undefined
                    };
                    if frame.lod_render_frame.ssao_parameters[0] >= 0.5 {
                        resources.append_ssao(
                            frame.lod_render_frame.projection_matrix,
                            frame.lod_render_frame.projection_inverse_matrix,
                            frame.lod_render_frame.ssao_parameters,
                            previous_ssao,
                            &mut ops,
                        );
                        stats.lod_ssao_pass_appended = true;
                        self.pending_lod_ssao_written = true;
                    } else {
                        resources.append_ssao_sample_state(previous_ssao, &mut ops);
                    }
                    if !post_ssao_generic_draws.is_empty() {
                        resources.append_resume(&mut ops);
                        append_private_dh_draws(&post_ssao_generic_draws, &mut ops)?;
                        resources.append_end(&mut ops);
                    }

                    // Frozen resolves DH's own far fade and fog before any
                    // vanilla terrain is drawn. Far fade alone needs the
                    // current sky/background color; later vanilla snapshots
                    // must never replace this resolver input.
                    if far_clip_fade {
                        resources.append_vanilla_snapshot(
                            frame_target,
                            owned_color_texture,
                            depth_texture,
                            previous_vanilla_color,
                            previous_vanilla_depth,
                            &mut ops,
                        );
                    } else {
                        resources.append_vanilla_sample_state(
                            previous_vanilla_color,
                            previous_vanilla_depth,
                            &mut ops,
                        );
                    }
                    // Both paths establish ShaderRead for the resolver. Later
                    // vanilla fade copies must preserve that same-frame use,
                    // even when far fade did not need snapshot contents.
                    self.pending_lod_vanilla_sample_state_established = true;
                    let previous_resolved = if self.lod_direct_composition_initialized {
                        TextureUsageState::ShaderRead
                    } else {
                        TextureUsageState::Undefined
                    };
                    resources.append_resolve(
                        previous_resolved,
                        frame.lod_render_frame.combined_matrix,
                        inverse_combined,
                        frame.lod_render_frame.camera_world_position,
                        frame.shader_environment.fog_parameter_color,
                        [
                            frame.shader_environment.fog_environmental_start,
                            frame.shader_environment.fog_environmental_end,
                            frame.shader_environment.fog_render_distance_start,
                            frame.shader_environment.fog_render_distance_end,
                        ],
                        compositor_dh_fog_parameters,
                        far_fade_parameters,
                        frame.lod_render_frame.ssao_parameters,
                        &mut ops,
                    );
                    // This is Frozen's unconditional DhApplyShader boundary:
                    // the resolved DH image becomes the underlay before
                    // vanilla opaque and translucent terrain execute.
                    resources.append_apply(
                        frame_target,
                        pass,
                        color_attachment,
                        depth_view,
                        &mut ops,
                    );
                    stats.lod_direct_composite_pass_count += 1;
                    self.pending_lod_direct_composition_written = true;

                    if vanilla_fade_mode > 0.0 {
                        if !skip_opaque_double_pass_for_audit {
                            resources.append_fade(
                                frame_target,
                                pass,
                                color_attachment,
                                depth_view,
                                frame.lod_render_frame.combined_matrix,
                                inverse_combined,
                                inverse_vanilla,
                                frame.lod_render_frame.camera_world_position,
                                frame.shader_environment.fog_parameter_color,
                                [
                                    frame.shader_environment.fog_environmental_start,
                                    frame.shader_environment.fog_environmental_end,
                                    frame.shader_environment.fog_render_distance_start,
                                    frame.shader_environment.fog_render_distance_end,
                                ],
                                compositor_dh_fog_parameters,
                                fade_parameters,
                                frame.lod_render_frame.ssao_parameters,
                                &mut deferred_dh_composite_ops,
                            );
                            stats.lod_direct_composite_pass_count += 1;
                        }
                        if double_pass_vanilla_fade && !skip_final_double_pass_for_audit {
                            resources.append_fade(
                                frame_target,
                                pass,
                                color_attachment,
                                depth_view,
                                frame.lod_render_frame.combined_matrix,
                                inverse_combined,
                                inverse_vanilla,
                                frame.lod_render_frame.camera_world_position,
                                frame.shader_environment.fog_parameter_color,
                                [
                                    frame.shader_environment.fog_environmental_start,
                                    frame.shader_environment.fog_environmental_end,
                                    frame.shader_environment.fog_render_distance_start,
                                    frame.shader_environment.fog_render_distance_end,
                                ],
                                compositor_dh_fog_parameters,
                                fade_parameters,
                                frame.lod_render_frame.ssao_parameters,
                                &mut final_double_pass_dh_composite_ops,
                            );
                            stats.lod_direct_composite_pass_count += 1;
                        }
                    }
                }
                if skip_dh_composite_for_audit && !post_ssao_generic_draws.is_empty() {
                    resources.append_resume(&mut ops);
                    append_private_dh_draws(&post_ssao_generic_draws, &mut ops)?;
                    resources.append_end(&mut ops);
                }
                lod_mesh_draws.clear();
            }
            // Opaque DH LOD geometry is a far-field color contributor. Put it
            // before vanilla terrain so the vanilla depth/color path remains
            // authoritative wherever both streams overlap. The shared LOD
            // target does not write depth; the private DH compositor owns the
            // source-faithful depth state when that boundary is active.
            if !lod_mesh_draws.is_empty() {
                let mut opaque_lod = Vec::new();
                let mut later_lod = Vec::new();
                for draw in lod_mesh_draws {
                    if draw.material_mode == TerrainMaterialPassMode::Opaque {
                        opaque_lod.push(draw);
                    } else {
                        later_lod.push(draw);
                    }
                }
                opaque_lod.extend(mesh_draws);
                mesh_draws = opaque_lod;
                mesh_draws.extend(later_lod);
            }
            if use_g_buffer_mesh_path {
                let shader_plan_started = std::time::Instant::now();
                let (depth_history, depth_history_submission) = {
                    let g_buffer = self.g_buffer_resources.as_ref().ok_or_else(|| {
                        GalError::backend("G-buffer resources missing before mesh submit")
                    })?;
                    match self
                        .prepare_g_buffer_depth_history_submission(frame.frame_id, g_buffer)?
                    {
                        Some((plan, submission)) => (Some(plan), Some(submission)),
                        None => (None, None),
                    }
                };
                let g_buffer = self.g_buffer_resources.as_ref().ok_or_else(|| {
                    GalError::backend("G-buffer resources missing before mesh submit")
                })?;
                let final_binding_key = g_buffer_final_binding_key.ok_or_else(|| {
                    GalError::backend("G-buffer final binding missing before mesh submit")
                })?;
                let final_binding = self
                    .g_buffer_final_bindings
                    .get(&final_binding_key)
                    .ok_or_else(|| {
                        GalError::backend("G-buffer final binding vanished before mesh submit")
                    })?;
                let executor = self
                    .shader_runtime
                    .as_ref()
                    .expect("shader runtime installed before terrain graph submit");
                stats.profile.shader_plan_lookup_nanos = stats
                    .profile
                    .shader_plan_lookup_nanos
                    .saturating_add(elapsed_nanos_u64(shader_plan_started));
                let mut runtime_targets = terrain_runtime_targets(g_buffer, final_binding);
                if lowered_source_terrain_programs.is_some() {
                    // Source packs own their declared translucent output
                    // formats; the vanilla RGBA8 capture is only valid for
                    // the normal deferred graph until source-target handoff
                    // is implemented explicitly.
                    runtime_targets.translucent_capture = None;
                }
                if draw_vanilla_sky_disc {
                    let resources = self
                        .sky_disc_resources
                        .get(&(ColorFormat::Rgba8Unorm, raster_y_direction))
                        .ok_or_else(|| {
                            GalError::backend(
                                "vanilla sky-disc G-buffer resources vanished before submit",
                            )
                        })?;
                    for texture in [
                        g_buffer.albedo_texture,
                        g_buffer.normal_texture,
                        g_buffer.material_light_texture,
                        g_buffer.world_position_texture,
                    ] {
                        ops.push(CommandOp::Barrier(texture_barrier(
                            texture,
                            TextureUsageState::Undefined,
                            TextureUsageState::ColorAttachment,
                        )));
                    }
                    ops.push(CommandOp::Barrier(texture_barrier(
                        g_buffer.depth_texture,
                        TextureUsageState::Undefined,
                        TextureUsageState::DepthStencilAttachment,
                    )));
                    ops.push(CommandOp::BeginPass {
                        pass: g_buffer.g_buffer_pass,
                        target: g_buffer.target,
                        colors: vec![
                            PassAttachment {
                                view: g_buffer.albedo_view,
                                load_op: AttachmentLoadOp::Clear,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: Some(background_color),
                            },
                            PassAttachment {
                                view: g_buffer.normal_view,
                                load_op: AttachmentLoadOp::Clear,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: Some(ClearColor {
                                    r: 0.5,
                                    g: 0.5,
                                    b: 1.0,
                                    a: 1.0,
                                }),
                            },
                            PassAttachment {
                                view: g_buffer.material_light_view,
                                load_op: AttachmentLoadOp::Clear,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: Some(ClearColor {
                                    r: 0.0,
                                    g: 1.0,
                                    b: 1.0,
                                    a: 0.0,
                                }),
                            },
                            PassAttachment {
                                view: g_buffer.world_position_view,
                                load_op: AttachmentLoadOp::Clear,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: Some(ClearColor {
                                    r: 0.5,
                                    g: 0.5,
                                    b: 0.5,
                                    a: 0.0,
                                }),
                            },
                        ],
                        depth_stencil: Some(PassAttachment {
                            view: g_buffer.depth_view,
                            load_op: AttachmentLoadOp::Clear,
                            store_op: AttachmentStoreOp::Store,
                            clear_color: None,
                        }),
                    });
                    ops.push(CommandOp::EndPass);
                    ops.push(CommandOp::Barrier(buffer_barrier(
                        resources.uniform_buffer,
                        TextureUsageState::ShaderRead,
                        TextureUsageState::TransferDst,
                    )));
                    ops.push(CommandOp::HostWriteBuffer {
                        buffer: resources.uniform_buffer,
                        offset: 0,
                        data: packed_sky_disc_uniforms(&frame),
                    });
                    ops.push(CommandOp::Barrier(buffer_barrier(
                        resources.uniform_buffer,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    )));
                    ops.push(CommandOp::BeginPass {
                        pass: g_buffer.g_buffer_pass,
                        target: g_buffer.target,
                        colors: vec![
                            loaded_frame_color_attachment(g_buffer.albedo_view),
                            loaded_frame_color_attachment(g_buffer.normal_view),
                            loaded_frame_color_attachment(g_buffer.material_light_view),
                            loaded_frame_color_attachment(g_buffer.world_position_view),
                        ],
                        depth_stencil: Some(PassAttachment {
                            view: g_buffer.depth_view,
                            load_op: AttachmentLoadOp::Load,
                            store_op: AttachmentStoreOp::Store,
                            clear_color: None,
                        }),
                    });
                    ops.push(CommandOp::BindGraphicsPipeline(resources.pipeline));
                    ops.push(CommandOp::BindResourceSet {
                        pipeline_layout: resources.pipeline_layout,
                        set_index: 0,
                        set: resources.resource_set,
                        dynamic_offsets: Vec::new(),
                    });
                    ops.push(CommandOp::Draw {
                        vertices: 10,
                        instances: 1,
                    });
                    ops.push(CommandOp::EndPass);
                    for texture in [
                        g_buffer.albedo_texture,
                        g_buffer.normal_texture,
                        g_buffer.material_light_texture,
                        g_buffer.world_position_texture,
                    ] {
                        ops.push(CommandOp::Barrier(texture_barrier(
                            texture,
                            TextureUsageState::ColorAttachment,
                            TextureUsageState::ShaderRead,
                        )));
                    }
                    ops.push(CommandOp::Barrier(texture_barrier(
                        g_buffer.depth_texture,
                        TextureUsageState::DepthStencilAttachment,
                        TextureUsageState::ShaderRead,
                    )));
                }
                if let Err(error) = executor.append_terrain_material_graph(
                    &mut ops,
                    runtime_targets,
                    TerrainRuntimeFrame {
                        frame_target,
                        color_attachment,
                        background_color,
                        g_buffer_background_initialized: draw_vanilla_sky_disc,
                        // Frozen's vanilla Sodium terrain route has no
                        // shadow-map multiplier: its fragment color is the
                        // atlas sample times the supplied vertex/lightmap
                        // color, followed by fog.  Keep the explicit shadow
                        // resources private until an admitted semantic
                        // producer supplies vanilla-equivalent shadow data;
                        // enabling the placeholder map here changes ordinary
                        // vanilla pixels.
                        uniforms: terrain_composite_uniforms(&frame, false)?,
                        depth_history,
                        translucent_capture_initialized: g_buffer.translucent_capture_initialized,
                        translucent_entity_external: self.pending_terrain_fabulous_handoff
                            && lowered_source_terrain_programs.is_none(),
                        // Fabulous owns the single final alpha composition:
                        // keep terrain panes in the dedicated translucent
                        // attachment and do not blend the same draws into the
                        // normal deferred color before that handoff.
                        translucent_terrain_external: self.pending_terrain_fabulous_handoff
                            && lowered_source_terrain_programs.is_none(),
                        screen_targets_initialized: g_buffer.screen_targets_initialized,
                        shadow_targets_initialized: g_buffer.shadow_targets_initialized,
                    },
                    &mesh_draws,
                    &forward_material_draws,
                ) {
                    if lowered_source_submission.is_some() {
                        self.discard_source_terrain_frame_transaction(gal, frame.frame_id);
                    }
                    return Err(error);
                }
                // The graph's deferred/composite writers leave all three
                // screen targets in ShaderRead for the next frame. Record
                // that semantic state only after graph construction succeeds;
                // the next frame will then emit ShaderRead→ColorAttachment
                // instead of repeatedly claiming an Undefined image.
                self.pending_graph_targets_written = true;
                if lowered_source_terrain_programs.is_none()
                    && mesh_draws.iter().any(|draw| {
                        draw.material_mode == TerrainMaterialPassMode::Translucent
                            && !(self.pending_terrain_fabulous_handoff
                                && draw.stratum == WORLD_STRATUM_ENTITY_MESH)
                    })
                {
                    self.pending_translucent_capture_written = true;
                }
                if let Some(submission) = depth_history_submission {
                    self.pending_g_buffer_depth_history_submission = Some(submission);
                }
                if self.pending_terrain_fabulous_handoff
                    && lowered_source_terrain_programs.is_none()
                {
                    self.append_terrain_external_entity_mesh_ops(&mut ops, &mesh_draws)?;
                }
            } else {
                ops.push(CommandOp::BeginPass {
                    pass,
                    target: frame_target,
                    colors: vec![loaded_frame_color_attachment(color_attachment)],
                    depth_stencil: Some(PassAttachment {
                        view: depth_view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                });
                let mut indexed_draw_state =
                    IndexedDrawState::with_indirect_limit(gal.capabilities().limits.max_draw_count);
                // Vanilla composites translucent terrain over entity shadows.
                // Keep receiver geometry first and preserve draw order within
                // each group. Only the shadow-bearing direct route needs this
                // boundary; source graph ordering remains graph-owned.
                let has_receiver_shadows = !receiver_shadow_ops.is_empty();
                let after_receiver_shadows = |draw: &&TerrainMeshDraw| {
                    has_receiver_shadows
                        && draw.stratum == WORLD_STRATUM_TERRAIN
                        && draw.material_mode == TerrainMaterialPassMode::Translucent
                };
                let mut dh_composite_inserted = false;
                for draw in mesh_draws
                    .iter()
                    .filter(|draw| !after_receiver_shadows(draw))
                    .chain(mesh_draws.iter().filter(after_receiver_shadows))
                {
                    if defer_dh_composite_until_after_opaque
                        && !dh_composite_inserted
                        && draw.material_mode == TerrainMaterialPassMode::Translucent
                        && !deferred_dh_composite_ops.is_empty()
                    {
                        ops.push(CommandOp::EndPass);
                        let resources = self
                            .lod_direct_composition_resources
                            .as_ref()
                            .ok_or_else(|| {
                                GalError::backend(
                                    "direct DH composition resources missing before deferred composite",
                                )
                            })?;
                        let previous_vanilla_state = if self.lod_vanilla_sample_state_initialized
                            || self.pending_lod_vanilla_sample_state_established
                        {
                            TextureUsageState::ShaderRead
                        } else {
                            TextureUsageState::Undefined
                        };
                        if vanilla_fade_mode > 0.0 {
                            resources.append_vanilla_snapshot(
                                frame_target,
                                owned_color_texture,
                                depth_texture,
                                previous_vanilla_state,
                                previous_vanilla_state,
                                &mut ops,
                            );
                        } else {
                            resources.append_vanilla_sample_state(
                                previous_vanilla_state,
                                previous_vanilla_state,
                                &mut ops,
                            );
                        }
                        ops.append(&mut deferred_dh_composite_ops);
                        self.pending_lod_direct_composition_written = true;
                        self.pending_lod_vanilla_sample_state_established = true;
                        dh_composite_inserted = true;
                        ops.push(CommandOp::BeginPass {
                            pass,
                            target: frame_target,
                            colors: vec![loaded_frame_color_attachment(color_attachment)],
                            depth_stencil: Some(PassAttachment {
                                view: depth_view,
                                load_op: AttachmentLoadOp::Load,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: None,
                            }),
                        });
                        indexed_draw_state = IndexedDrawState::with_indirect_limit(
                            gal.capabilities().limits.max_draw_count,
                        );
                    }
                    if after_receiver_shadows(&draw) && !receiver_shadow_ops.is_empty() {
                        ops.push(CommandOp::EndPass);
                        ops.push(CommandOp::BeginPass {
                            pass,
                            target: frame_target,
                            colors: vec![loaded_frame_color_attachment(color_attachment)],
                            depth_stencil: Some(PassAttachment {
                                view: depth_view,
                                load_op: AttachmentLoadOp::Load,
                                store_op: AttachmentStoreOp::Store,
                                clear_color: None,
                            }),
                        });
                        ops.append(&mut receiver_shadow_ops);
                        indexed_draw_state = IndexedDrawState::with_indirect_limit(
                            gal.capabilities().limits.max_draw_count,
                        );
                    }
                    append_indexed_draw(
                        &mut ops,
                        &mut indexed_draw_state,
                        draw.pipeline,
                        draw.pipeline_layout,
                        draw.resource_set,
                        &draw.resource_set_dynamic_offsets,
                        draw.shader_resource_set,
                        draw.index_buffer,
                        draw.index_offset,
                        draw.index_type,
                        draw.index_count,
                        draw.instance_count,
                        draw.indexed_indirect,
                    );
                }
                ops.push(CommandOp::EndPass);
            }
            if let Some(submission) = lowered_source_submission.flatten() {
                if self.pending_lowered_source_terrain_submission.is_some() {
                    self.discard_source_terrain_frame_transaction(gal, frame.frame_id);
                    return Err(GalError::backend(
                        "a lowered source terrain submission was already pending confirmation",
                    ));
                }
                self.pending_lowered_source_terrain_submission = Some(submission);
            }
        }
        if !deferred_dh_composite_ops.is_empty() {
            let resources = self
                .lod_direct_composition_resources
                .as_ref()
                .ok_or_else(|| {
                    GalError::backend(
                        "direct DH composition resources missing before deferred composite",
                    )
                })?;
            let previous_vanilla_state = if self.lod_vanilla_sample_state_initialized
                || self.pending_lod_vanilla_sample_state_established
            {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            };
            if vanilla_fade_mode > 0.0 || far_clip_fade {
                resources.append_vanilla_snapshot(
                    frame_target,
                    owned_color_texture,
                    depth_texture,
                    previous_vanilla_state,
                    previous_vanilla_state,
                    &mut ops,
                );
            } else if !defer_dh_composite_until_after_opaque {
                resources.append_vanilla_sample_state(
                    previous_vanilla_state,
                    previous_vanilla_state,
                    &mut ops,
                );
            }
            ops.append(&mut deferred_dh_composite_ops);
            self.pending_lod_direct_composition_written = true;
            self.pending_lod_vanilla_sample_state_established = true;
        }
        if !final_double_pass_dh_composite_ops.is_empty() {
            let resources = self
                .lod_direct_composition_resources
                .as_ref()
                .ok_or_else(|| {
                    GalError::backend(
                        "direct DH composition resources missing before final double-pass composite",
                    )
                })?;
            // The opaque boundary above left both snapshots shader-readable.
            // Refresh them after translucent terrain, then replay the same
            // private DH source exactly once at Frozen's second fade boundary.
            resources.append_vanilla_snapshot(
                frame_target,
                owned_color_texture,
                depth_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::ShaderRead,
                &mut ops,
            );
            ops.append(&mut final_double_pass_dh_composite_ops);
            self.pending_lod_direct_composition_written = true;
            self.pending_lod_vanilla_sample_state_established = true;
        }
        if !deferred_entity_layer_ops.is_empty() {
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.extend(deferred_entity_layer_ops);
            ops.push(CommandOp::EndPass);
        }
        if !receiver_shadow_ops.is_empty() {
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.extend(receiver_shadow_ops);
            ops.push(CommandOp::EndPass);
        }
        if !late_particle_ops.is_empty() {
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.extend(late_particle_ops);
            ops.push(CommandOp::EndPass);
        }
        if let Some(outline_plan) = entity_outline_plan.as_ref() {
            let outline_color_only_pass = self.color_only_frame_pass(gal, frame_target)?;
            let targets = self.entity_outline_targets.as_ref().ok_or_else(|| {
                GalError::backend("entity outline targets missing before mask lowering")
            })?;
            let mask_gpu = self.entity_outline_mask_gpu.as_ref().ok_or_else(|| {
                GalError::backend("entity outline mask resources missing before lowering")
            })?;
            let mask_depth_view = targets.mask_depth_view.ok_or_else(|| {
                GalError::backend("entity outline mask depth binding missing before lowering")
            })?;
            let depth_state = if use_g_buffer_mesh_path {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::DepthStencilAttachment
            };
            let prior_target_state = if self.entity_outline_targets_initialized {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            };
            ops.extend(features::outline::lower_entity_outline_mask_pass(
                targets,
                mask_gpu,
                mask_depth_view,
                depth_state,
                depth_state,
                prior_target_state,
            )?);
            let pipelines = self
                .entity_outline_post_effect_pipelines
                .as_ref()
                .ok_or_else(|| {
                    GalError::backend(
                        "entity outline post-effect pipelines missing before lowering",
                    )
                })?;
            let sets = self
                .entity_outline_post_effect_sets
                .as_ref()
                .ok_or_else(|| {
                    GalError::backend(
                        "entity outline post-effect resource sets missing before lowering",
                    )
                })?;
            ops.extend(features::outline::lower_entity_outline_post_effect_with_resources(
                outline_plan,
                targets,
                pipelines,
                sets,
                outline_color_only_pass,
                frame_target,
                color_attachment,
                pipelines.composite_depthless_pipeline,
                prior_target_state,
                prior_target_state,
                prior_target_state,
            )?);
            self.pending_entity_outline_targets_written = true;
        }
        if !frame.text_quads.is_empty() {
            // The complete Rust shader graph owns its own terrain depth. Text
            // is composed only after its final color pass, but ordinary
            // depth-aware glyphs must still test against that exact geometry
            // depth rather than the compatibility depth attachment used by
            // direct primitive-only frames.
            let (world_text_depth_texture, world_text_depth_view) = if use_g_buffer_mesh_path {
                let g_buffer = self.g_buffer_resources.as_ref().ok_or_else(|| {
                    GalError::backend("G-buffer resources missing before world text")
                })?;
                (g_buffer.depth_texture, g_buffer.depth_view)
            } else {
                // `ensure_depth_attachment` returns either the acquired
                // frame-target-owned depth image or the frontend's private
                // direct-pass image.  Use that explicit result here instead
                // of consulting the private slot, which is intentionally
                // empty when the frame target owns the depth resource.
                (depth_texture, depth_view)
            };
            let world_text_depth_before = if use_g_buffer_mesh_path {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::DepthStencilAttachment
            };
            let world_text_stats = self.world_text.append_frame_ops(
                gal,
                frame_target,
                pass,
                color_attachment,
                world_text_depth_texture,
                world_text_depth_view,
                world_text_depth_before,
                color_format,
                raster_y_direction,
                frame.view_matrix,
                frame.projection_matrix,
                &frame.text_quads,
                &mut ops,
                false,
            )?;
            stats.world_text_quad_count = world_text_stats.quad_count;
            stats.world_text_batch_count = world_text_stats.batch_count;
            stats.world_text_draw_count = world_text_stats.draw_count;
            stats.world_text_clip_xy_visible_quad_count =
                world_text_stats.clip_xy_visible_quad_count;
            stats.world_text_first_ndc_bounds = world_text_stats.first_ndc_bounds;
            stats.world_text_first_ndc_corners = world_text_stats.first_ndc_corners;
            stats.world_text_ndc_bounds_sample = world_text_stats.ndc_bounds_sample;
            if use_g_buffer_mesh_path {
                // The text frontend must use the shared terrain depth as an
                // attachment while drawing. Restore its sampled-image layout
                // before the remaining source graph stages bind that depth
                // descriptor again.
                ops.push(CommandOp::Barrier(texture_barrier(
                    world_text_depth_texture,
                    TextureUsageState::DepthStencilAttachment,
                    TextureUsageState::ShaderRead,
                )));
            }
        }
        let mut first_batch = false;
        if !border_batches.is_empty() {
            let resources = self
                .border_resources
                .get(&(color_format, raster_y_direction))
                .ok_or_else(|| {
                    GalError::backend("world border resources vanished before submit")
                })?;
            for batch in border_batches {
                let uniforms = packed_border_uniforms_for_batch(&frame, &batch)?;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    resources.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: resources.uniform_buffer,
                    offset: 0,
                    data: uniforms,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    resources.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                ops.push(CommandOp::BeginPass {
                    pass,
                    target: frame_target,
                    colors: vec![loaded_frame_color_attachment(color_attachment)],
                    depth_stencil: Some(PassAttachment {
                        view: depth_view,
                        load_op: if first_batch {
                            AttachmentLoadOp::Clear
                        } else {
                            AttachmentLoadOp::Load
                        },
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                });
                ops.push(CommandOp::BindGraphicsPipeline(
                    if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                        resources.pipeline_depth_test_write
                    } else {
                        resources.pipeline_depth_disabled
                    },
                ));
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: 0,
                    set: resources.resource_set,
                    dynamic_offsets: Vec::new(),
                });
                ops.push(CommandOp::Draw {
                    vertices: 6,
                    instances: batch.count as u32,
                });
                ops.push(CommandOp::EndPass);
                first_batch = false;
            }
        }
        if !crack_batches.is_empty() {
            let resources = self
                .crack_resources
                .get(&(color_format, raster_y_direction))
                .ok_or_else(|| GalError::backend("world crack resources vanished before submit"))?;
            for batch in crack_batches {
                let uniforms = packed_crack_uniforms_for_batch(&frame, &batch)?;
                ops.push(CommandOp::Barrier(buffer_barrier(
                    resources.uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )));
                ops.push(CommandOp::HostWriteBuffer {
                    buffer: resources.uniform_buffer,
                    offset: 0,
                    data: uniforms,
                });
                ops.push(CommandOp::Barrier(buffer_barrier(
                    resources.uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )));
                ops.push(CommandOp::BeginPass {
                    pass,
                    target: frame_target,
                    colors: vec![loaded_frame_color_attachment(color_attachment)],
                    depth_stencil: Some(PassAttachment {
                        view: depth_view,
                        load_op: if first_batch {
                            AttachmentLoadOp::Clear
                        } else {
                            AttachmentLoadOp::Load
                        },
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }),
                });
                ops.push(CommandOp::BindGraphicsPipeline(
                    if batch.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE {
                        resources.pipeline_depth_test_write
                    } else {
                        resources.pipeline_depth_disabled
                    },
                ));
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout: resources.pipeline_layout,
                    set_index: 0,
                    set: resources.resource_set,
                    dynamic_offsets: Vec::new(),
                });
                ops.push(CommandOp::Draw {
                    vertices: 6,
                    instances: batch.count as u32,
                });
                ops.push(CommandOp::EndPass);
                first_batch = false;
            }
        }
        let resources = self
            .resources
            .get(&(color_format, raster_y_direction))
            .filter(|_| !batches.is_empty());
        for batch in batches {
            let resources = resources.ok_or_else(|| {
                GalError::backend("world primitive resources vanished before submit")
            })?;
            let uniforms = packed_line_uniforms_for_batch(&frame, &batch)?;
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::HostWriteBuffer {
                buffer: resources.uniform_buffer,
                offset: 0,
                data: uniforms,
            });
            ops.push(CommandOp::Barrier(buffer_barrier(
                resources.uniform_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::BeginPass {
                pass,
                target: frame_target,
                colors: vec![loaded_frame_color_attachment(color_attachment)],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: if first_batch {
                        AttachmentLoadOp::Clear
                    } else {
                        AttachmentLoadOp::Load
                    },
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.push(CommandOp::BindGraphicsPipeline(match batch.depth_policy {
                WORLD_DEPTH_POLICY_TEST_WRITE => resources.pipeline_depth_test_write,
                WORLD_DEPTH_POLICY_TEST_NO_WRITE => resources.pipeline_depth_test_no_write,
                _ => resources.pipeline_depth_disabled,
            }));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: resources.pipeline_layout,
                set_index: 0,
                set: resources.resource_set,
                dynamic_offsets: Vec::new(),
            });
            ops.push(CommandOp::Draw {
                vertices: (batch.count * 6) as u32,
                instances: 1,
            });
            ops.push(CommandOp::EndPass);
            first_batch = false;
        }
        stats.world_draws = stats.primitive_batch_count;
        stats.crack_draw_count = stats.crack_batch_count;
        stats.border_draw_count = stats.border_batch_count;
        stats.material_draw_count = stats.material_batch_count;
        stats.mesh_draw_count = mesh_batches.len() as u64;
        stats.world_draws = stats
            .world_draws
            .saturating_add(stats.crack_draw_count)
            .saturating_add(stats.border_draw_count)
            .saturating_add(stats.material_draw_count)
            .saturating_add(stats.mesh_draw_count);

        if let Some((hand, hand_instances)) = builtin_first_person {
            let (hand_ops, hand_stats) = self.append_builtin_first_person_ops(
                gal,
                generation,
                frame_target,
                &frame,
                hand,
                hand_instances,
                raster_y_direction,
            )?;
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
        stats.command_ops = ops.len() as u64;
        // Every ordinary world graph pass binds the Rust-owned frame depth
        // attachment and stores it.  Admit it for subsequent GUI/post-effect
        // sampling only after the complete command list has been built.
        if frame_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            gal.begin_frame_target_depth_write(frame_target)?;
        }
        stats.profile.gal.gal_command_generation_nanos = stats
            .profile
            .gal.gal_command_generation_nanos
            .saturating_add(elapsed_nanos_u64(command_generation_started));
        Ok((ops, stats))
    }

    /// Lowers the builtin vanilla hand stream as an explicit second depth
    /// domain.  The caller chooses where these operations occur in the owning
    /// submission; terrain Fabulous frames append them after the final
    /// transparency blit so the blit cannot overwrite the hand.
    pub(crate) fn append_builtin_first_person_ops(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        frame_target: Handle,
        parent_frame: &WorldPrimitiveFrame,
        hand: WorldFirstPersonFrame,
        hand_instances: Vec<WorldMeshInstanceRequest>,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<(Vec<CommandOp>, WorldPrimitiveSubmitStats)> {
        if !hand.enabled || hand_instances.is_empty() {
            return Ok((Vec::new(), WorldPrimitiveSubmitStats::default()));
        }
        if crate::core::environment::var_os("MATTMC_STANDARD_FOIL_TRACE").is_some() {
            static TRACES: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
            if TRACES.fetch_add(1, std::sync::atomic::Ordering::Relaxed) < 4 {
                for instance in &hand_instances {
                    crate::core::console::stderr(format_args!("standard-foil.hand-input frame={} mesh={} foil={:?} model={:?} view={:?} projection={:?}",
                        parent_frame.frame_id, instance.mesh_key, instance.item_foil, instance.transform,
                        hand.model_view_matrix, hand.projection_matrix));
                }
            }
        }
        let mut hand_frame = parent_frame.clone();
        hand_frame.frame_id = parent_frame.frame_id;
        hand_frame.view_matrix = hand.model_view_matrix;
        hand_frame.projection_matrix = hand.projection_matrix;
        hand_frame.feature_coverage = WorldFeatureCoverageFrame::default();
        hand_frame.first_person = WorldFirstPersonFrame::default();
        hand_frame.first_person_mesh_instances.clear();
        hand_frame.mesh_instances = hand_instances;
        hand_frame.background = WorldBackgroundRequest::default();
        hand_frame.background.load_intent = WORLD_BACKGROUND_LOAD_CLEAR;
        hand_frame.segments.clear();
        hand_frame.crack_quads.clear();
        hand_frame.border_quads.clear();
        hand_frame.material_quads.clear();
        hand_frame.text_quads.clear();
        hand_frame.lod_instances.clear();
        hand_frame.dh_generic_boxes.clear();
        hand_frame.lod_render_frame = WorldLodRenderFrame::default();
        self.append_frame_ops_inner(
            gal,
            generation,
            frame_target,
            hand_frame,
            false,
            raster_y_direction,
        )
    }
}

//! Recording mesh items into the GUI frame.

use super::*;

impl GuiFrontend {
    /// Appends one complete Rust-owned standard-3D GUI-item family. The caller
    /// supplies copied semantic mesh batches only; source images are resolved
    /// from the existing Rust GUI asset generation, each item is rasterized
    /// into an owned PIP target, and its result is composed through the normal
    /// GUI target. This remains private until the frame ABI and route select it.
    pub(crate) fn append_mesh_items_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        mut world: Option<&mut (dyn GuiAtlasOwner + 'static)>,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        stats: &mut GuiSubmitStats,
    ) -> GalResult<Vec<CommandOp>> {
        let prepare_started = std::time::Instant::now();
        self.preflight_mesh_atlas_commands(world.as_deref(), &mesh_batches)?;
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        // Static item rasters are already retained by the Rust-owned target
        // cache. Once a prior submission has accepted that raster, composing
        // it does not require transforming every source vertex again. Keep
        // validation and composition metadata, but skip the frame-local mesh
        // reconstruction for those item groups.
        let mut reusable_items = BTreeSet::new();
        let mut inspected_items = BTreeSet::new();
        for batch in &mesh_batches {
            let item_key = (batch.stratum, batch.sequence);
            if !inspected_items.insert(item_key) {
                continue;
            }
            let Some(cache) = batch.item_cache else {
                continue;
            };
            if cache.animated {
                continue;
            }
            let (extent, _, _) = resolve_gui_mesh_item_raster(batch)?;
            let target_extent = Extent3d {
                width: extent[0],
                height: extent[1],
                depth: 1,
            };
            let Some(target) =
                self.mesh_targets
                    .peek_item(generation, target_extent, cache.identity)
            else {
                continue;
            };
            if gal.render_pass_last_submission(target.pass)?.is_some() {
                reusable_items.insert(item_key);
            }
        }
        let mut prepared = if reusable_items.is_empty() {
            prepare_gui_mesh_draws(&mesh_batches)?
        } else {
            prepare_gui_mesh_draws_with_reuse(&mesh_batches, &reusable_items)?
        };
        // Resolve exact owner incarnations before allocating any mesh resources.
        // The geometry keeps original sprite-local UVs until this native boundary.
        for (batch, draw) in mesh_batches.iter().zip(&mut prepared) {
            if !self.atlas_references.contains(draw.asset_id) {
                continue;
            }
            if !mesh_atlas_contract_supported(batch) {
                return Err(GalError::unsupported_feature("GUI mesh atlas sampling requires an explicit inventory base or front-model overlay layer"));
            }
            let owner = world.as_deref().ok_or_else(|| {
                GalError::invalid_argument(
                    "GUI mesh atlas sampling requires its explicit Rust image owner",
                )
            })?;
            let reference = self
                .atlas_references
                .resolve(draw.asset_id, |id| owner.accepted_gui_atlas_incarnation(id))?;
            for vertex in &mut draw.vertices {
                vertex.local_uv = reference.atlas_uv(vertex.local_uv)?;
            }
        }
        prepared.sort_by_key(|draw| (draw.stratum, draw.sequence, draw.layer_index));
        stats.mesh_prepare_nanos = stats
            .mesh_prepare_nanos
            .saturating_add(crate::render::vulkanic::metrics::elapsed_nanos_u64(prepare_started));
        if prepared.is_empty() {
            return Ok(Vec::new());
        }
        let lower_started = std::time::Instant::now();
        let frame_pass = match render_pass {
            Some(pass) => pass,
            None => self.frame_pass(gal, render_target, depth_format)?,
        };
        let color_format = gal.pass_target_color_format(render_target)?;
        let mut operations =
            Vec::with_capacity(prepared.len().saturating_mul(24).saturating_add(32));
        let mut pending_composites = Vec::with_capacity(prepared.len());
        let mut cursor = 0;
        while cursor < prepared.len() {
            let first = &prepared[cursor];
            let group_key = (first.stratum, first.sequence);
            let group_end = prepared[cursor..]
                .iter()
                .position(|draw| (draw.stratum, draw.sequence) != group_key)
                .map(|offset| cursor + offset)
                .unwrap_or(prepared.len());
            let item_layers = &prepared[cursor..group_end];
            validate_mesh_item_layers(item_layers)?;
            let entity_preview = item_layers
                .iter()
                .all(|draw| draw.lighting_mode.is_entity_material_lighting());
            if item_layers
                .iter()
                .any(|draw| draw.lighting_mode.is_entity_material_lighting())
                && !entity_preview
            {
                return Err(GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI entity-preview item layers must all use entity-preview lighting",
                ));
            }
            stats.mesh_item_count = stats.mesh_item_count.saturating_add(1);
            if entity_preview {
                stats.entity_preview_item_count = stats.entity_preview_item_count.saturating_add(1);
            }
            // Frozen submits the title panorama straight to its main frame
            // target. Keep that native-resolution semantic pass distinct from
            // standard-3D item PIP work, whose private target is part of its
            // actual rendering contract.
            if first.material_mode == GuiMeshMaterialMode::Panorama {
                if item_layers.len() != 1 {
                    return Err(GalError::ffi(
                        StatusCode::InvalidArgument,
                        "a semantic panorama must contain exactly one fullscreen layer",
                    ));
                }
                let texture_group = dynamic_mesh_texture_group(first);
                self.ensure_resources(gal, texture_group, color_format, depth_format, stats)?;
                let (texture_view, sampler) = {
                    let raw_resources = self
                        .resources
                        .get(&ResourceKey::new(texture_group, color_format, depth_format))
                        .ok_or_else(|| {
                            GalError::backend("GUI panorama image resources vanished before raster")
                        })?;
                    (raw_resources.texture_view, raw_resources.sampler)
                };
                let raster_key = direct_gui_mesh_raster_key(first, color_format);
                if !self.mesh_rasters.contains_key(&raster_key) {
                    if self.mesh_rasters.len() >= GUI_MAX_MESH_RASTER_RESOURCES {
                        return Err(GalError::unsupported_feature(format!(
                            "GUI mesh raster resource cache exceeds bounded limit {}",
                            GUI_MAX_MESH_RASTER_RESOURCES
                        )));
                    }
                    let shared_program = self.ensure_mesh_shared_program(
                        gal,
                        color_format,
                        depth_format,
                        first.material_mode,
                        first.front_face,
                    )?;
                    let raster = GuiMeshPassResources::create_with_shared_program(
                        gal,
                        &format!(
                            "minecraft.gui.panorama.asset{}.gen{}",
                            first.asset_id, generation
                        ),
                        texture_view,
                        sampler,
                        shared_program,
                    )?;
                    self.mesh_rasters.insert(raster_key, raster);
                    stats.resource_creates = stats.resource_creates.saturating_add(1);
                }
                let geometry_key = (
                    raster_key,
                    gui_mesh_geometry_fingerprint(first),
                    self.mesh_geometry_transaction,
                );
                let vertex_bytes = (first.vertices.len()
                    * crate::render::guirender::mesh::GUI_MESH_GPU_VERTEX_BYTES)
                    as u64;
                let index_bytes = (first.indices.len() * std::mem::size_of::<u32>()) as u64;
                let (stream, reused) = if let Some(residency) =
                    self.mesh_geometry_cache.get_mut(&geometry_key)
                {
                    operations.push(CommandOp::TrackSubmission(residency.usage.clone()));
                    (residency.stream, true)
                } else {
                    let residency =
                        self.allocate_mesh_geometry(gal, raster_key, vertex_bytes, index_bytes)?;
                    let stream = residency.stream;
                    operations.push(CommandOp::TrackSubmission(residency.usage.clone()));
                    self.mesh_geometry_cache.insert(geometry_key, residency);
                    (stream, false)
                };
                self.mesh_rasters
                    .get(&raster_key)
                    .ok_or_else(|| {
                        GalError::backend("GUI panorama raster resources vanished before draw")
                    })?
                    .append_direct_frame_draw(
                        frame_pass,
                        render_target,
                        color_attachment,
                        depth_attachment,
                        first,
                        stream,
                        !reused,
                        &mut operations,
                    )?;
                stats.mesh_batch_count = stats.mesh_batch_count.saturating_add(1);
                stats.mesh_draw_count = stats.mesh_draw_count.saturating_add(1);
                cursor = group_end;
                continue;
            }
            let item_identity = first.item_cache.map_or(0, |cache| cache.identity);
            let mut target = self.mesh_targets.stage_item(
                gal,
                generation,
                Extent3d {
                    width: first.render_extent[0],
                    height: first.render_extent[1],
                    depth: 1,
                },
                item_identity,
            )?;
            // A cached offscreen target cannot be rasterized again while its
            // previous pixels are still in COLOR_ATTACHMENT state. Flush all
            // pending composites before reusing that target; unique targets
            // remain batched into one final-target pass.
            if pending_composites
                .iter()
                .any(|composite: &PendingGuiMeshComposite<'_>| {
                    composite.source.target == target.target
                })
            {
                self.append_mesh_composite_batch(
                    frame_pass,
                    render_target,
                    color_attachment,
                    depth_attachment,
                    &pending_composites,
                    &mut operations,
                )?;
                pending_composites.clear();
            }
            let accepted_raster = gal.render_pass_last_submission(target.pass)?;
            // Preparation may be discarded. Persisted layout state is proven
            // by this pass's accepted submission, or by an earlier item in
            // the same command transaction, never by a previous preparation.
            target.initialized = accepted_raster.is_some()
                || stats.owned_intermediate_targets.contains(&target.target);
            let reuse_pixels =
                first.item_cache.is_some_and(|cache| !cache.animated) && accepted_raster.is_some();
            if !stats.owned_intermediate_targets.contains(&target.target) {
                stats.owned_intermediate_targets.push(target.target);
            }
            for (execution_index, layer_index) in mesh_item_layer_execution_order(item_layers)
                .into_iter()
                .enumerate()
                .filter(|_| !reuse_pixels)
            {
                let draw = &item_layers[layer_index];
                let texture_group = dynamic_mesh_texture_group(draw);
                if self.atlas_references.contains(draw.asset_id) {
                    self.prepare_owned_atlas_binding_group(
                        gal,
                        world.as_deref_mut().ok_or_else(|| {
                            GalError::invalid_argument("GUI mesh atlas owner unavailable")
                        })?,
                        texture_group,
                        color_format,
                        depth_format,
                    )?;
                }
                self.ensure_resources(gal, texture_group, color_format, depth_format, stats)?;
                let (texture_view, sampler) = {
                    let raw_resources = self
                        .resources
                        .get(&ResourceKey::new(texture_group, color_format, depth_format))
                        .ok_or_else(|| {
                            GalError::backend("GUI mesh image resources vanished before raster")
                        })?;
                    (raw_resources.texture_view, raw_resources.sampler)
                };
                let raster_key = gui_mesh_raster_key(draw);
                if !self.mesh_rasters.contains_key(&raster_key) {
                    if self.mesh_rasters.len() >= GUI_MAX_MESH_RASTER_RESOURCES {
                        return Err(GalError::unsupported_feature(format!(
                            "GUI mesh raster resource cache exceeds bounded limit {}",
                            GUI_MAX_MESH_RASTER_RESOURCES
                        )));
                    }
                    let shared_program = self.ensure_mesh_shared_program(
                        gal,
                        TextureFormat::Rgba8Unorm,
                        Some(TextureFormat::Depth32Float),
                        draw.material_mode,
                        draw.front_face,
                    )?;
                    let raster = GuiMeshPassResources::create_with_shared_program(
                        gal,
                        &format!(
                            "minecraft.gui.mesh.asset{}.gen{}",
                            draw.asset_id, generation
                        ),
                        texture_view,
                        sampler,
                        shared_program,
                    )?;
                    self.mesh_rasters.insert(raster_key, raster);
                    stats.resource_creates = stats.resource_creates.saturating_add(1);
                }
                let geometry_key = (
                    raster_key,
                    gui_mesh_geometry_fingerprint(draw),
                    self.mesh_geometry_transaction,
                );
                let vertex_bytes = (draw.vertices.len()
                    * crate::render::guirender::mesh::GUI_MESH_GPU_VERTEX_BYTES)
                    as u64;
                let index_bytes = (draw.indices.len() * std::mem::size_of::<u32>()) as u64;
                let (stream, reused) = if let Some(residency) =
                    self.mesh_geometry_cache.get_mut(&geometry_key)
                {
                    operations.push(CommandOp::TrackSubmission(residency.usage.clone()));
                    (residency.stream, true)
                } else {
                    let residency =
                        self.allocate_mesh_geometry(gal, raster_key, vertex_bytes, index_bytes)?;
                    let stream = residency.stream;
                    operations.push(CommandOp::TrackSubmission(residency.usage.clone()));
                    self.mesh_geometry_cache.insert(geometry_key, residency);
                    (stream, false)
                };
                let raster = self.mesh_rasters.get(&raster_key).ok_or_else(|| {
                    GalError::backend("GUI mesh raster resources vanished before draw")
                })?;
                if reused {
                    raster.append_draw_reusing_geometry(
                        target,
                        draw,
                        stream,
                        execution_index == 0,
                        &mut operations,
                    )?;
                } else {
                    raster.append_draw(
                        target,
                        draw,
                        stream,
                        execution_index == 0,
                        &mut operations,
                    )?;
                }
                target.initialized = true;
                stats.mesh_batch_count = stats.mesh_batch_count.saturating_add(1);
                stats.mesh_draw_count = stats.mesh_draw_count.saturating_add(1);
                if entity_preview {
                    stats.entity_preview_batch_count =
                        stats.entity_preview_batch_count.saturating_add(1);
                    stats.entity_preview_draw_count =
                        stats.entity_preview_draw_count.saturating_add(1);
                    stats.entity_preview_material_mask |=
                        gui_mesh_material_semantic_bit(draw.material_mode);
                    stats.entity_preview_vertex_count = stats
                        .entity_preview_vertex_count
                        .saturating_add(draw.vertices.len() as u64);
                    stats.entity_preview_index_count = stats
                        .entity_preview_index_count
                        .saturating_add(draw.indices.len() as u64);
                }
            }
            let composite_key = GuiMeshCompositeKey {
                item_identity,
                width: target.extent.width,
                height: target.extent.height,
                color_format,
                depth_format,
            };
            if !self.mesh_composites.contains_key(&composite_key) {
                if self.mesh_composites.len() >= GUI_MAX_MESH_COMPOSITE_RESOURCES {
                    return Err(GalError::unsupported_feature(format!(
                        "GUI mesh composite resource cache exceeds bounded limit {}",
                        GUI_MAX_MESH_COMPOSITE_RESOURCES
                    )));
                }
                let label = format!(
                    "minecraft.gui.mesh.composite.gen{}.{}x{}",
                    generation, target.extent.width, target.extent.height
                );
                let shared = self.mesh_composites.iter().find_map(|(key, resources)| {
                    (key.color_format == color_format && key.depth_format == depth_format)
                        .then_some(*resources)
                });
                let composite = if let Some(shared) = shared {
                    GuiMeshCompositeResources::create_binding(
                        gal,
                        &label,
                        target.color_view,
                        shared,
                    )?
                } else {
                    GuiMeshCompositeResources::create(
                        gal,
                        &label,
                        color_format,
                        depth_format,
                        target.color_view,
                    )?
                };
                self.mesh_composites.insert(composite_key, composite);
                stats.resource_creates = stats.resource_creates.saturating_add(1);
            }
            self.mesh_composites
                .get(&composite_key)
                .ok_or_else(|| GalError::backend("GUI mesh compositor vanished before draw"))?;
            pending_composites.push(PendingGuiMeshComposite {
                key: composite_key,
                source: target,
                source_usage: if reuse_pixels {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::ColorAttachment
                },
                draw: first,
                uniform_offset: self.mesh_composite_uniform_cursor,
            });
            self.mesh_composite_uniform_cursor = self
                .mesh_composite_uniform_cursor
                .checked_add(GUI_MESH_COMPOSITE_UNIFORM_STRIDE)
                .ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        "GUI mesh composite uniform stream cursor overflows",
                    )
                })?;
            stats.mesh_draw_count = stats.mesh_draw_count.saturating_add(1);
            if entity_preview {
                stats.entity_preview_draw_count = stats.entity_preview_draw_count.saturating_add(1);
            }
            cursor = group_end;
        }
        self.append_mesh_composite_batch(
            frame_pass,
            render_target,
            color_attachment,
            depth_attachment,
            &pending_composites,
            &mut operations,
        )?;
        stats.command_ops = stats.command_ops.saturating_add(operations.len() as u64);
        stats.mesh_lower_nanos = stats
            .mesh_lower_nanos
            .saturating_add(crate::render::vulkanic::metrics::elapsed_nanos_u64(lower_started));
        Ok(operations)
    }
}

fn gui_mesh_material_semantic_bit(mode: GuiMeshMaterialMode) -> u64 {
    let semantic_mode = match mode {
        GuiMeshMaterialMode::Opaque => 1,
        GuiMeshMaterialMode::Cutout => 2,
        GuiMeshMaterialMode::Translucent => 3,
        GuiMeshMaterialMode::Glint => 4,
        GuiMeshMaterialMode::Panorama => 5,
        GuiMeshMaterialMode::ModelOverlay => 6,
        GuiMeshMaterialMode::EntityCutoutNoCull => 7,
        GuiMeshMaterialMode::EntityTranslucentNoCull => 8,
        GuiMeshMaterialMode::EntityDecalCutoutNoCull => 9,
    };
    1_u64 << semantic_mode
}

//! Recording ordered GUI requests into an explicit render target, with or without owned atlases.

use super::*;

impl GuiFrontend {
    /// Records GUI work against an explicit GAL render target and its color
    /// attachment. The ordinary whole-frame route uses the acquired target for
    /// both values; source-owned shader frames provide their overlay target
    /// and view so GUI joins the final Rust composition before presentation.
    pub fn append_frame_ops_with_affine_quads_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        let needs_depth = affine_quads
            .iter()
            .any(|request| request.stratum == GUI_LEQUAL_DEPTH_BLIT_STRATUM);
        let infer_depth = needs_depth && depth_attachment.is_none();
        let (depth_attachment, depth_format) = if needs_depth && depth_attachment.is_none() {
            let Some((_, view)) = gal.pass_target_depth_attachment(render_target)? else {
                return Err(GalError::backend(
                    "depth-tested GUI semantic blit requires the Rust-owned frame depth attachment",
                ));
            };
            (Some(view), Some(TextureFormat::Depth32Float))
        } else {
            (depth_attachment, depth_format)
        };
        let render_pass = if infer_depth { None } else { render_pass };
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        if depth_attachment.is_some() != depth_format.is_some() {
            return Err(GalError::command(
                StatusCode::InvalidArgument,
                "GUI depth attachment and depth format must be supplied together",
            ));
        }
        if render_target.kind() == Some(crate::render::vulkanic::handles::HandleKind::FrameTarget) {
            if let Some(depth_attachment) = depth_attachment {
                let (_, owned_view) = gal.frame_target_owned_depth_attachment(render_target)?;
                if depth_attachment != owned_view {
                    return Err(GalError::command(
                        StatusCode::InvalidArgument,
                        "frame-target GUI depth attachment must be the Rust-owned frame depth view",
                    ));
                }
            }
        }
        let color_format = gal.pass_target_color_format(render_target)?;
        let frame_pass = match render_pass {
            Some(pass) => pass,
            None => self.frame_pass(gal, render_target, depth_format)?,
        };
        let mut batches: Vec<GuiBatch> = Vec::new();
        let mut stats = GuiSubmitStats {
            sprite_count: requests.len() as u64,
            affine_quad_count: affine_quads.len() as u64,
            ..GuiSubmitStats::default()
        };
        let ordered = order_gui_requests(requests, affine_quads)?;
        for request in ordered {
            match request {
                GuiFrameRequest::Sprite(request) => {
                    let def = sprite_def(request.sprite_id)?;
                    if request.stratum != def.stratum {
                        return Err(GalError::ffi(
                            StatusCode::InvalidArgument,
                            format!(
                                "GUI sprite '{}' requested stratum {} but registry stratum is {}",
                                def.name, request.stratum, def.stratum
                            ),
                        ));
                    }
                    validate_request(&request, def)?;
                    let group = def.group;
                    self.ensure_resources(gal, group, color_format, depth_format, &mut stats)?;
                    let quad = self.pack_sprite(&request, def, pre_present_y_flip)?;
                    append_gui_quad(&mut batches, request.stratum, group, quad);
                }
                GuiFrameRequest::AffineBatch(requests) => {
                    for request in requests {
                        let image_format = self
                            .raw_images
                            .get(&request.asset_id)
                            .ok_or_else(|| {
                                GalError::ffi(
                                    StatusCode::InvalidArgument,
                                    format!("unknown raw GUI image asset {}", request.asset_id),
                                )
                            })?
                            .format;
                        validate_affine_quad(&request)?;
                        let group = dynamic_texture_group(request.stratum, request.asset_id);
                        self.ensure_resources(gal, group, color_format, depth_format, &mut stats)?;
                        let quad = PackedGuiQuad {
                            origin: [request.x0, request.y0],
                            axis_u: [request.x1 - request.x0, request.y1 - request.y0],
                            axis_v: [request.x3 - request.x0, request.y3 - request.y0],
                            viewport: request.projection_extent,
                            clip: [
                                request.clip_left as f32,
                                request.clip_top as f32,
                                (request.clip_left + request.clip_width) as f32,
                                (request.clip_top + request.clip_height) as f32,
                            ],
                            clip_enabled: request.clip_mode == 1,
                            pre_present_y_flip,
                            uv: [
                                request.u0,
                                request.v0,
                                request.u1 - request.u0,
                                request.v1 - request.v0,
                            ],
                            color: request.material.color(argb_to_rgba(request.color_argb))?,
                            texture_mode: image_format.shader_mode(),
                            z: request.z,
                        };
                        append_gui_quad(&mut batches, request.stratum, group, quad);
                    }
                }
                GuiFrameRequest::Affine(_) => unreachable!("ordered affine requests are coalesced"),
                GuiFrameRequest::Mesh(_) => {
                    return Err(GalError::backend(
                        "mesh GUI request reached the sprite/affine-only frame builder",
                    ));
                }
            }
        }
        stats.sprite_batch_count = batches.len() as u64;
        let mut ops = Vec::new();
        // An empty loaded pass opens the frame target before any batch (or
        // when there are none) on every backend.
        ops.push(CommandOp::BeginPass {
            pass: frame_pass,
            target: render_target,
            colors: vec![loaded_frame_color_attachment(color_attachment)],
            depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
        });
        ops.push(CommandOp::EndPass);
        append_gui_batches_ops(
            self,
            frame_pass,
            render_target,
            color_attachment,
            depth_attachment,
            color_format,
            depth_format,
            &batches,
            &mut ops,
        )?;
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        Ok((ops, stats))
    }

    /// Same mixed scheduler and target, with explicit access to the image owner.
    pub(crate) fn append_frame_ops_with_owned_atlases_to_target(
        &mut self,
        gal: &mut VulkanicGal,
        mut world: Option<&mut (dyn GuiAtlasOwner + 'static)>,
        generation: u64,
        render_target: Handle,
        color_attachment: Handle,
        render_pass: Option<Handle>,
        depth_attachment: Option<Handle>,
        depth_format: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: Vec<GuiSpriteRequest>,
        affine_quads: Vec<GuiAffineQuadRequest>,
        mesh_batches: Vec<GuiMeshBatchRequest>,
        tiled_quads: Vec<GuiTiledQuadRequest>,
    ) -> GalResult<(Vec<CommandOp>, GuiSubmitStats)> {
        let frontend_started = std::time::Instant::now();
        preflight_tiled_affine_count(&tiled_quads, affine_quads.len())?;
        let has_atlases =
            self.preflight_owned_atlas_commands(world.as_deref(), &affine_quads, &tiled_quads)?;
        self.preflight_mesh_atlas_commands(world.as_deref(), &mesh_batches)?;
        let needs_depth = affine_quads
            .iter()
            .any(|request| request.stratum == GUI_LEQUAL_DEPTH_BLIT_STRATUM)
            || tiled_quads
                .iter()
                .any(|request| request.stratum == GUI_LEQUAL_DEPTH_BLIT_STRATUM);
        let infer_depth = needs_depth && depth_attachment.is_none();
        let (depth_attachment, depth_format) = if needs_depth && depth_attachment.is_none() {
            let Some((_, view)) = gal.pass_target_depth_attachment(render_target)? else {
                return Err(GalError::backend(
                    "depth-tested GUI semantic blit requires the Rust-owned frame depth attachment",
                ));
            };
            (Some(view), Some(TextureFormat::Depth32Float))
        } else {
            (depth_attachment, depth_format)
        };
        let render_pass = if infer_depth { None } else { render_pass };
        if mesh_batches.is_empty() && tiled_quads.is_empty() && !has_atlases {
            let (ops, mut stats) = self.append_frame_ops_with_affine_quads_to_target(
                gal,
                generation,
                render_target,
                color_attachment,
                render_pass,
                depth_attachment,
                depth_format,
                pre_present_y_flip,
                requests,
                affine_quads,
            )?;
            stats.frontend_nanos = crate::render::vulkanic::metrics::elapsed_nanos_u64(frontend_started);
            return Ok((ops, stats));
        }
        let ordered =
            order_gui_requests_with_tiles(requests, affine_quads, mesh_batches, tiled_quads)?;
        self.mesh_geometry_transaction = self
            .mesh_geometry_transaction
            .checked_add(1)
            .ok_or_else(|| GalError::invalid_argument("GUI mesh transaction identity exhausted"))?;
        if generation != self.generation {
            self.destroy_render_resources(gal);
            self.generation = generation;
        }
        // The backend reads mesh streams asynchronously. Reclaim only ranges
        // whose submission has completed, so animated semantic meshes cannot
        // exhaust permanent residency or overwrite in-flight vertices.
        self.reclaim_completed_mesh_geometry(gal.poll_completed());
        // Eviction uses the complete ordered frame. Recording one mesh item
        // at a time must not evict the other items needed later in that frame.
        let mut needed_targets = Vec::new();
        for entry in &ordered {
            if let GuiFrameRequest::Mesh(item) = entry {
                for batch in &item.layers {
                    let (extent, _, _) = resolve_gui_mesh_item_raster(batch)?;
                    needed_targets.push((
                        batch.item_cache.map_or(0, |cache| cache.identity),
                        Extent3d { width: extent[0], height: extent[1], depth: 1 },
                    ));
                }
            }
        }
        if self.mesh_targets.has_obsolete_items(generation, &needed_targets) {
            // Composite programs may be shared: release borrowers before owners.
            let mut composites: Vec<_> = std::mem::take(&mut self.mesh_composites)
                .into_values().collect();
            composites.sort_by_key(|resources| resources.owns_shared_resources());
            for resources in composites { resources.destroy(gal); }
            self.mesh_targets.retain_items(gal, generation, &needed_targets);
        }
        let color_format = gal.pass_target_color_format(render_target)?;
        let frame_pass = match render_pass {
            Some(pass) => pass,
            None => self.frame_pass(gal, render_target, depth_format)?,
        };
        self.mesh_composite_uniform_cursor = 0;
        let mut stats = GuiSubmitStats::default();
        let mut ops = Vec::new();
        ops.push(CommandOp::BeginPass {
            pass: frame_pass,
            target: render_target,
            colors: vec![loaded_frame_color_attachment(color_attachment)],
            depth_stencil: depth_attachment.map(loaded_frame_depth_attachment),
        });
        ops.push(CommandOp::EndPass);
        let item_raster_placements = if ordered.iter().any(|entry| matches!(entry,
            GuiFrameRequest::AffineBatch(batch) if batch.iter().any(|request| request.item_raster_scale != 0))) {
            let creates = gal.metrics().resource_creates;
            let owner = world.as_deref_mut().ok_or_else(|| GalError::invalid_argument("item raster owner missing"))?;
            let placements = self.prepare_full_item_rasters(gal,owner,&ordered,color_format,depth_format,&mut stats,&mut ops)?;
            stats.resource_creates += gal.metrics().resource_creates-creates;
            placements
        } else {BTreeMap::new()};
        let mut pending_gui_batches = Vec::new();
        for request in ordered {
            match request {
                GuiFrameRequest::Sprite(request) => {
                    let def = sprite_def(request.sprite_id)?;
                    if request.stratum != def.stratum {
                        return Err(GalError::ffi(
                            StatusCode::InvalidArgument,
                            format!(
                                "GUI sprite '{}' requested stratum {} but registry stratum is {}",
                                def.name, request.stratum, def.stratum
                            ),
                        ));
                    }
                    validate_request(&request, def)?;
                    self.ensure_resources(gal, def.group, color_format, depth_format, &mut stats)?;
                    append_gui_quad(
                        &mut pending_gui_batches,
                        request.stratum,
                        def.group,
                        self.pack_sprite(&request, def, pre_present_y_flip)?,
                    );
                    stats.sprite_count = stats.sprite_count.saturating_add(1);
                }
                GuiFrameRequest::AffineBatch(requests) => {
                    let first = requests.first().ok_or_else(|| {
                        GalError::ffi(StatusCode::InvalidArgument, "empty GUI affine batch")
                    })?;
                    if first.item_raster_scale != 0 {
                        append_gui_batches_ops(
                            self,
                            frame_pass,
                            render_target,
                            color_attachment,
                            depth_attachment,
                            color_format,
                            depth_format,
                            &pending_gui_batches,
                            &mut ops,
                        )?;
                        stats.sprite_batch_count += pending_gui_batches.len() as u64;
                        pending_gui_batches.clear();
                        for request in &requests {
                            let (key, placement, offset) = item_raster_placements
                                .get(&request.sequence)
                                .ok_or_else(|| {
                                    GalError::invalid_argument("item raster command not prepared")
                                })?;
                            let resource = self
                                .item_rasters
                                .get_mut(key)
                                .expect("prepared raster resource");
                            resource.composite.append_item_raster_composite(
                                resource.target.color,
                                resource.usage,
                                *placement,
                                frame_pass,
                                render_target,
                                color_attachment,
                                depth_attachment,
                                request,
                                pre_present_y_flip,
                                *offset,
                                &mut ops,
                            )?;
                            resource.usage = TextureUsageState::ShaderRead;
                        }
                        stats.affine_quad_count += requests.len() as u64;
                        stats.sprite_batch_count += requests.len() as u64;
                        continue;
                    }
                    if self.atlas_references.contains(first.asset_id) {
                        append_gui_batches_ops(
                            self,
                            frame_pass,
                            render_target,
                            color_attachment,
                            depth_attachment,
                            color_format,
                            depth_format,
                            &pending_gui_batches,
                            &mut ops,
                        )?;
                        stats.sprite_batch_count += pending_gui_batches.len() as u64;
                        pending_gui_batches.clear();
                        let owner = world.as_deref_mut().ok_or_else(|| {
                            GalError::unsupported_feature(
                                "GUI atlas commands require explicit world owner access",
                            )
                        })?;
                        let cached = self.resources.contains_key(&ResourceKey::new(
                            dynamic_texture_group(first.stratum, first.asset_id),
                            color_format,
                            depth_format,
                        ));
                        let creates_before = gal.metrics().resource_creates;
                        let atlas_ops = self.append_scheduled_owned_atlas_quads(
                            gal,
                            owner,
                            frame_pass,
                            render_target,
                            color_attachment,
                            depth_attachment,
                            color_format,
                            depth_format,
                            pre_present_y_flip,
                            &requests,
                            false,
                        )?;
                        stats.resource_creates += gal.metrics().resource_creates - creates_before;
                        if cached {
                            stats.cache_hits += 1;
                        } else {
                            stats.cache_misses += 1;
                        }
                        stats.affine_quad_count += requests.len() as u64;
                        stats.sprite_batch_count += atlas_ops
                            .iter()
                            .filter(|op| matches!(op, CommandOp::DrawIndexed { .. }))
                            .count() as u64;
                        ops.extend(atlas_ops);
                        continue;
                    }
                    let image_format = self
                        .raw_images
                        .get(&first.asset_id)
                        .ok_or_else(|| {
                            GalError::ffi(
                                StatusCode::InvalidArgument,
                                format!("unknown raw GUI image asset {}", first.asset_id),
                            )
                        })?
                        .format;
                    let group = dynamic_texture_group(first.stratum, first.asset_id);
                    self.ensure_resources(gal, group, color_format, depth_format, &mut stats)?;
                    for request in &requests {
                        if request.stratum != first.stratum || request.asset_id != first.asset_id {
                            return Err(GalError::ffi(
                                StatusCode::InvalidArgument,
                                "GUI affine batch changed semantic texture group",
                            ));
                        }
                        validate_affine_quad(request)?;
                        append_gui_quad(
                            &mut pending_gui_batches,
                            request.stratum,
                            group,
                            PackedGuiQuad {
                                origin: [request.x0, request.y0],
                                axis_u: [request.x1 - request.x0, request.y1 - request.y0],
                                axis_v: [request.x3 - request.x0, request.y3 - request.y0],
                                viewport: request.projection_extent,
                                clip: [
                                    request.clip_left as f32,
                                    request.clip_top as f32,
                                    (request.clip_left + request.clip_width) as f32,
                                    (request.clip_top + request.clip_height) as f32,
                                ],
                                clip_enabled: request.clip_mode == 1,
                                pre_present_y_flip,
                                uv: [
                                    request.u0,
                                    request.v0,
                                    request.u1 - request.u0,
                                    request.v1 - request.v0,
                                ],
                                color: request.material.color(argb_to_rgba(request.color_argb))?,
                                texture_mode: image_format.shader_mode(),
                                z: request.z,
                            },
                        );
                    }
                    stats.affine_quad_count = stats
                        .affine_quad_count
                        .saturating_add(requests.len() as u64);
                }
                GuiFrameRequest::Affine(_) => unreachable!("ordered affine requests are coalesced"),
                GuiFrameRequest::Mesh(item) => {
                    if !pending_gui_batches.is_empty() {
                        stats.sprite_batch_count = stats
                            .sprite_batch_count
                            .saturating_add(pending_gui_batches.len() as u64);
                        append_gui_batches_ops(
                            self,
                            frame_pass,
                            render_target,
                            color_attachment,
                            depth_attachment,
                            color_format,
                            depth_format,
                            &pending_gui_batches,
                            &mut ops,
                        )?;
                        pending_gui_batches.clear();
                    }
                    let mesh_ops = self.append_mesh_items_to_target(
                        gal,
                        world.as_deref_mut(),
                        generation,
                        render_target,
                        color_attachment,
                        Some(frame_pass),
                        depth_attachment,
                        depth_format,
                        item.layers,
                        &mut stats,
                    )?;
                    ops.extend(mesh_ops);
                }
            }
        }
        if !pending_gui_batches.is_empty() {
            stats.sprite_batch_count = stats
                .sprite_batch_count
                .saturating_add(pending_gui_batches.len() as u64);
            append_gui_batches_ops(
                self,
                frame_pass,
                render_target,
                color_attachment,
                depth_attachment,
                color_format,
                depth_format,
                &pending_gui_batches,
                &mut ops,
            )?;
        }
        let composite_uniform_buffers = self
            .mesh_composites
            .values()
            .map(|resources| resources.uniform_buffer)
            .collect::<BTreeSet<_>>();
        pack_gui_composite_uniform_uploads(&mut ops, &composite_uniform_buffers);
        stats.command_lists = 1;
        stats.command_ops = ops.len() as u64;
        Self::require_gui_draw_receipt(&stats, &ops)?;
        stats.frontend_nanos = crate::render::vulkanic::metrics::elapsed_nanos_u64(frontend_started);
        Ok((ops, stats))
    }

    /// A non-empty semantic GUI submission must lower to at least one explicit
    /// Rust draw. Copies, barriers, and target setup alone cannot satisfy the GUI
    /// contract or justify publishing the frame.
    pub(in crate::render::guirender::frontend) fn require_gui_draw_receipt(stats: &GuiSubmitStats, operations: &[CommandOp]) -> GalResult<()> {
        let semantic_items = stats
            .sprite_count
            .saturating_add(stats.affine_quad_count)
            .saturating_add(stats.mesh_batch_count);
        if semantic_items == 0 {
            return Ok(());
        }
        let draws = operations
            .iter()
            .filter(|operation| {
                matches!(
                    operation,
                    CommandOp::Draw { .. } | CommandOp::DrawIndexed { .. }
                )
            })
            .count();
        if draws == 0 {
            return Err(GalError::backend(format!(
                "GUI source writer recorded {semantic_items} semantic items but no draw operations"
            )));
        }
        Ok(())
    }
}

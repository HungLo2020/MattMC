//! References into world-owned atlases: staging, views, bindings and atlas quads.

use super::*;

impl GuiFrontend {
    /// Private declaration prerequisite. No FFI or renderer capability admits
    /// sampling these references until native view/lifetime lowering is complete.
    #[cfg(test)]
    pub(crate) fn stage_atlas_references(
        &mut self,
        revision: u64,
        references: &[GuiAtlasReference],
        accepted: impl FnMut(u32) -> Option<AcceptedAtlasIncarnation>,
    ) -> GalResult<()> {
        if !self.atlas_views.is_empty() {
            return Err(GalError::invalid_argument(
                "resident atlas views require owner-coordinated staging",
            ));
        }
        self.atlas_references.replace(
            revision,
            references,
            &self.raw_images.keys().copied().collect(),
            accepted,
        )
    }

    /// Context-owned staging path: declarations cannot certify their own acceptance.
    pub(crate) fn stage_owned_atlas_references(
        &mut self,
        gal: &mut VulkanicGal,
        world: &dyn GuiAtlasOwner,
        revision: u64,
        references: &[GuiAtlasReference],
    ) -> GalResult<()> {
        let mut next = self.atlas_references.clone();
        next.replace(
            revision,
            references,
            &self.raw_images.keys().copied().collect(),
            |id| world.accepted_gui_atlas_incarnation(id),
        )?;
        let changed = self
            .atlas_views
            .iter()
            .filter_map(|(asset, (incarnation, _))| {
                let retained = next
                    .resolve(*asset, |id| world.accepted_gui_atlas_incarnation(id))
                    .is_ok_and(|reference| reference.atlas == *incarnation);
                (!retained).then_some(*asset)
            })
            .collect();
        self.retire_atlas_views_for_assets(gal, &changed)?;
        self.atlas_references = next;
        Ok(())
    }

    /// Bounded, private preparation only. Native draw binding remains unadmitted.
    pub(crate) fn prepare_owned_atlas_view(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        asset: u64,
    ) -> GalResult<Handle> {
        let reference = self
            .atlas_references
            .resolve(asset, |id| world.accepted_gui_atlas_incarnation(id))?;
        if let Some((incarnation, view)) = self.atlas_views.get(&asset) {
            if *incarnation != reference.atlas {
                return Err(GalError::ffi(
                    StatusCode::StaleHandle,
                    "GUI atlas view was not invalidated before replacement",
                ));
            }
            return Ok(*view);
        }
        if self.atlas_views.len() >= crate::render::guirender::atlas_reference::MAX_GUI_ATLAS_REFERENCES {
            return Err(GalError::invalid_argument(
                "GUI atlas view residency bound exceeded",
            ));
        }
        let view = world.create_gui_atlas_view(gal, reference)?;
        self.atlas_views.insert(asset, (reference.atlas, view));
        Ok(view)
    }

    /// Called before the same context replaces owner textures. Keep declarations
    /// so failed publication can retry; consumption still checks exact incarnation.
    pub(crate) fn invalidate_atlas_texture_views(
        &mut self,
        gal: &mut VulkanicGal,
        textures: impl IntoIterator<Item = u32>,
    ) -> GalResult<()> {
        if self.atlas_views.is_empty() {
            return Ok(());
        }
        let textures: BTreeSet<_> = textures.into_iter().collect();
        let assets = self
            .atlas_views
            .iter()
            .filter_map(|(asset, (incarnation, _))| {
                textures.contains(&incarnation.texture_id).then_some(*asset)
            })
            .collect();
        self.retire_atlas_views_for_assets(gal, &assets)
    }

    /// Private native binding preparation. No Java/FFI capability admits this
    /// path yet. The owner's explicit upload prepares the image; this binding
    /// allocates no image, copied pixels, or texture-upload buffer.
    pub(crate) fn prepare_owned_atlas_binding(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        asset: u64,
        sampling: SamplerFilter,
        color: ColorFormat,
        depth: Option<TextureFormat>,
    ) -> GalResult<()> {
        let group = match sampling {
            SamplerFilter::Nearest => TextureGroup::Dynamic(asset),
            SamplerFilter::Linear => TextureGroup::DynamicLinear(asset),
        };
        self.prepare_owned_atlas_binding_group(gal, world, group, color, depth)
    }

    pub(super) fn prepare_owned_atlas_binding_group(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        group: TextureGroup,
        color: ColorFormat,
        depth: Option<TextureFormat>,
    ) -> GalResult<()> {
        let asset = TextureGroupKey::from(group)
            .dynamic_asset_id()
            .ok_or_else(|| {
                GalError::invalid_argument("GUI atlas binding needs a semantic image identity")
            })?;
        let sampling = group.sampling();
        let view = self.prepare_owned_atlas_view(gal, world, asset)?;
        let key = ResourceKey::new(group, color, depth);
        if let Some(binding) = self.resources.get(&key) {
            if !matches!(binding.image_ownership, GuiImageOwnership::AtlasView)
                || binding.texture_view != view
            {
                return Err(GalError::invalid_argument(
                    "GUI atlas binding ownership mismatch",
                ));
            }
            return Ok(());
        }
        let texture = gal.texture_view_info(view)?.texture;
        let pipeline = self.ensure_shared_pipeline(gal, group, color, depth)?;
        let mut created = Vec::new();
        let result = (|| -> GalResult<GuiResources> {
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("gui-atlas-{asset}.index"),
                size: index_bytes().len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Index,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(index_buffer);
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("gui-atlas-{asset}.uniform"),
                size: GUI_PACKED_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("gui-atlas-{asset}.sampler"),
                min_filter: sampling,
                mag_filter: sampling,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("gui-atlas-{asset}.set"),
                layout: pipeline.resource_layout,
                bindings: [
                    (uniform_buffer, ResourceBindingKind::UniformBuffer),
                    (view, ResourceBindingKind::SampledTexture),
                    (sampler, ResourceBindingKind::Sampler),
                ]
                .into_iter()
                .enumerate()
                .map(|(binding, (resource, kind))| ResourceBinding {
                    binding: binding as u32,
                    array_index: 0,
                    resource,
                    kind,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                })
                .collect(),
            })?;
            created.push(resource_set);
            gal.submit(SubmissionBatch {
                label: format!("gui-atlas-{asset}.index-upload"),
                command_lists: vec![CommandList::from(CommandListDesc {
                    label: format!("gui-atlas-{asset}.index-upload.commands"),
                    operations: gui_index_upload_ops(index_buffer),
                })],
            })?;
            Ok(GuiResources {
                private_sampler: None,
                index_buffer,
                uniform_buffer,
                texture,
                sampler,
                texture_view: view,
                resource_set,
                pipeline_layout: pipeline.pipeline_layout,
                pipeline: pipeline.pipeline,
                image_ownership: GuiImageOwnership::AtlasView,
            })
        })();
        match result {
            Ok(binding) => {
                self.resources.insert(key, binding);
                Ok(())
            }
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.retire(handle);
                }
                Err(error)
            }
        }
    }

    /// Private semantic atlas command consumer. It appends to the caller's GAL
    /// pass; it owns no target or presenter. Public mixed GUI admission remains
    /// disabled until its scheduler and FFI supply these typed references.
    pub(crate) fn append_owned_atlas_quads(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        frame_pass: Handle,
        target: Handle,
        color_view: Handle,
        depth_view: Option<Handle>,
        color: ColorFormat,
        depth: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: &[GuiAffineQuadRequest],
    ) -> GalResult<Vec<CommandOp>> {
        if requests.len() > GUI_MAX_EXPANDED_AFFINE_QUADS {
            return Err(GalError::invalid_argument(
                "GUI atlas quad count exceeds frame bound",
            ));
        }
        validate_gui_frame_sequences(&[], requests, &[], &[])?;
        let mut ordered = requests.to_vec();
        ordered.sort_by_key(|request| (request.stratum, request.sequence));
        self.append_scheduled_owned_atlas_quads(
            gal,
            world,
            frame_pass,
            target,
            color_view,
            depth_view,
            color,
            depth,
            pre_present_y_flip,
            &ordered,
            false,
        )
    }

    /// Only the parent-validated scheduler (or the validating wrapper above)
    /// calls this encoder. Expanded tile children legitimately share a parent
    /// sequence; they must not be reclassified as duplicate parent commands.
    pub(super) fn append_scheduled_owned_atlas_quads(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        frame_pass: Handle,
        target: Handle,
        color_view: Handle,
        depth_view: Option<Handle>,
        color: ColorFormat,
        depth: Option<TextureFormat>,
        pre_present_y_flip: bool,
        requests: &[GuiAffineQuadRequest],
        item_local_oriented_uv: bool,
    ) -> GalResult<Vec<CommandOp>> {
        if requests.len() > GUI_MAX_EXPANDED_AFFINE_QUADS {
            return Err(GalError::invalid_argument(
                "GUI atlas quad count exceeds frame bound",
            ));
        }
        if requests.is_empty() {
            return Ok(Vec::new());
        }
        world.require_gui_atlas_upload_boundary()?;
        let mut batches = Vec::new();
        // Validate the complete semantic input before allocating GPU bindings.
        for request in requests {
            if item_local_oriented_uv {
                // Ordered item-local quad vertices may run either direction
                // through the same bounded UV interval. Validate its bounds
                // without replacing the authored order used for rasterization.
                let mut bounds = request.clone();
                if ![bounds.u0, bounds.u1, bounds.v0, bounds.v1]
                    .iter()
                    .all(|v| v.is_finite())
                {
                    return Err(GalError::invalid_argument("non-finite item raster UV"));
                }
                if bounds.u0 > bounds.u1 {
                    std::mem::swap(&mut bounds.u0, &mut bounds.u1);
                }
                if bounds.v0 > bounds.v1 {
                    std::mem::swap(&mut bounds.v0, &mut bounds.v1);
                }
                validate_affine_quad(&bounds)?;
            } else {
                validate_affine_quad(request)?;
            }
            let reference = self.atlas_references.resolve(request.asset_id, |id| {
                world.accepted_gui_atlas_incarnation(id)
            })?;
            let low = reference.atlas_uv([request.u0, request.v0])?;
            let high = reference.atlas_uv([request.u1, request.v1])?;
            let uv = [low[0], low[1], high[0] - low[0], high[1] - low[1]];
            if uv.iter().any(|value| !value.is_finite()) {
                return Err(GalError::invalid_argument(
                    "GUI atlas UV interval overflows",
                ));
            }
            append_gui_quad(
                &mut batches,
                request.stratum,
                if item_local_oriented_uv && request.material.is_cutout() {
                    TextureGroup::DynamicItemCutout(request.asset_id)
                } else if item_local_oriented_uv {
                    TextureGroup::DynamicItemRaster(request.asset_id)
                } else {
                    dynamic_texture_group(request.stratum, request.asset_id)
                },
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
                    uv,
                    color: request.material.color(argb_to_rgba(request.color_argb))?,
                    texture_mode: GuiRawImageFormat::Rgba8.shader_mode(),
                    z: request.z,
                },
            );
        }
        for batch in &batches {
            self.prepare_owned_atlas_binding_group(gal, world, batch.group, color, depth)?;
        }
        let mut ops = Vec::new();
        append_gui_batches_ops(
            self, frame_pass, target, color_view, depth_view, color, depth, &batches, &mut ops,
        )?;
        Ok(ops)
    }

    pub(super) fn retire_atlas_views_for_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeSet<u64>,
    ) -> GalResult<()> {
        // Descriptor sets must release their view dependencies before the views.
        self.destroy_dynamic_resources_for_assets(gal, assets);
        for asset in assets {
            if let Some((_, view)) = self.atlas_views.get(asset) {
                gal.destroy(*view)?;
                // A failed destroy retains its ownership record for retry.
                self.atlas_views.remove(asset);
            }
        }
        Ok(())
    }

    pub(super) fn preflight_owned_atlas_commands(
        &self,
        world: Option<&dyn GuiAtlasOwner>,
        affine_quads: &[GuiAffineQuadRequest],
        tiled_quads: &[GuiTiledQuadRequest],
    ) -> GalResult<bool> {
        if affine_quads.iter().any(|quad| {
            quad.item_raster_scale != 0
                && (!self.atlas_references.contains(quad.asset_id)
                    || quad
                        .item_raster_layers
                        .iter()
                        .any(|layer| !self.atlas_references.contains(layer.asset_id)))
        }) {
            return Err(GalError::invalid_argument(
                "item raster requires an explicit owner-backed atlas source",
            ));
        }
        let has_atlases = affine_quads
            .iter()
            .any(|quad| self.atlas_references.contains(quad.asset_id))
            || tiled_quads
                .iter()
                .any(|quad| self.atlas_references.contains(quad.asset_id));
        if has_atlases {
            let owner = world.ok_or_else(|| {
                GalError::unsupported_feature(
                    "GUI atlas commands require explicit world owner access",
                )
            })?;
            owner.require_gui_atlas_upload_boundary()?;
            for asset in
                affine_quads
                    .iter()
                    .map(|quad| quad.asset_id)
                    .chain(affine_quads.iter().flat_map(|quad| {
                        quad.item_raster_layers.iter().map(|layer| layer.asset_id)
                    }))
                    .chain(tiled_quads.iter().map(|quad| quad.asset_id))
            {
                if self.atlas_references.contains(asset) {
                    self.atlas_references
                        .resolve(asset, |id| owner.accepted_gui_atlas_incarnation(id))?;
                }
            }
        }
        Ok(has_atlases)
    }

    pub(super) fn preflight_mesh_atlas_commands(
        &self,
        world: Option<&dyn GuiAtlasOwner>,
        batches: &[GuiMeshBatchRequest],
    ) -> GalResult<()> {
        for batch in batches {
            let atlas = self.atlas_references.contains(batch.asset_id);
            if batch.item_raster_scale != 0
                && !atlas
                && !self.raw_images.contains_key(&batch.asset_id)
            {
                return Err(GalError::invalid_argument(
                    "native item mesh requires an explicitly owned atlas or image resource",
                ));
            }
            if !atlas {
                continue;
            }
            if !mesh_atlas_contract_supported(batch) {
                return Err(GalError::unsupported_feature("GUI mesh atlas sampling requires an explicit inventory base or front-model overlay layer"));
            }
            let owner = world.ok_or_else(|| {
                GalError::invalid_argument(
                    "GUI mesh atlas sampling requires its explicit Rust image owner",
                )
            })?;
            owner.require_gui_atlas_upload_boundary()?;
            let reference = self.atlas_references.resolve(batch.asset_id, |id| {
                owner.accepted_gui_atlas_incarnation(id)
            })?;
            for vertex in &batch.vertices {
                reference.atlas_uv(vertex.local_uv)?;
            }
        }
        Ok(())
    }
}

pub(super) fn mesh_atlas_contract_supported(batch: &GuiMeshBatchRequest) -> bool {
    if batch.material_mode == GuiMeshMaterialMode::ModelOverlay {
        return batch.item_raster_scale != 0
            && batch.lighting_mode == GuiMeshLightingMode::FrontModel
            && batch.alpha_cutoff == 0.0
            && batch.item_foil.is_none();
    }
    (batch.item_raster_scale != 0 || batch.lighting_mode == GuiMeshLightingMode::InventoryBlock)
        && matches!(
            batch.material_mode,
            GuiMeshMaterialMode::Opaque
                | GuiMeshMaterialMode::Cutout
                | GuiMeshMaterialMode::Translucent
        )
}

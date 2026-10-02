//! Full item rasters: offscreen item renders composited as GUI quads.

use super::*;

pub(super) struct GuiItemRasterResources {
    pub(super) target: crate::render::guirender::items::raster::GuiItemRasterTarget,
    pub(super) composite: GuiMeshCompositeResources,
    pub(super) usage: TextureUsageState,
    pub(super) lease: crate::render::vulkanic::commands::SubmissionUsage,
}

#[derive(Clone, Debug)]
pub(crate) struct GuiItemRasterGroup {
    pub presentation: GuiAffineQuadRequest,
    pub layers: Vec<GuiItemRasterLayer>,
}

impl GuiItemRasterGroup {
    pub(super) fn single(request: &GuiAffineQuadRequest) -> Self {
        if !request.item_raster_layers.is_empty() {
            return Self {
                presentation: request.clone(),
                layers: request.item_raster_layers.clone(),
            };
        }
        Self {
            presentation: request.clone(),
            layers: vec![GuiItemRasterLayer {
                asset_id: request.asset_id,
                color_argb: request.color_argb,
                material: request.material,
                geometry: request.item_raster_geometry,
                uv: [request.u0, request.v0, request.u1, request.v1],
                model_transform: Default::default(),
            }],
        }
    }
}

impl GuiFrontend {
    /// Private item-local raster stage. The target and source atlas are both
    /// Rust GAL resources. No presentation or CPU pixel round-trip occurs.
    /// The caller owns target initialization, transitions and composition.
    pub(crate) fn append_owned_item_raster_quads(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        frame_pass: Handle,
        target: Handle,
        color_view: Handle,
        requests: &[(
            crate::render::guirender::items::raster::GuiItemRasterPlacement,
            GuiAffineQuadRequest,
        )],
    ) -> GalResult<Vec<CommandOp>> {
        if requests.len() > GUI_MAX_RAW_IMAGES {
            return Err(GalError::invalid_argument(
                "GUI item raster count exceeds bounded limit",
            ));
        }
        let extent = gal.pass_target_extent(target)?;
        let quads = requests
            .iter()
            .map(|(placement, local)| {
                if placement.target_extent != [extent.width, extent.height] {
                    return Err(GalError::invalid_argument(
                        "GUI item raster target extent mismatch",
                    ));
                }
                placement.lower_quad(local)
            })
            .collect::<GalResult<Vec<_>>>()?;
        validate_gui_frame_sequences(&[], &quads, &[], &[])?;
        self.append_scheduled_owned_atlas_quads(
            gal,
            world,
            frame_pass,
            target,
            color_view,
            None,
            ColorFormat::Rgba8Unorm,
            None,
            false,
            &quads,
            true,
        )
    }

    /// Prepare all full-item rasters before GUI ordering consumes their image
    /// cells. Source identity is immutable semantic data, not a Java cache key.
    pub(super) fn prepare_full_item_rasters(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        ordered: &[GuiFrameRequest],
        color: ColorFormat,
        depth: Option<TextureFormat>,
        stats: &mut GuiSubmitStats,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<
        BTreeMap<
            u64,
            (
                GuiMeshCompositeKey,
                crate::render::guirender::items::raster::GuiItemRasterPlacement,
                u64,
            ),
        >,
    > {
        let mut items: Vec<_> = ordered
            .iter()
            .filter_map(|entry| match entry {
                GuiFrameRequest::AffineBatch(batch) => Some(batch.iter()),
                _ => None,
            })
            .flatten()
            .filter(|request| request.item_raster_scale != 0)
            .collect();
        items.sort_by_key(|request| request.sequence);
        self.prepare_item_raster_groups(
            gal,
            world,
            &items
                .into_iter()
                .map(GuiItemRasterGroup::single)
                .collect::<Vec<_>>(),
            color,
            depth,
            stats,
            ops,
        )
    }

    /// One ordered layer group becomes one offscreen item and one presentation.
    /// This private native entry point is shared by single-quad lowering and
    /// grouped-raster tests; multi-layer Java admission remains disabled.
    pub(super) fn prepare_item_raster_groups(
        &mut self,
        gal: &mut VulkanicGal,
        world: &mut dyn GuiAtlasOwner,
        items: &[GuiItemRasterGroup],
        color: ColorFormat,
        depth: Option<TextureFormat>,
        stats: &mut GuiSubmitStats,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<
        BTreeMap<
            u64,
            (
                GuiMeshCompositeKey,
                crate::render::guirender::items::raster::GuiItemRasterPlacement,
                u64,
            ),
        >,
    > {
        use crate::render::guirender::items::raster::{
            GuiItemRasterIdentity, GuiItemRasterTarget, MAX_ITEM_LAYERS,
        };
        if items.is_empty() {
            return Ok(BTreeMap::new());
        }
        if items.len() > GUI_MAX_MESH_BATCHES
            || items
                .iter()
                .any(|item| item.layers.is_empty() || item.layers.len() > MAX_ITEM_LAYERS)
            || items.iter().map(|item| item.layers.len()).sum::<usize>() > GUI_MAX_RAW_IMAGES
        {
            return Err(GalError::invalid_argument(
                "full-item raster frame exceeds bounded composite stream",
            ));
        }
        let scale = items[0].presentation.item_raster_scale;
        let mut sequences = BTreeSet::new();
        let mut identities = BTreeMap::new();
        let mut unique_identities = Vec::new();
        let mut unique = Vec::new();
        let mut indices = Vec::new();
        for item in items {
            let request = &item.presentation;
            validate_affine_quad(request)?;
            if scale == 0
                || request.item_raster_scale != scale
                || request.sequence == 0
                || !sequences.insert(request.sequence)
            {
                return Err(GalError::invalid_argument(
                    "item rasters require one explicit frame GUI scale",
                ));
            }
            let mut prepared = item.clone();
            let mut identity = Vec::with_capacity(prepared.layers.len());
            for layer in &mut prepared.layers {
                layer.geometry = layer.model_transform.lower(layer.geometry)?;
                layer.model_transform = Default::default();
                layer.material.color(argb_to_rgba(layer.color_argb))?;
                let lighting = match layer.material {
                    crate::render::guirender::items::material::GuiAffineMaterial::FlatItem(value)
                    | crate::render::guirender::items::material::GuiAffineMaterial::FlatItemCutout(value) => value,
                    _ => {
                        return Err(GalError::invalid_argument(
                            "item layer requires explicit item material",
                        ))
                    }
                };
                if lighting
                    .rgb
                    .iter()
                    .any(|v| !v.is_finite() || !(0.0..=1.0).contains(v))
                {
                    return Err(GalError::invalid_argument("invalid item layer lighting"));
                }
                let reference = self.atlas_references.resolve(layer.asset_id, |id| {
                    world.accepted_gui_atlas_incarnation(id)
                })?;
                identity.push(GuiItemRasterIdentity {
                    asset_id: layer.asset_id,
                    color_argb: layer.color_argb,
                    lighting_rgb: lighting.rgb.map(|v| if v == 0.0 { 0 } else { v.to_bits() }),
                    cutout: layer.material.is_cutout(),
                    atlas_generation: reference.atlas.generation,
                    texture_id: reference.atlas.texture_id,
                    geometry: layer.geometry.identity()?,
                    uv: crate::render::guirender::items::raster::item_uv_identity(layer.uv)?,
                    region: [reference.x, reference.y, reference.width, reference.height],
                });
            }
            let index = *identities.entry(identity.clone()).or_insert_with(|| {
                unique_identities.push(identity);
                unique.push(prepared);
                (unique.len() - 1) as u32
            });
            indices.push(index);
        }
        let mut next_slots = self.item_raster_slots.clone();
        let placements = next_slots.prepare_groups(scale, &unique_identities, 4096)?;
        let [width, height] = placements[0].target_extent;
        let key = GuiMeshCompositeKey {
            item_identity: 0,
            width,
            height,
            color_format: color,
            depth_format: depth,
        };
        if !self.item_rasters.contains_key(&key) {
            let requested_pixels = u64::from(width) * u64::from(height);
            if requested_pixels > 32 * 1024 * 1024 {
                return Err(GalError::invalid_argument(
                    "item raster exceeds pixel budget",
                ));
            }
            loop {
                let pixels: u64 = self
                    .item_rasters
                    .keys()
                    .map(|key| u64::from(key.width) * u64::from(key.height))
                    .sum();
                if self.item_rasters.len() < 4 && pixels + requested_pixels <= 32 * 1024 * 1024 {
                    break;
                }
                let oldest = self
                    .item_rasters
                    .iter()
                    .filter(|(_, resources)| !resources.lease.has_pending_commands())
                    .min_by_key(|(_, resources)| resources.lease.last_submission())
                    .map(|(key, _)| key.clone())
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "item raster budget is pinned by prepared commands",
                        )
                    })?;
                if let Some(resources) = self.item_rasters.remove(&oldest) {
                    resources.composite.destroy(gal);
                    resources.target.destroy(gal)?;
                }
            }
            let target = GuiItemRasterTarget::create(
                gal,
                Extent3d {
                    width,
                    height,
                    depth: 1,
                },
            )?;
            let composite = match GuiMeshCompositeResources::create(
                gal,
                "gui.item-raster.composite",
                color,
                depth,
                target.view,
            ) {
                Ok(composite) => composite,
                Err(error) => {
                    let _ = target.destroy(gal);
                    return Err(error);
                }
            };
            self.item_rasters.insert(
                key,
                GuiItemRasterResources {
                    target,
                    composite,
                    usage: TextureUsageState::Undefined,
                    lease: Default::default(),
                },
            );
        }
        let resource = self.item_rasters.get(&key).expect("created item raster");
        let (pass, target, view, image) = (
            resource.target.pass,
            resource.target.target,
            resource.target.view,
            resource.target.color,
        );
        ops.push(CommandOp::TrackSubmission(resource.lease.clone()));
        let before = resource.usage;
        ops.push(CommandOp::Barrier(texture_barrier(
            image,
            before,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(crate::render::vulkanic::commands::ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 0.0,
                }),
            }],
            depth_stencil: None,
        });
        ops.push(CommandOp::EndPass);
        let mut raster_requests = Vec::new();
        for (index, item) in unique.into_iter().enumerate() {
            for layer in item.layers {
                let mut source = item.presentation.clone();
                source.item_raster_scale = 0;
                source.item_raster_layers.clear();
                source.sequence = raster_requests.len() as u64 + 1;
                source.asset_id = layer.asset_id;
                source.color_argb = layer.color_argb;
                source.material = layer.material;
                source.item_raster_geometry = layer.geometry;
                [source.u0, source.v0, source.u1, source.v1] = layer.uv;
                [
                    source.x0, source.y0, source.x1, source.y1, source.x3, source.y3,
                ] = layer.geometry.corners;
                raster_requests.push((placements[index], source));
            }
        }
        ops.extend(self.append_owned_item_raster_quads(
            gal,
            world,
            pass,
            target,
            view,
            &raster_requests,
        )?);
        self.item_rasters.get_mut(&key).unwrap().usage = TextureUsageState::ColorAttachment;
        self.item_raster_slots = next_slots;
        stats.owned_intermediate_targets.push(target);
        items
            .iter()
            .zip(indices)
            .enumerate()
            .map(|(offset, (item, index))| {
                Ok((
                    item.presentation.sequence,
                    (
                        key,
                        placements[index as usize],
                        offset as u64 * GUI_MESH_COMPOSITE_UNIFORM_STRIDE,
                    ),
                ))
            })
            .collect()
    }
}

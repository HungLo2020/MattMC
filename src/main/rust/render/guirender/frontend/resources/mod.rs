//! Texture groups, per-group GPU resources and frame passes.

mod groups;

pub(in crate::render::guirender::frontend) use self::groups::*;

use super::*;

pub(in crate::render::guirender::frontend) struct GuiResources {
    /// An optional sampler owned by this binding, distinct from the image's
    /// shared clamp samplers. Allocated only for repeating glint bindings.
    pub(in crate::render::guirender::frontend) private_sampler: Option<Handle>,
    pub(in crate::render::guirender::frontend) index_buffer: Handle,
    pub(in crate::render::guirender::frontend) uniform_buffer: Handle,
    pub(in crate::render::guirender::frontend) texture: Handle,
    pub(in crate::render::guirender::frontend) sampler: Handle,
    pub(in crate::render::guirender::frontend) texture_view: Handle,
    pub(in crate::render::guirender::frontend) resource_set: Handle,
    pub(in crate::render::guirender::frontend) pipeline_layout: Handle,
    pub(in crate::render::guirender::frontend) pipeline: Handle,
    pub(in crate::render::guirender::frontend) image_ownership: GuiImageOwnership,
}

impl GuiResources {
    /// The program objects are borrowed from `GuiFrontend::shared_pipelines`.
    /// A texture resource owns only its explicit texture/buffer/set state.
    pub(in crate::render::guirender::frontend) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        let mut handles = vec![self.resource_set, self.uniform_buffer, self.index_buffer];
        handles.extend(self.private_sampler);
        match self.image_ownership {
            GuiImageOwnership::Owned { upload_buffer } => {
                handles.extend([self.texture_view, self.sampler, self.texture, upload_buffer])
            }
            GuiImageOwnership::SharedRaw { .. } => {}
            GuiImageOwnership::AtlasView => handles.push(self.sampler),
        }
        handles
    }
}

#[derive(Clone, Copy)]
pub(in crate::render::guirender::frontend) struct CachedPass {
    pub(in crate::render::guirender::frontend) frame_target: Handle,
    pub(in crate::render::guirender::frontend) pass: Handle,
    pub(in crate::render::guirender::frontend) depth_format: Option<TextureFormat>,
}

#[derive(Clone)]
pub(in crate::render::guirender::frontend) struct GuiTextureSource {
    pub(in crate::render::guirender::frontend) width: u32,
    pub(in crate::render::guirender::frontend) height: u32,
    pub(in crate::render::guirender::frontend) format: GuiRawImageFormat,
    pub(in crate::render::guirender::frontend) bytes: Vec<u8>,
}

#[derive(Clone, Copy)]
pub(in crate::render::guirender::frontend) struct PackedGuiQuad {
    pub(in crate::render::guirender::frontend) origin: [f32; 2],
    pub(in crate::render::guirender::frontend) axis_u: [f32; 2],
    pub(in crate::render::guirender::frontend) axis_v: [f32; 2],
    pub(in crate::render::guirender::frontend) viewport: [f32; 2],
    pub(in crate::render::guirender::frontend) clip: [f32; 4],
    pub(in crate::render::guirender::frontend) clip_enabled: bool,
    /// The selected-source overlay receives one later final copy. Preserve
    /// Java's top-left GUI semantics by precompensating that copy in the
    /// Rust-owned GUI stream. Texture coordinates stay semantic: the later
    /// image copy supplies the corresponding texel-row inversion.
    pub(in crate::render::guirender::frontend) pre_present_y_flip: bool,
    pub(in crate::render::guirender::frontend) uv: [f32; 4],
    pub(in crate::render::guirender::frontend) color: [f32; 4],
    pub(in crate::render::guirender::frontend) texture_mode: f32,
    pub(in crate::render::guirender::frontend) z: f32,
}

pub(in crate::render::guirender::frontend) struct GuiBatch {
    pub(in crate::render::guirender::frontend) stratum: u32,
    pub(in crate::render::guirender::frontend) group: TextureGroup,
    pub(in crate::render::guirender::frontend) quads: Vec<PackedGuiQuad>,
}

impl GuiFrontend {
    pub fn clear_frame_pass(&mut self, gal: &mut VulkanicGal) {
        if let Some(pass) = self.cached_pass.take() {
            let _ = gal.retire(pass.pass);
        }
    }

    pub fn clear_frame_passes_for_targets(&mut self, gal: &mut VulkanicGal, targets: &[Handle]) {
        let Some(pass) = self.cached_pass else {
            return;
        };
        if targets.contains(&pass.frame_target) {
            self.cached_pass = None;
            let _ = gal.retire(pass.pass);
        }
    }

    pub(in crate::render::guirender::frontend) fn frame_pass(
        &mut self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
        depth_format: Option<TextureFormat>,
    ) -> GalResult<Handle> {
        if let Some(cached) = self.cached_pass {
            if cached.frame_target == frame_target && cached.depth_format == depth_format {
                return Ok(cached.pass);
            }
            gal.destroy(cached.pass)?;
            self.cached_pass = None;
        }
        let pass = gal.create_render_pass(RenderPassDesc {
            label: "minecraft.gui.frame.pass".to_string(),
            target: frame_target,
            color_formats: vec![gal.pass_target_color_format(frame_target)?],
            depth_format,
        })?;
        self.cached_pass = Some(CachedPass {
            frame_target,
            pass,
            depth_format,
        });
        Ok(pass)
    }

    pub(in crate::render::guirender::frontend) fn create_resources(
        &mut self,
        gal: &mut VulkanicGal,
        group: TextureGroup,
        shared_pipeline: GuiSharedPipeline,
    ) -> GalResult<GuiResources> {
        let label = format!("gui-textured-{}-gen{}", group.label(), self.generation);
        let source = self.texture_source(group)?;
        let dynamic_texture_key = TextureGroupKey::from(group)
            .dynamic_asset_id()
            .map(|asset_id| (asset_id, source.format));
        let mut created = Vec::new();
        let mut newly_shared_texture = None;
        let result = (|| -> GalResult<GuiResources> {
            let shared_texture =
                dynamic_texture_key.and_then(|key| self.dynamic_textures.get(&key).copied());
            let upload_buffer = if let Some(shared) = shared_texture {
                shared.upload_buffer
            } else {
                let upload = gal.create_buffer(BufferDesc {
                    label: format!("{label}.texture-upload"),
                    size: source.bytes.len() as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![
                        BufferUsage::TransferSrc,
                        BufferUsage::TransferDst,
                        BufferUsage::HostWrite,
                    ],
                })?;
                created.push(upload);
                upload
            };
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.index"),
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
                label: format!("{label}.uniform"),
                size: GUI_PACKED_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let (upload_buffer, texture, sampler, texture_view, reused_shared_texture) =
                if let Some(key) = dynamic_texture_key {
                    if let Some(shared) = self.dynamic_textures.get(&key).copied() {
                        // Shared dynamic textures are immutable for one raw-image
                        // generation. Reuse them directly so a second blend
                        // stratum does not transiently allocate duplicate native
                        // image objects before immediately destroying them.
                        (
                            shared.upload_buffer,
                            shared.texture,
                            shared.sampler(group.sampling()),
                            shared.texture_view,
                            true,
                        )
                    } else {
                        let texture = gal.create_texture(TextureDesc {
                            label: format!("{label}.texture"),
                            dimension: TextureDimension::D2,
                            format: source.format.texture_format(),
                            extent: Extent3d {
                                width: source.width,
                                height: source.height,
                                depth: 1,
                            },
                            mip_levels: 1,
                            array_layers: 1,
                            usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
                        })?;
                        created.push(texture);
                        let nearest_sampler = gal.create_sampler(SamplerDesc {
                            label: format!("{label}.sampler.nearest"),
                            min_filter: SamplerFilter::Nearest,
                            mag_filter: SamplerFilter::Nearest,
                            mip_filter: SamplerFilter::Nearest,
                            address_u: SamplerAddressMode::ClampToEdge,
                            address_v: SamplerAddressMode::ClampToEdge,
                            address_w: SamplerAddressMode::ClampToEdge,
                            comparison: None,
                        })?;
                        created.push(nearest_sampler);
                        let linear_sampler = gal.create_sampler(SamplerDesc {
                            label: format!("{label}.sampler.linear"),
                            min_filter: SamplerFilter::Linear,
                            mag_filter: SamplerFilter::Linear,
                            mip_filter: SamplerFilter::Nearest,
                            address_u: SamplerAddressMode::ClampToEdge,
                            address_v: SamplerAddressMode::ClampToEdge,
                            address_w: SamplerAddressMode::ClampToEdge,
                            comparison: None,
                        })?;
                        created.push(linear_sampler);
                        let texture_view = gal.create_texture_view(TextureViewDesc {
                            label: format!("{label}.texture-view"),
                            texture,
                            format: source.format.texture_format(),
                            base_mip: 0,
                            mip_count: 1,
                            base_layer: 0,
                            layer_count: 1,
                        })?;
                        created.push(texture_view);
                        newly_shared_texture = Some((
                            key,
                            SharedDynamicGuiTexture {
                                upload_buffer,
                                texture,
                                nearest_sampler,
                                linear_sampler,
                                texture_view,
                            },
                        ));
                        (
                            upload_buffer,
                            texture,
                            match group.sampling() {
                                SamplerFilter::Linear => linear_sampler,
                                SamplerFilter::Nearest => nearest_sampler,
                            },
                            texture_view,
                            false,
                        )
                    }
                } else {
                    let texture = gal.create_texture(TextureDesc {
                        label: format!("{label}.texture"),
                        dimension: TextureDimension::D2,
                        format: source.format.texture_format(),
                        extent: Extent3d {
                            width: source.width,
                            height: source.height,
                            depth: 1,
                        },
                        mip_levels: 1,
                        array_layers: 1,
                        usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
                    })?;
                    created.push(texture);
                    let sampler = gal.create_sampler(SamplerDesc {
                        label: format!("{label}.sampler"),
                        min_filter: SamplerFilter::Nearest,
                        mag_filter: SamplerFilter::Nearest,
                        mip_filter: SamplerFilter::Nearest,
                        address_u: SamplerAddressMode::ClampToEdge,
                        address_v: SamplerAddressMode::ClampToEdge,
                        address_w: SamplerAddressMode::ClampToEdge,
                        comparison: None,
                    })?;
                    created.push(sampler);
                    let texture_view = gal.create_texture_view(TextureViewDesc {
                        label: format!("{label}.texture-view"),
                        texture,
                        format: source.format.texture_format(),
                        base_mip: 0,
                        mip_count: 1,
                        base_layer: 0,
                        layer_count: 1,
                    })?;
                    created.push(texture_view);
                    (upload_buffer, texture, sampler, texture_view, false)
                };
            let private_sampler = if matches!(group, TextureGroup::DynamicGlint(_)) {
                let TextureGroup::DynamicGlint(asset_id) = group else {
                    unreachable!()
                };
                let (filter, address) = self
                    .raw_images
                    .get(&asset_id)
                    .and_then(|image| image.sampling)
                    .unwrap_or((SamplerFilter::Linear, SamplerAddressMode::Repeat));
                let sampler = gal.create_sampler(SamplerDesc {
                    label: format!("{label}.sampler.resource-glint"),
                    min_filter: filter,
                    mag_filter: filter,
                    mip_filter: SamplerFilter::Nearest,
                    address_u: address,
                    address_v: address,
                    address_w: address,
                    comparison: None,
                })?;
                created.push(sampler);
                Some(sampler)
            } else {
                None
            };
            let sampler = private_sampler.unwrap_or(sampler);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: shared_pipeline.resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: texture_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                ],
            })?;
            created.push(resource_set);
            let resources = GuiResources {
                private_sampler,
                index_buffer,
                uniform_buffer,
                texture,
                sampler,
                texture_view,
                resource_set,
                pipeline_layout: shared_pipeline.pipeline_layout,
                pipeline: shared_pipeline.pipeline,
                image_ownership: match dynamic_texture_key {
                    Some(key) => GuiImageOwnership::SharedRaw { key },
                    None => GuiImageOwnership::Owned { upload_buffer },
                },
            };
            self.upload_resources(
                gal,
                &source,
                group,
                &resources,
                (!reused_shared_texture).then_some(upload_buffer),
            )?;
            if let Some((key, shared)) = newly_shared_texture.take() {
                self.dynamic_textures.insert(key, shared);
            }
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub(in crate::render::guirender::frontend) fn upload_resources(
        &mut self,
        gal: &mut VulkanicGal,
        source: &GuiTextureSource,
        group: TextureGroup,
        resources: &GuiResources,
        image_upload: Option<Handle>,
    ) -> GalResult<()> {
        // Each binding owns a new index buffer even if its image is shared.
        // Initialize it independently; an existing image stays ShaderRead.
        let mut operations = gui_index_upload_ops(resources.index_buffer);
        if let Some(upload_buffer) = image_upload {
            operations.extend([
                CommandOp::HostWriteBuffer {
                    buffer: upload_buffer,
                    offset: 0,
                    data: source.bytes.clone(),
                },
                CommandOp::Barrier(buffer_barrier(
                    upload_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::Barrier(texture_barrier(
                    resources.texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                    buffer: upload_buffer,
                    buffer_offset: 0,
                    bytes_per_row: source.width * source.format.bytes_per_pixel() as u32,
                    rows_per_image: source.height,
                    texture: resources.texture,
                    texture_mip: 0,
                    texture_layer: 0,
                    texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                    extent: Extent3d {
                        width: source.width,
                        height: source.height,
                        depth: 1,
                    },
                }),
                CommandOp::Barrier(texture_barrier(
                    resources.texture,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
            ]);
        }
        gal.submit(SubmissionBatch {
            label: format!("gui-textured-{}.upload", group.label()),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: format!("gui-textured-{}.upload.commands", group.label()),
                operations,
            })],
        })?;
        Ok(())
    }

    pub(in crate::render::guirender::frontend) fn atlas_for(&mut self, group: TextureGroup) -> GalResult<&TextureAtlas> {
        if matches!(
            group,
            TextureGroup::Dynamic(_)
                | TextureGroup::DynamicLinear(_)
                | TextureGroup::DynamicGlint(_)
                | TextureGroup::DynamicOpaque(_)
                | TextureGroup::DynamicVignette(_)
                | TextureGroup::DynamicInvert(_)
                | TextureGroup::DynamicPremultiplied(_)
                | TextureGroup::DynamicAdditive(_)
        ) {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "raw GUI images do not belong to the static sprite atlas",
            ));
        }
        let key = TextureGroupKey::from(group);
        if !self.atlases.contains_key(&key) {
            let atlas = build_atlas(group, &self.asset_overrides)?;
            self.atlases.insert(key, atlas);
        }
        Ok(self.atlases.get(&key).expect("atlas was just inserted"))
    }

    pub(in crate::render::guirender::frontend) fn texture_source(&mut self, group: TextureGroup) -> GalResult<GuiTextureSource> {
        match group {
            TextureGroup::Alpha | TextureGroup::Invert => {
                let atlas = self.atlas_for(group)?.clone();
                Ok(GuiTextureSource {
                    width: atlas.width,
                    height: atlas.height,
                    format: GuiRawImageFormat::Rgba8,
                    bytes: atlas.bytes,
                })
            }
            TextureGroup::Dynamic(asset_id)
            | TextureGroup::DynamicLinear(asset_id)
            | TextureGroup::DynamicGlint(asset_id)
            | TextureGroup::DynamicOpaque(asset_id)
            | TextureGroup::DynamicVignette(asset_id)
            | TextureGroup::DynamicInvert(asset_id)
            | TextureGroup::DynamicPremultiplied(asset_id)
            | TextureGroup::DynamicAdditive(asset_id)
            | TextureGroup::DynamicLequalDepth(asset_id)
            | TextureGroup::DynamicItemRaster(asset_id)
            | TextureGroup::DynamicItemCutout(asset_id) => {
                if self.atlas_references.contains(asset_id) {
                    return Err(GalError::ffi(StatusCode::UnsupportedFeature,
                        "explicit GUI atlas sampling is not yet admitted; copied-image fallback is forbidden"));
                }
                let image = self.raw_images.get(&asset_id).ok_or_else(|| {
                    GalError::ffi(
                        StatusCode::InvalidArgument,
                        format!("unknown raw GUI image asset {asset_id}"),
                    )
                })?;
                Ok(GuiTextureSource {
                    width: image.width,
                    height: image.height,
                    format: image.format,
                    bytes: image.pixels.clone(),
                })
            }
        }
    }

    pub(in crate::render::guirender::frontend) fn ensure_resources(
        &mut self,
        gal: &mut VulkanicGal,
        group: TextureGroup,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
        stats: &mut GuiSubmitStats,
    ) -> GalResult<()> {
        let key = ResourceKey::new(group, color_format, depth_format);
        if self.resources.contains_key(&key) {
            stats.cache_hits += 1;
            return Ok(());
        }
        let shared_pipeline =
            self.ensure_shared_pipeline(gal, group, color_format, depth_format)?;
        let resources = self.create_resources(gal, group, shared_pipeline)?;
        self.resources.insert(key, resources);
        stats.cache_misses += 1;
        stats.resource_creates += 7;
        Ok(())
    }
}

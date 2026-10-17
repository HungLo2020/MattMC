//! Resources of custom shader-pack post-effect chains: programs, intermediates and depth targets.

use super::*;

/// Rust-owned resources for the bounded main-target custom post-effect
/// contract. Each pass owns an independent snapshot and pipeline; this keeps
/// sequential main-target chains explicit without allocating arbitrary graph
/// intermediates or borrowing Java PostChain/Iris handles.
pub(in crate::render::guirender::frontend) struct CustomPostEffectResources {
    pub(in crate::render::guirender::frontend) frame_copy_scratch: Option<Handle>,
    pub(in crate::render::guirender::frontend) identity: String,
    pub(in crate::render::guirender::frontend) width: u32,
    pub(in crate::render::guirender::frontend) height: u32,
    pub(in crate::render::guirender::frontend) color_format: ColorFormat,
    pub(in crate::render::guirender::frontend) snapshots: Vec<Handle>,
    pub(in crate::render::guirender::frontend) snapshot_views: Vec<Handle>,
    pub(in crate::render::guirender::frontend) target_samplers: Vec<Handle>,
    pub(in crate::render::guirender::frontend) image_textures: Vec<Option<Handle>>,
    pub(in crate::render::guirender::frontend) image_views: Vec<Option<Handle>>,
    pub(in crate::render::guirender::frontend) image_samplers: Vec<Option<Handle>>,
    pub(in crate::render::guirender::frontend) image_upload_buffers: Vec<Option<Handle>>,
    pub(in crate::render::guirender::frontend) combined_samplers: Vec<Handle>,
    pub(in crate::render::guirender::frontend) uniform_buffers: Vec<Handle>,
    pub(in crate::render::guirender::frontend) vertex_shader: Handle,
    pub(in crate::render::guirender::frontend) fragment_shader: Handle,
    pub(in crate::render::guirender::frontend) resource_layout: Handle,
    pub(in crate::render::guirender::frontend) resource_set: Handle,
    pub(in crate::render::guirender::frontend) pipeline_layout: Handle,
    pub(in crate::render::guirender::frontend) pipeline: Handle,
}

/// Rust-owned topology used when an acquired frame target is the source of a
/// depth-reading effect. The swapchain image stays opaque; the effect renders
/// into these owned attachments and is copied back through the explicit GAL
/// command at the end of the chain.
pub(in crate::render::guirender::frontend) struct CustomPostEffectDepthTarget {
    pub(in crate::render::guirender::frontend) source: Handle,
    pub(in crate::render::guirender::frontend) target: Handle,
    pub(in crate::render::guirender::frontend) color_texture: Option<Handle>,
    pub(in crate::render::guirender::frontend) color_view: Handle,
    pub(in crate::render::guirender::frontend) owns_color_view: bool,
    pub(in crate::render::guirender::frontend) depth_texture: Option<Handle>,
    pub(in crate::render::guirender::frontend) depth_view: Handle,
    pub(in crate::render::guirender::frontend) owns_depth_view: bool,
}

impl CustomPostEffectDepthTarget {
    pub(in crate::render::guirender::frontend) fn handles_in_destroy_order(self) -> Vec<Handle> {
        let mut handles = vec![self.target];
        if self.owns_color_view {
            handles.push(self.color_view);
        }
        if let Some(texture) = self.color_texture {
            handles.push(texture);
        }
        if self.owns_depth_view {
            handles.push(self.depth_view);
        }
        if let Some(texture) = self.depth_texture {
            handles.push(texture);
        }
        handles
    }
}

/// One bounded Rust-owned intermediate target for a custom post-effect graph.
/// The graph admission layer owns each declared target independently; the
/// bounded scheduler still rejects feedback and cycles before allocation.
pub(in crate::render::guirender::frontend) struct CustomPostEffectIntermediate {
    pub(in crate::render::guirender::frontend) identity: String,
    pub(in crate::render::guirender::frontend) width: u32,
    pub(in crate::render::guirender::frontend) height: u32,
    pub(in crate::render::guirender::frontend) color_format: ColorFormat,
    pub(in crate::render::guirender::frontend) texture: Handle,
    pub(in crate::render::guirender::frontend) view: Handle,
    pub(in crate::render::guirender::frontend) target: Handle,
    pub(in crate::render::guirender::frontend) pass: Handle,
    pub(in crate::render::guirender::frontend) usage: TextureUsageState,
}

impl CustomPostEffectIntermediate {
    pub(in crate::render::guirender::frontend) fn handles_in_destroy_order(&self) -> [Handle; 4] {
        [self.pass, self.target, self.view, self.texture]
    }
}

impl CustomPostEffectResources {
    pub(in crate::render::guirender::frontend) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        // Destroy dependents before the resources they reference. In
        // particular, combined samplers retain both the sampler and the
        // snapshot view, while the resource set retains the combined samplers
        // and uniform buffers. The explicit GAL permits deferred retirement,
        // but preserving this order keeps destruction valid for immediate
        // validation and for backends with stricter lifetime checks.
        let mut handles = vec![
            self.pipeline,
            self.pipeline_layout,
            self.resource_set,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
        ];
        handles.extend(self.combined_samplers.iter().copied());
        handles.extend(self.image_upload_buffers.iter().flatten().copied());
        handles.extend(self.image_samplers.iter().flatten().copied());
        handles.extend(self.image_views.iter().flatten().copied());
        handles.extend(self.image_textures.iter().flatten().copied());
        handles.extend(self.uniform_buffers.iter().copied());
        handles.extend(self.target_samplers.iter().copied());
        handles.extend(self.snapshot_views.iter().copied());
        handles.extend(self.snapshots.iter().copied());
        handles.extend(self.frame_copy_scratch);
        handles
    }
}

/// One copied custom post-effect pass. Shader code and static uniform bytes
/// originate in the Rust-owned resource snapshot; no Java uniform object or
/// backend handle crosses this boundary.
pub(crate) struct CustomPostEffectSource {
    /// Framebuffer-derived input row transformation; uploaded images retain
    /// their declared pixel order independently of attachment coordinates.
    pub(crate) input_row_order: crate::render::vulkanic::commands::TextureRowOrder,
    pub(crate) input_bilinear: Vec<bool>,
    pub(crate) sampler_info_uniform: Option<usize>,
    pub(crate) vertex_shader: Vec<u8>,
    pub(crate) fragment_shader: Vec<u8>,
    pub(crate) input_count: usize,
    pub(crate) input_targets: Vec<String>,
    pub(crate) input_images: Vec<Option<CustomPostEffectImage>>,
    pub(crate) input_use_depth: Vec<bool>,
    pub(crate) output_target: String,
    pub(crate) uniform_blocks: Vec<Vec<u8>>,
}

/// Copied semantic pixels for one resource-pack texture input. This type owns
/// no GAL object; upload and sampler lowering happen only in the Rust GUI
/// frontend after the graph has passed admission.
#[derive(Clone)]
pub(crate) struct CustomPostEffectImage {
    pub(crate) path: String,
    pub(crate) width: u32,
    pub(crate) height: u32,
    pub(crate) pixels_rgba8: Vec<u8>,
    pub(crate) bilinear: bool,
}

impl GuiFrontend {
    pub(in crate::render::guirender::frontend) fn ensure_custom_post_effect_resources(
        &mut self,
        gal: &mut VulkanicGal,
        identity: &str,
        pass_index: usize,
        width: u32,
        height: u32,
        color_format: ColorFormat,
        vertex_source: &[u8],
        fragment_source: &[u8],
        input_count: usize,
        input_row_order: crate::render::vulkanic::commands::TextureRowOrder,
        needs_frame_scratch: bool,
        input_bilinear: &[bool],
        input_targets: &[String],
        input_images: &[Option<CustomPostEffectImage>],
        input_depth_views: &[Option<Handle>],
        output_target: &str,
        uniform_blocks: &[Vec<u8>],
    ) -> GalResult<&CustomPostEffectResources> {
        if gal.capabilities().shader_conventions.glsl_dialect != GlslDialect::ExplicitBindings {
            return Err(GalError::unsupported_feature(
                "Rust custom post effects require an explicit-binding GLSL backend",
            ));
        }
        if pass_index >= MAX_CUSTOM_POST_EFFECT_PASSES {
            return Err(GalError::unsupported_feature(format!(
                "custom post-effect pass index exceeds bounded limit {MAX_CUSTOM_POST_EFFECT_PASSES}"
            )));
        }
        if input_depth_views.len() != input_count || input_bilinear.len() != input_count {
            return Err(GalError::invalid_argument(
                "custom post-effect depth-view/filter count does not match semantic input count",
            ));
        }
        let mut source_fingerprint = 0xcbf29ce484222325u64;
        source_fingerprint ^= input_row_order as u64 | ((needs_frame_scratch as u64) << 8);
        for byte in vertex_source.iter().chain(fragment_source.iter()) {
            source_fingerprint ^= *byte as u64;
            source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
        }
        for block in uniform_blocks {
            // Values are rewritten explicitly on each submission. Only the
            // allocation/layout belongs in pipeline-resource cache identity;
            // frame time must not rebuild the graph every frame.
            for byte in block.len().to_le_bytes() {
                source_fingerprint ^= byte as u64;
                source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
            }
        }
        source_fingerprint ^= input_count as u64;
        source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
        for bilinear in input_bilinear {
            source_fingerprint ^= *bilinear as u64;
            source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
        }
        for target in input_targets
            .iter()
            .chain(std::iter::once(&output_target.to_owned()))
        {
            for byte in target.as_bytes() {
                source_fingerprint ^= *byte as u64;
                source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
            }
        }
        for image in input_images.iter().flatten() {
            for byte in image
                .path
                .as_bytes()
                .iter()
                .chain(image.pixels_rgba8.iter())
            {
                source_fingerprint ^= *byte as u64;
                source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
            }
            source_fingerprint ^= image.bilinear as u64;
            source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
        }
        for view in input_depth_views.iter().flatten() {
            source_fingerprint ^= view.raw();
            source_fingerprint = source_fingerprint.wrapping_mul(0x100000001b3);
        }
        let cache_identity = format!("{identity}#{source_fingerprint:016x}");
        if self
            .custom_post_effect_resources
            .get(pass_index)
            .is_some_and(|resources| {
                resources.identity == cache_identity
                    && resources.width == width
                    && resources.height == height
                    && resources.color_format == color_format
            })
        {
            return Ok(&self.custom_post_effect_resources[pass_index]);
        }
        if pass_index == 0 {
            for previous in std::mem::take(&mut self.custom_post_effect_resources) {
                for handle in previous.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
            self.custom_post_effect_snapshot_initialized.clear();
            self.custom_post_effect_image_initialized.clear();
        } else if pass_index < self.custom_post_effect_resources.len() {
            // A changed pass invalidates every later pass: their descriptor
            // bindings and intermediate-target assumptions were compiled
            // against the previous graph suffix. Retire the whole suffix so
            // vector indices remain aligned with the current semantic chain.
            let stale_resources = self
                .custom_post_effect_resources
                .drain(pass_index..)
                .collect::<Vec<_>>();
            for previous in stale_resources {
                for handle in previous.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
            self.custom_post_effect_snapshot_initialized
                .truncate(pass_index);
            self.custom_post_effect_image_initialized
                .truncate(pass_index);
        }
        let label = format!("minecraft.post-effect.{identity}.pass-{pass_index}.{width}x{height}");
        let mut created = Vec::new();
        let result = (|| -> GalResult<CustomPostEffectResources> {
            if input_count == 0 || input_count > 4 {
                return Err(GalError::unsupported_feature(
                    "custom post-effect pass input count must be in the bounded range 1..=4",
                ));
            }
            if input_images.len() != input_count {
                return Err(GalError::invalid_argument(
                    "custom post-effect image-input count does not match semantic input count",
                ));
            }
            let mut snapshots = Vec::with_capacity(input_count);
            let mut snapshot_views = Vec::with_capacity(input_count);
            let frame_copy_scratch = if needs_frame_scratch {
                let texture = gal.create_texture(TextureDesc {
                    label: format!("{label}.frame-copy-scratch"),
                    dimension: TextureDimension::D2,
                    format: color_format,
                    extent: Extent3d {
                        width,
                        height,
                        depth: 1,
                    },
                    mip_levels: 1,
                    array_layers: 1,
                    usages: vec![TextureUsage::TransferSrc, TextureUsage::TransferDst],
                })?;
                created.push(texture);
                Some(texture)
            } else {
                None
            };
            for input_index in 0..input_count {
                let snapshot_format =
                    if input_row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse {
                        input_depth_views[input_index]
                            .map(|view| gal.texture_view_info(view).map(|info| info.format))
                            .transpose()?
                            .unwrap_or(color_format)
                    } else {
                        color_format
                    };
                let snapshot = gal.create_texture(TextureDesc {
                    label: format!("{label}.snapshot-{input_index}"),
                    dimension: TextureDimension::D2,
                    format: snapshot_format,
                    extent: Extent3d {
                        width,
                        height,
                        depth: 1,
                    },
                    mip_levels: 1,
                    array_layers: 1,
                    usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
                })?;
                created.push(snapshot);
                let snapshot_view = gal.create_texture_view(TextureViewDesc {
                    label: format!("{label}.snapshot-view-{input_index}"),
                    texture: snapshot,
                    format: snapshot_format,
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                })?;
                created.push(snapshot_view);
                snapshots.push(snapshot);
                snapshot_views.push(snapshot_view);
            }
            let mut image_textures = vec![None; input_count];
            let mut image_views = vec![None; input_count];
            let mut image_samplers = vec![None; input_count];
            let mut image_upload_buffers = vec![None; input_count];
            for (input_index, image) in input_images.iter().enumerate() {
                let Some(image) = image else { continue };
                let expected_bytes = (image.width as usize)
                    .checked_mul(image.height as usize)
                    .and_then(|pixels| pixels.checked_mul(4))
                    .ok_or_else(|| {
                        GalError::invalid_argument("custom post-effect texture dimensions overflow")
                    })?;
                if image.pixels_rgba8.len() != expected_bytes {
                    return Err(GalError::invalid_argument(format!(
                        "custom post-effect texture '{}' has {} bytes, expected {}",
                        image.path,
                        image.pixels_rgba8.len(),
                        expected_bytes
                    )));
                }
                let upload = gal.create_buffer(BufferDesc {
                    label: format!("{label}.image-{input_index}.upload"),
                    size: expected_bytes as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![
                        BufferUsage::TransferSrc,
                        BufferUsage::TransferDst,
                        BufferUsage::HostWrite,
                    ],
                })?;
                created.push(upload);
                let texture = gal.create_texture(TextureDesc {
                    label: format!("{label}.image-{input_index}.texture"),
                    dimension: TextureDimension::D2,
                    format: TextureFormat::Rgba8Unorm,
                    extent: Extent3d {
                        width: image.width,
                        height: image.height,
                        depth: 1,
                    },
                    mip_levels: 1,
                    array_layers: 1,
                    usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
                })?;
                created.push(texture);
                let view = gal.create_texture_view(TextureViewDesc {
                    label: format!("{label}.image-{input_index}.view"),
                    texture,
                    format: TextureFormat::Rgba8Unorm,
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                })?;
                created.push(view);
                let image_sampler = gal.create_sampler(SamplerDesc {
                    label: format!("{label}.image-{input_index}.sampler"),
                    min_filter: if image.bilinear {
                        SamplerFilter::Linear
                    } else {
                        SamplerFilter::Nearest
                    },
                    mag_filter: if image.bilinear {
                        SamplerFilter::Linear
                    } else {
                        SamplerFilter::Nearest
                    },
                    mip_filter: SamplerFilter::Nearest,
                    address_u: SamplerAddressMode::ClampToEdge,
                    address_v: SamplerAddressMode::ClampToEdge,
                    address_w: SamplerAddressMode::ClampToEdge,
                    comparison: None,
                })?;
                created.push(image_sampler);
                image_upload_buffers[input_index] = Some(upload);
                image_textures[input_index] = Some(texture);
                image_views[input_index] = Some(view);
                image_samplers[input_index] = Some(image_sampler);
            }
            let mut combined_samplers = Vec::with_capacity(input_count);
            let mut target_samplers = Vec::new();
            for input_index in 0..input_count {
                let sampler = if let Some(sampler) = image_samplers[input_index] {
                    sampler
                } else {
                    let filter = if input_bilinear[input_index] {
                        SamplerFilter::Linear
                    } else {
                        SamplerFilter::Nearest
                    };
                    let sampler = gal.create_sampler(SamplerDesc {
                        label: format!("{label}.target-{input_index}.sampler"),
                        min_filter: filter,
                        mag_filter: filter,
                        mip_filter: SamplerFilter::Nearest,
                        address_u: SamplerAddressMode::ClampToEdge,
                        address_v: SamplerAddressMode::ClampToEdge,
                        address_w: SamplerAddressMode::ClampToEdge,
                        comparison: None,
                    })?;
                    created.push(sampler);
                    target_samplers.push(sampler);
                    sampler
                };
                let (texture_view, input_sampler) = match (
                    input_depth_views[input_index],
                    image_views[input_index],
                    image_samplers[input_index],
                ) {
                    (Some(depth_view), _, _) => (
                        if input_row_order == crate::render::vulkanic::commands::TextureRowOrder::Reverse {
                            snapshot_views[input_index]
                        } else {
                            depth_view
                        },
                        sampler,
                    ),
                    (None, Some(view), Some(input_sampler)) => (view, input_sampler),
                    (None, _, _) => (snapshot_views[input_index], sampler),
                };
                let combined_sampler =
                    gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                        label: format!("{label}.combined-sampler-{input_index}"),
                        texture_view,
                        sampler: input_sampler,
                    })?;
                created.push(combined_sampler);
                combined_samplers.push(combined_sampler);
            }
            if uniform_blocks.len() > 4 {
                return Err(GalError::unsupported_feature(
                    "custom post-effect pass supports at most four static uniform blocks",
                ));
            }
            let mut uniform_buffers = Vec::with_capacity(uniform_blocks.len());
            for (block_index, bytes) in uniform_blocks.iter().enumerate() {
                let uniform_buffer = gal.create_buffer(BufferDesc {
                    label: format!("{label}.uniform-{block_index}"),
                    size: bytes.len().max(16) as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![
                        BufferUsage::Uniform,
                        BufferUsage::TransferDst,
                        BufferUsage::HostWrite,
                    ],
                })?;
                created.push(uniform_buffer);
                uniform_buffers.push(uniform_buffer);
            }
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: vertex_source.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: fragment_source.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: {
                    let mut bindings = (0..input_count)
                        .map(|input_index| ResourceBindingDesc {
                            binding: input_index as u32,
                            kind: ResourceBindingKind::CombinedTextureSampler,
                            stages: PipelineStageFlags::DRAW,
                            array_count: 1,
                            optional: false,
                            dynamic_offset_count: 0,
                        })
                        .collect::<Vec<_>>();
                    bindings.extend((0..uniform_blocks.len()).map(|block_index| {
                        ResourceBindingDesc {
                            binding: input_count as u32 + block_index as u32,
                            kind: ResourceBindingKind::UniformBuffer,
                            stages: PipelineStageFlags::DRAW,
                            array_count: 1,
                            optional: false,
                            dynamic_offset_count: 0,
                        }
                    }));
                    bindings
                },
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: {
                    let mut bindings = combined_samplers
                        .iter()
                        .enumerate()
                        .map(|(input_index, combined_sampler)| ResourceBinding {
                            binding: input_index as u32,
                            array_index: 0,
                            resource: *combined_sampler,
                            kind: ResourceBindingKind::CombinedTextureSampler,
                            access: AccessFlags::READ,
                            dynamic_offsets: Vec::new(),
                            buffer_range: None,
                        })
                        .collect::<Vec<_>>();
                    bindings.extend(
                            uniform_buffers
                                .iter()
                                .enumerate()
                                .map(|(block_index, buffer)| ResourceBinding {
                                    binding: input_count as u32 + block_index as u32,
                                    array_index: 0,
                                    resource: *buffer,
                                    kind: ResourceBindingKind::UniformBuffer,
                                    access: AccessFlags::READ,
                                    dynamic_offsets: Vec::new(),
                                    buffer_range: Some(
                                        uniform_blocks[block_index].len().max(16) as u64
                                    ),
                                }),
                        );
                    bindings
                },
            })?;
            created.push(resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend: BlendMode::Disabled,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: None,
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(CustomPostEffectResources {
                frame_copy_scratch,
                identity: cache_identity,
                width,
                height,
                color_format,
                snapshots,
                snapshot_views,
                target_samplers,
                image_textures,
                image_views,
                image_samplers,
                image_upload_buffers,
                combined_samplers,
                uniform_buffers,
                vertex_shader,
                fragment_shader,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.custom_post_effect_resources.push(result?);
        self.custom_post_effect_snapshot_initialized
            .push(vec![false; input_count]);
        self.custom_post_effect_image_initialized
            .push(vec![false; input_count]);
        Ok(self.custom_post_effect_resources.last().unwrap())
    }

    pub(in crate::render::guirender::frontend) fn ensure_custom_post_effect_intermediate(
        &mut self,
        gal: &mut VulkanicGal,
        identity: &str,
        target_name: &str,
        width: u32,
        height: u32,
        color_format: ColorFormat,
    ) -> GalResult<Option<(Handle, Handle, Handle, Handle)>> {
        if !self
            .custom_post_effect_intermediates
            .contains_key(target_name)
            && self.custom_post_effect_intermediates.len() >= MAX_CUSTOM_POST_EFFECT_INTERMEDIATES
        {
            return Err(GalError::unsupported_feature(format!(
                "custom post-effect intermediate cache exceeds bounded limit {MAX_CUSTOM_POST_EFFECT_INTERMEDIATES}"
            )));
        }
        let cache_identity = format!("{identity}#{target_name}");
        if let Some(existing) = self.custom_post_effect_intermediates.get(target_name) {
            if existing.identity == cache_identity
                && existing.width == width
                && existing.height == height
                && existing.color_format == color_format
            {
                return Ok(Some((
                    existing.texture,
                    existing.view,
                    existing.target,
                    existing.pass,
                )));
            }
        }
        if let Some(previous) = self.custom_post_effect_intermediates.remove(target_name) {
            for handle in previous.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let label =
            format!("minecraft.post-effect.{identity}.intermediate.{target_name}.{width}x{height}");
        let mut created = Vec::new();
        let result = (|| -> GalResult<CustomPostEffectIntermediate> {
            let texture = gal.create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::Sampled,
                    TextureUsage::ColorAttachment,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(texture);
            let view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![view],
                depth_stencil_view: None,
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
            })?;
            created.push(target);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: vec![color_format],
                depth_format: None,
            })?;
            created.push(pass);
            Ok(CustomPostEffectIntermediate {
                identity: cache_identity,
                width,
                height,
                color_format,
                texture,
                view,
                target,
                pass,
                usage: TextureUsageState::Undefined,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        let intermediate = result?;
        let handles = (
            intermediate.texture,
            intermediate.view,
            intermediate.target,
            intermediate.pass,
        );
        self.custom_post_effect_intermediates
            .insert(target_name.to_owned(), intermediate);
        Ok(Some(handles))
    }
}

pub(in crate::render::guirender::frontend) fn custom_sampler_info_bytes(
    extent: Extent3d,
    images: &[Option<CustomPostEffectImage>],
) -> Vec<u8> {
    let mut sizes = vec![extent.width as f32, extent.height as f32];
    for image in images {
        sizes.extend(
            image
                .as_ref()
                .map(|image| [image.width as f32, image.height as f32])
                .unwrap_or([extent.width as f32, extent.height as f32]),
        );
    }
    let mut data = sizes
        .into_iter()
        .flat_map(f32::to_le_bytes)
        .collect::<Vec<_>>();
    data.resize((data.len() + 15) / 16 * 16, 0);
    data
}

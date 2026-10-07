//! The menu background blur: boundary planning, resources and passes.

use super::*;

pub(in crate::render::guirender::frontend) struct GuiBlurResources {
    pub(in crate::render::guirender::frontend) width: u32,
    pub(in crate::render::guirender::frontend) height: u32,
    pub(in crate::render::guirender::frontend) color_format: ColorFormat,
    pub(in crate::render::guirender::frontend) texture: Handle,
    pub(in crate::render::guirender::frontend) creeper_texture: Handle,
    pub(in crate::render::guirender::frontend) creeper_view: Handle,
    pub(in crate::render::guirender::frontend) creeper_target: Handle,
    pub(in crate::render::guirender::frontend) creeper_pass: Handle,
    pub(in crate::render::guirender::frontend) uniform_buffer: Handle,
    pub(in crate::render::guirender::frontend) texture_view: Handle,
    pub(in crate::render::guirender::frontend) sampler: Handle,
    pub(in crate::render::guirender::frontend) vertex_shader: Handle,
    pub(in crate::render::guirender::frontend) fragment_shader: Handle,
    pub(in crate::render::guirender::frontend) invert_fragment_shader: Handle,
    pub(in crate::render::guirender::frontend) creeper_color_shader: Handle,
    pub(in crate::render::guirender::frontend) creeper_bits_shader: Handle,
    pub(in crate::render::guirender::frontend) resource_layout: Handle,
    pub(in crate::render::guirender::frontend) resource_set: Handle,
    pub(in crate::render::guirender::frontend) creeper_resource_set: Handle,
    pub(in crate::render::guirender::frontend) pipeline_layout: Handle,
    pub(in crate::render::guirender::frontend) pipeline: Handle,
    pub(in crate::render::guirender::frontend) invert_pipeline: Handle,
    pub(in crate::render::guirender::frontend) creeper_color_pipeline: Handle,
    pub(in crate::render::guirender::frontend) creeper_bits_pipeline: Handle,
    pub(in crate::render::guirender::frontend) spider_textures: [Handle; 4],
    pub(in crate::render::guirender::frontend) spider_views: [Handle; 4],
    pub(in crate::render::guirender::frontend) spider_targets: [Handle; 4],
    pub(in crate::render::guirender::frontend) spider_passes: [Handle; 4],
    pub(in crate::render::guirender::frontend) spider_single_sets: [Handle; 4],
    pub(in crate::render::guirender::frontend) spider_dual_sets: [Handle; 3],
    pub(in crate::render::guirender::frontend) spider_dual_layout: Handle,
    pub(in crate::render::guirender::frontend) spider_dual_pipeline_layout: Handle,
    pub(in crate::render::guirender::frontend) spider_box_shader: Handle,
    pub(in crate::render::guirender::frontend) spider_rot_shader: Handle,
    pub(in crate::render::guirender::frontend) spider_clip_shader: Handle,
    pub(in crate::render::guirender::frontend) spider_blit_shader: Handle,
    pub(in crate::render::guirender::frontend) spider_box_pipeline: Handle,
    pub(in crate::render::guirender::frontend) spider_clip_pipeline: Handle,
    pub(in crate::render::guirender::frontend) spider_blit_pipeline: Handle,
}

impl GuiBlurResources {
    pub(in crate::render::guirender::frontend) fn handles_in_destroy_order(&self) -> Vec<Handle> {
        let mut handles = vec![
            self.spider_blit_pipeline,
            self.spider_clip_pipeline,
            self.spider_box_pipeline,
            self.spider_dual_pipeline_layout,
            self.spider_dual_sets[0],
            self.spider_dual_sets[1],
            self.spider_dual_sets[2],
            self.spider_single_sets[0],
            self.spider_single_sets[1],
            self.spider_single_sets[2],
            self.spider_single_sets[3],
            self.spider_dual_layout,
            self.creeper_pass,
            self.creeper_target,
            self.creeper_bits_pipeline,
            self.creeper_color_pipeline,
            self.pipeline,
            self.invert_pipeline,
            self.pipeline_layout,
            self.creeper_resource_set,
            self.resource_set,
            self.resource_layout,
            self.texture_view,
            self.fragment_shader,
            self.invert_fragment_shader,
            self.creeper_bits_shader,
            self.creeper_color_shader,
            self.vertex_shader,
            self.sampler,
            self.uniform_buffer,
            self.texture,
            self.creeper_view,
            self.creeper_texture,
        ];
        handles.extend(self.spider_passes);
        handles.extend(self.spider_targets);
        handles.extend(self.spider_views);
        handles.extend(self.spider_textures);
        handles.extend([
            self.spider_rot_shader,
            self.spider_clip_shader,
            self.spider_blit_shader,
            self.spider_box_shader,
        ]);
        handles
    }
}

/// Semantic ordering plan for the screen-background blur boundary. Requests
/// from a render-state node use `node * 3 + phase` as their order; fixed
/// compatibility strata use larger orders and remain after a dynamic node
/// boundary. This plan contains counts only—no Java renderer or backend
/// resource can cross it.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct GuiBlurBoundaryPlan {
    pub boundary_stratum: u32,
    pub before_count: usize,
    pub after_count: usize,
}

impl GuiBlurBoundaryPlan {
    pub(in crate::render::guirender::frontend) fn phase_threshold(self) -> GalResult<u32> {
        self.boundary_stratum.checked_mul(3).ok_or_else(|| {
            GalError::invalid_argument("GUI blur boundary stratum overflows phase order")
        })
    }
}

pub(crate) fn plan_gui_blur_boundary(
    boundary_stratum: i32,
    request_strata: impl IntoIterator<Item = u32>,
) -> GalResult<GuiBlurBoundaryPlan> {
    let boundary_stratum = u32::try_from(boundary_stratum).map_err(|_| {
        GalError::invalid_argument("GUI blur boundary must be non-negative when planned")
    })?;
    let threshold = boundary_stratum.checked_mul(3).ok_or_else(|| {
        GalError::invalid_argument("GUI blur boundary stratum overflows phase order")
    })?;
    let mut before_count = 0usize;
    let mut after_count = 0usize;
    for stratum in request_strata {
        if stratum < threshold {
            before_count = before_count.saturating_add(1);
        } else {
            after_count = after_count.saturating_add(1);
        }
    }
    Ok(GuiBlurBoundaryPlan {
        boundary_stratum,
        before_count,
        after_count,
    })
}

impl GuiFrontend {
    pub(in crate::render::guirender::frontend) fn ensure_blur_resources(
        &mut self,
        gal: &mut VulkanicGal,
        width: u32,
        height: u32,
        color_format: ColorFormat,
    ) -> GalResult<&GuiBlurResources> {
        if gal.capabilities().shader_conventions.glsl_dialect != GlslDialect::ExplicitBindings {
            return Err(GalError::unsupported_feature(
                "Rust GUI blur requires an explicit-binding GLSL backend",
            ));
        }
        if self.blur_resources.as_ref().is_some_and(|resources| {
            resources.width == width
                && resources.height == height
                && resources.color_format == color_format
        }) {
            return Ok(self.blur_resources.as_ref().unwrap());
        }
        if let Some(previous) = self.blur_resources.take() {
            for handle in previous.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
            self.blur_snapshot_initialized = false;
            self.creeper_intermediate_initialized = false;
            self.spider_initialized = [false; 4];
        }
        let label = format!("minecraft.gui.blur.{}x{}", width, height);
        let mut created = Vec::new();
        let result = (|| -> GalResult<GuiBlurResources> {
            let texture = gal.create_texture(TextureDesc {
                label: format!("{label}.snapshot"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(texture);
            let texture_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.snapshot-view"),
                texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(texture_view);
            let creeper_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.creeper-intermediate"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::ColorAttachment],
            })?;
            created.push(creeper_texture);
            let creeper_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.creeper-intermediate-view"),
                texture: creeper_texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(creeper_view);
            let creeper_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.creeper-intermediate-target"),
                color_views: vec![creeper_view],
                depth_stencil_view: None,
                extent: Extent3d {
                    width,
                    height,
                    depth: 1,
                },
            })?;
            created.push(creeper_target);
            let creeper_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.creeper-intermediate-pass"),
                target: creeper_target,
                color_formats: vec![color_format],
                depth_format: None,
            })?;
            created.push(creeper_pass);
            let mut spider_textures = Vec::with_capacity(4);
            let mut spider_views = Vec::with_capacity(4);
            let mut spider_targets = Vec::with_capacity(4);
            let mut spider_passes = Vec::with_capacity(4);
            for name in ["large-blur", "small-blur", "temp", "swap"] {
                let texture = gal.create_texture(TextureDesc {
                    label: format!("{label}.spider-{name}"),
                    dimension: TextureDimension::D2,
                    format: color_format,
                    extent: Extent3d {
                        width,
                        height,
                        depth: 1,
                    },
                    mip_levels: 1,
                    array_layers: 1,
                    usages: vec![TextureUsage::Sampled, TextureUsage::ColorAttachment],
                })?;
                created.push(texture);
                let view = gal.create_texture_view(TextureViewDesc {
                    label: format!("{label}.spider-{name}-view"),
                    texture,
                    format: color_format,
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                })?;
                created.push(view);
                let target = gal.create_render_target(RenderTargetDesc {
                    label: format!("{label}.spider-{name}-target"),
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
                    label: format!("{label}.spider-{name}-pass"),
                    target,
                    color_formats: vec![color_format],
                    depth_format: None,
                })?;
                created.push(pass);
                spider_textures.push(texture);
                spider_views.push(view);
                spider_targets.push(target);
                spider_passes.push(pass);
            }
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: 64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: SamplerFilter::Linear,
                mag_filter: SamplerFilter::Linear,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: BLUR_VERTEX_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: BLUR_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let invert_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.invert-fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: INVERT_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(invert_fragment_shader);
            let creeper_color_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.creeper-color-fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: CREEPER_COLOR_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(creeper_color_shader);
            let creeper_bits_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.creeper-bits-fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: CREEPER_BITS_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(creeper_bits_shader);
            let spider_box_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.spider-box-blur"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: SPIDER_BOX_BLUR_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(spider_box_shader);
            let spider_rot_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.spider-rot-scale"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: SPIDER_ROT_SCALE_VERTEX_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(spider_rot_shader);
            let spider_clip_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.spider-clip"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: SPIDER_CLIP_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(spider_clip_shader);
            let spider_blit_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.spider-blit"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: SPIDER_BLIT_FRAGMENT_SHADER_VULKAN.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(spider_blit_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 2,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: texture_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(64),
                    },
                ],
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
            let creeper_resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.creeper-resource-set"),
                layout: resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: creeper_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(64),
                    },
                ],
            })?;
            created.push(creeper_resource_set);
            let single_views = [
                texture_view,
                spider_views[2],
                spider_views[1],
                spider_views[3],
            ];
            let mut spider_single_sets = Vec::with_capacity(4);
            for (index, view) in single_views.into_iter().enumerate() {
                let set = gal.create_resource_set(ResourceSetDesc {
                    label: format!("{label}.spider-single-set-{index}"),
                    layout: resource_layout,
                    bindings: vec![
                        ResourceBinding {
                            binding: 0,
                            array_index: 0,
                            resource: view,
                            kind: ResourceBindingKind::SampledTexture,
                            access: AccessFlags::READ,
                            dynamic_offsets: Vec::new(),
                            buffer_range: None,
                        },
                        ResourceBinding {
                            binding: 1,
                            array_index: 0,
                            resource: sampler,
                            kind: ResourceBindingKind::Sampler,
                            access: AccessFlags::READ,
                            dynamic_offsets: Vec::new(),
                            buffer_range: None,
                        },
                        ResourceBinding {
                            binding: 2,
                            array_index: 0,
                            resource: uniform_buffer,
                            kind: ResourceBindingKind::UniformBuffer,
                            access: AccessFlags::READ,
                            dynamic_offsets: Vec::new(),
                            buffer_range: Some(64),
                        },
                    ],
                })?;
                created.push(set);
                spider_single_sets.push(set);
            }
            let spider_dual_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.spider-dual-layout"),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 2,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 3,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ],
            })?;
            created.push(spider_dual_layout);
            let dual_inputs = [
                (texture_view, spider_views[0]),
                (spider_views[1], spider_views[2]),
                (spider_views[1], spider_views[3]),
            ];
            let mut spider_dual_sets = Vec::with_capacity(3);
            for (index, (input, blur)) in dual_inputs.into_iter().enumerate() {
                let set = gal.create_resource_set(ResourceSetDesc {
                    label: format!("{label}.spider-dual-set-{index}"),
                    layout: spider_dual_layout,
                    bindings: vec![
                        ResourceBinding {
                            binding: 0,
                            array_index: 0,
                            resource: input,
                            kind: ResourceBindingKind::SampledTexture,
                            access: AccessFlags::READ,
                            dynamic_offsets: Vec::new(),
                            buffer_range: None,
                        },
                        ResourceBinding {
                            binding: 1,
                            array_index: 0,
                            resource: blur,
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
                        ResourceBinding {
                            binding: 3,
                            array_index: 0,
                            resource: uniform_buffer,
                            kind: ResourceBindingKind::UniformBuffer,
                            access: AccessFlags::READ,
                            dynamic_offsets: Vec::new(),
                            buffer_range: Some(64),
                        },
                    ],
                })?;
                created.push(set);
                spider_dual_sets.push(set);
            }
            let invert_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.invert-pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: invert_fragment_shader,
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
            created.push(invert_pipeline);
            let creeper_color_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.creeper-color-pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: creeper_color_shader,
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
            created.push(creeper_color_pipeline);
            let creeper_bits_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.creeper-bits-pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: creeper_bits_shader,
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
            created.push(creeper_bits_pipeline);
            let spider_dual_pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.spider-dual-pipeline-layout"),
                resource_layouts: vec![spider_dual_layout],
            })?;
            created.push(spider_dual_pipeline_layout);
            let spider_box_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.spider-box-pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: spider_box_shader,
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
            created.push(spider_box_pipeline);
            let spider_clip_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.spider-clip-pipeline"),
                layout: spider_dual_pipeline_layout,
                vertex_shader: spider_rot_shader,
                fragment_shader: spider_clip_shader,
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
            created.push(spider_clip_pipeline);
            let spider_blit_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.spider-blit-pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: spider_blit_shader,
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
            created.push(spider_blit_pipeline);
            Ok(GuiBlurResources {
                width,
                height,
                color_format,
                texture,
                creeper_texture,
                creeper_view,
                creeper_target,
                creeper_pass,
                uniform_buffer,
                texture_view,
                sampler,
                vertex_shader,
                fragment_shader,
                invert_fragment_shader,
                creeper_color_shader,
                creeper_bits_shader,
                resource_layout,
                resource_set,
                creeper_resource_set,
                pipeline_layout,
                pipeline,
                invert_pipeline,
                creeper_color_pipeline,
                creeper_bits_pipeline,
                spider_textures: spider_textures.try_into().expect("four spider textures"),
                spider_views: spider_views.try_into().expect("four spider views"),
                spider_targets: spider_targets.try_into().expect("four spider targets"),
                spider_passes: spider_passes.try_into().expect("four spider passes"),
                spider_single_sets: spider_single_sets.try_into().expect("four spider sets"),
                spider_dual_sets: spider_dual_sets.try_into().expect("three spider dual sets"),
                spider_dual_layout,
                spider_dual_pipeline_layout,
                spider_box_shader,
                spider_rot_shader,
                spider_clip_shader,
                spider_blit_shader,
                spider_box_pipeline,
                spider_clip_pipeline,
                spider_blit_pipeline,
            })
        })();
        match result {
            Ok(resources) => {
                self.blur_resources = Some(resources);
                Ok(self.blur_resources.as_ref().unwrap())
            }
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.retire(handle);
                }
                Err(error)
            }
        }
    }
}

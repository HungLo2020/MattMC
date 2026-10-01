//! Direct DH composition: resolved color, SSAO, fade and apply passes.

use crate::render::worldrender::lod::*;

/// Rust-owned framebuffer boundary for the ordinary DH route.  The direct
/// path renders every DH material stream into these private color/depth
/// attachments, then samples them in one explicit fullscreen composite.  It
/// never aliases the vanilla depth attachment or a shader-pack target.
pub(crate) struct WorldLodDirectCompositionResources {
    pub color_texture: Handle,
    pub color_view: Handle,
    pub depth_texture: Handle,
    pub depth_view: Handle,
    /// DH color after source-owned SSAO, far fade and fog. Frozen preserves
    /// this image across the early apply and all later vanilla fade passes.
    pub resolved_color_texture: Handle,
    pub resolved_color_view: Handle,
    /// Snapshot of the completed vanilla color target used by the explicit
    /// MC/DH transition pass.  The acquired frame target is intentionally
    /// opaque and cannot be sampled directly.
    pub vanilla_color_texture: Handle,
    pub vanilla_color_view: Handle,
    /// Snapshot of the completed vanilla depth attachment used to reconstruct
    /// the source MC fragment distance for the fade contract.
    pub vanilla_depth_texture: Handle,
    pub vanilla_depth_view: Handle,
    pub target: Handle,
    pub pass: Handle,
    pub(in crate::render::worldrender::lod) resolved_target: Handle,
    pub(in crate::render::worldrender::lod) resolved_pass: Handle,
    pub(in crate::render::worldrender::lod) color_sampler: Handle,
    pub(in crate::render::worldrender::lod) depth_sampler: Handle,
    pub(in crate::render::worldrender::lod) ssao_sampler: Handle,
    pub(in crate::render::worldrender::lod) uniform_buffer: Handle,
    pub(in crate::render::worldrender::lod) ssao_uniform_buffer: Handle,
    pub(in crate::render::worldrender::lod) resource_layout: Handle,
    pub(in crate::render::worldrender::lod) ssao_resource_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline_layout: Handle,
    pub(in crate::render::worldrender::lod) ssao_pipeline_layout: Handle,
    pub(in crate::render::worldrender::lod) resource_set: Handle,
    pub(in crate::render::worldrender::lod) resolved_resource_set: Handle,
    pub(in crate::render::worldrender::lod) ssao_resource_set: Handle,
    pub(in crate::render::worldrender::lod) vertex_shader: Handle,
    pub(in crate::render::worldrender::lod) fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) pipeline: Handle,
    pub(in crate::render::worldrender::lod) apply_fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) apply_pipeline: Handle,
    pub(in crate::render::worldrender::lod) fade_fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) fade_pipeline: Handle,
    pub(in crate::render::worldrender::lod) ssao_fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) ssao_pipeline: Handle,
    pub(in crate::render::worldrender::lod) ssao_texture: Handle,
    pub(in crate::render::worldrender::lod) ssao_view: Handle,
    pub(in crate::render::worldrender::lod) ssao_target: Handle,
    pub(in crate::render::worldrender::lod) ssao_pass: Handle,
    pub extent: Extent3d,
    pub color_format: TextureFormat,
    pub raster_y_direction: RasterYDirection,
}

// Frozen uses a 16-bit intermediate for DH fog and fade before applying the
// result to Minecraft's 8-bit target. Keep the resolved image equally
// independent from the presentation format so later transition passes do not
// accumulate an extra 8-bit quantization step.
pub(super) const WORLD_LOD_RESOLVED_COLOR_FORMAT: TextureFormat = TextureFormat::Rgba16Float;

impl WorldLodDirectCompositionResources {
    /// Exposes the sparse pre-composite DH color only to the bounded
    /// whole-frame diagnostic. Its alpha channel is the render target's
    /// semantic geometry-coverage marker, so the capture can attribute final
    /// pixels without changing the production compositor or sharing this
    /// private image with another renderer.
    pub(crate) fn private_color_texture(&self) -> Handle {
        self.color_texture
    }

    /// Exposes the fog/fade-resolved DH image only to the bounded whole-frame
    /// diagnostic. Production composition continues to consume it through
    /// the private resource set.
    pub(crate) fn resolved_color_texture(&self) -> Handle {
        self.resolved_color_texture
    }

    /// Exposes the owned AO result only to the bounded whole-frame diagnostic.
    /// Production composition continues to consume it through the private
    /// resource set rather than sharing a backend object across renderers.
    pub(crate) fn ssao_texture(&self) -> Handle {
        self.ssao_texture
    }

    pub(crate) fn create(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<Self> {
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "direct DH composition target requires a non-zero 2D extent",
            ));
        }
        let label = format!(
            "world-lod-direct-composition-{}x{}",
            extent.width, extent.height
        );
        let resolved_color_format = WORLD_LOD_RESOLVED_COLOR_FORMAT;
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let color_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.color.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(color_texture);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.color.view"),
                texture: color_texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let depth_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.depth.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::DepthStencilAttachment, TextureUsage::Sampled],
            })?;
            created.push(depth_texture);
            let depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.depth.view"),
                texture: depth_texture,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(depth_view);
            let resolved_color_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.resolved-color.texture"),
                dimension: TextureDimension::D2,
                format: resolved_color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(resolved_color_texture);
            let resolved_color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.resolved-color.view"),
                texture: resolved_color_texture,
                format: resolved_color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(resolved_color_view);
            let vanilla_color_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.vanilla-color.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(vanilla_color_texture);
            let vanilla_color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.vanilla-color.view"),
                texture: vanilla_color_texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(vanilla_color_view);
            let vanilla_depth_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.vanilla-depth.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })?;
            created.push(vanilla_depth_texture);
            let vanilla_depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.vanilla-depth.view"),
                texture: vanilla_depth_texture,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(vanilla_depth_view);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views: vec![color_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(target);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            })?;
            created.push(pass);
            let resolved_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.resolved.target"),
                color_views: vec![resolved_color_view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(resolved_target);
            let resolved_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.resolved.pass"),
                target: resolved_target,
                color_formats: vec![resolved_color_format],
                depth_format: None,
            })?;
            created.push(resolved_pass);
            let color_sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.color.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(color_sampler);
            let depth_sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.depth.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(depth_sampler);
            let ssao_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.ssao.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba16Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(ssao_texture);
            let ssao_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.ssao.view"),
                texture: ssao_texture,
                format: TextureFormat::Rgba16Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(ssao_view);
            let ssao_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.ssao.target"),
                color_views: vec![ssao_view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(ssao_target);
            let ssao_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.ssao.pass"),
                target: ssao_target,
                color_formats: vec![TextureFormat::Rgba16Float],
                depth_format: None,
            })?;
            created.push(ssao_pass);
            let ssao_sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.ssao.sampler"),
                min_filter: SamplerFilter::Linear,
                mag_filter: SamplerFilter::Linear,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(ssao_sampler);
            let ssao_uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.ssao.uniform"),
                size: 160,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(ssao_uniform_buffer);
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: 368,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
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
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 3,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 4,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 5,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 6,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 7,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 8,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 9,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 10,
                        kind: ResourceBindingKind::Sampler,
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
                        resource: color_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: color_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: depth_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 3,
                        array_index: 0,
                        resource: depth_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 4,
                        array_index: 0,
                        resource: vanilla_color_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 5,
                        array_index: 0,
                        resource: color_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 6,
                        array_index: 0,
                        resource: vanilla_depth_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 7,
                        array_index: 0,
                        resource: depth_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 8,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(368),
                    },
                    ResourceBinding {
                        binding: 9,
                        array_index: 0,
                        resource: ssao_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 10,
                        array_index: 0,
                        resource: ssao_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                ],
            })?;
            created.push(resource_set);
            let resolved_resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resolved.resource-set"),
                layout: resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: resolved_color_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: color_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: depth_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 3,
                        array_index: 0,
                        resource: depth_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 4,
                        array_index: 0,
                        resource: vanilla_color_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 5,
                        array_index: 0,
                        resource: color_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 6,
                        array_index: 0,
                        resource: vanilla_depth_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 7,
                        array_index: 0,
                        resource: depth_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 8,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(368),
                    },
                    ResourceBinding {
                        binding: 9,
                        array_index: 0,
                        resource: ssao_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 10,
                        array_index: 0,
                        resource: ssao_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                ],
            })?;
            created.push(resolved_resource_set);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![resource_layout],
            })?;
            created.push(pipeline_layout);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_VERTEX,
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT,
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.resolve.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend: private_dh_compositor_blend_mode(),
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![resolved_color_format],
                depth_format: None,
                stencil: None,
            })?;
            created.push(pipeline);
            let apply_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.apply.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    MINIMAL_DISTANT_HORIZONS_DIRECT_APPLY_FRAGMENT,
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(apply_fragment_shader);
            let apply_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.apply.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: apply_fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend: private_dh_compositor_blend_mode(),
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(apply_pipeline);
            let fade_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fade.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT,
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(fade_fragment_shader);
            let fade_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.fade.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader: fade_fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend: private_dh_compositor_blend_mode(),
                depth_compare: private_dh_compositor_depth_compare(),
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(fade_pipeline);
            let ssao_resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.ssao.resource-layout"),
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
            created.push(ssao_resource_layout);
            let ssao_resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.ssao.resource-set"),
                layout: ssao_resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: depth_view,
                        kind: ResourceBindingKind::SampledTexture,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: depth_sampler,
                        kind: ResourceBindingKind::Sampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: ssao_uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(160),
                    },
                ],
            })?;
            created.push(ssao_resource_set);
            let ssao_pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.ssao.pipeline-layout"),
                resource_layouts: vec![ssao_resource_layout],
            })?;
            created.push(ssao_pipeline_layout);
            let ssao_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.ssao.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    MINIMAL_DISTANT_HORIZONS_SSAO_FRAGMENT,
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(ssao_fragment_shader);
            let ssao_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.ssao.pipeline"),
                layout: ssao_pipeline_layout,
                vertex_shader,
                fragment_shader: ssao_fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend: private_dh_compositor_blend_mode(),
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![TextureFormat::Rgba16Float],
                depth_format: None,
                stencil: None,
            })?;
            created.push(ssao_pipeline);
            Ok(Self {
                color_texture,
                color_view,
                depth_texture,
                depth_view,
                resolved_color_texture,
                resolved_color_view,
                vanilla_color_texture,
                vanilla_color_view,
                vanilla_depth_texture,
                vanilla_depth_view,
                target,
                pass,
                resolved_target,
                resolved_pass,
                color_sampler,
                depth_sampler,
                ssao_sampler,
                uniform_buffer,
                ssao_uniform_buffer,
                resource_layout,
                ssao_resource_layout,
                pipeline_layout,
                ssao_pipeline_layout,
                resource_set,
                resolved_resource_set,
                ssao_resource_set,
                vertex_shader,
                fragment_shader,
                pipeline,
                apply_fragment_shader,
                apply_pipeline,
                fade_fragment_shader,
                fade_pipeline,
                ssao_fragment_shader,
                ssao_pipeline,
                ssao_texture,
                ssao_view,
                ssao_target,
                ssao_pass,
                extent,
                color_format,
                raster_y_direction,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.fade_pipeline,
            self.apply_pipeline,
            self.pipeline,
            self.ssao_pipeline,
            self.ssao_fragment_shader,
            self.fade_fragment_shader,
            self.apply_fragment_shader,
            self.fragment_shader,
            self.vertex_shader,
            self.ssao_pipeline_layout,
            self.pipeline_layout,
            self.ssao_resource_set,
            self.resolved_resource_set,
            self.resource_set,
            self.ssao_resource_layout,
            self.resource_layout,
            self.ssao_uniform_buffer,
            self.uniform_buffer,
            self.ssao_sampler,
            self.depth_sampler,
            self.color_sampler,
            self.ssao_pass,
            self.ssao_target,
            self.ssao_view,
            self.ssao_texture,
            self.pass,
            self.target,
            self.resolved_pass,
            self.resolved_target,
            self.resolved_color_view,
            self.resolved_color_texture,
            self.depth_view,
            self.depth_texture,
            self.vanilla_depth_view,
            self.vanilla_depth_texture,
            self.vanilla_color_view,
            self.vanilla_color_texture,
            self.color_view,
            self.color_texture,
        ] {
            let _ = gal.destroy(handle);
        }
    }

    pub(crate) fn append_begin(
        &self,
        previous_color: TextureUsageState,
        previous_depth: TextureUsageState,
        source_clear_color: [f32; 4],
        ops: &mut Vec<CommandOp>,
    ) {
        ops.push(CommandOp::Barrier(texture_barrier(
            self.color_texture,
            previous_color,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.depth_texture,
            previous_depth,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: self.pass,
            target: self.target,
            colors: vec![PassAttachment {
                view: self.color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    // Frozen clears DH's private color target with the active
                    // Minecraft clear RGB and a zero alpha. The RGB remains
                    // observable wherever DH's transparent streams blend
                    // before the fog/apply passes, so transparent black is
                    // not an equivalent initialization.
                    r: source_clear_color[0],
                    g: source_clear_color[1],
                    b: source_clear_color[2],
                    a: 0.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: self.depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
    }

    pub(crate) fn append_end(&self, ops: &mut Vec<CommandOp>) {
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            self.color_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
    }

    /// Resume the private DH target after SSAO has sampled its first depth
    /// phase. Frozen draws non-SSAO generic objects and transparent LODs after
    /// SSAO without clearing either attachment.
    pub(crate) fn append_resume(&self, ops: &mut Vec<CommandOp>) {
        ops.push(CommandOp::Barrier(texture_barrier(
            self.color_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.depth_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: self.pass,
            target: self.target,
            colors: vec![PassAttachment {
                view: self.color_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: Some(PassAttachment {
                view: self.depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
    }

    /// Keep the optional vanilla bindings in a legal sampled state on the
    /// no-fade path.  The fragment shader branches before sampling them, so a
    /// no-fade frame does not pay for a frame-target copy or read undefined
    /// snapshot contents.
    pub(crate) fn append_vanilla_sample_state(
        &self,
        previous_color: TextureUsageState,
        previous_depth: TextureUsageState,
        ops: &mut Vec<CommandOp>,
    ) {
        if previous_color != TextureUsageState::ShaderRead {
            ops.push(CommandOp::Barrier(texture_barrier(
                self.vanilla_color_texture,
                previous_color,
                TextureUsageState::ShaderRead,
            )));
        }
        if previous_depth != TextureUsageState::ShaderRead {
            ops.push(CommandOp::Barrier(texture_barrier(
                self.vanilla_depth_texture,
                previous_depth,
                TextureUsageState::ShaderRead,
            )));
        }
    }

    pub(crate) fn append_ssao_sample_state(
        &self,
        previous: TextureUsageState,
        ops: &mut Vec<CommandOp>,
    ) {
        if previous != TextureUsageState::ShaderRead {
            ops.push(CommandOp::Barrier(texture_barrier(
                self.ssao_texture,
                previous,
                TextureUsageState::ShaderRead,
            )));
        }
    }

    /// Produce an owned DH AO image from the private depth attachment. This
    /// is the Rust replacement for DH's Java SSAO framebuffer/pass.
    pub(crate) fn append_ssao(
        &self,
        projection_matrix: [f32; 16],
        inverse_projection_matrix: [f32; 16],
        parameters: [f32; 8],
        previous: TextureUsageState,
        ops: &mut Vec<CommandOp>,
    ) {
        let mut data = Vec::with_capacity(160);
        for value in projection_matrix
            .into_iter()
            .chain(inverse_projection_matrix)
            .chain(parameters[0..4].iter().copied())
            .chain(parameters[4..8].iter().copied())
        {
            data.extend_from_slice(&value.to_le_bytes());
        }
        ops.push(CommandOp::Barrier(buffer_barrier(
            self.ssao_uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::HostWriteBuffer {
            buffer: self.ssao_uniform_buffer,
            offset: 0,
            data,
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            self.ssao_uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.ssao_texture,
            previous,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: self.ssao_pass,
            target: self.ssao_target,
            colors: vec![PassAttachment {
                view: self.ssao_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    r: 1.0,
                    g: 1.0,
                    b: 1.0,
                    a: 1.0,
                }),
            }],
            depth_stencil: None,
        });
        ops.push(CommandOp::BindGraphicsPipeline(self.ssao_pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: self.ssao_pipeline_layout,
            set_index: 0,
            set: self.ssao_resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            self.ssao_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
    }

    /// Copy the completed vanilla frame target into Rust-owned sampleable
    /// images.  Frame targets are opaque by design; this explicit operation
    /// is the only route by which the compositor may inspect their pixels.
    pub(crate) fn append_vanilla_snapshot(
        &self,
        frame_target: Handle,
        vanilla_depth_source: Handle,
        previous_color: TextureUsageState,
        previous_depth: TextureUsageState,
        ops: &mut Vec<CommandOp>,
    ) {
        ops.push(CommandOp::Barrier(texture_barrier(
            self.vanilla_color_texture,
            previous_color,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.vanilla_depth_texture,
            previous_depth,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            vanilla_depth_source,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::CopyFrameTargetToTexture {
            src: frame_target,
            dst: self.vanilla_color_texture,
            extent: self.extent,
        });
        ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
            row_order: TextureRowOrder::Preserve,
            src_texture: vanilla_depth_source,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: self.vanilla_depth_texture,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: self.extent,
        }));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.vanilla_color_texture,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.vanilla_depth_texture,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            vanilla_depth_source,
            TextureUsageState::TransferSrc,
            TextureUsageState::DepthStencilAttachment,
        )));
    }

    pub(super) fn append_composite_uniforms(
        &self,
        combined_matrix: [f32; 16],
        inverse_combined: [f32; 16],
        inverse_vanilla: [f32; 16],
        camera_position: [f32; 3],
        fog_color: [f32; 4],
        fog_ranges: [f32; 4],
        dh_fog_parameters: [f32; 20],
        fade_parameters: [f32; 4],
        ssao_parameters: [f32; 8],
        ops: &mut Vec<CommandOp>,
    ) {
        let mut data = Vec::with_capacity(368);
        let camera_uniform = [
            camera_position[0],
            camera_position[1],
            camera_position[2],
            1.0,
        ];
        for value in combined_matrix
            .into_iter()
            .chain(inverse_combined)
            .chain(inverse_vanilla)
            .chain(camera_uniform)
            .chain(fog_color)
            .chain(fog_ranges)
            .chain(dh_fog_parameters)
            .chain(fade_parameters)
            .chain(ssao_parameters[0..4].iter().copied())
            .chain(ssao_parameters[4..8].iter().copied())
        {
            data.extend_from_slice(&value.to_le_bytes());
        }
        ops.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::HostWriteBuffer {
            buffer: self.uniform_buffer,
            offset: 0,
            data,
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
    }

    pub(crate) fn append_resolve(
        &self,
        previous_resolved: TextureUsageState,
        combined_matrix: [f32; 16],
        inverse_combined: [f32; 16],
        camera_position: [f32; 3],
        fog_color: [f32; 4],
        fog_ranges: [f32; 4],
        dh_fog_parameters: [f32; 20],
        far_fade_parameters: [f32; 4],
        ssao_parameters: [f32; 8],
        ops: &mut Vec<CommandOp>,
    ) {
        self.append_composite_uniforms(
            combined_matrix,
            inverse_combined,
            [0.0; 16],
            camera_position,
            fog_color,
            fog_ranges,
            dh_fog_parameters,
            far_fade_parameters,
            ssao_parameters,
            ops,
        );
        ops.push(CommandOp::Barrier(texture_barrier(
            self.resolved_color_texture,
            previous_resolved,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: self.resolved_pass,
            target: self.resolved_target,
            colors: vec![PassAttachment {
                view: self.resolved_color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    // Frozen's private DH color remains at Minecraft's active
                    // clear RGB with zero alpha where no LOD was written.
                    // Ordinary sparse apply discards these pixels, while
                    // LOD-only mode deliberately copies them as its sky.
                    r: fog_color[0],
                    g: fog_color[1],
                    b: fog_color[2],
                    a: 0.0,
                }),
            }],
            depth_stencil: None,
        });
        ops.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            self.resolved_color_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
    }

    pub(crate) fn append_apply(
        &self,
        target: Handle,
        pass: Handle,
        color_view: Handle,
        depth_view: Handle,
        ops: &mut Vec<CommandOp>,
    ) {
        ops.push(CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view: color_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: Some(PassAttachment {
                view: depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        ops.push(CommandOp::BindGraphicsPipeline(self.apply_pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resolved_resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
    }

    pub(crate) fn append_fade(
        &self,
        target: Handle,
        pass: Handle,
        color_view: Handle,
        depth_view: Handle,
        combined_matrix: [f32; 16],
        inverse_combined: [f32; 16],
        inverse_vanilla: [f32; 16],
        camera_position: [f32; 3],
        fog_color: [f32; 4],
        fog_ranges: [f32; 4],
        dh_fog_parameters: [f32; 20],
        fade_parameters: [f32; 4],
        ssao_parameters: [f32; 8],
        ops: &mut Vec<CommandOp>,
    ) {
        self.append_composite_uniforms(
            combined_matrix,
            inverse_combined,
            inverse_vanilla,
            camera_position,
            fog_color,
            fog_ranges,
            dh_fog_parameters,
            fade_parameters,
            ssao_parameters,
            ops,
        );
        ops.push(CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view: color_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: Some(PassAttachment {
                view: depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        ops.push(CommandOp::BindGraphicsPipeline(self.fade_pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resolved_resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
    }
}

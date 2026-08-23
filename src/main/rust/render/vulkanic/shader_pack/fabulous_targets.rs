//! Rust-owned external attachments for the bundled Fabulous transparency graph.
//!
//! This module only owns explicit GAL resources.  It does not select a route,
//! borrow Java/Iris targets, or submit a second frame.  The frame coordinator
//! must still route semantic draws and lower the validated post-effect before
//! these resources can make transparency available.

use super::super::gal::VulkanicGal;
use super::super::handles::Handle;
use super::super::commands::{CommandOp, ResourceBarrier, TextureUsageState};
use super::super::resources::{
    AccessFlags, BufferDesc, BufferUsage, CombinedTextureSamplerDesc, Extent3d,
    MemoryDomain, PipelineStageFlags, ResourceBinding, ResourceBindingDesc, ResourceBindingKind,
    ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc, SamplerFilter,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc, GraphicsPipelineDesc, PrimitiveTopology, CullMode, BlendMode,
    FrontFace, PipelineLayoutDesc,
};
use super::super::resources::{RenderPassDesc, RenderTargetDesc};
use super::super::error::{GalError, GalResult};
use super::vanilla_post_effect_executor::{
    FabulousExternalTargetInventory, VanillaPostEffectExternalTargetBinding,
};

const FABULOUS_DEPTH_FORMAT: TextureFormat = TextureFormat::Depth32Float;

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) enum FabulousTargetRole {
    Main,
    Translucent,
    ItemEntity,
    Particles,
    Clouds,
    Weather,
}

impl FabulousTargetRole {
    /// Maps copied semantic material-source identity to its distinct
    /// Fabulous attachment. Unknown source programs remain unavailable rather
    /// than collapsing into the generic translucent target.
    pub(crate) const fn for_material_source(source_program: u32) -> Option<Self> {
        match source_program {
            super::super::world_primitive_frontend::WORLD_MATERIAL_SOURCE_UNSPECIFIED
            | super::super::world_primitive_frontend::WORLD_MATERIAL_SOURCE_TEXTURED => {
                Some(Self::Translucent)
            }
            super::super::world_primitive_frontend::WORLD_MATERIAL_SOURCE_PARTICLES => {
                Some(Self::Particles)
            }
            super::super::world_primitive_frontend::WORLD_MATERIAL_SOURCE_CLOUDS => {
                Some(Self::Clouds)
            }
            super::super::world_primitive_frontend::WORLD_MATERIAL_SOURCE_WEATHER => {
                Some(Self::Weather)
            }
            _ => None,
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct FabulousAttachmentResources {
    pub(crate) role: &'static str,
    pub(crate) color_texture: Handle,
    pub(crate) color_view: Handle,
    pub(crate) depth_texture: Handle,
    pub(crate) depth_view: Handle,
    pub(crate) render_target: Handle,
    pub(crate) render_pass: Handle,
    pub(crate) sampler: Handle,
    pub(crate) extent: Extent3d,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct FabulousIntermediateTarget {
    pub(crate) texture: Handle,
    pub(crate) view: Handle,
    pub(crate) render_target: Handle,
    pub(crate) render_pass: Handle,
    pub(crate) sampler: Handle,
    pub(crate) extent: Extent3d,
}

#[derive(Debug)]
pub(crate) struct FabulousTransparencyBindings {
    pub(crate) samplers: [Handle; 12],
    pub(crate) combined_samplers: [Handle; 12],
    pub(crate) resource_layout: Handle,
    pub(crate) resource_set: Handle,
    pub(crate) blit_combined_sampler: Handle,
    pub(crate) blit_sampler: Handle,
    pub(crate) blit_uniform_buffer: Handle,
    pub(crate) blit_resource_layout: Handle,
    pub(crate) blit_resource_set: Handle,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct FabulousTransparencyPipelines {
    pub(crate) transparency_vertex_shader: Handle,
    pub(crate) transparency_fragment_shader: Handle,
    pub(crate) blit_vertex_shader: Handle,
    pub(crate) blit_fragment_shader: Handle,
    pub(crate) transparency_pipeline_layout: Handle,
    pub(crate) transparency_pipeline: Handle,
    pub(crate) blit_pipeline_layout: Handle,
    pub(crate) blit_pipeline: Handle,
    pub(crate) blit_frame_pipeline: Handle,
}

impl FabulousTransparencyPipelines {
    fn create(
        gal: &mut VulkanicGal,
        bindings: &FabulousTransparencyBindings,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        let sources = super::vanilla_post_effect_executor::bundled_transparency_vulkan_shader_sources()?;
        if sources.len() != 2 {
            return Err(GalError::backend(
                "bundled transparency pipeline requires exactly two shader passes",
            ));
        }
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let transparency_vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: "fabulous.transparency.vertex".to_string(),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: sources[0].0.clone(),
                entry_point: "main".to_string(),
            })?;
            created.push(transparency_vertex_shader);
            let transparency_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: "fabulous.transparency.fragment".to_string(),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: sources[0].1.clone(),
                entry_point: "main".to_string(),
            })?;
            created.push(transparency_fragment_shader);
            let blit_vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: "fabulous.blit.vertex".to_string(),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: sources[1].0.clone(),
                entry_point: "main".to_string(),
            })?;
            created.push(blit_vertex_shader);
            let blit_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: "fabulous.blit.fragment".to_string(),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: sources[1].1.clone(),
                entry_point: "main".to_string(),
            })?;
            created.push(blit_fragment_shader);
            let transparency_pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: "fabulous.transparency.pipeline-layout".to_string(),
                resource_layouts: vec![bindings.resource_layout],
            })?;
            created.push(transparency_pipeline_layout);
            let transparency_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: "fabulous.transparency.pipeline".to_string(),
                layout: transparency_pipeline_layout,
                vertex_shader: transparency_vertex_shader,
                fragment_shader: transparency_fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                blend: BlendMode::Disabled,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: None,
            stencil: None,
            })?;
            created.push(transparency_pipeline);
            let blit_pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: "fabulous.blit.pipeline-layout".to_string(),
                resource_layouts: vec![bindings.blit_resource_layout],
            })?;
            created.push(blit_pipeline_layout);
            let blit_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: "fabulous.blit.pipeline".to_string(),
                layout: blit_pipeline_layout,
                vertex_shader: blit_vertex_shader,
                fragment_shader: blit_fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                blend: BlendMode::Disabled,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
            stencil: None,
            })?;
            created.push(blit_pipeline);
            let blit_frame_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: "fabulous.blit.frame-target-pipeline".to_string(),
                layout: blit_pipeline_layout,
                vertex_shader: blit_vertex_shader,
                fragment_shader: blit_fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                blend: BlendMode::Disabled,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: None,
            stencil: None,
            })?;
            created.push(blit_frame_pipeline);
            Ok(Self {
                transparency_vertex_shader,
                transparency_fragment_shader,
                blit_vertex_shader,
                blit_fragment_shader,
                transparency_pipeline_layout,
                transparency_pipeline,
                blit_pipeline_layout,
                blit_pipeline,
                blit_frame_pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    fn handles_in_destroy_order(self) -> [Handle; 9] {
        [
            self.blit_pipeline,
            self.blit_frame_pipeline,
            self.blit_pipeline_layout,
            self.transparency_pipeline,
            self.transparency_pipeline_layout,
            self.blit_fragment_shader,
            self.blit_vertex_shader,
            self.transparency_fragment_shader,
            self.transparency_vertex_shader,
        ]
    }
}

impl FabulousTransparencyBindings {
    fn create(gal: &mut VulkanicGal, set: &FabulousAttachmentSet) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let sources = [
                (&set.main, false),
                (&set.main, true),
                (&set.translucent, false),
                (&set.translucent, true),
                (&set.item_entity, false),
                (&set.item_entity, true),
                (&set.particles, false),
                (&set.particles, true),
                (&set.clouds, false),
                (&set.clouds, true),
                (&set.weather, false),
                (&set.weather, true),
            ];
            let mut samplers = Vec::new();
            let mut combined = Vec::new();
            for (index, (attachment, depth)) in sources.into_iter().enumerate() {
                let view = if depth { attachment.depth_view } else { attachment.color_view };
                let sampler = gal.create_sampler(SamplerDesc {
                    label: format!("fabulous.transparency.sampler.{index}"),
                    min_filter: SamplerFilter::Nearest,
                    mag_filter: SamplerFilter::Nearest,
                    mip_filter: SamplerFilter::Nearest,
                    address_u: SamplerAddressMode::ClampToEdge,
                    address_v: SamplerAddressMode::ClampToEdge,
                    address_w: SamplerAddressMode::ClampToEdge,
                    comparison: None,
                })?;
                created.push(sampler);
                samplers.push(sampler);
                let pair = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("fabulous.transparency.combined.{index}"),
                    texture_view: view,
                    sampler,
                })?;
                created.push(pair);
                combined.push(pair);
            }
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: "fabulous.transparency.resource-layout".to_string(),
                bindings: (0..12)
                    .map(|binding| ResourceBindingDesc {
                        binding,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    })
                    .collect(),
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: "fabulous.transparency.resource-set".to_string(),
                layout: resource_layout,
                bindings: combined
                    .iter()
                    .enumerate()
                    .map(|(binding, resource)| ResourceBinding {
                        binding: binding as u32,
                        array_index: 0,
                        resource: *resource,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    })
                    .collect(),
            })?;
            created.push(resource_set);
            let blit_sampler = gal.create_sampler(SamplerDesc {
                label: "fabulous.blit.sampler".to_string(),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(blit_sampler);
            let blit_combined_sampler = gal.create_combined_texture_sampler(
                CombinedTextureSamplerDesc {
                    label: "fabulous.blit.combined".to_string(),
                    texture_view: set.final_target.view,
                    sampler: blit_sampler,
                },
            )?;
            created.push(blit_combined_sampler);
            let blit_uniform_buffer = gal.create_buffer(BufferDesc {
                label: "fabulous.blit.uniform".to_string(),
                size: 16,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Uniform, BufferUsage::TransferDst, BufferUsage::HostWrite],
            })?;
            created.push(blit_uniform_buffer);
            let blit_resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: "fabulous.blit.resource-layout".to_string(),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::UniformBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ],
            })?;
            created.push(blit_resource_layout);
            let blit_resource_set = gal.create_resource_set(ResourceSetDesc {
                label: "fabulous.blit.resource-set".to_string(),
                layout: blit_resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: blit_combined_sampler,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: blit_uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(16),
                    },
                ],
            })?;
            created.push(blit_resource_set);
            Ok(Self {
                samplers: samplers.try_into().expect("twelve transparency samplers"),
                combined_samplers: combined.try_into().expect("twelve transparency samplers"),
                resource_layout,
                resource_set,
                blit_combined_sampler,
                blit_sampler,
                blit_uniform_buffer,
                blit_resource_layout,
                blit_resource_set,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    fn handles_in_destroy_order(&self) -> Vec<Handle> {
        let mut handles = vec![
            self.blit_resource_set,
            self.blit_resource_layout,
            self.blit_uniform_buffer,
            self.blit_combined_sampler,
            self.blit_sampler,
            self.resource_set,
            self.resource_layout,
        ];
        handles.extend(self.combined_samplers.iter().copied());
        handles
    }
}

impl FabulousIntermediateTarget {
    fn create(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        let prefix = "fabulous.final";
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let texture = gal.create_texture(TextureDesc {
                label: format!("{prefix}.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::ColorAttachment, TextureUsage::Sampled],
            })?;
            created.push(texture);
            let view = gal.create_texture_view(TextureViewDesc {
                label: format!("{prefix}.view"),
                texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let render_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{prefix}.target"),
                color_views: vec![view],
                depth_stencil_view: None,
                extent,
            })?;
            created.push(render_target);
            let render_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{prefix}.pass"),
                target: render_target,
                color_formats: vec![color_format],
                depth_format: None,
            })?;
            created.push(render_pass);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{prefix}.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            Ok(Self { texture, view, render_target, render_pass, sampler, extent })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    fn handles_in_destroy_order(self) -> [Handle; 5] {
        [self.sampler, self.render_pass, self.render_target, self.view, self.texture]
    }
}

impl FabulousAttachmentResources {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        role: &'static str,
        extent: Extent3d,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        Self::create_with_depth_format(gal, role, extent, color_format, FABULOUS_DEPTH_FORMAT, false)
    }

    /// Creates an attachment with an explicit depth/stencil format and,
    /// optionally, transfer-capable color storage.  The optical hand target
    /// uses this rather than changing the six shader-pack attachments: their
    /// depth images remain shader-readable `Depth32Float` resources, while
    /// the hand mask gets a real `Depth24Stencil8` domain.
    pub(crate) fn create_with_depth_format(
        gal: &mut VulkanicGal,
        role: &'static str,
        extent: Extent3d,
        color_format: TextureFormat,
        depth_format: TextureFormat,
        transfer_color: bool,
    ) -> GalResult<Self> {
        if role.trim().is_empty() {
            return Err(GalError::invalid_argument(
                "Fabulous attachment role must be non-empty",
            ));
        }
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "Fabulous attachment extent must be a non-empty 2D image",
            ));
        }
        let prefix = format!("fabulous.{role}");
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let mut color_usages = vec![TextureUsage::ColorAttachment, TextureUsage::Sampled];
            if transfer_color {
                color_usages.extend([TextureUsage::TransferSrc, TextureUsage::TransferDst]);
            }
            let color_texture = gal.create_texture(TextureDesc {
                label: format!("{prefix}.color.texture"),
                dimension: TextureDimension::D2,
                format: color_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: color_usages,
            })?;
            created.push(color_texture);
            let color_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{prefix}.color.view"),
                texture: color_texture,
                format: color_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(color_view);
            let depth_texture = gal.create_texture(TextureDesc {
                label: format!("{prefix}.depth.texture"),
                dimension: TextureDimension::D2,
                format: depth_format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::DepthStencilAttachment, TextureUsage::Sampled],
            })?;
            created.push(depth_texture);
            let depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{prefix}.depth.view"),
                texture: depth_texture,
                format: depth_format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(depth_view);
            let render_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{prefix}.target"),
                color_views: vec![color_view],
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            created.push(render_target);
            let render_pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{prefix}.pass"),
                target: render_target,
                color_formats: vec![color_format],
                depth_format: Some(depth_format),
            })?;
            created.push(render_pass);
            let sampler = gal.create_sampler(SamplerDesc {
                label: format!("{prefix}.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            Ok(Self {
                role,
                color_texture,
                color_view,
                depth_texture,
                depth_view,
                render_target,
                render_pass,
                sampler,
                extent,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(crate) fn handles_in_destroy_order(self) -> [Handle; 7] {
        [
            self.sampler,
            self.render_pass,
            self.render_target,
            self.depth_view,
            self.color_view,
            self.depth_texture,
            self.color_texture,
        ]
    }
}

/// All six external targets for one frame extent. Creation is atomic from the
/// caller's perspective: a failure destroys every resource created so far.
#[derive(Debug)]
pub(crate) struct FabulousAttachmentSet {
    pub(crate) final_target: FabulousIntermediateTarget,
    pub(crate) bindings: Option<FabulousTransparencyBindings>,
    pub(crate) pipelines: Option<FabulousTransparencyPipelines>,
    pub(crate) main: FabulousAttachmentResources,
    pub(crate) translucent: FabulousAttachmentResources,
    pub(crate) item_entity: FabulousAttachmentResources,
    pub(crate) particles: FabulousAttachmentResources,
    pub(crate) clouds: FabulousAttachmentResources,
    pub(crate) weather: FabulousAttachmentResources,
    /// Private first-person optical target. It is not part of the shader-pack
    /// external inventory and is only consumed once a semantic optical plan
    /// is admitted by the hand frontend.
    pub(crate) optical_hand: FabulousAttachmentResources,
}

impl FabulousAttachmentSet {
    pub(crate) fn create(
        gal: &mut VulkanicGal,
        extent: Extent3d,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let final_target = FabulousIntermediateTarget::create(gal, extent, color_format)?;
            created.extend(final_target.handles_in_destroy_order());
            // The private optical hand copy boundary reads/writes only the
            // Rust-owned main color attachment. Keep transfer usage explicit
            // on that resource; every other external attachment remains
            // render/sample-only.
            let main = FabulousAttachmentResources::create_with_depth_format(
                gal,
                "minecraft:main",
                extent,
                color_format,
                FABULOUS_DEPTH_FORMAT,
                true,
            )?;
            created.extend(main.handles_in_destroy_order());
            let mut create = |role| FabulousAttachmentResources::create(gal, role, extent, color_format);
            let translucent = create("minecraft:translucent")?;
            created.extend(translucent.handles_in_destroy_order());
            let item_entity = create("minecraft:item_entity")?;
            created.extend(item_entity.handles_in_destroy_order());
            let particles = create("minecraft:particles")?;
            created.extend(particles.handles_in_destroy_order());
            let clouds = create("minecraft:clouds")?;
            created.extend(clouds.handles_in_destroy_order());
            let weather = create("minecraft:weather")?;
            created.extend(weather.handles_in_destroy_order());
            let optical_hand = FabulousAttachmentResources::create_with_depth_format(
                gal,
                "mattmc:optical_hand",
                extent,
                color_format,
                TextureFormat::Depth24Stencil8,
                true,
            )?;
            created.extend(optical_hand.handles_in_destroy_order());
            let provisional = Self {
                final_target,
                bindings: None,
                pipelines: None,
                main,
                translucent,
                item_entity,
                particles,
                clouds,
                weather,
                optical_hand,
            };
            let bindings = FabulousTransparencyBindings::create(gal, &provisional)?;
            let pipelines = FabulousTransparencyPipelines::create(gal, &bindings, color_format)?;
            Ok(Self { bindings: Some(bindings), pipelines: Some(pipelines), ..provisional })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(crate) fn external_inventory(&self) -> FabulousExternalTargetInventory {
        fn binding(resource: &FabulousAttachmentResources) -> VanillaPostEffectExternalTargetBinding {
            VanillaPostEffectExternalTargetBinding {
                render_target: resource.render_target,
                color_attachment: resource.color_view,
                depth_attachment: Some(resource.depth_view),
                sampler: resource.sampler,
            }
        }
        FabulousExternalTargetInventory {
            main: binding(&self.main),
            translucent: binding(&self.translucent),
            item_entity: binding(&self.item_entity),
            particles: binding(&self.particles),
            clouds: binding(&self.clouds),
            weather: binding(&self.weather),
        }
    }

    pub(crate) fn transparency_pass_bindings(
        &self,
    ) -> GalResult<Vec<super::vanilla_post_effect_executor::VanillaPostEffectPassBinding>> {
        let bindings = self
            .bindings
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous descriptor bindings are not initialized"))?;
        let pipelines = self
            .pipelines
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous pipelines are not initialized"))?;
        let executor = super::vanilla_post_effect_executor::bundled_transparency_executor()?;
        let plan = executor.plan();
        let attachment = |name: &str| -> Option<&FabulousAttachmentResources> {
            match name {
                "minecraft:main" => Some(&self.main),
                "minecraft:translucent" => Some(&self.translucent),
                "minecraft:item_entity" => Some(&self.item_entity),
                "minecraft:particles" => Some(&self.particles),
                "minecraft:clouds" => Some(&self.clouds),
                "minecraft:weather" => Some(&self.weather),
                _ => None,
            }
        };
        let mut pass_bindings = Vec::with_capacity(plan.ordered_passes.len());
        for (index, pass) in plan.ordered_passes.iter().enumerate() {
            let inputs = pass
                .inputs
                .iter()
                .map(|input| {
                    if input.target == "final" {
                        return Ok(super::vanilla_post_effect_executor::VanillaPostEffectInputBinding {
                            texture_view: self.final_target.view,
                            sampler: self.final_target.sampler,
                            bilinear: input.bilinear,
                            use_depth_buffer: input.use_depth_buffer,
                        });
                    }
                    let target = attachment(&input.target).ok_or_else(|| {
                        GalError::unsupported_feature(format!(
                            "Fabulous post-effect input target {} has no Rust-owned attachment",
                            input.target
                        ))
                    })?;
                    let input_index = pass
                        .inputs
                        .iter()
                        .position(|candidate| candidate.sampler_name == input.sampler_name)
                        .ok_or_else(|| {
                            GalError::invalid_argument("Fabulous post-effect input order is not stable")
                        })?;
                    let sampler = bindings.samplers[input_index];
                    Ok(super::vanilla_post_effect_executor::VanillaPostEffectInputBinding {
                        texture_view: if input.use_depth_buffer {
                            target.depth_view
                        } else {
                            target.color_view
                        },
                        sampler,
                        bilinear: input.bilinear,
                        use_depth_buffer: input.use_depth_buffer,
                    })
                })
                .collect::<GalResult<Vec<_>>>()?;
            let (render_pass, render_target, color_attachment, depth_attachment, pipeline, pipeline_layout, resource_set) =
                if index == 0 {
                    (
                        self.final_target.render_pass,
                        self.final_target.render_target,
                        self.final_target.view,
                        None,
                        pipelines.transparency_pipeline,
                        pipelines.transparency_pipeline_layout,
                        bindings.resource_set,
                    )
                } else {
                    (
                        self.main.render_pass,
                        self.main.render_target,
                        self.main.color_view,
                        Some(self.main.depth_view),
                        pipelines.blit_pipeline,
                        pipelines.blit_pipeline_layout,
                        bindings.blit_resource_set,
                    )
                };
            pass_bindings.push(super::vanilla_post_effect_executor::VanillaPostEffectPassBinding {
                render_pass,
                render_target,
                color_attachment,
                depth_attachment,
                pipeline,
                pipeline_layout,
                resource_set,
                inputs,
                uniform_values: pass.uniform_values.clone(),
            });
        }
        Ok(pass_bindings)
    }

    /// Builds the final blit against the acquired GAL frame target. The
    /// caller owns and retires the returned render pass after submission
    /// completion; this helper never creates a second presenter.
    pub(crate) fn transparency_pass_bindings_to_frame_target(
        &self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
    ) -> GalResult<(
        Vec<super::vanilla_post_effect_executor::VanillaPostEffectPassBinding>,
        Handle,
    )> {
        let mut bindings = self.transparency_pass_bindings()?;
        let pipelines = self
            .pipelines
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous pipelines are not initialized"))?;
        if bindings.is_empty() {
            return Err(GalError::backend("Fabulous transparency graph has no final blit pass"));
        }
        let color_format = gal.pass_target_color_format(frame_target)?;
        let pass = gal.create_render_pass(super::super::resources::RenderPassDesc {
            label: "fabulous.blit.acquired-frame-pass".to_string(),
            target: frame_target,
            color_formats: vec![color_format],
            depth_format: None,
        })?;
        let last = bindings.last_mut().expect("non-empty Fabulous pass bindings");
        last.render_pass = pass;
        last.render_target = frame_target;
        last.color_attachment = frame_target;
        last.depth_attachment = None;
        last.pipeline = pipelines.blit_frame_pipeline;
        Ok((bindings, pass))
    }

    /// Creates a bounded final blit descriptor that samples the composed
    /// private main attachment rather than the intermediate transparency
    /// image. This is used when depth-backed overlays (for example world
    /// text) must be drawn into `main` before the one acquired-frame copy.
    pub(crate) fn create_main_to_frame_blit_resources(
        &self,
        gal: &mut VulkanicGal,
        frame_target: Handle,
    ) -> GalResult<(Handle, Handle, Handle, Handle)> {
        let bindings = self
            .bindings
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous descriptor bindings are not initialized"))?;
        let color_format = gal.pass_target_color_format(frame_target)?;
        let sampler = gal.create_sampler(SamplerDesc {
            label: "fabulous.main-to-frame.sampler".to_string(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })?;
        let combined = match gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: "fabulous.main-to-frame.combined".to_string(),
            texture_view: self.main.color_view,
            sampler,
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(sampler);
                return Err(error);
            }
        };
        let resource_set = match gal.create_resource_set(ResourceSetDesc {
            label: "fabulous.main-to-frame.resource-set".to_string(),
            layout: bindings.blit_resource_layout,
            bindings: vec![
                ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: combined,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 1,
                    array_index: 0,
                    resource: bindings.blit_uniform_buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: Some(16),
                },
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(combined);
                let _ = gal.destroy(sampler);
                return Err(error);
            }
        };
        let pass = match gal.create_render_pass(RenderPassDesc {
            label: "fabulous.main-to-frame.pass".to_string(),
            target: frame_target,
            color_formats: vec![color_format],
            depth_format: None,
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(resource_set);
                let _ = gal.destroy(combined);
                let _ = gal.destroy(sampler);
                return Err(error);
            }
        };
        Ok((resource_set, combined, sampler, pass))
    }

    /// Emits the explicit state transitions required before the first
    /// transparency fullscreen pass. The caller supplies no backend state:
    /// every resource identity comes from this Rust-owned attachment set.
    pub(crate) fn pre_transparency_barriers(&self) -> Vec<CommandOp> {
        let mut operations = Vec::with_capacity(13);
        for attachment in [
            &self.main,
            &self.translucent,
            &self.item_entity,
            &self.particles,
            &self.clouds,
            &self.weather,
        ] {
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }));
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.depth_texture,
                subresources: None,
                before: TextureUsageState::DepthStencilAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }));
        }
        operations.push(CommandOp::Barrier(ResourceBarrier {
            resource: self.final_target.texture,
            subresources: None,
            before: TextureUsageState::Undefined,
            after: TextureUsageState::ColorAttachment,
            src_queue: super::super::resources::QueueClass::Graphics,
            dst_queue: super::super::resources::QueueClass::Graphics,
        }));
        operations
    }

    pub(crate) fn between_transparency_pass_barriers(&self) -> Vec<CommandOp> {
        vec![CommandOp::Barrier(ResourceBarrier {
            resource: self.final_target.texture,
            subresources: None,
            before: TextureUsageState::ColorAttachment,
            after: TextureUsageState::ShaderRead,
            src_queue: super::super::resources::QueueClass::Graphics,
            dst_queue: super::super::resources::QueueClass::Graphics,
        })]
    }

    /// Transitions the six producer attachments after their semantic draw
    /// passes have completed.  Keeping this separate from the intermediate
    /// target transition lets the frame coordinator initialize empty families
    /// with a clear pass and still present every declared sampler in a valid
    /// shader-readable state.
    pub(crate) fn external_shader_read_barriers(&self) -> Vec<CommandOp> {
        let mut operations = Vec::with_capacity(12);
        for attachment in [
            &self.main,
            &self.translucent,
            &self.item_entity,
            &self.particles,
            &self.clouds,
            &self.weather,
        ] {
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }));
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: attachment.depth_texture,
                subresources: None,
                before: TextureUsageState::DepthStencilAttachment,
                after: TextureUsageState::ShaderRead,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }));
        }
        operations
    }

    pub(crate) fn final_target_color_attachment_barrier(&self) -> CommandOp {
        CommandOp::Barrier(ResourceBarrier {
            resource: self.final_target.texture,
            subresources: None,
            before: TextureUsageState::Undefined,
            after: TextureUsageState::ColorAttachment,
            src_queue: super::super::resources::QueueClass::Graphics,
            dst_queue: super::super::resources::QueueClass::Graphics,
        })
    }

    /// Copies the already-rendered main color into the private optical hand
    /// target.  The operation is deliberately expressed as GAL barriers and
    /// a texture copy; no framebuffer, image, or native handle crosses the
    /// semantic boundary.
    pub(crate) fn optical_hand_copy_from_main(&self, extent: Extent3d) -> Vec<CommandOp> {
        vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::TransferSrc,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferDst,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
            CommandOp::CopyTexture(super::super::commands::TextureImageCopyRegion {
                src_texture: self.main.color_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: super::super::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: self.optical_hand.color_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: super::super::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ColorAttachment,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::ColorAttachment,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
        ]
    }

    /// Copies the optical result back over the Rust-owned main color. The
    /// caller records this only after the optical hand pass has ended.
    pub(crate) fn optical_hand_copy_to_main(&self, extent: Extent3d) -> Vec<CommandOp> {
        vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::TransferSrc,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::ColorAttachment,
                after: TextureUsageState::TransferDst,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
            CommandOp::CopyTexture(super::super::commands::TextureImageCopyRegion {
                src_texture: self.optical_hand.color_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: super::super::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: self.main.color_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: super::super::commands::TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.main.color_texture,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ColorAttachment,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: self.optical_hand.color_texture,
                subresources: None,
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::ColorAttachment,
                src_queue: super::super::resources::QueueClass::Graphics,
                dst_queue: super::super::resources::QueueClass::Graphics,
            }),
        ]
    }

    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        if let Some(pipelines) = self.pipelines {
            for handle in pipelines.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        if let Some(bindings) = self.bindings {
            for handle in bindings.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        for handle in self.final_target.handles_in_destroy_order() {
            let _ = gal.destroy(handle);
        }
        for resource in [
            self.optical_hand,
            self.weather,
            self.clouds,
            self.particles,
            self.item_entity,
            self.translucent,
            self.main,
        ] {
            for handle in resource.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::mock::MockBackend;
    use crate::render::vulkanic::gal::VulkanicGal;
    use crate::render::vulkanic::backends::vulkan_capabilities;
    use crate::render::vulkanic::frame::{FrameRenderTargetId, FrameSurfaceDesc, PresentMode};
    use crate::render::vulkanic::resources::FrameTargetDesc;

    #[test]
    fn attachment_contract_is_explicitly_two_dimensional_and_bounded() {
        assert_eq!(FABULOUS_DEPTH_FORMAT, TextureFormat::Depth32Float);
        assert_eq!(Extent3d { width: 1, height: 1, depth: 1 }.depth, 1);
    }

    #[test]
    fn material_source_families_map_to_distinct_fabulous_roles() {
        use super::super::super::world_primitive_frontend::{
            WORLD_MATERIAL_SOURCE_CLOUDS, WORLD_MATERIAL_SOURCE_PARTICLES,
            WORLD_MATERIAL_SOURCE_TEXTURED, WORLD_MATERIAL_SOURCE_WEATHER,
        };
        assert_eq!(
            FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_TEXTURED),
            Some(FabulousTargetRole::Translucent)
        );
        assert_eq!(
            FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_PARTICLES),
            Some(FabulousTargetRole::Particles)
        );
        assert_eq!(
            FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_CLOUDS),
            Some(FabulousTargetRole::Clouds)
        );
        assert_eq!(
            FabulousTargetRole::for_material_source(WORLD_MATERIAL_SOURCE_WEATHER),
            Some(FabulousTargetRole::Weather)
        );
        assert_eq!(
            FabulousTargetRole::for_material_source(0),
            Some(FabulousTargetRole::Translucent)
        );
    }

    #[test]
    fn attachment_set_allocates_explicit_passes_and_validates_the_bundled_graph() {
        let mut capabilities = vulkan_capabilities();
        capabilities.features.presentation = true;
        let mut gal = VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(capabilities)),
            false,
        );
        let set = FabulousAttachmentSet::create(
            &mut gal,
            Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            TextureFormat::Rgba8Unorm,
        )
        .unwrap();
        assert_ne!(set.main.render_pass, Handle::NULL);
        assert_ne!(set.translucent.render_pass, set.particles.render_pass);
        assert_ne!(set.final_target.render_target, set.main.render_target);
        assert_eq!(set.bindings.as_ref().unwrap().combined_samplers.len(), 12);
        let executor = super::super::vanilla_post_effect_executor::bundled_transparency_executor()
            .unwrap();
        set.external_inventory()
            .validate_against(executor.plan())
            .unwrap();
        assert_eq!(13, set.pre_transparency_barriers().len());
        assert_eq!(1, set.between_transparency_pass_barriers().len());
        let copy_in = set.optical_hand_copy_from_main(Extent3d { width: 64, height: 64, depth: 1 });
        assert_eq!(5, copy_in.len());
        assert!(copy_in.iter().any(|operation| matches!(
            operation,
            super::super::super::commands::CommandOp::CopyTexture(copy)
                if copy.src_texture == set.main.color_texture
                    && copy.dst_texture == set.optical_hand.color_texture
        )));
        let copy_out = set.optical_hand_copy_to_main(Extent3d { width: 64, height: 64, depth: 1 });
        assert_eq!(5, copy_out.len());
        assert!(copy_out.iter().any(|operation| matches!(
            operation,
            super::super::super::commands::CommandOp::CopyTexture(copy)
                if copy.src_texture == set.optical_hand.color_texture
                    && copy.dst_texture == set.main.color_texture
        )));
        let pass_bindings = set.transparency_pass_bindings().unwrap();
        let operations = executor.lower(&pass_bindings).unwrap();
        assert_eq!(2, operations.iter().filter(|operation| matches!(
            operation,
            super::super::super::commands::CommandOp::Draw { vertices: 3, instances: 1 }
        )).count());
        gal.create_command_list(super::super::super::commands::CommandListDesc {
            label: "fabulous-transparency-test".to_string(),
            operations,
        })
        .unwrap();
        gal
            .configure_frame_surface(FrameSurfaceDesc {
                label: "fabulous-frame-surface".to_string(),
                extent: Extent3d { width: 64, height: 64, depth: 1 },
                color_format: TextureFormat::Rgba8Unorm,
                present_mode: PresentMode::Fifo,
                max_frames_in_flight: 2,
            })
            .unwrap();
        let frame_target = gal
            .create_frame_target(FrameTargetDesc {
                label: "fabulous-frame-target".to_string(),
                frame_id: 1,
                render_target: FrameRenderTargetId(1),
                extent: Extent3d { width: 64, height: 64, depth: 1 },
                color_format: TextureFormat::Rgba8Unorm,
            })
            .unwrap();
        let (frame_bindings, present_pass) = set
            .transparency_pass_bindings_to_frame_target(&mut gal, frame_target)
            .unwrap();
        assert_eq!(frame_target, frame_bindings.last().unwrap().render_target);
        assert_eq!(frame_target, frame_bindings.last().unwrap().color_attachment);
        assert!(frame_bindings.last().unwrap().depth_attachment.is_none());
        let frame_operations = executor.lower(&frame_bindings).unwrap();
        gal.create_command_list(super::super::super::commands::CommandListDesc {
            label: "fabulous-transparency-frame-target-test".to_string(),
            operations: frame_operations,
        })
        .unwrap();
        gal.destroy(present_pass).unwrap();
        set.destroy(&mut gal);
    }
}

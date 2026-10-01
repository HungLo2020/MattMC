//! The bundled transparency composition pipelines and their resource bindings.

use super::*;

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
    pub(super) fn create(
        gal: &mut VulkanicGal,
        bindings: &FabulousTransparencyBindings,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        let sources =
            crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_lowered_shader_sources(
                gal.capabilities().shader_conventions,
            )?;
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
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
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

    pub(super) fn handles_in_destroy_order(self) -> [Handle; 9] {
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
    pub(super) fn create(gal: &mut VulkanicGal, set: &FabulousAttachmentSet) -> GalResult<Self> {
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
                let view = if depth {
                    attachment.depth_view
                } else {
                    attachment.color_view
                };
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
            let blit_combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: "fabulous.blit.combined".to_string(),
                    texture_view: set.final_target.view,
                    sampler: blit_sampler,
                })?;
            created.push(blit_combined_sampler);
            let blit_uniform_buffer = gal.create_buffer(BufferDesc {
                label: "fabulous.blit.uniform".to_string(),
                size: 16,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
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

    pub(super) fn handles_in_destroy_order(&self) -> Vec<Handle> {
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
        // Combined descriptors reference, but do not own, these samplers.
        // Retire descriptors first, then all twelve separately created samplers.
        handles.extend(self.samplers.iter().copied());
        handles
    }
}

impl FabulousAttachmentSet {
    pub(crate) fn transparency_pass_bindings(
        &self,
    ) -> GalResult<Vec<crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectPassBinding>> {
        let bindings = self
            .bindings
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous descriptor bindings are not initialized"))?;
        let pipelines = self
            .pipelines
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous pipelines are not initialized"))?;
        let executor = crate::render::shaderpack::vanilla::post_effect::executor::bundled_transparency_executor()?;
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
                        return Ok(
                            crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectInputBinding {
                                texture_view: self.final_target.view,
                                sampler: self.final_target.sampler,
                                bilinear: input.bilinear,
                                use_depth_buffer: input.use_depth_buffer,
                            },
                        );
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
                            GalError::invalid_argument(
                                "Fabulous post-effect input order is not stable",
                            )
                        })?;
                    let sampler = bindings.samplers[input_index];
                    Ok(
                        crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectInputBinding {
                            texture_view: if input.use_depth_buffer {
                                target.depth_view
                            } else {
                                target.color_view
                            },
                            sampler,
                            bilinear: input.bilinear,
                            use_depth_buffer: input.use_depth_buffer,
                        },
                    )
                })
                .collect::<GalResult<Vec<_>>>()?;
            let (
                render_pass,
                render_target,
                color_attachment,
                depth_attachment,
                pipeline,
                pipeline_layout,
                resource_set,
            ) = if index == 0 {
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
            pass_bindings.push(
                crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectPassBinding {
                    render_pass,
                    render_target,
                    color_attachment,
                    depth_attachment,
                    pipeline,
                    pipeline_layout,
                    resource_set,
                    inputs,
                    uniform_values: pass.uniform_values.clone(),
                },
            );
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
        Vec<crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectPassBinding>,
        Handle,
    )> {
        let mut bindings = self.transparency_pass_bindings()?;
        let pipelines = self
            .pipelines
            .as_ref()
            .ok_or_else(|| GalError::backend("Fabulous pipelines are not initialized"))?;
        if bindings.is_empty() {
            return Err(GalError::backend(
                "Fabulous transparency graph has no final blit pass",
            ));
        }
        let color_format = gal.pass_target_color_format(frame_target)?;
        let pass = gal.create_render_pass(crate::render::vulkanic::resources::RenderPassDesc {
            label: "fabulous.blit.acquired-frame-pass".to_string(),
            target: frame_target,
            color_formats: vec![color_format],
            depth_format: None,
        })?;
        let last = bindings
            .last_mut()
            .expect("non-empty Fabulous pass bindings");
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
}

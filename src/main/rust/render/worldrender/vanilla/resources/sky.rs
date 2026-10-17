//! Sky-disc resources and background/clear colors.

use super::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn ensure_sky_disc_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<()> {
        if self
            .sky_disc_resources
            .contains_key(&(color_format, raster_y_direction))
        {
            return Ok(());
        }
        let label = format!("world-sky-disc-gen{}", self.generation);
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldSkyDiscResources> {
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: WORLD_SKY_DISC_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    std::str::from_utf8(WORLD_SKY_DISC_VERTEX_SHADER)
                        .expect("sky shader UTF-8"),
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_SKY_DISC_FRAGMENT_SHADER.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                }],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: vec![ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: uniform_buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: Some(WORLD_SKY_DISC_UNIFORM_BYTES),
                }],
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
                topology: PrimitiveTopology::TriangleFan,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend: BlendMode::Disabled,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: false,
                depth_bias: None,
                color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT; 4],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(WorldSkyDiscResources {
                uniform_buffer,
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
        self.sky_disc_resources
            .insert((color_format, raster_y_direction), result?);
        Ok(())
    }

    pub(crate) fn ensure_sky_disc_forward_resources(
        &mut self,
        gal: &mut VulkanicGal,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
    ) -> GalResult<()> {
        if self
            .sky_disc_forward_resources
            .contains_key(&(color_format, raster_y_direction))
        {
            return Ok(());
        }
        let label = format!("world-sky-disc-forward-gen{}", self.generation);
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldSkyDiscResources> {
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: WORLD_SKY_DISC_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(
                    gal.capabilities().shader_conventions,
                    std::str::from_utf8(WORLD_SKY_DISC_VERTEX_SHADER)
                        .expect("sky shader UTF-8"),
                ),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: WORLD_SKY_DISC_FORWARD_FRAGMENT_SHADER.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                }],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.resource-set"),
                layout: resource_layout,
                bindings: vec![ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: uniform_buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: Some(WORLD_SKY_DISC_UNIFORM_BYTES),
                }],
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
                topology: PrimitiveTopology::TriangleFan,
                cull_mode: CullMode::None,
                front_face: FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend: BlendMode::Disabled,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(WorldSkyDiscResources {
                uniform_buffer,
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
        self.sky_disc_forward_resources
            .insert((color_format, raster_y_direction), result?);
        Ok(())
    }
}

pub(crate) fn cull_mode_from_policy(policy: u32) -> GalResult<CullMode> {
    match policy {
        WORLD_CULL_NONE => Ok(CullMode::None),
        WORLD_CULL_BACK => Ok(CullMode::Back),
        WORLD_CULL_FRONT => Ok(CullMode::Front),
        _ => Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown world material cull policy {policy}"),
        )),
    }
}

pub(crate) fn background_clear_color(background: &WorldBackgroundRequest) -> crate::render::vulkanic::commands::ClearColor {
    if background.enabled {
        argb_clear_color(background.color_argb)
    } else {
        crate::render::vulkanic::commands::ClearColor {
            r: 0.063,
            g: 0.157,
            b: 0.855,
            a: 1.0,
        }
    }
}

pub(crate) fn vanilla_world_clear_color(frame: &WorldPrimitiveFrame) -> crate::render::vulkanic::commands::ClearColor {
    if frame.shader_environment.enabled {
        let color = frame.shader_environment.fog_parameter_color;
        return crate::render::vulkanic::commands::ClearColor {
            r: color[0],
            g: color[1],
            b: color[2],
            a: color[3],
        };
    }
    background_clear_color(&frame.background)
}

pub(crate) fn vanilla_sky_disc_required(background: &WorldBackgroundRequest, clear_background: bool) -> bool {
    clear_background
        && background.enabled
        && background.sky.visible
        && background.sky_type == WORLD_BACKGROUND_SKY_OVERWORLD
}

/// The direct Rust DH compositor overlays a sparse private target after the
/// background/sky setup and before ordinary opaque terrain, so its uncovered
/// pixels must retain the vanilla sky fan. A shader-pack/G-buffer route has a
/// later source-owned sky stage and keeps the fan disabled here. This is a
/// route policy decision, not a Vulkan workaround or a Java renderer fallback.
pub(crate) fn vanilla_sky_disc_required_for_frame(
    frame: &WorldPrimitiveFrame,
    clear_background: bool,
    use_g_buffer_mesh_path: bool,
) -> bool {
    let far_clip_fade = frame.lod_render_frame.flags & WORLD_LOD_FLAG_DH_FAR_CLIP_FADE != 0;
    vanilla_sky_disc_required(&frame.background, clear_background)
        && (!frame.lod_render_frame.rust_route_selected() || !use_g_buffer_mesh_path)
        && !(frame.lod_render_frame.rust_route_selected() && far_clip_fade)
}

pub(crate) fn argb_clear_color(color_argb: u32) -> crate::render::vulkanic::commands::ClearColor {
    let a = ((color_argb >> 24) & 0xff) as f32 / 255.0;
    let r = ((color_argb >> 16) & 0xff) as f32 / 255.0;
    let g = ((color_argb >> 8) & 0xff) as f32 / 255.0;
    let b = (color_argb & 0xff) as f32 / 255.0;
    crate::render::vulkanic::commands::ClearColor { r, g, b, a }
}

pub(crate) fn loaded_frame_color_attachment(color_attachment: Handle) -> PassAttachment {
    PassAttachment {
        view: color_attachment,
        load_op: AttachmentLoadOp::Load,
        store_op: AttachmentStoreOp::Store,
        clear_color: None,
    }
}

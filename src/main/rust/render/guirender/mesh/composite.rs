//! Composite resources that blit offscreen mesh rasters into the GUI frame.

use super::*;

/// Private Rust-owned resources that composite one PIP raster result into the
/// ordered GUI target. This is intentionally a normal textured GUI pass: the
/// Java PIP target, program, and blit state are neither observed nor reused.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshCompositeResources {
    pub uniform_buffer: Handle,
    pub sampler: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub resource_layout: Handle,
    pub resource_set: Handle,
    pub pipeline_layout: Handle,
    pub pipeline: Handle,
    pub(super) owns_shared: bool,
}

impl GuiMeshCompositeResources {
    pub(crate) fn owns_shared_resources(self) -> bool {
        self.owns_shared
    }

    pub fn create(
        gal: &mut VulkanicGal,
        label: &str,
        color_format: ColorFormat,
        depth_format: Option<TextureFormat>,
        source_color_view: Handle,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.uniform"),
                size: GUI_MESH_MAX_COMPOSITE_UNIFORM_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let sampler = gal.create_sampler(crate::render::vulkanic::resources::SamplerDesc {
                label: format!("{label}.sampler"),
                min_filter: crate::render::vulkanic::resources::SamplerFilter::Nearest,
                mag_filter: crate::render::vulkanic::resources::SamplerFilter::Nearest,
                mip_filter: crate::render::vulkanic::resources::SamplerFilter::Nearest,
                address_u: crate::render::vulkanic::resources::SamplerAddressMode::ClampToEdge,
                address_v: crate::render::vulkanic::resources::SamplerAddressMode::ClampToEdge,
                address_w: crate::render::vulkanic::resources::SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(sampler);
            let (vertex_code, fragment_code) = match gal.capabilities().shader_conventions.glsl_dialect {
                GlslDialect::CoreProfile => (
                    GUI_MESH_COMPOSITE_VERTEX_SHADER_OPENGL,
                    GUI_MESH_COMPOSITE_FRAGMENT_SHADER_OPENGL,
                ),
                GlslDialect::ExplicitBindings => (
                    GUI_MESH_COMPOSITE_VERTEX_SHADER_VULKAN,
                    GUI_MESH_COMPOSITE_FRAGMENT_SHADER_VULKAN,
                ),
            };
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: vertex_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: fragment_code.to_vec(),
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.layout"),
                bindings: vec![
                    dynamic_resource_binding_desc(0, ResourceBindingKind::UniformBuffer),
                    resource_binding_desc(1, ResourceBindingKind::SampledTexture),
                    resource_binding_desc(2, ResourceBindingKind::Sampler),
                ],
            })?;
            created.push(resource_layout);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.set"),
                layout: resource_layout,
                bindings: vec![
                    dynamic_read_binding(
                        0,
                        uniform_buffer,
                        ResourceBindingKind::UniformBuffer,
                        GUI_MESH_COMPOSITE_UNIFORM_BYTES as u64,
                    ),
                    read_binding(1, source_color_view, ResourceBindingKind::SampledTexture),
                    read_binding(2, sampler, ResourceBindingKind::Sampler),
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
                blend: BlendMode::Alpha,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![color_format],
                depth_format,
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(Self {
                uniform_buffer,
                sampler,
                vertex_shader,
                fragment_shader,
                resource_layout,
                resource_set,
                pipeline_layout,
                pipeline,
                owns_shared: true,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        result
    }

    pub fn create_binding(
        gal: &mut VulkanicGal,
        label: &str,
        source_color_view: Handle,
        shared: Self,
    ) -> GalResult<Self> {
        let resource_set = gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.set"),
            layout: shared.resource_layout,
            bindings: vec![
                dynamic_read_binding(
                    0,
                    shared.uniform_buffer,
                    ResourceBindingKind::UniformBuffer,
                    GUI_MESH_COMPOSITE_UNIFORM_BYTES as u64,
                ),
                read_binding(1, source_color_view, ResourceBindingKind::SampledTexture),
                read_binding(2, shared.sampler, ResourceBindingKind::Sampler),
            ],
        })?;
        Ok(Self {
            resource_set,
            owns_shared: false,
            ..shared
        })
    }

    pub fn append_composite(
        &self,
        source: GuiMeshOffscreenTarget,
        source_usage: TextureUsageState,
        destination_pass: Handle,
        destination_target: Handle,
        destination_color_view: Handle,
        destination_depth_view: Option<Handle>,
        draw: &GuiMeshPreparedDraw,
        uniform_offset: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if draw.render_extent != [source.extent.width, source.extent.height] {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh composite source extent does not match its prepared draw",
            ));
        }
        self.append_composite_upload(
            source.color,
            source_usage,
            composite_uniform_bytes(draw),
            uniform_offset,
            operations,
        )?;
        operations.push(CommandOp::BeginPass {
            pass: destination_pass,
            target: destination_target,
            colors: vec![PassAttachment {
                view: destination_color_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: destination_depth_view.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        self.append_composite_draw(uniform_offset, operations);
        operations.push(CommandOp::EndPass);
        Ok(())
    }

    /// Compose a Rust-owned item raster cell. Screen-space geometry remains
    /// distinct from item-local raster geometry, and the caller supplies the
    /// source's explicit usage instead of inferring an implicit framebuffer.
    pub(crate) fn append_item_raster_composite(
        &self,
        source_color: Handle,
        source_usage: TextureUsageState,
        placement: crate::render::guirender::items::raster::GuiItemRasterPlacement,
        destination_pass: Handle,
        destination_target: Handle,
        destination_color_view: Handle,
        destination_depth_view: Option<Handle>,
        screen: &crate::render::guirender::frontend::GuiAffineQuadRequest,
        pre_present_y_flip: bool,
        uniform_offset: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        placement.validate()?;
        let [u0, v0, u1, v1] = placement.composite_uv();
        let mut clip = if screen.clip_mode == 1 {
            [
                screen.clip_left as f32,
                screen.clip_top as f32,
                (screen.clip_left as f32 + screen.clip_width as f32),
                (screen.clip_top as f32 + screen.clip_height as f32),
            ]
        } else {
            [
                0.0,
                0.0,
                screen.projection_extent[0],
                screen.projection_extent[1],
            ]
        };
        let (origin_y, axis_u_y, axis_v_y) = if pre_present_y_flip {
            clip = [
                clip[0],
                screen.projection_extent[1] - clip[3],
                clip[2],
                screen.projection_extent[1] - clip[1],
            ];
            (
                screen.projection_extent[1] - screen.y0,
                screen.y0 - screen.y1,
                screen.y0 - screen.y3,
            )
        } else {
            (screen.y0, screen.y1 - screen.y0, screen.y3 - screen.y0)
        };
        let values = [
            screen.x1 - screen.x0,
            axis_u_y,
            screen.x3 - screen.x0,
            axis_v_y,
            screen.x0,
            origin_y,
            screen.projection_extent[0],
            screen.projection_extent[1],
            0.0,
            0.0,
            1.0,
            1.0,
            u0,
            v0,
            u1 - u0,
            v1 - v0,
            clip[0],
            clip[1],
            clip[2],
            clip[3],
        ];
        if values.iter().any(|v| !v.is_finite())
            || screen.projection_extent.iter().any(|v| *v <= 0.0)
            || screen.clip_mode > 1
            || screen.z != 0.0
        {
            return Err(GalError::invalid_argument(
                "invalid or unsupported item raster composition",
            ));
        }
        self.append_composite_uniforms(
            source_color,
            source_usage,
            destination_pass,
            destination_target,
            destination_color_view,
            destination_depth_view,
            values.into_iter().flat_map(f32::to_le_bytes).collect(),
            uniform_offset,
            operations,
        )
    }

    pub(super) fn append_composite_uniforms(
        &self,
        source_color: Handle,
        source_usage: TextureUsageState,
        destination_pass: Handle,
        destination_target: Handle,
        destination_color_view: Handle,
        destination_depth_view: Option<Handle>,
        uniforms: Vec<u8>,
        uniform_offset: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.append_composite_upload(
            source_color,
            source_usage,
            uniforms,
            uniform_offset,
            operations,
        )?;
        operations.push(CommandOp::BeginPass {
            pass: destination_pass,
            target: destination_target,
            colors: vec![PassAttachment {
                view: destination_color_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: destination_depth_view.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        self.append_composite_draw(uniform_offset, operations);
        operations.push(CommandOp::EndPass);
        Ok(())
    }

    /// Uploads one composite's uniform data and transitions its source image.
    /// The destination pass is deliberately separate so compatible composites
    /// can share one load/store pass without moving source barriers inside it.
    pub(crate) fn append_composite_upload(
        &self,
        source_color: Handle,
        source_usage: TextureUsageState,
        uniforms: Vec<u8>,
        uniform_offset: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if uniform_offset % GUI_MESH_COMPOSITE_UNIFORM_STRIDE != 0
            || uniform_offset
                .checked_add(GUI_MESH_COMPOSITE_UNIFORM_BYTES as u64)
                .map_or(true, |end| end > GUI_MESH_MAX_COMPOSITE_UNIFORM_BYTES)
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh composite uniform stream range is invalid",
            ));
        }
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.uniform_buffer,
            offset: uniform_offset,
            data: uniforms,
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        if source_usage != TextureUsageState::ShaderRead {
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: source_color,
                subresources: None,
                before: source_usage,
                after: TextureUsageState::ShaderRead,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
        }
        Ok(())
    }

    /// Appends the draw portion of a composite to an already-open destination
    /// pass. The caller has already emitted its upload/source barriers.
    pub(crate) fn append_composite_draw(
        &self,
        uniform_offset: u64,
        operations: &mut Vec<CommandOp>,
    ) {
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: vec![uniform_offset],
        });
        operations.push(CommandOp::Draw {
            vertices: 6,
            instances: 1,
        });
    }

    pub fn destroy(self, gal: &mut VulkanicGal) {
        let _ = gal.retire(self.resource_set);
        if !self.owns_shared {
            return;
        }
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.resource_layout,
            self.fragment_shader,
            self.vertex_shader,
            self.sampler,
            self.uniform_buffer,
        ] {
            let _ = gal.retire(handle);
        }
    }
}

pub(crate) fn composite_uniform_bytes(draw: &GuiMeshPreparedDraw) -> Vec<u8> {
    let [m00, m01, m10, m11, m20, m21] = draw.gui_pose;
    let [left, top, right, bottom] = draw.bounds;
    let [width, height] = draw.render_extent;
    let guard = draw.guard_pixels as f32;
    let width = width as f32;
    let height = height as f32;
    let mut bytes = Vec::with_capacity(GUI_MESH_COMPOSITE_UNIFORM_BYTES);
    for value in [
        m00,
        m01,
        m10,
        m11,
        m20,
        m21,
        draw.projection_extent[0],
        draw.projection_extent[1],
        left as f32,
        top as f32,
        right as f32,
        bottom as f32,
        guard / width,
        guard / height,
        (width - guard * 2.0) / width,
        (height - guard * 2.0) / height,
        if draw.clip_mode == 1 {
            draw.clip_left as f32
        } else {
            0.0
        },
        if draw.clip_mode == 1 {
            draw.clip_top as f32
        } else {
            0.0
        },
        if draw.clip_mode == 1 {
            (draw.clip_left + draw.clip_width) as f32
        } else {
            draw.projection_extent[0]
        },
        if draw.clip_mode == 1 {
            (draw.clip_top + draw.clip_height) as f32
        } else {
            draw.projection_extent[1]
        },
    ] {
        bytes.extend_from_slice(&value.to_le_bytes());
    }
    bytes
}

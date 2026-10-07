//! Per-draw GUI mesh pass resources and their recording.

use super::*;

/// Private Rust-owned pass objects for one GUI mesh asset. The texture view
/// and sampler come from the Rust GUI asset cache; Java never observes these
/// handles and no backend resource crosses FFI.
#[derive(Clone, Copy, Debug)]
pub struct GuiMeshPassResources {
    pub vertex_buffer: Handle,
    pub index_buffer: Handle,
    pub uniform_buffer: Handle,
    pub vertex_shader: Handle,
    pub fragment_shader: Handle,
    pub resource_layout: Handle,
    pub resource_set: Handle,
    pub pipeline_layout: Handle,
    pub pipeline: Handle,
    pub(super) owned_program: Option<GuiMeshSharedProgram>,
    owns_geometry: bool,
}

impl GuiMeshPassResources {
    pub fn create(
        gal: &mut VulkanicGal,
        label: &str,
        color_format: ColorFormat,
        texture_view: Handle,
        sampler: Handle,
        material_mode: GuiMeshMaterialMode,
        front_face: crate::render::vulkanic::resources::FrontFace,
    ) -> GalResult<Self> {
        let program = GuiMeshSharedProgram::create(
            gal,
            label,
            color_format,
            Some(TextureFormat::Depth32Float),
            material_mode,
            front_face,
        )?;
        match Self::create_with_shared_program(gal, label, texture_view, sampler, program) {
            Ok(mut resources) => {
                resources.owned_program = Some(program);
                Ok(resources)
            }
            Err(error) => {
                program.destroy(gal);
                Err(error)
            }
        }
    }

    /// Creates mutable asset resources which borrow an immutable program owned
    /// by the caller. The caller must destroy assets before that program.
    pub fn create_with_shared_program(
        gal: &mut VulkanicGal,
        label: &str,
        texture_view: Handle,
        sampler: Handle,
        program: GuiMeshSharedProgram,
    ) -> GalResult<Self> {
        Self::create_with_shared_streams(gal, label, texture_view, sampler, program, None)
    }

    /// Borrows frontend-owned geometry streams when supplied.
    pub fn create_with_shared_streams(
        gal: &mut VulkanicGal, label: &str, texture_view: Handle, sampler: Handle,
        program: GuiMeshSharedProgram, streams: Option<(Handle, Handle)>,
    ) -> GalResult<Self> {
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let (vertex_buffer, index_buffer) = if let Some(streams) = streams { streams } else {
            let vertex_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.vertices"),
                size: GUI_MESH_MAX_VERTEX_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Storage,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(vertex_buffer);
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.indices"),
                size: GUI_MESH_MAX_INDEX_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Index,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(index_buffer);
            (vertex_buffer, index_buffer)
            };
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.frame"),
                size: GUI_MESH_FRAME_UNIFORM_BYTES as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::Uniform,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(uniform_buffer);
            let resource_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.set"),
                layout: program.resource_layout,
                bindings: vec![
                    read_binding(0, vertex_buffer, ResourceBindingKind::StorageBuffer),
                    read_binding(1, uniform_buffer, ResourceBindingKind::UniformBuffer),
                    read_binding(2, texture_view, ResourceBindingKind::SampledTexture),
                    read_binding(3, sampler, ResourceBindingKind::Sampler),
                ],
            })?;
            created.push(resource_set);
            Ok(Self {
                vertex_buffer,
                index_buffer,
                uniform_buffer,
                vertex_shader: program.vertex_shader,
                fragment_shader: program.fragment_shader,
                resource_layout: program.resource_layout,
                resource_set,
                pipeline_layout: program.pipeline_layout,
                pipeline: program.pipeline,
                owned_program: None,
                owns_geometry: streams.is_none(),
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub fn append_draw(
        &self,
        target: GuiMeshOffscreenTarget,
        draw: &GuiMeshPreparedDraw,
        stream: GuiMeshStreamRange,
        clear: bool,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.append_draw_internal(target, draw, stream, clear, true, operations)
    }

    /// Appends a draw that reuses an already uploaded immutable geometry range.
    /// Uniforms and raster/composite state remain explicit per draw; only the
    /// redundant vertex/index host writes are omitted.
    pub fn append_draw_reusing_geometry(
        &self,
        target: GuiMeshOffscreenTarget,
        draw: &GuiMeshPreparedDraw,
        stream: GuiMeshStreamRange,
        clear: bool,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        self.append_draw_internal(target, draw, stream, clear, false, operations)
    }

    /// Rasterizes a semantic panorama directly into the acquired Rust frame
    /// target. Unlike item PIP meshes, Frozen's panorama is a native-resolution
    /// full-frame pass and has no intermediate texture to magnify afterwards.
    pub fn append_direct_frame_draw(
        &self,
        pass: Handle,
        target: Handle,
        color_attachment: Handle,
        depth_attachment: Option<Handle>,
        draw: &GuiMeshPreparedDraw,
        stream: GuiMeshStreamRange,
        write_geometry: bool,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if draw.material_mode != GuiMeshMaterialMode::Panorama {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "only the semantic panorama may bypass the private GUI mesh target",
            ));
        }
        if stream.vertex_offset % GUI_MESH_GPU_VERTEX_BYTES as u64 != 0
            || stream.index_offset % std::mem::size_of::<u32>() as u64 != 0
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh stream ranges must align to their vertex and index elements",
            ));
        }
        let vertex_base = u32::try_from(stream.vertex_offset / GUI_MESH_GPU_VERTEX_BYTES as u64)
            .map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh vertex stream offset exceeds u32 indices",
                )
            })?;
        // Resident geometry was packed and range-checked when it was written;
        // only its byte extents are needed to validate the stream range.
        let (vertex_bytes, index_bytes) = if write_geometry {
            (packed_vertices(draw), packed_indices_with_base(&draw.indices, vertex_base)?)
        } else {
            (Vec::new(), Vec::new())
        };
        let vertex_len = (draw.vertices.len() * GUI_MESH_GPU_VERTEX_BYTES) as u64;
        let index_len = (draw.indices.len() * std::mem::size_of::<u32>()) as u64;
        let vertex_end = stream
            .vertex_offset
            .checked_add(vertex_len)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh vertex stream range overflows",
                )
            })?;
        let index_end = stream
            .index_offset
            .checked_add(index_len)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh index stream range overflows",
                )
            })?;
        if vertex_end > GUI_MESH_MAX_VERTEX_BYTES || index_end > GUI_MESH_MAX_INDEX_BYTES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh draws exceed their persistent stream capacity",
            ));
        }
        let mut buffers = vec![(self.uniform_buffer, TextureUsageState::ShaderRead)];
        if write_geometry {
            buffers.insert(0, (self.vertex_buffer, TextureUsageState::ShaderRead));
            buffers.insert(1, (self.index_buffer, TextureUsageState::IndexRead));
        }
        for (buffer, before) in buffers {
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                before,
                TextureUsageState::TransferDst,
            )));
        }
        if write_geometry {
            operations.push(CommandOp::HostWriteBuffer {
                buffer: self.vertex_buffer,
                offset: stream.vertex_offset,
                data: vertex_bytes,
            });
            operations.push(CommandOp::HostWriteBuffer {
                buffer: self.index_buffer,
                offset: stream.index_offset,
                data: index_bytes,
            });
        }
        // Panorama vertices are supplied in logical GUI coordinates, whereas
        // the target is the physical acquired frame. Clip-space conversion
        // intentionally follows the semantic coordinate domain.
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.uniform_buffer,
            offset: 0,
            data: draw_frame_uniform_bytes(draw, draw.projection_extent),
        });
        if write_geometry {
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.vertex_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.index_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        )));
        }
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view: color_attachment,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: depth_attachment.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::SetIndexBuffer {
            buffer: self.index_buffer,
            offset: stream.index_offset,
            index_type: IndexType::U32,
        });
        operations.push(CommandOp::DrawIndexed {
            indices: draw.indices.len() as u32,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
        Ok(())
    }

    pub(super) fn append_draw_internal(
        &self,
        target: GuiMeshOffscreenTarget,
        draw: &GuiMeshPreparedDraw,
        stream: GuiMeshStreamRange,
        clear: bool,
        write_geometry: bool,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if draw.render_extent != [target.extent.width, target.extent.height] {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh draw offscreen extent does not match its Rust-owned target",
            ));
        }
        // The raster target is sampled by the preceding item's composite pass.
        // Only the first layer transitions it back to attachment-write ownership;
        // consecutive layers remain in COLOR_ATTACHMENT_OPTIMAL until the single
        // composite pass below. A newly staged target is still UNDEFINED, while a
        // cached target was left in SHADER_READ_ONLY by that prior composite.
        if clear {
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: target.color,
                subresources: None,
                before: if target.initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                after: TextureUsageState::ColorAttachment,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
            // The depth attachment is a separate explicit resource. Clearing
            // it in BeginPass does not transition an UNDEFINED Vulkan image.
            operations.push(CommandOp::Barrier(ResourceBarrier {
                resource: target.depth,
                subresources: None,
                before: if target.initialized {
                    TextureUsageState::DepthStencilAttachment
                } else {
                    TextureUsageState::Undefined
                },
                after: TextureUsageState::DepthStencilAttachment,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            }));
        }
        if stream.vertex_offset % GUI_MESH_GPU_VERTEX_BYTES as u64 != 0
            || stream.index_offset % std::mem::size_of::<u32>() as u64 != 0
        {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh stream ranges must align to their vertex and index elements",
            ));
        }
        let vertex_base = u32::try_from(stream.vertex_offset / GUI_MESH_GPU_VERTEX_BYTES as u64)
            .map_err(|_| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh vertex stream offset exceeds u32 indices",
                )
            })?;
        // Resident geometry was packed and range-checked when it was written;
        // only its byte extents are needed to validate the stream range.
        let (vertex_bytes, index_bytes) = if write_geometry {
            (packed_vertices(draw), packed_indices_with_base(&draw.indices, vertex_base)?)
        } else {
            (Vec::new(), Vec::new())
        };
        let vertex_len = (draw.vertices.len() * GUI_MESH_GPU_VERTEX_BYTES) as u64;
        let index_len = (draw.indices.len() * std::mem::size_of::<u32>()) as u64;
        let vertex_end = stream
            .vertex_offset
            .checked_add(vertex_len)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh vertex stream range overflows",
                )
            })?;
        let index_end = stream
            .index_offset
            .checked_add(index_len)
            .ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    "GUI mesh index stream range overflows",
                )
            })?;
        if vertex_end > GUI_MESH_MAX_VERTEX_BYTES || index_end > GUI_MESH_MAX_INDEX_BYTES {
            return Err(GalError::ffi(
                StatusCode::InvalidArgument,
                "GUI mesh draws exceed their persistent stream capacity",
            ));
        }
        let mut buffers = vec![(self.uniform_buffer, TextureUsageState::ShaderRead)];
        if write_geometry {
            buffers.insert(0, (self.vertex_buffer, TextureUsageState::ShaderRead));
            // Persistent GUI index storage was last consumed by the index
            // input stage, not by a shader. Preserve that explicit state so
            // the upload barrier also synchronizes the prior indexed draw.
            buffers.insert(1, (self.index_buffer, TextureUsageState::IndexRead));
        }
        for (buffer, before) in buffers {
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                before,
                TextureUsageState::TransferDst,
            )));
        }
        if write_geometry {
            operations.push(CommandOp::HostWriteBuffer {
                buffer: self.vertex_buffer,
                offset: stream.vertex_offset,
                data: vertex_bytes,
            });
            operations.push(CommandOp::HostWriteBuffer {
                buffer: self.index_buffer,
                offset: stream.index_offset,
                data: index_bytes,
            });
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.uniform_buffer,
            offset: 0,
            data: draw_frame_uniform_bytes(draw, draw.render_extent.map(|axis| axis as f32)),
        });
        if write_geometry {
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.vertex_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.index_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        )));
        }
        operations.push(CommandOp::Barrier(buffer_barrier(
            self.uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations.push(CommandOp::BeginPass {
            pass: target.pass,
            target: target.target,
            colors: vec![PassAttachment {
                view: target.color_view,
                load_op: if clear {
                    AttachmentLoadOp::Clear
                } else {
                    AttachmentLoadOp::Load
                },
                store_op: AttachmentStoreOp::Store,
                clear_color: clear.then_some(ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 0.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: target.depth_view,
                load_op: if clear {
                    AttachmentLoadOp::Clear
                } else {
                    AttachmentLoadOp::Load
                },
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        operations.push(CommandOp::BindGraphicsPipeline(self.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: self.pipeline_layout,
            set_index: 0,
            set: self.resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::SetIndexBuffer {
            buffer: self.index_buffer,
            offset: stream.index_offset,
            index_type: IndexType::U32,
        });
        operations.push(CommandOp::DrawIndexed {
            indices: draw.indices.len() as u32,
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
        Ok(())
    }

    /// Transfers stream ownership to the frontend, which releases all sets first.
    pub fn transfer_geometry_ownership(&mut self) -> (Handle, Handle) {
        self.owns_geometry = false;
        (self.vertex_buffer, self.index_buffer)
    }

    pub fn destroy_asset_resources(self, gal: &mut VulkanicGal) {
        for handle in [self.resource_set, self.uniform_buffer] { let _ = gal.destroy(handle); }
        if self.owns_geometry {
            for handle in [self.index_buffer, self.vertex_buffer] { let _ = gal.destroy(handle); }
        }
    }

    pub fn destroy(self, gal: &mut VulkanicGal) {
        self.destroy_asset_resources(gal);
        if let Some(program) = self.owned_program {
            program.destroy(gal);
        }
    }
}

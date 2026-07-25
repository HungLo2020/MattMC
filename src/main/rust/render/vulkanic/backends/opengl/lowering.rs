use std::collections::{BTreeMap, VecDeque};
use std::rc::Rc;

use glow::HasContext;

use super::resources::{texture_format, topology, OpenGlObjects, ResourceSetObject};
use super::trace;
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, BufferImageCopyRegion, CommandOp, ValidatedSubmissionBatch,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{BlendMode, CompareOp, CullMode, ResourceBindingKind};
use crate::render::vulkanic::sync::SubmissionId;

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::vulkanic) struct CompletedHostRead {
    pub(in crate::render::vulkanic) submission: SubmissionId,
    pub(in crate::render::vulkanic) buffer: Handle,
    pub(in crate::render::vulkanic) offset: u64,
    pub(in crate::render::vulkanic) bytes: Vec<u8>,
}

pub(super) struct OpenGlLowerer {
    gl: Rc<glow::Context>,
    pending: VecDeque<ValidatedSubmissionBatch>,
    completed: SubmissionId,
    completed_host_reads: Vec<CompletedHostRead>,
    gl_errors: Vec<String>,
    cache: StateCache,
}

impl OpenGlLowerer {
    pub(super) fn new(gl: Rc<glow::Context>) -> Self {
        Self {
            gl,
            pending: VecDeque::new(),
            completed: SubmissionId(0),
            completed_host_reads: Vec::new(),
            gl_errors: Vec::new(),
            cache: StateCache::default(),
        }
    }

    pub(super) fn encode(&mut self, batch: &ValidatedSubmissionBatch) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.lowering.own-validated-batch");
        self.pending.push_back(batch.clone());
        Ok(())
    }

    pub(super) fn submit(
        &mut self,
        id: SubmissionId,
        objects: &mut OpenGlObjects,
    ) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.lowering.execute-submission");
        let Some(batch) = self.pending.pop_front() else {
            return Err(GalError::backend(
                "OpenGL submit called without encoded commands",
            ));
        };
        let mut state = ExecutionState::default();
        for list in &batch.command_lists {
            for op in &list.operations {
                self.execute_op(id, objects, &mut state, op)?;
                self.check_errors(&format!("command in {}", list.label))?;
            }
        }
        unsafe {
            let _zone = trace::Zone::new("opengl.backend.flush-finish");
            self.gl.flush();
            self.gl.finish();
        }
        self.completed = id;
        Ok(())
    }

    pub(super) fn completed_submission(&self) -> SubmissionId {
        self.completed
    }

    pub(super) fn retire(&mut self, completed: SubmissionId) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.backend.retirement");
        if completed > self.completed {
            self.completed = completed;
        }
        Ok(())
    }

    pub(super) fn completed_host_reads_snapshot(&self) -> &[CompletedHostRead] {
        &self.completed_host_reads
    }

    #[cfg(test)]
    pub(super) fn gl_errors_for_test(&self) -> &[String] {
        &self.gl_errors
    }

    #[cfg(test)]
    pub(super) fn state_cache_for_test(&self) -> StateCacheSnapshot {
        StateCacheSnapshot {
            program_binds: self.cache.program_binds,
            vao_binds: self.cache.vao_binds,
            framebuffer_binds: self.cache.framebuffer_binds,
            texture_binds: self.cache.texture_binds,
            sampler_binds: self.cache.sampler_binds,
            state_changes: self.cache.state_changes,
        }
    }

    fn execute_op(
        &mut self,
        id: SubmissionId,
        objects: &mut OpenGlObjects,
        state: &mut ExecutionState,
        op: &CommandOp,
    ) -> GalResult<()> {
        match op {
            CommandOp::HostWriteBuffer {
                buffer,
                offset,
                data,
            } => self.host_write(objects, *buffer, *offset, data),
            CommandOp::CopyBuffer { src, dst, size } => {
                let _zone = trace::Zone::new("opengl.lowering.copy-buffer");
                let (src_gl_buffer, bytes) = {
                    let src = objects.buffer(*src)?;
                    (
                        src.buffer,
                        src.shadow[..usize::try_from(*size)
                            .map_err(|_| GalError::backend("copy size exceeds usize"))?]
                            .to_vec(),
                    )
                };
                let dst_object = objects.buffer_mut(*dst)?;
                let copy_len = bytes.len().min(dst_object.shadow.len());
                dst_object.shadow[..copy_len].copy_from_slice(&bytes[..copy_len]);
                unsafe {
                    self.gl
                        .bind_buffer(glow::COPY_READ_BUFFER, Some(src_gl_buffer));
                    self.gl
                        .bind_buffer(glow::COPY_WRITE_BUFFER, Some(dst_object.buffer));
                    self.gl.copy_buffer_sub_data(
                        glow::COPY_READ_BUFFER,
                        glow::COPY_WRITE_BUFFER,
                        0,
                        0,
                        i32::try_from(*size)
                            .map_err(|_| GalError::backend("copy size exceeds i32"))?,
                    );
                }
                Ok(())
            }
            CommandOp::CopyBufferToTexture(region) => self.copy_buffer_to_texture(objects, region),
            CommandOp::CopyTextureToBuffer(region) => self.copy_texture_to_buffer(objects, region),
            CommandOp::HostReadBuffer {
                buffer,
                offset,
                size,
            } => {
                let _zone = trace::Zone::new("opengl.backend.host-readback");
                let buffer_object = objects.buffer(*buffer)?;
                let start = usize::try_from(*offset)
                    .map_err(|_| GalError::backend("read offset exceeds usize"))?;
                let end = start
                    .checked_add(
                        usize::try_from(*size)
                            .map_err(|_| GalError::backend("read size exceeds usize"))?,
                    )
                    .ok_or_else(|| GalError::backend("read range overflows"))?;
                self.completed_host_reads.push(CompletedHostRead {
                    submission: id,
                    buffer: *buffer,
                    offset: *offset,
                    bytes: buffer_object.shadow[start..end].to_vec(),
                });
                Ok(())
            }
            CommandOp::BeginPass {
                pass,
                target,
                colors,
                depth_stencil,
            } => {
                let _zone = trace::Zone::new("opengl.lowering.begin-pass");
                let pass_object = objects.render_pass(*pass)?;
                let target_object = objects.pass_target(*target)?;
                if pass_object.target != *target {
                    return Err(GalError::backend(
                        "OpenGL render pass target mismatch during lowering",
                    ));
                }
                self.bind_framebuffer(target_object.framebuffer);
                unsafe {
                    self.gl.viewport(
                        0,
                        0,
                        i32::try_from(target_object.extent.width)
                            .map_err(|_| GalError::backend("viewport width exceeds i32"))?,
                        i32::try_from(target_object.extent.height)
                            .map_err(|_| GalError::backend("viewport height exceeds i32"))?,
                    );
                    self.gl.scissor(
                        0,
                        0,
                        i32::try_from(target_object.extent.width)
                            .map_err(|_| GalError::backend("scissor width exceeds i32"))?,
                        i32::try_from(target_object.extent.height)
                            .map_err(|_| GalError::backend("scissor height exceeds i32"))?,
                    );
                    self.gl.enable(glow::SCISSOR_TEST);
                    let mut mask = 0;
                    for color in colors {
                        if color.load_op == AttachmentLoadOp::Clear {
                            let clear = color.clear_color.unwrap_or(
                                crate::render::vulkanic::commands::ClearColor {
                                    r: 0.0,
                                    g: 0.0,
                                    b: 0.0,
                                    a: 0.0,
                                },
                            );
                            self.gl.clear_color(clear.r, clear.g, clear.b, clear.a);
                            mask |= glow::COLOR_BUFFER_BIT;
                        }
                    }
                    if let Some(depth) = depth_stencil {
                        if depth.load_op == AttachmentLoadOp::Clear {
                            self.gl.clear_depth_f32(1.0);
                            mask |= glow::DEPTH_BUFFER_BIT;
                        }
                    }
                    if mask != 0 {
                        self.gl.clear(mask);
                    }
                }
                state.in_pass = true;
                state.target = Some(*target);
                Ok(())
            }
            CommandOp::EndPass => {
                let _zone = trace::Zone::new("opengl.lowering.end-pass");
                unsafe {
                    self.gl.disable(glow::SCISSOR_TEST);
                }
                state.in_pass = false;
                state.pipeline = None;
                state.pipeline_layout = None;
                state.index_buffer = None;
                state.bound_sets.clear();
                Ok(())
            }
            CommandOp::BindGraphicsPipeline(handle) => {
                let _zone = trace::Zone::new("opengl.lowering.bind-graphics-pipeline");
                let pipeline = objects.graphics_pipeline(*handle)?;
                self.bind_program(Some(pipeline.program));
                self.bind_vao(Some(pipeline.vao));
                self.apply_fixed_state(pipeline.cull_mode, pipeline.blend, pipeline.depth_compare);
                state.pipeline = Some(*handle);
                state.pipeline_layout = Some(pipeline.layout);
                state.topology = topology(pipeline.topology);
                Ok(())
            }
            CommandOp::BindResourceSet {
                pipeline_layout,
                set_index,
                set,
            } => {
                let _zone = trace::Zone::new("opengl.lowering.bind-resource-set");
                let pipeline_layout_object = objects.pipeline_layout(*pipeline_layout)?;
                let Some(expected_layout) = pipeline_layout_object
                    .resource_layouts
                    .get(*set_index as usize)
                else {
                    return Err(GalError::backend("OpenGL resource set index out of range"));
                };
                let set_object = objects.resource_set(*set)?;
                if *expected_layout != set_object.layout {
                    return Err(GalError::backend(
                        "OpenGL resource set layout mismatch during lowering",
                    ));
                }
                state.bound_sets.insert(*set_index, *set);
                self.bind_resource_set(objects, set_object)?;
                Ok(())
            }
            CommandOp::SetIndexBuffer { buffer, offset } => {
                let _zone = trace::Zone::new("opengl.lowering.bind-index-buffer");
                let buffer_object = objects.buffer(*buffer)?;
                unsafe {
                    self.gl
                        .bind_buffer(glow::ELEMENT_ARRAY_BUFFER, Some(buffer_object.buffer));
                }
                state.index_buffer = Some((*buffer, *offset));
                Ok(())
            }
            CommandOp::DrawIndexed { indices, instances } => {
                let _zone = trace::Zone::new("opengl.lowering.draw-indexed");
                let Some((_, offset)) = state.index_buffer else {
                    return Err(GalError::backend("indexed draw missing index buffer"));
                };
                unsafe {
                    self.gl.draw_elements_instanced(
                        state.topology,
                        i32::try_from(*indices)
                            .map_err(|_| GalError::backend("index count exceeds i32"))?,
                        glow::UNSIGNED_INT,
                        i32::try_from(offset)
                            .map_err(|_| GalError::backend("index offset exceeds i32"))?,
                        i32::try_from(*instances)
                            .map_err(|_| GalError::backend("instance count exceeds i32"))?,
                    );
                }
                Ok(())
            }
            CommandOp::Barrier(_) => Ok(()),
            CommandOp::BindComputePipeline(_)
            | CommandOp::Dispatch { .. }
            | CommandOp::DispatchIndirect { .. }
            | CommandOp::Draw { .. }
            | CommandOp::DrawIndirect { .. }
            | CommandOp::Present { .. }
            | CommandOp::SetVertexBuffer { .. } => Err(GalError::backend(format!(
                "OpenGL backend does not support command in isolated path: {op:?}"
            ))),
        }
    }

    fn host_write(
        &mut self,
        objects: &mut OpenGlObjects,
        buffer: Handle,
        offset: u64,
        data: &[u8],
    ) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.lowering.host-write");
        let buffer_object = objects.buffer_mut(buffer)?;
        let start =
            usize::try_from(offset).map_err(|_| GalError::backend("write offset exceeds usize"))?;
        let end = start
            .checked_add(data.len())
            .ok_or_else(|| GalError::backend("write range overflows"))?;
        buffer_object.shadow[start..end].copy_from_slice(data);
        unsafe {
            self.gl
                .bind_buffer(glow::COPY_WRITE_BUFFER, Some(buffer_object.buffer));
            self.gl.buffer_sub_data_u8_slice(
                glow::COPY_WRITE_BUFFER,
                i32::try_from(offset).map_err(|_| GalError::backend("write offset exceeds i32"))?,
                data,
            );
        }
        Ok(())
    }

    fn copy_buffer_to_texture(
        &mut self,
        objects: &OpenGlObjects,
        region: &BufferImageCopyRegion,
    ) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.lowering.copy-buffer-to-texture");
        let source = objects.buffer(region.buffer)?;
        let texture = objects.texture(region.texture)?;
        let format = texture_format(texture.format)?;
        let bytes_per_row = usize::try_from(region.bytes_per_row)
            .map_err(|_| GalError::backend("bytes_per_row exceeds usize"))?;
        let rows = usize::try_from(region.extent.height)
            .map_err(|_| GalError::backend("copy row count exceeds usize"))?;
        let row_bytes = usize::try_from(region.extent.width)
            .map_err(|_| GalError::backend("copy width exceeds usize"))?
            .checked_mul(format.bytes_per_pixel as usize)
            .ok_or_else(|| GalError::backend("buffer texture copy row size overflows"))?;
        let src_start = usize::try_from(region.buffer_offset)
            .map_err(|_| GalError::backend("buffer offset exceeds usize"))?;
        let src_end = src_start
            .checked_add(
                bytes_per_row
                    .checked_mul(rows)
                    .ok_or_else(|| GalError::backend("buffer texture copy byte count overflows"))?,
            )
            .ok_or_else(|| GalError::backend("buffer texture copy range overflows"))?;
        let bytes = &source.shadow[src_start..src_end];
        let mut upload = vec![0; row_bytes * rows];
        for row in 0..rows {
            let src = (rows - 1 - row) * bytes_per_row;
            let dst = row * row_bytes;
            upload[dst..dst + row_bytes].copy_from_slice(&bytes[src..src + row_bytes]);
        }
        let gl_y = gl_y_for_top_left_region(texture, region)?;
        unsafe {
            self.gl.pixel_store_i32(glow::UNPACK_ALIGNMENT, 1);
            self.gl
                .bind_texture(glow::TEXTURE_2D, Some(texture.texture));
            self.gl.tex_sub_image_2d(
                glow::TEXTURE_2D,
                i32::try_from(region.texture_mip)
                    .map_err(|_| GalError::backend("texture mip exceeds i32"))?,
                i32::try_from(region.texture_origin.x)
                    .map_err(|_| GalError::backend("texture origin x exceeds i32"))?,
                gl_y,
                i32::try_from(region.extent.width)
                    .map_err(|_| GalError::backend("texture width exceeds i32"))?,
                i32::try_from(region.extent.height)
                    .map_err(|_| GalError::backend("texture height exceeds i32"))?,
                format.external,
                format.ty,
                glow::PixelUnpackData::Slice(Some(&upload)),
            );
        }
        Ok(())
    }

    fn copy_texture_to_buffer(
        &mut self,
        objects: &mut OpenGlObjects,
        region: &BufferImageCopyRegion,
    ) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.lowering.copy-texture-to-buffer");
        let texture = objects.texture(region.texture)?;
        let format = texture_format(texture.format)?;
        if texture.extent.depth != 1 || region.extent.depth != 1 {
            return Err(GalError::backend(
                "OpenGL readback only supports D2 texture regions",
            ));
        }
        let width = usize::try_from(region.extent.width)
            .map_err(|_| GalError::backend("readback width exceeds usize"))?;
        let height = usize::try_from(region.extent.height)
            .map_err(|_| GalError::backend("readback height exceeds usize"))?;
        let row_bytes = width
            .checked_mul(format.bytes_per_pixel as usize)
            .ok_or_else(|| GalError::backend("readback row size overflows"))?;
        let mut pixels = vec![0; row_bytes * height];
        let read_fbo = unsafe { self.gl.create_framebuffer() }.map_err(|error| {
            GalError::backend(format!("failed to create readback FBO: {error}"))
        })?;
        let gl_y = gl_y_for_top_left_region(texture, region)?;
        unsafe {
            self.gl
                .bind_framebuffer(glow::READ_FRAMEBUFFER, Some(read_fbo));
            self.gl.framebuffer_texture_2d(
                glow::READ_FRAMEBUFFER,
                glow::COLOR_ATTACHMENT0,
                glow::TEXTURE_2D,
                Some(texture.texture),
                i32::try_from(region.texture_mip)
                    .map_err(|_| GalError::backend("texture mip exceeds i32"))?,
            );
            self.gl.read_buffer(glow::COLOR_ATTACHMENT0);
            self.gl.pixel_store_i32(glow::PACK_ALIGNMENT, 1);
            self.gl.read_pixels(
                i32::try_from(region.texture_origin.x)
                    .map_err(|_| GalError::backend("texture origin x exceeds i32"))?,
                gl_y,
                i32::try_from(region.extent.width)
                    .map_err(|_| GalError::backend("texture width exceeds i32"))?,
                i32::try_from(region.extent.height)
                    .map_err(|_| GalError::backend("texture height exceeds i32"))?,
                format.external,
                format.ty,
                glow::PixelPackData::Slice(Some(&mut pixels)),
            );
            self.gl.bind_framebuffer(glow::READ_FRAMEBUFFER, None);
            self.gl.delete_framebuffer(read_fbo);
        }
        flip_rows_in_place(&mut pixels, row_bytes, height);
        let target = objects.buffer_mut(region.buffer)?;
        let dst_start = usize::try_from(region.buffer_offset)
            .map_err(|_| GalError::backend("readback buffer offset exceeds usize"))?;
        let bytes_per_row = usize::try_from(region.bytes_per_row)
            .map_err(|_| GalError::backend("readback bytes_per_row exceeds usize"))?;
        for row in 0..height {
            let src = row * row_bytes;
            let dst = dst_start + row * bytes_per_row;
            target.shadow[dst..dst + row_bytes].copy_from_slice(&pixels[src..src + row_bytes]);
        }
        unsafe {
            self.gl
                .bind_buffer(glow::COPY_WRITE_BUFFER, Some(target.buffer));
            self.gl.buffer_sub_data_u8_slice(
                glow::COPY_WRITE_BUFFER,
                i32::try_from(region.buffer_offset)
                    .map_err(|_| GalError::backend("readback buffer offset exceeds i32"))?,
                &target.shadow[dst_start..dst_start + bytes_per_row * height],
            );
        }
        Ok(())
    }

    fn bind_resource_set(
        &mut self,
        objects: &OpenGlObjects,
        set: &ResourceSetObject,
    ) -> GalResult<()> {
        let mut sampler_bindings = Vec::new();
        for binding in &set.bindings {
            match binding.kind {
                ResourceBindingKind::SampledTexture => {
                    let view = objects.texture_view(binding.resource)?;
                    let texture = objects.texture(view.texture)?;
                    let unit = binding.binding;
                    self.bind_texture_unit(unit, Some(texture.texture));
                    self.bind_sampler_unit(unit, None);
                    unsafe {
                        if let Some(program) = self.cache.program {
                            if let Some(location) = self.gl.get_uniform_location(program, "tex0") {
                                self.gl.uniform_1_i32(Some(&location), unit as i32);
                            }
                        }
                    }
                }
                ResourceBindingKind::Sampler => {
                    sampler_bindings.push(binding.resource);
                }
                ResourceBindingKind::UniformBuffer | ResourceBindingKind::StorageBuffer => {
                    let buffer = objects.buffer(binding.resource)?;
                    let target = if binding.kind == ResourceBindingKind::UniformBuffer {
                        glow::UNIFORM_BUFFER
                    } else {
                        glow::SHADER_STORAGE_BUFFER
                    };
                    let offset = binding.dynamic_offsets.first().copied().unwrap_or(0);
                    unsafe {
                        self.gl.bind_buffer_range(
                            target,
                            binding.binding,
                            Some(buffer.buffer),
                            i32::try_from(offset)
                                .map_err(|_| GalError::backend("dynamic offset exceeds i32"))?,
                            i32::try_from(buffer.size.saturating_sub(offset))
                                .map_err(|_| GalError::backend("dynamic range exceeds i32"))?,
                        );
                    }
                }
                ResourceBindingKind::StorageTexture => {
                    return Err(GalError::backend(
                        "OpenGL storage texture binding is not supported in the isolated path",
                    ))
                }
            }
        }
        for sampler in sampler_bindings {
            let sampler = objects.sampler(sampler)?;
            self.bind_sampler_unit(0, Some(sampler.sampler));
        }
        Ok(())
    }

    fn apply_fixed_state(
        &mut self,
        cull_mode: CullMode,
        blend: BlendMode,
        depth_compare: Option<CompareOp>,
    ) {
        unsafe {
            if self.cache.cull != Some(cull_mode) {
                match cull_mode {
                    CullMode::None => self.gl.disable(glow::CULL_FACE),
                    CullMode::Front => {
                        self.gl.enable(glow::CULL_FACE);
                        self.gl.cull_face(glow::FRONT);
                    }
                    CullMode::Back => {
                        self.gl.enable(glow::CULL_FACE);
                        self.gl.cull_face(glow::BACK);
                    }
                }
                self.cache.cull = Some(cull_mode);
                self.cache.state_changes += 1;
            }
            if self.cache.blend != Some(blend) {
                match blend {
                    BlendMode::Disabled => self.gl.disable(glow::BLEND),
                    BlendMode::Alpha => {
                        self.gl.enable(glow::BLEND);
                        self.gl.blend_func_separate(
                            glow::SRC_ALPHA,
                            glow::ONE_MINUS_SRC_ALPHA,
                            glow::ONE,
                            glow::ONE_MINUS_SRC_ALPHA,
                        );
                    }
                    BlendMode::Additive => {
                        self.gl.enable(glow::BLEND);
                        self.gl
                            .blend_func_separate(glow::ONE, glow::ONE, glow::ONE, glow::ONE);
                    }
                }
                self.cache.blend = Some(blend);
                self.cache.state_changes += 1;
            }
            if self.cache.depth_compare != depth_compare {
                if let Some(compare) = depth_compare {
                    self.gl.enable(glow::DEPTH_TEST);
                    self.gl.depth_func(compare_op(compare));
                    self.gl.depth_mask(true);
                } else {
                    self.gl.disable(glow::DEPTH_TEST);
                }
                self.cache.depth_compare = depth_compare;
                self.cache.state_changes += 1;
            }
        }
    }

    fn bind_program(&mut self, program: Option<glow::Program>) {
        if self.cache.program == program {
            return;
        }
        unsafe {
            self.gl.use_program(program);
        }
        self.cache.program = program;
        self.cache.program_binds += 1;
    }

    fn bind_vao(&mut self, vao: Option<glow::VertexArray>) {
        if self.cache.vao == vao {
            return;
        }
        unsafe {
            self.gl.bind_vertex_array(vao);
        }
        self.cache.vao = vao;
        self.cache.vao_binds += 1;
    }

    fn bind_framebuffer(&mut self, framebuffer: Option<glow::Framebuffer>) {
        if self.cache.framebuffer == framebuffer {
            return;
        }
        unsafe {
            self.gl.bind_framebuffer(glow::FRAMEBUFFER, framebuffer);
        }
        self.cache.framebuffer = framebuffer;
        self.cache.framebuffer_binds += 1;
    }

    fn bind_texture_unit(&mut self, unit: u32, texture: Option<glow::Texture>) {
        if self.cache.textures.get(&unit).copied().flatten() == texture {
            return;
        }
        unsafe {
            self.gl.active_texture(glow::TEXTURE0 + unit);
            self.gl.bind_texture(glow::TEXTURE_2D, texture);
        }
        self.cache.textures.insert(unit, texture);
        self.cache.texture_binds += 1;
    }

    fn bind_sampler_unit(&mut self, unit: u32, sampler: Option<glow::Sampler>) {
        if self.cache.samplers.get(&unit).copied().flatten() == sampler {
            return;
        }
        unsafe {
            self.gl.bind_sampler(unit, sampler);
        }
        self.cache.samplers.insert(unit, sampler);
        self.cache.sampler_binds += 1;
    }

    fn check_errors(&mut self, context: &str) -> GalResult<()> {
        let mut saw_error = false;
        loop {
            let error = unsafe { self.gl.get_error() };
            if error == glow::NO_ERROR {
                break;
            }
            saw_error = true;
            self.gl_errors
                .push(format!("OpenGL error 0x{error:04x} after {context}"));
        }
        if saw_error
            && std::env::var("MATTMC_OPENGL_STRICT")
                .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
                .unwrap_or(false)
        {
            return Err(GalError::backend(format!(
                "OpenGL strict error scan failed after {context}"
            )));
        }
        Ok(())
    }
}

#[derive(Default)]
struct ExecutionState {
    in_pass: bool,
    target: Option<Handle>,
    pipeline: Option<Handle>,
    pipeline_layout: Option<Handle>,
    index_buffer: Option<(Handle, u64)>,
    topology: u32,
    bound_sets: BTreeMap<u32, Handle>,
}

#[derive(Default)]
struct StateCache {
    program: Option<glow::Program>,
    vao: Option<glow::VertexArray>,
    framebuffer: Option<glow::Framebuffer>,
    cull: Option<CullMode>,
    blend: Option<BlendMode>,
    depth_compare: Option<CompareOp>,
    textures: BTreeMap<u32, Option<glow::Texture>>,
    samplers: BTreeMap<u32, Option<glow::Sampler>>,
    program_binds: usize,
    vao_binds: usize,
    framebuffer_binds: usize,
    texture_binds: usize,
    sampler_binds: usize,
    state_changes: usize,
}

#[cfg(test)]
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub(in crate::render::vulkanic) struct StateCacheSnapshot {
    pub(in crate::render::vulkanic) program_binds: usize,
    pub(in crate::render::vulkanic) vao_binds: usize,
    pub(in crate::render::vulkanic) framebuffer_binds: usize,
    pub(in crate::render::vulkanic) texture_binds: usize,
    pub(in crate::render::vulkanic) sampler_binds: usize,
    pub(in crate::render::vulkanic) state_changes: usize,
}

fn compare_op(compare: CompareOp) -> u32 {
    match compare {
        CompareOp::Always => glow::ALWAYS,
        CompareOp::Less => glow::LESS,
        CompareOp::LessOrEqual => glow::LEQUAL,
        CompareOp::Equal => glow::EQUAL,
    }
}

fn flip_rows_in_place(bytes: &mut [u8], row_bytes: usize, rows: usize) {
    for y in 0..rows / 2 {
        let top = y * row_bytes;
        let bottom = (rows - 1 - y) * row_bytes;
        for x in 0..row_bytes {
            bytes.swap(top + x, bottom + x);
        }
    }
}

fn gl_y_for_top_left_region(
    texture: &super::resources::TextureObject,
    region: &BufferImageCopyRegion,
) -> GalResult<i32> {
    let texture_height = i64::from(texture.extent.height);
    let origin_y = i64::from(region.texture_origin.y);
    let copy_height = i64::from(region.extent.height);
    let gl_y = texture_height
        .checked_sub(origin_y)
        .and_then(|value| value.checked_sub(copy_height))
        .ok_or_else(|| GalError::backend("top-left texture region is outside GL image bounds"))?;
    i32::try_from(gl_y).map_err(|_| GalError::backend("translated GL y exceeds i32"))
}

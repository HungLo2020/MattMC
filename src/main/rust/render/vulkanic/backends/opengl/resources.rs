use std::collections::BTreeMap;
use std::rc::Rc;

use glow::HasContext;

use super::trace;
use crate::render::vulkanic::backends::{BackendCreateDesc, BackendToken};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::*;

pub(super) struct OpenGlObjects {
    gl: Rc<glow::Context>,
    objects: BTreeMap<Handle, OpenGlObject>,
    frame_target_framebuffers: BTreeMap<u64, Option<glow::Framebuffer>>,
    current_frame_target_framebuffer: Option<Option<glow::Framebuffer>>,
    next_token: u64,
}

impl OpenGlObjects {
    pub(super) fn new(gl: Rc<glow::Context>) -> Self {
        Self {
            gl,
            objects: BTreeMap::new(),
            frame_target_framebuffers: BTreeMap::new(),
            current_frame_target_framebuffer: None,
            next_token: 1,
        }
    }

    pub(super) fn set_frame_target_framebuffer(
        &mut self,
        frame_id: u64,
        framebuffer: Option<glow::Framebuffer>,
    ) {
        self.current_frame_target_framebuffer = Some(framebuffer);
        self.frame_target_framebuffers.insert(frame_id, framebuffer);
    }

    pub(super) fn create(
        &mut self,
        handle: Handle,
        desc: BackendCreateDesc<'_>,
    ) -> GalResult<BackendToken> {
        let _zone = trace::Zone::new("opengl.resources.create-native");
        let token = BackendToken(self.next_token);
        self.next_token += 1;
        let object = match desc {
            BackendCreateDesc::Buffer(desc) => {
                OpenGlObject::Buffer(self.create_buffer(desc, token)?)
            }
            BackendCreateDesc::Texture(desc) => {
                OpenGlObject::Texture(self.create_texture(desc, token)?)
            }
            BackendCreateDesc::TextureView(desc) => OpenGlObject::TextureView(TextureViewObject {
                token,
                texture: desc.texture,
                format: desc.format,
                base_mip: desc.base_mip,
                base_layer: desc.base_layer,
            }),
            BackendCreateDesc::Sampler(desc) => {
                OpenGlObject::Sampler(self.create_sampler(desc, token)?)
            }
            BackendCreateDesc::ShaderModule(desc) => {
                OpenGlObject::ShaderModule(self.create_shader_module(desc, token)?)
            }
            BackendCreateDesc::ResourceLayout(desc) => {
                OpenGlObject::ResourceLayout(ResourceLayoutObject {
                    token,
                    bindings: desc.bindings.clone(),
                })
            }
            BackendCreateDesc::ResourceSet(desc) => OpenGlObject::ResourceSet(ResourceSetObject {
                token,
                layout: desc.layout,
                bindings: desc.bindings.clone(),
            }),
            BackendCreateDesc::PipelineLayout(desc) => {
                OpenGlObject::PipelineLayout(PipelineLayoutObject {
                    token,
                    resource_layouts: desc.resource_layouts.clone(),
                })
            }
            BackendCreateDesc::GraphicsPipeline(desc) => {
                OpenGlObject::GraphicsPipeline(self.create_graphics_pipeline(desc, token)?)
            }
            BackendCreateDesc::ComputePipeline(_) => {
                return Err(GalError::backend(
                    "OpenGL backend does not support compute pipelines in the isolated path",
                ))
            }
            BackendCreateDesc::RenderTarget(desc) => {
                OpenGlObject::RenderTarget(self.create_render_target(desc, token)?)
            }
            BackendCreateDesc::FrameTarget(desc) => OpenGlObject::FrameTarget(FrameTargetObject {
                token,
                frame_id: desc.frame_id,
                framebuffer: self
                    .frame_target_framebuffers
                    .remove(&desc.frame_id)
                    .or(self.current_frame_target_framebuffer)
                    .unwrap_or(None),
                extent: desc.extent,
                color_format: desc.color_format,
            }),
            BackendCreateDesc::RenderPass(desc) => OpenGlObject::RenderPass(RenderPassObject {
                token,
                target: desc.target,
                color_formats: desc.color_formats.clone(),
                depth_format: desc.depth_format,
            }),
        };
        self.objects.insert(handle, object);
        Ok(token)
    }

    pub(super) fn destroy(
        &mut self,
        handle: Handle,
        kind: HandleKind,
        token: BackendToken,
    ) -> GalResult<()> {
        let _zone = trace::Zone::new("opengl.resources.destroy-native");
        let Some(object) = self.objects.remove(&handle) else {
            return Err(GalError::backend("OpenGL destroy for unknown handle"));
        };
        if object.kind() != kind || object.token() != token {
            self.objects.insert(handle, object);
            return Err(GalError::backend("OpenGL destroy kind or token mismatch"));
        }
        self.destroy_object(object);
        Ok(())
    }

    pub(super) fn buffer(&self, handle: Handle) -> GalResult<&BufferObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::Buffer(object)) => Ok(object),
            _ => Err(expected("buffer", handle)),
        }
    }

    pub(super) fn buffer_mut(&mut self, handle: Handle) -> GalResult<&mut BufferObject> {
        match self.objects.get_mut(&handle) {
            Some(OpenGlObject::Buffer(object)) => Ok(object),
            _ => Err(expected("buffer", handle)),
        }
    }

    pub(super) fn texture(&self, handle: Handle) -> GalResult<&TextureObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::Texture(object)) => Ok(object),
            _ => Err(expected("texture", handle)),
        }
    }

    pub(super) fn texture_view(&self, handle: Handle) -> GalResult<&TextureViewObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::TextureView(object)) => Ok(object),
            _ => Err(expected("texture view", handle)),
        }
    }

    pub(super) fn sampler(&self, handle: Handle) -> GalResult<&SamplerObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::Sampler(object)) => Ok(object),
            _ => Err(expected("sampler", handle)),
        }
    }

    pub(super) fn resource_set(&self, handle: Handle) -> GalResult<&ResourceSetObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::ResourceSet(object)) => Ok(object),
            _ => Err(expected("resource set", handle)),
        }
    }

    pub(super) fn resource_layout(&self, handle: Handle) -> GalResult<&ResourceLayoutObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::ResourceLayout(object)) => Ok(object),
            _ => Err(expected("resource layout", handle)),
        }
    }

    pub(super) fn pipeline_layout(&self, handle: Handle) -> GalResult<&PipelineLayoutObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::PipelineLayout(object)) => Ok(object),
            _ => Err(expected("pipeline layout", handle)),
        }
    }

    pub(super) fn graphics_pipeline(&self, handle: Handle) -> GalResult<&GraphicsPipelineObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::GraphicsPipeline(object)) => Ok(object),
            _ => Err(expected("graphics pipeline", handle)),
        }
    }

    pub(super) fn pass_target(&self, handle: Handle) -> GalResult<PassTargetObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::RenderTarget(object)) => Ok(PassTargetObject {
                framebuffer: Some(object.framebuffer),
                extent: object.extent,
            }),
            Some(OpenGlObject::FrameTarget(object)) => Ok(PassTargetObject {
                // ABI v2 intentionally reuses the same GAL frame-target handle
                // across steady-state borrowed-context frames. Refresh the
                // native OpenGL draw framebuffer on acquire so screen
                // transitions cannot leave the persistent GAL handle pointing
                // at a stale Java framebuffer.
                framebuffer: self
                    .current_frame_target_framebuffer
                    .unwrap_or(object.framebuffer),
                extent: object.extent,
            }),
            _ => Err(expected("render target or frame target", handle)),
        }
    }

    pub(super) fn render_pass(&self, handle: Handle) -> GalResult<&RenderPassObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::RenderPass(object)) => Ok(object),
            _ => Err(expected("render pass", handle)),
        }
    }

    pub(super) fn destroy_all(&mut self) {
        let _zone = trace::Zone::new("opengl.resources.destroy-all");
        let objects = std::mem::take(&mut self.objects);
        for (_, object) in objects.into_iter().rev() {
            self.destroy_object(object);
        }
    }

    fn create_buffer(&self, desc: &BufferDesc, token: BackendToken) -> GalResult<BufferObject> {
        let size = usize::try_from(desc.size)
            .map_err(|_| GalError::backend("OpenGL buffer size exceeds addressable memory"))?;
        let buffer = unsafe { self.gl.create_buffer() }.map_err(|error| {
            GalError::backend(format!("failed to create OpenGL buffer: {error}"))
        })?;
        unsafe {
            self.gl.bind_buffer(glow::COPY_WRITE_BUFFER, Some(buffer));
            self.gl.buffer_data_size(
                glow::COPY_WRITE_BUFFER,
                i32::try_from(size)
                    .map_err(|_| GalError::backend("OpenGL buffer size exceeds i32"))?,
                glow::DYNAMIC_DRAW,
            );
            self.gl.bind_buffer(glow::COPY_WRITE_BUFFER, None);
        }
        Ok(BufferObject {
            token,
            buffer,
            size: desc.size,
            shadow: vec![0; size],
        })
    }

    fn create_texture(&self, desc: &TextureDesc, token: BackendToken) -> GalResult<TextureObject> {
        if desc.dimension != TextureDimension::D2 || desc.mip_levels != 1 || desc.array_layers != 1
        {
            return Err(GalError::backend(
                "OpenGL backend currently supports single-layer D2 textures in the isolated path",
            ));
        }
        let format = texture_format(desc.format)?;
        let texture = unsafe { self.gl.create_texture() }.map_err(|error| {
            GalError::backend(format!("failed to create OpenGL texture: {error}"))
        })?;
        unsafe {
            self.gl.bind_texture(glow::TEXTURE_2D, Some(texture));
            self.gl.tex_parameter_i32(
                glow::TEXTURE_2D,
                glow::TEXTURE_MIN_FILTER,
                glow::LINEAR as i32,
            );
            self.gl.tex_parameter_i32(
                glow::TEXTURE_2D,
                glow::TEXTURE_MAG_FILTER,
                glow::LINEAR as i32,
            );
            self.gl.tex_image_2d(
                glow::TEXTURE_2D,
                0,
                format.internal,
                i32::try_from(desc.extent.width)
                    .map_err(|_| GalError::backend("texture width exceeds i32"))?,
                i32::try_from(desc.extent.height)
                    .map_err(|_| GalError::backend("texture height exceeds i32"))?,
                0,
                format.external,
                format.ty,
                glow::PixelUnpackData::Slice(None),
            );
            self.gl.bind_texture(glow::TEXTURE_2D, None);
        }
        Ok(TextureObject {
            token,
            texture,
            format: desc.format,
            extent: desc.extent,
        })
    }

    fn create_sampler(&self, desc: &SamplerDesc, token: BackendToken) -> GalResult<SamplerObject> {
        let sampler = unsafe { self.gl.create_sampler() }.map_err(|error| {
            GalError::backend(format!("failed to create OpenGL sampler: {error}"))
        })?;
        unsafe {
            self.gl
                .sampler_parameter_i32(sampler, glow::TEXTURE_MIN_FILTER, min_filter(desc));
            self.gl.sampler_parameter_i32(
                sampler,
                glow::TEXTURE_MAG_FILTER,
                filter(desc.mag_filter),
            );
            self.gl
                .sampler_parameter_i32(sampler, glow::TEXTURE_WRAP_S, address(desc.address_u));
            self.gl
                .sampler_parameter_i32(sampler, glow::TEXTURE_WRAP_T, address(desc.address_v));
            self.gl
                .sampler_parameter_i32(sampler, glow::TEXTURE_WRAP_R, address(desc.address_w));
        }
        Ok(SamplerObject { token, sampler })
    }

    fn create_shader_module(
        &self,
        desc: &ShaderModuleDesc,
        token: BackendToken,
    ) -> GalResult<ShaderModuleObject> {
        if desc.code_format != ShaderCodeFormat::Glsl {
            return Err(GalError::backend(
                "OpenGL backend requires GLSL shader source",
            ));
        }
        let ty = shader_stage(desc.stage)?;
        let source = std::str::from_utf8(&desc.code)
            .map_err(|_| GalError::backend("OpenGL GLSL shader source is not UTF-8"))?;
        let source = opengl_shader_source(source);
        let shader = unsafe { self.gl.create_shader(ty) }.map_err(|error| {
            GalError::backend(format!(
                "failed to create OpenGL shader '{}': {error}",
                desc.label
            ))
        })?;
        unsafe {
            self.gl.shader_source(shader, &source);
            self.gl.compile_shader(shader);
            if !self.gl.get_shader_compile_status(shader) {
                let log = self.gl.get_shader_info_log(shader);
                self.gl.delete_shader(shader);
                return Err(GalError::backend(format!(
                    "OpenGL shader '{}' failed to compile: {log}",
                    desc.label
                )));
            }
        }
        Ok(ShaderModuleObject {
            token,
            shader,
            stage: desc.stage,
        })
    }

    fn create_graphics_pipeline(
        &self,
        desc: &GraphicsPipelineDesc,
        token: BackendToken,
    ) -> GalResult<GraphicsPipelineObject> {
        let vertex = self.shader(desc.vertex_shader)?;
        let fragment = self.shader(desc.fragment_shader)?;
        let program = unsafe { self.gl.create_program() }.map_err(|error| {
            GalError::backend(format!(
                "failed to create OpenGL program '{}': {error}",
                desc.label
            ))
        })?;
        unsafe {
            self.gl.attach_shader(program, vertex.shader);
            self.gl.attach_shader(program, fragment.shader);
            self.gl.link_program(program);
            self.gl.detach_shader(program, vertex.shader);
            self.gl.detach_shader(program, fragment.shader);
            if !self.gl.get_program_link_status(program) {
                let log = self.gl.get_program_info_log(program);
                self.gl.delete_program(program);
                return Err(GalError::backend(format!(
                    "OpenGL program '{}' failed to link: {log}",
                    desc.label
                )));
            }
            self.bind_program_interfaces(program, desc.layout)?;
        }
        let vao = unsafe { self.gl.create_vertex_array() }.map_err(|error| {
            unsafe { self.gl.delete_program(program) };
            GalError::backend(format!(
                "failed to create OpenGL VAO '{}': {error}",
                desc.label
            ))
        })?;
        Ok(GraphicsPipelineObject {
            token,
            program,
            vao,
            layout: desc.layout,
            topology: desc.topology,
            cull_mode: desc.cull_mode,
            blend: desc.blend,
            depth_compare: desc.depth_compare,
            depth_write: desc.depth_write,
        })
    }

    fn create_render_target(
        &self,
        desc: &RenderTargetDesc,
        token: BackendToken,
    ) -> GalResult<RenderTargetObject> {
        let framebuffer = unsafe { self.gl.create_framebuffer() }.map_err(|error| {
            GalError::backend(format!(
                "failed to create OpenGL FBO '{}': {error}",
                desc.label
            ))
        })?;
        unsafe {
            self.gl
                .bind_framebuffer(glow::FRAMEBUFFER, Some(framebuffer));
            for (index, view) in desc.color_views.iter().enumerate() {
                let view_object = self.texture_view(*view)?;
                let texture = self.texture(view_object.texture)?;
                self.gl.framebuffer_texture_2d(
                    glow::FRAMEBUFFER,
                    glow::COLOR_ATTACHMENT0
                        + u32::try_from(index)
                            .map_err(|_| GalError::backend("too many color attachments"))?,
                    glow::TEXTURE_2D,
                    Some(texture.texture),
                    i32::try_from(view_object.base_mip)
                        .map_err(|_| GalError::backend("mip level exceeds i32"))?,
                );
            }
            if let Some(view) = desc.depth_stencil_view {
                let view_object = self.texture_view(view)?;
                let texture = self.texture(view_object.texture)?;
                self.gl.framebuffer_texture_2d(
                    glow::FRAMEBUFFER,
                    glow::DEPTH_ATTACHMENT,
                    glow::TEXTURE_2D,
                    Some(texture.texture),
                    i32::try_from(view_object.base_mip)
                        .map_err(|_| GalError::backend("mip level exceeds i32"))?,
                );
            }
            let draw_buffers = desc
                .color_views
                .iter()
                .enumerate()
                .map(|(index, _)| glow::COLOR_ATTACHMENT0 + index as u32)
                .collect::<Vec<_>>();
            if !draw_buffers.is_empty() {
                self.gl.draw_buffers(&draw_buffers);
            } else {
                self.gl.draw_buffer(glow::NONE);
                self.gl.read_buffer(glow::NONE);
            }
            let status = self.gl.check_framebuffer_status(glow::FRAMEBUFFER);
            self.gl.bind_framebuffer(glow::FRAMEBUFFER, None);
            if status != glow::FRAMEBUFFER_COMPLETE {
                self.gl.delete_framebuffer(framebuffer);
                return Err(GalError::backend(format!(
                    "OpenGL FBO '{}' is incomplete: 0x{status:04x}",
                    desc.label
                )));
            }
        }
        Ok(RenderTargetObject {
            token,
            framebuffer,
            color_views: desc.color_views.clone(),
            depth_stencil_view: desc.depth_stencil_view,
            extent: desc.extent,
        })
    }

    fn shader(&self, handle: Handle) -> GalResult<&ShaderModuleObject> {
        match self.objects.get(&handle) {
            Some(OpenGlObject::ShaderModule(shader)) => Ok(shader),
            _ => Err(expected("shader", handle)),
        }
    }

    fn bind_program_interfaces(&self, program: glow::Program, layout: Handle) -> GalResult<()> {
        let pipeline_layout = self.pipeline_layout(layout)?;
        for resource_layout in &pipeline_layout.resource_layouts {
            let resource_layout = self.resource_layout(*resource_layout)?;
            for binding in &resource_layout.bindings {
                unsafe {
                    match binding.kind {
                        ResourceBindingKind::UniformBuffer => {
                            let mut bound = false;
                            for name in uniform_block_names(binding.binding) {
                                if let Some(index) = self.gl.get_uniform_block_index(program, &name)
                                {
                                    self.gl
                                        .uniform_block_binding(program, index, binding.binding);
                                    bound = true;
                                }
                            }
                            if !bound {
                                return Err(GalError::backend(format!(
                                    "OpenGL program is missing uniform block for binding {}",
                                    binding.binding
                                )));
                            }
                        }
                        ResourceBindingKind::StorageBuffer => {
                            let mut bound = false;
                            for name in storage_block_names(binding.binding) {
                                if let Some(index) =
                                    self.gl.get_shader_storage_block_index(program, &name)
                                {
                                    self.gl.shader_storage_block_binding(
                                        program,
                                        index,
                                        binding.binding,
                                    );
                                    bound = true;
                                }
                            }
                            if !bound {
                                return Err(GalError::backend(format!(
                                    "OpenGL program is missing storage block for binding {}",
                                    binding.binding
                                )));
                            }
                        }
                        ResourceBindingKind::SampledTexture => {
                            for name in sampler_uniform_names(binding.binding) {
                                if let Some(location) = self.gl.get_uniform_location(program, &name)
                                {
                                    self.gl.use_program(Some(program));
                                    self.gl.uniform_1_i32(
                                        Some(&location),
                                        i32::try_from(binding.binding).map_err(|_| {
                                            GalError::backend("OpenGL sampler binding exceeds i32")
                                        })?,
                                    );
                                }
                            }
                        }
                        ResourceBindingKind::Sampler | ResourceBindingKind::StorageTexture => {}
                    }
                }
            }
        }
        unsafe {
            self.gl.use_program(None);
        }
        Ok(())
    }

    fn destroy_object(&self, object: OpenGlObject) {
        unsafe {
            match object {
                OpenGlObject::Buffer(object) => self.gl.delete_buffer(object.buffer),
                OpenGlObject::Texture(object) => self.gl.delete_texture(object.texture),
                OpenGlObject::TextureView(_) => {}
                OpenGlObject::Sampler(object) => self.gl.delete_sampler(object.sampler),
                OpenGlObject::ShaderModule(object) => self.gl.delete_shader(object.shader),
                OpenGlObject::ResourceLayout(_) => {}
                OpenGlObject::ResourceSet(_) => {}
                OpenGlObject::PipelineLayout(_) => {}
                OpenGlObject::GraphicsPipeline(object) => {
                    self.gl.delete_vertex_array(object.vao);
                    self.gl.delete_program(object.program);
                }
                OpenGlObject::RenderTarget(object) => {
                    self.gl.delete_framebuffer(object.framebuffer)
                }
                OpenGlObject::FrameTarget(_) => {}
                OpenGlObject::RenderPass(_) => {}
            }
        }
    }
}

impl Drop for OpenGlObjects {
    fn drop(&mut self) {
        self.destroy_all();
    }
}

enum OpenGlObject {
    Buffer(BufferObject),
    Texture(TextureObject),
    TextureView(TextureViewObject),
    Sampler(SamplerObject),
    ShaderModule(ShaderModuleObject),
    ResourceLayout(ResourceLayoutObject),
    ResourceSet(ResourceSetObject),
    PipelineLayout(PipelineLayoutObject),
    GraphicsPipeline(GraphicsPipelineObject),
    RenderTarget(RenderTargetObject),
    FrameTarget(FrameTargetObject),
    RenderPass(RenderPassObject),
}

impl OpenGlObject {
    fn token(&self) -> BackendToken {
        match self {
            Self::Buffer(object) => object.token,
            Self::Texture(object) => object.token,
            Self::TextureView(object) => object.token,
            Self::Sampler(object) => object.token,
            Self::ShaderModule(object) => object.token,
            Self::ResourceLayout(object) => object.token,
            Self::ResourceSet(object) => object.token,
            Self::PipelineLayout(object) => object.token,
            Self::GraphicsPipeline(object) => object.token,
            Self::RenderTarget(object) => object.token,
            Self::FrameTarget(object) => object.token,
            Self::RenderPass(object) => object.token,
        }
    }

    fn kind(&self) -> HandleKind {
        match self {
            Self::Buffer(_) => HandleKind::Buffer,
            Self::Texture(_) => HandleKind::Texture,
            Self::TextureView(_) => HandleKind::TextureView,
            Self::Sampler(_) => HandleKind::Sampler,
            Self::ShaderModule(_) => HandleKind::ShaderModule,
            Self::ResourceLayout(_) => HandleKind::ResourceLayout,
            Self::ResourceSet(_) => HandleKind::ResourceSet,
            Self::PipelineLayout(_) => HandleKind::PipelineLayout,
            Self::GraphicsPipeline(_) => HandleKind::GraphicsPipeline,
            Self::RenderTarget(_) => HandleKind::RenderTarget,
            Self::FrameTarget(_) => HandleKind::FrameTarget,
            Self::RenderPass(_) => HandleKind::RenderPass,
        }
    }
}

pub(super) struct BufferObject {
    pub(super) token: BackendToken,
    pub(super) buffer: glow::Buffer,
    pub(super) size: u64,
    pub(super) shadow: Vec<u8>,
}

#[allow(dead_code)]
pub(super) struct TextureObject {
    pub(super) token: BackendToken,
    pub(super) texture: glow::Texture,
    pub(super) format: TextureFormat,
    pub(super) extent: Extent3d,
}

#[allow(dead_code)]
pub(super) struct TextureViewObject {
    pub(super) token: BackendToken,
    pub(super) texture: Handle,
    pub(super) format: TextureFormat,
    pub(super) base_mip: u32,
    pub(super) base_layer: u32,
}

pub(super) struct SamplerObject {
    pub(super) token: BackendToken,
    pub(super) sampler: glow::Sampler,
}

#[allow(dead_code)]
pub(super) struct ShaderModuleObject {
    pub(super) token: BackendToken,
    pub(super) shader: glow::Shader,
    pub(super) stage: ShaderStage,
}

#[allow(dead_code)]
pub(super) struct ResourceLayoutObject {
    pub(super) token: BackendToken,
    pub(super) bindings: Vec<ResourceBindingDesc>,
}

pub(super) struct ResourceSetObject {
    pub(super) token: BackendToken,
    pub(super) layout: Handle,
    pub(super) bindings: Vec<ResourceBinding>,
}

pub(super) struct PipelineLayoutObject {
    pub(super) token: BackendToken,
    pub(super) resource_layouts: Vec<Handle>,
}

pub(super) struct GraphicsPipelineObject {
    pub(super) token: BackendToken,
    pub(super) program: glow::Program,
    pub(super) vao: glow::VertexArray,
    pub(super) layout: Handle,
    pub(super) topology: PrimitiveTopology,
    pub(super) cull_mode: CullMode,
    pub(super) blend: BlendMode,
    pub(super) depth_compare: Option<CompareOp>,
    pub(super) depth_write: bool,
}

#[allow(dead_code)]
pub(super) struct RenderTargetObject {
    pub(super) token: BackendToken,
    pub(super) framebuffer: glow::Framebuffer,
    pub(super) color_views: Vec<Handle>,
    pub(super) depth_stencil_view: Option<Handle>,
    pub(super) extent: Extent3d,
}

#[allow(dead_code)]
pub(super) struct FrameTargetObject {
    pub(super) token: BackendToken,
    pub(super) frame_id: u64,
    pub(super) framebuffer: Option<glow::Framebuffer>,
    pub(super) extent: Extent3d,
    pub(super) color_format: TextureFormat,
}

#[derive(Clone, Copy)]
pub(super) struct PassTargetObject {
    pub(super) framebuffer: Option<glow::Framebuffer>,
    pub(super) extent: Extent3d,
}

#[cfg(test)]
mod tests {
    use super::super::OpenGlBackend;
    use super::*;
    use std::num::NonZeroU32;

    fn fake_framebuffer(id: u32) -> glow::Framebuffer {
        glow::NativeFramebuffer(NonZeroU32::new(id).unwrap())
    }

    fn frame_target_desc(frame_id: u64) -> FrameTargetDesc {
        FrameTargetDesc {
            label: format!("test-frame-target-{frame_id}"),
            frame_id,
            extent: Extent3d {
                width: 64,
                height: 48,
                depth: 1,
            },
            color_format: TextureFormat::Rgba8Unorm,
        }
    }

    #[test]
    fn borrowed_frame_targets_follow_latest_acquired_framebuffer() {
        let mut backend = match OpenGlBackend::new("MattMC OpenGL frame-target refresh test") {
            Ok(backend) => backend,
            Err(error) => {
                assert!(
                    error.to_string().contains("OpenGL") || error.to_string().contains("EGL"),
                    "unexpected OpenGL bootstrap failure: {error}"
                );
                return;
            }
        };
        let objects = &mut backend.objects;
        let handle = Handle::new(HandleKind::FrameTarget, 1, 1).unwrap();
        let first_framebuffer = fake_framebuffer(101);
        let second_framebuffer = fake_framebuffer(202);

        objects.set_frame_target_framebuffer(1, Some(first_framebuffer));
        objects
            .create(
                handle,
                BackendCreateDesc::FrameTarget(&frame_target_desc(1)),
            )
            .unwrap();
        assert_eq!(
            objects.pass_target(handle).unwrap().framebuffer,
            Some(first_framebuffer)
        );

        objects.set_frame_target_framebuffer(2, Some(second_framebuffer));
        assert_eq!(
            objects.pass_target(handle).unwrap().framebuffer,
            Some(second_framebuffer)
        );

        objects.set_frame_target_framebuffer(3, None);
        assert_eq!(objects.pass_target(handle).unwrap().framebuffer, None);
    }

    #[test]
    fn opengl_shader_source_maps_vulkan_style_vertex_builtins() {
        let source =
            "layout(set = 0, binding = 0, std140) uniform U { mat4 m; }; int v = gl_VertexIndex; int i = gl_InstanceIndex;";
        let normalized = opengl_shader_source(source);
        assert!(normalized.contains("layout(binding = 0, std140)"));
        assert!(!normalized.contains("set = 0"));
        assert!(normalized.contains("gl_VertexID"));
        assert!(normalized.contains("gl_InstanceID"));
        assert!(!normalized.contains("gl_VertexIndex"));
        assert!(!normalized.contains("gl_InstanceIndex"));
    }

    #[test]
    fn opengl_shader_source_maps_separate_texture_sampler_pair() {
        let source = "\
layout(set = 0, binding = 1) uniform texture2D Tex0;
layout(set = 0, binding = 2) uniform sampler Samp0;
void main() { vec4 color = texture(sampler2D(Tex0, Samp0), vec2(0.5)); }";
        let normalized = opengl_shader_source(source);
        assert!(normalized.contains("layout(binding = 1) uniform sampler2D Tex0;"));
        assert!(!normalized.contains("uniform sampler Samp0"));
        assert!(!normalized.contains("sampler2D(Tex0, Samp0)"));
        assert!(normalized.contains("texture(Tex0, vec2(0.5))"));
    }

    #[test]
    fn opengl_program_interface_aliases_include_world_mesh_blocks() {
        assert!(storage_block_names(0)
            .iter()
            .any(|name| name == "WorldMeshVertices"));
        assert!(storage_block_names(1)
            .iter()
            .any(|name| name == "WorldMeshInstances"));
    }
}

#[allow(dead_code)]
pub(super) struct RenderPassObject {
    pub(super) token: BackendToken,
    pub(super) target: Handle,
    pub(super) color_formats: Vec<TextureFormat>,
    pub(super) depth_format: Option<TextureFormat>,
}

pub(super) struct GlTextureFormat {
    pub(super) internal: i32,
    pub(super) external: u32,
    pub(super) ty: u32,
    pub(super) bytes_per_pixel: u32,
}

pub(super) fn texture_format(format: TextureFormat) -> GalResult<GlTextureFormat> {
    match format {
        TextureFormat::Rgba8Unorm => Ok(GlTextureFormat {
            internal: glow::RGBA8 as i32,
            external: glow::RGBA,
            ty: glow::UNSIGNED_BYTE,
            bytes_per_pixel: 4,
        }),
        TextureFormat::Depth32Float => Ok(GlTextureFormat {
            internal: glow::DEPTH_COMPONENT32F as i32,
            external: glow::DEPTH_COMPONENT,
            ty: glow::FLOAT,
            bytes_per_pixel: 4,
        }),
        TextureFormat::Bgra8Unorm | TextureFormat::Rgba16Float | TextureFormat::Depth24Stencil8 => {
            Err(GalError::backend(format!(
                "OpenGL texture format {format:?} is not supported in the isolated path"
            )))
        }
    }
}

pub(super) fn filter(filter: SamplerFilter) -> i32 {
    match filter {
        SamplerFilter::Nearest => glow::NEAREST as i32,
        SamplerFilter::Linear => glow::LINEAR as i32,
    }
}

fn opengl_shader_source(source: &str) -> String {
    source
        .replace("layout(set = 0, binding =", "layout(binding =")
        .replace("layout(set=0,binding=", "layout(binding=")
        .replace("uniform texture2D Tex0;", "uniform sampler2D Tex0;")
        .replace("uniform texture2D tex0;", "uniform sampler2D tex0;")
        .replace("layout(binding = 2) uniform sampler Samp0;\n", "")
        .replace("layout(binding=2) uniform sampler Samp0;\n", "")
        .replace("layout(binding = 1) uniform sampler samp0;\n", "")
        .replace("layout(binding=1) uniform sampler samp0;\n", "")
        .replace("sampler2D(Tex0, Samp0)", "Tex0")
        .replace("sampler2D(tex0, samp0)", "tex0")
        .replace("gl_VertexIndex", "gl_VertexID")
        .replace("gl_InstanceIndex", "gl_InstanceID")
}

pub(super) fn min_filter(desc: &SamplerDesc) -> i32 {
    filter(desc.min_filter)
}

pub(super) fn address(mode: SamplerAddressMode) -> i32 {
    match mode {
        SamplerAddressMode::ClampToEdge => glow::CLAMP_TO_EDGE as i32,
        SamplerAddressMode::Repeat => glow::REPEAT as i32,
        SamplerAddressMode::MirroredRepeat => glow::MIRRORED_REPEAT as i32,
    }
}

pub(super) fn topology(topology: PrimitiveTopology) -> u32 {
    match topology {
        PrimitiveTopology::Points => glow::POINTS,
        PrimitiveTopology::Lines => glow::LINES,
        PrimitiveTopology::Triangles => glow::TRIANGLES,
    }
}

fn uniform_block_names(binding: u32) -> Vec<String> {
    match binding {
        0 => vec![
            "GuiRect".to_string(),
            "GuiSpriteBatch".to_string(),
            "WorldLineBatch".to_string(),
            "CrackQuadBatch".to_string(),
            "WorldBorderBatch".to_string(),
            "DynamicTransforms".to_string(),
            "Projection".to_string(),
        ],
        1 => vec!["WorldMeshInstance".to_string(), "Uniforms1".to_string()],
        _ => vec![format!("Uniforms{binding}")],
    }
}

fn storage_block_names(binding: u32) -> Vec<String> {
    match binding {
        0 => vec![
            "WorldMaterialBatch".to_string(),
            "WorldMeshVertices".to_string(),
            "Storage0".to_string(),
        ],
        1 => vec!["WorldMeshInstances".to_string(), "Storage1".to_string()],
        _ => vec![format!("Storage{binding}")],
    }
}

pub(super) fn sampler_uniform_names(binding: u32) -> Vec<String> {
    match binding {
        0 => vec![
            "Sampler0".to_string(),
            "tex0".to_string(),
            "Tex0".to_string(),
        ],
        1 => vec![
            "Sampler0".to_string(),
            "tex0".to_string(),
            "Tex0".to_string(),
            "Sampler1".to_string(),
            "tex1".to_string(),
        ],
        2 => vec![
            "Samp0".to_string(),
            "Sampler2".to_string(),
            "tex2".to_string(),
        ],
        _ => vec![format!("Sampler{binding}"), format!("tex{binding}")],
    }
}

pub(super) fn shader_stage(stage: ShaderStage) -> GalResult<u32> {
    match stage {
        ShaderStage::Vertex => Ok(glow::VERTEX_SHADER),
        ShaderStage::Fragment => Ok(glow::FRAGMENT_SHADER),
        ShaderStage::Compute
        | ShaderStage::Geometry
        | ShaderStage::TessControl
        | ShaderStage::TessEvaluation => Err(GalError::backend(format!(
            "OpenGL shader stage {stage:?} is not supported in the isolated path"
        ))),
    }
}

fn expected(kind: &str, handle: Handle) -> GalError {
    GalError::backend(format!(
        "expected OpenGL {kind} for handle 0x{:016x}",
        handle.raw()
    ))
}

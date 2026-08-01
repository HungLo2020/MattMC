use std::collections::BTreeMap;
use std::ffi::{CStr, CString};
use std::io::Cursor;
use std::sync::Arc;

use ash::vk;

use super::device::VulkanContext;
use super::shaderc_spirv_compiler::compile_glsl_for_backend;
use super::trace;
use crate::render::vulkanic::backends::{BackendCreateDesc, BackendToken};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::resources::*;

pub(super) struct VulkanObjects {
    context: Arc<VulkanContext>,
    objects: BTreeMap<Handle, VulkanObject>,
    next_token: u64,
}

impl VulkanObjects {
    pub(super) fn new(context: Arc<VulkanContext>) -> Self {
        Self {
            context,
            objects: BTreeMap::new(),
            next_token: 1,
        }
    }

    pub(super) fn create(
        &mut self,
        handle: Handle,
        desc: BackendCreateDesc<'_>,
    ) -> GalResult<BackendToken> {
        let _zone = trace::Zone::new("vulkan.resources.create-native");
        let token = BackendToken(self.next_token);
        self.next_token += 1;
        let object = match desc {
            BackendCreateDesc::Buffer(desc) => {
                VulkanObject::Buffer(self.create_buffer(handle, desc, token)?)
            }
            BackendCreateDesc::Texture(desc) => {
                VulkanObject::Texture(self.create_texture(handle, desc, token)?)
            }
            BackendCreateDesc::TextureView(desc) => {
                VulkanObject::TextureView(self.create_texture_view(handle, desc, token)?)
            }
            BackendCreateDesc::Sampler(desc) => {
                VulkanObject::Sampler(self.create_sampler(handle, desc, token)?)
            }
            BackendCreateDesc::ShaderModule(desc) => {
                VulkanObject::ShaderModule(self.create_shader_module(handle, desc, token)?)
            }
            BackendCreateDesc::ResourceLayout(desc) => {
                VulkanObject::ResourceLayout(self.create_resource_layout(handle, desc, token)?)
            }
            BackendCreateDesc::ResourceSet(desc) => {
                VulkanObject::ResourceSet(self.create_resource_set(handle, desc, token)?)
            }
            BackendCreateDesc::PipelineLayout(desc) => {
                VulkanObject::PipelineLayout(self.create_pipeline_layout(handle, desc, token)?)
            }
            BackendCreateDesc::GraphicsPipeline(desc) => {
                VulkanObject::GraphicsPipeline(self.create_graphics_pipeline(handle, desc, token)?)
            }
            BackendCreateDesc::ComputePipeline(desc) => {
                VulkanObject::ComputePipeline(self.create_compute_pipeline(handle, desc, token)?)
            }
            BackendCreateDesc::RenderTarget(desc) => {
                VulkanObject::RenderTarget(RenderTargetObject {
                    token,
                    color_views: desc.color_views.clone(),
                    depth_stencil_view: desc.depth_stencil_view,
                    extent: desc.extent,
                })
            }
            BackendCreateDesc::FrameTarget(desc) => VulkanObject::FrameTarget(FrameTargetObject {
                token,
                frame_id: desc.frame_id,
                render_target: desc.render_target,
                extent: desc.extent,
                color_format: desc.color_format,
                image_index: u32::MAX,
                image: vk::Image::null(),
                image_view: vk::ImageView::null(),
                image_layout: vk::ImageLayout::UNDEFINED,
            }),
            BackendCreateDesc::RenderPass(desc) => VulkanObject::RenderPass(RenderPassObject {
                token,
                label: desc.label.clone(),
                target: desc.target,
                color_formats: desc.color_formats.clone(),
                depth_format: desc.depth_format,
            }),
        };
        self.objects.insert(handle, object);
        Ok(token)
    }

    pub(super) fn create_frame_target_from_swapchain(
        &mut self,
        handle: Handle,
        desc: &FrameTargetDesc,
        make_object: impl FnOnce(BackendToken) -> GalResult<FrameTargetObject>,
    ) -> GalResult<BackendToken> {
        let _zone = trace::Zone::new("vulkan.resources.create-frame-target");
        let token = BackendToken(self.next_token);
        self.next_token += 1;
        let object = make_object(token)?;
        if object.render_target != desc.render_target
            || object.extent != desc.extent
            || object.color_format != desc.color_format
        {
            return Err(GalError::backend(
                "swapchain frame target metadata does not match GAL frame target",
            ));
        }
        self.objects
            .insert(handle, VulkanObject::FrameTarget(object));
        Ok(token)
    }

    pub(super) fn refresh_frame_target_from_swapchain(
        &mut self,
        handle: Handle,
        make_object: impl FnOnce(
            BackendToken,
            crate::render::vulkanic::frame::FrameRenderTargetId,
            Extent3d,
            TextureFormat,
        ) -> GalResult<FrameTargetObject>,
    ) -> GalResult<()> {
        let Some(VulkanObject::FrameTarget(existing)) = self.objects.get(&handle) else {
            return Err(GalError::backend(
                "Vulkan frame target refresh for unknown frame target handle",
            ));
        };
        let token = existing.token;
        let render_target = existing.render_target;
        let extent = existing.extent;
        let color_format = existing.color_format;
        let object = make_object(token, render_target, extent, color_format)?;
        if object.token != token
            || object.render_target != render_target
            || object.extent != extent
            || object.color_format != color_format
        {
            return Err(GalError::backend(
                "refreshed swapchain frame target metadata does not match GAL frame target",
            ));
        }
        self.objects
            .insert(handle, VulkanObject::FrameTarget(object));
        Ok(())
    }

    pub(super) fn destroy(
        &mut self,
        handle: Handle,
        kind: HandleKind,
        token: BackendToken,
    ) -> GalResult<()> {
        let Some(object) = self.objects.remove(&handle) else {
            return Err(GalError::backend("Vulkan destroy for unknown handle"));
        };
        if object.kind() != kind || object.token() != token {
            self.objects.insert(handle, object);
            return Err(GalError::backend("Vulkan destroy kind or token mismatch"));
        }
        self.destroy_object(object);
        Ok(())
    }

    pub(super) fn buffer(&self, handle: Handle) -> GalResult<&BufferObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::Buffer(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan buffer for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn texture(&self, handle: Handle) -> GalResult<&TextureObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::Texture(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan texture for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn texture_view(&self, handle: Handle) -> GalResult<&TextureViewObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::TextureView(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan texture view for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn resource_set(&self, handle: Handle) -> GalResult<&ResourceSetObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::ResourceSet(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan resource set for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn pipeline_layout(&self, handle: Handle) -> GalResult<&PipelineLayoutObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::PipelineLayout(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan pipeline layout for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn graphics_pipeline(&self, handle: Handle) -> GalResult<&GraphicsPipelineObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::GraphicsPipeline(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan graphics pipeline for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn compute_pipeline(&self, handle: Handle) -> GalResult<&ComputePipelineObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::ComputePipeline(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan compute pipeline for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn render_target(&self, handle: Handle) -> GalResult<&RenderTargetObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::RenderTarget(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan render target for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn frame_target(&self, handle: Handle) -> GalResult<&FrameTargetObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::FrameTarget(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan frame target for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn render_pass(&self, handle: Handle) -> GalResult<&RenderPassObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::RenderPass(object)) => Ok(object),
            _ => Err(GalError::backend(format!(
                "expected Vulkan render pass for handle 0x{:016x}",
                handle.raw()
            ))),
        }
    }

    pub(super) fn destroy_all(&mut self) {
        let objects = std::mem::take(&mut self.objects);
        for (_, object) in objects.into_iter().rev() {
            self.destroy_object(object);
        }
    }

    fn create_buffer(
        &self,
        handle: Handle,
        desc: &BufferDesc,
        token: BackendToken,
    ) -> GalResult<BufferObject> {
        let usage = buffer_usage_flags(&desc.usages);
        let create_info = vk::BufferCreateInfo::default()
            .size(desc.size)
            .usage(usage)
            .sharing_mode(vk::SharingMode::EXCLUSIVE);
        let buffer =
            unsafe { self.context.device.create_buffer(&create_info, None) }.map_err(|error| {
                GalError::backend(format!(
                    "failed to create buffer '{}': {error:?}",
                    desc.label
                ))
            })?;
        let requirements = unsafe { self.context.device.get_buffer_memory_requirements(buffer) };
        let memory = match self
            .context
            .allocate_memory(requirements, memory_flags(desc.memory))
        {
            Ok(memory) => memory,
            Err(error) => {
                unsafe { self.context.device.destroy_buffer(buffer, None) };
                return Err(error);
            }
        };
        if let Err(error) = unsafe { self.context.device.bind_buffer_memory(buffer, memory, 0) } {
            unsafe {
                self.context.device.free_memory(memory, None);
                self.context.device.destroy_buffer(buffer, None);
            }
            return Err(GalError::backend(format!(
                "failed to bind buffer memory '{}': {error:?}",
                desc.label
            )));
        }
        self.context
            .set_object_name(buffer, &debug_name("buffer", handle, &desc.label));
        Ok(BufferObject {
            token,
            buffer,
            memory,
            size: desc.size,
            memory_domain: desc.memory,
        })
    }

    fn create_texture(
        &self,
        handle: Handle,
        desc: &TextureDesc,
        token: BackendToken,
    ) -> GalResult<TextureObject> {
        if desc.dimension != TextureDimension::D2 {
            return Err(GalError::backend(
                "Vulkan backend currently supports D2 textures in the isolated path",
            ));
        }
        let format = texture_format(desc.format);
        let usage = texture_usage_flags(&desc.usages);
        let create_info = vk::ImageCreateInfo::default()
            .image_type(vk::ImageType::TYPE_2D)
            .format(format)
            .extent(vk::Extent3D {
                width: desc.extent.width,
                height: desc.extent.height,
                depth: desc.extent.depth,
            })
            .mip_levels(desc.mip_levels)
            .array_layers(desc.array_layers)
            .samples(vk::SampleCountFlags::TYPE_1)
            .tiling(vk::ImageTiling::OPTIMAL)
            .usage(usage)
            .sharing_mode(vk::SharingMode::EXCLUSIVE)
            .initial_layout(vk::ImageLayout::UNDEFINED);
        let image =
            unsafe { self.context.device.create_image(&create_info, None) }.map_err(|error| {
                GalError::backend(format!(
                    "failed to create image '{}': {error:?}",
                    desc.label
                ))
            })?;
        let requirements = unsafe { self.context.device.get_image_memory_requirements(image) };
        let memory = match self
            .context
            .allocate_memory(requirements, vk::MemoryPropertyFlags::DEVICE_LOCAL)
        {
            Ok(memory) => memory,
            Err(error) => {
                unsafe { self.context.device.destroy_image(image, None) };
                return Err(error);
            }
        };
        if let Err(error) = unsafe { self.context.device.bind_image_memory(image, memory, 0) } {
            unsafe {
                self.context.device.free_memory(memory, None);
                self.context.device.destroy_image(image, None);
            }
            return Err(GalError::backend(format!(
                "failed to bind image memory '{}': {error:?}",
                desc.label
            )));
        }
        self.context
            .set_object_name(image, &debug_name("texture", handle, &desc.label));
        Ok(TextureObject {
            token,
            image,
            memory,
            format,
            extent: desc.extent,
            mip_levels: desc.mip_levels,
            array_layers: desc.array_layers,
            aspect: aspect_for_format(desc.format),
        })
    }

    fn create_texture_view(
        &self,
        handle: Handle,
        desc: &TextureViewDesc,
        token: BackendToken,
    ) -> GalResult<TextureViewObject> {
        let texture = self.texture(desc.texture)?;
        let create_info = vk::ImageViewCreateInfo::default()
            .image(texture.image)
            .view_type(vk::ImageViewType::TYPE_2D)
            .format(texture_format(desc.format))
            .subresource_range(vk::ImageSubresourceRange {
                aspect_mask: texture.aspect,
                base_mip_level: desc.base_mip,
                level_count: desc.mip_count,
                base_array_layer: desc.base_layer,
                layer_count: desc.layer_count,
            });
        let view = unsafe { self.context.device.create_image_view(&create_info, None) }.map_err(
            |error| {
                GalError::backend(format!(
                    "failed to create image view '{}': {error:?}",
                    desc.label
                ))
            },
        )?;
        self.context
            .set_object_name(view, &debug_name("texture-view", handle, &desc.label));
        Ok(TextureViewObject {
            token,
            view,
            texture: desc.texture,
            format: texture_format(desc.format),
            aspect: texture.aspect,
        })
    }

    fn create_sampler(
        &self,
        handle: Handle,
        desc: &SamplerDesc,
        token: BackendToken,
    ) -> GalResult<SamplerObject> {
        let create_info = vk::SamplerCreateInfo::default()
            .mag_filter(filter(desc.mag_filter))
            .min_filter(filter(desc.min_filter))
            .mipmap_mode(mipmap_filter(desc.mip_filter))
            .address_mode_u(address_mode(desc.address_u))
            .address_mode_v(address_mode(desc.address_v))
            .address_mode_w(address_mode(desc.address_w))
            .max_lod(vk::LOD_CLAMP_NONE);
        let sampler =
            unsafe { self.context.device.create_sampler(&create_info, None) }.map_err(|error| {
                GalError::backend(format!(
                    "failed to create sampler '{}': {error:?}",
                    desc.label
                ))
            })?;
        self.context
            .set_object_name(sampler, &debug_name("sampler", handle, &desc.label));
        Ok(SamplerObject { token, sampler })
    }

    fn create_shader_module(
        &self,
        handle: Handle,
        desc: &ShaderModuleDesc,
        token: BackendToken,
    ) -> GalResult<ShaderModuleObject> {
        let code = if desc.code_format == ShaderCodeFormat::Glsl {
            let source = std::str::from_utf8(&desc.code).map_err(|error| {
                GalError::backend(format!(
                    "GLSL shader '{}' is not UTF-8: {error}",
                    desc.label
                ))
            })?;
            compile_glsl_for_backend(
                shaderc_kind(desc.stage)?,
                source,
                &desc.label,
                &desc.entry_point,
            )
            .map_err(|error| {
                GalError::backend(format!(
                    "failed to compile GLSL shader '{}' for Vulkan backend: {error}",
                    desc.label
                ))
            })?
        } else {
            desc.code.clone()
        };
        if desc.code_format != ShaderCodeFormat::Spirv && desc.code_format != ShaderCodeFormat::Glsl
            || code.len() % 4 != 0
        {
            return Err(GalError::backend(
                "Vulkan backend requires 4-byte aligned SPIR-V shader code",
            ));
        }
        let words = ash::util::read_spv(&mut Cursor::new(&code)).map_err(|error| {
            GalError::backend(format!("failed to read SPIR-V '{}': {error}", desc.label))
        })?;
        let create_info = vk::ShaderModuleCreateInfo::default().code(&words);
        let module = unsafe { self.context.device.create_shader_module(&create_info, None) }
            .map_err(|error| {
                GalError::backend(format!(
                    "failed to create shader module '{}': {error:?}",
                    desc.label
                ))
            })?;
        self.context
            .set_object_name(module, &debug_name("shader", handle, &desc.label));
        let entry_point = CString::new(desc.entry_point.clone())
            .map_err(|_| GalError::backend("shader entry point contains NUL"))?;
        Ok(ShaderModuleObject {
            token,
            module,
            stage: shader_stage(desc.stage),
            entry_point,
        })
    }

    fn create_resource_layout(
        &self,
        handle: Handle,
        desc: &ResourceLayoutDesc,
        token: BackendToken,
    ) -> GalResult<ResourceLayoutObject> {
        let bindings = desc
            .bindings
            .iter()
            .map(|binding| {
                vk::DescriptorSetLayoutBinding::default()
                    .binding(binding.binding)
                    .descriptor_type(descriptor_type_for_binding(
                        binding.kind,
                        binding.dynamic_offset_count,
                    ))
                    .descriptor_count(binding.array_count)
                    .stage_flags(shader_stage_flags(binding.stages))
            })
            .collect::<Vec<_>>();
        let create_info = vk::DescriptorSetLayoutCreateInfo::default().bindings(&bindings);
        let layout = unsafe {
            self.context
                .device
                .create_descriptor_set_layout(&create_info, None)
        }
        .map_err(|error| {
            GalError::backend(format!(
                "failed to create descriptor set layout '{}': {error:?}",
                desc.label
            ))
        })?;
        self.context
            .set_object_name(layout, &debug_name("resource-layout", handle, &desc.label));
        Ok(ResourceLayoutObject {
            token,
            layout,
            bindings: desc.bindings.clone(),
        })
    }

    fn create_resource_set(
        &self,
        handle: Handle,
        desc: &ResourceSetDesc,
        token: BackendToken,
    ) -> GalResult<ResourceSetObject> {
        let layout_object = match self.objects.get(&desc.layout) {
            Some(VulkanObject::ResourceLayout(layout)) => layout,
            _ => return Err(GalError::backend("resource set references missing layout")),
        };
        let mut sizes: BTreeMap<vk::DescriptorType, u32> = BTreeMap::new();
        for binding in &layout_object.bindings {
            *sizes
                .entry(descriptor_type_for_binding(
                    binding.kind,
                    binding.dynamic_offset_count,
                ))
                .or_default() += binding.array_count;
        }
        let pool_sizes = sizes
            .into_iter()
            .map(|(ty, descriptor_count)| vk::DescriptorPoolSize {
                ty,
                descriptor_count,
            })
            .collect::<Vec<_>>();
        let pool_info = vk::DescriptorPoolCreateInfo::default()
            .max_sets(1)
            .pool_sizes(&pool_sizes);
        let pool = unsafe { self.context.device.create_descriptor_pool(&pool_info, None) }
            .map_err(|error| {
                GalError::backend(format!(
                    "failed to create descriptor pool '{}': {error:?}",
                    desc.label
                ))
            })?;
        self.context
            .set_object_name(pool, &debug_name("resource-set-pool", handle, &desc.label));
        let layouts = [layout_object.layout];
        let allocate_info = vk::DescriptorSetAllocateInfo::default()
            .descriptor_pool(pool)
            .set_layouts(&layouts);
        let set = match unsafe { self.context.device.allocate_descriptor_sets(&allocate_info) } {
            Ok(mut sets) => sets.remove(0),
            Err(error) => {
                unsafe { self.context.device.destroy_descriptor_pool(pool, None) };
                return Err(GalError::backend(format!(
                    "failed to allocate descriptor set '{}': {error:?}",
                    desc.label
                )));
            }
        };

        enum WritePlan {
            Buffer {
                binding: u32,
                array_index: u32,
                ty: vk::DescriptorType,
                info_index: usize,
            },
            Image {
                binding: u32,
                array_index: u32,
                ty: vk::DescriptorType,
                info_index: usize,
            },
        }
        let mut buffer_infos = Vec::new();
        let mut image_infos = Vec::new();
        let mut plans = Vec::new();
        for binding in &desc.bindings {
            match binding.kind {
                ResourceBindingKind::UniformBuffer | ResourceBindingKind::StorageBuffer => {
                    let buffer = self.buffer(binding.resource)?;
                    let info_index = buffer_infos.len();
                    buffer_infos.push(vk::DescriptorBufferInfo {
                        buffer: buffer.buffer,
                        offset: 0,
                        range: buffer.size,
                    });
                    plans.push(WritePlan::Buffer {
                        binding: binding.binding,
                        array_index: binding.array_index,
                        ty: descriptor_type_for_resource_binding(binding),
                        info_index,
                    });
                }
                ResourceBindingKind::SampledTexture | ResourceBindingKind::StorageTexture => {
                    let view = self.texture_view(binding.resource)?;
                    let info_index = image_infos.len();
                    image_infos.push(vk::DescriptorImageInfo {
                        sampler: vk::Sampler::null(),
                        image_view: view.view,
                        image_layout: if binding.kind == ResourceBindingKind::StorageTexture {
                            vk::ImageLayout::GENERAL
                        } else {
                            vk::ImageLayout::SHADER_READ_ONLY_OPTIMAL
                        },
                    });
                    plans.push(WritePlan::Image {
                        binding: binding.binding,
                        array_index: binding.array_index,
                        ty: descriptor_type_for_resource_binding(binding),
                        info_index,
                    });
                }
                ResourceBindingKind::Sampler => {
                    let sampler = match self.objects.get(&binding.resource) {
                        Some(VulkanObject::Sampler(sampler)) => sampler.sampler,
                        _ => {
                            return Err(GalError::backend(
                                "sampler binding references missing sampler",
                            ))
                        }
                    };
                    let info_index = image_infos.len();
                    image_infos.push(vk::DescriptorImageInfo {
                        sampler,
                        image_view: vk::ImageView::null(),
                        image_layout: vk::ImageLayout::UNDEFINED,
                    });
                    plans.push(WritePlan::Image {
                        binding: binding.binding,
                        array_index: binding.array_index,
                        ty: vk::DescriptorType::SAMPLER,
                        info_index,
                    });
                }
            }
        }
        let writes = plans
            .iter()
            .map(|plan| match *plan {
                WritePlan::Buffer {
                    binding,
                    array_index,
                    ty,
                    info_index,
                } => vk::WriteDescriptorSet::default()
                    .dst_set(set)
                    .dst_binding(binding)
                    .dst_array_element(array_index)
                    .descriptor_type(ty)
                    .buffer_info(&buffer_infos[info_index..info_index + 1]),
                WritePlan::Image {
                    binding,
                    array_index,
                    ty,
                    info_index,
                } => vk::WriteDescriptorSet::default()
                    .dst_set(set)
                    .dst_binding(binding)
                    .dst_array_element(array_index)
                    .descriptor_type(ty)
                    .image_info(&image_infos[info_index..info_index + 1]),
            })
            .collect::<Vec<_>>();
        unsafe { self.context.device.update_descriptor_sets(&writes, &[]) };
        let dynamic_offsets = desc
            .bindings
            .iter()
            .flat_map(|binding| binding.dynamic_offsets.iter().copied())
            .map(|offset| {
                u32::try_from(offset)
                    .map_err(|_| GalError::backend("dynamic descriptor offset exceeds u32"))
            })
            .collect::<GalResult<Vec<_>>>()?;
        Ok(ResourceSetObject {
            token,
            pool,
            set,
            dynamic_offsets,
        })
    }

    fn create_pipeline_layout(
        &self,
        handle: Handle,
        desc: &PipelineLayoutDesc,
        token: BackendToken,
    ) -> GalResult<PipelineLayoutObject> {
        let set_layouts = desc
            .resource_layouts
            .iter()
            .map(|handle| match self.objects.get(handle) {
                Some(VulkanObject::ResourceLayout(layout)) => Ok(layout.layout),
                _ => Err(GalError::backend(
                    "pipeline layout references missing resource layout",
                )),
            })
            .collect::<GalResult<Vec<_>>>()?;
        let create_info = vk::PipelineLayoutCreateInfo::default().set_layouts(&set_layouts);
        let layout = unsafe {
            self.context
                .device
                .create_pipeline_layout(&create_info, None)
        }
        .map_err(|error| {
            GalError::backend(format!(
                "failed to create pipeline layout '{}': {error:?}",
                desc.label
            ))
        })?;
        self.context
            .set_object_name(layout, &debug_name("pipeline-layout", handle, &desc.label));
        Ok(PipelineLayoutObject { token, layout })
    }

    fn create_graphics_pipeline(
        &self,
        handle: Handle,
        desc: &GraphicsPipelineDesc,
        token: BackendToken,
    ) -> GalResult<GraphicsPipelineObject> {
        let vertex = self.shader(desc.vertex_shader)?;
        let fragment = self.shader(desc.fragment_shader)?;
        let layout = self.pipeline_layout(desc.layout)?.layout;
        let stages = [shader_stage_create(vertex), shader_stage_create(fragment)];
        let vertex_input = vk::PipelineVertexInputStateCreateInfo::default();
        let input_assembly =
            vk::PipelineInputAssemblyStateCreateInfo::default().topology(topology(desc.topology));
        let viewport_state = vk::PipelineViewportStateCreateInfo::default()
            .viewport_count(1)
            .scissor_count(1);
        let rasterization = vk::PipelineRasterizationStateCreateInfo::default()
            .polygon_mode(vk::PolygonMode::FILL)
            .line_width(1.0)
            .cull_mode(cull_mode(desc.cull_mode))
            .front_face(vk::FrontFace::COUNTER_CLOCKWISE);
        let multisample = vk::PipelineMultisampleStateCreateInfo::default()
            .rasterization_samples(vk::SampleCountFlags::TYPE_1);
        let color_blend_attachments = desc
            .color_formats
            .iter()
            .map(|_| color_blend_attachment(desc.blend))
            .collect::<Vec<_>>();
        let color_blend =
            vk::PipelineColorBlendStateCreateInfo::default().attachments(&color_blend_attachments);
        let dynamic_states = [vk::DynamicState::VIEWPORT, vk::DynamicState::SCISSOR];
        let dynamic_state =
            vk::PipelineDynamicStateCreateInfo::default().dynamic_states(&dynamic_states);
        let depth_state = desc.depth_format.map(|_| {
            vk::PipelineDepthStencilStateCreateInfo::default()
                .depth_test_enable(true)
                .depth_write_enable(desc.depth_write)
                .depth_compare_op(compare_op(
                    desc.depth_compare.unwrap_or(CompareOp::LessOrEqual),
                ))
        });
        let color_formats = desc
            .color_formats
            .iter()
            .copied()
            .map(texture_format)
            .collect::<Vec<_>>();
        let mut rendering = vk::PipelineRenderingCreateInfo::default()
            .color_attachment_formats(&color_formats)
            .depth_attachment_format(
                desc.depth_format
                    .map(texture_format)
                    .unwrap_or(vk::Format::UNDEFINED),
            );
        let mut create_info = vk::GraphicsPipelineCreateInfo::default()
            .stages(&stages)
            .vertex_input_state(&vertex_input)
            .input_assembly_state(&input_assembly)
            .viewport_state(&viewport_state)
            .rasterization_state(&rasterization)
            .multisample_state(&multisample)
            .color_blend_state(&color_blend)
            .dynamic_state(&dynamic_state)
            .layout(layout)
            .push_next(&mut rendering);
        if let Some(depth_state) = depth_state.as_ref() {
            create_info = create_info.depth_stencil_state(depth_state);
        }
        let pipeline = unsafe {
            self.context.device.create_graphics_pipelines(
                vk::PipelineCache::null(),
                &[create_info],
                None,
            )
        }
        .map_err(|(_, error)| {
            GalError::backend(format!(
                "failed to create graphics pipeline '{}': {error:?}",
                desc.label
            ))
        })?
        .remove(0);
        self.context.set_object_name(
            pipeline,
            &debug_name("graphics-pipeline", handle, &desc.label),
        );
        Ok(GraphicsPipelineObject {
            token,
            pipeline,
            layout: desc.layout,
        })
    }

    fn create_compute_pipeline(
        &self,
        handle: Handle,
        desc: &ComputePipelineDesc,
        token: BackendToken,
    ) -> GalResult<ComputePipelineObject> {
        let shader = self.shader(desc.shader)?;
        let layout = self.pipeline_layout(desc.layout)?.layout;
        let stage = shader_stage_create(shader);
        let create_info = vk::ComputePipelineCreateInfo::default()
            .stage(stage)
            .layout(layout);
        let pipeline = unsafe {
            self.context.device.create_compute_pipelines(
                vk::PipelineCache::null(),
                &[create_info],
                None,
            )
        }
        .map_err(|(_, error)| {
            GalError::backend(format!(
                "failed to create compute pipeline '{}': {error:?}",
                desc.label
            ))
        })?
        .remove(0);
        self.context.set_object_name(
            pipeline,
            &debug_name("compute-pipeline", handle, &desc.label),
        );
        Ok(ComputePipelineObject {
            token,
            pipeline,
            layout: desc.layout,
        })
    }

    fn shader(&self, handle: Handle) -> GalResult<&ShaderModuleObject> {
        match self.objects.get(&handle) {
            Some(VulkanObject::ShaderModule(shader)) => Ok(shader),
            _ => Err(GalError::backend(
                "pipeline references missing shader module",
            )),
        }
    }

    fn destroy_object(&self, object: VulkanObject) {
        let _zone = trace::Zone::new("vulkan.resources.destroy-native");
        unsafe {
            match object {
                VulkanObject::Buffer(object) => {
                    self.context.device.destroy_buffer(object.buffer, None);
                    self.context.device.free_memory(object.memory, None);
                }
                VulkanObject::Texture(object) => {
                    self.context.device.destroy_image(object.image, None);
                    self.context.device.free_memory(object.memory, None);
                }
                VulkanObject::TextureView(object) => {
                    self.context.device.destroy_image_view(object.view, None);
                }
                VulkanObject::Sampler(object) => {
                    self.context.device.destroy_sampler(object.sampler, None);
                }
                VulkanObject::ShaderModule(object) => {
                    self.context
                        .device
                        .destroy_shader_module(object.module, None);
                }
                VulkanObject::ResourceLayout(object) => {
                    self.context
                        .device
                        .destroy_descriptor_set_layout(object.layout, None);
                }
                VulkanObject::ResourceSet(object) => {
                    self.context
                        .device
                        .destroy_descriptor_pool(object.pool, None);
                }
                VulkanObject::PipelineLayout(object) => {
                    self.context
                        .device
                        .destroy_pipeline_layout(object.layout, None);
                }
                VulkanObject::GraphicsPipeline(object) => {
                    self.context.device.destroy_pipeline(object.pipeline, None);
                }
                VulkanObject::ComputePipeline(object) => {
                    self.context.device.destroy_pipeline(object.pipeline, None);
                }
                VulkanObject::RenderTarget(_)
                | VulkanObject::FrameTarget(_)
                | VulkanObject::RenderPass(_) => {}
            }
        }
    }
}

impl Drop for VulkanObjects {
    fn drop(&mut self) {
        self.destroy_all();
    }
}

pub(super) enum VulkanObject {
    Buffer(BufferObject),
    Texture(TextureObject),
    TextureView(TextureViewObject),
    Sampler(SamplerObject),
    ShaderModule(ShaderModuleObject),
    ResourceLayout(ResourceLayoutObject),
    ResourceSet(ResourceSetObject),
    PipelineLayout(PipelineLayoutObject),
    GraphicsPipeline(GraphicsPipelineObject),
    ComputePipeline(ComputePipelineObject),
    RenderTarget(RenderTargetObject),
    FrameTarget(FrameTargetObject),
    RenderPass(RenderPassObject),
}

impl VulkanObject {
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
            Self::ComputePipeline(object) => object.token,
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
            Self::ComputePipeline(_) => HandleKind::ComputePipeline,
            Self::RenderTarget(_) => HandleKind::RenderTarget,
            Self::FrameTarget(_) => HandleKind::FrameTarget,
            Self::RenderPass(_) => HandleKind::RenderPass,
        }
    }
}

#[allow(dead_code)]
pub(super) struct BufferObject {
    pub(super) token: BackendToken,
    pub(super) buffer: vk::Buffer,
    pub(super) memory: vk::DeviceMemory,
    pub(super) size: u64,
    pub(super) memory_domain: MemoryDomain,
}

#[allow(dead_code)]
pub(super) struct TextureObject {
    pub(super) token: BackendToken,
    pub(super) image: vk::Image,
    pub(super) memory: vk::DeviceMemory,
    pub(super) format: vk::Format,
    pub(super) extent: Extent3d,
    pub(super) mip_levels: u32,
    pub(super) array_layers: u32,
    pub(super) aspect: vk::ImageAspectFlags,
}

#[allow(dead_code)]
pub(super) struct TextureViewObject {
    pub(super) token: BackendToken,
    pub(super) view: vk::ImageView,
    pub(super) texture: Handle,
    pub(super) format: vk::Format,
    pub(super) aspect: vk::ImageAspectFlags,
}

pub(super) struct SamplerObject {
    pub(super) token: BackendToken,
    pub(super) sampler: vk::Sampler,
}

pub(super) struct ShaderModuleObject {
    pub(super) token: BackendToken,
    pub(super) module: vk::ShaderModule,
    pub(super) stage: vk::ShaderStageFlags,
    pub(super) entry_point: CString,
}

pub(super) struct ResourceLayoutObject {
    pub(super) token: BackendToken,
    pub(super) layout: vk::DescriptorSetLayout,
    pub(super) bindings: Vec<ResourceBindingDesc>,
}

pub(super) struct ResourceSetObject {
    pub(super) token: BackendToken,
    pub(super) pool: vk::DescriptorPool,
    pub(super) set: vk::DescriptorSet,
    pub(super) dynamic_offsets: Vec<u32>,
}

pub(super) struct PipelineLayoutObject {
    pub(super) token: BackendToken,
    pub(super) layout: vk::PipelineLayout,
}

pub(super) struct GraphicsPipelineObject {
    pub(super) token: BackendToken,
    pub(super) pipeline: vk::Pipeline,
    pub(super) layout: Handle,
}

pub(super) struct ComputePipelineObject {
    pub(super) token: BackendToken,
    pub(super) pipeline: vk::Pipeline,
    pub(super) layout: Handle,
}

#[allow(dead_code)]
pub(super) struct RenderTargetObject {
    pub(super) token: BackendToken,
    pub(super) color_views: Vec<Handle>,
    pub(super) depth_stencil_view: Option<Handle>,
    pub(super) extent: Extent3d,
}

#[allow(dead_code)]
pub(super) struct FrameTargetObject {
    pub(super) token: BackendToken,
    pub(super) frame_id: u64,
    pub(super) render_target: crate::render::vulkanic::frame::FrameRenderTargetId,
    pub(super) extent: Extent3d,
    pub(super) color_format: TextureFormat,
    pub(super) image_index: u32,
    pub(super) image: vk::Image,
    pub(super) image_view: vk::ImageView,
    pub(super) image_layout: vk::ImageLayout,
}

#[allow(dead_code)]
pub(super) struct RenderPassObject {
    pub(super) token: BackendToken,
    pub(super) label: String,
    pub(super) target: Handle,
    pub(super) color_formats: Vec<ColorFormat>,
    pub(super) depth_format: Option<TextureFormat>,
}

pub(super) fn texture_format(format: TextureFormat) -> vk::Format {
    match format {
        TextureFormat::Rgba8Unorm => vk::Format::R8G8B8A8_UNORM,
        TextureFormat::Bgra8Unorm => vk::Format::B8G8R8A8_UNORM,
        TextureFormat::Rgba16Float => vk::Format::R16G16B16A16_SFLOAT,
        TextureFormat::Depth24Stencil8 => vk::Format::D24_UNORM_S8_UINT,
        TextureFormat::Depth32Float => vk::Format::D32_SFLOAT,
    }
}

pub(super) fn aspect_for_format(format: TextureFormat) -> vk::ImageAspectFlags {
    match format {
        TextureFormat::Depth24Stencil8 => {
            vk::ImageAspectFlags::DEPTH | vk::ImageAspectFlags::STENCIL
        }
        TextureFormat::Depth32Float => vk::ImageAspectFlags::DEPTH,
        _ => vk::ImageAspectFlags::COLOR,
    }
}

pub(super) fn texture_usage_flags(usages: &[TextureUsage]) -> vk::ImageUsageFlags {
    let mut flags = vk::ImageUsageFlags::empty();
    for usage in usages {
        flags |= match usage {
            TextureUsage::Sampled => vk::ImageUsageFlags::SAMPLED,
            TextureUsage::Storage => vk::ImageUsageFlags::STORAGE,
            TextureUsage::ColorAttachment => vk::ImageUsageFlags::COLOR_ATTACHMENT,
            TextureUsage::DepthStencilAttachment => vk::ImageUsageFlags::DEPTH_STENCIL_ATTACHMENT,
            TextureUsage::TransferSrc | TextureUsage::HostRead => vk::ImageUsageFlags::TRANSFER_SRC,
            TextureUsage::TransferDst | TextureUsage::HostWrite => {
                vk::ImageUsageFlags::TRANSFER_DST
            }
            TextureUsage::Present => vk::ImageUsageFlags::COLOR_ATTACHMENT,
        };
    }
    flags
}

pub(super) fn buffer_usage_flags(usages: &[BufferUsage]) -> vk::BufferUsageFlags {
    let mut flags = vk::BufferUsageFlags::empty();
    for usage in usages {
        flags |= match usage {
            BufferUsage::Vertex => vk::BufferUsageFlags::VERTEX_BUFFER,
            BufferUsage::Index => vk::BufferUsageFlags::INDEX_BUFFER,
            BufferUsage::Uniform => vk::BufferUsageFlags::UNIFORM_BUFFER,
            BufferUsage::Storage => vk::BufferUsageFlags::STORAGE_BUFFER,
            BufferUsage::TransferSrc | BufferUsage::HostRead => vk::BufferUsageFlags::TRANSFER_SRC,
            BufferUsage::TransferDst | BufferUsage::HostWrite => vk::BufferUsageFlags::TRANSFER_DST,
            BufferUsage::Indirect => vk::BufferUsageFlags::INDIRECT_BUFFER,
        };
    }
    flags
}

pub(super) fn memory_flags(domain: MemoryDomain) -> vk::MemoryPropertyFlags {
    match domain {
        MemoryDomain::DeviceLocal => vk::MemoryPropertyFlags::DEVICE_LOCAL,
        MemoryDomain::Upload | MemoryDomain::Readback => {
            vk::MemoryPropertyFlags::HOST_VISIBLE | vk::MemoryPropertyFlags::HOST_COHERENT
        }
    }
}

pub(super) fn filter(filter: SamplerFilter) -> vk::Filter {
    match filter {
        SamplerFilter::Nearest => vk::Filter::NEAREST,
        SamplerFilter::Linear => vk::Filter::LINEAR,
    }
}

pub(super) fn mipmap_filter(filter: SamplerFilter) -> vk::SamplerMipmapMode {
    match filter {
        SamplerFilter::Nearest => vk::SamplerMipmapMode::NEAREST,
        SamplerFilter::Linear => vk::SamplerMipmapMode::LINEAR,
    }
}

pub(super) fn address_mode(mode: SamplerAddressMode) -> vk::SamplerAddressMode {
    match mode {
        SamplerAddressMode::ClampToEdge => vk::SamplerAddressMode::CLAMP_TO_EDGE,
        SamplerAddressMode::Repeat => vk::SamplerAddressMode::REPEAT,
        SamplerAddressMode::MirroredRepeat => vk::SamplerAddressMode::MIRRORED_REPEAT,
    }
}

pub(super) fn shader_stage(stage: ShaderStage) -> vk::ShaderStageFlags {
    match stage {
        ShaderStage::Vertex => vk::ShaderStageFlags::VERTEX,
        ShaderStage::Fragment => vk::ShaderStageFlags::FRAGMENT,
        ShaderStage::Compute => vk::ShaderStageFlags::COMPUTE,
        ShaderStage::Geometry => vk::ShaderStageFlags::GEOMETRY,
        ShaderStage::TessControl => vk::ShaderStageFlags::TESSELLATION_CONTROL,
        ShaderStage::TessEvaluation => vk::ShaderStageFlags::TESSELLATION_EVALUATION,
    }
}

fn shaderc_kind(stage: ShaderStage) -> GalResult<shaderc::ShaderKind> {
    match stage {
        ShaderStage::Vertex => Ok(shaderc::ShaderKind::Vertex),
        ShaderStage::Fragment => Ok(shaderc::ShaderKind::Fragment),
        ShaderStage::Compute => Ok(shaderc::ShaderKind::Compute),
        ShaderStage::Geometry => Ok(shaderc::ShaderKind::Geometry),
        ShaderStage::TessControl => Ok(shaderc::ShaderKind::TessControl),
        ShaderStage::TessEvaluation => Ok(shaderc::ShaderKind::TessEvaluation),
    }
}

pub(super) fn shader_stage_flags(stages: PipelineStageFlags) -> vk::ShaderStageFlags {
    let mut flags = vk::ShaderStageFlags::empty();
    if stages.0 & PipelineStageFlags::DRAW.0 != 0 {
        flags |= vk::ShaderStageFlags::VERTEX | vk::ShaderStageFlags::FRAGMENT;
    }
    if stages.0 & PipelineStageFlags::COMPUTE.0 != 0 {
        flags |= vk::ShaderStageFlags::COMPUTE;
    }
    flags
}

pub(super) fn descriptor_type(kind: ResourceBindingKind) -> vk::DescriptorType {
    match kind {
        ResourceBindingKind::UniformBuffer => vk::DescriptorType::UNIFORM_BUFFER,
        ResourceBindingKind::StorageBuffer => vk::DescriptorType::STORAGE_BUFFER,
        ResourceBindingKind::SampledTexture => vk::DescriptorType::SAMPLED_IMAGE,
        ResourceBindingKind::StorageTexture => vk::DescriptorType::STORAGE_IMAGE,
        ResourceBindingKind::Sampler => vk::DescriptorType::SAMPLER,
    }
}

fn descriptor_type_for_binding(
    kind: ResourceBindingKind,
    dynamic_offset_count: u32,
) -> vk::DescriptorType {
    if dynamic_offset_count == 0 {
        return descriptor_type(kind);
    }
    match kind {
        ResourceBindingKind::UniformBuffer => vk::DescriptorType::UNIFORM_BUFFER_DYNAMIC,
        ResourceBindingKind::StorageBuffer => vk::DescriptorType::STORAGE_BUFFER_DYNAMIC,
        _ => descriptor_type(kind),
    }
}

fn descriptor_type_for_resource_binding(binding: &ResourceBinding) -> vk::DescriptorType {
    descriptor_type_for_binding(binding.kind, binding.dynamic_offsets.len() as u32)
}

pub(super) fn shader_stage_create(
    shader: &ShaderModuleObject,
) -> vk::PipelineShaderStageCreateInfo<'_> {
    let name: &CStr = shader.entry_point.as_c_str();
    vk::PipelineShaderStageCreateInfo::default()
        .stage(shader.stage)
        .module(shader.module)
        .name(name)
}

pub(super) fn topology(topology: PrimitiveTopology) -> vk::PrimitiveTopology {
    match topology {
        PrimitiveTopology::Points => vk::PrimitiveTopology::POINT_LIST,
        PrimitiveTopology::Lines => vk::PrimitiveTopology::LINE_LIST,
        PrimitiveTopology::Triangles => vk::PrimitiveTopology::TRIANGLE_LIST,
    }
}

pub(super) fn cull_mode(mode: CullMode) -> vk::CullModeFlags {
    match mode {
        CullMode::None => vk::CullModeFlags::NONE,
        CullMode::Front => vk::CullModeFlags::FRONT,
        CullMode::Back => vk::CullModeFlags::BACK,
    }
}

pub(super) fn compare_op(compare: CompareOp) -> vk::CompareOp {
    match compare {
        CompareOp::Always => vk::CompareOp::ALWAYS,
        CompareOp::Less => vk::CompareOp::LESS,
        CompareOp::LessOrEqual => vk::CompareOp::LESS_OR_EQUAL,
        CompareOp::Equal => vk::CompareOp::EQUAL,
    }
}

pub(super) fn color_blend_attachment(blend: BlendMode) -> vk::PipelineColorBlendAttachmentState {
    match blend {
        BlendMode::Disabled => vk::PipelineColorBlendAttachmentState::default()
            .color_write_mask(vk::ColorComponentFlags::RGBA),
        BlendMode::Alpha => vk::PipelineColorBlendAttachmentState::default()
            .blend_enable(true)
            .src_color_blend_factor(vk::BlendFactor::SRC_ALPHA)
            .dst_color_blend_factor(vk::BlendFactor::ONE_MINUS_SRC_ALPHA)
            .color_blend_op(vk::BlendOp::ADD)
            .src_alpha_blend_factor(vk::BlendFactor::ONE)
            .dst_alpha_blend_factor(vk::BlendFactor::ONE_MINUS_SRC_ALPHA)
            .alpha_blend_op(vk::BlendOp::ADD)
            .color_write_mask(vk::ColorComponentFlags::RGBA),
        BlendMode::Additive => vk::PipelineColorBlendAttachmentState::default()
            .blend_enable(true)
            .src_color_blend_factor(vk::BlendFactor::ONE)
            .dst_color_blend_factor(vk::BlendFactor::ONE)
            .color_blend_op(vk::BlendOp::ADD)
            .src_alpha_blend_factor(vk::BlendFactor::ONE)
            .dst_alpha_blend_factor(vk::BlendFactor::ONE)
            .alpha_blend_op(vk::BlendOp::ADD)
            .color_write_mask(vk::ColorComponentFlags::RGBA),
        BlendMode::Invert => vk::PipelineColorBlendAttachmentState::default()
            .blend_enable(true)
            .src_color_blend_factor(vk::BlendFactor::ONE_MINUS_DST_COLOR)
            .dst_color_blend_factor(vk::BlendFactor::ONE_MINUS_SRC_COLOR)
            .color_blend_op(vk::BlendOp::ADD)
            .src_alpha_blend_factor(vk::BlendFactor::ONE)
            .dst_alpha_blend_factor(vk::BlendFactor::ZERO)
            .alpha_blend_op(vk::BlendOp::ADD)
            .color_write_mask(vk::ColorComponentFlags::RGBA),
        BlendMode::Multiply => vk::PipelineColorBlendAttachmentState::default()
            .blend_enable(true)
            .src_color_blend_factor(vk::BlendFactor::DST_COLOR)
            .dst_color_blend_factor(vk::BlendFactor::ZERO)
            .color_blend_op(vk::BlendOp::ADD)
            .src_alpha_blend_factor(vk::BlendFactor::ONE)
            .dst_alpha_blend_factor(vk::BlendFactor::ZERO)
            .alpha_blend_op(vk::BlendOp::ADD)
            .color_write_mask(vk::ColorComponentFlags::RGBA),
        BlendMode::Overlay => vk::PipelineColorBlendAttachmentState::default()
            .blend_enable(true)
            .src_color_blend_factor(vk::BlendFactor::SRC_ALPHA)
            .dst_color_blend_factor(vk::BlendFactor::ONE)
            .color_blend_op(vk::BlendOp::ADD)
            .src_alpha_blend_factor(vk::BlendFactor::ONE)
            .dst_alpha_blend_factor(vk::BlendFactor::ZERO)
            .alpha_blend_op(vk::BlendOp::ADD)
            .color_write_mask(vk::ColorComponentFlags::RGBA),
    }
}

fn debug_name(kind: &str, handle: Handle, label: &str) -> String {
    format!("gal.{kind}.0x{:016x}.{label}", handle.raw())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn overlay_blend_lowers_to_source_alpha_additive_equation() {
        let attachment = color_blend_attachment(BlendMode::Overlay);
        assert_eq!(vk::TRUE, attachment.blend_enable);
        assert!(attachment.src_color_blend_factor == vk::BlendFactor::SRC_ALPHA);
        assert!(attachment.dst_color_blend_factor == vk::BlendFactor::ONE);
        assert!(attachment.color_blend_op == vk::BlendOp::ADD);
        assert!(attachment.src_alpha_blend_factor == vk::BlendFactor::ONE);
        assert!(attachment.dst_alpha_blend_factor == vk::BlendFactor::ZERO);
        assert!(attachment.alpha_blend_op == vk::BlendOp::ADD);
        assert!(attachment.color_write_mask == vk::ColorComponentFlags::RGBA);
    }

    #[test]
    fn multiply_blend_lowers_to_single_source_times_destination() {
        let attachment = color_blend_attachment(BlendMode::Multiply);
        assert_eq!(vk::TRUE, attachment.blend_enable);
        assert!(attachment.src_color_blend_factor == vk::BlendFactor::DST_COLOR);
        assert!(attachment.dst_color_blend_factor == vk::BlendFactor::ZERO);
        assert!(attachment.color_blend_op == vk::BlendOp::ADD);
        assert!(attachment.src_alpha_blend_factor == vk::BlendFactor::ONE);
        assert!(attachment.dst_alpha_blend_factor == vk::BlendFactor::ZERO);
        assert!(attachment.alpha_blend_op == vk::BlendOp::ADD);
        assert!(attachment.color_write_mask == vk::ColorComponentFlags::RGBA);
    }
}

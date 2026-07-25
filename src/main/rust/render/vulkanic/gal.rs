use std::collections::{BTreeMap, BTreeSet};

use super::backends::{Backend, BackendCreateDesc, BackendToken, CompletedHostRead};
use super::commands::{
    BufferImageCopyRegion, CommandList, CommandListDesc, CommandOp, ResourceBarrier,
    SubmissionBatch, ValidatedSubmissionBatch,
};
use super::error::{GalError, GalResult, StatusCode};
use super::frame::{
    AcquiredFrame, FrameAcquireDesc, FrameResizeDesc, FrameResizeResult, FrameSurfaceDesc,
    PresentFrameDesc, PresentedFrame,
};
use super::handles::{Handle, HandleKind, MAX_GENERATION};
use super::metrics::Metrics;
use super::resources::*;
use super::sync::{RetirementQueue, SubmissionId, SyncToken};

#[derive(Clone, Debug)]
struct Slot<T> {
    generation: u32,
    last_destroyed_generation: Option<u32>,
    value: Option<T>,
}

#[derive(Clone, Debug)]
struct Arena<T> {
    kind: HandleKind,
    slots: Vec<Slot<T>>,
}

impl<T> Arena<T> {
    #[allow(dead_code)]
    fn new(kind: HandleKind) -> Self {
        Self {
            kind,
            slots: Vec::new(),
        }
    }

    fn next_handle(&self) -> GalResult<Handle> {
        for (index, slot) in self.slots.iter().enumerate() {
            if slot.value.is_none() {
                let index = u32::try_from(index).map_err(|_| {
                    GalError::handle(
                        StatusCode::GenerationExhausted,
                        "handle index space exhausted",
                    )
                })?;
                return Handle::new(self.kind, index, slot.generation);
            }
        }
        let index = u32::try_from(self.slots.len()).map_err(|_| {
            GalError::handle(
                StatusCode::GenerationExhausted,
                "handle index space exhausted",
            )
        })?;
        Handle::new(self.kind, index, 1)
    }

    fn insert_at(&mut self, handle: Handle, value: T) -> GalResult<Handle> {
        let (index, generation) = handle.require_kind(self.kind)?;
        if index == self.slots.len() {
            self.slots.push(Slot {
                generation,
                last_destroyed_generation: None,
                value: Some(value),
            });
            return Ok(handle);
        }
        let slot = self.slots.get_mut(index).ok_or_else(|| {
            GalError::handle(StatusCode::StaleHandle, "handle slot does not exist")
        })?;
        if slot.generation != generation || slot.value.is_some() {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot is not available for insertion",
            ));
        }
        slot.last_destroyed_generation = None;
        slot.value = Some(value);
        Ok(handle)
    }

    fn get(&self, handle: Handle) -> GalResult<&T> {
        let (index, generation) = handle.require_kind(self.kind)?;
        let Some(slot) = self.slots.get(index) else {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot does not exist",
            ));
        };
        if slot.generation != generation {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "stale handle generation",
            ));
        }
        slot.value.as_ref().ok_or_else(|| {
            if slot.last_destroyed_generation == Some(generation) {
                GalError::handle(StatusCode::DoubleDestroy, "resource was already destroyed")
            } else {
                GalError::handle(StatusCode::StaleHandle, "resource is not live")
            }
        })
    }

    fn remove(&mut self, handle: Handle) -> GalResult<T> {
        let (index, generation) = handle.require_kind(self.kind)?;
        let Some(slot) = self.slots.get_mut(index) else {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot does not exist",
            ));
        };
        if slot.generation != generation {
            if slot.last_destroyed_generation == Some(generation) {
                return Err(GalError::handle(
                    StatusCode::DoubleDestroy,
                    "resource was already destroyed",
                ));
            }
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "stale handle generation",
            ));
        }
        let value = slot.value.take().ok_or_else(|| {
            GalError::handle(StatusCode::DoubleDestroy, "resource was already destroyed")
        })?;
        if slot.generation == MAX_GENERATION {
            slot.value = Some(value);
            return Err(GalError::handle(
                StatusCode::GenerationExhausted,
                "resource generation exhausted",
            ));
        }
        slot.last_destroyed_generation = Some(slot.generation);
        slot.generation += 1;
        Ok(value)
    }

    #[cfg(test)]
    fn force_generation(&mut self, handle: Handle, generation: u32) {
        let index = handle.index() as usize;
        self.slots[index].generation = generation;
    }
}

#[derive(Clone, Debug)]
struct ResourceRecord<T> {
    desc: T,
    token: BackendToken,
    last_submission: Option<SubmissionId>,
}

#[derive(Clone, Copy, Debug)]
struct PendingDestroy {
    kind: HandleKind,
    token: BackendToken,
}

#[derive(Clone, Debug)]
struct TextureViewInfo {
    texture: Handle,
    format: TextureFormat,
    extent: Extent3d,
    range: TextureSubresourceRange,
    usages: Vec<TextureUsage>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum AccessMode {
    Read,
    Write,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum AccessFamily {
    Vertex,
    Index,
    Uniform,
    Storage,
    Sampled,
    Transfer,
    Attachment,
    Host,
    Present,
    Indirect,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum AccessTarget {
    Buffer {
        handle: Handle,
        offset: u64,
        size: u64,
    },
    Texture {
        texture: Handle,
        range: TextureSubresourceRange,
    },
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
struct AccessEvent {
    target: AccessTarget,
    mode: AccessMode,
    family: AccessFamily,
}

pub struct VulkanicGal {
    backend: Box<dyn Backend>,
    buffers: Arena<ResourceRecord<BufferDesc>>,
    textures: Arena<ResourceRecord<TextureDesc>>,
    texture_views: Arena<ResourceRecord<TextureViewDesc>>,
    samplers: Arena<ResourceRecord<SamplerDesc>>,
    shaders: Arena<ResourceRecord<ShaderModuleDesc>>,
    resource_layouts: Arena<ResourceRecord<ResourceLayoutDesc>>,
    resource_sets: Arena<ResourceRecord<ResourceSetDesc>>,
    pipeline_layouts: Arena<ResourceRecord<PipelineLayoutDesc>>,
    graphics_pipelines: Arena<ResourceRecord<GraphicsPipelineDesc>>,
    compute_pipelines: Arena<ResourceRecord<ComputePipelineDesc>>,
    render_targets: Arena<ResourceRecord<RenderTargetDesc>>,
    frame_targets: Arena<ResourceRecord<FrameTargetDesc>>,
    render_passes: Arena<ResourceRecord<RenderPassDesc>>,
    dependencies: BTreeMap<Handle, BTreeSet<Handle>>,
    reverse_dependencies: BTreeMap<Handle, BTreeSet<Handle>>,
    pending_destroys: BTreeMap<Handle, PendingDestroy>,
    retirement: RetirementQueue,
    next_submission: u64,
    completed_submission: SubmissionId,
    metrics: Metrics,
}

impl VulkanicGal {
    #[allow(dead_code)]
    pub(in crate::render::vulkanic) fn new_with_backend(
        backend: Box<dyn Backend>,
        tracy_enabled: bool,
    ) -> Self {
        Self {
            backend,
            buffers: Arena::new(HandleKind::Buffer),
            textures: Arena::new(HandleKind::Texture),
            texture_views: Arena::new(HandleKind::TextureView),
            samplers: Arena::new(HandleKind::Sampler),
            shaders: Arena::new(HandleKind::ShaderModule),
            resource_layouts: Arena::new(HandleKind::ResourceLayout),
            resource_sets: Arena::new(HandleKind::ResourceSet),
            pipeline_layouts: Arena::new(HandleKind::PipelineLayout),
            graphics_pipelines: Arena::new(HandleKind::GraphicsPipeline),
            compute_pipelines: Arena::new(HandleKind::ComputePipeline),
            render_targets: Arena::new(HandleKind::RenderTarget),
            frame_targets: Arena::new(HandleKind::FrameTarget),
            render_passes: Arena::new(HandleKind::RenderPass),
            dependencies: BTreeMap::new(),
            reverse_dependencies: BTreeMap::new(),
            pending_destroys: BTreeMap::new(),
            retirement: RetirementQueue::new(),
            next_submission: 1,
            completed_submission: SubmissionId(0),
            metrics: Metrics::new(tracy_enabled),
        }
    }

    pub fn metrics(&self) -> &Metrics {
        &self.metrics
    }

    pub fn capabilities(&self) -> BackendCapabilities {
        self.backend.capabilities()
    }

    pub fn configure_frame_surface(&mut self, desc: FrameSurfaceDesc) -> GalResult<()> {
        if desc.extent.width == 0 || desc.extent.height == 0 || desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame surface extent must be non-zero",
            ));
        }
        if desc.max_frames_in_flight == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame surface must allow at least one frame in flight",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.configure_frame_surface(&desc)
    }

    pub fn acquire_frame(&mut self, desc: FrameAcquireDesc) -> GalResult<AcquiredFrame> {
        if desc.expected_extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame acquire extent depth must be non-zero",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.acquire_frame(&desc)
    }

    pub fn resize_frame_surface(&mut self, desc: FrameResizeDesc) -> GalResult<FrameResizeResult> {
        if desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame resize extent depth must be non-zero",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.resize_frame_surface(&desc)
    }

    pub fn present_frame(&mut self, desc: PresentFrameDesc) -> GalResult<PresentedFrame> {
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        let presented = self.backend.present_frame(&desc)?;
        if presented.completed_submission > self.completed_submission {
            self.completed_submission = presented.completed_submission;
        }
        Ok(presented)
    }

    pub fn shutdown_frame_surface(&mut self) -> GalResult<()> {
        self.backend.shutdown_frame_surface()
    }

    pub fn create_frame_target(&mut self, desc: FrameTargetDesc) -> GalResult<Handle> {
        if desc.extent.width == 0 || desc.extent.height == 0 || desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame target extent must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        let handle = self.frame_targets.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::FrameTarget(&desc))?;
        self.metrics.resource_creates += 1;
        self.frame_targets.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_buffer(&mut self, desc: BufferDesc) -> GalResult<Handle> {
        if desc.size == 0 || desc.usages.is_empty() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "buffer size and usages must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if desc.size > capabilities.limits.max_buffer_size {
            return self.unsupported(format!(
                "buffer '{}' size {} exceeds backend '{}' limit {}",
                desc.label, desc.size, capabilities.name, capabilities.limits.max_buffer_size
            ));
        }
        if desc.usages.contains(&BufferUsage::Storage)
            && !capabilities.supports(BackendFeature::StorageBuffers)
        {
            return self.unsupported(format!(
                "backend '{}' does not support storage buffers",
                capabilities.name
            ));
        }
        let handle = self.buffers.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::Buffer(&desc))?;
        self.metrics.resource_creates += 1;
        self.buffers.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_texture(&mut self, desc: TextureDesc) -> GalResult<Handle> {
        if desc.extent.width == 0
            || desc.extent.height == 0
            || desc.extent.depth == 0
            || desc.mip_levels == 0
            || desc.array_layers == 0
            || desc.usages.is_empty()
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture extent, levels, layers, and usages must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if desc.dimension != TextureDimension::D2 {
            return self.unsupported(format!(
                "backend '{}' currently supports D2 textures only",
                capabilities.name
            ));
        }
        if desc.extent.width > capabilities.limits.max_texture_extent_2d
            || desc.extent.height > capabilities.limits.max_texture_extent_2d
        {
            return self.unsupported(format!(
                "texture '{}' extent {}x{} exceeds backend '{}' 2D extent limit {}",
                desc.label,
                desc.extent.width,
                desc.extent.height,
                capabilities.name,
                capabilities.limits.max_texture_extent_2d
            ));
        }
        if desc.mip_levels > capabilities.limits.max_texture_mip_levels {
            return self.unsupported(format!(
                "texture '{}' mip count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.mip_levels,
                capabilities.name,
                capabilities.limits.max_texture_mip_levels
            ));
        }
        if desc.mip_levels > 1 && !capabilities.supports(BackendFeature::TextureMipLevels) {
            return self.unsupported(format!(
                "backend '{}' does not support multi-mip textures",
                capabilities.name
            ));
        }
        if desc.array_layers > capabilities.limits.max_texture_array_layers {
            return self.unsupported(format!(
                "texture '{}' layer count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.array_layers,
                capabilities.name,
                capabilities.limits.max_texture_array_layers
            ));
        }
        if desc.array_layers > 1 && !capabilities.supports(BackendFeature::TextureArrayLayers) {
            return self.unsupported(format!(
                "backend '{}' does not support texture arrays",
                capabilities.name
            ));
        }
        if desc.usages.contains(&TextureUsage::Storage)
            && !capabilities.supports(BackendFeature::StorageTextures)
        {
            return self.unsupported(format!(
                "backend '{}' does not support storage textures",
                capabilities.name
            ));
        }
        if desc.usages.contains(&TextureUsage::Present)
            && !capabilities.supports(BackendFeature::Presentation)
        {
            return self.unsupported(format!(
                "backend '{}' does not support presentation textures in the isolated path",
                capabilities.name
            ));
        }
        let handle = self.textures.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::Texture(&desc))?;
        self.metrics.resource_creates += 1;
        self.textures.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_texture_view(&mut self, desc: TextureViewDesc) -> GalResult<Handle> {
        let (texture_mip_levels, texture_array_layers, texture_format) = {
            let texture = self.textures.get(desc.texture)?;
            (
                texture.desc.mip_levels,
                texture.desc.array_layers,
                texture.desc.format,
            )
        };
        let Some(mip_end) = desc.base_mip.checked_add(desc.mip_count) else {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view mip range overflows",
            ));
        };
        let Some(layer_end) = desc.base_layer.checked_add(desc.layer_count) else {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view layer range overflows",
            ));
        };
        if desc.mip_count == 0
            || desc.layer_count == 0
            || mip_end > texture_mip_levels
            || layer_end > texture_array_layers
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view range is outside the texture",
            ));
        }
        if desc.format != texture_format {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view format reinterpretation is not modeled",
            ));
        }
        let handle = self.texture_views.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::TextureView(&desc))?;
        self.add_dependency(desc.texture, handle);
        self.metrics.resource_creates += 1;
        self.texture_views.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_sampler(&mut self, desc: SamplerDesc) -> GalResult<Handle> {
        let handle = self.samplers.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::Sampler(&desc))?;
        self.metrics.resource_creates += 1;
        self.samplers.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_shader_module(&mut self, desc: ShaderModuleDesc) -> GalResult<Handle> {
        if desc.code.is_empty() || desc.entry_point.trim().is_empty() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "shader code and entry point must be non-empty",
            ));
        }
        let handle = self.shaders.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::ShaderModule(&desc))?;
        self.metrics.resource_creates += 1;
        self.shaders.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_resource_layout(&mut self, desc: ResourceLayoutDesc) -> GalResult<Handle> {
        let capabilities = self.capabilities();
        if desc.bindings.len() > capabilities.limits.max_resource_layout_bindings as usize {
            return self.unsupported(format!(
                "resource layout '{}' binding count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.bindings.len(),
                capabilities.name,
                capabilities.limits.max_resource_layout_bindings
            ));
        }
        let mut seen = BTreeSet::new();
        for binding in &desc.bindings {
            if !seen.insert(binding.binding) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("duplicate resource binding {}", binding.binding),
                ));
            }
            if binding.array_count == 0 {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("binding {} array count must be non-zero", binding.binding),
                ));
            }
            if binding.array_count > 1 && !capabilities.supports(BackendFeature::DescriptorArrays) {
                return self.unsupported(format!(
                    "backend '{}' does not support descriptor arrays",
                    capabilities.name
                ));
            }
            if binding.array_count > capabilities.limits.max_binding_array_count {
                return self.unsupported(format!(
                    "binding {} array count {} exceeds backend '{}' limit {}",
                    binding.binding,
                    binding.array_count,
                    capabilities.name,
                    capabilities.limits.max_binding_array_count
                ));
            }
            if binding.optional && !capabilities.supports(BackendFeature::OptionalBindings) {
                return self.unsupported(format!(
                    "backend '{}' does not support optional bindings",
                    capabilities.name
                ));
            }
            if binding.stages == PipelineStageFlags::NONE {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("binding {} must declare shader stages", binding.binding),
                ));
            }
            if binding.dynamic_offset_count > 0
                && !matches!(
                    binding.kind,
                    ResourceBindingKind::UniformBuffer | ResourceBindingKind::StorageBuffer
                )
            {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!(
                        "binding {} dynamic offsets are only valid for buffer bindings",
                        binding.binding
                    ),
                ));
            }
            if binding.dynamic_offset_count > 0
                && !capabilities.supports(BackendFeature::DynamicBufferOffsets)
            {
                return self.unsupported(format!(
                    "backend '{}' does not support dynamic buffer offsets",
                    capabilities.name
                ));
            }
            if binding.dynamic_offset_count > capabilities.limits.max_dynamic_offsets_per_binding {
                return self.unsupported(format!(
                    "binding {} dynamic offset count {} exceeds backend '{}' limit {}",
                    binding.binding,
                    binding.dynamic_offset_count,
                    capabilities.name,
                    capabilities.limits.max_dynamic_offsets_per_binding
                ));
            }
            if binding.kind == ResourceBindingKind::StorageTexture
                && !capabilities.supports(BackendFeature::StorageTextures)
            {
                return self.unsupported(format!(
                    "backend '{}' does not support storage texture bindings",
                    capabilities.name
                ));
            }
        }
        let handle = self.resource_layouts.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::ResourceLayout(&desc))?;
        self.metrics.resource_creates += 1;
        self.resource_layouts.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_resource_set(&mut self, desc: ResourceSetDesc) -> GalResult<Handle> {
        let layout_bindings = self
            .resource_layouts
            .get(desc.layout)?
            .desc
            .bindings
            .clone();
        let mut seen = BTreeSet::new();
        let mut populated = BTreeSet::new();
        for binding in &desc.bindings {
            if !seen.insert((binding.binding, binding.array_index)) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!(
                        "duplicate resource set binding {}[{}]",
                        binding.binding, binding.array_index
                    ),
                ));
            }
            let Some(expected) = layout_bindings
                .iter()
                .find(|item| item.binding == binding.binding)
            else {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!(
                        "binding {} is not declared by resource layout",
                        binding.binding
                    ),
                ));
            };
            if binding.array_index >= expected.array_count {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!(
                        "binding {} array index {} is outside declared count {}",
                        binding.binding, binding.array_index, expected.array_count
                    ),
                ));
            }
            if expected.kind != binding.kind {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("binding {} kind mismatch", binding.binding),
                ));
            }
            if binding.dynamic_offsets.len() != expected.dynamic_offset_count as usize {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("binding {} dynamic offset count mismatch", binding.binding),
                ));
            }
            self.validate_binding_resource(binding)?;
            if !binding.access.reads() && !binding.access.writes() {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "resource binding must declare read or write access",
                ));
            }
            populated.insert((binding.binding, binding.array_index));
        }
        for expected in &layout_bindings {
            if expected.optional {
                continue;
            }
            for array_index in 0..expected.array_count {
                if !populated.contains(&(expected.binding, array_index)) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        format!(
                            "required binding {}[{}] is missing from resource set",
                            expected.binding, array_index
                        ),
                    ));
                }
            }
        }
        let handle = self.resource_sets.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::ResourceSet(&desc))?;
        self.add_dependency(desc.layout, handle);
        for binding in &desc.bindings {
            self.add_dependency(binding.resource, handle);
        }
        self.metrics.resource_creates += 1;
        self.resource_sets.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_pipeline_layout(&mut self, desc: PipelineLayoutDesc) -> GalResult<Handle> {
        for layout in &desc.resource_layouts {
            self.resource_layouts.get(*layout)?;
        }
        let handle = self.pipeline_layouts.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::PipelineLayout(&desc))?;
        for layout in &desc.resource_layouts {
            self.add_dependency(*layout, handle);
        }
        self.metrics.resource_creates += 1;
        self.pipeline_layouts.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_graphics_pipeline(&mut self, desc: GraphicsPipelineDesc) -> GalResult<Handle> {
        self.pipeline_layouts.get(desc.layout)?;
        self.require_shader_stage(desc.vertex_shader, ShaderStage::Vertex)?;
        self.require_shader_stage(desc.fragment_shader, ShaderStage::Fragment)?;
        if desc.color_formats.is_empty() && desc.depth_format.is_none() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "graphics pipeline needs at least one color or depth format",
            ));
        }
        let capabilities = self.capabilities();
        if desc.color_formats.len() > capabilities.limits.max_color_attachments as usize {
            return self.unsupported(format!(
                "graphics pipeline '{}' color attachment count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.color_formats.len(),
                capabilities.name,
                capabilities.limits.max_color_attachments
            ));
        }
        if desc.color_formats.len() > 1
            && !capabilities.supports(BackendFeature::MultipleColorAttachments)
        {
            return self.unsupported(format!(
                "backend '{}' does not support multiple color attachments",
                capabilities.name
            ));
        }
        if desc.color_formats.is_empty() && !capabilities.supports(BackendFeature::DepthOnlyPass) {
            return self.unsupported(format!(
                "backend '{}' does not support depth-only graphics passes",
                capabilities.name
            ));
        }
        if desc.blend != BlendMode::Disabled && !capabilities.supports(BackendFeature::BlendedPass)
        {
            return self.unsupported(format!(
                "backend '{}' does not support blended graphics passes",
                capabilities.name
            ));
        }
        let handle = self.graphics_pipelines.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::GraphicsPipeline(&desc))?;
        self.add_dependency(desc.layout, handle);
        self.add_dependency(desc.vertex_shader, handle);
        self.add_dependency(desc.fragment_shader, handle);
        self.metrics.resource_creates += 1;
        self.graphics_pipelines.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_compute_pipeline(&mut self, desc: ComputePipelineDesc) -> GalResult<Handle> {
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Compute) {
            return self.unsupported(format!(
                "backend '{}' does not support compute pipelines",
                capabilities.name
            ));
        }
        self.pipeline_layouts.get(desc.layout)?;
        self.require_shader_stage(desc.shader, ShaderStage::Compute)?;
        let handle = self.compute_pipelines.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::ComputePipeline(&desc))?;
        self.add_dependency(desc.layout, handle);
        self.add_dependency(desc.shader, handle);
        self.metrics.resource_creates += 1;
        self.compute_pipelines.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_render_target(&mut self, desc: RenderTargetDesc) -> GalResult<Handle> {
        if desc.color_views.is_empty() && desc.depth_stencil_view.is_none() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render target requires at least one attachment",
            ));
        }
        if desc.extent.width == 0 || desc.extent.height == 0 || desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render target extent must be non-empty",
            ));
        }
        let capabilities = self.capabilities();
        if desc.color_views.len() > capabilities.limits.max_color_attachments as usize {
            return self.unsupported(format!(
                "render target '{}' color attachment count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.color_views.len(),
                capabilities.name,
                capabilities.limits.max_color_attachments
            ));
        }
        if desc.color_views.len() > 1
            && !capabilities.supports(BackendFeature::MultipleColorAttachments)
        {
            return self.unsupported(format!(
                "backend '{}' does not support multiple color attachments",
                capabilities.name
            ));
        }
        if desc.color_views.is_empty() && !capabilities.supports(BackendFeature::DepthOnlyPass) {
            return self.unsupported(format!(
                "backend '{}' does not support depth-only render targets",
                capabilities.name
            ));
        }
        for view in &desc.color_views {
            let info = self.texture_view_info(*view)?;
            if is_depth_stencil_format(info.format) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "color attachment view uses a depth/stencil format",
                ));
            }
            if !info.usages.contains(&TextureUsage::ColorAttachment) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "color attachment texture lacks color attachment usage",
                ));
            }
            if info.extent != desc.extent {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "color attachment extent does not match render target",
                ));
            }
        }
        if let Some(view) = desc.depth_stencil_view {
            let info = self.texture_view_info(view)?;
            if !is_depth_stencil_format(info.format) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "depth attachment view does not use a depth/stencil format",
                ));
            }
            if !info.usages.contains(&TextureUsage::DepthStencilAttachment) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "depth attachment texture lacks depth/stencil attachment usage",
                ));
            }
            if info.extent != desc.extent {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "depth attachment extent does not match render target",
                ));
            }
        }
        let handle = self.render_targets.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::RenderTarget(&desc))?;
        for view in &desc.color_views {
            self.add_dependency(*view, handle);
        }
        if let Some(view) = desc.depth_stencil_view {
            self.add_dependency(view, handle);
        }
        self.metrics.resource_creates += 1;
        self.render_targets.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn create_render_pass(&mut self, desc: RenderPassDesc) -> GalResult<Handle> {
        let (target_color_formats, target_depth_format) = {
            match desc.target.kind() {
                Some(HandleKind::RenderTarget) => {
                    let target = self.render_targets.get(desc.target)?;
                    let color_views = target.desc.color_views.clone();
                    let depth_view = target.desc.depth_stencil_view;
                    let color_formats = color_views
                        .iter()
                        .map(|view| self.texture_view_info(*view).map(|info| info.format))
                        .collect::<GalResult<Vec<_>>>()?;
                    let depth_format = depth_view
                        .map(|view| self.texture_view_info(view).map(|info| info.format))
                        .transpose()?;
                    (color_formats, depth_format)
                }
                Some(HandleKind::FrameTarget) => {
                    let target = self.frame_targets.get(desc.target)?;
                    (vec![target.desc.color_format], None)
                }
                _ => {
                    return self.validation_error(GalError::resource(
                        StatusCode::WrongHandleType,
                        "render pass target must be a render target or frame target",
                    ))
                }
            }
        };
        if desc.color_formats != target_color_formats {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render pass color formats do not match target",
            ));
        }
        if desc.depth_format != target_depth_format {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render pass depth format does not match target",
            ));
        }
        let handle = self.render_passes.next_handle()?;
        let token = self
            .backend
            .create(handle, BackendCreateDesc::RenderPass(&desc))?;
        self.add_dependency(desc.target, handle);
        self.metrics.resource_creates += 1;
        self.render_passes.insert_at(
            handle,
            ResourceRecord {
                desc,
                token,
                last_submission: None,
            },
        )
    }

    pub fn destroy(&mut self, handle: Handle) -> GalResult<()> {
        self.ensure_no_dependents(handle)?;
        let pending = match handle.kind() {
            Some(HandleKind::Buffer) => self.remove_record(handle, HandleKind::Buffer)?,
            Some(HandleKind::Texture) => self.remove_record(handle, HandleKind::Texture)?,
            Some(HandleKind::TextureView) => self.remove_record(handle, HandleKind::TextureView)?,
            Some(HandleKind::Sampler) => self.remove_record(handle, HandleKind::Sampler)?,
            Some(HandleKind::ShaderModule) => {
                self.remove_record(handle, HandleKind::ShaderModule)?
            }
            Some(HandleKind::ResourceLayout) => {
                self.remove_record(handle, HandleKind::ResourceLayout)?
            }
            Some(HandleKind::ResourceSet) => self.remove_record(handle, HandleKind::ResourceSet)?,
            Some(HandleKind::PipelineLayout) => {
                self.remove_record(handle, HandleKind::PipelineLayout)?
            }
            Some(HandleKind::GraphicsPipeline) => {
                self.remove_record(handle, HandleKind::GraphicsPipeline)?
            }
            Some(HandleKind::ComputePipeline) => {
                self.remove_record(handle, HandleKind::ComputePipeline)?
            }
            Some(HandleKind::RenderTarget) => {
                self.remove_record(handle, HandleKind::RenderTarget)?
            }
            Some(HandleKind::FrameTarget) => self.remove_record(handle, HandleKind::FrameTarget)?,
            Some(HandleKind::RenderPass) => self.remove_record(handle, HandleKind::RenderPass)?,
            None => {
                return Err(GalError::handle(
                    StatusCode::WrongHandleType,
                    "unknown handle kind",
                ))
            }
        };
        self.remove_reverse_edges(handle);
        if let Some(last_submission) = pending.1 {
            if last_submission > self.completed_submission {
                self.retirement.defer(handle, last_submission);
                self.pending_destroys.insert(handle, pending.0);
                self.metrics.deferred_retires += 1;
                return Ok(());
            }
        }
        self.backend
            .destroy(handle, pending.0.kind, pending.0.token)?;
        self.metrics.resource_destroys += 1;
        Ok(())
    }

    pub fn create_command_list(&mut self, desc: CommandListDesc) -> GalResult<CommandList> {
        let capabilities = self.capabilities();
        if desc.operations.len() > capabilities.limits.max_commands_per_list as usize {
            return self.unsupported(format!(
                "command list '{}' operation count {} exceeds backend '{}' limit {}",
                desc.label,
                desc.operations.len(),
                capabilities.name,
                capabilities.limits.max_commands_per_list
            ));
        }
        self.validate_command_ops(&desc.operations)?;
        Ok(desc.into())
    }

    pub fn submit(&mut self, batch: SubmissionBatch) -> GalResult<SyncToken> {
        if batch.command_lists.is_empty() {
            return self.validation_error(GalError::submission(
                StatusCode::InvalidArgument,
                "submission batch must contain command lists",
            ));
        }
        let capabilities = self.capabilities();
        if batch.command_lists.len() > capabilities.limits.max_command_lists_per_submission as usize
        {
            return self.unsupported(format!(
                "submission '{}' command list count {} exceeds backend '{}' limit {}",
                batch.label,
                batch.command_lists.len(),
                capabilities.name,
                capabilities.limits.max_command_lists_per_submission
            ));
        }
        for list in &batch.command_lists {
            if list.operations.len() > capabilities.limits.max_commands_per_list as usize {
                return self.unsupported(format!(
                    "command list '{}' operation count {} exceeds backend '{}' limit {}",
                    list.label,
                    list.operations.len(),
                    capabilities.name,
                    capabilities.limits.max_commands_per_list
                ));
            }
            self.validate_command_ops(&list.operations)?;
        }
        let referenced = referenced_handles(&batch);
        for handle in &referenced {
            self.validate_any_resource(*handle)?;
        }
        self.validate_submission_hazards(&batch)?;
        let validated = ValidatedSubmissionBatch::from(batch);
        let id = SubmissionId(self.next_submission);
        self.next_submission += 1;
        self.backend.encode_passes(&validated)?;
        self.backend.submit(id, &validated)?;
        self.metrics.submissions += 1;
        for handle in referenced {
            self.mark_in_flight(handle, id)?;
        }
        Ok(SyncToken { submission: id })
    }

    pub fn retire_completed(&mut self) -> GalResult<Vec<Handle>> {
        self.poll_completed();
        self.backend.retire(self.completed_submission)?;
        let mut retired = Vec::new();
        for entry in self.retirement.drain_completed(self.completed_submission) {
            if let Some(pending) = self.pending_destroys.remove(&entry.handle) {
                self.backend
                    .destroy(entry.handle, pending.kind, pending.token)?;
                self.metrics.resource_destroys += 1;
                retired.push(entry.handle);
            }
        }
        Ok(retired)
    }

    pub(in crate::render::vulkanic) fn poll_completed(&mut self) -> SubmissionId {
        let completed = self.backend.completed_submission();
        if completed > self.completed_submission {
            self.completed_submission = completed;
        }
        self.completed_submission
    }

    pub(in crate::render::vulkanic) fn latest_submission_id(&self) -> SubmissionId {
        SubmissionId(self.next_submission.saturating_sub(1))
    }

    pub(in crate::render::vulkanic) fn retire_through(
        &mut self,
        id: SubmissionId,
    ) -> GalResult<Vec<Handle>> {
        self.backend.retire(id)?;
        if id > self.completed_submission {
            self.completed_submission = id;
        }
        let mut retired = Vec::new();
        for entry in self.retirement.drain_completed(self.completed_submission) {
            if let Some(pending) = self.pending_destroys.remove(&entry.handle) {
                self.backend
                    .destroy(entry.handle, pending.kind, pending.token)?;
                self.metrics.resource_destroys += 1;
                retired.push(entry.handle);
            }
        }
        Ok(retired)
    }

    pub(in crate::render::vulkanic) fn completed_host_reads(&self) -> Vec<CompletedHostRead> {
        self.backend.completed_host_reads()
    }

    fn validation_error<T>(&mut self, error: GalError) -> GalResult<T> {
        self.metrics.validation_failures += 1;
        Err(error)
    }

    fn unsupported<T>(&mut self, message: impl Into<String>) -> GalResult<T> {
        self.validation_error(GalError::unsupported_feature(message))
    }

    fn add_dependency(&mut self, resource: Handle, dependent: Handle) {
        self.dependencies
            .entry(resource)
            .or_default()
            .insert(dependent);
        self.reverse_dependencies
            .entry(dependent)
            .or_default()
            .insert(resource);
    }

    fn remove_reverse_edges(&mut self, dependent: Handle) {
        if let Some(resources) = self.reverse_dependencies.remove(&dependent) {
            for resource in resources {
                if let Some(dependents) = self.dependencies.get_mut(&resource) {
                    dependents.remove(&dependent);
                    if dependents.is_empty() {
                        self.dependencies.remove(&resource);
                    }
                }
            }
        }
    }

    fn ensure_no_dependents(&mut self, handle: Handle) -> GalResult<()> {
        if let Some(dependents) = self.dependencies.get(&handle) {
            if !dependents.is_empty() {
                return self.validation_error(GalError::resource(
                    StatusCode::DependencyViolation,
                    format!("resource 0x{:016x} has live dependents", handle.raw()),
                ));
            }
        }
        Ok(())
    }

    fn validate_binding_resource(&mut self, binding: &ResourceBinding) -> GalResult<()> {
        match binding.kind {
            ResourceBindingKind::UniformBuffer => {
                let record = self.buffers.get(binding.resource)?;
                if !record.desc.usages.contains(&BufferUsage::Uniform) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "uniform buffer binding requires uniform buffer usage",
                    ));
                }
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "uniform buffer binding cannot declare write access",
                    ));
                }
            }
            ResourceBindingKind::StorageBuffer => {
                let record = self.buffers.get(binding.resource)?;
                if !record.desc.usages.contains(&BufferUsage::Storage) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "storage buffer binding requires storage buffer usage",
                    ));
                }
            }
            ResourceBindingKind::SampledTexture => {
                let info = self.texture_view_info(binding.resource)?;
                if !info.usages.contains(&TextureUsage::Sampled) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "sampled texture binding requires sampled texture usage",
                    ));
                }
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "sampled texture binding cannot declare write access",
                    ));
                }
            }
            ResourceBindingKind::StorageTexture => {
                let info = self.texture_view_info(binding.resource)?;
                if !info.usages.contains(&TextureUsage::Storage) {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "storage texture binding requires storage texture usage",
                    ));
                }
            }
            ResourceBindingKind::Sampler => {
                self.samplers.get(binding.resource)?;
                if binding.access.writes() {
                    return self.validation_error(GalError::resource(
                        StatusCode::InvalidArgument,
                        "sampler binding cannot declare write access",
                    ));
                }
            }
        }
        Ok(())
    }

    fn require_shader_stage(&self, handle: Handle, stage: ShaderStage) -> GalResult<()> {
        let shader = self.shaders.get(handle)?;
        if shader.desc.stage != stage {
            return Err(GalError::resource(
                StatusCode::InvalidArgument,
                format!(
                    "shader stage mismatch: expected {stage:?}, got {:?}",
                    shader.desc.stage
                ),
            ));
        }
        Ok(())
    }

    fn validate_command_ops(&mut self, ops: &[CommandOp]) -> GalResult<()> {
        let capabilities = self.capabilities();
        let mut in_pass = false;
        let mut active_pass = None;
        let mut graphics_pipeline = None;
        let mut compute_pipeline = None;
        let mut active_pipeline_layout = None;
        let mut index_buffer = None;
        for op in ops {
            match op {
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors,
                    depth_stencil,
                } => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "nested passes are invalid",
                        ));
                    }
                    let pass_target = self.render_pass_desc(*pass)?.target;
                    let frame_target = match target.kind() {
                        Some(HandleKind::FrameTarget) => {
                            self.frame_targets.get(*target)?;
                            true
                        }
                        Some(HandleKind::RenderTarget) => false,
                        _ => {
                            return self.validation_error(GalError::command(
                                StatusCode::WrongHandleType,
                                "pass target must be a render target or frame target",
                            ))
                        }
                    };
                    let (expected_colors, expected_depth) = if frame_target {
                        (Vec::new(), None)
                    } else {
                        let target_record = self.render_targets.get(*target)?;
                        (
                            target_record.desc.color_views.clone(),
                            target_record.desc.depth_stencil_view,
                        )
                    };
                    if pass_target != *target {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass target does not match command target",
                        ));
                    }
                    if colors.len() != expected_colors.len() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass color attachment count does not match target",
                        ));
                    }
                    for (index, color) in colors.iter().enumerate() {
                        if color.view != expected_colors[index] {
                            return self.validation_error(GalError::command(
                                StatusCode::InvalidArgument,
                                "pass color attachment view does not match target",
                            ));
                        }
                    }
                    if depth_stencil.is_some() != expected_depth.is_some() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass depth attachment presence does not match target",
                        ));
                    }
                    if let (Some(depth), Some(expected_depth)) = (depth_stencil, expected_depth) {
                        if depth.view != expected_depth {
                            return self.validation_error(GalError::command(
                                StatusCode::InvalidArgument,
                                "pass depth attachment view does not match target",
                            ));
                        }
                    }
                    in_pass = true;
                    active_pass = Some(*pass);
                }
                CommandOp::BindGraphicsPipeline(handle) => {
                    let layout = self.graphics_pipelines.get(*handle)?.desc.layout;
                    if let Some(pass) = active_pass {
                        self.validate_pipeline_pass_compatibility(*handle, pass)?;
                    }
                    graphics_pipeline = Some(*handle);
                    compute_pipeline = None;
                    active_pipeline_layout = Some(layout);
                }
                CommandOp::BindComputePipeline(handle) => {
                    if in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "compute pipeline cannot be bound inside a render pass",
                        ));
                    }
                    let layout = self.compute_pipelines.get(*handle)?.desc.layout;
                    compute_pipeline = Some(*handle);
                    graphics_pipeline = None;
                    active_pipeline_layout = Some(layout);
                }
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index,
                    set,
                } => {
                    if active_pipeline_layout != Some(*pipeline_layout) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource set pipeline layout does not match active pipeline",
                        ));
                    }
                    let layout = self.pipeline_layouts.get(*pipeline_layout)?;
                    let set_record = self.resource_sets.get(*set)?;
                    let Some(expected_layout) =
                        layout.desc.resource_layouts.get(*set_index as usize)
                    else {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource set index is outside pipeline layout",
                        ));
                    };
                    if *expected_layout != set_record.desc.layout {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "resource set layout is not compatible with pipeline layout",
                        ));
                    }
                }
                CommandOp::SetVertexBuffer { buffer, .. } => {
                    let record = self.buffers.get(*buffer)?;
                    if !record.desc.usages.contains(&BufferUsage::Vertex) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "vertex buffer binding requires vertex buffer usage",
                        ));
                    }
                }
                CommandOp::SetIndexBuffer { buffer, .. } => {
                    let record = self.buffers.get(*buffer)?;
                    if !record.desc.usages.contains(&BufferUsage::Index) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "index buffer binding requires index buffer usage",
                        ));
                    }
                    index_buffer = Some(*buffer);
                }
                CommandOp::Draw {
                    vertices,
                    instances,
                } => {
                    if !in_pass || graphics_pipeline.is_none() || *vertices == 0 || *instances == 0
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "draw requires active pass, graphics pipeline, and non-zero counts",
                        ));
                    }
                }
                CommandOp::DrawIndexed { indices, instances } => {
                    if !in_pass
                        || graphics_pipeline.is_none()
                        || index_buffer.is_none()
                        || *indices == 0
                        || *instances == 0
                    {
                        return self.validation_error(GalError::command(StatusCode::InvalidArgument, "indexed draw requires active pass, pipeline, index buffer, and non-zero counts"));
                    }
                }
                CommandOp::DrawIndirect {
                    buffer,
                    offset,
                    draw_count,
                } => {
                    if !capabilities.supports(BackendFeature::IndirectDraw) {
                        return self.unsupported(format!(
                            "backend '{}' does not support indirect draw commands",
                            capabilities.name
                        ));
                    }
                    if !in_pass || graphics_pipeline.is_none() || *draw_count == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect draw requires active pass, graphics pipeline, and non-zero draw count",
                        ));
                    }
                    if *draw_count > capabilities.limits.max_draw_count {
                        return self.unsupported(format!(
                            "indirect draw count {} exceeds backend '{}' limit {}",
                            draw_count, capabilities.name, capabilities.limits.max_draw_count
                        ));
                    }
                    self.validate_buffer_range(*buffer, *offset, 1, BufferUsage::Indirect)?;
                }
                CommandOp::Dispatch {
                    groups_x,
                    groups_y,
                    groups_z,
                } => {
                    if !capabilities.supports(BackendFeature::Compute) {
                        return self.unsupported(format!(
                            "backend '{}' does not support dispatch commands",
                            capabilities.name
                        ));
                    }
                    if in_pass
                        || compute_pipeline.is_none()
                        || *groups_x == 0
                        || *groups_y == 0
                        || *groups_z == 0
                    {
                        return self.validation_error(GalError::command(StatusCode::InvalidArgument, "dispatch requires compute pipeline outside render pass and non-zero groups"));
                    }
                    if *groups_x > capabilities.limits.max_dispatch_groups_per_axis
                        || *groups_y > capabilities.limits.max_dispatch_groups_per_axis
                        || *groups_z > capabilities.limits.max_dispatch_groups_per_axis
                    {
                        return self.unsupported(format!(
                            "dispatch group count exceeds backend '{}' limit {}",
                            capabilities.name, capabilities.limits.max_dispatch_groups_per_axis
                        ));
                    }
                }
                CommandOp::DispatchIndirect { buffer, offset } => {
                    if !capabilities.supports(BackendFeature::IndirectDispatch) {
                        return self.unsupported(format!(
                            "backend '{}' does not support indirect dispatch commands",
                            capabilities.name
                        ));
                    }
                    if in_pass || compute_pipeline.is_none() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "indirect dispatch requires compute pipeline outside render pass",
                        ));
                    }
                    self.validate_buffer_range(*buffer, *offset, 1, BufferUsage::Indirect)?;
                }
                CommandOp::CopyBuffer { src, dst, size } => {
                    let src_record = self.buffers.get(*src)?;
                    let src_ok = src_record.desc.usages.contains(&BufferUsage::TransferSrc);
                    let dst_record = self.buffers.get(*dst)?;
                    let dst_ok = dst_record.desc.usages.contains(&BufferUsage::TransferDst);
                    if src == dst || *size == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer copy requires distinct buffers and non-zero size",
                        ));
                    }
                    if !src_ok || !dst_ok {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer copy requires transfer source and destination usages",
                        ));
                    }
                }
                CommandOp::CopyBufferToTexture(region) => {
                    if (region.texture_mip > 0 || region.texture_layer > 0)
                        && !capabilities.supports(BackendFeature::TextureSubresourceCopies)
                    {
                        return self.unsupported(format!(
                            "backend '{}' does not support texture subresource copies",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_texture_copy_region(
                        region,
                        BufferUsage::TransferSrc,
                        TextureUsage::TransferDst,
                    )?;
                }
                CommandOp::CopyTextureToBuffer(region) => {
                    if (region.texture_mip > 0 || region.texture_layer > 0)
                        && !capabilities.supports(BackendFeature::TextureSubresourceCopies)
                    {
                        return self.unsupported(format!(
                            "backend '{}' does not support texture subresource copies",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_texture_copy_region(
                        region,
                        BufferUsage::TransferDst,
                        TextureUsage::TransferSrc,
                    )?;
                }
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset,
                    data,
                } => {
                    if !capabilities.supports(BackendFeature::HostBufferAccess) {
                        return self.unsupported(format!(
                            "backend '{}' does not support host buffer writes",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_range(
                        *buffer,
                        *offset,
                        data.len() as u64,
                        BufferUsage::HostWrite,
                    )?;
                    let record = self.buffers.get(*buffer)?;
                    if record.desc.memory != MemoryDomain::Upload {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "host writes require upload memory",
                        ));
                    }
                }
                CommandOp::HostReadBuffer {
                    buffer,
                    offset,
                    size,
                } => {
                    if !capabilities.supports(BackendFeature::HostBufferAccess) {
                        return self.unsupported(format!(
                            "backend '{}' does not support host buffer reads",
                            capabilities.name
                        ));
                    }
                    self.validate_buffer_range(*buffer, *offset, *size, BufferUsage::HostRead)?;
                    let record = self.buffers.get(*buffer)?;
                    if record.desc.memory != MemoryDomain::Readback {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "host reads require readback memory",
                        ));
                    }
                }
                CommandOp::Present {
                    texture,
                    subresources,
                } => {
                    if !capabilities.supports(BackendFeature::Presentation) {
                        return self.unsupported(format!(
                            "backend '{}' does not support presentation commands in the isolated path",
                            capabilities.name
                        ));
                    }
                    let record = self.textures.get(*texture)?;
                    if !record.desc.usages.contains(&TextureUsage::Present) {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "presentation requires present texture usage",
                        ));
                    }
                    self.validate_texture_subresource_range(*texture, *subresources)?;
                }
                CommandOp::Barrier(barrier) => {
                    self.validate_any_resource(barrier.resource)?;
                    if let Some(range) = barrier.subresources {
                        match barrier.resource.kind() {
                            Some(HandleKind::Texture) => {
                                self.validate_texture_subresource_range(barrier.resource, range)?;
                            }
                            Some(HandleKind::TextureView) => {
                                let info = self.texture_view_info(barrier.resource)?;
                                self.validate_texture_subresource_range(info.texture, range)?;
                                if !texture_range_contains(info.range, range) {
                                    return self.validation_error(GalError::command(
                                        StatusCode::InvalidArgument,
                                        "barrier subresource range is outside the texture view",
                                    ));
                                }
                            }
                            _ => {
                                return self.validation_error(GalError::command(
                                    StatusCode::InvalidArgument,
                                    "barrier subresource ranges are only valid for textures",
                                ));
                            }
                        }
                    }
                    if barrier.access == AccessFlags::NONE
                        || barrier.stages == PipelineStageFlags::NONE
                        || barrier.before == barrier.after
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "barrier must declare access, stages, and a state change",
                        ));
                    }
                    if barrier.src_queue == QueueClass::Present
                        || barrier.dst_queue == QueueClass::Present
                    {
                        let texture = self.textures.get(barrier.resource)?;
                        if !texture.desc.usages.contains(&TextureUsage::Present) {
                            return self.validation_error(GalError::command(
                                StatusCode::InvalidArgument,
                                "queue ownership transfer involving presentation requires present texture usage",
                            ));
                        }
                    }
                }
                CommandOp::EndPass => {
                    if !in_pass {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "EndPass without BeginPass",
                        ));
                    }
                    in_pass = false;
                    active_pass = None;
                    graphics_pipeline = None;
                    active_pipeline_layout = None;
                    index_buffer = None;
                }
            }
        }
        if in_pass {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "command list ended inside a pass",
            ));
        }
        Ok(())
    }

    fn validate_pipeline_pass_compatibility(
        &mut self,
        pipeline: Handle,
        pass: Handle,
    ) -> GalResult<()> {
        let compatible = {
            let pipeline_record = self.graphics_pipelines.get(pipeline)?;
            let pass_record = self.render_passes.get(pass)?;
            pipeline_record.desc.color_formats == pass_record.desc.color_formats
                && pipeline_record.desc.depth_format == pass_record.desc.depth_format
        };
        if !compatible {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "graphics pipeline attachment formats are not compatible with render pass",
            ));
        }
        Ok(())
    }

    fn validate_any_resource(&self, handle: Handle) -> GalResult<()> {
        match handle.kind() {
            Some(HandleKind::Buffer) => self.buffers.get(handle).map(|_| ()),
            Some(HandleKind::Texture) => self.textures.get(handle).map(|_| ()),
            Some(HandleKind::TextureView) => self.texture_views.get(handle).map(|_| ()),
            Some(HandleKind::Sampler) => self.samplers.get(handle).map(|_| ()),
            Some(HandleKind::ShaderModule) => self.shaders.get(handle).map(|_| ()),
            Some(HandleKind::ResourceLayout) => self.resource_layouts.get(handle).map(|_| ()),
            Some(HandleKind::ResourceSet) => self.resource_sets.get(handle).map(|_| ()),
            Some(HandleKind::PipelineLayout) => self.pipeline_layouts.get(handle).map(|_| ()),
            Some(HandleKind::GraphicsPipeline) => self.graphics_pipelines.get(handle).map(|_| ()),
            Some(HandleKind::ComputePipeline) => self.compute_pipelines.get(handle).map(|_| ()),
            Some(HandleKind::RenderTarget) => self.render_targets.get(handle).map(|_| ()),
            Some(HandleKind::FrameTarget) => self.frame_targets.get(handle).map(|_| ()),
            Some(HandleKind::RenderPass) => self.render_passes.get(handle).map(|_| ()),
            None => Err(GalError::handle(
                StatusCode::WrongHandleType,
                "unknown handle kind",
            )),
        }
    }

    fn render_pass_desc(&self, pass: Handle) -> GalResult<&RenderPassDesc> {
        self.render_passes.get(pass).map(|record| &record.desc)
    }

    fn validate_submission_hazards(&mut self, batch: &SubmissionBatch) -> GalResult<()> {
        let mut accesses = Vec::new();
        for list in &batch.command_lists {
            for op in &list.operations {
                match op {
                    CommandOp::BeginPass {
                        colors,
                        depth_stencil,
                        ..
                    } => {
                        for color in colors {
                            let info = self.texture_view_info(color.view)?;
                            let event = AccessEvent {
                                target: AccessTarget::Texture {
                                    texture: info.texture,
                                    range: info.range,
                                },
                                mode: AccessMode::Write,
                                family: AccessFamily::Attachment,
                            };
                            self.record_access(&mut accesses, event)?;
                        }
                        if let Some(depth) = depth_stencil {
                            let info = self.texture_view_info(depth.view)?;
                            let event = AccessEvent {
                                target: AccessTarget::Texture {
                                    texture: info.texture,
                                    range: info.range,
                                },
                                mode: AccessMode::Write,
                                family: AccessFamily::Attachment,
                            };
                            self.record_access(&mut accesses, event)?;
                        }
                    }
                    CommandOp::BindResourceSet { set, .. } => {
                        let bindings = self.resource_sets.get(*set)?.desc.bindings.clone();
                        for binding in &bindings {
                            let event = self.resource_binding_access(binding)?;
                            self.record_access(&mut accesses, event)?;
                        }
                    }
                    CommandOp::SetVertexBuffer { buffer, offset, .. } => {
                        let target = self.buffer_access_target(*buffer, *offset, None)?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Vertex,
                            },
                        )?;
                    }
                    CommandOp::SetIndexBuffer { buffer, offset } => {
                        let target = self.buffer_access_target(*buffer, *offset, None)?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Index,
                            },
                        )?;
                    }
                    CommandOp::DrawIndirect {
                        buffer,
                        offset,
                        draw_count: _,
                    }
                    | CommandOp::DispatchIndirect { buffer, offset } => {
                        let target = self.buffer_access_target(*buffer, *offset, None)?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Indirect,
                            },
                        )?;
                    }
                    CommandOp::CopyBuffer { src, dst, size } => {
                        let src_target = self.buffer_access_target(*src, 0, Some(*size))?;
                        let dst_target = self.buffer_access_target(*dst, 0, Some(*size))?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: src_target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                            },
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: dst_target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                            },
                        )?;
                    }
                    CommandOp::CopyBufferToTexture(region) => {
                        let buffer_target = self.buffer_access_target(
                            region.buffer,
                            region.buffer_offset,
                            Some(self.buffer_texture_copy_size(region)?),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: buffer_target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                            },
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_copy_target(region)?,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                            },
                        )?;
                    }
                    CommandOp::CopyTextureToBuffer(region) => {
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: self.texture_copy_target(region)?,
                                mode: AccessMode::Read,
                                family: AccessFamily::Transfer,
                            },
                        )?;
                        let buffer_target = self.buffer_access_target(
                            region.buffer,
                            region.buffer_offset,
                            Some(self.buffer_texture_copy_size(region)?),
                        )?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: buffer_target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Transfer,
                            },
                        )?;
                    }
                    CommandOp::HostWriteBuffer {
                        buffer,
                        offset,
                        data,
                    } => {
                        let target =
                            self.buffer_access_target(*buffer, *offset, Some(data.len() as u64))?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Write,
                                family: AccessFamily::Host,
                            },
                        )?;
                    }
                    CommandOp::HostReadBuffer {
                        buffer,
                        offset,
                        size,
                    } => {
                        let target = self.buffer_access_target(*buffer, *offset, Some(*size))?;
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target,
                                mode: AccessMode::Read,
                                family: AccessFamily::Host,
                            },
                        )?;
                    }
                    CommandOp::Present {
                        texture,
                        subresources,
                    } => {
                        self.record_access(
                            &mut accesses,
                            AccessEvent {
                                target: AccessTarget::Texture {
                                    texture: *texture,
                                    range: *subresources,
                                },
                                mode: AccessMode::Read,
                                family: AccessFamily::Present,
                            },
                        )?;
                    }
                    CommandOp::Barrier(barrier) => {
                        let barrier_target = self.barrier_target(barrier)?;
                        accesses.retain(|access| !targets_overlap(access.target, barrier_target));
                    }
                    CommandOp::BindGraphicsPipeline(_)
                    | CommandOp::BindComputePipeline(_)
                    | CommandOp::Draw { .. }
                    | CommandOp::DrawIndexed { .. }
                    | CommandOp::Dispatch { .. }
                    | CommandOp::EndPass => {}
                }
            }
        }
        Ok(())
    }

    fn resource_binding_access(&self, binding: &ResourceBinding) -> GalResult<AccessEvent> {
        let mode = if binding.access.writes() {
            AccessMode::Write
        } else {
            AccessMode::Read
        };
        match binding.kind {
            ResourceBindingKind::UniformBuffer => Ok(AccessEvent {
                target: self.buffer_access_target(binding.resource, 0, None)?,
                mode: AccessMode::Read,
                family: AccessFamily::Uniform,
            }),
            ResourceBindingKind::StorageBuffer => Ok(AccessEvent {
                target: self.buffer_access_target(binding.resource, 0, None)?,
                mode,
                family: AccessFamily::Storage,
            }),
            ResourceBindingKind::SampledTexture => {
                let info = self.texture_view_info(binding.resource)?;
                Ok(AccessEvent {
                    target: AccessTarget::Texture {
                        texture: info.texture,
                        range: info.range,
                    },
                    mode: AccessMode::Read,
                    family: AccessFamily::Sampled,
                })
            }
            ResourceBindingKind::StorageTexture => {
                let info = self.texture_view_info(binding.resource)?;
                Ok(AccessEvent {
                    target: AccessTarget::Texture {
                        texture: info.texture,
                        range: info.range,
                    },
                    mode,
                    family: AccessFamily::Storage,
                })
            }
            ResourceBindingKind::Sampler => Ok(AccessEvent {
                target: AccessTarget::Buffer {
                    handle: binding.resource,
                    offset: 0,
                    size: 0,
                },
                mode: AccessMode::Read,
                family: AccessFamily::Sampled,
            }),
        }
    }

    fn record_access(
        &mut self,
        accesses: &mut Vec<AccessEvent>,
        event: AccessEvent,
    ) -> GalResult<()> {
        if event.target.is_zero_sized_sampler_marker() {
            return Ok(());
        }
        for previous in accesses.iter().copied() {
            if targets_overlap(previous.target, event.target)
                && (previous.mode == AccessMode::Write || event.mode == AccessMode::Write)
            {
                return self.validation_error(GalError::submission(
                    StatusCode::InvalidArgument,
                    format!(
                        "overlapping {:?} {:?} access conflicts with prior {:?} {:?} access",
                        event.family, event.mode, previous.family, previous.mode
                    ),
                ));
            }
        }
        accesses.push(event);
        Ok(())
    }

    fn buffer_access_target(
        &self,
        buffer: Handle,
        offset: u64,
        size: Option<u64>,
    ) -> GalResult<AccessTarget> {
        let record = self.buffers.get(buffer)?;
        let size = size.unwrap_or_else(|| record.desc.size.saturating_sub(offset));
        Ok(AccessTarget::Buffer {
            handle: buffer,
            offset,
            size,
        })
    }

    fn barrier_target(&self, barrier: &ResourceBarrier) -> GalResult<AccessTarget> {
        match barrier.resource.kind() {
            Some(HandleKind::Buffer) => self.buffer_access_target(barrier.resource, 0, None),
            Some(HandleKind::Texture) => {
                let record = self.textures.get(barrier.resource)?;
                Ok(AccessTarget::Texture {
                    texture: barrier.resource,
                    range: barrier.subresources.unwrap_or(TextureSubresourceRange {
                        base_mip: 0,
                        mip_count: record.desc.mip_levels,
                        base_layer: 0,
                        layer_count: record.desc.array_layers,
                    }),
                })
            }
            Some(HandleKind::TextureView) => {
                let info = self.texture_view_info(barrier.resource)?;
                Ok(AccessTarget::Texture {
                    texture: info.texture,
                    range: barrier.subresources.unwrap_or(info.range),
                })
            }
            _ => Err(GalError::command(
                StatusCode::InvalidArgument,
                "barrier resource must be a buffer, texture, or texture view",
            )),
        }
    }

    fn texture_view_info(&self, view: Handle) -> GalResult<TextureViewInfo> {
        let view_record = self.texture_views.get(view)?;
        let texture_record = self.textures.get(view_record.desc.texture)?;
        Ok(TextureViewInfo {
            texture: view_record.desc.texture,
            format: view_record.desc.format,
            extent: texture_record.desc.extent,
            range: TextureSubresourceRange {
                base_mip: view_record.desc.base_mip,
                mip_count: view_record.desc.mip_count,
                base_layer: view_record.desc.base_layer,
                layer_count: view_record.desc.layer_count,
            },
            usages: texture_record.desc.usages.clone(),
        })
    }

    fn validate_buffer_range(
        &mut self,
        buffer: Handle,
        offset: u64,
        size: u64,
        usage: BufferUsage,
    ) -> GalResult<()> {
        let record = self.buffers.get(buffer)?;
        let has_usage = record.desc.usages.contains(&usage);
        let buffer_size = record.desc.size;
        if size == 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "buffer range size must be non-zero",
            ));
        }
        let Some(end) = offset.checked_add(size) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "buffer range overflows",
            ));
        };
        if end > buffer_size {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "buffer range is outside the buffer",
            ));
        }
        if !has_usage {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                format!("buffer range requires {usage:?} usage"),
            ));
        }
        Ok(())
    }

    fn validate_buffer_texture_copy_region(
        &mut self,
        region: &BufferImageCopyRegion,
        buffer_usage: BufferUsage,
        texture_usage: TextureUsage,
    ) -> GalResult<()> {
        let size = self.buffer_texture_copy_size(region)?;
        self.validate_buffer_range(region.buffer, region.buffer_offset, size, buffer_usage)?;
        let (texture_usages, texture_extent) = {
            let texture = self.textures.get(region.texture)?;
            (texture.desc.usages.clone(), texture.desc.extent)
        };
        if !texture_usages.contains(&texture_usage) {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                format!("texture copy requires {texture_usage:?} usage"),
            ));
        }
        if region.extent.width == 0 || region.extent.height == 0 || region.extent.depth == 0 {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy extent must be non-zero",
            ));
        }
        if region.bytes_per_row == 0 || region.rows_per_image == 0 || region.bytes_per_row % 4 != 0
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy row layout is malformed",
            ));
        }
        self.validate_texture_subresource_range(
            region.texture,
            TextureSubresourceRange {
                base_mip: region.texture_mip,
                mip_count: 1,
                base_layer: region.texture_layer,
                layer_count: 1,
            },
        )?;
        let Some(x_end) = region.texture_origin.x.checked_add(region.extent.width) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy x range overflows",
            ));
        };
        let Some(y_end) = region.texture_origin.y.checked_add(region.extent.height) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy y range overflows",
            ));
        };
        let Some(z_end) = region.texture_origin.z.checked_add(region.extent.depth) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy z range overflows",
            ));
        };
        if x_end > texture_extent.width
            || y_end > texture_extent.height
            || z_end > texture_extent.depth
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy region is outside texture extent",
            ));
        }
        Ok(())
    }

    fn buffer_texture_copy_size(&self, region: &BufferImageCopyRegion) -> GalResult<u64> {
        if region.extent.width == 0 || region.extent.height == 0 || region.extent.depth == 0 {
            return Err(GalError::command(
                StatusCode::InvalidArgument,
                "texture copy extent must be non-zero",
            ));
        }
        let bytes_per_row = u64::from(region.bytes_per_row);
        let rows_per_image = u64::from(region.rows_per_image);
        let layers = u64::from(region.extent.depth);
        rows_per_image
            .checked_mul(layers.saturating_sub(1))
            .and_then(|rows_before_last| {
                rows_before_last.checked_add(u64::from(region.extent.height))
            })
            .and_then(|rows| rows.checked_mul(bytes_per_row))
            .ok_or_else(|| {
                GalError::command(
                    StatusCode::InvalidArgument,
                    "texture copy buffer size overflows",
                )
            })
    }

    fn texture_copy_target(&self, region: &BufferImageCopyRegion) -> GalResult<AccessTarget> {
        self.textures.get(region.texture)?;
        Ok(AccessTarget::Texture {
            texture: region.texture,
            range: TextureSubresourceRange {
                base_mip: region.texture_mip,
                mip_count: 1,
                base_layer: region.texture_layer,
                layer_count: 1,
            },
        })
    }

    fn validate_texture_subresource_range(
        &mut self,
        texture: Handle,
        range: TextureSubresourceRange,
    ) -> GalResult<()> {
        let record = self.textures.get(texture)?;
        let mip_levels = record.desc.mip_levels;
        let array_layers = record.desc.array_layers;
        let Some(mip_end) = range.base_mip.checked_add(range.mip_count) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture subresource mip range overflows",
            ));
        };
        let Some(layer_end) = range.base_layer.checked_add(range.layer_count) else {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture subresource layer range overflows",
            ));
        };
        if range.mip_count == 0
            || range.layer_count == 0
            || mip_end > mip_levels
            || layer_end > array_layers
        {
            return self.validation_error(GalError::command(
                StatusCode::InvalidArgument,
                "texture subresource range is outside the texture",
            ));
        }
        Ok(())
    }

    fn mark_in_flight(&mut self, handle: Handle, id: SubmissionId) -> GalResult<()> {
        match handle.kind() {
            Some(HandleKind::Buffer) => {
                self.buffers.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::Texture) => {
                self.textures.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::TextureView) => {
                self.texture_views.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::Sampler) => {
                self.samplers.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::ShaderModule) => {
                self.shaders.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::ResourceLayout) => {
                self.resource_layouts
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::ResourceSet) => {
                self.resource_sets.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::PipelineLayout) => {
                self.pipeline_layouts
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::GraphicsPipeline) => {
                self.graphics_pipelines
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::ComputePipeline) => {
                self.compute_pipelines
                    .get_mut_record(handle)?
                    .last_submission = Some(id)
            }
            Some(HandleKind::RenderTarget) => {
                self.render_targets.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::FrameTarget) => {
                self.frame_targets.get_mut_record(handle)?.last_submission = Some(id)
            }
            Some(HandleKind::RenderPass) => {
                self.render_passes.get_mut_record(handle)?.last_submission = Some(id)
            }
            None => {
                return Err(GalError::handle(
                    StatusCode::WrongHandleType,
                    "unknown handle kind",
                ))
            }
        }
        Ok(())
    }

    fn remove_record(
        &mut self,
        handle: Handle,
        kind: HandleKind,
    ) -> GalResult<(PendingDestroy, Option<SubmissionId>)> {
        macro_rules! remove_from {
            ($arena:expr) => {{
                let record = $arena.remove(handle)?;
                (
                    PendingDestroy {
                        kind,
                        token: record.token,
                    },
                    record.last_submission,
                )
            }};
        }
        Ok(match kind {
            HandleKind::Buffer => remove_from!(self.buffers),
            HandleKind::Texture => remove_from!(self.textures),
            HandleKind::TextureView => remove_from!(self.texture_views),
            HandleKind::Sampler => remove_from!(self.samplers),
            HandleKind::ShaderModule => remove_from!(self.shaders),
            HandleKind::ResourceLayout => remove_from!(self.resource_layouts),
            HandleKind::ResourceSet => remove_from!(self.resource_sets),
            HandleKind::PipelineLayout => remove_from!(self.pipeline_layouts),
            HandleKind::GraphicsPipeline => remove_from!(self.graphics_pipelines),
            HandleKind::ComputePipeline => remove_from!(self.compute_pipelines),
            HandleKind::RenderTarget => remove_from!(self.render_targets),
            HandleKind::FrameTarget => remove_from!(self.frame_targets),
            HandleKind::RenderPass => remove_from!(self.render_passes),
        })
    }

    #[cfg(test)]
    pub(super) fn force_buffer_generation_for_test(&mut self, handle: Handle, generation: u32) {
        self.buffers.force_generation(handle, generation);
    }

    #[cfg(test)]
    pub(super) fn mock_backend(&self) -> Option<&super::backends::mock::MockBackend> {
        self.backend.as_any().downcast_ref()
    }

    #[cfg(test)]
    pub(super) fn mock_backend_mut(&mut self) -> Option<&mut super::backends::mock::MockBackend> {
        self.backend.as_any_mut().downcast_mut()
    }

    #[cfg(test)]
    pub(super) fn vulkan_backend(&self) -> Option<&super::backends::vulkan::VulkanBackend> {
        self.backend.as_any().downcast_ref()
    }

    #[cfg(test)]
    pub(super) fn vulkan_backend_mut(
        &mut self,
    ) -> Option<&mut super::backends::vulkan::VulkanBackend> {
        self.backend.as_any_mut().downcast_mut()
    }

    #[cfg(test)]
    pub(super) fn opengl_backend(&self) -> Option<&super::backends::opengl::OpenGlBackend> {
        self.backend.as_any().downcast_ref()
    }

    #[cfg(test)]
    pub(super) fn retire_through_for_test(&mut self, id: SubmissionId) -> GalResult<Vec<Handle>> {
        self.retire_through(id)
    }
}

trait ArenaRecordExt<T> {
    fn get_mut_record(&mut self, handle: Handle) -> GalResult<&mut ResourceRecord<T>>;
}

impl<T> ArenaRecordExt<T> for Arena<ResourceRecord<T>> {
    fn get_mut_record(&mut self, handle: Handle) -> GalResult<&mut ResourceRecord<T>> {
        let (index, generation) = handle.require_kind(self.kind)?;
        let Some(slot) = self.slots.get_mut(index) else {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "handle slot does not exist",
            ));
        };
        if slot.generation != generation {
            return Err(GalError::handle(
                StatusCode::StaleHandle,
                "stale handle generation",
            ));
        }
        slot.value
            .as_mut()
            .ok_or_else(|| GalError::handle(StatusCode::StaleHandle, "resource is not live"))
    }
}

fn referenced_handles(batch: &SubmissionBatch) -> BTreeSet<Handle> {
    let mut handles = BTreeSet::new();
    for list in &batch.command_lists {
        for op in &list.operations {
            match op {
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors,
                    depth_stencil,
                } => {
                    handles.insert(*pass);
                    handles.insert(*target);
                    for color in colors {
                        handles.insert(color.view);
                    }
                    if let Some(depth) = depth_stencil {
                        handles.insert(depth.view);
                    }
                }
                CommandOp::BindGraphicsPipeline(handle)
                | CommandOp::BindComputePipeline(handle)
                | CommandOp::DrawIndirect { buffer: handle, .. }
                | CommandOp::DispatchIndirect { buffer: handle, .. }
                | CommandOp::SetIndexBuffer { buffer: handle, .. }
                | CommandOp::HostWriteBuffer { buffer: handle, .. }
                | CommandOp::HostReadBuffer { buffer: handle, .. }
                | CommandOp::Barrier(super::commands::ResourceBarrier {
                    resource: handle, ..
                }) => {
                    handles.insert(*handle);
                }
                CommandOp::CopyBufferToTexture(region) | CommandOp::CopyTextureToBuffer(region) => {
                    handles.insert(region.buffer);
                    handles.insert(region.texture);
                }
                CommandOp::Present {
                    texture: handle, ..
                } => {
                    handles.insert(*handle);
                }
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set,
                    ..
                } => {
                    handles.insert(*pipeline_layout);
                    handles.insert(*set);
                }
                CommandOp::SetVertexBuffer { buffer, .. } => {
                    handles.insert(*buffer);
                }
                CommandOp::CopyBuffer { src, dst, .. } => {
                    handles.insert(*src);
                    handles.insert(*dst);
                }
                CommandOp::Draw { .. }
                | CommandOp::DrawIndexed { .. }
                | CommandOp::Dispatch { .. }
                | CommandOp::EndPass => {}
            }
        }
    }
    handles
}

fn is_depth_stencil_format(format: TextureFormat) -> bool {
    matches!(
        format,
        TextureFormat::Depth24Stencil8 | TextureFormat::Depth32Float
    )
}

impl AccessTarget {
    fn is_zero_sized_sampler_marker(self) -> bool {
        matches!(self, AccessTarget::Buffer { size: 0, .. })
    }
}

fn targets_overlap(left: AccessTarget, right: AccessTarget) -> bool {
    match (left, right) {
        (
            AccessTarget::Buffer {
                handle: left_handle,
                offset: left_offset,
                size: left_size,
            },
            AccessTarget::Buffer {
                handle: right_handle,
                offset: right_offset,
                size: right_size,
            },
        ) => {
            if left_handle != right_handle || left_size == 0 || right_size == 0 {
                return false;
            }
            ranges_overlap(left_offset, left_size, right_offset, right_size)
        }
        (
            AccessTarget::Texture {
                texture: left_texture,
                range: left_range,
            },
            AccessTarget::Texture {
                texture: right_texture,
                range: right_range,
            },
        ) => left_texture == right_texture && texture_ranges_overlap(left_range, right_range),
        _ => false,
    }
}

fn ranges_overlap(left_offset: u64, left_size: u64, right_offset: u64, right_size: u64) -> bool {
    let left_end = left_offset.saturating_add(left_size);
    let right_end = right_offset.saturating_add(right_size);
    left_offset < right_end && right_offset < left_end
}

fn texture_ranges_overlap(left: TextureSubresourceRange, right: TextureSubresourceRange) -> bool {
    ranges_overlap(
        left.base_mip as u64,
        left.mip_count as u64,
        right.base_mip as u64,
        right.mip_count as u64,
    ) && ranges_overlap(
        left.base_layer as u64,
        left.layer_count as u64,
        right.base_layer as u64,
        right.layer_count as u64,
    )
}

fn texture_range_contains(outer: TextureSubresourceRange, inner: TextureSubresourceRange) -> bool {
    let outer_mip_end = outer.base_mip.saturating_add(outer.mip_count);
    let inner_mip_end = inner.base_mip.saturating_add(inner.mip_count);
    let outer_layer_end = outer.base_layer.saturating_add(outer.layer_count);
    let inner_layer_end = inner.base_layer.saturating_add(inner.layer_count);
    outer.base_mip <= inner.base_mip
        && inner_mip_end <= outer_mip_end
        && outer.base_layer <= inner.base_layer
        && inner_layer_end <= outer_layer_end
}

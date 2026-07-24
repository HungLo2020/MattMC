use std::collections::{BTreeMap, BTreeSet};

use super::backends::{Backend, BackendCreateDesc, BackendToken};
use super::commands::{CommandList, CommandListDesc, CommandOp, SubmissionBatch};
use super::error::{GalError, GalResult, StatusCode};
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
                return Handle::new(self.kind, index as u32, slot.generation);
            }
        }
        Handle::new(self.kind, self.slots.len() as u32, 1)
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
    pub(super) fn new_with_backend(backend: Box<dyn Backend>, tracy_enabled: bool) -> Self {
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

    pub fn create_buffer(&mut self, desc: BufferDesc) -> GalResult<Handle> {
        if desc.size == 0 || desc.usages.is_empty() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "buffer size and usages must be non-empty",
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
        let (texture_mip_levels, texture_array_layers) = {
            let texture = self.textures.get(desc.texture)?;
            (texture.desc.mip_levels, texture.desc.array_layers)
        };
        if desc.mip_count == 0
            || desc.layer_count == 0
            || desc.base_mip + desc.mip_count > texture_mip_levels
            || desc.base_layer + desc.layer_count > texture_array_layers
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "texture view range is outside the texture",
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
        let mut seen = BTreeSet::new();
        for binding in &desc.bindings {
            if !seen.insert(binding.binding) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("duplicate resource binding {}", binding.binding),
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
        for binding in &desc.bindings {
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
            if expected.kind != binding.kind {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    format!("binding {} kind mismatch", binding.binding),
                ));
            }
            self.validate_binding_resource(binding.kind, binding.resource)?;
            if !binding.access.reads() && !binding.access.writes() {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "resource binding must declare read or write access",
                ));
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
        for view in &desc.color_views {
            self.texture_views.get(*view)?;
        }
        if let Some(view) = desc.depth_stencil_view {
            self.texture_views.get(view)?;
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
        let (target_color_count, target_has_depth) = {
            let target = self.render_targets.get(desc.target)?;
            (
                target.desc.color_views.len(),
                target.desc.depth_stencil_view.is_some(),
            )
        };
        if desc.color_formats.len() != target_color_count {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render pass color format count does not match target",
            ));
        }
        if desc.depth_format.is_some() != target_has_depth {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "render pass depth format presence does not match target",
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
        for list in &batch.command_lists {
            self.validate_command_ops(&list.operations)?;
        }
        let id = SubmissionId(self.next_submission);
        self.next_submission += 1;
        self.backend.encode_passes(&batch)?;
        self.backend.submit(id, &batch)?;
        self.metrics.submissions += 1;
        for handle in referenced_handles(&batch) {
            self.mark_in_flight(handle, id)?;
        }
        Ok(SyncToken { submission: id })
    }

    pub fn retire_completed(&mut self) -> GalResult<Vec<Handle>> {
        let completed = self.backend.completed_submission();
        if completed > self.completed_submission {
            self.completed_submission = completed;
        }
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

    fn validation_error<T>(&mut self, error: GalError) -> GalResult<T> {
        self.metrics.validation_failures += 1;
        Err(error)
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

    fn validate_binding_resource(
        &self,
        kind: ResourceBindingKind,
        resource: Handle,
    ) -> GalResult<()> {
        match kind {
            ResourceBindingKind::UniformBuffer | ResourceBindingKind::StorageBuffer => {
                self.buffers.get(resource)?;
            }
            ResourceBindingKind::SampledTexture | ResourceBindingKind::StorageTexture => {
                self.texture_views.get(resource)?;
            }
            ResourceBindingKind::Sampler => {
                self.samplers.get(resource)?;
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
        let mut in_pass = false;
        let mut active_pass = None;
        let mut graphics_pipeline = None;
        let mut compute_pipeline = None;
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
                    let pass_record = self.render_pass_desc(*pass)?;
                    let target_record = self.render_targets.get(*target)?;
                    if pass_record.target != *target {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass target does not match command target",
                        ));
                    }
                    if colors.len() != target_record.desc.color_views.len() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass color attachment count does not match target",
                        ));
                    }
                    if depth_stencil.is_some() != target_record.desc.depth_stencil_view.is_some() {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "pass depth attachment presence does not match target",
                        ));
                    }
                    in_pass = true;
                    active_pass = Some(*pass);
                }
                CommandOp::BindGraphicsPipeline(handle) => {
                    self.graphics_pipelines.get(*handle)?;
                    if let Some(pass) = active_pass {
                        self.validate_pipeline_pass_compatibility(*handle, pass)?;
                    }
                    graphics_pipeline = Some(*handle);
                }
                CommandOp::BindComputePipeline(handle) => {
                    self.compute_pipelines.get(*handle)?;
                    compute_pipeline = Some(*handle);
                }
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index,
                    set,
                } => {
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
                    self.buffers.get(*buffer)?;
                }
                CommandOp::SetIndexBuffer { buffer, .. } => {
                    self.buffers.get(*buffer)?;
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
                CommandOp::Dispatch {
                    groups_x,
                    groups_y,
                    groups_z,
                } => {
                    if in_pass
                        || compute_pipeline.is_none()
                        || *groups_x == 0
                        || *groups_y == 0
                        || *groups_z == 0
                    {
                        return self.validation_error(GalError::command(StatusCode::InvalidArgument, "dispatch requires compute pipeline outside render pass and non-zero groups"));
                    }
                }
                CommandOp::CopyBuffer { src, dst, size } => {
                    self.buffers.get(*src)?;
                    self.buffers.get(*dst)?;
                    if src == dst || *size == 0 {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "buffer copy requires distinct buffers and non-zero size",
                        ));
                    }
                }
                CommandOp::Barrier(barrier) => {
                    self.validate_any_resource(barrier.resource)?;
                    if barrier.access == AccessFlags::NONE
                        || barrier.stages == PipelineStageFlags::NONE
                        || barrier.before == barrier.after
                    {
                        return self.validation_error(GalError::command(
                            StatusCode::InvalidArgument,
                            "barrier must declare access, stages, and a state change",
                        ));
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
                | CommandOp::SetIndexBuffer { buffer: handle, .. }
                | CommandOp::Barrier(super::commands::ResourceBarrier {
                    resource: handle, ..
                }) => {
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

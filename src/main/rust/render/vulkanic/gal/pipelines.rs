//! Creating shader modules, resource layouts and sets, pipeline layouts and pipelines.

use super::*;

impl VulkanicGal {
    /// Creates a shader module from SPIR-V, backend-portable IR or GLSL,
    /// written to the backend's shader conventions.
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

    /// Creates a resource layout: the bindings, kinds, stages and array counts
    /// resource sets created against it must provide.
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

    /// Creates a resource set, binding resources to a layout. Each binding
    /// is checked against the layout's kind, stages, array completeness and
    /// buffer ranges; the set keeps its resources alive.
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
                        "binding {} is not declared by resource layout (set '{}')",
                        binding.binding, desc.label
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
            self.validate_resource_binding_buffer_range(binding, &binding.dynamic_offsets)?;
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
        self.validate_integer_sampled_texture_sampler_pairing(&desc.bindings)?;
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

    /// Creates a pipeline layout from an ordered list of resource layouts.
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

    /// Creates a graphics pipeline for a pipeline layout, shaders, vertex
    /// layout and fixed-function state, checked against the target formats
    /// it will render to.
    pub fn create_graphics_pipeline(&mut self, desc: GraphicsPipelineDesc) -> GalResult<Handle> {
        if trace_label_matches("MATTMC_GAL_TRACE_PIPELINE_LABEL", &desc.label) {
            crate::core::console::stderr(format_args!(
                "[MattMC pipeline-depth-trace] label={} depth_format={:?} depth_compare={:?} depth_write={} colors={:?}",
                desc.label, desc.depth_format, desc.depth_compare, desc.depth_write, desc.color_formats
            ));
        }
        self.pipeline_layouts.get(desc.layout)?;
        self.require_shader_stage(desc.vertex_shader, ShaderStage::Vertex)?;
        self.require_shader_stage(desc.fragment_shader, ShaderStage::Fragment)?;
        if desc.color_formats.is_empty() && desc.depth_format.is_none() {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "graphics pipeline needs at least one color or depth format",
            ));
        }
        if let Some(depth_bias) = desc.depth_bias {
            if !depth_bias.is_finite() {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "graphics pipeline depth bias factors must be finite",
                ));
            }
            if desc.depth_format.is_none() || desc.depth_compare.is_none() {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "graphics pipeline depth bias requires an enabled depth attachment and test",
                ));
            }
        }
        if let Some(stencil) = desc.stencil {
            if desc.depth_format != Some(TextureFormat::Depth24Stencil8) {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "stencil state requires a Depth24Stencil8 attachment",
                ));
            }
            if stencil.front.read_mask == 0 && stencil.back.read_mask == 0 {
                return self.validation_error(GalError::resource(
                    StatusCode::InvalidArgument,
                    "stencil state requires a non-zero read mask",
                ));
            }
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
        self.tag_gpu_profile(handle, GpuProfiledObject::GraphicsPipeline, &desc.label);
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

    /// Creates a compute pipeline. Requires the `Compute` feature.
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
}

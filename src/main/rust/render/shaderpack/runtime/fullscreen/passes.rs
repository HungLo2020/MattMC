//! Preparing, validating and binding fullscreen source passes.

use super::*;

/// One fully-owned source fullscreen pass input/output contract. It contains
/// only semantic roles and opaque GAL handles; there are no Iris attachment
/// numbers, GL units, backend descriptors, or route decisions here.
#[derive(Debug)]
pub(crate) struct PreparedFullscreenSourcePass {
    pub program_identity: String,
    pub inputs: TerrainSourceOwnedResourceSet,
    /// Exact outputs of this stage in lowered GLSL location order. Their
    /// semantic shader-pack destinations may be sparse because `DRAWBUFFERS`
    /// maps a fragment output ordinal to a named source color target.
    pub(super) color_targets: Vec<FullscreenSourceColorAttachment>,
    /// The subset actually written by this source program. This is used for
    /// feedback validation and semantic diagnostics, not target layout.
    pub outputs: Vec<FullscreenSourceColorAttachment>,
    pub(super) color_resources: ShaderPackSourceColorResources,
}

/// Resource sets and owned buffers required by one compiled source pass.
/// Dynamic data is written by a later command recorder; this type establishes
/// the exact set-zero/set-one ABI without exposing backend descriptors.
#[derive(Debug)]
pub(crate) struct BoundFullscreenSourcePass {
    pub source_data_set: Handle,
    pub pack_resources_set: Handle,
    pub texture_transform_buffer: Handle,
    pub scalar_uniform_buffer: Option<Handle>,
}

/// Per-execution state for one compiled source fullscreen pass. The caller
/// declares every prior semantic usage; this recorder does not infer an Iris,
/// OpenGL, or backend target state. All pass outputs leave shader-readable.
#[derive(Debug)]
pub(crate) struct FullscreenSourcePassFrame {
    pub texture_transforms: Vec<u8>,
    pub scalar_uniforms: Vec<u8>,
    pub texture_transform_before: TextureUsageState,
    pub scalar_uniform_before: Option<TextureUsageState>,
    /// Frame semantic clear values for pack-declared color targets.
    pub clear_values: ShaderPackColorClearValues,
    /// Exact lowered GLSL output-location order of the compiled render target.
    pub color_attachment_before: Vec<TextureUsageState>,
    /// Per-target first-use clear decision from the source color frame plan.
    /// None is retained only for isolated direct recorder tests.
    pub clear_targets_this_pass: Option<Vec<bool>>,
}

impl PreparedFullscreenSourcePass {
    /// Prepares a complete, generation-coherent source pass contract. Source
    /// colors are allocated by the private target cache; all non-color roles
    /// must arrive through independently owned semantic resource sets.
    ///
    /// This cannot execute a draw. Keeping preparation separate makes an
    /// incomplete source plan reject before a backend can construct a pass or
    /// accidentally mix an internal fixture with shader-pack resources.
    pub(crate) fn prepare(
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        manifest: &ShaderPackColorTargetManifest,
        targets: &ShaderPackColorTargets,
        external_inputs: impl IntoIterator<Item = TerrainSourceOwnedResourceSet>,
    ) -> GalResult<Self> {
        let color_resources = prepare_fullscreen_source_color_resources(gal, program, targets)?;
        // Geometry admission snapshots can contain that program's current
        // color samplers. This stage owns its feedback/mipmap selection and
        // must not inherit those bindings. Exclude only the explicitly owned
        // color subset; retain strict conflict checks for every other role.
        let stage_color_roles = color_resources.resources().availability().resources()
            .map(|resource| resource.role).collect::<Vec<_>>();
        let input_sets = external_inputs.into_iter().map(|resources| {
            if resources.availability().shader_pack_generation() != targets.identity.shader_pack_generation
                || resources.availability().world_generation() != targets.identity.world_generation
            {
                return Err(GalError::invalid_argument(
                    "fullscreen source snapshot belongs to a different shader-pack or world generation",
                ));
            }
            resources.excluding_roles(stage_color_roles.iter().cloned())
        }).collect::<GalResult<Vec<_>>>();
        let mut input_sets = match input_sets {
            Ok(sets) => sets.into_iter(),
            Err(error) => {
                color_resources.destroy(gal);
                return Err(error);
            }
        };
        let Some(mut inputs) = input_sets.next() else {
            color_resources.destroy(gal);
            return Err(GalError::invalid_argument(
                "fullscreen source pass requires an exact source resource snapshot",
            ));
        };
        for resources in input_sets.chain(std::iter::once(color_resources.resources().clone())) {
            let unique = match resources.excluding_roles_already_owned_by(&inputs) {
                Ok(unique) => unique,
                Err(error) => {
                    color_resources.destroy(gal);
                    return Err(error);
                }
            };
            if unique.len() == 0 {
                continue;
            }
            inputs = match TerrainSourceOwnedResourceSet::merge([&inputs, &unique]) {
                Ok(merged) => merged,
                Err(error) => {
                    color_resources.destroy(gal);
                    return Err(error);
                }
            };
        }
        let outputs = match resolve_fullscreen_source_color_attachments(program, manifest, targets)
        {
            Ok(outputs) => outputs,
            Err(error) => {
                color_resources.destroy(gal);
                return Err(error);
            }
        };
        let color_targets = outputs.clone();

        if inputs.availability().shader_pack_generation() != program.shader_pack_generation {
            color_resources.destroy(gal);
            return Err(GalError::invalid_argument(format!(
                "fullscreen source program generation {} does not match its prepared input generation {}",
                program.shader_pack_generation,
                inputs.availability().shader_pack_generation()
            )));
        }
        if let Err(error) = program.require_semantic_resources(inputs.availability()) {
            color_resources.destroy(gal);
            let available = inputs
                .availability()
                .resources()
                .map(|resource| resource.role.semantic_name().to_string())
                .collect::<Vec<_>>()
                .join(",");
            return Err(GalError::invalid_argument(format!(
                "fullscreen source program '{}' semantic resources unavailable: {error}; available=[{}]",
                program.identity.as_str(), available
            )));
        }
        if let Err(error) = validate_feedback_separation(program, &outputs) {
            color_resources.destroy(gal);
            return Err(error);
        }
        if let Err(error) = validate_output_target_slots(&outputs, &color_targets) {
            color_resources.destroy(gal);
            return Err(error);
        }

        Ok(Self {
            program_identity: program.identity.as_str().to_string(),
            inputs,
            color_targets,
            outputs,
            color_resources,
        })
    }

    /// Explicit destruction of the program-local color samplers. Source color
    /// targets themselves remain owned by their generation cache.
    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        self.color_resources.destroy(gal);
    }

    /// Compiles an explicit backend-neutral fullscreen pass from this already
    /// validated semantic contract. It owns no frame target and issues no
    /// draw, so compiling cannot alter route selection or presentation.
    #[cfg(test)]
    pub(crate) fn compile(
        &self,
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        extent: crate::render::vulkanic::resources::Extent3d,
    ) -> GalResult<CompiledFullscreenSourcePass> {
        self.compile_cached(gal, program, extent, None)
    }

    /// As [`Self::compile`], reusing view-independent pipeline objects from
    /// `cache` (valid for the given source-candidate epochs) when present.
    pub(crate) fn compile_cached(
        &self,
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        extent: crate::render::vulkanic::resources::Extent3d,
        cache: Option<(&FullscreenPipelineCache, (u64, u64))>,
    ) -> GalResult<CompiledFullscreenSourcePass> {
        if self.program_identity != program.identity.as_str()
            || self.inputs.availability().shader_pack_generation() != program.shader_pack_generation
        {
            return Err(GalError::invalid_argument(
                "fullscreen source program does not match the prepared semantic contract",
            ));
        }
        if extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "fullscreen source pass requires a non-zero two-dimensional extent",
            ));
        }
        let layouts = program.execution_resource_layouts()?;
        let color_formats = self
            .color_targets
            .iter()
            .map(|output| output.format)
            .collect::<Vec<_>>();
        let color_views = self
            .color_targets
            .iter()
            .map(|output| output.view)
            .collect::<Vec<_>>();
        if color_formats.is_empty() {
            return Err(GalError::invalid_argument(
                "fullscreen source pass requires at least one semantic color output",
            ));
        }
        let label = format!("fullscreen-source.{}", self.program_identity);
        let celestial_blend = matches!(
            program.raster_primitive,
            FullscreenSourceRasterPrimitive::VanillaCelestialQuad
        );
        let cache_key = cache.map(|(cache, epochs)| {
            if cache.epochs.get() != Some(epochs) {
                cache.release_all(gal);
                cache.epochs.set(Some(epochs));
            }
            (
                cache,
                FullscreenPipelineKey {
                    program_identity: program.identity.as_str().to_string(),
                    shader_pack_generation: program.shader_pack_generation,
                    source_stage_path: program.source_stage_path.clone(),
                    color_formats: color_formats.clone(),
                    celestial_blend,
                },
            )
        });
        if let Some((cache, key)) = cache_key.as_ref() {
            let cached = cache.entries.borrow().get(key).and_then(|candidates| {
                candidates
                    .iter()
                    .find(|cached| {
                        cached.vertex == program.vertex
                            && cached.fragment == program.fragment
                            && cached.source_data_bindings == layouts.source_data.bindings
                            && cached.pack_resources_bindings == layouts.pack_resources.bindings
                    })
                    .map(|cached| std::sync::Arc::clone(&cached.objects))
            });
            if let Some(shared) = cached {
                let target = gal.create_render_target(RenderTargetDesc {
                    label: format!("{label}.target"),
                    color_views,
                    depth_stencil_view: None,
                    extent,
                })?;
                let pass = match gal.create_render_pass(RenderPassDesc {
                    label: format!("{label}.pass"),
                    target,
                    color_formats,
                    depth_format: None,
                }) {
                    Ok(pass) => pass,
                    Err(error) => {
                        let _ = gal.destroy(target);
                        return Err(error);
                    }
                };
                return Ok(CompiledFullscreenSourcePass {
                    target,
                    pass,
                    source_data_layout: shared.source_data_layout,
                    pack_resources_layout: shared.pack_resources_layout,
                    pipeline_layout: shared.pipeline_layout,
                    vertex_shader: shared.vertex_shader,
                    fragment_shader: shared.fragment_shader,
                    pipeline: shared.pipeline,
                    shared: Some(shared),
                });
            }
        }
        let cached_bindings = cache_key.as_ref().map(|_| {
            (
                layouts.source_data.bindings.clone(),
                layouts.pack_resources.bindings.clone(),
            )
        });
        let mut created = Vec::new();
        let result = (|| -> GalResult<CompiledFullscreenSourcePass> {
            let source_data_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.source-data"),
                bindings: layouts.source_data.bindings,
            })?;
            created.push(source_data_layout);
            let pack_resources_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.pack-resources"),
                bindings: layouts.pack_resources.bindings,
            })?;
            created.push(pack_resources_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.layout"),
                resource_layouts: vec![source_data_layout, pack_resources_layout],
            })?;
            created.push(pipeline_layout);
            let [vertex, fragment] = program.shader_module_descriptors(gal.capabilities().shader_conventions);
            let vertex_shader = gal.create_shader_module(vertex)?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(fragment)?;
            created.push(fragment_shader);
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.target"),
                color_views,
                depth_stencil_view: None,
                extent,
            })?;
            created.push(target);
            let pass = gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.pass"),
                target,
                color_formats: color_formats.clone(),
                depth_format: None,
            })?;
            created.push(pass);
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
                // Vanilla's CELESTIAL pipeline uses BlendFunction.OVERLAY:
                // src.rgb * src.a + dst.rgb. With replacement writes, the
                // transparent corners of the sun/moon quad erase the sky and
                // form a visible square after composite tone mapping.
                blend: if celestial_blend {
                    BlendMode::Overlay
                } else {
                    BlendMode::Disabled
                },
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats,
                depth_format: None,
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(CompiledFullscreenSourcePass {
                target,
                pass,
                source_data_layout,
                pack_resources_layout,
                pipeline_layout,
                vertex_shader,
                fragment_shader,
                pipeline,
                shared: None,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        let mut compiled = result?;
        if let (Some((cache, key)), Some((source_data_bindings, pack_resources_bindings))) =
            (cache_key, cached_bindings)
        {
            let shared = std::sync::Arc::new(FullscreenPipelineObjects {
                source_data_layout: compiled.source_data_layout,
                pack_resources_layout: compiled.pack_resources_layout,
                pipeline_layout: compiled.pipeline_layout,
                vertex_shader: compiled.vertex_shader,
                fragment_shader: compiled.fragment_shader,
                pipeline: compiled.pipeline,
            });
            let cached_count: usize = cache.entries.borrow().values().map(Vec::len).sum();
            if cached_count >= FULLSCREEN_PIPELINE_CACHE_ENTRIES {
                cache.release_all(gal);
            }
            cache.entries.borrow_mut().entry(key).or_default().push(CachedFullscreenPipeline {
                vertex: program.vertex.clone(),
                fragment: program.fragment.clone(),
                source_data_bindings,
                pack_resources_bindings,
                objects: std::sync::Arc::clone(&shared),
            });
            compiled.shared = Some(shared);
        }
        Ok(compiled)
    }

    /// Materializes the two explicit descriptor/resource sets required for
    /// this program. Buffers are Rust-owned upload resources; no Java memory
    /// or renderer uniform object is retained. The caller must write exactly
    /// the program ABI byte counts before recording the fullscreen draw.
    pub(crate) fn bind_resources(
        &self,
        gal: &mut VulkanicGal,
        program: &LoweredFullscreenSourceProgram,
        compiled: &CompiledFullscreenSourcePass,
    ) -> GalResult<BoundFullscreenSourcePass> {
        if self.program_identity != program.identity.as_str() {
            return Err(GalError::invalid_argument(
                "fullscreen source resources cannot bind a different program",
            ));
        }
        let interface = &program.execution_interface;
        interface.validate()?;
        let label = format!("fullscreen-source.{}", self.program_identity);
        let mut created = Vec::new();
        let result = (|| -> GalResult<BoundFullscreenSourcePass> {
            let texture_transform_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.texture-transforms"),
                size: u64::from(interface.texture_transform_bytes),
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
            })?;
            created.push(texture_transform_buffer);
            let scalar_uniform_buffer = if let Some(_) = interface.scalar_uniforms {
                let buffer = gal.create_buffer(BufferDesc {
                    label: format!("{label}.scalar-uniforms"),
                    size: u64::from(interface.scalar_uniform_bytes),
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
                })?;
                created.push(buffer);
                Some(buffer)
            } else {
                None
            };
            let mut source_bindings = vec![ResourceBinding {
                binding: interface.texture_transforms.binding,
                array_index: 0,
                resource: texture_transform_buffer,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![0],
                buffer_range: Some(u64::from(interface.texture_transform_bytes)),
            }];
            if let (Some(binding), Some(buffer)) =
                (interface.scalar_uniforms, scalar_uniform_buffer)
            {
                source_bindings.push(ResourceBinding {
                    binding: binding.binding,
                    array_index: 0,
                    resource: buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: vec![0],
                    buffer_range: Some(u64::from(interface.scalar_uniform_bytes)),
                });
            }
            source_bindings.sort_by_key(|binding| binding.binding);
            let source_data_set = gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.source-data-set"),
                layout: compiled.source_data_layout,
                bindings: source_bindings,
            })?;
            created.push(source_data_set);
            let pack_resources_set = gal.create_resource_set(program.pack_resource_set_desc(
                format!("{label}.pack-resources-set"),
                compiled.pack_resources_layout,
                &self.inputs,
            )?)?;
            created.push(pack_resources_set);
            Ok(BoundFullscreenSourcePass {
                source_data_set,
                pack_resources_set,
                texture_transform_buffer,
                scalar_uniform_buffer,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    /// Appends one fully explicit fullscreen draw. It neither submits nor
    /// presents, and cannot select a gameplay route. A higher-level runtime
    /// must own frame sequencing for both vanilla and Distant Horizons.
    pub(crate) fn append_draw(
        &self,
        program: &LoweredFullscreenSourceProgram,
        compiled: &CompiledFullscreenSourcePass,
        bound: &BoundFullscreenSourcePass,
        frame: FullscreenSourcePassFrame,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.program_identity != program.identity.as_str() {
            return Err(GalError::invalid_argument(
                "fullscreen source draw cannot use a different program than its prepared contract",
            ));
        }
        let interface = &program.execution_interface;
        interface.validate()?;
        if frame.texture_transforms.len() != interface.texture_transform_bytes as usize {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source texture transform payload is {} bytes but program ABI requires {}",
                frame.texture_transforms.len(),
                interface.texture_transform_bytes
            )));
        }
        match interface.scalar_uniforms {
            Some(_) if frame.scalar_uniforms.len() != interface.scalar_uniform_bytes as usize => {
                return Err(GalError::invalid_argument(format!(
                    "fullscreen source scalar uniform payload is {} bytes but program ABI requires {}",
                    frame.scalar_uniforms.len(),
                    interface.scalar_uniform_bytes
                )));
            }
            None if !frame.scalar_uniforms.is_empty() || frame.scalar_uniform_before.is_some() => {
                return Err(GalError::invalid_argument(
                    "fullscreen source program has no scalar uniform binding but scalar frame data was supplied",
                ));
            }
            Some(_) if frame.scalar_uniform_before.is_none() => {
                return Err(GalError::invalid_argument(
                    "fullscreen source scalar uniform binding requires an explicit prior state",
                ));
            }
            _ => {}
        }
        if frame.color_attachment_before.len() != self.color_targets.len() {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source frame supplies {} color attachment states for {} source slots",
                frame.color_attachment_before.len(),
                self.color_targets.len()
            )));
        }
        if frame
            .clear_targets_this_pass
            .as_ref()
            .is_some_and(|mask| mask.len() != self.color_targets.len())
        {
            return Err(GalError::invalid_argument(
                "fullscreen source clear mask does not match color targets",
            ));
        }
        if frame.texture_transform_before == TextureUsageState::TransferDst
            || frame.scalar_uniform_before == Some(TextureUsageState::TransferDst)
            || frame
                .color_attachment_before
                .iter()
                .any(|state| *state == TextureUsageState::ColorAttachment)
        {
            return Err(GalError::invalid_argument(
                "fullscreen source frame cannot begin from an in-progress transfer or color-attachment state",
            ));
        }
        let colors = self
            .color_targets
            .iter()
            .zip(frame.color_attachment_before.iter().copied())
            .enumerate()
            .map(|(index, (attachment, before))| {
                let clear_this_pass = frame
                    .clear_targets_this_pass
                    .as_ref()
                    .map_or(attachment.clear_each_frame, |mask| mask[index]);
                if clear_this_pass {
                    return Ok(PassAttachment {
                        view: attachment.view,
                        // `Clear = true` is source-pack frame semantics, not
                        // one-time allocation initialization. The prior
                        // layout still matters to the barrier below, but the
                        // attachment must be cleared on every source-frame
                        // use after it becomes a valid history image.
                        load_op: AttachmentLoadOp::Clear,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(source_color_clear_color(
                            attachment.source_slot,
                            attachment.clear_color_bits,
                            frame.clear_values.fog_color,
                        )),
                    });
                }
                if before == TextureUsageState::Undefined {
                    let writes_attachment = self
                        .outputs
                        .iter()
                        .any(|output| output.role == attachment.role);
                    if !clear_this_pass && !writes_attachment {
                        return Err(GalError::invalid_argument(format!(
                            "fullscreen source target '{}' is undefined, is not cleared, and is not written by this pass",
                            role_name(&attachment.role)
                        )));
                    }
                    return Ok(PassAttachment {
                        view: attachment.view,
                        // A source target with `Clear = false` explicitly
                        // leaves its prior contents unspecified. Only a pass
                        // that writes that exact target may discard them on a
                        // first use; untouched attachments must retain valid
                        // history and therefore reject while undefined.
                        // Feedback sampling remains separate and must be
                        // proven by its own previous image.
                        load_op: AttachmentLoadOp::DontCare,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    });
                }
                Ok(PassAttachment {
                    view: attachment.view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                })
            })
            .collect::<GalResult<Vec<_>>>()?;

        operations.push(CommandOp::Barrier(buffer_barrier(
            bound.texture_transform_buffer,
            frame.texture_transform_before,
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::HostWriteBuffer {
            buffer: bound.texture_transform_buffer,
            offset: 0,
            data: frame.texture_transforms,
        });
        operations.push(CommandOp::Barrier(buffer_barrier(
            bound.texture_transform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        if let Some(buffer) = bound.scalar_uniform_buffer {
            let before = frame
                .scalar_uniform_before
                .expect("validated scalar uniform prior state");
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                before,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer,
                offset: 0,
                data: frame.scalar_uniforms,
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        for (attachment, before) in self
            .color_targets
            .iter()
            .zip(frame.color_attachment_before.iter().copied())
        {
            operations.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                before,
                TextureUsageState::ColorAttachment,
            )));
        }
        operations.push(CommandOp::BeginPass {
            pass: compiled.pass,
            target: compiled.target,
            colors,
            depth_stencil: None,
        });
        operations.push(CommandOp::BindGraphicsPipeline(compiled.pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: compiled.pipeline_layout,
            set_index: 0,
            set: bound.source_data_set,
            dynamic_offsets: vec![0; usize::from(interface.scalar_uniforms.is_some()) + 1],
        });
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout: compiled.pipeline_layout,
            set_index: 1,
            set: bound.pack_resources_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Draw {
            vertices: program.raster_primitive.vertex_count(),
            instances: 1,
        });
        operations.push(CommandOp::EndPass);
        for attachment in &self.color_targets {
            operations.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        Ok(())
    }
}

impl BoundFullscreenSourcePass {
    pub(crate) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            Some(self.pack_resources_set),
            Some(self.source_data_set),
            self.scalar_uniform_buffer,
            Some(self.texture_transform_buffer),
        ]
        .into_iter()
        .flatten()
        {
            let _ = gal.destroy(handle);
        }
    }
}

pub(super) fn validate_feedback_separation(
    program: &LoweredFullscreenSourceProgram,
    outputs: &[FullscreenSourceColorAttachment],
) -> GalResult<()> {
    for output in outputs {
        let sampled_same_role = program
            .opaque_resource_bindings
            .bindings()
            .iter()
            .any(|binding| binding.role() == output.role);
        if !sampled_same_role {
            continue;
        }
        let output_location = program
            .outputs
            .iter()
            .find(|candidate| candidate.role() == output.role)
            .map(|candidate| candidate.source_location())
            .ok_or_else(|| {
                GalError::backend("fullscreen output attachment lost its lowered GLSL location")
            })?;
        let has_feedback_pair = program.feedback_requirements.iter().any(|requirement| {
            requirement.role == output.role && requirement.output_location == output_location
        });
        if !has_feedback_pair {
            return Err(GalError::invalid_argument(format!(
                "fullscreen source program '{}' samples and writes semantic color '{}' without an explicit feedback pair",
                program.identity.as_str(),
                role_name(&output.role)
            )));
        }
    }
    Ok(())
}

pub(super) fn validate_output_target_slots(
    outputs: &[FullscreenSourceColorAttachment],
    color_targets: &[FullscreenSourceColorAttachment],
) -> GalResult<()> {
    if outputs != color_targets {
        return Err(GalError::invalid_argument(
            "fullscreen source outputs differ from the staged lowered-location attachment set",
        ));
    }
    Ok(())
}

pub(super) fn role_name(role: &TerrainSourceResourceRole) -> String {
    role.diagnostic_name()
}

pub(super) fn buffer_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

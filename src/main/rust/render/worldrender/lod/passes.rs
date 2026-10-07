//! Opaque, forward-opaque, transparent and water LOD pass resources and their shared draw resources.

use crate::render::worldrender::lod::*;

/// Distant Horizons preserves the source OpenGL quad order in both its
/// reduced-color and provenance-resolved exact-atlas streams. The GAL
/// `RasterYDirection::Up` contract keeps the source `FrontFace` semantic
/// unchanged while each backend realizes its target coordinates, so source
/// back-face classification remains counter-clockwise on every backend. Keep
/// this DH-specific state separate from near-terrain winding; the source quad
/// index contract is otherwise unchanged.
pub(super) fn world_lod_source_front_face() -> FrontFace {
    FrontFace::CounterClockwise
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodDrawResourceKey {
    pub(in crate::render::worldrender::lod) column_key: u64,
    pub(in crate::render::worldrender::lod) column_generation: u64,
    pub(in crate::render::worldrender::lod) layer: u32,
    pub(in crate::render::worldrender::lod) segment_index: u32,
}

impl WorldLodDrawResourceKey {
    pub(super) fn from_draw(draw: WorldLodGpuDraw) -> Self {
        Self {
            column_key: draw.column_key,
            column_generation: draw.column_generation,
            // DH numbers VBO segments independently inside each material
            // bucket. Keep the immutable layer in the residency key so a
            // side, upward, or water segment with the same ordinal cannot
            // borrow another bucket's storage buffer.
            layer: draw.layer,
            segment_index: draw.segment_index,
        }
    }
}

pub(super) struct WorldLodPipelineResources {
    pub(in crate::render::worldrender::lod) vertex_shader: Handle,
    pub(in crate::render::worldrender::lod) fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) geometry_and_frame_layout: Handle,
    pub(in crate::render::worldrender::lod) lightmap_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline: Handle,
    /// Direct-route variant used by the Rust-owned DH offscreen color/depth
    /// boundary. Opaque writes depth into the private DH target; transparent
    /// streams retain their explicit no-write policy.
    pub(in crate::render::worldrender::lod) offscreen_pipeline: Option<Handle>,
    /// The same water geometry is replayed in Frozen's transparent phase
    /// after that phase installs accumulated-alpha blending. Keep this as a
    /// distinct immutable pipeline state instead of mutating or inferring
    /// blend state between draws.
    pub(in crate::render::worldrender::lod) offscreen_replay_pipeline: Option<Handle>,
}

impl WorldLodPipelineResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        if let Some(handle) = self.offscreen_pipeline {
            let _ = gal.retire(handle);
        }
        if let Some(handle) = self.offscreen_replay_pipeline {
            let _ = gal.retire(handle);
        }
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.lightmap_layout,
            self.geometry_and_frame_layout,
            self.fragment_shader,
            self.vertex_shader,
        ] {
            let _ = gal.retire(handle);
        }
    }
}

#[derive(Debug)]
pub(super) struct WorldLodDrawResources {
    pub(in crate::render::worldrender::lod) uniform_buffer: Option<Handle>,
    pub(in crate::render::worldrender::lod) resource_set: Handle,
    /// The frame block is owned by this immutable column/segment resource.
    /// Keep the last packed bytes so settled DH frames do not enqueue a
    /// redundant host write and two buffer barriers for an unchanged draw.
    /// This cache is discarded with the resource key at column-generation
    /// retirement, so it cannot cross a DH asset or world generation.
    pub(in crate::render::worldrender::lod) last_uniform_bytes: Option<[u8; 240]>,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodLightmapResourceKey {
    pub(in crate::render::worldrender::lod) world_generation: u64,
    pub(in crate::render::worldrender::lod) lightmap_generation: u64,
    /// A semantic generation can be staged again while the surrounding frame
    /// graph is rebuilt. Keep the set-one binding tied to the exact private
    /// GAL residency, rather than accidentally reusing a set that still
    /// references its predecessor's view.
    pub(in crate::render::worldrender::lod) texture_view: Handle,
    pub(in crate::render::worldrender::lod) sampler: Handle,
}

impl From<VanillaLightmapBinding> for WorldLodLightmapResourceKey {
    fn from(binding: VanillaLightmapBinding) -> Self {
        Self {
            world_generation: binding.world_generation,
            lightmap_generation: binding.lightmap_generation,
            texture_view: binding.texture_view,
            sampler: binding.sampler,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub(super) struct WorldLodLightmapResources {
    pub(in crate::render::worldrender::lod) texture_view: Handle,
    pub(in crate::render::worldrender::lod) sampler: Handle,
    pub(in crate::render::worldrender::lod) resource_set: Handle,
}

impl WorldLodDrawResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        let _ = gal.retire(self.resource_set);
        if let Some(uniform_buffer) = self.uniform_buffer {
            let _ = gal.retire(uniform_buffer);
        }
    }
}

/// Private resource owner shared by the opaque and non-water transparent DH
/// material passes. It owns only set-zero resources because the shared
/// semantic lightmap's generation-keyed set-one binding is supplied by the
/// later frame executor. The pass class remains explicit so compatible
/// semantic streams share no accidental blend/depth policy.
pub(super) struct WorldLodPassResources {
    pub(in crate::render::worldrender::lod) pass: WorldLodPassClass,
    pub(in crate::render::worldrender::lod) deferred: bool,
    pub(in crate::render::worldrender::lod) color_format: Option<TextureFormat>,
    pub(in crate::render::worldrender::lod) pipeline: Option<WorldLodPipelineResources>,
    pub(in crate::render::worldrender::lod) draws: BTreeMap<WorldLodDrawResourceKey, WorldLodDrawResources>,
    pub(in crate::render::worldrender::lod) lightmaps: BTreeMap<WorldLodLightmapResourceKey, WorldLodLightmapResources>,
    pub(in crate::render::worldrender::lod) packed_uniforms: Option<WorldLodPackedUniforms>,
    pub(in crate::render::worldrender::lod) use_packed_uniforms: bool,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodPreparedDraw {
    pub pipeline: Handle,
    pub offscreen_pipeline: Option<Handle>,
    pub offscreen_replay_pipeline: Option<Handle>,
    pub pipeline_layout: Handle,
    pub geometry_resource_set: Handle,
    pub lightmap_resource_set: Handle,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub uniform_dynamic_offset: Option<u64>,
}

impl WorldLodPassResources {
    pub(super) fn new(pass: WorldLodPassClass) -> Self {
        Self::new_with_mode(pass, true)
    }

    pub(super) fn new_forward(pass: WorldLodPassClass) -> Self {
        Self::new_with_mode(pass, false)
    }

    pub(super) fn new_with_mode(pass: WorldLodPassClass, deferred: bool) -> Self {
        Self {
            pass,
            deferred,
            color_format: deferred.then_some(SHADER_G_BUFFER_COLOR_FORMAT),
            pipeline: None,
            draws: BTreeMap::new(),
            lightmaps: BTreeMap::new(),
            packed_uniforms: None,
            use_packed_uniforms: !deferred && packed_lod_uniforms_enabled(),
        }
    }

    pub(super) fn begin_frame(&mut self) {
        if let Some(packed) = self.packed_uniforms.as_mut() {
            packed.begin_frame();
        }
    }

    pub(super) fn flush_packed_uniforms(&mut self, ops: &mut Vec<CommandOp>) {
        if let Some(packed) = self.packed_uniforms.as_mut() {
            packed.flush(ops);
        }
    }

    pub(super) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodGpuDraw,
        uniforms: WorldLodDrawUniform,
        material_contract: WorldLodMaterialContract,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedDraw> {
        if material_contract.pass != self.pass
            || material_contract.vertex_layout_version != WORLD_LOD_GPU_VERTEX_LAYOUT_V2
            || draw.index_count == 0
            || draw.index_count % 3 != 0
        {
            return Err(GalError::invalid_argument(
                "world LOD material pass received an invalid admitted draw",
            ));
        }
        self.ensure_pipeline(gal)?;
        if self.use_packed_uniforms && self.packed_uniforms.is_none() {
            self.packed_uniforms = Some(WorldLodPackedUniforms::new(
                gal,
                &format!("world-lod-{:?}", self.pass),
            )?);
        }
        let key = WorldLodDrawResourceKey::from_draw(draw);
        if !self.draws.contains_key(&key) {
            let pipeline = self
                .pipeline
                .as_ref()
                .expect("world LOD pipeline exists after successful initialization");
            let own_uniform_buffer = if self.use_packed_uniforms {
                None
            } else {
                Some(gal.create_buffer(BufferDesc {
                    label: format!(
                        "world-lod-column{}-gen{}-segment{}.frame",
                        key.column_key, key.column_generation, key.segment_index
                    ),
                    size: PACKED_LOD_UNIFORM_BYTES as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
                })?)
            };
            let uniform_buffer = own_uniform_buffer.unwrap_or_else(|| {
                self.packed_uniforms
                    .as_ref()
                    .expect("packed uniform arena exists")
                    .buffer
            });
            let resource_set = match gal.create_resource_set(ResourceSetDesc {
                label: format!(
                    "world-lod-column{}-gen{}-segment{}.geometry-and-frame-set",
                    key.column_key, key.column_generation, key.segment_index
                ),
                layout: pipeline.geometry_and_frame_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: draw.vertex_buffer,
                        kind: ResourceBindingKind::StorageBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: uniform_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: if self.use_packed_uniforms {
                            vec![0]
                        } else {
                            Vec::new()
                        },
                        buffer_range: self
                            .use_packed_uniforms
                            .then_some(PACKED_LOD_UNIFORM_BYTES as u64),
                    },
                ],
            }) {
                Ok(set) => set,
                Err(error) => {
                    if let Some(own_uniform_buffer) = own_uniform_buffer {
                        let _ = gal.retire(own_uniform_buffer);
                    }
                    return Err(error);
                }
            };
            self.draws.insert(
                key,
                WorldLodDrawResources {
                    uniform_buffer: own_uniform_buffer,
                    resource_set,
                    last_uniform_bytes: None,
                },
            );
        }
        let packed_uniform = uniforms.pack_std140();
        let (uniform_buffer, geometry_resource_set, uniform_changed) = {
            let resources = self
                .draws
                .get_mut(&key)
                .expect("world LOD material resource entry exists after creation");
            let uniform_changed =
                !self.use_packed_uniforms && resources.last_uniform_bytes != Some(packed_uniform);
            if uniform_changed {
                resources.last_uniform_bytes = Some(packed_uniform);
            }
            (
                resources.uniform_buffer,
                resources.resource_set,
                uniform_changed,
            )
        };
        let lightmap_resource_set = match self.ensure_lightmap_resource_set(gal, lightmap) {
            Ok(resource_set) => resource_set,
            Err(error) => return Err(error),
        };
        let uniform_dynamic_offset = if self.use_packed_uniforms {
            Some(
                self.packed_uniforms
                    .as_mut()
                    .expect("packed uniform arena exists")
                    .push(packed_uniform)?,
            )
        } else {
            None
        };
        if uniform_changed {
            let uniform_buffer = uniform_buffer.expect("per-draw uniform buffer exists");
            ops.extend([
                CommandOp::Barrier(buffer_barrier(
                    uniform_buffer,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::HostWriteBuffer {
                    buffer: uniform_buffer,
                    offset: 0,
                    data: packed_uniform.to_vec(),
                },
                CommandOp::Barrier(buffer_barrier(
                    uniform_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
            ]);
        }
        let pipeline = self
            .pipeline
            .as_ref()
            .expect("world LOD pipeline remains alive while draw resources are live");
        Ok(WorldLodPreparedDraw {
            pipeline: pipeline.pipeline,
            offscreen_pipeline: pipeline.offscreen_pipeline,
            offscreen_replay_pipeline: pipeline.offscreen_replay_pipeline,
            pipeline_layout: pipeline.pipeline_layout,
            geometry_resource_set,
            lightmap_resource_set,
            index_buffer: draw.index_buffer,
            index_offset: draw.index_offset,
            index_type: draw.index_type,
            index_count: draw.index_count,
            uniform_dynamic_offset,
        })
    }

    pub(super) fn set_forward_color_format(
        &mut self,
        gal: &mut VulkanicGal,
        format: TextureFormat,
    ) -> GalResult<()> {
        if self.deferred && self.pass == WorldLodPassClass::Opaque {
            return Err(GalError::invalid_argument(
                "deferred DH pass cannot be rebound to a forward color format",
            ));
        }
        if self.pipeline.is_some() && self.color_format != Some(format) {
            // Toggling shaders moves the transparent/water owners between the
            // forward frame target and the HDR G-buffer. Rebuild every
            // format-bound object; GAL retires in-flight ones after use.
            self.destroy(gal);
        }
        if self.pipeline.is_none() {
            // Transparent and water owners can also serve deferred draws;
            // only the explicit forward setup enables the packed experiment.
            self.use_packed_uniforms = packed_lod_uniforms_enabled();
        }
        self.color_format = Some(format);
        Ok(())
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        for (_, draw) in std::mem::take(&mut self.draws) {
            draw.destroy(gal);
        }
        if let Some(packed) = self.packed_uniforms.take() {
            packed.destroy(gal);
        }
        self.clear_lightmap_bindings(gal);
        if let Some(pipeline) = self.pipeline.take() {
            pipeline.destroy(gal);
        }
    }

    /// A lightmap residency may be replaced after the combined frame that
    /// first uses its successor has been submitted. Retire only the stale
    /// set-one bindings before the runtime releases the superseded texture
    /// view; geometry and pipeline resources remain generation-independent.
    pub(crate) fn retain_lightmap_binding(
        &mut self,
        gal: &mut VulkanicGal,
        binding: VanillaLightmapBinding,
    ) {
        let retained = WorldLodLightmapResourceKey::from(binding);
        let stale = self
            .lightmaps
            .keys()
            .filter(|key| **key != retained)
            .copied()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(resources) = self.lightmaps.remove(&key) {
                let _ = gal.retire(resources.resource_set);
            }
        }
    }

    /// Releases every private set-one binding before the owning shader
    /// runtime tears down its lightmap residency. The runtime owns the image
    /// lifetime; this cache owns only consumers of its view.
    pub(crate) fn clear_lightmap_bindings(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.lightmaps) {
            let _ = gal.retire(resources.resource_set);
        }
    }

    /// Drops private set-zero bindings that no longer match the immutable
    /// column asset map. Geometry residency owns the buffers themselves; this
    /// cache owns only resource sets/uniform buffers that reference them, so
    /// both layers retire at the same generation boundary.
    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        let stale = self
            .draws
            .keys()
            .filter(|key| {
                assets.get(&key.column_key).is_none_or(|asset| {
                    asset.column_generation != key.column_generation
                        || asset.segments.get(key.segment_index as usize).is_none()
                })
            })
            .copied()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(resources) = self.draws.remove(&key) {
                resources.destroy(gal);
            }
        }
    }

    pub(super) fn ensure_pipeline(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        if self.pipeline.is_some() {
            return Ok(());
        }
        let (label, program, blend, cull_mode, depth_compare, depth_write, color_formats) =
            match self.pass {
                WorldLodPassClass::Opaque if !self.deferred => (
                    "world-lod-forward-opaque",
                    minimal_distant_horizons_lod_forward_opaque_program(),
                    BlendMode::Disabled,
                    CullMode::Back,
                    CompareOp::LessOrEqual,
                    // Vanilla terrain has already populated the shared depth
                    // target, so LESS_OR_EQUAL keeps it authoritative. Opaque
                    // DH must still write the farther surviving depth so later
                    // LOD surfaces can be rejected instead of shading every
                    // hidden layer. The deferred/Iris path below remains a
                    // non-writing G-buffer input.
                    true,
                    vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)],
                ),
                WorldLodPassClass::Opaque => (
                    "world-lod-opaque",
                    minimal_distant_horizons_lod_opaque_program(),
                    BlendMode::Disabled,
                    CullMode::Back,
                    CompareOp::LessOrEqual,
                    // Keep coarse DH LOD out of the shared G-buffer depth
                    // history; the source-faithful depth state belongs to
                    // the private DH target variant below.
                    false,
                    vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm); 4],
                ),
                WorldLodPassClass::TransparentSide => (
                    "world-lod-transparent-side",
                    minimal_distant_horizons_lod_transparent_program(),
                    BlendMode::Alpha,
                    CullMode::Back,
                    CompareOp::LessOrEqual,
                    false,
                    vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)],
                ),
                WorldLodPassClass::TransparentUp => (
                    "world-lod-transparent-up",
                    minimal_distant_horizons_lod_transparent_program(),
                    BlendMode::Alpha,
                    CullMode::Back,
                    CompareOp::LessOrEqual,
                    false,
                    vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)],
                ),
                WorldLodPassClass::WaterSurface => (
                    "world-lod-water-surface",
                    minimal_distant_horizons_lod_transparent_program(),
                    BlendMode::AlphaSource,
                    CullMode::None,
                    // Frozen's water surface intentionally ignores the
                    // existing depth inside the private compositor. The
                    // shared target retains its prior conservative policy.
                    CompareOp::LessOrEqual,
                    false,
                    vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)],
                ),
            };
        // Capture-only raster isolation for the direct DH stream. Production
        // keeps the explicit pass policy above; a probe can temporarily remove
        // culling to distinguish winding loss from coverage/material defects.
        let cull_mode = if matches!(selected_source_raster_probe_cull_mode()?, CullMode::None) {
            CullMode::None
        } else {
            cull_mode
        };
        // The capture-only probe can invert the explicit source winding for a
        // paired raster experiment. Production remains pinned to the copied
        // DH source contract.
        let front_face = selected_source_raster_probe_front_face(world_lod_source_front_face())?;
        let depth_compare = selected_source_raster_probe_depth_compare(Some(depth_compare))?;
        let [mut geometry_and_frame_desc, lightmap_desc] =
            distant_horizons_lod_opaque_resource_layouts(label);
        if self.use_packed_uniforms {
            geometry_and_frame_desc.bindings[1].dynamic_offset_count = 1;
        }
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldLodPipelineResources> {
            let geometry_and_frame_layout = gal.create_resource_layout(geometry_and_frame_desc)?;
            created.push(geometry_and_frame_layout);
            let lightmap_layout = gal.create_resource_layout(lightmap_desc)?;
            created.push(lightmap_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![geometry_and_frame_layout, lightmap_layout],
            })?;
            created.push(pipeline_layout);
            let [vertex_desc, fragment_desc] =
                program.shader_module_descriptors(gal.capabilities().shader_conventions);
            let vertex_shader = gal.create_shader_module(vertex_desc)?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(fragment_desc)?;
            created.push(fragment_shader);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode,
                front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend,
                depth_compare,
                depth_write,
                depth_bias: None,
                color_formats: color_formats.clone(),
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            // Transparent and water resources are shared by the deferred
            // source graph and the direct DH color/depth boundary.  They
            // keep the same one-color attachment contract in both cases, so
            // give those pass classes an explicit private-target variant
            // even when their owner was initialized in deferred mode.  The
            // opaque deferred owner remains source/G-buffer-only and is not
            // admitted to the direct compositor.
            let offscreen_pipeline = if !self.deferred || self.pass != WorldLodPassClass::Opaque {
                // The private DH color/depth boundary has its own depth
                // domain, so it can reproduce Frozen's no-shader render
                // states without weakening the shared whole-frame target.
                // Opaque, transparent-side, and transparent-up phases use
                // LESS; water uses ALWAYS and writes its source surface into
                // that private depth image.  Each state keeps the exact
                // source blend/depth contract used by Frozen's no-shader
                // renderer.
                let (offscreen_blend, offscreen_depth_compare, offscreen_depth_write) =
                    private_dh_source_raster_policy(self.pass);
                // The selector is capture-only instrumentation. Apply it to
                // the private direct target as well as the shared pipeline so
                // a depth-disabled probe actually tests the raster boundary
                // that owns the DH attachment. Normal production state stays
                // on the source-faithful LESS/ALWAYS policy above.
                let offscreen_depth_compare =
                    selected_source_raster_probe_depth_compare(Some(offscreen_depth_compare))?;
                let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                    label: format!("{label}.offscreen.pipeline"),
                    layout: pipeline_layout,
                    vertex_shader,
                    fragment_shader,
                    topology: PrimitiveTopology::Triangles,
                    cull_mode,
                    front_face,
                    provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                    raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                    blend: offscreen_blend,
                    depth_compare: offscreen_depth_compare,
                    depth_write: offscreen_depth_write,
                    depth_bias: None,
                    color_formats,
                    depth_format: Some(TextureFormat::Depth32Float),
                    stencil: None,
                })?;
                created.push(pipeline);
                Some(pipeline)
            } else {
                None
            };
            let offscreen_replay_pipeline = if self.pass == WorldLodPassClass::WaterSurface {
                let (_, offscreen_depth_compare, offscreen_depth_write) =
                    private_dh_source_raster_policy(self.pass);
                let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                    label: format!("{label}.offscreen-transparent-replay.pipeline"),
                    layout: pipeline_layout,
                    vertex_shader,
                    fragment_shader,
                    topology: PrimitiveTopology::Triangles,
                    cull_mode,
                    front_face,
                    provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                    raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                    // Frozen's transparent pass changes alpha to
                    // ONE/ONE_MINUS_SRC_ALPHA before replaying water.
                    blend: private_dh_water_replay_blend_mode(),
                    depth_compare: selected_source_raster_probe_depth_compare(Some(
                        offscreen_depth_compare,
                    ))?,
                    depth_write: offscreen_depth_write,
                    depth_bias: None,
                    color_formats: vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)],
                    depth_format: Some(TextureFormat::Depth32Float),
                    stencil: None,
                })?;
                created.push(pipeline);
                Some(pipeline)
            } else {
                None
            };
            Ok(WorldLodPipelineResources {
                vertex_shader,
                fragment_shader,
                geometry_and_frame_layout,
                lightmap_layout,
                pipeline_layout,
                pipeline,
                offscreen_pipeline,
                offscreen_replay_pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.pipeline = Some(result?);
        Ok(())
    }

    pub(super) fn ensure_lightmap_resource_set(
        &mut self,
        gal: &mut VulkanicGal,
        binding: VanillaLightmapBinding,
    ) -> GalResult<Handle> {
        if binding.world_generation == 0 || binding.lightmap_generation == 0 {
            return Err(GalError::invalid_argument(
                "world LOD material pass requires a complete vanilla lightmap generation",
            ));
        }
        let key = WorldLodLightmapResourceKey::from(binding);
        if let Some(resources) = self.lightmaps.get(&key) {
            if resources.texture_view != binding.texture_view
                || resources.sampler != binding.sampler
            {
                return Err(GalError::invalid_argument(
                    "world LOD lightmap generation resolved to different Rust-owned resources",
                ));
            }
            return Ok(resources.resource_set);
        }
        let pipeline = self
            .pipeline
            .as_ref()
            .expect("world LOD pipeline exists before lightmap binding creation");
        let resource_set = gal.create_resource_set(ResourceSetDesc {
            label: format!(
                "world-lod-lightmap.world{}-gen{}.resource-set",
                key.world_generation, key.lightmap_generation
            ),
            layout: pipeline.lightmap_layout,
            bindings: vec![
                ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: binding.texture_view,
                    kind: ResourceBindingKind::SampledTexture,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 1,
                    array_index: 0,
                    resource: binding.sampler,
                    kind: ResourceBindingKind::Sampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
            ],
        })?;
        self.lightmaps.insert(
            key,
            WorldLodLightmapResources {
                texture_view: binding.texture_view,
                sampler: binding.sampler,
                resource_set,
            },
        );
        Ok(resource_set)
    }
}

/// Frozen's no-shader DH pass uses a distinct state block for the private LOD
/// framebuffer. Keep this policy separate from the shared Rust world target
/// and from deferred/source-derived shader-pack passes.
pub(super) fn private_dh_source_raster_policy(pass: WorldLodPassClass) -> (BlendMode, CompareOp, bool) {
    match pass {
        WorldLodPassClass::Opaque => (BlendMode::Disabled, CompareOp::Less, true),
        // Frozen enters the transparent pass by installing
        // SRC_ALPHA/ONE_MINUS_SRC_ALPHA for RGB and ONE/ONE_MINUS_SRC_ALPHA
        // for alpha. TRANSPARENT_DETAIL changes depth writes but deliberately
        // leaves those blend factors in place, so overlapping side faces
        // accumulate coverage instead of replacing it with the last face.
        WorldLodPassClass::TransparentSide => (BlendMode::Alpha, CompareOp::Less, false),
        WorldLodPassClass::TransparentUp => (BlendMode::Alpha, CompareOp::Less, true),
        WorldLodPassClass::WaterSurface => (BlendMode::AlphaSource, CompareOp::Always, true),
    }
}

/// Frozen replays upward water after the transparent pass has replaced the
/// initial alpha source factors with the accumulated transparent blend state.
pub(super) fn private_dh_water_replay_blend_mode() -> BlendMode {
    BlendMode::Alpha
}

/// The private DH attachment is a sparse replace source. The compositor
/// fragment discards its clear pixels, so the pipeline must not blend covered
/// pixels with the already-rendered vanilla target. Keep this decision named
/// and tested against Frozen's `DhApplyShader` contract.
pub(super) fn private_dh_compositor_blend_mode() -> BlendMode {
    BlendMode::Disabled
}

pub(super) fn private_dh_compositor_depth_compare() -> Option<CompareOp> {
    let enabled = matches!(
        crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COMPOSITE_DEPTH_TEST").as_deref(),
        Ok("1") | Ok("true") | Ok("TRUE")
    ) && matches!(
        crate::core::environment::var("MATTMC_GRAPHICS_AUDIT").as_deref(),
        Ok("1") | Ok("true") | Ok("TRUE")
    );
    if !enabled {
        return None;
    }
    let greater = matches!(
        crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COMPOSITE_DEPTH_GREATER").as_deref(),
        Ok("1") | Ok("true") | Ok("TRUE")
    );
    let strict_less = matches!(
        crate::core::environment::var("MATTMC_CAPTURE_DH_PRIVATE_COMPOSITE_DEPTH_LESS").as_deref(),
        Ok("1") | Ok("true") | Ok("TRUE")
    );
    Some(if greater {
        CompareOp::Greater
    } else if strict_less {
        CompareOp::Less
    } else {
        CompareOp::LessOrEqual
    })
}

/// Compatibility wrapper for the first Rust-owned DH opaque material pass.
/// The shared internal owner keeps opaque and transparent resource policy
/// physically separate while avoiding producer-specific duplicate plumbing.
pub(crate) struct WorldLodOpaquePassResources {
    pub(in crate::render::worldrender::lod) inner: WorldLodPassResources,
}

impl Default for WorldLodOpaquePassResources {
    fn default() -> Self {
        Self {
            inner: WorldLodPassResources::new(WorldLodPassClass::Opaque),
        }
    }
}

impl WorldLodOpaquePassResources {
    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodOpaqueDraw,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedDraw> {
        if draw.pass != WorldLodPassClass::Opaque
            || draw.material_contract != WorldLodMaterialContract::OPAQUE
        {
            return Err(GalError::invalid_argument(
                "world LOD opaque pass received a non-opaque admitted draw",
            ));
        }
        self.inner.stage_draw(
            gal,
            draw.draw,
            draw.uniforms,
            draw.material_contract,
            lightmap,
            ops,
        )
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.inner.destroy(gal);
    }

    pub(crate) fn retain_lightmap_binding(
        &mut self,
        gal: &mut VulkanicGal,
        binding: VanillaLightmapBinding,
    ) {
        self.inner.retain_lightmap_binding(gal, binding);
    }

    pub(crate) fn clear_lightmap_bindings(&mut self, gal: &mut VulkanicGal) {
        self.inner.clear_lightmap_bindings(gal);
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        self.inner.reconcile_assets(gal, assets);
    }
}

/// Forward-color DH opaque resources used by vanilla Rust Vulkan when no
/// shader-pack G-buffer is active. This keeps the deferred four-target pass
/// separate from the one-target presentation pass.
pub(crate) struct WorldLodForwardOpaquePassResources {
    pub(in crate::render::worldrender::lod) inner: WorldLodPassResources,
}

impl Default for WorldLodForwardOpaquePassResources {
    fn default() -> Self {
        Self {
            inner: WorldLodPassResources::new_forward(WorldLodPassClass::Opaque),
        }
    }
}

impl WorldLodForwardOpaquePassResources {
    pub(crate) fn begin_frame(&mut self) {
        self.inner.begin_frame();
    }

    pub(crate) fn flush_packed_uniforms(&mut self, ops: &mut Vec<CommandOp>) {
        self.inner.flush_packed_uniforms(ops);
    }

    pub(crate) fn set_color_format(
        &mut self,
        gal: &mut VulkanicGal,
        format: TextureFormat,
    ) -> GalResult<()> {
        self.inner.set_forward_color_format(gal, format)
    }

    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodOpaqueDraw,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedDraw> {
        if draw.pass != WorldLodPassClass::Opaque
            || draw.material_contract != WorldLodMaterialContract::OPAQUE
        {
            return Err(GalError::invalid_argument(
                "world LOD forward opaque pass received a non-opaque admitted draw",
            ));
        }
        self.inner.stage_draw(
            gal,
            draw.draw,
            draw.uniforms,
            draw.material_contract,
            lightmap,
            ops,
        )
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.inner.destroy(gal);
    }

    pub(crate) fn retain_lightmap_binding(
        &mut self,
        gal: &mut VulkanicGal,
        binding: VanillaLightmapBinding,
    ) {
        self.inner.retain_lightmap_binding(gal, binding);
    }

    pub(crate) fn clear_lightmap_bindings(&mut self, gal: &mut VulkanicGal) {
        self.inner.clear_lightmap_bindings(gal);
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        self.inner.reconcile_assets(gal, assets);
    }
}

/// Rust-owned non-water transparent DH pass resources. The public type keeps
/// DH's side/detail and upward transparent buckets on separate pipelines so
/// their blend/depth state cannot be inferred from a shared layer or leak
/// across the private compositor; water is intentionally not admitted here.
pub(crate) struct WorldLodTransparentPassResources {
    pub(in crate::render::worldrender::lod) inner_side: WorldLodPassResources,
    pub(in crate::render::worldrender::lod) inner_up: WorldLodPassResources,
}

impl Default for WorldLodTransparentPassResources {
    fn default() -> Self {
        Self {
            inner_side: WorldLodPassResources::new(WorldLodPassClass::TransparentSide),
            inner_up: WorldLodPassResources::new(WorldLodPassClass::TransparentUp),
        }
    }
}

impl WorldLodTransparentPassResources {
    pub(crate) fn begin_frame(&mut self) {
        self.inner_side.begin_frame();
        self.inner_up.begin_frame();
    }

    pub(crate) fn flush_packed_uniforms(&mut self, ops: &mut Vec<CommandOp>) {
        self.inner_side.flush_packed_uniforms(ops);
        self.inner_up.flush_packed_uniforms(ops);
    }

    pub(crate) fn set_color_format(
        &mut self,
        gal: &mut VulkanicGal,
        format: TextureFormat,
    ) -> GalResult<()> {
        self.inner_side.set_forward_color_format(gal, format)?;
        self.inner_up.set_forward_color_format(gal, format)
    }

    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodTransparentDraw,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedDraw> {
        match (draw.pass, draw.material_contract) {
            (WorldLodPassClass::TransparentSide, WorldLodMaterialContract::TRANSPARENT_SIDE) => {
                self.inner_side.stage_draw(
                    gal,
                    draw.draw,
                    draw.uniforms,
                    draw.material_contract,
                    lightmap,
                    ops,
                )
            }
            (WorldLodPassClass::TransparentUp, WorldLodMaterialContract::TRANSPARENT_UP) => {
                self.inner_up.stage_draw(
                    gal,
                    draw.draw,
                    draw.uniforms,
                    draw.material_contract,
                    lightmap,
                    ops,
                )
            }
            _ => Err(GalError::invalid_argument(
                "world LOD transparent pass received a non-transparent admitted draw",
            )),
        }
    }

    /// Frozen OpenGL renders every transparent bucket under the inherited
    /// TRANSPARENT state. The Rust planner keeps side/up/water identities
    /// distinct, but the ordinary forward compositor must lower all three to
    /// that one source raster policy rather than inheriting the Java Vulkan
    /// compatibility renderer's water-specific replay.
    pub(crate) fn stage_frozen_opengl_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodGpuDraw,
        uniforms: WorldLodDrawUniform,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedDraw> {
        self.inner_up.stage_draw(
            gal,
            draw,
            uniforms,
            WorldLodMaterialContract::TRANSPARENT_UP,
            lightmap,
            ops,
        )
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.inner_side.destroy(gal);
        self.inner_up.destroy(gal);
    }

    pub(crate) fn retain_lightmap_binding(
        &mut self,
        gal: &mut VulkanicGal,
        binding: VanillaLightmapBinding,
    ) {
        self.inner_side.retain_lightmap_binding(gal, binding);
        self.inner_up.retain_lightmap_binding(gal, binding);
    }

    pub(crate) fn clear_lightmap_bindings(&mut self, gal: &mut VulkanicGal) {
        self.inner_side.clear_lightmap_bindings(gal);
        self.inner_up.clear_lightmap_bindings(gal);
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        self.inner_side.reconcile_assets(gal, assets);
        self.inner_up.reconcile_assets(gal, assets);
    }
}

/// Rust-owned DH water-surface pass. It shares the immutable copied geometry
/// and lightmap ownership with other LOD materials, while retaining DH's
/// water-specific cull/depth/blend policy in its own private pipeline.
pub(crate) struct WorldLodWaterPassResources {
    pub(in crate::render::worldrender::lod) inner: WorldLodPassResources,
}

impl Default for WorldLodWaterPassResources {
    fn default() -> Self {
        Self {
            inner: WorldLodPassResources::new(WorldLodPassClass::WaterSurface),
        }
    }
}

impl WorldLodWaterPassResources {
    pub(crate) fn begin_frame(&mut self) {
        self.inner.begin_frame();
    }

    pub(crate) fn flush_packed_uniforms(&mut self, ops: &mut Vec<CommandOp>) {
        self.inner.flush_packed_uniforms(ops);
    }

    pub(crate) fn set_color_format(
        &mut self,
        gal: &mut VulkanicGal,
        format: TextureFormat,
    ) -> GalResult<()> {
        self.inner.set_forward_color_format(gal, format)
    }

    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodWaterDraw,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedDraw> {
        if draw.pass != WorldLodPassClass::WaterSurface
            || draw.material_contract != WorldLodMaterialContract::WATER_SURFACE
        {
            return Err(GalError::invalid_argument(
                "world LOD water pass received a non-water admitted draw",
            ));
        }
        self.inner.stage_draw(
            gal,
            draw.draw,
            draw.uniforms,
            draw.material_contract,
            lightmap,
            ops,
        )
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.inner.destroy(gal);
    }

    pub(crate) fn retain_lightmap_binding(
        &mut self,
        gal: &mut VulkanicGal,
        binding: VanillaLightmapBinding,
    ) {
        self.inner.retain_lightmap_binding(gal, binding);
    }

    pub(crate) fn clear_lightmap_bindings(&mut self, gal: &mut VulkanicGal) {
        self.inner.clear_lightmap_bindings(gal);
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        self.inner.reconcile_assets(gal, assets);
    }
}

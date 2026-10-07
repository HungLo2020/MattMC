//! Exact-atlas LOD passes (built-in and pack source) and their pipelines.

use crate::render::worldrender::lod::*;

/// A generation-bound owned terrain-atlas binding. The frontend derives this
/// from its resource-pack snapshot; Java/DH GL texture state never enters the
/// LOD pass.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodTerrainAtlasBinding {
    pub mesh_generation: u64,
    pub texture_view: Handle,
    pub sampler: Handle,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodExactAtlasPreparedDraw {
    pub pipeline: Handle,
    pub offscreen_pipeline: Option<Handle>,
    pub offscreen_replay_pipeline: Option<Handle>,
    pub pipeline_layout: Handle,
    pub geometry_resource_set: Handle,
    pub atlas_and_lightmap_resource_set: Handle,
    pub index_buffer: Handle,
    pub index_type: IndexType,
    pub index_count: u32,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodExactAtlasBindingKey {
    pub(in crate::render::worldrender::lod) mesh_generation: u64,
    pub(in crate::render::worldrender::lod) atlas_view: Handle,
    pub(in crate::render::worldrender::lod) atlas_sampler: Handle,
    pub(in crate::render::worldrender::lod) lightmap: WorldLodLightmapResourceKey,
}

pub(super) struct WorldLodExactAtlasPipelineResources {
    pub(in crate::render::worldrender::lod) vertex_shader: Handle,
    pub(in crate::render::worldrender::lod) fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) geometry_and_frame_layout: Handle,
    pub(in crate::render::worldrender::lod) atlas_and_lightmap_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline: Handle,
    /// Direct-route variant for the Rust-owned DH color/depth boundary. The
    /// shared forward pipeline remains a separate target/state contract.
    pub(in crate::render::worldrender::lod) offscreen_pipeline: Option<Handle>,
    pub(in crate::render::worldrender::lod) offscreen_replay_pipeline: Option<Handle>,
}

impl WorldLodExactAtlasPipelineResources {
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
            self.atlas_and_lightmap_layout,
            self.geometry_and_frame_layout,
            self.fragment_shader,
            self.vertex_shader,
        ] {
            let _ = gal.retire(handle);
        }
    }
}

/// Material state for a provenance-resolved exact-atlas DH pass. The vertex
/// and descriptor contracts are shared, while blend/cull/depth state remains
/// explicit per DH material layer. Keeping this state in one owner avoids
/// duplicating resource lifetime code without making transparent geometry use
/// opaque raster policy.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(crate) enum WorldLodExactAtlasPassKind {
    Opaque,
    TransparentSide,
    TransparentUp,
    WaterSurface,
}

impl WorldLodExactAtlasPassKind {
    pub(super) fn expected_layer(self) -> u32 {
        match self {
            Self::Opaque => WORLD_LOD_LAYER_OPAQUE,
            Self::TransparentSide => WORLD_LOD_LAYER_TRANSPARENT_SIDE,
            Self::TransparentUp => WORLD_LOD_LAYER_TRANSPARENT_UP,
            Self::WaterSurface => WORLD_LOD_LAYER_TRANSPARENT_WATER_UP,
        }
    }

    pub(super) fn label(self) -> &'static str {
        match self {
            Self::Opaque => "opaque",
            Self::TransparentSide => "transparent-side",
            Self::TransparentUp => "transparent-up",
            Self::WaterSurface => "water-surface",
        }
    }

    pub(super) fn shared_raster_policy(self) -> (BlendMode, CullMode, CompareOp, bool) {
        match self {
            Self::Opaque => (
                BlendMode::Disabled,
                CullMode::Back,
                CompareOp::LessOrEqual,
                true,
            ),
            Self::TransparentSide => (
                BlendMode::Alpha,
                CullMode::Back,
                CompareOp::LessOrEqual,
                false,
            ),
            Self::TransparentUp => (
                BlendMode::Alpha,
                CullMode::Back,
                CompareOp::LessOrEqual,
                false,
            ),
            Self::WaterSurface => (
                BlendMode::AlphaSource,
                CullMode::None,
                CompareOp::LessOrEqual,
                false,
            ),
        }
    }

    pub(super) fn private_raster_policy(self) -> (BlendMode, CompareOp, bool) {
        match self {
            Self::Opaque => (BlendMode::Disabled, CompareOp::Less, true),
            Self::TransparentSide => (BlendMode::Alpha, CompareOp::Less, false),
            Self::TransparentUp => (BlendMode::Alpha, CompareOp::Less, true),
            Self::WaterSurface => (BlendMode::AlphaSource, CompareOp::Always, true),
        }
    }
}

/// Private exact-atlas DH pass. It is intentionally unable to receive
/// incomplete source ranges: callers construct it only from
/// `WorldLodTexturedGpuDraw`, whose source ordinal has already been paired to
/// complete copied material provenance. Partial transparent ranges stay on
/// their single reduced-color draw so alpha ordering cannot be changed by
/// splitting one source segment into two passes.
pub(crate) struct WorldLodExactAtlasPassResources {
    pub(in crate::render::worldrender::lod) pipeline: Option<WorldLodExactAtlasPipelineResources>,
    pub(in crate::render::worldrender::lod) draws: BTreeMap<WorldLodDrawResourceKey, WorldLodDrawResources>,
    pub(in crate::render::worldrender::lod) material_sets: BTreeMap<WorldLodExactAtlasBindingKey, Handle>,
    pub(in crate::render::worldrender::lod) deferred: bool,
    pub(in crate::render::worldrender::lod) color_format: Option<TextureFormat>,
    pub(in crate::render::worldrender::lod) pass: WorldLodExactAtlasPassKind,
}

impl Default for WorldLodExactAtlasPassResources {
    fn default() -> Self {
        Self {
            pipeline: None,
            draws: BTreeMap::new(),
            material_sets: BTreeMap::new(),
            deferred: true,
            color_format: Some(SHADER_G_BUFFER_COLOR_FORMAT),
            pass: WorldLodExactAtlasPassKind::Opaque,
        }
    }
}

impl WorldLodExactAtlasPassResources {
    pub(crate) fn new_deferred_transparent_side() -> Self {
        Self::new_deferred_for(WorldLodExactAtlasPassKind::TransparentSide)
    }

    pub(crate) fn new_deferred_transparent_up() -> Self {
        Self::new_deferred_for(WorldLodExactAtlasPassKind::TransparentUp)
    }

    pub(crate) fn new_deferred_water_surface() -> Self {
        Self::new_deferred_for(WorldLodExactAtlasPassKind::WaterSurface)
    }

    pub(crate) fn new_forward() -> Self {
        Self::new_forward_for(WorldLodExactAtlasPassKind::Opaque)
    }

    pub(crate) fn new_forward_transparent_side() -> Self {
        Self::new_forward_for(WorldLodExactAtlasPassKind::TransparentSide)
    }

    pub(crate) fn new_forward_transparent_up() -> Self {
        Self::new_forward_for(WorldLodExactAtlasPassKind::TransparentUp)
    }

    pub(crate) fn new_forward_water_surface() -> Self {
        Self::new_forward_for(WorldLodExactAtlasPassKind::WaterSurface)
    }

    pub(super) fn new_deferred_for(pass: WorldLodExactAtlasPassKind) -> Self {
        Self {
            pipeline: None,
            draws: BTreeMap::new(),
            material_sets: BTreeMap::new(),
            deferred: true,
            color_format: Some(SHADER_G_BUFFER_COLOR_FORMAT),
            pass,
        }
    }

    pub(super) fn new_forward_for(pass: WorldLodExactAtlasPassKind) -> Self {
        Self {
            pipeline: None,
            draws: BTreeMap::new(),
            material_sets: BTreeMap::new(),
            deferred: false,
            color_format: None,
            pass,
        }
    }

    pub(crate) fn set_color_format(
        &mut self,
        gal: &mut VulkanicGal,
        format: TextureFormat,
    ) -> GalResult<()> {
        if self.deferred {
            return Err(GalError::invalid_argument(
                "deferred exact-atlas pass cannot change target format",
            ));
        }
        if self.pipeline.is_some() && self.color_format != Some(format) {
            // A frame-target format change (shader toggle, swapchain
            // recreation) rebuilds the format-bound pipeline and sets.
            self.destroy(gal);
        }
        self.color_format = Some(format);
        Ok(())
    }

    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        draw: WorldLodTexturedGpuDraw,
        uniforms: WorldLodDrawUniform,
        atlas: WorldLodTerrainAtlasBinding,
        lightmap: VanillaLightmapBinding,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodExactAtlasPreparedDraw> {
        if draw.layer != self.pass.expected_layer() {
            return Err(GalError::invalid_argument(format!(
                "world LOD exact-atlas {} pass received layer {}",
                self.pass.label(),
                draw.layer
            )));
        }
        if atlas.mesh_generation == 0
            || lightmap.world_generation == 0
            || lightmap.lightmap_generation == 0
        {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas pass requires complete atlas and lightmap generations",
            ));
        }
        self.ensure_pipeline(gal)?;
        let key = WorldLodDrawResourceKey {
            column_key: draw.column_key,
            column_generation: draw.column_generation,
            layer: draw.layer,
            segment_index: draw.source_segment_index,
        };
        if !self.draws.contains_key(&key) {
            let pipeline = self.pipeline.as_ref().expect("exact-atlas pipeline exists");
            let uniform_buffer = gal.create_buffer(BufferDesc {
                label: format!(
                    "world-lod-exact-atlas-column{}-gen{}-segment{}.frame",
                    key.column_key, key.column_generation, key.segment_index
                ),
                size: 240,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
            })?;
            let resource_set = match gal.create_resource_set(ResourceSetDesc {
                label: format!(
                    "world-lod-exact-atlas-column{}-gen{}-segment{}.geometry-and-frame-set",
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
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                ],
            }) {
                Ok(set) => set,
                Err(error) => {
                    let _ = gal.retire(uniform_buffer);
                    return Err(error);
                }
            };
            self.draws.insert(
                key,
                WorldLodDrawResources {
                    uniform_buffer: Some(uniform_buffer),
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
                .expect("exact-atlas draw resources exist");
            let uniform_changed = resources.last_uniform_bytes != Some(packed_uniform);
            if uniform_changed {
                resources.last_uniform_bytes = Some(packed_uniform);
            }
            (
                resources.uniform_buffer,
                resources.resource_set,
                uniform_changed,
            )
        };
        let material_set = self.ensure_material_set(gal, atlas, lightmap)?;
        if uniform_changed {
            let uniform_buffer = uniform_buffer.expect("exact-atlas uniform buffer exists");
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
            .expect("exact-atlas pipeline remains alive");
        Ok(WorldLodExactAtlasPreparedDraw {
            pipeline: pipeline.pipeline,
            offscreen_pipeline: pipeline.offscreen_pipeline,
            offscreen_replay_pipeline: pipeline.offscreen_replay_pipeline,
            pipeline_layout: pipeline.pipeline_layout,
            geometry_resource_set,
            atlas_and_lightmap_resource_set: material_set,
            index_buffer: draw.index_buffer,
            index_type: draw.index_type,
            index_count: draw.index_count,
        })
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodTexturedGpuColumnAsset>,
    ) {
        let stale = self
            .draws
            .keys()
            .filter(|key| {
                assets.get(&key.column_key).is_none_or(|asset| {
                    asset.column_generation != key.column_generation
                        || !asset
                            .segments
                            .iter()
                            .any(|segment| segment.source_segment_index == key.segment_index)
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

    pub(crate) fn retain_bindings(
        &mut self,
        gal: &mut VulkanicGal,
        atlas: WorldLodTerrainAtlasBinding,
        lightmap: VanillaLightmapBinding,
    ) {
        let retained = WorldLodExactAtlasBindingKey {
            mesh_generation: atlas.mesh_generation,
            atlas_view: atlas.texture_view,
            atlas_sampler: atlas.sampler,
            lightmap: lightmap.into(),
        };
        let stale = self
            .material_sets
            .keys()
            .filter(|key| **key != retained)
            .copied()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(set) = self.material_sets.remove(&key) {
                let _ = gal.retire(set);
            }
        }
    }

    pub(crate) fn clear_bindings(&mut self, gal: &mut VulkanicGal) {
        for (_, set) in std::mem::take(&mut self.material_sets) {
            let _ = gal.retire(set);
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.draws) {
            resources.destroy(gal);
        }
        self.clear_bindings(gal);
        if let Some(pipeline) = self.pipeline.take() {
            pipeline.destroy(gal);
        }
    }

    pub(super) fn ensure_pipeline(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        if self.pipeline.is_some() {
            return Ok(());
        }
        let label = format!("world-lod-exact-atlas-{}", self.pass.label());
        let [geometry_and_frame_desc, atlas_and_lightmap_desc] =
            distant_horizons_lod_exact_atlas_resource_layouts(&label);
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldLodExactAtlasPipelineResources> {
            let geometry_and_frame_layout = gal.create_resource_layout(geometry_and_frame_desc)?;
            created.push(geometry_and_frame_layout);
            let atlas_and_lightmap_layout = gal.create_resource_layout(atlas_and_lightmap_desc)?;
            created.push(atlas_and_lightmap_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![geometry_and_frame_layout, atlas_and_lightmap_layout],
            })?;
            created.push(pipeline_layout);
            let [vertex_desc, fragment_desc] = if self.deferred {
                minimal_distant_horizons_lod_exact_atlas_opaque_program()
                    .shader_module_descriptors(gal.capabilities().shader_conventions)
            } else {
                minimal_distant_horizons_lod_exact_atlas_forward_opaque_program()
                    .shader_module_descriptors(gal.capabilities().shader_conventions)
            };
            let vertex_shader = gal.create_shader_module(vertex_desc)?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(fragment_desc)?;
            created.push(fragment_shader);
            let (blend, cull_mode, depth_compare, depth_write) = self.pass.shared_raster_policy();
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: if matches!(selected_source_raster_probe_cull_mode()?, CullMode::None) {
                    CullMode::None
                } else {
                    cull_mode
                },
                front_face: selected_source_raster_probe_front_face(world_lod_source_front_face())?,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend,
                depth_compare: selected_source_raster_probe_depth_compare(Some(depth_compare))?,
                depth_write,
                depth_bias: None,
                color_formats: if self.deferred {
                    vec![SHADER_G_BUFFER_COLOR_FORMAT; 4]
                } else {
                    vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)]
                },
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            let offscreen_pipeline = if !self.deferred {
                // The direct DH compositor owns a separate color/depth
                // attachment. Keep its exact-atlas state explicit rather than
                // reusing the shared forward pipeline: opaque LODs use the
                // source no-shader LESS/depth-write contract in that private
                // domain, while the shared target retains LEQUAL semantics.
                let (offscreen_blend, offscreen_depth_compare, offscreen_depth_write) =
                    self.pass.private_raster_policy();
                let offscreen_pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                    label: format!("{label}.offscreen.pipeline"),
                    layout: pipeline_layout,
                    vertex_shader,
                    fragment_shader,
                    topology: PrimitiveTopology::Triangles,
                    cull_mode: if matches!(
                        selected_source_raster_probe_cull_mode()?,
                        CullMode::None
                    ) {
                        CullMode::None
                    } else {
                        cull_mode
                    },
                    front_face: selected_source_raster_probe_front_face(
                        world_lod_source_front_face(),
                    )?,
                    provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                    raster_y_direction: RasterYDirection::Up,
                    blend: offscreen_blend,
                    depth_compare: selected_source_raster_probe_depth_compare(Some(
                        offscreen_depth_compare,
                    ))?,
                    depth_write: offscreen_depth_write,
                    depth_bias: None,
                    color_formats: vec![self.color_format.unwrap_or(TextureFormat::Rgba8Unorm)],
                    depth_format: Some(TextureFormat::Depth32Float),
                    stencil: None,
                })?;
                created.push(offscreen_pipeline);
                Some(offscreen_pipeline)
            } else {
                None
            };
            let offscreen_replay_pipeline =
                if !self.deferred && self.pass == WorldLodExactAtlasPassKind::WaterSurface {
                    let (_, offscreen_depth_compare, offscreen_depth_write) =
                        self.pass.private_raster_policy();
                    let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                        label: format!("{label}.offscreen-transparent-replay.pipeline"),
                        layout: pipeline_layout,
                        vertex_shader,
                        fragment_shader,
                        topology: PrimitiveTopology::Triangles,
                        cull_mode: if matches!(
                            selected_source_raster_probe_cull_mode()?,
                            CullMode::None
                        ) {
                            CullMode::None
                        } else {
                            cull_mode
                        },
                        front_face: selected_source_raster_probe_front_face(
                            world_lod_source_front_face(),
                        )?,
                        provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                        raster_y_direction: RasterYDirection::Up,
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
            Ok(WorldLodExactAtlasPipelineResources {
                vertex_shader,
                fragment_shader,
                geometry_and_frame_layout,
                atlas_and_lightmap_layout,
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

    pub(super) fn ensure_material_set(
        &mut self,
        gal: &mut VulkanicGal,
        atlas: WorldLodTerrainAtlasBinding,
        lightmap: VanillaLightmapBinding,
    ) -> GalResult<Handle> {
        let key = WorldLodExactAtlasBindingKey {
            mesh_generation: atlas.mesh_generation,
            atlas_view: atlas.texture_view,
            atlas_sampler: atlas.sampler,
            lightmap: lightmap.into(),
        };
        if let Some(&set) = self.material_sets.get(&key) {
            return Ok(set);
        }
        let pipeline = self
            .pipeline
            .as_ref()
            .expect("exact-atlas pipeline exists before material set creation");
        let set = gal.create_resource_set(ResourceSetDesc {
            label: format!(
                "world-lod-exact-atlas.mesh{}-lightmap-world{}-gen{}.resource-set",
                key.mesh_generation,
                key.lightmap.world_generation,
                key.lightmap.lightmap_generation
            ),
            layout: pipeline.atlas_and_lightmap_layout,
            bindings: vec![
                ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: atlas.texture_view,
                    kind: ResourceBindingKind::SampledTexture,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 1,
                    array_index: 0,
                    resource: atlas.sampler,
                    kind: ResourceBindingKind::Sampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 2,
                    array_index: 0,
                    resource: lightmap.texture_view,
                    kind: ResourceBindingKind::SampledTexture,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 3,
                    array_index: 0,
                    resource: lightmap.sampler,
                    kind: ResourceBindingKind::Sampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
            ],
        })?;
        self.material_sets.insert(key, set);
        Ok(set)
    }
}

/// Compatibility name retained for the deferred/source opaque owner and its
/// existing tests. Transparent direct owners use the same explicit resource
/// implementation with a different pass kind.
pub(crate) type WorldLodExactAtlasOpaquePassResources = WorldLodExactAtlasPassResources;

/// Source-frame counterpart of the regular exact-atlas DH pass. It shares
/// immutable copied geometry and Rust-owned atlas/lightmap semantics, then
/// writes the named terrain color, normal, and material outputs required by
/// later selected-source shader-pack stages. The selected DH program still
/// owns unresolved reduced-color ranges; this owner never guesses a texture
/// from a DH material category.
#[derive(Default)]
pub(crate) struct WorldLodExactAtlasSourcePassResources {
    pub(in crate::render::worldrender::lod) pipelines:
        BTreeMap<WorldLodExactAtlasSourcePipelineKey, WorldLodExactAtlasSourcePipelineResources>,
    pub(in crate::render::worldrender::lod) draws: BTreeMap<WorldLodExactAtlasSourceDrawKey, WorldLodExactAtlasSourceDrawResources>,
    pub(in crate::render::worldrender::lod) material_sets: BTreeMap<WorldLodExactAtlasSourceBindingKey, Handle>,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodExactAtlasSourceDrawKey {
    pub(in crate::render::worldrender::lod) pipeline: WorldLodExactAtlasSourcePipelineKey,
    pub(in crate::render::worldrender::lod) draw: WorldLodDrawResourceKey,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodExactAtlasSourceBindingKey {
    pub(in crate::render::worldrender::lod) pipeline: WorldLodExactAtlasSourcePipelineKey,
    pub(in crate::render::worldrender::lod) mesh_generation: u64,
    pub(in crate::render::worldrender::lod) atlas_view: Handle,
    pub(in crate::render::worldrender::lod) atlas_sampler: Handle,
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodExactAtlasSourcePipelineKey {
    pub(in crate::render::worldrender::lod) identity: String,
    pub(in crate::render::worldrender::lod) shader_pack_generation: u64,
    pub(in crate::render::worldrender::lod) primary_format: TextureFormat,
    pub(in crate::render::worldrender::lod) pack_resources_layout: Handle,
    pub(in crate::render::worldrender::lod) front_face: FrontFace,
}

pub(super) struct WorldLodExactAtlasSourcePipelineResources {
    pub(in crate::render::worldrender::lod) vertex_shader: Handle,
    pub(in crate::render::worldrender::lod) fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) source_data_layout: Handle,
    pub(in crate::render::worldrender::lod) pack_resources_layout: Handle,
    pub(in crate::render::worldrender::lod) exact_atlas_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline: Handle,
}

impl WorldLodExactAtlasSourcePipelineResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.exact_atlas_layout,
            self.source_data_layout,
            self.fragment_shader,
            self.vertex_shader,
        ] {
            let _ = gal.retire(handle);
        }
    }
}

pub(super) struct WorldLodExactAtlasSourceDrawResources {
    pub(in crate::render::worldrender::lod) column_frame_buffer: Handle,
    pub(in crate::render::worldrender::lod) scalar_uniform_buffer: Option<Handle>,
    pub(in crate::render::worldrender::lod) source_data_set: Handle,
}

impl WorldLodExactAtlasSourceDrawResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        let _ = gal.retire(self.source_data_set);
        if let Some(buffer) = self.scalar_uniform_buffer {
            let _ = gal.retire(buffer);
        }
        let _ = gal.retire(self.column_frame_buffer);
    }
}

impl WorldLodExactAtlasSourcePassResources {
    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsExactAtlasSourceProgram,
        color_attachment: &TerrainSourceColorAttachment,
        pack_resources_layout: Handle,
        draw: WorldLodTexturedGpuDraw,
        uniforms: WorldLodDrawUniform,
        atlas: WorldLodTerrainAtlasBinding,
        source_uniforms: &TerrainSourceUniformFrame,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedSourceDraw> {
        if draw.layer != WORLD_LOD_LAYER_OPAQUE {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas source pass accepts opaque segments only",
            ));
        }
        if atlas.mesh_generation == 0 {
            return Err(GalError::invalid_argument(
                "world LOD exact-atlas source pass requires a complete atlas generation",
            ));
        }
        let pipeline_key = exact_atlas_source_pipeline_key(
            program,
            color_attachment,
            pack_resources_layout,
        )?;
        self.ensure_pipeline(gal, program, &pipeline_key)?;
        let scalar_uniforms = program.source.pack_scalar_uniforms(source_uniforms)?;
        let draw_key = WorldLodExactAtlasSourceDrawKey {
            pipeline: pipeline_key.clone(),
            draw: WorldLodDrawResourceKey {
                column_key: draw.column_key,
                column_generation: draw.column_generation,
                layer: draw.layer,
                segment_index: draw.source_segment_index,
            },
        };
        if !self.draws.contains_key(&draw_key) {
            let pipeline = self
                .pipelines
                .get(&pipeline_key)
                .expect("exact-atlas source pipeline exists");
            let column_frame_buffer = gal.create_buffer(BufferDesc {
                label: format!(
                    "world-lod-exact-atlas-source-column{}-gen{}-segment{}.frame",
                    draw_key.draw.column_key,
                    draw_key.draw.column_generation,
                    draw_key.draw.segment_index
                ),
                size: 240,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
            })?;
            let scalar_uniform_buffer =
                if program.source.execution_interface.scalar_uniforms.is_some() {
                    Some(gal.create_buffer(BufferDesc {
                        label: format!(
                            "world-lod-exact-atlas-source-column{}-gen{}-segment{}.scalars",
                            draw_key.draw.column_key,
                            draw_key.draw.column_generation,
                            draw_key.draw.segment_index
                        ),
                        size: u64::from(program.source.execution_interface.scalar_uniform_bytes),
                        memory: MemoryDomain::Upload,
                        usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
                    })?)
                } else {
                    None
                };
            let mut bindings = vec![
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
                    resource: column_frame_buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: vec![0],
                    buffer_range: Some(u64::from(
                        program.source.execution_interface.column_frame_bytes,
                    )),
                },
            ];
            if let (Some(binding), Some(buffer)) = (
                program.source.execution_interface.scalar_uniforms,
                scalar_uniform_buffer,
            ) {
                bindings.push(ResourceBinding {
                    binding: binding.binding,
                    array_index: 0,
                    resource: buffer,
                    kind: ResourceBindingKind::UniformBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: vec![0],
                    buffer_range: Some(u64::from(
                        program.source.execution_interface.scalar_uniform_bytes,
                    )),
                });
            }
            bindings.sort_by_key(|binding| binding.binding);
            let resource_set = match gal.create_resource_set(ResourceSetDesc {
                label: format!(
                    "world-lod-exact-atlas-source-column{}-gen{}-segment{}.geometry-and-frame-set",
                    draw_key.draw.column_key,
                    draw_key.draw.column_generation,
                    draw_key.draw.segment_index
                ),
                layout: pipeline.source_data_layout,
                bindings,
            }) {
                Ok(set) => set,
                Err(error) => {
                    if let Some(buffer) = scalar_uniform_buffer {
                        let _ = gal.retire(buffer);
                    }
                    let _ = gal.retire(column_frame_buffer);
                    return Err(error);
                }
            };
            self.draws.insert(
                draw_key.clone(),
                WorldLodExactAtlasSourceDrawResources {
                    column_frame_buffer,
                    scalar_uniform_buffer,
                    source_data_set: resource_set,
                },
            );
        }
        let (column_frame_buffer, scalar_uniform_buffer, source_data_set) = self
            .draws
            .get(&draw_key)
            .map(|resources| {
                (
                    resources.column_frame_buffer,
                    resources.scalar_uniform_buffer,
                    resources.source_data_set,
                )
            })
            .expect("exact-atlas source draw resources exist");
        let atlas_set = self.ensure_material_set(gal, &pipeline_key, atlas)?;
        append_source_uniform_upload(
            ops,
            column_frame_buffer,
            uniforms.pack_source_std140().to_vec(),
        );
        if let Some(buffer) = scalar_uniform_buffer {
            append_source_uniform_upload(ops, buffer, scalar_uniforms);
        }
        let pipeline = self
            .pipelines
            .get(&pipeline_key)
            .expect("exact-atlas source pipeline remains alive");
        Ok(WorldLodPreparedSourceDraw {
            pipeline: pipeline.pipeline,
            pipeline_layout: pipeline.pipeline_layout,
            source_data_set,
            source_data_dynamic_offsets: [0, 0],
            source_data_dynamic_offset_count: if program
                .source
                .execution_interface
                .scalar_uniforms
                .is_some()
            {
                2
            } else {
                1
            },
            pack_resources_layout: pipeline.pack_resources_layout,
            source_extra_resource_set: Some(atlas_set),
            index_buffer: draw.index_buffer,
            index_offset: 0,
            index_type: draw.index_type,
            index_count: draw.index_count,
        })
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodTexturedGpuColumnAsset>,
    ) {
        let stale =
            self.draws
                .keys()
                .filter(|key| {
                    assets.get(&key.draw.column_key).is_none_or(|asset| {
                        asset.column_generation != key.draw.column_generation
                            || !asset.segments.iter().any(|segment| {
                                segment.source_segment_index == key.draw.segment_index
                            })
                    })
                })
                .cloned()
                .collect::<Vec<_>>();
        for key in stale {
            if let Some(resources) = self.draws.remove(&key) {
                resources.destroy(gal);
            }
        }
    }

    pub(crate) fn retain_bindings(
        &mut self,
        gal: &mut VulkanicGal,
        atlas: WorldLodTerrainAtlasBinding,
    ) {
        let stale = self
            .material_sets
            .keys()
            .filter(|key| {
                key.mesh_generation != atlas.mesh_generation
                    || key.atlas_view != atlas.texture_view
                    || key.atlas_sampler != atlas.sampler
            })
            .cloned()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(set) = self.material_sets.remove(&key) {
                let _ = gal.retire(set);
            }
        }
    }

    pub(crate) fn clear_bindings(&mut self, gal: &mut VulkanicGal) {
        for (_, set) in std::mem::take(&mut self.material_sets) {
            let _ = gal.retire(set);
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.draws) {
            resources.destroy(gal);
        }
        self.clear_bindings(gal);
        for (_, pipeline) in std::mem::take(&mut self.pipelines) {
            pipeline.destroy(gal);
        }
    }

    pub(super) fn ensure_pipeline(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsExactAtlasSourceProgram,
        pipeline_key: &WorldLodExactAtlasSourcePipelineKey,
    ) -> GalResult<()> {
        if self.pipelines.contains_key(&pipeline_key) {
            return Ok(());
        }
        let label = format!(
            "world-lod-exact-atlas-source-{}",
            pipeline_key.identity.replace(':', "-")
        );
        let layouts = program.source.execution_resource_layouts()?;
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldLodExactAtlasSourcePipelineResources> {
            let source_data_layout = gal.create_resource_layout(layouts.source_data)?;
            created.push(source_data_layout);
            let exact_atlas_layout = gal.create_resource_layout(
                distant_horizons_exact_atlas_source_resource_layout(&label),
            )?;
            created.push(exact_atlas_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![
                    source_data_layout,
                    pipeline_key.pack_resources_layout,
                    exact_atlas_layout,
                ],
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
                cull_mode: CullMode::Back,
                front_face: pipeline_key.front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend: BlendMode::Disabled,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: true,
                depth_bias: None,
                color_formats: vec![pipeline_key.primary_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(WorldLodExactAtlasSourcePipelineResources {
                vertex_shader,
                fragment_shader,
                source_data_layout,
                pack_resources_layout: pipeline_key.pack_resources_layout,
                exact_atlas_layout,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.pipelines.insert(pipeline_key.clone(), result?);
        Ok(())
    }

    pub(super) fn ensure_material_set(
        &mut self,
        gal: &mut VulkanicGal,
        pipeline_key: &WorldLodExactAtlasSourcePipelineKey,
        atlas: WorldLodTerrainAtlasBinding,
    ) -> GalResult<Handle> {
        let key = WorldLodExactAtlasSourceBindingKey {
            pipeline: pipeline_key.clone(),
            mesh_generation: atlas.mesh_generation,
            atlas_view: atlas.texture_view,
            atlas_sampler: atlas.sampler,
        };
        if let Some(&set) = self.material_sets.get(&key) {
            return Ok(set);
        }
        let pipeline = self
            .pipelines
            .get(&pipeline_key)
            .expect("exact-atlas source pipeline exists before material set creation");
        let set = gal.create_resource_set(ResourceSetDesc {
            label: format!(
                "world-lod-exact-atlas-source.mesh{}.resource-set",
                key.mesh_generation,
            ),
            layout: pipeline.exact_atlas_layout,
            bindings: vec![
                ResourceBinding {
                    binding: 0,
                    array_index: 0,
                    resource: atlas.texture_view,
                    kind: ResourceBindingKind::SampledTexture,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
                ResourceBinding {
                    binding: 1,
                    array_index: 0,
                    resource: atlas.sampler,
                    kind: ResourceBindingKind::Sampler,
                    access: AccessFlags::READ,
                    dynamic_offsets: Vec::new(),
                    buffer_range: None,
                },
            ],
        })?;
        self.material_sets.insert(key, set);
        Ok(set)
    }
}

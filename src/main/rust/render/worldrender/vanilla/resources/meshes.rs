//! Mesh pipelines, mesh resources and the built-in terrain lightmap layout.

use super::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn ensure_mesh_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        key: MeshPipelineResourceKey,
    ) -> GalResult<()> {
        let terrain_program = if key.vertex_abi == MeshVertexAbi::DirectTerrain32 {
            let kind = match key.material_mode {
                WORLD_MATERIAL_MODE_OPAQUE => TerrainMaterialProgramKind::Opaque,
                WORLD_MATERIAL_MODE_CUTOUT => TerrainMaterialProgramKind::Cutout,
                WORLD_MATERIAL_MODE_TRANSLUCENT => TerrainMaterialProgramKind::Translucent,
                _ => {
                    return Err(GalError::invalid_argument(
                        "compact direct terrain pipeline requires opaque/cutout/translucent material",
                    ));
                }
            };
            if key.shader_program_identity.as_str()
                == STATIC_COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID
                || key.shader_program_identity.as_str()
                    == STATIC_COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID
                || key.shader_program_identity.as_str()
                    == STATIC_COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID
            {
                minimal_static_compact_direct_terrain_program(kind)
            } else {
                minimal_compact_direct_terrain_program(kind)
            }
        } else if key.shader_program_identity.as_str() == STANDARD_ITEM_FOIL_PROGRAM_ID {
            minimal_direct_standard_item_foil_program()
        } else if key.shader_program_identity.as_str() == WORLD_DECAL_FOIL_PROGRAM_ID {
            minimal_direct_world_decal_foil_program()
        } else {
            terrain_program_for_mode(key.material_mode, key.g_buffer)?
        };
        if key.shader_program_identity != terrain_program.identity {
            return Err(GalError::invalid_argument(
                "builtin mesh pipeline key does not match its terrain program identity",
            ));
        }
        self.ensure_mesh_pipeline_resources_for_program(gal, key, &terrain_program)
    }

    /// Declares the normal material pass's explicit dynamic-lightmap inputs.
    /// This layout owns no image/view/sampler: those are created per copied
    /// lightmap generation by `VanillaLightmapResidency` and therefore cannot
    /// outlive the resource they reference.
    pub(crate) fn ensure_builtin_terrain_lightmap_layout(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<Handle> {
        if let Some(layout) = self.builtin_terrain_lightmap_layout {
            return Ok(layout);
        }
        let layout = gal.create_resource_layout(ResourceLayoutDesc {
            label: "world-builtin-terrain-lightmap.resource-layout".to_string(),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::SampledTexture,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::Sampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        })?;
        self.builtin_terrain_lightmap_layout = Some(layout);
        Ok(layout)
    }

    /// Creates one mesh pipeline from an explicit Rust-owned terrain program.
    /// Normal routes use `ensure_mesh_pipeline_resources`, which supplies the
    /// builtin program above. Source-derived candidates can later use this
    /// same path with their matching semantic resource layout; no backend
    /// state or Java renderer object is involved.
    pub(crate) fn ensure_mesh_pipeline_resources_for_program(
        &mut self,
        gal: &mut VulkanicGal,
        key: MeshPipelineResourceKey,
        terrain_program: &TerrainMaterialProgram,
    ) -> GalResult<()> {
        if key.g_buffer
            && key.raster_y_direction != RasterYDirection::Up
            && key.shader_program_identity
                != terrain_program_for_mode(key.material_mode, true)?.identity
        {
            return Err(GalError::unsupported_feature(
                "source mesh graph requires canonical Up raster direction",
            ));
        }
        if key.shader_program_identity != terrain_program.identity {
            return Err(GalError::invalid_argument(
                "mesh pipeline key does not match the supplied terrain program identity",
            ));
        }
        let standard_foil = matches!(
            key.shader_program_identity.as_str(),
            STANDARD_ITEM_FOIL_PROGRAM_ID | WORLD_DECAL_FOIL_PROGRAM_ID
        );
        if standard_foil && key.material_mode != WORLD_MATERIAL_MODE_GLINT {
            return Err(GalError::invalid_argument(
                "standard foil pipeline requires glint material",
            ));
        }
        if self.mesh_pipeline_resources.contains_key(&key) {
            return Ok(());
        }
        if self.mesh_pipeline_resources.len() >= WORLD_MESH_PIPELINE_RESIDENCY {
            return Err(GalError::unsupported_feature(format!(
                "world mesh pipeline residency exceeds bounded limit {WORLD_MESH_PIPELINE_RESIDENCY}"
            )));
        }
        let label = format!(
            "world-mesh-pipeline-{}-{}-mode{}-depth{}-cull{}-winding{}-gen{}",
            if key.g_buffer { "gbuffer" } else { "direct" },
            key.shader_program_identity.as_str(),
            key.material_mode,
            key.depth_policy,
            key.cull_policy,
            key.winding,
            self.generation
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<MeshPipelineResources> {
            let vertex_observation = if !key.g_buffer
                && diagnostics::vertex_observation::selected(key.shader_program_identity.as_str())
            {
                Some(diagnostics::vertex_observation::Observation::create(gal, &mut created)?)
            } else {
                None
            };
            let vertex_source = if vertex_observation.is_some() {
                diagnostics::vertex_observation::instrument(&terrain_program.vertex.source)?
            } else {
                terrain_program.vertex.source.clone()
            };
            let is_translucent = material_mode_uses_alpha_blending(key.material_mode);
            let is_glint = key.material_mode == WORLD_MATERIAL_MODE_GLINT;
            let is_optical_write = key.material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE;
            let is_optical_test = key.material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST;
            if key.g_buffer && (is_optical_write || is_optical_test) {
                return Err(GalError::unsupported_feature(
                    "optical stencil mesh sections require the private direct hand target",
                ));
            }
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(gal.capabilities().shader_conventions, &vertex_source),
                entry_point: terrain_program.vertex.entry_point.clone(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: terrain_program.fragment.source.as_bytes().to_vec(),
                entry_point: terrain_program.fragment.entry_point.clone(),
            })?;
            created.push(fragment_shader);
            let (shadow_vertex_shader, shadow_fragment_shader) = if key.g_buffer
                && !is_translucent
                && !is_optical_write
                && !is_optical_test
                && !standard_foil
            {
                let shadow_program = minimal_shadow_depth_program();
                let shadow_vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                    label: format!("{label}.shadow.vertex"),
                    stage: ShaderStage::Vertex,
                    code_format: ShaderCodeFormat::Glsl,
                    code: shader_stage_code(
                        gal.capabilities().shader_conventions,
                        &shadow_program.vertex.source,
                    ),
                    entry_point: shadow_program.vertex.entry_point.clone(),
                })?;
                created.push(shadow_vertex_shader);
                let shadow_fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                    label: format!("{label}.shadow.fragment"),
                    stage: ShaderStage::Fragment,
                    code_format: ShaderCodeFormat::Glsl,
                    code: shadow_program.fragment.source.as_bytes().to_vec(),
                    entry_point: shadow_program.fragment.entry_point.clone(),
                })?;
                created.push(shadow_fragment_shader);
                (Some(shadow_vertex_shader), Some(shadow_fragment_shader))
            } else {
                (None, None)
            };
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::StorageBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 1,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::StorageBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 1,
                    },
                    ResourceBindingDesc {
                        binding: 2,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 3,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ]
                .into_iter()
                .chain(standard_foil.then_some(ResourceBindingDesc {
                    binding: 4,
                    kind: ResourceBindingKind::StorageBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 1,
                }))
                .chain(
                    vertex_observation
                        .as_ref()
                        .map(|_| diagnostics::vertex_observation::declaration()),
                )
                .collect(),
            })?;
            created.push(resource_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: mesh_pipeline_layouts(
                    resource_layout,
                    key.shader_resource_layout,
                )?,
            })?;
            created.push(pipeline_layout);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: effective_cull_mode_for_winding(key.cull_policy, key.winding)?,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: key.raster_y_direction,
                blend: if is_optical_write {
                    BlendMode::Alpha
                } else if is_glint {
                    BlendMode::SrcColorAdditive
                } else if is_translucent {
                    BlendMode::Alpha
                } else {
                    BlendMode::Disabled
                },
                depth_compare: if is_optical_write {
                    None
                } else if is_glint {
                    Some(CompareOp::Equal)
                } else {
                    depth_compare_for_policy(key.depth_policy)?
                },
                depth_write: if is_optical_write {
                    false
                } else {
                    matches!(
                        key.depth_policy,
                        WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE
                    ) && !is_glint
                },
                depth_bias: None,
                color_formats: if key.g_buffer && !is_translucent && !standard_foil {
                    vec![SHADER_G_BUFFER_COLOR_FORMAT; 4]
                } else {
                    vec![if key.g_buffer {
                        SHADER_G_BUFFER_COLOR_FORMAT
                    } else {
                        key.color_format
                    }]
                },
                depth_format: Some(if is_optical_write || is_optical_test {
                    TextureFormat::Depth24Stencil8
                } else {
                    TextureFormat::Depth32Float
                }),
                stencil: if is_optical_write {
                    Some(StencilState {
                        front: StencilFaceState::replace(1, 0xff, 0xff),
                        back: StencilFaceState::replace(1, 0xff, 0xff),
                    })
                } else if is_optical_test {
                    Some(StencilState {
                        front: StencilFaceState::keep(CompareOp::Equal, 1, 0xff),
                        back: StencilFaceState::keep(CompareOp::Equal, 1, 0xff),
                    })
                } else {
                    None
                },
            })?;
            created.push(pipeline);
            let shadow_pipeline = if let (Some(vertex_shader), Some(fragment_shader)) =
                (shadow_vertex_shader, shadow_fragment_shader)
            {
                let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                    label: format!("{label}.shadow-pipeline"),
                    layout: pipeline_layout,
                    vertex_shader,
                    fragment_shader,
                    topology: PrimitiveTopology::Triangles,
                    cull_mode: effective_cull_mode_for_winding(key.cull_policy, key.winding)?,
                    front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                    provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                    raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                    blend: BlendMode::Disabled,
                    depth_compare: Some(CompareOp::LessOrEqual),
                    depth_write: true,
                    depth_bias: None,
                    color_formats: vec![SHADER_G_BUFFER_COLOR_FORMAT; 2],
                    depth_format: Some(TextureFormat::Depth32Float),
                    stencil: None,
                })?;
                created.push(pipeline);
                Some(pipeline)
            } else {
                None
            };
            Ok(MeshPipelineResources {
                vertex_observation,
                vertex_shader,
                fragment_shader,
                shadow_vertex_shader,
                shadow_fragment_shader,
                resource_layout,
                pipeline_layout,
                pipeline,
                shadow_pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        let resources = result?;
        if standard_foil && crate::core::environment::var_os("MATTMC_STANDARD_FOIL_TRACE").is_some() {
            crate::core::console::stderr(format_args!(
                "standard-foil.pipeline-created program={} mode={} depth={} generation={}",
                STANDARD_ITEM_FOIL_PROGRAM_ID, key.material_mode, key.depth_policy, self.generation
            ));
        }
        self.mesh_pipeline_resources.insert(key, resources);
        Ok(())
    }

    pub(crate) fn ensure_mesh_resources(
        &mut self,
        gal: &mut VulkanicGal,
        key: MeshResourceKey,
    ) -> GalResult<()> {
        // Builtin indexed-mesh programs share a copied-lightmap layout;
        // standard foil deliberately does not sample it. Source variants build their own
        // program resources afterwards, but their base mesh cache remains
        // compatible with the normal Rust route when selection changes.
        let lightmap_layout = self.ensure_builtin_terrain_lightmap_layout(gal)?;
        self.ensure_mesh_resources_with_shader_resource_layout(gal, key, Some(lightmap_layout))
    }

    pub(crate) fn ensure_mesh_resources_with_shader_resource_layout(
        &mut self,
        gal: &mut VulkanicGal,
        key: MeshResourceKey,
        shader_resource_layout: Option<Handle>,
    ) -> GalResult<()> {
        if key.standard_item_foil {
            let texture = self
                .mesh_texture_assets
                .get(&key.texture_id)
                .ok_or_else(|| {
                    GalError::invalid_argument("standard foil requires an owned texture asset")
                })?;
            if texture.sampling.is_none() || texture.requested_mip_levels != 1 {
                return Err(GalError::invalid_argument(
                    "standard foil requires explicit resource sampling and one mip level",
                ));
            }
        }
        if self.mesh_resources.contains_key(&key) {
            return Ok(());
        }
        if self.mesh_resources.len() >= WORLD_MESH_RESOURCE_RESIDENCY {
            return Err(GalError::backend(format!(
                "world mesh GPU resource residency exceeds bounded limit {WORLD_MESH_RESOURCE_RESIDENCY}"
            )));
        }
        let asset = self.mesh_assets.get(&key.mesh_key).ok_or_else(|| {
            GalError::invalid_argument(format!("world mesh asset {} is missing", key.mesh_key))
        })?;
        let section = asset
            .sections
            .get(key.section_index as usize)
            .ok_or_else(|| GalError::invalid_argument("world mesh section is missing"))?;
        let label = format!(
            "world-mesh-{}-stratum{}-{}-gen{}-section{}-texture{}-mode{}-depth{}-cull{}",
            if key.g_buffer { "gbuffer" } else { "direct" },
            key.stratum,
            key.mesh_key,
            key.mesh_generation,
            key.section_index,
            key.texture_id,
            key.material_mode,
            key.depth_policy,
            key.cull_policy
        );
        let vertex_abi = mesh_vertex_abi_for_builtin(key);
        let vertex_bytes = match vertex_abi {
            MeshVertexAbi::Rich80 => asset.vertex_bytes.clone(),
            MeshVertexAbi::DirectTerrain32 => compact_direct_terrain_vertices(&asset.vertex_bytes)?,
        };
        let index_bytes = asset.index_bytes.clone();
        let index_type = asset.index_type;
        let geometry_key = key.geometry_key();
        let index_offset = section.index_offset;
        let index_count = section.index_count;
        self.ensure_mesh_geometry_resources(
            gal,
            geometry_key,
            vertex_bytes,
            index_bytes,
            key.view_layering.is_some(),
        )?;
        let geometry = self
            .mesh_geometry_resources
            .get(&geometry_key)
            .ok_or_else(|| GalError::backend("world mesh geometry resources missing"))?;
        let vertex_buffer = geometry.vertex_buffer;
        let vertex_offset = geometry.vertex_offset;
        let vertex_range = geometry.vertex_range;
        let vertex_stride = geometry.vertex_stride;
        let index_buffer = geometry.index_buffer;
        let geometry_index_offset = geometry.index_offset;
        self.ensure_mesh_texture_resources(gal, key.texture_id, &label)?;
        let texture_resources = self
            .mesh_texture_resources
            .get(&key.texture_id)
            .ok_or_else(|| GalError::backend("world mesh texture resources missing"))?;
        let texture_view = texture_resources.view;
        let sampler = texture_resources.sampler;
        let mut pipeline_key = mesh_pipeline_key(key)?;
        pipeline_key.shader_resource_layout = shader_resource_layout;
        self.ensure_mesh_pipeline_resources(gal, pipeline_key.clone())?;
        let pipeline_resources = self
            .mesh_pipeline_resources
            .get(&pipeline_key)
            .ok_or_else(|| GalError::backend("world mesh pipeline resources missing"))?;
        let resource_layout = pipeline_resources.resource_layout;
        let pipeline_layout = pipeline_resources.pipeline_layout;
        let pipeline = pipeline_resources.pipeline;
        let shadow_pipeline = pipeline_resources.shadow_pipeline;
        let observation_buffer = pipeline_resources
            .vertex_observation
            .as_ref()
            .map(|o| o.output);
        let mut created = Vec::new();
        let result = (|| -> GalResult<MeshResources> {
            let stream_binding = self.ensure_mesh_instance_stream(gal, 1)?;
            let resource_set = create_mesh_resource_set(
                gal,
                &label,
                resource_layout,
                vertex_buffer,
                vertex_range,
                stream_binding.buffer,
                WORLD_MESH_INSTANCE_STREAM_BINDING_RANGE_BYTES,
                texture_view,
                sampler,
                key.standard_item_foil,
                observation_buffer,
            )?;
            created.push(resource_set);
            let resources = MeshResources {
                geometry_key,
                vertex_buffer,
                vertex_offset,
                vertex_range,
                vertex_stride,
                index_buffer,
                index_offset: geometry_index_offset,
                index_type,
                pipeline_layout,
                pipeline,
                shadow_pipeline,
                resource_set,
                page_resource_set: None,
            };
            let _ = (index_offset, index_count, index_type);
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.mesh_resources.insert(key, result?);
        Ok(())
    }

    pub(crate) fn destroy_mesh_pipeline_resources(&mut self, gal: &mut VulkanicGal) {
        self.destroy_mesh_page_resource_sets(gal);
        let resources = std::mem::take(&mut self.mesh_pipeline_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }
}

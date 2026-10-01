//! Material-quad resources, slots and texture uploads.

use super::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn ensure_material_resources(
        &mut self,
        gal: &mut VulkanicGal,
        key: MaterialResourceKey,
    ) -> GalResult<()> {
        if self.material_resources.contains_key(&key) {
            return Ok(());
        }
        let label = format!(
            "world-material-reg{}-stratum{}-compact{}-{}-texture{}-mode{}-depth{}-cull{}-winding{}-gen{}",
            assets::material_registry::WORLD_MATERIAL_REGISTRY_VERSION,
            key.stratum,
            key.compact_dh_box,
            key.material_id,
            key.texture_id,
            key.material_mode,
            key.depth_policy,
            key.cull_policy,
            key.winding,
            self.generation
        );
        // A registered texture has one Rust-owned GPU incarnation. Material
        // consumers must bind that image, not a snapshot that misses later
        // region uploads from the owned animation scheduler.
        let registered_texture = if self.mesh_texture_assets.contains_key(&key.texture_id) {
            self.ensure_mesh_texture_resources(gal, key.texture_id, &label)?;
            let registered = self
                .mesh_texture_resources
                .get(&key.texture_id)
                .expect("ensured registered material texture");
            Some((
                registered.texture,
                registered.width,
                registered.height,
                registered.mip_levels,
            ))
        } else {
            None
        };
        let (texture_bytes, texture_width, texture_height, material_texture_mip_levels) =
            if let Some((_, width, height, mip_levels)) = registered_texture {
                (Vec::new(), width, height, mip_levels)
            } else {
                let (bytes, width, height) = self.world_material_texture_bytes(key.texture_id)?;
                let mip_levels =
                    material_texture_mip_level_count(key.source_program, width, height);
                (bytes, width, height, mip_levels)
            };
        let lightmap_resource_layout = material_uses_lightmap(key)
            .then(|| self.ensure_builtin_terrain_lightmap_layout(gal))
            .transpose()?;
        let mut created = Vec::new();
        let result = (|| -> GalResult<MaterialResources> {
            let upload_buffer = if registered_texture.is_none() {
                let buffer = gal.create_buffer(BufferDesc {
                    label: format!("{label}.texture-upload"),
                    size: texture_bytes.len() as u64,
                    memory: MemoryDomain::Upload,
                    usages: vec![
                        BufferUsage::TransferSrc,
                        BufferUsage::TransferDst,
                        BufferUsage::HostWrite,
                    ],
                })?;
                created.push(buffer);
                Some(buffer)
            } else {
                None
            };
            let index_upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.index-upload"),
                size: WORLD_MATERIAL_INDEX_BYTES,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(index_upload_buffer);
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.index"),
                size: WORLD_MATERIAL_INDEX_BYTES,
                memory: MemoryDomain::DeviceLocal,
                usages: vec![BufferUsage::Index, BufferUsage::TransferDst],
            })?;
            created.push(index_buffer);
            let texture = if let Some((texture, _, _, _)) = registered_texture {
                texture
            } else {
                let texture = gal.create_texture(TextureDesc {
                    label: format!("{label}.texture"),
                    dimension: TextureDimension::D2,
                    format: TextureFormat::Rgba8Unorm,
                    extent: Extent3d {
                        width: texture_width,
                        height: texture_height,
                        depth: 1,
                    },
                    mip_levels: material_texture_mip_levels,
                    array_layers: 1,
                    usages: vec![
                        TextureUsage::Sampled,
                        TextureUsage::TransferSrc,
                        TextureUsage::TransferDst,
                    ],
                })?;
                created.push(texture);
                texture
            };
            let address_mode = material_sampler_address_mode(key.material_id, key.texture_id);
            let sampling = self
                .mesh_texture_assets
                .get(&key.texture_id)
                .and_then(|asset| asset.sampling)
                .unwrap_or(crate::render::shared::texture_sampling::TextureSampling {
                    filter: SamplerFilter::Nearest,
                    address: address_mode,
                });
            let sampler = gal.create_sampler(
                sampling.descriptor(format!("{label}.sampler"), SamplerFilter::Nearest),
            )?;
            created.push(sampler);
            let particle_material = material_uses_particle_shader(key.source_program);
            let lightmapped_material = material_uses_lightmap(key);
            let vertex_source = std::str::from_utf8(if key.compact_dh_box {
                WORLD_DH_GENERIC_BOX_VERTEX_SHADER
            } else {
                WORLD_MATERIAL_VERTEX_SHADER
            })
            .expect("world material shader is UTF-8");
            let vertex_source = if lightmapped_material {
                vertex_source.replacen(
                    "#version 450",
                    "#version 450\n#define VULKANIC_GAL_PARTICLE_LIGHTMAP",
                    1,
                )
            } else {
                vertex_source.to_owned()
            };
            let vertex_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.vertex"),
                stage: ShaderStage::Vertex,
                code_format: ShaderCodeFormat::Glsl,
                code: shader_stage_code(gal.capabilities().shader_conventions, &vertex_source),
                entry_point: "main".to_string(),
            })?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(ShaderModuleDesc {
                label: format!("{label}.fragment"),
                stage: ShaderStage::Fragment,
                code_format: ShaderCodeFormat::Glsl,
                code: if particle_material {
                    WORLD_PARTICLE_MATERIAL_FRAGMENT_SHADER.to_vec()
                } else if key.material_id == WORLD_MATERIAL_ID_CELESTIAL {
                    WORLD_CELESTIAL_MATERIAL_FRAGMENT_SHADER.to_vec()
                } else {
                    WORLD_MATERIAL_FRAGMENT_SHADER.to_vec()
                },
                entry_point: "main".to_string(),
            })?;
            created.push(fragment_shader);
            let texture_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.texture-view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: material_texture_mip_levels,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(texture_view);
            let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
                label: format!("{label}.resource-layout"),
                bindings: vec![
                    ResourceBindingDesc {
                        binding: 0,
                        kind: ResourceBindingKind::StorageBuffer,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 1,
                        kind: ResourceBindingKind::SampledTexture,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                    ResourceBindingDesc {
                        binding: 2,
                        kind: ResourceBindingKind::Sampler,
                        stages: PipelineStageFlags::DRAW,
                        array_count: 1,
                        optional: false,
                        dynamic_offset_count: 0,
                    },
                ],
            })?;
            created.push(resource_layout);
            let slot = create_material_data_slot(
                gal,
                &format!("{label}.slot0"),
                resource_layout,
                texture_view,
                sampler,
            )?;
            created.push(slot.uniform_buffer);
            created.push(slot.resource_set);
            let mut resource_layouts = vec![resource_layout];
            if let Some(lightmap_layout) = lightmap_resource_layout {
                resource_layouts.push(lightmap_layout);
            }
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts,
            })?;
            created.push(pipeline_layout);
            let (cull_mode, front_face, blend, depth_compare) = generic_material_raster_state(
                key.source_program,
                key.material_mode,
                key.depth_policy,
                key.cull_policy,
            )?;
            let blend = assets::material_registry::blend_override(key.material_id).unwrap_or(blend);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode,
                front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: key.raster_y_direction,
                // Material modes are semantic frontend state. In particular,
                // the real vanilla weather producer shares this generic quad
                // path and must retain straight-alpha composition rather than
                // being treated as an opaque texture writer.
                blend,
                depth_compare,
                depth_write: key.material_mode != WORLD_MATERIAL_MODE_GLINT
                    && key.depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE,
                depth_bias: if key.material_id == WORLD_MATERIAL_ID_MODEL_CRUMBLING {
                    Some(DepthBias {
                        constant_factor: -1.0,
                        slope_factor: -10.0,
                    })
                } else {
                    None
                },
                color_formats: vec![key.color_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            let resources = MaterialResources {
                upload_buffer,
                index_upload_buffer,
                index_buffer,
                texture,
                sampler,
                vertex_shader,
                fragment_shader,
                texture_view,
                resource_layout,
                lightmap_resource_layout,
                pipeline_layout,
                pipeline,
                data_slots: vec![slot],
            };
            self.upload_material_resources(
                gal,
                &resources,
                key,
                texture_bytes,
                texture_width,
                texture_height,
            )?;
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.material_resources.insert(key, result?);
        Ok(())
    }

    pub(crate) fn ensure_material_resource_slots(
        &mut self,
        gal: &mut VulkanicGal,
        key: MaterialResourceKey,
        required_slots: usize,
    ) -> GalResult<()> {
        let resources = self.material_resources.get_mut(&key).ok_or_else(|| {
            GalError::backend("world material resources missing during slot growth")
        })?;
        while resources.data_slots.len() < required_slots {
            let slot_index = resources.data_slots.len();
            let label = format!(
                "world-material-{}-texture{}-mode{}-depth{}-cull{}-slot{}-gen{}",
                key.material_id,
                key.texture_id,
                key.material_mode,
                key.depth_policy,
                key.cull_policy,
                slot_index,
                self.generation
            );
            let slot = create_material_data_slot(
                gal,
                &label,
                resources.resource_layout,
                resources.texture_view,
                resources.sampler,
            )?;
            resources.data_slots.push(slot);
        }
        Ok(())
    }

    pub(crate) fn world_material_texture_bytes(&self, texture_id: u32) -> GalResult<(Vec<u8>, u32, u32)> {
        // Particle atlases may be resource-pack or mod-owned FNV identities.
        // Resolve the copied Rust-owned asset before consulting the fixed
        // vanilla material registry; an unregistered identity still fails
        // closed below during source-local upload preparation.
        if let Some(asset) = self.mesh_texture_assets.get(&texture_id) {
            return Ok((asset.rgba.clone(), asset.width, asset.height));
        }
        let texture_id = frame::material_quads::canonical_texture_id(texture_id).ok_or_else(|| {
            GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material texture id {texture_id}"),
            )
        })?;
        if frame::material_quads::is_runtime_mesh_texture_id(texture_id) {
            let asset = self.mesh_texture_assets.get(&texture_id).ok_or_else(|| {
                GalError::ffi(
                    StatusCode::InvalidArgument,
                    format!("runtime world material texture asset {texture_id} is not registered"),
                )
            })?;
            return Ok((asset.rgba.clone(), asset.width, asset.height));
        }
        if !frame::material_quads::is_known_texture_id(texture_id) {
            return Err(GalError::ffi(
                StatusCode::UnknownEnum,
                format!("unknown world material texture id {texture_id}"),
            ));
        }
        if let Some(asset) = self.material_asset_overrides.get(&texture_id) {
            return Ok((asset.rgba.clone(), asset.width, asset.height));
        }
        bundled_world_material_texture_bytes(texture_id)
    }

    pub(crate) fn upload_material_resources(
        &mut self,
        gal: &mut VulkanicGal,
        resources: &MaterialResources,
        key: MaterialResourceKey,
        texture_bytes: Vec<u8>,
        texture_width: u32,
        texture_height: u32,
    ) -> GalResult<()> {
        let mut index_bytes = Vec::with_capacity(WORLD_MATERIAL_INDEX_BYTES as usize);
        for index in material_quad_indices_for_winding(key.winding)? {
            push_u32(&mut index_bytes, index);
        }
        let mut operations = Vec::new();
        if let Some(upload_buffer) = resources.upload_buffer {
            operations.extend([
                CommandOp::HostWriteBuffer {
                    buffer: upload_buffer,
                    offset: 0,
                    data: texture_bytes,
                },
                CommandOp::Barrier(buffer_barrier(
                    upload_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::Barrier(sampled_texture_barrier(
                    resources.texture,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                    buffer: upload_buffer,
                    buffer_offset: 0,
                    bytes_per_row: texture_width * 4,
                    rows_per_image: texture_height,
                    texture: resources.texture,
                    texture_mip: 0,
                    texture_layer: 0,
                    texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                    extent: Extent3d {
                        width: texture_width,
                        height: texture_height,
                        depth: 1,
                    },
                }),
                CommandOp::Barrier(sampled_texture_barrier(
                    resources.texture,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
            ]);
        }
        operations.extend([
            CommandOp::HostWriteBuffer {
                buffer: resources.index_upload_buffer,
                offset: 0,
                data: index_bytes,
            },
            CommandOp::Barrier(buffer_barrier(
                resources.index_upload_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::TransferSrc,
            )),
            CommandOp::Barrier(buffer_barrier(
                resources.index_buffer,
                TextureUsageState::Undefined,
                TextureUsageState::TransferDst,
            )),
            CommandOp::CopyBuffer {
                src: resources.index_upload_buffer,
                dst: resources.index_buffer,
                size: WORLD_MATERIAL_INDEX_BYTES,
            },
            CommandOp::Barrier(buffer_barrier(
                resources.index_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::IndexRead,
            )),
        ]);
        self.submit_or_queue_world_upload(gal, "world-material.upload", operations)
    }

    pub(crate) fn destroy_material_resources(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.material_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
    }
}

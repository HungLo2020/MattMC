//! Lowered terrain and shadow programs: layouts, pipelines, pack/frame/geometry data and draws.

use super::*;

impl WorldPrimitiveFrontend {
    /// Materializes the immutable mesh buffers for a fully lowered source
    /// program. This owns only geometry; mutable per-frame data is prepared
    /// separately so multiple batches cannot overwrite one another.
    pub(crate) fn ensure_lowered_source_terrain_geometry_resources(
        &mut self,
        gal: &mut VulkanicGal,
        _program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
    ) -> GalResult<LoweredSourceTerrainDataKey> {
        // Validated once when converted (source_terrain_mesh_asset).
        let key = LoweredSourceTerrainDataKey {
            mesh_key: prepared.mesh.mesh_key,
            mesh_generation: prepared.mesh.mesh_generation,
            abi: SourceGeometryAbi::Terrain,
        };
        self.ensure_lowered_source_mesh_geometry_resources(
            gal,
            key,
            "source-terrain",
            &prepared.mesh.vertex_bytes,
            &prepared.mesh.index_bytes,
        )
    }

    /// Materializes immutable fixed-ABI geometry shared by every admitted
    /// source mesh writer. The key includes the fixed source ABI rather than
    /// program identity, so terrain programs with the same vertex contract
    /// share one uploaded stream while local-textured entities stay separate.
    /// It owns no pass, route, presenter, or backend state.
    pub(crate) fn ensure_lowered_source_mesh_geometry_resources(
        &mut self,
        gal: &mut VulkanicGal,
        key: LoweredSourceTerrainDataKey,
        writer_label: &str,
        vertex_bytes: &[u8],
        index_bytes: &[u8],
    ) -> GalResult<LoweredSourceTerrainDataKey> {
        if self
            .lowered_source_terrain_geometry_resources
            .contains_key(&key)
        {
            return Ok(key);
        }
        let vertex_size = u64::try_from(vertex_bytes.len()).map_err(|_| {
            GalError::invalid_argument("source geometry vertex payload exceeds u64")
        })?;
        let index_size = u64::try_from(index_bytes.len())
            .map_err(|_| GalError::invalid_argument("source geometry index payload exceeds u64"))?;
        let byte_size = vertex_size
            .checked_add(index_size)
            .ok_or_else(|| GalError::invalid_argument("source geometry byte size overflow"))?;
        let resident_bytes = self
            .lowered_source_terrain_geometry_resources
            .values()
            .map(|resources| resources.byte_size)
            .try_fold(0_u64, u64::checked_add)
            .ok_or_else(|| {
                GalError::backend("lowered source geometry residency accounting overflow")
            })?;
        if !lowered_source_geometry_budget_allows(resident_bytes, byte_size) {
            return Err(GalError::unsupported_feature(format!(
                "lowered source geometry residency budget exceeded (resident={} requested={} limit={})",
                resident_bytes, byte_size, LOWERED_SOURCE_GEOMETRY_MAX_BYTES
            )));
        }
        let label = format!(
            "{writer_label}-geometry-{:?}-mesh{}-gen{}",
            key.abi, key.mesh_key, key.mesh_generation
        );
        // Shader programs read every source vertex each frame, so where the
        // backend has device-local memory the streams live there behind a
        // one-shot staging copy, like ordinary terrain arenas; host-visible
        // memory would put the whole resident world on the bus every frame.
        let device_local = gal.capabilities().supports(BackendFeature::DeviceLocalMemory);
        if key.abi == SourceGeometryAbi::Terrain && source_terrain_geometry_pages_enabled() {
            return self.ensure_paged_source_terrain_geometry(
                gal,
                key,
                &label,
                vertex_bytes,
                index_bytes,
                byte_size,
                device_local,
            );
        }
        let mut created = Vec::new();
        let result = (|| -> GalResult<LoweredSourceTerrainGeometryResources> {
            let (memory, upload_usage) = if device_local {
                (MemoryDomain::DeviceLocal, BufferUsage::TransferDst)
            } else {
                (MemoryDomain::Upload, BufferUsage::HostWrite)
            };
            let vertex_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.vertices"),
                size: vertex_bytes.len() as u64,
                memory,
                usages: vec![BufferUsage::Storage, upload_usage],
            })?;
            created.push(vertex_buffer);
            let index_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.indices"),
                size: index_bytes.len() as u64,
                memory,
                usages: vec![BufferUsage::Index, upload_usage],
            })?;
            created.push(index_buffer);
            let staging_buffer = if device_local {
                let staging = gal.create_buffer(BufferDesc {
                    label: format!("{label}.staging"),
                    size: byte_size,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
                })?;
                created.push(staging);
                Some(staging)
            } else {
                None
            };
            Ok(LoweredSourceTerrainGeometryResources {
                vertex_buffer,
                index_buffer,
                byte_size,
                staging_buffer,
                paged: None,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        let resources = result?;
        let upload_ops = match resources.staging_buffer {
            Some(staging) => vec![
                CommandOp::HostWriteBuffer {
                    buffer: staging,
                    offset: 0,
                    data: [vertex_bytes, index_bytes].concat(),
                },
                CommandOp::Barrier(buffer_barrier(
                    staging,
                    TextureUsageState::TransferDst,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::Barrier(buffer_barrier(
                    resources.vertex_buffer,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::Barrier(buffer_barrier(
                    resources.index_buffer,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::CopyBufferRegion {
                    src: staging,
                    src_offset: 0,
                    dst: resources.vertex_buffer,
                    dst_offset: 0,
                    size: vertex_size,
                },
                CommandOp::CopyBufferRegion {
                    src: staging,
                    src_offset: vertex_size,
                    dst: resources.index_buffer,
                    dst_offset: 0,
                    size: index_size,
                },
                CommandOp::Barrier(buffer_barrier(
                    resources.vertex_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::Barrier(buffer_barrier(
                    resources.index_buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::IndexRead,
                )),
            ],
            None => Self::host_visible_source_geometry_upload_ops(
                &resources,
                vertex_bytes,
                index_bytes,
            ),
        };
        self.pending_lowered_source_terrain_geometry_uploads
            .insert(key.clone(), upload_ops);
        self.lowered_source_terrain_geometry_resources
            .insert(key.clone(), resources);
        Ok(key)
    }

    /// Materializes the mutable set-zero resources declared by a fully lowered
    /// source program. The key includes the exact semantic payload, while the
    /// geometry binding comes from the persistent mesh-generation cache.
    /// This remains private preparation: it does not select a route, create a
    /// render pass, or issue a draw.
    pub(in crate::render::worldrender) fn ensure_lowered_source_terrain_data_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
    ) -> GalResult<(
        LoweredSourceTerrainDataKey,
        LoweredSourceTerrainFrameDataKey,
        SourceTerrainFrameStreamAllocation,
    )> {
        let data = self.ensure_lowered_source_terrain_frame_data(gal, program, prepared)?;
        Ok((data.geometry_key, data.frame_data_key, data.stream))
    }

    /// Stages one terrain batch's frame payload and resolves its set-zero.
    /// On a multi-draw frame, paged geometry binds its whole page and the
    /// frame slot's instance stream from offset zero; the batch's records are
    /// then addressed by the returned `firstInstance`.
    pub(crate) fn ensure_lowered_source_terrain_frame_data(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
    ) -> GalResult<LoweredSourceTerrainFrameData> {
        let interface = &program.execution_interface;
        if !self.source_program_prevalidated(prepared.frame_id, program) {
            interface.validate()?;
        }
        // Validated once when converted (source_terrain_mesh_asset).
        let geometry_key =
            self.ensure_lowered_source_terrain_geometry_resources(gal, program, prepared)?;
        let page = if self.source_terrain_multidraw_frame == Some(prepared.frame_id) {
            self.lowered_source_terrain_geometry_resources
                .get(&geometry_key)
                .and_then(|resources| resources.paged)
                .map(|(vertex, _)| vertex.buffer)
        } else {
            None
        };
        let required_instance_bytes =
            u64::try_from(prepared.instance_transforms.len()).map_err(|_| {
                GalError::invalid_argument("source terrain instance payload length exceeds u64")
            })?;
        if required_instance_bytes == 0 {
            return Err(GalError::invalid_argument(
                "source terrain resource preparation requires non-empty instance data",
            ));
        }
        if interface.scalar_uniforms.is_some() && prepared.scalar_uniforms.is_empty() {
            return Err(GalError::invalid_argument(
                "source terrain program declares scalar uniforms without packed scalar data",
            ));
        }
        if interface.scalar_uniforms.is_none() && !prepared.scalar_uniforms.is_empty() {
            return Err(GalError::invalid_argument(
                "source terrain program has no scalar uniform binding but packed scalar data was supplied",
            ));
        }
        let shared_uniforms = self
            .pending_source_terrain_frame_transactions
            .get(&prepared.frame_id)
            .and_then(|transaction| {
                transaction.shared_uniform_offsets(
                    &prepared.legacy_texture_transforms,
                    &prepared.scalar_uniforms,
                )
            });
        let stream = self.allocate_source_terrain_frame_stream_aligned(
            gal,
            prepared.frame_id,
            u64::from(interface.legacy_transform_bytes),
            u64::from(interface.scalar_uniform_bytes),
            required_instance_bytes,
            shared_uniforms,
            if page.is_some() {
                SOURCE_TERRAIN_MULTIDRAW_INSTANCE_ALIGNMENT
            } else {
                WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64
            },
        )?;
        let (key, multidraw_first_instance) = match page {
            Some(page) => {
                let slot_capacity = self
                    .source_terrain_frame_stream_slots
                    .iter()
                    .find(|slot| slot.buffer == stream.buffer)
                    .map(|slot| slot.capacity)
                    .ok_or_else(|| GalError::backend("source multi-draw stream slot vanished"))?;
                let range = slot_capacity.min(SOURCE_TERRAIN_MULTIDRAW_INSTANCE_RANGE_MAX);
                if stream.instance_offset + required_instance_bytes > range {
                    return Err(GalError::unsupported_feature(
                        "source multi-draw instance records exceed the bindable stream range",
                    ));
                }
                let first_instance =
                    u32::try_from(stream.instance_offset / TERRAIN_SOURCE_INSTANCE_BYTES as u64)
                        .map_err(|_| {
                            GalError::invalid_argument("source multi-draw first instance exceeds u32")
                        })?;
                (
                    LoweredSourceTerrainFrameDataKey {
                        geometry: LoweredSourceTerrainDataKey {
                            mesh_key: page.raw(),
                            mesh_generation: 0,
                            abi: SourceGeometryAbi::TerrainPage,
                        },
                        shader_program_identity: program.identity.clone(),
                        shader_pack_generation: program.shader_pack_generation,
                        stream_buffer: stream.buffer,
                        instance_bytes: range,
                    },
                    Some(first_instance),
                )
            }
            None => (
                LoweredSourceTerrainFrameDataKey {
                    geometry: geometry_key.clone(),
                    shader_program_identity: program.identity.clone(),
                    shader_pack_generation: program.shader_pack_generation,
                    stream_buffer: stream.buffer,
                    instance_bytes: required_instance_bytes,
                },
                None,
            ),
        };
        if !self
            .lowered_source_terrain_frame_data_resources
            .contains_key(&key)
        {
            let program_layouts =
                self.ensure_lowered_source_terrain_program_layouts(gal, program)?;
            let label = format!(
                "source-terrain-frame-data-{}-mesh{}-gen{}",
                program.identity.as_str(),
                geometry_key.mesh_key,
                geometry_key.mesh_generation,
            );
            let vertex_buffer = match page {
                Some(page) => page,
                None => self
                    .lowered_source_terrain_geometry_resources
                    .get(&geometry_key)
                    .map(|resources| resources.vertex_buffer)
                    .ok_or_else(|| GalError::backend("source terrain geometry resources vanished"))?,
            };
            let instance_range = key.instance_bytes;
            let mut created = Vec::new();
            let result = (|| -> GalResult<LoweredSourceTerrainFrameDataResources> {
                let mut bindings = vec![
                    ResourceBinding {
                        binding: interface.vertex_stream.binding,
                        array_index: 0,
                        resource: vertex_buffer,
                        kind: ResourceBindingKind::StorageBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: interface.legacy_transforms.binding,
                        array_index: 0,
                        resource: stream.buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: vec![0],
                        buffer_range: Some(u64::from(interface.legacy_transform_bytes)),
                    },
                    ResourceBinding {
                        binding: interface.instance_stream.binding,
                        array_index: 0,
                        resource: stream.buffer,
                        kind: ResourceBindingKind::StorageBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: vec![0],
                        // Dynamic offsets choose the record range inside the
                        // shared completion-gated stream. The byte length remains
                        // part of this stable binding key so backend descriptor
                        // range validation stays exact without keying on payload.
                        buffer_range: Some(instance_range),
                    },
                ];
                if let Some(binding) = interface.scalar_uniforms {
                    bindings.push(ResourceBinding {
                        binding: binding.binding,
                        array_index: 0,
                        resource: stream.buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: vec![0],
                        buffer_range: Some(u64::from(interface.scalar_uniform_bytes)),
                    });
                }
                bindings.sort_by_key(|binding| binding.binding);
                let resource_set = gal.create_resource_set(ResourceSetDesc {
                    label: format!("{label}.set-zero"),
                    layout: program_layouts.source_data,
                    bindings,
                })?;
                created.push(resource_set);
                Ok(LoweredSourceTerrainFrameDataResources { resource_set })
            })();
            if result.is_err() {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
            }
            self.lowered_source_terrain_frame_data_resources
                .insert(key.clone(), result?);
        }
        let geometry_upload_ops = self
            .pending_lowered_source_terrain_geometry_uploads
            .remove(&geometry_key)
            .unwrap_or_default();
        let transaction = self
            .pending_source_terrain_frame_transactions
            .entry(prepared.frame_id)
            .or_insert_with(|| SourceTerrainFrameTransaction {
                frame_id: prepared.frame_id,
                stream_buffer: stream.buffer,
                stream_epoch: stream.epoch,
                operations: Vec::new(),
                source_material_texture_ids: BTreeSet::new(),
                geometry_uploads: Vec::new(),
                stream_staging: Vec::new(),
                shared_uniforms: Vec::new(),
                indirect_buffer: None,
                indirect_staging: Vec::new(),
            });
        if transaction.stream_buffer != stream.buffer || transaction.stream_epoch != stream.epoch {
            if !geometry_upload_ops.is_empty() {
                self.pending_lowered_source_terrain_geometry_uploads
                    .insert(geometry_key.clone(), geometry_upload_ops);
            }
            return Err(GalError::backend(
                "source terrain frame payloads resolved to different stream slots",
            ));
        }
        if !geometry_upload_ops.is_empty() {
            transaction
                .geometry_uploads
                .push((geometry_key.clone(), geometry_upload_ops));
        }
        if shared_uniforms.is_some() {
            transaction.stage_stream_write(stream.instance_offset, &prepared.instance_transforms);
        } else {
            transaction.stage_stream_parts(
                stream,
                &prepared.legacy_texture_transforms,
                &prepared.scalar_uniforms,
                &prepared.instance_transforms,
            );
            if transaction.shared_uniforms.len() < 64 {
                transaction.shared_uniforms.push((
                    prepared.legacy_texture_transforms.clone(),
                    prepared.scalar_uniforms.clone(),
                    stream.legacy_transform_offset,
                    stream.scalar_uniform_offset,
                ));
            }
        }
        Ok(LoweredSourceTerrainFrameData {
            geometry_key,
            frame_data_key: key,
            stream,
            multidraw_first_instance,
        })
    }

    /// Materializes the lowered source program's set-one semantic sampler and
    /// storage-image bindings. The owned resource table already proves each
    /// role's shape and generation; this method only turns that backend-neutral
    /// declaration into an explicit GAL layout/set. It cannot select a
    /// pipeline or issue a draw.
    pub(crate) fn ensure_lowered_source_terrain_pack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<LoweredSourceTerrainPackKey> {
        let addresses = (
            program as *const LoweredTerrainSourceProgram as usize,
            resources as *const TerrainSourceOwnedResourceSet as usize,
        );
        if let Some(scope) = self.source_terrain_batch_scope.as_ref() {
            if let Some((_, _, key)) = scope
                .pack_keys
                .iter()
                .find(|(program, resources, _)| (*program, *resources) == addresses)
            {
                return Ok(key.clone());
            }
        }
        let key = self.ensure_lowered_source_terrain_pack_resources_uncached(gal, program, resources)?;
        if let Some(scope) = self.source_terrain_batch_scope.as_mut() {
            scope.pack_keys.push((addresses.0, addresses.1, key.clone()));
        }
        Ok(key)
    }

    pub(crate) fn ensure_lowered_source_terrain_pack_resources_uncached(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<LoweredSourceTerrainPackKey> {
        program.require_semantic_resources(resources.availability())?;
        let key = LoweredSourceTerrainPackKey {
            shader_program_identity: program.identity.clone(),
            shader_pack_generation: program.shader_pack_generation,
            world_generation: resources.availability().world_generation(),
            resource_generations: resources.generation_signature(),
        };
        if self
            .lowered_source_terrain_pack_resources
            .contains_key(&key)
        {
            return Ok(key);
        }
        let stale_keys = self
            .lowered_source_terrain_pack_resources
            .keys()
            .filter(|existing| {
                existing.shader_program_identity == key.shader_program_identity
                    && existing.shader_pack_generation == key.shader_pack_generation
                    && existing.world_generation == key.world_generation
                    && existing.resource_generations != key.resource_generations
            })
            .cloned()
            .collect::<Vec<_>>();
        if !stale_keys.is_empty() {
            if let Some(scope) = self.source_terrain_batch_scope.as_mut() {
                scope.pack_keys.clear();
            }
        }
        self.destroy_lowered_source_terrain_pack_resources_for_keys(gal, stale_keys);
        if !lowered_source_pack_residency_allows(self.lowered_source_terrain_pack_resources.len()) {
            return Err(GalError::unsupported_feature(format!(
                "lowered terrain source pack residency limit reached (limit={})",
                LOWERED_SOURCE_PACK_RESIDENCY
            )));
        }

        let program_layouts = self.ensure_lowered_source_terrain_program_layouts(gal, program)?;
        let label = format!(
            "source-terrain-pack-{}-pack{}-world{}",
            program.identity.as_str(),
            key.shader_pack_generation,
            key.world_generation
        );
        let resource_set = match program.pack_resource_set_desc(
            format!("{label}.set-one"),
            program_layouts.pack_resources,
            resources,
        ) {
            Ok(desc) => match gal.create_resource_set(desc) {
                Ok(set) => set,
                Err(error) => return Err(error),
            },
            Err(error) => return Err(error),
        };
        self.lowered_source_terrain_pack_resources.insert(
            key.clone(),
            LoweredSourceTerrainPackResources { resource_set },
        );
        Ok(key)
    }

    /// Creates the two static layouts declared by a lowered source program
    /// exactly once per source generation. Individual mesh and resource-set
    /// caches reference these layouts but never own their lifetime.
    pub(crate) fn ensure_lowered_source_terrain_program_layouts(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
    ) -> GalResult<LoweredSourceTerrainProgramLayouts> {
        self.ensure_lowered_source_program_layouts(
            gal,
            program.identity.clone(),
            program.shader_pack_generation,
            program.execution_resource_layouts()?,
        )
    }

    /// Caches the two fixed source-stream layouts by semantic program
    /// identity. Entity and terrain source programs may share this ABI, but
    /// distinct source identities still receive distinct layouts and cannot
    /// alias set-one material semantics.
    pub(crate) fn ensure_lowered_source_program_layouts(
        &mut self,
        gal: &mut VulkanicGal,
        shader_program_identity: ProgramIdentity,
        shader_pack_generation: u64,
        layouts: TerrainSourceExecutionLayouts,
    ) -> GalResult<LoweredSourceTerrainProgramLayouts> {
        let key = LoweredSourceTerrainProgramKey {
            shader_program_identity,
            shader_pack_generation,
        };
        if let Some(layouts) = self
            .lowered_source_terrain_program_layouts
            .get(&key)
            .copied()
        {
            return Ok(layouts);
        }
        if !lowered_source_residency_allows(self.lowered_source_terrain_program_layouts.len()) {
            return Err(GalError::unsupported_feature(format!(
                "lowered source program-layout residency limit reached (limit={})",
                WORLD_MESH_PIPELINE_RESIDENCY
            )));
        }
        let source_data = gal.create_resource_layout(layouts.source_data)?;
        let pack_resources = match gal.create_resource_layout(layouts.pack_resources) {
            Ok(layout) => layout,
            Err(error) => {
                let _ = gal.destroy(source_data);
                return Err(error);
            }
        };
        let resources = LoweredSourceTerrainProgramLayouts {
            source_data,
            pack_resources,
        };
        self.lowered_source_terrain_program_layouts
            .insert(key, resources);
        Ok(resources)
    }

    /// Compiles and caches one private source-derived terrain pipeline from
    /// the owned lowered stages. It has no render-pass ownership or draw path
    /// yet; the purpose is to prove that source data and pack-resource layouts
    /// form a backend-neutral GAL pipeline without Java or Iris state.
    pub(crate) fn ensure_lowered_source_terrain_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
    ) -> GalResult<LoweredSourceTerrainPipelineKey> {
        self.ensure_lowered_source_terrain_pipeline_resources_for_outputs(
            gal,
            program,
            material_mode,
            cull_policy,
            winding,
            vec![SHADER_G_BUFFER_COLOR_FORMAT; 4],
        )
    }

    pub(crate) fn ensure_lowered_source_shadow_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        material_mode: u32,
        _terrain_cull_policy: u32,
        winding: u32,
    ) -> GalResult<LoweredSourceTerrainPipelineKey> {
        // Iris disables face culling for the shadow terrain pass so surfaces
        // outside the camera-facing half of a section can still cast shadows.
        // Camera-color terrain retains its own cull policy.
        //
        // Every shadow-map consumer addresses it through the shadow matrices
        // (GL layout: clip y = -1 at row 0), never through screen space. The
        // GL-style flipped viewport used for screen targets would store the map
        // upside down, so shadow writers rasterize natively.
        self.ensure_lowered_source_terrain_pipeline_resources_with_raster(
            gal,
            program,
            material_mode,
            WORLD_CULL_NONE,
            winding,
            vec![SHADER_G_BUFFER_COLOR_FORMAT; 2],
            crate::render::vulkanic::resources::RasterYDirection::Down,
        )
    }

    pub(crate) fn ensure_lowered_source_terrain_pipeline_resources_for_outputs(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<LoweredSourceTerrainPipelineKey> {
        self.ensure_lowered_source_terrain_pipeline_resources_with_raster(
            gal,
            program,
            material_mode,
            cull_policy,
            winding,
            color_formats,
            crate::render::vulkanic::resources::RasterYDirection::Up,
        )
    }

    pub(crate) fn ensure_lowered_source_terrain_pipeline_resources_with_raster(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
        raster_y_direction: crate::render::vulkanic::resources::RasterYDirection,
    ) -> GalResult<LoweredSourceTerrainPipelineKey> {
        let (mut blend, mut depth_write) =
            source_terrain_pipeline_raster_state(program, material_mode)?;
        if matches!(
            crate::core::environment::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] pipeline program={} material_mode={} vertex_label={} clip_finalizer={} depth_compare=LessOrEqual depth_write={} depth_format=Depth32Float color_formats={:?}",
                program.identity.as_str(),
                material_mode,
                program.vertex.label,
                program
                    .vertex
                    .source
                    .contains("gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5"),
                depth_write,
                color_formats,
            );
        }
        if color_formats.is_empty() {
            return Err(GalError::invalid_argument(
                "lowered source terrain pipeline requires at least one explicit color output format",
            ));
        }
        let key = LoweredSourceTerrainPipelineKey {
            program: LoweredSourceTerrainProgramKey {
                shader_program_identity: program.identity.clone(),
                shader_pack_generation: program.shader_pack_generation,
            },
            material_mode,
            cull_policy,
            winding,
            color_formats: color_formats.clone(),
            raster_y_direction,
        };
        if self
            .lowered_source_terrain_pipeline_resources
            .contains_key(&key)
        {
            return Ok(key);
        }
        if !lowered_source_residency_allows(self.lowered_source_terrain_pipeline_resources.len()) {
            return Err(GalError::unsupported_feature(format!(
                "lowered source pipeline residency limit reached (limit={})",
                WORLD_MESH_PIPELINE_RESIDENCY
            )));
        }
        let layouts = self.ensure_lowered_source_terrain_program_layouts(gal, program)?;
        let label = format!(
            "source-terrain-pipeline-{}-mode{}-cull{}-winding{}",
            program.identity.as_str(),
            material_mode,
            cull_policy,
            winding
        );
        // This is a capture-only isolation aid for the owned source path. It
        // never changes normal routing or material semantics: each opt-in
        // process can prove whether an otherwise black source target is caused
        // by culling or by depth/clip rejection before a rendering change is
        // considered.
        let raster_probe = selected_source_raster_probe()?;
        let mut cull_mode = effective_cull_mode_for_winding(cull_policy, winding)?;
        let mut front_face = crate::render::vulkanic::resources::FrontFace::CounterClockwise;
        let mut depth_compare = Some(CompareOp::LessOrEqual);
        match raster_probe {
            SelectedSourceRasterProbe::None | SelectedSourceRasterProbe::InvertFrontFace => {}
            SelectedSourceRasterProbe::NoCull => cull_mode = CullMode::None,
            SelectedSourceRasterProbe::DepthDisabled => {
                depth_compare = None;
                depth_write = false;
            }
            SelectedSourceRasterProbe::BlendDisabled => blend = BlendMode::Disabled,
        }
        front_face = selected_source_raster_probe_front_face(front_face)?;
        let shadow_alpha_cutoff = if program.terrain_output_color_slots().is_none()
            && material_mode == WORLD_MATERIAL_MODE_CUTOUT
        {
            let policy = self.shader_pack_sources.active_shadow_policy().ok_or_else(|| {
                GalError::unsupported_feature("source shadow cutout has no selected shadow policy")
            })?;
            if policy.generation() != program.shader_pack_generation {
                return Err(GalError::invalid_argument(
                    "source shadow cutout policy generation does not match its program",
                ));
            }
            policy.cutout_alpha_cutoff()
        } else {
            None
        };
        let mut created = Vec::new();
        let result = (|| -> GalResult<LoweredSourceTerrainPipelineResources> {
            let [vertex_desc, fragment_desc] = if program.terrain_output_color_slots().is_none() {
                program.shadow_shader_module_descriptors(
                    gal.capabilities().shader_conventions,
                    shadow_alpha_cutoff,
                )?
            } else {
                program.shader_module_descriptors(gal.capabilities().shader_conventions)
            };
            let vertex_shader = gal.create_shader_module(vertex_desc)?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(fragment_desc)?;
            created.push(fragment_shader);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.layout"),
                resource_layouts: vec![layouts.source_data, layouts.pack_resources],
            })?;
            created.push(pipeline_layout);
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode,
                front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction,
                blend,
                depth_compare,
                depth_write,
                depth_bias: None,
                color_formats,
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(LoweredSourceTerrainPipelineResources {
                vertex_shader,
                fragment_shader,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.lowered_source_terrain_pipeline_resources
            .insert(key.clone(), result?);
        Ok(key)
    }

    /// Assembles explicit runtime draw records from a fully lowered source
    /// program and Rust-owned semantic resources. This is deliberately only
    /// draw preparation: it neither selects a source route nor records a
    /// render pass. A future admitted source terrain executor can hand these
    /// records to the existing Rust-owned terrain graph without borrowing
    /// Java, Iris, or backend state.
    pub(crate) fn prepare_lowered_source_terrain_draws(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        self.prepare_lowered_source_terrain_draws_for_color_formats(
            gal,
            program,
            prepared,
            pack_resources,
            material_mode,
            cull_policy,
            winding,
            vec![SHADER_G_BUFFER_COLOR_FORMAT; 4],
        )
    }

    /// Assembles source terrain draws for an already resolved named source
    /// output schema. The compact format vector follows named output order,
    /// never a legacy draw-buffer slot, and is part of the pipeline cache
    /// key so incompatible source targets cannot share a pipeline.
    pub(crate) fn prepare_lowered_source_terrain_draws_for_color_formats(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        self.ensure_source_mesh_generation(
            "terrain",
            prepared.mesh.mesh_key,
            prepared.mesh.mesh_generation,
        )?;
        // Validate material/pass compatibility before allocating any stream
        // resources. Translucency is admitted only when its distinct source
        // program carried explicit raster semantics; it cannot inherit the
        // opaque terrain pipeline's disabled blend/depth-write state.
        let _ = source_terrain_pipeline_raster_state(program, material_mode)?;
        if prepared.instance_transforms.len() % TERRAIN_SOURCE_INSTANCE_BYTES != 0 {
            return Err(GalError::invalid_argument(
                "source terrain instance payload does not contain whole records",
            ));
        }
        let instance_count = prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES;
        let instance_count = u32::try_from(instance_count)
            .map_err(|_| GalError::invalid_argument("source terrain instance count exceeds u32"))?;
        if instance_count == 0 {
            return Err(GalError::invalid_argument(
                "source terrain draw preparation requires at least one instance",
            ));
        }

        let frame_data = self.ensure_lowered_source_terrain_frame_data(gal, program, prepared)?;
        let geometry_key = frame_data.geometry_key.clone();
        let frame_data_key = frame_data.frame_data_key.clone();
        let pack_key =
            self.ensure_lowered_source_terrain_pack_resources(gal, program, pack_resources)?;
        let pipeline_key = self.ensure_lowered_source_terrain_pipeline_resources_for_outputs(
            gal,
            program,
            material_mode,
            cull_policy,
            winding,
            color_formats,
        )?;
        let (index_buffer, index_base) = self
            .lowered_source_terrain_geometry_resources
            .get(&geometry_key)
            .map(|resources| (resources.index_buffer, resources.index_offset()))
            .ok_or_else(|| GalError::backend("source terrain geometry resources vanished"))?;
        let resource_set = self
            .lowered_source_terrain_frame_data_resources
            .get(&frame_data_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("source terrain frame data resources vanished"))?;
        let pack_resource_set = self
            .lowered_source_terrain_pack_resources
            .get(&pack_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("source terrain pack resources vanished"))?;
        let (pipeline, pipeline_layout) = self
            .lowered_source_terrain_pipeline_resources
            .get(&pipeline_key)
            .map(|resources| (resources.pipeline, resources.pipeline_layout))
            .ok_or_else(|| GalError::backend("source terrain pipeline resources vanished"))?;
        let material_mode = terrain_material_pass_mode(material_mode)?;

        let dynamic_offsets = frame_data.dynamic_offsets();
        let frame_id = prepared.frame_id;
        prepared
            .section_indices
            .iter()
            .map(|&section_index| {
                let section = prepared
                    .mesh
                    .sections
                    .get(section_index as usize)
                    .expect("validated source terrain section selection");
                let index_offset = index_base
                    + source_draw_index_offset(section.index_offset, prepared.index_subrange);
                let index_count = prepared
                    .index_subrange
                    .map_or(section.index_count, |(_, count)| count);
                // Multi-drawn sections keep the page index binding at zero and
                // carry their range in the command, so compatible draws coalesce.
                let (index_offset, indexed_indirect) = match frame_data.multidraw_first_instance {
                    Some(first_instance) => (
                        0,
                        Some(self.append_source_terrain_multidraw_command(
                            frame_id,
                            PageIndexedDrawCommand {
                                index_count,
                                instance_count,
                                first_index: u32::try_from(index_offset / 4).map_err(|_| {
                                    GalError::invalid_argument("source multi-draw first index exceeds u32")
                                })?,
                                vertex_offset: 0,
                                first_instance,
                            },
                        )?),
                    ),
                    None => (index_offset, None),
                };
                Ok(TerrainMeshDraw {
                    shadow: None,
                    pipeline,
                    offscreen_pipeline: None,
                    pipeline_layout,
                    resource_set,
                    resource_set_dynamic_offsets: dynamic_offsets.clone(),
                    shader_resource_set: Some(TerrainShaderResourceSet {
                        set_index: 1,
                        set: pack_resource_set,
                    }),
                    index_buffer,
                    index_offset,
                    index_type: IndexType::U32,
                    index_count,
                    instance_count,
                    indexed_indirect,
                    // This draw is the source-derived replacement for the
                    // vanilla terrain section.  Keep its semantic stratum as
                    // terrain so the explicit pass graph writes the main
                    // terrain attachment rather than Fabulous's item/entity
                    // attachment (the latter is reserved for entity meshes).
                    stratum: WORLD_STRATUM_TERRAIN,
                    material_mode,
                    shadow_participation: TerrainShadowParticipation::Required,
                })
            })
            .collect()
    }

    /// Assembles source-derived shadow bindings for the same copied mesh
    /// sections as an already prepared terrain frame. The shadow program owns
    /// its scalar uniforms and set-one resources; only the semantic mesh
    /// selection and Rust frame transaction are shared with terrain.
    pub(crate) fn prepare_lowered_source_shadow_draws(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
    ) -> GalResult<Vec<TerrainShadowDraw>> {
        self.prepare_lowered_source_shadow_draws_with_first_instance(
            gal, program, prepared, pack_resources, material_mode, cull_policy, winding,
        )
        .map(|(draws, _)| draws)
    }

    /// As above, also returning the multi-draw `firstInstance` of this
    /// shadow payload (shadow-only draws own their command; a camera draw's
    /// shadow twin reuses the camera command, whose records are identical).
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn prepare_lowered_source_shadow_draws_with_first_instance(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
    ) -> GalResult<(Vec<TerrainShadowDraw>, Option<u32>)> {
        let frame_data = self.ensure_lowered_source_terrain_frame_data(gal, program, prepared)?;
        let geometry_key = frame_data.geometry_key.clone();
        let frame_data_key = frame_data.frame_data_key.clone();
        let pack_key =
            self.ensure_lowered_source_terrain_pack_resources(gal, program, pack_resources)?;
        let pipeline_key = self.ensure_lowered_source_shadow_pipeline_resources(
            gal,
            program,
            material_mode,
            cull_policy,
            winding,
        )?;
        let resource_set = self
            .lowered_source_terrain_frame_data_resources
            .get(&frame_data_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("source shadow frame data resources vanished"))?;
        let pack_resource_set = self
            .lowered_source_terrain_pack_resources
            .get(&pack_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("source shadow pack resources vanished"))?;
        let (pipeline, pipeline_layout) = self
            .lowered_source_terrain_pipeline_resources
            .get(&pipeline_key)
            .map(|resources| (resources.pipeline, resources.pipeline_layout))
            .ok_or_else(|| GalError::backend("source shadow pipeline resources vanished"))?;
        let instance_count =
            u32::try_from(prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES)
                .map_err(|_| {
                    GalError::invalid_argument("source shadow instance count exceeds u32")
                })?;
        if instance_count == 0 {
            return Err(GalError::invalid_argument(
                "source shadow draw preparation requires at least one instance",
            ));
        }
        let _ = geometry_key;
        let dynamic_offsets = frame_data.dynamic_offsets();
        let draws = prepared
            .section_indices
            .iter()
            .map(|_| TerrainShadowDraw {
                pipeline,
                pipeline_layout,
                resource_set,
                resource_set_dynamic_offsets: dynamic_offsets.clone(),
                shader_resource_set: Some(TerrainShaderResourceSet {
                    set_index: 1,
                    set: pack_resource_set,
                }),
            })
            .collect();
        Ok((draws, frame_data.multidraw_first_instance))
    }

    /// Builds an independent shadow-only draw from a resident copied terrain
    /// range. Its type has no color pipeline, so candidate sections outside
    /// the camera domain cannot be replayed into any source color writer.
    pub(crate) fn prepare_lowered_source_shadow_only_draws(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
    ) -> GalResult<Vec<TerrainShadowMeshDraw>> {
        let (shadows, multidraw_first_instance) = self
            .prepare_lowered_source_shadow_draws_with_first_instance(
                gal, program, prepared, pack_resources, material_mode, cull_policy, winding,
            )?;
        let geometry_key =
            self.ensure_lowered_source_terrain_geometry_resources(gal, program, prepared)?;
        let (index_buffer, index_base) = self
            .lowered_source_terrain_geometry_resources
            .get(&geometry_key)
            .map(|resources| (resources.index_buffer, resources.index_offset()))
            .ok_or_else(|| GalError::backend("shadow-only terrain geometry resources vanished"))?;
        let instance_count = u32::try_from(
            prepared.instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
        )
        .map_err(|_| GalError::invalid_argument("shadow-only instance count exceeds u32"))?;
        let pass_mode = terrain_material_pass_mode(material_mode)?;
        prepared
            .section_indices
            .iter()
            .zip(shadows)
            .map(|(&section_index, shadow)| {
                let section = prepared.mesh.sections.get(section_index as usize).ok_or_else(|| {
                    GalError::invalid_argument("shadow-only draw selected a missing mesh section")
                })?;
                let index_offset = index_base
                    + source_draw_index_offset(section.index_offset, prepared.index_subrange);
                let index_count = prepared
                    .index_subrange
                    .map_or(section.index_count, |(_, count)| count);
                let (index_offset, indexed_indirect) = match multidraw_first_instance {
                    Some(first_instance) => (
                        0,
                        Some(self.append_source_terrain_multidraw_command(
                            prepared.frame_id,
                            PageIndexedDrawCommand {
                                index_count,
                                instance_count,
                                first_index: u32::try_from(index_offset / 4).map_err(|_| {
                                    GalError::invalid_argument("source multi-draw first index exceeds u32")
                                })?,
                                vertex_offset: 0,
                                first_instance,
                            },
                        )?),
                    ),
                    None => (index_offset, None),
                };
                Ok(TerrainShadowMeshDraw {
                    shadow,
                    index_buffer,
                    index_offset,
                    index_type: IndexType::U32,
                    index_count,
                    instance_count,
                    indexed_indirect,
                    material_mode: pass_mode,
                })
            })
            .collect()
    }

    /// Prepares source-derived terrain draws only from the complete semantic
    /// resource snapshot recorded for this exact frame. Keeping the lookup
    /// here makes a future source executor prove pack, world, frame, and
    /// program identity before it can create a pipeline or resource set.
    /// This remains CPU/GAL preparation only and does not select a route,
    /// record a pass, or borrow Java/Iris state.
    pub(crate) fn prepare_lowered_source_terrain_draws_for_snapshot(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        program: &LoweredTerrainSourceProgram,
        prepared: &PreparedSourceTerrainFrame,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        let pack_resources = self
            .candidate_source_resources_for_program(
                program.shader_pack_generation,
                world_generation,
                prepared.frame_id,
                program,
            )?
            .clone();
        self.prepare_lowered_source_terrain_draws(
            gal,
            program,
            prepared,
            &pack_resources,
            material_mode,
            cull_policy,
            winding,
        )
    }

    /// Converts already-admitted ordinary terrain batch semantics into the
    /// fixed source-derived mesh ABI. The caller supplies no renderer state:
    /// section ranges, transforms, material mode, culling, and ordering all
    /// originate from the shared Rust frontend batch. This is still private
    /// preparation, not source-route selection or pass recording.
    pub(in crate::render::worldrender) fn prepare_lowered_source_terrain_draws_for_mesh_batches(
        &mut self,
        gal: &mut VulkanicGal,
        programs: &LoweredSourceTerrainPrograms,
        world_generation: u64,
        frame: &WorldPrimitiveFrame,
        batches: &[MeshBatch],
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "lowered source terrain preparation requires a non-zero world generation",
            ));
        }
        let shader_pack_generation = programs.shader_pack_generation()?;
        let shadow_policy = self
            .shader_pack_sources
            .active_shadow_policy()
            .filter(|policy| policy.generation() == shader_pack_generation)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "source terrain shadow policy generation is missing or stale",
                )
            })?;
        let render_translucent_shadows = shadow_policy.render_translucent();
        let shadow_frustum = if terrain_program_scope_for_sky_type(frame.background.sky_type)?
            == Some(TerrainProgramScope::Overworld)
        {
            Some(crate::render::shaderpack::properties::shadow::AdvancedShadowCasterFrustum::from_frame(
                shadow_policy,
                frame.shader_environment.time_of_day,
                frame.projection_matrix,
                frame.view_matrix,
            )?)
        } else {
            None
        };
        // Validate the independently complete terrain program before
        // spending work on private source-stream state. Whole-frame route
        // selection separately requires every retained DH/fullscreen stage.
        self.candidate_source_resources_for_program(
            shader_pack_generation,
            world_generation,
            frame.frame_id,
            &programs.opaque,
        )?;
        self.candidate_source_resources_for_program(
            shader_pack_generation,
            world_generation,
            frame.frame_id,
            &programs.cutout,
        )?;
        self.candidate_source_resources_for_program(
            shader_pack_generation,
            world_generation,
            frame.frame_id,
            &programs.shadow,
        )?;
        let base_uniform_frame = self.source_uniform_frame_for_owned_resources(frame)?;
        let texture_transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
        let mut draws = Vec::new();
        let mut transform_probes = Vec::new();
        for batch in batches {
            if !is_source_terrain_mesh_stratum(batch.key.stratum) {
                return Err(GalError::unsupported_feature(format!(
                    "lowered source terrain preparation rejects unsupported mesh stratum {} for mesh {}",
                    batch.key.stratum, batch.key.mesh_key
                )));
            }
            if !batch.key.g_buffer {
                return Err(GalError::invalid_argument(
                    "lowered source terrain preparation requires G-buffer mesh batches",
                ));
            }
            let program = programs.for_material_mode(batch.key.material_mode)?;
            let mut uniform_frame = base_uniform_frame.clone();
            uniform_frame.render_stage =
                Some(self.source_render_stage_for_material_mode(batch.key.material_mode)?);
            let mut shadow_uniform_frame = base_uniform_frame.clone();
            shadow_uniform_frame.render_stage = Some(self.source_shadow_render_stage()?);
            let instances = batch
                .indices
                .iter()
                .map(|&index| {
                    frame
                        .mesh_instances
                        .get(index)
                        .map(|instance| (instance.transform, instance.color_argb))
                        .ok_or_else(|| {
                            GalError::invalid_argument(
                            "lowered source terrain batch references a missing semantic instance",
                        )
                        })
                })
                .collect::<GalResult<Vec<_>>>()?;
            let prepared = self.prepare_source_terrain_frame_for_mesh_range(
                program,
                frame.frame_id,
                batch.key.mesh_key,
                batch.key.mesh_generation,
                batch.index_offset,
                batch.index_count,
                &instances,
                &texture_transforms,
                &uniform_frame,
            )?;
            collect_selected_source_terrain_transform_probes(
                &mut transform_probes,
                frame,
                batch,
                &prepared,
            )?;
            let mut terrain_draws = self.prepare_lowered_source_terrain_draws_for_snapshot(
                gal,
                world_generation,
                program,
                &prepared,
                batch.key.material_mode,
                batch.key.cull_policy,
                batch.key.winding,
            )?;
            for terrain_draw in &mut terrain_draws {
                terrain_draw.stratum = batch.key.stratum;
            }
            let shadow_instances = batch
                .indices
                .iter()
                .filter_map(|&index| {
                    let instance = &frame.mesh_instances[index];
                    shadow_frustum
                        .as_ref()
                        .is_none_or(|frustum| source_shadow_instance_intersects(frustum, instance, None))
                        .then_some((instance.transform, instance.color_argb))
                })
                .collect::<Vec<_>>();
            if source_shadow_required_for_material_mode(batch.key.material_mode)
                && (batch.key.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT
                    || render_translucent_shadows)
                && !shadow_instances.is_empty()
            {
                let shadow_prepared = self.prepare_source_terrain_frame_for_mesh_range(
                    &programs.shadow,
                    frame.frame_id,
                    batch.key.mesh_key,
                    batch.key.mesh_generation,
                    batch.index_offset,
                    batch.index_count,
                    &shadow_instances,
                    &texture_transforms,
                    &shadow_uniform_frame,
                )?;
                let source_resources = self
                    .candidate_source_resources_for_program(
                        shader_pack_generation,
                        world_generation,
                        frame.frame_id,
                        &programs.shadow,
                    )?
                    .clone();
                let shadow_draws = self.prepare_lowered_source_shadow_draws(
                    gal,
                    &programs.shadow,
                    &shadow_prepared,
                    &source_resources,
                    batch.key.material_mode,
                    batch.key.cull_policy,
                    batch.key.winding,
                )?;
                if terrain_draws.len() != shadow_draws.len() {
                    return Err(GalError::invalid_argument(
                        "source terrain and shadow programs selected different mesh section counts",
                    ));
                }
                for (terrain_draw, shadow_draw) in terrain_draws.iter_mut().zip(shadow_draws) {
                    terrain_draw.shadow = Some(shadow_draw);
                }
            } else {
                for terrain_draw in &mut terrain_draws {
                    terrain_draw.shadow_participation = TerrainShadowParticipation::Unavailable;
                }
            }
            draws.extend(terrain_draws);
        }
        self.write_selected_source_terrain_transform_receipt(frame, &transform_probes);
        Ok(draws)
    }

    /// Produces the only handoff a future admitted source executor needs:
    /// all source draws plus the upload transaction that must precede them in
    /// the same submission. Any preparation failure releases unsubmitted
    /// frame-local state rather than consuming a stream slot indefinitely.
    pub(in crate::render::worldrender) fn prepare_lowered_source_terrain_frame_plan(
        &mut self,
        gal: &mut VulkanicGal,
        programs: &LoweredSourceTerrainPrograms,
        world_generation: u64,
        frame: &WorldPrimitiveFrame,
        batches: &[MeshBatch],
    ) -> GalResult<PreparedLoweredSourceTerrainFramePlan> {
        let draws = match self.prepare_lowered_source_terrain_draws_for_mesh_batches(
            gal,
            programs,
            world_generation,
            frame,
            batches,
        ) {
            Ok(draws) => draws,
            Err(error) => {
                self.discard_source_terrain_frame_transaction(gal, frame.frame_id);
                return Err(error);
            }
        };
        let transaction = match self.take_source_terrain_frame_transaction(frame.frame_id) {
            Ok(transaction) => Some(transaction),
            Err(_error) if draws.is_empty() => None,
            Err(error) => {
                self.discard_source_terrain_frame_transaction(gal, frame.frame_id);
                return Err(error);
            }
        };
        Ok(PreparedLoweredSourceTerrainFramePlan {
            frame_id: frame.frame_id,
            draws,
            transaction,
        })
    }

    /// Returns the lowered source programs for the complete-frame coordinator
    /// after the base frame snapshot and final target have been
    /// correlated. Named color/depth inputs are deliberately not required at
    /// this point: that coordinator owns staging them as part of the same
    /// transaction, and every individual source pass validates the completed
    /// resource set immediately before its pipeline is created.
    pub(crate) fn lowered_source_programs_for_complete_plan(
        &self,
        world_generation: u64,
        frame_id: u64,
        frame_target: Handle,
    ) -> GalResult<LoweredSourceTerrainPrograms> {
        #[cfg(test)]
        if self.candidate_subset_execution_enabled {
            return Err(GalError::invalid_argument(
                "fixture and lowered selected-source terrain routes cannot execute together",
            ));
        }
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "lowered selected-source terrain execution requires a non-zero world generation",
            ));
        }
        let runtime = self.shader_runtime.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "lowered selected-source terrain execution requires an initialized shader runtime",
            )
        })?;
        let opaque = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted opaque program",
                )
            })?;
        let cutout = runtime
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout)?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted cutout program",
                )
            })?;
        let shadow = runtime
            .prepared_lowered_shadow_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "lowered selected-source terrain execution has no admitted shadow program",
                )
            })?;
        let translucent = runtime
            .prepared_lowered_translucent_terrain_source_program()
            .ok()
            .flatten();
        let programs = LoweredSourceTerrainPrograms {
            opaque,
            cutout,
            shadow,
            translucent,
        };
        let shader_pack_generation = programs.shader_pack_generation()?;
        self.candidate_source_resource_snapshot_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
        )?;
        self.candidate_source_g_buffer_final_binding_for_frame(
            shader_pack_generation,
            world_generation,
            frame_id,
            frame_target,
        )?;
        Ok(programs)
    }
}

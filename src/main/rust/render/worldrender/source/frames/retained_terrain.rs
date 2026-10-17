//! Retained per-mesh records for selected-source terrain batches.
//!
//! A frame prepares several thousand terrain batches. The general path
//! resolves each one through the range memo, the asset map, the geometry map
//! and the frame-data map, and each lookup is a cache miss on a large table.
//! Once a mesh generation's geometry is resident in a page, everything those
//! lookups produce is fixed, so it is kept in one compact record per mesh.
//! A batch then makes one lookup and performs exactly the general path's
//! stream allocation, record staging and command appends, in the same order,
//! so the frame's bytes and draws are unchanged.

use super::*;

/// Bound on remembered ranges per mesh generation; terrain uses a few.
const RETAINED_SOURCE_TERRAIN_RANGES_PER_MESH: usize = 16;

pub(crate) struct RetainedSourceTerrainMesh {
    pub(crate) generation: u64,
    pub(crate) material_ids: bool,
    mesh: Arc<SourceTerrainMeshAsset>,
    pub(crate) page: Handle,
    pub(crate) index_buffer: Handle,
    index_base: u64,
    ranges: SmallVec<[RetainedSourceTerrainRange; 4]>,
    /// Every facing group of the resident mesh, for the scene path; `None`
    /// when a section cannot be drawn there (static chunk terrain only).
    pub(crate) groups: Option<Arc<[SceneTerrainGroup]>>,
}

struct RetainedSourceTerrainRange {
    index_offset: u64,
    index_count: u32,
    kind: u8,
    /// Per selected section, in selection order: absolute first index in
    /// the page's index buffer and index count, as the general path computes
    /// them. Precomputed so a batch never touches the cold mesh asset.
    commands: Arc<[(u32, u32)]>,
}

/// Facts every retained batch of one pass shares: its program, its shared
/// uniform blocks (by identity), their staged offsets, the frame stream and
/// the multi-draw set of each geometry page seen so far. Lives in the
/// frame's batch scope, so it never outlives the transaction it describes.
pub(crate) struct RetainedSourceTerrainPass {
    program: usize,
    legacy_texture_transforms: Arc<[u8]>,
    scalar_uniforms: Arc<[u8]>,
    shared_uniforms: (u64, Option<u64>),
    stream_buffer: Handle,
    instance_range: u64,
    page_sets: SmallVec<[(Handle, Handle); 4]>,
}

/// One batch's instance records, written through a retained record.
pub(crate) struct RetainedSourceTerrainWrite {
    pub(in crate::render::worldrender) resource_set: Handle,
    pub(in crate::render::worldrender) dynamic_offsets: SmallVec<[u64; 3]>,
    pub(in crate::render::worldrender) first_instance: u32,
    pub(in crate::render::worldrender) instance_count: u32,
    pub(in crate::render::worldrender) commands: Arc<[(u32, u32)]>,
    pub(in crate::render::worldrender) index_buffer: Handle,
    page: Handle,
}

/// One resolved draw state of the retained builders, keyed by the identity
/// of its frame-stable inputs (the program, resource snapshot and format list
/// live for the whole terrain plan). Lives in the frame's batch scope.
pub(crate) struct RetainedSourceTerrainDrawState {
    key: (usize, usize, usize, usize, u32, u32, u32, Option<u32>),
    pack_set: Handle,
    pipeline: Handle,
    pipeline_layout: Handle,
}

impl RetainedSourceTerrainWrite {
    /// Index count and multi-draw command of one selected section.
    pub(in crate::render::worldrender) fn section_command(
        &self,
        &(first_index, index_count): &(u32, u32),
    ) -> (u32, PageIndexedDrawCommand) {
        (
            index_count,
            PageIndexedDrawCommand {
                index_count,
                instance_count: self.instance_count,
                first_index,
                vertex_offset: 0,
                first_instance: self.first_instance,
            },
        )
    }
}

pub(in crate::render::worldrender) fn source_terrain_program_range_kind(
    program: &LoweredTerrainSourceProgram,
) -> u8 {
    match program.material_kind {
        Some(TerrainMaterialProgramKind::Opaque) => 0,
        Some(TerrainMaterialProgramKind::Cutout) => 1,
        Some(TerrainMaterialProgramKind::Translucent) => 2,
        None => 3,
    }
}

impl WorldPrimitiveFrontend {
    pub(crate) fn source_terrain_material_ids_active(&self) -> bool {
        self.shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::candidate_runtime_block_state_material_ids)
            .is_some()
    }

    /// Records a range the general path just prepared, once its geometry is
    /// resident in a page and its first upload belongs to a frame.
    pub(crate) fn remember_retained_source_terrain_range(
        &mut self,
        program: &LoweredTerrainSourceProgram,
        mesh_key: u64,
        mesh_generation: u64,
        index_offset: u64,
        index_count: u32,
    ) {
        let material_ids = self.source_terrain_material_ids_active();
        let kind = source_terrain_program_range_kind(program);
        let range_key = SourceTerrainRangeKey {
            mesh_key,
            mesh_generation,
            material_ids,
            index_offset,
            index_count,
            kind,
        };
        // Only slim (geometry-resident) meshes: the general path collects no
        // diagnostic transform probes for them, so skipping it changes nothing.
        let Some(selection) = self
            .source_terrain_range_memo
            .get(&range_key)
            .filter(|selection| selection.mesh.index_bytes.is_empty() && selection.mesh.vertex_bytes.is_empty())
        else {
            return;
        };
        let geometry_key = LoweredSourceTerrainDataKey {
            mesh_key,
            mesh_generation,
            abi: SourceGeometryAbi::Terrain,
        };
        if self.pending_lowered_source_terrain_geometry_uploads.contains_key(&geometry_key) {
            return;
        }
        let Some((page, index_buffer, index_base)) = self
            .lowered_source_terrain_geometry_resources
            .get(&geometry_key)
            .and_then(|resources| {
                resources
                    .paged
                    .map(|(vertex, _)| (vertex.buffer, resources.index_buffer, resources.index_offset()))
            })
        else {
            return;
        };
        // Same arithmetic as the general path's per-section draw.
        let Some(commands) = selection
            .section_indices
            .iter()
            .map(|&section_index| {
                let section = selection.mesh.sections.get(section_index as usize)?;
                let offset = index_base + source_draw_index_offset(section.index_offset, selection.index_subrange);
                let count = selection.index_subrange.map_or(section.index_count, |(_, count)| count);
                Some((u32::try_from(offset / 4).ok()?, count))
            })
            .collect::<Option<Arc<[(u32, u32)]>>>()
        else {
            return;
        };
        let range = RetainedSourceTerrainRange {
            index_offset,
            index_count,
            kind,
            commands,
        };
        let mesh = Arc::clone(&selection.mesh);
        let describe = |frontend: &Self| {
            frontend
                .mesh_assets
                .get(&mesh_key)
                .filter(|asset| asset.mesh_generation == mesh_generation)
                .and_then(|asset| scene_terrain_groups(&asset.sections, &mesh.sections, index_base))
        };
        let groups = describe(self);
        let record = self
            .retained_source_terrain_meshes
            .entry(mesh_key)
            .or_insert_with(|| RetainedSourceTerrainMesh {
                generation: mesh_generation,
                material_ids,
                mesh: Arc::clone(&mesh),
                page,
                index_buffer,
                index_base,
                ranges: SmallVec::new(),
                groups: groups.clone(),
            });
        if record.generation != mesh_generation
            || record.material_ids != material_ids
            || record.page != page
            || record.index_buffer != index_buffer
            || record.index_base != index_base
            || !Arc::ptr_eq(&record.mesh, &mesh)
        {
            *record = RetainedSourceTerrainMesh {
                generation: mesh_generation,
                material_ids,
                mesh,
                page,
                index_buffer,
                index_base,
                ranges: SmallVec::new(),
                groups,
            };
        }
        if record.ranges.len() < RETAINED_SOURCE_TERRAIN_RANGES_PER_MESH
            && !record.ranges.iter().any(|existing| {
                existing.index_offset == index_offset
                    && existing.index_count == index_count
                    && existing.kind == kind
            })
        {
            record.ranges.push(range);
        }
    }

    /// Drops records whose geometry or range selections were invalidated.
    pub(crate) fn forget_retained_source_terrain_meshes(&mut self, mesh_keys: impl IntoIterator<Item = u64>) {
        if self.retained_source_terrain_meshes.is_empty() {
            return;
        }
        for mesh_key in mesh_keys {
            self.retained_source_terrain_meshes.remove(&mesh_key);
        }
    }

    pub(crate) fn clear_retained_source_terrain_meshes(&mut self) {
        self.retained_source_terrain_meshes.clear();
    }

    /// Resolves one batch's retained binding: resource set, shared dynamic
    /// offsets and geometry selection, without allocating or staging. `None`
    /// means a lookup failed and the caller must use the general path.
    #[allow(clippy::too_many_arguments)]
    fn retained_source_terrain_batch_target(
        &self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh_key: u64,
        mesh_generation: u64,
        index_offset: u64,
        index_count: u32,
        instances: &[SourceTerrainInstance],
        legacy_texture_transforms: &Arc<[u8]>,
        scalar_uniforms: &Arc<[u8]>,
    ) -> GalResult<Option<(RetainedSourceTerrainWrite, (u64, Option<u64>), Handle, u64)>> {
        if self.source_terrain_multidraw_frame != Some(frame_id) || instances.is_empty() {
            return Ok(None);
        }
        let program_address = program as *const LoweredTerrainSourceProgram as usize;
        let Some(scope) = self
            .source_terrain_batch_scope
            .as_ref()
            .filter(|scope| scope.frame_id == frame_id && !scope.scalar_uniform_receipts)
        else {
            return Ok(None);
        };
        if !scope.validated_programs.contains(&program_address) {
            return Ok(None);
        }
        let interface = &program.execution_interface;
        if interface.scalar_uniforms.is_some() == scalar_uniforms.is_empty()
            || instances
                .iter()
                .any(|(transform, _)| transform.iter().any(|component| !component.is_finite()))
        {
            return Ok(None);
        }
        let material_ids = self.source_terrain_material_ids_active();
        let kind = source_terrain_program_range_kind(program);
        let Some(record) = self
            .retained_source_terrain_meshes
            .get(&mesh_key)
            .filter(|record| record.generation == mesh_generation && record.material_ids == material_ids)
        else {
            return Ok(None);
        };
        let Some(range) = record.ranges.iter().find(|range| {
            range.index_offset == index_offset && range.index_count == index_count && range.kind == kind
        }) else {
            return Ok(None);
        };
        if !self.pending_lowered_source_terrain_geometry_uploads.is_empty()
            && self.pending_lowered_source_terrain_geometry_uploads.contains_key(&LoweredSourceTerrainDataKey {
                mesh_key,
                mesh_generation,
                abi: SourceGeometryAbi::Terrain,
            })
        {
            return Ok(None);
        }
        let Some((shared_uniforms, stream_buffer, instance_range, resource_set)) = self
            .retained_source_terrain_pass_binding(
                scope,
                program_address,
                frame_id,
                record.page,
                legacy_texture_transforms,
                scalar_uniforms,
            )
        else {
            return Ok(None);
        };
        let mut dynamic_offsets = SmallVec::new();
        dynamic_offsets.push(shared_uniforms.0);
        if let Some(offset) = shared_uniforms.1 {
            dynamic_offsets.push(offset);
        }
        dynamic_offsets.push(0);
        let write = RetainedSourceTerrainWrite {
            resource_set,
            dynamic_offsets,
            first_instance: 0,
            instance_count: u32::try_from(instances.len())
                .map_err(|_| GalError::invalid_argument("source terrain instance count exceeds u32"))?,
            commands: Arc::clone(&range.commands),
            index_buffer: record.index_buffer,
            page: record.page,
        };
        Ok(Some((write, shared_uniforms, stream_buffer, instance_range)))
    }

    /// Resolves the pass-invariant part of a retained batch binding, once
    /// per (program, uniform blocks) and page per frame. `None` sends the
    /// batch to the general path, exactly as each per-batch lookup did.
    fn retained_source_terrain_pass_binding(
        &self,
        scope: &SourceTerrainBatchScope,
        program: usize,
        frame_id: u64,
        page: Handle,
        legacy_texture_transforms: &Arc<[u8]>,
        scalar_uniforms: &Arc<[u8]>,
    ) -> Option<((u64, Option<u64>), Handle, u64, Handle)> {
        let mut passes = scope.retained_passes.borrow_mut();
        let pass_index = match passes.iter().position(|pass| {
            pass.program == program
                && Arc::ptr_eq(&pass.legacy_texture_transforms, legacy_texture_transforms)
                && Arc::ptr_eq(&pass.scalar_uniforms, scalar_uniforms)
        }) {
            Some(index) => index,
            None => {
                // Not cached until the general path has staged these blocks.
                let shared_uniforms = self
                    .pending_source_terrain_frame_transactions
                    .get(&frame_id)
                    .and_then(|transaction| {
                        transaction.shared_uniform_offsets(legacy_texture_transforms, scalar_uniforms)
                    })?;
                let (stream_buffer, slot_capacity) = self
                    .source_terrain_frame_stream_slots
                    .iter()
                    .find(|slot| slot.frame_id == Some(frame_id))
                    .map(|slot| (slot.buffer, slot.capacity))?;
                passes.push(RetainedSourceTerrainPass {
                    program,
                    legacy_texture_transforms: Arc::clone(legacy_texture_transforms),
                    scalar_uniforms: Arc::clone(scalar_uniforms),
                    shared_uniforms,
                    stream_buffer,
                    instance_range: slot_capacity.min(SOURCE_TERRAIN_MULTIDRAW_INSTANCE_RANGE_MAX),
                    page_sets: SmallVec::new(),
                });
                passes.len() - 1
            }
        };
        let pass = &mut passes[pass_index];
        let resource_set = match pass.page_sets.iter().find(|(candidate, _)| *candidate == page) {
            Some(&(_, set)) => set,
            None => {
                let memo_key = SourceTerrainFrameDataMemoKey {
                    program,
                    page,
                    stream_buffer: pass.stream_buffer,
                    instance_bytes: pass.instance_range,
                };
                let set = scope
                    .multidraw_frame_data
                    .iter()
                    .find(|(candidate, _, _)| *candidate == memo_key)
                    .map(|(_, _, resource_set)| *resource_set)?;
                pass.page_sets.push((page, set));
                set
            }
        };
        Some((pass.shared_uniforms, pass.stream_buffer, pass.instance_range, resource_set))
    }

    /// The shadow twin of a retained camera write: the shadow program's set and
    /// shared offsets for the same page, with the camera write's commands and
    /// instance count (twins replay the camera draws' indirect commands).
    /// `None` sends the twin to the slower paths.
    pub(crate) fn retained_source_terrain_shadow_twin_of(
        &self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        camera: &RetainedSourceTerrainWrite,
        legacy_texture_transforms: &Arc<[u8]>,
        scalar_uniforms: &Arc<[u8]>,
    ) -> Option<RetainedSourceTerrainWrite> {
        if self.source_terrain_multidraw_frame != Some(frame_id) {
            return None;
        }
        let program_address = program as *const LoweredTerrainSourceProgram as usize;
        let scope = self
            .source_terrain_batch_scope
            .as_ref()
            .filter(|scope| scope.frame_id == frame_id && !scope.scalar_uniform_receipts)?;
        if !scope.validated_programs.contains(&program_address)
            || program.execution_interface.scalar_uniforms.is_some() == scalar_uniforms.is_empty()
        {
            return None;
        }
        let (shared_uniforms, _, _, resource_set) = self.retained_source_terrain_pass_binding(
            scope,
            program_address,
            frame_id,
            camera.page,
            legacy_texture_transforms,
            scalar_uniforms,
        )?;
        let mut dynamic_offsets = SmallVec::new();
        dynamic_offsets.push(shared_uniforms.0);
        if let Some(offset) = shared_uniforms.1 {
            dynamic_offsets.push(offset);
        }
        dynamic_offsets.push(0);
        Some(RetainedSourceTerrainWrite {
            resource_set,
            dynamic_offsets,
            first_instance: camera.first_instance,
            instance_count: camera.instance_count,
            commands: Arc::clone(&camera.commands),
            index_buffer: camera.index_buffer,
            page: camera.page,
        })
    }

    /// Pipeline and pack set for one retained draw state, resolved once per
    /// frame and state (batches arrive grouped by state).
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn retained_source_terrain_draw_state(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Option<&[TextureFormat]>,
        shadow_alpha_cutoff: Option<f32>,
    ) -> GalResult<(Handle, Handle, Handle)> {
        let key = (
            program as *const LoweredTerrainSourceProgram as usize,
            pack_resources as *const TerrainSourceOwnedResourceSet as usize,
            color_formats.map_or(0, |formats| formats.as_ptr() as usize),
            color_formats.map_or(usize::MAX, <[TextureFormat]>::len),
            material_mode,
            cull_policy,
            winding,
            shadow_alpha_cutoff.map(f32::to_bits),
        );
        if let Some(scope) = self.source_terrain_batch_scope.as_ref() {
            if let Some(state) = scope.retained_draw_states.borrow().iter().find(|state| state.key == key) {
                return Ok((state.pack_set, state.pipeline, state.pipeline_layout));
            }
        }
        let (_, pack_set) = self.resolve_lowered_source_terrain_pack_resources(gal, program, pack_resources)?;
        let (pipeline, pipeline_layout) = match color_formats {
            Some(color_formats) => self.resolve_lowered_source_terrain_pipeline(
                gal,
                program,
                material_mode,
                cull_policy,
                winding,
                color_formats,
                crate::render::vulkanic::resources::RasterYDirection::Up,
                None,
            )?,
            None => self.resolve_lowered_source_shadow_pipeline(
                gal,
                program,
                material_mode,
                cull_policy,
                winding,
                shadow_alpha_cutoff,
            )?,
        };
        if let Some(scope) = self.source_terrain_batch_scope.as_ref() {
            scope.retained_draw_states.borrow_mut().push(RetainedSourceTerrainDrawState {
                key,
                pack_set,
                pipeline,
                pipeline_layout,
            });
        }
        Ok((pack_set, pipeline, pipeline_layout))
    }

    /// Binding for a camera batch's shadow twin. Twin shadow draws replay the
    /// camera draw's indirect commands, whose `first_instance` already selects
    /// the camera write's identical transform/color records in the same frame
    /// stream; the shadow program needs only its own set and shared uniforms.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn bind_retained_source_terrain_shadow_twin(
        &self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh_key: u64,
        mesh_generation: u64,
        index_offset: u64,
        index_count: u32,
        instances: &[SourceTerrainInstance],
        legacy_texture_transforms: &Arc<[u8]>,
        scalar_uniforms: &Arc<[u8]>,
    ) -> GalResult<Option<RetainedSourceTerrainWrite>> {
        Ok(self
            .retained_source_terrain_batch_target(
                program,
                frame_id,
                mesh_key,
                mesh_generation,
                index_offset,
                index_count,
                instances,
                legacy_texture_transforms,
                scalar_uniforms,
            )?
            .map(|(write, _, _, _)| write))
    }

    /// Writes one batch's instance records through its retained record.
    /// Every lookup that can fail happens before any side effect: `None`
    /// means nothing was changed and the caller must use the general path.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn write_retained_source_terrain_batch(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh_key: u64,
        mesh_generation: u64,
        index_offset: u64,
        index_count: u32,
        instances: &[SourceTerrainInstance],
        legacy_texture_transforms: &Arc<[u8]>,
        scalar_uniforms: &Arc<[u8]>,
    ) -> GalResult<Option<RetainedSourceTerrainWrite>> {
        let Some((mut write, shared_uniforms, stream_buffer, instance_range)) = self
            .retained_source_terrain_batch_target(
                program,
                frame_id,
                mesh_key,
                mesh_generation,
                index_offset,
                index_count,
                instances,
                legacy_texture_transforms,
                scalar_uniforms,
            )?
        else {
            return Ok(None);
        };
        let interface = &program.execution_interface;

        // Side effects begin: the general path's allocation and staging.
        let mut records = std::mem::take(&mut self.retained_source_terrain_instance_scratch);
        records.clear();
        records.reserve(instances.len() * TERRAIN_SOURCE_INSTANCE_BYTES);
        // Same encoding as `pack_source_terrain_instances`.
        for (transform, color_argb) in instances {
            for component in transform {
                push_f32(&mut records, *component);
            }
            for component in argb_to_rgba(*color_argb) {
                push_f32(&mut records, component);
            }
        }
        let result = (|| {
            let stream = self.allocate_source_terrain_frame_stream_aligned(
                gal,
                frame_id,
                u64::from(interface.legacy_transform_bytes),
                u64::from(interface.scalar_uniform_bytes),
                records.len() as u64,
                Some(shared_uniforms),
                SOURCE_TERRAIN_MULTIDRAW_INSTANCE_ALIGNMENT,
            )?;
            if stream.buffer != stream_buffer {
                return Err(GalError::backend("source multi-draw stream slot changed during a frame"));
            }
            if stream.instance_offset + records.len() as u64 > instance_range {
                return Err(GalError::unsupported_feature(
                    "source multi-draw instance records exceed the bindable stream range",
                ));
            }
            write.first_instance =
                u32::try_from(stream.instance_offset / TERRAIN_SOURCE_INSTANCE_BYTES as u64).map_err(|_| {
                    GalError::invalid_argument("source multi-draw first instance exceeds u32")
                })?;
            let transaction = self
                .pending_source_terrain_frame_transactions
                .get_mut(&frame_id)
                .ok_or_else(|| GalError::backend("source terrain frame transaction vanished"))?;
            if transaction.stream_buffer != stream.buffer || transaction.stream_epoch != stream.epoch {
                return Err(GalError::backend(
                    "source terrain frame payloads resolved to different stream slots",
                ));
            }
            if stream.legacy_transform_offset != shared_uniforms.0
                || stream.scalar_uniform_offset != shared_uniforms.1
            {
                return Err(GalError::backend("source multi-draw stream moved its shared uniforms"));
            }
            transaction.stage_stream_write(stream.instance_offset, &records);
            Ok(())
        })();
        self.retained_source_terrain_instance_scratch = records;
        result.map(|()| Some(write))
    }
}

impl WorldPrimitiveFrontend {
    /// Camera draws for a retained batch, as
    /// `prepare_lowered_source_terrain_draws_for_color_formats` builds them.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn retained_source_terrain_color_draws(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        write: &RetainedSourceTerrainWrite,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: &[TextureFormat],
        frame_id: u64,
    ) -> GalResult<Vec<TerrainMeshDraw>> {
        let (pack_resource_set, pipeline, pipeline_layout) = self.retained_source_terrain_draw_state(
            gal,
            program,
            pack_resources,
            material_mode,
            cull_policy,
            winding,
            Some(color_formats),
            None,
        )?;
        let material_mode = terrain_material_pass_mode(material_mode)?;
        write
            .commands
            .iter()
            .map(|section| {
                let (index_count, command) = write.section_command(section);
                let indexed_indirect = self.append_source_terrain_multidraw_command(frame_id, command)?;
                Ok(TerrainMeshDraw {
                    shadow: None,
                    pipeline,
                    offscreen_pipeline: None,
                    pipeline_layout,
                    resource_set: write.resource_set,
                    resource_set_dynamic_offsets: write.dynamic_offsets.clone(),
                    shader_resource_set: Some(TerrainShaderResourceSet {
                        set_index: 1,
                        set: pack_resource_set,
                    }),
                    index_buffer: write.index_buffer,
                    index_offset: 0,
                    index_type: IndexType::U32,
                    index_count,
                    instance_count: write.instance_count,
                    indexed_indirect: Some(indexed_indirect),
                    stratum: WORLD_STRATUM_TERRAIN,
                    material_mode,
                    shadow_participation: TerrainShadowParticipation::Required,
                })
            })
            .collect()
    }

    /// Shadow bindings for a retained batch, as
    /// `prepare_lowered_source_shadow_draws_with_first_instance` builds them.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn retained_source_terrain_shadow_draws(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        write: &RetainedSourceTerrainWrite,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        shadow_alpha_cutoff: Option<f32>,
    ) -> GalResult<Vec<TerrainShadowDraw>> {
        let (pack_resource_set, pipeline, pipeline_layout) = self.retained_source_terrain_draw_state(
            gal,
            program,
            pack_resources,
            material_mode,
            cull_policy,
            winding,
            None,
            shadow_alpha_cutoff,
        )?;
        Ok(write
            .commands
            .iter()
            .map(|_| TerrainShadowDraw {
                pipeline,
                pipeline_layout,
                resource_set: write.resource_set,
                resource_set_dynamic_offsets: write.dynamic_offsets.clone(),
                shader_resource_set: Some(TerrainShaderResourceSet {
                    set_index: 1,
                    set: pack_resource_set,
                }),
            })
            .collect())
    }

    /// Shadow-only caster draws for a retained batch, as
    /// `prepare_lowered_source_shadow_only_draws` builds them.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn retained_source_terrain_shadow_only_draws(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTerrainSourceProgram,
        write: &RetainedSourceTerrainWrite,
        pack_resources: &TerrainSourceOwnedResourceSet,
        material_mode: u32,
        cull_policy: u32,
        winding: u32,
        shadow_alpha_cutoff: Option<f32>,
        frame_id: u64,
    ) -> GalResult<Vec<TerrainShadowMeshDraw>> {
        let shadows = self.retained_source_terrain_shadow_draws(
            gal, program, write, pack_resources, material_mode, cull_policy, winding, shadow_alpha_cutoff,
        )?;
        let pass_mode = terrain_material_pass_mode(material_mode)?;
        write
            .commands
            .iter()
            .zip(shadows)
            .map(|(section, shadow)| {
                let (index_count, command) = write.section_command(section);
                let indexed_indirect = self.append_source_terrain_multidraw_command(frame_id, command)?;
                Ok(TerrainShadowMeshDraw {
                    shadow,
                    index_buffer: write.index_buffer,
                    index_offset: 0,
                    index_type: IndexType::U32,
                    index_count,
                    instance_count: write.instance_count,
                    indexed_indirect: Some(indexed_indirect),
                    material_mode: pass_mode,
                })
            })
            .collect()
    }
}

impl WorldPrimitiveFrontend {
    /// Orders source terrain batches for indirect-draw merging. Opaque and
    /// cutout terrain is depth-tested without blending, so its batch order
    /// only decides which consecutive draws share state: grouping by material
    /// mode and retained geometry page keeps each pass's indirect commands
    /// contiguous under one resource set. Translucent batches keep their
    /// incoming order (stable sort, single key).
    pub(in crate::render::worldrender) fn order_source_terrain_batches_for_multidraw<B: std::borrow::Borrow<MeshBatch>>(
        &self,
        batches: &mut [B],
    ) {
        batches.sort_by_cached_key(|batch| {
            let key = &batch.borrow().key;
            if key.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT {
                return (1, 0, None);
            }
            let page = self
                .retained_source_terrain_meshes
                .get(&key.mesh_key)
                .filter(|record| record.generation == key.mesh_generation)
                .map(|record| record.page);
            (0, key.material_mode, page)
        });
    }
}

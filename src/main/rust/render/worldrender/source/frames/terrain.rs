//! Source terrain frames: mesh assets, paged geometry, frame transactions and depth history.

use super::*;

/// Keep source shadow material admission shared by capacity planning and draw
/// construction. The pack policy can independently disable translucent
/// casters before either step.
pub(crate) fn source_shadow_required_for_material_mode(material_mode: u32) -> bool {
    matches!(
        material_mode,
        WORLD_MATERIAL_MODE_OPAQUE
            | WORLD_MATERIAL_MODE_CUTOUT
            | WORLD_MATERIAL_MODE_TRANSLUCENT
    )
}

pub(crate) fn source_shadow_instance_intersects(
    frustum: &crate::render::shaderpack::properties::shadow::AdvancedShadowCasterFrustum,
    instance: &WorldMeshInstanceRequest,
    distance_limit: Option<f32>,
) -> bool {
    let origin = [instance.transform[12], instance.transform[13], instance.transform[14]];
    if !distance_limit.is_none_or(|limit| source_shadow_section_within_vanilla_distance(origin, limit)) {
        return false;
    }
    // Sodium's shadow Viewport tests a section centered at origin+8 with
    // radius 8+1+1/8: one block of model overhang plus frustum allowance.
    let min = origin.map(|coordinate| coordinate - 1.125);
    let max = min.map(|coordinate| coordinate + 18.25);
    frustum.intersects(min, max)
}

pub(crate) fn source_shadow_section_within_vanilla_distance(origin: [f32; 3], limit: f32) -> bool {
    // Frozen's Sodium shadow tree still uses its normal render-distance
    // cylinder even when Iris replaces the camera frustum. The copied `far`
    // scalar is effective render distance * 16 blocks on this source route.
    // Its overhang envelope is one block, distinct from the 1/8 precision
    // extension on the light-frustum section AABB above.
    let closest = origin.map(|coordinate| {
        let min = coordinate - 1.0;
        let max = coordinate + 17.0;
        if min > 0.0 { min } else if max < 0.0 { max } else { 0.0 }
    });
    closest[0] * closest[0] + closest[2] * closest[2] < limit * limit
        && closest[1].abs() < limit
}

/// Immutable, caller-independent data needed to bind one source-derived
/// terrain mesh for a frame. It is still CPU preparation only: no GAL
/// handles, shader route selection, or backend state is present here.
#[derive(Clone, Debug)]
pub(crate) struct PreparedSourceTerrainFrame {
    /// Explicit semantic render-frame ownership for a future bounded stream
    /// allocation. This is a correlation ID, never a backend frame slot.
    pub frame_id: u64,
    pub mesh: Arc<SourceTerrainMeshAsset>,
    /// Source-mesh section ordinals selected by the semantic frame batch.
    /// They retain the original opaque/cutout admission and ordering without
    /// making a future source route expand unrelated mesh sections.
    pub section_indices: Vec<u32>,
    /// A camera-sorted translucent run inside the single selected section:
    /// `(first_index, index_count)` relative to that section.
    pub index_subrange: Option<(u32, u32)>,
    pub legacy_texture_transforms: Vec<u8>,
    pub scalar_uniforms: Vec<u8>,
    pub instance_transforms: Vec<u8>,
}

/// One owned, one-shot upload transaction for a semantic source-terrain
/// frame. The caller must append these operations to the same GAL submission
/// as the draws that consume them, then confirm that submission by frame ID.
/// Keeping this out of the generic world-op queue prevents a later frame from
/// accidentally submitting stale dynamic data.
pub(crate) struct SourceTerrainFrameTransaction {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) stream_buffer: Handle,
    pub(in crate::render::worldrender) stream_epoch: u64,
    pub(in crate::render::worldrender) operations: Vec<CommandOp>,
    pub(in crate::render::worldrender) source_material_texture_ids: BTreeSet<u32>,
    /// First uploads of cached immutable source geometry. They are kept apart
    /// from frame payloads so a discarded frame can return them to the pending
    /// queue; otherwise the cached buffers would be drawn uninitialized.
    pub(in crate::render::worldrender) geometry_uploads: Vec<(LoweredSourceTerrainDataKey, Vec<CommandOp>)>,
    /// Frame-stream payload bytes from offset zero, written by one host
    /// write at submission instead of five ops per draw.
    pub(in crate::render::worldrender) stream_staging: Vec<u8>,
    /// Uniform blocks already staged this frame, as (legacy, scalar,
    /// legacy offset, scalar offset); equal blocks share one stream copy.
    pub(in crate::render::worldrender) shared_uniforms: Vec<(Vec<u8>, Vec<u8>, u64, Option<u64>)>,
    /// Multi-draw commands staged for the slot's indirect buffer.
    pub(in crate::render::worldrender) indirect_buffer: Option<Handle>,
    pub(in crate::render::worldrender) indirect_staging: Vec<u8>,
}

pub(crate) struct PendingSourceMaterialTextureUpload {
    pub(in crate::render::worldrender) texture_id: u32,
    pub(in crate::render::worldrender) operations: Vec<CommandOp>,
}

/// Returned only when a source frame transaction transfers its commands to a
/// combined submission. Requiring this token for confirmation keeps the
/// stream slot tied to that exact transaction rather than a caller-supplied
/// frame number.
pub(crate) struct SourceTerrainFrameSubmission {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) stream_buffer: Handle,
    pub(in crate::render::worldrender) stream_epoch: u64,
    pub(in crate::render::worldrender) source_material_texture_ids: BTreeSet<u32>,
    /// Subset whose copy operation is present in the final source command
    /// stream. Residency is promoted only from this explicit evidence.
    pub(in crate::render::worldrender) uploaded_source_material_texture_ids: BTreeSet<u32>,
    /// Geometry uploads carried by this submission, re-queued if it is
    /// discarded before confirmation.
    pub(in crate::render::worldrender) geometry_uploads: Vec<(LoweredSourceTerrainDataKey, Vec<CommandOp>)>,
}

impl SourceTerrainFrameTransaction {
    pub(crate) fn stage_stream_write(&mut self, offset: u64, data: &[u8]) {
        let start = offset as usize;
        let end = start + data.len();
        if self.stream_staging.len() < end {
            self.stream_staging.resize(end, 0);
        }
        self.stream_staging[start..end].copy_from_slice(data);
    }

    pub(in crate::render::worldrender) fn stage_stream_parts(
        &mut self,
        stream: SourceTerrainFrameStreamAllocation,
        legacy_texture_transforms: &[u8],
        scalar_uniforms: &[u8],
        payload: &[u8],
    ) {
        self.stage_stream_write(stream.legacy_transform_offset, legacy_texture_transforms);
        if let Some(offset) = stream.scalar_uniform_offset {
            self.stage_stream_write(offset, scalar_uniforms);
        }
        self.stage_stream_write(stream.instance_offset, payload);
    }

    pub(crate) fn shared_uniform_offsets(&self, legacy: &[u8], scalar: &[u8]) -> Option<(u64, Option<u64>)> {
        self.shared_uniforms
            .iter()
            .find(|entry| entry.0 == legacy && entry.1 == scalar)
            .map(|entry| (entry.2, entry.3))
    }

    pub(crate) fn into_submission_parts(self) -> (Vec<CommandOp>, SourceTerrainFrameSubmission) {
        let mut operations = self
            .geometry_uploads
            .iter()
            .flat_map(|(_, uploads)| uploads.iter().cloned())
            .collect::<Vec<_>>();
        operations.extend(self.operations);
        if !self.stream_staging.is_empty() {
            operations.push(CommandOp::Barrier(buffer_barrier(
                self.stream_buffer,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer: self.stream_buffer,
                offset: 0,
                data: self.stream_staging,
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                self.stream_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
        }
        if let Some(indirect_buffer) = self.indirect_buffer.filter(|_| !self.indirect_staging.is_empty()) {
            operations.push(CommandOp::Barrier(buffer_barrier(
                indirect_buffer,
                TextureUsageState::IndirectRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::HostWriteBuffer {
                buffer: indirect_buffer,
                offset: 0,
                data: self.indirect_staging,
            });
            operations.push(CommandOp::Barrier(buffer_barrier(
                indirect_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::IndirectRead,
            )));
        }
        (
            operations,
            SourceTerrainFrameSubmission {
                frame_id: self.frame_id,
                stream_buffer: self.stream_buffer,
                stream_epoch: self.stream_epoch,
                source_material_texture_ids: self.source_material_texture_ids,
                uploaded_source_material_texture_ids: BTreeSet::new(),
                geometry_uploads: self.geometry_uploads,
            },
        )
    }
}

impl WorldPrimitiveFrontend {
    /// The selected source route consumes its own lowered geometry and never
    /// records an ordinary indexed-mesh draw. Snapshot preparation may have
    /// populated ordinary resources while deriving exact semantic state, but
    /// retaining those buffers alongside the selected source ABI doubles
    /// streamed-world residency. Retire only the ordinary execution bindings
    /// here; mesh semantics, textures, pipelines, and all lowered source
    /// resources remain available to the source transaction. `VulkanicGal`
    /// keeps any still-in-flight prior submission alive through its explicit
    /// completion-aware destruction path.
    pub(crate) fn retire_ordinary_mesh_execution_resources_for_selected_source(&mut self) {
        let resources = std::mem::take(&mut self.mesh_resources);
        for (_, resources) in resources {
            self.deferred_mesh_resource_destroys
                .extend(resources.handles_in_destroy_order());
        }
        let geometry = std::mem::take(&mut self.mesh_geometry_resources);
        for (_, resources) in geometry {
            self.deferred_mesh_geometry_range_releases.push(resources);
        }
        let legacy_source_resources = std::mem::take(&mut self.source_mesh_resources);
        for (_, resources) in legacy_source_resources {
            self.deferred_mesh_resource_destroys
                .extend(resources.handles_in_destroy_order());
        }
    }

    pub(crate) fn ensure_source_mesh_generation(
        &self,
        route: &str,
        mesh_key: u64,
        mesh_generation: u64,
    ) -> GalResult<()> {
        let asset = self.mesh_assets.get(&mesh_key).ok_or_else(|| {
            GalError::invalid_argument(format!(
                "source {} mesh {} is missing from the asset cache",
                route, mesh_key
            ))
        })?;
        if asset.mesh_generation != mesh_generation {
            return Err(GalError::invalid_argument(format!(
                "source {} mesh {} generation {} does not match cached generation {}",
                route, mesh_key, mesh_generation, asset.mesh_generation
            )));
        }
        Ok(())
    }

    /// Expands copied Rust semantic input into an immutable stream owned by
    /// the selected source frame. The world-asset cache intentionally never
    /// retains this expanded ABI: it is substantially larger than the exact
    /// input and retaining one per streamed section made source execution
    /// unbounded. A later frame recreates it from the same owned semantics.
    pub(crate) fn source_terrain_mesh_asset(
        &mut self,
        mesh_key: u64,
        mesh_generation: u64,
    ) -> GalResult<Arc<SourceTerrainMeshAsset>> {
        let material_ids = self
            .shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::candidate_runtime_block_state_material_ids);
        let memo_key = (mesh_key, mesh_generation, material_ids.is_some());
        if let Some(mesh) = self
            .source_terrain_mesh_frame_memo
            .as_ref()
            .and_then(|memo| memo.get(&memo_key))
        {
            // A frame-memo entry was generation-checked when inserted, and
            // mesh assets do not change while a frame is being planned.
            return Ok(Arc::clone(mesh));
        }
        self.ensure_source_mesh_generation("terrain", mesh_key, mesh_generation)?;
        // Once a mesh's source geometry is resident on the GPU its converted
        // bytes are never read again (only section metadata is), so the cache
        // keeps a byte-free entry. A slim entry whose geometry has since been
        // released is converted again.
        let geometry_resident = self.lowered_source_terrain_geometry_resources.contains_key(
            &LoweredSourceTerrainDataKey {
                mesh_key,
                mesh_generation,
                abi: SourceGeometryAbi::Terrain,
            },
        );
        if let Some(mesh) = self.source_terrain_mesh_cache.get(&memo_key) {
            let usable = if mesh.vertex_bytes.is_empty() {
                geometry_resident.then_some(mesh)
            } else if geometry_resident {
                let slim = Arc::new(SourceTerrainMeshAsset {
                    mesh_key: mesh.mesh_key,
                    mesh_generation: mesh.mesh_generation,
                    vertex_bytes: Vec::new(),
                    index_bytes: Vec::new(),
                    sections: mesh.sections.clone(),
                });
                self.source_terrain_mesh_cache.insert(memo_key, Arc::clone(&slim));
                Some(slim)
            } else {
                Some(mesh)
            };
            if let Some(mesh) = usable {
                if let Some(memo) = self.source_terrain_mesh_frame_memo.as_mut() {
                    memo.insert(memo_key, Arc::clone(&mesh));
                }
                return Ok(mesh);
            }
        }
        let asset = self
            .mesh_assets
            .get(&mesh_key)
            .expect("checked mesh asset exists");
        let input = asset.source_terrain_view(mesh_key).ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "source terrain mesh {} generation {} is unavailable: world mesh uses no source-compatible vertex layout",
                mesh_key, mesh_generation
            ))
        })?;
        let mesh = prepare_source_terrain_mesh_asset_view_with_material_ids(&input, material_ids)
            .map(Arc::new)
            .map_err(|error| {
                GalError::unsupported_feature(format!(
                    "source terrain mesh {} generation {} is unavailable: {}",
                    mesh_key, mesh_generation, error
                ))
            })?;
        // Converted streams are immutable per mesh generation: validate once
        // here instead of on every per-frame draw preparation.
        mesh.validate()?;
        if let Some(memo) = self.source_terrain_mesh_frame_memo.as_mut() {
            memo.insert(memo_key, Arc::clone(&mesh));
        }
        self.source_terrain_mesh_cache.insert(memo_key, Arc::clone(&mesh));
        Ok(mesh)
    }

    /// Produces the complete owned CPU payload for a later source-derived
    /// terrain binding. It verifies the shader source's fixed execution ABI
    /// against the cached source mesh before packing the named scalar and
    /// transform semantics. This deliberately creates neither a GAL buffer
    /// nor a resource set, keeping incomplete selected-source execution
    /// unavailable outside focused preparation tests.
    pub(crate) fn prepare_source_terrain_frame(
        &mut self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh_key: u64,
        mesh_generation: u64,
        section_indices: &[u32],
        transforms: &[[f32; 16]],
        texture_transforms: &TerrainSourceTextureTransforms,
        uniform_frame: &TerrainSourceUniformFrame,
    ) -> GalResult<PreparedSourceTerrainFrame> {
        let instances = transforms
            .iter()
            .copied()
            .map(|transform| (transform, u32::MAX))
            .collect::<Vec<_>>();
        self.prepare_source_terrain_frame_with_instances(
            program,
            frame_id,
            mesh_key,
            mesh_generation,
            section_indices,
            &instances,
            texture_transforms,
            uniform_frame,
        )
    }

    pub(crate) fn source_program_prevalidated(&self, frame_id: u64, program: &LoweredTerrainSourceProgram) -> bool {
        let address = program as *const LoweredTerrainSourceProgram as usize;
        self.source_terrain_batch_scope.as_ref().is_some_and(|scope| {
            scope.frame_id == frame_id && scope.validated_programs.contains(&address)
        })
    }

    pub(crate) fn open_source_terrain_batch_scope(
        &mut self,
        frame_id: u64,
        programs: &[&LoweredTerrainSourceProgram],
    ) -> GalResult<()> {
        self.source_terrain_batch_scope = None;
        let mut validated_programs = Vec::with_capacity(programs.len());
        for program in programs {
            program.execution_interface.validate()?;
            validated_programs.push(*program as *const LoweredTerrainSourceProgram as usize);
        }
        self.source_terrain_batch_scope = Some(SourceTerrainBatchScope {
            frame_id,
            scalar_uniform_receipts: crate::core::environment::var_os("MATTMC_RUST_SELECTED_SOURCE_FRAGMENT_PROBE")
                .is_some(),
            validated_programs,
            pack_keys: Vec::new(),
        });
        Ok(())
    }

    pub(crate) fn prepare_source_terrain_frame_with_instances(
        &mut self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh_key: u64,
        mesh_generation: u64,
        section_indices: &[u32],
        instances: &[SourceTerrainInstance],
        texture_transforms: &TerrainSourceTextureTransforms,
        uniform_frame: &TerrainSourceUniformFrame,
    ) -> GalResult<PreparedSourceTerrainFrame> {
        if !self.source_program_prevalidated(frame_id, program) {
            program.execution_interface.validate()?;
        }
        if instances.is_empty() {
            return Err(GalError::invalid_argument(
                "source terrain frame requires at least one semantic instance transform",
            ));
        }
        let mesh = self.source_terrain_mesh_asset(mesh_key, mesh_generation)?;
        // Validated once when converted (source_terrain_mesh_asset).
        if section_indices.is_empty() {
            return Err(GalError::invalid_argument(
                "source terrain frame requires at least one selected mesh section",
            ));
        }
        let mut selected_sections = BTreeSet::new();
        for &section_index in section_indices {
            if section_index as usize >= mesh.sections.len() {
                return Err(GalError::invalid_argument(format!(
                    "source terrain frame selects missing mesh section {} for mesh {}",
                    section_index, mesh.mesh_key
                )));
            }
            if !selected_sections.insert(section_index) {
                return Err(GalError::invalid_argument(format!(
                    "source terrain frame selects mesh section {} more than once",
                    section_index
                )));
            }
        }
        // A slim cached mesh (GPU geometry resident) carries no bytes; its
        // stride was checked when it was converted and uploaded.
        if !mesh.vertex_bytes.is_empty()
            && mesh.vertex_bytes.len() % program.execution_interface.vertex_stride as usize != 0
        {
            return Err(GalError::invalid_argument(format!(
                "source terrain mesh {} does not match program vertex stride {}",
                mesh_key, program.execution_interface.vertex_stride
            )));
        }
        self.prepare_source_terrain_frame_payload(
            program,
            frame_id,
            mesh,
            section_indices.to_vec(),
            instances,
            texture_transforms,
            uniform_frame,
        )
    }

    /// Packs one validated range's frame payload (uniforms and instances).
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn prepare_source_terrain_frame_payload(
        &mut self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh: Arc<SourceTerrainMeshAsset>,
        section_indices: Vec<u32>,
        instances: &[SourceTerrainInstance],
        texture_transforms: &TerrainSourceTextureTransforms,
        uniform_frame: &TerrainSourceUniformFrame,
    ) -> GalResult<PreparedSourceTerrainFrame> {
        // Batches of one program in a frame almost always share an identical
        // uniform frame (only the render stage varies by pass), so reuse the
        // packed bytes for an equal (program, uniform frame, transforms).
        let program_key = (
            program as *const LoweredTerrainSourceProgram as usize,
            program.shader_pack_generation,
        );
        if self
            .source_uniform_pack_memo
            .as_ref()
            .is_none_or(|(memo_frame, _)| *memo_frame != frame_id)
        {
            self.source_uniform_pack_memo = Some((frame_id, Vec::new()));
        }
        let cached = self.source_uniform_pack_memo.as_ref().and_then(|(_, memo)| {
            memo.iter()
                .find(|entry| {
                    entry.0 == program_key && &entry.1 == uniform_frame && &entry.2 == texture_transforms
                })
                .map(|entry| (entry.3.clone(), entry.4.clone()))
        });
        let (legacy_texture_transforms, scalar_uniforms) = match cached {
            Some(packed) => packed,
            None => {
                let legacy = program.pack_legacy_texture_transforms(texture_transforms)?;
                let scalar = program.pack_scalar_uniforms(uniform_frame)?;
                if let Some((_, memo)) = self.source_uniform_pack_memo.as_mut() {
                    if memo.len() < 64 {
                        memo.push((
                            program_key,
                            uniform_frame.clone(),
                            texture_transforms.clone(),
                            legacy.clone(),
                            scalar.clone(),
                        ));
                    }
                }
                (legacy, scalar)
            }
        };
        if self
            .source_terrain_batch_scope
            .as_ref()
            .is_none_or(|scope| scope.scalar_uniform_receipts)
        {
            self.write_selected_source_scalar_uniform_receipt(frame_id, program, &scalar_uniforms);
        }
        let instance_transforms = pack_source_terrain_instances(instances)?;
        if instance_transforms.len()
            != instances
                .len()
                .checked_mul(program.execution_interface.instance_stride as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument("source terrain instance payload length overflows")
                })?
        {
            return Err(GalError::invalid_argument(
                "source terrain instance payload does not match the source program ABI",
            ));
        }
        Ok(PreparedSourceTerrainFrame {
            frame_id,
            mesh,
            section_indices,
            index_subrange: None,
            legacy_texture_transforms,
            scalar_uniforms,
            instance_transforms,
        })
    }

    pub(crate) fn prepare_source_terrain_frame_for_mesh_range(
        &mut self,
        program: &LoweredTerrainSourceProgram,
        frame_id: u64,
        mesh_key: u64,
        mesh_generation: u64,
        index_offset: u64,
        index_count: u32,
        instances: &[SourceTerrainInstance],
        texture_transforms: &TerrainSourceTextureTransforms,
        uniform_frame: &TerrainSourceUniformFrame,
    ) -> GalResult<PreparedSourceTerrainFrame> {
        let range_key = SourceTerrainRangeKey {
            mesh_key,
            mesh_generation,
            material_ids: self
                .shader_runtime
                .as_ref()
                .and_then(ShaderPackRuntimeExecutor::candidate_runtime_block_state_material_ids)
                .is_some(),
            index_offset,
            index_count,
            kind: match program.material_kind {
                Some(TerrainMaterialProgramKind::Opaque) => 0,
                Some(TerrainMaterialProgramKind::Cutout) => 1,
                Some(TerrainMaterialProgramKind::Translucent) => 2,
                None => 3,
            },
        };
        if let Some(selection) = self.source_terrain_range_memo.get(&range_key).cloned() {
            // Validated when memoized; the entry is dropped with its geometry.
            if !self.source_program_prevalidated(frame_id, program) {
                program.execution_interface.validate()?;
            }
            if instances.is_empty() {
                return Err(GalError::invalid_argument(
                    "source terrain frame requires at least one semantic instance transform",
                ));
            }
            let mut prepared = self.prepare_source_terrain_frame_payload(
                program,
                frame_id,
                selection.mesh,
                selection.section_indices,
                instances,
                texture_transforms,
                uniform_frame,
            )?;
            prepared.index_subrange = selection.index_subrange;
            return Ok(prepared);
        }
        {
            let asset = self.mesh_assets.get(&mesh_key).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "source terrain range references unknown mesh key {}",
                    mesh_key
                ))
            })?;
            if asset.mesh_generation != mesh_generation {
                return Err(GalError::invalid_argument(format!(
                    "source terrain range mesh {} generation {} does not match cached generation {}",
                    mesh_key, mesh_generation, asset.mesh_generation
                )));
            }
        }
        let source_mesh = self.source_terrain_mesh_asset(mesh_key, mesh_generation)?;
        // Borrow (not clone) the ordinary sections; the borrow ends before
        // the mutable preparation call below.
        let asset = self
            .mesh_assets
            .get(&mesh_key)
            .expect("mesh asset checked above");
        let index_type = asset.index_type;
        let sections = asset.sections.as_slice();
        let (section_indices, index_subrange) = match source_section_indices_for_mesh_range(
            &sections,
            index_type,
            source_mesh.as_ref(),
            index_offset,
            index_count,
        ) {
            Ok(section_indices) => (section_indices, None),
            Err(error) => {
                let Some((section, first_index, count)) = source_translucent_subrange_for_mesh_range(
                    &sections,
                    index_type,
                    index_offset,
                    index_count,
                ) else {
                    return Err(error);
                };
                let source_section = source_mesh.sections.get(section as usize).ok_or_else(|| {
                    GalError::invalid_argument("sorted translucent run selects a missing section")
                })?;
                if source_section.index_count != sections[section as usize].index_count {
                    return Err(error);
                }
                (vec![section], Some((first_index, count)))
            }
        };
        match program.material_kind {
            Some(TerrainMaterialProgramKind::Opaque) => {
                Self::validate_source_range_material_mode(
                    &sections,
                    &section_indices,
                    WORLD_MATERIAL_MODE_OPAQUE,
                    program.material_kind,
                )?;
            }
            Some(TerrainMaterialProgramKind::Cutout) => {
                Self::validate_source_range_material_mode(
                    &sections,
                    &section_indices,
                    WORLD_MATERIAL_MODE_CUTOUT,
                    program.material_kind,
                )?;
            }
            Some(TerrainMaterialProgramKind::Translucent) => {
                Self::validate_source_range_material_mode(
                    &sections,
                    &section_indices,
                    WORLD_MATERIAL_MODE_TRANSLUCENT,
                    program.material_kind,
                )?;
            }
            // Source shadow geometry deliberately reuses the same copied
            // opaque/cutout/translucent ranges, but it is not a material pass. Its own
            // preparation and draw path still select shadow targets/pipeline
            // state below; this only prevents the generic range checker from
            // rejecting a valid shadow-only program before that boundary.
            None => {
                for &section_index in &section_indices {
                    let section = &sections[section_index as usize];
                    if !matches!(
                        section.material_mode,
                        WORLD_MATERIAL_MODE_OPAQUE
                            | WORLD_MATERIAL_MODE_CUTOUT
                            | WORLD_MATERIAL_MODE_TRANSLUCENT
                    ) {
                        return Err(GalError::invalid_argument(format!(
                            "source shadow range selected section {} with unsupported material mode {}",
                            section_index, section.material_mode
                        )));
                    }
                }
            }
        }
        let mut prepared = self.prepare_source_terrain_frame_with_instances(
            program,
            frame_id,
            mesh_key,
            mesh_generation,
            &section_indices,
            instances,
            texture_transforms,
            uniform_frame,
        )?;
        prepared.index_subrange = index_subrange;
        // Only slim meshes (geometry resident) are memoized, so the memo never
        // pins converted bytes against the source mesh cache budget.
        if prepared.mesh.vertex_bytes.is_empty() {
            if self.source_terrain_range_memo.len() >= SOURCE_TERRAIN_RANGE_MEMO_MAX_ENTRIES {
                self.source_terrain_range_memo.clear();
            }
            self.source_terrain_range_memo.insert(
                range_key,
                SourceTerrainRangeSelection {
                    mesh: Arc::clone(&prepared.mesh),
                    section_indices: prepared.section_indices.clone(),
                    index_subrange,
                },
            );
        }
        Ok(prepared)
    }

    /// Places one terrain mesh's source stream in the shared geometry pages.
    /// Indices are rebased to the page's vertex origin, so an ordinary indexed
    /// draw of the page reads exactly this mesh's vertices.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn ensure_paged_source_terrain_geometry(
        &mut self,
        gal: &mut VulkanicGal,
        key: LoweredSourceTerrainDataKey,
        label: &str,
        vertex_bytes: &[u8],
        index_bytes: &[u8],
        byte_size: u64,
        device_local: bool,
    ) -> GalResult<LoweredSourceTerrainDataKey> {
        let stride = TERRAIN_SOURCE_VERTEX_BYTES as u64;
        if vertex_bytes.is_empty()
            || index_bytes.is_empty()
            || vertex_bytes.len() as u64 % stride != 0
            || index_bytes.len() % std::mem::size_of::<u32>() != 0
        {
            return Err(GalError::invalid_argument(
                "paged source terrain geometry requires whole vertices and u32 indices",
            ));
        }
        let pages = &mut self.source_terrain_geometry_pages;
        let vertex = pages.allocate_vertex(gal, vertex_bytes.len() as u64, device_local)?;
        let index = match pages.allocate_index(gal, index_bytes.len() as u64, device_local) {
            Ok(index) => index,
            Err(error) => {
                pages.release_now(vertex);
                return Err(error);
            }
        };
        let rebased = (|| -> GalResult<Vec<u8>> {
            let base_vertex = u32::try_from(vertex.offset / stride).map_err(|_| {
                GalError::invalid_argument("paged source terrain vertex origin exceeds u32")
            })?;
            let mut rebased = Vec::with_capacity(index_bytes.len());
            for chunk in index_bytes.chunks_exact(4) {
                let value = u32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
                let value = value.checked_add(base_vertex).ok_or_else(|| {
                    GalError::invalid_argument("paged source terrain index overflows u32")
                })?;
                rebased.extend_from_slice(&value.to_le_bytes());
            }
            Ok(rebased)
        })();
        let staging = rebased.and_then(|rebased| {
            let staging = if device_local {
                Some(gal.create_buffer(BufferDesc {
                    label: format!("{label}.staging"),
                    size: byte_size,
                    memory: MemoryDomain::Upload,
                    usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
                })?)
            } else {
                None
            };
            Ok((rebased, staging))
        });
        let (rebased, staging_buffer) = match staging {
            Ok(value) => value,
            Err(error) => {
                let pages = &mut self.source_terrain_geometry_pages;
                pages.release_now(vertex);
                pages.release_now(index);
                return Err(error);
            }
        };
        let vertex_size = vertex_bytes.len() as u64;
        let index_size = rebased.len() as u64;
        let upload_ops = match staging_buffer {
            Some(staging) => vec![
                CommandOp::HostWriteBuffer {
                    buffer: staging,
                    offset: 0,
                    data: [vertex_bytes, rebased.as_slice()].concat(),
                },
                CommandOp::Barrier(buffer_barrier(
                    staging,
                    TextureUsageState::TransferDst,
                    TextureUsageState::TransferSrc,
                )),
                CommandOp::Barrier(buffer_barrier(
                    vertex.buffer,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::Barrier(buffer_barrier(
                    index.buffer,
                    TextureUsageState::Undefined,
                    TextureUsageState::TransferDst,
                )),
                CommandOp::CopyBufferRegion {
                    src: staging,
                    src_offset: 0,
                    dst: vertex.buffer,
                    dst_offset: vertex.offset,
                    size: vertex_size,
                },
                CommandOp::CopyBufferRegion {
                    src: staging,
                    src_offset: vertex_size,
                    dst: index.buffer,
                    dst_offset: index.offset,
                    size: index_size,
                },
                CommandOp::Barrier(buffer_barrier(
                    vertex.buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::Barrier(buffer_barrier(
                    index.buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::IndexRead,
                )),
            ],
            None => vec![
                CommandOp::HostWriteBuffer {
                    buffer: vertex.buffer,
                    offset: vertex.offset,
                    data: vertex_bytes.to_vec(),
                },
                CommandOp::Barrier(buffer_barrier(
                    vertex.buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::ShaderRead,
                )),
                CommandOp::HostWriteBuffer {
                    buffer: index.buffer,
                    offset: index.offset,
                    data: rebased,
                },
                CommandOp::Barrier(buffer_barrier(
                    index.buffer,
                    TextureUsageState::TransferDst,
                    TextureUsageState::IndexRead,
                )),
            ],
        };
        self.pending_lowered_source_terrain_geometry_uploads
            .insert(key.clone(), upload_ops);
        self.lowered_source_terrain_geometry_resources.insert(
            key.clone(),
            LoweredSourceTerrainGeometryResources {
                vertex_buffer: vertex.buffer,
                index_buffer: index.buffer,
                byte_size,
                staging_buffer,
                paged: Some((vertex, index)),
            },
        );
        Ok(key)
    }

    pub(crate) fn host_visible_source_geometry_upload_ops(
        resources: &LoweredSourceTerrainGeometryResources,
        vertex_bytes: &[u8],
        index_bytes: &[u8],
    ) -> Vec<CommandOp> {
        vec![
            CommandOp::HostWriteBuffer {
                buffer: resources.vertex_buffer,
                offset: 0,
                data: vertex_bytes.to_vec(),
            },
            CommandOp::Barrier(buffer_barrier(
                resources.vertex_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )),
            CommandOp::HostWriteBuffer {
                buffer: resources.index_buffer,
                offset: 0,
                data: index_bytes.to_vec(),
            },
            CommandOp::Barrier(buffer_barrier(
                resources.index_buffer,
                TextureUsageState::TransferDst,
                TextureUsageState::IndexRead,
            )),
        ]
    }

    pub(in crate::render::worldrender) fn source_terrain_frame_upload_ops(
        &self,
        stream: SourceTerrainFrameStreamAllocation,
        prepared: &PreparedSourceTerrainFrame,
    ) -> Vec<CommandOp> {
        self.source_terrain_frame_upload_ops_for_parts(
            stream,
            prepared.legacy_texture_transforms.clone(),
            prepared.scalar_uniforms.clone(),
            prepared.instance_transforms.clone(),
        )
    }

    /// Serializes one owned frame-local source payload into the shared
    /// completion-gated stream. Terrain uses the final lane for instances;
    /// `gbuffers_textured` uses it for compact vertices. The transaction and
    /// barriers are intentionally identical, while the set-zero layouts keep
    /// the two source ABIs distinct.
    pub(in crate::render::worldrender) fn source_terrain_frame_upload_ops_for_parts(
        &self,
        stream: SourceTerrainFrameStreamAllocation,
        legacy_texture_transforms: Vec<u8>,
        scalar_uniforms: Vec<u8>,
        payload: Vec<u8>,
    ) -> Vec<CommandOp> {
        let mut operations = vec![CommandOp::Barrier(buffer_barrier(
            stream.buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        ))];
        let mut write_dynamic = |offset: u64, data: Vec<u8>| {
            operations.push(CommandOp::HostWriteBuffer {
                buffer: stream.buffer,
                offset,
                data,
            });
        };
        write_dynamic(stream.legacy_transform_offset, legacy_texture_transforms);
        if let Some(offset) = stream.scalar_uniform_offset {
            write_dynamic(offset, scalar_uniforms);
        }
        write_dynamic(stream.instance_offset, payload);
        operations.push(CommandOp::Barrier(buffer_barrier(
            stream.buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        operations
    }

    /// Transfers a prepared source frame's uploads to its single combined
    /// submission. This remains private until selected-source execution is
    /// admitted, but its lifetime is now identical to the production path.
    pub(crate) fn take_source_terrain_frame_transaction(
        &mut self,
        frame_id: u64,
    ) -> GalResult<SourceTerrainFrameTransaction> {
        self.pending_source_terrain_frame_transactions
            .remove(&frame_id)
            .ok_or_else(|| {
                GalError::invalid_argument("source terrain frame has no pending upload transaction")
            })
    }

    /// Aborts private source-frame preparation before it reaches a GAL
    /// submission. Persistent geometry stays cached by generation, while the
    /// mutable frame data and its unsubmitted stream reservation are released
    /// so a malformed source frame cannot starve later valid frames.
    pub(crate) fn discard_source_terrain_frame_transaction(&mut self, gal: &mut VulkanicGal, frame_id: u64) {
        let mut source_material_texture_ids = self
            .pending_source_material_texture_uploads
            .remove(&frame_id)
            .unwrap_or_default()
            .into_iter()
            .map(|pending| pending.texture_id)
            .collect::<BTreeSet<_>>();
        if let Some(transaction) = self
            .pending_source_terrain_frame_transactions
            .remove(&frame_id)
        {
            source_material_texture_ids.extend(transaction.source_material_texture_ids);
            self.requeue_source_geometry_uploads(transaction.geometry_uploads);
        }
        self.discard_unsubmitted_source_material_textures(gal, &source_material_texture_ids);
        if let Some(slot) = self
            .source_terrain_frame_stream_slots
            .iter_mut()
            .find(|slot| slot.frame_id == Some(frame_id) && slot.submission.is_none())
        {
            slot.frame_id = None;
            slot.cursor = 0;
        }
    }

    pub(crate) fn discard_pending_lowered_source_terrain_submission(&mut self, gal: &mut VulkanicGal) {
        if let Some(submission) = self.pending_lowered_source_terrain_submission.take() {
            self.discard_source_terrain_frame_submission(gal, submission);
        }
    }

    /// Cancels all private source-frame work that has not reached the owning
    /// combined submission. Asset generations are atomic: no staged upload or
    /// resource binding may survive into a newer material generation.
    pub(crate) fn discard_all_unsubmitted_source_terrain_frames(&mut self, gal: &mut VulkanicGal) {
        self.discard_pending_lowered_source_terrain_submission(gal);
        let mut frame_ids = self
            .pending_source_terrain_frame_transactions
            .keys()
            .copied()
            .collect::<BTreeSet<_>>();
        frame_ids.extend(self.pending_source_material_texture_uploads.keys().copied());
        for frame_id in frame_ids {
            self.discard_source_terrain_frame_transaction(gal, frame_id);
        }
    }

    /// Returns whether the discovered source contract declares either
    /// temporal main-depth role and Rust has explicitly opted into preparing
    /// the source contract. Preparation is distinct from route selection:
    /// the ordinary Rust graph continues to execute until a later frame has
    /// confirmed every named source resource and armed the complete plan.
    pub(crate) fn source_main_depth_history_required(&self) -> bool {
        self.runtime_source_execution_requested()
            && self.shader_runtime.as_ref().is_some_and(|runtime| {
                runtime.candidate_source_requires_resource(
                    TerrainSourceResourceRole::MainDepthBeforeTranslucency,
                ) || runtime.candidate_source_requires_resource(
                    TerrainSourceResourceRole::MainDepthPrevious,
                )
            })
    }

    /// Prepares the depth-history copy contract for one exact graph/frame.
    /// This has no persistent effect until `confirm_g_buffer_depth_history`
    /// receives the submission token from `submit_whole_frame`.
    pub(in crate::render::worldrender) fn prepare_g_buffer_depth_history_submission(
        &self,
        frame_id: u64,
        resources: &GBufferResources,
    ) -> GalResult<Option<(TerrainDepthHistoryPlan, GBufferDepthHistorySubmission)>> {
        if !self.source_main_depth_history_required() {
            return Ok(None);
        }
        if self.pending_g_buffer_depth_history_submission.is_some() {
            return Err(GalError::backend(
                "main depth history submission is awaiting combined-frame confirmation",
            ));
        }
        let history = if self.g_buffer_depth_history.graph_generation == resources.generation {
            self.g_buffer_depth_history
        } else {
            GBufferDepthHistoryState::default()
        };
        Ok(Some((
            TerrainDepthHistoryPlan {
                prior_before_translucency_valid: history.before_translucency_valid,
                prior_previous_valid: history.previous_valid,
                extent: resources.extent,
            },
            GBufferDepthHistorySubmission {
                frame_id,
                graph_generation: resources.generation,
                prior_before_translucency_valid: history.before_translucency_valid,
            },
        )))
    }

    pub(crate) fn discard_pending_g_buffer_depth_history_submission(&mut self) {
        self.pending_g_buffer_depth_history_submission = None;
    }

    pub(in crate::render::worldrender) fn confirm_g_buffer_depth_history_submission(
        &mut self,
        submission: GBufferDepthHistorySubmission,
        gal_submission: SubmissionId,
        active_graph_generation: u64,
    ) -> GalResult<()> {
        if active_graph_generation != submission.graph_generation {
            return Err(GalError::backend(
                "main depth history graph generation changed before submission confirmation",
            ));
        }
        self.g_buffer_depth_history = GBufferDepthHistoryState {
            graph_generation: submission.graph_generation,
            before_translucency_valid: true,
            previous_valid: submission.prior_before_translucency_valid,
            last_frame_id: Some(submission.frame_id),
            last_submission: Some(gal_submission),
        };
        Ok(())
    }

    pub(crate) fn confirm_source_terrain_frame_transaction(
        &mut self,
        transaction: &SourceTerrainFrameSubmission,
        submission: SubmissionId,
    ) -> GalResult<()> {
        self.mark_source_terrain_frame_stream_submitted(
            transaction.frame_id,
            transaction.stream_buffer,
            transaction.stream_epoch,
            submission,
        )?;
        self.source_material_texture_resident.extend(
            transaction
                .uploaded_source_material_texture_ids
                .iter()
                .copied(),
        );
        self.source_material_texture_upload_confirmed.extend(
            transaction
                .uploaded_source_material_texture_ids
                .iter()
                .copied(),
        );
        for texture_id in &transaction.uploaded_source_material_texture_ids {
            self.source_material_texture_staged.remove(texture_id);
            self.source_material_texture_upload_operations
                .remove(texture_id);
        }
        for (key, _) in &transaction.geometry_uploads {
            if let Some(staging) = self
                .lowered_source_terrain_geometry_resources
                .get_mut(key)
                .and_then(|resources| resources.staging_buffer.take())
            {
                self.retired_source_geometry_staging.push(staging);
            }
        }
        Ok(())
    }

    pub(crate) fn discard_source_terrain_frame_submission(
        &mut self,
        gal: &mut VulkanicGal,
        submission: SourceTerrainFrameSubmission,
    ) {
        self.discard_unsubmitted_source_material_textures(
            gal,
            &submission.source_material_texture_ids,
        );
        self.requeue_source_geometry_uploads(submission.geometry_uploads);
        self.discard_source_terrain_frame_transaction(gal, submission.frame_id);
    }

    /// Returns first-use geometry uploads of a discarded source frame to the
    /// pending queue while their cached buffers still exist, so the next
    /// accepted source frame writes them before any draw reads them.
    pub(crate) fn requeue_source_geometry_uploads(
        &mut self,
        uploads: Vec<(LoweredSourceTerrainDataKey, Vec<CommandOp>)>,
    ) {
        for (key, operations) in uploads {
            if self.lowered_source_terrain_geometry_resources.contains_key(&key) {
                self.pending_lowered_source_terrain_geometry_uploads
                    .entry(key)
                    .or_insert(operations);
            }
        }
    }

    pub(crate) fn ensure_source_mesh_resources(
        &mut self,
        gal: &mut VulkanicGal,
        mesh_key: MeshResourceKey,
        candidate: &TerrainSourceProgramCandidate,
    ) -> GalResult<SourceMeshResourceKey> {
        if mesh_key.standard_item_foil {
            return Err(GalError::unsupported_feature(
                "standard item foil is not a source-selected mesh contract",
            ));
        }
        let binding = candidate.binding;
        let source_key = source_mesh_resource_key(mesh_key, candidate);
        if self.source_mesh_resources.contains_key(&source_key) {
            return Ok(source_key);
        }
        if self.source_mesh_resources.len() >= WORLD_SOURCE_MESH_RESOURCE_RESIDENCY {
            return Err(GalError::unsupported_feature(format!(
                "world source mesh resource residency exceeds bounded limit {WORLD_SOURCE_MESH_RESOURCE_RESIDENCY}"
            )));
        }
        // Source-derived programs retain the complete 80-byte semantic ABI
        // even when the shader-off builtin route has a compact GPU lowering.
        let rich_geometry_key = mesh_key.geometry_key_for_abi(MeshVertexAbi::Rich80);
        let (rich_vertices, index_bytes, index_type) = self
            .mesh_assets
            .get(&mesh_key.mesh_key)
            .map(|asset| {
                (
                    asset.vertex_bytes.clone(),
                    asset.index_bytes.clone(),
                    asset.index_type,
                )
            })
            .ok_or_else(|| GalError::backend("source mesh asset vanished before lowering"))?;
        self.ensure_mesh_geometry_resources(
            gal,
            rich_geometry_key,
            rich_vertices,
            index_bytes,
            mesh_key.view_layering.is_some(),
        )?;
        let (vertex_buffer, vertex_offset, vertex_range, index_buffer, index_offset) = self
            .mesh_geometry_resources
            .get(&rich_geometry_key)
            .map(|geometry| {
                (
                    geometry.vertex_buffer,
                    geometry.vertex_offset,
                    geometry.vertex_range,
                    geometry.index_buffer,
                    geometry.index_offset,
                )
            })
            .ok_or_else(|| GalError::backend("rich source mesh geometry vanished"))?;
        let texture_id = mesh_key.texture_id;
        self.ensure_mesh_texture_resources(gal, texture_id, "world-mesh-source")?;
        let (texture_view, sampler) = self
            .mesh_texture_resources
            .get(&texture_id)
            .map(|resources| (resources.view, resources.sampler))
            .ok_or_else(|| GalError::backend("source mesh texture resources vanished"))?;
        let pipeline_key = MeshPipelineResourceKey {
            vertex_abi: MeshVertexAbi::Rich80,
            raster_y_direction: mesh_key.raster_y_direction,
            g_buffer: mesh_key.g_buffer,
            material_mode: mesh_key.material_mode,
            winding: mesh_key.winding,
            depth_policy: mesh_key.depth_policy,
            cull_policy: mesh_key.cull_policy,
            color_format: mesh_key.color_format,
            shader_program_identity: candidate.program.identity.clone(),
            shader_resource_layout: binding.map(|binding| binding.resource_layout),
        };
        self.ensure_mesh_pipeline_resources_for_program(
            gal,
            pipeline_key.clone(),
            &candidate.program,
        )?;
        let (resource_layout, pipeline_layout, pipeline, shadow_pipeline) = self
            .mesh_pipeline_resources
            .get(&pipeline_key)
            .map(|resources| {
                (
                    resources.resource_layout,
                    resources.pipeline_layout,
                    resources.pipeline,
                    resources.shadow_pipeline,
                )
            })
            .ok_or_else(|| GalError::backend("source mesh pipeline resources vanished"))?;
        let stream_binding = self.ensure_mesh_instance_stream(gal, 1)?;
        let resource_set = create_mesh_resource_set(
            gal,
            &format!(
                "world-mesh-source-{}-mesh{}-section{}",
                candidate.program.identity.as_str(),
                mesh_key.mesh_key,
                mesh_key.section_index
            ),
            resource_layout,
            vertex_buffer,
            vertex_range,
            stream_binding.buffer,
            WORLD_MESH_INSTANCE_STREAM_BINDING_RANGE_BYTES,
            texture_view,
            sampler,
            false,
            None,
        )?;
        self.source_mesh_resources.insert(
            source_key.clone(),
            SourceMeshResources {
                geometry_key: rich_geometry_key,
                vertex_offset,
                vertex_stride: WORLD_MESH_GPU_VERTEX_BYTES,
                index_buffer,
                index_offset,
                index_type,
                pipeline_layout,
                pipeline,
                shadow_pipeline,
                resource_set,
            },
        );
        Ok(source_key)
    }

    pub(crate) fn destroy_source_mesh_resources(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.source_mesh_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
    }
}

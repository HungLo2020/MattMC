//! Terrain and DH voxel-source meshes, the DH shadow pass decision and voxel bounds.

use super::*;

/// Gives DH occupancy inputs their own stable semantic namespace while the
/// shared voxel cache remains keyed by one opaque mesh identifier. The input
/// is copied column/segment identity only; it never derives from a native
/// buffer or renderer handle. A collision with any visible source mesh stays
/// a validation error at the snapshot boundary.
pub(crate) fn distant_horizons_voxel_mesh_key(column_key: u64, segment_index: u32) -> u64 {
    let mut bytes = [0u8; 12];
    bytes[..8].copy_from_slice(&column_key.to_le_bytes());
    bytes[8..].copy_from_slice(&segment_index.to_le_bytes());
    let high = u64::from(xxh32(&bytes, 0x44_48_56_58));
    let low = u64::from(xxh32(&bytes, 0x4f_43_43_50));
    let key = (high << 32) | low;
    if key == 0 {
        1
    } else {
        key
    }
}

/// Identity of the immutable terrain input used to prepare the private
/// shader-pack occupancy field. It intentionally contains only semantic
/// world data: resource handles, backend objects, and transient camera
/// fractions do not participate. A complete field is expensive to build, so
/// source preparation waits until this input is unchanged across a bounded
/// pair of frames instead of restarting while terrain streaming is active.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(crate) struct CandidateSourceOccupancyInputIdentity {
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) resource_generation: u64,
    pub(in crate::render::worldrender) camera_cell: [i32; 3],
    pub(in crate::render::worldrender) terrain_meshes: Vec<(u64, u64)>,
    pub(in crate::render::worldrender) lod_segments: Vec<(u64, u64, u32, u32)>,
    pub(in crate::render::worldrender) lod_world_y_offset: i32,
}

pub(crate) const CANDIDATE_SOURCE_OCCUPANCY_STABLE_FRAME_COUNT: u8 = 2;

/// Immutable, source-material-resolved DH geometry reused by the private
/// terrain occupancy preparation. The copied column asset and source pack
/// generation are both part of the key; a frame may clone its `Arc` payloads
/// but must never rebuild equivalent vertex/index vectors every frame.
#[derive(Clone, Debug)]
pub(crate) struct DistantHorizonsVoxelSourceCacheEntry {
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) world_y_offset: i32,
    pub(in crate::render::worldrender) mesh: TerrainVoxelSourceMesh,
}

pub(crate) struct LocalMaterialMemoGroup {
    pub(in crate::render::worldrender) program: ProgramIdentity,
    pub(in crate::render::worldrender) shader_pack_generation: u64,
    pub(in crate::render::worldrender) base: TerrainSourceOwnedResourceSet,
    pub(in crate::render::worldrender) entries: HashMap<(u32, u64), (TerrainSourceOwnedResourceSet, (u32, u64), Handle)>,
}

#[derive(Clone, Copy, PartialEq, Eq)]
pub(crate) struct SourceUniformFrameMemoKey {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) world_generation: u64,
    pub(in crate::render::worldrender) frame_time_bits: u32,
    pub(in crate::render::worldrender) view_bits: [u32; 16],
}

pub(crate) struct TerrainVoxelSourceMemo {
    pub(in crate::render::worldrender) runtime_generation: Option<u64>,
    pub(in crate::render::worldrender) cull: Option<[[i32; 3]; 2]>,
    pub(in crate::render::worldrender) instances: Vec<(u64, u64, [u32; 16])>,
    pub(in crate::render::worldrender) meshes: Vec<TerrainVoxelSourceMesh>,
}

impl WorldPrimitiveFrontend {
    /// Returns the currently visible static-terrain source meshes with the
    /// exact model transforms used by the frame. This is intentionally a
    /// semantic cache query: it does not allocate GPU resources, record
    /// commands, or select a shader path.
    pub(crate) fn terrain_voxel_source_meshes(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<TerrainVoxelSourceMesh>> {
        self.terrain_voxel_source_meshes_within(frame, None)
    }

    /// As `terrain_voxel_source_meshes`, but static terrain meshes whose block
    /// centres all lie outside the half-open world `cull` box are omitted:
    /// such meshes can only produce out-of-volume samples.
    pub(crate) fn terrain_voxel_source_meshes_within(
        &mut self,
        frame: &WorldPrimitiveFrame,
        cull: Option<[[i32; 3]; 2]>,
    ) -> GalResult<Vec<TerrainVoxelSourceMesh>> {
        let camera_world_position = frame.voxel_volume.camera_world_position;
        if camera_world_position.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(
                "terrain voxel source frame has a non-finite camera world position",
            ));
        }
        // The list depends only on the terrain instances' identities and
        // (canonical) world transforms plus the cull box; reuse it while those
        // are unchanged. DH meshes carry their own per-frame provenance.
        // DH meshes join the list only through a pack `dh_shadow` program;
        // without one (e.g. Complementary) the list is terrain-only.
        let distant_horizons_voxelized = frame.lod_render_frame.rust_route_selected()
            && !frame.lod_instances.is_empty()
            && match terrain_program_scope_for_sky_type(frame.background.sky_type)? {
                Some(scope) => self.source_distant_horizons_shadow_pass_enabled(scope)?,
                None => false,
            };
        let memoizable = !distant_horizons_voxelized;
        let instances = if memoizable {
            let mut instances = Vec::with_capacity(frame.mesh_instances.len());
            for instance in frame
                .mesh_instances
                .iter()
                .filter(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
            {
                let world = Self::world_transform_from_camera_relative(
                    instance.transform,
                    camera_world_position,
                )?;
                instances.push((
                    instance.mesh_key,
                    instance.mesh_generation,
                    world.map(f32::to_bits),
                ));
            }
            let runtime_generation = self.shader_runtime.as_ref().map(|runtime| runtime.generation());
            if let Some(memo) = self.terrain_voxel_source_memo.as_ref() {
                if memo.runtime_generation == runtime_generation
                    && memo.cull == cull
                    && memo.instances == instances
                {
                    return Ok(memo.meshes.clone());
                }
            }
            Some(instances)
        } else {
            None
        };
        let meshes = self.build_terrain_voxel_source_meshes(frame, cull)?;
        let runtime_generation = self.shader_runtime.as_ref().map(|runtime| runtime.generation());
        self.terrain_voxel_source_memo = instances.map(|instances| TerrainVoxelSourceMemo {
            runtime_generation,
            cull,
            instances,
            meshes: meshes.clone(),
        });
        Ok(meshes)
    }

    pub(crate) fn build_terrain_voxel_source_meshes(
        &mut self,
        frame: &WorldPrimitiveFrame,
        cull: Option<[[i32; 3]; 2]>,
    ) -> GalResult<Vec<TerrainVoxelSourceMesh>> {
        let camera_world_position = frame.voxel_volume.camera_world_position;
        let mut seen_meshes = BTreeSet::new();
        let mut result = Vec::new();
        for instance in frame
            .mesh_instances
            .iter()
            .filter(|instance| instance.stratum == WORLD_STRATUM_TERRAIN)
        {
            validate_mesh_instance(instance, frame)?;
            if !seen_meshes.insert(instance.mesh_key) {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source frame has duplicate visible mesh {}",
                    instance.mesh_key
                )));
            }
            let source_material_ids = self
                .shader_runtime
                .as_ref()
                .and_then(ShaderPackRuntimeExecutor::candidate_runtime_block_state_material_ids);
            let asset = self
                .mesh_assets
                .get_mut(&instance.mesh_key)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "terrain voxel source mesh {} is missing from the asset cache",
                        instance.mesh_key
                    ))
                })?;
            if asset.mesh_generation != instance.mesh_generation {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source mesh {} generation {} does not match cached generation {}",
                    instance.mesh_key, instance.mesh_generation, asset.mesh_generation
                )));
            }
            if asset.vertex_layout_version != WORLD_MESH_VERTEX_LAYOUT_V3 {
                return Err(GalError::unsupported_feature(format!(
                    "terrain voxel source mesh {} requires V3 semantic vertices; cached layout is {}",
                    instance.mesh_key, asset.vertex_layout_version
                )));
            }
            // Meshes uploaded while shaders were off keep no semantic source
            // input. Enabling shaders mid-session rebuilds them; until their
            // new generation arrives they are simply not voxelized, and source
            // admission rejects the frame (see coverage validation).
            if !matches!(asset.source_input, Some(SourceMeshSemanticInput::Terrain(_))) {
                continue;
            }
            let transform = Self::world_transform_from_camera_relative(
                instance.transform,
                camera_world_position,
            )?;
            if let Some(cull) = cull {
                let bounds = *asset.terrain_voxel_model_bounds.get_or_insert_with(|| {
                    let SourceMeshSemanticInput::Terrain(input) = asset
                        .source_input
                        .as_ref()
                        .expect("v3 terrain asset keeps immutable semantic source input")
                    else {
                        panic!("v3 terrain asset must retain terrain source semantics");
                    };
                    terrain_voxel_model_bounds(input)
                });
                let Some(bounds) = bounds else {
                    continue;
                };
                if !terrain_voxel_bounds_may_touch(bounds, transform, cull) {
                    continue;
                }
            }
            let vertices = Arc::clone(asset.terrain_voxel_vertices.get_or_insert_with(|| {
                let SourceMeshSemanticInput::Terrain(input) = asset
                    .source_input
                    .as_ref()
                    .expect("v3 terrain asset keeps immutable semantic source input")
                else {
                    panic!("v3 terrain asset must retain terrain source semantics");
                };
                Arc::new(
                    input
                        .iter()
                        .map(|vertex| TerrainVoxelSourceVertex {
                            position: vertex.position,
                            mid_block_packed: vertex.mid_block_packed,
                            shader_material_id: source_material_ids
                                .as_ref()
                                .and_then(|ids| ids.get(&vertex.shader_block_id))
                                .copied()
                                .unwrap_or(vertex.shader_block_id),
                        })
                        .collect(),
                )
            }));
            let indices = Arc::clone(asset.terrain_voxel_indices.get_or_insert_with(|| {
                Arc::new(
                    decoded_terrain_voxel_indices(&asset.index_bytes, asset.index_type)
                        .expect("validated world mesh indices remain decodable"),
                )
            }));
            // Derived once per immutable asset, like its vertices/indices.
            let translucent_indices = match &asset.terrain_voxel_translucent_indices {
                Some(translucent) => Arc::clone(translucent),
                None => {
                    let translucent = Arc::new(Self::terrain_voxel_translucent_indices(
                        instance.mesh_key,
                        asset,
                        &indices,
                    )?);
                    asset.terrain_voxel_translucent_indices = Some(Arc::clone(&translucent));
                    translucent
                }
            };
            result.push(TerrainVoxelSourceMesh {
                mesh_key: instance.mesh_key,
                mesh_generation: instance.mesh_generation,
                vertices,
                indices,
                translucent_indices,
                // Static terrain instances render in a camera-relative
                // coordinate space. The voxel field is world-addressed, so
                // reconstruct only that semantic origin here; the rendering
                // transform remains untouched for the normal Rust pass.
                transform,
            });
        }
        for mesh in self.distant_horizons_voxel_source_meshes(frame)? {
            if !seen_meshes.insert(mesh.mesh_key) {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source frame has duplicate static/Distant Horizons mesh key {}",
                    mesh.mesh_key
                )));
            }
            result.push(mesh);
        }
        Ok(result)
    }

    /// Whether Iris would render DH geometry into this scope's shadow pass:
    /// the pack has a `dh_shadow` program and does not set
    /// `dhShadow.enabled=false` (IrisRenderingPipeline: `orElse(true)`).
    pub(crate) fn source_distant_horizons_shadow_pass_enabled(
        &self,
        scope: TerrainProgramScope,
    ) -> GalResult<bool> {
        let Some(source) = self.shader_pack_sources.active() else {
            return Ok(false);
        };
        let memo_key = (source.generation(), scope);
        if let Some((key, enabled)) = self.distant_horizons_shadow_pass_memo.get() {
            if key == memo_key {
                return Ok(enabled);
            }
        }
        let enabled = Self::resolve_distant_horizons_shadow_pass(source, scope)?;
        self.distant_horizons_shadow_pass_memo.set(Some((memo_key, enabled)));
        Ok(enabled)
    }

    pub(crate) fn resolve_distant_horizons_shadow_pass(
        source: &crate::render::shaderpack::source::ShaderPackSource,
        scope: TerrainProgramScope,
    ) -> GalResult<bool> {
        let candidates: &[&str] = match scope {
            TerrainProgramScope::Default => &["dh_shadow.vsh"],
            TerrainProgramScope::Overworld => &["world0/dh_shadow.vsh", "dh_shadow.vsh"],
            TerrainProgramScope::Nether => &["world-1/dh_shadow.vsh", "dh_shadow.vsh"],
            TerrainProgramScope::End => &["world1/dh_shadow.vsh", "dh_shadow.vsh"],
        };
        if !candidates.iter().any(|path| source.get(path).is_some()) {
            return Ok(false);
        }
        let Some((properties, _)) =
            crate::render::shaderpack::contracts::fullscreen::resolved_source_properties(source, scope)?
        else {
            return Ok(true);
        };
        let mut enabled = true;
        for line in properties.lines() {
            if let Some((key, value)) = line.trim().split_once('=') {
                if key.trim() == "dhShadow.enabled" {
                    enabled = value.trim() != "false";
                }
            }
        }
        Ok(enabled)
    }

    /// Converts only generation-bound, per-quad DH material provenance into
    /// the compact semantic geometry consumed by the shared occupancy path.
    /// DH's coarse category is intentionally never used as a substitute for a
    /// block state or atlas identity: unresolved and mixed quads remain out
    /// of this source input rather than acquiring a guessed material.
    pub(crate) fn distant_horizons_voxel_source_meshes(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<TerrainVoxelSourceMesh>> {
        if !frame.lod_render_frame.rust_route_selected() || frame.lod_instances.is_empty() {
            return Ok(Vec::new());
        }
        // Packs voxelize from their shadow pass. Iris draws DH geometry there
        // only through the pack's own `dh_shadow` program with
        // `dhShadow.enabled` (default true); otherwise DH never reaches the
        // voxel images (Complementary disables it under DISTANT_HORIZONS).
        let Some(scope) = terrain_program_scope_for_sky_type(frame.background.sky_type)? else {
            return Ok(Vec::new());
        };
        if !self.source_distant_horizons_shadow_pass_enabled(scope)? {
            return Ok(Vec::new());
        }
        let Some(runtime) = self.shader_runtime.as_ref() else {
            return Ok(Vec::new());
        };
        if !runtime.has_candidate_material_contract() {
            return Ok(Vec::new());
        }
        let shader_pack_generation = runtime.expected_shader_pack_generation_for_resources();

        let mut seen_columns = BTreeSet::new();
        let mut result = Vec::new();
        for instance in &frame.lod_instances {
            let column_key = instance.column_key;
            let asset = self.lod_column_assets.get(&column_key).ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "terrain voxel source references missing Distant Horizons column {column_key}"
                ))
            })?;
            if asset.column_generation != instance.column_generation {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source Distant Horizons column {column_key} generation {} differs from visible generation {}",
                    asset.column_generation, instance.column_generation
                )));
            }
            let provenance = self.lod_material_provenance.get(&column_key).ok_or_else(|| {
                GalError::unsupported_feature(format!(
                    "terrain voxel source Distant Horizons column {column_key} has no generation-bound material provenance"
                ))
            })?;
            let plan = self.lod_textured_column_plans.get(&column_key).ok_or_else(|| {
                GalError::unsupported_feature(format!(
                    "terrain voxel source Distant Horizons column {column_key} has no exact material plan"
                ))
            })?;
            if provenance.column_generation != instance.column_generation
                || plan.column_generation != instance.column_generation
            {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source Distant Horizons column {column_key} mixes asset, provenance, or exact-plan generations"
                )));
            }
            let segment = plan
                .segments
                .get(instance.segment_index as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "terrain voxel source Distant Horizons column {column_key} is missing visible segment {}",
                        instance.segment_index
                    ))
                })?;
            if segment.layer != instance.layer {
                return Err(GalError::invalid_argument(
                    "terrain voxel source Distant Horizons visible layer differs from its exact material plan",
                ));
            }

            let cache_key = (column_key, instance.segment_index);
            if let Some(entry) = self.lod_voxel_source_meshes.get(&cache_key) {
                if entry.shader_pack_generation == shader_pack_generation
                    && entry.mesh.mesh_generation == instance.column_generation
                    && entry.world_y_offset == frame.lod_render_frame.world_y_offset
                {
                    result.push(entry.mesh.clone());
                    continue;
                }
            }

            let mut vertices = Vec::with_capacity(segment.quads.len().saturating_mul(4));
            let mut indices = Vec::with_capacity(segment.quads.len().saturating_mul(6));
            for quad in &segment.quads {
                let identity = provenance
                    .identities
                    .get(quad.material_id.saturating_sub(1) as usize)
                    .ok_or_else(|| {
                        GalError::invalid_argument(format!(
                            "terrain voxel source Distant Horizons column {column_key} quad {} references material {} outside its provenance table",
                            quad.quad_index, quad.material_id
                        ))
                    })?;
                let shader_material_id = self
                    .shader_runtime
                    .as_ref()
                    .expect("source candidate remains present while building its semantic occupancy")
                    .candidate_material_id_for_block_state_identity(&identity.block_state_identity)
                    .ok_or_else(|| {
                        GalError::unsupported_feature(format!(
                            "terrain voxel source Distant Horizons column {column_key} has no source material mapping for '{}'",
                            identity.block_state_identity
                        ))
                    })??;
                let base = u32::try_from(vertices.len()).map_err(|_| {
                    GalError::invalid_argument(
                        "terrain voxel source Distant Horizons vertex count exceeds u32",
                    )
                })?;
                for vertex in quad.vertices {
                    vertices.push(TerrainVoxelSourceVertex {
                        position: [
                            asset.origin[0] as f32
                                + vertex.local_position[0]
                                + vertex.micro_offset[0],
                            asset.origin[1] as f32
                                + vertex.local_position[1]
                                + vertex.micro_offset[1],
                            asset.origin[2] as f32
                                + vertex.local_position[2]
                                + vertex.micro_offset[2],
                        ],
                        // DH geometry is already a reduced world-space
                        // primitive. It has no Minecraft `at_midBlock` lane;
                        // a zero midpoint explicitly preserves its copied
                        // geometry rather than inventing a block-local one.
                        mid_block_packed: 0,
                        shader_material_id,
                    });
                }
                indices.extend_from_slice(&[base, base + 1, base + 2, base + 2, base + 3, base]);
            }
            if vertices.is_empty() {
                continue;
            }
            let mesh_key = distant_horizons_voxel_mesh_key(column_key, instance.segment_index);
            if !seen_columns.insert(mesh_key) {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source Distant Horizons frame has duplicate visible column/segment key {mesh_key}"
                )));
            }
            let mesh = TerrainVoxelSourceMesh {
                mesh_key,
                mesh_generation: instance.column_generation,
                vertices: Arc::new(vertices),
                indices: Arc::new(indices),
                translucent_indices: Arc::new(Vec::new()),
                transform: IDENTITY_WORLD_TRANSFORM,
            };
            if !self.lod_voxel_source_meshes.contains_key(&cache_key)
                && self.lod_voxel_source_meshes.len() >= WORLD_LOD_MAX_SOURCE_MESH_CACHE
            {
                self.lod_voxel_source_meshes.clear();
            }
            self.lod_voxel_source_meshes.insert(
                cache_key,
                DistantHorizonsVoxelSourceCacheEntry {
                    shader_pack_generation,
                    world_y_offset: frame.lod_render_frame.world_y_offset,
                    mesh: mesh.clone(),
                },
            );
            result.push(mesh);
        }
        Ok(result)
    }

    pub(in crate::render::worldrender) fn terrain_voxel_translucent_indices(
        mesh_key: u64,
        asset: &MeshAssetStore,
        indices: &[u32],
    ) -> GalResult<Vec<u32>> {
        let stride = index_stride(asset.index_type);
        let mut translucent = Vec::new();
        for (section_index, section) in asset.sections.iter().enumerate() {
            if section.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT {
                continue;
            }
            let first = u64::from(section.index_offset)
            .checked_div(stride)
            .filter(|_| u64::from(section.index_offset) % stride == 0)
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "terrain voxel source mesh {} translucent section {} has unaligned index offset {}",
                    mesh_key, section_index, section.index_offset
                ))
            })?;
            let end = first
                .checked_add(u64::from(section.index_count))
                .ok_or_else(|| {
                    GalError::invalid_argument(
                        "terrain voxel translucent section index range overflows",
                    )
                })?;
            let range = usize::try_from(first)
            .ok()
            .zip(usize::try_from(end).ok())
            .filter(|(start, end)| start <= end && *end <= indices.len())
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "terrain voxel source mesh {} translucent section {} range {}..{} exceeds {} decoded indices",
                    mesh_key, section_index, first, end, indices.len()
                ))
            })?;
            translucent.extend_from_slice(&indices[range.0..range.1]);
        }
        Ok(translucent)
    }

    pub(crate) fn world_transform_from_camera_relative(
        camera_relative: [f32; 16],
        camera_world_position: [f32; 3],
    ) -> GalResult<[f32; 16]> {
        if camera_relative.iter().any(|value| !value.is_finite())
            || camera_world_position.iter().any(|value| !value.is_finite())
        {
            return Err(GalError::invalid_argument(
                "camera-relative terrain transform must be finite",
            ));
        }
        // Column-major `translation(camera) * camera_relative`. Static chunk
        // section origins are integral world coordinates, whereas their
        // camera-relative float subtraction may vary by a few ULPs from frame
        // to frame. Canonicalize only near-integral translations for the
        // private occupancy identity; rendering retains the original matrix.
        let mut world = camera_relative;
        for column in 0..4 {
            let row_w = camera_relative[column * 4 + 3];
            world[column * 4] += camera_world_position[0] * row_w;
            world[column * 4 + 1] += camera_world_position[1] * row_w;
            world[column * 4 + 2] += camera_world_position[2] * row_w;
        }
        for component in [12, 13, 14] {
            let nearest = world[component].round();
            if (world[component] - nearest).abs() <= 0.001 {
                world[component] = nearest;
            }
        }
        Ok(world)
    }
}

/// Model-space bounds of the voxel block centres (`position + mid-block`,
/// matching `TerrainVoxelSample::world_block_center`) of a terrain mesh.
pub(crate) fn terrain_voxel_model_bounds(input: &[WorldMeshVertex]) -> Option<[[f32; 3]; 2]> {
    let mut bounds: Option<[[f32; 3]; 2]> = None;
    for vertex in input {
        let offset = [0, 8, 16].map(|shift| {
            ((vertex.mid_block_packed >> shift) as u8 as i8) as f32 / 64.0
        });
        let point = [0, 1, 2].map(|axis| vertex.position[axis] + offset[axis]);
        if point.iter().any(|value| !value.is_finite()) {
            continue;
        }
        bounds = Some(match bounds {
            None => [point, point],
            Some([min, max]) => [
                [0, 1, 2].map(|axis| min[axis].min(point[axis])),
                [0, 1, 2].map(|axis| max[axis].max(point[axis])),
            ],
        });
    }
    bounds
}

/// Conservative test whether any block centre inside the model `bounds`,
/// transformed by the column-major affine `transform`, floors into `cull`.
pub(crate) fn terrain_voxel_bounds_may_touch(
    bounds: [[f32; 3]; 2],
    transform: [f32; 16],
    cull: [[i32; 3]; 2],
) -> bool {
    let mut world_min = [f32::INFINITY; 3];
    let mut world_max = [f32::NEG_INFINITY; 3];
    for corner in 0..8 {
        let point = [0, 1, 2].map(|axis| bounds[(corner >> axis) & 1][axis]);
        for row in 0..3 {
            let value = transform[row]
                * point[0]
                + transform[4 + row] * point[1]
                + transform[8 + row] * point[2]
                + transform[12 + row];
            world_min[row] = world_min[row].min(value);
            world_max[row] = world_max[row].max(value);
        }
    }
    // One block of slack absorbs float rounding at cell boundaries.
    (0..3).all(|axis| {
        world_max[axis].floor() + 1.0 >= cull[0][axis] as f32
            && world_min[axis].floor() - 1.0 < cull[1][axis] as f32
    })
}

pub(crate) fn decoded_terrain_voxel_indices(index_bytes: &[u8], index_type: IndexType) -> GalResult<Vec<u32>> {
    let index_size = match index_type {
        IndexType::U16 => 2,
        IndexType::U32 => 4,
    };
    if index_bytes.len() % index_size != 0 {
        return Err(GalError::invalid_argument(
            "terrain voxel source index bytes are not aligned to their index type",
        ));
    }
    let count = index_bytes.len() / index_size;
    if count == 0 || count % 3 != 0 {
        return Err(GalError::invalid_argument(
            "terrain voxel source requires a non-empty triangle index stream",
        ));
    }
    (0..count)
        .map(|index| mesh_index_value(index_bytes, index_type, index))
        .collect()
}

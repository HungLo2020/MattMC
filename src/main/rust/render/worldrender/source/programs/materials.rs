//! Lowered textured-material, weather, cloud, glint, line and damaged-block programs, and local material textures.

use super::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn line_source_program(
        &mut self,
        shader_pack_generation: u64,
        scope: TerrainProgramScope,
    ) -> GalResult<LoweredTexturedMaterialSourceProgram> {
        if let Some((key, program)) = self.line_source_program_cache.as_ref() {
            if *key == (shader_pack_generation, scope) {
                return Ok(program.clone());
            }
        }
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument("line source program requires an active shader pack")
        })?;
        if source.generation() != shader_pack_generation {
            return Err(GalError::invalid_argument(
                "line source program generation does not match the active shader pack",
            ));
        }
        let program = crate::render::shaderpack::contracts::line::prepare_line_source_program(source, scope)?;
        self.line_source_program_cache = Some(((shader_pack_generation, scope), program.clone()));
        Ok(program)
    }

    /// Block-entity crumbling (vanilla's `crumblingBufferSource`) arrives as
    /// entity mesh instances whose every section carries the crumbling
    /// material. Iris draws those with `gbuffers_damagedblock`, never with
    /// the entity program, so the source route routes them to that writer.
    pub(crate) fn is_crumbling_mesh_instance(&self, instance: &WorldMeshInstanceRequest) -> bool {
        instance.stratum == WORLD_STRATUM_ENTITY_MESH
            && self.mesh_assets.get(&instance.mesh_key).is_some_and(|asset| {
                asset.mesh_generation == instance.mesh_generation
                    && !asset.sections.is_empty()
                    && asset
                        .sections
                        .iter()
                        .all(|section| section.material_id == WORLD_MATERIAL_ID_MODEL_CRUMBLING)
            })
    }

    /// Copies each crumbling instance's triangles into camera-relative
    /// material quads (a triangle is the quad `a, b, c, c`). Positions take
    /// the instance pose; the decal UVs were projected by the Java producer
    /// exactly like vanilla's `SheetedDecalTextureGenerator`. Iris draws this
    /// program FULLBRIGHT with a white vertex color.
    pub(crate) fn source_damaged_block_batches(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<SourceDamagedBlockBatch>> {
        let mut batches: Vec<SourceDamagedBlockBatch> = Vec::new();
        for instance in &frame.mesh_instances {
            if !self.is_crumbling_mesh_instance(instance) {
                continue;
            }
            let transform =
                crate::render::shared::view_layering::apply_to_model(instance.transform, mesh_view_layering(instance))?;
            let mesh = self.source_entity_mesh_asset(
                instance.mesh_key,
                instance.mesh_generation,
                instance.packed_light,
            )?;
            let vertex_count = mesh.vertex_bytes.len() / TERRAIN_SOURCE_VERTEX_BYTES;
            let read_f32 = |offset: usize| -> f32 {
                f32::from_le_bytes(
                    mesh.vertex_bytes[offset..offset + 4]
                        .try_into()
                        .expect("four-byte vertex lane"),
                )
            };
            let section_indices: Vec<usize> = if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                (0..mesh.sections.len()).collect()
            } else {
                vec![instance.mesh_section_index as usize]
            };
            for section_index in section_indices {
                let section = mesh.sections.get(section_index).ok_or_else(|| {
                    GalError::invalid_argument("crumbling instance selects a missing mesh section")
                })?;
                let cull_policy = if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                    section.cull_policy
                } else {
                    instance.cull_policy
                };
                let start = usize::try_from(section.index_offset / 4).map_err(|_| {
                    GalError::invalid_argument("crumbling section index offset exceeds usize")
                })?;
                let count = section.index_count as usize;
                if count % 3 != 0 || (start + count) * 4 > mesh.index_bytes.len() {
                    return Err(GalError::invalid_argument(
                        "crumbling section index range is not a whole triangle list",
                    ));
                }
                let mut primitives = Vec::with_capacity(count / 3);
                for triangle in 0..count / 3 {
                    let mut positions = [[0.0f32; 3]; 4];
                    let mut uvs = [[0.0f32; 2]; 4];
                    for corner in 0..3 {
                        let at = (start + triangle * 3 + corner) * 4;
                        let vertex = u32::from_le_bytes(
                            mesh.index_bytes[at..at + 4].try_into().expect("u32 index"),
                        ) as usize;
                        if vertex >= vertex_count {
                            return Err(GalError::invalid_argument(
                                "crumbling mesh index references a missing vertex",
                            ));
                        }
                        let base = vertex * TERRAIN_SOURCE_VERTEX_BYTES;
                        let local = [read_f32(base), read_f32(base + 4), read_f32(base + 8), 1.0];
                        let placed = transform_column_major_vec4(transform, local);
                        positions[corner] = [placed[0], placed[1], placed[2]];
                        // `atlas_uv_lightmap.xy` lane of the fixed record.
                        uvs[corner] = [read_f32(base + 48), read_f32(base + 52)];
                    }
                    positions[3] = positions[2];
                    uvs[3] = uvs[2];
                    primitives.push(crate::render::shaderpack::contracts::material::stage_textured_material_primitive(
                        positions,
                        uvs,
                        0xFFFF_FFFF,
                        0x00F0_00F0,
                        TexturedMaterialTextureCoordinates::LocalTexture,
                        TexturedMaterialWinding::CounterClockwise,
                    )?);
                }
                match batches.last_mut() {
                    Some(batch)
                        if batch.texture_id == section.texture_id
                            && batch.cull_policy == cull_policy =>
                    {
                        batch.primitives.extend(primitives);
                    }
                    _ => batches.push(SourceDamagedBlockBatch {
                        texture_id: section.texture_id,
                        cull_policy,
                        primitives,
                    }),
                }
            }
        }
        Ok(batches)
    }

    pub(crate) fn entity_glint_source_program(
        &mut self,
        shader_pack_generation: u64,
        scope: TerrainProgramScope,
    ) -> GalResult<LoweredEntitySourceProgram> {
        if let Some((key, program)) = self.entity_glint_source_program_cache.as_ref() {
            if *key == (shader_pack_generation, scope) {
                return Ok(program.clone());
            }
        }
        let source = self
            .shader_pack_sources
            .active()
            .filter(|source| source.generation() == shader_pack_generation)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "glint source program requires the active shader-pack generation",
                )
            })?;
        let program =
            crate::render::shaderpack::contracts::entity::prepare_entity_glint_source_program(source, scope)?;
        self.entity_glint_source_program_cache =
            Some(((shader_pack_generation, scope), program.clone()));
        Ok(program)
    }

    pub(crate) fn hand_glint_source_program(
        &mut self,
        shader_pack_generation: u64,
        scope: TerrainProgramScope,
    ) -> GalResult<LoweredHandSourceProgram> {
        if let Some((key, program)) = self.hand_glint_source_program_cache.as_ref() {
            if *key == (shader_pack_generation, scope) {
                return Ok(program.clone());
            }
        }
        let source = self
            .shader_pack_sources
            .active()
            .filter(|source| source.generation() == shader_pack_generation)
            .ok_or_else(|| {
                GalError::invalid_argument(
                    "hand glint source program requires the active shader-pack generation",
                )
            })?;
        let program =
            crate::render::shaderpack::contracts::hand::prepare_hand_glint_source_program(source, scope)?;
        self.hand_glint_source_program_cache =
            Some(((shader_pack_generation, scope), program.clone()));
        Ok(program)
    }

    pub(crate) fn damaged_block_source_program(
        &mut self,
        shader_pack_generation: u64,
        scope: TerrainProgramScope,
    ) -> GalResult<LoweredTexturedMaterialSourceProgram> {
        if let Some((key, program)) = self.damaged_block_source_program_cache.as_ref() {
            if *key == (shader_pack_generation, scope) {
                return Ok(program.clone());
            }
        }
        let source = self.shader_pack_sources.active().ok_or_else(|| {
            GalError::invalid_argument("damagedblock source program requires an active shader pack")
        })?;
        if source.generation() != shader_pack_generation {
            return Err(GalError::invalid_argument(
                "damagedblock source program generation does not match the active shader pack",
            ));
        }
        let program =
            crate::render::shaderpack::contracts::damaged_block::prepare_damaged_block_source_program(
                source, scope,
            )?;
        self.damaged_block_source_program_cache =
            Some(((shader_pack_generation, scope), program.clone()));
        Ok(program)
    }
}

impl WorldPrimitiveFrontend {
    pub(crate) fn ensure_lowered_textured_material_source_program_layouts(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
    ) -> GalResult<LoweredTexturedMaterialSourceProgramLayouts> {
        let key = LoweredSourceTerrainProgramKey {
            shader_program_identity: program.identity.clone(),
            shader_pack_generation: program.shader_pack_generation,
        };
        if let Some(layouts) = self
            .lowered_textured_material_source_program_layouts
            .get(&key)
            .copied()
        {
            return Ok(layouts);
        }
        let layouts = program.execution_resource_layouts()?;
        let source_data = gal.create_resource_layout(layouts.source_data)?;
        let pack_resources = match gal.create_resource_layout(layouts.pack_resources) {
            Ok(layout) => layout,
            Err(error) => {
                let _ = gal.retire(source_data);
                return Err(error);
            }
        };
        let layouts = LoweredTexturedMaterialSourceProgramLayouts {
            source_data,
            pack_resources,
        };
        self.lowered_textured_material_source_program_layouts
            .insert(key, layouts);
        Ok(layouts)
    }

    pub(crate) fn ensure_lowered_textured_material_source_pack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        resources: &TerrainSourceOwnedResourceSet,
        local_texture: Option<(u32, u64)>,
    ) -> GalResult<LoweredTexturedMaterialSourcePackKey> {
        program.require_semantic_resources(resources.availability())?;
        let key = LoweredTexturedMaterialSourcePackKey {
            shader_program_identity: program.identity.clone(),
            shader_pack_generation: program.shader_pack_generation,
            world_generation: resources.availability().world_generation(),
            resource_generations: resources.generation_signature(),
            local_texture,
        };
        if self
            .lowered_textured_material_source_pack_resources
            .contains_key(&key)
        {
            return Ok(key);
        }
        let stale_keys = self
            .lowered_textured_material_source_pack_resources
            .keys()
            .filter(|existing| {
                existing.shader_program_identity == key.shader_program_identity
                    && existing.shader_pack_generation == key.shader_pack_generation
                    && existing.world_generation == key.world_generation
                    && existing.local_texture == key.local_texture
                    && existing.resource_generations != key.resource_generations
            })
            .cloned()
            .collect::<Vec<_>>();
        self.destroy_lowered_textured_material_source_pack_resources_for_keys(gal, stale_keys);
        if !lowered_source_pack_residency_allows(
            self.lowered_textured_material_source_pack_resources.len(),
        ) {
            return Err(GalError::unsupported_feature(format!(
                "lowered textured-material source pack residency limit reached (limit={})",
                LOWERED_SOURCE_PACK_RESIDENCY
            )));
        }
        let layouts = self.ensure_lowered_textured_material_source_program_layouts(gal, program)?;
        let resource_set = program.pack_resource_set_desc(
            format!(
                "source-textured-material-pack-{}-pack{}-world{}.set-one",
                program.identity.as_str(),
                key.shader_pack_generation,
                key.world_generation
            ),
            layouts.pack_resources,
            resources,
        )?;
        let resource_set = gal.create_resource_set(resource_set)?;
        self.lowered_textured_material_source_pack_resources.insert(
            key.clone(),
            LoweredTexturedMaterialSourcePackResources { resource_set },
        );
        Ok(key)
    }

    /// Resolves the selected source program's base-color semantic role for
    /// one ordered material batch. Atlas UVs retain the frame's copied atlas;
    /// local UVs receive a separately owned material texture wrapper. The
    /// returned table is still a normal semantic source-resource set, so the
    /// program/layout validation path remains identical on both backends.
    pub(in crate::render::worldrender) fn source_resources_for_textured_material_batch(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        base_resources: &TerrainSourceOwnedResourceSet,
        batch: SourceTexturedMaterialBatch,
        frame_id: u64,
    ) -> GalResult<(
        TerrainSourceOwnedResourceSet,
        Option<(u32, u64)>,
        Option<[i32; 2]>,
    )> {
        let local_role = if program.opaque_resource_bindings.bindings().iter()
            .any(|binding| binding.role() == TerrainSourceResourceRole::MaterialTexture) {
            TerrainSourceResourceRole::MaterialTexture
        } else {
            TerrainSourceResourceRole::MaterialAtlas
        };
        match batch.source_uv_space {
            WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS => {
                if local_role == TerrainSourceResourceRole::MaterialTexture {
                    return Err(GalError::invalid_argument("world glyph local-texture writer cannot inherit the terrain atlas"));
                }
                Ok((base_resources.clone(), None, None))
            }
            WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE => {
                let (resources, identity, extent) = self
                    .source_resources_with_local_material_texture_for_role(
                        gal,
                        program.shader_pack_generation,
                        base_resources,
                        batch.texture_id,
                        frame_id,
                        local_role,
                    )?;
                Ok((resources, Some(identity), Some(extent)))
            }
            value => Err(GalError::invalid_argument(format!(
                "world material source batch has unknown UV space {value}",
            ))),
        }
    }

    /// Rebinds the source contract's semantic material-atlas role to one
    /// copied, Rust-owned local texture. This is shared by source writers
    /// whose pack program consumes vanilla resource-location assets rather
    /// than the terrain atlas. The cache key stays generation-bound and never
    /// includes a Java renderer object or native backend identity.
    pub(crate) fn source_resources_with_local_material_texture(
        &mut self,
        gal: &mut VulkanicGal,
        shader_pack_generation: u64,
        base_resources: &TerrainSourceOwnedResourceSet,
        texture_id: u32,
        frame_id: u64,
    ) -> GalResult<(TerrainSourceOwnedResourceSet, (u32, u64), [i32; 2])> {
        self.source_resources_with_local_material_texture_for_role(gal, shader_pack_generation,
            base_resources, texture_id, frame_id, TerrainSourceResourceRole::MaterialAtlas)
    }

    fn source_resources_with_local_material_texture_for_role(
        &mut self,
        gal: &mut VulkanicGal,
        shader_pack_generation: u64,
        base_resources: &TerrainSourceOwnedResourceSet,
        texture_id: u32,
        frame_id: u64,
        role: TerrainSourceResourceRole,
    ) -> GalResult<(TerrainSourceOwnedResourceSet, (u32, u64), [i32; 2])> {
        if shader_pack_generation == 0
            || shader_pack_generation != base_resources.availability().shader_pack_generation()
        {
            return Err(GalError::invalid_argument(
                "source local material texture pack generation does not match its resource snapshot",
            ));
        }
        let texture_generation = self.source_local_texture_generation(texture_id)?;
        self.ensure_source_local_material_texture_resources(gal, texture_id, frame_id)?;
        let key = LoweredTexturedMaterialSourceLocalTextureKey {
            shader_pack_generation,
            world_generation: base_resources.availability().world_generation(),
            texture_id,
            texture_generation,
        };
        let (texture_view, sampler, width, height) = self
            .source_material_texture_resources
            .get(&texture_id)
            .map(|texture| (texture.view, texture.sampler, texture.width, texture.height))
            .ok_or_else(|| {
                GalError::backend(
                    "source material texture cache entry vanished before sampler binding",
                )
            })?;
        if !self
            .lowered_textured_material_source_local_texture_resources
            .contains_key(&key)
        {
            let combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!(
                        "source-local-material.texture{texture_id}.pack{}.world{}.material{}",
                        key.shader_pack_generation, key.world_generation, key.texture_generation,
                    ),
                    texture_view,
                    sampler,
                })?;
            self.lowered_textured_material_source_local_texture_resources
                .insert(
                    key.clone(),
                    LoweredTexturedMaterialSourceLocalTextureResources { combined_sampler },
                );
        }
        let combined_sampler = self
            .lowered_textured_material_source_local_texture_resources
            .get(&key)
            .map(|resources| resources.combined_sampler)
            .ok_or_else(|| {
                GalError::backend(
                    "source material local sampler vanished after successful creation",
                )
            })?;
        let resources = if role == TerrainSourceResourceRole::MaterialAtlas {
            base_resources.with_combined_sampler_override(role, combined_sampler, key.texture_generation)?
        } else {
            let local = TerrainSourceOwnedResourceSet::new(
                TerrainSourceResourceAvailabilitySet::new(shader_pack_generation, key.world_generation,
                    [TerrainSourceResourceAvailability { role: role.clone(), shape: role.expected_sampled_resource_shape(),
                        resource_generation: key.texture_generation }])?,
                [TerrainSourceOwnedResource { role: role.clone(), combined_sampler }],
            )?;
            let base = if base_resources.availability().resource_for(role.clone()).is_some() {
                base_resources.excluding_roles([role])?
            } else { base_resources.clone() };
            TerrainSourceOwnedResourceSet::merge([&base, &local])?
        };
        let width = i32::try_from(width).map_err(|_| {
            GalError::invalid_argument("local source material width exceeds source ivec2")
        })?;
        let height = i32::try_from(height).map_err(|_| {
            GalError::invalid_argument("local source material height exceeds source ivec2")
        })?;
        Ok((
            resources,
            (texture_id, key.texture_generation),
            [width, height],
        ))
    }

    /// Adds one Rust-owned local material texture to an exact-frame source
    /// resource snapshot. Entity and hand programs resolve their semantic
    /// `MaterialTexture` role here; neither inherits the terrain atlas binding
    /// used by block material writers.
    pub(crate) fn source_resources_for_local_material<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        base_resources: &TerrainSourceOwnedResourceSet,
        texture_id: u32,
        frame_id: u64,
    ) -> GalResult<(TerrainSourceOwnedResourceSet, (u32, u64))> {
        if program.shader_pack_generation()
            != base_resources.availability().shader_pack_generation()
        {
            return Err(GalError::invalid_argument(
                "entity source program and exact-frame resources have different shader-pack generations",
            ));
        }
        let texture_generation = self.source_local_texture_generation(texture_id)?;
        self.ensure_source_local_material_texture_resources(gal, texture_id, frame_id)?;
        if let Some(hit) = self.local_material_memo_lookup(
            program.identity(),
            program.shader_pack_generation(),
            base_resources,
            texture_id,
            texture_generation,
            frame_id,
        ) {
            return Ok(hit);
        }
        let key = LoweredTexturedMaterialSourceLocalTextureKey {
            shader_pack_generation: program.shader_pack_generation(),
            world_generation: base_resources.availability().world_generation(),
            texture_id,
            texture_generation,
        };
        let (texture_view, sampler) = self
            .source_material_texture_resources
            .get(&texture_id)
            .map(|texture| (texture.view, texture.sampler))
            .ok_or_else(|| {
                GalError::backend(
                    "entity source material texture cache entry vanished before sampler binding",
                )
            })?;
        if !self
            .lowered_textured_material_source_local_texture_resources
            .contains_key(&key)
        {
            let combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!(
                        "source-entity.local-texture{texture_id}.pack{}.world{}.material{}",
                        key.shader_pack_generation, key.world_generation, key.texture_generation,
                    ),
                    texture_view,
                    sampler,
                })?;
            self.lowered_textured_material_source_local_texture_resources
                .insert(
                    key.clone(),
                    LoweredTexturedMaterialSourceLocalTextureResources { combined_sampler },
                );
        }
        let combined_sampler = self
            .lowered_textured_material_source_local_texture_resources
            .get(&key)
            .map(|resources| resources.combined_sampler)
            .ok_or_else(|| {
                GalError::backend("entity source local sampler vanished after successful creation")
            })?;
        let local_resources = TerrainSourceOwnedResourceSet::new(
            TerrainSourceResourceAvailabilitySet::new(
                program.shader_pack_generation(),
                base_resources.availability().world_generation(),
                [TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::MaterialTexture,
                    shape: TerrainSourceResourceRole::MaterialTexture
                        .expected_sampled_resource_shape(),
                    resource_generation: key.texture_generation,
                }],
            )?,
            [TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::MaterialTexture,
                combined_sampler,
            }],
        )?;
        let base_without_local_texture = if base_resources
            .availability()
            .resource_for(TerrainSourceResourceRole::MaterialTexture)
            .is_some()
        {
            base_resources.excluding_roles([TerrainSourceResourceRole::MaterialTexture])?
        } else {
            base_resources.clone()
        };
        let resources =
            TerrainSourceOwnedResourceSet::merge([&base_without_local_texture, &local_resources])?;
        program.require_semantic_resources(resources.availability())?;
        self.local_material_memo_store(
            program.identity(),
            program.shader_pack_generation(),
            base_resources,
            texture_id,
            key.texture_generation,
            frame_id,
            (resources.clone(), (texture_id, key.texture_generation), combined_sampler),
        );
        Ok((resources, (texture_id, key.texture_generation)))
    }

    pub(crate) fn local_material_memo_lookup(
        &self,
        program: &ProgramIdentity,
        shader_pack_generation: u64,
        base: &TerrainSourceOwnedResourceSet,
        texture_id: u32,
        texture_generation: u64,
        frame_id: u64,
    ) -> Option<(TerrainSourceOwnedResourceSet, (u32, u64))> {
        let (memo_frame, groups) = self.local_material_resource_memo.as_ref()?;
        if *memo_frame != frame_id {
            return None;
        }
        let group = groups.iter().find(|group| {
            group.shader_pack_generation == shader_pack_generation
                && &group.program == program
                && group.base.same_snapshot(base)
        })?;
        let (resources, local_texture, combined_sampler) =
            group.entries.get(&(texture_id, texture_generation))?;
        // The combined sampler must still be the live cached one.
        let live = self
            .lowered_textured_material_source_local_texture_resources
            .get(&LoweredTexturedMaterialSourceLocalTextureKey {
                shader_pack_generation,
                world_generation: base.availability().world_generation(),
                texture_id,
                texture_generation,
            })
            .map(|resources| resources.combined_sampler);
        (live == Some(*combined_sampler)).then(|| (resources.clone(), *local_texture))
    }

    pub(crate) fn local_material_memo_store(
        &mut self,
        program: &ProgramIdentity,
        shader_pack_generation: u64,
        base: &TerrainSourceOwnedResourceSet,
        texture_id: u32,
        texture_generation: u64,
        frame_id: u64,
        value: (TerrainSourceOwnedResourceSet, (u32, u64), Handle),
    ) {
        if self
            .local_material_resource_memo
            .as_ref()
            .is_none_or(|(memo_frame, _)| *memo_frame != frame_id)
        {
            self.local_material_resource_memo = Some((frame_id, Vec::new()));
        }
        let (_, groups) = self.local_material_resource_memo.as_mut().expect("just set");
        let index = match groups.iter().position(|group| {
            group.shader_pack_generation == shader_pack_generation
                && &group.program == program
                && group.base.same_snapshot(base)
        }) {
            Some(index) => index,
            None => {
                groups.push(LocalMaterialMemoGroup {
                    program: program.clone(),
                    shader_pack_generation,
                    base: base.clone(),
                    entries: HashMap::new(),
                });
                groups.len() - 1
            }
        };
        groups[index]
            .entries
            .insert((texture_id, texture_generation), value);
    }

    /// Creates a local-textured source program's set-one bindings from a complete
    /// Rust-owned semantic snapshot. The cache key contains only semantic
    /// generations and local material identity; backend/native handles stay
    /// inside the resource set.
    pub(crate) fn ensure_lowered_local_source_pack_resources<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        resources: &TerrainSourceOwnedResourceSet,
        local_texture: (u32, u64),
    ) -> GalResult<LoweredEntitySourcePackKey> {
        program.require_semantic_resources(resources.availability())?;
        let key = LoweredEntitySourcePackKey {
            shader_program_identity: program.identity().clone(),
            shader_pack_generation: program.shader_pack_generation(),
            world_generation: resources.availability().world_generation(),
            resource_generations: resources.generation_signature(),
            local_texture,
        };
        if self.lowered_entity_source_pack_resources.contains_key(&key) {
            return Ok(key);
        }
        let stale_keys = self
            .lowered_entity_source_pack_resources
            .keys()
            .filter(|existing| {
                existing.shader_program_identity == key.shader_program_identity
                    && existing.shader_pack_generation == key.shader_pack_generation
                    && existing.world_generation == key.world_generation
                    && existing.local_texture == key.local_texture
                    && existing.resource_generations != key.resource_generations
            })
            .cloned()
            .collect::<Vec<_>>();
        self.destroy_lowered_entity_source_pack_resources_for_keys(gal, stale_keys);
        if !lowered_source_pack_residency_allows(self.lowered_entity_source_pack_resources.len()) {
            return Err(GalError::unsupported_feature(format!(
                "lowered entity source pack residency limit reached (limit={})",
                LOWERED_SOURCE_PACK_RESIDENCY
            )));
        }
        let layouts = self.ensure_lowered_local_source_program_layouts(gal, program)?;
        let resource_set = program.pack_resource_set_desc(
            format!(
                "source-entity-pack-{}-pack{}-world{}.set-one",
                program.identity().as_str(),
                key.shader_pack_generation,
                key.world_generation,
            ),
            layouts.pack_resources,
            resources,
        )?;
        let resource_set = gal.create_resource_set(resource_set)?;
        self.lowered_entity_source_pack_resources.insert(
            key.clone(),
            LoweredEntitySourcePackResources { resource_set },
        );
        Ok(key)
    }

    /// Compiles one explicit local-textured source pipeline for a fully Rust-owned
    /// named-output schema. It derives blend/cull/depth state from copied producer
    /// semantics, never from
    /// Java renderer state or an OpenGL compatibility object.
    pub(crate) fn ensure_lowered_local_source_pipeline_resources<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        depth_format: TextureFormat,
        color_formats: Vec<TextureFormat>,
        shadow_caster: Option<Option<f32>>,
    ) -> GalResult<LoweredEntitySourcePipelineKey> {
        if !matches!(
            material_mode,
            WORLD_MATERIAL_MODE_OPAQUE
                | WORLD_MATERIAL_MODE_CUTOUT
                | WORLD_MATERIAL_MODE_TRANSLUCENT
                | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
                | WORLD_MATERIAL_MODE_GLINT
                | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
        ) {
            return Err(GalError::unsupported_feature(format!(
                "entity source writer rejects material mode {material_mode}",
            )));
        }
        if shadow_caster.is_none()
            && material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT
            && depth_policy != WORLD_DEPTH_POLICY_TEST_NO_WRITE
        {
            return Err(GalError::invalid_argument(
                "translucent entity source draws require depth-test/no-write semantics",
            ));
        }
        if color_formats.is_empty() {
            return Err(GalError::invalid_argument(
                "entity source pipeline requires named color formats",
            ));
        }
        let key = LoweredEntitySourcePipelineKey {
            shader_program_identity: program.identity().clone(),
            shader_pack_generation: program.shader_pack_generation(),
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            depth_format,
            color_formats: color_formats.clone(),
            shadow_caster: shadow_caster.is_some(),
        };
        if self
            .lowered_entity_source_pipeline_resources
            .contains_key(&key)
        {
            return Ok(key);
        }
        let layouts = self.ensure_lowered_local_source_program_layouts(gal, program)?;
        let label = format!(
            "source-entity-pipeline-{}-mode{}-depth{}-cull{}-winding{}",
            program.identity().as_str(),
            material_mode,
            depth_policy,
            cull_policy,
            winding,
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<LoweredEntitySourcePipelineResources> {
            let [vertex_desc, fragment_desc] = program.shader_module_descriptors_with_alpha_cutoff(
                gal.capabilities().shader_conventions,
                match shadow_caster {
                    Some(cutoff) => cutoff,
                    None => source_entity_alpha_cutoff(material_mode)?,
                },
            );
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
                cull_mode: if shadow_caster.is_some() {
                    effective_cull_mode_for_winding(WORLD_CULL_NONE, winding)?
                } else {
                    effective_cull_mode_for_winding(cull_policy, winding)?
                },
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: if shadow_caster.is_some() {
                    crate::render::vulkanic::resources::RasterYDirection::Down
                } else {
                    crate::render::vulkanic::resources::RasterYDirection::Up
                },
                blend: if shadow_caster.is_some() {
                    BlendMode::Disabled
                } else {
                    source_textured_material_blend(material_mode)?
                },
                depth_compare: if shadow_caster.is_some() {
                    depth_compare_for_policy(WORLD_DEPTH_POLICY_TEST_WRITE)?
                } else if material_mode == WORLD_MATERIAL_MODE_GLINT {
                    // Vanilla GLINT pipeline: `EQUAL_DEPTH_TEST`, no write.
                    Some(CompareOp::Equal)
                } else if material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE {
                    None
                } else {
                    depth_compare_for_policy(depth_policy)?
                },
                depth_write: shadow_caster.is_some()
                    || (material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT
                        && material_mode != WORLD_MATERIAL_MODE_GLINT
                        && material_mode != WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                        && depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE),
                depth_bias: None,
                color_formats,
                depth_format: Some(depth_format),
                stencil: if material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE {
                    Some(StencilState {
                        front: StencilFaceState::replace(1, 0xff, 0xff),
                        back: StencilFaceState::replace(1, 0xff, 0xff),
                    })
                } else if material_mode == WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST {
                    Some(StencilState {
                        front: StencilFaceState::keep(CompareOp::Equal, 1, 0xff),
                        back: StencilFaceState::keep(CompareOp::Equal, 1, 0xff),
                    })
                } else {
                    None
                },
            })?;
            created.push(pipeline);
            Ok(LoweredEntitySourcePipelineResources {
                vertex_shader,
                fragment_shader,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.lowered_entity_source_pipeline_resources
            .insert(key.clone(), result?);
        Ok(key)
    }

    pub(crate) fn ensure_lowered_textured_material_source_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<LoweredTexturedMaterialSourcePipelineKey> {
        let blend = source_textured_material_blend(material_mode)?;
        self.ensure_lowered_source_material_pipeline_resources(
            gal,
            program,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats,
            blend,
            &[
                WORLD_MATERIAL_MODE_OPAQUE,
                WORLD_MATERIAL_MODE_CUTOUT,
                WORLD_MATERIAL_MODE_TRANSLUCENT,
                WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT,
                WORLD_MATERIAL_MODE_GLINT,
            ],
            "textured material",
            None,
        )
    }

    pub(crate) fn ensure_lowered_weather_source_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<LoweredTexturedMaterialSourcePipelineKey> {
        self.ensure_lowered_source_material_pipeline_resources(
            gal,
            program,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats,
            BlendMode::Alpha,
            &[WORLD_MATERIAL_MODE_TRANSLUCENT],
            "weather",
            None,
        )
    }

    pub(crate) fn ensure_lowered_cloud_source_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
        cloud_blend: CloudBlend,
    ) -> GalResult<LoweredTexturedMaterialSourcePipelineKey> {
        let blend = match cloud_blend {
            CloudBlend::SourceAlphaOver => BlendMode::Alpha,
        };
        self.ensure_lowered_source_material_pipeline_resources(
            gal,
            program,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats,
            blend,
            &[WORLD_MATERIAL_MODE_TRANSLUCENT],
            "clouds",
            None,
        )
    }

    pub(crate) fn ensure_lowered_source_material_pipeline_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
        blend: BlendMode,
        allowed_modes: &[u32],
        writer: &str,
        depth_bias: Option<DepthBias>,
    ) -> GalResult<LoweredTexturedMaterialSourcePipelineKey> {
        if !allowed_modes.contains(&material_mode) {
            return Err(GalError::unsupported_feature(format!(
                "{writer} source writer rejects material mode {material_mode}"
            )));
        }
        if color_formats.is_empty() {
            return Err(GalError::invalid_argument(
                "textured material source pipeline requires named color formats",
            ));
        }
        let key = LoweredTexturedMaterialSourcePipelineKey {
            shader_program_identity: program.identity.clone(),
            shader_pack_generation: program.shader_pack_generation,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats: color_formats.clone(),
        };
        if self
            .lowered_textured_material_source_pipeline_resources
            .contains_key(&key)
        {
            return Ok(key);
        }
        let layouts = self.ensure_lowered_textured_material_source_program_layouts(gal, program)?;
        let label = format!(
            "source-{writer}-pipeline-{}-mode{}-depth{}-cull{}-winding{}",
            program.identity.as_str(),
            material_mode,
            depth_policy,
            cull_policy,
            winding
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<LoweredTexturedMaterialSourcePipelineResources> {
            let [vertex_desc, fragment_desc] =
                program.shader_module_descriptors(gal.capabilities().shader_conventions);
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
                cull_mode: effective_cull_mode_for_winding(cull_policy, winding)?,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend,
                depth_compare: depth_compare_for_policy(depth_policy)?,
                depth_write: depth_policy == WORLD_DEPTH_POLICY_TEST_WRITE,
                depth_bias,
                color_formats,
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(LoweredTexturedMaterialSourcePipelineResources {
                vertex_shader,
                fragment_shader,
                pipeline_layout,
                pipeline,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.lowered_textured_material_source_pipeline_resources
            .insert(key.clone(), result?);
        Ok(key)
    }

    /// Stages one compact quad stream into the same completion-gated source
    /// transaction as terrain. It creates no pass and cannot select a route;
    /// the caller must still append it to the named source color transaction.
    pub(crate) fn prepare_lowered_textured_material_source_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        prepared: &PreparedTexturedMaterialSourceFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        local_texture: Option<(u32, u64)>,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<TexturedMaterialSourceDraw> {
        self.prepare_lowered_source_material_draw(
            gal,
            program,
            prepared,
            pack_resources,
            local_texture,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats,
            SourceMaterialWriterKind::TexturedMaterial,
            None,
        )
    }

    pub(crate) fn prepare_lowered_weather_source_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        prepared: &PreparedTexturedMaterialSourceFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        local_texture: Option<(u32, u64)>,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
    ) -> GalResult<TexturedMaterialSourceDraw> {
        self.prepare_lowered_source_material_draw(
            gal,
            program,
            prepared,
            pack_resources,
            local_texture,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats,
            SourceMaterialWriterKind::Weather,
            None,
        )
    }

    pub(crate) fn prepare_lowered_cloud_source_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        prepared: &PreparedTexturedMaterialSourceFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        local_texture: Option<(u32, u64)>,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
        cloud_blend: CloudBlend,
    ) -> GalResult<TexturedMaterialSourceDraw> {
        self.prepare_lowered_source_material_draw(
            gal,
            program,
            prepared,
            pack_resources,
            local_texture,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            color_formats,
            SourceMaterialWriterKind::Clouds,
            Some(cloud_blend),
        )
    }

    pub(crate) fn prepare_lowered_source_material_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredTexturedMaterialSourceProgram,
        prepared: &PreparedTexturedMaterialSourceFrame,
        pack_resources: &TerrainSourceOwnedResourceSet,
        local_texture: Option<(u32, u64)>,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        color_formats: Vec<TextureFormat>,
        writer_kind: SourceMaterialWriterKind,
        cloud_blend: Option<CloudBlend>,
    ) -> GalResult<TexturedMaterialSourceDraw> {
        let interface = &program.execution_interface;
        interface.validate()?;
        if prepared.vertex_stream.is_empty()
            || prepared.vertex_stream.len() % interface.vertex_stride as usize != 0
            || prepared.vertex_stream.len() / interface.vertex_stride as usize
                != prepared.primitives.len() * 4
        {
            return Err(GalError::invalid_argument(
                "textured material source stream must contain exactly four packed vertices per primitive",
            ));
        }
        if interface.scalar_uniforms.is_some() != !prepared.scalar_uniforms.is_empty() {
            return Err(GalError::invalid_argument(
                "textured material source scalar payload does not match its declared interface",
            ));
        }
        let vertex_bytes = u64::try_from(prepared.vertex_stream.len()).map_err(|_| {
            GalError::invalid_argument("textured material source vertex stream exceeds u64")
        })?;
        let stream = self.allocate_source_terrain_frame_stream(
            gal,
            prepared.frame_id,
            u64::from(interface.legacy_transform_bytes),
            u64::from(interface.scalar_uniform_bytes),
            vertex_bytes,
        )?;
        let frame_key = LoweredTexturedMaterialSourceFrameDataKey {
            shader_program_identity: program.identity.clone(),
            shader_pack_generation: program.shader_pack_generation,
            stream_buffer: stream.buffer,
            vertex_bytes,
        };
        if !self
            .lowered_textured_material_source_frame_data_resources
            .contains_key(&frame_key)
        {
            let layouts =
                self.ensure_lowered_textured_material_source_program_layouts(gal, program)?;
            let mut bindings = vec![
                ResourceBinding {
                    binding: interface.vertex_stream.binding,
                    array_index: 0,
                    resource: stream.buffer,
                    kind: ResourceBindingKind::StorageBuffer,
                    access: AccessFlags::READ,
                    dynamic_offsets: vec![0],
                    buffer_range: Some(vertex_bytes),
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
            ];
            if let Some(scalar) = interface.scalar_uniforms {
                bindings.push(ResourceBinding {
                    binding: scalar.binding,
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
                label: format!(
                    "source-textured-material-frame-{}-stream{}.set-zero",
                    program.identity.as_str(),
                    stream.buffer.index()
                ),
                layout: layouts.source_data,
                bindings,
            })?;
            self.lowered_textured_material_source_frame_data_resources
                .insert(
                    frame_key.clone(),
                    LoweredTexturedMaterialSourceFrameDataResources { resource_set },
                );
        }
        let pack_key = self.ensure_lowered_textured_material_source_pack_resources(
            gal,
            program,
            pack_resources,
            local_texture,
        )?;
        let pipeline_key = match writer_kind {
            SourceMaterialWriterKind::TexturedMaterial => self
                .ensure_lowered_textured_material_source_pipeline_resources(
                    gal,
                    program,
                    material_mode,
                    depth_policy,
                    cull_policy,
                    winding,
                    color_formats,
                )?,
            SourceMaterialWriterKind::Weather => self
                .ensure_lowered_weather_source_pipeline_resources(
                    gal,
                    program,
                    material_mode,
                    depth_policy,
                    cull_policy,
                    winding,
                    color_formats,
                )?,
            SourceMaterialWriterKind::Lines => self
                .ensure_lowered_source_material_pipeline_resources(
                    gal,
                    program,
                    material_mode,
                    depth_policy,
                    cull_policy,
                    winding,
                    color_formats,
                    // Vanilla `RenderPipelines.LINES`: BlendFunction.TRANSLUCENT.
                    BlendMode::Alpha,
                    &[WORLD_MATERIAL_MODE_TRANSLUCENT],
                    "lines",
                    None,
                )?,
            SourceMaterialWriterKind::DamagedBlock => self
                .ensure_lowered_source_material_pipeline_resources(
                    gal,
                    program,
                    material_mode,
                    depth_policy,
                    cull_policy,
                    winding,
                    color_formats,
                    // Vanilla `RenderPipelines.CRUMBLING`: DST_COLOR/SRC_COLOR
                    // colour, ONE/ZERO alpha; Iris keeps it (no pack blend).
                    BlendMode::DoubleModulate,
                    &[WORLD_MATERIAL_MODE_TRANSLUCENT],
                    "damagedblock",
                    Some(DepthBias {
                        constant_factor: -1.0,
                        slope_factor: -10.0,
                    }),
                )?,
            SourceMaterialWriterKind::Clouds => self
                .ensure_lowered_cloud_source_pipeline_resources(
                    gal,
                    program,
                    material_mode,
                    depth_policy,
                    cull_policy,
                    winding,
                    color_formats,
                    cloud_blend.ok_or_else(|| {
                        GalError::backend(
                            "cloud source draw lost its declared semantic blend contract",
                        )
                    })?,
                )?,
        };
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
            return Err(GalError::backend(
                "textured material source payload resolved to a different frame stream slot",
            ));
        }
        let pending_texture_uploads = self
            .pending_source_material_texture_uploads
            .remove(&prepared.frame_id)
            .unwrap_or_default();
        for pending in pending_texture_uploads {
            transaction
                .source_material_texture_ids
                .insert(pending.texture_id);
            transaction.operations.extend(pending.operations);
        }
        transaction.stage_stream_parts(
            stream,
            &prepared.legacy_texture_transforms,
            &prepared.scalar_uniforms,
            &prepared.vertex_stream,
        );
        let resource_set = self
            .lowered_textured_material_source_frame_data_resources
            .get(&frame_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| {
                GalError::backend("textured material source frame resources vanished")
            })?;
        let pack_resource_set = self
            .lowered_textured_material_source_pack_resources
            .get(&pack_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("textured material source pack resources vanished"))?;
        let (pipeline, pipeline_layout) = self
            .lowered_textured_material_source_pipeline_resources
            .get(&pipeline_key)
            .map(|resources| (resources.pipeline, resources.pipeline_layout))
            .ok_or_else(|| GalError::backend("textured material source pipeline vanished"))?;
        let vertices =
            u32::try_from(prepared.primitives.len().checked_mul(6).ok_or_else(|| {
                GalError::invalid_argument("textured material source direct vertex count overflows")
            })?)
            .map_err(|_| {
                GalError::invalid_argument(
                    "textured material source direct vertex count exceeds u32",
                )
            })?;
        Ok(TexturedMaterialSourceDraw {
            pipeline,
            pipeline_layout,
            resource_set,
            resource_set_dynamic_offsets: {
                let mut offsets = vec![stream.instance_offset, stream.legacy_transform_offset];
                if let Some(offset) = stream.scalar_uniform_offset {
                    offsets.push(offset);
                }
                offsets
            },
            shader_resource_set: Some(TerrainShaderResourceSet {
                set_index: 1,
                set: pack_resource_set,
            }),
            vertices,
        })
    }

    /// Stages immutable Rust-owned geometry for a lowered local-textured source
    /// program. This remains private resource preparation: the caller must
    /// still attach the queued upload to one confirmed combined source-frame
    /// submission before any entity writer can consume it.
    pub(crate) fn ensure_lowered_local_source_geometry_resources<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        _program: &P,
        mesh: &SourceEntityMeshAsset,
    ) -> GalResult<LoweredSourceTerrainDataKey> {
        let key = LoweredSourceTerrainDataKey {
            mesh_key: mesh.mesh_key,
            mesh_generation: mesh.mesh_generation,
            abi: SourceGeometryAbi::LocalTextured,
        };
        // Constructors validate assets; re-check only before the one upload
        // instead of scanning every index on every draw.
        if !self.lowered_source_terrain_geometry_resources.contains_key(&key) {
            mesh.validate()?;
        }
        self.ensure_lowered_source_mesh_geometry_resources(
            gal,
            key,
            "source-local-textured",
            &mesh.vertex_bytes,
            &mesh.index_bytes,
        )
    }

    /// Materializes a local-textured source program's set-zero bindings and queues
    /// its frame-local uploads in the existing completion-gated source-frame
    /// transaction. This is deliberately not a pass writer: no target,
    /// pipeline, command recording, route, or presentation can follow from
    /// this helper alone.
    pub(in crate::render::worldrender) fn ensure_lowered_local_source_data_resources<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        frame_id: u64,
        mesh: &SourceEntityMeshAsset,
        legacy_texture_transforms: &[u8],
        scalar_uniforms: &[u8],
        instance_transforms: &[u8],
    ) -> GalResult<(
        LoweredSourceTerrainDataKey,
        LoweredSourceTerrainFrameDataKey,
        SourceTerrainFrameStreamAllocation,
    )> {
        let interface = program.execution_interface();
        let validated_key = (program as *const P as *const () as usize, program.shader_pack_generation());
        if self.local_source_validated_interfaces.get(frame_id, &validated_key).is_none() {
            interface.validate()?;
            self.local_source_validated_interfaces.insert(frame_id, validated_key, ());
        }
        // `mesh` is an immutable asset validated by its constructor.
        let geometry_key =
            self.ensure_lowered_local_source_geometry_resources(gal, program, mesh)?;
        let required_instance_bytes = u64::try_from(instance_transforms.len()).map_err(|_| {
            GalError::invalid_argument("source entity instance payload length exceeds u64")
        })?;
        if required_instance_bytes == 0 {
            return Err(GalError::invalid_argument(
                "source entity resource preparation requires non-empty instance data",
            ));
        }
        if interface.scalar_uniforms.is_some() && scalar_uniforms.is_empty() {
            return Err(GalError::invalid_argument(
                "source entity program declares scalar uniforms without packed scalar data",
            ));
        }
        if interface.scalar_uniforms.is_none() && !scalar_uniforms.is_empty() {
            return Err(GalError::invalid_argument(
                "source entity program has no scalar uniform binding but packed scalar data was supplied",
            ));
        }
        if legacy_texture_transforms.len() != interface.legacy_transform_bytes as usize {
            return Err(GalError::invalid_argument(
                "source entity texture transforms do not match the fixed source ABI",
            ));
        }
        let stream = self.allocate_source_terrain_frame_stream(
            gal,
            frame_id,
            u64::from(interface.legacy_transform_bytes),
            u64::from(interface.scalar_uniform_bytes),
            required_instance_bytes,
        )?;
        let key = LoweredSourceTerrainFrameDataKey {
            geometry: geometry_key.clone(),
            shader_program_identity: program.identity().clone(),
            shader_pack_generation: program.shader_pack_generation(),
            stream_buffer: stream.buffer,
            instance_bytes: required_instance_bytes,
        };
        if !self
            .lowered_source_terrain_frame_data_resources
            .contains_key(&key)
        {
            let program_layouts = self.ensure_lowered_local_source_program_layouts(gal, program)?;
            let label = format!(
                "source-entity-frame-data-{}-mesh{}-gen{}",
                program.identity().as_str(),
                geometry_key.mesh_key,
                geometry_key.mesh_generation,
            );
            let vertex_buffer = self
                .lowered_source_terrain_geometry_resources
                .get(&geometry_key)
                .map(|resources| resources.vertex_buffer)
                .ok_or_else(|| GalError::backend("source entity geometry resources vanished"))?;
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
                        buffer_range: Some(required_instance_bytes),
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
                    let _ = gal.retire(handle);
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
            .entry(frame_id)
            .or_insert_with(|| SourceTerrainFrameTransaction {
                frame_id,
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
                "source entity frame payloads resolved to different stream slots",
            ));
        }
        // Local entity textures are first-use resources. Their upload must be
        // part of this source-frame transaction before the entity can sample
        // them through the selected shader-pack program.
        let pending_texture_uploads = self
            .pending_source_material_texture_uploads
            .remove(&frame_id)
            .unwrap_or_default();
        for pending in pending_texture_uploads {
            transaction
                .source_material_texture_ids
                .insert(pending.texture_id);
            transaction.operations.extend(pending.operations);
        }
        if !geometry_upload_ops.is_empty() {
            transaction
                .geometry_uploads
                .push((geometry_key.clone(), geometry_upload_ops));
        }
        transaction.stage_stream_parts(
            stream,
            legacy_texture_transforms,
            scalar_uniforms,
            instance_transforms,
        );
        Ok((geometry_key, key, stream))
    }

    // Keep focused entity-cache tests expressed in their producer vocabulary
    // while production preparation uses the shared local-textured helpers.
    #[cfg(test)]
    pub(crate) fn source_resources_for_entity_local_material(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredEntitySourceProgram,
        base_resources: &TerrainSourceOwnedResourceSet,
        texture_id: u32,
        frame_id: u64,
    ) -> GalResult<(TerrainSourceOwnedResourceSet, (u32, u64))> {
        self.source_resources_for_local_material(gal, program, base_resources, texture_id, frame_id)
    }

    /// Prepares one explicit indexed draw for a local-textured source program
    /// from copied semantics and Rust-owned assets. It has no target, pass,
    /// route, or presentation ownership; a caller must still record it
    /// through that program's dedicated named phase in one combined
    /// submission.
    pub(crate) fn prepare_lowered_local_source_draw<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        frame_id: u64,
        mesh: &Arc<SourceEntityMeshAsset>,
        section_index: u32,
        section_count: u32,
        texture_id: u32,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        depth_format: TextureFormat,
        legacy_texture_transforms: &[u8],
        scalar_uniforms: &[u8],
        instance_transforms: &[u8],
        base_resources: &TerrainSourceOwnedResourceSet,
        color_formats: Vec<TextureFormat>,
        shadow_caster: Option<Option<f32>>,
    ) -> GalResult<EntitySourceDraw> {
        // Entity source assets are already generation-validated when copied
        // from the world asset cache.  A non-zero instance light may produce
        // a transient geometry variant with a derived mesh key, so looking
        // that key up in the ordinary asset cache here would reject a valid
        // Rust-owned payload.
        if instance_transforms.len() % TERRAIN_SOURCE_INSTANCE_BYTES != 0 {
            return Err(GalError::invalid_argument(
                "source entity instance payload does not contain whole records",
            ));
        }
        let instance_count = u32::try_from(
            instance_transforms.len() / TERRAIN_SOURCE_INSTANCE_BYTES,
        )
        .map_err(|_| GalError::invalid_argument("source entity instance count exceeds u32"))?;
        if instance_count == 0 {
            return Err(GalError::invalid_argument(
                "source entity draw preparation requires at least one instance",
            ));
        }
        let section_end = section_index
            .checked_add(section_count)
            .filter(|_| section_count != 0)
            .ok_or_else(|| GalError::invalid_argument("source entity draw selects an empty section run"))?;
        let sections = mesh
            .sections
            .get(section_index as usize..section_end as usize)
            .ok_or_else(|| GalError::invalid_argument("source entity draw selects a missing section"))?;
        if sections.iter().any(|section| {
            section.texture_id != texture_id
                || section.material_mode != material_mode
                || section.cull_policy != cull_policy
                || section.winding != winding
        }) {
            return Err(GalError::invalid_argument(
                "source entity draw semantic group no longer matches its immutable mesh section",
            ));
        }
        let index_offset = sections[0].index_offset;
        if crate::render::worldrender::source::frames::source_entity_section_run_end(
            mesh,
            section_index,
            section_count,
        )?
        .is_none()
        {
            return Err(GalError::invalid_argument(
                "source entity draw section run is not index-contiguous",
            ));
        }
        let index_count = sections.iter().try_fold(0u32, |total, section| {
            total
                .checked_add(section.index_count)
                .ok_or_else(|| GalError::invalid_argument("source entity section run index count overflows"))
        })?;
        let (resources, local_texture) = self.source_resources_for_local_material(
            gal,
            program,
            base_resources,
            texture_id,
            frame_id,
        )?;
        let (geometry_key, frame_data_key, stream) = self
            .ensure_lowered_local_source_data_resources(
                gal,
                program,
                frame_id,
                mesh.as_ref(),
                legacy_texture_transforms,
                scalar_uniforms,
                instance_transforms,
            )?;
        let pack_resource_set =
            self.resolve_lowered_local_source_pack(gal, program, frame_id, resources, local_texture)?;
        let (pipeline, pipeline_layout) = self.resolve_lowered_local_source_pipeline(
            gal,
            program,
            frame_id,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            depth_format,
            color_formats,
            shadow_caster,
        )?;
        let index_buffer = self
            .lowered_source_terrain_geometry_resources
            .get(&geometry_key)
            .map(|resources| resources.index_buffer)
            .ok_or_else(|| GalError::backend("source entity geometry resources vanished"))?;
        let resource_set = self
            .lowered_source_terrain_frame_data_resources
            .get(&frame_data_key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("source entity frame data resources vanished"))?;
        let mut dynamic_offsets = vec![stream.legacy_transform_offset];
        if stream.scalar_uniform_offset.is_some() {
            dynamic_offsets.push(stream.scalar_uniform_offset.expect("checked scalar offset"));
        }
        dynamic_offsets.push(stream.instance_offset);
        Ok(EntitySourceDraw {
            pipeline,
            pipeline_layout,
            resource_set,
            resource_set_dynamic_offsets: dynamic_offsets.into(),
            shader_resource_set: TerrainShaderResourceSet {
                set_index: 1,
                set: pack_resource_set,
            },
            index_buffer,
            index_offset,
            index_type: IndexType::U32,
            index_count,
            instance_count,
        })
    }

    /// Set-one bindings for one resource snapshot and local texture, shared
    /// by every draw of the frame that uses them.
    pub(crate) fn resolve_lowered_local_source_pack<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        frame_id: u64,
        resources: TerrainSourceOwnedResourceSet,
        local_texture: (u32, u64),
    ) -> GalResult<Handle> {
        let memo_key = LocalSourcePackMemoKey {
            program: program as *const P as *const () as usize,
            shader_pack_generation: program.shader_pack_generation(),
            resources,
            local_texture,
        };
        if let Some(set) = self.local_source_pack_memo.get(frame_id, &memo_key) {
            return Ok(set);
        }
        let key = self.ensure_lowered_local_source_pack_resources(
            gal,
            program,
            &memo_key.resources,
            local_texture,
        )?;
        let set = self
            .lowered_entity_source_pack_resources
            .get(&key)
            .map(|resources| resources.resource_set)
            .ok_or_else(|| GalError::backend("source entity pack resources vanished"))?;
        self.local_source_pack_memo.insert(frame_id, memo_key, set);
        Ok(set)
    }

    /// Pipeline and layout for one local-textured draw configuration,
    /// resolved once per frame.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn resolve_lowered_local_source_pipeline<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
        frame_id: u64,
        material_mode: u32,
        depth_policy: u32,
        cull_policy: u32,
        winding: u32,
        depth_format: TextureFormat,
        color_formats: Vec<TextureFormat>,
        shadow_caster: Option<Option<f32>>,
    ) -> GalResult<(Handle, Handle)> {
        let memo_key = LocalSourcePipelineMemoKey {
            program: program as *const P as *const () as usize,
            shader_pack_generation: program.shader_pack_generation(),
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            depth_format,
            color_formats: color_formats.iter().copied().collect(),
            shadow_caster: shadow_caster.map(|cutoff| cutoff.map(f32::to_bits)),
        };
        if let Some(resolved) = self.local_source_pipeline_memo.get(frame_id, &memo_key) {
            return Ok(resolved);
        }
        let key = self.ensure_lowered_local_source_pipeline_resources(
            gal,
            program,
            material_mode,
            depth_policy,
            cull_policy,
            winding,
            depth_format,
            color_formats,
            shadow_caster,
        )?;
        let resolved = self
            .lowered_entity_source_pipeline_resources
            .get(&key)
            .map(|resources| (resources.pipeline, resources.pipeline_layout))
            .ok_or_else(|| GalError::backend("source entity pipeline resources vanished"))?;
        self.local_source_pipeline_memo.insert(frame_id, memo_key, resolved);
        Ok(resolved)
    }

    pub(crate) fn ensure_lowered_local_source_program_layouts<P: LocalTexturedSourceProgram>(
        &mut self,
        gal: &mut VulkanicGal,
        program: &P,
    ) -> GalResult<LoweredSourceTerrainProgramLayouts> {
        self.ensure_lowered_source_program_layouts(
            gal,
            program.identity().clone(),
            program.shader_pack_generation(),
            program.execution_resource_layouts()?,
        )
    }

    /// Allocates the standalone base-color resource required by a local-UV
    /// source-material batch. This deliberately shares the ordinary material
    /// asset decode/update generation, but not its direct-route pipeline or
    /// resource set: selected-source execution owns a distinct pass binding.
    pub(crate) fn ensure_source_local_material_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
        texture_id: u32,
        frame_id: u64,
    ) -> GalResult<()> {
        if self
            .source_material_texture_upload_confirmed
            .contains(&texture_id)
            || self.source_material_texture_staged.contains(&texture_id)
            || self
                .pending_source_material_texture_uploads
                .values()
                .flatten()
                .any(|pending| pending.texture_id == texture_id)
            || self
                .pending_source_terrain_frame_transactions
                .values()
                .any(|transaction| {
                    transaction
                        .source_material_texture_ids
                        .contains(&texture_id)
                })
        {
            // Texture-generation replacement retires this resource and
            // recreates its copied payload. Rewriting a persistent upload
            // buffer every source frame would race its prior transfer read
            // without a host-write ownership state, so an admitted resource
            // is immutable for its generation.
            return Ok(());
        }
        if self
            .source_material_texture_resources
            .contains_key(&texture_id)
        {
            // A rejected private candidate may have created the image before
            // its upload transaction was submitted. Retire its dependent
            // views/resource sets as one unit before recreating the payload;
            // otherwise a cached lowered set can retain the stale generation.
            self.discard_unsubmitted_source_material_textures(gal, &BTreeSet::from([texture_id]));
        }
        let (texture_bytes, texture_width, texture_height) =
            self.source_local_material_texture_bytes(texture_id)?;
        // Account for the sampled image and its upload buffer before creating
        // either handle.  The check is deliberately side-effect free: a
        // rejected source candidate cannot leave a partially created texture
        // behind or evict a resource referenced by an in-flight submission.
        let upload_bytes = u64::try_from(texture_bytes.len()).map_err(|_| {
            GalError::invalid_argument("source material texture payload exceeds u64")
        })?;
        let requested_bytes = texture_mip_chain_byte_size(texture_width, texture_height, 4)?
            .checked_add(upload_bytes)
            .ok_or_else(|| {
                GalError::invalid_argument("source material texture residency overflows u64")
            })?;
        let resident_sizes = self
            .source_material_texture_resources
            .values()
            .map(|resources| {
                texture_mip_chain_byte_size(resources.width, resources.height, 4).and_then(
                    |image_bytes| {
                        u64::from(resources.width)
                            .checked_mul(u64::from(resources.height))
                            .and_then(|pixels| pixels.checked_mul(4))
                            .and_then(|upload_bytes| image_bytes.checked_add(upload_bytes))
                            .ok_or_else(|| {
                                GalError::backend(
                                    "source material texture residency accounting overflow",
                                )
                            })
                    },
                )
            })
            .collect::<GalResult<Vec<_>>>()?;
        let resident_bytes = resident_sizes
            .into_iter()
            .try_fold(0_u64, u64::checked_add)
            .ok_or_else(|| {
                GalError::backend("source material texture residency accounting overflow")
            })?;
        if !lowered_source_texture_budget_allows(resident_bytes, requested_bytes) {
            return Err(GalError::unsupported_feature(format!(
                "lowered source texture residency budget exceeded (resident={} requested={} limit={})",
                resident_bytes, requested_bytes, LOWERED_SOURCE_TEXTURE_MAX_BYTES
            )));
        }
        let texture_generation = self.source_local_texture_generation(texture_id)?;
        // Destroy-stage decals use world-projected UVs and repeat, like the
        // vanilla crumbling texture; every other local material clamps.
        let local_address = if self.mesh_assets.values().any(|asset| {
            asset.sections.iter().any(|section| {
                section.texture_id == texture_id
                    && section.material_id == WORLD_MATERIAL_ID_MODEL_CRUMBLING
            })
        }) {
            SamplerAddressMode::Repeat
        } else {
            SamplerAddressMode::ClampToEdge
        };
        let label =
            format!("source-textured-material.texture{texture_id}.generation{texture_generation}",);
        let mut created = Vec::new();
        let result = (|| -> GalResult<MeshTextureResources> {
            let upload_buffer = gal.create_buffer(BufferDesc {
                label: format!("{label}.upload"),
                size: texture_bytes.len() as u64,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                    BufferUsage::HostWrite,
                ],
            })?;
            created.push(upload_buffer);
            let texture = gal.create_texture(TextureDesc {
                label: format!("{label}.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: texture_width,
                    height: texture_height,
                    depth: 1,
                },
                mip_levels: texture_mip_level_count(texture_width, texture_height),
                array_layers: 1,
                usages: vec![
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })?;
            created.push(texture);
            // Copied resource-pack sampling (`.mcmeta` blur/clamp, e.g. the
            // glint texture) is the texture's own semantic, as on the vanilla
            // route; unspecified materials keep the local default.
            let sampling = self
                .mesh_texture_assets
                .get(&texture_id)
                .and_then(|asset| asset.sampling)
                .unwrap_or(crate::render::shared::texture_sampling::TextureSampling {
                    filter: SamplerFilter::Nearest,
                    address: local_address,
                });
            let sampler = gal.create_sampler(
                sampling.descriptor(format!("{label}.sampler"), mesh_texture_mip_filter(texture_id)),
            )?;
            created.push(sampler);
            let view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.view"),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: texture_mip_level_count(texture_width, texture_height),
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(view);
            let resources = MeshTextureResources {
                upload_buffer,
                texture,
                sampler,
                view,
                width: texture_width,
                height: texture_height,
                mip_levels: texture_mip_level_count(texture_width, texture_height),
            };
            let upload = Self::mesh_texture_upload_ops_from_state(
                &resources,
                texture_bytes,
                texture_width,
                texture_height,
                TextureUsageState::Undefined,
            )?;
            self.source_material_texture_upload_operations
                .insert(texture_id, upload.clone());
            self.pending_source_material_texture_uploads
                .entry(frame_id)
                .or_default()
                .push(PendingSourceMaterialTextureUpload {
                    texture_id,
                    operations: upload,
                });
            Ok(resources)
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.retire(handle);
            }
        }
        self.source_material_texture_resources
            .insert(texture_id, result?);
        self.source_material_texture_staged.insert(texture_id);
        Ok(())
    }

    /// Returns the generation of the Rust-owned local texture payload that a
    /// selected source writer binds. Indexed mesh assets take precedence over
    /// the small generic material registry so entity/model producers can use
    /// their copied resource-location texture payloads without expanding that
    /// registry per producer.
    pub(crate) fn source_local_texture_generation(&self, texture_id: u32) -> GalResult<u64> {
        if let Some(texture) = self.mesh_texture_assets.get(&texture_id) {
            if texture.animation_generation == 0 {
                return Err(GalError::invalid_argument(format!(
                    "source local mesh texture {texture_id} has no generation",
                )));
            }
            return Ok(texture.animation_generation);
        }
        if frame::material_quads::canonical_texture_id(texture_id).is_some()
            && self.material_asset_generation != 0
        {
            return Ok(self.material_asset_generation);
        }
        Err(GalError::ffi(
            StatusCode::UnknownEnum,
            format!("unknown source local material texture id {texture_id}"),
        ))
    }
}

pub(in crate::render::worldrender) fn source_material_batches_for_program(
    frame: &WorldPrimitiveFrame,
    source_program: u32,
    allowed_modes: &[u32],
) -> GalResult<Vec<SourceTexturedMaterialBatch>> {
    let mut batches = Vec::new();
    for (index, quad) in frame.material_quads.iter().enumerate() {
        if is_distant_horizons_generic_stratum(quad.stratum) {
            continue;
        }
        let source_program_matches = quad.source_program == source_program
            || (source_program == WORLD_MATERIAL_SOURCE_TEXTURED
                && matches!(
                    quad.source_program,
                    WORLD_MATERIAL_SOURCE_PARTICLES | WORLD_MATERIAL_SOURCE_ENTITY_MODEL
                ));
        if !source_program_matches {
            continue;
        }
        // The same copied sun/moon quads have already been consumed by the
        // pre-terrain gbuffers_skytextured stage. Replaying them here through
        // gbuffers_textured, after deferred1 has generated clouds, overwrites
        // the clouds with a rectangular patch of sky color.
        if source_program == WORLD_MATERIAL_SOURCE_TEXTURED
            && (quad.material_id == WORLD_MATERIAL_ID_CELESTIAL || is_end_sky_quad(quad))
        {
            continue;
        }
        // Vanilla's below-horizon dark disc (camera under the horizon, e.g.
        // underwater) is sky geometry: Iris draws it with gbuffers_skybasic,
        // i.e. the pack's sky colour, which the source sky initializer has
        // already written for every direction. It is not a textured writer.
        if is_vanilla_dark_disc_quad(quad) {
            continue;
        }
        // Iris disables vanilla entity-shadow decals whenever the pack owns a
        // shadow pass (`IrisRenderingPipeline.shouldDisableVanillaEntityShadows`
        // is `shadowRenderer != null`). The selected-source route only arms
        // with an admitted shadow program, so these copied decals never draw.
        if quad.material_id == WORLD_MATERIAL_ID_ENTITY_SHADOW {
            continue;
        }
        if !allowed_modes.contains(&quad.material_mode) {
            return Err(GalError::unsupported_feature(format!(
                "world material quad {index} has unsupported source material mode {} for source program {source_program}",
                quad.material_mode
            )));
        }
        // Shader-pack execution is outside the vanilla migration contract, and
        // gbuffers_textured does not imply vanilla's EMISSIVE/additive pipeline.
        // Keep this family unavailable there instead of silently flattening it.
        if quad.material_id == WORLD_MATERIAL_ID_ENERGY_SWIRL {
            return Err(GalError::unsupported_feature(format!(
                "world material quad {index} energy swirl has no selected-source material contract"
            )));
        }
        if !matches!(
            quad.depth_policy,
            WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_NO_WRITE
        ) {
            return Err(GalError::unsupported_feature(format!(
                "world material quad {index} has unsupported source depth policy {} (material {}, texture {}, source program {})",
                quad.depth_policy, quad.material_id, quad.texture_id, quad.source_program
            )));
        }
        if !matches!(quad.winding, WORLD_WINDING_CCW | WORLD_WINDING_CW) {
            return Err(GalError::invalid_argument(format!(
                "world material quad {index} has invalid source winding {}",
                quad.winding
            )));
        }
        if quad.block_entity_id < -1 {
            return Err(GalError::invalid_argument(format!(
                "world material quad {index} has invalid block entity id {}",
                quad.block_entity_id
            )));
        }
        if !matches!(
            quad.source_uv_space,
            WORLD_MATERIAL_SOURCE_UV_LOCAL_TEXTURE | WORLD_MATERIAL_SOURCE_UV_MINECRAFT_BLOCK_ATLAS
        ) {
            return Err(GalError::invalid_argument(format!(
                "world material quad {index} has invalid source UV space {}",
                quad.source_uv_space
            )));
        }
        let (depth_policy, cull_policy) = material_depth_and_cull(quad);
        let same_state = batches
            .last()
            .is_some_and(|batch: &SourceTexturedMaterialBatch| {
                batch.start + batch.count == index
                    && batch.material_id == quad.material_id
                    && batch.texture_id == quad.texture_id
                    && batch.source_uv_space == quad.source_uv_space
                    && batch.material_mode == quad.material_mode
                    && batch.depth_policy == depth_policy
                    && batch.cull_policy == cull_policy
                    && batch.winding == quad.winding
                    && batch.block_entity_id == quad.block_entity_id
            });
        if same_state
            && batches.last().expect("checked source batch").count
                < WORLD_MAX_MATERIAL_QUADS_PER_BATCH
        {
            batches.last_mut().expect("checked source batch").count += 1;
        } else {
            batches.push(SourceTexturedMaterialBatch {
                material_id: quad.material_id,
                start: index,
                count: 1,
                texture_id: quad.texture_id,
                source_uv_space: quad.source_uv_space,
                material_mode: quad.material_mode,
                depth_policy,
                cull_policy,
                winding: quad.winding,
                block_entity_id: quad.block_entity_id,
            });
        }
    }
    Ok(batches)
}

pub(in crate::render::worldrender) fn source_textured_material_batches(
    frame: &WorldPrimitiveFrame,
) -> GalResult<Vec<SourceTexturedMaterialBatch>> {
    source_material_batches_for_program(
        frame,
        WORLD_MATERIAL_SOURCE_TEXTURED,
        &[
            WORLD_MATERIAL_MODE_OPAQUE,
            WORLD_MATERIAL_MODE_CUTOUT,
            WORLD_MATERIAL_MODE_TRANSLUCENT,
            WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT,
        ],
    )
}

/// Texture matrix 0 for an entity-stream draw: identity, or vanilla's
/// animated glint matrix (`RenderStateShard.setupGlintTexturing`), which Iris
/// exposes to `gbuffers_armor_glint` as `gl_TextureMatrix[0]`.
pub(crate) fn source_glint_texture_transforms(
    foil: Option<crate::render::shared::item_foil::StandardItemFoil>,
) -> GalResult<TerrainSourceTextureTransforms> {
    let mut transforms = TerrainSourceTextureTransforms::canonical_minecraft_terrain();
    if let Some(foil) = foil {
        let [c0, c1, t] = foil.texture_transform()?;
        transforms.atlas_texture_matrix = [
            c0[0], c0[1], 0.0, 0.0, //
            c1[0], c1[1], 0.0, 0.0, //
            0.0, 0.0, 1.0, 0.0, //
            t[0], t[1], 0.0, 1.0,
        ];
    }
    Ok(transforms)
}

/// Local-textured source writers share this material blend contract. Both
/// translucent modes blend surviving pixels; translucent-cutout also discards
/// pixels below its separate source alpha threshold.
pub(crate) fn source_textured_material_blend(material_mode: u32) -> GalResult<BlendMode> {
    match material_mode {
        WORLD_MATERIAL_MODE_OPAQUE
        | WORLD_MATERIAL_MODE_CUTOUT
        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST => Ok(BlendMode::Disabled),
        // Vanilla `BlendFunction.GLINT` (SRC_COLOR, ONE / ZERO, ONE); Iris
        // keeps it for `gbuffers_armor_glint` (no pack or program override).
        WORLD_MATERIAL_MODE_GLINT => Ok(BlendMode::SrcColorAdditive),
        WORLD_MATERIAL_MODE_TRANSLUCENT | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT => {
            Ok(BlendMode::Alpha)
        }
        value => Err(GalError::unsupported_feature(format!(
            "textured material source program does not support material mode {value}"
        ))),
    }
}

/// Vanilla's `ENTITY_CUTOUT` pipeline declares `ALPHA_CUTOUT = 0.1`.  The
/// selected-source entity program exposes an explicit shader hook for this
/// semantic material decision because VulkanicGAL deliberately has no legacy
/// fixed-function alpha-test state.
/// `SkyRenderer.renderDarkDisc` as copied by the Java sky producer.
pub(crate) fn is_vanilla_dark_disc_quad(quad: &WorldMaterialQuadRequest) -> bool {
    quad.material_id == WORLD_MATERIAL_ID_SKY_DARK_DISC
}

pub(crate) fn source_entity_alpha_cutoff(material_mode: u32) -> GalResult<Option<f32>> {
    match material_mode {
        WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_TRANSLUCENT => Ok(None),
        // Iris `ShaderKey.GLINT` uses `AlphaTests.NON_ZERO_ALPHA`.
        WORLD_MATERIAL_MODE_GLINT => Ok(Some(0.0001)),
        WORLD_MATERIAL_MODE_CUTOUT
        | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST => Ok(Some(0.1)),
        value => Err(GalError::unsupported_feature(format!(
            "entity source program does not support material mode {value}"
        ))),
    }
}

pub(in crate::render::worldrender) fn source_weather_material_batches(
    frame: &WorldPrimitiveFrame,
) -> GalResult<Vec<SourceTexturedMaterialBatch>> {
    source_material_batches_for_program(
        frame,
        WORLD_MATERIAL_SOURCE_WEATHER,
        &[WORLD_MATERIAL_MODE_TRANSLUCENT],
    )
}

pub(in crate::render::worldrender) fn source_cloud_material_batches(
    frame: &WorldPrimitiveFrame,
) -> GalResult<Vec<SourceTexturedMaterialBatch>> {
    source_material_batches_for_program(
        frame,
        WORLD_MATERIAL_SOURCE_CLOUDS,
        &[WORLD_MATERIAL_MODE_TRANSLUCENT],
    )
}

pub(in crate::render::worldrender) fn source_material_batch_stream_bytes(
    program: &LoweredTexturedMaterialSourceProgram,
    batches: &[SourceTexturedMaterialBatch],
    writer: &str,
) -> GalResult<u64> {
    batches.iter().try_fold(0_u64, |total, batch| {
        let vertices = u64::try_from(batch.count)
            .ok()
            .and_then(|count| count.checked_mul(4))
            .and_then(|count| {
                count.checked_mul(u64::from(program.execution_interface.vertex_stride))
            })
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "{writer} source frame stream reservation overflows"
                ))
            })?;
        total
            .checked_add(u64::from(
                program.execution_interface.legacy_transform_bytes,
            ))
            .and_then(|value| {
                value.checked_add(u64::from(program.execution_interface.scalar_uniform_bytes))
            })
            .and_then(|value| value.checked_add(vertices))
            .and_then(|value| value.checked_add(3 * WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64))
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "{writer} source frame stream reservation overflows"
                ))
            })
    })
}

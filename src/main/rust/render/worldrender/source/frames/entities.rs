//! Source entity, hand and glint frames and decal-glint meshes.

use super::*;

/// Immutable, caller-independent entity source payload. It is deliberately
/// CPU-only: no target, pipeline, GAL handle, route selection, or native
/// state is present until a complete Rust-owned entity pass writer exists.
///
/// A record describes exactly one semantic mesh section and one compatible
/// group of producer instances. The local material identity is retained for a
/// future Rust-owned resource-set lookup; it is never a Java atlas object or
/// native texture handle.
#[derive(Clone, Debug)]
pub(crate) struct PreparedSourceEntityFrame {
    pub frame_id: u64,
    pub mesh: Arc<SourceEntityMeshAsset>,
    pub section_index: u32,
    pub texture_id: u32,
    pub material_mode: u32,
    pub depth_policy: u32,
    pub cull_policy: u32,
    pub winding: u32,
    pub entity_identity: String,
    pub entity_id: i32,
    pub entity_id_generation: u64,
    pub entity_color: [f32; 4],
    pub legacy_texture_transforms: Vec<u8>,
    pub scalar_uniforms: Vec<u8>,
    pub instance_transforms: Vec<u8>,
}

/// Immutable, caller-independent source payload for a future Rust-owned
/// first-person hand pass. It deliberately uses the same copied local-mesh
/// asset family as other textured source meshes while preserving the hand
/// boundary as an explicit semantic ordering key.
#[derive(Clone, Debug)]
pub(crate) struct PreparedSourceHandFrame {
    pub(in crate::render::worldrender) frame_id: u64,
    pub(in crate::render::worldrender) hand: FirstPersonHand,
    pub(in crate::render::worldrender) mesh: Arc<SourceEntityMeshAsset>,
    pub(in crate::render::worldrender) section_index: u32,
    pub(in crate::render::worldrender) texture_id: u32,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) legacy_texture_transforms: Vec<u8>,
    pub(in crate::render::worldrender) scalar_uniforms: Vec<u8>,
    pub(in crate::render::worldrender) instance_transforms: Vec<u8>,
}

pub(crate) type SourceTerrainInstance = ([f32; 16], u32);

impl WorldPrimitiveFrontend {
    /// Returns the immutable local-texture entity source stream for an exact
    /// asset generation. It intentionally has no fallback to terrain source
    /// preparation: selected entity programs require different material and
    /// identity semantics even though both use the same fixed owned storage
    /// record shape.
    pub(crate) fn source_entity_mesh_asset(
        &mut self,
        mesh_key: u64,
        mesh_generation: u64,
        packed_light: u32,
    ) -> GalResult<Arc<SourceEntityMeshAsset>> {
        self.ensure_source_mesh_generation("entity", mesh_key, mesh_generation)?;
        let cache_key = (mesh_key, mesh_generation, packed_light);
        if let Some(mesh) = self.source_entity_mesh_cache.get(&cache_key) {
            return Ok(mesh);
        }
        let asset = self
            .mesh_assets
            .get(&mesh_key)
            .expect("checked mesh asset exists");
        let input = asset.source_entity_view(mesh_key).ok_or_else(|| {
            GalError::unsupported_feature(format!(
                "source entity mesh {} generation {} is unavailable: world mesh has no entity source identity or compatible vertex layout",
                mesh_key, mesh_generation
            ))
        })?;
        prepare_source_entity_mesh_asset_view(&input)
            .and_then(|mut prepared| {
                if packed_light != 0 {
                    // Java's entity producer supplies UV2 as an instance
                    // semantic for models whose copied vertices do not carry
                    // a resolved light value.  The selected-source ABI keeps
                    // light in the vertex record, so materialize a bounded
                    // per-light geometry variant instead of dropping the
                    // semantic or widening the shared instance ABI.
                    override_source_entity_vertex_light(&mut prepared, packed_light)?;
                    prepared.mesh_key = source_entity_light_variant_key(mesh_key, packed_light);
                }
                Ok(Arc::new(prepared))
            })
            .map_err(|error| {
                GalError::unsupported_feature(format!(
                    "source entity mesh {} generation {} is unavailable: {}",
                    mesh_key, mesh_generation, error
                ))
            })
            .inspect(|mesh| {
                self.source_entity_mesh_cache.insert(cache_key, Arc::clone(mesh));
            })
    }

    /// Prepares the owned semantic payloads for explicit entity-mesh records.
    /// This is intentionally unavailable to route selection: it allocates no
    /// GAL resource, compiles no pipeline, and records no draw. A later
    /// Rust-owned entity writer must consume every returned payload in one
    /// named pass or leave the producer compatibility-owned.
    pub(crate) fn prepare_source_entity_frames(
        &mut self,
        program: &LoweredEntitySourceProgram,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<PreparedSourceEntityFrame>> {
        self.prepare_source_entity_frames_for(program, frame, &frame.mesh_instances, None, false)
    }

    /// Glint sections of world entity/item meshes, prepared for the pack's
    /// `gbuffers_armor_glint` program on the entity stream.
    /// Iris draws special (decal) foil through the glint program with the
    /// UVs vanilla's SheetedDecalTextureGenerator emits, not the model UVs.
    /// Derive that exact per-pose mesh once and reuse it while the pose holds.
    pub(crate) fn decal_glint_source_mesh(
        &mut self,
        mesh: Arc<SourceEntityMeshAsset>,
        decal: Option<crate::render::worldrender::features::decal_foil::WorldDecalFoilProjection>,
    ) -> GalResult<Arc<SourceEntityMeshAsset>> {
        let Some(decal) = decal else {
            return Ok(mesh);
        };
        let mut hash = 0xcbf2_9ce4_8422_2325_u64;
        let mut mix = |value: u64| {
            for byte in value.to_le_bytes() {
                hash ^= u64::from(byte);
                hash = hash.wrapping_mul(0x0000_0100_0000_01b3);
            }
        };
        mix(mesh.mesh_key);
        mix(mesh.mesh_generation);
        for value in decal.model_pose.iter().chain(decal.normal_pose.iter()) {
            mix(u64::from(value.to_bits()));
        }
        mix(u64::from(decal.first_person) | u64::from(decal.trusted_normals) << 1);
        let key = hash.max(1);
        self.source_decal_glint_used.insert(key);
        if let Some(derived) = self.source_decal_glint_meshes.get(&key) {
            return Ok(Arc::clone(derived));
        }
        let projection = decal.prepare()?;
        let mut vertex_bytes = mesh.vertex_bytes.clone();
        if vertex_bytes.len() % TERRAIN_SOURCE_VERTEX_BYTES != 0 {
            return Err(GalError::invalid_argument(
                "source decal glint mesh is not aligned to the fixed source ABI",
            ));
        }
        let read = |bytes: &[u8], offset: usize| {
            f32::from_ne_bytes([bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]])
        };
        for vertex in vertex_bytes.chunks_exact_mut(TERRAIN_SOURCE_VERTEX_BYTES) {
            let position = [read(vertex, 0), read(vertex, 4), read(vertex, 8)];
            // The record holds the exact signed-byte normal divided by 127.
            let packed_normal = (0..3).fold(0u32, |packed, lane| {
                let value = (read(vertex, 32 + lane * 4).clamp(-1.0, 1.0) * 127.0).round() as i8;
                packed | u32::from(value as u8) << (lane * 8)
            });
            let [u, v] = projection.texture_uv(position, packed_normal)?;
            vertex[48..52].copy_from_slice(&u.to_ne_bytes());
            vertex[52..56].copy_from_slice(&v.to_ne_bytes());
        }
        let derived = Arc::new(SourceEntityMeshAsset {
            mesh_key: key,
            mesh_generation: 1,
            entity_identity: mesh.entity_identity.clone(),
            vertex_bytes,
            index_bytes: mesh.index_bytes.clone(),
            sections: mesh.sections.clone(),
        });
        derived.validate()?;
        self.source_decal_glint_meshes.insert(key, Arc::clone(&derived));
        Ok(derived)
    }

    /// Drops derived decal glint meshes (and their uploaded geometry) that
    /// the previous frame no longer used; called once per source frame.
    pub(crate) fn retire_unused_decal_glint_meshes(&mut self, gal: &mut VulkanicGal) {
        let used = std::mem::take(&mut self.source_decal_glint_used);
        let stale = self
            .source_decal_glint_meshes
            .keys()
            .filter(|key| !used.contains(key))
            .copied()
            .collect::<Vec<_>>();
        if stale.is_empty() {
            return;
        }
        for key in &stale {
            self.source_decal_glint_meshes.remove(key);
        }
        self.destroy_lowered_source_terrain_resources_for_keys(
            gal,
            stale
                .into_iter()
                .map(|mesh_key| LoweredSourceTerrainDataKey {
                    mesh_key,
                    mesh_generation: 1,
                    abi: SourceGeometryAbi::LocalTextured,
                })
                .collect(),
        );
    }

    pub(crate) fn prepare_source_entity_glint_frames(
        &mut self,
        program: &LoweredEntitySourceProgram,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<PreparedSourceEntityFrame>> {
        self.prepare_source_entity_frames_for(program, frame, &frame.mesh_instances, None, true)
    }

    /// Shared entity-stream frame preparation. Shadow casters pass their own
    /// instance list and the shadow render stage; the program (entity or
    /// entity-shadow) owns the transform semantics.
    pub(crate) fn prepare_source_entity_frames_for(
        &mut self,
        program: &LoweredEntitySourceProgram,
        frame: &WorldPrimitiveFrame,
        instances: &[WorldMeshInstanceRequest],
        render_stage: Option<i32>,
        glint: bool,
    ) -> GalResult<Vec<PreparedSourceEntityFrame>> {
        program.execution_interface.validate()?;
        // Report source-entity block identity violations before validating or
        // resolving any other instance state. This keeps the source route's
        // diagnostics deterministic even when a caller mutates an otherwise
        // valid frame into an invalid block-entity identity.
        for instance in instances
            .iter()
            .filter(|instance| instance.stratum == WORLD_STRATUM_ENTITY_MESH)
        {
            if instance.block_entity_id < -1 {
                return Err(GalError::invalid_argument(
                    "source entity preparation rejects invalid block-entity IDs",
                ));
            }
        }
        let mut grouped = BTreeMap::<
            (u64, u64, u32, u32, u32, u32, u32, u32, i32, u32, Option<(u8, u64, u64, u32)>),
            (
                Arc<SourceEntityMeshAsset>,
                Vec<SourceTerrainInstance>,
                Option<crate::render::shared::item_foil::StandardItemFoil>,
            ),
        >::new();

        for instance in instances
            .iter()
            .filter(|instance| instance.stratum == WORLD_STRATUM_ENTITY_MESH)
        {
            validate_mesh_instance(instance, frame)?;
            if self.is_crumbling_mesh_instance(instance) {
                // Drawn by the damaged-block writer, as Iris does.
                continue;
            }
            // Vanilla layered overlays (armor, glint) postmultiply
            // ModelViewMat; fold that into the instance transform so the
            // pack's gbufferModelView remains the world view.
            let layered_transform =
                crate::render::shared::view_layering::apply_to_model(instance.transform, mesh_view_layering(instance))?;
            if instance.entity_id != 0 {
                return Err(GalError::invalid_argument(
                    "entity source preparation rejects Java-supplied shader-pack entity IDs",
                ));
            }
            if instance.block_entity_id < -1 {
                return Err(GalError::invalid_argument(
                    "source entity preparation rejects invalid block-entity IDs",
                ));
            }
            let mesh = self.source_entity_mesh_asset(
                instance.mesh_key,
                instance.mesh_generation,
                instance.packed_light,
            )?;
            let mesh = if glint {
                self.decal_glint_source_mesh(mesh, instance.decal_foil)?
            } else {
                mesh
            };
            // Both entity source asset constructors validate the immutable
            // (Arc-owned) asset; re-scanning its indices per instance was a
            // hot-path cost with ~1k entity draws per frame.
            let section_indices = if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                (0..mesh.sections.len())
                    .map(|index| {
                        u32::try_from(index).map_err(|_| {
                            GalError::invalid_argument("source entity section ordinal exceeds u32")
                        })
                    })
                    .collect::<GalResult<Vec<_>>>()?
            } else {
                vec![instance.mesh_section_index]
            };
            for section_index in section_indices {
                let section = mesh.sections.get(section_index as usize).ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "source entity mesh {} selects missing section {}",
                        mesh.mesh_key, section_index
                    ))
                })?;
                if matches!(
                    section.material_mode,
                    WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
                        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST
                ) {
                    return Err(GalError::unsupported_feature(
                        "ordinary source entity draws cannot consume first-person optical stencil sections",
                    ));
                }
                // Glint sections belong to the separate `gbuffers_armor_glint`
                // writer; each call prepares exactly one of the two families.
                if (section.material_mode == WORLD_MATERIAL_MODE_GLINT) != glint {
                    continue;
                }
                let foil = if glint {
                    Some(instance.item_foil.ok_or_else(|| {
                        GalError::invalid_argument(
                            "source glint section has no copied standard foil semantics",
                        )
                    })?)
                } else {
                    None
                };
                // Iris feeds glint `gl_Color = (ColorModulator.rgb,
                // ColorModulator.a * GlintAlpha)`: white with the strength in
                // alpha, independent of the base draw's colour.
                let color_argb = match foil {
                    Some(foil) => {
                        ((foil.strength.clamp(0.0, 1.0) * 255.0).round() as u32) << 24 | 0x00FF_FFFF
                    }
                    None => instance.color_argb,
                };
                let foil_key = foil.map(|foil| {
                    (
                        foil.kind as u8,
                        foil.clock_millis,
                        foil.speed.to_bits(),
                        foil.strength.to_bits(),
                    )
                });
                let cull_policy = if glint {
                    // Vanilla GLINT pipeline: culling disabled.
                    WORLD_CULL_NONE
                } else if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                    section.cull_policy
                } else {
                    instance.cull_policy
                };
                let key = (
                    mesh.mesh_key,
                    mesh.mesh_generation,
                    section_index,
                    section.texture_id,
                    section.material_mode,
                    instance.depth_policy,
                    cull_policy,
                    instance.entity_color_argb,
                    instance.block_entity_id,
                    instance.packed_light,
                    foil_key,
                );
                grouped
                    .entry(key)
                    .or_insert_with(|| (Arc::clone(&mesh), Vec::new(), foil))
                    .1
                    .push((layered_transform, color_argb));
            }
        }

        let mut prepared = Vec::with_capacity(grouped.len());
        for (
            (
                _,
                _,
                section_index,
                texture_id,
                material_mode,
                depth_policy,
                cull_policy,
                entity_color_argb,
                block_entity_id,
                _packed_light,
                _foil_key,
            ),
            (mesh, instances, foil),
        ) in grouped
        {
            let winding = mesh
                .sections
                .get(section_index as usize)
                .ok_or_else(|| {
                    GalError::backend("source entity grouping retained a missing mesh section")
                })?
                .winding;
            let semantics =
                program.resolve_draw_semantics(&mesh.entity_identity, entity_color_argb)?;
            // Enabled gameplay always needs the derived frame (celestial,
            // smoothed light, custom expressions), even without atlasSize.
            // Complementary's entity program shares the pack-global
            // `atlasSize` uniform with terrain even though this writer binds a
            // draw-local entity texture for `tex`.  Supply that global from
            // the Rust-owned block-atlas resource only when the selected
            // source actually declares it; entity-local material ownership
            // remains separate below.
            let mut uniforms =
                if frame.shader_environment.enabled
                    || render_stage.is_some()
                    || glint
                    || program
                    .scalar_uniform_requirements
                    .fields()
                    .iter()
                    .any(|requirement| {
                        requirement.semantic
                            == Some(TerrainSourceUniformSemantic::MaterialAtlasSize)
                    })
                {
                    self.source_uniform_frame_for_owned_resources(frame)?
                } else {
                    frame.source_uniform_frame()?
                };
            if program
                .scalar_uniform_requirements
                .fields()
                .iter()
                .any(|requirement| {
                    requirement.semantic == Some(TerrainSourceUniformSemantic::RenderStage)
                })
            {
                uniforms.render_stage = Some(match render_stage {
                    Some(stage) => stage,
                    None => self.source_entity_render_stage()?,
                });
            }
            uniforms.entity_id = Some(semantics.entity_id);
            uniforms.block_entity_id = Some(block_entity_id);
            uniforms.entity_color = Some(semantics.entity_color);
            if program
                .scalar_uniform_requirements
                .fields()
                .iter()
                .any(|requirement| {
                    requirement.semantic
                        == Some(TerrainSourceUniformSemantic::CurrentRenderedItemId)
                })
            {
                uniforms.current_rendered_item_id =
                    Some(self.source_entity_current_item_id(&mesh.entity_identity)?);
            }
            let legacy_texture_transforms = program
                .pack_legacy_texture_transforms(&source_glint_texture_transforms(foil)?)?;
            let scalar_uniforms = program.pack_scalar_uniforms(&uniforms)?;
            let instance_transforms = pack_source_terrain_instances(&instances)?;
            let expected_instance_bytes = instances
                .len()
                .checked_mul(program.execution_interface.instance_stride as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument("source entity instance payload length overflows")
                })?;
            if instance_transforms.len() != expected_instance_bytes {
                return Err(GalError::invalid_argument(
                    "source entity instance payload does not match the source program ABI",
                ));
            }
            prepared.push(PreparedSourceEntityFrame {
                frame_id: frame.frame_id,
                mesh,
                section_index,
                texture_id,
                material_mode,
                depth_policy,
                cull_policy,
                winding,
                entity_identity: semantics.entity_identity,
                entity_id: semantics.entity_id,
                entity_id_generation: semantics.entity_id_generation,
                entity_color: semantics.entity_color,
                legacy_texture_transforms,
                scalar_uniforms,
                instance_transforms,
            });
        }
        Ok(prepared)
    }

    /// Bridges a discovered Rust-owned entity source contract to the private
    /// CPU payload preparation above. It deliberately stops before any GAL
    /// allocation, pass target, pipeline, resource-set, command, or route
    /// decision, so ordinary Java/Iris compatibility ownership cannot be
    /// mistaken for a partially active Rust entity pass.
    pub(crate) fn prepare_candidate_source_entity_frames(
        &mut self,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<PreparedSourceEntityFrame>> {
        if !frame
            .mesh_instances
            .iter()
            .any(|instance| instance.stratum == WORLD_STRATUM_ENTITY_MESH)
        {
            return Ok(Vec::new());
        }
        let program = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "entity source preparation requires a discovered Rust shader runtime",
                )
            })?
            .prepared_lowered_entity_source_program()?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "entity source preparation requires a complete selected-source entity contract",
                )
            })?;
        self.prepare_source_entity_frames(&program, frame)
    }

    /// Prepares copied first-person mesh records for the Rust-owned hand
    /// writer. This remains CPU-only at this boundary; the combined source
    /// transaction consumes the records in their dedicated color/depth domain.
    ///
    /// The frame transport presently carries only the shared first-person
    /// matrices and mesh-local material semantics. A selected source which
    /// additionally requires item IDs or held-light uniforms is rejected here
    /// rather than inheriting stale entity values or a Java renderer binding.
    pub(crate) fn prepare_source_hand_frames(
        &mut self,
        program: &LoweredHandSourceProgram,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<PreparedSourceHandFrame>> {
        self.prepare_source_hand_frames_for(program, frame, false)
    }

    /// First-person glint sections for the pack's `gbuffers_armor_glint`,
    /// which Iris's hand pass flushes with `ShaderKey.GLINT`.
    pub(crate) fn prepare_source_hand_glint_frames(
        &mut self,
        program: &LoweredHandSourceProgram,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<Vec<PreparedSourceHandFrame>> {
        self.prepare_source_hand_frames_for(program, frame, true)
    }

    pub(crate) fn prepare_source_hand_frames_for(
        &mut self,
        program: &LoweredHandSourceProgram,
        frame: &WorldPrimitiveFrame,
        glint: bool,
    ) -> GalResult<Vec<PreparedSourceHandFrame>> {
        program.execution_interface.validate()?;
        if !frame.first_person.enabled {
            if frame.first_person_mesh_instances.is_empty() {
                return Ok(Vec::new());
            }
            return Err(GalError::invalid_argument(
                "first-person source preparation requires an enabled first-person frame",
            ));
        }
        let unsupported_uniform = program
            .scalar_uniform_requirements
            .fields()
            .iter()
            .find_map(|requirement| match requirement.semantic {
                Some(
                    TerrainSourceUniformSemantic::EntityId
                    | TerrainSourceUniformSemantic::EntityColor
                    | TerrainSourceUniformSemantic::PreviousViewMatrix
                    | TerrainSourceUniformSemantic::PreviousProjectionMatrix,
                ) => Some(requirement.field.name().to_string()),
                _ => None,
            });
        if let Some(name) = unsupported_uniform {
            return Err(GalError::unsupported_feature(format!(
                "hand source uniform {name} has no explicit first-person semantic transport"
            )));
        }

        // Validate hand identity as a frame-level contract before resolving
        // any mesh assets. A malformed hand record must not be able to publish
        // texture generations or leave a partially prepared hand batch behind.
        for instance in &frame.first_person_mesh_instances {
            if instance.block_entity_id != -1 {
                return Err(GalError::invalid_argument(
                    "first-person source preparation does not accept block-entity identity",
                ));
            }
            if instance.entity_id != 0 {
                return Err(GalError::invalid_argument(
                    "first-person source preparation rejects Java-supplied shader-pack entity IDs",
                ));
            }
        }

        // Hand layers can blend and write depth. Preserve producer order;
        // regrouping all matching assets by key can draw a transparent sleeve
        // before its opaque base arm and hide the latter through depth writes.
        let mut grouped = Vec::<(
            (FirstPersonHand, u64, u64, u32, u32, u32, u32, u32, Option<(u8, u64, u64, u32)>),
            (
                Arc<SourceEntityMeshAsset>,
                Vec<SourceTerrainInstance>,
                Option<crate::render::shared::item_foil::StandardItemFoil>,
            ),
        )>::new();
        let instance_count = frame.first_person_mesh_instances.len();
        for (instance_index, instance) in frame.first_person_mesh_instances.iter().enumerate() {
            validate_mesh_instance(instance, frame)?;
            if instance.stratum != WORLD_STRATUM_ENTITY_MESH {
                return Err(GalError::invalid_argument(
                    "first-person source preparation requires the entity-mesh semantic stratum",
                ));
            }
            if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0 {
                return Err(GalError::invalid_argument(
                    "first-person source preparation does not accept outline-only mesh instances",
                ));
            }
            if instance.block_entity_id != -1 {
                return Err(GalError::invalid_argument(
                    "first-person source preparation does not accept block-entity identity",
                ));
            }
            if instance.entity_id != 0 {
                return Err(GalError::invalid_argument(
                    "first-person source preparation rejects Java-supplied shader-pack entity IDs",
                ));
            }
            let hand = frame
                .first_person
                .hand_for_instance(instance_index, instance_count)?;
            let mesh = self.source_entity_mesh_asset(
                instance.mesh_key,
                instance.mesh_generation,
                instance.packed_light,
            )?;
            let mesh = if glint {
                self.decal_glint_source_mesh(mesh, instance.decal_foil)?
            } else {
                mesh
            };
            // Both entity source asset constructors validate the immutable
            // (Arc-owned) asset; re-scanning its indices per instance was a
            // hot-path cost with ~1k entity draws per frame.
            let section_indices = if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                (0..mesh.sections.len())
                    .map(|index| {
                        u32::try_from(index).map_err(|_| {
                            GalError::invalid_argument(
                                "first-person source section ordinal exceeds u32",
                            )
                        })
                    })
                    .collect::<GalResult<Vec<_>>>()?
            } else {
                vec![instance.mesh_section_index]
            };
            for section_index in section_indices {
                let section = mesh.sections.get(section_index as usize).ok_or_else(|| {
                    GalError::invalid_argument(format!(
                        "first-person source mesh {} selects missing section {}",
                        mesh.mesh_key, section_index
                    ))
                })?;
                if (section.material_mode == WORLD_MATERIAL_MODE_GLINT) != glint {
                    continue;
                }
                let foil = if glint {
                    Some(instance.item_foil.ok_or_else(|| {
                        GalError::invalid_argument(
                            "first-person glint section has no copied standard foil semantics",
                        )
                    })?)
                } else {
                    None
                };
                let color_argb = match foil {
                    Some(foil) => {
                        ((foil.strength.clamp(0.0, 1.0) * 255.0).round() as u32) << 24 | 0x00FF_FFFF
                    }
                    None => instance.color_argb,
                };
                let foil_key = foil.map(|foil| {
                    (
                        foil.kind as u8,
                        foil.clock_millis,
                        foil.speed.to_bits(),
                        foil.strength.to_bits(),
                    )
                });
                let cull_policy = if glint {
                    WORLD_CULL_NONE
                } else if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
                    section.cull_policy
                } else {
                    instance.cull_policy
                };
                let key = (
                    hand,
                    mesh.mesh_key,
                    mesh.mesh_generation,
                    section_index,
                    section.texture_id,
                    section.material_mode,
                    instance.depth_policy,
                    cull_policy,
                    foil_key,
                );
                // First-person layered overlays use the same ModelViewMat
                // postmultiply as world entities; keep gbufferModelView intact.
                let layered_transform = crate::render::shared::view_layering::apply_to_model(
                    instance.transform,
                    mesh_view_layering(instance),
                )?;
                if grouped.last().is_some_and(|(previous, _)| *previous == key) {
                    grouped
                        .last_mut()
                        .expect("checked last group")
                        .1
                        .1
                        .push((layered_transform, color_argb));
                } else {
                    grouped.push((
                        key,
                        (Arc::clone(&mesh), vec![(layered_transform, color_argb)], foil),
                    ));
                }
            }
        }

        let mut prepared = Vec::with_capacity(grouped.len());
        for (
            (hand, _, _, section_index, texture_id, material_mode, depth_policy, cull_policy, _),
            (mesh, instances, foil),
        ) in grouped
        {
            let winding = mesh
                .sections
                .get(section_index as usize)
                .ok_or_else(|| {
                    GalError::backend(
                        "first-person source grouping retained a missing mesh section",
                    )
                })?
                .winding;
            // The owned shader-environment builder resolves the selected
            // pack's held-item IDs and held-light policy from copied gameplay
            // inputs. Use it whenever the selected source names any of those
            // semantics. Enabled gameplay also uses derived frame semantics;
            // disabled conformance frames that name none stay usable without
            // inventing a terrain atlas dependency.
            let requires_owned_hand_uniforms = program
                .scalar_uniform_requirements
                .fields()
                .iter()
                .any(|requirement| {
                    matches!(
                        requirement.semantic,
                        Some(
                            TerrainSourceUniformSemantic::MaterialAtlasSize
                                | TerrainSourceUniformSemantic::CurrentRenderedItemId
                                | TerrainSourceUniformSemantic::HeldItemIdMain
                                | TerrainSourceUniformSemantic::HeldItemIdOffHand
                                | TerrainSourceUniformSemantic::HeldBlockLightMain
                                | TerrainSourceUniformSemantic::HeldBlockLightOffHand
                        )
                    )
                });
            let mut uniforms = if frame.shader_environment.enabled || requires_owned_hand_uniforms || glint {
                self.source_uniform_frame_for_owned_resources(frame)?
            } else {
                frame.source_uniform_frame()?
            };
            uniforms.view_matrix = Some(frame.first_person.model_view_matrix);
            uniforms.view_matrix_inverse = Some(invert_column_major_mat4(
                frame.first_person.model_view_matrix,
                "first-person source model-view",
            )?);
            // Iris retains gbufferProjection as the world projection while
            // the legacy hand clip projection changes. The latter is packed
            // separately in the hand-only transform block below.
            if program
                .scalar_uniform_requirements
                .fields()
                .iter()
                .any(|requirement| {
                    requirement.semantic
                        == Some(TerrainSourceUniformSemantic::CurrentRenderedItemId)
                })
            {
                uniforms.current_rendered_item_id =
                    Some(self.source_hand_current_item_id(frame, hand, &mesh.entity_identity)?);
            }
            if program
                .scalar_uniform_requirements
                .fields()
                .iter()
                .any(|requirement| {
                    requirement.semantic == Some(TerrainSourceUniformSemantic::RenderStage)
                })
            {
                uniforms.render_stage = Some(
                    self.source_hand_render_stage(frame.first_person.hand_is_translucent(hand))?,
                );
            }
            let legacy_texture_transforms = program.pack_legacy_texture_transforms(
                &source_glint_texture_transforms(foil)?,
                &frame.first_person.projection_matrix,
            )?;
            let scalar_uniforms = program.pack_scalar_uniforms(&uniforms)?;
            let instance_transforms = pack_source_terrain_instances(&instances)?;
            let expected_instance_bytes = instances
                .len()
                .checked_mul(program.execution_interface.instance_stride as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument(
                        "first-person source instance payload length overflows",
                    )
                })?;
            if instance_transforms.len() != expected_instance_bytes {
                return Err(GalError::invalid_argument(
                    "first-person source instance payload does not match the source program ABI",
                ));
            }
            prepared.push(PreparedSourceHandFrame {
                frame_id: frame.frame_id,
                hand,
                mesh,
                section_index,
                texture_id,
                material_mode,
                depth_policy,
                cull_policy,
                winding,
                legacy_texture_transforms,
                scalar_uniforms,
                instance_transforms,
            });
        }
        Ok(prepared)
    }
}

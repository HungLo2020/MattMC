//! Per-family source color-pass targets, hand depth and post-hand main-depth resources.

use super::*;

impl WorldPrimitiveFrontend {
    /// Releases the frame-local state staged for a source frame whose
    /// recording failed after its plan was consumed.
    pub(crate) fn discard_unrecorded_source_frame(&mut self, gal: &mut VulkanicGal, frame_id: u64) {
        self.discard_source_terrain_frame_transaction(gal, frame_id);
        if self.pending_distant_horizons_source_targets.is_some() {
            self.lod_source_targets.discard_submission(gal);
            self.discard_distant_horizons_generic_source_buffers(gal);
            self.pending_distant_horizons_source_targets = None;
            self.lod_gpu_residency.discard_submission(gal);
            self.lod_textured_gpu_residency.discard_submission(gal);
        }
        if let Some(runtime) = self.shader_runtime.as_mut() {
            runtime.discard_source_color_targets_submission(gal);
            runtime.discard_private_terrain_occupancy_submission();
        }
    }

    /// Stages the normal-terrain render target over Rust-owned, pack-named
    /// color images. The runtime owns those images and their history; this
    /// frontend cache owns only the explicit GAL target/pass paired with the
    /// current Rust-owned depth attachment. It has no route-selection or
    /// presenter authority.
    pub(crate) fn stage_source_terrain_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredTerrainSourceProgram,
        color_targets: &ShaderPackColorTargets,
        depth_texture: Handle,
        depth_view: Handle,
        clear_values: ShaderPackColorClearValues,
        phase: TerrainSourceColorPassPhase,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        if world_generation == 0 || graph_generation == 0 {
            return Err(GalError::invalid_argument(
                "normal source terrain targets require non-zero world and graph generations",
            ));
        }
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
            || color_targets.identity.extent != extent
        {
            return Err(GalError::invalid_argument(
                "normal source terrain target identity does not match its program or world frame",
            ));
        }
        let expected_phase = TerrainSourceColorPassPhase::for_program(program);
        if !phase.compatible_with_program(program) {
            return Err(GalError::invalid_argument(format!(
                "source terrain program requires {expected_phase:?} pass ordering, not {phase:?}",
            )));
        }
        let color_attachments = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before source target staging")
            })?
            .resolve_terrain_source_color_outputs(program, color_targets)?;
        let key = SourceTerrainColorPassTargetKey {
            world_generation,
            shader_pack_generation: program.shader_pack_generation,
            graph_generation,
            extent: [extent.width, extent.height, extent.depth],
            color_attachments: color_attachments
                .iter()
                .map(|attachment| SourceTerrainColorAttachmentKey {
                    output: attachment.output,
                    source_slot: attachment.source_slot,
                    texture: attachment.texture,
                    view: attachment.view,
                    format: attachment.format,
                    clear_each_frame: attachment.clear_each_frame,
                    clear_color_bits: attachment.clear_color_bits,
                })
                .collect(),
            phase,
            depth_texture,
            depth_view,
        };
        if !self.source_terrain_color_pass_targets.contains_key(&key) {
            // Bootstrap and translucent writers are a coordinated pair for
            // one named color/depth generation. Replace only an incompatible
            // member of that pair; removing every other key here would
            // destroy the opaque target while staging the translucent one.
            let stale = self
                .source_terrain_color_pass_targets
                .keys()
                .filter(|existing| {
                    existing.phase == key.phase
                        || existing.world_generation != key.world_generation
                        || existing.shader_pack_generation != key.shader_pack_generation
                        || existing.graph_generation != key.graph_generation
                        || existing.extent != key.extent
                })
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale {
                if let Some(resources) = self.source_terrain_color_pass_targets.remove(&stale_key) {
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.pass);
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.target);
                }
            }
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!(
                    "world-source-terrain.{phase:?}.world{world_generation}.pack{}.target",
                    program.shader_pack_generation,
                ),
                color_views: color_attachments
                    .iter()
                    .map(|attachment| attachment.view)
                    .collect(),
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            let pass = match gal.create_render_pass(RenderPassDesc {
                label: format!(
                    "world-source-terrain.{phase:?}.world{world_generation}.pack{}.pass",
                    program.shader_pack_generation,
                ),
                target,
                color_formats: color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect(),
                depth_format: Some(TextureFormat::Depth32Float),
            }) {
                Ok(pass) => pass,
                Err(error) => {
                    let _ = gal.destroy(target);
                    return Err(error);
                }
            };
            self.source_terrain_color_pass_targets.insert(
                key.clone(),
                SourceTerrainColorPassTargetResources {
                    targets: TerrainSourceColorPassTargets {
                        phase,
                        color_attachments,
                        clear_values,
                        depth_texture,
                        depth_view,
                        target,
                        pass,
                    },
                },
            );
        }
        Ok(&self
            .source_terrain_color_pass_targets
            .get(&key)
            .expect("normal source terrain target exists after successful staging")
            .targets)
    }

    /// Stages the distinct `gbuffers_textured` writer over the already owned
    /// shader-pack target generation. This shares only the semantic target
    /// family with terrain: program resources, vertex stream, and draw
    /// recording remain material-specific. In particular, this pass is
    /// load-only and can never stand in for the legacy final overlay path.
    pub(crate) fn stage_textured_material_source_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredTexturedMaterialSourceProgram,
        color_targets: &ShaderPackColorTargets,
        depth_texture: Handle,
        depth_view: Handle,
        clear_values: ShaderPackColorClearValues,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        self.stage_source_material_color_pass_targets(
            gal,
            world_generation,
            graph_generation,
            extent,
            program,
            color_targets,
            depth_texture,
            depth_view,
            clear_values,
            TerrainSourceColorPassPhase::TexturedMaterial,
            "textured-material",
        )
    }

    pub(crate) fn stage_weather_source_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredTexturedMaterialSourceProgram,
        color_targets: &ShaderPackColorTargets,
        depth_texture: Handle,
        depth_view: Handle,
        clear_values: ShaderPackColorClearValues,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        self.stage_source_material_color_pass_targets(
            gal,
            world_generation,
            graph_generation,
            extent,
            program,
            color_targets,
            depth_texture,
            depth_view,
            clear_values,
            TerrainSourceColorPassPhase::Weather,
            "weather",
        )
    }

    pub(crate) fn stage_cloud_source_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredTexturedMaterialSourceProgram,
        color_targets: &ShaderPackColorTargets,
        depth_texture: Handle,
        depth_view: Handle,
        clear_values: ShaderPackColorClearValues,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        self.stage_source_material_color_pass_targets(
            gal,
            world_generation,
            graph_generation,
            extent,
            program,
            color_targets,
            depth_texture,
            depth_view,
            clear_values,
            TerrainSourceColorPassPhase::Clouds,
            "clouds",
        )
    }

    /// Stages the separate `gbuffers_entities` writer over the exact named
    /// color generation already owned by the source frame. Entity output
    /// resolution stays source-contract based; it never inherits terrain
    /// attachments by position or Java/Iris framebuffer state.
    /// Load-only glint writer targets (`gbuffers_armor_glint` writes only its
    /// own named outputs). They reuse the depth attachment of the pass whose
    /// geometry the glint overlays, so the EQUAL depth test matches exactly.
    pub(crate) fn stage_glint_source_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        shader_pack_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        color_attachments: Vec<crate::render::shaderpack::resources::color_targets::TerrainSourceColorAttachment>,
        phase: TerrainSourceColorPassPhase,
        depth_texture: Handle,
        depth_view: Handle,
        depth_format: TextureFormat,
        clear_values: ShaderPackColorClearValues,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        let key = SourceTerrainColorPassTargetKey {
            world_generation,
            shader_pack_generation: shader_pack_generation,
            graph_generation,
            extent: [extent.width, extent.height, extent.depth],
            color_attachments: color_attachments
                .iter()
                .map(|attachment| SourceTerrainColorAttachmentKey {
                    output: attachment.output,
                    source_slot: attachment.source_slot,
                    texture: attachment.texture,
                    view: attachment.view,
                    format: attachment.format,
                    clear_each_frame: attachment.clear_each_frame,
                    clear_color_bits: attachment.clear_color_bits,
                })
                .collect(),
            phase,
            depth_texture,
            depth_view,
        };
        if !self.source_terrain_color_pass_targets.contains_key(&key) {
            let stale = self
                .source_terrain_color_pass_targets
                .keys()
                .filter(|existing| {
                    existing.phase == key.phase
                        || existing.world_generation != key.world_generation
                        || existing.shader_pack_generation != key.shader_pack_generation
                        || existing.graph_generation != key.graph_generation
                        || existing.extent != key.extent
                })
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale {
                if let Some(resources) = self.source_terrain_color_pass_targets.remove(&stale_key) {
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.pass);
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.target);
                }
            }
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!(
                    "world-source-{phase:?}.world{world_generation}.pack{}.target",
                    shader_pack_generation,
                ),
                color_views: color_attachments
                    .iter()
                    .map(|attachment| attachment.view)
                    .collect(),
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            let pass = match gal.create_render_pass(RenderPassDesc {
                label: format!(
                    "world-source-{phase:?}.world{world_generation}.pack{}.pass",
                    shader_pack_generation,
                ),
                target,
                color_formats: color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect(),
                depth_format: Some(depth_format),
            }) {
                Ok(pass) => pass,
                Err(error) => {
                    let _ = gal.destroy(target);
                    return Err(error);
                }
            };
            self.source_terrain_color_pass_targets.insert(
                key.clone(),
                SourceTerrainColorPassTargetResources {
                    targets: TerrainSourceColorPassTargets {
                        phase,
                        color_attachments,
                        clear_values,
                        depth_texture,
                        depth_view,
                        target,
                        pass,
                    },
                },
            );
        }
        Ok(&self
            .source_terrain_color_pass_targets
            .get(&key)
            .expect("entity source target exists after successful staging")
            .targets)
    }

    pub(crate) fn stage_entity_source_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredEntitySourceProgram,
        color_targets: &ShaderPackColorTargets,
        depth_texture: Handle,
        depth_view: Handle,
        clear_values: ShaderPackColorClearValues,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        if world_generation == 0 || graph_generation == 0 {
            return Err(GalError::invalid_argument(
                "entity source targets require non-zero world and graph generations",
            ));
        }
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
            || color_targets.identity.extent != extent
        {
            return Err(GalError::invalid_argument(
                "entity source target identity does not match its program or world frame",
            ));
        }
        let color_attachments = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| {
                GalError::backend("shader runtime vanished before entity target staging")
            })?
            .resolve_entity_source_color_outputs(program, color_targets)?;
        let key = SourceTerrainColorPassTargetKey {
            world_generation,
            shader_pack_generation: program.shader_pack_generation,
            graph_generation,
            extent: [extent.width, extent.height, extent.depth],
            color_attachments: color_attachments
                .iter()
                .map(|attachment| SourceTerrainColorAttachmentKey {
                    output: attachment.output,
                    source_slot: attachment.source_slot,
                    texture: attachment.texture,
                    view: attachment.view,
                    format: attachment.format,
                    clear_each_frame: attachment.clear_each_frame,
                    clear_color_bits: attachment.clear_color_bits,
                })
                .collect(),
            phase: TerrainSourceColorPassPhase::Entities,
            depth_texture,
            depth_view,
        };
        if !self.source_terrain_color_pass_targets.contains_key(&key) {
            let stale = self
                .source_terrain_color_pass_targets
                .keys()
                .filter(|existing| {
                    existing.phase == key.phase
                        || existing.world_generation != key.world_generation
                        || existing.shader_pack_generation != key.shader_pack_generation
                        || existing.graph_generation != key.graph_generation
                        || existing.extent != key.extent
                })
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale {
                if let Some(resources) = self.source_terrain_color_pass_targets.remove(&stale_key) {
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.pass);
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.target);
                }
            }
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!(
                    "world-source-entities.world{world_generation}.pack{}.target",
                    program.shader_pack_generation,
                ),
                color_views: color_attachments
                    .iter()
                    .map(|attachment| attachment.view)
                    .collect(),
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            let pass = match gal.create_render_pass(RenderPassDesc {
                label: format!(
                    "world-source-entities.world{world_generation}.pack{}.pass",
                    program.shader_pack_generation,
                ),
                target,
                color_formats: color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect(),
                depth_format: Some(TextureFormat::Depth32Float),
            }) {
                Ok(pass) => pass,
                Err(error) => {
                    let _ = gal.destroy(target);
                    return Err(error);
                }
            };
            self.source_terrain_color_pass_targets.insert(
                key.clone(),
                SourceTerrainColorPassTargetResources {
                    targets: TerrainSourceColorPassTargets {
                        phase: TerrainSourceColorPassPhase::Entities,
                        color_attachments,
                        clear_values,
                        depth_texture,
                        depth_view,
                        target,
                        pass,
                    },
                },
            );
        }
        Ok(&self
            .source_terrain_color_pass_targets
            .get(&key)
            .expect("entity source target exists after successful staging")
            .targets)
    }

    pub(crate) fn stage_hand_source_depth_attachment(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        shader_pack_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        format: TextureFormat,
    ) -> GalResult<(Handle, Handle)> {
        let key = HandSourceDepthKey {
            world_generation,
            shader_pack_generation,
            graph_generation,
            extent: [extent.width, extent.height, extent.depth],
            format,
        };
        if !self.hand_source_depth_targets.contains_key(&key) {
            // A hand target references its depth attachment. Retire every
            // incompatible hand wrapper before its depth view goes away; all
            // other source writers retain their shared world-depth wrappers.
            let stale_targets = self
                .source_terrain_color_pass_targets
                .keys()
                .filter(|existing| existing.phase == TerrainSourceColorPassPhase::Hands)
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale_targets {
                if let Some(resources) = self.source_terrain_color_pass_targets.remove(&stale_key) {
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.pass);
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.target);
                }
            }
            let stale_depth = std::mem::take(&mut self.hand_source_depth_targets);
            for (_, resources) in stale_depth {
                resources.destroy(gal);
            }

            let texture = gal.create_texture(TextureDesc {
                label: format!(
                    "world-source-hands.world{world_generation}.pack{shader_pack_generation}.depth"
                ),
                dimension: TextureDimension::D2,
                // Ordinary hands expose depthtex0 to later pack stages via a
                // sampled Depth32 view. Optical stencil roles retain D24S8.
                format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::DepthStencilAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                    TextureUsage::TransferDst,
                ],
            })?;
            let view = match gal.create_texture_view(TextureViewDesc {
                label: format!(
                    "world-source-hands.world{world_generation}.pack{shader_pack_generation}.depth-view"
                ),
                texture,
                format,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            }) {
                Ok(view) => view,
                Err(error) => {
                    let _ = gal.destroy(texture);
                    return Err(error);
                }
            };
            self.hand_source_depth_targets.insert(
                key,
                HandSourceDepthResources {
                    texture,
                    view,
                    sampled_sampler: None,
                    combined_sampler: None,
                },
            );
        }
        let resources = self
            .hand_source_depth_targets
            .get(&key)
            .expect("hand source depth exists after successful staging");
        Ok((resources.texture, resources.view))
    }

    /// After the hand writer, Iris's depthtex0 names the cleared hand depth
    /// domain. Keep world-depth samplers intact for earlier writers and bind
    /// this exact Rust-owned view only to later fullscreen consumers.
    pub(crate) fn stage_post_hand_main_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
        hand_targets: &TerrainSourceColorPassTargets,
        graph_generation: u64,
        sampler: Handle,
        world_depth_resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<TerrainSourceOwnedResourceSet> {
        let role = TerrainSourceResourceRole::MainDepth;
        if world_depth_resources
            .availability()
            .resource_for(role.clone())
            .is_none()
        {
            return Ok(world_depth_resources.clone());
        }
        let key = self
            .hand_source_depth_targets
            .iter()
            .find_map(|(key, resources)| (resources.view == hand_targets.depth_view).then_some(*key))
            .ok_or_else(|| GalError::backend("post-hand main depth has no owned hand view"))?;
        if key.graph_generation != graph_generation
            || key.world_generation != world_depth_resources.availability().world_generation()
            || key.shader_pack_generation
                != world_depth_resources.availability().shader_pack_generation()
        {
            return Err(GalError::invalid_argument(
                "post-hand main depth and world depth roles do not share a graph generation",
            ));
        }
        let resources = self.hand_source_depth_targets.get_mut(&key).ok_or_else(|| {
            GalError::backend("post-hand main depth has no generation-matched hand attachment")
        })?;
        if resources.view != hand_targets.depth_view {
            return Err(GalError::invalid_argument(
                "post-hand main depth view differs from the hand writer's depth attachment",
            ));
        }
        if key.format != TextureFormat::Depth32Float {
            // D24S8 currently has no depth-only sampled view in GAL. Preserve
            // the stencil-capable optical path without binding its combined
            // depth/stencil view as a sampled texture.
            return Ok(world_depth_resources.clone());
        }
        if let Some(existing_sampler) = resources.sampled_sampler {
            if existing_sampler != sampler {
                return Err(GalError::invalid_argument(
                    "post-hand main depth sampler changed within one graph generation",
                ));
            }
        } else {
            let combined_sampler = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!(
                    "shader-pack.source-post-hand-main-depth.pack{}.world{}.graph{}",
                    key.shader_pack_generation, key.world_generation, key.graph_generation,
                ),
                texture_view: resources.view,
                sampler,
            })?;
            resources.sampled_sampler = Some(sampler);
            resources.combined_sampler = Some(combined_sampler);
        }
        let availability = TerrainSourceResourceAvailabilitySet::new(
            key.shader_pack_generation,
            key.world_generation,
            [TerrainSourceResourceAvailability {
                role: role.clone(),
                shape: role.expected_sampled_resource_shape(),
                resource_generation: key.graph_generation,
            }],
        )?;
        let hand_depth = TerrainSourceOwnedResourceSet::new(
            availability,
            [TerrainSourceOwnedResource {
                role: role.clone(),
                combined_sampler: resources.combined_sampler.expect("created or cached above"),
            }],
        )?;
        let other_depth_roles = world_depth_resources.excluding_roles([role])?;
        TerrainSourceOwnedResourceSet::merge([&other_depth_roles, &hand_depth])
    }

    /// Stages the separately lowered `gbuffers_hand` pass over the same
    /// Rust-owned named color generation. Unlike the entity pass, the hand
    /// writer owns a fresh depth domain; its explicit `Hands` phase causes a
    /// later recorder to clear depth while loading world color. No Java/Iris
    /// framebuffer, program, or attachment identity is consulted here.
    pub(crate) fn stage_hand_source_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredHandSourceProgram,
        color_targets: &ShaderPackColorTargets,
        clear_values: ShaderPackColorClearValues,
        depth_format: TextureFormat,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        if world_generation == 0 || graph_generation == 0 {
            return Err(GalError::invalid_argument(
                "hand source targets require non-zero world and graph generations",
            ));
        }
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
            || color_targets.identity.extent != extent
        {
            return Err(GalError::invalid_argument(
                "hand source target identity does not match its program or world frame",
            ));
        }
        let color_attachments = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| GalError::backend("shader runtime vanished before hand target staging"))?
            .resolve_hand_source_color_outputs(program, color_targets)?;
        let (depth_texture, depth_view) = self.stage_hand_source_depth_attachment(
            gal,
            world_generation,
            program.shader_pack_generation,
            graph_generation,
            extent,
            depth_format,
        )?;
        let key = SourceTerrainColorPassTargetKey {
            world_generation,
            shader_pack_generation: program.shader_pack_generation,
            graph_generation,
            extent: [extent.width, extent.height, extent.depth],
            color_attachments: color_attachments
                .iter()
                .map(|attachment| SourceTerrainColorAttachmentKey {
                    output: attachment.output,
                    source_slot: attachment.source_slot,
                    texture: attachment.texture,
                    view: attachment.view,
                    format: attachment.format,
                    clear_each_frame: attachment.clear_each_frame,
                    clear_color_bits: attachment.clear_color_bits,
                })
                .collect(),
            phase: TerrainSourceColorPassPhase::Hands,
            depth_texture,
            depth_view,
        };
        if !self.source_terrain_color_pass_targets.contains_key(&key) {
            let stale = self
                .source_terrain_color_pass_targets
                .keys()
                .filter(|existing| {
                    existing.phase == key.phase
                        || existing.world_generation != key.world_generation
                        || existing.shader_pack_generation != key.shader_pack_generation
                        || existing.graph_generation != key.graph_generation
                        || existing.extent != key.extent
                })
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale {
                if let Some(resources) = self.source_terrain_color_pass_targets.remove(&stale_key) {
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.pass);
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.target);
                }
            }
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!(
                    "world-source-hands.world{world_generation}.pack{}.target",
                    program.shader_pack_generation,
                ),
                color_views: color_attachments
                    .iter()
                    .map(|attachment| attachment.view)
                    .collect(),
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            let pass = match gal.create_render_pass(RenderPassDesc {
                label: format!(
                    "world-source-hands.world{world_generation}.pack{}.pass",
                    program.shader_pack_generation,
                ),
                target,
                color_formats: color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect(),
                        depth_format: Some(depth_format),
            }) {
                Ok(pass) => pass,
                Err(error) => {
                    let _ = gal.destroy(target);
                    return Err(error);
                }
            };
            self.source_terrain_color_pass_targets.insert(
                key.clone(),
                SourceTerrainColorPassTargetResources {
                    targets: TerrainSourceColorPassTargets {
                        phase: TerrainSourceColorPassPhase::Hands,
                        color_attachments,
                        clear_values,
                        depth_texture,
                        depth_view,
                        target,
                        pass,
                    },
                },
            );
        }
        Ok(&self
            .source_terrain_color_pass_targets
            .get(&key)
            .expect("hand source target exists after successful staging")
            .targets)
    }

    pub(crate) fn stage_source_material_color_pass_targets(
        &mut self,
        gal: &mut VulkanicGal,
        world_generation: u64,
        graph_generation: u64,
        extent: Extent3d,
        program: &LoweredTexturedMaterialSourceProgram,
        color_targets: &ShaderPackColorTargets,
        depth_texture: Handle,
        depth_view: Handle,
        clear_values: ShaderPackColorClearValues,
        phase: TerrainSourceColorPassPhase,
        writer: &str,
    ) -> GalResult<&TerrainSourceColorPassTargets> {
        if world_generation == 0 || graph_generation == 0 {
            return Err(GalError::invalid_argument(format!(
                "{writer} source targets require non-zero world and graph generations"
            )));
        }
        if color_targets.identity.world_generation != world_generation
            || color_targets.identity.shader_pack_generation != program.shader_pack_generation
            || color_targets.identity.extent != extent
        {
            return Err(GalError::invalid_argument(format!(
                "{writer} source target identity does not match its program or world frame"
            )));
        }
        let color_attachments = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| {
                GalError::backend(format!(
                    "shader runtime vanished before {writer} target staging"
                ))
            })?
            .resolve_textured_material_source_color_outputs(program, color_targets)?;
        let key = SourceTerrainColorPassTargetKey {
            world_generation,
            shader_pack_generation: program.shader_pack_generation,
            graph_generation,
            extent: [extent.width, extent.height, extent.depth],
            color_attachments: color_attachments
                .iter()
                .map(|attachment| SourceTerrainColorAttachmentKey {
                    output: attachment.output,
                    source_slot: attachment.source_slot,
                    texture: attachment.texture,
                    view: attachment.view,
                    format: attachment.format,
                    clear_each_frame: attachment.clear_each_frame,
                    clear_color_bits: attachment.clear_color_bits,
                })
                .collect(),
            phase,
            depth_texture,
            depth_view,
        };
        if !self.source_terrain_color_pass_targets.contains_key(&key) {
            let stale = self
                .source_terrain_color_pass_targets
                .keys()
                .filter(|existing| {
                    existing.phase == key.phase
                        || existing.world_generation != key.world_generation
                        || existing.shader_pack_generation != key.shader_pack_generation
                        || existing.graph_generation != key.graph_generation
                        || existing.extent != key.extent
                })
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale {
                if let Some(resources) = self.source_terrain_color_pass_targets.remove(&stale_key) {
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.pass);
                    self.deferred_mesh_resource_destroys
                        .push(resources.targets.target);
                }
            }
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!(
                    "world-source-{writer}.world{world_generation}.pack{}.target",
                    program.shader_pack_generation,
                ),
                color_views: color_attachments
                    .iter()
                    .map(|attachment| attachment.view)
                    .collect(),
                depth_stencil_view: Some(depth_view),
                extent,
            })?;
            let pass = match gal.create_render_pass(RenderPassDesc {
                label: format!(
                    "world-source-{writer}.world{world_generation}.pack{}.pass",
                    program.shader_pack_generation,
                ),
                target,
                color_formats: color_attachments
                    .iter()
                    .map(|attachment| attachment.format)
                    .collect(),
                depth_format: Some(TextureFormat::Depth32Float),
            }) {
                Ok(pass) => pass,
                Err(error) => {
                    let _ = gal.destroy(target);
                    return Err(error);
                }
            };
            self.source_terrain_color_pass_targets.insert(
                key.clone(),
                SourceTerrainColorPassTargetResources {
                    targets: TerrainSourceColorPassTargets {
                        phase,
                        color_attachments,
                        clear_values,
                        depth_texture,
                        depth_view,
                        target,
                        pass,
                    },
                },
            );
        }
        Ok(&self
            .source_terrain_color_pass_targets
            .get(&key)
            .expect("textured material source target exists after successful staging")
            .targets)
    }

    pub(crate) fn destroy_source_terrain_color_pass_targets(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.source_terrain_color_pass_targets);
        for (_, resources) in resources {
            self.deferred_mesh_resource_destroys
                .push(resources.targets.pass);
            self.deferred_mesh_resource_destroys
                .push(resources.targets.target);
        }
        let hand_depth = std::mem::take(&mut self.hand_source_depth_targets);
        for (_, resources) in hand_depth {
            resources.destroy(gal);
        }
    }
}

//! Recording of the terrain material graph: shadow, G-buffer, deferred, translucent, composites.

use super::*;

impl ShaderPackRuntimeExecutor {
    pub(crate) fn append_terrain_material_graph(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        frame: TerrainRuntimeFrame,
        draws: &[TerrainMeshDraw],
        forward_material_draws: &[TerrainForwardMaterialDraw],
    ) -> GalResult<()> {
        self.validate_terrain_material_graph()?;
        // A sky/hand-only entry still needs cleared shadow attachments and
        // submitted depth snapshots before source admission. Empty geometry
        // does not make the graph's sampled resources or forward work optional.
        let mut targets = targets;
        targets.translucent_capture_initialized = frame.translucent_capture_initialized;
        let isolation = TerrainGraphIsolation::from_env();
        let effective_draws_storage;
        let effective_draws = if isolation == TerrainGraphIsolation::FullDrawsSkipped {
            effective_draws_storage = Vec::new();
            effective_draws_storage.as_slice()
        } else {
            draws
        };

        if matches!(
            isolation,
            TerrainGraphIsolation::Full
                | TerrainGraphIsolation::TerrainPlusShadow
                | TerrainGraphIsolation::FullDrawsSkipped
        ) {
            self.append_shadow_depth_pass(
                ops,
                targets.into(),
                effective_draws,
                &[],
                frame.shadow_targets_initialized,
                false,
            )?;
        } else if isolation == TerrainGraphIsolation::GBufferNoShadow {
            // The deferred graph still samples the explicit shadow-depth
            // attachment even when this diagnostic graph excludes shadow
            // geometry.  Initialize it through the same owned pass contract
            // with an empty draw list; leaving it Undefined would violate
            // Vulkan's sampled-image layout requirement.
            self.append_shadow_depth_pass(
                ops,
                targets.into(),
                &[],
                &[],
                frame.shadow_targets_initialized,
                false,
            )?;
        }
        self.append_g_buffer_passes(
            ops,
            targets,
            frame.background_color,
            effective_draws,
            isolation == TerrainGraphIsolation::FullDrawsSkipped,
            frame.screen_targets_initialized,
            frame.g_buffer_background_initialized,
        )?;
        if let Some(history) = frame.depth_history {
            Self::append_main_depth_history(ops, targets.depth_history, history)?;
        }
        if matches!(
            isolation,
            TerrainGraphIsolation::Full
                | TerrainGraphIsolation::GBufferNoShadow
                | TerrainGraphIsolation::FullDrawsSkipped
        ) {
            self.append_deferred_and_composites(
                ops,
                targets,
                frame,
                effective_draws,
                forward_material_draws,
            )?;
        }
        Ok(())
    }

    /// Retains explicit main-depth snapshots at the boundary immediately
    /// after opaque/cutout G-buffer work and before the translucent pass.
    /// This is a semantic texture-to-texture operation: GAL validates the
    /// transfer usages and both backends lower the same command privately.
    pub(crate) fn append_main_depth_history(
        ops: &mut Vec<CommandOp>,
        targets: TerrainDepthHistoryTargets,
        history: TerrainDepthHistoryPlan,
    ) -> GalResult<()> {
        if matches!(
            crate::core::environment::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            crate::core::console::stderr(format_args!(
                "[MattMC source-depth-trace] history-copy main_texture=0x{:016x} before_texture=0x{:016x} previous_texture=0x{:016x}",
                targets.main_depth_texture.raw(),
                targets.before_translucency_texture.raw(),
                targets.previous_texture.raw(),
            ));
        }
        if history.extent.width == 0 || history.extent.height == 0 || history.extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "main depth history requires a non-zero 2D extent",
            ));
        }
        let full_copy = |src_texture, dst_texture| TextureImageCopyRegion {
            row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
            src_texture,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: history.extent,
        };

        if history.prior_before_translucency_valid {
            ops.push(CommandOp::Barrier(texture_barrier(
                targets.before_translucency_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferSrc,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                targets.previous_texture,
                if history.prior_previous_valid {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::CopyTexture(full_copy(
                targets.before_translucency_texture,
                targets.previous_texture,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                targets.previous_texture,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                targets.before_translucency_texture,
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            )));
        }

        ops.push(CommandOp::Barrier(texture_barrier(
            targets.main_depth_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.before_translucency_texture,
            if history.prior_before_translucency_valid {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            },
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::CopyTexture(full_copy(
            targets.main_depth_texture,
            targets.before_translucency_texture,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.before_translucency_texture,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.main_depth_texture,
            TextureUsageState::TransferSrc,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    /// Captures one current-frame selected-source depth boundary. The
    /// destination is replaced in this submission before any consumer reads
    /// it; it is never interpreted as a previous-frame history image.
    pub(crate) fn append_source_main_depth_snapshot(
        ops: &mut Vec<CommandOp>,
        source: Handle,
        destination: Handle,
        extent: Extent3d,
    ) -> GalResult<()> {
        if source == destination || extent.width == 0 || extent.height == 0 || extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "source main depth snapshot requires distinct full-size 2D textures",
            ));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            source,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            destination,
            TextureUsageState::Undefined,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
            row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
            src_texture: source,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: destination,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }));
        ops.push(CommandOp::Barrier(texture_barrier(
            destination,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            source,
            TextureUsageState::TransferSrc,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    /// Exercises one validated source-derived G-buffer transaction without
    /// selecting a gameplay route or claiming the incomplete source shadow
    /// and composite passes. This is intentionally test-only: production
    /// callers must enter through the complete runtime graph.
    #[cfg(test)]
    pub(crate) fn append_terrain_g_buffer_for_test(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        background_color: ClearColor,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        self.validate_terrain_material_graph()?;
        self.append_g_buffer_passes(ops, targets, background_color, draws, false, false, false)
    }

    /// Records the lowered source shadow programs into their explicit owned
    /// shadow attachments. The caller still owns source-frame ordering,
    /// color-history completion, fullscreen consumers, and route admission.
    /// This reusable boundary is shared by ordinary terrain and later DH
    /// source work without inheriting the rest of the fixture pass graph.
    pub(crate) fn append_terrain_source_shadow_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainSourceShadowPassTargets,
        draws: &[TerrainMeshDraw],
        shadow_only_draws: &[TerrainShadowMeshDraw],
        entity_shadow_draws: &[EntitySourceDraw],
    ) -> GalResult<()> {
        self.append_shadow_depth_pass_with_entities(
            ops,
            targets,
            draws,
            shadow_only_draws,
            entity_shadow_draws,
            targets.initialized,
            true,
        )
    }

    pub(super) fn validate_terrain_material_graph(&self) -> GalResult<()> {
        let passes = self
            .plan
            .graph
            .passes()
            .iter()
            .map(|pass| pass.identity.as_str())
            .collect::<Vec<_>>();
        let expected = [
            "vulkanic:pass/shadow_depth",
            "vulkanic:pass/terrain_opaque",
            "vulkanic:pass/terrain_cutout",
            "vulkanic:pass/deferred_lighting",
            "vulkanic:pass/terrain_translucent",
            "vulkanic:pass/composite_0",
            "vulkanic:pass/composite_1",
            "vulkanic:pass/final_output",
        ];
        if passes != expected {
            return Err(GalError::invalid_argument(format!(
                "terrain material runtime graph has unexpected pass order: {:?}",
                passes
            )));
        }
        Ok(())
    }

    pub(super) fn append_shadow_depth_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainSourceShadowPassTargets,
        draws: &[TerrainMeshDraw],
        shadow_only_draws: &[TerrainShadowMeshDraw],
        targets_initialized: bool,
        include_translucent: bool,
    ) -> GalResult<()> {
        self.append_shadow_depth_pass_with_entities(
            ops,
            targets,
            draws,
            shadow_only_draws,
            &[],
            targets_initialized,
            include_translucent,
        )
    }

    pub(super) fn append_shadow_depth_pass_with_entities(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainSourceShadowPassTargets,
        draws: &[TerrainMeshDraw],
        shadow_only_draws: &[TerrainShadowMeshDraw],
        entity_shadow_draws: &[EntitySourceDraw],
        targets_initialized: bool,
        include_translucent: bool,
    ) -> GalResult<()> {
        let pass = self.pass_identity(AttachmentRole::ShadowDepth)?;
        let attachment_before = if targets_initialized {
            TextureUsageState::ShaderRead
        } else {
            TextureUsageState::Undefined
        };
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.shadow_depth_texture,
            attachment_before,
            TextureUsageState::DepthStencilAttachment,
        )));
        for texture in [
            targets.shadow_color_texture,
            targets.shadow_light_shaft_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                attachment_before,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::BeginPass {
            pass: targets.shadow_pass,
            target: targets.shadow_target,
            colors: vec![
                PassAttachment {
                    view: targets.shadow_color_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        // Iris shadowcolor sampling defaults to opaque white
                        // unless the pack supplies a clear-color directive.
                        r: 1.0,
                        g: 1.0,
                        b: 1.0,
                        a: 1.0,
                    }),
                },
                PassAttachment {
                    view: targets.shadow_light_shaft_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        r: 1.0,
                        g: 1.0,
                        b: 1.0,
                        a: 1.0,
                    }),
                },
            ],
            depth_stencil: Some(PassAttachment {
                view: targets.shadow_depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = IndexedDrawState::default();
        // Frozen's shadow scene renders solid/cutout terrain before the
        // translucent terrain group. Preserve that phase order even when the
        // semantic batch list arrives in a different color-pass order.
        for translucent_phase in [false, true] {
            if translucent_phase && targets.shadow_depth_opaque_texture != Handle::NULL {
                // Iris copies shadowtex0 into shadowtex1 between the opaque and
                // translucent shadow casters; resume the same targets with load.
                ops.push(CommandOp::EndPass);
                ops.push(CommandOp::Barrier(texture_barrier(
                    targets.shadow_depth_texture,
                    TextureUsageState::DepthStencilAttachment,
                    TextureUsageState::ShaderRead,
                )));
                Self::append_source_main_depth_snapshot(
                    ops,
                    targets.shadow_depth_texture,
                    targets.shadow_depth_opaque_texture,
                    targets.shadow_extent,
                )?;
                ops.push(CommandOp::Barrier(texture_barrier(
                    targets.shadow_depth_texture,
                    TextureUsageState::ShaderRead,
                    TextureUsageState::DepthStencilAttachment,
                )));
                let load = |view| PassAttachment {
                    view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                };
                ops.push(CommandOp::BeginPass {
                    pass: targets.shadow_pass,
                    target: targets.shadow_target,
                    colors: vec![
                        load(targets.shadow_color_view),
                        load(targets.shadow_light_shaft_view),
                    ],
                    depth_stencil: Some(load(targets.shadow_depth_view)),
                });
                draw_state = IndexedDrawState::default();
            }
            for draw in draws.iter().filter(|draw| {
                draw.shadow_participation == TerrainShadowParticipation::Required
                    && (draw.material_mode == TerrainMaterialPassMode::Translucent)
                        == translucent_phase
                    && (include_translucent || !translucent_phase)
            }) {
                let shadow = draw.shadow.as_ref().ok_or_else(|| {
                    GalError::backend(format!(
                        "{} mesh draw missing shadow pipeline (stratum {} material {:?} indices {} instances {})",
                        pass.as_str(),
                        draw.stratum,
                        draw.material_mode,
                        draw.index_count,
                        draw.instance_count
                    ))
                })?;
                append_indexed_draw(
                    ops,
                    &mut draw_state,
                    shadow.pipeline,
                    shadow.pipeline_layout,
                    shadow.resource_set,
                    &shadow.resource_set_dynamic_offsets,
                    shadow.shader_resource_set,
                    draw.index_buffer,
                    draw.index_offset,
                    draw.index_type,
                    draw.index_count,
                    draw.instance_count,
                    draw.indexed_indirect,
                );
            }
            if !translucent_phase {
                // Iris renders entity shadow casters after solid/cutout terrain.
                for draw in entity_shadow_draws {
                    append_indexed_draw(
                        ops,
                        &mut draw_state,
                        draw.pipeline,
                        draw.pipeline_layout,
                        draw.resource_set,
                        &draw.resource_set_dynamic_offsets,
                        Some(draw.shader_resource_set),
                        draw.index_buffer,
                        draw.index_offset,
                        draw.index_type,
                        draw.index_count,
                        draw.instance_count,
                        None,
                    );
                }
            }
            if include_translucent || !translucent_phase {
                for draw in shadow_only_draws.iter().filter(|draw| {
                    (draw.material_mode == TerrainMaterialPassMode::Translucent)
                        == translucent_phase
                }) {
                    append_indexed_draw(
                        ops,
                        &mut draw_state,
                        draw.shadow.pipeline,
                        draw.shadow.pipeline_layout,
                        draw.shadow.resource_set,
                        &draw.shadow.resource_set_dynamic_offsets,
                        draw.shadow.shader_resource_set,
                        draw.index_buffer,
                        draw.index_offset,
                        draw.index_type,
                        draw.index_count,
                        draw.instance_count,
                        draw.indexed_indirect,
                    );
                }
            }
        }
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.shadow_depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        for texture in [
            targets.shadow_color_texture,
            targets.shadow_light_shaft_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        Ok(())
    }

    pub(super) fn append_g_buffer_passes(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        background_color: ClearColor,
        draws: &[TerrainMeshDraw],
        force_empty_clear: bool,
        targets_initialized: bool,
        background_initialized: bool,
    ) -> GalResult<()> {
        let attachment_before = if targets_initialized || background_initialized {
            TextureUsageState::ShaderRead
        } else {
            TextureUsageState::Undefined
        };
        for texture in [
            targets.albedo_texture,
            targets.normal_texture,
            targets.material_light_texture,
            targets.world_position_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                attachment_before,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            attachment_before,
            TextureUsageState::DepthStencilAttachment,
        )));

        let mut wrote_g_buffer = false;
        for mode in [
            TerrainMaterialPassMode::Opaque,
            TerrainMaterialPassMode::Cutout,
        ] {
            let has_mode_draws = draws.iter().any(|draw| draw.material_mode == mode);
            if !has_mode_draws && !force_empty_clear {
                continue;
            }
            let load_op = if wrote_g_buffer || background_initialized {
                AttachmentLoadOp::Load
            } else {
                AttachmentLoadOp::Clear
            };
            ops.push(CommandOp::BeginPass {
                pass: targets.g_buffer_pass,
                target: targets.target,
                colors: vec![
                    PassAttachment {
                        view: targets.albedo_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(background_color),
                    },
                    PassAttachment {
                        view: targets.normal_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.5,
                            g: 0.5,
                            b: 1.0,
                            a: 1.0,
                        }),
                    },
                    PassAttachment {
                        view: targets.material_light_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.0,
                            g: 1.0,
                            b: 1.0,
                            a: 0.0,
                        }),
                    },
                    PassAttachment {
                        view: targets.world_position_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.5,
                            g: 0.5,
                            b: 0.5,
                            a: 0.0,
                        }),
                    },
                ],
                depth_stencil: Some(PassAttachment {
                    view: targets.depth_view,
                    load_op,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            let mut draw_state = IndexedDrawState::default();
            for draw in draws.iter().filter(|draw| draw.material_mode == mode) {
                append_indexed_draw(
                    ops,
                    &mut draw_state,
                    draw.pipeline,
                    draw.pipeline_layout,
                    draw.resource_set,
                    &draw.resource_set_dynamic_offsets,
                    draw.shader_resource_set,
                    draw.index_buffer,
                    draw.index_offset,
                    draw.index_type,
                    draw.index_count,
                    draw.instance_count,
                    draw.indexed_indirect,
                );
            }
            ops.push(CommandOp::EndPass);
            wrote_g_buffer = true;
        }

        for texture in [
            targets.albedo_texture,
            targets.normal_texture,
            targets.material_light_texture,
            targets.world_position_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    pub(super) fn append_deferred_and_composites(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        frame: TerrainRuntimeFrame,
        draws: &[TerrainMeshDraw],
        forward_material_draws: &[TerrainForwardMaterialDraw],
    ) -> GalResult<()> {
        let screen_texture_before = screen_texture_before(frame.screen_targets_initialized);
        ops.push(CommandOp::Barrier(buffer_barrier(
            targets.composite_uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::HostWriteBuffer {
            buffer: targets.composite_uniform_buffer,
            offset: 0,
            data: frame.uniforms.pack(),
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            targets.composite_uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));

        self.append_screen_pass(
            ops,
            targets.deferred_lit_texture,
            targets.deferred_lighting_pass,
            targets.deferred_lit_target,
            targets.deferred_lit_view,
            targets.deferred_lighting_pipeline,
            targets.deferred_lighting_resource_set,
            targets.screen_pipeline_layout,
            transparent_clear(frame.background_color),
            screen_texture_before,
        );
        self.append_translucent_pass(
            ops,
            targets,
            draws,
            frame.translucent_entity_external,
            frame.translucent_terrain_external,
        )?;
        self.append_forward_material_pass(ops, targets, forward_material_draws)?;
        self.append_screen_pass(
            ops,
            targets.composite0_texture,
            targets.composite0_pass,
            targets.composite0_target,
            targets.composite0_view,
            targets.composite0_pipeline,
            targets.composite0_resource_set,
            targets.screen_pipeline_layout,
            transparent_clear(frame.background_color),
            screen_texture_before,
        );
        self.append_screen_pass(
            ops,
            targets.composite1_texture,
            targets.composite1_pass,
            targets.composite1_target,
            targets.composite1_view,
            targets.composite1_pipeline,
            targets.composite1_resource_set,
            targets.screen_pipeline_layout,
            transparent_clear(frame.background_color),
            screen_texture_before,
        );

        ops.push(CommandOp::BeginPass {
            pass: targets.final_pass,
            target: frame.frame_target,
            colors: vec![PassAttachment {
                view: frame.color_attachment,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            // Final fullscreen stages sample the main depth texture through
            // their resource set; they do not perform depth testing. Keeping
            // it attached here creates an illegal sampled+depth-attachment
            // feedback layout on Vulkan.
            depth_stencil: None,
        });
        ops.push(CommandOp::BindGraphicsPipeline(targets.final_pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: targets.screen_pipeline_layout,
            set_index: 0,
            set: targets.final_resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        Ok(())
    }

    pub(super) fn append_translucent_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        draws: &[TerrainMeshDraw],
        translucent_entity_external: bool,
        translucent_terrain_external: bool,
    ) -> GalResult<()> {
        if !draws.iter().any(|draw| {
            draw.material_mode == TerrainMaterialPassMode::Translucent
                && !Self::translucent_draw_is_external(draw, translucent_entity_external, false)
        }) {
            return Ok(());
        }
        if let Some(capture) = targets.translucent_capture {
            ops.push(CommandOp::Barrier(texture_barrier(
                capture.color_texture,
                if targets.translucent_capture_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::ColorAttachment,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                targets.depth_texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::TransferSrc,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                capture.depth_texture,
                if targets.translucent_capture_initialized {
                    TextureUsageState::ShaderRead
                } else {
                    TextureUsageState::Undefined
                },
                TextureUsageState::TransferDst,
            )));
            ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
                row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
                src_texture: targets.depth_texture,
                src_mip: 0,
                src_layer: 0,
                src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                dst_texture: capture.depth_texture,
                dst_mip: 0,
                dst_layer: 0,
                dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                extent: capture.extent,
            }));
            ops.push(CommandOp::Barrier(texture_barrier(
                targets.depth_texture,
                TextureUsageState::TransferSrc,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                capture.depth_texture,
                TextureUsageState::TransferDst,
                TextureUsageState::DepthStencilAttachment,
            )));
            ops.push(CommandOp::BeginPass {
                pass: capture.pass,
                target: capture.target,
                colors: vec![PassAttachment {
                    view: capture.color_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        r: 0.0,
                        g: 0.0,
                        b: 0.0,
                        a: 0.0,
                    }),
                }],
                depth_stencil: Some(PassAttachment {
                    view: capture.depth_view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            let mut capture_draw_state = IndexedDrawState::default();
            for draw in draws.iter().filter(|draw| {
                draw.material_mode == TerrainMaterialPassMode::Translucent
                    && !Self::translucent_draw_is_external(draw, translucent_entity_external, false)
            }) {
                append_indexed_draw(
                    ops,
                    &mut capture_draw_state,
                    draw.pipeline,
                    draw.pipeline_layout,
                    draw.resource_set,
                    &draw.resource_set_dynamic_offsets,
                    draw.shader_resource_set,
                    draw.index_buffer,
                    draw.index_offset,
                    draw.index_type,
                    draw.index_count,
                    draw.instance_count,
                    draw.indexed_indirect,
                );
            }
            ops.push(CommandOp::EndPass);
            ops.push(CommandOp::Barrier(texture_barrier(
                capture.color_texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
            ops.push(CommandOp::Barrier(texture_barrier(
                capture.depth_texture,
                TextureUsageState::DepthStencilAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        // The Fabulous handoff samples the dedicated capture and performs the
        // one alpha composition itself.  Do not open an empty deferred pass
        // after all translucent work was routed there: more importantly, do
        // not let a future draw accidentally reintroduce that second writer.
        if !draws.iter().any(|draw| {
            draw.material_mode == TerrainMaterialPassMode::Translucent
                && !Self::translucent_draw_is_external(
                    draw,
                    translucent_entity_external,
                    translucent_terrain_external,
                )
        }) {
            return Ok(());
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.deferred_lit_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.translucent_pass,
            target: targets.translucent_target,
            colors: vec![PassAttachment {
                view: targets.deferred_lit_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: Some(PassAttachment {
                view: targets.depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = IndexedDrawState::default();
        for draw in draws.iter().filter(|draw| {
            draw.material_mode == TerrainMaterialPassMode::Translucent
                && !Self::translucent_draw_is_external(
                    draw,
                    translucent_entity_external,
                    translucent_terrain_external,
                )
        }) {
            append_indexed_draw(
                ops,
                &mut draw_state,
                draw.pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                &draw.resource_set_dynamic_offsets,
                draw.shader_resource_set,
                draw.index_buffer,
                draw.index_offset,
                draw.index_type,
                draw.index_count,
                draw.instance_count,
                draw.indexed_indirect,
            );
        }
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.deferred_lit_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    /// Returns whether this translucent draw is owned by the explicit Fabulous
    /// attachment graph rather than the normal deferred color target.  The
    /// capture pass deliberately keeps terrain available to Fabulous while entity
    /// meshes use their separate `item_entity` role; the final deferred pass must
    /// omit both external families so neither is composited twice.
    pub(super) fn translucent_draw_is_external(
        draw: &TerrainMeshDraw,
        translucent_entity_external: bool,
        translucent_terrain_external: bool,
    ) -> bool {
        (translucent_entity_external && draw.stratum == WORLD_STRATUM_ENTITY_MESH)
            || (translucent_terrain_external && draw.stratum == WORLD_STRATUM_TERRAIN)
    }

    /// Direct semantic material quads use their own texture/pipeline contract,
    /// but must be written after deferred terrain lighting and before the
    /// composite chain. Recording them against the acquired target earlier in
    /// the frame lets the final composite overwrite their color.
    pub(super) fn append_forward_material_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        draws: &[TerrainForwardMaterialDraw],
    ) -> GalResult<()> {
        if draws.is_empty() {
            return Ok(());
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.deferred_lit_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.translucent_pass,
            target: targets.translucent_target,
            colors: vec![PassAttachment {
                view: targets.deferred_lit_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: Some(PassAttachment {
                view: targets.depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = IndexedDrawState::default();
        for draw in draws {
            append_indexed_draw(
                ops,
                &mut draw_state,
                draw.pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                &[],
                draw.shader_resource_set,
                draw.index_buffer,
                draw.index_offset,
                draw.index_type,
                draw.index_count,
                draw.instance_count,
                None,
            );
        }
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.deferred_lit_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    pub(super) fn append_screen_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        texture: Handle,
        pass: Handle,
        target: Handle,
        color_view: Handle,
        pipeline: Handle,
        resource_set: Handle,
        pipeline_layout: Handle,
        clear_color: ClearColor,
        texture_before: TextureUsageState,
    ) {
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            texture_before,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view: color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(clear_color),
            }],
            depth_stencil: None,
        });
        ops.push(CommandOp::BindGraphicsPipeline(pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
    }

    pub(super) fn pass_identity(&self, role: AttachmentRole) -> GalResult<&PassIdentity> {
        self.plan
            .graph
            .passes()
            .iter()
            .find(|pass| pass.depth == Some(role) || pass.colors.contains(&role))
            .map(|pass| &pass.identity)
            .ok_or_else(|| {
                GalError::invalid_argument(format!("shader runtime graph is missing {role:?} pass"))
            })
    }
}

pub(super) fn screen_texture_before(initialized: bool) -> TextureUsageState {
    if initialized {
        TextureUsageState::ShaderRead
    } else {
        TextureUsageState::Undefined
    }
}

pub(super) fn write_contract_diagnostic(plan: &ShaderPackRuntimePlan) {
    let Some(dir) = crate::core::environment::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
        return;
    };
    let dir = Path::new(&dir);
    if fs::create_dir_all(dir).is_err() {
        return;
    }
    let _ = fs::write(
        dir.join(format!(
            "terrain-pass-contract-generation-{}.json",
            plan.generation
        )),
        format!("{}\n", plan.terrain_contract_diagnostic_json()),
    );
}

impl TerrainCompositeUniforms {
    pub(crate) fn pack(self) -> Vec<u8> {
        let mut out = Vec::with_capacity(TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES as usize);
        for value in self.light_view_projection {
            push_f32(&mut out, value);
        }
        for value in self.shadow_params {
            push_f32(&mut out, value);
        }
        for value in self.color_grade_params {
            push_f32(&mut out, value);
        }
        for value in self.projection_inverse {
            push_f32(&mut out, value);
        }
        for value in self.fog_color_and_environmental_start {
            push_f32(&mut out, value);
        }
        for value in self.fog_ranges {
            push_f32(&mut out, value);
        }
        out
    }
}

//! Recording of source-program color passes (terrain, material, entity, hand, weather, cloud).

use super::*;

impl ShaderPackRuntimeExecutor {
    /// Appends one normal-terrain source pass to its named shader-pack color
    /// targets. This is private scheduling infrastructure only: the caller
    /// must still own complete source-graph admission, source-color history,
    /// shadow/DH/fullscreen ordering, and the eventual one-presenter route.
    /// It intentionally has no fallback or route-selection behavior.
    pub(crate) fn append_terrain_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        if matches!(
            std::env::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] source-pass phase={:?} depth_texture=0x{:016x} depth_view=0x{:016x} draws={}",
                targets.phase,
                targets.depth_texture.raw(),
                targets.depth_view.raw(),
                draws.len(),
            );
        }
        if matches!(
            std::env::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] terrain depth_texture=0x{:016x} depth_view=0x{:016x} phase={:?}",
                targets.depth_texture.raw(),
                targets.depth_view.raw(),
                targets.phase,
            );
        }
        if targets.color_attachments.is_empty() {
            return Err(GalError::invalid_argument(
                "source terrain color pass requires at least one named color attachment",
            ));
        }
        let mut seen_slots = std::collections::BTreeSet::new();
        let mut seen_outputs = std::collections::BTreeSet::new();
        for attachment in &targets.color_attachments {
            if !seen_slots.insert(attachment.source_slot) || !seen_outputs.insert(attachment.output)
            {
                return Err(GalError::invalid_argument(
                    "source terrain color pass has duplicate named output attachments",
                ));
            }
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            targets.phase.depth_before(),
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.pass,
            target: targets.target,
            colors: targets
                .color_attachments
                .iter()
                .map(|attachment| PassAttachment {
                    view: attachment.view,
                    load_op: targets.phase.color_load_op(attachment),
                    store_op: AttachmentStoreOp::Store,
                    clear_color: matches!(
                        targets.phase.color_load_op(attachment),
                        AttachmentLoadOp::Clear
                    )
                    .then(|| {
                        source_color_clear_color(
                            attachment.source_slot,
                            attachment.clear_color_bits,
                            targets.clear_values.fog_color,
                        )
                    }),
                })
                .collect(),
            depth_stencil: Some(PassAttachment {
                view: targets.depth_view,
                load_op: targets.phase.depth_load_op(),
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let accepted_draws = draws
            .iter()
            .filter(|draw| targets.phase.accepts_material(draw.material_mode))
            .collect::<Vec<_>>();
        if matches!(
            std::env::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] source-pass phase={:?} accepted_draws={} attachments={:?}",
                targets.phase,
                accepted_draws.len(),
                targets
                    .color_attachments
                    .iter()
                    .map(|attachment| (
                        attachment.role.shader_pack_color_name(),
                        attachment.source_slot,
                    ))
                    .collect::<Vec<_>>(),
            );
        }
        let mut draw_state = IndexedDrawState::default();
        for draw in accepted_draws {
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
        for attachment in &targets.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
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

    /// Appends the Rust-owned `gbuffers_textured` writer against the selected
    /// pack's named targets. It is intentionally load-only and does not own
    /// source-color transaction completion, route selection, or presentation.
    /// Those remain with the combined source-frame coordinator.
    pub(crate) fn append_textured_material_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TexturedMaterialSourceDraw],
    ) -> GalResult<()> {
        self.append_source_material_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::TexturedMaterial,
            "textured material",
        )
    }

    /// Appends the separate source-derived weather writer. It has no target,
    /// presentation, or transaction ownership outside this combined frame;
    /// the phase merely preserves its distinct alpha-over source contract.
    pub(crate) fn append_weather_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TexturedMaterialSourceDraw],
    ) -> GalResult<()> {
        self.append_source_material_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::Weather,
            "weather",
        )
    }

    /// Appends the source-derived cloud writer against the same Rust-owned
    /// named targets. The caller owns route selection and transaction
    /// completion; this only records the explicit load-only pass.
    pub(crate) fn append_line_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TexturedMaterialSourceDraw],
    ) -> GalResult<()> {
        self.append_source_material_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::Lines,
            "lines",
        )
    }

    pub(crate) fn append_damaged_block_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TexturedMaterialSourceDraw],
    ) -> GalResult<()> {
        self.append_source_material_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::DamagedBlock,
            "damagedblock",
        )
    }

    pub(crate) fn append_cloud_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TexturedMaterialSourceDraw],
    ) -> GalResult<()> {
        self.append_source_material_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::Clouds,
            "clouds",
        )
    }

    /// Appends one Rust-owned indexed entity writer against the selected
    /// shader-pack targets. The pass is intentionally load-only and has no
    /// route, target, transaction, or presentation ownership outside the
    /// combined source-frame coordinator.
    pub(crate) fn append_entity_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[EntitySourceDraw],
    ) -> GalResult<()> {
        self.append_indexed_source_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::Entities,
            "entity",
            false,
        )
    }

    /// Records the separate Rust-owned `gbuffers_hand` pass. It shares the
    /// explicit indexed source stream with entity meshes. Ordinary hands copy
    /// world depth first so depthtex0 retains terrain and hand depth.
    /// It has no route or presentation ownership outside the source-frame
    /// coordinator.
    pub(crate) fn append_hand_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[EntitySourceDraw],
        world_depth: Option<(Handle, Extent3d)>,
    ) -> GalResult<()> {
        if let Some((world_texture, extent)) = world_depth {
            Self::append_source_main_depth_snapshot(
                ops,
                world_texture,
                targets.depth_texture,
                extent,
            )?;
        }
        self.append_indexed_source_color_pass(
            ops,
            targets,
            draws,
            TerrainSourceColorPassPhase::Hands,
            "hand",
            world_depth.is_some(),
        )
    }

    pub(crate) fn append_glint_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[EntitySourceDraw],
        phase: TerrainSourceColorPassPhase,
    ) -> GalResult<()> {
        if !matches!(
            phase,
            TerrainSourceColorPassPhase::EntityGlint | TerrainSourceColorPassPhase::HandGlint
        ) {
            return Err(GalError::invalid_argument("glint source pass requires a glint phase"));
        }
        self.append_indexed_source_color_pass(ops, targets, draws, phase, "glint", false)
    }

    pub(super) fn append_indexed_source_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[EntitySourceDraw],
        expected_phase: TerrainSourceColorPassPhase,
        writer: &str,
        hand_depth_loaded: bool,
    ) -> GalResult<()> {
        if matches!(
            std::env::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] indexed-pass writer={} phase={:?} depth_texture=0x{:016x} depth_view=0x{:016x} draws={}",
                writer, targets.phase, targets.depth_texture.raw(), targets.depth_view.raw(), draws.len(),
            );
        }
        if targets.phase != expected_phase {
            return Err(GalError::invalid_argument(format!(
                "{writer} source draw requires its explicit source pass phase",
            )));
        }
        if targets.color_attachments.is_empty() {
            return Err(GalError::invalid_argument(format!(
                "{writer} source pass requires at least one named color attachment",
            )));
        }
        let mut seen_slots = std::collections::BTreeSet::new();
        let mut seen_outputs = std::collections::BTreeSet::new();
        for attachment in &targets.color_attachments {
            if !seen_slots.insert(attachment.source_slot) || !seen_outputs.insert(attachment.output)
            {
                return Err(GalError::invalid_argument(format!(
                    "{writer} source pass has duplicate named output attachments",
                )));
            }
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            if hand_depth_loaded { TextureUsageState::ShaderRead } else { targets.phase.depth_before() },
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.pass,
            target: targets.target,
            colors: targets
                .color_attachments
                .iter()
                .map(|attachment| PassAttachment {
                    view: attachment.view,
                    load_op: targets.phase.color_load_op(attachment),
                    store_op: AttachmentStoreOp::Store,
                    clear_color: matches!(
                        targets.phase.color_load_op(attachment),
                        AttachmentLoadOp::Clear
                    )
                    .then(|| {
                        source_color_clear_color(
                            attachment.source_slot,
                            attachment.clear_color_bits,
                            targets.clear_values.fog_color,
                        )
                    }),
                })
                .collect(),
            depth_stencil: Some(PassAttachment {
                view: targets.depth_view,
                load_op: if hand_depth_loaded { AttachmentLoadOp::Load } else { targets.phase.depth_load_op() },
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
        ops.push(CommandOp::EndPass);
        for attachment in &targets.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
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

    pub(super) fn append_source_material_color_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: &TerrainSourceColorPassTargets,
        draws: &[TexturedMaterialSourceDraw],
        expected_phase: TerrainSourceColorPassPhase,
        writer: &str,
    ) -> GalResult<()> {
        if matches!(
            std::env::var("MATTMC_RUST_SOURCE_DEPTH_TRACE").as_deref(),
            Ok("1") | Ok("true") | Ok("TRUE")
        ) {
            eprintln!(
                "[MattMC source-depth-trace] material-pass phase={:?} depth_texture=0x{:016x} depth_view=0x{:016x} draws={}",
                targets.phase, targets.depth_texture.raw(), targets.depth_view.raw(), draws.len(),
            );
        }
        if targets.phase != expected_phase {
            return Err(GalError::invalid_argument(format!(
                "{writer} source draw requires its explicit source pass phase"
            )));
        }
        if targets.color_attachments.is_empty() {
            return Err(GalError::invalid_argument(format!(
                "{writer} source pass requires at least one named color attachment",
            )));
        }
        let mut seen_slots = std::collections::BTreeSet::new();
        let mut seen_outputs = std::collections::BTreeSet::new();
        for attachment in &targets.color_attachments {
            if !seen_slots.insert(attachment.source_slot) || !seen_outputs.insert(attachment.output)
            {
                return Err(GalError::invalid_argument(format!(
                    "{writer} source pass has duplicate named output attachments",
                )));
            }
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                TextureUsageState::ShaderRead,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.pass,
            target: targets.target,
            colors: targets
                .color_attachments
                .iter()
                .map(|attachment| PassAttachment {
                    view: attachment.view,
                    load_op: AttachmentLoadOp::Load,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                })
                .collect(),
            depth_stencil: Some(PassAttachment {
                view: targets.depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = DirectDrawState::default();
        for draw in draws {
            append_direct_draw(
                ops,
                &mut draw_state,
                draw.pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                &draw.resource_set_dynamic_offsets,
                draw.shader_resource_set,
                draw.vertices,
            );
        }
        ops.push(CommandOp::EndPass);
        for attachment in &targets.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
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
}

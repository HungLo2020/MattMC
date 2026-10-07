//! DH pack-source passes, their targets and the target cache.

use crate::render::worldrender::lod::*;

/// Program identity for one Rust-owned source-derived DH pipeline. The color
/// target format is semantic pass data; native image or framebuffer identity
/// deliberately does not participate in this cache key.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodSourceProgramKey {
    pub(in crate::render::worldrender::lod) identity: String,
    pub(in crate::render::worldrender::lod) shader_pack_generation: u64,
    pub(in crate::render::worldrender::lod) color_format: TextureFormat,
    pub(in crate::render::worldrender::lod) cull_mode: u32,
    pub(in crate::render::worldrender::lod) front_face: FrontFace,
    /// DH generic objects (clouds, beacon beams) run the opaque `dh_terrain`
    /// source program with DH's generic-renderer alpha blend enabled.
    pub(in crate::render::worldrender::lod) alpha_blend: bool,
}

impl WorldLodSourceProgramKey {
    pub(super) fn from_program(
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
    ) -> GalResult<Self> {
        Ok(Self {
            identity: program.identity.as_str().to_string(),
            shader_pack_generation: program.shader_pack_generation,
            color_format,
            // This probe is diagnostic-only. It is deliberately part of the
            // pipeline identity so a cached normal pipeline cannot make an
            // experiment silently ineffective.
            cull_mode: selected_source_raster_probe_cull_mode()? as u32,
            front_face: selected_source_raster_probe_front_face(world_lod_source_front_face())?,
            alpha_blend: false,
        })
    }
}

#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodSourceDrawKey {
    pub(in crate::render::worldrender::lod) program: WorldLodSourceProgramKey,
    pub(in crate::render::worldrender::lod) draw: WorldLodDrawResourceKey,
}

/// Stable, semantic set-one identity for one source-derived DH program. The
/// key intentionally excludes opaque GAL/native resource handles: resource
/// generations prove compatibility while each backend keeps physical binding
/// identity private.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodSourcePackKey {
    pub(in crate::render::worldrender::lod) program: WorldLodSourceProgramKey,
    pub(in crate::render::worldrender::lod) world_generation: u64,
    pub(in crate::render::worldrender::lod) resource_generations: Vec<(TerrainSourceResourceRole, u64)>,
}

pub(super) struct WorldLodSourcePipelineResources {
    pub(in crate::render::worldrender::lod) vertex_shader: Handle,
    pub(in crate::render::worldrender::lod) fragment_shader: Handle,
    pub(in crate::render::worldrender::lod) source_data_layout: Handle,
    pub(in crate::render::worldrender::lod) pack_resources_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline_layout: Handle,
    pub(in crate::render::worldrender::lod) pipeline: Handle,
    /// A generic-blend variant shares the base pipeline's modules and
    /// layouts (pack sets must bind the identical layout) and owns only its
    /// pipeline object.
    pub(in crate::render::worldrender::lod) shares_base_layouts: bool,
}

impl WorldLodSourcePipelineResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        if self.shares_base_layouts {
            let _ = gal.destroy(self.pipeline);
            return;
        }
        for handle in [
            self.pipeline,
            self.pipeline_layout,
            self.pack_resources_layout,
            self.source_data_layout,
            self.fragment_shader,
            self.vertex_shader,
        ] {
            let _ = gal.destroy(handle);
        }
    }
}

/// Per-program ring of DH column-frame blocks for one frame: every draw of
/// the program binds this buffer at its own dynamic offset, and the frame's
/// blocks are uploaded with one host write.
pub(super) struct WorldLodSourceColumnFrameRing {
    pub(in crate::render::worldrender::lod) buffer: Handle,
    pub(in crate::render::worldrender::lod) stride: u64,
    pub(in crate::render::worldrender::lod) capacity: u32,
    pub(in crate::render::worldrender::lod) next: u32,
    pub(in crate::render::worldrender::lod) bytes: Vec<u8>,
}

pub(super) struct WorldLodSourceDrawResources {
    /// Ring-backed draws own no per-draw frame buffer.
    pub(in crate::render::worldrender::lod) column_frame_buffer: Option<Handle>,
    pub(in crate::render::worldrender::lod) scalar_uniform_buffer: Option<Handle>,
    pub(in crate::render::worldrender::lod) source_data_set: Handle,
}

impl WorldLodSourceDrawResources {
    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        let _ = gal.destroy(self.source_data_set);
        if let Some(buffer) = self.scalar_uniform_buffer {
            let _ = gal.destroy(buffer);
        }
        if let Some(buffer) = self.column_frame_buffer {
            let _ = gal.destroy(buffer);
        }
    }
}

/// One explicit draw record for the source-derived DH terrain pass. Set zero
/// is fully Rust-owned here; the caller must separately construct set one
/// from the lowered program's semantic pack-resource plan. That separation
/// keeps source texture/material policy out of column geometry ownership.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodPreparedSourceDraw {
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub source_data_set: Handle,
    pub source_data_dynamic_offsets: [u32; 2],
    pub source_data_dynamic_offset_count: u8,
    pub pack_resources_layout: Handle,
    /// An exact-atlas source range retains one additional Rust-owned atlas
    /// binding after the selected shader-pack resources. Ordinary reduced DH
    /// draws leave this absent.
    pub source_extra_resource_set: Option<Handle>,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
}

/// One private render target pairing source-declared terrain outputs with
/// DH's separately owned depth stream. The key is restricted to Rust GAL
/// handles and semantic generations; neither Java/Iris framebuffer state nor
/// a backend-native image identity participates.
#[derive(Clone, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodSourceTargetKey {
    pub(in crate::render::worldrender::lod) world_generation: u64,
    pub(in crate::render::worldrender::lod) shader_pack_generation: u64,
    pub(in crate::render::worldrender::lod) width: u32,
    pub(in crate::render::worldrender::lod) height: u32,
    pub(in crate::render::worldrender::lod) color_attachments: Vec<WorldLodSourceColorAttachmentKey>,
    /// The target owns this exact depth attachment. A source-depth cache can
    /// replace its texture/view while retaining the same world, pack, and
    /// extent, so omitting it would let DH draw into an old attachment while
    /// downstream source stages sample the replacement.
    pub(in crate::render::worldrender::lod) distant_depth_view: Handle,
}

#[derive(Clone, Copy, Debug)]
pub(super) struct WorldLodSourceTargetResources {
    pub(in crate::render::worldrender::lod) target: Handle,
    pub(in crate::render::worldrender::lod) pass: Handle,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub(super) struct WorldLodSourceColorAttachmentKey {
    pub(in crate::render::worldrender::lod) output: TerrainPassOutput,
    pub(in crate::render::worldrender::lod) source_slot: u32,
    pub(in crate::render::worldrender::lod) texture: Handle,
    pub(in crate::render::worldrender::lod) view: Handle,
    pub(in crate::render::worldrender::lod) format: TextureFormat,
}

/// Explicit pass-local handles for the source-derived DH opaque stage. This
/// record does not own a route decision or draw list; the eventual executor
/// must bind the matching source program and consume the target in its one
/// combined frame submission.
#[derive(Clone, Debug)]
pub(crate) struct WorldLodPreparedSourceTarget {
    pub target: Handle,
    pub pass: Handle,
    pub color_attachments: Vec<TerrainSourceColorAttachment>,
    pub primary_color_texture: Handle,
    pub primary_color_view: Handle,
    pub primary_color_format: TextureFormat,
    pub primary_color_clears_each_frame: bool,
    pub distant_depth_texture: Handle,
    pub distant_depth_view: Handle,
}

pub(super) fn primary_source_color_attachment(
    color_targets: &ShaderPackColorTargets,
) -> GalResult<TerrainSourceColorAttachment> {
    let target = color_targets.target("primary").ok_or_else(|| {
        GalError::invalid_argument(
            "Distant Horizons source contract has no semantic primary color target",
        )
    })?;
    Ok(TerrainSourceColorAttachment {
        output: TerrainPassOutput::LitTerrainColor,
        source_slot: target.source_slot,
        role: TerrainSourceResourceRole::ShaderPackColor("primary".to_string()),
        texture: target.current_texture,
        view: target.current_attachment_view,
        format: target.format,
        clear_each_frame: target.clear_each_frame,
        clear_color_bits: target.clear_color_bits,
    })
}

pub(super) fn exact_atlas_source_pipeline_key(
    program: &LoweredDistantHorizonsExactAtlasSourceProgram,
    attachment: &TerrainSourceColorAttachment,
    pack_resources_layout: Handle,
) -> GalResult<WorldLodExactAtlasSourcePipelineKey> {
    if attachment.output != TerrainPassOutput::LitTerrainColor {
        return Err(GalError::invalid_argument(
            "world LOD exact-atlas source pass requires the named DH primary color attachment",
        ));
    }
    Ok(WorldLodExactAtlasSourcePipelineKey {
        identity: program.source.identity.as_str().to_string(),
        shader_pack_generation: program.source.shader_pack_generation,
        primary_format: attachment.format,
        pack_resources_layout,
        front_face: selected_source_raster_probe_front_face(world_lod_source_front_face())?,
    })
}

/// Private Rust owner for the source-derived DH column ABI. It is deliberately
/// not a route selector and owns no render target or pack-resource set: those
/// belong to the shader-pack runtime's named target/resource transaction.
///
/// The owner is nevertheless production-grade in the narrow sense that its
/// pipelines, buffers, resource layouts, and update barriers are explicit
/// VulkanicGAL objects reusable by both Rust backends.
#[derive(Default)]
pub(crate) struct WorldLodSourcePassResources {
    pub(in crate::render::worldrender::lod) pipelines: BTreeMap<WorldLodSourceProgramKey, WorldLodSourcePipelineResources>,
    pub(in crate::render::worldrender::lod) draws: BTreeMap<WorldLodSourceDrawKey, WorldLodSourceDrawResources>,
    /// Per-frame DH generic-box draws. They bind the frontend's transient
    /// generic geometry buffers, never column assets, so column
    /// reconciliation leaves them alone; `retire_generic_draws` releases them
    /// before those buffers are replaced.
    pub(in crate::render::worldrender::lod) generic_draws: BTreeMap<WorldLodSourceDrawKey, WorldLodSourceDrawResources>,
    /// One scalar-uniform buffer per source program (shared by its generic
    /// blend variant): the pack's scalar block is identical for every DH draw
    /// of a program in a frame, as Iris sets it once per program.
    pub(in crate::render::worldrender::lod) scalar_buffers: BTreeMap<WorldLodSourceProgramKey, Handle>,
    /// Programs whose scalar block was already written this frame.
    pub(in crate::render::worldrender::lod) scalar_written_this_frame: BTreeSet<WorldLodSourceProgramKey>,
    pub(in crate::render::worldrender::lod) column_frame_rings: BTreeMap<WorldLodSourceProgramKey, WorldLodSourceColumnFrameRing>,
    /// Per-frame memo of the last program's pipeline key (the key reads the
    /// raster probe and clones the program identity) and of the programs
    /// whose execution interface was already validated this frame.
    pub(in crate::render::worldrender::lod) key_memo: Option<(usize, u64, TextureFormat, WorldLodSourceProgramKey)>,
    pub(in crate::render::worldrender::lod) validated_this_frame: BTreeSet<WorldLodSourceProgramKey>,
    pub(in crate::render::worldrender::lod) pack_resources: BTreeMap<WorldLodSourcePackKey, Handle>,
    pub(in crate::render::worldrender::lod) targets: BTreeMap<WorldLodSourceTargetKey, WorldLodSourceTargetResources>,
}

impl WorldLodSourcePassResources {
    /// Stages the shared terrain-compatible render target used by source-derived
    /// DH phases. Named pack colors remain runtime-owned; this owner joins
    /// their terrain output schema to DH's distinct depth attachment.
    pub(crate) fn stage_target(
        &mut self,
        gal: &mut VulkanicGal,
        color_targets: &ShaderPackColorTargets,
        depth_targets: WorldLodSourceTargets,
    ) -> GalResult<WorldLodPreparedSourceTarget> {
        if color_targets.identity.world_generation != depth_targets.identity.world_generation
            || color_targets.identity.shader_pack_generation
                != depth_targets.identity.shader_pack_generation
            || color_targets.identity.extent != depth_targets.identity.extent
        {
            return Err(GalError::invalid_argument(
                "Distant Horizons source color and depth target generations do not match",
            ));
        }
        let primary = primary_source_color_attachment(color_targets)?;
        self.stage_target_attachments(gal, vec![primary.clone()], primary, depth_targets)
    }

    pub(super) fn stage_target_attachments(
        &mut self,
        gal: &mut VulkanicGal,
        color_attachments: Vec<TerrainSourceColorAttachment>,
        primary: TerrainSourceColorAttachment,
        depth_targets: WorldLodSourceTargets,
    ) -> GalResult<WorldLodPreparedSourceTarget> {
        if color_attachments.is_empty() {
            return Err(GalError::invalid_argument(
                "Distant Horizons source target requires at least one named color attachment",
            ));
        }
        let key = WorldLodSourceTargetKey {
            world_generation: depth_targets.identity.world_generation,
            shader_pack_generation: depth_targets.identity.shader_pack_generation,
            width: depth_targets.identity.extent.width,
            height: depth_targets.identity.extent.height,
            color_attachments: color_attachments
                .iter()
                .map(|attachment| WorldLodSourceColorAttachmentKey {
                    output: attachment.output,
                    source_slot: attachment.source_slot,
                    texture: attachment.texture,
                    view: attachment.view,
                    format: attachment.format,
                })
                .collect(),
            distant_depth_view: depth_targets.distant_depth_view,
        };
        if !self.targets.contains_key(&key) {
            let stale = self
                .targets
                .keys()
                .filter(|existing| {
                    existing.world_generation != key.world_generation
                        || existing.shader_pack_generation != key.shader_pack_generation
                        || existing.width != key.width
                        || existing.height != key.height
                        || existing.distant_depth_view != key.distant_depth_view
                })
                .cloned()
                .collect::<Vec<_>>();
            for stale_key in stale {
                if let Some(resources) = self.targets.remove(&stale_key) {
                    let _ = gal.destroy(resources.pass);
                    let _ = gal.destroy(resources.target);
                }
            }
            let target = gal.create_render_target(RenderTargetDesc {
                label: format!(
                    "world-lod-source.world{}-pack{}.opaque-target",
                    key.world_generation, key.shader_pack_generation
                ),
                color_views: color_attachments
                    .iter()
                    .map(|attachment| attachment.view)
                    .collect(),
                depth_stencil_view: Some(depth_targets.distant_depth_view),
                extent: depth_targets.identity.extent,
            })?;
            let pass = match gal.create_render_pass(RenderPassDesc {
                label: format!(
                    "world-lod-source.world{}-pack{}.opaque-pass",
                    key.world_generation, key.shader_pack_generation
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
            self.targets
                .insert(key.clone(), WorldLodSourceTargetResources { target, pass });
        }
        let resources = self
            .targets
            .get(&key)
            .expect("DH source target exists after successful staging");
        Ok(WorldLodPreparedSourceTarget {
            target: resources.target,
            pass: resources.pass,
            color_attachments,
            primary_color_texture: primary.texture,
            primary_color_view: primary.view,
            primary_color_format: primary.format,
            primary_color_clears_each_frame: primary.clear_each_frame,
            distant_depth_texture: depth_targets.distant_depth_texture,
            distant_depth_view: depth_targets.distant_depth_view,
        })
    }

    /// Records one source-derived DH draw using only prepared Rust-owned
    /// target and descriptor sets. The caller owns phase ordering and chooses
    /// whether the depth image remains attached for an immediate snapshot or
    /// transitions back to shader-read after a late translucent pass.
    pub(crate) fn append_draw(
        target: &WorldLodPreparedSourceTarget,
        draw: WorldLodPreparedSourceDraw,
        pack_resources: Handle,
        fog_color: crate::render::vulkanic::commands::ClearColor,
        // The combined source-frame scheduler owns the one initial clear of
        // a shared named color target. DH may be the first writer, or it may
        // follow ordinary terrain, so this cannot be inferred from the DH
        // target alone.
        clear_primary_color: bool,
        color_before: TextureUsageState,
        depth_before: TextureUsageState,
        depth_after: Option<TextureUsageState>,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if pack_resources.kind() != Some(crate::render::vulkanic::handles::HandleKind::ResourceSet)
        {
            return Err(GalError::invalid_argument(
                "Distant Horizons source opaque draw requires a GAL pack resource-set handle",
            ));
        }
        if draw.source_data_dynamic_offset_count > 2 {
            return Err(GalError::invalid_argument(
                "Distant Horizons source opaque draw has an invalid set-zero dynamic-offset count",
            ));
        }
        if color_before == TextureUsageState::ColorAttachment
            || depth_before == TextureUsageState::DepthStencilAttachment
        {
            return Err(GalError::invalid_argument(
                "Distant Horizons source opaque draw cannot begin while a target attachment is already in a pass",
            ));
        }
        if color_before == TextureUsageState::Undefined && !clear_primary_color {
            return Err(GalError::invalid_argument(
                "Distant Horizons source primary color is undefined but the combined source frame did not schedule its initial clear",
            ));
        }
        if clear_primary_color && !target.primary_color_clears_each_frame {
            return Err(GalError::invalid_argument(
                "Distant Horizons source primary color clear conflicts with the shader-pack target declaration",
            ));
        }
        if clear_primary_color && color_before != TextureUsageState::ShaderRead {
            return Err(GalError::invalid_argument(
                "Distant Horizons source primary color can clear only from the source-frame shader-read boundary",
            ));
        }
        for attachment in &target.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                color_before,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            target.distant_depth_texture,
            depth_before,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: target.pass,
            target: target.target,
            colors: target
                .color_attachments
                .iter()
                .map(|attachment| PassAttachment {
                    view: attachment.view,
                    load_op: if clear_primary_color
                        && attachment.output == TerrainPassOutput::LitTerrainColor
                    {
                        AttachmentLoadOp::Clear
                    } else {
                        AttachmentLoadOp::Load
                    },
                    store_op: AttachmentStoreOp::Store,
                    clear_color: (clear_primary_color
                        && attachment.output == TerrainPassOutput::LitTerrainColor)
                        .then(|| {
                            source_color_clear_color(
                                attachment.source_slot,
                                attachment.clear_color_bits,
                                fog_color,
                            )
                        }),
                })
                .collect(),
            depth_stencil: Some(PassAttachment {
                view: target.distant_depth_view,
                load_op: if depth_before == TextureUsageState::Undefined {
                    AttachmentLoadOp::Clear
                } else {
                    AttachmentLoadOp::Load
                },
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        ops.push(CommandOp::BindGraphicsPipeline(draw.pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: draw.pipeline_layout,
            set_index: 0,
            set: draw.source_data_set,
            dynamic_offsets: draw.source_data_dynamic_offsets
                [..usize::from(draw.source_data_dynamic_offset_count)]
                .iter()
                .copied()
                .map(u64::from)
                .collect(),
        });
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: draw.pipeline_layout,
            set_index: 1,
            set: pack_resources,
            dynamic_offsets: Vec::new(),
        });
        if let Some(extra_resources) = draw.source_extra_resource_set {
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: draw.pipeline_layout,
                set_index: 2,
                set: extra_resources,
                dynamic_offsets: Vec::new(),
            });
        }
        ops.push(CommandOp::SetIndexBuffer {
            buffer: draw.index_buffer,
            offset: draw.index_offset,
            index_type: draw.index_type,
        });
        ops.push(CommandOp::DrawIndexed {
            indices: draw.index_count,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        if let Some(depth_after) = depth_after {
            ops.push(CommandOp::Barrier(texture_barrier(
                target.distant_depth_texture,
                TextureUsageState::DepthStencilAttachment,
                depth_after,
            )));
        }
        // Downstream source-derived passes consume the same named target as a
        // semantic sampled resource. Make that dependency explicit instead
        // of leaving the target in an attachment state for a backend to infer.
        for attachment in &target.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        Ok(())
    }

    /// Appends an ordered DH draw range in one render pass.
    ///
    /// The opaque range (reduced-color, exact-atlas and generic draws) and the
    /// late translucent range each write the same named source attachments
    /// with one depth domain and nothing reads them mid-range. Keeping the
    /// attachments open across a range preserves draw (and blend) order and
    /// per-draw pipeline/resource bindings while avoiding a pass/barrier round
    /// trip for every segment. The caller still performs the one opaque-depth
    /// snapshot after the opaque range, before translucent work reads it.
    pub(crate) fn append_ordered_batch(
        target: &WorldLodPreparedSourceTarget,
        draws: &[(WorldLodPreparedSourceDraw, Handle)],
        fog_color: crate::render::vulkanic::commands::ClearColor,
        clear_primary_color: bool,
        color_before: TextureUsageState,
        depth_before: TextureUsageState,
        depth_after: Option<TextureUsageState>,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if draws.is_empty() {
            return Ok(());
        }
        if color_before == TextureUsageState::ColorAttachment
            || depth_before == TextureUsageState::DepthStencilAttachment
        {
            return Err(GalError::invalid_argument(
                "Distant Horizons source batch cannot begin while a target attachment is already in a pass",
            ));
        }
        if color_before == TextureUsageState::Undefined && !clear_primary_color {
            return Err(GalError::invalid_argument(
                "Distant Horizons source primary color is undefined but the combined source frame did not schedule its initial clear",
            ));
        }
        if clear_primary_color && !target.primary_color_clears_each_frame {
            return Err(GalError::invalid_argument(
                "Distant Horizons source primary color clear conflicts with the shader-pack target declaration",
            ));
        }
        if clear_primary_color && color_before != TextureUsageState::ShaderRead {
            return Err(GalError::invalid_argument(
                "Distant Horizons source primary color can clear only from the source-frame shader-read boundary",
            ));
        }
        for (draw, pack_resources) in draws {
            if pack_resources.kind()
                != Some(crate::render::vulkanic::handles::HandleKind::ResourceSet)
            {
                return Err(GalError::invalid_argument(
                    "Distant Horizons source batch requires GAL pack resource-set handles",
                ));
            }
            if draw.source_data_dynamic_offset_count > 2 {
                return Err(GalError::invalid_argument(
                    "Distant Horizons source batch has an invalid set-zero dynamic-offset count",
                ));
            }
            if draw.index_count == 0 || draw.index_count % 3 != 0 {
                return Err(GalError::invalid_argument(
                    "Distant Horizons source batch requires triangle-aligned indices",
                ));
            }
        }
        for attachment in &target.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                color_before,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            target.distant_depth_texture,
            depth_before,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: target.pass,
            target: target.target,
            colors: target
                .color_attachments
                .iter()
                .map(|attachment| PassAttachment {
                    view: attachment.view,
                    load_op: if clear_primary_color
                        && attachment.output == TerrainPassOutput::LitTerrainColor
                    {
                        AttachmentLoadOp::Clear
                    } else {
                        AttachmentLoadOp::Load
                    },
                    store_op: AttachmentStoreOp::Store,
                    clear_color: (clear_primary_color
                        && attachment.output == TerrainPassOutput::LitTerrainColor)
                        .then(|| {
                            source_color_clear_color(
                                attachment.source_slot,
                                attachment.clear_color_bits,
                                fog_color,
                            )
                        }),
                })
                .collect(),
            depth_stencil: Some(PassAttachment {
                view: target.distant_depth_view,
                load_op: if depth_before == TextureUsageState::Undefined {
                    AttachmentLoadOp::Clear
                } else {
                    AttachmentLoadOp::Load
                },
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        for (draw, pack_resources) in draws {
            ops.push(CommandOp::BindGraphicsPipeline(draw.pipeline));
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: draw.pipeline_layout,
                set_index: 0,
                set: draw.source_data_set,
                dynamic_offsets: draw.source_data_dynamic_offsets
                    [..usize::from(draw.source_data_dynamic_offset_count)]
                    .iter()
                    .copied()
                    .map(u64::from)
                    .collect(),
            });
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout: draw.pipeline_layout,
                set_index: 1,
                set: *pack_resources,
                dynamic_offsets: Vec::new(),
            });
            if let Some(extra_resources) = draw.source_extra_resource_set {
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout: draw.pipeline_layout,
                    set_index: 2,
                    set: extra_resources,
                    dynamic_offsets: Vec::new(),
                });
            }
            ops.push(CommandOp::SetIndexBuffer {
                buffer: draw.index_buffer,
                offset: draw.index_offset,
                index_type: draw.index_type,
            });
            ops.push(CommandOp::DrawIndexed {
                indices: draw.index_count,
                instances: 1,
            });
        }
        ops.push(CommandOp::EndPass);
        if let Some(depth_after) = depth_after {
            ops.push(CommandOp::Barrier(texture_barrier(
                target.distant_depth_texture,
                TextureUsageState::DepthStencilAttachment,
                depth_after,
            )));
        }
        for attachment in &target.color_attachments {
            ops.push(CommandOp::Barrier(texture_barrier(
                attachment.texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        Ok(())
    }

    pub(crate) fn stage_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
        draw: WorldLodGpuDraw,
        column_frame: WorldLodDrawUniform,
        source_uniforms: &TerrainSourceUniformFrame,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedSourceDraw> {
        self.stage_draw_inner(
            gal, program, color_format, draw, column_frame, source_uniforms, ops, false,
        )
    }

    /// Stages one DH generic-object range (Iris `dh_generic`, falling back to
    /// `dh_terrain`) with DH's generic alpha blend.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn stage_generic_draw(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
        draw: WorldLodGpuDraw,
        column_frame: WorldLodDrawUniform,
        source_uniforms: &TerrainSourceUniformFrame,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<WorldLodPreparedSourceDraw> {
        self.stage_draw_inner(
            gal, program, color_format, draw, column_frame, source_uniforms, ops, true,
        )
    }

    /// Releases the set-one pack bindings that bind a role `binds` selects
    /// (lightmap, voxel volumes, named targets, ...). Draw sets, pipelines and
    /// per-frame rings bind none of those, and pack sets that bind none of the
    /// released roles stay valid (a lightmap change must not rebuild them).
    pub(crate) fn release_pack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        binds: impl Fn(&[(TerrainSourceResourceRole, u64)]) -> bool,
    ) {
        let released: Vec<_> = self
            .pack_resources
            .keys()
            .filter(|key| binds(&key.resource_generations))
            .cloned()
            .collect();
        for key in released {
            if let Some(set) = self.pack_resources.remove(&key) {
                let _ = gal.destroy(set);
            }
        }
    }

    /// Starts one source frame: each program's shared scalar block is written
    /// once, by its first staged draw.
    pub(crate) fn begin_source_frame(&mut self) {
        self.scalar_written_this_frame.clear();
        self.key_memo = None;
        self.validated_this_frame.clear();
        for ring in self.column_frame_rings.values_mut() {
            ring.next = 0;
            ring.bytes.clear();
        }
    }

    /// Ensures `program` can stage `count` draws this frame before any of
    /// them is staged (growing the ring retires the sets bound to it).
    pub(crate) fn reserve_column_frames(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
        count: usize,
    ) -> GalResult<()> {
        let key =
            WorldLodSourceProgramKey::from_program(program, color_format)?;
        self.ensure_column_frame_capacity(gal, program, &key, count)
    }

    pub(super) fn ensure_column_frame_capacity(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        scalar_key: &WorldLodSourceProgramKey,
        count: usize,
    ) -> GalResult<()> {
        let count = u32::try_from(count.max(1))
            .map_err(|_| GalError::invalid_argument("DH source draw count exceeds u32"))?;
        if let Some(ring) = self.column_frame_rings.get(scalar_key) {
            if ring.capacity >= count {
                return Ok(());
            }
            if ring.next != 0 {
                return Err(GalError::invalid_argument(
                    "DH source column-frame ring cannot grow after draws were staged this frame",
                ));
            }
        }
        // Retire every draw set bound to the ring being replaced.
        let bound = |key: &WorldLodSourceDrawKey| {
            WorldLodSourceProgramKey {
                alpha_blend: false,
                ..key.program.clone()
            } == *scalar_key
        };
        for map in [&mut self.draws, &mut self.generic_draws] {
            let stale = map.keys().filter(|key| bound(key)).cloned().collect::<Vec<_>>();
            for key in stale {
                if let Some(resources) = map.remove(&key) {
                    resources.destroy(gal);
                }
            }
        }
        if let Some(previous) = self.column_frame_rings.remove(scalar_key) {
            let _ = gal.destroy(previous.buffer);
        }
        let alignment = gal.capabilities().limits.uniform_buffer_offset_alignment.max(1);
        let block = u64::from(program.execution_interface.column_frame_bytes);
        let stride = block.div_ceil(alignment) * alignment;
        let capacity = count.next_power_of_two().max(64);
        let buffer = gal.create_buffer(BufferDesc {
            label: format!(
                "world-lod-source-{}-gen{}.column-frames",
                scalar_key.identity.replace(':', "-"),
                scalar_key.shader_pack_generation
            ),
            size: stride * u64::from(capacity),
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
        })?;
        self.column_frame_rings.insert(
            scalar_key.clone(),
            WorldLodSourceColumnFrameRing {
                buffer,
                stride,
                capacity,
                next: 0,
                bytes: Vec::new(),
            },
        );
        Ok(())
    }

    /// Uploads every program's column-frame blocks staged this frame.
    pub(crate) fn flush_source_frame(&mut self, ops: &mut Vec<CommandOp>) {
        for ring in self.column_frame_rings.values_mut() {
            if ring.bytes.is_empty() {
                continue;
            }
            append_source_uniform_upload(ops, ring.buffer, std::mem::take(&mut ring.bytes));
        }
    }

    /// Releases every generic-box draw set before its geometry buffers are
    /// replaced or dropped.
    pub(crate) fn retire_generic_draws(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.generic_draws) {
            resources.destroy(gal);
        }
    }

    #[allow(clippy::too_many_arguments)]
    pub(super) fn stage_draw_inner(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
        draw: WorldLodGpuDraw,
        column_frame: WorldLodDrawUniform,
        source_uniforms: &TerrainSourceUniformFrame,
        ops: &mut Vec<CommandOp>,
        alpha_blend: bool,
    ) -> GalResult<WorldLodPreparedSourceDraw> {
        if draw.index_count == 0 || draw.index_count % 3 != 0 {
            return Err(GalError::invalid_argument(
                "source-derived Distant Horizons draw requires triangle-aligned indices",
            ));
        }
        let program_address = program as *const LoweredDistantHorizonsSourceProgram as usize;
        let base_key = match &self.key_memo {
            Some((address, generation, format, key))
                if *address == program_address
                    && *generation == program.shader_pack_generation
                    && *format == color_format =>
            {
                key.clone()
            }
            _ => {
                let key = WorldLodSourceProgramKey::from_program(program, color_format)?;
                self.key_memo = Some((
                    program_address,
                    program.shader_pack_generation,
                    color_format,
                    key.clone(),
                ));
                key
            }
        };
        if !self.validated_this_frame.contains(&base_key) {
            program.execution_interface.validate()?;
            self.validated_this_frame.insert(base_key.clone());
        }
        let mut key = base_key;
        key.alpha_blend = alpha_blend;
        self.ensure_pipeline(gal, program, &key)?;
        let scalar_key = WorldLodSourceProgramKey {
            alpha_blend: false,
            ..key.clone()
        };
        let shared_scalar_buffer = if program.execution_interface.scalar_uniforms.is_some() {
            let buffer = match self.scalar_buffers.get(&scalar_key) {
                Some(buffer) => *buffer,
                None => {
                    let buffer = gal.create_buffer(BufferDesc {
                        label: format!(
                            "world-lod-source-{}-gen{}.scalars",
                            scalar_key.identity.replace(':', "-"),
                            scalar_key.shader_pack_generation
                        ),
                        size: u64::from(program.execution_interface.scalar_uniform_bytes),
                        memory: MemoryDomain::Upload,
                        usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
                    })?;
                    self.scalar_buffers.insert(scalar_key.clone(), buffer);
                    buffer
                }
            };
            if self.scalar_written_this_frame.insert(scalar_key.clone()) {
                append_source_uniform_upload(ops, buffer, program.pack_scalar_uniforms(source_uniforms)?);
            }
            Some(buffer)
        } else {
            if !program.pack_scalar_uniforms(source_uniforms)?.is_empty() {
                return Err(GalError::invalid_argument(
                    "source-derived Distant Horizons program has no scalar binding but received scalar bytes",
                ));
            }
            None
        };
        // Claim this draw's column-frame slot (the ring grows only before
        // any draw of this program was staged this frame).
        let needed = self
            .column_frame_rings
            .get(&scalar_key)
            .map_or(1, |ring| ring.next as usize + 1);
        self.ensure_column_frame_capacity(gal, program, &scalar_key, needed)?;
        let (column_frame_ring_buffer, column_frame_offset) = {
            let ring = self
                .column_frame_rings
                .get_mut(&scalar_key)
                .expect("column-frame ring exists after capacity check");
            let offset = u64::from(ring.next) * ring.stride;
            ring.bytes.resize(offset as usize, 0);
            ring.bytes.extend_from_slice(column_frame.pack_source_std140().as_slice());
            ring.bytes.resize((offset + ring.stride) as usize, 0);
            ring.next += 1;
            (ring.buffer, offset)
        };
        let column_frame_offset = u32::try_from(column_frame_offset)
            .map_err(|_| GalError::invalid_argument("DH column-frame offset exceeds u32"))?;
        let draw_key = WorldLodSourceDrawKey {
            program: key.clone(),
            draw: WorldLodDrawResourceKey::from_draw(draw),
        };
        let created_draw_resources = !if alpha_blend {
            &self.generic_draws
        } else {
            &self.draws
        }
        .contains_key(&draw_key);
        if created_draw_resources {
            let pipeline = self
                .pipelines
                .get(&key)
                .expect("source DH pipeline exists after successful initialization");
            let mut created = Vec::new();
            let result = (|| -> GalResult<WorldLodSourceDrawResources> {
                let column_frame_buffer: Option<Handle> = None;
                // Draw sets bind the program's shared scalar buffer.
                let scalar_uniform_buffer: Option<Handle> = None;
                let mut bindings = vec![
                    ResourceBinding {
                        binding: program.execution_interface.vertex_stream.binding,
                        array_index: 0,
                        resource: draw.vertex_buffer,
                        kind: ResourceBindingKind::StorageBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: program.execution_interface.column_frame.binding,
                        array_index: 0,
                        resource: column_frame_ring_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: vec![0],
                        buffer_range: Some(u64::from(
                            program.execution_interface.column_frame_bytes,
                        )),
                    },
                ];
                if let (Some(binding), Some(buffer)) = (
                    program.execution_interface.scalar_uniforms,
                    shared_scalar_buffer,
                ) {
                    bindings.push(ResourceBinding {
                        binding: binding.binding,
                        array_index: 0,
                        resource: buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: vec![0],
                        buffer_range: Some(u64::from(
                            program.execution_interface.scalar_uniform_bytes,
                        )),
                    });
                }
                bindings.sort_by_key(|binding| binding.binding);
                let source_data_set = gal.create_resource_set(ResourceSetDesc {
                    label: format!(
                        "world-lod-source-column{}-gen{}-segment{}.set-zero",
                        draw.column_key, draw.column_generation, draw.segment_index
                    ),
                    layout: pipeline.source_data_layout,
                    bindings,
                })?;
                created.push(source_data_set);
                Ok(WorldLodSourceDrawResources {
                    column_frame_buffer,
                    scalar_uniform_buffer,
                    source_data_set,
                })
            })();
            if result.is_err() {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
            }
            if alpha_blend {
                self.generic_draws.insert(draw_key.clone(), result?);
            } else {
                self.draws.insert(draw_key.clone(), result?);
            }
        }
        let resources = if alpha_blend {
            &self.generic_draws
        } else {
            &self.draws
        }
        .get(&draw_key)
            .expect("source DH draw resources exist after successful staging");

        let pipeline = self
            .pipelines
            .get(&key)
            .expect("source DH pipeline remains alive while draw resources are live");
        Ok(WorldLodPreparedSourceDraw {
            pipeline: pipeline.pipeline,
            pipeline_layout: pipeline.pipeline_layout,
            source_data_set: resources.source_data_set,
            source_data_dynamic_offsets: [column_frame_offset, 0],
            source_data_dynamic_offset_count: if program
                .execution_interface
                .scalar_uniforms
                .is_some()
            {
                2
            } else {
                1
            },
            pack_resources_layout: pipeline.pack_resources_layout,
            source_extra_resource_set: None,
            index_buffer: draw.index_buffer,
            index_offset: draw.index_offset,
            index_type: draw.index_type,
            index_count: draw.index_count,
        })
    }

    /// Materializes the source program's named semantic sampler/storage set
    /// after set-zero data preparation. This remains separate from draw
    /// recording and target selection, so incomplete pack resources reject
    /// before the DH route can issue any command.
    pub(crate) fn stage_pack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
        resources: &TerrainSourceOwnedResourceSet,
    ) -> GalResult<Handle> {
        program.require_semantic_resources(resources.availability())?;
        let world_generation = resources.availability().world_generation();
        if world_generation == 0 {
            return Err(GalError::invalid_argument(
                "Distant Horizons source pack resources require a non-zero world generation",
            ));
        }
        let program_key =
            WorldLodSourceProgramKey::from_program(program, color_format)?;
        self.ensure_pipeline(gal, program, &program_key)?;
        let key = WorldLodSourcePackKey {
            program: program_key.clone(),
            world_generation,
            resource_generations: resources.generation_signature(),
        };
        if let Some(set) = self.pack_resources.get(&key) {
            return Ok(*set);
        }
        let stale = self
            .pack_resources
            .keys()
            .filter(|existing| {
                existing.program == key.program
                    && existing.world_generation == key.world_generation
                    && existing.resource_generations != key.resource_generations
            })
            .cloned()
            .collect::<Vec<_>>();
        for stale_key in stale {
            if let Some(set) = self.pack_resources.remove(&stale_key) {
                let _ = gal.destroy(set);
            }
        }
        let layout = self
            .pipelines
            .get(&program_key)
            .expect("source DH pipeline exists after successful initialization")
            .pack_resources_layout;
        let set = gal.create_resource_set(program.pack_resource_set_desc(
            format!(
                "world-lod-source-{}-pack{}-world{}.set-one",
                program_key.identity.replace(':', "-"),
                program_key.shader_pack_generation,
                world_generation,
            ),
            layout,
            resources,
        )?)?;
        self.pack_resources.insert(key, set);
        Ok(set)
    }

    /// Returns the layout owned by the normal source pipeline for semantic
    /// pack resources. Source-derived variants must share this exact layout
    /// handle with the set-one resource set, rather than recreating an
    /// equivalent layout with a distinct GAL identity.
    pub(crate) fn pack_resources_layout(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        color_format: TextureFormat,
    ) -> GalResult<Handle> {
        let key =
            WorldLodSourceProgramKey::from_program(program, color_format)?;
        self.ensure_pipeline(gal, program, &key)?;
        Ok(self
            .pipelines
            .get(&key)
            .expect("source DH pipeline exists after successful initialization")
            .pack_resources_layout)
    }

    pub(crate) fn reconcile_assets(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &BTreeMap<u64, WorldLodGpuColumnAsset>,
    ) {
        let stale = self
            .draws
            .keys()
            .filter(|key| {
                assets.get(&key.draw.column_key).is_none_or(|asset| {
                    asset.column_generation != key.draw.column_generation
                        || asset
                            .segments
                            .get(key.draw.segment_index as usize)
                            .is_none()
                })
            })
            .cloned()
            .collect::<Vec<_>>();
        for key in stale {
            if let Some(resources) = self.draws.remove(&key) {
                resources.destroy(gal);
            }
        }
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.draws) {
            resources.destroy(gal);
        }
        self.retire_generic_draws(gal);
        // Draw sets bound these; they are gone now.
        for (_, buffer) in std::mem::take(&mut self.scalar_buffers) {
            let _ = gal.destroy(buffer);
        }
        for (_, ring) in std::mem::take(&mut self.column_frame_rings) {
            let _ = gal.destroy(ring.buffer);
        }
        self.scalar_written_this_frame.clear();
        for (_, set) in std::mem::take(&mut self.pack_resources) {
            let _ = gal.destroy(set);
        }
        for (_, resources) in std::mem::take(&mut self.targets) {
            let _ = gal.destroy(resources.pass);
            let _ = gal.destroy(resources.target);
        }
        // Generic-blend variants borrow their base layouts: destroy them first.
        let (shared, owned): (Vec<_>, Vec<_>) = std::mem::take(&mut self.pipelines)
            .into_values()
            .partition(|resources| resources.shares_base_layouts);
        for resources in shared.into_iter().chain(owned) {
            resources.destroy(gal);
        }
    }

    pub(super) fn ensure_pipeline(
        &mut self,
        gal: &mut VulkanicGal,
        program: &LoweredDistantHorizonsSourceProgram,
        key: &WorldLodSourceProgramKey,
    ) -> GalResult<()> {
        if self.pipelines.contains_key(key) {
            return Ok(());
        }
        if key.alpha_blend {
            let base_key = WorldLodSourceProgramKey {
                alpha_blend: false,
                ..key.clone()
            };
            self.ensure_pipeline(gal, program, &base_key)?;
            let base = self
                .pipelines
                .get(&base_key)
                .expect("base source DH pipeline exists after successful initialization");
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!(
                    "world-lod-source-{}-gen{}-generic.pipeline",
                    key.identity.replace(':', "-"),
                    key.shader_pack_generation
                ),
                layout: base.pipeline_layout,
                vertex_shader: base.vertex_shader,
                fragment_shader: base.fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: match key.cull_mode {
                    value if value == CullMode::None as u32 => CullMode::None,
                    value if value == CullMode::Front as u32 => CullMode::Front,
                    value if value == CullMode::Back as u32 => CullMode::Back,
                    _ => {
                        unreachable!("source DH pipeline cache key only permits a valid cull mode")
                    }
                },
                front_face: key.front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                // DH's GenericObjectRenderer: SRC_ALPHA/ONE_MINUS_SRC_ALPHA
                // (alpha ONE/ONE_MINUS_SRC_ALPHA), depth test and write on.
                blend: BlendMode::Alpha,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write: true,
                depth_bias: None,
                color_formats: vec![key.color_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            let shared = WorldLodSourcePipelineResources {
                vertex_shader: base.vertex_shader,
                fragment_shader: base.fragment_shader,
                source_data_layout: base.source_data_layout,
                pack_resources_layout: base.pack_resources_layout,
                pipeline_layout: base.pipeline_layout,
                pipeline,
                shares_base_layouts: true,
            };
            self.pipelines.insert(key.clone(), shared);
            return Ok(());
        }
        let layouts = program.execution_resource_layouts()?;
        let label = format!(
            "world-lod-source-{}-gen{}",
            key.identity.replace(':', "-"),
            key.shader_pack_generation
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<WorldLodSourcePipelineResources> {
            let source_data_layout = gal.create_resource_layout(layouts.source_data)?;
            created.push(source_data_layout);
            let pack_resources_layout = gal.create_resource_layout(layouts.pack_resources)?;
            created.push(pack_resources_layout);
            let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
                label: format!("{label}.pipeline-layout"),
                resource_layouts: vec![source_data_layout, pack_resources_layout],
            })?;
            created.push(pipeline_layout);
            let [vertex_desc, fragment_desc] =
                program.shader_module_descriptors(gal.capabilities().shader_conventions);
            let vertex_shader = gal.create_shader_module(vertex_desc)?;
            created.push(vertex_shader);
            let fragment_shader = gal.create_shader_module(fragment_desc)?;
            created.push(fragment_shader);
            let (blend, depth_write) = match (program.pass_kind, program.translucent_blend) {
                (DistantHorizonsPassKind::Opaque, None) => (BlendMode::Disabled, true),
                // DH's TRANSPARENT render state keeps depth writes on, so
                // Iris's dhDepthTex0 holds the water surface (composites
                // compare it with the pre-translucent dhDepthTex1).
                (DistantHorizonsPassKind::Translucent, Some(_)) => (BlendMode::Alpha, true),
                (DistantHorizonsPassKind::Opaque, Some(_)) => {
                    return Err(GalError::invalid_argument(
                        "opaque Distant Horizons source pipeline cannot carry translucent blend semantics",
                    ));
                }
                (DistantHorizonsPassKind::Translucent, None) => {
                    return Err(GalError::invalid_argument(
                        "translucent Distant Horizons source pipeline requires explicit source blend semantics",
                    ));
                }
            };
            let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
                label: format!("{label}.pipeline"),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: match key.cull_mode {
                    value if value == CullMode::None as u32 => CullMode::None,
                    value if value == CullMode::Front as u32 => CullMode::Front,
                    value if value == CullMode::Back as u32 => CullMode::Back,
                    _ => {
                        unreachable!("source DH pipeline cache key only permits a valid cull mode")
                    }
                },
                front_face: key.front_face,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend,
                depth_compare: Some(CompareOp::LessOrEqual),
                depth_write,
                depth_bias: None,
                color_formats: vec![key.color_format],
                depth_format: Some(TextureFormat::Depth32Float),
                stencil: None,
            })?;
            created.push(pipeline);
            Ok(WorldLodSourcePipelineResources {
                vertex_shader,
                fragment_shader,
                source_data_layout,
                pack_resources_layout,
                pipeline_layout,
                pipeline,
                shares_base_layouts: false,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        self.pipelines.insert(key.clone(), result?);
        Ok(())
    }
}

pub(super) fn append_source_uniform_upload(ops: &mut Vec<CommandOp>, buffer: Handle, data: Vec<u8>) {
    ops.push(CommandOp::Barrier(buffer_barrier(
        buffer,
        TextureUsageState::ShaderRead,
        TextureUsageState::TransferDst,
    )));
    ops.push(CommandOp::HostWriteBuffer {
        buffer,
        offset: 0,
        data,
    });
    ops.push(CommandOp::Barrier(buffer_barrier(
        buffer,
        TextureUsageState::TransferDst,
        TextureUsageState::ShaderRead,
    )));
}

/// Semantic identity for the Rust-owned distant-depth target consumed by a
/// future source-derived Distant Horizons stage. It deliberately contains no
/// frame target, native image, GL framebuffer, or backend handle: selected DH
/// source writes the shader pack's shared primary color target, while later
/// pack stages sample its distinct distant depth.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct WorldLodSourceTargetIdentity {
    pub world_generation: u64,
    pub shader_pack_generation: u64,
    pub extent: Extent3d,
}

impl WorldLodSourceTargetIdentity {
    pub(crate) fn validate(self) -> GalResult<()> {
        if self.world_generation == 0 || self.shader_pack_generation == 0 {
            return Err(GalError::invalid_argument(
                "Distant Horizons source target requires non-zero world and shader-pack generations",
            ));
        }
        if self.extent.width == 0 || self.extent.height == 0 || self.extent.depth != 1 {
            return Err(GalError::invalid_argument(
                "Distant Horizons source target requires a non-zero 2D extent",
            ));
        }
        Ok(())
    }
}

/// Backend-neutral handles owned wholly by Rust for one source-derived DH
/// depth generation. The source program and shared pack-color target are not
/// created or selected here.
#[derive(Clone, Copy, Debug)]
pub(crate) struct WorldLodSourceTargets {
    pub identity: WorldLodSourceTargetIdentity,
    /// Monotonic Rust-owned allocation generation. This distinguishes a
    /// replacement depth image from an earlier allocation with the same
    /// world, pack, and extent without exposing a native image identity.
    pub(in crate::render::worldrender::lod) resource_generation: u64,
    pub distant_depth_texture: Handle,
    pub distant_depth_view: Handle,
    pub(in crate::render::worldrender::lod) distant_depth_sampler: Handle,
    pub(in crate::render::worldrender::lod) distant_depth_combined_sampler: Handle,
    /// A depth-only pass used to establish the far-depth stream on a frame
    /// with no visible DH opaque ranges. It is an explicit semantic clear,
    /// never an alias of near-terrain depth.
    pub(in crate::render::worldrender::lod) distant_depth_clear_target: Handle,
    pub(in crate::render::worldrender::lod) distant_depth_clear_pass: Handle,
    /// A distinct snapshot of the opaque DH depth stream. Shader-pack stages
    /// may read it after the opaque pass while later work writes or samples
    /// the live target, so it must never alias `distant_depth_texture`.
    pub distant_depth_before_translucency_texture: Handle,
    pub distant_depth_before_translucency_view: Handle,
    pub(in crate::render::worldrender::lod) distant_depth_before_translucency_sampler: Handle,
    pub(in crate::render::worldrender::lod) distant_depth_before_translucency_combined_sampler: Handle,
}

impl WorldLodSourceTargets {
    pub(super) fn create(
        gal: &mut VulkanicGal,
        identity: WorldLodSourceTargetIdentity,
        resource_generation: u64,
    ) -> GalResult<Self> {
        identity.validate()?;
        if resource_generation == 0 {
            return Err(GalError::invalid_argument(
                "Distant Horizons source targets require a non-zero allocation generation",
            ));
        }
        let label = format!(
            "world-lod-source.world{}-pack{}-{}x{}",
            identity.world_generation,
            identity.shader_pack_generation,
            identity.extent.width,
            identity.extent.height,
        );
        let mut created = Vec::new();
        let result = (|| -> GalResult<Self> {
            let distant_depth_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.distant-depth.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: identity.extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::DepthStencilAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(distant_depth_texture);
            let distant_depth_view = gal.create_texture_view(TextureViewDesc {
                label: format!("{label}.distant-depth.view"),
                texture: distant_depth_texture,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })?;
            created.push(distant_depth_view);
            let distant_depth_sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.distant-depth.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(distant_depth_sampler);
            let distant_depth_combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label}.distant-depth.combined-sampler"),
                    texture_view: distant_depth_view,
                    sampler: distant_depth_sampler,
                })?;
            created.push(distant_depth_combined_sampler);
            let distant_depth_clear_target = gal.create_render_target(RenderTargetDesc {
                label: format!("{label}.distant-depth.clear-target"),
                color_views: Vec::new(),
                depth_stencil_view: Some(distant_depth_view),
                extent: identity.extent,
            })?;
            created.push(distant_depth_clear_target);
            let distant_depth_clear_pass = match gal.create_render_pass(RenderPassDesc {
                label: format!("{label}.distant-depth.clear-pass"),
                target: distant_depth_clear_target,
                color_formats: Vec::new(),
                depth_format: Some(TextureFormat::Depth32Float),
            }) {
                Ok(pass) => pass,
                Err(error) => return Err(error),
            };
            created.push(distant_depth_clear_pass);
            let distant_depth_before_translucency_texture = gal.create_texture(TextureDesc {
                label: format!("{label}.distant-depth-before-translucency.texture"),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: identity.extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::Sampled,
                    TextureUsage::TransferDst,
                    // Exact-frame diagnostics read the opaque snapshot, not
                    // the live depth subsequently changed by translucency.
                    TextureUsage::TransferSrc,
                ],
            })?;
            created.push(distant_depth_before_translucency_texture);
            let distant_depth_before_translucency_view =
                gal.create_texture_view(TextureViewDesc {
                    label: format!("{label}.distant-depth-before-translucency.view"),
                    texture: distant_depth_before_translucency_texture,
                    format: TextureFormat::Depth32Float,
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                })?;
            created.push(distant_depth_before_translucency_view);
            let distant_depth_before_translucency_sampler = gal.create_sampler(SamplerDesc {
                label: format!("{label}.distant-depth-before-translucency.sampler"),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })?;
            created.push(distant_depth_before_translucency_sampler);
            let distant_depth_before_translucency_combined_sampler = gal
                .create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label}.distant-depth-before-translucency.combined-sampler"),
                    texture_view: distant_depth_before_translucency_view,
                    sampler: distant_depth_before_translucency_sampler,
                })?;
            created.push(distant_depth_before_translucency_combined_sampler);
            Ok(Self {
                identity,
                resource_generation,
                distant_depth_texture,
                distant_depth_view,
                distant_depth_sampler,
                distant_depth_combined_sampler,
                distant_depth_clear_target,
                distant_depth_clear_pass,
                distant_depth_before_translucency_texture,
                distant_depth_before_translucency_view,
                distant_depth_before_translucency_sampler,
                distant_depth_before_translucency_combined_sampler,
            })
        })();
        if result.is_err() {
            for handle in created.into_iter().rev() {
                let _ = gal.destroy(handle);
            }
        }
        result
    }

    pub(super) fn destroy(self, gal: &mut VulkanicGal) {
        for handle in [
            self.distant_depth_before_translucency_combined_sampler,
            self.distant_depth_before_translucency_sampler,
            self.distant_depth_before_translucency_view,
            self.distant_depth_before_translucency_texture,
            self.distant_depth_clear_pass,
            self.distant_depth_clear_target,
            self.distant_depth_combined_sampler,
            self.distant_depth_sampler,
            self.distant_depth_view,
            self.distant_depth_texture,
        ] {
            let _ = gal.destroy(handle);
        }
    }

    /// Returns the two independently sampled DH depth streams as one
    /// generation-coherent semantic source-resource subset. This is still
    /// target ownership only: it selects no shader program and creates no
    /// render pass. Later source stages must merge this exact set with the
    /// pack and world resource sets before their own source declarations are
    /// admitted.
    pub(crate) fn semantic_resources(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        let generation = self.resource_generation;
        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.identity.shader_pack_generation,
            self.identity.world_generation,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::DistantHorizonsOpaqueDepth,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: generation,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency,
                    shape: TerrainSourceSampledResourceShape::Texture2d,
                    resource_generation: generation,
                },
            ],
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::DistantHorizonsOpaqueDepth,
                    combined_sampler: self.distant_depth_combined_sampler,
                },
                TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::DistantHorizonsDepthBeforeTranslucency,
                    combined_sampler: self.distant_depth_before_translucency_combined_sampler,
                },
            ],
        )
    }

    /// Appends the semantic opaque-depth snapshot boundary used by later DH
    /// source stages. The live depth target is expected to have completed its
    /// opaque attachment writes; both images finish shader-readable. No
    /// source program, native target, or route selection is implied here.
    pub(crate) fn append_opaque_depth_snapshot(&self, ops: &mut Vec<CommandOp>) {
        let extent = self.identity.extent;
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_before_translucency_texture,
            TextureUsageState::Undefined,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
            row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
            src_texture: self.distant_depth_texture,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: self.distant_depth_before_translucency_texture,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        }));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_texture,
            TextureUsageState::TransferSrc,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_before_translucency_texture,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
    }

    /// Establishes a valid far opaque-depth stream when no visible Distant
    /// Horizons opaque range is available for a source-preparation frame.
    /// The later shader-pack stages still receive their own cleared far-depth
    /// images, not the near-terrain depth attachment and not an undefined
    /// placeholder. Both sampled streams finish in `ShaderRead`.
    pub(crate) fn append_empty_opaque_depth_snapshot(
        &self,
        prior_usage: TextureUsageState,
        ops: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if !matches!(
            prior_usage,
            TextureUsageState::Undefined | TextureUsageState::ShaderRead
        ) {
            return Err(GalError::invalid_argument(
                "empty Distant Horizons source depth must begin undefined or shader-readable",
            ));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_texture,
            prior_usage,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: self.distant_depth_clear_pass,
            target: self.distant_depth_clear_target,
            colors: Vec::new(),
            depth_stencil: Some(PassAttachment {
                view: self.distant_depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::TransferSrc,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_before_translucency_texture,
            prior_usage,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::CopyTexture(TextureImageCopyRegion {
            row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
            src_texture: self.distant_depth_texture,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: self.distant_depth_before_translucency_texture,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: self.identity.extent,
        }));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_texture,
            TextureUsageState::TransferSrc,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            self.distant_depth_before_translucency_texture,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }
}

/// Two-phase target cache. A replacement becomes active only after the exact
/// submission using it succeeds; failed source-plan preparation cannot leak a
/// newly created target into a later world or shader-pack generation.
#[derive(Default)]
pub(crate) struct WorldLodSourceTargetCache {
    pub(in crate::render::worldrender::lod) active: Option<WorldLodSourceTargets>,
    pub(in crate::render::worldrender::lod) pending: Option<WorldLodSourceTargets>,
    pub(in crate::render::worldrender::lod) next_resource_generation: u64,
}

impl WorldLodSourceTargetCache {
    pub(crate) fn stage(
        &mut self,
        gal: &mut VulkanicGal,
        identity: WorldLodSourceTargetIdentity,
    ) -> GalResult<WorldLodSourceTargets> {
        identity.validate()?;
        if let Some(pending) = self.pending {
            if pending.identity != identity {
                return Err(GalError::backend(
                    "Distant Horizons source target replacement is awaiting submission confirmation",
                ));
            }
            return Ok(pending);
        }
        if let Some(active) = self.active {
            if active.identity == identity {
                return Ok(active);
            }
        }
        let targets = self.create_targets(gal, identity)?;
        self.pending = Some(targets);
        Ok(targets)
    }

    /// Stages the same owned source depth family while reporting the only
    /// valid usage that can precede a depth clear. A pending replacement is
    /// deliberately rejected here: one admission frame must own one clear
    /// and one later confirmation, rather than recording duplicate clears
    /// against the same unconfirmed targets.
    pub(crate) fn stage_for_empty_depth_snapshot(
        &mut self,
        gal: &mut VulkanicGal,
        identity: WorldLodSourceTargetIdentity,
    ) -> GalResult<(WorldLodSourceTargets, TextureUsageState, bool)> {
        identity.validate()?;
        if self.pending.is_some() {
            return Err(GalError::backend(
                "Distant Horizons source depth targets are already awaiting one admission submission",
            ));
        }
        if let Some(active) = self.active {
            if active.identity == identity {
                return Ok((active, TextureUsageState::ShaderRead, false));
            }
        }
        let targets = self.create_targets(gal, identity)?;
        self.pending = Some(targets);
        Ok((targets, TextureUsageState::Undefined, true))
    }

    pub(crate) fn confirm_submission(&mut self, gal: &mut VulkanicGal) {
        let Some(replacement) = self.pending.take() else {
            return;
        };
        if let Some(previous) = self.active.replace(replacement) {
            previous.destroy(gal);
        }
    }

    pub(crate) fn discard_submission(&mut self, gal: &mut VulkanicGal) {
        if let Some(pending) = self.pending.take() {
            pending.destroy(gal);
        }
    }

    /// Returns the confirmed semantic far-depth inputs only when they belong
    /// to this exact source frame compatibility identity. Callers receive no
    /// backend object identity and cannot reuse a stale world, pack, or extent.
    pub(crate) fn active_semantic_resources(
        &self,
        identity: WorldLodSourceTargetIdentity,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        identity.validate()?;
        self.active
            .filter(|targets| targets.identity == identity)
            .map(|targets| targets.semantic_resources())
            .transpose()
    }

    pub(crate) fn destroy(&mut self, gal: &mut VulkanicGal) {
        self.discard_submission(gal);
        if let Some(active) = self.active.take() {
            active.destroy(gal);
        }
    }

    pub(super) fn create_targets(
        &mut self,
        gal: &mut VulkanicGal,
        identity: WorldLodSourceTargetIdentity,
    ) -> GalResult<WorldLodSourceTargets> {
        self.next_resource_generation =
            self.next_resource_generation
                .checked_add(1)
                .ok_or_else(|| {
                    GalError::backend("Distant Horizons source target generation overflow")
                })?;
        WorldLodSourceTargets::create(gal, identity, self.next_resource_generation)
    }

    #[cfg(test)]
    pub(crate) fn active_identity(&self) -> Option<WorldLodSourceTargetIdentity> {
        self.active.map(|targets| targets.identity)
    }
}

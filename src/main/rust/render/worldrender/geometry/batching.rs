//! Batching of meshes, materials, lines, cracks and borders, and packing of their uniforms and draw streams.

use crate::render::worldrender::*;

pub(in crate::render::worldrender) fn material_mode_uses_alpha_blending(mode: u32) -> bool {
    matches!(
        mode,
        WORLD_MATERIAL_MODE_TRANSLUCENT | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
    )
}

pub(in crate::render::worldrender) const WORLD_LINE_HEADER_BYTES: usize = 144;

pub(in crate::render::worldrender) const WORLD_LINE_SEGMENT_BYTES: usize = 48;

pub(in crate::render::worldrender) const WORLD_LINE_UNIFORM_BYTES: u64 =
    (WORLD_LINE_HEADER_BYTES + WORLD_MAX_LINE_SEGMENTS * WORLD_LINE_SEGMENT_BYTES) as u64;

pub(in crate::render::worldrender) const WORLD_CRACK_HEADER_BYTES: usize = 144;

pub(in crate::render::worldrender) const WORLD_CRACK_QUAD_BYTES: usize = 96;

pub(in crate::render::worldrender) const WORLD_CRACK_UNIFORM_BYTES: u64 =
    (WORLD_CRACK_HEADER_BYTES + WORLD_MAX_CRACK_QUADS * WORLD_CRACK_QUAD_BYTES) as u64;

pub(in crate::render::worldrender) const WORLD_BORDER_HEADER_BYTES: usize = 144;

pub(in crate::render::worldrender) const WORLD_BORDER_QUAD_BYTES: usize = 112;

pub(in crate::render::worldrender) const WORLD_BORDER_UNIFORM_BYTES: u64 =
    (WORLD_BORDER_HEADER_BYTES + WORLD_MAX_BORDER_QUADS * WORLD_BORDER_QUAD_BYTES) as u64;

pub(in crate::render::worldrender) const WORLD_MATERIAL_HEADER_BYTES: usize = 144;
pub(in crate::render::worldrender) const WORLD_SKY_MATERIAL_HEADER_BYTES: usize = 160;

// Four copied lightmap coordinate pairs retain the source UV2 semantic for
// material families such as weather.  Keeping this in the shared explicit
// quad ABI avoids a Java-side lighting policy while allowing only the
// material programs which declare a lightmap contract to consume it.
pub(in crate::render::worldrender) const WORLD_MATERIAL_QUAD_BYTES: usize = 192;

pub(in crate::render::worldrender) const WORLD_MATERIAL_UNIFORM_BYTES: u64 = (WORLD_SKY_MATERIAL_HEADER_BYTES
    + WORLD_MAX_MATERIAL_QUADS_PER_BATCH * WORLD_MATERIAL_QUAD_BYTES)
    as u64;

pub(in crate::render::worldrender) const WORLD_MATERIAL_INDEX_BYTES: u64 = 6 * 4;

pub(in crate::render::worldrender) const WORLD_MESH_GPU_VERTEX_BYTES: usize = 5 * 4 * 4;

pub(in crate::render::worldrender) const WORLD_MESH_DIRECT_TERRAIN_VERTEX_BYTES: usize = 2 * 4 * 4;

pub(in crate::render::worldrender) const WORLD_MESH_BATCH_HEADER_BYTES: usize = 16 * 4 + 16 * 4 + 16 * 4 + 4 * 4 + 4 * 4 + 4 * 4;

pub(in crate::render::worldrender) const WORLD_MESH_INSTANCE_BYTES: usize = 16 * 4 + 6 * 4 * 4;

pub(in crate::render::worldrender) const WORLD_MESH_INSTANCE_BUFFER_BYTES: u64 =
    (WORLD_MESH_BATCH_HEADER_BYTES + WORLD_MAX_MESH_INSTANCES * WORLD_MESH_INSTANCE_BYTES) as u64;

pub(in crate::render::worldrender) const WORLD_MESH_INSTANCE_STREAM_ALIGNMENT: usize = 256;

pub(in crate::render::worldrender) const WORLD_MESH_INSTANCE_STREAM_BINDING_RANGE_BYTES: u64 = WORLD_MESH_INSTANCE_BUFFER_BYTES;

/// Immutable streamed mesh generations share explicitly range-addressed GAL
/// buffers.  This bounds native buffer-object churn without asking a backend
/// to infer ownership or reconstruct a hidden mesh heap.
pub(in crate::render::worldrender) const WORLD_MESH_GEOMETRY_PAGE_BYTES: u64 = 16 * 1024 * 1024;

// Page-relative indexed draws address vertices in whole records while storage
// descriptors retain Vulkan's 256-byte dynamic-offset compatibility. Keep the
// existing conservative page alignment; it is divisible by both the 32-byte
// direct-terrain and 80-byte rich vertex records.
pub(in crate::render::worldrender) const WORLD_MESH_GEOMETRY_ALIGNMENT: u64 = 3_840;

pub(in crate::render::worldrender) const WORLD_MESH_INDEXED_INDIRECT_COMMAND_BYTES: u64 = 20;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct LineBatch {
    pub(in crate::render::worldrender) start: usize,
    pub(in crate::render::worldrender) count: usize,
    pub(in crate::render::worldrender) depth_policy: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct CrackBatch {
    pub(in crate::render::worldrender) start: usize,
    pub(in crate::render::worldrender) count: usize,
    pub(in crate::render::worldrender) depth_policy: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct BorderBatch {
    pub(in crate::render::worldrender) start: usize,
    pub(in crate::render::worldrender) count: usize,
    pub(in crate::render::worldrender) depth_policy: u32,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct MaterialBatch {
    pub(in crate::render::worldrender) key: MaterialResourceKey,
    pub(in crate::render::worldrender) indices: Vec<usize>,
}

#[derive(Clone, Debug)]
pub(in crate::render::worldrender) struct DistantHorizonsGenericBoxBatch {
    pub(in crate::render::worldrender) key: MaterialResourceKey,
    pub(in crate::render::worldrender) indices: Vec<usize>,
}

impl DistantHorizonsGenericBoxBatch {
    pub(in crate::render::worldrender) fn face_count(&self) -> u32 {
        (self.indices.len() * 6) as u32
    }
}

/// Preserves semantic source-material ordering while grouping only adjacent
/// quads with identical raster and semantic texture bindings. A local
/// material texture remains a Rust-owned source resource; it is never an
/// atlas object, texture unit, or backend handle in the semantic frame.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct SourceTexturedMaterialBatch {
    pub(in crate::render::worldrender) start: usize,
    pub(in crate::render::worldrender) count: usize,
    pub(in crate::render::worldrender) texture_id: u32,
    pub(in crate::render::worldrender) source_uv_space: u32,
    pub(in crate::render::worldrender) material_mode: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) block_entity_id: i32,
}

impl SourceTexturedMaterialBatch {
    pub(in crate::render::worldrender) fn indices(self) -> std::ops::Range<usize> {
        self.start..self.start + self.count
    }
}

impl MaterialBatch {
    pub(in crate::render::worldrender) fn count(&self) -> usize {
        self.indices.len()
    }
}

#[derive(Clone)]
pub(in crate::render::worldrender) struct MeshBatch {
    pub(in crate::render::worldrender) model_submission_order: Option<i32>,
    pub(in crate::render::worldrender) key: MeshResourceKey,
    pub(in crate::render::worldrender) index_offset: u64,
    pub(in crate::render::worldrender) index_count: u32,
    /// Frame-local index bytes in camera order. The immutable mesh index
    /// buffer remains the source for every ordinary and source-program draw.
    pub(in crate::render::worldrender) sorted_index_offset: Option<u64>,
    /// Most visible world mesh batches contain one instance. Keep that common
    /// case inline so rebuilding the semantic batch view does not allocate a
    /// heap vector per section; larger batches spill to the same SmallVec
    /// storage without changing ordering or draw semantics.
    pub(in crate::render::worldrender) indices: SmallVec<[usize; 4]>,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct PageIndexedDrawCommand {
    pub(in crate::render::worldrender) index_count: u32,
    pub(in crate::render::worldrender) instance_count: u32,
    pub(in crate::render::worldrender) first_index: u32,
    pub(in crate::render::worldrender) vertex_offset: i32,
    pub(in crate::render::worldrender) first_instance: u32,
}

impl PageIndexedDrawCommand {
    pub(in crate::render::worldrender) fn append_bytes(self, out: &mut Vec<u8>) {
        out.extend_from_slice(&self.index_count.to_le_bytes());
        out.extend_from_slice(&self.instance_count.to_le_bytes());
        out.extend_from_slice(&self.first_index.to_le_bytes());
        out.extend_from_slice(&self.vertex_offset.to_le_bytes());
        out.extend_from_slice(&self.first_instance.to_le_bytes());
    }
}

pub(in crate::render::worldrender) struct PendingMeshDraw {
    pub(in crate::render::worldrender) draw: TerrainMeshDraw,
    pub(in crate::render::worldrender) page_command: Option<PageIndexedDrawCommand>,
    pub(in crate::render::worldrender) front_to_back_distance_squared: f32,
}

/// Groups only adjacent page-addressed draws inside one opaque/cutout phase.
/// Non-page draws are hard ordering boundaries, so entities, authored models,
/// foil and translucent work cannot move. The command payload is emitted only
/// after this ordering step, which keeps compatible records contiguous for
/// backend-neutral indexed multidraw without changing semantic frame data.
pub(in crate::render::worldrender) fn order_compatible_page_indirect_draws(draws: &mut [PendingMeshDraw]) {
    let mut start = 0usize;
    while start < draws.len() {
        if draws[start].page_command.is_none() {
            start += 1;
            continue;
        }
        let mode = draws[start].draw.material_mode;
        let mut end = start + 1;
        while end < draws.len()
            && draws[end].page_command.is_some()
            && draws[end].draw.material_mode == mode
        {
            end += 1;
        }
        // Camera-sorted translucent batches carry an exact back-to-front
        // order. Page addressing changes their bindings, never their order.
        if matches!(
            mode,
            TerrainMaterialPassMode::Opaque | TerrainMaterialPassMode::Cutout
        ) {
            sort_page_run(&mut draws[start..end]);
        }
        start = end;
    }
}

/// The fields `page_indirect_draw_order` compares, in its order.
type PageDrawKey = (
    Handle,
    Handle,
    Handle,
    Option<(u32, Handle)>,
    Handle,
    u32,
    Option<(Handle, Handle, Handle)>,
    Option<(u32, Handle)>,
);

fn page_draw_key(draw: &PendingMeshDraw) -> PageDrawKey {
    let draw = &draw.draw;
    let shadow = draw.shadow.as_ref();
    (
        draw.pipeline,
        draw.pipeline_layout,
        draw.resource_set,
        draw.shader_resource_set.map(|binding| (binding.set_index, binding.set)),
        draw.index_buffer,
        draw.index_type as u32,
        shadow.map(|shadow| (shadow.pipeline, shadow.pipeline_layout, shadow.resource_set)),
        shadow
            .and_then(|shadow| shadow.shader_resource_set)
            .map(|binding| (binding.set_index, binding.set)),
    )
}

/// Same order as a stable sort by `page_indirect_draw_order` then distance,
/// but sorts compact keys and moves each (large) draw along one permutation.
fn sort_page_run(draws: &mut [PendingMeshDraw]) {
    let mut keys: Vec<(PageDrawKey, f32, u32)> = draws
        .iter()
        .enumerate()
        .map(|(index, draw)| (page_draw_key(draw), draw.front_to_back_distance_squared, index as u32))
        .collect();
    keys.sort_unstable_by(|left, right| {
        left.0
            .cmp(&right.0)
            .then_with(|| left.1.total_cmp(&right.1))
            .then_with(|| left.2.cmp(&right.2))
    });
    // Position i receives the draw originally at keys[i].2. Apply the
    // permutation in place by following each cycle with swaps.
    let mut source: Vec<u32> = keys.into_iter().map(|key| key.2).collect();
    for start in 0..source.len() {
        let mut current = start;
        while source[current] as usize != start {
            let next = source[current] as usize;
            draws.swap(current, next);
            source[current] = current as u32;
            current = next;
        }
        source[current] = current as u32;
    }
}

pub(in crate::render::worldrender) fn page_indirect_draw_order(left: &PendingMeshDraw, right: &PendingMeshDraw) -> std::cmp::Ordering {
    let left_draw = &left.draw;
    let right_draw = &right.draw;
    let left_shadow = left_draw.shadow.as_ref();
    let right_shadow = right_draw.shadow.as_ref();
    left_draw
        .pipeline
        .cmp(&right_draw.pipeline)
        .then_with(|| left_draw.pipeline_layout.cmp(&right_draw.pipeline_layout))
        .then_with(|| left_draw.resource_set.cmp(&right_draw.resource_set))
        .then_with(|| {
            left_draw
                .shader_resource_set
                .map(|binding| (binding.set_index, binding.set))
                .cmp(
                    &right_draw
                        .shader_resource_set
                        .map(|binding| (binding.set_index, binding.set)),
                )
        })
        .then_with(|| left_draw.index_buffer.cmp(&right_draw.index_buffer))
        .then_with(|| (left_draw.index_type as u32).cmp(&(right_draw.index_type as u32)))
        .then_with(|| {
            left_shadow
                .map(|shadow| (shadow.pipeline, shadow.pipeline_layout, shadow.resource_set))
                .cmp(
                    &right_shadow.map(|shadow| {
                        (shadow.pipeline, shadow.pipeline_layout, shadow.resource_set)
                    }),
                )
        })
        .then_with(|| {
            left_shadow
                .and_then(|shadow| shadow.shader_resource_set)
                .map(|binding| (binding.set_index, binding.set))
                .cmp(
                    &right_shadow
                        .and_then(|shadow| shadow.shader_resource_set)
                        .map(|binding| (binding.set_index, binding.set)),
                )
        })
}

/// Semantic identity for the part of a mesh instance that affects batch
/// topology.  Per-frame transforms, colours, and foil clocks deliberately do
/// not participate: they are written through the instance stream after the
/// cached topology is selected.
#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct MeshBatchInstanceKey {
    pub(in crate::render::worldrender) stratum: u32,
    pub(in crate::render::worldrender) mesh_key: u64,
    pub(in crate::render::worldrender) mesh_generation: u64,
    pub(in crate::render::worldrender) mesh_section_index: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) cull_policy: u32,
    pub(in crate::render::worldrender) winding: u32,
    pub(in crate::render::worldrender) flags: u32,
    pub(in crate::render::worldrender) terrain_visible_facing_mask: u8,
    pub(in crate::render::worldrender) model_submission_order: Option<i32>,
    pub(in crate::render::worldrender) standard_foil_kind: Option<crate::render::shared::item_foil::StandardFoilKind>,
    pub(in crate::render::worldrender) has_decal_foil: bool,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct MeshBatchPlanKey {
    pub(in crate::render::worldrender) color_format: ColorFormat,
    pub(in crate::render::worldrender) raster_y_direction: RasterYDirection,
    pub(in crate::render::worldrender) g_buffer: bool,
    pub(in crate::render::worldrender) allow_optical: bool,
    pub(in crate::render::worldrender) selection: MeshBatchSelection,
    /// Only source-terrain strata are batched; other instances are masked in
    /// `instances` so their per-frame churn does not invalidate the plan.
    pub(in crate::render::worldrender) terrain_only: bool,
    pub(in crate::render::worldrender) instances: Vec<MeshBatchInstanceKey>,
    /// Explicit original frame positions for a policy-selected subset.
    pub(in crate::render::worldrender) instance_indices: Option<Vec<usize>>,
}

pub(in crate::render::worldrender) struct MeshBatchPlanCacheEntry {
    pub(in crate::render::worldrender) key: MeshBatchPlanKey,
    pub(in crate::render::worldrender) batches: Arc<Vec<MeshBatch>>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) struct MaterialBatchPlanKey {
    pub(in crate::render::worldrender) color_format: ColorFormat,
    pub(in crate::render::worldrender) raster_y_direction: RasterYDirection,
    pub(in crate::render::worldrender) identities: Vec<MaterialResourceKey>,
}

pub(in crate::render::worldrender) struct MaterialBatchPlanCacheEntry {
    pub(in crate::render::worldrender) key: MaterialBatchPlanKey,
    pub(in crate::render::worldrender) batches: Arc<Vec<MaterialBatch>>,
}

/// Bounded selected-source audit evidence. This is deliberately calculated
/// from the shared frontend batches and the source draw records before command
/// recording so a diagnostic capture can distinguish missing semantic work
/// from a later raster or fragment-stage failure.
#[derive(Clone, Copy, Debug, Default)]
pub(in crate::render::worldrender) struct SourceTerrainDrawCoverage {
    pub(in crate::render::worldrender) opaque_batches: u64,
    pub(in crate::render::worldrender) cutout_batches: u64,
    pub(in crate::render::worldrender) opaque_instances: u64,
    pub(in crate::render::worldrender) cutout_instances: u64,
    pub(in crate::render::worldrender) opaque_indices: u64,
    pub(in crate::render::worldrender) cutout_indices: u64,
    pub(in crate::render::worldrender) opaque_draws: u64,
    pub(in crate::render::worldrender) cutout_draws: u64,
    pub(in crate::render::worldrender) opaque_draw_indices: u64,
    pub(in crate::render::worldrender) cutout_draw_indices: u64,
    pub(in crate::render::worldrender) translucent_batches: u64,
    pub(in crate::render::worldrender) translucent_instances: u64,
    pub(in crate::render::worldrender) translucent_indices: u64,
    pub(in crate::render::worldrender) translucent_draws: u64,
    pub(in crate::render::worldrender) translucent_draw_indices: u64,
}

impl SourceTerrainDrawCoverage {
    /// The batch path's share: removes the indices the scene path drew,
    /// which have no batches to prove them (the scene covers them by
    /// construction).
    pub(in crate::render::worldrender) fn without_scene(mut self, scene: SceneTerrainCoverage) -> Self {
        self.opaque_draw_indices = self.opaque_draw_indices.saturating_sub(scene.indices[0]);
        self.cutout_draw_indices = self.cutout_draw_indices.saturating_sub(scene.indices[1]);
        self.translucent_draw_indices = self.translucent_draw_indices.saturating_sub(scene.indices[2]);
        self
    }

    pub(in crate::render::worldrender) fn from_batches_and_draws(batches: &[MeshBatch], draws: &[TerrainMeshDraw]) -> Self {
        let mut coverage = Self::default();
        for batch in batches {
            if !is_source_terrain_mesh_stratum(batch.key.stratum) {
                continue;
            }
            let (batch_count, instance_count, index_count) = match batch.key.material_mode {
                WORLD_MATERIAL_MODE_OPAQUE => (
                    &mut coverage.opaque_batches,
                    &mut coverage.opaque_instances,
                    &mut coverage.opaque_indices,
                ),
                WORLD_MATERIAL_MODE_CUTOUT => (
                    &mut coverage.cutout_batches,
                    &mut coverage.cutout_instances,
                    &mut coverage.cutout_indices,
                ),
                WORLD_MATERIAL_MODE_TRANSLUCENT => (
                    &mut coverage.translucent_batches,
                    &mut coverage.translucent_instances,
                    &mut coverage.translucent_indices,
                ),
                _ => continue,
            };
            *batch_count = batch_count.saturating_add(1);
            *instance_count = instance_count.saturating_add(batch.indices.len() as u64);
            *index_count = index_count.saturating_add(batch.index_count as u64);
        }
        for draw in draws {
            match draw.material_mode {
                TerrainMaterialPassMode::Opaque => {
                    coverage.opaque_draws = coverage.opaque_draws.saturating_add(1);
                    coverage.opaque_draw_indices = coverage
                        .opaque_draw_indices
                        .saturating_add(draw.index_count as u64);
                }
                TerrainMaterialPassMode::Cutout => {
                    coverage.cutout_draws = coverage.cutout_draws.saturating_add(1);
                    coverage.cutout_draw_indices = coverage
                        .cutout_draw_indices
                        .saturating_add(draw.index_count as u64);
                }
                TerrainMaterialPassMode::Translucent => {
                    coverage.translucent_draws = coverage.translucent_draws.saturating_add(1);
                    coverage.translucent_draw_indices = coverage
                        .translucent_draw_indices
                        .saturating_add(draw.index_count as u64);
                }
            }
        }
        coverage
    }
}

impl MeshBatch {
    pub(in crate::render::worldrender) fn count(&self) -> usize {
        self.indices.len()
    }
}

pub(in crate::render::worldrender) fn line_batches(frame: &WorldPrimitiveFrame) -> Vec<LineBatch> {
    let mut batches = Vec::new();
    let Some(first) = frame.segments.first() else {
        return batches;
    };
    let mut current = LineBatch {
        start: 0,
        count: 0,
        depth_policy: first.depth_policy,
    };
    for (index, segment) in frame.segments.iter().enumerate() {
        if current.count > 0 && segment.depth_policy != current.depth_policy {
            batches.push(current);
            current = LineBatch {
                start: index,
                count: 0,
                depth_policy: segment.depth_policy,
            };
        }
        current.count += 1;
    }
    if current.count > 0 {
        batches.push(current);
    }
    batches
}

pub(in crate::render::worldrender) fn crack_batches(frame: &WorldPrimitiveFrame) -> Vec<CrackBatch> {
    let mut batches = Vec::new();
    let Some(first) = frame.crack_quads.first() else {
        return batches;
    };
    let mut current = CrackBatch {
        start: 0,
        count: 0,
        depth_policy: first.depth_policy,
    };
    for (index, quad) in frame.crack_quads.iter().enumerate() {
        if current.count > 0 && quad.depth_policy != current.depth_policy {
            batches.push(current);
            current = CrackBatch {
                start: index,
                count: 0,
                depth_policy: quad.depth_policy,
            };
        }
        current.count += 1;
    }
    if current.count > 0 {
        batches.push(current);
    }
    batches
}

pub(in crate::render::worldrender) fn border_batches(frame: &WorldPrimitiveFrame) -> Vec<BorderBatch> {
    let mut batches = Vec::new();
    let Some(first) = frame.border_quads.first() else {
        return batches;
    };
    let mut current = BorderBatch {
        start: 0,
        count: 0,
        depth_policy: first.depth_policy,
    };
    for (index, quad) in frame.border_quads.iter().enumerate() {
        if current.count > 0 && quad.depth_policy != current.depth_policy {
            batches.push(current);
            current = BorderBatch {
                start: index,
                count: 0,
                depth_policy: quad.depth_policy,
            };
        }
        current.count += 1;
    }
    if current.count > 0 {
        batches.push(current);
    }
    batches
}

/// Resolves the copied vanilla sampler wrap policy for source-local material
/// textures. Weather and projected crumbling coordinates deliberately extend
/// beyond `[0, 1]`; Frozen loads those PNGs without clamp metadata, so both
/// axes must repeat.
/// Keeping this identity policy beside material resource creation prevents the
/// private Vulkan sampler from inheriting an unrelated generic-local-texture
/// default.
/// Frozen uses core/particle for both ordinary particles and weather. The
/// lightmap is an explicit Rust-owned resource, never a borrowed GPU binding.
pub(in crate::render::worldrender) fn material_uses_particle_shader(source_program: u32) -> bool {
    matches!(
        source_program,
        WORLD_MATERIAL_SOURCE_WEATHER | WORLD_MATERIAL_SOURCE_PARTICLES
    )
}

pub(in crate::render::worldrender) fn append_private_dh_draws(draws: &[TerrainMeshDraw], ops: &mut Vec<CommandOp>) -> GalResult<()> {
    // This helper emits one uninterrupted private-target pass. Keep its bound
    // pipeline and lightmap state locally so repeated DH segments do not
    // manufacture commands that GAL would discard during normalization.
    let mut bound_pipeline = None;
    let mut bound_shader_set = None;
    for draw in draws {
        let pipeline = draw.offscreen_pipeline.ok_or_else(|| {
            GalError::backend("direct DH draw has no private offscreen pipeline variant")
        })?;
        if bound_pipeline != Some((pipeline, draw.pipeline_layout)) {
            ops.push(CommandOp::BindGraphicsPipeline(pipeline));
            bound_pipeline = Some((pipeline, draw.pipeline_layout));
            bound_shader_set = None;
        }
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: draw.pipeline_layout,
            set_index: 0,
            set: draw.resource_set,
            dynamic_offsets: draw.resource_set_dynamic_offsets.to_vec(),
        });
        if let Some(shader_resource_set) = draw.shader_resource_set {
            let shader_set = (
                draw.pipeline_layout,
                shader_resource_set.set_index,
                shader_resource_set.set,
            );
            if shader_resource_set.set_index == 0 || bound_shader_set != Some(shader_set) {
                ops.push(CommandOp::BindResourceSet {
                    pipeline_layout: draw.pipeline_layout,
                    set_index: shader_resource_set.set_index,
                    set: shader_resource_set.set,
                    dynamic_offsets: Vec::new(),
                });
                bound_shader_set = Some(shader_set);
            }
        }
        ops.push(CommandOp::SetIndexBuffer {
            buffer: draw.index_buffer,
            offset: draw.index_offset,
            index_type: draw.index_type,
        });
        ops.push(CommandOp::DrawIndexed {
            indices: draw.index_count,
            instances: draw.instance_count,
        });
    }
    Ok(())
}

pub(in crate::render::worldrender) fn material_uses_lightmap(key: MaterialResourceKey) -> bool {
    is_distant_horizons_generic_stratum(key.stratum)
        || material_uses_particle_shader(key.source_program)
}

pub(in crate::render::worldrender) fn is_distant_horizons_generic_stratum(stratum: u32) -> bool {
    matches!(
        stratum,
        WORLD_STRATUM_DH_GENERIC | WORLD_STRATUM_DH_GENERIC_SSAO
    )
}

pub(in crate::render::worldrender) fn material_sampler_address_mode(material_id: u32, texture_id: u32) -> SamplerAddressMode {
    if material_id == WORLD_MATERIAL_ID_MODEL_CRUMBLING {
        return SamplerAddressMode::Repeat;
    }
    match texture_id {
        WORLD_MATERIAL_TEXTURE_END_SKY
        | WORLD_MATERIAL_TEXTURE_WEATHER_RAIN
        | WORLD_MATERIAL_TEXTURE_WEATHER_SNOW => SamplerAddressMode::Repeat,
        _ => SamplerAddressMode::ClampToEdge,
    }
}

/// Java's weather `TextureStateShard` deliberately declares `mipmap=false`.
/// The copied rain and snow images are therefore sampled exclusively from mip
/// zero in Frozen.  Keep that source policy explicit in the Rust-owned
/// resource description instead of silently allocating a derivative-selected
/// mip chain for the weather stream.
pub(in crate::render::worldrender) fn material_texture_mip_level_count(source_program: u32, width: u32, height: u32) -> u32 {
    if source_program == WORLD_MATERIAL_SOURCE_WEATHER {
        1
    } else {
        texture_mip_level_count(width, height)
    }
}

pub(in crate::render::worldrender) fn material_batches(
    frame: &WorldPrimitiveFrame,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
) -> Vec<MaterialBatch> {
    let mut batches: Vec<MaterialBatch> = Vec::new();
    // Batch order is the first-seen order in `batches`; the lookup table does
    // not contribute ordering semantics. A hash index keeps this per-frame
    // grouping off the O(log n) tree path used by large material streams.
    let mut key_to_batch = HashMap::<MaterialResourceKey, usize>::with_capacity(
        frame
            .material_quads
            .len()
            .min(WORLD_MAX_MATERIAL_QUADS_PER_BATCH),
    );
    for (index, quad) in frame.material_quads.iter().enumerate() {
        if is_distant_horizons_generic_stratum(quad.stratum) {
            continue;
        }
        let key = material_key(quad, color_format, raster_y_direction);
        // Clouds use ordinary source-alpha composition.  Their decoded face
        // stream is therefore part of the semantic result: re-grouping an
        // earlier cloud face after a later one changes overlapping interior
        // and exterior pixels. Frozen emits one ordered cloud stream, so keep
        // only adjacent compatible cloud faces together. Other material
        // families retain their established resource-key batching.
        if key.source_program == WORLD_MATERIAL_SOURCE_CLOUDS {
            if let Some(previous) = batches.last_mut().filter(|batch| {
                batch.key == key && batch.count() < WORLD_MAX_MATERIAL_QUADS_PER_BATCH
            }) {
                previous.indices.push(index);
            } else {
                batches.push(MaterialBatch {
                    key,
                    indices: vec![index],
                });
            }
            continue;
        }
        let reusable_batch = key_to_batch.get(&key).copied().filter(|batch_index| {
            batches[*batch_index].count() < WORLD_MAX_MATERIAL_QUADS_PER_BATCH
        });
        if let Some(batch_index) = reusable_batch {
            batches[batch_index].indices.push(index);
        } else {
            let batch_index = batches.len();
            key_to_batch.insert(key, batch_index);
            batches.push(MaterialBatch {
                key,
                indices: vec![index],
            });
        }
    }
    batches
}

pub(in crate::render::worldrender) fn distant_horizons_generic_material_batches(
    frame: &WorldPrimitiveFrame,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
) -> Vec<MaterialBatch> {
    let mut batches = Vec::<MaterialBatch>::new();
    let mut key_to_batch = HashMap::<MaterialResourceKey, usize>::new();
    for (index, quad) in frame.material_quads.iter().enumerate() {
        if !is_distant_horizons_generic_stratum(quad.stratum) {
            continue;
        }
        let key = material_key(quad, color_format, raster_y_direction);
        if let Some(batch_index) = key_to_batch.get(&key).copied().filter(|batch_index| {
            batches[*batch_index].count() < WORLD_MAX_MATERIAL_QUADS_PER_BATCH
        }) {
            batches[batch_index].indices.push(index);
        } else {
            let batch_index = batches.len();
            key_to_batch.insert(key, batch_index);
            batches.push(MaterialBatch {
                key,
                indices: vec![index],
            });
        }
    }
    batches
}

pub(in crate::render::worldrender) fn distant_horizons_generic_box_batches(
    frame: &WorldPrimitiveFrame,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
) -> Vec<DistantHorizonsGenericBoxBatch> {
    let mut batches = Vec::<DistantHorizonsGenericBoxBatch>::new();
    let mut key_to_batch = HashMap::<MaterialResourceKey, usize>::new();
    for (index, item) in frame.dh_generic_boxes.iter().enumerate() {
        let translucent = item.color_argb >> 24 != 0xff;
        let key = MaterialResourceKey {
            raster_y_direction,
            compact_dh_box: true,
            stratum: if item.ssao_enabled {
                WORLD_STRATUM_DH_GENERIC_SSAO
            } else {
                WORLD_STRATUM_DH_GENERIC
            },
            material_id: if translucent {
                WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED
            } else {
                WORLD_MATERIAL_ID_OPAQUE_TEXTURED
            },
            texture_id: WORLD_MATERIAL_TEXTURE_GENERATED_WHITE,
            source_program: WORLD_MATERIAL_SOURCE_TEXTURED,
            material_mode: if translucent {
                WORLD_MATERIAL_MODE_TRANSLUCENT
            } else {
                WORLD_MATERIAL_MODE_OPAQUE
            },
            depth_policy: WORLD_DEPTH_POLICY_TEST_WRITE,
            cull_policy: WORLD_CULL_BACK,
            winding: WORLD_WINDING_CCW,
            color_format,
        };
        let reusable = key_to_batch.get(&key).copied().filter(|batch_index| {
            batches[*batch_index].indices.len() < WORLD_MAX_MATERIAL_QUADS_PER_BATCH
        });
        if let Some(batch_index) = reusable {
            batches[batch_index].indices.push(index);
        } else {
            key_to_batch.insert(key, batches.len());
            batches.push(DistantHorizonsGenericBoxBatch {
                key,
                indices: vec![index],
            });
        }
    }
    batches
}

pub(in crate::render::worldrender) fn material_batch_plan_key(
    frame: &WorldPrimitiveFrame,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
) -> MaterialBatchPlanKey {
    MaterialBatchPlanKey {
        color_format,
        raster_y_direction,
        identities: frame
            .material_quads
            .iter()
            .map(|quad| material_key(quad, color_format, raster_y_direction))
            .collect(),
    }
}

/// Disable only the reusable direct-material grouping plan for a controlled
/// performance comparison. The default path remains optimized; this switch
/// does not admit a fallback presenter or alter any semantic/rendering policy.
pub(in crate::render::worldrender) fn material_batch_plan_cache_disabled() -> bool {
    static DISABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *DISABLED.get_or_init(|| matches!(
        crate::core::environment::var("MATTMC_RUST_DISABLE_WORLD_MATERIAL_BATCH_PLAN_CACHE")
            .ok()
            .as_deref(),
        Some("1") | Some("true") | Some("TRUE")
    ))
}

pub(in crate::render::worldrender) fn material_depth_and_cull(quad: &WorldMaterialQuadRequest) -> (u32, u32) {
    // Frozen's ordinary opaque/translucent particle pipelines retain both
    // depth writing and back-face culling. The source-family contract belongs
    // in this Rust frontend, before explicit GAL state is constructed; it is
    // not inferred from blending or a transitional Java raster-state guess.
    if quad.source_program == WORLD_MATERIAL_SOURCE_PARTICLES {
        (WORLD_DEPTH_POLICY_TEST_WRITE, WORLD_CULL_BACK)
    } else {
        (quad.depth_policy, quad.cull_policy)
    }
}

pub(in crate::render::worldrender) fn material_key(
    quad: &WorldMaterialQuadRequest,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
) -> MaterialResourceKey {
    let (depth_policy, cull_policy) = material_depth_and_cull(quad);
    MaterialResourceKey {
        raster_y_direction,
        compact_dh_box: false,
        stratum: quad.stratum,
        material_id: quad.material_id,
        texture_id: quad.texture_id,
        source_program: quad.source_program,
        material_mode: quad.material_mode,
        depth_policy,
        cull_policy,
        winding: quad.winding,
        color_format,
    }
}

pub(in crate::render::worldrender) fn mesh_batches(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    allow_optical: bool,
) -> GalResult<Vec<MeshBatch>> {
    mesh_batches_selected(
        frame,
        frontend,
        color_format,
        raster_y_direction,
        g_buffer,
        allow_optical,
        MeshBatchSelection::All,
    )
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(in crate::render::worldrender) enum MeshBatchSelection {
    All,
    Static,
    CameraSorted,
    ShadowOnly,
    /// Missing camera-facing ranges plus complete off-camera shadow candidates.
    ShadowSupplement,
}

impl MeshBatchSelection {
    pub(in crate::render::worldrender) fn includes(self, instance: &WorldMeshInstanceRequest) -> bool {
        let camera_sorted = instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0;
        if instance.stratum == WORLD_STRATUM_ENTITY_SHADOW_CASTER {
            // Shadow-only entity casters use the entity shadow program, never
            // a terrain/camera mesh batch.
            return false;
        }
        let shadow_only = instance.stratum == WORLD_STRATUM_TERRAIN
            && instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY != 0;
        match self {
            Self::All => !shadow_only,
            Self::Static => !camera_sorted && !shadow_only,
            Self::CameraSorted => camera_sorted && !shadow_only,
            Self::ShadowOnly => shadow_only,
            Self::ShadowSupplement => is_source_terrain_mesh_stratum(instance.stratum)
                && (shadow_only || !camera_sorted),
        }
    }
}

pub(in crate::render::worldrender) fn mesh_batches_selected(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    allow_optical: bool,
    selection: MeshBatchSelection,
) -> GalResult<Vec<MeshBatch>> {
    mesh_batches_selected_with_sorted_indices(
        frame,
        frontend,
        color_format,
        raster_y_direction,
        g_buffer,
        allow_optical,
        selection,
        None,
    )
}

/// Paged source terrain is multi-drawn unless this diagnostic A/B switch
/// keeps one bound draw per section. Read once.
pub(in crate::render::worldrender) fn source_terrain_multidraw_enabled() -> bool {
    static DISABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    source_terrain_geometry_pages_enabled()
        && !*DISABLED.get_or_init(|| {
            matches!(
                crate::core::environment::var("MATTMC_RUST_DISABLE_SOURCE_TERRAIN_MULTIDRAW").as_deref(),
                Ok("1") | Ok("true") | Ok("yes")
            )
        })
}

/// Source terrain geometry lives in shared pages unless this diagnostic
/// A/B switch restores one buffer pair per mesh. Read once.
pub(in crate::render::worldrender) fn source_terrain_geometry_pages_enabled() -> bool {
    static DISABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    !*DISABLED.get_or_init(|| {
        matches!(
            crate::core::environment::var("MATTMC_RUST_DISABLE_SOURCE_TERRAIN_PAGES").as_deref(),
            Ok("1") | Ok("true") | Ok("yes")
        )
    })
}

/// Diagnostic opt-out, read once: this sits in the per-instance batching loop.
pub(in crate::render::worldrender) fn per_section_terrain_animation_disabled() -> bool {
    static DISABLED: std::sync::OnceLock<bool> = std::sync::OnceLock::new();
    *DISABLED.get_or_init(|| {
        matches!(
            crate::core::environment::var("MATTMC_RUST_DISABLE_PER_SECTION_TERRAIN_ANIMATION").as_deref(),
            Ok("1") | Ok("true") | Ok("yes")
        )
    })
}

/// Terrain-only variant used by the selected-source route, which draws other
/// strata through their own writers: skipping them is output-identical for
/// its terrain batches and keeps entity churn out of the cached plans.
pub(in crate::render::worldrender) fn mesh_batches_filtered(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    selection: MeshBatchSelection,
    terrain_only: bool,
) -> GalResult<Vec<MeshBatch>> {
    mesh_batches_core(
        frame,
        frontend,
        color_format,
        raster_y_direction,
        g_buffer,
        false,
        selection,
        None,
        terrain_only,
    )
}

pub(in crate::render::worldrender) fn mesh_batches_selected_with_sorted_indices(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    allow_optical: bool,
    selection: MeshBatchSelection,
    sorted_indices: Option<&mut Vec<u8>>,
) -> GalResult<Vec<MeshBatch>> {
    mesh_batches_core(
        frame,
        frontend,
        color_format,
        raster_y_direction,
        g_buffer,
        allow_optical,
        selection,
        sorted_indices,
        false,
    )
}

pub(in crate::render::worldrender) fn mesh_batches_core(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    allow_optical: bool,
    selection: MeshBatchSelection,
    sorted_indices: Option<&mut Vec<u8>>,
    terrain_only: bool,
) -> GalResult<Vec<MeshBatch>> {
    mesh_batches_core_indices(
        frame,
        frontend,
        color_format,
        raster_y_direction,
        g_buffer,
        allow_optical,
        selection,
        sorted_indices,
        terrain_only,
        None,
    )
}

pub(in crate::render::worldrender) fn mesh_batches_filtered_indices(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    selection: MeshBatchSelection,
    terrain_only: bool,
    indices: &[usize],
) -> GalResult<Vec<MeshBatch>> {
    if indices
        .last()
        .is_some_and(|index| *index >= frame.mesh_instances.len())
        || indices.windows(2).any(|pair| pair[0] >= pair[1])
    {
        return Err(GalError::invalid_argument(
            "selected mesh indices must be in bounds and strictly increasing",
        ));
    }
    // Removing the first occurrence of a shared mesh can change its batch's
    // stable order; removing an intervening translucent draw can coalesce two
    // previously distinct batches. Retain the established build-then-filter
    // behavior when a selected mesh repeats. Ordinary terrain uses one
    // instance per mesh, so its independent ranges can be grouped after
    // policy selection.
    if mesh_batch_selection_repeats_selected_mesh(frame, selection, terrain_only, indices) {
        let mut batches = mesh_batches_core_indices(
            frame,
            frontend,
            color_format,
            raster_y_direction,
            g_buffer,
            false,
            selection,
            None,
            terrain_only,
            None,
        )?;
        for batch in &mut batches {
            batch
                .indices
                .retain(|index| indices.binary_search(index).is_ok());
        }
        batches.retain(|batch| !batch.indices.is_empty());
        return Ok(batches);
    }
    mesh_batches_core_indices(
        frame,
        frontend,
        color_format,
        raster_y_direction,
        g_buffer,
        false,
        selection,
        None,
        terrain_only,
        Some(indices),
    )
}

/// Whether any selected instance's mesh appears more than once among the
/// instances this selection admits. Only such meshes let culled instances
/// affect a selected plan's batch order or translucent coalescing.
pub(in crate::render::worldrender) fn mesh_batch_selection_repeats_selected_mesh(
    frame: &WorldPrimitiveFrame,
    selection: MeshBatchSelection,
    terrain_only: bool,
    indices: &[usize],
) -> bool {
    let mut seen = MeshKeySet::with_capacity_and_hasher(frame.mesh_instances.len(), Default::default());
    let mut repeated = MeshKeySet::default();
    for instance in frame.mesh_instances.iter().filter(|instance| {
        selection.includes(instance)
            && (!terrain_only || is_source_terrain_mesh_stratum(instance.stratum))
            && instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY == 0
    }) {
        if !seen.insert(instance.mesh_key) {
            repeated.insert(instance.mesh_key);
        }
    }
    !repeated.is_empty()
        && indices.iter().any(|&index| {
            frame
                .mesh_instances
                .get(index)
                .is_some_and(|instance| repeated.contains(&instance.mesh_key))
        })
}

/// Whether ordered `indices` are exactly the instances a plan with this
/// selection admits (none culled), so selecting them changes nothing.
pub(in crate::render::worldrender) fn mesh_batch_indices_cover_selection(
    frame: &WorldPrimitiveFrame,
    selection: MeshBatchSelection,
    terrain_only: bool,
    indices: &[usize],
) -> bool {
    let mut remaining = indices.iter().copied();
    frame
        .mesh_instances
        .iter()
        .enumerate()
        .filter(|(_, instance)| {
            selection.includes(instance)
                && (!terrain_only || is_source_terrain_mesh_stratum(instance.stratum))
                && instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY == 0
        })
        .all(|(index, _)| remaining.next() == Some(index))
        && remaining.next().is_none()
}

/// `mesh_batch_asset`'s checks for a whole-mesh instance, answered from the
/// compact index: a resident drawable generation without optical-stencil
/// sections. `false` means the full check must run (and may report an error).
pub(in crate::render::worldrender) fn plain_mesh_instance_is_valid(
    frontend: &WorldPrimitiveFrontend,
    instance: &WorldMeshInstanceRequest,
) -> bool {
    instance.mesh_section_index == WORLD_MESH_SECTION_ALL
        && frontend
            .mesh_asset_drawable_generations
            .get(&instance.mesh_key)
            .is_some_and(|&(generation, optical)| generation == instance.mesh_generation && !optical)
}

/// Checks immutable asset references even when a later policy culls the instance.
pub(in crate::render::worldrender) fn mesh_batch_asset<'a>(
    frame: &WorldPrimitiveFrame,
    frontend: &'a WorldPrimitiveFrontend,
    instance: &WorldMeshInstanceRequest,
    allow_optical: bool,
) -> GalResult<&'a MeshAssetStore> {
    let asset = frontend
        .mesh_assets
        .get(&instance.mesh_key)
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "world mesh instance references unknown mesh key {}",
                instance.mesh_key
            ))
        })?;
    if (!allow_optical || !frame.first_person.enabled) && asset.has_optical_stencil_sections() {
        return Err(GalError::unsupported_feature(
            "optical stencil mesh sections require the enabled Rust-owned hand target",
        ));
    }
    if instance.mesh_generation != asset_generation_for_key(instance.mesh_key, asset)? {
        return Err(GalError::invalid_argument(format!(
            "world mesh instance generation {} does not match mesh {} generation",
            instance.mesh_generation, instance.mesh_key
        )));
    }
    if instance.mesh_section_index != WORLD_MESH_SECTION_ALL
        && asset
            .sections
            .get(instance.mesh_section_index as usize)
            .is_none()
    {
        return Err(GalError::invalid_argument(
            "world mesh instance references missing section",
        ));
    }
    Ok(asset)
}

fn mesh_batches_core_indices(
    frame: &WorldPrimitiveFrame, frontend: &WorldPrimitiveFrontend,
    color_format: ColorFormat, raster_y_direction: RasterYDirection,
    g_buffer: bool, allow_optical: bool, selection: MeshBatchSelection,
    mut sorted_indices: Option<&mut Vec<u8>>, terrain_only: bool,
    instance_indices: Option<&[usize]>,
) -> GalResult<Vec<MeshBatch>> {
    // Preserve first-seen batch order for deterministic submission, but use a
    // hash index for membership.  The previous ordered tree made terrain
    // streaming cost scale as O(instances * log batches); a settled world can
    // contain thousands of section instances and many compatible ranges.
    // Ordering is represented by `batches`, so this changes lookup complexity
    // without changing semantic draw order or pipeline grouping.
    let capacity = instance_indices.map_or(frame.mesh_instances.len(), |indices| indices.len());
    let mut batches: Vec<MeshBatch> = Vec::with_capacity(capacity);
    // Keys are Rust-built resource identities (no flooding concern); the
    // default SipHash dominated plan rebuilds with thousands of sections.
    let mut key_to_batch = MeshBatchIndex::with_capacity(capacity);
    let mut selected = instance_indices.map(|indices| indices.iter().copied().peekable());
    for (index, instance) in frame.mesh_instances.iter().enumerate() {
        if let Some(indices) = selected.as_mut() {
            if indices.peek() != Some(&index) { continue; }
            indices.next();
        }
        if !selection.includes(instance) {
            continue;
        }
        if terrain_only && !is_source_terrain_mesh_stratum(instance.stratum) {
            continue;
        }
        if instance.flags & WORLD_MESH_INSTANCE_FLAG_OUTLINE_ONLY != 0 {
            // Outline-only instances remain in the frame for the dedicated
            // mask/post-effect planner, but never enter the regular material
            // batch where they would reveal an invisible entity body.
            continue;
        }
        let memo_key = MeshRangeMemoKey::for_instance(
            instance, selection, color_format, raster_y_direction, g_buffer,
        )
        .filter(|_| plain_mesh_instance_is_valid(frontend, instance));
        if let Some(ranges) = memo_key.and_then(|key| {
            frontend.mesh_range_memo.borrow_mut().get(key, frontend.mesh_texture_animation_generation)
        }) {
            // Resident, non-optical generation already validated by the slow path below.
            key_to_batch.begin_instance(&batches, instance.mesh_key);
            for range in ranges.iter().copied() {
                key_to_batch.push(
                    &mut batches,
                    range.key,
                    range.index_offset,
                    range.index_count,
                    index,
                    instance.model_submission_order,
                )?;
            }
            continue;
        }
        let asset = mesh_batch_asset(frame, frontend, instance, allow_optical)?;
        key_to_batch.begin_instance(&batches, instance.mesh_key);
        // Shadow raster culling cannot restore baked faces removed here.
        // Off-camera casters need every facing; camera casters supplement
        // only ranges absent from their existing color/shadow batches.
        let mut shadow_instance;
        let instance = if matches!(selection, MeshBatchSelection::ShadowOnly | MeshBatchSelection::ShadowSupplement) {
            let facing_mask = if selection == MeshBatchSelection::ShadowOnly
                || instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY != 0
            { 0x7f } else { !instance.terrain_visible_facing_mask & 0x7f };
            if facing_mask == instance.terrain_visible_facing_mask {
                instance
            } else {
                shadow_instance = instance.clone();
                shadow_instance.terrain_visible_facing_mask = facing_mask;
                &shadow_instance
            }
        } else { instance };
        if instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0 {
            geometry::translucent_order::append_batches(
                instance,
                asset,
                index,
                color_format,
                raster_y_direction,
                g_buffer,
                &mut batches,
                sorted_indices.as_mut().map(|bytes| &mut **bytes),
            )?;
            continue;
        }
        if instance.mesh_section_index == WORLD_MESH_SECTION_ALL {
            let ranges = if per_section_terrain_animation_disabled() {
                let texture_animated = asset
                    .sections
                    .iter()
                    .any(|section| frontend.world_mesh_texture_is_animated(section.texture_id));
                asset.compatible_section_ranges(
                    instance,
                    color_format,
                    raster_y_direction,
                    g_buffer,
                    texture_animated,
                )?
            } else {
                asset.compatible_section_ranges_per_texture(
                    instance,
                    color_format,
                    raster_y_direction,
                    g_buffer,
                    Some(frontend.mesh_texture_animation_generation),
                    |texture_id| frontend.world_mesh_texture_is_animated(texture_id),
                )?
            };
            if let Some(key) = memo_key.filter(|_| !asset.has_optical_stencil_sections()) {
                frontend.mesh_range_memo.borrow_mut().insert(
                    key,
                    frontend.mesh_texture_animation_generation,
                    Arc::clone(&ranges),
                    frontend.mesh_assets.len(),
                );
            }
            for range in ranges.iter().copied() {
                key_to_batch.push(
                    &mut batches,
                    range.key,
                    range.index_offset,
                    range.index_count,
                    index,
                    instance.model_submission_order,
                )?;
            }
        } else {
            let section = asset
                .sections
                .get(instance.mesh_section_index as usize)
                .ok_or_else(|| {
                    GalError::invalid_argument("world mesh instance references missing section")
                })?;
            let key = mesh_key_for_section(
                instance,
                section,
                instance.mesh_section_index,
                instance.cull_policy,
                asset.mesh_generation,
                asset.vertex_bytes.len() / WORLD_MESH_GPU_VERTEX_BYTES,
                color_format,
                raster_y_direction,
                g_buffer,
                frontend.world_mesh_texture_is_animated(section.texture_id),
            );
            key_to_batch.push(
                &mut batches,
                key,
                section.index_offset as u64,
                section.index_count,
                index,
                instance.model_submission_order,
            )?;
        }
    }
    // Ordinary model layers retain authored order. Armor glint is accumulated
    // by vanilla's fixed buffer and flushed after the model collections, so its
    // semantic material has a separate deferred phase within the model group.
    sort_mesh_batches(&mut batches, frame);
    Ok(batches)
}

pub(in crate::render::worldrender) fn sort_mesh_batches(batches: &mut [MeshBatch], frame: &WorldPrimitiveFrame) {
    // Stable like `sort_by_key`, but each wide batch moves about once:
    // keys are computed once and sorted with their positions.
    batches.sort_by_cached_key(|batch| mesh_batch_order_key(batch, frame));
}

fn mesh_batch_order_key(batch: &MeshBatch, frame: &WorldPrimitiveFrame) -> (u16, u8, i32, u8) {
    let phase = if batch.key.standard_item_foil {
        4
    } else {
        mesh_material_render_phase(batch.key.material_mode)
    };
    if let Some(order) = batch.model_submission_order {
        let deferred_armor = batch
            .indices
            .first()
            .and_then(|i| frame.mesh_instances[*i].item_foil)
            .is_some_and(|foil| foil.kind.armor_projection().is_some());
        (2u16, u8::from(deferred_armor), order, phase)
    } else {
        (
            match phase {
                0 => 0,
                1 => 1,
                2 => 3,
                3 => 4,
                4 => 5,
                _ => u16::MAX,
            },
            0,
            0,
            phase,
        )
    }
}

pub(in crate::render::worldrender) fn mesh_batch_plan_key(
    frame: &WorldPrimitiveFrame,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    allow_optical: bool,
) -> MeshBatchPlanKey {
    MeshBatchPlanKey {
        color_format,
        raster_y_direction,
        g_buffer,
        allow_optical,
        selection: MeshBatchSelection::All,
        terrain_only: false,
        instance_indices: None,
        instances: frame
            .mesh_instances
            .iter()
            .map(mesh_batch_instance_key)
            .collect(),
    }
}

/// Identity used by terrain-only batch plans: non-terrain instances keep their
/// position (batches store instance indices) but not their churning identity.
pub(in crate::render::worldrender) fn terrain_batch_instance_key(instance: &WorldMeshInstanceRequest) -> MeshBatchInstanceKey {
    if is_source_terrain_mesh_stratum(instance.stratum) {
        mesh_batch_instance_key(instance)
    } else {
        MeshBatchInstanceKey {
            stratum: u32::MAX,
            mesh_key: 0,
            mesh_generation: 0,
            mesh_section_index: 0,
            depth_policy: 0,
            cull_policy: 0,
            winding: 0,
            flags: 0,
            terrain_visible_facing_mask: 0,
            model_submission_order: None,
            standard_foil_kind: None,
            has_decal_foil: false,
        }
    }
}

pub(in crate::render::worldrender) fn mesh_batch_instance_key(instance: &WorldMeshInstanceRequest) -> MeshBatchInstanceKey {
    MeshBatchInstanceKey {
        stratum: instance.stratum,
        mesh_key: instance.mesh_key,
        mesh_generation: instance.mesh_generation,
        mesh_section_index: instance.mesh_section_index,
        depth_policy: instance.depth_policy,
        cull_policy: instance.cull_policy,
        winding: instance.winding,
        flags: instance.flags,
        terrain_visible_facing_mask: instance.terrain_visible_facing_mask,
        model_submission_order: instance.model_submission_order,
        standard_foil_kind: instance.item_foil.map(|foil| foil.kind),
        has_decal_foil: instance.decal_foil.is_some(),
    }
}

pub(in crate::render::worldrender) fn mesh_material_render_phase(material_mode: u32) -> u8 {
    match material_mode {
        WORLD_MATERIAL_MODE_OPAQUE => 0,
        WORLD_MATERIAL_MODE_CUTOUT
        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_WRITE
        | WORLD_MATERIAL_MODE_OPTICAL_STENCIL_TEST => 1,
        WORLD_MATERIAL_MODE_GLINT => 2,
        WORLD_MATERIAL_MODE_TRANSLUCENT | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT => 3,
        // Validation rejects unknown modes before pipeline construction. Keep
        // them at the end here so an invalid semantic record can never push a
        // valid transparent range ahead of opaque terrain before that error.
        _ => u8::MAX,
    }
}

#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(in crate::render::worldrender) struct MeshSectionRangeCacheKey {
    pub(in crate::render::worldrender) stratum: u32,
    pub(in crate::render::worldrender) depth_policy: u32,
    pub(in crate::render::worldrender) terrain_visible_facing_mask: u8,
    pub(in crate::render::worldrender) standard_item_foil: bool,
    pub(in crate::render::worldrender) color_format: ColorFormat,
    pub(in crate::render::worldrender) raster_y_direction: RasterYDirection,
    pub(in crate::render::worldrender) g_buffer: bool,
    pub(in crate::render::worldrender) view_layering: Option<crate::render::shared::view_layering::Projection>,
    pub(in crate::render::worldrender) decal_vertex_count: usize,
    pub(in crate::render::worldrender) texture_animation_signature: u64,
}

#[derive(Clone, Copy)]
pub(in crate::render::worldrender) struct MeshSectionRange {
    pub(in crate::render::worldrender) key: MeshResourceKey,
    pub(in crate::render::worldrender) index_offset: u64,
    pub(in crate::render::worldrender) index_count: u32,
}

pub(in crate::render::worldrender) fn texture_animation_signature(
    asset: &MeshAssetStore,
    texture_is_animated: impl Fn(u32) -> bool,
) -> u64 {
    // A small deterministic rolling signature keeps section-range reuse safe
    // across texture-only animation updates without retaining a second vector
    // of booleans in every mesh asset.
    let mut signature = 0xcbf2_9ce4_8422_2325_u64 ^ asset.sections.len() as u64;
    for section in &asset.sections {
        signature ^= u64::from(section.texture_id);
        signature = signature.wrapping_mul(0x1000_0000_01b3);
        signature ^= u64::from(texture_is_animated(section.texture_id));
        signature = signature.wrapping_mul(0x1000_0000_01b3);
    }
    signature
}

pub(in crate::render::worldrender) fn compatible_mesh_section_ranges(
    instance: &WorldMeshInstanceRequest,
    asset: &MeshAssetStore,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    texture_animated: bool,
) -> GalResult<Vec<MeshSectionRange>> {
    compatible_mesh_section_ranges_per_texture(
        instance,
        asset,
        color_format,
        raster_y_direction,
        g_buffer,
        |_| texture_animated,
    )
}

pub(in crate::render::worldrender) fn compatible_mesh_section_ranges_per_texture(
    instance: &WorldMeshInstanceRequest,
    asset: &MeshAssetStore,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    texture_is_animated: impl Fn(u32) -> bool,
) -> GalResult<Vec<MeshSectionRange>> {
    let mut ranges = Vec::new();
    let mut visible_sections = asset.sections.iter().enumerate().filter(|(_, section)| {
        instance.terrain_visible_facing_mask & (1u8 << section.source_facing) != 0
    });
    let Some((first_index, first)) = visible_sections.next() else {
        return Ok(ranges);
    };
    let mut current_key = mesh_key_for_section(
        instance,
        first,
        first_index as u32,
        first.cull_policy,
        asset.mesh_generation,
        asset.vertex_bytes.len() / WORLD_MESH_GPU_VERTEX_BYTES,
        color_format,
        raster_y_direction,
        g_buffer,
        texture_is_animated(first.texture_id),
    );
    let mut current_offset = first.index_offset as u64;
    let mut current_count = first.index_count;
    let mut previous = first;
    for (section_index, section) in visible_sections {
        let key = mesh_key_for_section(
            instance,
            section,
            section_index as u32,
            section.cull_policy,
            asset.mesh_generation,
            asset.vertex_bytes.len() / WORLD_MESH_GPU_VERTEX_BYTES,
            color_format,
            raster_y_direction,
            g_buffer,
            texture_is_animated(section.texture_id),
        );
        if mesh_sections_can_coalesce(&current_key, &key, previous, section, asset.index_type)
            && current_count
                .checked_add(section.index_count)
                .ok_or_else(|| {
                    GalError::invalid_argument("world mesh range index count overflow")
                })?
                <= u32::MAX
        {
            current_count += section.index_count;
        } else {
            ranges.push(MeshSectionRange {
                key: current_key,
                index_offset: current_offset,
                index_count: current_count,
            });
            current_key = key;
            current_offset = section.index_offset as u64;
            current_count = section.index_count;
        }
        previous = section;
    }
    ranges.push(MeshSectionRange {
        key: current_key,
        index_offset: current_offset,
        index_count: current_count,
    });
    Ok(ranges)
}

pub(in crate::render::worldrender) fn mesh_sections_can_coalesce(
    current_key: &MeshResourceKey,
    next_key: &MeshResourceKey,
    current_section: &WorldMeshSection,
    next_section: &WorldMeshSection,
    index_type: IndexType,
) -> bool {
    let mut compatible_next = *current_key;
    compatible_next.section_index = next_key.section_index;
    compatible_next == *next_key
        && contiguous_index_range(current_section, next_section, index_type)
}

pub(in crate::render::worldrender) fn contiguous_index_range(
    current_section: &WorldMeshSection,
    next_section: &WorldMeshSection,
    index_type: IndexType,
) -> bool {
    let Some(current_byte_len) =
        (current_section.index_count as u64).checked_mul(index_stride(index_type))
    else {
        return false;
    };
    current_section.index_offset as u64 + current_byte_len == next_section.index_offset as u64
}

pub(in crate::render::worldrender) fn index_stride(index_type: IndexType) -> u64 {
    match index_type {
        IndexType::U16 => 2,
        IndexType::U32 => 4,
    }
}

/// Batch membership for `mesh_batches_core_indices`. Batch keys include the
/// mesh identity, so different meshes can never share an opaque batch. While
/// every visited mesh is distinct, which is the normal terrain case, a key can
/// only repeat within the current instance; scan just its batches instead of
/// hashing every wide key. The first repeated mesh switches to the exact map
/// that `push_mesh_batch` maintains, built from the same eligible batches.
pub(in crate::render::worldrender) struct MeshBatchIndex {
    map: Option<
        HashMap<(MeshResourceKey, Option<i32>), usize, crate::render::vulkanic::gal::AccessHashBuilder>,
    >,
    meshes: crate::render::worldrender::MeshKeySet<u64>,
    /// Batches a keyed lookup would have registered, in creation order.
    keyed: Vec<usize>,
    instance_start: usize,
}

impl MeshBatchIndex {
    pub(in crate::render::worldrender) fn with_capacity(capacity: usize) -> Self {
        Self {
            map: None,
            meshes: crate::render::worldrender::MeshKeySet::with_capacity_and_hasher(capacity, Default::default()),
            keyed: Vec::with_capacity(capacity),
            instance_start: 0,
        }
    }

    pub(in crate::render::worldrender) fn begin_instance(&mut self, batches: &[MeshBatch], mesh_key: u64) {
        self.instance_start = batches.len();
        if self.map.is_none() && !self.meshes.insert(mesh_key) {
            let mut map = HashMap::with_capacity_and_hasher(self.keyed.len().max(16), Default::default());
            for &index in &self.keyed {
                let batch: &MeshBatch = &batches[index];
                map.insert((batch.key, batch.model_submission_order), index);
            }
            self.map = Some(map);
        }
    }

    pub(in crate::render::worldrender) fn push(
        &mut self,
        batches: &mut Vec<MeshBatch>,
        key: MeshResourceKey,
        index_offset: u64,
        index_count: u32,
        instance_index: usize,
        model_submission_order: Option<i32>,
    ) -> GalResult<()> {
        if let Some(map) = self.map.as_mut() {
            return push_mesh_batch(
                batches, map, key, index_offset, index_count, instance_index, model_submission_order,
            );
        }
        if material_mode_uses_alpha_blending(key.material_mode) || key.standard_item_foil {
            // Adjacent-only coalescing never consults the keyed index.
            let mut unused = HashMap::default();
            return push_mesh_batch(
                batches, &mut unused, key, index_offset, index_count, instance_index, model_submission_order,
            );
        }
        let existing = self
            .keyed
            .iter()
            .rev()
            .take_while(|&&index| index >= self.instance_start)
            .copied()
            .find(|&index| batches[index].key == key && batches[index].model_submission_order == model_submission_order);
        if let Some(batch_index) = existing {
            let batch = &mut batches[batch_index];
            if batch.index_offset != index_offset || batch.index_count != index_count {
                return Err(GalError::invalid_argument(
                    "world mesh batch key maps to incompatible index range",
                ));
            }
            batch.indices.push(instance_index);
            return Ok(());
        }
        self.keyed.push(batches.len());
        batches.push(MeshBatch {
            model_submission_order,
            key,
            index_offset,
            index_count,
            sorted_index_offset: None,
            indices: smallvec![instance_index],
        });
        Ok(())
    }
}

pub(in crate::render::worldrender) fn push_mesh_batch(
    batches: &mut Vec<MeshBatch>,
    key_to_batch: &mut HashMap<
        (MeshResourceKey, Option<i32>),
        usize,
        crate::render::vulkanic::gal::AccessHashBuilder,
    >,
    key: MeshResourceKey,
    index_offset: u64,
    index_count: u32,
    instance_index: usize,
    model_submission_order: Option<i32>,
) -> GalResult<()> {
    // A translucent section's producer order is its back-to-front order.
    // Unlike opaque terrain, two equal resource keys separated by another
    // translucent section are not interchangeable: merging them would move
    // the later draw in front of that intervening section. Coalesce only
    // immediately adjacent equal translucent records, retaining the exact
    // semantic command stream while still avoiding redundant instance draws.
    if material_mode_uses_alpha_blending(key.material_mode) || key.standard_item_foil {
        if let Some(batch) = batches.last_mut().filter(|batch| {
            batch.key == key
                && batch.model_submission_order == model_submission_order
                && (key.decal_vertex_count == 0
                    || decal_foil_payload_bytes(batch.count() + 1, key.decal_vertex_count).is_ok())
        }) {
            if batch.index_offset != index_offset || batch.index_count != index_count {
                return Err(GalError::invalid_argument(
                    "adjacent translucent world mesh records use incompatible index ranges",
                ));
            }
            batch.indices.push(instance_index);
        } else {
            batches.push(MeshBatch {
                model_submission_order,
                key,
                index_offset,
                index_count,
                sorted_index_offset: None,
                indices: smallvec![instance_index],
            });
        }
        return Ok(());
    }
    if let Some(batch_index) = key_to_batch.get(&(key, model_submission_order)).copied() {
        let batch = &mut batches[batch_index];
        if batch.index_offset != index_offset || batch.index_count != index_count {
            return Err(GalError::invalid_argument(
                "world mesh batch key maps to incompatible index range",
            ));
        }
        batch.indices.push(instance_index);
    } else {
        let batch_index = batches.len();
        key_to_batch.insert((key, model_submission_order), batch_index);
        batches.push(MeshBatch {
            model_submission_order,
            key,
            index_offset,
            index_count,
            sorted_index_offset: None,
            indices: smallvec![instance_index],
        });
    }
    Ok(())
}

pub(in crate::render::worldrender) fn mesh_view_layering(
    instance: &WorldMeshInstanceRequest,
) -> Option<crate::render::shared::view_layering::Projection> {
    if instance.stratum == WORLD_STRATUM_TERRAIN {
        return None;
    }
    instance
        .item_foil
        .and_then(|foil| foil.kind.armor_projection())
        .or_else(|| crate::render::shared::view_layering::from_flags(instance.flags))
}

pub(in crate::render::worldrender) fn mesh_key_for_section(
    instance: &WorldMeshInstanceRequest,
    section: &WorldMeshSection,
    section_index: u32,
    cull_policy: u32,
    resource_generation: u64,
    vertex_count: usize,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    texture_animated: bool,
) -> MeshResourceKey {
    MeshResourceKey {
        raster_y_direction,
        g_buffer,
        standard_item_foil: instance.item_foil.is_some(),
        view_layering: mesh_view_layering(instance),
        decal_vertex_count: if instance.decal_foil.is_some() {
            vertex_count
        } else {
            0
        },
        stratum: instance.stratum,
        mesh_key: instance.mesh_key,
        mesh_generation: resource_generation,
        section_index,
        material_id: section.material_id,
        texture_id: section.texture_id,
        texture_animated,
        material_mode: section.material_mode,
        winding: section.winding,
        depth_policy: instance.depth_policy,
        cull_policy,
        color_format,
    }
}

pub(in crate::render::worldrender) fn packed_line_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &LineBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_LINE_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(&mut out, 0.0);
    push_f32(&mut out, 0.0);
    for segment in &frame.segments[batch.start..batch.start + batch.count] {
        for value in segment.start {
            push_f32(&mut out, value);
        }
        push_f32(&mut out, segment.line_width);
        for value in segment.end {
            push_f32(&mut out, value);
        }
        push_f32(&mut out, 1.0);
        for value in argb_to_rgba(segment.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

/// Exact uniform ordering consumed by the private vanilla sky-disc pipeline.
/// It deliberately takes only semantic camera transforms and the copied
/// biome/time-adjusted ARGB value; no Java renderer or Iris object crosses
/// this boundary.
pub(in crate::render::worldrender) fn packed_sky_disc_uniforms(frame: &WorldPrimitiveFrame) -> Vec<u8> {
    let mut out = Vec::with_capacity(WORLD_SKY_DISC_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    // The clear/background record intentionally carries fog colour so
    // uncovered pixels and terrain fog share Frozen's clear contract. The
    // camera sky fan is a distinct semantic input (`level.getSkyColor`) that
    // arrives in the copied environment frame; using the background color
    // here collapsed those two values and visibly tinted the whole sky.
    for value in sky_disc_color(frame) {
        push_f32(&mut out, value);
    }
    for value in frame.shader_environment.fog_parameter_color {
        push_f32(&mut out, value);
    }
    for value in [frame.shader_environment.fog_sky_end, 0.0, 0.0, 0.0] {
        push_f32(&mut out, value);
    }
    out
}

pub(in crate::render::worldrender) fn sky_disc_color(frame: &WorldPrimitiveFrame) -> [f32; 4] {
    if frame.shader_environment.enabled {
        let [r, g, b] = frame.shader_environment.sky_color;
        [r, g, b, 1.0]
    } else {
        argb_to_rgba(frame.background.sky.sky_color_argb)
    }
}

pub(in crate::render::worldrender) fn packed_crack_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &CrackBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_CRACK_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(&mut out, 0.0);
    push_f32(&mut out, 0.0);
    for quad in &frame.crack_quads[batch.start..batch.start + batch.count] {
        for vertex in quad.vertices {
            for value in vertex {
                push_f32(&mut out, value);
            }
            push_f32(&mut out, 1.0);
        }
        let atlas_width = (CRACK_STAGE_COUNT * CRACK_STAGE_SIZE) as f32;
        let stage_x = (quad.stage * CRACK_STAGE_SIZE) as f32;
        push_f32(&mut out, (stage_x + 0.5) / atlas_width);
        push_f32(&mut out, 0.5 / CRACK_STAGE_SIZE as f32);
        push_f32(&mut out, (CRACK_STAGE_SIZE - 1) as f32 / atlas_width);
        push_f32(
            &mut out,
            (CRACK_STAGE_SIZE - 1) as f32 / CRACK_STAGE_SIZE as f32,
        );
        for value in argb_to_rgba(quad.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

pub(in crate::render::worldrender) fn packed_border_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &BorderBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_BORDER_UNIFORM_BYTES as usize);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(&mut out, 0.0);
    push_f32(&mut out, 0.0);
    for quad in &frame.border_quads[batch.start..batch.start + batch.count] {
        for vertex in quad.vertices {
            for value in vertex {
                push_f32(&mut out, value);
            }
            push_f32(&mut out, 1.0);
        }
        for value in quad.uv_region {
            push_f32(&mut out, value);
        }
        push_f32(&mut out, quad.scroll[0]);
        push_f32(&mut out, quad.scroll[1]);
        push_f32(&mut out, quad.border_size);
        push_f32(&mut out, quad.distance_to_border);
        for value in argb_to_rgba(quad.color_argb) {
            push_f32(&mut out, value);
        }
    }
    Ok(out)
}

/// World material positions are camera-relative. Applying the material's
/// scale before the copied view matrix is equivalent to vanilla's
/// ModelViewMat.scale(1 - 1/4096) for perspective world shadow layering.
/// This is resolved from semantic material metadata, independently of texture
/// identity, and is shared by direct and source-program lowering.
pub(in crate::render::worldrender) fn layered_material_vertices(quad: &WorldMaterialQuadRequest) -> [[f32; 3]; 4] {
    let scale = assets::material_registry::material(quad.material_id)
        .map_or(1.0, |material| material.perspective_layer_scale);
    quad.vertices
        .map(|vertex| vertex.map(|component| component * scale))
}

pub(in crate::render::worldrender) fn packed_material_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &MaterialBatch,
) -> GalResult<Vec<u8>> {
    let mut out = Vec::with_capacity(WORLD_MATERIAL_UNIFORM_BYTES as usize);
    let (view, projection) = if is_distant_horizons_generic_stratum(batch.key.stratum) {
        (
            [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
            frame.lod_render_frame.combined_matrix,
        )
    } else {
        (frame.view_matrix, frame.projection_matrix)
    };
    for value in view {
        push_f32(&mut out, value);
    }
    for value in projection {
        push_f32(&mut out, value);
    }
    push_f32(&mut out, frame.viewport_width as f32);
    push_f32(&mut out, frame.viewport_height as f32);
    push_f32(
        &mut out,
        assets::material_registry::cutout_threshold(batch.key.material_id),
    );
    // The fourth lane carries only a family's own sky/cloud fog range.
    // Generic materials keep zero; the typed dark-disc variant appends its
    // fog color without shifting the ordinary material or compact-box records.
    push_f32(
        &mut out,
        if batch.key.source_program == WORLD_MATERIAL_SOURCE_CLOUDS {
            frame.shader_environment.fog_clouds_end
        } else if batch.key.material_id == WORLD_MATERIAL_ID_SKY_DARK_DISC {
            frame.shader_environment.fog_sky_end
        } else {
            0.0
        },
    );
    if batch.key.material_id == WORLD_MATERIAL_ID_SKY_DARK_DISC {
        for value in frame.shader_environment.fog_parameter_color {
            push_f32(&mut out, value);
        }
    }
    for index in &batch.indices {
        let quad = &frame.material_quads[*index];
        for (vertex_index, vertex) in layered_material_vertices(quad).iter().enumerate() {
            for value in vertex {
                push_f32(&mut out, *value);
            }
            push_f32(
                &mut out,
                if vertex_index == 0 {
                    quad.winding as f32
                } else {
                    1.0
                },
            );
        }
        push_f32(&mut out, quad.uvs[0][0]);
        push_f32(&mut out, quad.uvs[0][1]);
        push_f32(&mut out, quad.uvs[1][0]);
        push_f32(&mut out, quad.uvs[1][1]);
        push_f32(&mut out, quad.uvs[2][0]);
        push_f32(&mut out, quad.uvs[2][1]);
        push_f32(&mut out, quad.uvs[3][0]);
        push_f32(&mut out, quad.uvs[3][1]);
        for vertex_color in quad.vertex_color_argb {
            let color = if quad.material_id == WORLD_MATERIAL_ID_SKY_STARS {
                // Frozen's stars shader consumes the unquantized scalar as
                // RGBA. Preserve that semantic precision through GAL uniforms.
                [frame.background.sky.star_brightness; 4]
            } else if quad.material_id == WORLD_MATERIAL_ID_CELESTIAL {
                [1.0, 1.0, 1.0, frame.background.sky.rain_brightness]
            } else {
                argb_to_rgba(vertex_color)
            };
            for value in color {
                push_f32(&mut out, value);
            }
        }
        for packed_light in quad.vertex_packed_light {
            for value in source_lightmap_coordinates(packed_light) {
                push_f32(&mut out, value);
            }
        }
    }
    Ok(out)
}

pub(in crate::render::worldrender) fn packed_dh_generic_box_uniforms_for_batch(
    frame: &WorldPrimitiveFrame,
    batch: &DistantHorizonsGenericBoxBatch,
) -> Vec<u8> {
    let mut out = Vec::with_capacity(WORLD_MATERIAL_HEADER_BYTES + batch.indices.len() * 64);
    for value in [
        1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
    ] {
        push_f32(&mut out, value);
    }
    for value in frame.lod_render_frame.combined_matrix {
        push_f32(&mut out, value);
    }
    for value in [
        frame.viewport_width as f32,
        frame.viewport_height as f32,
        0.0,
        0.0,
    ] {
        push_f32(&mut out, value);
    }
    for index in &batch.indices {
        let item = &frame.dh_generic_boxes[*index];
        for value in item
            .min
            .into_iter()
            .chain([0.0])
            .chain(item.max)
            .chain([0.0])
        {
            push_f32(&mut out, value);
        }
        for shading in [
            item.shading[0],
            item.shading[1],
            item.shading[3],
            item.shading[2],
            item.shading[5],
            item.shading[4],
        ] {
            let shade = |component: u32| -> u32 {
                ((component as f32 * shading).round() as i32).clamp(0, 255) as u32
            };
            let color = (item.color_argb & 0xff00_0000)
                | (shade((item.color_argb >> 16) & 0xff) << 16)
                | (shade((item.color_argb >> 8) & 0xff) << 8)
                | shade(item.color_argb & 0xff);
            push_u32(&mut out, color);
        }
        push_u32(&mut out, item.packed_light);
        push_u32(&mut out, 0);
    }
    out
}

pub(in crate::render::worldrender) fn required_mesh_instance_stream_bytes(mesh_batches: &[MeshBatch]) -> GalResult<u64> {
    let mut cursor = 0u64;
    for batch in mesh_batches {
        cursor = align_up_u64(cursor, WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64)?;
        let batch_bytes = WORLD_MESH_BATCH_HEADER_BYTES
            .checked_add(
                batch
                    .count()
                    .checked_mul(WORLD_MESH_INSTANCE_BYTES)
                    .ok_or_else(|| {
                        GalError::invalid_argument("world mesh stream instance byte count overflow")
                    })?,
            )
            .ok_or_else(|| GalError::invalid_argument("world mesh stream byte count overflow"))?;
        cursor = cursor
            .checked_add(batch_bytes as u64)
            .ok_or_else(|| GalError::invalid_argument("world mesh stream cursor overflow"))?;
        if batch.key.standard_item_foil {
            cursor = item_foil_stream_range(cursor, batch.count(), batch.key.decal_vertex_count)?.1;
        }
    }
    if cursor > LOWERED_SOURCE_FRAME_STREAM_MAX_BYTES {
        return Err(GalError::invalid_argument(
            "world mesh stream exceeds bounded frame bytes",
        ));
    }
    Ok(cursor)
}

/// Separate aligned binding4 range inside the existing owned mesh stream.
/// The returned end excludes descriptor padding, which the allocator owns.
pub(in crate::render::worldrender) fn mesh_stream_dynamic_offsets(
    batch: &MeshBatch,
    vertex_offset: u64,
    instance_offset: u64,
) -> GalResult<Vec<u64>> {
    Ok(mesh_stream_dynamic_offsets_inline(batch, vertex_offset, instance_offset)?.into_vec())
}

pub(in crate::render::worldrender) fn mesh_stream_dynamic_offsets_inline(
    batch: &MeshBatch,
    vertex_offset: u64,
    instance_offset: u64,
) -> GalResult<SmallVec<[u64; 3]>> {
    let mut offsets = smallvec![vertex_offset, instance_offset];
    if batch.key.standard_item_foil {
        if batch.key.material_mode != WORLD_MATERIAL_MODE_GLINT {
            return Err(GalError::invalid_argument(
                "standard foil offsets require glint material",
            ));
        }
        if batch.count() == 0 || batch.count() > WORLD_MAX_MESH_INSTANCES {
            return Err(GalError::invalid_argument(
                "standard foil offset instance count outside bounded range",
            ));
        }
        let instance_end = instance_offset
            .checked_add(
                (WORLD_MESH_BATCH_HEADER_BYTES + batch.count() * WORLD_MESH_INSTANCE_BYTES) as u64,
            )
            .ok_or_else(|| GalError::invalid_argument("standard foil instance offset overflow"))?;
        offsets.push(
            item_foil_stream_range(instance_end, batch.count(), batch.key.decal_vertex_count)?.0,
        );
    }
    Ok(offsets)
}

pub(in crate::render::worldrender) fn decal_foil_payload_bytes(count: usize, vertex_count: usize) -> GalResult<usize> {
    let bytes = vertex_count
        .checked_mul(8)
        .and_then(|v| v.checked_add(48))
        .and_then(|v| v.checked_mul(count));
    if count == 0
        || count > WORLD_MAX_MESH_INSTANCES
        || vertex_count == 0
        || bytes.is_none_or(|v| v as u64 > WORLD_MESH_INSTANCE_STREAM_BINDING_RANGE_BYTES)
    {
        return Err(GalError::invalid_argument(
            "world decal payload exceeds bounded binding range",
        ));
    }
    Ok(bytes.unwrap())
}

pub(in crate::render::worldrender) fn item_foil_stream_range(cursor: u64, count: usize, vertex_count: usize) -> GalResult<(u64, u64)> {
    if vertex_count == 0 {
        return standard_item_foil_stream_range(cursor, count);
    }
    let bytes = decal_foil_payload_bytes(count, vertex_count)?;
    let start = align_up_u64(cursor, WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64)?;
    let end = start
        .checked_add(bytes as u64)
        .ok_or_else(|| GalError::invalid_argument("world decal stream offset overflow"))?;
    Ok((start, end))
}

/// Parameters and per-instance projected UVs share binding4. Word9 of each
/// 12-word parameter record addresses that instance's UVs within this payload.
/// Geometry and packed source normals remain immutable across instances/frames.
pub(in crate::render::worldrender) fn packed_decal_item_foil_instances(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    batch: &MeshBatch,
) -> GalResult<Vec<u8>> {
    let bytes = decal_foil_payload_bytes(batch.count(), batch.key.decal_vertex_count)?;
    if !batch.key.standard_item_foil || batch.key.material_mode != WORLD_MATERIAL_MODE_GLINT {
        return Err(GalError::invalid_argument(
            "decal stream requires explicit item glint material",
        ));
    }
    let asset = frontend
        .mesh_assets
        .get(&batch.key.mesh_key)
        .ok_or_else(|| GalError::invalid_argument("decal mesh asset missing"))?;
    let normals = asset
        .decal_normals
        .as_ref()
        .ok_or_else(|| GalError::invalid_argument("decal source normals missing"))?;
    if asset.mesh_generation != batch.key.mesh_generation
        || normals.len() != batch.key.decal_vertex_count
        || asset.vertex_bytes.len() != normals.len() * WORLD_MESH_GPU_VERTEX_BYTES
    {
        return Err(GalError::invalid_argument(
            "decal mesh incarnation or vertex count mismatch",
        ));
    }
    let mut prepared = Vec::with_capacity(batch.count());
    for index in &batch.indices {
        let instance = frame
            .mesh_instances
            .get(*index)
            .ok_or_else(|| GalError::invalid_argument("decal instance index out of bounds"))?;
        let decal = instance
            .decal_foil
            .ok_or_else(|| GalError::invalid_argument("decal instance semantics missing"))?;
        let foil = instance
            .item_foil
            .ok_or_else(|| GalError::invalid_argument("decal item foil missing"))?;
        if foil.kind != crate::render::shared::item_foil::StandardFoilKind::Item
            || instance.transform != decal.model_pose
            || instance.mesh_key != batch.key.mesh_key
            || instance.mesh_generation != batch.key.mesh_generation
        {
            return Err(GalError::invalid_argument(
                "decal instance pose, material or incarnation mismatch",
            ));
        }
        prepared.push((foil.packed_instance()?, decal.prepare()?));
    }
    let mut out = Vec::with_capacity(bytes);
    for (i, (parameters, _)) in prepared.iter().enumerate() {
        out.extend_from_slice(parameters);
        let uv_word = batch.count() * 12 + i * normals.len() * 2;
        out[i * 48 + 36..i * 48 + 40].copy_from_slice(&(uv_word as u32).to_le_bytes());
    }
    for (_, projection) in prepared {
        for (i, normal) in normals.iter().enumerate() {
            let position = std::array::from_fn(|axis| {
                let start = i * WORLD_MESH_GPU_VERTEX_BYTES + axis * 4;
                f32::from_le_bytes(asset.vertex_bytes[start..start + 4].try_into().unwrap())
            });
            for value in projection.texture_uv(position, *normal)? {
                push_f32(&mut out, value);
            }
        }
    }
    debug_assert_eq!(out.len(), bytes);
    Ok(out)
}

pub(in crate::render::worldrender) fn standard_item_foil_stream_range(cursor: u64, count: usize) -> GalResult<(u64, u64)> {
    if count == 0 || count > WORLD_MAX_MESH_INSTANCES {
        return Err(GalError::invalid_argument(
            "standard foil stream count outside bounded range",
        ));
    }
    let start = align_up_u64(cursor, WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64)?;
    let end = start
        .checked_add(count as u64 * 48)
        .ok_or_else(|| GalError::invalid_argument("standard foil stream range overflow"))?;
    Ok((start, end))
}

/// Pack exactly the selected draw order for the dedicated foil binding.
/// No asset mutation, animation cache, sorting or Java-computed UV payload.
pub(in crate::render::worldrender) fn packed_standard_item_foil_instances(
    instances: &[WorldMeshInstanceRequest],
    indices: &[usize],
) -> GalResult<Vec<u8>> {
    if indices.is_empty() || indices.len() > WORLD_MAX_MESH_INSTANCES {
        return Err(GalError::invalid_argument(
            "standard foil instance count outside bounded range",
        ));
    }
    // Validate before allocating or publishing any stream payload.
    for index in indices {
        let instance = instances.get(*index).ok_or_else(|| {
            GalError::invalid_argument("standard foil instance index out of bounds")
        })?;
        if instance.decal_foil.is_some() {
            return Err(GalError::invalid_argument(
                "decal semantics require native projected UV payload",
            ));
        }
        instance
            .item_foil
            .ok_or_else(|| GalError::invalid_argument("standard foil instance semantics missing"))?
            .validate()?;
    }
    let mut bytes = Vec::with_capacity(indices.len() * 48);
    for index in indices {
        bytes.extend_from_slice(
            &instances[*index]
                .item_foil
                .expect("validated immutable foil semantics")
                .packed_instance()?,
        );
    }
    Ok(bytes)
}

pub(in crate::render::worldrender) fn packed_mesh_uniforms_for_batches(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    mesh_batches: &[MeshBatch],
    required_bytes: u64,
) -> GalResult<(Vec<u8>, Vec<u64>)> {
    let mut out = Vec::with_capacity(required_bytes as usize);
    let mut offsets = Vec::with_capacity(mesh_batches.len());
    let header = packed_mesh_uniform_header(frame);
    for batch in mesh_batches {
        let aligned = align_up_u64(
            out.len() as u64,
            WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64,
        )?;
        if aligned > out.len() as u64 {
            out.resize(aligned as usize, 0);
        }
        offsets.push(aligned);
        if batch.indices.is_empty()
            || batch.indices.iter().any(|index| {
                frame.mesh_instances.get(*index).map(mesh_view_layering)
                    != Some(batch.key.view_layering)
            })
        {
            return Err(GalError::invalid_argument(
                "missing or mixed view layering in mesh batch",
            ));
        }
        if let Some(projection) = batch.key.view_layering {
            let view = crate::render::shared::view_layering::apply(frame.view_matrix, Some(projection))?;
            for value in view {
                push_f32(&mut out, value);
            }
            out.extend_from_slice(&header[64..]);
        } else {
            out.extend_from_slice(&header);
        }
        append_mesh_instances(frame, frontend, batch, &mut out)?;
        if batch.key.standard_item_foil {
            if batch.key.material_mode != WORLD_MATERIAL_MODE_GLINT {
                return Err(GalError::invalid_argument(
                    "standard foil stream requires glint material",
                ));
            }
            let (foil_start, foil_end) = item_foil_stream_range(
                out.len() as u64,
                batch.count(),
                batch.key.decal_vertex_count,
            )?;
            let payload = if batch.key.decal_vertex_count == 0 {
                packed_standard_item_foil_instances(&frame.mesh_instances, &batch.indices)?
            } else {
                packed_decal_item_foil_instances(frame, frontend, batch)?
            };
            out.resize(foil_start as usize, 0);
            out.extend_from_slice(&payload);
            debug_assert_eq!(out.len() as u64, foil_end);
        } else if batch
            .indices
            .iter()
            .any(|index| frame.mesh_instances[*index].item_foil.is_some())
        {
            return Err(GalError::invalid_argument(
                "standard foil semantics cannot use a legacy mesh binding",
            ));
        }
    }
    Ok((out, offsets))
}

pub(in crate::render::worldrender) struct PackedMeshDrawStream {
    pub(in crate::render::worldrender) payload: Vec<u8>,
    pub(in crate::render::worldrender) dynamic_offsets: Vec<u64>,
    pub(in crate::render::worldrender) first_instances: Vec<Option<u32>>,
}

pub(in crate::render::worldrender) fn mesh_batch_supports_page_indirect(batch: &MeshBatch, enable_translucent: bool) -> bool {
    !batch.key.g_buffer
        && batch.key.stratum == WORLD_STRATUM_TERRAIN
        && (matches!(
            batch.key.material_mode,
            WORLD_MATERIAL_MODE_OPAQUE | WORLD_MATERIAL_MODE_CUTOUT
        ) || (enable_translucent
            && matches!(
                batch.key.material_mode,
                WORLD_MATERIAL_MODE_TRANSLUCENT | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
            )))
        && batch.key.texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS
        && !batch.key.standard_item_foil
        && batch.key.view_layering.is_none()
        && batch.key.decal_vertex_count == 0
}

/// Packs one common header and a contiguous instance array for ordinary
/// opaque/cutout terrain. Other semantic routes retain their existing aligned
/// per-batch ABI, so source shaders, foil, entities and ordered translucency
/// are unaffected by the page-addressed indirect path.
pub(in crate::render::worldrender) fn packed_mesh_draw_stream(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    mesh_batches: &[MeshBatch],
    capacity_hint: u64,
    enable_page_indirect: bool,
    enable_translucent_page_indirect: bool,
) -> GalResult<PackedMeshDrawStream> {
    let mut payload = Vec::with_capacity(capacity_hint as usize);
    let mut dynamic_offsets = vec![0; mesh_batches.len()];
    let mut first_instances = vec![None; mesh_batches.len()];
    let header = packed_mesh_uniform_header(frame);
    let mut next_instance = 0u32;
    if enable_page_indirect
        && mesh_batches
            .iter()
            .any(|batch| mesh_batch_supports_page_indirect(batch, enable_translucent_page_indirect))
    {
        payload.extend_from_slice(&header);
        for (index, batch) in mesh_batches.iter().enumerate() {
            if enable_page_indirect
                && mesh_batch_supports_page_indirect(batch, enable_translucent_page_indirect)
            {
                let non_translation = batch.indices.iter().any(|instance_index| {
                    !is_translation_only_transform(&frame.mesh_instances[*instance_index].transform)
                });
                if non_translation
                    && matches!(
                        batch.key.material_mode,
                        WORLD_MATERIAL_MODE_TRANSLUCENT | WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
                    )
                {
                    continue;
                }
                if non_translation {
                    return Err(GalError::invalid_argument(
                        "compact vanilla terrain placement must be translation-only",
                    ));
                }
                first_instances[index] = Some(next_instance);
                next_instance =
                    next_instance
                        .checked_add(batch.count() as u32)
                        .ok_or_else(|| {
                            GalError::invalid_argument(
                                "world mesh indirect instance index overflow",
                            )
                        })?;
                append_mesh_instances(frame, frontend, batch, &mut payload)?;
            }
        }
    }
    for (index, batch) in mesh_batches.iter().enumerate() {
        if first_instances[index].is_some() {
            continue;
        }
        let aligned = align_up_u64(
            payload.len() as u64,
            WORLD_MESH_INSTANCE_STREAM_ALIGNMENT as u64,
        )?;
        payload.resize(aligned as usize, 0);
        dynamic_offsets[index] = aligned;
        if batch.indices.is_empty()
            || batch.indices.iter().any(|instance| {
                frame.mesh_instances.get(*instance).map(mesh_view_layering)
                    != Some(batch.key.view_layering)
            })
        {
            return Err(GalError::invalid_argument(
                "missing or mixed view layering in mesh batch",
            ));
        }
        if let Some(projection) = batch.key.view_layering {
            let view = crate::render::shared::view_layering::apply(frame.view_matrix, Some(projection))?;
            for value in view {
                push_f32(&mut payload, value);
            }
            payload.extend_from_slice(&header[64..]);
        } else {
            payload.extend_from_slice(&header);
        }
        append_mesh_instances(frame, frontend, batch, &mut payload)?;
        if batch.key.standard_item_foil {
            let (start, end) = item_foil_stream_range(
                payload.len() as u64,
                batch.count(),
                batch.key.decal_vertex_count,
            )?;
            let foil = if batch.key.decal_vertex_count == 0 {
                packed_standard_item_foil_instances(&frame.mesh_instances, &batch.indices)?
            } else {
                packed_decal_item_foil_instances(frame, frontend, batch)?
            };
            payload.resize(start as usize, 0);
            payload.extend_from_slice(&foil);
            debug_assert_eq!(payload.len() as u64, end);
        }
    }
    if payload.len() as u64 > LOWERED_SOURCE_FRAME_STREAM_MAX_BYTES {
        return Err(GalError::invalid_argument(
            "world mesh packed draw stream exceeds bounded frame bytes",
        ));
    }
    Ok(PackedMeshDrawStream {
        payload,
        dynamic_offsets,
        first_instances,
    })
}

pub(in crate::render::worldrender) fn packed_mesh_uniform_header(frame: &WorldPrimitiveFrame) -> Vec<u8> {
    let mut out = Vec::with_capacity(WORLD_MESH_BATCH_HEADER_BYTES);
    for value in frame.view_matrix {
        push_f32(&mut out, value);
    }
    for value in frame.projection_matrix {
        push_f32(&mut out, value);
    }
    for value in shadow_light_view_projection_matrix() {
        push_f32(&mut out, value);
    }
    for value in shader_shadow_params(true) {
        push_f32(&mut out, value);
    }
    let fog = frame.shader_environment.fog_parameter_color;
    for value in [
        fog[0],
        fog[1],
        fog[2],
        frame.shader_environment.fog_environmental_start,
    ] {
        push_f32(&mut out, value);
    }
    for value in [
        frame.shader_environment.fog_environmental_end,
        frame.shader_environment.fog_render_distance_start,
        frame.shader_environment.fog_render_distance_end,
        // Direct terrain consumes this same ABI header rather than the
        // deferred composite uniform block. Preserve Sodium's fogColor.a so
        // the per-vertex fog factor is not silently forced to zero.
        fog[3],
    ] {
        push_f32(&mut out, value);
    }
    out
}

pub(in crate::render::worldrender) fn append_mesh_instances(
    frame: &WorldPrimitiveFrame,
    frontend: &WorldPrimitiveFrontend,
    batch: &MeshBatch,
    out: &mut Vec<u8>,
) -> GalResult<()> {
    // Animated atlas phases follow the semantic game frame counter when the
    // caller provides shader timing. Render-frame IDs are only a diagnostic
    // fallback for legacy/minimal frames that intentionally omit it; using
    // them unconditionally makes animation speed depend on FPS.
    let animation_phase = if frame.shader_environment.enabled {
        u64::try_from(frame.shader_environment.frame_counter).map_err(|_| {
            GalError::invalid_argument(
                "enabled shader environment frame counter must be non-negative",
            )
        })?
    } else {
        frame.frame_id
    };
    // The immutable texture asset owns this table. Sampling it by reference
    // avoids cloning the animation frame vector once per mesh batch; the
    // resulting phase/regions are identical to the owned helper used by
    // animation-focused callers.
    let animation_sample =
        frontend.world_mesh_texture_animation_sample(batch.key.texture_id, animation_phase);
    for instance_index in &batch.indices {
        let instance = &frame.mesh_instances[*instance_index];
        for value in instance.transform {
            push_f32(out, value);
        }
        for value in argb_to_rgba(instance.color_argb) {
            push_f32(out, value);
        }
        if batch.key.material_mode == WORLD_MATERIAL_MODE_CUTOUT
            || batch.key.material_mode == WORLD_MATERIAL_MODE_TRANSLUCENT_CUTOUT
        {
            push_f32(
                out,
                assets::material_registry::cutout_threshold(batch.key.material_id),
            );
        } else {
            push_f32(out, 0.0);
        }
        push_f32(out, if animation_sample.animated { 1.0 } else { 0.0 });
        push_f32(out, animation_sample.interpolation);
        // Independent Rust-owned shader semantics: atlas coordinates, model
        // diffuse lighting, Nether light direction, and lightmap sampling.
        // Atlas-backed models must not lose their lighting or their atlas UVs.
        push_f32(
            out,
            (mesh_material_semantics(batch.key.stratum, batch.key.texture_id,
                frame.background.sky_type)
                // Only cutout model passes consume their declared cutoff.
                // Preserve ordinary lighting/atlas semantics for other modes.
                | if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH
                    && assets::material_registry::per_face_lighting(batch.key.material_id) { 64 } else { 0 }
                | if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH
                    && batch.key.material_mode == WORLD_MATERIAL_MODE_CUTOUT { 32 } else { 0 }
				| if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH
					&& assets::material_registry::fullbright_without_cardinal_lighting(batch.key.material_id) { 128 } else { 0 }
                | if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH
                    && assets::material_registry::lightmapped_without_cardinal_lighting(batch.key.material_id) { 256 } else { 0 }
                | if batch.key.stratum == WORLD_STRATUM_ENTITY_MESH
					&& assets::material_registry::fullbright_with_cardinal_lighting(batch.key.material_id) { 512 } else { 0 }
				| if instance.packed_light != 0 { 1024 } else { 0 }) as f32,
        );
        for value in animation_sample.current_region {
            push_f32(out, value);
        }
        for value in animation_sample.next_region {
            push_f32(out, value);
        }
        for value in argb_to_rgba(instance.entity_color_argb) {
            push_f32(out, value);
        }
        let uv_offset_u = if instance.flags & WORLD_MESH_INSTANCE_FLAG_UV_OFFSET_U != 0 {
            ((instance.flags & WORLD_MESH_INSTANCE_UV_OFFSET_PAYLOAD)
                >> WORLD_MESH_INSTANCE_UV_OFFSET_SHIFT) as f32
                / WORLD_MESH_INSTANCE_UV_OFFSET_MAX as f32
        } else {
            0.0
        };
        let packed_light = if instance.packed_light != 0 {
            f32::from_bits(instance.packed_light)
        } else {
            0.0
        };
        for value in [1.0, 1.0, uv_offset_u, packed_light] {
            push_f32(out, value);
        }
    }
    Ok(())
}

pub(in crate::render::worldrender) fn is_translation_only_transform(transform: &[f32; 16]) -> bool {
    [0usize, 5, 10, 15]
        .into_iter()
        .all(|index| transform[index] == 1.0)
        && [1usize, 2, 3, 4, 6, 7, 8, 9, 11]
            .into_iter()
            .all(|index| transform[index] == 0.0)
}

/// Chooses the UV semantic owned by a copied mesh texture. This is a frontend
/// resource contract, not a backend texture-property query.
pub(in crate::render::worldrender) fn mesh_texture_uses_atlas_coordinates(texture_id: u32) -> bool {
    texture_id == WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS
}

pub(in crate::render::worldrender) fn mesh_material_semantics(stratum: u32, texture_id: u32, sky_type: u32) -> u32 {
    let atlas = u32::from(mesh_texture_uses_atlas_coordinates(texture_id));
    atlas
        | match stratum {
            // Frozen Sodium chunks use level/16; vanilla baked blocks use
            // core/terrain.vsh's half-texel offset. Entity and moving-block
            // shaders instead fetch UV2/16 directly. These are not interchangeable
            // with Sodium's boundary-filtered lookup.
            WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY | WORLD_STRATUM_ORDINARY_BLOCK => 8,
            WORLD_STRATUM_MOVING_MESH => 16,
            WORLD_STRATUM_ENTITY_MESH => {
                16 | 2
                    | if sky_type == WORLD_BACKGROUND_SKY_NETHER {
                        4
                    } else {
                        0
                    }
            }
            _ => 0,
        }
}

pub(in crate::render::worldrender) fn terrain_composite_uniforms(
    frame: &WorldPrimitiveFrame,
    enabled: bool,
) -> GalResult<TerrainCompositeUniforms> {
    let projection_inverse =
        invert_column_major_mat4(frame.projection_matrix, "terrain fog projection")?;
    Ok(TerrainCompositeUniforms {
        light_view_projection: shadow_light_view_projection_matrix(),
        shadow_params: shader_shadow_params(enabled),
        color_grade_params: [1.0, 0.0, 1.0, 1.0],
        projection_inverse,
        fog_color_and_environmental_start: [
            frame.shader_environment.fog_parameter_color[0],
            frame.shader_environment.fog_parameter_color[1],
            frame.shader_environment.fog_parameter_color[2],
            frame.shader_environment.fog_environmental_start,
        ],
        fog_ranges: [
            frame.shader_environment.fog_environmental_end,
            frame.shader_environment.fog_render_distance_start,
            frame.shader_environment.fog_render_distance_end,
            // Sodium's `_linearFog` multiplies the resolved fog fraction by
            // the semantic fog color alpha. This is source-independent
            // vanilla frame data, not a renderer-state query.
            frame.shader_environment.fog_parameter_color[3],
        ],
    })
}

/// Inputs that fully determine a static terrain instance's section ranges,
/// given a resident drawable generation and per-texture animation classes.
#[derive(Clone, Copy, Debug, Eq, Hash, PartialEq)]
pub(in crate::render::worldrender) struct MeshRangeMemoKey {
    mesh_key: u64,
    mesh_generation: u64,
    depth_policy: u32,
    terrain_visible_facing_mask: u8,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
}

impl MeshRangeMemoKey {
    /// Only plain static terrain sections (no foil, decal, layering, sorted
    /// quads or single-section selection) take the memoized path. The mask is
    /// the one the selection draws with (see `mesh_batches_core_indices`).
    fn for_instance(
        instance: &WorldMeshInstanceRequest,
        selection: MeshBatchSelection,
        color_format: ColorFormat,
        raster_y_direction: RasterYDirection,
        g_buffer: bool,
    ) -> Option<Self> {
        if instance.stratum != WORLD_STRATUM_TERRAIN
            || instance.mesh_section_index != WORLD_MESH_SECTION_ALL
            || instance.flags & WORLD_MESH_INSTANCE_FLAG_CAMERA_SORTED_QUADS != 0
            || instance.item_foil.is_some()
            || instance.decal_foil.is_some()
            || mesh_view_layering(instance).is_some()
            || per_section_terrain_animation_disabled()
        {
            return None;
        }
        let terrain_visible_facing_mask =
            if matches!(selection, MeshBatchSelection::ShadowOnly | MeshBatchSelection::ShadowSupplement) {
                if selection == MeshBatchSelection::ShadowOnly
                    || instance.flags & WORLD_MESH_INSTANCE_FLAG_SHADOW_ONLY != 0
                {
                    0x7f
                } else {
                    !instance.terrain_visible_facing_mask & 0x7f
                }
            } else {
                instance.terrain_visible_facing_mask
            };
        Some(Self {
            mesh_key: instance.mesh_key,
            mesh_generation: instance.mesh_generation,
            depth_policy: instance.depth_policy,
            terrain_visible_facing_mask,
            color_format,
            raster_y_direction,
            g_buffer,
        })
    }
}

/// Section ranges of resident, non-optical static terrain generations, valid
/// for one texture-animation generation. Batch plans miss whenever the
/// visible set moves; this lets a miss skip the large asset stores.
#[derive(Default)]
pub(in crate::render::worldrender) struct MeshRangeMemo {
    animation_generation: u64,
    entries: HashMap<MeshRangeMemoKey, Arc<Vec<MeshSectionRange>>, MeshKeyBuildHasher>,
}

impl MeshRangeMemo {
    fn get(&mut self, key: MeshRangeMemoKey, animation_generation: u64) -> Option<Arc<Vec<MeshSectionRange>>> {
        if self.animation_generation != animation_generation {
            self.entries.clear();
            self.animation_generation = animation_generation;
            return None;
        }
        self.entries.get(&key).cloned()
    }

    fn insert(
        &mut self,
        key: MeshRangeMemoKey,
        animation_generation: u64,
        ranges: Arc<Vec<MeshSectionRange>>,
        resident_meshes: usize,
    ) {
        if self.animation_generation != animation_generation {
            self.entries.clear();
            self.animation_generation = animation_generation;
        }
        // Keys are per generation and facing mask; bound stale entries.
        if self.entries.len() >= 4 * resident_meshes + 4096 {
            self.entries.clear();
        }
        self.entries.insert(key, ranges);
    }

    pub(in crate::render::worldrender) fn clear(&mut self) {
        self.entries.clear();
    }

    #[cfg(test)]
    pub(in crate::render::worldrender) fn len(&self) -> usize {
        self.entries.len()
    }
}

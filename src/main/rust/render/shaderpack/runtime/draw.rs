//! Indexed and direct draw emission, draw-state dedup and barrier helpers.

use super::*;

pub(crate) struct IndexedDrawState {
    pub(super) pipeline: Option<Handle>,
    pub(super) resource_set: Option<(Handle, u32, Handle, Vec<u64>)>,
    pub(super) shader_resource_set: Option<(Handle, u32, Handle)>,
    pub(super) index_buffer: Option<(Handle, u64, IndexType)>,
    pub(super) max_indirect_draw_count: u32,
}

impl IndexedDrawState {
    pub(crate) fn with_indirect_limit(max_indirect_draw_count: u32) -> Self {
        Self {
            pipeline: None,
            resource_set: None,
            shader_resource_set: None,
            index_buffer: None,
            max_indirect_draw_count: max_indirect_draw_count.max(1),
        }
    }
}

impl Default for IndexedDrawState {
    fn default() -> Self {
        // Source-graph callers currently emit direct indexed draws. Keep the
        // conservative VulkanicGAL ceiling for any indirect run they add.
        Self::with_indirect_limit(4_096)
    }
}

/// Shared explicit binding cache for direct source-material draws. It stays
/// separate from indexed terrain state so a future source writer cannot leave
/// an index-buffer assumption attached to its pass.
#[derive(Default)]
pub(super) struct DirectDrawState {
    pub(super) pipeline: Option<Handle>,
    pub(super) resource_set: Option<(Handle, u32, Handle, Vec<u64>)>,
    pub(super) shader_resource_set: Option<(Handle, u32, Handle)>,
}

#[allow(clippy::too_many_arguments)]
pub(super) fn append_direct_draw(
    ops: &mut Vec<CommandOp>,
    state: &mut DirectDrawState,
    pipeline: Handle,
    pipeline_layout: Handle,
    resource_set: Handle,
    resource_set_dynamic_offsets: &[u64],
    shader_resource_set: Option<TerrainShaderResourceSet>,
    vertices: u32,
) {
    if state.pipeline != Some(pipeline) {
        ops.push(CommandOp::BindGraphicsPipeline(pipeline));
        state.pipeline = Some(pipeline);
        state.resource_set = None;
        state.shader_resource_set = None;
    }
    let resource_set_matches = state.resource_set.as_ref().is_some_and(
        |(layout, set_index, bound_set, dynamic_offsets)| {
            *layout == pipeline_layout
                && *set_index == 0
                && *bound_set == resource_set
                && dynamic_offsets.as_slice() == resource_set_dynamic_offsets
        },
    );
    if !resource_set_matches {
        let dynamic_offsets = resource_set_dynamic_offsets.to_vec();
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: dynamic_offsets.clone(),
        });
        state.resource_set = Some((pipeline_layout, 0, resource_set, dynamic_offsets));
    }
    let shader_resource_set_binding =
        shader_resource_set.map(|binding| (pipeline_layout, binding.set_index, binding.set));
    if state.shader_resource_set != shader_resource_set_binding {
        if let Some((pipeline_layout, set_index, set)) = shader_resource_set_binding {
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout,
                set_index,
                set,
                dynamic_offsets: Vec::new(),
            });
        }
        state.shader_resource_set = shader_resource_set_binding;
    }
    ops.push(CommandOp::Draw {
        vertices,
        instances: 1,
    });
}

#[allow(clippy::too_many_arguments)]
pub(crate) fn append_indexed_draw(
    ops: &mut Vec<CommandOp>,
    state: &mut IndexedDrawState,
    pipeline: Handle,
    pipeline_layout: Handle,
    resource_set: Handle,
    resource_set_dynamic_offsets: &[u64],
    shader_resource_set: Option<TerrainShaderResourceSet>,
    index_buffer: Handle,
    index_offset: u64,
    index_type: IndexType,
    index_count: u32,
    instance_count: u32,
    indexed_indirect: Option<TerrainIndexedIndirect>,
) {
    if state.pipeline != Some(pipeline) {
        ops.push(CommandOp::BindGraphicsPipeline(pipeline));
        state.pipeline = Some(pipeline);
        state.resource_set = None;
        state.shader_resource_set = None;
    }
    let resource_set_matches = state.resource_set.as_ref().is_some_and(
        |(layout, set_index, bound_set, dynamic_offsets)| {
            *layout == pipeline_layout
                && *set_index == 0
                && *bound_set == resource_set
                && dynamic_offsets.as_slice() == resource_set_dynamic_offsets
        },
    );
    if !resource_set_matches {
        let dynamic_offsets = resource_set_dynamic_offsets.to_vec();
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: dynamic_offsets.clone(),
        });
        state.resource_set = Some((pipeline_layout, 0, resource_set, dynamic_offsets));
    }
    let shader_resource_set_binding =
        shader_resource_set.map(|binding| (pipeline_layout, binding.set_index, binding.set));
    if state.shader_resource_set != shader_resource_set_binding {
        if let Some((pipeline_layout, set_index, set)) = shader_resource_set_binding {
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout,
                set_index,
                set,
                dynamic_offsets: Vec::new(),
            });
        }
        state.shader_resource_set = shader_resource_set_binding;
    }
    let index_binding = (index_buffer, index_offset, index_type);
    if state.index_buffer != Some(index_binding) {
        ops.push(CommandOp::SetIndexBuffer {
            buffer: index_buffer,
            offset: index_offset,
            index_type,
        });
        state.index_buffer = Some(index_binding);
    }
    if let Some(indirect) = indexed_indirect {
        let mut offset = indirect.offset;
        let mut remaining = indirect.draw_count.max(1);
        // Extend a contiguous preceding run, then emit the rest in chunks no
        // larger than the backend's per-command limit.
        if let Some(CommandOp::DrawIndexedIndirect {
            buffer,
            offset: previous_offset,
            draw_count,
        }) = ops.last_mut()
        {
            let expected = previous_offset.saturating_add(u64::from(*draw_count) * 20);
            if *buffer == indirect.buffer && expected == offset && *draw_count < state.max_indirect_draw_count {
                let merged = remaining.min(state.max_indirect_draw_count - *draw_count);
                *draw_count += merged;
                remaining -= merged;
                offset += u64::from(merged) * 20;
            }
        }
        while remaining > 0 {
            let count = remaining.min(state.max_indirect_draw_count);
            ops.push(CommandOp::DrawIndexedIndirect {
                buffer: indirect.buffer,
                offset,
                draw_count: count,
            });
            remaining -= count;
            offset += u64::from(count) * 20;
        }
    } else {
        ops.push(CommandOp::DrawIndexed {
            indices: index_count,
            instances: instance_count,
        });
    }
}

pub(super) fn buffer_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn transparent_clear(color: ClearColor) -> ClearColor {
    ClearColor {
        r: color.r,
        g: color.g,
        b: color.b,
        a: 0.0,
    }
}

pub(super) fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

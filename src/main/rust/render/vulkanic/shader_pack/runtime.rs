use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{IndexType, QueueClass};

use super::pass_graph::{AttachmentRole, PassIdentity};
use super::resources::ShaderPackRuntimePlan;

pub(crate) const TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES: u64 = 16 * 4 + 4 * 4 + 4 * 4 + 4 * 4;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum TerrainMaterialPassMode {
    Opaque,
    Cutout,
    Translucent,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum TerrainGraphIsolation {
    Full,
    TerrainOnly,
    GBufferNoShadow,
    TerrainPlusShadow,
    FullDrawsSkipped,
}

impl TerrainGraphIsolation {
    fn from_env() -> Self {
        match std::env::var("MATTMC_RUST_SHADER_GRAPH_ISOLATION")
            .unwrap_or_default()
            .trim()
        {
            "terrain-only" => Self::TerrainOnly,
            "terrain-plus-gbuffer-no-shadow" | "gbuffer-no-shadow" => Self::GBufferNoShadow,
            "terrain-plus-shadow" => Self::TerrainPlusShadow,
            "full-draws-skipped" => Self::FullDrawsSkipped,
            _ => Self::Full,
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainMeshDraw {
    pub shadow_pipeline: Option<Handle>,
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    pub resource_set_dynamic_offsets: [u64; 1],
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub instance_count: u32,
    pub material_mode: TerrainMaterialPassMode,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainRuntimeTargets {
    pub shadow_depth_texture: Handle,
    pub shadow_depth_view: Handle,
    pub shadow_target: Handle,
    pub shadow_pass: Handle,
    pub albedo_texture: Handle,
    pub albedo_view: Handle,
    pub normal_texture: Handle,
    pub normal_view: Handle,
    pub material_light_texture: Handle,
    pub material_light_view: Handle,
    pub world_position_texture: Handle,
    pub world_position_view: Handle,
    pub depth_texture: Handle,
    pub depth_view: Handle,
    pub target: Handle,
    pub g_buffer_pass: Handle,
    pub deferred_lit_texture: Handle,
    pub deferred_lit_view: Handle,
    pub deferred_lit_target: Handle,
    pub deferred_lighting_pass: Handle,
    pub deferred_lighting_pipeline: Handle,
    pub deferred_lighting_resource_set: Handle,
    pub translucent_target: Handle,
    pub translucent_pass: Handle,
    pub composite0_texture: Handle,
    pub composite0_view: Handle,
    pub composite0_target: Handle,
    pub composite0_pass: Handle,
    pub composite0_pipeline: Handle,
    pub composite0_resource_set: Handle,
    pub composite1_texture: Handle,
    pub composite1_view: Handle,
    pub composite1_target: Handle,
    pub composite1_pass: Handle,
    pub composite1_pipeline: Handle,
    pub composite1_resource_set: Handle,
    pub final_pass: Handle,
    pub final_pipeline: Handle,
    pub final_resource_set: Handle,
    pub screen_pipeline_layout: Handle,
    pub composite_uniform_buffer: Handle,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainRuntimeFrame {
    pub frame_target: Handle,
    pub color_attachment: Handle,
    pub background_color: ClearColor,
    pub final_depth_view: Option<Handle>,
    pub uniforms: TerrainCompositeUniforms,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainCompositeUniforms {
    pub light_view_projection: [f32; 16],
    pub shadow_params: [f32; 4],
    pub color_grade_params: [f32; 4],
    pub fog_params: [f32; 4],
}

#[derive(Clone, Debug)]
pub(crate) struct ShaderPackRuntimeExecutor {
    plan: ShaderPackRuntimePlan,
}

impl ShaderPackRuntimeExecutor {
    pub(crate) fn terrain_material_multipass_v1(generation: u64) -> GalResult<Self> {
        Ok(Self {
            plan: ShaderPackRuntimePlan::terrain_material_multipass_v1(generation)?,
        })
    }

    pub(crate) fn plan(&self) -> &ShaderPackRuntimePlan {
        &self.plan
    }

    pub(crate) fn append_terrain_material_graph(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        frame: TerrainRuntimeFrame,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        self.validate_terrain_material_graph()?;
        if draws.is_empty() {
            return Ok(());
        }
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
            self.append_shadow_depth_pass(ops, targets, effective_draws)?;
        }
        self.append_g_buffer_passes(
            ops,
            targets,
            frame.background_color,
            effective_draws,
            isolation == TerrainGraphIsolation::FullDrawsSkipped,
        )?;
        if matches!(
            isolation,
            TerrainGraphIsolation::Full
                | TerrainGraphIsolation::GBufferNoShadow
                | TerrainGraphIsolation::FullDrawsSkipped
        ) {
            self.append_deferred_and_composites(ops, targets, frame, effective_draws)?;
        }
        Ok(())
    }

    fn validate_terrain_material_graph(&self) -> GalResult<()> {
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

    fn append_shadow_depth_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        let pass = self.pass_identity(AttachmentRole::ShadowDepth)?;
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.shadow_depth_texture,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.shadow_pass,
            target: targets.shadow_target,
            colors: Vec::new(),
            depth_stencil: Some(PassAttachment {
                view: targets.shadow_depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = IndexedDrawState::default();
        for draw in draws
            .iter()
            .filter(|draw| draw.material_mode != TerrainMaterialPassMode::Translucent)
        {
            let shadow_pipeline = draw.shadow_pipeline.ok_or_else(|| {
                GalError::backend(format!(
                    "{} mesh draw missing shadow pipeline",
                    pass.as_str()
                ))
            })?;
            append_indexed_draw(
                ops,
                &mut draw_state,
                shadow_pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                draw.resource_set_dynamic_offsets,
                draw.index_buffer,
                draw.index_offset,
                draw.index_type,
                draw.index_count,
                draw.instance_count,
            );
        }
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.shadow_depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    fn append_g_buffer_passes(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        background_color: ClearColor,
        draws: &[TerrainMeshDraw],
        force_empty_clear: bool,
    ) -> GalResult<()> {
        for texture in [
            targets.albedo_texture,
            targets.normal_texture,
            targets.material_light_texture,
            targets.world_position_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::Undefined,
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
            let load_op = if wrote_g_buffer {
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
                    draw.resource_set_dynamic_offsets,
                    draw.index_buffer,
                    draw.index_offset,
                    draw.index_type,
                    draw.index_count,
                    draw.instance_count,
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

    fn append_deferred_and_composites(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        frame: TerrainRuntimeFrame,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
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
        );
        self.append_translucent_pass(ops, targets, draws)?;
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
            depth_stencil: frame.final_depth_view.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
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

    fn append_translucent_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        if !draws
            .iter()
            .any(|draw| draw.material_mode == TerrainMaterialPassMode::Translucent)
        {
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
        for draw in draws
            .iter()
            .filter(|draw| draw.material_mode == TerrainMaterialPassMode::Translucent)
        {
            append_indexed_draw(
                ops,
                &mut draw_state,
                draw.pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                draw.resource_set_dynamic_offsets,
                draw.index_buffer,
                draw.index_offset,
                draw.index_type,
                draw.index_count,
                draw.instance_count,
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
    fn append_screen_pass(
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
    ) {
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::Undefined,
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

    fn pass_identity(&self, role: AttachmentRole) -> GalResult<&PassIdentity> {
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
        for value in self.fog_params {
            push_f32(&mut out, value);
        }
        out
    }
}

#[derive(Default)]
struct IndexedDrawState {
    pipeline: Option<Handle>,
    resource_set: Option<(Handle, u32, Handle, [u64; 1])>,
    index_buffer: Option<(Handle, u64, IndexType)>,
}

#[allow(clippy::too_many_arguments)]
fn append_indexed_draw(
    ops: &mut Vec<CommandOp>,
    state: &mut IndexedDrawState,
    pipeline: Handle,
    pipeline_layout: Handle,
    resource_set: Handle,
    resource_set_dynamic_offsets: [u64; 1],
    index_buffer: Handle,
    index_offset: u64,
    index_type: IndexType,
    index_count: u32,
    instance_count: u32,
) {
    if state.pipeline != Some(pipeline) {
        ops.push(CommandOp::BindGraphicsPipeline(pipeline));
        state.pipeline = Some(pipeline);
        state.resource_set = None;
    }
    let resource_set_binding = (
        pipeline_layout,
        0,
        resource_set,
        resource_set_dynamic_offsets,
    );
    if state.resource_set != Some(resource_set_binding) {
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: resource_set_dynamic_offsets.to_vec(),
        });
        state.resource_set = Some(resource_set_binding);
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
    ops.push(CommandOp::DrawIndexed {
        indices: index_count,
        instances: instance_count,
    });
}

fn buffer_barrier(
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

fn texture_barrier(
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

fn transparent_clear(color: ClearColor) -> ClearColor {
    ClearColor {
        r: color.r,
        g: color.g,
        b: color.b,
        a: 0.0,
    }
}

fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::handles::HandleKind;

    #[test]
    fn runtime_executor_owns_expected_gameplay_pass_order() {
        let executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
        let passes = executor
            .plan()
            .graph
            .passes()
            .iter()
            .map(|pass| pass.identity.as_str())
            .collect::<Vec<_>>();
        assert_eq!(
            passes,
            vec![
                "vulkanic:pass/shadow_depth",
                "vulkanic:pass/terrain_opaque",
                "vulkanic:pass/terrain_cutout",
                "vulkanic:pass/deferred_lighting",
                "vulkanic:pass/terrain_translucent",
                "vulkanic:pass/composite_0",
                "vulkanic:pass/composite_1",
                "vulkanic:pass/final_output",
            ]
        );
        assert!(executor.validate_terrain_material_graph().is_ok());
    }

    #[test]
    fn composite_uniform_block_layout_is_stable() {
        let uniforms = TerrainCompositeUniforms {
            light_view_projection: [1.0; 16],
            shadow_params: [2.0; 4],
            color_grade_params: [3.0; 4],
            fog_params: [4.0; 4],
        };
        assert_eq!(
            TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES as usize,
            uniforms.pack().len()
        );
    }

    #[test]
    fn indexed_draw_emission_skips_redundant_state_binds_inside_pass() {
        let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
        let other_pipeline = test_handle(HandleKind::GraphicsPipeline, 2);
        let layout = test_handle(HandleKind::PipelineLayout, 3);
        let set = test_handle(HandleKind::ResourceSet, 4);
        let other_set = test_handle(HandleKind::ResourceSet, 5);
        let index_buffer = test_handle(HandleKind::Buffer, 6);
        let mut ops = Vec::new();
        let mut state = IndexedDrawState::default();

        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            [0],
            index_buffer,
            0,
            IndexType::U32,
            6,
            1,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            [0],
            index_buffer,
            0,
            IndexType::U32,
            6,
            2,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            other_set,
            [0],
            index_buffer,
            0,
            IndexType::U32,
            6,
            3,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            other_set,
            [0],
            index_buffer,
            12,
            IndexType::U32,
            6,
            4,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            other_pipeline,
            layout,
            other_set,
            [0],
            index_buffer,
            12,
            IndexType::U32,
            6,
            5,
        );

        let pipeline_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::BindGraphicsPipeline(_)))
            .count();
        let resource_set_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
            .count();
        let index_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::SetIndexBuffer { .. }))
            .count();
        let draws = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::DrawIndexed { .. }))
            .count();

        assert_eq!(2, pipeline_binds);
        assert_eq!(3, resource_set_binds);
        assert_eq!(2, index_binds);
        assert_eq!(5, draws);
    }

    fn test_handle(kind: HandleKind, index: u32) -> Handle {
        Handle::new(kind, index, 1).unwrap()
    }
}

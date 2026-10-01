//! Command normalization: redundant binds, pass fusion and descriptor rebinding.

use super::*;

pub(super) fn normalize_ops_for_test(
    operations: Vec<CommandOp>,
) -> (CommandNormalizationStats, Vec<CommandOp>) {
    let mut batch = SubmissionBatch {
        label: "normalizer-test".to_owned(),
        command_lists: vec![CommandList {
            label: "main".to_owned(),
            operations,
        }],
    };
    let stats = normalize_submission_batch(&mut batch);
    let list = batch.command_lists.pop().unwrap();
    (stats, list.operations)
}

pub(super) fn normalize_ops_for_test_with_pipeline_layouts(
    operations: Vec<CommandOp>,
    graphics_pipeline_layouts: BTreeMap<Handle, Handle>,
) -> (CommandNormalizationStats, Vec<CommandOp>) {
    let mut batch = SubmissionBatch {
        label: "normalizer-layout-test".to_owned(),
        command_lists: vec![CommandList {
            label: "main".to_owned(),
            operations,
        }],
    };
    let stats = normalize_submission_batch_with_pipeline_layouts(
        &mut batch,
        &graphics_pipeline_layouts,
        &BTreeMap::new(),
    );
    let list = batch.command_lists.pop().unwrap();
    (stats, list.operations)
}

#[test]
fn command_normalization_removes_redundant_state_binds() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let layout = test_handle(HandleKind::PipelineLayout, 1);
    let set = test_handle(HandleKind::ResourceSet, 1);
    let vertex = test_handle(HandleKind::Buffer, 1);
    let index = test_handle(HandleKind::Buffer, 2);

    let (stats, operations) = normalize_ops_for_test(vec![
        minimal_begin_pass(),
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set,
            dynamic_offsets: Vec::new(),
        },
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set,
            dynamic_offsets: Vec::new(),
        },
        CommandOp::SetVertexBuffer {
            slot: 0,
            buffer: vertex,
            offset: 64,
        },
        CommandOp::SetVertexBuffer {
            slot: 0,
            buffer: vertex,
            offset: 64,
        },
        CommandOp::SetIndexBuffer {
            buffer: index,
            offset: 0,
            index_type: IndexType::U16,
        },
        CommandOp::SetIndexBuffer {
            buffer: index,
            offset: 0,
            index_type: IndexType::U16,
        },
        CommandOp::DrawIndexed {
            indices: 6,
            instances: 1,
        },
        CommandOp::EndPass,
    ]);

    assert_eq!(stats.ops_before, 11);
    assert_eq!(stats.ops_after, 7);
    assert_eq!(stats.pipeline_binds_removed, 1);
    assert_eq!(stats.resource_set_binds_removed, 1);
    assert_eq!(stats.vertex_buffer_binds_removed, 1);
    assert_eq!(stats.index_buffer_binds_removed, 1);
    assert!(matches!(operations[1], CommandOp::BindGraphicsPipeline(_)));
    assert!(matches!(operations[2], CommandOp::BindResourceSet { .. }));
    assert!(matches!(operations[3], CommandOp::SetVertexBuffer { .. }));
    assert!(matches!(operations[4], CommandOp::SetIndexBuffer { .. }));
    assert!(matches!(operations[5], CommandOp::DrawIndexed { .. }));
}

#[test]
fn command_normalization_fuses_adjacent_identical_loaded_passes() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let begin = minimal_begin_pass();
    let (stats, operations) = normalize_ops_for_test(vec![
        begin.clone(),
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::Draw {
            vertices: 6,
            instances: 1,
        },
        CommandOp::EndPass,
        begin,
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::Draw {
            vertices: 6,
            instances: 1,
        },
        CommandOp::EndPass,
    ]);

    assert_eq!(stats.ops_before, 8);
    assert_eq!(stats.ops_after, 5);
    assert_eq!(stats.pipeline_binds_removed, 1);
    assert_eq!(
        operations
            .iter()
            .filter(|operation| matches!(operation, CommandOp::BeginPass { .. }))
            .count(),
        1
    );
    assert_eq!(
        operations
            .iter()
            .filter(|operation| matches!(operation, CommandOp::EndPass))
            .count(),
        1
    );
}

#[test]
fn command_normalization_keeps_resource_binds_with_distinct_dynamic_offsets() {
    let layout = test_handle(HandleKind::PipelineLayout, 1);
    let set = test_handle(HandleKind::ResourceSet, 1);

    let (stats, operations) = normalize_ops_for_test(vec![
        minimal_begin_pass(),
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set,
            dynamic_offsets: vec![0],
        },
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set,
            dynamic_offsets: vec![256],
        },
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set,
            dynamic_offsets: vec![256],
        },
        CommandOp::EndPass,
    ]);

    assert_eq!(stats.resource_set_binds_removed, 1);
    assert_eq!(
        2,
        operations
            .iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
            .count()
    );
}

#[test]
fn command_normalization_rebinds_descriptor_sets_after_pipeline_change() {
    let first_pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let second_pipeline = test_handle(HandleKind::GraphicsPipeline, 2);
    let layout = test_handle(HandleKind::PipelineLayout, 1);
    let set = test_handle(HandleKind::ResourceSet, 1);

    let (stats, operations) = normalize_ops_for_test(vec![
        minimal_begin_pass(),
        CommandOp::BindGraphicsPipeline(first_pipeline),
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 1,
            set,
            dynamic_offsets: Vec::new(),
        },
        CommandOp::DrawIndexed {
            indices: 6,
            instances: 1,
        },
        CommandOp::BindGraphicsPipeline(second_pipeline),
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 1,
            set,
            dynamic_offsets: Vec::new(),
        },
        CommandOp::DrawIndexed {
            indices: 6,
            instances: 1,
        },
        CommandOp::EndPass,
    ]);

    assert_eq!(stats.resource_set_binds_removed, 0);
    assert_eq!(
        2,
        operations
            .iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
            .count()
    );
}

#[test]
fn command_normalization_retains_sets_for_pipelines_sharing_a_layout() {
    let first_pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let second_pipeline = test_handle(HandleKind::GraphicsPipeline, 2);
    let layout = test_handle(HandleKind::PipelineLayout, 1);
    let set = test_handle(HandleKind::ResourceSet, 1);
    let (stats, operations) = normalize_ops_for_test_with_pipeline_layouts(
        vec![
            minimal_begin_pass(),
            CommandOp::BindGraphicsPipeline(first_pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout: layout,
                set_index: 1,
                set,
                dynamic_offsets: Vec::new(),
            },
            CommandOp::DrawIndexed {
                indices: 6,
                instances: 1,
            },
            CommandOp::BindGraphicsPipeline(second_pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout: layout,
                set_index: 1,
                set,
                dynamic_offsets: Vec::new(),
            },
            CommandOp::DrawIndexed {
                indices: 6,
                instances: 1,
            },
            CommandOp::EndPass,
        ],
        BTreeMap::from([(first_pipeline, layout), (second_pipeline, layout)]),
    );
    assert_eq!(stats.resource_set_binds_removed, 1);
    assert_eq!(
        1,
        operations
            .iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
            .count()
    );
}

#[test]
fn command_normalization_respects_barriers_while_fusing_identical_passes() {
    let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
    let texture = test_handle(HandleKind::Texture, 1);
    let barrier = CommandOp::Barrier(ResourceBarrier {
        resource: texture,
        subresources: None,
        before: TextureUsageState::TransferDst,
        after: TextureUsageState::ShaderRead,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    });

    let (stats, operations) = normalize_ops_for_test(vec![
        minimal_begin_pass(),
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::BindGraphicsPipeline(pipeline),
        barrier,
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::EndPass,
        minimal_begin_pass(),
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::EndPass,
    ]);

    assert_eq!(stats.ops_before, 9);
    assert_eq!(stats.ops_after, 5);
    assert_eq!(stats.pipeline_binds_removed, 2);
    let kept_pipeline_binds = operations
        .iter()
        .filter(|op| matches!(op, CommandOp::BindGraphicsPipeline(_)))
        .count();
    assert_eq!(kept_pipeline_binds, 2);
}

#[test]
fn command_normalization_keeps_distinct_state_changes() {
    let pipeline_a = test_handle(HandleKind::GraphicsPipeline, 1);
    let pipeline_b = test_handle(HandleKind::GraphicsPipeline, 2);
    let layout = test_handle(HandleKind::PipelineLayout, 1);
    let set_a = test_handle(HandleKind::ResourceSet, 1);
    let set_b = test_handle(HandleKind::ResourceSet, 2);
    let index = test_handle(HandleKind::Buffer, 1);

    let (stats, operations) = normalize_ops_for_test(vec![
        minimal_begin_pass(),
        CommandOp::BindGraphicsPipeline(pipeline_a),
        CommandOp::BindGraphicsPipeline(pipeline_b),
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set: set_a,
            dynamic_offsets: Vec::new(),
        },
        CommandOp::BindResourceSet {
            pipeline_layout: layout,
            set_index: 0,
            set: set_b,
            dynamic_offsets: Vec::new(),
        },
        CommandOp::SetIndexBuffer {
            buffer: index,
            offset: 0,
            index_type: IndexType::U16,
        },
        CommandOp::SetIndexBuffer {
            buffer: index,
            offset: 2,
            index_type: IndexType::U16,
        },
        CommandOp::SetIndexBuffer {
            buffer: index,
            offset: 2,
            index_type: IndexType::U32,
        },
        CommandOp::EndPass,
    ]);

    assert_eq!(stats.ops_before, 9);
    assert_eq!(stats.ops_after, 9);
    assert_eq!(stats.pipeline_binds_removed, 0);
    assert_eq!(stats.resource_set_binds_removed, 0);
    assert_eq!(stats.index_buffer_binds_removed, 0);
    assert_eq!(operations.len(), 9);
}

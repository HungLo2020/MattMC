//! Handle generations, dependencies, deferred destroys during recording and failed creates.

use super::*;

#[test]
fn handles_reuse_generations_and_reject_stale_or_wrong_types() {
    let mut gal = gal();
    let first = gal
        .create_buffer(buffer("first", vec![BufferUsage::Vertex]))
        .unwrap();
    assert_eq!(first.kind(), Some(HandleKind::Buffer));
    assert_eq!(first.index(), 0);
    assert_eq!(first.generation(), 1);

    gal.destroy(first).unwrap();
    assert_code(gal.destroy(first), super::StatusCode::DoubleDestroy);

    let second = gal
        .create_buffer(buffer("second", vec![BufferUsage::Vertex]))
        .unwrap();
    assert_eq!(second.index(), first.index());
    assert_eq!(second.generation(), first.generation() + 1);

    assert_code(
        gal.create_texture_view(view("wrong-resource", second, TextureFormat::Rgba8Unorm)),
        super::StatusCode::WrongHandleType,
    );
}

#[test]
fn dependencies_block_parent_destruction_and_allow_child_cleanup() {
    let mut gal = gal();
    let texture = gal
        .create_texture(texture(
            "tex",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::Sampled],
        ))
        .unwrap();
    let view = gal
        .create_texture_view(view("view", texture, TextureFormat::Rgba8Unorm))
        .unwrap();

    assert_code(gal.destroy(texture), super::StatusCode::DependencyViolation);
    gal.destroy(view).unwrap();
    gal.destroy(texture).unwrap();
    assert_code(gal.destroy(view), super::StatusCode::DoubleDestroy);
}

#[test]
fn command_recording_keeps_destroyed_resource_set_generation_live_until_submit() {
    let mut gal = gal();
    let buffer = gal
        .create_buffer(buffer("recorded-set-buffer", vec![BufferUsage::Uniform]))
        .unwrap();
    let resource_layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "recorded-set-layout".to_owned(),
            bindings: vec![layout_binding(
                0,
                ResourceBindingKind::UniformBuffer,
                PipelineStageFlags::COMPUTE,
            )],
        })
        .unwrap();
    let pipeline_layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "recorded-set-pipeline-layout".to_owned(),
            resource_layouts: vec![resource_layout],
        })
        .unwrap();
    let compute_shader = gal
        .create_shader_module(shader("recorded-set-compute", ShaderStage::Compute))
        .unwrap();
    let pipeline = gal
        .create_compute_pipeline(ComputePipelineDesc {
            label: "recorded-set-pipeline".to_owned(),
            layout: pipeline_layout,
            shader: compute_shader,
        })
        .unwrap();
    let make_set = |gal: &mut VulkanicGal, label: &str| {
        gal.create_resource_set(ResourceSetDesc {
            label: label.to_owned(),
            layout: resource_layout,
            bindings: vec![resource_binding(
                0,
                buffer,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            )],
        })
        .unwrap()
    };
    let recorded = make_set(&mut gal, "recorded-set");
    let commands = CommandList::from(CommandListDesc {
        label: "recorded-set-commands".to_owned(),
        operations: vec![
            CommandOp::BindComputePipeline(pipeline),
            CommandOp::BindResourceSet {
                pipeline_layout,
                set_index: 0,
                set: recorded,
                dynamic_offsets: Vec::new(),
            },
            CommandOp::Dispatch {
                groups_x: 1,
                groups_y: 1,
                groups_z: 1,
            },
        ],
    });

    gal.begin_command_recording().unwrap();
    gal.destroy(recorded).unwrap();
    let replacement = make_set(&mut gal, "replacement-set");
    assert_ne!(
        recorded.index(),
        replacement.index(),
        "a recorded handle slot must not be reused inside its command transaction"
    );
    gal.submit(SubmissionBatch {
        label: "recorded-set-submit".to_owned(),
        command_lists: vec![commands],
    })
    .unwrap();
    gal.finish_command_recording().unwrap();

    let reused = make_set(&mut gal, "reused-after-submit");
    assert_eq!(recorded.index(), reused.index());
    assert_eq!(recorded.generation() + 1, reused.generation());
}

#[test]
fn nested_command_recording_keeps_abandoned_resource_set_live_until_outer_finish() {
    let mut gal = gal();
    let buffer = gal
        .create_buffer(buffer("abandoned-set-buffer", vec![BufferUsage::Uniform]))
        .unwrap();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "abandoned-set-layout".to_owned(),
            bindings: vec![layout_binding(
                0,
                ResourceBindingKind::UniformBuffer,
                PipelineStageFlags::COMPUTE,
            )],
        })
        .unwrap();
    let make_set = |gal: &mut VulkanicGal, label: &str| {
        gal.create_resource_set(ResourceSetDesc {
            label: label.to_owned(),
            layout,
            bindings: vec![resource_binding(
                0,
                buffer,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            )],
        })
        .unwrap()
    };
    let recorded = make_set(&mut gal, "abandoned-recorded-set");

    // Combined GUI/world frames nest the world recording transaction inside
    // the GUI transaction. A rejected frame still closes both scopes, but the
    // inner close must not make already-recorded GUI handles reusable.
    gal.begin_command_recording().unwrap();
    gal.begin_command_recording().unwrap();
    gal.destroy(recorded).unwrap();
    gal.finish_command_recording().unwrap();
    let inner_replacement = make_set(&mut gal, "replacement-after-inner-finish");
    assert_ne!(recorded.index(), inner_replacement.index());
    gal.finish_command_recording().unwrap();

    let reused = make_set(&mut gal, "replacement-after-abandon");
    assert_eq!(recorded.index(), reused.index());
    assert_eq!(recorded.generation() + 1, reused.generation());
}

#[test]
fn partial_backend_create_failure_does_not_consume_handle_identity() {
    let mut gal = gal();
    gal.mock_backend_mut().unwrap().fail_next_create();
    let error = gal
        .create_buffer(buffer("fails", vec![BufferUsage::Vertex]))
        .unwrap_err();
    assert_eq!(error.domain, ErrorDomain::Backend);
    assert_eq!(gal.metrics().resource_creates, 0);

    let handle = gal
        .create_buffer(buffer("after-failure", vec![BufferUsage::Vertex]))
        .unwrap();
    assert_eq!(handle.index(), 0);
    assert_eq!(handle.generation(), 1);
}

#[test]
fn generation_exhaustion_is_explicit() {
    let mut gal = gal();
    let handle = gal
        .create_buffer(buffer("last-generation", vec![BufferUsage::Vertex]))
        .unwrap();
    let max_generation_handle =
        Handle::new(HandleKind::Buffer, handle.index(), MAX_GENERATION).unwrap();
    gal.force_buffer_generation_for_test(handle, MAX_GENERATION);
    assert_code(
        gal.destroy(max_generation_handle),
        super::StatusCode::GenerationExhausted,
    );
}

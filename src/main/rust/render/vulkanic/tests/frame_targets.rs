//! Acquired frame targets as pass targets, their descriptors and copies to and from them.

use super::*;

#[test]
fn acquired_frame_targets_are_normal_pass_targets_without_attachment_borrows() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("borrowed-default-framebuffer"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(101),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "minecraft-default-framebuffer".to_owned(),
            frame_id: acquired.frame.0,
            render_target: acquired.render_target,
            extent: acquired.extent,
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    assert_eq!(frame_target.kind(), Some(HandleKind::FrameTarget));
    let pass = gal
        .create_render_pass(RenderPassDesc {
            label: "gui-frame-pass".to_owned(),
            target: frame_target,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "gui-frame-pipeline-layout".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    let vertex_shader = gal
        .create_shader_module(shader("gui-frame-vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("gui-frame-fragment", ShaderStage::Fragment))
        .unwrap();
    let pipeline = gal
        .create_graphics_pipeline(GraphicsPipelineDesc {
            label: "gui-frame-pipeline".to_owned(),
            layout,
            vertex_shader,
            fragment_shader,
            topology: PrimitiveTopology::Triangles,
            cull_mode: CullMode::Back,
            front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
            provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
            raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
            blend: BlendMode::Alpha,
            depth_compare: None,
            depth_write: false,
            depth_bias: None,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
            stencil: None,
        })
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "gui-frame-list".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target: frame_target,
                    colors: vec![PassAttachment {
                        view: frame_target,
                        load_op: AttachmentLoadOp::Clear,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.25,
                            g: 0.5,
                            b: 0.75,
                            a: 1.0,
                        }),
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
            ],
        })
        .unwrap();
    let submission = gal
        .submit(SubmissionBatch {
            label: "gui-frame-submit".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    let presented = gal
        .present_frame(PresentFrameDesc {
            frame: acquired.frame,
            correlation_id: acquired.correlation_id,
            wait_for: submission.submission,
        })
        .unwrap();
    assert_eq!(presented.completed_submission, submission.submission);
}

#[test]
fn frame_target_descriptor_exposes_only_semantic_swapchain_identity() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("semantic-frame-target"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(102),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "semantic-frame-target".to_owned(),
            frame_id: acquired.frame.0,
            render_target: acquired.render_target,
            extent: acquired.extent,
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let descriptor = gal.frame_target_desc(frame_target).unwrap();
    assert_eq!(acquired.frame.0, descriptor.frame_id);
    assert_eq!(acquired.render_target, descriptor.render_target);
    assert_eq!(acquired.extent, descriptor.extent);
    assert_eq!(TextureFormat::Rgba8Unorm, descriptor.color_format);
    let (depth_texture, depth_view) = gal
        .frame_target_owned_depth_attachment(frame_target)
        .unwrap();
    assert_ne!(depth_texture, depth_view);
    assert!(gal
        .pass_target_depth_attachment(frame_target)
        .unwrap()
        .is_none());
    gal.begin_frame_target_depth_write(frame_target).unwrap();
    assert_eq!(
        Some((depth_texture, depth_view)),
        gal.pass_target_depth_attachment(frame_target).unwrap()
    );
    gal.rollback_frame_target_depth_write(frame_target);
    assert!(gal
        .pass_target_depth_attachment(frame_target)
        .unwrap()
        .is_none());
    gal.begin_frame_target_depth_write(frame_target).unwrap();
    gal.commit_frame_target_depth_write(frame_target).unwrap();
    assert_eq!(
        Some((depth_texture, depth_view)),
        gal.pass_target_depth_attachment(frame_target).unwrap()
    );
    gal.destroy(frame_target).unwrap();
    assert_code(
        gal.frame_target_desc(frame_target),
        super::StatusCode::StaleHandle,
    );
    assert_code(
        gal.frame_target_owned_depth_attachment(frame_target),
        super::StatusCode::StaleHandle,
    );
}

#[test]
fn frame_target_copy_requires_explicit_owned_destination_and_matching_extent() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("frame-copy-contract"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(103),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "frame-copy-source".to_owned(),
            frame_id: acquired.frame.0,
            render_target: acquired.render_target,
            extent: acquired.extent,
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let destination = gal
        .create_texture(texture(
            "frame-copy-destination",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferDst],
        ))
        .unwrap();

    gal.create_command_list(CommandListDesc {
        label: "valid-frame-copy".to_owned(),
        operations: vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: destination,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferDst,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::CopyFrameTargetToTexture {
                src: frame_target,
                dst: destination,
                extent: Extent3d {
                    width: 128,
                    height: 72,
                    depth: 1,
                },
            },
        ],
    })
    .unwrap();

    let color_only_destination = gal
        .create_texture(texture(
            "frame-copy-color-only",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "frame-copy-without-transfer-dst".to_owned(),
            operations: vec![CommandOp::CopyFrameTargetToTexture {
                src: frame_target,
                dst: color_only_destination,
                extent: Extent3d {
                    width: 64,
                    height: 36,
                    depth: 1,
                },
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "frame-copy-outside-source".to_owned(),
            operations: vec![CommandOp::CopyFrameTargetToTexture {
                src: frame_target,
                dst: destination,
                extent: Extent3d {
                    width: 129,
                    height: 72,
                    depth: 1,
                },
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn texture_to_frame_target_copy_requires_explicit_owned_source_and_matching_extent() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("frame-copy-reverse-contract"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(104),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    let frame_target = gal
        .create_frame_target(FrameTargetDesc {
            label: "frame-copy-reverse-destination".to_owned(),
            frame_id: acquired.frame.0,
            render_target: acquired.render_target,
            extent: acquired.extent,
            color_format: TextureFormat::Rgba8Unorm,
        })
        .unwrap();
    let source = gal
        .create_texture(texture(
            "frame-copy-reverse-source",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferSrc],
        ))
        .unwrap();
    gal.create_command_list(CommandListDesc {
        label: "valid-reverse-frame-copy".to_owned(),
        operations: vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: source,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferSrc,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::CopyTextureToFrameTarget {
                src: source,
                dst: frame_target,
                extent: Extent3d {
                    width: 128,
                    height: 72,
                    depth: 1,
                },
            },
        ],
    })
    .unwrap();
    let invalid_source = gal
        .create_texture(texture(
            "frame-copy-reverse-invalid-source",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::Sampled],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "reverse-frame-copy-without-transfer-src".to_owned(),
            operations: vec![CommandOp::CopyTextureToFrameTarget {
                src: invalid_source,
                dst: frame_target,
                extent: Extent3d {
                    width: 64,
                    height: 36,
                    depth: 1,
                },
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
}

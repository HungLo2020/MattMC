//! Malformed commands and submissions, indexed draws and indirect/host ranges.

use super::*;

#[test]
fn malformed_commands_and_usage_declarations_are_rejected() {
    let mut gal = gal();
    let (color_view, target, pass, _layout, pipeline) = simple_graphics_scene(&mut gal);
    let compute_layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "compute-layout".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    let compute_shader = gal
        .create_shader_module(shader("compute", ShaderStage::Compute))
        .unwrap();
    let compute = gal
        .create_compute_pipeline(ComputePipelineDesc {
            label: "compute".to_owned(),
            layout: compute_layout,
            shader: compute_shader,
        })
        .unwrap();
    let src = gal
        .create_buffer(buffer("src", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let not_transfer_dst = gal
        .create_buffer(buffer("not-dst", vec![BufferUsage::Vertex]))
        .unwrap();

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "draw-without-pass".to_owned(),
            operations: vec![
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::Draw {
                    vertices: 3,
                    instances: 1,
                },
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "dispatch-inside-pass".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindComputePipeline(compute),
                CommandOp::Dispatch {
                    groups_x: 1,
                    groups_y: 1,
                    groups_z: 1,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "bad-barrier".to_owned(),
            operations: vec![CommandOp::Barrier(ResourceBarrier {
                resource: src,
                subresources: None,
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::TransferSrc,
                src_queue: QueueClass::Transfer,
                dst_queue: QueueClass::Transfer,
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "self-copy".to_owned(),
            operations: vec![CommandOp::CopyBuffer {
                src,
                dst: src,
                size: 16,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-without-dst-usage".to_owned(),
            operations: vec![CommandOp::CopyBuffer {
                src,
                dst: not_transfer_dst,
                size: 16,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "index-without-index-usage".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::SetIndexBuffer {
                    buffer: not_transfer_dst,
                    offset: 0,
                    index_type: IndexType::U32,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn indexed_draws_validate_index_type_alignment_and_range() {
    let mut gal = gal();
    let (color_view, target, pass, _layout, pipeline) = simple_graphics_scene(&mut gal);
    let index = gal
        .create_buffer(BufferDesc {
            label: "typed-index".to_owned(),
            size: 12,
            memory: MemoryDomain::DeviceLocal,
            usages: vec![BufferUsage::Index],
        })
        .unwrap();
    let valid = |offset, index_type, indices| CommandListDesc {
        label: "typed-index-draw".to_owned(),
        operations: vec![
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![color_attachment(color_view)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(pipeline),
            CommandOp::SetIndexBuffer {
                buffer: index,
                offset,
                index_type,
            },
            CommandOp::DrawIndexed {
                indices,
                instances: 1,
            },
            CommandOp::EndPass,
        ],
    };
    gal.create_command_list(valid(8, IndexType::U16, 2))
        .unwrap();
    gal.create_command_list(valid(4, IndexType::U32, 2))
        .unwrap();
    assert_code(
        gal.create_command_list(valid(1, IndexType::U16, 2)),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(valid(8, IndexType::U32, 2)),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn indirect_host_and_malformed_ranges_are_validated_semantically() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    let (color_view, target, pass, _layout, pipeline) = simple_graphics_scene(&mut gal);
    let not_indirect = gal
        .create_buffer(buffer("not-indirect", vec![BufferUsage::Vertex]))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "draw-indirect-without-usage".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::DrawIndirect {
                    buffer: not_indirect,
                    offset: 0,
                    draw_count: 1,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );

    let indirect = gal
        .create_buffer(buffer("indirect", vec![BufferUsage::Indirect]))
        .unwrap();
    gal.create_command_list(CommandListDesc {
        label: "draw-indirect".to_owned(),
        operations: vec![
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![color_attachment(color_view)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(pipeline),
            CommandOp::DrawIndirect {
                buffer: indirect,
                offset: 0,
                draw_count: 1,
            },
            CommandOp::EndPass,
        ],
    })
    .unwrap();

    let index = gal
        .create_buffer(buffer("indexed-indirect-index", vec![BufferUsage::Index]))
        .unwrap();
    gal.create_command_list(CommandListDesc {
        label: "indexed-indirect-draw".to_owned(),
        operations: vec![
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![color_attachment(color_view)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(pipeline),
            CommandOp::SetIndexBuffer {
                buffer: index,
                offset: 0,
                index_type: IndexType::U16,
            },
            CommandOp::DrawIndexedIndirect {
                buffer: indirect,
                offset: 0,
                draw_count: 2,
            },
            CommandOp::EndPass,
        ],
    })
    .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "indexed-indirect-missing-index".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::DrawIndexedIndirect {
                    buffer: indirect,
                    offset: 0,
                    draw_count: 1,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "indexed-indirect-misaligned".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::SetIndexBuffer {
                    buffer: index,
                    offset: 0,
                    index_type: IndexType::U16,
                },
                CommandOp::DrawIndexedIndirect {
                    buffer: indirect,
                    offset: 2,
                    draw_count: 1,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );

    let host_wrong_memory = gal
        .create_buffer(buffer("host-wrong-memory", vec![BufferUsage::HostWrite]))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "host-write-wrong-memory".to_owned(),
            operations: vec![CommandOp::HostWriteBuffer {
                buffer: host_wrong_memory,
                offset: 0,
                data: vec![0; 16],
            }],
        }),
        super::StatusCode::InvalidArgument,
    );

    let upload = gal
        .create_buffer(BufferDesc {
            label: "upload".to_owned(),
            size: 64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite],
        })
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "host-write-inside-render-pass".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::HostWriteBuffer {
                    buffer: upload,
                    offset: 0,
                    data: vec![0; 16],
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "barrier-inside-render-pass".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::Barrier(ResourceBarrier {
                    resource: upload,
                    subresources: None,
                    before: TextureUsageState::TransferDst,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "host-write-overflow".to_owned(),
            operations: vec![CommandOp::HostWriteBuffer {
                buffer: upload,
                offset: u64::MAX,
                data: vec![0; 16],
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "host-write-outside".to_owned(),
            operations: vec![CommandOp::HostWriteBuffer {
                buffer: upload,
                offset: 32,
                data: vec![0; 64],
            }],
        }),
        super::StatusCode::InvalidArgument,
    );

    let not_present = gal
        .create_texture(texture(
            "not-present",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::Sampled],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "present-without-usage".to_owned(),
            operations: vec![CommandOp::Present {
                texture: not_present,
                subresources: TextureSubresourceRange {
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                },
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
    let present_texture = gal
        .create_texture(texture(
            "present",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::Present],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "present-bad-range".to_owned(),
            operations: vec![CommandOp::Present {
                texture: present_texture,
                subresources: TextureSubresourceRange {
                    base_mip: 0,
                    mip_count: 0,
                    base_layer: 0,
                    layer_count: 1,
                },
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn malformed_submission_is_rejected_before_backend_encoding() {
    let mut gal = gal();
    let (color_view, target, pass, _layout, _pipeline) = simple_graphics_scene(&mut gal);
    let stale_view = {
        let texture = gal
            .create_texture(texture(
                "temporary",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::ColorAttachment],
            ))
            .unwrap();
        let view = gal
            .create_texture_view(view("temporary-view", texture, TextureFormat::Rgba8Unorm))
            .unwrap();
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
        view
    };
    let list = CommandList {
        label: "bad-direct-list".to_owned(),
        operations: vec![
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![color_attachment(stale_view)],
                depth_stencil: None,
            },
            CommandOp::EndPass,
        ],
    };
    assert_code(
        gal.submit(SubmissionBatch {
            label: "bad-batch".to_owned(),
            command_lists: vec![list],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_eq!(gal.mock_backend().unwrap().encoded_batches, 0);
    assert!(gal.mock_backend().unwrap().submissions.is_empty());
    assert_ne!(stale_view, color_view);
}

//! Hazard analysis: barriers, attachment/presentation separation and subresource ranges.

use super::*;

#[test]
fn same_usage_write_barriers_are_dependencies_but_read_only_noops_are_rejected() {
    let mut gal = gal();
    let buffer = gal
        .create_buffer(BufferDesc {
            label: "same-usage-dependency".into(),
            size: 4,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::TransferSrc,
                BufferUsage::TransferDst,
                BufferUsage::HostWrite,
            ],
        })
        .unwrap();
    let barrier = |before, after| {
        CommandOp::Barrier(ResourceBarrier {
            resource: buffer,
            subresources: None,
            before,
            after,
            src_queue: QueueClass::Graphics,
            dst_queue: QueueClass::Graphics,
        })
    };
    let batch = |operations| SubmissionBatch {
        label: "same-usage-dependency".into(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "commands".into(),
            operations,
        })],
    };
    gal.submit(batch(vec![
        CommandOp::HostWriteBuffer {
            buffer,
            offset: 0,
            data: vec![1, 2, 3, 4],
        },
        barrier(
            TextureUsageState::TransferDst,
            TextureUsageState::TransferDst,
        ),
        CommandOp::HostWriteBuffer {
            buffer,
            offset: 0,
            data: vec![5, 6, 7, 8],
        },
        barrier(
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        ),
    ]))
    .unwrap();
    assert!(gal
        .submit(batch(vec![barrier(
            TextureUsageState::TransferSrc,
            TextureUsageState::TransferSrc
        )]))
        .is_err());
}

#[test]
fn unrelated_accesses_and_partial_reads_preserve_pending_destination_checks() {
    for transition_before_write in [false, true] {
        let mut gal = gal();
        let mut buffers = Vec::new();
        for label in ["guarded-source", "unrelated-destination"] {
            buffers.push(gal.create_buffer(BufferDesc {
                label: label.into(),
                size: 16,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc, BufferUsage::TransferDst],
            }).unwrap());
        }
        let [source, destination] = [buffers[0], buffers[1]];
        let barrier = |resource, before, after| CommandOp::Barrier(ResourceBarrier {
            resource, subresources: None, before, after,
            src_queue: QueueClass::Graphics, dst_queue: QueueClass::Graphics,
        });
        let mut operations = vec![
            CommandOp::HostWriteBuffer { buffer: source, offset: 0, data: vec![1; 16] },
            barrier(source, TextureUsageState::TransferDst, TextureUsageState::TransferSrc),
            // This consumes no destination on the source buffer.
            CommandOp::HostWriteBuffer { buffer: destination, offset: 0, data: vec![2; 16] },
            barrier(destination, TextureUsageState::TransferDst, TextureUsageState::TransferDst),
            // Consuming the first four bytes must retain the upper source range.
            CommandOp::CopyBufferRegion {
                src: source, src_offset: 0, dst: destination, dst_offset: 0, size: 4,
            },
        ];
        if transition_before_write {
            operations.push(barrier(source, TextureUsageState::TransferSrc, TextureUsageState::TransferDst));
        }
        // The earlier read does not overlap this write. Only the retained
        // barrier destination can reject its incompatible usage.
        operations.push(CommandOp::HostWriteBuffer { buffer: source, offset: 8, data: vec![3; 4] });
        let result = gal.submit(SubmissionBatch {
            label: "destination-range-preservation".into(),
            command_lists: vec![CommandList::from(CommandListDesc {
                label: "commands".into(), operations,
            })],
        });
        if transition_before_write {
            result.unwrap();
        } else {
            let error = result.unwrap_err();
            assert_eq!(error.code, StatusCode::InvalidArgument);
            assert!(error.message.contains("barrier after TransferSrc"), "{error:?}");
        }
    }
}

#[test]
fn attachment_and_presentation_hazards_require_semantic_separation() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    let texture = gal
        .create_texture(texture(
            "attachment",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment, TextureUsage::Present],
        ))
        .unwrap();
    let first_view = gal
        .create_texture_view(view("first-view", texture, TextureFormat::Rgba8Unorm))
        .unwrap();
    let second_view = gal
        .create_texture_view(view("second-view", texture, TextureFormat::Rgba8Unorm))
        .unwrap();
    let overlapping_target = gal
        .create_render_target(RenderTargetDesc {
            label: "overlapping-target".to_owned(),
            color_views: vec![first_view, second_view],
            depth_stencil_view: None,
            extent: Extent3d {
                width: 128,
                height: 128,
                depth: 1,
            },
        })
        .unwrap();
    let overlapping_pass = gal
        .create_render_pass(RenderPassDesc {
            label: "overlapping-pass".to_owned(),
            target: overlapping_target,
            color_formats: vec![TextureFormat::Rgba8Unorm, TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "attachment-layout".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    let vertex_shader = gal
        .create_shader_module(shader("attachment-vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("attachment-fragment", ShaderStage::Fragment))
        .unwrap();
    let overlapping_pipeline = gal
        .create_graphics_pipeline(GraphicsPipelineDesc {
            label: "overlapping-pipeline".to_owned(),
            layout,
            vertex_shader,
            fragment_shader,
            topology: PrimitiveTopology::Triangles,
            cull_mode: CullMode::Back,
            front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
            provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
            raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
            blend: BlendMode::Disabled,
            depth_compare: None,
            depth_write: false,
            depth_bias: None,
            color_formats: vec![TextureFormat::Rgba8Unorm, TextureFormat::Rgba8Unorm],
            depth_format: None,
            stencil: None,
        })
        .unwrap();
    let overlapping_list = gal
        .create_command_list(CommandListDesc {
            label: "overlapping-attachments".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass: overlapping_pass,
                    target: overlapping_target,
                    colors: vec![color_attachment(first_view), color_attachment(second_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(overlapping_pipeline),
                CommandOp::EndPass,
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "attachment-overlap".to_owned(),
            command_lists: vec![overlapping_list],
        }),
        super::StatusCode::InvalidArgument,
    );

    let target = gal
        .create_render_target(RenderTargetDesc {
            label: "single-target".to_owned(),
            color_views: vec![first_view],
            depth_stencil_view: None,
            extent: Extent3d {
                width: 128,
                height: 128,
                depth: 1,
            },
        })
        .unwrap();
    let pass = gal
        .create_render_pass(RenderPassDesc {
            label: "single-pass".to_owned(),
            target,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let vertex_shader = gal
        .create_shader_module(shader("present-vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("present-fragment", ShaderStage::Fragment))
        .unwrap();
    let pipeline = gal
        .create_graphics_pipeline(GraphicsPipelineDesc {
            label: "present-pipeline".to_owned(),
            layout,
            vertex_shader,
            fragment_shader,
            topology: PrimitiveTopology::Triangles,
            cull_mode: CullMode::Back,
            front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
            provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
            raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
            blend: BlendMode::Disabled,
            depth_compare: None,
            depth_write: false,
            depth_bias: None,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
            stencil: None,
        })
        .unwrap();
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    let repeated_attachment_writes = gal
        .create_command_list(CommandListDesc {
            label: "repeated-attachment-writes".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(first_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![PassAttachment {
                        view: first_view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
            ],
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "ordered-repeated-attachment-writes".to_owned(),
        command_lists: vec![repeated_attachment_writes],
    })
    .unwrap();

    let load_after_dont_care_store = gal
        .create_command_list(CommandListDesc {
            label: "load-after-dont-care-store".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![PassAttachment {
                        view: first_view,
                        load_op: AttachmentLoadOp::Clear,
                        store_op: AttachmentStoreOp::DontCare,
                        clear_color: None,
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![PassAttachment {
                        view: first_view,
                        load_op: AttachmentLoadOp::Load,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: None,
                    }],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "attachment-load-after-dont-care-store".to_owned(),
            command_lists: vec![load_after_dont_care_store],
        }),
        super::StatusCode::InvalidArgument,
    );

    let present_without_barrier = gal
        .create_command_list(CommandListDesc {
            label: "present-without-barrier".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(first_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
                CommandOp::Present {
                    texture,
                    subresources: full_range,
                },
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "present-hazard".to_owned(),
            command_lists: vec![present_without_barrier],
        }),
        super::StatusCode::InvalidArgument,
    );

    let present_with_barrier = gal
        .create_command_list(CommandListDesc {
            label: "present-with-barrier".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(first_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::ColorAttachment,
                    after: TextureUsageState::Present,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Present,
                }),
                CommandOp::Present {
                    texture,
                    subresources: full_range,
                },
            ],
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "present-separated".to_owned(),
        command_lists: vec![present_with_barrier],
    })
    .unwrap();

    let present_with_view_barrier = gal
        .create_command_list(CommandListDesc {
            label: "present-with-view-barrier".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: first_view,
                    subresources: Some(full_range),
                    before: TextureUsageState::ColorAttachment,
                    after: TextureUsageState::Present,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Present,
                }),
                CommandOp::Present {
                    texture,
                    subresources: full_range,
                },
            ],
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "present-view-separated".to_owned(),
        command_lists: vec![present_with_view_barrier],
    })
    .unwrap();
}

#[test]
fn storage_and_subresource_hazards_are_conservative() {
    let mut gal = gal();
    let storage = gal
        .create_buffer(BufferDesc {
            label: "storage".to_owned(),
            size: 128,
            memory: MemoryDomain::Readback,
            usages: vec![
                BufferUsage::Storage,
                BufferUsage::HostRead,
                BufferUsage::TransferDst,
            ],
        })
        .unwrap();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "storage-layout".to_owned(),
            bindings: vec![layout_binding(
                0,
                ResourceBindingKind::StorageBuffer,
                PipelineStageFlags::COMPUTE,
            )],
        })
        .unwrap();
    let set = gal
        .create_resource_set(ResourceSetDesc {
            label: "storage-set".to_owned(),
            layout,
            bindings: vec![resource_binding(
                0,
                storage,
                ResourceBindingKind::StorageBuffer,
                AccessFlags::WRITE,
            )],
        })
        .unwrap();
    let pipeline_layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "storage-pipeline-layout".to_owned(),
            resource_layouts: vec![layout],
        })
        .unwrap();
    let shader = gal
        .create_shader_module(shader("storage-compute", ShaderStage::Compute))
        .unwrap();
    let pipeline = gal
        .create_compute_pipeline(ComputePipelineDesc {
            label: "storage-pipeline".to_owned(),
            layout: pipeline_layout,
            shader,
        })
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "storage-write-then-host-read".to_owned(),
            operations: vec![
                CommandOp::BindComputePipeline(pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Dispatch {
                    groups_x: 1,
                    groups_y: 1,
                    groups_z: 1,
                },
                CommandOp::HostReadBuffer {
                    buffer: storage,
                    offset: 0,
                    size: 16,
                },
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "storage-hazard".to_owned(),
            command_lists: vec![list],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn texture_subresource_hazards_respect_non_overlapping_ranges() {
    let mut gal = gal();
    let texture = gal
        .create_texture(TextureDesc {
            label: "storage-texture".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 64,
                height: 64,
                depth: 1,
            },
            mip_levels: 2,
            array_layers: 1,
            usages: vec![TextureUsage::Storage],
        })
        .unwrap();
    let mip0 = gal
        .create_texture_view(TextureViewDesc {
            label: "mip0".to_owned(),
            texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let mip1 = gal
        .create_texture_view(TextureViewDesc {
            label: "mip1".to_owned(),
            texture,
            format: TextureFormat::Rgba8Unorm,
            base_mip: 1,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        })
        .unwrap();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "texture-storage-layout".to_owned(),
            bindings: vec![
                layout_binding(
                    0,
                    ResourceBindingKind::StorageTexture,
                    PipelineStageFlags::COMPUTE,
                ),
                layout_binding(
                    1,
                    ResourceBindingKind::StorageTexture,
                    PipelineStageFlags::COMPUTE,
                ),
            ],
        })
        .unwrap();
    let pipeline_layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "texture-storage-pipeline-layout".to_owned(),
            resource_layouts: vec![layout],
        })
        .unwrap();
    let shader = gal
        .create_shader_module(shader("texture-compute", ShaderStage::Compute))
        .unwrap();
    let pipeline = gal
        .create_compute_pipeline(ComputePipelineDesc {
            label: "texture-compute".to_owned(),
            layout: pipeline_layout,
            shader,
        })
        .unwrap();

    let disjoint = gal
        .create_resource_set(ResourceSetDesc {
            label: "disjoint-textures".to_owned(),
            layout,
            bindings: vec![
                resource_binding(
                    0,
                    mip0,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
                resource_binding(
                    1,
                    mip1,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
            ],
        })
        .unwrap();
    let disjoint_list = gal
        .create_command_list(CommandListDesc {
            label: "disjoint".to_owned(),
            operations: vec![
                CommandOp::BindComputePipeline(pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set: disjoint,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Dispatch {
                    groups_x: 1,
                    groups_y: 1,
                    groups_z: 1,
                },
            ],
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "disjoint-subresources".to_owned(),
        command_lists: vec![disjoint_list],
    })
    .unwrap();

    let overlapping = gal
        .create_resource_set(ResourceSetDesc {
            label: "overlapping-textures".to_owned(),
            layout,
            bindings: vec![
                resource_binding(
                    0,
                    mip0,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
                resource_binding(
                    1,
                    mip0,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
            ],
        })
        .unwrap();
    let overlapping_list = gal
        .create_command_list(CommandListDesc {
            label: "overlapping".to_owned(),
            operations: vec![
                CommandOp::BindComputePipeline(pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set: overlapping,
                    dynamic_offsets: Vec::new(),
                },
                CommandOp::Dispatch {
                    groups_x: 1,
                    groups_y: 1,
                    groups_z: 1,
                },
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "overlapping-subresources".to_owned(),
            command_lists: vec![overlapping_list],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn hazard_tracking_ignores_unrelated_resources() {
    let mut gal = gal();
    let mut operations = Vec::new();
    for index in 0..64 {
        let handle = gal
            .create_buffer(BufferDesc {
                label: format!("unrelated-write-{index}"),
                size: 16,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::HostWrite],
            })
            .unwrap();
        operations.push(CommandOp::HostWriteBuffer {
            buffer: handle,
            offset: 0,
            data: vec![index as u8; 4],
        });
    }
    let list = gal
        .create_command_list(CommandListDesc {
            label: "unrelated-writes".to_owned(),
            operations,
        })
        .unwrap();
    let mut profile = SubmitProfile::default();
    gal.submit_profiled(
        SubmissionBatch {
            label: "unrelated-write-submit".to_owned(),
            command_lists: vec![list],
        },
        &mut profile,
    )
    .unwrap();

    assert_eq!(profile.gal_hazard_write_events, 64);
    assert_eq!(profile.gal_hazard_candidates_examined, 0);
    assert_eq!(profile.gal_hazard_active_write_entries, 64);
}

#[test]
fn hazard_tracking_still_checks_same_resource_ranges() {
    let mut gal = gal();
    let buffer = gal
        .create_buffer(BufferDesc {
            label: "same-buffer".to_owned(),
            size: 64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite],
        })
        .unwrap();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "disjoint-same-buffer-writes".to_owned(),
            operations: vec![
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset: 0,
                    data: vec![1; 4],
                },
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset: 8,
                    data: vec![2; 4],
                },
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset: 16,
                    data: vec![3; 4],
                },
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset: 32,
                    data: vec![4; 4],
                },
            ],
        })
        .unwrap();
    let mut profile = SubmitProfile::default();
    gal.submit_profiled(
        SubmissionBatch {
            label: "disjoint-same-buffer-submit".to_owned(),
            command_lists: vec![list],
        },
        &mut profile,
    )
    .unwrap();
    assert_eq!(profile.gal_hazard_write_events, 4);
    assert_eq!(profile.gal_hazard_read_events, 0);
    assert_eq!(profile.gal_hazard_candidates_examined, 6);

    let overlapping = gal
        .create_command_list(CommandListDesc {
            label: "overlapping-same-buffer-write-read".to_owned(),
            operations: vec![
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset: 0,
                    data: vec![1; 8],
                },
                CommandOp::HostWriteBuffer {
                    buffer,
                    offset: 4,
                    data: vec![2; 4],
                },
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "overlapping-same-buffer-submit".to_owned(),
            command_lists: vec![overlapping],
        }),
        super::StatusCode::InvalidArgument,
    );

}

#[test]
fn storage_binding_hazards_use_explicit_ranges_and_effective_dynamic_offsets() {
    for overrides in [false, true] {
        for second_access in [AccessFlags::READ, AccessFlags::WRITE] {
            for (second_offset, accepted) in [(256u64, true), (128, false), (0, false)] {
                let mut gal = gal();
                let buffer = gal
                    .create_buffer(BufferDesc {
                        label: "ranged-storage".into(),
                        size: 1024,
                        memory: MemoryDomain::DeviceLocal,
                        usages: vec![BufferUsage::Storage],
                    })
                    .unwrap();
                let layout = gal
                    .create_resource_layout(ResourceLayoutDesc {
                        label: "ranged-storage-layout".into(),
                        bindings: vec![ResourceBindingDesc {
                            binding: 0,
                            kind: ResourceBindingKind::StorageBuffer,
                            stages: PipelineStageFlags::COMPUTE,
                            array_count: 1,
                            optional: false,
                            dynamic_offset_count: 1,
                        }],
                    })
                    .unwrap();
                let pipeline_layout = gal
                    .create_pipeline_layout(PipelineLayoutDesc {
                        label: "ranged-storage-pipeline-layout".into(),
                        resource_layouts: vec![layout],
                    })
                    .unwrap();
                let shader = gal
                    .create_shader_module(shader("ranged-storage-shader", ShaderStage::Compute))
                    .unwrap();
                let pipeline = gal
                    .create_compute_pipeline(ComputePipelineDesc {
                        label: "ranged-storage-pipeline".into(),
                        layout: pipeline_layout,
                        shader,
                    })
                    .unwrap();
                let mut operations = vec![CommandOp::BindComputePipeline(pipeline)];
                for (offset, access) in [(0, AccessFlags::WRITE), (second_offset, second_access)] {
                    let set = gal
                        .create_resource_set(ResourceSetDesc {
                            label: "ranged-storage-set".into(),
                            layout,
                            bindings: vec![ResourceBinding {
                                binding: 0,
                                array_index: 0,
                                resource: buffer,
                                kind: ResourceBindingKind::StorageBuffer,
                                access,
                                dynamic_offsets: vec![if overrides { 0 } else { offset }],
                                buffer_range: Some(256),
                            }],
                        })
                        .unwrap();
                    operations.push(CommandOp::BindResourceSet {
                        pipeline_layout,
                        set_index: 0,
                        set,
                        dynamic_offsets: if overrides { vec![offset] } else { vec![] },
                    });
                    operations.push(CommandOp::Dispatch {
                        groups_x: 1,
                        groups_y: 1,
                        groups_z: 1,
                    });
                }
                let commands = gal
                    .create_command_list(CommandListDesc {
                        label: "ranged-storage-commands".into(),
                        operations,
                    })
                    .unwrap();
                let result = gal.submit(SubmissionBatch {
                    label: "ranged-storage-submit".into(),
                    command_lists: vec![commands],
                });
                assert_eq!(result.is_ok(), accepted, "offset={second_offset} overrides={overrides} access={second_access:?}: {result:?}");
                if let Err(error) = result {
                    assert_eq!(error.domain, ErrorDomain::Submission);
                }
            }
        }
    }
}

#[test]
fn dispatch_accesses_are_tracked_without_rebinding_and_barriers_are_typed() {
    let mut gal = gal();
    let buffer = gal.create_buffer(buffer("storage", vec![BufferUsage::Storage])).unwrap();
    let resource_layout = gal.create_resource_layout(ResourceLayoutDesc { label: "storage".into(),
        bindings: vec![layout_binding(0, ResourceBindingKind::StorageBuffer, PipelineStageFlags::COMPUTE)] }).unwrap();
    let layout = gal.create_pipeline_layout(PipelineLayoutDesc { label: "compute".into(), resource_layouts: vec![resource_layout] }).unwrap();
    let shader = gal.create_shader_module(shader("compute", ShaderStage::Compute)).unwrap();
    let pipeline = gal.create_compute_pipeline(ComputePipelineDesc { label: "compute".into(), layout, shader }).unwrap();
    let set = gal.create_resource_set(ResourceSetDesc { label: "set".into(), layout: resource_layout,
        bindings: vec![resource_binding(0, buffer, ResourceBindingKind::StorageBuffer, AccessFlags::WRITE)] }).unwrap();
    let dispatch = CommandOp::Dispatch { groups_x: 1, groups_y: 1, groups_z: 1 };
    let barrier = |before, after| CommandOp::Barrier(ResourceBarrier { resource: buffer, subresources: None,
        before, after, src_queue: QueueClass::Compute, dst_queue: QueueClass::Compute });
    let prefix = vec![CommandOp::BindComputePipeline(pipeline), CommandOp::BindResourceSet {
        pipeline_layout: layout, set_index: 0, set, dynamic_offsets: vec![] }];
    let mut submit = |middle: Vec<CommandOp>| {
        let mut operations = prefix.clone();
        operations.extend(middle);
        gal.submit(SubmissionBatch { label: "hazard-regression".into(), command_lists: vec![CommandList::from(CommandListDesc { label: "compute".into(), operations })] })
    };
    // A bind is state, not a write.
    submit(vec![]).unwrap();
    assert!(submit(vec![dispatch.clone(), dispatch.clone()]).is_err());
    assert!(submit(vec![dispatch.clone(), barrier(TextureUsageState::TransferDst, TextureUsageState::ShaderWrite), dispatch.clone()]).is_err());
    assert!(submit(vec![dispatch.clone(), barrier(TextureUsageState::ShaderWrite, TextureUsageState::ShaderRead), dispatch.clone()]).is_err());
    submit(vec![dispatch.clone(), barrier(TextureUsageState::ShaderWrite, TextureUsageState::ShaderWrite), dispatch]).unwrap();
}

#[test]
fn consecutive_draws_reuse_read_bindings_but_still_reject_repeated_storage_writes() {
    for (access, expect_conflict) in [(AccessFlags::READ, false), (AccessFlags::WRITE, true)] {
        let mut gal = gal();
        let (color_view, target, pass, _, _) = simple_graphics_scene(&mut gal);
        let storage = gal
            .create_buffer(buffer("draw-storage", vec![BufferUsage::Storage]))
            .unwrap();
        let resource_layout = gal
            .create_resource_layout(ResourceLayoutDesc {
                label: "draw-storage-layout".to_owned(),
                bindings: vec![layout_binding(
                    0,
                    ResourceBindingKind::StorageBuffer,
                    PipelineStageFlags::DRAW,
                )],
            })
            .unwrap();
        let set = gal
            .create_resource_set(ResourceSetDesc {
                label: "draw-storage-set".to_owned(),
                layout: resource_layout,
                bindings: vec![resource_binding(0, storage, ResourceBindingKind::StorageBuffer, access)],
            })
            .unwrap();
        let pipeline_layout = gal
            .create_pipeline_layout(PipelineLayoutDesc {
                label: "draw-storage-pipeline-layout".to_owned(),
                resource_layouts: vec![resource_layout],
            })
            .unwrap();
        let vertex_shader = gal.create_shader_module(shader("v", ShaderStage::Vertex)).unwrap();
        let fragment_shader = gal.create_shader_module(shader("f", ShaderStage::Fragment)).unwrap();
        let pipeline = gal
            .create_graphics_pipeline(GraphicsPipelineDesc {
                label: "draw-storage-pipeline".to_owned(),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::Back,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend: BlendMode::Disabled,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: None,
                stencil: None,
            })
            .unwrap();
        let draw = CommandOp::Draw { vertices: 3, instances: 1 };
        let list = gal
            .create_command_list(CommandListDesc {
                label: "consecutive-draws".to_owned(),
                operations: vec![
                    CommandOp::BeginPass {
                        pass,
                        target,
                        colors: vec![color_attachment(color_view)],
                        depth_stencil: None,
                    },
                    CommandOp::BindGraphicsPipeline(pipeline),
                    CommandOp::BindResourceSet {
                        pipeline_layout,
                        set_index: 0,
                        set,
                        dynamic_offsets: Vec::new(),
                    },
                    draw.clone(),
                    draw.clone(),
                    draw,
                    CommandOp::EndPass,
                ],
            })
            .unwrap();
        let result = gal.submit(SubmissionBatch {
            label: "consecutive-draws".to_owned(),
            command_lists: vec![list],
        });
        if expect_conflict {
            assert_code(result, super::StatusCode::InvalidArgument);
        } else {
            result.unwrap();
        }
    }
}

#[test]
fn rebinding_one_set_still_checks_unchanged_reads_against_new_writes() {
    for (second_access, second_targets_first_buffer, expect_conflict) in [
        (AccessFlags::READ, false, false),
        (AccessFlags::WRITE, true, true),
    ] {
        let mut gal = gal();
        let (color_view, target, pass, _, _) = simple_graphics_scene(&mut gal);
        let shared = gal.create_buffer(buffer("shared", vec![BufferUsage::Storage])).unwrap();
        let other = gal.create_buffer(buffer("other", vec![BufferUsage::Storage])).unwrap();
        let layout = |gal: &mut VulkanicGal, label: &str| {
            gal.create_resource_layout(ResourceLayoutDesc {
                label: label.to_owned(),
                bindings: vec![layout_binding(0, ResourceBindingKind::StorageBuffer, PipelineStageFlags::DRAW)],
            })
            .unwrap()
        };
        let first_layout = layout(&mut gal, "first-layout");
        let second_layout = layout(&mut gal, "second-layout");
        let set = |gal: &mut VulkanicGal, layout, resource, access| {
            gal.create_resource_set(ResourceSetDesc {
                label: "set".to_owned(),
                layout,
                bindings: vec![resource_binding(0, resource, ResourceBindingKind::StorageBuffer, access)],
            })
            .unwrap()
        };
        let first = set(&mut gal, first_layout, shared, AccessFlags::READ);
        let second_initial = set(&mut gal, second_layout, other, AccessFlags::READ);
        let second_rebound = set(
            &mut gal,
            second_layout,
            if second_targets_first_buffer { shared } else { other },
            second_access,
        );
        let pipeline_layout = gal
            .create_pipeline_layout(PipelineLayoutDesc {
                label: "two-set-layout".to_owned(),
                resource_layouts: vec![first_layout, second_layout],
            })
            .unwrap();
        let vertex_shader = gal.create_shader_module(shader("v", ShaderStage::Vertex)).unwrap();
        let fragment_shader = gal.create_shader_module(shader("f", ShaderStage::Fragment)).unwrap();
        let pipeline = gal
            .create_graphics_pipeline(GraphicsPipelineDesc {
                label: "two-set-pipeline".to_owned(),
                layout: pipeline_layout,
                vertex_shader,
                fragment_shader,
                topology: PrimitiveTopology::Triangles,
                cull_mode: CullMode::Back,
                front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
                provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
                raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
                blend: BlendMode::Disabled,
                depth_compare: None,
                depth_write: false,
                depth_bias: None,
                color_formats: vec![TextureFormat::Rgba8Unorm],
                depth_format: None,
                stencil: None,
            })
            .unwrap();
        let bind = |set_index, set| CommandOp::BindResourceSet {
            pipeline_layout,
            set_index,
            set,
            dynamic_offsets: Vec::new(),
        };
        let draw = CommandOp::Draw { vertices: 3, instances: 1 };
        let list = gal
            .create_command_list(CommandListDesc {
                label: "rebind-one-set".to_owned(),
                operations: vec![
                    CommandOp::BeginPass {
                        pass,
                        target,
                        colors: vec![color_attachment(color_view)],
                        depth_stencil: None,
                    },
                    CommandOp::BindGraphicsPipeline(pipeline),
                    bind(0, first),
                    bind(1, second_initial),
                    draw.clone(),
                    bind(1, second_rebound),
                    draw,
                    CommandOp::EndPass,
                ],
            })
            .unwrap();
        let result = gal.submit(SubmissionBatch {
            label: "rebind-one-set".to_owned(),
            command_lists: vec![list],
        });
        if expect_conflict {
            assert_code(result, super::StatusCode::InvalidArgument);
        } else {
            result.unwrap();
        }
    }
}

use std::fs;
use std::mem::{align_of, size_of};
use std::path::Path;
use std::ptr::NonNull;

use super::backends::mock::MockBackend;
use super::commands::*;
use super::error::{ErrorDomain, GalError};
use super::ffi::*;
use super::gal::VulkanicGal;
use super::handles::{Handle, HandleKind, MAX_GENERATION};
use super::metrics::Metrics;
use super::resources::*;

fn gal() -> VulkanicGal {
    VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false)
}

fn assert_code<T>(result: Result<T, GalError>, code: super::StatusCode) {
    let error = match result {
        Ok(_) => panic!("operation unexpectedly succeeded"),
        Err(error) => error,
    };
    assert_eq!(error.code, code, "{error}");
}

fn buffer(label: &str, usages: Vec<BufferUsage>) -> BufferDesc {
    BufferDesc {
        label: label.to_owned(),
        size: 4096,
        memory: MemoryDomain::DeviceLocal,
        usages,
    }
}

fn texture(label: &str, format: TextureFormat, usages: Vec<TextureUsage>) -> TextureDesc {
    TextureDesc {
        label: label.to_owned(),
        dimension: TextureDimension::D2,
        format,
        extent: Extent3d {
            width: 128,
            height: 128,
            depth: 1,
        },
        mip_levels: 1,
        array_layers: 1,
        usages,
    }
}

fn view(label: &str, texture: Handle, format: TextureFormat) -> TextureViewDesc {
    TextureViewDesc {
        label: label.to_owned(),
        texture,
        format,
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    }
}

fn sampler(label: &str) -> SamplerDesc {
    SamplerDesc {
        label: label.to_owned(),
        min_filter: SamplerFilter::Linear,
        mag_filter: SamplerFilter::Linear,
        mip_filter: SamplerFilter::Nearest,
        address_u: SamplerAddressMode::ClampToEdge,
        address_v: SamplerAddressMode::ClampToEdge,
        address_w: SamplerAddressMode::ClampToEdge,
    }
}

fn layout_binding(
    binding: u32,
    kind: ResourceBindingKind,
    stages: PipelineStageFlags,
) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding,
        kind,
        stages,
        array_count: 1,
        optional: false,
        dynamic_offset_count: 0,
    }
}

fn resource_binding(
    binding: u32,
    resource: Handle,
    kind: ResourceBindingKind,
    access: AccessFlags,
) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind,
        access,
        dynamic_offsets: vec![],
    }
}

fn shader(label: &str, stage: ShaderStage) -> ShaderModuleDesc {
    ShaderModuleDesc {
        label: label.to_owned(),
        stage,
        code_format: ShaderCodeFormat::Spirv,
        code: vec![3, 2, 35, 7],
        entry_point: "main".to_owned(),
    }
}

fn color_attachment(view: Handle) -> PassAttachment {
    PassAttachment {
        view,
        load_op: AttachmentLoadOp::Clear,
        store_op: AttachmentStoreOp::Store,
        clear_color: Some(ClearColor {
            r: 0.0,
            g: 0.0,
            b: 0.0,
            a: 1.0,
        }),
    }
}

fn simple_graphics_scene(gal: &mut VulkanicGal) -> (Handle, Handle, Handle, Handle, Handle) {
    let color_texture = gal
        .create_texture(texture(
            "color",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment, TextureUsage::Sampled],
        ))
        .unwrap();
    let color_view = gal
        .create_texture_view(view("color-view", color_texture, TextureFormat::Rgba8Unorm))
        .unwrap();
    let target = gal
        .create_render_target(RenderTargetDesc {
            label: "target".to_owned(),
            color_views: vec![color_view],
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
            label: "main-pass".to_owned(),
            target,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "empty-pipeline-layout".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    let vertex_shader = gal
        .create_shader_module(shader("vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("fragment", ShaderStage::Fragment))
        .unwrap();
    let pipeline = gal
        .create_graphics_pipeline(GraphicsPipelineDesc {
            label: "pipeline".to_owned(),
            layout,
            vertex_shader,
            fragment_shader,
            topology: PrimitiveTopology::Triangles,
            cull_mode: CullMode::Back,
            blend: BlendMode::Disabled,
            depth_compare: None,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    (color_view, target, pass, layout, pipeline)
}

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
fn resource_sets_validate_layout_binding_kind_resource_type_and_access() {
    let mut gal = gal();
    let buffer = gal
        .create_buffer(buffer("ubo", vec![BufferUsage::Uniform]))
        .unwrap();
    let sampler = gal.create_sampler(sampler("sampler")).unwrap();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "layout".to_owned(),
            bindings: vec![
                layout_binding(
                    0,
                    ResourceBindingKind::UniformBuffer,
                    PipelineStageFlags::DRAW,
                ),
                layout_binding(1, ResourceBindingKind::Sampler, PipelineStageFlags::DRAW),
            ],
        })
        .unwrap();

    gal.create_resource_set(ResourceSetDesc {
        label: "set".to_owned(),
        layout,
        bindings: vec![
            resource_binding(
                0,
                buffer,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            ),
            resource_binding(1, sampler, ResourceBindingKind::Sampler, AccessFlags::READ),
        ],
    })
    .unwrap();

    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "wrong-kind".to_owned(),
            layout,
            bindings: vec![resource_binding(
                0,
                buffer,
                ResourceBindingKind::Sampler,
                AccessFlags::READ,
            )],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "wrong-type".to_owned(),
            layout,
            bindings: vec![resource_binding(
                1,
                buffer,
                ResourceBindingKind::Sampler,
                AccessFlags::READ,
            )],
        }),
        super::StatusCode::WrongHandleType,
    );
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "no-access".to_owned(),
            layout,
            bindings: vec![resource_binding(
                0,
                buffer,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::NONE,
            )],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_resource_layout(ResourceLayoutDesc {
            label: "no-stage-layout".to_owned(),
            bindings: vec![layout_binding(
                0,
                ResourceBindingKind::UniformBuffer,
                PipelineStageFlags::NONE,
            )],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "duplicate-set-binding".to_owned(),
            layout,
            bindings: vec![
                resource_binding(
                    0,
                    buffer,
                    ResourceBindingKind::UniformBuffer,
                    AccessFlags::READ,
                ),
                resource_binding(
                    0,
                    buffer,
                    ResourceBindingKind::UniformBuffer,
                    AccessFlags::READ,
                ),
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn resource_sets_require_complete_arrays_and_explicit_optionality() {
    let mut gal = gal();
    let first = gal
        .create_buffer(buffer("first-ubo", vec![BufferUsage::Uniform]))
        .unwrap();
    let second = gal
        .create_buffer(buffer("second-ubo", vec![BufferUsage::Uniform]))
        .unwrap();
    let array_layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "array-layout".to_owned(),
            bindings: vec![ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::UniformBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 2,
                optional: false,
                dynamic_offset_count: 0,
            }],
        })
        .unwrap();

    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "incomplete-array".to_owned(),
            layout: array_layout,
            bindings: vec![resource_binding(
                0,
                first,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            )],
        }),
        super::StatusCode::InvalidArgument,
    );

    gal.create_resource_set(ResourceSetDesc {
        label: "complete-array".to_owned(),
        layout: array_layout,
        bindings: vec![
            resource_binding(
                0,
                first,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            ),
            ResourceBinding {
                binding: 0,
                array_index: 1,
                resource: second,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![],
            },
        ],
    })
    .unwrap();

    let optional_layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "optional-layout".to_owned(),
            bindings: vec![ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::UniformBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 2,
                optional: true,
                dynamic_offset_count: 0,
            }],
        })
        .unwrap();
    gal.create_resource_set(ResourceSetDesc {
        label: "explicitly-partial".to_owned(),
        layout: optional_layout,
        bindings: vec![],
    })
    .unwrap();

    let dynamic_layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "dynamic-layout".to_owned(),
            bindings: vec![ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::UniformBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 1,
            }],
        })
        .unwrap();
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "missing-dynamic-offset".to_owned(),
            layout: dynamic_layout,
            bindings: vec![resource_binding(
                0,
                first,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            )],
        }),
        super::StatusCode::InvalidArgument,
    );
    gal.create_resource_set(ResourceSetDesc {
        label: "dynamic-offset".to_owned(),
        layout: dynamic_layout,
        bindings: vec![ResourceBinding {
            binding: 0,
            array_index: 0,
            resource: first,
            kind: ResourceBindingKind::UniformBuffer,
            access: AccessFlags::READ,
            dynamic_offsets: vec![64],
        }],
    })
    .unwrap();
}

#[test]
fn pipeline_and_pass_compatibility_is_validated() {
    let mut gal = gal();
    let (color_view, target, pass, layout, pipeline) = simple_graphics_scene(&mut gal);
    let good_ops = vec![
        CommandOp::BeginPass {
            pass,
            target,
            colors: vec![color_attachment(color_view)],
            depth_stencil: None,
        },
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::Draw {
            vertices: 3,
            instances: 1,
        },
        CommandOp::EndPass,
    ];
    gal.create_command_list(CommandListDesc {
        label: "good".to_owned(),
        operations: good_ops,
    })
    .unwrap();

    let vertex_shader = gal
        .create_shader_module(shader("second-vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("second-fragment", ShaderStage::Fragment))
        .unwrap();
    let bad_pipeline = gal
        .create_graphics_pipeline(GraphicsPipelineDesc {
            label: "bad-pipeline".to_owned(),
            layout,
            vertex_shader,
            fragment_shader,
            topology: PrimitiveTopology::Triangles,
            cull_mode: CullMode::Back,
            blend: BlendMode::Disabled,
            depth_compare: None,
            color_formats: vec![TextureFormat::Bgra8Unorm],
            depth_format: None,
        })
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "bad".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(bad_pipeline),
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn render_target_pass_and_attachment_views_match_their_textures() {
    let mut gal = gal();
    let color_texture = gal
        .create_texture(texture(
            "color",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment],
        ))
        .unwrap();
    assert_code(
        gal.create_texture_view(view(
            "reinterpret",
            color_texture,
            TextureFormat::Bgra8Unorm,
        )),
        super::StatusCode::InvalidArgument,
    );
    let color_view = gal
        .create_texture_view(view("color-view", color_texture, TextureFormat::Rgba8Unorm))
        .unwrap();
    assert_code(
        gal.create_render_target(RenderTargetDesc {
            label: "wrong-extent".to_owned(),
            color_views: vec![color_view],
            depth_stencil_view: None,
            extent: Extent3d {
                width: 64,
                height: 128,
                depth: 1,
            },
        }),
        super::StatusCode::InvalidArgument,
    );
    let target = gal
        .create_render_target(RenderTargetDesc {
            label: "target".to_owned(),
            color_views: vec![color_view],
            depth_stencil_view: None,
            extent: Extent3d {
                width: 128,
                height: 128,
                depth: 1,
            },
        })
        .unwrap();
    assert_code(
        gal.create_render_pass(RenderPassDesc {
            label: "wrong-format-pass".to_owned(),
            target,
            color_formats: vec![TextureFormat::Bgra8Unorm],
            depth_format: None,
        }),
        super::StatusCode::InvalidArgument,
    );
    let pass = gal
        .create_render_pass(RenderPassDesc {
            label: "pass".to_owned(),
            target,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let other_texture = gal
        .create_texture(texture(
            "other",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment],
        ))
        .unwrap();
    let other_view = gal
        .create_texture_view(view("other-view", other_texture, TextureFormat::Rgba8Unorm))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "wrong-attachment".to_owned(),
            operations: vec![CommandOp::BeginPass {
                pass,
                target,
                colors: vec![color_attachment(other_view)],
                depth_stencil: None,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn attachment_and_presentation_hazards_require_semantic_separation() {
    let mut gal = gal();
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
            blend: BlendMode::Disabled,
            depth_compare: None,
            color_formats: vec![TextureFormat::Rgba8Unorm, TextureFormat::Rgba8Unorm],
            depth_format: None,
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
            blend: BlendMode::Disabled,
            depth_compare: None,
            color_formats: vec![TextureFormat::Rgba8Unorm],
            depth_format: None,
        })
        .unwrap();
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
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
                    stages: PipelineStageFlags(
                        PipelineStageFlags::DRAW.0 | PipelineStageFlags::PRESENT.0,
                    ),
                    access: AccessFlags(AccessFlags::COLOR_ATTACHMENT.0 | AccessFlags::READ.0),
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
}

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
                after: TextureUsageState::TransferDst,
                stages: PipelineStageFlags::NONE,
                access: AccessFlags::TRANSFER,
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
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn buffer_texture_copy_commands_validate_usage_ranges_and_hazards() {
    let mut gal = gal();
    let upload = gal
        .create_buffer(buffer("texture-upload", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let readback = gal
        .create_buffer(buffer("texture-readback", vec![BufferUsage::TransferDst]))
        .unwrap();
    let texture_handle = gal
        .create_texture(texture(
            "copy-texture",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
        ))
        .unwrap();
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 16,
        rows_per_image: 4,
        texture: texture_handle,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };

    let read_region = BufferImageCopyRegion {
        buffer: readback,
        ..region.clone()
    };
    gal.create_command_list(CommandListDesc {
        label: "buffer-texture-transfer-with-barrier".to_owned(),
        operations: vec![
            CommandOp::CopyBufferToTexture(region.clone()),
            CommandOp::Barrier(ResourceBarrier {
                resource: texture_handle,
                subresources: Some(full_range),
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::TransferSrc,
                stages: PipelineStageFlags::TRANSFER,
                access: AccessFlags::TRANSFER,
                src_queue: QueueClass::Transfer,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::CopyTextureToBuffer(read_region.clone()),
        ],
    })
    .unwrap();

    let no_barrier = gal
        .create_command_list(CommandListDesc {
            label: "buffer-texture-transfer-without-barrier".to_owned(),
            operations: vec![
                CommandOp::CopyBufferToTexture(region.clone()),
                CommandOp::CopyTextureToBuffer(read_region.clone()),
            ],
        })
        .unwrap();
    assert_code(
        gal.submit(SubmissionBatch {
            label: "buffer-texture-hazard".to_owned(),
            command_lists: vec![no_barrier],
        }),
        super::StatusCode::InvalidArgument,
    );

    let transfer_src_only = gal
        .create_texture(texture(
            "transfer-src-only-texture",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferSrc],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-to-texture-without-dst-usage".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture: transfer_src_only,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-to-texture-bad-row-layout".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                bytes_per_row: 18,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-to-texture-outside-extent".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture_origin: TextureOrigin3d { x: 127, y: 0, z: 0 },
                ..region
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn indirect_host_and_malformed_ranges_are_validated_semantically() {
    let mut gal = gal();
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
fn resource_set_binding_requires_the_active_pipeline_layout() {
    let mut gal = gal();
    let (color_view, target, pass, _empty_layout, empty_pipeline) = simple_graphics_scene(&mut gal);
    let uniform = gal
        .create_buffer(buffer("uniform", vec![BufferUsage::Uniform]))
        .unwrap();
    let set_layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "set-layout".to_owned(),
            bindings: vec![layout_binding(
                0,
                ResourceBindingKind::UniformBuffer,
                PipelineStageFlags::DRAW,
            )],
        })
        .unwrap();
    let set = gal
        .create_resource_set(ResourceSetDesc {
            label: "set".to_owned(),
            layout: set_layout,
            bindings: vec![resource_binding(
                0,
                uniform,
                ResourceBindingKind::UniformBuffer,
                AccessFlags::READ,
            )],
        })
        .unwrap();
    let pipeline_layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "set-pipeline-layout".to_owned(),
            resource_layouts: vec![set_layout],
        })
        .unwrap();

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "bind-set-without-matching-active-layout".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(empty_pipeline),
                CommandOp::BindResourceSet {
                    pipeline_layout,
                    set_index: 0,
                    set,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn command_order_submission_ids_and_deferred_retirement_are_deterministic() {
    let mut gal = gal();
    let src = gal
        .create_buffer(buffer("src", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let dst = gal
        .create_buffer(buffer("dst", vec![BufferUsage::TransferDst]))
        .unwrap();
    let ops = vec![CommandOp::CopyBuffer {
        src,
        dst,
        size: 256,
    }];
    let mut caller_ops = ops.clone();
    let list = gal
        .create_command_list(CommandListDesc {
            label: "copy-list".to_owned(),
            operations: caller_ops.clone(),
        })
        .unwrap();
    caller_ops.clear();
    assert_eq!(list.operations, ops);

    let token = gal
        .submit(SubmissionBatch {
            label: "copy-batch".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    assert_eq!(token.submission.0, 1);
    assert_eq!(gal.mock_backend().unwrap().encoded_batches, 1);
    assert_eq!(
        gal.mock_backend().unwrap().submissions,
        vec![token.submission]
    );
    assert_eq!(
        gal.mock_backend()
            .unwrap()
            .submitted_labels
            .front()
            .unwrap(),
        "copy-batch"
    );

    gal.destroy(src).unwrap();
    assert_eq!(gal.metrics().deferred_retires, 1);
    assert_eq!(gal.metrics().resource_destroys, 0);

    gal.mock_backend_mut()
        .unwrap()
        .complete_through(token.submission);
    assert_eq!(gal.retire_completed().unwrap(), vec![src]);
    assert_eq!(gal.metrics().resource_destroys, 1);
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

#[test]
fn metrics_and_tracy_zones_are_gated() {
    let mut disabled = Metrics::new(false);
    {
        let _zone = disabled.zone("frame");
    }
    assert_eq!(disabled.zones["frame"].count, 1);
    assert_eq!(disabled.zones["frame"].total_nanos, 0);

    let mut enabled = Metrics::new(true);
    {
        let _zone = enabled.zone("frame");
        std::thread::sleep(std::time::Duration::from_micros(20));
    }
    assert_eq!(enabled.zones["frame"].count, 1);
    assert!(enabled.zones["frame"].total_nanos > 0);
}

#[test]
fn ffi_payload_validation_rejects_malformed_inputs() {
    unsafe {
        assert_code(
            validate_buffer_create_request(std::ptr::null()),
            super::StatusCode::NullPointer,
        );

        let request = FfiBufferCreateRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiBufferCreateRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            size: 64,
            memory_domain: 99,
            usage_bits: 1,
        };
        assert_code(
            validate_buffer_create_request(&request),
            super::StatusCode::UnknownEnum,
        );

        let misaligned = (&request as *const _ as usize + 1) as *const FfiBufferCreateRequest;
        if (misaligned as usize) % align_of::<FfiBufferCreateRequest>() != 0 {
            assert_code(
                validate_buffer_create_request(misaligned),
                super::StatusCode::Alignment,
            );
        }

        let huge_lists = FfiSlice::<FfiCommandListRequest> {
            ptr: NonNull::<FfiCommandListRequest>::dangling().as_ptr(),
            count: u64::MAX,
        };
        let huge_request = FfiSubmissionRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiSubmissionRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            command_lists: huge_lists,
        };
        assert_code(
            validate_submission_request(&huge_request),
            super::StatusCode::LengthOverflow,
        );

        let op_bytes = [1_u8, 2, 3, 4];
        let resource_uses = [FfiResourceUse {
            resource: FfiHandle { raw: 0 },
            stage_bits: 1,
            access_bits: 1,
        }];
        let list = FfiCommandListRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiCommandListRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            encoded_ops: FfiBytes {
                ptr: op_bytes.as_ptr(),
                len: op_bytes.len() as u64,
            },
            resource_uses: FfiSlice {
                ptr: resource_uses.as_ptr(),
                count: resource_uses.len() as u64,
            },
        };
        let request = FfiSubmissionRequest {
            header: FfiHeader {
                version: FFI_ABI_VERSION,
                byte_size: size_of::<FfiSubmissionRequest>() as u32,
            },
            label: FfiBytes {
                ptr: std::ptr::null(),
                len: 0,
            },
            command_lists: FfiSlice {
                ptr: &list,
                count: 1,
            },
        };
        assert_code(
            validate_submission_request(&request),
            super::StatusCode::InvalidArgument,
        );
    }
}

#[test]
fn backend_specific_api_tokens_do_not_leak_into_gal_core() {
    let root = Path::new(env!("CARGO_MANIFEST_DIR")).join("render/vulkanic");
    for relative in [
        "commands.rs",
        "error.rs",
        "ffi.rs",
        "gal.rs",
        "handles.rs",
        "metrics.rs",
        "mod.rs",
        "resources.rs",
        "sync.rs",
    ] {
        let source = fs::read_to_string(root.join(relative)).unwrap();
        for forbidden in [
            concat!("ash", "::"),
            concat!("glow", "::"),
            concat!("vk", "::"),
            concat!("GL", "_"),
            concat!("VK", "_"),
        ] {
            assert!(
                !source.contains(forbidden),
                "{relative} leaks backend-specific token {forbidden}"
            );
        }
    }
}

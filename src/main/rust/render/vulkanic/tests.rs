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
    let texture = gal
        .create_texture(texture(
            "color",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment, TextureUsage::Sampled],
        ))
        .unwrap();
    let color_view = gal
        .create_texture_view(view("color-view", texture, TextureFormat::Rgba8Unorm))
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
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::Sampler,
                    stages: PipelineStageFlags::DRAW,
                },
            ],
        })
        .unwrap();

    gal.create_resource_set(ResourceSetDesc {
        label: "set".to_owned(),
        layout,
        bindings: vec![
            ResourceBinding {
                binding: 0,
                resource: buffer,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::READ,
            },
            ResourceBinding {
                binding: 1,
                resource: sampler,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
            },
        ],
    })
    .unwrap();

    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "wrong-kind".to_owned(),
            layout,
            bindings: vec![ResourceBinding {
                binding: 0,
                resource: buffer,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "wrong-type".to_owned(),
            layout,
            bindings: vec![ResourceBinding {
                binding: 1,
                resource: buffer,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
            }],
        }),
        super::StatusCode::WrongHandleType,
    );
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "no-access".to_owned(),
            layout,
            bindings: vec![ResourceBinding {
                binding: 0,
                resource: buffer,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::NONE,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn pipeline_and_pass_compatibility_is_validated() {
    let mut gal = gal();
    let (color_view, target, pass, _layout, pipeline) = simple_graphics_scene(&mut gal);
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

    let bad_pass = gal
        .create_render_pass(RenderPassDesc {
            label: "bad-pass".to_owned(),
            target,
            color_formats: vec![TextureFormat::Bgra8Unorm],
            depth_format: None,
        })
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "bad".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass: bad_pass,
                    target,
                    colors: vec![color_attachment(color_view)],
                    depth_stencil: None,
                },
                CommandOp::BindGraphicsPipeline(pipeline),
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
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
                before: TextureUsageState::TransferSrc,
                after: TextureUsageState::TransferDst,
                stages: PipelineStageFlags::NONE,
                access: AccessFlags::TRANSFER,
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
    let list = gal
        .create_command_list(CommandListDesc {
            label: "copy-list".to_owned(),
            operations: ops.clone(),
        })
        .unwrap();
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

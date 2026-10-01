use std::collections::BTreeMap;
use std::fs;
use std::path::Path;

use super::backends::{mock::MockBackend, opengl_capabilities, vulkan_capabilities};
use super::commands::*;
use super::error::{ErrorDomain, GalError, StatusCode};
use super::frame::*;
use super::gal::{
    normalize_submission_batch, normalize_submission_batch_with_pipeline_layouts,
    CommandNormalizationStats, VulkanicGal,
};
use super::handles::{Handle, HandleKind, MAX_GENERATION};
use super::metrics::{Metrics, SubmitProfile};
use super::resources::*;
use super::sync::SubmissionId;

fn gal() -> VulkanicGal {
    VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false)
}

#[test]
fn submission_usage_tracks_accepted_work_and_retained_command_copies() {
    let mut gal = gal();
    let usage = SubmissionUsage::default();
    let batch = SubmissionBatch {
        label: "tracked commands".into(),
        command_lists: vec![gal
            .create_command_list(CommandListDesc {
                label: "tracked list".into(),
                operations: vec![CommandOp::TrackSubmission(usage.clone())],
            })
            .unwrap()],
    };
    assert!(usage.has_pending_commands());
    // A failed backend attempt consumes an ID but must not publish accepted use.
    gal.mock_backend_mut().unwrap().fail_next_submit = true;
    assert!(gal.submit(batch.clone()).is_err());
    assert_eq!(usage.last_submission(), SubmissionId(0));
    assert!(
        usage.has_pending_commands(),
        "the retained retry is still pending"
    );
    let first = gal.submit(batch.clone()).unwrap();
    assert_eq!(usage.last_submission(), first.submission);
    gal.retire_through(first.submission).unwrap();
    assert!(
        usage.has_pending_commands(),
        "completed use does not cancel a retained command copy"
    );
    let second = gal.submit(batch).unwrap();
    assert!(second.submission > first.submission);
    assert_eq!(usage.last_submission(), second.submission);
    assert!(!usage.has_pending_commands());
    assert_eq!(
        gal.poll_completed(),
        first.submission,
        "new accepted work remains in flight"
    );
    gal.retire_through(second.submission).unwrap();
    assert_eq!(gal.poll_completed(), usage.last_submission());

    let cancelled = CommandOp::TrackSubmission(usage.clone());
    drop(cancelled);
    assert!(!usage.has_pending_commands());
    assert_eq!(
        usage.last_submission(),
        second.submission,
        "cancellation preserves earlier accepted use"
    );
}

#[test]
fn completion_wait_rejects_unsubmitted_ids_without_fabricating_progress() {
    let mut gal = gal();
    assert!(gal.retire_through(SubmissionId(1)).is_err());
    assert_eq!(gal.poll_completed(), SubmissionId(0));
    assert!(gal.mock_backend().unwrap().retire_requests.is_empty());
    let batch = || SubmissionBatch {
        label: "completion-failure".into(),
        command_lists: vec![CommandList::from(CommandListDesc {
            label: "empty-but-valid-command-list".into(),
            operations: vec![],
        })],
    };
    gal.mock_backend_mut().unwrap().fail_next_submit = true;
    assert!(gal.submit(batch()).is_err());
    assert_eq!(gal.next_submission_id(), SubmissionId(2));
    assert_eq!(
        gal.latest_submission_id(),
        SubmissionId(0),
        "a failed attempt is not an accepted receipt"
    );
    assert!(gal.retire_through(SubmissionId(1)).is_err());
    assert!(gal.mock_backend().unwrap().retire_requests.is_empty());
    let token = gal.submit(batch()).unwrap();
    assert_eq!(token.submission, SubmissionId(2));
    assert_eq!(gal.latest_submission_id(), token.submission);
    gal.retire_through(token.submission).unwrap();
    assert_eq!(gal.poll_completed(), token.submission);
}

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

fn gal_with_capabilities(capabilities: BackendCapabilities) -> VulkanicGal {
    VulkanicGal::new_with_backend(
        Box::new(MockBackend::with_capabilities(capabilities)),
        false,
    )
}

fn presentation_capabilities() -> BackendCapabilities {
    let mut capabilities = vulkan_capabilities();
    capabilities.features.presentation = true;
    capabilities
}

fn frame_surface(label: &str) -> FrameSurfaceDesc {
    FrameSurfaceDesc {
        label: label.to_owned(),
        extent: Extent3d {
            width: 128,
            height: 72,
            depth: 1,
        },
        color_format: TextureFormat::Rgba8Unorm,
        present_mode: PresentMode::Fifo,
        max_frames_in_flight: 2,
    }
}

fn assert_code<T>(result: Result<T, GalError>, code: super::StatusCode) {
    let error = match result {
        Ok(_) => panic!("operation unexpectedly succeeded"),
        Err(error) => error,
    };
    assert_eq!(error.code, code, "{error}");
}

fn assert_unsupported<T>(result: Result<T, GalError>, needle: &str) {
    let error = match result {
        Ok(_) => panic!("operation unexpectedly succeeded"),
        Err(error) => error,
    };
    assert_eq!(error.code, super::StatusCode::UnsupportedFeature, "{error}");
    assert!(
        error.message.contains(needle),
        "unsupported error '{error}' did not contain '{needle}'"
    );
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
        comparison: None,
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
        buffer_range: None,
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
    (color_view, target, pass, layout, pipeline)
}

#[test]
fn backend_capabilities_are_queryable_and_fingerprinted_without_api_tokens() {
    let gal = gal_with_capabilities(opengl_capabilities());
    let capabilities = gal.capabilities();
    assert_eq!("Rust OpenGL", capabilities.name);
    assert!(capabilities.supports(BackendFeature::Graphics));
    assert!(capabilities.supports(BackendFeature::DescriptorArrays));
    assert!(!capabilities.supports(BackendFeature::Compute));
    assert!(!capabilities.supports(BackendFeature::StorageTextures));
    assert!(capabilities.supports(BackendFeature::Texture3d));
    assert!(capabilities.supports(BackendFeature::TextureMipLevels));
    assert!(capabilities.supports_texture_3d_usage(TextureFormat::R8Uint, TextureUsage::Sampled));
    assert!(capabilities
        .supports_texture_3d_usage(TextureFormat::Rgba16Float, TextureUsage::TransferDst));
    assert!(!capabilities.supports_texture_3d_usage(TextureFormat::R8Uint, TextureUsage::Storage));
    assert!(!capabilities
        .supports_texture_3d_usage(TextureFormat::R8Uint, TextureUsage::ColorAttachment));
    let vulkan = vulkan_capabilities();
    assert!(vulkan.supports_texture_3d_usage(TextureFormat::R8Uint, TextureUsage::Storage));
    assert!(vulkan.supports_texture_3d_usage(TextureFormat::Rgba16Float, TextureUsage::Storage));
    let fingerprint = capabilities.fingerprint_json();
    assert!(fingerprint.contains("\"name\":\"Rust OpenGL\""));
    assert!(fingerprint.contains("\"compute\":false"));
    assert!(fingerprint.contains("\"max_color_attachments\":4"));
    assert!(fingerprint.contains("\"texture_3d\":true"));
    assert!(fingerprint.contains("\"max_texture_extent_3d\":2048"));
    assert!(fingerprint.contains("\"uniform_buffer_offset_alignment\":256"));
    assert!(fingerprint.contains("\"max_commands_per_list\":8192"));
    assert!(fingerprint.contains("\"max_dispatch_groups_per_axis\":0"));
    for forbidden in [
        concat!("glow", "::"),
        concat!("ash", "::"),
        concat!("vk", "::"),
        concat!("GL", "_"),
        concat!("VK", "_"),
    ] {
        assert!(!fingerprint.contains(forbidden));
    }
}

#[test]
fn backend_capability_rejection_is_deterministic_before_native_creation() {
    let mut gal = gal_with_capabilities(opengl_capabilities());
    assert_unsupported(
        gal.create_texture(TextureDesc {
            dimension: TextureDimension::D3,
            extent: Extent3d {
                width: 8,
                height: 8,
                depth: 8,
            },
            array_layers: 1,
            usages: vec![TextureUsage::ColorAttachment],
            ..texture(
                "d3-render-target",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::ColorAttachment],
            )
        }),
        "not render targets",
    );
    assert_unsupported(
        gal.create_texture(TextureDesc {
            usages: vec![TextureUsage::Storage],
            ..texture(
                "storage-image",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::Storage],
            )
        }),
        "storage textures",
    );
    assert_unsupported(
        gal.create_compute_pipeline(ComputePipelineDesc {
            label: "compute".to_owned(),
            layout: Handle::from_raw(0),
            shader: Handle::from_raw(0),
        }),
        "compute pipelines",
    );
    assert_eq!(0, gal.metrics().resource_creates);
    assert_eq!(3, gal.metrics().validation_failures);
}

#[test]
fn capability_limits_reject_descriptor_and_attachment_overflows() {
    let mut gal = gal_with_capabilities(opengl_capabilities());
    assert_unsupported(
        gal.create_resource_layout(ResourceLayoutDesc {
            label: "too-many-bindings".to_owned(),
            bindings: (0..17)
                .map(|binding| {
                    layout_binding(
                        binding,
                        ResourceBindingKind::UniformBuffer,
                        PipelineStageFlags::DRAW,
                    )
                })
                .collect(),
        }),
        "binding count",
    );
    assert_unsupported(
        gal.create_resource_layout(ResourceLayoutDesc {
            label: "too-wide-array".to_owned(),
            bindings: vec![ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::UniformBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 9,
                optional: false,
                dynamic_offset_count: 0,
            }],
        }),
        "array count",
    );
    let vertex_shader = gal
        .create_shader_module(shader("vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("fragment", ShaderStage::Fragment))
        .unwrap();
    let layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "empty".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    assert_unsupported(
        gal.create_graphics_pipeline(GraphicsPipelineDesc {
            label: "too-many-attachments".to_owned(),
            layout,
            vertex_shader,
            fragment_shader,
            topology: PrimitiveTopology::Triangles,
            cull_mode: CullMode::None,
            front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
            provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
            raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
            blend: BlendMode::Disabled,
            depth_compare: None,
            depth_write: false,
            depth_bias: None,
            color_formats: vec![TextureFormat::Rgba8Unorm; 5],
            depth_format: None,
            stencil: None,
        }),
        "color attachment count",
    );
}

#[test]
fn unsupported_indirect_and_presentation_commands_reject_before_backend_encoding() {
    let mut gal = gal_with_capabilities(opengl_capabilities());
    let (_color_view, target, pass, _layout, pipeline) = simple_graphics_scene(&mut gal);
    let indirect = gal
        .create_buffer(buffer("indirect", vec![BufferUsage::Indirect]))
        .unwrap();
    assert_unsupported(
        gal.create_command_list(CommandListDesc {
            label: "unsupported-indirect".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(_color_view)],
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
        }),
        "indirect draw",
    );
    assert_unsupported(
        gal.create_command_list(CommandListDesc {
            label: "unsupported-indexed-indirect".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(_color_view)],
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
        "indexed indirect draw",
    );
    let present_texture = gal
        .create_texture(texture(
            "ordinary-color",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::ColorAttachment],
        ))
        .unwrap();
    assert_unsupported(
        gal.create_command_list(CommandListDesc {
            label: "unsupported-present".to_owned(),
            operations: vec![CommandOp::Present {
                texture: present_texture,
                subresources: TextureSubresourceRange {
                    base_mip: 0,
                    mip_count: 1,
                    base_layer: 0,
                    layer_count: 1,
                },
            }],
        }),
        "presentation commands",
    );
}

#[test]
fn frame_lifecycle_requires_presentation_capability() {
    let mut gal = gal();
    assert_unsupported(
        gal.configure_frame_surface(frame_surface("headless")),
        "presentation support",
    );
    assert_unsupported(
        gal.acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(1),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        }),
        "presentation support",
    );
}

#[test]
fn frame_surface_rejects_excessive_in_flight_slots() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    let mut surface = frame_surface("bounded-slots");
    surface.max_frames_in_flight = super::gal::MAX_FRAMES_IN_FLIGHT + 1;
    assert_code(
        gal.configure_frame_surface(surface),
        StatusCode::InvalidArgument,
    );
}

#[test]
fn frame_surface_rejects_oversized_or_non_2d_extent() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    let mut oversized = frame_surface("oversized-surface");
    oversized.extent.width = super::gal::MAX_FRAME_SURFACE_AXIS + 1;
    assert_code(
        gal.configure_frame_surface(oversized),
        StatusCode::InvalidArgument,
    );

    let mut three_dimensional = frame_surface("3d-surface");
    three_dimensional.extent.depth = 2;
    assert_code(
        gal.configure_frame_surface(three_dimensional),
        StatusCode::InvalidArgument,
    );
}

#[test]
fn frame_lifecycle_preserves_correlation_and_submission_ids() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("window"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(77),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    assert_eq!(acquired.status, FrameAcquireStatus::Ready);
    assert_eq!(acquired.correlation_id, FrameCorrelationId(77));
    assert_eq!(
        acquired.render_target,
        FrameRenderTargetId(acquired.frame.0)
    );
    let presented = gal
        .present_frame(PresentFrameDesc {
            frame: acquired.frame,
            correlation_id: acquired.correlation_id,
            wait_for: SubmissionId(9),
        })
        .unwrap();
    assert_eq!(presented.status, FramePresentStatus::Presented);
    assert_eq!(presented.completed_submission, SubmissionId(9));
    assert_eq!(gal.poll_completed(), SubmissionId(9));
}

#[test]
fn frame_lifecycle_models_resize_and_minimized_windows() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("resize"))
        .unwrap();
    let resize = gal
        .resize_frame_surface(FrameResizeDesc {
            correlation_id: FrameCorrelationId(2),
            extent: Extent3d {
                width: 256,
                height: 144,
                depth: 1,
            },
        })
        .unwrap();
    assert_eq!(resize.status, FrameAcquireStatus::Resized);
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(3),
            expected_extent: resize.extent,
        })
        .unwrap();
    assert_eq!(acquired.extent, resize.extent);
    let minimized = gal
        .resize_frame_surface(FrameResizeDesc {
            correlation_id: FrameCorrelationId(4),
            extent: Extent3d {
                width: 0,
                height: 0,
                depth: 1,
            },
        })
        .unwrap();
    assert_eq!(minimized.status, FrameAcquireStatus::Minimized);
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(5),
            expected_extent: minimized.extent,
        })
        .unwrap();
    assert_eq!(acquired.status, FrameAcquireStatus::Minimized);
}

#[test]
fn frame_lifecycle_rejects_present_without_acquire() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("bad-present"))
        .unwrap();
    assert_code(
        gal.present_frame(PresentFrameDesc {
            frame: FrameId(99),
            correlation_id: FrameCorrelationId(6),
            wait_for: SubmissionId(1),
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn frame_lifecycle_cancel_releases_an_acquired_frame() {
    let mut gal = gal_with_capabilities(presentation_capabilities());
    gal.configure_frame_surface(frame_surface("cancel"))
        .unwrap();
    let acquired = gal
        .acquire_frame(FrameAcquireDesc {
            correlation_id: FrameCorrelationId(8),
            expected_extent: Extent3d {
                width: 128,
                height: 72,
                depth: 1,
            },
        })
        .unwrap();
    gal.cancel_frame(acquired.frame).unwrap();
    assert_code(
        gal.present_frame(PresentFrameDesc {
            frame: acquired.frame,
            correlation_id: acquired.correlation_id,
            wait_for: SubmissionId(1),
        }),
        super::StatusCode::InvalidArgument,
    );
}

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

#[test]
fn large_supported_batches_validate_with_backend_limits() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let src = gal
        .create_buffer(BufferDesc {
            label: "large-src".to_owned(),
            size: 4096,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
        })
        .unwrap();
    let dst = gal
        .create_buffer(BufferDesc {
            label: "large-dst".to_owned(),
            size: 4096,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    let mut lists = Vec::new();
    for index in 0..8 {
        lists.push(
            gal.create_command_list(CommandListDesc {
                label: format!("large-list-{index}"),
                operations: vec![
                    CommandOp::CopyBuffer { src, dst, size: 16 },
                    CommandOp::Barrier(ResourceBarrier {
                        resource: dst,
                        subresources: None,
                        before: TextureUsageState::TransferDst,
                        after: TextureUsageState::ShaderRead,
                        src_queue: QueueClass::Graphics,
                        dst_queue: QueueClass::Graphics,
                    }),
                ],
            })
            .unwrap(),
        );
    }
    let token = gal
        .submit(SubmissionBatch {
            label: "large-submit".to_owned(),
            command_lists: lists,
        })
        .unwrap();
    assert_eq!(SubmissionId(1), token.submission);
    assert_eq!(1, gal.metrics().submissions);
}

#[test]
fn vulkan_capabilities_accept_mip_layer_copy_and_indirect_commands() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let upload = gal
        .create_buffer(BufferDesc {
            label: "mip-layer-upload".to_owned(),
            size: 64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::TransferSrc, BufferUsage::HostWrite],
        })
        .unwrap();
    let readback = gal
        .create_buffer(BufferDesc {
            label: "mip-layer-readback".to_owned(),
            size: 64,
            memory: MemoryDomain::Readback,
            usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
        })
        .unwrap();
    let texture = gal
        .create_texture(TextureDesc {
            label: "mip-layer-texture".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 8,
                height: 8,
                depth: 1,
            },
            mip_levels: 2,
            array_layers: 2,
            usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
        })
        .unwrap();
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 16,
        rows_per_image: 4,
        texture,
        texture_mip: 1,
        texture_layer: 1,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };
    gal.create_command_list(CommandListDesc {
        label: "mip-layer-copy".to_owned(),
        operations: vec![CommandOp::CopyBufferToTexture(region)],
    })
    .unwrap();
    let region = BufferImageCopyRegion {
        buffer: readback,
        buffer_offset: 0,
        bytes_per_row: 16,
        rows_per_image: 4,
        texture,
        texture_mip: 1,
        texture_layer: 1,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };
    gal.create_command_list(CommandListDesc {
        label: "mip-layer-readback".to_owned(),
        operations: vec![CommandOp::CopyTextureToBuffer(region)],
    })
    .unwrap();

    let indirect = gal
        .create_buffer(buffer("indirect", vec![BufferUsage::Indirect]))
        .unwrap();
    let (_color_view, target, pass, _layout, graphics) = simple_graphics_scene(&mut gal);
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "copy-inside-render-pass".to_owned(),
            operations: vec![
                CommandOp::BeginPass {
                    pass,
                    target,
                    colors: vec![color_attachment(_color_view)],
                    depth_stencil: None,
                },
                CommandOp::CopyBuffer {
                    src: upload,
                    dst: readback,
                    size: 4,
                },
                CommandOp::EndPass,
            ],
        }),
        super::StatusCode::InvalidArgument,
    );
    gal.create_command_list(CommandListDesc {
        label: "indirect-draw".to_owned(),
        operations: vec![
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![color_attachment(_color_view)],
                depth_stencil: None,
            },
            CommandOp::BindGraphicsPipeline(graphics),
            CommandOp::DrawIndirect {
                buffer: indirect,
                offset: 0,
                draw_count: 1,
            },
            CommandOp::EndPass,
        ],
    })
    .unwrap();

    let layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "compute-layout".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    let shader = gal
        .create_shader_module(shader("compute", ShaderStage::Compute))
        .unwrap();
    let compute = gal
        .create_compute_pipeline(ComputePipelineDesc {
            label: "compute".to_owned(),
            layout,
            shader,
        })
        .unwrap();
    gal.create_command_list(CommandListDesc {
        label: "indirect-dispatch".to_owned(),
        operations: vec![
            CommandOp::BindComputePipeline(compute),
            CommandOp::DispatchIndirect {
                buffer: indirect,
                offset: 0,
            },
        ],
    })
    .unwrap();
}

#[test]
fn opengl_capabilities_reject_storage_images_and_layered_copies() {
    let mut gal = gal_with_capabilities(opengl_capabilities());
    assert_unsupported(
        gal.create_texture(TextureDesc {
            label: "layered".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 8,
                height: 8,
                depth: 1,
            },
            mip_levels: 1,
            array_layers: 2,
            usages: vec![TextureUsage::Sampled],
        }),
        "layer count",
    );
    assert_unsupported(
        gal.create_resource_layout(ResourceLayoutDesc {
            label: "storage-image-layout".to_owned(),
            bindings: vec![ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::StorageTexture,
                stages: PipelineStageFlags::COMPUTE,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            }],
        }),
        "storage texture bindings",
    );
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
fn resource_sets_reject_linear_sampler_state_for_integer_sampled_textures() {
    let mut gal = gal();
    let occupancy = gal
        .create_texture(texture(
            "integer-occupancy",
            TextureFormat::R8Uint,
            vec![TextureUsage::Sampled],
        ))
        .unwrap();
    let occupancy_view = gal
        .create_texture_view(view(
            "integer-occupancy-view",
            occupancy,
            TextureFormat::R8Uint,
        ))
        .unwrap();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "integer-sampler-layout".to_owned(),
            bindings: vec![
                layout_binding(
                    0,
                    ResourceBindingKind::SampledTexture,
                    PipelineStageFlags::DRAW,
                ),
                layout_binding(1, ResourceBindingKind::Sampler, PipelineStageFlags::DRAW),
            ],
        })
        .unwrap();
    let linear_sampler = gal
        .create_sampler(sampler("linear-integer-sampler"))
        .unwrap();

    let error = gal
        .create_resource_set(ResourceSetDesc {
            label: "integer-linear-sampler-set".to_owned(),
            layout,
            bindings: vec![
                resource_binding(
                    0,
                    occupancy_view,
                    ResourceBindingKind::SampledTexture,
                    AccessFlags::READ,
                ),
                resource_binding(
                    1,
                    linear_sampler,
                    ResourceBindingKind::Sampler,
                    AccessFlags::READ,
                ),
            ],
        })
        .expect_err("integer textures must reject linear sampler filtering");
    assert_eq!(error.code, super::StatusCode::InvalidArgument);
    assert!(error.message.contains("integer sampled textures"));

    let nearest_sampler = gal
        .create_sampler(SamplerDesc {
            label: "nearest-integer-sampler".to_owned(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    gal.create_resource_set(ResourceSetDesc {
        label: "integer-nearest-sampler-set".to_owned(),
        layout,
        bindings: vec![
            resource_binding(
                0,
                occupancy_view,
                ResourceBindingKind::SampledTexture,
                AccessFlags::READ,
            ),
            resource_binding(
                1,
                nearest_sampler,
                ResourceBindingKind::Sampler,
                AccessFlags::READ,
            ),
        ],
    })
    .expect("integer textures must accept nearest sampler filtering");
}

#[test]
fn combined_texture_sampler_is_a_read_only_owned_pair() {
    let mut gal = gal();
    let occupancy = gal
        .create_texture(texture(
            "combined-integer-occupancy",
            TextureFormat::R8Uint,
            vec![TextureUsage::Sampled],
        ))
        .unwrap();
    let occupancy_view = gal
        .create_texture_view(view(
            "combined-integer-occupancy-view",
            occupancy,
            TextureFormat::R8Uint,
        ))
        .unwrap();
    let linear_sampler = gal
        .create_sampler(sampler("combined-linear-integer-sampler"))
        .unwrap();
    let error = gal
        .create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: "combined-linear-integer".to_owned(),
            texture_view: occupancy_view,
            sampler: linear_sampler,
        })
        .expect_err("integer combined samplers must reject linear filtering");
    assert_eq!(error.code, super::StatusCode::InvalidArgument);
    assert!(error.message.contains("integer combined texture samplers"));

    let nearest_sampler = gal
        .create_sampler(SamplerDesc {
            label: "combined-nearest-integer-sampler".to_owned(),
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: None,
        })
        .unwrap();
    let combined = gal
        .create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: "combined-nearest-integer".to_owned(),
            texture_view: occupancy_view,
            sampler: nearest_sampler,
        })
        .unwrap();
    let layout = gal
        .create_resource_layout(ResourceLayoutDesc {
            label: "combined-sampler-layout".to_owned(),
            bindings: vec![layout_binding(
                0,
                ResourceBindingKind::CombinedTextureSampler,
                PipelineStageFlags::DRAW,
            )],
        })
        .unwrap();
    let set = gal
        .create_resource_set(ResourceSetDesc {
            label: "combined-sampler-set".to_owned(),
            layout,
            bindings: vec![resource_binding(
                0,
                combined,
                ResourceBindingKind::CombinedTextureSampler,
                AccessFlags::READ,
            )],
        })
        .unwrap();

    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "combined-sampler-write-set".to_owned(),
            layout,
            bindings: vec![resource_binding(
                0,
                combined,
                ResourceBindingKind::CombinedTextureSampler,
                AccessFlags::WRITE,
            )],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.destroy(occupancy_view),
        super::StatusCode::DependencyViolation,
    );
    assert_code(
        gal.destroy(nearest_sampler),
        super::StatusCode::DependencyViolation,
    );
    gal.destroy(set).unwrap();
    gal.destroy(combined).unwrap();
    gal.destroy(nearest_sampler).unwrap();
    gal.destroy(occupancy_view).unwrap();
    gal.destroy(occupancy).unwrap();
}

#[test]
fn comparison_samplers_require_depth_views_and_preserve_ffi_default_sampling() {
    let mut gal = gal();
    let color = gal
        .create_texture(texture(
            "comparison-color",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::Sampled],
        ))
        .unwrap();
    let color_view = gal
        .create_texture_view(view(
            "comparison-color-view",
            color,
            TextureFormat::Rgba8Unorm,
        ))
        .unwrap();
    let comparison_sampler = gal
        .create_sampler(SamplerDesc {
            comparison: Some(CompareOp::LessOrEqual),
            ..sampler("comparison-sampler")
        })
        .unwrap();
    let error = gal
        .create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: "comparison-color-combined".to_owned(),
            texture_view: color_view,
            sampler: comparison_sampler,
        })
        .expect_err("comparison samplers must reject color texture views");
    assert_eq!(error.code, super::StatusCode::InvalidArgument);
    assert!(error.message.contains("depth texture view"));

    let depth = gal
        .create_texture(texture(
            "comparison-depth",
            TextureFormat::Depth32Float,
            vec![TextureUsage::Sampled, TextureUsage::DepthStencilAttachment],
        ))
        .unwrap();
    let depth_view = gal
        .create_texture_view(view(
            "comparison-depth-view",
            depth,
            TextureFormat::Depth32Float,
        ))
        .unwrap();
    let combined = gal
        .create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: "comparison-depth-combined".to_owned(),
            texture_view: depth_view,
            sampler: comparison_sampler,
        })
        .unwrap();
    gal.destroy(combined).unwrap();
    gal.destroy(depth_view).unwrap();
    gal.destroy(depth).unwrap();
    gal.destroy(color_view).unwrap();
    gal.destroy(color).unwrap();
    gal.destroy(comparison_sampler).unwrap();
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
                buffer_range: None,
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
    assert_code(
        gal.create_resource_set(ResourceSetDesc {
            label: "unaligned-dynamic-offset".to_owned(),
            layout: dynamic_layout,
            bindings: vec![ResourceBinding {
                binding: 0,
                array_index: 0,
                resource: first,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: vec![64],
                buffer_range: None,
            }],
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
            dynamic_offsets: vec![256],
            buffer_range: None,
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
            front_face: crate::render::vulkanic::resources::FrontFace::CounterClockwise,
            provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
            raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
            blend: BlendMode::Disabled,
            depth_compare: None,
            depth_write: false,
            depth_bias: None,
            color_formats: vec![TextureFormat::Bgra8Unorm],
            depth_format: None,
            stencil: None,
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
fn graphics_pipeline_depth_bias_requires_finite_enabled_depth_contract() {
    let mut gal = gal();
    let layout = gal
        .create_pipeline_layout(PipelineLayoutDesc {
            label: "depth-bias-layout".to_owned(),
            resource_layouts: vec![],
        })
        .unwrap();
    let vertex_shader = gal
        .create_shader_module(shader("depth-bias-vertex", ShaderStage::Vertex))
        .unwrap();
    let fragment_shader = gal
        .create_shader_module(shader("depth-bias-fragment", ShaderStage::Fragment))
        .unwrap();
    let pipeline = |label: &str, depth_compare, depth_bias, depth_format| GraphicsPipelineDesc {
        label: label.to_owned(),
        layout,
        vertex_shader,
        fragment_shader,
        topology: PrimitiveTopology::Triangles,
        cull_mode: CullMode::None,
        front_face: FrontFace::CounterClockwise,
        provoking_vertex: crate::render::vulkanic::resources::ProvokingVertex::Last,
        raster_y_direction: crate::render::vulkanic::resources::RasterYDirection::Up,
        blend: BlendMode::Disabled,
        depth_compare,
        depth_write: false,
        depth_bias,
        color_formats: vec![TextureFormat::Rgba8Unorm],
        depth_format,
        stencil: None,
    };

    assert_code(
        gal.create_graphics_pipeline(pipeline(
            "non-finite-depth-bias",
            Some(CompareOp::LessOrEqual),
            Some(DepthBias::new(f32::NAN, -1.0)),
            Some(TextureFormat::Depth32Float),
        )),
        StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_graphics_pipeline(pipeline(
            "depth-bias-without-depth-test",
            None,
            Some(DepthBias::new(-10.0, -1.0)),
            Some(TextureFormat::Depth32Float),
        )),
        StatusCode::InvalidArgument,
    );
    assert!(gal
        .create_graphics_pipeline(pipeline(
            "valid-depth-bias",
            Some(CompareOp::LessOrEqual),
            Some(DepthBias::new(-10.0, -1.0)),
            Some(TextureFormat::Depth32Float),
        ))
        .is_ok());
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
fn texture_row_reversal_is_capability_checked_and_retains_copy_validation() {
    for supported in [false, true] {
        let mut caps = vulkan_capabilities();
        caps.features.texture_row_reversal = supported;
        let mut gal = gal_with_capabilities(caps);
        let source = gal
            .create_texture(texture(
                "row-source",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::TransferSrc],
            ))
            .unwrap();
        let destination = gal
            .create_texture(texture(
                "row-destination",
                TextureFormat::Rgba8Unorm,
                vec![TextureUsage::TransferDst],
            ))
            .unwrap();
        let region = TextureImageCopyRegion {
            row_order: TextureRowOrder::Reverse,
            src_texture: source,
            src_mip: 0,
            src_layer: 0,
            src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            dst_texture: destination,
            dst_mip: 0,
            dst_layer: 0,
            dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent: Extent3d {
                width: 2,
                height: 3,
                depth: 1,
            },
        };
        let list = |region| CommandListDesc {
            label: "reverse-rows".into(),
            operations: vec![CommandOp::CopyTexture(region)],
        };
        if supported {
            gal.create_command_list(list(region.clone())).unwrap();
            assert_code(
                gal.create_command_list(list(TextureImageCopyRegion {
                    dst_texture: source,
                    ..region.clone()
                })),
                StatusCode::InvalidArgument,
            );
            assert_code(
                gal.create_command_list(list(TextureImageCopyRegion {
                    dst_origin: TextureOrigin3d { x: 0, y: 127, z: 0 },
                    ..region
                })),
                StatusCode::InvalidArgument,
            );
        } else {
            assert_code(
                gal.create_command_list(list(region.clone())),
                StatusCode::UnsupportedFeature,
            );
            gal.create_command_list(list(TextureImageCopyRegion {
                row_order: TextureRowOrder::Preserve,
                ..region
            }))
            .unwrap();
        }
    }
}

#[test]
fn vulkan_texture_row_reversal_copies_exact_asymmetric_color_and_depth_rows() {
    use super::backends::vulkan::VulkanBackend;
    // This is deliberately a real device test: inability to create the backend
    // must not be reported as a passing pixel comparison.
    let backend = VulkanBackend::new("explicit row reversal conformance").unwrap();
    let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
    for format in [TextureFormat::Rgba8Unorm, TextureFormat::Depth32Float] {
        let extent = Extent3d {
            width: 2,
            height: 3,
            depth: 1,
        };
        let pixels: Vec<u8> = if format == TextureFormat::Depth32Float {
            [0.125f32, 0.25, 0.375, 0.5, 0.625, 0.875]
                .into_iter()
                .flat_map(f32::to_le_bytes)
                .collect()
        } else {
            (1u8..=24).collect()
        };
        let source = gal
            .create_texture(TextureDesc {
                label: "row-source".into(),
                dimension: TextureDimension::D2,
                format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
            })
            .unwrap();
        let destination = gal
            .create_texture(TextureDesc {
                label: "row-destination".into(),
                dimension: TextureDimension::D2,
                format,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::TransferDst, TextureUsage::TransferSrc],
            })
            .unwrap();
        let upload = gal
            .create_buffer(BufferDesc {
                label: "row-upload".into(),
                size: 24,
                memory: MemoryDomain::Upload,
                usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
            })
            .unwrap();
        let readback = gal
            .create_buffer(BufferDesc {
                label: "row-readback".into(),
                size: 24,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        let barrier = |resource, before, after| {
            CommandOp::Barrier(ResourceBarrier {
                resource,
                subresources: None,
                before,
                after,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Graphics,
            })
        };
        let copy = |buffer, texture| BufferImageCopyRegion {
            buffer,
            buffer_offset: 0,
            bytes_per_row: 8,
            rows_per_image: 3,
            texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
            extent,
        };
        let list = gal
            .create_command_list(CommandListDesc {
                label: "explicit-row-reversal".into(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: upload,
                        offset: 0,
                        data: pixels.clone(),
                    },
                    barrier(
                        upload,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    ),
                    barrier(
                        source,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    ),
                    CommandOp::CopyBufferToTexture(copy(upload, source)),
                    barrier(
                        source,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    ),
                    barrier(
                        destination,
                        TextureUsageState::Undefined,
                        TextureUsageState::TransferDst,
                    ),
                    CommandOp::CopyTexture(TextureImageCopyRegion {
                        row_order: TextureRowOrder::Reverse,
                        src_texture: source,
                        src_mip: 0,
                        src_layer: 0,
                        src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        dst_texture: destination,
                        dst_mip: 0,
                        dst_layer: 0,
                        dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
                        extent,
                    }),
                    barrier(
                        destination,
                        TextureUsageState::TransferDst,
                        TextureUsageState::TransferSrc,
                    ),
                    CommandOp::CopyTextureToBuffer(copy(readback, destination)),
                    barrier(
                        readback,
                        TextureUsageState::TransferDst,
                        TextureUsageState::ShaderRead,
                    ),
                    CommandOp::HostReadBuffer {
                        buffer: readback,
                        offset: 0,
                        size: 24,
                    },
                ],
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "explicit-row-reversal".into(),
                command_lists: vec![list],
            })
            .unwrap();
        gal.retire_through_for_test(token.submission).unwrap();
        let reads = gal.completed_host_reads();
        let actual = &reads
            .iter()
            .rev()
            .find(|read| read.buffer == readback)
            .unwrap()
            .bytes;
        let expected: Vec<u8> = pixels.chunks_exact(8).rev().flatten().copied().collect();
        assert_eq!(
            &expected, actual,
            "row reversal must preserve exact texels for {format:?}"
        );
        for handle in [source, destination, upload, readback] {
            gal.destroy(handle).unwrap();
        }
    }
}

#[test]
fn texture_copy_validates_depth_snapshot_regions_and_transfer_hazards() {
    let mut gal = gal();
    let source = gal
        .create_texture(texture(
            "depth-copy-source",
            TextureFormat::Depth32Float,
            vec![TextureUsage::TransferSrc],
        ))
        .unwrap();
    let destination = gal
        .create_texture(texture(
            "depth-copy-destination",
            TextureFormat::Depth32Float,
            vec![TextureUsage::TransferDst],
        ))
        .unwrap();
    let region = TextureImageCopyRegion {
        row_order: crate::render::vulkanic::commands::TextureRowOrder::Preserve,
        src_texture: source,
        src_mip: 0,
        src_layer: 0,
        src_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        dst_texture: destination,
        dst_mip: 0,
        dst_layer: 0,
        dst_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };
    gal.create_command_list(CommandListDesc {
        label: "depth-snapshot-copy".to_owned(),
        operations: vec![
            CommandOp::Barrier(ResourceBarrier {
                resource: source,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferSrc,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::Barrier(ResourceBarrier {
                resource: destination,
                subresources: None,
                before: TextureUsageState::Undefined,
                after: TextureUsageState::TransferDst,
                src_queue: QueueClass::Graphics,
                dst_queue: QueueClass::Transfer,
            }),
            CommandOp::CopyTexture(region.clone()),
        ],
    })
    .unwrap();

    let rgba_destination = gal
        .create_texture(texture(
            "depth-copy-rgba-destination",
            TextureFormat::Rgba8Unorm,
            vec![TextureUsage::TransferDst],
        ))
        .unwrap();
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "depth-copy-format-mismatch".to_owned(),
            operations: vec![CommandOp::CopyTexture(TextureImageCopyRegion {
                dst_texture: rgba_destination,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "depth-copy-outside-extent".to_owned(),
            operations: vec![CommandOp::CopyTexture(TextureImageCopyRegion {
                dst_origin: TextureOrigin3d { x: 127, y: 0, z: 0 },
                ..region
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn d3_texture_validation_enforces_dimension_layers_mips_and_copy_boxes() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let upload = gal
        .create_buffer(buffer("d3-upload", vec![BufferUsage::TransferSrc]))
        .unwrap();
    let texture = gal
        .create_texture(TextureDesc {
            label: "d3-r8".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 2,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::TransferDst,
                TextureUsage::TransferSrc,
                TextureUsage::Sampled,
            ],
        })
        .unwrap();
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 4,
        rows_per_image: 4,
        texture,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 2,
        },
    };
    gal.create_command_list(CommandListDesc {
        label: "d3-full-upload".to_owned(),
        operations: vec![CommandOp::CopyBufferToTexture(region.clone())],
    })
    .unwrap();

    assert_code(
        gal.create_texture_view(TextureViewDesc {
            label: "d3-invalid-layer-view".to_owned(),
            texture,
            format: TextureFormat::R8Uint,
            base_mip: 0,
            mip_count: 1,
            base_layer: 1,
            layer_count: 1,
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-invalid-array-layer-copy".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture_layer: 1,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-invalid-partial-row".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                bytes_per_row: 3,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-invalid-depth-box".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                texture_origin: TextureOrigin3d { x: 0, y: 0, z: 1 },
                ..region
            })],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn d3_texture_lifecycle_validates_mips_storage_transitions_and_retirement() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let texture = gal
        .create_texture(TextureDesc {
            label: "d3-rgba16-storage".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::Rgba16Float,
            extent: Extent3d {
                width: 8,
                height: 4,
                depth: 2,
            },
            mip_levels: 2,
            array_layers: 1,
            usages: vec![
                TextureUsage::Sampled,
                TextureUsage::Storage,
                TextureUsage::TransferDst,
            ],
        })
        .unwrap();
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 2,
        base_layer: 0,
        layer_count: 1,
    };
    let list = gal
        .create_command_list(CommandListDesc {
            label: "d3-storage-transition".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::ShaderWrite,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::ShaderWrite,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ],
        })
        .unwrap();
    let submission = gal
        .submit(SubmissionBatch {
            label: "d3-storage-transition-submit".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.destroy(texture).unwrap();
    assert!(gal
        .retire_through_for_test(submission.submission)
        .unwrap()
        .contains(&texture));
    assert_code(
        gal.create_texture_view(TextureViewDesc {
            label: "stale-d3-view".to_owned(),
            texture,
            format: TextureFormat::Rgba16Float,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }),
        super::StatusCode::StaleHandle,
    );

    assert_code(
        gal.create_texture(TextureDesc {
            label: "d3-too-many-mips".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 4,
            },
            mip_levels: 4,
            array_layers: 1,
            usages: vec![TextureUsage::Sampled],
        }),
        super::StatusCode::InvalidArgument,
    );
}

#[test]
fn mip_generation_requires_explicit_ranges_usages_and_non_integer_color_formats() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let texture = gal
        .create_texture(TextureDesc {
            label: "mip-generation-color".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::Rgba8Unorm,
            extent: Extent3d {
                width: 16,
                height: 8,
                depth: 1,
            },
            mip_levels: 5,
            array_layers: 1,
            usages: vec![
                TextureUsage::TransferSrc,
                TextureUsage::TransferDst,
                TextureUsage::Sampled,
            ],
        })
        .unwrap();
    let base_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    let descendant_range = TextureSubresourceRange {
        base_mip: 1,
        mip_count: 4,
        base_layer: 0,
        layer_count: 1,
    };
    let full_range = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 5,
        base_layer: 0,
        layer_count: 1,
    };
    let list = gal
        .create_command_list(CommandListDesc {
            label: "mip-generation-valid".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(base_range),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::TransferSrc,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(descendant_range),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::TransferDst,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::GenerateMipmaps {
                    texture,
                    subresources: full_range,
                },
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(full_range),
                    before: TextureUsageState::TransferSrc,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ],
        })
        .unwrap();
    gal.submit(SubmissionBatch {
        label: "mip-generation-valid-submit".to_owned(),
        command_lists: vec![list],
    })
    .unwrap();

    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "mip-generation-one-level".to_owned(),
            operations: vec![CommandOp::GenerateMipmaps {
                texture,
                subresources: base_range,
            }],
        }),
        super::StatusCode::InvalidArgument,
    );

    let integer = gal
        .create_texture(TextureDesc {
            label: "mip-generation-integer".to_owned(),
            dimension: TextureDimension::D2,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 1,
            },
            mip_levels: 3,
            array_layers: 1,
            usages: vec![TextureUsage::TransferSrc, TextureUsage::TransferDst],
        })
        .unwrap();
    assert_unsupported(
        gal.create_command_list(CommandListDesc {
            label: "mip-generation-integer-command".to_owned(),
            operations: vec![CommandOp::GenerateMipmaps {
                texture: integer,
                subresources: TextureSubresourceRange {
                    base_mip: 0,
                    mip_count: 3,
                    base_layer: 0,
                    layer_count: 1,
                },
            }],
        }),
        "non-depth, non-integer",
    );
}

#[test]
fn rejected_d3_copy_does_not_poison_the_following_valid_submission() {
    let mut gal = gal_with_capabilities(vulkan_capabilities());
    let upload = gal
        .create_buffer(BufferDesc {
            label: "d3-rollback-upload".to_owned(),
            size: 32,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
        })
        .unwrap();
    let texture = gal
        .create_texture(TextureDesc {
            label: "d3-rollback-volume".to_owned(),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent: Extent3d {
                width: 4,
                height: 4,
                depth: 2,
            },
            mip_levels: 1,
            array_layers: 1,
            usages: vec![TextureUsage::TransferDst, TextureUsage::Sampled],
        })
        .unwrap();
    let region = BufferImageCopyRegion {
        buffer: upload,
        buffer_offset: 0,
        bytes_per_row: 4,
        rows_per_image: 4,
        texture,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 2,
        },
    };
    let subresources = TextureSubresourceRange {
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    };
    assert_code(
        gal.create_command_list(CommandListDesc {
            label: "d3-rejected-copy".to_owned(),
            operations: vec![CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                bytes_per_row: 3,
                ..region.clone()
            })],
        }),
        super::StatusCode::InvalidArgument,
    );

    let list = gal
        .create_command_list(CommandListDesc {
            label: "d3-valid-copy-after-rejection".to_owned(),
            operations: vec![
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(subresources),
                    before: TextureUsageState::Undefined,
                    after: TextureUsageState::TransferDst,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
                CommandOp::CopyBufferToTexture(region),
                CommandOp::Barrier(ResourceBarrier {
                    resource: texture,
                    subresources: Some(subresources),
                    before: TextureUsageState::TransferDst,
                    after: TextureUsageState::ShaderRead,
                    src_queue: QueueClass::Graphics,
                    dst_queue: QueueClass::Graphics,
                }),
            ],
        })
        .unwrap();
    let submission = gal
        .submit(SubmissionBatch {
            label: "d3-valid-copy-after-rejection-submit".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
    gal.destroy(texture).unwrap();
    gal.destroy(upload).unwrap();
    let retired = gal.retire_through_for_test(submission.submission).unwrap();
    assert!(retired.contains(&texture));
    assert!(retired.contains(&upload));
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
                    dynamic_offsets: Vec::new(),
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

fn test_handle(kind: HandleKind, index: u32) -> Handle {
    Handle::new(kind, index, 1).unwrap()
}

fn minimal_begin_pass() -> CommandOp {
    CommandOp::BeginPass {
        pass: test_handle(HandleKind::RenderPass, 1),
        target: test_handle(HandleKind::RenderTarget, 1),
        colors: vec![PassAttachment {
            view: test_handle(HandleKind::TextureView, 1),
            load_op: AttachmentLoadOp::Load,
            store_op: AttachmentStoreOp::Store,
            clear_color: None,
        }],
        depth_stencil: None,
    }
}

fn normalize_ops_for_test(
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

fn normalize_ops_for_test_with_pipeline_layouts(
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
fn backend_specific_api_tokens_do_not_leak_into_gal_core() {
    // The GAL core (everything in `vulkanic` outside `backends/`) and the
    // bridge never name a backend API; only backend modules may.
    fn collect(dir: &Path, files: &mut Vec<std::path::PathBuf>) {
        for entry in fs::read_dir(dir).unwrap() {
            let path = entry.unwrap().path();
            if path.is_dir() {
                if path.file_name().is_some_and(|name| name != "backends") {
                    collect(&path, files);
                }
            } else if path.extension().is_some_and(|ext| ext == "rs") {
                files.push(path);
            }
        }
    }
    let render = Path::new(env!("CARGO_MANIFEST_DIR")).join("render");
    let mut files = Vec::new();
    collect(&render.join("vulkanic"), &mut files);
    collect(&render.join("bridge"), &mut files);
    let this_file = Path::new(file!()).file_name().unwrap();
    assert!(files.iter().any(|file| file.ends_with("vulkanic/gal.rs")));
    assert!(files.iter().any(|file| file.ends_with("bridge/abi/common.rs")));
    for file in files {
        if file.file_name() == Some(this_file) && file.parent().unwrap().ends_with("vulkanic") {
            continue;
        }
        let source = fs::read_to_string(&file).unwrap();
        for forbidden in [
            concat!("ash", "::"),
            concat!("glow", "::"),
            concat!("vk", "::"),
            concat!("GL", "_"),
            concat!("VK", "_"),
        ] {
            assert!(
                !source.contains(forbidden),
                "{} leaks backend-specific token {forbidden}",
                file.display()
            );
        }
    }
}

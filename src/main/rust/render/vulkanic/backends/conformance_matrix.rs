use crate::render::vulkanic::error::GalError;
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::*;
use crate::render::vulkanic::{
    AccessFlags, AttachmentLoadOp, AttachmentStoreOp, BufferDesc, BufferUsage, ClearColor,
    CommandListDesc, CommandOp, MemoryDomain, PassAttachment, QueueClass, RenderPassDesc,
    RenderTargetDesc, ResourceBarrier, SubmissionBatch, TextureDesc, TextureFormat,
    TextureSubresourceRange, TextureUsage, TextureUsageState, TextureViewDesc,
};

const WIDTH: u32 = 96;
const HEIGHT: u32 = 64;

#[test]
fn vulkan_and_opengl_clean_conformance_match_for_shared_graphics_subset() {
    let extent = Extent3d {
        width: WIDTH,
        height: HEIGHT,
        depth: 1,
    };
    let vulkan = match super::vulkan::conformance::run_conformance("matrix-shared", extent) {
        Ok(report) => report,
        Err(error) => {
            assert_environment_gap(&error, "Vulkan");
            return;
        }
    };
    let opengl = match super::opengl::conformance::run_conformance("matrix-shared", extent) {
        Ok(report) => report,
        Err(error) => {
            assert_environment_gap(&error, "OpenGL");
            return;
        }
    };
    assert_eq!(vulkan.width, opengl.width);
    assert_eq!(vulkan.height, opengl.height);
    assert_eq!(vulkan.pixel_hash, opengl.pixel_hash);
    assert_eq!(vulkan.non_zero_pixels, opengl.non_zero_pixels);
    assert_eq!(vulkan.evidence_json, opengl.evidence_json);
    assert!(vulkan
        .capabilities_json
        .contains("\"name\":\"Rust Vulkan\""));
    assert!(opengl
        .capabilities_json
        .contains("\"name\":\"Rust OpenGL\""));
    assert!(vulkan.capabilities_json.contains("\"compute\":true"));
    assert!(opengl.capabilities_json.contains("\"compute\":false"));
}

#[test]
fn shared_pass_shape_conformance_covers_mrt_and_depth_only() {
    for backend in [BackendKind::Vulkan, BackendKind::OpenGl] {
        let mut gal = match gal_for(backend, "MattMC pass shape conformance") {
            Ok(gal) => gal,
            Err(error) => {
                assert_environment_gap(&error, backend.name());
                continue;
            }
        };
        submit_mrt_clear(&mut gal)
            .unwrap_or_else(|error| panic!("{} MRT pass shape failed: {error}", backend.name()));
        submit_depth_only_clear(&mut gal).unwrap_or_else(|error| {
            panic!("{} depth-only pass shape failed: {error}", backend.name())
        });
    }
}

#[test]
fn shared_large_copy_lifecycle_conformance_reuses_and_retires_resources() {
    for backend in [BackendKind::Vulkan, BackendKind::OpenGl] {
        let mut gal = match gal_for(backend, "MattMC large copy lifecycle conformance") {
            Ok(gal) => gal,
            Err(error) => {
                assert_environment_gap(&error, backend.name());
                continue;
            }
        };
        let src = gal
            .create_buffer(BufferDesc {
                label: "large-copy-src".to_owned(),
                size: 4096,
                memory: MemoryDomain::Upload,
                usages: vec![
                    BufferUsage::HostWrite,
                    BufferUsage::TransferSrc,
                    BufferUsage::TransferDst,
                ],
            })
            .unwrap();
        let dst = gal
            .create_buffer(BufferDesc {
                label: "large-copy-dst".to_owned(),
                size: 4096,
                memory: MemoryDomain::Readback,
                usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
            })
            .unwrap();
        let mut lists = Vec::new();
        lists.push(
            gal.create_command_list(CommandListDesc {
                label: "large-copy-upload".to_owned(),
                operations: vec![
                    CommandOp::HostWriteBuffer {
                        buffer: src,
                        offset: 0,
                        data: vec![backend.seed(); 4096],
                    },
                    buffer_barrier(src),
                ],
            })
            .unwrap(),
        );
        for index in 0..24 {
            lists.push(
                gal.create_command_list(CommandListDesc {
                    label: format!("large-copy-{index}"),
                    operations: vec![
                        CommandOp::CopyBuffer {
                            src,
                            dst,
                            size: 256,
                        },
                        buffer_barrier(dst),
                    ],
                })
                .unwrap(),
            );
        }
        lists.push(
            gal.create_command_list(CommandListDesc {
                label: "large-copy-read".to_owned(),
                operations: vec![CommandOp::HostReadBuffer {
                    buffer: dst,
                    offset: 0,
                    size: 256,
                }],
            })
            .unwrap(),
        );
        let token = gal
            .submit(SubmissionBatch {
                label: format!("{}-large-copy-submit", backend.name()),
                command_lists: lists,
            })
            .unwrap();
        gal.destroy(src).unwrap();
        gal.destroy(dst).unwrap();
        let retired = gal.retire_through_for_test(token.submission).unwrap();
        assert_eq!(2, retired.len(), "{} should retire src/dst", backend.name());
    }
}

fn assert_environment_gap(error: &GalError, backend: &str) {
    let text = error.to_string();
    assert!(
        text.contains(backend)
            || text.contains("physical device")
            || text.contains("EGL")
            || text.contains("GL"),
        "unexpected cross-backend conformance failure: {text}"
    );
}

#[derive(Clone, Copy)]
enum BackendKind {
    Vulkan,
    OpenGl,
}

impl BackendKind {
    fn name(self) -> &'static str {
        match self {
            Self::Vulkan => "Vulkan",
            Self::OpenGl => "OpenGL",
        }
    }

    fn seed(self) -> u8 {
        match self {
            Self::Vulkan => 0x56,
            Self::OpenGl => 0x47,
        }
    }
}

fn gal_for(backend: BackendKind, label: &str) -> Result<VulkanicGal, GalError> {
    match backend {
        BackendKind::Vulkan => Ok(VulkanicGal::new_with_backend(
            Box::new(super::vulkan::VulkanBackend::new(label)?),
            false,
        )),
        BackendKind::OpenGl => Ok(VulkanicGal::new_with_backend(
            Box::new(super::opengl::OpenGlBackend::new(label)?),
            false,
        )),
    }
}

fn submit_mrt_clear(gal: &mut VulkanicGal) -> Result<(), GalError> {
    let extent = extent();
    let color_a = color_texture(gal, "mrt.color.a", extent)?;
    let color_b = color_texture(gal, "mrt.color.b", extent)?;
    let view_a =
        gal.create_texture_view(view("mrt.color.a.view", color_a, TextureFormat::Rgba8Unorm))?;
    let view_b =
        gal.create_texture_view(view("mrt.color.b.view", color_b, TextureFormat::Rgba8Unorm))?;
    let target = gal.create_render_target(RenderTargetDesc {
        label: "mrt.target".to_owned(),
        color_views: vec![view_a, view_b],
        depth_stencil_view: None,
        extent,
    })?;
    let pass = gal.create_render_pass(RenderPassDesc {
        label: "mrt.pass".to_owned(),
        target,
        color_formats: vec![TextureFormat::Rgba8Unorm, TextureFormat::Rgba8Unorm],
        depth_format: None,
    })?;
    let list = gal.create_command_list(CommandListDesc {
        label: "mrt.clear".to_owned(),
        operations: vec![
            texture_barrier(
                color_a,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            ),
            texture_barrier(
                color_b,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            ),
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![
                    color_attachment(view_a, 0.25, 0.0, 0.0, 1.0),
                    color_attachment(view_b, 0.0, 0.25, 0.0, 1.0),
                ],
                depth_stencil: None,
            },
            CommandOp::EndPass,
        ],
    })?;
    let token = gal.submit(SubmissionBatch {
        label: "mrt.submit".to_owned(),
        command_lists: vec![list],
    })?;
    gal.retire_through_for_test(token.submission)?;
    Ok(())
}

fn submit_depth_only_clear(gal: &mut VulkanicGal) -> Result<(), GalError> {
    let extent = extent();
    let depth = gal.create_texture(TextureDesc {
        label: "depth-only.depth".to_owned(),
        dimension: TextureDimension::D2,
        format: TextureFormat::Depth32Float,
        extent,
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::DepthStencilAttachment],
    })?;
    let depth_view =
        gal.create_texture_view(view("depth-only.view", depth, TextureFormat::Depth32Float))?;
    let target = gal.create_render_target(RenderTargetDesc {
        label: "depth-only.target".to_owned(),
        color_views: vec![],
        depth_stencil_view: Some(depth_view),
        extent,
    })?;
    let pass = gal.create_render_pass(RenderPassDesc {
        label: "depth-only.pass".to_owned(),
        target,
        color_formats: vec![],
        depth_format: Some(TextureFormat::Depth32Float),
    })?;
    let list = gal.create_command_list(CommandListDesc {
        label: "depth-only.clear".to_owned(),
        operations: vec![
            texture_barrier(
                depth,
                TextureUsageState::Undefined,
                TextureUsageState::DepthStencilAttachment,
            ),
            CommandOp::BeginPass {
                pass,
                target,
                colors: vec![],
                depth_stencil: Some(PassAttachment {
                    view: depth_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::DontCare,
                    clear_color: None,
                }),
            },
            CommandOp::EndPass,
        ],
    })?;
    let token = gal.submit(SubmissionBatch {
        label: "depth-only.submit".to_owned(),
        command_lists: vec![list],
    })?;
    gal.retire_through_for_test(token.submission)?;
    Ok(())
}

fn color_texture(gal: &mut VulkanicGal, label: &str, extent: Extent3d) -> Result<Handle, GalError> {
    gal.create_texture(TextureDesc {
        label: label.to_owned(),
        dimension: TextureDimension::D2,
        format: TextureFormat::Rgba8Unorm,
        extent,
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::ColorAttachment],
    })
}

fn extent() -> Extent3d {
    Extent3d {
        width: 32,
        height: 32,
        depth: 1,
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

fn color_attachment(view: Handle, r: f32, g: f32, b: f32, a: f32) -> PassAttachment {
    PassAttachment {
        view,
        load_op: AttachmentLoadOp::Clear,
        store_op: AttachmentStoreOp::Store,
        clear_color: Some(ClearColor { r, g, b, a }),
    }
}

fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> CommandOp {
    CommandOp::Barrier(ResourceBarrier {
        resource,
        subresources: Some(TextureSubresourceRange {
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }),
        before,
        after,
        stages: PipelineStageFlags::TRANSFER,
        access: AccessFlags::TRANSFER,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    })
}

fn buffer_barrier(resource: Handle) -> CommandOp {
    CommandOp::Barrier(ResourceBarrier {
        resource,
        subresources: None,
        before: TextureUsageState::TransferDst,
        after: TextureUsageState::TransferSrc,
        stages: PipelineStageFlags::TRANSFER,
        access: AccessFlags::TRANSFER,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    })
}

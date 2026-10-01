//! GAL tests by concern; shared descriptor builders and mock GALs live here.

mod submission;
mod capabilities;
mod frames;
mod frame_targets;
mod handles;
mod bindings;
mod pipelines;
mod commands;
mod textures;
mod hazards;
mod normalization;


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
use super::metrics::SubmitProfile;
use super::resources::*;
use super::sync::SubmissionId;

fn gal() -> VulkanicGal {
    VulkanicGal::new_with_backend(Box::new(MockBackend::default()))
}

fn gal_with_capabilities(capabilities: BackendCapabilities) -> VulkanicGal {
    VulkanicGal::new_with_backend(Box::new(MockBackend::with_capabilities(capabilities)))
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


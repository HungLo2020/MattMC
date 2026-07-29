use std::path::PathBuf;

use xxhash_rust::xxh32::xxh32;

use super::device::{ValidationMode, VulkanContext};
use super::shaderc_spirv_compiler::compile_glsl_for_backend_test;
use super::trace;
use super::VulkanBackend;
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, BufferImageCopyRegion, ClearColor, CommandListDesc,
    CommandOp, PassAttachment, ResourceBarrier, SubmissionBatch, TextureOrigin3d,
    TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::resources::*;

const WIDTH: u32 = 96;
const HEIGHT: u32 = 64;

#[test]
fn isolated_vulkan_conformance_renders_indexed_textured_draw() {
    let result = run_conformance(
        "standard",
        Extent3d {
            width: WIDTH,
            height: HEIGHT,
            depth: 1,
        },
    );
    match result {
        Ok(report) => {
            assert_ne!(report.pixel_hash, 0);
            assert!(report.non_zero_pixels > 0);
        }
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("Vulkan")
                    || text.contains("vulkan")
                    || text.contains("physical device"),
                "unexpected conformance failure: {text}"
            );
        }
    }
}

#[test]
fn isolated_vulkan_conformance_pins_gal_coordinate_and_state_conventions() {
    match run_conformance(
        "conventions",
        Extent3d {
            width: WIDTH,
            height: HEIGHT,
            depth: 1,
        },
    ) {
        Ok(report) => assert_conformance_conventions(&report),
        Err(error) => {
            let text = error.to_string();
            assert!(
                text.contains("Vulkan")
                    || text.contains("vulkan")
                    || text.contains("physical device"),
                "unexpected convention conformance failure: {text}"
            );
        }
    }
}

#[test]
fn isolated_vulkan_conformance_supports_resize_recreation() {
    if let Err(error) = run_conformance(
        "resize-small",
        Extent3d {
            width: 32,
            height: 32,
            depth: 1,
        },
    ) {
        assert!(
            error.to_string().contains("Vulkan") || error.to_string().contains("physical device"),
            "unexpected resize failure: {error}"
        );
        return;
    }
    let report = run_conformance(
        "resize-large",
        Extent3d {
            width: 64,
            height: 48,
            depth: 1,
        },
    )
    .expect("second conformance run should recreate resources cleanly");
    assert_ne!(report.pixel_hash, 0);
}

#[test]
fn isolated_vulkan_conformance_rejects_partial_failure_cleanly() {
    let context = match VulkanContext::new("MattMC partial failure test", ValidationMode::Off) {
        Ok(context) => context,
        Err(error) => {
            assert!(
                error.to_string().contains("Vulkan")
                    || error.to_string().contains("physical device")
            );
            return;
        }
    };
    let impossible = ash::vk::MemoryRequirements {
        size: u64::MAX / 2,
        alignment: 4096,
        memory_type_bits: u32::MAX,
    };
    assert!(context
        .allocate_memory(impossible, ash::vk::MemoryPropertyFlags::DEVICE_LOCAL)
        .is_err());
    context.wait_idle().expect("device should remain usable");
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub(in crate::render::vulkanic::backends) struct ConformanceReport {
    pub(in crate::render::vulkanic::backends) mode: String,
    pub(in crate::render::vulkanic::backends) width: u32,
    pub(in crate::render::vulkanic::backends) height: u32,
    pub(in crate::render::vulkanic::backends) pixel_hash: u32,
    pub(in crate::render::vulkanic::backends) non_zero_pixels: usize,
    pub(in crate::render::vulkanic::backends) evidence_json: String,
    pub(in crate::render::vulkanic::backends) capabilities_json: String,
}

fn assert_conformance_conventions(report: &ConformanceReport) {
    assert_eq!(report.pixel_hash, 0x7212_13ca);
    assert_eq!(report.non_zero_pixels, 15_362);
    assert!(report.evidence_json.contains("\"top_left\":[0,0,0,255]"));
    assert!(report.evidence_json.contains("\"top_mid\":[0,0,0,255]"));
    assert!(report
        .evidence_json
        .contains("\"upper_inner\":[96,0,191,255]"));
    assert!(report.evidence_json.contains("\"center\":[191,0,191,255]"));
    assert!(report
        .evidence_json
        .contains("\"lower_inner\":[191,0,191,255]"));
    assert!(report.evidence_json.contains("\"bottom_mid\":[0,0,0,255]"));
    assert!(report
        .evidence_json
        .contains("\"alpha_counts\":{\"255\":6144}"));
}

pub(in crate::render::vulkanic::backends) fn run_conformance(
    mode: &str,
    extent: Extent3d,
) -> GalResult<ConformanceReport> {
    trace::message("rust-vulkan-conformance-start");
    let validation = ValidationMode::from_env();
    let backend = VulkanBackend::new("MattMC VulkanicGAL conformance")?;
    let mut gal = VulkanicGal::new_with_backend(Box::new(backend), tracy_enabled_from_env());
    let capabilities_json = gal.capabilities().fingerprint_json();
    let _renderdoc_frame = super::renderdoc::RenderDocFrame::start_if_requested();

    let texture_bytes = texture_bytes();
    let index_bytes = index_bytes();
    let upload_texture = gal.create_buffer(BufferDesc {
        label: "conformance.texture-upload".to_string(),
        size: texture_bytes.len() as u64,
        memory: MemoryDomain::Upload,
        usages: vec![BufferUsage::HostWrite, BufferUsage::TransferSrc],
    })?;
    let index = gal.create_buffer(BufferDesc {
        label: "conformance.index-upload".to_string(),
        size: index_bytes.len() as u64,
        memory: MemoryDomain::Upload,
        usages: vec![BufferUsage::HostWrite, BufferUsage::Index],
    })?;
    let readback_size = u64::from(extent.width) * u64::from(extent.height) * 4;
    let readback = gal.create_buffer(BufferDesc {
        label: "conformance.readback".to_string(),
        size: readback_size,
        memory: MemoryDomain::Readback,
        usages: vec![BufferUsage::TransferDst, BufferUsage::HostRead],
    })?;
    let uniform_a = gal.create_buffer(BufferDesc {
        label: "conformance.uniform.a".to_string(),
        size: 64,
        memory: MemoryDomain::DeviceLocal,
        usages: vec![BufferUsage::Uniform],
    })?;
    let uniform_b = gal.create_buffer(BufferDesc {
        label: "conformance.uniform.b".to_string(),
        size: 64,
        memory: MemoryDomain::DeviceLocal,
        usages: vec![BufferUsage::Uniform],
    })?;
    let storage = gal.create_buffer(BufferDesc {
        label: "conformance.storage".to_string(),
        size: 128,
        memory: MemoryDomain::DeviceLocal,
        usages: vec![BufferUsage::Storage],
    })?;
    let sampled = gal.create_texture(TextureDesc {
        label: "conformance.sampled".to_string(),
        dimension: TextureDimension::D2,
        format: TextureFormat::Rgba8Unorm,
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::TransferDst, TextureUsage::Sampled],
    })?;
    let color = gal.create_texture(TextureDesc {
        label: "conformance.color".to_string(),
        dimension: TextureDimension::D2,
        format: TextureFormat::Rgba8Unorm,
        extent,
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::ColorAttachment, TextureUsage::TransferSrc],
    })?;
    let depth = gal.create_texture(TextureDesc {
        label: "conformance.depth".to_string(),
        dimension: TextureDimension::D2,
        format: TextureFormat::Depth32Float,
        extent,
        mip_levels: 1,
        array_layers: 1,
        usages: vec![TextureUsage::DepthStencilAttachment],
    })?;
    let sampled_view = gal.create_texture_view(view(
        "conformance.sampled.view",
        sampled,
        TextureFormat::Rgba8Unorm,
    ))?;
    let color_view = gal.create_texture_view(view(
        "conformance.color.view",
        color,
        TextureFormat::Rgba8Unorm,
    ))?;
    let depth_view = gal.create_texture_view(view(
        "conformance.depth.view",
        depth,
        TextureFormat::Depth32Float,
    ))?;
    let sampler = gal.create_sampler(SamplerDesc {
        label: "conformance.sampler".to_string(),
        min_filter: SamplerFilter::Nearest,
        mag_filter: SamplerFilter::Nearest,
        mip_filter: SamplerFilter::Nearest,
        address_u: SamplerAddressMode::ClampToEdge,
        address_v: SamplerAddressMode::ClampToEdge,
        address_w: SamplerAddressMode::ClampToEdge,
    })?;
    let resource_layout = gal.create_resource_layout(ResourceLayoutDesc {
        label: "conformance.resource-layout".to_string(),
        bindings: vec![
            ResourceBindingDesc {
                binding: 0,
                kind: ResourceBindingKind::SampledTexture,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 1,
                kind: ResourceBindingKind::Sampler,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 2,
                kind: ResourceBindingKind::UniformBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 2,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 3,
                kind: ResourceBindingKind::StorageBuffer,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: false,
                dynamic_offset_count: 0,
            },
            ResourceBindingDesc {
                binding: 4,
                kind: ResourceBindingKind::Sampler,
                stages: PipelineStageFlags::DRAW,
                array_count: 1,
                optional: true,
                dynamic_offset_count: 0,
            },
        ],
    })?;
    let resource_set = gal.create_resource_set(ResourceSetDesc {
        label: "conformance.resource-set".to_string(),
        layout: resource_layout,
        bindings: vec![
            ResourceBinding {
                binding: 0,
                array_index: 0,
                resource: sampled_view,
                kind: ResourceBindingKind::SampledTexture,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
            },
            ResourceBinding {
                binding: 1,
                array_index: 0,
                resource: sampler,
                kind: ResourceBindingKind::Sampler,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
            },
            ResourceBinding {
                binding: 2,
                array_index: 0,
                resource: uniform_a,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
            },
            ResourceBinding {
                binding: 2,
                array_index: 1,
                resource: uniform_b,
                kind: ResourceBindingKind::UniformBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
            },
            ResourceBinding {
                binding: 3,
                array_index: 0,
                resource: storage,
                kind: ResourceBindingKind::StorageBuffer,
                access: AccessFlags::READ,
                dynamic_offsets: Vec::new(),
            },
        ],
    })?;
    let pipeline_layout = gal.create_pipeline_layout(PipelineLayoutDesc {
        label: "conformance.pipeline-layout".to_string(),
        resource_layouts: vec![resource_layout],
    })?;
    let vertex_shader = shader_module(
        "conformance.vertex",
        ShaderStage::Vertex,
        shaderc::ShaderKind::Vertex,
        VERTEX_SHADER,
    )?;
    let fragment_shader = shader_module(
        "conformance.fragment",
        ShaderStage::Fragment,
        shaderc::ShaderKind::Fragment,
        FRAGMENT_SHADER,
    )?;
    let vertex_shader = gal.create_shader_module(vertex_shader)?;
    let fragment_shader = gal.create_shader_module(fragment_shader)?;
    let pipeline = gal.create_graphics_pipeline(GraphicsPipelineDesc {
        label: "conformance.pipeline".to_string(),
        layout: pipeline_layout,
        vertex_shader,
        fragment_shader,
        topology: PrimitiveTopology::Triangles,
        cull_mode: CullMode::Back,
        blend: BlendMode::Alpha,
        depth_compare: Some(CompareOp::LessOrEqual),
        depth_write: true,
        color_formats: vec![TextureFormat::Rgba8Unorm],
        depth_format: Some(TextureFormat::Depth32Float),
    })?;
    let target = gal.create_render_target(RenderTargetDesc {
        label: "conformance.target".to_string(),
        color_views: vec![color_view],
        depth_stencil_view: Some(depth_view),
        extent,
    })?;
    let pass = gal.create_render_pass(RenderPassDesc {
        label: "conformance.pass".to_string(),
        target,
        color_formats: vec![TextureFormat::Rgba8Unorm],
        depth_format: Some(TextureFormat::Depth32Float),
    })?;

    let command_list = gal.create_command_list(CommandListDesc {
        label: "conformance.commands".to_string(),
        operations: conformance_ops(
            upload_texture,
            index,
            readback,
            sampled,
            color,
            depth,
            pass,
            target,
            color_view,
            depth_view,
            pipeline,
            pipeline_layout,
            resource_set,
            texture_bytes,
            index_bytes,
            extent,
        ),
    })?;
    let token = gal.submit(SubmissionBatch {
        label: "conformance.submit".to_string(),
        command_lists: vec![command_list],
    })?;
    gal.retire_through_for_test(token.submission)?;
    let reads = gal
        .vulkan_backend()
        .ok_or_else(|| GalError::backend("conformance backend was not Vulkan"))?
        .completed_host_reads_for_test();
    let pixels = reads
        .iter()
        .rev()
        .find(|read| read.buffer == readback)
        .map(|read| read.bytes.clone())
        .ok_or_else(|| GalError::backend("conformance readback command produced no bytes"))?;
    let pixel_hash = xxh32(&pixels, 0x4d_43_47_41);
    let non_zero_pixels = pixels.iter().filter(|byte| **byte != 0).count();
    let evidence_json = pixel_evidence_json(&pixels, extent);
    let report = ConformanceReport {
        mode: mode.to_string(),
        width: extent.width,
        height: extent.height,
        pixel_hash,
        non_zero_pixels,
        evidence_json,
        capabilities_json,
    };
    write_report(&report, validation)?;
    trace::message("rust-vulkan-conformance-complete");
    Ok(report)
}

#[allow(clippy::too_many_arguments)]
fn conformance_ops(
    upload_texture: crate::render::vulkanic::handles::Handle,
    index: crate::render::vulkanic::handles::Handle,
    readback: crate::render::vulkanic::handles::Handle,
    sampled: crate::render::vulkanic::handles::Handle,
    color: crate::render::vulkanic::handles::Handle,
    depth: crate::render::vulkanic::handles::Handle,
    pass: crate::render::vulkanic::handles::Handle,
    target: crate::render::vulkanic::handles::Handle,
    color_view: crate::render::vulkanic::handles::Handle,
    depth_view: crate::render::vulkanic::handles::Handle,
    pipeline: crate::render::vulkanic::handles::Handle,
    pipeline_layout: crate::render::vulkanic::handles::Handle,
    resource_set: crate::render::vulkanic::handles::Handle,
    texture_bytes: Vec<u8>,
    index_bytes: Vec<u8>,
    extent: Extent3d,
) -> Vec<CommandOp> {
    let tex_copy = BufferImageCopyRegion {
        buffer: upload_texture,
        buffer_offset: 0,
        bytes_per_row: 16,
        rows_per_image: 4,
        texture: sampled,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent: Extent3d {
            width: 4,
            height: 4,
            depth: 1,
        },
    };
    let read_copy = BufferImageCopyRegion {
        buffer: readback,
        buffer_offset: 0,
        bytes_per_row: extent.width * 4,
        rows_per_image: extent.height,
        texture: color,
        texture_mip: 0,
        texture_layer: 0,
        texture_origin: TextureOrigin3d { x: 0, y: 0, z: 0 },
        extent,
    };
    vec![
        CommandOp::HostWriteBuffer {
            buffer: upload_texture,
            offset: 0,
            data: texture_bytes,
        },
        buffer_barrier(
            upload_texture,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        ),
        CommandOp::HostWriteBuffer {
            buffer: index,
            offset: 0,
            data: index_bytes,
        },
        buffer_barrier(
            index,
            TextureUsageState::TransferDst,
            TextureUsageState::IndexRead,
        ),
        texture_barrier(
            sampled,
            TextureUsageState::Undefined,
            TextureUsageState::TransferDst,
        ),
        CommandOp::CopyBufferToTexture(tex_copy),
        texture_barrier(
            sampled,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        ),
        texture_barrier(
            color,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        ),
        texture_barrier(
            depth,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        ),
        CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view: color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(ClearColor {
                    r: 0.0,
                    g: 0.0,
                    b: 0.0,
                    a: 1.0,
                }),
            }],
            depth_stencil: Some(PassAttachment {
                view: depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::DontCare,
                clear_color: None,
            }),
        },
        CommandOp::BindGraphicsPipeline(pipeline),
        CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
        },
        CommandOp::SetIndexBuffer {
            buffer: index,
            offset: 0,
        },
        CommandOp::DrawIndexed {
            indices: 6,
            instances: 1,
        },
        CommandOp::EndPass,
        texture_barrier(
            color,
            TextureUsageState::ColorAttachment,
            TextureUsageState::TransferSrc,
        ),
        CommandOp::CopyTextureToBuffer(read_copy),
        buffer_barrier(
            readback,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        ),
        CommandOp::HostReadBuffer {
            buffer: readback,
            offset: 0,
            size: u64::from(extent.width) * u64::from(extent.height) * 4,
        },
    ]
}

fn buffer_barrier(
    resource: crate::render::vulkanic::handles::Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> CommandOp {
    CommandOp::Barrier(ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        stages: PipelineStageFlags::TRANSFER,
        access: AccessFlags::TRANSFER,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    })
}

fn texture_barrier(
    resource: crate::render::vulkanic::handles::Handle,
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

fn view(
    label: &str,
    texture: crate::render::vulkanic::handles::Handle,
    format: TextureFormat,
) -> TextureViewDesc {
    TextureViewDesc {
        label: label.to_string(),
        texture,
        format,
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    }
}

fn shader_module(
    label: &str,
    stage: ShaderStage,
    kind: shaderc::ShaderKind,
    source: &str,
) -> GalResult<ShaderModuleDesc> {
    let code = compile_glsl_for_backend_test(kind, source, label).map_err(GalError::backend)?;
    Ok(ShaderModuleDesc {
        label: label.to_string(),
        stage,
        code_format: ShaderCodeFormat::Spirv,
        code,
        entry_point: "main".to_string(),
    })
}

fn texture_bytes() -> Vec<u8> {
    vec![
        255, 0, 0, 255, 0, 255, 0, 255, 0, 0, 255, 255, 255, 255, 0, 255, 255, 255, 255, 255, 0,
        128, 255, 255, 255, 0, 255, 255, 32, 32, 32, 255, 255, 128, 0, 255, 0, 255, 128, 255, 128,
        0, 255, 255, 255, 255, 255, 255, 64, 64, 64, 255, 255, 0, 128, 255, 0, 255, 255, 255, 128,
        255, 0, 255,
    ]
}

fn index_bytes() -> Vec<u8> {
    [0_u32, 1, 2, 2, 3, 0]
        .into_iter()
        .flat_map(u32::to_ne_bytes)
        .collect()
}

fn tracy_enabled_from_env() -> bool {
    std::env::var("MATTMC_RUST_TRACY")
        .map(|value| value == "1" || value.eq_ignore_ascii_case("true"))
        .unwrap_or(false)
}

fn write_report(report: &ConformanceReport, validation: ValidationMode) -> GalResult<()> {
    let path = report_path()?;
    std::fs::create_dir_all(path.parent().expect("report path has parent")).map_err(|error| {
        GalError::backend(format!("failed to create conformance log dir: {error}"))
    })?;
    let json = format!(
        "{{\n  \"artifact_class\": \"rust_vulkan_conformance\",\n  \"backend\": \"Rust Vulkan\",\n  \"mode\": \"{}\",\n  \"validation\": \"{:?}\",\n  \"validation_features\": [{}],\n  \"instrumentation\": \"clean-conformance\",\n  \"width\": {},\n  \"height\": {},\n  \"pixel_hash_xxh32\": \"{:08x}\",\n  \"non_zero_pixels\": {},\n  \"pixel_evidence\": {},\n  \"backend_capabilities\": {}\n}}\n",
        report.mode,
        validation,
        validation_features_json(validation),
        report.width,
        report.height,
        report.pixel_hash,
        report.non_zero_pixels,
        report.evidence_json,
        report.capabilities_json
    );
    std::fs::write(&path, json)
        .map_err(|error| GalError::backend(format!("failed to write conformance report: {error}")))
}

fn pixel_evidence_json(pixels: &[u8], extent: Extent3d) -> String {
    let width = extent.width as usize;
    let height = extent.height as usize;
    let row_bytes = width * 4;
    let top_row = &pixels[..row_bytes];
    let bottom_row = &pixels[(height - 1) * row_bytes..height * row_bytes];
    let mut alpha_counts = std::collections::BTreeMap::<u8, usize>::new();
    for pixel in pixels.chunks_exact(4) {
        *alpha_counts.entry(pixel[3]).or_default() += 1;
    }
    let alpha_json = alpha_counts
        .into_iter()
        .map(|(alpha, count)| format!("\"{}\":{}", alpha, count))
        .collect::<Vec<_>>()
        .join(",");
    format!(
        "{{\"top_left\":{},\"top_mid\":{},\"upper_inner\":{},\"center\":{},\"lower_inner\":{},\"bottom_mid\":{},\"bottom_left\":{},\"top_row_hash\":\"{:08x}\",\"bottom_row_hash\":\"{:08x}\",\"alpha_counts\":{{{}}}}}",
        pixel_json(pixels, width, 0, 0),
        pixel_json(pixels, width, width / 2, 0),
        pixel_json(pixels, width, width / 2, height / 3),
        pixel_json(pixels, width, width / 2, height / 2),
        pixel_json(pixels, width, width / 2, (height * 2) / 3),
        pixel_json(pixels, width, width / 2, height - 1),
        pixel_json(pixels, width, 0, height - 1),
        xxh32(top_row, 0x4d_43_47_41),
        xxh32(bottom_row, 0x4d_43_47_41),
        alpha_json
    )
}

fn pixel_json(pixels: &[u8], width: usize, x: usize, y: usize) -> String {
    let index = (y * width + x) * 4;
    format!(
        "[{},{},{},{}]",
        pixels[index],
        pixels[index + 1],
        pixels[index + 2],
        pixels[index + 3]
    )
}

fn report_path() -> GalResult<PathBuf> {
    let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
        .parent()
        .and_then(|path| path.parent())
        .and_then(|path| path.parent())
        .map(PathBuf::from)
        .ok_or_else(|| GalError::backend("failed to locate repository root from Cargo manifest"))?;
    Ok(root.join("logs/rust-vulkanic/conformance/latest.json"))
}

fn validation_features_json(validation: ValidationMode) -> &'static str {
    match validation {
        ValidationMode::Off => "",
        ValidationMode::Routine => "\"synchronization_validation\", \"best_practices\"",
        ValidationMode::Deep => {
            "\"gpu_assisted\", \"gpu_assisted_reserve_binding_slot\", \"synchronization_validation\", \"best_practices\""
        }
    }
}

const VERTEX_SHADER: &str = r#"
#version 450
layout(location = 0) out vec2 v_uv;
vec2 pos[4] = vec2[](
    vec2(-0.85, -0.85),
    vec2( 0.85, -0.85),
    vec2( 0.85,  0.85),
    vec2(-0.85,  0.85)
);
vec2 uv[4] = vec2[](
    vec2(0.0, 0.0),
    vec2(1.0, 0.0),
    vec2(1.0, 1.0),
    vec2(0.0, 1.0)
);
void main() {
    gl_Position = vec4(pos[gl_VertexIndex], 0.5, 1.0);
    v_uv = uv[gl_VertexIndex];
}
"#;

const FRAGMENT_SHADER: &str = r#"
#version 450
layout(set = 0, binding = 0) uniform texture2D tex0;
layout(set = 0, binding = 1) uniform sampler samp0;
layout(location = 0) in vec2 v_uv;
layout(location = 0) out vec4 out_color;
void main() {
    out_color = texture(sampler2D(tex0, samp0), v_uv) * vec4(1.0, 1.0, 1.0, 0.75);
}
"#;

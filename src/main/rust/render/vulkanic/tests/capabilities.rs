//! Backend capabilities: queries, fingerprints, limits and capability-gated rejection.

use super::*;

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
    assert!(files.iter().any(|file| file.ends_with("vulkanic/gal/mod.rs")));
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

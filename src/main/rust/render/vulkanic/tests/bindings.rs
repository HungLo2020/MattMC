//! Resource-set validation: layouts, binding kinds, arrays, samplers and the active pipeline layout.

use super::*;

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

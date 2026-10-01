//! Pipeline and pass compatibility, depth bias and render-target views.

use super::*;

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

use crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract;
use crate::render::shaderpack::vanilla::post_effect::executor::*;
use crate::render::vulkanic::handles::HandleKind;

/// Explicit-binding conventions with every adaptation macro enabled.
fn test_conventions() -> ShaderConventions {
    ShaderConventions {
        glsl_dialect: GlslDialect::ExplicitBindings,
        zero_to_one_clip_depth: true,
        flip_fullscreen_uv_y: true,
        readback_rows_bottom_up: false,
    }
}

fn handle(kind: HandleKind, index: u32) -> Handle {
    Handle::new(kind, index, 1).unwrap()
}

#[test]
fn lowers_each_validated_pass_to_one_explicit_triangle_pass() {
    let contract = VanillaPostEffectContract::parse(
        "invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    let plan = contract.execution_plan();
    let bindings = (0..plan.ordered_passes.len())
        .map(|index| VanillaPostEffectPassBinding {
            render_pass: handle(HandleKind::RenderPass, index as u32 + 1),
            render_target: handle(HandleKind::RenderTarget, index as u32 + 1),
            color_attachment: handle(HandleKind::TextureView, index as u32 + 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, index as u32 + 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, index as u32 + 1),
            resource_set: handle(HandleKind::ResourceSet, index as u32 + 1),
            inputs: vec![VanillaPostEffectInputBinding {
                texture_view: handle(HandleKind::TextureView, index as u32 + 1),
                sampler: handle(HandleKind::Sampler, index as u32 + 1),
                bilinear: false,
                use_depth_buffer: false,
            }],
            uniform_values: plan.ordered_passes[index].uniform_values.clone(),
        })
        .collect::<Vec<_>>();
    let operations = lower_execution_plan(&plan, &bindings).unwrap();
    assert_eq!(plan.ordered_passes.len() * 5, operations.len());
    assert!(operations.iter().any(|operation| matches!(
        operation,
        CommandOp::Draw {
            vertices: 3,
            instances: 1
        }
    )));
}

#[test]
fn null_private_binding_is_rejected_before_command_generation() {
    let contract = VanillaPostEffectContract::parse(
        "invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    let plan = contract.execution_plan();
    let bindings = vec![
        VanillaPostEffectPassBinding {
            render_pass: Handle::NULL,
            render_target: handle(HandleKind::RenderTarget, 1),
            color_attachment: handle(HandleKind::TextureView, 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, 1),
            resource_set: handle(HandleKind::ResourceSet, 1),
            inputs: vec![VanillaPostEffectInputBinding {
                texture_view: handle(HandleKind::TextureView, 1),
                sampler: handle(HandleKind::Sampler, 1),
                bilinear: false,
                use_depth_buffer: false,
            }],
            uniform_values: plan.ordered_passes[0].uniform_values.clone(),
        };
        plan.ordered_passes.len()
    ];
    let error = lower_execution_plan(&plan, &bindings).unwrap_err();
    assert!(error.to_string().contains("invalid render pass binding"));
    assert!(error.to_string().contains("null handle"));
}

#[test]
fn wrong_private_binding_kind_is_rejected_before_command_generation() {
    let contract = VanillaPostEffectContract::parse(
        "invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    let plan = contract.execution_plan();
    let bindings = vec![
        VanillaPostEffectPassBinding {
            render_pass: handle(HandleKind::Buffer, 1),
            render_target: handle(HandleKind::RenderTarget, 1),
            color_attachment: handle(HandleKind::TextureView, 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, 1),
            resource_set: handle(HandleKind::ResourceSet, 1),
            inputs: vec![VanillaPostEffectInputBinding {
                texture_view: handle(HandleKind::TextureView, 1),
                sampler: handle(HandleKind::Sampler, 1),
                bilinear: false,
                use_depth_buffer: false,
            }],
            uniform_values: plan.ordered_passes[0].uniform_values.clone(),
        };
        plan.ordered_passes.len()
    ];
    let error = lower_execution_plan(&plan, &bindings).unwrap_err();
    assert!(error.to_string().contains("invalid render pass binding"));
    assert!(error.to_string().contains("expected RenderPass"));
}

#[test]
fn graph_sampling_flags_must_match_private_input_bindings() {
    let contract = VanillaPostEffectContract::parse(
        "blur",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/blur.json"),
    )
    .unwrap();
    let plan = contract.execution_plan();
    let mut bindings = plan
        .ordered_passes
        .iter()
        .enumerate()
        .map(|(index, pass)| VanillaPostEffectPassBinding {
            render_pass: handle(HandleKind::RenderPass, index as u32 + 1),
            render_target: handle(HandleKind::RenderTarget, index as u32 + 1),
            color_attachment: handle(HandleKind::TextureView, index as u32 + 1),
            depth_attachment: None,
            pipeline: handle(HandleKind::GraphicsPipeline, index as u32 + 1),
            pipeline_layout: handle(HandleKind::PipelineLayout, index as u32 + 1),
            resource_set: handle(HandleKind::ResourceSet, index as u32 + 1),
            inputs: pass
                .inputs
                .iter()
                .map(|input| VanillaPostEffectInputBinding {
                    texture_view: handle(HandleKind::TextureView, index as u32 + 1),
                    sampler: handle(HandleKind::Sampler, index as u32 + 1),
                    bilinear: input.bilinear,
                    use_depth_buffer: input.use_depth_buffer,
                })
                .collect(),
            uniform_values: pass.uniform_values.clone(),
        })
        .collect::<Vec<_>>();
    bindings[0].inputs[0].bilinear = false;
    let error = lower_execution_plan(&plan, &bindings).unwrap_err();
    assert!(error.to_string().contains("sampling flags do not match"));
}

#[test]
fn packs_bundled_uniform_blocks_with_std140_alignment() {
    let contract = VanillaPostEffectContract::parse(
        "blur",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/blur.json"),
    )
    .unwrap();
    let block = pack_uniform_block(&contract.passes[0], "BlurConfig").unwrap();
    // vec2 at offset 0, scalar at offset 8, block rounded to 16 bytes.
    assert_eq!(16, block.len());
    assert_eq!(&block[0..4], &1.0f32.to_le_bytes());
    assert_eq!(&block[4..8], &0.0f32.to_le_bytes());
    assert_eq!(&block[8..12], &0.0f32.to_le_bytes());
    assert!(block[12..].iter().all(|byte| *byte == 0));

    let invert = VanillaPostEffectContract::parse(
        "invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    let inverse = pack_uniform_block(&invert.passes[0], "InvertConfig").unwrap();
    assert_eq!(16, inverse.len());
    assert_eq!(&inverse[0..4], &0.8f32.to_le_bytes());
    let packed = pack_uniform_blocks(&invert.passes[0]).unwrap();
    assert_eq!(packed.len(), 1);
    assert_eq!(packed.get("InvertConfig"), Some(&inverse));
}

#[test]
fn vulkan_screenquad_normalization_flips_attachment_row_origin() {
    let source =
        include_bytes!("../../../../../../resources/assets/minecraft/shaders/core/screenquad.vsh");
    let lowered = String::from_utf8(
        lower_post_effect_vertex_source(test_conventions(), source)
            .expect("bundled screenquad must normalize for Vulkan"),
    )
    .unwrap();
    assert!(lowered.contains("gl_VertexIndex"));
    assert!(lowered.contains("texCoord.y = 1.0 - texCoord.y;"));
}

#[test]
fn packs_integer_uniforms_as_four_byte_components() {
    let pass = VanillaPostEffectPass {
        vertex_shader: "v".to_owned(),
        fragment_shader: "f".to_owned(),
        inputs: Vec::new(),
        output: "minecraft:main".to_owned(),
        uniform_blocks: BTreeSet::from(["Config".to_owned()]),
        uniform_values: BTreeMap::from([(
            "Config".to_owned(),
            vec![
                VanillaPostEffectUniform {
                    name: "Mode".to_owned(),
                    value_type: "int".to_owned(),
                    values: vec![3.0],
                },
                VanillaPostEffectUniform {
                    name: "Mask".to_owned(),
                    value_type: "ivec3".to_owned(),
                    values: vec![1.0, 2.0, 4.0],
                },
            ],
        )]),
    };
    let bytes = pack_uniform_block(&pass, "Config").unwrap();
    assert_eq!(32, bytes.len());
    assert_eq!(&bytes[0..4], &3i32.to_le_bytes());
    assert_eq!(&bytes[16..28], &[1, 0, 0, 0, 2, 0, 0, 0, 4, 0, 0, 0]);
}

#[test]
fn packs_matrix4x4_as_four_std140_columns() {
    let pass = VanillaPostEffectPass {
        vertex_shader: "v".to_owned(),
        fragment_shader: "f".to_owned(),
        inputs: Vec::new(),
        output: "minecraft:main".to_owned(),
        uniform_blocks: BTreeSet::from(["Transform".to_owned()]),
        uniform_values: BTreeMap::from([(
            "Transform".to_owned(),
            vec![VanillaPostEffectUniform {
                name: "Model".to_owned(),
                value_type: "matrix4x4".to_owned(),
                values: (0..16).map(|value| value as f32).collect(),
            }],
        )]),
    };
    let bytes = pack_uniform_block(&pass, "Transform").unwrap();
    assert_eq!(64, bytes.len());
    assert_eq!(&bytes[0..4], &0.0f32.to_le_bytes());
    assert_eq!(&bytes[60..64], &15.0f32.to_le_bytes());
}

#[test]
fn normalizes_multiple_static_uniform_blocks_to_distinct_vulkan_bindings() {
    let mut pass = VanillaPostEffectPass {
        vertex_shader: "fullscreen.vsh".to_owned(),
        fragment_shader: "fullscreen.fsh".to_owned(),
        inputs: vec![VanillaPostEffectInput {
            sampler_name: "In".to_owned(),
            target: "minecraft:main".to_owned(),
            texture_path: None,
            texture_width: None,
            texture_height: None,
            bilinear: true,
            use_depth_buffer: false,
        }],
        output: "minecraft:main".to_owned(),
        uniform_blocks: BTreeSet::from(["First".to_owned(), "Second".to_owned()]),
        uniform_values: BTreeMap::from([
            ("First".to_owned(), Vec::new()),
            ("Second".to_owned(), Vec::new()),
        ]),
    };
    let source = "#version 330\nin vec2 texCoord;\nuniform sampler2D InSampler;\nlayout(std140) uniform First { vec4 value; };\nlayout(std140) uniform Second { vec4 value; };\nout vec4 fragColor;";
    pass.fragment_shader = source.to_owned();
    let lowered =
        lower_post_effect_fragment_source(test_conventions(), source.as_bytes(), &pass)
            .unwrap();
    let lowered = String::from_utf8(lowered).unwrap();
    assert!(lowered.contains("layout(set = 0, binding = 1, std140) uniform First"));
    assert!(lowered.contains("layout(set = 0, binding = 2, std140) uniform Second"));
}

#[test]
fn uniform_packer_rejects_declared_type_and_value_count_mismatch() {
    let contract = VanillaPostEffectContract::parse(
        "invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    let mut invalid = contract.passes[0].clone();
    invalid.uniform_values.get_mut("InvertConfig").unwrap()[0]
        .values
        .push(0.25);
    let error = pack_uniform_block(&invalid, "InvertConfig").unwrap_err();
    assert!(error
        .to_string()
        .contains("declares float but supplies 2 values"));
}

#[test]
fn executor_owns_validated_plan_before_submission() {
    let contract = VanillaPostEffectContract::parse(
        "minecraft:invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    let executor = VanillaPostEffectExecutor::new(contract.execution_plan().clone()).unwrap();
    assert_eq!(executor.plan().effect_name, "minecraft:invert");
    assert_eq!(executor.plan().ordered_passes.len(), 2);
}

#[test]
fn bundled_entity_outline_executor_owns_the_complete_four_pass_graph() {
    let executor = bundled_entity_outline_executor().unwrap();
    assert_eq!("minecraft:entity_outline", executor.plan().effect_name);
    assert_eq!(4, executor.plan().ordered_passes.len());
    assert_eq!(
        "minecraft:entity_outline",
        executor.plan().ordered_passes[0].inputs[0].target
    );
    assert_eq!(
        "minecraft:entity_outline",
        executor.plan().ordered_passes[3].output
    );
}

#[test]
fn bundled_transparency_executor_owns_all_external_attachment_inputs() {
    let executor = bundled_transparency_executor().unwrap();
    assert_eq!("minecraft:transparency", executor.plan().effect_name);
    assert_eq!(2, executor.plan().ordered_passes.len());
    let first = &executor.plan().ordered_passes[0];
    assert_eq!("final", first.output);
    assert_eq!(12, first.inputs.len());
    assert!(first
        .inputs
        .iter()
        .any(|input| { input.target == "minecraft:weather" && input.use_depth_buffer }));
    assert_eq!("minecraft:main", executor.plan().ordered_passes[1].output);
    let sources = bundled_transparency_shader_sources().unwrap();
    assert_eq!(2, sources.len());
    assert!(sources.iter().all(|source| {
        !source.vertex_shader.is_empty() && !source.fragment_shader.is_empty()
    }));
    let vulkan_sources =
        bundled_transparency_lowered_shader_sources(test_conventions()).unwrap();
    assert_eq!(2, vulkan_sources.len());
    let first_vertex = String::from_utf8(vulkan_sources[0].0.clone()).unwrap();
    assert!(first_vertex.contains("#version 450"));
    assert!(first_vertex.contains("layout(location = 0) out vec2 texCoord;"));
    let first_fragment = String::from_utf8(vulkan_sources[0].1.clone()).unwrap();
    assert!(first_fragment.contains("#version 450"));
    assert!(
        first_fragment.contains("layout(set = 0, binding = 0) uniform sampler2D MainSampler;")
    );
    assert!(first_fragment.contains("layout(set = 0, binding = 11) uniform sampler2D"));
    let second_fragment = String::from_utf8(vulkan_sources[1].1.clone()).unwrap();
    assert!(
        second_fragment.contains("layout(set = 0, binding = 0) uniform sampler2D InSampler;")
    );
    assert!(second_fragment.contains("layout(set = 0, binding = 1, std140) uniform BlitConfig"));

    let inventory = FabulousExternalTargetInventory {
        main: VanillaPostEffectExternalTargetBinding {
            render_pass: handle(HandleKind::RenderPass, 1),
            render_target: handle(HandleKind::RenderTarget, 1),
            color_attachment: handle(HandleKind::TextureView, 1),
            depth_attachment: Some(handle(HandleKind::TextureView, 1)),
            sampler: handle(HandleKind::Sampler, 1),
            color_usage: TextureUsageState::ShaderRead,
            depth_usage: Some(TextureUsageState::ShaderRead),
        },
        translucent: VanillaPostEffectExternalTargetBinding {
            render_pass: handle(HandleKind::RenderPass, 2),
            render_target: handle(HandleKind::RenderTarget, 2),
            color_attachment: handle(HandleKind::TextureView, 2),
            depth_attachment: Some(handle(HandleKind::TextureView, 2)),
            sampler: handle(HandleKind::Sampler, 2),
            color_usage: TextureUsageState::ShaderRead,
            depth_usage: Some(TextureUsageState::ShaderRead),
        },
        item_entity: VanillaPostEffectExternalTargetBinding {
            render_pass: handle(HandleKind::RenderPass, 3),
            render_target: handle(HandleKind::RenderTarget, 3),
            color_attachment: handle(HandleKind::TextureView, 3),
            depth_attachment: Some(handle(HandleKind::TextureView, 3)),
            sampler: handle(HandleKind::Sampler, 3),
            color_usage: TextureUsageState::ShaderRead,
            depth_usage: Some(TextureUsageState::ShaderRead),
        },
        particles: VanillaPostEffectExternalTargetBinding {
            render_pass: handle(HandleKind::RenderPass, 4),
            render_target: handle(HandleKind::RenderTarget, 4),
            color_attachment: handle(HandleKind::TextureView, 4),
            depth_attachment: Some(handle(HandleKind::TextureView, 4)),
            sampler: handle(HandleKind::Sampler, 4),
            color_usage: TextureUsageState::ShaderRead,
            depth_usage: Some(TextureUsageState::ShaderRead),
        },
        clouds: VanillaPostEffectExternalTargetBinding {
            render_pass: handle(HandleKind::RenderPass, 5),
            render_target: handle(HandleKind::RenderTarget, 5),
            color_attachment: handle(HandleKind::TextureView, 5),
            depth_attachment: Some(handle(HandleKind::TextureView, 5)),
            sampler: handle(HandleKind::Sampler, 5),
            color_usage: TextureUsageState::ShaderRead,
            depth_usage: Some(TextureUsageState::ShaderRead),
        },
        weather: VanillaPostEffectExternalTargetBinding {
            render_pass: handle(HandleKind::RenderPass, 6),
            render_target: handle(HandleKind::RenderTarget, 6),
            color_attachment: handle(HandleKind::TextureView, 6),
            depth_attachment: Some(handle(HandleKind::TextureView, 6)),
            sampler: handle(HandleKind::Sampler, 6),
            color_usage: TextureUsageState::ShaderRead,
            depth_usage: Some(TextureUsageState::ShaderRead),
        },
    };
    let external = inventory.validate_against(executor.plan()).unwrap();
    assert!(external.get("minecraft:particles").is_some());
    let populated = BTreeSet::from([
        "minecraft:main".to_owned(),
        "minecraft:translucent".to_owned(),
        "minecraft:item_entity".to_owned(),
        "minecraft:particles".to_owned(),
        "minecraft:clouds".to_owned(),
        "minecraft:weather".to_owned(),
    ]);
    assert!(inventory
        .validate_populated_for_plan(executor.plan(), &populated)
        .is_ok());
    let missing_weather = populated
        .iter()
        .filter(|role| role.as_str() != "minecraft:weather")
        .cloned()
        .collect::<BTreeSet<_>>();
    let error = inventory
        .validate_populated_for_plan(executor.plan(), &missing_weather)
        .unwrap_err();
    assert!(error
        .to_string()
        .contains("unpopulated Rust external targets"));

    let error = FabulousExternalTargetInventory {
        main: inventory.main,
        translucent: VanillaPostEffectExternalTargetBinding {
            depth_attachment: None,
            depth_usage: None,
            ..inventory.translucent
        },
        item_entity: inventory.item_entity,
        particles: inventory.particles,
        clouds: inventory.clouds,
        weather: inventory.weather,
    }
    .validate_against(executor.plan())
    .unwrap_err();
    assert!(error.to_string().contains("depth attachment"));

    let error = FabulousExternalTargetInventory {
        particles: inventory.main,
        ..inventory
    }
    .validate_against(executor.plan())
    .unwrap_err();
    assert!(error.to_string().contains("must not alias"));
}

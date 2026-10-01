use std::collections::BTreeSet;

use crate::render::shaderpack::vanilla::post_effect::contract::VanillaPostEffectContract;

#[test]
fn bundled_effects_are_explicit_graphs() {
    let invert = VanillaPostEffectContract::parse(
        "invert",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/invert.json"),
    )
    .unwrap();
    assert_eq!(2, invert.passes.len());
    assert_eq!("minecraft:post/invert", invert.passes[0].fragment_shader);
    assert_eq!("minecraft:main", invert.passes[1].output);

    let spider = VanillaPostEffectContract::parse(
        "spider",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/spider.json"),
    )
    .unwrap();
    assert!(spider.passes.len() > 4);
    assert!(spider.targets.contains("large_blur"));
    assert!(spider
        .passes
        .iter()
        .any(|pass| pass.fragment_shader == "minecraft:post/spiderclip"));
    let plan = spider.execution_plan();
    assert_eq!(spider.passes, plan.ordered_passes);
    assert!(plan
        .intermediate_targets
        .contains(&"large_blur".to_string()));
    let sources = spider.shader_sources().unwrap();
    assert_eq!(spider.passes.len(), sources.len());
    assert!(sources
        .iter()
        .all(|source| !source.vertex_shader.is_empty() && !source.fragment_shader.is_empty()));

    let entity_outline = VanillaPostEffectContract::parse(
        "entity_outline",
        include_bytes!(
            "../../../../../../resources/assets/minecraft/post_effect/entity_outline.json"
        ),
    )
    .unwrap();
    assert_eq!(
        "minecraft:entity_outline",
        entity_outline.passes.last().unwrap().output
    );
    assert_eq!(
        entity_outline.passes.len(),
        entity_outline.shader_sources().unwrap().len()
    );

    let transparency = VanillaPostEffectContract::parse(
        "transparency",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )
    .unwrap();
    assert_eq!(
        transparency.passes.len(),
        transparency.shader_sources().unwrap().len()
    );
    assert_eq!(
        transparency.execution_plan().required_external_targets(),
        BTreeSet::from([
            "minecraft:clouds".to_owned(),
            "minecraft:item_entity".to_owned(),
            "minecraft:main".to_owned(),
            "minecraft:particles".to_owned(),
            "minecraft:translucent".to_owned(),
            "minecraft:weather".to_owned(),
        ])
    );
    let mut incomplete = BTreeSet::from([
        "minecraft:main".to_owned(),
        "minecraft:translucent".to_owned(),
    ]);
    let error = transparency
        .execution_plan()
        .validate_external_targets(&incomplete)
        .unwrap_err();
    assert!(error.to_string().contains("minecraft:clouds"));
    incomplete.extend([
        "minecraft:clouds".to_owned(),
        "minecraft:item_entity".to_owned(),
        "minecraft:particles".to_owned(),
        "minecraft:weather".to_owned(),
    ]);
    transparency
        .execution_plan()
        .validate_external_targets(&incomplete)
        .unwrap();
    incomplete.insert("minecraft:undeclared".to_owned());
    let extra_error = transparency
        .execution_plan()
        .validate_external_targets(&incomplete)
        .unwrap_err();
    assert!(extra_error.to_string().contains("minecraft:undeclared"));

    for (name, bytes) in [
        (
            "blur",
            include_bytes!("../../../../../../resources/assets/minecraft/post_effect/blur.json")
                .as_slice(),
        ),
        (
            "creeper",
            include_bytes!("../../../../../../resources/assets/minecraft/post_effect/creeper.json")
                .as_slice(),
        ),
    ] {
        let effect = VanillaPostEffectContract::parse(name, bytes).unwrap();
        assert_eq!(effect.passes.len(), effect.shader_sources().unwrap().len());
    }
}

#[test]
fn undeclared_target_is_rejected_before_resource_creation() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader": "minecraft:core/screenquad",
                "fragment_shader": "minecraft:post/blit",
                "inputs": [{"sampler_name": "In", "target": "missing"}],
                "output": "minecraft:main"
            }]
        }"#;
    let error = VanillaPostEffectContract::parse("bad", json).unwrap_err();
    assert!(error.to_string().contains("undeclared target missing"));
}

#[test]
fn forward_intermediate_reads_are_rejected() {
    let json = br#"{
            "targets": {"later": {}},
            "passes": [
                {"vertex_shader":"v", "fragment_shader":"f", "inputs":[{"sampler_name":"In", "target":"later"}], "output":"minecraft:main"}
            ]
        }"#;
    let error = VanillaPostEffectContract::parse("forward", json).unwrap_err();
    assert!(error.to_string().contains("before it is produced"));
}

#[test]
fn required_shader_uniform_abi_is_rejected_when_missing() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader":"minecraft:core/screenquad",
                "fragment_shader":"minecraft:post/invert",
                "inputs":[{"sampler_name":"In", "target":"minecraft:main"}],
                "output":"minecraft:main",
                "uniforms": {}
            }]
        }"#;
    let error = VanillaPostEffectContract::parse("missing-uniform", json).unwrap_err();
    assert!(error.to_string().contains("InvertConfig"));
}

#[test]
fn duplicate_uniform_names_are_rejected_before_execution() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader":"minecraft:core/screenquad",
                "fragment_shader":"minecraft:post/invert",
                "inputs":[{"sampler_name":"In", "target":"minecraft:main"}],
                "output":"minecraft:main",
                "uniforms":{"InvertConfig":[
                    {"name":"InverseAmount","type":"float","value":0.5},
                    {"name":"InverseAmount","type":"float","value":0.8}
                ]}
            }]
        }"#;
    let error = VanillaPostEffectContract::parse("duplicate", json).unwrap_err();
    assert!(error.to_string().contains("repeats uniform InverseAmount"));
}

#[test]
fn integer_uniform_vectors_are_copied_with_bounded_integral_values() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader":"v",
                "fragment_shader":"f",
                "inputs":[{"sampler_name":"In", "target":"minecraft:main"}],
                "output":"minecraft:main",
                "uniforms":{"Config":[
                    {"name":"Mode","type":"int","value":3},
                    {"name":"Mask","type":"ivec3","value":[1,2,4]}
                ]}
            }]
        }"#;
    let contract = VanillaPostEffectContract::parse("integer-uniforms", json).unwrap();
    let values = &contract.passes[0].uniform_values["Config"];
    assert_eq!(values[0].value_type, "int");
    assert_eq!(values[1].values, vec![1.0, 2.0, 4.0]);
}

#[test]
fn fractional_integer_uniforms_are_rejected() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader":"v", "fragment_shader":"f",
                "inputs":[{"sampler_name":"In", "target":"minecraft:main"}],
                "output":"minecraft:main",
                "uniforms":{"Config":[{"name":"Mode","type":"int","value":1.5}]}
            }]
        }"#;
    let error = VanillaPostEffectContract::parse("fractional-integer", json).unwrap_err();
    assert!(error.to_string().contains("32-bit integral values"));
}

#[test]
fn matrix4x4_uniforms_require_sixteen_finite_values() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader":"v", "fragment_shader":"f",
                "inputs":[{"sampler_name":"In", "target":"minecraft:main"}],
                "output":"minecraft:main",
                "uniforms":{"Transform":[{"name":"Model","type":"matrix4x4","value":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]}]}
            }]
        }"#;
    let contract = VanillaPostEffectContract::parse("matrix-uniform", json).unwrap();
    let uniform = &contract.passes[0].uniform_values["Transform"][0];
    assert_eq!(uniform.value_type, "matrix4x4");
    assert_eq!(uniform.values.len(), 16);
}

#[test]
fn input_sampling_semantics_are_retained_for_multi_target_effects() {
    let transparency = VanillaPostEffectContract::parse(
        "transparency",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/transparency.json"),
    )
    .unwrap();
    let depth_input = transparency.passes[0]
        .inputs
        .iter()
        .find(|input| input.sampler_name == "MainDepth")
        .unwrap();
    assert_eq!("minecraft:main", depth_input.target);
    assert!(depth_input.use_depth_buffer);
    assert!(!depth_input.bilinear);

    let blur = VanillaPostEffectContract::parse(
        "blur",
        include_bytes!("../../../../../../resources/assets/minecraft/post_effect/blur.json"),
    )
    .unwrap();
    let blur_config = blur.passes[0].uniform_values.get("BlurConfig").unwrap();
    assert_eq!(blur_config[0].name, "BlurDir");
    assert_eq!(blur_config[0].values, vec![1.0, 0.0]);
    assert_eq!(blur_config[1].value_type, "float");
    assert_eq!(blur_config[1].values, vec![0.0]);
}

#[test]
fn texture_inputs_are_copied_as_explicit_unadmitted_assets() {
    let json = br#"{
            "targets": {},
            "passes": [{
                "vertex_shader":"v",
                "fragment_shader":"f",
                "inputs":[{"sampler_name":"Mask","location":"minecraft:textures/effect/mask.png","width":64,"height":32,"bilinear":true}],
                "output":"minecraft:main"
            }]
        }"#;
    let contract = VanillaPostEffectContract::parse("texture-input", json).unwrap();
    let input = &contract.passes[0].inputs[0];
    assert!(input.target.is_empty());
    assert_eq!(
        input.texture_path.as_deref(),
        Some("minecraft:textures/effect/mask.png")
    );
    assert_eq!(input.texture_width, Some(64));
    assert_eq!(input.texture_height, Some(32));
    assert_eq!(
        contract.execution_plan().required_external_targets(),
        BTreeSet::from(["minecraft:main".to_owned()])
    );
}

#[test]
fn copied_shader_pack_sources_override_bundled_post_effect_stages() {
    let contract = VanillaPostEffectContract::parse(
        "custom_effect",
        br#"{
                "targets": {},
                "passes": [{
                    "vertex_shader": "minecraft:post/custom",
                    "fragment_shader": "minecraft:post/custom",
                    "output": "minecraft:main"
                }]
            }"#,
    )
    .unwrap();
    let source = crate::render::shaderpack::source::ShaderPackSource::new(
        "custom-pack",
        1,
        vec![
            crate::render::shaderpack::source::ShaderSourceFile::new(
                "post/custom.vsh",
                "#version 450\nvoid main(){}",
            ),
            crate::render::shaderpack::source::ShaderSourceFile::new(
                "post/custom.fsh",
                "#version 450\nvoid main(){}",
            ),
        ],
    )
    .unwrap();
    let stages = contract.shader_sources_from_source(&source).unwrap();
    assert_eq!(
        b"#version 450\nvoid main(){}",
        stages[0].vertex_shader.as_slice()
    );
    assert_eq!(
        b"#version 450\nvoid main(){}",
        stages[0].fragment_shader.as_slice()
    );
}

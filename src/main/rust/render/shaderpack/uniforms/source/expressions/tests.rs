use super::*;
use crate::render::shaderpack::properties::custom_uniforms::definitions_from_preprocessed;

fn evaluate(
    properties: &str,
    name: &str,
    ty: TerrainSourceUniformType,
    frame: &TerrainSourceUniformFrame,
) -> GalResult<Value> {
    let definitions = definitions_from_preprocessed(properties)?;
    let program = Program::compile(&definitions, &[(name, ty)])?;
    let values = program.evaluate(frame)?;
    Ok(values[program.root(name).unwrap() as usize].unwrap())
}

#[test]
fn custom_expressions_link_dependencies_and_evaluate_original_time_formulas() {
    let properties = concat!(
        "variable.float.hour_world = worldTime * 0.001\n",
        "uniform.float.dayMoment = hour_world * 0.04166666666666667\n",
        "variable.float.moment_aux = dayMoment - 0.25\n",
        "variable.float.moment_aux_2 = moment_aux * moment_aux\n",
        "uniform.float.dayMixer = clamp(-moment_aux_2 * 20.0 + 1.25, 0.0, 1.0)\n",
        "uniform.float.unused = unsupportedGpuFunction(textureHandle)\n",
    );
    for (time, expected) in [(0, 0.0), (6000, 1.0), (12000, 0.0)] {
        let frame = TerrainSourceUniformFrame {
            world_time: Some(time),
            ..Default::default()
        };
        assert_eq!(
            Value::Float(expected),
            evaluate(
                properties,
                "dayMixer",
                TerrainSourceUniformType::Float,
                &frame
            )
            .unwrap()
        );
    }
}

#[test]
fn builtin_frame_inputs_feed_custom_expressions_without_untyped_uniform_data() {
    let frame = TerrainSourceUniformFrame {
        eye_brightness_smooth: Some([119, 121]),
        sun_position: Some([10.0, 20.0, 30.0]),
        ..Default::default()
    };
    assert_eq!(
        Value::Float(90.5),
        evaluate(
            "uniform.float.value=eyeBrightnessSmooth.y*0.5+sunPosition.z",
            "value",
            TerrainSourceUniformType::Float,
            &frame
        )
        .unwrap()
    );
}

#[test]
fn custom_expressions_use_multiline_frame_choices_and_typed_vectors() {
    let properties = concat!(
        "uniform.int.frameMod = fmod(frameCounter, 16)\n",
        "uniform.float.pixelSizeX = 1.0 / viewWidth\n",
        "uniform.float.pixelSizeY = 1.0 / viewHeight\n",
        "uniform.vec2.offset = vec2(if(frameMod == 0, 0.5, \\\n",
        "frameMod == 1, -0.5, 0.0) * pixelSizeX, -0.5 * pixelSizeY)\n",
    );
    let frame = TerrainSourceUniformFrame {
        frame_counter: Some(17),
        viewport_width: Some(1280.0),
        viewport_height: Some(720.0),
        ..Default::default()
    };
    assert_eq!(
        Value::Vector([-0.5 / 1280.0, -0.5 / 720.0, 0.0, 0.0], 2),
        evaluate(properties, "offset", TerrainSourceUniformType::Vec2, &frame).unwrap()
    );
}

#[test]
fn custom_expressions_follow_frozen_precedence_division_and_floor_mod() {
    let frame = TerrainSourceUniformFrame::default();
    assert_eq!(
        Value::Bool(false),
        evaluate(
            "uniform.bool.choice=1==1 || 1==0 && 1==0",
            "choice",
            TerrainSourceUniformType::Bool,
            &frame
        )
        .unwrap()
    );
    assert_eq!(
        Value::Float(0.5),
        evaluate(
            "uniform.float.ratio=1/2",
            "ratio",
            TerrainSourceUniformType::Float,
            &frame
        )
        .unwrap()
    );
    assert_eq!(
        Value::Int(15),
        evaluate(
            "uniform.int.remainder=fmod(-17,16)",
            "remainder",
            TerrainSourceUniformType::Int,
            &frame
        )
        .unwrap()
    );
    assert_eq!(
        Value::Float(15.0),
        evaluate(
            "uniform.float.remainder=fmod(-17.0,16.0)",
            "remainder",
            TerrainSourceUniformType::Float,
            &frame
        )
        .unwrap()
    );
    assert_eq!(
        Value::Int(-1),
        evaluate(
            "uniform.int.remainder=-17%16",
            "remainder",
            TerrainSourceUniformType::Int,
            &frame
        )
        .unwrap()
    );
}

#[test]
fn custom_expressions_select_lazy_values_and_read_column_major_inputs() {
    let frame = TerrainSourceUniformFrame {
        projection_matrix: Some(std::array::from_fn(|index| index as f32)),
        ..Default::default()
    };
    assert_eq!(
        Value::Float(9.0),
        evaluate(
            "uniform.float.entry=gbufferProjection.2.1",
            "entry",
            TerrainSourceUniformType::Float,
            &frame
        )
        .unwrap()
    );
    assert_eq!(
        Value::Float(2.0),
        evaluate(
            "uniform.float.value=if(1==1,2.0,viewWidth)",
            "value",
            TerrainSourceUniformType::Float,
            &frame
        )
        .unwrap()
    );
    assert!(evaluate(
        "uniform.float.value=viewWidth",
        "value",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
    assert!(evaluate(
        "uniform.float.entry=gbufferProjection.4.1",
        "entry",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
}

#[test]
fn custom_expressions_reject_cycles_missing_inputs_types_and_invalid_math() {
    let frame = TerrainSourceUniformFrame::default();
    for properties in [
        "uniform.float.value=other\nvariable.float.other=value",
        "uniform.float.value=undefinedInput",
        "uniform.float.value=texture(viewWidth,0)",
        "uniform.float.value=if(1,2,3)",
        "uniform.float.value=1.0/0.0",
        "uniform.float.value=sqrt(-1.0)",
        "uniform.float.value=vec2(1.0,2.0)",
        "uniform.float.value=if(1==1,1.0,1==0)",
        "uniform.float.value=1.0 trailing",
    ] {
        assert!(
            evaluate(properties, "value", TerrainSourceUniformType::Float, &frame).is_err(),
            "{properties}"
        );
    }
    assert!(evaluate(
        "uniform.int.value=1",
        "value",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
}

#[test]
fn custom_expression_syntax_and_dependency_work_are_bounded() {
    let frame = TerrainSourceUniformFrame::default();
    let properties = format!(
        "uniform.float.value={}1{}",
        "(".repeat(1000),
        ")".repeat(1000)
    );
    assert!(evaluate(
        &properties,
        "value",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
    let mut properties = "uniform.float.value=next0\n".to_string();
    for index in 0..150 {
        properties.push_str(&format!("variable.float.next{index}=next{}\n", index + 1));
    }
    properties.push_str("variable.float.next150=1.0\n");
    assert!(evaluate(
        &properties,
        "value",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
    assert!(definitions_from_preprocessed("uniform.float.value=1.0\\").is_err());
}

#[test]
fn custom_expressions_match_frozen_numeric_overloads_and_explicit_casts() {
    // Established by FrozenCustomExpressionProbe using Frozen's compiled
    // IrisFunctions/ExpressionResolver rather than a reimplementation.
    let frame = TerrainSourceUniformFrame::default();
    for (expression, expected) in [
        ("2147483647+1", -2147483648.0),
        ("(2147483647+1)*1.0", -2147483648.0),
        ("2147483647+(1*1.0)", 2147483648.0),
        ("if(1==1,2147483647+1,0.0)", -2147483648.0),
        ("min(2147483647+1,0.0)", -2147483648.0),
        ("2147483648", 2147483648.0),
        ("clamp(0.0,2.0,1.0)", 2.0),
        ("floor(1.5)+2147483647", -2147483648.0),
        ("floor(1.5)+2147483647.0", 2147483648.0),
        ("log(2.0,8.0)", 3.0),
    ] {
        assert_eq!(
            Value::Float(expected),
            evaluate(
                &format!("uniform.float.value={expression}"),
                "value",
                TerrainSourceUniformType::Float,
                &frame
            )
            .unwrap(),
            "{expression}"
        );
    }
    for (expression, expected) in [
        ("toInt(1.5)", 1),
        ("toInt(1)", 1),
        ("010", 8),
        ("0x20", 32),
        ("0b10", 2),
        ("floor(1.5)+1", 2),
        ("ceil(1.5)+1", 3),
    ] {
        assert_eq!(
            Value::Int(expected),
            evaluate(
                &format!("uniform.int.value={expression}"),
                "value",
                TerrainSourceUniformType::Int,
                &frame
            )
            .unwrap()
        );
    }
    for expression in ["1.5", "1/2", "08", "int(1.5)"] {
        assert!(evaluate(
            &format!("uniform.int.value={expression}"),
            "value",
            TerrainSourceUniformType::Int,
            &frame
        )
        .is_err());
    }
    assert!(evaluate(
        "uniform.float.value=toFloat(1.5)",
        "value",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
    assert_eq!(
        Value::Float(2.5),
        evaluate(
            "uniform.float.value=2.5f",
            "value",
            TerrainSourceUniformType::Float,
            &frame
        )
        .unwrap()
    );
    assert!(evaluate(
        "uniform.float.value=1e-3",
        "value",
        TerrainSourceUniformType::Float,
        &frame
    )
    .is_err());
    for expression in ["true", "false"] {
        assert!(evaluate(
            &format!("uniform.bool.value={expression}"),
            "value",
            TerrainSourceUniformType::Bool,
            &frame
        )
        .is_err());
    }
}

//! Custom properties through the paired source lowerer and actual std140 ABI.

use super::*;
use crate::render::shaderpack::source::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedShaderSource,
};

fn artifacts(
    properties: &str,
    option: &str,
) -> (PreprocessedShaderSource, PreprocessedShaderSource) {
    let source = ShaderPackSource::new("custom-property-integration", 4, vec![
        ShaderSourceFile::new("gbuffers_terrain.vsh", concat!(
            "#version 120\n#define AA_MODE 1 // [0 1]\n",
            "uniform vec2 jitter;\nvarying vec2 uv;\n",
            "void main() { gl_Position = ftransform(); gl_Position.xy += jitter; uv = gl_MultiTexCoord0.xy; }\n",
        )),
        ShaderSourceFile::new("gbuffers_terrain.fsh", concat!(
            "#version 120\n#define AA_MODE 1 // [0 1]\n",
            "uniform sampler2D tex;\nuniform float dayMixer;\nuniform int frameMod;\nuniform bool enabled;\n",
            "varying vec2 uv;\n/* DRAWBUFFERS:1 */\n",
            "void main() { vec4 pixel = texture2D(tex, uv); if (enabled) pixel.rgb *= dayMixer + float(frameMod); gl_FragData[0] = pixel; }\n",
        )),
        ShaderSourceFile::new("shaders.properties", properties),
        ShaderSourceFile::new(crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH, format!("AA_MODE={option}\n")),
    ]).unwrap();
    (
        preprocess_artifact_with_runtime_options(&source, "gbuffers_terrain.vsh", &[]).unwrap(),
        preprocess_artifact_with_runtime_options(&source, "gbuffers_terrain.fsh", &[]).unwrap(),
    )
}

const PROPERTIES: &str = concat!(
    "variable.float.hour_world=worldTime*0.001\n",
    "variable.float.dayMoment=hour_world*0.04166666666666667\n",
    "variable.float.moment_aux=dayMoment-0.25\n",
    "uniform.float.dayMixer=clamp(-moment_aux*moment_aux*20.0+1.25,0.0,1.0)\n",
    "uniform.int.frameMod=fmod(frameCounter,16)\n",
    "uniform.bool.enabled=frameMod>=0\n",
    "#if AA_MODE == 1\n",
    "uniform.vec2.jitter=vec2(-0.5/viewWidth, \\\n",
    "0.5/viewHeight)\n",
    "#else\n",
    "uniform.vec2.jitter=vec2(0.0,0.0)\n",
    "#endif\n",
    "uniform.float.unused=unsupportedGpuFunction(textureHandle)\n",
);

fn requirements(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceUniformRequirements> {
    let lowered = lower_terrain_source_pair(vertex, fragment)?;
    TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract())
}

fn frame() -> TerrainSourceUniformFrame {
    TerrainSourceUniformFrame {
        world_time: Some(6000),
        frame_counter: Some(17),
        viewport_width: Some(1280.0),
        viewport_height: Some(720.0),
        view_matrix: Some([1.0; 16]),
        projection_matrix: Some([1.0; 16]),
        ..Default::default()
    }
}

fn offset(requirements: &TerrainSourceUniformRequirements, name: &str) -> usize {
    requirements
        .fields()
        .iter()
        .find(|field| field.field.name() == name)
        .unwrap()
        .field
        .offset() as usize
}

#[test]
fn custom_expressions_pack_typed_active_properties_into_std140() {
    let (vertex, fragment) = artifacts(PROPERTIES, "1");
    let requirements = requirements(&vertex, &fragment).unwrap();
    assert!(requirements.is_fully_semantic());
    let bytes = frame().pack_std140(&requirements).unwrap();
    for (name, expected) in [("dayMixer", 1.0_f32), ("jitter", -0.5_f32 / 1280.0)] {
        let offset = offset(&requirements, name);
        assert_eq!(expected.to_le_bytes(), bytes[offset..offset + 4], "{name}");
    }
    let jitter = offset(&requirements, "jitter");
    assert_eq!(
        (0.5_f32 / 720.0).to_le_bytes(),
        bytes[jitter + 4..jitter + 8]
    );
    for name in ["frameMod", "enabled"] {
        let offset = offset(&requirements, name);
        assert_eq!(1_i32.to_le_bytes(), bytes[offset..offset + 4]);
    }
    // Verify that typed custom values retain a native-compilable shared UBO
    // and varying ABI in both configured branches.
    use crate::render::shaderpack::programs::{ShaderStageKind, ShaderStageSource};
    let mut gal =
        crate::render::vulkanic::test_support::vulkan_gal("custom-property-uniforms").unwrap();
    let conventions = gal.capabilities().shader_conventions;
    for option in ["0", "1"] {
        let (vertex, fragment) = artifacts(PROPERTIES, option);
        let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
        lowered.require_backend_neutral_lowering().unwrap();
        for (stage, source) in [
            (ShaderStageKind::Vertex, lowered.vertex().source()),
            (ShaderStageKind::Fragment, lowered.fragment().source()),
        ] {
            let module = gal
                .create_shader_module(
                    ShaderStageSource {
                        stage,
                        label: format!("custom-property-{option}-{stage:?}"),
                        source: source.to_owned(),
                        entry_point: "main".into(),
                    }
                    .shader_module_descriptor(conventions),
                )
                .unwrap();
            gal.destroy(module).unwrap();
        }
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
    assert!(TerrainSourceUniformFrame {
        viewport_width: None,
        ..frame()
    }
    .pack_std140(&requirements)
    .is_err());
}

#[test]
fn custom_expressions_follow_selected_options_and_invalidate_source_identity() {
    let (vertex, fragment) = artifacts(PROPERTIES, "0");
    let disabled = requirements(&vertex, &fragment).unwrap();
    // The inactive TAA expression must not require absent viewport inputs.
    let bytes = TerrainSourceUniformFrame {
        viewport_width: None,
        viewport_height: None,
        ..frame()
    }
    .pack_std140(&disabled)
    .unwrap();
    let jitter = offset(&disabled, "jitter");
    assert_eq!([0; 8], bytes[jitter..jitter + 8]);
    let changed = PROPERTIES.replace("-0.5/viewWidth", "-0.25/viewWidth");
    let (original, _) = artifacts(PROPERTIES, "1");
    let (changed, _) = artifacts(&changed, "1");
    assert_eq!(original.expanded_source(), changed.expanded_source());
    assert_ne!(original.fingerprint(), changed.fingerprint());
    let rewritten = original
        .rewritten_for_lowering(original.expanded_source().to_owned())
        .unwrap();
    assert_eq!(original.custom_uniforms(), rewritten.custom_uniforms());
    assert_eq!(original.fingerprint(), rewritten.fingerprint());
}

#[test]
fn custom_expressions_reject_conflicting_stage_properties_and_types() {
    let (vertex, fragment) = artifacts(PROPERTIES, "1");
    let (_, changed) = artifacts(
        &PROPERTIES.replace("uniform.int.frameMod", "uniform.float.frameMod"),
        "1",
    );
    assert!(lower_terrain_source_pair(&vertex, &changed).is_err());
    let (wrong_vertex, wrong_fragment) = artifacts(
        &PROPERTIES.replace("uniform.int.frameMod", "uniform.float.frameMod"),
        "1",
    );
    assert!(requirements(&wrong_vertex, &wrong_fragment).is_err());
    let (cycle_vertex, cycle_fragment) = artifacts(
        &PROPERTIES.replace("hour_world*0.04166666666666667", "dayMixer"),
        "1",
    );
    assert!(requirements(&cycle_vertex, &cycle_fragment).is_err());
    assert!(requirements(&vertex, &fragment).is_ok());
}

use super::*;

#[test]
fn fullscreen_final_default_output_is_explicit_and_does_not_relax_other_passes() {
    fn lower(
        entry: &str,
        fragment: &str,
    ) -> crate::render::vulkanic::error::GalResult<LoweredFullscreenSourcePair> {
        let source = ShaderPackSource::new(
            "final-default",
            1,
            vec![
                ShaderSourceFile::new(
                    "final.vsh",
                    "#version 130\nvoid main() { gl_Position = ftransform(); }\n",
                ),
                ShaderSourceFile::new(entry, fragment),
                ShaderSourceFile::new(
                    crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH,
                    "colortex0=shader_pack_color:primary\n",
                ),
            ],
        )
        .unwrap();
        let vertex = preprocess_artifact(PreprocessInput {
            source: &source,
            entry: "final.vsh",
            defines: &[],
        })
        .unwrap();
        let fragment = preprocess_artifact(PreprocessInput {
            source: &source,
            entry,
            defines: &[],
        })
        .unwrap();
        let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
        lower_fullscreen_source_pair(&vertex, &fragment, &bindings)
    }
    let fragment = "#version 130\nvoid main() { gl_FragData[0] = vec4(1.0); }\n";
    for entry in ["final.fsh", "world0/final.fsh"] {
        let lowered = lower(entry, fragment).unwrap();
        assert_eq!(1, lowered.fragment().outputs().len());
        assert_eq!(0, lowered.fragment().outputs()[0].source_slot());
        assert_eq!(
            TerrainSourceResourceRole::ShaderPackColor("primary".into()),
            lowered.fragment().outputs()[0].role()
        );
    }
    assert!(lower("world0/deferred.fsh", fragment)
        .unwrap_err()
        .to_string()
        .contains("missing a DRAWBUFFERS"));
    assert!(lower("final.fsh", &format!("/* DRAWBUFFERS: */\n{fragment}")).is_err());
    assert!(lower("final.fsh", &fragment.replace("[0]", "[1]")).is_err());
}

#[test]
fn legacy_sampler_declaration_comments_preserve_active_bindings_and_scalar_requirements() {
    let vertex = artifact("#version 130\nvoid main() { gl_Position = vec4(0.0); }\n");
    let fragment = artifact(concat!(
        "#version 130\nuniform float frameTime; // frame duration\n",
        "uniform/* texture annotation */sampler2D colortex3; // TAA history\n",
        "uniform sampler2D unused; // not sampled\n",
        "/* uniform sampler2D fake; */\n",
        "/* const bool colortex3MipmapEnabled = true; */\n",
        "void main() { gl_FragData[0] = texture2D(colortex3, vec2(frameTime)); }\n",
    ));
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert_eq!(
        vec!["frameTime"],
        lowered
            .uniform_contract()
            .fields()
            .iter()
            .map(|field| field.name())
            .collect::<Vec<_>>()
    );
    let resources = lowered.opaque_resource_contract();
    assert!(resources.resource_for("colortex3").unwrap().active());
    assert!(!resources.resource_for("unused").unwrap().active());
    assert!(resources.resource_for("fake").is_none());
    assert!(lowered
        .fragment()
        .source()
        .contains("uniform sampler2D colortex3;"));
    assert!(lowered
        .fragment()
        .source()
        .contains("layout(set = 1, binding = 0)"));
    assert!(lowered
        .fragment()
        .source()
        .contains("/* const bool colortex3MipmapEnabled = true; */"));
}

#[test]
fn legacy_sampler_varying_comments_and_matrix_locations_preserve_the_paired_interface() {
    let vertex = artifact(concat!(
        "#version 130\n/* varying vec4 fake; */\n",
        "varying float a_exposure; // Flat\n",
        "varying/* annotation */mat4 b_matrix;\n",
        "/* multiline π interface annotation\nends here */ varying vec2 z_uv;\n",
        "void main() { a_exposure = 1.0; b_matrix = mat4(1.0);\n",
        "z_uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }\n",
    ));
    let fragment = artifact(concat!(
        "#version 130\nvarying float a_exposure;\n",
        "varying mat4 b_matrix; // inverse view projection\n",
        "varying vec2 z_uv;\n",
        "void main() { gl_FragData[0] = vec4(z_uv, b_matrix[0][0], a_exposure); }\n",
    ));
    let contract = derive_terrain_source_varying_contract(&vertex, &fragment).unwrap();
    assert_eq!(
        vec![("a_exposure", 0), ("b_matrix", 1), ("z_uv", 5)],
        contract
            .fields()
            .iter()
            .map(|field| (field.name(), field.location()))
            .collect::<Vec<_>>()
    );
    let linked_vertex = apply_varying_locations(
        &replace_identifier(vertex.expanded_source(), "varying", "out"),
        VaryingStorage::Out,
        &contract,
    )
    .unwrap();
    let linked_fragment = apply_varying_locations(
        &replace_identifier(fragment.expanded_source(), "varying", "in"),
        VaryingStorage::In,
        &contract,
    )
    .unwrap();
    assert!(linked_vertex.contains("layout(location = 1) out mat4 b_matrix;"));
    assert!(linked_vertex.contains("layout(location = 5) out vec2 z_uv;"));
    assert!(linked_fragment.contains("layout(location = 5) in vec2 z_uv;"));
    let mismatch = artifact("#version 130\nvarying mat3 b_matrix;\nvoid main() { gl_FragData[0] = vec4(b_matrix[0], 1.0); }\n");
    assert!(derive_terrain_source_varying_contract(&vertex, &mismatch)
        .unwrap_err()
        .to_string()
        .contains("differs between"));
}

#[test]
fn legacy_sampler_compatibility_links_fog_and_implicit_combined_matrix_on_native_device() {
    let vertex = artifact(concat!(
        "#version 130\nvarying vec2 uv;\nvoid main () {\n",
        "uv=gl_MultiTexCoord0.xy; gl_Position=gl_ModelViewProjectionMatrix * gl_Vertex;\n",
        "gl_FogFragCoord=length((gl_ModelViewMatrix * gl_Vertex).xyz);\n}\n",
    ));
    let fragment = artifact(concat!(
        "#version 130\nvarying vec2 uv;\nvoid main() {\n",
        "gl_FragData[0]=vec4(uv, gl_FogFragCoord, 1.0);\n}\n",
    ));
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    let requirements = crate::render::shaderpack::uniforms::source::TerrainSourceUniformRequirements::from_contract(lowered.uniform_contract()).unwrap();
    assert_eq!(0, requirements.unresolved_fields().count());
    assert!(lowered
        .vertex()
        .source()
        .contains("(gbufferProjection * vulkanic_source_model_view)"));
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_fog_frag_coord=length("));
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_fog_frag_coord = 0.0;"));
    assert!(!lowered.fragment().source().contains("gl_FogFragCoord"));
    let fog = lowered
        .varying_contract()
        .fields()
        .iter()
        .find(|field| field.name() == "vulkanic_source_fog_frag_coord")
        .unwrap();
    assert!(lowered
        .vertex()
        .source()
        .contains(&format!("layout(location = {}) out float", fog.location())));
    assert!(lowered
        .fragment()
        .source()
        .contains(&format!("layout(location = {}) in float", fog.location())));
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("legacy-inputs").unwrap();
    for (stage, source) in [
        (
            crate::render::vulkanic::resources::ShaderStage::Vertex,
            lowered.vertex().source(),
        ),
        (
            crate::render::vulkanic::resources::ShaderStage::Fragment,
            lowered.fragment().source(),
        ),
    ] {
        let module = gal
            .create_shader_module(crate::render::vulkanic::resources::ShaderModuleDesc {
                label: "legacy-inputs".to_owned(),
                stage,
                entry_point: "main".to_owned(),
                code_format: crate::render::vulkanic::resources::ShaderCodeFormat::Glsl,
                code: source.as_bytes().to_vec(),
            })
            .unwrap();
        gal.destroy(module).unwrap();
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn legacy_sampler_fog_without_pack_vertex_write_matches_frozen_zero_default() {
    let vertex = artifact(
        "#version 130\nvoid main(){gl_Position=gl_ModelViewProjectionMatrix * gl_Vertex;}\n",
    );
    let fragment = artifact("#version 130\nvoid main(){gl_FragData[0]=vec4(gl_FogFragCoord);}\n");
    let lowered = lower_terrain_source_pair(&vertex, &fragment).unwrap();
    assert!(lowered
        .vertex()
        .source()
        .contains("vulkanic_source_fog_frag_coord = 0.0;"));
    assert_eq!(1, lowered.varying_contract().fields().len());
}

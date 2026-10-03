//! Ordinary and cutout source-alpha policy must match Frozen's terrain passes.
use super::*;
use crate::render::shaderpack::contracts::terrain::derive_terrain_contract_for_scope;

fn source(properties: &str) -> ShaderPackSource {
    ShaderPackSource::new("terrain-alpha", 3,vec![
        ShaderSourceFile::new("world0/gbuffers_terrain.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/gbuffers_terrain.fsh", concat!(
            "#version 130\n#define CUTOUT_FIXTURE 1\n/* DRAWBUFFERS:1 */\n",
            "// void main() { bogus comment }\n",
            "void main /* signature trivia */ (void) { gl_FragData[0]=vec4(1.0,0.0,0.0,0.1); return; }\n")),
        ShaderSourceFile::new("shaders.properties",properties),
        ShaderSourceFile::new("block.properties",""),
    ]).unwrap()
}

#[test]
fn normal_terrain_alpha_defaults_overrides_and_selected_options_compile_natively() {
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("terrain-alpha-test").unwrap();
    for (properties,opaque_cutoff,cutout_cutoff) in [
        ("",None,Some(0.1)),
        ("alphaTest.gbuffers_terrain=off\n",None,None),
        ("alphaTest.gbuffers_terrain=false\n",None,None),
        ("alphaTest.gbuffers_terrain=GREATER 0.25\n",Some(0.25),Some(0.25)),
        ("#ifdef CUTOUT_FIXTURE\nalphaTest.gbuffers_terrain=GREATER 0.2\n#else\nalphaTest.gbuffers_terrain=off\n#endif\n",Some(0.2),Some(0.2)),
    ] {
        let source=source(properties);
        let contract=derive_terrain_contract_for_scope(&source,TerrainProgramScope::Overworld).unwrap();
        assert_eq!(opaque_cutoff,contract.normal_alpha_test.cutoff(TerrainMaterialClass::Opaque));
        assert_eq!(cutout_cutoff,contract.normal_alpha_test.cutoff(TerrainMaterialClass::Cutout));
        let stages=preprocess_terrain_sources(&source,&contract.source_stages().unwrap()).unwrap();
        let lowered=lower_terrain_source_pair(&stages.vertex,&stages.fragment).unwrap();
        let declarations=TerrainSourceResourceBindings::from_source(&source).unwrap();
        let bindings=lowered.opaque_resource_contract().bind_semantic_roles(&declarations).unwrap();
        for (kind,cutoff) in [(TerrainMaterialProgramKind::Opaque,opaque_cutoff),(TerrainMaterialProgramKind::Cutout,cutout_cutoff)] {
            let program=prepare_lowered_terrain_source_program(&contract,&lowered,&bindings,kind).unwrap();
            assert_eq!(Some(&[(TerrainPassOutput::LitTerrainColor,1)][..]),program.terrain_output_color_slots());
            assert_eq!(cutoff.is_some(),program.fragment.source.contains("vulkanic_source_terrain_main"));
            if let Some(cutoff)=cutoff {
                assert!(program.fragment.source.contains(&format!("if (!(out_terrain_lit_color.a > {cutoff:?})) discard;")));
                assert!(program.fragment.source.contains("void vulkanic_source_terrain_main /* signature trivia */ (void)"));
                assert!(program.fragment.source.contains("// void main() { bogus comment }"));
            }
            for descriptor in program.shader_module_descriptors(gal.capabilities().shader_conventions) {
                let module=gal.create_shader_module(descriptor).unwrap();gal.destroy(module).unwrap();
            }
        }
    }
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn normal_terrain_alpha_rejects_unsupported_or_nonfinite_overrides() {
    for value in ["LESS 0.1", "GREATER NaN", "GREATER -0.1", "GREATER 1.1"] {
        assert!(
            derive_terrain_contract_for_scope(
                &source(&format!("alphaTest.gbuffers_terrain={value}\n")),
                TerrainProgramScope::Overworld
            )
            .is_err(),
            "{value}"
        );
    }
}

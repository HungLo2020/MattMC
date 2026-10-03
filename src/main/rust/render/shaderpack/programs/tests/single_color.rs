//! Single-color selected-source material contracts and native compilation.

use super::*;

#[test]
fn single_color_translucent_preserves_slot_lighting_and_explicit_alpha_policy() {
    use crate::render::shaderpack::contracts::terrain::derive_translucent_terrain_contract_for_scope;
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("single-color-water").unwrap();
    for property in ["", "alphaTest.gbuffers_water=off\n", "alphaTest.gbuffers_water=GREATER 0.02\n"] {
        let source = single_color_translucent_source(property, false);
        let contract = derive_translucent_terrain_contract_for_scope(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(std::collections::BTreeSet::from([TerrainMaterialClass::Translucent]), contract.material_classes);
        assert_eq!(Some(1), contract.output_color_slot(TerrainPassOutput::LitTerrainColor));
        assert_eq!(1, contract.outputs.len());
        let artifacts = preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
        let lowered = lower_translucent_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let bindings = lowered.opaque_resource_contract().bind_semantic_roles(&declarations).unwrap();
        let program = prepare_lowered_translucent_terrain_source_program(&contract, &lowered, &bindings).unwrap();
        assert_eq!(Some(&[(TerrainPassOutput::LitTerrainColor, 1)][..]), program.terrain_output_color_slots());
        assert!(program.fragment.source.contains("pow(vec3(0.8), vec3(2.0))"));
        assert_eq!(property.contains("GREATER"), program.fragment.source.contains("if (!(out_terrain_lit_color.a > 0.02)) discard;"));
        assert_eq!(property.contains("GREATER"), program.translucent_raster_state().unwrap().alpha_test.is_some());
        for descriptor in program.shader_module_descriptors(gal.capabilities().shader_conventions) {
            let module = gal.create_shader_module(descriptor).unwrap();
            gal.destroy(module).unwrap();
        }
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn single_color_translucent_rejects_undeclared_auxiliary_write() {
    use crate::render::shaderpack::contracts::terrain::derive_translucent_terrain_contract_for_scope;
    let source = single_color_translucent_source("", true);
    let contract = derive_translucent_terrain_contract_for_scope(&source, TerrainProgramScope::Overworld).unwrap();
    let artifacts = preprocess_terrain_sources(&source, &contract.source_stages().unwrap()).unwrap();
    assert!(lower_translucent_terrain_source_pair(&artifacts.vertex, &artifacts.fragment).is_err());
}

fn single_color_translucent_source(properties: &str, auxiliary: bool) -> ShaderPackSource {
    ShaderPackSource::new("single-color-water", 3, vec![
        ShaderSourceFile::new("world0/gbuffers_water.vsh", concat!(
            "#version 120\nvarying vec2 uv;\nvarying vec4 tint;\n",
            "void main() { gl_Position=ftransform(); uv=gl_MultiTexCoord0.xy; tint=gl_Color; }\n"
        )),
        ShaderSourceFile::new("world0/gbuffers_water.fsh", format!(
            "#version 120\nuniform sampler2D tex;\nvarying vec2 uv;\nvarying vec4 tint;\n/* DRAWBUFFERS:1 */\nvoid main() {{ vec4 pixel=texture2D(tex,uv)*tint; pixel.rgb*=pow(vec3(0.8), vec3(2.0)); gl_FragData[0]=pixel; {} }}\n",
            if auxiliary { "gl_FragData[1]=pixel;" } else { "" }
        )),
        ShaderSourceFile::new("shaders.properties", properties),
        ShaderSourceFile::new("block.properties", "block.10200=minecraft:water\n"),
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
    ]).unwrap()
}

#[test]
fn single_color_material_families_preserve_pack_lighting_and_output_slot() {
    use crate::render::shaderpack::contracts::{hand, material};
    let source = single_color_material_source();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let scope = TerrainProgramScope::Overworld;

    let contract = material::derive_textured_material_contract(&source, scope).unwrap();
    let lowered = material::lower_textured_material_source_pair(&source, &contract).unwrap();
    let bindings = lowered.opaque_resource_contract().bind_semantic_roles(&declarations).unwrap();
    let textured = prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings).unwrap();

    let contract = derive_entity_contract(&source, scope).unwrap();
    let lowered = lower_entity_source_pair(&source, &contract).unwrap();
    let bindings = bind_entity_source_resources(&lowered, &declarations).unwrap();
    let entity = prepare_lowered_entity_source_program(&contract, &lowered, &bindings).unwrap();

    let contract = hand::derive_hand_contract(&source, scope).unwrap();
    let lowered = hand::lower_hand_source_pair(&source, &contract).unwrap();
    let bindings = hand::bind_hand_source_resources(&lowered, &declarations).unwrap();
    let hand = prepare_lowered_hand_source_program(&contract, &lowered, &bindings).unwrap();

    for (slots, fragment) in [
        (textured.named_output_color_slots(), textured.fragment.source.as_str()),
        (entity.named_output_color_slots(), entity.fragment.source.as_str()),
        (hand.named_output_color_slots(), hand.fragment.source.as_str()),
    ] {
        assert_eq!(&[(TerrainPassOutput::LitTerrainColor, 1)], slots);
        assert!(fragment.contains("pow(vec3(0.8), vec3(2.0))"));
        assert!(!fragment.contains("DoLighting("));
        assert!(!fragment.contains("out_terrain_material_auxiliary"));
    }
    assert_eq!(Some(TerrainSourceResourceRole::MaterialAtlas), textured.opaque_resource_bindings.role_for("tex"));
    assert_eq!(Some(TerrainSourceResourceRole::MaterialTexture), entity.opaque_resource_bindings.role_for("tex"));
    assert_eq!(Some(TerrainSourceResourceRole::MaterialTexture), hand.opaque_resource_bindings.role_for("tex"));
    assert!(textured.fragment.source.contains("out_textured_material_lit_color.a > 0.1"));

    // The actual backend compiler verifies the explicit vertex/fragment ABI,
    // including legacy varyings and the one-output alpha-test wrapper.
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("single-color-materials").unwrap();
    let conventions = gal.capabilities().shader_conventions;
    for descriptors in [
        textured.shader_module_descriptors(conventions),
        entity.shader_module_descriptors(conventions),
        hand.shader_module_descriptors(conventions),
    ] {
        for descriptor in descriptors {
            let module = gal.create_shader_module(descriptor).unwrap();
            gal.destroy(module).unwrap();
        }
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

fn single_color_material_source() -> ShaderPackSource {
    let mut files = Vec::new();
    for entry in ["gbuffers_textured", "gbuffers_entities", "gbuffers_hand"] {
        let vertex = concat!(
            "#version 120\nvarying vec2 uv;\nvarying vec4 tint;\n",
            "// This shared header declaration is not read by these programs.\n",
            "attribute vec4 mc_Entity;\n",
            "void main() { gl_Position = ftransform(); uv = gl_MultiTexCoord0.xy; tint = gl_Color; }\n"
        );
        files.push(ShaderSourceFile::new(format!("world0/{entry}.vsh"), vertex));
        files.push(ShaderSourceFile::new(format!("world0/{entry}.fsh"), concat!(
            "#version 120\nuniform sampler2D tex;\nvarying vec2 uv;\nvarying vec4 tint;\n",
            "/* DRAWBUFFERS:1 */\nvoid main() { vec4 pixel = texture2D(tex, uv) * tint; ",
            "pixel.rgb *= pow(vec3(0.8), vec3(2.0)); gl_FragData[0] = pixel; }\n"
        )));
    }
    files.push(ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"));
    ShaderPackSource::new("single-color-materials", 3, files).unwrap()
}

#[test]
fn single_color_material_families_reject_undeclared_auxiliary_writes() {
    use crate::render::shaderpack::contracts::{hand, material};
    let base = single_color_material_source();
    let files = base.files().into_iter().map(|mut file| {
        if file.path.ends_with(".fsh") {
            file.contents = file.contents.replace("gl_FragData[0] = pixel;", "gl_FragData[0] = pixel; gl_FragData[1] = pixel;");
        }
        file
    }).collect();
    let source = ShaderPackSource::new(base.name(), base.generation(), files).unwrap();
    let scope = TerrainProgramScope::Overworld;
    let contract = material::derive_textured_material_contract(&source, scope).unwrap();
    assert!(material::lower_textured_material_source_pair(&source, &contract).is_err());
    let contract = derive_entity_contract(&source, scope).unwrap();
    assert!(lower_entity_source_pair(&source, &contract).unwrap_err().to_string().contains("undeclared auxiliary"));
    let contract = hand::derive_hand_contract(&source, scope).unwrap();
    assert!(hand::lower_hand_source_pair(&source, &contract).unwrap_err().to_string().contains("undeclared auxiliary"));
}

#[test]
fn compact_particle_source_preserves_frozen_generic_entity_attribute() {
    use crate::render::shaderpack::contracts::material;
    let base = single_color_material_source();
    let files = base.files().into_iter().map(|mut file| {
        if file.path == "world0/gbuffers_textured.vsh" {
            file.contents = concat!(
                "#version 120\nattribute vec4 mc_Entity;\n",
                "varying vec2 uv;\nvarying vec4 tint;\nvoid main() {\n",
                "float gloss = mc_Entity.x == 10400.0 ? 100.0 : 6.0;\n",
                "gl_Position = ftransform() + vec4(mc_Entity.xyz, 0.0);\n",
                "uv = gl_MultiTexCoord0.xy; tint = gl_Color * vec4(vec3(gloss / 6.0), mc_Entity.w); }\n"
            ).into();
        }
        file
    }).collect();
    let source = ShaderPackSource::new(base.name(), base.generation(), files).unwrap();
    let contract = material::derive_textured_material_contract(&source, TerrainProgramScope::Overworld).unwrap();
    assert!(contract.inputs.contains(&material::TexturedMaterialSourceInput::GenericEntityAttribute));
    let lowered = material::lower_textured_material_source_pair(&source, &contract).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = lowered.opaque_resource_contract().bind_semantic_roles(&declarations).unwrap();
    let program = prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings).unwrap();
    assert_eq!(64, program.execution_interface.vertex_stride);
    assert!(program.vertex.source.contains("#define vulkanic_source_entity vec4(0.0, 0.0, 0.0, 1.0)"));

    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("compact-particle-entity-input").unwrap();
    for descriptor in program.shader_module_descriptors(gal.capabilities().shader_conventions) {
        let module = gal.create_shader_module(descriptor).unwrap();
        gal.destroy(module).unwrap();
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

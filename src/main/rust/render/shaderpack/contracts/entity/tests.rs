use crate::render::shaderpack::contracts::entity::*;
use crate::render::shaderpack::source::ShaderSourceFile;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceResourceBindings, TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
};

fn source(draw_buffers: &str, entity_inputs: &str, attributes: &str) -> ShaderPackSource {
    ShaderPackSource::new(
        "entity-fixture",
        19,
        vec![
            ShaderSourceFile::new(
                "world0/gbuffers_entities.vsh",
                format!(
                    "#version 130\nvoid main() {{ vec4 p = gl_Vertex; vec2 light = GetLightMapCoordinates(); vec3 normal = gl_Normal; vec4 color = gl_Color; {attributes} gl_Position = ftransform(); }}"
                ),
            ),
            ShaderSourceFile::new(
                "world0/gbuffers_entities.fsh",
                format!(
                    "#version 130\nuniform sampler2D tex;\nvoid DoLighting() {{}}\nvoid main() {{ vec4 color = texture2D(tex, texCoord); color *= glColor; {entity_inputs} DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; /* DRAWBUFFERS:{draw_buffers} */ }}"
                ),
            ),
            ShaderSourceFile::new("entity.properties", "entity.50076=boat\n"),
            ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ],
    )
    .unwrap()
}

#[test]
fn discovers_entity_identity_color_and_optional_vertex_attributes() {
    let source = source(
        "06",
        "if (entityId == 50004) color.rgb = entityColor.rgb;",
        "vec4 mid = mc_midTexCoord; vec4 tangent = at_tangent;",
    );
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

    assert_eq!("world0/gbuffers_entities.fsh", contract.program_path);
    assert_eq!(vec![0, 6], contract.output_color_slots);
    assert!(contract.inputs.contains(&EntitySourceInput::EntityIdentity));
    assert!(contract.inputs.contains(&EntitySourceInput::EntityColor));
    assert_eq!(
        vec![
            EntitySourceVertexAttribute::MidTextureCoordinate,
            EntitySourceVertexAttribute::Tangent,
        ],
        contract.vertex_attributes
    );
    assert_eq!(
        TerrainPassOutput::MaterialAuxiliary,
        EntitySourceOutput::MaterialAuxiliary.terrain_output()
    );
}

#[test]
fn requires_the_bounded_entity_draw_buffer_schema() {
    let source = source("015", "", "");
    let error = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap_err();
    assert!(error.to_string().contains("DRAWBUFFERS schema"));
}

#[test]
fn does_not_invent_entity_fields_when_the_selected_source_does_not_use_them() {
    let source = source("06", "", "");
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

    assert!(!contract.inputs.contains(&EntitySourceInput::EntityIdentity));
    assert!(!contract.inputs.contains(&EntitySourceInput::EntityColor));
    assert!(contract.vertex_attributes.is_empty());
}

#[test]
fn retains_the_optional_named_view_space_normal_output() {
    let source = source("065", "", "");
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

    assert_eq!(
        vec![
            EntitySourceOutput::LitColor,
            EntitySourceOutput::MaterialAuxiliary,
            EntitySourceOutput::ViewSpaceNormal,
        ],
        contract.outputs
    );
    assert_eq!(
        TerrainPassOutput::ViewSpaceNormal,
        EntitySourceOutput::ViewSpaceNormal.terrain_output()
    );
}

#[test]
fn lowers_entity_source_through_the_owned_indexed_mesh_stream() {
    let source = source(
        "06",
        "if (entityId == 50004) color.rgb = entityColor.rgb;",
        "vec4 mid = mc_midTexCoord; vec4 tangent = at_tangent;",
    );
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let lowered = lower_entity_source_pair(&source, &contract).unwrap();

    lowered.require_backend_neutral_lowering().unwrap();
    assert!(lowered
        .vertex()
        .source()
        .contains("VulkanicSourceTerrainVertices"));
    assert!(lowered.vertex().source().contains("vulkanic_source_entity"));
    assert!(lowered
        .fragment()
        .source()
        .contains("out_terrain_material_auxiliary"));
}

#[test]
fn entity_source_rebinds_only_the_base_sampler_to_local_material_texture() {
    let source = source("06", "", "");
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
    let lowered = lower_entity_source_pair(&source, &contract).unwrap();
    let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let bindings = bind_entity_source_resources(&lowered, &declarations).unwrap();

    assert_eq!(
        Some(TerrainSourceResourceRole::MaterialTexture),
        bindings.role_for("tex")
    );
}

#[test]
fn resolves_pack_entity_ids_from_canonical_gameplay_identity() {
    let source = source("06", "", "");
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
    assert_eq!(19, contract.entity_id_generation());
    assert_eq!(
        50076,
        contract.entity_id_for_identity("minecraft:boat").unwrap()
    );
    assert_eq!(
        -1,
        contract.entity_id_for_identity("minecraft:arrow").unwrap()
    );
}

#[test]
fn resolves_per_draw_entity_semantics_without_java_pack_ids() {
    let source = source("06", "", "");
    let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

    let boat = contract
        .resolve_draw_semantics("minecraft:boat", 0x8040_80ff)
        .unwrap();
    assert_eq!("minecraft:boat", boat.entity_identity);
    assert_eq!(50_076, boat.entity_id);
    assert_eq!(
        [64.0 / 255.0, 128.0 / 255.0, 1.0, 128.0 / 255.0],
        boat.entity_color
    );
    assert_eq!(19, boat.entity_id_generation);

    let arrow = contract
        .resolve_draw_semantics("minecraft:arrow", 0)
        .unwrap();
    assert_eq!(-1, arrow.entity_id);
    assert_eq!([0.0; 4], arrow.entity_color);
    assert!(contract
        .resolve_draw_semantics("Minecraft:Arrow", 0)
        .unwrap_err()
        .to_string()
        .contains("not canonical"));
}

#[test]
fn bundled_armor_glint_lowers_for_world_and_first_person_streams() {
    let source =
        crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test();
    let entity = prepare_entity_glint_source_program(&source, TerrainProgramScope::Overworld)
        .unwrap();
    assert!(entity.identity.as_str().contains("entity_glint_source_gen"));
    let hand = crate::render::shaderpack::contracts::hand::prepare_hand_glint_source_program(
        &source,
        TerrainProgramScope::Overworld,
    )
    .unwrap();
    assert!(hand.identity.as_str().contains("hand_glint_source_gen"));
}

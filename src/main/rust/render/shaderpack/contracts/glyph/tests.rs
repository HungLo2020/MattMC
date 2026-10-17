use super::*;
use crate::render::shaderpack::contracts::terrain::TerrainPassOutput;
use crate::render::shaderpack::resources::bindings::TERRAIN_RESOURCE_BINDINGS_PATH;
use crate::render::shaderpack::source::ShaderSourceFile;

fn source(generation: u64, explicit_translucent: bool) -> ShaderPackSource {
    let vertex = "#version 130\nvarying vec2 uv;\nuniform int entityId;\nvoid main() { uv = gl_MultiTexCoord0.xy; gl_Position = ftransform(); }";
    let fragment = "#version 130\nvarying vec2 uv;\nuniform sampler2D tex;\nvoid main() { gl_FragData[0] = texture2D(tex, uv); gl_FragData[1] = vec4(0.0); gl_FragData[2] = vec4(0.0); } /* DRAWBUFFERS:065 */";
    let mut files = vec![
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
        ShaderSourceFile::new("world0/gbuffers_entities.vsh", vertex),
        ShaderSourceFile::new("world0/gbuffers_entities.fsh", fragment),
        ShaderSourceFile::new(
            "entity.properties",
            format!(
                "entity.{}=minecraft:item_frame\nentity.{}=minecraft:glow_item_frame",
                generation + 100,
                generation + 200
            ),
        ),
    ];
    if explicit_translucent {
        files.push(ShaderSourceFile::new(
            "world0/gbuffers_entities_translucent.vsh",
            vertex,
        ));
        files.push(ShaderSourceFile::new(
            "world0/gbuffers_entities_translucent.fsh",
            fragment,
        ));
    }
    ShaderPackSource::new("glyph-fixture", generation, files).unwrap()
}

#[test]
fn world_glyph_keeps_translucent_source_selection_and_named_normal_output() {
    for explicit in [false, true] {
        let p = prepare_world_glyph_source_program(
            &source(17, explicit),
            TerrainProgramScope::Overworld,
        )
        .unwrap();
        let family = if explicit {
            "gbuffers_entities_translucent"
        } else {
            "gbuffers_entities"
        };
        assert!(p
            .program
            .fragment
            .label
            .contains(&format!("/{family}.fsh:")));
        assert_eq!(
            p.program.named_output_color_slots(),
            &[
                (TerrainPassOutput::LitTerrainColor, 0),
                (TerrainPassOutput::MaterialAuxiliary, 6),
                (TerrainPassOutput::ViewSpaceNormal, 5)
            ]
        );
        assert_eq!(p.entity_id("minecraft:item_frame").unwrap(), 117);
        assert_eq!(p.entity_id("minecraft:glow_item_frame").unwrap(), 217);
        assert_eq!(p.program.execution_interface.vertex_stride, 64);
    }
}

#[test]
fn world_glyph_reload_resolves_new_ids_and_never_substitutes_a_broken_selected_writer() {
    let first =
        prepare_world_glyph_source_program(&source(17, true), TerrainProgramScope::Overworld)
            .unwrap();
    let second =
        prepare_world_glyph_source_program(&source(18, true), TerrainProgramScope::Overworld)
            .unwrap();
    assert_eq!(first.entity_id("minecraft:item_frame").unwrap(), 117);
    assert_eq!(second.entity_id("minecraft:item_frame").unwrap(), 118);
    assert_ne!(first.program.identity, second.program.identity);
    let mut files = source(17, true).files();
    files.retain(|f| f.path != "world0/gbuffers_entities_translucent.vsh");
    let broken = ShaderPackSource::new("glyph-fixture", 17, files).unwrap();
    assert!(prepare_world_glyph_source_program(&broken, TerrainProgramScope::Overworld).is_err());
}

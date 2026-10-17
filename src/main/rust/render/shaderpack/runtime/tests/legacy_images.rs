//! Real bundled source discovery without MattMC's optional binding manifest.

use super::*;

fn original_pack_source(clouds: bool) -> ShaderPackSource {
    let complete = complete_bundled_pack_source_for_test();
    let mut files = complete.files();
    files.retain(|file| file.path != TERRAIN_RESOURCE_BINDINGS_PATH);
    if clouds {
        files.push(ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
            "CLOUD_STYLE_DEFINE=50\n",
        ));
    }
    ShaderPackSource::new("original-pack-image-bindings", complete.generation(), files).unwrap()
}

#[test]
fn legacy_owned_image_aliases_real_pack_prepare_geometry_and_complete_fullscreen_chain() {
    let source = original_pack_source(true);
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

    assert_eq!(
        ("prepared", None),
        executor.candidate_weather_source_diagnostic()
    );
    assert_eq!(
        ("prepared", None),
        executor.candidate_cloud_source_diagnostic()
    );
    assert!(executor
        .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
        .unwrap()
        .is_some());
    assert!(executor
        .prepared_lowered_shadow_source_program()
        .unwrap()
        .is_some());
    let chain = executor
        .prepared_lowered_post_terrain_fullscreen_programs()
        .unwrap();
    assert_eq!(8, chain.len());
    let composite = chain
        .iter()
        .find(|program| {
            program
                .identity
                .as_str()
                .contains("world0-composite-source")
        })
        .unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::ColoredVoxelLightCurrent),
        composite
            .opaque_resource_bindings
            .role_for("floodfill_sampler")
    );
    assert_eq!(
        Some(TerrainSourceResourceRole::ColoredVoxelLightPrevious),
        composite
            .opaque_resource_bindings
            .role_for("floodfill_sampler_copy")
    );
}

#[test]
fn legacy_owned_image_aliases_real_pack_prepare_dh_and_its_depth_consumers() {
    let source = original_pack_source(false);
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    executor.observe_distant_horizons_source_candidate_for_scope(
        &source,
        TerrainProgramScope::Overworld,
    );

    assert!(executor
        .prepared_lowered_distant_horizons_source_program()
        .unwrap()
        .is_some());
    let consumers = executor
        .prepared_lowered_distant_horizons_depth_consumers()
        .unwrap();
    assert_eq!(5, consumers.len());
    assert!(consumers.iter().any(|program| program
        .opaque_resource_bindings
        .role_for("floodfill_sampler")
        == Some(TerrainSourceResourceRole::ColoredVoxelLightCurrent)));
}

#[test]
fn legacy_owned_image_aliases_real_pack_prepare_late_draw_families() {
    let source = original_pack_source(false);
    let scope = TerrainProgramScope::Overworld;
    use crate::render::shaderpack::contracts::{damaged_block, entity, hand, line};
    let outcomes = [
        (
            "entity shadow",
            entity::prepare_entity_shadow_source_program(&source, scope)
                .map(|program| program.identity.as_str().to_owned()),
        ),
        (
            "entity glint",
            entity::prepare_entity_glint_source_program(&source, scope)
                .map(|program| program.identity.as_str().to_owned()),
        ),
        (
            "hand glint",
            hand::prepare_hand_glint_source_program(&source, scope)
                .map(|program| program.identity.as_str().to_owned()),
        ),
        (
            "line",
            line::prepare_line_source_program(&source, scope)
                .map(|program| program.identity.as_str().to_owned()),
        ),
        (
            "damaged block",
            damaged_block::prepare_damaged_block_source_program(&source, scope)
                .map(|program| program.identity.as_str().to_owned()),
        ),
    ];
    assert!(
        outcomes.iter().all(|(_, result)| result.is_ok()),
        "{outcomes:#?}"
    );
}

#[test]
fn legacy_owned_image_aliases_real_pack_shadow_uses_gbuffers_custom_texture_group() {
    let source = original_pack_source(false);
    let shadow =
        crate::render::shaderpack::contracts::entity::prepare_entity_shadow_source_program(
            &source,
            TerrainProgramScope::Overworld,
        )
        .unwrap();
    assert_eq!(
        Some(TerrainSourceResourceRole::PackTexture(
            "lib/textures/cloud-water.png".to_owned()
        )),
        shadow.opaque_resource_bindings.role_for("gaux4")
    );
}

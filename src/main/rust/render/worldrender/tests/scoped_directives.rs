//! Scoped attachment and pipeline identity are both private GAL preparation.

use super::*;

#[test]
fn scoped_directive_shadow_extent_replaces_resources_and_keeps_last_valid_on_bad_scope() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.generation = 1;
    frontend
        .apply_shader_pack_source_update(ShaderPackSourceUpdate {
            pack_name: "dimension-shadow".into(),
            generation: 1,
            files: vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_terrain.fsh",
                    "const int shadowMapResolution = 512;\n",
                ),
                ShaderSourceFile::new(
                    "world1/gbuffers_terrain.fsh",
                    "const int shadowMapResolution = 768;\n",
                ),
                ShaderSourceFile::new(
                    "world-1/gbuffers_terrain.fsh",
                    "const int shadowMapResolution = 8192;\n",
                ),
            ],
        })
        .unwrap();
    let mut profile = WholeFrameProfile::default();
    frontend
        .ensure_g_buffer_resources(
            &mut gal,
            128,
            128,
            ColorFormat::Bgra8Unorm,
            None,
            Some(TerrainProgramScope::Overworld),
            &mut profile,
        )
        .unwrap();
    assert_eq!(
        512,
        frontend
            .g_buffer_resources
            .as_ref()
            .unwrap()
            .shadow_extent
            .width
    );
    let creates = gal.metrics().resource_creates;
    frontend
        .ensure_g_buffer_resources(
            &mut gal,
            128,
            128,
            ColorFormat::Bgra8Unorm,
            None,
            Some(TerrainProgramScope::Overworld),
            &mut profile,
        )
        .unwrap();
    assert_eq!(
        creates,
        gal.metrics().resource_creates,
        "same scope reuses the attachment graph"
    );
    frontend
        .ensure_g_buffer_resources(
            &mut gal,
            128,
            128,
            ColorFormat::Bgra8Unorm,
            None,
            Some(TerrainProgramScope::End),
            &mut profile,
        )
        .unwrap();
    assert_eq!(
        768,
        frontend
            .g_buffer_resources
            .as_ref()
            .unwrap()
            .shadow_extent
            .width
    );
    assert!(gal.metrics().resource_creates > creates);
    let creates = gal.metrics().resource_creates;
    assert!(frontend
        .ensure_g_buffer_resources(
            &mut gal,
            128,
            128,
            ColorFormat::Bgra8Unorm,
            None,
            Some(TerrainProgramScope::Nether),
            &mut profile
        )
        .is_err());
    assert_eq!(
        creates,
        gal.metrics().resource_creates,
        "invalid policy rejects before resource mutation"
    );
    assert_eq!(
        768,
        frontend
            .g_buffer_resources
            .as_ref()
            .unwrap()
            .shadow_extent
            .width
    );
    frontend.reset(&mut gal);
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
    assert!(gal.mock_backend().unwrap().live.is_empty());
}

#[test]
fn scoped_directive_shadow_cutout_specializations_have_distinct_pipeline_identity() {
    let mut gal = gal();
    let mut frontend = WorldPrimitiveFrontend::default();
    frontend.generation = 1;
    let source = complete_bundled_pack_source_for_test();
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let program = executor
        .prepared_lowered_shadow_source_program()
        .unwrap()
        .unwrap();
    let first = frontend
        .ensure_lowered_source_shadow_pipeline_resources(
            &mut gal,
            &program,
            WORLD_MATERIAL_MODE_CUTOUT,
            WORLD_CULL_NONE,
            WORLD_WINDING_CCW,
            Some(0.1),
        )
        .unwrap();
    let second = frontend
        .ensure_lowered_source_shadow_pipeline_resources(
            &mut gal,
            &program,
            WORLD_MATERIAL_MODE_CUTOUT,
            WORLD_CULL_NONE,
            WORLD_WINDING_CCW,
            Some(0.25),
        )
        .unwrap();
    assert_ne!(
        first, second,
        "a reused base program must retain the dimension's alpha rule"
    );
    let creates = gal.metrics().resource_creates;
    assert_eq!(
        first,
        frontend
            .ensure_lowered_source_shadow_pipeline_resources(
                &mut gal,
                &program,
                WORLD_MATERIAL_MODE_CUTOUT,
                WORLD_CULL_NONE,
                WORLD_WINDING_CCW,
                Some(0.1)
            )
            .unwrap()
    );
    assert_eq!(creates, gal.metrics().resource_creates);
    frontend.reset(&mut gal);
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

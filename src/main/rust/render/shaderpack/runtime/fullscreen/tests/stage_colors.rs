//! A geometry admission snapshot must not choose a fullscreen stage's colors.

use super::*;
use crate::render::shaderpack::resources::color_targets::{
    prepare_source_color_resources, ShaderPackColorSamplingPlan,
};

#[test]
fn fullscreen_stage_color_binding_selects_its_feedback_image_from_a_geometry_snapshot() {
    let source = source("#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }\n");
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, true);
    // Terrain admission supplies the current image with geometry's sampling
    // policy. This pass samples its own output and must use its previous side.
    let geometry_colors = prepare_source_color_resources(
        &mut gal,
        source.generation(),
        &program.opaque_resource_bindings,
        &ShaderPackColorSamplingPlan::default(),
        &targets,
    )
    .unwrap();
    let role = TerrainSourceResourceRole::ShaderPackColor("primary".into());
    assert_eq!(
        Some(targets.target("primary").unwrap().current_view),
        geometry_colors.sampled_view_for(role.clone())
    );
    let prepared = PreparedFullscreenSourcePass::prepare(
        &mut gal,
        &program,
        &manifest,
        &targets,
        std::iter::once(geometry_colors.resources().clone()),
    )
    .unwrap();
    assert_eq!(
        targets.target("primary").unwrap().previous_view,
        prepared.color_resources.sampled_view_for(role.clone())
    );
    assert_ne!(
        geometry_colors
            .resources()
            .combined_sampler_for(role.clone()),
        prepared.inputs.combined_sampler_for(role)
    );
    prepared.destroy(&mut gal);
    geometry_colors.destroy(&mut gal);
    cache.destroy(&mut gal);
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

#[test]
fn fullscreen_stage_color_binding_rejects_foreign_generation_snapshots_and_retires_partial_resources(
) {
    let source = source("#version 130\nin vec2 uv;\nuniform sampler2D colortex0;\nvoid main() { gl_FragData[0] = texture2D(colortex0, uv); }\n");
    let program = program(&source);
    let mut gal = crate::render::vulkanic::test_support::mock_gal();
    let (manifest, targets, mut cache) = staged(&source, &mut gal, true);
    for (pack_generation, world_generation) in
        [(source.generation() + 1, 13), (source.generation(), 14)]
    {
        let external = TerrainSourceOwnedResourceSet::new(
            TerrainSourceResourceAvailabilitySet::new(pack_generation, world_generation, [])
                .unwrap(),
            [],
        )
        .unwrap();
        let error = PreparedFullscreenSourcePass::prepare(
            &mut gal,
            &program,
            &manifest,
            &targets,
            std::iter::once(external),
        )
        .unwrap_err();
        assert!(error.to_string().contains("generation"), "{error}");
    }
    cache.destroy(&mut gal);
    assert_eq!(
        gal.metrics().resource_creates,
        gal.metrics().resource_destroys
    );
}

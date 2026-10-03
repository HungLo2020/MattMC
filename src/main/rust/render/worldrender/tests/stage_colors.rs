//! Program-local color samplers must supersede geometry admission bindings.

use super::*;

#[test]
fn geometry_stage_color_binding_replaces_an_opaque_snapshot_with_translucent_bindings() {
    let source = ShaderPackSource::new("geometry-stage-colors", 1, vec![
        ShaderSourceFile::new("gbuffers_terrain.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("gbuffers_water.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("gbuffers_terrain.fsh", "#version 130\nuniform sampler2D colortex7;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=texture2D(colortex7,vec2(0.25)); }\n"),
        ShaderSourceFile::new("gbuffers_water.fsh", "#version 130\nuniform sampler2D colortex6;\nuniform sampler2D gaux4;\n/* DRAWBUFFERS:1 */\nvoid main() { gl_FragData[0]=texture2D(colortex6,vec2(0.25))+texture2D(gaux4,vec2(0.25)); }\n"),
        ShaderSourceFile::new("block.properties", ""),
        ShaderSourceFile::new("shaders.properties", ""),
    ]).unwrap();
    let mut frontend = WorldPrimitiveFrontend::default();
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let opaque = runtime.prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque).unwrap().unwrap();
    let translucent = runtime.prepared_lowered_translucent_terrain_source_program().unwrap().unwrap();
    let mut gal = gal();
    let targets = runtime.stage_source_color_targets(&mut gal, 1,
        Extent3d { width: 16, height: 16, depth: 1 }).unwrap().unwrap();
    let opaque_colors = runtime.stage_terrain_source_color_resources(&mut gal, &opaque, &targets).unwrap();
    let translucent_colors = runtime.stage_terrain_source_color_resources(&mut gal, &translucent, &targets).unwrap();
    let role = TerrainSourceResourceRole::ShaderPackColor("auxiliary_h".into());
    assert_ne!(opaque_colors.combined_sampler_for(role.clone()), translucent_colors.combined_sampler_for(role.clone()));
    frontend.shader_runtime = Some(runtime);
    frontend.candidate_source_resource_snapshot = Some(CandidateSourceResourceSnapshot {
        shader_pack_generation: 1, world_generation: 1, frame_id: 42,
        resources: opaque_colors.clone(),
    });
    let merged = frontend.stage_candidate_source_resources_for_terrain_program(
        &mut gal, 1, 1, 42, &translucent, &targets).unwrap();
    assert_eq!(translucent_colors.combined_sampler_for(role.clone()), merged.combined_sampler_for(role.clone()));
    // A local writer must not mutate the admission table or take its ownership.
    assert_eq!(opaque_colors.combined_sampler_for(role.clone()), frontend.candidate_source_resource_snapshot.as_ref().unwrap().resources.combined_sampler_for(role));
    frontend.shader_runtime.take().unwrap().destroy(&mut gal).unwrap();
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

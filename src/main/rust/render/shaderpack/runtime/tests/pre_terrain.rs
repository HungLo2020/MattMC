//! Mandatory pre-world source writers and their target/admission closure.
use super::*;

fn pre_terrain_source(malformed: bool) -> ShaderPackSource {
    let mut files = vec![
        ShaderSourceFile::new("world0/gbuffers_terrain.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/gbuffers_terrain.fsh", "#version 130\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=vec4(1.0); }\n"),
        ShaderSourceFile::new("block.properties", ""),
        ShaderSourceFile::new("shaders.properties", "program.world0/prepare1.enabled=false\n"),
    ];
    for (name, fragment) in [
        ("begin", "/* DRAWBUFFERS:7 */\nvoid main() { gl_FragData[0]=vec4(0.2); }"),
        ("begin2", "/* DRAWBUFFERS:6 */\nvoid main() { gl_FragData[0]=vec4(0.3); }"),
        ("prepare", "uniform sampler2D colortex7;\nuniform sampler2D noisetex;\nconst bool colortex7MipmapEnabled=true;\n/* DRAWBUFFERS:7 */\nvoid main() { gl_FragData[0]=texture2D(colortex7,vec2(0.5))+texture2D(noisetex,vec2(0.5)); }"),
        ("prepare1", "/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=vec4(0.4); }"),
        ("final", "void main() { gl_FragData[0]=vec4(1.0); }"),
    ] {
        files.push(ShaderSourceFile::new(format!("world0/{name}.vsh"), "#version 130\nvoid main() { gl_Position=gl_ModelViewProjectionMatrix*gl_Vertex; }\n"));
        files.push(ShaderSourceFile::new(format!("world0/{name}.fsh"), format!("#version 130\n{fragment}\n")));
    }
    if malformed { files.retain(|file| file.path != "world0/prepare.vsh"); }
    ShaderPackSource::new("pre-terrain", 1, files).unwrap()
}

#[test]
fn pre_terrain_fullscreen_writers_are_ordered_required_and_in_target_closure() {
    let source = pre_terrain_source(false);
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    let programs = runtime.prepared_lowered_pre_terrain_fullscreen_programs(false).unwrap();
    assert_eq!(vec!["world0/begin.fsh", "world0/begin2.fsh", "world0/prepare.fsh"],
        programs.iter().map(|p| p.source_stage_path.as_str()).collect::<Vec<_>>());
    assert!(programs.iter().all(|p| p.raster_primitive == FullscreenSourceRasterPrimitive::FullscreenTriangle));
    assert_eq!(1, runtime.prepared_lowered_post_terrain_fullscreen_programs().unwrap().len());
    assert!(runtime.source_required_resource_roles_for_frame(false).contains(&TerrainSourceResourceRole::Noise));
    let (feedback, mipmaps) = runtime.complete_source_color_target_requirements().unwrap();
    assert!(feedback.contains(&"auxiliary_h".to_string()));
    assert!(mipmaps.contains(&"auxiliary_h".to_string()));
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("pre-terrain-source").unwrap();
    for program in programs {
        for descriptor in program.shader_module_descriptors(gal.capabilities().shader_conventions) {
            let module = gal.create_shader_module(descriptor).unwrap();
            gal.destroy(module).unwrap();
        }
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

#[test]
fn pre_terrain_fullscreen_missing_pair_rejects_admission_and_target_staging() {
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate_for_scope(&pre_terrain_source(true), TerrainProgramScope::Overworld);
    assert!(runtime.prepared_lowered_pre_terrain_fullscreen_programs(false).unwrap_err().to_string().contains("prepare.vsh"));
    assert!(runtime.complete_source_color_target_requirements().unwrap_err().to_string().contains("prepare.vsh"));
}

#[test]
fn pre_terrain_fullscreen_clear_policy_preserves_writers_after_frame_start_clears() {
    use crate::render::shaderpack::contracts::terrain::TerrainPassOutput;
    use crate::render::shaderpack::resources::color_targets::TerrainSourceColorAttachment;
    let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    runtime.observe_source_candidate_for_scope(&pre_terrain_source(false), TerrainProgramScope::Overworld);
    let mut gal = gal();
    let targets = runtime.stage_complete_source_color_targets(&mut gal, 1,
        Extent3d { width: 16, height: 16, depth: 1 }).unwrap().unwrap();
    let mut operations = Vec::new();
    let mut first = runtime.begin_source_color_transaction(&mut gal, &targets,
        ShaderPackColorClearValues { fog_color: ClearColor { r: 0.0, g: 0.0, b: 0.0, a: 1.0 } }, &mut operations).unwrap();
    first.finish(&mut operations).unwrap();
    gal.submit(SubmissionBatch { label: "initial-color-clear".into(), command_lists: vec![CommandList::from(CommandListDesc { label: "initial-color-clear".into(), operations })] }).unwrap();
    first.confirm(&mut runtime, &mut gal).unwrap();
    let attachment = |name: &str, clear| {
        let target = targets.target(name).unwrap();
        TerrainSourceColorAttachment {
            output: TerrainPassOutput::LitTerrainColor, source_slot: target.source_slot,
            role: TerrainSourceResourceRole::ShaderPackColor(name.into()), texture: target.current_texture,
            view: target.current_attachment_view, format: target.format,
            clear_each_frame: clear, clear_color_bits: target.clear_color_bits,
        }
    };
    let original = vec![attachment("primary", true), attachment("auxiliary_h", true), attachment("auxiliary_g", false)];
    let mut operations = Vec::new();
    let mut warm = runtime.begin_source_color_transaction(&mut gal, &targets,
        ShaderPackColorClearValues { fog_color: ClearColor { r: 0.0, g: 0.0, b: 0.0, a: 1.0 } }, &mut operations).unwrap();
    assert!(operations.iter().any(|op| matches!(op, CommandOp::BeginPass { .. })), "clear-enabled targets must clear before pre-terrain stages");
    warm.record_external_outputs(&[TerrainSourceResourceRole::ShaderPackColor("auxiliary_h".into())]).unwrap();
    let mut resolved = original.clone();
    warm.resolve_terrain_color_clear_policy(&mut resolved).unwrap();
    let phase = TerrainSourceColorPassPhase::BootstrapAfterInitialization;
    assert_eq!(AttachmentLoadOp::Load, phase.color_load_op(&resolved[0]));
    assert_eq!(AttachmentLoadOp::Load, phase.color_load_op(&resolved[1]));
    assert_eq!(AttachmentLoadOp::Load, phase.color_load_op(&resolved[2]));
    assert!(original[1].clear_each_frame, "cached declarations remain immutable");
    warm.discard(&mut runtime, &mut gal);
    runtime.destroy(&mut gal).unwrap();
    gal.retire_through(gal.latest_submission_id()).unwrap();
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
}

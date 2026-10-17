//! Opt-in source and native-module diagnosis of an extracted real shader pack.
//! It is supplemental: no gameplay, resource admission or presentation occurs.

use super::*;
use crate::render::shaderpack::source::RUNTIME_ENVIRONMENT_PATH;

#[test]
#[ignore = "requires MATTMC_SHADER_PROBE_SOURCE and a native Vulkan device"]
fn configured_pack_source_diagnostic() {
    fn collect(directory: &std::path::Path, root: &std::path::Path,
               files: &mut Vec<ShaderSourceFile>, bytes: &mut usize) {
        let mut entries = std::fs::read_dir(directory).unwrap()
            .map(Result::unwrap).collect::<Vec<_>>();
        entries.sort_by_key(|entry| entry.path());
        for entry in entries {
            let kind = entry.file_type().unwrap();
            assert!(!kind.is_symlink(), "source probe does not follow symlinks");
            if kind.is_dir() {
                collect(&entry.path(), root, files, bytes);
            } else if kind.is_file() {
                let path = entry.path();
                let extension = path.extension().and_then(|value| value.to_str()).unwrap_or("");
                if !matches!(extension, "vsh" | "fsh" | "gsh" | "csh" | "glsl" | "properties") {
                    continue;
                }
                let size = usize::try_from(entry.metadata().unwrap().len()).unwrap();
                *bytes = bytes.checked_add(size).unwrap();
                assert!(*bytes <= 64 * 1024 * 1024 && files.len() < 4096, "source probe budget exceeded");
                files.push(ShaderSourceFile::new(
                    path.strip_prefix(root).unwrap().to_string_lossy().replace('\\', "/"),
                    std::fs::read_to_string(path).unwrap(),
                ));
            }
        }
    }
    let root = PathBuf::from(std::env::var("MATTMC_SHADER_PROBE_SOURCE").unwrap());
    let mut files = Vec::new();
    collect(&root, &root, &mut files, &mut 0);
    if !files.iter().any(|file| file.path == RUNTIME_ENVIRONMENT_PATH) {
        // Explicit normal Iris/MC environment for a local Overworld probe.
        // An extracted semantic snapshot may provide its exact environment instead.
        files.push(ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, concat!(
            "IRIS_VERSION=12000\nMC_VERSION=12110\nIS_IRIS=1\nMC_OS_LINUX=1\n",
            "MC_RENDER_STAGE_SUN=4\nMC_RENDER_STAGE_MOON=5\n",
            "MC_RENDER_STAGE_TERRAIN_SOLID=8\nMC_RENDER_STAGE_TERRAIN_CUTOUT_MIPPED=9\n",
            "MC_RENDER_STAGE_TERRAIN_CUTOUT=10\nMC_RENDER_STAGE_TERRAIN_TRANSLUCENT=15\n",
            "MC_RENDER_STAGE_RAIN_SNOW=19\n"
        )));
    }
    let source = ShaderPackSource::new("configured-pack-probe", 1, files).unwrap();
    eprintln!("built-in frame policy: {:?}", source.frame_uniform_policy(TerrainProgramScope::Overworld).unwrap());
    eprintln!("scoped shadow policy: {:?}", source.shadow_policy_for_scope(TerrainProgramScope::Overworld));
    let bindings = crate::render::shaderpack::resources::bindings::TerrainSourceResourceBindings::from_source(&source).unwrap();
    eprintln!("scoped color targets: {:?}", crate::render::shaderpack::resources::color_targets::ShaderPackColorTargetManifest::from_source_for_scope(&source, &bindings, TerrainProgramScope::Overworld));
    let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(1).unwrap();
    executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
    match executor.source_candidate() {
        TerrainSourceCandidateState::Discovered { contract, .. } => {
            eprintln!("terrain contract: {} outputs={:?}", contract.program_path, contract.output_color_slots);
        }
        TerrainSourceCandidateState::Rejected { reason, .. } => panic!("terrain discovery rejected: {reason}"),
        _ => panic!("terrain source unavailable"),
    }
    let assets = executor.source_asset_binding_plan()
        .expect("selected property branches must produce an asset declaration plan");
    eprintln!("selected PNG declarations: {:?}", assets.samplers().collect::<Vec<_>>());
    let mut gal = crate::render::vulkanic::test_support::vulkan_gal("shader-source-probe").unwrap();
    let conventions = gal.capabilities().shader_conventions;
    let mut failures = Vec::new();
    macro_rules! probe {
        ($name:expr, $program:expr) => {
            match $program {
                Ok(Some(program)) => {
                    let unresolved = program.scalar_uniform_requirements.unresolved_fields()
                        .map(|field| field.name()).collect::<Vec<_>>();
                    eprintln!("{} unresolved scalar semantics: {:?}", $name, unresolved);
                    for descriptor in program.shader_module_descriptors(conventions) {
                        match gal.create_shader_module(descriptor) {
                            Ok(module) => gal.destroy(module).unwrap(),
                            Err(error) => {
                                eprintln!("{} native module rejected: {error}", $name);
                                failures.push($name);
                            }
                        }
                    }
                }
                Ok(None) => { eprintln!("{} source program unavailable", $name); failures.push($name); }
                Err(error) => { eprintln!("{} preparation rejected: {error}", $name); failures.push($name); }
            }
        }
    }
    probe!("opaque", executor.prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque));
    probe!("cutout", executor.prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Cutout));
    probe!("translucent", executor.prepared_lowered_translucent_terrain_source_program());
    probe!("shadow", executor.prepared_lowered_shadow_source_program());
    probe!("textured", executor.prepared_lowered_textured_material_source_program());
    probe!("entity", executor.prepared_lowered_entity_source_program());
    probe!("hand", executor.prepared_lowered_hand_source_program());
    for (name, candidate) in [
        ("sky", executor.prepared_lowered_pre_terrain_sky_program()),
        ("celestial", executor.prepared_lowered_pre_terrain_celestial_program()),
    ] {
        if matches!(&candidate, Ok(None)) {
            eprintln!("{name} source stage not declared");
        } else {
            probe!(name, candidate);
        }
    }
    match executor.prepared_lowered_pre_terrain_fullscreen_programs(false).and_then(|mut programs| {
        programs.extend(executor.prepared_lowered_post_terrain_fullscreen_programs()?);
        Ok(programs)
    }) {
        Ok(programs) => {
            eprintln!("pre/post-terrain fullscreen stages: {}", programs.len());
            let required = executor.source_required_resource_roles_for_frame(false);
            for program in programs {
                for binding in program.opaque_resource_bindings.bindings() {
                    assert!(required.contains(&binding.role()),
                        "normal-world asset/admission closure omitted {} from {}",
                        binding.role().diagnostic_name(),program.source_stage_path);
                }
                let unresolved = program.scalar_uniform_requirements.unresolved_fields()
                    .map(|field| field.name()).collect::<Vec<_>>();
                eprintln!("{} unresolved scalar semantics: {:?}", program.source_stage_path, unresolved);
                for descriptor in program.shader_module_descriptors(conventions) {
                    match gal.create_shader_module(descriptor) {
                        Ok(module) => gal.destroy(module).unwrap(),
                        Err(error) => {
                            eprintln!("{} native module rejected: {error}", program.source_stage_path);
                            failures.push("post-terrain fullscreen native compilation");
                        }
                    }
                }
            }
        }
        Err(error) => {
            eprintln!("post-terrain fullscreen preparation rejected: {error}");
            failures.push("post-terrain fullscreen preparation");
        }
    }
    assert_eq!(gal.metrics().resource_creates, gal.metrics().resource_destroys);
    assert!(failures.is_empty(), "source/native preparation failures: {failures:?}");
}

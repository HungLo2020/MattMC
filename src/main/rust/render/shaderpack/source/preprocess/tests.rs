use crate::render::shaderpack::source::preprocess::*;
use crate::render::shaderpack::source::ShaderSourceFile;

fn source(files: Vec<ShaderSourceFile>) -> ShaderPackSource {
    ShaderPackSource::new("test-pack", 7, files).unwrap()
}

#[test]
fn complete_bundled_terrain_pair_expands_without_admitting_execution() {
    let source = complete_bundled_pack_source_for_test();
    let stages = crate::render::shaderpack::contracts::terrain::TerrainSourceStages {
        vertex: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "world0/gbuffers_terrain.vsh".to_string(),
            defines: std::collections::BTreeMap::new(),
        },
        fragment: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "world0/gbuffers_terrain.fsh".to_string(),
            defines: std::collections::BTreeMap::new(),
        },
    };
    let artifacts = preprocess_terrain_sources(&source, &stages).unwrap();
    assert!(artifacts.vertex.expanded_source().contains("void main"));
    assert!(artifacts.fragment.expanded_source().contains("void main"));
    assert!(artifacts
        .vertex
        .expanded_source()
        .contains("#define IRIS_FEATURE_CUSTOM_IMAGES 1"));
    assert!(artifacts
        .fragment
        .expanded_source()
        .contains("#define IRIS_FEATURE_CUSTOM_IMAGES 1"));
    assert!(artifacts
        .vertex
        .expanded_source()
        .lines()
        .all(|line| !line.trim_start().starts_with("#include")));
    assert!(artifacts
        .fragment
        .expanded_source()
        .lines()
        .all(|line| !line.trim_start().starts_with("#include")));
    assert!(artifacts.vertex.resolved_paths().len() > 5);
    assert!(artifacts.fragment.resolved_paths().len() > 5);
    let vertex_dialect = crate::render::shaderpack::source::dialect::analyze_glsl_dialect(&artifacts.vertex);
    let fragment_dialect = crate::render::shaderpack::source::dialect::analyze_glsl_dialect(&artifacts.fragment);
    assert_eq!(Some(130), vertex_dialect.declared_version());
    assert_eq!(Some(130), fragment_dialect.declared_version());
    assert!(vertex_dialect
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::FixedFunctionVertexTransform));
    assert!(fragment_dialect
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::CompatibilityFragmentOutputs));
    assert!(vertex_dialect.require_backend_neutral_lowering().is_err());
    assert!(fragment_dialect.require_backend_neutral_lowering().is_err());
    let vertex_interface =
        crate::render::shaderpack::contracts::vertex_interface::analyze_terrain_vertex_interface(&artifacts.vertex);
    assert!(vertex_interface
        .required()
        .contains(&crate::render::shaderpack::contracts::vertex_interface::TerrainVertexSemantic::SpriteMidpoint));
    assert!(vertex_interface
        .require_current_world_mesh_support()
        .is_ok());
    let lowered = crate::render::shaderpack::lowering::lower_terrain_source_pair(
        &artifacts.vertex,
        &artifacts.fragment,
    )
    .unwrap();
    let lowered_vertex = lowered.vertex();
    assert!(lowered_vertex
        .source()
        .contains("VulkanicSourceTerrainVertex"));
    assert!(lowered_vertex.remaining_dialect().gaps().is_empty());
    let lowered_fragment = lowered.fragment();
    assert!(lowered_fragment
        .outputs()
        .contains(&crate::render::shaderpack::lowering::TerrainFragmentOutput::LitColor));
    assert!(lowered_fragment
        .outputs()
        .contains(&crate::render::shaderpack::lowering::TerrainFragmentOutput::MaterialAuxiliary));
    assert!(!lowered_fragment
        .remaining_dialect()
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::PreVulkanGlslVersion));
    assert!(!lowered_fragment
        .remaining_dialect()
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::CompatibilityTextureBuiltin));
    assert!(!lowered_fragment
        .remaining_dialect()
        .gaps()
        .contains(&crate::render::shaderpack::source::dialect::GlslDialectGap::CompatibilityFragmentOutputs));
    assert!(lowered_fragment
        .remaining_dialect()
        .require_backend_neutral_lowering()
        .is_ok());
    let contract =
        crate::render::shaderpack::contracts::terrain::derive_complementary_terrain_contract_for_scope(
            &source,
            crate::render::shaderpack::contracts::terrain::TerrainProgramScope::Overworld,
        )
        .unwrap();
    // Include expansion was already proven above. Source admission must
    // now be decided by the lowered interface and semantic resources,
    // rather than by raw `#include` text in the original pack file.
    assert!(contract.require_selected_subset().is_ok());
}

#[test]
fn expands_only_active_nested_branch_and_skips_inactive_includes() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program.glsl",
            "#if QUALITY >= 2\n#include \"active.glsl\"\n#elif defined FALLBACK\n#include \"missing.glsl\"\n#else\nwrong\n#endif\n#ifdef EXTRA\nextra\n#endif",
        ),
        ShaderSourceFile::new("active.glsl", "#define LOCAL 3\nactive LOCAL"),
    ]);
    let output = preprocess(PreprocessInput {
        source: &source,
        entry: "program.glsl",
        defines: &[("QUALITY", "2")],
    })
    .unwrap();
    assert!(output.contains("active LOCAL"));
    assert!(!output.contains("wrong"));
    assert!(!output.contains("missing.glsl"));
    assert!(!output.contains("extra"));
}

#[test]
fn emits_external_defines_after_root_glsl_version() {
    let source = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "// legal leading comment\n#version 450 core\n#if FEATURE\nselected\n#endif",
    )]);
    let output = preprocess(PreprocessInput {
        source: &source,
        entry: "program.glsl",
        defines: &[("FEATURE", "1")],
    })
    .unwrap();
    let version = output.find("#version 450 core").unwrap();
    let define = output.find("#define FEATURE 1").unwrap();
    let selected = output.find("selected").unwrap();
    assert!(version < define && define < selected);
}

#[test]
fn runtime_option_snapshot_is_included_in_the_owned_preprocess_identity() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program.glsl",
            "#if COLORED_LIGHTING == 128\nselected\n#endif",
        ),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
            "COLORED_LIGHTING=128\n",
        ),
    ]);
    let artifact =
        preprocess_artifact_with_runtime_options(&source, "program.glsl", &[]).unwrap();
    assert!(artifact.expanded_source().contains("selected"));
    assert_eq!(
        &[("COLORED_LIGHTING".to_string(), "128".to_string())],
        artifact.defines()
    );
    assert!(preprocess_artifact_with_runtime_options(
        &source,
        "program.glsl",
        &[("COLORED_LIGHTING", "0")]
    )
    .is_err());
}

#[test]
fn caller_semantic_define_must_agree_with_runtime_option_snapshot() {
    let source = source(vec![
        ShaderSourceFile::new("program.glsl", "#if DH_BLOCK_AIR == 14\nselected\n#endif"),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
            "DH_BLOCK_AIR=14\n",
        ),
    ]);

    let agreed = preprocess_artifact_with_runtime_options(
        &source,
        "program.glsl",
        &[("DH_BLOCK_AIR", "14")],
    )
    .expect("matching engine and runtime semantic defines must deduplicate");
    assert!(agreed.expanded_source().contains("selected"));
    assert_eq!(
        Some("14"),
        agreed
            .defines()
            .iter()
            .find_map(|(key, value)| (key == "DH_BLOCK_AIR").then_some(value.as_str()))
    );

    let error = preprocess_artifact_with_runtime_options(
        &source,
        "program.glsl",
        &[("DH_BLOCK_AIR", "99")],
    )
    .expect_err("a mismatched engine semantic define must remain a hard rejection");
    assert!(error.to_string().contains("DH_BLOCK_AIR=99"));
    assert!(error.to_string().contains("'14'"));
}

#[test]
fn runtime_constant_snapshot_rewrites_only_matching_const_declarations() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program.glsl",
            "#version 450\nconst float shadowDistance = 192.0; // selected\nfloat useValue() { return shadowDistance; }",
        ),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_CONSTANTS_PATH,
            "shadowDistance=320.0\n",
        ),
    ]);

    let artifact =
        preprocess_artifact_with_runtime_options(&source, "program.glsl", &[]).unwrap();
    assert!(artifact
        .expanded_source()
        .contains("const float shadowDistance = 320.0; // selected"));
    assert!(!artifact
        .expanded_source()
        .contains("#define shadowDistance"));
    assert!(artifact
        .expanded_source()
        .contains("return shadowDistance;"));
}

#[test]
fn runtime_snapshots_cannot_select_a_shader_stage() {
    let option_source = source(vec![
        ShaderSourceFile::new("program.glsl", "#version 450\nvoid main() {}"),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
            "FRAGMENT_SHADER=1\n",
        ),
    ]);
    assert!(preprocess_artifact_with_runtime_options(&option_source, "program.glsl", &[])
        .unwrap_err()
        .to_string()
        .contains("runtime option snapshot must not define shader stage selector 'FRAGMENT_SHADER'"));

    let environment_source = source(vec![
        ShaderSourceFile::new("program.glsl", "#version 450\nvoid main() {}"),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_ENVIRONMENT_PATH,
            "VERTEX_SHADER=1\n",
        ),
    ]);
    assert!(preprocess_artifact_with_runtime_options(
        &environment_source,
        "program.glsl",
        &[]
    )
    .unwrap_err()
    .to_string()
    .contains("runtime environment snapshot must not define shader stage selector 'VERTEX_SHADER'"));
}

#[test]
fn runtime_option_snapshot_owns_its_selected_value() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program.glsl",
            "#include \"common.glsl\"\n#ifdef CUSTOM_PBR\nunsupported\n#endif",
        ),
        ShaderSourceFile::new(
            "common.glsl",
            "#define RP_MODE 1\n#if RP_MODE >= 2\n#define CUSTOM_PBR\n#endif",
        ),
        ShaderSourceFile::new(crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH, "RP_MODE=0\n"),
    ]);

    let artifact =
        preprocess_artifact_with_runtime_options(&source, "program.glsl", &[]).unwrap();
    assert_eq!(
        Some("0"),
        artifact
            .defines()
            .iter()
            .find_map(|(key, value)| (key == "RP_MODE").then_some(value.as_str()))
    );
    assert!(!artifact.expanded_source().contains("unsupported"));
    assert!(!artifact
        .defines()
        .iter()
        .any(|(key, _)| key == "CUSTOM_PBR"));
    assert_eq!(
        1,
        artifact
            .expanded_source()
            .matches("#define RP_MODE 0")
            .count(),
        "the selected runtime value must be emitted once rather than allowing the pack default to redefine it"
    );
}

#[test]
fn runtime_option_fallback_is_emitted_for_a_stage_without_its_declaration() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program/dh_terrain.glsl",
            "#version 450\n#if SHADOW_QUALITY >= 2\nconst int selected = 1;\n#endif\nvoid main() {}",
        ),
        ShaderSourceFile::new(
            "lib/common.glsl",
            "#define SHADOW_QUALITY 0\n",
        ),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
            "SHADOW_QUALITY=2\n",
        ),
    ]);

    let artifact =
        preprocess_artifact_with_runtime_options(&source, "program/dh_terrain.glsl", &[])
            .unwrap();
    let version = artifact.expanded_source().find("#version 450").unwrap();
    let fallback = artifact
        .expanded_source()
        .find("#define SHADOW_QUALITY 2")
        .unwrap();
    assert!(version < fallback);
    assert!(artifact
        .expanded_source()
        .contains("const int selected = 1;"));
}

#[test]
fn source_conditional_can_undefine_a_configured_option() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program.glsl",
            "#ifdef NETHER\n#undef ATMOSPHERIC_FOG\n#endif\n#ifdef ATMOSPHERIC_FOG\nselected\n#endif",
        ),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH,
            "ATMOSPHERIC_FOG=1\n",
        ),
        ShaderSourceFile::new(
            crate::render::shaderpack::source::RUNTIME_ENVIRONMENT_PATH,
            "NETHER=1\n",
        ),
    ]);

    let artifact =
        preprocess_artifact_with_runtime_options(&source, "program.glsl", &[]).unwrap();
    assert!(!artifact.expanded_source().contains("selected"));
    assert!(!artifact
        .defines()
        .iter()
        .any(|(key, _)| key == "ATMOSPHERIC_FOG"));
}

#[test]
fn paired_terrain_sources_expand_both_stage_definitions_from_one_generation() {
    let source = source(vec![
        ShaderSourceFile::new(
            "program/gbuffers_terrain.glsl",
            "#ifdef VERTEX_SHADER\nvertex\n#endif\n#ifdef FRAGMENT_SHADER\nfragment\n#endif",
        ),
        ShaderSourceFile::new(crate::render::shaderpack::source::RUNTIME_OPTIONS_PATH, "QUALITY=2\n"),
    ]);
    let stages = crate::render::shaderpack::contracts::terrain::TerrainSourceStages {
        vertex: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "program/gbuffers_terrain.glsl".to_string(),
            defines: BTreeMap::from([("VERTEX_SHADER".to_string(), "1".to_string())]),
        },
        fragment: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "program/gbuffers_terrain.glsl".to_string(),
            defines: BTreeMap::from([("FRAGMENT_SHADER".to_string(), "1".to_string())]),
        },
    };
    let artifacts = preprocess_terrain_sources(&source, &stages).unwrap();
    assert!(artifacts.vertex.expanded_source().contains("vertex"));
    assert!(!artifacts.vertex.expanded_source().contains("fragment"));
    assert!(artifacts.fragment.expanded_source().contains("fragment"));
    assert!(!artifacts.fragment.expanded_source().contains("vertex"));
    assert_eq!(source.generation(), artifacts.vertex.source_generation());
    assert_eq!(source.generation(), artifacts.fragment.source_generation());
    let summary = artifacts.summary();
    assert_eq!(source.generation(), summary.source_generation);
    assert_eq!("program/gbuffers_terrain.glsl", summary.vertex_entry);
    assert_eq!("program/gbuffers_terrain.glsl", summary.fragment_entry);
    assert_ne!(0, summary.vertex_fingerprint);
    assert_ne!(0, summary.fragment_fingerprint);
}

#[test]
fn distant_horizons_fullscreen_consumers_use_the_dh_source_mode() {
    let source = source(vec![ShaderSourceFile::new(
        "program/deferred1.glsl",
        "#ifdef VERTEX_SHADER\nvertex\n#endif\n#ifdef FRAGMENT_SHADER\n#ifdef DISTANT_HORIZONS\ndh-depth-consumer\n#else\nsky-path\n#endif\n#endif",
    )]);
    let stages = crate::render::shaderpack::contracts::terrain::TerrainSourceStages {
        vertex: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "program/deferred1.glsl".to_string(),
            defines: BTreeMap::from([("VERTEX_SHADER".to_string(), "1".to_string())]),
        },
        fragment: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "program/deferred1.glsl".to_string(),
            defines: BTreeMap::from([("FRAGMENT_SHADER".to_string(), "1".to_string())]),
        },
    };
    let ordinary = preprocess_source_stage_pair(&source, &stages).unwrap();
    let distant = preprocess_distant_horizons_fullscreen_stage_pair(&source, &stages).unwrap();
    assert!(ordinary.fragment.expanded_source().contains("sky-path"));
    assert!(!ordinary
        .fragment
        .expanded_source()
        .contains("dh-depth-consumer"));
    assert!(distant
        .fragment
        .expanded_source()
        .contains("dh-depth-consumer"));
    assert!(!distant.fragment.expanded_source().contains("sky-path"));
    assert!(distant
        .fragment
        .defines()
        .iter()
        .any(|(name, value)| name == "DISTANT_HORIZONS" && value == "1"));
}

#[test]
fn preserves_function_like_macros_and_tracks_them_for_defined_conditions() {
    let source = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#define MIX(a, b) ((a) + (b))\n#if defined(MIX)\nfloat value = MIX(1.0, 2.0);\n#endif",
    )]);
    let artifact = preprocess_artifact(PreprocessInput {
        source: &source,
        entry: "program.glsl",
        defines: &[],
    })
    .unwrap();
    assert!(artifact
        .expanded_source()
        .contains("#define MIX(a, b) ((a) + (b))"));
    assert!(artifact.expanded_source().contains("MIX(1.0, 2.0)"));
    assert_eq!(
        Some(&("MIX".to_string(), "1".to_string())),
        artifact.defines().iter().find(|(key, _)| key == "MIX")
    );
}

#[test]
fn owned_artifact_is_include_free_and_has_a_stable_semantic_identity() {
    let source = source(vec![
        ShaderSourceFile::new("lib/lighting.glsl", "vec3 light(vec3 c) { return c; }"),
        ShaderSourceFile::new(
            "program/terrain.glsl",
            "#version 450\n#include \"../lib/lighting.glsl\"\n#if QUALITY == 2\nvoid main() {}\n#endif",
        ),
    ]);
    let first = preprocess_artifact(PreprocessInput {
        source: &source,
        entry: "program/terrain.glsl",
        defines: &[("QUALITY", "2")],
    })
    .unwrap();
    let second = preprocess_artifact(PreprocessInput {
        source: &source,
        entry: "program/terrain.glsl",
        defines: &[("QUALITY", "2")],
    })
    .unwrap();

    assert_eq!("test-pack", first.pack_name());
    assert_eq!(7, first.source_generation());
    assert_eq!("program/terrain.glsl", first.entry_path());
    assert_eq!(
        &[
            "lib/lighting.glsl".to_string(),
            "program/terrain.glsl".to_string()
        ],
        first.resolved_paths()
    );
    assert!(!first.expanded_source().contains("#include"));
    assert!(first.expanded_source().contains("vec3 light"));
    assert_eq!(first.fingerprint(), second.fingerprint());

    let changed = preprocess_artifact(PreprocessInput {
        source: &source,
        entry: "program/terrain.glsl",
        defines: &[("QUALITY", "1")],
    })
    .unwrap();
    assert_ne!(first.fingerprint(), changed.fingerprint());
    assert!(!changed.expanded_source().contains("void main"));
}

#[test]
fn artifact_cannot_exceed_the_owned_source_budget() {
    let includes = (0..17)
        .map(|_| "#include \"large.glsl\"")
        .collect::<Vec<_>>()
        .join("\n");
    let source = source(vec![
        ShaderSourceFile::new("program.glsl", includes),
        ShaderSourceFile::new("large.glsl", "x".repeat(ShaderPackSource::MAX_FILE_BYTES)),
    ]);
    let error = preprocess_artifact(PreprocessInput {
        source: &source,
        entry: "program.glsl",
        defines: &[],
    })
    .unwrap_err();
    assert!(format!("{error}").contains("expanded shader source exceeds"));
}

#[test]
fn relative_include_canonicalization_allows_siblings_and_rejects_root_escape() {
    let sibling = source(vec![
        ShaderSourceFile::new("lib/value.glsl", "const int VALUE = 7;"),
        ShaderSourceFile::new("program/terrain.glsl", "#include \"../lib/value.glsl\""),
    ]);
    let artifact = preprocess_artifact(PreprocessInput {
        source: &sibling,
        entry: "program/terrain.glsl",
        defines: &[],
    })
    .unwrap();
    assert!(artifact.expanded_source().contains("VALUE = 7"));

    let escaping = source(vec![ShaderSourceFile::new(
        "program/terrain.glsl",
        "#include \"../../outside.glsl\"",
    )]);
    let error = preprocess_artifact(PreprocessInput {
        source: &escaping,
        entry: "program/terrain.glsl",
        defines: &[],
    })
    .unwrap_err();
    assert!(format!("{error}").contains("escapes its pack root"));
}

#[test]
fn rejects_unterminated_and_unsupported_conditional_expressions() {
    let unterminated = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#if FLAG\nvalue",
    )]);
    assert!(preprocess(PreprocessInput {
        source: &unterminated,
        entry: "program.glsl",
        defines: &[("FLAG", "1")],
    })
    .is_err());

    let unsupported = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#if FLAG + 1\nvalue\n#endif",
    )]);
    assert!(preprocess(PreprocessInput {
        source: &unsupported,
        entry: "program.glsl",
        defines: &[("FLAG", "1")],
    })
    .is_err());
}

#[test]
fn resolves_numeric_define_aliases_and_rejects_cycles() {
    let aliases = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#define COLORED_LIGHTING 256\n#define COLORED_LIGHTING_INTERNAL COLORED_LIGHTING\n#if COLORED_LIGHTING_INTERNAL > 0\nselected\n#endif",
    )]);
    let output = preprocess(PreprocessInput {
        source: &aliases,
        entry: "program.glsl",
        defines: &[],
    })
    .unwrap();
    assert!(output.contains("selected"));

    let cycle = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#define A B\n#define B A\n#if A > 0\ninvalid\n#endif",
    )]);
    assert!(preprocess(PreprocessInput {
        source: &cycle,
        entry: "program.glsl",
        defines: &[],
    })
    .is_err());
}

#[test]
fn distant_horizons_preprocessing_injects_owned_material_semantics() {
    let source = source(vec![
        ShaderSourceFile::new(
            "dh_terrain.vsh",
            "#version 130\n#if DH_BLOCK_SAND == 9\nvertex_selected\n#endif",
        ),
        ShaderSourceFile::new(
            "dh_terrain.fsh",
            "#version 130\n#if DH_BLOCK_LEAVES == 1 && DH_BLOCK_LAVA == 6\nfragment_selected\n#endif",
        ),
    ]);
    let stages = TerrainSourceStages {
        vertex: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "dh_terrain.vsh".to_string(),
            defines: BTreeMap::new(),
        },
        fragment: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "dh_terrain.fsh".to_string(),
            defines: BTreeMap::new(),
        },
    };
    let artifacts = preprocess_distant_horizons_sources(&source, &stages).unwrap();
    assert!(artifacts
        .vertex
        .expanded_source()
        .contains("vertex_selected"));
    assert!(artifacts
        .fragment
        .expanded_source()
        .contains("fragment_selected"));
}

#[test]
fn skips_unsupported_conditions_in_inactive_parents_and_rejects_invalid_branch_chains() {
    let inactive_parent = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#if 0\n#if FLAG + 1\nunreachable\n#endif\n#else\nselected\n#endif",
    )]);
    let output = preprocess(PreprocessInput {
        source: &inactive_parent,
        entry: "program.glsl",
        defines: &[],
    })
    .unwrap();
    assert!(output.contains("selected"));
    assert!(!output.contains("unreachable"));

    let repeated_else = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#if 0\na\n#else\nb\n#else\nc\n#endif",
    )]);
    assert!(preprocess(PreprocessInput {
        source: &repeated_else,
        entry: "program.glsl",
        defines: &[],
    })
    .is_err());

    let elif_after_else = source(vec![ShaderSourceFile::new(
        "program.glsl",
        "#if 0\na\n#else\nb\n#elif 1\nc\n#endif",
    )]);
    assert!(preprocess(PreprocessInput {
        source: &elif_after_else,
        entry: "program.glsl",
        defines: &[],
    })
    .is_err());
}

use crate::render::shaderpack::source::*;

fn update(generation: u64, contents: &str) -> ShaderPackSourceUpdate {
    ShaderPackSourceUpdate {
        pack_name: "test-pack".to_string(),
        generation,
        files: vec![ShaderSourceFile::new("lib\\common.glsl", contents)],
    }
}

#[test]
fn source_paths_are_canonical_and_do_not_escape_the_pack_root() {
    let source = ShaderPackSource::new(
        "test-pack",
        1,
        vec![ShaderSourceFile::new("/lib\\common.glsl", "void main() {}")],
    )
    .unwrap();
    assert_eq!(Some("void main() {}"), source.get("/lib/common.glsl"));
    assert!(ShaderPackSource::new(
        "test-pack",
        1,
        vec![ShaderSourceFile::new("../outside.glsl", "")],
    )
    .is_err());
}

#[test]
fn source_store_replaces_only_complete_new_generations() {
    let mut store = ShaderPackSourceStore::default();
    store.apply_update(update(3, "first")).unwrap();
    assert_eq!(Some(3), store.active_generation());
    assert_eq!(
        Some("first"),
        store.active().unwrap().get("lib/common.glsl")
    );

    let malformed = ShaderPackSourceUpdate {
        pack_name: "test-pack".to_string(),
        generation: 4,
        files: vec![ShaderSourceFile::new("../../outside.glsl", "bad")],
    };
    assert!(store.apply_update(malformed).is_err());
    assert_eq!(Some(3), store.active_generation());
    assert_eq!(
        Some("first"),
        store.active().unwrap().get("lib/common.glsl")
    );

    store.apply_update(update(5, "replacement")).unwrap();
    assert_eq!(Some(5), store.active_generation());
    assert_eq!(
        Some("replacement"),
        store.active().unwrap().get("lib/common.glsl")
    );
    assert_eq!(&[4], store.failed_generations());
}

#[test]
fn source_store_bounds_failed_generation_diagnostics() {
    let mut store = ShaderPackSourceStore::default();
    store.apply_update(update(100, "valid")).unwrap();
    for generation in 1..=ShaderPackSourceStore::MAX_FAILED_GENERATIONS as u64 + 4 {
        assert!(store.apply_update(update(generation, "stale")).is_err());
    }
    assert_eq!(
        ShaderPackSourceStore::MAX_FAILED_GENERATIONS,
        store.failed_generations().len()
    );
    assert_eq!(
        5,
        store.failed_generations()[0],
        "oldest diagnostics are retired before unbounded accumulation"
    );
    assert_eq!(
        ShaderPackSourceStore::MAX_FAILED_GENERATIONS as u64 + 4,
        *store.failed_generations().last().unwrap()
    );
    assert_eq!(Some(100), store.active_generation());
}

#[test]
fn saved_options_apply_like_iris_booleans_constants_and_profile_defaults() {
    let source = ShaderPackSource::new(
        "option-kinds",
        3,
        vec![
            ShaderSourceFile::new(
                "lib/common.glsl",
                "#define WAVES //\n//#define GLOW\n#define QUALITY 2 //[1 2 3]\nconst float shadowDistance = 192.0; //[128.0 192.0]\n",
            ),
            ShaderSourceFile::new(
                "program.fsh",
                "#include \"/lib/common.glsl\"\n#ifdef WAVES\nint waves;\n#endif\n#ifdef GLOW\nint glow;\n#endif\nint q = QUALITY;\n",
            ),
            ShaderSourceFile::new(
                RUNTIME_OPTIONS_PATH,
                "GLOW=true\nQUALITY=3\nWAVES=false\nshadowDistance=128.0\n",
            ),
            ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "IS_IRIS=1\nQUALITY=2\n"),
        ],
    )
    .unwrap();
    assert_eq!(
        BTreeMap::from([
            ("GLOW".to_string(), "1".to_string()),
            ("QUALITY".to_string(), "3".to_string()),
        ]),
        source.runtime_option_semantic_defines().unwrap()
    );
    assert_eq!(
        BTreeSet::from(["WAVES".to_string()]),
        source.runtime_disabled_option_defines().unwrap()
    );
    assert_eq!(
        Some(&"128.0".to_string()),
        source.runtime_constant_values().unwrap().get("shadowDistance")
    );
    assert_eq!(
        Some(&"3".to_string()),
        source.runtime_semantic_defines().unwrap().get("QUALITY")
    );
    let expanded = crate::render::shaderpack::source::preprocess::preprocess_artifact_with_runtime_options(
        &source,
        "program.fsh",
        &[],
    )
    .unwrap();
    let text = expanded.expanded_source();
    assert!(!text.contains("int waves;"), "{text}");
    assert!(text.contains("int glow;"), "{text}");
    assert!(text.contains("#define QUALITY 3"), "{text}");
    assert!(text.contains("const float shadowDistance = 128.0;"), "{text}");
}

#[test]
fn runtime_option_snapshot_is_canonical_and_rejects_malformed_entries() {
    let source = ShaderPackSource::new(
        "test-pack",
        1,
        vec![ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "Z_OPTION=low\n# comment\nA_OPTION=1\n",
        )],
    )
    .unwrap();
    assert_eq!(
        BTreeMap::from([
            ("A_OPTION".to_string(), "1".to_string()),
            ("Z_OPTION".to_string(), "low".to_string()),
        ]),
        source.runtime_option_defines().unwrap()
    );

    for contents in [
        "BAD-OPTION=1\n",
        "VALID=\n",
        "VALID=bad value\n",
        "A=1\nA=2\n",
    ] {
        let malformed = ShaderPackSource::new(
            "bad-options",
            2,
            vec![ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, contents)],
        )
        .unwrap();
        assert!(malformed.runtime_option_defines().is_err(), "{contents}");
    }
}

#[test]
fn semantic_snapshot_merges_environment_defines_with_saved_options_winning() {
    let source = ShaderPackSource::new(
        "test-pack",
        3,
        vec![
            ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "QUALITY=2\n"),
            ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "IRIS_VERSION=12000\nIS_IRIS=1\n"),
        ],
    )
    .unwrap();
    assert_eq!(
        BTreeMap::from([
            ("IRIS_VERSION".to_string(), "12000".to_string()),
            ("IS_IRIS".to_string(), "1".to_string()),
            ("QUALITY".to_string(), "2".to_string()),
        ]),
        source.runtime_semantic_defines().unwrap()
    );

    let duplicate = ShaderPackSource::new(
        "test-pack",
        4,
        vec![
            ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "QUALITY=2\n"),
            ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "QUALITY=1\n"),
        ],
    )
    .unwrap();
    // An environment default (such as a profile quality level) yields to
    // the option the user saved, as Iris applies it.
    assert_eq!(
        Some(&"2".to_string()),
        duplicate.runtime_semantic_defines().unwrap().get("QUALITY")
    );
}

#[test]
fn semantic_snapshot_excludes_translation_unit_include_guards() {
    let source = ShaderPackSource::new(
        "test-pack",
        5,
        vec![
            ShaderSourceFile::new(
                "lib/guarded.glsl",
                "#ifndef INCLUDE_GUARDED\n#define INCLUDE_GUARDED\nfloat guarded;\n#endif\n",
            ),
            ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "INCLUDE_GUARDED=1\nQUALITY=2\n"),
            ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "INCLUDE_GUARDED=1\nIS_IRIS=1\n"),
        ],
    )
    .unwrap();

    assert_eq!(
        BTreeMap::from([
            ("IS_IRIS".to_string(), "1".to_string()),
            ("QUALITY".to_string(), "2".to_string()),
        ]),
        source.runtime_semantic_defines().unwrap()
    );
}

#[test]
fn semantic_i32_define_requires_a_present_integer() {
    let source = ShaderPackSource::new(
        "test-pack",
        5,
        vec![ShaderSourceFile::new(
            RUNTIME_ENVIRONMENT_PATH,
            "MC_RENDER_STAGE_TERRAIN_SOLID=8\n",
        )],
    )
    .unwrap();
    assert_eq!(
        8,
        source
            .runtime_semantic_i32("MC_RENDER_STAGE_TERRAIN_SOLID")
            .unwrap()
    );
    assert!(source
        .runtime_semantic_i32("MC_RENDER_STAGE_TERRAIN_CUTOUT")
        .unwrap_err()
        .to_string()
        .contains("missing required semantic define"));

    let malformed = ShaderPackSource::new(
        "test-pack",
        6,
        vec![ShaderSourceFile::new(
            RUNTIME_ENVIRONMENT_PATH,
            "MC_RENDER_STAGE_TERRAIN_SOLID=solid\n",
        )],
    )
    .unwrap();
    assert!(malformed
        .runtime_semantic_i32("MC_RENDER_STAGE_TERRAIN_SOLID")
        .unwrap_err()
        .to_string()
        .contains("must be a signed integer"));
}

#[test]
fn source_store_rolls_back_an_invalid_pack_owned_item_map() {
    let mut store = ShaderPackSourceStore::default();
    store
        .apply_update(ShaderPackSourceUpdate {
            pack_name: "valid".to_string(),
            generation: 1,
            files: vec![ShaderSourceFile::new("item.properties", "item.7=stone\n")],
        })
        .unwrap();
    assert_eq!(Some(1), store.active_generation());
    assert_eq!(
        Some(7),
        store
            .active_item_id_map()
            .unwrap()
            .resolve("minecraft:stone")
            .ok()
    );

    let error = store
        .apply_update(ShaderPackSourceUpdate {
            pack_name: "invalid".to_string(),
            generation: 2,
            files: vec![ShaderSourceFile::new(
                "item.properties",
                "item.7=stone\nitem.8=minecraft:stone\n",
            )],
        })
        .expect_err("ambiguous item mappings must not replace a valid source generation");
    assert!(error.to_string().contains("both 7 and 8"));
    assert_eq!(Some(1), store.active_generation());
    assert_eq!(
        7,
        store
            .active_item_id_map()
            .unwrap()
            .resolve("minecraft:stone")
            .unwrap()
    );
}

#[test]
fn source_store_rolls_back_an_invalid_held_light_policy() {
    let mut store = ShaderPackSourceStore::default();
    store
        .apply_update(ShaderPackSourceUpdate {
            pack_name: "valid".to_string(),
            generation: 1,
            files: vec![ShaderSourceFile::new(
                "shaders.properties",
                "oldHandLight=false\n",
            )],
        })
        .unwrap();
    assert!(!store
        .active_held_light_policy()
        .unwrap()
        .main_hand_uses_stronger_off_hand());

    let error = store
        .apply_update(ShaderPackSourceUpdate {
            pack_name: "invalid".to_string(),
            generation: 2,
            files: vec![ShaderSourceFile::new(
                "shaders.properties",
                "oldHandLight=maybe\n",
            )],
        })
        .expect_err("a malformed held-light policy must not replace a valid generation");
    assert!(error.to_string().contains("oldHandLight"));
    assert_eq!(Some(1), store.active_generation());
    assert!(!store
        .active_held_light_policy()
        .unwrap()
        .main_hand_uses_stronger_off_hand());
}

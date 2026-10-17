//! Capture evidence must survive late frames and remain tied to its source owner.
use super::*;

#[test]
fn captured_fullscreen_uniforms_survive_later_frames_and_publish_after_completion() {
    let (mut gal, frontend, target, mut scene) = empty_source_frame();
    scene.frame_id = 1_009_101;
    scene.correlation_id = 1_009_102;
    // The bundled empty-shadow fixture has no post-terrain stages. Lower a
    // minimal real scalar interface instead of depending on installed packs.
    let source = ShaderPackSource::new("captured-uniforms", 1, vec![
        ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "colortex0=shader_pack_color:primary\n"),
        ShaderSourceFile::new("world0/composite.vsh", "#version 130\nvoid main() { gl_Position=ftransform(); }\n"),
        ShaderSourceFile::new("world0/composite.fsh", "#version 130\nuniform vec3 fogColor;\nuniform float frameTime;\n/* DRAWBUFFERS:0 */\nvoid main() { gl_FragData[0]=vec4(fogColor,frameTime); }\n"),
    ]).unwrap();
    let stages = crate::render::shaderpack::contracts::terrain::TerrainSourceStages {
        vertex: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "world0/composite.vsh".into(),
            defines: Default::default(),
        },
        fragment: crate::render::shaderpack::contracts::terrain::TerrainSourceStage {
            path: "world0/composite.fsh".into(),
            defines: Default::default(),
        },
    };
    let artifacts = crate::render::shaderpack::source::preprocess::preprocess_source_stage_pair(
        &source, &stages,
    )
    .unwrap();
    let bindings = TerrainSourceResourceBindings::from_source(&source).unwrap();
    let lowered = crate::render::shaderpack::lowering::lower_fullscreen_source_pair(
        &artifacts.vertex,
        &artifacts.fragment,
        &bindings,
    )
    .unwrap();
    let opaque = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&bindings)
        .unwrap();
    let program = crate::render::shaderpack::programs::prepare_lowered_fullscreen_source_program(
        source.name(),
        1,
        "world0/composite.fsh",
        &lowered,
        &opaque,
    )
    .unwrap();
    let uniforms = TerrainSourceUniformFrame {
        fog_color: Some([0.25, 0.5, 0.75]),
        frame_time_seconds: Some(1.0 / 60.0),
        ..Default::default()
    };
    let mut bytes = program.pack_scalar_uniforms(&uniforms).unwrap();
    let original = bytes.clone();
    let root = std::env::temp_dir().join(format!(
        "mattmc-source-uniform-capture-{}",
        std::process::id()
    ));
    std::fs::create_dir_all(&root).unwrap();
    let request_path = root.join("request.properties");
    GameplayAttachmentCaptureRequest {
        frame_id: scene.frame_id,
        correlation_id: scene.correlation_id,
        deterministic_rendered_frame_index: 41,
        source_selected_capture: true,
        source_selected_pending: false,
        required_entity_mesh: false,
    }
    .write_atomic(&request_path)
    .unwrap();
    let _dir = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR",
        root.to_str().unwrap(),
    );
    let _request = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST",
        request_path.to_str().unwrap(),
    );
    let _minimum = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_MESH_INSTANCES",
        "0",
    );
    let _audit = crate::core::environment::scoped_override("MATTMC_GRAPHICS_AUDIT", "1");
    let _receipt = crate::core::environment::scoped_override(
        "MATTMC_RUST_SELECTED_SOURCE_FULLSCREEN_UNIFORM_RECEIPT",
        "1",
    );
    let mut capture = GameplayAttachmentCapture::select_source(&scene, 1, 1, test_conventions())
        .unwrap()
        .unwrap();
    let mut wrong = scene.clone();
    wrong.correlation_id += 1;
    frontend.retain_captured_fullscreen_uniform_receipts(
        &wrong,
        &mut capture,
        [(&program, bytes.as_slice())],
    );
    assert!(capture.fullscreen_uniform_receipts.is_empty());
    capture.source_selected = false;
    frontend.retain_captured_fullscreen_uniform_receipts(
        &scene,
        &mut capture,
        [(&program, bytes.as_slice())],
    );
    assert!(
        capture.fullscreen_uniform_receipts.is_empty(),
        "ordinary-route captures cannot claim source blocks"
    );
    capture.source_selected = true;
    frontend.retain_captured_fullscreen_uniform_receipts(
        &scene,
        &mut capture,
        [(&program, bytes.as_slice())],
    );
    assert_eq!(1, capture.fullscreen_uniform_receipts.len());
    let retained = capture.fullscreen_uniform_receipts.clone();
    bytes.fill(0);
    wrong.frame_id += 10_000;
    wrong.correlation_id += 10_000;
    frontend.retain_captured_fullscreen_uniform_receipts(
        &wrong,
        &mut capture,
        [(&program, bytes.as_slice())],
    );
    assert_eq!(retained, capture.fullscreen_uniform_receipts);
    let receipt_dir = root.join(format!("source-uniforms-frame-{}", scene.frame_id));
    assert!(
        !receipt_dir.exists(),
        "do not publish before completed readback artifacts"
    );
    capture
        .write_artifacts(
            &mut gal,
            Vec::new(),
            42,
            &WorldPrimitiveSubmitStats::default(),
        )
        .unwrap();
    let manifest: serde_json::Value = serde_json::from_slice(
        &std::fs::read(root.join(format!(
            "gameplay-attachments-frame-{}.json",
            scene.frame_id
        )))
        .unwrap(),
    )
    .unwrap();
    let files = manifest["source_uniform_receipts"].as_array().unwrap();
    assert_eq!(1, files.len());
    let published: serde_json::Value =
        serde_json::from_slice(&std::fs::read(root.join(files[0].as_str().unwrap())).unwrap())
            .unwrap();
    assert_eq!(scene.frame_id, published["frame_id"]);
    assert_eq!(scene.correlation_id, published["correlation_id"]);
    assert_eq!(42, published["gal_submission_id"]);
    for field in published["fields"].as_array().unwrap() {
        let offset = field["offset"].as_u64().unwrap() as usize;
        let size = field["size"].as_u64().unwrap() as usize;
        let expected = original[offset..offset + size]
            .chunks_exact(4)
            .map(|word| u32::from_le_bytes(word.try_into().unwrap()))
            .collect::<Vec<_>>();
        assert_eq!(serde_json::json!(expected), field["words"]);
    }
    cleanup(gal, frontend, target);
    std::fs::remove_dir_all(root).unwrap();
}

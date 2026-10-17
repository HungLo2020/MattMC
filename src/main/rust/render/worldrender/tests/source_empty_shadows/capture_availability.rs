//! A full dump must not turn an unused compatibility attachment into a reader.
use super::*;

#[test]
fn gameplay_capture_requires_a_translucent_snapshot_producer() {
    let (mut gal, mut frontend, target, mut scene) = empty_source_frame();
    frontend
        .append_frame_ops_inner_with_source_preparation(
            &mut gal,
            1,
            target,
            scene.clone(),
            true,
            RasterYDirection::Up,
            true,
        )
        .unwrap();
    let root = std::env::temp_dir().join(format!(
        "mattmc-capture-availability-{}",
        std::process::id()
    ));
    std::fs::create_dir_all(&root).unwrap();
    let request = root.join("request.properties");
    let _dir = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR",
        root.to_str().unwrap(),
    );
    let _request = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST",
        request.to_str().unwrap(),
    );
    let _minimum = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_MESH_INSTANCES",
        "0",
    );
    let _full = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY",
        "0",
    );
    for (index, prior_submission, current_producer) in
        [(0, false, false), (1, true, false), (2, false, true)]
    {
        scene.frame_id = 1_010_100 + index;
        scene.correlation_id = scene.frame_id;
        GameplayAttachmentCaptureRequest {
            frame_id: scene.frame_id,
            correlation_id: scene.correlation_id,
            deterministic_rendered_frame_index: 41,
            source_selected_capture: true,
            source_selected_pending: false,
            required_entity_mesh: false,
        }
        .write_atomic(&request)
        .unwrap();
        let mut capture =
            GameplayAttachmentCapture::select_source(&scene, 1, 1, test_conventions())
                .unwrap()
                .unwrap();
        let graph = frontend.g_buffer_resources.as_mut().unwrap();
        graph.translucent_capture_initialized = prior_submission;
        let mut ops = Vec::new();
        if current_producer {
            // The already-recorded pass is in this capture's same submission;
            // its initialized flag must remain false until submit succeeds.
            ops.push(CommandOp::BeginPass {
                pass: graph.translucent_capture_pass,
                target: graph.translucent_capture_target,
                colors: vec![PassAttachment {
                    view: graph.translucent_capture_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: Some(ClearColor {
                        r: 0.0,
                        g: 0.0,
                        b: 0.0,
                        a: 0.0,
                    }),
                }],
                depth_stencil: Some(PassAttachment {
                    view: graph.translucent_capture_depth_view,
                    load_op: AttachmentLoadOp::Clear,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            ops.push(CommandOp::EndPass);
        }
        capture
            .append_ops(&mut gal, &mut ops, Some(graph), None)
            .unwrap();
        for (name, texture) in [
            ("translucent_capture", graph.translucent_capture_texture),
            (
                "translucent_capture_depth",
                graph.translucent_capture_depth_texture,
            ),
        ] {
            let available = prior_submission || current_producer;
            assert_eq!(
                available,
                capture.readbacks.contains_key(name),
                "{name}: no producer means no readback"
            );
            assert_eq!(
                !available,
                capture.unavailable_attachments.contains_key(name),
                "absence must remain explicit in attachment evidence"
            );
            assert_eq!(available, ops.iter().any(|op| matches!(op,
                CommandOp::Barrier(barrier) if barrier.resource == texture && barrier.after == TextureUsageState::TransferSrc)),
                "do not manufacture a sampled/transfer layout for an unproduced image");
        }
        capture.discard(&mut gal);
    }
    cleanup(gal, frontend, target);
    std::fs::remove_dir_all(root).unwrap();
}

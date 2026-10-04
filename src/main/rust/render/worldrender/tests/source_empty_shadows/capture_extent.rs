//! Shadow diagnostics must cover the pack texture, including texels outside the viewport.

use super::*;

#[test]
fn gameplay_shadow_capture_uses_pack_extent_instead_of_screen_extent() {
    let (mut gal, mut frontend, target, mut scene) = empty_source_frame();
    scene.frame_id = 1_009_031;
    scene.correlation_id = 1_009_032;
    frontend.append_frame_ops_inner_with_source_preparation(
        &mut gal, 1, target, scene.clone(), true, RasterYDirection::Up, true,
    ).unwrap();
    let root = std::env::temp_dir().join(format!("mattmc-shadow-capture-{}", std::process::id()));
    std::fs::create_dir_all(&root).unwrap();
    let request_path = root.join("request.properties");
    GameplayAttachmentCaptureRequest {
        frame_id: scene.frame_id,
        correlation_id: scene.correlation_id,
        deterministic_rendered_frame_index: 41,
        source_selected_capture: true,
        source_selected_pending: false,
        required_entity_mesh: false,
    }.write_atomic(&request_path).unwrap();
    let _dir = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_DIR", root.to_str().unwrap());
    let _request = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_REQUEST", request_path.to_str().unwrap());
    let _minimum = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_MIN_MESH_INSTANCES", "0");
    let _full = crate::core::environment::scoped_override(
        "MATTMC_RUST_WHOLE_FRAME_ATTACHMENT_FINAL_ONLY", "0");
    let mut capture = GameplayAttachmentCapture::select_source(
        &scene, 1, 1, test_conventions()).unwrap().unwrap();
    let graph = frontend.g_buffer_resources.as_ref().unwrap();
    assert_ne!(graph.extent, graph.shadow_extent, "fixture must exercise a separate pack resolution");
    let mut ops = Vec::new();
    capture.append_ops(&mut gal, &mut ops, Some(graph), None).unwrap();
    let copy = ops.iter().find_map(|op| match op {
        CommandOp::CopyTextureToBuffer(copy) if copy.texture == graph.shadow_depth_texture => Some(copy),
        _ => None,
    }).unwrap();
    assert_eq!(graph.shadow_extent, copy.extent, "capture the complete shader-pack shadow map");
    assert_eq!(graph.shadow_extent.width * 4, copy.bytes_per_row);
    assert_eq!(graph.shadow_extent.height, copy.rows_per_image);
    assert_eq!(Some(&graph.shadow_extent), capture.readback_extents.get("shadow_depth"),
        "PNG and evidence metadata must retain the texture's extent through completion");
    assert!(ops.iter().any(|op| matches!(op,
        CommandOp::CopyTextureToBuffer(copy) if copy.texture == graph.composite1_texture
            && copy.extent == graph.extent)), "screen attachments must keep their screen extent");
    assert!(ops.iter().any(|op| matches!(op,
        CommandOp::HostReadBuffer { buffer, size, .. }
        if *buffer == copy.buffer && *size == u64::from(graph.shadow_extent.width) * u64::from(graph.shadow_extent.height) * 4)));
    assert!(ops.iter().any(|op| matches!(op,
        CommandOp::Barrier(barrier) if barrier.resource == graph.shadow_depth_texture
            && barrier.before == TextureUsageState::TransferSrc && barrier.after == TextureUsageState::ShaderRead)));
    capture.discard(&mut gal);
    cleanup(gal, frontend, target);
    std::fs::remove_dir_all(root).unwrap();
}

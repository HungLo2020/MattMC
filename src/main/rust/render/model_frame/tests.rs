use super::*;
use instance::Input;

fn input(first_pose: u32) -> Input {
    Input {
        rig: 9,
        first_pose,
        flags: 0,
        depth: 1,
        cull: 2,
        color: 0xff123456,
        entity_color: 0x7f112233,
        outline: 0xff000001,
        packed_light: 0xf000f0,
        width: 1280,
        height: 720,
        block_id: -1,
        order: -2,
        order_mode: 1,
        foil_mode: 0,
        foil_clock: 0,
        foil_speed: 0.0,
        foil_strength: 0.0,
        reserved: 0,
        transform: [1.0; 16],
        culling_mode: 1,
        culling_flags: 5,
        bounds: [1.0, 2.0, 3.0, 4.0, 5.0, 6.0],
        leash_bounds: [0.0; 6],
        camera: [7.0; 3],
    }
}

#[test]
fn layout_and_partial_pose_reservations_are_bounded() {
    assert_eq!(std::mem::size_of::<Header>(), 48);
    assert_eq!(std::mem::offset_of!(Header, instance_count), 24);
    assert_eq!(std::mem::size_of::<Input>(), 280);
    assert_eq!(std::mem::offset_of!(Input, transform), 88);
    assert_eq!(std::mem::offset_of!(Input, camera), 256);
    let mut frame = Frame::default();
    assert_eq!(frame.reserve_poses(3), Some(0));
    assert_eq!(frame.header.pose_count, 0);
    assert!(!frame.commit_poses(1, 3));
    assert!(!frame.commit_poses(0, 2));
    assert_eq!(frame.reserve_poses(1), Some(0));
    assert!(frame.commit_poses(0, 1));
    assert_eq!(frame.reserve_poses(MAX_NODES + 1), None);
    assert_eq!(frame.header.pose_count, 1);
    assert_eq!(frame.reserve_poses(0), None);
    assert_eq!(frame.reserve_poses(2), Some(1));
    frame.seal();
    assert_eq!(frame.poses.len(), 1);
    assert!(!frame.commit_poses(1, 2));
    assert_eq!(frame.reserve_poses(1), None);
}

#[test]
fn emitted_instance_and_variants_preserve_original_facts() {
    let source = input(13);
    let original = instance::produce(&source).unwrap();
    assert_eq!(original.stratum, 67);
    assert_eq!(original.mesh_generation, 14);
    assert_eq!(original.winding, 1);
    assert_eq!(original.mesh_section_index, u32::MAX);
    assert_eq!(original.transform, source.transform);
    assert_eq!(original.entity_culling_bounds, source.bounds);
    assert_eq!(original.model_submission_order, -2);
    assert_eq!(original.flags, 0x4000_0000);
    let mut expected = original;
    expected.viewport_width = 640;
    expected.viewport_height = 480;
    assert_eq!(
        instance::variant(original, 1, 640, 480, 0.0, 0.0),
        Some(expected)
    );
    assert_eq!(original.viewport_width, 1280);
    let shadow = instance::variant(original, 4, 0, 0, 0.0, 0.0).unwrap();
    assert_eq!(shadow.stratum, 69);
    assert_eq!(shadow.outline_color_argb, 0);
    assert_eq!(shadow.model_submission_order_mode, 0);
    assert_eq!(shadow.entity_culling_bounds, source.bounds);
    assert!(instance::variant(shadow, 3, 10, 1, 0.0, 0.0).is_none());
    for mode in 1..=4 {
        let foil = instance::variant(original, 5, mode, 100, 0.5, 0.75).unwrap();
        assert_eq!(foil.item_foil_mode, mode as u32);
        assert_eq!(foil.item_foil_strength, 0.75);
        assert!(instance::variant(foil, 4, 0, 0, 0.0, 0.0).is_none());
    }
    assert!(instance::variant(original, 2, -1, 0, 0.0, 0.0).is_none());
}

#[test]
fn semantic_rejection_preserves_pose_prefix_without_an_instance() {
    let mut frame = Frame::default();
    frame.reserve_poses(2).unwrap();
    assert!(frame.commit_poses(0, 2));
    let mut source = input(0);
    source.packed_light = -1;
    assert_eq!(frame.append(&source), None);
    assert_eq!(frame.header.pose_count, 2);
    assert_eq!(frame.header.instance_count, 0);
    source.packed_light = 0;
    source.flags = 4;
    assert!(frame.append(&source).is_some());
    source.flags = 12;
    assert!(frame.append(&source).is_none());
    source.flags = 0;
    source.depth = 3;
    source.foil_mode = 2;
    assert!(frame.append(&source).is_none());
    source.depth = 1;
    source.block_id = 0;
    assert!(frame.append(&source).is_none());
}

#[test]
fn rollback_growth_and_sealing_keep_stable_instance_indices() {
    let mut frame = Frame::default();
    frame.reserve_poses(1).unwrap();
    frame.commit_poses(0, 1);
    let first = frame.append(&input(0)).unwrap();
    let original = frame.instances[first];
    for _ in 0..1024 {
        let pose = frame.reserve_poses(1).unwrap();
        frame.commit_poses(pose, 1);
        frame.append(&input(pose as u32)).unwrap();
    }
    assert_eq!(frame.instances[first], original);
    assert!(frame.rollback(1, 1));
    let invalidated = frame.next_version;
    let replacement = frame.append(&input(0)).unwrap();
    assert!(frame.versions[replacement] > invalidated);
    assert!(frame.rollback(1, 1));
    assert_eq!(frame.header.instance_count, 1);
    assert_eq!(frame.header.pose_count, 1);
    assert!(!frame.rollback(2, 1));
    frame.seal();
    assert!(!frame.rollback(0, 0));
    assert!(frame.append(&input(0)).is_none());
    assert!(frame.variant(first, 1, 1920, 1080, 0.0, 0.0).is_some());
    assert_eq!(frame.instances[first], original);
}

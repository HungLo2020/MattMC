use super::*;
fn frame(now: i64) -> Inputs {
    Inputs {
        enabled: true,
        now_millis: now,
        speed: 6.0,
        camera: [0.0; 3],
        look: [0.0, 0.0, 1.0],
        radius_chunks: 128,
        max_height: 320,
    }
}
#[test]
fn disabled_interval_retains_time_and_reentry_moves_the_full_elapsed_interval() {
    let mut group = Group::new(2048, [0, 0], 1000).unwrap();
    assert_eq!(
        group.prepare(frame(2000)).origin.unwrap(),
        [1018.0, 520.0, 1024.0]
    );
    let mut off = frame(3000);
    off.enabled = false;
    assert!(group.prepare(off).origin.is_none());
    assert_eq!(
        group.prepare(frame(4000)).origin.unwrap(),
        [1006.0, 520.0, 1024.0]
    );
    assert!(!group.color_changed(0xffffffff));
    assert!(group.color_changed(0xffcccccc));
    assert!(!group.color_changed(0xffcccccc));
}
#[test]
fn center_nine_groups_remain_admitted_even_with_no_distance_and_a_backward_look() {
    for x in -1..=1 {
        for z in -1..=1 {
            let mut group = Group::new(2048, [x, z], 0).unwrap();
            let mut f = frame(0);
            f.radius_chunks = 0;
            f.look = [0.0, 0.0, -1.0];
            assert!(group.prepare(f).active);
        }
    }
}
#[test]
fn negative_exact_tile_boundary_preserves_the_original_extra_width_adjustment() {
    let mut group = Group::new(2048, [0, 0], 0).unwrap();
    let mut f = frame(0);
    f.camera = [-2048.0, 0.0, -0.5];
    assert_eq!(group.prepare(f).origin.unwrap(), [-3072.0, 520.0, 1024.0]);
}
#[test]
fn outer_groups_are_rejected_when_all_corners_are_outside_distance_or_behind() {
    let mut group = Group::new(2048, [0, 2], 0).unwrap();
    let mut f = frame(0);
    f.radius_chunks = 0;
    assert!(!group.prepare(f).active);
    f.radius_chunks = 1024;
    f.look = [0.0, 0.0, -1.0];
    assert!(!group.prepare(f).active);
    f.look = [0.0, 0.0, 1.0];
    assert!(group.prepare(f).active);
}

use super::*;
#[test]
fn no_transform_ignores_authored_vectors_and_centers_both_hands() {
    let o = Owner::new([f32::NAN; 9], true).unwrap();
    for p in o.poses {
        assert_eq!(&p.model[12..], &[-0.5, -0.5, -0.5, 1.]);
        assert_eq!(p.trusted_normals, 1);
    }
    assert_eq!(std::mem::size_of::<Owner>(), 208);
}
#[test]
fn immutable_pose_captures_mirroring_and_declines_nonfinite_results() {
    let o = Owner::new([0., 0., 0., 2., 3., 4., 1., 1., 1.], false).unwrap();
    assert_eq!(&o.poses[0].model[12..], &[1.5, 2.5, 3.5, 1.]);
    assert_eq!(&o.poses[1].model[12..], &[-2.5, 2.5, 3.5, 1.]);
    assert!(Owner::new([0., 0., 0., 0., 0., 0., 0., 1., 1.], false).is_none());
    assert!(Owner::new([f32::INFINITY; 9], false).is_none());
    let p = Owner::new([0., 0., 0., 0., 0., 0., -2., -2., -2.], false)
        .unwrap()
        .poses[0];
    assert_eq!(p.trusted_normals, 1);
    assert_eq!(p.normal[0], -1.);
}

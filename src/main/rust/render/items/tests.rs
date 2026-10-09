use super::*;
#[test]
fn no_transform_ignores_authored_vectors_and_centers_both_hands() {
    let o = Owner::new([f32::NAN; 9], true).unwrap();
    for p in o.poses {
        assert_eq!(&p.model[12..], &[-0.5, -0.5, -0.5, 1.]);
        assert_eq!(p.trusted_normals, 1);
    }
    assert_eq!(std::mem::size_of::<Owner>(), 340);
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

#[test]
fn world_owner_keeps_gui_prefix_and_declines_unknown_operations() {
    assert_eq!(std::mem::offset_of!(Owner,safe_for_world),336);
    let o=Owner::new([0.,0.,0.,2.,3.,4.,1.,1.,1.],false).unwrap();
    let parent=world_pose::ResolvedPose {model:[1.,0.,0.,0.,0.,1.,0.,0.,0.,0.,1.,0.,10.,20.,30.,1.],normal:[1.,0.,0.,0.,1.,0.,0.,0.,1.],trusted:true};
    assert_eq!(o.resolve(1,parent,26).unwrap().model[12..],[11.5,22.5,33.5,1.]);
    assert_eq!(o.resolve(3,parent,26).unwrap().model[12..],[11.5,22.5,33.5,1.]);
    assert!(o.resolve(0,parent,26).is_none());assert!(o.resolve(5,parent,26).is_none());assert!(o.resolve(1,parent,0).is_none());
}

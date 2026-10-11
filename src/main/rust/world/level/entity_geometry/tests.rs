use super::*;

#[test]
fn java_cpu_view_layout_is_stable() {
    use std::mem::{offset_of, size_of};
    assert_eq!(size_of::<View>(), 152);
    assert_eq!(offset_of!(View, culling), 72);
    assert_eq!(offset_of!(View, block), 120);
    assert_eq!(offset_of!(View, chunk), 132);
    assert_eq!(offset_of!(View, visible), 140);
    assert_eq!(offset_of!(View, revision), 144);
    assert_eq!(size_of::<Header>(), 32);
    assert_eq!(offset_of!(Header, query_revision), 24);
}

#[test]
fn primary_move_preserves_nan_signed_zero_and_floor_edges() {
    let v = View::new([0.0; 3], [0.0; 6]);
    assert_eq!(v.move_to([-0.0, 0.0, 0.0]), 0);
    assert_eq!(v.move_to([-0.1, 16.0, 32.0]), 7);
    assert_eq!(
        v.block.each_ref().map(|v| v.load(Ordering::Relaxed)),
        [-1, 16, 32]
    );
    assert_eq!(v.move_to([-0.2, 16.5, 32.5]), 1);
    assert_eq!(v.move_to([f64::NAN, f64::NEG_INFINITY, f64::INFINITY]), 7);
    assert_eq!(
        v.block.each_ref().map(|v| v.load(Ordering::Relaxed)),
        [0, i32::MAX, i32::MAX]
    );
    assert_ne!(v.move_to([f64::NAN, f64::NEG_INFINITY, f64::INFINITY]), 0);
}
#[test]
fn sparse_generations_and_dense_removal_do_not_alias_leases() {
    let mut store = Store::new();
    let a = store
        .insert(Arc::new(View::new([0.0; 3], [0.0; 6])))
        .unwrap();
    let b = store
        .insert(Arc::new(View::new([1.0; 3], [0.0; 6])))
        .unwrap();
    assert!(store.remove(a));
    assert_eq!(store.index(b), Some(0));
    assert!(!store.remove(a));
    let c = store
        .insert(Arc::new(View::new([2.0; 3], [0.0; 6])))
        .unwrap();
    assert_ne!(a, c);
    assert_eq!(a as u32, c as u32);
    assert_eq!(store.index(a), None);
}
#[test]
fn transfer_retains_primary_view_and_closing_reclaims_each_world() {
    unsafe {
        let a = mattmc_entity_geometry_world_create();
        let b = mattmc_entity_geometry_world_create();
        let body = mattmc_entity_geometry_create(a, 0., 0., 0., -0.2, -0.2, -0.2, 0.2, 0.2, 0.2);
        let view = mattmc_entity_geometry_view(body);
        assert_eq!((*a).header.count.load(Ordering::Acquire), 1);
        assert_eq!(mattmc_entity_geometry_transfer(body, b), 1);
        assert_eq!(mattmc_entity_geometry_view(body), view);
        assert_eq!((*a).header.count.load(Ordering::Acquire), 0);
        assert_eq!((*b).header.count.load(Ordering::Acquire), 1);
        let matrix = [
            1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1., 0., 0., 0., 0., 1.,
        ];
        assert_ne!(
            mattmc_entity_geometry_query(b, matrix.as_ptr(), 16, 0., 0., 0.),
            0
        );
        assert_eq!((*view).visible.load(Ordering::Acquire), 1);
        assert_eq!(
            mattmc_entity_geometry_change(body, 0, 10., 10., 10., 0., 0., 0.),
            3
        );
        // Position alone does not translate the supplied physical bounding box.
        assert_ne!(
            mattmc_entity_geometry_query(b, matrix.as_ptr(), 16, 0., 0., 0.),
            0
        );
        assert_eq!((*view).visible.load(Ordering::Acquire), 1);
        mattmc_entity_geometry_change(body, 1, f64::NAN, 0., 0., 0., 0., 0.);
        mattmc_entity_geometry_query(b, matrix.as_ptr(), 16, 0., 0., 0.);
        assert_eq!((*view).visible.load(Ordering::Acquire), 0);
        mattmc_entity_geometry_release(body);
        assert_eq!((*b).header.count.load(Ordering::Acquire), 0);
        mattmc_entity_geometry_world_release(a);
        mattmc_entity_geometry_world_release(b);
    }
}

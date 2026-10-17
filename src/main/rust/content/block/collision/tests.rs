use super::*;
#[test]
fn cached_collision_and_dh_policy_match_actual_frozen_for_all_admitted_states() {
    let fixture = crate::world::level::chunk::dh_heightmaps::fixture::load();
    let mut admitted = 0;
    for (id, &(solid, opacity)) in fixture.expected.iter().enumerate() {
        if let Some(actual) = fixture.catalog.dh(id as u32) {
            assert_eq!(
                (actual.solid, actual.opacity),
                (solid, opacity),
                "state {id}"
            );
            admitted += 1;
        }
    }
    assert_eq!(admitted, 31532);
}
#[test]
fn malformed_and_contextual_geometry_are_rejected() {
    let facts = |_| (StateFlags::default(), 0);
    assert!(Catalog::new(vec![0], vec![vec![[0., 0., 0., f64::NAN, 1., 1.]]], facts).is_none());
    assert!(Catalog::new(vec![1], vec![vec![]], facts).is_none());
    assert!(Catalog::new(vec![-2], vec![vec![]], facts).is_none());
    let custom = Catalog::new(vec![0, -1], vec![vec![]], |_| (StateFlags::CUSTOM, 0)).unwrap();
    assert_eq!(custom.dh(0), None);
    assert_eq!(custom.dh(1), None);
    assert_eq!(custom.dh(u32::MAX), None);
}

use super::*;
const LEGACY: Policy = Policy {
    exact: false,
    overlap_errors: false,
    curvature: false,
};
fn quad(x: i16, identity: i32) -> Quad {
    Quad {
        x,
        y: 8,
        z: 3,
        width: 1,
        height: 2,
        direction: 2,
        material: 2,
        sky: 15,
        block: 0,
        error: 0,
        variant: 1,
        color: 0xff336699,
        identity,
        position: 17,
    }
}

#[test]
fn projection_layout_is_complete() {
    assert_eq!(std::mem::size_of::<Quad>(), 32);
    assert_eq!(std::mem::offset_of!(Quad, color), 16);
    assert_eq!(std::mem::offset_of!(Quad, identity), 20);
    assert_eq!(std::mem::offset_of!(Quad, position), 24);
}
#[test]
fn premerge_emissions_and_exact_identity_outcomes() {
    let mut builder = Builder::new(true);
    assert!(builder.add(quad(0, 1), true, LEGACY));
    assert!(builder.add(quad(1, 2), true, LEGACY));
    assert_eq!(builder.premerges, 1);
    assert_eq!(builder.coverage, [2, 0, 0, 2, 0, 0]);
    assert_eq!(builder.lists[0][2][0].identity, -1);
    let mut exact = Builder::new(true);
    let policy = Policy {
        exact: true,
        ..LEGACY
    };
    exact.add(quad(0, 1), true, policy);
    exact.add(quad(1, 2), true, policy);
    exact.merge(policy);
    assert_eq!(exact.lists[0][2].len(), 2);
}
#[test]
fn overlap_marks_both_and_curvature_uses_floor_division() {
    let mut left = quad(15, 1);
    let mut right = quad(16, 1);
    assert!(!left.merge(
        &mut right,
        true,
        Policy {
            curvature: true,
            ..LEGACY
        }
    ));
    assert!(left.merge(&mut right, true, LEGACY));
    let mut overlap = quad(15, 1);
    assert!(!left.merge(
        &mut overlap,
        true,
        Policy {
            overlap_errors: true,
            ..LEGACY
        }
    ));
    assert_eq!((left.error, overlap.error), (1, 1));
}
#[test]
fn water_classification_and_invalid_admission() {
    let mut owner = Builder::new(true);
    let mut water = quad(0, 1);
    water.material = 12;
    owner.add(water, false, LEGACY);
    assert_eq!(owner.lists[1][2].len(), 1);
    water.width = 0;
    assert!(!owner.add(water, false, LEGACY));
    assert_eq!(owner.coverage, [1, 0, 0, 0, 0, 0]);
}

#[test]
fn negative_curvature_boundaries_and_variant_positions() {
    let curvature = Policy {
        curvature: true,
        ..LEGACY
    };
    let mut left = quad(-17, 1);
    let mut right = quad(-16, 1);
    assert!(!left.merge(&mut right, true, curvature));
    assert!(left.merge(&mut right, true, LEGACY));
    let mut left = quad(-16, 1);
    let mut right = quad(-15, 1);
    right.position = 18;
    assert!(!left.merge(
        &mut right,
        true,
        Policy {
            exact: true,
            ..curvature
        }
    ));
    assert!(left.merge(&mut right, true, curvature));
    assert_eq!((left.variant, left.position), (2, 0));
}
#[test]
fn stable_equal_keys_keep_original_identity_order() {
    let mut owner = Builder::new(false);
    owner.add(quad(-1, 7), false, LEGACY);
    owner.add(quad(-1, 8), false, LEGACY);
    owner.merge(LEGACY);
    assert_eq!(
        owner.lists[0][2]
            .iter()
            .map(|q| q.identity)
            .collect::<Vec<_>>(),
        [7, 8]
    );
}

#[test]
fn cached_permutation_preserves_signed_keys_and_large_stable_fallback() {
    let exact = Policy {
        exact: true,
        ..LEGACY
    };
    for length in [3usize, 255, 65_536, 65_537] {
        let mut input: Vec<_> = (0..length)
            .map(|i| {
                let mut q = quad((i.wrapping_mul(31)) as i16, i as i32 + 1);
                q.y = (i.wrapping_mul(7)) as i16;
                q.z = (i.wrapping_mul(13)) as i16;
                q
            })
            .collect();
        input.reverse();
        let mut expected = input.clone();
        expected.sort_by_key(|q| q.key(true));
        Builder::merge_list(&mut input, &mut Vec::new(), true, exact);
        assert_eq!(input, expected, "length {length}");
    }
}

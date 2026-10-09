use super::*;
use crate::world::level::chunk::live;

fn air() -> Policy {
    Policy {
        air: true,
        ticking: false,
        fluid: false,
        fluid_ticks: false,
    }
}
fn water() -> Policy {
    Policy {
        air: false,
        ticking: false,
        fluid: true,
        fluid_ticks: false,
    }
}
fn lava() -> Policy {
    Policy {
        air: false,
        ticking: false,
        fluid: true,
        fluid_ticks: true,
    }
}

#[test]
fn signed_lanes_wrap_independently_and_network_replaces_only_one() {
    let counts = Owner::new(0);
    counts.set([32767, -32768, 65535]);
    assert_eq!(counts.values(), [32767, -32768, -1]);
    counts.adjust(0, 1, false);
    counts.adjust(1, -1, false);
    assert_eq!(counts.values(), [-32768, 32767, -1]);
    counts.adjust(0, 17, true);
    assert_eq!(counts.values(), [17, 32767, -1]);
    assert_eq!(std::mem::size_of::<Owner>(), 8);
    assert_eq!(std::mem::align_of::<Owner>(), 8);
}
#[test]
fn incremental_and_recount_fluid_rules_and_independent_alias_counters() {
    let policies = [Some(air()), Some(water()), Some(lava())];
    let live = live::Owner::load(0, 0, &[1], &[], 3, 15).unwrap();
    let counts = Owner::new(0);
    assert!(live.recount(&counts, &policies));
    assert_eq!(counts.values(), [8192, 0, 0]);
    assert_eq!(live.write_counts(&counts, 14, 2, &policies).unwrap().0, 1);
    assert_eq!(counts.values(), [8192, 0, 0]); // Both fluids increment the fluid lane.
    assert!(live.recount(&counts, &policies));
    assert_eq!(counts.values(), [8192, 0, 1]); // Only lava ticks when recounted.
    let other = Owner::new(0);
    live.write_counts(&other, 14, 0, &policies).unwrap();
    assert_eq!(other.values(), [-1, 0, -1]);
    assert_eq!(counts.values(), [8192, 0, 1]);
    let (_, _, _, _, captured) = live.stage_with_counts(&counts, 3, 15).unwrap();
    assert_eq!(captured, [8192, 0, 1]);
}
#[test]
fn declined_policy_or_index_keeps_storage_and_counters_unchanged() {
    let policies = [Some(air()), None, Some(lava())];
    let live = live::Owner::load(0, 0, &[0], &[], 3, 15).unwrap();
    let counts = Owner::new(123);
    assert!(live.write_counts(&counts, 0, 1, &policies).is_none());
    assert!(live.write_counts(&counts, 4096, 2, &policies).is_none());
    assert_eq!(counts.packed(), 123);
    assert_eq!(live.stage_with_counts(&counts, 3, 15).unwrap().2, [0]);
    let custom = live::Owner::load(0, 0, &[1], &[], 3, 15).unwrap();
    assert!(!custom.recount(&counts, &policies));
    assert_eq!(counts.packed(), 123);
}

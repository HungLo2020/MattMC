use super::*;
use crate::content::block::StateFlags;
fn owner(ids: &[u32]) -> live::Owner {
    let mut words = vec![0u64; 1024];
    for (index, &id) in ids.iter().enumerate() {
        words[index / 4] |= (id as u64) << ((index % 4) * 15);
    }
    live::Owner::load(15, 9, &[], &words, 31809, 15).unwrap()
}
#[test]
fn all_saved_frozen_height_fields_match_direct_native_live_reads() {
    let fixture = super::fixture::load();
    for (index, chunk) in fixture.chunks.iter().enumerate() {
        let owners: Vec<_> = chunk.states.iter().map(|s| owner(s)).collect();
        let counters: Vec<_> = chunk
            .states
            .iter()
            .map(|s| {
                counters::Owner::new(
                    s.iter()
                        .filter(|&&id| !fixture.flags[id as usize].contains(StateFlags::AIR))
                        .count() as u64,
                )
            })
            .collect();
        let inputs: Vec<_> = owners.iter().zip(&counters).collect();
        let result = build(&inputs, chunk.min_y, &fixture.catalog).unwrap();
        assert_eq!(
            (result.min, result.max),
            (chunk.min, chunk.max),
            "chunk {index}"
        );
        assert_eq!(result.solid, chunk.solid, "chunk {index} solid");
        assert_eq!(result.blocking, chunk.blocking, "chunk {index} blocking");
    }
}
#[test]
fn stale_empty_counters_bottom_row_and_all_empty_bounds_are_preserved() {
    let catalog = Catalog::new(
        vec![0, 1, -1],
        vec![vec![], vec![[0., 0., 0., 1., 1., 1.]]],
        |id| {
            (
                if id == 0 {
                    StateFlags::AIR
                } else {
                    StateFlags::CAN_OCCLUDE
                },
                15,
            )
        },
    )
    .unwrap();
    let stone = owner(&vec![1; 4096]);
    let empty = counters::Owner::new(0);
    let result = build(&[(&stone, &empty), (&stone, &empty)], -64, &catalog).unwrap();
    assert_eq!((result.min, result.max), (-64, -48));
    assert_eq!(result.solid, [-64; 256]);
    let mut ids = vec![0; 4096];
    ids[..256].fill(1);
    let bottom = owner(&ids);
    let nonempty = counters::Owner::new(256);
    assert_eq!(
        build(&[(&bottom, &nonempty)], 0, &catalog).unwrap().solid,
        [0; 256]
    );
    ids[256] = 2;
    let contextual = owner(&ids);
    assert!(build(&[(&contextual, &nonempty)], 0, &catalog).is_none());
    assert!(build(&[], 0, &catalog).is_none());
    assert!(build(&[(&stone, &nonempty)], 1, &catalog).is_none());
}
#[test]
fn outputs_own_values_after_inputs_are_released() {
    let catalog = Catalog::new(vec![0], vec![vec![[0., 0., 0., 1., 1., 1.]]], |_| {
        (StateFlags::CAN_OCCLUDE, 15)
    })
    .unwrap();
    let section = owner(&vec![0; 4096]);
    let counts = counters::Owner::new(4096);
    let output = build(&[(&section, &counts)], -64, &catalog).unwrap();
    drop(section);
    drop(counts);
    assert_eq!(output.solid, [-49; 256]);
    assert_eq!(output.blocking, [-49; 256]);
    assert_eq!(std::mem::size_of::<Heightmaps>(), 2056);
}

use super::*;

#[test]
fn retired_world_binding_declines_but_copied_owner_and_old_reader_remain_live() {
    let owner = single();
    let copy = owner.copy();
    let previous = owner.world_value(0).unwrap();
    owner.invalidate();
    assert_eq!(owner.world_value(0), None);
    assert_eq!(copy.world_value(0), Some(previous));
    assert_eq!(owner.with_state_reader(|r| r.get(0)), previous);
}
use crate::world::level::levelgen::noise_fill::section::Section;
fn single() -> Owner {
    Owner::load(0, 0, &[71], &[], 31809, 15).unwrap()
}

#[test]
fn packed_reader_captures_values_without_pinning_mutable_generations() {
    let owner = single(); let alias = owner.copy();
    let captured = owner.with_state_reader(|reader| {
        let captured = reader.get(0);
        assert!(alias.read_single(72));
        assert_eq!(reader.palette_states().collect::<Vec<_>>(), vec![captured]);
        captured
    });
    assert_eq!(captured, 71);
    assert_eq!(owner.with_state_reader(|reader| reader.get(0)), 72);
    for i in 0..260 { owner.write(i, i as u32).unwrap(); }
    let (bits, global, palette, words) = owner.with_state_reader(|reader|
        (reader.packed_bits(), reader.global_palette(),
         reader.palette_states().collect::<Vec<_>>(), reader.packed_words().collect::<Vec<_>>()));
    assert!(global); assert_eq!(bits, 15); assert!(palette.is_empty());
    let per = 64 / bits;
    assert_eq!((words[12 / per] >> (12 % per * bits)) & ((1 << bits) - 1), 12);
    owner.write(12, 999).unwrap();
    assert_eq!((words[12 / per] >> (12 % per * bits)) & ((1 << bits) - 1), 12);
    assert_eq!(owner.with_state_reader(|reader| reader.get(12)), 999);
}
#[test]
fn ordered_word_visits_match_scalar_reads_for_single_local_global_and_padding() {
    for bits in [0usize, 4, 5, 6, 7, 8, 15] {
        let palette: Vec<i32> = if bits == 15 { vec![] }
            else if bits == 0 { vec![71] } else { vec![71, 72] };
        let mut words = if bits == 0 { vec![] }
            else { vec![0u64; ENTRIES.div_ceil(64 / bits)] };
        if bits != 0 {
            let per = 64 / bits;
            for index in 0..ENTRIES {
                let value = if bits == 15 { index as u64 } else { (index % 2) as u64 };
                words[index / per] |= value << (index % per * bits);
            }
            // Unused last-word cells are not emitted by the visitor.
            if ENTRIES % per != 0 {
                *words.last_mut().unwrap() |= u64::MAX << (ENTRIES % per * bits);
            }
        }
        let owner = Owner::load(bits, bits, &palette, &words, 31809, 15).unwrap();
        owner.with_state_reader(|reader| {
            let mut visited = 0;
            reader.for_each_state(|index, state| {
                assert_eq!(index, visited); assert_eq!(state, reader.get(index)); visited += 1;
            });
            assert_eq!(visited, ENTRIES);
        });
    }
}
#[test]
fn live_growth_and_overwrites_match_stage_policy_word_for_word() {
    let live = single();
    let mut reference = Section::new(71, 15);
    let mut rng = 123u64;
    for i in 0..18000 {
        rng = rng.wrapping_mul(6364136223846793005).wrapping_add(1);
        let index = (rng >> 32) as usize % ENTRIES;
        let value = if i < 600 {
            i as u32
        } else {
            (rng >> 16) as u32 % 31809
        };
        let old = reference.get(index);
        assert_eq!(live.write(index, value).unwrap().0 as i32, old);
        reference.set(index, value as i32);
        if i < 600 || i % 128 == 0 {
            let s = live.state.lock().unwrap();
            let g = &s.generation;
            assert_eq!(g.bits as u32, reference.storage_bits());
            assert_eq!(g.requested as u32, reference.requested_bits());
            assert_eq!(
                g.words
                    .iter()
                    .map(|w| w.load(Ordering::Acquire) as i64)
                    .collect::<Vec<_>>(),
                reference.packed()
            );
            assert_eq!(
                (0..g.count.load(Ordering::Acquire) as usize)
                    .map(|id| g.palette[id].load(Ordering::Acquire) as i32)
                    .collect::<Vec<_>>(),
                reference.palette()
            );
        }
    }
}
#[test]
fn invalid_imports_decline_before_publication() {
    assert!(Owner::load(4, 4, &[2, 2], &[0; 256], 31809, 15).is_none());
    assert!(Owner::load(4, 4, &[2], &[15; 256], 31809, 15).is_none());
    assert!(Owner::load(4, 4, &[31809], &[0; 256], 31809, 15).is_none());
    assert!(Owner::load(15, 9, &[], &[u64::MAX; 1024], 31809, 15).is_none());
    assert!(Owner::load(4, 5, &[2], &[0; 256], 31809, 15).is_none());
    let owner = single();
    assert!(owner.write(4096, 5).is_none());
    assert!(owner.write(0, 31809).is_none());
    assert_eq!(owner.capture()[0], 71);
}
#[test]
fn copies_views_and_captures_survive_growth_and_owner_release() {
    let owner = single();
    let old = owner.state.lock().unwrap().generation.clone();
    owner.write(0, 2).unwrap();
    let local = owner.state.lock().unwrap().generation.clone();
    let snapshot = owner.capture();
    let copy = owner.copy();
    for i in 0..4096 {
        owner.write(i, i as u32).unwrap();
    }
    drop(owner);
    assert_eq!(old.state(0), 71);
    assert_eq!(local.state(4095), 71);
    assert_eq!(snapshot[0], 2);
    assert_eq!(copy.capture(), snapshot);
    assert_eq!(copy.write(0, 9).unwrap().0, 2);
    assert_eq!(snapshot[0], 2);
}
#[test]
fn import_padding_is_preserved_and_resize_padding_is_fresh() {
    let mut words = vec![0u64; ENTRIES.div_ceil(64 / 5)];
    for word in &mut words {
        *word = 15 << 60;
    }
    let palette: Vec<i32> = (0..32).collect();
    let owner = Owner::load(5, 5, &palette, &words, 31809, 15).unwrap();
    owner.write(1, 2).unwrap();
    assert_eq!(
        owner.state.lock().unwrap().generation.words[0].load(Ordering::Acquire) >> 60,
        15
    );
    owner.write(0, 33).unwrap();
    let s = owner.state.lock().unwrap();
    assert_eq!(s.generation.bits, 6);
    for word in &s.generation.words {
        assert_eq!(word.load(Ordering::Acquire) >> 60, 0);
    }
}
#[test]
fn concurrent_atomic_views_observe_only_admitted_values() {
    let owner = Arc::new(single());
    owner.write(0, 1).unwrap();
    let view = owner.state.lock().unwrap().generation.clone();
    let writer = owner.clone();
    std::thread::scope(|scope| {
        scope.spawn(move || {
            for i in 0..50000 {
                writer.write(i % ENTRIES, (i % 16) as u32).unwrap();
            }
        });
        for i in 0..50000 {
            let value = view.state(i % ENTRIES);
            assert!(value == 71 || value < 16);
        }
    });
}
#[test]
fn single_copies_share_network_palette_replacement_until_growth() {
    let owner = single();
    let copy = owner.copy();
    assert!(copy.read_single(2));
    assert_eq!(owner.capture()[0], 2);
    assert_eq!(owner.write(0, 71).unwrap().0, 2);
    assert_eq!(owner.capture()[0], 71);
    assert_eq!(copy.capture()[0], 2);
    assert!(copy.read_single(3));
    assert_eq!(owner.capture()[1], 2);
    assert_eq!(copy.capture()[0], 3);
    assert!(!copy.read_single(31809));
    assert!(!owner.read_single(4));
}

#[test]
fn borrowed_scan_captures_single_palette_alias_once() {
    let owner = single();
    let alias = owner.copy();
    owner.with_state_reader(|reader| {
        assert_eq!(reader.get(0), 71);
        assert!(alias.read_single(5));
        assert_eq!(reader.get(4095), 71);
        assert!(reader.all_states(|id| id == 71));
    });
    owner.with_state_reader(|reader| assert_eq!(reader.get(0), 5));
}
#[test]
fn capture_matches_per_index_decode_at_every_width() {
    let mut rng = 0x9e37_79b9_7f4a_7c15u64;
    for distinct in [1u32, 2, 5, 16, 17, 40, 100, 300, 2000] {
        let owner = single();
        for _ in 0..6000 {
            rng ^= rng << 13;
            rng ^= rng >> 7;
            rng ^= rng << 17;
            let index = (rng >> 20) as usize % ENTRIES;
            owner.write(index, (rng >> 40) as u32 % distinct).unwrap();
        }
        let captured = owner.capture();
        let s = owner.state.lock().unwrap();
        for (i, value) in captured.iter().enumerate() {
            assert_eq!(*value, s.generation.state(i) as u16, "distinct={distinct} index={i}");
        }
    }
}

#[test]
fn read_stamps_cover_alias_replacement_growth_writes_and_saturation_without_pinning_data() {
    let owner = single(); let alias = owner.copy();
    let initial = owner.read_stamp().unwrap(); assert!(initial.matches(&alias.read_stamp().unwrap()));
    assert!(alias.read_single(2)); assert!(!initial.matches(&owner.read_stamp().unwrap()));
    let shared = owner.read_stamp().unwrap(); owner.write(0, 3).unwrap();
    assert!(!shared.matches(&owner.read_stamp().unwrap())); assert!(shared.matches(&alias.read_stamp().unwrap()));
    let grown = owner.read_stamp().unwrap(); owner.write(1, 3).unwrap();
    assert!(!grown.matches(&owner.read_stamp().unwrap()));
    let before_invalid = owner.read_stamp().unwrap(); assert!(owner.write(4096, 3).is_none());
    assert!(before_invalid.matches(&owner.read_stamp().unwrap()));
    { let state = owner.state.lock().unwrap(); state.generation.revision.store(u64::MAX, Ordering::Release); }
    assert!(owner.read_stamp().is_none()); owner.write(2, 3).unwrap(); assert!(owner.read_stamp().is_none());
    drop(owner); drop(alias); assert!(!initial.alive());
}

use super::*;
use std::io::Read;
struct Reader(Vec<u8>, usize);
impl Reader {
    fn bytes(&mut self, n: usize) -> &[u8] { let i = self.1; self.1 += n; &self.0[i..self.1] }
    fn byte(&mut self) -> i8 { self.bytes(1)[0] as i8 }
    fn int(&mut self) -> i32 { i32::from_be_bytes(self.bytes(4).try_into().unwrap()) }
    fn long(&mut self) -> u64 { u64::from_be_bytes(self.bytes(8).try_into().unwrap()) }
    fn text(&mut self) -> String { let n = u16::from_be_bytes(self.bytes(2).try_into().unwrap()) as usize; String::from_utf8(self.bytes(n).to_vec()).unwrap() }
    fn state(&mut self) -> Vec<State> { (0..WIDTH).map(|_| {
        let generation = self.byte(); let compression = self.byte(); let n = self.int();
        State { generation, compression, points: (n >= 0).then(|| (0..n).map(|_| self.long()).collect()) }
    }).collect() }
}
struct State { generation: i8, compression: i8, points: Option<Vec<u64>> }
#[test]
fn all_actual_frozen_signed_generation_pairs_and_saved_sources_match_both_updates() {
    let mut bytes = Vec::new();
    flate2::read::GzDecoder::new(include_bytes!("../../../../../../../test/resources/world/chunk/frozen-dh-same-detail.bin.gz").as_slice()).read_to_end(&mut bytes).unwrap();
    let mut r = Reader(bytes, 0); assert_eq!(r.int(), 0x44484d31);
    assert_eq!(r.text(), "dd03b56d30b6f7f02755a6251e30a538f49936da38fcc34777f1c4d0ff9b0ed4");
    assert_eq!(r.text().len(), 64); assert!(r.text().contains("65536 signed generation pairs")); assert_eq!(r.int(), 48);
    for _ in 0..48 {
        let name = r.text(); let count = r.int(); let ids: Vec<_> = (0..count).map(|_| r.int()).collect();
        let mut empty = r.byte() != 0; let mut target = r.state(); let source = r.state();
        for pass in 0..2 {
            let inputs: Vec<_> = source.iter().zip(&target).map(|(s,t)| Input { source: s.points.as_deref(), target: t.points.as_deref(),
                generation: [t.generation, s.generation], compression: s.compression, uniform: false,
                footprint: None, previous_footprint: false }).collect();
            let mut result = build(&inputs, &ids, 0).unwrap();
            drop(inputs);
            let mut storage: Vec<_> = target.iter().map(|s| s.points.as_ref().map(|p| Box::new(Column(p.clone())))).collect();
            let owners: Vec<_> = storage.iter_mut().map(|s| s.as_mut().map_or(0, |c| c.as_mut() as *mut Column as usize)).enumerate()
                .map(|(i,p)| if result.flags[i] & 1 != 0 { p } else { 0 }).collect();
            let mut output = vec![0usize; WIDTH * 2];
            unsafe { assert_eq!(mattmc_dh_same_detail_commit(&mut result, owners.as_ptr(), output.as_mut_ptr()), 1); }
            assert_eq!(r.byte() != 0, result.changed, "{name} pass{pass} changed");
            if result.flags.iter().any(|f| f & 1 != 0) { empty = false; }
            assert_eq!(r.byte() != 0, empty, "{name} pass{pass} empty");
            for (index, (expected, actual)) in r.state().into_iter().zip(&mut target).enumerate() {
                if result.flags[index] & 1 != 0 {
                    actual.points = Some(if let Some(column) = storage[index].take() { column.0 }
                        else { result.columns[index].take().unwrap().0 });
                    actual.generation = result.metadata[index][0]; actual.compression = result.metadata[index][1];
                    assert_eq!(result.hashes[index], hash(actual.points.as_ref().unwrap()));
                }
                assert_eq!((actual.generation, actual.compression), (expected.generation, expected.compression), "{name} pass{pass} column{index} metadata");
                assert_eq!(actual.points, expected.points, "{name} pass{pass} column{index} points");
            }
        }
    }
    assert_eq!(r.1, r.0.len());
}
#[test]
fn ordered_footprint_replacement_capacity_clipping_and_signed_remaps_are_preserved() {
    let mut source = vec![0u64; 300];
    let input = [0u64];
    let mut inputs: Vec<_> = (0..WIDTH).map(|i| Input { source: (i < 3).then_some(input.as_slice()), target: Some(&input),
        generation: [0, 9], compression: -128, uniform: i == 0,
        footprint: Some([Some(&source), None, Some(&[]), Some(&input)]), previous_footprint: i == 0 || (100..1123).contains(&i) }).collect();
    let result = build(&inputs, &[-1], 1024).unwrap();
    assert!(!result.changed, "Wrapped hash collision follows Frozen, not value comparison");
    assert_eq!(result.metadata[0], [9, -128]);
    assert_eq!(result.descriptors[0][1], usize::MAX - 1);
    assert_eq!(result.descriptors[1][1], 256); assert_eq!(result.descriptors[1][3], usize::MAX);
    assert_eq!(result.descriptors[1][5], 0); assert_eq!(result.descriptors[1][7], 1);
    assert_eq!(result.descriptors[2][1], usize::MAX - 1);
    drop(inputs); source[0] = 8;
    unsafe { assert_eq!(*(result.descriptors[1][0] as *const u64), u64::MAX); }
    inputs = (0..WIDTH).map(|_| Input { source: Some(&input), target: Some(&input), generation: [0, 9],
        compression: 0, uniform: true, footprint: None, previous_footprint: false }).collect();
    assert!(build(&inputs, &[], 0).is_none());
}
#[test]
fn publication_preserves_existing_owners_null_adoption_and_decline_before_mutation() {
    let input = [1u64]; let old = [0u64];
    let inputs: Vec<_> = (0..WIDTH).map(|i| Input { source: (i < 2).then_some(input.as_slice()), target: (i != 1).then_some(old.as_slice()),
        generation: [0,9], compression: 1, uniform: true, footprint: None, previous_footprint: false }).collect();
    let mut merge = build(&inputs, &[10,11], 0).unwrap(); let mut target = Column(Vec::with_capacity(16)); target.0.push(3);
    let allocation = target.0.as_ptr();
    let mut owners = vec![0usize; WIDTH]; let mut output = vec![99usize; WIDTH * 2];
    owners[0] = &mut target as *mut Column as usize; owners[1] = owners[0];
    unsafe {
        assert!(mattmc_dh_same_detail_take(&mut merge, 1).is_null());
        assert_eq!(mattmc_dh_same_detail_commit(&mut merge, owners.as_ptr(), output.as_mut_ptr()), 0);
        assert_eq!(target.0, [3]); assert!(output.iter().all(|&x| x == 99));
        owners[1] = 0;
        assert_eq!(mattmc_dh_same_detail_commit(&mut merge, owners.as_ptr(), output.as_mut_ptr()), 1);
        assert_eq!(target.0, [11]); assert_eq!(output[1], 1); assert_eq!(target.0.as_ptr(), allocation);
        let new = mattmc_dh_same_detail_take(&mut merge, 1); assert!(!new.is_null());
        assert_eq!((*new).0, [11]); assert!(mattmc_dh_same_detail_take(&mut merge, 1).is_null());
        assert_eq!(mattmc_dh_same_detail_commit(&mut merge, owners.as_ptr(), output.as_mut_ptr()), 0);
        super::super::mutable::mattmc_dh_column_release(new);
    }
    assert_eq!(input, [1]);
}
#[test]
fn later_reserve_failure_refreshes_moved_views_without_changing_any_values() {
    let source = vec![0u64; 2048]; let old = [1u64];
    let inputs: Vec<_> = (0..WIDTH).map(|i| Input { source: (i < 2).then_some(source.as_slice()), target: Some(&old),
        generation: [0,9], compression: 0, uniform: true, footprint: None, previous_footprint: false }).collect();
    let mut merge = build(&inputs, &[0], 0).unwrap();
    let mut first = Column(vec![1]); let mut second = Column(vec![2]);
    let mut targets = vec![0usize; WIDTH]; targets[0] = &mut first as *mut Column as usize; targets[1] = &mut second as *mut Column as usize;
    let mut output = vec![0usize; WIDTH * 2]; let mut count = 0;
    unsafe {
        assert_eq!(commit_with_reserve(&mut merge, targets.as_ptr(), output.as_mut_ptr(), |points, wanted| {
            count += 1;
            if count == 2 { return false; }
            // Force a moved allocation, instead of relying on realloc behavior.
            let mut fresh = Vec::with_capacity(wanted); fresh.extend_from_slice(points); *points = fresh; true
        }), -1);
    }
    assert_eq!(first.0, [1]); assert_eq!(second.0, [2]); assert!(!merge.committed);
    assert_eq!((output[0],output[1]), (first.0.as_ptr() as usize,1));
    assert_eq!((output[2],output[3]), (second.0.as_ptr() as usize,1));
    unsafe { assert_eq!(mattmc_dh_same_detail_commit(&mut merge, targets.as_ptr(), output.as_mut_ptr()), 1); }
    assert_eq!(first.0, source); assert_eq!(second.0, source);
}

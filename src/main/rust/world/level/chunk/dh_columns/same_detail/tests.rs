use super::*;
use std::io::Read;
#[test]
fn copy_proofs_track_source_target_remap_and_replacement_without_trusting_hash_collisions() {
    use super::super::{source::*, mutable::*};
    unsafe {
        let source = mattmc_dh_source_create(); let target = mattmc_dh_source_create();
        let input = mattmc_dh_column_create([0,31].as_ptr(),2);
        let recipient = mattmc_dh_column_create([1,0].as_ptr(),2);
        assert_eq!(mattmc_dh_source_set(source,0,2,input),1);
        assert_eq!(mattmc_dh_source_set(target,0,2,recipient),1);
        let mut facts = vec![0u8;WIDTH*6]; facts[0]=9; facts[WIDTH*2]=9;
        facts[WIDTH*3]=255; facts[WIDTH*4]=1;
        let mut ids: Vec<i32> = (0..32).collect();
        let update = |reuse: bool, ids: &[i32]| {
            let raw = mattmc_dh_same_detail_sources(target,source,facts.as_ptr(),ptr::null(),0,ids.as_ptr(),ids.len() as i32,0);
            assert!(!raw.is_null()); let mut merge = Box::from_raw(raw);
            assert_eq!(merge.reused(0),reuse);
            let mut output=vec![0usize;WIDTH*2];let mut detached=vec![0u8;WIDTH];
            assert_eq!(mattmc_dh_same_detail_commit_source(&mut *merge,target,output.as_mut_ptr(),detached.as_mut_ptr()),1);
            assert_eq!(output[0],(*recipient).points().as_ptr() as usize); assert_eq!(output[1],2);
            let mut projection=vec![0u64;WIDTH*4];
            assert_eq!(mattmc_dh_same_detail_projection(&*merge,output.as_ptr(),detached.as_ptr(),projection.as_mut_ptr()),1);
            assert_eq!(projection[3]&8!=0,reuse);
            assert_eq!((*recipient).hash(),hash((*recipient).points()));
            merge.changed
        };
        assert_eq!((*input).hash(),(*recipient).hash(),"Distinct points deliberately collide");
        assert!(!update(false,&ids)); assert_eq!((*recipient).points(),[0,31]);
        assert!(!update(true,&ids));
        assert_eq!(mattmc_dh_column_set(input,0,1),1); assert_eq!(mattmc_dh_column_set(input,1,0),1);
        assert!(!update(false,&ids)); assert_eq!((*recipient).points(),[1,0]);
        assert!(!update(true,&ids));
        assert_eq!(mattmc_dh_column_set(recipient,0,0),1); assert_eq!(mattmc_dh_column_set(recipient,1,31),1);
        assert!(!update(false,&ids)); assert_eq!((*recipient).points(),[1,0]);
        ids[1]=-1; assert!(update(false,&ids)); assert_eq!((*recipient).points(),[u64::MAX,0]);
        assert!(!update(true,&ids));
        ids[1]=1; assert!(update(false,&ids)); assert!(!update(true,&ids));
        let replacement=mattmc_dh_column_create([1,0].as_ptr(),2);
        assert_eq!(mattmc_dh_source_set(source,0,2,replacement),1);
        assert!(!update(false,&ids)); assert!(!update(true,&ids));
        let oversized: Vec<i32> = (0..4097).collect();
        assert!(!update(false,&oversized)); assert!(!update(false,&oversized));
        assert!(!update(false,&ids)); assert!(!update(true,&ids));

        // The checked descriptor ABI may publish to a different allocation;
        // it always copies, even if preparation proved the original target.
        let raw=mattmc_dh_same_detail_sources(target,source,facts.as_ptr(),ptr::null(),0,ids.as_ptr(),32,0);
        let mut merge=Box::from_raw(raw);assert!(merge.reused(0));
        let mut other=Column::new(vec![3,3]);let mut targets=vec![0usize;WIDTH];targets[0]=&mut other as *mut Column as usize;
        let mut output=vec![99usize;WIDTH*2];
        assert_eq!(mattmc_dh_same_detail_commit(&mut *merge,targets.as_ptr(),output.as_mut_ptr()),1);
        assert_eq!(other.points(),[1,0]); assert_eq!(other.hash(),(*recipient).hash());

        let raw=mattmc_dh_same_detail_sources(target,source,facts.as_ptr(),ptr::null(),0,ids.as_ptr(),32,0);
        let mut stale=Box::from_raw(raw);let mut detached=vec![77u8;WIDTH];output.fill(99);
        assert_eq!(mattmc_dh_source_set(source,1,0,ptr::null_mut()),1);
        assert_eq!(mattmc_dh_same_detail_commit_source(&mut *stale,target,output.as_mut_ptr(),detached.as_mut_ptr()),0);
        assert!(output.iter().all(|&v|v==99));assert!(detached.iter().all(|&v|v==77));
        mattmc_dh_column_release(input);mattmc_dh_column_release(recipient);mattmc_dh_column_release(replacement);
        mattmc_dh_source_release(source);mattmc_dh_source_release(target);
    }
}
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
unsafe fn retained_source(states: &[State]) -> *mut super::super::source::Source {
    use super::super::{source::*, mutable::*};
    let source = mattmc_dh_source_create();
    for (i, state) in states.iter().enumerate() {
        if let Some(points) = &state.points {
            let column = mattmc_dh_column_create(points.as_ptr(), points.len() as i32);
            assert!(!column.is_null());
            assert_eq!(mattmc_dh_source_set(source, i as i32, 2, column), 1);
            mattmc_dh_column_release(column);
        } else { assert_eq!(mattmc_dh_source_set(source, i as i32, 0, ptr::null_mut()), 1); }
    }
    source
}
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
            let reference = build(&inputs, &ids, 0).unwrap();
            drop(inputs);
            let (native_target, native_source) = unsafe { (retained_source(&target), retained_source(&source)) };
            let mut facts = vec![0u8; WIDTH * 6];
            for i in 0..WIDTH {
                facts[i] = target[i].generation as u8; facts[WIDTH + i] = target[i].compression as u8;
                facts[WIDTH * 2 + i] = source[i].generation as u8; facts[WIDTH * 3 + i] = source[i].compression as u8;
            }
            let pointer = unsafe { mattmc_dh_same_detail_sources(native_target, native_source, facts.as_ptr(), ptr::null(), 0, ids.as_ptr(), ids.len() as i32, 0) };
            assert!(!pointer.is_null(), "{name} retained-source admission");
            let mut result = unsafe { Box::from_raw(pointer) };
            assert_eq!(reference.flags, result.flags); assert_eq!(reference.metadata, result.metadata);
            assert_eq!(reference.hashes, result.hashes); assert_eq!(reference.changed, result.changed);
            drop(reference);
            let direct_pointer = unsafe { mattmc_dh_same_detail_sources(native_target, native_source, facts.as_ptr(), ptr::null(), 0, ids.as_ptr(), ids.len() as i32, 0) };
            let mut direct = unsafe { Box::from_raw(direct_pointer) };
            let mut direct_output = vec![0usize; WIDTH * 2]; let mut detached = vec![0u8; WIDTH];
            unsafe { assert_eq!(mattmc_dh_same_detail_commit_source(&mut *direct,native_target,direct_output.as_mut_ptr(),detached.as_mut_ptr()),1); }
            let mut projection=vec![77u64;WIDTH*4];
            unsafe {
                let saved=detached[4095];detached[4095]=2;
                assert_eq!(mattmc_dh_same_detail_projection(&*direct,direct_output.as_ptr(),detached.as_ptr(),projection.as_mut_ptr()),0);
                assert!(projection.iter().all(|&v|v==77));detached[4095]=saved;
                assert_eq!(mattmc_dh_same_detail_projection(&*direct,direct_output.as_ptr(),detached.as_ptr(),projection.as_mut_ptr()),1);
                for i in 0..WIDTH {
                    assert_eq!(&projection[i*4..i*4+2],&[direct_output[i*2] as u64,direct_output[i*2+1] as u64]);
                    assert_eq!(projection[i*4+2] as i32,direct.hashes[i]);assert_eq!(projection[i*4+3],u64::from(direct.flags[i])|(u64::from(detached[i])<<2)|(u64::from(direct.reused(i))<<3));
                }
                let mut aliased=vec![99u64;WIDTH*4];
                assert_eq!(mattmc_dh_same_detail_projection(&*direct,aliased.as_ptr().cast(),detached.as_ptr(),aliased.as_mut_ptr()),0);
                assert!(aliased.iter().all(|&v|v==99));
            }
            let mut generation: Vec<_> = target.iter().map(|s| s.generation).collect();
            let mut compression: Vec<_> = target.iter().map(|s| s.compression).collect(); let mut flags = vec![99u8; WIDTH];
            unsafe {
                assert_eq!(mattmc_dh_same_detail_metadata(&*direct,generation.as_mut_ptr(),compression.as_mut_ptr(),flags.as_mut_ptr()),direct.flags.iter().filter(|&&f|f&1!=0).count() as i32);
                for i in 0..WIDTH { if flags[i]&1!=0 && detached[i]==0 {
                    let column=mattmc_dh_same_detail_take(&mut *direct,i as i32); assert!(!column.is_null());
                    assert_eq!(super::super::source::mattmc_dh_source_set(native_target,i as i32,2,column),1);
                    super::super::mutable::mattmc_dh_column_release(column);
                }}
            }
            let mut storage: Vec<_> = target.iter().map(|s| s.points.as_ref().map(|p| Box::new(Column::new(p.clone())))).collect();
            let owners: Vec<_> = storage.iter_mut().map(|s| s.as_mut().map_or(0, |c| c.as_mut() as *mut Column as usize)).enumerate()
                .map(|(i,p)| if result.flags[i] & 1 != 0 { p } else { 0 }).collect();
            let mut output = vec![0usize; WIDTH * 2];
            unsafe { assert_eq!(mattmc_dh_same_detail_commit(&mut *result, owners.as_ptr(), output.as_mut_ptr()), 1); }
            assert_eq!(r.byte() != 0, result.changed, "{name} pass{pass} changed");
            if result.flags.iter().any(|f| f & 1 != 0) { empty = false; }
            assert_eq!(r.byte() != 0, empty, "{name} pass{pass} empty");
            for (index, (expected, actual)) in r.state().into_iter().zip(&mut target).enumerate() {
                if result.flags[index] & 1 != 0 {
                    actual.points = Some(if let Some(column) = storage[index].take() { column.into_points() }
                        else { result.columns[index].take().unwrap().into_points() });
                    actual.generation = result.metadata[index][0]; actual.compression = result.metadata[index][1];
                    assert_eq!(result.hashes[index], hash(actual.points.as_ref().unwrap()));
                }
                assert_eq!((actual.generation, actual.compression), (expected.generation, expected.compression), "{name} pass{pass} column{index} metadata");
                assert_eq!(actual.points, expected.points, "{name} pass{pass} column{index} points");
                assert_eq!((generation[index],compression[index]),(expected.generation,expected.compression),"{name} bulk metadata {index}");
                unsafe { assert_eq!((*native_target).present(index),expected.points.is_some());
                    assert_eq!((*native_target).column(index).unwrap(),expected.points.as_deref().unwrap_or(&[]),"{name} retained commit {index}"); }

            }
            unsafe { super::super::source::mattmc_dh_source_release(native_source); super::super::source::mattmc_dh_source_release(native_target); }
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
    assert_eq!(result.actions,[0,1],"Only removals and admitted footprints require Java map publication");
    assert_eq!(result.descriptors()[0][1], usize::MAX - 1);
    assert_eq!(result.descriptors()[1][1], 256); assert_eq!(result.descriptors()[1][3], usize::MAX);
    assert_eq!(result.descriptors()[1][5], 0); assert_eq!(result.descriptors()[1][7], 1);
    assert_eq!(result.descriptors()[2][1], usize::MAX - 1);
    drop(inputs); source[0] = 8;
    unsafe { assert_eq!(*(result.descriptors()[1][0] as *const u64), u64::MAX); }
    inputs = (0..WIDTH).map(|_| Input { source: Some(&input), target: Some(&input), generation: [0, 9],
        compression: 0, uniform: true, footprint: None, previous_footprint: false }).collect();
    assert!(build(&inputs, &[], 0).is_none());
}
#[test]
fn publication_preserves_existing_owners_null_adoption_and_decline_before_mutation() {
    let input = [1u64]; let old = [0u64];
    let inputs: Vec<_> = (0..WIDTH).map(|i| Input { source: (i < 2).then_some(input.as_slice()), target: (i != 1).then_some(old.as_slice()),
        generation: [0,9], compression: 1, uniform: true, footprint: None, previous_footprint: false }).collect();
    let mut merge = build(&inputs, &[10,11], 0).unwrap(); let mut target = Column::new(Vec::with_capacity(16)); unsafe { Column::points_mut(&mut target).push(3); }
    let allocation = target.points().as_ptr();
    let mut owners = vec![0usize; WIDTH]; let mut output = vec![99usize; WIDTH * 2];
    owners[0] = &mut target as *mut Column as usize; owners[1] = owners[0];
    unsafe {
        let mut generations=vec![-128i8;WIDTH]; let mut modes=vec![127i8;WIDTH]; let mut flags=vec![99u8;WIDTH];
        assert_eq!(mattmc_dh_same_detail_metadata(&merge,generations.as_mut_ptr(),modes.as_mut_ptr(),flags.as_mut_ptr()),-1);
        assert!(generations.iter().all(|&v|v==-128)); assert!(modes.iter().all(|&v|v==127)); assert!(flags.iter().all(|&v|v==99));
        assert!(mattmc_dh_same_detail_take(&mut merge, 1).is_null());
        assert_eq!(mattmc_dh_same_detail_commit(&mut merge, owners.as_ptr(), output.as_mut_ptr()), 0);
        assert_eq!(target.points(), [3]); assert!(output.iter().all(|&x| x == 99));
        owners[1] = 0;
        assert_eq!(mattmc_dh_same_detail_commit(&mut merge, owners.as_ptr(), output.as_mut_ptr()), 1);
        assert_eq!(target.points(), [11]); assert_eq!(output[1], 1); assert_eq!(target.points().as_ptr(), allocation);
        assert_eq!(mattmc_dh_same_detail_metadata(&merge,generations.as_mut_ptr(),generations.as_mut_ptr(),flags.as_mut_ptr()),-1);
        assert!(generations.iter().all(|&v|v==-128)); assert!(flags.iter().all(|&v|v==99));
        assert_eq!(mattmc_dh_same_detail_metadata(&merge,generations.as_mut_ptr(),modes.as_mut_ptr(),flags.as_mut_ptr()),2);
        assert_eq!(&generations[..3],&[9,9,-128]); assert_eq!(&modes[..3],&[1,1,127]); assert_eq!(&flags[..3],&[3,3,0]);
        let new = mattmc_dh_same_detail_take(&mut merge, 1); assert!(!new.is_null());
        assert_eq!((*new).points(), [11]); assert!(mattmc_dh_same_detail_take(&mut merge, 1).is_null());
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
    let mut first = Column::new(vec![1]); let mut second = Column::new(vec![2]);
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
    assert_eq!(first.points(), [1]); assert_eq!(second.points(), [2]); assert!(!merge.committed);
    assert_eq!((output[0],output[1]), (first.points().as_ptr() as usize,1));
    assert_eq!((output[2],output[3]), (second.points().as_ptr() as usize,1));
    unsafe { assert_eq!(mattmc_dh_same_detail_commit(&mut merge, targets.as_ptr(), output.as_mut_ptr()), 1); }
    assert_eq!(first.points(), source); assert_eq!(second.points(), source);
}

//! One ordered source pass over borrowed native column owners and CPU views.
//! The caller excludes mutation and pins every input for the entire call.
use super::{materials::Materials, mutable::Column, occlusion::cull};
use std::{mem::align_of, slice};

/// Cull directly from the retained source. Java receives only center extents:
/// count followed by index/pointer/length triples (capacity 4096 triples).
/// Decline leaves values and output untouched. Allocation failure publishes all
/// successfully detached extents before returning -1, without point changes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_cull(source: *mut super::source::Source,
    policy: *const Materials, output: *mut usize) -> i32 {
    owned_pass(source, policy, output, |owner| Column::make_owned(owner),false)
}
/// Same complete pass, projecting escaped readers only.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_cull_readers(source:*mut super::source::Source,policy:*const Materials,output:*mut usize)->i32 {
    owned_pass(source,policy,output,|owner|Column::make_owned(owner),true)
}
fn reader_extents(source:&super::source::Source,output:&mut [usize]) {
    let mut kept=0;
    for row in 0..output[0] {
        let first=1+row*3;
        if source.has_reader(output[first]) {
            output.copy_within(first..first+3,1+kept*3);kept+=1;
        }
    }
    output[0]=kept;
}

unsafe fn owned_pass(source: *mut super::source::Source, policy: *const Materials,
    output: *mut usize, mut detach: impl FnMut(*mut Column) -> bool,readers_only:bool) -> i32 {
    let (Some(source), Some(policy)) = (source.as_mut(), policy.as_ref()) else { return 0; };
    if output.is_null() || output as usize % align_of::<usize>() != 0 { return 0; }
    // The cached primary identity proof includes immutable backing leases.
    // Distinct immutable leases may share producer points: detach each center
    // before mutation, so those shared spans never alias mutable storage.
    if source.identities().windows(2).any(|p| p[0] == p[1]) { return 0; }
    let mut owned = true;
    for i in 0..4096 {
        let Some(points) = source.column(i) else { return 0; };
        if points.len() > 4096 || points.iter().any(|p| *p as u32 as usize >= policy.0.len()) { return 0; }
        let owner = source.owner(i);
        if !owner.is_null() && !(*owner).is_owned() { owned = false; }
    }
    let output = slice::from_raw_parts_mut(output, 1 + 4096 * 3);
    output[0] = 0;
    // Mutable recipients require no allocation: combine selection and culling
    // without a second neighborhood scan or an intermediate extent grid.
    if owned {
        for x in 1..63 { for z in 1..63 {
            let i = x * 64 + z;
            if source.column(i).unwrap().len() <= 1 { continue; }
            let adjacent = [i + 64, i - 64, i + 1, i - 1].map(|n| source.column(n).unwrap());
            if adjacent.iter().any(|c| c.is_empty()) { continue; }
            let owner = source.owner(i);
            // Every ID was admitted above. Removal and packed height/bottom
            // setters preserve the low 32-bit material identity.
            cull(Column::points_mut(owner), adjacent,
                &|p| policy.0.get_unchecked(p as u32 as usize).opacity < 16);
            if readers_only && !source.has_reader(i){continue;}
            let points = (*owner).points(); let row = 1 + output[0] * 3;
            output[row..row + 3].copy_from_slice(&[i, points.as_ptr() as usize, points.len()]);
            output[0] += 1;
        }}
        return 1;
    }
    for x in 1..63 { for z in 1..63 {
        let i = x * 64 + z;
        if source.column(i).unwrap().len() <= 1
            || [i + 64, i - 64, i + 1, i - 1].iter().any(|&n| source.column(n).unwrap().is_empty()) { continue; }
        let owner = source.owner(i);
        if !detach(owner) {if readers_only{reader_extents(source,output);} return -1; }
        let row = 1 + output[0] * 3;
        let points = (*owner).points();
        output[row..row + 3].copy_from_slice(&[i, points.as_ptr() as usize, points.len()]);
        output[0] += 1;
    }}
    // All allocations have succeeded; preserve Frozen's x-major/z-minor order
    // and read each neighbor's current values after earlier center removals.
    for row in 0..output[0] {
        let row = 1 + row * 3; let i = output[row]; let owner = source.owner(i);
        let adjacent = [i + 64, i - 64, i + 1, i - 1].map(|n| source.column(n).unwrap());
        cull(Column::points_mut(owner), adjacent, &|p| policy.0.get_unchecked(p as u32 as usize).opacity < 16);
        let points = (*owner).points();
        output[row + 1] = points.as_ptr() as usize; output[row + 2] = points.len();
    }
    if readers_only{reader_extents(source,output);}
    1
}

#[derive(Clone, Copy)]
struct Input { owner: *mut Column, points: *const u64, count: usize }
impl Input {
    unsafe fn points<'a>(self) -> &'a [u64] {
        if !self.owner.is_null() { (*self.owner).points() }
        else if self.count == 0 { &[] }
        else { slice::from_raw_parts(self.points, self.count) }
    }
}

/// Each input is owner/pointer/count: an owned vector, or an immutable view.
/// Output is pointer/count/changed/hash per column. A decline never mutates.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_columns_cull_pass(raw: *const usize, policy: *const Materials, output: *mut usize) -> i32 {
    if raw.is_null() || policy.is_null() || output.is_null()
        || raw as usize % align_of::<usize>() != 0 || output as usize % align_of::<usize>() != 0 { return 0; }
    let raw = slice::from_raw_parts(raw, 4096 * 3);
    let mut inputs = Vec::with_capacity(4096);
    let mut owners = Vec::new();
    let mut allocations = Vec::new();
    let facts = &(*policy).0;
    for record in raw.chunks_exact(3) {
        let input = Input { owner: record[0] as *mut Column, points: record[1] as *const u64, count: record[2] };
        if input.owner.is_null() {
            if input.count > 4096 || input.count != 0 && (input.points.is_null() || input.points as usize % 8 != 0) { return 0; }
        } else {
            if input.owner as usize % align_of::<Column>() != 0 || record[1] != 0 || record[2] != 0 { return 0; }
            owners.push(record[0]);
            let column = (*input.owner).points();
            if column.len() > 4096 { return 0; }
            if let Some(allocation) = (*input.owner).allocation() { allocations.push(allocation); }
        }
        if input.points().iter().any(|p| *p as u32 as usize >= facts.len()) { return 0; }
        inputs.push(input);
    }
    owners.sort_unstable();
    if owners.windows(2).any(|pair| pair[0] == pair[1]) { return 0; }
    allocations.sort_unstable();
    for input in &inputs {
        if input.owner.is_null() && input.count != 0 {
            let start = input.points as usize;
            let Some(end) = start.checked_add(input.count * 8) else { return 0; };
            let index = allocations.partition_point(|&(first, _)| first < end);
            if index != 0 && allocations[index - 1].1 > start { return 0; }
        }
    }
    // A read-only center requiring mutation must decline before any earlier
    // center changes; Java then retains its original callbacks/failure prefix.
    for x in 1..63 { for z in 1..63 {
        let i = x * 64 + z;
        if inputs[i].owner.is_null() && inputs[i].points().len() > 1
            && [i + 64, i - 64, i + 1, i - 1].iter().all(|&n| !inputs[n].points().is_empty()) { return 0; }
    }}
    let output = slice::from_raw_parts_mut(output, 4096 * 4);
    output.fill(0);
    for x in 1..63 { for z in 1..63 {
        let i = x * 64 + z;
        if inputs[i].owner.is_null() || inputs[i].points().len() <= 1 { continue; }
        let adjacent = [i + 64, i - 64, i + 1, i - 1].map(|n| inputs[n].points());
        if cull(Column::points_mut(inputs[i].owner), adjacent, &|p| facts[p as u32 as usize].opacity < 16) {
            output[i * 4 + 2] = 1;
            output[i * 4 + 3] = (*inputs[i].owner).hash() as u32 as usize;
        }
    }}
    for (i, input) in inputs.into_iter().enumerate() {
        let points = input.points();
        output[i * 4] = points.as_ptr() as usize; output[i * 4 + 1] = points.len();
    }
    1
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::materials::Fact;
    #[test]
    fn reader_projection_never_skips_culling_unescaped_primary_columns() {
        use super::super::{source::*,mutable::*};
        unsafe {
            let source=mattmc_dh_source_create();
            let points=vec![(2u64<<44)|(1u64<<32),(1u64<<44)|(1u64<<32),1u64<<32];
            (*source).adopt(65,Box::new(Column::new(points.clone())));
            for i in [1,129,64,66] {(*source).adopt(i,Box::new(Column::new(vec![3u64<<32])));}
            let policy=Materials(vec![Fact{opacity:16,air:false,liquid:false}].into_boxed_slice());
            let mut output=vec![99usize;1+4096*3];
            assert_eq!(mattmc_dh_source_cull_readers(source,&policy,output.as_mut_ptr()),1);
            assert_eq!(output[0],0);assert_eq!((*source).column(65).unwrap().len(),2);
            assert_eq!(mattmc_dh_source_reader(source,65),1);
            assert_eq!(mattmc_dh_source_cull_readers(source,&policy,output.as_mut_ptr()),1);
            assert_eq!(output[0],1);assert_eq!(output[1],65);assert_eq!(output[3],2);
            let version=(*source).version();assert_eq!(mattmc_dh_source_reader(source,4095),1);
            assert_eq!((*source).version(),version,"Reader facts cannot invalidate primary/copy proofs");
            assert_eq!(mattmc_dh_source_set(source,65,0,std::ptr::null_mut()),1);
            assert!(!(*source).has_reader(65));assert_eq!(mattmc_dh_source_reader(source,65),0);
            assert_eq!(mattmc_dh_source_reader(source,-1),0);assert_eq!(mattmc_dh_source_reader(source,4096),0);
            mattmc_dh_source_release(source);
        }
    }
    #[test]
    fn owned_immutable_centers_publish_detached_prefix_on_failure_and_preserve_producer() {
        use super::super::{source::*, mutable::*, Columns};
        use std::sync::Arc;
        unsafe {
            let points = vec![(2u64 << 44) | (1u64 << 32), (1u64 << 44) | (1u64 << 32), 1u64 << 32];
            let producer = Arc::new(Columns {pos:6,chunk_x:0,chunk_z:0,compression:0,
                points:points.clone().into_boxed_slice(),offsets:Box::new([0;257]),materials:vec![].into_boxed_slice()});
            let raw = Arc::into_raw(producer);
            let source = mattmc_dh_source_create();
            let a = mattmc_dh_column_shared(0,raw.cast(),0,3);
            let b = mattmc_dh_column_shared(0,raw.cast(),0,3);
            for (i,c) in [(65,a),(66,b)] {assert_eq!(mattmc_dh_source_set(source,i,2,c),1);}
            for i in [1,129,64,2,130,67] {
                let c=mattmc_dh_column_create([3u64<<32].as_ptr(),1);
                assert_eq!(mattmc_dh_source_set(source,i,2,c),1);mattmc_dh_column_release(c);
            }
            let policy=Materials(vec![Fact{opacity:16,air:false,liquid:false}].into_boxed_slice());
            let mut output=vec![99usize;1+4096*3];let mut calls=0;
            assert_eq!(owned_pass(source,&policy,output.as_mut_ptr(),|c| {
                calls+=1;calls==1 && Column::make_owned(c)
            },false),-1);
            assert_eq!(&output[..4],&[1,65,(*a).points().as_ptr() as usize,3]);
            assert_ne!((*a).points().as_ptr(),(*raw).points.as_ptr());
            assert_eq!((*b).points().as_ptr(),(*raw).points.as_ptr());
            assert_eq!((*a).points(),points);assert_eq!((*b).points(),points);
            assert_eq!(mattmc_dh_source_cull(source,&policy,output.as_mut_ptr()),1);
            assert_eq!(output[0],2);assert_eq!(output[1],65);assert_eq!(output[4],66);
            assert_eq!((*a).points().len(),2);assert_eq!((*raw).points.as_ref(),points);
            assert_eq!((*source).owner(65),a,"Primary identity survives detachment");
            mattmc_dh_source_release(source);
            assert_eq!((*a).points().len(),2,"Independent reader survives source close");
            mattmc_dh_column_release(a);mattmc_dh_column_release(b);
            super::super::ffi::mattmc_dh_columns_release(raw as *mut Columns);
        }
    }
    #[test]
    fn owned_aliases_custom_rows_bad_ids_and_oversized_columns_decline_without_mutation() {
        use super::super::{source::*, mutable::*};
        unsafe {
            let source=mattmc_dh_source_create();
            let c=mattmc_dh_column_create([3u64<<32,1u64<<32].as_ptr(),2);
            let policy=Materials(vec![Fact{opacity:16,air:false,liquid:false}].into_boxed_slice());
            let mut output=vec![99usize;1+4096*3];
            assert_eq!(mattmc_dh_source_set(source,65,2,c),1);
            assert_eq!(mattmc_dh_source_set(source,66,2,c),1);
            assert_eq!(mattmc_dh_source_cull(source,&policy,output.as_mut_ptr()),0);
            assert_eq!(mattmc_dh_source_set(source,66,1,std::ptr::null_mut()),1);
            assert_eq!(mattmc_dh_source_cull(source,&policy,output.as_mut_ptr()),0);
            assert_eq!(mattmc_dh_source_set(source,66,0,std::ptr::null_mut()),1);
            let bad=mattmc_dh_column_create([1u64].as_ptr(),1);
            assert_eq!(mattmc_dh_source_set(source,4095,2,bad),1);
            assert_eq!(mattmc_dh_source_cull(source,&policy,output.as_mut_ptr()),0);
            assert_eq!((*c).points(),[3u64<<32,1u64<<32]);assert!(output.iter().all(|&p|p==99));
            Column::points_mut(bad).resize(4097,0);
            assert_eq!(mattmc_dh_source_cull(source,&policy,output.as_mut_ptr()),0);
            assert!(output.iter().all(|&p|p==99));
            mattmc_dh_source_release(source);mattmc_dh_column_release(c);mattmc_dh_column_release(bad);
        }
    }
    #[test]
    fn immutable_neighbors_and_mutable_center_match_ordered_point_and_hash_output() {
        let policy = Materials(vec![Fact { opacity: 16, air: false, liquid: false }].into_boxed_slice());
        let mut center = Column::new(vec![(2u64 << 44) | (1u64 << 32), (1u64 << 44) | (1u64 << 32), 1u64 << 32]);
        let previous_hash = center.hash();
        let adjacent = [3u64 << 32];
        let mut raw = vec![0usize; 4096 * 3]; let mut out = vec![0; 4096 * 4]; let i = 65;
        raw[i * 3] = &mut center as *mut Column as usize;
        for n in [i + 64, i - 64, i + 1, i - 1] { raw[n * 3 + 1] = adjacent.as_ptr() as usize; raw[n * 3 + 2] = 1; }
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 1); }
        assert_eq!(center.points(), [(1u64 << 44) | (2u64 << 32), 1u64 << 32]);
        assert_eq!((out[i * 4 + 1], out[i * 4 + 2]), (2, 1));
        let hash = center.points().iter().fold(1i32, |h, &p| h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32));
        assert_eq!(out[i * 4 + 3] as u32 as i32, hash);
        assert_eq!(center.hash(),hash); assert_ne!(previous_hash,hash);
    }
    #[test]
    fn aliases_bad_ids_and_overlapping_readers_decline_before_mutation() {
        let policy = Materials(vec![Fact { opacity: 16, air: false, liquid: false }].into_boxed_slice());
        let mut center = Column::new(vec![3u64 << 32, 2u64 << 32, 1u64 << 32]);
        let old = center.points().to_vec();
        let mut raw = vec![0usize; 4096 * 3]; let mut out = vec![99; 4096 * 4];
        let i = 64 + 1;
        raw[i * 3] = &mut center as *mut Column as usize;
        raw[(i + 64) * 3] = raw[i * 3];
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 0); }
        assert_eq!(center.points(), old); assert!(out.iter().all(|&x| x == 99));
        raw[(i + 64) * 3] = 0; raw[(i + 64) * 3 + 1] = center.points().as_ptr() as usize; raw[(i + 64) * 3 + 2] = 3;
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 0); }
        assert_eq!(center.points(), old);
        raw[(i + 64) * 3 + 1] = 0; raw[(i + 64) * 3 + 2] = 0; unsafe { Column::points_mut(&mut center)[0] |= 1; }
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 0); }
        assert_eq!(center.points()[0], old[0] | 1); assert!(out.iter().all(|&x| x == 99));
    }
}

//! One ordered source pass over borrowed native column owners and CPU views.
//! The caller excludes mutation and pins every input for the entire call.
use super::{materials::Materials, mutable::Column, occlusion::cull};
use std::{mem::align_of, slice};

#[derive(Clone, Copy)]
struct Input { owner: *mut Column, points: *const u64, count: usize }
impl Input {
    unsafe fn points<'a>(self) -> &'a [u64] {
        if !self.owner.is_null() { &(*self.owner).0 }
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
            let column = &(*input.owner).0;
            if column.len() > 4096 { return 0; }
            if column.capacity() != 0 { allocations.push((column.as_ptr() as usize, column.as_ptr() as usize + column.capacity() * 8)); }
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
        if cull(&mut (*inputs[i].owner).0, adjacent, &|p| facts[p as u32 as usize].opacity < 16) {
            output[i * 4 + 2] = 1;
            output[i * 4 + 3] = (*inputs[i].owner).0.iter().fold(1i32, |h, &p|
                h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32)) as u32 as usize;
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
    fn immutable_neighbors_and_mutable_center_match_ordered_point_and_hash_output() {
        let policy = Materials(vec![Fact { opacity: 16, air: false, liquid: false }].into_boxed_slice());
        let mut center = Column(vec![(2u64 << 44) | (1u64 << 32), (1u64 << 44) | (1u64 << 32), 1u64 << 32]);
        let adjacent = [3u64 << 32];
        let mut raw = vec![0usize; 4096 * 3]; let mut out = vec![0; 4096 * 4]; let i = 65;
        raw[i * 3] = &mut center as *mut Column as usize;
        for n in [i + 64, i - 64, i + 1, i - 1] { raw[n * 3 + 1] = adjacent.as_ptr() as usize; raw[n * 3 + 2] = 1; }
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 1); }
        assert_eq!(center.0, [(1u64 << 44) | (2u64 << 32), 1u64 << 32]);
        assert_eq!((out[i * 4 + 1], out[i * 4 + 2]), (2, 1));
        let hash = center.0.iter().fold(1i32, |h, &p| h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32));
        assert_eq!(out[i * 4 + 3] as u32 as i32, hash);
    }
    #[test]
    fn aliases_bad_ids_and_overlapping_readers_decline_before_mutation() {
        let policy = Materials(vec![Fact { opacity: 16, air: false, liquid: false }].into_boxed_slice());
        let mut center = Column(vec![3u64 << 32, 2u64 << 32, 1u64 << 32]);
        let old = center.0.clone();
        let mut raw = vec![0usize; 4096 * 3]; let mut out = vec![99; 4096 * 4];
        let i = 64 + 1;
        raw[i * 3] = &mut center as *mut Column as usize;
        raw[(i + 64) * 3] = raw[i * 3];
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 0); }
        assert_eq!(center.0, old); assert!(out.iter().all(|&x| x == 99));
        raw[(i + 64) * 3] = 0; raw[(i + 64) * 3 + 1] = center.0.as_ptr() as usize; raw[(i + 64) * 3 + 2] = 3;
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 0); }
        assert_eq!(center.0, old);
        raw[(i + 64) * 3 + 1] = 0; raw[(i + 64) * 3 + 2] = 0; center.0[0] |= 1;
        unsafe { assert_eq!(mattmc_dh_columns_cull_pass(raw.as_ptr(), &policy, out.as_mut_ptr()), 0); }
        assert_eq!(center.0[0], old[0] | 1); assert!(out.iter().all(|&x| x == 99));
    }
}

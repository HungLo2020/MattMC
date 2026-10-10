//! Frozen's complete same-detail recipient merge, prepared before publication.
//! Java supplies source facts/CPU views; Rust owns policy and replacement vectors.
use super::{contributors::{capture, Snapshot}, mutable::Column};
use std::{mem::align_of, ptr, slice};
const WIDTH: usize = 4096;
pub(crate) struct Input<'a, 'b> {
    pub(crate) source: Option<&'a [u64]>,
    pub(crate) target: Option<&'b [u64]>,
    pub(crate) generation: [i8; 2],
    pub(crate) compression: i8,
    pub(crate) uniform: bool,
    pub(crate) footprint: Option<[Option<&'b [u64]>; 4]>,
    pub(crate) previous_footprint: bool,
}
pub(crate) struct Merge<'a> {
    sources: Box<[Option<&'a [u64]>]>,
    remap: Box<[i32]>,
    columns: Vec<Option<Box<Column>>>,
    flags: Box<[u8]>,
    metadata: Box<[[i8; 2]]>,
    hashes: Box<[i32]>,
    descriptors: Box<[[usize; 8]]>,
    _footprints: Vec<Snapshot>,
    changed: bool,
    committed: bool,
}
fn hash(points: &[u64]) -> i32 {
    points.iter().fold(1i32, |h, &p| h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32))
}
fn admit(old: i8, new: i8) -> bool {
    new == -1 && (old == 0 || old == -1) || new != 0 && old <= new
}
pub(crate) fn build<'a>(inputs: &[Input<'a, '_>], remap: &[i32], mut retained: usize) -> Option<Merge<'a>> {
    if inputs.len() != WIDTH || inputs.iter().filter(|i| i.previous_footprint).count() > retained { return None; }
    let mut result = Merge { sources: vec![None; WIDTH].into_boxed_slice(), remap: remap.to_vec().into_boxed_slice(),
        columns: (0..WIDTH).map(|_| None).collect(), flags: vec![0; WIDTH].into_boxed_slice(),
        metadata: vec![[0; 2]; WIDTH].into_boxed_slice(), hashes: vec![0; WIDTH].into_boxed_slice(),
        descriptors: vec![[0, usize::MAX - 1, 0, 0, 0, 0, 0, 0]; WIDTH].into_boxed_slice(),
        _footprints: Vec::new(), changed: false, committed: false };
    for (index, input) in inputs.iter().enumerate() {
        let Some(source) = input.source else { continue; };
        if !admit(input.generation[0], input.generation[1]) { continue; }
        let mut new_hash = 1i32;
        for &point in source {
            let id = *remap.get(point as u32 as usize)?;
            let point = point & !(u32::MAX as u64) | id as i64 as u64;
            new_hash = new_hash.wrapping_mul(31).wrapping_add((point ^ (point >> 32)) as i32);
        }
        // Frozen's changed flag is global. Metadata-only replacements do not
        // set it, and later columns stop comparing old hashes once it is true.
        result.changed |= input.target.is_none_or(|target| target.len() != source.len());
        if !result.changed { result.changed = hash(input.target.unwrap()) != new_hash; }
        result.sources[index] = Some(source);
        result.flags[index] = 1 | (u8::from(input.uniform) << 1);
        result.metadata[index] = [input.generation[1], input.compression];
        result.hashes[index] = new_hash;
        retained -= usize::from(input.previous_footprint);
        if !input.uniform && retained < 1024 {
            if let Some(footprint) = input.footprint {
                let snapshot = capture(footprint, Some(remap), 256)?;
                result.descriptors[index] = snapshot.descriptors();
                result._footprints.push(snapshot); retained += 1;
            }
        }
    }
    Some(result)
}
unsafe fn points<'a>(pointer: usize, count: usize) -> Option<&'a [u64]> {
    if count > 4096 || count != 0 && (pointer == 0 || pointer % 8 != 0) { return None; }
    Some(if count == 0 { &[] } else { slice::from_raw_parts(pointer as *const u64, count) })
}
/// Six words per input: source pointer/count, target pointer/count, raw facts,
/// optional immutable footprint header. Facts are three bytes and four booleans.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_create(raw: *const usize, remap: *const i32, count: i32, retained: i32) -> *mut Merge<'static> {
    if raw.is_null() || raw as usize % align_of::<usize>() != 0 || count < 0 || retained < 0
        || count != 0 && (remap.is_null() || remap as usize % 4 != 0) { return ptr::null_mut(); }
    let mut inputs = Vec::with_capacity(WIDTH);
    for row in slice::from_raw_parts(raw, WIDTH * 6).chunks_exact(6) {
        let facts = row[4];
        if facts & !0x0fff_ffff != 0 || facts & (1 << 25) == 0 && row[1] != 0
            || facts & (1 << 26) == 0 && row[3] != 0 { return ptr::null_mut(); }
        let (Some(source), Some(target)) = (points(row[0], row[1]), points(row[2], row[3])) else { return ptr::null_mut(); };
        let footprint = if row[5] == 0 { None } else {
            if row[5] % align_of::<usize>() != 0 { return ptr::null_mut(); }
            let mut columns = [None; 4];
            for (i, pair) in slice::from_raw_parts(row[5] as *const usize, 8).chunks_exact(2).enumerate() {
                if pair[1] != usize::MAX {
                    let Some(column) = points(pair[0], pair[1]) else { return ptr::null_mut(); };
                    columns[i] = Some(column);
                }
            }
            Some(columns)
        };
        inputs.push(Input { source: (facts & (1 << 25) != 0).then_some(source), target: (facts & (1 << 26) != 0).then_some(target),
            generation: [facts as u8 as i8, (facts >> 8) as u8 as i8], compression: (facts >> 16) as u8 as i8,
            uniform: facts & (1 << 24) != 0, previous_footprint: facts & (1 << 27) != 0, footprint });
    }
    let ids = if count == 0 { &[] } else { slice::from_raw_parts(remap, count as usize) };
    build(&inputs, ids, retained as usize).map_or(ptr::null_mut(), |m| Box::into_raw(Box::new(m)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_view(owner: *const Merge<'_>, output: *mut usize) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % align_of::<usize>() != 0 { return 0; }
    let owner = &*owner;
    slice::from_raw_parts_mut(output, 9).copy_from_slice(&[owner.flags.as_ptr() as usize, WIDTH,
        owner.metadata.as_ptr() as usize, WIDTH, owner.hashes.as_ptr() as usize, WIDTH,
        owner.descriptors.as_ptr() as usize, WIDTH, usize::from(owner.changed)]); 1
}
/// Copy/remap directly into recipient capacity in one call. Source views remain
/// pinned/excluded until success; null recipients remain owned until taken.
/// A reserve failure returns -1 and refreshed existing views, without value writes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_commit(owner: *mut Merge<'_>, targets: *const usize, output: *mut usize) -> i32 {
    commit_with_reserve(owner, targets, output, |points, count| points.try_reserve(count.saturating_sub(points.len())).is_ok())
}
unsafe fn commit_with_reserve(owner: *mut Merge<'_>, targets: *const usize, output: *mut usize,
    mut reserve: impl FnMut(&mut Vec<u64>, usize) -> bool) -> i32 {
    if owner.is_null() || targets.is_null() || output.is_null()
        || targets as usize % align_of::<usize>() != 0 || output as usize % align_of::<usize>() != 0 { return 0; }
    let owner = &mut *owner;
    if owner.committed { return 0; }
    let targets = slice::from_raw_parts(targets, WIDTH);
    let mut unique = Vec::new();
    for (i, &pointer) in targets.iter().enumerate() {
        if owner.flags[i] & 1 == 0 { if pointer != 0 { return 0; } continue; }
        if pointer != 0 {
            if pointer % align_of::<Column>() != 0 { return 0; }
            unique.push(pointer);
        }
    }
    unique.sort_unstable(); if unique.windows(2).any(|v| v[0] == v[1]) { return 0; }
    let mut allocations: Vec<_> = unique.iter().filter_map(|&p| {
        let c = &(*(p as *const Column)).0;
        (c.capacity() != 0).then(|| (c.as_ptr() as usize, c.as_ptr() as usize + c.capacity() * 8))
    }).collect();
    allocations.sort_unstable();
    for source in owner.sources.iter().flatten().filter(|s| !s.is_empty()) {
        let start = source.as_ptr() as usize;
        let end = start + source.len() * 8;
        let index = allocations.partition_point(|&(first, _)| first < end);
        if index != 0 && allocations[index - 1].1 > start { return 0; }
    }
    let output = slice::from_raw_parts_mut(output, WIDTH * 2); output.fill(0);
    // Reserve before values change. A growth may move an existing allocation;
    // callers must refresh every existing view even if a later reserve fails.
    let mut reserved = true;
    for (index, &target) in targets.iter().enumerate() {
        let Some(source) = owner.sources[index] else { continue; };
        let points = if target == 0 {
            owner.columns[index] = Some(Box::new(Column(Vec::new())));
            &mut owner.columns[index].as_mut().unwrap().0
        } else { &mut (*(target as *mut Column)).0 };
        if !reserve(points, source.len()) { reserved = false; break; }
    }
    if !reserved {
        for (index, &target) in targets.iter().enumerate() {
            if target == 0 { continue; }
            let points = &(*(target as *const Column)).0;
            output[index * 2] = points.as_ptr() as usize; output[index * 2 + 1] = points.len();
        }
        return -1;
    }
    for (index, &target) in targets.iter().enumerate() {
        let Some(source) = owner.sources[index] else { continue; };
        let points = if target == 0 { &mut owner.columns[index].as_mut().unwrap().0 }
            else { &mut (*(target as *mut Column)).0 };
        points.clear();
        for &point in source {
            let id = owner.remap[point as u32 as usize];
            points.push(point & !(u32::MAX as u64) | id as i64 as u64);
        };
        output[index * 2] = points.as_ptr() as usize; output[index * 2 + 1] = points.len();
    }
    owner.sources.fill(None);
    owner.committed = true; 1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_take(owner: *mut Merge<'_>, index: i32) -> *mut Column {
    let Some(owner) = owner.as_mut() else { return ptr::null_mut(); };
    if !owner.committed || !(0..WIDTH as i32).contains(&index) { return ptr::null_mut(); }
    owner.columns[index as usize].take().map_or(ptr::null_mut(), Box::into_raw)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_release(owner: *mut Merge<'_>) {
    if !owner.is_null() { drop(Box::from_raw(owner)); }
}

#[cfg(test)] mod tests;

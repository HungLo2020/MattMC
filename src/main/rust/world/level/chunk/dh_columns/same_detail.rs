//! Frozen's complete same-detail recipient merge, prepared before publication.
//! Java supplies source facts/CPU views; Rust owns policy and replacement vectors.
use super::{contributors::{capture, Snapshot}, mutable::Column};
use std::{mem::align_of, ptr, slice, sync::Arc};
const WIDTH: usize = 4096;
static ALL_COLUMNS: [u32; WIDTH] = {
    let mut indices=[0u32;WIDTH];let mut i=0;
    while i<WIDTH {indices[i]=i as u32;i+=1;}indices
};
#[derive(Clone, Copy)]
pub(crate) struct Input<'a, 'b> {
    pub(crate) source: Option<&'a [u64]>,
    pub(crate) target: Option<&'b [u64]>,
    pub(crate) generation: [i8; 2],
    pub(crate) compression: i8,
    pub(crate) uniform: bool,
    pub(crate) footprint: Option<[Option<&'b [u64]>; 4]>,
    pub(crate) previous_footprint: bool,
}
// Keep the common retained-column stream small. Contributor arrays are rare
// and are read lazily only after ordered footprint admission succeeds.
struct ColumnInput<'a,'b> {
    source: Option<&'a [u64]>, target: Option<&'b [u64]>,
    generation: [i8;2], compression:i8, uniform:bool, previous_footprint:bool,
}
pub(crate) struct Merge<'a> {
    sources: Box<[Option<&'a [u64]>]>,
    remap: Box<[i32]>,
    columns: Vec<Option<Box<Column>>>,
    flags: Box<[u8]>,
    metadata: Box<[[i8; 2]]>,
    hashes: Box<[i32]>,
    counts: Box<[u16]>,
    descriptors: Option<Box<[[usize; 8]]>>,
    actions: Vec<u32>,
    /// Admitted footprints by ascending cell index; descriptors borrow them.
    footprints: Vec<(u32, Arc<Snapshot>)>,
    changed: bool,
    committed: bool,
    target_source: Option<(*const super::source::Source, u64)>,
    source_proof: Option<(*const super::source::Source, u64, [u64; 2])>,
    /// Set instead of `source_proof` for a merge read straight from producer
    /// columns: the recipient's mapping stamp, with no source copy proofs.
    producer_mapping: Option<[u64; 2]>,
    reused: [u64; WIDTH / 64],
    writes: Vec<u32>,
    identity_remap: bool,
}
fn hash(points: &[u64]) -> i32 {
    points.iter().fold(1i32, |h, &p| h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32))
}
fn admit(old: i8, new: i8) -> bool {
    new == -1 && (old == 0 || old == -1) || new != 0 && old <= new
}
const EMPTY_DESCRIPTOR: [usize; 8] = [0, usize::MAX - 1, 0, 0, 0, 0, 0, 0];
static EMPTY_DESCRIPTORS: [[usize; 8]; WIDTH] = [EMPTY_DESCRIPTOR; WIDTH];
impl Merge<'_> {
    fn descriptors(&self) -> &[[usize; 8]] { self.descriptors.as_deref().unwrap_or(&EMPTY_DESCRIPTORS) }
    fn reused(&self, index: usize) -> bool { self.reused[index / 64] & (1 << (index % 64)) != 0 }
}
pub(crate) fn build<'a>(inputs: &[Input<'a, '_>], remap: &[i32], retained: usize) -> Option<Merge<'a>> {
    if inputs.len() != WIDTH || inputs.iter().filter(|i| i.previous_footprint).count() > retained { return None; }
    build_columns(inputs.iter().map(|input| Some((ColumnInput {
        source:input.source,target:input.target,generation:input.generation,
        compression:input.compression,uniform:input.uniform,previous_footprint:input.previous_footprint,
    },None,false))),remap,retained,|i|inputs[i].footprint)
}
fn build_columns<'a,'b>(inputs: impl ExactSizeIterator<Item=Option<(ColumnInput<'a,'b>, Option<i32>, bool)>>, remap: &[i32], mut retained: usize,
    mut footprint:impl FnMut(usize)->Option<[Option<&'b [u64]>;4]>) -> Option<Merge<'a>> {
    if inputs.len() != WIDTH { return None; }
    let mut result = Merge { sources: vec![None; WIDTH].into_boxed_slice(), remap: remap.to_vec().into_boxed_slice(),
        columns: (0..WIDTH).map(|_| None).collect(), flags: vec![0; WIDTH].into_boxed_slice(),
        metadata: vec![[0; 2]; WIDTH].into_boxed_slice(), hashes: vec![0; WIDTH].into_boxed_slice(),
        counts: vec![0;WIDTH].into_boxed_slice(),
        descriptors: None,
        actions: Vec::new(), footprints: Vec::new(), changed: false, committed: false, target_source: None,
        source_proof: None, producer_mapping: None, reused: [0; WIDTH / 64], writes: Vec::new(),
        identity_remap: remap.iter().enumerate().all(|(i,&id)| id as i64 == i as i64) };
    for (index, input) in inputs.enumerate() {
        let (input, hash_hint, reused)=input?;
        let Some(source) = input.source else { continue; };
        if !admit(input.generation[0], input.generation[1]) { continue; }
        let mut new_hash = hash_hint.unwrap_or(1);
        if hash_hint.is_none() { for &point in source {
            let id = *remap.get(point as u32 as usize)?;
            let point = point & !(u32::MAX as u64) | id as i64 as u64;
            new_hash = new_hash.wrapping_mul(31).wrapping_add((point ^ (point >> 32)) as i32);
        }}
        // Frozen's changed flag is global. Metadata-only replacements do not
        // set it, and later columns stop comparing old hashes once it is true.
        result.changed |= input.target.is_none_or(|target| target.len() != source.len());
        if !result.changed && !reused { result.changed = hash(input.target.unwrap()) != new_hash; }
        if reused { result.reused[index / 64] |= 1 << (index % 64); }
        else {result.writes.push(index as u32);}
        result.sources[index] = Some(source);
        // A scalar length survives release of the borrowed source spans at
        // commit. 4097 is sufficient to preserve oversized-hash decline.
        result.counts[index]=source.len().min(4097) as u16;
        result.flags[index] = 1 | (u8::from(input.uniform) << 1);
        result.metadata[index] = [input.generation[1], input.compression];
        result.hashes[index] = new_hash;
        retained -= usize::from(input.previous_footprint);
        let mut footprint_admitted = false;
        if !input.uniform && retained < 1024 {
            if let Some(footprint) = footprint(index) {
                let snapshot = capture(footprint, Some(remap), 256)?;
                result.descriptors.get_or_insert_with(||vec![EMPTY_DESCRIPTOR;WIDTH].into_boxed_slice())[index] = snapshot.descriptors();
                result.footprints.push((index as u32, Arc::new(snapshot))); retained += 1;
                footprint_admitted = true;
            }
        }
        if input.previous_footprint || footprint_admitted {
            result.actions.push(index as u32);
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
/// Read retained source/recipient columns directly. Six byte planes contain
/// generation/compression/uniform/footprint facts, not Java policy results.
/// Sparse footprint rows contain an index and four existing CPU span pairs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_sources(
    target: *const super::source::Source, source: *const super::source::Source,
    facts: *const u8, footprints: *const usize, footprint_count: i32,
    remap: *const i32, count: i32, retained: i32,
) -> *mut Merge<'static> {
    if facts.is_null() { return ptr::null_mut(); }
    sources_data(target, source, facts, ptr::null(), footprints, footprint_count, remap, count, retained, false)
}
/// Ordinary source metadata is authoritative Rust data; only sparse footprint membership is transported.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_owned(
    target: *const super::source::Source, source: *const super::source::Source,
    previous: *const u8, footprints: *const usize, footprint_count: i32,
    remap: *const i32, count: i32, retained: i32,
) -> *mut Merge<'static> {
    if previous.is_null() { return ptr::null_mut(); }
    sources_data(target, source, ptr::null(), previous, footprints, footprint_count, remap, count, retained, false)
}
/// The production same-detail merge: metadata and footprints are read from the
/// two source tables (the recipient's presence and count, the input's
/// snapshots). Publish admitted footprints with `publish_footprints` after commit.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_source_owned(
    target: *const super::source::Source, source: *const super::source::Source, remap: *const i32, count: i32,
) -> *mut Merge<'static> {
    let Some(recipient) = target.as_ref() else { return ptr::null_mut(); };
    let previous: Vec<u8> = (0..WIDTH).map(|i| u8::from(recipient.footprint(i).is_some())).collect();
    sources_data(target, source, ptr::null(), previous.as_ptr(), ptr::null(), 0, remap, count, recipient.footprint_count() as i32, true)
}
/// Apply a committed merge's footprint actions to its recipient, in cell order:
/// each acted cell loses its old footprint and takes the admitted one, if any.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_publish_footprints(owner: *const Merge<'_>, target: *mut super::source::Source) -> i32 {
    let (Some(merge), Some(source)) = (owner.as_ref(), target.as_mut()) else { return -1; };
    if !merge.committed || merge.target_source.is_none_or(|(p, _)| p != target as *const _) { return -1; }
    for &index in &merge.actions {
        let admitted = merge.footprints.binary_search_by_key(&index, |(i, _)| *i).ok().map(|at| merge.footprints[at].1.clone());
        source.set_footprint(index as usize, admitted);
    }
    merge.actions.len() as i32
}
unsafe fn sources_data(
    target: *const super::source::Source, source: *const super::source::Source,
    facts: *const u8, previous: *const u8, footprints: *const usize, footprint_count: i32,
    remap: *const i32, count: i32, retained: i32, owned_footprints: bool,
) -> *mut Merge<'static> {
    if target.is_null() || source.is_null() || target == source
        || !(0..=4096).contains(&footprint_count) || count < 0 || retained < 0
        || footprint_count != 0 && (footprints.is_null() || footprints as usize % align_of::<usize>() != 0)
        || count != 0 && (remap.is_null() || remap as usize % 4 != 0) { return ptr::null_mut(); }
    let target_pointer = target; let source_pointer = source;
    // Caller exclusion also covers the bounded structural proof caches.
    let (target, source) = (&mut *(target as *mut super::source::Source), &mut *(source as *mut super::source::Source));
    let facts = (!facts.is_null()).then(|| slice::from_raw_parts(facts, WIDTH * 6));
    let previous = facts.map_or_else(|| slice::from_raw_parts(previous, WIDTH), |f| &f[WIDTH * 5..]);
    if previous.iter().any(|&b| b > 1) || facts.is_some_and(|f| f[WIDTH * 4..WIDTH * 5].iter().any(|&b| b > 1))
        || facts.is_none() && (!target.metadata.canonical() || !source.metadata.canonical()
            || source.metadata.plane(2).iter().any(|&b| b != 0 && b != 1)) { return ptr::null_mut(); }
    // Distinct immutable leases can share a producer span: writes detach them.
    if !target.disjoint_from(source) {return ptr::null_mut();}
    if previous.iter().filter(|&&b|b!=0).count() > retained as usize { return ptr::null_mut(); }
    // Only actual contributor facts need a temporary sparse sidecar. The
    // ordinary no-contributor update streams primary columns into policy.
    let mut footprint_inputs = if footprint_count == 0 { None }
        else { Some(vec![None;WIDTH]) };
    if footprint_count != 0 {
        for row in slice::from_raw_parts(footprints, footprint_count as usize * 9).chunks_exact(9) {
            let Some(input) = footprint_inputs.as_mut().unwrap().get_mut(row[0]) else { return ptr::null_mut(); };
            if input.is_some() { return ptr::null_mut(); }
            let mut columns = [None; 4];
            for (i, pair) in row[1..].chunks_exact(2).enumerate() {
                if pair[1] != usize::MAX {
                    let Some(points) = points(pair[0], pair[1]) else { return ptr::null_mut(); };
                    columns[i] = Some(points);
                }
            }
            *input = Some(columns);
        }
    }
    let ids = if count == 0 { &[] } else { slice::from_raw_parts(remap, count as usize) };
    let mapping = target.mapping(ids);
    let identity_remap = ids.iter().enumerate().all(|(i,&id)| id as i64 == i as i64);
    // Select borrowed planes once. Repeating the compatibility/native selection
    // inside each column prevents the ordinary owned consumer from staying small.
    let (old_generation,new_generation,compression,uniform): (&[u8],&[u8],&[u8],&[u8]) = if let Some(f)=facts {
        (&f[..WIDTH],&f[WIDTH*2..WIDTH*3],&f[WIDTH*3..WIDTH*4],&f[WIDTH*4..WIDTH*5])
    } else {
        let bytes=|p: &[i8]| slice::from_raw_parts(p.as_ptr().cast::<u8>(),p.len());
        (bytes(target.metadata.plane(0)),bytes(source.metadata.plane(0)),bytes(source.metadata.plane(1)),bytes(source.metadata.plane(2)))
    };
    let inputs = (0..WIDTH).map(|i| {
        let (input_owner, recipient) = (source.owner(i), target.owner(i));
        let reused_hash = if !input_owner.is_null() && !recipient.is_null()
            && (*recipient).copied_from((*input_owner).proof(mapping)) { Some((*recipient).hash()) } else { None };
        let new=source.column(i)?;
        // Exact copy proof already establishes the recipient's point count and
        // value hash. Do not read a second point backing just to compare lengths.
        // Keep the real source span for explicit compatibility-copy consumers.
        let old=if reused_hash.is_some() {new} else {target.column(i)?};
        // Only the descriptor compatibility routes bound column sizes; the
        // owned route has none (oversized columns skip the fused hash).
        if !owned_footprints && (new.len()>4096 || old.len()>4096) {return None;}
        let hash_hint = if reused_hash.is_some() { reused_hash }
            else if identity_remap && !input_owner.is_null()
                && admit(old_generation[i] as i8,new_generation[i] as i8) {
                if !(*input_owner).ids_fit(ids.len()) { return None; }
                Some((*input_owner).hash())
            } else { None };
        Some((ColumnInput { source: source.present(i).then_some(new),target: target.present(i).then_some(old),
            generation:[old_generation[i] as i8,new_generation[i] as i8],
            compression:compression[i] as i8,
            uniform:uniform[i]!=0,previous_footprint:previous[i]!=0 }, hash_hint, reused_hash.is_some()))
    });
    let source_footprints: &super::source::Source = source;
    build_columns(inputs, ids, retained as usize,|i| if owned_footprints { source_footprints.footprint(i).map(|s| s.spans()) }
        else { footprint_inputs.as_ref().and_then(|columns|columns[i]) }).map_or(ptr::null_mut(), |mut m| {
        m.target_source = Some((target_pointer, target.version()));
        m.source_proof = Some((source_pointer, source.version(), mapping)); Box::into_raw(Box::new(m))
    })
}
/// `mattmc_dh_same_detail_owned` with a generated chunk's columns as the
/// source, without a producer data source. The producer occupies its chunk's
/// 16x16 region of the 64x64 section (`createNative`'s layout) with generation
/// LIGHT (9), its compression and the block-detail uniform flag; every other
/// column has no source, which the owned merge never admits. No footprints
/// apply at block detail (`previous` must be all zero, `retained` 0). The
/// producer must stay alive until the merge is released.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_columns(
    target: *const super::source::Source, producer: *const super::Columns,
    remap: *const i32, count: i32,
) -> *mut Merge<'static> {
    if target.is_null() || producer.is_null() || producer as usize % align_of::<super::Columns>() != 0 || count < 0
        || count != 0 && (remap.is_null() || remap as usize % 4 != 0) { return ptr::null_mut(); }
    let target_pointer = target;
    // Caller exclusion also covers the bounded structural proof caches.
    let target = &mut *(target as *mut super::source::Source);
    let producer = &*producer;
    if !target.metadata.canonical() { return ptr::null_mut(); }
    let ids = if count == 0 { &[] } else { slice::from_raw_parts(remap, count as usize) };
    let mapping = target.mapping(ids);
    let old_generation = target.metadata.plane(0);
    let (ox, oz) = ((producer.chunk_x & 3) as usize * 16, (producer.chunk_z & 3) as usize * 16);
    const LIGHT: i8 = 9;
    let inputs = (0..WIDTH).map(|i| {
        let (x, z) = (i / 64, i % 64);
        let occupied = (ox..ox + 16).contains(&x) && (oz..oz + 16).contains(&z);
        let new = if occupied { Some(producer.column(x - ox, z - oz)) } else { None };
        let old = target.column(i)?;
        if new.is_some_and(|n| n.len() > 4096) || old.len() > 4096 { return None; }
        Some((ColumnInput { source: new, target: target.present(i).then_some(old),
            generation: [old_generation[i], if occupied { LIGHT } else { 0 }],
            compression: if occupied { producer.compression as i8 } else { 0 },
            uniform: occupied, previous_footprint: false }, None, false))
    });
    build_columns(inputs, ids, 0, |_| None).map_or(ptr::null_mut(), |mut m| {
        m.target_source = Some((target_pointer, target.version()));
        m.producer_mapping = Some(mapping); Box::into_raw(Box::new(m))
    })
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_view(owner: *const Merge<'_>, output: *mut usize) -> i32 {
    if owner.is_null() || output.is_null() || output as usize % align_of::<usize>() != 0 { return 0; }
    let owner = &*owner;
    slice::from_raw_parts_mut(output, 11).copy_from_slice(&[owner.flags.as_ptr() as usize, WIDTH,
        owner.metadata.as_ptr() as usize, WIDTH, owner.hashes.as_ptr() as usize, WIDTH,
        owner.descriptors().as_ptr() as usize, WIDTH, usize::from(owner.changed),
        owner.actions.as_ptr() as usize, owner.actions.len()]); 1
}
/// Bounded numeric compatibility publication; no point arrays or native owners
/// are reconstructed in Java. Heap output is borrowed only during this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_projection(owner:*const Merge<'_>,views:*const usize,detached:*const u8,output:*mut u64)->i32 {
    let Some(owner)=owner.as_ref() else{return 0;};
    if views.is_null()||views as usize%align_of::<usize>()!=0||detached.is_null()||output.is_null()||output as usize%8!=0{return 0;}
    let Some(end)=(output as usize).checked_add(WIDTH*4*8) else{return 0;};
    for (start,len) in [(views as usize,WIDTH*2*std::mem::size_of::<usize>()),(detached as usize,WIDTH)] {
        let Some(input_end)=start.checked_add(len) else{return 0;};
        if (output as usize)<input_end && start<end{return 0;}
    }
    // Decline all malformed flags before writing any output.
    let detached=slice::from_raw_parts(detached,WIDTH);if detached.iter().any(|&b|b>1){return 0;}
    let views=slice::from_raw_parts(views,WIDTH*2);
    for i in 0..WIDTH {
        if owner.flags[i]&1!=0 && views[i*2+1]>4096{return 0;}
    }
    for i in 0..WIDTH {
        output.add(i*4).write(views[i*2] as u64);output.add(i*4+1).write(views[i*2+1] as u64);
        output.add(i*4+2).write(owner.hashes[i] as i64 as u64);output.add(i*4+3).write(u64::from(owner.flags[i])|(u64::from(detached[i])<<2)|(u64::from(owner.reused(i))<<3));
    }
    1
}
/// Only changed extents cross back to temporary Java readers. Copy-proof reuse
/// leaves their addresses/lengths untouched. On reserve failure include every
/// selected non-reused reader, including earlier successful detachments.
/// Four words per row: column index, CPU address, count, existing owned lease.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_extent_projection(owner:*const Merge<'_>,views:*const usize,detached:*const u8,output:*mut u64)->i32 {
    extent_projection(owner,views,detached,output,|_|true)
}
/// Reader registration is native, so ordinary publication need not construct
/// thousands of rows just for Java to discard unescaped columns.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_reader_projection(owner:*const Merge<'_>,target:*const super::source::Source,
    views:*const usize,detached:*const u8,output:*mut u64)->i32 {
    let (Some(merge),Some(source))=(owner.as_ref(),target.as_ref()) else{return -1;};
    if merge.target_source.is_none_or(|(p,_)|p!=target){return -1;}
    extent_projection(owner,views,detached,output,|i|source.has_reader(i))
}
unsafe fn extent_projection(owner:*const Merge<'_>,views:*const usize,detached:*const u8,output:*mut u64,
    reader:impl Fn(usize)->bool)->i32 {
    let Some(owner)=owner.as_ref() else{return -1;};
    if views.is_null()||views as usize%align_of::<usize>()!=0||detached.is_null()||output.is_null()||output as usize%8!=0{return -1;}
    let Some(end)=(output as usize).checked_add(WIDTH*4*8) else{return -1;};
    for (start,len) in [(views as usize,WIDTH*2*std::mem::size_of::<usize>()),(detached as usize,WIDTH)] {
        let Some(input_end)=start.checked_add(len) else{return -1;};
        if (output as usize)<input_end && start<end{return -1;}
    }
    let detached=slice::from_raw_parts(detached,WIDTH);
    let views=slice::from_raw_parts(views,WIDTH*2);
    // Validate the whole input before writing, preserving decline-before-write.
    for i in 0..WIDTH {
        if detached[i]>1 {return -1;}
    }
    let mut rows=0;
    for &index in &owner.writes {let i=index as usize;
        if !reader(i){continue;}
        output.add(rows*4).write(i as u64);output.add(rows*4+1).write(views[i*2] as u64);
        output.add(rows*4+2).write(views[i*2+1] as u64);output.add(rows*4+3).write(detached[i] as u64);
        rows+=1;
    }
    rows as i32
}
/// Publish all three planes directly, preserving the source metadata's CPU identity.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_metadata_owned(owner: *const Merge<'_>, target: *const super::source::Source) -> i32 {
    let (Some(owner),Some(source)) = (owner.as_ref(),target.as_ref()) else { return -1; };
    if !owner.committed || owner.target_source.is_none_or(|(p,_)| p!=target) || !source.metadata.canonical() { return -1; }
    let (generation,compression,uniform)=(source.metadata.plane_mut(0),source.metadata.plane_mut(1),source.metadata.plane_mut(2));
    let mut count=0;
    for i in 0..WIDTH { if owner.flags[i]&1!=0 {
        generation[i]=owner.metadata[i][0];compression[i]=owner.metadata[i][1];uniform[i]=(owner.flags[i]>>1&1) as i8;count+=1;
    }}
    count
}
/// Same publication, with the final base hash folded during its grid visit.
/// The tagged value is an immediate update result, never a persistent cache.
/// A bounded hash decline does not interrupt successful metadata publication.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_metadata_owned_hash(owner:*const Merge<'_>,target:*const super::source::Source,pos:u64,out:*mut u64)->i32 {
    let (Some(owner),Some(source))=(owner.as_ref(),target.as_ref()) else{return -1;};
    if !owner.committed||owner.target_source.is_none_or(|(p,_)|p!=target)||!source.metadata.canonical()
        ||out.is_null()||out as usize%8!=0{return -1;}
    let Some(end)=(out as usize).checked_add(8) else{return -1;};
    for plane in 0..3 {let first=source.metadata.plane(plane).as_ptr() as usize;
        if (out as usize)<first+WIDTH && first<end {return -1;}
    }
    let (generation,compression,uniform)=(source.metadata.plane_mut(0),source.metadata.plane_mut(1),source.metadata.plane_mut(2));
    let (mut data,mut steps,mut modes)=(1i32,1i32,1i32);
    let (mut work,mut count,mut valid)=(0usize,0i32,true);
    for i in 0..WIDTH {
        if owner.flags[i]&1!=0 {
            generation[i]=owner.metadata[i][0];compression[i]=owner.metadata[i][1];uniform[i]=(owner.flags[i]>>1&1) as i8;count+=1;
        }
        if valid {
            // Successful publication already establishes these selected values
            // and lengths. Reuse that immediate result under caller exclusion;
            // unselected cells still require a primary-table read. Preserve the
            // same logical point budget as the standalone hash ABI.
            let hash=if owner.flags[i]&1!=0 {
                let count=owner.counts[i] as usize;work+=count;
                (count<=4096 && work<=65536).then_some(owner.hashes[i])
            } else {source.bounded_column_hash(i,&mut work)};
            if let Some(hash)=hash {
                data=data.wrapping_mul(31).wrapping_add(hash);
                steps=steps.wrapping_mul(31).wrapping_add(generation[i] as i32);
                modes=modes.wrapping_mul(31).wrapping_add(compression[i] as i32);
            } else {valid=false;}
        }
    }
    let hash=((pos^pos>>32) as i32).wrapping_mul(31).wrapping_add(data)
        .wrapping_mul(17).wrapping_add(steps).wrapping_mul(43).wrapping_add(modes);
    out.write(if valid {(1u64<<32)|hash as u32 as u64} else {0});count
}
/// Bounded publication into existing semantic byte arrays. Eligibility and
/// metadata policy are already native; Java only projects uniformity booleans.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_metadata(owner: *const Merge<'_>, generation: *mut i8,
    compression: *mut i8, flags: *mut u8) -> i32 {
    let Some(owner) = owner.as_ref() else { return -1; };
    if !owner.committed || generation.is_null() || compression.is_null() || flags.is_null() { return -1; }
    let addresses = [generation as usize, compression as usize, flags as usize];
    for (i, &a) in addresses.iter().enumerate() {
        let Some(end) = a.checked_add(WIDTH) else { return -1; };
        if addresses[..i].iter().any(|&b| a < b.saturating_add(WIDTH) && b < end) { return -1; }
    }
    let generation = slice::from_raw_parts_mut(generation, WIDTH);
    let compression = slice::from_raw_parts_mut(compression, WIDTH);
    let flags = slice::from_raw_parts_mut(flags, WIDTH);
    flags.copy_from_slice(&owner.flags);
    let mut updated = 0;
    for i in 0..WIDTH {
        if owner.flags[i] & 1 != 0 {
            generation[i] = owner.metadata[i][0]; compression[i] = owner.metadata[i][1]; updated += 1;
        }
    }
    updated
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
    commit_checked(owner, targets, output, false, false, &mut reserve)
}
unsafe fn commit_checked(owner: *mut Merge<'_>, targets: *const usize, output: *mut usize,
    retained_proof: bool, sparse: bool, reserve: &mut impl FnMut(&mut Vec<u64>, usize) -> bool) -> i32 {
    if owner.is_null() || targets.is_null() || output.is_null()
        || targets as usize % align_of::<usize>() != 0 || output as usize % align_of::<usize>() != 0 { return 0; }
    let owner = &mut *owner;
    if owner.committed { return 0; }
    let targets = slice::from_raw_parts(targets, WIDTH);
    if !retained_proof {
    let mut unique = Vec::new();
    for (i, &pointer) in targets.iter().enumerate() {
        if owner.flags[i] & 1 == 0 { if pointer != 0 { return 0; } continue; }
        if pointer != 0 {
            if pointer % align_of::<Column>() != 0 { return 0; }
            unique.push(pointer);
        }
    }
    unique.sort_unstable(); if unique.windows(2).any(|v| v[0] == v[1]) { return 0; }
    let mut allocations: Vec<_> = unique.iter().filter_map(|&p| (*(p as *const Column)).allocation()).collect();
    allocations.sort_unstable();
    for source in owner.sources.iter().flatten().filter(|s| !s.is_empty()) {
        let start = source.as_ptr() as usize;
        let end = start + source.len() * 8;
        let index = allocations.partition_point(|&(first, _)| first < end);
        if index != 0 && allocations[index - 1].1 > start { return 0; }
    }
    }
    let output = slice::from_raw_parts_mut(output, WIDTH * 2); output.fill(0);
    let indices=if sparse {owner.writes.as_slice()} else {&ALL_COLUMNS};
    // Reserve before values change. A growth may move an existing allocation;
    // callers must refresh every existing view even if a later reserve fails.
    let mut reserved = true;
    for &index in indices {let index=index as usize;let target=targets[index];
        if retained_proof && owner.reused(index) {
            if target != 0 {
                let points = (*(target as *const Column)).points();
                output[index*2] = points.as_ptr() as usize; output[index*2+1] = points.len();
            }
            continue;
        }
        let Some(source) = owner.sources[index] else { continue; };
        let column = if target == 0 {
            owner.columns[index] = Some(Box::new(Column::new(Vec::new())));
            &mut **owner.columns[index].as_mut().unwrap() as *mut Column
        } else { target as *mut Column };
        if !Column::reserve(column, source.len(), reserve) { reserved = false; break; }
    }
    if !reserved {
        for &index in indices {let index=index as usize;let target=targets[index];
            if target == 0 { continue; }
            let points = (*(target as *const Column)).points();
            output[index * 2] = points.as_ptr() as usize; output[index * 2 + 1] = points.len();
        }
        return -1;
    }
    for &index in indices {let index=index as usize;let target=targets[index];
        if retained_proof && owner.reused(index) { continue; }
        let Some(source) = owner.sources[index] else { continue; };
        let column = if target == 0 { &mut **owner.columns[index].as_mut().unwrap() as *mut Column }
            else { target as *mut Column };
        let points = Column::points_mut(column);
        points.clear();
        if owner.identity_remap { points.extend_from_slice(source); }
        else {
        for &point in source {
            let id = owner.remap[point as u32 as usize];
            points.push(point & !(u32::MAX as u64) | id as i64 as u64);
        };
        }
        output[index * 2] = points.as_ptr() as usize; output[index * 2 + 1] = points.len();
        Column::publish_hash(column, owner.hashes[index]);
    }
    owner.sources.fill(None);
    owner.committed = true; 1
}
/// Direct publication to the primary columns validated during preparation.
/// The source table must stay pinned/excluded and structurally unchanged.
/// Java refreshes list projections and adopts new empty/null owners afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_commit_source(owner: *mut Merge<'_>, target: *const super::source::Source,
    output: *mut usize, detached: *mut u8) -> i32 {
    commit_source(owner,target,output,detached,false)
}
/// The owned consumer needs extents only for actual point writes. Dense
/// compatibility output remains available through commit_source above.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_commit_source_extents(owner: *mut Merge<'_>, target: *const super::source::Source,
    output: *mut usize, detached: *mut u8) -> i32 {
    commit_source(owner,target,output,detached,true)
}
/// Complete primary publication, including previously empty/null recipients.
/// The merge relinquishes each new vector to the source, so releasing the
/// preparation never invalidates its points. Java projects escaped readers only.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_same_detail_commit_primary_extents(owner: *mut Merge<'_>, target: *mut super::source::Source,
    output: *mut usize, detached: *mut u8) -> i32 {
    let result=commit_source(owner,target,output,detached,true);
    if result==1 {
        let merge=&mut *owner;
        for &index in &merge.writes {
            if let Some(column)=merge.columns[index as usize].take() {
                (*target).adopt(index as usize,column);
            }
        }
    }
    result
}
unsafe fn commit_source(owner: *mut Merge<'_>, target: *const super::source::Source,
    output: *mut usize, detached: *mut u8, sparse:bool) -> i32 {
    let Some(merge) = owner.as_ref() else { return 0; };
    let Some(target_ref) = target.as_ref() else { return 0; };
    if output.is_null() || output as usize % align_of::<usize>() != 0 || detached.is_null()
        || merge.committed || merge.target_source != Some((target, target_ref.version())) { return 0; }
    // A producer-column merge has no source table to prove copies against.
    let proof = if let Some(mapping) = merge.producer_mapping {
        if target_ref.mapping_stamp() != mapping { return 0; }
        None
    } else {
        let Some((source, source_version, mapping)) = merge.source_proof else { return 0; };
        if (*source).version() != source_version || target_ref.mapping_stamp() != mapping { return 0; }
        Some((source, mapping))
    };
    let mut targets = [0usize; WIDTH];
    let detached = slice::from_raw_parts_mut(detached, WIDTH); detached.fill(0);
    let indices=if sparse {merge.writes.as_slice()} else {&ALL_COLUMNS};
    for &index in indices {let i=index as usize;
        if merge.flags[i] & 1 == 0 { continue; }
        targets[i] = target_ref.owner(i) as usize;
        if targets[i] == 0 { continue; }
        if merge.reused(i) { detached[i] = 1; continue; }
        let column = targets[i] as *mut Column;
        if !Column::make_owned(column) {
            // Earlier detachments may have changed CPU addresses. Publish all
            // selected existing views even though no values were committed.
            let views = slice::from_raw_parts_mut(output, WIDTH * 2); views.fill(0);
            for &index in indices {let j=index as usize;if merge.flags[j] & 1 != 0 {
                let column = target_ref.owner(j);
                if !column.is_null() { let points = (*column).points(); views[j*2] = points.as_ptr() as usize; views[j*2+1] = points.len(); }
            }}
            return -1;
        }
        detached[i] = 1;
    }
    let result = commit_checked(owner, targets.as_ptr(), output, true, sparse,
        &mut |points, count| points.try_reserve(count.saturating_sub(points.len())).is_ok());
    if let (1, Some((source, mapping))) = (result, proof) {
        let merge = &mut *owner;
        for &index in &merge.writes {let i=index as usize;
            let input = (*source).owner(i);
            if input.is_null() { continue; }
            let recipient = if targets[i] != 0 { targets[i] as *mut Column }
                else { &mut **merge.columns[i].as_mut().unwrap() as *mut Column };
            Column::publish_copy(recipient, (*input).proof(mapping));
        }
    }
    result
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

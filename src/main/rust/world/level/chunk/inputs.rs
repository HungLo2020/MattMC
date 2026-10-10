//! Retained authoritative block/counter bindings, shared by chunk consumers.
//! Retain the mutable owner rather than a generation: growth stays observable.
use super::{biomes, counters, live};
use std::sync::Arc;

struct Binding {
    blocks: Arc<live::Owner>,
    counts: Arc<counters::Owner>,
}
pub(crate) struct Chunk {
    pub(crate) min_y: i32,
    sections: Box<[Binding]>,
    biomes: Option<Arc<Biomes>>,
}
/// Shared immutable biome bindings; mutable primary owners remain authoritative.
pub(crate) struct Biomes { owners: Box<[Arc<biomes::Owner>]> }
impl Biomes {
    pub(crate) fn len(&self) -> usize { self.owners.len() }
    pub(crate) fn owner(&self, index: usize) -> &Arc<biomes::Owner> { &self.owners[index] }
    pub(crate) fn iter(&self) -> impl Iterator<Item=&Arc<biomes::Owner>> { self.owners.iter() }
}
/// Borrowed inputs for compatibility fixtures, or retained production bindings.
/// Consumers use this view without rebuilding pointer/reference arrays.
#[derive(Clone, Copy)]
pub(crate) enum Sections<'a> {
    Pairs(&'a [(&'a live::Owner, &'a counters::Owner)]),
    Blocks(&'a [&'a live::Owner], Option<&'a [&'a counters::Owner]>),
    Columns(&'a [(&'a live::Owner, &'a biomes::Owner)], Option<&'a [&'a counters::Owner]>),
    Retained(&'a Chunk),
}
impl<'a> Sections<'a> {
    pub(crate) fn len(self) -> usize {
        match self { Self::Pairs(s) => s.len(), Self::Blocks(s, _) => s.len(), Self::Columns(s, _) => s.len(), Self::Retained(c) => c.sections.len() }
    }
    pub(crate) fn valid_counts(self) -> bool {
        match self { Self::Blocks(s, Some(c)) => s.len() == c.len(), Self::Columns(s, Some(c)) => s.len() == c.len(), _ => true }
    }
    pub(crate) fn block(self, index: usize) -> &'a live::Owner {
        match self { Self::Pairs(s) => s[index].0, Self::Blocks(s, _) => s[index], Self::Columns(s, _) => s[index].0, Self::Retained(c) => &c.sections[index].blocks }
    }
    pub(crate) fn biome(self, index: usize) -> Option<&'a biomes::Owner> {
        match self { Self::Columns(s, _) => Some(s[index].1), Self::Retained(c) => c.biomes.as_ref().map(|b|b.owner(index).as_ref()), _ => None }
    }
    pub(crate) fn counter(self, index: usize) -> Option<&'a counters::Owner> {
        match self { Self::Pairs(s) => Some(s[index].1), Self::Blocks(_, c) | Self::Columns(_, c) => c.map(|c| c[index]), Self::Retained(c) => Some(&c.sections[index].counts) }
    }
}
/// Pointer pairs must come from the Arc-backed live/counter create/adopt APIs.
/// They are pinned through this call. Validation precedes any ownership change.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_inputs_create(pairs: *const usize, count: i32, min_y: i32) -> *mut Chunk {
    if pairs.is_null() || pairs as usize % std::mem::align_of::<usize>() != 0
        || !(1..=256).contains(&count) || min_y % 16 != 0 || min_y.unsigned_abs() > 1_000_000 {
        return std::ptr::null_mut();
    }
    let pairs = std::slice::from_raw_parts(pairs, count as usize * 2);
    if pairs.chunks_exact(2).any(|p| p[0] == 0 || p[1] == 0
        || p[0] % std::mem::align_of::<live::Owner>() != 0
        || p[1] % std::mem::align_of::<counters::Owner>() != 0) { return std::ptr::null_mut(); }
    let mut sections = Vec::new();
    if sections.try_reserve_exact(count as usize).is_err() { return std::ptr::null_mut(); }
    for p in pairs.chunks_exact(2) {
        let blocks = p[0] as *const live::Owner;
        let counts = p[1] as *const counters::Owner;
        Arc::increment_strong_count(blocks);
        Arc::increment_strong_count(counts);
        sections.push(Binding { blocks: Arc::from_raw(blocks), counts: Arc::from_raw(counts) });
    }
    Arc::into_raw(Arc::new(Chunk { min_y, sections: sections.into_boxed_slice(), biomes: None })) as *mut Chunk
}
/// Extend retained bindings with canonical biome owners only when a consumer
/// needs them. Height/emitter admission never touches biome compatibility state.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_inputs_with_biomes(owner: *const Chunk, biomes: *const usize) -> *mut Chunk {
    if owner.is_null() || owner as usize % std::mem::align_of::<Chunk>() != 0
        || biomes.is_null() || biomes as usize % std::mem::align_of::<usize>() != 0 { return std::ptr::null_mut(); }
    let pointer = mattmc_chunk_biome_inputs_create(biomes, (&*owner).sections.len() as i32);
    if pointer.is_null() { return std::ptr::null_mut(); }
    let result = mattmc_chunk_inputs_with_biome_inputs(owner, pointer);
    mattmc_chunk_biome_inputs_release(pointer);
    result
}
/// Borrow one existing immutable biome binding, never reconstruct its owner rows.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_inputs_with_biome_inputs(owner: *const Chunk, biomes: *const Biomes) -> *mut Chunk {
    if owner.is_null() || owner as usize % std::mem::align_of::<Chunk>() != 0
        || biomes.is_null() || biomes as usize % std::mem::align_of::<Biomes>() != 0 { return std::ptr::null_mut(); }
    let owner = &*owner;
    if owner.sections.len() != (*biomes).len() { return std::ptr::null_mut(); }
    let mut sections = Vec::new();
    if sections.try_reserve_exact(owner.sections.len()).is_err() { return std::ptr::null_mut(); }
    for binding in &owner.sections {
        sections.push(Binding { blocks: binding.blocks.clone(), counts: binding.counts.clone() });
    }
    Arc::increment_strong_count(biomes);
    Arc::into_raw(Arc::new(Chunk { min_y: owner.min_y, sections: sections.into_boxed_slice(),
        biomes: Some(Arc::from_raw(biomes)) })) as *mut Chunk
}
/// Typed biome primary owners only. Zero rows are representable so world
/// admission rejects malformed height exactly as the older pointer-span bridge.
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_biome_inputs_create(pointers: *const usize, count: i32) -> *mut Biomes {
    if pointers.is_null() || pointers as usize % std::mem::align_of::<usize>() != 0
        || !(0..=1024).contains(&count) { return std::ptr::null_mut(); }
    let pointers=std::slice::from_raw_parts(pointers,count as usize);
    if pointers.iter().any(|&p| p==0 || p%std::mem::align_of::<biomes::Owner>()!=0) { return std::ptr::null_mut(); }
    let mut owners=Vec::new();
    if owners.try_reserve_exact(pointers.len()).is_err() { return std::ptr::null_mut(); }
    for &p in pointers {
        let pointer=p as *const biomes::Owner;
        Arc::increment_strong_count(pointer);owners.push(Arc::from_raw(pointer));
    }
    Arc::into_raw(Arc::new(Biomes { owners: owners.into_boxed_slice() })) as *mut Biomes
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_biome_inputs_release(owner: *mut Biomes) {
    if !owner.is_null() { drop(Arc::from_raw(owner)); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_chunk_inputs_release(owner: *mut Chunk) {
    if !owner.is_null() { drop(Arc::from_raw(owner)); }
}

#[cfg(test)]
mod tests;

//! Ordered DH material identity and remapping, independent of Java bindings.
use std::{collections::HashMap, slice, sync::Arc};
#[derive(Eq, PartialEq, Hash)]
struct Key { biome: Box<[u16]>, state: Box<[u16]> }
impl Key {
    fn hash(&self) -> i32 {
        fn string(units: &[u16]) -> i32 { units.iter().fold(0i32, |h, &c| h.wrapping_mul(31).wrapping_add(c as i32)) }
        1953i32.wrapping_add(string(&self.biome).wrapping_mul(31)).wrapping_add(string(&self.state))
    }
}
#[derive(Clone)]
struct Entry { key: Arc<Key>, state_id: u32, tint: Option<Arc<super::tint::Identity>> }
impl std::ops::Deref for Entry { type Target = Key; fn deref(&self) -> &Key { &self.key } }
pub struct Map {
    entries: Vec<Entry>,
    indexed: Vec<u8>,
    /// By `lookup_key`, so a deduplicating add can find its entry without
    /// copying the caller's text.
    lookup: HashMap<Box<[u16]>, i32>,
    list_hash: i32,
    scratch: Vec<u16>,
}
impl Default for Map {
    fn default() -> Self { Self { entries: Vec::new(), indexed: Vec::new(), lookup: HashMap::new(), list_hash: 1, scratch: Vec::new() } }
}
/// One lookup key for a (biome, state) pair: the biome's length, then both texts.
fn lookup_key(biome: &[u16], state: &[u16], out: &mut Vec<u16>) {
    out.clear();
    let length = biome.len() as u32;
    out.extend_from_slice(&[length as u16, (length >> 16) as u16]);
    out.extend_from_slice(biome);
    out.extend_from_slice(state);
}
impl Map {
    pub(super) fn tint_biome(&self, index: usize) -> Option<&Arc<super::tint::Identity>> { self.entries.get(index)?.tint.as_ref() }
    fn new() -> Self { Self::default() }
    pub(super) fn state_ids(&self) -> impl ExactSizeIterator<Item = u32> + '_ { self.entries.iter().map(|entry| entry.state_id) }
    fn add(&mut self, entry: Entry, mode: i32) -> Result<i32, ()> {
        let mut scratch = std::mem::take(&mut self.scratch);
        lookup_key(&entry.biome, &entry.state, &mut scratch);
        let result = self.add_keyed(entry, &scratch, mode);
        self.scratch = scratch;
        result
    }
    /// `add` for borrowed text: a deduplicated pair already present costs no allocation.
    fn add_units(&mut self, biome: &[u16], state: &[u16], state_id: u32, mode: i32) -> Option<i32> {
        let mut scratch = std::mem::take(&mut self.scratch);
        lookup_key(biome, state, &mut scratch);
        let found = if mode == 0 { self.lookup.get(&scratch[..]).copied() } else { None };
        let result = match found {
            Some(id) => Some(id),
            None => owned(biome).zip(owned(state)).and_then(|(biome, state)| {
                self.add_keyed(Entry { key: Arc::new(Key { biome, state }), state_id, tint: None }, &scratch, mode).ok()
            }),
        };
        self.scratch = scratch;
        result
    }
    fn add_keyed(&mut self, entry: Entry, lookup_key: &[u16], mode: i32) -> Result<i32, ()> {
        if mode == 0 { if let Some(&id) = self.lookup.get(lookup_key) { return Ok(id); } }
        let id = i32::try_from(self.entries.len()).map_err(|_| ())?;
        self.entries.try_reserve(1).map_err(|_| ())?;
        self.indexed.try_reserve(1).map_err(|_| ())?;
        if mode != 2 { self.lookup.try_reserve(1).map_err(|_| ())?; }
        self.list_hash = self.list_hash.wrapping_mul(31).wrapping_add(entry.hash());
        let key = if mode != 2 {
            let mut key = Vec::new(); key.try_reserve_exact(lookup_key.len()).map_err(|_| ())?;
            key.extend_from_slice(lookup_key); Some(key.into_boxed_slice())
        } else { None };
        self.entries.push(entry); self.indexed.push(u8::from(mode != 2));
        if let Some(key) = key { self.lookup.insert(key, id); }
        Ok(id)
    }
    fn hash(&self, pos: u64) -> i32 {
        // Frozen folds the same full-list hash once for every entry. Exponentiate
        // that affine transform with wrapped arithmetic, without changing it.
        let mut value = (pos ^ (pos >> 32)) as i32;
        let (mut a, mut b, mut n) = (31i32, self.list_hash, self.entries.len());
        while n != 0 {
            if n & 1 != 0 { value = value.wrapping_mul(a).wrapping_add(b); }
            b = b.wrapping_mul(a).wrapping_add(b); a = a.wrapping_mul(a); n >>= 1;
        }
        value
    }
}
unsafe fn units<'a>(pointer: *const u16, length: i32) -> Option<&'a [u16]> {
    if length < 0 || length != 0 && (pointer.is_null() || pointer as usize % 2 != 0) { return None; }
    Some(if length == 0 { &[] } else { slice::from_raw_parts(pointer, length as usize) })
}
fn owned(units: &[u16]) -> Option<Box<[u16]>> {
    let mut result = Vec::new(); result.try_reserve_exact(units.len()).ok()?;
    result.extend_from_slice(units); Some(result.into_boxed_slice())
}
#[no_mangle]
pub extern "C" fn mattmc_dh_id_map_create() -> *mut Map { Box::into_raw(Box::new(Map::new())) }
/// Modes: deduplicate, append/index (including duplicates), append/unindexed.
/// Unindexed imports preserve Frozen deserialize's deliberately empty lookup.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_id_map_add(owner: *mut Map, biome: *const u16, biome_len: i32,
    state: *const u16, state_len: i32, state_id: u32, mode: i32) -> i32 {
    if owner.is_null() || !(0..=2).contains(&mode) { return -1; }
    let (Some(biome), Some(state)) = (units(biome, biome_len), units(state, state_len)) else { return -2; };
    (*owner).add_units(biome, state, state_id, mode).unwrap_or(-2)
}
/// Returns the completed input prefix, including on allocation failure. Java
/// publishes those bindings before reporting the failure; no stale ID escapes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_id_map_merge(owner: *mut Map, source: *const Map, output: *mut i32, count: i32, mode: i32) -> i32 {
    if owner.is_null() || source.is_null() || count < 0 || !(0..=1).contains(&mode)
        || count != 0 && (output.is_null() || output as usize % 4 != 0) || (*source).entries.len() != count as usize { return -1; }
    if owner == source as *mut Map {
        // Original self-merge fixes input size before appending missing entries.
        let mut keys = Vec::new(); if keys.try_reserve_exact(count as usize).is_err() { return 0; }
        keys.extend((*source).entries.iter().cloned());
        for (i, key) in keys.into_iter().enumerate() {
            let Ok(id) = (*owner).add(key, mode) else { return i as i32; }; output.add(i).write(id);
        }
    } else {
        for (i, key) in (*source).entries.iter().enumerate() {
            let Ok(id) = (*owner).add(key.clone(), mode) else { return i as i32; }; output.add(i).write(id);
        }
    }
    count
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_id_map_hash(owner: *const Map, pos: u64) -> i32 { (*owner).hash(pos) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_id_map_indexed(owner: *const Map, index: i32) -> i32 {
    owner.as_ref().and_then(|m| m.indexed.get(index as usize)).map_or(-1, |&v| v as i32)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_id_map_release(owner: *mut Map) { if !owner.is_null() { drop(Box::from_raw(owner)); } }
#[cfg(test)] mod tests;

/// Retain an immutable CPU identity lease, not a borrowed Java/native address.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_id_map_bind_tint(owner:*mut Map,index:i32,biome:*const super::tint::Biome)->i32 {
    let (Some(owner),Some(biome))=(owner.as_mut(),biome.as_ref()) else{return 0;};
    let Some(entry)=owner.entries.get_mut(index as usize) else{return 0;};
    if entry.tint.is_none() {entry.tint=Some(biome.0.clone());}1
}

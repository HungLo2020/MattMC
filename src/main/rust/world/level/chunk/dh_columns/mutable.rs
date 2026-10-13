//! Mutable DH column storage. Java holds CPU leases, never a mirrored point array.
//! Callers retain the original DH exclusion rules; views expire on mutation.
use std::{cell::Cell, slice, sync::{Arc, atomic::{AtomicU64, AtomicUsize, Ordering}}};
use super::Columns;

// Shared ownership does not add concurrent mutation: every point access remains
// under DH's original source/list exclusion. Leases hold raw pointers, never
// persistent Rust references. Atomic counts allow arena cleaners to release a
// different lease safely, without freeing an allocation still used by a source.
enum Backing { Producer(Arc<Columns>, usize, usize) }
static NEXT_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);
pub(super) fn identity() -> u64 {
    let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
    if id == u64::MAX { std::process::abort(); } // Never allow pointer-reuse/epoch ABA.
    id
}
#[derive(Clone, Copy, PartialEq, Eq)]
pub(super) struct CopyProof { source: u64, revision: u64, mapping: [u64; 2] }
pub(crate) struct Column {
    // Frozen LongArrayList accepts a negative logical size on corrupt input.
    // Keep that compatibility state separate from every actual Rust slice.
    invalid_len: Cell<i32>,
    points: Vec<u64>,
    backing: Option<Backing>,
    references: AtomicUsize,
    hash: AtomicU64,
    maximum_id: Cell<u64>,
    identity: u64,
    revision: Cell<u64>,
    copied: Cell<Option<CopyProof>>,
}
impl Column {
    #[inline] pub(super) fn is_owned(&self) -> bool { self.backing.is_none() }
    pub(crate) fn new(points: Vec<u64>) -> Self {
        Self { invalid_len: Cell::new(0), points, backing: None, references: AtomicUsize::new(1), hash: AtomicU64::new(0),
            maximum_id: Cell::new(0),
            identity: identity(), revision: Cell::new(0), copied: Cell::new(None) }
    }
    pub(super) fn original_producer(&self, producer: *const Columns, first: usize, count: usize) -> bool {
        matches!(&self.backing,Some(Backing::Producer(owner,start,len)) if Arc::as_ptr(owner)==producer && *start==first && *len==count)
    }
    #[inline]
    pub(crate) fn points(&self) -> &[u64] {
        match &self.backing {
            Some(Backing::Producer(owner, first, count)) => &owner.points[*first..*first + *count],
            None => &self.points,
        }
    }
    pub(super) fn logical_len(&self) -> i64 { if self.invalid_len.get()<0 {self.invalid_len.get() as i64}else{self.points().len() as i64} }
    pub(super) fn invalid_size(&self) -> bool {self.invalid_len.get()<0}
    pub(super) fn negative_size(&self, count:i32) {debug_assert!(count<0);self.invalid_len.set(count);}
    /// All mutable point access invalidates the primary hash. Callers exclude
    /// reads/writes and must detach immutable backing before using this method.
    /// Borrow only these fields: arena cleanup may touch the atomic refcount.
    #[inline]
    pub(crate) unsafe fn points_mut<'a>(owner: *mut Self) -> &'a mut Vec<u64> {
        debug_assert!((*owner).backing.is_none());
        (*owner).invalid_len.set(0);
        (*owner).hash.store(0,Ordering::Relaxed);
        (*owner).maximum_id.set(0);
        (*owner).copied.set(None);
        (*owner).revision.set((*owner).revision.get().checked_add(1).unwrap_or_else(|| std::process::abort()));
        &mut (*owner).points
    }
    /// Capacity changes preserve values, hashes and exact copy proofs. The
    /// private callback may reserve only; callers publish relocated extents.
    pub(super) unsafe fn reserve(owner: *mut Self, count: usize,
        reserve: &mut impl FnMut(&mut Vec<u64>, usize) -> bool) -> bool {
        debug_assert!((*owner).backing.is_none());
        reserve(&mut (*owner).points, count)
    }
    /// One bounded scalar admission cache, invalidated by every point write.
    /// It retains no mapping/source and works across identity-map growth/shrink.
    #[inline]
    pub(super) fn ids_fit(&self, count: usize) -> bool {
        if self.points().is_empty() { return true; }
        let mut cached = self.maximum_id.get();
        if cached >> 32 == 0 {
            let max = self.points().iter().map(|p| *p as u32).max().unwrap();
            cached = (1u64 << 32) | max as u64; self.maximum_id.set(cached);
        }
        (cached as u32 as usize) < count
    }
    #[inline]
    pub(crate) fn hash(&self) -> i32 {
        let cached = self.hash.load(Ordering::Relaxed);
        if cached >> 32 != 0 { return cached as i32; }
        let hash = self.points().iter().fold(1i32, |h, &p|
            h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32));
        self.hash.store((1u64 << 32) | hash as u32 as u64,Ordering::Relaxed); hash
    }
    pub(crate) unsafe fn publish_hash(owner: *mut Self, hash: i32) {
        (*owner).hash.store((1u64 << 32) | hash as u32 as u64,Ordering::Relaxed);
    }
    #[inline] pub(super) fn proof(&self, mapping: [u64; 2]) -> CopyProof {
        CopyProof { source: self.identity, revision: self.revision.get(), mapping }
    }
    #[inline] pub(super) fn copied_from(&self, proof: CopyProof) -> bool { self.copied.get() == Some(proof) }
    pub(super) unsafe fn publish_copy(owner: *mut Self, proof: CopyProof) { (*owner).copied.set(Some(proof)); }
    pub(crate) fn allocation(&self) -> Option<(usize, usize)> {
        (self.points.capacity() != 0).then(|| (self.points.as_ptr() as usize,
            self.points.as_ptr() as usize + self.points.capacity() * 8))
    }
    #[cfg(test)] pub(crate) fn into_points(self) -> Vec<u64> { self.points }
    #[inline]
    pub(super) unsafe fn make_owned(owner: *mut Self) -> bool {
        if (*owner).backing.is_none() { return true; }
        let mut points = Vec::new();
        if points.try_reserve_exact((*owner).points().len()).is_err() { return false; }
        points.extend_from_slice((*owner).points());
        (*owner).points = points; (*owner).backing = None; true
    }
}
/// A retained primary column, without another point allocation or Java owner.
pub(crate) struct Lease(*mut Column);
impl Lease {
    pub(super) fn adopt(owner: Box<Column>) -> Self { Self(Box::into_raw(owner)) }
    pub(super) fn into_raw(self) -> *mut Column {
        let owner = self.0; std::mem::forget(self); owner
    }
    #[inline] pub(crate) fn identity(&self) -> usize { self.0 as usize }
    pub(crate) unsafe fn retain(owner: *mut Column) -> Self {
        let old = (*owner).references.fetch_add(1, Ordering::Relaxed);
        if old >= isize::MAX as usize { std::process::abort(); }
        Self(owner)
    }
    // Unsafe because the ABI caller must exclude mutations for this borrow.
    #[inline] pub(crate) unsafe fn points(&self) -> &[u64] { (*self.0).points() }
}
impl Drop for Lease {
    fn drop(&mut self) { unsafe { release(self.0); } }
}
unsafe fn release(owner: *mut Column) {
    if (*owner).references.fetch_sub(1, Ordering::Release) == 1 {
        std::sync::atomic::fence(Ordering::Acquire);
        drop(Box::from_raw(owner));
    }
}
/// Retain an immutable producer span. Detach only on its first point write.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_shared(kind: i32, owner: *const (), first: i64, count: i32) -> *mut Column {
    if owner.is_null() || owner as usize % 8 != 0 || first < 0 || count < 0 { return std::ptr::null_mut(); }
    let (first, count) = (first as usize, count as usize);
    let Some(end) = first.checked_add(count) else { return std::ptr::null_mut(); };
    let backing = match kind {
        0 => {
            let owner = owner.cast::<Columns>();
            if end > (&(*owner).points).len() { return std::ptr::null_mut(); }
            Arc::increment_strong_count(owner);
            Backing::Producer(Arc::from_raw(owner), first, count)
        },
        _ => return std::ptr::null_mut(),
    };
    Box::into_raw(Box::new(Column { backing: Some(backing), ..Column::new(Vec::new()) }))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_make_owned(owner: *mut Column) -> i32 {
    if owner.is_null() { 0 } else if Column::make_owned(owner) { 1 } else { -1 }
}

unsafe fn input<'a, T>(data: *const T, count: i32) -> Option<&'a [T]> {
    if count < 0 || (count != 0 && (data.is_null() || data as usize % std::mem::align_of::<T>() != 0)) {
        return None;
    }
    Some(if count == 0 { &[] } else { slice::from_raw_parts(data, count as usize) })
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_create(data: *const u64, count: i32) -> *mut Column {
    let Some(data) = input(data, count) else { return std::ptr::null_mut(); };
    let mut points = Vec::new();
    if points.try_reserve_exact(data.len()).is_err() { return std::ptr::null_mut(); }
    points.extend_from_slice(data);
    Box::into_raw(Box::new(Column::new(points)))
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_release(owner: *mut Column) {
    if !owner.is_null() { release(owner); }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_view(owner: *const Column, out: *mut usize) -> i32 {
    if owner.is_null() || out.is_null() || out as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let points = (*owner).points();
    slice::from_raw_parts_mut(out, 2).copy_from_slice(&[points.as_ptr() as usize, (*owner).logical_len() as usize]);
    1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_set(owner: *mut Column, index: i32, value: u64) -> i32 {
    if owner.is_null() { return 0; }
    if !Column::make_owned(owner) { return -1; }
    let points = Column::points_mut(owner);
    let Some(point) = points.get_mut(index as usize) else { return 0; };
    *point = value; 1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_add(owner: *mut Column, index: i32, value: u64) -> i32 {
    if owner.is_null() { return 0; }
    if !Column::make_owned(owner) { return -1; }
    let points = Column::points_mut(owner);
    if index < 0 || index as usize > points.len() || points.len() == i32::MAX as usize { return 0; }
    if points.try_reserve(1).is_err() { return -1; }
    points.insert(index as usize, value); 1
}

/// Grow with zero-filled points or truncate in one operation. The caller pins
/// and excludes the CPU owner; no Java callback or point export is involved.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_resize(owner: *mut Column, count: i32) -> i32 {
    if owner.is_null() { return 0; }
    if count>=0 && (*owner).invalid_size() {return 2;}
    if !Column::make_owned(owner) { return -1; }
    let points = Column::points_mut(owner);
    if count<0 {points.clear();(*owner).negative_size(count);return 1;}
    let count = count as usize;
    if count > points.len() && points.try_reserve(count - points.len()).is_err() { return -1; }
    points.resize(count, 0);
    1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_remove(owner: *mut Column, from: i32, to: i32) -> i32 {
    if owner.is_null() { return 0; }
    if !Column::make_owned(owner) { return -1; }
    let points = Column::points_mut(owner);
    if from < 0 || to < from || to as usize > points.len() { return 0; }
    points.drain(from as usize..to as usize); 1
}

/// Self-insertion reads the original range before growth/mutation, without an
/// aliasing mutable reference to the borrowed input allocation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_insert(owner: *mut Column, index: i32, data: *const u64, count: i32) -> i32 {
    let Some(previous) = owner.as_ref() else { return 0; };
    if previous.backing.is_some() { return 0; }
    if index < 0 || index as usize > previous.points.len() || count < 0
        || previous.points.len().checked_add(count as usize).is_none_or(|n| n > i32::MAX as usize) { return 0; }
    let Some(values) = input(data, count) else { return 0; };
    let start = previous.points.as_ptr() as usize;
    let end = start + previous.points.capacity() * 8;
    let address = data as usize;
    let alias = count != 0 && address < end && address.saturating_add(count as usize * 8) > start;
    let copy = alias.then(|| values.to_vec());
    let values = copy.as_deref().unwrap_or(values);
    let points = Column::points_mut(owner);
    if points.try_reserve(values.len()).is_err() { return -1; }
    points.splice(index as usize..index as usize, values.iter().copied()); 1
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_hash(data: *const u64, count: i32) -> i32 {
    input(data, count).map_or(0, |points| points.iter().fold(1i32, |h, &p|
        h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32)))
}
/// Bounded primary hash for Java list compatibility. Oversized columns keep the
/// ordinary span hashing route; validity and invalidation remain native.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_cached_hash(owner: *const Column) -> u64 {
    let Some(owner) = owner.as_ref() else { return 0; };
    if owner.points().len() > 4096 { return 0; }
    (1u64 << 32) | owner.hash() as u32 as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_owner_hash(owner: *const Column) -> i32 {
    owner.as_ref().map_or(0, Column::hash)
}

/// Match the old loop's successful mutation prefix if a later ID is invalid.
/// Return the first failing point index, or -1 on success.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_remap(owner: *mut Column, ids: *const i32, count: i32) -> i32 {
    let Some(ids) = input(ids, count) else { return 0; };
    if owner.is_null() { return 0; }
    if !Column::make_owned(owner) { return -1; }
    let points = Column::points_mut(owner);
    for (i, point) in points.iter_mut().enumerate() {
        let id = *point as u32 as i32;
        let Some(&mapped) = ids.get(id as usize) else { return i as i32; };
        *point = (*point & !0xffff_ffff) | (mapped as i64 as u64);
    }
    -1
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn primary_hash_tracks_every_mutation_and_failed_remap_prefix() {
        unsafe {
            let owner = mattmc_dh_column_create([0u64, 1, 2].as_ptr(), 3);
            let check = || {
                let points = (*owner).points();
                assert_eq!((*owner).hash(), mattmc_dh_column_hash(points.as_ptr(), points.len() as i32));
                assert_eq!((*owner).hash.load(Ordering::Relaxed), (1u64 << 32) | (*owner).hash() as u32 as u64);
                for count in 0..5 {
                    assert_eq!((*owner).ids_fit(count), points.iter().all(|p| (*p as u32 as usize) < count));
                }
            };
            check();
            assert_eq!(mattmc_dh_column_set(owner, 0, u64::MAX), 1); check();
            assert_eq!(mattmc_dh_column_add(owner, 0, 0), 1); check();
            assert_eq!(mattmc_dh_column_remove(owner, 1, 2), 1); check();
            let input = (*owner).points().as_ptr();
            assert_eq!(mattmc_dh_column_insert(owner, 1, input, 3), 1); check();
            assert_eq!(mattmc_dh_column_remap(owner, [9,8].as_ptr(), 2), 3);
            assert_eq!((*owner).points(), [9,9,8,2,1,2]); check();
            assert_eq!(mattmc_dh_column_set(owner, -1, 0), 0); check();
            assert_eq!(mattmc_dh_column_remove(owner, 0, 6), 1); check();
            assert_eq!((*owner).hash(), 1);
            assert_eq!(mattmc_dh_column_cached_hash(owner), (1u64 << 32) | 1);
            Column::points_mut(owner).resize(4097, u64::MAX);
            assert_eq!(mattmc_dh_column_cached_hash(owner),0);
            assert_eq!((*owner).hash.load(Ordering::Relaxed),0,"Bounded decline cannot populate an oversized hash");
            assert_eq!(mattmc_dh_column_owner_hash(owner),mattmc_dh_column_hash((*owner).points().as_ptr(),4097));
            mattmc_dh_column_release(owner);
        }
    }
    #[test]
    fn concurrent_hash_readers_share_primary_cache_without_serializing_readers() {
        unsafe {
            let owner=mattmc_dh_column_create([u64::MAX,1,2].as_ptr(),3);
            let expected=mattmc_dh_column_hash((*owner).points().as_ptr(),3);
            let address=owner as usize;
            std::thread::scope(|scope| { for _ in 0..4 { scope.spawn(move || {
                for _ in 0..1000 {
                    assert_eq!(mattmc_dh_column_cached_hash(address as *const Column),(1u64<<32)|expected as u32 as u64);
                }
            }); }});
            mattmc_dh_column_release(owner);
        }
    }
    #[test]
    fn native_storage_handles_aliasing_growth_and_failed_remap_prefix() {
        unsafe {
            let owner = mattmc_dh_column_create([0u64, 1, 2].as_ptr(), 3);
            assert!(!owner.is_null());
            let data = (*owner).points.as_ptr();
            assert_eq!(mattmc_dh_column_insert(owner, 1, data, 3), 1);
            assert_eq!((*owner).points, [0, 0, 1, 2, 1, 2]);
            assert_eq!(mattmc_dh_column_remove(owner, 1, 4), 1);
            assert_eq!((*owner).points, [0, 1, 2]);
            assert_eq!(mattmc_dh_column_remap(owner, [9, 8].as_ptr(), 2), 2);
            assert_eq!((*owner).points, [9, 8, 2]);
            assert_eq!(mattmc_dh_column_remove(owner, 3, 2), 0);
            assert_eq!(mattmc_dh_column_insert(owner, -1, std::ptr::null(), 0), 0);
            mattmc_dh_column_release(owner);
        }
    }
}

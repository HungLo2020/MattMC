//! Authoritative DH generation, compression and uniformity planes.
//! Native and Java CPU views share these allocations under DH caller exclusion.
use std::{cell::UnsafeCell, sync::Arc};

pub(crate) struct Metadata {
    planes: [UnsafeCell<Vec<i8>>; 3],
}
impl Metadata {
    pub(super) fn new() -> Self {
        Self { planes: std::array::from_fn(|_| UnsafeCell::new(vec![0; 4096])) }
    }
    /// No borrowed plane may survive a resize, point update or caller write.
    pub(super) unsafe fn plane(&self, kind: usize) -> &[i8] {
        &*self.planes[kind].get()
    }
    pub(super) unsafe fn plane_mut(&self, kind: usize) -> &mut Vec<i8> {
        &mut *self.planes[kind].get()
    }
    pub(super) unsafe fn canonical(&self) -> bool {
        (0..3).all(|k| self.plane(k).len() == 4096)
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_metadata(source: *const super::source::Source) -> *const Metadata {
    source.as_ref().map_or(std::ptr::null(), |s| Arc::into_raw(s.metadata.clone()))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_release(owner: *const Metadata) {
    if !owner.is_null() { drop(Arc::from_raw(owner)); }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_view(owner: *const Metadata, kind: i32, out: *mut usize) -> i32 {
    let Some(owner) = owner.as_ref() else { return 0; };
    if !(0..3).contains(&kind) || out.is_null() || out as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let plane = owner.plane(kind as usize);
    out.write(plane.as_ptr() as usize); out.add(1).write(plane.len()); 1
}
// Resizing/insertion/removal belongs to Rust. Uniformity has a fixed extent.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_edit(owner: *const Metadata, kind: i32, op: i32,
    index: i32, count: i32, data: *const i8, value: i8) -> i32 {
    let Some(owner) = owner.as_ref() else { return 0; };
    if !(0..2).contains(&kind) || index < 0 || count < 0 { return 0; }
    let plane = owner.plane_mut(kind as usize);
    let (index, count) = (index as usize, count as usize);
    match op {
        0 => {
            if plane.try_reserve(count.saturating_sub(plane.len())).is_err() { return -1; }
            plane.resize(count, 0);
        },
        1 | 2 => {
            let count = if op == 1 { 1 } else { count };
            if index > plane.len() || plane.len().checked_add(count).is_none_or(|n| n > i32::MAX as usize)
                || op == 2 && count != 0 && data.is_null() { return 0; }
            if plane.try_reserve(count).is_err() { return -1; }
            if op == 1 { plane.insert(index, value); }
            else if count != 0 { plane.splice(index..index, std::slice::from_raw_parts(data, count).iter().copied()); }
        },
        3 => {
            if index.checked_add(count).is_none_or(|end| end > plane.len()) { return 0; }
            plane.drain(index..index + count);
        },
        _ => return 0,
    }
    1
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_metadata_hash(owner: *const Metadata, kind: i32) -> i32 {
    let Some(owner) = owner.as_ref() else { return 0; };
    if !(0..3).contains(&kind) { return 0; }
    owner.plane(kind as usize).iter().fold(1i32, |h, &b| h.wrapping_mul(31).wrapping_add(b as i32))
}

#[cfg(test)]
mod tests {
    use super::*;
    use super::super::source::{mattmc_dh_source_create, mattmc_dh_source_release};
    #[test]
    fn source_and_retained_cpu_views_share_signed_values_and_release_independently() {
        unsafe {
            let source = mattmc_dh_source_create();
            let metadata = mattmc_dh_source_metadata(source);
            let weak = Arc::downgrade(&(*source).metadata);
            let mut view = [0usize; 2];
            assert_eq!(mattmc_dh_metadata_view(metadata, 0, view.as_mut_ptr()), 1);
            assert_eq!(view[1], 4096);
            (view[0] as *mut i8).write(-128);
            assert_eq!((*source).metadata.plane(0)[0], -128);
            assert_eq!(mattmc_dh_metadata_hash(metadata, 0), (*source).metadata.plane(0).iter()
                .fold(1i32, |h,&v| h.wrapping_mul(31).wrapping_add(v as i32)));
            mattmc_dh_source_release(source);
            assert_eq!((*metadata).plane(0)[0], -128);
            assert!(weak.upgrade().is_some());
            mattmc_dh_metadata_release(metadata);
            assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn edits_preserve_prefixes_zero_growth_and_decline_invalid_uniform_or_ranges() {
        unsafe {
            let metadata = Metadata::new();
            metadata.plane_mut(0)[0..3].copy_from_slice(&[-128, -1, 127]);
            assert_eq!(mattmc_dh_metadata_edit(&metadata,0,0,0,2,std::ptr::null(),0),1);
            assert_eq!(metadata.plane(0),[-128,-1]);
            assert!(!metadata.canonical());
            assert_eq!(mattmc_dh_metadata_edit(&metadata,0,0,0,4,std::ptr::null(),0),1);
            assert_eq!(metadata.plane(0),[-128,-1,0,0]);
            assert_eq!(mattmc_dh_metadata_edit(&metadata,0,2,1,3,[1i8,2,3].as_ptr(),0),1);
            assert_eq!(metadata.plane(0),[-128,1,2,3,-1,0,0]);
            assert_eq!(mattmc_dh_metadata_edit(&metadata,0,3,1,3,std::ptr::null(),0),1);
            assert_eq!(metadata.plane(0),[-128,-1,0,0]);
            assert_eq!(mattmc_dh_metadata_edit(&metadata,0,3,1,20,std::ptr::null(),0),0);
            assert_eq!(metadata.plane(0),[-128,-1,0,0]);
            assert_eq!(mattmc_dh_metadata_edit(&metadata,2,0,0,0,std::ptr::null(),0),0);
            assert_eq!(metadata.plane(2).len(),4096);
        }
    }
}

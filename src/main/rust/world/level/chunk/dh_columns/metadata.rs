//! Authoritative DH generation, compression and uniformity planes.
//! Native and Java CPU views share these allocations under DH caller exclusion.
use std::{cell::UnsafeCell, sync::Arc};

pub(crate) struct Metadata {
    /// Generation, compression, uniformity, footprint presence (Rust-maintained).
    planes: [UnsafeCell<Vec<i8>>; 4],
}
impl Metadata {
    pub(super) fn new() -> Self {
        Self { planes: std::array::from_fn(|_| UnsafeCell::new(vec![0; 4096])) }
    }
    /// Planes have exactly one value per column. No borrowed plane may survive a point update or caller write.
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
    if !(0..4).contains(&kind) || out.is_null() || out as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let plane = owner.plane(kind as usize);
    out.write(plane.as_ptr() as usize); out.add(1).write(plane.len()); 1
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
}

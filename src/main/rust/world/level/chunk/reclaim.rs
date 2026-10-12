//! Batched release of chunk-section native objects that Java no longer reaches.
//!
//! Java's `NativeReclaimer` queues each owner's raw pointer when its Java
//! owner becomes unreachable and releases them here in batches, instead of
//! one downcall (and one JDK cleaner session) per object.

use std::ffi::c_void;

/// `NativeReclaimer` kind codes.
pub(crate) const LIVE_SECTION: u8 = 1;
pub(crate) const LIVE_SECTION_VIEW: u8 = 2;
pub(crate) const LIVE_BIOME: u8 = 3;
pub(crate) const LIVE_BIOME_VIEW: u8 = 4;
pub(crate) const SECTION_COUNTERS: u8 = 5;
pub(crate) const DH_CONTRIBUTORS: u8 = 6;

extern "C" {
    // The owners' own release exports; each drops one reference it was given.
    fn mattmc_live_section_release(owner: *mut c_void);
    fn mattmc_live_section_view_release(view: *const c_void);
    fn mattmc_live_biome_release(owner: *const c_void);
    fn mattmc_live_biome_view_release(lease: *const c_void);
    fn mattmc_section_counters_release(owner: *mut c_void);
    fn mattmc_dh_contributors_release(owner: *mut c_void);
}

/// Releases `count` pointers, each of its kind. Returns how many were
/// released; an unknown kind stops the batch there (Java reports it).
///
/// # Safety
/// `kinds` and `pointers` readable for `count` entries; every pointer is a
/// live reference of its kind that the caller will not use again.
#[no_mangle]
pub unsafe extern "C" fn mattmc_native_release_batch(kinds: *const u8, pointers: *const *mut c_void, count: u32) -> u32 {
    if kinds.is_null() || pointers.is_null() {
        return 0;
    }
    for index in 0..count as usize {
        let pointer = *pointers.add(index);
        match *kinds.add(index) {
            LIVE_SECTION => mattmc_live_section_release(pointer),
            LIVE_SECTION_VIEW => mattmc_live_section_view_release(pointer),
            LIVE_BIOME => mattmc_live_biome_release(pointer),
            LIVE_BIOME_VIEW => mattmc_live_biome_view_release(pointer),
            SECTION_COUNTERS => mattmc_section_counters_release(pointer),
            DH_CONTRIBUTORS => mattmc_dh_contributors_release(pointer),
            _ => return index as u32,
        }
    }
    count
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn releases_counters_and_stops_at_unknown_kinds() {
        extern "C" {
            fn mattmc_section_counters_create(packed: u64) -> *mut c_void;
        }
        unsafe {
            let first = mattmc_section_counters_create(0);
            let second = mattmc_section_counters_create(7);
            assert!(!first.is_null() && !second.is_null());
            let pointers = [first, second];
            assert_eq!(2, mattmc_native_release_batch([SECTION_COUNTERS; 2].as_ptr(), pointers.as_ptr(), 2));
            let unknown = [std::ptr::null_mut::<c_void>()];
            assert_eq!(0, mattmc_native_release_batch([0u8].as_ptr(), unknown.as_ptr(), 1));
            assert_eq!(0, mattmc_native_release_batch(std::ptr::null(), pointers.as_ptr(), 2));
        }
    }
}

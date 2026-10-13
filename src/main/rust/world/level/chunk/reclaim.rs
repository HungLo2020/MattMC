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
// Kind 7 (saved contributor batches) is retired; saved footprints decode into their source.
pub(crate) const SECTION_SNAPSHOT: u8 = 8;
pub(crate) const LIGHT_LAYER: u8 = 9;
pub(crate) const LIGHT_VIEW: u8 = 10;
pub(crate) const DH_HEIGHTMAPS: u8 = 11;
pub(crate) const DH_SOURCES: u8 = 12;
pub(crate) const DH_LIGHT_FIELD: u8 = 13;
pub(crate) const CHUNK_INPUTS: u8 = 14;
pub(crate) const BIOME_CHUNK_INPUTS: u8 = 15;
pub(crate) const LIGHT_MAP: u8 = 16;
pub(crate) const LIGHT_MAP_VIEW: u8 = 17;

extern "C" {
    // The owners' own release exports; each drops one reference it was given.
    fn mattmc_live_section_release(owner: *mut c_void);
    fn mattmc_live_section_view_release(view: *const c_void);
    fn mattmc_live_biome_release(owner: *const c_void);
    fn mattmc_live_biome_view_release(lease: *const c_void);
    fn mattmc_section_counters_release(owner: *mut c_void);
    fn mattmc_dh_contributors_release(owner: *mut c_void);
    fn mattmc_chunk_snapshot_release(states: *mut c_void);
    fn mattmc_light_layer_release(layer: *mut c_void);
    fn mattmc_light_layer_view_release(view: *mut c_void);
    fn mattmc_dh_heightmaps_release(owner: *mut c_void);
    fn mattmc_dh_sources_release(owner: *mut c_void);
    fn mattmc_dh_light_field_release(field: *mut c_void);
    fn mattmc_chunk_inputs_release(owner: *mut c_void);
    fn mattmc_chunk_biome_inputs_release(owner: *mut c_void);
    fn mattmc_light_map_release(pointer: *mut c_void);
    fn mattmc_light_map_view_release(pointer: *mut c_void);
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
            SECTION_SNAPSHOT => mattmc_chunk_snapshot_release(pointer),
            LIGHT_LAYER => mattmc_light_layer_release(pointer),
            LIGHT_VIEW => mattmc_light_layer_view_release(pointer),
            DH_HEIGHTMAPS => mattmc_dh_heightmaps_release(pointer),
            DH_SOURCES => mattmc_dh_sources_release(pointer),
            DH_LIGHT_FIELD => mattmc_dh_light_field_release(pointer),
            CHUNK_INPUTS => mattmc_chunk_inputs_release(pointer),
            BIOME_CHUNK_INPUTS => mattmc_chunk_biome_inputs_release(pointer),
            LIGHT_MAP => mattmc_light_map_release(pointer),
            LIGHT_MAP_VIEW => mattmc_light_map_view_release(pointer),
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

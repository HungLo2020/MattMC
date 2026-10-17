//! Optional per-owner lifetime probe. Normal source construction never allocates it.
use super::{Source, Store};
use std::sync::{Mutex, Weak};
struct Probe(Weak<Mutex<Store>>);
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_probe_new(id: u64) -> u64 {
    let source = &*(id as usize as *const Source);
    Box::into_raw(Box::new(Probe(std::sync::Arc::downgrade(&source.0)))) as usize as u64
}
/// Actual Arc owners of this exact source; never upgrades/roots the source.
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_probe_owners(id: u64) -> u64 {
    (&*(id as usize as *const Probe)).0.strong_count() as u64
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_sky_source_probe_release(id: u64) {
    if id != 0 { drop(Box::from_raw(id as usize as *mut Probe)); }
}

//! Canonical Legacy source state and its actual default Gaussian cache.
use super::{owned_gaussian::Gaussian, random::Legacy};
use std::sync::{atomic::{AtomicU64, Ordering}, Arc};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
pub(crate) struct Source {
    seed: AtomicU64,
    pub(crate) gaussian: Gaussian,
}
impl Source {
    pub(crate) fn raw_initial() -> Arc<Self> {
        LIVE.fetch_add(1, Ordering::Relaxed); CREATED.fetch_add(1, Ordering::Relaxed);
        Arc::new(Self { seed: AtomicU64::new(0), gaussian: Gaussian::default() })
    }
    pub(crate) fn seeded(seed: i64) -> Arc<Self> {
        let value=Self::raw_initial(); value.raw_set(Legacy::new(seed).state()); value
    }
    pub(crate) fn state(&self) -> i64 { self.seed.load(Ordering::SeqCst) as i64 }
    pub(crate) fn raw_set(&self, seed: i64) { self.seed.store(Legacy::from_raw(seed).state() as u64, Ordering::SeqCst); }
    fn commit(&self, old: i64, new: i64) -> Result<(), ()> {
        self.seed.compare_exchange(old as u64, new as u64, Ordering::SeqCst, Ordering::SeqCst).map(|_| ()).map_err(|_| ())
    }
    pub(crate) fn reseed(&self, seed: i64, reset: bool) -> Result<(), ()> {
        self.commit(self.state(), Legacy::new(seed).state())?;
        if reset { self.gaussian.reset(); } Ok(())
    }
    pub(crate) fn next(&self, bits: i32) -> Result<i32, ()> {
        let old = self.state(); let mut draw = Legacy::from_raw(old); let value = draw.next(bits as u32);
        self.commit(old, draw.state())?; Ok(value)
    }
    pub(crate) fn double(&self) -> Result<f64, ()> {
        Ok(super::random::legacy_double(self.next(26)?, self.next(27)?))
    }
    pub(crate) fn bounded(&self, bound: i32) -> Result<i32, ()> {
        super::random::legacy_bounded(bound, |bits| self.next(bits))
    }

}
impl Drop for Source { fn drop(&mut self) { LIVE.fetch_sub(1, Ordering::Relaxed); RETIRED.fetch_add(1, Ordering::Relaxed); } }
pub(crate) mod ffi {
    use super::*;
    pub(crate) unsafe fn retain(id: u64) -> Arc<Source> {
        let ptr = std::ptr::with_exposed_provenance::<Source>(id as usize); Arc::increment_strong_count(ptr); Arc::from_raw(ptr)
    }
    unsafe fn source<'a>(id: u64) -> &'a Source { &*std::ptr::with_exposed_provenance::<Source>(id as usize) }
    #[no_mangle] pub extern "C" fn mattmc_random_source_legacy_new() -> u64 { Arc::into_raw(Source::raw_initial()).expose_provenance() as u64 }
    #[no_mangle] pub unsafe extern "C" fn mattmc_random_source_legacy_release(id: u64) {
        if id != 0 { drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Source>(id as usize))); }
    }
    #[no_mangle] pub unsafe extern "C" fn mattmc_random_source_legacy_seed(id: u64, seed: i64, reset: i32) -> i32 { i32::from(source(id).reseed(seed, reset != 0).is_ok()) }
    #[no_mangle] pub unsafe extern "C" fn mattmc_random_source_legacy_next(id: u64, bits: i32) -> u64 {
        match source(id).next(bits) { Ok(value) => value as u32 as u64, Err(()) => 1 << 32 }
    }
    #[no_mangle] pub unsafe extern "C" fn mattmc_random_source_legacy_raw(id: u64) -> i64 { source(id).state() }
    #[no_mangle] pub unsafe extern "C" fn mattmc_random_source_legacy_raw_set(id: u64, seed: i64) { source(id).raw_set(seed); }
    #[no_mangle] pub unsafe extern "C" fn mattmc_random_source_legacy_statistics(out: *mut u64) {
        for (i, value) in [&LIVE, &CREATED, &RETIRED].into_iter().enumerate() { out.add(i).write(value.load(Ordering::Relaxed)); }
    }
}
#[cfg(test)] mod tests {
    use super::*;
    #[test] fn actual_atomic_state_matches_existing_arithmetic_for_all_shift_counts() {
        let source = Source::raw_initial(); source.reseed(42, true).unwrap(); let mut control = Legacy::new(42);
        for bits in [i32::MIN,-65,-33,-1,0,1,31,32,33,47,48,49,63,64,65,i32::MAX] { assert_eq!(source.next(bits).unwrap(), control.next(bits as u32)); assert_eq!(source.state(), control.state()); }
    }
    #[test] fn stale_cas_rejects_without_rewriting_actual_state_or_cache() {
        let source = Source::raw_initial(); let old = source.state(); source.raw_set(7); source.gaussian.set(true, 123);
        assert_eq!(source.commit(old, 19), Err(())); assert_eq!(source.state(), 7); assert_eq!(source.gaussian.state(), (true,123));
    }
    #[test] fn raw_import_masks_without_touching_the_actual_cache() {
        let source = Source::raw_initial(); source.gaussian.set(true, (-0.0f64).to_bits()); source.raw_set(-1);
        assert_eq!(source.state(), Legacy::from_raw(-1).state()); assert_eq!(source.gaussian.state(), (true,(-0.0f64).to_bits()));
        source.reseed(19,false).unwrap(); assert!(source.gaussian.state().0); source.reseed(19,true).unwrap(); assert_eq!(source.gaussian.state(), (false,(-0.0f64).to_bits()));
    }
    #[test] fn source_retires_at_its_real_last_consumer() {
        let source = Source::raw_initial(); let weak = Arc::downgrade(&source); let consumer = source.clone(); drop(source);
        assert!(weak.upgrade().is_some()); consumer.next(32).unwrap(); drop(consumer); assert!(weak.upgrade().is_none());
    }
}

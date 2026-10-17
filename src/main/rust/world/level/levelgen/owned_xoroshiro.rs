//! Canonical Xoroshiro state shared by native producers and consumers.
use super::random::Xoroshiro;
use std::sync::{
    atomic::{AtomicI64, AtomicU64, Ordering},
    Arc,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
pub(crate) struct OwnedXoroshiro {
    lo: AtomicI64,
    hi: AtomicI64,
}
impl OwnedXoroshiro {
    pub(crate) fn new(lo: i64, hi: i64) -> Arc<Self> {
        let (lo, hi) = Xoroshiro::new(lo, hi).state();
        Self::from_raw(lo, hi)
    }
    pub(crate) fn from_raw(lo: i64, hi: i64) -> Arc<Self> {
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Arc::new(Self {
            lo: AtomicI64::new(lo),
            hi: AtomicI64::new(hi),
        })
    }
    pub(crate) fn from_seed(seed: i64) -> Arc<Self> {
        let (lo, hi) = super::random::upgrade_seed(seed);
        Self::new(lo, hi)
    }
    pub(crate) fn next_float(&self) -> f32 {
        let (lo, hi) = self.state();
        let mut value = Xoroshiro::from_raw(lo, hi);
        let result = value.next_float();
        let (lo, hi) = value.state();
        self.raw_set(lo, hi);
        result
    }
    pub(crate) fn consume(&self, count: i32) {
        for _ in 0..count {
            self.next_long();
        }
    }
    pub(crate) fn state(&self) -> (i64, i64) {
        (
            self.lo.load(Ordering::Relaxed),
            self.hi.load(Ordering::Relaxed),
        )
    }
    pub(crate) fn raw_set(&self, lo: i64, hi: i64) {
        self.lo.store(lo, Ordering::Relaxed);
        self.hi.store(hi, Ordering::Relaxed);
    }
    pub(crate) fn next_int_bound(&self, bound: i32) -> i32 {
        super::random::xoroshiro_bound(bound, || self.next_long() as i32)
    }
    pub(crate) fn next_double(&self) -> f64 {
        let (lo, hi) = self.state();
        let mut value = Xoroshiro::from_raw(lo, hi);
        let result = value.next_double();
        let (lo, hi) = value.state();
        self.raw_set(lo, hi);
        result
    }
    pub(crate) fn next_long(&self) -> i64 {
        let (lo, hi) = self.state();
        let mut value = Xoroshiro::from_raw(lo, hi);
        let result = value.next_long();
        let (lo, hi) = value.state();
        self.raw_set(lo, hi);
        result
    }
}
impl Drop for OwnedXoroshiro {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
// Separate atomic cells avoid Rust data races while preserving the original
// non-thread-safe two-word read/store shape. No atomic-pair/linearization promise.
pub(crate) mod ffi {
    use super::*;
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_new(lo: i64, hi: i64) -> u64 {
        Arc::into_raw(OwnedXoroshiro::new(lo, hi)).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_new_seeded(seed: i64) -> u64 {
        Arc::into_raw(OwnedXoroshiro::from_seed(seed)).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_bounded(id: u64, bound: i32) -> i32 {
        if bound <= 0 {
            return -1;
        }
        (&*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize)).next_int_bound(bound)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_double(id: u64) -> f64 {
        (&*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize)).next_double()
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_float(id: u64) -> f32 {
        (&*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize)).next_float()
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_consume(id: u64, count: i32) {
        (&*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize)).consume(count)
    }
    pub(crate) unsafe fn retain(id: u64) -> Arc<OwnedXoroshiro> {
        let p = std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize);
        Arc::increment_strong_count(p);
        Arc::from_raw(p)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<
                OwnedXoroshiro,
            >(id as usize)));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_next(id: u64) -> i64 {
        (&*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize)).next_long()
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_get(id: u64, slot: i32) -> i64 {
        let state = &*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize);
        if slot == 0 {
            state.lo.load(Ordering::Relaxed)
        } else {
            state.hi.load(Ordering::Relaxed)
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_set(id: u64, lo: i64, hi: i64) {
        (&*std::ptr::with_exposed_provenance::<OwnedXoroshiro>(id as usize)).raw_set(lo, hi);
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_xoroshiro_statistics(out: *mut u64) {
        for (i, field) in [&LIVE, &CREATED, &RETIRED].into_iter().enumerate() {
            out.add(i).write(field.load(Ordering::Relaxed));
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn constructor_and_raw_write_have_distinct_zero_behavior() {
        let value = OwnedXoroshiro::new(0, 0);
        assert_eq!(value.state(), (-7046029254386353131, 7640891576956012809));
        value.raw_set(0, 0);
        assert_eq!(value.next_long(), 0);
        assert_eq!(value.state(), (0, 0));
    }
    #[test]
    fn genuine_native_consumer_keeps_same_mutable_owner_after_facade_handle_release() {
        unsafe {
            let raw = ffi::mattmc_random_xoroshiro_new(1, 2);
            let consumer = ffi::retain(raw);
            let weak = Arc::downgrade(&consumer);
            assert_eq!(ffi::mattmc_random_xoroshiro_next(raw), 393217);
            assert_eq!(
                consumer.state(),
                (
                    ffi::mattmc_random_xoroshiro_get(raw, 0),
                    ffi::mattmc_random_xoroshiro_get(raw, 1)
                )
            );
            ffi::mattmc_random_xoroshiro_release(raw);
            assert!(weak.upgrade().is_some());
            consumer.raw_set(0, 0);
            assert_eq!(consumer.next_long(), 0);
            drop(consumer);
            assert!(weak.upgrade().is_none());
        }
    }
    #[test]
    fn all_raw_word_values_roundtrip_without_constructor_normalization() {
        let state = OwnedXoroshiro::new(1, 2);
        for pair in [(0, 0), (i64::MIN, i64::MAX), (-1, 0), (0, -1)] {
            state.raw_set(pair.0, pair.1);
            assert_eq!(state.state(), pair);
        }
    }
}

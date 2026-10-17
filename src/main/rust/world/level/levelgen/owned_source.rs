//! Actual source/current-generator custody, sharing the existing RNG arithmetic.
use super::{
    owned_xoroshiro::OwnedXoroshiro,
    random::{upgrade_seed, Xoroshiro},
};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc, Mutex,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
pub(crate) enum Data {
    Inline(Xoroshiro),
    Shared(Arc<OwnedXoroshiro>),
}
impl Data {
    pub(crate) fn next_long(&mut self) -> i64 {
        match self {
            Self::Inline(value) => value.next_long(),
            Self::Shared(value) => value.next_long(),
        }
    }
    pub(crate) fn bounded(&mut self, bound: i32) -> i32 {
        match self {
            Self::Inline(value) => value.next_int_bound(bound),
            Self::Shared(value) => value.next_int_bound(bound),
        }
    }
    pub(crate) fn double(&mut self) -> f64 {
        match self {
            Self::Inline(value) => value.next_double(),
            Self::Shared(value) => value.next_double(),
        }
    }
    pub(crate) fn float(&mut self) -> f32 {
        match self {
            Self::Inline(value) => value.next_float(),
            Self::Shared(value) => value.next_float(),
        }
    }
    pub(crate) fn consume(&mut self, count: i32) {
        for _ in 0..count {
            self.next_long();
        }
    }
}
pub(crate) struct Source {
    data: Mutex<Data>,
}
impl Source {
    fn new(data: Data) -> Arc<Self> {
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Arc::new(Self {
            data: Mutex::new(data),
        })
    }
    pub(crate) fn seeded(seed: i64) -> Arc<Self> {
        let (lo, hi) = upgrade_seed(seed);
        Self::new(Data::Inline(Xoroshiro::new(lo, hi)))
    }
    pub(crate) fn with<T>(&self, effect: impl FnOnce(&mut Data) -> T) -> T {
        effect(&mut self.data.lock().unwrap())
    }
    pub(crate) fn reseed(&self, seed: i64) {
        let (lo, hi) = upgrade_seed(seed);
        *self.data.lock().unwrap() = Data::Inline(Xoroshiro::new(lo, hi));
    }
    fn bind(&self, root: Arc<OwnedXoroshiro>) {
        *self.data.lock().unwrap() = Data::Shared(root);
    }
    pub(crate) fn project(&self) -> Arc<OwnedXoroshiro> {
        let mut data = self.data.lock().unwrap();
        match &*data {
            Data::Shared(root) => root.clone(),
            Data::Inline(value) => {
                let (lo, hi) = value.state();
                let root = OwnedXoroshiro::from_raw(lo, hi);
                *data = Data::Shared(root.clone());
                root
            }
        }
    }
}
impl Drop for Source {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}
// These locks span only native arithmetic/leaf construction, never VM callbacks.
pub(crate) mod ffi {
    use super::*;
    pub(crate) unsafe fn retain(id: u64) -> Arc<Source> {
        let ptr = std::ptr::with_exposed_provenance::<Source>(id as usize);
        Arc::increment_strong_count(ptr);
        Arc::from_raw(ptr)
    }
    unsafe fn source<'a>(id: u64) -> &'a Source {
        &*std::ptr::with_exposed_provenance::<Source>(id as usize)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_new(a: i64, b: i64, kind: i32) -> u64 {
        let data = match kind {
            0 => Data::Inline(Xoroshiro::new(a, b)),
            1 => {
                let (lo, hi) = upgrade_seed(a);
                Data::Inline(Xoroshiro::new(lo, hi))
            }
            2 => Data::Shared(super::super::owned_xoroshiro::ffi::retain(a as u64)),
            _ => return 0,
        };
        Arc::into_raw(Source::new(data)).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(std::ptr::with_exposed_provenance::<Source>(
                id as usize,
            )));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_root(id: u64) -> u64 {
        Arc::into_raw(source(id).project()).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_bind(id: u64, root: u64) {
        source(id).bind(super::super::owned_xoroshiro::ffi::retain(root));
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_seed(id: u64, seed: i64) {
        source(id).reseed(seed);
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_next(id: u64) -> i64 {
        source(id).with(|d| d.next_long())
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_bounded(id: u64, bound: i32) -> i32 {
        if bound <= 0 {
            return -1;
        }
        source(id).with(|d| d.bounded(bound))
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_double(id: u64) -> f64 {
        source(id).with(|d| d.double())
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_float(id: u64) -> f32 {
        source(id).with(|d| d.float())
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_consume(id: u64, count: i32) {
        source(id).with(|d| d.consume(count));
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_random_source_xoroshiro_statistics(out: *mut u64) {
        for (i, value) in [&LIVE, &CREATED, &RETIRED].into_iter().enumerate() {
            out.add(i).write(value.load(Ordering::Relaxed));
        }
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn real_source_consumer_survives_caller_release() {
        let source = Source::seeded(42);
        let weak = Arc::downgrade(&source);
        let consumer = source.clone();
        drop(source);
        assert!(weak.upgrade().is_some());
        consumer.with(|d| {
            d.consume(17);
            d.next_long()
        });
        drop(consumer);
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn escaped_generator_keeps_its_actual_old_state_after_reseed() {
        let source = Source::seeded(42);
        let alias = source.project();
        source.with(|d| d.next_long());
        let old = alias.state();
        source.reseed(19);
        assert_eq!(alias.state(), old);
        let next = source.project();
        assert!(!Arc::ptr_eq(&alias, &next));
        let expected = super::super::random::upgrade_seed(19);
        assert_eq!(next.state(), expected);
    }
    #[test]
    fn reseed_retires_unreferenced_projected_root_while_source_stays_live() {
        let source = Source::seeded(42);
        let root = source.project();
        let weak = Arc::downgrade(&root);
        drop(root);
        assert!(weak.upgrade().is_some());
        source.reseed(19);
        assert!(weak.upgrade().is_none());
        source.with(|d| assert!(matches!(d, Data::Inline(_))));
    }
    #[test]
    fn raw_zero_projection_does_not_normalize() {
        let source = Source::new(Data::Inline(Xoroshiro::from_raw(0, 0)));
        let root = source.project();
        assert_eq!(root.state(), (0, 0));
        assert_eq!(source.with(|d| d.next_long()), 0);
    }
}

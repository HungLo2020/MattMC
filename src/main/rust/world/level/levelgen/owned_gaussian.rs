//! Canonical Gaussian cache and draw/rejection/result policy.
//! VM effects run between native steps; no borrow spans a VM callback.
use crate::world::level::levelgen::owned_source::Source;
use std::sync::{
    atomic::{AtomicBool, AtomicU64, Ordering},
    Arc,
};
static LIVE: AtomicU64 = AtomicU64::new(0);
static CREATED: AtomicU64 = AtomicU64::new(0);
static RETIRED: AtomicU64 = AtomicU64::new(0);
static FRAMES: AtomicU64 = AtomicU64::new(0);
static FRAMES_CREATED: AtomicU64 = AtomicU64::new(0);
static FRAMES_RETIRED: AtomicU64 = AtomicU64::new(0);

#[cfg(test)]
mod tests {
    use super::*;
    fn input(draw: &mut Draw, value: f64) -> Effect {
        draw.advance(value.to_bits())
    }
    fn pair(draw: &mut Draw) -> f64 {
        assert_eq!(draw.advance(0), Effect::DrawDouble);
        assert_eq!(input(draw, 0.75), Effect::DrawDouble);
        let Effect::JavaLog(value) = input(draw, 0.5) else {
            panic!("missing actual log effect")
        };
        assert_eq!(value, 0.25);
        value
    }
    fn complete(draw: &mut Draw) -> u64 {
        let Effect::Complete(value) = draw.advance(0xbff6_2e42_fefa_39ef) else {
            panic!("missing result")
        };
        value
    }
    #[test]
    fn raw_cache_value_survives_pop_and_reset() {
        let owner = Gaussian::new();
        owner.set(true, 0x7ff8_0000_0000_0042);
        assert_eq!(
            Draw::new(owner.clone(), None).advance(0),
            Effect::Complete(0x7ff8_0000_0000_0042)
        );
        assert_eq!(owner.state(), (false, 0x7ff8_0000_0000_0042));
        owner.reset();
        assert_eq!(owner.state(), (false, 0x7ff8_0000_0000_0042));
        owner.set(false, (-0.0f64).to_bits());
        owner.reset();
        assert_eq!(owner.state(), (false, (-0.0f64).to_bits()));
    }
    #[test]
    fn genuine_rejection_prefix_yields_each_original_draw_then_math() {
        let owner = Gaussian::new();
        let mut draw = Draw::new(owner.clone(), None);
        assert_eq!(draw.advance(0), Effect::DrawDouble);
        for value in [0.5, 0.5, 1., 0.5, 0.75] {
            assert_eq!(input(&mut draw, value), Effect::DrawDouble);
        }
        assert_eq!(input(&mut draw, 0.5), Effect::JavaLog(0.25));
        let actual = complete(&mut draw);
        assert_eq!(
            actual,
            (0.5 * (-2. * f64::from_bits(0xbff6_2e42_fefa_39ef) / 0.25).sqrt()).to_bits()
        );
        assert_eq!(owner.state(), (true, 0.0f64.to_bits()));
    }
    #[test]
    fn pending_draw_owns_actual_cache_after_facade_owner_release() {
        let owner = Gaussian::new();
        let weak = Arc::downgrade(&owner);
        let mut draw = Draw::new(owner, None);
        assert!(weak.upgrade().is_some());
        pair(&mut draw);
        complete(&mut draw);
        assert!(weak.upgrade().is_some());
        drop(draw);
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn actual_default_source_retires_after_last_draw_before_math_effect() {
        let source = Source::seeded(42);
        let weak = Arc::downgrade(&source);
        let mut draw = Draw::new(Gaussian::new(), Some(source));
        assert!(weak.upgrade().is_some());
        assert!(matches!(draw.advance(0), Effect::JavaLog(_)));
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn failed_outer_effect_keeps_real_nested_cache_changes() {
        let owner = Gaussian::new();
        let mut outer = Draw::new(owner.clone(), None);
        assert_eq!(outer.advance(0), Effect::DrawDouble);
        let mut inner = Draw::new(owner.clone(), None);
        pair(&mut inner);
        complete(&mut inner);
        let state = owner.state();
        assert!(state.0);
        drop(outer);
        assert_eq!(owner.state(), state);
    }
    #[test]
    fn completed_outer_overwrites_nested_cache_in_original_order() {
        let owner = Gaussian::new();
        let mut outer = Draw::new(owner.clone(), None);
        assert_eq!(outer.advance(0), Effect::DrawDouble);
        let mut inner = Draw::new(owner.clone(), None);
        pair(&mut inner);
        complete(&mut inner);
        owner.set(true, 123);
        assert_eq!(input(&mut outer, 0.75), Effect::DrawDouble);
        assert_eq!(input(&mut outer, 0.5), Effect::JavaLog(0.25));
        complete(&mut outer);
        assert_eq!(owner.state(), (true, 0.0f64.to_bits()));
    }
    #[test]
    fn reset_during_real_pending_draw_keeps_later_successful_cache_commit() {
        let owner = Gaussian::new();
        let mut draw = Draw::new(owner.clone(), None);
        pair(&mut draw);
        owner.reset();
        complete(&mut draw);
        assert_eq!(owner.state(), (true, 0.0f64.to_bits()));
    }
    #[test]
    fn cached_result_does_not_consume_or_retain_an_unused_source() {
        let owner = Gaussian::new();
        owner.set(true, 19);
        let source = Source::seeded(42);
        let weak = Arc::downgrade(&source);
        let mut draw = Draw::new(owner.clone(), Some(source));
        assert_eq!(draw.advance(0), Effect::Complete(19));
        assert!(weak.upgrade().is_none());
        owner.set(true, 23);
        assert_eq!(draw.advance(0), Effect::Complete(19));
        assert_eq!(owner.state(), (true, 23));
    }
    #[test]
    fn start_returns_cached_bits_without_creating_pending_work() {
        let owner = Gaussian::new();
        owner.set(true, 0x7ff8_0000_0000_0042);
        let source = Source::seeded(42);
        let weak = Arc::downgrade(&source);
        let Start::Cached(bits) = owner.clone().start(Some(source)) else {
            panic!("unnecessary pending cache frame")
        };
        assert_eq!(bits, 0x7ff8_0000_0000_0042);
        assert_eq!(owner.state(), (false, bits));
        assert!(weak.upgrade().is_none());
    }
    #[test]
    fn a_started_miss_does_not_take_cache_again_after_reentry() {
        let owner = Gaussian::new();
        let Start::Pending(mut draw) = owner.clone().start(None) else {
            panic!("missing actual pending work")
        };
        owner.set(true, 123);
        assert_eq!(draw.advance(0), Effect::DrawDouble);
        assert_eq!(owner.state(), (true, 123));
        drop(draw);
        assert_eq!(owner.state(), (true, 123));
    }
}

pub(crate) struct Gaussian {
    has: AtomicBool,
    bits: AtomicU64,
}
impl Default for Gaussian {
    fn default() -> Self {
        LIVE.fetch_add(1, Ordering::Relaxed);
        CREATED.fetch_add(1, Ordering::Relaxed);
        Self {
            has: AtomicBool::new(false),
            bits: AtomicU64::new(0),
        }
    }
}
impl Drop for Gaussian {
    fn drop(&mut self) {
        LIVE.fetch_sub(1, Ordering::Relaxed);
        RETIRED.fetch_add(1, Ordering::Relaxed);
    }
}

impl Gaussian {
    pub(crate) fn new() -> Arc<Self> {
        Arc::new(Self::default())
    }
    pub(crate) fn state(&self) -> (bool, u64) {
        (
            self.has.load(Ordering::Relaxed),
            self.bits.load(Ordering::Relaxed),
        )
    }
    pub(crate) fn set(&self, has: bool, bits: u64) {
        self.bits.store(bits, Ordering::Relaxed);
        self.has.store(has, Ordering::Relaxed);
    }
    pub(crate) fn reset(&self) {
        self.has.store(false, Ordering::Relaxed);
    }
    // Original non-thread-safe flag/value ordering, with data-race-free native
    // cells. No atomic pair or linearized concurrent Gaussian stream is promised.
    fn take(&self) -> Option<u64> {
        if self.has.load(Ordering::Relaxed) {
            self.has.store(false, Ordering::Relaxed);
            Some(self.bits.load(Ordering::Relaxed))
        } else {
            None
        }
    }
    pub(crate) fn start(self: Arc<Self>, source: Option<Arc<Source>>) -> Start {
        Owner::Detached(self).start(source)
    }
}

enum Owner {
    Detached(Arc<Gaussian>),
    Source(Arc<Source>),
    Legacy(Arc<super::owned_legacy::Source>),
}
impl Owner {
    fn cache(&self) -> &Gaussian {
        match self {
            Self::Detached(owner) => owner,
            Self::Source(owner) => &owner.gaussian,
            Self::Legacy(owner) => &owner.gaussian,
        }
    }
    fn take(&self) -> Option<u64> {
        self.cache().take()
    }
    fn set(&self, has: bool, bits: u64) {
        self.cache().set(has, bits);
    }
    fn start(self, source: Option<Arc<Source>>) -> Start {
        match self.take() {
            Some(bits) => Start::Cached(bits),
            None => {
                let mut draw = Draw::with_owner(self, source);
                draw.stage = Stage::UncachedStart;
                Start::Pending(draw)
            }
        }
    }
}

// The actual Rust caller can return Cached directly, without a pending frame.
// A future FFM adapter must also avoid Box/Arena allocation on this path.
pub(crate) enum Start {
    Cached(u64),
    Pending(Draw),
}

// Opaque owner/frame exports for the thin VM effect caller.
pub(crate) mod ffi {
    use super::*;
    // Bounded diagnostic capabilities observe the actual Arc owners without
    // retaining their data. No registry or VM identity is stored here.
    enum Watch {
        Detached(std::sync::Weak<Gaussian>),
        Source(std::sync::Weak<Source>),
        Legacy(std::sync::Weak<super::super::owned_legacy::Source>),
    }
    unsafe fn owner<'a>(id: u64, kind: i32) -> Option<&'a Gaussian> {
        if id == 0 {
            return None;
        }
        match kind {
            0 => Some(&*std::ptr::with_exposed_provenance::<Gaussian>(id as usize)),
            1 => Some(&(*std::ptr::with_exposed_provenance::<Source>(id as usize)).gaussian),
            2 => Some(&(*std::ptr::with_exposed_provenance::<super::super::owned_legacy::Source>(id as usize)).gaussian),
            _ => None,
        }
    }
    unsafe fn retain(id: u64, kind: i32) -> Option<Owner> {
        if id == 0 {
            return None;
        }
        match kind {
            0 => {
                let ptr = std::ptr::with_exposed_provenance::<Gaussian>(id as usize);
                Arc::increment_strong_count(ptr);
                Some(Owner::Detached(Arc::from_raw(ptr)))
            }
            1 => Some(Owner::Source(
                crate::world::level::levelgen::owned_source::ffi::retain(id),
            )),
            2 => Some(Owner::Legacy(super::super::owned_legacy::ffi::retain(id))),
            _ => None,
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_weak_new(id: u64, kind: i32) -> u64 {
        let Some(owner) = retain(id, kind) else {
            return 0;
        };
        let watch = match &owner {
            Owner::Detached(owner) => Watch::Detached(Arc::downgrade(owner)),
            Owner::Source(owner) => Watch::Source(Arc::downgrade(owner)),
            Owner::Legacy(owner) => Watch::Legacy(Arc::downgrade(owner)),
        };
        Box::into_raw(Box::new(watch)).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_weak_count(id: u64) -> u64 {
        match &*std::ptr::with_exposed_provenance::<Watch>(id as usize) {
            Watch::Detached(owner) => owner.strong_count() as u64,
            Watch::Source(owner) => owner.strong_count() as u64,
            Watch::Legacy(owner) => owner.strong_count() as u64,
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_weak_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(
                std::ptr::with_exposed_provenance_mut::<Watch>(id as usize),
            ));
        }
    }
    #[repr(C)]
    struct Pending {
        words: [u64; 2],
        draw: Draw,
    }
    impl Pending {
        fn new(draw: Draw) -> Self {
            FRAMES.fetch_add(1, Ordering::Relaxed);
            FRAMES_CREATED.fetch_add(1, Ordering::Relaxed);
            Self {
                words: [0; 2],
                draw,
            }
        }
    }
    impl Drop for Pending {
        fn drop(&mut self) {
            FRAMES.fetch_sub(1, Ordering::Relaxed);
            FRAMES_RETIRED.fetch_add(1, Ordering::Relaxed);
        }
    }
    #[no_mangle]
    pub extern "C" fn mattmc_gaussian_new() -> u64 {
        Arc::into_raw(Gaussian::new()).expose_provenance() as u64
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_release(id: u64) {
        if id != 0 {
            drop(Arc::from_raw(
                std::ptr::with_exposed_provenance::<Gaussian>(id as usize),
            ));
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_has(id: u64, kind: i32) -> i32 {
        owner(id, kind).map_or(0, |owner| i32::from(owner.state().0))
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_value(id: u64, kind: i32) -> u64 {
        owner(id, kind).map_or(0, |owner| owner.state().1)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_reset(id: u64, kind: i32) {
        if let Some(owner) = owner(id, kind) {
            owner.reset();
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_set(id: u64, kind: i32, has: i32, bits: u64) {
        if let Some(owner) = owner(id, kind) {
            owner.set(has != 0, bits);
        }
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_draw_new(id: u64, kind: i32, source: u64) -> u64 {
        mattmc_gaussian_draw_new_source(id, kind, source, 1)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_draw_new_source(id: u64, kind: i32, source: u64, source_kind: i32) -> u64 {
        let Some(cache) = owner(id, kind) else { return 0; };
        if cache.take().is_some() { return 0; }
        let Some(owner) = retain(id, kind) else { return 0; };
        let source = if source == 0 { None } else {
            Some(match source_kind {
                1 => Producer::Xoroshiro(super::super::owned_source::ffi::retain(source)),
                2 => Producer::Legacy(super::super::owned_legacy::ffi::retain(source)),
                _ => return 0,
            })
        };
        let mut draw = Draw::with_producer(owner, source);
        draw.stage = Stage::UncachedStart;
        Box::into_raw(Box::new(Pending::new(draw))).expose_provenance() as u64
    }

    /// Called only after the real cache miss. Target owns actual backing data;
    /// a missing VM count owner is requested before any bit is consumed.
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_draw_bind_worldgen(id:u64,count:u64,kind:i32,source:u64)->i32 {
        if id==0 {return 0;}
        let frame=&mut*std::ptr::with_exposed_provenance_mut::<Pending>(id as usize);
        if !matches!(frame.draw.stage,Stage::UncachedStart)||frame.draw.source.is_some(){return 0;}
        let Some(target)=super::super::owned_worldgen::ffi::target(kind,source)else{return 0;};
        frame.draw.source=Some(if count==0 {Producer::PendingWorldgen(target)}else{
            Producer::Worldgen(super::super::owned_worldgen::Kernel::owned(super::super::owned_worldgen::ffi::retain(count),target))
        });
        1
    }

    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_draw_next(id: u64, response: u64) -> i32 {
        let frame = &mut *std::ptr::with_exposed_provenance_mut::<Pending>(id as usize);
        let (op, value) = match frame.draw.advance(response) {
            Effect::DrawDouble => (1, 0),
            Effect::LegacyCollision => (3, 0),
            Effect::WorldgenCount => (4, 0),
            Effect::JavaLog(value) => (2, value.to_bits()),
            Effect::Complete(bits) => (0, bits),
        };
        frame.words = [op, value];
        i32::from(op != 0)
    }
    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_draw_release(id: u64) {
        if id != 0 {
            drop(Box::from_raw(std::ptr::with_exposed_provenance_mut::<
                Pending,
            >(id as usize)));
        }
    }

    #[no_mangle]
    pub unsafe extern "C" fn mattmc_gaussian_statistics(out: *mut u64) {
        for (i, value) in [
            &LIVE,
            &CREATED,
            &RETIRED,
            &FRAMES,
            &FRAMES_CREATED,
            &FRAMES_RETIRED,
        ]
        .into_iter()
        .enumerate()
        {
            out.add(i).write(value.load(Ordering::Relaxed));
        }
    }
    #[cfg(test)]
    mod tests {
        use super::*;
        #[test]
        fn actual_worldgen_kernel_retires_count_and_backing_before_math_but_keeps_cache() {
            unsafe {
                use super::super::super::{owned_worldgen::ffi as wg,owned_legacy::ffi as legacy};
                let source=legacy::mattmc_random_source_legacy_new();legacy::mattmc_random_source_legacy_seed(source,42,1);
                let source_weak=Arc::downgrade(&legacy::retain(source));let count=wg::mattmc_worldgen_new();let cw=wg::mattmc_worldgen_weak_new(count);
                let cache=mattmc_gaussian_new();let watch=mattmc_gaussian_weak_new(cache,0);let frame=mattmc_gaussian_draw_new_source(cache,0,0,1);
                assert_eq!(mattmc_gaussian_draw_bind_worldgen(frame,count,1,source),1);
                wg::mattmc_worldgen_release(count);legacy::mattmc_random_source_legacy_release(source);mattmc_gaussian_release(cache);
                assert_eq!(wg::mattmc_worldgen_weak_count(cw),1);assert_eq!(source_weak.strong_count(),1);assert_eq!(mattmc_gaussian_weak_count(watch),1);
                assert_eq!(mattmc_gaussian_draw_next(frame,0),1);let words=std::ptr::with_exposed_provenance::<[u64;2]>(frame as usize).read();assert_eq!(words[0],2);
                assert_eq!(wg::mattmc_worldgen_weak_count(cw),0);assert_eq!(source_weak.strong_count(),0);assert_eq!(mattmc_gaussian_weak_count(watch),1);
                assert_eq!(mattmc_gaussian_draw_next(frame,f64::from_bits(words[1]).ln().to_bits()),0);assert_eq!(mattmc_gaussian_has(cache,0),1);
                mattmc_gaussian_draw_release(frame);assert_eq!(mattmc_gaussian_weak_count(watch),0);mattmc_gaussian_weak_release(watch);wg::mattmc_worldgen_weak_release(cw);
            }
        }
        #[test]
        fn actual_worldgen_first_draw_requests_count_before_consuming_any_backing_bits() {
            unsafe {
                use super::super::super::{owned_worldgen::ffi as wg,owned_legacy::ffi as legacy};
                let source=legacy::mattmc_random_source_legacy_new();legacy::mattmc_random_source_legacy_seed(source,42,1);let sw=Arc::downgrade(&legacy::retain(source));
                let cache=mattmc_gaussian_new();let frame=mattmc_gaussian_draw_new_source(cache,0,0,1);assert_eq!(mattmc_gaussian_draw_bind_worldgen(frame,0,1,source),1);
                legacy::mattmc_random_source_legacy_release(source);assert_eq!(sw.strong_count(),1);assert_eq!(mattmc_gaussian_draw_next(frame,0),1);assert_eq!(std::ptr::with_exposed_provenance::<[u64;2]>(frame as usize).read(),[4,0]);assert_eq!(sw.strong_count(),1);
                let count=wg::mattmc_worldgen_new();let cw=wg::mattmc_worldgen_weak_new(count);assert_eq!(mattmc_gaussian_draw_next(frame,count),1);let words=std::ptr::with_exposed_provenance::<[u64;2]>(frame as usize).read();assert_eq!(words[0],2);
                assert_eq!(wg::mattmc_worldgen_count(count),4);assert_eq!(wg::mattmc_worldgen_weak_count(cw),1);assert_eq!(sw.strong_count(),0);wg::mattmc_worldgen_release(count);assert_eq!(wg::mattmc_worldgen_weak_count(cw),0);
                mattmc_gaussian_draw_next(frame,f64::from_bits(words[1]).ln().to_bits());mattmc_gaussian_draw_release(frame);mattmc_gaussian_release(cache);wg::mattmc_worldgen_weak_release(cw);
            }
        }
        #[test]
        fn abandoned_actual_worldgen_count_request_retires_target_without_a_draw() {
            unsafe {
                use super::super::super::owned_legacy::ffi as legacy;
                let source=legacy::mattmc_random_source_legacy_new();let sw=Arc::downgrade(&legacy::retain(source));let cache=mattmc_gaussian_new();let watch=mattmc_gaussian_weak_new(cache,0);
                let frame=mattmc_gaussian_draw_new_source(cache,0,0,1);assert_eq!(mattmc_gaussian_draw_bind_worldgen(frame,0,1,source),1);legacy::mattmc_random_source_legacy_release(source);mattmc_gaussian_release(cache);
                assert_eq!(mattmc_gaussian_draw_next(frame,0),1);assert_eq!(std::ptr::with_exposed_provenance::<[u64;2]>(frame as usize).read(),[4,0]);assert_eq!(sw.strong_count(),1);
                mattmc_gaussian_draw_release(frame);assert_eq!(sw.strong_count(),0);assert_eq!(mattmc_gaussian_weak_count(watch),0);mattmc_gaussian_weak_release(watch);
            }
        }
        #[test]
        fn aliased_actual_gaussian_cache_keeps_backing_until_commit_after_worldgen_count_retires() {
            unsafe {
                use super::super::super::{owned_worldgen::ffi as wg,owned_legacy::ffi as legacy};
                let source=legacy::mattmc_random_source_legacy_new();legacy::mattmc_random_source_legacy_seed(source,42,1);let sw=Arc::downgrade(&legacy::retain(source));let count=wg::mattmc_worldgen_new();let cw=wg::mattmc_worldgen_weak_new(count);
                let frame=mattmc_gaussian_draw_new_source(source,2,0,1);assert_eq!(mattmc_gaussian_draw_bind_worldgen(frame,count,1,source),1);wg::mattmc_worldgen_release(count);legacy::mattmc_random_source_legacy_release(source);assert_eq!(sw.strong_count(),2);
                assert_eq!(mattmc_gaussian_draw_next(frame,0),1);let words=std::ptr::with_exposed_provenance::<[u64;2]>(frame as usize).read();assert_eq!(words[0],2);assert_eq!(wg::mattmc_worldgen_weak_count(cw),0);assert_eq!(sw.strong_count(),1);
                assert_eq!(mattmc_gaussian_draw_next(frame,f64::from_bits(words[1]).ln().to_bits()),0);assert_eq!(mattmc_gaussian_has(source,2),1);mattmc_gaussian_draw_release(frame);assert_eq!(sw.strong_count(),0);wg::mattmc_worldgen_weak_release(cw);
            }
        }
        #[test]
        fn actual_legacy_cache_and_producer_keep_data_until_the_final_commit() {
            unsafe {
                use super::super::super::owned_legacy::ffi as legacy;
                let id = legacy::mattmc_random_source_legacy_new();
                assert_eq!(legacy::mattmc_random_source_legacy_seed(id,42,1),1);
                let watch = mattmc_gaussian_weak_new(id,2);
                let frame = mattmc_gaussian_draw_new_source(id,2,id,2);
                legacy::mattmc_random_source_legacy_release(id);
                assert!(mattmc_gaussian_weak_count(watch)>0);
                assert_eq!(mattmc_gaussian_draw_next(frame,0),1);
                let words=std::ptr::with_exposed_provenance::<[u64;2]>(frame as usize).read();
                assert_eq!(words[0],2);
                assert_eq!(mattmc_gaussian_weak_count(watch),1);
                assert_eq!(mattmc_gaussian_draw_next(frame,f64::from_bits(words[1]).ln().to_bits()),0);
                assert_eq!(mattmc_gaussian_has(id,2),1);
                mattmc_gaussian_draw_release(frame);
                assert_eq!(mattmc_gaussian_weak_count(watch),0);
                mattmc_gaussian_weak_release(watch);
            }
        }
        #[test]
        fn cached_ffi_path_preserves_all_bits_without_a_pending_frame() {
            unsafe {
                let id = mattmc_gaussian_new();
                mattmc_gaussian_set(id, 0, 1, 0x7ff8_0000_0000_0042);
                assert_eq!(mattmc_gaussian_draw_new(id, 0, 0), 0);
                assert_eq!(mattmc_gaussian_has(id, 0), 0);
                assert_eq!(mattmc_gaussian_value(id, 0), 0x7ff8_0000_0000_0042);
                mattmc_gaussian_release(id);
            }
        }
        #[test]
        fn actual_pending_ffi_release_keeps_nested_vm_cache_effects() {
            unsafe {
                let id = mattmc_gaussian_new();
                let frame = mattmc_gaussian_draw_new(id, 0, 0);
                assert_ne!(frame, 0);
                assert_eq!(mattmc_gaussian_draw_next(frame, 0), 1);
                let words = std::ptr::with_exposed_provenance::<[u64; 2]>(frame as usize).read();
                assert_eq!(words, [1, 0]);
                mattmc_gaussian_set(id, 0, 1, 123);
                mattmc_gaussian_draw_release(frame);
                assert_eq!(mattmc_gaussian_has(id, 0), 1);
                assert_eq!(mattmc_gaussian_value(id, 0), 123);
                mattmc_gaussian_release(id);
            }
        }
        #[test]
        fn actual_default_ffi_frame_releases_source_before_its_math_effect() {
            unsafe {
                use crate::world::level::levelgen::owned_source::ffi as source;
                let id = mattmc_gaussian_new();
                let source_id = source::mattmc_random_source_xoroshiro_new(42, 0, 1);
                let alias = source::retain(source_id);
                let weak = Arc::downgrade(&alias);
                drop(alias);
                let frame = mattmc_gaussian_draw_new(id, 0, source_id);
                source::mattmc_random_source_xoroshiro_release(source_id);
                assert!(weak.upgrade().is_some());
                assert_eq!(mattmc_gaussian_draw_next(frame, 0), 1);
                let words = std::ptr::with_exposed_provenance::<[u64; 2]>(frame as usize).read();
                assert_eq!(words[0], 2);
                assert!(weak.upgrade().is_none());
                let log_input = f64::from_bits(words[1]);
                assert!(log_input > 0. && log_input < 1.);
                assert_eq!(
                    mattmc_gaussian_draw_next(frame, log_input.ln().to_bits()),
                    0
                );
                assert_eq!(mattmc_gaussian_has(id, 0), 1);
                mattmc_gaussian_draw_release(frame);
                mattmc_gaussian_release(id);
            }
        }
        #[test]
        fn source_embedded_cache_is_the_actual_pending_consumers_data() {
            unsafe {
                use crate::world::level::levelgen::owned_source::ffi as source;
                let source_id = source::mattmc_random_source_xoroshiro_new(42, 0, 1);
                let alias = source::retain(source_id);
                let weak = Arc::downgrade(&alias);
                drop(alias);
                let frame = mattmc_gaussian_draw_new(source_id, 1, source_id);
                source::mattmc_random_source_xoroshiro_release(source_id);
                assert_eq!(mattmc_gaussian_draw_next(frame, 0), 1);
                let words = std::ptr::with_exposed_provenance::<[u64; 2]>(frame as usize).read();
                assert_eq!(words[0], 2);
                assert!(
                    weak.upgrade().is_some(),
                    "pending result still owns its actual cache"
                );
                assert_eq!(
                    mattmc_gaussian_draw_next(frame, f64::from_bits(words[1]).ln().to_bits()),
                    0
                );
                assert_eq!(mattmc_gaussian_has(source_id, 1), 1);
                mattmc_gaussian_draw_release(frame);
                assert!(weak.upgrade().is_none());
            }
        }
        #[test]
        fn reseeding_resets_only_the_actual_default_cache_and_keeps_value_bits() {
            unsafe {
                use crate::world::level::levelgen::owned_source::ffi as source;
                let source_id = source::mattmc_random_source_xoroshiro_new(42, 0, 1);
                let detached = mattmc_gaussian_new();
                mattmc_gaussian_set(source_id, 1, 1, (-0.0f64).to_bits());
                mattmc_gaussian_set(detached, 0, 1, 123);
                source::mattmc_random_source_xoroshiro_seed(source_id, 19);
                assert_eq!(mattmc_gaussian_has(source_id, 1), 0);
                assert_eq!(mattmc_gaussian_value(source_id, 1), (-0.0f64).to_bits());
                assert_eq!(mattmc_gaussian_has(detached, 0), 1);
                assert_eq!(mattmc_gaussian_value(detached, 0), 123);
                mattmc_gaussian_release(detached);
                source::mattmc_random_source_xoroshiro_release(source_id);
            }
        }
        #[test]
        fn replaced_vm_default_commits_seed_without_resetting_escaped_cache() {
            unsafe {
                use crate::world::level::levelgen::owned_source::ffi as source;
                let id = source::mattmc_random_source_xoroshiro_new(42, 0, 1);
                mattmc_gaussian_set(id, 1, 1, 123);
                source::mattmc_random_source_xoroshiro_seed_current(id, 19, 0);
                assert_eq!(mattmc_gaussian_has(id, 1), 1);
                assert_eq!(mattmc_gaussian_value(id, 1), 123);
                let control = Source::seeded(19);
                assert_eq!(
                    source::mattmc_random_source_xoroshiro_next(id),
                    control.with(|data| data.next_long())
                );
                source::mattmc_random_source_xoroshiro_release(id);
            }
        }
        #[test]
        fn detached_watch_does_not_keep_data_after_its_real_last_consumer() {
            unsafe {
                let id = mattmc_gaussian_new();
                let watch = mattmc_gaussian_weak_new(id, 0);
                let frame = mattmc_gaussian_draw_new(id, 0, 0);
                assert_eq!(mattmc_gaussian_weak_count(watch), 2);
                mattmc_gaussian_release(id);
                assert_eq!(mattmc_gaussian_weak_count(watch), 1);
                mattmc_gaussian_draw_release(frame);
                assert_eq!(mattmc_gaussian_weak_count(watch), 0);
                mattmc_gaussian_weak_release(watch);
            }
        }
        #[test]
        fn source_watch_follows_actual_draw_then_cache_commit_last_uses() {
            unsafe {
                use crate::world::level::levelgen::owned_source::ffi as source;
                let id = source::mattmc_random_source_xoroshiro_new(42, 0, 1);
                let watch = mattmc_gaussian_weak_new(id, 1);
                let frame = mattmc_gaussian_draw_new(id, 1, id);
                let before = mattmc_gaussian_weak_count(watch);
                source::mattmc_random_source_xoroshiro_release(id);
                assert_eq!(mattmc_gaussian_weak_count(watch), before - 1);
                assert_eq!(mattmc_gaussian_draw_next(frame, 0), 1);
                assert_eq!(mattmc_gaussian_weak_count(watch), 1);
                let words = std::ptr::with_exposed_provenance::<[u64; 2]>(frame as usize).read();
                assert_eq!(
                    mattmc_gaussian_draw_next(frame, f64::from_bits(words[1]).ln().to_bits()),
                    0
                );
                assert_eq!(mattmc_gaussian_weak_count(watch), 1);
                mattmc_gaussian_draw_release(frame);
                assert_eq!(mattmc_gaussian_weak_count(watch), 0);
                mattmc_gaussian_weak_release(watch);
            }
        }
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Effect {
    DrawDouble,
    LegacyCollision,
    WorldgenCount,
    JavaLog(f64),
    Complete(u64),
}

enum Stage {
    Start,
    UncachedStart,
    BindWorldgen,
    First,
    Second,
    Log,
    Done(u64),
}

enum Producer {
    Xoroshiro(Arc<Source>),
    Legacy(Arc<super::owned_legacy::Source>),
    Worldgen(super::owned_worldgen::Kernel),
    PendingWorldgen(super::owned_worldgen::Target),
}
impl Producer {
    fn double(&self) -> Result<f64, ()> {
        match self {
            Self::Xoroshiro(source) => Ok(source.with(|data| data.double())),
            Self::Legacy(source) => source.double(),
            Self::Worldgen(kernel) => kernel.double(),
            Self::PendingWorldgen(_) => unreachable!("count resource must bind before a draw"),
        }
    }
}

pub(crate) struct Draw {
    gaussian: Owner,
    source: Option<Producer>,
    stage: Stage,
    d: f64,
    e: f64,
    f: f64,
}

impl Draw {
    pub(crate) fn new(gaussian: Arc<Gaussian>, source: Option<Arc<Source>>) -> Self {
        Self::with_owner(Owner::Detached(gaussian), source)
    }
    fn with_owner(gaussian: Owner, source: Option<Arc<Source>>) -> Self {
        Self::with_producer(gaussian, source.map(Producer::Xoroshiro))
    }
    fn with_producer(gaussian: Owner, source: Option<Producer>) -> Self {
        Self {
            gaussian,
            source,
            stage: Stage::Start,
            d: 0.,
            e: 0.,
            f: 0.,
        }
    }
    // Each advance ends every cache/source borrow before returning a VM effect.
    // Dropping an incomplete draw preserves actual cache changes from reentry.
    pub(crate) fn advance(&mut self, response: u64) -> Effect {
        let mut response = response;
        loop {
            match self.stage {
                Stage::Start => {
                    if let Some(bits) = self.gaussian.take() {
                        self.source = None;
                        self.stage = Stage::Done(bits);
                        return Effect::Complete(bits);
                    }
                    self.stage = Stage::First;
                }
                Stage::UncachedStart => {
                    if matches!(self.source,Some(Producer::PendingWorldgen(_))) {
                        self.stage=Stage::BindWorldgen;
                        return Effect::WorldgenCount;
                    }
                    self.stage=Stage::First;
                }
                Stage::BindWorldgen => {
                    if response==0 {return Effect::WorldgenCount;}
                    let Some(Producer::PendingWorldgen(target))=self.source.take()else{unreachable!()};
                    self.source=Some(Producer::Worldgen(super::owned_worldgen::Kernel::owned(
                        unsafe{super::owned_worldgen::ffi::retain(response)},target)));
                    self.stage=Stage::First;
                }
                Stage::First => {
                    self.d = 2. * f64::from_bits(response) - 1.;
                    self.stage = Stage::Second;
                }
                Stage::Second => {
                    self.e = 2. * f64::from_bits(response) - 1.;
                    self.f = self.d * self.d + self.e * self.e;
                    if self.f >= 1. || self.f == 0. {
                        self.stage = Stage::First;
                    } else {
                        self.source = None;
                        self.stage = Stage::Log;
                        return Effect::JavaLog(self.f);
                    }
                }
                Stage::Log => {
                    let g = (-2. * f64::from_bits(response) / self.f).sqrt();
                    self.gaussian.set(true, (self.e * g).to_bits());
                    let result = (self.d * g).to_bits();
                    self.stage = Stage::Done(result);
                    return Effect::Complete(result);
                }
                Stage::Done(bits) => return Effect::Complete(bits),
            }
            // Only genuine exact-default sources may supply this owned native path.
            // Custom source/generator dispatch remains an actual VM draw effect.
            if let Some(source) = self.source.as_ref() {
                match source.double() {
                    Ok(value) => response = value.to_bits(),
                    Err(()) => return Effect::LegacyCollision,
                }
            } else {
                return Effect::DrawDouble;
            }
        }
    }
}

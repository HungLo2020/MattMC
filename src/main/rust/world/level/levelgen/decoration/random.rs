//! `WorldgenRandom` over `XoroshiroRandomSource`, as `ChunkGenerator.applyBiomeDecoration`
//! creates it. Draws go through `BitRandomSource`'s derivations of `next(bits)`,
//! which takes the top bits of one Xoroshiro long; they are not Xoroshiro's own
//! `nextInt`/`nextFloat`.
use crate::world::level::levelgen::random::Xoroshiro;
use std::{cell::{Cell, RefCell}, rc::Rc};

/// Private callback framing; ordinary callbacks from other owners keep their ABI.
pub(crate) const RNG_CALLBACK: i32 = 1 << 29;
type Callback = super::region::Callback;
thread_local! { static CURRENT: RefCell<Option<Rc<Invocation>>> = const { RefCell::new(None) }; }

/// The actual public wrapper count survives the per-feature native RNGs in a
/// decoration sequence. There is one active owner, never a Java count policy.
struct Invocation {
    count: Cell<i32>,
    last: Cell<[i64; 5]>,
    active: Cell<*mut WorldgenRandom>,
    callback: Option<Callback>,
}

pub(crate) struct InvocationScope {
    owner: Rc<Invocation>,
    previous: Option<Rc<Invocation>>,
}

impl InvocationScope {
    pub(crate) fn enter(callback: Option<Callback>, state: [i64; 5]) -> Self {
        let owner = Rc::new(Invocation { count: Cell::new(state[4] as i32), last: Cell::new(state),
            active: Cell::new(std::ptr::null_mut()), callback });
        let previous = CURRENT.with(|current| current.replace(Some(Rc::clone(&owner))));
        Self { owner, previous }
    }
    pub(crate) fn callback(&self) -> Option<Callback> { self.owner.callback.map(|_| callback as Callback) }
    pub(crate) fn state(&self) -> [i64; 5] {
        let mut state = self.owner.last.get();
        state[4] = i64::from(self.owner.count.get());
        state
    }
}
impl Drop for InvocationScope {
    fn drop(&mut self) { CURRENT.with(|current| { current.replace(self.previous.take()); }); }
}

/// Installed only after the placement has reached its stable invocation stack
/// address. No RefCell borrow or reference into the RNG crosses a Java call.
pub(crate) struct ActiveRandom {
    owner: Option<Rc<Invocation>>,
    pointer: *mut WorldgenRandom,
    previous: *mut WorldgenRandom,
}
impl ActiveRandom {
    pub(crate) fn enter(random: &mut WorldgenRandom) -> Self {
        let owner = CURRENT.with(|current| current.borrow().clone());
        let pointer = random as *mut _;
        let previous = owner.as_ref().map_or(std::ptr::null_mut(), |owner| {
            random.counter = Counter::Shared(Rc::clone(owner));
            owner.last.set(random.snapshot());
            owner.active.replace(pointer)
        });
        Self { owner, pointer, previous }
    }
}
impl Drop for ActiveRandom {
    fn drop(&mut self) {
        if let Some(owner) = &self.owner {
            // SAFETY: the placement drops this guard before moving its RNG.
            owner.last.set(unsafe { (&*self.pointer).snapshot() });
            owner.active.set(self.previous);
        }
    }
}

unsafe extern "C" fn callback(op: i32, args: *mut i64, length: i32) -> i32 {
    let Some(owner) = CURRENT.with(|current| current.borrow().clone()) else { return -1; };
    let Some(callback) = owner.callback else { return -1; };
    // Math has no user callback or access to the random; keep its existing path.
    if matches!(op, super::region::OP_MATH_LOG | super::region::OP_MATH_POW
        | super::region::OP_MATH_SIN | super::region::OP_MATH_COS) {
        return callback(op, args, length);
    }
    let Ok(length) = usize::try_from(length) else { return -1; };
    if args.is_null() || length > (i32::MAX as usize) - 6 { return -1; }
    let pointer = owner.active.get();
    let mut state = if pointer.is_null() { owner.last.get() } else { (&*pointer).snapshot() };
    state[4] = i64::from(owner.count.get());
    // Transport only; every native reference/borrow ends before the actual call.
    let mut words = Vec::with_capacity(length + 6);
    words.extend_from_slice(std::slice::from_raw_parts(args, length));
    words.push(i64::from(!pointer.is_null()));
    words.extend_from_slice(&state);
    let result = callback(op | RNG_CALLBACK, words.as_mut_ptr(), words.len() as i32);
    std::ptr::copy_nonoverlapping(words.as_ptr(), args, length);
    state.copy_from_slice(&words[length + 1..length + 6]);
    owner.count.set(state[4] as i32);
    owner.last.set(state);
    if !pointer.is_null() { (&mut *pointer).restore(state); }
    // These existing callers also consume their original seed/cache replies.
    // A throwing callback must not let that older payload erase its true prefix.
    match op {
        super::region::OP_PLACE_JAVA | super::region::OP_JAVA_STEP if length >= 9 => {
            *args.add(4) = state[0]; *args.add(5) = state[1];
            *args.add(7) = state[2]; *args.add(8) = state[3];
        }
        _ => {}
    }
    result
}

enum Counter { Local(i32), Shared(Rc<Invocation>) }



pub(crate) struct WorldgenRandom {
    source: Xoroshiro,
    counter: Counter,
    /// `MarsagliaPolarGaussian`'s cached second value. `WorldgenRandom.setSeed`
    /// never resets it, so it carries from one placed feature to the next.
    pub(crate) gaussian: Option<f64>,
}

/// A gaussian cache as two words for Java: `[have, value bits]`.
pub(crate) fn gaussian_words(gaussian: Option<f64>) -> [i64; 2] {
    match gaussian {
        Some(value) => [1, value.to_bits() as i64],
        None => [0, 0],
    }
}

pub(crate) fn gaussian_from(have: i64, bits: i64) -> Option<f64> {
    (have != 0).then(|| f64::from_bits(bits as u64))
}

impl WorldgenRandom {
    pub(crate) fn from_invocation(state: [i64; 5]) -> Self {
        let mut random = Self::new(0);
        random.restore(state);
        random
    }
    pub(crate) fn new(seed: i64) -> Self {
        let mut random = Self { source: Xoroshiro::new(0, 0), counter: Counter::Local(0), gaussian: None };
        random.set_seed(seed);
        random
    }

    /// `XoroshiroRandomSource.setSeed`: `upgradeSeedTo128bit`, then mixed.
    pub(crate) fn set_seed(&mut self, seed: i64) {
        let (lo, hi) = super::super::random::upgrade_seed(seed);
        self.source = Xoroshiro::new(lo, hi);
    }

    /// `WorldgenRandom.setDecorationSeed`; returns the seed it installs.
    pub(crate) fn set_decoration_seed(&mut self, seed: i64, x: i32, z: i32) -> i64 {
        self.set_seed(seed);
        let m = self.next_long() | 1;
        let n = self.next_long() | 1;
        let result = (x as i64).wrapping_mul(m).wrapping_add((z as i64).wrapping_mul(n)) ^ seed;
        self.set_seed(result);
        result
    }

    /// `WorldgenRandom.setFeatureSeed`.
    pub(crate) fn set_feature_seed(&mut self, decoration: i64, index: i32, step: i32) {
        self.set_seed(decoration.wrapping_add(index as i64).wrapping_add(10_000i64.wrapping_mul(step as i64)));
    }

    /// The Xoroshiro state, handed to Java for a feature that still runs there.
    pub(crate) fn state(&self) -> (i64, i64) {
        self.source.state()
    }

    pub(crate) fn set_state(&mut self, lo: i64, hi: i64) {
        self.source = Xoroshiro::new(lo, hi);
    }

    pub(crate) fn count(&self) -> i32 {
        match &self.counter { Counter::Local(value) => *value, Counter::Shared(owner) => owner.count.get() }
    }
    fn snapshot(&self) -> [i64; 5] {
        let (lo, hi) = self.state();
        let [have, bits] = gaussian_words(self.gaussian);
        [lo, hi, have, bits, i64::from(self.count())]
    }
    fn restore(&mut self, state: [i64; 5]) {
        self.set_state(state[0], state[1]);
        self.gaussian = gaussian_from(state[2], state[3]);
        match &mut self.counter { Counter::Local(value) => *value = state[4] as i32,
            Counter::Shared(owner) => owner.count.set(state[4] as i32) }
    }

    /// `WorldgenRandom.next`: the high bits of one Xoroshiro long.
    pub(crate) fn next(&mut self, bits: u32) -> i32 {
        let next = self.count().wrapping_add(1);
        match &mut self.counter { Counter::Local(value) => *value = next,
            Counter::Shared(owner) => owner.count.set(next) }
        ((self.source.next_long() as u64) >> (64 - bits)) as i32
    }

    /// `BitRandomSource.nextInt(int)`; `bound` must be positive.
    pub(crate) fn next_int(&mut self, bound: i32) -> i32 {
        debug_assert!(bound > 0);
        if bound & (bound - 1) == 0 {
            return ((bound as i64 * self.next(31) as i64) >> 31) as i32;
        }
        loop {
            let j = self.next(31);
            let k = j % bound;
            if j.wrapping_sub(k).wrapping_add(bound - 1) >= 0 {
                return k;
            }
        }
    }

    /// `Mth.randomBetweenInclusive`.
    pub(crate) fn between_inclusive(&mut self, min: i32, max: i32) -> i32 {
        self.next_int(max.wrapping_sub(min).wrapping_add(1)).wrapping_add(min)
    }

    pub(crate) fn next_long(&mut self) -> i64 {
        let high = self.next(32) as i64;
        let low = self.next(32) as i64;
        (high << 32).wrapping_add(low)
    }

    /// `BitRandomSource.nextBoolean`.
    pub(crate) fn next_boolean(&mut self) -> bool {
        self.next(1) != 0
    }

    pub(crate) fn next_float(&mut self) -> f32 {
        self.next(24) as f32 * 5.960_464_5e-8_f32
    }

    /// `BitRandomSource.nextDouble`: the scale is a float, so this has float precision.
    pub(crate) fn next_double(&mut self) -> f64 {
        let high = self.next(26) as i64;
        let low = self.next(27) as i64;
        f64::from(((high << 27) + low) as f32 * 1.110_223e-16_f32)
    }
}

#[cfg(test)]
mod ownership_tests {
    use super::*;

    #[test]
    fn wrapper_count_wraps_and_reseed_does_not_reset_it() {
        let mut random = WorldgenRandom::new(42);
        random.counter = Counter::Local(i32::MAX);
        random.next(31);
        assert_eq!(random.count(), i32::MIN);
        random.gaussian = Some(7.25);
        random.set_seed(8191);
        assert_eq!(random.count(), i32::MIN);
        assert_eq!(random.gaussian, Some(7.25));
        random.next_double();
        assert_eq!(random.count(), i32::MIN + 2);
    }

    unsafe extern "C" fn nested_host(op: i32, args: *mut i64, length: i32) -> i32 {
        assert_eq!(op, super::super::region::OP_TEST_PREDICATE | RNG_CALLBACK);
        let words = std::slice::from_raw_parts_mut(args, length as usize);
        let at = words.len() - 5;
        let state: [i64; 5] = words[at..].try_into().unwrap();
        let nested = InvocationScope::enter(None, state);
        let mut random = WorldgenRandom::new(0);
        random.restore(state);
        let active = ActiveRandom::enter(&mut random);
        random.next(31);
        random.next(24);
        drop(active);
        words[at..].copy_from_slice(&nested.state());
        -1 // the genuine host consumed its prefix before failing
    }

    #[test]
    fn nested_failed_callback_preserves_actual_count_and_source_prefix() {
        let initial = WorldgenRandom::new(42).snapshot();
        let scope = InvocationScope::enter(Some(nested_host), initial);
        let mut random = WorldgenRandom::new(42);
        let active = ActiveRandom::enter(&mut random);
        random.next(1);
        let mut args = [0i64];
        assert_eq!(unsafe { callback(super::super::region::OP_TEST_PREDICATE, args.as_mut_ptr(), 1) }, -1);
        assert_eq!(random.count(), 3);
        let mut original = WorldgenRandom::new(42);
        original.next(1); original.next(31); original.next(24);
        assert_eq!(random.state(), original.state());
        random.next(31);
        drop(active);
        assert_eq!(scope.state()[4], 4);
        assert!(CURRENT.with(|current| Rc::ptr_eq(current.borrow().as_ref().unwrap(), &scope.owner)));
    }

    #[test]
    fn unrelated_native_generator_does_not_increment_primary_wrapper() {
        let scope = InvocationScope::enter(None, [1, 2, 0, 0, 19]);
        let mut primary = WorldgenRandom::new(42);
        let active = ActiveRandom::enter(&mut primary);
        let mut independent = WorldgenRandom::new(8191);
        independent.next_long();
        assert_eq!(independent.count(), 2);
        assert_eq!(primary.count(), 19);
        primary.next(1);
        drop(active);
        assert_eq!(scope.state()[4], 20);
    }

    #[test]
    fn invocation_and_random_retire_without_a_strong_cycle() {
        let scope = InvocationScope::enter(None, [1, 2, 0, 0, 0]);
        let weak = Rc::downgrade(&scope.owner);
        let mut random = WorldgenRandom::new(42);
        let active = ActiveRandom::enter(&mut random);
        random.next(1);
        drop(active);
        drop(scope);
        assert!(weak.upgrade().is_some()); // the actual RNG still owns its count
        drop(random);
        assert!(weak.upgrade().is_none());
        assert!(CURRENT.with(|current| current.borrow().is_none()));
    }
}

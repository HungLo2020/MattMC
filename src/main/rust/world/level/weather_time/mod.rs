//! World clock, weather cycle and sleep-threshold policy.
//!
//! One [`Clock`] per primary level data owns game/day time and the weather
//! counters. Each server dimension holds a [`View`] with its own
//! rain/thunder interpolation levels and a `writable` flag: derived
//! dimensions read the shared clock but their transitions never write it,
//! matching `DerivedLevelData`'s ignored setters. Clock fields are atomics:
//! any thread may read or write them. A view is mutated only on its owner
//! (server) thread, which the Java bridge enforces.
//!
//! Weather sampling stays on the level's existing `RandomSource`: [`View::plan`]
//! decides which samples the current state needs (thunder before rain), Java
//! draws exactly those, and [`View::commit`] applies the whole transition.
use std::sync::Arc;
use std::sync::atomic::{AtomicI32, AtomicI64, AtomicIsize, AtomicU8, AtomicU32, AtomicU64, Ordering::Relaxed};

/// Live clocks plus views, for lifetime verification.
pub(crate) static LIVE: AtomicIsize = AtomicIsize::new(0);

#[cfg(test)]
mod tests;
pub(crate) mod ffi;

#[derive(Default)]
pub struct Clock {
    game_time: AtomicI64,
    day_time: AtomicI64,
    clear_weather_time: AtomicI32,
    rain_time: AtomicI32,
    thunder_time: AtomicI32,
    raining: AtomicU8,
    thundering: AtomicU8,
    /// Bumped by every write; a weather plan is valid only for its generation.
    generation: AtomicU64,
}

impl Drop for Clock {
    fn drop(&mut self) { LIVE.fetch_sub(1, Relaxed); }
}

/// Plain values for construction, snapshots and tests.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct ClockState {
    pub game_time: i64,
    pub day_time: i64,
    pub clear_weather_time: i32,
    pub rain_time: i32,
    pub thunder_time: i32,
    pub raining: bool,
    pub thundering: bool,
}

impl Clock {
    pub fn new(state: ClockState) -> Self {
        LIVE.fetch_add(1, Relaxed);
        let clock = Self::default();
        clock.store(state);
        clock
    }
    pub fn state(&self) -> ClockState {
        ClockState {
            game_time: self.game_time.load(Relaxed),
            day_time: self.day_time.load(Relaxed),
            clear_weather_time: self.clear_weather_time.load(Relaxed),
            rain_time: self.rain_time.load(Relaxed),
            thunder_time: self.thunder_time.load(Relaxed),
            raining: self.raining.load(Relaxed) != 0,
            thundering: self.thundering.load(Relaxed) != 0,
        }
    }
    fn store(&self, state: ClockState) {
        self.game_time.store(state.game_time, Relaxed);
        self.day_time.store(state.day_time, Relaxed);
        self.set_weather(state.clear_weather_time, state.rain_time, state.thunder_time,
            state.raining, state.thundering);
    }
    fn generation(&self) -> u64 { self.generation.load(Relaxed) }
    /// An atomic read-modify-write: writer confinement is not enforced, so
    /// concurrent writers must never lose a generation.
    fn bump(&self) { self.generation.fetch_add(1, Relaxed); }
    fn set_weather(&self, clear: i32, rain: i32, thunder: i32, raining: bool, thundering: bool) {
        self.bump();
        self.thunder_time.store(thunder, Relaxed);
        self.rain_time.store(rain, Relaxed);
        self.clear_weather_time.store(clear, Relaxed);
        self.thundering.store(thundering as u8, Relaxed);
        self.raining.store(raining as u8, Relaxed);
    }
    pub fn set_game_time(&self, value: i64) { self.bump(); self.game_time.store(value, Relaxed); }
    pub fn set_day_time(&self, value: i64) { self.bump(); self.day_time.store(value, Relaxed); }
    pub fn set_clear_weather_time(&self, value: i32) { self.bump(); self.clear_weather_time.store(value, Relaxed); }
    pub fn set_rain_time(&self, value: i32) { self.bump(); self.rain_time.store(value, Relaxed); }
    pub fn set_thunder_time(&self, value: i32) { self.bump(); self.thunder_time.store(value, Relaxed); }
    pub fn set_raining(&self, value: bool) { self.bump(); self.raining.store(value as u8, Relaxed); }
    pub fn set_thundering(&self, value: bool) { self.bump(); self.thundering.store(value as u8, Relaxed); }
}

/// Sample requests returned by [`View::plan`]. Java draws them in bit order:
/// thunder first, then rain, each from the named duration or delay provider.
pub const PLANNED: i32 = 1;
pub const THUNDER_SAMPLE: i32 = 2;
pub const THUNDER_DURATION: i32 = 4;
pub const RAIN_SAMPLE: i32 = 8;
pub const RAIN_DURATION: i32 = 16;
const SKYLIGHT: i32 = 32;
const CYCLE: i32 = 64;

#[derive(Debug, PartialEq, Eq)]
pub enum CommitError { NoPlan, MissingSample, StalePlan }

pub struct View {
    o_rain: AtomicU32,
    rain: AtomicU32,
    o_thunder: AtomicU32,
    thunder: AtomicU32,
    plan: AtomicI32,
    plan_generation: AtomicU64,
    writable: bool,
    clock: Arc<Clock>,
}

fn load(value: &AtomicU32) -> f32 { f32::from_bits(value.load(Relaxed)) }
fn store(value: &AtomicU32, f: f32) { value.store(f.to_bits(), Relaxed); }
/// `Mth.clamp(float, 0, 1)`.
fn clamp01(f: f32) -> f32 { if f < 0.0 { 0.0 } else if f > 1.0 { 1.0 } else { f } }

impl View {
    pub fn new(clock: Arc<Clock>, writable: bool) -> Self {
        LIVE.fetch_add(1, Relaxed);
        Self { o_rain: AtomicU32::new(0), rain: AtomicU32::new(0), o_thunder: AtomicU32::new(0),
            thunder: AtomicU32::new(0), plan: AtomicI32::new(0), plan_generation: AtomicU64::new(0),
            writable, clock }
    }
    pub fn clock(&self) -> &Clock { &self.clock }
    pub fn levels(&self) -> [f32; 4] {
        [load(&self.o_rain), load(&self.rain), load(&self.o_thunder), load(&self.thunder)]
    }

    /// `Level.prepareWeather`: only the current levels start raised.
    pub fn prepare(&self) {
        let state = self.clock.state();
        if state.raining {
            store(&self.rain, 1.0);
            if state.thundering { store(&self.thunder, 1.0); }
        }
    }
    /// `Level.setRainLevel` / `setThunderLevel`.
    pub fn set_rain_level(&self, f: f32) { let g = clamp01(f); store(&self.o_rain, g); store(&self.rain, g); }
    pub fn set_thunder_level(&self, f: f32) { let g = clamp01(f); store(&self.o_thunder, g); store(&self.thunder, g); }

    /// Decide which samples this tick's weather transition needs. No sample is
    /// requested without skylight, with the cycle rule off, or while the clear
    /// countdown runs.
    pub fn plan(&self, has_skylight: bool, cycle: bool) -> i32 {
        let mut plan = PLANNED;
        if has_skylight { plan |= SKYLIGHT; }
        if has_skylight && cycle {
            plan |= CYCLE;
            let state = self.clock.state();
            if state.clear_weather_time <= 0 {
                if state.thunder_time <= 0 {
                    plan |= THUNDER_SAMPLE | if state.thundering { THUNDER_DURATION } else { 0 };
                }
                if state.rain_time <= 0 {
                    plan |= RAIN_SAMPLE | if state.raining { RAIN_DURATION } else { 0 };
                }
            }
        }
        self.plan_generation.store(self.clock.generation(), Relaxed);
        self.plan.store(plan, Relaxed);
        plan & (PLANNED | THUNDER_SAMPLE | THUNDER_DURATION | RAIN_SAMPLE | RAIN_DURATION)
    }

    /// Plan, and commit at once when no sample is needed (the common tick).
    /// Returns 0 once committed, otherwise the plan's sample bits for
    /// [`View::commit`].
    pub fn tick_weather(&self, has_skylight: bool, cycle: bool) -> i32 {
        self.plan.store(0, Relaxed);
        if !has_skylight { return 0; }
        if cycle {
            let c = &self.clock;
            let clear = c.clear_weather_time.load(Relaxed);
            let (thunder, rain) = (c.thunder_time.load(Relaxed), c.rain_time.load(Relaxed));
            if clear <= 0 && (thunder <= 0 || rain <= 0) { return self.plan(has_skylight, cycle); }
            if self.writable {
                let (thundering, raining) = (c.thundering.load(Relaxed) != 0, c.raining.load(Relaxed) != 0);
                if clear > 0 {
                    c.set_weather(clear - 1, if raining { 0 } else { 1 }, if thundering { 0 } else { 1 }, false, false);
                } else {
                    c.set_weather(clear, rain - 1, thunder - 1, raining ^ (rain == 1), thundering ^ (thunder == 1));
                }
            }
        }
        self.ramp();
        0
    }

    /// The 0.01 level ramps toward the (reread) canonical flags.
    fn ramp(&self) {
        let c = &self.clock;
        let thunder = load(&self.thunder);
        store(&self.o_thunder, thunder);
        store(&self.thunder, clamp01(if c.thundering.load(Relaxed) != 0 { thunder + 0.01 } else { thunder - 0.01 }));
        let rain = load(&self.rain);
        store(&self.o_rain, rain);
        store(&self.rain, clamp01(if c.raining.load(Relaxed) != 0 { rain + 0.01 } else { rain - 0.01 }));
    }

    /// Apply the planned transition and level ramps. Samples not requested by
    /// the plan are ignored. Nothing changes on error.
    pub fn commit(&self, thunder_sample: Option<i32>, rain_sample: Option<i32>) -> Result<(), CommitError> {
        let plan = self.plan.load(Relaxed);
        if plan & PLANNED == 0 { return Err(CommitError::NoPlan); }
        if (plan & THUNDER_SAMPLE != 0 && thunder_sample.is_none())
            || (plan & RAIN_SAMPLE != 0 && rain_sample.is_none()) {
            return Err(CommitError::MissingSample);
        }
        // A plan is consumed even when the commit fails.
        self.plan.store(0, Relaxed);
        if self.plan_generation.load(Relaxed) != self.clock.generation() { return Err(CommitError::StalePlan); }
        if plan & SKYLIGHT == 0 { return Ok(()); }
        if plan & CYCLE != 0 {
            let state = self.clock.state();
            let (mut clear, mut thunder, mut rain) =
                (state.clear_weather_time, state.thunder_time, state.rain_time);
            let (mut thundering, mut raining) = (state.thundering, state.raining);
            if clear > 0 {
                clear -= 1;
                thunder = if thundering { 0 } else { 1 };
                rain = if raining { 0 } else { 1 };
                thundering = false;
                raining = false;
            } else {
                // The plan was made from this same state on this thread; a
                // missing sample means the state changed in between.
                if thunder > 0 {
                    thunder -= 1;
                    if thunder == 0 { thundering = !thundering; }
                } else {
                    thunder = thunder_sample.ok_or(CommitError::StalePlan)?;
                }
                if rain > 0 {
                    rain -= 1;
                    if rain == 0 { raining = !raining; }
                } else {
                    rain = rain_sample.ok_or(CommitError::StalePlan)?;
                }
            }
            if self.writable { self.clock.set_weather(clear, rain, thunder, raining, thundering); }
        }
        self.ramp();
        Ok(())
    }

    /// `ServerLevel.resetWeatherCycle`.
    pub fn reset_weather(&self) {
        if self.writable {
            let clock = &self.clock;
            clock.set_rain_time(0);
            clock.set_raining(false);
            clock.set_thunder_time(0);
            clock.set_thundering(false);
        }
    }
    /// `ServerLevel.setWeatherParameters`: rain and thunder share one duration.
    pub fn set_weather(&self, clear: i32, duration: i32, raining: bool, thundering: bool) {
        if self.writable { self.clock.set_weather(clear, duration, duration, raining, thundering); }
    }
    /// First `tickTime` phase; returns the time scheduled functions observe.
    pub fn advance_game_time(&self) -> i64 {
        let next = self.clock.game_time.load(Relaxed).wrapping_add(1);
        if self.writable { self.clock.set_game_time(next); }
        next
    }
    /// Last `tickTime` phase, after scheduled functions and the daylight rule.
    pub fn advance_day_time(&self) {
        if self.writable { self.clock.set_day_time(self.clock.day_time.load(Relaxed).wrapping_add(1)); }
    }
    /// Sleeping through the night skips to the next multiple of 24000.
    pub fn skip_to_morning(&self) {
        let next = self.clock.day_time.load(Relaxed).wrapping_add(24000);
        if self.writable { self.clock.set_day_time(next.wrapping_sub(next % 24000)); }
    }
}

impl Drop for View {
    fn drop(&mut self) { LIVE.fetch_sub(1, Relaxed); }
}

/// `SleepStatus.sleepersNeeded`: Java int multiply, float divide, `Mth.ceil`.
pub fn sleepers_needed(active: i32, percentage: i32) -> i32 {
    let f = active.wrapping_mul(percentage) as f32 / 100.0;
    let i = f as i32;
    let ceil = if f > i as f32 { i.wrapping_add(1) } else { i };
    ceil.max(1)
}

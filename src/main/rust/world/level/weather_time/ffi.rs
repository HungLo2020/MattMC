//! `NativeWeatherTime` downcalls. Java never reads clock or view memory
//! directly: every read and transition goes through these functions.
use super::{Clock, ClockState, CommitError, View, LIVE, RAIN_SAMPLE, THUNDER_SAMPLE, sleepers_needed};
use std::sync::Arc;
use std::sync::atomic::Ordering::Relaxed;

#[no_mangle]
pub extern "C" fn mattmc_weather_time_create(game_time: i64, day_time: i64, clear_weather_time: i32,
    rain_time: i32, raining: i32, thunder_time: i32, thundering: i32) -> *const Clock {
    Arc::into_raw(Arc::new(Clock::new(ClockState { game_time, day_time, clear_weather_time,
        rain_time, raining: raining != 0, thunder_time, thundering: thundering != 0 })))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_release(clock: *const Clock) {
    if !clock.is_null() { drop(Arc::from_raw(clock)); }
}

/// Primary level data setters. Derived level data never calls these.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_game_time(clock: *const Clock, value: i64) { (*clock).set_game_time(value) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_day_time(clock: *const Clock, value: i64) { (*clock).set_day_time(value) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_clear_weather_time(clock: *const Clock, value: i32) { (*clock).set_clear_weather_time(value) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_rain_time(clock: *const Clock, value: i32) { (*clock).set_rain_time(value) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_thunder_time(clock: *const Clock, value: i32) { (*clock).set_thunder_time(value) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_raining(clock: *const Clock, value: i32) { (*clock).set_raining(value != 0) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_set_thundering(clock: *const Clock, value: i32) { (*clock).set_thundering(value != 0) }

/// Atomic (Relaxed) getters: Java never reads clock memory directly.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_game_time(clock: *const Clock) -> i64 { (*clock).game_time.load(Relaxed) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_day_time(clock: *const Clock) -> i64 { (*clock).day_time.load(Relaxed) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_clear_weather_time(clock: *const Clock) -> i32 { (*clock).clear_weather_time.load(Relaxed) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_rain_time(clock: *const Clock) -> i32 { (*clock).rain_time.load(Relaxed) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_thunder_time(clock: *const Clock) -> i32 { (*clock).thunder_time.load(Relaxed) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_raining(clock: *const Clock) -> i32 { (*clock).raining.load(Relaxed) as i32 }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_thundering(clock: *const Clock) -> i32 { (*clock).thundering.load(Relaxed) as i32 }

/// A view keeps its clock alive independently of the Java primary data.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_create(clock: *const Clock, writable: i32) -> *mut View {
    if clock.is_null() { return std::ptr::null_mut(); }
    Arc::increment_strong_count(clock);
    Box::into_raw(Box::new(View::new(Arc::from_raw(clock), writable != 0)))
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_release(view: *mut View) {
    if !view.is_null() { drop(Box::from_raw(view)); }
}
/// Copies the view's four levels (o-rain, rain, o-thunder, thunder) into a
/// caller-owned buffer, atomically per field, on the view's owner thread.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_levels(view: *const View, output: *mut [f32; 4]) {
    *output = (*view).levels();
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_prepare(view: *const View) { (*view).prepare() }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_set_rain_level(view: *const View, f: f32) { (*view).set_rain_level(f) }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_set_thunder_level(view: *const View, f: f32) { (*view).set_thunder_level(f) }
/// One weather step: 0 when committed without samples, else sample bits.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_tick_weather(view: *const View, has_skylight: i32, cycle: i32) -> i32 {
    (*view).tick_weather(has_skylight != 0, cycle != 0)
}
/// `provided` carries the plan's sample bits for the samples Java drew.
/// Returns 0, or 1 without a plan, 2 for a missing sample, 3 for a stale plan.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_commit(view: *const View, provided: i32,
    thunder_sample: i32, rain_sample: i32) -> i32 {
    let thunder = (provided & THUNDER_SAMPLE != 0).then_some(thunder_sample);
    let rain = (provided & RAIN_SAMPLE != 0).then_some(rain_sample);
    match (*view).commit(thunder, rain) {
        Ok(()) => 0,
        Err(CommitError::NoPlan) => 1,
        Err(CommitError::MissingSample) => 2,
        Err(CommitError::StalePlan) => 3,
    }
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_reset_weather(view: *const View) { (*view).reset_weather() }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_set_weather(view: *const View, clear: i32, duration: i32,
    raining: i32, thundering: i32) {
    (*view).set_weather(clear, duration, raining != 0, thundering != 0)
}
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_advance_game_time(view: *const View) -> i64 { (*view).advance_game_time() }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_advance_day_time(view: *const View) { (*view).advance_day_time() }
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_skip_to_morning(view: *const View) { (*view).skip_to_morning() }
#[no_mangle]
pub extern "C" fn mattmc_weather_time_sleepers_needed(active: i32, percentage: i32) -> i32 {
    sleepers_needed(active, percentage)
}
/// The view's native-decided writability, fixed at creation.
#[no_mangle]
pub unsafe extern "C" fn mattmc_weather_time_view_writable(view: *const View) -> i32 { (*view).writable as i32 }
/// Live clocks plus views in this process.
#[no_mangle]
pub extern "C" fn mattmc_weather_time_live() -> i64 { LIVE.load(std::sync::atomic::Ordering::Relaxed) as i64 }

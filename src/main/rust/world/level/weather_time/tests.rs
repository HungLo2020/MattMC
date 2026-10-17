use super::*;

fn clock(state: ClockState) -> Arc<Clock> { Arc::new(Clock::new(state)) }
/// The production step: one-pass `tick_weather`, then a commit only when it
/// requested samples.
fn tick(view: &View, skylight: bool, cycle: bool, samples: &mut impl FnMut(bool, bool) -> i32) -> i32 {
    let plan = view.tick_weather(skylight, cycle);
    if plan == 0 { return 0; }
    let thunder = (plan & THUNDER_SAMPLE != 0).then(|| samples(true, plan & THUNDER_DURATION != 0));
    let rain = (plan & RAIN_SAMPLE != 0).then(|| samples(false, plan & RAIN_DURATION != 0));
    view.commit(thunder, rain).unwrap();
    plan
}

/// The two-call path, which must agree with `tick`.
fn tick_planned(view: &View, skylight: bool, cycle: bool, samples: &mut impl FnMut(bool, bool) -> i32) {
    let plan = view.plan(skylight, cycle);
    let thunder = (plan & THUNDER_SAMPLE != 0).then(|| samples(true, plan & THUNDER_DURATION != 0));
    let rain = (plan & RAIN_SAMPLE != 0).then(|| samples(false, plan & RAIN_DURATION != 0));
    view.commit(thunder, rain).unwrap();
}

/// Literal transcription of `ServerLevel.advanceWeatherCycle` state changes.
fn reference(s: &mut ClockState, levels: &mut [f32; 4], writable: bool, skylight: bool, cycle: bool,
    samples: &mut impl FnMut(bool, bool) -> i32) {
    if !skylight { return; }
    if cycle {
        let (mut i, mut j, mut k, mut bl2, mut bl3) =
            (s.clear_weather_time, s.thunder_time, s.rain_time, s.thundering, s.raining);
        if i > 0 {
            i -= 1; j = if bl2 { 0 } else { 1 }; k = if bl3 { 0 } else { 1 }; bl2 = false; bl3 = false;
        } else {
            if j > 0 { j -= 1; if j == 0 { bl2 = !bl2; } } else { j = samples(true, bl2); }
            if k > 0 { k -= 1; if k == 0 { bl3 = !bl3; } } else { k = samples(false, bl3); }
        }
        if writable { (s.thunder_time, s.rain_time, s.clear_weather_time, s.thundering, s.raining) = (j, k, i, bl2, bl3); }
    }
    levels[2] = levels[3];
    levels[3] = clamp01(if s.thundering { levels[3] + 0.01 } else { levels[3] - 0.01 });
    levels[0] = levels[1];
    levels[1] = clamp01(if s.raining { levels[1] + 0.01 } else { levels[1] - 0.01 });
}

/// Deterministic stand-in for IntProvider sampling; records draw order.
struct Draws { seed: u64, log: Vec<(bool, bool)> }
impl Draws {
    fn sample(&mut self, thunder: bool, duration: bool) -> i32 {
        self.log.push((thunder, duration));
        self.seed = self.seed.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        let span = if duration { 600 } else { 400 };
        1 + (self.seed >> 33) as i32 % span
    }
}

#[test]
fn matches_reference_weather_cycle_and_draw_order() {
    for (skylight, cycle, start) in [
        (true, true, ClockState::default()),
        (true, true, ClockState { clear_weather_time: 3, raining: true, thundering: true, ..Default::default() }),
        (true, true, ClockState { rain_time: -5, thunder_time: i32::MIN, ..Default::default() }),
        (true, false, ClockState { raining: true, ..Default::default() }),
        (false, true, ClockState { raining: true, ..Default::default() }),
    ] {
        let view = View::new(clock(start), true);
        view.prepare();
        let mut expected = start;
        let mut levels = view.levels();
        let (mut native, mut java) = (Draws { seed: 7, log: vec![] }, Draws { seed: 7, log: vec![] });
        for _ in 0..20_000 {
            tick(&view, skylight, cycle, &mut |t, d| native.sample(t, d));
            reference(&mut expected, &mut levels, true, skylight, cycle, &mut |t, d| java.sample(t, d));
            assert_eq!(view.clock().state(), expected);
            assert_eq!(view.levels().map(f32::to_bits), levels.map(f32::to_bits));
        }
        assert_eq!(native.log, java.log);
        if !(skylight && cycle) { assert!(native.log.is_empty()); }
        // The planned two-call path and a read-only view agree with the same reference.
        let (planned, derived) = (View::new(clock(start), true), View::new(clock(start), false));
        planned.prepare(); derived.prepare();
        let (mut planned_state, mut planned_levels) = (start, planned.levels());
        let (mut derived_state, mut derived_levels) = (start, derived.levels());
        let (mut a, mut b) = (Draws { seed: 9, log: vec![] }, Draws { seed: 9, log: vec![] });
        let (mut d, mut e) = (Draws { seed: 5, log: vec![] }, Draws { seed: 5, log: vec![] });
        for _ in 0..5_000 {
            tick_planned(&planned, skylight, cycle, &mut |t, dur| a.sample(t, dur));
            reference(&mut planned_state, &mut planned_levels, true, skylight, cycle, &mut |t, dur| b.sample(t, dur));
            assert_eq!(planned.clock().state(), planned_state);
            assert_eq!(planned.levels().map(f32::to_bits), planned_levels.map(f32::to_bits));
            tick(&derived, skylight, cycle, &mut |t, dur| d.sample(t, dur));
            reference(&mut derived_state, &mut derived_levels, false, skylight, cycle, &mut |t, dur| e.sample(t, dur));
            assert_eq!(derived.clock().state(), start, "derived writes must be discarded");
            assert_eq!(derived.levels().map(f32::to_bits), derived_levels.map(f32::to_bits));
        }
        assert_eq!((a.log, d.log), (b.log, e.log));
    }
}

#[test]
fn clear_countdown_draws_nothing_and_thunder_samples_before_rain() {
    let view = View::new(clock(ClockState { clear_weather_time: 2, ..Default::default() }), true);
    assert_eq!(view.plan(true, true), PLANNED);
    view.commit(None, None).unwrap();
    assert_eq!(view.clock().state().clear_weather_time, 1);
    let view = View::new(clock(ClockState { raining: true, ..Default::default() }), true);
    assert_eq!(view.plan(true, true), PLANNED | THUNDER_SAMPLE | RAIN_SAMPLE | RAIN_DURATION);
}

#[test]
fn derived_views_share_reads_and_ignore_writes() {
    let shared = clock(ClockState { game_time: 10, day_time: 20, raining: true, ..Default::default() });
    let primary = View::new(shared.clone(), true);
    let derived = View::new(shared.clone(), false);
    derived.prepare();
    assert_eq!(derived.levels()[1], 1.0);
    let before = shared.state();
    assert_eq!(derived.advance_game_time(), 11);
    derived.advance_day_time();
    derived.skip_to_morning();
    derived.reset_weather();
    derived.set_weather(5, 6, false, true);
    assert_eq!(derived.plan(true, true), PLANNED | THUNDER_SAMPLE | RAIN_SAMPLE | RAIN_DURATION);
    derived.commit(Some(100), Some(200)).unwrap();
    assert_eq!(shared.state(), before);
    // Interpolation stays dimension-local.
    assert_eq!(primary.levels(), [0.0; 4]);
    assert_eq!(primary.advance_game_time(), 11);
    assert_eq!(derived.clock().state().game_time, 11);
}

#[test]
fn time_arithmetic_wraps_like_java_longs() {
    let view = View::new(clock(ClockState { game_time: i64::MAX, day_time: i64::MAX, ..Default::default() }), true);
    assert_eq!(view.advance_game_time(), i64::MIN);
    view.advance_day_time();
    assert_eq!(view.clock().state().day_time, i64::MIN);
    for (day, morning) in [(0, 24000), (23999, 24000), (24000, 48000), (-1, 0), (-24001, 0), (-30000, -0)] {
        view.clock().set_day_time(day);
        view.skip_to_morning();
        assert_eq!(view.clock().state().day_time, morning, "from {day}");
    }
    view.clock().set_day_time(i64::MAX - 10);
    view.skip_to_morning();
    let l = (i64::MAX - 10).wrapping_add(24000);
    assert_eq!(view.clock().state().day_time, l - l % 24000);
}

#[test]
fn commit_failures_change_nothing() {
    let view = View::new(clock(ClockState::default()), true);
    assert_eq!(view.commit(None, None), Err(CommitError::NoPlan));
    view.plan(true, true);
    assert_eq!(view.commit(Some(1), None), Err(CommitError::MissingSample));
    assert_eq!(view.clock().state(), ClockState::default());
    // State changed after planning: the commit refuses instead of inventing a sample.
    let view = View::new(clock(ClockState { thunder_time: 1, rain_time: 1, ..Default::default() }), true);
    view.plan(true, true);
    view.clock().set_thunder_time(0);
    let before = (view.clock().state(), view.levels());
    assert_eq!(view.commit(None, None), Err(CommitError::StalePlan));
    assert_eq!((view.clock().state(), view.levels()), before);
    assert_eq!(view.commit(None, None), Err(CommitError::NoPlan));
    // Any clock write between plan and commit invalidates the plan, even one
    // that leaves the planned branch unchanged.
    let view = View::new(clock(ClockState { thunder_time: 5, rain_time: 5, ..Default::default() }), true);
    view.plan(true, true);
    view.clock().set_day_time(1);
    let before = view.clock().state();
    assert_eq!(view.commit(None, None), Err(CommitError::StalePlan));
    assert_eq!(view.clock().state(), before);
    // Weather ticks of other dimensions sharing the clock do not invalidate it
    // unless they write.
    let derived = View::new(view.clock.clone(), false);
    view.plan(true, true);
    derived.plan(true, true);
    derived.commit(None, None).unwrap();
    view.commit(None, None).unwrap();
}

#[test]
fn weather_parameters_and_level_setters() {
    let view = View::new(clock(ClockState::default()), true);
    view.set_weather(0, 1200, true, true);
    let s = view.clock().state();
    assert_eq!((s.clear_weather_time, s.rain_time, s.thunder_time, s.raining, s.thundering), (0, 1200, 1200, true, true));
    view.reset_weather();
    let s = view.clock().state();
    assert_eq!((s.rain_time, s.thunder_time, s.raining, s.thundering), (0, 0, false, false));
    view.set_rain_level(3.0);
    view.set_thunder_level(-1.0);
    assert_eq!(view.levels(), [1.0, 1.0, 0.0, 0.0]);
}

/// Java: `Math.max(1, Mth.ceil(active * pct / 100.0F))` with int overflow.
fn java_sleepers(active: i32, pct: i32) -> i32 {
    let f = active.wrapping_mul(pct) as f32 / 100.0f32;
    let i = f as i32;
    std::cmp::max(1, if f > i as f32 { i + 1 } else { i })
}

#[test]
fn sleepers_needed_matches_java_arithmetic() {
    assert_eq!(sleepers_needed(0, 100), 1);
    assert_eq!(sleepers_needed(1, 0), 1);
    assert_eq!(sleepers_needed(3, 50), 2);
    assert_eq!(sleepers_needed(4, 50), 2);
    assert_eq!(sleepers_needed(7, 101), 8);
    assert_eq!(sleepers_needed(10, -5), 1);
    for (a, p) in [(i32::MAX, 100), (100_000_000, 100), (21_474_837, 100), (3, i32::MAX)] {
        assert_eq!(sleepers_needed(a, p), java_sleepers(a, p), "{a} {p}");
    }
    for a in 0..200 { for p in [0, 1, 33, 50, 99, 100, 101, 1000] {
        assert_eq!(sleepers_needed(a, p), java_sleepers(a, p));
    } }
}

#[test]
fn tick_weather_commits_sampleless_steps_in_one_call() {
    let view = View::new(clock(ClockState { rain_time: 5, thunder_time: 5, ..Default::default() }), true);
    assert_eq!(view.tick_weather(true, true), 0);
    assert_eq!(view.clock().state().rain_time, 4);
    assert_eq!(view.commit(None, None), Err(CommitError::NoPlan));
    let view = View::new(clock(ClockState::default()), true);
    assert_eq!(view.tick_weather(true, true), PLANNED | THUNDER_SAMPLE | RAIN_SAMPLE);
    view.commit(Some(3), Some(4)).unwrap();
    assert_eq!((view.clock().state().thunder_time, view.clock().state().rain_time), (3, 4));
    assert_eq!(view.tick_weather(false, true), 0);
}

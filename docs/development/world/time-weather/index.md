# World time and weather

**2026-10-14: Rust owns world time, the weather cycle and the sleep threshold; Java
still orders the tick phases.**

Rust owns game time, day time, the weather counters, each dimension's rain and
thunder levels and the sleeping-player threshold. The code is in
[`world/level/weather_time`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/weather_time/mod.rs).
[`NativeWeatherTime`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/storage/NativeWeatherTime.java)
is the Java bridge. Java still decides when each phase runs inside
`ServerLevel.tick`, sends the packets and counts players. Moving that phase
ordering into Rust is remaining goal-3 work.

## Ownership

- **Clock.** Each world's `PrimaryLevelData` holds one clock with game time,
  day time and the weather counters. Every read and write is a native call.
  Each write also advances the clock's write generation. `level.dat` is
  written from the clock's current values under the same keys as before.
- **View.** Each `ServerLevel` holds a view of that shared clock with its own
  rain and thunder levels. The overworld's view can write. Views over
  `DerivedLevelData` (the other dimensions) read the overworld clock and
  discard their writes, as `DerivedLevelData` always has. That is why
  `/time add` across all levels advances the clock once.
- The client's `Level` fields and `ClientLevelData` are unchanged.

## Contracts to preserve

- **Shared random source.** Weather durations come from the level's own
  `random`, which block callbacks also use. `plan` names the samples needed
  (thunder before rain, duration or delay), Java draws exactly those, and
  `commit` applies the whole transition. A sample drawn from the shared
  source cannot be undone. A clock write between plan and commit therefore
  fails with an exception; never replan or retry silently.
- **Derived dimensions still draw.** A derived dimension with skylight draws
  its samples as before, and the results are discarded. Its rain and thunder
  levels follow the reread overworld flags.
- **No samples** are drawn without skylight, with the `doWeatherCycle` rule
  off, or while the clear-weather countdown runs.
- **`tickTime` stays three phases.** Game time advances, scheduled functions
  run (they may change rules or time), then the daylight rule is read and day
  time advances. Do not combine these into one native call.
- Long and int arithmetic wraps as in Java, including the skip-to-morning
  rounding for negative day times.
- **Lifetime.** Clocks and views are released by GC cleaners once no Java
  object references them, not when a level closes.
  `NativeWeatherTime.liveAllocations()` reports the live count for tests.
- **Threads.**
  - Java never reads native memory directly.
  - Clock fields are Rust atomics. Every clock read is a native getter that
    loads atomically, and every write is a native atomic store, so a clock
    is safe to use from any thread.
  - A view may be changed only by its owner thread, which is the first thread
    that changes it (the server thread). Any other thread gets an exception.
  - Each view publishes its four levels as one immutable `Levels` record. A
    new record is published after every Rust call that can change the levels.
    `ServerLevel` reads one record per calculation, so it never mixes old and
    new values.
  - A view's writable flag is decided in Rust and never changes. Derived views
    use it to skip their no-op time calls.
  - Reading several clock fields one after another does not give a consistent
    set of values.

**Performance:** calls between Java and Rust add a per-tick cost. The project
owner's migration policy allows up to 25% overhead from those calls, measured
against matched Frozen runs. This slice is within that limit. See
[World time and weather performance](PERFORMANCE.md) for the measurements and
the explanation.

## Verify

```sh
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib world::level::weather_time
./gradlew -PmattmcRustProfile=release test --tests '*NativeWeatherTimeTest'
python3 DevUtils/tests/world/weather/VerifyWeatherTime.py \
  --java-home /path/to/jdk25 --output /new/output/dir --control-ref <pre-change-commit> [--benchmark]
```

[`VerifyWeatherTime.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/weather/VerifyWeatherTime.py)
runs the actual `ServerLevel` weather and time methods on Frozen, on the
pre-change Current classes and on the candidate, then compares per-tick
traces. Each trace covers state, interpolated levels, sky darkness, every
packet and its audience, and the next random draw. The run uses one primary
and three derived dimensions, commands, rule flips, a scheduled callback and
long overflow. It needs the clean Frozen checkout at
`../MattMC_JavaPerfTesting/MattMC`.

These level objects are built without their constructors. The run does not
cover players sleeping and waking, chunks, `level.dat` round trips or the
client, so check those in a live server or client run.

## Troubleshooting

- *"Native weather commit rejected (3)"*: the clock was written between plan
  and commit, for example from inside the sampler. Find that writer; do not
  catch the exception and continue.
- *"Weather view mutated from …, owned by …"*: something changed a view off
  the server thread. Route that call through the server thread instead.

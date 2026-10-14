# World time and weather performance

**Measured 2026-10-14 on one host. Under the owner's migration policy the
limit is 10% general regression, or 25% if attributable to Java/Rust boundary
overhead, measured against matched Frozen runs. The two limits are
alternatives, not additive. This slice's component cost is +19.5% against
Frozen, attributed to boundary calls.** The cost
should go away as more tick orchestration moves into Rust. This page makes no
promise of a particular speedup.

Host: HungLoSVR, a Ryzen 5 5600G with 62 GiB of RAM, running OpenJDK
25.0.4.1 and Ubuntu 26.04. The load average was 1.7–3.6 from other work on
the host. Base: `b7c56e72`. Frozen: `7a4d1817`.

## What costs time

**The remaining cost comes from crossing between Java and Rust.** Each
dimension tick now makes these calls into Rust:

- one weather step, for every dimension;
- a game-time advance and a day-time advance, for the overworld only. Derived
  dimensions skip these because their writes would be discarded anyway.

Scheduled functions run between the two time phases, so those calls cannot be
merged into one.

The figures below were measured in isolation, as the best of 10 runs of 10M
calls each, in one JVM:

| Operation | Cost per call |
|---|---|
| Plain Java field read | 0.02 ns |
| Read through a session-checked segment | 0.23 ns (in hot loops; see below) |
| Game-time or day-time call into Rust | 2.5–3.5 ns |
| Weather step with no sample needed: first cut | 10.2 ns |
| Weather step with no sample needed: one-pass, no atomic read-modify-write | 5.7 ns |

**The current read path costs one Rust call per clock read.** The earlier
direct reads of native memory raced Rust's atomic writes and were removed for
correctness. Each level refresh is one call into a buffer the view owns.

**Profiling found two costs beyond the calls themselves.** Java Flight
Recorder showed `MemorySessionImpl.checkValidStateRaw` and segment alignment
checks at the top of the candidate's profile. The rain and thunder levels are
read about 14 times per dimension tick, and reading them through a segment
was not optimized away in those call paths. The views now keep a read-only
Java copy of their four levels. The copy is refreshed after every Rust call
that can change those levels, and nothing else writes it. After this change
those samples dropped out of the profile.

**Attribution:** the remaining difference is the Rust calls listed above.
This is an inference from the isolated costs and the profiles, not a measured
breakdown of the remaining delta.

## Component benchmark

Run with `VerifyWeatherTime.py --benchmark`:

- 100k ticks × 4 dimensions (1 primary, 3 derived) per sample.
- Each tick runs `advanceWeatherCycle`, `updateSkyBrightness` and `tickTime`.
- 3 warm-up runs, then 10 samples per JVM.
- 3 alternating rounds per side, giving 30 samples per side.

Each cell is the median time per sample:

| Candidate revision | Frozen | Pre-change | Candidate | Candidate spread |
|---|---|---|---|---|
| First cut: plan + commit, 4 Rust calls per tick | 31.7 ms | 30.9 ms | 44.2 ms | 38.8–47.1, bimodal |
| Single call when no sample is needed; static sampler | 31.2 | 31.9 | 39.1 | 37.7–67.1 |
| Pointer passed as `long` | 31.1 | 31.5 | 43.2 | 37–66, bimodal |
| Derived dimensions skip time calls | 31.2 | 30.7 | 45.5 | 36.7–67.0, bimodal |
| One-pass weather step, plain generation bump | 31.4 | 31.2 | 42.9 | 35–64, bimodal |
| Global-scope reads with fences | 31.9 | 31.3 | 37.4 | 31.3–43.9 |
| Read-only Java copy of the levels (superseded: its plain reads raced native writes) | 30.9 | 31.0 | 33.4 | 30.0–34.1, unimodal |
| **Atomic native getters, owner-thread views, immutable `Levels` (current)** | **30.7** | **31.2** | **36.7** | **32.0–39.6** |

The bimodal phases came from slow candidate warm-up. A single JVM measured
over 40 samples with no warm-up shows the pre-change classes settling after
1–12 samples. The first-cut candidate needed about 20 samples to settle, at
36.5 ms against 24.7 ms. The read-only-copy candidate (superseded) settled at
26.4–28 ms against 24.8–26 ms. The current candidate has no single-JVM curve.
Its standard benchmark median is 36.7 ms against Frozen's 30.7, which is
about 15 ns per dimension tick. As an estimate, not a whole-server
measurement, that is about 1 µs of CPU per second for three dimensions at
20 ticks per second.

## Whole-server timing

Run with `BenchmarkServerTicks.py`: real dedicated servers using
`/tick sprint 6000`, alternating candidate and pre-change classes, 3 rounds
each. Each server does 4 sprints and the first is discarded. The table
records the server's own ms/tick report and the process CPU time from
`/proc`.

For the current candidate, 9 measured sprints per side, with Frozen added:

| Workload | Side | ms/tick median (range) | CPU ms/tick median (range) |
|---|---|---|---|
| Idle flat world | Frozen | 0.05 (0.04–0.05) | 0.188 (0.067–0.408) |
| Idle flat world | Pre-change | 0.05 (0.05–0.05) | 0.133 (0.113–0.153) |
| Idle flat world | Candidate | 0.05 (0.05–0.05) | 0.133 (0.092–0.185) |
| Loaded: 10×10 force-loaded chunks, 100 mobs | Frozen | 0.78 (0.75–0.83) | 0.885 (0.823–0.927) |
| Loaded | Pre-change | 1.05 (1.04–1.07) | 1.152 (1.102–1.293) |
| Loaded | Candidate | 1.06 (1.03–1.13) | 1.183 (1.112–1.253) |

**The weather slice adds no measurable whole-server cost:** candidate and
pre-change ranges overlap in both workloads.

**Diagnostic context only:** on this unmatched loaded workload, pre-change
master's ms/tick is about 35% higher than Frozen's. That is not a proven
regression. Frozen and Current generate the world differently and carry
different content, so the "same seed, normal world" workload is not
equivalent. Under the owner's policy, each slice's allowance applies to the
workload it changes, so this aggregate figure does not gate the slice.

The server's ms/tick report has 0.01 ms resolution. Process CPU time includes
GC and worker threads.

An earlier run against the superseded read-only-copy candidate measured
loaded 1.07 against 1.10 ms/tick (pre-change) and idle equal. That run had no
Frozen side.

This covers only the dedicated server. Client and FPS checks are pending on
the owner's desktop, which runs them during its normal sync and test cycle.

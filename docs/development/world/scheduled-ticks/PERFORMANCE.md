# Scheduled tick performance

**Measured 2026-10-14 on one host (HungLoSVR: Ryzen 5 5600G, OpenJDK
25.0.4.1).** The limits come from the owner's migration policy: up to 10%
general regression, or up to 25% if the regression comes from Java/Rust
boundary overhead. Each is measured against matched Frozen on the workload
that changed, and the two are not added together. Base: `f3815385`. Frozen:
`7a4d1817`.

## Benchmark

The numbers come from `VerifyChunkTicks.py --benchmark`. One sample is a
seeded 4000-tick `LevelTicks` run over 64 containers, with about 60 schedules
per tick. Each JVM does 5 warm-up runs, then 10 samples, over 3 rotating
rounds, giving 30 samples per side.

There are two workloads:

- **stress** also calls `clearArea` from 5% of callbacks and occasionally runs
  `copyAreaFrom`;
- **gameplay** leaves those area operations out. In the game they run only
  from game tests and `/clone`.

Medians, newest candidate last:

| Candidate | Frozen | Pre-change | Candidate | vs Frozen |
|---|---|---|---|---|
| r2: native chunk queue only | 47.8–51.3 | 48.4–50.7 | 60.6–63.4 | +18 to +33% (pooled +26.1%) |
| r3: native drain, one call with the full container table | 46.5–47.8 | 46.6–47.9 | 67.1–67.9 | +42 to +44% |
| r4 first cut: registry and persistent heap | 51.9 | 51.5 | 76.8 | +48% |
| r4: in-module hasher, no `Arc` clones, stress | 72.8 | 74.4 | 92.4 | +26.9% |
| r4: in-module hasher, no `Arc` clones, gameplay | 66.0 | 67.1 | 77.5 | +17.4% |
| **r5: native `clearArea` box removal (current), stress** | **68.4** | **72.4** | **78.2** | **+14.4%** |
| **r5 current, gameplay** | **67.3** | **66.7** | **77.4** | **+15.0%** |

The r4 and r5 rows come from the driver with its phase timers added, so they
are not directly comparable with the earlier rows. The r5 stress workload also
covers chunks at negative coordinates and full-range `y` boxes.

## Where the time goes

Phase timers in the driver, from the r4 first cut, against Frozen:

- scheduling: 32.8 against 23.7 ms
- `tick()`: 49.0 against 34.1 ms

Two causes were found:

1. Rust's default hasher (SipHash) was used for the dedup set on every
   schedule and poll, and for the per-collect container map.
2. Every `collect` call cloned an `Arc` for each container it touched.

Both are fixed.

In r5, `clearArea` removes the ticks in its box with one Rust call per
container, following the same JDK iterator-removal sequence. Before that, it
copied every container's entry ids across to Java and tested them there.

The remaining cost is one Java-to-Rust call per scheduled tick, plus the Java
table that maps entry ids back to tick objects, plus one call per collect.
That is an inference from the timers and profiles, not a measured breakdown
of every nanosecond.

**Status:** both workloads are within the 25% boundary allowance, at +14.4%
and +15.0%. Moving more of tick orchestration into Rust is the intended way
to remove the remaining boundary cost.

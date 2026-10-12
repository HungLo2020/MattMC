# Latest Rust vs Frozen performance (2026-10-12; native168dd924; matched JVM)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1324.95 / 1334.95 **FAIL** | 3.410 / 3.629 |
| Vanilla + DH | 811.80 / 867.40 **FAIL** | 3.142 / 4.774 |
| Shaders | 343.80 / 316.05 | 5.186 / 7.036 |
| Shaders + DH | 263.60 / 225.90 | 5.199 / 9.412 |
All20 exact6000-frame runs clean; live heap/GC/JIT controls identical; final source/native/Frozen/prompt/three input guards pass. Vanilla FPS misses0.75%, DH FPS misses6.41%; all p99 and shader FPS floors pass. Aggregate performance remains FAIL; no isolated gain/regression cause claimed.
Rust2698/3ignored; Java/parity2022/2skip; actual Frozen index6721/manager4417 match. Seven lifecycles/two world/cow-body-shadow pairs pass,VUID0;14 originals reviewed/10 identities match. Broad/temporal/streaming/memory acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#local-section-coordinate-checkpoint).

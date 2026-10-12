# Latest Rust vs Frozen performance (2026-10-11; native6076262e; matched JVM)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1263.55 / 1268.40 **FAIL** | 3.674 / 3.460 **FAIL** |
| Vanilla + DH | 769.85 / 634.30 | 3.281 / 6.727 |
| Shaders | 349.80 / 315.70 | 5.034 / 7.169 |
| Shaders + DH | 269.00 / 226.60 | 5.276 / 9.146 |
All20 exact6000-frame runs clean; live heap/GC/JIT controls identical; final source/native/Frozen/prompt/three input guards pass. Vanilla FPS misses0.38%, p99 misses6.18%; DH and both shader modes pass. Aggregate performance remains FAIL; no isolated gain claimed.
Rust2691/3ignored; Java/parity2018/2skip; Frozen membership6123/manager4417/DH1280 pass. Seven lifecycles/two world/cow-body-shadow pairs pass,VUID0;14 originals reviewed/10 identities match. Broader performance/parity, streaming/memory and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#local-entity-section-checkpoint).

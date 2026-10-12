# Latest Rust vs Frozen performance (2026-10-12; nativeca8bf37a; matched JVM)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1286.70 / 1222.20 | 3.597 / 3.564 |
| Vanilla + DH | 784.95 / 836.05 | 3.251 / 4.907 |
| Shaders | 341.00 / 305.65 | 4.986 / 8.140 |
| Shaders + DH | 246.70 / 217.60 | 7.258 / 10.453 |
All20 exact6000-frame runs clean; live heap/GC/JIT controls identical; final source/native/class/Frozen/prompt/three input guards pass. Vanilla p99 misses0.93%, DH FPS misses6.11%; remaining floors pass. Aggregate performance FAIL; no isolated whole-game gain claimed.
Rust2711/3ignored; Java2029/2skip; actual Frozen lighting492 passes/437089 state rows match. Seven lifecycles/two settled pairs/matching six-column flights pass,VUID0;17 originals reviewed. Hazy lifecycle views do not accept world entry; broad/temporal/streaming/memory acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#local-light-work-checkpoint).

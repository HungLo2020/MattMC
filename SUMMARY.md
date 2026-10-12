# Latest Rust vs Frozen performance (2026-10-12; native3c8e2d3d; code8e16a569c)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1312.65 / 1208.70 | 3.362 / 3.540 |
| Vanilla + DH | 742.00 / 808.40 | 4.310 / 5.175 |
| Shaders | 351.20 / 315.50 | 4.834 / 6.930 |
| Shaders + DH | 260.80 / 217.35 | 6.573 / 10.419 |
All20 exact6000-frame runs clean; every live heap/GC/JIT control identical; final source/native/class/Frozen/prompt/three input guards pass. DH FPS misses8.2%; other FPS/allp99 floors pass. Aggregate FAIL; no isolated whole-game gain claimed. Current DH RSS8.52–9.12GiB,combined9.53/9.92GiB below guard; long memory remains open.
Rust2714/3ignored; Java2032/2skip; actual Frozen lighting492 passes/437089 state rows match. Seven lifecycles/two settled pairs/matching six-column flights pass,VUID0;17 originals reviewed. Hazy lifecycle views do not accept world entry; broad/temporal/streaming acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#block-light-sources-and-bulk-dh-resize).

# Latest Rust vs Frozen performance (2026-10-12; natived64eb03a; matched JVM)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1255.50 / 1225.65 | 3.654 / 3.543 |
| Vanilla + DH | 727.60 / 801.55 | 4.758 / 5.098 |
| Shaders | 345.50 / 317.75 | 4.800 / 7.195 |
| Shaders + DH | 258.80 / 226.65 | 6.787 / 9.552 |
All20 exact6000-frame runs clean; live heap/GC/JIT controls identical; final source/native/Frozen/prompt/three input guards pass. Vanilla p99 misses3.15%, DH FPS misses9.23%; remaining floors pass. Aggregate performance FAIL; no isolated cause/gain claimed.
Rust2707/3ignored; Java/parity2028/2skip; actual Frozen fog11778 matches. Seven lifecycles/two world/cow/rain comparisons pass,VUID0;22 accepted originals reviewed/11 identities match. Weather foreground/HUD, broad/temporal/streaming/memory acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#local-fog-policy-checkpoint).

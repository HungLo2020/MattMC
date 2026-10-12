# Latest Rust vs Frozen performance (2026-10-12; native35b68379; matched JVM)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1316.75 / 1199.85 | 3.317 / 3.943 |
| Vanilla + DH | 819.35 / 784.90 | 2.807 / 5.936 |
| Shaders | 346.80 / 314.55 | 4.391 / 7.359 |
| Shaders + DH | 258.60 / 225.55 | 6.685 / 9.325 |
All20 exact6000-frame runs clean; all four FPS/p99 floors PASS; live heap/GC/JIT controls identical; final source/native/Frozen/prompt/three input guards pass. Settled rotating workload only; no isolated migration speedup is claimed.
Rust2703/3ignored; Java/parity2026/2skip; actual Frozen residency5264/index6721/lookup4417 match. Seven lifecycles/two world/cow-body-shadow pairs pass,VUID0;14 originals reviewed/10 identities match. Broad/temporal/streaming/memory acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#local-entity-residency-checkpoint).

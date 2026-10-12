# Latest Rust vs Frozen performance (2026-10-12; native94d74a8b; DH codec)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1340.95 / 1216.60 | 3.435 / 3.626 |
| Vanilla + DH | 743.50 / 789.60 | 4.268 / 5.316 |
| Shaders | 347.60 / 316.05 | 5.344 / 7.080 |
| Shaders + DH | 261.70 / 225.95 | 6.980 / 8.950 |
All20 exact6000-frame runs clean; every live heap/GC/JIT control identical; source/native/class/Frozen/prompt/prepared-input/saved-corpus guards pass. DH FPS misses5.8%; other FPS/allp99 floors pass. Aggregate FAIL; no isolated whole-game gain claimed. DH RSS8.77–9.23GiB,combined9.89/10.40GiB below guard; long memory remains open.
Rust2718/3ignored; Java2036/2skip; actual Frozen codec696830 rows/170 cases match, including120 saved blobs. Seven lifecycles and DH/Iris+DH settled pairs pass,VUID0;11 accepted originals/9 identities reviewed. Real24-DTO hydration oldCurrent/current/Frozen110.88/78.29/41.72ms;29.4% faster but still1.88xFrozen. Broad/temporal/streaming acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#dh-saved-point-codec-checkpoint).

# Latest Rust vs Frozen performance (2026-10-12; native a9286e05; DH render sources)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1322.30 / 1231.60 | 3.243 / 3.650 |
| Vanilla + DH | 796.85 / 873.55 | 3.363 / 4.368 |
| Shaders | 347.40 / 314.85 | 5.376 / 7.484 |
| Shaders + DH | 264.00 / 226.95 | 6.492 / 9.940 |
16 completed 6,000-frame windows, plus one retained DH startup failure (13.25 GiB RSS, zero measured frames). DH FPS misses Frozen by 8.8%; other FPS floors and all p99 floors pass. Matched JVM/source controls; no isolated migration FPS gain claimed.
Rust 2,728 passed / 3 ignored; Java 2,046 passed / 2 skipped. Seven lifecycle and three settled pairs pass. Saved-source CPU build 51.1% faster Frozen, 2.6% slower predecessor; Java allocation 88.1% lower predecessor. Memory diagnosis reached 9.52 GiB without reproducing the failed peak. Temporal/streaming/memory acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#render-source-storage-checkpoint).

# Latest Rust vs Frozen performance (2026-10-12; nativea6467e9d; light inputs)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1178.80 / 1162.10 | 3.752 / 4.111 |
| Vanilla + DH | 803.15 / 669.25 | 3.116 / 6.331 |
| Shaders | 348.40 / 316.40 | 4.791 / 7.003 |
| Shaders + DH | 263.80 / 227.30 | 6.577 / 8.755 |
All20 measured6000-frame runs clean; matched live JVM controls and source/native/class/Frozen/prompt/prepared-input/corpus guards pass. All FPS/p99 floors PASS in this scene; no isolated migration speedup claimed. Successful DH RSS8.88–9.20GiB,combined9.63/9.75GiB. Earlier DH startup RSS12.95GiB/0 frames failed and remains unroot-caused; memory bounding stays open.
Rust2725/3ignored; Java2043/2skip; actual Frozen437089 rows/492 passes match with6 primary-owner probes. Seven lifecycle/three settled pairs pass,VUID0;13 retained originals/10 native identities reviewed after gate retention recovery. Complete lighting workload timings unchanged vs predecessor. Broad/temporal/streaming acceptance and Java removal remain open. [Results](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#direct-light-section-input-checkpoint).

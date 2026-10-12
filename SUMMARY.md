# Last measured Rust vs Frozen performance (2026-10-11; native 9f372451)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1313.65 / 1142.25 | 3.248 / 3.307 |
| Vanilla + DH | 808.10 / 709.80 | 3.156 / 5.266 |
| Shaders | 341.95 / 312.95 | 4.780 / 7.169 |
| Shaders + DH | 260.60 / 226.05 | 5.275 / 9.203 |
All 20 alternating runs clean; all FPS/p99 floors and identity/input guards pass. Exact 6,000 frames, 360 settle/240 warmup; DH four repeats per side, others two. Combined Rust: 2,680 passed/3 ignored; Java/parity: 2,005 executed/2 skipped. Frozen replays: 11,489 geometry/removal, 645 range, 4,143 order; seven lifecycles, two world pairs and four GUI pairs/12 original reviews pass, zero VUIDs.
Rust removal/camera retirement and bounded model alpha masks are verified on atlas/model base 71762d602. No isolated speedup or prior p99 cause proved. First-frame/first-turn, streaming/memory, broad gameplay and Java removal remain unfinished. [Results and limits](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary).

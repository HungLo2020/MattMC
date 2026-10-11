# Rust vs Frozen performance (2026-10-11; native c487f375; settled rotating view)

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1351.75 / 1146.45 | 3.410 / 3.253 — FAIL |
| Vanilla + DH | 833.00 / 712.00 | 2.815 / 5.481 |
| Shaders | 352.65 / 312.65 | 4.756 / 7.181 |
| Shaders + DH | 264.80 / 226.00 | 5.371 / 9.124 |
All 20 alternating runs are clean: 6,000 frames, 360 settle/240 warmup; DH four repeats per side, others two. Identity/input guards pass. Rust: 2,656 passed/3 ignored; Java/parity: 2,003 executed/2 skipped. Frozen replays: 11,057 geometry/645 range/4,143 order cases; seven lifecycles/two reviewed image pairs pass, zero VUIDs.
Vanilla p99 fails by 4.8%; all other FPS/p99 floors pass. First-frame/first-turn, streaming/memory, broad gameplay and Java removal remain unfinished. [Results and limits](docs/development/rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary).

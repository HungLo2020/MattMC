# Rust vs Frozen Java speed (2026-10-07 evening, moving camera, FPS / median frame; Frozen from the midday same-day session)
| Scenario | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders, 1,800 frames | 311–349 / 2.60–2.93 ms (GPU-bound) | 307–309 / 2.99 ms (earlier session) |
| Shaders + DH, 1,800 / 6,000 frames | 223 / 4.10 ms, p99 10.3; 241 / 3.99 ms, p99 8.4 | 232 / 4.11 ms, p99 8.0 |
| Vanilla + DH, 1,800 frames | 472 / 1.70 ms | 271 / 2.99 ms |
| Vanilla, 1,800 frames | 813–882 / 0.84–0.89 ms (short runs: warm-up spikes) | 1000 / 0.84 ms |
| Vanilla, 60,000 frames (profiler attached) | 1590–1630 / 0.51–0.53 ms (midday) | 1462 / 0.52 ms (30,000, earlier session) |

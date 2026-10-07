# Rust vs Frozen Java speed (2026-10-07 midday, same session, moving camera, FPS / median frame)
| Scenario | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders, 1,800 frames | 314–316 / 2.98–3.00 ms (GPU-bound) | 307–309 / 2.99 ms (earlier session) |
| Shaders + DH, 1,800 frames | 232 / 4.06 ms, p99 10.3 | 232 / 4.11 ms, p99 8.0 |
| Vanilla + DH, 1,800 frames | 342–346 / 2.00–2.05 ms | 271 / 2.99 ms (was 415 in an earlier session) |
| Vanilla, 1,800 frames | 707–808 / 0.80–0.95 ms (spikes: warm-up, entity-heavy view) | 1000 / 0.84 ms |
| Vanilla, 1,800 after 6,000 warm-up | 1018 / 0.81 ms | 1395 / 0.66 ms (earlier session) |
| Vanilla, 60,000 frames (profiler attached) | 1590–1630 / 0.51–0.53 ms | 1462 / 0.52 ms (30,000, earlier session) |

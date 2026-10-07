# Rust vs Frozen Java speed (2026-10-07 midday, same session, moving camera, FPS / median frame)
| Scenario | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders, 1,800 frames | 314–316 / 2.98–3.00 ms (GPU-bound) | 307–309 / 2.99 ms (earlier session) |
| Shaders + DH, 1,800 frames | 227 / 4.19 ms, p99 10.7 | 232 / 4.11 ms, p99 8.0 |
| Vanilla + DH, 1,800 frames | 342–346 / 2.00–2.05 ms | 271 / 2.99 ms (was 415 in an earlier session) |
| Vanilla, 1,800 frames | 679–764 / 0.96–1.16 ms (JIT warm-up bound) | 1000 / 0.84 ms |
| Vanilla, 1,800 after 6,000 warm-up | 1018 / 0.81 ms | 1395 / 0.66 ms (earlier session) |
| Vanilla, 60,000 frames (profiler attached) | 1523–1580 / 0.52–0.55 ms | 1462 / 0.52 ms (30,000, earlier session) |

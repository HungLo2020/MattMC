# Rust vs Frozen Java speed (2026-10-07; moving camera, same settings, FPS / median frame)
| Scenario | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders, 1,800 frames | 314–316 / 2.98–3.00 ms (GPU-bound) | 307–309 / 2.99 ms |
| Shaders + DH, 1,800 frames | 223 / 4.21 ms (was 193 / 4.94) | 228 / 4.19 ms |
| Vanilla + DH, 1,800 frames | 417 / 2.01 ms (was 332–354) | 415 / 2.08 ms |
| Vanilla, 1,800 frames (240 warm-up) | 679–749 / 1.01–1.16 ms (JIT warm-up bound; was 545–676) | 937 / 0.84 ms |
| Vanilla, 1,800 after 6,000 warm-up | 912 / 0.94 ms (earlier today) | 1395 / 0.66 ms |
| Vanilla, long run | 1436 / 0.58 ms (60,000 frames, profiler attached) | 1462 / 0.52 ms (30,000) |

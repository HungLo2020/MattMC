# Rust vs Frozen Java speed (2026-10-07; moving camera, same settings, FPS / median frame)
| Scenario | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders, 1,800 frames | 291–305 / 3.06–3.10 ms (GPU-bound) | 307–309 / 2.99 ms |
| Shaders + DH, 1,800 frames | 215 / 4.25 ms (was 193 / 4.94) | 228 / 4.19 ms |
| Vanilla + DH, 1,800 frames | 354 / 2.15 ms | 415 / 2.08 ms |
| Vanilla, 1,800 frames (240 warm-up) | 528 / 1.51 ms (Java-bound, view-dependent) | 937 / 0.84 ms |
| Vanilla, 1,800 after 6,000 warm-up | 912 / 0.94 ms (was 860 / 1.04) | 1395 / 0.66 ms |
| Vanilla, 30,000 after 6,000 warm-up | 1334 / 0.64 ms (was 1079 / 0.81) | 1462 / 0.52 ms |

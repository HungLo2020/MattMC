# Rust vs Frozen Java speed (2026-10-07; moving camera, same settings, FPS / median frame)
| Scenario | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders, 1,800 frames | 314 / 2.98 ms (GPU-bound) | 307–309 / 2.99 ms |
| Shaders + DH, 1,800 frames | 223 / 4.21 ms (was 193 / 4.94) | 228 / 4.19 ms |
| Vanilla + DH, 1,800 frames | 338–354 / 2.15–2.17 ms (warm-up; matches by run end) | 415 / 2.08 ms |
| Vanilla, 1,800 frames (240 warm-up) | 595 / 1.37 ms (was ~500 / 1.6; Java-bound) | 937 / 0.84 ms |
| Vanilla, 1,800 after 6,000 warm-up | 912 / 0.94 ms (earlier today) | 1395 / 0.66 ms |
| Vanilla, 30,000 after 6,000 warm-up | 1334 / 0.64 ms (earlier today) | 1462 / 0.52 ms |

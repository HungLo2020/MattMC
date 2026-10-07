# Rust vs Frozen Java speed (updated 2026-10-07)

| Scenario (moving camera, same settings both sides) | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders (Complementary), 1,800 frames: FPS / median | 291–305 FPS / 3.06–3.10 ms | 307–309 FPS / 2.99 ms |
| Shaders + DH, 1,800 frames | 215 FPS / 4.25 ms (was 193 / 4.94) | 228 FPS / 4.19 ms |
| Vanilla + DH, 1,800 frames | 354 FPS / 2.15 ms | 415 FPS / 2.08 ms |
| Vanilla, 1,800 frames after 240 warm-up: FPS / median | 492–517 FPS / 1.56–1.66 ms | 937 FPS / 0.84 ms |
| Vanilla, 1,800 frames after 6,000 warm-up | 912 FPS / 0.94 ms (was 860 / 1.04) | 1395 FPS / 0.66 ms |
| Vanilla, 30,000 frames after 6,000 warm-up | 1334 FPS / 0.64 ms (was 1079 / 0.81) | 1462 FPS / 0.52 ms |

Shaders: GPU-bound (100%), ~2% behind. Vanilla: native worker bounds (world record + GUI + GAL encode); Java JIT warm-up dominates short runs.

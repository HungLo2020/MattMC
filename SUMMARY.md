# Rust vs Frozen Java speed (updated 2026-10-07)

| Scenario (moving camera) | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders (Complementary), 1,800 frames: FPS / median | 291–305 FPS / 3.06–3.10 ms | 307–309 FPS / 2.99 ms |
| Shaders GPU frame time (GPU at 100%) | 2.93–3.0 ms | ~3.0 ms |
| Vanilla, 1,800 frames after 240 warm-up: FPS / median | 492–517 FPS / 1.56–1.66 ms | 937 FPS / 0.84 ms |
| Vanilla, steady state (6,000 warm-up): FPS / median | 860–1079 FPS / 0.81–1.04 ms | 1395 FPS / 0.66 ms |

Shaders: GPU-bound, ~2% behind. Vanilla: native worker bounds at steady state (world record + GUI + GAL encode), Java JIT warm-up dominates short runs.

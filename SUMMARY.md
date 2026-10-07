# Rust vs Frozen Java speed (updated 2026-10-06)

| Scenario (moving camera, 1,800 frames) | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders (Complementary) FPS / median frame | 291–302 FPS (latest 302.5, near-to-far terrain) / 3.10 ms | 307 FPS / 2.99 ms |
| Shaders, last 600 frames (mean) | 3.10–3.24 ms | 3.20 ms |
| Shaders GPU frame time | 2.93 ms (was 3.18 before near-to-far order) | ~3.0 ms (GPU-bound) |
| Vanilla FPS / median frame | 516 FPS / 1.60 ms (Java-bound; worker 1.3 ms, GPU 0.68 ms) | 937 FPS / 0.84 ms |

Shader gap: first ~600 frames (Java JIT warm-up, streaming) and heavier GPU terrain/shadow views. Vanilla gap: CPU (native worker + Java).

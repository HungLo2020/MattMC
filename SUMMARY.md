# Rust vs Frozen Java speed (updated 2026-10-06)

| Scenario (moving camera, 1,800 frames) | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Shaders (Complementary) FPS / median frame | 271–288 FPS (latest 283–284) / 3.32–3.38 ms | 307 FPS / 2.99 ms |
| Shaders, last 600 frames (mean) | 3.10–3.24 ms | 3.20 ms |
| Shaders GPU frame time | 3.18–3.23 ms | ~3.0 ms (GPU-bound) |
| Vanilla FPS / median frame | 451–472 FPS / 1.80 ms (Java-bound; worker 1.40 ms, GPU 0.68 ms) | 937 FPS / 0.84 ms |

Shader gap: first ~600 frames (Java JIT warm-up, streaming) and heavier GPU terrain/shadow views. Vanilla gap: CPU (native worker + Java).

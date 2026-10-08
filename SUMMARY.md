# Rust vs Frozen Java speed (2026-10-07 evening, same desktop and session, moving camera; FPS / median frame)
| Mode | Rust/Vulkan (current) | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 813–882 / 0.84–0.89 ms (1,800 frames) | 900 / 0.87 ms |
| Vanilla + DH | 631 / 1.50 ms (6,000 frames); 440–549 / 1.53–1.75 ms (1,800) | 671 / 1.28 ms |
| Shaders | 311–349 / 2.60–2.93 ms (GPU-bound) | 304 / 3.04 ms |
| Shaders + DH | 223–241 / 3.99–4.10 ms (GPU-bound) | 226 / 4.20 ms |
Gap: vanilla + DH (worker-bound on ~430 per-column DH draws, each with its own descriptor-set bind). Next fix: shared vertex pages + multi-draw indirect.

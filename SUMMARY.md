# Rust vs Frozen Java speed (2026-10-07 20:32, same desktop and session, moving camera, 6,000 frames; FPS / median frame)
| Mode | Rust/Vulkan (current) | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,100 / 0.67 ms | 1,151 / 0.73 ms |
| Vanilla + DH | 617 / 1.31 ms | 736 / 1.14 ms |
| Shaders | 341 / 2.83 ms (GPU-bound) | 318 / 3.02 ms |
| Shaders + DH | 253 / 3.87 ms (GPU-bound) | 229 / 4.23 ms |
Single runs; this desktop shows about ±25% run-to-run noise in vanilla + DH. All runs clean (0 VUIDs, 0 exceptions).
Gap: vanilla + DH (worker-bound on per-column DH draws). DH geometry now uses shared pages with one set per page; next: multi-draw indirect.

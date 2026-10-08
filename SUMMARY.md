# Rust vs Frozen Java speed (2026-10-07 22:36, desktop RTX 2070, moving camera, interleaved ABAB, 6,000 frames; FPS per run)
| Mode | Rust/Vulkan (current) | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,171 / 1,130 | 1,226 / 1,123 |
| Vanilla + DH | 757 / 770 | 736 / 599 |
| Shaders | 343 / 351 (GPU-bound) | 318 / 316 |
| Shaders + DH | 250 / 252 (GPU-bound) | 228 / 227 |
All runs clean (0 VUIDs, 0 exceptions). Run-to-run noise on this desktop is up to ±20% (Frozen vanilla + DH 736 vs 599).
Rust leads in shaders, shaders + DH and vanilla + DH and is within noise in vanilla. Next DH step: multi-draw indirect.
Reproduce: `python3 DevUtils/tests/rendering/RunValidation.py --label <new> --perf --skip java-tests --skip rust-tests --skip wiki --skip gate --skip parity`.

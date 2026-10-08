# Rust vs Frozen Java speed (2026-10-08; native state graphs + fluid definitions; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,062.5 / 996.9 | 1,097.2 / 1,094.7 |
| Vanilla + DH | 590.9 / 600.1 | 892.8 / 570.0 |
| Shaders | 331.3 / 327.6 | 305.6 / 302.6 |
| Shaders + DH | 238.8 / 238.7 | 217.6 / 218.5 |
ABAB receipts: `artifacts/graphics-captures/validation/native-fluid-definitions-master-20261008/summary.json`; all 16 runs clean, exact frame counts, VUID0.
Performance FAIL: FPS medians in table order -6.0%, -18.6%, +8.3%, +9.5%; p99 Current/Frozen 3.987/3.628, 6.945/5.740, 10.777/7.387, 14.423/9.907 ms. All four tails fail.
Java1,697/Rust2,351 tests pass; 2 Java skips/3 Rust ignores. Seven lifecycle cases and reviewed vanilla/Iris+DH coast pairs pass; broader parity and Rust-only application remain incomplete.

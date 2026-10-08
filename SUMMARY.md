# Rust vs Frozen Java speed (2026-10-08; native block physics, before intrinsic-state follow-up; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,004.7 / 1,054.4 | 1,111.5 / 1,124.9 |
| Vanilla + DH | 495.7 / 703.1 | 705.4 / 598.2 |
| Shaders | 322.9 / 325.7 | 303.2 / 306.3 |
| Shaders + DH | 238.6 / 242.8 | 217.9 / 216.3 |
ABAB receipts: `artifacts/graphics-captures/validation/native-block-physics-master-20261008/summary.json`; all16 runs clean, exact6000,VUID0;25 generated fixture copies retired.
Performance FAIL: FPS medians in table order −7.9%, −8.0%, +6.4%, +10.9%; p99 Current/Frozen 3.627/3.752, 7.310/5.991, 11.447/7.323, 14.605/9.860 ms. Vanilla/DH average FPS and three p99 comparisons fail; no isolated physics speedup established.
Java1,701/Rust2,357 tests pass;2 Java skips/3 Rust ignores. Seven lifecycle cases, reviewed vanilla/Iris+DH pairs and all five content digests pass; full parity and Rust-only application remain incomplete.

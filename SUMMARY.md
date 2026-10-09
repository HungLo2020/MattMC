# Rust vs Frozen Java speed (2026-10-08; native block families; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,119.3 / 1,012.5 | 1,132.6 / 1,156.1 |
| Vanilla + DH | 597.1 / 559.9 | 833.0 / 793.2 |
| Shaders | 333.3 / 341.2 | 311.0 / 312.7 |
| Shaders + DH | 247.1 / 240.6 | 223.7 / 223.0 |
ABAB receipts: `artifacts/graphics-captures/validation/native-block-families-master-20261008/summary.json`; all16 runs clean, exact6000,VUID0;25 generated fixture copies retired.
Performance FAIL: FPS medians in table order -6.9%, -28.9%, +8.1%, +9.2%; p99 Current/Frozen 3.742/3.473, 7.973/4.275, 5.310/6.205, 7.735/8.257 ms. Vanilla/DH average and p99 fail;both shader modes pass. The larger DH gap needs investigation; no isolated family speedup or regression attribution established.
Java1,711/Rust2,366 tests pass;2 Java skips/3 Rust ignores. Seven lifecycle cases,reviewed vanilla/Iris+DH pairs and all eight content digests pass;full parity and Rust-only application remain incomplete.

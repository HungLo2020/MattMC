# Rust vs Frozen Java speed (2026-10-08; native sounds/offsets, before family follow-up; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,152.5 / 1,124.0 | 1,170.6 / 1,175.3 |
| Vanilla + DH | 613.2 / 624.7 | 669.9 / 617.4 |
| Shaders | 348.0 / 345.9 | 320.4 / 318.3 |
| Shaders + DH | 249.8 / 249.0 | 228.2 / 227.1 |
ABAB receipts: `artifacts/graphics-captures/validation/native-block-materials-master-20261008/summary.json`; all16 runs clean, exact6000,VUID0;25 generated fixture copies retired.
Performance FAIL: FPS medians in table order -3.0%, -3.8%, +8.6%, +9.6%; p99 Current/Frozen 3.559/3.007, 6.987/6.581, 5.678/5.860, 8.001/7.816 ms. Vanilla/DH average and p99 fail; shaders+DH p99 fails. No isolated sound/offset speedup established.
Java1,708/Rust2,363 tests pass;2 Java skips/3 Rust ignores. Seven lifecycle cases, reviewed vanilla/Iris+DH pairs and all seven content digests pass; full parity and Rust-only application remain incomplete.

# Rust vs Frozen Java speed (2026-10-08; native block intrinsics, before sound/offset follow-up; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,107.5 / 1,106.4 | 1,149.1 / 1,201.9 |
| Vanilla + DH | 626.0 / 630.0 | 792.3 / 582.2 |
| Shaders | 326.8 / 328.3 | 310.5 / 308.3 |
| Shaders + DH | 245.0 / 244.1 | 221.8 / 221.0 |
ABAB receipts: `artifacts/graphics-captures/validation/native-block-intrinsics-master-20261008/summary.json`; all16 runs clean, exact6000,VUID0;25 generated fixture copies retired.
Performance FAIL: FPS medians in table order -5.8%, -8.6%, +5.9%, +10.5%; p99 Current/Frozen 3.582/3.184, 6.233/5.789, 5.920/6.156, 7.674/8.325 ms. Vanilla/DH average FPS and p99 fail; both shader modes pass this run. No isolated intrinsic-state speedup established.
Java1,704/Rust2,359 tests pass;2 Java skips/3 Rust ignores. Seven lifecycle cases, reviewed vanilla/Iris+DH pairs and all six content digests pass; full parity and Rust-only application remain incomplete.

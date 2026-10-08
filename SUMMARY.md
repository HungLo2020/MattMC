# Rust vs Frozen Java speed (2026-10-08; native property milestone; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,059.7 / 1,063.5 | 1,121.7 / 1,143.7 |
| Vanilla + DH | 611.6 / 623.5 | 586.3 / 654.8 |
| Shaders | 339.6 / 345.0 | 311.8 / 308.6 |
| Shaders + DH | 245.3 / 241.5 | 223.5 / 222.8 |
ABAB receipts: `artifacts/graphics-captures/validation/native-properties-master-20261008/summary.json`; all16 runs clean, exact frame counts, VUID0.
Performance FAIL: FPS medians in table order -6.3%, -0.5%, +10.3%, +9.1%; p99 Current/Frozen 3.730/3.549, 6.483/6.251, 5.512/6.638, 7.660/8.043 ms. Vanilla and DH fail; both shader modes pass this run.
Java1,699/Rust2,356 tests pass; 2 Java skips/3 Rust ignores. Seven lifecycle cases and reviewed vanilla/Iris+DH coast pairs pass; broader parity and Rust-only application remain incomplete.

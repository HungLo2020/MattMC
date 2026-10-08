# Rust vs Frozen Java speed (2026-10-08; native block state definitions, before physical-settings follow-up; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,055.0 / 998.8 | 1,142.8 / 1,125.2 |
| Vanilla + DH | 612.2 / 733.0 | 589.2 / 707.9 |
| Shaders | 329.8 / 336.8 | 307.5 / 305.2 |
| Shaders + DH | 237.8 / 247.1 | 217.5 / 217.2 |
Receipts: `validation/native-block-definitions-master-20261008/summary.json`; vanilla uses `validation/native-block-definitions-vanilla-clean-v2-20261008/summary.json` (under artifacts/graphics-captures). All16 accepted runs clean, exact6000,VUID0; earlier vanilla sets excluded.
Performance FAIL: FPS medians in table order −9.4%, +3.7%, +8.8%, +11.5%; p99 Current/Frozen 3.880/3.682, 5.900/5.817, 11.195/7.360, 14.606/9.848 ms. All p99 comparisons fail; no isolated migration speedup established.
Java1,699/Rust2,356 tests pass;2 Java skips/3 Rust ignores. Seven lifecycle cases, reviewed vanilla/Iris+DH pairs and all native definition digests pass; full parity and the Rust-only application remain incomplete.

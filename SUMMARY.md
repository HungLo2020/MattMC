# Rust vs Frozen Java speed (2026-10-09; local DH frame handoff SHA1ad43eea; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,075.5 / 1,024.5 | 1,182.1 / 1,111.8 |
| Vanilla + DH | 600.7 / 599.4 | 719.0 / 609.9 |
| Shaders | 344.5 / 325.4 | 308.2 / 311.3 |
| Shaders + DH | 247.3 / 245.8 | 222.6 / 220.6 |
Receipt: `validation/native-dh-frame-ownership-20261009/summary.json` FAILED performance floors. All16 runs clean/exact6000,VUID0,exceptions0,owned orphans0;source/library/Frozen unchanged. Measured release predates later malformed-input hardening;no isolated speedup claim.
Performance OPEN: vanilla-8.5%,p99 3.867/3.203ms; DH-9.7%,p99 7.249/5.582ms; shaders+8.1%,p99 5.300/6.489ms; shader+DH+11.3%,p99 7.347/7.964ms. Vanilla/DH fail FPS and p99;both shader modes pass. Between-session differences do not establish causation.
Java1729/Rust2396 pass(2 skips/3 ignores),Wiki2481/43. Seven lifecycle cases/reviewed pairs pass on measured release;hardened SHA77998a11 passes fresh Iris+DH proof and paired60000-frame profiles. Sampled consumption432→34MB/15s;encoder no samples. World-state/Rust-only migration remain unfinished.

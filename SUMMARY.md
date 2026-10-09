# Rust vs Frozen Java speed (2026-10-08; local map/state-policy candidate; RTX 2070; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,052.6 / 1,087.5 | 1,132.9 / 1,147.9 |
| Vanilla + DH | 732.4 / 602.1 | 605.2 / 712.4 |
| Shaders | 329.0 / 330.1 | 311.3 / 310.0 |
| Shaders + DH | 243.8 / 248.8 | 212.6 / 221.9 |
Receipts: `validation/native-map-policy-master-v2-20261008/summary.json` (original workflow FAILED); shader+DH replaced by fresh `native-map-policy-shader-dh-health-v3-20261008/summary.json` (PASS). Combined16 accepted runs: exact6000,VUID0,clean shutdown,unchanged sources/native/Frozen. Failed receipts preserved.
Performance remains OPEN: vanilla median−6.2%,p99 Current/Frozen3.516/3.455 ms fail;DH+1.3%,4.847/5.721 ms and shaders+6.1%,5.581/6.306 ms pass protocol medians. Shader+DH+13.4%,7.410/9.666 ms passes. DH spread is large;no isolated migration gain or regression is established.
Java1,725/Rust2,389 tests pass;2 Java skips/3 Rust ignores. Seven lifecycle cases,reviewed vanilla/Iris+DH pairs and Wiki2481/43 pass. Final release map/crop proof and five-pair observer pass(ten digests;bootstrap2.303/2.277s,allocation960.26/1067.78MB). Full parity and Rust-only application remain incomplete.

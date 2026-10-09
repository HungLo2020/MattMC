# Rust vs Frozen Java speed (2026-10-09; release31c8c8cc; RTX3080Ti; moving camera;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,059.3 / 1,132.7 | 1,152.4 / 1,153.9 |
| Vanilla + DH | 685.2 / 720.5 | 608.0 / 730.7 |
| Shaders | 352.9 / 351.6 | 316.9 / 316.1 |
| Shaders + DH | 254.7 / 250.6 | 230.3 / 228.6 |
Receipt:`validation/native-live-light-final-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;performance gate FAILS vanilla FPS/p99 and DH p99.25 generated copies retired;source/native/Frozen/user-edit integrity passes.
Performance OPEN:median p99 Current/Frozen vanilla3.781/3.439ms,DH5.524/5.519,shaders5.370/6.693,shader+DH7.377/7.542.No isolated or overall gain claimed;streaming profiles pass identity/movement/cleanup.Weighted Java allocation0.934/2.186GB per8s(Current/Frozen),diagnostic only.
Rust2436(3 ignored),Java1800(2 skipped),all7 lifecycle cases,reviewed vanilla/Iris+DH pairs,DH coverage andWiki2489/43 pass.Rust owns canonical live light generations,propagation/sky results and independent packet imports.Next:bulk terrain lighting and retained CPU mesh payloads;Rust-only app unfinished.

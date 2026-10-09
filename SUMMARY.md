# Rust vs Frozen Java speed (2026-10-09; release0570723b; pre-sync e62309898; RTX3080Ti; moving camera;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,055.9 / 1,065.3 | 1,159.6 / 1,177.8 |
| Vanilla + DH | 717.1 / 736.8 | 669.9 / 591.2 |
| Shaders | 334.7 / 341.8 | 307.6 / 309.4 |
| Shaders + DH | 242.7 / 242.2 | 222.2 / 222.8 |
Receipt:`validation/native-item-layer-final-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;performance floors FAIL. Measured source/native/Frozen/user-edit integrity passes;25 generated copies retired. Incoming111d7a9c4 Java fixes were integrated afterward;these timings do not measure that combined Java source.
Performance OPEN:vanilla−9.2%,p99 3.906/3.319ms;DH+15.3%,p99 3.017/6.427;shaders+9.6%,p99 5.516/6.792;shader+DH+9.0%,p99 8.397/8.535. DH/Frozen repeats vary;no isolated item-layer speedup or prior tail root cause proven.
Rust2429(3 ignores),focused Java153/full Java1768(2 skips),all7 lifecycle cases,reviewed coast/HUD pairs andWiki2488/43 pass on measured source. Rust owns item-layer poses/direct GUI consumption;unused mutable meshes allocate lazily. Next:world/hand and world-state producers/consumers;Rust-only app unfinished.

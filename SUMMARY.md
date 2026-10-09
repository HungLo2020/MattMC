# Rust vs Frozen Java speed (2026-10-09; release0d54a098; RTX3080Ti; moving camera;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,076.9 / 1,457.3 | 1,178.0 / 1,161.7 |
| Vanilla + DH | 748.1 / 738.2 | 797.4 / 602.7 |
| Shaders | 356.3 / 351.8 | 319.6 / 320.0 |
| Shaders + DH | 257.1 / 246.4 | 228.4 / 226.6 |
Receipt:`validation/native-world-item-final-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;overall performance gate FAILS vanilla p99. Measured source/native/Frozen/user-edit integrity passes;25 generated copies retired.
Performance OPEN:median p99 Current/Frozen vanilla3.506/3.019ms,DH4.862/5.604,shaders4.725/5.826,shader+DH6.733/7.695. All median average-FPS floors pass here, but Current vanilla and Frozen DH repeats vary substantially;no robust or isolated speedup claimed.
Rust2432(3 ignores),full Java1793(2 skips),final affected Java184,all7 lifecycle cases,reviewed coast/HUD pairs andWiki2488/43 pass;strict held-clock foil fixture passes after observer corrections. Rust owns world/hand pose composition;source-flag getenv samples194→0MB/15s. Next:live light storage/direct terrain consumers;Rust-only app unfinished.

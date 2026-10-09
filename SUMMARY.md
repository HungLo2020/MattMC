# Rust vs Frozen Java speed (2026-10-09; release d9d1a9d6; RTX3080Ti; settled rotating view;6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,053.6 / 1,473.1 | 1,160.8 / 1,155.7 |
| Vanilla + DH | 748.0 / 565.6 | 612.6 / 673.1 |
| Shaders | 337.5 / 335.4 | 317.3 / 316.9 |
| Shaders + DH | 263.1 / 251.4 | 228.3 / 227.4 |
Receipt:`validation/native-terrain-light-integrated-20261009/summary.json`:all16 ABAB/exact6000 clean,VUID/exception/orphan0;all7 lifecycle and reviewed diagnostic vanilla/Iris+DH compatibility pairs/DH coverage pass.25 generated copies retired;source/native/Frozen/user-edit integrity passes.
Performance OPEN:averageFPS exceeds Frozen in all4 modes;medianp99 Current/Frozen vanilla3.216/3.072ms,DH6.839/5.749,shaders6.137/5.979,shaderDH6.212/7.446.Gate FAILS vanilla,shader andDH p99;substantial repeat variance,no isolated migration gain.
CombinedRust2443 pass/3 ignored;Java1807 tests/2 skipped/no failures;Wiki2491/43 pass.Ordinary DH+visible-map C/F/F/C loopFPS median C/F entry535/555,standing619/601,travel634/623;p99ms6.740/5.904,2.625/2.911,3.000/2.904:entry/travel floors remain open;pop-in unproved.JDK fix6 checks pass;next biome owner/direct sky12 CPU checks pass,production wiring pending;Rust-only app unfinished.

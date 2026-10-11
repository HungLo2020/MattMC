# Rust vs Frozen Java speed (2026-10-11; saved-light batch on abbd16d3d, release a15c0555; RTX3080Ti; driver595.99.02; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,367.2 / 1,357.9 | 1,144.7 / 1,152.3 |
| Vanilla + DH | 868.2 / 886.2 / 890.3 / 795.8 | 610.1 / 611.0 / 822.9 / 807.8 |
| Shaders | 355.9 / 333.4 | 315.4 / 316.2 |
| Shaders + DH | 260.6 / 262.7 | 226.9 / 226.8 |
Receipt: build/native-dh-saved-light/gameplay/performance.json; all20 predeclared alternating/exact6000 runs clean; source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained;20 completed work copies pruned.
Performance gate FAIL: medianFPS C/F 1362.55/1148.50, 877.20/709.40, 344.65/315.80, 261.65/226.85; medianp99ms 3.365/3.337, 2.299/5.322, 4.748/6.737, 5.494/8.743 (vanilla/DH/shaders/combined). All FPS floors pass; vanilla p99 misses by0.029ms, other p99 floors pass. Settled rotating workload only; no isolated migration FPS gain or broad acceptance claim.
Migration active: Java simulation/network/platform/startup and chunk orchestration remain; Rust executable unimplemented. Saved palettes/NBT now create primary Rust block/biome/light owners;82 palette and40 actual Frozen chunk cases match. Release453df088 full suites:Rust2623pass/3ignored;Java1969reported/2skips/0fail. Three quiet guarded40-chunk load pairs:Current10.22–10.30ms vsFrozen8.31ms (~1.23–1.24×, floor FAIL), down from earlier d710's1.61–1.62×. Warmed old-image JFR allocation estimate7.01/18.23GB C/F over40,000 loads is diagnostic. All3 quiet control pairs pass:22–23% below pre-optimization,26–27% below published baseline; 7 lifecycle cases and2 settled Frozen pairs pass on453df088,VUID0;9 exact native artifacts and4 deterministic originals reviewed. No fresh453df088 FPS matrix. Receipts:build/native-saved-palettes/.

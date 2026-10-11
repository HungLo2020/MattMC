# Rust vs Frozen Java speed (2026-10-11; saved-light batch on abbd16d3d, release a15c0555; RTX3080Ti; driver595.99.02; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,367.2 / 1,357.9 | 1,144.7 / 1,152.3 |
| Vanilla + DH | 868.2 / 886.2 / 890.3 / 795.8 | 610.1 / 611.0 / 822.9 / 807.8 |
| Shaders | 355.9 / 333.4 | 315.4 / 316.2 |
| Shaders + DH | 260.6 / 262.7 | 226.9 / 226.8 |
Receipt: build/native-dh-saved-light/gameplay/performance.json; all20 predeclared alternating/exact6000 runs clean; source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained;20 completed work copies pruned.
Performance gate FAIL: medianFPS C/F 1362.55/1148.50, 877.20/709.40, 344.65/315.80, 261.65/226.85; medianp99ms 3.365/3.337, 2.299/5.322, 4.748/6.737, 5.494/8.743 (vanilla/DH/shaders/combined). All FPS floors pass; vanilla p99 misses by0.029ms, other p99 floors pass. Settled rotating workload only; no isolated migration FPS gain or broad acceptance claim.
Saved-lighta15c0555: fullRust2618pass/3ignored; fullJava1962reported/2skips/0fail, seven exact consumers;64 Frozen imports/clear-edits and256 native columns match. Six alternating100-import pairs C/F5.794/289.178ms, C/prior5.767/290.383ms (~98% lower scoped CPU cost). Runtime profile clean but did not sample importer calls; hardware7 lifecycle/2 reviewed settled pairs pass,9 exact artifacts,VUID0. First-frame/temporal/streaming/long-memory unproved. NBT/file orchestration, source traversal/material resolution, simulation/startup/network remain Java; Rust app unimplemented; goal incomplete.

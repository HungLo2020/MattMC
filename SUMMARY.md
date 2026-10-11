# Rust vs Frozen Java speed (2026-10-10; last measured DH columns/lighting handoff, release839ff128; later terrain/full-data changes unmeasured; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,376.1 / 1,287.5 | 1,142.8 / 1,164.3 |
| Vanilla + DH | 745.4 / 721.2 / 723.4 / 728.3 | 693.4 / 806.3 / 632.5 / 774.0 |
| Shaders | 340.5 / 354.1 | 315.3 / 317.4 |
| Shaders + DH | 254.8 / 253.5 | 226.1 / 224.0 |
Receipt: build/native-dh-column-migration/performance.json; all20 predeclared ABAB/exact6000 runs clean, source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained.
Performance acceptance FAIL: medianFPS C/F1331.80/1153.55 vanilla,725.85/733.70 DH,347.30/316.35 shaders,254.15/225.05 combined; medianp99ms3.421/3.110,4.865/5.321,4.967/5.979,6.811/8.034. Vanilla p99 and DH FPS miss; both shader modes pass. No isolated speedup or broad acceptance claim.
Last verified DH metadata/parent/scoped bridge on upstream2edce1f66 + audio, imageef31dac8: fullRust2593pass/3ignored; fullJava1943reported/2skips/zero failures, six exact workers. Fresh six-pair CPU C/F per1000: create10.299/13.879ms (.742x); update55.868/71.545ms (.781x). Earlier source-table86f20f9b: create5.959/15.063ms; update102.182/71.016ms FAIL; tint ratios .964/.666/.512/.494/.486/.914 for r0d6/r1d6/r2d6/r3d6/r3d7/r3d8, default20.805/42.122ms per50000. Guards/checksums match; CPU fixtures only, no fresh FPS/streaming/long-memory acceptance. NVIDIA595.99.02 matched; Vulkan preflight passes. General simulation/startup/network remain Java; Rust app unimplemented; goal incomplete.

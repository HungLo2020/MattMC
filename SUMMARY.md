# Rust vs Frozen Java speed (2026-10-10; last measured DH columns/lighting handoff, release839ff128; later terrain/full-data changes unmeasured; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,376.1 / 1,287.5 | 1,142.8 / 1,164.3 |
| Vanilla + DH | 745.4 / 721.2 / 723.4 / 728.3 | 693.4 / 806.3 / 632.5 / 774.0 |
| Shaders | 340.5 / 354.1 | 315.3 / 317.4 |
| Shaders + DH | 254.8 / 253.5 | 226.1 / 224.0 |
Receipt: build/native-dh-column-migration/performance.json; all20 predeclared ABAB/exact6000 runs clean, source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained.
Performance acceptance FAIL: medianFPS C/F1331.80/1153.55 vanilla,725.85/733.70 DH,347.30/316.35 shaders,254.15/225.05 combined; medianp99ms3.421/3.110,4.865/5.321,4.967/5.979,6.811/8.034. Vanilla p99 and DH FPS miss; both shader modes pass. No isolated speedup or broad acceptance claim.
Current DH packets6ae38d7a onf8feb3ff5: fullRust2607pass/3ignored; fullJava1953reported/2skips/zero failures (1951executed), six exact workers. Frozen512 topology/byte cases and1920 public packet/callback checks pass, including audit mode. Six-pair CPU per2000 builds×2048 faces C/F, packets smooth/overlap/scatter then grass variants: 234.272/430.817ms (0.544x); 300.963/616.584ms (0.488x); 302.844/898.005ms (0.337x); 262.227/456.136ms (0.575x); 324.842/662.464ms (0.490x); 328.676/934.345ms (0.352x). All scoped gates/checksums/guards pass. CPU only: fresh FPS/streaming/long-memory unproved; NVIDIA595.99.02/Vulkan preflight pass. Faces, contextual getters, collector transport and simulation/startup/network remain Java; Rust app unimplemented; goal incomplete.

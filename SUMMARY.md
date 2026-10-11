# Rust vs Frozen Java speed (2026-10-10; last measured DH columns/lighting handoff, release839ff128; later terrain/full-data changes unmeasured; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,376.1 / 1,287.5 | 1,142.8 / 1,164.3 |
| Vanilla + DH | 745.4 / 721.2 / 723.4 / 728.3 | 693.4 / 806.3 / 632.5 / 774.0 |
| Shaders | 340.5 / 354.1 | 315.3 / 317.4 |
| Shaders + DH | 254.8 / 253.5 | 226.1 / 224.0 |
Receipt: build/native-dh-column-migration/performance.json; all20 predeclared ABAB/exact6000 runs clean, source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained.
Performance acceptance FAIL: medianFPS C/F1331.80/1153.55 vanilla,725.85/733.70 DH,347.30/316.35 shaders,254.15/225.05 combined; medianp99ms3.421/3.110,4.865/5.321,4.967/5.979,6.811/8.034. Vanilla p99 and DH FPS miss; both shader modes pass. No isolated speedup or broad acceptance claim.
Current DH handoff111c5922 on d42abf1c: fullRust2611pass/3ignored; fullJava1958reported/2skips/zero failures (1956executed), six exact workers. Frozen1920 public packet checks and480 whole-column callback sequences pass, including audit mode. Six-pair CPU per2000 builds×2048 faces C/F, smooth/overlap/scatter then grass:210.064/266.860ms (.787x);264.614/387.627 (.683x);265.082/668.460 (.397x);226.006/287.424 (.786x);280.196/426.954 (.656x);278.210/696.769 (.399x). All Frozen gates/checksums/guards pass. Complete publication vs previous Current: non-grass1.025/1.004/1.008x, grass.971/.965/.980x; no overall isolated speedup. Seven Iris+DH lifecycle cases and2 reviewed settled Frozen pairs pass (VUID0, DH19.39%); fresh FPS/temporal/streaming/long-memory unproved. Faces, contextual getters, collector orchestration and simulation/startup/network remain Java; Rust app unimplemented; goal incomplete.

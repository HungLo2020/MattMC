# Rust vs Frozen Java speed (2026-10-10; last measured DH columns/lighting handoff, release839ff128; later terrain/full-data changes unmeasured; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,376.1 / 1,287.5 | 1,142.8 / 1,164.3 |
| Vanilla + DH | 745.4 / 721.2 / 723.4 / 728.3 | 693.4 / 806.3 / 632.5 / 774.0 |
| Shaders | 340.5 / 354.1 | 315.3 / 317.4 |
| Shaders + DH | 254.8 / 253.5 | 226.1 / 224.0 |
Receipt: build/native-dh-column-migration/performance.json; all20 predeclared ABAB/exact6000 runs clean, source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained.
Performance acceptance FAIL: medianFPS C/F1331.80/1153.55 vanilla,725.85/733.70 DH,347.30/316.35 shaders,254.15/225.05 combined; medianp99ms3.421/3.110,4.865/5.321,4.967/5.979,6.811/8.034. Vanilla p99 and DH FPS miss; both shader modes pass. No isolated speedup or broad acceptance claim.
Release628cfd97: retained world/DH producers and native terrain handoff now include client residency, shared biome inputs and the complete DH change-hash algorithm. RustCPU542pass; focusedJava31pass; fullJava1909reported/two skips/zero failures/errors, six exact workers and39481 Current/38176 Frozen/native/prompt guards match. Actual Frozen600-operation residency and96-case hash replay pass. Java still binds objects/orchestrates callbacks and owns general simulation/startup. Integrated368c10a87 documentation; tested batch prepared for authorized incremental master publication. FullRust/hardware/runtime/FPS/long-memory await matched NVIDIA driver; new leases/overhead unmeasured. Earlier839ff128 weightedJava0.828/4.226GB C/F excludes native; shortCurrentRSS4.684GiB only. Broad/temporal parity and Java removal remain open.

# Rust vs Frozen Java speed (2026-10-10; last measured DH columns/lighting handoff, release839ff128; later terrain/full-data changes unmeasured; RTX3080Ti; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,376.1 / 1,287.5 | 1,142.8 / 1,164.3 |
| Vanilla + DH | 745.4 / 721.2 / 723.4 / 728.3 | 693.4 / 806.3 / 632.5 / 774.0 |
| Shaders | 340.5 / 354.1 | 315.3 / 317.4 |
| Shaders + DH | 254.8 / 253.5 | 226.1 / 224.0 |
Receipt: build/native-dh-column-migration/performance.json; all20 predeclared ABAB/exact6000 runs clean, source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained.
Performance acceptance FAIL: medianFPS C/F1331.80/1153.55 vanilla,725.85/733.70 DH,347.30/316.35 shaders,254.15/225.05 combined; medianp99ms3.421/3.110,4.865/5.321,4.967/5.979,6.811/8.034. Vanilla p99 and DH FPS miss; both shader modes pass. No isolated speedup or broad acceptance claim.
Latest CPU batch6f0ea0b6 moves complete server chunk-task queue ownership and callable-row allocation into Rust; Java retains callable pins and dispatch/future callbacks. Frozen37624 actual operations match;257 Rust world/27 architecture/4 cloud and4 JNI controls pass. FullJava1918reported/two skips/zero failures/errors across344 suites, six exact workers and39503 Current/38176 Frozen/native/prompt guards pass. Supplemental six-pair queue comparison:249.94ms C/255.99ms F, 15000 measured rounds after75000 warmup rounds per JVM; no server/FPS or broad performance acceptance implied. Earlier world/DH batchesd3c427a79 and5de23173a are published. General simulation/startup/network and app remain Java or unimplemented; goal incomplete. Hardware/runtime/long-memory checks await matched NVIDIA driver.

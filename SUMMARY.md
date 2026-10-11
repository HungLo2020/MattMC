# Rust vs Frozen Java speed (2026-10-11; DH boxes tested onabb768873, release0133e073; RTX3080Ti; driver595.99.02; rotating view; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,465.0 / 1,390.9 | 1,164.3 / 1,178.8 |
| Vanilla + DH | 894.3 / 781.9 / 892.1 / 790.2 | 742.3 / 610.1 / 604.3 / 820.5 |
| Shaders | 344.7 / 347.7 | 319.2 / 318.7 |
| Shaders + DH | 261.3 / 271.7 | 225.0 / 228.3 |
Receipt: build/native-dh-box-gameplay/performance.json; all20 predeclared alternating/exact6000 runs clean, source/native/Frozen/protected-prompt guards and10 Current fingerprints pass. DH has four repeats/side; others two. All runs retained.
Scoped performance PASS: medianFPS C/F1427.95/1171.55 vanilla,841.15/676.20 DH,346.20/318.95 shaders,266.50/226.65 combined; medianp99ms2.905/3.024,3.064/5.653,4.456/5.976,5.445/8.757. Every FPS/p99 floor passes. Settled rotating workload only; no isolated migration speedup or broad acceptance claim.
Rust DH box0133e073: fullRust2616pass/3ignored; fullJava1959reported/2skips/0fail, seven exact consumers; Frozen3072 callbacks/768 native boxes,192 semantic boxes/48 shade values pass. All12 scoped CPU gates/checksums pass: 25–52% lower component time vs Frozen,9–55% vs prior Current. Real DH profile confirms native producer use. Hardware7 lifecycle/2 reviewed settled pairs pass,9 exact artifacts,VUID0. First-frame/temporal/streaming/long-memory unproved. Source traversal/material resolution, collector orchestration, simulation/startup/network remain Java; Rust app unimplemented; goal incomplete.

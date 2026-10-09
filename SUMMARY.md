# Rust vs Frozen Java speed (2026-10-09; section-snapshot SHA15c7ba5e; RTX 3080 Ti; moving camera; 6,000 frames)
| Mode | Rust/Vulkan candidate | Frozen Java/OpenGL |
| --- | --- | --- |
| Vanilla | 1,039.1 / 1,029.5 | 1,156.8 / 1,137.9 |
| Vanilla + DH | 778.4 / 604.2 | 627.3 / 715.1 |
| Shaders | 327.7 / 340.8 | 311.6 / 309.4 |
| Shaders + DH | 244.8 / 241.1 | 223.2 / 221.0 |
Receipt: `validation/native-chunk-state-snapshots-20261009/summary.json` FAILED performance floors. All16 runs clean/exact6000,VUID0,exceptions0,owned orphans0;source/library/Frozen unchanged during measurement;predates final Java view-adoption hardening. No isolated speedup claim.
Performance OPEN: vanilla−9.8%,p99 3.970/3.391ms;DH+3.0%,p99 4.466/5.472ms;shaders+7.6%,p99 5.912/5.857ms;shader+DH+9.4%,p99 7.925/7.774ms. Vanilla FPS/p99 and both shader p99 floors fail;DH repeats vary substantially.
Full Java1739(2 skips) before hardening;final38 affected Java,Rust2409(3 ignores),video7,Wiki2483/43 pass. Seven lifecycle/reviewed pairs and final hardened IrisDH proof pass. Streaming profiles/source/positions pass;capture+slice prep35→24 samples/8s,downstream snapshot313→334;diagnostic only. Loaded-world mutation/Rust-only app unfinished.
